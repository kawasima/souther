package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.GenerationScope;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.query.Composition;
import souther.compiler.query.OfferingRequest;
import souther.compiler.query.RowKey;
import souther.compiler.query.Settlements;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row goes only where the offering answers as much without it.
 *
 * <p>What is preserved is what the offering puts in front of a person for each item: a row that
 * settles it, or the row composed for it. Everything the rows offered for before, the rows that are
 * left offer for; and no row of what is left can go and leave that true.
 *
 * <p>The first of those is checked here in the narrower words as well — everything some row settled
 * is still settled — so that the two statements do not both rest on one method. The second reads
 * {@link Settlements#offers}, which is what the reduction preserves: written out again here, this
 * test and the reduction would be two statements of one contract.
 *
 * <p>And what may not be acted on. A row that cannot be told about is not a row that answers, so
 * nothing is dropped on the strength of one — a reduction that counted what it could not tell would
 * drop the row that was the only offer for something.
 */
class WhatTheOfferingAnswersSurvivesDroppingARowTest {

    /**
     * Lines a declaration draws, classes the model divides its positions into, and arms a body
     * takes — the three kinds of thing a row is offered for, in one model with no rows written.
     */
    private static final String MODEL = """
            module example.shipping

            data Total = Int
                invariant value >= 0
                invariant value <= 1000000

            data Membership = Premium | Standard

            data Delivery = Express | Regular

            data Fee = Int
                invariant value >= 0

            behavior shippingFee : (total: Total, member: Membership, delivery: Delivery) -> Fee
                constructs Fee

            let baseFee (total: Total, member: Membership): Int =
                match member with
                    | Premium -> 0
                    | Standard -> if total.value >= 5000 then 0 else 500

            let expressFee (delivery: Delivery): Int =
                match delivery with
                    | Express -> 500
                    | Regular -> 0

            let shippingFee (total, member, delivery) =
                Fee(baseFee(total, member) + expressFee(delivery))
            """;

    @Test
    void everythingSomeRowSettledIsStillSettled() {
        Settlements table = table();
        Set<ObligationIdentity> before = table.settled();
        Set<RowKey> kept = table.keeping();
        assertFalse(kept.isEmpty(), "something is offered: " + table.byRow().keySet());
        for (ObligationIdentity item : before) {
            assertTrue(kept.stream().anyMatch(row -> table.at(row, item).settles()),
                    "a kept row still settles " + item);
        }
    }

    @Test
    void everyItemARowWasComposedForStillHasOneThatAnswersIt() {
        Settlements table = table();
        Set<RowKey> kept = table.keeping();
        table.composedFor().forEach((item, row) -> assertTrue(
                kept.contains(row) || kept.stream().anyMatch(one -> table.at(one, item).settles()),
                "the row composed for " + item + " went, and nothing kept settles it"));
    }

    @Test
    void nothingElseCanGo() {
        Settlements table = table();
        Set<RowKey> kept = table.keeping();
        for (RowKey row : kept) {
            Set<RowKey> without = new LinkedHashSet<>(kept);
            without.remove(row);
            assertTrue(lost(table, kept, without),
                    row + " could go too, so what is left is not irredundant");
        }
    }

    @Test
    void nothingGoesOnWhatCouldNotBeToldAbout() {
        Settlements table = table();
        Set<RowKey> kept = table.keeping();
        for (RowKey row : table.byRow().keySet()) {
            if (kept.contains(row)) {
                continue;
            }
            // What it was dropped for has to be something another kept row settles. A row dropped
            // where the only reading of it was undetermined is a row dropped on not knowing.
            table.composedFor().forEach((item, composed) -> {
                if (composed.equals(row)) {
                    assertTrue(kept.stream().anyMatch(one -> table.at(one, item).settles()),
                            "the row composed for " + item + " went on an answer nobody has");
                }
            });
        }
    }

    /**
     * Whether {@code without} offers less than {@code kept} does.
     *
     * <p>Asked of {@link Settlements#offers}, which is what the reduction preserves — both halves of
     * it, so a row that is the only offer for something it could not be told to settle counts as
     * something lost. Written out here instead, this test and the reduction would be two statements
     * of one contract, and the one that drifted would be the one nobody reads.
     */
    private static boolean lost(Settlements table, Set<RowKey> kept, Set<RowKey> without) {
        for (ObligationIdentity item : table.requested()) {
            if (table.offers(kept, item) && !table.offers(without, item)) {
                return true;
            }
        }
        return false;
    }

    private static Settlements table() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, Adequacy.Filling> generated =
                Adequacy.generatedOf(compilation.db(), "example.shipping");
        assertNotNull(generated, "the model under test compiles: " + compilation.errors());
        Composition composed = Composition.composed(
                OfferingRequest.overTheModule("example.shipping"), generated,
                Adequacy.accountFor(compilation.db(), "example.shipping",
                        new GenerationScope.Module()));
        Settlements table = Settlements.of(compilation.db(), composed);
        assertFalse(table.byRow().isEmpty(), "the model under test is offered rows");
        assertFalse(table.settled().isEmpty(), "and some of them settle something");
        // And something here is redundant, so that what the three tests below hold of the result is
        // held of a result a row was actually taken out of. A model with nothing to drop satisfies
        // every one of them by there being nothing to say.
        assertTrue(table.keeping().size() < table.byRow().size(),
                "the model under test has a row another row answers for: " + table.byRow().size()
                        + " rows, " + table.keeping().size() + " kept");
        return table;
    }
}
