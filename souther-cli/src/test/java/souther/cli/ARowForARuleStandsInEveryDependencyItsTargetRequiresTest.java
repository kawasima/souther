package souther.cli;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row offered for a rule the body decides by what a dependency answered says what that dependency
 * answers.
 *
 * <p>Three things at once, and they are three because each fails on its own. The rule has to be
 * settled at all — which takes running a row, which takes standing the dependency in; the value has
 * to be one that takes <em>that</em> rule rather than the one beside it; and the row has to go out
 * saying what it stood in with, or a person pastes a row nothing applies.
 *
 * <p>The last is true of every dependency the target requires and not only of the ones the decision
 * turns on. A behavior that calls one and decides on nothing it says still cannot be applied
 * without it, and a block that supplied only what the way asked about would offer rows that cannot
 * be run.
 */
class ARowForARuleStandsInEveryDependencyItsTargetRequiresTest {

    private static final String TYPES = """
            module example.stood

            data Customer = { id: Int }
            data Found
            data Missing
            data Sighting = Found | Missing
            data Yes
            data No
            data Answer = Yes | No
            """;

    /** A body forking on what a dependency answered: each arm is a rule, and a row for one says
     *  which case the dependency answers with. */
    private static final String FORKED = TYPES + """

            behavior lookup : (id: Int) -> Sighting

            behavior decides : (id: Int) -> Answer
                depends on lookup
            let decides (id, lookup) = match lookup(id) with
                | Found -> Yes
                | Missing -> No

            example decides
                | "found" : (1) with lookup = Found -> Yes
            """;

    /** A body comparing what a dependency answered: the two sides of the comparison are two rules,
     *  and a row for either pins the answer to a number on its side. */
    private static final String COMPARED = TYPES + """

            behavior riskScore : (c: Customer) -> Int

            behavior decides : (c: Customer) -> Answer
                depends on riskScore
            let decides (c, riskScore) = if riskScore(c) >= 700 then Yes else No
            """;

    /** A body that requires two dependencies and decides by one of them. */
    private static final String BESIDE = TYPES + """

            data Mark = { n: Int }
            data Verdict = { answer: Answer, mark: Mark }

            behavior lookup : (id: Int) -> Sighting
            behavior marking : (id: Int) -> Mark

            behavior decides : (id: Int) -> Verdict
                depends on lookup, marking
            let decides (id, lookup, marking) = match lookup(id) with
                | Found -> Verdict { answer = Yes, mark = marking(id) }
                | Missing -> Verdict { answer = No, mark = marking(id) }

            example decides
                | "found" : (1) with lookup = Found, marking = Mark { n = 1 }
                    -> Verdict { answer = Yes, mark = Mark { n = 1 } }
            """;

    /**
     * The arm no row goes through is offered a row that answers the dependency with the case that
     * arm is reached by.
     *
     * <p>Both halves. That the block says {@code Missing} is what makes it a row for the rule left
     * open; that it says anything at all is what makes it a row anybody can run.
     */
    @Test
    void aRowForAnArmOfWhatADependencyAnsweredSaysWhichCaseItAnswers() {
        String block = generated(FORKED);

        assertTrue(block.contains("with lookup = Missing"),
                () -> "the row stands the dependency in at the case the open rule is reached by: "
                        + block);
        assertFalse(block.contains("(1) with lookup = Found"),
                () -> "and the rule the written row already takes is not offered again: " + block);
    }

    /**
     * A comparison over what a dependency answered is two rules, and the two rows sit in one block
     * with opposite answers.
     *
     * <p>Which is what makes a row's own {@code with} the right form for this. A table beside the
     * block holds one answer at one key, so two rows wanting opposite answers of one dependency
     * could not both be written against it.
     */
    @Test
    void twoRulesOverOneComparisonAreTwoRowsWithOppositeAnswers() {
        String block = generated(COMPARED);

        assertTrue(block.contains("with riskScore = 700"),
                () -> "one row answers on the side the comparison holds: " + block);
        assertTrue(block.contains("with riskScore = 699"),
                () -> "and one on the side it does not: " + block);
        assertEquals(2, block.lines().filter(each -> each.contains("with riskScore")).count(),
                () -> "both in one block, which is where a row's own answer lets them sit: "
                        + block);
    }

    /**
     * A dependency the decision never reads is still stood in.
     *
     * <p>Supplying one is not something the account is owed — no column of the table is about it —
     * and it is something a row cannot be run without. Left out, the row a person pastes reports a
     * stand-in missing.
     */
    @Test
    void aDependencyTheDecisionNeverReadsIsStoodInAllTheSame() {
        String block = generated(BESIDE);

        assertTrue(block.contains("with lookup = Missing"),
                () -> "the row is for the rule the dependency's other case leaves open: " + block);
        assertTrue(block.contains("marking = Mark { n = 0 }"),
                () -> "and the dependency the decision reads nothing of is answered too: " + block);
    }

    /** A body asking one dependency about two calls it can tell apart. */
    private static final String TWO_CALLS = TYPES + """

            behavior lookup : (id: Int) -> Sighting

            behavior decides : (id: Int) -> Answer
                depends on lookup
            let decides (id, lookup) =
                if id > 5 then
                    match lookup(id) with
                        | Found -> match lookup(0) with
                            | Found -> Yes
                            | Missing -> No
                        | Missing -> No
                else No
            """;

    /**
     * A row that needs one dependency to answer two calls differently is written with a table, and
     * the table is keyed on what the row applied it to.
     *
     * <p>Which is what the asking's own identity is for. Folded to one answer per dependency, the
     * row would have nothing left to key a table on by the time anything wrote one — and the block
     * would have to choose between a row that answers the wrong call and saying the way cannot be
     * composed for, neither of which is so.
     */
    @Test
    void aRowNeedingTwoAnswersAtTwoCallsIsWrittenWithATableKeyedOnThem() {
        String block = generated(TWO_CALLS);

        assertTrue(block.contains("fake lookup"),
                () -> "the block writes a table for the dependency: " + block);
        assertTrue(block.contains("| (6) -> Found"),
                () -> "keyed on the call the row writes at the position: " + block);
        assertTrue(block.contains("| (0) -> Missing"),
                () -> "and on the call the body writes the number at: " + block);
        // The row that reads the table carries no clause of its own: a `with` answers every call
        // the row makes, which is the one thing this row must not do.
        assertTrue(block.contains("| (6)                                   -> <?>")
                        || block.lines().anyMatch(each -> each.trim().equals("| (6) -> <?>")),
                () -> "and the row that needs it writes no `with` of its own: " + block);
    }

    private static String generated(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return GeneratedRows.of(compilation, compilation.modules().get(0), "decides", true,
                SourceNameResolver.identity()).text();
    }
}
