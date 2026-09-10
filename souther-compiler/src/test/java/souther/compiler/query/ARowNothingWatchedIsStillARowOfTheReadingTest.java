package souther.compiler.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class ARowNothingWatchedIsStillARowOfTheReadingTest {

    /** One row that runs, and one whose input the rules refuse, so nothing applies the behavior. */
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

    @Test
    void itIsCountedAndTheReadingSaysSo() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        DecisionEvidence evidence =
                compilation.db().ask(new Adequacy.Decides(module)).value().get("decides");

        DecisionEvidence.Taken.Read read = assertInstanceOf(DecisionEvidence.Taken.Read.class,
                evidence.taken(), "the rows were read");
        assertEquals(2, read.rowsRead(), () -> "both rows were read: " + read);
        assertEquals(read.rowsRead(),
                read.rowsPlaced() + read.rowsNotPlaced() + read.rowsNotWatched(),
                () -> "and each of them is in one of the counts: " + read);
        assertEquals(1, read.rowsPlaced(), () -> "the row that ran took a rule: " + read);
        assertTrue(read.everyRowWasWatched(),
                () -> "and both were watched, so the reading was made in full: " + read);
    }
}
