package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.diag.msg.InjectionMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checks a {@code behavior} and its implementing {@code let} are subject to: that the two agree
 * on inputs and output, that a {@code depends on} names something with a requirement of its own, that
 * no behavior reaches itself, that a stage takes one input, and that an exposed composition declares
 * the output it actually produces.
 */
public final class SpecChecker {

    private SpecChecker() {}

    /**
     * A behavior does not reach itself (spec {@code [#calling-a-behavior]}, E1608). The edges are
     * calls, {@code depends on} and {@code >->} stages, walked as one graph because a cycle through a
     * mixture of them is the same cycle: a {@code depends on} cycle leaves nothing to build first, and
     * a call cycle does not terminate.
     *
     * <p>Only this module's behaviors are walked. Reaching another module's takes an import, and a
     * cycle of imports is already refused (E1501), so following one here could not close a loop this
     * check has not already seen.
     */
    static void checkBehaviorsDoNotRecurse(Hir.Module module) {
        // This module's own behaviors, as declarations. A reference to another module's may be
        // written with the same name as one of these, and it is not one of these: matched by
        // spelling, a stage naming `other.f` would be an edge from this module's `f` to itself.
        Set<ValueName.Behavior> names = new LinkedHashSet<>();
        for (Hir.BehaviorDef b : module.behaviors()) {
            names.add(new ValueName.Behavior(module.name(), b.name()));
        }
        Map<ValueName.Behavior, List<ValueName.Behavior>> edges = new LinkedHashMap<>();
        Map<String, Hir.FnDef> fns = new LinkedHashMap<>();
        for (Hir.FnDef fn : module.fns()) {
            fns.put(fn.name(), fn);
        }
        for (Hir.BehaviorDef b : module.behaviors()) {
            List<ValueName.Behavior> out = new ArrayList<>();
            switch (b) {
                case Hir.SpecBehavior spec -> {
                    for (Hir.Var req : spec.dependsOn()) {
                        ValueName.Behavior named = behaviorReached(req);
                        if (named != null && names.contains(named) && !out.contains(named)) {
                            out.add(named);
                        }
                    }
                    Hir.FnDef fn = fns.get(spec.name());
                    if (fn != null) {
                        for (ValueName.Behavior called
                                : requiredCalls(fn.writtenBody(), names,
                                        SpecImplementation.align(spec, fn).injectedBindings())) {
                            if (!out.contains(called)) {
                                out.add(called);
                            }
                        }
                    }
                }
                case Hir.PipeBehavior pipe -> {
                    for (Hir.Var stage : pipe.stages()) {
                        ValueName.Behavior named = behaviorReached(stage);
                        if (named != null && names.contains(named) && !out.contains(named)) {
                            out.add(named);
                        }
                    }
                }
            }
            edges.put(new ValueName.Behavior(module.name(), b.name()), out);
        }
        for (Hir.BehaviorDef b : module.behaviors()) {
            ValueName.Behavior self = new ValueName.Behavior(module.name(), b.name());
            List<ValueName.Behavior> path = new ArrayList<>();
            if (reaches(self, self, edges, path, new HashSet<>())) {
                path.add(self);
                List<String> written = new ArrayList<>();
                for (ValueName.Behavior each : path) {
                    written.add(each.name());
                }
                throw CompileException.of(Diagnostic
                                .at(b.pos())
                                .hint(new DeclarationMessage.ABehaviorDoesNotRecurse()).say(new DeclarationMessage.ABehaviorReachesItself(b.name(), String.join(" -> ", written))).build());
            }
        }
    }

    /** Whether {@code target} is reachable from {@code from}, recording the way there in
     *  {@code path}. {@code path} starts with {@code from} and ends at the last step before
     *  {@code target}. */
    private static boolean reaches(ValueName.Behavior from, ValueName.Behavior target,
                                   Map<ValueName.Behavior, List<ValueName.Behavior>> edges,
                                   List<ValueName.Behavior> path, Set<ValueName.Behavior> seen) {
        if (!seen.add(from)) {
            return false;
        }
        path.add(from);
        for (ValueName.Behavior next : edges.getOrDefault(from, List.of())) {
            if (next.equals(target) || reaches(next, target, edges, path, seen)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    /**
     * A {@code depends on} names a behavior whose requirement set is not empty (spec
     * {@code [#depends-on]}): one with no implementation of its own, or one whose {@code let} declares
     * {@code depends on} in turn, here or in a module this one imports.
     *
     * <p>Reported where the clause is written, and the three ways it can be wrong are told apart
     * because the fix differs: a behavior that depends on nothing is called instead, a {@code >->}
     * composition cannot be rested on because its requirements are not written, and a name that is no
     * behavior has to be declared or imported. The body check would see only a call it cannot type
     * and report all three as a name that resolves to nothing (E1023).
     */
    static void checkRequiresAreInjectionTargets(Hir.Module module, Map<ValueName.Behavior, ReqSig> reqSigs,
                                                 Map<ValueName.Behavior, ReqSig> calleeSigs) {
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (!(b instanceof Hir.SpecBehavior spec)) {
                continue;
            }
            for (Hir.Var required : spec.dependsOn()) {
                if (!(required instanceof Hir.Var.Denoting named)) {
                    continue;
                }
                ValueName.Behavior reached = behaviorReached(required);
                if (reached != null && reqSigs.containsKey(reached)) {
                    continue;
                }
                String req = named.written().canonical();
                // Three ways a name can be wrong here, told apart because the fix differs. A
                // behavior that depends on nothing has nothing to inject and is called instead; a
                // composition cannot be rested on because its requirements are not written; a name
                // that is no behavior has to be declared or imported. Which of the three is read off
                // what the name was resolved to, so an imported composition is the composition case
                // rather than the unknown one — scanning this module's own behaviors would only find
                // the local ones. All are reported at the name, as the clause that names nothing is.
                boolean dependsOnNothing = reached != null && calleeSigs.containsKey(reached);
                boolean aComposition = named.denotes() instanceof ValueName.Behavior;
                throw CompileException.of(Diagnostic.at(required.written().reportedAt())
                        .say(dependsOnNothing
                                ? new DeclarationMessage
                                        .DependsOnNamesSomethingThatDependsOnNothing(spec.name(),
                                                req)
                                : aComposition
                                        ? new DeclarationMessage.DependsOnNamesAComposition(
                                                spec.name(), req)
                                        : new DeclarationMessage.DependsOnNamesNoSuchBehavior(
                                                spec.name(), req))
                        .hint(dependsOnNothing
                                ? new DeclarationMessage.RemoveItAndCallItDirectly(req)
                                : aComposition
                                        ? new DeclarationMessage
                                                .ACompositionsRequirementsAreNotWritten(req)
                                        : new DeclarationMessage.DeclareItHereOrImportIt(req))
                        .build());
            }
        }
    }

    /**
     * An exposed composition ({@code >->}) behavior must declare its output in the {@code exposing} list
     * ({@code exposing ( name : A | B )}, spec §declared-composition-output, ADR-0024), and the declaration
     * must match the inferred output exactly. A far-away change that grows the output then fails here, at the
     * module boundary, instead of reaching separately-compiled consumers unannounced.
     *
     * <p>The requirement applies only to a composition that is explicitly exposed: a module with no
     * {@code exposing} publishes everything with inference intact, and a non-composition behavior
     * states its type at its definition, so a signature on one is rejected.
     */
    static void checkExposedPipeOutputs(Hir.Module module, Set<String> exposed,
            Map<String, Sig> sigs, Symbols symbols) {
        Set<String> pipeNames = new HashSet<>();
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (b instanceof Hir.PipeBehavior p) {
                pipeNames.add(p.name());
            }
        }
        // a signature in `exposing` is only meaningful on a composition behavior
        for (String name : module.exposedOutputs().keySet()) {
            if (!pipeNames.contains(name)) {
                throw CompileException.of(Diagnostic.at(module.pos()).say(new DeclarationMessage.OnlyACompositionTakesAnOutputSignature(name)).build());
            }
        }
        // every exposed composition must declare its output, matching the inferred one
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (!(b instanceof Hir.PipeBehavior pipe) || !exposed.contains(pipe.name())) {
                continue;
            }
            Sig sig = sigs.get(pipe.name());
            if (sig == null) {
                // A composition with no signature is one that rests on a stage naming nothing,
                // reported where that stage was written. There is no output to hold a declaration
                // against, and the other compositions still have theirs.
                continue;
            }
            Set<TypeSymbol> inferred = new LinkedHashSet<>(AtomSpace.subjectAtoms(sig.outputType(), symbols));
            Hir.RetType declared = module.exposedOutputs().get(pipe.name());
            if (declared == null) {
                throw CompileException.of(Diagnostic.at(pipe.pos())
                                
                                .hint(new DeclarationMessage.WriteTheOutputSignature(pipe.name(), PipelineSigs.caseList(inferred)))
                                .say(new DeclarationMessage.AnExposedCompositionDeclaresItsOutput(pipe.name())).build());
            }
            // What was written is read first, and whether it can be compared with what is produced
            // is asked of the reading. A member no arm can name is a mistake in the declaration
            // itself, and it is the author's whether or not something beside it went unresolved.
            Type declaredOut = TypeOps.successType(declared);
            if (TypeOps.restsOnAnUnresolvedName(declared)) {
                throw new Unanswerable(declared.pos());
            }
            Set<TypeSymbol> declaredCases = new LinkedHashSet<>(AtomSpace.subjectAtoms(declaredOut, symbols));
            if (!inferred.equals(declaredCases)) {
                throw CompileException.of(Diagnostic.at(pipe.pos())
                                
                                .hint(new DeclarationMessage.UpdateTheOutputOrHandleTheCase())
                                .say(new DeclarationMessage.TheDeclaredOutputIsNotWhatThePipelineProduces(pipe.name(), PipelineSigs.caseList(declaredCases), PipelineSigs.caseList(inferred))).build());
            }
        }
    }

    /**
     * Checks a behavior's {@code fn} implementation against the behavior's declared signature
     * (spec §fn-declaration). The {@code fn}'s parameters are the behavior's inputs followed by its
     * {@code depends on} (§depends-on); the trailing ones name the injection targets in declared order and
     * do not bind values — they resolve as inline calls to those behaviors.
     *
     * <p>Both representations come back, because both were typed here and only one of them used to
     * leave. The analysis one was built to be read by the invariant check and then dropped, so every
     * later reader that wanted the meanings had the algorithm and nothing saying so.
     */
    static Checked checkSpecFn(Hir.SpecBehavior spec, Hir.FnDef fn, Hir.Expr inlinedBody,
                                    InvariantChecker.Source discharge,
                                    Symbols symbols, ReadingPolicy policy,
                                    Map<ValueName.Behavior, ReqSig> calleeSigs,
                                    Map<ValueName.Behavior, ReqSig> reqSigs, HelperInliner inliner,
                                    Map<String, Type> recursiveHelperFns,
                                    Map<String, DataChecker.Constructs> recHelperConstructs,
                                    List<Diagnostic> warnings) {
        if (fn.declaredReturn() != null) {
            throw CompileException.of(Diagnostic
                            .at(fn.pos()).say(new BehaviorMessage.AnImplementationsReturnComesFromTheBehavior(fn.name(), spec.name())).build());
        }
        for (Hir.Var required : spec.dependsOn()) {
            // A `depends on` naming nothing was reported where it is written. What this fn's trailing
            // parameters should be called comes from those names, so there is nothing to hold them
            // against — saying they are named wrongly would name the spelling that denotes nothing.
            if (required.unresolved()) {
                throw new Unanswerable(required.pos());
            }
        }
        // What each parameter this fn wrote stands for, asked of the behavior rather than worked
        // out here (§fn-declaration). Every question below about which parameter is which is read
        // off this one division.
        SpecImplementation.Implemented implemented = SpecImplementation.align(spec, fn);
        if (!implemented.hasExactArity()) {
            throw CompileException.of(Diagnostic
                            .at(fn.pos())
                            .say(new BehaviorMessage.TheImplementationTakesAnotherNumberOfParameters(fn.name(), String.valueOf(fn.params().size()), spec.name(), String.valueOf(spec.params().size()), String.valueOf(spec.dependsOn().size()))).build());
        }
        for (Hir.FnParam p : fn.params()) {
            // a pattern in parameter position names a type, but it is not an annotation: it opens
            // the input the behavior already typed
            if (p.type() != null && !p.typeFromPattern()) {
                throw CompileException.of(Diagnostic
                                .at(p.pos()).say(new BehaviorMessage.AnImplementationsParametersTakeTheirTypesFromIt(fn.name(), spec.name(), p.name())).build());
            }
        }
        for (SpecImplementation.ParameterBinding binding : implemented.bindings()) {
            switch (binding) {
                // An input's name is the implementation's to choose.
                case SpecImplementation.ParameterBinding.AnInput _ -> { }
                // A clause naming nothing names no parameter for this one to be out of order
                // against, and it was refused above, at the clause rather than at this list.
                case SpecImplementation.ParameterBinding.Unanswered _ -> { }
                // The arity is exact by here, so no parameter is standing past what the
                // declaration asks for.
                case SpecImplementation.ParameterBinding.Extraneous _ -> { }
                case SpecImplementation.ParameterBinding.AnInjection injected -> {
                    String got = injected.written().name();
                    if (!got.equals(injected.behavior().name())) {
                        throw CompileException.of(Diagnostic
                                        .at(fn.pos()).say(new BehaviorMessage.AnInjectedParameterIsOutOfOrder(fn.name(), got, injected.behavior().name())).build());
                    }
                }
            }
        }

        Scope env = Scope.NONE;
        for (Hir.FnParam p : fn.params()) {
            Elaborator.rejectBuiltinShadow(p.name(), p.pos());
        }
        Elaborator.rejectBuiltinShadowing(fn.writtenBody());
        for (SpecImplementation.ParameterBinding.AnInput input : implemented.declaredInputs()) {
            env = env.with(input.written().binder(),
                    TypeOps.successType(input.declared().type()));
        }
        // Which behavior each injected parameter stands for, read off the same division — an
        // implementation names its own parameters, and the behaviors it depends on may be declared
        // by different modules under one name.
        Map<souther.compiler.types.BindingId, ValueName.Behavior> dependsOn =
                implemented.injectedBindings();
        Type output = TypeOps.successType(spec.ret());
        // recursive helpers this behavior calls resolve through their signatures (spec §fn-declaration); merged
        // only for typing, so the construction and dependency walks below still see the business params alone.
        // A parameter of the same name wins: a binding in force wins over the declaration it shadows
        // (spec §fn-rules), so an input written `depth` is the input and not the helper spelled that way.
        Scope tenv = env.reaching(recursiveHelperFns);
        // Check functions passed to helper parameters (e.g. a combinator's predicate) against their
        // declared types first, so a mismatch names the parameter, not the derivation it expands to.
        // A nested fold reaches `List.foldFrom` inside a block, so its signature must be in scope here.
        HelperTyping.checkFunctionArgs(fn.writtenBody(), tenv, symbols, reqSigs, inliner);
        // The body arrives with helper calls already expanded (the Lower stage, ADR-0021): it is
        // checked as one expression, so a helper's constructions and injected calls count toward this
        // behavior's permission and dependencies — exactly as if the code had been written inline (§blocks).
        Hir.Expr body = inlinedBody;

        // push the declared output type into the body so a body that is directly an empty collection
        // (or a construction whose field is one) takes the declared type rather than a bottom
        Core elaboratedBody = Elaborator.elaborate(body, tenv,
                new CheckContext(symbols, null, reqSigs).withCallees(calleeSigs)
                        .withDependencies(dependsOn), output);
        Type rt = elaboratedBody.type();
        if (!TypeOps.assignable(rt, output, symbols)) {
            throw CompileException.of(Diagnostic
                            .at(body.pos())
                            .diff(Type.show(rt, output), Type.show(output, rt)).say(new BehaviorMessage.TheBodyIsNotWhatTheBehaviorReturns(spec.name(), Type.show(output), Type.show(rt))).build());
        }

        // One expression (spec §guard): this single walk sees every construction, including under a
        // desugared `guard`.
        DataChecker.Constructs constructed = DataChecker.Constructs.empty();
        DataChecker.collectConstructs(body, constructed, symbols, recHelperConstructs);
        // `constructs` on an fn-backed behavior is optional: its construction permission is internal
        // (invisible to callers, unlike `depends on`), so with the body visible the set can be inferred
        // (ADR-0002). Omit it and inference stands. Declare it and it must match the body exactly —
        // under-declaration is E1002, over-declaration E1006 — so an explicit clause stays a checkable,
        // readable record of what is newly built versus passed through (spec §constructs), the same exact
        // match `depends on` gets (E1602/E1603). Injected behaviors still declare it: no body to infer
        // from, and it drives factory generation (spec §java-base-class).
        if (!spec.constructs().isEmpty()) {
            // Both sides name types, and a type has one identity however it is written — `up.Amount`
            // and an `Amount` an import brings in are the same one. Each side keeps its own spelling
            // in whatever it has to report.
            Set<TypeSymbol> declared = new HashSet<>(MatchElaborator.denoted(spec.constructs()));
            // Every way this clause and this body disagree, and not the first of them. Both sides
            // are worked out once and whole before any of it is said, so stopping at one left the
            // author to fix that one, compile, and be told the next — a walk down the call graph,
            // one build per name. One clause is one thing to rewrite, and a clause that is short of
            // one name and carrying another it does not build is wrong in both directions at once:
            // reporting the two separately is two builds to learn what one reading of the body
            // already knows.
            //
            // One violation is one diagnostic. Each names the type it is about and carries the hint
            // for that type, so an editor has one thing to act on per name rather than a sentence
            // listing several. What is short comes before what is extra, in the order the body
            // builds them (`originated` keeps the order it collected them in) and then the order the
            // clause writes them — every one of them is at the declaration, so nothing reorders them
            // afterwards.
            List<Diagnostic> disagreements = new ArrayList<>();
            for (Map.Entry<TypeSymbol, String> built : constructed.originated().entrySet()) {
                if (!declared.contains(built.getKey())) {
                    String c = built.getValue();
                    disagreements.add(Diagnostic.at(spec.pos())
                                    .hint(new DeclarationMessage.AddTheConstructsEntry(spec.name(), c)).say(new DeclarationMessage.ItConstructsWithoutDeclaringIt(spec.name(), c)).build());
                }
            }
            for (Hir.Name declaredName : spec.constructs()) {
                String name = declaredName.written();
                // A name that names nothing declares no construction to be kept or removed; it is
                // reported where it is written.
                if (declaredName.answered() == null) {
                    continue;
                }
                // A unit is in no construction set, so `builds` is false for one however the body is
                // written — and E1006 would say the body never built it, which is a different claim
                // and often a false one. The entry has its own answer (E1026), raised by the check
                // that reads every clause of the module.
                //
                // Load-bearing rather than belt-and-braces: this reader is reached by the
                // per-behavior query as well, which asks about one body and not about the module, so
                // the clause check has not run before it. Measured from there by
                // `askingOneBodyOfSuchAModuleReportsNoOverDeclaration`.
                if (isUnitData(declaredName.answered().type(), symbols)) {
                    continue;
                }
                if (!constructed.builds(declaredName.answered().type())) {
                    disagreements.add(Diagnostic.at(spec.pos())
                                    .hint(new DeclarationMessage.RemoveTheConstructsEntry(name)).say(new DeclarationMessage.ItDeclaresConstructsAndNeverBuilds(spec.name(), name)).build());
                }
            }
            if (!disagreements.isEmpty()) {
                throw CompileException.ofAll(disagreements,
                        DiagnosticRenderer.legacyBody(disagreements.get(0)));
            }
        }
        // The `depends on` clause must match what the fn actually calls (spec §depends-on): missing -> E1602,
        // extra -> E1603.
        List<ValueName.Behavior> actual = requiredCalls(body, reqSigs.keySet(), dependsOn);
        List<ValueName.Behavior> declared = new ArrayList<>();
        for (Hir.Var required : spec.dependsOn()) {
            // Reported where it is written; it declares no requirement for a call to answer to.
            ValueName.Behavior named = behaviorReached(required);
            if (named != null) {
                declared.add(named);
            }
        }
        for (ValueName.Behavior call : actual) {
            if (!declared.contains(call)) {
                String written = call.name();
                throw CompileException.of(Diagnostic.at(spec.pos())
                                .hint(new DeclarationMessage.AddTheDependsOnEntry(written, spec.name())).say(new DeclarationMessage.ItCallsSomethingWithNoImplementation(fn.name(), written, spec.name())).build());
            }
        }
        for (ValueName.Behavior req : declared) {
            if (!actual.contains(req)) {
                String written = req.name();
                throw CompileException.of(Diagnostic.at(spec.pos())
                                .hint(new DeclarationMessage.RemoveTheDependsOnEntry(written)).say(new DeclarationMessage.ItDeclaresDependsOnAndNeverCallsIt(spec.name(), written, fn.name())).build());
            }
        }
        // Intraprocedural invariant discharge: seed from the input
        // newtypes' invariants, refine along each `guard`/`if` guard, and check every construction.
        // A guard-discharged one is silent; an unproven one is a warning (a possible abort); one the
        // guards prove must fail on a reachable path is an error (the path-sensitive generalization of
        // the constant `金額(-5)` check).
        // The discharge representation is typed by the checker like any other, keeping the language's
        // own operations standing because that is what the analysis reading it has rules about. A
        // representation there is none of is not analyzed at all, rather than analyzed over the
        // emitted tree, whose operations are no longer operations.
        Core dischargeBody = discharge == null ? null
                : Elaborator.elaborate(discharge.body(), tenv,
                        new CheckContext(symbols, null, reqSigs).withCallees(calleeSigs)
                                .withDependencies(dependsOn).forDischarge(), output);
        InvariantChecker.Findings inv = discharge == null
                ? InvariantChecker.Findings.notRun()
                : InvariantChecker.analyze(dischargeBody, discharge.reading(),
                        discharge.contracts(), env);
        warnings.addAll(inv.warnings());
        if (!inv.errors().isEmpty()) {
            throw inv.errors().get(0);
        }
        return new Checked(elaboratedBody,
                dischargeBody == null ? null
                        : new AnalysisBody(dischargeBody, discharge.elements()));
    }

    /**
     * What checking one body produced: the tree the backend emits, and the tree an analysis reads.
     *
     * <p>Both, because both were typed and they are not the same tree. Which one a reader wants
     * follows from what it is asking — what runs, or what the model means — and handing back one
     * {@code Core} made that a question nobody could ask.
     *
     * @param emitted  the algorithm, with the language's own operations expanded into what they do
     * @param analysis the meanings, with those operations standing as themselves, or null where this
     *                 behavior has no such representation. Null is "there is none" and never "it is
     *                 the other one": a reader owed the meanings and given the algorithm finds the
     *                 operations gone with nothing saying they were there
     */
    public record Checked(Core emitted, AnalysisBody analysis) {

        public Checked {
            if (emitted == null) {
                throw new IllegalArgumentException("a body that was checked was elaborated");
            }
        }
    }

    /**
     * Every effective member of a behavior's output goes by a name of its own. A member is named by
     * a {@code match} arm and by the {@code "type"} discriminator of the external representation, and
     * both take the name as written, so two types that are written the same cannot be members of one
     * union. Asked after a named sum is expanded to its leaves, since a sum contributes its cases.
     *
     * <p>Asked of the signature rather than of what was written, so a composition is subject to it as
     * well: two stages may depart cases of one spelling from two modules.
     */
    static void checkUnionMemberNames(Hir.Module module, Map<String, Sig> sigs, Symbols symbols) {
        for (Hir.BehaviorDef b : module.behaviors()) {
            Sig sig = sigs.get(b.name());
            if (sig == null) {
                continue;
            }
            TypeSymbol.AtModule[] clash = TypeOps.ambiguousMembers(sig.outputType(), symbols);
            if (clash == null) {
                continue;
            }
            throw CompileException.of(Diagnostic
                            .at(b.pos())
                            .hint(new TypeMessage.AMemberIsNamedByItsWrittenName()).say(new TypeMessage.OneNameForTwoMembers(clash[1].name(), clash[0].module(), clash[1].module())).build());
        }
    }

    /**
     * A member of an output union lays its fields flatly beside the discriminator the union writes
     * (spec §jvm-anonymous-union), so a member declaring a field of that name and the tag want one
     * key. The same rule a sum's cases are under, asked of the signature so a composition is subject
     * to it too. Which key that is, is {@code Boundary}'s and is not named again here.
     */
    static void checkUnionMemberFields(Hir.Module module, Map<String, Sig> sigs, Symbols symbols) {
        for (Hir.BehaviorDef b : module.behaviors()) {
            Sig sig = sigs.get(b.name());
            if (sig == null || !(sig.outputType() instanceof Type.Union)) {
                continue;
            }
            // The key is the settled representation's, the same one a named sum's cases are held to
            // (`DataChecker`). Written here as a constant of its own, this checker and the codec that
            // writes the key were two places the language's own spelling was kept.
            if (!(Boundary.of(sig.outputType(), symbols).representation()
                    instanceof Boundary.Representation.Discriminated(String key))) {
                continue;
            }
            TypeSymbol carrying = TypeOps.memberCarryingField(sig.outputType(), key, symbols);
            if (carrying == null) {
                continue;
            }
            throw CompileException.of(Diagnostic
                            .at(b.pos())
                            .hint(new DataMessage.TheTagAndTheFieldWantOneKey(key)).say(new DataMessage.AMemberDeclaresTheDiscriminatorField(carrying.name(), key, b.name())).build());
        }
    }


    /** Whether a name resolves to a unit data of this compilation or of a module it reads. */
    private static boolean isUnitData(TypeSymbol type, Symbols symbols) {
        return symbols.declaredNode(type) instanceof Hir.UnitData;
    }

    /**
     * No entry of a {@code constructs} clause is a unit data (spec §constructs-excludes-unit-data).
     *
     * <p>Asked of the clause and of nothing else, so it is the same answer for an injected behavior
     * and an implemented one. A unit is constructed where its name is written and holds no
     * construction authority — it has no fields, carries no invariant, and has one value, so there
     * is no telling a minted one from the existing one passed through. Collected into the set, the
     * position a unit name stood in decided the clause: {@code r.kind == Domestic} demanded an entry
     * that {@code match r.kind with | Domestic} did not.
     *
     * <p>Every entry of one clause, rather than the first, and handed back rather than raised: the
     * caller has the module's other clauses to ask the same of, and a wrong clause is one thing to
     * rewrite. That is the reason E1002 and E1006 report each name too.
     */
    static List<Diagnostic> unitDataNamedInConstructs(Hir.SpecBehavior spec, Symbols symbols) {
        List<Diagnostic> named = new ArrayList<>();
        for (Hir.Name name : spec.constructs()) {
            // A name that answers nothing names no data to be kept or removed; it is reported where
            // it is written.
            if (name.answered() != null && isUnitData(name.answered().type(), symbols)) {
                String c = name.written();
                named.add(Diagnostic.at(spec.pos())
                        .hint(new DeclarationMessage.RemoveTheConstructsEntry(c))
                        .say(new DeclarationMessage.ItNamesAUnitDataInConstructs(spec.name(), c))
                        .build());
            }
        }
        return named;
    }

    /**
     * An injected behavior's declared {@code constructs} must each be Java-buildable (spec §java-base-class):
     * an exposed data, whose {@code decoder} is public. An unexposed one is E1305 — Java has no way
     * to mint it.
     *
     * <p>Every entry is a non-unit data (spec §constructs-excludes-unit-data), so this reaches all of
     * them. A unit case of the output is creatable without being exposed, through the
     * {@code protected} factory the base class carries for it — which the output type supplies, not
     * this clause.
     */
    static void checkInjectionConstructs(Hir.SpecBehavior spec, Symbols symbols,
                                                 boolean exposeAll, Set<String> exposed) {
        for (Hir.Name name : spec.constructs()) {
            String c = name.written();
            if (name.answered() == null) {
                continue;   // it names no type to be built from outside; reported where written
            }
            TypeSymbol built = name.answered().type();
            // What Java needs is a way in: the decoder, which a module publishes by exposing the type.
            // For a type of another module that is its own `exposing` to answer, not this one's.
            // `exposed` lists this module's own names, so the resolved name is what to look up — a
            // type of this module written through it (`down.Out`) is the same one as `Out`
            boolean buildable = symbols.scope().isForeign(built)
                    ? symbols.scope().isExposed(built) : exposeAll || exposed.contains(built.name());
            if (!buildable) {
                throw CompileException.of(Diagnostic.at(spec.pos())
                                .hint(new DeclarationMessage.ExposeIt(c)).say(new DeclarationMessage.AnInjectedBehaviorConstructsWhatIsKept(spec.name(), c)).build());
            }
        }
    }

    /**
     * What a module reaches out with may not rest on what it keeps to itself.
     *
     * <p>Two things reach out. A name in {@code exposing} is one: a reader names it, so it names the
     * types its fields and its signature are written in, and a type this module keeps to itself has
     * no name there and a class the reader may not touch. The abstract base of an injected behavior
     * is the other, whether or not the behavior is exposed, because it is public whatever
     * {@code exposing} says (spec §java-base-class) and the implementation writes the input and output types
     * where it overrides {@code apply} — with no raw-typed override to fall back on, since the
     * erased signature clashes with the one being overridden rather than overriding it.
     *
     * <p>Reported here, where the module decides it, rather than at each reader that runs into it —
     * as Rust's {@code private_interfaces} and F#'s {@code FS0410} do.
     *
     * <p>This does not reach the <em>cases</em> of a sum, or of an output written as more than one
     * case. Those are generated as a union interface which is public regardless, and a case reaches
     * a reader through the decoder or through the {@code protected} factory rather than by being
     * named — which is what E1305's unit-data allowance rests on.
     */
    static void checkExposedSurface(Hir.Module module, Set<String> injectionTargets,
                                    Map<String, Sig> sigs, Symbols symbols,
                                    boolean exposeAll, Set<String> exposed,
                                    Map<String, Type> definitionTypes) {
        if (exposeAll) {
            return;   // nothing is kept to the module, so nothing can be rested on
        }
        for (Hir.Def d : module.defs()) {
            if (!(d instanceof Hir.Data data) || !exposed.contains(data.name())) {
                continue;
            }
            // Read through the includes: a spread flattens another data's fields into this one, so
            // they are this data's fields on the generated class and carry their types with them.
            for (Map.Entry<String, Type> f : TypeOps.fieldTypes(data, symbols).entrySet()) {
                refuseHidden(f.getValue(),
                        hidden -> Diagnostic.say(new ModuleMessage.AnExposedFieldRestsOnWhatIsKept(data.name(),
                                f.getKey(), hidden))
                                .hint(new ModuleMessage.WhatReachesOutMayNotRestOnWhatIsKept(hidden,
                                data.name())),
                        data.pos(), symbols, exposeAll, exposed);
            }
        }
        // A published definition may not rest on a type the module keeps to itself: a reader would
        // hold a value it has no name for, or be unable to write the argument it has to pass. Both
        // questions are asked of the definition's *type* — what it stands for, and what it takes —
        // and never of the constructions its body happens to make on the way, which are its own
        // workings and would put every inner type back in the reader's import list.
        //
        // The types are settled from the body (ADR-0066), so they are read from what the standalone
        // helper check settled rather than from the shape of the body: a definition may name a type
        // it does not build (`let published (n: Int) : List<Hidden> = []`), and reading the body for
        // constructions answers about a different thing and misses that one.
        //
        // A behavior's input and output are asked below, under its own name; a behavior's own `let`
        // is an implementation and not one of the module's definitions.
        for (Hir.FnDef fn : HelperInliner.helpersOf(module).values()) {
            if (!(fn.body() instanceof Hir.FnBody.Written) || !exposed.contains(fn.name())) {
                continue;
            }
            for (Hir.FnParam p : fn.params()) {
                refuseHidden(TypeOps.resolveParamType(p.type()),
                        hidden -> Diagnostic
                                .say(new ModuleMessage.AnExposedArgumentRestsOnWhatIsKept(fn.name(),
                                        p.name(), hidden))
                                .hint(new ModuleMessage.WhatReachesOutMayNotRestOnWhatIsKept(hidden,
                                        fn.name())),
                        fn.pos(), symbols, exposeAll, exposed);
            }
            // A definition whose check did not settle a type has none to ask about: it failed its own
            // check, which is reported, or it returns a function, which does not cross into another
            // module as a value (ADR-0004).
            Type stands = definitionTypes.get(fn.name());
            if (stands != null) {
                refuseHidden(stands,
                        hidden -> Diagnostic.say(new ModuleMessage.AnExposedValueRestsOnWhatIsKept(fn.name(),
                                hidden))
                                .hint(new ModuleMessage.WhatReachesOutMayNotRestOnWhatIsKept(hidden,
                                fn.name())),
                        fn.pos(), symbols, exposeAll, exposed);
            }
        }
        // Read off the signature map rather than the declarations: a composition's input and output
        // are inferred from its stages, so they are written nowhere, and this is the one place both
        // kinds of behavior answer the same question.
        for (Hir.BehaviorDef b : module.behaviors()) {
            boolean injected = injectionTargets.contains(b.name());
            Sig sig = sigs.get(b.name());
            if (sig == null || (!injected && !exposed.contains(b.name()))) {
                continue;
            }
            // An injected behavior and an exposed one are refused for different reasons and told
            // different things to do, so each says its refusal and its repair together rather than
            // the two being chosen apart and paired up here.
            for (Type in : sig.inputTypes()) {
                refuseHidden(in,
                        hidden -> injected
                                ? Diagnostic
                                        .say(new InjectionMessage.AnInjectedInputRestsOnWhatIsKept(
                                                b.name(), hidden))
                                        .hint(new InjectionMessage
                                                .TheBaseClassIsPublicWhateverExposingSays(hidden))
                                : Diagnostic
                                        .say(new ModuleMessage.AnExposedInputRestsOnWhatIsKept(
                                                b.name(), hidden))
                                        .hint(new ModuleMessage
                                                .WhatReachesOutMayNotRestOnWhatIsKept(hidden,
                                                        b.name())),
                        b.pos(), symbols, exposeAll, exposed);
            }
            refuseHidden(sig.outputType(),
                    hidden -> injected
                            ? Diagnostic
                                    .say(new InjectionMessage.AnInjectedOutputRestsOnWhatIsKept(
                                            b.name(), hidden))
                                    .hint(new InjectionMessage
                                            .TheBaseClassIsPublicWhateverExposingSays(hidden))
                            : Diagnostic
                                    .say(new ModuleMessage.AnExposedOutputRestsOnWhatIsKept(
                                            b.name(), hidden))
                                    .hint(new ModuleMessage.WhatReachesOutMayNotRestOnWhatIsKept(
                                            hidden, b.name())),
                    b.pos(), symbols, exposeAll, exposed);
        }
    }

    private static void refuseHidden(Type written,
                                     java.util.function.Function<String, Diagnostic.Builder> saying,
                                     SourcePos pos, Symbols symbols,
                                     boolean exposeAll, Set<String> exposed) {
        if (written == null) {
            return;   // an unresolved reference has its own error
        }
        // `mentions` stops at the first match, so the predicate keeps the name it stopped on: a
        // collection carries its element out with it too (`List<Id>` names `Id`).
        TypeSymbol[] hidden = new TypeSymbol[1];
        Type.mentions(written, t -> {
            if (t instanceof Type.Ref ref && !nameableOutside(ref.name(), symbols, exposeAll, exposed)) {
                hidden[0] = ref.name();
                return true;
            }
            return false;
        });
        if (hidden[0] == null) {
            return;
        }
        String name = hidden[0].name();
        throw CompileException.of(saying.apply(name).at(pos).build());
    }

    /** Whether a reader outside the declaring module can write {@code name}. */
    private static boolean nameableOutside(TypeSymbol name, Symbols symbols, boolean exposeAll,
                                           Set<String> exposed) {
        return symbols.scope().isForeign(name)
                ? symbols.scope().isExposed(name) : exposeAll || exposed.contains(name.name());
    }

    /**
     * Nothing built here may hold a behavior Souther is to implement and nobody has (spec
     * §unwritten-behavior).
     *
     * <p>Declaring one is not an error and neither is resting on one from another that is itself
     * unwritten: that graph is the specification, and a model written example-first passes through
     * it. What is refused is a behavior with a body — a {@code let} of its own, or a {@code >->} that
     * is its own implementation — which would hold the unwritten one as an injected field. Nothing
     * can supply it: Java has no base to extend, because the model says the body is Souther's to
     * write, and Souther has not written it.
     *
     * <p>One clause deep and not the whole requirement closure. A behavior with a body that rests on
     * one that rests on something unwritten is refused at the middle one, which has a body too — so
     * walking further would report the same model twice and name a behavior the author did not
     * write the clause on.
     */
    static void checkNothingBuiltHereRestsOnAnUnwrittenBehavior(
            Hir.Module module, Set<ValueName.Behavior> importedUnwritten) {
        Set<String> fns = new HashSet<>();
        for (Hir.FnDef fn : module.fns()) {
            fns.add(fn.name());
        }
        Set<ValueName.Behavior> unwritten = new HashSet<>(importedUnwritten);
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (Requirements.implementationOf(b, fns) == BehaviorImplementation.UNIMPLEMENTED) {
                unwritten.add(new ValueName.Behavior(module.name(), b.name()));
            }
        }
        if (unwritten.isEmpty()) {
            return;
        }
        Map<ValueName.Behavior, List<Hir.Var>> pipeStages = PipelineSigs.pipelineStages(module);
        for (Hir.BehaviorDef b : module.behaviors()) {
            switch (b) {
                case Hir.SpecBehavior spec when fns.contains(spec.name()) ->
                        refuseFirstUnwritten(spec.name(), spec.dependsOn(), unwritten);
                case Hir.PipeBehavior pipe -> refuseFirstUnwritten(pipe.name(),
                        PipelineSigs.flattenStages(pipe.stages(), pipeStages, pipe.pos()), unwritten);
                default -> { }
            }
        }
    }

    /** The first of {@code named} that nobody has written, reported where it is named. */
    private static void refuseFirstUnwritten(String behavior, List<Hir.Var> named,
                                             Set<ValueName.Behavior> unwritten) {
        for (Hir.Var each : named) {
            ValueName.Behavior reached = behaviorReached(each);
            if (reached == null || !unwritten.contains(reached)) {
                continue;
            }
            throw CompileException.of(Diagnostic.at(each.written().reportedAt())
                    .say(new DeclarationMessage.ItRestsOnABehaviorNobodyHasWritten(
                            behavior, reached.name()))
                    .hint(new DeclarationMessage.WriteItsLetOrLeaveThisOneUnwrittenToo(
                            reached.name(), behavior))
                    .build());
        }
    }

    /**
     * Every stage after the first takes exactly one input (spec §sequential-composition): {@code >->} hands a single
     * value along.
     *
     * <p>The first stage is not restricted — it consumes the pipeline's own arguments, and the
     * pipeline simply takes what it takes. The spec DSL relies on this
     * (`behavior 却下して差し戻す = 却下する >-> 差し戻す`, where `却下する` reads
     * `事前承認待ち AND 却下者ID`); requiring the whole chain to be single-input would reject the
     * very line 14.1 cites.
     */
    static void checkStagesAreSingleInput(Hir.Module module) {
        Map<ValueName.Behavior, Integer> arity = new HashMap<>();
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (b instanceof Hir.SpecBehavior spec) {
                arity.put(new ValueName.Behavior(module.name(), spec.name()), spec.params().size());
            }
        }
        Map<ValueName.Behavior, List<Hir.Var>> pipeStages = PipelineSigs.pipelineStages(module);
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (!(b instanceof Hir.PipeBehavior pipe)) {
                continue;
            }
            // check the flattened stages: a named intermediate splices in its own first stage, which
            // then sits after `>->` and so must be single-input too (spec §sequential-composition, §type-routing)
            List<Hir.Var> stages = PipelineSigs.flattenStages(pipe.stages(), pipeStages,
                    pipe.pos());
            for (int i = 1; i < stages.size(); i++) {
                ValueName.Behavior stage = behaviorReached(stages.get(i));
                if (stage == null) {
                    continue;   // reported where it is written; it declares no arity to hold it to
                }
                Integer n = arity.get(stage);
                if (n != null && n != 1) {
                    throw CompileException.of(Diagnostic
                                    .at(pipe.pos()).say(new BehaviorMessage.AStageAfterTheFirstTakesOneInput(stage.name(), String.valueOf(n), pipe.name())).build());
                }
            }
        }
    }

    /** The distinct injection targets a fn body calls, in first-seen order. Calls may appear
     * anywhere in an expression (e.g. inline in a record literal), not only bound to a let. */
    public static List<ValueName.Behavior> requiredCalls(
            Hir.Expr body, java.util.Set<ValueName.Behavior> requiredNames,
            Map<souther.compiler.types.BindingId, ValueName.Behavior> dependencies) {
        List<ValueName.Behavior> calls = new java.util.ArrayList<>();
        collectRequiredCalls(body, requiredNames, dependencies, calls);
        return calls;
    }

    /**
     * The behavior {@code named} reaches, or null where resolution found none or it names something
     * that is no behavior.
     *
     * <p>Asked of the declaration and not of the name it was written under. A call to another
     * module's behavior and a call to one this module declares can be written the same, and what a
     * requirement is about is one of the two.
     */
    private static ValueName.Behavior behaviorReached(Hir.Var named) {
        return named.answered() != null
                && named.answered().denotes() instanceof ValueName.Behavior behavior
                ? behavior : null;
    }

    private static void collectRequiredCalls(
            Hir.Expr e, Set<ValueName.Behavior> requiredNames,
            Map<souther.compiler.types.BindingId, ValueName.Behavior> dependencies,
            List<ValueName.Behavior> out) {
        if (e instanceof Hir.Apply call && call.answered() != null) {
            // The behavior called: one named outright, or the trailing parameter an implementation
            // takes it as, which is a binding.
            ValueName.Behavior reached = switch (call.answered().denotes()) {
                case ValueName.Behavior behavior -> behavior;
                case ValueName.Local local -> dependencies.get(local.id());
                default -> null;
            };
            if (reached != null && requiredNames.contains(reached) && !out.contains(reached)) {
                out.add(reached);
            }
        }
        // Every subexpression, through the one exhaustive walk — a call to an injected behavior may
        // sit anywhere. Listing the node kinds here instead left `-dep(x)` and `(dep(x), y)` out of
        // the set, and a block's requirements still float out to the behavior that passes it
        // (spec §blocks, §requirement-propagation) because a Block's body is one of its children.
        Hir.forEachChild(e, c -> collectRequiredCalls(c, requiredNames, dependencies, out));
    }

}
