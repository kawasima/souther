package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A behavior can consume an optional field by matching {@code Some}/{@code None} (spec 16.3):
 * {@code Some as v} binds the unwrapped element, {@code None} has no binding.
 */
class CompileOptionMatchTest {

    private static final String MODULE = """
            module demo

            data Id { value: String }
            data Trip { id: Id  approver: Id? }
            data Label { value: String }

            behavior approverLabel(t: Trip) -> Label constructs Label {
                match t.approver {
                    case Some as a => Label { value: a.value }
                    case None => Label { value: "none" }
                }
            }
            """;

    private String run(Map<String, Raw> tripObject) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder<?> cd = (Decoder<?>) loader.loadClass("demo.Trip").getMethod("decoder").invoke(null);
        Object trip = cd.decode(Raw.object(tripObject));

        Object behavior = loader.loadClass("demo.approverLabel").getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        Object label = ((Behavior<Object, Object>) behavior).apply(trip);

        Encoder enc = (Encoder) loader.loadClass("demo.Label").getMethod("encoder").invoke(null);
        return ((Raw.TextValue) enc.encode(label)).value();
    }

    @Test
    void someBindsTheUnwrappedValue() throws Exception {
        assertEquals("e-9", run(Map.of("id", Raw.text("t-1"), "approver", Raw.text("e-9"))));
    }

    @Test
    void noneTakesTheNoneArm() throws Exception {
        assertEquals("none", run(Map.of("id", Raw.text("t-1"))));
    }
}
