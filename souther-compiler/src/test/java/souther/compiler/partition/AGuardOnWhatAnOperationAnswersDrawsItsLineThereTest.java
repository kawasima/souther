package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Sig;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A guard comparing what an operation answers is a line on that number, measured by what the
 * operation answers and read off the value's own order.
 *
 * <p>Only a size could be one before, and nothing said so. A term was a size or a position's own
 * content, so a guard on anything else named no term the reading had, no line was drawn, and the
 * guard went past without a word — a division the body makes that the report does not know about.
 *
 * <p>{@code Time.hour} beside {@code String.length} because the pair is what shows where each answer
 * comes from. They share nothing: different accounts of what is taken, different orders at the
 * position, and the same order for the answer. A carrier read off the account or off the kind of
 * term would have to be one answer for both, and what is actually one for both is that the operation
 * answers an {@code Int} (#1027).
 *
 * <p>And {@code Int.abs} is not among them and cannot be. It is an ordinary {@code let} over
 * {@code <} and {@code -}, so the reading takes its body and draws the line the definition draws —
 * at nought, which is where {@code n < 0} is. A term standing for the call would be a second reading
 * of the same call, and the declarations refuse to be written that way.
 */
class AGuardOnWhatAnOperationAnswersDrawsItsLineThereTest {

    private static final String MODEL = """
            module example.answered

            data Near
            data Far

            behavior howLong : (s: String) -> Near | Far
            let howLong (s) = if String.length(s) > 10 then Far else Near

            behavior afterNoon : (t: Time) -> Near | Far
            let afterNoon (t) = if Time.hour(t) > 12 then Far else Near

            behavior howFar : (n: Int) -> Near | Far
            let howFar (n) = if Int.abs(n) > 10 then Far else Near
            """;

    /** Every threshold a body's guards put on a number: the term, where it stands, and what the
     *  line is measured on. */
    private static List<String> thresholdsOf(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        assertNotNull(sigs.get(behavior), "and its signature is read");
        CoverageSites.Plan plan = checked.plan();
        Core body = checked.behaviorBodies().get(behavior);
        InputDomain inputs = compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior);
        souther.compiler.inputs.Quantities quantities = inputs.quantities(rules);
        return GuardThresholds.of(behavior, body, plan, inputs, rules).thresholds().stream()
                .<String>map(each -> each.term() + " at "
                        + (each.value() == null ? "nowhere" : each.value().key()) + " on "
                        + quantities.ordersOf(each.term()).answered())
                .toList();
    }

    /** The one that worked before, and still says the same thing. */
    @Test
    void aSizeGuardIsALineOnTheCount() {
        assertEquals(List.of("String.length(s) at 10 on Whole[]"), thresholdsOf("howLong"));
    }

    /**
     * And a guard on the hour of a time is a line on the hours.
     *
     * <p>The assertion the separation turns on. The position counts the seconds of its day and the
     * line stands at the twelfth hour, so what the boundary is measured on is not what the value is
     * read on — taken from the position, the line would be at the twelfth second.
     */
    @Test
    void aGuardOnAPartOfATimeIsALineOnThatPart() {
        assertEquals(List.of("Time.hour(t) at 12 on Whole[]"), thresholdsOf("afterNoon"));
    }

    /**
     * A guard on an operation the language writes out is read through it.
     *
     * <p>Not a gap. {@code Int.abs(n) > 10} is {@code (if n < 0 then 0 - n else n) > 10}, and what
     * the reading takes from it is the line the definition draws at nought. That is more than a term
     * standing for the call could say, which is why such an operation may not declare one.
     */
    @Test
    void aGuardOnAnOperationWrittenInTheLanguageIsReadThroughIt() {
        assertEquals(List.of("n at 0 on Whole[]"), thresholdsOf("howFar"));
    }
}
