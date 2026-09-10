package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.DeclaredLine;
import souther.compiler.check.InvariantStatementId;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.EndSide;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bound carries which way it keeps its values, and nothing works it back out.
 *
 * <p>Which end a clause placed is settled where the end is read: the ends are read one at a time,
 * so a minimum is known to be a minimum there and nowhere else. What is left to recover it from
 * further down is the range the rules leave the position — and that is every rule's answer and not
 * this one's, so a bound at a value both ends stand at comes back the same for both of them, and a
 * bound at a value neither end stands at comes back as nothing at all.
 *
 * <p>What the range is still worth is a check. Which way the bound runs and where the position stops
 * are two readings of the same rules, and a bound's line is the end of what it leaves on the side it
 * keeps — so the two are held against each other and a model that puts them at odds stops the build.
 */
class ABoundSaysWhichSideItKeepsTest {

    /**
     * A clause leaving one value draws both of its lines there.
     *
     * <p>{@code value >= 5 && value <= 5} places a minimum and a maximum at 5. They are two lines
     * the author wrote, a row at 5 stands at both, and what tells them apart is which side each
     * keeps. Told apart by the range instead, both come back as the minimum: the line is the low end
     * of what the rules leave and it is the high end as well, and one of the two answers is taken.
     */
    @Test
    void aClauseLeavingOneValueDrawsBothOfItsLinesThere() {
        Map<String, List<BorderAssessment>> lines = boundariesOf(ONE_VALUE, "example.one");
        List<BorderAssessment> at = lines.values().stream().flatMap(List::stream).toList();

        assertEquals(2, at.size(),
                () -> "one minimum and one maximum, both at 5: "
                        + at.stream().map(each -> each.border().label()).toList());
        assertEquals(2, at.stream().map(each -> each.border().obligation()).distinct().count(),
                "and two rows to write, one owed to each conjunct of the clause");
        assertEquals(List.of("n = 5", "n = 5"),
                at.stream().map(each -> each.border().label()).toList(),
                "at one value, so nothing about where they are tells them apart");
    }

    /**
     * The side is what the two ends of one conjunct are told apart by.
     *
     * <p>Under the test above, which reads a model: {@code value >= 5 && value <= 5} draws its two
     * lines from two conjuncts, so a reading that carried no side at all would still tell them
     * apart by the conjunct and the model test would pass. What the side is worth is here — one
     * rule, one conjunct, one inclusivity, and two lines.
     */
    @Test
    void theTwoEndsOfOneConjunctAreToldApartByTheSideTheyKeep() {
        LineOrigin least = new LineOrigin.InvariantOrigin(aPart(), EndSide.LOWER, true);
        LineOrigin most = new LineOrigin.InvariantOrigin(aPart(), EndSide.UPPER, true);

        assertNotEquals(least.lineFacts(), most.lineFacts(),
                "which side of the line the value it stops at is on is what the two disagree about");
        assertNotEquals(least.authoredLine(), most.authoredLine(),
                "so they are two lines of the model, and two rows to write");
        assertEquals(least.authoredLine().which().rule(), most.authoredLine().which().rule(),
                "one clause placed both, which is what provenance answers");
    }

    /**
     * A maximum keeps what is below it and a minimum what is above.
     *
     * <p>Read through the point inside the partition each bounds, which is the one thing the side
     * decides for a bound: the run is the values the bound leaves, and it lies one way of the line.
     */
    @Test
    void eachEndKeepsItsOwnSideOfTheLine() {
        Map<String, BorderAssessment> lines = bordersOf(RANGE, "example.range");

        assertEquals("1 < n <= 10", inside(lines, "n = 1"),
                "a minimum keeps what is above it");
        assertEquals("1 <= n < 10", inside(lines, "n = 10"),
                "and a maximum what is below");
    }

    /**
     * A bound whose line is not where what it leaves stops is refused.
     *
     * <p>The reading that placed the end and the reading of what the position is left with are two
     * readings of the same rules, and this is where they meet. A bound at 100 over a run from 1 is a
     * border no model draws — nothing is below a bound — and taken at its word it would offer an
     * {@code ON} point at the line and a point inside covering the values it refuses.
     */
    @Test
    void aBoundThatDoesNotStopWhereItsRangeStopsIsRefused() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> Border.at(aLineAt(100),
                        new LineOrigin.InvariantOrigin(aPart(), EndSide.LOWER, true),
                        new NumericDomain.Bounds(Endpoint.inclusive(Count.of(1)),
                                Endpoint.inclusive(Count.of(1000)))));
        assertTrue(refused.getMessage()
                        .startsWith("a bound whose line is not where what it leaves stops"),
                refused.getMessage());
    }

    /**
     * And so is a maximum held against the end a minimum stands at.
     *
     * <p>The half a check that took either end would pass. Both ends of a range leaving one value
     * are at the line, and a bound is answered for by the end it placed rather than by whichever end
     * happens to match — so a maximum over a range that has no upper end is refused however far its
     * lower end reaches.
     */
    @Test
    void aMaximumIsHeldAgainstTheUpperEndAndNotTheLowerOne() {
        assertThrows(IllegalStateException.class,
                () -> Border.at(aLineAt(5),
                        new LineOrigin.InvariantOrigin(aPart(), EndSide.UPPER, true),
                        new NumericDomain.Bounds(Endpoint.inclusive(Count.of(5)), null)),
                "the line is the low end of what the rules leave, and this bound placed the high"
                        + " one");
        assertEquals("= 5", Border.at(aLineAt(5),
                        new LineOrigin.InvariantOrigin(aPart(), EndSide.LOWER, true),
                        new NumericDomain.Bounds(Endpoint.inclusive(Count.of(5)), null))
                .demand(PointRole.ON).criterion().asked(aLineAt(5).of()),
                "and the minimum that did place it is the border this range draws");
    }

    /** The point inside the partition a border bounds, as the report writes it. */
    private static String inside(Map<String, BorderAssessment> lines, String label) {
        BorderAssessment line = lines.get(label);
        assertNotNull(line, () -> label + " is not a line of this model: " + lines.keySet());
        Border border = line.border();
        return border.label(border.demand(PointRole.IN).criterion());
    }

    /** One position's line at {@code at}, on the carrier whole numbers run on. */
    private static BoundaryTarget aLineAt(int at) {
        Carrier carrier = new Carrier.Whole();
        AxisId axis = new AxisId("f", "n");
        souther.compiler.inputs.NumericTerm.ValueOf term =
                new souther.compiler.inputs.NumericTerm.ValueOf(
                        souther.compiler.inputs.TermPath.of(axis.term()));
        return BoundaryTarget.at(
                new BorderQuantity.OfACoordinate(axis.behavior(), term,
                        souther.compiler.inputs.TermOrdersFixtures.itself(term, carrier)),
                new Level.OnACarrier(carrier, Count.of(at)));
    }

    /** The clause the bounds here name, which is only an identity. */
    private static RuleRef.Invariant aClause() {
        return new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("example.one", "N")), 0),
                Optional.of(new ClauseName("within"))));
    }

    /** The one statement of the one part that clause was written in. */
    private static DeclaredLine aPart() {
        return new DeclaredLine.OfAStatement(
                new InvariantStatementId(new PartId<>(aClause(), 0), 0));
    }

    /** A clause whose two conjuncts leave the position one value. */
    private static final String ONE_VALUE = """
            module example.one

            data N = Int
                invariant within = value >= 5 && value <= 5

            data Ok

            behavior f : (n: N) -> Ok
            let f (n) = Ok

            example f
                | "x" : (N(5)) -> Ok
            """;

    /** The same clause with room between its ends. */
    private static final String RANGE = """
            module example.range

            data N = Int
                invariant within = value >= 1 && value <= 10

            data Ok

            behavior f : (n: N) -> Ok
            let f (n) = Ok

            example f
                | "x" : (N(3)) -> Ok
            """;

    private static Map<String, List<BorderAssessment>> boundariesOf(String model, String module) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), module);
        assertNotNull(boundaries, "the model under test compiles");
        return boundaries;
    }

    private static Map<String, BorderAssessment> bordersOf(String model, String module) {
        Map<String, BorderAssessment> out = new LinkedHashMap<>();
        boundariesOf(model, module).values()
                .forEach(each -> each.forEach(b -> out.put(b.border().label(), b)));
        return out;
    }
}
