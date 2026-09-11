package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E1005 names one rule — a construction gives every field the type declares a value — and a
 * construction can break it with a spread or without one. The code and the header say the rule, so
 * both breaks look up the same section; the hint is the only part that says which way it was broken.
 * A construction that simply omits a field used to be headed {@code MISSING FIELD IN SPREAD} and
 * hinted that a spread had not supplied it, sending the reader after a construct they had not
 * written.
 */
class AMissingFieldIsNotBlamedOnASpreadThatWasNotWrittenTest {

    /** The header every break carries: the rule, not one way of breaking it. A locale translates it
     *  and does not narrow it, so both bundles are held to the same contract. */
    private static final String HEADER = "MISSING FIELD IN CONSTRUCTION";
    private static final String HEADER_JA = "構築でのフィールド欠落";

    private static final String OMITTED = """
            module demo

            data Loan = {
                bookId: String,
                returnedOn: Date?
            }

            let plain = Loan { bookId = "b-1" }
            """;

    private static final String FROM_A_DATA = """
            module demo

            data Base = { bookId: String }

            data Loan = {
                bookId: String,
                returnedOn: Date?
            }

            let make (b: Base) = Loan { ...b }
            """;

    private static final String FROM_A_SUM = """
            module demo

            data Common = { id: String }
            data Draft = { ...Common }
            data Sent = { ...Common, at: String }
            data Doc = Draft | Sent
            data Out = { ...Common, tag: String }

            behavior run : (d: Doc) -> Out constructs Out

            let run (d) = Out { ...d }
            """;

    private static Diagnostic diagnosticOf(String src) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        return e.diagnostic();
    }

    private static String rendered(String src, Locale locale) {
        return new HumanRenderer(false)
                .render(diagnosticOf(src), new SourceContext("demo.sou", src, SourceLayout.of(src)), locale);
    }

    @Test
    void aConstructionThatOmitsAFieldIsNotToldAboutASpread() {
        assertEquals("E1005", diagnosticOf(OMITTED).code());

        String en = rendered(OMITTED, Locale.ENGLISH);
        assertTrue(en.contains(HEADER), en);
        assertTrue(en.contains("`returnedOn`"), en);
        // the whole report, not the hint alone: a banner naming a spread misdirects just as far
        assertFalse(en.toLowerCase(Locale.ROOT).contains("spread"), en);

        String ja = rendered(OMITTED, Locale.JAPANESE);
        assertTrue(ja.contains(HEADER_JA), ja);
        assertTrue(ja.contains("`returnedOn`"), ja);
        assertFalse(ja.contains("スプレッド"), ja);
    }

    @Test
    void aSpreadThatDoesNotSupplyTheFieldIsStillTheOneNamed() {
        assertEquals("E1005", diagnosticOf(FROM_A_DATA).code());

        String en = rendered(FROM_A_DATA, Locale.ENGLISH);
        assertTrue(en.contains(HEADER), en);
        assertTrue(en.contains("the spread does not provide it"), en);

        String ja = rendered(FROM_A_DATA, Locale.JAPANESE);
        assertTrue(ja.contains(HEADER_JA), ja);
        assertTrue(ja.contains("スプレッド"), ja);
    }

    @Test
    void aSpreadOfASumStillNamesTheSumWhoseSharedPartLacksTheField() {
        assertEquals("E1005", diagnosticOf(FROM_A_SUM).code());

        String en = rendered(FROM_A_SUM, Locale.ENGLISH);
        assertTrue(en.contains(HEADER), en);
        assertTrue(en.contains("`Doc`"), en);
        assertTrue(en.contains("`match`"), en);

        String ja = rendered(FROM_A_SUM, Locale.JAPANESE);
        assertTrue(ja.contains(HEADER_JA), ja);
        assertTrue(ja.contains("`match`"), ja);
    }

    @Test
    void oneRuleBrokenTwoWaysReadsAsOneRule() {
        String omitted = rendered(OMITTED, Locale.ENGLISH);
        String spread = rendered(FROM_A_DATA, Locale.ENGLISH);

        // same code, same header, and the same sentence for what went wrong
        assertEquals(diagnosticOf(OMITTED).code(), diagnosticOf(FROM_A_DATA).code());
        assertTrue(omitted.contains(HEADER) && spread.contains(HEADER), omitted + spread);
        assertTrue(omitted.contains("is missing field `returnedOn`"), omitted);
        assertTrue(spread.contains("is missing field `returnedOn`"), spread);

        String omittedJa = rendered(OMITTED, Locale.JAPANESE);
        String spreadJa = rendered(FROM_A_DATA, Locale.JAPANESE);
        assertTrue(omittedJa.contains(HEADER_JA) && spreadJa.contains(HEADER_JA),
                omittedJa + spreadJa);
    }
}
