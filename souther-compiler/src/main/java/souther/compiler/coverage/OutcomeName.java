package souther.compiler.coverage;

import souther.compiler.types.SourceConstruct;

/**
 * What a reader is told one outcome of one construct is.
 *
 * <p>The projection of the pair — the construct a source wrote, and what one way through it means —
 * onto the vocabulary the reports share. A distinction and not a word: what a document spells it and
 * what a sentence in the reader's language calls it are two renderings of this, and both are chosen
 * where the reader is.
 *
 * <p>Every pair passes through {@link #of}, which is the one place the admissible pairs are written
 * down. A construct or an outcome added later stops there rather than falling into a default and
 * arriving at a reader named after whichever arm the code was written next to.
 */
public enum OutcomeName {

    /** The arm an {@code if} takes when its condition holds. */
    THEN,

    /** The arm the author wrote {@code else} on — an {@code if}'s, and a {@code guard}'s departure. */
    ELSE,

    /**
     * The body past a {@code guard} whose condition held.
     *
     * <p>Not a {@code then}. The author wrote one arm of the fork and the compiler supplied this one
     * out of the rest of the block, so there is no word in the source to quote.
     */
    CONTINUED,

    /** The element a comprehension yields when its condition holds. */
    KEPT,

    /** The empty list a comprehension yields when it does not. */
    DROPPED,

    /** One arm of a {@code match}. */
    CASE,

    /** The way taken when an attempted construction built its value. */
    CONSTRUCTED,

    /** The way taken when a clause refused it. */
    DEPARTURE,

    /** One comparison of a condition, which is a site and not an arm. */
    COMPARISON;

    /**
     * Whether a document names this among the arms a branch measure counts.
     *
     * <p>The image of {@link SourceOutcome.Arm} under this projection, and held to it by
     * {@code AnOutcomeIsNamedByWhatWasWrittenTest}, which walks the admissible pairs and asks both.
     */
    public boolean isArm() {
        return this != COMPARISON;
    }

    /**
     * What to call {@code outcome} under {@code construct}.
     *
     * @throws IllegalArgumentException where no construct of the language has that outcome — a
     *                                  comprehension attempting a construction, a {@code match}
     *                                  settling a condition. The pair is what carries the meaning, so
     *                                  a pair nothing can be written as is a walk that has put an
     *                                  outcome on the wrong construct
     */
    public static OutcomeName of(SourceConstruct construct, SourceOutcome outcome) {
        return switch (outcome) {
            case SourceOutcome.Held(SourceOutcome.HeldBy.Condition _) -> switch (construct) {
                case IF -> THEN;
                case GUARD -> CONTINUED;
                case COMPREHENSION -> KEPT;
                case MATCH, BINARY, CALL, COLLECTION_LITERAL, NOT_WRITTEN ->
                        refuse(construct, outcome);
            };
            case SourceOutcome.Failed(SourceOutcome.FailedBy.Condition _) -> switch (construct) {
                case IF, GUARD -> ELSE;
                case COMPREHENSION -> DROPPED;
                case MATCH, BINARY, CALL, COLLECTION_LITERAL, NOT_WRITTEN ->
                        refuse(construct, outcome);
            };
            // An attempted construction is a shape either of the two conditionals may be written in,
            // and no other construct has one. What the value was attempted under is said by the
            // construct beside this rather than by a second word here.
            case SourceOutcome.Held(SourceOutcome.HeldBy.Construction _) -> switch (construct) {
                case IF, GUARD -> CONSTRUCTED;
                case COMPREHENSION, MATCH, BINARY, CALL, COLLECTION_LITERAL, NOT_WRITTEN ->
                        refuse(construct, outcome);
            };
            case SourceOutcome.Failed(SourceOutcome.FailedBy.Construction _) -> switch (construct) {
                case IF, GUARD -> DEPARTURE;
                case COMPREHENSION, MATCH, BINARY, CALL, COLLECTION_LITERAL, NOT_WRITTEN ->
                        refuse(construct, outcome);
            };
            case SourceOutcome.Matched _ -> switch (construct) {
                case MATCH -> CASE;
                case IF, GUARD, COMPREHENSION, BINARY, CALL, COLLECTION_LITERAL, NOT_WRITTEN ->
                        refuse(construct, outcome);
            };
            // The one place the two axes meet without naming each other: what was written is a
            // binary expression, and being read as a comparison a row can reach is what happened to
            // it. Every other binary expression the source wrote has no outcome at all, and neither
            // has any application — a rule read off one of those divides a position into classes
            // and leaves no run to record.
            case SourceOutcome.Compared _ -> switch (construct) {
                case BINARY -> COMPARISON;
                case IF, GUARD, COMPREHENSION, MATCH, CALL, COLLECTION_LITERAL, NOT_WRITTEN ->
                        refuse(construct, outcome);
            };
        };
    }

    private static OutcomeName refuse(SourceConstruct construct, SourceOutcome outcome) {
        throw new IllegalArgumentException(
                "no construct of the language has this outcome: " + construct + " with " + outcome);
    }
}
