package net.unit8.souther.compiler;

import net.unit8.souther.runtime.ConstraintViolation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Generated classes carry JVM debug info (a {@code SourceFile} attribute and a
 * {@code LineNumberTable}) derived from the {@code SourcePos} every {@code Core} node keeps, so a
 * runtime stack trace — most importantly an invariant abort ({@code ConstraintViolation}) — points
 * back to the {@code .sou} source line rather than "Unknown Source". The source file name is the
 * module's simple name plus {@code .sou}.
 */
class CompileSourceMapTest {

    @Test
    void anInvariantAbortStackTraceNamesTheSouSourceAndLine() throws Exception {
        // 金額(x - 100) sits on line 5 of the module; make(50) builds 金額(-50), which aborts there.
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                behavior make : (x: Int) -> 金額 constructs 金額
                let make (x) = 金額(x - 100)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object impl = loader.loadClass("demo.Make$Impl").getConstructor().newInstance();

        ConstraintViolation v = assertThrows(ConstraintViolation.class, () -> Codecs.apply(impl, 50L));

        StackTraceElement frame = Arrays.stream(v.getStackTrace())
                .filter(f -> f.getClassName().startsWith("demo."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated frame: " + Arrays.toString(v.getStackTrace())));
        assertEquals("demo.sou", frame.getFileName(), "the stack frame names the .sou source file");
        assertEquals(5, frame.getLineNumber(), "the stack frame points to the construction's line");
    }
}
