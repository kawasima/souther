package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position is measured at every number the rules name of it, and at every kind of thing they say.
 *
 * <p>One location carries one axis no longer. {@code Time.hour} and {@code Time.minute} are two
 * numbers of one time, a body comparing both draws two lines, and what a report said was that the
 * behavior divides no position and draws no line — the sentence a body with no comparison in it
 * gets, with the measurement called complete beside it.
 *
 * <p>Two things were behind it and each is measured here. Which numbers a position is measured at
 * was answered with the one a body names, so two were answered as none; and it was answered from
 * the lines alone, so a body that only singles a value out named none either.
 */
class APositionIsMeasuredAtEveryNumberTheRulesNameOfItTest {

    private static final String MODEL = """
            module example.gate

            data Early
            data Late
            data When = Early | Late

            data Slot = { at: Time }
            data Box = { s: String }

            behavior oneNumber : (slot: Slot) -> When
            let oneNumber (slot) =
                if Time.hour(slot.at) >= 9 then Late else Early

            behavior twoNumbersAtOneLocation : (slot: Slot) -> When
            let twoNumbersAtOneLocation (slot) =
                if Time.hour(slot.at) >= 9 && Time.minute(slot.at) >= 30 then Late else Early

            behavior oneNumberSingledOut : (slot: Slot) -> When
            let oneNumberSingledOut (slot) =
                if Time.hour(slot.at) == 9 then Late else Early

            behavior oneSingledOneOrdered : (slot: Slot) -> When
            let oneSingledOneOrdered (slot) =
                if Time.hour(slot.at) == 9 && Time.minute(slot.at) >= 30 then Late else Early

            behavior orderedAndSingledOnOneNumber : (slot: Slot) -> When
            let orderedAndSingledOnOneNumber (slot) =
                if Time.hour(slot.at) >= 9 && Time.hour(slot.at) == 12 then Late else Early

            behavior itsOwnValueAndANumberTakenOfIt : (box: Box) -> When
            let itsOwnValueAndANumberTakenOfIt (box) =
                if box.s == "x" && String.length(box.s) > 3 then Late else Early
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

    /** The one this always got right, so that what the others assert is a difference and not a
     *  reading of the report this test made up. */
    @Test
    void oneNumberOfALocationIsMeasured() {
        assertTrue(blockOf("oneNumber").contains("partition   axes 1"), blockOf("oneNumber"));
        assertTrue(blockOf("oneNumber").contains("border      borders 1"), blockOf("oneNumber"));
    }

    /** Two numbers of one location are two axes and two lines. Neither dropping both nor keeping
     *  one is an outcome: each line is on a number of its own, and the numbers tell them apart. */
    @Test
    void twoNumbersOfOneLocationAreTwoAxes() {
        String block = blockOf("twoNumbersAtOneLocation");
        assertTrue(block.contains("partition   axes 2"), block);
        assertTrue(block.contains("border      borders 2"), block);
    }

    /** A value singled out names a number as much as a line does. Read off the lines alone, a body
     *  whose only comparison is an equality measured nothing at all. */
    @Test
    void aValueSingledOutNamesTheNumberItIsOf() {
        String block = blockOf("oneNumberSingledOut");
        assertTrue(block.contains("partition   axes 1"), block);
        assertTrue(block.contains("border      borders 1"), block);
    }

    /** And the two kinds together at two numbers. Read off the lines alone, this measured the
     *  ordered number and dropped the singled one, which is the same loss keeping one of two is. */
    @Test
    void aSingledNumberAndAnOrderedOneAreBothMeasured() {
        String block = blockOf("oneSingledOneOrdered");
        assertTrue(block.contains("partition   axes 2"), block);
        assertTrue(block.contains("border      borders 2"), block);
    }

    /**
     * What a location holds is one of the numbers the rules name of it, not the alternative to
     * them. Answered as the alternative, a body naming both measured only the location's own value
     * and the other went unsaid.
     */
    @Test
    void aLocationsOwnValueIsOneOfItsNumbers() {
        String block = blockOf("itsOwnValueAndANumberTakenOfIt");
        assertTrue(block.contains("partition   axes 2"), block);
        assertTrue(block.contains("border      borders 2"), block);
    }

    /**
     * Both kinds about one number, and the rule that names a value keeps the values either side of
     * it apart.
     *
     * <p>Asked of the lines and not of the report's sentences. What a block prints about a line is
     * what some row came to at it, and this model has no rows — so a reading that dropped the
     * second line would print the same nothing as one that kept it. What says the rule is still
     * measured is that its line is there, cutting where it wrote, with a value of the position on
     * each side of the one it names.
     *
     * <p>Both halves, because either alone passes on a reading this is about. The two labels alone
     * hold against a reading that kept the equality and read it as an order — which is the shape
     * that has one neighbour rather than two — and the points alone hold against one that lost the
     * ordering beside it.
     */
    @Test
    void aValueSingledOutBesideAnOrderingIsStillALine() {
        List<BorderAssessment> lines = linesOf("orderedAndSingledOnOneNumber");
        assertEquals(List.of("Time.hour(slot.at) = 12", "Time.hour(slot.at) = 9"),
                lines.stream().map(BorderAssessment::label).sorted().toList(),
                "one number, cut by an order at nine and by a rule naming twelve");

        assertEquals(List.of("ON = 12", "OFF below the line = 11", "OFF above the line = 13"),
                againstTheLine(lines, "Time.hour(slot.at) = 12"),
                "the value the rule names, and the nearest value on each side of it");
        assertEquals(List.of("ON = 9", "OFF = 8"),
                againstTheLine(lines, "Time.hour(slot.at) = 9"),
                "and an order beside it has the one neighbour whose class differs");
    }

    /** What a row is asked for at each point of one line that names a value, by the name that line
     *  gives the point. */
    private static List<String> againstTheLine(List<BorderAssessment> lines, String label) {
        BorderAssessment line = lines.stream().filter(each -> label.equals(each.label()))
                .findFirst().orElseThrow(() -> new AssertionError("no line here is " + label));
        return line.border().answers().keySet().stream()
                .filter(souther.compiler.partition.DomainPoint::againstTheLine)
                .map(point -> line.border().named(point) + " = " + line.against(point)).toList();
    }

    /** The lines one behavior of this model draws, as they were measured. */
    private static List<BorderAssessment> linesOf(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return Adequacy.boundariesOf(compilation.db(), "example.gate").get(behavior);
    }
}
