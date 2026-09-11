package souther.compiler.diag;

import org.junit.jupiter.api.Test;

import souther.compiler.source.SourceId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * An error that takes on the other errors a compilation found still says what it was raised with.
 *
 * <p>A pass that words its own summary — the one reporting every failing {@code example} row — words
 * it over what it found. Rebuilding the message from the leading diagnostic would answer with a
 * sentence about one row instead, and nothing about the collecting would say so.
 */
class AnErrorKeepsTheMessageItWasRaisedWithTest {

    private static Diagnostic at(int line, String says) {
        return Diagnostic.literal(Placement.aFileOfThisCompile(new SourceId("a.sou")).at(line, 1), says);
    }

    @Test
    void theSummaryAPassWordedIsStillTheMessage() {
        CompileException raised = CompileException.ofAll(List.of(at(3, "one row"), at(4, "another")),
                "2 example rows did not hold");
        String said = raised.getMessage();

        CompileException joined = raised.alsoReporting(
                List.of(new Located(at(9, "elsewhere"), ReportContext.NONE)));

        assertEquals(said, joined.getMessage());
        assertEquals(3, joined.diagnostics().size());
        assertEquals(raised.code(), joined.code());
        assertEquals(raised.pos(), joined.pos());
    }

    @Test
    void whatItTakesOnComesAfterWhatItHad() {
        CompileException raised = CompileException.of(at(3, "first"));

        CompileException joined = raised.alsoReporting(List.of(
                new Located(at(5, "second"), ReportContext.inFile(new SourceId("b.sou"))),
                new Located(at(7, "third"), ReportContext.NONE)));

        assertEquals(List.of("first", "second", "third"),
                joined.diagnostics().stream().map(DiagnosticRenderer::legacyBody).toList());
        assertEquals(new SourceId("b.sou"), joined.sourceIdOf(1));
        assertEquals(null, joined.sourceIdOf(2), "the third named none");
    }

    @Test
    void anUnnamedSourceIsStillTaggedAfterwards() {
        CompileException joined = CompileException.of(at(3, "first"))
                .alsoReporting(List.of(new Located(at(5, "second"), ReportContext.NONE)))
                .inSource(new SourceId("a.sou"));

        assertEquals(new SourceId("a.sou"), joined.sourceIdOf(0));
        assertEquals(new SourceId("a.sou"), joined.sourceIdOf(1));
    }

    @Test
    void takingOnNothingIsTheSameError() {
        CompileException raised = CompileException.of(at(3, "first"));

        assertSame(raised, raised.alsoReporting(List.of()));
    }

}
