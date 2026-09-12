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
import souther.compiler.types.CaseSelector;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plan is made where the requirements of the positions it builds at are put together.
 *
 * <p>A path states what has to hold for the position it names to exist, and a caller may have
 * requirements besides — a class selects a refinement at its own position, which no path says. Both
 * go into one plan (ADR-0114: coverage and construction use one merge and neither keeps an account
 * of its own), and the defect this exists over is a caller that handed over the first and left the
 * second empty: the boundary search fixed a value at {@code query.tag@Tag} and planned against
 * {@code Requirements.NONE}, so the plan built the position as the sum and the row offered for a
 * line under {@code Tag} carried a {@code NoTag}.
 *
 * <p>What holds it is that a plan is only ever made by {@link ConstructionPlan#of}, which is where
 * the two are merged. The scan below is a tripwire under that.
 */
class APlanIsMadeWhereItsRequirementsArePutTogetherTest {

    /** The rules counting nothing, which is what every model here leaves them saying. */
    private static final ConstructionPlan.HowManyItHolds ANY =
            (_, _) -> new DeclaredBounds.CountRange(0, Integer.MAX_VALUE);

    private static final String FILTER = """
            module g

            data Tag = String
            data NoTag
            data Filter = Tag | NoTag

            data Query = { tag: Filter, other: Tag }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    @Test
    void aPlanHasNoConstructorAReaderCanReach() {
        for (Constructor<?> each : ConstructionPlan.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(each.getModifiers()),
                    "a caller could hold a plan its requirements were never put together for: "
                            + each);
        }
        assertEquals(0, ConstructionPlan.class.getConstructors().length,
                "and none is reachable by reflection either");
    }

    /**
     * A path fixed under a case carries the case into the plan, with nothing added by the caller.
     *
     * <p>The invariant itself. {@code query.tag@Tag} says the position had to be a {@code Tag} for
     * there to be a value at it, and a caller that says no more than that has still said it — so the
     * plan builds a {@code Tag} at the refined position rather than a {@code Filter} at the
     * unrefined one.
     */
    @Test
    void aFixedPathUnderACaseIsPlannedAsThatCase() {
        TermPath under = TermPath.of("query").then("tag").refine(caseOf("Tag"));

        ConstructionPlan plan = planned(Set.of(under), Requirements.NONE);

        List<String> slots = plan.slots().stream().map(each -> each.at().toString()).toList();
        assertTrue(slots.contains(under.toString()),
                "the position the value was fixed at is the position the plan builds at: " + slots);
        assertFalse(slots.contains("query.tag"),
                "and the unrefined position is not a second name for it: " + slots);
        ConstructionPlan.Slot fixed = plan.slots().stream()
                .filter(each -> each.at().equals(under)).findFirst().orElseThrow();
        assertInstanceOf(ConstructionPlan.Leaf.Fixed.class, fixed.leaf(),
                "it is the caller's value that goes there");
        assertEquals("Tag", souther.compiler.types.Type.show(fixed.type()),
                "built as the case and not as the sum");
    }

    /**
     * And two requirements at one position that disagree are a row no value is.
     *
     * <p>Which is an answer about the model and not a fall-short of this search, so it comes back
     * said rather than thrown: a caller asking for a {@code NoTag} at a position it also fixed a
     * {@code Tag} value under has asked for something no row is.
     */
    @Test
    void twoNarrowingsOfOnePositionAreNoRow() {
        TermPath tag = TermPath.of("query").then("tag");

        ConstructionPlan.Result asked = ConstructionPlan.of(typeOf(), TermPath.of("query"),
                symbols(), ScopedDeclarations.of(symbols()), Set.of(tag.refine(caseOf("Tag"))),
                Requirements.NONE.and(tag, caseOf("NoTag")), ANY);

        ConstructionPlan.ModelRefusal.Conflict against = assertInstanceOf(
                ConstructionPlan.ModelRefusal.Conflict.class,
                assertInstanceOf(ConstructionPlan.Result.Refused.class, asked,
                        "no value at `query.tag` is both a `Tag` and a `NoTag`").why(),
                "and it is the model settling it, said as the two it would have to be");
        assertEquals(tag, against.at());
        assertEquals(Set.of("Tag", "NoTag"),
                Set.of(against.one().spelled(), against.other().spelled()),
                "and the answer says which two it would have to be");
    }

    /**
     * A value fixed at a position that is also required to be narrowed is two accounts of one
     * location, and is refused.
     *
     * <p>What ADR-0114 keeps apart, said as a contract rather than answered by preferring one of
     * the two: a class that narrows states the narrowing and does not also fix a value there,
     * because the value stands at the narrowed position and is chosen there. Nothing a model writes
     * reaches it — the classes that narrow offer no value to fix and the ones that offer a value
     * narrow nothing — and it is exactly the arrangement an optional's classes had before they said
     * which narrowing they were, so leaving the API able to express it leaves the next producer able
     * to write it.
     */
    @Test
    void aValueFixedWhereANarrowingIsRequiredIsTwoAccountsOfOnePosition() {
        TermPath tag = TermPath.of("query").then("tag");

        IllegalStateException said = assertThrows(IllegalStateException.class,
                () -> ConstructionPlan.of(typeOf(), TermPath.of("query"), symbols(),
                        ScopedDeclarations.of(symbols()), Set.of(tag),
                        Requirements.NONE.and(tag, caseOf("Tag")), ANY));

        assertTrue(said.getMessage().contains("query.tag") && said.getMessage().contains("Tag"),
                "the answer names the position said twice: " + said.getMessage());
    }

    private static final String HELD = """
            module g

            data Tag = String
            data NoTag
            data Filter = NoTag | Tag

            data Query = { tag: Filter?, other: Tag }
            data Page = { count: Int }

            behavior readArticles : (query: Query) -> Page
            """;

    /**
     * A second narrowing of a position an absence settled is asked about nothing.
     *
     * <p>A refinement does not move to another position, so it is written at the same path the
     * absence left — `x@None` and not something below it. Read as "not below", it would be let
     * through, and the plan would answer `None` for a caller that asked for a `Tag`.
     */
    @Test
    void aNarrowingUnderAnAbsenceIsAskedAboutNothing() {
        TermPath tag = TermPath.of("query").then("tag");
        TermPath absent = tag.refine(Refinement.of(new Case.Presence(false)));

        IllegalStateException said = assertThrows(IllegalStateException.class,
                () -> ConstructionPlan.of(heldType(), TermPath.of("query"), heldSymbols(),
                        ScopedDeclarations.of(heldSymbols()), Set.of(),
                        Requirements.NONE.and(tag, Refinement.of(new Case.Presence(false)))
                                .and(absent, caseOf("Tag")),
                        ANY));

        assertTrue(said.getMessage().contains("query.tag@None"),
                "the answer names the position that holds no value: " + said.getMessage());
    }

    /**
     * And a value fixed under one is too, which no requirement would have shown.
     *
     * <p>A field step adds no requirement, so `x@None.foo` appears nowhere in what the caller
     * stated — only in what it fixed. Read off the requirements alone the value is dropped in
     * silence, which is the half of a demand this check exists to stop being read on its own.
     */
    @Test
    void aValueFixedUnderAnAbsenceIsAskedAboutNothing() {
        TermPath tag = TermPath.of("query").then("tag");
        TermPath absent = tag.refine(Refinement.of(new Case.Presence(false)));

        IllegalStateException said = assertThrows(IllegalStateException.class,
                () -> ConstructionPlan.of(heldType(), TermPath.of("query"), heldSymbols(),
                        ScopedDeclarations.of(heldSymbols()), Set.of(absent.then("value")),
                        Requirements.NONE.and(tag, Refinement.of(new Case.Presence(false))),
                        ANY));

        assertTrue(said.getMessage().contains("query.tag@None.value"),
                "the answer names the value fixed where nothing stands: " + said.getMessage());
    }

    private static souther.compiler.types.Type heldType() {
        return readOf(HELD).sig().inputTypes().get(0);
    }

    private static Symbols heldSymbols() {
        return readOf(HELD).symbols();
    }

    private static ConstructionPlan planned(Set<TermPath> decided, Requirements additional) {
        return assertInstanceOf(ConstructionPlan.Result.Planned.class,
                ConstructionPlan.of(typeOf(), TermPath.of("query"), symbols(),
                        ScopedDeclarations.of(symbols()), decided, additional, ANY),
                "nothing here asks one position to be two things").plan();
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement caseOf(String leaf) {
        souther.compiler.types.TypeSymbol named = TypeSymbols.declared(new TypeKey("g", leaf));
        return Refinement.of(souther.compiler.types.ResolvedCase.of(
                CaseSelector.direct(named), java.util.List.of(named)));
    }

    /** The behavior's one parameter type, and the names it is read against. */
    private static souther.compiler.types.Type typeOf() {
        return read().sig().inputTypes().get(0);
    }

    private static Symbols symbols() {
        return read().symbols();
    }

    private record Read(Sig sig, Symbols symbols) {}

    private static Read read() {
        return readOf(FILTER);
    }

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
