package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.query.ReadAs;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How far the reading of what a clause owes goes into the clause, and where it stops.
 *
 * <p>It descends where the connective under the polarity in force composes both of what it joins,
 * and stops where it composes either. A conjunction stated gives both conjuncts; a choice denied
 * gives both denials; a choice stated gives neither, and the whole of it is handed to the reader of
 * comparisons as one thing. Which of those a clause is is what the reading answers by descending or
 * not, and it is the answer this holds.
 *
 * <p>Held on the parts the reading says it read, which is what it tells a caller as it goes
 * ({@code PerPart}), rather than on what any of them came to. What a conjunct owes is the reader of
 * comparisons' answer and is held elsewhere; how far the walk went is this reading's own, and it is
 * the thing that moves when a clause's shape is recognised somewhere else.
 *
 * <p>What it is told is keyed by the node, and the order below is the order the shape is walked in:
 * what a part came to before what the connective composing it came to, and a node a restatement was
 * written as before the node under it. A caller reads what it was told by the node it holds, so the
 * order is not what any of them takes from it.
 */
class HowFarTheReadingOfWhatAClauseOwesDescendsTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "f");
    private static final BindingId VALUE = new BindingId(OWNER, 0);

    private static Terms terms() {
        return RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()),
                ReadAs.THE_COMPILATION_DOES);
    }

    private static Denotations rootAt() {
        return Denotations.none().location(VALUE, AsPlaces.of(VALUE), AsPlaces.term(VALUE));
    }

    private static Core.Read value() {
        return new Core.Read("value", VALUE, Type.INT, POS);
    }

    private static Core.Binary comparing(BinOp op, Core left, Core right, Type answers) {
        return new Core.Binary(op, left, right, ConstructOccurrence.unwritten(), answers, POS);
    }

    /** `value >= 1`, one of the two rules every clause below is written out of. */
    private static Core.Binary atLeastOne() {
        return comparing(BinOp.GE, value(), new Core.Int(1, Type.INT, POS), Type.BOOL);
    }

    /** `value <= 9`, the other. */
    private static Core.Binary atMostNine() {
        return comparing(BinOp.LE, value(), new Core.Int(9, Type.INT, POS), Type.BOOL);
    }

    private static Core.Binary joined(BinOp op, Core left, Core right) {
        return comparing(op, left, right, Type.BOOL);
    }

    /** {@code clause} denied, written as the comparison against {@code false} that says so. */
    private static Core.Binary denied(Core clause) {
        return comparing(BinOp.EQ, clause, new Core.Bool(false, Type.BOOL, POS), Type.BOOL);
    }

    /** The parts the reading says it read, in the order it read them. */
    private static List<Core> read(Core clause) {
        List<Core> parts = new ArrayList<>();
        new Predicates(terms()).assumed(clause, rootAt(), false, (_, part, _) -> parts.add(part));
        return parts;
    }

    /** A conjunction stated gives both of its conjuncts, and says so of the whole beside them. */
    @Test
    void aConjunctionStatedIsReadAConjunctAtATime() {
        Core.Binary both = joined(BinOp.AND, atLeastOne(), atMostNine());

        assertEquals(List.of(atLeastOne(), atMostNine(), both), read(both),
                "each conjunct, and the clause they are the conjuncts of");
    }

    /** A choice denied is the choice between its parts denied, so both of them are read. */
    @Test
    void aChoiceDeniedIsReadAPartAtATime() {
        Core.Binary either = joined(BinOp.OR, atLeastOne(), atMostNine());
        Core.Binary neither = denied(either);

        assertEquals(List.of(atLeastOne(), atMostNine(), neither, either), read(neither),
                "each part denied, and the two nodes the choice and its denial were written as");
    }

    /**
     * A choice stated is read as one thing, and neither of its parts is read.
     *
     * <p>What a choice states is not what its parts state — one of them holds and this cannot say
     * which — so the reader of comparisons is handed the whole of it. Descending here and composing
     * what each part owes would be a different question about a disjunctive clause, and it is the
     * question this reading does not ask.
     */
    @Test
    void aChoiceStatedIsReadWhole() {
        Core.Binary either = joined(BinOp.OR, atLeastOne(), atMostNine());

        assertEquals(List.of(either), read(either),
                "the choice itself, and neither of the parts it is written out of");
    }

    /** And a conjunction denied is the choice between its conjuncts denied, which is read whole. */
    @Test
    void aConjunctionDeniedIsReadWhole() {
        Core.Binary both = joined(BinOp.AND, atLeastOne(), atMostNine());
        Core.Binary neither = denied(both);

        assertEquals(List.of(neither, both), read(neither),
                "the two nodes the conjunction and its denial were written as, and neither half");
    }

    /**
     * What a conjunction owes is what its conjuncts owe together.
     *
     * <p>Which is why a caller wanting a clause without one of its parts leaves the part out of
     * the list rather than telling this reading to step over a node: the two come to the same
     * thing, and the list is where which parts a clause has was settled.
     */
    @Test
    void whatAConjunctionOwesIsWhatItsConjunctsOweTogether() {
        Predicates predicates = new Predicates(terms());
        Predicates.Owed together = predicates.assumed(
                joined(BinOp.AND, atLeastOne(), atMostNine()), rootAt(), false);
        Predicates.Owed apart = predicates.assumed(atLeastOne(), rootAt(), false)
                .and(predicates.assumed(atMostNine(), rootAt(), false));

        assertEquals(together.parts(), apart.parts(),
                "read as one clause and read a conjunct at a time");
        assertEquals(together.folded(), apart.folded(),
                "and the same of what it came to on its own");
    }

    /**
     * What a clause that came out false on its own owes, where the caller says that decides it.
     *
     * <p>Two answers about one clause and not one: what it owes, and that it is already refused.
     * A caller that folds the clause itself would be reading it a second time, and the two agree
     * only until either changes.
     */
    @Test
    void aClauseThatFoldsFalseSaysSoWhereTheCallerAsksForIt() {
        Core.Binary refused = comparing(BinOp.GE, new Core.Int(1, Type.INT, POS),
                new Core.Int(2, Type.INT, POS), Type.BOOL);
        Predicates predicates = new Predicates(terms());

        assertEquals(List.of(Predicates.Fold.FAILS, Predicates.Fold.FAILS), List.of(
                        predicates.assumed(refused, rootAt(), false).folded(),
                        predicates.assumed(refused, rootAt(), true).folded()),
                "the clause folds to false whether or not the caller acts on it");
        assertEquals(
                predicates.assumed(refused, rootAt(), false).parts().size(),
                predicates.assumed(refused, rootAt(), true).parts().size(),
                "and owes what it owes either way: what it states is one answer and that it came"
                        + " out false is another, said beside it rather than instead of it");
    }
}
