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
 * The empty-list literal {@code []} (ADR-0028, relaxing spec §list). It has no element type of its
 * own; the type is fixed by context — the other operand of {@code ++}, the sibling case of an
 * {@code if}, the accumulator a {@code fold} seed grows into, or the {@code List<T>} a field expects.
 * An empty list is polymorphic and element-agnostic at runtime, so this is always sound.
 */
class CompileEmptyListTest {

    private BytesClassLoader loader(String module) {
        return new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
    }

    private Object decode(BytesClassLoader loader, String type, Map<String, Object> fields) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Ok) d.decode(fields, Path.ROOT)).value();
    }

    private Map<?, ?> run(BytesClassLoader loader, Object in) throws Exception {
        Object r = ((Behavior) loader.loadClass("demo.Work").getDeclaredConstructor().newInstance()).apply(in);
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        return (Map<?, ?>) enc.encode(r);
    }

    /** {@code []} as a fold seed the block grows a list into (the shape a derived {@code map} takes). */
    @Test
    void emptyListAsFoldSeed() throws Exception {
        String module = """
                module demo

                import List { fold }

                data In = { xs: List<Int> }
                data Out = { ys: List<Int> }

                behavior work : (i: In) -> Out constructs Out

                let work (i) = Out { ys = copy(i.xs) }
                let copy (xs: List<Int>) = fold((acc, x) -> acc ++ [x], [], xs)
                """;
        BytesClassLoader loader = loader(module);
        Object in = decode(loader, "In", Map.of("xs", List.of(1L, 2L, 3L)));
        assertEquals(List.of(1L, 2L, 3L), run(loader, in).get("ys"));
    }

    /** {@code []} as one case of an {@code if}; the other case fixes the element type. */
    @Test
    void emptyListAsIfBranch() throws Exception {
        String module = """
                module demo

                data In = { keep: Bool, x: Int }
                data Out = { ys: List<Int> }

                behavior work : (i: In) -> Out constructs Out

                let work (i) = Out { ys = if i.keep then [i.x] else [] }
                """;
        BytesClassLoader loader = loader(module);
        assertEquals(List.of(9L), run(loader, decode(loader, "In", Map.of("keep", true, "x", 9L))).get("ys"));
        assertEquals(List.of(), run(loader, decode(loader, "In", Map.of("keep", false, "x", 9L))).get("ys"));
    }

    /**
     * A {@code map} result — an empty-seeded fold that grows {@code [] ++ [f(x)]} — feeding another
     * combinator. The grown list's element type must survive the concat, or the outer fold reads a
     * {@code Nothing} element and codegen crashes. Regression for the nested-combinator case the unit
     * tests missed until an example exercised it.
     */
    @Test
    void anEmptySeededFoldResultFeedsAnotherFold() throws Exception {
        String module = """
                module demo

                import List { fold, map }

                data In = { xs: List<Int> }
                data Out = { total: Int }

                behavior work : (i: In) -> Out constructs Out

                let work (i) = Out { total = fold((acc, x) -> acc + x, 0, map(dbl, i.xs)) }
                let dbl (n: Int) = n * 2
                """;
        BytesClassLoader loader = loader(module);
        Object in = decode(loader, "In", Map.of("xs", List.of(1L, 2L, 3L)));
        assertEquals(12L, run(loader, in).get("total"));   // (1 + 2 + 3) * 2
    }

    /** {@code []} straight into a {@code List<T>} field: an empty list of that type. */
    @Test
    void emptyListIntoField() throws Exception {
        String module = """
                module demo

                data In = { x: Int }
                data Out = { ys: List<Int> }

                behavior work : (i: In) -> Out constructs Out

                let work (i) = Out { ys = [] }
                """;
        BytesClassLoader loader = loader(module);
        assertEquals(List.of(), run(loader, decode(loader, "In", Map.of("x", 1L))).get("ys"));
    }
}
