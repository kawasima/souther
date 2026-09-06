package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Located;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a relation between positions comes to, and what it does not turn on.
 *
 * <p>A relation is a set of pairs and a pair is unordered, so the same rules written another way
 * are the same rules. What is asserted here is that: which side of a denial an author wrote first,
 * which order the clauses are in, and how a conjunction was bracketed all leave one answer.
 *
 * <p>And what it does turn on. A denial one branch of a choice states is not the choice's, and a
 * conjunction that puts two blocks together carries the denials onto the blocks it leaves — both of
 * which are rules about where a relation is filed rather than about what it says.
 */
class WhatDenialsComeToDoesNotTurnOnHowTheyWereWrittenTest {

    private static final String STAGE = """
            module demo

            data Ready
            data Done
            data Stage = Ready | Done

            """;

    private static List<String> saidOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(Located::diagnostic)
                .map(each -> each.said().getClass().getSimpleName())
                .toList();
    }

    private static void refuses(String rule) {
        assertEquals(List.of("NoValuesTheseCanAllDifferIn"), saidOf(STAGE + """
                data Pair = { p: Stage, r: Stage }
                    invariant no = RULE
                """.replace("RULE", rule)), rule);
    }

    private static void admits(String source) {
        assertEquals(List.of(), saidOf(source), "a value of this can be written");
    }

    /** A pair is unordered, so a denial written either way round is one rule. */
    @Test
    void aDenialIsTheSameRuleWrittenEitherWayRound() {
        refuses("p /= r && p == Ready && r == Ready");
        refuses("r /= p && p == Ready && r == Ready");
    }

    /** And the clauses of a conjunction are met in whatever order they are written. */
    @Test
    void andTheClausesAreMetInWhateverOrderTheyAreWritten() {
        refuses("p /= r && p == Ready && r == Ready");
        refuses("p == Ready && p /= r && r == Ready");
        refuses("p == Ready && r == Ready && p /= r");
        refuses("r == Ready && p /= r && p == Ready");
    }

    /** And wherever the brackets fall, since a conjunction of a conjunction is a conjunction. */
    @Test
    void andWhereverTheBracketsFall() {
        refuses("(p /= r && p == Ready) && r == Ready");
        refuses("p /= r && (p == Ready && r == Ready)");
        refuses("(p /= r) && (p == Ready) && (r == Ready)");
    }

    /**
     * A denial one alternative states is not the choice's.
     *
     * <p>The first branch is one nobody can be in and the second is one anybody can, so the choice
     * stands. Lent across, the denial would refuse the branch beside it and a choice would hold a
     * rule neither alternative states.
     */
    @Test
    void aDenialOneBranchStatesIsNotLentToTheBranchBesideIt() {
        admits(STAGE + """
                data Pair = { p: Stage, r: Stage }
                    invariant either = (p /= r && p == Ready && r == Ready)
                        || (p == Ready && r == Ready)
                """);
    }

    /**
     * A denial reaching a block a later equality widens is a denial about the block it leaves.
     *
     * <p>{@code q /= r} is stated of {@code q} on its own, and {@code p == q} makes {@code p} and
     * {@code q} one value — so the denial is between that block and {@code r}, and the rules
     * leaving both of them {@code Ready} leave the pair nothing. Left where it was stated, the
     * denial would name a block the conjunction does not answer in and would reach nothing.
     */
    @Test
    void aDenialIsCarriedOntoTheBlocksAConjunctionLeaves() {
        assertEquals(List.of("NoValuesTheseCanAllDifferIn"), saidOf(STAGE + """
                data Trio = { p: Stage, q: Stage, r: Stage }
                    invariant no = p == q && q /= r && p == Ready && r == Ready
                """), "the denial is between the block p and q are and r");
    }

    /** And the same rules leaving the block and {@code r} different values are admitted. */
    @Test
    void andTheSameRulesLeavingThemDifferentValuesAreAdmitted() {
        admits(STAGE + """
                data Trio = { p: Stage, q: Stage, r: Stage }
                    invariant ok = p == q && q /= r && p == Ready && r == Done
                """);
    }
}
