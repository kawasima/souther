package souther.compiler.query;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.partition.AdequacyPolicy;
import souther.compiler.partition.Budgets;
import souther.compiler.partition.GenerationReason;
import souther.compiler.regex.PatternPlan;
import souther.compiler.DocumentShape;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a measurement may spend is the compilation's to set, and what spending it costs is reported.
 *
 * <p>Two things and the second is why the first matters. That the number can be set is rule 4; that
 * a measurement held to a small one says what it was short of is rule 3. Tested together because a
 * budget that can be lowered and reports nothing is the arrangement issue #969 removed, under a new
 * name.
 *
 * <p>Held against a small budget rather than a large model. Every default is set with room over
 * anything in this repository, so the only way to reach the path past one is to say what the budget
 * is — which is what having it as an input is for, and what a private constant made impossible.
 */
class ABudgetIsTheCompilationsToSetTest {

    /** Two positions of two classes each, which is four pairs — one more than a budget of three. */
    private static final String FOUR_PAIRS = """
            module example.pairs

            data A
            data B
            data Flag = A | B
            data Res = { n: Int }

            behavior pick : (x: Flag, y: Flag) -> Res
                constructs Res
            let pick (x, y) = Res { n = 1 }

            example pick
                | "one" : (A, A) -> Res { n = 1 }
            """;

    /**
     * A pair space past the budget leaves the measure partial and says which limit did it.
     *
     * <p>The same model twice, and only the budget differs — so the two answers are the budget's
     * and not the model's. Read with one compilation, a partial measurement is as good an account
     * of a model this cannot read at all.
     */
    @Test
    void aPairSpacePastTheBudgetIsReportedAsPartial() {
        PartitionEvidence wide = evidenceFor(FOUR_PAIRS, Budgets.measures().pairSpace());
        PartitionEvidence narrow = evidenceFor(FOUR_PAIRS, 3);

        assertInstanceOf(Measurement.Complete.class, wide.pairs().counted(),
                () -> "at the standard budget the space is walked: " + wide.pairs());
        assertInstanceOf(Measurement.Partial.class, narrow.pairs().counted(),
                () -> "and past a budget of three it is not: " + narrow.pairs());
        assertEquals(4, narrow.pairs().total(),
                "the size of the space is what the model says, whatever was walked of it");
    }

    /**
     * A budget that admits nothing is refused where it is written.
     *
     * <p>Each of the three, because each is a different sentence about a different walk. A bound of
     * nought is not a small bound: it measures nothing and reports every model as partial over a
     * space it never entered, which is a compilation saying less about every model than one with no
     * bound at all would.
     */
    @Test
    void aBudgetBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new AdequacyPolicy.OfTheMeasures(0,
                        PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS), "a pair space of nought");
        assertThrows(IllegalArgumentException.class,
                () -> new AdequacyPolicy.OfTheMeasures(20_000, null),
                "nothing to build a behavior's distinctions with");
        assertThrows(IllegalArgumentException.class,
                () -> new AdequacyPolicy.OfTheGeneration(0, 4096), "no rows");
        assertThrows(IllegalArgumentException.class,
                () -> new AdequacyPolicy.OfTheGeneration(200, 0), "no cells");
    }

    /**
     * A cell budget the compilation sets reaches the generation, which is the other half of rule 4.
     *
     * <p>The measures' half is above and would hold on its own while the generation's wiring was
     * cut. The two travel by different routes — one through {@code Adequacy.Coverage} and one
     * through {@code Adequacy.Generated} and {@code rowsFor} — so a test of the first says nothing
     * about the second, and the generation's budget reaching the search was, until this, held by
     * nothing at all.
     *
     * <p>Held through the query and not by handing a budget to {@code Generator}. What is being
     * tested is the handing over: a search given a budget directly would answer the same whether or
     * not {@code Front.Adequacy} were still wired to it.
     */
    @Test
    void aCellBudgetTheCompilationSetsReachesTheGeneration() {
        assertTrue(offeredUnder(Budgets.generation().cellsPerGroup()).isEmpty(),
                "at the standard budget the group is walked and nothing is held back");

        List<GenerationReason.GroupsNotOffered> held = offeredUnder(1);
        assertEquals(1, held.size(),
                () -> "and at a budget of one it is not: " + held);
        assertEquals(1, held.get(0).groups(), "one group went unwalked");
    }

    /** And the standard one is a policy, so the three numbers are read from one place. */
    @Test
    void theStandardBudgetIsWhatACompilationSets() {
        assertTrue(Budgets.measures().pairSpace() > 0);
        assertTrue(Budgets.generation().rowLimit() > 0);
        assertTrue(Budgets.generation().cellsPerGroup() > 0);
    }

    /**
     * And the document a measure held to a small budget comes out in is shaped the way the schema
     * says.
     *
     * <p>The word for what a behavior's distinctions cost is one no model reaches on its own: every
     * default is set with room over anything in this repository, so the way to the state is to say
     * what the measure may spend, and saying that is this package's to do.
     *
     * <p>What is asked of the document here is its shape, which is the walk's question and not the
     * word's. Whether every reason this compiler may write in that field is one some compilation
     * writes is asked of the whole of them in one place
     * ({@link EveryNotReadReasonIsWrittenBySomeCompilationTest}), and that table says down an
     * allowance for this word as this does — so the two are told apart by what they ask rather than
     * by what their models are allowed.
     *
     * <p>The walk is the one that reads every document this compiler writes
     * ({@link DocumentShape}); what is added here is a document it cannot make, because saying what
     * a measure may spend is this package's to do.
     */
    @Test
    void aDocumentWrittenWhereTheDistinctionsCostTooMuchIsShapedTheWayTheSchemaSays() {
        Compilation compilation = Compilation.ofSource(TELLS_STRINGS_APART, "Main")
                .withAdequacyPolicy(new AdequacyPolicy(
                        new AdequacyPolicy.OfTheMeasures(Budgets.measures().pairSpace(),
                                new PatternPlan.Budget(1, 1)),
                        Budgets.generation()));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String document = AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts()));

        assertTrue(document.contains("behavior_distinctions_too_costly"),
                "the measure was held to less than the rule's two sides take: " + document);
        DocumentShape.assertShapedLikeTheSchema(JsonMapper.builder().build().readTree(document));
    }

    /** A behavior whose rule about the strings at a position tells a set of them from the rest,
     *  which is what the allowance above cannot afford to build. */
    private static final String TELLS_STRINGS_APART = """
            module example.strings

            data Yes
            data No
            data Answer = Yes | No

            behavior route : (code: String) -> Answer
            let route (code) = if String.startsWith("JP", code) then Yes else No
            """;

    /** What the generation said about groups it did not offer, over a model of two decisions
     *  meeting on one value, compiled under a budget of {@code cells} choices per group. */
    private static List<GenerationReason.GroupsNotOffered> offeredUnder(int cells) {
        Compilation compilation = Compilation.ofSource(TWO_DECISIONS, "Main")
                .withAdequacyPolicy(new AdequacyPolicy(
                        Budgets.measures(),
                        new AdequacyPolicy.OfTheGeneration(Budgets.generation().rowLimit(), cells)));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, Adequacy.Filling> filling =
                Adequacy.generatedOf(compilation.db(), "example.two");
        assertNotNull(filling, "the module was asked for rows");
        Adequacy.Filling shipping = filling.get("shippingFee");
        assertNotNull(shipping, "the behavior was asked for rows");
        return shipping.composed().reasons().stream()
                .filter(GenerationReason.GroupsNotOffered.class::isInstance)
                .map(GenerationReason.GroupsNotOffered.class::cast).toList();
    }

    /** Two decisions consumed into one value, which is four ways of settling it. Both are matched
     *  on, so no threshold is needed to divide anything. */
    private static final String TWO_DECISIONS = """
            module example.two

            data Premium
            data Standard
            data Membership = Premium | Standard

            data Express
            data Regular
            data Delivery = Express | Regular

            data Fee = Int
                invariant value >= 0

            behavior shippingFee : (member: Membership, delivery: Delivery) -> Fee
                constructs Fee

            let baseFee (tier: Membership): Int =
                match tier with
                    | Premium  -> 0
                    | Standard -> 500

            let expressFee (speed: Delivery): Int =
                match speed with
                    | Express -> 500
                    | Regular -> 0

            let shippingFee (member, delivery) =
                Fee(baseFee(member) + expressFee(delivery))

            example shippingFee
                | "one" : (Premium, Express) -> Fee(500)
            """;

    private static PartitionEvidence evidenceFor(String source, int pairSpace) {
        Compilation compilation = Compilation.ofSource(source, "Main")
                .withAdequacyPolicy(new AdequacyPolicy(
                        new AdequacyPolicy.OfTheMeasures(pairSpace,
                                PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS),
                        Budgets.generation()));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, PartitionEvidence> partitions = compilation.db()
                .ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
        PartitionEvidence evidence = partitions.get("pick");
        assertNotNull(evidence, "the behavior was measured");
        return evidence;
    }
}
