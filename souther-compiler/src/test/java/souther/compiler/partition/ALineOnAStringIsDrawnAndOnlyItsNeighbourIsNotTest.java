package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule written over a string is measured as far as the values go, which is all of it but one row.
 *
 * <p>Four things a measure produces at a line, and only the last needs something a string has not:
 * the line itself, the classes either side of it, a row at the line, and a row just below it. The
 * first three need the order, which the language defines on a string; the fourth needs the value
 * before another one, which it does not name.
 *
 * <p>It produced none of the four. The algebra held every ordered value as one number and a string
 * has none to count to, so a position holding one was closed at the entrance and the report said the
 * comparison was against values no line could be drawn on — of values the language orders, two lines
 * below a body that compares them.
 *
 * <p>What that cost is the whole of this: a build could not refuse over it. The same rule written
 * over an {@code Int} is a gap the rows must fill, and written over a {@code String} it was a
 * position nothing was known about — so the type an author reached for decided whether the boundary
 * rule applied to them at all.
 */
class ALineOnAStringIsDrawnAndOnlyItsNeighbourIsNotTest {

    private static final String MODEL = """
            module example.month

            data YearMonth = String

            data Newer
            data Older
            data Era = Newer | Older

            behavior classifyMonth : (m: YearMonth) -> Era
            let classifyMonth (m) = {
                guard m.value < "2026-08" else Newer
                Older
            }
            """;

    /** The line, and the classes either side of it, named by the strings the model wrote. */
    @Test
    void theClassesAreTheTwoSidesOfTheLine() {
        assertEquals(List.of("m/x < 2026-08", "m/2026-08 <= x"), classes());
    }

    /** A row at the line, which is the literal the body already holds. */
    @Test
    void aRowIsOfferedAtTheLine() {
        String rows = generated();

        assertTrue(rows.contains("(YearMonth(\"2026-08\"))"), rows);
    }

    /**
     * And no row below it, which is the one thing out of reach.
     *
     * <p>Not silence about it: the class below the line is offered a row too, at the least string
     * there is. What is absent is the boundary row one step under the line — a string has no value
     * just below another that this language names, and inventing one would put a character this
     * compiler chose into a row somebody has to read.
     *
     * <p>Three rows and not two, and the third is not a boundary row. The body decides by a
     * comparison this compiler has no words for, so each way through it is a rule no row takes and
     * one of them is owed a row of its own. What this test is about is the boundary, and the count
     * is here so that a row appearing just below the line would be seen.
     */
    @Test
    void noRowIsOfferedJustBelowTheLine() {
        String rows = generated();

        assertTrue(rows.contains("(YearMonth(\"\"))"), "the class below the line has a row: " + rows);
        assertFalse(rows.contains("m = 2026-07"), rows);
        assertEquals(3, rows.lines().filter(each -> each.contains("YearMonth(")).count(), rows);
    }

    private static Compilation measured() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static List<String> classes() {
        Compilation compilation = measured();
        return compilation.db().ask(new Adequacy.Coverage(compilation.modules().get(0)))
                .value().get("classifyMonth").axes().get(0).classes();
    }

    private static String generated() {
        return souther.compiler.report.GeneratedRows.of(measured(), "example.month",
                "classifyMonth", true,
                souther.compiler.diag.SourceNameResolver.identity()).text();
    }
}
