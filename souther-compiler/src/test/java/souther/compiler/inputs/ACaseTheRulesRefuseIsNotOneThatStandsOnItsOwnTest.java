package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Whether the rules leave a case is asked before what stands under it.
 *
 * <p>The two are different questions and one presupposes the other. That naming a case builds it
 * says there is nothing under it to read; it does not say a value may stand there. A case with
 * nothing under it that the rules refuse is both at once, and read as the second it comes back as
 * the plainest kind of value there is — which is what every reader asking whether a sum has a value
 * then takes for a witness.
 */
class ACaseTheRulesRefuseIsNotOneThatStandsOnItsOwnTest {

    private static final String REFUSED = """
            module g

            data Prospecting
            data Qualified
            data Won
            data Stage = Prospecting | Qualified | Won

            data StageI = Stage
                invariant onlyLater = value >= Qualified

            data Holder = { s: StageI }

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same model with the rule taken out, so that what the rule does is what is measured. */
    private static final String UNRULED =
            REFUSED.replace("    invariant onlyLater = value >= Qualified\n", "");

    @Test
    void aBareCaseTheRulesRefuseIsRefusedAndNotStanding() {
        NameReach.NotEntered why = whyNotEntered(REFUSED, "Prospecting");

        assertInstanceOf(NameReach.NotEntered.TheRulesLeaveNothingAtIt.class, why,
                "the rules leave no Prospecting, which is why the reading did not go down it");
    }

    @Test
    void andOneNothingRefusesStandsOnItsOwn() {
        NameReach.NotEntered why = whyNotEntered(UNRULED, "Prospecting");

        assertInstanceOf(NameReach.NotEntered.NothingStandsUnderIt.class, why,
                "naming the case builds it, and no rule says otherwise");
    }

    /** And the cases the rule leaves are read the same way it always was. */
    @Test
    void aCaseTheRuleLeavesIsStillOneThatStandsOnItsOwn() {
        for (String at : List.of("Qualified", "Won")) {
            assertInstanceOf(NameReach.NotEntered.NothingStandsUnderIt.class,
                    whyNotEntered(REFUSED, at),
                    at + " is left by the rule, and naming it builds it");
        }
    }

    private static NameReach.NotEntered whyNotEntered(String source, String branch) {
        List<NameReach.BranchNotEntered> not =
                reading(source, "read").reach().branchesNotEntered().stream()
                        .filter(each -> each.branch().spelled().equals(branch))
                        .toList();

        assertEquals(1, not.size(),
                () -> "the reading turned back at " + branch + " once, and said so once");
        return not.get(0).why();
    }

    private static InputDomain reading(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), rules, ReadAs.THE_COMPILATION_DOES);
    }
}
