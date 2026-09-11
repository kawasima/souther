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
 * An expansion refuses an argument at the argument, not at the call that carried it.
 *
 * <p>What holds an application to the signature it instantiated is the one reader that sees every
 * position of one variable at once, so it is the only one that can refuse two readings that
 * disagree. It was given the two types and a position of the form it was reading, and answered
 * there. The argument is what supplied the type it refused, and the caller reading it still has the
 * argument.
 */
class AnExpansionRefusesAnArgumentWhereItIsWrittenTest {

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

    @Test
    void anArgumentHeldToWhatTheBindingDeclaresIsUnderlinedWhole() {
        String source = """
                module demo

                let twice (n: Int): Int = n + n

                behavior notice : (a: String, b: String) -> Int

                let notice (a, b) =
                    twice(
                        String.append(a, b)
                    )
                """;

        assertEquals("String.append(a, b)", underlined(source, regionOf(source)),
                "the argument is `String.append(a, b)`, so the caret covers it rather than its "
                        + "first character");
    }
}
