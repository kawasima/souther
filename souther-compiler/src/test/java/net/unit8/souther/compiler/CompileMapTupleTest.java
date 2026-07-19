package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Map.toList / fromList and the tuple-in-signature rule ([#stdlib-map], ADR-0036): a tuple type is
 *  allowed in a stdlib signature but rejected at a codec boundary (data field, behavior I/O). */
class CompileMapTupleTest {

    @Test
    void toListFromListRoundTrip() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Map ( fromList, toList, size )

                data In = { m: Map<String, Int> }
                data Out = { round: Map<String, Int>, built: Map<String, Int>, n: Int }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    let built = fromList([("a", 1), ("b", 2)])
                    Out {
                        round = fromList(toList(i.m)),
                        built = built,
                        n = size(built)
                    }
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("m", Map.of("x", 5L, "y", 7L)));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(Map.of("x", 5L, "y", 7L), m.get("round"), "toList then fromList round-trips");
        assertEquals(Map.of("a", 1L, "b", 2L), m.get("built"));
        assertEquals(2L, m.get("n"));
    }

    @Test
    void aTupleCannotBeADataField() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data Bad = { pair: (Int, String) }
                """));
        assertTrue(e.getMessage().contains("tuple"), e.getMessage());
    }

    @Test
    void aBehaviorCannotOutputATuple() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data A = { x: Int }
                behavior run : (a: A) -> (A, A) constructs A
                let run (a) = (a, a)
                """));
        assertTrue(e.getMessage().contains("tuple"), e.getMessage());
    }
}
