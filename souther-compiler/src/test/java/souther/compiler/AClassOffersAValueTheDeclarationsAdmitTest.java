package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.OfferingRequest;
import souther.compiler.report.GeneratedRows;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The value a class offers is one the declarations leave the position, whichever vocabulary the
 * classes were composed in.
 *
 * <p>A body singling a value out divides the position on the order the values are counted on, and
 * the class for everything away from that value has to name something to stand for it. Worked out
 * from where the rules leave that number, the answer knows nothing of a rule about another number
 * of the same place: a string bounded below by its length is offered the empty string, and what
 * says so is a construction that fails rather than the values the position was said to admit.
 *
 * <p>Read off the rows a person is handed, because that is where the value has to survive: a class
 * offering a value the declarations refuse still looks like a class with a representative, and the
 * only thing that tells the two apart is whether anything can be built out of it.
 */
class AClassOffersAValueTheDeclarationsAdmitTest {

    /** A string singled out by the body, and a rule on how many its value holds. */
    private static final String AT_LEAST_ONE = """
            module example.away

            data Voucher = String
                invariant String.length(value) >= 1

            data Redeemed = { voucher: Voucher }
            data NotThisVoucher

            behavior redeem : (voucher: Voucher) -> Redeemed | NotThisVoucher
                constructs Redeemed

            let redeem (voucher) = {
                guard voucher.value == "spring" else NotThisVoucher
                Redeemed { voucher = voucher }
            }
            """;

    /** The same with both ends, so it is the run the rule leaves and not the one end that is read. */
    private static final String A_BAND = AT_LEAST_ONE.replace(
            "invariant String.length(value) >= 1",
            "invariant String.length(value) >= 2 && String.length(value) <= 3");

    /**
     * And the value singled out being the first one the set has to offer.
     *
     * <p>The one string of a length the rule allows that this compiler reaches for first is the
     * tab, which is what the rows above are written with. Singled out, it is the value the class
     * exists to exclude — so a reader that takes a value out of the set and refuses it afterwards
     * has nothing left to offer, while the strings the declarations leave in the class are without
     * end.
     */
    private static final String THE_FIRST_ONE_OFFERED =
            AT_LEAST_ONE.replace("== \"spring\"", "== \"\\t\"");

    /** The block a person is handed for the one module in {@code source}. */
    private static String rowsFor(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        souther.compiler.query.Offering offering = Adequacy.offeredFor(compilation.db(),
                OfferingRequest.overTheModule("example.away", false));
        assertNotNull(offering, "the model under test compiles");
        return GeneratedRows.of(offering, Map.of(), SourceNameResolver.identity(),
                compilation.db()).text();
    }

    /** A rule on the length is a rule about another number of the same place, and it reaches the
     *  value the class away from the singled one is offered. */
    @Test
    void theClassAwayFromASingledStringIsOfferedOneTheLengthRuleLeaves() {
        String rows = rowsFor(AT_LEAST_ONE);

        assertTrue(rows.contains("\"voucher=/= spring\""),
                () -> "the class away from the singled value is offered a row:\n" + rows);
        assertFalse(rows.contains("no row for `voucher=/= spring`"), () -> rows);
        // What the old answer offered. Said here as well as above, so that the day the note is
        // worded differently this is still a test about which value was written.
        assertFalse(rows.contains("Voucher(\"\")"),
                () -> "nothing of no length is a voucher:\n" + rows);
    }

    /** And a run with both ends, which the empty string is outside of the same way a value one
     *  character long is. */
    @Test
    void theSameClassUnderABandIsOfferedAValueInsideIt() {
        String rows = rowsFor(A_BAND);

        assertTrue(rows.contains("\"voucher=/= spring\""),
                () -> "the class away from the singled value is offered a row:\n" + rows);
        assertFalse(rows.contains("no row for `voucher=/= spring`"), () -> rows);
        assertFalse(rows.contains("Voucher(\"\")"), () -> rows);
    }

    /**
     * The value looked for is one of what the class holds, and not one of what the position admits
     * that the class is then asked about.
     *
     * <p>Here the two come apart: the value the set has first to offer is the one the body singled
     * out. Taken out of the set before the value is looked for, the strings left are without end
     * and one of them stands for the class; taken out afterwards, there is nothing left to offer
     * and a class the declarations leave inhabited says nothing stands for it.
     */
    @Test
    void theValueSingledOutIsTakenOutBeforeOneIsLookedFor() {
        String rows = rowsFor(THE_FIRST_ONE_OFFERED);

        assertTrue(rows.contains("\"voucher=/= \\t\""),
                () -> "the class away from the singled value is offered a row:\n" + rows);
        assertFalse(rows.contains("no row for `voucher=/= \\t`"), () -> rows);
        assertFalse(rows.contains("Voucher(\"\")"), () -> rows);
    }
}
