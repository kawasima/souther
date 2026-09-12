package souther.cli;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row for a rule is composed under what that rule asks of the dependency, and a row for something
 * that asks nothing under what the behavior answers generally.
 *
 * <p>Two things being searched for of one behavior, in two environments, and one composer. What a
 * row stands a dependency in with belongs to the request rather than to the thing making the
 * attempt: a point of a line asks nothing of an answer, and a rule of the decision asks the case
 * the body read.
 *
 * <p>Which the block shows as two answers in one column. An answer that is a union of cases is
 * composed as a value of one of them — the case a way names, or the first there is where none is
 * named — so the rows of the two kinds stand the dependency in differently, and both are right.
 *
 * <p>What this holds is that the environment reaches the composer. Fixed when the search was set
 * up, every row of the behavior would carry the generic answer; and where a way's own answer
 * composes and the generic one does not, the way would be refused for a reason belonging to a
 * question nobody asked.
 */
class ARowForARuleIsComposedUnderWhatThatRuleAsksTest {

    /**
     * A dependency answering with either of two cases, under a guard that leaves a way open.
     *
     * <p>The guard is what makes the two kinds of row appear together: its border is owed a point,
     * which asks nothing of the dependency, and the arms behind it are rules, which ask for a case
     * apiece.
     */
    private static final String EITHER = """
            module example.stood

            data Yes
            data No
            data Answer = Yes | No

            data Blocked = Int
            data Cleared

            behavior lookup : (id: Int) -> Blocked | Cleared

            behavior decides : (id: Int) -> Answer
                depends on lookup
            let decides (id, lookup) =
                if id > 5 then
                    match lookup(id) with
                        | Blocked -> No
                        | Cleared -> Yes
                else No

            example decides
                | "small" : (0) with lookup = Cleared -> No
            """;

    @Test
    void aRuleStandsTheDependencyInAtTheCaseItReadsAndAPointAtWhateverAnswers() {
        String block = generated(EITHER);

        assertTrue(block.contains("with lookup = Cleared"),
                () -> "the rule that reads a case is answered with that case: " + block);
        assertTrue(block.contains("with lookup = Blocked"),
                () -> "and what asks nothing of the answer takes the case that answers"
                        + " generally: " + block);
    }

    private static String generated(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        if (compilation.modules().isEmpty()) {
            throw new IllegalStateException("the model under test compiles");
        }
        return GeneratedRows.of(compilation, compilation.modules().get(0), "decides",
                SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();
    }
}
