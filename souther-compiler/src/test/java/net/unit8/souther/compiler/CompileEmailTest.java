package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.NonEmptyList;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end golden test: compile a single-field {@code data} with invariant, decoder,
 * and encoder to bytecode, load it, and drive decode/encode (spec section 27.1, 27.7).
 */
class CompileEmailTest {

    private static final String EMAIL = """
            module demo

            data Email {
                value: String
                invariant contains(value, "@")

                decoder from Text as input {
                    let normalized = lowercase(trim(input))
                    require length(normalized) > 0 else EmptyEmail
                    require contains(normalized, "@") else MissingAtSign
                    Email { value: normalized }
                }
                encoder self {
                    Text(self.value)
                }
            }
            """;

    private BytesClassLoader compile(String src) {
        Map<String, byte[]> classes = Compiler.compile(src);
        return new BytesClassLoader(classes, getClass().getClassLoader());
    }

    @Test
    void decodesNormalizesAndEncodes() throws Exception {
        Class<?> email = compile(EMAIL).loadClass("demo.Email");
        Decoder<?> decoder = (Decoder<?>) email.getMethod("decoder").invoke(null);

        Result<?, NonEmptyList<DecodeError>> ok = decoder.decode(Raw.text("  A@B.com "));
        assertTrue(ok.isOk(), "expected Ok for a valid, normalizable email");

        Object emailValue = ((Result.Ok<?, ?>) ok).value();
        Encoder enc = (Encoder) email.getMethod("encoder").invoke(null);
        Raw raw = enc.encode(emailValue);
        assertEquals("a@b.com", ((Raw.TextValue) raw).value(),
                "decoder should trim + lowercase before construction");
    }

    @Test
    void accumulatesTheRightErrorCode() throws Exception {
        Decoder<?> decoder = (Decoder<?>) compile(EMAIL).loadClass("demo.Email")
                .getMethod("decoder").invoke(null);

        Result<?, NonEmptyList<DecodeError>> missingAt = decoder.decode(Raw.text("nope"));
        assertTrue(missingAt.isErr());
        assertEquals("MissingAtSign",
                ((Result.Err<?, NonEmptyList<DecodeError>>) missingAt).error().head().code());

        Result<?, NonEmptyList<DecodeError>> empty = decoder.decode(Raw.text("   "));
        assertTrue(empty.isErr());
        assertEquals("EmptyEmail",
                ((Result.Err<?, NonEmptyList<DecodeError>>) empty).error().head().code());
    }

    @Test
    void unicodeAndInvariantOnlyDataVerify() throws Exception {
        String src = """
                module demo

                data 金額 {
                    value: Int
                    invariant value >= 0
                }

                data 管理職 {
                    level: Int
                    invariant level >= 1 && level <= 5
                }
                """;
        BytesClassLoader loader = compile(src);
        // Loading forces bytecode verification.
        loader.loadClass("demo.金額");
        loader.loadClass("demo.管理職");
    }

    @Test
    void invariantMustBeBool() {
        String src = """
                module demo
                data X {
                    value: String
                    invariant value
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1101", e.code());
    }
}
