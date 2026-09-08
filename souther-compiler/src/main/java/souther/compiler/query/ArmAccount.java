package souther.compiler.query;

import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.CoverageSites;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Every arm one behavior is owed a row for, with what was found out about each and about the set.
 *
 * <p>Made once, from what was observed, and read one way. What the rows lit, which forks stand for a
 * number of rules nobody established, and which proofs a row has already shown wrong are three
 * first-hand facts, and each of them is written onto the thing it is about: the first onto the arm
 * it lit, the second onto the arms of the fork it is about, the third onto the census. A
 * measurement assembled over this reads {@link #weakening()} and carries the reasons on; nothing
 * reads them back to work out what an arm's state was.
 *
 * <p>Which is the whole of the arrangement. Held the other way round — one set of reasons for the
 * measurement, and every surface deciding for itself which of them bears on what — an arm nothing
 * reaches stopped being reported because a helper elsewhere in the body could not be told apart, and
 * a reading that stopped in one row was printed as a word over every arm of the behavior.
 *
 * <p>Not public, and that is the boundary rather than a habit. This is where the measurement's own
 * weakening comes from, and a consumer that could reach it could work an arm's state out of the
 * reasons a second time. What leaves this package is {@link ArmSummary}, which has the groups and
 * the census and no way to the provenance.
 */
record ArmAccount(List<ArmObligation> obligations, ArmCensus census) {

    ArmAccount {
        obligations = List.copyOf(obligations);
        java.util.Objects.requireNonNull(census, "an account says whether its denominator stands");
    }

    /**
     * What became of every arm of {@code owed}, and of the set of them.
     *
     * <p>The occurrences of one arm are gathered here, and this is the only place the quotient is
     * taken. Everything below it — the probes, the proofs about what can reach what — is about one
     * occurrence at a time, because a copy of an arm spliced under one call site is reachable on
     * terms the copy under the next one does not share. What a row is owed for is the arm the author
     * wrote.
     *
     * @param owed       every occurrence of every arm the behavior is owed a row for, in the order
     *                   the body holds them
     * @param covered    the probes a row that states what it expects went through
     * @param awaiting   the probes a row whose answer is owed went through
     * @param rowsUnread what the reading of this behavior's rows went without
     * @param census     whether anything has shown {@code owed} to be short of an arm
     */
    static ArmAccount of(List<CoverageSites.ArmSite> owed, Set<ArmProbe> covered,
                         Set<ArmProbe> awaiting, WeakeningSet rowsUnread, ArmCensus census) {
        java.util.SequencedMap<CoverageSites.Obligation, List<CoverageSites.ArmSite>> byObligation =
                new LinkedHashMap<>();
        for (CoverageSites.ArmSite site : owed) {
            byObligation.computeIfAbsent(site.obligation(), _ -> new ArrayList<>()).add(site);
        }
        List<ArmObligation> arms = new ArrayList<>();
        byObligation.forEach((_, occurrences) ->
                arms.add(ArmObligation.of(occurrences, covered, awaiting, rowsUnread)));
        return new ArmAccount(arms, census);
    }

    /**
     * Everything this account went without, gathered from the things it went without them about.
     *
     * <p>One direction, and every part of the account is in it. An arm the rows left open carries
     * what the reading went without; an arm out of the count carries the fork nothing settled; the
     * census carries the proof a row went against. A measurement over this is complete where all
     * three are empty, which is what says a behavior whose every arm a row went through is measured
     * in full although a row of it somewhere else did not come back.
     *
     * <p>The reading's own reasons are not added here whole. What an unfinished reading leaves this
     * account is the arms it may have lit and did not, so the reasons arrive through those arms and
     * through nothing else; added over the top, they would weaken an account with nothing open in
     * it.
     */
    WeakeningSet weakening() {
        WeakeningSet out = census.weakening();
        for (ArmObligation arm : obligations) {
            out = switch (arm) {
                case ArmObligation.Counted it -> out.union(it.coverage().weakening());
                case ArmObligation.NotCounted it ->
                        out.union(WeakeningSet.of(it.because().weakening()));
            };
        }
        return out;
    }

    /** What a consumer reads, which is the groups and the qualification and nothing else. */
    ArmSummary summary() {
        return new ArmSummary(obligations, census);
    }
}
