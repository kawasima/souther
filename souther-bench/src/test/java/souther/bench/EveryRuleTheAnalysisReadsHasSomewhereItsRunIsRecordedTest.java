package souther.bench;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.coverage.ComparisonEmissionSite;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ExpansionLineage;
import souther.compiler.types.ModelOccurrence;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Over the models this repository carries for measurement: every comparison the analysis reads has
 * somewhere the tree that runs writes it.
 *
 * <p>The same question the compiler's own corpus is asked, over the models written to be big. That
 * corpus is written against what the language declares and reaches each construct once or twice;
 * these are written as somebody would write them, and what they say about a rule read through one of
 * the language's operations is a count rather than an example.
 *
 * <p>Why it is here and not beside the compiler: these models live in this module, and a test of the
 * compiler cannot read them. What it measures belongs to the compiler all the same, so the
 * projection is written out here a second time — which is a thing to remove once the compiler owns
 * it, and a thing to notice if the two ever answer differently.
 */
@Tag("population")
class EveryRuleTheAnalysisReadsHasSomewhereItsRunIsRecordedTest {

    /** The comparisons of one behavior, in each reading of its body. */
    private record BothReadings(String behavior, Set<ConstructOccurrence> emitted,
                                Set<ConstructOccurrence> analysis) {}

    /**
     * Each comparison the analysis reads is reached by at least one of the emitted tree.
     *
     * <p>How many is not asked. A library operation may evaluate a closure it was handed more than
     * once, so a comparison the author wrote once is written into the tree that runs more than once
     * — one rule, several places it is watched at. This asked for exactly one, over models that
     * called no such operation, and the first model that did stopped the compile.
     */
    @Test
    void eachComparisonTheAnalysisReadsIsReachedByOneOfTheEmittedTree() {
        List<String> unreached = new ArrayList<>();
        int[] reached = new int[1];
        int[] onlyEmitted = new int[1];
        for (BothReadings both : everyBodyBothWays()) {
            Map<ModelOccurrence, List<ConstructOccurrence>> arriving = new LinkedHashMap<>();
            Set<ModelOccurrence> stated = new LinkedHashSet<>();
            both.analysis().forEach(each -> ModelOccurrence.statedAt(each).ifPresent(stated::add));
            for (ConstructOccurrence which : both.emitted()) {
                ModelOccurrence states = ModelOccurrence.statedAt(which).orElse(null);
                if (states != null && stated.contains(states)) {
                    arriving.computeIfAbsent(states, _ -> new ArrayList<>()).add(which);
                } else {
                    onlyEmitted[0]++;
                }
            }
            for (ModelOccurrence read : stated) {
                if (arriving.containsKey(read)) {
                    reached[0]++;
                } else {
                    unreached.add(both.behavior() + "\n  analysis: " + read + "\n  emitted:  "
                            + both.emitted().stream()
                                    .filter(each -> each.origin().equals(read.origin()))
                                    .map(String::valueOf).toList());
                }
            }
        }

        assertTrue(reached[0] > 0, "no comparison of the analysis reading was reached at all");
        assertEquals(List.of(), unreached,
                () -> "a comparison the analysis reads is reached by none of the emitted tree, so a"
                        + " rule it states has no place a run through it is recorded. Reached: "
                        + reached[0]);
    }

    /**
     * Each construct of the model the analysis reads has somewhere a run through it is recorded.
     *
     * <p>The whole crossing, measured end to end: what the analysis states, through the comparisons
     * the backend emits, to the numbers a probe writes. Two places and nothing says which of them a
     * row was owed for; none and a rule the model states could not be measured at all.
     *
     * <p>The addresses are read through the numbering as it stands, which is keyed by the walk of
     * the emitted bodies. That is the arrangement being measured against, not the one being checked:
     * nothing here concludes that two constructs are one because the walk gave them one number. What
     * says they are one is the model occurrence, and the number is only how a run is found.
     */
    @Test
    void eachConstructTheModelStatesHasOnePlaceItsRunIsRecorded() {
        Map<String, Integer> sitesPerModel = new TreeMap<>();
        Map<String, Integer> sitesPerEmitted = new TreeMap<>();
        List<String> statedButUnplaced = new ArrayList<>();
        int[] onlyEmitted = new int[1];
        for (Corpus corpus : Corpus.all()) {
            Compilation compilation = Compilation.ofSources(corpus.sources(), ModulePath.EMPTY);
            compilation.answerEverything();
            for (String module : compilation.modules()) {
                Bodies.Elaborated checked =
                        compilation.db().ask(new Bodies.Checked(module)).value();
                if (checked == null) {
                    continue;
                }
                CoverageSites.Plan plan = checked.plan();
                for (Map.Entry<String, Core> body : checked.behaviorBodies().entrySet()) {
                    var read = checked.analysisBodies().get(body.getKey());
                    if (read == null) {
                        continue;
                    }
                    Set<ModelOccurrence> stated = new LinkedHashSet<>();
                    comparisonsIn(read.core()).forEach(each ->
                            ModelOccurrence.statedAt(each).ifPresent(stated::add));

                    Map<ModelOccurrence, Set<ComparisonEmissionSite>> placed =
                            new LinkedHashMap<>();
                    Map<ModelOccurrence, Set<ConstructOccurrence>> uninstrumented =
                            new LinkedHashMap<>();
                    for (Core.Binary node : comparisonNodesIn(body.getValue())) {
                        ModelOccurrence states =
                                ModelOccurrence.statedAt(node.occurrence()).orElse(null);
                        if (states == null || !stated.contains(states)) {
                            onlyEmitted[0]++;
                            continue;
                        }
                        var site = plan.comparisons().at(node)
                                .flatMap(one -> plan.emissionSiteOf(one.which()));
                        // A comparison behind an abort is one no run reaches, so the plan numbers
                        // none — which is a fact about what is measured and not about the join.
                        if (site.isEmpty()) {
                            uninstrumented.computeIfAbsent(states, _ -> new LinkedHashSet<>())
                                    .add(node.occurrence());
                        } else {
                            placed.computeIfAbsent(states, _ -> new LinkedHashSet<>())
                                    .add(site.get());
                        }
                        sitesPerEmitted.merge(site.isEmpty() ? "0" : "1", 1, Integer::sum);
                    }

                    for (ModelOccurrence states : stated) {
                        Set<ComparisonEmissionSite> sites =
                                placed.getOrDefault(states, Set.of());
                        sitesPerModel.merge(String.valueOf(sites.size()), 1, Integer::sum);
                        if (sites.isEmpty() && !uninstrumented.containsKey(states)) {
                            statedButUnplaced.add(module + "." + body.getKey() + " " + states);
                        }
                    }
                }
            }
        }

        assertTrue(!sitesPerModel.isEmpty(), "nothing the model states was met at all");
        assertEquals(List.of(), statedButUnplaced,
                () -> "the model states a construct that reaches no comparison of the emitted tree"
                        + " at all: " + sitesPerModel);
        // Nothing is said about how many places a construct is watched at. A library operation
        // evaluating a closure it was handed twice writes the comparison twice, so one rule may be
        // watched in more than one place — and that this really happens is held where a model is
        // written for it rather than over whatever this corpus turns out to call.
        assertEquals(List.of("0", "1"), List.copyOf(sitesPerEmitted.keySet()),
                () -> "a comparison of the emitted tree has more than one address: "
                        + sitesPerEmitted);
    }

    /**
     * Every place {@code body} writes a comparison, one per node.
     *
     * <p>Held by the node and never by the occurrence it carries. What is being measured here is
     * whether each construct of the model has somewhere a run through it is recorded, and gathering
     * the places under the name each carries would answer that with a population the names had
     * already thinned — two places whose occurrences agreed would arrive as one, and the count would
     * come back right because one of them was never looked at.
     *
     * <p>By identity, because a node this walk arrives at twice is one place written once.
     */
    private static List<Core.Binary> comparisonNodesIn(Core body) {
        Map<Core, Boolean> met = new IdentityHashMap<>();
        List<Core.Binary> out = new ArrayList<>();
        nodes(body, met, out);
        return out;
    }

    private static void nodes(Core e, Map<Core, Boolean> met, List<Core.Binary> out) {
        if (e instanceof Core.Binary binary && binary.origin() != null
                && binary.origin().isWritten() && met.put(binary, Boolean.TRUE) == null) {
            out.add(binary);
        }
        Core.forEachChild(e, child -> nodes(child, met, out));
    }

    /**
     * What the emitted tree has between the copies both readings hold, said as what a step is a copy
     * of rather than as where it is written.
     *
     * <p>The question this is here to answer. A comparison the analysis reads through one of the
     * language's operations is under the copies the caller made, and the emitted tree has the
     * operation's own copies threaded between them — so what a projection has to recognise is a
     * copy the operation brought, and the thing to recognise it by is what a reader can see without
     * looking anything up.
     */
    @Test
    void everyCopyOnlyTheEmittedTreeHasIsOneAnOperationBrought() {
        Map<String, Integer> gapLengths = new TreeMap<>();
        Map<String, Integer> heads = new TreeMap<>();
        Map<String, Integer> tails = new TreeMap<>();
        Map<String, Integer> tailOwners = new TreeMap<>();
        Map<String, Integer> keptLocals = new TreeMap<>();
        int[] aligned = new int[1];
        List<String> notASubsequence = new ArrayList<>();
        Set<String> longGaps = new LinkedHashSet<>();

        for (BothReadings both : everyBodyBothWays()) {
            for (ConstructOccurrence read : both.analysis()) {
                List<ConstructOccurrence> matching = both.emitted().stream()
                        .filter(each -> each.origin().equals(read.origin()))
                        .filter(each -> gapsBetween(stepsOf(read.lineage()),
                                stepsOf(each.lineage())) != null)
                        .toList();
                if (matching.size() != 1) {
                    if (matching.isEmpty()) {
                        notASubsequence.add(both.behavior() + " " + read);
                    }
                    continue;
                }
                aligned[0]++;
                List<List<ExpansionLineage.Expansion>> gaps =
                        gapsBetween(stepsOf(read.lineage()),
                                stepsOf(matching.get(0).lineage()));
                for (List<ExpansionLineage.Expansion> gap : gaps) {
                    gapLengths.merge(String.valueOf(gap.size()), 1, Integer::sum);
                    if (gap.size() > 2) {
                        longGaps.add(gap.stream()
                                .map(step -> armOf(step.expanded()) + " " + step.expanded()
                                        + " @ " + step.at())
                                .toList().toString());
                    }
                    heads.merge(armOf(gap.get(0).expanded()), 1, Integer::sum);
                    ExpansionLineage.Expansion tail = gap.get(gap.size() - 1);
                    tails.merge(armOf(tail.expanded()), 1, Integer::sum);
                    tailOwners.merge(ownerOf(tail, gap.get(0)), 1, Integer::sum);
                }
                // And the copies both readings hold: where one of them is a block, what it belongs
                // to is what says it is not one an operation brought.
                for (ExpansionLineage.Expansion kept : stepsOf(read.lineage())) {
                    if (kept.expanded() instanceof ValueName.Local local) {
                        keptLocals.merge(local.id().owner().getClass().getSimpleName(), 1,
                                Integer::sum);
                    }
                }
            }
        }

        assertTrue(aligned[0] > 0, "nothing was aligned at all, so this says nothing");
        assertEquals(List.of(), notASubsequence,
                () -> "what the analysis reads is not what the emitted tree reads with copies"
                        + " inserted, so the two are not one reading with an envelope in it");
        assertTrue(!heads.isEmpty(),
                "the two readings held every comparison alike, so nothing here says what parts them");
        assertEquals(List.of("Stdlib.Operation"), List.copyOf(heads.keySet()),
                () -> "a run of copies only the emitted tree has begins at something other than an"
                        + " operation of the language: " + heads);
        assertEquals(List.of("Local"), List.copyOf(tails.keySet()),
                () -> "such a run ends at something other than a block: " + tails);
        assertEquals(List.of("the copy the run begins with"), List.copyOf(tailOwners.keySet()),
                () -> "the block such a run ends at belongs to something other than the copy the"
                        + " run begins with: " + tailOwners);
        // The lengths are the corpora's and not the rule's: a run is as long as the operation's own
        // body is deep, and pinning it would make this a test of the models. What it must not become
        // is a run of one, because then the block that closes it is the operation itself and there
        // is nothing here about where a run ends.
        assertTrue(gapLengths.keySet().stream().anyMatch(each -> Integer.parseInt(each) > 2),
                () -> "no operation's copies nest, so nothing here says a run ends at the block the"
                        + " caller handed the outermost of them: " + gapLengths + " " + longGaps);
    }

    /**
     * Where {@code inside} sits in {@code outside} as a subsequence, as the runs of steps between
     * the ones they share — or null where it does not sit in it at all.
     *
     * <p>Matched on what a step is on its own — what was expanded and where — and not on the step as
     * a value, which carries the chain above it and so is equal only where the whole chain is. The
     * chains are what differ; that is the thing being measured. Matched on the callee alone, a body
     * calling one helper twice would let the walk take either, and the runs between would be
     * whatever that choice left.
     */
    private static List<List<ExpansionLineage.Expansion>> gapsBetween(
            List<ExpansionLineage.Expansion> inside, List<ExpansionLineage.Expansion> outside) {
        List<List<ExpansionLineage.Expansion>> gaps = new ArrayList<>();
        List<ExpansionLineage.Expansion> gap = new ArrayList<>();
        int at = 0;
        for (ExpansionLineage.Expansion step : outside) {
            if (at < inside.size() && sameStep(step, inside.get(at))) {
                if (!gap.isEmpty()) {
                    gaps.add(List.copyOf(gap));
                    gap.clear();
                }
                at++;
            } else {
                gap.add(step);
            }
        }
        if (!gap.isEmpty()) {
            gaps.add(List.copyOf(gap));
        }
        return at == inside.size() ? gaps : null;
    }

    /** Whether two steps are the same copy of the same thing at the same call, chains aside. */
    private static boolean sameStep(ExpansionLineage.Expansion one,
                                    ExpansionLineage.Expansion other) {
        return one.expanded().equals(other.expanded()) && one.at().equals(other.at());
    }

    /** What the block at the end of a gap belongs to, said against the copy the gap begins with. */
    private static String ownerOf(ExpansionLineage.Expansion tail,
                                  ExpansionLineage.Expansion head) {
        if (!(tail.expanded() instanceof ValueName.Local local)) {
            return "not a block: " + armOf(tail.expanded());
        }
        if (!(local.id().owner() instanceof souther.compiler.types.BindingOwner.Expansion owner)) {
            return "owned by " + local.id().owner().getClass().getSimpleName();
        }
        return owner.expanded().equals(head.expanded())
                ? "the copy the run begins with"
                : "another copy: " + owner.expanded();
    }

    /** The copies of {@code lineage}, outermost first. */
    private static List<ExpansionLineage.Expansion> stepsOf(ExpansionLineage lineage) {
        List<ExpansionLineage.Expansion> out = new ArrayList<>();
        for (ExpansionLineage each = lineage;
                each instanceof ExpansionLineage.Expansion step; each = step.within()) {
            out.add(0, step);
        }
        return out;
    }

    private static String armOf(ValueName expanded) {
        return switch (expanded) {
            case ValueName.Stdlib.Operation _ -> "Stdlib.Operation";
            case ValueName.Stdlib.Namespace _ -> "Stdlib.Namespace";
            case ValueName.Helper _ -> "Helper";
            case ValueName.Behavior _ -> "Behavior";
            case ValueName.Local _ -> "Local";
            case ValueName.OfType _ -> "OfType";
            case ValueName.Builtin _ -> "Builtin";
        };
    }

    private static List<BothReadings> everyBodyBothWays() {
        List<BothReadings> out = new ArrayList<>();
        for (Corpus corpus : Corpus.all()) {
            Compilation compilation = Compilation.ofSources(corpus.sources(), ModulePath.EMPTY);
            compilation.answerEverything();
            int before = out.size();
            for (String module : compilation.modules()) {
                Bodies.Elaborated checked =
                        compilation.db().ask(new Bodies.Checked(module)).value();
                if (checked == null) {
                    continue;
                }
                checked.behaviorBodies().forEach((behavior, emitted) -> {
                    var read = checked.analysisBodies().get(behavior);
                    if (read != null) {
                        out.add(new BothReadings(module + "." + behavior,
                                comparisonsIn(emitted), comparisonsIn(read.core())));
                    }
                });
            }
            assertTrue(out.size() > before,
                    () -> "`" + corpus.name() + "` compiled to no body read both ways: "
                            + compilation.errors());
        }
        return out;
    }

    private static Set<ConstructOccurrence> comparisonsIn(Core body) {
        Set<ConstructOccurrence> out = new LinkedHashSet<>();
        walk(body, out);
        return out;
    }

    private static void walk(Core e, Set<ConstructOccurrence> out) {
        if (e instanceof Core.Binary binary && binary.origin() != null
                && binary.origin().isWritten()) {
            out.add(binary.occurrence());
        }
        Core.forEachChild(e, child -> walk(child, out));
    }
}
