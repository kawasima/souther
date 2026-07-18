package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** End-to-end test for pure single-input behaviors and the constructs checks (spec 12, 22.2, 22.3). */
class CompileBehaviorTest {

    private static final String MODULE = """
            module demo

            data MemberId = String

            data Member = {
                id: MemberId
                , name: String
            }

            data Response = { id: MemberId }

            behavior toResponse : (m: Member) -> Response
                constructs Response

            let toResponse (m) = Response { id = m.id }
            """;

    @Test
    @SuppressWarnings("unchecked")
    void pureBehaviorTransformsAValue() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Decoder memberDecoder = (Decoder) loader.loadClass("demo.Member")
                .getMethod("decoder").invoke(null);
        Object member = ((Ok) memberDecoder.decode(
                Map.of("id", "m-1", "name", "bob"), Path.ROOT)).value();

        Object behavior = loader.loadClass("demo.ToResponse").getConstructor().newInstance();
        Object response = ((Behavior<Object, Object>) behavior).apply(member);

        Encoder responseEncoder = (Encoder) loader.loadClass("demo.Response")
                .getMethod("encoder").invoke(null);
        Map<?, ?> encoded = (Map<?, ?>) responseEncoder.encode(response);
        assertEquals("m-1", encoded.get("id"), "response carries the member id");
    }

    @Test
    void undeclaredConstructionIsE1002() {
        // `constructs` may be omitted (then inferred), but a declared clause must be complete: here
        // `Empty` is declared while `Response` is also built, so the undeclared `Response` is E1002.
        String src = """
                module demo
                data Response = { id: String }
                data Empty
                behavior make : (x: String) -> Response | Empty constructs Empty

                let make (x) = if x == "" then Empty else Response { id = x }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }

    /** System 2 (ADR-0026): a behavior signature uses `:`, so the old `=` signature is rejected. */
    @Test
    void oldEqualsSignatureIsRejected() {
        String src = MODULE.replace("behavior toResponse : (m: Member)",
                "behavior toResponse = (m: Member)");
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    /** System 2 (ADR-0026): an implementation is `let`, so the old `fn` keyword no longer parses. */
    @Test
    void oldFnKeywordIsRejected() {
        String src = MODULE.replace("let toResponse (m)", "fn toResponse (m)");
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    @Test
    void constructingInvariantDataNeedsNoViolationCase() {
        // A violation aborts (spec 7.3, 9.4), so the output needs no 制約違反 case — this compiles.
        String src = """
                module demo
                data Positive = { value: Int } invariant value > 0
                behavior make : (x: Int) -> Positive
                    constructs Positive

                let make (x) = Positive { value = x }
                """;
        Compiler.compile(src);
    }
}
