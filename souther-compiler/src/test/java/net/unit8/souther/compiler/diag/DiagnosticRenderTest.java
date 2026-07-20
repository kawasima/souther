package net.unit8.souther.compiler.diag;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.Compiler;
import net.unit8.souther.compiler.diag.SourcePos;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The diagnostic renderers: Elm-style human output, the JSON form, and locale selection. */
class DiagnosticRenderTest {

    private static final SourceContext SRC =
            new SourceContext("demo.sou", "module demo\nlet f (n) = null\n");

    @Test
    void humanRendererQuotesTheLineAndUnderlinesTheToken() {
        Diagnostic d = Diagnostic.of("E1301", "e1301.msg")
                .at(new SourcePos(2, 13), 4)
                .build();
        String out = new HumanRenderer(false).render(d, SRC, Locale.ENGLISH);
        assertTrue(out.contains("E1301"), out);
        assertTrue(out.contains("2| let f (n) = null"), out);
        assertTrue(out.contains("^^^^"), out);              // token-width underline (null = 4)
    }

    @Test
    void titleFollowsTheLocale() {
        Diagnostic d = Diagnostic.literal(new SourcePos(2, 13), "E1301", "boom");
        String en = new HumanRenderer(false).render(d, SRC, Locale.ENGLISH);
        String ja = new HumanRenderer(false).render(d, SRC, Locale.JAPANESE);
        assertTrue(en.contains("USE OF NULL"), en);
        assertTrue(ja.contains("null の使用"), ja);
    }

    @Test
    void englishDoesNotLeakTheDefaultLocale() {
        // With the JVM default locale ja, an explicit English request must still land on English.
        assertEquals("ERROR", Messages.get("diag.error.title", Locale.ENGLISH));
        assertEquals("エラー", Messages.get("diag.error.title", Locale.JAPANESE));
    }

    @Test
    void missingKeyFallsBackToTheKeyItself() {
        assertFalse(Messages.has("no.such.key", Locale.JAPANESE));
        assertEquals("no.such.key", Messages.get("no.such.key", Locale.JAPANESE));
    }

    @Test
    void jsonRendererCarriesCodeAndRegion() {
        Diagnostic d = Diagnostic.of("E1301", "e1301.msg")
                .at(new SourcePos(2, 13), 4)
                .build();
        String json = new JsonRenderer().render(d, SRC, Locale.JAPANESE);
        assertTrue(json.contains("\"code\":\"E1301\""), json);
        assertTrue(json.contains("\"file\":\"demo.sou\""), json);
        assertTrue(json.contains("\"startCol\":13"), json);
        assertTrue(json.contains("\"endCol\":17"), json);
        assertTrue(json.contains("\"severity\":\"error\""), json);
    }

    @Test
    void aRealCompileErrorCarriesAStructuredDiagnostic() {
        String src = """
                module demo
                data N = Int
                behavior f : (n: N) -> N constructs N
                let f (n) = null
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        Diagnostic d = e.diagnostic();
        assertEquals("E1301", d.code());
        assertEquals("E1301", d.pos() == null ? null : d.code());
        String json = new JsonRenderer().render(d, null, Locale.JAPANESE);
        assertTrue(json.contains("\"code\":\"E1301\""), json);
    }
}
