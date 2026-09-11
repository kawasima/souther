package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.OfferingRequest;
import souther.compiler.report.GeneratedRows;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule about how many a value holds reaches the row a class is offered.
 *
 * <p>One model, run the whole way. What the choice of a value comes to over the combinations is
 * asked where the choice is made ({@code AValueStandingForEverythingElseIsOneThePositionAdmitsTest})
 * — every case there is a reading of the declarations, a measure, a search and a rendering away
 * from a row, and a failure among them says which of the four it was by having been asked there.
 * What is left for a model is whether the answer arrives: the position's admitted values have to
 * reach the class that needs them, and nothing below the report says whether they did.
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

    /** The block a person is handed for the one module in {@code source}. */
    private static String rowsFor(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        souther.compiler.query.Offering offering = Adequacy.offeredFor(compilation.db(),
                OfferingRequest.overTheModule("example.away", false));
        assertNotNull(offering, "the model under test compiles");
        return GeneratedRows.of(offering, Map.of(), SourceRendering.namedByIdentity(compilation.texts()),
                compilation.db()).text();
    }

    /** The class away from the value the body singled out is offered a row, and the row builds. */
    @Test
    void theClassAwayFromASingledStringIsOfferedARowThatBuilds() {
        String rows = rowsFor(AT_LEAST_ONE);

        assertTrue(rows.contains("\"voucher=/= spring\""),
                () -> "the class away from the singled value is offered a row:\n" + rows);
        // The note is what a class offered a value the declarations refuse comes to: the row is
        // composed, the construction fails, and a reader is told the combination was not reached.
        assertFalse(rows.contains("no row for `voucher=/= spring`"), () -> rows);
        assertFalse(rows.contains("Voucher(\"\")"),
                () -> "nothing of no length is a voucher:\n" + rows);
    }
}
