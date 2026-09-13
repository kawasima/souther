package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.observe.FieldTypes;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ExampleExecutions;
import souther.compiler.query.Scopes;
import souther.compiler.query.Shapes;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crossing a {@code .} asks the world the reading is being made in, and answers with what it says.
 *
 * <p>{@link DeclaredTypeReading} owns how evidence flows through an expression — a {@code let} it
 * enters, a name it follows, a definition it steps into. What a field of a declaration holds is not
 * that walk's to work out: an accepted program has one answer to it and it is the check's, and a
 * walk deriving a second from the declarations would be the reading a comparison is made against
 * and the reading a projection is typed by coming apart.
 *
 * <p>Held to by a world that answers something the declarations do not. The walk has the
 * declarations in hand — it resolves names against them — so an answer that follows the world it
 * was handed rather than what it could read there is the whole of the property.
 */
class AFieldAccessIsTypedByTheWorldsOwnAnswerTest {

    private static final String MODULE = """
            module demo

            data Line = { amount: Int }
            data Order = { line: Line }

            let sample = Order { line = Line { amount = 1 } }
            let taken = sample.line
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

    /** What the check settled, which is what an accepted program's readers are handed. */
    @Test
    void aFieldIsWhatTheCheckSettledItHolds() {
        Type taken = readingIn(checked()).declaredTypeOf(bodyOf("taken"));
        assertEquals("Line", assertInstanceOf(Type.Ref.class, taken).name().name(),
                "`sample.line` is declared to hold a `Line`");
    }

    /**
     * And a world saying something else is what is answered — the walk keeps no reading of its own
     * to fall back to.
     *
     * <p>A program where {@code Order.line} holds a {@code String} is one no declaration here
     * writes, which is the point: what says the walk is not deriving this is that it answers the
     * one thing it could not have read off the declarations it is resolving names against.
     */
    @Test
    void andNotWhatTheDeclarationsWouldSayBesideIt() {
        FieldTypes saysAString = _ -> Map.of("line", Type.STRING);
        Type taken = readingIn(saysAString).declaredTypeOf(bodyOf("taken"));
        assertEquals(Type.STRING, taken,
                "the walk read the declarations rather than the world it was handed");
    }

    /**
     * A product the check said nothing about is not a product with no such field.
     *
     * <p>Answered as an absence, a value of it would be compared as whatever its parts happen to
     * look like — and a row about it would hold here and fail wherever the shape was in hand. So
     * the accepted reading refuses, and nothing falls back to reading the declaration.
     */
    @Test
    void andAProductWithNoSettledShapeIsRefusedRatherThanAnsweredEmpty() {
        FieldTypes nothingSettled =
                FieldTypes.over(new CheckedDeclarations(
                        Shapes.publishedDeclarations(compilation.db()), _ -> null));
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> nothingSettled.of(orderOf()));
        assertTrue(refused.getMessage().contains("Order"),
                "the refusal names the declaration nothing was settled about: " + refused.getMessage());
    }

    /**
     * And a declaration this reading cannot reach is refused too, rather than read as a value with
     * no fields.
     *
     * <p>The two are different answers about different things: a sum has no fields to take, and a
     * data whose declaration is out of reach has fields nobody here can see. Read alike, the second
     * becomes a value compared as whatever its parts happen to look like.
     */
    @Test
    void andSoIsOneThisReadingCannotReach() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> checked().of(TypeSymbols.declared(new TypeKey("demo", "Missing"))));
        assertTrue(refused.getMessage().contains("Missing"), refused.getMessage());
    }

    /** The declaration {@code Order}, as the reading of this module names it. */
    private TypeSymbol orderOf() {
        Type sample = readingIn(checked()).declaredTypeOf(bodyOf("sample"));
        return assertInstanceOf(Type.Ref.class, sample).name();
    }

    /**
     * A reading that is not downstream of a check reads a declaration the check settled nothing
     * about, and answers rather than raising.
     *
     * <p>A clause that is not a condition leaves its declaration without a settled shape while the
     * declaration itself stands: a measurement of that module — what a generation reads to find the
     * values a row could be written against — still crosses a field of it. What it is owed there is
     * what the declarations denote, because it is measuring a text and not running an accepted
     * program, and a measure that raised where it could not measure would report a compiler fault
     * for a mistake the author is being told about anyway.
     */
    @Test
    void aReadingThatIsNotDownstreamOfACheckAnswersWhereTheCheckSettledNothing() {
        Compilation c = Compilation.ofSource("""
                module demo

                data Bad =
                    { n: Int }
                    invariant broken = n
                """, "Main");
        c.answerEverything();
        Symbols scope = Scopes.derived(c.db(), "demo").value();
        TypeSymbol bad = TypeSymbols.declared(new TypeKey("demo", "Bad"));
        assertEquals(Map.of(), c.db().ask(new Shapes.ValueShapes("demo")).value(),
                "the clause did not elaborate, so the check settled nothing about `Bad`");

        assertEquals(Type.INT,
                new FieldRead(scope, ScopedDeclarations.of(scope),
                        ScopedDeclarations.kindsOf(scope), new ResolvedFieldTypes(scope, ScopedDeclarations.wrapsOf(scope)),
                        FieldRead.Unreadable.REFUSED)
                        .of(Type.ref(bad), "n"),
                "and what the declaration denotes is still there to be read");
    }

    /** The reading of a {@code .} this walk is handed, in {@code world}. */
    private FieldRead reading(FieldTypes world) {
        return new FieldRead(symbols, ScopedDeclarations.of(symbols),
                ScopedDeclarations.kindsOf(symbols), world,
                FieldRead.Unreadable.REFUSED);
    }

    /** What the declarations state about an expression, read in {@code world}. */
    private DeclaredTypeReading readingIn(FieldTypes world) {
        return new DeclaredTypeReading(new DeclarationFacts(reading(world)), values,
                compilation.db().ask(new Bodies.Reachable(module)).value());
    }

    private FieldTypes checked() {
        return ExampleExecutions.of(compilation.db(), module).fieldTypes();
    }

    private Hir.Expr bodyOf(String name) {
        return assertInstanceOf(Hir.FnBody.Written.class, values.get(name).body()).expr();
    }
}
