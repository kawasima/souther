package souther.compiler.query;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A point a declaration owes is written from the declaration, and two readings naming their
 * positions differently is an ordinary thing for a model to do.
 *
 * <p>A line an {@code invariant} drew is owed once for the module and read at every position the
 * type reaches. Where the type is a case of a sum, one behavior takes the case and another takes
 * the sum, so the same line is met at {@code x.leaveFrom} and at {@code x@Onl.leaveFrom} — two
 * paths for one place, which is what a sum is.
 *
 * <p>The sentence for the point used to be settled by writing it at every reading and refusing
 * where two of them differed. They differ exactly where the answer is a reading's: a line between
 * two positions writes its level as a distance from the other position, whose name is the path a
 * walk reached it by. So the refusal fired on a model that is written correctly, and what it
 * refused to write was a sentence nothing could have written — the declaration keeps no name for
 * the pair, because a rule relating two positions places no end (ADR-0090).
 *
 * <p>Which makes the agreement of two readings the wrong question. What a debt may be written from
 * is what the declaration retains, and whether it retains a quantity is
 * {@link souther.compiler.partition.BorderQuantity#statesADeclarationRelativeLevel}'s answer — known
 * without asking a reading at all (issue #1251).
 */
class ADeclarationsPointIsWrittenFromTheDeclarationTest {

    /**
     * A case that spreads a common record, an invariant relating a spread field to one of its own,
     * and the sum the case belongs to — read once through each.
     */
    private static final String TWO_READINGS = """
            module example.two

            data Ok

            data Common = { hiredOn: Date }

            data Onl = { ...Common, leaveFrom: Date }
                invariant Date.daysBetween(hiredOn, leaveFrom) >= 0

            data Other = { ...Common, m: Int }

            data Any = Onl | Other

            behavior direct : (x: Onl) -> Ok
            let direct (x) = Ok

            behavior viaSum : (x: Any) -> Ok
            let viaSum (x) = Ok

            example direct
                | "a" : (Onl { hiredOn = Date("2020-01-01"), leaveFrom = Date("2020-01-02") }) -> Ok
            """;

    /**
     * The readings really do name the position two ways, so what is held below is not vacuous.
     *
     * <p>Read off the account rather than off the page: which positions a line was met at is what
     * the readings are, and a report prints them only under a point nobody could decide.
     */
    @Test
    void theTwoReadingsNameThePositionDifferently() {
        List<String> at = measured().modules().get(0).debts().get(0).debt().readingsSaid().stream()
                .map(BorderObligationPointAssessment.ReadingSaid::at)
                .distinct().sorted().toList();

        assertEquals(2, at.size(), at.toString());
        assertTrue(at.stream().anyMatch(each -> each.contains("@Onl.")), at.toString());
        assertTrue(at.stream().anyMatch(each -> !each.contains("@Onl.")), at.toString());
    }

    /** And the report is produced, which is the whole of what a partial sentence cost. */
    @Test
    void theReportIsProduced() {
        assertNotNull(report());
        assertNotNull(measured().json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    /**
     * The point is named by the line the rule drew, and by no reading's position.
     *
     * <p>What is left out is what the declaration does not hold. A sentence naming one of the two
     * paths would be naming the point after the place a walk reached first, which is what the
     * refusal was written to stop and is still not done.
     */
    @Test
    void thePointIsNamedByTheRuleAndByNoReadingsPosition() {
        String report = report();

        assertTrue(report.contains("no row is at the ON point of invariant Onl #1"), report);
        assertTrue(report.lines()
                        .filter(each -> each.contains("no row is at the"))
                        .noneMatch(each -> each.contains("x@Onl.") || each.contains("x.leaveFrom")),
                "a point of a declaration's line names no reading's position:\n" + report);
    }

    /** And nothing is written for a quantity the declaration has no words for. */
    @Test
    void nothingIsWrittenForAQuantityTheDeclarationDoesNotHold() {
        Adequacy.DeclaredDebt debt = measured().modules().get(0).debts().get(0);

        assertNull(debt.against(),
                "the level is a distance from a position the declaration does not name");
        assertTrue(debt.said().equals(debt.axis()),
                "so the sentence is what the line is on and nothing beside it: " + debt.said());
    }

    private static AdequacyReport measured() {
        Compilation compilation = Compilation.ofSource(TWO_READINGS, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }

    private static String report() {
        return measured().human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
