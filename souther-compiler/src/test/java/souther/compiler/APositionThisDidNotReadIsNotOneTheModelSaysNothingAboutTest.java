package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the report says about a position it derived nothing at.
 *
 * <p>Two behaviors differing by three tokens. One compares a position and nothing else; the other
 * compares the same position to the same number with a second comparison in front of it. Both come
 * back with no axis, and the report used to say the same thing about both — that the model draws no
 * line there — which is true of neither, and of the second is the opposite of what the body says
 * two lines above.
 *
 * <p>So the sentence has to name which of the two happened. Not because the wording is nicer: an
 * author reading "the model draws no line here" stops looking, and the row at the line is the one
 * the model most needs.
 */
class APositionThisDidNotReadIsNotOneTheModelSaysNothingAboutTest {

    private static final String MODEL = """
            module example.repro

            data Kind = Domestic | Overseas
            data Request = { kind: Kind, cost: Int, note: String }

            data Auto
            data Manual

            data Prospecting
            data Qualified
            data Won
            data Stage = Prospecting | Qualified | Won

            behavior alone : (r: Request) -> Auto | Manual
            let alone (r) = if r.cost <= 100000 then Auto else Manual

            behavior inAConjunction : (r: Request) -> Auto | Manual
            let inAConjunction (r) =
                if r.cost >= 0 && r.cost <= 100000 then Auto else Manual

            data Deep = { note: String, more: Option<Deep> }
            data Outer = { deep: Deep }

            behavior returnsToItself : (o: Outer) -> Auto | Manual
            let returnsToItself (o) = if o.deep.note == "x" then Auto else Manual

            behavior byEquality : (r: Request) -> Auto | Manual
            let byEquality (r) = if r.cost == 3 then Auto else Manual

            behavior byDateTime : (at: DateTime) -> Auto | Manual
            let byDateTime (at) =
                if at < DateTime("2026-01-01T00:00:00") then Auto else Manual

            behavior byCase : (s: Qualified) -> Auto | Manual
            let byCase (s) = if s < Won then Auto else Manual

            behavior nothingCompared : (r: Request) -> Auto | Manual
            let nothingCompared (r) =
                match r.kind with
                    | Domestic -> Auto
                    | Overseas -> Manual

            data Cutoff = Date
                invariant value >= Date("2026-01-01")
            data Stepped = Int
                invariant value >= 1 + 1
            data Amount = Int
                invariant value >= 100

            behavior boundedByADate : (c: Cutoff) -> Auto | Manual
            let boundedByADate (c) = Auto

            behavior boundedByAnUnreadableEnd : (m: Stepped) -> Auto | Manual
            let boundedByAnUnreadableEnd (m) = Auto

            behavior boundedByANumber : (a: Amount) -> Auto | Manual
            let boundedByANumber (a) = Auto
            """;

    private static String blockOf(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String human = AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
        StringBuilder block = new StringBuilder();
        boolean inside = false;
        for (String line : human.split("\n", -1)) {
            boolean header = line.startsWith("  ") && !line.startsWith("   ");
            if (header) {
                inside = line.startsWith("  " + behavior + " ");
            }
            if (inside) {
                block.append(line).append('\n');
            }
        }
        return block.toString();
    }

    /** A position a comparison names, in a form nothing reads. */
    @Test
    void aPositionComparedInsideAConjunctionIsSaidToBeUnread() {
        assertTrue(linesOf("inAConjunction").stream()
                        .anyMatch(line -> line.axis().equals("inAConjunction/r.cost")),
                "the body compares it two lines above, so a line was read on it");
        assertFalse(blockOf("inAConjunction").contains("divided no way: r.cost"),
                "the body compares it two lines above: " + blockOf("inAConjunction"));
    }

    /** Every line one behavior's rules drew, which is what says the position was read at all. */
    private static List<BorderAssessment> linesOf(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), "example.repro");
        assertNotNull(boundaries, "the model under test compiles");
        return boundaries.get(behavior);
    }

    /** A position nothing compares, which is the sentence the other one was borrowing. */
    @Test
    void aPositionNothingComparesIsStillSaidToBeUndivided() {
        String block = blockOf("nothingCompared");

        assertTrue(block.contains("divided no way: r.cost"), block);
    }

    /**
     * A position the walk stopped at, which is not one it looked at and found undivided.
     *
     * <p>Where the input returns to a declaration already open on the path, what is under the
     * position is what is under the one above and is not read again. A row still carries a value
     * there, so this is a position nothing measured rather than a position the model divides no way
     * — and the two sentences send a reader to different work.
     */
    @Test
    void aPositionTheWalkStoppedAtSaysSo() {
        String block = blockOf("returnsToItself");

        assertFalse(block.contains("divided no way: o.deep.more@Some"), block);
        assertTrue(block.contains("the input returns here to a declaration already read above it"),
                block);
    }

    /** An equality divides the position, so it is not one nothing was established about. */
    @Test
    void anEqualityIsRead() {
        String block = blockOf("byEquality");

        assertFalse(notReadAbout(block, "r.cost"), block);
        assertFalse(block.contains("divided no way: r.cost"), block);
    }

    /**
     * A carrier no line can be drawn on is still said as that.
     *
     * <p>A position declared as one case of an enumeration. It is ordered — the comparison is on the
     * sum's order and typechecks — and it ranges over one value, so the sum's places are not its
     * own and no line divides it (ADR-0090). The ordered types that do carry a line are read;
     * this sentence is what is left for the values that do not.
     */
    @Test
    void aCarrierNoLineIsDrawnOnSaysThat() {
        String block = blockOf("byCase");

        assertTrue(notReadAbout(block, "s"), block);
        assertTrue(block.contains("no line can be drawn on"), block);
    }

    /** And a date-time is read, as the date beside it in this file already was. */
    @Test
    void aDateTimeIsReadAsADateIs() {
        String block = blockOf("byDateTime");

        assertFalse(notReadAbout(block, "at"), block);
        assertFalse(block.contains("divided no way: at"), block);
    }

    /**
     * A bound written on a carrier this draws no line on, which is a rule the model states.
     *
     * <p>The same sentence the comparison in a body earns, owed by the other rule that draws a line.
     * A position bounded by an invariant nothing here could read is not one the model leaves open:
     * the rule is two lines above the behavior, and naming it undivided says the opposite of what the
     * declaration says.
     */
    @Test
    void aPositionBoundedByARuleThisCouldNotReadIsSaidToBeUnread() {
        String block = blockOf("boundedByAnUnreadableEnd");

        assertFalse(block.contains("divided no way: m"), block);
        assertTrue(notReadAbout(block, "m"), block);
        assertTrue(block.contains("this compiler does not read"), block);
    }

    /**
     * A bound on a date, which is a line and is drawn.
     *
     * <p>The same rule a {@code guard} at a date states, owed by an invariant. The two were read by
     * separate tables and only the {@code guard}'s knew about dates, so a type stating its own
     * cut-over had no edge to reach and nothing said one was missing.
     */
    @Test
    void aBoundOnADateIsALineTheSameWayAGuardsIs() {
        String block = blockOf("boundedByADate");

        assertFalse(block.contains("divided no way: c"), block);
        assertFalse(notReadAbout(block, "c"), block);
        assertTrue(block.contains("border      borders 1   obligations 0/0\n"), block);
    }

    /**
     * And the row it asks for is a date.
     *
     * <p>The count a date is carried as inside the algebra is not something a model says or a person
     * writes, so a row at the edge would be a value the position does not take — and one that reads
     * perfectly well as the {@code Int} it is not. Asserted at the row rather than at the boundary,
     * because that is where the count would surface.
     */
    @Test
    void theRowAtADatesBoundIsWrittenAsADate() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        String block = souther.compiler.report.GeneratedRows.of(
                compilation, "example.repro", "boundedByADate", true, SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();

        assertTrue(block.contains("Cutoff(Date(\"2026-01-01\"))"), block);
    }

    /**
     * And so is the row at a date-time's.
     *
     * <p>A second count reads perfectly well as the number it is, so a row carrying one is refused
     * by the decoder rather than by anything that could say what went wrong. Asserted at the text,
     * because the count and the date-time are both values and only one of them is the position's.
     */
    @Test
    void theRowAtADateTimesLineIsWrittenAsADateTime() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        String block = souther.compiler.report.GeneratedRows.of(
                compilation, "example.repro", "byDateTime", true, SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();

        assertTrue(block.contains("DateTime(\"2026-01-01T00:00:00\")"), block);
        assertFalse(block.contains("refused at construction"), block);
    }

    /** The same shape on a carrier this does draw a line on, so a reading that stopped everywhere
     * cannot pass the one above. */
    @Test
    void aPositionBoundedByANumberIsNamedNeitherWay() {
        String block = blockOf("boundedByANumber");

        assertFalse(block.contains("divided no way: a"), block);
        assertFalse(notReadAbout(block, "a"), block);
    }

    /** The one that is read is not named either way. */
    @Test
    void aPositionThatWasReadIsNotNamedAtAll() {
        String block = blockOf("alone");

        assertFalse(block.contains("divided no way: r.cost"), block);
        assertFalse(notReadAbout(block, "r.cost"), block);
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
