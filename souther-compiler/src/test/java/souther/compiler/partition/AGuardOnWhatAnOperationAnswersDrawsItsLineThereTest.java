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
 * <p>And {@code Int.abs} is not among them, which is the other half of what this shows. It answers
 * an {@code Int} like the two above and the library declares nothing about what that number is, so
 * there is no account for a line to be measured on — the guard is a rule about a value an operation
 * made, and no line comes of it.
 *
 * <p><b>Not a line at nought, which is what the reading used to draw.</b> {@code Int.abs} is written
 * in this language as a fork at nought, and a reading made over a tree with that body spliced in
 * took the line the body draws. A caller's model never said anything about nought: the number is
 * this operation's implementation, and a row owed at it is a row owed for how the library happens
 * to be written. So what the reading is made over keeps the operation standing, and the line an
 * author is owed rows for is the one an author wrote.
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
        return GuardThresholds.of(behavior, checked.analysisBodies().get(behavior), body, plan,
                        inputs, rules).thresholds().stream()
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
     * And a guard on an operation the language writes out draws no line here.
     *
     * <p>Nothing declares what {@code Int.abs} answers of the number it is given, so there is no
     * account the line could be measured on and the rule is about a value an operation made. That
     * is a reading short of what the model states, and it is reported as one — where a line at
     * nought, taken from how the library is written, was a row owed at a number the author's model
     * never mentions.
     *
     * <p>What would place a line here is a statement about the operation, said where the operation
     * is declared. Which is the same thing the two above have and this one does not.
     */
    @Test
    void aGuardOnAnOperationWrittenInTheLanguageDrawsNoLine() {
        assertEquals(List.of(), thresholdsOf("howFar"));
    }
}
