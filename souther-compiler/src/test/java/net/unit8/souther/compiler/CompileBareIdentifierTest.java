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
                behavior f : (立替: Int) -> R constructs R
                let f (立替) = R { v: 立替 }
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"), "立替 here is the parameter, not a construction");
    }

    @Test
    void aLetAlsoShadows() {
        String src = """
                module demo
                data 立替
                data R = { v: Int }
                behavior f : (x: Int) -> R constructs R
                let f (x) = {
                    let 立替 = x
                    R { v: 立替 }
                }
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"));
    }

    /** With nothing bound, the same name is the unit's construction and needs declaring. */
    @Test
    void anUnboundNameConstructsTheUnitData() {
        // the bare name `立替` resolves to a unit construction: `空` is declared but `立替` is also
        // built, so the undeclared `立替` is E1002 — proving the bare name counts as a construction.
        String src = """
                module demo
                data 立替
                data 空
                behavior f : (x: Int) -> 立替 | 空 constructs 空
                let f (x) = if x > 0 then 立替 else 空
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }

    @Test
    void declaringItLetsTheUnitBeConstructed() {
        String src = """
                module demo
                data 立替
                behavior f : (x: Int) -> 立替 constructs 立替
                let f (x) = 立替
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"));
    }
}
