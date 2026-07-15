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

            data MemberId = { value: String }

            data Member = {
                id: MemberId
                name: String
            }

            data Response = { id: MemberId }

            behavior toResponse = (m: Member) -> Response
                constructs Response
            {
                Response { id: m.id }
            }
            """;

    @Test
    @SuppressWarnings("unchecked")
    void pureBehaviorTransformsAValue() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Decoder memberDecoder = (Decoder) loader.loadClass("demo.Member")
                .getMethod("decoder").invoke(null);
        Object member = ((Ok) memberDecoder.decode(
                Map.of("id", "m-1", "name", "bob"), Path.ROOT)).value();

        Object behavior = loader.loadClass("demo.toResponse").getConstructor().newInstance();
        Object response = ((Behavior<Object, Object>) behavior).apply(member);

        Encoder responseEncoder = (Encoder) loader.loadClass("demo.Response")
                .getMethod("encoder").invoke(null);
        Map<?, ?> encoded = (Map<?, ?>) responseEncoder.encode(response);
        assertEquals("m-1", encoded.get("id"), "response carries the member id");
    }

    @Test
    void undeclaredConstructionIsE1002() {
        String src = """
                module demo
                data Response = { id: String }
                behavior make = (x: String) -> Response {
                    Response { id: x }
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }

    @Test
    void constructingInvariantDataInPureBehaviorIsE1003() {
        String src = """
                module demo
                data Positive = { value: Int  invariant value > 0 }
                behavior make = (x: Int) -> Positive
                    constructs Positive
                {
                    Positive { value: x }
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1003", e.code());
    }
}
