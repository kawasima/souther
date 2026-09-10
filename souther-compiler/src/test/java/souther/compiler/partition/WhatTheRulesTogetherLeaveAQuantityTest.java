package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.DeclaredLine;
import souther.compiler.check.InvariantStatementId;
import souther.compiler.check.PartId;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.EndSide;
import souther.compiler.numeric.Towards;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runs of values several rules leave one quantity.
 *
 * <p>Several rules can cut one quantity and they are one arrangement however they were written:
 * {@code n > 10} and {@code n > 20} leave three runs, not two overlapping pairs. What each rule did
 * on its own is a {@link Seam}; what they come to together is here, and it is the one thing both the
 * classes of a position and the points of a border are read off.
 *
 * <p>Built from seams rather than from thresholds, so two spellings of one division contribute one
 * place to part the values rather than two — and the run between them, which no row could ever be
 * written in, never exists to be counted.
 */
class WhatTheRulesTogetherLeaveAQuantityTest {

    private static final Carrier WHOLE = new Carrier.Whole();
    private static final LevelSpace NUMBERS = LevelSpace.onACarrier(WHOLE);

    private static Level at(String number) {
        return new Level.OnACarrier(WHOLE, new Count(new java.math.BigDecimal(number)));
    }

    /** Where {@code n <= t} parts the whole numbers. */
    private static Seam upTo(String t) {
        return Seam.of(NUMBERS, at(t), Towards.BELOW);
    }

    /** Where {@code n < t} parts them, which is the same place as {@code n <= t - 1}. */
    private static Seam under(String t) {
        return Seam.of(NUMBERS, at(t), Towards.ABOVE);
    }

    /** How the runs read, as the two seams each of them lies between. */
    private static List<String> bandsOf(Seam... parted) {
        return within(null, null, parted);
    }

    /** The same, where the rules leave the quantity only what lies between two values. */
    private static List<String> within(String low, String high, Seam... parted) {
        return QuantityArrangement.of(NUMBERS, byItsOwnRule(parted),
                        between(low == null ? null : Bound.at(at(low), true),
                                high == null ? null : Bound.at(at(high), true)))
                .bands().stream().map(Band::key).toList();
    }

    /**
     * The two places the rules leave the quantity between, with nothing having taken either in.
     *
     * <p>Written side by side here and not offered by {@link DomainEnds}, which asks for the end at
     * a side rather than being handed a pair: what that keeps a caller from doing is putting one
     * end's attribution beside the other's, and there is no attribution here to put anywhere.
     */
    private static DomainEnds between(Bound low, Bound high) {
        return DomainEnds.of(side -> DomainEnd.at(side, side == EndSide.LOWER ? low : high));
    }

    /**
     * A run parted at one end reaches somewhere on that side, and is refused without it.
     *
     * <p>A line gives the run beside it an end whether or not the quantity has a value there, so
     * there is no run parted here that reaches nowhere. Left open, such an end would answer where
     * the run reaches with the same nothing an end of the order does, and everything that reads a
     * run as a set of values would take it for one that runs on.
     */
    @Test
    void aRunPartedAtOneEndReachesSomewhereOnThatSide() {
        assertThrows(IllegalArgumentException.class,
                () -> new BandEnd.AtParting(upTo("10"), null),
                "a parted end without a reach is not a state a run can be in");
    }

    /**
     * A run claiming to be stopped where it does not reach is refused.
     *
     * <p>The run and what stops it are one value because they are one answer, and a value saying one
     * thing in its own ends and another in what it is owed to would be two answers to the question
     * it exists to settle. So each of the things said to stop it is held to where the run actually
     * gets: a line stops it only where the run reaches that line, and an end the rules leave only
     * where the run reaches that end.
     */
    @Test
    void aRunClaimingToStopWhereItDoesNotReachIsRefused() {
        Band values = new Band(new BandEnd.AtOrderEnd(Towards.BELOW),
                new BandEnd.AtDomain(Bound.at(at("50"), true)));

        assertThrows(IllegalArgumentException.class,
                () -> new QuantityArrangement.Run(values, stoppedByTheOrder(Towards.BELOW),
                        stoppedByTheDomain(Bound.at(at("100"), true))),
                "the run stops at fifty and says it stops at a hundred");
        assertThrows(IllegalArgumentException.class,
                () -> new QuantityArrangement.Run(values, stoppedByTheOrder(Towards.ABOVE),
                        stoppedByTheDomain(Bound.at(at("50"), true))),
                "nor is the end of the order the other way round from the end this is");
    }

    private static List<RegionClaim> stoppedByTheOrder(Towards towards) {
        return List.of(new RegionClaim(new FarEnd.AtTheOrderEnd(towards),
                PointContributions.none()));
    }

    private static List<RegionClaim> stoppedByTheDomain(Bound at) {
        return List.of(new RegionClaim(new FarEnd.AtTheDomain(at), PointContributions.none()));
    }

    /** What stops the first run of {@code arranged} at its high end, without who can move it. */
    private static List<FarEnd> farEndsOf(QuantityArrangement arranged) {
        return arranged.runs().get(0).endsAt(Towards.ABOVE).stream()
                .map(RegionClaim::basis).toList();
    }

    /** Each place with a line of its own against it, these being tests about where the values part
     *  and not about who parted them. */
    private static List<Parting> byItsOwnRule(Seam... parted) {
        List<Parting> out = new java.util.ArrayList<>();
        for (Seam each : parted) {
            out.add(Parting.by(each, aLine(out.size())));
        }
        return out;
    }

    /** One clause of one declaration, told from the next by which clause of it this is. */
    static AuthoredLine aLine(int clause) {
        return new AuthoredLine(new WhichLine.OfADeclarationsLine(
                new DeclaredLine.OfAStatement(new InvariantStatementId(new PartId<>(
                        new souther.compiler.check.RuleRef.Invariant(
                                new souther.compiler.check.Clause.Ref(
                                        new souther.compiler.check.Clause.Id(
                                                souther.compiler.types.TypeSymbols.declared(
                                                        new souther.compiler.types.TypeKey(
                                                                "example.runs", "N")),
                                                clause),
                                        java.util.Optional.empty())), 0),
                        0))),
                new LineFacts(new ComparisonClaim.Cut(Towards.ABOVE, true)), List.of());
    }

    /**
     * A run says which values are in it, and the ends it names are in it.
     *
     * <p>Both ends inclusive, because a run is named by the values at its ends rather than by the
     * lines beside it: the first value above a seam is in the run above, and the last value below is
     * in the run below. A reader that took the seam's own numbers for the ends would put the value
     * against the line on the wrong side of it.
     */
    @Test
    void aRunHoldsTheValuesFromItsFirstToItsLast() {
        Band mid = QuantityArrangement.of(NUMBERS, byItsOwnRule(upTo("10"), upTo("20")))
                .bands().get(1);

        assertEquals(false, mid.holds(at("10")), "ten is the last value below it");
        assertEquals(true, mid.holds(at("11")), "eleven is its first");
        assertEquals(true, mid.holds(at("20")), "twenty is its last");
        assertEquals(false, mid.holds(at("21")), "twenty-one is the first value above it");
    }

    /** And a run with no seam under it runs from wherever the rules start the quantity. */
    @Test
    void aRunWithNothingPartingItBelowRunsFromTheStart() {
        Band first = QuantityArrangement.of(NUMBERS, byItsOwnRule(upTo("10")),
                        between(Bound.at(at("0"), true), null))
                .bands().get(0);

        assertEquals(false, first.holds(at("-1")), "the rules leave nothing below zero");
        assertEquals(true, first.holds(at("0")));
        assertEquals(true, first.holds(at("10")));
        assertEquals(false, first.holds(at("11")));
    }

    /**
     * A run open at a line the quantity names no value beside is read at the line, in its own units.
     *
     * <p>{@code 3 * d <= 1} parts the decimals at a third, which no finite decimal is — so neither
     * run has a value against the line and both are read at the position itself. A third is what the
     * rule wrote over how much of the quantity it wrote it in, and a reader comparing a value of
     * {@code d} against the one the rule carried would put every decimal up to one below the line.
     */
    @Test
    void aRunOpenAtItsLineIsReadInTheQuantitysOwnUnits() {
        java.math.BigDecimal three = new java.math.BigDecimal("3");
        souther.compiler.check.Carrier dense = new souther.compiler.check.Carrier.Dense();
        LevelSpace decimals = LevelSpace.onACarrier(dense);
        Seam third = Seam.of(
                LevelSpace.overFiniteDecimals(LevelSpace.generatorOverFiniteDecimals(three)),
                new Level.ACount(new Count(java.math.BigDecimal.ONE)), Towards.BELOW,
                new Seam.Scale(three, dense));
        Band below = QuantityArrangement.of(decimals, byItsOwnRule(third)).bands().get(0);

        assertEquals(true, below.holds(decimal(dense, "0.2")), "a fifth is under a third");
        assertEquals(false, below.holds(decimal(dense, "0.5")),
                "and a half is over it, however the rule wrote the line");
    }

    private static Level decimal(souther.compiler.check.Carrier of, String number) {
        return new Level.OnACarrier(of, new Count(new java.math.BigDecimal(number)));
    }

    /**
     * Two rules that part the values at one place, one keeping it and one giving it away.
     *
     * <p>Over a carrier whose values fill, {@code <= 0.5} and {@code < 0.5} are two divisions at one
     * number, and together they leave three runs: everything under it, the number itself, and
     * everything over it. Ordered by a value either of them names — both name 0.5 — the two come out
     * in whichever order they were read, and one of the two orders leaves the middle run open at
     * both ends. Read on, that run holds nothing and disappears, and the two runs either side both
     * hold 0.5.
     */
    @Test
    void twoRulesAtOnePlaceLeaveTheValueItselfBetweenThem() {
        souther.compiler.check.Carrier of = new souther.compiler.check.Carrier.Dense();
        LevelSpace decimals = LevelSpace.onACarrier(of);
        Level half = new Level.OnACarrier(of, new Count(new java.math.BigDecimal("0.5")));
        Seam keeps = Seam.of(decimals, half, Towards.BELOW);
        Seam givesAway = Seam.of(decimals, half, Towards.ABOVE);

        for (List<Seam> order : List.of(List.of(keeps, givesAway), List.of(givesAway, keeps))) {
            assertEquals(List.of("|", "0.5|0.5", "|"),
                    QuantityArrangement.of(decimals,
                                    byItsOwnRule(order.toArray(new Seam[0]))).bands().stream()
                            .map(Band::key).toList(),
                    "read in either order, the number itself is a run of its own: " + order);
        }
    }

    /**
     * Two rules at one place divide the quantity once, and both of them are against that place.
     *
     * <p>Where the values part is the quantity's answer, so a second rule at one number leaves the
     * runs as they were. Which rules put a line there is the model's, and both did — each of them
     * enough on its own, since taking either away leaves the place where it is.
     *
     * <p>Told apart by whoever read them instead, one of the two stood for the other and the second
     * line was not written down anywhere. Which is why the arrangement is the only place that says
     * two candidates are one place.
     */
    @Test
    void twoRulesAtOnePlaceAreOnePlaceWithBothLinesAgainstIt() {
        QuantityArrangement arranged = QuantityArrangement.of(NUMBERS,
                List.of(Parting.by(upTo("10"), aLine(0)), Parting.by(upTo("10"), aLine(1))));

        assertEquals(1, arranged.partings().size(), "the values part once");
        assertEquals(List.of(aLine(0), aLine(1)), arranged.partings().get(0).alternatives(),
                "and each of the two lines is against that place");
        assertEquals(List.of("|10", "11|"), arranged.bands().stream().map(Band::key).toList(),
                "so there are two runs and not three");
    }

    /**
     * A second line at one place leaves what a row is asked for exactly as it was.
     *
     * <p>Which is the whole of why the lines against a place are the arrangement's answer and not
     * the run's. A body comparing against a number a declaration already stops the quantity at adds
     * a line and moves nothing: the values part where they parted, and the run beside it reaches
     * where it reached. Carried into the run, that redundant rule would be inside every value
     * saying what is asked of a row — and two readings asking one thing would come out asking two,
     * which is exactly what the check for two readings of one line disagreeing is about.
     */
    @Test
    void aSecondLineAtOnePlaceDoesNotChangeWhatARowIsAskedFor() {
        QuantityArrangement one = QuantityArrangement.of(NUMBERS,
                List.of(Parting.by(upTo("10"), aLine(0))));
        QuantityArrangement also = QuantityArrangement.of(NUMBERS,
                List.of(Parting.by(upTo("10"), aLine(0)), Parting.by(upTo("10"), aLine(1))));

        assertNotEquals(one.partings(), also.partings(),
                "the two arrangements do differ, and this is what they differ in");
        assertEquals(one.bands(), also.bands(), "and the runs they leave are the same runs");
        assertTrue(run(one).sameAs(run(also)),
                "so one row answers both, and the second line has not made a second demand");
    }

    /** The run above the line, as a border's point away from it asks for it. */
    private static Criterion run(QuantityArrangement arranged) {
        return new Criterion.Within(arranged.bands().get(1), null, Towards.ABOVE);
    }

    /**
     * A line where the rules already stop the quantity stops the run as well, and neither replaces
     * the other.
     *
     * <p>Each of them stops it there without the other: taking the line away leaves the run at the
     * end the rules leave, and taking that away leaves it at the line. So a row inside answers for
     * both, and adding the line to a model that already had the end leaves what was owed there owed
     * and adds one — which is what a rule that moves nothing may do and no more.
     */
    @Test
    void aLineWhereTheRulesAlreadyStopTheQuantityStopsTheRunAsWell() {
        Bound upTo100 = Bound.at(at("100"), true);
        QuantityArrangement withoutTheLine = QuantityArrangement.of(NUMBERS, List.of(),
                between(null, upTo100));
        QuantityArrangement withTheLine = QuantityArrangement.of(NUMBERS,
                List.of(Parting.by(upTo("100"), aLine(0))),
                between(null, upTo100));

        assertEquals(List.of(new FarEnd.AtTheDomain(upTo100)),
                farEndsOf(withoutTheLine),
                "with no line there, the run stops where the rules leave the quantity");
        assertEquals(List.of(new FarEnd.AtALine(aLine(0), upTo("100")),
                        new FarEnd.AtTheDomain(upTo100)),
                farEndsOf(withTheLine),
                "and with one, at the line as well — the end it had is still an end");
        assertTrue(new Criterion.Within(withoutTheLine.bands().get(0), null, Towards.BELOW)
                        .sameAs(new Criterion.Within(withTheLine.bands().get(0), null,
                                Towards.BELOW)),
                "and the run asks a row for exactly what it did: the values are the values, however"
                        + " many things stop it where it stops");
    }

    /**
     * A line the rules stop the quantity short of does not stop the run.
     *
     * <p>The run reaches the tighter of the two, and a line past that is a line it never gets to.
     * Written down as what stopped it, a rule that settles nothing about the region would be named
     * as the thing a row inside answers for — and two readings whose runs stop in different places
     * would come back as one point asking two different things.
     */
    @Test
    void aLineTheRulesStopShortOfDoesNotStopTheRun() {
        QuantityArrangement arranged = QuantityArrangement.of(NUMBERS,
                List.of(Parting.by(upTo("100"), aLine(0))),
                between(null, Bound.at(at("50"), true)));

        assertEquals(List.of(new FarEnd.AtTheDomain(Bound.at(at("50"), true))),
                farEndsOf(arranged),
                "the rules stop it at fifty, so the line at a hundred settles nothing here");
    }

    /** And a rule read twice is one rule, which is what says how many runs a row answers for. */
    @Test
    void oneRuleReadTwiceIsOneLineAgainstThePlace() {
        QuantityArrangement arranged = QuantityArrangement.of(NUMBERS,
                List.of(Parting.by(upTo("10"), aLine(0)), Parting.by(upTo("10"), aLine(0))));

        assertEquals(List.of(aLine(0)), arranged.partings().get(0).alternatives(),
                "one line, however many readings of it arrived");
    }

    /**
     * The outermost runs stop where the rules stop the quantity.
     *
     * <p>A bound is not a cut: nothing outside it can be constructed, so there is no run on the far
     * side to cover (ADR-0090). What it does is end the run beside it — the two either side of a
     * line at ten run from the bound and not from the order's own extent.
     */
    @Test
    void theOutermostRunsStopWhereTheRulesStopTheQuantity() {
        assertEquals(List.of("0|10", "11|20", "21|100"), within("0", "100", upTo("10"), upTo("20")));
    }

    /**
     * A place the rules leave nothing at parts nothing.
     *
     * <p>A cut outside what the quantity is left is not a division of it — the values it would tell
     * apart are values no row can be written at — so it is dropped rather than kept as a run holding
     * nothing.
     */
    @Test
    void aCutOutsideWhatTheRulesLeaveDividesNothing() {
        assertEquals(List.of("0|10", "11|100"), within("0", "100", upTo("10"), upTo("200")));
    }

    /**
     * And a run the rules leave no value in is not a run.
     *
     * <p>{@code invariant value <= 10} beside a line at ten leaves everything from eleven up with no
     * value in it. Counted, it is a class an author is told to write a row for and cannot. Judged on
     * the numbers alone it looks inhabited, because eleven is less than the order's own end; what
     * settles it is what the quantity is left, which is nothing above ten.
     */
    @Test
    void aRunTheRulesLeaveNoValueInIsNotARun() {
        assertEquals(List.of("0|10"), within("0", "10", upTo("10")));
    }

    /**
     * One cut leaves two runs, and both of them run to the order's own end.
     *
     * <p>The ends are the order's and are not seams. A run with nothing parting it at one end is
     * where the quantity itself stops, and writing a seam there would owe a row against a line no
     * rule drew.
     */
    @Test
    void oneCutLeavesTwoRuns() {
        assertEquals(List.of("|4", "5|"), bandsOf(upTo("4")));
    }

    /** Two cuts leave three, and the middle one runs between them. */
    @Test
    void twoCutsLeaveThreeRuns() {
        assertEquals(List.of("|10", "11|20", "21|"),
                bandsOf(upTo("10"), upTo("20")));
    }

    /** Three leave four, in the order the values are in and not the order the rules were written. */
    @Test
    void threeCutsLeaveFourRunsInTheOrderOfTheValues() {
        assertEquals(List.of("|10", "11|20", "21|30", "31|"),
                bandsOf(upTo("30"), upTo("10"), upTo("20")));
    }

    /**
     * Two rules that part the values in one place leave one seam and no run between them.
     *
     * <p>The defect this is built to make impossible. {@code n <= 4} and {@code n < 5} are one
     * division of the whole numbers; taken as two thresholds they leave a run above four and below
     * five, and a report counts a class that no row can ever be written in.
     */
    @Test
    void twoSpellingsOfOneDivisionLeaveNoRunBetweenThem() {
        assertEquals(List.of("|4", "5|"),
                bandsOf(upTo("4"), under("5")));
    }
}
