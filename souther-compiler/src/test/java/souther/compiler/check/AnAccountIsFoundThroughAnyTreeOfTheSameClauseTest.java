package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * One clause built twice is one clause, and what a reading made of a part of it is found either way.
 *
 * <p>A clause reaches a value through whatever tree the step that brought it there built. Those
 * trees are equal and are not the same objects — a substitution that changes nothing hands back
 * what it was given, and one that changes something builds afresh — so a reader that filed what it
 * made of a part under the node it read it at held an address that answered for exactly one of
 * them. Asked with the other, it found nothing, and nothing is how a part that constrained a
 * position reads when the walk asks what it constrained.
 *
 * <p>What is held instead is where in the clause the part is. The occurrences are handed out once,
 * by the one reading of the structure ({@link ClauseExpr}), and the structure is what the two trees
 * share — so the coordinate is the same coordinate in both, while the nodes are two.
 *
 * <p>The control is in the second test: the nodes at one coordinate are equal and are not the same
 * object, which is what an address made of them would have been telling apart.
 */
class AnAccountIsFoundThroughAnyTreeOfTheSameClauseTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final RuleRef.Invariant RULE = new RuleRef.Invariant(new Clause.Ref(
            new Clause.Id(TypeSymbols.declared(new TypeKey("demo", "R")), 0), Optional.empty()));

    /** What a reading is recorded as having made of a part. Two of them, told apart by nothing
     *  the address could read, so finding the wrong one is a failure and not a coincidence. */
    private static final ReadByClauses.OfAPart LEFT_SAID = accounting("left");
    private static final ReadByClauses.OfAPart RIGHT_SAID = accounting("right");

    /** {@code a == a && b == b}, built afresh each time this is called. */
    private static Core clause() {
        return new Core.Binary(BinOp.AND, leaf("a"), leaf("b"),
                ConstructOccurrence.unwritten(), Type.BOOL, POS);
    }

    @Test
    void oneClauseBuiltTwiceHandsOutOneSetOfCoordinates() {
        ClauseExpr one = ClauseExpr.of(clause(), true);
        ClauseExpr other = ClauseExpr.of(clause(), true);
        Map<ClauseOccurrence, Core> here = nodesBy(one);
        Map<ClauseOccurrence, Core> there = nodesBy(other);
        assertEquals(here.keySet(), there.keySet(),
                "the occurrences are the structure's, and the two trees are one structure");
        here.forEach((at, node) -> {
            assertEquals(node, there.get(at),
                    "the node at " + at + " is the same node of the clause either way");
            assertNotSame(node, there.get(at),
                    "and it is not the same object, which is what an address made of it read");
        });
    }

    @Test
    void anAccountFiledOverOneTreeIsFoundOverTheOther() {
        Core built = clause();
        ClauseExpr shape = ClauseExpr.of(built, true);
        ClauseExpr.Joined joined = (ClauseExpr.Joined) shape;
        InvariantChecker.Written reading = readingOver(built, joined)
                .alsoAdopting(List.of(Map.entry(joined.left().at(), LEFT_SAID),
                        Map.entry(joined.right().at(), RIGHT_SAID)));

        // The same clause as the step that brought it here built it a second time, which is what a
        // reader downstream is holding.
        ClauseExpr.Joined again = (ClauseExpr.Joined) ClauseExpr.of(clause(), true);
        assertNotSame(joined.left().written(), again.left().written(),
                "the trees are two, which is the whole of what this is about");

        assertSame(LEFT_SAID, reading.adoptedAt(again.left().at()),
                "what the reading made of the left conjunct, asked for at the coordinate the other"
                        + " tree gives it");
        assertSame(RIGHT_SAID, reading.adoptedAt(again.right().at()),
                "and the right one, which is a different coordinate and a different answer");
    }

    @Test
    void andACoordinateNoReadingFiledIsAbsentRatherThanEmpty() {
        Core built = clause();
        ClauseExpr.Joined joined = (ClauseExpr.Joined) ClauseExpr.of(built, true);
        InvariantChecker.Written reading = readingOver(built, joined)
                .alsoAdopting(List.of(Map.entry(joined.left().at(), LEFT_SAID)));
        assertNull(reading.adoptedAt(joined.right().at()),
                "a part this reading never read is not one it read and made nothing of");
    }

    /** A reading of {@code built}, in a world holding both of the parts its author wrote. */
    private static InvariantChecker.Written readingOver(Core built, ClauseExpr.Joined joined) {
        List<Clauses.StatedPart> authored = List.of(
                new Clauses.StatedPart(new PartId<>(RULE, 0), joined.left()),
                new Clauses.StatedPart(new PartId<>(RULE, 1), joined.right()));
        return InvariantChecker.Written.of(new InvariantChecker.ReadingId(0), built, authored,
                PartsLeftOut.without(Set.of()), Map.of());
    }

    /** Every node of the shape, under the coordinate the shape gives it. */
    private static Map<ClauseOccurrence, Core> nodesBy(ClauseExpr shape) {
        Map<ClauseOccurrence, Core> out = new LinkedHashMap<>();
        gather(shape, out);
        return out;
    }

    private static void gather(ClauseExpr shape, Map<ClauseOccurrence, Core> out) {
        out.put(shape.at(), shape.written());
        switch (shape) {
            case ClauseExpr.Leaf _ -> {
            }
            case ClauseExpr.Scoped it -> gather(it.body(), out);
            case ClauseExpr.Joined it -> {
                gather(it.left(), out);
                gather(it.right(), out);
            }
        }
    }

    private static ReadByClauses.OfAPart accounting(String named) {
        List<RuleShortfall> nothing = new ArrayList<>();
        return new ReadByClauses.OfAPart(Adoption.nothing(), Adoption.nothing(),
                Set.of(FactSubject.of(new Term.Interner().written(named))),
                Set.copyOf(nothing), Map.of(), EndsLeftOpen.nothing(), Map.of());
    }

    /** A clause of no connective, named by which of them it is. */
    private static Core leaf(String named) {
        return new Core.Binary(BinOp.EQ, new Core.Str(named, Type.STRING, POS),
                new Core.Str(named, Type.STRING, POS), ConstructOccurrence.unwritten(),
                Type.BOOL, POS);
    }
}
