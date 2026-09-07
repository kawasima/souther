package souther.compiler.partition;

/**
 * Why {@link BoundaryPolicy} refuses a comparison of a body.
 *
 * <p>The two gates the policy asks, in the order it asks them, and an answer rather than an
 * absence: which gate refused a comparison is a fact the policy has in hand when it refuses, and
 * folded into one {@code false} it would have to be worked out again by anyone who needed it.
 *
 * <p><b>Neither is a rule without a line, and no report says either.</b> A comparison nothing reads
 * decides nothing the behavior answers by; a comparison no run can be recorded through decides
 * nothing any row reaches. In both the comparison's outcome is about no row, so there is nothing
 * for a finding about a position to say — and nothing that could be said in the reading's words
 * ({@link souther.compiler.inputs.BlockReason}), which are about what the arithmetic did with a
 * form: what refuses the comparison is where it stands, and the arithmetic is never asked. A
 * reason in those words for a refused comparison would be one made up from material that cannot
 * answer the question, and it is the type of {@link BoundaryPolicy.Standing.Refused} that makes
 * such a reason unsayable.
 *
 * <p>The first is said where both hold, because it is the one about the model. A reader told that a
 * comparison's outcome cannot be attributed to a row would go looking for a way to attribute it,
 * when the behavior's answer does not turn on the comparison at all.
 */
public enum NotABoundary {

    /**
     * Nothing reads the comparison's truth, so the behavior's answer does not turn on it.
     *
     * <p>The one of these that is about the model rather than about what can be measured. A body
     * binding {@code t.value < 240} to a name it never reads answers alike either side of 240, so a
     * partition there would have a report say this behavior distinguishes two ranges of its input
     * that it in fact answers the same — a distinction the model does not draw, asked for as rows.
     *
     * <p>Which values reach an answer is settled by {@link LiveFlow}, which over-reports on purpose:
     * a chain of dead bindings is still counted as read. So a comparison named this is one nothing
     * reads under any reading, and one not named this may still be one a sharper reading would.
     */
    NOTHING_READS_IT
}
