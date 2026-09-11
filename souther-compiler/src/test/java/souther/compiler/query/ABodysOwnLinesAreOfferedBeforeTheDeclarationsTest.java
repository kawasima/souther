package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.partition.ObligationIdentity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block offers what a body's own lines are owed before what the declarations' are.
 *
 * <p>An order, and it decides what a person is handed. Two rows can answer one point — a value
 * inside a run is inside it whichever line put it there — and the reduction keeps the earlier of
 * them, so that an edit further down the model does not move what is offered above it
 * ({@link Settlements#keeping}).
 *
 * <p>One account answers for both kinds of line, so the order is what
 * {@link BorderAccount#rowsByCarrier} lists them in rather than the order the points were gathered
 * in — which is a fact about a walk and about nothing a reader can see.
 */
class ABodysOwnLinesAreOfferedBeforeTheDeclarationsTest {

    /** A guard over two positions, under a declaration that bounds both of them. */
    private static final String DENSE = """
            module example.dense

            data D = Decimal
                invariant value >= 0m
                invariant value <= 100m

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (a: D, b: D) -> Result
                constructs No, Yes
            let f (a, b) = {
                guard a.value * 2m + b.value * 4m <= 9m else No { why = 0 }
                Yes { v = 1 }
            }

            example f
                | "under" : (D(1m), D(50m)) -> Yes { v = 1 }
                | "over" : (D(50m), D(1m)) -> No { why = 0 }
            """;

    /** The body's rows first, then the declarations', and the account's own order under each. */
    @Test
    void theRowsAreListedWithTheBodysFirst() {
        BorderAccount account = account();

        List<List<String>> theBodys = new ArrayList<>();
        List<List<String>> theDeclarations = new ArrayList<>();
        account.resolved().forEach((_, answer) -> {
            if (answer.resolution() instanceof PointResolution.Generated(var at, var row)
                    && at.behavior().equals("f")) {
                (answer.point().owedToTheReading() ? theBodys : theDeclarations)
                        .add(written(row));
            }
        });
        assertFalse(theBodys.isEmpty(), "this model has lines of its own");
        assertFalse(theDeclarations.isEmpty(), "and lines its declarations own");

        List<List<String>> expected = new ArrayList<>(theBodys);
        expected.addAll(theDeclarations);
        assertEquals(expected, account.rowsByCarrier().get("f").stream()
                        .map(ABodysOwnLinesAreOfferedBeforeTheDeclarationsTest::written).toList(),
                "the body's own lines are offered first");
    }

    /**
     * And the row composed for a body's own point is the one that survives.
     *
     * <p>What the order is for. A row standing at the bottom of the declaration's range also stands
     * in the run beside the guard's line, so it settles that point as well — and a reduction that
     * kept it and dropped the row composed for the point would offer the point's own row to
     * nobody.
     */
    @Test
    void theRowComposedForTheBodysPointIsTheOneKept() {
        Compilation compilation = compiled();
        Composition composition = Composition.composed(
                OfferingRequest.overTheModule("example.dense", true),
                Adequacy.generatedOf(compilation.db(), "example.dense"), account(),
                Composition.WhatStandsIn.REQUIRING_NOTHING);
        Settlements table = Settlements.of(compilation.db(), composition);

        ObligationIdentity inTheRun = table.requested().stream()
                .filter(each -> each instanceof ObligationIdentity.OfALine(var point)
                        && point.role() == souther.compiler.partition.PointRole.IN)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "this model owes a row in the run beside its guard: " + table.requested()));
        RowKey composedThere = table.composedFor().get(inTheRun);
        assertNotNull(composedThere, "a row was composed for it: " + inTheRun);

        long settling = table.byRow().keySet().stream()
                .filter(row -> table.at(row, inTheRun).settles()).count();
        assertTrue(settling > 1,
                "more than one row stands in the run, so which is kept is an order and not a fact");
        assertTrue(table.keeping().contains(composedThere),
                () -> "and the one composed for it is kept: " + table.keeping());
    }

    private static List<String> written(Generator.GeneratedRow row) {
        return row.inputs().stream().map(FixtureTemplate::text).toList();
    }

    private static BorderAccount account() {
        return Adequacy.accountFor(compiled().db(), "example.dense",
                new GenerationScope.Module());
    }

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(DENSE, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }
}
