package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Carrier;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.SearchRegion;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A form reads each of its positions on the order that position is written on, and is over
 * positions written back differently where the arithmetic puts them together.
 *
 * <p>A quantity used to answer with the one order every position under it was on. A coordinate has
 * one and a line between two positions has one, so nothing forced the two questions apart — which
 * order a position is read and written on, and which orders a form may be over. A form was held to
 * the same answer, and one over a {@code Decimal} position and an {@code Int} one was refused
 * without a word: the reading returned no quantity, and a rule the model states drew no border.
 *
 * <p><b>And no rule here about which orders may be added.</b> Which positions a form weighs, and
 * with what, is settled before a quantity is built. A refusal written here would be written without
 * the coefficients, and the coefficients are what decides: {@code b + a} over two dates leaves an
 * origin in, {@code b - a - n} cancels it and is a count of days against a number, and the two are
 * the same orders in the same numbers. That second form is what issue #949 asks for, so a rule
 * refusing it is the thing being repaired rather than a guard on the repair.
 */
class AFormAddsPositionsWrittenBackDifferentlyTest {

    private static final String A_DECIMAL_AN_INT_AND_A_DATE = """
            module example.mixed

            data Amount = Decimal
                invariant value >= 0
                invariant value <= 10

            data Steps = Int
                invariant value >= 0
                invariant value <= 10

            data P = { d: Amount, n: Steps, from: Date, to: Date }

            data Yes = { v: Int }

            behavior take : (p: P) -> Yes
                constructs Yes
            let take (p) = Yes { v = 1 }
            """;

    private static NumericTerm value(String field) {
        return new NumericTerm.ValueOf(TermPath.of("p").then(field));
    }

    /** {@code d - n}, the difference of a decimal position and a whole-number one. */
    private static LinearForm<NumericTerm> aDecimalLessAnInt() {
        return new LinearForm<>(BigDecimal.ZERO,
                Map.of(value("d"), BigDecimal.ONE, value("n"), BigDecimal.ONE.negate()));
    }

    /**
     * The form issue #949 exists for: two dates and a whole number, over one quantity.
     *
     * <p>{@code Date.daysBetween(a, b) > n} is {@code b - a - n > 0}, whose positions are read on
     * {@code DATE}, {@code DATE} and {@code WHOLE}. Held here, a rule that the orders under a form
     * must count the same thing refuses it — and it is exactly the form stage four has to build, so
     * the rule would have taken the acceptance of the issue with it.
     *
     * <p>Written as a quantity here rather than read from a source, because nothing composes
     * {@code daysBetween} into a form yet. What it fixes is that this side is ready for it.
     */
    @Test
    void twoDatesAndAWholeNumberAreOneQuantity() {
        LinearForm<NumericTerm> daysBetweenLessN =
                new LinearForm<>(BigDecimal.ZERO,
                        Map.of(value("to"), BigDecimal.ONE,
                                value("from"), BigDecimal.ONE.negate(),
                                value("n"), BigDecimal.ONE.negate()));

        BorderQuantity.OverAForm over = new BorderQuantity.OverAForm("take", daysBetweenLessN,
                Map.of(value("to"), on("to", Carrier.DATE), value("from"), on("from", Carrier.DATE),
                        value("n"), on("n", Carrier.WHOLE)));

        assertEquals(Carrier.DATE, over.carrierOf(value("to")));
        assertEquals(Carrier.WHOLE, over.carrierOf(value("n")));
        assertEquals(souther.compiler.numeric.Granularity.DISCRETE, over.spacing(),
                "days step and whole numbers step, so the difference steps");
    }

    /** A position read and written on one order, which is every position here: what an operation
     *  answered of one is not what these tests are about. */
    private static souther.compiler.inputs.TermOrders on(String field, Carrier carrier) {
        return souther.compiler.inputs.TermOrdersFixtures.itself(value(field), carrier);
    }

    /** And a position with no number under it is one a sum has nothing to add. */
    @Test
    void aPositionWithNoCountIsRefused() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new BorderQuantity.OverAForm("take", aDecimalLessAnInt(),
                        Map.of(value("d"), on("d", Carrier.TEXT), value("n"), on("n", Carrier.WHOLE))))
                .getMessage().contains("no number under it"));
    }

    /** The form the whole of this is about: two positions on two orders. */
    @Test
    void aFormOverTwoDifferentOrdersIsAQuantity() {
        BorderQuantity.OverAForm over = new BorderQuantity.OverAForm("take",
                aDecimalLessAnInt(),
                Map.of(value("d"), on("d", Carrier.DENSE), value("n"), on("n", Carrier.WHOLE)));

        assertEquals(Carrier.DENSE, over.carrierOf(value("d")),
                "the decimal position is read and written as a decimal");
        assertEquals(Carrier.WHOLE, over.carrierOf(value("n")),
                "and the whole-number position as a whole number");
        assertEquals(null, over.carrierOf(value("from")),
                "and a position the form is not over has no order under this quantity");
    }

    /**
     * And its levels are dense, which is the sum's answer and not any one position's.
     *
     * <p>A whole number added to a decimal lands wherever the decimal does. Read off a single order
     * handed to the form, this was whatever that order said — so a form the search had to step
     * through would have been stepped through by ones.
     */
    @Test
    void whatTheSumStepsByIsWhatItsPositionsStepByTogether() {
        BorderQuantity.OverAForm mixed = new BorderQuantity.OverAForm("take",
                aDecimalLessAnInt(),
                Map.of(value("d"), on("d", Carrier.DENSE), value("n"), on("n", Carrier.WHOLE)));
        BorderQuantity.OverAForm whole = new BorderQuantity.OverAForm("take",
                aDecimalLessAnInt(),
                Map.of(value("d"), on("d", Carrier.WHOLE), value("n"), on("n", Carrier.WHOLE)));

        assertEquals(souther.compiler.numeric.Granularity.DENSE, mixed.spacing(),
                "one dense position makes the sum dense");
        assertEquals(souther.compiler.numeric.Granularity.DISCRETE, whole.spacing(),
                "and a form of whole numbers answers what it always did");
    }

    /**
     * An order for a position the form does not name, and a position with no order, are both
     * refused.
     *
     * <p>Two structures are what come apart. The orders are written beside the form and could name
     * a position it does not have, or leave one of its own with nothing to be read on — and a reader
     * meeting the second has a position and no way to read it, which is the answer it would have had
     * to invent.
     */
    @Test
    void aFormAndItsOrdersAreOverTheSamePositions() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new BorderQuantity.OverAForm("take", aDecimalLessAnInt(),
                        Map.of(value("d"), on("d", Carrier.DENSE))))
                .getMessage().contains("read on one order"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new BorderQuantity.OverAForm("take", aDecimalLessAnInt(),
                        Map.of(value("d"), on("d", Carrier.DENSE), value("n"), on("n", Carrier.WHOLE),
                                value("from"), on("from", Carrier.DATE))))
                .getMessage().contains("read on one order"));
    }

    /**
     * And the whole way through: a level of such a form comes back as a row.
     *
     * <p>Through {@link BorderQuantity#standingAt} and {@link LevelRealizer} rather than asserted of
     * the quantity, because what the single order was reaching was the search — which walked every
     * position over the values of whichever order it had been handed. A decimal position walked by
     * whole numbers finds a row at some levels and not at others, and which is which is nothing a
     * reader could have named.
     */
    @Test
    void aLevelOfSuchAFormIsReached() {
        BorderQuantity.OverAForm over = new BorderQuantity.OverAForm("take",
                aDecimalLessAnInt(),
                Map.of(value("d"), on("d", Carrier.DENSE), value("n"), on("n", Carrier.WHOLE)));

        Standing standing = over.standingAt(
                new Criterion.AtTheLevel(new Level.ACount(Count.of(new BigDecimal("2.5")))));

        assertInstanceOf(Standing.OfAForm.class, standing);
        assertEquals(Map.of(value("d"), Carrier.DENSE, value("n"), Carrier.WHOLE),
                ((Standing.OfAForm) standing).on(),
                "the standing carries the order each position's number is measured on, which is"
                        + " what the search writes a value back on");
        assertInstanceOf(Realization.Found.class,
                new LevelRealizer().realize(standing, region()),
                "a difference of two and a half is reached by a decimal and a whole number, and by"
                        + " no two whole numbers");
    }

    /**
     * And a form whose weights put the dense position on a coset, the whole way through.
     *
     * <p>{@code 3d + n} at one is met at {@code d = 0, n = 1}. What the search has to be handed for
     * that level is which decimals {@code d} leave {@code n} a whole number — a progression whose
     * two numbers are thirds and whose members are not. Read off those two numbers, the search was
     * told no value of {@code d} is on the coset at all, and settled the level as one the rules
     * leave nothing at.
     *
     * <p>The acceptance above does not reach it: {@code d - n} weighs {@code d} by one, so the
     * progression it leaves is written in the numbers its members are.
     */
    @Test
    void aFormThatPutsTheDensePositionOnACosetIsStillReached() {
        LinearForm<NumericTerm> thriceD = new LinearForm<>(
                BigDecimal.ZERO,
                Map.of(value("d"), new BigDecimal("3"), value("n"), BigDecimal.ONE));
        BorderQuantity.OverAForm over = new BorderQuantity.OverAForm("take", thriceD,
                Map.of(value("d"), on("d", Carrier.DENSE), value("n"), on("n", Carrier.WHOLE)));

        Realization made = new LevelRealizer().realize(
                over.standingAt(
                        new Criterion.AtTheLevel(new Level.ACount(Count.of(BigDecimal.ONE)))),
                region());

        assertInstanceOf(Realization.Found.class, made,
                "a level the rules leave a row at is not a level nothing stands at");
    }

    private static SearchRegion region() {
        Compilation compilation = Compilation.ofSource(A_DECIMAL_AN_INT_AND_A_DATE, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get("take"), rules, ReadAs.THE_COMPILATION_DOES)
                .quantities(rules).region();
    }
}
