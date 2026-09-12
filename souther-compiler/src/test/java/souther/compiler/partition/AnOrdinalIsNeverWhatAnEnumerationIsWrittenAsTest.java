package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.inputs.BoundaryDomain;
import souther.compiler.numeric.Count;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An enumeration's count is an ordinal, and no reader outside the carrier ever sees one.
 *
 * <p>The count of the other four carriers is a number a model could plausibly have written — a day
 * count is a six-figure integer, a second count a ten-figure one, and both look wrong at a glance in
 * a report. An ordinal is 0, 1, 2. Leaked into a row it is a value the position does not hold and
 * reads like one that does; leaked into a report it names a line at a number that appears nowhere in
 * the model. It is the carrier whose leak is hardest to see, which is why it is the one held here.
 *
 * <p>What a line on an enumeration owes is asked where every other carrier's is, in
 * {@code ThresholdNormalizationTest}: an obligation is what a guard's comparison leaves, and the
 * harness that reads one is there.
 */
class AnOrdinalIsNeverWhatAnEnumerationIsWrittenAsTest {

    private static final TypeSymbol.AtModule STAGE = TypeSymbols.declared(new TypeKey("example.stage", "Stage"));

    private static Carrier.Ordinal carrier() {
        return new Carrier.Ordinal(STAGE, List.of(
                TypeSymbols.declared(new TypeKey("example.stage", "Prospecting")),
                TypeSymbols.declared(new TypeKey("example.stage", "Qualified")),
                TypeSymbols.declared(new TypeKey("example.stage", "Won"))));
    }

    /** The counts are the places, and the values are the cases. Neither is the other. */
    @Test
    void aCaseCountsToWhereItIsDeclaredAndComesBackAsItself() {
        Carrier.Ordinal stage = carrier();

        assertEquals(Count.of(0), stage.placeOf(
                new ObservedValue.Unit(TypeSymbols.declared(new TypeKey("example.stage", "Prospecting")))));
        assertEquals(Count.of(2), stage.placeOf(
                new ObservedValue.Unit(TypeSymbols.declared(new TypeKey("example.stage", "Won")))));
        assertEquals(new ObservedValue.Unit(TypeSymbols.declared(new TypeKey("example.stage", "Qualified"))),
                stage.valueOf(Count.of(1)));
        assertEquals("Qualified", stage.written(Count.of(1)));
    }

    /**
     * A case of some other sum is not a place on this order.
     *
     * <p>Null and not zero. Read as a count, a case this enumeration does not list would land at
     * whatever index it happened to take elsewhere, and the row would be labelled for a class of a
     * type it is not a value of.
     */
    @Test
    void aCaseThisEnumerationDoesNotListHasNoPlaceOnIt() {
        assertNull(carrier().placeOf(new ObservedValue.Unit(TypeSymbols.declared(new TypeKey("elsewhere", "Won")))));
        assertNull(carrier().placeOf(new ObservedValue.Integer(1)),
                "a number is not a case, whatever the ordinal of a case happens to be");
    }

    /** An enumeration stops where its cases stop, so nothing steps off either end of it. */
    @Test
    void anOrdinalPastTheLastCaseIsNotOnTheOrder() {
        Carrier.Ordinal stage = carrier();

        assertEquals(Count.of(2), stage.onTheGrid(Count.of(2)));
        assertNull(stage.onTheGrid(Count.of(3)));
        assertNull(stage.onTheGrid(Count.of(-1)));
        assertTrue(BoundaryDomain.on(stage).successor(Count.of(2)).isEmpty(),
                "there is nothing after the last case to ask for a row at");
        assertEquals(java.util.Optional.of(Count.of(2)),
                BoundaryDomain.on(stage).successor(Count.of(1)));
    }

    /**
     * A position declared as one case is not on the enumeration's order.
     *
     * <p>Which order two operands can be compared on is a wider question than which counts a
     * position ranges over, and the two have different answers: `Qualified < Won` compares on
     * `Stage`, and a position declared as `Qualified` holds one value. Answered with the wider one,
     * the position took the whole enumeration's counts and the line drawn on it asked for a row at
     * `Won` — a value that position cannot hold.
     */
    @Test
    void aPositionDeclaredAsOneCaseIsNotOnTheEnumerationsOrder() {
        souther.compiler.query.Compilation compilation =
                souther.compiler.query.Compilation.ofSource("""
                        module example.onecase

                        data Prospecting
                        data Qualified
                        data Won
                        data Stage = Prospecting | Qualified | Won

                        data Ok
                        data No
                        data Verdict = Ok | No

                        behavior f : (s: Qualified) -> Verdict
                        let f (s) = { guard s < Won else Ok
                            No }
                        """, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        souther.compiler.check.Symbols symbols =
                souther.compiler.query.Scopes.derived(compilation.db(), module).value();

        assertNull(Carrier.ofValue(
                souther.compiler.types.Type.ref(TypeSymbols.declared(new TypeKey("example.onecase", "Qualified"))),
                symbols, souther.compiler.query.Shapes.declarationKinds(compilation.db()),
                souther.compiler.query.Shapes.publishedDeclarations(compilation.db())),
                "one case of a sum is not the sum");
        assertNotNull(Carrier.ofValue(
                souther.compiler.types.Type.ref(TypeSymbols.declared(new TypeKey("example.onecase", "Stage"))),
                symbols, souther.compiler.query.Shapes.declarationKinds(compilation.db()),
                souther.compiler.query.Shapes.publishedDeclarations(compilation.db())),
                "and the sum itself still is");
    }

    /** A row writes the case, which is what naming it builds. */
    @Test
    void aRowAtAnOrdinalCarriesTheCaseAndNotItsPlace() {
        FixtureTemplate written = FixtureTemplate.on(carrier(), Count.of(1),
                souther.compiler.types.TypeReachName.Bare::new);

        assertEquals("Qualified", written.text());
        assertInstanceOf(souther.compiler.ast.Hir.Var.class, written.value(),
                "naming a case is constructing it");
    }
}
