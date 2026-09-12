package souther.compiler.inputs;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Ast;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.ast.Hir;
import souther.compiler.check.Resolve;
import souther.compiler.check.Shape;
import souther.compiler.check.SyntaxSymbols;
import souther.compiler.check.Symbols;
import souther.compiler.diag.CompileException;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a position is one a partition can be derived from is carried by the input rather than
 * rechecked, so nothing downstream has to ask and nothing downstream can answer wrongly.
 *
 * <p>The set is the partition's own. It is not the set a signature admits, nor the set a field
 * admits — those two disagree with each other — so a shape is written out here and a boundary that
 * starts admitting a new one stops this compiling.
 *
 * <p>That the walk goes through it is the signature of {@link LocalInspection#inspect} and not
 * anything here: the phase takes the proof, so a position can be read only after being admitted.
 * These rows are what admitting one comes to. Asked for the proof after the local phase had
 * answered, the one shape that produces classes without being admissible — a union nobody named —
 * would have gone round it, which is the disagreement the whole type is for.
 */
class APositionAReadingIsMadeOfIsProvedToBeOneTest {

    private static final String MODULE = """
            module demo

            data Ok
            data Prospecting
            data Won
            data Stage = Prospecting | Won
            data StageN = Stage
            data Cyclic = Cyclic
            data Slot = { hour: Int, room: String }
            """;

    private final Symbols symbols = Symbols.of(resolved(), DefaultStdlib.get());

    private static Hir.Module resolved() {
        Ast.Module parsed = CstFrontend.parse(MODULE);
        return Resolve.module(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get()));
    }

    private Shape.ReadablePositionShape admit(Type type) {
        return ReadablePosition.of(type, symbols, ScopedDeclarations.of(symbols)).shape();
    }

    private Type named(String name) {
        return Type.ref(TypeSymbols.declared(new TypeKey(symbols.module(), name)));
    }

    // --- what a partition may be derived from ---------------------------------------------------

    @Test
    void everyShapeAValueCanStandAtIsAdmitted() {
        assertInstanceOf(Shape.Scalar.class, admit(Type.INT));
        assertInstanceOf(Shape.Unit.class, admit(named("Ok")));
        assertInstanceOf(Shape.Sum.class, admit(named("Stage")));
        assertInstanceOf(Shape.Product.class, admit(named("Slot")));
        assertInstanceOf(Shape.Sequence.class, admit(Type.list(Type.INT)));
        assertInstanceOf(Shape.Mapping.class, admit(Type.map(Type.STRING, Type.INT)));
        assertInstanceOf(Shape.Optional.class, admit(Type.option(Type.INT)));
    }

    /** A name is off before the shape is read, so the position a partition is derived from is the
     *  same one whether or not the model wrote a name round it (issue #631). */
    @Test
    void aNameRoundAPositionDoesNotChangeWhatItIsDerivedFrom() {
        assertEquals(admit(named("Stage")), admit(named("StageN")));
    }

    /**
     * A declaration reachable from itself compiles, so a position typed by one arrives here. It is
     * admitted — what a reader does with it is say it could not be interpreted, and a compiling
     * model must not take a path that throws.
     */
    @Test
    void aTypeThisCouldNotInterpretIsAdmittedRatherThanRefused() {
        assertInstanceOf(Shape.Unresolved.class, admit(named("Cyclic")));
    }

    // --- and what cannot stand at one ------------------------------------------------------------

    /**
     * The seven a position cannot have. Each is refused where a signature or a field is read, so one
     * arriving here is this compiler disagreeing with itself — which is said as that, and never as a
     * model that divides nothing.
     */
    @Test
    void aShapeNoPositionCanHaveIsThisCompilerDisagreeingWithItself() {
        List<Type> unreachable = List.of(
                Type.union(java.util.Set.of(TypeSymbols.declared(new TypeKey(symbols.module(), "Prospecting")), TypeSymbols.declared(new TypeKey(symbols.module(), "Won")))),
                Type.tuple(List.of(Type.INT, Type.STRING)),
                Type.fn(List.of(Type.INT), Type.INT),
                Type.NEVER,
                Type.NOTHING,
                Type.ERRONEOUS,
                Type.var("'a"));
        for (Type type : unreachable) {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> admit(type), Type.show(type) + " cannot stand at a position");
            assertTrue(thrown.getMessage().contains("disagree"),
                    "says the two readings disagree rather than reporting on the model: "
                            + thrown.getMessage());
        }
    }

    // --- and the boundary really does keep them out ----------------------------------------------

    /**
     * The set above is written out independently of the boundary's, which is what keeps a change
     * there from silently changing what is measured here. Independence costs a check that the two
     * still meet: a shape this calls unreachable has to be one the boundary refuses, or a model
     * would reach the throw.
     *
     * <p>Two of the seven, at the two ways in — a parameter and a field.
     */
    @Test
    void theBoundaryRefusesWhatThisCallsUnreachable() {
        assertThrows(CompileException.class, () -> souther.compiler.Compiler.compile("""
                module demo
                data Ok
                behavior run : (x: (Int, String)) -> Ok
                let run (x) = Ok
                """), "a tuple parameter never reaches a partition");

        assertThrows(CompileException.class, () -> souther.compiler.Compiler.compile("""
                module demo
                data Ok
                data Holder = { f: (Int) -> Int }
                behavior run : (h: Holder) -> Ok
                let run (h) = Ok
                """), "a function field never reaches a partition");
    }
}
