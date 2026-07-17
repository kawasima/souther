package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A named helper fn may be passed to a list combinator by name: {@code all(xs, positive)} is the
 * same as {@code all(xs, x -> positive(x))} (spec 12.5, lines 918-921). The bare name is desugared
 * to a block wrapping the call, which is then expanded inline like any other helper call.
 */
class CompileHelperByNameTest {

    private static final String MODULE = """
            module demo

            data Order = { qtys: List<Int> }
            data Result = { ok: Bool  n: Int }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = Result { ok: all(o.qtys, positive), n: length(o.qtys) }

            let positive (x: Int) = x > 0
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object decodeOrder(BytesClassLoader loader, List<Long> qtys) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo.Order").getMethod("decoder").invoke(null);
        return ((Ok) d.decode(Map.of("qtys", qtys), Path.ROOT)).value();
    }

    private Map<?, ?> run(BytesClassLoader loader, Object check, List<Long> qtys) throws Exception {
        Object r = ((Behavior) check).apply(decodeOrder(loader, qtys));
        Encoder enc = (Encoder) loader.loadClass("demo.Result").getMethod("encoder").invoke(null);
        return (Map<?, ?>) enc.encode(r);
    }

    @Test
    void allWithAHelperPassedByName() throws Exception {
        BytesClassLoader loader = loader();
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(Boolean.TRUE, run(loader, check, List.of(1L, 2L, 3L)).get("ok"));
        assertEquals(Boolean.FALSE, run(loader, check, List.of(1L, -2L, 3L)).get("ok"));
        assertEquals(3L, run(loader, check, List.of(1L, 2L, 3L)).get("n"));
    }
}
