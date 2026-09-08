package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.Membership;
import souther.compiler.observe.Classification;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.RowOutcome;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.query.Shapes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Why a row could not be placed at a position, and who is entitled to say.
 *
 * <p>A classifier may read through the value it is given: a number at a position is the number
 * inside the newtype named there, because a newtype is not a step in a path. So the value the walk
 * finds and the value a class is about are not always the same node, and only the classifier knows
 * which one it read.
 *
 * <p>Held here because the difference is invisible half the time. A limit reached one wrapper in
 * used to come back as a value that could not be read — right about the row and wrong about what
 * happened to it — while the same failure at the node itself came back correctly, and an unreadable
 * value one wrapper in came back correctly by accident. Which is why nothing noticed.
 */
class WhyAValueCouldNotBePlacedIsTheClassifiersToSayTest {

    private static final String MODEL = """
            module example.trip

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Amount = Int
                invariant value >= 0

            data Request = { kind: Kind, cost: Amount, plain: Int }
            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (request: Request) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (request) = {
                guard request.cost.value <= 100 else Waiting { cost = request.cost }
                guard request.plain <= 10 else Waiting { cost = request.cost }
                Submitted { cost = request.cost }
            }

            example submit
                | (Request { kind = Domestic, cost = Amount(50), plain = 1 }) -> Submitted
            """;

    private record Read(MeasuredInput subject, RowOutcome row) {

        MeasuredInput.MeasuredAxes axes() {
            return subject.axes();
        }
    }

    private static Read read() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                .filter(b -> b.name().equals("submit")).findFirst().orElseThrow();
        Core body = checked.behaviorBodies().get("submit");
        CoverageSites.Plan plan = checked.plan();
        InputDomain read = InputDomain.of(spec, sigs.get("submit"), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning = Partitions.withThresholds(
                Partitions.of(spec.name(), read, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                read.quantities(rules),
                GuardThresholds.of("submit", checked.analysisBodies().get("submit"), body, plan,
                compilation.db().ask(new souther.compiler.query.Adequacy.Inputs(module)).value().get("submit"), rules).thresholds(), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES,
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS));
        Output.Examples.Of observed = compilation.db()
                .ask(Output.Examples.asked(compilation.db(), module,
                        compilation.sourceIds().get(0))).value();
        assertNotNull(observed);
        return new Read(MeasuredInput.of("submit", read.reading(rules), partitioning),
                observed.rows().get(0));
    }

    /** The row it read, with one of the request's fields replaced. */
    private static RowOutcome giving(Read read, String field, ObservedValue value) {
        ObservedValue.Constructed request = (ObservedValue.Constructed) read.row().inputs().get(0);
        Map<String, ObservedValue> fields = new LinkedHashMap<>(request.fields());
        fields.put(field, value);
        return new RowOutcome(read.row().at(), read.row().target(), read.row().identity(),
                read.row().expectation(),
                read.row().stage(), read.row().disposition(), read.row().failurePhase(),
                read.row().expectedArm(), read.row().resultArm(), read.row().inputCases(),
                List.of(new ObservedValue.Constructed(request.type(), fields)),
                statingTheSame(read.row(),
                        List.of(new ObservedValue.Constructed(request.type(), fields))),
                read.row().run());
    }

    /**
     * What a row with {@code inputs} states, which is not always what the row it came from stated.
     *
     * <p>A row whose input the observation stopped in states no values — it states that one of them
     * could not be carried — so a fixture that changed the values while keeping what the original
     * stated would be a row saying two different things about what it handed over.
     */
    private static souther.compiler.observe.RowStatement statingTheSame(RowOutcome row,
                                                                        List<ObservedValue> inputs) {
        return row.statement() instanceof souther.compiler.observe.RowStatement.Stated stated
                ? souther.compiler.observe.RowStatements.read(List.of(), inputs, stated.expects())
                : row.statement();
    }

    /** The newtype the row wrote at {@code cost}, holding {@code inner} where its number was. */
    private static ObservedValue wrapping(Read read, ObservedValue inner) {
        ObservedValue.Constructed request = (ObservedValue.Constructed) read.row().inputs().get(0);
        ObservedValue.Constructed amount = (ObservedValue.Constructed) request.field("cost");
        Map<String, ObservedValue> value = new LinkedHashMap<>(amount.fields());
        value.put("value", inner);
        return new ObservedValue.Constructed(amount.type(), value);
    }

    private static Classification at(Read read, RowOutcome row, String path) {
        Map<AxisId, Classification> classes = InputClassifications.of(row.inputs(), read.axes());
        return classes.entrySet().stream().filter(e -> e.getKey().term().equals(path))
                .map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new AssertionError("no axis at " + path));
    }

    private static Incompleteness.Code why(Classification where) {
        return assertInstanceOf(Classification.Unclassified.class, where).reason().code();
    }

    @Test
    void aValueTheLimitStoppedSaysSoWhereItIsTheValueItself() {
        Read read = read();

        assertEquals(Incompleteness.Code.VALUE_TRUNCATED,
                why(at(read, giving(read, "plain", new ObservedValue.Truncated()), "request.plain")));
    }

    @Test
    void aValueTheWalkCouldNotReadSaysSoWhereItIsTheValueItself() {
        Read read = read();

        assertEquals(Incompleteness.Code.VALUE_UNREADABLE,
                why(at(read, giving(read, "plain", new ObservedValue.Unknown("gone")),
                        "request.plain")));
    }

    /** The one this issue is about. A newtype is not a step, so the limit was reached at a node the
     * walk never names — and the class that reads through it is the only thing that knows. */
    @Test
    void aValueTheLimitStoppedInsideANewtypeSaysSoTo() {
        Read read = read();

        assertEquals(Incompleteness.Code.VALUE_TRUNCATED,
                why(at(read, giving(read, "cost", wrapping(read, new ObservedValue.Truncated())),
                        "request.cost")));
    }

    @Test
    void aValueTheWalkCouldNotReadInsideANewtypeSaysSoTo() {
        Read read = read();

        assertEquals(Incompleteness.Code.VALUE_UNREADABLE,
                why(at(read, giving(read, "cost", wrapping(read, new ObservedValue.Unknown("gone"))),
                        "request.cost")));
    }

    /** A class told apart by shape says it too, which is where that test moved to. */
    @Test
    void aShapeClassSaysWhenTheObservationWasStopped() {
        Read read = read();

        assertEquals(Incompleteness.Code.VALUE_TRUNCATED,
                why(at(read, giving(read, "kind", new ObservedValue.Truncated()), "request.kind")));
        assertEquals(Incompleteness.Code.VALUE_UNREADABLE,
                why(at(read, giving(read, "kind", new ObservedValue.Unknown("gone")),
                        "request.kind")));
    }

    /**
     * A value that was read and is not a number is a class this position does not hold.
     *
     * <p>Not an incompleteness. `VALUE_UNREADABLE` is a value that could not be read back into an
     * observed form at all, and a `Text` was read — it is the partition that does not fit it. So the
     * classes all decline, and declining together is the contract below, not a row nobody could
     * read.
     */
    @Test
    void aReadableValueThatIsNotANumberIsNotAnIncompleteness() {
        Read read = read();
        RowOutcome row = giving(read, "plain", new ObservedValue.Text("x"));

        assertThrows(IllegalStateException.class,
                () -> InputClassifications.of(row.inputs(), read.axes()));
    }

    /** And a value every class could read still lands where it did. */
    @Test
    void aReadableValueIsStillPlacedInTheClassThatHoldsIt() {
        Read read = read();

        assertEquals(Classification.in("request.cost/0 <= x <= 100"),
                at(read, read.row(), "request.cost"));
        assertEquals(Classification.in("request.plain/x <= 10"),
                at(read, read.row(), "request.plain"));
        assertEquals(Classification.in("Domestic"),
                at(read, read.row(), "request.kind"));
    }

    // --- what the contract says cannot happen ---------------------------------------------------

    /**
     * The answers of an axis's classes, as {@link InputClassifications} reads them.
     *
     * <p>The answers and not classes that would give them. What a class recognises is a {@link
     * Recognition} and every one of them reads a value the same way, so none of the three states
     * below can be reached by building classes — which is the point of the rule and also why the
     * rule has to be asked directly. Asked through classes instead, these would be tests of what
     * this compiler can no longer produce rather than of what it does when handed it.
     */
    private static souther.compiler.observe.Classification answering(Membership... answers) {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < answers.length; i++) {
            ids.add("c" + i);
        }
        return InputClassifications.decided(new AxisId("submit", "request.plain"),
                ids, List.of(answers), null);
    }

    /**
     * A class that holds the value wins over classes that could not read it, wherever it sits.
     *
     * <p>One class saying it could not read says nothing about the rest — that is why the answers
     * are collected rather than returned on — and it says nothing about whether one of them holds
     * the value either. An incompleteness is what is left once nothing has claimed it, so it cannot
     * be acted on before every class has answered.
     */
    @Test
    void aClassThatHoldsTheValueWinsOverOnesThatCouldNotReadIt() {
        assertEquals(Classification.in("c2"), answering(
                new Membership.Incomplete(Incompleteness.Code.VALUE_TRUNCATED),
                new Membership.Incomplete(Incompleteness.Code.VALUE_UNREADABLE),
                Membership.MATCH));
    }

    /**
     * A readable value in none of the classes is the partition's contract broken.
     *
     * <p>{@code Axis} says its classes are exclusive and exhaustive over the position's values, so a
     * value every class read and none holds is not a third thing a row can be. Reporting it as a row
     * that could not be read would answer a defect in the partition with a measurement failure, and
     * the report would name the row.
     */
    @Test
    void everyClassReadingTheValueAndNoneHoldingItIsNotAnAnswer() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> answering(Membership.NO_MATCH, Membership.NO_MATCH));
        org.junit.jupiter.api.Assertions.assertTrue(
                thrown.getMessage().contains("holds a value it read"), thrown.getMessage());
    }

    /**
     * And two classes disagreeing about why they could not read it is the same kind of thing.
     *
     * <p>One value, two readings of whether it is there. Picking between them would be inventing a
     * precedence for a question nothing has asked: today every class of a numeric position reads
     * through the same reader and they cannot disagree.
     */
    @Test
    void twoClassesDisagreeingAboutWhyIsNotAnAnswerEither() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> answering(
                        new Membership.Incomplete(Incompleteness.Code.VALUE_TRUNCATED),
                        new Membership.Incomplete(Incompleteness.Code.VALUE_UNREADABLE)));
        org.junit.jupiter.api.Assertions.assertTrue(
                thrown.getMessage().contains("disagree about why"), thrown.getMessage());
    }
}
