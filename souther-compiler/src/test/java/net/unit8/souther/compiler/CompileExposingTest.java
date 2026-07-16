package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every name in an {@code exposing} clause must resolve to a data or behavior of the module
 * (spec 4). A typo used to be accepted silently — exposing nothing — which quietly left a type
 * package-private. It is now a compile error.
 */
class CompileExposingTest {

    @Test
    void anExposedNameThatDoesNotExistIsRejected() {
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                exposing { NoSuchType }
                data Real = { v: Int }
                """));
    }

    @Test
    void aMisspelledDecoderMemberIsRejected() {
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                exposing { Reol.decoder }
                data Real = { v: Int }
                """));
    }

    @Test
    void realExposedNamesAreAccepted() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module demo
                exposing { Real, Real.decoder, greet }

                data Real = { v: Int }
                data Out = { v: Int }

                behavior greet = (r: Real) -> Out constructs Out
                fn greet (r) = Out { v: r.v }
                """));
    }
}
