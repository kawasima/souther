package souther.compiler.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.conformance.RepositoryModels;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.BindingOwner;
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
                                    instanceof BindingOwner.Expansion copy
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

    /**
     * What the emitted tree has between the copies both readings hold is one an operation brought.
     *
     * <p>What a reader wanting both readings of one position has to recognise, measured. A
     * comparison the analysis reads through one of the language's operations is under the copies the
     * caller made, and the emitted tree has the operation's own copies threaded between them — so
     * what a projection has to drop is a copy the operation brought, and the thing to know it by is
     * what a reader can see without looking anything up.
     *
     * <p>Nothing here is a projection. Where such a run begins, where it ends, and what the block it
     * ends at belongs to are the three the models are asked, and a projection would be written from
     * the answers rather than beside them.
     */
    @Test
    void everyCopyOnlyTheEmittedTreeHasIsOneAnOperationBrought() {
        Map<String, Integer> gapLengths = new TreeMap<>();
        Map<String, Integer> heads = new TreeMap<>();
        Map<String, Integer> tails = new TreeMap<>();
        Map<String, Integer> tailOwners = new TreeMap<>();
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
        // The lengths are the models' and not the rule's: a run is as long as the operation's own
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
        if (!(local.id().owner() instanceof BindingOwner.Expansion owner)) {
            return "owned by " + local.id().owner().getClass().getSimpleName();
        }
        return owner.expanded().equals(head.expanded())
                ? "the copy the run begins with"
                : "another copy: " + owner.expanded();
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

    /** The copies of {@code lineage}, outermost first. */
    private static List<ExpansionLineage.Expansion> stepsOf(ExpansionLineage lineage) {
        List<ExpansionLineage.Expansion> out = new ArrayList<>();
        for (ExpansionLineage each = lineage;
                each instanceof ExpansionLineage.Expansion step; each = step.within()) {
            out.add(0, step);
        }
        return out;
    }

    /**
     * Every body of every model this repository carries, plus the ones written above.
     *
     * <p>All of the models and not the corpus written against what the language declares. That one
     * reaches a construct about as often as it takes to declare it, and what a rule read through one
     * of the language's operations does under conditions nobody wrote it for is what the models
     * written to be worked with have. Neither is the other's fixture: the written ones above hold
     * shapes no corpus has, and no corpus is asked to grow one.
     */
    private static List<BothReadings> everyBodyBothWays() {
        List<BothReadings> out = new ArrayList<>(bodiesOfTheRepositorysModels());
        for (String written : List.of(COMBINATOR, SPLICED, BLOCKS, THROUGH_A_BLOCK)) {
            out.addAll(bodiesBothWays(List.of(List.of(written))));
        }
        return out;
    }

    private static List<BothReadings> bodiesOfTheRepositorysModels() {
        List<BothReadings> out = new ArrayList<>();
        for (Compilation compilation : RepositoryModels.all()) {
            int before = out.size();
            bothWaysOf(compilation, out);
            assertTrue(out.size() > before,
                    () -> "a model this repository carries has no body read both ways: "
                            + compilation.errors());
        }
        return out;
    }

    private static List<BothReadings> bodiesBothWays(List<List<String>> sources) {
        List<BothReadings> out = new ArrayList<>();
        for (List<String> each : sources) {
            Compilation compilation = Compilation.ofSources(each, ModulePath.EMPTY);
            compilation.answerEverything();
            int before = out.size();
            bothWaysOf(compilation, out);
            assertTrue(out.size() > before,
                    () -> "a source set compiled to no body read both ways: " + compilation.errors());
        }
        return out;
    }

    private static void bothWaysOf(Compilation compilation, List<BothReadings> out) {
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
