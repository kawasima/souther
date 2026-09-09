package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a run reads its values from, and what may be assumed of every value it walks.
 *
 * <p>A number taken over a run is answered by no single position, so where its values run is not a
 * position's answer either. What is left is to say it of the path the run reads from — and a path
 * that descends through a sum stands at no position at all. One stands under each case, and the
 * cases disagree.
 *
 * <p>So the reach an occurrence may be assumed to lie in is the join over the cases the path stands
 * under, and it is that whichever case a particular occurrence turned out to be: a run walks every
 * occurrence and no case is fixed for the walk. Taken from one of them — the first the reading
 * found, or the one a context happened to have opened — a total would be held against numbers half
 * the rows it is measured over never reach.
 *
 * <p><b>The join is the ceiling on what may be assumed, and is not what anybody computes.</b>
 * {@code RunReach} asks what a value of the type standing at the run's path guarantees of itself,
 * which says nothing at all where the bound was written on the cases — so the reading actually taken
 * lies inside the join and is blunter than it. What is fixed here is the ceiling: a reading that
 * answered anything outside it would be claiming of every occurrence something half of them do not
 * satisfy, and either case taken alone is exactly that.
 */
class APathDescendingThroughASumStandsAtNoPositionTest {

    /**
     * A number every case of a sum spreads, bounded differently by each.
     *
     * <p>The two cases disagree on both ends, so no case's own answer is the join and neither is the
     * type's: reading either one alone is visible here as a bound the other case's values are
     * outside of.
     */
    private static final String CASES = """
            module g

            data Amount = Int
                invariant value >= 0

            data Common = { amount: Amount }

            data Small =
                { ...Common }
                invariant small = amount.value <= 10

            data Large =
                { ...Common }
                invariant large = amount.value >= 100

            data Kind = Small | Large

            data Item = { kind: Kind }
            data Holder = { items: List<Item> }

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /**
     * The path a run over these amounts is read from stands at no position.
     *
     * <p>This is what refuses reading a run's element bounds off the position at its path. The
     * reading answers about {@code kind@Small.amount} and about {@code kind@Large.amount}, and a
     * lookup by the path the run carries finds neither.
     */
    @Test
    void aPathDescendingThroughASumStandsAtNoPosition() {
        InputDomain read = reading(CASES, "read");

        assertNull(positionAt(read, "h.items[*].kind.amount"),
                () -> "the run reads from a path under a sum, and the positions are "
                        + read.positions().stream().map(Position::path).toList());
    }

    /** And one stands under each case, which is where the numbers are. */
    @Test
    void oneStandsUnderEachCaseOfTheSum() {
        InputDomain read = reading(CASES, "read");

        assertNotNull(positionAt(read, "h.items[*].kind@Small.amount"));
        assertNotNull(positionAt(read, "h.items[*].kind@Large.amount"));
    }

    /**
     * The cases disagree, so no one case answers for an occurrence.
     *
     * <p>Written out as the two ranges rather than as a difference between them: what makes either
     * of them the wrong answer is that it excludes values the other case's occurrences hold.
     */
    @Test
    void theCasesDisagreeAboutWhereAnOccurrenceRuns() {
        InputDomain read = reading(CASES, "read");
        NumericDomain.Bounds small = reachAt(read, "h.items[*].kind@Small.amount");
        NumericDomain.Bounds large = reachAt(read, "h.items[*].kind@Large.amount");

        assertEquals("0 to 10", show(small));
        assertEquals("100 to unbounded", show(large));
    }

    /**
     * What may be assumed of every occurrence is the two joined, which is what a walk over them is
     * entitled to.
     *
     * <p>The join is written here as the rule and not as its answer for this model, so a case whose
     * bound moves moves this with it. What is fixed is that the answer holds every value either case
     * holds — an occurrence is one of them and the run does not say which.
     */
    @Test
    void whatEveryOccurrenceLiesInIsTheTwoJoined() {
        InputDomain read = reading(CASES, "read");
        NumericDomain.Bounds small = reachAt(read, "h.items[*].kind@Small.amount");
        NumericDomain.Bounds large = reachAt(read, "h.items[*].kind@Large.amount");

        NumericDomain.Bounds join = NumericDomain.Bounds.spanning(small, large);

        assertTrue(small.liesWithin(join), "an occurrence that is a `Small` lies in it");
        assertTrue(large.liesWithin(join), "and so does one that is a `Large`");
        assertEquals("0 to unbounded", show(join),
                "the amounts are at or above nought and nothing bounds them above");
    }

    /** A range as two ends, with an end nothing wrote said as the word for it. */
    private static String show(NumericDomain.Bounds bounds) {
        return (bounds.min() == null ? "unbounded" : bounds.min().at())
                + " to " + (bounds.max() == null ? "unbounded" : bounds.max().at());
    }

    private static NumericDomain.Bounds reachAt(InputDomain read, String spelled) {
        Position at = positionAt(read, spelled);
        assertNotNull(at, () -> "no position at " + spelled);
        NumericDomain.Bounds runs =
                at.boundsFor(new NumericTerm.ValueOf(at.path())).admissible();
        assertNotNull(runs, () -> spelled + " is a number this reading bounds");
        return runs;
    }

    private static Position positionAt(InputDomain read, String spelled) {
        return read.positions().stream()
                .filter(each -> each.path().toString().equals(spelled))
                .findFirst().orElse(null);
    }

    private static InputDomain reading(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), rules, ReadAs.THE_COMPILATION_DOES);
    }
}
