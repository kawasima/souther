package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.Prepared;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Sig;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.Case;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.query.Shapes;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plan that stopped short of the positions a value has says so, and a plan that did not is a
 * different value.
 *
 * <p>Past {@link CompositionBudget#DEPTH_A_CONSTRUCTION_PLAN_DESCENDS} a position that is made of
 * positions is planned as one a whole value is chosen at — which is what a number or a record with
 * no fields is planned as, and those are the declarations saying so. Written as one word, this
 * compiler declining to look was said in the model's own words, and the composing afterwards
 * reported a value nothing was planned for as one the rules leave nothing at.
 *
 * <p><b>What follows from a plan being short is not that anything is owed.</b> Where nothing was
 * asked for under the position, a whole value chosen there may compose a row like any other, and a
 * row is an answer. So the figure is carried and stays quiet, and the case that says so is a
 * recursive type the suite has always had — see {@code
 * ACandidateIsProposedFromTheRuleAndNotTheCarrierAloneTest}, whose tree still comes back with rows
 * and nothing unresolved.
 */
class APlanSaysWhereItStoppedShortOfWhatTheValueHasTest {

    /** The rules counting nothing, which is what every model here leaves them saying. */
    private static final ConstructionPlan.HowManyItHolds ANY =
            (_, _) -> new DeclaredBounds.CountRange(0, Integer.MAX_VALUE);

    /** The rules leaving a collection holding none, which is the model settling that nothing can be
     *  placed inside one. */
    private static final ConstructionPlan.HowManyItHolds NONE_OF_THEM =
            (_, _) -> new DeclaredBounds.CountRange(0, 0);


    /**
     * Eight records each holding the next, which is one more than the figure allows.
     *
     * <p>Counted from the parameter: {@code query} is where the descent starts, so {@code L1} is
     * one down and {@code L8} is eight. What sits at {@code L8} is a record with a field, which is
     * a position made of positions everywhere else in this file — and the only reason it is not
     * planned as one is the figure.
     */
    private static final String DEEP = """
            module g

            data L8 = { v: Int }
            data L7 = { down: L8 }
            data L6 = { down: L7 }
            data L5 = { down: L6 }
            data L4 = { down: L5 }
            data L3 = { down: L4 }
            data L2 = { down: L3 }
            data L1 = { down: L2 }

            data Query = { down: L1 }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    /** A type written in terms of itself, which is the shape the figure was written for and the one
     *  the suite reaches. What holds the descent is a list, so the figure is met where a sequence is
     *  planned rather than where a record is. */
    private static final String TREE = """
            module g

            data Tree = { kids: List<Tree> }

            data Query = { tree: Tree }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    /**
     * One step above the figure the plan still composes, and at it the plan stops.
     *
     * <p>Both, because either alone says nothing. A plan that stops everywhere would pass a test
     * that only looked at the deep position, and a plan that never stops would pass one that only
     * looked at the shallow one.
     */
    @Test
    void aPositionAtTheFigureIsPlannedAsOneThisStoppedShortOf() {
        ConstructionPlan plan = planned(DEEP, Set.of(), Requirements.NONE);

        assertInstanceOf(ConstructionPlan.Built.class, at(plan, 7),
                "seven down is a record made of positions, and the plan composes it");
        ConstructionPlan.Slot stopped = assertInstanceOf(ConstructionPlan.Slot.class, at(plan, 8),
                "eight down is as deep as this plans, so a whole value is chosen there");
        assertInstanceOf(ConstructionPlan.Leaf.Beneath.class, stopped.leaf(),
                "and the plan says that is why, rather than saying what it says of a position the"
                        + " declarations put nothing under");
    }

    /**
     * The figure travels on the plan, and a plan that reached everything carries nothing.
     *
     * <p>The second half is the control. Read off a plan that stops nowhere, an account would say a
     * point is open for a figure somebody could raise wherever this compiler in fact looked at
     * everything.
     */
    @Test
    void thePlanCarriesTheFigureItStoppedAtAndNoOther() {
        assertEquals(Set.of(CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS),
                planned(DEEP, Set.of(), Requirements.NONE).cutBy(),
                "the plan stopped short, and says which figure stopped it");
        assertEquals(Set.of(), planned(SHALLOW, Set.of(), Requirements.NONE).cutBy(),
                "and a plan of the same shape inside the figure carries nothing");
    }

    /**
     * A list the plan stopped short of reading is not a list nothing was asked inside.
     *
     * <p>The sequence is planned by asking what stands at its element, and the answer to "is a
     * class being placed in here" is read off that. Where the reading gave up part-way, an empty
     * hand is not the model saying there is no class — which is the same rewrite one stage down as
     * the one this whole test is about.
     */
    @Test
    void aListThisStoppedShortOfReadingSaysSoRatherThanSayingNothingIsInside() {
        ConstructionPlan plan = planned(TREE, Set.of(), Requirements.NONE);

        assertEquals(Set.of(CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS), plan.cutBy(),
                "the descent through the lists gave up at the figure, and the plan carries it");
        assertTrue(plan.slots().stream().anyMatch(
                        each -> each.leaf() instanceof ConstructionPlan.Leaf.Beneath),
                "and the position it gave up at is one of the plan's own, not a fact that was"
                        + " dropped when the sequence chose to be composed whole: " + plan.slots());
    }

    /**
     * A value the caller fixed under the figure is not a plan at all.
     *
     * <p>There is no position in such a plan for the value to be written at, so a row composed
     * against it is a row the caller's own value is missing from — and every other answer this
     * could give is a row like any other with something quietly left out of it.
     */
    @Test
    void aValueFixedUnderTheFigureIsRefusedRatherThanDropped() {
        TermPath deeper = down(9);

        ConstructionPlan.Result asked = ConstructionPlan.of(typeOf(DEEP), TermPath.of("query"),
                ScopedDeclarations.wrapsOf(symbolsOf(DEEP)),
                symbolsOf(DEEP), ScopedDeclarations.of(symbolsOf(DEEP)), Set.of(deeper),
                Requirements.NONE, ANY);

        assertEquals(Set.of(CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS),
                assertInstanceOf(ConstructionPlan.Result.Beyond.class, asked,
                        "the caller asked for a position this plan never reached, so there is no"
                                + " plan to hand back").by(),
                "and it says which figure put the position out of reach");
    }

    /**
     * A narrowing stated under the figure is refused the same way.
     *
     * <p><b>The other half of the demand, and it has to be its own claim.</b> What a caller asks
     * for is the paths it fixed a value at and the narrowings it stated, and a reading of either
     * alone lets the other through: a value fixed at a field adds no requirement that the step was
     * taken, and a narrowing states no value. Held to only the first, this would drop a caller's
     * narrowing under the figure in silence — which is the same defect the figure had before any of
     * this, with the other half of the demand.
     */
    @Test
    void aNarrowingStatedUnderTheFigureIsRefusedRatherThanDropped() {
        ConstructionPlan.Result asked = ConstructionPlan.of(typeOf(DEEP), TermPath.of("query"),
                ScopedDeclarations.wrapsOf(symbolsOf(DEEP)),
                symbolsOf(DEEP), ScopedDeclarations.of(symbolsOf(DEEP)), Set.of(),
                Requirements.NONE.and(down(9), Refinement.of(new Case.Presence(true))),
                ANY);

        assertEquals(Set.of(CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS),
                assertInstanceOf(ConstructionPlan.Result.Beyond.class, asked,
                        "the caller stated something of a position this plan never reached").by(),
                "and it says which figure put the position out of reach");
    }

    /**
     * A demand under a collection the rules leave no room in is the model's answer, and the figure
     * below it is not named.
     *
     * <p>Both are true of this plan at once: the list holds nothing, and what the caller asked for
     * is deeper than the descent goes. Which is the ordinary way for them to meet — a demand this
     * compiler cannot reach is a demand it has not shown anything about, and the rules have already
     * settled it above. Named as the figure, an author would raise it and be told the list still
     * holds nothing.
     */
    @Test
    void aDemandUnderACollectionWithNoRoomIsTheModelsAnswerAndNotTheFigure() {
        ConstructionPlan.Result asked = ConstructionPlan.of(typeOf(TREE), TermPath.of("query"),
                ScopedDeclarations.wrapsOf(symbolsOf(TREE)),
                symbolsOf(TREE), ScopedDeclarations.of(symbolsOf(TREE)),
                Set.of(insideTheTree(6)), Requirements.NONE, NONE_OF_THEM);

        ConstructionPlan.ModelRefusal.NoRoom why = assertInstanceOf(
                ConstructionPlan.ModelRefusal.NoRoom.class,
                assertInstanceOf(ConstructionPlan.Result.Refused.class, asked,
                        "the rules leave the list nothing, which they say whatever this compiler"
                                + " went on to read").why(),
                "and it is the collection that has no room");
        assertEquals(1, why.needed(),
                "one is what the list would have to hold for the value to be placed in it");
        assertEquals(0, why.holds().most(), "and none is what the rules leave room for");
    }

    /**
     * The same demand where the rules leave room is the figure, and nothing about the model.
     *
     * <p>The control of the one above, and the half that says the refusal is not being read off the
     * demand. Only the count the rules leave differs between the two.
     */
    @Test
    void theSameDemandWhereThereIsRoomIsTheFigure() {
        ConstructionPlan.Result asked = ConstructionPlan.of(typeOf(TREE), TermPath.of("query"),
                ScopedDeclarations.wrapsOf(symbolsOf(TREE)),
                symbolsOf(TREE), ScopedDeclarations.of(symbolsOf(TREE)),
                Set.of(insideTheTree(6)), Requirements.NONE, ANY);

        assertEquals(Set.of(CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS),
                assertInstanceOf(ConstructionPlan.Result.Beyond.class, asked,
                        "nothing the rules say stops this, so what is in the way is the figure")
                        .by(),
                "and it says which figure put the position out of reach");
    }

    /** A position {@code deep} lists down the tree, which is deeper than the descent goes. */
    private static TermPath insideTheTree(int deep) {
        TermPath at = TermPath.of("query").then("tree");
        for (int each = 0; each < deep; each++) {
            at = at.then("kids").element();
        }
        return at;
    }

    /**
     * The same of a value fixed at a position the plan does reach.
     *
     * <p>The control for the one above. A plan that answered {@code Beyond} to any fixed value at
     * all would pass that test while refusing every row this compiler can write.
     */
    @Test
    void aValueFixedInsideTheFigureIsPlannedAt() {
        TermPath inside = down(8);

        ConstructionPlan.Result asked = ConstructionPlan.of(typeOf(DEEP), TermPath.of("query"),
                ScopedDeclarations.wrapsOf(symbolsOf(DEEP)),
                symbolsOf(DEEP), ScopedDeclarations.of(symbolsOf(DEEP)), Set.of(inside),
                Requirements.NONE, ANY);

        ConstructionPlan plan = assertInstanceOf(ConstructionPlan.Result.Planned.class, asked,
                "the position is one this plans at").plan();
        assertInstanceOf(ConstructionPlan.Leaf.Fixed.class,
                plan.slots().stream().filter(each -> each.at().equals(inside)).findFirst()
                        .orElseThrow().leaf(),
                "and it takes the caller's value, which is not the same as this having stopped"
                        + " there");
    }

    /**
     * A position at the figure that nothing composes anyway is the declarations' answer, not this
     * compiler's.
     *
     * <p>A number eight down would be chosen whole at any depth: there are no positions under it to
     * have stopped short of. Answered by the figure because the figure was reached, the plan would
     * carry one wherever a model happens to nest a plain field deeply, and every point it touched
     * would be reported as open for a figure that took nothing away.
     */
    @Test
    void aPositionNothingComposesIsTheModelsAnswerEvenAtTheFigure() {
        ConstructionPlan plan = planned(PLAIN_AT_THE_FIGURE, Set.of(), Requirements.NONE);

        assertInstanceOf(ConstructionPlan.Leaf.Open.class,
                assertInstanceOf(ConstructionPlan.Slot.class, at(plan, 8),
                        "a number is chosen whole wherever it sits").leaf(),
                "and nothing was given up to choose it, so the plan says what it says of any"
                        + " position the declarations put nothing under");
        assertEquals(Set.of(), plan.cutBy(),
                "a plan that gave nothing up carries no figure");
    }

    /** Eight down, and what sits there is a number: a position the declarations put nothing under,
     *  at exactly the depth the figure is reached. */
    private static final String PLAIN_AT_THE_FIGURE = """
            module g

            data L7 = { down: Int }
            data L6 = { down: L7 }
            data L5 = { down: L6 }
            data L4 = { down: L5 }
            data L3 = { down: L4 }
            data L2 = { down: L3 }
            data L1 = { down: L2 }

            data Query = { down: L1 }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    /** Seven deep, which is inside the figure. */
    private static final String SHALLOW = """
            module g

            data L7 = { v: Int }
            data L6 = { down: L7 }
            data L5 = { down: L6 }
            data L4 = { down: L5 }
            data L3 = { down: L4 }
            data L2 = { down: L3 }
            data L1 = { down: L2 }

            data Query = { down: L1 }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    /** The path {@code steps} field steps below the parameter. */
    private static TermPath down(int steps) {
        TermPath path = TermPath.of("query");
        for (int i = 0; i < steps - 1; i++) {
            path = path.then("down");
        }
        return steps == 9 ? path.then("v") : path;
    }

    /** The node {@code steps} field steps below the parameter, walked through the plan itself so
     *  that nothing here spells a path the plan does not. */
    private static ConstructionPlan.Node at(ConstructionPlan plan, int steps) {
        ConstructionPlan.Node node = plan.root();
        for (int i = 0; i < steps; i++) {
            node = assertInstanceOf(ConstructionPlan.Built.class, node,
                    "the plan composes down to " + i + " and this walk goes one further")
                    .under().get("down");
        }
        return node;
    }

    private static ConstructionPlan planned(String source, Set<TermPath> decided,
                                            Requirements additional) {
        return assertInstanceOf(ConstructionPlan.Result.Planned.class,
                ConstructionPlan.of(typeOf(source), TermPath.of("query"),
                        ScopedDeclarations.wrapsOf(symbolsOf(source)), symbolsOf(source),
                        ScopedDeclarations.of(symbolsOf(source)), decided, additional, ANY),
                "nothing here asks one position to be two things").plan();
    }

    private static souther.compiler.types.Type typeOf(String source) {
        return readOf(source).sig().inputTypes().get(0);
    }

    private static Symbols symbolsOf(String source) {
        return readOf(source).symbols();
    }

    private record Read(Sig sig, Symbols symbols) {}

    private static Read readOf(String source) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
        Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                .filter(b -> b.name().equals("readArticles")).findFirst().orElseThrow();
        return new Read(sigs.get(spec.name()), Scopes.derived(compilation.db(), module).value());
    }
}
