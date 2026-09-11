package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Measurement;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A line the body draws is the model's line wherever in the condition it is written.
 *
 * <p>Two behaviors comparing the same position to the same number. One writes the comparison on its
 * own and gets an axis divided at it and a boundary on each side; the other writes a second
 * comparison in front of it and used to get neither. Nothing about the model differs, so nothing
 * about what the rows owe should.
 *
 * <p>Both sides of the line are owed, including the one a row reaches by making the comparison false.
 * Under {@code A && B} that row lands in {@code else}, where a row that never got to {@code B} lands
 * too — so the arms do not decide it and the comparison is observed where it runs instead.
 */
class AComparisonInsideAConjunctionIsStillTheModelsLineTest {

    private static final String MODEL = """
            module example.repro

            data Kind = Domestic | Overseas
            data Request = { kind: Kind, cost: Int }

            data Auto
            data Manual

            behavior alone : (r: Request) -> Auto | Manual
            let alone (r) = if r.cost <= 100000 then Auto else Manual

            behavior inAConjunction : (r: Request) -> Auto | Manual
            let inAConjunction (r) =
                if r.cost >= 0 && r.cost <= 100000 then Auto else Manual

            example alone
                | "inside" : (Request { kind = Domestic, cost = 1 }) -> Auto

            example inAConjunction
                | "inside" : (Request { kind = Domestic, cost = 1 }) -> Auto
            """;

    private static String blockOf(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String human = AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
        StringBuilder block = new StringBuilder();
        boolean inside = false;
        for (String line : human.split("\n", -1)) {
            if (line.startsWith("  ") && !line.startsWith("   ")) {
                inside = line.startsWith("  " + behavior + " ");
            }
            if (inside) {
                block.append(line).append('\n');
            }
        }
        return block.toString();
    }

    /** The position is divided at the number the body compares it to, either way it is written. */
    @Test
    void thePositionIsDividedAtTheNumberTheBodyNames() {
        assertTrue(blockOf("alone").contains("partition   axes 2"), blockOf("alone"));
        assertTrue(blockOf("inAConjunction").contains("partition   axes 2"),
                blockOf("inAConjunction"));
    }

    /** And the position is no longer one nothing was established about. */
    @Test
    void thePositionIsNoLongerReportedAsUnread() {
        assertFalse(notReadAbout(blockOf("inAConjunction"), "r.cost"),
                blockOf("inAConjunction"));
    }

    /** The edge a row can reach through the arm that proves the comparison ran is owed as ever. */
    @Test
    void theEdgeOnTheSideTheConjunctionAdmitsIsStillOwed() {
        assertTrue(blockOf("inAConjunction").contains("read as inAConjunction/r.cost: = 100000"),
                blockOf("inAConjunction"));
    }

    /**
     * And so is the edge on the other side, which is the one a conjunction used to lose.
     *
     * <p>A row at 100001 takes the {@code else} arm, which under a conjunction is also where a row
     * taking it for the other comparison's sake lands. The arms do not separate them; the site at the
     * comparison does, so the edge is owed the same as the one beside it.
     */
    @Test
    void theEdgeOnTheOtherSideIsOwedTheSame() {
        assertEquals(new ItemAssessment.Coverage.NoHit(),
coverageAt("inAConjunction", "inAConjunction/r.cost", "100001").made().orElseThrow());
    }

    /** What the rows established about one line of one behavior. */
    private static Measurement<ItemAssessment.Coverage> coverageAt(String behavior, String axis,
                                                          String value) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), "example.repro");
        assertNotNull(boundaries, "the model under test compiles");
        return BorderAssessment.pointsOf(boundaries.get(behavior)).stream()
                .filter(p -> p.role().againstTheLine()).filter(p -> p.owed() != null)
                .filter(p -> p.border().axis().equals(axis) && value.equals(p.against()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line at " + axis + " = " + value))
                .owed().coverage();
    }

    /**
     * Whether any {@code not read} line of {@code block} is about {@code position}.
     *
     * <p>Asked as a line rather than as a prefix. A finding about a rule names the rule first and
     * the position after it, and one about a position names the position — so a test matching
     * `+not read: <position>+` stopped meaning anything for the first kind rather than failing,
     * which is a negative assertion that passes because the words moved.
     */
    private static boolean notReadAbout(String block, String position) {
        return block.lines().anyMatch(line -> line.contains("not read:")
                && (line.contains("not read: " + position + " ")
                        || line.contains("about `" + position + "`")));
    }
}
