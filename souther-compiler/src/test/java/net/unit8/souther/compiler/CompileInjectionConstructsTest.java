package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An injected behavior (a signature with no fn — spec 13.2) hands its Java implementation a
 * {@code protected} factory for every declared unit-data arm, and nothing else. A non-unit arm
 * it declares in {@code constructs} must therefore be reachable another way: as an exposed data
 * whose {@code decoder} is public (spec 13.3). One that is neither a unit nor exposed cannot be
 * built by the Java side at all — E1305.
 */
class CompileInjectionConstructsTest {

    @Test
    void aUnitArmNeedsNoExposure() {
        // 会員なし is a unit: the base class gets a protected factory for it, so no exposure is needed.
        assertDoesNotThrow(() -> Compiler.compile("""
                module demo
                exposing { Member }

                data Id = String
                data Member = { id: Id }
                data 会員なし

                behavior findMember : (id: Id) -> Member | 会員なし
                    constructs 会員なし
                """));
    }

    @Test
    void anExposedNonUnitArmIsAllowed() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module demo
                exposing { Member, 保存データ不正 }

                data Id = String
                data Member = { id: Id }
                data 保存データ不正 = { reason: String }

                behavior findMember : (id: Id) -> Member | 保存データ不正
                    constructs 保存データ不正
                """));
    }

    @Test
    void aNonUnitUnexposedArmIsE1305() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                exposing { Member }

                data Id = String
                data Member = { id: Id }
                data 保存データ不正 = { reason: String }

                behavior findMember : (id: Id) -> Member | 保存データ不正
                    constructs 保存データ不正
                """));
        assertEquals("E1305", e.code());
    }
}
