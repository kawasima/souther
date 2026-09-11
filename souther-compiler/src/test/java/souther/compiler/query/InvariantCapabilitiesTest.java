package souther.compiler.query;

import souther.compiler.WhereItSits;
import souther.compiler.check.CapabilityResult;
import souther.compiler.check.ClauseDischarge;
import souther.compiler.check.StaticRoute;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the compiler says about each clause of an invariant: whether a construction of that type can
 * be judged safe from it, and by what kind of guard (spec §invariant-discharge-capability).
 *
 * <p>Once some clauses are statically dischargeable and others are not, the same {@code invariant}
 * keyword means two things to a reader, and nothing in the source distinguishes them. These pin the
 * answer, so the classification is the language's rather than whatever the checker manages today.
 */
class InvariantCapabilitiesTest {

    /** What the reading came to for one clause, which is what these are about. */
    private static CapabilityResult read(List<ClauseDischarge> clauses, int nth) {
        return clauses.get(nth).capability();
    }

    /** One part of a clause, carried by these routes — which is what most clauses come to. */
    private static CapabilityResult.Analyzed routed(StaticRoute... routes) {
        return CapabilityResult.Analyzed.routed(routes);
    }

    /** A bound the domain reasons over, which the check also names as a term: two ways to discharge
     *  one part, and an author needs only the weaker of them. Reported as the bound alone, the other
     *  was dropped where a reader could have used it. */
    private static CapabilityResult.Analyzed aBound() {
        return routed(new StaticRoute.AsABound(), new StaticRoute.AsATerm());
    }

    private static List<ClauseDischarge> of(String source, String type) {
        Compilation c = Compilation.ofDocuments(Map.of("a.sou", source), Set.of(), ModulePath.EMPTY);
        Map<TypeSymbol, List<ClauseDischarge>> caps =
                c.db().ask(new Shapes.InvariantCapabilities("m.a")).value();
        return caps == null ? List.of() : caps.getOrDefault(TypeSymbols.declared(new TypeKey("m.a", type)), List.of());
    }

    @Test
    void aRelationTheDomainReasonsOverIsDerivable() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Money = Int
                    invariant value >= 0
                """, "Money");
        assertEquals(1, clauses.size());
        assertEquals(aBound(), read(clauses, 0));
    }

    @Test
    void aLengthIsDerivableToo() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Lines = List<Int>
                    invariant List.length(value) >= 1
                """, "Lines");
        assertEquals(aBound(), read(clauses, 0));
    }

    @Test
    void aPredicateTakesAnExactMatch() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Code = String
                    invariant String.matches("[A-Z]{2}", value)
                """, "Code");
        assertEquals(routed(new StaticRoute.AsATerm()), read(clauses, 0));
    }

    /**
     * A clause that holds of every value asks nothing of a guard, and that is what is said of it.
     *
     * <p>Not a clause outside the fragment, which is what it was answered as: every part of it was
     * read, and what the reading owed was nothing, because the clause had already folded. Read off
     * the empty answer, an editor told an author that the static checker cannot represent
     * {@code 1 >= 0} and that no guard discharges it.
     */
    @Test
    void aClauseThatHoldsOfEveryValueIsSaidToHold() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Money = Int
                    invariant 1 >= 0
                """, "Money");
        assertEquals(new CapabilityResult.Decided(true), read(clauses, 0));
    }

    /**
     * And one that holds of none is said not to hold, rather than being answered as a bound.
     *
     * <p>The declaration is refused where the whole compiler runs — nothing satisfies its rules — but
     * this classification is asked of half-written source by an editor, so it answers for a
     * declaration a build would reject. Answered from what the obligations came to, the clause fell
     * past the fold and was read as a numeric relation, and an author was told that any guard
     * implying it discharges the construction. There is no guard that implies {@code 1 < 0}.
     */
    @Test
    void aClauseThatHoldsOfNoValueIsSaidNotTo() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Money = Int
                    invariant 1 < 0
                """, "Money");
        assertEquals(new CapabilityResult.Decided(false), read(clauses, 0));
    }

    /**
     * A clause that holds under a call the fold cannot take whole is answered the same way.
     *
     * <p>{@code Bool.not(1 < 0)} does not fold here — it is a call — and the reading under it does:
     * it negates what it reads, folds that, and comes back owing nothing. So the answer comes from a
     * walk that owed nothing rather than from the fold, and the two paths to {@code always holds}
     * are both taken by programs.
     */
    @Test
    void aClauseThatHoldsUnderACallIsSaidToHoldToo() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Money = Int
                    invariant Bool.not(1 < 0)
                """, "Money");
        assertEquals(new CapabilityResult.Decided(true), read(clauses, 0));
    }

    /**
     * And one that settles only once it is read as what it states.
     *
     * <p>{@code Int.compare(1, 2) >= 0} is a call to a reader folding what an author wrote, and is
     * the order of {@code 1} and {@code 2} to the walk that reads clauses, which reads a clause as
     * the comparisons it states before it decides anything. Folded before that walk, the clause fell
     * through to be read as a relation over two constants,
     * and an author was told that any guard implying it discharges the construction — for a clause
     * no guard implies and no value satisfies.
     */
    @Test
    void aClauseThatSettlesOnlyOnceItIsNormalizedIsSaidToSettle() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Money = Int
                    invariant Int.compare(1, 2) >= 0
                """, "Money");
        assertEquals(new CapabilityResult.Decided(false), read(clauses, 0));
    }

    /**
     * A conjunction a helper brings is one conjunct, and the half of it that settles does not settle
     * the clause.
     *
     * <p>Conjuncts are split from what the author wrote, so {@code a && b} inside a helper is not two
     * of them. The clause is the call, and what matters here is that {@code 1 >= 0} inside the
     * helper does not come back as a clause holding of every value. A reading that took the settled
     * half for the whole would say no guard is needed for a rule that plainly needs one.
     *
     * <p>Both routes, because the rule the helper states is read as the rule it is: the domain
     * reasons over {@code n >= 0} as a bound on the value, and the check names the call as a term
     * that a guard may state. The clause still needs one of them, which is the whole of what is
     * being held.
     */
    @Test
    void aConjunctionAHelperBringsDoesNotSettleFromOneHalf() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                let nonNegative (n: Int): Bool = 1 >= 0 && n >= 0
                data Money = Int
                    invariant nonNegative(value)
                """, "Money");
        assertEquals(1, clauses.size(), "one conjunct, because the author wrote one");
        assertEquals(routed(new StaticRoute.AsABound(), new StaticRoute.AsATerm()),
                read(clauses, 0),
                "the rule the helper states is a bound, and the call is a term a guard may state");
    }

    /**
     * A clause the reading could not type is not a conclusion about the clause.
     *
     * <p>This source names no type {@code Anything}, so the reading never began. Said as a shape the
     * check does not read, an author is told that their clause is outside the static fragment and
     * that no guard discharges it — a sentence about their model, printed because this compiler did
     * not get far enough to have an opinion. Which is the state an editor asks in most often.
     */
    @Test
    void aClauseTheReadingCouldNotTypeIsNotAConclusionAboutIt() {
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Kind = String
                    invariant match value with
                        | Anything -> true
                """, "Kind");
        assertInstanceOf(CapabilityResult.AnalysisStopped.class, read(clauses, 0));
    }

    @Test
    void eachClauseOfAConjunctionIsAnsweredOnItsOwn() {
        // the two clauses of #222's target: a length the domain reasons over, and a uniqueness it can
        // only compare for identity
        List<ClauseDischarge> clauses = of("""
                module m.a
                data Row = { product: String }
                data Lines = List<Row>
                    invariant List.length(value) >= 1 && List.allDistinctBy(.product, value)
                """, "Lines");
        assertEquals(2, clauses.size(), "one answer per clause");
        assertEquals(aBound(), read(clauses, 0));
        assertEquals(routed(new StaticRoute.AsATerm()), read(clauses, 1));
    }

    @Test
    void aClauseIsAnsweredAtItsOwnPosition() {
        String source = """
                module m.a
                data Row = { product: String }
                data Lines = List<Row>
                    invariant List.length(value) >= 1 && List.allDistinctBy(.product, value)
                """;
        List<ClauseDischarge> clauses = of(source, "Lines");
        assertEquals(4, WhereItSits.in(source, clauses.get(0).owed().clause()).line(), "both clauses are on the invariant's line");
        assertEquals(4, WhereItSits.in(source, clauses.get(1).owed().clause()).line());
        assertTrue(WhereItSits.in(source, clauses.get(0).owed().clause()).column() < WhereItSits.in(source, clauses.get(1).owed().clause()).column(),
                "in the order they are written, so a position picks one out");
    }

    @Test
    void aClauseThroughAHelperIsAnsweredAsWhatTheHelperSays() {
        // the helper is expanded before the clause is read, so `twice(value) >= 0` is arithmetic
        String source = """
                module m.a
                let twice (n: Int): Int = n * 2
                data Even = Int
                    invariant twice(value) >= 0
                """;
        List<ClauseDischarge> clauses = of(source, "Even");
        assertEquals(aBound(), read(clauses, 0));
        assertEquals(4, WhereItSits.in(source, clauses.get(0).owed().clause()).line(), "reported where it is written, not where it expands");
    }
}
