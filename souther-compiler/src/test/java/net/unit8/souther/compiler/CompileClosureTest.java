package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A lambda chosen at runtime cannot be expanded inline: {@code let f = if c then λ1 else λ2} binds
 * {@code f} to an {@code if}, not a block, so each branch becomes a first-class function value and
 * the application {@code f(x)} dispatches through {@link net.unit8.souther.runtime.Fn}. The lambda's
 * parameter type is inferred from the argument at the application site.
 */
class CompileClosureTest {

    private static final String MODULE = """
            module demo

            data Order = { v: Int, spring: Bool }
            data Result = { n: Int }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = {
                let f = if o.spring then (x) -> x + 100 else (x) -> x + 1
                Result { n = f(o.v) }
            }
            """;

    private long run(BytesClassLoader loader, Object check, long v, boolean spring) throws Exception {
        Object order = Codecs.decoded(loader, "demo.Order", Map.of("v", v, "spring", spring));
        Object r = Codecs.apply(check, order);
        return (Long) ((Map<?, ?>) Codecs.encode(loader, "demo.Result", r)).get("n");
    }

    @Test
    void aLambdaChosenAtRuntimeDispatchesThroughFn() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(110L, run(loader, check, 10L, true));
        assertEquals(11L, run(loader, check, 10L, false));
    }

    // both branches capture the enclosing `o`, so the Fn class holds it in a field
    private static final String CAPTURING = """
            module demo

            data Order = { v: Int, spring: Bool }
            data Result = { n: Int }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = {
                let f = if o.spring then (x) -> x + o.v else (x) -> x - o.v
                Result { n = f(100) }
            }
            """;

    @Test
    void aClosureCapturesAFreeVariable() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(CAPTURING), getClass().getClassLoader());
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(110L, run(loader, check, 10L, true));    // 100 + 10
        assertEquals(90L, run(loader, check, 10L, false));    // 100 - 10
    }

    // a helper returns a lambda capturing its parameter: adder(5) is a closure over n = 5. Inlining
    // adder leaves `let $n = 5 in (x) -> x + $n`, so the function value sits under a capture-let.
    private static final String RETURNING = """
            module demo

            data Order = { v: Int }
            data Result = { n: Int }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = {
                let add5 = adder(5)
                Result { n = add5(o.v) }
            }

            let adder (n: Int) = (x) -> x + n
            """;

    // a two-parameter closure chosen at runtime: apply passes both args through the Object[]
    private static final String MULTIARG = """
            module demo

            data Pair = { a: Int, b: Int, plus: Bool }
            data Out = { v: Int }

            behavior check : (o: Pair) -> Out
                constructs Out

            let check (o) = {
                let f = if o.plus then (x, y) -> x + y else (x, y) -> x - y
                Out { v = f(o.a, o.b) }
            }
            """;

    @Test
    void aMultiParameterClosure() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MULTIARG), getClass().getClassLoader());
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        Object plus = Codecs.decoded(loader, "demo.Pair", Map.of("a", 7L, "b", 3L, "plus", true));
        Object minus = Codecs.decoded(loader, "demo.Pair", Map.of("a", 7L, "b", 3L, "plus", false));
        assertEquals(10L, ((Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(check, plus))).get("v"));
        assertEquals(4L, ((Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(check, minus))).get("v"));
    }

    // a closure that constructs a data — the construction must count against the behavior's
    // `constructs` permission even though it happens inside an escaping lambda
    private static final String CONSTRUCTING = """
            module demo

            data In = { v: Int, up: Bool }
            data Out = { v: Int }

            behavior check : (o: In) -> Out
                constructs Out

            let check (o) = {
                let f = if o.up then (x) -> Out { v = x + 1 } else (x) -> Out { v = x - 1 }
                f(o.v)
            }
            """;

    @Test
    void aClosureThatConstructsAData() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(CONSTRUCTING), getClass().getClassLoader());
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        Object up = Codecs.decoded(loader, "demo.In", Map.of("v", 10L, "up", true));
        Object down = Codecs.decoded(loader, "demo.In", Map.of("v", 10L, "up", false));
        assertEquals(11L, ((Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(check, up))).get("v"));
        assertEquals(9L, ((Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(check, down))).get("v"));
    }

    /** A closure that constructs a data must still obey the behavior's `constructs` permission: here
     * the else branch builds {@code Extra}, which is not declared, so it is E1002 — proving the
     * permission check counts constructions inside an escaping lambda, not only inline ones. */
    @Test
    void aClosureCannotConstructAnUndeclaredData() {
        String src = """
                module demo

                data In = { v: Int, up: Bool }
                data Out = { v: Int }
                data Extra = { w: Int }

                behavior check : (o: In) -> Out
                    constructs Out

                let check (o) = {
                    let f = if o.up then (x) -> Out { v = x + 1 } else (x) -> { let e = Extra { w = x }  Out { v = e.w } }
                    f(o.v)
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code(), e.getMessage());
    }

    @Test
    void aHelperReturnsACapturingClosure() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(RETURNING), getClass().getClassLoader());
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        Object order = Codecs.decoded(loader, "demo.Order", Map.of("v", 10L));
        Object r = Codecs.apply(check, order);
        assertEquals(15L, ((Map<?, ?>) Codecs.encode(loader, "demo.Result", r)).get("n"));   // (x -> x + 5)(10)
    }
}
