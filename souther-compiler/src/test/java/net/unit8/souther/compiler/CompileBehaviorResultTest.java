package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec 19.8: a behavior whose output is an anonymous union (`-> A | B`) gets a generated
 * sealed interface {@code <behavior名>結果} whose {@code permits} are the union cases, and every
 * case data implements it.
 */
class CompileBehaviorResultTest {

    private static final String MODULE = """
            module demo

            data Draft = Int
            data Cheap = { cost: Int }
            data Pricey = { cost: Int }

            behavior classify : (d: Draft) -> Cheap | Pricey constructs Cheap, Pricey

            let classify (d) =
                if d.value <= 100 then Cheap { cost = d.value } else Pricey { cost = d.value }
            """;

    @Test
    void anonymousUnionOutputGeneratesASealedResultInterface() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Class<?> result = loader.loadClass("demo.Classify結果");
        assertTrue(result.isInterface(), "結果 must be an interface");
        assertTrue(result.isSealed(), "結果 must be sealed");
        Set<String> permitted = Arrays.stream(result.getPermittedSubclasses())
                .map(Class::getName).collect(Collectors.toSet());
        assertEquals(Set.of("demo.Cheap", "demo.Pricey"), permitted);
    }

    @Test
    void eachCaseImplementsTheResultInterface() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Class<?> result = loader.loadClass("demo.Classify結果");
        assertTrue(result.isAssignableFrom(loader.loadClass("demo.Cheap")));
        assertTrue(result.isAssignableFrom(loader.loadClass("demo.Pricey")));
    }

    private static final String INJECTED = """
            module demo
            exposing ( Member )

            data Id = String
            data Member = { id: Id }
            data 会員なし

            behavior findMember : (id: Id) -> Member | 会員なし
                constructs 会員なし
            """;

    /** Spec 19.8/24: the abstract base a Java implementation extends declares the result interface
     *  as its {@code Behavior} return type, so the author writes {@code findMember結果 apply(Id)}. */
    @Test
    void injectedBaseDeclaresTheResultInterfaceAsItsGenericReturnType() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(INJECTED), getClass().getClassLoader());
        Class<?> base = loader.loadClass("demo.FindMember");
        java.lang.reflect.Type[] ifaces = base.getGenericInterfaces();
        assertEquals(1, ifaces.length);
        ParameterizedType pt = (ParameterizedType) ifaces[0];
        assertEquals(Behavior.class, pt.getRawType());
        java.lang.reflect.Type[] args = pt.getActualTypeArguments();
        assertEquals("demo.Id", ((Class<?>) args[0]).getName());
        assertEquals("demo.FindMember結果", ((Class<?>) args[1]).getName());
    }
}
