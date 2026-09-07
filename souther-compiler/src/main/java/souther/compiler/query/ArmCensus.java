package souther.compiler.query;

import souther.compiler.coverage.CoverageSites;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Whether the arms this account holds are all the arms it is owed.
 *
 * <p>A second question beside where each arm stands, and not a shade of it. Which arms a behavior
 * owes a row for is worked out from the model's own proofs about what can reach what: an arm nothing
 * arrives at is instrumented and is not owed, because no row can light it. Where a row lights one of
 * those anyway, nothing about the model is wrong and the proof is — and what that bears on is the
 * denominator, not any one arm. A row through an arm went through it however wrong a proof about a
 * neighbouring arm turned out to be.
 *
 * <p>Held apart for that reason. Folded into the arms, a disproved proof would take every arm's
 * answer with it: the arms a row certainly does not reach would stop being reported over an analysis
 * that was wrong somewhere else in the body, which is the shape this account exists to keep out.
 */
public sealed interface ArmCensus {

    /** Nothing has shown the denominator to be wrong. */
    record Settled() implements ArmCensus {}

    /**
     * Something has, so what the count is over is more than it says.
     *
     * <p>Never weakened by nothing, for the reason {@link Measurement.Partial} is not: a state that
     * says it is short of something and cannot say what would be the absence of the answer written
     * as an answer.
     */
    record Undecided(WeakeningSet by) implements ArmCensus {

        public Undecided {
            if (by == null || by.isEmpty()) {
                throw new IllegalArgumentException(
                        "a denominator left in doubt says what left it so");
            }
        }
    }

    /** Whether the denominator stands. */
    default boolean settled() {
        return this instanceof Settled;
    }

    /** What left it in doubt, which is empty where nothing did. */
    default WeakeningSet weakening() {
        return this instanceof Undecided it ? it.by() : WeakeningSet.none();
    }

    /**
     * What {@code provedWrong} leaves the denominator, which is the one place the two are related.
     *
     * @param provedWrong the arms proven unreachable that a row went through anyway, as the source
     *                    wrote them. Named by the arm and no longer by the behavior beside it: an
     *                    arm says which behavior's it is
     */
    static ArmCensus of(Set<CoverageSites.Obligation> provedWrong) {
        if (provedWrong.isEmpty()) {
            return new Settled();
        }
        Set<Weakening> by = new LinkedHashSet<>();
        for (CoverageSites.Obligation arm : provedWrong) {
            by.add(new Weakening.ProofContradicted(arm));
        }
        return new Undecided(WeakeningSet.ofAll(by));
    }
}
