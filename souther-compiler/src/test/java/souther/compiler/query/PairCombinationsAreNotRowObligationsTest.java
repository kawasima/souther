package souther.compiler.query;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A combination of two classes is not a thing a row is owed at.
 *
 * <p>What the pair space counts is how much of the model's own product the rows happen to reach.
 * Nothing asks for a row at a combination and nothing offers one: a row composed for a pair moves
 * two positions at once and says nothing about which of them the answer turned on, which is what
 * issue #967 settled. So a count of combinations no row reaches is not work anybody is behind on,
 * and the report says so beside it.
 *
 * <p><b>This is what makes that sentence true.</b> A report saying nothing is owed on the strength
 * of a formatter's reading would go on saying it the day a finding about a combination was added.
 * What holds it is that no gap this compiler can find is about one: the vocabulary of findings has
 * no arm for a combination, and the offering of rows answers every arm it has.
 *
 * <p>So a change that gave a combination a finding fails here rather than in a formatter, and
 * whoever makes it is asked the question this test is named for. Answering it is allowed — the
 * decision is #967's and may be revisited — but it is a change to what a row is owed at, and this
 * says so out loud.
 */
class PairCombinationsAreNotRowObligationsTest {

    /**
     * Every kind of gap this compiler can find, named, and none of them is about a combination.
     *
     * <p>The kinds are written out rather than matched on their names. A name says what somebody
     * called an arm and not what it is about, so a finding about a combination added under another
     * word would pass a sweep for the word — and this is a law about what a row is owed at, which a
     * spelling does not settle.
     *
     * <p>So an arm added to {@link About} arrives here as a kind nobody has said anything about,
     * and whoever adds it answers the question this test is named for: is a row owed at one? The
     * answer may be yes. The decision is #967's and may be revisited; what it may not be is made
     * without saying so.
     */
    @Test
    void everyKindOfGapIsOneOfTheseAndNoneIsAboutACombination() {
        List<String> every = new ArrayList<>();
        walk(About.class, every);

        // Two of them twice: a rule with no line and a rule nothing classified are each reached
        // under two of the seals above, and the walk says so rather than folding what it found.
        assertEquals(List.of(
                "ACaseNoRowExpects", "ACaseNothingWasSeenToProduce", "ACaseNoRowAppliesItTo",
                "AClassNoRowIsIn", "APointOfABorder", "APointOfADeclaredBorder",
                // An arm and a row at it: what a row is owed at is the arm either way, and the
                // second says the row is written and its answer is not.
                "AnArmNoRowGoesThrough", "ARowAtAnArmAwaitsItsAnswer",
                "ARuleWithoutALine", "ARuleNothingClassified",
                "AQuestionNothingAnswered", "ARuleWithoutALine", "ARuleNothingClassified",
                "APositionThisCouldNotRead", "APositionNoLineDivides",
                "APositionReadWiderThanItsRules", "APositionWhoseRulesWereNotReached",
                // A row owed an answer, which is owed at the row. What it is owed at is the thing
                // somebody wrote, so it is no more about a combination than an arm is — and it is
                // the one kind here that is read off the source rather than measured.
                "AnUnansweredRow"), every,
                "a kind of gap this compiler finds that this law says nothing about");
    }

    /** Every kind under {@code from}, however many seals deep it is written. */
    private static void walk(Class<?> from, List<String> into) {
        if (from.isSealed()) {
            for (Class<?> arm : from.getPermittedSubclasses()) {
                walk(arm, into);
            }
            return;
        }
        into.add(from.getSimpleName());
    }

    /**
     * And nothing a row can be offered for is a combination either.
     *
     * <p>The other half of what this law says it rests on, and the half a check over the findings
     * alone leaves open: a change that offered a row for a combination without giving it a finding
     * would say a row is owed at one in the only way an author meets — the command that hands them
     * the row to write.
     *
     * <p>Read from what says what a row can be offered for, which is a sum of three and none of
     * them is a relation between two positions.
     */
    @Test
    void nothingARowIsOfferedForIsACombination() {
        List<String> every = new ArrayList<>();
        walk(souther.compiler.partition.ObligationIdentity.class, every);

        assertEquals(List.of("OfALine", "OfAnArm", "OfAClass"), every,
                "a thing a row can be offered for that this law says nothing about");
    }

    /**
     * And the pair space carries no finding of its own.
     *
     * <p>The other half. A measure may answer for gaps it did not name — the classes measure finds
     * a class no row is in — so what says a combination is owed nothing is that the space itself
     * hands none over.
     */
    @Test
    void thePairSpaceHandsOverNoFinding() {
        for (java.lang.reflect.RecordComponent part
                : PartitionEvidence.PairSpace.class.getRecordComponents()) {
            assertFalse(Adequacy.Finding.class.isAssignableFrom(part.getType()),
                    () -> "the pair space carries " + part.getName() + ", which is a finding");
        }
    }
}
