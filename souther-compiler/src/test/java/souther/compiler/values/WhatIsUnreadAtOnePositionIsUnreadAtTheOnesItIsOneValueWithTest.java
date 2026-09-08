package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule that went unread at one position, read as a rule about every position holding that value.
 *
 * <p>Whether a reading speaks for what stands at a position is a question about the value, and a
 * value several positions are held as one is one value — so a rule nobody could read at any of them
 * is a rule about all of them. Answered per position, a reading reported that every rule about
 * {@code r} had been taken in while handing it the answer the unread rule about {@code p} was going
 * to narrow.
 *
 * <p>And the two halves of the answer — whether it speaks for the position and what stopped it —
 * are one derivation. Written apart, the first was asked of the value and the second read the
 * position's own reasons, so a position came out unanswerable with nothing to say for it.
 */
class WhatIsUnreadAtOnePositionIsUnreadAtTheOnesItIsOneValueWithTest {

    private static Allowance<String> allowing() {
        return AsACompilationAllows.forAdmittedValues();
    }

    /** A description worked out, which is how a reading is come by. */
    private static AdmissibleValues<String> built(PlannedValues<String> planned) {
        return planned.resolve(allowing()).values();
    }

    /** An equality between two positions, worked out. */
    private static AdmissibleValues<String> heldAsOne(String here, String there) {
        return built(PlannedValues.holdingAsOne(here, there));
    }

    /** A rule nothing could read, worked out. */
    private static AdmissibleValues<String> unread(Set<String> named, UnreadReason why) {
        return built(PlannedValues.unreadable(named, why));
    }

    /** A rule nothing could read at one position, beside an equality holding it with another. */
    private static AdmissibleValues<String> unreadAtOneOfAPair() {
        return heldAsOne("p", "r")
                .meet(unread(Set.of("p"), UnreadReason.FORM_NOT_READ), allowing());
    }

    /** The position the rule was written at is one the reading does not speak for. */
    @Test
    void thePositionTheRuleWasWrittenAtIsNotSpokenFor() {
        AdmissibleValues<String> reading = unreadAtOneOfAPair();

        assertFalse(reading.speaksFor("p"));
        assertEquals(List.of(UnreadReason.FORM_NOT_READ), reading.whyUnread("p"));
    }

    /**
     * And so is the position held as one value with it, with the same reason.
     *
     * <p>This is the reading the equality is for. Nothing was written about {@code r}, and what
     * stands there is what stands at {@code p} — so a rule about {@code p} nobody could read is
     * one this reading cannot say it took in about {@code r} either.
     */
    @Test
    void andSoIsThePositionHeldAsOneValueWithIt() {
        AdmissibleValues<String> reading = unreadAtOneOfAPair();

        assertFalse(reading.speaksFor("r"), "r holds whatever p holds");
        assertEquals(List.of(UnreadReason.FORM_NOT_READ), reading.whyUnread("r"),
                "and is short of it for the same reason");
    }

    /** Whether it speaks for a position and what stopped it are one answer, so neither is empty
     *  while the other is not. */
    @Test
    void whetherItSpeaksAndWhyAreOneAnswer() {
        AdmissibleValues<String> reading = unreadAtOneOfAPair();

        for (String position : List.of("p", "r", "s")) {
            assertEquals(reading.speaksFor(position), reading.whyUnread(position).isEmpty(),
                    () -> "at " + position);
        }
    }

    /** A position no equality reached takes nothing from the pair beside it. */
    @Test
    void aPositionOutsideTheBlockIsUntouched() {
        AdmissibleValues<String> reading = unreadAtOneOfAPair();

        assertTrue(reading.speaksFor("s"));
        assertEquals(List.of(), reading.whyUnread("s"));
    }

    /**
     * And whether what stands at a position is the whole of what the rules leave it is the
     * block's answer too.
     *
     * <p>Positions held as one value have one machine between them, so one of them cannot be exact
     * while the other is: read per position, a block whose machine was given up on would report
     * the position the widening was recorded against as wide and the position beside it as exact,
     * while both are handed the same set.
     */
    @Test
    void whetherWhatStandsIsExactIsTheBlocksAnswer() {
        // A machine somebody gave up on at one of the pair, which is what leaves a block wide: the
        // description says so of the position it was written at, and the reading it comes to says
        // it of the block that position is on.
        AdmissibleValues<String> wide = built(PlannedValues.<String>holdingAsOne("p", "r")
                .alsoStanding(Standing.of(Set.of("p"), UnreadReason.EXACT_VALUES_TOO_COSTLY)));

        assertFalse(wide.projectionExactAt("p"));
        assertFalse(wide.projectionExactAt("r"), "one machine, one answer about it");
        assertTrue(wide.projectionExactAt("s"), "and a position outside the block is untouched");
    }

    /**
     * Reasons at several positions of one block are read in the order the rules were written.
     *
     * <p>Which is recoverable because what the reading was handed is kept whole: one entry per rule
     * it gave up on, holding every position that rule named. Filed by position, the order survives
     * only inside one place, and a reader shown two places would be shown them in an order this
     * compiler invented.
     */
    @Test
    void reasonsAtSeveralPositionsAreReadInTheOrderTheRulesWereWritten() {
        AdmissibleValues<String> reading = heldAsOne("p", "r")
                .meet(unread(Set.of("r"), UnreadReason.RELATES_TWO_POSITIONS), allowing())
                .meet(unread(Set.of("p"), UnreadReason.FORM_NOT_READ), allowing());

        assertEquals(List.of(UnreadReason.RELATES_TWO_POSITIONS, UnreadReason.FORM_NOT_READ),
                reading.whyUnread("p"), "the rule about r was written first");
        assertEquals(reading.whyUnread("p"), reading.whyUnread("r"),
                "and both of them are short of the same rules");
    }

    /** And the other way round, so the order is the rules' and not the positions'. */
    @Test
    void andTheOtherWayRoundWhenTheRulesAreWrittenTheOtherWayRound() {
        AdmissibleValues<String> reading = heldAsOne("p", "r")
                .meet(unread(Set.of("p"), UnreadReason.FORM_NOT_READ), allowing())
                .meet(unread(Set.of("r"), UnreadReason.RELATES_TWO_POSITIONS), allowing());

        assertEquals(List.of(UnreadReason.FORM_NOT_READ, UnreadReason.RELATES_TWO_POSITIONS),
                reading.whyUnread("p"));
    }
}
