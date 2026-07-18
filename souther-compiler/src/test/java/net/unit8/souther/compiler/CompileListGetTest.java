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

/** {@code get} on a List returns Option<T>: Some(element) in range, None otherwise (spec 18.4). */
class CompileListGetTest {

    private static final String MODULE = """
            module demo

            import List { get }

            data Item = String
            data Bag = { items: List<Item> }
            data Label = String

            behavior firstValue : (b: Bag) -> Label constructs Label

            let firstValue (b) =
                match get(0, b.items) with
                    | Some as x -> Label { value = x.value }
                    | None -> Label { value = "none" }
            """;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String firstValue(Object... items) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder bagDec = (Decoder) loader.loadClass("demo.Bag").getMethod("decoder").invoke(null);
        Object bag = ((Ok) bagDec.decode(Map.of("items", List.of(items)), Path.ROOT)).value();

        Object label = ((Behavior<Object, Object>) loader.loadClass("demo.FirstValue")
                .getConstructor().newInstance()).apply(bag);

        // Label is a single-field newtype, so its encoder yields the bare String.
        Encoder enc = (Encoder) loader.loadClass("demo.Label").getMethod("encoder").invoke(null);
        return (String) enc.encode(label);
    }

    @Test
    void someWhenInRange() throws Exception {
        assertEquals("a", firstValue("a", "b"));
    }

    @Test
    void noneWhenEmpty() throws Exception {
        assertEquals("none", firstValue());
    }
}
