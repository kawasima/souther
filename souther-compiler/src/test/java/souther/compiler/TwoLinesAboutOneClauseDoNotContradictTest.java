package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two lines about one clause do not contradict each other.
 *
 * <p>Issue #842, at the surface it was found on. A bounded numeric newtype divided by a {@code
 * guard} printed both of these, four rows apart:
 *
 * <pre>
 *     partition   axes 1   equivalence partitions 2/2
 *       · read in part: length (a rule about it is one this compiler did not read)
 *     border      2/4
 *       ! no row is at the ON point price/length = 1 (invariant Length (min))
 * </pre>
 *
 * <p>The clause the first line said had gone unread is the clause the second draws its line from,
 * named. Both were true of different questions and neither said which question it was about: the
 * first was written off the reading that turns clauses into sets of values, which has no word for a
 * range and is short of the rules at every position an invariant bounds, and the second off the
 * reading that turns the same clause into an end and had it whole.
 *
 * <p>What settles it is asking the model rather than a reading. The question a rule raises is
 * answered by whichever reading took the rule in, and both of these rules were taken in — so there
 * is nothing to say about them, and the line is gone rather than reworded.
 */
class TwoLinesAboutOneClauseDoNotContradictTest {

    /** The issue's model, verbatim. */
    private static final String THE_ISSUE = """
            module example.rooms

            data Length = Int
                invariant min = value >= 1
                invariant max = value <= 100

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2

            example price
                | "long" : (Length(50)) -> 1
                | "short" : (Length(49)) -> 2
            """;

    /** The same, with one clause nothing reads. */
    private static final String WITH_A_CLAUSE_NOTHING_READS = """
            module example.rooms

            data Length = Int
                invariant min = value >= 1
                invariant max = value <= 100
                invariant square = value * value >= 4

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2

            example price
                | "long" : (Length(50)) -> 1
                | "short" : (Length(49)) -> 2
            """;

    /** A `String` bounded on its length, and a clause about that length nothing reads. */
    private static final String MEASURED_AT_A_COUNT = """
            module example.rooms

            data Code = String
                invariant nonEmpty = String.length(value) >= 1
                invariant odd = Int.abs(String.length(value)) >= 2

            behavior price : (code: Code) -> Int
            let price (code) =
                if String.length(code.value) >= 5 then 1 else 2

            example price
                | "long" : (Code("abcde")) -> 1
                | "short" : (Code("abcd")) -> 2
            """;

    /**
     * The line names what the question is about, which is not what the axis is measured at.
     *
     * <p>A length bound says which strings may stand at the position and draws its line on the
     * count, and the axis is named after the second. A question about which values may stand
     * somewhere is about `code`; printed against `String.length(code)` it is a reading of the
     * string's values reported against its length, which is the other half of what #842 found.
     */
    @Test
    void aQuestionIsPrintedAgainstItsOwnSubject() {
        String human = humanOf(MEASURED_AT_A_COUNT);

        assertTrue(human.contains(
                        "not accounted for: invariant Code (odd) — which values may stand at code"),
                human);
        assertFalse(human.contains("which values may stand at String.length(code)"),
                "the axis is measured at the length; the question is about the string: " + human);
    }

    private static String humanOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    /**
     * The clause a boundary is drawn from is not reported as one nothing read.
     *
     * <p>Both halves are asserted. Dropping the line would pass a test that only looked for its
     * absence, and the boundary lines are what say the clauses were read — so the report has to be
     * carrying them while saying nothing else about the same clauses.
     */
    @Test
    void aClauseALineIsDrawnFromIsNotReportedAsUnread() {
        String human = humanOf(THE_ISSUE);

        assertTrue(human.contains("value = 1 (invariant Length (min))"), human);
        assertTrue(human.contains("value = 100 (invariant Length (max))"), human);
        assertFalse(human.contains("not accounted for"),
                "every rule of this model was taken in by something: " + human);
        assertFalse(human.contains("rules not reached"), human);
    }

    /**
     * A clause nothing reads is reported, and named.
     *
     * <p>So that the line above is gone because the model is read and not because the report stopped
     * saying anything. {@code value * value >= 4} is beyond every reading here — it leaves the
     * position's floor where {@code min} put it and takes nothing out of the values it admits.
     */
    @Test
    void aClauseNothingReadsIsReportedAndNamed() {
        String human = humanOf(WITH_A_CLAUSE_NOTHING_READS);

        assertTrue(human.contains(
                        "not accounted for: invariant Length (square)"
                                + " — which values may stand at length"),
                "the rule the author wrote, and what about the position it was left saying: "
                        + human);
        assertFalse(human.contains("invariant Length (min) —"),
                "and nothing is said of the clauses something did take in: " + human);
    }
}
