package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceRendering;
import souther.compiler.partition.AdequacyPolicy;
import souther.compiler.partition.Budgets;
import souther.compiler.partition.GenerationOutcome;
import souther.compiler.partition.Generator;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.report.GeneratedRows;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule the block had no room for is still a rule this run was asked about.
 *
 * <p>What one block hands a person is limited, and what the account is owed is not. The two are
 * different questions and the limit answers only the first: a rule left out of the block is a rule
 * nothing was written for here, not a rule nobody has to answer.
 *
 * <p>Which matters because a row offered for something else may take it. A row composed for a class
 * runs down one way of the body, so it takes a rule — and whether it does is weighed against every
 * obligation this run was asked about. Read off the rows the run managed to compose, a rule the
 * limit stopped at would be in nobody's universe, and the next block would offer a row for work its
 * own row had already done.
 */
class ARuleTheBlockHadNoRoomForIsStillWeighedTest {

    /**
     * A body of two independent decisions, which is four ways through it.
     *
     * <p>The rows take two of those ways, so two rules are owed a row and a block held to one row
     * can carry only one of them.
     */
    private static final String MODEL = """
            module example.narrow

            data Yes
            data No
            data Verdict = Yes | No

            data Safe
            data Risky
            data Mode = Safe | Risky

            data On
            data Off
            data Flag = On | Off

            let choose (flag: Flag, yes: Verdict, no: Verdict): Verdict =
                match flag with
                    | On  -> yes
                    | Off -> no

            behavior decide : (mode: Mode, flag: Flag) -> Verdict
            let decide (mode, flag) =
                match mode with
                    | Safe  -> choose(flag, Yes, Yes)
                    | Risky -> choose(flag, Yes, No)

            example decide
                | "safe on"  : (Safe, On)  -> Yes
                | "risky on" : (Risky, On) -> Yes
            """;

    @Test
    void aRuleTheBlockStoppedShortOfSaysSoAndIsStillAskedAbout() {
        Adequacy.Filling wide = fillingUnder(Budgets.generation().rowLimit());
        List<Adequacy.GenerationDisposition> offered = rulesOf(wide);
        assertTrue(offered.size() > 1,
                () -> "more than one rule is owed a row, so a limit can stop short: " + offered);

        assertTrue(offered.stream()
                        .allMatch(each -> each.outcome() instanceof GenerationOutcome.Generated),
                () -> "a block with room offers a row for each of them: " + offered);

        Adequacy.Filling narrow = fillingUnder(1);
        List<Adequacy.GenerationDisposition> under = rulesOf(narrow);
        assertEquals(offered.size(), under.size(),
                () -> "the same rules are answered for whatever the block has room for: " + under);

        // And what is left over says the block is full rather than going quiet or claiming the
        // search found nothing.
        List<Generator.UnresolvedCombination.Reason> why = under.stream()
                .map(Adequacy.GenerationDisposition::outcome)
                .filter(GenerationOutcome.CannotGenerate.class::isInstance)
                .map(GenerationOutcome.CannotGenerate.class::cast)
                .flatMap(each -> each.why().stream())
                .map(Generator.UnresolvedCombination::reason)
                .distinct().toList();
        assertEquals(List.of(
                        Generator.UnresolvedCombination.Reason.THE_BLOCK_IS_AS_LONG_AS_IT_MAY_BE),
                why,
                () -> "and the rest say the block is full rather than that the search fell short: "
                        + under);

        // And the rule with no row is still something a row is weighed against, which is what the
        // settlement table is: a row composed for anything else that takes it discharges it.
        assertTrue(askedAbout(narrow).stream()
                        .filter(ObligationIdentity.OfADecisionRule.class::isInstance).count() > 1,
                () -> "every rule owed a row is in the universe: " + askedAbout(narrow));

        // The block itself says it, so a person reading one is told why the list is short.
        assertTrue(blockUnder(1).contains("as many rows as it may"),
                () -> "the block says what stopped it:\n" + blockUnder(1));
    }

    /** What this run was asked to weigh a row against, which is the account's own universe. */
    private static List<ObligationIdentity> askedAbout(Adequacy.Filling filling) {
        return filling.rules().asked().stream()
                .map(rule -> (ObligationIdentity) new ObligationIdentity.OfADecisionRule(
                        "decide", rule))
                .toList();
    }

    private static List<Adequacy.GenerationDisposition> rulesOf(Adequacy.Filling filling) {
        return filling.generation().stream()
                .filter(each -> each.finding().about() instanceof About.ARuleNoRowTakes)
                .toList();
    }

    private static Adequacy.Filling fillingUnder(int rowLimit) {
        Map<String, Adequacy.Filling> filled =
                Adequacy.generatedOf(compiled(rowLimit).db(), "example.narrow");
        assertNotNull(filled, "the module was asked for rows");
        Adequacy.Filling decide = filled.get("decide");
        assertNotNull(decide, "the behavior was asked for rows");
        return decide;
    }

    private static String blockUnder(int rowLimit) {
        Compilation compiled = compiled(rowLimit);
        return GeneratedRows.of(compiled, "example.narrow", null,
                SourceRendering.namedByIdentity(compiled.texts())).text();
    }

    private static Compilation compiled(int rowLimit) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main")
                .withAdequacyPolicy(new AdequacyPolicy(Budgets.measures(),
                        new AdequacyPolicy.OfTheGeneration(rowLimit,
                                Budgets.generation().cellsPerGroup())));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertFalse(compilation.modules().isEmpty(), "the model compiled");
        return compilation;
    }
}
