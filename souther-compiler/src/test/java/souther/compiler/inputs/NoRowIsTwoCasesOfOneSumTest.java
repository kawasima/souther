package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.numeric.Count;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Numbers fixed under two cases of one sum are fixed in no row.
 *
 * <p>A position holds one value, so a value that is an {@code A} is not also a {@code B}, and a
 * caller that put a number under each of them has asked about a row nothing can write. The rules
 * were never consulted: what contradicts is the pair of assignments, which is what tells this from
 * an input the declarations refuse.
 *
 * <p><b>And the answer does not carry the order the fixings arrived in.</b> Two of them said either
 * way round are the same two, so which position a refusal names, and which two narrowings it names
 * there, are settled by the model rather than by which question the caller asked first.
 */
class NoRowIsTwoCasesOfOneSumTest {

    private static final String MODEL = """
            module g

            data Shared = { lo: Int, hi: Int }
            data A = { ...Shared, x: Int }
            data B = { ...Shared, y: Int }
            data Q = A | B

            data Holder = { q: Q }

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    @Test
    void twoCasesFixedAtOncePutTheRowNowhere() {
        EmptyInput.TwoRefinementsAtOnePosition why = bothFixed(false);

        assertEquals("h.q", why.at().toString(), "the position that cannot be both");
        assertEquals("A", why.one().spelled());
        assertEquals("B", why.other().spelled());
    }

    @Test
    void andTheOrderTheyWereFixedInIsNoPartOfTheAnswer() {
        assertEquals(bothFixed(false), bothFixed(true),
                "the same two fixings, whichever way round they were said");
    }

    /** Both positions fixed, in one order or the other. */
    private static EmptyInput.TwoRefinementsAtOnePosition bothFixed(boolean reversed) {
        InputDomain read = reading(MODEL, "read");
        NumericTerm underA = new NumericTerm.ValueOf(pathOf(read, "h.q@A.lo"));
        NumericTerm underB = new NumericTerm.ValueOf(pathOf(read, "h.q@B.lo"));
        Quantities asked = read.quantities(rulesOf(MODEL));
        Optional<EmptyInput> why = (reversed
                ? asked.given(underB, Count.of(BigDecimal.TWO))
                        .given(underA, Count.of(BigDecimal.ONE))
                : asked.given(underA, Count.of(BigDecimal.ONE))
                        .given(underB, Count.of(BigDecimal.TWO)))
                .emptiness();

        return assertInstanceOf(EmptyInput.TwoRefinementsAtOnePosition.class,
                why.orElseThrow(() -> new AssertionError("no row is both cases, and nothing said so")),
                "what contradicts is the pair of assignments and not anything the rules say");
    }

    private static TermPath pathOf(InputDomain read, String spelled) {
        return read.positions().stream().map(Position::path)
                .filter(each -> each.toString().equals(spelled))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no position at " + spelled + " among " + read.positions().stream()
                                .map(Position::path).toList()));
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
