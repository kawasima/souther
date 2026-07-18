package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.SourcePos;
import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The slice-3 type checker. Adds a module symbol table so fields, decoders, and encoders
 * can reference other data types (e.g. {@code id: MemberId}, {@code field("id",
 * MemberId.decoder)}, {@code MemberId.encode(self.id)}). Exposes {@link #symbols},
 * {@link #fieldTypes}, {@link #resolveType} and {@link #typeOf} for the backend.
 */
public final class TypeChecker {

    private TypeChecker() {}

    public static void check(Ast.Module module) {
        check(module, symbols(module));
    }

    /** Type-checks a module against {@code symbols} (own definitions plus any imported ones). */
    public static void check(Ast.Module module, Map<String, Ast.Def> symbols) {
        check(module, symbols, Map.of());
    }

    /**
     * Type-checks a module. {@code importedSigs} carries the signatures of behaviors imported from
     * other modules (spec 4, 14), so a composition can name an imported behavior as a stage.
     */
    public static void check(Ast.Module module, Map<String, Ast.Def> symbols,
                             Map<String, Sig> importedSigs) {
        for (Ast.Def def : module.defs()) {
            switch (def) {
                case Ast.Data data -> checkData(data, symbols);
                case Ast.SumData sum -> checkSum(sum, symbols);
                case Ast.UnitData ignored -> { }
            }
        }
        Map<String, Ast.FnDef> fns = new HashMap<>();
        for (Ast.FnDef fn : module.fns()) {
            if (fns.put(fn.name(), fn) != null) {
                throw new CompileException(fn.pos(), "duplicate `let " + fn.name() + "`");
            }
        }
        Set<String> allBehaviors = new HashSet<>();
        Set<String> specNames = new HashSet<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            allBehaviors.add(b.name());
            if (b instanceof Ast.SpecBehavior spec) {
                specNames.add(b.name());
                rejectAnonymousUnionParams(spec);
            }
        }
        // A data is Java-buildable from outside iff the whole module is public (no `exposing`) or
        // its name is exposed. Used by the injection constructs check (E1305).
        boolean exposeAll = module.exposing().isEmpty();
        // `exposing` lists a module's own public surface. A module's own type names, as opposed to
        // `symbols`, which also holds the data it imports — an imported name is not re-exported.
        Set<String> ownTypes = new HashSet<>();
        for (Ast.Def d : module.defs()) {
            ownTypes.add(d.name());
        }
        Set<String> exposed = new HashSet<>();
        for (String e : module.exposing()) {
            int dot = e.indexOf('.');
            // `exposing` is type-granular: a data's decoder/encoder are always public API once the
            // data itself is exposed (spec 19.4), so there is nothing a `.decoder`/`.encoder` member
            // could narrow. Reject it rather than accept a form that reads as a granularity that
            // does not exist.
            if (dot >= 0) {
                throw new CompileException(module.pos(), "`exposing` is type-granular: a data's "
                        + "`decoder`/`encoder` are always public once the data is exposed (spec 19.4)."
                        + " Write `" + e.substring(0, dot) + "`, not `" + e + "`");
            }
            // an exposed name must be one of this module's own definitions. An imported name that is
            // merely visible here is not re-exported — importers reach it from its declaring module.
            if (!ownTypes.contains(e) && !allBehaviors.contains(e)) {
                String why = symbols.containsKey(e)
                        ? " is imported into this module, not defined here; `exposing` lists a"
                          + " module's own definitions and does not re-export imported names"
                        : ", which is not a data or behavior of this module";
                throw new CompileException(module.pos(), "`exposing` names `" + e + "`" + why);
            }
            exposed.add(e);
        }
        // Injection targets (spec 13.2): a SpecBehavior with no matching fn. Its name and success
        // type let a fn call it inline (spec 12.2); it is the "required" behavior of the old form.
        Map<String, ReqSig> reqSigs = new HashMap<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.SpecBehavior spec && !fns.containsKey(spec.name())) {
                // `requires` names what an implementation calls (12.6), and an injection target has
                // no implementation here — the Java side provides it (13.2). Declaring `requires` on
                // one is meaningless: nothing calls those behaviors, and nothing injects them. The
                // behavior that composes or calls this one carries the requirement instead (13.2).
                if (!spec.requires().isEmpty()) {
                    throw new CompileException(spec.pos(), "behavior `" + spec.name()
                            + "` has no `let`, so it is an injection target (spec 13.2); it cannot"
                            + " declare `requires` — the behavior that calls or composes it does");
                }
                reqSigs.put(spec.name(), new ReqSig(
                        spec.params().isEmpty() ? null : successType(spec.params().get(0).type(), symbols),
                        successType(spec.ret(), symbols)));
                checkInjectionConstructs(spec, symbols, exposeAll, exposed);
            }
        }
        // Helper fns (no matching behavior) are expanded inline at each call site (spec 12.5); a
        // helper is checked standalone against its own declared parameter types (spec 13.1).
        HelperInliner inliner = HelperInliner.forModule(module);
        checkHelpers(inliner, symbols, reqSigs);
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.SpecBehavior spec) {
                Ast.FnDef fn = fns.get(spec.name());
                if (fn != null) {
                    checkSpecFn(spec, fn, symbols, allBehaviors, reqSigs, inliner);
                }
            }
        }
        // A fn matching a pipeline is rejected (a pipeline is already its own implementation, so it
        // cannot also have a fn body — spec 13.1). A fn matching a SpecBehavior is that behavior's
        // implementation (checked above); any other fn is a helper (checked by checkHelpers).
        for (Ast.FnDef fn : module.fns()) {
            if (!specNames.contains(fn.name()) && allBehaviors.contains(fn.name())) {
                throw new CompileException(fn.pos(), "`let " + fn.name()
                        + "` cannot implement the composition `behavior " + fn.name()
                        + "`, which is already its own implementation (spec 13.1)");
            }
        }
        checkStagesAreSingleInput(module);
        // validates composition types (imported behaviors resolve as stages via importedSigs)
        Map<String, Sig> sigs = signatures(module, symbols, importedSigs);
        // an exposed composition must declare its output in `exposing`, matching the inferred one
        // (spec 14.5, ADR-0024), so a far-away change cannot grow a published output silently
        checkExposedPipeOutputs(module, exposed, sigs, symbols);
    }

    /**
     * An exposed composition ({@code >->}) behavior must declare its output in the {@code exposing}
     * list ({@code exposing { name : A | B }}, spec 14.5, ADR-0024), and the declaration must match
     * the inferred output exactly. A far-away change that grows the output then fails here, at the
     * module boundary, instead of reaching separately-compiled consumers unannounced.
     *
     * <p>The requirement applies only to a composition that is explicitly exposed: a module with no
     * {@code exposing} publishes everything with inference intact, and a non-composition behavior
     * states its type at its definition, so a signature on one is rejected.
     */
    private static void checkExposedPipeOutputs(Ast.Module module, Set<String> exposed,
            Map<String, Sig> sigs, Map<String, Ast.Def> symbols) {
        Set<String> pipeNames = new HashSet<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.PipeBehavior p) {
                pipeNames.add(p.name());
            }
        }
        // a signature in `exposing` is only meaningful on a composition behavior
        for (String name : module.exposedOutputs().keySet()) {
            if (!pipeNames.contains(name)) {
                throw new CompileException(module.pos(), "E1605", "`exposing` gives an output "
                        + "signature to `" + name + "`, which is not a composition (`>->`) behavior;"
                        + " only a composition needs one — every other definition states its type"
                        + " where it is written (spec 14.5)");
            }
        }
        // every exposed composition must declare its output, matching the inferred one
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (!(b instanceof Ast.PipeBehavior pipe) || !exposed.contains(pipe.name())) {
                continue;
            }
            Set<String> inferred = leafArms(sigs.get(pipe.name()).out(), symbols);
            Ast.RetType declared = module.exposedOutputs().get(pipe.name());
            if (declared == null) {
                throw new CompileException(pipe.pos(), "E1605", "exposed composition `" + pipe.name()
                        + "` must declare its output in `exposing` (spec 14.5): write "
                        + "`exposing { " + pipe.name() + " : " + armList(inferred) + " }`");
            }
            Set<String> declaredArms = leafArms(successType(declared, symbols), symbols);
            if (!inferred.equals(declaredArms)) {
                throw new CompileException(pipe.pos(), "E1604", "exposed composition `" + pipe.name()
                        + "` declares -> " + armList(declaredArms) + " in `exposing`, but the pipeline"
                        + " produces " + armList(inferred) + ". Update the declared output or handle"
                        + " the arm.");
            }
        }
    }

    /**
     * Checks a behavior's {@code fn} implementation against the behavior's declared signature
     * (spec 13.1). The {@code fn}'s parameters are the behavior's inputs followed by its
     * {@code requires} (12.6); the trailing ones name the injection targets in declared order and
     * do not bind values — they resolve as inline calls to those behaviors.
     */
    /**
     * Type-checks every helper fn standalone against its own declared parameter types (spec 13.1).
     * Calls to other helpers in the body are expanded first, so what is left is builtins and
     * injected behaviors, which {@code reqSigs} resolves. The construction-permission and
     * {@code requires} checks are the caller's (the helper is inlined there), so they are not
     * repeated here.
     */
    private static void checkHelpers(HelperInliner inliner, Map<String, Ast.Def> symbols,
                                     Map<String, ReqSig> reqSigs) {
        for (Ast.FnDef h : inliner.helpers().values()) {
            Map<String, Type> env = new HashMap<>();
            for (Ast.FnParam p : h.params()) {
                if (p.type() == null) {
                    throw new CompileException(p.pos(), "helper `let " + h.name() + "` must annotate "
                            + "parameter `" + p.name() + "` with its type (spec 13.1)");
                }
                rejectBuiltinShadow(p.name(), p.pos());
                env.put(p.name(), resolveParamType(p.type(), symbols));
            }
            rejectBuiltinShadowing(h.body());
            Ast.Expr body = inliner.inline(h.body());
            // a helper that returns a function (e.g. `let adder (n) = (x) -> x + n`) has no application
            // here to infer the lambda's parameter types from; it is checked where it is inlined and
            // applied (spec §blocks).
            if (producesFunction(body)) {
                continue;
            }
            typeOf(body, env, null, symbols, reqSigs);
        }
    }

    private static void checkSpecFn(Ast.SpecBehavior spec, Ast.FnDef fn, Map<String, Ast.Def> symbols,
                                    Set<String> allBehaviors, Map<String, ReqSig> reqSigs,
                                    HelperInliner inliner) {
        int nBusiness = spec.params().size();
        int nReq = spec.requires().size();
        if (fn.params().size() != nBusiness + nReq) {
            throw new CompileException(fn.pos(), "`let " + fn.name() + "` takes " + fn.params().size()
                    + " parameter(s) but `behavior " + spec.name() + "` has " + nBusiness + " input(s)"
                    + (nReq == 0 ? "" : " plus " + nReq + " requires") + " (spec 13.1)");
        }
        for (Ast.FnParam p : fn.params()) {
            if (p.type() != null) {
                throw new CompileException(p.pos(), "`let " + fn.name() + "` implements `behavior "
                        + spec.name() + "`, so its parameters take their types from it — do not annotate `"
                        + p.name() + "` (spec 13.1)");
            }
        }
        for (int i = 0; i < nReq; i++) {
            String got = fn.params().get(nBusiness + i).name();
            String want = spec.requires().get(i);
            if (!got.equals(want)) {
                throw new CompileException(fn.pos(), "`let " + fn.name() + "` parameter `" + got
                        + "` should be `" + want + "`: the `requires` become the trailing parameters "
                        + "in declared order (spec 12.6)");
            }
        }

        Map<String, Type> env = new HashMap<>();
        for (Ast.FnParam p : fn.params()) {
            rejectBuiltinShadow(p.name(), p.pos());
        }
        rejectBuiltinShadowing(fn.body());
        for (int i = 0; i < nBusiness; i++) {
            env.put(fn.params().get(i).name(), successType(spec.params().get(i).type(), symbols));
        }
        Type output = successType(spec.ret(), symbols);
        // Expand helper calls inline (spec 12.5): the whole body is then checked as one expression,
        // so a helper's constructions and injected calls count toward this behavior's permission and
        // requires — exactly as if the code had been written inline.
        Ast.Expr body = inliner.inline(fn.body());
        rejectNonRequiredCalls(body, allBehaviors, reqSigs);

        Type rt = typeOf(body, env, null, symbols, reqSigs);
        if (!assignable(rt, output, symbols)) {
            throw new CompileException(body.pos(),
                    "behavior `" + spec.name() + "` returns " + output + " but its `let` body is " + rt);
        }

        // One expression (spec 16.4): this single walk sees every construction, including under a
        // desugared `require`.
        Set<String> constructed = new HashSet<>();
        collectConstructs(body, constructed, symbols, new HashSet<>(env.keySet()));
        // `constructs` on an fn-backed behavior is optional: its construction permission is internal
        // (invisible to callers, unlike `requires`), so with the body visible the set can be inferred
        // (ADR-0002). Omit it and inference stands. Declare it and it must match the body exactly —
        // under-declaration is E1002, over-declaration E1006 — so an explicit clause stays a checkable,
        // readable record of what is newly built versus passed through (spec 12.3), the same exact
        // match `requires` gets (E1602/E1603). Injected behaviors still declare it: no body to infer
        // from, and it drives factory generation (spec 13.3).
        if (!spec.constructs().isEmpty()) {
            for (String c : constructed) {
                if (!spec.constructs().contains(c)) {
                    throw new CompileException(spec.pos(), "E1002",
                            "Behavior `" + spec.name() + "` constructs `" + c
                                    + "` but does not declare `constructs " + c + "`.");
                }
            }
            for (String declared : spec.constructs()) {
                if (!constructed.contains(declared)) {
                    throw new CompileException(spec.pos(), "E1006",
                            "Behavior `" + spec.name() + "` declares `constructs " + declared
                                    + "` but never builds " + declared + " — it passes an existing"
                                    + " value through. Remove it from the `constructs` clause.");
                }
            }
        }
        checkInvariantConstructInTail(body, symbols);

        // The requires clause must match what the fn actually calls (spec 12.6): missing -> E1602,
        // extra -> E1603.
        List<String> actual = requiredCalls(body, reqSigs.keySet());
        for (String call : actual) {
            if (!spec.requires().contains(call)) {
                throw new CompileException(spec.pos(), "E1602", "`let " + fn.name() + "` calls `" + call
                        + "`, which has no implementation, but `behavior " + spec.name()
                        + "` does not declare `requires " + call + "`.");
            }
        }
        for (String req : spec.requires()) {
            if (!actual.contains(req)) {
                throw new CompileException(spec.pos(), "E1603", "`behavior " + spec.name()
                        + "` declares `requires " + req + "`, but `let " + fn.name()
                        + "` never calls it. Remove it from the `requires` clause.");
            }
        }
    }

    /**
     * An injected behavior's declared {@code constructs} must each be Java-buildable (spec 13.3):
     * a unit data (the base class hands the implementation a {@code protected} factory) or an
     * exposed data (its {@code decoder} is public). A non-unit, unexposed one is E1305 — Java has
     * no way to mint it.
     */
    /**
     * An anonymous union appears only in a behavior's output; a parameter type is always a single
     * named type, a named sum included (spec 8.6, 12.2). A parameter written as {@code A | B} — a
     * {@code RetType} with more than one arm — is rejected: declare {@code data AB = A | B} and take
     * {@code (x: AB)}, so the input has a name the reader and the JVM can hold onto.
     */
    private static void rejectAnonymousUnionParams(Ast.SpecBehavior spec) {
        for (Ast.Param p : spec.params()) {
            if (p.type().arms().size() > 1) {
                String union = p.type().arms().stream()
                        .map(Ast.TypeRef::name)
                        .collect(java.util.stream.Collectors.joining(" | "));
                throw new CompileException(p.pos(), "parameter `" + p.name()
                        + "` has an anonymous union type `" + union + "`; a parameter type must be a"
                        + " single named type — declare `data ... = " + union
                        + "` and take that name (spec 8.6, 12.2)");
            }
        }
    }

    private static void checkInjectionConstructs(Ast.SpecBehavior spec, Map<String, Ast.Def> symbols,
                                                 boolean exposeAll, Set<String> exposed) {
        for (String c : spec.constructs()) {
            Ast.Def d = symbols.get(c);
            if (d == null || d instanceof Ast.UnitData) {
                continue;   // unknown names are caught elsewhere; a unit has a generated factory
            }
            if (!exposeAll && !exposed.contains(c)) {
                throw new CompileException(spec.pos(), "E1305", "Injected behavior `" + spec.name()
                        + "` declares `constructs " + c + "`, but " + c + " is neither a unit data nor "
                        + "exposed. Java cannot build it: no factory is generated and its decoder is not "
                        + "public. Expose " + c + ", or make it a unit data.");
            }
        }
    }

    /**
     * Every stage after the first takes exactly one input (spec 14.1): {@code >->} hands a single
     * value along.
     *
     * <p>The first stage is not restricted — it consumes the pipeline's own arguments, and the
     * pipeline simply takes what it takes. The spec DSL relies on this
     * (`behavior 却下して差し戻す = 却下する >-> 差し戻す`, where `却下する` reads
     * `事前承認待ち AND 却下者ID`); requiring the whole chain to be single-input would reject the
     * very line 14.1 cites.
     */
    private static void checkStagesAreSingleInput(Ast.Module module) {
        Map<String, Integer> arity = new HashMap<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.SpecBehavior spec) {
                arity.put(spec.name(), spec.params().size());
            }
        }
        Map<String, List<String>> pipeStages = pipelineStages(module);
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (!(b instanceof Ast.PipeBehavior pipe)) {
                continue;
            }
            // check the flattened stages: a named intermediate splices in its own first stage, which
            // then sits after `>->` and so must be single-input too (spec 14.1, 14.2)
            List<String> stages = flattenStages(pipe.stages(), pipeStages, pipe.pos());
            for (int i = 1; i < stages.size(); i++) {
                String stage = stages.get(i);
                Integer n = arity.get(stage);
                if (n != null && n != 1) {
                    throw new CompileException(pipe.pos(),
                            "`" + stage + "` takes " + n + " inputs, so it cannot follow `>->` in `"
                                    + pipe.name() + "`. Every stage after the first takes one input: "
                                    + "call it inline or open the branches with `match` instead "
                                    + "(spec 14.1). Only the first stage may take several.");
                }
            }
        }
    }

    /** A required behavior's input and success types (for typing calls). */
    /** The input and success types of a required (injected) behavior, for typing an inline call to
     * it. Public so the backend can build the same view when it re-types a closure body. */
    public record ReqSig(Type param, Type success) {}

    /** The distinct injection targets a fn body calls, in first-seen order. Calls may appear
     * anywhere in an expression (e.g. inline in a record literal), not only bound to a let. */
    public static List<String> requiredCalls(Ast.Expr body, java.util.Set<String> requiredNames) {
        List<String> calls = new java.util.ArrayList<>();
        collectRequiredCalls(body, requiredNames, calls);
        return calls;
    }

    private static void collectRequiredCalls(Ast.Expr e, Set<String> requiredNames, List<String> out) {
        switch (e) {
            case Ast.Call call -> {
                if (requiredNames.contains(call.fn()) && !out.contains(call.fn())) {
                    out.add(call.fn());
                }
                call.args().forEach(a -> collectRequiredCalls(a, requiredNames, out));
            }
            case Ast.NewData nd -> nd.inits().forEach(i -> collectRequiredCalls(i.value(), requiredNames, out));
            case Ast.FieldAccess fa -> collectRequiredCalls(fa.target(), requiredNames, out);
            case Ast.Binary bin -> {
                collectRequiredCalls(bin.left(), requiredNames, out);
                collectRequiredCalls(bin.right(), requiredNames, out);
            }
            case Ast.Match m -> {
                collectRequiredCalls(m.scrutinee(), requiredNames, out);
                m.cases().forEach(c -> collectRequiredCalls(c.body(), requiredNames, out));
            }
            case Ast.If iff -> {
                collectRequiredCalls(iff.cond(), requiredNames, out);
                collectRequiredCalls(iff.then(), requiredNames, out);
                collectRequiredCalls(iff.els(), requiredNames, out);
            }
            case Ast.ListLit lit -> lit.elements().forEach(el -> collectRequiredCalls(el, requiredNames, out));
            case Ast.ListComp comp -> {
                collectRequiredCalls(comp.element(), requiredNames, out);
                comp.guards().forEach(g -> collectRequiredCalls(g, requiredNames, out));
            }
            case Ast.LetIn li -> {
                collectRequiredCalls(li.value(), requiredNames, out);
                collectRequiredCalls(li.body(), requiredNames, out);
            }
            // a block's requirements float out to the behavior that passes it (spec 12.5, 29)
            case Ast.Block block -> collectRequiredCalls(block.body(), requiredNames, out);
            default -> { }
        }
    }

    /**
     * A behavior's input and output types.
     *
     * <p>{@code ins} is the whole parameter list. Only the first stage of a pipeline may have more
     * than one: {@code >->} hands a single value along, so every stage after the first takes one
     * input (spec 14.1). {@link #in} is for those.
     */
    public record Sig(List<Type> ins, Type out) {
        public Sig(Type in, Type out) {
            this(List.of(in), out);
        }

        /** The sole input. Only call this for a stage after the first, which takes exactly one. */
        public Type in() {
            return ins.get(0);
        }
    }

    /** Builds the input/output signature of every behavior, checking pipeline composition. */
    public static Map<String, Sig> signatures(Ast.Module module, Map<String, Ast.Def> symbols) {
        return signatures(module, symbols, Map.of());
    }

    /**
     * Builds the input/output signature of every behavior, checking pipeline composition. The
     * {@code imported} map seeds the resolvable behaviors with those imported from other modules
     * (spec 4, 14), so a stage naming an imported behavior resolves through {@link #stageSig}.
     */
    public static Map<String, Sig> signatures(Ast.Module module, Map<String, Ast.Def> symbols,
                                              Map<String, Sig> imported) {
        Set<String> fnNames = new HashSet<>();
        for (Ast.FnDef fn : module.fns()) {
            fnNames.add(fn.name());
        }
        Map<String, Sig> sigs = new HashMap<>(imported);
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.SpecBehavior spec) {
                if (fnNames.contains(spec.name())) {
                    // implemented: any arity — a multi-input behavior can be a pipeline's first stage (14.1)
                    List<Type> ins = new ArrayList<>();
                    for (Ast.Param p : spec.params()) {
                        ins.add(successType(p.type(), symbols));
                    }
                    sigs.put(spec.name(), new Sig(ins, successType(spec.ret(), symbols)));
                } else if (spec.params().size() == 1) {
                    // injected: only a single-input one can be a stage; a zero-arg one cannot (14.1)
                    sigs.put(spec.name(), new Sig(successType(spec.params().get(0).type(), symbols),
                            successType(spec.ret(), symbols)));
                }
            }
        }
        Map<String, List<String>> pipeStages = pipelineStages(module);
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.PipeBehavior pipe) {
                sigs.put(pipe.name(), pipeSig(pipe, sigs, symbols, pipeStages));
            }
        }
        return sigs;
    }

    /** Maps each pipeline behavior's name to its declared stages (for flattening, spec 14.2). */
    public static Map<String, List<String>> pipelineStages(Ast.Module module) {
        Map<String, List<String>> stages = new HashMap<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.PipeBehavior pipe) {
                stages.put(pipe.name(), pipe.stages());
            }
        }
        return stages;
    }

    /**
     * Flattens a pipeline's stage list, splicing any stage that is itself a pipeline into its own
     * (recursively flattened) stages (spec 14.2). This is what makes {@code >->} associative:
     * {@code half >-> finish} with {@code half = split >-> work} routes over {@code split, work,
     * finish}, exactly as the flat form would, so a retired arm stays retired across a named
     * intermediate. A pipeline viewed on its own still has the merged output its own stages produce.
     */
    public static List<String> flattenStages(List<String> stages, Map<String, List<String>> pipeStages,
                                             SourcePos pos) {
        List<String> out = new ArrayList<>();
        flattenInto(stages, pipeStages, out, new LinkedHashSet<>(), pos);
        return out;
    }

    private static void flattenInto(List<String> stages, Map<String, List<String>> pipeStages,
                                    List<String> out, Set<String> inProgress, SourcePos pos) {
        for (String s : stages) {
            List<String> sub = pipeStages.get(s);
            if (sub == null) {
                out.add(s);
                continue;
            }
            if (!inProgress.add(s)) {
                throw new CompileException(pos, "pipeline `" + s + "` composes with itself (a cycle)");
            }
            flattenInto(sub, pipeStages, out, inProgress, pos);
            inProgress.remove(s);
        }
    }

    /** The signature of a pipeline stage. Only behaviors compose with {@code >->}; decode/encode
     * are boundary edges, not stages (spec 14.1). */
    public static Sig stageSig(String stage, Map<String, Sig> sigs, Map<String, Ast.Def> symbols,
                               SourcePos pos) {
        if (stage.endsWith(".decoder") || stage.endsWith(".encoder")) {
            throw new CompileException(pos, "decode/encode are boundary edges, not pipeline stages; "
                    + "`>->` composes behaviors only (spec 14.1)");
        }
        Sig s = sigs.get(stage);
        if (s == null) {
            throw new CompileException(pos, "unknown behavior `" + stage + "` in pipeline"
                    + Suggest.hint(stage, sigs.keySet()));
        }
        return s;
    }

    private static Sig pipeSig(Ast.PipeBehavior pipe, Map<String, Sig> sigs, Map<String, Ast.Def> symbols,
                               Map<String, List<String>> pipeStages) {
        // flatten nested pipeline stages so `>->` is associative (spec 14.2)
        List<String> stages = flattenStages(pipe.stages(), pipeStages, pipe.pos());
        Sig first = stageSig(stages.get(0), sigs, symbols, pipe.pos());
        Type mainline = first.out();
        Set<String> retired = new LinkedHashSet<>();
        for (int i = 1; i < stages.size(); i++) {
            mainline = route(mainline, stageSig(stages.get(i), sigs, symbols, pipe.pos()),
                    retired, symbols, pipe.pos());
        }
        Type out = withRetired(mainline, retired);
        // an optional declared output must match the inferred one exactly (spec 14.5): neither a
        // missing arm (too narrow) nor an extra one (too wide) is accepted.
        if (pipe.declaredOut() != null) {
            Set<String> inferred = leafArms(out, symbols);
            Set<String> declared = leafArms(successType(pipe.declaredOut(), symbols), symbols);
            if (!inferred.equals(declared)) {
                throw new CompileException(pipe.pos(), "E1604", "behavior " + pipe.name()
                        + " declares -> " + armList(declared) + ", but the pipeline produces "
                        + armList(inferred) + ". Update the declared output or handle the arm.");
            }
        }
        // the pipeline takes whatever its first stage takes (spec 14.1)
        return new Sig(first.ins(), out);
    }

    /** Formats a set of arm names as {@code A | B} (sorted, for a stable diagnostic). */
    private static String armList(Set<String> arms) {
        return String.join(" | ", new java.util.TreeSet<>(arms));
    }

    /** The pipeline's output: what the last stage yields, plus everything that left the main line. */
    private static Type withRetired(Type mainline, Set<String> retired) {
        if (retired.isEmpty()) {
            return mainline;
        }
        Set<String> all = new LinkedHashSet<>(armNamesOf(mainline));
        if (all.isEmpty()) {
            throw new IllegalStateException("cannot merge non-data stage output with retired arms");
        }
        all.addAll(retired);
        return armSetType(all);
    }

    /** The main-line leaf arms {@code g} accepts — the ones the backend routes into it (spec 14.2). */
    public static List<String> mainlineArms(Type mainline, Sig g, Map<String, Ast.Def> symbols) {
        List<String> accepted = new ArrayList<>();
        for (String arm : leafArms(mainline, symbols)) {
            if (assignable(Type.ref(arm), g.in(), symbols)) {
                accepted.add(arm);
            }
        }
        return accepted;
    }

    /** The main line after {@code g} runs, for the backend's routing walk. */
    public static Type stageOut(Type mainline, Sig g, Map<String, Ast.Def> symbols, SourcePos pos) {
        return route(mainline, g, new LinkedHashSet<>(), symbols, pos);
    }

    /**
     * One step of type-routed composition (spec 14.2). Returns the new main line — what {@code g}
     * yields — and adds the arms {@code g} did not accept to {@code retired}.
     *
     * <p>An arm that leaves the main line does not come back: later stages are only offered the
     * main line. That is what makes this Railway (14.2). Feeding the retired arms onward instead
     * would let a stage pick up something an earlier stage had already dropped, which changes the
     * meaning of a pipeline depending on where it is split.
     *
     * <p>Naming an intermediate does not lose the split (spec 14.2): a pipeline stage is flattened
     * into its own stages before routing ({@link #flattenStages}), so `fg >-> h` with
     * `fg = f >-> g` routes over `f, g, h` — a retired arm stays retired, exactly as in the flat
     * `f >-> g >-> h`. That flattening is what makes `>->` associative; a value never carries a mark
     * saying it once left a main line (2.6), the plumbing is structural. Viewed on its own, `fg`
     * still has the merged sum `f`+`g` produce as its output.
     */
    private static Type route(Type mainline, Sig g, Set<String> retired, Map<String, Ast.Def> symbols,
                              SourcePos pos) {
        Type in = g.in();
        if (isDataLike(mainline)) {
            Set<String> consumed = new LinkedHashSet<>();
            Set<String> passed = new LinkedHashSet<>();
            // route over the leaf arms: a named sum output splits into its members, so a stage that
            // accepts one of them consumes it while the rest retire (spec 8.3, 14.2)
            for (String arm : leafArms(mainline, symbols)) {
                if (assignable(Type.ref(arm), in, symbols)) {
                    consumed.add(arm);
                } else {
                    passed.add(arm);
                }
            }
            if (consumed.isEmpty()) {
                throw new CompileException(pos, "E1701",
                        "Cannot compose behaviors: no output arm of the left behavior is accepted by "
                                + "the right behavior's input. Left output: " + mainline + ", right input: " + in);
            }
            retired.addAll(passed);
            return g.out();
        }
        if (!mainline.equals(in)) {
            throw new CompileException(pos, "E1701",
                    "Cannot compose behaviors. Left output: " + mainline + ", right input: " + in);
        }
        return g.out();
    }

    /**
     * Constructing an invariant-bearing data goes through {@code __construct}, and on violation it
     * aborts (spec 7.3, 9.4). The backend only emits that checked construction (and its abort) in
     * tail position, so for now allow an invariant-bearing construction there and nowhere else.
     * This is a backend limitation, not a semantic rule — a violation aborts wherever it happens.
     *
     * <p>{@code e} is in tail position, as are the branches of an {@code if} and the body of a
     * {@code let}. A desugared {@code require} (spec 16.4) is an {@code if}, so the construction
     * after a guard stays in tail position. {@code match} arms are not treated as tail: the
     * backend emits a match as a value-producing expression, which does not yet route the checked
     * construction.
     */
    private static void checkInvariantConstructInTail(Ast.Expr e, Map<String, Ast.Def> symbols) {
        switch (e) {
            case Ast.If iff -> {
                forbidInvariantConstruct(iff.cond(), symbols);
                checkInvariantConstructInTail(iff.then(), symbols);
                checkInvariantConstructInTail(iff.els(), symbols);
            }
            case Ast.LetIn li -> {
                forbidInvariantConstruct(li.value(), symbols);
                checkInvariantConstructInTail(li.body(), symbols);
            }
            case Ast.NewData nd when isInvariantBearing(nd.typeName(), symbols) ->
                    nd.inits().forEach(i -> forbidInvariantConstruct(i.value(), symbols));
            default -> forbidInvariantConstruct(e, symbols);
        }
    }

    /**
     * Only required behaviors may be called from a body; other behaviors compose with {@code >->}
     * (spec 14.1). Checked up front so the diagnostic names the rule rather than reporting the
     * behavior as an unknown function.
     */
    private static void rejectNonRequiredCalls(Ast.Expr e, Set<String> allBehaviors,
                                               Map<String, ReqSig> reqSigs) {
        if (e instanceof Ast.Call call && allBehaviors.contains(call.fn())
                && !reqSigs.containsKey(call.fn())) {
            throw new CompileException(call.pos(),
                    "only required behaviors can be called from a body; compose others with `>->`");
        }
        forEachChild(e, c -> rejectNonRequiredCalls(c, allBehaviors, reqSigs));
    }

    /** Applies {@code f} to every direct subexpression of {@code e}. */
    private static void forEachChild(Ast.Expr e, java.util.function.Consumer<Ast.Expr> f) {
        switch (e) {
            case Ast.NewData nd -> nd.inits().forEach(i -> f.accept(i.value()));
            case Ast.FieldAccess fa -> f.accept(fa.target());
            case Ast.Call call -> call.args().forEach(f);
            case Ast.Binary bin -> {
                f.accept(bin.left());
                f.accept(bin.right());
            }
            case Ast.Match m -> {
                f.accept(m.scrutinee());
                m.cases().forEach(c -> f.accept(c.body()));
            }
            case Ast.If iff -> {
                f.accept(iff.cond());
                f.accept(iff.then());
                f.accept(iff.els());
            }
            case Ast.ListLit lit -> lit.elements().forEach(f);
            case Ast.ListComp comp -> {
                f.accept(comp.element());
                comp.guards().forEach(f);
            }
            case Ast.LetIn li -> {
                f.accept(li.value());
                f.accept(li.body());
            }
            default -> { }
        }
    }

    public static boolean isInvariantBearing(String typeName, Map<String, Ast.Def> symbols) {
        return symbols.get(typeName) instanceof Ast.Data d && !effectiveInvariants(d, symbols).isEmpty();
    }

    private static void forbidInvariantConstruct(Ast.Expr e, Map<String, Ast.Def> symbols) {
        if (e instanceof Ast.NewData nd && isInvariantBearing(nd.typeName(), symbols)) {
            throw new CompileException(nd.pos(), "invariant-bearing `" + nd.typeName()
                    + "` can only be constructed as the behavior's result expression");
        }
        switch (e) {
            case Ast.NewData nd -> nd.inits().forEach(i -> forbidInvariantConstruct(i.value(), symbols));
            case Ast.FieldAccess fa -> forbidInvariantConstruct(fa.target(), symbols);
            case Ast.Call call -> call.args().forEach(a -> forbidInvariantConstruct(a, symbols));
            case Ast.Binary bin -> {
                forbidInvariantConstruct(bin.left(), symbols);
                forbidInvariantConstruct(bin.right(), symbols);
            }
            case Ast.Match m -> {
                forbidInvariantConstruct(m.scrutinee(), symbols);
                m.cases().forEach(c -> forbidInvariantConstruct(c.body(), symbols));
            }
            case Ast.If iff -> {
                forbidInvariantConstruct(iff.cond(), symbols);
                forbidInvariantConstruct(iff.then(), symbols);
                forbidInvariantConstruct(iff.els(), symbols);
            }
            case Ast.ListLit lit -> lit.elements().forEach(el -> forbidInvariantConstruct(el, symbols));
            case Ast.ListComp comp -> {
                forbidInvariantConstruct(comp.element(), symbols);
                comp.guards().forEach(g -> forbidInvariantConstruct(g, symbols));
            }
            default -> { }
        }
    }

    /**
     * The data types {@code e} constructs.
     *
     * <p>{@code bound} carries the names in scope, because a bare identifier is a unit data's
     * construction only when nothing has bound it — a local of the same name wins (spec 8.4).
     * Without it, a parameter named after a unit data was read as constructing that unit.
     */
    private static void collectConstructs(Ast.Expr e, Set<String> out, Map<String, Ast.Def> symbols,
                                          Set<String> bound) {
        switch (e) {
            case Ast.LetIn li -> {
                collectConstructs(li.value(), out, symbols, bound);
                Set<String> inner = new HashSet<>(bound);
                inner.add(li.name());
                collectConstructs(li.body(), out, symbols, inner);
            }
            case Ast.NewData nd -> {
                out.add(nd.typeName());
                for (Ast.FieldInit init : nd.inits()) {
                    collectConstructs(init.value(), out, symbols, bound);
                }
            }
            case Ast.FieldAccess fa -> collectConstructs(fa.target(), out, symbols, bound);
            case Ast.Call call -> call.args().forEach(a -> collectConstructs(a, out, symbols, bound));
            case Ast.Binary bin -> {
                collectConstructs(bin.left(), out, symbols, bound);
                collectConstructs(bin.right(), out, symbols, bound);
            }
            case Ast.Neg neg -> collectConstructs(neg.operand(), out, symbols, bound);
            case Ast.Match m -> {
                collectConstructs(m.scrutinee(), out, symbols, bound);
                for (Ast.Case c : m.cases()) {
                    Set<String> inner = new HashSet<>(bound);
                    if (c.binding() != null) {
                        inner.add(c.binding());
                    }
                    collectConstructs(c.body(), out, symbols, inner);
                }
            }
            case Ast.If iff -> {
                collectConstructs(iff.cond(), out, symbols, bound);
                collectConstructs(iff.then(), out, symbols, bound);
                collectConstructs(iff.els(), out, symbols, bound);
            }
            case Ast.ListLit lit -> lit.elements().forEach(el -> collectConstructs(el, out, symbols, bound));
            case Ast.ListComp comp -> {
                collectConstructs(comp.element(), out, symbols, bound);
                comp.guards().forEach(g -> collectConstructs(g, out, symbols, bound));
            }
            case Ast.Block block -> {
                // a block builds under the enclosing behavior's permission (spec 12.5)
                Set<String> inner = new HashSet<>(bound);
                inner.addAll(block.params());
                collectConstructs(block.body(), out, symbols, inner);
            }
            // a bare name that resolves to a unit data is that unit's construction (spec 8.4)
            case Ast.Var v when !bound.contains(v.name())
                    && symbols.get(v.name()) instanceof Ast.UnitData -> out.add(v.name());
            case Ast.IntLit ignored -> { }
            case Ast.DecimalLit ignored -> { }
            case Ast.StringLit ignored -> { }
            case Ast.BoolLit ignored -> { }
            case Ast.Var ignored -> { }
        }
    }

    /** Builds the name → definition table for a module. */
    public static Map<String, Ast.Def> symbols(Ast.Module module) {
        Map<String, Ast.Def> symbols = new HashMap<>();
        for (Ast.Def def : module.defs()) {
            if (symbols.put(def.name(), def) != null) {
                throw new CompileException(def.pos(), "duplicate data `" + def.name() + "`");
            }
        }
        return symbols;
    }

    private static void checkSum(Ast.SumData sum, Map<String, Ast.Def> symbols) {
        for (String arm : sum.arms()) {
            if (!symbols.containsKey(arm)) {
                throw new CompileException(sum.pos(),
                        "unknown arm `" + arm + "` in sum `" + sum.name() + "`");
            }
        }
        sum.decoder().ifPresent(disc -> {
            // a derived codec dispatches over the leaves, so a nested sum's arms count too (8.3, 10.3)
            Set<String> dispatchable = leafArms(Type.ref(sum.name()), symbols);
            for (Ast.Variant v : disc.variants()) {
                Ast.Def armDef = symbols.get(v.armType());
                if (armDef == null || !dispatchable.contains(v.armType())) {
                    throw new CompileException(v.pos(),
                            "variant `" + v.armType() + "` is not an arm of `" + sum.name() + "`");
                }
                // a unit-data arm has an implicit (field-less) decoder generated on its class;
                // an arm may itself be a sum (spec 8.3's nested `自社負担 | 先方負担`)
                boolean armDecodes = armDef instanceof Ast.UnitData
                        || (armDef instanceof Ast.Data d && d.decoder().isPresent())
                        || (armDef instanceof Ast.SumData s && s.decoder().isPresent());
                if (!armDecodes) {
                    throw new CompileException(v.pos(),
                            "variant `" + v.armType() + "` needs a decoder");
                }
            }
        });
        sum.encoder().ifPresent(enc -> {
            Set<String> covered = new HashSet<>();
            Set<String> encodable = leafArms(Type.ref(sum.name()), symbols);
            for (Ast.EncVariant v : enc.variants()) {
                if (!encodable.contains(v.armType())) {
                    throw new CompileException(v.pos(),
                            "`" + v.armType() + "` is not an arm of `" + sum.name() + "`");
                }
                Ast.Def armDef = symbols.get(v.armType());
                boolean armEncodes = armDef instanceof Ast.UnitData
                        || (armDef instanceof Ast.Data d && d.encoder().isPresent())
                        || (armDef instanceof Ast.SumData s && s.encoder().isPresent());
                if (!armEncodes) {
                    throw new CompileException(v.pos(), "arm `" + v.armType() + "` needs an encoder");
                }
                covered.add(v.armType());
            }
            for (String arm : encodable) {
                if (!covered.contains(arm)) {
                    throw new CompileException(enc.pos(),
                            "encoder for `" + sum.name() + "` is missing arm `" + arm + "`");
                }
            }
        });
    }

    /** Effective field name → type (included data flattened first, then own fields). */
    public static Map<String, Type> fieldTypes(Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> types = new LinkedHashMap<>();
        for (String inc : data.includes()) {
            if (!(symbols.get(inc) instanceof Ast.Data id)) {
                throw new CompileException(data.pos(),
                        "cannot `include " + inc + "` (not a product data)");
            }
            for (Map.Entry<String, Type> e : fieldTypes(id, symbols).entrySet()) {
                if (types.put(e.getKey(), e.getValue()) != null) {
                    throw new CompileException(data.pos(), "E1004", "Field `" + e.getKey()
                            + "` from `include " + inc + "` conflicts with a field of `" + data.name() + "`.");
                }
            }
        }
        for (Ast.Field f : data.fields()) {
            if (types.put(f.name(), resolveType(f.type(), symbols)) != null) {
                throw new CompileException(f.pos(), "E1004",
                        "duplicate field `" + f.name() + "` in `" + data.name() + "`");
            }
        }
        return types;
    }

    /** All invariants that apply to a data: included data's invariants first, then its own. */
    public static List<Ast.Expr> effectiveInvariants(Ast.Data data, Map<String, Ast.Def> symbols) {
        List<Ast.Expr> invs = new ArrayList<>();
        for (String inc : data.includes()) {
            if (symbols.get(inc) instanceof Ast.Data id) {
                invs.addAll(effectiveInvariants(id, symbols));
            }
        }
        data.invariant().ifPresent(invs::add);
        return invs;
    }

    private static void checkData(Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> fields = fieldTypes(data, symbols);

        data.invariant().ifPresent(expr -> {
            Type t = typeOf(expr, fields, data, symbols);
            if (t != Type.BOOL) {
                throw new CompileException(expr.pos(), "E1101",
                        "Invariant expression must have type Bool. Found: " + t);
            }
        });

        data.decoder().ifPresent(dec -> checkDecoder(dec, data, fields, symbols));
        data.encoder().ifPresent(enc -> checkEncoder(enc, data, symbols));
    }

    private static void checkDecoder(Ast.DecoderDef dec, Ast.Data data, Map<String, Type> fields,
                                     Map<String, Ast.Def> symbols) {
        switch (dec) {
            case Ast.PrimDecoder prim -> {
                Type inputType = primType(prim.from());
                Map<String, Type> env = new HashMap<>();
                env.put(prim.inputName(), inputType);
                for (Ast.DecStmt stmt : prim.stmts()) {
                    switch (stmt) {
                        case Ast.Let let -> env.put(let.name(), typeOf(let.value(), env, data, symbols));
                        case Ast.Require req -> requireType(req.cond(), Type.BOOL, env, data, symbols,
                                NO_REQS, "require condition");
                    }
                }
                checkConstruct(prim.result(), data, fields, env, symbols);
            }
            case Ast.ObjectDecoder obj -> {
                Map<String, Type> env = new HashMap<>();
                for (Ast.Bind bind : obj.binds()) {
                    env.put(bind.name(), decRefType(bind.ref(), symbols));
                }
                checkConstruct(obj.result(), data, fields, env, symbols);
            }
            case Ast.NewtypeDecoder nt -> {
                Map<String, Type> env = new HashMap<>();
                env.put(nt.inputName(), decRefType(nt.inner(), symbols));
                checkConstruct(nt.result(), data, fields, env, symbols);
            }
        }
    }

    public static Type primType(Ast.RawKind kind) {
        return switch (kind) {
            case TEXT -> Type.STRING;
            case INT -> Type.INT;
            case BOOL -> Type.BOOL;
            case DECIMAL -> Type.DECIMAL;
            case DATE -> Type.DATE;
            case DATETIME -> Type.DATETIME;
        };
    }

    public static Type primType(Ast.PrimKind kind) {
        return switch (kind) {
            case STRING -> Type.STRING;
            case INT -> Type.INT;
            case BOOL -> Type.BOOL;
            case DECIMAL -> Type.DECIMAL;
            case DATE -> Type.DATE;
            case DATETIME -> Type.DATETIME;
        };
    }

    private static Type decRefType(Ast.DecRef ref, Map<String, Ast.Def> symbols) {
        return switch (ref) {
            case Ast.PrimDecRef p -> primType(p.kind());
            case Ast.DataDecRef d -> {
                Ast.Def def = symbols.get(d.typeName());
                boolean hasDecoder = (def instanceof Ast.Data dd && dd.decoder().isPresent())
                        || (def instanceof Ast.SumData s && s.decoder().isPresent());
                if (!hasDecoder) {
                    throw new CompileException(d.pos(),
                            "`" + d.typeName() + "` has no decoder to call `" + d.typeName() + ".decoder`");
                }
                yield Type.ref(d.typeName());
            }
            case Ast.ListDecRef l -> Type.list(decRefType(l.element(), symbols));
            case Ast.OptionDecRef o -> Type.option(decRefType(o.element(), symbols));
            case Ast.MapDecRef mp -> Type.map(decRefType(mp.value(), symbols));
        };
    }

    private static void checkConstruct(Ast.Construct c, Ast.Data data, Map<String, Type> fields,
                                       Map<String, Type> env, Map<String, Ast.Def> symbols) {
        if (!c.typeName().equals(data.name())) {
            throw new CompileException(c.pos(),
                    "decoder for `" + data.name() + "` must construct `" + data.name()
                            + "`, but constructs `" + c.typeName() + "`");
        }
        checkConstruction(c.typeName(), c.inits(), c.spreads(), c.pos(), fields, env, data, symbols, NO_REQS);
    }

    private static void checkConstruction(String typeName, List<Ast.FieldInit> inits, List<String> spreads,
                                          SourcePos pos, Map<String, Type> fields, Map<String, Type> env,
                                          Ast.Data data, Map<String, Ast.Def> symbols,
                                          Map<String, ReqSig> reqs) {
        Map<String, Ast.FieldInit> byName = new HashMap<>();
        for (Ast.FieldInit init : inits) {
            if (byName.put(init.name(), init) != null) {
                throw new CompileException(init.pos(), "duplicate field `" + init.name() + "`");
            }
            Type ft = fields.get(init.name());
            if (ft == null) {
                throw new CompileException(init.pos(),
                        "`" + init.name() + "` is not a field of `" + typeName + "`");
            }
            Type vt = typeOf(init.value(), env, data, symbols, reqs);
            if (!assignable(vt, ft, symbols)) {   // an arm value widens to its sum-typed field (spec 8.3)
                throw new CompileException(init.pos(),
                        "field `" + init.name() + "` expects " + ft + " but got " + vt);
            }
        }
        Map<String, Type> provided = new HashMap<>();
        for (String sp : spreads) {
            if (!(env.get(sp) instanceof Type.Ref ref)
                    || !(symbols.get(ref.name()) instanceof Ast.Data sd)) {
                throw new CompileException(pos, "spread `.." + sp + "` must be a data value");
            }
            provided.putAll(fieldTypes(sd, symbols));
        }
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            if (byName.containsKey(f.getKey())) {
                continue;
            }
            Type pv = provided.get(f.getKey());
            if (pv == null) {
                throw new CompileException(pos, "E1005",
                        "construction of `" + typeName + "` is missing field `" + f.getKey() + "`");
            }
            if (!assignable(pv, f.getValue(), symbols)) {
                throw new CompileException(pos, "spread provides `" + f.getKey() + "` as " + pv
                        + " but `" + typeName + "` needs " + f.getValue());
            }
        }
    }

    private static void checkEncoder(Ast.EncoderDef enc, Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> env = Map.of(enc.selfName(), Type.ref(data.name()));
        checkRawExpr(enc.result(), env, data, symbols);
    }

    private static void checkRawExpr(Ast.RawExpr raw, Map<String, Type> env, Ast.Data data,
                                     Map<String, Ast.Def> symbols) {
        switch (raw) {
            case Ast.TextRaw t -> requireType(t.arg(), Type.STRING, env, data, symbols, NO_REQS,
                    "argument of Text");
            case Ast.IntRaw i -> requireType(i.arg(), Type.INT, env, data, symbols, NO_REQS,
                    "argument of Int");
            case Ast.BoolRaw b -> requireType(b.arg(), Type.BOOL, env, data, symbols, NO_REQS,
                    "argument of Bool");
            case Ast.DecimalRaw d -> requireType(d.arg(), Type.DECIMAL, env, data, symbols, NO_REQS,
                    "argument of Decimal");
            case Ast.IsoTextRaw t -> {
                Type at = typeOf(t.arg(), env, data, symbols);
                if (at != Type.DATE && at != Type.DATETIME) {
                    throw new CompileException(t.pos(), "ISO text encoder expects Date or DateTime, got " + at);
                }
            }
            case Ast.OptionRaw o -> {
                Type at = typeOf(o.access(), env, data, symbols);
                if (!(at instanceof Type.OptionOf oo)) {
                    throw new CompileException(o.pos(), "optional encoder expects an Option, got " + at);
                }
                Map<String, Type> inner = new HashMap<>(env);
                inner.put(o.elemVar(), oo.element());
                checkRawExpr(o.inner(), inner, data, symbols);
            }
            case Ast.ObjectRaw o -> {
                for (Ast.RawEntry entry : o.entries()) {
                    checkRawExpr(entry.value(), env, data, symbols);
                }
            }
            case Ast.EncodeRaw e -> {
                Ast.Def encDef = symbols.get(e.typeName());
                boolean hasEncoder = (encDef instanceof Ast.Data ed && ed.encoder().isPresent())
                        || (encDef instanceof Ast.SumData sd && sd.encoder().isPresent());
                if (!hasEncoder) {
                    throw new CompileException(e.pos(),
                            "`" + e.typeName() + "` has no encoder to call `" + e.typeName() + ".encode`");
                }
                requireType(e.arg(), Type.ref(e.typeName()), env, data, symbols, NO_REQS,
                        "argument of " + e.typeName() + ".encode");
            }
            case Ast.ListEnc le -> {
                Type st = typeOf(le.source(), env, data, symbols);
                if (!(st instanceof Type.ListOf lo)) {
                    throw new CompileException(le.pos(), "list(...) source must be a List, got " + st);
                }
                checkEncElem(le.elem(), lo.element(), le.pos(), symbols);
            }
            case Ast.MapEnc me -> {
                Type st = typeOf(me.source(), env, data, symbols);
                if (!(st instanceof Type.MapOf mo)) {
                    throw new CompileException(me.pos(), "map encoder source must be a Map, got " + st);
                }
                checkEncElem(me.elem(), mo.value(), me.pos(), symbols);
            }
        }
    }

    private static void checkEncElem(Ast.EncElem elem, Type elemType, SourcePos pos,
                                     Map<String, Ast.Def> symbols) {
        switch (elem) {
            case Ast.PrimEnc p -> {
                if (!elemType.equals(primType(p.kind()))) {
                    throw new CompileException(pos,
                            "element encoder " + p.kind() + " does not match " + elemType);
                }
            }
            case Ast.DataEnc d -> {
                // the element may be a product or a sum: `List<事前承認理由>` holds a sum (spec 11.2)
                Ast.Def def = symbols.get(d.typeName());
                boolean hasEncoder = (def instanceof Ast.Data dd && dd.encoder().isPresent())
                        || (def instanceof Ast.SumData sd && sd.encoder().isPresent());
                if (!elemType.equals(Type.ref(d.typeName())) || !hasEncoder) {
                    throw new CompileException(pos,
                            "element encoder `" + d.typeName() + "` does not match " + elemType);
                }
            }
        }
    }

    // --- expression typing (shared with the backend) ---

    /** No required behaviors are in scope (decoders, encoders, invariants — spec 9.3, 17). */
    private static final Map<String, ReqSig> NO_REQS = Map.of();

    public static Type typeOf(Ast.Expr e, Map<String, Type> env, Ast.Data data,
                              Map<String, Ast.Def> symbols) {
        return typeOf(e, env, data, symbols, NO_REQS);
    }

    public static Type typeOf(Ast.Expr e, Map<String, Type> env, Ast.Data data,
                              Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        return switch (e) {
            case Ast.IntLit ignored -> Type.INT;
            case Ast.DecimalLit ignored -> Type.DECIMAL;
            case Ast.StringLit ignored -> Type.STRING;
            case Ast.BoolLit ignored -> Type.BOOL;
            case Ast.Neg neg -> {
                Type t = typeOf(neg.operand(), env, data, symbols, reqs);
                if (t != Type.INT && t != Type.DECIMAL) {
                    throw new CompileException(neg.pos(), "unary minus needs an Int or Decimal, got " + t);
                }
                yield t;
            }
            case Ast.LetIn li -> {
                // the binding is visible only inside the body, so a sibling branch cannot see it
                Map<String, Type> inner = new HashMap<>(env);
                if (isFunctionSelection(li.value())) {
                    // a lambda bound to a local that could not be inlined (e.g. chosen by an `if`):
                    // it is a first-class function value. Its parameter types are unannotated, so
                    // infer them from how the body applies it (spec §blocks).
                    List<Type> paramTypes = inferFnParamTypes(li.name(), li.body(), env, data, symbols, reqs);
                    inner.put(li.name(), typeFunctionValue(li.value(), paramTypes, env, data, symbols, reqs));
                } else {
                    inner.put(li.name(), typeOf(li.value(), env, data, symbols, reqs));
                }
                yield typeOf(li.body(), inner, data, symbols, reqs);
            }
            // reached only where a block escapes: it may be passed as an argument, or bound to a
            // `let` and applied, but it is not a value that can be returned or stored, because that
            // would need a runtime closure (spec 12.5)
            case Ast.Block block -> throw new CompileException(block.pos(),
                    "a block is not a value: it may be passed as an argument or bound to a `let` and "
                            + "applied, but it cannot be returned or stored in a data (spec 12.5)");
            case Ast.Var v -> {
                Type t = env.get(v.name());
                if (t != null) {
                    yield t;
                }
                // a bare name that isn't a local is a unit-data value (spec 8.4)
                if (symbols.get(v.name()) instanceof Ast.UnitData) {
                    yield Type.ref(v.name());
                }
                if (v.name().equals("null")) {
                    throw new CompileException(v.pos(), "E1301",
                            "`null` is not part of the language. Use an optional field with `?`.");
                }
                throw new CompileException(v.pos(), "unknown identifier `" + v.name() + "`"
                        + Suggest.hint(v.name(), env.keySet()));
            }
            case Ast.FieldAccess fa -> typeOfFieldAccess(fa, env, data, symbols, reqs);
            case Ast.Call call -> typeOfCall(call, env, data, symbols, reqs);
            case Ast.Binary bin -> typeOfBinary(bin, env, data, symbols, reqs);
            case Ast.NewData nd -> {
                if (!(symbols.get(nd.typeName()) instanceof Ast.Data owner)) {
                    throw new CompileException(nd.pos(), "cannot construct `" + nd.typeName() + "`");
                }
                checkConstruction(nd.typeName(), nd.inits(), nd.spreads(), nd.pos(),
                        fieldTypes(owner, symbols), env, data, symbols, reqs);
                yield Type.ref(nd.typeName());
            }
            case Ast.Match m -> typeOfMatch(m, env, data, symbols, reqs);
            case Ast.If iff -> {
                requireType(iff.cond(), Type.BOOL, env, data, symbols, reqs, "if condition");
                Type tt = typeOf(iff.then(), env, data, symbols, reqs);
                Type et = typeOf(iff.els(), env, data, symbols, reqs);
                Type empty = absorbEmptyList(tt, et);   // one arm may be `[]` (ADR-0028)
                if (empty != null) {
                    yield empty;
                }
                if (tt.equals(et)) {
                    yield tt;
                }
                if (isDataLike(tt) && isDataLike(et)) {
                    Set<String> names = new HashSet<>(namesOf(tt));
                    names.addAll(namesOf(et));
                    yield Type.union(names);
                }
                throw new CompileException(iff.pos(), "if branches disagree: " + tt + " vs " + et);
            }
            case Ast.ListLit lit -> {
                if (lit.elements().isEmpty()) {
                    yield Type.EMPTY_LIST;   // `[]`: element type fixed by context (ADR-0028)
                }
                Type elem = null;
                for (Ast.Expr el : lit.elements()) {
                    Type t = typeOf(el, env, data, symbols, reqs);
                    elem = elem == null ? t : unifyElem(elem, t, lit.pos());
                }
                yield Type.list(elem);
            }
            case Ast.ListComp comp -> {
                for (Ast.Expr g : comp.guards()) {
                    requireType(g, Type.BOOL, env, data, symbols, reqs, "guard of a comprehension");
                }
                yield Type.list(typeOf(comp.element(), env, data, symbols, reqs));
            }
        };
    }

    /** The common element type of two list positions: identical types collapse; two data-like
     * types widen to the union of their arms (so {@code [High] ++ [LowRole]} is a list of both). */
    /** When one of two joined positions ({@code if}/{@code match} arms) is the empty list {@code []}
     * and the other is a concrete list, the join is that concrete list — the empty list takes on its
     * type (ADR-0028). Returns {@code null} when neither is empty, so the caller falls through to its
     * ordinary rules. Two empty lists stay empty. */
    private static Type absorbEmptyList(Type a, Type b) {
        if (a.equals(Type.EMPTY_LIST)) {
            return b;
        }
        if (b.equals(Type.EMPTY_LIST)) {
            return a;
        }
        return null;
    }

    private static Type unifyElem(Type a, Type b, SourcePos pos) {
        if (a == Type.NOTHING) {   // the empty list absorbs into the other's element type (ADR-0028)
            return b;
        }
        if (b == Type.NOTHING) {
            return a;
        }
        if (a.equals(b)) {
            return a;
        }
        if (isDataLike(a) && isDataLike(b)) {
            Set<String> names = new HashSet<>(namesOf(a));
            names.addAll(namesOf(b));
            return Type.union(names);
        }
        throw new CompileException(pos, "list elements disagree on type: " + a + " vs " + b);
    }

    private static Type typeOfMatch(Ast.Match m, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Type st = typeOf(m.scrutinee(), env, data, symbols, reqs);
        if (st instanceof Type.OptionOf oo) {
            return typeOfOptionMatch(m, oo.element(), env, data, symbols, reqs);
        }
        if (st instanceof Type.Union union) {
            return typeOfArmsMatch(m, union.members(), "union " + union, st, env, data, symbols, reqs);
        }
        if (!(st instanceof Type.Ref ref) || !(symbols.get(ref.name()) instanceof Ast.SumData sum)) {
            throw new CompileException(m.pos(), "match requires a sum-typed value, got " + st);
        }
        return typeOfArmsMatch(m, new HashSet<>(sum.arms()), "data `" + sum.name() + "`",
                st, env, data, symbols, reqs);
    }

    /** Match over a fixed set of data arms (a named sum's arms, or an anonymous union's members).
     * A single-arm case binds that arm's type; an or-pattern ({@code A | B}) binds {@code scrutinee}
     * (the sum type), since no one arm type fits all its alternatives. Every arm must be covered
     * exactly once (E1201; a second cover is an overlap error). */
    private static Type typeOfArmsMatch(Ast.Match m, Set<String> arms, String what, Type scrutinee,
                                        Map<String, Type> env, Ast.Data data, Map<String, Ast.Def> symbols,
                                        Map<String, ReqSig> reqs) {
        Set<String> covered = new HashSet<>();
        Type branchType = null;
        for (Ast.Case c : m.cases()) {
            for (String arm : c.armTypes()) {
                if (!arms.contains(arm)) {
                    throw new CompileException(c.pos(), "`" + arm + "` is not an arm of " + what);
                }
                if (!covered.add(arm)) {
                    throw new CompileException(c.pos(), "`" + arm + "` is matched by more than one case");
                }
            }
            Type bindType = c.armTypes().size() == 1 ? armBindType(c.armTypes().get(0)) : scrutinee;
            branchType = mergeBranch(m, branchType,
                    typeOf(c.body(), bound(env, c.binding(), bindType), data, symbols, reqs), c);
        }
        for (String arm : arms) {
            if (!covered.contains(arm)) {
                throw new CompileException(m.pos(), "E1201",
                        "Non-exhaustive match for " + what + ". Missing case: " + arm);
            }
        }
        if (branchType == null) {
            throw new CompileException(m.pos(), "match has no cases");
        }
        return branchType;
    }

    /** Match over {@code Option<element>}: arms are {@code Some} (binds the element) and
     * {@code None}; both must be present (spec 16.3). */
    private static Type typeOfOptionMatch(Ast.Match m, Type element, Map<String, Type> env, Ast.Data data,
                                          Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Set<String> covered = new HashSet<>();
        Type branchType = null;
        for (Ast.Case c : m.cases()) {
            if (c.armTypes().size() != 1) {
                throw new CompileException(c.pos(), "or-patterns are not allowed in an Option match;"
                        + " use separate Some and None cases");
            }
            String armType = c.armTypes().get(0);
            Type bind = switch (armType) {
                case "Some" -> element;
                case "None" -> null;
                default -> throw new CompileException(c.pos(),
                        "`" + armType + "` is not an arm of Option; use Some or None");
            };
            covered.add(armType);
            branchType = mergeBranch(m, branchType,
                    typeOf(c.body(), bound(env, c.binding(), bind), data, symbols, reqs), c);
        }
        for (String arm : List.of("Some", "None")) {
            if (!covered.contains(arm)) {
                throw new CompileException(m.pos(), "E1201",
                        "Non-exhaustive match for Option. Missing case: " + arm);
            }
        }
        return branchType;
    }

    /** The type a match arm binds. A primitive-named arm (e.g. {@code Int} in {@code Int |
     * DivisionByZero}) binds that primitive; a data-named arm binds its data type. */
    public static Type armBindType(String armName) {
        return switch (armName) {
            case "Int" -> Type.INT;
            case "String" -> Type.STRING;
            case "Bool" -> Type.BOOL;
            case "Decimal" -> Type.DECIMAL;
            case "Date" -> Type.DATE;
            case "DateTime" -> Type.DATETIME;
            default -> Type.ref(armName);
        };
    }

    /** Extends {@code env} with {@code name -> type} when both are present; otherwise returns it as is. */
    private static Map<String, Type> bound(Map<String, Type> env, String name, Type type) {
        if (name == null || type == null) {
            return env;
        }
        Map<String, Type> benv = new HashMap<>(env);
        benv.put(name, type);
        return benv;
    }

    private static Type mergeBranch(Ast.Match m, Type branchType, Type bt, Ast.Case c) {
        if (branchType == null) {
            return bt;
        }
        if (branchType.equals(bt)) {
            return branchType;
        }
        Type empty = absorbEmptyList(branchType, bt);   // one arm may be `[]` (ADR-0028)
        if (empty != null) {
            return empty;
        }
        // arms yielding different data types widen to their union, as `if` branches do (spec 16.2)
        if (isDataLike(branchType) && isDataLike(bt)) {
            Set<String> names = new HashSet<>(namesOf(branchType));
            names.addAll(namesOf(bt));
            return Type.union(names);
        }
        throw new CompileException(c.pos(),
                "match branches disagree: " + branchType + " vs " + bt);
    }


    private static Type typeOfFieldAccess(Ast.FieldAccess fa, Map<String, Type> env, Ast.Data data,
                                          Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Type target = typeOf(fa.target(), env, data, symbols, reqs);
        if (target instanceof Type.Ref ref && symbols.get(ref.name()) instanceof Ast.Data owner) {
            Type ft = fieldTypes(owner, symbols).get(fa.field());
            if (ft != null) {
                return ft;
            }
        }
        throw new CompileException(fa.pos(), "cannot access field `" + fa.field() + "` on this value");
    }

    private static Type typeOfCall(Ast.Call call, Map<String, Type> env, Ast.Data data,
                                   Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        List<Ast.Expr> args = call.args();
        // A shipped intrinsic behaves like a built-in: check the call against its declared signature
        // (from the prelude) and yield its result type; the backend emits the primitive for its key.
        net.unit8.souther.compiler.Prelude.IntrinsicSig intrinsic =
                net.unit8.souther.compiler.Prelude.intrinsics().get(call.fn());
        if (intrinsic != null) {
            if (args.size() != intrinsic.params().size()) {
                throw new CompileException(call.pos(), call.fn() + " takes " + intrinsic.params().size()
                        + " argument(s) but is called with " + args.size());
            }
            Map<String, Type> bindings = new HashMap<>();
            for (int i = 0; i < args.size(); i++) {
                Type argType = typeOf(args.get(i), env, data, symbols, reqs);
                unify(intrinsic.params().get(i), argType, bindings, symbols, call.pos(),
                        "argument " + (i + 1) + " of " + call.fn());
            }
            return substitute(intrinsic.result(), bindings);
        }
        return switch (call.fn()) {
            case "length" -> {
                arity(call, 1);
                // one name for both, as spec 18.1 and 18.4 both call it `length`
                Type t = typeOf(args.get(0), env, data, symbols, reqs);
                if (!(t instanceof Type.ListOf) && t != Type.STRING) {
                    throw new CompileException(call.pos(),
                            "argument of length must be a String or a List but is " + t);
                }
                yield Type.INT;
            }
            // map/filter/all/any are no longer built in: they are prelude helpers derived from fold
            // (ADR-0028, souther.list), so a call to one is expanded inline before it reaches here.
            case "fold" -> {
                arity(call, 3);
                Type src = typeOf(args.get(0), env, data, symbols, reqs);
                if (!(src instanceof Type.ListOf lo)) {
                    throw new CompileException(call.pos(), "fold expects a List, got " + src);
                }
                Type acc = typeOf(args.get(1), env, data, symbols, reqs);
                Type bt = blockType(call, args.get(2), List.of(acc, lo.element()), env, data, symbols, reqs);
                if (acc.equals(Type.EMPTY_LIST)) {
                    // the seed is `[]`; the accumulator's type is the list the block grows (ADR-0028)
                    if (!(bt instanceof Type.ListOf)) {
                        throw new CompileException(call.pos(), "fold seeded with the empty list `[]` "
                                + "must build a list, but its block returns " + bt);
                    }
                    yield bt;
                }
                if (!bt.equals(acc) && absorbEmptyList(acc, bt) == null) {
                    throw new CompileException(call.pos(),
                            "fold's block must return the accumulator type " + acc + ", got " + bt);
                }
                yield acc;
            }
            case "get" -> {
                arity(call, 2);
                Type first = typeOf(args.get(0), env, data, symbols, reqs);
                if (first instanceof Type.ListOf lo) {
                    requireType(args.get(1), Type.INT, env, data, symbols, reqs, "index of get");
                    yield Type.option(lo.element());
                }
                if (first instanceof Type.MapOf mo) {
                    requireType(args.get(1), Type.STRING, env, data, symbols, reqs, "key of get");
                    yield Type.option(mo.value());
                }
                throw new CompileException(call.pos(), "get expects a List or Map, got " + first);
            }
            case "add", "subtract", "multiply" -> numericOp(call, env, data, symbols, reqs, false);
            case "compare" -> numericOp(call, env, data, symbols, reqs, true);
            case "remainder" -> {
                arity(call, 2);
                requireType(args.get(0), Type.INT, env, data, symbols, reqs, "argument 1 of remainder");
                requireType(args.get(1), Type.INT, env, data, symbols, reqs, "argument 2 of remainder");
                // partial: a zero divisor produces the DivisionByZero arm (spec 18.2)
                yield Type.union(new java.util.LinkedHashSet<>(List.of("Int", "DivisionByZero")));
            }
            case "divide" -> {
                if (args.size() == 4) {
                    // Decimal divide states its rounding: divide(a, b, scale, mode) (spec 18.3)
                    requireType(args.get(0), Type.DECIMAL, env, data, symbols, reqs, "argument 1 of divide");
                    requireType(args.get(1), Type.DECIMAL, env, data, symbols, reqs, "argument 2 of divide");
                    requireType(args.get(2), Type.INT, env, data, symbols, reqs, "scale of divide");
                    requireRoundingMode(args.get(3));
                    yield Type.union(new java.util.LinkedHashSet<>(List.of("Decimal", "DivisionByZero")));
                }
                arity(call, 2);
                requireType(args.get(0), Type.INT, env, data, symbols, reqs, "argument 1 of divide");
                requireType(args.get(1), Type.INT, env, data, symbols, reqs, "argument 2 of divide");
                yield Type.union(new java.util.LinkedHashSet<>(List.of("Int", "DivisionByZero")));
            }
            default -> {
                // a function-typed value in scope (a helper's function parameter) applied to
                // arguments — f(x) (spec §fn-declaration)
                if (env.get(call.fn()) instanceof Type.FnOf fn) {
                    if (args.size() != fn.params().size()) {
                        throw new CompileException(call.pos(), "`" + call.fn() + "` takes "
                                + fn.params().size() + " argument(s) but is applied to " + args.size());
                    }
                    for (int i = 0; i < args.size(); i++) {
                        requireType(args.get(i), fn.params().get(i), env, data, symbols, reqs,
                                "argument " + (i + 1) + " of " + call.fn());
                    }
                    yield fn.result();
                }
                // a required behavior called inline (spec 12.2, 13): type it as its success arm
                ReqSig callee = reqs.get(call.fn());
                if (callee == null) {
                    throw new CompileException(call.pos(), "E1401", "`" + call.fn()
                            + "` is not a behavior or builtin"
                            + Suggest.hint(call.fn(), reqs.keySet())
                            + ". Calling arbitrary JVM methods is not "
                            + "allowed; declare a behavior without a `let` and implement it from Java.");
                }
                if (callee.param() == null) {
                    arity(call, 0);            // `() -> R`, e.g. 現在時刻() (spec 13.1)
                } else {
                    arity(call, 1);
                    requireType(args.get(0), callee.param(), env, data, symbols, reqs,
                            "argument of " + call.fn());
                }
                yield callee.success();
            }
        };
    }

    private static Type typeOfBinary(Ast.Binary bin, Map<String, Type> env, Ast.Data data,
                                     Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        return switch (bin.op()) {
            case AND, OR -> {
                requireType(bin.left(), Type.BOOL, env, data, symbols, reqs, "operand of logical operator");
                requireType(bin.right(), Type.BOOL, env, data, symbols, reqs, "operand of logical operator");
                yield Type.BOOL;
            }
            case LT, LE, GT, GE -> {
                requireType(bin.left(), Type.INT, env, data, symbols, reqs, "operand of comparison");
                requireType(bin.right(), Type.INT, env, data, symbols, reqs, "operand of comparison");
                yield Type.BOOL;
            }
            case ADD, SUB, MUL -> {
                requireType(bin.left(), Type.INT, env, data, symbols, reqs, "operand of arithmetic");
                requireType(bin.right(), Type.INT, env, data, symbols, reqs, "operand of arithmetic");
                yield Type.INT;
            }
            case CONCAT -> {
                Type lt = typeOf(bin.left(), env, data, symbols, reqs);
                Type rt = typeOf(bin.right(), env, data, symbols, reqs);
                if (!(lt instanceof Type.ListOf lo) || !(rt instanceof Type.ListOf ro)) {
                    throw new CompileException(bin.pos(), "`++` needs two lists, got " + lt + " and " + rt);
                }
                yield Type.list(unifyElem(lo.element(), ro.element(), bin.pos()));
            }
            case EQ, NE -> {
                Type lt = typeOf(bin.left(), env, data, symbols, reqs);
                Type rt = typeOf(bin.right(), env, data, symbols, reqs);
                // two values of the same data compare by their fields (spec 16.2); across
                // different types there is nothing to compare
                if (!lt.equals(rt)) {
                    throw new CompileException(bin.pos(), "cannot compare " + lt + " with " + rt);
                }
                yield Type.BOOL;
            }
        };
    }

    /** A binary numeric op over two Int or two Decimal operands (spec 18.2, 18.3). {@code compare}
     * yields Int (-1/0/1); the arithmetic ops yield the operand type. */
    private static Type numericOp(Ast.Call call, Map<String, Type> env, Ast.Data data,
                                  Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs, boolean compare) {
        arity(call, 2);
        Type lt = typeOf(call.args().get(0), env, data, symbols, reqs);
        if (lt != Type.INT && lt != Type.DECIMAL) {
            throw new CompileException(call.pos(), call.fn() + " expects Int or Decimal, got " + lt);
        }
        requireType(call.args().get(1), lt, env, data, symbols, reqs, "argument 2 of " + call.fn());
        return compare ? Type.INT : lt;
    }

    /**
     * Types a block argument, binding its parameters to {@code paramTypes} (spec 12.5).
     *
     * <p>The parameters are visible only inside the block's body, and its requirement set is
     * whatever it calls — which flows outward into the enclosing behavior's, so nothing about
     * requirements has to be written down (spec 29).
     */
    private static Type blockType(Ast.Call call, Ast.Expr arg, List<Type> paramTypes,
                                  Map<String, Type> env, Ast.Data data,
                                  Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        if (!(arg instanceof Ast.Block block)) {
            // a function-typed value — a helper's function parameter (spec §fn-declaration) —
            // stands in for a block: check its shape and yield its result type.
            if (typeOf(arg, env, data, symbols, reqs) instanceof Type.FnOf fn) {
                if (fn.params().size() != paramTypes.size()) {
                    throw new CompileException(arg.pos(), call.fn() + " calls its function with "
                            + paramTypes.size() + " argument(s), but it takes " + fn.params().size());
                }
                for (int i = 0; i < paramTypes.size(); i++) {
                    if (!assignable(paramTypes.get(i), fn.params().get(i), symbols)) {
                        throw new CompileException(arg.pos(), call.fn() + "'s element type "
                                + paramTypes.get(i) + " is not acceptable to the function, which takes "
                                + fn.params().get(i));
                    }
                }
                return fn.result();
            }
            throw new CompileException(arg.pos(),
                    call.fn() + " expects a block, e.g. `" + call.fn() + "(xs, x -> ...)` (spec 12.5)");
        }
        if (block.params().size() != paramTypes.size()) {
            throw new CompileException(block.pos(),
                    "this block takes " + paramTypes.size() + " parameter(s), got "
                            + block.params().size());
        }
        Map<String, Type> inner = new HashMap<>(env);
        for (int i = 0; i < paramTypes.size(); i++) {
            inner.put(block.params().get(i), paramTypes.get(i));
        }
        return typeOf(block.body(), inner, data, symbols, reqs);
    }

    /** Whether an expression bound to a {@code let} is a function value: a lambda, or an {@code if}
     * whose branches are functions. Such a value cannot be inlined (the inliner leaves it), so it
     * becomes a first-class {@code Fn} (spec §blocks). */
    public static boolean producesFunction(Ast.Expr e) {
        return switch (e) {
            case Ast.Block ignored -> true;
            case Ast.If iff -> producesFunction(iff.then()) || producesFunction(iff.els());
            // a lambda returned under its capture bindings, e.g. inlining `adder(5)` leaves
            // `let $n = 5 in (x) -> x + $n` (spec §blocks)
            case Ast.LetIn li -> producesFunction(li.body());
            default -> false;
        };
    }

    /** A function value that could not be inlined away and so becomes a first-class {@code Fn}: a
     * function chosen at runtime by an {@code if} (spec §blocks). A bare lambda is not here — it is
     * either inlined at its application or, if it escapes, reported as "a block is not a value". */
    public static boolean isFunctionSelection(Ast.Expr e) {
        return !(e instanceof Ast.Block) && producesFunction(e);
    }

    /** The backend re-derives a let-bound function's parameter types the same way (spec §blocks). */
    public static List<Type> inferFnParamTypes(String name, Ast.Expr body, Map<String, Type> env,
                                               Ast.Data data, Map<String, Ast.Def> symbols) {
        return inferFnParamTypes(name, body, env, data, symbols, NO_REQS);
    }

    /** Infers a let-bound function's parameter types from how the body applies it: every
     * {@code f(args)} in the body must agree on the argument types (spec §blocks). A function that
     * is never applied cannot have its type inferred. */
    private static List<Type> inferFnParamTypes(String name, Ast.Expr body, Map<String, Type> env,
                                                Ast.Data data, Map<String, Ast.Def> symbols,
                                                Map<String, ReqSig> reqs) {
        List<List<Type>> uses = new ArrayList<>();
        collectApplications(name, body, env, data, symbols, reqs, uses);
        if (uses.isEmpty()) {
            throw new CompileException(body.pos(), "cannot infer the type of the function `" + name
                    + "`: apply it (as `" + name + "(x)`) at least once so its parameter types are "
                    + "known. A function passed on rather than applied — e.g. to a combinator — must "
                    + "be written inline instead (`map(xs, x -> ...)`)");
        }
        List<Type> first = uses.get(0);
        for (List<Type> u : uses) {
            if (!u.equals(first)) {
                throw new CompileException(body.pos(), "the function `" + name
                        + "` is applied with different argument types: " + first + " vs " + u);
            }
        }
        return first;
    }

    /** Collects the argument-type lists of every application {@code name(args)} in {@code e}. */
    private static void collectApplications(String name, Ast.Expr e, Map<String, Type> env,
                                            Ast.Data data, Map<String, Ast.Def> symbols,
                                            Map<String, ReqSig> reqs, List<List<Type>> out) {
        if (e instanceof Ast.Call call && call.fn().equals(name)) {
            List<Type> argTypes = new ArrayList<>();
            for (Ast.Expr a : call.args()) {
                argTypes.add(typeOf(a, env, data, symbols, reqs));
            }
            out.add(argTypes);
        }
        forEachChild(e, sub -> collectApplications(name, sub, env, data, symbols, reqs, out));
    }

    /** Types a function value against inferred parameter types: a lambda binds its parameters and
     * yields {@code FnOf(params, resultOfBody)}; an {@code if} requires both branches to be the same
     * function type (spec §blocks). */
    private static Type typeFunctionValue(Ast.Expr value, List<Type> paramTypes, Map<String, Type> env,
                                          Ast.Data data, Map<String, Ast.Def> symbols,
                                          Map<String, ReqSig> reqs) {
        return switch (value) {
            case Ast.Block b -> {
                if (b.params().size() != paramTypes.size()) {
                    throw new CompileException(b.pos(), "this lambda takes " + b.params().size()
                            + " parameter(s) but is applied with " + paramTypes.size());
                }
                Map<String, Type> inner = new HashMap<>(env);
                for (int i = 0; i < paramTypes.size(); i++) {
                    inner.put(b.params().get(i), paramTypes.get(i));
                }
                yield Type.fn(paramTypes, typeOf(b.body(), inner, data, symbols, reqs));
            }
            case Ast.If iff -> {
                requireType(iff.cond(), Type.BOOL, env, data, symbols, reqs, "if condition");
                Type t = typeFunctionValue(iff.then(), paramTypes, env, data, symbols, reqs);
                Type f = typeFunctionValue(iff.els(), paramTypes, env, data, symbols, reqs);
                if (!t.equals(f)) {
                    throw new CompileException(iff.pos(), "the two branches produce different function "
                            + "types: " + t + " vs " + f);
                }
                yield t;
            }
            case Ast.LetIn li -> {
                // a capture binding around the function (e.g. `let $n = 5 in (x) -> x + $n`)
                Map<String, Type> inner = new HashMap<>(env);
                inner.put(li.name(), typeOf(li.value(), env, data, symbols, reqs));
                yield typeFunctionValue(li.body(), paramTypes, inner, data, symbols, reqs);
            }
            default -> typeOf(value, env, data, symbols, reqs);
        };
    }

    /** The built-in rounding modes (spec 18.3), each a bare identifier resolving to a
     * {@code java.math.RoundingMode} — like {@code None}, a built-in value, not a data (spec 8.4). */
    public static final Set<String> ROUNDING_MODES = Set.of(
            "HALF_UP", "HALF_EVEN", "HALF_DOWN", "UP", "DOWN", "CEILING", "FLOOR");

    /** Built-in values written as bare identifiers ({@code None}, the rounding modes): a binding may
     * not take one of these names, because it would shadow the built-in and make it unreachable. */
    private static final Set<String> BUILTIN_VALUES = builtinValues();

    private static Set<String> builtinValues() {
        Set<String> s = new HashSet<>(ROUNDING_MODES);
        s.add("None");
        return Set.copyOf(s);
    }

    private static void rejectBuiltinShadow(String name, SourcePos pos) {
        if (BUILTIN_VALUES.contains(name)) {
            throw new CompileException(pos, "`" + name + "` is a built-in value and cannot be used as "
                    + "a binding name — it would shadow the built-in; choose another name");
        }
    }

    /** Rejects any binder in {@code e} — a {@code let}, {@code match} binding, or lambda parameter —
     * that takes a built-in value's name. */
    private static void rejectBuiltinShadowing(Ast.Expr e) {
        switch (e) {
            case Ast.LetIn li -> {
                rejectBuiltinShadow(li.name(), li.pos());
                rejectBuiltinShadowing(li.value());
                rejectBuiltinShadowing(li.body());
            }
            case Ast.Block b -> {
                for (String p : b.params()) {
                    rejectBuiltinShadow(p, b.pos());
                }
                rejectBuiltinShadowing(b.body());
            }
            case Ast.Match m -> {
                rejectBuiltinShadowing(m.scrutinee());
                for (Ast.Case c : m.cases()) {
                    if (c.binding() != null) {
                        rejectBuiltinShadow(c.binding(), c.pos());
                    }
                    rejectBuiltinShadowing(c.body());
                }
            }
            default -> forEachChild(e, TypeChecker::rejectBuiltinShadowing);
        }
    }

    /** The rounding-mode argument of {@code divide} is one of the built-in identifiers, written
     * bare — not an ordinary expression (spec 18.3). */
    private static void requireRoundingMode(Ast.Expr e) {
        if (!(e instanceof Ast.Var v) || !ROUNDING_MODES.contains(v.name())) {
            throw new CompileException(e.pos(), "the rounding mode of `divide` must be one of "
                    + "HALF_UP, HALF_EVEN, HALF_DOWN, UP, DOWN, CEILING, FLOOR (spec 18.3)");
        }
    }

    private static void arity(Ast.Call call, int n) {
        if (call.args().size() != n) {
            throw new CompileException(call.pos(),
                    call.fn() + " expects " + n + " argument(s), got " + call.args().size());
        }
    }

    private static void requireType(Ast.Expr e, Type expected, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs, String what) {
        Type actual = typeOf(e, env, data, symbols, reqs);
        if (!assignable(actual, expected, symbols)) {   // an arm widens to its sum (spec 8.3)
            throw new CompileException(e.pos(), what + " must be " + expected + " but is " + actual);
        }
    }

    /** Resolves a helper parameter's written type: an ordinary type or a function type. */
    public static Type resolveParamType(Ast.ParamType t, Map<String, Ast.Def> symbols) {
        return switch (t) {
            case Ast.RetType rt -> successType(rt, symbols);
            case Ast.FnType ft -> {
                List<Type> params = new ArrayList<>();
                for (Ast.RetType p : ft.params()) {
                    params.add(successType(p, symbols));
                }
                yield Type.fn(params, successType(ft.result(), symbols));
            }
        };
    }

    /** The output type of a behavior return: a single arm, or a union of two or more arms. */
    public static Type successType(Ast.RetType ret, Map<String, Ast.Def> symbols) {
        List<Type> members = new ArrayList<>();
        for (Ast.TypeRef t : ret.arms()) {
            members.add(resolveType(t, symbols));
        }
        if (members.size() == 1) {
            return members.get(0);
        }
        Set<String> names = new HashSet<>();
        for (Type m : members) {
            if (!(m instanceof Type.Ref r)) {
                throw new CompileException(ret.pos(), "union members must be data types");
            }
            names.add(r.name());
        }
        return Type.union(names);
    }

    /** Builds a Ref (one name) or Union (two or more) from a set of arm names. */
    static Type armSetType(Set<String> names) {
        if (names.size() == 1) {
            return Type.ref(names.iterator().next());
        }
        return Type.union(names);
    }

    public static boolean isDataLike(Type t) {
        return t instanceof Type.Ref || t instanceof Type.Union;
    }

    public static Set<String> namesOf(Type t) {
        if (t instanceof Type.Ref r) {
            return Set.of(r.name());
        }
        if (t instanceof Type.Union u) {
            return u.members();
        }
        return Set.of();
    }

    /** Arm names of a stage output, treating a {@code Raw} encoder output as the arm {@code "Raw"}
     * so it can be unioned with propagated error arms (spec 14.1, 24). */
    private static Set<String> armNamesOf(Type t) {
        if (t == Type.RAW) {
            return Set.of("Raw");
        }
        return namesOf(t);
    }

    /** True when a value of {@code sub} is acceptable where {@code sup} is expected. */
    public static boolean subtypeOf(Type sub, Type sup) {
        if (sub.equals(sup)) {
            return true;
        }
        return sup instanceof Type.Union u && u.members().containsAll(namesOf(sub));
    }

    /** Whether a {@code from} value can be assigned where {@code to} is expected. Lists are
     * covariant, and a data-like type widens to the set of leaf arms it can be — so a list of
     * a sum's arms is assignable to a list of the sum (spec 8.3, 12.2). */
    public static boolean assignable(Type from, Type to, Map<String, Ast.Def> symbols) {
        if (from.equals(to)) {
            return true;
        }
        if (from == Type.NOTHING) {
            return true;   // the empty list's bottom element assigns into any element type (ADR-0028)
        }
        // immutable collections are element-covariant: A <: S makes a List/Map/Option of A
        // assignable to one of S. Sound because they cannot be mutated (spec 6), so no write can
        // smuggle a sibling arm in — the same reason Scala's immutable List and Kotlin's read-only
        // List are covariant, and Java's mutable arrays are not.
        if (from instanceof Type.ListOf a && to instanceof Type.ListOf b) {
            return assignable(a.element(), b.element(), symbols);
        }
        if (from instanceof Type.MapOf a && to instanceof Type.MapOf b) {
            return assignable(a.value(), b.value(), symbols);
        }
        if (from instanceof Type.OptionOf a && to instanceof Type.OptionOf b) {
            return assignable(a.element(), b.element(), symbols);
        }
        Set<String> fa = leafArms(from, symbols);
        Set<String> ta = leafArms(to, symbols);
        return !fa.isEmpty() && !ta.isEmpty() && ta.containsAll(fa);
    }

    /**
     * Matches an intrinsic's declared parameter type against an actual argument type, binding any
     * type variables it carries (spec §intrinsics). A variable binds on first sight and every later
     * occurrence must agree; a composite ({@code List<'a>}, {@code Map<String, 'a>}) recurses into
     * its element; a concrete parameter just requires the argument to be assignable. This is what
     * monomorphises a generic intrinsic — {@code values(m: Map<String, 'a>): List<'a>} learns
     * {@code 'a} from the map so {@link #substitute} can resolve the {@code List<'a>} result.
     */
    private static void unify(Type param, Type arg, Map<String, Type> bindings,
                              Map<String, Ast.Def> symbols, SourcePos pos, String what) {
        switch (param) {
            case Type.Var v -> {
                Type bound = bindings.get(v.name());
                if (bound == null) {
                    bindings.put(v.name(), arg);
                } else if (!assignable(arg, bound, symbols) && !assignable(bound, arg, symbols)) {
                    throw new CompileException(pos, what + ": expected " + bound + " but got " + arg);
                }
            }
            case Type.ListOf p when arg instanceof Type.ListOf a ->
                    unify(p.element(), a.element(), bindings, symbols, pos, what);
            case Type.MapOf p when arg instanceof Type.MapOf a ->
                    unify(p.value(), a.value(), bindings, symbols, pos, what);
            case Type.OptionOf p when arg instanceof Type.OptionOf a ->
                    unify(p.element(), a.element(), bindings, symbols, pos, what);
            case Type.FnOf p when arg instanceof Type.FnOf a && p.params().size() == a.params().size() -> {
                for (int i = 0; i < p.params().size(); i++) {
                    unify(p.params().get(i), a.params().get(i), bindings, symbols, pos, what);
                }
                unify(p.result(), a.result(), bindings, symbols, pos, what);
            }
            default -> {
                if (!assignable(arg, param, symbols)) {
                    throw new CompileException(pos, what + ": expected " + param + " but got " + arg);
                }
            }
        }
    }

    /** Replaces the type variables bound by {@link #unify} in an intrinsic's result type. */
    private static Type substitute(Type t, Map<String, Type> bindings) {
        return switch (t) {
            case Type.Var v -> bindings.getOrDefault(v.name(), v);
            case Type.ListOf l -> Type.list(substitute(l.element(), bindings));
            case Type.MapOf m -> Type.map(substitute(m.value(), bindings));
            case Type.OptionOf o -> Type.option(substitute(o.element(), bindings));
            default -> t;
        };
    }

    /** The set of leaf (non-sum) arm names a data-like type covers, flattening nested sums. */
    public static Set<String> leafArms(Type t, Map<String, Ast.Def> symbols) {
        Set<String> out = new HashSet<>();
        for (String name : namesOf(t)) {
            if (symbols.get(name) instanceof Ast.SumData s) {
                for (String arm : s.arms()) {
                    out.addAll(leafArms(Type.ref(arm), symbols));
                }
            } else {
                out.add(name);
            }
        }
        return out;
    }

    public static Type resolveType(Ast.TypeRef ref, Map<String, Ast.Def> symbols) {
        return switch (ref.name()) {
            case "Int" -> Type.INT;
            case "String" -> Type.STRING;
            case "Bool" -> Type.BOOL;
            case "Decimal" -> Type.DECIMAL;
            case "Date" -> Type.DATE;
            case "DateTime" -> Type.DATETIME;
            // 制約違反 is no longer a writable arm: an invariant violation aborts (spec 7.3, 9.4).
            case "List" -> {
                if (ref.arg() == null) {
                    throw new CompileException(ref.pos(), "List needs a type argument, e.g. List<Int>");
                }
                yield Type.list(resolveType(ref.arg(), symbols));
            }
            case "Option" -> {
                if (ref.arg() == null) {
                    throw new CompileException(ref.pos(), "Option needs a type argument");
                }
                yield Type.option(resolveType(ref.arg(), symbols));
            }
            case "Map" -> {
                if (ref.arg() == null) {
                    throw new CompileException(ref.pos(), "Map needs a value type, e.g. Map<String, Int>");
                }
                yield Type.map(resolveType(ref.arg(), symbols));
            }
            default -> {
                if (ref.name().startsWith("'")) {
                    yield Type.var(ref.name());   // a type variable, admitted only in the core
                }
                if (symbols.containsKey(ref.name())) {
                    yield Type.ref(ref.name());
                }
                throw new CompileException(ref.pos(), "unknown type `" + ref.name() + "`"
                        + Suggest.hint(ref.name(), symbols.keySet()));
            }
        };
    }
}
