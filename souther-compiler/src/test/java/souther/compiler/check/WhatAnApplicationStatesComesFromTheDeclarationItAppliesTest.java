package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.meta.ModulePath;
import souther.compiler.observe.FieldTypes;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            data Words  = { items: List<String> }
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

            let ints    = Basket { items = [1, 2] }
            let strings = Words  { items = ["a"] }

            let after (a, b) = List.drop(List.length(a), b)

            let atTwoElements = after(after(strings.items, ints.items), strings.items)

            data Item  = { id: String }
            data Shelf = { items: List<Item> }

            let shelf = Shelf { items = [] }

            let byABlock = List.distinctBy(i -> i.id, shelf.items)
            let widened          = {
                let d: Deal = closed
                d
            }
            let built            = Date("2026-09-30")
            let held             = List.get(0, basket.items)
            let mapped           = List.map(n -> n + 1, basket.items)
            """;

    /** The same declarations, with a call the check refuses: what arrives at {@code costOf} is no
     *  {@code Draft}. An editor reads text like this — it is what a body looks like while it is
     *  being written — so what this reading says about it is a question with an answer. */
    private static final String APPLIED_TO_WHAT_IT_DOES_NOT_TAKE = """
            module demo

            data Cost   = { amount: Int }
            data Draft  = { plannedCost: Cost }
            data Basket = { items: List<Int> }

            let costOf (d: Draft) = d.plannedCost

            let basket = Basket { items = [1, 2] }

            let wrong = costOf(basket).amount
            """;

    /** A module whose published definition applies one of its own to what it does not take. Its
     *  bodies are closed for a reader before anything types them, so this is what an expansion
     *  holding an argument the declaration does not admit is reached through. */
    private static final List<String> ACROSS_ONE_THAT_DOES_NOT_CHECK = List.of("""
            module lib exposing ( Draft, Basket, wrong )

            data Cost   = { amount: Int }
            data Draft  = { plannedCost: Cost }
            data Basket = { items: List<Int> }

            let itself (d: Draft) = d

            let wrong (b: Basket) = itself(b)
            """, """
            module app

            import lib as l ( Draft, Basket, wrong )

            let basket = Basket { items = [1, 2] }

            let read = wrong(basket)
            """);

    /** Constructions the declarations do not admit: one given more values than what it builds is
     *  written with, and one given a value of another type than it takes. */
    private static final String CONSTRUCTED_FROM_ANOTHER_NUMBER = """
            module demo

            data AmountN = Int

            let overWritten = AmountN(1, 2)

            let ofNoString  = Date(1)
            """;

    /**
     * A module publishing a definition that calls one of its own, and a module reading it.
     *
     * <p>What arrives at the reader is the published body with the call already put in place of —
     * which is how a definition of another module is expanded here — so an expansion is what this
     * reading meets when it walks the imported definition. It is reached from inside and handed in
     * by nobody, which is why it took an import to find.
     *
     * <p>{@code inner} takes the sum and is given a case. What the callee declared is what its body
     * was written against, so the binding the expansion wrote holds the sum.
     */
    private static final List<String> ACROSS_A_PUBLISHED_DEFINITION = List.of("""
            module lib exposing ( Deal, Open, Closed, widened, tail, each )

            data Open   = { id: String }
            data Closed = { id: String, closedOn: Date }
            data Deal   = Open | Closed

            let inner (d: Deal) = d

            let widened (c: Closed) = inner(c)

            let tail (xs: List<Int>) = List.drop(1, xs)

            let each (xs: List<Int>) = List.map(n -> n + 1, xs)
            """, """
            module app

            import lib as l ( Deal, Open, Closed, widened, tail, each )

            data Basket = { items: List<Int> }

            let closed = Closed { id = "d-1", closedOn = Date("2026-07-30") }
            let basket = Basket { items = [1, 2] }

            let read    = widened(closed)
            let dropped = tail(basket.items)
            let mapped  = each(basket.items)
            """);

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

    /** And so is the pair, which is the other model these answers are read off. A name one of them
     *  does not publish would leave the reading nothing to reach rather than nothing to say. */
    @Test
    void andSoIsTheModelReadAcrossTwoOfThem() {
        assertDoesNotThrow(() -> Compiler.compileModules(ACROSS_A_PUBLISHED_DEFINITION));
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

    /**
     * One definition read at two sets of parameter types in one walk is two answers.
     *
     * <p>A module writes no generics, but its definitions are not monomorphic for that: what a
     * parameter is is worked out from the body, and a use that says only that two positions hold the
     * same thing leaves a variable, which the compiler writes back onto the declaration. Each call
     * decides it, so one definition really is read at two things — and a reading holding one answer
     * per callee would hand the second reading the first one's.
     *
     * <p>Both readings are in one walk, which is where the answers could come apart: the inner
     * application is read to settle the outer one's, so what the outer answers is what it states at
     * its own parameters and not what the inner one left behind.
     */
    @Test
    void oneDefinitionAtTwoSetsOfParameterTypesIsTwoAnswers() {
        assertEquals(Type.list(Type.STRING), declaredTypeOf("atTwoElements"),
                "the outer `after` answers what its second argument holds, whatever the inner one"
                        + " was applied to");
    }

    /**
     * A declaration left open where a function would close it is still admitted.
     *
     * <p>{@code List.distinctBy} relates its key function to nothing else it wrote: what the key
     * answers appears at no other position, so the arguments settle everything the answer needs and
     * the function's own variable stays open. Held to a settled declaration, the call would be
     * refused for the one position the arguments were never going to reach — and this reading does
     * not type a function argument, which is the whole reason that position is open.
     */
    @Test
    void aDeclarationLeftOpenWhereAFunctionWouldCloseItIsStillAdmitted() {
        assertEquals(Type.list(Type.ref(TypeSymbols.declared(new TypeKey("demo", "Item")))),
                declaredTypeOf("byABlock"),
                "`List.distinctBy` answers a list of what it was given, whatever the key answers");
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

    /**
     * An application the declaration does not admit states nothing.
     *
     * <p>What a declaration states about an application is what it states when applied to what it
     * takes. Read off the parameter types alone, an argument of any other type went unnoticed and
     * the body was read at the parameters the declaration wrote — so a call the check refuses came
     * back with a type, and every field taken off it after that was answered too. That is a type
     * invented for a call that cannot happen, which is the one thing this reading is for not doing.
     *
     * <p>Both halves are held. The check refuses the call, and this reading says nothing about it;
     * either alone would leave the other free to move.
     */
    @Test
    void anApplicationTheDeclarationDoesNotAdmitStatesNothing() {
        assertThrows(CompileException.class,
                () -> Compiler.compile(APPLIED_TO_WHAT_IT_DOES_NOT_TAKE),
                "the check refuses a `Basket` where a `Draft` is taken");

        assertNull(declaredTypeIn(APPLIED_TO_WHAT_IT_DOES_NOT_TAKE, "wrong"),
                "and nothing states what a call the declarations do not admit answers");
    }

    /**
     * And so does a construction the declarations do not admit.
     *
     * <p>What a declaration says about being applied is how many values it takes and what each
     * position takes, and it says both whether what is applied is a signature or a name that builds
     * something. Held only where a signature was read, a construction answered its own type for any
     * arguments at all — the same hole as above, at the arms that were not asking.
     */
    @Test
    void andSoDoesAConstructionTheDeclarationsDoNotAdmit() {
        assertThrows(CompileException.class,
                () -> Compiler.compile(CONSTRUCTED_FROM_ANOTHER_NUMBER),
                "the check refuses both of these constructions");

        assertNull(declaredTypeIn(CONSTRUCTED_FROM_ANOTHER_NUMBER, "overWritten"),
                "a newtype is written with one value, so nothing states what two of them build");
        assertNull(declaredTypeIn(CONSTRUCTED_FROM_ANOTHER_NUMBER, "ofNoString"),
                "and a temporal is built from a string, not from whatever stands there");
    }

    /**
     * A definition another module published is read through the expansion it arrives with.
     *
     * <p>A published body has the definitions it calls already put in place of the calls, and this
     * reading walks published bodies like any other — so what it meets there is an expansion, and
     * meeting one is ordinary rather than a tree somebody handed over by mistake. Which is what
     * makes an input domain read off the callers wrong: this one is reached from inside.
     *
     * <p>And the binding that expansion wrote holds what the callee declared. The case that arrived
     * is not what the body was written against, so a reading that took the argument's own type
     * answered {@code Closed} where the declaration says the sum.
     */
    @Test
    void aDefinitionAnotherModulePublishedIsReadThroughItsExpansion() {
        assertEquals("Deal",
                assertInstanceOf(Type.Ref.class,
                        declaredTypeAcross(ACROSS_A_PUBLISHED_DEFINITION, "app", "read"))
                        .name().name(),
                "`inner` takes a `Deal`, so the binding the expansion wrote holds a `Deal`");
    }

    /**
     * And what an expansion answers is what the callee declared, where this application settled it.
     *
     * <p>An expansion is one application of a declaration, and it holds all three of what that
     * declaration takes, what it answers, and what was given to it — in variables minted for this
     * one copy. Read as the bindings alone, the declared result went unread and what the call
     * answered fell to whatever its body happened to state: {@code List.drop} answers what it was
     * given a list of, and its body is a fold, so a call still written as one answered while the
     * same call expanded answered nothing.
     */
    @Test
    void andWhatItAnswersIsWhatTheCalleeDeclaredWhereThisApplicationSettledIt() {
        assertEquals(Type.list(Type.INT),
                declaredTypeAcross(ACROSS_A_PUBLISHED_DEFINITION, "app", "dropped"),
                "`List.drop` answers a list of what it was given, and this one was given `Int`s");
    }

    /**
     * An expansion holding an argument the declaration does not admit states nothing.
     *
     * <p>Reached because a module's bodies are closed for whoever imports them before anything types
     * them, so a published definition that will not check is published all the same. What the
     * deciding does there is settle variables, and it says of itself that it judges no shape: an
     * argument of a type the declaration never wrote carries no variable to disagree about, so it
     * passes the deciding untouched. Holding it to what the declaration states is the other half,
     * and it is the same half a written call has.
     */
    @Test
    void anExpansionHoldingWhatTheDeclarationDoesNotTakeStatesNothing() {
        assertThrows(CompileException.class,
                () -> Compiler.compileModules(ACROSS_ONE_THAT_DOES_NOT_CHECK),
                "the check refuses a `Basket` where a `Draft` is taken, wherever it is written");

        assertNull(declaredTypeAcross(ACROSS_ONE_THAT_DOES_NOT_CHECK, "app", "read"),
                "and nothing states what an application the declarations do not admit answers");
    }


    /**
     * And a declaration whose answer the arguments do not settle states nothing, expanded or not.
     *
     * <p>What {@code List.map} answers is decided by the function it was given, which this reading
     * does not type — so a call of it states nothing, and the same call inside a published body
     * states nothing for the same reason. Read by falling to the callee's body where the
     * declaration did not settle, the expanded one would answer by whatever shape that body
     * happened to have, and one declaration would state two things by how the call reached here.
     */
    @Test
    void andADeclarationTheArgumentsDoNotSettleStatesNothingExpandedOrNot() {
        assertNull(declaredTypeOf("mapped"),
                "written out, `List.map` states nothing this reading can settle");
        assertNull(declaredTypeAcross(ACROSS_A_PUBLISHED_DEFINITION, "app", "mapped"),
                "and expanded into a published body it states the same nothing");
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

    /** What the declarations of {@code source} state about the value it declares as {@code name} —
     *  for a model this class does not hold, read the same way this one is. */
    private static Type declaredTypeIn(String source, String name) {
        Compilation read = Compilation.ofSource(source, "Main");
        read.answerEverything();
        return declaredTypeOf(read, read.modules().get(0), name);
    }

    /** The same, for a value one module of a compile of several declares. */
    private static Type declaredTypeAcross(List<String> sources, String module, String name) {
        Compilation read = Compilation.ofSources(sources, ModulePath.EMPTY);
        read.answerEverything();
        return declaredTypeOf(read, module, name);
    }

    private static Type declaredTypeOf(Compilation read, String module, String name) {
        Symbols scope = Scopes.derived(read.db(), module).value();
        Map<String, Hir.FnDef> declared =
                read.db().ask(new Bodies.ModuleDefinitions(module)).value();
        return new DeclaredTypeReading(
                new DeclarationFacts(new FieldRead(scope, new ResolvedFieldTypes(scope),
                        FieldRead.Unreadable.REFUSED)),
                declared, read.db().ask(new Bodies.Reachable(module)).value())
                .declaredTypeOf(assertInstanceOf(Hir.FnBody.Written.class,
                        declared.get(name).body()).expr());
    }

    /** The body of {@code name} as the module settled it. */
    private Hir.Expr bodyOf(String name) {
        return assertInstanceOf(Hir.FnBody.Written.class, values.get(name).body()).expr();
    }
}
