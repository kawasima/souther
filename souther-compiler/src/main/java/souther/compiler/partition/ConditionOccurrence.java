package souther.compiler.partition;

/**
 * Which condition on the way to a border this is, as the reading that met it names them.
 *
 * <p>What tells one condition from every other, and nothing about where it stands. A report is sent
 * to a place and this is not one: two conditions written a page apart are two of these because the
 * reading met two, and a condition that moves is the same one.
 *
 * <p><b>Minted by the fold and not read off the tree.</b> A condition is what
 * {@link Condition#of} makes of an expression, and that reading looks through a binding and through
 * a name standing for what it was bound to — so a path down the tree that runs is not an address
 * among the conditions, and two readings of one subtree would number by different shapes. What
 * numbers them is the fold itself, once, and every reader below is handed the answer.
 *
 * <p><b>Counted within the body being read, not over the module.</b> A count over the module makes
 * the number a function of everything read before it there, so an edit to one behavior renames the
 * conditions of every one after it — and an identity that moves for an edit nothing about it can
 * see is not one. The two halves travel together for the reason
 * {@link souther.compiler.types.SourceConstructOrigin} keeps its owner beside its ordinal: a number
 * says which condition only under something that says which conditions were being counted.
 *
 * <p>Not a name anything outside one reading can be matched by, and not one to work a place out of.
 * Where a report about a condition points is
 * {@link ConditionReportAnchor}'s question, asked of whoever can answer it.
 *
 * @param behavior the body whose reading met it
 * @param ordinal  which condition of that reading, by the fold's own count over it
 */
public record ConditionOccurrence(String behavior, int ordinal) {

    public ConditionOccurrence {
        if (behavior == null) {
            throw new IllegalArgumentException(
                    "a condition on the way is one some reading met: " + ordinal);
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "a condition stands somewhere among the ones a reading met: " + ordinal);
        }
    }

    @Override
    public String toString() {
        return "condition " + ordinal + " of `" + behavior + "`";
    }
}
