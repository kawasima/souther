package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.Prepared;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Sig;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Shapes;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value asked for under a sum is a value asked for under one of its cases, and the plan says
 * which cases there are rather than choosing one.
 *
 * <p>Where every case spreads the same declaration, a field of it is read through the sum: the path
 * names a position of the sum and no case of it (spec §sum-data), which is enough to read a rule at
 * and not enough to write a value with. So the reading and the writing part here, and what the
 * writing needs is the one thing the reading did not have to say.
 *
 * <p><b>What a plan must not do with such a demand is drop it.</b> A sum composes nothing out of
 * anything, so a whole value is chosen there and a path fixed below it has nowhere to be written —
 * the arrangement {@code ConstructionPlan.Result.Beyond} refuses at the depth figure, met at a
 * position instead of at a depth. Handed back as an ordinary plan, the composing satisfies it and
 * the caller's value is quietly missing from what was built.
 */
class APlanSaysWhichNarrowingADemandUnderASumIsOwedTest {

    /** The rules counting nothing, which is what every model here leaves them saying. */
    private static final ConstructionPlan.HowManyItHolds ANY =
            (_, _) -> new DeclaredBounds.CountRange(0, Integer.MAX_VALUE);


    /**
     * A list of items whose method is a sum of two cases, both spreading the same amount.
     *
     * <p>The shape a total over a sequence meets: the amount is one field, readable through the
     * sum, and each case has a field of its own so that neither is the whole of what a value there
     * is.
     */
    private static final String SHARED = """
            module g

            data Common = { amount: Int }
            data Card = { ...Common, issuer: Int }
            data Cash = { ...Common, note: Int }
            data Method = Card | Cash
            data Item = { method: Method }

            data Query = { item: Item }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    /** The amount, reached through the sum and under no case. */
    private static TermPath through() {
        return TermPath.of("query").then("item").then("method").then("amount");
    }

    /** The sum itself, which is where a narrowing is owed. */
    private static TermPath sum() {
        return TermPath.of("query").then("item").then("method");
    }

    /**
     * The demand comes back with the position and what stands there, in the order the sum declares
     * its cases.
     *
     * <p>The order is part of the answer. What the caller does with these is offer one value per
     * case, and a reader comparing two runs of this compiler is comparing lists — an order read off
     * a hash would make the same model answer differently on two days.
     */
    @Test
    void aDemandUnderASumNamesThePositionAndWhatStandsThere() {
        ConstructionPlan.Result result = planning(Set.of(through()));

        ConstructionPlan.Result.Unnarrowed owed = assertInstanceOf(
                ConstructionPlan.Result.Unnarrowed.class, result,
                "nothing said which case the value is, so there is no plan and this says what is"
                        + " missing");
        assertEquals(sum(), owed.at(), "the position a narrowing is owed at is the sum itself");
        assertEquals(List.of("Card", "Cash"),
                owed.narrowings().stream().map(Refinement::spelled).toList(),
                "and what stands there is its cases, in the order they are declared");
    }

    /**
     * With the case written in, the same demand plans, and the value is written at it.
     *
     * <p>The other half of the first. Without this, a plan that refused every demand under a sum
     * would pass — and what the caller is owed is not a refusal but somewhere to put the value once
     * it has said which case it is building.
     */
    @Test
    void theSameDemandUnderACasePlansAndTakesTheValueThere() {
        TermPath under = sum().refine(caseOf("Card")).then("amount");

        ConstructionPlan plan = assertInstanceOf(ConstructionPlan.Result.Planned.class,
                planning(Set.of(under)), "the case is stated, so the position is one the plan has")
                .plan();

        ConstructionPlan.Slot at = plan.slots().stream()
                .filter(each -> each.at().equals(under)).findFirst().orElseThrow();
        assertInstanceOf(ConstructionPlan.Leaf.Fixed.class, at.leaf(),
                "and the plan says the caller's value goes there");
    }

    /**
     * What is offered is the narrowings that leave something to be built, and an absence is not one
     * of them.
     *
     * <p>{@code None} settles the value rather than narrowing to something with positions under it,
     * so a caller that stated it would be asking for a value under a position holding none — which
     * the plan refuses outright. Offered as a way down, the one answer a caller could act on came
     * back beside one that ends the walk.
     */
    @Test
    void anAbsenceIsNotOfferedAsAWayDown() {
        TermPath under = TermPath.of("query").then("held").then("amount");

        ConstructionPlan.Result.Unnarrowed owed = assertInstanceOf(
                ConstructionPlan.Result.Unnarrowed.class, planningOf(OPTIONAL, Set.of(under)),
                "an optional holds nothing until something says the value is there");
        assertEquals(List.of("Some"),
                owed.narrowings().stream().map(Refinement::spelled).toList(),
                "and what is offered is the presence, which is what puts a position under it");
    }

    /** A record whose field is an optional of a record, which is where an absence is a distinction
     *  of the position and no way down to what the record holds. */
    private static final String OPTIONAL = """
            module g

            data Inner = { amount: Int }
            data Query = { held: Inner? }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    /**
     * A whole value asked for at the sum is not a demand under it, and nothing is owed.
     *
     * <p>The control. Read as "anything to do with a sum", the answer above would arrive wherever a
     * caller fixes a value at a position that happens to be one — which is every class of a sum
     * standing at a position, and none of them is missing anything.
     */
    @Test
    void aValueFixedAtTheSumItselfPlansWithNothingOwed() {
        ConstructionPlan plan = assertInstanceOf(ConstructionPlan.Result.Planned.class,
                planning(Set.of(sum())),
                "a value at the sum is a value at a position, whatever the position divides into")
                .plan();

        assertTrue(plan.slots().stream().anyMatch(each -> each.at().equals(sum())),
                () -> "and it is chosen at the sum: " + plan.slots().stream()
                        .map(ConstructionPlan.Slot::at).toList());
    }

    /**
     * The same sum with a collection declared after it, so that the walk meets the narrowing first.
     *
     * <p>What is asked of both at once: a value under the sum, which nothing has said which case
     * of, and a value inside the list.
     */
    private static final String BESIDE_A_LIST = """
            module g

            data Common = { amount: Int }
            data Card = { ...Common, issuer: Int }
            data Cash = { ...Common, note: Int }
            data Method = Card | Cash
            data Item = { method: Method, bag: List<Int> }

            data Query = { item: Item }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    /**
     * A field the model refuses outranks a narrowing owed at the field before it.
     *
     * <p>The two are not alike. A refusal is an answer about the value being built and holds
     * whatever the caller does next; a narrowing owed is a question put to whoever asked for the
     * value. A caller answered with the question states a narrowing, asks again, and is told there
     * was never a row — so the walk goes on past the question to find out whether there is one.
     *
     * <p>Held because the other way round reads like tidying. The narrowing is the first thing the
     * walk has in hand and returning it there is one line shorter, and what it costs is a round
     * trip through the caller that cannot end anywhere.
     */
    @Test
    void aRefusalFurtherAlongOutranksANarrowingOwedBeforeIt() {
        TermPath bag = TermPath.of("query").then("item").then("bag");

        ConstructionPlan.Result asked = ConstructionPlan.of(typeOf(BESIDE_A_LIST),
                TermPath.of("query"), ScopedDeclarations.wrapsOf(symbolsOf(BESIDE_A_LIST)),
                symbolsOf(BESIDE_A_LIST),
                ScopedDeclarations.of(symbolsOf(BESIDE_A_LIST)),
                Set.of(TermPath.of("query").then("item").then("method").then("amount"),
                        bag.element()),
                Requirements.NONE,
                (at, _) -> at.equals(bag) ? new DeclaredBounds.CountRange(0, 0)
                        : new DeclaredBounds.CountRange(0, Integer.MAX_VALUE));

        assertInstanceOf(ConstructionPlan.ModelRefusal.NoRoom.class,
                assertInstanceOf(ConstructionPlan.Result.Refused.class, asked,
                        "the list leaves no room, and no narrowing the caller states changes that")
                        .why(),
                "so what comes back is the refusal and not the narrowing owed at the field before"
                        + " it");
    }

    /**
     * And where the list has room, the narrowing owed is what comes back.
     *
     * <p>The control. Only what the rules leave the list differs between the two, so a walk that
     * returned the refusal whatever the count was would pass the one above.
     */
    @Test
    void aNarrowingOwedIsWhatComesBackWhereNothingIsRefused() {
        ConstructionPlan.Result asked = ConstructionPlan.of(typeOf(BESIDE_A_LIST),
                TermPath.of("query"), ScopedDeclarations.wrapsOf(symbolsOf(BESIDE_A_LIST)),
                symbolsOf(BESIDE_A_LIST),
                ScopedDeclarations.of(symbolsOf(BESIDE_A_LIST)),
                Set.of(TermPath.of("query").then("item").then("method").then("amount"),
                        TermPath.of("query").then("item").then("bag").element()),
                Requirements.NONE, ANY);

        assertEquals(TermPath.of("query").then("item").then("method"),
                assertInstanceOf(ConstructionPlan.Result.Unnarrowed.class, asked,
                        "nothing is refused, so what the caller is owed is the narrowing").at(),
                "at the position it is owed at");
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement caseOf(String leaf) {
        TypeSymbol named = TypeSymbols.declared(new TypeKey("g", leaf));
        return Refinement.of(ResolvedCase.of(CaseSelector.direct(named), List.of(named)));
    }

    private static ConstructionPlan.Result planning(Set<TermPath> decided) {
        return planningOf(SHARED, decided);
    }

    private static ConstructionPlan.Result planningOf(String source, Set<TermPath> decided) {
        return ConstructionPlan.of(typeOf(source), TermPath.of("query"),
                ScopedDeclarations.wrapsOf(symbolsOf(source)), symbolsOf(source),
                ScopedDeclarations.of(symbolsOf(source)), decided, Requirements.NONE, ANY);
    }

    private static Type typeOf(String source) {
        return readOf(source).sig().inputTypes().get(0);
    }

    private static Symbols symbolsOf(String source) {
        return readOf(source).symbols();
    }

    private record Read(Sig sig, Symbols symbols) {}

    private static Read readOf(String source) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
        Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                .filter(b -> b.name().equals("readArticles")).findFirst().orElseThrow();
        return new Read(sigs.get(spec.name()), Scopes.derived(compilation.db(), module).value());
    }
}
