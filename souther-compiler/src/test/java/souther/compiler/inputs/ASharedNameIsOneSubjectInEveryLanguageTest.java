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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name the cases of a sum share is one subject in every language the rules are read in.
 *
 * <p>What the value above says about it and what the case says about it are about the same thing,
 * whether what they say is a relation between numbers, a bound on an order, or which values may
 * stand there. Carried as one subject for the arithmetic and as two for the rest, a clause of the
 * sum and a clause of the case stop meeting the moment neither of them is about a number — and
 * nothing says so, because two subjects that never meet leave every answer wider rather than wrong.
 *
 * <p>Beside {@link ARelationOverSharedNamesReachesTheNumbersTheyStandAtTest}, which measures the
 * same crossing where the rules are arithmetic.
 */
class ASharedNameIsOneSubjectInEveryLanguageTest {

    /**
     * A bound on an order, said above and said in every case, that no value meets.
     *
     * <p>Neither clause refuses anything on its own. Read as one subject they leave the name
     * nothing, and every case of the sum is a case no value stands at.
     */
    private static final String ON_AN_ORDER = """
            module g

            data Shared = { tag: String }
            data A = { ...Shared, x: Int }
                invariant late = tag >= "M"
            data B = { ...Shared, y: Int }
                invariant late = tag >= "M"
            data Q = A | B

            data Holder = { q: Q }
                invariant early = q.tag <= "A"

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same model with the rule the value above wrote taken out. */
    private static final String ON_AN_ORDER_UNRULED =
            ON_AN_ORDER.replace("    invariant early = q.tag <= \"A\"\n", "");

    /**
     * Which values may stand at the name, said above and said in every case.
     *
     * <p>An equality is a set of one and an exclusion is that set taken away, and neither is a range
     * — so this is the reading that answers where the order and the arithmetic have nothing to say.
     */
    private static final String ON_THE_VALUES = """
            module g

            data Shared = { tag: String }
            data A = { ...Shared, x: Int }
                invariant notThat = tag /= "A"
            data B = { ...Shared, y: Int }
                invariant notThat = tag /= "A"
            data Q = A | B

            data Holder = { q: Q }
                invariant thatOne = q.tag == "A"

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    private static final String ON_THE_VALUES_UNRULED =
            ON_THE_VALUES.replace("    invariant thatOne = q.tag == \"A\"\n", "");

    @Test
    void whatTheValueAboveBoundsOnAnOrderMeetsWhatTheCaseBounds() {
        assertTrue(emptinessOf(ON_AN_ORDER).isPresent(),
                "no tag is both at most \"A\" and at least \"M\", so no case of Q has a value");
    }

    @Test
    void andWhichValuesItAdmitsMeetsWhatTheCaseAdmits() {
        assertTrue(emptinessOf(ON_THE_VALUES).isPresent(),
                "no tag is both \"A\" and not \"A\", so no case of Q has a value");
    }

    /** And with the rule above taken out, each of them leaves the input its values. */
    @Test
    void andWithoutTheRuleAboveNothingRefusesTheInput() {
        for (String source : List.of(ON_AN_ORDER_UNRULED, ON_THE_VALUES_UNRULED)) {
            assertEquals(Optional.empty(), emptinessOf(source),
                    "what one case says of the name refuses nothing on its own");
        }
    }

    private static Optional<EmptyInput> emptinessOf(String source) {
        InputDomain read = reading(source, "read");
        return read.quantities(rulesOf(source)).emptiness();
    }

    private static RuleReadingSource rulesOf(String source) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        return RuleReadings.of(compilation, compilation.modules().get(0));
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
