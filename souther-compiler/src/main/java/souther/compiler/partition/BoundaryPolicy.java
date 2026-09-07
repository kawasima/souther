package souther.compiler.partition;

import java.util.Optional;

/**
 * Whether the rules of a model are read off one comparison, and why not where they are not.
 *
 * <p><b>The rule, said in what a behavior does rather than in what a body looks like.</b> A
 * comparison is a boundary rule where its truth is on a live flow to what the behavior answers with,
 * and where one run passes it at most once. Nothing about the construct it is written in is part of
 * that. A comparison tested by an {@code if}, given a name a line above the fork that tests it,
 * returned as the behavior's answer, or written into a field of the answer's data is one rule put to
 * four uses, and a model divides its input at the same value in all four — so naming a truth is not
 * a statement about what a model divides, and neither is spelling one out.
 *
 * <p>It was written as {@code directly under a fork, through &&  and ||} because a fork was for a
 * long time the only thing that could measure a comparison: reaching an arm was the evidence that
 * the comparison ran. That made one sentence do two jobs — whether the comparison can be measured,
 * and whether the model states anything by it — and the two came apart the day a comparison got a
 * site of its own. The measurement moved to the comparison and the policy did not, so a
 * meaning-preserving rewrite moved the partition.
 *
 * <p><b>A policy over {@link souther.compiler.coverage.ComparisonCatalog}</b> and not a second
 * account of what a comparison is. The catalog says what the bodies hold; this decides which of
 * those a line is drawn on, and the two are different questions with different answers. Kept apart
 * because the first is a fact about the source and the second is a decision about what a model
 * divides — and while they were one walk, the decision was being made by whatever the numbering
 * happened to reach.
 *
 * <p><b>A decision and not a walk.</b> Where a comparison is written and what is in force there is
 * {@link ComparisonReadings}'s, asked once for the whole body; this is what that walk asks at each
 * of them. Holding a walk of its own, this was one of two readings threading the same {@code let}
 * rule through the same tree to answer different halves of one question.
 *
 * <p>What is not answered here is what reaching an arm of a fork proves about a comparison under it.
 * Under {@code A && (B || C)} the arms tell those three apart — the arm the whole condition holds on
 * proves {@code A} ran and {@code B} ran, and proves nothing about {@code C} — and no measure asks: a
 * row meets a line by lighting the comparison's own probe, which is a fact the comparison records for
 * itself. It is a relation between a comparison and one use of its truth, so a comparison consumed by
 * two forks has two answers and one consumed by none has no answer at all. Anything modelling it
 * belongs where the uses are, not here and not on the origin.
 */
final class BoundaryPolicy {

    /**
     * What this policy says about one comparison of the body, and what the comparison came to
     * where the policy admits it.
     *
     * <p>One answer per comparison. Whether a line may be drawn on it is this policy's question and
     * what the line is, or why there is none, is the reading's ({@link ComparisonAssessment}) — and
     * the second is asked only of a comparison the first admits, so a reason for bearing no line is
     * never worked out from a reading that was never made. A comparison this refuses is not a rule
     * with no line here: its outcome is about no row ({@link NotABoundary}), so a report saying
     * nothing of it is right. Nor could it be said in the reading's words: what refuses it is where
     * it stands, and the arithmetic has no answer to that.
     *
     * <p>Which comparison it is about is not held here. The reading that holds this holds the
     * comparison beside it ({@link ComparisonReadings.Reading}), and a second copy would be one
     * nothing keeps equal to the first.
     */
    sealed interface Standing {

        /** A line may be drawn on it, and {@code read} is what the one reading of it came to. */
        record Admitted(ComparisonAssessment read) implements Standing {

            public Admitted {
                if (read == null) {
                    throw new IllegalArgumentException("a comparison this admits has been read");
                }
            }
        }

        /** No line may be drawn on it, and this is which of the reasons it is. */
        record Refused(NotABoundary why) implements Standing {

            public Refused {
                if (why == null) {
                    throw new IllegalArgumentException("a comparison is refused for a reason");
                }
            }
        }
    }

    /**
     * Whether a line may be drawn on {@code comparison} where it stands, or why not.
     *
     * <p>The reason about the model is said first. A comparison the behavior's answer does not turn
     * on is not a boundary whichever way it could have been measured, and a reader told instead that
     * its outcome cannot be attributed to a row would go looking for a way to attribute it.
     *
     * <p>Where the comparison stands and what its names point at are not arguments to this, and
     * neither is the reading of the comparison. A decision that took the reading only to hand it
     * back was a second place holding it, and the walk that has it is {@link ComparisonReadings},
     * which reads what this admits.
     *
     * <p><b>How many times a run passes the comparison is not asked.</b> A recording holds that a
     * place was passed and not how many times, so two outcomes of one comparison in one run would
     * have to be told from two rows' outcomes — and what settles that is the reading of the
     * comparison rather than anything about the construct it stands in.
     *
     * <p>What the reading settles is this: <b>every atom of a form it composed is a term the row
     * decides.</b> A value at a position of the input is one, a number taken of one is one, and a
     * number taken over the occurrences of one path in a single run is one — that last names no
     * single position, and what makes the row decide it is the run naming exactly one sequence
     * ({@link souther.compiler.inputs.RunSource}). What a row does not decide is not an atom at all:
     * it enters a form only where it was written out and every value of it came to the same form
     * ({@link souther.compiler.check.AffineForms}), after which it is a number and no longer an
     * uncertainty. So every pass of a comparison that bears a line reads what the row holds — at one
     * occurrence of a position where the passes stand at different occurrences of one, and at the
     * same values where they do not — and a pass reading anything else is one no line came of.
     *
     * <p><b>What a run can be observed at is not asked here.</b> Whether the emitter numbered a
     * place for the comparison is a fact about the tree that runs, and this decides what a model
     * states, which is read where the language's operations stand. The two were one clause while
     * one walk answered both; a reading that asked it would be a reading no tree could make on its
     * own, and a comparison the emitter numbers nothing for would have no reading at all rather
     * than a reading with nowhere to watch it.
     *
     * @param live whether what is computed at this position is read on the way to what the behavior
     *             answers with, which is {@link LiveFlow}'s answer carried down the walk
     * @return why the comparison is refused, or empty where a line may be drawn on it. Decided from
     *         what is passed in and nothing else: the reading of the comparison is made by the
     *         caller, and only where this is empty
     */
    static Optional<NotABoundary> refuses(boolean live) {
        if (!live) {
            return Optional.of(NotABoundary.NOTHING_READS_IT);
        }
        return Optional.empty();
    }

    private BoundaryPolicy() {}
}
