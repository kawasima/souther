package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading of a clause stands where the clause does, and a conjunction does not move into a choice.
 *
 * <p>Everything a choice is answerable for is asked of its two alternatives, and an alternative is
 * what an author wrote between the brackets. A conjunction distributed into the branches makes each
 * of them a clause the author did not write — and then the choice reads {@code (x == "A" || x ==
 * "B") && f(y)} as one whose alternatives nothing could read, on the strength of a conjunct written
 * beside it.
 *
 * <p>The rewriting itself is not the defect and is not being removed. Which values a position may
 * take is settled by every clause together, so a branch has to be refined by what was written beside
 * it — over the tree that derives values ({@code StatedTogether}), which is a projection this one
 * cannot be reached from.
 *
 * <p><b>The count of connectives is not the property.</b> Distribution leaves it exactly where it
 * was: {@code (a || b) && c} and {@code (a && c) || (b && c)} have one choice each. So what is held
 * is which node stands under which, down to the identity of every node — and the negative control
 * below is the distributed tree, which a count would accept.
 *
 * <p>Every clause of every model the suite compiles goes through the same predicate, as an assertion
 * where the reading is made. What is here is the predicate itself, with something for it to refuse.
 */
class AReadingOfAClauseIsTheTreeItsAuthorWroteTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final Core A = leaf();
    private static final Core B = leaf();
    private static final Core C = leaf();

    /** {@code (a || b) && c}, which is the shape a conjunction beside a choice is written in. */
    private static final Core BESIDE_THE_BRACKET =
            joined(BinOp.AND, joined(BinOp.OR, A, B), C);

    /**
     * What the reading makes of it, node for node.
     *
     * <p>The whole clause is named twice: the fold names every node a shape was spelled as, and
     * the caller that started the walk names the clause it asked about.
     */
    private static StatedByClauses asWritten() {
        Core choice = ((Core.Binary) BESIDE_THE_BRACKET).left();
        return part(BESIDE_THE_BRACKET, part(BESIDE_THE_BRACKET, new StatedByClauses.Both(
                part(choice, new StatedByClauses.Either(new ChoiceId(), choice,
                        part(A, said()), part(B, said()))),
                part(C, said()))));
    }

    /**
     * And the same clause distributed, which is what the reading used to hand back.
     *
     * <p>One choice, both leaves, every node of the clause named somewhere — and {@code c} standing
     * inside each alternative, which is the whole of what is wrong with it.
     */
    private static StatedByClauses distributed() {
        Core choice = ((Core.Binary) BESIDE_THE_BRACKET).left();
        ChoiceId id = new ChoiceId();
        return part(BESIDE_THE_BRACKET, part(BESIDE_THE_BRACKET,
                new StatedByClauses.Either(id, choice,
                        new StatedByClauses.Both(part(A, said()), part(C, said())),
                        new StatedByClauses.Both(part(B, said()), part(C, said())))));
    }

    @Test
    void aReadingStandsWhereTheClauseDoes() {
        assertTrue(StatedByClauses.mirrors(BESIDE_THE_BRACKET, asWritten(),
                        ClauseView.asWritten()),
                "the conjunction is a conjunction of the choice, which is what the author wrote");
    }

    @Test
    void aConjunctionMovedIntoTheBranchesIsRefused() {
        assertFalse(StatedByClauses.mirrors(BESIDE_THE_BRACKET, distributed(),
                        ClauseView.asWritten()),
                "the conjunct written beside the brackets stands inside both alternatives, so each"
                        + " of them is a clause nobody wrote and the choice would be answerable"
                        + " for what one of them left open");
    }

    /**
     * And a part recorded inside a branch it was not written in is the same loss, one node down.
     *
     * <p>The conjunction stands where the author wrote it here, and only the record of what
     * {@code c} came to has been pushed into the alternatives — which is the second road into the
     * same place, and the reason a conjunction standing still is not the whole property.
     */
    @Test
    void aPartPushedIntoTheBranchesIsRefused() {
        Core choice = ((Core.Binary) BESIDE_THE_BRACKET).left();
        assertFalse(StatedByClauses.mirrors(BESIDE_THE_BRACKET,
                        part(BESIDE_THE_BRACKET, part(BESIDE_THE_BRACKET,
                                new StatedByClauses.Both(
                                        part(choice, new StatedByClauses.Either(
                                                new ChoiceId(), choice,
                                                part(C, part(A, said())),
                                                part(C, part(B, said())))),
                                        part(C, said())))),
                        ClauseView.asWritten()),
                "the whole clause is recorded as what each alternative came to, so a reader asking"
                        + " what it came to is answered by one branch of a choice");
    }

    private static StatedByClauses said() {
        return new StatedByClauses.Said(Confinement.Planned.top(Map.of()),
                StatedByClauses.Part.nothing());
    }

    private static StatedByClauses part(Core node, StatedByClauses of) {
        return new StatedByClauses.CameFrom(node, of);
    }

    private static Core joined(BinOp op, Core left, Core right) {
        return new Core.Binary(op, left, right, ConstructOccurrence.unwritten(), Type.BOOL, POS);
    }

    /** A clause of no connective, which is all a shape needs of one. */
    private static Core leaf() {
        return new Core.Binary(BinOp.EQ, new Core.Int(0, Type.INT, POS),
                new Core.Int(0, Type.INT, POS), ConstructOccurrence.unwritten(), Type.BOOL, POS);
    }
}
