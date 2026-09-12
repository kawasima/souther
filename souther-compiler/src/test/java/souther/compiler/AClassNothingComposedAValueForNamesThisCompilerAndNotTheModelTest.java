package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class this compiler composed nothing for says what it did not manage, and not what the model
 * holds.
 *
 * <p>The two license different work. Told the position has no value in a range, an author has
 * nothing to do; told nothing here composed one, they can write the row by hand. And the first is
 * a claim about the model that the order is in no position to make: above a string a rule stops
 * short of, it declines to name a value on purpose, because choosing between the strings with that
 * one as a prefix puts a character nobody wrote into a row somebody reads.
 *
 * <p>Which is a difference a report can be caught getting wrong from the inside: the range the
 * sentence is about is one the same report writes a row in.
 */
class AClassNothingComposedAValueForNamesThisCompilerAndNotTheModelTest {

    private static String rowsOffered() {
        Compilation compilation = Compilation.ofSource("""
                module example.text

                data Code = String

                data Taken = { code: Code }
                data NotIt = { why: Int }
                data Result = Taken | NotIt

                behavior take : (code: Code) -> Result
                    constructs Taken, NotIt

                let take (code) = {
                    guard code.value <= "spring" else NotIt { why = 0 }
                    Taken { code = code }
                }

                example take
                    | "one" : (Code("spring")) -> Taken { code = Code("spring") }
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return GeneratedRows.of(compilation, "example.text", "take",
                SourceRendering.namedByIdentity(compilation.texts())).text();
    }

    /**
     * The range above the line is one the report writes a row in, and the class of it is not
     * reported as one the position holds no value in.
     */
    @Test
    void aRangeTheReportWritesARowInIsNotSaidToHoldNoValue() {
        String rows = rowsOffered();

        assertTrue(rows.contains("(Code(\"t\"))"),
                () -> "a row above `spring`, which the boundary search composes:\n" + rows);
        assertFalse(rows.contains("no value this position can hold"),
                () -> "and the class of that same range is not said to be empty of values:\n"
                        + rows);
    }

    /** And what it says instead is what nothing here managed to write. */
    @Test
    void theClassSaysWhatNothingHereComposed() {
        String rows = rowsOffered();

        assertTrue(rows.contains("no row for `code=spring < x` in `take`: nothing here writes a"
                        + " value whose value is in this range"),
                () -> "the reason is the one about composing, said of the range the class is:\n"
                        + rows);
    }
}
