package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wrapping a value in a name does not change what its rules leave or who accounts for it.
 *
 * <p>The rules are the same rules and the value is the same value: a newtype over a type states
 * what that type states, and a clause written on either reaches the value it is about. So the two
 * models leave the position the same values, and the same authored lines are owed a row at the same
 * places.
 *
 * <p>Which the counterfactual has to be asked on both roads for. A clause reaches a value either as
 * its own declaration's, which the gathering takes in, or as the declaration it wraps, which the
 * guarantee walk does — and a reading asked to leave a conjunct out on one road only leaves it in
 * on the other. What comes back then is a reading identical to the whole one, which is the answer a
 * set of rules that accounts for nothing gets.
 */
class WrappingAValueDoesNotChangeWhatItsRulesAccountForTest {

    /** Two clauses on the declaration the position's type is. */
    private static final String PLAIN = """
            data Held = Int
                invariant atLeastNought = value >= 0
                invariant notNought = value /= 0
            """;

    /** The same two, on a declaration this one wraps. */
    private static final String WRAPPED = """
            data Inner = Int
                invariant atLeastNought = value >= 0
                invariant notNought = value /= 0

            data Held = Inner
            """;

    /** One on each, which is the shape a wrapper is usually written in. */
    private static final String SPLIT = """
            data Inner = Int
                invariant atLeastNought = value >= 0

            data Held = Inner
                invariant notNought = value /= 0
            """;

    @Test
    void theRulesLeaveThePositionTheSameValuesEitherWay() {
        assertEquals(rangeOf(PLAIN), rangeOf(WRAPPED));
        assertEquals(rangeOf(PLAIN), rangeOf(SPLIT));
    }

    /**
     * And the same lines are owed a row, at the same places.
     *
     * <p>Read as the clause and the conjunct rather than as the declaration: which declaration a
     * clause is written on is what the models differ in, and it is no part of what a row at the
     * line is owed for.
     */
    @Test
    void theSameLinesAreOwedARowEitherWay() {
        assertEquals(owedBy(PLAIN), owedBy(WRAPPED));
        assertEquals(owedBy(PLAIN), owedBy(SPLIT));
        assertEquals(List.of("atLeastNought#0 LOWER at 1", "notNought#0 LOWER at 1"),
                owedBy(PLAIN),
                "both clauses hold the values at one, and each is a clause an author can rewrite");
    }

    /** Which authored lines are owed a row, as the clauses that drew them are named. */
    private static List<String> owedBy(String declarations) {
        return boundariesOf(declarations).stream()
                .map(WrappingAValueDoesNotChangeWhatItsRulesAccountForTest::said)
                .sorted().toList();
    }

    /** One line, said without the declaration it happens to be written on. */
    private static String said(BorderAssessment border) {
        if (!(border.origin() instanceof LineOrigin.InvariantOrigin invariant)) {
            throw new AssertionError("this line was not drawn by an invariant: " + border.origin());
        }
        return invariant.rule().clause().name().orElseThrow() + "#" + invariant.part().ordinal()
                + " " + invariant.keeps() + " at " + border.value();
    }

    private static List<BorderAssessment> boundariesOf(String declarations) {
        Compilation compilation = Compilation.ofSource(sourceOf(declarations), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), "example.wrapping");
        if (boundaries == null) {
            throw new AssertionError("the model under test compiles: " + declarations);
        }
        return boundaries.values().stream().flatMap(List::stream).toList();
    }

    /** What the rules leave the value standing at the position, once every one of them has been
     *  read. The clauses here are about that number and nothing counts an {@code Int}. */
    private static NumericDomain.Bounds rangeOf(String declarations) {
        Compilation compilation = Compilation.ofSource(sourceOf(declarations), "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        TermPath path = TermPath.of("n");
        return InputDomain.of(sigs.get("take"), rules, ReadAs.THE_COMPILATION_DOES)
                .at(path).boundsFor(new NumericTerm.ValueOf(path)).rangeLeft();
    }

    private static String sourceOf(String declarations) {
        return """
                module example.wrapping

                %s
                data Ok

                behavior take : (n: Held) -> Ok
                let take (n) = Ok
                """.formatted(declarations);
    }
}
