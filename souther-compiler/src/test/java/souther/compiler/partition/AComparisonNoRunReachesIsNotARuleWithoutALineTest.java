package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A comparison no run answers through is not a rule the model has no line for.
 *
 * <p>Its outcome is about no row, because the expression it decides never answers a value — so
 * there is nothing for a report to say of it, and in particular nothing in the words a reading of
 * the arithmetic answers in. Those words are about what the arithmetic could do with the form, and
 * the form is not what refuses the comparison: where it stands is. Given a reason in those words all
 * the same, a comparison the arithmetic reads to the end is reported as one whose form went unread,
 * and the measurement is marked short of something no reader fell short of.
 *
 * <p>The claim is made at both levels, and they are two levels now. The reading says what the model
 * states, and the model states this comparison whether or not a run answers through it — so the
 * reading admits it. What refuses it is where a run through it would be recorded, which the emitted
 * tree answers and the reading is not asked. Without the first, the silence below would hold of a
 * model whose comparison was simply a line.
 *
 * <p>And the silence is shown to be the right answer rather than a missing one: the case the
 * comparison stands in is reported as one the model rules out, and everything the model does answer
 * by is measured whole.
 */
class AComparisonNoRunReachesIsNotARuleWithoutALineTest {

    /**
     * A comparison whose every continuation aborts, with the rule written in {@code condition}.
     *
     * <p>{@code Off} is a case the model rules out, so the {@code unreachable}s are claims the rules
     * bear out (E1326 otherwise) — and the comparison deciding between the two of them is one no
     * run answers past, whichever way it comes out.
     */
    private static String model(String condition) {
        return """
                module example.probe

                data On
                data Off
                data Flag = On | Off
                data Active = Flag invariant value /= Off
                data Answer = Int

                behavior pick : (f: Active, n: Int) -> Answer
                let pick (f, n) = match f.value with
                    | On  -> Answer(1)
                    | Off -> if %s then unreachable "a" else unreachable "b"

                example pick
                    | "on" : (Active(On), 3) -> Answer(1)
                """.formatted(condition);
    }

    /** A form the arithmetic reads to the end: one line, cutting one position at one value. */
    private static final String READ = "n >= 100000";

    /** A form the arithmetic stops on: the product of a position with itself. */
    private static final String STOPPED = "n * n >= 100000";

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    /**
     * The reading reads it, because the model states it.
     *
     * <p>Whether a run answers through the comparison is a fact about the tree that runs, and the
     * reading is of what the model states. Refused here, a comparison the emitter numbers nothing
     * for would have no reading at all — and the report below would be silent about it for want of
     * anything to say rather than because there is nothing to say.
     */
    @Test
    void theComparisonIsReadWhereItStandsAllTheSame() {
        assertInstanceOf(BoundaryPolicy.Standing.Admitted.class,
                ReadComparisons.of(model(READ), "pick").only().standing(),
                "the model states this comparison whether or not a run answers through it");
    }

    @Test
    void aFormReadToTheEndIsNotReportedAsOneNobodyRead() {
        String human = report(model(READ));
        assertFalse(human.contains("not read: comparison"), human);
        assertTrue(human.contains("measurement: complete"), human);
    }

    /**
     * And a form the arithmetic would have stopped on says nothing either. The standing is decided
     * before the arithmetic is asked, so what the arithmetic would have made of the form is not a
     * fact anybody holds.
     */
    @Test
    void aFormTheArithmeticWouldStopOnIsNotReportedEither() {
        String human = report(model(STOPPED));
        assertFalse(human.contains("not read: comparison"), human);
        assertTrue(human.contains("measurement: complete"), human);
    }

    /**
     * The silence is the right answer and not a gap. What the model does answer by is measured
     * whole, and the case the comparison stands in is said to be one the model rules out.
     */
    @Test
    void whatTheModelAnswersByIsMeasuredWholeAndTheCaseIsSaidToBeRuledOut() {
        String human = report(model(READ));
        assertTrue(human.contains("branch      1/1"), human);
        assertTrue(human.contains("`Off` is declared unreachable on every path"), human);
        // The position the refused comparison names is one the model draws no line through, which
        // is what is true of it, said in the words for that and not in a reading's.
        assertTrue(human.contains("divided no way: n"), human);
        assertTrue(human.contains("border      not applicable"), human);
    }

    /** The half that makes the other half worth reading: the same rule where a run answers through
     *  it is a line, and the report carries the border. */
    @Test
    void theSameRuleWhereARunAnswersThroughItIsALine() {
        String human = report("""
                module example.probe

                data Answer = Int

                behavior pick : (n: Int) -> Answer
                let pick (n) = if n >= 100000 then Answer(1) else Answer(0)

                example pick
                    | "low" : (3) -> Answer(0)
                """);
        assertTrue(human.contains("borders 1"), human);
        // The point names where on the line it is and which rule drew it; what it is on is the
        // reading's word, since a line is owed once wherever it is read.
        assertTrue(human.contains("the ON point (comparison@6:21)"), human);
        assertTrue(human.contains("read as pick/n: = 100000"), human);
    }
}
