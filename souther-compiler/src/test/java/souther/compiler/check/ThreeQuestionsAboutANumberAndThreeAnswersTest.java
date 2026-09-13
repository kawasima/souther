package souther.compiler.check;

import souther.compiler.query.Scopes;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a number is, asked three ways, of the same six types.
 *
 * <p>They read alike and they are not one question. The first is what gives a <em>newtype</em> closed
 * arithmetic, which the language grants only for one directly over a number and means to (spec
 * §newtype-arithmetic) — it is not "arithmetic works here", which is also true of a bare `Int`, and
 * the column is named for what it measures so the table does not repeat the confusion it is about.
 * Being an ordered number reaches the recursive base, which is what a comparison reads (ADR-0047).
 * Whether the affine domain can carry a value is the analyser's own capability, and answering it with
 * the first is how a comparison the language accepts stopped reaching the reasoning (#461).
 *
 * <p>Held as a table because the three coincide on most types and part on exactly the ones that
 * matter. A change to any of them shows here as a cell, and which cell says whether the language's
 * capability moved or the analyser's did.
 */
class ThreeQuestionsAboutANumberAndThreeAnswersTest {

    private static final String TYPES = """
            module example.numbers

            data Minute = Int
            data StartMinute = Minute

            data Ratio = Decimal
            data Share = Ratio

            data Label = String
            data Tag = Label
            """;

    private record Answers(Type directNewtypeArithmetic, Type ordered, Type affine) {}

    private static Answers of(String type) {
        Compilation compilation = Compilation.ofSource(TYPES, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols, "the model did not compile");
        Type t = switch (type) {
            case "Int" -> Type.INT;
            case "Decimal" -> Type.DECIMAL;
            default -> Type.ref(TypeSymbols.declared(new TypeKey(module, type)));
        };
        Type base = TypeOps.base(t, ScopedDeclarations.wrapsOf(symbols));
        return new Answers(
                TypeOps.directNumericNewtypeBase(t, symbols),
                base == Type.INT || base == Type.DECIMAL ? base : null,
                RuleReadings.termsOfNoClauseFiled(symbols,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES).affineScalarBase(t));
    }

    /** A primitive is no newtype, so the first column has nothing to say about it; it is a number to
     * the other two. */
    @Test
    void aPrimitiveIsANumberWithoutBeingANewtypeOverOne() {
        assertEquals(new Answers(null, Type.INT, Type.INT), of("Int"));
        assertEquals(new Answers(null, Type.DECIMAL, Type.DECIMAL), of("Decimal"));
    }

    /** One layer over a number: all three agree. */
    @Test
    void aNewtypeDirectlyOverANumberIsANumberEveryWay() {
        assertEquals(new Answers(Type.INT, Type.INT, Type.INT), of("Minute"));
        assertEquals(new Answers(Type.DECIMAL, Type.DECIMAL, Type.DECIMAL), of("Ratio"));
    }

    /**
     * Two layers: no newtype arithmetic, and a number to the other two.
     *
     * <p>This is the row the three questions part on, and the row #461 was. The language compares
     * such a value because comparison reaches the base; the analyser carries it for the same reason,
     * and used to refuse it by answering with the first column.
     */
    @Test
    void aNewtypeOverANewtypeIsNoArithmeticAndStillANumber() {
        assertEquals(new Answers(null, Type.INT, Type.INT), of("StartMinute"));
        assertEquals(new Answers(null, Type.DECIMAL, Type.DECIMAL), of("Share"));
    }

    /** And a chain over something that is no number is no number by any of the three. */
    @Test
    void aChainOverSomethingElseIsNotANumberAtAll() {
        assertEquals(new Answers(null, null, null), of("Label"));
        assertEquals(new Answers(null, null, null), of("Tag"));
    }
}
