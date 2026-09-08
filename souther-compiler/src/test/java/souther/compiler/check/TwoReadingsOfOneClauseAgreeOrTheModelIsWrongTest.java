package souther.compiler.check;

import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What two readings of one clause come to, and what they may not come to.
 *
 * <p>One construction is read once per branch of a conditional above it, and the two readings are
 * combined. They differ in what they could prove and not in what the declaration says, so anything
 * else differing is the compiler having called two clauses one, or one clause two, and answering
 * that with a clause would report a declaration nobody wrote.
 *
 * <p>Commutative, associative and idempotent, because the order the walk combines branches in is not
 * something a reader should be able to see. A first-wins union reads as one of these until a clause
 * is reached by two paths that know different amounts, and then what the warning says depends on
 * which branch the walk read first.
 */
class TwoReadingsOfOneClauseAgreeOrTheModelIsWrongTest {

    /**
     * The three properties, held over readings the model can actually produce.
     *
     * <p>Which is what settles them. Nothing here picks between two readings, so there is no rule
     * whose preference could depend on the order or the grouping — two readings of one clause say
     * the same thing about it, and a pair that does not is refused whichever way round it is asked.
     * The version before this preferred the reading that could point somewhere, and was associative
     * without being commutative, then commutative without being associative.
     */
    @Test
    void mergingIsCommutativeAssociativeAndIdempotent() {
        Clause.Ref one = clause(FIRST, "ordered");
        Clause.Ref two = clause(FIRST, "ordered");
        Clause.Ref three = clause(FIRST, "ordered");

        assertEquals(Clause.Ref.merge(one, two), Clause.Ref.merge(two, one));
        assertEquals(Clause.Ref.merge(Clause.Ref.merge(one, two), three),
                Clause.Ref.merge(one, Clause.Ref.merge(two, three)));
        assertEquals(one, Clause.Ref.merge(one, one));
    }

    /**
     * Two readings that reached the clause by two names are one reading.
     *
     * <p>The name a reading reached the code by is a fact about that reading, and two readings of one
     * clause reach it by two names as easily as one. Carried into what a clause holds, the two are
     * different values and the merge has a pair to choose between — which is a first-wins union
     * whichever rule it chooses by. A clause takes the declaration's own form, so there is nothing
     * left to differ in.
     */
    @Test
    void twoReadingsThatReachedTheSameClauseByTwoNamesAreOneReading() {
        Clause.Ref viaOne = clause(FIRST, "ordered");
        Clause.Ref viaTwo = clause(FIRST, "ordered");

        assertEquals(viaOne, viaTwo, "a clause is which one it is, not how it was reached");
        assertEquals(viaOne, Clause.Ref.merge(viaOne, viaTwo));
        assertEquals(viaOne, Clause.Ref.merge(viaTwo, viaOne));
    }

    private static final TypeSymbol.AtModule BOUND = TypeSymbols.declared(new TypeKey("demo", "Bound"));
    private static final TypeSymbol.AtModule OTHER = TypeSymbols.declared(new TypeKey("demo", "Other"));

    private static final Clause.Id FIRST = new Clause.Id(BOUND, 0);
    private static final Clause.Id SECOND = new Clause.Id(BOUND, 1);

    private static Clause.Ref clause(Clause.Id id, String name) {
        return new Clause.Ref(id, Optional.ofNullable(name).map(ClauseName::new));
    }

    // --- what may not differ ----------------------------------------------------------------

    @Test
    void twoClausesAreNotMergedIntoOne() {
        assertThrows(Clause.NotOneClause.class, () -> Clause.Ref.merge(
                clause(FIRST, "ordered"),
                clause(SECOND, "ordered")));
        assertThrows(Clause.NotOneClause.class, () -> Clause.Ref.merge(
                clause(FIRST, "ordered"),
                clause(new Clause.Id(OTHER, 0), "ordered")));
    }

    /**
     * A name is what the declaration says, not what a reading found out, so one reading finding a
     * name where another found none is not a reading that knew more. It is the two of them reading
     * different declarations under one identity.
     */
    @Test
    void oneClauseIsNotNamedTwoWays() {
        assertThrows(Clause.NotOneClause.class, () -> Clause.Ref.merge(
                clause(FIRST, "ordered"),
                clause(FIRST, "lowNonNegative")));
        assertThrows(Clause.NotOneClause.class, () -> Clause.Ref.merge(
                clause(FIRST, "ordered"),
                clause(FIRST, null)));
        assertThrows(Clause.NotOneClause.class, () -> Clause.Ref.merge(
                clause(FIRST, null),
                clause(FIRST, "ordered")));
    }
}
