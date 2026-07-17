package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec 19.8: a behavior whose output is an anonymous union (`-> A | B`) gets a generated
 * sealed interface {@code <behavior名>結果} whose {@code permits} are the union arms, and every
 * arm data implements it.
 */
class CompileBehaviorResultTest {

    private static final String MODULE = """
            module demo

            data Draft = Int
            data Cheap = { cost: Int }
            data Pricey = { cost: Int }

            behavior classify = (d: Draft) -> Cheap | Pricey constructs Cheap, Pricey

            fn classify (d) =
                if d.value <= 100 then Cheap { cost: d.value } else Pricey { cost: d.value }
            """;

    @Test
    void anonymousUnionOutputGeneratesASealedResultInterface() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Class<?> result = loader.loadClass("demo.classify結果");
        assertTrue(result.isInterface(), "結果 must be an interface");
        assertTrue(result.isSealed(), "結果 must be sealed");
        Set<String> permitted = Arrays.stream(result.getPermittedSubclasses())
                .map(Class::getName).collect(Collectors.toSet());
        assertEquals(Set.of("demo.Cheap", "demo.Pricey"), permitted);
    }

    @Test
    void eachArmImplementsTheResultInterface() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Class<?> result = loader.loadClass("demo.classify結果");
        assertTrue(result.isAssignableFrom(loader.loadClass("demo.Cheap")));
        assertTrue(result.isAssignableFrom(loader.loadClass("demo.Pricey")));
    }
}
