package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tuples are expression-level, first-class values (ADR-0036): {@code (a, b)} builds one and
 * {@code let (x, y) = t} destructures it. They never cross the data/behavior boundary, so no codec
 * is derived — they only carry several values through a computation.
 */
class CompileTupleTest {

    private long sum(String body, long x, long y) throws Exception {
        String src = """
                module demo

                data In = { x: Int, y: Int }
                data Out = { r: Int }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = %s
                """.formatted(body);
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In", Map.of("x", x, "y", y));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);
        return (Long) ((Map<?, ?>) Codecs.encode(loader, "demo.Out", out)).get("r");
    }

    @Test
    void buildAndDestructureATuple() throws Exception {
        String body = """
                {
                        let (a, b) = (i.x, i.y)
                        Out { r = a + b }
                    }
                """;
        assertEquals(7L, sum(body, 3, 4));
    }

    @Test
    void tupleElementsKeepTheirOrder() throws Exception {
        // b is x, a is y — destructuring binds by position, so r = x - y
        String body = """
                {
                        let (b, a) = (i.x, i.y)
                        Out { r = b - a }
                    }
                """;
        assertEquals(-1L, sum(body, 3, 4));   // 3 - 4
    }

    @Test
    void aDestructureMustMatchTheTupleArity() {
        // Elm rejects a tuple pattern whose arity differs from the tuple's, in either direction.
        String tooFew = """
                module demo
                data In = { x: Int, y: Int }
                data Out = { r: Int }
                behavior run : (i: In) -> Out constructs Out
                let run (i) = {
                    let (a, b) = (i.x, i.y, i.x)
                    Out { r = a + b }
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(tooFew));   // 3-tuple, 2 names

        String tooMany = """
                module demo
                data In = { x: Int, y: Int }
                data Out = { r: Int }
                behavior run : (i: In) -> Out constructs Out
                let run (i) = {
                    let (a, b, c) = (i.x, i.y)
                    Out { r = a + b + c }
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(tooMany));   // 2-tuple, 3 names
    }

    @Test
    void aThreeTupleWorks() throws Exception {
        String body = """
                {
                        let (a, b, c) = (i.x, i.y, i.x)
                        Out { r = a + b + c }
                    }
                """;
        assertEquals(10L, sum(body, 3, 4));   // 3 + 4 + 3
    }

    @Test
    void aTupleFromEitherBranchOfAnIf() throws Exception {
        // both branches yield (Int, Int), so the if's type is the tuple and it destructures
        String body = """
                {
                        let (a, b) = if i.x > 0 then (i.x, i.y) else (i.y, i.x)
                        Out { r = a - b }
                    }
                """;
        assertEquals(-1L, sum(body, 3, 4));   // x > 0 -> (3, 4) -> 3 - 4
    }

    @Test
    void aTupleCanBeHeldInALetThenDestructured() throws Exception {
        String body = """
                {
                        let p = (i.x, i.y)
                        let (a, b) = p
                        Out { r = a + b }
                    }
                """;
        assertEquals(7L, sum(body, 3, 4));
    }

    @Test
    void aHelperCanReturnATupleTheCallerDestructures() throws Exception {
        String src = """
                module demo

                import List ( sum, length )

                data In = { ns: List<Int> }
                data Out = { total: Int, count: Int }

                behavior run : (i: In) -> Out constructs Out

                let stats (xs: List<Int>) = (sum(xs), length(xs))
                let run (i) = {
                    let (t, c) = stats(i.ns)
                    Out { total = t, count = c }
                }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In", Map.of("ns", java.util.List.of(1L, 2L, 3L)));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);
        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(6L, m.get("total"));
        assertEquals(3L, m.get("count"));
    }
}
