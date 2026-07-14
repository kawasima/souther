package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.DecodeFailure;
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

/**
 * End-to-end test for multi-field product data: an {@code Object} decoder that
 * accumulates every field error (spec section 27.7) and an {@code Object} encoder.
 */
class CompileObjectTest {

    private static final String ACCOUNT = """
            module demo

            data Account {
                id: String
                balance: Int
                owner: String

                invariant length(id) > 0
            }
            """;

    private Class<?> compileAccount() throws Exception {
        Map<String, byte[]> classes = Compiler.compile(ACCOUNT);
        return new BytesClassLoader(classes, getClass().getClassLoader()).loadClass("demo.Account");
    }

    @Test
    void decodesAndEncodesAnObject() throws Exception {
        Class<?> account = compileAccount();
        Decoder<?> decoder = (Decoder<?>) account.getMethod("decoder").invoke(null);

        Raw input = Raw.object(Map.of(
                "id", Raw.text("acc-1"),
                "balance", Raw.integer(100),
                "owner", Raw.text("bob")));

        Object value = decoder.decode(input);
        assertTrue(!(value instanceof DecodeFailure), "expected a valid account to decode");
        Encoder enc = (Encoder) account.getMethod("encoder").invoke(null);
        Raw.ObjectValue encoded = (Raw.ObjectValue) enc.encode(value);
        assertEquals(Raw.integer(100), encoded.value().get("balance"));
        assertEquals(Raw.text("acc-1"), encoded.value().get("id"));
    }

    @Test
    void accumulatesEveryFieldError() throws Exception {
        Decoder<?> decoder = (Decoder<?>) compileAccount().getMethod("decoder").invoke(null);

        // id is an int (expected text), balance is text (expected int), owner is missing.
        Raw input = Raw.object(Map.of(
                "id", Raw.integer(5),
                "balance", Raw.text("nope")));

        Object bad = decoder.decode(input);
        assertTrue(bad instanceof DecodeFailure);
        NonEmptyList<DecodeError> errors = ((DecodeFailure) bad).errors();
        assertEquals(3, errors.size(), "all three field errors should accumulate");

        Set<String> codes = errors.toList().stream()
                .map(DecodeError::code)
                .collect(Collectors.toSet());
        assertEquals(Set.of("expected_text", "expected_int", "required"), codes);
    }

    @Test
    void invariantRunsAfterAccumulation() throws Exception {
        Decoder<?> decoder = (Decoder<?>) compileAccount().getMethod("decoder").invoke(null);

        Raw input = Raw.object(Map.of(
                "id", Raw.text(""),
                "balance", Raw.integer(0),
                "owner", Raw.text("bob")));

        Object bad = decoder.decode(input);
        assertTrue(bad instanceof DecodeFailure);
        assertEquals("invariant_violation",
                ((DecodeFailure) bad).errors().head().code());
    }
}
