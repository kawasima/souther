package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A binding may not take the name of a built-in value ({@code None}, a rounding mode). Those names
 * resolve to the built-in, so shadowing one silently makes it unreachable in that scope — always a
 * mistake, so it is rejected rather than allowed to shadow.
 */
class CompileShadowingTest {

    @Test
    void aLetCannotShadowNone() {
        String src = """
                module demo
                data In = { v: Int }
                data Out = { v: Int }
                behavior check : (o: In) -> Out constructs Out
                let check (o) = {
                    let None = 99
                    Out { v = None }
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("None"), e.getMessage());
    }

    @Test
    void aHelperParamCannotShadowARoundingMode() {
        String src = """
                module demo
                data Out = { v: Int }
                behavior check : (o: Out) -> Out constructs Out
                let check (o) = Out { v = twice(o.v) }
                let twice (HALF_UP: Int) = HALF_UP + HALF_UP
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("HALF_UP"), e.getMessage());
    }
}
