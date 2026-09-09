package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which rules a world has is settled before a reading says how far into them it goes.
 *
 * <p>A reading asked what one conjunct was holding reads the declaration under a rule set the
 * author did not write, and the conjunct left out is not a rule of that world at all. How far a
 * reading descends is its own answer ({@link Descent}) — a conjunction one takes whole is a part to
 * it and two parts to the reading beside it — and neither answer is about which rules there are.
 *
 * <p>Asked the other way round, a world holds for the readings that descend and for no others: a
 * reading that takes a conjunction whole is handed the node with the conjunct still under it and
 * reads a rule its world does not have. Every reading of a declaration's connectives descends
 * today, so nothing a model can be written as shows it — which is why this is asked of the fold
 * itself, with a reading that stops where the ones in this compiler do not.
 */
class AWorldSaysWhichRulesThereAreBeforeAReadingSaysHowFarItGoesTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final Core LEFT = leaf("left");
    private static final Core RIGHT = leaf("right");
    private static final Core BOTH = new Core.Binary(BinOp.AND, LEFT, RIGHT,
            ConstructOccurrence.unwritten(), Type.BOOL, POS);

    /**
     * A reading that takes a conjunction whole is handed only what its world has.
     *
     * <p>It never descends, so nothing about composing two answers can be what carries the
     * omission: what reaches it is one part, and the part is the one the world holds.
     */
    @Test
    void aReadingThatTakesAConjunctionWholeIsHandedOnlyWhatItsWorldHas() {
        assertEquals(List.of("right"), whatReached(withoutTheLeft()),
                "the left conjunct is no rule of this world, so a reading that would have taken"
                        + " the whole conjunction is handed the rule that is there");
    }

    /**
     * And the same reading in the world the author wrote is handed the conjunction.
     *
     * <p>The control. Without it the case above would pass on a fold that never hands a reading a
     * connective at all, which is a different mechanism answering the same way.
     */
    @Test
    void andInTheWorldTheAuthorWroteItIsHandedTheConjunction() {
        assertEquals(List.of("left && right"), whatReached(ClauseView.asWritten()),
                "both conjuncts are rules here, so what the reading stops at is the conjunction");
    }

    /** What a reading that stops at every connective was handed, in the order it reached them. */
    private static List<String> whatReached(ClauseView view) {
        List<String> reached = new java.util.ArrayList<>();
        ClauseReading<String, Void> stopping = new ClauseReading<>() {

            @Override
            public String whole(ClauseExpr.Part part, Void at) {
                reached.add(spelled(part.of()));
                return spelled(part.of());
            }

            /** Never, which is the point: how far this goes is its own answer and says nothing
             *  about which rules its world has. */
            @Override
            public Descent<String> at(ClauseExpr.Joined join) {
                return new Descent.Whole<>();
            }
        };
        stopping.read(BOTH, true, null, ClauseScope.unchanged(), null, view);
        return List.copyOf(reached);
    }

    /** The world that holds the right conjunct and not the left one. */
    private static ClauseView withoutTheLeft() {
        RuleRef.Invariant rule = new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("demo", "R")), 0),
                Optional.empty()));
        PartId<RuleRef.Invariant> left = new PartId<>(rule, 0);
        PartId<RuleRef.Invariant> right = new PartId<>(rule, 1);
        return PartsLeftOut.without(Set.of(left)).viewOf(
                List.of(new Clauses.StatedPart(left, ClauseExpr.of(LEFT, true)),
                        new Clauses.StatedPart(right, ClauseExpr.of(RIGHT, true))));
    }

    /** What a node reads as here, which is enough to tell the two conjuncts and the whole apart. */
    private static String spelled(Core node) {
        if (node == LEFT) {
            return "left";
        }
        if (node == RIGHT) {
            return "right";
        }
        return node == BOTH ? "left && right" : "something else";
    }

    /** A clause of no connective, named by which of them it is. */
    private static Core leaf(String named) {
        return new Core.Binary(BinOp.EQ, new Core.Str(named, Type.STRING, POS),
                new Core.Str(named, Type.STRING, POS), ConstructOccurrence.unwritten(),
                Type.BOOL, POS);
    }
}
