package souther.compiler.diag;

import souther.compiler.WhereItSits;
import souther.compiler.Compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A report about an expression underlines the expression, not a per-kind guess at how wide it is.
 *
 * <p>Where a node is anchored and how far it reaches are two questions. An anchor may sit anywhere
 * the node is best complained at — a binary operation at its operator, a field read at its field —
 * so a region built from the anchor and a measured width answers neither: it starts in the middle of
 * what was written and stops wherever the measurement happened to land.
 *
 * <p>The measurement is the second half of it. A width read off a decoded value is a claim about the
 * value and not about the file, and the two part company wherever the source spells something the
 * value does not keep: an escape, a decomposed spelling, a leading zero.
 *
 * <p>So every case here cuts the region out of the source and compares the characters. An assertion
 * on the number of columns would pass on a region of the right size in the wrong place.
 */
class AnExpressionIsUnderlinedOverWhatWasWrittenTest {

    /** A hiragana ka followed by a combining voiced sound mark — two UTF-16 units, one glyph. */
    private static final String NFD = of(0x304b, 0x3099);

    private static String of(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    /**
     * An application reaches past its callee. The arguments are as much of the expression whose type
     * did not fit as the name is, and a region taken from the callee's name stops before them.
     */
    @Test
    void anApplicationIsUnderlinedOverItsArgumentsAsWell() {
        String source = """
                module demo

                behavior f : (a: String, b: String) -> String

                let f (a, b) =
                    String.append(a, String.length(b))
                """;

        assertEquals("String.length(b)", underlined(source, primary(source)));
    }

    /**
     * A binary operation is anchored at its operator, which is not where it begins. Nothing measured
     * from the anchor can reach the left operand, however wide the measurement.
     */
    @Test
    void aBinaryOperationIsUnderlinedFromItsLeftOperand() {
        String source = """
                module demo

                behavior h : (a: Int, b: String) -> Int

                let h (a, b) =
                    if a > 0 then a + 100 else b
                """;

        assertEquals("a + 100", underlined(source, secondary(source, 0)));
    }

    /** A field read is anchored at its field and written from its target. */
    @Test
    void aFieldReadIsUnderlinedOverItsTargetAsWell() {
        String source = """
                module demo

                data Counter = { count: Int }

                behavior r : (a: String, c: Counter) -> String

                let r (a, c) =
                    String.append(a, c.count)
                """;

        assertEquals("c.count", underlined(source, primary(source)));
    }

    /** A kind the width table had no case for was one column, which was most of the language. */
    @Test
    void aListIsUnderlinedOverTheWholeList() {
        String source = """
                module demo

                behavior k : (a: Int, b: String) -> String

                let k (a, b) =
                    String.append(b, [a, a, a])
                """;

        assertEquals("[a, a, a]", underlined(source, primary(source)));
    }

    /**
     * A string literal is written with escapes and read as the characters they stand for, so its
     * source is longer than its value by however many the author wrote.
     */
    @Test
    void aStringLiteralIsUnderlinedOverWhatWasTypedAndNotOverWhatItDecodesTo() {
        String source = """
                module demo

                behavior g : (x: Int) -> Date

                let g (x) =
                    Date("2026-02-30\\tx")
                """;

        assertEquals("\"2026-02-30\\tx\"", underlined(source, primary(source)));
    }

    /**
     * The same defect without an escape in sight. A literal is canonicalized to NFC as it is read, so
     * a decomposed spelling loses a unit between the file and the value — and a region measured in
     * the value stops one short of the closing quote.
     */
    @Test
    void aDecomposedStringLiteralIsUnderlinedOverTheSpellingAndNotTheCanonicalForm() {
        String source = """
                module demo

                behavior p : (x: Int) -> Date

                let p (x) =
                    Date("2026-01-01%s")
                """.formatted(NFD);

        assertEquals("\"2026-01-01" + NFD + "\"", underlined(source, primary(source)));
    }

    /** An integer is written with the zeros the author typed and read as the number they spell. */
    @Test
    void anIntegerLiteralIsUnderlinedOverItsLeadingZeros() {
        String source = """
                module demo

                behavior n : (a: String) -> String

                let n (a) =
                    String.append(a, 007)
                """;

        assertEquals("007", underlined(source, primary(source)));
    }

    /**
     * Parentheses are dropped from the tree and not from the file. What the author wrote as the
     * argument is the whole of {@code (a + 100)}, and a region that stops inside it points at
     * something the reader has to work out is the same expression.
     */
    @Test
    void aParenthesizedExpressionIsUnderlinedOverItsParentheses() {
        String source = """
                module demo

                behavior q : (a: Int, b: String) -> String

                let q (a, b) =
                    String.append(b, (a + 100))
                """;

        assertEquals("(a + 100)", underlined(source, primary(source)));
    }

    /**
     * A name in parentheses is one expression written over more characters than the name is, so the
     * two places it holds are two answers and not one.
     *
     * <p>The kind most likely to lose them. Every other kind carries its extent as a component of its
     * own, and a name carries where it is written already — which is what a rewrite that answers what
     * the name means reaches for when it rebuilds one. Resolution answers every name in a body, so a
     * name that took its extent from its spelling would lose the brackets on the way through.
     */
    @Test
    void aParenthesizedNameIsUnderlinedOverItsParentheses() {
        String source = """
                module demo

                behavior s : (a: Int, b: String) -> String

                let s (a, b) =
                    String.append(b, (a))
                """;

        assertEquals("(a)", underlined(source, primary(source)));
    }

    /**
     * An expression written over several lines has both ends and says so. What a terminal draws under
     * one of these is the renderer's, and it draws one caret today; what the region says is the
     * compiler's, and a published one is read by an editor that can mark every line of it.
     */
    @Test
    void anExpressionWrittenOverSeveralLinesReachesTheLineItEndsOn() {
        String source = """
                module demo

                behavior m : (a: String, x: Int) -> String

                let m (a, x) =
                    String.append(a,
                        if x > 0
                        then 1
                        else 2)
                """;

        Region region = primary(source);
        assertEquals("""
                if x > 0
                        then 1
                        else 2""", underlined(source, region));
        assertEquals(7, WhereItSits.in(source, region).start().line());
        assertEquals(9, WhereItSits.in(source, region).end().line());
    }

    /** The primary region of the one report compiling {@code source} produces. */
    private static Region primary(String source) {
        return ((Primary.InSource) only(source).primary()).place().region();
    }

    /** The {@code n}th secondary region of the one report compiling {@code source} produces. */
    private static Region secondary(String source, int n) {
        List<LabeledRegion> labels = only(source).secondary();
        return ((souther.compiler.diag.DiagnosticPlace.InSource) labels.get(n).place()).region();
    }

    private static Diagnostic only(String source) {
        CompileException thrown =
                assertThrows(CompileException.class, () -> Compiler.compile(source));
        List<Diagnostic> all = thrown.diagnostics();
        assertEquals(1, all.size(), "one mistake, one report: " + all);
        return all.get(0);
    }

    /** The characters {@code region} covers, cut out of the source it was read from. */
    private static String underlined(String source, Region region) {
        List<String> lines = List.of(source.split("\n", -1));
        SourcePos start = region.start();
        SourcePos end = region.end();
        if (WhereItSits.in(source, start).line() == WhereItSits.in(source, end).line()) {
            return lines.get(WhereItSits.in(source, start).line() - 1).substring(WhereItSits.in(source, start).column() - 1, WhereItSits.in(source, end).column() - 1);
        }
        StringBuilder out = new StringBuilder(lines.get(WhereItSits.in(source, start).line() - 1).substring(WhereItSits.in(source, start).column() - 1));
        for (int line = WhereItSits.in(source, start).line() + 1; line < WhereItSits.in(source, end).line(); line++) {
            out.append('\n').append(lines.get(line - 1));
        }
        return out.append('\n').append(lines.get(WhereItSits.in(source, end).line() - 1), 0, WhereItSits.in(source, end).column() - 1).toString();
    }
}
