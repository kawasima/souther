package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blocks and the list combinators that take them (spec 12.5, 18.4).
 *
 * <p>A block's requirements float out to the behavior that passes it, so nothing about them is
 * written down (spec 29), and the backend inlines the block instead of building a closure. A block
 * may also be bound to a {@code let} and applied — see {@link CompileLambdaLetTest}; only a block
 * that escapes (is used as a value, not just applied) is rejected, for want of a runtime closure.
 */
class CompileBlockTest {

    private static final String MODULE = """
            module demo
            import List { map, filter, fold, all, any }

            behavior 倍にする : (xs: List<Int>) -> List<Int>
            behavior 正だけ : (xs: List<Int>) -> List<Int>
            behavior 合計 : (xs: List<Int>) -> Int
            behavior 全部正か : (xs: List<Int>) -> Bool
            behavior どれか正か : (xs: List<Int>) -> Bool

            let 倍にする   (xs) = map(xs, x -> x * 2)
            let 正だけ     (xs) = filter(xs, x -> x > 0)
            let 合計       (xs) = fold(xs, 0, (acc, x) -> acc + x)
            let 全部正か   (xs) = all(xs, x -> x > 0)
            let どれか正か (xs) = any(xs, x -> x > 0)
            """;

    @SuppressWarnings("unchecked")
    private Object run(String name, Object arg) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object b = loader.loadClass("demo." + name).getConstructor().newInstance();
        return ((Behavior<Object, Object>) b).apply(arg);
    }

    @Test
    void theListCombinatorsRunTheBlock() throws Exception {
        List<Long> xs = List.of(3L, -1L, 4L);
        assertEquals(List.of(6L, -2L, 8L), run("倍にする", xs));
        assertEquals(List.of(3L, 4L), run("正だけ", xs));
        assertEquals(6L, run("合計", xs));
        assertEquals(false, run("全部正か", xs));
        assertEquals(true, run("どれか正か", xs));
    }

    @Test
    void anEmptyListIsHandled() throws Exception {
        assertEquals(List.of(), run("倍にする", List.of()));
        assertEquals(0L, run("合計", List.of()));
        assertEquals(true, run("全部正か", List.of()), "vacuously true");
        assertEquals(false, run("どれか正か", List.of()));
    }

    /**
     * The spec's own 12.5 example: the shape the DSL asks for when a data holds
     * {@code List<未検証注文明細>} and the next state holds {@code List<検証済み注文明細>}.
     */
    @Test
    void aBlockMayConstructUnderTheBehaviorsPermission() {
        String src = """
                module demo
                import List { map }
                data 未検証明細 = { コード: String }
                data 検証済み明細 = { コード: String }
                behavior 明細を検証する : (xs: List<未検証明細>) -> List<検証済み明細>
                    constructs 検証済み明細

                let 明細を検証する (xs) = map(xs, x -> 検証済み明細 { コード = x.コード })
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.明細を検証する"));
    }

    @Test
    void constructingInABlockStillNeedsTheDeclaration() {
        // the block builds `検証済み明細` inside `map`: `補助` is declared but `検証済み明細` is also
        // built, so the undeclared `検証済み明細` is E1002 (a declared clause must be complete).
        String src = """
                module demo
                import List { map, length }
                data 未検証明細 = { コード: String }
                data 検証済み明細 = { コード: String }
                data 補助
                data 明細 = 検証済み明細 | 補助
                behavior 明細を検証する : (xs: List<未検証明細>) -> List<明細> constructs 補助
                let 明細を検証する (xs) = map(xs, x -> 検証済み明細 { コード = x.コード }) ++ [補助 | length(xs) > 0]
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }

    /** The point of second-class blocks: this still infers, and nothing is written. */
    @Test
    void aBlocksRequirementsFloatOutToTheEnclosingBehavior() throws Exception {
        String src = """
                module demo
                import List { map }
                data Id = { v: String }
                data Nm = { v: String }
                behavior 名前を引く : (id: Id) -> Nm
                behavior 全部引く : (xs: List<Id>) -> List<Nm> requires 名前を引く
                let 全部引く (xs, 名前を引く) = map(xs, x -> 名前を引く(x))
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Class<?> c = loader.loadClass("demo.全部引く");
        // the requirement became the behavior's own, so it is injected like any other
        assertEquals("demo.名前を引く", c.getMethod("bind", loader.loadClass("demo.名前を引く"))
                .getParameterTypes()[0].getName());
    }

    @Test
    void aBlockTypeCannotBeAFieldType() {
        String src = """
                module demo
                data D = { f: (Int) -> Int }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    // filter's predicate must return Bool. Although filter is now a prelude helper derived from fold
    // (ADR-0028), the predicate is checked against filter's declared `keep: ('a) -> Bool` at the call
    // site, so the message names the combinator and its parameter — not the `if` the fold expands to —
    // and points at the user's `filter` call (line 3), not at a line of souther.list.
    @Test
    void theBlockMustReturnBoolForFilter() {
        String src = """
                module demo
                behavior f : (xs: List<Int>) -> List<Int>
                let f (xs) = List.filter(xs, x -> x * 2)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("filter"), e.getMessage());
        assertTrue(e.getMessage().contains("BOOL"), e.getMessage());
        assertTrue(e.getMessage().startsWith("3:"), "points at the user's call, not the prelude: " + e.getMessage());
    }

    // Passing a value where a combinator wants a function is rejected at the call site, naming the
    // parameter and the combinator — checked against `map`'s declared `f: ('a) -> 'b` before the
    // derivation is expanded, so the message speaks of `map`, not of the fold it becomes.
    @Test
    void mapWithoutAFunctionIsRejected() {
        String src = """
                module demo
                behavior f : (xs: List<Int>) -> List<Int>
                let f (xs) = List.map(xs, xs)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("expects a function"), e.getMessage());
        assertTrue(e.getMessage().contains("map"), e.getMessage());
        assertTrue(e.getMessage().startsWith("3:"), "points at the user's call: " + e.getMessage());
    }
}
