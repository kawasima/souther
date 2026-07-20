package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An or-pattern {@code | A | B -> body} runs one body for several cases (spec 16.3). Its cases
 * count toward exhaustiveness; covering a case twice is an overlap error; and with {@code as x} the
 * binding is the scrutinee's sum type, since no single case type fits every alternative.
 */
class CompileOrPatternTest {

    private static final String ROUTING = """
            module demo

            data A = { v: Int }
            data B = { v: Int }
            data C = { v: Int }
            data Three = A | B | C

            data Lo = Int
            data Hi = Int

            behavior classify : (x: Three) -> Lo | Hi constructs Lo, Hi
            let classify (x) =
                match x with
                    | A | B -> Lo { value = 1 }
                    | C -> Hi { value = 2 }
            """;

    @Test
    void anOrPatternRunsOneBodyForEveryAlternative() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(ROUTING), CompileOrPatternTest.class.getClassLoader());
        Object classify = loader.loadClass("demo.Classify").getConstructor().newInstance();

        // A and B take the same (Lo) case; C takes the other (Hi) — the or-pattern fires for both A and B
        assertEquals("demo.Lo", apply(loader, classify, "A").getClass().getName());
        assertEquals("demo.Lo", apply(loader, classify, "B").getClass().getName());
        assertEquals("demo.Hi", apply(loader, classify, "C").getClass().getName());
    }

    private static Object apply(BytesClassLoader loader, Object behavior, String tag) throws Exception {
        Object value = Codecs.decoded(loader, "demo.Three", Map.of("type", tag, "v", 0L));
        return Codecs.apply(behavior, value);
    }

    @Test
    void anOrPatternCountsTowardExhaustiveness() {
        // case A | B plus case C covers all three cases
        assertDoesNotThrow(() -> Compiler.compile(ROUTING));
    }

    @Test
    void aMissingCaseIsStillNonExhaustive() {
        String module = """
                module demo
                data A = { v: Int }
                data B = { v: Int }
                data C = { v: Int }
                data Three = A | B | C
                data Out = Int
                behavior f : (x: Three) -> Out constructs Out
                let f (x) = match x with | A | B -> Out { value = 1 }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(module));
        assertEquals("E1201", e.code());
    }

    @Test
    void coveringAnCaseTwiceIsAnOverlapError() {
        String module = """
                module demo
                data A = { v: Int }
                data B = { v: Int }
                data Two = A | B
                data Out = Int
                behavior f : (x: Two) -> Out constructs Out
                let f (x) = match x with | A | B -> Out { value = 1 } | A -> Out { value = 2 }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(module));
        assertTrue(e.getMessage().contains("more than one"), e.getMessage());
    }

    @Test
    void anOrPatternBindingHasTheScrutineeSumType() {
        // `ab` binds the sum type Three, so it can be passed where Three is expected (a helper that
        // takes Three) — but not have a case-specific field read off it.
        String module = """
                module demo
                data A = { v: Int }
                data B = { v: Int }
                data C = { v: Int }
                data Three = A | B | C
                data Out = Int

                let describe (x: Three) = 0

                behavior tag : (x: Three) -> Out constructs Out
                let tag (x) =
                    match x with
                        | A | B as ab -> Out { value = describe(ab) }
                        | C -> Out { value = 9 }
                """;
        assertDoesNotThrow(() -> Compiler.compile(module));
    }
}
