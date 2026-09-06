package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.query.ReadAs;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        return new Core.Binary(op, left, right, SourceConstructOrigin.unwritten(), answers, POS);
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
        return read(clause, Predicates.PartsToRead.ALL);
    }

    private static List<Core> read(Core clause, Predicates.PartsToRead asked) {
        List<Core> parts = new ArrayList<>();
        new Predicates(terms()).assumed(clause, rootAt(), false, (part, _) -> parts.add(part),
                asked);
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

        assertEquals(List.of(atLeastOne(), atMostNine(), either, neither), read(neither),
                "each part denied, the choice they were written as, and the denial of it");
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

        assertEquals(List.of(both, neither), read(neither),
                "the conjunction as the choice its denial makes of it, and the denial");
    }

    /**
     * A conjunct a caller did not ask for is the other conjunct alone.
     *
     * <p>Not an empty answer composed with the other: what a conjunction comes to is its conjuncts
     * together, and there is nothing for a conjunct nobody read to contribute.
     */
    @Test
    void aConjunctLeftOutIsTheConjunctBesideItAlone() {
        Core.Binary both = joined(BinOp.AND, atLeastOne(), atMostNine());

        assertEquals(List.of(atMostNine(), both),
                read(both, Predicates.PartsToRead.without(Set.of(atLeastOne()))),
                "the conjunct that was asked for, and the clause it is a conjunct of");
        assertEquals(List.of(), read(both, Predicates.PartsToRead.without(Set.of(both))),
                "and a clause of one conjunct left out is the whole of what the clause states");
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
