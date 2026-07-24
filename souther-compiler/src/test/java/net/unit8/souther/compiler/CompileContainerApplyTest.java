package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A collection parameter of a behavior's {@code apply} keeps its runtime interface type
 * ({@code java.util.List/Map/Set}, or the runtime {@code Option}) plus a generic signature, rather
 * than degrading to {@code Object} (issue #57). This is what a Java/Kotlin/Scala/Clojure author sees.
 */
class CompileContainerApplyTest {

    @Test
    void aMultiInputInjectedApplyTypesAListParamAsJavaUtilList() throws Exception {
        String src = """
                module demo
                data A = { x: Int }
                data R = { z: Int }
                behavior take : (xs: List<A>, n: Int) -> R
                    constructs R
                behavior use : (xs: List<A>, n: Int) -> R requires take
                let use (xs, n, take) = take(xs, n)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Class<?> take = loader.loadClass("demo.Take");
        // the typed apply carries java.util.List, not Object
        assertNotNull(take.getMethod("apply", List.class, Long.class),
                "a List param types as java.util.List in the multi-input apply");
        // and its generic signature recovers List<A>
        Type first = take.getMethod("apply", List.class, Long.class).getGenericParameterTypes()[0];
        ParameterizedType pt = assertInstanceOf(ParameterizedType.class, first);
        assertEquals(List.class, pt.getRawType());
        assertEquals("demo.A", ((Class<?>) pt.getActualTypeArguments()[0]).getName());
    }

    @Test
    void aSingleInputInjectedWithAListInputKeepsItsBehaviorGenericSignature() throws Exception {
        String src = """
                module demo
                data A = { x: Int }
                data R = { z: Int }
                behavior sum : (xs: List<A>) -> R
                    constructs R
                behavior use : (xs: List<A>) -> R requires sum
                let use (xs, sum) = sum(xs)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Class<?> sum = loader.loadClass("demo.Sum");
        // a single-input injected base still implements Behavior<In, Out>; the In must be List<A>, not dropped
        Type iface = null;
        for (Type t : sum.getGenericInterfaces()) {
            if (t instanceof ParameterizedType p && p.getRawType() == Behavior.class) {
                iface = t;
            }
        }
        ParameterizedType behavior = assertInstanceOf(ParameterizedType.class, iface,
                "the base keeps a parameterized Behavior<In, Out> signature");
        Type in = behavior.getActualTypeArguments()[0];
        ParameterizedType inList = assertInstanceOf(ParameterizedType.class, in);
        assertEquals(List.class, inList.getRawType());
    }
}
