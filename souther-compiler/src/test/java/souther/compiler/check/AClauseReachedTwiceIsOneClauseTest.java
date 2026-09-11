package souther.compiler.check;

import souther.compiler.WhereItSits;
import souther.compiler.Compiler;
import souther.compiler.check.InvariantChecker.Judgment;
import souther.compiler.check.InvariantChecker.Said;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What makes two clauses two, and one clause reached twice one.
 *
 * <p>A clause is identified by the declaration that wrote it and its place among that declaration's
 * own clauses. Not by its name, which a clause need not have; not by where it is written, which this
 * compile need not be able to quote; and not by the walk that reached it, which reaches one clause
 * once per branch above the construction.
 *
 * <p>The first of those is what the sets a judgment carries used to get wrong in the other
 * direction: they held names, so two clauses an author wrote without names were nothing at all
 * rather than two entries. Nothing downstream could tell that from one clause, or from none.
 */
class AClauseReachedTwiceIsOneClauseTest {

    /** Two clauses, neither named, both left standing. */
    private static final String TWO_UNNAMED = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant low <= high
                invariant high <= 100

            behavior widen : (low: Int, high: Int) -> Bound
                constructs Bound

            let widen (low, high) = Bound { low = low, high = high }
            """;

    /** One clause, and a construction the walk reads once down each branch above it. */
    private static final String READ_ON_TWO_BRANCHES = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant low <= high

            behavior widen : (low: Int, high: Int, wide: Bool) -> Bound
                constructs Bound

            let widen (low, high, wide) =
                if wide
                    then Bound { low = low, high = high }
                    else Bound { low = low, high = high }
            """;

    // --- two clauses are two --------------------------------------------------------------------

    @Test
    void twoClausesWrittenWithoutNamesAreTwoClauses() {
        Judgment judgment = judgmentOn(TWO_UNNAMED);

        assertEquals(2, judgment.unsettled().size(),
                "the author wrote two, and neither having a name does not make them one: "
                        + judgment.unsettled());
        assertTrue(judgment.unsettled().values().stream().allMatch(c -> c.name().isEmpty()),
                "neither was named");
    }

    @Test
    void theTwoAreToldApartByWhereTheyStandInTheirDeclaration() {
        List<Clause.Id> ids = List.copyOf(judgmentOn(TWO_UNNAMED).unsettled().keySet());

        assertEquals(2, ids.size());
        assertEquals(ids.get(0).declaredOn(), ids.get(1).declaredOn(), "one declaration wrote both");
        assertNotEquals(ids.get(0).ordinal(), ids.get(1).ordinal(),
                "and they are told apart by which of its clauses each is");
        assertEquals(List.of(0, 1), ids.stream().map(Clause.Id::ordinal).toList(),
                "in the order they were written");
    }

    /** Each of them is a place of its own to send a reader to. Read off what the report points at,
     *  which is where a place is asked for — the judgment says which clauses it is about, and the
     *  declaration says where each of them is written. */
    @Test
    void eachOfTheTwoIsWrittenSomewhereOfItsOwn() {
        assertEquals(List.of(7, 8), linesPointedAtIn(TWO_UNNAMED),
                "the two `invariant` lines the declaration writes");
    }

    // --- one clause reached twice is one --------------------------------------------------------

    @Test
    void aClauseReadOnBothBranchesIsHeldOnce() {
        Judgment judgment = judgmentOn(READ_ON_TWO_BRANCHES);

        assertEquals(1, judgment.unsettled().size(),
                "one clause, read once down each branch: " + judgment.unsettled());
        assertEquals(Set.of(7), Set.copyOf(linesPointedAtIn(READ_ON_TWO_BRANCHES)),
                "and however many constructions are reported, there is one line to send a reader"
                        + " to, because there is one clause");
    }

    // --- reading the check ----------------------------------------------------------------------

    /** The lines the warning about {@code Bound}'s construction sends a reader to, in the order it
     *  writes them. */
    private static List<Integer> linesPointedAtIn(String source) {
        return Compiler.compileWithWarnings(source).warnings().stream()
                .filter(d -> "E2011".equals(d.code()))
                .flatMap(d -> d.secondary().stream())
                .map(label -> label.place())
                .filter(place -> place instanceof souther.compiler.diag.DiagnosticPlace.InSource)
                .map(place -> WhereItSits.in(source,
                        ((souther.compiler.diag.DiagnosticPlace.InSource) place).region())
                        .start().line())
                .toList();
    }

    /**
     * The judgment for the construction of {@code Bound}, taken through the seam the check reports
     * through. A construction read on two branches is reported once, so what comes back here is
     * already the readings combined.
     */
    private static Judgment judgmentOn(String source) {
        List<Said> said = Collections.synchronizedList(new ArrayList<>());
        InvariantChecker.WATCHING = said;
        try {
            Compiler.compileWithWarnings(source);
        } finally {
            InvariantChecker.WATCHING = null;
        }
        assertFalse(said.isEmpty(), "nothing was checked, so nothing here is being held to anything");
        return said.stream().filter(s -> s.type().equals("Bound")).findFirst().orElseThrow()
                .judgment();
    }
}
