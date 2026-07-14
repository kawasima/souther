package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.NonEmptyList;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integer-tag discriminate for reading versioned records (representation_version, spec 10.4). */
class CompileVersionTest {

    private static final String MODULE = """
            module demo

            data V1 { name: String  decoder from Object { name <- field("fullName", string)  V1 { name } } }
            data V2 { name: String  decoder from Object { name <- field("name", string)  V2 { name } } }

            data Person = V1 | V2 {
                decoder from Object discriminate on "representation_version" {
                    1 => V1.decoder
                    2 => V2.decoder
                }
            }
            """;

    @Test
    void dispatchesOnAnIntegerVersion() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder<?> decoder = (Decoder<?>) loader.loadClass("demo.Person").getMethod("decoder").invoke(null);

        Result<?, NonEmptyList<DecodeError>> v1 = decoder.decode(Raw.object(Map.of(
                "representation_version", Raw.integer(1), "fullName", Raw.text("Bob"))));
        assertTrue(v1.isOk());
        assertEquals("demo.V1", ((Result.Ok<?, ?>) v1).value().getClass().getName());

        Result<?, NonEmptyList<DecodeError>> v2 = decoder.decode(Raw.object(Map.of(
                "representation_version", Raw.integer(2), "name", Raw.text("Bob"))));
        assertTrue(v2.isOk());
        assertEquals("demo.V2", ((Result.Ok<?, ?>) v2).value().getClass().getName());

        Result<?, NonEmptyList<DecodeError>> v3 = decoder.decode(Raw.object(Map.of(
                "representation_version", Raw.integer(3))));
        assertTrue(v3.isErr());
        assertEquals("no_variant",
                ((Result.Err<?, NonEmptyList<DecodeError>>) v3).error().head().code());
    }
}
