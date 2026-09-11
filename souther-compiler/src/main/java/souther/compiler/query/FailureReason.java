package souther.compiler.query;

import souther.compiler.observe.MeasureReason;

/**
 * Why a measurement that was started could not be finished.
 *
 * <p>The state that had no word. A measure whose rules this compiler could not read and one nobody
 * wrote a row for were both {@code NOT_MEASURED}, and the difference — which of them leaves every
 * number around it weaker than it looks — was recovered by asking the reason a boolean
 * ({@code somethingWasUnreadable}). A reader who did not know to ask got the wrong one.
 *
 * <p>A measurement that failed carries what it went without beside this, the way a partial one does.
 * The reason says what kind of nothing came back; the weakening says what would have been needed.
 *
 * <p><b>Closed, and here because that is where it can be.</b> Which reasons a measure has stays the
 * measure's own business, so every one of these is declared beside the measure that has it — and a
 * sum may name its arms only where they are written. So the vocabulary lives with the questions it
 * is answered by. Left open, what stands here would be settled by nothing, and a reader of an answer
 * could not say what it may hold without running one.
 */
public sealed interface FailureReason extends MeasureReason
        permits Adequacy.BranchEvidence.Unelaborated,
                Adequacy.BranchEvidence.Unreadable,
                Adequacy.RowReading.Unavailable,
                DecisionEvidence.Unreadable,
                BoundaryDerivation.TheReadingDidNotRunOut,
                BoundaryForMeasurement.NotDerived,
                ItemAssessment.Coverage.CouldNotAsk,
                PartitionDerivation.TheReadingDidNotRunOut {}
