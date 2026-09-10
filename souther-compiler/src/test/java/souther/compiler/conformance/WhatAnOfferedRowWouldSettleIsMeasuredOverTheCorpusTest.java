package souther.compiler.conformance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import souther.compiler.query.Adequacy;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.query.Composition;
import souther.compiler.query.OfferingRequest;
import souther.compiler.query.RowKey;
import souther.compiler.query.Settlement;
import souther.compiler.query.Settlements;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every row the corpus is offered would settle, counted over the whole of it.
 *
 * <p>The table this is about is what a reduction of the offering would act on, and a reduction acts
 * only on what a row is known to settle. So the two things worth holding are that the walk answers
 * for the whole table — every row against every item the run was asked about — and that what it
 * cannot tell stays told apart from what it read and found not to be the case.
 *
 * <p>The numbers are printed rather than pinned. What the corpus happens to hold moves as the corpus
 * is written, and a checked-in count would be a snapshot of the models rather than of this walk.
 * What is asserted is the shape: the table is total, the items are what was asked for, and no row
 * composed for something is read as not settling it.
 *
 * <p><b>That last one holds of every model, and this measures it.</b> A composed row goes through
 * this walk before it is offered, so the search and this reading are not two accounts that have to
 * agree — the search asks this one. What the corpus adds is that the rows are still there: a gate
 * that refused everything would satisfy the same sentence with nothing behind it.
 *
 * <p><b>And it is the weaker of the two sentences it could be.</b> What holds is that no offered row
 * is <em>observed</em> not to settle its point, which is not that every offered row settles it: a
 * row nothing watched run, and a row whose values could not be read at the point, are offered and
 * come back {@code Undetermined} here. Strengthening the assertion to {@code Settles} would be
 * asserting something nothing establishes.
 */
@Tag("population")
class WhatAnOfferedRowWouldSettleIsMeasuredOverTheCorpusTest {

    @Test
    void everyOfferedRowIsAnsweredForAtEveryItemTheRunWasAskedAbout() {
        Map<String, Integer> answers = new TreeMap<>();
        Map<String, Integer> undetermined = new TreeMap<>();
        int rows = 0;
        int items = 0;
        int settledNotComposedFor = 0;
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            ConformanceCorpus.Analysed analysed = corpus.analyse();
            for (String module : analysed.compilation().modules()) {
                java.util.Map<String, Adequacy.Filling> filled =
                        Adequacy.generatedOf(analysed.compilation().db(), module);
                if (filled == null) {
                    continue;
                }
                // The composition, because what a row was composed for is a fact about it: read
                // off what a person is finally handed, a row another row answers for is not there
                // to be asked about.
                Composition offering = Composition.composed(
                        OfferingRequest.overTheModule(module, true), filled,
                        Adequacy.accountFor(analysed.compilation().db(), module,
                                new souther.compiler.query.GenerationScope.Module()));
                Settlements settlements =
                        Settlements.of(analysed.compilation().db(), offering);
                rows += settlements.byRow().size();
                items += settlements.requested().size();

                // Total over the two axes it names. A pair with no entry is a row nobody asked
                // about at an item, and a reduction reading that absence would be free to read it
                // either way.
                for (Map.Entry<RowKey, Map<ObligationIdentity, Settlement>> row
                        : settlements.byRow().entrySet()) {
                    assertEquals(new LinkedHashSet<>(settlements.requested()),
                            row.getValue().keySet(),
                            "every item this run was asked about is answered for " + row.getKey());
                    row.getValue().forEach((item, settlement) -> {
                        answers.merge(kindOf(item) + " " + nameOf(settlement), 1, Integer::sum);
                        if (settlement instanceof Settlement.Undetermined(var why)) {
                            undetermined.merge(why.name(), 1, Integer::sum);
                        }
                    });
                }

                // What the searches composed a row for, against what the rows turn out to settle.
                // A row composed for an item and read as not settling it is a row that went out
                // without being put to this walk, which is what offering one goes through.
                settlements.composedFor().forEach((item, row) -> {
                    Map<ObligationIdentity, Settlement> here = settlements.byRow().get(row);
                    if (here != null) {
                        assertFalse(here.get(item) instanceof Settlement.DoesNotSettle,
                                "a row composed for " + item + " does not settle it: " + row);
                    }
                });
                Set<ObligationIdentity> settled = settlements.settled();
                for (ObligationIdentity item : settled) {
                    if (!settlements.composedFor().containsKey(item)) {
                        settledNotComposedFor++;
                    }
                }
                assertTrue(settlements.requested().containsAll(settled),
                        "nothing is settled that was not asked about");
            }
        }
        System.out.println("rows " + rows + ", items " + items
                + ", settled with no row composed for them " + settledNotComposedFor);
        System.out.println("answers " + answers);
        System.out.println("undetermined " + undetermined);
        assertTrue(rows > 0, "the corpus offers rows");
        assertTrue(items > 0, "and is asked for something");
    }

    private static String kindOf(ObligationIdentity item) {
        return switch (item) {
            case ObligationIdentity.OfAClass _ -> "class";
            case ObligationIdentity.OfAnArm _ -> "arm";
            case ObligationIdentity.OfALine _ -> "point";
        };
    }

    private static String nameOf(Settlement settlement) {
        return switch (settlement) {
            case Settlement.Settles _ -> "settles";
            case Settlement.DoesNotSettle _ -> "does not";
            case Settlement.Undetermined _ -> "undetermined";
        };
    }
}
