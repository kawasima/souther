package souther.compiler.partition;

import souther.compiler.coverage.ArmProbe;
import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A group of decisions too wide to walk is reported, not dropped.
 *
 * <p>The limit itself is not the subject and neither is its number. What is held here is that
 * exhausting it leaves something a reader can act on: an arm claimed only by a group nothing walked
 * used to leave no entry at all, and an arm with no entry is one no combination claims — so the
 * report said the body never reaches it. That is a statement about the model this compiler had no
 * evidence for, and it is the one thing a limit must never produce.
 *
 * <p>Held against a source rather than by handing a fixture to the search. What decides whether a
 * group is offered is the product of its factors' outcomes, which is read off the body; a fixture
 * naming a group as too wide would be the test saying what the reading is supposed to say.
 */
class AGroupTooWideToWalkSaysSoTest {

    /**
     * As many decisions meeting on one value as it takes to go past the walk.
     *
     * <p>Each parameter is matched by its own helper and every result is added into one {@code Fee},
     * so the thirteen meet at one operator and the group's choices are two to the thirteenth. The
     * standard budget is a capacity of four thousand and ninety-six, so twelve decisions are exactly
     * it and are offered, and the thirteenth is the first width that is not.
     */
    private static final String THIRTEEN = model(13);

    /** The widest model still offered under the standard budget: twelve decisions are four thousand
     *  and ninety-six choices, which is the capacity exactly. Without it, a group not offered would
     *  be as good an account of a model this cannot read at all. */
    private static final String TWELVE = model(12);

    /** Two decisions, which is four choices — small enough that a budget written here can be put
     *  either side of it exactly. Two to the thirteenth is where the default falls, and a default
     *  can only be reached at a power of two; a budget the caller names can be reached anywhere. */
    private static final String TWO = model(2);

    /** The same behavior with rows, so a compilation can be asked what it is still owed. Each row
     *  settles every parameter the same way, which takes one arm of each helper. */
    private static String modelWithRows(int decisions, boolean bothWays) {
        StringBuilder rows = new StringBuilder();
        for (String at : bothWays ? new String[] {"On", "Off"} : new String[] {"On"}) {
            StringBuilder values = new StringBuilder();
            int total = 0;
            for (int i = 1; i <= decisions; i++) {
                if (i > 1) {
                    values.append(", ");
                }
                values.append(at);
                total += at.equals("On") ? i : 0;
            }
            rows.append("""
                    example total
                        | "%s" : (%s) -> Fee(%d)

                    """.formatted(at, values, total));
        }
        return model(decisions) + "\n" + rows;
    }

    private static String model(int decisions) {
        StringBuilder params = new StringBuilder();
        StringBuilder names = new StringBuilder();
        StringBuilder helpers = new StringBuilder();
        StringBuilder sum = new StringBuilder();
        for (int at = 1; at <= decisions; at++) {
            if (at > 1) {
                params.append(", ");
                names.append(", ");
                sum.append(" + ");
            }
            params.append("p").append(at).append(": Flag");
            names.append("p").append(at);
            sum.append("fee").append(at).append("(p").append(at).append(")");
            helpers.append("""
                    let fee%d (f: Flag): Int =
                        match f with
                            | On  -> %d
                            | Off -> 0

                    """.formatted(at, at));
        }
        return """
                module example.wide

                data On
                data Off
                data Flag = On | Off

                data Fee = Int
                    invariant value >= 0

                behavior total : (%s) -> Fee
                    constructs Fee

                %slet total (%s) = Fee(%s)
                """.formatted(params, helpers, names, sum);
    }

    /**
     * Past the limit, the group is named as one nothing was offered for.
     *
     * <p>Two facts and both are needed. That no group is offered is what the limit does; that the
     * one held back is reported is what this change is. Read off the offered list alone, a model
     * whose decisions this could not read at all looks the same.
     */
    @Test
    void aGroupPastTheLimitIsHeldBackAndSaidSo() {
        Model model = Model.of(THIRTEEN);
        InteractionCells.Offered offered =
                InteractionCells.of(model.groups(), model.subject().axes().axes(), Budgets.generation());

        assertEquals(List.of(), offered.groups(),
                "the group is not offered, which is what the limit is for");
        assertEquals(1, offered.notOffered().size(),
                () -> "and it is named rather than dropped: " + offered.notOffered());
        assertFalse(offered.notOffered().get(0).claims().isEmpty(),
                "carrying what a run through it would have been seen to do");
    }

    /** And one decision fewer is offered, so the answer above is the limit's and not the model's. */
    @Test
    void andUnderTheLimitTheGroupIsOffered() {
        Model model = Model.of(TWELVE);
        InteractionCells.Offered offered =
                InteractionCells.of(model.groups(), model.subject().axes().axes(), Budgets.generation());

        assertEquals(List.of(), offered.notOffered(),
                "nothing is held back");
        assertFalse(offered.groups().isEmpty(),
                "and the group is there to be walked");
    }

    /**
     * A group of exactly the budget is offered, and one choice more is not.
     *
     * <p>The point on the line, which the pair either side of it does not establish. Twelve
     * decisions and thirteen are four thousand and ninety-six choices and eight thousand and
     * one hundred and ninety-two, so the default can say which side of the limit a model falls on
     * and never what happens at it — a default is reached only at a power of two, and the boundary
     * is a single number.
     *
     * <p>Written by naming the budget instead, which is what having one as an input is for. Four
     * choices against a capacity of four is the group being offered <em>at</em> the limit, and the
     * same four against three is the first thing past it. Read the other way round, this is the
     * assertion that {@code cellsPerGroup} is a capacity and not a cutoff: under a cutoff the first
     * of these two would be held back.
     */
    @Test
    void aGroupOfExactlyTheBudgetIsOffered() {
        Model model = Model.of(TWO);
        assertEquals(4, InteractionCells.of(model.groups(), model.subject().axes().axes(),
                        atMost(4)).groups().get(0).size(),
                "two decisions of two outcomes are four choices");

        assertEquals(List.of(), InteractionCells.of(model.groups(), model.subject().axes().axes(),
                        atMost(4)).notOffered(),
                "a group of exactly the budget is offered");
        assertEquals(1, InteractionCells.of(model.groups(), model.subject().axes().axes(),
                        atMost(3)).notOffered().size(),
                "and one choice past it is not");
    }


    private static AdequacyPolicy.OfTheGeneration atMost(int cells) {
        return new AdequacyPolicy.OfTheGeneration(Budgets.generation().rowLimit(), cells);
    }

    /**
     * The arms behind a held-back group are arms the combinations take.
     *
     * <p>Which is the half that used to go missing. A caller asking what the combinations reach got
     * a smaller answer because this compiler declined to look, and nothing in the answer said so —
     * so the arms simply were not asked for, and the report about them was that nothing claims them.
     */
    @Test
    void theArmsBehindItAreStillArmsTheCombinationsTake() {
        Model wide = Model.of(THIRTEEN);
        Model narrow = Model.of(TWELVE);

        Set<ArmProbe> fromWide =
                Generator.everyArmACombinationMayTake(wide.subject(), wide.groups(), Budgets.generation());
        Set<ArmProbe> fromNarrow =
                Generator.everyArmACombinationMayTake(narrow.subject(), narrow.groups(), Budgets.generation());

        assertFalse(fromWide.isEmpty(),
                "the arms behind the group the limit held back are still named");
        assertTrue(fromWide.size() > fromNarrow.size(),
                () -> "and a wider body claims more of them, not fewer: " + fromWide.size()
                        + " against " + fromNarrow.size());
    }

    /**
     * An arm behind the group the limit held back is answered from the way into it.
     *
     * <p>Walking the group is one place a row through an arm is looked for and never the only one.
     * What it takes to arrive at an arm is what the reading of the body says, and that is there
     * whether or not the combinations above it were too many to walk — so the limit costs the
     * combinations of that group and costs no arm its row.
     *
     * <p>And the generation still says the walk was not made, in words a row budget does not
     * explain: the combinations of that group were not looked in, whatever the arms behind it came
     * to. Told the row limit instead, an author raises a number that changes nothing here.
     */
    @Test
    void anArmBehindTheHeldGroupIsAnsweredFromTheWayIntoIt() {
        Model model = Model.of(THIRTEEN);

        FillResult composed = Generator.fill(model.subject(), List.of(),
                Generator.CandidateCheck.ANY, model.read(), Generator.Trial.NOTHING_RUNS, Budgets.generation());

        assertFalse(composed.discharge().arms().values().isEmpty(), () -> "the arms are answered: " + composed.discharge().arms().values());
        assertTrue(composed.discharge().arms().values().stream().allMatch(ArmDisposition.Built.class::isInstance),
                () -> "each of them with a row through it: " + composed.discharge().arms().values());

        List<GenerationReason.GroupsNotOffered> said = composed.reasons().stream()
                .filter(GenerationReason.GroupsNotOffered.class::isInstance)
                .map(GenerationReason.GroupsNotOffered.class::cast).toList();
        assertEquals(1, said.size(), () -> "the generation says so once: " + composed.reasons());
        assertEquals(1, said.get(0).groups(), "naming how many groups went unwalked");
    }

    /**
     * A group held back that nothing on the list was behind is not reported.
     *
     * <p>What a run owes is the classes and the arms a caller names. Walking a group is how a row
     * for an arm is looked for and is not itself owed, so a run asked for nothing behind a group
     * lost nothing by not walking it — and a line saying no rows were offered there says rows were
     * due where none were.
     *
     * <p>Which is also what keeps the summary in step with the entries. An arm is answered
     * {@code THE_GROUP_WAS_NOT_OFFERED} only where it was owed, so a count of every group held back
     * is a number with no entry under it.
     */
    @Test
    void aGroupNothingWasAskedForBehindIsNotReported() {
        Model model = Model.of(THIRTEEN);

        FillResult asked = Generator.fill(model.subject(), List.of(),
                Generator.CandidateCheck.ANY, model.read(), Generator.Trial.NOTHING_RUNS,
                List.of(), List.of(), List.of(), Budgets.generation());

        assertEquals(List.of(), asked.reasons().stream()
                        .filter(GenerationReason.GroupsNotOffered.class::isInstance).toList(),
                () -> "nothing was owed behind it: " + asked.reasons());
        assertEquals(Map.of(), asked.discharge().arms(), "and no arm was answered for");
    }

    /**
     * Through a compilation, an arm the rows leave owed is offered one whatever the limit did.
     *
     * <p>The production path, where what is owed comes from what measuring the arms established
     * rather than from a set a caller wrote. Held with the same model twice and only the rows
     * differing, so what the two answers are is the rows' and not the model's — and the group is
     * past the budget in both, which is what says the difference is the obligation and not the
     * limit.
     */
    @Test
    void whatIsOwedIsAnsweredWhateverTheLimitDidToTheGroup() {
        List<ArmDisposition> owed = armsFor(modelWithRows(13, false));
        assertFalse(owed.isEmpty(),
                "one row leaves the other arm of each helper owed, and each is answered");
        assertTrue(owed.stream().allMatch(ArmDisposition.Built.class::isInstance),
                () -> "with a row through it, from the way in the reading has for it: " + owed);
        assertEquals(1, groupsNotOfferedFor(modelWithRows(13, false)).size(),
                "and the group the limit held back is named, its combinations not being looked in");

        assertEquals(List.of(), armsFor(modelWithRows(13, true)),
                "two rows take every arm, so nothing is owed at all");
        assertEquals(List.of(), groupsNotOfferedFor(modelWithRows(13, true)),
                "and the group costs this run nothing, so it is not named");
    }

    /** What the compilation's own generation came to at each arm it was owed one at. */
    private static List<ArmDisposition> armsFor(String source) {
        return List.copyOf(fillingFor(source).composed().discharge().arms().values());
    }

    private static List<GenerationReason.GroupsNotOffered> groupsNotOfferedFor(String source) {
        return fillingFor(source).composed().reasons().stream()
                .filter(GenerationReason.GroupsNotOffered.class::isInstance)
                .map(GenerationReason.GroupsNotOffered.class::cast).toList();
    }

    private static souther.compiler.query.Adequacy.Filling fillingFor(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(souther.compiler.query.Adequacy.Asked.fullReport());
        compilation.answerEverything();
        // The rows have to have run for what is owed to be what the arms established. A model whose
        // examples failed is owed nothing for a reason of its own, and an empty answer from one of
        // those would look exactly like the answer this test is about.
        assertEquals(List.of(), compilation.db().allReports().stream()
                        .filter(found -> found.report().isError())
                        .map(found -> found.report().diagnostic().code().toString()).toList(),
                "the model compiles and its rows run");
        Map<String, souther.compiler.query.Adequacy.Filling> filling =
                souther.compiler.query.Adequacy.generatedOf(compilation.db(), "example.wide");
        assertNotNull(filling, "the module was asked for rows");
        souther.compiler.query.Adequacy.Filling total = filling.get("total");
        assertNotNull(total, "the behavior was asked for rows");
        return total;
    }

    /**
     * Three inner meetings and an outer one over their values, so an arm is claimed by two groups.
     *
     * <p>Each {@code +} settles a value from two decisions, and the {@code *} over the three is a
     * meeting of those values — so the outer group's factors are the inner values and its claims
     * carry the arms on the way to them. The outer group has four times four times four choices and
     * each inner one has four, which is a budget between them away from being reachable.
     */
    private static final String NESTED = """
            module example.nested

            data On
            data Off
            data Flag = On | Off

            data Fee = Int
                invariant value >= 0

            let fee1 (f: Flag): Int = match f with | On -> 1 | Off -> 0
            let fee2 (f: Flag): Int = match f with | On -> 2 | Off -> 0
            let fee3 (f: Flag): Int = match f with | On -> 4 | Off -> 0
            let fee4 (f: Flag): Int = match f with | On -> 8 | Off -> 0
            let fee5 (f: Flag): Int = match f with | On -> 16 | Off -> 0
            let fee6 (f: Flag): Int = match f with | On -> 32 | Off -> 0

            behavior total : (p1: Flag, p2: Flag, p3: Flag, p4: Flag, p5: Flag, p6: Flag) -> Fee
                constructs Fee

            let total (p1, p2, p3, p4, p5, p6) =
                Fee((fee1(p1) + fee2(p2)) * (fee3(p3) + fee4(p4)) * (fee5(p5) + fee6(p6)))

            example total
                | "one" : (On, On, On, On, On, On) -> Fee(3 * 12 * 48)
            """;

    /**
     * A group held back is named for the walk that was not made, and costs no arm its row.
     *
     * <p>Two things and the line says the first. What the limit settled is that none of that
     * group's combinations was looked in, which nothing else reports: the combination measure
     * counts what the rows were seen doing, and a walk this declined to make is not that. What it
     * did not settle is whether the arms behind it are owed rows — every one of them is on the way
     * into one of the three inner groups, and each is answered from the way into it either way.
     *
     * <p>Which is why the line is about the combinations rather than about rows. Written as rows
     * not offered, it said a row was owed where one is written two lines above.
     */
    @Test
    void aHeldGroupIsNamedForTheWalkAndCostsNoArmItsRow() {
        Model model = Model.of(NESTED);
        AdequacyPolicy.OfTheGeneration budget = atMost(8);

        InteractionCells.Offered offered =
                InteractionCells.of(model.groups(), model.subject().axes().axes(), budget);
        assertEquals(1, offered.notOffered().size(), "the outer group is past the budget");
        assertEquals(3, offered.groups().size(), "and the three inner ones are offered");

        FillResult composed = Generator.fill(model.subject(), List.of(),
                Generator.CandidateCheck.ANY, model.read(), Generator.Trial.NOTHING_RUNS, budget);

        // The held group is one arms were owed behind: without this, the answer below would hold of
        // a group that claimed nothing and would say nothing about when a group is named.
        Set<ArmProbe> owed = Generator.everyArmACombinationMayTake(
                model.subject(), model.groups(), budget);
        Set<ArmProbe> behindTheHeldGroup = new LinkedHashSet<>(armsIn(offered.notOffered().get(0)));
        behindTheHeldGroup.retainAll(owed);
        assertFalse(behindTheHeldGroup.isEmpty(),
                "arms were owed behind the group that was held back");

        assertTrue(composed.discharge().arms().values().stream().allMatch(ArmDisposition.Built.class::isInstance),
                () -> "every arm behind it has a row through it: " + composed.discharge().arms().values());
        assertEquals(1, composed.reasons().stream()
                        .filter(GenerationReason.GroupsNotOffered.class::isInstance).count(),
                () -> "and the walk that was not made is still said: " + composed.reasons());
    }

    /** Which arms a group the limit held back could have been searched at. */
    private static List<ArmProbe> armsIn(InteractionCells.NotOffered held) {
        List<ArmProbe> out = new java.util.ArrayList<>();
        for (souther.compiler.coverage.ControlClaim claim : held.claims()) {
            if (claim.at() instanceof souther.compiler.coverage.ControlPlace.Arm arm
                    && arm.probe().isPresent()) {
                out.add(arm.probe().get());
            }
        }
        return out;
    }

    /** The behavior's inputs, its axes and the groups its body meets at, off one compile. */
    private record Model(MeasuredInput subject, CoverageRead.Read read) {

        /** The groups of the one reading, for a caller asking about the combinations alone. */
        List<Interaction> groups() {
            return read.interactions();
        }

        static Model of(String source) {
            Compilation compilation = Compilation.ofSource(source, "Main");
            compilation.answerEverything();
            String module = compilation.modules().get(0);
            Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
            Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
            assertNotNull(prepared, "the model compiles");
            assertNotNull(sigs);
            assertNotNull(checked);
            Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                    .filter(b -> b.name().equals("total")).findFirst().orElseThrow();
            InputDomain inputs = compilation.db()
                    .ask(new souther.compiler.query.Adequacy.Inputs(module)).value().get("total");
            assertNotNull(inputs, "the behavior's inputs were read");
            Partitions.Partitioning partitioning = Partitions.of(spec.name(), inputs, rules,
                    souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
            Core body = checked.behaviorBodies().get("total");
            assertNotNull(body, "the behavior under test has a body");
            CoverageSites.Plan plan = checked.plan();
            return new Model(MeasuredInput.of(spec.name(), inputs.reading(rules),
                    partitioning),
                    CoverageRead.of("total", body, plan, inputs, rules));
        }
    }
}
