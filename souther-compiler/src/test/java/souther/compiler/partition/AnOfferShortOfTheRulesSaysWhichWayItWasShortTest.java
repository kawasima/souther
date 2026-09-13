package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.TermPath;
import souther.compiler.regex.Meter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sentence beside a block's word says which way the offer was short, and the ways do not share
 * a sentence.
 *
 * <p>Three ways and three things an author does: rewrite a rule this compiler cannot read, write a
 * pattern that is a smaller machine, or allow more. Held here rather than through a compile,
 * because two of the three are reached by spending an allowance down and a fixture that spends it
 * charges every run of this suite for a sentence.
 *
 * <p>Which leaves what a compile does hold: that the ways are reached at all, and that the word the
 * block writes is the one that does not claim the refusals were of everything
 * ({@code WhatWasTriedIsNotEverythingWhereARuleComposedNothing}). The two together are the whole of
 * it — that each way happens, and that each says its own thing.
 */
class AnOfferShortOfTheRulesSaysWhichWayItWasShortTest {

    private static final TermPath AT = TermPath.of("t").then("code");

    private static String saidOf(StringOfferShortfall offered) {
        SequencedMap<TermPath, StringOfferShortfall> at = new LinkedHashMap<>();
        at.put(AT, offered);
        return Generator.whatWasNotOffered(at);
    }

    private static StringOfferShortfall stoppedBy(Meter.Stopped stopped) {
        return new StringOfferShortfall(List.of(),
                List.of(new StringOfferShortfall.WitnessOfferStopped(stopped)));
    }

    @Test
    void aRuleThatWasNotReadSaysSoAndNamesThePosition() {
        String said = saidOf(new StringOfferShortfall(
                List.of(new BlockReason.UnreadValueRule()), List.of()));

        assertTrue(said.contains("`t.code`"), said);
        assertTrue(said.contains("could not read"), said);
    }

    /**
     * And the two limits are two sentences, because one is a pattern to write smaller and the other
     * is a figure to raise.
     */
    @Test
    void theTwoLimitsOnComposingAValueAreNotOneSentence() {
        String machine = saidOf(stoppedBy(Meter.Stopped.ONE_MACHINE));
        String allowance = saidOf(stoppedBy(Meter.Stopped.THE_ANSWER));

        assertTrue(machine.contains("`t.code`"), machine);
        assertTrue(allowance.contains("`t.code`"), allowance);
        assertNotEquals(machine, allowance,
                "one is a machine larger than a machine may be and the other is an allowance"
                        + " already spent, and the same pattern asked for first would have built");
        assertTrue(machine.contains("larger machine"), machine);
        assertTrue(allowance.contains("spent"), allowance);
    }

    /**
     * And a position short both ways says both, because an author who rewrites the rule meets the
     * allowance next and one who allows more meets the rule.
     */
    @Test
    void aPositionShortBothWaysSaysBoth() {
        String said = saidOf(new StringOfferShortfall(
                List.of(new BlockReason.UnreadValueRule()),
                List.of(new StringOfferShortfall.WitnessOfferStopped(Meter.Stopped.THE_ANSWER))));

        assertTrue(said.contains("could not read"), said);
        assertTrue(said.contains("spent"), said);
    }
}
