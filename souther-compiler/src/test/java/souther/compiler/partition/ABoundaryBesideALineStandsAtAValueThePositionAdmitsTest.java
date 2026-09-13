package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Text;
import souther.compiler.numeric.Towards;
import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternRead;
import souther.compiler.types.ValueName;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row offered beside a line stands at a value the position holds.
 *
 * <p>The run below a line on a string starts at the empty string, and a position whose declarations
 * ask for a character does not hold it. Composed from the run alone, that is the value offered: it is
 * the least the order has, it is inside the run, and nothing between choosing it and building the row
 * is able to say the declarations refuse it — the rules a placement is held against are the
 * arithmetic ones, and a place that is a string is handed back unheld
 * ({@code LevelRealizer.theRulesHaveNotRefused}). So the row goes out, comes back refused where it is
 * built, and a reader is told every value was tried at a run holding all but one of them.
 *
 * <p>What the run cannot say is exactly this. {@code String.length(value) >= 1} is a rule about
 * another number of the same position, so it is nowhere in the run of the order the boundary is on —
 * which is why what the position admits is an input of its own here and not something read back off
 * the range (#1581, and #1577 for the same sentence one route over).
 *
 * <p><b>Which value is composed is not held here.</b> What is held is that one is, and that it is a
 * value the declarations leave standing. Which string a set and a run come to between them is the
 * crossing's answer and moves with how the set is stated, which is a defect of its own (#1612) — a
 * test naming the string would go red when that is fixed and say nothing about this.
 */
class ABoundaryBesideALineStandsAtAValueThePositionAdmitsTest {

    private static final TermPath CODE = TermPath.of("code");

    private static final NumericTerm.FromOnePosition VALUE = new NumericTerm.ValueOf(CODE);

    private static final Carrier TEXT = new Carrier.Text();

    /**
     * What {@code String.length(value) >= 1} leaves, as the reading of the declarations states it:
     * the strings of a character or more, said as the language of them.
     *
     * <p>Said the way the corpus's own rule is said, because which shape a set arrives in decides
     * what the crossing can name in it (#1612). Written as {@link ValueSet.Cofinite} of the empty
     * string, the same set names no value above a line, and a test using it would be measuring that
     * defect rather than this one.
     */
    private static final ValueSet A_CHARACTER_AT_LEAST = matching("[\\s\\S]+");

    /** The one string the rule above refuses. */
    private static final Value REFUSED = new Value.Text("");

    private static ValueSet matching(String regex) {
        PatternRead said = PatternParser.read(regex);
        return ValueSet.matching(PatternPlan.of(
                        assertInstanceOf(PatternRead.Read.class, said, regex).syntax())
                .compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter()));
    }

    /**
     * The run on one side of a line at `spring`, which is what a boundary beside that line asks for.
     *
     * <p>The line's own value belongs to the side the run is not on, which is what an equality leaves
     * each side: the value against the line is the point on it, and the run beside it holds every
     * other value of that side. So the seam is built from the far side each way round, which is the
     * shape the report shows for `voucher < spring` and `spring < voucher`.
     */
    private static Criterion.Within run(Towards away) {
        LevelSpace space = LevelSpace.onACarrier(TEXT);
        Level line = new Level.OnACarrier(TEXT, Text.of("spring"));
        Seam parted = Seam.of(space, line, away.opposite());
        Band band = away == Towards.BELOW
                ? new Band(Band.endAt(null, null, Towards.ABOVE),
                        Band.endAt(parted, null, Towards.BELOW))
                : new Band(Band.endAt(parted, null, Towards.ABOVE),
                        Band.endAt(null, null, Towards.BELOW));
        return new Criterion.Within(band, null, away);
    }

    private static Realization realize(Criterion where, WitnessSearch looking) {
        return new LevelRealizer().realize(
                new Standing.OfOneCoordinate(VALUE, TEXT, where),
                NothingTheRulesSay.REGION, looking);
    }

    private static WitnessSearch admitting(ValueSet set) {
        return new WitnessSearch(AdmittedValues.of(Map.of(CODE, set)),
                PatternPlan.Budget.OF_A_WITNESS::meter);
    }

    /**
     * Either side of the line is offered a value the declarations leave standing.
     *
     * <p>Both, because the same input was missing on both. Below the line the run named a value and
     * it was the one string the rules refuse; above it the run named none at all and the set is what
     * has a value to name there. Different sentences in the report, one thing absent from the search.
     */
    @Test
    void theRunBelowTheLineStandsAtAValueTheDeclarationsLeave() {
        Place at = admittedValueIn(Towards.BELOW);

        assertNotEquals(Text.of(""), at,
                "the empty string is the least of the order and is the one string this position does"
                        + " not hold");
    }

    /**
     * And so does the run above it, which the range alone could name no value in at all.
     *
     * <p>A test of its own rather than the other side of a loop: each side is a way the search went
     * wrong and a run that stopped at the first would leave the second unmeasured.
     */
    @Test
    void theRunAboveTheLineStandsAtAValueTheDeclarationsLeaveToo() {
        assertInstanceOf(Text.class, admittedValueIn(Towards.ABOVE),
                "the range named no value above the line, and the set has one to name there");
    }

    /** The value the search offers for the run on one side, held to the set and to the run. */
    private static Place admittedValueIn(Towards away) {
        Place at = at(realize(run(away), admitting(A_CHARACTER_AT_LEAST)));
        Text wrote = assertInstanceOf(Text.class, at, () -> "a string was composed " + away);
        assertTrue(A_CHARACTER_AT_LEAST.has(new Value.Text(wrote.at())),
                () -> "the value offered is one the declarations admit: " + at);
        assertTrue(away == Towards.BELOW
                        ? wrote.at().compareTo("spring") < 0
                        : wrote.at().compareTo("spring") > 0,
                () -> "and it is inside the run the item is: " + at + " " + away);
        return at;
    }

    /**
     * A position nothing was read about is offered what the order offers, which is the value against
     * the line.
     *
     * <p>The other half of the same rule, and it is what says the set narrows the search rather than
     * replacing it. The end the item is named for reads better in a row than anything worked out of a
     * set, so it is what a position with nothing to say about its values keeps getting.
     */
    @Test
    void aPositionNothingWasReadAboutKeepsWhatTheOrderOffers() {
        assertEquals(Text.of(""), at(realize(run(Towards.BELOW), admitting(ValueSet.ANY))),
                "the least string of the order, which is what the run below a line starts at");
    }

    /** A search that may be handed no answer about the position is one that composes without
     *  asking, which is the shape this issue was. */
    @Test
    void whatThePositionAdmitsIsNotSomethingASearchMayBeHandedNoneOf() {
        assertThrows(IllegalArgumentException.class,
                () -> new LevelRealizer().realize(
                        new Standing.OfOneCoordinate(VALUE, TEXT, run(Towards.BELOW)),
                        NothingTheRulesSay.REGION, null));
    }

    /**
     * A position nothing worked out is said to be one, and not read as unrestricted.
     *
     * <p>Three states and the middle one is what this issue was. A rule leaving the position
     * everything is an answer and comes back as the set of every value; a position this reading
     * stopped before reaching is not that, and answering it with every value is the defect kept
     * behind the lookup instead of in front of it. A line can be drawn at a name deeper than the
     * positions a reading divided, so the third state is one models reach.
     */
    @Test
    void aPositionNothingWorkedOutSaysSoAndIsNotReadAsUnrestricted() {
        AdmittedValues admitted = AdmittedValues.of(Map.of(CODE, A_CHARACTER_AT_LEAST));

        assertEquals(new AdmittedValues.Admitted.Values(A_CHARACTER_AT_LEAST), admitted.at(CODE));
        assertTrue(!A_CHARACTER_AT_LEAST.has(REFUSED),
                "the set under test is the one that refuses the least string of the order");
        assertEquals(new AdmittedValues.Admitted.NotWorkedOut(),
                admitted.at(TermPath.of("somewhereElse")),
                "nothing was read about that position, which is not its rules leaving it every"
                        + " value");
    }

    /**
     * And a boundary at such a position composes nothing rather than a value out of every string.
     *
     * <p>Which is the whole point of the middle state reaching this far. Read as unrestricted, the
     * run below the line would be offered the least string of the order — the same value this issue
     * is about, arrived at one step further back.
     */
    @Test
    void aBoundaryAtAPositionNothingWorkedOutComposesNothing() {
        WitnessSearch elsewhere = new WitnessSearch(
                AdmittedValues.of(Map.of(TermPath.of("somewhereElse"), A_CHARACTER_AT_LEAST)),
                PatternPlan.Budget.OF_A_WITNESS::meter);

        assertInstanceOf(Realization.Unknown.class, realize(run(Towards.BELOW), elsewhere),
                "nothing worked out what this position holds, so nothing here composes a value at"
                        + " it");
    }

    /**
     * A number taken of the position is not one of its values, so the set is not put to it.
     *
     * <p>{@code String.length(code)} counts a string and the place composed for a boundary on it is a
     * count; the set holds the strings. Put to it, every count is refused for not being one of them
     * and a boundary on a length stops being offered a row — which is what happened when this was
     * wired by the position alone. The location the set is keyed by is where a value answering the
     * number is written, and that is not where the number itself stands.
     */
    @Test
    void aNumberTakenOfThePositionIsNotHeldToTheSetOfItsValues() {
        NumericTerm.FromOnePosition length = NumericTerm.TakenOf.of(
                ValueName.Stdlib.operation("String", "length"), CODE,
                souther.compiler.types.Type.Prim.STRING,
                souther.compiler.check.NewtypeInners.NONE,
                souther.compiler.check.Symbols.none(souther.compiler.DefaultStdlib.get()));

        assertEquals(CODE, length.position(),
                "both numbers of the location are read from the one location");
        Realization made = new LevelRealizer().realize(
                new Standing.OfOneCoordinate(length, new Carrier.Whole(),
                        new Criterion.Within(new Band(Band.endAt(null, null, Towards.ABOVE),
                                Band.endAt(null, null, Towards.BELOW)), null, Towards.ABOVE)),
                NothingTheRulesSay.REGION, admitting(A_CHARACTER_AT_LEAST));

        assertInstanceOf(Realization.Found.class, made,
                "a count is composed for a boundary on a length, and the strings the position holds"
                        + " are not what says whether one is");
    }

    /**
     * Each crossing of the set with a run is given an allowance of its own.
     *
     * <p>An item is a region and a region is as many runs as the rules leave it: a value singled out
     * of the middle of a band leaves the run under it and the run over it. Both are crossed with the
     * set in turn, and a meter spends down — so one shared between them would take what the first
     * crossing cost off what the second may spend, and whether the second is answered would follow
     * from the order the runs are looked at. Which order that is is a searching policy and says
     * nothing about the values.
     *
     * <p>Counted rather than starved. A test that made the first crossing expensive enough to starve
     * the second would be pinning how large a machine of these strings comes out, which is not what
     * is being promised here.
     */
    @Test
    void everyCrossingOfTheSetWithARunIsGivenItsOwnAllowance() {
        Carrier whole = new Carrier.Whole();
        // A band with a value singled out of the middle of it, which is two runs.
        Band band = new Band(Band.endAt(null, Bound.at(count(whole, 0), true), Towards.ABOVE),
                Band.endAt(null, Bound.at(count(whole, 10), true), Towards.BELOW));
        Criterion where = new Criterion.Within(band, count(whole, 5), Towards.ABOVE);
        // Nothing the order offers at the near end of either run, and a value only the upper one
        // holds — so the lower run is crossed, answers nothing, and the upper is crossed after it.
        ValueSet onlyEight = new ValueSet.Finite(Set.of(new Value.Number(
                java.math.BigDecimal.valueOf(8))));
        java.util.concurrent.atomic.AtomicInteger taken = new java.util.concurrent.atomic.AtomicInteger();
        WitnessSearch counting = new WitnessSearch(
                AdmittedValues.of(Map.of(CODE, onlyEight)),
                () -> {
                    taken.incrementAndGet();
                    return PatternPlan.Budget.OF_A_WITNESS.meter();
                });

        new LevelRealizer().realize(
                new Standing.OfOneCoordinate(VALUE, whole, where),
                NothingTheRulesSay.REGION, counting);

        assertTrue(taken.get() > 1,
                () -> "one allowance per crossing, and this item is more than one run: " + taken);
    }

    private static Level count(Carrier on, long at) {
        return new Level.OnACarrier(on, souther.compiler.numeric.Count.of(at));
    }

    private static Place at(Realization made) {
        Realization.Found found = assertInstanceOf(Realization.Found.class, made,
                "the run holds values the declarations leave, so a row stands somewhere in it");
        return found.fixing().get(new RealizationTarget.AtOnePosition(VALUE));
    }
}
