package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A user helper may recurse over self-referential data (ADR-0028 overturned): a recursive helper is
 * lowered to a static method rather than inlined, so a self- or mutual call is an ordinary method
 * call. A recursive helper must declare its return type (the cycle can't be inferred through) and
 * stays pure (it cannot call an injected behavior).
 */
class CompileRecursiveHelperTest {

    // An org chart: an employee optionally reports to a boss, who is itself an employee. Walking the
    // reporting line to the top is recursion that fold cannot express (the depth is unbounded).
    private static final String ORG = """
            module demo

            data Employee = { boss: Employee?, name: String }
            data Depth = Int

            behavior measureDepth : (e: Employee) -> Depth constructs Depth

            let depth (e: Employee): Int =
                match e.boss with
                    | Some b -> depth(b) + 1
                    | None -> 1

            let measureDepth (e) = Depth(depth(e))
            """;

    private long measure(Map<String, Object> employee) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(ORG), getClass().getClassLoader());
        Object e = Codecs.decoded(loader, "demo.Employee", employee);

        Object behavior = loader.loadClass("demo.MeasureDepth").getConstructor().newInstance();
        Object depth = Codecs.apply(behavior, e);

        return (long) Codecs.encode(loader, "demo.Depth", depth);
    }

    @Test
    void selfRecursiveHelperComputesOrgChartDepth() throws Exception {
        // c (no boss) = 1, b reports to c = 2, a reports to b = 3.
        Map<String, Object> chart = Map.of(
                "name", "a", "boss", Map.of(
                        "name", "b", "boss", Map.of(
                                "name", "c")));
        assertEquals(3L, measure(chart));
    }

    @Test
    void aLeafEmployeeHasDepthOne() throws Exception {
        assertEquals(1L, measure(Map.of("name", "solo")));
    }

    @Test
    void recursiveHelperWithoutAReturnTypeIsRejected() {
        // depth calls itself, so its result type can't be inferred through the cycle; it must be declared.
        String src = ORG.replace("let depth (e: Employee): Int =", "let depth (e: Employee) =");
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("depth") && ex.getMessage().contains("return type"),
                ex.getMessage());
    }

    // Two helpers that call each other (a mutual cycle): both are lowered to methods, so neither
    // needs the other inlined first. ping(n) counts down through pong and back, adding 1 each hop.
    private static final String MUTUAL = """
            module demo

            data N = Int
            data Steps = Int

            behavior countHops : (n: N) -> Steps constructs Steps

            let ping (n: Int): Int = if n == 0 then 0 else pong(n - 1) + 1
            let pong (n: Int): Int = if n == 0 then 0 else ping(n - 1) + 1

            let countHops (n) = Steps(ping(n.value))
            """;

    @Test
    void mutuallyRecursiveHelpersEachReachTheOther() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MUTUAL), getClass().getClassLoader());
        Object n = Codecs.decoded(loader, "demo.N", 5L);

        Object behavior = loader.loadClass("demo.CountHops").getConstructor().newInstance();
        Object steps = Codecs.apply(behavior, n);

        assertEquals(5L, (long) Codecs.encode(loader, "demo.Steps", steps));
    }

    @Test
    void recursiveHelperCallingAnInjectedBehaviorIsRejected() {
        // A recursive helper is a pure static method with no injected fields, so it cannot reach the
        // clock — the effect belongs in the behavior that calls the helper.
        String src = """
                module demo

                data N = Int
                data Out = Int

                behavior now : () -> N
                behavior run : (n: N) -> Out requires now constructs Out

                let loop (n: Int): Int = {
                    let c = now()
                    c.value + loop(n)
                }

                let run (n) = Out(loop(n.value))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("loop") && ex.getMessage().contains("now")
                && ex.getMessage().contains("pure"), ex.getMessage());
    }

    @Test
    void aHelperWhoseDeclaredReturnTypeDisagreesWithItsBodyIsRejected() {
        // A declared return type is now allowed on any helper (required on recursive ones). It must
        // still match the body — a lying annotation is not silently ignored.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let g (n: Int): String = n + 1
                let f (x) = X(g(x.value))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("g") && ex.getMessage().contains("declares"), ex.getMessage());
    }

    @Test
    void aRecursiveHelperCannotTakeAFunctionParameter() {
        // A recursive helper is a static method; a function value cannot be passed to it, so a
        // function-typed parameter is rejected with a message about that, not "a block is not a value".
        String src = """
                module demo
                data N = Int
                data Out = Int
                behavior run : (n: N) -> Out constructs Out
                let count (n: Int, f: (Int) -> Int): Int = if n == 0 then 0 else f(n) + count(n - 1, f)
                let run (n) = Out(count(n.value, (x) -> x))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("count") && ex.getMessage().contains("function"),
                ex.getMessage());
    }

    @Test
    void aDataBuiltInsideARecursiveHelperSatisfiesTheBehaviorConstructsClause() throws Exception {
        // A recursive helper is not inlined, so the behavior's `constructs` inference must follow the
        // call into it — declaring `constructs Tag` for a Tag built only inside `label` must be
        // accepted, not falsely rejected as "never builds Tag", and the value is really constructed.
        String src = """
                module demo
                import String ( length )
                data E = { boss: E?, name: String }
                data Tag = String
                    invariant length(value) > 0
                behavior run : (e: E) -> Tag constructs Tag
                let label (e: E): Tag = match e.boss with | Some b -> label(b) | None -> Tag(e.name)
                let run (e) = label(e)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object e = Codecs.decoded(loader, "demo.E", Map.of("name", "root"));

        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object tag = Codecs.apply(behavior, e);

        assertEquals("root", Codecs.encode(loader, "demo.Tag", tag));
    }

    @Test
    void aDataBuiltOnlyInsideARecursiveHelperMustBeDeclared() {
        // The attribution runs both ways: a Tag built inside the recursive helper counts toward the
        // behavior's constructions, so omitting it from a non-empty `constructs` clause is E1002.
        String src = """
                module demo
                import String ( length )
                data E = { boss: E?, name: String }
                data Note = { text: String }
                data Tag = String
                    invariant length(value) > 0
                behavior run : (e: E) -> Note constructs Note
                let label (e: E): Tag = match e.boss with | Some b -> label(b) | None -> Tag(e.name)
                let run (e) = {
                    let t = label(e)
                    Note { text = t.value }
                }
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("Tag"), ex.getMessage());
    }

    @Test
    void aMandatorySelfReferenceIsRejectedAsUninhabitable() {
        // `boss: Employee` (no `?`, no List) is a base-less cycle: building an Employee would need an
        // Employee, forever. No value can exist, so it is a compile error, not a runtime overflow.
        String src = """
                module demo
                data Employee = { boss: Employee, name: String }
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("Employee"), ex.getMessage());
    }

    @Test
    void aMutualMandatoryCycleIsRejected() {
        String src = """
                module demo
                data A = { b: B }
                data B = { a: A }
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("A") || ex.getMessage().contains("B"), ex.getMessage());
    }

    @Test
    void aRecursiveHelperCannotBeCalledFromAnInvariant() {
        // An invariant runs on every construction and must terminate, so a recursive helper — which
        // has no termination guarantee — cannot appear in one. The message names the invariant, not
        // "arbitrary JVM methods".
        String src = """
                module demo
                let count (n: Int): Int = if n == 0 then 0 else count(n - 1) + 1
                data X = Int
                    invariant count(value) < 100
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("count") && ex.getMessage().contains("invariant"),
                ex.getMessage());
    }

    @Test
    void anOptionalSelfReferenceIsAllowed() {
        // the `?` gives a base case (the top of the chain has None), so the type is inhabitable.
        Compiler.compile("module demo\ndata Employee = { boss: Employee?, name: String }\n");
    }

    @Test
    void aListSelfReferenceIsAllowed() {
        // an empty list is a base case (a leaf has no children), so a tree is inhabitable.
        Compiler.compile("module demo\ndata Tree = { children: List<Tree>, label: String }\n");
    }
}
