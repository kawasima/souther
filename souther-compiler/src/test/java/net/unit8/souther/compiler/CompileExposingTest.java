package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every name in an {@code exposing} clause must resolve to a data or behavior of the module, and
 * the clause is type-granular: a data's {@code decoder}/{@code encoder} are always public once the
 * data is exposed (spec 4, 19.4), so a {@code A.decoder} member is rejected. A typo used to be
 * accepted silently — exposing nothing — which quietly left a type package-private.
 */
class CompileExposingTest {

    @Test
    void anExposedNameThatDoesNotExistIsRejected() {
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                exposing ( NoSuchType )
                data Real = { v: Int }
                """));
    }

    @Test
    void aDecoderMemberInExposingIsRejected() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                exposing ( Real.decoder )
                data Real = { v: Int }
                """));
        assertTrue(e.getMessage().contains("type-granular"), e.getMessage());
    }

    @Test
    void realExposedNamesAreAccepted() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module demo
                exposing ( Real, greet )

                data Real = { v: Int }
                data Out = { v: Int }

                behavior greet : (r: Real) -> Out constructs Out
                let greet (r) = Out { v = r.v }
                """));
    }
}
