package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.partition.Generator;
import souther.compiler.partition.RulesTaken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every row the reading was given is in one of its counts.
 *
 * <p>A row is one of three things and the three are different facts: its rule was told, something
 * watched it and no rule could be told, or nothing watched it at all. A run with no account did not
 * go nowhere — it went somewhere nothing recorded, which is this compiler's shortfall and says
 * nothing about the model.
 *
 * <p>So the reading is held to accounting for what it was given. Written as the accounts that came
 * back, a row that left none is in no number at all, and a reading short of a row reads exactly
 * like one that read every one of them.
 */
class EveryRowTheReadingWasGivenIsInOneOfItsCountsTest {

    /** One row that runs and takes a rule, and one whose input the rules refuse. */
    private static final String MODEL = """
            module example.unwatched

            data Count = Int
                invariant value >= 0
            data Yes
            data No
            data Answer = Yes | No

            behavior decides : (a: Count) -> Answer
            let decides (a) = if a.value > 5 then Yes else No

            example decides
                | "over"    : (Count(6))  -> Yes
                | "refused" : (Count(-1)) -> No
            """;

    /** A row something watched and nothing could place is counted beside the placed one. */
    @Test
    void aRowWatchedAndNotPlacedIsCountedBesideThePlacedOne() {
        DecisionEvidence.Taken.Read read = readOf(compiled());
        assertEquals(2, read.rowsRead(), () -> "both rows were read: " + read);
        assertEquals(read.rowsRead(),
                read.rowsPlaced() + read.rowsNotPlaced() + read.rowsNotWatched(),
                () -> "and each of them is in one of the counts: " + read);
        assertEquals(1, read.rowsPlaced(), () -> "the row that ran took a rule: " + read);
        assertEquals(1, read.rowsNotPlaced(),
                () -> "and the row the rules refused was watched and placed nowhere: " + read);
        assertTrue(read.everyRowWasWatched(),
                () -> "both were watched, so this reading went without nothing: " + read);
    }

    /**
     * And a row nothing watched is counted as one, which the model above does not produce.
     *
     * <p>Asked of the reading itself rather than through a model. What leaves a row with no account
     * is a run this compile never read — a budget spent, a value that could not be handed on — and
     * a model that produces one is a model about those rather than about what a decision reads.
     * What has to hold is that such a row is not dropped, and that is this.
     */
    @Test
    void andARowNothingWatchedIsCountedAsOne() {
        DecisionEvidence.Taken.Read read = assertInstanceOf(DecisionEvidence.Taken.Read.class,
                DecisionEvidence.of("classify", rulesTakenOf(compiled()),
                        List.of(new Generator.Watched.NoAccount())),
                "a row is read whether or not anything watched it");
        assertEquals(1, read.rowsRead(), () -> "the row is one the reading was given: " + read);
        assertEquals(1, read.rowsNotWatched(), () -> "and nothing watched it: " + read);
        assertEquals(0, read.rowsPlaced() + read.rowsNotPlaced(),
                () -> "so it is in neither of the others: " + read);
        assertFalse(read.everyRowWasWatched(),
                () -> "and the reading says it went without something: " + read);
    }

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static DecisionEvidence.Taken.Read readOf(Compilation compilation) {
        DecisionEvidence evidence = compilation.db()
                .ask(new Adequacy.Decides(compilation.modules().get(0))).value().get("decides");
        return assertInstanceOf(DecisionEvidence.Taken.Read.class, evidence.taken(),
                "the rows were read");
    }

    /** What the rules of this body are matched against, which a reading of no runs never asks. */
    private static RulesTaken rulesTakenOf(Compilation compilation) {
        String module = compilation.modules().get(0);
        DecisionEvidence evidence =
                compilation.db().ask(new Adequacy.Decides(module)).value().get("decides");
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        CoverageSites.Plan plan = checked.plan();
        return RulesTaken.of(evidence.read(), checked.behaviorBodies().get("decides"), plan);
    }
}
