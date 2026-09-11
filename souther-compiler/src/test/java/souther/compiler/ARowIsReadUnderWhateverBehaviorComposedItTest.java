package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PointResolution;
import souther.compiler.query.BorderAccount;
import souther.compiler.query.GenerationScope;
import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.query.Composition;
import souther.compiler.query.OfferingRequest;
import souther.compiler.query.RowKey;
import souther.compiler.query.Settlement;
import souther.compiler.query.Settlements;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row is read under the behavior that composed it, whatever else that behavior was asked.
 *
 * <p>A line a declaration draws is owed a row once over every behavior carrying the type, and the
 * row is composed by whichever reading could compose it. That behavior need not be one anything else
 * was asked about — an offering holds the row under it either way, which is what
 * {@link Composition#composed} says in as many words.
 *
 * <p>Read off the searches instead, such a row has no reading at all: every question put to it comes
 * back as one nothing could tell about, so nothing it stands at can make another row redundant and
 * the two go out as two pieces of work. Which is this issue's own defect, on the side nobody
 * measured.
 */
class ARowIsReadUnderWhateverBehaviorComposedItTest {

    private static final String DECLARED = """
            module example.carried

            data Code = String
                invariant longEnough = String.length(value) >= 4

            data Amount = Int
                invariant within = value >= 1 && value <= 100

            data Ok

            behavior take : (code: Code, amount: Amount) -> Ok

            let take (code, amount) = {
                guard amount.value > 50 else Ok
                Ok
            }
            """;

    @Test
    void aRowComposedByABehaviorNothingElseWasAskedOfIsStillRead() {
        Compilation compilation = Compilation.ofSource(DECLARED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        BorderAccount declared = Adequacy.accountFor(compilation.db(),
                "example.carried", new GenerationScope.Module());
        assertFalse(declared.rowsByCarrier().isEmpty(),
                "a declaration's line is owed a row and one behavior composed it: " + declared);

        // The offering a run makes when nothing was asked of the carrier itself. Which is the state
        // `Composition.composed` is written for, and the one a reader off the searches cannot read.
        Composition composed = Composition.composed(
                OfferingRequest.overTheModule("example.carried", true), Map.of(), declared,
                Composition.WhatStandsIn.REQUIRING_NOTHING);
        assertFalse(composed.rowsByBehavior().isEmpty(), "the row is offered under its carrier: " + composed);
        for (String carrier : composed.rowsByBehavior().keySet()) {
            assertFalse(composed.searched().containsKey(carrier),
                    "and nothing was searched for that behavior: " + carrier);
        }

        Settlements table = Settlements.of(compilation.db(), composed);
        assertFalse(table.byRow().isEmpty(), "the row is in the table");
        assertFalse(table.composedFor().isEmpty(),
                "and something was composed for: " + table.requested());
        table.composedFor().forEach((item, row) -> {
            Map<ObligationIdentity, Settlement> here = table.byRow().get(row);
            assertNotNull(here, "the row composed for " + item + " is one this offers");
            assertInstanceOf(Settlement.Settles.class, here.get(item),
                    "a row composed by a behavior nothing else was asked of settles what it was"
                            + " composed for");
        });
    }

    /**
     * And nothing this run did not ask of it comes back as work.
     *
     * <p>The behavior draws a line of its own, and a run that asked it for nothing has no search of
     * it: what it holds is a row somebody else's line needed. Asked for the search while reading
     * that row, the run would make the search it had decided not to make, and the behavior's own
     * points would arrive as items nobody was set — which is a candidate standing at one of them
     * becoming the only offer for work nobody asked for.
     */
    @Test
    void andNothingOfItsOwnIsAskedForWhereNothingWasAskedOfIt() {
        Compilation compilation = Compilation.ofSource(DECLARED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        BorderAccount declared = Adequacy.accountFor(compilation.db(),
                "example.carried", new GenerationScope.Module());
        Composition composed = Composition.composed(
                OfferingRequest.overTheModule("example.carried", true), Map.of(), declared,
                Composition.WhatStandsIn.REQUIRING_NOTHING);
        Settlements table = Settlements.of(compilation.db(), composed);

        // The behavior has lines of its own, so this says something.
        assertFalse(Adequacy.generatedOf(compilation.db(), "example.carried").isEmpty(),
                "the model under test has a behavior with a search of its own to be asked for");

        Set<BorderObligationPoint> asked = new LinkedHashSet<>();
        for (ObligationIdentity item : table.requested()) {
            if (item instanceof ObligationIdentity.OfALine(var point)) {
                asked.add(point);
            }
        }
        assertEquals(new LinkedHashSet<>(declared.resolved().keySet().stream()
                        .filter(point -> !(declared.resolved().get(point).resolution()
                                instanceof PointResolution.NoSearch))
                        .toList()),
                asked,
                "only the declarations' points are asked about, and none of the behavior's own");
    }

    @Test
    void andWhatItSettlesCanMakeAnotherRowRedundant() {
        Compilation compilation = Compilation.ofSource(DECLARED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        BorderAccount declared = Adequacy.accountFor(compilation.db(),
                "example.carried", new GenerationScope.Module());
        Composition composed = Composition.composed(
                OfferingRequest.overTheModule("example.carried", true), Map.of(), declared,
                Composition.WhatStandsIn.REQUIRING_NOTHING);
        Settlements table = Settlements.of(compilation.db(), composed);

        // Nothing here is undetermined for want of a reading. What a run cannot tell about is a
        // value it could not build or a run nobody watched, and neither is what a carrier without a
        // search of its own is.
        long settles = 0;
        for (Map.Entry<RowKey, Map<ObligationIdentity, Settlement>> row : table.byRow().entrySet()) {
            settles += row.getValue().values().stream().filter(Settlement::settles).count();
        }
        assertTrue(settles > 0, "the rows settle something: " + table.byRow());

        for (var each : declared.resolved().entrySet()) {
            if (!(each.getValue().resolution() instanceof PointResolution.Generated(var by,
                    var row))) {
                continue;
            }
            ObligationIdentity item = new ObligationIdentity.OfALine(each.getKey());
            assertTrue(table.offers(table.keeping(), item),
                    "what the carrier's row was composed for is offered after the reduction: "
                            + item + " " + by + " " + row);
        }
    }
}
