package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.DeclaredLine;
import souther.compiler.check.InvariantStatementId;
import souther.compiler.check.PartId;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.RuleRef;
import souther.compiler.numeric.EndSide;
import souther.compiler.numeric.Towards;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which end a bound placed survives being written down and read back.
 *
 * <p>A bound records the end it placed. Which side its own threshold falls on is not recorded — it
 * is derived, from that end together with whether the bound admits the threshold. So a reader
 * holding the derived pair and wanting the end back has to run that derivation backwards, and the
 * two are one law read in two directions.
 *
 * <p><b>Read off the side alone, half the rules a model can write come back inverted.</b> The two
 * agree wherever a bound admits its own threshold and disagree wherever it does not — so
 * {@code a <= b} lands on the end it was written with and {@code a < b} lands on the other one, and
 * a line whose sides are the wrong way round asks for two rows that prove nothing.
 *
 * <p>Held as the table rather than as one example. Which of the four a fixture happens to exercise
 * is not a property of the law, and the two that agree pass under either reading.
 */
class WhichEndABoundPlacedSurvivesBeingReadBackTest {

    /** One row of the law: what a line says about its own value, and the end that says it. */
    private record Row(Towards valueBelongs, boolean holdsAtTheValue, EndSide keeps) {

        /** The line these two facts are of, which is what the derivations under test read. */
        ComparisonClaim.Cut cut() {
            return new ComparisonClaim.Cut(valueBelongs, holdsAtTheValue);
        }
    }

    /**
     * Every way a bound's line can stand to its own value.
     *
     * <p>A maximum keeps what is below, so its own value is below the line exactly when it admits
     * it; a minimum keeps what is above, so its own value is below the line exactly when it does
     * not.
     */
    private static final List<Row> THE_LAW = List.of(
            new Row(Towards.BELOW, true, EndSide.UPPER),
            new Row(Towards.ABOVE, false, EndSide.UPPER),
            new Row(Towards.ABOVE, true, EndSide.LOWER),
            new Row(Towards.BELOW, false, EndSide.LOWER));

    /** The clause these name, which is only an identity. */
    private static RuleRef.Invariant aClause() {
        return new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("example.ends", "N")), 0),
                Optional.of(new ClauseName("within"))));
    }

    /** The end each pair is written with, said out loud so the table is the specification. */
    @Test
    void theEndFollowsFromWhatTheLineSaysAboutItsOwnValue() {
        for (Row row : THE_LAW) {
            assertEquals(row.keeps(), DeclaredThresholds.endKept(row.cut()),
                    () -> "belongs=" + row.valueBelongs() + " holds=" + row.holdsAtTheValue());
        }
    }

    /**
     * And a bound built from that end reads back as the pair it was built from.
     *
     * <p>The round trip, which is what makes the table above a law rather than a second opinion. A
     * derivation that disagreed with its inverse would put a line's sides the wrong way round in
     * exactly the cases the two happen to differ.
     */
    @Test
    void aBoundBuiltFromThatEndReadsBackAsThePairItCameFrom() {
        for (Row row : THE_LAW) {
            LineOrigin.InvariantOrigin origin = new LineOrigin.InvariantOrigin(
                    new DeclaredLine.OfAStatement(new InvariantStatementId(
                            new PartId<>(aClause(), 0), 0)),
                    DeclaredThresholds.endKept(row.cut()), row.holdsAtTheValue());

            assertEquals(row.cut(), origin.lineFacts().claim(),
                    () -> "which side the threshold's own value is on and whether the rule admits"
                            + " it, read back: " + row);
        }
    }

    /**
     * And the two ends are told apart, which is what the round trip would pass without.
     *
     * <p>A derivation answering one end for everything round-trips wherever the other half of the
     * pair carries the difference. The four rows are two of each, and that is the assertion.
     */
    @Test
    void bothEndsAreReached() {
        assertEquals(2, THE_LAW.stream()
                .map(row -> DeclaredThresholds.endKept(row.cut()))
                .distinct().count(),
                "a law that answered one end for every pair would round-trip and say nothing");
    }
}
