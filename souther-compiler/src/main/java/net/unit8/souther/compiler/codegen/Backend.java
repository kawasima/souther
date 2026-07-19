package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.core.Core;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static net.unit8.souther.compiler.codegen.Descriptors.*;
import static net.unit8.souther.compiler.codegen.JvmTypes.*;

/**
 * The ClassFile-API backend (spec sections 19, 20). Emits JVM bytecode directly for each
 * {@code data}: the value class (package-private ctor + invariant-checking
 * {@code __construct}) and nested {@code $Dec}/{@code $Enc} classes. Fields may reference
 * other data types; object decoders accumulate every field error (spec sections 15, 27.7).
 */
public final class Backend {

    private final CodegenContext ctx;
    /** Aliases of {@link CodegenContext#pkg}/{@link CodegenContext#symbols}, read as bare names by
     * the code still living here. */
    private final String pkg;
    private final Map<String, Ast.Def> symbols;

    private final CodecGen codec;
    private final ValueClassGen value;

    private Backend(CodegenContext ctx) {
        this.ctx = ctx;
        this.pkg = ctx.pkg;
        this.symbols = ctx.symbols;
        this.codec = new CodecGen(ctx);
        this.value = new ValueClassGen(ctx, codec);
    }

    /** {@code ACC_PUBLIC} when the name is exposed (or the module exposes all), else 0. */
    private int pub(String name) {
        return ctx.pub(name);
    }

    public static Map<String, byte[]> generate(Ast.Module module) {
        return generate(module, TypeChecker.symbols(module), Map.of());
    }

    public static Map<String, byte[]> generate(Ast.Module module, Map<String, Ast.Def> symbols,
                                               Map<String, String> typePackage) {
        return generate(module, symbols, typePackage, Map.of(), Set.of());
    }

    public static Map<String, byte[]> generate(Ast.Module module, Map<String, Ast.Def> symbols,
                                               Map<String, String> typePackage,
                                               Map<String, TypeChecker.Sig> importedSigs) {
        return generate(module, symbols, typePackage, importedSigs, Set.of());
    }

    /** Generates a module's classes. {@code symbols} covers own plus imported definitions;
     * {@code typePackage} maps an imported type or behavior name to its declaring module (spec 4);
     * {@code importedSigs} carries imported behaviors' signatures so a composition can name one as
     * a stage (spec 14); {@code importedInjected} are imported injection-target behaviors, which a
     * composition here inherits as requirements to inject and bind (spec 13.2, 14.3). */
    public static Map<String, byte[]> generate(Ast.Module module, Map<String, Ast.Def> symbols,
                                               Map<String, String> typePackage,
                                               Map<String, TypeChecker.Sig> importedSigs,
                                               Set<String> importedInjected) {
        Map<String, List<String>> caseToSums = new HashMap<>();
        for (Ast.Def def : module.defs()) {
            if (def instanceof Ast.SumData sum) {
                for (String caseName : sum.cases()) {
                    caseToSums.computeIfAbsent(caseName, k -> new ArrayList<>()).add(sum.name());
                }
            }
        }
        // The checker has already run and rejects any dotted `A.decoder`/`.encoder` member
        // (exposing is type-granular, spec 19.4), so every entry that reaches codegen is a bare name.
        Set<String> exposed = new HashSet<>(module.exposing());
        // After the Lower stage the only non-behavior fns left are recursive helpers (spec 13.1);
        // each is lowered to a static method on the module's `$Fns` class rather than inlined.
        Set<String> behaviorNames = new HashSet<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            behaviorNames.add(bd.name());
        }
        Map<String, Ast.FnDef> recHelpers = new LinkedHashMap<>();
        for (Ast.FnDef fn : module.fns()) {
            if (!behaviorNames.contains(fn.name())) {
                recHelpers.put(fn.name(), fn);
            }
        }
        CodegenContext ctx = new CodegenContext(module.name(), symbols, caseToSums, typePackage,
                module.exposing().isEmpty(), exposed, recHelpers);
        Backend b = new Backend(ctx);
        // A behavior's class capitalizes its first letter (spec 19.5). Data names are already
        // capitalized, so `behavior quote` producing `data Quote` would generate two classes named
        // `Quote`. Reject the collision here rather than let one silently overwrite the other.
        Set<String> localTypes = new HashSet<>();
        for (Ast.Def d : module.defs()) {
            localTypes.add(d.name());
        }
        Map<String, String> behaviorClassOwner = new HashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            String cls = behaviorClass(bd.name());
            if (localTypes.contains(cls)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.behavior.collision.data").title("check.duplicate.title")
                                .at(bd.pos()).args(bd.name(), cls).build(),
                        "behavior `" + bd.name() + "` generates class `" + cls + "`, which collides with"
                                + " data `" + cls + "` (spec 19.5); rename one so their class names differ");
            }
            String prev = behaviorClassOwner.put(cls, bd.name());
            if (prev != null) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.behavior.collision.behavior").title("check.duplicate.title")
                                .at(bd.pos()).args(prev, bd.name(), cls).build(),
                        "behaviors `" + prev + "` and `" + bd.name() + "` both generate class `" + cls
                                + "` (spec 19.5); rename one");
            }
        }
        // A behavior whose output is an anonymous union gets a generated sealed interface
        // <behavior名>結果 that its cases implement (spec 19.8). Register those case->interface links
        // in caseToSums before the data classes are generated, so each case class picks the interface
        // up in withInterfaceSymbols. The interface classes themselves are emitted below.
        Map<String, List<String>> behaviorResults = b.behaviorResultInterfaces(module, importedSigs);
        behaviorResults.forEach((resultName, cases) -> {
            for (String caseName : cases) {
                caseToSums.computeIfAbsent(caseName, k -> new ArrayList<>()).add(resultName);
            }
        });
        Map<String, byte[]> out = new LinkedHashMap<>();
        behaviorResults.forEach((resultName, cases) ->
                out.put(module.name() + "." + resultName, b.generateBehaviorResult(resultName, cases)));
        for (Ast.Def def : module.defs()) {
            switch (def) {
                case Ast.Data data -> b.value.generateData(data, out);
                case Ast.SumData sum -> b.value.generateSum(sum, out);
                case Ast.UnitData unit -> b.value.generateUnit(unit, out);
            }
        }
        // Behavior fn bodies arrive with their helper calls already inlined (the Lower stage,
        // ADR-0021); the backend emits them as-is and never lowers a helper on its own.
        Map<String, Ast.FnDef> fns = new HashMap<>();
        for (Ast.FnDef fn : module.fns()) {
            fns.put(fn.name(), fn);
        }
        // Injection targets (spec 13.2): a SpecBehavior with no matching fn. Each becomes an
        // abstract base class a Java implementation extends (13.3). Imported injection targets
        // (their base lives in the declaring module) are requirements too, so a composition here
        // injects and binds them (spec 14.3) — but no base is generated for them here.
        Set<String> requiredNames = new HashSet<>(importedInjected);
        Map<String, Type> requiredSuccess = new HashMap<>();
        Map<String, Type> requiredParam = new HashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            if (bd instanceof Ast.SpecBehavior spec && !fns.containsKey(spec.name())) {
                requiredNames.add(spec.name());
                requiredSuccess.put(spec.name(), b.successType(spec.ret()));
                requiredParam.put(spec.name(),
                        spec.params().size() == 1 ? b.successType(spec.params().get(0).type()) : null);
                List<String> unitCases = new ArrayList<>();
                for (Ast.TypeRef t : spec.ret().cases()) {
                    if (b.symbols.get(t.name()) instanceof Ast.UnitData) {
                        unitCases.add(t.name());
                    }
                }
                ClassDesc inputRef = spec.params().size() == 1
                        ? b.refTypeOrNull(b.successType(spec.params().get(0).type()), spec.name())
                        : null;
                ClassDesc outputRef = b.refTypeOrNull(b.successType(spec.ret()), spec.name());
                out.put(module.name() + "." + behaviorClass(spec.name()),
                        b.generateRequiredBase(spec.name(), unitCases, inputRef, outputRef));
            }
        }
        Map<String, TypeChecker.Sig> sigs = TypeChecker.signatures(module, b.symbols, importedSigs);
        Map<String, List<String>> behaviorDeps = requirementSets(module, requiredNames);
        Map<String, List<String>> pipeStages = TypeChecker.pipelineStages(module);
        for (Ast.BehaviorDef bd : module.behaviors()) {
            switch (bd) {
                case Ast.SpecBehavior spec -> {
                    Ast.FnDef fn = fns.get(spec.name());
                    if (fn != null) {
                        out.put(module.name() + "." + behaviorClass(spec.name()),
                                b.generateSpecFn(spec, fn, requiredNames, requiredSuccess, requiredParam));
                    }
                    // else: injection target — its abstract base was generated above (spec 13.3)
                }
                case Ast.PipeBehavior pipe ->
                        out.put(module.name() + "." + behaviorClass(pipe.name()),
                                b.generatePipe(pipe, requiredNames, sigs, behaviorDeps, pipeStages));
            }
        }
        if (!recHelpers.isEmpty()) {
            out.put(module.name() + ".$Fns", b.generateRecursiveHelpers(recHelpers));
        }
        out.putAll(b.ctx.synthClasses());   // escaping lambdas compiled to Fn classes (spec §blocks)
        return out;
    }

    /**
     * Emits the module's recursive helpers as {@code static} methods on a package-private {@code $Fns}
     * class (spec 13.1). Each helper's declared parameter and return types are boxed as {@code Object}
     * across the method boundary, unboxed on entry and boxed on return, so a self- or mutual call is a
     * plain {@code invokestatic} — the recursion the inliner cannot express. The body is emitted through
     * the same {@code emitBodyTail} path a behavior uses; a helper is pure, so it has no injected fields.
     */
    private byte[] generateRecursiveHelpers(Map<String, Ast.FnDef> helpers) {
        ClassDesc cdFns = ClassDesc.of(pkg + ".$Fns");
        return build(cdFns, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);   // package-private, not exposed
            for (Ast.FnDef h : helpers.values()) {
                int n = h.params().size();
                ClassDesc[] params = new ClassDesc[n];
                java.util.Arrays.fill(params, CD_Object);
                cb.withMethodBody(h.name(), MethodTypeDesc.of(CD_Object, params), ClassFile.ACC_STATIC,
                        code -> {
                    BodyGen gen = new BodyGen(ctx, code, null, cdFns, n);
                    for (int i = 0; i < n; i++) {
                        Type pt = successType((Ast.RetType) h.params().get(i).type());
                        code.aload(i);
                        int slot = gen.slot(pt);
                        unbox(code, pt, slot);
                        gen.bind(h.params().get(i).name(), slot, pt);
                    }
                    gen.emitTail(Core.of(h.body()), cdFns, Set.of(), Map.of());
                });
            }
        });
    }

    /** Emits injected required-behavior fields plus the matching constructor (or a no-arg ctor). */
    private void emitInjection(ClassBuilder cb, ClassDesc cdX, List<String> requireds) {
        if (requireds.isEmpty()) {
            emitPublicCtor(cb);
            return;
        }
        for (String req : requireds) {
            cb.withField(req, CD_Behavior, ClassFile.ACC_FINAL);
        }
        ClassDesc[] params = new ClassDesc[requireds.size()];
        for (int i = 0; i < requireds.size(); i++) {
            params[i] = CD_Behavior;
        }
        MethodTypeDesc ctorDesc = MethodTypeDesc.of(ConstantDescs.CD_void, params);
        cb.withMethodBody("<init>", ctorDesc, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0);
            code.invokespecial(CD_Object, "<init>", MTD_void);
            for (int i = 0; i < requireds.size(); i++) {
                code.aload(0);
                code.aload(i + 1);
                code.putfield(cdX, requireds.get(i), CD_Behavior);
            }
            code.return_();
        });

        // bind(<named required interfaces>) -> this behavior (spec 19.5)
        ClassDesc[] bindParams = new ClassDesc[requireds.size()];
        for (int i = 0; i < requireds.size(); i++) {
            bindParams[i] = cdBehavior(requireds.get(i));
        }
        cb.withMethodBody("bind", MethodTypeDesc.of(cdX, bindParams),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    code.new_(cdX);
                    code.dup();
                    for (int i = 0; i < requireds.size(); i++) {
                        code.aload(i);
                    }
                    code.invokespecial(cdX, "<init>", ctorDesc);
                    code.areturn();
                });
    }

    /**
     * Generates the abstract base class for a required behavior (spec 13.3): an abstract
     * {@code Behavior} that a Java implementation extends. The base exposes a {@code protected}
     * factory for each declared unit-data output case — the only sanctioned way for the
     * implementation to mint those cases (spec 2.1). The data constructors stay non-public, so
     * a subclass can build exactly this behavior's declared cases and nothing else, from any
     * package (no in-package placement required).
     *
     * <p>When both the input and output map to a concrete reference type, the base carries a
     * generic {@code Behavior<In, Out>} signature (spec 19.8, 24) — {@code Out} is the {@code <名>結果}
     * interface for an anonymous union output — so a Java author writes the real return type rather
     * than {@code Object}. If either side is a list/option/map (no single reference class), the
     * signature is omitted and the raw interface stands.
     */
    private byte[] generateRequiredBase(String name, List<String> unitCases,
                                        ClassDesc inputRef, ClassDesc outputRef) {
        ClassDesc cdR = cdBehavior(name);
        return build(cdR, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT | ClassFile.ACC_SUPER);
            if (inputRef != null && outputRef != null) {
                String beh = CD_Behavior.descriptorString();
                beh = beh.substring(0, beh.length() - 1); // drop trailing ';' to insert type args
                String sig = CD_Object.descriptorString() + beh + "<"
                        + inputRef.descriptorString() + outputRef.descriptorString() + ">;";
                cb.with(SignatureAttribute.of(ClassSignature.parseFrom(sig)));
            }
            cb.withInterfaceSymbols(CD_Behavior);
            // protected no-arg ctor so subclasses in any package can call super()
            cb.withMethodBody("<init>", MTD_void, ClassFile.ACC_PROTECTED, code -> {
                code.aload(0);
                code.invokespecial(CD_Object, "<init>", MTD_void);
                code.return_();
            });
            for (String caseName : unitCases) {
                ClassDesc caseCd = cd(caseName);
                cb.withMethodBody(caseName, MethodTypeDesc.of(caseCd),
                        ClassFile.ACC_PROTECTED | ClassFile.ACC_FINAL, code -> {
                            code.new_(caseCd);
                            code.dup();
                            code.invokespecial(caseCd, "<init>", MTD_void);
                            code.areturn();
                        });
            }
        });
    }

    /**
     * Adds the source-level {@code Behavior<I, O>} signature to a generated single-input
     * behavior. Its erased JVM {@code apply} descriptor remains {@code Object -> Object}, but
     * Java callers then receive the declared input and outcome types without a cast.
     */
    private void withBehaviorSignature(ClassBuilder cb, Type input, Type output, String behaviorName) {
        ClassDesc inputRef = refTypeOrNull(input, behaviorName);
        ClassDesc outputRef = refTypeOrNull(output, behaviorName);
        if (inputRef == null || outputRef == null) {
            return;
        }
        String beh = CD_Behavior.descriptorString();
        beh = beh.substring(0, beh.length() - 1); // drop trailing ';' to insert type args
        String sig = CD_Object.descriptorString() + beh + "<"
                + inputRef.descriptorString() + outputRef.descriptorString() + ">;";
        cb.with(SignatureAttribute.of(ClassSignature.parseFrom(sig)));
    }

    /**
     * Behavior-result interfaces to generate (spec 19.8): for each behavior whose output is an
     * anonymous union, maps {@code <behavior名>結果} to its leaf cases — the {@code permits} list and
     * the set of case classes that {@code implements} it. A named-sum output is already a sealed
     * interface (19.3) and a single-case output uses that case's own type, so neither gets one. Case
     * order is sorted for deterministic bytecode.
     */
    private Map<String, List<String>> behaviorResultInterfaces(Ast.Module module,
                                                               Map<String, TypeChecker.Sig> importedSigs) {
        Map<String, TypeChecker.Sig> sigs = TypeChecker.signatures(module, symbols, importedSigs);
        Map<String, List<String>> results = new LinkedHashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            TypeChecker.Sig sig = sigs.get(bd.name());
            if (sig == null || !(sig.out() instanceof Type.Union)) {
                continue;
            }
            List<String> cases = new ArrayList<>(TypeChecker.leafCases(sig.out(), symbols));
            Collections.sort(cases);
            results.put(behaviorClass(bd.name()) + "結果", cases);
        }
        return results;
    }

    /**
     * Generates the sealed interface for a behavior's anonymous union output (spec 19.8). The body
     * is empty: Java consumers receive a value and {@code switch} over the permitted cases, each of
     * which carries its own codec, so the interface itself needs no members.
     */
    private byte[] generateBehaviorResult(String resultName, List<String> cases) {
        ClassDesc cdR = cd(resultName);
        List<ClassDesc> caseCds = new ArrayList<>();
        for (String caseName : cases) {
            caseCds.add(cd(caseName));
        }
        return build(cdR, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            cb.with(PermittedSubclassesAttribute.ofSymbols(caseCds));
        });
    }

    /**
     * The single reference class a behavior's input or output success type maps to, for a generic
     * {@code Behavior<In, Out>} signature: the {@code <名>結果} interface for an anonymous union, the
     * named data/sum for a single case, the boxed class for a primitive. Returns {@code null} for a
     * list/option/map, which has no single reference class to name here.
     */
    private ClassDesc refTypeOrNull(Type t, String behaviorName) {
        return ctx.refTypeOrNull(t, behaviorName);
    }

    private Type resolveType(Ast.TypeRef ref) {
        return ctx.resolveType(ref);
    }

    private Type successType(Ast.RetType ret) {
        return ctx.successType(ret);
    }

    private ClassDesc[] caseInterfaces(String name) {
        return ctx.caseInterfaces(name);
    }

    private ClassDesc matchCaseClass(String caseName) {
        return ctx.matchCaseClass(caseName);
    }

    private Map<String, Type> fieldTypes(Ast.Data data) {
        return ctx.fieldTypes(data);
    }

    private ClassDesc cd(String typeName) {
        return ctx.cd(typeName);
    }

    static String behaviorClass(String name) {
        return CodegenContext.behaviorClass(name);
    }

    private ClassDesc cdBehavior(String name) {
        return ctx.cdBehavior(name);
    }


    private ClassDesc caseClass(String typeName) {
        return ctx.caseClass(typeName);
    }

    // --- sum data (sealed interface) ---

    /** Emits a {@code static} factory that returns a fresh instance of {@code impl}. */
    // --- behaviors ---

    /**
     * Generates a behavior implemented by a {@code fn} (spec 13.1). The behavior's inputs are the
     * {@code apply} arguments; its {@code requires} are injected fields (12.6). The {@code fn}'s
     * leading parameters name the inputs (their types come from the behavior); the trailing ones
     * name the injected behaviors and are resolved as inline calls, not bound as locals.
     */
    private byte[] generateSpecFn(Ast.SpecBehavior spec, Ast.FnDef fn, Set<String> requiredNames,
                                  Map<String, Type> requiredSuccess, Map<String, Type> requiredParam) {
        ClassDesc cdB = cdBehavior(spec.name());
        int n = spec.params().size();
        // declared requires, validated to equal what the fn calls (E1602/E1603); the same order is
        // used by pipeline callers (requirementSets), so the injected fields line up.
        List<String> injected = spec.requires();
        ClassDesc[] applyParams = new ClassDesc[n];
        for (int i = 0; i < n; i++) {
            applyParams[i] = CD_Object;
        }
        MethodTypeDesc mtdApply = MethodTypeDesc.of(CD_Object, applyParams);
        return build(cdB, cb -> {
            cb.withFlags(pub(spec.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            if (n == 1) {
                cb.withInterfaceSymbols(CD_Behavior); // single-input behaviors compose with >->
                withBehaviorSignature(cb, successType(spec.params().getFirst().type()),
                        successType(spec.ret()), spec.name());
            }
            emitInjection(cb, cdB, injected);
            cb.withMethodBody("apply", mtdApply, ClassFile.ACC_PUBLIC, code -> {
                BodyGen gen = new BodyGen(ctx, code, null, cdB, n + 1);
                gen.requireds(requiredNames, requiredSuccess, requiredParam);
                for (int i = 0; i < n; i++) {
                    // the fn's leading param names the input; its type comes from the behavior
                    Type pt = successType(spec.params().get(i).type());
                    code.aload(i + 1);
                    int slot = gen.slot(pt);
                    unbox(code, pt, slot);
                    gen.bind(fn.params().get(i).name(), slot, pt);
                }
                gen.emitTail(Core.of(fn.body()), cdB, requiredNames, requiredSuccess);
            });
        });
    }


    /**
     * Every behavior's requirement set, in constructor (first-seen) order (spec 13.6, 14.3).
     *
     * <p>A required behavior requires itself; a body behavior requires what it calls; a pipeline
     * requires the union of its stages'. Pipelines are resolved too, and transitively: a pipeline
     * used as a stage of another pipeline still needs its own dependencies injected, and leaving
     * it out produced a class whose {@code apply} called a constructor that does not exist.
     */
    private static Map<String, List<String>> requirementSets(Ast.Module module, Set<String> requiredNames) {
        Map<String, Ast.BehaviorDef> byName = new HashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            byName.put(bd.name(), bd);
        }
        Map<String, List<String>> memo = new HashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            resolveDeps(bd.name(), byName, requiredNames, memo, new LinkedHashSet<>());
        }
        return memo;
    }

    private static List<String> resolveDeps(String name, Map<String, Ast.BehaviorDef> byName,
                                            Set<String> requiredNames, Map<String, List<String>> memo,
                                            LinkedHashSet<String> inProgress) {
        if (requiredNames.contains(name)) {
            return List.of(name);
        }
        List<String> cached = memo.get(name);
        if (cached != null) {
            return cached;
        }
        Ast.BehaviorDef bd = byName.get(name);
        if (bd == null) {
            return List.of();
        }
        if (!inProgress.add(name)) {
            throw new CompileException(bd.pos(),
                    "cyclic behavior composition: " + String.join(" >-> ", inProgress) + " >-> " + name);
        }
        LinkedHashSet<String> deps = new LinkedHashSet<>();
        switch (bd) {
            // an injection target short-circuits above, so a SpecBehavior here is fn-implemented:
            // its dependencies are its declared requires, in that order (spec 12.6, 13.6)
            case Ast.SpecBehavior spec -> deps.addAll(spec.requires());
            case Ast.PipeBehavior pipe -> {
                for (String stage : pipe.stages()) {
                    deps.addAll(resolveDeps(stage, byName, requiredNames, memo, inProgress));
                }
            }
        }
        inProgress.remove(name);
        List<String> out = new ArrayList<>(deps);
        memo.put(name, out);
        return out;
    }

    private byte[] generatePipe(Ast.PipeBehavior pipe, Set<String> requiredNames,
                                Map<String, TypeChecker.Sig> sigs, Map<String, List<String>> behaviorDeps,
                                Map<String, List<String>> pipeStages) {
        ClassDesc cdP = cdBehavior(pipe.name());
        // Flatten nested pipeline stages so the routing is over leaf behaviors (spec 14.2): a named
        // intermediate `half = split >-> work` inlines to `split, work`, which keeps a retired case
        // retired across the composition, making `>->` associative.
        List<String> flat = TypeChecker.flattenStages(pipe.stages(), pipeStages, pipe.pos());
        // the pipeline's injected fields are the union of its stages' requirements (spec 14.3)
        List<String> reqStages = behaviorDeps.getOrDefault(pipe.name(), List.of());
        // the pipeline takes whatever its first stage takes (spec 14.1)
        int arity = TypeChecker.stageSig(flat.get(0), sigs, symbols, pipe.pos()).ins().size();
        ClassDesc[] applyParams = new ClassDesc[arity];
        java.util.Arrays.fill(applyParams, CD_Object);
        MethodTypeDesc mtdApply = MethodTypeDesc.of(CD_Object, applyParams);

        return build(cdP, cb -> {
            cb.withFlags(pub(pipe.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            if (arity == 1) {
                cb.withInterfaceSymbols(CD_Behavior);   // only single-input pipelines compose
                TypeChecker.Sig sig = TypeChecker.stageSig(pipe.name(), sigs, symbols, pipe.pos());
                withBehaviorSignature(cb, sig.ins().getFirst(), sig.out(), pipe.name());
            }
            emitInjection(cb, cdP, reqStages);

            cb.withMethodBody("apply", mtdApply, ClassFile.ACC_PUBLIC, code -> {
                // slot 1 always holds the running value (an output case, as an Object).
                List<String> stages = flat;
                // stage 0 consumes the pipeline's arguments unconditionally
                applyFirstStage(code, cdP, stages.get(0), arity, requiredNames, behaviorDeps);
                Type mainline = TypeChecker.stageSig(stages.get(0), sigs, symbols, pipe.pos()).out();
                Label end = code.newLabel();
                for (int i = 1; i < stages.size(); i++) {
                    String stage = stages.get(i);
                    TypeChecker.Sig g = TypeChecker.stageSig(stage, sigs, symbols, pipe.pos());
                    if (TypeChecker.isDataLike(mainline)) {
                        // Apply g only when the running value is one of the main-line cases it
                        // accepts. Anything else has left the main line: jump to the end rather
                        // than offering it to the stages after this one (spec 14.2). Branching to
                        // the end is what makes a retired case unreachable without tagging it — the
                        // same case type may legitimately reappear on the main line downstream.
                        List<String> accepted = TypeChecker.mainlineCases(mainline, g, symbols);
                        Label doApply = code.newLabel();
                        for (String caseName : accepted) {
                            code.aload(1);
                            code.instanceOf(caseClass(caseName));
                            code.ifne(doApply);
                        }
                        code.goto_(end);
                        code.labelBinding(doApply);
                        applyStage(code, cdP, stage, requiredNames, behaviorDeps);
                    } else {
                        applyStage(code, cdP, stage, requiredNames, behaviorDeps);
                    }
                    mainline = TypeChecker.stageOut(mainline, g, symbols, pipe.pos());
                }
                code.labelBinding(end);
                code.aload(1);
                code.areturn();
            });
        });
    }

    /**
     * Applies the first stage to the pipeline's own arguments, leaving the result in slot 1.
     *
     * <p>Only this stage may take several inputs (spec 14.1). A multi-input behavior does not
     * implement {@code Behavior} — that interface takes one value — so it is called on its own
     * class rather than through the interface.
     */
    private void applyFirstStage(CodeBuilder code, ClassDesc cdP, String stage, int arity,
                                 Set<String> requiredNames, Map<String, List<String>> behaviorDeps) {
        if (arity == 1) {
            applyStage(code, cdP, stage, requiredNames, behaviorDeps);
            return;
        }
        pushStage(code, cdP, stage, requiredNames, behaviorDeps);
        for (int i = 0; i < arity; i++) {
            code.aload(i + 1);
        }
        ClassDesc[] params = new ClassDesc[arity];
        java.util.Arrays.fill(params, CD_Object);
        code.invokevirtual(cdBehavior(stage), "apply", MethodTypeDesc.of(CD_Object, params));
        code.astore(1);
    }

    /** Applies one pipeline stage to the running value in slot 1, storing the result back. A stage
     * is a behavior, or a {@code Type.decoder}/{@code Type.encoder} boundary codec (spec 14.1). */
    private void applyStage(CodeBuilder code, ClassDesc cdP, String stage, Set<String> requiredNames,
                            Map<String, List<String>> behaviorDeps) {
        // decode/encode are boundary edges, not pipeline stages (spec 14.1): `>->` composes
        // behaviors only.
        pushStage(code, cdP, stage, requiredNames, behaviorDeps);
        code.aload(1);
        code.invokeinterface(CD_Behavior, "apply", MTD_apply);
        code.astore(1);
    }

    /** Pushes the behavior object for a pipeline stage: an injected required field, or a fresh
     * body-behavior instance constructed with the required dependencies it declares (spec 14.3). */
    private void pushStage(CodeBuilder code, ClassDesc cdP, String stage, Set<String> requiredNames,
                           Map<String, List<String>> behaviorDeps) {
        if (requiredNames.contains(stage)) {
            code.aload(0);
            code.getfield(cdP, stage, CD_Behavior);
            return;
        }
        ClassDesc cdStage = cdBehavior(stage);
        code.new_(cdStage);
        code.dup();
        List<String> deps = behaviorDeps.getOrDefault(stage, List.of());
        ClassDesc[] ctorParams = new ClassDesc[deps.size()];
        for (int i = 0; i < deps.size(); i++) {
            code.aload(0);
            code.getfield(cdP, deps.get(i), CD_Behavior);   // reuse the pipeline's injected field
            ctorParams[i] = CD_Behavior;
        }
        code.invokespecial(cdStage, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, ctorParams));
    }

    private void castFromObject(CodeBuilder code, Type type) {
        JvmTypes.castFromObject(code, type, ctx);
    }

    // --- value class members ---

    // --- source compatibility: which extra source decoders a type's shape supports (spec 10.6) ---

    // --- $Dec class ---

    /** Pushes each field value in declaration order, sourced from an explicit init or a spread. */

    // --- $Enc class ---

    // --- helpers ---

    private void unbox(CodeBuilder code, Type type, int slot) {
        JvmTypes.unbox(code, type, slot, ctx);
    }

    private ClassDesc jvmType(Type type) {
        return JvmTypes.jvmType(type, ctx);
    }

    private ClassDesc[] fieldDescs(Map<String, Type> fields) {
        return JvmTypes.fieldDescs(fields, ctx);
    }

}
