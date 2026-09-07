package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Prepared;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.StatedContract;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A line a behavior's clause draws is named by the part its author wrote and by which of that
 * part's statements drew it.
 *
 * <p>A clause is decomposed twice and the two are counted in different things. The author joins the
 * parts, so which part a line came out of is a fact about what they wrote; what one part states is
 * read off the tree it expanded into, where a helper's body brings connectives nobody wrote, so
 * which statement a line came out of is a fact about that part alone.
 *
 * <p><b>Which is why the second is counted within the part.</b> Counted over the clause instead —
 * one number running on from part to part, which is what the reading of lines kept for itself — the
 * name of a line in one part moves when a part before it comes to state one thing more. Nothing the
 * author wrote about that line changed, and a consumer holding the old name finds a different line
 * or none.
 *
 * <p>The model below is written so the two countings disagree: the first part is one call, and it
 * states two things once the helper it names is expanded into it. A line in the second part is the
 * third statement of the clause and the first of its own part.
 */
class ALineOfAClauseIsNamedWithinThePartThatDrewItTest {

    /**
     * A clause whose first part states two things and whose second states one.
     *
     * <p>The two are told apart by what the author wrote and not by how far anything read: the
     * helper is what brings the second statement into the first part, and the author wrote one call
     * there.
     */
    private static final String A_HELPER_IN_THE_FIRST_PART = """
            module g

            data N = Int
            data Todo = { id: N }
            data NotFound = { asked: N }

            let bothPositive (x: N, y: N): Bool = x.value > 0 && y.value > 0

            behavior findTodo : (a: N, b: N, c: N) -> Todo | NotFound
                ensures ok = NotFound -> bothPositive(a, b) && c.value > 5
            """;

    /** The lines one behavior's clauses draw, through the readings a report is built from. */
    private static EnsuresThresholds.Clauses drawn(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, StatedContract> stated =
                compilation.db().ask(new Bodies.StatedContracts(module)).value();
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior);
        assertEquals(1, prepared.behaviors().stream()
                        .filter(each -> each.name().equals(behavior)).count(),
                "the behavior under test is declared");
        return EnsuresThresholds.of(stated == null ? null : stated.get(behavior), inputs, rules);
    }

    /** Each line, as the position it is on and the name the two decompositions gave it. */
    private static List<String> named(EnsuresThresholds.Clauses clauses) {
        return clauses.thresholds().stream().map(each -> {
            WhichLine.OfAComparisonOfAPart which =
                    ((LineOrigin.EnsuresOrigin) each.origin()).which();
            return each.path() + " = part " + which.statement().part().ordinal()
                    + ", statement " + which.statement().ordinal();
        }).sorted().toList();
    }

    /**
     * Every line is named within its own part.
     *
     * <p>The two the helper brought are the first part's and are told apart there; the one written
     * beside the call is the second part's first statement, and not the clause's third.
     */
    @Test
    void aStatementIsNumberedWithinItsPartAndNotAcrossTheClause() {
        assertEquals(
                List.of("a = part 0, statement 0",
                        "b = part 0, statement 1",
                        "c = part 1, statement 0"),
                named(drawn(A_HELPER_IN_THE_FIRST_PART, "findTodo")),
                "a line is named by the part its author wrote and by which of that part's"
                        + " statements drew it");
    }

    /**
     * And the line in the second part keeps its name when the first part states one thing less.
     *
     * <p>The same clause with the helper's second comparison taken out, so the first part states
     * one thing where it stated two. What the author wrote about {@code c} did not change, and its
     * line is the same line — so it is named the same. A count running over the whole clause cannot
     * say that: one statement fewer before it moves it from the third to the second, and a consumer
     * holding the name it had finds another line under it.
     */
    @Test
    void aLineKeepsItsNameWhenThePartBeforeItStatesOneThingLess() {
        String fewer = A_HELPER_IN_THE_FIRST_PART
                .replace("(x: N, y: N)", "(x: N)")
                .replace("= x.value > 0 && y.value > 0", "= x.value > 0")
                .replace("bothPositive(a, b)", "bothPositive(a)");

        assertEquals(List.of("a = part 0, statement 0", "c = part 1, statement 0"),
                named(drawn(fewer, "findTodo")),
                "what names the line is which part drew it and where it stands in that part,"
                        + " neither of which is about the part beside it");
    }
}
