package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Composition;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Behavior signatures and pipeline composition: what each behavior takes and yields, and how
 * {@code >->} routes a stage's output cases into the next stage (spec §composition).
 *
 * <p>The routing walk here is the only one. A backend used to replay it as it emitted a pipeline,
 * which made what a stage is offered a thing two places worked out; {@link #composition} answers it
 * once and both the signature and the emitter read that.
 */
public final class PipelineSigs {

    private PipelineSigs() {}

    /**
     * The signature of every behavior a module can name, which is what a stage is typed against.
     *
     * <p>{@code declared} is what the module's own declarations were admitted as, by the name each
     * is declared under, and {@code imported} the same for the behaviors it borrows (spec §modules,
     * §composition) — so a stage naming either resolves through {@link #stageSig}. What is worked
     * out here is the compositions, whose answer nobody wrote.
     *
     * <p>A declared behavior's signature is what it declares, whether a {@code let} implements it
     * here or the Java side is injected (spec §injected-behavior): both are named the same way from a
     * {@code >->} or a {@code depends on}, and both need the output union's generated interface.
     * Where the arity rules out a use — every stage after the first takes one input
     * (§sequential-composition) — the composition says so; leaving the name out of this map instead
     * would report it as one that was never declared.
     */
    public static Map<ValueName.Behavior, Sig> signatures(String module,
                                                         List<Hir.BehaviorDef> behaviors,
                                                         Map<String, Sig> declared,
                                                         Symbols symbols,
                                                         PublishedDeclarations published,
                                                         DeclarationKinds kinds,
                                                         Map<ValueName.Behavior, Sig> imported) {
        Map<ValueName.Behavior, Sig> sigs = new HashMap<>(imported);
        declared.forEach((name, sig) -> sigs.put(new ValueName.Behavior(module, name), sig));
        Map<ValueName.Behavior, List<Hir.Var>> pipeStages = pipelineStages(module, behaviors);
        for (Hir.BehaviorDef b : behaviors) {
            if (b instanceof Hir.PipeBehavior pipe) {
                try {
                    sigs.put(new ValueName.Behavior(module, pipe.name()),
                            pipeSig(pipe, sigs, symbols, published, kinds, pipeStages));
                } catch (Unanswerable _) {
                    // A stage that names nothing was reported where it was written, and this
                    // composition has no signature to work out. It is one behavior: the others keep
                    // theirs, and the module is checked past it. Nothing will be emitted — the name
                    // that denotes nothing is what `Names.Sound` answers for.
                }
            }
        }
        return sigs;
    }

    /** Maps each pipeline behavior's name to its declared stages (for flattening, spec §type-routing). */
    public static Map<ValueName.Behavior, List<Hir.Var>> pipelineStages(Hir.Module module) {
        return pipelineStages(module.name(), module.behaviors());
    }

    /** The same, of the behaviors themselves — what a reader holding them rather than a module
     * asks. */
    public static Map<ValueName.Behavior, List<Hir.Var>> pipelineStages(
            String module, List<Hir.BehaviorDef> behaviors) {
        Map<ValueName.Behavior, List<Hir.Var>> stages = new HashMap<>();
        for (Hir.BehaviorDef b : behaviors) {
            if (b instanceof Hir.PipeBehavior pipe) {
                stages.put(new ValueName.Behavior(module, pipe.name()), pipe.stages());
            }
        }
        return stages;
    }

    /**
     * Flattens a pipeline's stage list, splicing any stage that is itself a pipeline into its own
     * (recursively flattened) stages (spec §type-routing). This is what makes {@code >->} associative:
     * {@code half >-> finish} with {@code half = split >-> work} routes over {@code split, work,
     * finish}, exactly as the flat form would, so a retired case stays retired across a named
     * intermediate. A pipeline viewed on its own still has the merged output its own stages produce.
     */
    public static List<Hir.Var> flattenStages(List<Hir.Var> stages,
                                                   Map<ValueName.Behavior, List<Hir.Var>> pipeStages,
                                                   SourcePos pos) {
        List<Hir.Var> out = new ArrayList<>();
        flattenInto(stages, pipeStages, out, new LinkedHashSet<>(), pos);
        return out;
    }

    private static void flattenInto(List<Hir.Var> stages,
                                    Map<ValueName.Behavior, List<Hir.Var>> pipeStages,
                                    List<Hir.Var> out, Set<ValueName.Behavior> inProgress,
                                    SourcePos pos) {
        for (Hir.Var s : stages) {
            // A stage that names nothing was reported where it is written. It is no pipeline to
            // splice in, and the composition it is part of is abandoned where its signature is
            // asked for rather than here.
            ValueName.Behavior named = reaches(s);
            List<Hir.Var> sub = named == null ? null : pipeStages.get(named);
            if (sub == null) {
                out.add(s);
                continue;
            }
            if (!inProgress.add(named)) {
                throw CompileException.of(Diagnostic
                                .at(pos).say(new BehaviorMessage.APipelineComposesWithItself(s.name())).build());
            }
            flattenInto(sub, pipeStages, out, inProgress, pos);
            inProgress.remove(named);
        }
    }

    /**
     * The behavior {@code stage} names, or null where resolution found none.
     *
     * <p>The declaration and not the name it is written under. Two modules may declare a behavior
     * of one name and a stage says which of them it reaches, so a table asked with the spelling
     * answers whichever entry was written last — which is a different behavior, typed and emitted
     * as though the author had named it.
     */
    private static ValueName.Behavior reaches(Hir.Var stage) {
        return stage.answered() != null
                && stage.answered().denotes() instanceof ValueName.Behavior behavior
                ? behavior : null;
    }

    /**
     * The signature of a pipeline stage.
     *
     * <p>Which behavior a stage names was answered when the module's names were resolved, so there
     * is no spelling to test here. A stage that names nothing was reported there, and this
     * composition has no meaning to work out: the behavior it belongs to is abandoned, and the
     * definitions around it are checked as they would be without it.
     */
    private static Sig stageSig(Hir.Var stage, Map<ValueName.Behavior, Sig> sigs) {
        ValueName.Behavior named = reaches(stage);
        if (named == null) {
            throw new Unanswerable(stage.pos());
        }
        Sig s = sigs.get(named);
        if (s == null) {
            // The behavior is declared by a module this compilation could not work out — reported
            // on that module, whose author is the one who can act on it.
            throw new Unanswerable(stage.pos());
        }
        return s;
    }

    /**
     * The routing a composition performs, as the checker settles it.
     *
     * <p>The one walk. What a stage is offered and what runs on after it decides both what the
     * composition answers — which is its signature — and what a backend emits, and those two were
     * worked out separately: here, and again in the JVM emitter as it wrote the branches. The
     * emitter's copy is gone; it reads this.
     *
     * <p>Asked of a declaration and the signatures around it, so it can be asked before the module
     * has checked (a signature needs it) and after (a backend needs it), and answer the same.
     */
    public static Composition composition(Hir.PipeBehavior pipe, Map<ValueName.Behavior, Sig> sigs,
                                          Symbols symbols, PublishedDeclarations published,
                                          Map<ValueName.Behavior, List<Hir.Var>> pipeStages) {
        // flatten nested pipeline stages so `>->` is associative (spec §type-routing)
        List<Hir.Var> stages = flattenStages(pipe.stages(), pipeStages, pipe.pos());
        Sig first = stageSig(stages.get(0), sigs);
        List<Composition.Stage> walked = new ArrayList<>();
        // the first stage takes the composition's own arguments, so nothing is routed into it
        walked.add(new Composition.Stage(reaches(stages.get(0)), first.outputType(),
                new Composition.Routing.Always()));
        Type mainline = first.outputType();
        Set<TypeSymbol> retired = new LinkedHashSet<>();
        for (int i = 1; i < stages.size(); i++) {
            Sig g = stageSig(stages.get(i), sigs);
            // Every stage after the first takes exactly one input (spec §sequential-composition).
            // `checkStagesAreSingleInput` says so too and is the diagnostic the author usually sees, but
            // signatures are built before it runs and are also built for an imported module that was never
            // checked here — so the arity is confirmed rather than assumed, or `route` would index an empty
            // input list.
            if (g.ins().size() != 1) {
                throw CompileException.of(Diagnostic
                                .at(pipe.pos()).say(new BehaviorMessage.AStageAfterTheFirstTakesOneInput(stages.get(i).name(), String.valueOf(g.ins().size()), pipe.name())).build());
            }
            // A running value carrying cases is offered to this stage only where it is one the
            // stage accepts (spec §type-routing). A running value with no cases to tell apart is
            // offered whole: there is nothing to decide.
            walked.add(new Composition.Stage(reaches(stages.get(i)), g.outputType(),
                    TypeOps.isDataLike(mainline)
                            ? new Composition.Routing.OnCases(
                                    mainlineCases(mainline, g, symbols, published))
                            : new Composition.Routing.Always()));
            mainline = route(mainline, g, retired, symbols, published, pipe.pos());
        }
        return new Composition(walked, withRetired(mainline, retired));
    }

    private static Sig pipeSig(Hir.PipeBehavior pipe, Map<ValueName.Behavior, Sig> sigs,
                               Symbols symbols, PublishedDeclarations published,
                               DeclarationKinds kinds,
                               Map<ValueName.Behavior, List<Hir.Var>> pipeStages) {
        Composition composed = composition(pipe, sigs, symbols, published, pipeStages);
        Type out = composed.answers();
        // an optional declared output must match the inferred one exactly (spec
        // §declared-composition-output): neither a missing case (too narrow) nor an extra one (too wide) is
        // accepted.
        if (pipe.declaredOut() != null) {
            // What was written is read first, and whether it can be compared with what is produced
            // is asked of the reading. A member no arm can name is a mistake in the declaration
            // itself, and it is the author's whether or not something beside it went unresolved.
            Type declaredOut = TypeOps.successType(pipe.declaredOut());
            if (TypeOps.restsOnAnUnresolvedName(pipe.declaredOut())) {
                throw new Unanswerable(pipe.declaredOut().pos());
            }
            Set<TypeSymbol> inferred = new LinkedHashSet<>(AtomSpace.subjectAtoms(out, published));
            Set<TypeSymbol> declared =
                    new LinkedHashSet<>(AtomSpace.subjectAtoms(declaredOut, published));
            if (!inferred.equals(declared)) {
                throw CompileException.of(Diagnostic.at(pipe.pos())

                                .hint(new DeclarationMessage.UpdateTheOutputOrHandleTheCase())
                                .say(new DeclarationMessage.TheDeclaredOutputIsNotWhatThePipelineProduces(pipe.name(), caseList(declared), caseList(inferred))).build());
            }
        }
        // The pipeline takes whatever its first stage takes (spec §sequential-composition), which arrived admitted with
        // that stage's own signature and is not asked again. What it answers is a type nobody wrote
        // — the last stage's answer, merged with the cases that left the main line — so that is
        // where the boundary is asked, once, about a composition.
        Sig first = sigs.get(composed.stages().get(0).behavior());
        return new Sig(first.ins(),
                SignatureBoundary.composedOutput(pipe.name(), pipe.pos(), out, symbols, kinds,
                        published));
    }

    /** Formats a set of case names as {@code A | B} (sorted, for a stable diagnostic). */
    static String caseList(Set<TypeSymbol> cases) {
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (TypeSymbol c : cases) {
            names.add(c.name());
        }
        return String.join(" | ", names);
    }

    /** The pipeline's output: what the last stage yields, plus everything that left the main line. */
    private static Type withRetired(Type mainline, Set<TypeSymbol> retired) {
        if (retired.isEmpty()) {
            return mainline;
        }
        Set<TypeSymbol> all = new LinkedHashSet<>(TypeOps.caseNamesOf(mainline));
        if (all.isEmpty()) {
            throw new IllegalStateException("cannot merge non-data stage output with retired cases");
        }
        all.addAll(retired);
        return TypeOps.caseSetType(all);
    }

    /** The main-line leaf cases {@code g} accepts — the ones the backend routes into it (spec §type-routing). */
    private static List<TypeSymbol> mainlineCases(Type mainline, Sig g, Symbols symbols,
                                                  PublishedDeclarations published) {
        List<TypeSymbol> accepted = new ArrayList<>();
        for (TypeSymbol caseName : AtomSpace.subjectAtoms(mainline, published)) {
            if (TypeOps.assignable(Type.ref(caseName), g.in(), published)) {
                accepted.add(caseName);
            }
        }
        return accepted;
    }

    /**
     * One step of type-routed composition (spec §type-routing). Returns the new main line — what {@code g}
     * yields — and adds the cases {@code g} did not accept to {@code retired}.
     *
     * <p>A case that leaves the main line does not come back: later stages are only offered the
     * main line. That is what makes this Railway (§type-routing). Feeding the retired cases onward instead
     * would let a stage pick up something an earlier stage had already dropped, which changes the
     * meaning of a pipeline depending on where it is split.
     *
     * <p>Naming an intermediate does not lose the split (spec §type-routing): a pipeline stage is flattened
     * into its own stages before routing ({@link #flattenStages}), so `fg >-> h` with
     * `fg = f >-> g` routes over `f, g, h` — a retired case stays retired, exactly as in the flat
     * `f >-> g >-> h`. That flattening is what makes `>->` associative; a value never carries a mark
     * saying it once left a main line (§unmarked-sum), the plumbing is structural. Viewed on its own, `fg`
     * still has the merged sum `f`+`g` produce as its output.
     */
    private static Type route(Type mainline, Sig g, Set<TypeSymbol> retired, Symbols symbols,
                              PublishedDeclarations published, SourcePos pos) {
        Type in = g.in();
        if (TypeOps.isDataLike(mainline)) {
            Set<TypeSymbol> consumed = new LinkedHashSet<>();
            Set<TypeSymbol> passed = new LinkedHashSet<>();
            // route over the leaf cases: a named sum output splits into its members, so a stage that
            // accepts one of them consumes it while the rest retire (spec §sum-data, §type-routing)
            for (TypeSymbol caseName : AtomSpace.subjectAtoms(mainline, published)) {
                if (TypeOps.assignable(Type.ref(caseName), in, published)) {
                    consumed.add(caseName);
                } else {
                    passed.add(caseName);
                }
            }
            if (consumed.isEmpty()) {
                throw CompileException.of(Diagnostic
                                .at(pos)
                                .diff(Type.show(mainline, in), Type.show(in, mainline))
                                .hint(new DeclarationMessage.MakeTheLeftOutputACaseTheRightAccepts())
                                .say(new DeclarationMessage.TheseBehaviorsCannotBeComposed()).build());
            }
            retired.addAll(passed);
            return g.outputType();
        }
        if (!mainline.equals(in)) {
            throw CompileException.of(Diagnostic
                            .at(pos)
                            .diff(Type.show(mainline, in), Type.show(in, mainline))
                            .hint(new DeclarationMessage.MakeTheLeftOutputACaseTheRightAccepts())
                            .say(new DeclarationMessage.TheseBehaviorsCannotBeComposed()).build());
        }
        return g.outputType();
    }
}
