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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior may be imported and composed two hops away (spec 4, 14): module {@code c} composes
 * {@code b}'s {@code twice}, whose own definition composes {@code a}'s {@code inc}. Resolving
 * {@code twice}'s signature for {@code c} has to resolve {@code b}'s imports in turn, not just
 * {@code b}'s own definitions — an import chain deeper than one hop.
 */
class CompileModuleChainTest {

    private static final String A = """
            module a exposing { N, inc }
            data N = Int
            behavior inc = (n: N) -> N
            fn inc (n) = n
            """;
    private static final String B = """
            module b exposing { twice : N }
            import a { N, inc }
            behavior twice = inc >-> inc
            """;
    // c imports N as well: its generated `quad` carries a `Behavior<N, N>` signature (spec 19.8,
    // 24), so N's class must be nameable here — the same reason Java code importing a generic method
    // imports its type arguments.
    private static final String C = """
            module c
            import a { N }
            import b { twice }
            behavior quad = twice >-> twice
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void composesABehaviorTwoImportHopsAway() throws Exception {
        Map<String, byte[]> classes = Compiler.compileModules(List.of(A, B, C));
        assertTrue(classes.containsKey("c.Quad"), "the two-hop composition generates c.Quad");

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());
        Decoder nDec = (Decoder) loader.loadClass("a.N").getMethod("decoder").invoke(null);
        Object five = ((Ok) nDec.decode(5L, Path.ROOT)).value();
        Object quad = loader.loadClass("c.Quad").getConstructor().newInstance();
        Object r = ((Behavior) quad).apply(five);

        Encoder enc = (Encoder) loader.loadClass("a.N").getMethod("encoder").invoke(null);
        assertEquals(5L, enc.encode(r), "inc is identity, so quad round-trips 5 through the chain");
    }
}
