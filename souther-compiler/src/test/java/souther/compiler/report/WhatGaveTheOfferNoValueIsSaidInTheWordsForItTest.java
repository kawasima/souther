package souther.compiler.report;

import org.junit.jupiter.api.Test;

import souther.compiler.inputs.BlockReason;
import souther.compiler.partition.StringOfferShortfall;
import souther.compiler.regex.Meter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why a rule gave the offer no value is said in words that tell the ways apart.
 *
 * <p>Three things an author does about it: rewrite a rule this compiler does not read, write a
 * pattern that is a smaller machine, or allow more. A sentence that said only that something here
 * could not be worked out sends two of the three to the wrong place.
 *
 * <p>Held here rather than through a compile, because two of the ways are reached by spending an
 * allowance down and a fixture that spends it charges every run of this suite for a sentence. What
 * a compile holds is that the ways are reached at all, and that the block prints them beside what
 * the search came to ({@code WhatWasTriedIsNotEverythingWhereARuleComposedNothing}).
 */
class WhatGaveTheOfferNoValueIsSaidInTheWordsForItTest {

    private static String said(StringOfferShortfall.Why why) {
        return GeneratedRows.becauseOf(why);
    }

    /**
     * A rule read no further and one read too deeply are two sentences, because a wider run reaches
     * the second and never the first.
     */
    @Test
    void theWaysARuleGoesUnreadKeepTheirOwnWords() {
        String unread = said(new StringOfferShortfall.Why.NotRead(
                new BlockReason.UnreadValueRule()));
        String nested = said(new StringOfferShortfall.Why.NotRead(
                new BlockReason.PatternTooDeeplyNested()));

        assertNotEquals(unread, nested,
                "one sends an author to the construct nothing here enters and the other to the"
                        + " brackets, and a run allowed more reaches only the second");
        assertTrue(unread.contains("does not read"), unread);
        assertTrue(nested.contains("deeply"), nested);
    }

    /** And the two limits on composing a value are two more, for the same reason. */
    @Test
    void theTwoLimitsOnComposingAValueAreNotOneSentence() {
        String machine = said(new StringOfferShortfall.Why.TooCostly(Meter.Stopped.ONE_MACHINE));
        String allowance = said(new StringOfferShortfall.Why.TooCostly(Meter.Stopped.THE_ANSWER));

        assertNotEquals(machine, allowance,
                "one is a machine larger than a machine may be and the other is an allowance"
                        + " already spent, and the same pattern asked for first would have built");
        assertTrue(machine.contains("larger machine"), machine);
        assertTrue(allowance.contains("spend"), allowance);
    }

    /**
     * And a rule this compiler could not read is said in the words the document already has for
     * that, rather than in words of this surface's own.
     *
     * <p>Held because the two surfaces are read together: an author meets the rule in the block and
     * meets it again in the account, and two spellings of one reading read as two readings.
     */
    @Test
    void aReadingThatStoppedIsSaidInTheDocumentsOwnWords() {
        assertEquals("gave none of them: " + AdequacyReport.whyUnread(
                        souther.compiler.partition.ReportedReason.of(
                                new BlockReason.UnreadValueRule())),
                said(new StringOfferShortfall.Why.NotRead(new BlockReason.UnreadValueRule())));
    }
}
