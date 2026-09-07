package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;

import java.util.Map;

import static souther.compiler.AxisClasses.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many combinations of two classes the rows reach, and what is honestly known about the rest.
 *
 * <p>The restraint under test is the denominator. A combination a row sits in is proven reachable and
 * the row is the proof; one no row sits in has not been shown impossible, because nothing has tried to
 * build a value for it. Reporting that as a gap sends an author after rows that may not exist, and
 * reporting it as unreachable flatters the number.
 */
class PairSpaceTest {

    private static final String MODEL = """
            module example.trip

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Amount = Int
                invariant value >= 0

            data Request = { kind: Kind, cost: Amount }
            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (request: Request) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (request) = {
                guard request.cost.value <= 100 else Waiting { cost = request.cost }
                Submitted { cost = request.cost }
            }
            """;

    private static PartitionEvidence evidence(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, PartitionEvidence> all = compilation.db()
                .ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
        assertNotNull(all, "the model under test compiles");
        return all.get(behavior);
    }

    @Test
    void theSpaceIsEveryClassOfOnePositionAgainstEveryClassOfAnother() {
        PartitionEvidence.PairSpace pairs = evidence(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(50) }) -> Submitted
                """, "submit").pairs();

        assertEquals(4, pairs.total(), "two kinds against two cost ranges");
        assertEquals(1, pairs.counts().covered());
    }

    /** The row is the proof. Nothing else here proves anything, and nothing here says it does. */
    @Test
    void aCombinationARowSitsInIsCoveredAndWhatIsLeftIsUnknown() {
        PartitionEvidence.PairSpace pairs = evidence(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(50) })  -> Submitted
                    | (Request { kind = Overseas, cost = Amount(500) }) -> Waiting
                """, "submit").pairs();

        assertEquals(2, pairs.counts().covered());
        assertEquals(2, pairs.unknown(),
                "nothing has tried to build the other two, so nothing is known about them");
        assertFalse(pairs.decided(), "with unknown combinations a single ratio would say nothing");
    }

    /**
     * And the two numbers are of the two positions the combinations are between.
     *
     * <p>What a reader is sent to. Summed, twenty-one unknown combinations across four positions
     * are as consistent with one relation nothing reaches as with six each missing a little, and
     * the two send an author to different places.
     */
    @Test
    void whatIsCoveredAndWhatIsUnknownIsSaidOfEachRelation() {
        PartitionEvidence.PairSpace pairs = evidence(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(50) })  -> Submitted
                    | (Request { kind = Overseas, cost = Amount(500) }) -> Waiting
                """, "submit").pairs();

        assertEquals(1, pairs.space().size(), "two divided positions make one relation");
        PartitionEvidence.PairSpace.Between between = pairs.space().getFirst().between();
        assertEquals(4, pairs.sizeOf(between));
        assertEquals(2, pairs.counts().covered(between));
        assertEquals(2, pairs.unknown(between));
    }

    @Test
    void aSpaceTheRowsFillEntirelyIsDecided() {
        PartitionEvidence.PairSpace pairs = evidence(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(50) })  -> Submitted
                    | (Request { kind = Domestic, cost = Amount(500) }) -> Waiting
                    | (Request { kind = Overseas, cost = Amount(50) })  -> Submitted
                    | (Request { kind = Overseas, cost = Amount(500) }) -> Waiting
                """, "submit").pairs();

        assertEquals(4, pairs.counts().covered());
        assertEquals(0, pairs.unknown());
        assertTrue(pairs.decided());
    }

    /** One divided position has no pairs, which is why single-position coverage is measured on its
     * own: derived from pairs it would read as complete while a class went untouched. */
    @Test
    void oneDividedPositionHasNoPairsAndStillHasClasses() {
        PartitionEvidence evidence = evidence("""
                module example.one

                data Yes
                data No
                data Answer = Yes | No

                data Score = { n: Int }

                behavior scoreFor : (answer: Answer) -> Score
                    constructs Score

                let scoreFor (answer) = Score { n = 1 }

                example scoreFor
                    | (Yes) -> Score { n = 1 }
                """, "scoreFor");

        assertEquals(0, evidence.pairs().total());
        assertEquals(1, evidence.axes().size());
        assertEquals(java.util.List.of("No"), names(evidence.axes().get(0).uncovered()),
                "the untouched class is still reported");
    }

    @Test
    void aRowThatCannotBeClassifiedAtOnePositionContributesNoPairThroughIt() {
        PartitionEvidence.PairSpace pairs = evidence(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(50) }) -> Submitted
                """, "submit").pairs();

        assertEquals(1, pairs.counts().covered(), "one row, one pair");
        assertEquals(3, pairs.unknown());
    }
}
