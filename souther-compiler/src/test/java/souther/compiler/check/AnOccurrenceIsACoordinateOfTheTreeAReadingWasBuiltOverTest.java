package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Where a part of a clause stands is counted over the tree a reading was built over, which is not
 * the tree its author wrote.
 *
 * <p>A clause is read at each place a walk opens a value, with the fields of that construction put
 * in for the reads — and what a construction gives a field is an expression of its own. Give a
 * field a conjunction and the tree the reading is over has a connective the author of the clause
 * did not write, {@link ClauseExpr} numbers it like any other, and everything written after it
 * takes the next number along.
 *
 * <p>So an occurrence names a part among the parts of one reading. It is not what a declaration
 * could publish about a clause: two constructions of one declaration are two trees, and the part
 * an author wrote in one place is at two numbers. What does not move is which statement of which
 * conjunct it is ({@link InvariantStatementId}), which is counted over what the author wrote.
 *
 * <p>The control is the other half and is what makes this a measurement of the coordinate rather
 * than of trees being different: a connective substituted after the part leaves its number alone,
 * because the walk has already passed it.
 */
class AnOccurrenceIsACoordinateOfTheTreeAReadingWasBuiltOverTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    /** The part an author wrote, which is one part of one clause in every reading below. */
    private static final Core TARGET = leaf();

    @Test
    void aConnectiveSubstitutedBeforeAPartMovesIt() {
        Core read = joined(leaf(), TARGET);
        Core withAJoinPutIn = joined(joined(leaf(), leaf()), TARGET);

        assertNotEquals(occurrenceOf(read), occurrenceOf(withAJoinPutIn),
                "a construction gave a field a conjunction, and the part written after it is"
                        + " numbered further along");
    }

    @Test
    void andOneSubstitutedAfterItDoesNot() {
        Core read = joined(TARGET, leaf());
        Core withAJoinPutIn = joined(TARGET, joined(leaf(), leaf()));

        assertEquals(occurrenceOf(read), occurrenceOf(withAJoinPutIn),
                "the walk numbers what it reaches, and it had passed this part already");
    }

    /** Where {@code TARGET} sits in the shape read off {@code clause}. */
    private static ClauseOccurrence occurrenceOf(Core clause) {
        ClauseOccurrence found = found(ClauseExpr.of(clause, true));
        if (found == null) {
            throw new IllegalStateException("the part under test is not in the tree read over");
        }
        return found;
    }

    private static ClauseOccurrence found(ClauseExpr shape) {
        return switch (shape) {
            case ClauseExpr.Leaf it -> it.of() == TARGET ? it.at() : null;
            case ClauseExpr.Joined it -> {
                ClauseOccurrence left = found(it.left());
                yield left != null ? left : found(it.right());
            }
            case ClauseExpr.Scoped it -> found(it.body());
        };
    }

    /** A clause of two parts, written as the author's {@code &&}. */
    private static Core joined(Core left, Core right) {
        return new Core.Binary(BinOp.AND, left, right, ConstructOccurrence.unwritten(), Type.BOOL,
                POS);
    }

    /** A part of no connective, which is all a shape needs of one. */
    private static Core leaf() {
        return new Core.Binary(BinOp.EQ, new Core.Int(0, Type.INT, POS),
                new Core.Int(0, Type.INT, POS), ConstructOccurrence.unwritten(), Type.BOOL, POS);
    }
}
