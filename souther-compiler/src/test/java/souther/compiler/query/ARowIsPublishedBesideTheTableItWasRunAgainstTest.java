package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;
import souther.compiler.partition.Generator;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a row was run against is what goes out beside it.
 *
 * <p>A row that needs a dependency to answer by what it was applied to is certified by running it:
 * a value is composed, the row is applied with that table in force, and the reading is asked which
 * way it took. What a person is handed has to be that table — published beside a different one, the
 * row goes somewhere the run never saw, and the calls it would differ at are exactly the ones a
 * fallback exists for.
 *
 * <p>Which is why a module publishes one table per dependency rather than several merged. A union
 * of two rows' tables is a third table neither was run against.
 */
class ARowIsPublishedBesideTheTableItWasRunAgainstTest {

    /**
     * The guard forces the two calls apart, so the askings are two calls one row makes; and the
     * arms answer crosswise, so two of the ways want the dependency to answer the two calls
     * differently — which is two rows wanting two tables for one dependency.
     */
    private static final String TWO_CALLS = """
            module example.published

            data Found
            data Missing
            data Sighting = Found | Missing
            data Yes
            data No
            data Answer = Yes | No

            behavior lookup : (id: Int) -> Sighting
            behavior marking : (id: Int) -> Sighting

            behavior decides : (id: Int) -> Answer
                depends on lookup, marking
            let decides (id, lookup, marking) =
                if id > 5 then
                    match lookup(id) with
                        | Found -> match lookup(0) with
                            | Found -> match marking(id) with
                                | Found -> match marking(0) with
                                    | Found -> Yes
                                    | Missing -> No
                                | Missing -> No
                            | Missing -> Yes
                        | Missing -> No
                else No
            """;

    /**
     * One dependency asked at two calls, answered crosswise, so two of the ways want it to answer
     * the two calls differently — which is two rows wanting two tables for one dependency.
     */
    private static final String TWO_TABLES = """
            module example.wanted

            data Found
            data Missing
            data Sighting = Found | Missing
            data Yes
            data No
            data Answer = Yes | No

            behavior lookup : (id: Int) -> Sighting

            behavior decides : (id: Int) -> Answer
                depends on lookup
            let decides (id, lookup) =
                if id > 5 then
                    match lookup(id) with
                        | Found -> match lookup(0) with
                            | Found -> Yes
                            | Missing -> No
                        | Missing -> match lookup(0) with
                            | Found -> No
                            | Missing -> Yes
                else No
            """;

    @Test
    void whatTheBlockPublishesIsOneRowsWholeTable() {
        Compilation compilation = Compilation.ofSource(TWO_CALLS, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String module = compilation.modules().get(0);

        Offering offering = Adequacy.offeredFor(compilation.db(),
                new OfferingRequest(module, new GenerationScope.Module(), true));
        assertNotNull(offering, "the model under test is one a run offers rows for");
        assertFalse(offering.tables().isEmpty(),
                () -> "a row here needs a table: " + offering.rowsByBehavior());

        // Every table published is some offered row's, whole — its entries and what it answers a
        // call none of them names. A union of two rows' would be neither of theirs.
        List<StandInTable> rows = new ArrayList<>();
        for (List<OfferedRow> here : offering.rowsByBehavior().values()) {
            for (OfferedRow row : here) {
                for (ValueName.Behavior dependency : dependenciesOf(row)) {
                    StandInTable mine = StandInTable.of(dependency, row.answers());
                    if (mine != null) {
                        rows.add(mine);
                    }
                }
            }
        }
        for (StandInTable published : offering.tables().values()) {
            assertEquals(1, rows.stream().filter(published::equals).count(),
                    () -> "the table published for " + published.dependency()
                            + " is one offered row's, whole: " + rows);
        }
    }

    /**
     * And the row that wanted a different one is not in the block, with what it cost said.
     *
     * <p>A table is written once for a module, so the second row's table is not one this block can
     * hold. It is not in it and the reason is beside it — dropped quietly, a person reads a block
     * that says nothing about work this run composed and could not hand over.
     */
    @Test
    void andTheRowThatWantedAnotherIsSaidRatherThanDropped() {
        Compilation compilation = Compilation.ofSource(TWO_TABLES, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        Offering offering = Adequacy.offeredFor(compilation.db(),
                new OfferingRequest(compilation.modules().get(0), new GenerationScope.Module(),
                        true));
        assertNotNull(offering, "the model under test is one a run offers rows for");

        assertEquals(List.of(Generator.UnresolvedCombination.Reason
                        .A_TABLE_IS_WRITTEN_ONCE_FOR_A_MODULE),
                offering.withheld().stream()
                        .map(Generator.UnresolvedCombination::reason).distinct().toList(),
                () -> "the row wanting a second table is withheld, and says why: "
                        + offering.withheld());
    }

    /**
     * And nothing else composes one, so no caller can reach publication without saying what its
     * rows stand in with.
     *
     * <p>The hole this closes is a second way in. A behavior with nothing of its own to fill can
     * still carry the row a declaration's line is owed, and a walk that took those rows somewhere
     * else published them having stood nothing in — which is a row refused the moment it is pasted.
     */
    @Test
    void andOnePlaceComposesAnOffering() {
        assertEquals(List.of("souther.compiler.query.Adequacy"),
                List.copyOf(WhatWasCompiled.callersOf(Composition.class, "composed")),
                "an offering is composed where what its rows stand in with is asked for");
    }

    private static List<ValueName.Behavior> dependenciesOf(OfferedRow row) {
        List<ValueName.Behavior> out = new ArrayList<>();
        for (StoodInAnswer each : row.answers()) {
            if (!out.contains(each.dependency())) {
                out.add(each.dependency());
            }
        }
        return out;
    }
}
