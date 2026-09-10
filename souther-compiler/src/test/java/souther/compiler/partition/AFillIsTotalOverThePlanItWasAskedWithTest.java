package souther.compiler.partition;

import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.Numberings;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Symbols;
import souther.compiler.query.ReadAs;
import souther.compiler.types.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A run answers for everything its plan named, and offers no row nothing points at.
 *
 * <p>Two relations, each held as a set equality. The obligations against the answers, so a way out
 * of the search that writes no entry cannot be built at all — which is what a reader downstream
 * used to meet as an absence, and made a sentence out of saying this compiler had failed to say.
 * And the rows against the answers that point at them, in both directions: an answer naming a row
 * the offer does not hold reports an obligation met by a line nobody is shown, and a row nothing
 * points at is a line offered for nothing.
 *
 * <p>Held here rather than in the search. The search is one producer and there are others — the
 * rows could not be read, the classes would not link — and a rule kept where the searching happens
 * is one the others are free to break.
 */
class AFillIsTotalOverThePlanItWasAskedWithTest {

    private static final RuleReadingSource SYMBOLS =
            RuleReadings.ofNoClauseFiled(Symbols.none(DefaultStdlib.get()));

    private static final ClassOfAPosition A_CLASS =
            new ClassOfAPosition(new AxisId("fee", "days"), "days/low");

    private static final ClassOfAPosition ANOTHER_CLASS =
            new ClassOfAPosition(new AxisId("fee", "days"), "days/high");

    /** Two places of one numbering, so that the arms below are addresses of one. */
    private static final Map<Integer, ArmProbe> PLACES = Numberings.arms(3);

    private static final ArmProbe ARM = PLACES.get(1);

    private static final ArmProbe ANOTHER = PLACES.get(2);

    private static final Generator.ArmOwed AN_ARM = new Generator.ArmOwed(ARM);

    private static final Generator.ArmOwed ANOTHER_ARM = new Generator.ArmOwed(ANOTHER);

    private static final Generator.UnresolvedCombination NOTHING_CAME_OF_IT =
            new Generator.UnresolvedCombination(List.of("days=low"),
                    Generator.UnresolvedCombination.Reason.NO_CANDIDATE_WAS_OFFERED);

    @Test
    void aClassTheRunDidNotAnswerForIsRefused() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new FillResult(planOver(List.of(A_CLASS), List.of()), new LinkedHashMap<>(), List.of(),
                        List.of(), Discharge.NOTHING));

        assertEquals(true, refused.getMessage().contains("days/low"), refused.getMessage());
    }

    @Test
    void anArmTheRunDidNotAnswerForIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> new FillResult(planOver(List.of(), List.of(AN_ARM)), new LinkedHashMap<>(), List.of(),
                        List.of(), Discharge.NOTHING));
    }

    @Test
    void aClassTheRunAnsweredForAndNobodyAskedAboutIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> new FillResult(planOver(List.of(A_CLASS), List.of()), new LinkedHashMap<>(), List.of(),
                        List.of(), new Discharge(
                                Map.of(A_CLASS, new ClassDisposition.Unresolved(NOTHING_CAME_OF_IT),
                                        ANOTHER_CLASS,
                                        new ClassDisposition.Unresolved(NOTHING_CAME_OF_IT)),
                                Map.of())));
    }

    @Test
    void anArmTheRunAnsweredForAndNobodyAskedAboutIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> new FillResult(planOver(List.of(), List.of(AN_ARM)), new LinkedHashMap<>(), List.of(),
                        List.of(), new Discharge(Map.of(),
                                Map.of(AN_ARM, new ArmDisposition.Unresolved(
                                                List.of(NOTHING_CAME_OF_IT)),
                                        ANOTHER_ARM, new ArmDisposition.Unresolved(
                                                List.of(NOTHING_CAME_OF_IT))))));
    }

    /**
     * A key with nothing under it is not an answer.
     *
     * <p>The check is over the obligations and what became of them, and holding it over the keys
     * alone let the absence back in one layer down: the plan named the class, the discharge held
     * the class, and what a reader looking it up got was the null every one of these values is
     * arranged to have none of.
     */
    @Test
    void aClassWithNothingUnderItIsNotAnAnswer() {
        Map<ClassOfAPosition, ClassDisposition> nothing = new LinkedHashMap<>();
        nothing.put(A_CLASS, null);

        assertThrows(IllegalArgumentException.class,
                () -> new Discharge(nothing, Map.of()),
                "a class the run was asked about and did not answer for");
    }

    @Test
    void anArmWithNothingUnderItIsNotAnAnswer() {
        Map<Generator.ArmOwed, ArmDisposition> nothing = new LinkedHashMap<>();
        nothing.put(AN_ARM, null);

        assertThrows(IllegalArgumentException.class, () -> new Discharge(Map.of(), nothing));
    }

    /** And the rows the answers point at, for the same reason: an id under nothing is not a row. */
    @Test
    void aRowIdWithNothingUnderItIsNotARow() {
        LinkedHashMap<RowId, ComposedRow> nothing = new LinkedHashMap<>();
        nothing.put(new RowId(0), null);

        assertThrows(IllegalArgumentException.class,
                () -> new FillResult(planOver(List.of(), List.of()), nothing, List.of(), List.of(),
                        Discharge.NOTHING));
    }

    @Test
    void anAnswerPointingAtARowTheOfferDoesNotHoldIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> new FillResult(planOver(List.of(A_CLASS), List.of()), new LinkedHashMap<>(), List.of(),
                        List.of(), new Discharge(
                                Map.of(A_CLASS, new ClassDisposition.Built(new RowId(0))),
                                Map.of())));
    }

    @Test
    void aRowNothingPointsAtIsRefused() {
        LinkedHashMap<RowId, ComposedRow> composed = new LinkedHashMap<>();
        composed.put(new RowId(0), new ComposedRow(List.of(FixtureTemplate.integer(1))));

        assertThrows(IllegalStateException.class,
                () -> new FillResult(planOver(List.of(A_CLASS), List.of()), composed, List.of(),
                        List.of(), new Discharge(
                                Map.of(A_CLASS,
                                        new ClassDisposition.Unresolved(NOTHING_CAME_OF_IT)),
                                Map.of())));
    }

    /** One answer per obligation and one row apiece, which is what a run that composed both looks
     *  like. */
    @Test
    void aRunThatAnsweredForEverythingItWasAskedIsBuilt() {
        LinkedHashMap<RowId, ComposedRow> composed = new LinkedHashMap<>();
        composed.put(new RowId(0), new ComposedRow(List.of(FixtureTemplate.integer(1))));

        FillResult filled = new FillResult(planOver(List.of(A_CLASS), List.of(AN_ARM)), composed,
                List.of(), List.of(), new Discharge(
                        Map.of(A_CLASS, new ClassDisposition.Built(new RowId(0))),
                        Map.of(AN_ARM, new ArmDisposition.Built(new RowId(0)))));

        assertEquals(1, filled.rows().size(), "one line, offered for both");
    }

    private static GenerationPlan planOver(List<ClassOfAPosition> classes,
                                           List<Generator.ArmOwed> arms) {
        souther.compiler.inputs.NumericTerm.ValueOf atDays =
                new souther.compiler.inputs.NumericTerm.ValueOf(
                        souther.compiler.inputs.TermPath.of("days"));
        Axis days = new Axis(new AxisId("fee", "days"), atDays,
                List.of(divided("days/low", 1).ofTheNumber(atDays),
                        divided("days/high", 9).ofTheNumber(atDays)),
                List.of());
        MeasuredInput subject = MeasuredInput.of("fee",
                souther.compiler.inputs.InputDomain.of(
                        List.of(new souther.compiler.inputs.InputDomain.Parameter("days", null,
                                Type.INT)),
                        SYMBOLS, ReadAs.THE_COMPILATION_DOES).reading(SYMBOLS),
                AxesATestWrote.asAMeasurement("fee", List.of(days)));
        return new GenerationPlan(subject, classes, arms);
    }

    private static PartitionClass divided(String id, long value) {
        return PartitionClass.of(id, id, new Recognition.Nothing(),
                RepresentativeSource.of(List.of(FixtureTemplate.integer(value))));
    }
}
