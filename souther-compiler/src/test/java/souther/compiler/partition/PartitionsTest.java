package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.Membership;
import souther.compiler.numeric.Count;
import souther.compiler.observe.ObservedValue;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The equivalence classes a model already states.
 *
 * <p>What is being checked as much as anything is the restraint: a position the model draws no line
 * through gets no classes. Splitting an unbounded {@code Int} at zero would measure a rule nobody
 * wrote and then report a gap for not having tested it.
 */
class PartitionsTest {

    private static Partitions.Partitioning partitioningOf(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        assertNotNull(sigs);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.of(behavior, InputDomain.of(sigs.get(behavior), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    private static Axis axis(Partitions.Partitioning partitioning, String path) {
        return partitioning.axes().stream().filter(a -> a.path().toString().equals(path))
                .findFirst().orElseThrow(() -> new AssertionError("no axis at " + path
                        + "; had " + partitioning.axes().stream().map(a -> a.path().toString())
                        .toList()));
    }

    private static List<String> classIds(Axis axis) {
        return axis.classes().stream().map(PartitionClass::id).toList();
    }

    private static final String KINDS = """
            module example.trip

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Amount = Int
                invariant value >= 0 && value <= 1000

            data Note = String

            data Request = { kind: Kind, cost: Amount, urgent: Bool, memo: Note?, note: Note }

            data Accepted = { at: String }

            behavior submit : (request: Request) -> Accepted
                constructs Accepted

            let submit (request) = Accepted { at = "now" }
            """;

    @Test
    void aSumsCasesAreItsClasses() {
        Axis kind = axis(partitioningOf(KINDS, "submit"), "request.kind");

        assertEquals(List.of("Domestic", "Overseas"), classIds(kind));
        assertTrue(kind.classes().stream().allMatch(PartitionClass::generatable),
                "a unit case is written as its own name");
    }

    @Test
    void aBoolIsTwoClassesAndAnOptionalIsTwo() {
        Partitions.Partitioning partitioning = partitioningOf(KINDS, "submit");

        assertEquals(List.of("true", "false"), classIds(axis(partitioning, "request.urgent")));
        assertEquals(List.of("None", "Some"), classIds(axis(partitioning, "request.memo")));
    }

    /** The restraint. A bound refuses everything outside it at construction, so there is no class on
     * the far side to cover — only an edge worth a row. */
    @Test
    void aBoundedNewtypeHasBoundariesAndNoClasses() {
        Axis cost = axis(partitioningOf(KINDS, "submit"), "request.cost");

        assertEquals(List.of(), classIds(cost));
        assertFalse(cost.derivable());
        assertTrue(cost.asksForARow(), "there is still an edge to reach");
        assertEquals(List.of(Count.of(0L), Count.of(1000L)),
                cost.cuts().stream().map(Cut::at).toList());
    }

    /**
     * A rule reaches through as many names as are wrapped round the number.
     *
     * <p>`data StartMinute = Minute` is one the language compares and bounds, and was one the
     * derivation read nothing off — the range reader stopped at the first name and the position came
     * back as one the model draws no line through (#461). Both ends and the rule between them are
     * read at the numbers underneath, so the cuts are the type's edges taken in by the record.
     */
    @Test
    void aBoundReachesThroughEveryNameWrappedRoundTheNumber() {
        Partitions.Partitioning span = partitioningOf("""
                module example.nested

                data Minute = Int
                    invariant withinDay = value >= 0 && value <= 1440

                data StartMinute = Minute
                data EndMinute = Minute

                data Span =
                    { from: StartMinute
                    , to: EndMinute
                    }
                    invariant ordered = from.value < to.value

                data Ok = { at: String }

                behavior classify : (span: Span) -> Ok
                    constructs Ok

                let classify (span) = Ok { at = "x" }
                """, "classify");

        assertEquals(List.of(Count.of(0L), Count.of(1439L)),
                axis(span, "span.from").cuts().stream().map(Cut::at).toList());
        assertEquals(List.of(Count.of(1L), Count.of(1440L)),
                axis(span, "span.to").cuts().stream().map(Cut::at).toList());
        assertEquals(List.of("invariant Minute (withinDay)", "invariant Minute (withinDay) within Span"),
                axis(span, "span.from").cuts().stream()
                        .map(c -> c.origins().get(0).saidWithoutAPlace()).toList(),
                "the rule that drew each end is the one that wrote it, not the outermost name");
    }

    /**
     * Bounds on two of the names a value wears intersect, and each end is owed to the rule that put
     * it there.
     *
     * <p>Neither layer alone says the range is `[0, 10]`. A reader that took the outermost
     * declaration's clauses would answer `[nothing, 10]`, and one that kept a single name per end
     * would drop an obligation where two layers state the same bound.
     */
    @Test
    void boundsOnTwoOfTheNamesIntersectAndBothAreOwed() {
        Partitions.Partitioning wrapped = partitioningOf("""
                module example.wrapped

                data Inner = Int
                    invariant innerMin = value >= 0

                data Outer = Inner
                    invariant outerMin = value >= 0
                    invariant outerMax = value <= 10

                data Ok = { at: String }

                behavior classify : (o: Outer) -> Ok
                    constructs Ok

                let classify (o) = Ok { at = "x" }
                """, "classify");
        Axis o = axis(wrapped, "o");

        assertEquals(List.of(Count.of(0L), Count.of(10L)),
                o.cuts().stream().map(Cut::at).toList());
        assertEquals(List.of("invariant Outer (outerMin)", "invariant Inner (innerMin)"),
                o.cuts().get(0).origins().stream().map(LineOrigin::saidWithoutAPlace).toList(),
                "one value, two rules, and a row is owed to each");
        assertEquals(List.of("invariant Outer (outerMax)"),
                o.cuts().get(1).origins().stream().map(LineOrigin::saidWithoutAPlace).toList());
    }

    /** A `Decimal` under two names reads the same way. */
    @Test
    void theSameHoldsOverADecimalBase() {
        Partitions.Partitioning share = partitioningOf("""
                module example.share

                data Ratio = Decimal
                    invariant withinOne = value >= 0.0m && value <= 1.0m

                data Share = Ratio

                data Ok = { at: String }

                behavior classify : (s: Share) -> Ok
                    constructs Ok

                let classify (s) = Ok { at = "x" }
                """, "classify");

        assertEquals(List.of(Count.of(java.math.BigDecimal.ZERO), Count.of(java.math.BigDecimal.ONE)),
                axis(share, "s").cuts().stream().map(Cut::at).toList(),
                "how many places a literal was written to is not where the line is");
    }

    @Test
    void aTypeTheModelDrawsNoLineThroughIsNotDerivable() {
        Partitions.Partitioning partitioning = partitioningOf(KINDS, "submit");
        PositionMeasurements note = partitioning.measurements().stream()
                .filter(each -> each.position().path().toString().equals("request.note"))
                .findFirst().orElseThrow();

        assertEquals(List.of(), note.axes(), "a plain string is measured at nothing");
        assertTrue(partitioning.undivided().stream()
                        .anyMatch(each -> each.at().toString().equals("request.note")),
                "and the position says so, which is where a report reads it");
    }

    @Test
    void aCutRemembersTheRuleThatDrewIt() {
        Axis cost = axis(partitioningOf(KINDS, "submit"), "request.cost");

        LineOrigin origin = cost.cuts().get(0).origins().get(0);
        LineOrigin.InvariantOrigin invariant =
                org.junit.jupiter.api.Assertions.assertInstanceOf(LineOrigin.InvariantOrigin.class,
                        origin);
        assertEquals("Amount", invariant.rule().clause().id().declaredOn().name());
    }

    /**
     * A record is taken apart, and only so far: two levels reach a field of a record a parameter
     * holds, which is where rules are written.
     *
     * <p>{@code memo@Some} is beside {@code memo} rather than instead of it, and is not a third
     * level. The optional divides into holding something and holding nothing, which is what
     * {@code memo} is measured on; what it holds stands at the narrowing, which is where anything
     * written about that type is read. A narrowing is not a step into the value, so the field is
     * where it was.
     */
    @Test
    void aProductIsTakenApartFieldByField() {
        List<String> paths = partitioningOf(KINDS, "submit").positions().stream()
                .map(a -> a.path().toString()).toList();

        assertEquals(List.of("request.kind", "request.cost", "request.urgent", "request.memo",
                "request.memo@Some", "request.note"), paths);
    }

    @Test
    void classifiersRecogniseTheValuesRowsCarry() {
        Partitions.Partitioning partitioning = partitioningOf(KINDS, "submit");
        Axis kind = axis(partitioning, "request.kind");
        assertEquals(Membership.MATCH, kind.classes().get(0).classifier().membershipOf(
                        new ObservedValue.Unit(TypeSymbols.declared(new TypeKey("example.trip", "Domestic")))),
                "a unit case is recognised by the type it names");

        Axis urgent = axis(partitioning, "request.urgent");
        assertEquals(Membership.MATCH,
                urgent.classOf("true").classifier().membershipOf(new ObservedValue.Bool(true)));
        assertEquals(Membership.NO_MATCH,
                urgent.classOf("true").classifier().membershipOf(new ObservedValue.Bool(false)));

        Axis memo = axis(partitioning, "request.memo");
        assertEquals(Membership.MATCH,
                memo.classOf("None").classifier().membershipOf(new ObservedValue.Absent()));
        assertEquals(Membership.NO_MATCH,
                memo.classOf("None").classifier().membershipOf(new ObservedValue.Text("x")));
    }

    /**
     * The shipping-fee example from smdd-book chapter 8: two bands the model names, so two axes of two
     * classes each. The 2999/3000 boundary the book also derives comes from the threshold the rule
     * compares against, which is read from the behavior's body rather than from these types.
     */
    @Test
    void theBandsAModelNamesAreItsClasses() {
        Partitions.Partitioning shipping = partitioningOf("""
                module example.shipping

                data UnderThreeThousand
                data ThreeThousandOrOver
                data AmountBand = UnderThreeThousand | ThreeThousandOrOver

                data Remote
                data NotRemote
                data RegionBand = Remote | NotRemote

                data Fee = { yen: Int }

                behavior feeFor : (amount: AmountBand, region: RegionBand) -> Fee
                    constructs Fee

                let feeFor (amount, region) = Fee { yen = 0 }
                """, "feeFor");

        assertEquals(2, shipping.partitionAxes().size());
        assertEquals(List.of("UnderThreeThousand", "ThreeThousandOrOver"),
                classIds(axis(shipping, "amount")));
        assertEquals(List.of("Remote", "NotRemote"), classIds(axis(shipping, "region")));
    }

    /**
     * Every position the model divides is an axis, however many of them there are.
     *
     * <p>How many positions a behavior takes is not a measure of what measuring them costs. Fifteen
     * {@code Bool}s and two are the same work per position, and what the pair space and the row
     * search cost is bounded where each of them is walked — so a count of positions taken as a
     * budget bounds the evidence and not the work (see this package's documentation).
     *
     * <p>Fifteen rather than thirteen so the number is not the one a limit was written at. A test
     * written at the old ceiling plus one passes again the moment somebody sets a new ceiling one
     * higher, which is the reintroduction it exists to catch.
     */
    @Test
    void everyPositionTheModelDividesIsAnAxisHoweverManyThereAre() {
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            fields.append("f").append(i).append(": Bool, ");
        }
        String wide = """
                module example.wide

                data Wide = { %s }
                data Done = { at: String }

                behavior run : (wide: Wide) -> Done
                    constructs Done

                let run (wide) = Done { at = "now" }
                """.formatted(fields.substring(0, fields.length() - 2));

        Partitions.Partitioning partitioning = partitioningOf(wide, "run");

        assertEquals(15, partitioning.partitionAxes().size(),
                () -> "every field is divided: " + partitioning.partitionAxes().stream()
                        .map(each -> each.id().toString()).toList());
        assertEquals("run/wide.f14",
                partitioning.partitionAxes().get(14).id().toString(),
                "including the last, which no ordering may quietly leave out");
    }
}
