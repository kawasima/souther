package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Text;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternRead;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two restrictions coming to the same values are one question, whichever of them was written down.
 *
 * <p>What a search for a value is given is a set the declarations leave and a run of the order, and
 * what it is asked about is the values in both. So the same values reached by putting the restriction
 * in the set and by putting it in the run are the same question, and a value written out for one is a
 * value written out for the other.
 *
 * <p>Which they are not. A set that names a language is crossed with the run as a machine and a
 * string is read out of it wherever the run leaves one; a set that names every value but a few has no
 * machine built for it here, and the walk that stands in offers the least string and then longer ones
 * built from a letter — none of which is inside a run away from that handful. So a rule written as a
 * comparison comes back with no value where the same rule written as a prefix comes back with one,
 * and a report says nothing could build a representative for a run holding as many strings as there
 * are.
 *
 * <p>The shapes are right to differ; a set naming its values and a set naming a language are not
 * searched the same way. What is wrong is that the difference reaches the answer, which is a step a
 * reader of the model cannot see.
 *
 * <p><b>About a witness that can be written, and not about which one.</b> What this order hands back
 * is a value a source can carry ({@link souther.compiler.regex.Language#someWritten}), so what has to
 * agree is whether one exists — two searches may write out different strings. And where an allowance
 * ran out there is no answer to agree about: that is an explicit limit and not evidence that the run
 * holds nothing, which is why it is told apart here rather than read off a null.
 */
class OneEffectiveDomainIsOneAnswerHoweverItWasRestrictedTest {

    private static final Carrier TEXT = new Carrier.Text();

    /** The strings strictly between `JP` and `JQ`, said as a run of the order. */
    private static final OrderedInterval BETWEEN = new OrderedInterval(
            Endpoint.exclusive(Text.of("JP")), Endpoint.exclusive(Text.of("JQ")));

    /** The whole of the order, said as a run. */
    private static final OrderedInterval EVERYTHING = new OrderedInterval(null, null);

    /** The strings `JP` starts, said as a set. */
    private static final ValueSet STARTING_WITH_JP = matching("JP[\\s\\S]*");

    /** What a search came back with, told apart from having had no room to answer. */
    private sealed interface Answer {
        record Wrote(String string) implements Answer {}
        record NoneWritable() implements Answer {}
        record RanOut(Meter.Stopped limit) implements Answer {}
    }

    private static ValueSet matching(String regex) {
        PatternRead said = PatternParser.read(regex);
        return ValueSet.matching(PatternPlan.of(
                        assertInstanceOf(PatternRead.Read.class, said, regex).syntax())
                .compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter()));
    }

    /**
     * What the order says about the values of {@code set} inside {@code run}.
     *
     * <p>The meter is read beside the answer, because a null is both "nothing here writes one" and
     * "there was no room to find out" and only one of those is a fact about the values.
     */
    private static Answer asked(ValueSet set, OrderedInterval run) {
        Meter meter = PatternPlan.Budget.OF_A_WITNESS.meter();
        Place at = TEXT.somewhereIn(set, run, List.of(), meter);
        if (at != null) {
            return new Answer.Wrote(((Text) at).at());
        }
        return meter.stoppedBy() == null ? new Answer.NoneWritable()
                : new Answer.RanOut(meter.stoppedBy());
    }

    /** Which of the three it was, for a comparison that is not about which string was written. */
    private static Class<?> answerClass(Answer answer) {
        return answer.getClass();
    }

    /**
     * The two ways of saying it reach the same strings, which is what makes them one question.
     *
     * <p>Measured rather than argued. A test whose two cases turned out to be about two sets of
     * values would be asking for the same answer to two questions.
     */
    @Test
    void theTwoRestrictionsReachTheSameStrings() {
        for (String each : List.of("JPa", "JP0", "JPzzz")) {
            assertTrue(BETWEEN.admits(Text.of(each)) && STARTING_WITH_JP.has(new Value.Text(each)),
                    () -> each + " is in both");
        }
        for (String each : List.of("JP", "JQ", "JR", "J", "")) {
            assertTrue(!(BETWEEN.admits(Text.of(each))
                            && STARTING_WITH_JP.has(new Value.Text(each))),
                    () -> each + " is in neither, once each side is met with the other");
        }
    }

    /** A set naming a language, met with the whole order: a string is written out. */
    @Test
    void aSetThatNamesTheStringsIsAskedAndAnswers() {
        assertInstanceOf(Answer.Wrote.class, asked(STARTING_WITH_JP, EVERYTHING),
                "the language holds strings a source can carry, and one of them is written out");
    }

    /** The same strings reached through the run instead, which comes to the same answer. */
    @Test
    void andSoIsTheRunThatLeavesTheSameStrings() {
        assertInstanceOf(Answer.Wrote.class, asked(ValueSet.ANY, BETWEEN),
                "the run holds every string `JP` starts but itself, and a search told so by the run"
                        + " rather than by the set has the same values to choose from");
    }

    /**
     * And the two together are that question again.
     *
     * <p>Compared as which of the three answers came back and not as which string. Two searches over
     * one domain may write out different values of it; what they may not do is disagree about whether
     * the domain has one to write.
     */
    @Test
    void andTheTwoTogetherAreTheSameQuestionAgain() {
        assertEquals(answerClass(asked(STARTING_WITH_JP, BETWEEN)),
                answerClass(asked(ValueSet.ANY, BETWEEN)),
                "one domain, one answer about whether a witness can be written");
    }
}
