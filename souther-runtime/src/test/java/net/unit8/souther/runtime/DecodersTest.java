package net.unit8.souther.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Smoke tests for the runtime facade's Raoh bridge (spec section 10.6). */
class DecodersTest {

    @Test
    void textReadsRawText() {
        Result<String, NonEmptyList<DecodeError>> r = Decoders.text(Raw.text("hi"));
        assertTrue(r.isOk());
        assertEquals("hi", ((Result.Ok<String, ?>) r).value());
    }

    @Test
    void textRejectsNonText() {
        Result<String, NonEmptyList<DecodeError>> r = Decoders.text(Raw.integer(5));
        assertTrue(r.isErr());
        assertEquals("expected_text",
                ((Result.Err<String, NonEmptyList<DecodeError>>) r).error().head().code());
    }

    @Test
    void integerReadsRawInt() {
        Result<Long, NonEmptyList<DecodeError>> r = Decoders.integer(Raw.integer(42));
        assertTrue(r.isOk());
        assertEquals(42L, ((Result.Ok<Long, ?>) r).value());
    }
}
