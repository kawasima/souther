package souther.compiler.publish;

import souther.compiler.query.Adequacy;
import souther.compiler.query.DecisionEvidence;
import souther.compiler.query.InputCaseEvidence;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.NotMeasuredReason;
import souther.compiler.query.NothingWasAsked;
import souther.compiler.query.OutputCaseEvidence;
import souther.compiler.query.PartitionEvidence;

/**
 * What a document calls the thing a measure nobody made was waiting for.
 *
 * <p>An enum for the reason {@link AdequacyOpeningWord} is one: the shipped schema names these
 * words in its own file and is held against this, so a word added here has to be taught to the
 * schema before it can be written. And every reason a verdict can rest on has one of these, which
 * is asked separately — the two together are what say that the words a document writes and the
 * words it allows are the same words.
 *
 * <p>Fewer words than there are reasons. Nine measures can go unmade and each says which of these
 * three it was waiting for — a measure with no rows to read is the same news whichever measure it
 * was, and which measure it was is said where the measure is. So this is the vocabulary and not the
 * list of things that produce it, which is why a sequence of these can be put in an order at all.
 */
public enum NotMeasuredWord {

    /** The model has no rows this measure reads, so nothing was measured. An author writes one. */
    NO_ROWS,

    /** This build did not ask for the measure. A run asks for it. */
    NOT_ASKED,

    /** This build asked for coverage and not for the arms, which are measured under their own
     *  allowance. */
    ARMS_NOT_ASKED;

    /**
     * The word for one reason a measure was never made.
     *
     * <p>A {@code switch} with no {@code default} over both the arms and their constants, so a
     * measure that can go unmade for a new reason has to be given a word here, and the word has to
     * be one the schema allows. Read off {@code name()} instead, a constant renamed for whoever
     * reads the code would rename a word in every document this compiler writes.
     */
    public static NotMeasuredWord of(NotMeasuredReason reason) {
        return switch (reason) {
            // Two arms carry more than one constant, and the constants are what the words are of:
            // a measure nobody asked for and one there were no rows for are different news out of
            // one enum.
            case Adequacy.BranchEvidence.NotAsked it -> switch (it) {
                case NOT_ASKED -> NOT_ASKED;
                case NO_ROWS -> NO_ROWS;
            };
            case ItemAssessment.Coverage.NotAsked it -> switch (it) {
                case NOT_ASKED -> NOT_ASKED;
                case ARMS_NOT_ASKED -> ARMS_NOT_ASKED;
                case NO_ROWS -> NO_ROWS;
            };
            // A reading of the rows this build was never going to make. A verdict is not held open
            // by one — it is taken out of what the verdict rests on before any of this is reached —
            // so there is no word for it, and giving it one would promise a word nothing writes.
            case Adequacy.RowReading.NotAsked it -> throw new IllegalArgumentException(
                    "a reading nobody asked for does not hold a verdict open: " + it);
            case DecisionEvidence.NotAsked it -> switch (it) {
                case NOT_ASKED -> NOT_ASKED;
            };
            case NothingWasAsked _ -> NOT_ASKED;
            case Adequacy.SignatureEvidence.NoRows _ -> NO_ROWS;
            case InputCaseEvidence.NoRows _ -> NO_ROWS;
            case OutputCaseEvidence.NoRows _ -> NO_ROWS;
            case PartitionEvidence.AxisCoverage.NoRows _ -> NO_ROWS;
            case PartitionEvidence.PairSpace.NoRows _ -> NO_ROWS;
        };
    }
}
