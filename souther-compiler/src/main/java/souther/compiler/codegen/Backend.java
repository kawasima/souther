package souther.compiler.codegen;

import souther.compiler.query.Bodies;

import souther.compiler.check.ExpandedClauseLookup;
import souther.compiler.check.InvariantStatements;
import souther.compiler.check.Boundary;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.SourcePos;
import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.ReqSig;
import souther.compiler.check.Requirements;
import souther.compiler.core.Contract;
import souther.compiler.core.ValueShape;
import souther.compiler.check.Sig;
import souther.compiler.check.SpecImplementation;
import souther.compiler.core.EnsuresEnforcement;
import souther.compiler.core.KernelSignatures;
import souther.compiler.diag.SourceLayouts;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.check.TypeOps;
import souther.compiler.core.Composition;
import souther.compiler.core.Core;

import souther.compiler.coverage.CoverageSites;
import souther.compiler.generated.ProbeImage;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.JvmClassName;
import souther.compiler.jvm.SoutherJvmAbi;
import souther.compiler.types.ValueName;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static souther.compiler.codegen.Descriptors.*;
import static souther.compiler.codegen.JvmTypes.*;

/**
 * The ClassFile-API backend (spec §jvm-output, §compiler-pipeline). Emits JVM bytecode directly for each
 * {@code data}: the value class (package-private ctor + invariant-checking
 * {@code __construct}) and nested {@code $Dec}/{@code $Enc} classes. Fields may reference
 * other data types; object decoders accumulate every field error (spec §case-propagation).
 */
public final class Backend {

    private final CodegenContext ctx;
    /** Aliases of {@link CodegenContext#pkg}/{@link CodegenContext#symbols}, read as bare names by
     * the code still living here. */
    private final String pkg;
    private final DerivedSymbols symbols;

    private final CodecGen codec;
    private final ValueClassGen value;
    /** The checker's elaborated bodies: what this emits from (issue #81). */
    private final Bodies.Elaborated checked;
    private final EnsuresGen ensures;

    /**
     * Every fn {@code module} emits a method for: what its source declared, and what it took on to
     * emit because it reaches a recursive helper it did not write.
     *
     * <p>The two are one question down here and two everywhere above. What may be walked, what must
     * be proven total, what the module publishes — each of those is about the declaring module and
     * reads the component the fn is in; a class being written is about neither, because a method is a
     * method.
     */
    private static List<Hir.FnDef> emitted(Hir.Module module) {
        List<Hir.FnDef> all = new ArrayList<>(module.fns());
        all.addAll(module.takenOn());
        return all;
    }

    private Backend(CodegenContext ctx, Bodies.Elaborated checked) {
        this.ctx = ctx;
        this.pkg = ctx.pkg;
        this.symbols = ctx.symbols;
        this.codec = new CodecGen(ctx);
        this.value = new ValueClassGen(ctx, codec);
        this.checked = checked;
        this.ensures = new EnsuresGen(ctx);
    }

    /**
     * Where {@code behavior}'s declared relation is checked.
     *
     * <p>Asked of the context, which is where the decision was put for the emitters to read. Kept
     * here as well it would be one decision in two fields filled from one argument, and the whole
     * point of there being one value is that there is one reading of it — a later change setting one
     * and not the other is a check emitted in a place the other half disagrees with.
     */
    private EnsuresEnforcement checkOf(ValueName.Behavior behavior) {
        return ctx.ensuresCheckOf(behavior);
    }

    /** The body the checker elaborated for {@code name}. Codegen runs only on a module that type
     * checked, and the check elaborates every body the backend emits, so a missing one is a compiler
     * invariant violation rather than something to emit around. */
    private static Core elaborated(Map<String, Core> bodies, String name) {
        Core body = bodies.get(name);
        if (body == null) {
            throw new IllegalStateException("no elaborated body for `" + name + "`");
        }
        return body;
    }

    /** {@code ACC_PUBLIC} when the name is exposed (or the module exposes all), else 0. */
    private int pub(String name) {
        return ctx.pub(name);
    }

    /** Generates a module's classes. {@code symbols} covers own plus imported definitions;
     * {@code typePackage} maps an imported type or behavior name to its declaring module (spec §modules);
     * {@code importedSigs} carries imported behaviors' signatures so a composition can name one as a stage
     * (spec §composition); {@code importedInjected} are imported injection-target behaviors, which a
     * composition here inherits as requirements to inject and bind (spec §injected-behavior,
     * §composition-with-requirements); {@code requirements} says what each behavior takes injected and in
     * what order — the answer the example verifier reads too, so a fake reaches the parameter this
     * constructor binds it to; {@code checked} carries the type checker's elaborated bodies, which is what
     * the emitter reads instead of inferring types again (issue #81); {@code dischargeInvariants} carries
     * this module's invariant clauses in the representation the language's own operations survive in, which
     * is what a derived decoder's constraint mapping reads (spec §decoder-error); {@code shapes} says
     * what a value of each declared data is made of and what must hold of one, which is what a
     * construction is refused by and is the checker's answer rather than this emitter's
     * (issue #1080). */
    public static Emissions generate(Hir.Module module, DerivedSymbols symbols,
                                               PublishedDeclarations published,
                                               DeclarationKinds kinds,
                                               KernelSignatures kernels,
                                               Map<String, String> typePackage,
                                               Map<ValueName.Behavior, Sig> sigs,
                                               Map<ValueName.Behavior, Sig> importedSigs,
                                               Set<ValueName.Behavior> importedInjected,
                                               Map<ValueName.Behavior, ReqSig> calleeSigs,
                                               Map<String, List<BehaviorRequirement>> requirements,
                                               Bodies.Elaborated checked,
                                               Map<ValueName.Behavior, Composition> compositions,
                                               ExpandedClauseLookup dischargeInvariants,
                                               InvariantStatements invariantStatements,
                                               Map<TypeSymbol.AtModule, ValueShape> shapes,
                                               Map<ValueName.Behavior, EnsuresEnforcement> checks,
                                               Map<String, Type> standingCalls,
                                               SourceLayouts layouts) {
        return generate(module, symbols, published, kinds, kernels, typePackage, sigs, importedSigs,
                importedInjected,
                calleeSigs, requirements, checked, compositions, dischargeInvariants,
                invariantStatements, shapes, checks, standingCalls, layouts, Instrumentation.NONE);
    }

    /**
     * The same classes, counting what the code goes through and recording which arms it took.
     *
     * <p>An overload rather than a mode on the one signature, because these classes are not the
     * module's classes. They call into the compiler, they are never written out, and the only things
     * that ask for them are an evaluation and a measurement. Anything that ships goes through the
     * signature above and gets bytecode with no reference to either in it at all.
     *
     * <p>Where {@code instrumentation} asks for coverage, the arms numbered are the arms of
     * {@code checked}, and the plan that numbers them is made here from those bodies. The emitter
     * looks each node up by identity, so a plan of any other bodies addresses none of the nodes
     * being walked — which is why there is nowhere for a caller to supply one.
     */
    public static Emissions generate(Hir.Module module, DerivedSymbols symbols,
                                               PublishedDeclarations published,
                                               DeclarationKinds kinds,
                                               KernelSignatures kernels,
                                               Map<String, String> typePackage,
                                               Map<ValueName.Behavior, Sig> sigs,
                                               Map<ValueName.Behavior, Sig> importedSigs,
                                               Set<ValueName.Behavior> importedInjected,
                                               Map<ValueName.Behavior, ReqSig> calleeSigs,
                                               Map<String, List<BehaviorRequirement>> requirements,
                                               Bodies.Elaborated checked,
                                               Map<ValueName.Behavior, Composition> compositions,
                                               ExpandedClauseLookup dischargeInvariants,
                                               InvariantStatements invariantStatements,
                                               Map<TypeSymbol.AtModule, ValueShape> shapes,
                                               Map<ValueName.Behavior, EnsuresEnforcement> checks,
                                               Map<String, Type> standingCalls,
                                               SourceLayouts layouts,
                                               Instrumentation instrumentation) {
        try {
            return generating(module, symbols, published, kinds, kernels, typePackage, sigs,
                    importedSigs,
                    importedInjected, calleeSigs, requirements, checked, compositions,
                    dischargeInvariants, invariantStatements, shapes, checks, standingCalls,
                    layouts, instrumentation);
        } catch (IllegalArgumentException e) {
            // Something the writer would not hold, from a member no definition here claimed — a
            // synthesised class, a shared one. It belongs to the module, which is as near as anything
            // gets to naming it.
            throw asLimit(e, WrittenName.synthetic(module.name(), module.pos()));
        }
    }

    private static Emissions generating(Hir.Module module, DerivedSymbols symbols,
                                        PublishedDeclarations published,
                                        DeclarationKinds kinds,
                                        KernelSignatures kernels,
                                                  Map<String, String> typePackage,
                                                  Map<ValueName.Behavior, Sig> sigs,
                                                  Map<ValueName.Behavior, Sig> importedSigs,
                                                  Set<ValueName.Behavior> importedInjected,
                                                  Map<ValueName.Behavior, ReqSig> calleeSigs,
                                                  Map<String, List<BehaviorRequirement>> requirements,
                                                  Bodies.Elaborated checked,
                                                  Map<ValueName.Behavior, Composition> compositions,
                                                  ExpandedClauseLookup dischargeInvariants,
                                                  InvariantStatements invariantStatements,
                                                  Map<TypeSymbol.AtModule, ValueShape> shapes,
                                                  Map<ValueName.Behavior, EnsuresEnforcement> checks,
                                                  Map<String, Type> standingCalls,
                                                  SourceLayouts layouts,
                                                  Instrumentation instrumentation) {
        Map<String, List<GeneratedClass>> caseToSums = new HashMap<>();
        for (Hir.Def def : module.defs()) {
            if (def instanceof Hir.SumData sum) {
                for (Hir.Name caseName : sum.cases()) {
                    caseToSums.computeIfAbsent(names(caseName).name(), k -> new ArrayList<>())
                            .add(new GeneratedClass.Value(sum.declares()));
                }
            }
        }
        // The checker has already run and rejects any dotted `A.decoder`/`.encoder` member
        // (exposing is type-granular, spec §jvm-codec), so every entry that reaches codegen is a bare name.
        Set<String> exposed = new HashSet<>(module.exposing());
        // After the Lower stage the only non-behavior fns left are recursive helpers (spec §fn-declaration);
        // each is lowered to a static method on the module's `$Fns` class rather than inlined.
        Set<String> behaviorNames = new HashSet<>();
        for (Hir.BehaviorDef bd : module.behaviors()) {
            behaviorNames.add(bd.name());
        }
        // Both components: a method is emitted for what the module declared and for what it took on
        // to emit, and by the time a class is written there is no difference between them.
        Map<String, Hir.FnDef> recHelpers = new LinkedHashMap<>();
        for (Hir.FnDef fn : emitted(module)) {
            if (!behaviorNames.contains(fn.name())) {
                recHelpers.put(fn.name(), fn);
            }
        }
        CodegenContext ctx = new CodegenContext(module.name(), symbols, published, kinds, kernels,
                caseToSums, typePackage,
                module.exposing().isEmpty(), exposed, standingCalls, layouts,
                module.pos().quotedFrom());
        ctx.setDischargeInvariants(dischargeInvariants);
        ctx.setInvariantStatements(invariantStatements);
        ctx.setValueShapes(shapes);
        ctx.setEnsuresChecks(checks);
        // The one place a coverage plan is made, and it is made from the bodies about to be emitted.
        // Every number the emitter writes into the bytecode and every number a report reads back
        // comes from this call, so there is no second numbering for either of them to disagree with.
        CoverageSites.Plan coverage = instrumentation.measuresCoverage()
                ? checked.plan() : CoverageSites.Plan.NONE;
        // Said off what was asked for and not off what the plan came to hold. A module whose bodies
        // have no arm to number is a module a run leaves an empty account of, which is not the same
        // as one a run leaves no account of at all.
        ProbeImage probes = instrumentation.measuresCoverage()
                ? new ProbeImage.Instrumented(coverage.identity())
                : new ProbeImage.Uninstrumented();
        ctx.setCoveragePlan(coverage);
        ctx.setCounting(instrumentation.counting());
        Backend b = new Backend(ctx, checked);
        // Before anything is written: a declaration wide enough that its generated method cannot hold
        // its arguments produces a class the JVM refuses at load time, and nothing downstream notices.
        JvmLimits.checkParameterSlots(module, ctx, recHelpers, sigs, requirements);
        // A behavior's class capitalizes its first letter (spec §jvm-behavior). Data names are already
        // capitalized, so `behavior quote` producing `data Quote` would generate two classes named
        // `Quote`. Reject the collision here rather than let one silently overwrite the other.
        // Every one of these checks asks the same question — does the ABI spell two of this module's
        // identities the same — so each compares what the ABI answers rather than a spelling worked
        // out here. What collides is decided on the JVM name; what the report names is the Souther
        // declarations that landed on it.
        Map<JvmClassName, Hir.Def> localTypes = new LinkedHashMap<>();
        for (Hir.Def d : module.defs()) {
            localTypes.put(SoutherJvmAbi.nameOf(new GeneratedClass.Value(d.declares())), d);
        }
        Map<JvmClassName, String> behaviorClassOwner = new LinkedHashMap<>();
        for (Hir.BehaviorDef bd : module.behaviors()) {
            JvmClassName cls = SoutherJvmAbi.nameOf(
                    new GeneratedClass.BehaviorInterface(module.name(), bd.name()));
            Hir.Def data = localTypes.get(cls);
            if (data != null) {
                throw CompileException.of(Diagnostic
                                .at(bd.pos()).say(new BehaviorMessage.ABehaviorCapitalizesOntoAData(bd.name(), data.name())).build());
            }
            String prev = behaviorClassOwner.put(cls, bd.name());
            if (prev != null) {
                throw CompileException.of(Diagnostic
                                .at(bd.pos()).say(new BehaviorMessage.TwoBehaviorsCapitalizeToOneClass(prev, bd.name(), cls.classDesc().displayName())).build());
            }
        }
        // A behavior whose output is an anonymous union gets a generated sealed interface
        // <behavior名>Result that its cases implement (spec §jvm-anonymous-union). Register those case->interface links
        // in caseToSums before the data classes are generated, so each case class picks the interface
        // up in withInterfaceSymbols. The interface classes themselves are emitted below.
        Map<GeneratedClass.BehaviorResult, Boundary.Alternatives> behaviorResults =
                b.behaviorResultInterfaces(module, sigs);
        b.rejectResultUnionCollisions(module, behaviorResults, localTypes, behaviorClassOwner);
        // A case class carries the result unions it belongs to as interfaces it implements, and that
        // list is settled when its own module is generated. A member this module declared takes the
        // interface on itself. A primitive and a type another module emitted cannot: `java.lang.Long`
        // is the JDK's, and giving an imported class an interface from here would make a module's
        // bytecode depend on which modules import it — the mirror of what ADR-0024 refuses. Such a
        // member reaches the union through a bridge case this module emits instead (ADR-0057).
        Map<TypeSymbol, List<GeneratedClass.BehaviorResult>> bridgeCases = new LinkedHashMap<>();
        behaviorResults.forEach((union, alternatives) -> {
            for (TypeSymbol member : alternatives.atoms()) {
                if (b.ctx.isLocalMember(member)) {
                    caseToSums.computeIfAbsent(member.name(), k -> new ArrayList<>()).add(union);
                } else {
                    bridgeCases.computeIfAbsent(member, k -> new ArrayList<>()).add(union);
                }
            }
        });
        b.rejectBridgeCaseCollisions(module, bridgeCases, localTypes, behaviorClassOwner);
        Emissions out = new Emissions(module.name(), probes);
        behaviorResults.forEach((union, alternatives) -> {
            // the union and its encoder belong to the behavior whose output they are, not to the
            // module, though the behavior did not write them
            Hir.BehaviorDef owner = b.behaviorNamed(module, union.behavior());
            emitting(owner.written(), () -> {
                out.put(union, b.generateBehaviorResult(union, alternatives));
                out.put(new GeneratedClass.Encoder(union),
                        b.codec.generateResultUnionEncoder(union, alternatives));
            });
        });
        // A bridge case is shared by every union that reaches its member, so no one behavior owns it;
        // it is left to the module, which the outermost boundary answers for.
        bridgeCases.forEach((member, unions) ->
                out.put(new GeneratedClass.BridgeCase(module.name(), member),
                        b.value.generateBridgeCase(member, unions)));
        for (Hir.Def def : module.defs()) {
            emitting(def.written(), () -> {
                switch (def) {
                    case Hir.Data data -> b.value.generateData(data, out);
                    case Hir.SumData sum -> b.value.generateSum(sum, out);
                    case Hir.UnitData unit -> b.value.generateUnit(unit, out);
                }
            });
        }
        // Injection targets (spec §injected-behavior): a SpecBehavior with no matching fn. Each becomes an
        // abstract base class a Java implementation extends (§java-base-class). Imported injection targets
        // (their base lives in the declaring module) are requirements too, so a composition here
        // injects and binds them (spec §composition-with-requirements) — but no base is generated for them here.
        Set<ValueName.Behavior> requiredNames = Requirements.injectedNames(module, importedInjected);
        // Beside them, and not among them: a behavior Souther is to implement and nobody has is
        // nothing to inject — no base is emitted for it, so there is nothing a caller could be
        // handed. `requiredNames` grows below with every behavior held as a field, which is why
        // what is unwritten is asked of the declarations rather than read off what is left over.
        Set<ValueName.Behavior> unwrittenNames = Requirements.unwrittenNames(module);
        Map<ValueName.Behavior, Type> requiredSuccess = new HashMap<>();
        Map<ValueName.Behavior, List<Type>> requiredParam = new HashMap<>();
        for (Hir.BehaviorDef bd : module.behaviors()) {
            ValueName.Behavior declared = new ValueName.Behavior(module.name(), bd.name());
            if (bd instanceof Hir.SpecBehavior spec && requiredNames.contains(declared)) {
                requiredSuccess.put(declared, b.successType(spec.ret()));
                List<Type> reqParams = new ArrayList<>();
                for (Hir.Param p : spec.params()) {
                    reqParams.add(b.successType(p.type()));
                }
                requiredParam.put(declared, reqParams);
                // Unit output cases get a no-arg factory (a unit has nothing to validate, so it is
                // built directly). A field-bearing constructed type gets a typed factory, but only
                // when the behavior declares it in `constructs` — that declaration is the authority
                // to build it (spec §asymmetric-interop), and unlike a unit it cannot be told apart from a decoded
                // pass-through output (会員) by shape alone.
                List<TypeSymbol> unitCases = new ArrayList<>();
                for (Hir.TypeTerm term : spec.ret().cases()) {
                    if (term instanceof Hir.TypeRef t && t.denotes() instanceof Type.Ref r
                            && b.symbols.declaredNode(r.name()) instanceof Hir.UnitData) {
                        unitCases.add(r.name());
                    }
                }
                List<TypeSymbol> dataConstructs = new ArrayList<>();
                Set<TypeSymbol> seenConstruct = new HashSet<>();
                if (spec.constructs() != null) {
                    for (Hir.Name tn : spec.constructs()) {
                        // a field-bearing data or newtype; de-duplicated so a repeated `constructs`
                        // entry does not emit the factory method twice (a duplicate-method class file)
                        if (b.symbols.declaredNode(names(tn)) instanceof Hir.Data
                                && seenConstruct.add(names(tn))) {
                            dataConstructs.add(names(tn));
                        }
                    }
                }
                emitting(spec.written(), () ->
                        out.put(new GeneratedClass.BehaviorInterface(module.name(), spec.name()),
                                b.generateRequiredBase(spec.name(), unitCases, dataConstructs,
                                        reqParams, b.successType(spec.ret()))));
            }
        }
        // An imported injected behavior (its base lives in the declaring module, so no base is built
        // here) is a requirement too; take its arity from the imported signature so the unary-vs-multi
        // dispatch treats a cross-module multi-input dependency the same as a local one (issue #57).
        // A behavior with an implementation of its own may be a requirement too, when it declares
        // `depends on` (spec [#depends-on]). Nothing is generated for it here — it has its own $Impl —
        // but the module that named it holds it as a field, so its arity and output belong in the
        // same maps the unary-vs-multi dispatch reads.
        Map<ValueName.Behavior, Hir.SpecBehavior> ownSpecs = new HashMap<>();
        for (Hir.BehaviorDef bd : module.behaviors()) {
            if (bd instanceof Hir.SpecBehavior spec) {
                ownSpecs.put(new ValueName.Behavior(module.name(), spec.name()), spec);
            }
        }
        for (Hir.BehaviorDef bd : module.behaviors()) {
            if (!(bd instanceof Hir.SpecBehavior spec)) {
                continue;
            }
            for (Hir.Var req : spec.dependsOn()) {
                ValueName.Behavior name = reachedBy(req);
                if (name == null || requiredNames.contains(name)) {
                    continue;
                }
                Hir.SpecBehavior own = ownSpecs.get(name);
                if (own != null) {
                    List<Type> ins = new ArrayList<>();
                    for (Hir.Param p : own.params()) {
                        ins.add(b.successType(p.type()));
                    }
                    requiredNames.add(name);
                    requiredParam.put(name, ins);
                    requiredSuccess.put(name, b.successType(own.ret()));
                    continue;
                }
                Sig imported = importedSigs.get(name);
                if (imported != null) {
                    requiredNames.add(name);
                    requiredParam.put(name, imported.inputTypes());
                    requiredSuccess.put(name, imported.outputType());
                }
            }
        }
        for (ValueName.Behavior name : importedInjected) {
            Sig sig = importedSigs.get(name);
            if (sig != null) {
                requiredParam.put(name, sig.inputTypes());
                requiredSuccess.put(name, sig.outputType());
            }
        }
        // The unary-vs-multi dispatch for required behaviors reads these; set once, so the base class,
        // the $Impl field/ctor, the bind factory, and every call site agree (issue #57).
        b.ctx.setRequiredSignatures(requiredParam, requiredSuccess);
        // A behavior that depends on nothing is called by being built where it is called, so it is not
        // in the injection maps above; what a call site needs is the signature it was typed against.
        b.ctx.setCalleeSignatures(calleeSigs);
        // What each behavior takes injected, worked out once and read here and at an example
        // (Bodies.Requirements): the order is this constructor's parameter order.
        Map<ValueName.Behavior, List<ValueName.Behavior>> behaviorDeps = new LinkedHashMap<>();
        for (Map.Entry<String, List<BehaviorRequirement>> e : requirements.entrySet()) {
            behaviorDeps.put(new ValueName.Behavior(module.name(), e.getKey()),
                    Requirements.names(e.getValue()));
        }
        // Which definition implements each behavior, and which of that definition's parameters are
        // the declared inputs, asked once for the module rather than worked out here: the snapshot's
        // assembler reads its binders from the same answer, so which local an input arrives in
        // cannot be one thing there and another here.
        Map<String, SpecImplementation.Implemented> implementations =
                SpecImplementation.implementationsOf(module);
        for (Hir.BehaviorDef bd : module.behaviors()) {
            emitting(bd.written(), () -> {
                // The class a declared relation is checked by, emitted by the module that declares
                // it whichever of the two places the check is called from. A crossing is in the
                // caller's bytecode and calls this one, so the rule has one home and a caller has
                // nothing of it to restate.
                ValueName.Behavior named = new ValueName.Behavior(module.name(), bd.name());
                Contract contract = b.checkOf(named).contract();
                if (contract != null) {
                    out.put(new GeneratedClass.Ensures(
                                    new GeneratedClass.BehaviorInterface(module.name(), bd.name())),
                            b.ensures.generate(contract));
                }
                switch (bd) {
                    case Hir.SpecBehavior spec -> {
                        // Bodies arrive with their helper calls already inlined (the Lower stage,
                        // ADR-0021), and are emitted as-is.
                        SpecImplementation.Implemented implemented =
                                implementations.get(spec.name());
                        if (implemented != null) {
                            // What this emitter takes, said here rather than by the reading that
                            // divided the parameters: an editor reads a definition whose parameters
                            // do not line up and answers what it can about it, and this may not run
                            // on one at all. The division is the same either way; what differs is
                            // who may act on it.
                            if (!implemented.hasCompleteShape()) {
                                throw new IllegalStateException("`" + module.name() + "."
                                        + spec.name() + "` is emitted from an implementation whose"
                                        + " parameters the declaration does not account for");
                            }
                            // a fn-implemented behavior: the $Impl holds the logic, the public interface
                            // (behaviorClass) is what Java code declares (spec §jvm-anonymous-union).
                            out.put(new GeneratedClass.BehaviorImpl(module.name(), spec.name()),
                                    b.generateSpecFn(spec, implemented, requiredNames, requiredSuccess, requiredParam));
                            List<Type> pts = new ArrayList<>();
                            for (Hir.Param p : spec.params()) {
                                pts.add(b.successType(p.type()));
                            }
                            out.put(new GeneratedClass.BehaviorInterface(module.name(), spec.name()),
                                    b.generateBehaviorInterface(spec.name(), pts, b.successType(spec.ret()),
                                            requiredBy(spec)));
                        } else if (unwrittenNames.contains(
                                new ValueName.Behavior(module.name(), spec.name()))) {
                            // Souther's to implement and not written (spec §unwritten-behavior).
                            // The declaration is emitted so its name exists; nothing that would need
                            // the body it has not got is.
                            List<Type> pts = new ArrayList<>();
                            for (Hir.Param p : spec.params()) {
                                pts.add(b.successType(p.type()));
                            }
                            out.put(new GeneratedClass.BehaviorInterface(module.name(), spec.name()),
                                    b.generateUnwrittenBehaviorInterface(spec.name(), pts,
                                            b.successType(spec.ret())));
                        }
                        // else: injection target — its abstract base was generated above (spec §java-base-class)
                    }
                    case Hir.PipeBehavior pipe -> {
                        out.put(new GeneratedClass.BehaviorImpl(module.name(), pipe.name()),
                                b.generatePipe(pipe, composedOf(compositions, named), requiredNames,
                                        sigs, behaviorDeps));
                        Sig sig = declaredSig(module.name(), pipe, sigs);
                        out.put(new GeneratedClass.BehaviorInterface(module.name(), pipe.name()),
                                b.generateBehaviorInterface(pipe.name(), sig.inputTypes(), sig.outputType(),
                                        behaviorDeps.getOrDefault(
                                                new ValueName.Behavior(module.name(), pipe.name()),
                                                List.of())));
                    }
                }
            });
        }
        if (!recHelpers.isEmpty()) {
            // The helpers share one class, so the writer names a method rather than a definition: a
            // method it would not write is the helper whose name it is, and a pool it would not hold
            // belongs to all of them and so to the module.
            try {
                out.put(new GeneratedClass.Helpers(module.name()), b.generateRecursiveHelpers(recHelpers));
            } catch (IllegalArgumentException e) {
                JvmLimits.Exceeded exceeded = JvmLimits.exceeded(e);
                Hir.FnDef helper = exceeded == null ? null : helperNamed(recHelpers, exceeded.method());
                throw helper == null
                        ? asLimit(e, WrittenName.synthetic(module.name(), module.pos()))
                        : asLimit(e, helper.written());
            }
        }
        out.putAll(b.ctx.synthClasses());   // escaping lambdas compiled to Fn classes (spec §blocks)
        // Every site the plan numbered has to be in the bytecode. A missing arm comes back as an arm
        // no row goes through and a missing comparison as a line no row reached, and both read as the
        // model being short of rows rather than the measurement being short of probes. A body the
        // emitter walks without counting is how that happens, and it is silent at the site — so it is
        // caught here, where the plan and what was emitted can be compared.
        List<Integer> missed = b.ctx.plannedButNotEmitted();
        if (!missed.isEmpty()) {
            throw new IllegalStateException("the plan numbered " + missed.size()
                    + " site(s) that nothing emitted: " + missed
                    + "; a body was walked without counting what it holds");
        }
        return out;
    }

    /**
     * Emits one definition, saying what the class file writer would not hold as that definition.
     *
     * <p>How long a method is and how many constants a class refers to are not known before it is
     * written, so unlike an argument count they cannot be checked at the declaration. What the writer
     * says names its own rule and the method it was writing, and the author has no way back from
     * either to what they wrote — so it is said here, where the definition being emitted is in hand.
     * A refusal that names no limit is not this compiler's to answer for and goes on unchanged.
     */
    private static void emitting(WrittenName written, Runnable emit) {
        try {
            emit.run();
        } catch (IllegalArgumentException e) {
            throw asLimit(e, written);
        }
    }

    /** The refusal as the diagnostic for {@code name}, or unchanged where it names no limit this
     *  compiler answers for. */
    private static RuntimeException asLimit(IllegalArgumentException e, WrittenName written) {
        JvmLimits.Exceeded exceeded = JvmLimits.exceeded(e);
        return exceeded == null ? e : JvmLimits.tooLarge(exceeded, written);
    }

    /** The helper emitted as {@code method} on {@code $Fns}, or null if the name is not one of
     *  theirs. */
    private static Hir.FnDef helperNamed(Map<String, Hir.FnDef> helpers, String method) {
        if (method == null) {
            return null;
        }
        for (Hir.FnDef helper : helpers.values()) {
            if (CodegenContext.helperMethod(helper.name()).equals(method)) {
                return helper;
            }
        }
        return null;
    }

    /** The {@code $Fns} method a definition is emitted as, for a caller outside this package: an
     * {@code example} run looks its operand's method up by name, and the name is decided in one
     * place. */
    public static String helperMethod(String helper) {
        return CodegenContext.helperMethod(helper);
    }

    /** The {@code $Ctfe} method checking the clause declared {@code i}th, for a caller outside this
     * package: the compile-time check of a constant construction asks one clause at a time, so that a
     * constant the compiler rejects names the rule it broke. */
    public static String clauseCheck(int index) {
        return ValueClassGen.ctfeClauseCheck(index);
    }

    /**
     * Emits the module's recursive helpers as {@code static} methods on a package-private {@code $Fns}
     * class (spec §fn-declaration). Each helper's declared parameter and return types are boxed as {@code Object}
     * across the method boundary, unboxed on entry and boxed on return, so a self- or mutual call is a
     * plain {@code invokestatic} — the recursion the inliner cannot express. The body is emitted through
     * the same {@code emitBodyTail} path a behavior uses; a helper is pure, so it has no injected fields.
     */
    private byte[] generateRecursiveHelpers(Map<String, Hir.FnDef> helpers) {
        ClassDesc cdFns = ctx.cd(new GeneratedClass.Helpers(pkg));
        return build(cdFns, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);   // package-private, not exposed
            for (Hir.FnDef h : helpers.values()) {
                int n = h.params().size();
                ClassDesc[] params = new ClassDesc[n];
                java.util.Arrays.fill(params, CD_Object);
                MethodTypeDesc desc = MethodTypeDesc.of(CD_Object, params);
                String called = CodegenContext.helperMethod(h.name());
                // Where the depth is counted, what every caller reaches is the counting wrapper and the
                // body moves aside. A recursive call is an `invokestatic` of the called name (a tail
                // call to the same helper loops instead and adds no frame), so putting the counting
                // there is what makes a recursion count itself however it is reached — from a row, from
                // a fixture's helper, or from another helper in its group.
                String emitted = ctx.counting() ? called + "$body" : called;
                if (ctx.counting()) {
                    cb.withMethodBody(called, desc, ClassFile.ACC_STATIC,
                            code -> emitDepthCounted(code, cdFns, emitted, desc, n));
                }
                cb.withMethodBody(emitted, desc, ClassFile.ACC_STATIC,
                        code -> {
                    BodyGen gen = new BodyGen(ctx, code, null, cdFns, n);
                    for (int i = 0; i < n; i++) {
                        // a function parameter arrives as an Fn value (a closure); every other parameter
                        // as its boxed value. resolveParamType handles both shapes.
                        Type pt = TypeOps.resolveParamType(h.params().get(i).type());
                        code.aload(i);
                        int slot = gen.slot(pt);
                        unbox(code, pt, slot);
                        gen.bind(h.params().get(i).binder().binding(), h.params().get(i).binder().name(),
                                slot, pt);
                    }
                    // A tail-position call to this same helper loops back here instead of recursing,
                    // so a self-tail-recursive helper runs in constant stack.
                    gen.beginSelfRecursion(h.name(), h.params());
                    // a recursive helper declares its return type; thread it so a tail-position fold
                    // over an empty seed materialises its step at the declared type, not a bottom (#70)
                    Type helperReturn = h.declaredReturn() == null ? null : successType(h.declaredReturn());
                    gen.emitTail(elaborated(checked.emittedHelpers(), h.name()),
                            cdFns, Set.of(), Map.of(), helperReturn);
                });
            }
        });
    }

    /**
     * The helper as its callers reach it while an evaluation is counting: one frame counted in, the
     * body applied, and the frame counted out however the body leaves.
     *
     * <p>A wrapper rather than counting inside the body, because leaving has to be counted on every
     * path out and the body has many — every arm of every {@code match} returns on its own. One
     * catch-all handler around one call says the same thing once, and says it for the exceptional path
     * as well, which is the path that matters: a row is given up on by an exception thrown from inside
     * the code, so the depth of a helper it was inside has to come back down as that leaves.
     *
     * <p>Counting the depth is not the same as counting the steps, and neither stands in for the
     * other. A recursion that goes deep in few steps is stopped by this before the stack it runs on
     * runs out, which is what stops how deep it may go from being an answer the JVM gives.
     */
    private static void emitDepthCounted(CodeBuilder code, ClassDesc owner, String body,
                                         MethodTypeDesc desc, int arity) {
        Label from = code.newLabel();
        Label to = code.newLabel();
        Label unwinding = code.newLabel();
        code.invokestatic(CD_EvaluationContext, "enter", MTD_EvaluationContext_count);
        code.labelBinding(from);
        for (int i = 0; i < arity; i++) {
            code.aload(i);
        }
        code.invokestatic(owner, body, desc);
        code.labelBinding(to);
        code.invokestatic(CD_EvaluationContext, "leave", MTD_EvaluationContext_count);
        code.areturn();
        code.exceptionCatchAll(from, to, unwinding);
        code.labelBinding(unwinding);
        code.astore(arity);
        code.invokestatic(CD_EvaluationContext, "leave", MTD_EvaluationContext_count);
        code.aload(arity);
        code.athrow();
    }

    /** Emits injected required-behavior fields plus the matching constructor (or a no-arg ctor) on a
     * behavior's {@code $Impl}. The {@code of()}/{@code bind()} factories live on the public interface
     * ({@link #emitBehaviorFactory}), not here. */
    private void emitInjection(ClassBuilder cb, ClassDesc cdX, InjectionSlots held) {
        if (held.isEmpty()) {
            emitPublicCtor(cb);
            return;
        }
        for (InjectionSlots.Slot slot : held.all()) {
            // a multi-input required behavior is stored as its own base class (invokevirtual), not the
            // unary Behavior — see CodegenContext.requiredFieldType (issue #57)
            cb.withField(slot.fieldName(), slot.type(), ClassFile.ACC_FINAL);
        }
        ClassDesc[] params = new ClassDesc[held.all().size()];
        for (int i = 0; i < params.length; i++) {
            params[i] = held.all().get(i).type();
        }
        MethodTypeDesc ctorDesc = MethodTypeDesc.of(ConstantDescs.CD_void, params);
        cb.withMethodBody("<init>", ctorDesc, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0);
            code.invokespecial(CD_Object, "<init>", MTD_void);
            for (int i = 0; i < held.all().size(); i++) {
                InjectionSlots.Slot slot = held.all().get(i);
                code.aload(0);
                code.aload(i + 1);
                code.putfield(cdX, slot.fieldName(), slot.type());
            }
            code.return_();
        });
    }

    /**
     * Emits the static factory a Java caller uses to build a fn/pipe behavior, on its public
     * interface (spec §jvm-behavior). {@code of()} for a behavior with no {@code depends on}; {@code bind(<named
     * required interfaces>)} for one that injects dependencies. Both return the interface type and
     * construct the {@code $Impl}, so the caller never names the implementation class.
     */
    private void emitBehaviorFactory(ClassBuilder cb, ClassDesc cdI, ClassDesc cdImpl,
                                     List<ValueName.Behavior> requireds) {
        if (requireds.isEmpty()) {
            cb.withMethodBody("of", MethodTypeDesc.of(cdI),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                        code.new_(cdImpl);
                        code.dup();
                        code.invokespecial(cdImpl, "<init>", MTD_void);
                        code.areturn();
                    });
            return;
        }
        ClassDesc[] bindParams = new ClassDesc[requireds.size()];
        ClassDesc[] ctorParams = new ClassDesc[requireds.size()];
        for (int i = 0; i < requireds.size(); i++) {
            bindParams[i] = cdBehavior(requireds.get(i));   // the required's public interface / base
            ctorParams[i] = ctx.requiredFieldType(requireds.get(i));
        }
        MethodTypeDesc ctorDesc = MethodTypeDesc.of(ConstantDescs.CD_void, ctorParams);
        cb.withMethodBody("bind", MethodTypeDesc.of(cdI, bindParams),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    code.new_(cdImpl);
                    code.dup();
                    for (int i = 0; i < requireds.size(); i++) {
                        code.aload(i);
                    }
                    code.invokespecial(cdImpl, "<init>", ctorDesc);
                    code.areturn();
                });
    }

    /**
     * Generates the abstract base class for a required behavior (spec §java-base-class): an abstract
     * {@code Behavior} that a Java implementation extends. The base exposes a {@code protected}
     * factory for what the implementation may build (spec §closed-construction). The two kinds are sourced differently.
     * A unit output case gets a no-arg factory: a unit has no invariant to validate, so it is built
     * directly, and it is taken from the output cases (an injected behavior may leave {@code constructs}
     * implicit, and a unit is safe to hand out either way). A field-bearing type gets a typed factory
     * built through its {@code __construct} so the invariant is checked, but only when the behavior
     * declares it in {@code constructs}: that declaration is the authority to build it (spec §asymmetric-interop), and
     * unlike a unit it cannot be told apart from a decoded pass-through output by shape alone. The typed
     * factory lets the implementation compose already-held values into its declared output without
     * round-tripping through the decoder. The data constructors stay non-public, so a subclass builds
     * exactly these and nothing else, from any package.
     *
     * <p>When both the input and output map to a concrete reference type, the base carries a generic {@code
     * Behavior<In, Out>} signature (spec §jvm-anonymous-union, §jvm-behavior) — {@code Out} is the {@code
     * <名>Result} interface for an anonymous union output — so a Java author writes the real return type
     * rather than {@code Object}. If either side is a list/option/map (no single reference class), the
     * signature is omitted and the raw interface stands.
     */
    private byte[] generateRequiredBase(String name, List<TypeSymbol> unitCases, List<TypeSymbol> dataConstructs,
                                        List<Type> paramTypes, Type retType) {
        ClassDesc cdR = cdBehavior(name);
        // Injected-vs-unary is orthogonal to composition: one input is the unary Behavior<In,Out> (so
        // it can follow an arrow); any other count is a standalone abstract class with a typed
        // apply(A,B,...) — a first stage but never a later one — the same param-count branch a fn
        // behavior takes in generateBehaviorInterface. A `() -> R` produces rather than transforms:
        // there is no input to hand it, so it declares `apply()` instead of taking a value it ignores.
        boolean single = paramTypes.size() == 1;
        return build(cdR, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT | ClassFile.ACC_SUPER);
            if (single) {
                withBehaviorSignature(cb, paramTypes.get(0), retType, name);
                cb.withInterfaceSymbols(CD_Behavior);
            } else {
                emitAbstractApply(cb, name, paramTypes, retType);
            }
            // protected no-arg ctor so subclasses in any package can call super()
            cb.withMethodBody("<init>", MTD_void, ClassFile.ACC_PROTECTED, code -> {
                code.aload(0);
                code.invokespecial(CD_Object, "<init>", MTD_void);
                code.return_();
            });
            for (TypeSymbol caseName : unitCases) {
                emitUnitFactory(cb, caseName);
            }
            for (TypeSymbol construct : dataConstructs) {   // a field-bearing data or a newtype
                emitDataFactory(cb, construct);
            }
        });
    }

    /** A no-arg factory for a unit case: the type has exactly one value, so it hands that out. */
    private void emitUnitFactory(ClassBuilder cb, TypeSymbol typeName) {
        ClassDesc caseCd = ctx.cd(typeName);
        cb.withMethodBody(typeName.name(), MethodTypeDesc.of(caseCd),
                ClassFile.ACC_PROTECTED | ClassFile.ACC_FINAL, code -> {
                    loadSharedInstance(code, caseCd);
                    code.areturn();
                });
    }

    /** A factory taking the data's fields (in declaration order) and building it through
     * {@code __construct}, so the invariant is checked and a violation aborts (spec §algebraic-types) — the same
     * path an in-domain construction takes, not a decode of an external representation. */
    private void emitDataFactory(ClassBuilder cb, TypeSymbol construct) {
        // The type as the `constructs` clause resolved it: an entry there may name a type another
        // module declares, and the class of one is that module's.
        Hir.Data data = (Hir.Data) symbols.declaredNode(construct);
        ClassDesc cdType = ctx.cd(construct);
        Map<String, Type> fields = ctx.fieldTypes(data);
        ClassDesc[] fieldDs = fieldDescs(fields, ctx);
        String sig = factorySignature(fields, cdType);
        cb.withMethod(data.name(), MethodTypeDesc.of(cdType, fieldDs),
                ClassFile.ACC_PROTECTED | ClassFile.ACC_FINAL, mb -> {
                    if (sig != null) mb.with(SignatureAttribute.of(MethodSignature.parseFrom(sig)));
                    mb.withCode(code -> {
                        int slot = 1;   // slot 0 is `this`
                        for (Type t : fields.values()) {
                            load(code, slot, t);
                            slot += width(t);
                        }
                        code.invokestatic(cdType, "__construct", MethodTypeDesc.of(CD_Result, fieldDs));
                        code.invokestatic(CD_ConstraintViolation, "orThrow", MTD_orThrow);
                        code.checkcast(cdType);
                        code.areturn();
                    });
                });
    }

    /** A generic method {@code Signature} for a factory whose fields include a container
     * (List/Set/Map/Option), else {@code null}. Mirrors the value-class accessor signature (spec §field-visibility)
     * so a Java caller passes {@code List<Line>} rather than a raw {@code List}. A non-container field
     * keeps its plain descriptor; the signature erases to the method's descriptor either way. */
    private String factorySignature(Map<String, Type> fields, ClassDesc ret) {
        boolean anyContainer = false;
        StringBuilder sb = new StringBuilder("(");
        for (Type t : fields.values()) {
            String g = JvmTypes.genericSig(t, ctx);
            if (g != null) {
                anyContainer = true;
                sb.append(g);
            } else {
                sb.append(jvmType(t, ctx).descriptorString());
            }
        }
        sb.append(")").append(ret.descriptorString());
        return anyContainer ? sb.toString() : null;
    }

    /**
     * Adds the source-level {@code Behavior<I, O>} signature to a generated single-input
     * behavior. Its erased JVM {@code apply} descriptor remains {@code Object -> Object}, but
     * Java callers then receive the declared input and outcome types without a cast.
     */
    private void withBehaviorSignature(ClassBuilder cb, Type input, Type output, String behaviorName) {
        // A collection In/Out keeps its element type (sigRefOrNull), so it no longer suppresses the
        // whole Behavior<In,Out> signature — only a truly erased type (var/tuple/fn) does (issue #57).
        String inSig = ctx.sigRefOrNull(input, own(behaviorName));
        String outSig = ctx.sigRefOrNull(output, own(behaviorName));
        if (inSig == null || outSig == null) {
            return;
        }
        String beh = CD_Behavior.descriptorString();
        beh = beh.substring(0, beh.length() - 1); // drop trailing ';' to insert type args
        String sig = CD_Object.descriptorString() + beh + "<" + inSig + outSig + ">;";
        cb.with(SignatureAttribute.of(ClassSignature.parseFrom(sig)));
    }

    /** Declares the abstract, typed multi-argument {@code apply(A,B,…)} of a multi-input behavior
     * (a fn interface or an injected base), with a generic {@code Signature} when a param/return is a
     * collection (issue #57). */
    private void emitAbstractApply(ClassBuilder cb, String name, List<Type> paramTypes, Type retType) {
        String sig = ctx.applySignatureOrNull(own(name), paramTypes, retType);
        cb.withMethod("apply", ctx.typedApplyDesc(own(name), paramTypes, retType),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, mb -> {
                    if (sig != null) {
                        mb.with(SignatureAttribute.of(MethodSignature.parseFrom(sig)));
                    }
                });
    }

    /**
     * The public interface a Java caller declares for a fn/pipe behavior (spec §jvm-anonymous-union). It hides the
     * generated {@code <名>Result} union: the caller writes the behavior name and switches over the
     * cases. A single-input behavior's interface {@code extends Behavior<In, Out>} — so it composes
     * with {@code >->} and its {@code apply} return type is typed by inheritance; a multi-input one is
     * a standalone functional interface declaring a typed, multi-argument {@code apply}. Both carry the
     * static {@code of()}/{@code bind(...)} factory that builds the {@code $Impl}.
     */
    private byte[] generateBehaviorInterface(String name, List<Type> paramTypes, Type retType,
                                             List<ValueName.Behavior> dependsOn) {
        return behaviorInterface(name, paramTypes, retType, dependsOn);
    }

    /**
     * The same interface for a behavior Souther is to implement and nobody has (spec
     * §unwritten-behavior).
     *
     * <p>The name exists, so a module that imports it reads a declaration and a report can say what
     * it owes. What it does not carry is the {@code of()}/{@code bind(...)} factory: there is no
     * {@code $Impl} for it to build, and a factory naming a class nothing emitted is a link error
     * held until a caller reaches it. It is not the abstract base either — that base is what a Java
     * implementation extends, and this behavior's implementation is Souther's to write.
     */
    private byte[] generateUnwrittenBehaviorInterface(String name, List<Type> paramTypes,
                                                      Type retType) {
        return behaviorInterface(name, paramTypes, retType, null);
    }

    /** {@code dependsOn} null where there is no implementation to build, so no factory is written. */
    private byte[] behaviorInterface(String name, List<Type> paramTypes, Type retType,
                                     List<ValueName.Behavior> dependsOn) {
        ClassDesc cdI = cdBehavior(name);
        ClassDesc cdImpl = cdBehaviorImpl(name);
        boolean single = paramTypes.size() == 1;
        return build(cdI, cb -> {
            cb.withFlags(pub(name) | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            if (single) {
                cb.withInterfaceSymbols(CD_Behavior);   // extends Behavior<In, Out> (raw if untyped)
                withBehaviorSignature(cb, paramTypes.getFirst(), retType, name);
            } else {
                // no Behavior supertype (it takes one argument): declare the typed apply directly
                emitAbstractApply(cb, name, paramTypes, retType);
            }
            if (dependsOn != null) {
                emitBehaviorFactory(cb, cdI, cdImpl, dependsOn);
            }
        });
    }

    private MethodTypeDesc typedApplyDesc(String name, List<Type> paramTypes, Type retType) {
        return ctx.typedApplyDesc(own(name), paramTypes, retType);
    }

    /** Emits the covariant bridge that satisfies a multi-input interface's typed apply by delegating
     * to the erased {@code apply(Object...)Object} the body lives on. Skipped when the typed and
     * erased descriptors coincide (every param and the return degraded to {@code Object}), because
     * then the erased apply already implements the interface method. */
    private void emitTypedApplyBridge(ClassBuilder cb, ClassDesc cdImpl, MethodTypeDesc typed) {
        int n = typed.parameterCount();
        ClassDesc[] erasedParams = new ClassDesc[n];
        java.util.Arrays.fill(erasedParams, CD_Object);
        MethodTypeDesc erased = MethodTypeDesc.of(CD_Object, erasedParams);
        if (typed.equals(erased)) {
            return;
        }
        cb.withMethodBody("apply", typed, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0);
            for (int i = 0; i < n; i++) {
                code.aload(i + 1);
            }
            code.invokevirtual(cdImpl, "apply", erased);
            ClassDesc ret = typed.returnType();
            if (!ret.equals(CD_Object)) {
                code.checkcast(ret);
            }
            code.areturn();
        });
    }

    /**
     * A bridge case takes a class name in this module (spec §jvm-anonymous-union), so it is subject to the same rule
     * as every other name this module emits: no two of them may be one class. {@code YenCase} is a
     * name a model may well have declared, and {@code IntCase} / {@code DateCase} the same, so this
     * is reported rather than reserved — the collision is decided by what the module holds, and a
     * name nothing collides with stays available.
     *
     * <p>Three ways to collide: with a data this module declares, with the class a behavior
     * capitalizes into (spec §jvm-behavior), and with another bridge case. The last is two members of one
     * spelling from two modules — refused within a union by the member-name rule, and reaching here
     * when they are members of two different unions of this module.
     */
    private void rejectBridgeCaseCollisions(Hir.Module module,
                                            Map<TypeSymbol, List<GeneratedClass.BehaviorResult>> bridgeCases,
                                            Map<JvmClassName, Hir.Def> localTypes,
                                            Map<JvmClassName, String> behaviorClassOwner) {
        Map<JvmClassName, TypeSymbol> byBridgeName = new LinkedHashMap<>();
        for (Map.Entry<TypeSymbol, List<GeneratedClass.BehaviorResult>> e : bridgeCases.entrySet()) {
            TypeSymbol member = e.getKey();
            JvmClassName cls = SoutherJvmAbi.nameOf(new GeneratedClass.BridgeCase(module.name(), member));
            String bridge = cls.classDesc().displayName();
            Hir.BehaviorDef owner = behaviorNamed(module, e.getValue().get(0).behavior());
            SourcePos pos = owner.pos();
            String what = owner.name();
            TypeSymbol sameName = byBridgeName.put(cls, member);
            if (sameName != null) {
                // Each written as the identity it is: a module's with its module, and a primitive
                // as itself. Which is what tells the two members apart, and what a reader has to
                // see to know which pair collided — the class name they share is the third part.
                // Not `Type.show`, which writes a simple name and would print one name twice.
                throw CompileException.of(Diagnostic.say(new ModuleMessage.TwoMembersJoinThroughOneCaseClass(String.valueOf(sameName), String.valueOf(member), bridge))
                                .at(pos)
                                .hint(new ModuleMessage.AMemberGoesByItsOwnNameWithCaseAfterIt()).build());
            }
            Hir.Def aData = localTypes.get(cls);
            String aBehavior = behaviorClassOwner.get(cls);
            if (aData != null || aBehavior != null) {
                String other = aData != null ? aData.name() : aBehavior;
                throw CompileException.of(Diagnostic.at(pos)
                                .say(aData != null
                                        ? new ModuleMessage.AMemberReachesTheUnionThroughAData(what,
                                                member.name(), bridge, other)
                                        : new ModuleMessage
                                                .AMemberReachesTheUnionThroughABehavior(what,
                                                        member.name(), bridge, other))
                                .hint(new ModuleMessage.RenameTheMemberOrTheTypeItCollidesWith(bridge)).build());
            }
        }
    }

    /**
     * A behavior with a union output is generated as a sealed interface named after it, so that name
     * is subject to the rule every other name this module emits is subject to: no two of them may be
     * one class. Two ways to collide — with a data this module declares, and with the class another
     * behavior capitalizes into. Two result unions cannot collide with each other, their behaviors
     * having already been rejected for capitalizing into one class.
     */
    private void rejectResultUnionCollisions(Hir.Module module,
                                             Map<GeneratedClass.BehaviorResult, Boundary.Alternatives> behaviorResults,
                                             Map<JvmClassName, Hir.Def> localTypes,
                                             Map<JvmClassName, String> behaviorClassOwner) {
        for (GeneratedClass.BehaviorResult union : behaviorResults.keySet()) {
            JvmClassName cls = SoutherJvmAbi.nameOf(union);
            String resultName = cls.classDesc().displayName();
            Hir.BehaviorDef owner = behaviorNamed(module, union.behavior());
            SourcePos pos = owner.pos();
            String what = owner.name();
            if (localTypes.containsKey(cls)) {
                throw CompileException.of(Diagnostic
                                .at(pos)
                                .hint(new BehaviorMessage.AUnionOutputReachesJavaThroughThatName(resultName)).say(new BehaviorMessage.AUnionOutputsInterfaceCollidesWithAData(what, resultName)).build());
            }
            String behavior = behaviorClassOwner.get(cls);
            if (behavior != null) {
                throw CompileException.of(Diagnostic
                                .at(pos)
                                .hint(new BehaviorMessage.AUnionOutputReachesJavaThroughThatName(resultName)).say(new BehaviorMessage.AUnionOutputsInterfaceCollidesWithABehavior(what, resultName, behavior)).build());
            }
        }
    }

    /** The behavior of this module written under {@code name}. Every result union and every bridge
     *  case reaching here was built from one of these, so there is always one. */
    private Hir.BehaviorDef behaviorNamed(Hir.Module module, String name) {
        for (Hir.BehaviorDef bd : module.behaviors()) {
            if (bd.name().equals(name)) {
                return bd;
            }
        }
        throw new IllegalStateException("no behavior named " + name + " in " + module.name());
    }

    /** The result union of each behavior that has one, keyed by the behavior — which is what a result
     *  union is identified by. Keying by what the union is called would mean reading the behavior back
     *  out of the spelling to find whose it is. */
    private Map<GeneratedClass.BehaviorResult, Boundary.Alternatives> behaviorResultInterfaces(Hir.Module module,
                                                                 Map<ValueName.Behavior, Sig> sigs) {
        Map<GeneratedClass.BehaviorResult, Boundary.Alternatives> results = new LinkedHashMap<>();
        for (Hir.BehaviorDef bd : module.behaviors()) {
            Sig sig = sigs.get(new ValueName.Behavior(module.name(), bd.name()));
            if (sig == null || !(sig.outputType() instanceof Type.Union)) {
                continue;
            }
            // How a union's answer is written is settled once here and handed to the interface, the
            // encoder and the bridge cases, so none of them is in a position to work the form or a
            // tag out again.
            results.put(new GeneratedClass.BehaviorResult(module.name(), bd.name()),
                    Boundary.of(sig.outputType(), ctx.kinds, ctx.published));
        }
        return results;
    }

    /**
     * Generates the sealed interface for a behavior's anonymous union output (spec §jvm-anonymous-union). A member
     * this module declared is permitted as itself; any other is permitted as its bridge case. The
     * interface carries the union's {@code encoder()}: a Java consumer that switches reads a case
     * and uses that case's own codec, and one that wants the answer as it crosses a boundary asks
     * the union, which writes the discriminator no member writes on itself.
     */
    private byte[] generateBehaviorResult(GeneratedClass.BehaviorResult union,
                                          Boundary.Alternatives alternatives) {
        ClassDesc cdR = ctx.cd(union);
        List<ClassDesc> caseCds = new ArrayList<>();
        for (TypeSymbol member : alternatives.atoms()) {
            caseCds.add(ctx.resultMemberClass(member));
        }
        return build(cdR, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            cb.with(PermittedSubclassesAttribute.ofSymbols(caseCds));
            codec.emitResultUnionEncoderFactory(cb, union, alternatives);
        });
    }


    private Type successType(Hir.RetType ret) {
        return ctx.successType(ret);
    }

    /**
     * The version of what another module reaches across the boundary into a compiled one.
     *
     * <p>Two things are under it. One is what this package emits: the {@code __construct} descriptors
     * and visibilities, the codecs, a behavior's class and its methods, an output union's case names,
     * and the runtime types in those signatures. A change to any of it makes a previously built jar
     * unusable and has to move this number with it; a change confined to the inside of a generated
     * method does not touch it.
     *
     * <p>The other is the source a jar carries for a reader to compile: a data's invariant, and the
     * body of every value and helper the module publishes ({@link
     * souther.compiler.meta.ModuleMetadata}). Those are read back by whichever compiler imports the
     * module, so what the front end makes of them is a promise the jar carries too — a change to how
     * one of those bodies is read moves this number as surely as a change to a descriptor does, and
     * it is the front end's change rather than this package's. {@link
     * souther.compiler.meta.ModuleReadback} refuses a jar that disagrees, so the disagreement is
     * reported as what it is instead of surfacing as an unresolved name inside a body nobody wrote.
     *
     * <p>A third thing arrived with version 6: what a published helper's declaration promises. A helper
     * written without {@code partial} carries the termination guarantee for everything it reaches (spec
     * §fn-rules), so a reader answers the question off the word on the declaration and does not walk the
     * closure behind it. That only holds if the writer enforced the rule, which is why an older jar —
     * whose unmarked helper may reach a {@code partial} one — is refused rather than read.
     *
     * <p>Version 7 narrows what a published behavior's signature may be: an optional does not stand
     * anywhere in a behavior's boundary shape (spec §external-representation). A reader takes an
     * imported declaration as one the writer was held to, so a jar built when a parameter could be
     * written {@code Option<Int>} carries a signature this compiler would refuse, and calling it would
     * put the runtime's {@code Option} across a boundary that no longer admits one.
     *
     * <p>Version 8 narrows it again: a named type standing in a behavior's boundary is one a model
     * declares, so the vocabulary the language keeps for its own operations — what a division by zero
     * answers with, what a rounding takes, the reserved {@code Raw} — is not carried across one. A
     * module written without an {@code exposing} line publishes every behavior it declares, so a jar
     * built before this carries a public {@code Behavior<souther.runtime.DivisionByZero, …>} that this
     * compiler refuses to write.
     *
     * <p>Version 9 asks those rules of every signature rather than of every declaration. Admitting
     * what a boundary carries is now the making of the signature, so a composition's merged output is
     * subject to it — a case that retired out of a stage's answer is a name in what crosses like any
     * other — and so is a declaration read back from a jar. A jar built before this was trusted for
     * both: what its compiler checked was what its author wrote, and a composition it published, or a
     * declaration a reader takes on faith, was never asked.
     *
     * <p>Version 10 moves where the {@code "value"} envelope is written. A derived codec is now the
     * standalone representation of its type, and a sum's encoding adds what membership in that sum
     * requires (spec §sum-discrimination); before, a newtype that some sum listed had the envelope
     * written into its own codec, so what the type published depended on a declaration elsewhere. A
     * jar built before this carries a {@code $Enc} that writes {@code {"value": …}} and a
     * {@code $Dec} that demands it, which this compiler's sum encoder would wrap a second time and
     * its sum decoder would hand the inner value to.
     *
     * <p>Version 11 narrows what a string literal may be: it ends on the line it began on, and a
     * backslash is read only before {@code n}, {@code t}, {@code r}, a quote and another backslash
     * (spec §string-literal). A published helper's body travels in the jar as source and is read
     * back by the importing compiler (spec §exposed-values), so what a literal may be is part of
     * what a compiled module promises. A jar built before this may carry a body this compiler
     * cannot read — a literal that ran past its line — or one whose backslash the older compiler
     * dropped in silence and this one refuses.
     *
     * <p>Version 12 widens the same thing rather than narrowing it: {@code <-} is not a token, so a
     * {@code <} and the negation after it need no space between them and {@code a<-1} is a body this
     * compiler admits and copies into a jar as written. A reader is held to an equal version and not
     * merely a lower one, which is what makes the widening matter: an older compiler would take the
     * module for one it understands and then fail on a body whose {@code <-} its own lexer still
     * glues into a token no production of its own reads.
     *
     * <p>Version 13 settles what a name is made of: {@code XID_Start XID_Continue*} at Unicode
     * 17.0.0 (spec §identifier). It moves in both directions. A jar built before this may carry a
     * body naming something this compiler will not read — a name holding {@code $}, or beginning
     * with {@code _} — because the older compiler read Java's identifier rule. And a body written
     * now may name something an older compiler cannot read, since a name may hold a character
     * outside the basic plane, which the older one scanned by UTF-16 unit and refused. The same
     * number covers a later Unicode version, which admits names that were not names before.
     *
     * <p>Version 14 narrows what a temporal carries, and widens what the primitives are. A
     * {@code DateTime} is held to the second, so a jar built before this accepts
     * {@code "09:30:45.123"} at a field this compiler's decoder refuses, and a caller reading the
     * older jar's promise would be told a value crossed that no longer does. {@code Time} and
     * {@code Instant} are new primitives, so a jar built now may declare a boundary shape an older
     * compiler has no type for at all.
     *
     * <p>Version 15 widens which types may key a boundary map: a newtype is one exactly when what it
     * wraps is one, so a newtype over any of the four temporals, over an enumeration, or over
     * another such newtype crosses where before only a newtype directly over {@code String} did
     * (spec §collections). A signature is admitted again when it is read back out of a jar, and an
     * older compiler asked about {@code Map<LoanDate, Int>} would refuse a declaration this one
     * publishes.
     *
     * <p>Version 16 widens what a behavior's published declaration may say: it may carry an
     * {@code ensures}, relating what the behavior is given to what it answers. A signature is read
     * back out of a jar as the text that was written, so a declaration this compiler publishes is
     * one an older compiler's reader has no production for and refuses.
     *
     * <p>Version 17 changes what a published class calls the runtime with. A violated {@code ensures}
     * now records two things where it recorded one — the case the broken rule was declared for, and
     * the case the answer turned out to be — so a class emitted before this builds its abort with a
     * constructor that is no longer there. It would reach it only where a clause is already broken,
     * which is the worst place to find a jar and a runtime that were not built together. What a jar
     * declares is unchanged; what moved is what its own classes call.
     *
     * <p>What that number does not cover, and does not need to: where such a clause is checked.
     * Enforcement is not read back — a caller reaches an imported behavior through its signature and
     * its class, and nothing it reads says which of the two places the check was emitted in. A jar
     * published earlier in this same version carries clauses nothing runs, and one published now
     * carries clauses its own module runs, and a reader of either reads the same declaration. It
     * becomes a question the day a caller may assume a clause it did not check itself, and what has
     * to be decided then is which jars an assumption may rest on — the version being one way to
     * answer that, and this note being where the question was left.
     *
     * <p>That a check is emitted is one question and what the emitted code calls is another. The
     * first is not read back and needs no number. The second is not read back either and needs one
     * all the same: a class in a jar runs, and what it calls has to be there when it does.
     */
    public static final int BOUNDARY_VERSION = 18;

    /** Emits the class a module's own declarations are published on, carrying {@code declarations}.
     * What it says is the caller's; that it is built like every other generated class — the same Java
     * floor, the same {@code SourceFile} — is this package's. */
    public static byte[] moduleClass(String moduleName, Annotation declarations) {
        return Descriptors.build(SoutherJvmAbi.nameOf(
                new GeneratedClass.ModuleDeclarations(moduleName)).classDesc(), cb -> cb
                .withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC)
                .with(RuntimeInvisibleAnnotationsAttribute.of(declarations)));
    }

    /**
     * A behavior this module declares, as the declaration it is.
     *
     * <p>The emitter is generating one module, so a bare name it reads off a declaration here is
     * that module's. A name that arrived from somewhere else is carried as a declaration already
     * and is not made into one here.
     */
    private ValueName.Behavior own(String behavior) {
        return new ValueName.Behavior(ctx.pkg, behavior);
    }

    private ClassDesc cdBehavior(String name) {
        return ctx.cdBehavior(own(name));
    }

    private ClassDesc cdBehavior(ValueName.Behavior name) {
        return ctx.cdBehavior(name);
    }

    private ClassDesc cdBehaviorImpl(String name) {
        return ctx.cdBehaviorImpl(own(name));
    }


    private ClassDesc caseClass(TypeSymbol typeName) {
        return ctx.caseClass(typeName);
    }

    // --- sum data (sealed interface) ---

    // --- behaviors ---

    /**
     * Generates a behavior implemented by a {@code fn} (spec §fn-declaration). The behavior's inputs are the
     * {@code apply} arguments; its {@code depends on} are injected fields (§depends-on). The {@code fn}'s
     * leading parameters name the inputs (their types come from the behavior); the trailing ones
     * name the injected behaviors and are resolved as inline calls, not bound as locals.
     */
    private byte[] generateSpecFn(Hir.SpecBehavior spec, SpecImplementation.Implemented implemented,
                                  Set<ValueName.Behavior> requiredNames,
                                  Map<ValueName.Behavior, Type> requiredSuccess,
                                  Map<ValueName.Behavior, List<Type>> requiredParam) {
        ClassDesc cdB = cdBehaviorImpl(spec.name());   // the $Impl behind the public interface
        int n = spec.params().size();
        // declared dependencies, validated to equal what the fn calls (E1602/E1603); the same order is
        // used by pipeline callers (requirementSets), so the injected fields line up.
        InjectionSlots injected = InjectionSlots.of(requiredBy(spec), ctx);
        ClassDesc[] applyParams = new ClassDesc[n];
        for (int i = 0; i < n; i++) {
            applyParams[i] = CD_Object;
        }
        MethodTypeDesc mtdApply = MethodTypeDesc.of(CD_Object, applyParams);
        // Where this behavior's declared relation is checked, decided before the emitter ran. Where
        // it is checked here, the body moves under a name of its own and `apply` becomes the wrapper
        // that runs it and then holds its answer to the contract — so every way in goes through the
        // check, including the typed bridge a multi-input interface is satisfied by.
        EnsuresEnforcement where = checkOf(new ValueName.Behavior(ctx.pkg, spec.name()));
        String bodyMethod = where instanceof EnsuresEnforcement.AtTheCallee ? "apply$body" : "apply";
        int bodyFlags = where instanceof EnsuresEnforcement.AtTheCallee
                ? ClassFile.ACC_PRIVATE : ClassFile.ACC_PUBLIC;
        return build(cdB, cb -> {
            cb.withFlags(pub(spec.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            // implements its public interface (which itself extends Behavior for a single-input one)
            cb.withInterfaceSymbols(cdBehavior(spec.name()));
            emitInjection(cb, cdB, injected);
            if (where instanceof EnsuresEnforcement.AtTheCallee(Contract _)) {
                emitCheckingApply(cb, cdB, spec, mtdApply, n);
            }
            cb.withMethodBody(bodyMethod, mtdApply, bodyFlags, code -> {
                BodyGen gen = new BodyGen(ctx, code, null, cdB, n + 1);
                // The one body a coverage plan is made from, so the one body whose arms are counted.
                gen.armsAreCounted();
                gen.injectsInto(successType(spec.ret()));
                gen.requireds(requiredNames, requiredSuccess, requiredParam, injected);
                for (SpecImplementation.ParameterBinding.AnInput input
                        : implemented.declaredInputs()) {
                    // the definition's input names the binding; its type comes from the behavior,
                    // paired with it where the parameters were divided rather than here
                    Type pt = successType(input.declared().type());
                    code.aload(input.at() + 1);
                    int slot = gen.slot(pt);
                    unbox(code, pt, slot);
                    Hir.Binder binder = input.written().binder();
                    gen.bind(binder.binding(), binder.name(), slot, pt);
                }
                // thread the behavior's declared output so a tail-position fold over an empty seed
                // materialises its step at the output type, not a bottom (issue #70)
                gen.emitTail(elaborated(checked.behaviorBodies(), implemented.definition().name()),
                        cdB, requiredNames, requiredSuccess, successType(spec.ret()));
            });
            if (n != 1) {
                List<Type> pts = new ArrayList<>();
                for (Hir.Param p : spec.params()) {
                    pts.add(successType(p.type()));
                }
                emitTypedApplyBridge(cb, cdB, typedApplyDesc(spec.name(), pts, successType(spec.ret())));
            }
        });
    }


    /**
     * {@code apply}, where the behavior's own body is what has to be held to its declaration.
     *
     * <p>The body keeps its own method and this one calls it. Woven into the body instead, the check
     * would go at each of the places a body returns by — and a body returns from inside a
     * {@code match} arm, from either side of a guard, from a departure — so the one that was missed
     * would be a path that answers without being held to anything, and nothing would say so.
     *
     * <p>What runs is the body, then the check, then the same value the body produced. The answer is
     * saved as it came, and what the check is handed is a copy read back through
     * {@link ResultBoundary#project}: the value the body produced is a member of the behavior's
     * declared union, and a member this module did not declare is carried in a bridge case. A rule
     * is written about the answer and not about how the boundary carries it, so it is read out of the
     * carrier first and the saved value — the one the caller is owed — is what is returned.
     *
     * <p>Nothing about how the body runs changes. It is still an instance method of this class, so
     * the injected dependencies it reads are the same fields it read before; it runs to completion
     * before anything here looks at its answer, so nothing is reordered around it; and a behavior
     * cannot call itself ({@link SpecChecker#checkBehaviorsDoNotRecurse}), so there is no path where
     * this wrapper stands between a behavior and its own recursion.
     */
    private void emitCheckingApply(ClassBuilder cb, ClassDesc cdB, Hir.SpecBehavior spec,
                                   MethodTypeDesc mtdApply, int n) {
        ClassDesc cdEnsures = ctx.cd(new GeneratedClass.Ensures(
                new GeneratedClass.BehaviorInterface(ctx.pkg, spec.name())));
        List<TypeSymbol> bridged = ctx.bridgedMembers(successType(spec.ret()));
        List<ClassDesc> checkParams = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            checkParams.add(CD_Object);
        }
        MethodTypeDesc mtdCheck =
                MethodTypeDesc.of(ConstantDescs.CD_void, checkParams.toArray(new ClassDesc[0]));
        cb.withMethodBody("apply", mtdApply, ClassFile.ACC_PUBLIC, code -> {
            int answered = n + 1;    // this=0, the arguments are 1..n
            int carrier = n + 2;
            code.aload(0);
            for (int i = 0; i < n; i++) {
                code.aload(i + 1);
            }
            code.invokespecial(cdB, "apply$body", mtdApply);
            code.astore(answered);
            code.aload(answered);
            ResultBoundary.project(code, ctx, new ValueName.Behavior(ctx.pkg, spec.name()),
                    bridged, carrier);
            code.astore(carrier);
            for (int i = 0; i < n; i++) {
                code.aload(i + 1);
            }
            code.aload(carrier);
            code.invokestatic(cdEnsures, "check", mtdCheck);
            code.aload(answered);
            code.areturn();
        });
    }

    /**
     * A composition's own signature, worked out when the module's signatures were.
     *
     * <p>A composition resting on a stage that names nothing has none — it was abandoned there. A
     * module holding one is not emitted, so reaching this with no signature means the gate that
     * decides let something through that has no meaning, and there is nothing to emit for it.
     */
    private static Sig declaredSig(String module, Hir.PipeBehavior pipe,
                                   Map<ValueName.Behavior, Sig> sigs) {
        Sig sig = sigs.get(new ValueName.Behavior(module, pipe.name()));
        if (sig == null) {
            throw new IllegalStateException("`" + pipe.name() + "` reached codegen with no signature,"
                    + " at " + pipe.pos());
        }
        return sig;
    }

    /**
     * The routing settled for {@code named}.
     *
     * <p>A composition that reached codegen was checked, and a checked composition has one. Missing
     * means the two sets have come apart — a behavior emitted as a composition that the checker
     * never walked — which is not something to carry on past.
     */
    private static Composition composedOf(Map<ValueName.Behavior, Composition> compositions,
                                          ValueName.Behavior named) {
        Composition composed = compositions.get(named);
        if (composed == null) {
            throw new IllegalStateException("`" + named.name() + "` reached codegen as a"
                    + " composition with no routing settled for it");
        }
        return composed;
    }

    /** The behaviors a spec declares it depends on, by the name each is reached by. */
    private static List<ValueName.Behavior> requiredBy(Hir.SpecBehavior spec) {
        List<ValueName.Behavior> names = new ArrayList<>();
        for (Hir.Var req : spec.dependsOn()) {
            ValueName.Behavior reached = reachedBy(req);
            if (reached != null) {
                names.add(reached);
            }
        }
        return names;
    }

    /**
     * The declaration a type name written in a module being generated names.
     *
     * <p>Answered for the same reason {@link #reachedBy} is: what reaches the backend is an
     * elaboration {@code Bodies.Checked} handed over, and it hands one over only where
     * {@code Names.Sound} holds of the module — which resolution makes false as soon as it reports
     * a name denoting nothing.
     */
    static TypeSymbol names(Hir.Name name) {
        return switch (name) {
            case Hir.Name.Denoting d -> d.type();
            case Hir.Name.Unanswered u -> throw u.unexpectedHere();
        };
    }

    /**
     * The name a dependency or a pipeline stage is reached by.
     *
     * <p>Every name in a module reaching the backend was answered. {@code Bodies.Checked} hands over
     * an elaboration only where {@code Names.Sound} holds of the module, and that is false as soon
     * as resolution reports a name denoting nothing; {@code Output.Classes} builds what it generates
     * from that answer and from nothing else. So one arriving here is not a mistake in the source —
     * it is this module being emitted with a hole in it, which is the thing that gate is for.
     */
    private static ValueName.Behavior reachedBy(Hir.Var named) {
        return switch (named) {
            case Hir.Var.Denoting d -> d.denotes() instanceof ValueName.Behavior behavior
                    ? behavior : null;
            case Hir.Var.Unanswered u -> throw u.unexpectedHere();
        };
    }

    /**
     * The class behind a composed behavior: what {@code composed} says the composition does, in
     * bytecode.
     *
     * <p>Nothing here decides what a stage is offered. Which cases reach a stage, and what runs on
     * after it, is a fact about the Souther program and was settled where the composition was
     * checked; this walks the answer. A second walk of the declaration lived here, and what it
     * derived had to agree with what the composition's own signature was built from — two
     * derivations of one rule, one of them in a backend.
     */
    private byte[] generatePipe(Hir.PipeBehavior pipe, Composition composed,
                                Set<ValueName.Behavior> requiredNames,
                                Map<ValueName.Behavior, Sig> sigs,
                                Map<ValueName.Behavior, List<ValueName.Behavior>> behaviorDeps) {
        ClassDesc cdP = cdBehaviorImpl(pipe.name());   // the $Impl behind the public interface
        // the pipeline's injected fields are the union of its stages' requirements (spec
        // §composition-with-requirements)
        InjectionSlots reqStages =
                InjectionSlots.of(behaviorDeps.getOrDefault(own(pipe.name()), List.of()), ctx);
        // the pipeline takes whatever its first stage takes (spec §sequential-composition), which is
        // what its own signature was built with
        Sig declared = declaredSig(ctx.pkg, pipe, sigs);
        int arity = declared.inputTypes().size();
        ClassDesc[] applyParams = new ClassDesc[arity];
        java.util.Arrays.fill(applyParams, CD_Object);
        MethodTypeDesc mtdApply = MethodTypeDesc.of(CD_Object, applyParams);

        return build(cdP, cb -> {
            cb.withFlags(pub(pipe.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            // implements its public interface (which itself extends Behavior for a single-input one)
            cb.withInterfaceSymbols(cdBehavior(pipe.name()));
            emitInjection(cb, cdP, reqStages);

            cb.withMethodBody("apply", mtdApply, ClassFile.ACC_PUBLIC, code -> {
                // slot 1 always holds the running value (an output case, as an Object).
                List<Composition.Stage> stages = composed.stages();
                // stage 0 consumes the pipeline's arguments unconditionally
                applyFirstStage(code, cdP, stages.get(0).behavior(), arity, requiredNames,
                        reqStages, behaviorDeps, stages.get(0).answers(), arity + 1);
                Label end = code.newLabel();
                for (int i = 1; i < stages.size(); i++) {
                    Composition.Stage stage = stages.get(i);
                    switch (stage.routing()) {
                        // Anything the stage was not offered has left the main line: jump to the
                        // end rather than offering it to the stages after this one (spec
                        // §type-routing). Branching to the end is what makes a retired case
                        // unreachable without tagging it — the same case type may legitimately
                        // reappear on the main line downstream.
                        case Composition.Routing.OnCases on -> {
                            Label doApply = code.newLabel();
                            for (TypeSymbol caseName : on.accepted()) {
                                code.aload(1);
                                code.instanceOf(caseClass(caseName));
                                code.ifne(doApply);
                            }
                            code.goto_(end);
                            code.labelBinding(doApply);
                        }
                        case Composition.Routing.Always _ -> { }
                    }
                    applyStage(code, cdP, stage.behavior(), requiredNames, reqStages, behaviorDeps,
                            stage.answers(), arity + 1);
                }
                code.labelBinding(end);
                code.aload(1);
                // The composition's own return: the running value is a Souther value, and this is
                // where it becomes a member of the union this composition answers with.
                ResultBoundary.inject(code, ctx, ctx.bridgedMembers(declared.outputType()),
                        arity + 1);
            });
            if (arity != 1) {
                emitTypedApplyBridge(cb, cdP,
                        typedApplyDesc(pipe.name(), declared.inputTypes(), declared.outputType()));
            }
        });
    }

    /**
     * Applies the first stage to the pipeline's own arguments, leaving the result in slot 1.
     *
     * <p>Only this stage may take other than one input (spec §sequential-composition). Such a behavior does not
     * implement {@code Behavior} — that interface takes one value — so it is called on its own
     * class rather than through the interface. An injected one is that class: the pipeline holds it
     * in the field it was bound to, and its {@code apply} is typed rather than erased (issue #57),
     * so the arguments are cast from the erased {@code apply(Object,…)} this body lives on. A
     * zero-input stage takes that same path with nothing to cast.
     */
    private void applyFirstStage(CodeBuilder code, ClassDesc cdP, ValueName.Behavior stage,
                                 int arity, Set<ValueName.Behavior> requiredNames,
                                 InjectionSlots held,
                                 Map<ValueName.Behavior, List<ValueName.Behavior>> behaviorDeps,
                                 Type stageOut, int slot) {
        if (arity == 1) {
            applyStage(code, cdP, stage, requiredNames, held, behaviorDeps, stageOut, slot);
            return;
        }
        pushStage(code, cdP, stage, requiredNames, held, behaviorDeps);
        if (ctx.isStandaloneRequired(stage)) {
            MethodTypeDesc desc = ctx.requiredApplyDesc(stage);
            for (int i = 0; i < arity; i++) {
                code.aload(i + 1);
                ClassDesc param = desc.parameterType(i);
                if (!param.equals(CD_Object)) {
                    code.checkcast(param);
                }
            }
            code.invokevirtual(cdBehavior(stage), "apply", desc);
            projectStage(code, stage, stageOut, slot);
            checkStageAtCrossing(code, stage, arity, slot + 1);
            code.astore(1);
            return;
        }
        for (int i = 0; i < arity; i++) {
            code.aload(i + 1);
        }
        ClassDesc[] params = new ClassDesc[arity];
        java.util.Arrays.fill(params, CD_Object);
        // a multi-input first stage with a `let` is a fn/pipe behavior; call the erased apply on its $Impl
        code.invokevirtual(ctx.cdBehaviorImpl(stage), "apply",
                MethodTypeDesc.of(CD_Object, params));
        projectStage(code, stage, stageOut, slot);
        checkStageAtCrossing(code, stage, arity, slot + 1);
        code.astore(1);
    }

    /** Applies one pipeline stage to the running value in slot 1, storing the result back. A stage
     * is a behavior, or a {@code Type.decoder}/{@code Type.encoder} boundary codec (spec §sequential-composition). */
    private void applyStage(CodeBuilder code, ClassDesc cdP, ValueName.Behavior stage,
                            Set<ValueName.Behavior> requiredNames, InjectionSlots held,
                            Map<ValueName.Behavior, List<ValueName.Behavior>> behaviorDeps,
                            Type stageOut, int slot) {
        // decode/encode are boundary edges, not pipeline stages (spec §sequential-composition): `>->` composes
        // behaviors only.
        pushStage(code, cdP, stage, requiredNames, held, behaviorDeps);
        code.aload(1);
        code.invokeinterface(CD_Behavior, "apply", MTD_apply);
        projectStage(code, stage, stageOut, slot);
        checkStageAtCrossing(code, stage, 1, slot + 1);
        code.astore(1);
    }

    /**
     * Holds an injected stage's answer to what it declared, with the answer on the stack as the
     * boxed carrier {@code projectStage} left it and its arguments still in the slots they came in.
     *
     * <p>A stage whose body this compiler emits holds itself where it answers, so nothing is emitted
     * for it here — this is the same crossing a call from a body makes, at the place a pipeline
     * makes it. Read before the running value is stored back, because storing it is what overwrites
     * the argument a single-input stage was given.
     */
    private void checkStageAtCrossing(CodeBuilder code, ValueName.Behavior stage, int arity,
                                      int carrier) {
        if (!(ctx.ensuresCheckOf(stage) instanceof EnsuresEnforcement.AtEachCrossing)) {
            return;
        }
        code.astore(carrier);
        for (int i = 0; i < arity; i++) {
            code.aload(i + 1);
        }
        code.aload(carrier);
        ClassDesc[] params = new ClassDesc[arity + 1];
        java.util.Arrays.fill(params, CD_Object);
        code.invokestatic(ctx.cd(new GeneratedClass.Ensures(
                        new GeneratedClass.BehaviorInterface(stage.module(), stage.name()))),
                "check", MethodTypeDesc.of(ConstantDescs.CD_void, params));
        code.aload(carrier);
    }

    /** A stage answered with a member of its own result union; the running value the next stage sees
     * is a Souther value again (spec §jvm-anonymous-union). The same conversion a body does after a call. */
    private void projectStage(CodeBuilder code, ValueName.Behavior stage, Type stageOut,
                              int slot) {
        ResultBoundary.project(code, ctx, stage, ctx.bridgedMembersOf(stage, stageOut), slot);
    }

    /** Pushes the behavior object for a pipeline stage: an injected required field, or a fresh
     * body-behavior instance constructed with the required dependencies it declares (spec
     * §composition-with-requirements). */
    private void pushStage(CodeBuilder code, ClassDesc cdP, ValueName.Behavior stage,
                           Set<ValueName.Behavior> requiredNames, InjectionSlots held,
                           Map<ValueName.Behavior, List<ValueName.Behavior>> behaviorDeps) {
        if (requiredNames.contains(stage)) {
            InjectionSlots.Slot slot = held.of(stage);
            code.aload(0);
            code.getfield(cdP, slot.fieldName(), slot.type());
            return;
        }
        ClassDesc cdStage = ctx.cdBehaviorImpl(stage);   // the $Impl, not the interface
        code.new_(cdStage);
        code.dup();
        List<ValueName.Behavior> deps = behaviorDeps.getOrDefault(stage, List.of());
        ClassDesc[] ctorParams = new ClassDesc[deps.size()];
        for (int i = 0; i < deps.size(); i++) {
            // Reuse the composition's own field: it holds the dependency and hands it to the stage
            // it builds. Which field that is is the composition's to say — the stage keeps the same
            // dependency in a field of its own, at whatever position its own list puts it — so the
            // read is asked of the slots this class was emitted with.
            //
            // A multi-arg injected dependency is stored and wired by its base class rather than the
            // unary Behavior (issue #57), and the field descriptor and the stage's constructor
            // parameter are the same type because both come from the dependency.
            InjectionSlots.Slot slot = held.of(deps.get(i));
            code.aload(0);
            code.getfield(cdP, slot.fieldName(), slot.type());
            ctorParams[i] = slot.type();
        }
        code.invokespecial(cdStage, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, ctorParams));
    }

    // --- value class members ---

    // --- source compatibility: which extra source decoders a type's shape supports (spec §codec-generation) ---

    // --- $Dec class ---

    /** Pushes each field value in declaration order, sourced from an explicit init or a spread. */

    // --- $Enc class ---

    // --- helpers ---

    private void unbox(CodeBuilder code, Type type, int slot) {
        JvmTypes.unbox(code, type, slot, ctx);
    }

}
