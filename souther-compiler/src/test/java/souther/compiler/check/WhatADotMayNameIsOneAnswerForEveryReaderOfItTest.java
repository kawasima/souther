package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.observe.FieldTypes;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ExampleExecutions;
import souther.compiler.query.Scopes;
import souther.compiler.sites.Evidence;
import souther.compiler.sites.SemanticSnapshot;
import souther.compiler.sites.TypeFact;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a {@code .} written on a value may name, and who says so.
 *
 * <p>Two questions stand either side of this one and neither is it. What a value of a declaration is
 * <em>laid out</em> as is {@link FieldTypes}', and a sum lays out nothing — a value of it is a value
 * of one of its cases. What a value standing here makes <em>readable</em> is this, and at a sum it is
 * the names every one of its cases spreads, because every value of it carries them. The two are one
 * map at a record, which is what makes taking the first for the second look right until a sum
 * arrives.
 *
 * <p>So the reading is {@link FieldRead}'s, and the readers that cross a {@code .} — the elaboration
 * that types what a text wrote, the walk that says what declarations already state about an
 * expression, the snapshot an editor asks what may follow a {@code .} — read it rather than each
 * working out a surface of its own. The last of these is why the agreement is checked and not left
 * to follow from one implementation: an author offered a name the compiler will refuse, or refused a
 * name it accepts, is the defect whichever of the two is right.
 */
class WhatADotMayNameIsOneAnswerForEveryReaderOfItTest {

    /**
     * A sum whose cases share a spread, a case that declares one name of its own, and two names
     * worn over things that do have fields.
     *
     * <p>{@code Wrapped} and {@code Named} are the two directions a name can be worn: over a record,
     * and over the sum itself. Both matter, because a reading that looked through the name would
     * reach a surface in either case, and they are different surfaces.
     */
    private static final String MODEL = """
            module demo

            data Common  = { id: String }
            data Open    = { ...Common }
            data Closed  = { ...Common, closedOn: Date }
            data Deal    = Open | Closed
            data Wrapped = Closed
            data Named   = Deal
            data Holder  = { deal: Deal }

            let held  = Holder { deal = Closed { id = "d-1", closedOn = Date("2026-07-30") } }
            let taken = held.deal.id
            """;

    private static final Compilation COMPILATION = compiled();

    private final Symbols symbols = Scopes.derived(COMPILATION.db(), "demo").value();

    /**
     * The surface is read against the declarations as this text resolves them, and not against what
     * the check settled.
     *
     * <p>Because the surface is what a check goes on. A reading held to an accepted program would be
     * a reading of what this answer already decided, so a change here that stopped a model compiling
     * would come back as a setup that could not be built rather than as the surface that moved.
     */
    private final FieldTypes world = new ResolvedFieldTypes(symbols);
    private final FieldRead read =
            new FieldRead(symbols, world, FieldRead.Unreadable.REFUSED);

    // --- what one position makes readable -------------------------------------------------------

    /**
     * At a record, what is readable is the declaration's own layout.
     *
     * <p>Stated as the layout the world answers with rather than as a map written out here: the two
     * being the same answer is the property, and a literal on both sides would hold while neither
     * was what the world says.
     */
    @Test
    void atARecordWhatIsReadableIsWhatTheDeclarationIsLaidOutAs() {
        assertEquals(List.copyOf(world.of(named("Closed")).entrySet()),
                List.copyOf(read.at(Type.ref(named("Closed"))).entrySet()),
                "a record makes its own fields readable, in the order it is laid out");
        assertTrue(read.at(Type.ref(named("Closed"))).containsKey("closedOn"),
                "the model under test declares a record with a name of its own");
    }

    /**
     * At a sum, a name every case spreads is readable, and it holds what the declaration that wrote
     * it says.
     *
     * <p>Read off {@code Common} — the declaration the spread came from — rather than off a case
     * that happens to carry it. What makes the name readable at the sum is that it was written once
     * and spread, so that is where what it holds is read from.
     */
    @Test
    void atASumANameEveryCaseSpreadsIsReadable() {
        assertEquals(world.of(named("Common")).get("id"), read.of(Type.ref(named("Deal")), "id"),
                "the name every case spreads is readable, holding what the spread declares");
        assertEquals(Type.STRING, read.of(Type.ref(named("Deal")), "id"),
                "which is a `String` in the model under test");
    }

    /** And a name only one case declares is not, however many values in hand carry it. */
    @Test
    void atASumANameOnlyOneCaseDeclaresIsNot() {
        assertTrue(world.of(named("Closed")).containsKey("closedOn"),
                "one case declares it");
        assertNull(read.of(Type.ref(named("Deal")), "closedOn"),
                "and it is not readable without opening that case");
    }

    /** And a name nothing declares is not, which is the same answer said of a different absence. */
    @Test
    void andANameNothingDeclaresIsNot() {
        assertNull(read.of(Type.ref(named("Deal")), "nothing"),
                "no case declares it, so a value of the sum carries no such name");
    }

    /**
     * A name worn over a value is read as itself, and what it wraps is not reached through it.
     *
     * <p>Both directions of wearing one: over a record, whose fields would otherwise be offered at
     * the name, and over a sum, whose shared names would. A value written {@code Wrapped(c)} is a
     * {@code Wrapped}, and what a {@code .} on it names is the one field it is written with.
     */
    @Test
    void aNameWornOverAValueIsReadAsItselfAndNotThroughToWhatItWraps() {
        assertEquals(Type.ref(named("Closed")), read.of(Type.ref(named("Wrapped")), "value"),
                "a newtype makes its own `value` readable");
        assertNull(read.of(Type.ref(named("Wrapped")), "id"),
                "and not a field of the record it wraps");
        assertNull(read.of(Type.ref(named("Named")), "id"),
                "nor a name the sum it wraps makes readable");
        assertEquals(Type.STRING, read.of(Type.ref(named("Deal")), "id"),
                "which is readable on the sum itself, so the barrier is the name and not the shape");
    }

    /**
     * And a world does not decide what a worn name makes readable either.
     *
     * <p>A newtype is written with one implicit field and that is the whole of its surface. Read as
     * whatever the world holds for that declaration, a world with more to say about it would widen
     * what a name makes readable — the same thing a world adding a name to a shape's surface would
     * do, said of the other half of this reading.
     */
    @Test
    void aWorldDoesNotDecideWhatAWornNameMakesReadable() {
        FieldTypes inventsOne = _ -> Map.of("value", Type.INT, "andAnother", Type.STRING);
        FieldRead reading = new FieldRead(symbols, inventsOne, FieldRead.Unreadable.REFUSED);

        assertEquals(Map.of("value", Type.INT), reading.at(Type.ref(named("Wrapped"))),
                "a name makes its one written field readable and nothing the world adds");
        assertNull(reading.of(Type.ref(named("Wrapped")), "andAnother"),
                "and the name the world added is not looked up either");
    }

    // --- and one answer for every reader of it --------------------------------------------------

    /**
     * The three readers of a {@code .} answer the same thing about one written {@code held.deal.id}.
     *
     * <p>Each is asked in its own words, because that is what a reader of it has: the elaboration
     * settles what a body's expression is and refuses a model that says otherwise, the walk over the
     * declarations answers a type, and the snapshot answers the names an author may write. All three
     * are held to {@code String} rather than to each other — two readings compared with each other
     * agree while both are wrong, and both were.
     */
    @Test
    void thePeopleWhoCrossADotAnswerTheSameThing() {
        // Held to what the check settled, because these are readers of an accepted program and that
        // is the world they are handed.
        assertNotNull(ExampleExecutions.of(COMPILATION.db(), "demo"),
                "the model under test is accepted, or these readers have no program to read");
        FieldTypes checked = ExampleExecutions.of(COMPILATION.db(), "demo").fieldTypes();
        Type declared = new DeclaredTypeReading(
                new DeclarationFacts(
                        new FieldRead(symbols, checked, FieldRead.Unreadable.REFUSED)),
                definitions(), COMPILATION.db().ask(new Bodies.Reachable("demo")).value())
                .declaredTypeOf(bodyOf("taken"));
        assertEquals(Type.STRING, declared,
                "the walk over the declarations says `held.deal.id` is a `String`");

        Map<String, Type> offered = SemanticSnapshot.of(COMPILATION.db(), "demo").orElseThrow()
                .fieldsOf(new TypeFact(Type.ref(named("Deal")), new Evidence.Declared()));
        assertEquals(Type.STRING, offered.get("id"),
                "and an author writing `.` after a `Deal` is offered `id` holding the same");

        assertEquals(Map.of("id", Type.STRING), offered,
                "and nothing else, since one name is all the cases share");
    }

    /**
     * And the elaboration types the same read the same way, said by a model that only compiles if it
     * does.
     *
     * <p>With the refusal beside it. A model accepted says the read was typed as something the
     * output admits; the second says it was not typed as anything else, which acceptance alone does
     * not.
     */
    @Test
    void andTheElaborationTypesTheSameReadTheSameWay() {
        Compiler.compile(behaviorAnswering("String"));
        CompileException refused = assertThrows(CompileException.class,
                () -> Compiler.compile(behaviorAnswering("Int")));
        assertTrue(refused.getMessage().contains("Int"), refused.getMessage());
    }

    /**
     * A declaration that does not read is refused where a check reads it and makes nothing readable
     * where a text is being typed.
     *
     * <p>The nominal half of the reading has the two worlds the field half has, and this is where
     * they part. What a value here is cannot be settled without following the spreads under it, so
     * a declaration that does not read is found by reading a position and not only by checking the
     * declaration — and an author who is already being told what is wrong with it is not also told
     * that the buffer cannot say what may follow a {@code .}.
     *
     * <p>Both worlds on each source, so that a reading which simply never refused would fail here
     * as surely as one that always did.
     */
    @Test
    void aDeclarationThatDoesNotReadIsRefusedForACheckAndAnsweredForAText() {
        for (String[] each : new String[][] {
                {"A", """
                        module demo
                        data A = { x: Int, x: String }
                        """},
                {"Bad", """
                        module demo
                        data Open   = { id: String }
                        data Closed = { id: String }
                        data Deal   = Open | Closed
                        data Bad    = { ...Deal }
                        """},
                {"Bad", """
                        module demo
                        data One = { id: String }
                        data Two = { id: String }
                        data Bad = { ...One, ...Two }
                        """}}) {
            // Resolved and not checked, which is the state these readings are made in: the check
            // refuses the module, and an editor is looking at it while it does.
            Compilation c = Compilation.ofSource(each[1], "Main");
            Symbols scope = Scopes.derived(c.db(), "demo").value();
            Type position = Type.ref(TypeSymbols.declared(new TypeKey("demo", each[0])));
            FieldTypes text = new ResolvedFieldTypes(scope);

            assertEquals(Map.of(),
                    new FieldRead(scope, text, FieldRead.Unreadable.MAKES_NOTHING_READABLE)
                            .at(position),
                    () -> "a text still being typed is answered at " + Type.show(position));
            assertThrows(CompileException.class,
                    () -> new FieldRead(scope, text, FieldRead.Unreadable.REFUSED).at(position),
                    () -> "and a check reads the same position and refuses it, at "
                            + Type.show(position));
        }
    }

    /**
     * And the editor's own reading is the one that answers.
     *
     * <p>Held of the snapshot rather than of a {@code FieldRead} built here, because which of the
     * two worlds it is in is what the snapshot decides — built with the other, it would compile and
     * throw a declaration diagnostic out of the one reader whose whole contract is to answer from
     * what the declarations denote now.
     */
    @Test
    void andTheSnapshotAnAuthorAsksIsTheOneThatAnswers() {
        Compilation c = Compilation.ofSource("""
                module demo
                data A = { x: Int, x: String }
                """, "Main");
        Type a = Type.ref(TypeSymbols.declared(new TypeKey("demo", "A")));
        assertEquals(Map.of(),
                SemanticSnapshot.of(c.db(), "demo").orElseThrow()
                        .fieldsOf(new TypeFact(a, new Evidence.Declared())),
                "an author writing `.` after a declaration that does not read is told nothing,"
                        + " rather than the buffer refusing to answer");
    }

    /** A behavior reading the shared name off the sum and answering with {@code answers}. */
    private static String behaviorAnswering(String answers) {
        return """
                module demo

                data Common = { id: String }
                data Open   = { ...Common }
                data Closed = { ...Common, closedOn: Date }
                data Deal   = Open | Closed

                behavior idOf : (deal: Deal) -> %s

                let idOf (deal) = deal.id
                """.formatted(answers);
    }

    private static Compilation compiled() {
        Compilation c = Compilation.ofSource(MODEL, "Main");
        c.answerEverything();
        return c;
    }

    private static TypeSymbol named(String declaration) {
        return TypeSymbols.declared(new TypeKey("demo", declaration));
    }

    private static Map<String, Hir.FnDef> definitions() {
        return COMPILATION.db().ask(new Bodies.ModuleDefinitions("demo")).value();
    }

    private static Hir.Expr bodyOf(String name) {
        return assertInstanceOf(Hir.FnBody.Written.class, definitions().get(name).body()).expr();
    }
}
