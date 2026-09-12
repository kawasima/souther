package souther.compiler.codegen;

import souther.compiler.check.ExpandedClauseLookup;
import souther.compiler.check.InvariantStatements;
import souther.compiler.check.AtomSpace;
import souther.compiler.check.ReqSig;
import souther.compiler.core.EnsuresEnforcement;
import souther.compiler.core.Kernel;
import souther.compiler.core.KernelSignature;
import souther.compiler.core.KernelSignatures;
import souther.compiler.core.ValueShape;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.ast.Hir;
import souther.compiler.diag.PhysicalPos;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.check.TypeOps;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.SoutherJvmAbi;
import souther.compiler.types.ValueName;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static souther.compiler.codegen.Descriptors.*;

/**
 * The module-wide state every generator shares: the symbol table and package map, the name
 * resolution built on them (a type or behavior name to its {@link ClassDesc}), and the sink for the
 * synthetic {@code Fn} classes escaping lambdas compile to. It resolves names and types; it never
 * touches a {@code CodeBuilder}. One instance is built per module in {@link Backend#generate} and
 * handed to {@link Backend.Gen}, and later to the codec and value-class generators.
 */
final class CodegenContext {

    final String pkg;
    final DerivedSymbols symbols;
    /** What the declarations an emitted class names say about themselves. Beside {@link #symbols}
     *  and not read off it, for the reason {@code PublishedDeclarations} gives. */
    final PublishedDeclarations published;

    /** Which form each declaration was written in, for the emissions that only have to tell a sum
     *  from anything else. */
    final DeclarationKinds kinds;

    /**
     * What the language declares of its kernels: what each takes and answers, as the compilation
     * that checked these bodies settled it.
     *
     * <p>Handed in rather than fetched. A backend that could reach the library would be a backend
     * that could ask it anything, and what it has business asking is what the operation it is
     * emitting a call to was declared with — which is a decision of the language, already made and
     * already carried. Given the value, there is no arrangement under which this emits against one
     * reading and the program says another.
     */
    private final KernelSignatures kernels;

    /** What {@code kernel} was declared to take and answer. */
    KernelSignature kernelSignature(Kernel kernel) {
        return kernels.signatureOf(kernel);
    }
    final Map<String, List<GeneratedClass>> caseToSums;
    final Map<String, String> typePackage;
    /** True when the module has no {@code exposing} clause: everything stays public. */
    final boolean exposeAll;
    /** Base names the module exposes (only these are public when {@link #exposeAll} is false). */
    final Set<String> exposed;
    /**
     * What a call this module leaves standing is typed against, by the name it is reached by.
     *
     * <p>The same answer the check typed those calls against, handed over rather than worked out
     * again. The emitter re-types the expressions it emits — a clause, a rule — and a table built
     * here out of what this module happens to emit would answer for the methods written rather than
     * for the names a call can hold, which is a narrower question and not the one being asked.
     */
    final Map<String, Type> standingCalls;

    /**
     * The texts this module's code was read from, for the debug table.
     *
     * <p>Handed in for the run and not asked for. What line an instruction's code is at is what its
     * file is laid out as at the moment, and a backend that could go and find that out would be a
     * backend that reads the workspace; what it has business knowing is the texts the compilation
     * it is emitting for was given.
     */
    private final SourceLayouts layouts;

    /**
     * Which text the classes generated here are of.
     *
     * <p>A class carries one {@code SourceFile}, and it is this module's. So this is the one text
     * whose line numbers a class generated here can be read against: a place in any other text has
     * no line to contribute, because whatever line that place is at is a line of a file the class
     * does not name, and the file it does name may be shorter than it.
     */
    private final QuotedFrom home;

    /** Where a place sits in the text this module's classes name, or null where it sits in another
     *  text or this compilation holds none. */
    PhysicalPos sits(SourcePos place) {
        if (place == null || !home.equals(place.quotedFrom())) {
            return null;
        }
        return layouts.resolve(place);
    }

    /** Synthetic {@code Fn} classes generated for escaping lambdas (spec §blocks), merged into the
     * module output once every behavior is generated. */
    private final Map<GeneratedClass, byte[]> synthClasses = new LinkedHashMap<>();
    private int lambdaCounter = 0;

    /** Per-injected-behavior input/success types, set once the module's required behaviors are known
     * ({@link Backend#generate}). Drives the unary-vs-standalone dispatch (issue #57): a required
     * behavior that does not take exactly one input is stored by its own base class and called with
     * {@code invokevirtual}, not the unary {@code Behavior}. Both {@link Backend} and
     * {@link Backend.Gen}/{@code BodyGen} read this, so the field type, ctor param and call
     * descriptor cannot drift apart. */
    private Map<ValueName.Behavior, List<Type>> reqParams = Map.of();
    private Map<ValueName.Behavior, Type> reqSuccess = Map.of();

    void setRequiredSignatures(Map<ValueName.Behavior, List<Type>> params,
                               Map<ValueName.Behavior, Type> success) {
        this.reqParams = params;
        this.reqSuccess = success;
    }

    /**
     * This module's declarations' invariant clauses in the representation the language's own operations
     * survive in ({@link souther.compiler.check.InliningPolicy#DISCHARGE}). The constraint mapping a
     * derived decoder does is written against those operations, so it reads this rather than the
     * settled form the rest of the backend emits from.
     *
     * <p>Null until it is set, and not an empty one. A module reading as stating nothing and a module
     * whose representation never arrived are the same empty map and opposite facts, and a decoder
     * built from the second would silently constrain nothing.
     */
    private ExpandedClauseLookup dischargeInvariants;

    /**
     * Where each behavior's declared relation is checked, as it was decided before emission.
     *
     * <p>Set rather than worked out, for the reason it is a decision and not a pair of facts: the
     * emitter is adding to a set of injected names while it runs, so a reader here would be reading
     * that set at whatever point it happened to be at.
     */
    private Map<ValueName.Behavior, EnsuresEnforcement> ensuresChecks = Map.of();

    void setEnsuresChecks(Map<ValueName.Behavior, EnsuresEnforcement> checks) {
        this.ensuresChecks = Map.copyOf(checks);
    }

    /**
     * What is being done about {@code behavior}'s clause.
     *
     * <p>{@link EnsuresEnforcement.NotDecidedHere} for a behavior another module declared, which the
     * lookup answers rather than this: a table of this compilation's decisions holds one for every
     * behavior it declares, so a name it has nothing under is a name from somewhere else — which is
     * not the same answer as having decided there is no check.
     */
    EnsuresEnforcement ensuresCheckOf(ValueName.Behavior behavior) {
        return EnsuresEnforcement.in(ensuresChecks, pkg, behavior);
    }

    /**
     * What each conjunct of this module's declarations states, statement by statement.
     *
     * <p>The reading the front end made, handed over rather than repeated. What a rule states is
     * settled where the clause's shape was read — a binding crossed, a denial spent — and a backend
     * that read the tree for itself would recognise a rule written out and decline the same rule
     * named through a helper, which is a difference in what a decoder reports and not in the model.
     *
     * <p>Null until it is set, for the reason {@link #dischargeInvariants} gives.
     */
    private InvariantStatements invariantStatements;

    void setInvariantStatements(InvariantStatements statements) {
        this.invariantStatements = statements;
    }

    InvariantStatements invariantStatements() {
        if (invariantStatements == null) {
            throw new IllegalStateException(
                    "what " + pkg + "'s clauses state was never handed over");
        }
        return invariantStatements;
    }

    void setDischargeInvariants(ExpandedClauseLookup clauses) {
        this.dischargeInvariants = clauses;
    }

    ExpandedClauseLookup dischargeInvariants() {
        if (dischargeInvariants == null) {
            throw new IllegalStateException(
                    "the analysis representation of " + pkg + "'s clauses was never handed over");
        }
        return dischargeInvariants;
    }

    private Map<TypeSymbol.AtModule, ValueShape> shapes = Map.of();

    void setValueShapes(Map<TypeSymbol.AtModule, ValueShape> shapes) {
        this.shapes = shapes;
    }

    /**
     * What a value of {@code named} is made of and what must hold of one, as the check answered it.
     *
     * <p>The fields and the clauses together, because binding a field and running a clause are two
     * halves of one environment. Null for a data no module here declares — nothing asks, since a
     * value class is emitted where its type is declared.
     */
    ValueShape shapeOf(TypeSymbol.AtModule named) {
        return shapes.get(named);
    }

    /**
     * The arms to record, where this generation is one that measures. Empty otherwise, which is every
     * generation whose classes are meant to be shipped.
     *
     * <p>Keyed by the identity of the nodes being emitted, so what is set here has to be the plan made
     * from <em>these</em> bodies. Core nodes are records: a plan made from an equal body would answer
     * for the wrong arm and never say so.
     */
    private souther.compiler.coverage.CoverageSites.Plan coverage =
            souther.compiler.coverage.CoverageSites.Plan.NONE;

    void setCoveragePlan(souther.compiler.coverage.CoverageSites.Plan plan) {
        this.coverage = plan;
    }

    boolean measuring() {
        return !coverage.hasNoProbes();
    }

    /**
     * Whether this generation counts what the code goes through.
     *
     * <p>A separate question from {@link #measuring}, and it has to stay separate. Coverage is asked
     * for by a measurement and is about one module's arms; a budget is what every evaluation is held
     * to and covers every module a row reaches. A generation may do either, both, or neither, and the
     * two are planned differently — an arm is a numbered node of a body the plan was made from, while
     * a counted point is any loop the emitter emits, including ones in a decoder that no body has a
     * node for.
     */
    private boolean counting;

    void setCounting(boolean counting) {
        this.counting = counting;
    }

    boolean counting() {
        return counting;
    }

    /**
     * One counted point, where this generation counts them.
     *
     * <p>Every loop the emitter emits goes through here, so a loop added later cannot be a loop that
     * nothing counts. Put on the branch that goes back rather than at the loop's entry: what is being
     * counted is going round again, and a loop that runs no iterations should cost nothing.
     *
     * <p>Emits nothing at all for a generation whose classes ship, so a jar carries no reference to
     * the compiler.
     */
    void countOneStep(java.lang.classfile.CodeBuilder code) {
        if (counting) {
            code.invokestatic(Descriptors.CD_EvaluationContext, "tick",
                    Descriptors.MTD_EvaluationContext_count);
        }
    }

    /**
     * The arm numbers of one node, in the order the emitter emits them.
     *
     * <p>Throws where a measuring generation meets a node the plan does not know. Only bodies the plan
     * was made from ask this, so a node it does not hold is a plan made from other nodes than these —
     * and going on would leave an arm that ran reported as one no row reaches, which reads as a gap in
     * the model rather than as a fault in the measurement.
     */
    int[] probesOf(souther.compiler.core.Core node) {
        int[] arms = coverage.probesOf(node);
        if (arms == null) {
            throw new IllegalStateException("no probe was planned for a "
                    + node.getClass().getSimpleName() + " at " + node.pos()
                    + "; the plan was made from other nodes than these");
        }
        return arms;
    }

    /**
     * Where this comparison's value is recorded, or empty where it is not one of a guard's condition.
     *
     * <p>Not the loud lookup {@link #probesOf} is. An arm the plan does not hold is a plan made from
     * other nodes; a comparison it does not hold is any comparison written outside a condition, which
     * is most of them.
     */
    java.util.Optional<souther.compiler.coverage.ComparisonEmissionSite> comparisonSiteOf(
            souther.compiler.core.Core comparison) {
        // Which comparison the node is, then where a run through it is written down: the catalog
        // answers the first for every comparison the bodies hold, and the plan the second for the
        // ones it instruments. The emitter is walking the tree, so the node is how it gets in.
        return coverage.comparisons().occurrenceAt(comparison)
                .flatMap(coverage::emissionSiteOf);
    }

    /** Records that one planned arm was emitted. */
    void emitted(int site) {
        emittedSites.add(site);
    }

    private final Set<Integer> emittedSites = new java.util.LinkedHashSet<>();

    /**
     * Which planned arms never reached the bytecode.
     *
     * <p>What makes an omission loud. A body the emitter walks without counting its arms — a path
     * nobody thought to say either way about — takes the arms of that behavior out of the measurement
     * silently, and every one of them is then reported as an arm no row goes through.
     */
    List<Integer> plannedButNotEmitted() {
        List<Integer> missing = new java.util.ArrayList<>();
        for (souther.compiler.coverage.CoverageSites.Site site : coverage.sites()) {
            // By the number, because what was emitted is what was written into the code: this is
            // the side of the boundary where a place is a constant in a call, and both families
            // are written the same way there.
            if (!emittedSites.contains(site.index().raw())) {
                missing.add(site.index().raw());
            }
        }
        return missing;
    }

    /** The module being generated. Module is package (spec §modules), so this is also {@link #pkg}. */
    String module() {
        return pkg;
    }

    /** The behaviors a body may call by name — the ones whose requirement set is empty (spec
     * {@code [#calling-a-behavior]}). A call to one is built where it is called rather than read out
     * of a field, so it needs no injection; what is kept is the signature the call was typed against,
     * which decides the descriptor the call links to. Set once, with the required signatures. */
    private Map<ValueName.Behavior, ReqSig> callees = Map.of();

    void setCalleeSignatures(Map<ValueName.Behavior, ReqSig> sigs) {
        this.callees = sigs;
    }

    /** The signature a behavior called by name was typed against, or null when the name is not one. */
    ReqSig calleeSig(ValueName.Behavior name) {
        return callees.get(name);
    }

    /** A required (injected) behavior takes other than one input, so it is a standalone base rather
     * than the unary {@code Behavior} (issue #57, spec §java-base-class). Two inputs are too many to
     * hand along an arrow and none is too few, so both are called on their own class with a typed
     * {@code apply}; only a single input is the transformation {@code Behavior} describes. */
    boolean isStandaloneRequired(ValueName.Behavior name) {
        List<Type> params = reqParams.get(name);
        return params != null && params.size() != 1;   // absent: not a required behavior at all
    }

    /** The JVM type a required behavior is stored/injected as: its own base class unless it takes
     * exactly one input, which is the unary {@code Behavior} composition contract. */
    ClassDesc requiredFieldType(ValueName.Behavior name) {
        return isStandaloneRequired(name) ? cdBehavior(name) : CD_Behavior;
    }

    /** The typed {@code apply(A,B,…)} descriptor of a standalone required behavior's base — the same
     * descriptor {@link Backend#generateRequiredBase} declared, so an {@code invokevirtual} on it links. */
    MethodTypeDesc requiredApplyDesc(ValueName.Behavior name) {
        return typedApplyDesc(name, reqParams.get(name), reqSuccess.get(name));
    }

    /** The interface-facing apply descriptor for a multi-input behavior: each param and the return
     * mapped to its runtime reference type. A collection keeps its {@code java.util.List/Map/Set} (or
     * runtime {@code Option}) interface — not degraded to {@code Object} — with the element type
     * carried by {@link #applySignatureOrNull} (issue #57). */
    MethodTypeDesc typedApplyDesc(ValueName.Behavior name, List<Type> paramTypes, Type retType) {
        ClassDesc[] p = new ClassDesc[paramTypes.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = applyParamType(paramTypes.get(i), name);
        }
        return MethodTypeDesc.of(applyParamType(retType, name), p);
    }

    /** The JVM reference type an {@code apply} slot takes for {@code t}: a collection keeps its raw
     * runtime interface ({@code java.util.List/Map/Set}, runtime {@code Option}); a data/union/primitive
     * maps to its ref; anything erased (type var, tuple, fn) falls back to {@code Object}. */
    ClassDesc applyParamType(Type t, ValueName.Behavior name) {
        if (t instanceof Type.ListOf || t instanceof Type.MapOf
                || t instanceof Type.SetOf || t instanceof Type.OptionOf) {
            return JvmTypes.jvmType(t, this);
        }
        ClassDesc r = refTypeOrNull(t, name);
        return r != null ? r : CD_Object;
    }

    /** A generic {@code Signature} for a typed {@code apply}, or null when no param/return is a
     * collection (the raw descriptor then already names every type). Mirrors the data-factory signature:
     * a collection element is carried via {@link JvmTypes#genericSig}, everything else by its descriptor. */
    String applySignatureOrNull(ValueName.Behavior name, List<Type> params, Type ret) {
        boolean anyContainer = JvmTypes.genericSig(ret, this) != null;
        for (Type p : params) {
            anyContainer |= JvmTypes.genericSig(p, this) != null;
        }
        if (!anyContainer) {
            return null;
        }
        StringBuilder sb = new StringBuilder("(");
        for (Type p : params) {
            sb.append(applySigElem(p, name));
        }
        return sb.append(")").append(applySigElem(ret, name)).toString();
    }

    private String applySigElem(Type t, ValueName.Behavior name) {
        String g = JvmTypes.genericSig(t, this);
        return g != null ? g : applyParamType(t, name).descriptorString();
    }

    /** The signature-form of a single {@code Behavior<In, Out>} type argument: a collection carries its
     * element type; a data/primitive/union its descriptor; a truly erased type (var/tuple/fn) yields
     * null, which suppresses the whole generic {@code Behavior} signature. */
    String sigRefOrNull(Type t, ValueName.Behavior name) {
        String g = JvmTypes.genericSig(t, this);
        if (g != null) {
            return g;
        }
        ClassDesc r = refTypeOrNull(t, name);
        return r != null ? r.descriptorString() : null;
    }

    CodegenContext(String pkg, DerivedSymbols symbols, PublishedDeclarations published,
                   DeclarationKinds kinds,
                   KernelSignatures kernels,
                   Map<String, List<GeneratedClass>> caseToSums,
                   Map<String, String> typePackage, boolean exposeAll, Set<String> exposed,
                   Map<String, Type> standingCalls, SourceLayouts layouts, QuotedFrom home) {
        this.layouts = layouts;
        this.home = Objects.requireNonNull(home, "the classes of a module are of the text it was read from");
        this.pkg = pkg;
        this.symbols = symbols;
        this.published = published;
        this.kinds = kinds;
        this.kernels = kernels;
        this.caseToSums = caseToSums;
        this.typePackage = typePackage;
        this.exposeAll = exposeAll;
        this.exposed = exposed;
        this.standingCalls = standingCalls;
    }

    /** {@code ACC_PUBLIC} when the name is exposed (or the module exposes all), else 0. */
    int pub(String name) {
        return (exposeAll || exposed.contains(name)) ? ClassFile.ACC_PUBLIC : 0;
    }

    // The same handful of classes is turned into a descriptor again at every emission site, and
    // ClassDesc.of re-validates the name on each call, so this keeps what it has already built. It
    // lives on the context, so it is per module generated and never outlives it.
    private final Map<GeneratedClass, ClassDesc> descs = new HashMap<>();

    /** The descriptor of a generated class. What it is called is {@link SoutherJvmAbi}'s to say; this
     * only remembers the answer. */
    ClassDesc cd(GeneratedClass generated) {
        return descs.computeIfAbsent(generated, g -> SoutherJvmAbi.nameOf(g).classDesc());
    }

    /** The class of a type, from the module that declares it — nothing to look up, since a
     * {@link TypeSymbol} already says where it lives. */
    ClassDesc cd(TypeSymbol name) {
        return cd(new GeneratedClass.Value(name));
    }

    /** The class of a declaration of the module being generated. */
    ClassDesc cd(Hir.Def def) {
        return cd(def.declares());
    }

    ClassDesc cdBehavior(ValueName.Behavior name) {
        return cd(new GeneratedClass.BehaviorInterface(name.module(), name.name()));
    }

    /** The implementation class behind a fn/pipe behavior's public interface. The interface is what
     * Java code declares; the implementation holds the fields, constructor and {@code apply}, and is
     * what a pipeline instantiates. Injected behaviors have none (their abstract base is the named
     * class). */
    ClassDesc cdBehaviorImpl(ValueName.Behavior name) {
        return cd(new GeneratedClass.BehaviorImpl(name.module(), name.name()));
    }

    /**
     * The result-union class of a behavior, in the module that declared the behavior. Nothing declares
     * this class — the backend makes it up — so placing it in the module being generated would be
     * right only for a behavior declared here. An imported one is called on a typed {@code apply}
     * naming this class, and the class lives where the behavior does.
     */
    ClassDesc cdBehaviorResult(ValueName.Behavior name) {
        return cd(new GeneratedClass.BehaviorResult(name.module(), name.name()));
    }

    /**
     * The bridge case a non-local union member reaches its result unions through, in the module that
     * declares the union. A member this module declared implements the union itself; a primitive or
     * a type another module emitted cannot be given that interface, so this module emits a record
     * holding the value and implementing the union in its stead. One per member per module: it
     * carries every result union of this module the member belongs to, which is the rule a local case
     * class already follows (spec §jvm-anonymous-union).
     */
    ClassDesc bridgeCaseClass(TypeSymbol member) {
        return cd(new GeneratedClass.BridgeCase(pkg, member));
    }

    /** The class a union member occupies in the union: itself when this module declared it, its
     * bridge case otherwise. What {@code permits} lists, and what a value of the union is at the
     * {@code apply} boundary. */
    ClassDesc resultMemberClass(TypeSymbol member) {
        return isLocalMember(member) ? cd(member) : bridgeCaseClass(member);
    }

    /** Whether {@code member} is a type this module declares, and so carries its result unions
     * itself. What the language declares never is — no module emits a primitive or the prelude's
     * data — nor is a type another module emitted. */
    boolean isLocalMember(TypeSymbol member) {
        return !member.isDeclaredByLanguage()
                && member instanceof TypeSymbol.AtModule at && at.module().equals(pkg);
    }

    /** The members of {@code out} that reach their union through a bridge case, in the order the
     * union lists them. Empty when {@code out} is not a union, or when every member of it is a type
     * this module declared — then the union's JVM form and its Souther form are the same values and
     * neither boundary converts anything. */
    List<TypeSymbol> bridgedMembers(Type out) {
        return bridgedMembersIn(pkg, out);
    }

    /** The bridged members of a behavior's output, decided in the module that declares that behavior:
     * a member is local to the union's own module, which for a call is the callee's, not this one's. */
    List<TypeSymbol> bridgedMembersOf(ValueName.Behavior behavior, Type out) {
        return bridgedMembersIn(behavior.module(), out);
    }

    private List<TypeSymbol> bridgedMembersIn(String module, Type out) {
        if (!(out instanceof Type.Union)) {
            return List.of();
        }
        List<TypeSymbol> bridged = new ArrayList<>();
        for (TypeSymbol member : AtomSpace.subjectAtoms(out, published)) {
            if (member.isDeclaredByLanguage()
                    || !(member instanceof TypeSymbol.AtModule at)
                    || !at.module().equals(module)) {
                bridged.add(member);
            }
        }
        return bridged;
    }

    /** The bridge case of {@code member} in the module that declares {@code behavior}. */
    ClassDesc bridgeCaseClassOf(ValueName.Behavior behavior, TypeSymbol member) {
        return cd(new GeneratedClass.BridgeCase(behavior.module(), member));
    }


    /** The {@code $Fns} method name for a definition the module emits. A module-own helper keeps its
     * bare name; one reached under a qualified name ({@code List.foldFrom}) has the dot mangled to
     * {@code $}, since a JVM method name cannot contain a dot. Public because an {@code example} run
     * looks its operand's method up by this name, and the name is decided here. */
    public static String helperMethod(String name) {
        return name.replace('.', '$');
    }

    /** The JVM class of an output case. The built-in {@code DivisionByZero}/{@code NotANumber} need
     * no special case: their {@link TypeSymbol} names {@code souther.runtime}, which is where they are. An
     * invariant violation is no longer a case — it aborts (spec §algebraic-types, §violation-destination) —
     * so there is no 制約違反 case here. */
    ClassDesc caseClass(TypeSymbol typeName) {
        return cd(typeName);
    }

    /** The class a match case is tested against: a boxed/reference class for a primitive case,
     * otherwise the case's data class, which its resolved name already names. */
    ClassDesc matchCaseClass(TypeSymbol caseName) {
        if (!caseName.isPrimitive()) {
            return caseClass(caseName);
        }
        // The boxed carrier of the primitive the name spells, taken from the one table that says
        // which class carries which primitive rather than from a second copy of it here.
        Type.Prim prim = caseName.primitiveKind();
        ClassDesc boxed = prim == null ? null : JvmTypes.boxedPrim(prim);
        if (boxed != null) {
            return boxed;
        }
        // Option's `Some`/`None` are named here as well, being declared by no module, and never
        // reach this: an Option match dispatches on the runtime Option classes, not on an arm's
        // own name. Anything else naming no class is a resolution that should not have happened.
        throw new IllegalStateException("no class for the case " + caseName);
    }

    ClassDesc[] caseInterfaces(String name) {
        List<ClassDesc> ifaces = new ArrayList<>();
        for (GeneratedClass sum : caseToSums.getOrDefault(name, List.of())) {
            ifaces.add(cd(sum));
        }
        return ifaces.toArray(new ClassDesc[0]);
    }

    /**
     * The single reference class a behavior's input or output success type maps to, for a generic
     * {@code Behavior<In, Out>} signature: the {@code <名>Result} interface for an anonymous union, the
     * named data/sum for a single case, the boxed class for a primitive. Returns {@code null} for a
     * list/option/map, which has no single reference class to name here.
     */
    ClassDesc refTypeOrNull(Type t, ValueName.Behavior behaviorName) {
        if (t instanceof Type.Union) {
            return cdBehaviorResult(behaviorName);
        }
        if (t instanceof Type.Ref r) {
            return cd(r.name());
        }
        return JvmTypes.boxedPrim(t);
    }

    Map<String, Type> fieldTypes(Hir.Data data) {
        return TypeOps.fieldTypes(data, symbols);
    }

    Type successType(Hir.RetType ret) {
        return TypeOps.successType(ret);
    }

    /** Whether {@code name} is an imported type or behavior (declared in another module, spec §modules). */
    boolean isImported(String name) {
        return typePackage.containsKey(name);
    }

    // --- synthetic-class sink ---

    /** The next id for an escaping lambda's generated {@code $Fn} class (spec §blocks). */
    int nextLambdaId() {
        return lambdaCounter++;
    }

    void addSynth(GeneratedClass.Lambda lambda, byte[] bytes) {
        synthClasses.put(lambda, bytes);
    }

    /** The synthetic classes accumulated so far, for merging into the module output. */
    Map<GeneratedClass, byte[]> synthClasses() {
        return synthClasses;
    }
}
