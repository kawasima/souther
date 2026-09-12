package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a way asks of an answer that nothing composes against is carried as a thing it asked and
 * could not state.
 *
 * <p>Both halves of a way travel: what could be stated is what a value is composed against, and
 * what could not is why a row composed against the rest may not take the way. Stated all the same,
 * a demand nothing meets would sit among the ones a value was built for while nothing built one for
 * it — and a way whose demands were not all met would report itself as one whose demands were all
 * stated.
 *
 * <p>Which is the same shape as a narrowing the arithmetic declines. A reading that meets something
 * it has no words for says so; it does not put the thing on the list and leave the next reader to
 * notice that nothing acted on it.
 */
class ADemandNothingComposesForIsDeclinedAndNotStatedTest {

    private static final String TYPES = """
            module example.declined

            data Reading = { ok: Bool, at: Int }
            data Yes
            data No
            data Answer = Yes | No
            """;

    /**
     * A truth of the answer itself is stated: a {@code Bool} is two values, and the demand is the
     * value.
     */
    @Test
    void aTruthOfTheWholeAnswerIsStated() {
        AnswersDemanded demanded = demandsOf(TYPES + """

                behavior permits : (at: Int) -> Bool

                behavior decides : (at: Int) -> Answer
                    depends on permits
                let decides (at, permits) = if permits(at) then Yes else No
                """);

        assertTrue(demanded.whole(),
                () -> "every demand of the way was stated: " + demanded);
        assertTrue(demanded.stated().stream().anyMatch(AnswerDemand.ATruth.class::isInstance),
                () -> "and the truth is one of them: " + demanded);
    }

    /**
     * A truth read off a place inside the answer is not.
     *
     * <p>A {@code Bool} holds no position, so the demand is about a place a value composed for the
     * answer has no way of being put at. What the way asks is real; what this reading can do about
     * it is nothing, and that is what it says.
     */
    @Test
    void aTruthOfAPlaceInsideTheAnswerIsDeclined() {
        AnswersDemanded demanded = demandsOf(TYPES + """

                behavior look : (at: Int) -> Reading

                behavior decides : (at: Int) -> Answer
                    depends on look
                let decides (at, look) = if look(at).ok then Yes else No
                """);

        assertFalse(demanded.whole(),
                () -> "the way asks something of the answer this cannot state: " + demanded);
        assertFalse(demanded.stated().stream().anyMatch(AnswerDemand.ATruth.class::isInstance),
                () -> "and it is not among the demands a value would be composed against: "
                        + demanded);
    }

    /** The demands of the first way the body states, which is where the condition is met. */
    private static AnswersDemanded demandsOf(String source) {
        List<DecisionReading.Ruled> found = DecisionReadings.of(source, "decides").found();
        assertFalse(found.isEmpty(), "the body states a way");
        return found.getFirst().demands();
    }
}
