package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much a method holds and how many constants a class refers to are not known until it is
 * written, so these are not counted at the declaration the way argument slots are. The class file
 * writer refuses, and what it says names its own rule and the method it happened to be writing. It
 * is reported as the definition that was being emitted, which is the one the author can do something
 * about.
 *
 * <p>The widths here are the shapes that reach each limit first on this compiler, not thresholds the
 * JVM sets: what matters is that a small one is emitted and a large one is reported, so neither side
 * is pinned to a number the emitter could reasonably change.
 */
class AGeneratedMemberTooLargeForTheJvmIsSaidAtItsDefinitionTest {

    @Test
    void aBodyLongerThanOneMethodHoldsIsSaidAtTheBehavior() {
        assertDoesNotThrow(() -> Compiler.compile(behaviorOverAListOf(100)));

        String src = behaviorOverAListOf(8000);
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E2102", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("f"), e.getMessage());
    }

    @Test
    void itSaysWhichGeneratedMethodAndHowFarPastTheLimit() {
        String src = behaviorOverAListOf(8000);
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));

        String said = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
        assertTrue(said.contains("apply"), "the method it could not write: " + said);
        assertTrue(said.contains("65535"), "the limit it went past: " + said);
    }

    @Test
    void aClassNeedingMoreConstantsThanThePoolHoldsIsSaidAtTheBehavior() {
        String src = behaviorOverAListOf(40000);
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E2103", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("f"), e.getMessage());
    }

    @Test
    void aRecursiveHelperTooLongForOneMethodIsSaidAtTheHelper() {
        String src = helperOverAListOf(8000);
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E2102", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("spin"),
                "the helper, not the $Fns class the helpers share: " + e.getMessage());
    }

    private static String listOf(int n) {
        return "[" + IntStream.range(0, n).mapToObj(String::valueOf)
                .collect(Collectors.joining(", ")) + "]";
    }

    private static String behaviorOverAListOf(int n) {
        return "module demo\n\ndata Out = { n: Int }\n\nbehavior f : (n: Int) -> Out\n"
                + "    constructs Out\nlet f (n) = Out { n = List.sum(" + listOf(n) + ") }\n";
    }

    private static String helperOverAListOf(int n) {
        return "module demo\n\ndata Out = { n: Int }\n\n"
                + "partial let spin (n: Int): Int =\n"
                + "    if n == 0 then List.sum(" + listOf(n) + ") else spin(n - 1)\n\n"
                + "behavior f : (n: Int) -> Out\n    constructs Out\n"
                + "let f (n) = Out { n = spin(n) }\n";
    }
}
