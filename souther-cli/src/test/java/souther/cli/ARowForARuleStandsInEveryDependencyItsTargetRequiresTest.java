package souther.cli;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
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
     * A way that needs one dependency to answer two calls differently is one nothing composes a row
     * for, and the block says so.
     *
     * <p>What such a way needs is a table, which is written once for a module — it belongs to the
     * environment several rows share rather than to a row, and what a module already states about
     * that environment is not read here. A row offered with a table beside it would be a row
     * certified against an environment the module it is pasted into does not have.
     *
     * <p>So the way stays where it was: neither covered nor a gap, which is what a way this
     * compiler looked at and could not compose for is. What goes out is every row that could be
     * composed, each of them runnable on its own.
     */
    @Test
    void aWayNeedingTwoAnswersAtTwoCallsIsNotOfferedARow() {
        String block = generated(TWO_CALLS);

        assertFalse(block.contains("fake "),
                () -> "nothing here writes a table beside the rows: " + block);
        assertTrue(block.lines().filter(each -> each.trim().startsWith("| "))
                        .allMatch(each -> each.contains(" with lookup = ")),
                () -> "and every row that did go out stands the dependency in: " + block);
    }

    private static String generated(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return GeneratedRows.of(compilation, compilation.modules().get(0), "decides",
                SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();
    }
}
