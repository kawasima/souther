package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code get} on a List returns Option<T>: Some(element) in range, None otherwise (spec 18.4). */
class CompileListGetTest {

    private static final String MODULE = """
            module demo

            import List ( get )

            data Item = String
            data Bag = { items: List<Item> }
            data Label = String

            behavior firstValue : (b: Bag) -> Label constructs Label

            let firstValue (b) =
                match get(0, b.items) with
                    | Some x -> Label { value = x.value }
                    | None -> Label { value = "none" }
            """;

    private String firstValue(Object... items) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object bag = Codecs.decoded(loader, "demo.Bag", Map.of("items", List.of(items)));

        Object behavior = loader.loadClass("demo.FirstValue").getConstructor().newInstance();
        Object label = Codecs.apply(behavior, bag);

        // Label is a single-field newtype, so its encoder yields the bare String.
        return (String) Codecs.encode(loader, "demo.Label", label);
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
