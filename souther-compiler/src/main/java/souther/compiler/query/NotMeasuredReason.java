package souther.compiler.query;

import souther.compiler.observe.MeasureReason;

/**
 * Why a measure that could have been made was not.
 *
 * <p>Says what to do: write a row, ask for the arms. Nothing went wrong — the measurement was never
 * started, so there is nothing it went without and nothing here weakens a measure above it.
 *
 * <p>That last part is what separates this from {@link FailureReason}. A measurement nobody asked
 * for and one that was asked for and could not read what it needed both come back with no number,
 * and only the second means the numbers around it are worth less than they look.
 *
 * <p>Closed, and here for the reason {@link FailureReason} is: a sum may name its arms only where
 * they are written, and each of these is written beside the measure that has it.
 */
public sealed interface NotMeasuredReason extends MeasureReason
        permits Adequacy.BranchEvidence.NotAsked,
                Adequacy.RowReading.NotAsked,
                Adequacy.SignatureEvidence.NoRows,
                DecisionEvidence.NotAsked,
                InputCaseEvidence.NoRows,
                ItemAssessment.Coverage.NotAsked,
                NothingWasAsked,
                OutputCaseEvidence.NoRows,
                PartitionEvidence.AxisCoverage.NoRows,
                PartitionEvidence.PairSpace.NoRows {}
