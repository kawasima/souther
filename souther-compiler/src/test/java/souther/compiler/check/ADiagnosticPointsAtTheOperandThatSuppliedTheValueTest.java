package souther.compiler.check;

import souther.compiler.WhereItSits;
import souther.compiler.diag.Primary;

import souther.compiler.Compiler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Region;

/**
 * A report about a value that did not fit points at the value, not at the form that asked for it.
 *
 * <p>The message names the operand in words — {@code argument 2 of String.append}, the text a
 * temporal was written as — and a reader who has the message still has to find the operand. Where
 * the caret sits under the form, that search crosses lines: an argument list has no bound on how far
 * its arguments are written from its callee, so the two can be a page apart. What the reader cannot
 * recover from the message is the operand, and that is what the caret is for.
 *
 * <p>The operand is the whole of what was written there, arguments and all. An argument that is
 * itself a call did not fit as the call it is, and a caret stopping at its callee's name leaves the
 * reader to work out that the rest of it is the same expression.
 *
 * <p>Which region a report gets is not decided by its code. The rows below cover the three paths a
 * call written in a body is typed by — a kernel applied against its declared signature, a helper
 * expanded, a behavior called by name — which answered three different ways for one code and one
 * kind of failure. They are the paths that exist now; a fourth is not held to anything here.
 */
class ADiagnosticPointsAtTheOperandThatSuppliedTheValueTest {

    /** The text {@code region} underlines, cut out of {@code source}. */
    private static String underlined(String source, Region region) {
        String line = source.split("\n", -1)[WhereItSits.in(source, region).start().line() - 1];
        int from = WhereItSits.in(source, region).start().column() - 1;
        int to = WhereItSits.in(source, region).end().line() == WhereItSits.in(source, region).start().line()
                ? WhereItSits.in(source, region).end().column() - 1 : line.length();
        return line.substring(Math.min(from, line.length()), Math.min(to, line.length()));
    }

    private static Region regionOf(String source) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(source));
        return ((Primary.InSource) e.diagnostic().primary()).place().region();
    }

    /** The argument is written three lines below the callee, so nothing but the argument's own
     *  position puts the caret on a line the reader has to look at. */
    @Test
    void aKernelsArgumentIsUnderlinedWhereItIsWritten() {
        String source = """
                module demo

                behavior notice : (a: String, b: String) -> String

                let notice (a, b) =
                    String.append(
                        a,
                        String.length(b)
                    )
                """;

        assertEquals("String.length(b)", underlined(source, regionOf(source)),
                "argument 2 is `String.length(b)` on line 8, not the callee on line 6");
    }

    /**
     * A block is typed against what the value arguments settled, so a value argument that does not
     * fit is refused before the block is reached.
     *
     * <p>Otherwise the block is checked against a parameter type worked out from a type the call
     * does not have, and what it reports is something wrong inside the block — which is a second
     * wrong place to send the reader, and one the message does not even name the argument in.
     */
    @Test
    void aValueArgumentIsRefusedBeforeTheBlockThatWasTypedFromIt() {
        String source = """
                module demo

                behavior sorted : (n: Int) -> List<Int>

                let sorted (n) =
                    List.sortBy(
                        x -> String.length(x),
                        n
                    )
                """;

        assertEquals("n", underlined(source, regionOf(source)),
                "`n` is what does not fit; `String.length(x)` is a block typed from it");
    }

    /** A helper is expanded rather than applied, and answers with the argument already. */
    @Test
    void anExpandedHelpersArgumentIsPointedAtWhereItIsWritten() {
        String source = """
                module demo

                let shout (s: String): String = s

                behavior notice : (a: String) -> String

                let notice (a) =
                    shout(
                        String.length(a)
                    )
                """;

        assertEquals(9, WhereItSits.in(source, regionOf(source)).start().line(),
                "the argument is on line 9 and the callee on line 8");
    }

    /** A behavior is called against what it declares, and answers with the argument already. */
    @Test
    void aCalledBehaviorsArgumentIsUnderlinedWhereItIsWritten() {
        String source = """
                module demo

                behavior shout : (s: String) -> String

                behavior notice : (x: String) -> String
                    depends on shout

                let notice (x, shout) =
                    shout(
                        String.length(x)
                    )
                """;

        assertEquals("String.length(x)", underlined(source, regionOf(source)),
                "argument 1 is `String.length(x)` on line 10, not the callee on line 9");
    }

    /** The text that would not parse is what the message quotes, so it is what the caret covers. */
    @Test
    void aTemporalThatWouldNotParseIsUnderlinedAtTheTextItWasWrittenAs() {
        String source = """
                module demo

                data Out = { on: Date }

                behavior closedOn : (n: Int) -> Out constructs Out

                let closedOn (n) = Out {
                    on = Date("2026-02-30")
                }
                """;

        assertEquals("\"2026-02-30\"", underlined(source, regionOf(source)),
                "the message quotes `2026-02-30`, so the caret covers it rather than `Date`");
    }
}
