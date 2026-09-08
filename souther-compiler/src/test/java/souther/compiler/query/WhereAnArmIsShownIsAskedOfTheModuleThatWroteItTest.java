package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.coverage.ArmReportAnchor;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.diag.Citation;
import souther.compiler.meta.ModulePath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fork one module writes stands in every module that calls into it, and where it is written is
 * the writing module's answer rather than something each copy carries.
 *
 * <p>What crosses the boundary is which fork it is. A report about one of its arms points into the
 * file the fork is written in, which is a second question — so moving that file moves the caret and
 * leaves the arm the caller is holding exactly as it was. Carried on the arm, the same edit would
 * arrive at the caller as a fork that says something different, and nothing about what either
 * module says would have changed.
 */
class WhereAnArmIsShownIsAskedOfTheModuleThatWroteItTest {

    private static final String LIMITS = """
            module shop.limits exposing ( withinLimit )

            let withinLimit (n: Int): Bool =
                if n <= 100 then true else false
            """;

    /** The same, with a line written above the helper and nothing else changed. */
    private static final String LIMITS_MOVED = """
            module shop.limits exposing ( withinLimit )

            // The cap the business agreed.
            let withinLimit (n: Int): Bool =
                if n <= 100 then true else false
            """;

    private static final String ORDERS = """
            module shop.orders exposing ( accept )

            import shop.limits ( withinLimit )

            behavior accept : (n: Int) -> Bool
            let accept (n) = withinLimit(n)
            """;

    private static Map<String, String> workspace(String limits) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("limits.sou", limits);
        byId.put("orders.sou", ORDERS);
        return byId;
    }

    private static Compilation started(String limits) {
        Compilation c = Compilation.ofDocuments(workspace(limits), Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        return c;
    }

    /** The arms `shop.orders` reaches that `shop.limits` wrote. */
    private static List<CoverageSites.ArmSite> importedArms(Compilation c) {
        Answer<Bodies.Elaborated> checked = c.db().ask(new Bodies.Checked("shop.orders"));
        assertTrue(checked.present() && checked.value() != null,
                "shop.orders checks, so it has places");
        return checked.value().plan().sites().stream()
                .filter(CoverageSites.ArmSite.class::isInstance)
                .map(CoverageSites.ArmSite.class::cast)
                .filter(each -> "shop.limits".equals(each.obligation().origin().module()))
                .toList();
    }

    @Test
    void theQuestionIsPutToTheModuleThatWroteTheFork() {
        Compilation c = started(LIMITS);
        List<CoverageSites.ArmSite> arms = importedArms(c);
        assertEquals(2, arms.size(), "the helper's `if` is spliced into the caller as two arms");

        ArmReportAnchor.WhereItIsWritten anchor = assertInstanceOf(
                ArmReportAnchor.WhereItIsWritten.class, arms.get(0).anchor(),
                "the helper is written in a file this compilation holds");
        assertEquals("shop.limits",
                new Sites.WhereAConstructIsWritten(anchor.origin()).module(),
                "so where it is written is a question about shop.limits, asked of shop.limits");
    }

    @Test
    void movingTheHelperMovesTheCaretAndLeavesTheArmAlone() {
        Compilation before = started(LIMITS);
        CoverageSites.ArmSite was = importedArms(before).get(0);
        Citation shownBefore = Sites.placeOf(before.db(), was.anchor());

        Compilation after = started(LIMITS_MOVED);
        CoverageSites.ArmSite now = importedArms(after).get(0);
        Citation shownAfter = Sites.placeOf(after.db(), now.anchor());

        assertEquals(was.anchor(), now.anchor(),
                "the caller is holding the same fork: what it says did not change");
        assertNotEquals(shownBefore, shownAfter,
                "and the file it is written in says it is a line further down");
    }

    @Test
    void theWarningIsPutWhereTheHelperIsWrittenAndNotWhereItWasCalled() {
        Compilation c = started(LIMITS);
        Citation shown = Sites.placeOf(c.db(), importedArms(c).get(0).anchor());
        Citation.Written written = assertInstanceOf(Citation.Written.class, shown,
                "the helper is in a file the reader holds");
        assertEquals(4, written.at().line(),
                "which is the line the `if` is on in limits.sou, not the call in orders.sou");
    }
}
