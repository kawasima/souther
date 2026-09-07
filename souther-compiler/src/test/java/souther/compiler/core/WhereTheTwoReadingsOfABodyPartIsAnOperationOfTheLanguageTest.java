package souther.compiler.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ExpansionLineage;
import souther.compiler.types.ModelOccurrence;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * A helper called from inside the block handed to an operation.
     *
     * <p>What says an envelope is left rather than run to the end of the lineage. The comparison
     * stands in a copy the caller made — {@code over240} is the module's own helper — and that copy
     * is made after the operation's body has reached the block it was handed. A projection that
     * never left the envelope would drop it, and the comparison would come out standing where it was
     * written while the reading that keeps the operation standing has it inside the helper.
     */
    private static final String THROUGH_A_BLOCK = """
            module through

            let over240 (n: Int): Bool = n >= 240

            behavior over : (xs: List<Int>) -> Bool
            let over (xs) = List.all(x -> over240(x), xs)
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
     * Every comparison the analysis reads is reached by at least one of the emitted reading.
     *
     * <p>The direction a reader wants. A rule is read where the operations stand and a run through
     * it is recorded where they are expanded, so what has to hold is that each rule read there has
     * somewhere to look for its run: none, and a rule the analysis states could not be measured at
     * all.
     *
     * <p><b>And more than one is allowed.</b> A library operation may evaluate a closure it was
     * handed more than once, so a comparison the author wrote once is written into the tree that
     * runs more than once — one rule, several places it is watched at. This was written as exactly
     * one, over a corpus that called no such operation, and the day a model called one the compile
     * stopped.
     *
     * <p>That more than one really happens is not asked here. This walks whatever the corpus holds,
     * and a property that needed a shape to be in it would be one the corpus decides; the shape is
     * held to where a model is written for it
     * ({@code AComparisonWrittenOnceIsWatchedWhereverTheOperationEvaluatesItTest}).
     *
     * <p>Nothing is asked of the comparisons only the emitted reading holds. A comparison written in
     * an operation's own body is one the analysis never enters and states no rule about, and
     * counting it as a rule left unreached would be reporting the difference between the readings as
     * an omission.
     */
    @Test
    void everyComparisonTheAnalysisReadsIsReachedByOneOfTheOther() {
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
                    unreached.add(both.behavior() + " " + read);
                }
            }
        }

        assertTrue(reached[0] > 0, "no comparison of the analysis reading was reached at all");
        assertEquals(List.of(), unreached,
                () -> "a comparison the analysis reads is reached by none of the emitted reading,"
                        + " so a rule it states has no place a run through it is recorded, over "
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

    /** The copies of {@code lineage}, outermost first. */
    private static List<ExpansionLineage.Expansion> stepsOf(ExpansionLineage lineage) {
        List<ExpansionLineage.Expansion> out = new ArrayList<>();
        for (ExpansionLineage each = lineage;
                each instanceof ExpansionLineage.Expansion step; each = step.within()) {
            out.add(0, step);
        }
        return out;
    }

    private static List<BothReadings> everyBodyBothWays() {
        List<List<String>> sources = new ArrayList<>();
        ConformanceCorpus.all().forEach(corpus -> sources.add(corpus.sources()));
        sources.add(List.of(COMBINATOR));
        sources.add(List.of(SPLICED));
        sources.add(List.of(BLOCKS));
        sources.add(List.of(THROUGH_A_BLOCK));
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
