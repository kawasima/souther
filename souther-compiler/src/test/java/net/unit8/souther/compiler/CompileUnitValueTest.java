package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A unit data is constructed by writing its name bare — the functional-language idiom for a
 * nullary constructor (spec 8.4). It still needs {@code constructs} (spec 2.1, 12.3).
 */
class CompileUnitValueTest {

    private static final String MODULE = """
            module demo

            data Mark
            data Flag = { on: Bool }

            behavior marks = (f: Flag) -> List<Mark> constructs Mark

            fn marks (f) = [Mark | f.on]
            """;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<?> marks(boolean on) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.Flag").getMethod("decoder").invoke(null);
        Object flag = ((Ok) d.decode(on, Path.ROOT)).value();   // Flag is a single-Bool newtype: bare bool
        return (List<?>) ((Behavior<Object, Object>) loader.loadClass("demo.marks")
                .getConstructor().newInstance()).apply(flag);
    }

    @Test
    void bareUnitNameConstructsTheUnit() throws Exception {
        List<?> present = marks(true);
        assertEquals(1, present.size());
        assertEquals("demo.Mark", present.get(0).getClass().getName());

        assertEquals(0, marks(false).size());
    }

    @Test
    void constructingAUnitStillNeedsConstructs() {
        String src = """
                module demo
                data Mark
                data Flag = { on: Bool }
                behavior marks = (f: Flag) -> List<Mark>
                fn marks (f) = [Mark | f.on]
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }
}
