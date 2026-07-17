package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An or-pattern {@code case A | B => body} runs one body for several arms (spec 16.3). Its arms
 * count toward exhaustiveness; covering an arm twice is an overlap error; and with {@code as x} the
 * binding is the scrutinee's sum type, since no single arm type fits every alternative.
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

            behavior classify = (x: Three) -> Lo | Hi constructs Lo, Hi
            fn classify (x) =
                match x {
                    case A | B => Lo { value: 1 }
                    case C => Hi { value: 2 }
                }
            """;

    @Test
    @SuppressWarnings("unchecked")
    void anOrPatternRunsOneBodyForEveryAlternative() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(ROUTING), CompileOrPatternTest.class.getClassLoader());
        Decoder three = (Decoder) loader.loadClass("demo.Three").getMethod("decoder").invoke(null);
        Object classify = loader.loadClass("demo.Classify").getConstructor().newInstance();

        // A and B take the same (Lo) arm; C takes the other (Hi) — the or-pattern fires for both A and B
        assertEquals("demo.Lo", apply(classify, three, "A").getClass().getName());
        assertEquals("demo.Lo", apply(classify, three, "B").getClass().getName());
        assertEquals("demo.Hi", apply(classify, three, "C").getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static Object apply(Object behavior, Decoder threeDecoder, String tag) {
        Object value = ((Ok) threeDecoder.decode(Map.of("type", tag, "v", 0L), Path.ROOT)).value();
        return ((Behavior<Object, Object>) behavior).apply(value);
    }

    @Test
    void anOrPatternCountsTowardExhaustiveness() {
        // case A | B plus case C covers all three arms
        assertDoesNotThrow(() -> Compiler.compile(ROUTING));
    }

    @Test
    void aMissingArmIsStillNonExhaustive() {
        String module = """
                module demo
                data A = { v: Int }
                data B = { v: Int }
                data C = { v: Int }
                data Three = A | B | C
                data Out = Int
                behavior f = (x: Three) -> Out constructs Out
                fn f (x) = match x { case A | B => Out { value: 1 } }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(module));
        assertEquals("E1201", e.code());
    }

    @Test
    void coveringAnArmTwiceIsAnOverlapError() {
        String module = """
                module demo
                data A = { v: Int }
                data B = { v: Int }
                data Two = A | B
                data Out = Int
                behavior f = (x: Two) -> Out constructs Out
                fn f (x) = match x { case A | B => Out { value: 1 } case A => Out { value: 2 } }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(module));
        assertTrue(e.getMessage().contains("more than one"), e.getMessage());
    }

    @Test
    void anOrPatternBindingHasTheScrutineeSumType() {
        // `ab` binds the sum type Three, so it can be passed where Three is expected (a helper that
        // takes Three) — but not have an arm-specific field read off it.
        String module = """
                module demo
                data A = { v: Int }
                data B = { v: Int }
                data C = { v: Int }
                data Three = A | B | C
                data Out = Int

                fn describe (x: Three) = 0

                behavior tag = (x: Three) -> Out constructs Out
                fn tag (x) =
                    match x {
                        case A | B as ab => Out { value: describe(ab) }
                        case C => Out { value: 9 }
                    }
                """;
        assertDoesNotThrow(() -> Compiler.compile(module));
    }
}
