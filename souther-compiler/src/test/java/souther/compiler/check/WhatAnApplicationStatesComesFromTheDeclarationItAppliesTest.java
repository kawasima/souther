package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.observe.FieldTypes;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.Type;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What an application is declared to answer, and what says so.
 *
 * <p>An application is the one expression whose type is not in it. Every other form either carries
 * its own answer or is a step to a value that does; this one is the callee's declaration applied to
 * what the arguments state, and a reading with no step for applying anything answered that nothing
 * states what a call is — which is not a fact about any declaration.
 *
 * <p>Two things are held here and they pull apart. What is applied decides where the answer is read
 * from — a body, a signature, a library's declaration — and a definition of a module declares
 * nothing about its answer, so reading one means reading its body at the parameter types this
 * application gives it. That is the expansion below done again, so the cost of doing it is held too:
 * once per set of parameter types, and not once per place a call is written.
 */
class WhatAnApplicationStatesComesFromTheDeclarationItAppliesTest {

    private static final String MODULE = """
            module demo

            data Cost   = { amount: Int }
            data Draft  = { plannedCost: Cost }
            data Basket = { items: List<Int> }
            data Open   = { id: String }
            data Closed = { id: String, closedOn: Date }
            data Deal   = Open | Closed

            let costOf (d: Draft) = d.plannedCost
            let pick   (a: Cost, b: Cost) = a
            let itself (d: Deal) = d

            let draft  = Draft { plannedCost = Cost { amount = 1 } }
            let basket = Basket { items = [1, 2] }
            let closed = Closed { id = "d-1", closedOn = Date("2026-07-30") }

            let takenFromAHelper = costOf(draft).amount
            let readTwice        = pick(costOf(draft), costOf(draft))
            let heldAsTheSum     = itself(closed)
            let widened          = {
                let d: Deal = closed
                d
            }
            let built            = Date("2026-09-30")
            let held             = List.get(0, basket.items)
            let mapped           = List.map(n -> n + 1, basket.items)
            """;

    private final Compilation compilation = compiled();
    private final String module = compilation.modules().get(0);
    private final Symbols symbols = Scopes.derived(compilation.db(), module).value();
    private final Map<String, Hir.FnDef> values =
            compilation.db().ask(new Bodies.ModuleDefinitions(module)).value();

    private static Compilation compiled() {
        Compilation c = Compilation.ofSource(MODULE, "Main");
        c.answerEverything();
        return c;
    }

    /** The model these answers are read off compiles, so an answer of nothing here is this reading
     *  and not a name that reaches no declaration. */
    @Test
    void theModelUnderTestIsAccepted() {
        assertDoesNotThrow(() -> Compiler.compile(MODULE));
    }

    /** What a helper answers is what its body states, read with its parameters standing for what
     *  arrived — the step the expansion below takes at every call. */
    @Test
    void aHelpersCallIsWhatItsBodyStatesAtWhatItWasAppliedTo() {
        assertEquals(Type.INT, declaredTypeOf("takenFromAHelper"),
                "`costOf(draft)` is a `Cost`, so `.amount` off it is an `Int`");
    }

    /**
     * One reading of a definition per set of parameter types, however many places apply it.
     *
     * <p>Reading what an application answers is reading the callee's body, and a body that calls
     * bodies is the expansion below with nothing bounding it. What bounds it is that an answer is
     * about the parameter types and not about the call, so the second place asking takes the first
     * one's answer.
     *
     * <p>Counted at the world, which is asked once for each declaration a field is taken off. A
     * reading that walked the body again would ask it again.
     */
    @Test
    void aDefinitionIsReadOncePerSetOfParameterTypes() {
        Map<String, Integer> asked = new HashMap<>();
        Type twice = readingCounting(asked).declaredTypeOf(bodyOf("readTwice"));

        assertEquals("Cost", assertInstanceOf(Type.Ref.class, twice).name().name(),
                "the answer is what the helper's body states");
        assertEquals(1, asked.getOrDefault("Draft", 0),
                "and `costOf` was read once, though two arguments applied it to a `Draft`");
    }

    /** A library operation is its declared signature applied to what the arguments state — the same
     *  step, and the variables of the declaration settled by the arguments rather than by anything
     *  written here. */
    @Test
    void aLibrarySignatureIsSettledByWhatTheArgumentsState() {
        assertEquals(new Type.OptionOf(Type.INT), declaredTypeOf("held"),
                "`List.first` answers an `Option<'a>`, and the argument says what `'a` is");
    }

    /**
     * And an answer still holding a variable is not one this states.
     *
     * <p>A variable a function argument decides is decided by typing that argument, which this
     * reading does not do. Answered with the variable left in it, the answer would be a type nothing
     * here settled — and read as a type at all, a reader would take it for one.
     */
    @Test
    void andAnAnswerHoldingAVariableNothingSettledIsNotStated() {
        assertNull(declaredTypeOf("mapped"),
                "what `List.map` answers is decided by the block, which this reading does not type");
    }

    /** The namespace of a temporal applied builds a value of it, which the library says of itself
     *  and no other namespace says. */
    @Test
    void theNamespaceOfATemporalAppliedBuildsOne() {
        assertEquals(Type.DATE, declaredTypeOf("built"),
                "`Date(\"2026-09-30\")` is a `Date`");
    }

    /**
     * A parameter a declaration wrote as a sum stays the sum.
     *
     * <p>The case that arrived is not what the body was written against: a case argument widens to
     * its sum (spec §sum-data), and a body reading the name reads the sum. So what a parameter is
     * is the declaration's where it wrote one, and the argument's only where it did not.
     */
    @Test
    void aParameterWrittenAsASumStaysTheSum() {
        assertEquals("Deal",
                assertInstanceOf(Type.Ref.class, declaredTypeOf("heldAsTheSum")).name().name(),
                "`itself` takes a `Deal`, so what it answers is a `Deal` and not the case given");
    }

    /** And so does a binding the author wrote a type on, for the same reason: the annotation is what
     *  the reader of the name is looking at, and the value only says what it happens to be. */
    @Test
    void andSoDoesABindingWrittenWithOne() {
        assertEquals("Deal",
                assertInstanceOf(Type.Ref.class, declaredTypeOf("widened")).name().name(),
                "`let d: Deal = closed` puts a `Deal` in force");
    }

    private Type declaredTypeOf(String value) {
        return reading().declaredTypeOf(bodyOf(value));
    }

    private DeclaredTypeReading reading() {
        return readingOver(new ResolvedFieldTypes(symbols));
    }

    /** The same reading, over a world that records which declarations it was asked about. */
    private DeclaredTypeReading readingCounting(Map<String, Integer> asked) {
        FieldTypes world = new ResolvedFieldTypes(symbols);
        return readingOver(owner -> {
            asked.merge(owner.name(), 1, Integer::sum);
            return world.of(owner);
        });
    }

    private DeclaredTypeReading readingOver(FieldTypes world) {
        return new DeclaredTypeReading(
                new DeclarationFacts(new FieldRead(symbols, world, FieldRead.Unreadable.REFUSED)),
                values, compilation.db().ask(new Bodies.Reachable(module)).value());
    }

    /** The body of {@code name} as the module settled it. */
    private Hir.Expr bodyOf(String name) {
        return assertInstanceOf(Hir.FnBody.Written.class, values.get(name).body()).expr();
    }
}
