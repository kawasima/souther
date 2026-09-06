package souther.bench;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ExpansionLineage;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
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
 * one comparison of the emitted tree behind it.
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
class EveryRuleTheAnalysisReadsHasOnePlaceItsRunIsRecordedTest {

    /** The comparisons of one behavior, in each reading of its body. */
    private record BothReadings(String behavior, Set<ConstructOccurrence> emitted,
                                Set<ConstructOccurrence> analysis) {}

    /**
     * Each comparison the analysis reads is reached by one of the emitted tree, none by two, and
     * none by none.
     */
    @Test
    void eachComparisonTheAnalysisReadsIsReachedByOneOfTheEmittedTree() {
        List<String> unreached = new ArrayList<>();
        Map<String, List<String>> reachedTwice = new LinkedHashMap<>();
        int[] reached = new int[1];
        int[] onlyEmitted = new int[1];
        for (BothReadings both : everyBodyBothWays()) {
            Map<ConstructOccurrence, List<ConstructOccurrence>> arriving = new LinkedHashMap<>();
            for (ConstructOccurrence which : both.emitted()) {
                ConstructOccurrence stopped = upToTheFirstOperation(which);
                if (both.analysis().contains(stopped)) {
                    arriving.computeIfAbsent(stopped, _ -> new ArrayList<>()).add(which);
                } else {
                    onlyEmitted[0]++;
                }
            }
            for (ConstructOccurrence read : both.analysis()) {
                List<ConstructOccurrence> from = arriving.get(read);
                if (from == null) {
                    unreached.add(both.behavior() + "\n  analysis: " + read + "\n  emitted:  "
                            + both.emitted().stream()
                                    .filter(each -> each.origin().equals(read.origin()))
                                    .map(String::valueOf).toList());
                } else if (from.size() > 1) {
                    reachedTwice.put(both.behavior() + " " + read,
                            from.stream().map(String::valueOf).toList());
                } else {
                    reached[0]++;
                }
            }
        }

        assertTrue(reached[0] > 0, "no comparison of the analysis reading was reached at all");
        assertEquals(List.of(), unreached,
                () -> "a comparison the analysis reads is reached by none of the emitted tree, so a"
                        + " rule it states has no place a run through it is recorded. Reached: "
                        + reached[0]);
        assertEquals(Map.of(), reachedTwice,
                () -> "two comparisons of the emitted tree reach one the analysis reads, over "
                        + reached[0] + " reached and " + onlyEmitted[0]
                        + " standing only where the operations are expanded");
    }

    /** And the copies the emitted tree has and the analysis does not are the ones an operation of
     *  the language began. */
    @Test
    void theCopiesOnlyTheEmittedTreeHasBeginAtAnOperation() {
        Map<String, Integer> byArm = new TreeMap<>();
        for (BothReadings both : everyBodyBothWays()) {
            for (ConstructOccurrence which : both.emitted()) {
                ConstructOccurrence stopped = upToTheFirstOperation(which);
                if (stopped.equals(which)) {
                    continue;
                }
                byArm.merge(armOf(stepsOf(which.lineage())
                        .get(stepsOf(stopped.lineage()).size()).expanded()), 1, Integer::sum);
            }
        }

        assertTrue(!byArm.isEmpty(),
                "no comparison of these models stands inside a copy an operation began, so this"
                        + " says nothing about where the two readings part");
        assertEquals(List.of("Stdlib.Operation"), List.copyOf(byArm.keySet()),
                () -> "the copies one reading has and the other does not begin at something other"
                        + " than an operation of the language: " + byArm);
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
        assertEquals(Map.of(), Map.of("gapLengths", gapLengths, "heads", heads, "tails", tails,
                        "tailOwners", tailOwners, "keptLocals", keptLocals),
                () -> "aligned " + aligned[0] + ", gaps longer than a pair: " + longGaps);
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
                ? "the copy the gap begins with, parameter " + local.id().ordinal()
                : "another copy: " + owner.expanded();
    }

    private static ConstructOccurrence upToTheFirstOperation(ConstructOccurrence which) {
        ExpansionLineage out = ExpansionLineage.ORIGINAL;
        for (ExpansionLineage.Expansion step : stepsOf(which.lineage())) {
            if (step.expanded() instanceof ValueName.Stdlib.Operation) {
                return new ConstructOccurrence(which.origin(), out);
            }
            out = out.copiedInto(step.expanded(), step.at());
        }
        return new ConstructOccurrence(which.origin(), out);
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
