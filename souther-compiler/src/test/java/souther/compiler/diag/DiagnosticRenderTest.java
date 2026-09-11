package souther.compiler.diag;

import souther.compiler.cst.SourceLayout;
import souther.compiler.Compiler;

import souther.compiler.diag.msg.DeclarationMessage;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The diagnostic renderers: Elm-style human output, the JSON form, and locale selection. */
class DiagnosticRenderTest {

    private static final SourceContext SRC =
            new SourceContext("demo.sou", "module demo\nlet f (n) = null\n", SourceLayout.of("module demo\nlet f (n) = null\n"));

    /**
     * The JSON form carries the values the message is about, under the names its entry writes them
     * under.
     *
     * <p>This is the half of the interface a tool reads. The rendered sentence is for a person and
     * changes with the language it is asked in; a tool that wanted the field it names would have had
     * to find it inside that sentence. Each value is written as the text it renders as, so which
     * Java type a component happens to have stays a fact about the compiler.
     */
    @Test
    void jsonCarriesTheValuesTheMessageIsAbout() {
        Diagnostic d = Diagnostic.at(new SourcePos(2, 13))
                .say(new souther.compiler.diag.msg.DataMessage.SpreadFieldCollision(
                        "issuedAt", "Sold", "...Issued"))
                .build();
        String out = new JsonRenderer().render(d, SRC, Locale.ENGLISH);
        assertTrue(out.contains("\"values\":{\"field\":\"issuedAt\",\"from\":\"Sold\","
                + "\"heldBy\":\"...Issued\"}"), out);
    }

    /** A wrapped text carries no values object rather than an empty one: it is not a message. */
    @Test
    void jsonCarriesNoValuesWhereThereIsNoMessage() {
        Diagnostic d = Diagnostic.literal(new SourcePos(2, 13), "the compiler was handed this");
        assertFalse(new JsonRenderer().render(d, SRC, Locale.ENGLISH).contains("\"values\""));
    }

    /** The names are the message's, so they are the same in every language the sentence is asked in. */
    @Test
    void theValuesAreNamedTheSameInEveryLanguage() {
        Diagnostic d = Diagnostic.at(new SourcePos(2, 13))
                .say(new souther.compiler.diag.msg.DataMessage.SpreadFieldCollision(
                        "issuedAt", "Sold", "...Issued"))
                .build();
        String english = new JsonRenderer().render(d, SRC, Locale.ENGLISH);
        String japanese = new JsonRenderer().render(d, SRC, Locale.JAPANESE);
        assertTrue(japanese.contains("\"values\":{\"field\":\"issuedAt\""), japanese);
        assertFalse(english.equals(japanese), "the sentence differs; the names do not");
    }

    @Test
    void humanRendererQuotesTheLineAndUnderlinesTheToken() {
        Diagnostic d = Diagnostic.say(new DeclarationMessage.NullIsNotPartOfTheLanguage())
                .at(new SourcePos(2, 13), 4)
                .build();
        String out = new HumanRenderer(false).render(d, SRC, Locale.ENGLISH);
        assertTrue(out.contains("E1301"), out);
        assertTrue(out.contains("2| let f (n) = null"), out);
        assertTrue(out.contains("^^^^"), out);              // token-width underline (null = 4)
    }

    @Test
    void titleFollowsTheLocale() {
        Diagnostic d = Diagnostic.say(new DeclarationMessage.NullIsNotPartOfTheLanguage())
                .at(new SourcePos(2, 13)).build();
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
        Diagnostic d = Diagnostic.say(new DeclarationMessage.NullIsNotPartOfTheLanguage())
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
        assertInstanceOf(Primary.InSource.class, d.primary(),
                "a report from a compile of a source points into that source");
        String json = new JsonRenderer().render(d, null, Locale.JAPANESE);
        assertTrue(json.contains("\"code\":\"E1301\""), json);
    }
}
