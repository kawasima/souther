package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A recursive helper may take its step as a first-class function value (a closure), applied per
 * element via {@code Fn.apply}. This is the mechanism a self-hosted fold needs: an ordinary recursive
 * helper that walks the list, the self-call in tail position so the backend loops it. These tests
 * drive the mechanism with a module-own helper of concrete types, before it is generalized and moved
 * into {@code souther.list} as the fold that replaces the former privileged loop.
 */
class CompileFoldSelfHostTest {

    private long sum(List<Long> xs) throws Exception {
        String src = """
                module demo
                data Bag = { xs: List<Int> }
                data Out = Int
                behavior run : (b: Bag) -> Out constructs Out
                let run (b) = Out(List.foldFrom((acc, x) -> acc + x, 0, b.xs, 0))
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object bag = Codecs.decoded(loader, "demo.Bag", Map.of("xs", xs));
        Object behavior = loader.loadClass("demo.Run" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, bag);
        return (long) Codecs.encode(loader, "demo.Out", out);
    }

    @Test
    void aRecursiveHelperSumsAListThroughAClosureStep() throws Exception {
        assertEquals(15L, sum(List.of(1L, 2L, 3L, 4L, 5L)));
    }

    @Test
    void aRecursiveHelperOverAnEmptyListYieldsTheSeed() throws Exception {
        assertEquals(0L, sum(List.of()));
    }
}
