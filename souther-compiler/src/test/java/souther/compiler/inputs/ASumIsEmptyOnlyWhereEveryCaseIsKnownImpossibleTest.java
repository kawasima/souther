package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Emptiness;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two halves of what a choice between alternatives means.
 *
 * <p>A sum has a value wherever any of its cases does, so what proves it has none is every case at
 * once — and a case this reading never entered is not one of those. Nothing was shown about it,
 * which is not the same as its having been shown to hold nothing, and a proof built from it would
 * say a model has no value because this compiler stopped looking.
 *
 * <p>Beside {@link AViableCaseSurvivesADeadSiblingTest}, which measures that one refused case takes
 * nothing with it. What this measures is that the reading can still prove the thing it is supposed
 * to prove, and that it stops proving it exactly where it stops knowing.
 */
class ASumIsEmptyOnlyWhereEveryCaseIsKnownImpossibleTest {

    private static final String EVERY_CASE_REFUSED = """
            module g

            data A = { x: Int }
                invariant impossible = x >= 1 && x <= 0
            data B = { y: Int }
                invariant impossible = y >= 1 && y <= 0
            data Q = A | B

            data Holder = { q: Q }

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same, with one case left standing, so that what refuses the input is the whole list. */
    private static final String ONE_CASE_STANDS =
            EVERY_CASE_REFUSED.replace("    invariant impossible = y >= 1 && y <= 0\n", "");

    /**
     * A sum whose cases the reading never enters, because the walk turns back where a path returns
     * to a declaration already open on it.
     */
    private static final String NEVER_ENTERED = """
            module g

            data Leaf = { n: Int }
                invariant impossible = n >= 1 && n <= 0
            data Node = { left: Tree, right: Tree }
            data Tree = Leaf | Node

            data Holder = { tree: Tree }

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    @Test
    void everyCaseRefusedLeavesTheInputNone() {
        Optional<EmptyInput> why = emptinessOf(EVERY_CASE_REFUSED);

        assertTrue(why.isPresent(), "no value of Q is left, so no value of the input is");
        Emptiness proof = ((EmptyInput.ProvedByTheRules) why.orElseThrow()).why();
        Emptiness.AtAField at = assertInstanceOf(Emptiness.AtAField.class, proof,
                "the lack is at the sum, which is the place a reader is sent to");
        assertEquals(new Emptiness.AtAField.Where.In("h.q"), at.where());
        assertEquals(2, assertInstanceOf(Emptiness.AcrossEveryCase.class, at.under(),
                "and it is proved over every case rather than by one of them").cases().size());
    }

    @Test
    void andOneStandingCaseIsEnoughToLeaveIt() {
        assertEquals(Optional.empty(), emptinessOf(ONE_CASE_STANDS),
                "every B is a row this behavior takes");
    }

    /**
     * A case this reading did not enter proves nothing.
     *
     * <p>The walk stops where a path returns to a declaration already open on it, so the sums under
     * {@code Node} are met and never read. Read as cases that hold nothing, the recursion itself
     * would refuse every model that has one.
     */
    @Test
    void aCaseThisReadingDidNotEnterProvesNothing() {
        assertEquals(Optional.empty(), emptinessOf(NEVER_ENTERED),
                "a tree of one node is a row this behavior takes, and the deeper sums were never"
                        + " read");
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
