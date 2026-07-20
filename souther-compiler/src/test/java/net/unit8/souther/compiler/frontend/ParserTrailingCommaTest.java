package net.unit8.souther.compiler.frontend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * A trailing comma before a closing delimiter is accepted in every comma-separated list, so a
 * writer coming from Go or TypeScript may leave one. The record family and import/exposing already
 * tolerated it; these cases cover the bracket-delimited lists that did not.
 */
class ParserTrailingCommaTest {

    @Test
    void behaviorParameters() {
        assertDoesNotThrow(() -> CstFrontend.parse("""
                module demo
                data A = Int
                data C = Int
                behavior f : (
                    a: A,
                    b: A,
                ) -> C
                """));
    }

    @Test
    void letParametersAndCallArguments() {
        assertDoesNotThrow(() -> CstFrontend.parse("""
                module demo
                let g (x) = h(1, 2,)
                let f (
                    a,
                    b,
                ) = 0
                """));
    }

    @Test
    void listLiteralAndTuple() {
        assertDoesNotThrow(() -> CstFrontend.parse("""
                module demo
                let l (x) = [1, 2, 3,]
                let t (x) = (1, 2,)
                """));
    }

    @Test
    void tupleType() {
        assertDoesNotThrow(() -> CstFrontend.parse("""
                module demo
                data X = { p: List<(Int, Int,)> }
                """));
    }

    @Test
    void lambdaParameters() {
        assertDoesNotThrow(() -> CstFrontend.parse("""
                module demo
                let f (xs) = List.fold((acc, x,) -> acc, 0, xs)
                """));
    }

    @Test
    void constructsAndRequiresNameList() {
        assertDoesNotThrow(() -> CstFrontend.parse("""
                module demo
                data A = Int
                data B = Int
                behavior f : (x: A) -> B
                    constructs A, B,
                    requires g,
                """));
    }

    @Test
    void recordFieldsStillParse() {
        // already tolerated before this change — a regression guard
        assertDoesNotThrow(() -> CstFrontend.parse("""
                module demo
                data X = {
                    a: Int,
                    b: Int,
                }
                """));
    }
}
