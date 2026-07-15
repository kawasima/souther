package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code get} on a List returns Option<T>: Some(element) in range, None otherwise (spec 18.4). */
class CompileListGetTest {

    private static final String MODULE = """
            module demo

            data Item { value: String }
            data Bag { items: List<Item> }
            data Label { value: String }

            behavior firstValue(b: Bag) -> Label constructs Label {
                match get(b.items, 0) {
                    case Some as x => Label { value: x.value }
                    case None => Label { value: "none" }
                }
            }
            """;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String firstValue(List<Raw> items) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder<?> bagDec = (Decoder<?>) loader.loadClass("demo.Bag").getMethod("decoder").invoke(null);
        Object bag = bagDec.decode(Raw.object(Map.of("items", Raw.list(items))));

        Object label = ((Behavior<Object, Object>) loader.loadClass("demo.firstValue")
                .getConstructor().newInstance()).apply(bag);

        Encoder enc = (Encoder) loader.loadClass("demo.Label").getMethod("encoder").invoke(null);
        return ((Raw.TextValue) enc.encode(label)).value();
    }

    @Test
    void someWhenInRange() throws Exception {
        assertEquals("a", firstValue(List.of(Raw.text("a"), Raw.text("b"))));
    }

    @Test
    void noneWhenEmpty() throws Exception {
        assertEquals("none", firstValue(List.of()));
    }
}
