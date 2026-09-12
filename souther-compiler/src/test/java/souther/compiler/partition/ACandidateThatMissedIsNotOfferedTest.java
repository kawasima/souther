package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.core.Core;
import souther.compiler.coverage.AlignedObservation;
import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.Runs;
import souther.compiler.coverage.SiteNumbering;
import souther.compiler.inputs.InputDomain;
import souther.compiler.reading.Interaction;
import souther.compiler.reading.CoverageRead;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row composed for a combination, run, and held to what the combination says.
 *
 * <p>Which is the step that was missing. A row is composed by narrowing each position to the classes
 * the combination leaves it and choosing one; every part of that is a reading of the body, and a row
 * that goes somewhere else is what any of those readings being wrong produces. Offered without being
 * run it is named for a combination nobody confirmed it sits in.
 *
 * <p>The rows a combination admits are more than one here — one position takes no part in the
 * decisions, so it is free — which is what makes trying another assignment a thing that can happen
 * rather than a branch nothing reaches.
 */
class ACandidateThatMissedIsNotOfferedTest {

    private static final String SHIPPING = """
            module example.shipping

            data Membership = Premium | Standard

            data Delivery = Express | Regular

            data Fee = Int
                invariant value >= 0

            behavior shippingFee : (member: Membership, delivery: Delivery, rush: Bool) -> Fee
                constructs Fee

            let baseFee (tier: Membership): Int =
                match tier with
                    | Premium -> 0
                    | Standard -> 500

            let expressFee (speed: Delivery): Int =
                match speed with
                    | Express -> 500
                    | Regular -> 0

            let shippingFee (member, delivery, rush) =
                Fee(baseFee(member) + expressFee(delivery))
            """;

    /** A run that did nothing at all, which is a run that missed every combination. Of the
     *  model's own numbering: a run is a run of somewhere, and one of nowhere could be asked about
     *  any place at all and answer. */
    private static Generator.Watched missed(Model model) {
        return new Generator.Watched.Ran(Runs.nowhere(model.numbering()));
    }

    /**
     * A combination every candidate missed is not offered, and is not called impossible.
     *
     * <p>Both halves. Offering it would hand an author a row named for a combination it does not
     * reach; recording it as impossible would say the model settles something it does not. What is
     * said is that the candidates tried were not witnesses.
     */
    @Test
    void aCombinationEveryCandidateMissedIsLeftUntried() {
        Model model = Model.of(SHIPPING, "shippingFee");

        FillResult filled = fill(model, _ -> missed(model));

        assertTrue(filled.unresolved().stream().anyMatch(each -> each.reason()
                        == Generator.UnresolvedCombination.Reason.NO_CERTIFIED_WITNESS),
                "the combinations were left untried: " + filled.unresolved());
        assertTrue(filled.unresolved().stream().noneMatch(each -> each.reason()
                        == Generator.UnresolvedCombination.Reason.THE_RULES_LEAVE_NOTHING_THERE),
                "and nothing was said to be impossible: " + filled.unresolved());
    }

    /** A run that did what the combination names takes it out of the offer, by being a witness. */
    @Test
    void aCandidateThatArrivedIsOffered() {
        Model model = Model.of(SHIPPING, "shippingFee");
        AlignedObservation everything = doing(model.numbering(), everyClaimOf(model));

        FillResult filled =
                fill(model, _ -> new Generator.Watched.Ran(everything));

        assertTrue(filled.unresolved().stream().noneMatch(each -> each.reason()
                        == Generator.UnresolvedCombination.Reason.NO_CERTIFIED_WITNESS),
                "nothing missed: " + filled.unresolved());
        assertFalse(filled.rows().isEmpty(), "and the rows are offered");
    }

    /**
     * Another assignment is tried, and they are different rows.
     *
     * <p>A combination leaves the free position either of its classes, and which one the first
     * candidate took was this search's choice rather than the combination's. So a candidate that
     * missed is not the combination's answer — another assignment is composed and run, and the two
     * are different rows rather than the same one twice.
     */
    @Test
    void anotherAssignmentIsTriedAndItIsADifferentRow() {
        Model model = Model.of(SHIPPING, "shippingFee");
        List<List<String>> ran = new ArrayList<>();

        fill(model, row -> {
            ran.add(row.inputs().stream().map(FixtureTemplate::text).toList());
            return missed(model);
        });

        // The two positions the decisions read say which combination a candidate was composed for;
        // the third is the one they leave free, and is where another assignment differs. Counted
        // this way and not by how many candidates ran in all: one candidate at each of four
        // combinations is four runs and no second try.
        Map<List<String>, Set<List<String>>> byCombination = new java.util.LinkedHashMap<>();
        for (List<String> row : ran) {
            byCombination.computeIfAbsent(row.subList(0, 2), _ -> new LinkedHashSet<>()).add(row);
        }
        assertFalse(byCombination.isEmpty(), "candidates were composed and run");
        assertTrue(byCombination.values().stream().anyMatch(rows -> rows.size() > 1),
                "a combination whose first candidate missed had another composed for it: " + ran);
        assertEquals(ran.size(), new LinkedHashSet<>(ran).size(),
                "and no row was run twice: " + ran);
    }

    /**
     * Where nothing runs a row, the rows are still offered and the generation says so.
     *
     * <p>Silence would read as confirmation. A row nothing ran is worth writing — this is the
     * account a generation could always give of one — but what it is offered for is a reading of
     * the body rather than something anything watched, and an author acting on it is acting on
     * that reading.
     */
    @Test
    void rowsNothingRanAreOfferedAndSaidToBeUnconfirmed() {
        Model model = Model.of(SHIPPING, "shippingFee");

        FillResult filled = fill(model, Generator.Trial.NOTHING_RUNS);

        assertFalse(filled.rows().isEmpty(), "the rows are offered");
        assertEquals(1, filled.reasons().stream()
                        .filter(GenerationReason.RowsNotConfirmed.class::isInstance).count(),
                "and the generation says once that nothing ran them: " + filled.reasons());
    }

    private static FillResult fill(Model model, Generator.Trial trial) {
        return Generator.fill(model.subject(), List.of(), Generator.CandidateCheck.ANY,
                model.read(), trial, Budgets.generation());
    }

    /** Every claim any combination of the model makes, so that one run answers all of them. */
    private static List<ControlClaim> everyClaimOf(Model model) {
        List<ControlClaim> out = new ArrayList<>();
        for (InteractionCells.Group group : InteractionCells.of(model.groups(), model.subject().axes().axes(), Budgets.generation()).groups()) {
            for (int index = 0; index < group.size(); index++) {
                CellSelection selection = group.at(index);
                if (selection != null) {
                    out.addAll(selection.claims());
                }
            }
        }
        assertFalse(out.isEmpty(), "the model has combinations to claim anything about");
        return out;
    }

    /** A run that did everything {@code claims} names. */
    private static AlignedObservation doing(SiteNumbering numbering,
                                           List<ControlClaim> claims) {
        return Runs.doing(numbering, claims);
    }

    private record Model(MeasuredInput subject, CoverageRead.Read read,
                         SiteNumbering numbering) {

        /** The groups of the one reading, for a caller asking about the combinations alone. */
        List<Interaction> groups() {
            return read.interactions();
        }

        static Model of(String source, String behavior) {
            Compilation compilation = Compilation.ofSource(source, "Main");
            compilation.answerEverything();
            String module = compilation.modules().get(0);
            Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
            Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
            assertNotNull(prepared);
            assertNotNull(sigs);
            assertNotNull(checked);
            Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                    .filter(b -> b.name().equals(behavior)).findFirst().orElseThrow();
            InputDomain inputs =
                    compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior);
            assertNotNull(inputs, "the behavior's inputs were read");
            Core body = checked.behaviorBodies().get(behavior);
            assertNotNull(body, "the behavior under test has a body");
            return new Model(MeasuredInput.of(spec.name(), inputs.reading(rules),
                    Partitions.of(spec.name(), inputs, rules,
                            souther.compiler.query.ReadAs.THE_COMPILATION_DOES)),
                    CoverageRead.of(spec.name(), body,
                            checked.plan(), inputs,
                            rules), checked.plan().numbering());
        }
    }
}
