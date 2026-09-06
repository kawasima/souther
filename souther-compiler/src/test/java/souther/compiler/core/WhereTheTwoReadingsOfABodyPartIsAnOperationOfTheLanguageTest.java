package souther.compiler.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.conformance.ConformanceCorpus;
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
 * The two readings of a body hold the same comparisons down to the first operation of the language,
 * and part there.
 *
 * <p>What a reader wanting both readings of one position needs, measured rather than assumed. What
 * the backend emits has the language's own operations expanded into what they do and what the
 * analysis reads keeps them standing, so a comparison written in a block handed to one of them
 * stands in copies in the first tree and where it was written in the second. Whether that is the
 * whole of the difference — and whether the copies the difference is made of are the ones an
 * operation began — is what is asked here.
 *
 * <p>Read off {@link ValueName}, which already says which of the two a call reached: a module's own
 * helper is one thing and an operation the library publishes is another, and they are separate arms
 * rather than one arm to be told apart by the module it is spelled in. Nothing here asks the
 * inlining policy, which is why a tree has the expansions it has and not what any one of them is.
 */
@Tag("population")
class WhereTheTwoReadingsOfABodyPartIsAnOperationOfTheLanguageTest {

    /** A comparison written in a block handed to one of the language's own operations. */
    private static final String COMBINATOR = """
            module combinator

            behavior over : (xs: List<Int>) -> Bool
            let over (xs) = List.all(x -> x >= 240, xs)
            """;

    /**
     * A block the author bound and applied, which both readings expand.
     *
     * <p>The negative control for what a projection may drop. Applying a block is a copy like any
     * other, and the copy is named by the block — so a projection recognising the copies an
     * operation brought by their being blocks would drop this one, and a comparison the author wrote
     * inside it would come out standing where it was written. What tells the two apart is what the
     * block belongs to, and this one belongs to the body that bound it.
     */
    private static final String BLOCKS = """
            module blocks

            behavior over : (a: Int) -> Bool
            let over (a) = {
                let positive = y -> y > 0
                positive(a)
            }
            """;

    /** One module helper called twice, which both readings expand. */
    private static final String SPLICED = """
            module demo

            let picked (n: Int): Bool = n >= 240

            behavior over : (a: Int, b: Int) -> Bool
            let over (a, b) = picked(a) && picked(b)
            """;

    /** The comparisons of one behavior, in each reading of its body. */
    private record BothReadings(String behavior, Set<ConstructOccurrence> emitted,
                                Set<ConstructOccurrence> analysis) {}

    /**
     * The copies the emitted reading has and the analysis reading does not are the ones an operation
     * of the language began.
     *
     * <p>The classification, asked apart from what it is later used for. Where this holds, the two
     * readings differ by exactly the copies made inside the operations one of them keeps standing,
     * and a projection that stops at the first of those is describing the difference rather than
     * approximating it.
     */
    @Test
    void theCopiesOneReadingHasAndTheOtherDoesNotBeginAtAnOperation() {
        Map<String, Integer> byArm = new TreeMap<>();
        int[] dropped = new int[1];
        for (BothReadings both : everyBodyBothWays()) {
            for (ConstructOccurrence which : both.emitted()) {
                ConstructOccurrence stopped = new ConstructOccurrence(which.origin(),
                        upToTheFirstOperation(which.lineage()));
                if (stopped.equals(which)) {
                    // The two readings hold it alike: nothing was dropped, and there is no boundary
                    // to classify.
                    continue;
                }
                dropped[0]++;
                byArm.merge(armOf(stepsOf(which.lineage())
                        .get(stepsOf(stopped.lineage()).size()).expanded()), 1, Integer::sum);
            }
        }

        assertTrue(dropped[0] > 0,
                "no comparison stands inside a copy an operation began, so this says nothing");
        assertEquals(List.of("Stdlib.Operation"), List.copyOf(byArm.keySet()),
                () -> "the copies one reading has and the other does not begin at something other"
                        + " than an operation of the language: " + byArm);
    }

    /**
     * Every comparison the analysis reads is reached by exactly one of the emitted reading.
     *
     * <p>The direction a reader wants, and the cardinality of it. A rule is read where the
     * operations stand and a run through it is recorded where they are expanded, so what has to hold
     * is that each rule read there has one place to look for its run: none, and a rule the analysis
     * states could not be measured; two, and nothing says which of them a row was owed for.
     *
     * <p>Nothing is asked of the comparisons only the emitted reading holds. A comparison written in
     * an operation's own body is one the analysis never enters and states no rule about, and
     * counting it as a rule left unreached would be reporting the difference between the readings as
     * an omission.
     */
    @Test
    void everyComparisonTheAnalysisReadsIsReachedByOneOfTheOther() {
        List<String> unreached = new ArrayList<>();
        Map<String, List<String>> reachedTwice = new LinkedHashMap<>();
        int[] reached = new int[1];
        int[] onlyEmitted = new int[1];
        for (BothReadings both : everyBodyBothWays()) {
            Map<ConstructOccurrence, List<ConstructOccurrence>> arriving = new LinkedHashMap<>();
            for (ConstructOccurrence which : both.emitted()) {
                ConstructOccurrence stopped = new ConstructOccurrence(which.origin(),
                        upToTheFirstOperation(which.lineage()));
                if (both.analysis().contains(stopped)) {
                    arriving.computeIfAbsent(stopped, _ -> new ArrayList<>()).add(which);
                } else {
                    onlyEmitted[0]++;
                }
            }
            for (ConstructOccurrence read : both.analysis()) {
                List<ConstructOccurrence> from = arriving.get(read);
                if (from == null) {
                    unreached.add(both.behavior() + " " + read);
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
                () -> "a comparison the analysis reads is reached by none of the emitted reading,"
                        + " so a rule it states has no place a run through it is recorded");
        assertEquals(Map.of(), reachedTwice,
                () -> "two comparisons of the emitted reading reach one the analysis reads, over "
                        + reached[0] + " reached and " + onlyEmitted[0]
                        + " standing only where the operations are expanded");
    }

    /**
     * A block the author bound is a copy both readings make, and what it belongs to is the body that
     * bound it.
     *
     * <p>What says a projection dropping the copies an operation brought is not dropping blocks. The
     * copies it must drop are the ones an operation's own body re-enters, and those belong to the
     * copy of the operation; this one belongs to what the author wrote, and both readings hold it.
     */
    @Test
    void aBlockTheAuthorBoundIsACopyBothReadingsMake() {
        List<String> owners = new ArrayList<>();
        int[] readings = new int[1];
        for (BothReadings both : bodiesBothWays(List.of(List.of(BLOCKS)))) {
            for (Set<ConstructOccurrence> reading : List.of(both.emitted(), both.analysis())) {
                readings[0]++;
                for (ConstructOccurrence which : reading) {
                    for (ExpansionLineage.Expansion step : stepsOf(which.lineage())) {
                        if (step.expanded() instanceof ValueName.Local local) {
                            owners.add(local.id().owner()
                                    instanceof souther.compiler.types.BindingOwner.Expansion copy
                                    ? "a copy of " + copy.expanded()
                                    : local.id().owner().getClass().getSimpleName());
                        }
                    }
                }
            }
        }

        assertEquals(2, readings[0], "the body is read both ways");
        assertEquals(List.of("OfValue", "OfValue"), owners,
                () -> "the block the author bound belongs to the body that bound it, in both"
                        + " readings: " + owners);
    }

    /** The lineage with everything from the first operation of the language inward taken off. */
    private static ExpansionLineage upToTheFirstOperation(ExpansionLineage lineage) {
        ExpansionLineage out = ExpansionLineage.ORIGINAL;
        for (ExpansionLineage.Expansion step : stepsOf(lineage)) {
            // Stopped and not filtered: what is inside an operation's body is inside it, so the
            // copies below the one it began are copies the analysis never reads either.
            if (step.expanded() instanceof ValueName.Stdlib.Operation) {
                return out;
            }
            out = out.copiedInto(step.expanded(), step.at());
        }
        return out;
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
        List<List<String>> sources = new ArrayList<>();
        ConformanceCorpus.all().forEach(corpus -> sources.add(corpus.sources()));
        sources.add(List.of(COMBINATOR));
        sources.add(List.of(SPLICED));
        sources.add(List.of(BLOCKS));
        return bodiesBothWays(sources);
    }

    private static List<BothReadings> bodiesBothWays(List<List<String>> sources) {
        List<BothReadings> out = new ArrayList<>();
        for (List<String> each : sources) {
            Compilation compilation = Compilation.ofSources(each, ModulePath.EMPTY);
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
                    () -> "a source set compiled to no body read both ways: " + compilation.errors());
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
