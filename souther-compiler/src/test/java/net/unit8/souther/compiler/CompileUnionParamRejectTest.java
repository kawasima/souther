package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An anonymous union appears only in a behavior's output; a parameter type is always a single
 * named type, a named sum included (spec 8.6, 12.2). Writing {@code (x: A | B)} in a parameter is
 * rejected — declare {@code data AB = A | B} and take {@code (x: AB)}, opening it with {@code match}.
 */
class CompileUnionParamRejectTest {

    @Test
    void anAnonymousUnionParameterIsRejected() {
        String src = """
                module demo
                data Sub = Int
                data Pre = Int
                data Done = Int
                behavior finish : (app: Sub | Pre) -> Done constructs Done
                let finish (app) = match app with
                    | Sub as s -> Done { value: s.value }
                    | Pre as p -> Done { value: p.value }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("app"), e.getMessage());
    }

    @Test
    void aNamedSumParameterIsAccepted() {
        String src = """
                module demo
                data Sub = Int
                data Pre = Int
                data SubPre = Sub | Pre
                data Done = Int
                behavior finish : (app: SubPre) -> Done constructs Done
                let finish (app) = match app with
                    | Sub as s -> Done { value: s.value }
                    | Pre as p -> Done { value: p.value }
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.Finish"),
                "a named sum parameter is the sanctioned form");
    }
}
