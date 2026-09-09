package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a question assumes stands is read into the rules it is answered against.
 *
 * <p>Naming a position inside a sequence assumes the sequence holds something. That is a fact about
 * the rows being asked about, and it is one the rules have a word for — so it belongs in the state
 * the question is answered out of, and not only in the decision about whose rules to read.
 *
 * <p>Read as the second alone, a context whose prerequisites the declarations refuse comes back as
 * a reading that leaves values, and a number the declarations tie to a length comes back running
 * where no row of that question puts it.
 */
class WhatAContextAssumesIsReadIntoTheRulesTest {

    private static final String NO_ROOM = """
            module g

            data Item = { n: Int }
            data Holder = { items: List<Item> }
                invariant empty = List.length(items) <= 0

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    private static final String COUNTED = """
            module g

            data Item = { n: Int }
            data Holder = { items: List<Item>, count: Int }
                invariant same = count == List.length(items)

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /**
     * A number fixed inside a sequence the declarations leave no room in.
     *
     * <p>Fixing it says the sequence holds something; the rule says it holds nothing. Neither refuses
     * anything on its own, and no row meets both.
     */
    @Test
    void aNumberFixedInsideASequenceTheRulesLeaveEmptyRefusesTheInput() {
        assertTrue(withAnElementFixed(NO_ROOM, "h.items[*].n").emptiness().isPresent(),
                "the rule leaves the list no room and the fixing puts something in it");
    }

    /** And with nothing fixed, the empty list is a row this behavior takes. */
    @Test
    void andWithNothingFixedTheEmptyListIsARow() {
        InputDomain read = reading(NO_ROOM, "read");

        assertEquals(Optional.empty(), read.quantities(rulesOf(NO_ROOM)).emptiness(),
                "nothing asked for an element, so nothing needs the list to hold one");
    }

    /**
     * And a number the declarations tie to the length runs where the question puts it.
     *
     * <p>The rule says the count is the length; the question says the list holds something. So the
     * count is at least one — which no clause writes down and the question does.
     */
    @Test
    void aNumberTiedToTheLengthKnowsTheSequenceHoldsSomething() {
        InputDomain read = reading(COUNTED, "read");
        NumericDomain.Bounds runs = withAnElementFixed(COUNTED, "h.items[*].n")
                .runsBetween(new NumericTerm.ValueOf(pathOf(read, "h.count")));

        assertNotNull(runs, "h.count is a number this reading answers about");
        assertNotNull(runs.min(), () -> "h.count starts where the question puts it, and runs "
                + runs);
        assertEquals("1", Count.number(runs.min().at()).at().stripTrailingZeros().toPlainString(),
                "the list holds something, so the count it equals is at least one");
    }

    /** Where {@code element} is fixed at one, which is a question about rows whose list holds it. */
    private static Quantities withAnElementFixed(String source, String element) {
        InputDomain read = reading(source, "read");
        return read.quantities(rulesOf(source))
                .given(new NumericTerm.ValueOf(pathOf(read, element)), Count.of(BigDecimal.ONE));
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
