package souther.compiler;

import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import souther.compiler.meta.ModulePath;
import souther.compiler.observe.Applied;
import souther.compiler.coverage.RunRecord;
import souther.compiler.observe.Counting;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Run;
import souther.compiler.observe.Stage;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An outcome records what applied the behavior for that row, and what that application is measured
 * in. Today there is one answerer — the bytecode this compile generated, applied through the loader
 * the run builds — so the answer is the same for every row a compile ran, and this holds it to that
 * while it is still true. What makes the field worth having before a second answerer exists is that
 * it is checkable now: when one arrives, the rows that name it are the ones that changed.
 *
 * <p>What the row cost is the other half of the same record and a different question. A row's
 * evaluation is not only its application: the fixtures are built first and apply whatever helpers
 * they name, so counted points are spent before the behavior is reached and by rows that never reach
 * it. The count is therefore held to the row and not to the application, and what says whether the
 * application itself was code this compile counted is the applier.
 *
 * <p>Which of the two a row is comes from the stage it reached, and the outcome refuses to be built
 * saying otherwise: the two are written from one evaluation and read apart, so a row that says it
 * applied the behavior and says nothing applied it is a state no reader should have to resolve.
 */
class ARowSaysWhatAppliedTheBehaviorTest {

    private static final String MODEL = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Draft = { cost: Amount }
            data Submitted = { cost: Amount }
            data Rejected = { reason: String }

            behavior submit : (request: Draft) -> Submitted | Rejected
                constructs Submitted, Rejected

            let submit (request) = {
                guard request.cost.value <= 100 else Rejected { reason = "over" }
                Submitted { cost = request.cost }
            }

            // Nothing runs this one, so its rows are recorded and not applied.
            behavior settle : (request: Draft) -> Submitted
                constructs Submitted
            """;

    /** Rows that ran, rows that failed on the way, and rows nothing can run. */
    private static final String ROWS = MODEL + """

            example submit
                | "under the ceiling" : (Draft { cost = Amount(50) }) -> Submitted
                | "over it" : (Draft { cost = Amount(200) }) -> Rejected
                | "the answer disagrees" : (Draft { cost = Amount(50) }) -> Rejected

            example settle
                | "waiting for a body" : (Draft { cost = Amount(50) }) -> Submitted
            """;

    /** A row whose input fixture applies a helper and then breaks the invariant it is built against. */
    private static final String SPENDS_BEFORE_IT_BREAKS = """
            module example.before

            data Amount = Int
                invariant value >= 0

            data Draft = { cost: Amount }
            data Done = { cost: Amount }

            let costOf (xs: List<Int>): Int = List.fold((sum, x) -> sum + x, 0, xs)

            behavior take : (request: Draft) -> Done
                constructs Done

            let take (request) = Done { cost = request.cost }

            example take
                | "the fixture spends what it spends and then breaks the invariant"
                    : (Draft { cost = Amount(0 - costOf([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])) })
                    -> Done { cost = Amount(0) }
            """;

    private static List<RowOutcome> rowsOf(String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        List<RowOutcome> rows = new ArrayList<>();
        for (String module : compilation.modules()) {
            for (SourceId id : compilation.exampleSourcesOf(module)) {
                Output.Examples.Of ran =
                        compilation.db().ask(Output.Examples.asked(compilation.db(), module, id)).value();
                if (ran != null) {
                    rows.addAll(ran.rows());
                }
            }
        }
        return rows;
    }

    @Test
    void everyRowThisCompileAppliedNamesTheClassesThisCompileGenerated() {
        List<RowOutcome> rows = rowsOf(ROWS);
        List<RowOutcome> applied = rows.stream().filter(row -> row.stage().reached(Stage.INVOKED)).toList();

        assertEquals(4, rows.size(), "three rows of a behavior with a body and one without");
        assertEquals(3, applied.size(), "the three with a body to run are the ones applied: " + rows);
        for (RowOutcome row : applied) {
            assertInstanceOf(Applied.GeneratedHere.class, row.run().applied(),
                    "nothing else applies a behavior in a compile: " + row.identity().shown());
        }
    }

    @Test
    void aRowNothingAppliedSaysNothingApplied() {
        RowOutcome waiting = rowsOf(ROWS).stream()
                .filter(row -> row.disposition() == Disposition.PENDING)
                .findFirst().orElseThrow(() -> new AssertionError("the model has a row waiting"));

        assertEquals(new Applied.Nothing(), waiting.run().applied(),
                "a row recorded against a behavior with nothing to run it applied nothing");
        assertEquals(Stage.FIXTURES_VALIDATED, waiting.stage(),
                "and it says how far it did get, which is a different question");
    }

    /** A row that ran says what it cost, in the unit this compile counts. */
    @Test
    void whatARowCostIsWhatThisCompileCountedOfIt() {
        RowOutcome held = rowsOf(ROWS).stream()
                .filter(row -> row.disposition() == Disposition.HELD)
                .findFirst().orElseThrow(() -> new AssertionError("a row holds in this model"));

        Counting.Read counted = assertInstanceOf(Counting.Read.class, held.run().counting());
        assertTrue(counted.steps() >= 0, "the count is this compile's own reading");
        RunRecord.Recorded recorded = assertInstanceOf(RunRecord.Recorded.class, counted.recorded(),
                "this compile emitted what records where a row goes, so a run of one was recorded");
        assertFalse(recorded.seen().arms().isEmpty(),
                "and so are the arms it went through, this compile having emitted what counts them");
    }

    /**
     * The count is the row's and not the application's. A fixture applies the helpers it names before
     * the behavior is reached, so a row that never reached it has still done counted work — and
     * recording the count under what applied the behavior would have thrown that away.
     */
    @Test
    void aRowThatAppliedNothingStillSaysWhatItsFixturesSpent() {
        RowOutcome broke = rowsOf(SPENDS_BEFORE_IT_BREAKS).stream()
                .filter(row -> row.failurePhase() == souther.compiler.observe.FailurePhase.INPUT_FIXTURE)
                .findFirst().orElseThrow(() -> new AssertionError("the input fixture is the one that broke"));

        assertEquals(Stage.NONE, broke.stage(), "it did not get as far as applying anything");
        assertEquals(new Applied.Nothing(), broke.run().applied());
        assertTrue(assertInstanceOf(Counting.Read.class, broke.run().counting()).steps() > 0,
                "and the helper its fixture applied cost counted points: " + broke.run().counting());
    }

    @Test
    void anOutcomeCannotSayItAppliedNothingAndReachedApplication() {
        RowOutcome ran = rowsOf(ROWS).stream()
                .filter(row -> row.stage().reached(Stage.INVOKED))
                .findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> new RowOutcome(ran.at(), ran.target(), ran.identity(), ran.expectation(),
                        ran.stage(),
                        ran.disposition(), ran.failurePhase(), ran.expectedArm(), ran.resultArm(),
                        ran.inputCases(), ran.inputs(), ran.statement(), Run.nothing()),
                "a row that applied the behavior says what applied it");
        assertThrows(IllegalArgumentException.class,
                () -> new RowOutcome(ran.at(), ran.target(), ran.identity(), ran.expectation(),
                        Stage.FIXTURES_VALIDATED,
                        ran.disposition(), ran.failurePhase(), ran.expectedArm(), ran.resultArm(),
                        ran.inputCases(), ran.inputs(), ran.statement(),
                        new Run(new Applied.GeneratedHere(), new Counting.Read(1L, new RunRecord.NoAccount()))),
                "and one that did not has nothing to say applied it");
        assertThrows(NullPointerException.class,
                () -> new RowOutcome(ran.at(), ran.target(), ran.identity(), ran.expectation(),
                        ran.stage(),
                        ran.disposition(), ran.failurePhase(), ran.expectedArm(), ran.resultArm(),
                        ran.inputCases(), ran.inputs(), ran.statement(), null),
                "and every row says what became of its evaluation");
    }
}
