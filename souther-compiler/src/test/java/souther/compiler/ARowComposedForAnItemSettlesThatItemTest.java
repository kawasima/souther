package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.query.Composition;
import souther.compiler.query.OfferingRequest;
import souther.compiler.query.RowKey;
import souther.compiler.query.Settlement;
import souther.compiler.query.Settlements;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row a search composed for something answers that something when it is read back.
 *
 * <p>The two are separate walks over one model — a search that composes a value at a line, and a
 * reading that asks where a row's values sit — and they have to agree. Where they do not, one of
 * them is wrong about the model and neither says which: a reduction acting on the second would drop
 * a row the first had just written, and the item it was for would go out with nothing offering it.
 *
 * <p><b>A line a declaration owns is the case worth writing down.</b> A behavior's account holds the
 * lines that behavior is owed a row at, and a declaration's lines are none of them — so a reading
 * that looked only there found no reading of a declared line anywhere, and answered that no row
 * stands at one, of rows composed to stand at exactly that.
 */
class ARowComposedForAnItemSettlesThatItemTest {

    /** A module whose only lines are the ones its declarations draw, and no rows written at them. */
    private static final String DECLARED = """
            module example.declared

            data Code = String
                invariant longEnough = String.length(value) >= 4

            data Amount = Int
                invariant within = value >= 1 && value <= 100

            data Ok

            behavior take : (code: Code, amount: Amount) -> Ok

            let take (code, amount) = Ok
            """;

    @Test
    void aRowComposedAtADeclaredLineStandsAtIt() {
        Compilation compilation = Compilation.ofSource(DECLARED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Settlements table = Settlements.of(compilation.db(), composed(compilation));

        assertFalse(table.composedFor().isEmpty(),
                "the declarations draw lines and rows are composed at them: " + table.requested());
        assertTrue(table.composedFor().keySet().stream()
                        .anyMatch(ObligationIdentity.OfALine.class::isInstance),
                "and at least one of them is a point of a line: " + table.composedFor().keySet());
        table.composedFor().forEach((item, row) -> {
            Map<ObligationIdentity, Settlement> here = table.byRow().get(row);
            assertNotNull(here, "the row composed for " + item + " is one this offers: " + row);
            assertInstanceOf(Settlement.Settles.class, here.get(item),
                    "a row composed for " + item + " settles it");
        });
    }

    @Test
    void everyOfferedRowIsAnsweredForAtEveryItem() {
        Compilation compilation = Compilation.ofSource(DECLARED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Settlements table = Settlements.of(compilation.db(), composed(compilation));

        assertFalse(table.byRow().isEmpty(), "there are rows to answer for");
        for (Map.Entry<RowKey, Map<ObligationIdentity, Settlement>> row : table.byRow().entrySet()) {
            for (ObligationIdentity item : table.requested()) {
                assertNotNull(row.getValue().get(item),
                        row.getKey() + " is answered for at " + item);
            }
        }
    }

    /**
     * What the searches composed, before anything asks what the rows settle.
     *
     * <p>What a row was composed for is a fact about the composition. Read off what a person is
     * finally handed, a row answering something another row also answers is not there to be asked
     * about — so the two would agree by one of them being gone.
     */
    private static Composition composed(Compilation compilation) {
        Map<String, Adequacy.Filling> generated =
                Adequacy.generatedOf(compilation.db(), "example.declared");
        assertNotNull(generated, "the model under test compiles");
        return Composition.composed(OfferingRequest.overTheModule("example.declared", true), generated,
                Adequacy.accountFor(compilation.db(), "example.declared",
                        new souther.compiler.query.GenerationScope.Module()),
                Composition.WhatStandsIn.REQUIRING_NOTHING);
    }
}
