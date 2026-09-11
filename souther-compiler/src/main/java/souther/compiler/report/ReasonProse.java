package souther.compiler.report;

import souther.compiler.observe.MeasureReason;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BoundaryDerivation;
import souther.compiler.query.BoundaryForMeasurement;
import souther.compiler.query.DecisionEvidence;
import souther.compiler.query.FailureReason;
import souther.compiler.query.InputCaseEvidence;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.NoFeasibleInput;
import souther.compiler.query.NotApplicableReason;
import souther.compiler.query.NotMeasuredReason;
import souther.compiler.query.NothingWasAsked;
import souther.compiler.query.OutputCaseEvidence;
import souther.compiler.query.PartitionDerivation;
import souther.compiler.query.PartitionEvidence;

/**
 * What a person is told about a measure that has no number.
 *
 * <p>The whole of the report's vocabulary for a reason, in one place, which is where every other
 * surface a reason reaches already spells it: the document writes {@code reason.name()} lowercased,
 * and the fact one reaches an author with is chosen inside the reason's own {@code switch}. Written
 * instead where each line is printed, a vocabulary gives a reason arriving at a line nobody wrote a
 * word for the word of whichever arm its type happens to match.
 *
 * <p><b>The state and not its type.</b> A reason is a constant and a reason type may hold more than
 * one, so an arm binding the type covers every constant that type will ever have — and a behavior
 * measured short of either half of its boundary is told its signature could not be read, when for
 * one of the two the signature is in hand. Every enum here is read through to its constants by a
 * {@code switch} expression with no {@code default}, whether it has two constants today or one:
 * what closes this is that adding a constant stops the compiler, and an enum spelled as one arm
 * today is the enum a second constant is added to tomorrow.
 *
 * <p><b>Which of the three families a reason is in decides how the sentence opens.</b> {@code not
 * applicable} asks nothing of an author and {@code not measured} says what to do
 * (spec §example-report-vocabulary), and that is what {@link NotApplicableReason} and the two
 * beside it are. Chosen at each line instead, the opening is a second reading of the reason, held
 * to the family by whoever writes the line.
 *
 * <p>Each family is asked in its own {@code switch} over a sealed interface, so a reason type added
 * to one of them stops the compiler here as well. What is left open is {@link MeasureReason}
 * itself: a type reaching it without passing through one of the three has no sentence, and there is
 * nothing below to ask for one.
 *
 * <p><b>Words, and nothing about where they are said.</b> Whether a reason is a fact about the run
 * or about the behavior decides whether a surface gives it a line of its own, and it is the
 * reason's answer ({@link MeasureReason#about()}) rather than a second column here. Held beside the
 * words, one fact about a constant was written down in as many tables as there are readers, and the
 * fold over the readings of one line — which needs the same fact to tell two readings saying one
 * thing from two readings saying two — had no table to read and worked from a different question.
 *
 * @param introduction how the sentence opens, which the family settles
 * @param said         what this state says
 */
record ReasonProse(Introduction introduction, String said) {

    /** How a sentence opens, which is the whole of what the family says to a reader. */
    enum Introduction {

        NOT_APPLICABLE("not applicable"),
        NOT_MEASURED("not measured");

        private final String word;

        Introduction(String word) {
            this.word = word;
        }
    }

    /**
     * What the report says about this reason, and what it is about.
     *
     * <p>The three arms are tried in the order they are written, and that is an answer only because
     * a reason is in one family. Two sealed interfaces may permit one type and that type may
     * implement both, which leaves every {@code switch} over either of them exhaustive and this
     * reader answering from whichever arm comes first — so the families being disjoint is asked of
     * the families themselves, by {@code AReasonIsInOneFamilyAndNotTwoTest}.
     */
    static ReasonProse of(MeasureReason reason) {
        return switch (reason) {
            case NotApplicableReason it ->
                    new ReasonProse(Introduction.NOT_APPLICABLE, nothingToBeAbout(it));
            case NotMeasuredReason it ->
                    new ReasonProse(Introduction.NOT_MEASURED, neverMade(it));
            case FailureReason it ->
                    new ReasonProse(Introduction.NOT_MEASURED, couldNotBeFinished(it));
            default -> throw new IllegalStateException(
                    "a reason in none of the three families a measure answers with, so nothing"
                            + " here says what it tells a reader: " + reason);
        };
    }

    /** The whole sentence, for a line whose subject is this measure and nothing else. */
    String sentence() {
        return introduction.word + " (" + clause() + ")";
    }

    /**
     * The clause alone, for a line that is about something else and says what stopped this.
     *
     * <p>An obligation and an axis are printed because they are owed, and what a reading of them
     * met is a clause under a line that would be there without it. The opening belongs to a line
     * whose whole subject is one measure.
     */
    String clause() {
        return said;
    }

    private static String nothingToBeAbout(NotApplicableReason reason) {
        return switch (reason) {
            case Adequacy.BranchEvidence.NoArms it -> switch (it) {
                case NO_BODY -> "this behavior has no body";
                case NO_ARM_OBLIGATIONS -> "this body owes no arm";
            };
            case Adequacy.SignatureEvidence.NotASum it -> switch (it) {
                case NOT_A_SUM -> "this behavior's output is not a sum";
            };
            // Said of the rules and not of their absence, here and at the partition below. A
            // behavior whose only rule about a pair of positions relates them has rules — printed a
            // line above, by name — and they divide no position and draw no line; a sentence saying
            // the model has none would read as contradicting the rule beside it.
            case BoundaryDerivation.NoRuleDrawsALine _ ->
                    "the rules of this behavior draw no line";
            case BoundaryDerivation.NoSubject it -> switch (it) {
                case NO_SUBJECT -> "this behavior is measured at its stages";
            };
            case InputCaseEvidence.NotASum it -> switch (it) {
                case NOT_A_SUM -> "this position is one data rather than a sum";
            };
            case NoFeasibleInput _ ->
                    "the rules reaching this behavior's input leave it no value";
            case OutputCaseEvidence.NotASum it -> switch (it) {
                case NOT_A_SUM -> "what this behavior answers with is not a sum";
            };
            case PartitionDerivation.NoSubject it -> switch (it) {
                case NO_SUBJECT -> "this behavior is measured at its stages";
            };
            case PartitionDerivation.NothingIsDivided _ ->
                    "the rules of this behavior divide no position";
        };
    }

    private static String neverMade(NotMeasuredReason reason) {
        return switch (reason) {
            case Adequacy.BranchEvidence.NotAsked it -> switch (it) {
                case NOT_ASKED -> "the build did not ask for the arms";
                case NO_ROWS -> "no row names this behavior";
            };
            case Adequacy.RowReading.NotAsked it -> switch (it) {
                case ROWS_NOT_ASKED -> "this build does not read rows";
            };
            case DecisionEvidence.NotAsked it -> switch (it) {
                case NOT_ASKED -> "the build did not ask where the rows went";
            };
            case Adequacy.SignatureEvidence.NoRows it -> switch (it) {
                case NO_ROWS -> "no row names this behavior";
            };
            case InputCaseEvidence.NoRows it -> switch (it) {
                case NO_ROWS -> "no row names this behavior";
            };
            case ItemAssessment.Coverage.NotAsked it -> switch (it) {
                case NOT_ASKED -> "nothing was asked for";
                case ARMS_NOT_ASKED -> "the arms were not asked for";
                case NO_ROWS -> "no row names this behavior";
            };
            case NothingWasAsked it -> switch (it) {
                case NOT_ASKED -> "nothing was asked for";
            };
            case OutputCaseEvidence.NoRows it -> switch (it) {
                case NO_ROWS -> "no row names this behavior";
            };
            case PartitionEvidence.AxisCoverage.NoRows it -> switch (it) {
                case NO_ROWS -> "no row names this behavior";
            };
            case PartitionEvidence.PairSpace.NoRows it -> switch (it) {
                case NO_ROWS -> "no row names this behavior";
            };
        };
    }

    private static String couldNotBeFinished(FailureReason reason) {
        return switch (reason) {
            // The model says this behavior writes a body. What it owes is unknown rather than
            // nothing, which is the difference the line saying this exists to show.
            case Adequacy.BranchEvidence.Unelaborated it -> switch (it) {
                case BODIES_NOT_ELABORATED -> "this module's bodies were not elaborated";
            };
            case Adequacy.BranchEvidence.Unreadable it -> switch (it) {
                case UNREADABLE -> "the arms could not be read";
            };
            case Adequacy.RowReading.Unavailable it -> switch (it) {
                case ROWS_UNAVAILABLE -> "nothing came back from the rows";
            };
            case DecisionEvidence.Unreadable it -> switch (it) {
                case THE_BODY_WAS_NOT_READ -> "this behavior's body was not elaborated";
                case NO_ROW_CAME_BACK -> "no row of this behavior came back to be read";
                case THE_ROWS_CARRY_NO_ACCOUNT -> "the rows carry no account of where they went";
                case NO_RULE_IS_RECOGNISABLE ->
                        "no run through any of these rules can be recognised";
            };
            case BoundaryDerivation.TheReadingDidNotRunOut it -> switch (it) {
                case THE_READING_DID_NOT_RUN_OUT -> "no line was derived at any position";
            };
            // The two halves of one boundary, and an author acts on them in different places. A
            // behavior whose boundary was not derived has a name in its own declaration that
            // resolved to nothing; this one's declaration is whole and what went unread is its
            // input, which a hole anywhere in the module refuses the reading of. Which name
            // resolved to nothing is reported where it was written, on the line the author edits.
            case BoundaryForMeasurement.NotDerived it -> switch (it) {
                case BEHAVIOR_BOUNDARY_NOT_DERIVED ->
                        "this behavior's signature could not be read";
                case BEHAVIOR_INPUT_NOT_READ -> "what this behavior takes was not read";
            };
            case ItemAssessment.Coverage.CouldNotAsk it -> switch (it) {
                case ARMS_UNREADABLE -> "the arms could not be measured";
            };
            case PartitionDerivation.TheReadingDidNotRunOut it -> switch (it) {
                case THE_READING_DID_NOT_RUN_OUT ->
                        "no partition axis was derived at any position";
            };
        };
    }
}
