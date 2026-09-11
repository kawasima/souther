package souther.compiler.diag;

import souther.compiler.WhereItSits;
import souther.compiler.Compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A report about the value a function argument answered with underlines the expression that supplied
 * that value.
 *
 * <p>The value a block answers is its body's, and a block's own position is where its parameters
 * start. A report that names what the block returned and points at what takes its parameters has the
 * reader looking at one thing while being told about another — the two are on different lines as soon
 * as the block is written over more than one.
 *
 * <p>A {@code let} written above the answer holds the value it binds and not the block's answer, so
 * the bindings are stepped over. An {@code if} or a {@code match} is not: its type is the join of its
 * arms, so no one arm supplied it and the whole construct is what did.
 *
 * <p>Where the argument is a function value rather than a block, nothing at this call site made the
 * value — the expression that did is in the function's own declaration — so the argument is as far as
 * the source goes.
 */
class AResultReportUnderlinesWhatSuppliedTheValueTest {

    private static final String APPLY_TWICE = """
            module demo

            let applyTwice (f: (Int) -> Int, x: Int) =
                f(f(x))
            """;

    private static final String BAG = """
            module demo

            data Empty
            data Full = { held: Int }
            data Bag = Empty | Full
            """;

    /**
     * The declared result is concrete, so the block is checked against it directly. The answer is a
     * literal one line below the parameters that used to be underlined.
     */
    @Test
    void aBlockAnsweringAConcreteResultIsUnderlinedAtItsAnswer() {
        String source = APPLY_TWICE + """

                let used =
                    applyTwice(
                        (n) ->
                            "not an int"
                    , 1
                    )
                """;

        assertEquals("\"not an int\"", underlined(source, only(source)));
    }

    /**
     * The declared result still carries a type variable, so the block is checked by unifying against
     * it. That is the other of the two branches and it reaches the same report.
     */
    @Test
    void aBlockAnsweringAResultStillCarryingAVariableIsUnderlinedAtItsAnswer() {
        String source = """
                module demo

                let picked =
                    List.filterMap(
                        (n) ->
                            1
                    , [1, 2, 3]
                    )
                """;

        assertEquals("1", underlined(source, only(source)));
    }

    /**
     * A block written with bindings above its answer. Underlining the body would put the caret on the
     * {@code let} keyword, which holds the value it binds and not the one the block answered with.
     */
    @Test
    void aBlockWithBindingsAboveItsAnswerIsUnderlinedAtTheAnswer() {
        String source = APPLY_TWICE + """

                let used =
                    applyTwice(
                        (n) -> {
                            let m = n + 1
                            "not an int"
                        }
                    , 1
                    )
                """;

        assertEquals("\"not an int\"", underlined(source, only(source)));
    }

    /**
     * Both arms answer the refused type, and neither of them is what supplied it: the type is the
     * join, which the {@code if} has and its arms do not. Descending into one of them would name an
     * arm the report is not about.
     */
    @Test
    void aBlockAnsweringWithAJoinIsUnderlinedAtTheConstructThatJoins() {
        String source = APPLY_TWICE + """

                let used =
                    applyTwice(
                        (n) ->
                            if n > 0 then
                                "a"
                            else
                                "b"
                    , 1
                    )
                """;

        Diagnostic report = only(source);
        assertEquals("if n > 0 then", lineUnderlined(source, report).trim());
        assertEquals(1, WhereItSits.in(source,
                ((Primary.InSource) report.primary()).place().region()).sourceSpan(), "a construct is measured from its start");
    }

    /** A fold's step, written as a block, answers something the accumulator cannot hold. */
    @Test
    void aStepAnsweringAnotherTypeThanTheAccumulatorIsUnderlinedAtItsAnswer() {
        String source = BAG + """

                let summed (ms: List<Int>) =
                    List.fold(
                        (acc, m) ->
                            "not a bag"
                    , Empty
                    , ms
                    )
                """;

        assertEquals("\"not a bag\"", underlined(source, only(source)));
    }

    /** The same step with bindings above its answer. */
    @Test
    void aStepWithBindingsAboveItsAnswerIsUnderlinedAtTheAnswer() {
        String source = BAG + """

                let summed (ms: List<Int>) =
                    List.fold(
                        (acc, m) -> {
                            let doubled = m * 2
                            "not a bag"
                        }
                    , Empty
                    , ms
                    )
                """;

        assertEquals("\"not a bag\"", underlined(source, only(source)));
    }

    /**
     * A step passed as a function value. The name is eta-expanded into a block before this runs, so
     * what the check holds is a block whose body is the expansion of the callee — and the expression
     * that answered is inside that callee, at a place the author did not write this value. The report
     * stays at the argument.
     *
     * <p>This is the row that fails if the expansion is stepped over along with the bindings: it
     * would move to {@code wrong}'s own body, two definitions up.
     */
    @Test
    void aStepPassedAsAFunctionValueIsUnderlinedAtTheArgumentAndNotInsideTheCallee() {
        String source = BAG + """

                let wrong (acc: Bag, m: Int): Int =
                    m

                let summed (ms: List<Int>) =
                    List.fold(
                        wrong
                    , Empty
                    , ms
                    )
                """;

        assertEquals("wrong", lineUnderlined(source, only(source)).trim());
    }

    /** The one report compiling {@code source} produces. */
    private static Diagnostic only(String source) {
        CompileException thrown =
                assertThrows(CompileException.class, () -> Compiler.compile(source));
        List<Diagnostic> all = thrown.diagnostics();
        assertEquals(1, all.size(), "one mistake, one report: " + all);
        return all.get(0);
    }

    /** The characters of {@code source} a report's primary region covers. */
    private static String underlined(String source, Diagnostic report) {
        Region region = ((Primary.InSource) report.primary()).place().region();
        assertEquals(WhereItSits.in(source, region).start().line(),
                WhereItSits.in(source, region).end().line(),
                "an expression this test is about is one line's worth");
        return WhereItSits.underlined(source, region);
    }

    /** The whole source line a report's primary region begins on. */
    private static String lineUnderlined(String source, Diagnostic report) {
        return source.lines().toList().get(WhereItSits.in(source, ((Primary.InSource) report.primary()).place().region()).start().line() - 1);
    }
}
