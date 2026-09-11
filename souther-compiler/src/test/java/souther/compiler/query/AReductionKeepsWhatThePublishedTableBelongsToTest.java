package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.types.ValueName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row the block's table belongs to is not one the offering offers as much without.
 *
 * <p>What a reduction preserves used to be the items a row settles and nothing else. A row that
 * answers a dependency by what it was applied to brings more than that: it brings the table the
 * block writes beside the rows, which is the environment that row was run in. Reduced by the items
 * alone, such a row goes the moment another row settles what it settled — and the block goes on
 * publishing a table nothing left in it runs against, while the row held back for wanting a
 * different one stays held back for a table nobody needs.
 *
 * <p>Asked of the reduction directly, because the reduction is a function of what it is given and
 * the shape this is about is one a model reaches only by accident: a row that is redundant about
 * every item it settles and is still the last thing a published table is true of.
 */
class AReductionKeepsWhatThePublishedTableBelongsToTest {

    private static final ValueName.Behavior LOOKUP =
            new ValueName.Behavior("example.owners", "lookup");

    private static final ObligationIdentity A_RULE = new ObligationIdentity.OfADecisionRule(
            "decides", new souther.compiler.partition.DecisionRule(Map.of()));

    private static final RowKey ORDINARY =
            new RowKey("decides", List.of("0"), List.of());

    private static final RowKey OWNS_THE_TABLE = new RowKey("decides", List.of("1"),
            List.of("example.owners.lookup(1) = Found"));

    /**
     * Both rows settle the one item, so the second is redundant about everything the reduction used
     * to read — and it is the only row the published table belongs to.
     */
    @Test
    void theLastRowATableBelongsToStays() {
        Set<RowKey> kept = settlements(Map.of(LOOKUP, Set.of(OWNS_THE_TABLE))).keeping();

        assertTrue(kept.contains(OWNS_THE_TABLE),
                () -> "the row the block's table belongs to is kept: " + kept);
        assertFalse(kept.contains(ORDINARY),
                () -> "and the one that brings nothing beside the item it settles is not: " + kept);
    }

    /** And where nothing is published, the reduction reads the items alone, as it always did. */
    @Test
    void andWhereNoTableIsPublishedTheItemsDecideAlone() {
        Set<RowKey> kept = settlements(Map.of()).keeping();

        assertTrue(kept.contains(ORDINARY),
                () -> "the first row settling the item stays: " + kept);
        assertFalse(kept.contains(OWNS_THE_TABLE),
                () -> "and the second settles nothing the first does not: " + kept);
    }

    /** Two rows, both settling the one item, walked in this order. */
    private static Settlements settlements(Map<ValueName.Behavior, Set<RowKey>> owners) {
        SequencedMap<RowKey, Map<ObligationIdentity, Settlement>> byRow = new LinkedHashMap<>();
        byRow.put(ORDINARY, Map.of(A_RULE, new Settlement.Settles()));
        byRow.put(OWNS_THE_TABLE, Map.of(A_RULE, new Settlement.Settles()));
        SequencedMap<ValueName.Behavior, Set<RowKey>> owning = new LinkedHashMap<>();
        owners.forEach(owning::put);
        return new Settlements(List.of(A_RULE), new LinkedHashMap<>(), byRow, owning);
    }
}
