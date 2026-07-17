package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Diagnostic codes for two rejected constructs: {@code null} (E1301) and a call to something
 *  that is not a behavior or builtin (E1401). See spec 22.8, 22.10. */
class CompileErrorCodeTest {

    @Test
    void nullIsE1301() {
        String src = """
                module demo
                data N = Int
                behavior f = (n: N) -> N constructs N
                fn f (n) = null
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1301", e.code());
    }

    @Test
    void callingSomethingThatIsNotABehaviorIsE1401() {
        String src = """
                module demo
                data N = Int
                behavior f = (n: N) -> N constructs N
                fn f (n) = someJavaMethod(n)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1401", e.code());
    }
}
