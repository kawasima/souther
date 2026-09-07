package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.observe.Classification;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;
import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rows for what nothing covers.
 *
 * <p>Two things are being held to here beyond the rows themselves. The same model has to produce the
 * same rows every time, or a generated block cannot be compared with the one from last week and a build
 * that prints it is noise. And a combination nothing could be written for has to come back said out
 * loud, with which of the three things happened — a generator that returned its successes and swallowed
 * the rest would read as though it had filled everything.
 */
class GeneratorTest {

    private static final String TRIP = """
            module example.trip

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Request = { kind: Kind, urgent: Bool }

            data Accepted = { at: String }

            behavior submit : (request: Request) -> Accepted
                constructs Accepted

            let submit (request) = Accepted { at = "now" }
            """;

    /** A sum whose cases are records, which is what a generator composes rather than names. */
    private static final String PAYMENT = """
            module example.pay

            data Amount = Decimal
                invariant value >= 0.0m

            data Card = { number: Amount }
            data Cash = { amount: Amount }
            data Payment = Card | Cash

            behavior feeFor : (p: Payment) -> Amount
                constructs Amount

            let feeFor (p) =
                match p with
                    | Card -> Amount(1.0m)
                    | Cash -> Amount(0.0m)
            """;

    /** An optional whose element is a record, which is the same value this composes at a parameter. */
    private static final String OPTIONAL_RECORD = """
            module example.opt

            data Amount = Decimal
                invariant value >= 0.0m

            data Card = { number: Amount }

            data Request = { card: Card? }

            behavior feeOf : (p: Request) -> Amount
                constructs Amount

            let feeOf (p) =
                match p.card with
                    | None -> Amount(0.0m)
                    | Some c -> c.number
            """;

    private record Model(MeasuredInput subject, RuleReadingSource rules) {}

    private static Model modelOf(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        assertNotNull(prepared);
        assertNotNull(sigs);
        Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                .filter(b -> b.name().equals(behavior)).findFirst().orElseThrow();
        Sig sig = sigs.get(behavior);
        InputDomain domain = InputDomain.of(spec, sig, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning = Partitions.of(spec.name(), domain, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        return new Model(
                MeasuredInput.of(spec.name(), domain.reading(rules), partitioning),
                rules);
    }

    private static List<String> texts(FillResult result) {
        List<String> out = new ArrayList<>();
        for (Generator.GeneratedRow row : result.rows()) {
            out.add(String.join(", ", row.inputs().stream().map(FixtureTemplate::text).toList()));
        }
        return out;
    }

    /**
     * One row per class no row is in, and each of them about that class alone.
     *
     * <p>Two positions of two classes each is four classes, and nothing having been written means
     * all four are still to write. Not four <em>combinations</em>: the four are what the report
     * names, and a row named for two of them at once says nothing about which of the two the answer
     * turned on (issue #967). What the position the row is not about holds is a value it has to
     * hold and is no part of what the row is for.
     */
    @Test
    void everyClassNoRowIsInGetsARowAboutThatClassAlone() {
        FillResult filled = Generator.fill(modelOf(TRIP, "submit").subject(),
                List.of(), Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(List.of(), filled.unresolved());
        assertEquals(4, filled.rows().size(), texts(filled).toString());
        assertEquals(List.of(
                        List.of("request.kind=Domestic"),
                        List.of("request.kind=Overseas"),
                        List.of("request.urgent=true"),
                        List.of("request.urgent=false")),
                filled.rows().stream().map(row -> row.labels()).toList());
    }

    /** Several positions of one parameter are one value. {@code request.kind} and {@code request.urgent}
     * are two positions of one {@code Request}, and a row writes one of those. */
    @Test
    void positionsOfOneParameterCompoundIntoOneValue() {
        FillResult filled = Generator.fill(modelOf(TRIP, "submit").subject(),
                List.of(), Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(1, filled.rows().get(0).inputs().size());
        assertEquals("Request { kind = Domestic, urgent = true }",
                filled.rows().get(0).inputs().get(0).text());
    }

    /** A class a row already sits in is not asked for again — and one row sits in one of each. */
    @Test
    void whatTheRowsAlreadyReachIsNotGeneratedAgain() {
        MeasuredInput subject = modelOf(TRIP, "submit").subject();
        Map<AxisId, Classification> written = Map.of(
                new AxisId("submit", "request.kind"), Classification.in("Domestic"),
                new AxisId("submit", "request.urgent"), Classification.in("true"));

        FillResult filled =
                Generator.fill(subject, List.of(Generator.ObservedRow.unseen(written)),
                        Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(List.of(List.of("request.kind=Overseas"), List.of("request.urgent=false")),
                filled.rows().stream().map(row -> row.labels()).toList(),
                "the two classes that row is in are not asked for again");
    }

    /** A block that changed between two runs of one model could not be compared with the last one. */
    @Test
    void theSameModelGeneratesTheSameRowsTwice() {
        FillResult once = Generator.fill(modelOf(TRIP, "submit").subject(),
                List.of(), Generator.CandidateCheck.ANY, Budgets.generation());
        FillResult again = Generator.fill(modelOf(TRIP, "submit").subject(),
                List.of(), Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(texts(once), texts(again));
        assertEquals(once.rows().stream().map(row -> row.labels()).toList(),
                again.rows().stream().map(row -> row.labels()).toList());
    }

    // --- what a candidate says and what it does not ----------------------------------------------

    /** Two positions, each a bare number, so a hand-made class is the whole of what is at each. */
    private static MeasuredInput twoNumbers(RuleReadingSource rules, List<PartitionClass> left,
                                                List<PartitionClass> right) {
        NumericTerm.ValueOf atA = new NumericTerm.ValueOf(TermPath.of("a"));
        NumericTerm.ValueOf atB = new NumericTerm.ValueOf(TermPath.of("b"));
        Axis a = new Axis(new AxisId("f", "a"), atA, classesOf(left, atA), List.of());
        Axis b = new Axis(new AxisId("f", "b"), atB, classesOf(right, atB), List.of());
        // Axes written here rather than read off a model, so nothing counts a container of this
        // input. The reading is still the input's own: what a number at one of these positions is
        // measured on is what the declarations say, and the subject asks it for that.
        return MeasuredInput.of("f", readingOf(rules, "a", "b"),
                AxesATestWrote.asAMeasurement("f", List.of(a, b)));
    }

    /** The reading of an input whose parameters are bare numbers, which is what says what a number
     *  at one of them is measured on. */
    private static souther.compiler.inputs.InputReading readingOf(RuleReadingSource rules,
                                                                  String... parameters) {
        List<souther.compiler.inputs.InputDomain.Parameter> declared = new java.util.ArrayList<>();
        for (String each : parameters) {
            declared.add(new souther.compiler.inputs.InputDomain.Parameter(each, null, Type.INT));
        }
        return souther.compiler.inputs.InputDomain.of(declared, rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES).reading(rules);
    }

    /** The classes said to be of the number the axis they are put on measures, which is what a
     *  producer of them does and what an axis requires. */
    private static List<PartitionClass> classesOf(List<PartitionClass> classes,
                                                  NumericTerm.FromOnePosition of) {
        return classes.stream().map(each -> each.ofTheNumber(of)).toList();
    }

    private static PartitionClass number(String id, long... candidates) {
        List<FixtureTemplate> values = new ArrayList<>();
        for (long each : candidates) {
            values.add(FixtureTemplate.integer(each));
        }
        return PartitionClass.of(id, id, new Recognition.Nothing(),
                RepresentativeSource.of(values));
    }

    /**
     * A value refused at construction says nothing about the class it stood for.
     *
     * <p>Two classes covering wide ranges can have chosen representatives that break a rule relating
     * them while other values of the same two classes do not. A generator that gave up on the first
     * refusal would report a combination as unfilled that a second value fills.
     */
    @Test
    void aRefusedValueIsFollowedByTheNextOne() {
        RuleReadingSource rules = modelOf(TRIP, "submit").rules();
        MeasuredInput subject = twoNumbers(rules, List.of(number("low", 1, 2)),
                List.of(number("high", 10, 20)));
        Generator.CandidateCheck refusesTheFirst = Generator.CandidateCheck.refusing(
                (at, candidate) -> candidate.text().equals("1") || candidate.text().equals("10")
                        ? Optional.of("the first pair is not allowed together") : Optional.empty());

        FillResult filled =
                Generator.fill(subject, List.of(), refusesTheFirst, Budgets.generation());

        assertEquals(List.of(), filled.unresolved());
        // One row per class owed, which here is one class at each of two positions. What this is
        // about is which values they were written at, and both took the second of each.
        assertEquals(List.of("2, 20", "2, 20"), texts(filled));
    }

    /**
     * Every value refused is not a proof that the combination is impossible.
     *
     * <p>Which is why it is reported as its own reason and is counted as no kind of impossibility:
     * another value of the same two classes may well build, and nothing here has tried one.
     */
    @Test
    void everyCandidateRefusedIsSaidAsItsOwnReason() {
        RuleReadingSource rules = modelOf(TRIP, "submit").rules();
        MeasuredInput subject = twoNumbers(rules, List.of(number("low", 1)),
                List.of(number("high", 10)));

        FillResult filled =
                Generator.fill(subject, List.of(),
                        Generator.CandidateCheck.refusing((_, _) -> Optional.of("no")), Budgets.generation());

        assertEquals(List.of(), filled.rows());
        assertTrue(filled.unresolved().stream().allMatch(left ->
                        left.reason() == Generator.UnresolvedCombination.Reason
                                .ALL_CANDIDATES_REJECTED),
                filled.unresolved().toString());
        assertEquals(List.of(List.of("a=low"), List.of("b=high")),
                filled.unresolved().stream()
                        .map(Generator.UnresolvedCombination::classes).toList(),
                "the class each row was owed for, and not the pair they would have made");
    }

    /** A class nothing can write a value for is still a class, and the row it wants is still owed. */
    @Test
    void aClassWithNoValueIsNamedRatherThanDropped() {
        RuleReadingSource rules = modelOf(TRIP, "submit").rules();
        MeasuredInput subject = twoNumbers(rules,
                List.of(PartitionClass.ungeneratable("opaque", "opaque", new Recognition.Nothing(), "no value")),
                List.of(number("high", 10)));

        FillResult filled =
                Generator.fill(subject, List.of(), Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(List.of(), filled.rows());
        assertTrue(filled.unresolved().stream()
                        .anyMatch(left -> left.classes().contains("a=opaque")),
                filled.unresolved().toString());
    }

    /**
     * What could not be written names what had nothing, not the combinations that wanted it.
     *
     * <p>A position nothing can write a value for makes every combination it takes part in unfillable.
     * Saying so per combination is one fact repeated as many times as the arithmetic allows — 547 lines
     * on one model measured here — and not one of them says which position it was.
     */
    @Test
    void whatHadNoValueIsNamedRatherThanTheCombinationsThatWantedIt() {
        RuleReadingSource rules = modelOf(TRIP, "submit").rules();
        MeasuredInput subject = twoNumbers(rules,
                List.of(PartitionClass.ungeneratable("opaque", "opaque", new Recognition.Nothing(), "no value"),
                        number("low", 1)),
                List.of(number("high", 10), number("higher", 20)));

        FillResult filled =
                Generator.fill(subject, List.of(), Generator.CandidateCheck.ANY, Budgets.generation());

        List<String> subjects = filled.unresolved().stream()
                .map(Generator.UnresolvedCombination::subject).distinct().toList();
        assertEquals(List.of("a=opaque"), subjects,
                "the class with nothing, once — not the three combinations it is in");
        assertEquals(3, filled.rows().size(),
                "the other three classes are still asked for, each standing where `a` can be built");
    }

    /**
     * A sum's record case is composed from its fields, the same as a record a parameter holds.
     *
     * <p>Nothing about it was ever unwritable: a row carrying one is a line an author writes by hand,
     * and the walk that would compose it is the one every other record goes through. What the class
     * could not do is name a value, which is a different thing from there being none — so it names
     * the constructor instead and the composition happens where composition happens.
     */
    @Test
    void aRecordCaseOfASumIsComposedFromItsFields() {
        FillResult filled = Generator.fill(modelOf(PAYMENT, "feeFor").subject(),
                List.of(), Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(List.of(), filled.unresolved(), filled.unresolved().toString());
        assertEquals(List.of("Card { number = Amount(0m) }", "Cash { amount = Amount(0m) }"),
                texts(filled));
    }

    /**
     * An optional whose element is a record is offered a row, which is the value this composes at a
     * parameter of the same type.
     *
     * <p>What {@code Some} stands for is what stands for the element, arrived at the way the row
     * above arrives at a record. Held as values only, the class said it could produce nothing and
     * the position was reported as one no value can be written at (issue #651).
     */
    @Test
    void anOptionalWhoseElementIsARecordIsOfferedARow() {
        FillResult filled = Generator.fill(
                modelOf(OPTIONAL_RECORD, "feeOf").subject(), List.of(),
                Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(List.of(), filled.unresolved(), filled.unresolved().toString());
        assertTrue(texts(filled).contains("Request { card = Card { number = Amount(0m) } }"),
                () -> "the element composed, under the optional: " + texts(filled));
    }

    /**
     * A class that recorded why nothing was composed for it is not reported as one nothing can be
     * written for.
     *
     * <p>The two license different work. "No value can be written there" tells an author the row does
     * not exist; what the class recorded is that this composed nothing here, which may be a row they
     * can write by hand in one line. The class already says which of the two it is, at the point it
     * is built, and the reason carries what it said rather than a category read off an empty list.
     */
    @Test
    void aClassThatSaidWhyNothingWasComposedIsNotReportedAsHavingNoValue() {
        RuleReadingSource rules = modelOf(TRIP, "submit").rules();
        MeasuredInput subject = twoNumbers(rules,
                List.of(PartitionClass.ungeneratable("empty", "empty", new Recognition.Nothing(),
                        "no value this position can hold lies inside this range")),
                List.of(number("high", 10)));

        FillResult filled =
                Generator.fill(subject, List.of(), Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(List.of(), filled.rows(), "nothing was composed at the first position");
        Generator.UnresolvedCombination only = filled.unresolved().getFirst();
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE, only.reason(),
                "a value this could not compose, not one that cannot exist");
        assertEquals(Optional.of("no value this position can hold lies inside this range"),
                only.said(), "the sentence the class recorded, and not one made up here");
    }

    /**
     * One divided position has no pairs at all, and still owes a row for each of its classes.
     *
     * <p>Which is why the two are counted separately rather than one derived from the other. A
     * generator that only filled pairs would write nothing here and leave a report saying a class
     * nothing reaches, with no way to act on it.
     */
    @Test
    void onePositionHasNoPairsAndItsClassesStillOweRows() {
        RuleReadingSource rules = modelOf(TRIP, "submit").rules();
        NumericTerm.ValueOf atA = new NumericTerm.ValueOf(TermPath.of("a"));
        Axis only = new Axis(new AxisId("f", "a"), atA,
                classesOf(List.of(number("low", 1), number("high", 9)), atA), List.of());
        MeasuredInput subject = MeasuredInput.of("f", readingOf(rules, "a"),
                AxesATestWrote.asAMeasurement("f", List.of(only)));

        FillResult filled =
                Generator.fill(subject, List.of(), Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(List.of("1", "9"), texts(filled));
        assertEquals(List.of(List.of("a=low"), List.of("a=high")),
                filled.rows().stream().map(row -> row.labels()).toList());
    }
}
