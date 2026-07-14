package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.NonEmptyList;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for data-typed fields: nested decoders and nested encode (spec 8.1, 10.3, 11.3). */
class CompileNestedTest {

    private static final String MEMBER = """
            module demo

            data MemberId {
                value: String
                invariant length(value) > 0
                decoder from Text as input {
                    let v = trim(input)
                    require length(v) > 0 else EmptyId
                    MemberId { value: v }
                }
                encoder self { Text(self.value) }
            }

            data Email {
                value: String
                invariant contains(value, "@")
                decoder from Text as input {
                    let n = lowercase(trim(input))
                    require contains(n, "@") else MissingAtSign
                    Email { value: n }
                }
                encoder self { Text(self.value) }
            }

            data Member {
                id: MemberId
                email: Email
                decoder from Object {
                    id    <- field("id", MemberId.decoder)
                    email <- field("email", Email.decoder)
                    Member { id, email }
                }
                encoder self {
                    Object {
                        "id": MemberId.encode(self.id),
                        "email": Email.encode(self.email)
                    }
                }
            }
            """;

    private Class<?> member() throws Exception {
        return new BytesClassLoader(Compiler.compile(MEMBER), getClass().getClassLoader())
                .loadClass("demo.Member");
    }

    @Test
    void decodesNestedAndEncodesBack() throws Exception {
        Class<?> member = member();
        Decoder<?> decoder = (Decoder<?>) member.getMethod("decoder").invoke(null);

        Raw input = Raw.object(Map.of("id", Raw.text("m-1"), "email", Raw.text("A@B")));
        Result<?, NonEmptyList<DecodeError>> ok = decoder.decode(input);
        assertTrue(ok.isOk(), "nested decode should succeed");

        Encoder enc = (Encoder) member.getMethod("encoder").invoke(null);
        Raw.ObjectValue encoded = (Raw.ObjectValue) enc.encode(((Result.Ok<?, ?>) ok).value());
        assertEquals(Raw.text("m-1"), encoded.value().get("id"));
        assertEquals(Raw.text("a@b"), encoded.value().get("email"), "nested email was normalized");
    }

    @Test
    void accumulatesNestedErrorsWithFieldPaths() throws Exception {
        Decoder<?> decoder = (Decoder<?>) member().getMethod("decoder").invoke(null);

        Raw input = Raw.object(Map.of("id", Raw.text(""), "email", Raw.text("bad")));
        Result<?, NonEmptyList<DecodeError>> bad = decoder.decode(input);
        assertTrue(bad.isErr());
        NonEmptyList<DecodeError> errors = ((Result.Err<?, NonEmptyList<DecodeError>>) bad).error();

        assertEquals(2, errors.size());
        Set<String> codes = errors.toList().stream().map(DecodeError::code).collect(Collectors.toSet());
        assertEquals(Set.of("EmptyId", "MissingAtSign"), codes);
        // each error is rooted at its field
        for (DecodeError e : errors.toList()) {
            assertTrue(e.path().get(0) instanceof DecodeError.PathElement.Field);
        }
    }
}
