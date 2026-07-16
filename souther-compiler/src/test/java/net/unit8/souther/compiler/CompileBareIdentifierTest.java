package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bare identifier is a local if one is bound, and otherwise the construction of the unit data
 * of that name (spec 8.4). Souther does not distinguish the two by case — business vocabulary is
 * written in Japanese, which has none — so the scope decides.
 */
class CompileBareIdentifierTest {

    /** Regression: the permission check read a local as constructing the unit data it shadows. */
    @Test
    void aLocalShadowsAUnitDataOfTheSameName() {
        String src = """
                module demo
                data 立替
                data R = { v: Int }
                behavior f = (立替: Int) -> R constructs R
                fn f (立替) = R { v: 立替 }
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.f"), "立替 here is the parameter, not a construction");
    }

    @Test
    void aLetAlsoShadows() {
        String src = """
                module demo
                data 立替
                data R = { v: Int }
                behavior f = (x: Int) -> R constructs R
                fn f (x) = {
                    let 立替 = x
                    R { v: 立替 }
                }
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.f"));
    }

    /** With nothing bound, the same name is the unit's construction and needs declaring. */
    @Test
    void anUnboundNameConstructsTheUnitData() {
        String src = """
                module demo
                data 立替
                behavior f = (x: Int) -> 立替
                fn f (x) = 立替
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }

    @Test
    void declaringItLetsTheUnitBeConstructed() {
        String src = """
                module demo
                data 立替
                behavior f = (x: Int) -> 立替 constructs 立替
                fn f (x) = 立替
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.f"));
    }
}
