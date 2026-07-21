package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

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
            data Flag = Bool

            behavior marks : (f: Flag) -> List<Mark> constructs Mark

            let marks (f) = [Mark | f.value]
            """;

    private List<?> marks(boolean on) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object flag = Codecs.decoded(loader, "demo.Flag", on);   // Flag is a single-Bool newtype: bare bool
        return (List<?>) Codecs.apply(loader.loadClass("demo.Marks" + "$Impl")
                .getConstructor().newInstance(), flag);
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
        // constructing the unit `Mark` (a bare name) counts: `Note` is declared but `Mark` is also
        // built, so the undeclared `Mark` is E1002 (a declared `constructs` must list every build).
        String src = """
                module demo
                data Mark
                data Note
                data Flag = Bool
                behavior marks : (f: Flag) -> Mark | Note constructs Note
                let marks (f) = if f.value then Mark else Note
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }
}
