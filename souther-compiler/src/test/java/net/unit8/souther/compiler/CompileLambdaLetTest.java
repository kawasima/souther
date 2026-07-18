package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lambda is a value: it may be bound to a local with {@code let} and applied. When it does not
 * escape (it is only applied, never returned or stored), each application expands the lambda's body
 * inline, so no runtime closure is built. This is the first step beyond the original spec, which
 * only let a block be passed as an argument.
 */
class CompileLambdaLetTest {

    private static final String MODULE = """
            module demo

            data Order = { v: Int }
            data Result = { n: Int }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = {
                let inc = (x) -> x + 1
                Result { n = inc(o.v) }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private long run(BytesClassLoader loader, Object check, long v) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo.Order").getMethod("decoder").invoke(null);
        Object order = ((Ok) d.decode(Map.of("v", v), Path.ROOT)).value();
        Object r = ((Behavior) check).apply(order);
        Encoder enc = (Encoder) loader.loadClass("demo.Result").getMethod("encoder").invoke(null);
        return (Long) ((Map<?, ?>) enc.encode(r)).get("n");
    }

    @Test
    void lambdaBoundWithLetAndApplied() throws Exception {
        BytesClassLoader loader = loader();
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(6L, run(loader, check, 5L));
        assertEquals(1L, run(loader, check, 0L));
    }

    /** A lambda that escapes — used as a value rather than only applied — still needs a runtime
     * closure, which is not built yet, so it is rejected (the boundary of this step). */
    @Test
    void aLambdaThatEscapesIsRejected() {
        String module = """
                module demo

                data Order = { v: Int }
                data Result = { n: Int }

                behavior check : (o: Order) -> Result
                    constructs Result

                let check (o) = {
                    let inc = (x) -> x + 1
                    inc
                }
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(module));
        assertTrue(ex.getMessage().contains("not a value"),
                "expected a block-is-not-a-value rejection, got: " + ex.getMessage());
    }
}
