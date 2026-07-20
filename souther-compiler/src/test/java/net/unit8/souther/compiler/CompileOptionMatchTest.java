package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior can consume an optional field by matching {@code Some}/{@code None} (spec 16.3):
 * {@code Some v} binds the unwrapped element positionally (F#/Elm form); {@code None} has no binding.
 */
class CompileOptionMatchTest {

    private static final String MODULE = """
            module demo

            data Id = String
            data Trip = { id: Id, approver: Id? }
            data Label = String

            behavior approverLabel : (t: Trip) -> Label constructs Label

            let approverLabel (t) =
                match t.approver with
                    | Some a -> Label { value = a.value }
                    | None -> Label { value = "none" }
            """;

    private String run(Map<String, Object> tripObject) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object trip = Codecs.decoded(loader, "demo.Trip", tripObject);

        Object behavior = loader.loadClass("demo.ApproverLabel").getConstructor().newInstance();
        Object label = Codecs.apply(behavior, trip);

        // Label is a single-field newtype, so its encoder yields the bare String.
        return (String) Codecs.encode(loader, "demo.Label", label);
    }

    @Test
    void someBindsTheUnwrappedValue() throws Exception {
        assertEquals("e-9", run(Map.of("id", "t-1", "approver", "e-9")));
    }

    @Test
    void noneTakesTheNoneCase() throws Exception {
        assertEquals("none", run(Map.of("id", "t-1")));
    }

    @Test
    void someWithAsIsRejectedInFavourOfThePositionalForm() {
        // `as` binds the whole matched value everywhere; the wrapped value is reached positionally.
        String src = MODULE.replace("| Some a ->", "| Some as a ->");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("Some"), e.getMessage());
    }

    @Test
    void someAndNoneCannotBeDeclaredAsUserData() {
        // Some/None are the built-in Option cases; declaring one as a data type is rejected, so a
        // `| Some v` pattern is unambiguously about Option, not a user case (ADR-0011, ADR-0035).
        String someSrc = """
                module demo
                data Some = { x: Int }
                """;
        CompileException a = assertThrows(CompileException.class, () -> Compiler.compile(someSrc));
        assertTrue(a.getMessage().contains("Some"), a.getMessage());

        String noneSrc = """
                module demo
                data None = { x: Int }
                """;
        CompileException b = assertThrows(CompileException.class, () -> Compiler.compile(noneSrc));
        assertTrue(b.getMessage().contains("None"), b.getMessage());
    }
}
