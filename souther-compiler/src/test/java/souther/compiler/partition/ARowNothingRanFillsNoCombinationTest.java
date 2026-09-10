package souther.compiler.partition;

import souther.compiler.coverage.ArmProbe;
import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.core.Core;
import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.SiteNumbering;
import souther.compiler.inputs.InputDomain;
import souther.compiler.reading.Interaction;
import souther.compiler.reading.CoverageRead;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What fills a combination of a body's decisions, and what only looks like it.
 *
 * <p>A combination is a path through the body and an outcome at each decision that meets on it. What
 * fills one is a run that took that path and settled them those ways. Sitting in the classes the
 * combination leaves open is what the reading expected of such a run — and the step from the
 * decisions to the classes is a reading, which can be wrong while looking right.
 *
 * <p>So this puts the same row in twice, once with a run behind it and once with nothing, and asks
 * what is still owed. The row's values are the same both times; only whether anything watched it
 * differs.
 */
class ARowNothingRanFillsNoCombinationTest {

    /** Two decisions, one per input, consumed into one value. Both are matched on, so the classes a
     * row sits in are the declared cases and no threshold is needed to divide anything. */
    private static final String SHIPPING = """
            module example.shipping

            data Membership = Premium | Standard

            data Delivery = Express | Regular

            data Fee = Int
                invariant value >= 0

            behavior shippingFee : (member: Membership, delivery: Delivery) -> Fee
                constructs Fee

            let baseFee (tier: Membership): Int =
                match tier with
                    | Premium -> 0
                    | Standard -> 500

            let expressFee (speed: Delivery): Int =
                match speed with
                    | Express -> 500
                    | Regular -> 0

            let shippingFee (member, delivery) =
                Fee(baseFee(member) + expressFee(delivery))
            """;

    /**
     * A combination is where an arm's row is looked for, and is not itself owed one.
     *
     * <p>This used to be about which rows filled a combination: seen doing what it names it filled
     * one, seen doing something else it did not, and where nothing could watch it an author's row
     * was given the benefit. All three were the generator counting rows against a space nothing
     * reports — and it counted them from a reading of the body, which is what could be wrong.
     *
     * <p>The three answers are still there and they are one measure's: whether an arm was reached
     * is what {@code BranchEvidence} establishes, and an arm nothing read the rows for is not
     * established as unreached at all. What is left here is that the search looks where it is told
     * to and composes for nothing of its own.
     */
    @Test
    void aCombinationIsSearchedForAnArmOnTheListAndForNothingElse() {
        Model model = Model.of(SHIPPING, "shippingFee");
        InteractionCells.Offered offered =
                InteractionCells.of(model.groups(), model.subject().axes().axes(), Budgets.generation());
        List<InteractionCells.Group> groups = offered.groups();
        assertEquals(1, groups.size(), "the two decisions meet once");
        assertEquals(List.of(), offered.notOffered(),
                "and none of them was held back, so what follows is about a group that was walked");
        CellSelection first = groups.get(0).at(0);
        assertNotNull(first, "and its first choice is a combination");
        assertFalse(first.claims().isEmpty(), "which a run can be held to");

        Set<ArmProbe> every =
                Generator.everyArmACombinationMayTake(model.subject(), model.groups(), Budgets.generation());
        assertTrue(offeredFor(model, every).containsAll(claimedBy(first)),
                "asked about the arms this combination takes, a row is composed for each of them");
        assertEquals(Set.of(), offeredFor(model, Set.of()),
                "and asked about none of them, nothing here composes for the combination itself");
    }

    /**
     * A row is composed for the arms it was looked for, and not for the combination it was found at.
     *
     * <p>One combination can be what two arms were both waiting for. Named for the combination, the
     * row read as answering one thing called {@code a x b} — an obligation nobody raised, standing
     * where the two that were raised should have been.
     */
    @Test
    void aRowIsComposedForTheArmsAndNotForWhereTheyWereFound() {
        Model model = Model.of(SHIPPING, "shippingFee");
        CellSelection first = InteractionCells.of(model.groups(), model.subject().axes().axes(), Budgets.generation())
                .groups().get(0).at(0);
        assertNotNull(first);
        Set<ArmProbe> takes = claimedBy(first);
        assertTrue(takes.size() > 1, "the combination takes an arm of each decision: " + takes);

        List<Generator.GeneratedRow> composed = Generator.fill(model.subject(), List.of(),
                        Generator.CandidateCheck.ANY, model.read(),
                        Generator.Trial.NOTHING_RUNS, List.of(), List.of(), List.copyOf(takes), Budgets.generation())
                .rows();

        assertEquals(1, composed.size(), "one row answers both: " + composed);
        assertEquals(takes, composed.get(0).purposes().stream()
                        .map(Generator.Purpose.ForAnArm.class::cast)
                        .map(Generator.Purpose.ForAnArm::probe)
                        .collect(java.util.stream.Collectors.toSet()),
                "and it is composed for each of them, not for where it was found");
        assertEquals(List.of(), composed.get(0).labels(),
                "which this package spells no name for: what an arm is called is the report's word");
    }

    /**
     * An arm every combination claiming it failed at keeps what each of them came to.
     *
     * <p>They are not one fact. A combination the model's own rules leave nothing in and one the
     * search stopped at are different news — a reader may act on the first and not on the second —
     * and they do not order against each other, so a single one carried forward carried the order
     * the cells were walked in. Both are kept and the reader is handed both.
     */
    @Test
    void anArmKeepsWhatEveryCombinationClaimingItCameTo() {
        Model model = Model.of(SHIPPING, "shippingFee");
        Set<ArmProbe> every =
                Generator.everyArmACombinationMayTake(model.subject(), model.groups(), Budgets.generation());
        FillResult filled = Generator.fill(model.subject(), List.of(),
                Generator.CandidateCheck.refusing((_, _) -> java.util.Optional.of("no")),
                model.read(), Generator.Trial.NOTHING_RUNS, List.of(), List.of(), List.copyOf(every), Budgets.generation());

        assertEquals(List.of(), filled.rows(), "nothing builds, so nothing is composed");
        for (ArmProbe probe : every) {
            ArmDisposition at = filled.discharge().at(new Generator.ArmOwed(probe));
            assertInstanceOf(ArmDisposition.Unresolved.class, at,
                    "the arm was tried and says so: " + probe);
            assertEquals(3, ((ArmDisposition.Unresolved) at).why().size(),
                    "and keeps what each place it was looked for came to — the two combinations"
                            + " claiming it and the way into it: " + at);
        }
    }

    /**
     * One row through an arm is what the arm is owed, whichever combination composed it.
     *
     * <p>A rule relating two positions can refuse the values one combination names while another
     * builds. Answered by the first combination walked, the same arm came back as one nothing could
     * compose for or as one a row goes through, depending on which of them the search reached first.
     */
    @Test
    void oneRowThroughAnArmIsTheAnswerWhateverTheOthersCameTo() {
        Model model = Model.of(SHIPPING, "shippingFee");
        Set<ArmProbe> every =
                Generator.everyArmACombinationMayTake(model.subject(), model.groups(), Budgets.generation());
        // Refuses the first case of the first position, so the combinations naming it fail and the
        // ones beside them build. Every arm of the second decision is claimed by both.
        FillResult filled = Generator.fill(model.subject(), List.of(),
                Generator.CandidateCheck.refusing((_, candidate) ->
                        candidate.text().contains("Premium")
                                ? java.util.Optional.of("no") : java.util.Optional.empty()),
                model.read(), Generator.Trial.NOTHING_RUNS, List.of(), List.of(), List.copyOf(every), Budgets.generation());

        assertFalse(filled.rows().isEmpty(), "the combinations that build compose their rows");
        List<ArmProbe> built = every.stream()
                .filter(probe -> filled.discharge().at(new Generator.ArmOwed(probe)) instanceof ArmDisposition.Built)
                .toList();
        assertEquals(3, built.size(),
                "the arm nothing builds is the refused one; the three beside it are answered: "
                        + filled.discharge().arms().values());
    }

    /**
     * A position with more classes than the run has rows, and two arms below it.
     *
     * <p>The classes go first and spend the whole budget, so the arms are reached with nothing left
     * to compose. What the search has to say about them is that it stopped.
     */
    private static String wide() {
        StringBuilder cases = new StringBuilder();
        for (int i = 0; i < 210; i++) {
            cases.append(i == 0 ? "" : " | ").append("A").append(i);
        }
        return """
                module example.wide

                data Wide = %s

                data Flag = On | Off

                data Mode = Fast | Slow

                data Out = Int
                    invariant value >= 0

                behavior submit : (w: Wide, f: Flag, m: Mode) -> Out
                    constructs Out

                let pick (flag: Flag): Int =
                    match flag with
                        | On -> 1
                        | Off -> 0

                let speed (mode: Mode): Int =
                    match mode with
                        | Fast -> 10
                        | Slow -> 0

                let submit (w, f, m) = Out(pick(f) + speed(m))
                """.formatted(cases.toString());
    }

    /**
     * An arm the search stopped short of says so, and is not reported as one nothing reaches.
     *
     * <p>The two are one silence otherwise. An arm with no entry in the ledger used to mean both
     * "no combination of the body claims it" and "the row limit came first", and the second was read
     * as the first — so a model whose classes alone spend the budget had every arm below them
     * answered "no combination reaches this arm", which is a fact about the model told on the
     * strength of a limit (issue #967).
     */
    @Test
    void anArmTheLimitCutOffSaysSoRatherThanReadingAsUnreachable() {
        Model model = Model.of(wide(), "submit");
        Set<ArmProbe> every =
                Generator.everyArmACombinationMayTake(model.subject(), model.groups(), Budgets.generation());
        assertFalse(every.isEmpty(), "the body has arms");

        FillResult filled = Generator.fill(model.subject(), List.of(),
                Generator.CandidateCheck.ANY, model.read(), Generator.Trial.NOTHING_RUNS,
                List.of(), Generator.everyClassNoRowSitsIn(model.subject(), List.of()),
                List.copyOf(every), Budgets.generation());

        assertTrue(filled.reasons().stream()
                        .anyMatch(GenerationReason.SearchLimit.class::isInstance),
                "the classes alone spend the budget: " + filled.reasons());
        for (ArmProbe probe : every) {
            ArmDisposition at = filled.discharge().at(new Generator.ArmOwed(probe));
            assertInstanceOf(ArmDisposition.Unresolved.class, at,
                    "the arm has an entry rather than the silence of one nothing claims: " + probe);
            assertEquals(List.of(Generator.UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED),
                    ((ArmDisposition.Unresolved) at).why().stream()
                            .map(Generator.UnresolvedCombination::reason).toList(),
                    "and says the search stopped, nothing having been tried at it");
        }
    }

    /** The arms one combination claims a run through. */
    private static Set<ArmProbe> claimedBy(CellSelection selection) {
        Set<ArmProbe> out = new LinkedHashSet<>();
        for (ControlClaim claim : selection.claims()) {
            if (claim.at() instanceof ControlPlace.Arm arm && arm.probe().isPresent()) {
                out.add(arm.probe().get());
            }
        }
        return out;
    }

    /**
     * A class is composed for when it is on the list, and no class is on it by the search's doing.
     *
     * <p>The same question the arms are asked, at the other measure. Which classes are owed a row is
     * what the partition measure reads off the rows, and a search working it out again for itself
     * is a second reading of one fact — free to offer an author a row at a class the report calls
     * reached, or to say nothing at one it calls unreached.
     */
    @Test
    void aClassIsComposedForWhenItIsOnTheListAndNotOtherwise() {
        Model model = Model.of(SHIPPING, "shippingFee");
        List<Generator.ClassOwed> every =
                Generator.everyClassNoRowSitsIn(model.subject(), List.of());
        assertFalse(every.isEmpty(), "the model divides its positions");
        Generator.ClassOwed one = every.get(0);

        assertEquals(List.of(), composedFor(model, List.of()),
                "asked for no class, nothing is composed");
        assertEquals(List.of(one), composedFor(model, List.of(one)),
                "and asked for one, that one and nothing beside it");
    }

    /** Which classes the generator composes a row for when it is asked about {@code classes}. */
    private static List<Generator.ClassOwed> composedFor(Model model,
                                                         List<Generator.ClassOwed> classes) {
        return Generator.fill(model.subject(), List.of(), Generator.CandidateCheck.ANY,
                        model.read(), Generator.Trial.NOTHING_RUNS, List.of(), classes,
                        List.of(), Budgets.generation())
                .rows().stream().flatMap(row -> row.purposes().stream())
                .map(Generator.Purpose.ForAClass.class::cast)
                .map(at -> new Generator.ClassOwed(at.at(), at.classId())).toList();
    }

    /** Which arms the generator composes a row for when it is asked about {@code arms}. */
    private static Set<ArmProbe> offeredFor(Model model, Set<ArmProbe> arms) {
        return Generator.fill(model.subject(), List.of(), Generator.CandidateCheck.ANY,
                        model.read(), Generator.Trial.NOTHING_RUNS, List.of(), List.of(), List.copyOf(arms), Budgets.generation())
                .rows().stream().flatMap(row -> row.purposes().stream())
                .map(Generator.Purpose.ForAnArm.class::cast)
                .map(Generator.Purpose.ForAnArm::probe)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
            InputDomain inputs = compilation.db()
                    .ask(new souther.compiler.query.Adequacy.Inputs(module)).value()
                    .get(behavior);
            assertNotNull(inputs, "the behavior's inputs were read");
            Partitions.Partitioning partitioning = Partitions.of(spec.name(), inputs, rules,
                    souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
            Core body = checked.behaviorBodies().get(behavior);
            assertNotNull(body, "the behavior under test has a body");
            CoverageSites.Plan plan = checked.plan();
            return new Model(MeasuredInput.of(spec.name(), inputs.reading(rules),
                    partitioning),
                    CoverageRead.of(spec.name(), body, plan, inputs, rules), plan.numbering());
        }
    }
}
