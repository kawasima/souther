package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A named helper fn may be passed to a list combinator by name: {@code all(positive, xs)} is the
 * same as {@code all(x -> positive(x), xs)} (spec 12.5, lines 918-921). The bare name is desugared
 * to a block wrapping the call, which is then expanded inline like any other helper call.
 */
class CompileHelperByNameTest {

    private static final String MODULE = """
            module demo
            import List ( all, length )

            data Order = { qtys: List<Int> }
            data Result = { ok: Bool, n: Int }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = Result { ok = all(positive, o.qtys), n = length(o.qtys) }

            let positive (x: Int) = x > 0
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object decodeOrder(BytesClassLoader loader, List<Long> qtys) throws Exception {
        return Codecs.decoded(loader, "demo.Order", Map.of("qtys", qtys));
    }

    private Map<?, ?> run(BytesClassLoader loader, Object check, List<Long> qtys) throws Exception {
        Object r = Codecs.apply(check, decodeOrder(loader, qtys));
        return (Map<?, ?>) Codecs.encode(loader, "demo.Result", r);
    }

    @Test
    void allWithAHelperPassedByName() throws Exception {
        BytesClassLoader loader = loader();
        Object check = loader.loadClass("demo.Check" + "$Impl").getDeclaredConstructor().newInstance();

        assertEquals(Boolean.TRUE, run(loader, check, List.of(1L, 2L, 3L)).get("ok"));
        assertEquals(Boolean.FALSE, run(loader, check, List.of(1L, -2L, 3L)).get("ok"));
        assertEquals(3L, run(loader, check, List.of(1L, 2L, 3L)).get("n"));
    }

    @Test
    void foldWithAHelperStepPassedByName() throws Exception {
        // fold's block is its first argument (spec §pipe); a named 2-arg helper stands in for it and
        // desugars to `($b0, $b1) -> add($b0, $b1)`, which the inliner then expands.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo
                import List ( fold )

                data In = { ns: List<Int> }
                data Out = { total: Int }

                behavior run : (i: In) -> Out constructs Out

                let add (acc: Int, x: Int) = acc + x
                let run (i) = Out { total = fold(add, 0, i.ns) }
                """), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In", Map.of("ns", List.of(1L, 2L, 3L)));
        Object out = Codecs.apply(loader.loadClass("demo.Run" + "$Impl").getDeclaredConstructor().newInstance(), in);
        assertEquals(6L, ((Map<?, ?>) Codecs.encode(loader, "demo.Out", out)).get("total"));
    }
}
