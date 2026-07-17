package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

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

            data Id = String
            data Trip = { id: Id  approver: Id? }
            data Label = String

            behavior approverLabel = (t: Trip) -> Label constructs Label

            fn approverLabel (t) =
                match t.approver {
                    case Some as a -> Label { value: a.value }
                    case None -> Label { value: "none" }
                }
            """;

    private String run(Map<String, Object> tripObject) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder cd = (Decoder) loader.loadClass("demo.Trip").getMethod("decoder").invoke(null);
        Object trip = ((Ok) cd.decode(tripObject, Path.ROOT)).value();

        Object behavior = loader.loadClass("demo.ApproverLabel").getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        Object label = ((Behavior<Object, Object>) behavior).apply(trip);

        // Label is a single-field newtype, so its encoder yields the bare String.
        Encoder enc = (Encoder) loader.loadClass("demo.Label").getMethod("encoder").invoke(null);
        return (String) enc.encode(label);
    }

    @Test
    void someBindsTheUnwrappedValue() throws Exception {
        assertEquals("e-9", run(Map.of("id", "t-1", "approver", "e-9")));
    }

    @Test
    void noneTakesTheNoneArm() throws Exception {
        assertEquals("none", run(Map.of("id", "t-1")));
    }
}
