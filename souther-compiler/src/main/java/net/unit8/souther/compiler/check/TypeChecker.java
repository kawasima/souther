package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.Prelude;
import net.unit8.souther.compiler.diag.SourcePos;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.core.Core;
import net.unit8.souther.compiler.diag.Diagnostic;
import net.unit8.souther.compiler.diag.Localizable;
import net.unit8.souther.compiler.diag.Region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The slice-3 type checker. Adds a module symbol table so fields, decoders, and encoders
 * can reference other data types (e.g. {@code id: MemberId}, {@code field("id",
 * MemberId.decoder)}, {@code MemberId.encode(self.id)}). Exposes {@link #symbols},
 * {@link #fieldTypes}, {@link #resolveType} and {@link #typeOf} for the backend.
 */
public final class TypeChecker {

    private TypeChecker() {}

    /** Type-checks a self-contained module against its own symbols, collecting every error. */
    public static List<Diagnostic> check(Ast.Module module) {
        return check(module, symbols(module), Map.of(), Lower.run(module));
    }

    /**
     * Type-checks {@code module} and, if any error was found, throws the first — the fail-fast entry
     * point for the CLI and the annotation processor, which stop at the first error. The recovering
     * {@link #check(Ast.Module, Map, Map, Ast.Module)} collects every error for the LSP instead.
     */
    public static List<Diagnostic> checkOrThrow(Ast.Module module, Map<String, Ast.Def> symbols,
                                                Map<String, Sig> importedSigs, Ast.Module lowered) {
        List<Diagnostic> warnings = new ArrayList<>();
        List<CompileException> errors = checkCollecting(module, symbols, importedSigs, lowered, warnings);
        if (!errors.isEmpty()) {
            throw errors.get(0);   // the original exception, so its rendered message is unchanged
        }
        return warnings;
    }

    /** Every error found in {@code module}, recovering past each so the whole module is checked; the
     * originating exceptions in the order they were found (so the first is the one the old fail-fast
     * check would have thrown), deduped. */
    private static List<CompileException> checkCollecting(Ast.Module module, Map<String, Ast.Def> symbols,
                                                          Map<String, Sig> importedSigs, Ast.Module lowered,
                                                          List<Diagnostic> warnings) {
        List<CompileException> errors = new ArrayList<>();
        try {
            checkRecovering(module, symbols, importedSigs, lowered, errors, warnings);
        } catch (CompileException e) {
            // A structural / prerequisite check (a duplicate name, an `exposing` violation, a module
            // cycle) is fail-fast: it can leave later phases without the state they read, so its first
            // error is recorded and the rest of the module is abandoned. Per-definition and
            // per-behavior errors are collected instead, so one broken body does not hide another.
            // An unresolvable type can still poison a later phase and abort here — reporting every such
            // error needs an error-type bottom (issue #37 follow-up), out of this change's scope.
            errors.add(e);
        }
        return deduped(errors);
    }

    /** Runs one independent unit's check, recording its first error instead of throwing so the next
     * unit is still checked — the recovery boundary that lets a module report more than one error. */
    private static void collect(List<CompileException> errors, Runnable unitCheck) {
        try {
            unitCheck.run();
        } catch (CompileException e) {
            errors.add(e);
        }
    }

    /** Collected errors as a stable set in first-seen order: one per (code, message, region), so the
     * same underlying error reported by two phases is shown once. */
    private static List<CompileException> deduped(List<CompileException> errors) {
        Map<String, CompileException> unique = new LinkedHashMap<>();
        for (CompileException e : errors) {
            unique.putIfAbsent(dedupKey(e.diagnostic()), e);
        }
        return new ArrayList<>(unique.values());
    }

    private static String dedupKey(Diagnostic d) {
        if (d == null) {
            return "null";
        }
        Region r = d.region();
        String at = r == null ? "" : r.start() + ":" + r.end();
        return d.code() + "|" + d.messageKey() + "|" + d.literalMessage() + "|" + at;
    }

    /**
     * Type-checks a module whose behavior fn bodies have already been inlined by the {@link Lower}
     * stage (ADR-0021), so the inlining is computed once and shared with the backend rather than the
     * checker inlining a second time. The main body check reads {@code lowered}; the standalone
     * helper check and the function-argument check still read the original bodies, which carry the
     * un-inlined helper calls they inspect.
     */
    public static List<Diagnostic> check(Ast.Module module, Map<String, Ast.Def> symbols,
                             Map<String, Sig> importedSigs, Ast.Module lowered) {
        List<Diagnostic> out = new ArrayList<>();
        List<Diagnostic> warnings = new ArrayList<>();
        for (CompileException e : checkCollecting(module, symbols, importedSigs, lowered, warnings)) {
            out.add(e.diagnostic());
        }
        out.addAll(warnings);
        return out;
    }

    /**
     * The check phases, appending to {@code errors} rather than returning them. Contract: each
     * independent per-definition or per-behavior check MUST be run through {@link #collect} so its
     * failure is recorded and the next unit is still checked. Only a phase that builds state a later
     * phase reads (the {@code fns} map, the {@code exposed} set, {@code reqSigs}, {@code sigs}) may
     * throw straight out — its caller treats that as fail-fast and abandons the module.
     */
    private static void checkRecovering(Ast.Module module, Map<String, Ast.Def> symbols,
                                        Map<String, Sig> importedSigs, Ast.Module lowered,
                                        List<CompileException> errors, List<Diagnostic> warnings) {
        Map<String, Ast.Expr> loweredBodies = new HashMap<>();
        for (Ast.FnDef fn : lowered.fns()) {
            loweredBodies.put(fn.name(), fn.body());
        }
        HelperInliner inliner = HelperInliner.forModule(module);
        Map<String, Type> recursiveHelperFns = recursiveHelperSigs(inliner, symbols);
        // An invariant runs on every construction and must terminate, so it may not call a recursive
        // helper (spec §invariant-expressions); a non-recursive helper is already inlined into it. This
        // runs before the data check, so the invariant's recursive call is named before it is otherwise
        // reported as an unknown function.
        for (Ast.Def def : module.defs()) {
            if (def instanceof Ast.Data data && data.invariant().isPresent()) {
                rejectRecursiveHelperInInvariant(data.invariant().get(), data.name(),
                        recursiveHelperFns.keySet());
            }
        }
        for (Ast.Def def : module.defs()) {
            collect(errors, () -> {
                switch (def) {
                    case Ast.Data data -> checkData(data, symbols);
                    case Ast.SumData sum -> checkSum(sum, symbols);
                    case Ast.UnitData _ -> { }
                }
            });
        }
        collect(errors, () -> checkNoUninhabitableCycle(module, symbols));
        Map<String, Ast.FnDef> fns = new HashMap<>();
        for (Ast.FnDef fn : module.fns()) {
            if (fns.put(fn.name(), fn) != null) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.dup.let").title("check.duplicate.title")
                                .at(fn.pos()).args(fn.name()).build(),
                        "duplicate `let " + fn.name() + "`");
            }
        }
        Set<String> allBehaviors = new HashSet<>();
        Set<String> specNames = new HashSet<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            allBehaviors.add(b.name());
            if (b instanceof Ast.SpecBehavior spec) {
                specNames.add(b.name());
                rejectAnonymousUnionParams(spec);
                rejectTupleIO(spec);
                List<String> outputCases = new ArrayList<>();
                for (Ast.TypeRef t : spec.ret().cases()) {
                    outputCases.add(t.name());
                }
                rejectDuplicateNames(outputCases, "the behavior output", spec.pos());
                rejectDuplicateNames(spec.requires(), "`requires`", spec.pos());
                rejectDuplicateNames(spec.constructs(), "`constructs`", spec.pos());
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
                throw CompileException.of(
                        Diagnostic.of(null, "check.exposing.granular").title("check.module.title")
                                .at(module.pos()).args(e.substring(0, dot), e).build(),
                        "`exposing` is type-granular: a data's `decoder`/`encoder` are always public"
                                + " once the data is exposed (spec 19.4). Write `" + e.substring(0, dot)
                                + "`, not `" + e + "`");
            }
            // an exposed name must be one of this module's own definitions. An imported name that is
            // merely visible here is not re-exported — importers reach it from its declaring module.
            if (!ownTypes.contains(e) && !allBehaviors.contains(e)) {
                boolean imported = symbols.containsKey(e);
                String why = imported
                        ? " is imported into this module, not defined here; `exposing` lists a"
                          + " module's own definitions and does not re-export imported names"
                        : ", which is not a data or behavior of this module";
                throw CompileException.of(
                        Diagnostic.of(null, imported ? "check.exposing.imported" : "check.exposing.notdefined")
                                .title("check.module.title").at(module.pos()).args(e).build(),
                        "`exposing` names `" + e + "`" + why);
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
                    throw CompileException.of(
                            Diagnostic.of(null, "check.inject.requires").title("check.module.title")
                                    .at(spec.pos()).args(spec.name()).build(),
                            "behavior `" + spec.name() + "` has no `let`, so it is an injection target"
                                    + " (spec 13.2); it cannot declare `requires` — the behavior that"
                                    + " calls or composes it does");
                }
                reqSigs.put(spec.name(), new ReqSig(
                        spec.params().isEmpty() ? null : successType(spec.params().get(0).type(), symbols),
                        successType(spec.ret(), symbols)));
                checkInjectionConstructs(spec, symbols, exposeAll, exposed);
            }
        }
        // Helper fns (no matching behavior) are expanded inline at each call site (spec 12.5); a
        // helper is checked standalone against its own declared parameter types (spec 13.1). Recovered
        // so a broken helper does not hide the behavior-body errors checked below.
        collect(errors, () -> checkHelpers(inliner, symbols, reqSigs, recursiveHelperFns, module));
        // Recursion is total by default (spec §fn-declaration): a non-`partial` recursive helper must
        // be structurally recursive, so its examples terminate at compile time.
        collect(errors, () -> TotalityChecker.check(inliner));
        // What each recursive helper constructs, transitively — a recursive helper is not inlined, so
        // its constructions are attributed to the behavior that calls it (spec 12.5).
        Map<String, Set<String>> recHelperConstructs =
                recursiveHelperConstructs(recursiveHelperFns.keySet(), loweredBodies, inliner, symbols);
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.SpecBehavior spec) {
                Ast.FnDef fn = fns.get(spec.name());
                if (fn != null) {
                    collect(errors, () -> checkSpecFn(spec, fn, loweredBodies.get(spec.name()), symbols,
                            allBehaviors, reqSigs, inliner, recursiveHelperFns, recHelperConstructs, warnings));
                }
            }
        }
        // A fn matching a pipeline is rejected (a pipeline is already its own implementation, so it
        // cannot also have a fn body — spec 13.1). A fn matching a SpecBehavior is that behavior's
        // implementation (checked above); any other fn is a helper (checked by checkHelpers). These
        // terminal validations build no state, so each is recovered independently.
        collect(errors, () -> {
            for (Ast.FnDef fn : module.fns()) {
                if (!specNames.contains(fn.name()) && allBehaviors.contains(fn.name())) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.impl.compose").title("check.impl.title")
                                    .at(fn.pos()).args(fn.name()).build(),
                            "`let " + fn.name() + "` cannot implement the composition `behavior " + fn.name()
                                    + "`, which is already its own implementation (spec 13.1)");
                }
            }
        });
        collect(errors, () -> checkStagesAreSingleInput(module));
        // an exposed composition must declare its output in `exposing`, matching the inferred one
        // (spec 14.5, ADR-0024), so a far-away change cannot grow a published output silently.
        // `signatures` builds the map `checkExposedPipeOutputs` reads, so it stays fail-fast.
        collect(errors, () -> checkExposedPipeOutputs(module,
                exposed, signatures(module, symbols, importedSigs), symbols));
    }

    /**
     * An exposed composition ({@code >->}) behavior must declare its output in the {@code exposing}
     * list ({@code exposing ( name : A | B )}, spec 14.5, ADR-0024), and the declaration must match
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
                throw CompileException.of(
                        Diagnostic.of("E1605", "e1605.notcomposition").at(module.pos()).args(name).build(),
                        "`exposing` gives an output signature to `" + name + "`, which is not a"
                                + " composition (`>->`) behavior; only a composition needs one — every"
                                + " other definition states its type where it is written (spec 14.5)");
            }
        }
        // every exposed composition must declare its output, matching the inferred one
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (!(b instanceof Ast.PipeBehavior pipe) || !exposed.contains(pipe.name())) {
                continue;
            }
            Set<String> inferred = leafCases(sigs.get(pipe.name()).out(), symbols);
            Ast.RetType declared = module.exposedOutputs().get(pipe.name());
            if (declared == null) {
                throw CompileException.of(
                        Diagnostic.of("E1605", "e1605.missing").at(pipe.pos())
                                .args(pipe.name())
                                .hint("e1605.missing.hint", pipe.name(), caseList(inferred))
                                .build(),
                        "exposed composition `" + pipe.name() + "` must declare its output in `exposing`"
                                + " (spec 14.5): write `exposing ( " + pipe.name() + " : "
                                + caseList(inferred) + " )`");
            }
            Set<String> declaredCases = leafCases(successType(declared, symbols), symbols);
            if (!inferred.equals(declaredCases)) {
                throw CompileException.of(
                        Diagnostic.of("E1604", "e1604.msg").at(pipe.pos())
                                .args(pipe.name(), caseList(declaredCases), caseList(inferred))
                                .hint("e1604.hint")
                                .build(),
                        "exposed composition `" + pipe.name() + "` declares -> " + caseList(declaredCases)
                                + " in `exposing`, but the pipeline produces " + caseList(inferred)
                                + ". Update the declared output or handle the case.");
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
                                     Map<String, ReqSig> reqSigs, Map<String, Type> recursiveHelperFns,
                                     Ast.Module module) {
        // Call sites that inference reads argument types from, built once for the whole module (each is
        // a top-level fn body with the environment its parameters bind).
        List<CallScope> callScopes = inferenceScopes(module, symbols);
        for (Ast.FnDef h : inliner.helpers().values()) {
            boolean recursive = recursiveHelperFns.containsKey(h.name());
            Map<String, Type> env = new HashMap<>();
            List<Integer> inferred = new ArrayList<>();
            for (int i = 0; i < h.params().size(); i++) {
                Ast.FnParam p = h.params().get(i);
                rejectBuiltinShadow(p.name(), p.pos());
                if (p.type() == null) {
                    if (recursive) {
                        // a recursive helper is lowered to a method, not inlined, so no call site
                        // expands it — its parameter types cannot be inferred and must be declared.
                        throw CompileException.of(
                                Diagnostic.of(null, "check.helper.annotate").title("check.helper.title")
                                        .at(p.pos(), p.name().length()).args(h.name(), p.name()).build(),
                                "helper `let " + h.name() + "` must annotate parameter `" + p.name()
                                        + "` with its type (spec 13.1)");
                    }
                    // a non-recursive helper is inline-expanded at each call site, so a value
                    // parameter's type is inferred from how it is called (spec 13.1).
                    inferred.add(i);
                    continue;
                }
                env.put(p.name(), resolveParamType(p.type(), symbols));
            }
            rejectBuiltinShadowing(h.body());
            if (!inferred.isEmpty()) {
                // Infer the un-annotated value parameters from the helper's call sites, monomorphic
                // across them, then complete the env and run the same standalone check an annotated
                // helper gets — so a mis-declared return type or a mis-passed function argument in the
                // body is caught here, at the helper, not only where it is later inlined.
                Map<Integer, Type> types = inferHelperParams(h, inferred, callScopes, inliner, symbols, reqSigs);
                for (int idx : inferred) {
                    env.put(h.params().get(idx).name(), types.get(idx));
                }
            }
            // a recursive helper hides its own parameters from helper resolution while its body is
            // expanded (foldFrom's `step` is a parameter, not a same-named user helper).
            Ast.Expr body = recursive ? inliner.inlineRecursiveBody(h) : inliner.inline(h.body());
            // a helper that returns a function (e.g. `let adder (n) = (x) -> x + n`) has no application
            // here to infer the lambda's parameter types from; it is checked where it is inlined and
            // applied (spec §blocks).
            checkFunctionArgs(h.body(), env, symbols, reqSigs, inliner);
            if (producesFunction(body)) {
                continue;
            }
            if (recursiveHelperFns.containsKey(h.name())) {
                // a recursive helper is pure: it is a static method with no injected fields, so it
                // cannot reach an injected behavior — put the effect in the behavior that calls it.
                rejectInjectedCalls(body, h.name(), reqSigs.keySet());
            }
            // self- and mutual calls resolve through the helper signatures, so the recursion type-checks
            // without a fixpoint (each declares its return type, spec 13.1).
            Map<String, Type> tenv = new HashMap<>(env);
            tenv.putAll(recursiveHelperFns);
            Type bodyType = typeOf(body, tenv, null, symbols, reqSigs);
            // a declared return type — required on a recursive helper, allowed on any helper — must
            // match the body; a lying annotation is not silently ignored.
            if (h.declaredReturn() != null) {
                Type declared = successType(h.declaredReturn(), symbols);
                if (!assignable(bodyType, declared, symbols)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.helper.return").title("check.helper.title")
                                    .at(h.pos()).args(h.name(), Type.show(declared), Type.show(bodyType))
                                    .build(),
                            "helper `let " + h.name() + "` declares it returns " + declared
                                    + " but its body is " + bodyType);
                }
            }
        }
    }

    /** A top-level fn body paired with the environment its parameters bind — a place inference reads
     * a helper call's argument types from. */
    private record CallScope(Ast.Expr body, Map<String, Type> env) {}

    /**
     * The call scopes inference reads argument types from: every top-level fn body with the
     * environment its parameters bind. A behavior-backed fn takes its parameter types from its
     * behavior; a helper fn from its own annotations, leaving an un-annotated parameter (itself still
     * being inferred) out, so an argument that refers to one simply won't type. Built once per module.
     */
    private static List<CallScope> inferenceScopes(Ast.Module module, Map<String, Ast.Def> symbols) {
        Map<String, Ast.SpecBehavior> specs = new HashMap<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.SpecBehavior s) {
                specs.put(s.name(), s);
            }
        }
        List<CallScope> scopes = new ArrayList<>();
        for (Ast.FnDef fn : module.fns()) {
            Map<String, Type> base = new HashMap<>();
            Ast.SpecBehavior spec = specs.get(fn.name());
            if (spec != null) {
                for (int i = 0; i < spec.params().size() && i < fn.params().size(); i++) {
                    base.put(fn.params().get(i).name(), successType(spec.params().get(i).type(), symbols));
                }
            } else {
                for (Ast.FnParam p : fn.params()) {
                    if (p.type() != null) {
                        base.put(p.name(), resolveParamType(p.type(), symbols));
                    }
                }
            }
            scopes.add(new CallScope(fn.body(), base));
        }
        return scopes;
    }

    /**
     * Infers a non-recursive helper's un-annotated value parameters from its call sites and enforces
     * that the helper is monomorphic. A parameter must be typeable at some call site (a helper with no
     * call site, or one whose argument cannot be typed here, is asked to annotate), and its type must
     * agree across all call sites (Int at one site and String at another is a compile error, not an
     * inline-expanded polymorphism). Returns the inferred type of each parameter index; the caller
     * completes the env and runs the standalone body check.
     */
    private static Map<Integer, Type> inferHelperParams(Ast.FnDef h, List<Integer> inferred,
            List<CallScope> scopes, HelperInliner inliner, Map<String, Ast.Def> symbols,
            Map<String, ReqSig> reqSigs) {
        Map<Integer, Type> unified = new HashMap<>();
        Map<Integer, Ast.Expr> untypeable = new HashMap<>();
        for (CallScope scope : scopes) {
            collectHelperCalls(scope.body(), scope.env(), h.name(), symbols, reqSigs, inliner, (call, env) -> {
                if (call.args().size() != h.params().size()) {
                    return;   // an arity mismatch is reported by the inliner
                }
                for (int idx : inferred) {
                    Type t;
                    try {
                        t = typeOf(inliner.inline(call.args().get(idx)), env, null, symbols, reqSigs);
                    } catch (CompileException ignored) {
                        untypeable.putIfAbsent(idx, call.args().get(idx));
                        continue;
                    }
                    Type prev = unified.get(idx);
                    if (prev == null) {
                        unified.put(idx, t);
                    } else {
                        Type wider = widerType(prev, t, symbols);
                        if (wider == null) {
                            Ast.FnParam p = h.params().get(idx);
                            throw CompileException.of(
                                    Diagnostic.of(null, "check.helper.conflict").title("check.helper.title")
                                            .at(p.pos(), p.name().length())
                                            .args(h.name(), p.name(), Type.show(prev), Type.show(t)).build(),
                                    "helper `let " + h.name() + "` parameter `" + p.name()
                                            + "` is used at conflicting types (" + Type.show(prev) + " and "
                                            + Type.show(t) + "); a helper is monomorphic — annotate it");
                        }
                        unified.put(idx, wider);
                    }
                }
            });
        }
        for (int idx : inferred) {
            if (unified.containsKey(idx)) {
                continue;
            }
            Ast.FnParam p = h.params().get(idx);
            Ast.Expr sample = untypeable.get(idx);
            boolean looksFunction = sample instanceof Ast.Block
                    || (sample instanceof Ast.Var v
                        && (inliner.helpers().containsKey(v.name()) || reqSigs.containsKey(v.name())));
            if (looksFunction) {
                // a function reached the parameter, but a function type cannot be inferred from a bare
                // name or a lambda at the call site — the annotation is required (spec 13.1).
                throw CompileException.of(
                        Diagnostic.of(null, "check.helper.fnparam").title("check.helper.title")
                                .at(p.pos(), p.name().length()).args(h.name(), p.name()).build(),
                        "helper `let " + h.name() + "` parameter `" + p.name() + "` receives a function;"
                                + " a function-typed parameter must be annotated with its type (spec 13.1)");
            }
            throw CompileException.of(
                    Diagnostic.of(null, "check.helper.infer").title("check.helper.title")
                            .at(p.pos(), p.name().length()).args(h.name(), p.name()).build(),
                    "helper `let " + h.name() + "` parameter `" + p.name() + "` cannot be inferred from"
                            + " its call sites; annotate it with its type (spec 13.1)");
        }
        return unified;
    }

    /**
     * Walks an expression for calls to {@code target}, carrying the local environment so a call
     * argument can be typed in scope. Only {@code let} bindings extend the environment here; other
     * binding forms descend with the enclosing scope, so an argument that refers to a match binding or
     * a lambda parameter simply won't type — the parameter then falls back to requiring an annotation.
     */
    private static void collectHelperCalls(Ast.Expr e, Map<String, Type> env, String target,
            Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs, HelperInliner inliner,
            java.util.function.BiConsumer<Ast.Call, Map<String, Type>> onCall) {
        if (e instanceof Ast.Call call) {
            if (call.fn().equals(target)) {
                onCall.accept(call, env);
            }
            for (Ast.Expr a : call.args()) {
                collectHelperCalls(a, env, target, symbols, reqs, inliner, onCall);
            }
            return;
        }
        if (e instanceof Ast.LetIn li) {
            collectHelperCalls(li.value(), env, target, symbols, reqs, inliner, onCall);
            Map<String, Type> inner = new HashMap<>(env);
            try {
                inner.put(li.name(), typeOf(inliner.inline(li.value()), env, null, symbols, reqs));
            } catch (CompileException ignored) {
                // an untypeable value leaves its name unbound; a later reference just won't infer.
            }
            collectHelperCalls(li.body(), inner, target, symbols, reqs, inliner, onCall);
            return;
        }
        forEachChild(e, c -> collectHelperCalls(c, env, target, symbols, reqs, inliner, onCall));
    }

    /** The wider of two types when one is assignable to the other, else null (an irreconcilable pair). */
    private static Type widerType(Type a, Type b, Map<String, Ast.Def> symbols) {
        if (assignable(a, b, symbols)) {
            return b;
        }
        if (assignable(b, a, symbols)) {
            return a;
        }
        return null;
    }

    /**
     * Signatures of the module's recursive helpers, each a {@link Type.FnOf} from its declared
     * parameter types to its declared return type. A recursive helper must declare its return type:
     * the type can't be inferred through the cycle. Registered in a body's environment so a self- or
     * mutual call type-checks (spec 13.1).
     */
    private static Map<String, Type> recursiveHelperSigs(HelperInliner inliner, Map<String, Ast.Def> symbols) {
        Map<String, Type> sigs = new HashMap<>();
        for (String name : inliner.recursiveHelpers()) {
            Ast.FnDef h = inliner.helper(name);
            if (h.declaredReturn() == null) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.rechelper.return").title("check.helper.title")
                                .at(h.pos()).args(name).build(),
                        "recursive helper `let " + name + "` must declare its return type — `let " + name
                                + " (...) : <type> = ...` — because its result cannot be inferred through"
                                + " the recursion (spec 13.1)");
            }
            List<Type> params = new ArrayList<>();
            for (Ast.FnParam p : h.params()) {
                if (p.type() == null) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.helper.annotate").title("check.helper.title")
                                    .at(p.pos(), p.name().length()).args(name, p.name()).build(),
                            "helper `let " + name + "` must annotate parameter `" + p.name()
                                    + "` with its type (spec 13.1)");
                }
                // a recursive helper is a static method taking its parameters as values; a function
                // parameter is passed as a first-class Fn (a closure), applied inside the method.
                params.add(resolveParamType(p.type(), symbols));
            }
            sigs.put(name, Type.fn(params, successType(h.declaredReturn(), symbols)));
        }
        return sigs;
    }

    /** Rejects a call to a recursive helper inside an invariant: an invariant is checked on every
     * construction and must terminate, so it cannot call one (spec §invariant-expressions). */
    private static void rejectRecursiveHelperInInvariant(Ast.Expr e, String data, Set<String> recursive) {
        if (e instanceof Ast.Call call && recursive.contains(call.fn())) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.invariant.recursive").title("check.invariant.title")
                            .at(call.pos(), call.fn().length()).args(data, call.fn()).build(),
                    "the invariant of `" + data + "` calls the recursive helper `" + call.fn()
                            + "`, but an invariant is checked at construction time and must terminate"
                            + " — a recursive helper cannot appear in an invariant");
        }
        forEachChild(e, c -> rejectRecursiveHelperInInvariant(c, data, recursive));
    }

    /** Rejects a call to an injected behavior inside a recursive helper: it is pure (spec 13.1). */
    private static void rejectInjectedCalls(Ast.Expr e, String helper, Set<String> injected) {
        if (e instanceof Ast.Call call && injected.contains(call.fn())) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.rechelper.pure").title("check.helper.title")
                            .at(call.pos(), call.fn().length()).args(helper, call.fn()).build(),
                    "recursive helper `let " + helper + "` is pure and cannot call the injected behavior `"
                            + call.fn() + "` — put the effect in the behavior that calls this helper"
                            + " (spec 13.1)");
        }
        forEachChild(e, c -> rejectInjectedCalls(c, helper, injected));
    }

    /**
     * Checks each function passed to a helper's function-typed parameter against that parameter's
     * declared type, at the call site — before the helper is expanded inline. Without this, a bad
     * function argument to a combinator surfaces deep inside the {@code fold} it derives from (a
     * non-{@code Bool} {@code filter} predicate as the {@code if} the derivation expands to), which
     * names the derivation, not the mistake. Here the error names the parameter and the combinator.
     *
     * <p>It walks the un-inlined body. Every helper call binds its signature's type variables from
     * its collection arguments ({@code 'a} from a {@code List<'a>}), then checks each function
     * argument against the resulting concrete function type. The check is best-effort: when an
     * argument's type cannot be determined in the available scope (a value bound further out), it is
     * skipped and the ordinary inlined check still applies.
     */
    private static void checkFunctionArgs(Ast.Expr e, Map<String, Type> env, Map<String, Ast.Def> symbols,
                                          Map<String, ReqSig> reqs, HelperInliner inliner) {
        if (e instanceof Ast.Call call) {
            checkHelperCallFnArgs(call, env, symbols, reqs, inliner);
        }
        forEachChild(e, sub -> checkFunctionArgs(sub, env, symbols, reqs, inliner));
    }

    private static void checkHelperCallFnArgs(Ast.Call call, Map<String, Type> env, Map<String, Ast.Def> symbols,
                                              Map<String, ReqSig> reqs, HelperInliner inliner) {
        Ast.FnDef h = inliner.helper(call.fn());
        if (h == null || call.args().size() != h.params().size()) {
            return;   // not a helper, or an arity mismatch the inliner reports
        }
        List<Type> declared = new ArrayList<>();
        boolean hasFn = false;
        for (Ast.FnParam p : h.params()) {
            // an unannotated value parameter is inferred from its call sites (spec 13.1); it is never a
            // function parameter, so leave its slot null and treat it as a non-function argument here.
            Type pt = p.type() == null ? null : resolveParamType(p.type(), symbols);
            declared.add(pt);
            hasFn |= pt instanceof Type.FnOf;
        }
        if (!hasFn) {
            return;
        }
        // the collection (non-function) arguments bind the signature's type variables — `'a` from a
        // `List<'a>` collection — so the function parameters become concrete before the check.
        Map<String, Type> bind = new HashMap<>();
        for (int i = 0; i < declared.size(); i++) {
            if (declared.get(i) == null || declared.get(i) instanceof Type.FnOf) {
                continue;
            }
            try {
                Type at = typeOf(inliner.inline(call.args().get(i)), env, null, symbols, reqs);
                unify(declared.get(i), at, bind, symbols, call.pos(), "argument " + (i + 1));
            } catch (CompileException ignored) {
                return;   // can't pin the types here; leave it to the inlined check
            }
        }
        for (int i = 0; i < declared.size(); i++) {
            if (declared.get(i) instanceof Type.FnOf fn0) {
                checkFunctionArg(h, h.params().get(i).name(), (Type.FnOf) substitute(fn0, bind),
                        call.args().get(i), env, symbols, reqs, inliner);
            }
        }
    }

    private static void checkFunctionArg(Ast.FnDef h, String paramName, Type.FnOf want, Ast.Expr arg,
                                         Map<String, Type> env, Map<String, Ast.Def> symbols,
                                         Map<String, ReqSig> reqs, HelperInliner inliner) {
        if (arg instanceof Ast.Block lambda) {
            if (lambda.params().size() != want.params().size()) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.fn.blockparam.arity").title("check.fn.title")
                                .at(arg.pos()).args(paramName, h.name(), want.params().size(),
                                        lambda.params().size()).build(),
                        "the block passed to `" + paramName + "` of `let " + h.name() + "` takes "
                                + want.params().size() + " argument(s) but is written with "
                                + lambda.params().size());
            }
            Map<String, Type> lenv = new HashMap<>(env);
            for (int j = 0; j < lambda.params().size(); j++) {
                if (want.params().get(j) instanceof Type.Var) {
                    return;   // the parameter type is still open; nothing concrete to check
                }
                lenv.put(lambda.params().get(j), want.params().get(j));
            }
            Type got;
            try {
                got = typeOf(inliner.inline(lambda.body()), lenv, null, symbols, reqs);
            } catch (CompileException ignored) {
                return;   // best-effort; the inlined check reports a genuine error with full context
            }
            if (!(want.result() instanceof Type.Var) && !assignable(got, want.result(), symbols)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.fn.blockparam.return").title("check.fn.title")
                                .at(lambda.pos()).args(paramName, h.name(), Type.show(want.result()),
                                        Type.show(got)).build(),
                        "the block passed to `" + paramName + "` of `let " + h.name() + "` must return "
                                + want.result() + " but returns " + got);
            }
        } else if (arg instanceof Ast.Var v && env.get(v.name()) instanceof Type vt && !(vt instanceof Type.FnOf)) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.fn.notfunction").title("check.fn.title")
                            .at(arg.pos()).args(paramName, h.name(), v.name()).build(),
                    "`" + paramName + "` of `let " + h.name() + "` expects a function, but `" + v.name()
                            + "` is a value, not a function");
        }
    }

    private static void checkSpecFn(Ast.SpecBehavior spec, Ast.FnDef fn, Ast.Expr inlinedBody,
                                    Map<String, Ast.Def> symbols, Set<String> allBehaviors,
                                    Map<String, ReqSig> reqSigs, HelperInliner inliner,
                                    Map<String, Type> recursiveHelperFns,
                                    Map<String, Set<String>> recHelperConstructs,
                                    List<Diagnostic> warnings) {
        if (fn.declaredReturn() != null) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.impl.noreturn").title("check.impl.title")
                            .at(fn.pos()).args(fn.name(), spec.name()).build(),
                    "`let " + fn.name() + "` implements `behavior " + spec.name()
                            + "`, so its return type comes from the behavior — do not declare one"
                            + " (spec 13.1)");
        }
        int nBusiness = spec.params().size();
        int nReq = spec.requires().size();
        if (fn.params().size() != nBusiness + nReq) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.impl.arity").title("check.impl.title")
                            .at(fn.pos()).args(fn.name(), fn.params().size(), spec.name(), nBusiness, nReq)
                            .build(),
                    "`let " + fn.name() + "` takes " + fn.params().size()
                            + " parameter(s) but `behavior " + spec.name() + "` has " + nBusiness
                            + " input(s)" + (nReq == 0 ? "" : " plus " + nReq + " requires")
                            + " (spec 13.1)");
        }
        for (Ast.FnParam p : fn.params()) {
            if (p.type() != null) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.impl.noannotate").title("check.impl.title")
                                .at(p.pos()).args(fn.name(), spec.name(), p.name()).build(),
                        "`let " + fn.name() + "` implements `behavior " + spec.name()
                                + "`, so its parameters take their types from it — do not annotate `"
                                + p.name() + "` (spec 13.1)");
            }
        }
        for (int i = 0; i < nReq; i++) {
            String got = fn.params().get(nBusiness + i).name();
            String want = spec.requires().get(i);
            if (!got.equals(want)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.impl.reqorder").title("check.impl.title")
                                .at(fn.pos()).args(fn.name(), got, want).build(),
                        "`let " + fn.name() + "` parameter `" + got + "` should be `" + want
                                + "`: the `requires` become the trailing parameters in declared order"
                                + " (spec 12.6)");
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
        // recursive helpers this behavior calls resolve through their signatures (spec 13.1); merged
        // only for typing, so construction/requires walks below still see the business params alone.
        Map<String, Type> tenv = new HashMap<>(env);
        tenv.putAll(recursiveHelperFns);
        // Check functions passed to helper parameters (e.g. a combinator's predicate) against their
        // declared types first, so a mismatch names the parameter, not the derivation it expands to.
        // A nested fold reaches `List.foldFrom` inside a block, so its signature must be in scope here.
        checkFunctionArgs(fn.body(), tenv, symbols, reqSigs, inliner);
        // The body arrives with helper calls already expanded (the Lower stage, ADR-0021): it is
        // checked as one expression, so a helper's constructions and injected calls count toward this
        // behavior's permission and requires — exactly as if the code had been written inline (12.5).
        Ast.Expr body = inlinedBody;
        rejectNonRequiredCalls(body, allBehaviors, reqSigs);

        Type rt = typeOf(body, tenv, null, symbols, reqSigs);
        if (!assignable(rt, output, symbols)) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.behavior.return").title("check.type.mismatch.title")
                            .at(body.pos()).args(spec.name(), Type.show(output), Type.show(rt))
                            .diff(Type.show(rt), Type.show(output)).build(),
                    "behavior `" + spec.name() + "` returns " + output + " but its `let` body is " + rt);
        }

        // One expression (spec 16.4): this single walk sees every construction, including under a
        // desugared `require`.
        Set<String> constructed = new HashSet<>();
        collectConstructs(body, constructed, symbols, new HashSet<>(env.keySet()), recHelperConstructs);
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
                    throw CompileException.of(
                            Diagnostic.of("E1002", "e1002.msg").at(spec.pos())
                                    .args(spec.name(), c).hint("e1002.hint").build(),
                            "Behavior `" + spec.name() + "` constructs `" + c
                                    + "` but does not declare `constructs " + c + "`.");
                }
            }
            for (String declared : spec.constructs()) {
                if (!constructed.contains(declared)) {
                    throw CompileException.of(
                            Diagnostic.of("E1006", "e1006.msg").at(spec.pos())
                                    .args(spec.name(), declared).hint("e1006.hint").build(),
                            "Behavior `" + spec.name() + "` declares `constructs " + declared
                                    + "` but never builds " + declared + " — it passes an existing"
                                    + " value through. Remove it from the `constructs` clause.");
                }
            }
        }
        // The requires clause must match what the fn actually calls (spec 12.6): missing -> E1602,
        // extra -> E1603.
        List<String> actual = requiredCalls(body, reqSigs.keySet());
        for (String call : actual) {
            if (!spec.requires().contains(call)) {
                throw CompileException.of(
                        Diagnostic.of("E1602", "e1602.msg").at(spec.pos())
                                .args(fn.name(), call, spec.name()).hint("e1602.hint").build(),
                        "`let " + fn.name() + "` calls `" + call + "`, which has no implementation, but"
                                + " `behavior " + spec.name() + "` does not declare `requires " + call + "`.");
            }
        }
        for (String req : spec.requires()) {
            if (!actual.contains(req)) {
                throw CompileException.of(
                        Diagnostic.of("E1603", "e1603.msg").at(spec.pos())
                                .args(spec.name(), req, fn.name()).hint("e1603.hint").build(),
                        "`behavior " + spec.name() + "` declares `requires " + req + "`, but `let "
                                + fn.name() + "` never calls it. Remove it from the `requires` clause.");
            }
        }
        // Intraprocedural invariant discharge: seed from the input
        // newtypes' invariants, refine along each `require`/`if` guard, and check every construction.
        // A guard-discharged one is silent; an unproven one is a warning (a possible abort); one the
        // guards prove must fail on a reachable path is an error (the path-sensitive generalization of
        // the constant `金額(-5)` check).
        InvariantChecker.Findings inv = InvariantChecker.analyze(body, env, symbols);
        warnings.addAll(inv.warnings());
        if (!inv.errors().isEmpty()) {
            throw inv.errors().get(0);
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
     * {@code RetType} with more than one case — is rejected: declare {@code data AB = A | B} and take
     * {@code (x: AB)}, so the input has a name the reader and the JVM can hold onto.
     */
    private static void rejectAnonymousUnionParams(Ast.SpecBehavior spec) {
        for (Ast.Param p : spec.params()) {
            if (p.type().cases().size() > 1) {
                String union = p.type().cases().stream()
                        .map(Ast.TypeRef::name)
                        .collect(java.util.stream.Collectors.joining(" | "));
                throw CompileException.of(
                        Diagnostic.of(null, "check.param.union").title("check.boundary.title")
                                .at(p.pos(), p.name().length()).args(p.name(), union).build(),
                        "parameter `" + p.name() + "` has an anonymous union type `" + union
                                + "`; a parameter type must be a single named type — declare `data ... = "
                                + union + "` and take that name (spec 8.6, 12.2)");
            }
        }
    }

    /** A tuple is expression-level only (ADR-0036): it has no external representation and cannot
     * cross a decoder/encoder boundary, so it may not be a behavior's input or output. Tuple types in
     * a helper/stdlib signature are fine — they never touch a codec. */
    private static void rejectTupleIO(Ast.SpecBehavior spec) {
        for (Ast.Param p : spec.params()) {
            for (Ast.TypeRef c : p.type().cases()) {
                if (refHasTuple(c)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.param.tuple").title("check.boundary.title")
                                    .at(p.pos(), p.name().length()).args(p.name()).build(),
                            "parameter `" + p.name() + "` is a tuple; a tuple has no external"
                                    + " representation and cannot cross the boundary, so a behavior's"
                                    + " input must be a named data (ADR-0036)");
                }
            }
        }
        for (Ast.TypeRef c : spec.ret().cases()) {
            if (refHasTuple(c)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.output.tuple").title("check.boundary.title")
                                .at(spec.pos()).args(spec.name()).build(),
                        "behavior `" + spec.name() + "` outputs a tuple; a tuple cannot cross the"
                                + " boundary, so a behavior's output must be a named data or a sum of"
                                + " them (ADR-0036)");
            }
        }
    }

    private static boolean refHasTuple(Ast.TypeRef ref) {
        return ref.isTuple() || (ref.arg() != null && refHasTuple(ref.arg()));
    }

    private static boolean containsTuple(Type t) {
        return Type.mentions(t, x -> x instanceof Type.TupleOf);
    }

    private static void checkInjectionConstructs(Ast.SpecBehavior spec, Map<String, Ast.Def> symbols,
                                                 boolean exposeAll, Set<String> exposed) {
        for (String c : spec.constructs()) {
            Ast.Def d = symbols.get(c);
            if (d == null || d instanceof Ast.UnitData) {
                continue;   // unknown names are caught elsewhere; a unit has a generated factory
            }
            if (!exposeAll && !exposed.contains(c)) {
                throw CompileException.of(
                        Diagnostic.of("E1305", "e1305.msg").at(spec.pos())
                                .args(spec.name(), c).hint("e1305.hint").build(),
                        "Injected behavior `" + spec.name() + "` declares `constructs " + c + "`, but "
                                + c + " is neither a unit data nor exposed. Java cannot build it: no"
                                + " factory is generated and its decoder is not public. Expose " + c
                                + ", or make it a unit data.");
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
                    throw CompileException.of(
                            Diagnostic.of(null, "check.pipe.multiinput").title("check.pipe.title")
                                    .at(pipe.pos()).args(stage, n, pipe.name()).build(),
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
     * finish}, exactly as the flat form would, so a retired case stays retired across a named
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
                throw CompileException.of(
                        Diagnostic.of(null, "check.pipe.selfcompose").title("check.pipe.title")
                                .at(pos).args(s).build(),
                        "pipeline `" + s + "` composes with itself (a cycle)");
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
            throw CompileException.of(
                    Diagnostic.of(null, "check.pipe.boundary").title("check.pipe.title").at(pos).build(),
                    "decode/encode are boundary edges, not pipeline stages; `>->` composes behaviors"
                            + " only (spec 14.1)");
        }
        Sig s = sigs.get(stage);
        if (s == null) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.unknown.behavior.msg")
                            .title("check.unknown.title")
                            .at(pos, stage.length())
                            .args(stage)
                            .suggestion(Suggest.candidate(stage, sigs.keySet()))
                            .build(),
                    "unknown behavior `" + stage + "` in pipeline" + Suggest.hint(stage, sigs.keySet()));
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
        // missing case (too narrow) nor an extra one (too wide) is accepted.
        if (pipe.declaredOut() != null) {
            Set<String> inferred = leafCases(out, symbols);
            Set<String> declared = leafCases(successType(pipe.declaredOut(), symbols), symbols);
            if (!inferred.equals(declared)) {
                throw CompileException.of(
                        Diagnostic.of("E1604", "e1604.msg").at(pipe.pos())
                                .args(pipe.name(), caseList(declared), caseList(inferred))
                                .hint("e1604.hint")
                                .build(),
                        "behavior " + pipe.name() + " declares -> " + caseList(declared)
                                + ", but the pipeline produces " + caseList(inferred)
                                + ". Update the declared output or handle the case.");
            }
        }
        // the pipeline takes whatever its first stage takes (spec 14.1)
        return new Sig(first.ins(), out);
    }

    /** Formats a set of case names as {@code A | B} (sorted, for a stable diagnostic). */
    private static String caseList(Set<String> cases) {
        return String.join(" | ", new java.util.TreeSet<>(cases));
    }

    /** The pipeline's output: what the last stage yields, plus everything that left the main line. */
    private static Type withRetired(Type mainline, Set<String> retired) {
        if (retired.isEmpty()) {
            return mainline;
        }
        Set<String> all = new LinkedHashSet<>(caseNamesOf(mainline));
        if (all.isEmpty()) {
            throw new IllegalStateException("cannot merge non-data stage output with retired cases");
        }
        all.addAll(retired);
        return caseSetType(all);
    }

    /** The main-line leaf cases {@code g} accepts — the ones the backend routes into it (spec 14.2). */
    public static List<String> mainlineCases(Type mainline, Sig g, Map<String, Ast.Def> symbols) {
        List<String> accepted = new ArrayList<>();
        for (String caseName : leafCases(mainline, symbols)) {
            if (assignable(Type.ref(caseName), g.in(), symbols)) {
                accepted.add(caseName);
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
     * yields — and adds the cases {@code g} did not accept to {@code retired}.
     *
     * <p>A case that leaves the main line does not come back: later stages are only offered the
     * main line. That is what makes this Railway (14.2). Feeding the retired cases onward instead
     * would let a stage pick up something an earlier stage had already dropped, which changes the
     * meaning of a pipeline depending on where it is split.
     *
     * <p>Naming an intermediate does not lose the split (spec 14.2): a pipeline stage is flattened
     * into its own stages before routing ({@link #flattenStages}), so `fg >-> h` with
     * `fg = f >-> g` routes over `f, g, h` — a retired case stays retired, exactly as in the flat
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
            // route over the leaf cases: a named sum output splits into its members, so a stage that
            // accepts one of them consumes it while the rest retire (spec 8.3, 14.2)
            for (String caseName : leafCases(mainline, symbols)) {
                if (assignable(Type.ref(caseName), in, symbols)) {
                    consumed.add(caseName);
                } else {
                    passed.add(caseName);
                }
            }
            if (consumed.isEmpty()) {
                throw CompileException.of(
                        Diagnostic.of("E1701", "e1701.msg")
                                .at(pos)
                                .diff(Type.show(mainline), Type.show(in))
                                .hint("e1701.hint")
                                .build(),
                        "Cannot compose behaviors: no output case of the left behavior is accepted by "
                                + "the right behavior's input. Left output: " + mainline + ", right input: " + in);
            }
            retired.addAll(passed);
            return g.out();
        }
        if (!mainline.equals(in)) {
            throw CompileException.of(
                    Diagnostic.of("E1701", "e1701.msg")
                            .at(pos)
                            .diff(Type.show(mainline), Type.show(in))
                            .hint("e1701.hint")
                            .build(),
                    "Cannot compose behaviors. Left output: " + mainline + ", right input: " + in);
        }
        return g.out();
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
            throw CompileException.of(
                    Diagnostic.of(null, "check.call.nonrequired").title("check.impl.title")
                            .at(call.pos(), call.fn().length()).build(),
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
            case Ast.Block b -> f.accept(b.body());   // a lambda / block body
            case Ast.Tuple tup -> tup.elements().forEach(f);
            case Ast.TupleGet tg -> f.accept(tg.tuple());
            default -> { }
        }
    }

    /** A constant newtype construction to verify after codegen: its wrapped constant and its site. */
    public record ConstCheck(String typeName, Object value, SourcePos pos) {}

    /**
     * Every {@code 金額(constant)} in the module: a newtype construction whose argument folds to a
     * compile-time constant. The compiler runs each through the generated {@code $Ctfe.check}
     * (CTFE) so a violation becomes a compile error rather than a run-time abort (ADR-0032).
     */
    public static List<ConstCheck> constNewtypeChecks(Ast.Module module, Map<String, Ast.Def> symbols) {
        List<ConstCheck> out = new ArrayList<>();
        for (Ast.FnDef fn : module.fns()) {
            collectConstChecks(fn.body(), symbols, out);
        }
        return out;
    }

    private static void collectConstChecks(Ast.Expr e, Map<String, Ast.Def> symbols, List<ConstCheck> out) {
        if (e instanceof Ast.NewData nd && symbols.get(nd.typeName()) instanceof Ast.Data nt
                && nt.newtype() && isInvariantBearing(nd.typeName(), symbols)) {
            newtypeConstantArg(nd).ifPresent(v -> out.add(new ConstCheck(nd.typeName(), v, nd.pos())));
        }
        forEachChild(e, c -> collectConstChecks(c, symbols, out));
    }

    public static boolean isInvariantBearing(String typeName, Map<String, Ast.Def> symbols) {
        return symbols.get(typeName) instanceof Ast.Data d && !effectiveInvariants(d, symbols).isEmpty();
    }

    /**
     * The data types {@code e} constructs.
     *
     * <p>{@code bound} carries the names in scope, because a bare identifier is a unit data's
     * construction only when nothing has bound it — a local of the same name wins (spec 8.4).
     * Without it, a parameter named after a unit data was read as constructing that unit.
     */
    private static void collectConstructs(Ast.Expr e, Set<String> out, Map<String, Ast.Def> symbols,
                                          Set<String> bound, Map<String, Set<String>> recConstructs) {
        switch (e) {
            case Ast.LetIn li -> {
                collectConstructs(li.value(), out, symbols, bound, recConstructs);
                Set<String> inner = new HashSet<>(bound);
                inner.add(li.name());
                collectConstructs(li.body(), out, symbols, inner, recConstructs);
            }
            case Ast.NewData nd -> {
                out.add(nd.typeName());
                for (Ast.FieldInit init : nd.inits()) {
                    collectConstructs(init.value(), out, symbols, bound, recConstructs);
                }
            }
            case Ast.FieldAccess fa -> collectConstructs(fa.target(), out, symbols, bound, recConstructs);
            case Ast.Tuple tup -> tup.elements().forEach(el -> collectConstructs(el, out, symbols, bound, recConstructs));
            case Ast.TupleGet tg -> collectConstructs(tg.tuple(), out, symbols, bound, recConstructs);
            case Ast.Call call -> {
                // a recursive helper is not inlined, so its own (transitive) constructions are
                // attributed to the behavior that calls it, exactly as an inlined helper's would be.
                Set<String> viaHelper = recConstructs.get(call.fn());
                if (viaHelper != null) {
                    out.addAll(viaHelper);
                }
                call.args().forEach(a -> collectConstructs(a, out, symbols, bound, recConstructs));
            }
            case Ast.Binary bin -> {
                collectConstructs(bin.left(), out, symbols, bound, recConstructs);
                collectConstructs(bin.right(), out, symbols, bound, recConstructs);
            }
            case Ast.Neg neg -> collectConstructs(neg.operand(), out, symbols, bound, recConstructs);
            case Ast.Match m -> {
                collectConstructs(m.scrutinee(), out, symbols, bound, recConstructs);
                for (Ast.Case c : m.cases()) {
                    Set<String> inner = new HashSet<>(bound);
                    if (c.binding() != null) {
                        inner.add(c.binding());
                    }
                    collectConstructs(c.body(), out, symbols, inner, recConstructs);
                }
            }
            case Ast.If iff -> {
                collectConstructs(iff.cond(), out, symbols, bound, recConstructs);
                collectConstructs(iff.then(), out, symbols, bound, recConstructs);
                collectConstructs(iff.els(), out, symbols, bound, recConstructs);
            }
            case Ast.ListLit lit -> lit.elements().forEach(el -> collectConstructs(el, out, symbols, bound, recConstructs));
            case Ast.ListComp comp -> {
                collectConstructs(comp.element(), out, symbols, bound, recConstructs);
                comp.guards().forEach(g -> collectConstructs(g, out, symbols, bound, recConstructs));
            }
            case Ast.Block block -> {
                // a block builds under the enclosing behavior's permission (spec 12.5)
                Set<String> inner = new HashSet<>(bound);
                inner.addAll(block.params());
                collectConstructs(block.body(), out, symbols, inner, recConstructs);
            }
            // a bare name that resolves to a unit data is that unit's construction (spec 8.4)
            case Ast.Var v when !bound.contains(v.name())
                    && symbols.get(v.name()) instanceof Ast.UnitData -> out.add(v.name());
            case Ast.IntLit _ -> { }
            case Ast.DecimalLit _ -> { }
            case Ast.StringLit _ -> { }
            case Ast.BoolLit _ -> { }
            case Ast.Var _ -> { }
        }
    }

    /**
     * The data each recursive helper constructs, transitively. A recursive helper is lowered to a
     * method rather than inlined, so its constructions do not appear in a caller's body; this map lets
     * {@link #collectConstructs} attribute them to the behavior that calls the helper (spec 12.5). The
     * closure follows recursive-helper calls: a helper's set includes what the recursive helpers it
     * calls construct. Non-recursive helper calls are already inlined into the bodies here.
     */
    private static Map<String, Set<String>> recursiveHelperConstructs(
            Set<String> recursive, Map<String, Ast.Expr> loweredBodies,
            HelperInliner inliner, Map<String, Ast.Def> symbols) {
        Map<String, Set<String>> own = new HashMap<>();
        Map<String, Set<String>> calls = new HashMap<>();
        for (String h : recursive) {
            Ast.Expr body = loweredBodies.get(h);
            Set<String> bound = new HashSet<>();
            for (Ast.FnParam p : inliner.helper(h).params()) {
                bound.add(p.name());
            }
            Set<String> c = new HashSet<>();
            collectConstructs(body, c, symbols, bound, Map.of());   // recursive calls opaque here
            own.put(h, c);
            Set<String> callees = new LinkedHashSet<>();
            collectCalls(body, callees, recursive);
            calls.put(h, callees);
        }
        Map<String, Set<String>> full = new HashMap<>();
        for (String h : recursive) {
            full.put(h, new HashSet<>(own.get(h)));
        }
        // fixpoint: propagate each callee's constructions until nothing new is added (handles mutual
        // recursion, whose call graph has cycles).
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String h : recursive) {
                for (String g : calls.get(h)) {
                    if (full.get(h).addAll(full.get(g))) {
                        changed = true;
                    }
                }
            }
        }
        return full;
    }

    /** Collects the names in {@code names} that {@code e} calls (a recursive-helper call graph edge). */
    private static void collectCalls(Ast.Expr e, Set<String> out, Set<String> names) {
        if (e instanceof Ast.Call call && names.contains(call.fn())) {
            out.add(call.fn());
        }
        forEachChild(e, c -> collectCalls(c, out, names));
    }

    /** Builds the name → definition table for a module. */
    public static Map<String, Ast.Def> symbols(Ast.Module module) {
        Map<String, Ast.Def> symbols = new HashMap<>();
        for (Ast.Def def : module.defs()) {
            if (def.name().equals("Some") || def.name().equals("None")) {
                // Some/None are the built-in Option cases (ADR-0011); a user data of the same name
                // would make a `| Some v` pattern ambiguous between Option and the user case, so the
                // declaration is rejected here rather than allowed to collide (ADR-0035).
                throw CompileException.of(
                        Diagnostic.of(null, "check.sum.optioncase").title("check.sum.title")
                                .at(def.pos(), def.name().length()).args(def.name()).build(),
                        "`" + def.name() + "` is a built-in Option case and cannot be declared as a data type");
            }
            if (symbols.put(def.name(), def) != null) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.dup.data").title("check.duplicate.title")
                                .at(def.pos()).args(def.name()).build(),
                        "duplicate data `" + def.name() + "`");
            }
        }
        return symbols;
    }

    /** Rejects a name listed more than once in a declaration ({@code where}). The duplicate is
     * meaningless and, left to codegen, would emit a duplicate JVM member — a duplicate method,
     * field, or implemented interface, i.e. a malformed class file. */
    private static void rejectDuplicateNames(List<String> names, String where, SourcePos pos) {
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            if (!seen.add(n)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.dup.name").title("check.duplicate.title")
                                .at(pos).args(n, where).build(),
                        "`" + n + "` is listed more than once in " + where);
            }
        }
    }

    private static void checkSum(Ast.SumData sum, Map<String, Ast.Def> symbols) {
        rejectDuplicateNames(sum.cases(), "the sum `" + sum.name() + "`", sum.pos());
        for (String caseName : sum.cases()) {
            if (!symbols.containsKey(caseName)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.sum.unknowncase").title("check.sum.title")
                                .at(sum.pos()).args(caseName, sum.name()).build(),
                        "unknown case `" + caseName + "` in sum `" + sum.name() + "`");
            }
        }
        sum.decoder().ifPresent(disc -> {
            // a derived codec dispatches over the leaves, so a nested sum's cases count too (8.3, 10.3)
            Set<String> dispatchable = leafCases(Type.ref(sum.name()), symbols);
            for (Ast.Variant v : disc.variants()) {
                Ast.Def caseDef = symbols.get(v.caseType());
                if (caseDef == null || !dispatchable.contains(v.caseType())) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.notcase").title("check.codec.title")
                                    .at(v.pos()).args(v.caseType(), sum.name()).build(),
                            "variant `" + v.caseType() + "` is not a case of `" + sum.name() + "`");
                }
                // a unit-data case has an implicit (field-less) decoder generated on its class;
                // a case may itself be a sum (spec 8.3's nested `自社負担 | 先方負担`)
                boolean caseDecodes = caseDef instanceof Ast.UnitData
                        || (caseDef instanceof Ast.Data d && d.decoder().isPresent())
                        || (caseDef instanceof Ast.SumData s && s.decoder().isPresent());
                if (!caseDecodes) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.needdecoder").title("check.codec.title")
                                    .at(v.pos()).args(v.caseType()).build(),
                            "variant `" + v.caseType() + "` needs a decoder");
                }
            }
        });
        sum.encoder().ifPresent(enc -> {
            Set<String> covered = new HashSet<>();
            Set<String> encodable = leafCases(Type.ref(sum.name()), symbols);
            for (Ast.EncVariant v : enc.variants()) {
                if (!encodable.contains(v.caseType())) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.notcase").title("check.codec.title")
                                    .at(v.pos()).args(v.caseType(), sum.name()).build(),
                            "`" + v.caseType() + "` is not a case of `" + sum.name() + "`");
                }
                Ast.Def caseDef = symbols.get(v.caseType());
                boolean caseEncodes = caseDef instanceof Ast.UnitData
                        || (caseDef instanceof Ast.Data d && d.encoder().isPresent())
                        || (caseDef instanceof Ast.SumData s && s.encoder().isPresent());
                if (!caseEncodes) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.needencoder").title("check.codec.title")
                                    .at(v.pos()).args(v.caseType()).build(),
                            "case `" + v.caseType() + "` needs an encoder");
                }
                covered.add(v.caseType());
            }
            for (String caseName : encodable) {
                if (!covered.contains(caseName)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.missingcase").title("check.codec.title")
                                    .at(enc.pos()).args(sum.name(), caseName).build(),
                            "encoder for `" + sum.name() + "` is missing case `" + caseName + "`");
                }
            }
        });
    }

    /** Effective field name → type (included data flattened first, then own fields). */
    public static Map<String, Type> fieldTypes(Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> types = new LinkedHashMap<>();
        for (String inc : data.includes()) {
            if (!(symbols.get(inc) instanceof Ast.Data id)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.spread.notproduct").title("check.construct.title")
                                .at(data.pos()).args(inc).build(),
                        "cannot spread `..." + inc + "` (not a product data)");
            }
            for (Map.Entry<String, Type> e : fieldTypes(id, symbols).entrySet()) {
                if (types.put(e.getKey(), e.getValue()) != null) {
                    throw CompileException.of(
                            Diagnostic.of("E1004", "e1004.msg").at(data.pos())
                                    .args(e.getKey(), inc, data.name()).build(),
                            "Field `" + e.getKey() + "` from `..." + inc + "` conflicts with a field of `"
                                    + data.name() + "`.");
                }
            }
        }
        for (Ast.Field f : data.fields()) {
            if (types.put(f.name(), resolveType(f.type(), symbols)) != null) {
                throw CompileException.of(
                        Diagnostic.of("E1004", "e1004.dup").at(f.pos())
                                .args(f.name(), data.name()).build(),
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

    /**
     * Rejects a data whose construction requires constructing itself through mandatory fields with no
     * base case — a base-less cycle is uninhabitable, so no value can ever be built. An optional
     * ({@code ?}) field or a {@code List}/{@code Map} field is a base case ({@code None} or the empty
     * collection breaks the cycle), so it does not count as a mandatory edge. A sum is OR-composed —
     * a cycle routed through one may bottom out in another case — so the walk stops at a sum rather
     * than raise a false positive.
     */
    private static void checkNoUninhabitableCycle(Ast.Module module, Map<String, Ast.Def> symbols) {
        for (Ast.Def def : module.defs()) {
            if (def instanceof Ast.Data data
                    && mandatoryReaches(data.name(), data.name(), symbols, new HashSet<>())) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.construct.self").title("check.construct.title")
                                .at(data.pos()).args(data.name()).build(),
                        "data `" + data.name() + "` cannot be constructed:"
                                + " it needs a value of itself through a mandatory field, with no `?` or"
                                + " `List` to bottom out — make the self-referring field optional (`?`)"
                                + " or a `List`.");
            }
        }
    }

    /** Whether {@code target} is reachable from {@code from} through mandatory data-typed fields. A
     * plain field of a record/newtype type is a {@link Type.Ref}; an optional, list, or map field is
     * not, so only mandatory references form edges. */
    private static boolean mandatoryReaches(String from, String target, Map<String, Ast.Def> symbols,
                                            Set<String> seen) {
        if (!(symbols.get(from) instanceof Ast.Data d)) {
            return false;   // a sum (OR-composed) or unit (field-less) breaks the mandatory chain
        }
        for (Type ft : fieldTypes(d, symbols).values()) {
            if (ft instanceof Type.Ref ref) {
                if (ref.name().equals(target)) {
                    return true;
                }
                if (seen.add(ref.name()) && mandatoryReaches(ref.name(), target, symbols, seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void checkData(Ast.Data data, Map<String, Ast.Def> symbols) {
        Map<String, Type> fields = fieldTypes(data, symbols);

        for (Map.Entry<String, Type> e : fields.entrySet()) {
            if (containsTuple(e.getValue())) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.field.tuple").title("check.boundary.title")
                                .at(data.pos()).args(data.name(), e.getKey()).build(),
                        "a tuple cannot be a data field (`" + data.name() + "." + e.getKey()
                                + "`): a tuple has no external representation, so it cannot cross a"
                                + " decoder/encoder boundary (ADR-0036). Use a named data.");
            }
        }

        data.invariant().ifPresent(expr -> {
            Type t = typeOf(expr, fields, data, symbols);
            if (t != Type.BOOL) {
                throw CompileException.of(
                        Diagnostic.of("E1101", "e1101.msg").at(expr.pos()).args(Type.show(t)).build(),
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
            case Ast.SetDecRef s -> Type.set(decRefType(s.element(), symbols));
            case Ast.PrimDecRef p -> primType(p.kind());
            case Ast.DataDecRef d -> {
                Ast.Def def = symbols.get(d.typeName());
                boolean hasDecoder = (def instanceof Ast.Data dd && dd.decoder().isPresent())
                        || (def instanceof Ast.SumData s && s.decoder().isPresent());
                if (!hasDecoder) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.nodecoder").title("check.codec.title")
                                    .at(d.pos()).args(d.typeName()).build(),
                            "`" + d.typeName() + "` has no decoder to call `" + d.typeName() + ".decoder`");
                }
                yield Type.ref(d.typeName());
            }
            case Ast.ListDecRef l -> Type.list(decRefType(l.element(), symbols));
            case Ast.OptionDecRef o -> Type.option(decRefType(o.element(), symbols));
            case Ast.MapDecRef mp -> Type.map(
                    mp.keyType() == null ? Type.STRING : Type.ref(mp.keyType()),
                    decRefType(mp.value(), symbols));
        };
    }

    private static void checkConstruct(Ast.Construct c, Ast.Data data, Map<String, Type> fields,
                                       Map<String, Type> env, Map<String, Ast.Def> symbols) {
        if (!c.typeName().equals(data.name())) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.codec.mustconstruct").title("check.codec.title")
                            .at(c.pos()).args(data.name(), c.typeName()).build(),
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
                throw CompileException.of(
                        Diagnostic.of(null, "check.dup.field").title("check.duplicate.title")
                                .at(init.pos()).args(init.name()).build(),
                        "duplicate field `" + init.name() + "`");
            }
            Type ft = fields.get(init.name());
            if (ft == null) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.construct.nofield").title("check.construct.title")
                                .at(init.pos(), init.name().length()).args(init.name(), typeName).build(),
                        "`" + init.name() + "` is not a field of `" + typeName + "`");
            }
            Type vt = typeOf(init.value(), env, data, symbols, reqs);
            if (!assignable(vt, ft, symbols)) {   // a case value widens to its sum-typed field (spec 8.3)
                throw CompileException.of(
                        Diagnostic.of(null, "check.field.type").title("check.type.mismatch.title")
                                .at(init.pos(), init.name().length())
                                .args(init.name(), Type.show(ft), Type.show(vt))
                                .diff(Type.show(vt), Type.show(ft)).build(),
                        "field `" + init.name() + "` expects " + ft + " but got " + vt);
            }
        }
        Map<String, Type> provided = new HashMap<>();
        for (String sp : spreads) {
            if (!(env.get(sp) instanceof Type.Ref ref)
                    || !(symbols.get(ref.name()) instanceof Ast.Data sd)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.spread.notdata").title("check.construct.title")
                                .at(pos).args(sp).build(),
                        "spread `.." + sp + "` must be a data value");
            }
            provided.putAll(fieldTypes(sd, symbols));
        }
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            if (byName.containsKey(f.getKey())) {
                continue;
            }
            Type pv = provided.get(f.getKey());
            if (pv == null) {
                throw CompileException.of(
                        Diagnostic.of("E1005", "e1005.msg").at(pos)
                                .args(typeName, f.getKey()).hint("e1005.hint").build(),
                        "construction of `" + typeName + "` is missing field `" + f.getKey() + "`");
            }
            if (!assignable(pv, f.getValue(), symbols)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.spread.provides").title("check.type.mismatch.title")
                                .at(pos).args(f.getKey(), Type.show(pv), typeName, Type.show(f.getValue()))
                                .diff(Type.show(pv), Type.show(f.getValue())).build(),
                        "spread provides `" + f.getKey() + "` as " + pv + " but `" + typeName + "` needs "
                                + f.getValue());
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
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.iso").title("check.codec.title")
                                    .at(t.pos()).args(Type.show(at)).build(),
                            "ISO text encoder expects Date or DateTime, got " + at);
                }
            }
            case Ast.OptionRaw o -> {
                Type at = typeOf(o.access(), env, data, symbols);
                if (!(at instanceof Type.OptionOf oo)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.option").title("check.codec.title")
                                    .at(o.pos()).args(Type.show(at)).build(),
                            "optional encoder expects an Option, got " + at);
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
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.noencoder").title("check.codec.title")
                                    .at(e.pos()).args(e.typeName()).build(),
                            "`" + e.typeName() + "` has no encoder to call `" + e.typeName() + ".encode`");
                }
                requireType(e.arg(), Type.ref(e.typeName()), env, data, symbols, NO_REQS,
                        "argument of " + e.typeName() + ".encode");
            }
            case Ast.ListEnc le -> {
                Type st = typeOf(le.source(), env, data, symbols);
                if (!(st instanceof Type.ListOf lo)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.listsource").title("check.codec.title")
                                    .at(le.pos()).args(Type.show(st)).build(),
                            "list(...) source must be a List, got " + st);
                }
                checkEncElem(le.elem(), lo.element(), le.pos(), symbols);
            }
            case Ast.SetEnc se -> {
                Type st = typeOf(se.source(), env, data, symbols);
                if (!(st instanceof Type.SetOf so)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.setsource").title("check.codec.title")
                                    .at(se.pos()).args(Type.show(st)).build(),
                            "set encoder source must be a Set, got " + st);
                }
                checkEncElem(se.elem(), so.element(), se.pos(), symbols);
            }
            case Ast.MapEnc me -> {
                Type st = typeOf(me.source(), env, data, symbols);
                if (!(st instanceof Type.MapOf mo)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.mapsource").title("check.codec.title")
                                    .at(me.pos()).args(Type.show(st)).build(),
                            "map encoder source must be a Map, got " + st);
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
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.elemenc").title("check.codec.title")
                                    .at(pos).args(p.kind().toString(), Type.show(elemType)).build(),
                            "element encoder " + p.kind() + " does not match " + elemType);
                }
            }
            case Ast.DataEnc d -> {
                // the element may be a product or a sum: `List<事前承認理由>` holds a sum (spec 11.2)
                Ast.Def def = symbols.get(d.typeName());
                boolean hasEncoder = (def instanceof Ast.Data dd && dd.encoder().isPresent())
                        || (def instanceof Ast.SumData sd && sd.encoder().isPresent());
                if (!elemType.equals(Type.ref(d.typeName())) || !hasEncoder) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.codec.elemenc").title("check.codec.title")
                                    .at(pos).args("`" + d.typeName() + "`", Type.show(elemType)).build(),
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
            case Ast.IntLit _ -> Type.INT;
            case Ast.DecimalLit _ -> Type.DECIMAL;
            case Ast.StringLit _ -> Type.STRING;
            case Ast.BoolLit _ -> Type.BOOL;
            case Ast.Tuple tup -> {
                List<Type> elems = new ArrayList<>();
                for (Ast.Expr el : tup.elements()) {
                    elems.add(typeOf(el, env, data, symbols, reqs));
                }
                yield Type.tuple(elems);
            }
            case Ast.TupleGet tg -> {
                Type tt = typeOf(tg.tuple(), env, data, symbols, reqs);
                if (!(tt instanceof Type.TupleOf to)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.tuple.pattern").title("check.type.mismatch.title")
                                    .at(tg.pos()).args(Type.show(tt)).build(),
                            "a tuple pattern needs a tuple, got " + tt);
                }
                if (to.elements().size() != tg.arity()) {   // exact arity, in either direction (Elm)
                    throw CompileException.of(
                            Diagnostic.of(null, "check.tuple.arity").title("check.type.mismatch.title")
                                    .at(tg.pos()).args(tg.arity(), to.elements().size()).build(),
                            "this pattern binds " + tg.arity()
                                    + " name(s) but the tuple has " + to.elements().size() + " element(s)");
                }
                yield to.elements().get(tg.index());
            }
            case Ast.Neg neg -> {
                Type t = typeOf(neg.operand(), env, data, symbols, reqs);
                if (t != Type.INT && t != Type.DECIMAL) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.neg.msg")
                                    .title("check.neg.title")
                                    .at(neg.pos(), width(neg.operand()))
                                    .args(Type.show(t))
                                    .build(),
                            "unary minus needs an Int or Decimal, got " + t);
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
                    Type valueType = typeOf(li.value(), env, data, symbols, reqs);
                    Type bindType = valueType;
                    if (li.declaredType() instanceof Ast.RetType rt) {
                        // A binding carrying an inlined helper's declared parameter type. When that type
                        // is a sum, keep it: a case argument widens to its sum (spec 8.3), so a `match`
                        // in the body still sees the sum rather than the argument's specific case. Other
                        // declared types (a type variable in a generic prelude helper, a record, a list)
                        // are left to the argument's own type, which monomorphisation and the call-site
                        // check already handle.
                        Type declared = resolveParamType(rt, symbols);
                        if (isSumType(declared, symbols) && assignable(valueType, declared, symbols)) {
                            bindType = declared;
                        }
                    }
                    inner.put(li.name(), bindType);
                }
                yield typeOf(li.body(), inner, data, symbols, reqs);
            }
            // reached only where a block escapes: it may be passed as an argument, or bound to a
            // `let` and applied, but it is not a value that can be returned or stored, because that
            // would need a runtime closure (spec 12.5)
            case Ast.Block block -> throw CompileException.of(
                    Diagnostic.of(null, "check.block.notvalue").title("check.block.title")
                            .at(block.pos()).build(),
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
                throw CompileException.of(
                        Diagnostic.of(null, "check.unknown.name.msg")
                                .title("check.unknown.title")
                                .at(v.pos(), v.name().length())
                                .args(v.name())
                                .suggestion(Suggest.candidate(v.name(), env.keySet()))
                                .build(),
                        "unknown identifier `" + v.name() + "`" + Suggest.hint(v.name(), env.keySet()));
            }
            case Ast.FieldAccess fa -> typeOfFieldAccess(fa, env, data, symbols, reqs);
            case Ast.Call call -> typeOfCall(call, env, data, symbols, reqs);
            case Ast.Binary bin -> typeOfBinary(bin, env, data, symbols, reqs);
            case Ast.NewData nd -> {
                if (!(symbols.get(nd.typeName()) instanceof Ast.Data owner)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.construct.no").title("check.construct.title")
                                    .at(nd.pos(), nd.typeName().length()).args(nd.typeName()).build(),
                            "cannot construct `" + nd.typeName() + "`");
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
                Type empty = absorbBottom(tt, et);   // one case may be `[]`, or a tuple of them (ADR-0028)
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
                throw CompileException.of(
                        Diagnostic.of(null, "check.if.msg")
                                .title("check.if.title")
                                .at(iff.pos(), 2)
                                .secondary(Region.ofWidth(iff.then().pos(), width(iff.then())),
                                        "check.if.then", Type.show(tt))
                                .secondary(Region.ofWidth(iff.els().pos(), width(iff.els())),
                                        "check.if.else", Type.show(et))
                                .hint("check.if.hint")
                                .build(),
                        "if branches disagree: " + tt + " vs " + et);
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
     * types widen to the union of their cases (so {@code [High] ++ [LowRole]} is a list of both). */
    /** When one of two joined positions ({@code if}/{@code match} cases) is the empty list {@code []}
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

    /** Reconciles two joined positions where one may carry the empty-collection bottom, recursing
     * through tuples so a {@code ([], [])} accumulator grown on either side joins to {@code (T, T)}
     * (the {@code partition} shape). Beyond the whole empty list {@link #absorbEmptyList} handles, it
     * joins same-arity tuples element-wise. Returns {@code null} when the two do not reconcile, so the
     * caller falls through to its ordinary rules. */
    /** Refines the type-variable bindings from a function argument's actual result: where the
     * function's declared result is a type variable and its current binding is unknown or an
     * empty-collection bottom, replace it with the concrete result the step grows. This is how a
     * {@code foldFrom} seeded with {@code []} recovers its accumulator type — the block returns the
     * grown list, not the bottom the seed carried. A composite result (a tuple of accumulators, as
     * {@code partition}/{@code distinct} fold) refines position by position. */
    public static void refineBottom(Type declaredResult, Type got, Map<String, Type> bind) {
        if (declaredResult instanceof Type.Var v) {
            Type cur = bind.get(v.name());
            if ((cur == null || Type.mentions(cur, TypeChecker::isBottom))
                    && !Type.mentions(got, TypeChecker::isBottom)) {
                bind.put(v.name(), got);
            }
        } else if (declaredResult instanceof Type.TupleOf dt && got instanceof Type.TupleOf gt
                && dt.elements().size() == gt.elements().size()) {
            for (int i = 0; i < dt.elements().size(); i++) {
                refineBottom(dt.elements().get(i), gt.elements().get(i), bind);
            }
        }
    }

    private static Type absorbBottom(Type a, Type b) {
        if (a.equals(b)) {
            return a;
        }
        Type list = absorbEmptyList(a, b);
        if (list != null) {
            return list;
        }
        if (a instanceof Type.TupleOf ta && b instanceof Type.TupleOf tb
                && ta.elements().size() == tb.elements().size()) {
            List<Type> elems = new ArrayList<>();
            for (int i = 0; i < ta.elements().size(); i++) {
                Type e = absorbBottom(ta.elements().get(i), tb.elements().get(i));
                if (e == null) {
                    return null;
                }
                elems.add(e);
            }
            return Type.tuple(elems);
        }
        return null;
    }

    /** The scalar empty-collection bottom: the element type of a {@code []} whose type is not yet
     * fixed (ADR-0028). It unifies with any type, so a comparison against it is left to run time. */
    private static boolean isBottom(Type t) {
        return t instanceof Type.Nothing;
    }

    /** Reads a bottom ({@code Nothing}) as the empty list — its run-time value when it is a list read
     * from an accumulator an empty collection seed grows (see the {@code CONCAT} case). Leaves any
     * other type untouched. */
    private static Type bottomAsEmptyList(Type t) {
        return isBottom(t) ? Type.EMPTY_LIST : t;
    }

    /** Whether {@code a} and {@code b} are the same kind of collection (so an empty-collection seed of
     * {@code a}'s kind is grown into {@code b}) — both lists, sets, options, maps, or same-arity
     * tuples. A bare seed and a grown value of the same shape pass; a mismatched shape does not. */
    private static boolean sameKind(Type a, Type b) {
        return (a instanceof Type.ListOf && b instanceof Type.ListOf)
                || (a instanceof Type.SetOf && b instanceof Type.SetOf)
                || (a instanceof Type.OptionOf && b instanceof Type.OptionOf)
                || (a instanceof Type.MapOf && b instanceof Type.MapOf)
                || (a instanceof Type.TupleOf ta && b instanceof Type.TupleOf tb
                        && ta.elements().size() == tb.elements().size());
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
        throw CompileException.of(
                Diagnostic.of(null, "check.list.msg")
                        .title("check.list.title")
                        .at(pos)
                        .hint("check.list.hint", Type.show(a), Type.show(b))
                        .build(),
                "list elements disagree on type: " + a + " vs " + b);
    }

    private static Type typeOfMatch(Ast.Match m, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Type st = typeOf(m.scrutinee(), env, data, symbols, reqs);
        if (st instanceof Type.OptionOf oo) {
            return typeOfOptionMatch(m, oo.element(), env, data, symbols, reqs);
        }
        if (st instanceof Type.Union union) {
            return typeOfCasesMatch(m, union.members(), "union " + union, st, env, data, symbols, reqs);
        }
        if (!(st instanceof Type.Ref ref) || !(symbols.get(ref.name()) instanceof Ast.SumData sum)) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.match.notsum").title("check.match.title")
                            .at(m.pos(), 5).args(Type.show(st)).build(),
                    "match requires a sum-typed value, got " + st);
        }
        return typeOfCasesMatch(m, new HashSet<>(sum.cases()), "data `" + sum.name() + "`",
                st, env, data, symbols, reqs);
    }

    /** Match over a fixed set of data cases (a named sum's cases, or an anonymous union's members).
     * A single-case case binds that case's type; an or-pattern ({@code A | B}) binds {@code scrutinee}
     * (the sum type), since no one case type fits all its alternatives. Every case must be covered
     * exactly once (E1201; a second cover is an overlap error). */
    private static Type typeOfCasesMatch(Ast.Match m, Set<String> cases, String what, Type scrutinee,
                                        Map<String, Type> env, Ast.Data data, Map<String, Ast.Def> symbols,
                                        Map<String, ReqSig> reqs) {
        Set<String> covered = new HashSet<>();
        Type branchType = null;
        for (Ast.Case c : m.cases()) {
            for (String caseName : c.caseTypes()) {
                if (!cases.contains(caseName)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.match.notcase").title("check.match.title")
                                    .at(c.pos()).args(caseName, what).build(),
                            "`" + caseName + "` is not a case of " + what);
                }
                if (!covered.add(caseName)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.match.overlap").title("check.match.title")
                                    .at(c.pos()).args(caseName).build(),
                            "`" + caseName + "` is matched by more than one case");
                }
            }
            Type bindType = c.caseTypes().size() == 1 ? caseBindType(c.caseTypes().get(0)) : scrutinee;
            branchType = mergeBranch(m, branchType,
                    typeOf(c.body(), bound(env, c.binding(), bindType), data, symbols, reqs), c);
        }
        List<String> missing = new ArrayList<>();
        for (String caseName : cases) {
            if (!covered.contains(caseName)) {
                missing.add(caseName);
            }
        }
        if (!missing.isEmpty()) {
            missing.sort(null);
            throw nonExhaustive(m.pos(), what, missing);
        }
        if (branchType == null) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.match.nocases").title("check.match.title")
                            .at(m.pos(), 5).build(),
                    "match has no cases");
        }
        return branchType;
    }

    /** Match over {@code Option<element>}: cases are {@code Some} (binds the element) and
     * {@code None}; both must be present (spec 16.3). */
    private static Type typeOfOptionMatch(Ast.Match m, Type element, Map<String, Type> env, Ast.Data data,
                                          Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Set<String> covered = new HashSet<>();
        Type branchType = null;
        for (Ast.Case c : m.cases()) {
            if (c.caseTypes().size() != 1) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.match.option.orpattern").title("check.match.title")
                                .at(c.pos()).build(),
                        "or-patterns are not allowed in an Option match; use separate Some and None cases");
            }
            String caseType = c.caseTypes().get(0);
            Type bind = switch (caseType) {
                case "Some" -> element;
                case "None" -> null;
                default -> throw CompileException.of(
                        Diagnostic.of(null, "check.match.option.notcase").title("check.match.title")
                                .at(c.pos()).args(caseType).build(),
                        "`" + caseType + "` is not a case of Option; use Some or None");
            };
            covered.add(caseType);
            branchType = mergeBranch(m, branchType,
                    typeOf(c.body(), bound(env, c.binding(), bind), data, symbols, reqs), c);
        }
        List<String> missing = new ArrayList<>();
        for (String caseName : List.of("Some", "None")) {
            if (!covered.contains(caseName)) {
                missing.add(caseName);
            }
        }
        if (!missing.isEmpty()) {
            throw nonExhaustive(m.pos(), "Option", missing);
        }
        return branchType;
    }

    /** The type a match case binds. A primitive-named case (e.g. {@code Int} in {@code Int |
     * DivisionByZero}) binds that primitive; a data-named case binds its data type. */
    public static Type caseBindType(String caseName) {
        return switch (caseName) {
            case "Int" -> Type.INT;
            case "String" -> Type.STRING;
            case "Bool" -> Type.BOOL;
            case "Decimal" -> Type.DECIMAL;
            case "Date" -> Type.DATE;
            case "DateTime" -> Type.DATETIME;
            default -> Type.ref(caseName);
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
        Type empty = absorbEmptyList(branchType, bt);   // one case may be `[]` (ADR-0028)
        if (empty != null) {
            return empty;
        }
        // cases yielding different data types widen to their union, as `if` branches do (spec 16.2)
        if (isDataLike(branchType) && isDataLike(bt)) {
            Set<String> names = new HashSet<>(namesOf(branchType));
            names.addAll(namesOf(bt));
            return Type.union(names);
        }
        throw CompileException.of(
                Diagnostic.of(null, "check.match.branchtypes").title("check.match.title")
                        .at(c.pos()).args(Type.show(branchType), Type.show(bt))
                        .diff(Type.show(bt), Type.show(branchType)).build(),
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
        throw CompileException.of(
                Diagnostic.of(null, "check.access").title("check.type.mismatch.title")
                        .at(fa.pos(), fa.field().length()).args(fa.field()).build(),
                "cannot access field `" + fa.field() + "` on this value");
    }

    private static Type typeOfCall(Ast.Call call, Map<String, Type> env, Ast.Data data,
                                   Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        List<Ast.Expr> args = call.args();
        // A shipped intrinsic behaves like a built-in: check the call against its declared signature
        // (from the prelude) and yield its result type; the backend emits the primitive for its key.
        Prelude.IntrinsicSig intrinsic = Prelude.intrinsics().get(call.fn());
        if (intrinsic != null) {
            if (args.size() != intrinsic.params().size()) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.arity").title("check.arity.title")
                                .at(call.pos(), call.fn().length())
                                .args(call.fn(), intrinsic.params().size(), args.size()).build(),
                        call.fn() + " takes " + intrinsic.params().size()
                                + " argument(s) but is called with " + args.size());
            }
            Map<String, Type> bindings = new HashMap<>();
            for (int i = 0; i < args.size(); i++) {
                Type argType = typeOf(args.get(i), env, data, symbols, reqs);
                unify(intrinsic.params().get(i), argType, bindings, symbols, call.pos(),
                        "argument " + (i + 1) + " of " + call.fn());
            }
            Type result = substitute(intrinsic.result(), bindings);
            // `sort` carries no `comparable` constraint in its `List<'a>` signature (Souther has no
            // type classes), so guard here: only the ordered primitives sort. A data or a newtype
            // carries as a non-Comparable object, which would throw at runtime — reject it now. The
            // empty-list literal (element `Nothing`) is fine: it sorts to itself, so let it through.
            if (intrinsic.key().equals("list.sort") && result instanceof Type.ListOf lo
                    && !(lo.element() instanceof Type.Nothing) && !isOrdered(lo.element())) {
                throw needsOrdered(call.pos(), "sort", lo.element(),
                        "sort needs a list of ordered values (Int, String, Decimal, Date, or DateTime),"
                                + " but the element is " + lo.element()
                                + " — sort its ordered field instead (e.g. map to `.value` first)");
            }
            return result;
        }
        return switch (call.fn()) {
            case "String.length" -> {
                arity(call, 1);
                requireType(args.get(0), Type.STRING, env, data, symbols, reqs, "argument of String.length");
                yield Type.INT;
            }
            case "List.length" -> {
                arity(call, 1);
                Type t = typeOf(args.get(0), env, data, symbols, reqs);
                if (!(t instanceof Type.ListOf)) {
                    throw expects(call.pos(), "List.length", "kind.list", t,
                            "argument of List.length must be a List but is " + t);
                }
                yield Type.INT;
            }
            case "List.max", "List.min" -> {
                arity(call, 1);
                Type t = typeOf(args.get(0), env, data, symbols, reqs);
                if (!(t instanceof Type.ListOf lo)) {
                    throw expects(call.pos(), call.fn(), "kind.list", t,
                            "argument of " + call.fn() + " must be a List but is " + t);
                }
                // Like `sort`, max/min compare by natural order, so the element must be an ordered
                // primitive (Souther has no type classes); a data / newtype element is not Comparable.
                // The empty-list literal (element `Nothing`) is fine — its result is `None`.
                if (!isBottom(lo.element()) && !isOrdered(lo.element())) {
                    throw needsOrdered(call.pos(), call.fn(), lo.element(),
                            call.fn() + " needs a list of ordered values (Int, String, Decimal, Date,"
                                    + " or DateTime), but the element is " + lo.element()
                                    + " — compare its ordered field instead");
                }
                yield Type.option(lo.element());
            }
            case "List.find" -> {
                arity(call, 2);   // find(p, xs): predicate first, list last (F#/Elm order)
                Type t = typeOf(args.get(1), env, data, symbols, reqs);
                if (!(t instanceof Type.ListOf lo)) {
                    throw expects(call.pos(), "List.find", "kind.list", t,
                            "List.find expects a List, got " + t);
                }
                Type pr = blockType(call.fn(), args.get(0), List.of(lo.element()), env, data, symbols, reqs);
                if (pr != Type.BOOL) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.fn.predicatebool").title("check.fn.title")
                                    .at(call.pos()).args("List.find", Type.show(pr)).build(),
                            "List.find's predicate must return Bool, but returns " + pr);
                }
                yield Type.option(lo.element());
            }
            case "List.sortBy" -> {
                arity(call, 2);   // sortBy(key, xs): key first, list last (F#/Elm order)
                Type t = typeOf(args.get(1), env, data, symbols, reqs);
                if (!(t instanceof Type.ListOf lo)) {
                    throw expects(call.pos(), "List.sortBy", "kind.list", t,
                            "List.sortBy expects a List, got " + t);
                }
                Type keyT = blockType(call.fn(), args.get(0), List.of(lo.element()), env, data, symbols, reqs);
                if (!isBottom(keyT) && !isOrdered(keyT)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.ordered.key").title("check.type.mismatch.title")
                                    .at(call.pos()).args("List.sortBy", Type.show(keyT))
                                    .hint("check.ordered.hint").build(),
                            "List.sortBy's key must be an ordered value (Int, String, Decimal, Date, or"
                                    + " DateTime), but returns " + keyT);
                }
                yield Type.list(lo.element());
            }
            case "List.get" -> {
                arity(call, 2);
                Type first = typeOf(args.get(1), env, data, symbols, reqs);   // get(index, xs): list last
                if (!(first instanceof Type.ListOf lo)) {
                    throw expects(call.pos(), "List.get", "kind.list", first,
                            "List.get expects a List, got " + first);
                }
                requireType(args.get(0), Type.INT, env, data, symbols, reqs, "index of List.get");
                yield Type.option(lo.element());
            }
            case "Map.get" -> {
                arity(call, 2);
                Type first = typeOf(args.get(1), env, data, symbols, reqs);   // get(key, m): map last
                if (!(first instanceof Type.MapOf mo)) {
                    throw expects(call.pos(), "Map.get", "kind.map", first,
                            "Map.get expects a Map, got " + first);
                }
                // A bottom key type is a `Map.empty`-seeded accumulator whose key is not fixed yet;
                // the block growing it — `Map.get(k, acc)` in a groupBy fold — supplies the real key,
                // so accept it rather than demand the bottom. Otherwise the key must match.
                if (!isBottom(mo.key())) {
                    requireType(args.get(0), mo.key(), env, data, symbols, reqs, "key of Map.get");
                }
                yield Type.option(mo.value());
            }
            case "Map.empty" -> {
                arity(call, 0);
                // like `[]`, the empty map's key and value are bottoms fixed by context (ADR-0028)
                yield Type.map(Type.NOTHING, Type.NOTHING);
            }
            case "Set.empty" -> {
                arity(call, 0);
                yield Type.set(Type.NOTHING);   // empty set's element type fixed by context (ADR-0028)
            }
            case "Int.add", "Int.subtract", "Int.multiply",
                 "Decimal.add", "Decimal.subtract", "Decimal.multiply" ->
                    numericOp(call, env, data, symbols, reqs, false);
            case "Int.compare", "Decimal.compare" -> numericOp(call, env, data, symbols, reqs, true);
            case "Int.remainder" -> {
                arity(call, 2);
                requireType(args.get(0), Type.INT, env, data, symbols, reqs, "argument 1 of remainder");
                requireType(args.get(1), Type.INT, env, data, symbols, reqs, "argument 2 of remainder");
                // partial: a zero divisor produces the DivisionByZero case (spec 18.2)
                yield Type.union(new java.util.LinkedHashSet<>(List.of("Int", "DivisionByZero")));
            }
            case "Int.divide", "Decimal.divide" -> {
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
                // arguments — f(x) (spec §fn-declaration). A newtype construction 金額(500) never
                // reaches here — NewtypeDesugar has lowered it to a NewData literal.
                if (env.get(call.fn()) instanceof Type.FnOf fn) {
                    if (args.size() != fn.params().size()) {
                        throw CompileException.of(
                                Diagnostic.of(null, "check.arity").title("check.arity.title")
                                        .at(call.pos(), call.fn().length())
                                        .args(call.fn(), fn.params().size(), args.size()).build(),
                                "`" + call.fn() + "` takes " + fn.params().size()
                                        + " argument(s) but is applied to " + args.size());
                    }
                    // Resolve the signature's type variables from the value (non-function) arguments
                    // first — a generic recursive helper like `foldFrom(step, seed, xs, i)` fixes `'acc`
                    // from the seed and `'a` from the list. An empty-collection seed ([], Map.empty)
                    // binds the accumulator to a bottom; the step's result then grows it to the concrete
                    // type, so a function argument's result refines the binding (as the old fold did).
                    Map<String, Type> bind = new HashMap<>();
                    for (int i = 0; i < args.size(); i++) {
                        if (!(fn.params().get(i) instanceof Type.FnOf)) {
                            Type at = typeOf(args.get(i), env, data, symbols, reqs);
                            unify(fn.params().get(i), at, bind, symbols, call.pos(), "argument " + (i + 1));
                        }
                    }
                    for (int i = 0; i < args.size(); i++) {
                        if (fn.params().get(i) instanceof Type.FnOf fp0) {
                            resolveStepBinding(call.fn(), fp0, args.get(i), bind, env, data, symbols, reqs);
                        }
                    }
                    for (int i = 0; i < args.size(); i++) {
                        if (!(fn.params().get(i) instanceof Type.FnOf)) {
                            requireType(args.get(i), substitute(fn.params().get(i), bind), env, data,
                                    symbols, reqs, "argument " + (i + 1) + " of " + call.fn());
                        }
                    }
                    yield substitute(fn.result(), bind);
                }
                // a qualified name that matched no stdlib builtin/intrinsic above is a wrong stdlib
                // call (spec §stdlib) — report it as such, not as a missing behavior.
                if (call.fn().indexOf('.') >= 0) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.stdlib.notfunction").title("check.unknown.title")
                                    .at(call.pos(), call.fn().length()).args(call.fn()).build(),
                            "`" + call.fn() + "` is not a standard-library function.");
                }
                // a required behavior called inline (spec 12.2, 13): type it as its success case
                ReqSig callee = reqs.get(call.fn());
                if (callee == null) {
                    String qualified = Prelude.qualifiedFor(call.fn());
                    if (qualified != null) {
                        throw CompileException.of(
                                Diagnostic.of(null, "check.stdlib.qualified.msg")
                                        .title("check.unknown.title").at(call.pos(), call.fn().length())
                                        .args(call.fn(), qualified).build(),
                                "`" + call.fn() + "` is a standard-library function and must be called"
                                        + " qualified, as `" + qualified + "` (spec §stdlib).");
                    }
                    throw CompileException.of(
                            Diagnostic.of("E1401", "e1401.msg").at(call.pos(), call.fn().length())
                                    .args(call.fn())
                                    .suggestion(Suggest.candidate(call.fn(), reqs.keySet()))
                                    .hint("e1401.hint")
                                    .build(),
                            "`" + call.fn() + "` is not a behavior or builtin"
                                    + Suggest.hint(call.fn(), reqs.keySet())
                                    + ". Calling arbitrary JVM methods is not allowed; declare a behavior"
                                    + " without a `let` and implement it from Java.");
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

    /**
     * The constant a newtype construction wraps, if its {@code value} argument folds — for
     * {@code 金額(500)} (lowered to {@code 金額 { value = 500 }} by NewtypeDesugar) or the record
     * form written directly. Empty when the argument is a runtime value or the data is not a
     * single-{@code value} wrapper (e.g. a product).
     */
    private static Optional<Object> newtypeConstantArg(Ast.NewData nd) {
        if (nd.spreads().isEmpty() && nd.inits().size() == 1
                && nd.inits().get(0).name().equals("value")) {
            return ConstEval.eval(nd.inits().get(0).value());
        }
        return Optional.empty();
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
                // The ordered primitives: Int numerically, String lexicographically, Decimal by
                // value, Date/DateTime in time. Unlike Elm (which orders only Int/Float/Char/String
                // because it rides JavaScript), Souther sits on the JVM where BigDecimal/LocalDate/
                // LocalDateTime are Comparable, so it orders them too. A single-value newtype over an
                // ordered type is ordered by that value; the operands stay the same newtype (nominal),
                // except that a bare literal takes the other side's newtype from context.
                Type lt = typeOf(bin.left(), env, data, symbols, reqs);
                Type rt = typeOf(bin.right(), env, data, symbols, reqs);
                if (!orderedComparable(lt, rt, bin.left(), bin.right(), symbols)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.compare.ordered").title("check.type.mismatch.title")
                                    .at(bin.pos()).args(Type.show(lt), Type.show(rt)).build(),
                            "operand of comparison must be two ordered values of the same type (Int,"
                                    + " String, Decimal, Date, DateTime, or a newtype over one of these),"
                                    + " got " + lt + " and " + rt);
                }
                yield Type.BOOL;
            }
            case ADD, SUB, MUL, DIV -> {
                // `+ - * /` work on two Int or two Decimal operands (spec 18.1). Int aborts on
                // overflow and `/` aborts on a zero divisor; Decimal `/` rounds by the default
                // scale/mode. Case handling for a zero divisor is the `divide`/`remainder` functions.
                Type lt = typeOf(bin.left(), env, data, symbols, reqs);
                Type rt = typeOf(bin.right(), env, data, symbols, reqs);
                boolean addSub = bin.op() == Ast.BinOp.ADD || bin.op() == Ast.BinOp.SUB;
                // Closed newtype arithmetic (spec §newtype-arithmetic): `+`/`-` over a single-value
                // numeric newtype yield that newtype. The result is re-wrapped and its invariant re-checked at construction,
                // which a behavior's guard discharges. The operands are the same newtype, or a newtype
                // with a bare literal of its base (as for comparison).
                if (addSub && arithClosedNewtype(lt, rt, bin.left(), bin.right(), symbols)) {
                    yield closedNewtypeArithResult(lt, rt, symbols);
                }
                // Scalar newtype arithmetic: `*`/`/` scale a numeric newtype by a plain Int/Decimal of
                // the same base (`金額 * 2`), staying in the newtype — the dimension is unchanged.
                // `金額 * 金額` and `金額 * 数量` (a dimension change / units, not modeled) fall through
                // to the base path below, an error.
                if (!addSub && scalarNewtypeArith(lt, rt, bin.op(), symbols)) {
                    yield closedNewtypeArithResult(lt, rt, symbols);
                }
                if (lt != Type.INT && lt != Type.DECIMAL) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.arith.operand").title("check.type.mismatch.title")
                                    .at(bin.pos()).args(Type.show(lt)).build(),
                            "operand of arithmetic must be Int or Decimal, got " + lt);
                }
                requireType(bin.right(), rt, lt, symbols, "operand of arithmetic");   // rt reused, no re-type
                yield lt;
            }
            case CONCAT -> {
                // `++` is Elm's appendable operator: two strings concatenate to a string, two lists to
                // a list (spec 18.1). Strings are checked first, before the empty-list absorption below.
                Type lraw = typeOf(bin.left(), env, data, symbols, reqs);
                Type rraw = typeOf(bin.right(), env, data, symbols, reqs);
                if (lraw == Type.STRING && rraw == Type.STRING) {
                    yield Type.STRING;
                }
                // A bottom operand ({@code Nothing}) is a list read from an accumulator an empty
                // collection seed grows — the value at a key of a `Map.empty`-seeded fold, whose element
                // type is not fixed yet. At run time it is a list, so read it as the empty list and let
                // the other operand fix the element type, as `[] ++ xs` does.
                Type lt = bottomAsEmptyList(lraw);
                Type rt = bottomAsEmptyList(rraw);
                if (!(lt instanceof Type.ListOf lo) || !(rt instanceof Type.ListOf ro)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.concat.msg")
                                    .title("check.concat.title")
                                    .at(bin.pos(), 2)
                                    .secondary(Region.ofWidth(bin.left().pos(), width(bin.left())),
                                            "check.operand", Type.show(lt))
                                    .secondary(Region.ofWidth(bin.right().pos(), width(bin.right())),
                                            "check.operand", Type.show(rt))
                                    .args(Type.show(lt), Type.show(rt))
                                    .build(),
                            "`++` needs two lists or two strings, got " + lt + " and " + rt);
                }
                yield Type.list(unifyElem(lo.element(), ro.element(), bin.pos()));
            }
            case EQ, NE -> {
                Type lt = typeOf(bin.left(), env, data, symbols, reqs);
                Type rt = typeOf(bin.right(), env, data, symbols, reqs);
                // two values of the same data compare by their fields (spec 16.2); across different
                // types there is nothing to compare. An operand may be the scalar empty-collection
                // bottom (`Nothing`) when it reads an accumulator a `[]` seed grows — the `e` in
                // `if any(e -> e == x, acc) …` over a `fold(…, [], xs)` is bound to the not-yet-fixed
                // element type (ADR-0028). At run time it holds the other operand's type, so absorb the
                // bottom rather than reject the comparison. (A whole empty list `[]` stays a type error
                // against a non-list, so this does not loosen `[] == 5`.)
                // A sum may also be compared with one of its cases (`役職 == 一般社員`): a case value
                // is a value of its sum (case->sum is transparent everywhere else, spec §sum-data), so
                // this is a sum-vs-sum comparison by case (spec §equality). Check the relation on the
                // top-level case sets directly, not through `assignable` — `assignable` recurses into
                // collections (covariance), which would wrongly let `List<一般社員> == List<役職>` compare;
                // the exemption is only the direct sum<->case scalar relationship. Unrelated types
                // (`金額 == 数量`) have disjoint case sets and still fail.
                Set<String> lCases = leafCases(lt, symbols);
                Set<String> rCases = leafCases(rt, symbols);
                boolean caseOfSum = !lCases.isEmpty() && !rCases.isEmpty()
                        && (lCases.containsAll(rCases) || rCases.containsAll(lCases));
                if (!lt.equals(rt) && !eqCoercible(lt, rt, bin.left(), bin.right(), symbols)
                        && !caseOfSum && !isBottom(lt) && !isBottom(rt)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.compare.msg")
                                    .title("check.compare.title")
                                    .at(bin.pos(), 2)
                                    .secondary(Region.ofWidth(bin.left().pos(), width(bin.left())),
                                            "check.operand", Type.show(lt))
                                    .secondary(Region.ofWidth(bin.right().pos(), width(bin.right())),
                                            "check.operand", Type.show(rt))
                                    .args(Type.show(lt), Type.show(rt))
                                    .build(),
                            "cannot compare " + lt + " with " + rt);
                }
                yield Type.BOOL;
            }
        };
    }

    /** The ordered primitives: the ones the JVM carries as {@link Comparable}, so {@code <}/{@code >}
     * and {@code sort} work on them (spec §primitives, §stdlib-list). A newtype over one of these is
     * a wrapper object, not itself Comparable, so it does not count. */
    private static boolean isOrdered(Type t) {
        return t == Type.INT || t == Type.STRING || t == Type.DECIMAL
                || t == Type.DATE || t == Type.DATETIME;
    }

    /** The underlying base of a type: itself, or — for a single-value newtype ({@code data X = Y}) —
     * the base of its {@code value} type, recursively (so {@code 管理職 = レベル = Int} bases to Int).
     * A newtype's value is what its comparison and equality read. */
    private static Type base(Type t, Map<String, Ast.Def> symbols) {
        if (isSingleValueNewtype(t, symbols)) {
            Type inner = fieldTypes((Ast.Data) symbols.get(((Type.Ref) t).name()), symbols).get("value");
            if (inner != null) {
                return base(inner, symbols);
            }
        }
        return t;
    }

    private static boolean isSingleValueNewtype(Type t, Map<String, Ast.Def> symbols) {
        return t instanceof Type.Ref ref
                && symbols.get(ref.name()) instanceof Ast.Data d && d.newtype();
    }

    /** A source literal (Int/Decimal/String/Bool, or a negated literal) — the only thing allowed to
     * take a newtype from the other operand. A variable of the underlying type is not (write the
     * newtype construction, e.g. {@code 金額(x)}). */
    private static boolean isLiteralExpr(Ast.Expr e) {
        return e instanceof Ast.IntLit || e instanceof Ast.DecimalLit
                || e instanceof Ast.StringLit || e instanceof Ast.BoolLit
                || (e instanceof Ast.Neg n && isLiteralExpr(n.operand()));
    }

    /** Whether {@code <}/{@code <=}/{@code >}/{@code >=} may compare the operands: both must reduce to
     * the same ordered base, and be either the same nominal type or a newtype paired with a bare
     * literal of its base (so {@code 金額 <= 金額} and {@code 金額 <= 100} pass, {@code 金額 <= 数量}
     * and {@code 金額 <= (Int variable)} do not). */
    private static boolean orderedComparable(Type lt, Type rt, Ast.Expr le, Ast.Expr re,
                                             Map<String, Ast.Def> symbols) {
        Type lb = base(lt, symbols);
        if (!isOrdered(lb) || !lb.equals(base(rt, symbols))) {
            return false;
        }
        if (lt.equals(rt)) {
            return true;
        }
        return literalPairsNewtype(lt, rt, le, re, symbols);
    }

    /** Whether {@code ==}/{@code /=} may pair a newtype with a bare literal of its base type (the
     * same-type and bottom cases are handled by the caller). */
    private static boolean eqCoercible(Type lt, Type rt, Ast.Expr le, Ast.Expr re,
                                       Map<String, Ast.Def> symbols) {
        return base(lt, symbols).equals(base(rt, symbols))
                && literalPairsNewtype(lt, rt, le, re, symbols);
    }

    /** Whether {@code +}/{@code -} may combine the operands as closed newtype arithmetic: a
     * single-value newtype whose value is Int or Decimal, paired with the same newtype, or with a
     * bare literal of that base. A nested newtype (value is another newtype) and {@code *}/{@code /}
     * are excluded; {@code 金額 - 数量} (two different newtypes) is not combinable and falls through
     * to the base-only path (an error). This is the one home of the admissibility rule; codegen and
     * the invariant analysis run on validated code and only pick the result via
     * {@link #closedNewtypeArithResult}. */
    private static boolean arithClosedNewtype(Type lt, Type rt, Ast.Expr le, Ast.Expr re,
                                              Map<String, Ast.Def> symbols) {
        Type ln = directNumericNewtypeBase(lt, symbols);
        Type rn = directNumericNewtypeBase(rt, symbols);
        if (ln == null && rn == null) {
            return false;   // no newtype operand — the plain Int/Decimal path handles it
        }
        if (lt.equals(rt)) {
            return ln != null;   // same single-value newtype over a numeric base
        }
        if (ln != null && !isSingleValueNewtype(rt, symbols) && isLiteralExpr(re) && rt.equals(ln)) {
            return true;        // 金額 + 100 (the literal takes 金額)
        }
        return rn != null && !isSingleValueNewtype(lt, symbols) && isLiteralExpr(le) && lt.equals(rn);
    }

    /** Whether {@code *}/{@code /} scales a single-value numeric newtype by a plain Int/Decimal of the
     * same base (`金額 * 2`, `2 * 金額`, `金額 / 2`), staying in the newtype. One operand is such a
     * newtype and the other is the bare base (Int/Decimal, literal or variable — a scalar is not
     * coerced to the newtype, it stays a plain number). {@code newtype × newtype} (a dimension change /
     * units, not modeled — spec §newtype-arithmetic) is excluded. Division is not commutative: only
     * {@code newtype / scalar} scales; {@code scalar / newtype} (`2 / 金額`) is an inverse — a
     * dimension change — so a scalar on the left is admitted for {@code *} only. */
    private static boolean scalarNewtypeArith(Type lt, Type rt, Ast.BinOp op, Map<String, Ast.Def> symbols) {
        Type ln = directNumericNewtypeBase(lt, symbols);
        Type rn = directNumericNewtypeBase(rt, symbols);
        if (ln != null && rn == null && rt.equals(ln)) {
            return true;   // 金額 * 2, 金額 / 2 — newtype on the left, scalar on the right
        }
        // scalar on the left (2 * 金額) is fine only for `*`; 2 / 金額 is an inverse, rejected.
        return op == Ast.BinOp.MUL && rn != null && ln == null && lt.equals(rn);
    }

    /** The single-value numeric newtype a closed {@code +}/{@code -} over {@code lt} and {@code rt}
     * yields — whichever operand is such a newtype — or {@code null} if neither is. Callers that have
     * already passed the type checker's {@link #arithClosedNewtype} gate (codegen, the invariant
     * analysis) use this to pick the result without re-deriving the admissibility rule. */
    public static Type closedNewtypeArithResult(Type lt, Type rt, Map<String, Ast.Def> symbols) {
        if (directNumericNewtypeBase(lt, symbols) != null) {
            return lt;
        }
        if (directNumericNewtypeBase(rt, symbols) != null) {
            return rt;
        }
        return null;
    }

    /** The Int or Decimal that a single-value newtype directly wraps (one level), or {@code null}
     * (a non-newtype, or a newtype over a non-numeric or over another newtype). */
    static Type directNumericNewtypeBase(Type t, Map<String, Ast.Def> symbols) {
        if (isSingleValueNewtype(t, symbols)) {
            Type inner = fieldTypes((Ast.Data) symbols.get(((Type.Ref) t).name()), symbols).get("value");
            if (inner == Type.INT || inner == Type.DECIMAL) {
                return inner;
            }
        }
        return null;
    }

    /** One side is a single-value newtype and the other is a bare literal (not itself a newtype). */
    private static boolean literalPairsNewtype(Type lt, Type rt, Ast.Expr le, Ast.Expr re,
                                               Map<String, Ast.Def> symbols) {
        return (isSingleValueNewtype(lt, symbols) && !isSingleValueNewtype(rt, symbols) && isLiteralExpr(re))
                || (isSingleValueNewtype(rt, symbols) && !isSingleValueNewtype(lt, symbols) && isLiteralExpr(le));
    }

    /** A binary numeric op over two Int or two Decimal operands (spec 18.2, 18.3). {@code compare}
     * yields Int (-1/0/1); the arithmetic ops yield the operand type. */
    private static Type numericOp(Ast.Call call, Map<String, Type> env, Ast.Data data,
                                  Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs, boolean compare) {
        arity(call, 2);
        Type lt = typeOf(call.args().get(0), env, data, symbols, reqs);
        if (lt != Type.INT && lt != Type.DECIMAL) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.arith.expects").title("check.type.mismatch.title")
                            .at(call.pos(), call.fn().length()).args(call.fn(), Type.show(lt)).build(),
                    call.fn() + " expects Int or Decimal, got " + lt);
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
    /**
     * Resolves the accumulator type for one function argument (a fold's step) of a helper call,
     * updating {@code bind}. The step is first typed at the accumulator the value arguments fixed —
     * the seed's type, which may be a narrow case. That type stands when the step is a fixpoint there
     * (it reads the seed's fields and returns the same case). Only when the narrow case does not type
     * (the step matches on the accumulator, which needs its sum) or is not a fixpoint (the step grows
     * the accumulator into its sum) is the accumulator widened to the sum that case belongs to, and the
     * step re-typed there. An empty-collection seed's bottom is refined from the block's result along
     * the way. Shared by the checker's call typing and the backend's step materialization, so the two
     * resolve identically.
     */
    public static void resolveStepBinding(String fnName, Type.FnOf declaredStep, Ast.Expr stepArg,
                                          Map<String, Type> bind, Map<String, Type> env, Ast.Data data,
                                          Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        Type.FnOf narrow = (Type.FnOf) substitute(declaredStep, bind);
        Type narrowGot = null;
        CompileException narrowFailed = null;
        try {
            narrowGot = blockType(fnName, stepArg, narrow.params(), env, data, symbols, reqs);
        } catch (CompileException e) {
            narrowFailed = e;
        }
        if (narrowGot != null) {
            refineBottom(declaredStep.result(), narrowGot, bind);
            Type want = substitute(declaredStep.result(), bind);
            if (want instanceof Type.Var || assignable(narrowGot, want, symbols)) {
                return;   // the narrow accumulator is a fixpoint
            }
        }
        // The step matches on, or grows the accumulator into, the sum the seed's case belongs to.
        if (declaredStep.result() instanceof Type.Var accVar) {
            Type sum = enclosingSum(substitute(declaredStep.result(), bind), symbols);
            if (sum != null) {
                Map<String, Type> widened = new HashMap<>(bind);
                widened.put(accVar.name(), sum);
                Type got = blockType(fnName, stepArg,
                        ((Type.FnOf) substitute(declaredStep, widened)).params(), env, data, symbols, reqs);
                if (assignable(got, sum, symbols)) {
                    bind.put(accVar.name(), sum);
                    return;
                }
            }
        }
        if (narrowGot == null) {
            throw narrowFailed;   // the narrow type errored and there was no sum to fall back to
        }
        throw CompileException.of(
                Diagnostic.of(null, "check.fn.argtype").title("check.fn.title")
                        .at(stepArg.pos()).args(fnName, Type.show(narrowGot),
                                Type.show(substitute(declaredStep.result(), bind))).build(),
                "the step of " + fnName + " returns " + Type.show(narrowGot)
                        + ", but the accumulator is " + Type.show(substitute(declaredStep.result(), bind)));
    }

    private static Type blockType(String fnName, Ast.Expr arg, List<Type> paramTypes,
                                  Map<String, Type> env, Ast.Data data,
                                  Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs) {
        if (!(arg instanceof Ast.Block block)) {
            // a function-typed value — a helper's function parameter (spec §fn-declaration) —
            // stands in for a block: check its shape and yield its result type.
            if (typeOf(arg, env, data, symbols, reqs) instanceof Type.FnOf fn) {
                if (fn.params().size() != paramTypes.size()) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.fn.callarity").title("check.fn.title")
                                    .at(arg.pos()).args(fnName, paramTypes.size(), fn.params().size())
                                    .build(),
                            fnName + " calls its function with " + paramTypes.size()
                                    + " argument(s), but it takes " + fn.params().size());
                }
                for (int i = 0; i < paramTypes.size(); i++) {
                    if (!assignable(paramTypes.get(i), fn.params().get(i), symbols)) {
                        throw CompileException.of(
                                Diagnostic.of(null, "check.fn.argtype").title("check.fn.title")
                                        .at(arg.pos()).args(fnName, Type.show(paramTypes.get(i)),
                                                Type.show(fn.params().get(i))).build(),
                                fnName + "'s element type " + paramTypes.get(i)
                                        + " is not acceptable to the function, which takes "
                                        + fn.params().get(i));
                    }
                }
                return fn.result();
            }
            throw CompileException.of(
                    Diagnostic.of(null, "check.fn.expectsblock").title("check.fn.title")
                            .at(arg.pos()).args(fnName).build(),
                    fnName + " expects a block, e.g. `" + fnName
                            + "((acc, x) -> ..., seed, xs)` (spec 12.5)");
        }
        if (block.params().size() != paramTypes.size()) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.fn.blockarity").title("check.fn.title")
                            .at(block.pos()).args(paramTypes.size(), block.params().size()).build(),
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
            case Ast.Block _ -> true;
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
            throw CompileException.of(
                    Diagnostic.of(null, "check.fn.noinfer").title("check.fn.title")
                            .at(body.pos()).args(name).build(),
                    "cannot infer the type of the function `" + name + "`: apply it (as `" + name
                            + "(x)`) at least once so its parameter types are known. A function passed"
                            + " on rather than applied — e.g. to a combinator — must be written inline"
                            + " instead (`map(xs, x -> ...)`)");
        }
        List<Type> first = uses.get(0);
        for (List<Type> u : uses) {
            if (!u.equals(first)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.fn.difftypes").title("check.fn.title")
                                .at(body.pos()).args(name, first.toString(), u.toString()).build(),
                        "the function `" + name + "` is applied with different argument types: "
                                + first + " vs " + u);
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
                    throw CompileException.of(
                            Diagnostic.of(null, "check.fn.lambdaarity").title("check.fn.title")
                                    .at(b.pos()).args(b.params().size(), paramTypes.size()).build(),
                            "this lambda takes " + b.params().size() + " parameter(s) but is applied with "
                                    + paramTypes.size());
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
                    throw CompileException.of(
                            Diagnostic.of(null, "check.fn.branchtypes").title("check.fn.title")
                                    .at(iff.pos(), 2).args(Type.show(t), Type.show(f)).build(),
                            "the two branches produce different function types: " + t + " vs " + f);
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
            throw CompileException.of(
                    Diagnostic.of(null, "check.builtin.shadow").title("check.reserved.title")
                            .at(pos, name.length()).args(name).build(),
                    "`" + name + "` is a built-in value and cannot be used as a binding name — it would"
                            + " shadow the built-in; choose another name");
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
            throw CompileException.of(
                    Diagnostic.of(null, "check.divide.rounding").title("check.type.mismatch.title")
                            .at(e.pos()).build(),
                    "the rounding mode of `divide` must be one of HALF_UP, HALF_EVEN, HALF_DOWN, UP,"
                            + " DOWN, CEILING, FLOOR (spec 18.3)");
        }
    }

    /** A stdlib argument-type error: {@code subject} (a function name) expects a container of kind
     * {@code kindKey} (a localized phrase such as "a List"), but got {@code actual}. */
    private static CompileException expects(SourcePos pos, String subject, String kindKey, Type actual,
                                            String legacy) {
        return CompileException.of(
                Diagnostic.of(null, "check.expects").title("check.type.mismatch.title").at(pos)
                        .args(subject, Localizable.of(kindKey), Type.show(actual)).build(),
                legacy);
    }

    /** A stdlib error where a list's element (or a key) must be an ordered primitive to sort/compare. */
    private static CompileException needsOrdered(SourcePos pos, String subject, Type element, String legacy) {
        return CompileException.of(
                Diagnostic.of(null, "check.ordered").title("check.type.mismatch.title").at(pos)
                        .args(subject, Localizable.of("kind.ordered.list"), Type.show(element))
                        .hint("check.ordered.hint").build(),
                legacy);
    }

    private static void arity(Ast.Call call, int n) {
        if (call.args().size() != n) {
            throw CompileException.of(
                    Diagnostic.of(null, "check.arity").title("check.arity.title")
                            .at(call.pos(), call.fn().length())
                            .args(call.fn(), n, call.args().size()).build(),
                    call.fn() + " expects " + n + " argument(s), got " + call.args().size());
        }
    }

    private static void requireType(Ast.Expr e, Type expected, Map<String, Type> env, Ast.Data data,
                                    Map<String, Ast.Def> symbols, Map<String, ReqSig> reqs, String what) {
        requireType(e, typeOf(e, env, data, symbols, reqs), expected, symbols, what);
    }

    /** As {@link #requireType(Ast.Expr, Type, Map, Ast.Data, Map, Map, String)}, but with the
     * operand's type already computed — a caller that has typed {@code e} does not re-type its
     * subtree. */
    private static void requireType(Ast.Expr e, Type actual, Type expected,
                                    Map<String, Ast.Def> symbols, String what) {
        if (!assignable(actual, expected, symbols)) {   // a case widens to its sum (spec 8.3)
            throw CompileException.of(
                    Diagnostic.of(null, "check.type.mismatch.msg")
                            .title("check.type.mismatch.title")
                            .at(e.pos(), width(e))
                            .args(what)
                            .diff(Type.show(actual), Type.show(expected))
                            .hint("check.type.mismatch.hint")
                            .build(),
                    what + " must be " + expected + " but is " + actual);
        }
    }

    /** A non-exhaustive-match error (E1201) listing every missing case. The legacy message names the
     * first missing case, as it did before, so callers reading the text are unchanged. */
    private static CompileException nonExhaustive(SourcePos pos, String what, List<String> missing) {
        return CompileException.of(
                Diagnostic.of("E1201", "e1201.msg")
                        .at(pos, 5)
                        .args(what)
                        .hint("e1201.hint", String.join(", ", missing))
                        .build(),
                "Non-exhaustive match for " + what + ". Missing case: " + missing.get(0));
    }

    /** A best-effort caret width for {@code e}: the token length when the node is a leaf whose source
     * text is known, otherwise 1. The renderer underlines this many columns from the node's start. */
    private static int width(Ast.Expr e) {
        return switch (e) {
            case Ast.Var v -> v.name().length();
            case Ast.StringLit s -> s.value().length() + 2;
            case Ast.IntLit i -> Long.toString(i.value()).length();
            case Ast.BoolLit b -> b.value() ? 4 : 5;
            case Ast.DecimalLit d -> d.value().toPlainString().length() + 1;
            case Ast.FieldAccess fa -> fa.field().length();
            case Ast.Call c -> c.fn().length();
            default -> 1;
        };
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

    /** The output type of a behavior return: a single case, or a union of two or more cases. */
    public static Type successType(Ast.RetType ret, Map<String, Ast.Def> symbols) {
        List<Type> members = new ArrayList<>();
        for (Ast.TypeRef t : ret.cases()) {
            members.add(resolveType(t, symbols));
        }
        if (members.size() == 1) {
            return members.get(0);
        }
        Set<String> names = new HashSet<>();
        for (Type m : members) {
            if (!(m instanceof Type.Ref r)) {
                throw CompileException.of(
                        Diagnostic.of(null, "check.union.members").title("check.boundary.title")
                                .at(ret.pos()).build(),
                        "union members must be data types");
            }
            names.add(r.name());
        }
        return Type.union(names);
    }

    /** Builds a Ref (one name) or Union (two or more) from a set of case names. */
    static Type caseSetType(Set<String> names) {
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

    /** Case names of a stage output, treating a {@code Raw} encoder output as the case {@code "Raw"}
     * so it can be unioned with propagated error cases (spec 14.1, 24). */
    private static Set<String> caseNamesOf(Type t) {
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

    /** Whether {@code t} is a sum: an anonymous union, or a reference to a named {@code data X = A | B}.
     * A case→sum widening only matters for these — not for a covariant list/map/tuple. */
    /** The sum type that {@code t}'s case belongs to, or null when {@code t} is not a case of a named
     * sum. A {@code fold} whose seed is a case ({@code PricedCart}) and whose step grows and matches the
     * accumulator at the sum ({@code PricedCart | NotFound}) is typed at that sum, not the seed case. */
    public static Type enclosingSum(Type t, Map<String, Ast.Def> symbols) {
        if (!(t instanceof Type.Ref ref) || symbols.get(ref.name()) instanceof Ast.SumData) {
            return null;
        }
        // A case may belong to more than one sum; pick by name so the choice is deterministic across
        // runs rather than dependent on the symbol map's iteration order.
        String chosen = null;
        for (Ast.Def d : symbols.values()) {
            if (d instanceof Ast.SumData sum && sum.cases().contains(ref.name())
                    && (chosen == null || sum.name().compareTo(chosen) < 0)) {
                chosen = sum.name();
            }
        }
        return chosen == null ? null : Type.ref(chosen);
    }

    public static boolean isSumType(Type t, Map<String, Ast.Def> symbols) {
        return t instanceof Type.Union
                || (t instanceof Type.Ref r && symbols.get(r.name()) instanceof Ast.SumData);
    }

    /** Whether a {@code from} value can be assigned where {@code to} is expected. Lists are
     * covariant, and a data-like type widens to the set of leaf cases it can be — so a list of
     * a sum's cases is assignable to a list of the sum (spec 8.3, 12.2). */
    public static boolean assignable(Type from, Type to, Map<String, Ast.Def> symbols) {
        if (from.equals(to)) {
            return true;
        }
        if (from == Type.NOTHING) {
            return true;   // the empty list's bottom element assigns into any element type (ADR-0028)
        }
        // immutable collections are element-covariant: A <: S makes a List/Map/Option of A
        // assignable to one of S. Sound because they cannot be mutated (spec 6), so no write can
        // smuggle a sibling case in — the same reason Scala's immutable List and Kotlin's read-only
        // List are covariant, and Java's mutable arrays are not.
        if (from instanceof Type.ListOf a && to instanceof Type.ListOf b) {
            return assignable(a.element(), b.element(), symbols);
        }
        if (from instanceof Type.MapOf a && to instanceof Type.MapOf b) {
            return assignable(a.key(), b.key(), symbols) && assignable(a.value(), b.value(), symbols);
        }
        if (from instanceof Type.SetOf a && to instanceof Type.SetOf b) {
            return assignable(a.element(), b.element(), symbols);
        }
        if (from instanceof Type.OptionOf a && to instanceof Type.OptionOf b) {
            return assignable(a.element(), b.element(), symbols);
        }
        if (from instanceof Type.TupleOf a && to instanceof Type.TupleOf b
                && a.elements().size() == b.elements().size()) {
            for (int i = 0; i < a.elements().size(); i++) {
                if (!assignable(a.elements().get(i), b.elements().get(i), symbols)) {
                    return false;
                }
            }
            return true;
        }
        Set<String> fa = leafCases(from, symbols);
        Set<String> ta = leafCases(to, symbols);
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
    public static void unify(Type param, Type arg, Map<String, Type> bindings,
                              Map<String, Ast.Def> symbols, SourcePos pos, String what) {
        switch (param) {
            case Type.Var v -> {
                Type bound = bindings.get(v.name());
                if (bound == null || bound == Type.NOTHING) {
                    // first sight, or widen an empty-collection bottom to a concrete element: an
                    // earlier `[]` / `Map.empty` argument bound NOTHING, and a later real element
                    // fixes it (ADR-0028). Order-independent, so insert(k, v, Map.empty) infers V.
                    bindings.put(v.name(), arg);
                } else if (arg == Type.NOTHING) {
                    // the empty bottom absorbs into the concrete binding already learned
                } else if (!assignable(arg, bound, symbols) && !assignable(bound, arg, symbols)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.generic.arg").title("check.type.mismatch.title")
                                    .at(pos).args(what, Type.show(bound), Type.show(arg))
                                    .diff(Type.show(arg), Type.show(bound)).build(),
                            what + ": expected " + bound + " but got " + arg);
                }
            }
            case Type.ListOf p when arg instanceof Type.ListOf a ->
                    unify(p.element(), a.element(), bindings, symbols, pos, what);
            case Type.MapOf p when arg instanceof Type.MapOf a -> {
                unify(p.key(), a.key(), bindings, symbols, pos, what);
                unify(p.value(), a.value(), bindings, symbols, pos, what);
            }
            case Type.SetOf p when arg instanceof Type.SetOf a ->
                    unify(p.element(), a.element(), bindings, symbols, pos, what);
            case Type.OptionOf p when arg instanceof Type.OptionOf a ->
                    unify(p.element(), a.element(), bindings, symbols, pos, what);
            case Type.TupleOf p when arg instanceof Type.TupleOf a
                    && p.elements().size() == a.elements().size() -> {
                for (int i = 0; i < p.elements().size(); i++) {
                    unify(p.elements().get(i), a.elements().get(i), bindings, symbols, pos, what);
                }
            }
            case Type.FnOf p when arg instanceof Type.FnOf a && p.params().size() == a.params().size() -> {
                for (int i = 0; i < p.params().size(); i++) {
                    unify(p.params().get(i), a.params().get(i), bindings, symbols, pos, what);
                }
                unify(p.result(), a.result(), bindings, symbols, pos, what);
            }
            default -> {
                if (!assignable(arg, param, symbols)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.generic.arg").title("check.type.mismatch.title")
                                    .at(pos).args(what, Type.show(param), Type.show(arg))
                                    .diff(Type.show(arg), Type.show(param)).build(),
                            what + ": expected " + param + " but got " + arg);
                }
            }
        }
    }

    /** Replaces the type variables bound by {@link #unify} in a result type. */
    public static Type substitute(Type t, Map<String, Type> bindings) {
        return switch (t) {
            case Type.Var v -> bindings.getOrDefault(v.name(), v);
            case Type.ListOf l -> Type.list(substitute(l.element(), bindings));
            case Type.MapOf m -> Type.map(substitute(m.key(), bindings), substitute(m.value(), bindings));
            case Type.SetOf s -> Type.set(substitute(s.element(), bindings));
            case Type.OptionOf o -> Type.option(substitute(o.element(), bindings));
            case Type.FnOf f -> {
                List<Type> params = new ArrayList<>();
                for (Type p : f.params()) {
                    params.add(substitute(p, bindings));
                }
                yield Type.fn(params, substitute(f.result(), bindings));
            }
            case Type.TupleOf tup -> {
                List<Type> es = new ArrayList<>();
                for (Type e : tup.elements()) {
                    es.add(substitute(e, bindings));
                }
                yield Type.tuple(es);
            }
            default -> t;
        };
    }

    /** The set of leaf (non-sum) case names a data-like type covers, flattening nested sums. */
    public static Set<String> leafCases(Type t, Map<String, Ast.Def> symbols) {
        Set<String> out = new HashSet<>();
        for (String name : namesOf(t)) {
            if (symbols.get(name) instanceof Ast.SumData s) {
                for (String caseName : s.cases()) {
                    out.addAll(leafCases(Type.ref(caseName), symbols));
                }
            } else {
                out.add(name);
            }
        }
        return out;
    }

    /** Whether {@code name} is a newtype over {@code String} ({@code data X = String}) — the only key
     *  type a {@code Map} admits besides {@code String} itself (ADR-0040). */
    static boolean isStringNewtype(String name, Map<String, Ast.Def> symbols) {
        return symbols.get(name) instanceof Ast.Data d && d.newtype()
                && d.fields().size() == 1 && "String".equals(d.fields().get(0).type().name());
    }

    public static Type resolveType(Ast.TypeRef ref, Map<String, Ast.Def> symbols) {
        if (ref.isTuple()) {
            List<Type> elems = new ArrayList<>();
            for (Ast.TypeRef e : ref.tupleElems()) {
                elems.add(resolveType(e, symbols));
            }
            return Type.tuple(elems);   // (A, B, ...) — a helper/stdlib signature only (ADR-0036)
        }
        return switch (ref.name()) {
            case "Int" -> Type.INT;
            case "String" -> Type.STRING;
            case "Bool" -> Type.BOOL;
            case "Decimal" -> Type.DECIMAL;
            case "Date" -> Type.DATE;
            case "DateTime" -> Type.DATETIME;
            // 制約違反 is no longer a writable case: an invariant violation aborts (spec 7.3, 9.4).
            case "List" -> {
                if (ref.arg() == null) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.typearg.list").title("check.typearg.title")
                                    .at(ref.pos(), 4).build(),
                            "List needs a type argument, e.g. List<Int>");
                }
                yield Type.list(resolveType(ref.arg(), symbols));
            }
            case "Set" -> {
                if (ref.arg() == null) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.typearg.set").title("check.typearg.title")
                                    .at(ref.pos(), 3).build(),
                            "Set needs a type argument, e.g. Set<String>");
                }
                yield Type.set(resolveType(ref.arg(), symbols));
            }
            case "Option" -> {
                if (ref.arg() == null) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.typearg.option").title("check.typearg.title")
                                    .at(ref.pos(), 6).build(),
                            "Option needs a type argument");
                }
                yield Type.option(resolveType(ref.arg(), symbols));
            }
            case "Map" -> {
                if (ref.arg() == null) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.typearg.map").title("check.typearg.title")
                                    .at(ref.pos(), 3).build(),
                            "Map needs a value type, e.g. Map<String, Int>");
                }
                Type key = ref.tupleElems() == null
                        ? Type.STRING : resolveType(ref.tupleElems().get(0), symbols);
                if (!(key == Type.STRING || key instanceof Type.Var
                        || (key instanceof Type.Ref r && isStringNewtype(r.name(), symbols)))) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.map.key").title("check.boundary.title")
                                    .at(ref.pos(), 3).args(Type.show(key)).build(),
                            "a Map key must be String or a String-backed newtype (`data X = String`), got "
                                    + key + " (ADR-0040)");
                }
                yield Type.map(key, resolveType(ref.arg(), symbols));
            }
            default -> {
                if (ref.name().startsWith("'")) {
                    yield Type.var(ref.name());   // a type variable, admitted only in the core
                }
                if (symbols.containsKey(ref.name())) {
                    yield Type.ref(ref.name());
                }
                throw CompileException.of(
                        Diagnostic.of(null, "check.unknown.type.msg")
                                .title("check.unknown.title")
                                .at(ref.pos(), ref.name().length())
                                .args(ref.name())
                                .suggestion(Suggest.candidate(ref.name(), symbols.keySet()))
                                .build(),
                        "unknown type `" + ref.name() + "`" + Suggest.hint(ref.name(), symbols.keySet()));
            }
        };
    }
}
