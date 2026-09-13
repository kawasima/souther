package souther.compiler.partition;

import souther.compiler.regex.Meter;

/**
 * The words an adequacy document writes for what gave an offer no value.
 *
 * <p>Between the two vocabularies and belonging to neither, the way {@link ReportedReason} is.
 * {@link StringOfferShortfall} records what this compiler was doing and what refused it;
 * a consumer reads what the shortfall is attributed to and what stopped that. A document written
 * by spelling the shapes of {@link StringOfferShortfall.Subject} would be a contract that moves
 * whenever those are rearranged, and the words a consumer keys on are not the compiler's to
 * rearrange.
 *
 * <p>Two questions and two fields, because they are not one answer. What the shortfall is
 * attributed to says where an author goes; what stopped it says what they do there — and a rule
 * this compiler could not read and a rule whose machine it would not build are one attribution and
 * two pieces of work.
 */
public final class ReportedShortfall {

    private ReportedShortfall() {
    }

    /**
     * What a shortfall is attributed to.
     *
     * <p>A rule is the author's to rewrite. What the rules leave between them is nobody's rule and
     * is a size this compiler declined to work out, so an author sent to either of the rules would
     * be sent to one that reads perfectly. Composing a value is the whole question's allowance and
     * is about no rule of it at all.
     */
    public enum Attribution {
        A_RULE,
        THE_RULES_TOGETHER,
        COMPOSING_A_VALUE
    }

    /**
     * Which limit refused it, where a limit did.
     *
     * <p>Kept apart from the reasons a reading stops. A machine larger than a machine may be is a
     * pattern somebody can write smaller; an allowance already spent is not, and the same pattern
     * asked for first would have been built. Under one word, a consumer counting what a wider
     * allowance would reach would count both.
     */
    public enum Limit {
        A_MACHINE_LARGER_THAN_ONE_MAY_BE,
        WHAT_COMPOSING_A_VALUE_MAY_SPEND
    }

    /** What {@code of} is attributed to, in the word a document writes for it. */
    public static Attribution attribution(StringOfferShortfall.Subject of) {
        return switch (of) {
            case StringOfferShortfall.Subject.ARule _ -> Attribution.A_RULE;
            case StringOfferShortfall.Subject.WhatTheyLeaveTogether _ ->
                    Attribution.THE_RULES_TOGETHER;
            case StringOfferShortfall.Subject.ComposingAValue _ -> Attribution.COMPOSING_A_VALUE;
        };
    }

    /** The same of what refused a value, where what refused it was a limit. */
    public static Limit limit(Meter.Stopped stopped) {
        return switch (stopped) {
            case ONE_MACHINE -> Limit.A_MACHINE_LARGER_THAN_ONE_MAY_BE;
            case THE_ANSWER -> Limit.WHAT_COMPOSING_A_VALUE_MAY_SPEND;
        };
    }
}
