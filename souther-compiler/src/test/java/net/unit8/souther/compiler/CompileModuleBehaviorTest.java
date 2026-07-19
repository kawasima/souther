package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Spec 4 / 14: a behavior exposed by one module can be imported and composed in another. Here
 * {@code m.b} imports {@code m.a}'s {@code inc} and builds {@code twice = inc >-> inc}; the stage
 * resolves to {@code m.a.inc} in the declaring module's package.
 */
class CompileModuleBehaviorTest {

    private static final String A = """
            module m.a exposing ( N, inc )

            data N = Int

            behavior inc : (n: N) -> N constructs N
            let inc (n) = N { value = n.value + 1 }
            """;

    private static final String B = """
            module m.b

            import m.a ( N, inc )

            behavior twice = inc >-> inc
            """;

    @Test
    void importedBehaviorComposesInAnotherModule() throws Exception {
        Map<String, byte[]> classes = Compiler.compileModules(List.of(A, B));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Object five = Codecs.decoded(loader, "m.a.N", 5L);

        Object twice = loader.loadClass("m.b.Twice").getConstructor().newInstance();
        Object r = Codecs.apply(twice, five);

        // inc twice: 5 -> 6 -> 7. N is a newtype, so its encoder yields the bare Long.
        assertEquals(7L, Codecs.encode(loader, "m.a.N", r));
    }
}
