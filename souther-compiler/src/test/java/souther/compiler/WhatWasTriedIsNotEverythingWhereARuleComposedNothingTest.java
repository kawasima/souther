package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block says every value was refused only where every value there was got tried.
 *
 * <p>Two things a search can come back with, and they read alike from inside it. Where every rule
 * about a position reached the offer, the values tried are the values the rules leave and a reader
 * told they were all refused may go looking for the rule that refuses them. Where a rule composed
 * nothing — this compiler could not read it, or could not afford the machine for it — the values
 * tried came from the rules beside it, and the same sentence sends that reader after a rule that
 * refuses nothing.
 *
 * <p>So the sentence turns on what was offered rather than on what came back, and the pair below is
 * what holds it: the same refusals, once with everything offered and once without.
 *
 * <p>What a shortfall is not is a reason to say nothing. A rule that composed nothing leaves the
 * rules beside it composing as they did, and where one of their values clears the whole of the
 * rules a row is written and the shortfall is not news about it.
 */
class WhatWasTriedIsNotEverythingWhereARuleComposedNothingTest {

    /** A rule about the strings written in a construct this compiler's reader does not enter. */
    private static final String OUTSIDE_THE_SUBSET = "String.matches(\"(a+)\\\\1\", value)";

    /**
     * One it enters and cannot build a machine for within what composing a value may spend.
     *
     * <p>A pattern whose machine is large rather than one whose building is long: what is being
     * held here is the sentence a run gets when the allowance refuses it, and a fixture that spends
     * the whole allowance to arrive at the same sentence costs every run of this suite the spending.
     */
    private static final String MORE_THAN_A_WITNESS_MAY_SPEND =
            "String.matches(\".*a.{20}\", value)";

    private static String blockFor(String rule) {
        String model = """
                module example.offer

                data Code = String
                    invariant shape = RULE

                data Flag = Yes | No

                data T = { flag: Flag, code: Code }

                data Ok

                behavior look : (t: T) -> Ok

                let look (t) = Ok
                """.replace("RULE", rule);
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return GeneratedRows.of(compilation, "example.offer", "look",
                SourceRendering.namedByIdentity(compilation.texts())).text();
    }

    /**
     * A rule outside the subset leaves a search that tried less than the rules leave, and the block
     * says so rather than saying the refusals were of everything.
     */
    @Test
    void aRuleThisCompilerCannotReadIsNotEveryValueRefused() {
        String block = blockFor(OUTSIDE_THE_SUBSET);

        assertTrue(block.contains("`t.code` holds a rule this compiler could not read"), block);
        assertFalse(block.contains("every value tried was refused at construction, which does not"
                + " make the combination impossible"),
                () -> "the values tried came from the rules beside the one that composed"
                        + " nothing:\n" + block);
    }

    /**
     * And an allowance run down says its own thing, because an author raises a figure about it and
     * has no rule to rewrite.
     */
    @Test
    void anAllowanceRunDownSaysWhichFigureItWas() {
        String block = blockFor(MORE_THAN_A_WITNESS_MAY_SPEND);

        assertTrue(block.contains("working a value out of a rule on `t.code`"), block);
        assertFalse(block.contains("could not read"),
                () -> "this rule was read from end to end, and an author sent after its form would"
                        + " find nothing the matter with it:\n" + block);
    }

    /**
     * The other half of the pair. Every rule about the position reached the offer, every value it
     * left was tried, and every one was refused — which is the sentence the two above may not
     * borrow.
     */
    @Test
    void anOfferNothingWasShortOfStillSaysEveryValueWasRefused() {
        String model = """
                module example.offer

                data Amount = Int
                    invariant range = value >= 0 && value <= 3

                data R = { a1: Amount, a2: Amount }
                    invariant rule = a1.value * a2.value >= 100

                data Ok

                behavior f : (r: R, flag: Bool) -> Ok

                let f (r, flag) = Ok
                """;
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String block = GeneratedRows.of(compilation, "example.offer", "f",
                SourceRendering.namedByIdentity(compilation.texts())).text();

        assertTrue(block.contains("every value tried was refused at construction"), block);
        assertFalse(block.contains("was not all there was to try"), block);
        assertFalse(block.contains("could not read"), block);
    }

    /**
     * A figure being reached does not answer for a rule that composed nothing, so a search short
     * both ways says both.
     *
     * <p>The two are reached by different mechanisms and an author acts on each: one raises a
     * figure, the other reads a rule. Reported as the figure alone, they raise it and meet a block
     * whose values still come from the rules beside the one that gave none.
     */
    @Test
    void aFigureReachedBesideARuleThatComposedNothingSaysBoth() {
        String model = """
                module example.offer

                data Code = String
                    invariant shape = %s

                data Long = String
                    invariant huge = String.length(value) >= 5000

                data Flag = Yes | No

                data T = { flag: Flag, code: Code, long: Long }

                data Ok

                behavior look : (t: T) -> Ok

                let look (t) = Ok
                """.formatted(OUTSIDE_THE_SUBSET);
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String block = GeneratedRows.of(compilation, "example.offer", "look",
                SourceRendering.namedByIdentity(compilation.texts())).text();

        // The row whose search met the figure at one position and the unread rule at another. What
        // it may not do is name the figure and go quiet about the rule.
        assertTrue(block.contains("`t.code` holds a rule this compiler could not read"),
                () -> "a figure at `t.long` does not answer for the rule at `t.code`:\n" + block);
    }

    /**
     * And a point of a border is told the same thing, because it is the same block and the same
     * author reading it.
     */
    @Test
    void aPointOfABorderSaysItToo() {
        String model = """
                module example.offer

                data Amount = Int
                    invariant range = value >= 0 && value <= 100

                data Code = String
                    invariant shape = %s

                data P = { amount: Amount, code: Code }

                data Ok

                behavior place : (p: P) -> Ok

                let place (p) = Ok
                """.formatted(OUTSIDE_THE_SUBSET);
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String block = GeneratedRows.of(compilation, "example.offer", "place",
                SourceRendering.namedByIdentity(compilation.texts())).text();

        assertTrue(block.contains("no row for `p.amount = 0`"), block);
        assertTrue(block.contains("`p.code` holds a rule this compiler could not read"), block);
        assertFalse(block.contains("every value tried was refused at construction, which does not"
                + " make the combination impossible"), block);
    }

    /**
     * And a rule that composed nothing is not news where a row was written all the same: the rules
     * beside it composed a value, and the decoder — which reads every rule, including the one this
     * compiler did not — took it.
     */
    @Test
    void aRuleThatComposedNothingIsNotSaidWhereARowWasWritten() {
        String block = blockFor(OUTSIDE_THE_SUBSET + " && String.matches(\"aaaa\", value)");

        assertTrue(block.contains("Code(\"aaaa\")"),
                () -> "the readable rule still composes, and `aaaa` clears the unreadable one"
                        + " too:\n" + block);
        assertFalse(block.contains("could not read"), block);
    }
}
