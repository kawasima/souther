package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.AdmissibleSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a position admits, and whether this compiler could say so, are the same for the same rules.
 *
 * <p>Two patterns whose meet is a large machine, beside a rule naming one string. What the three of
 * them leave is that one string, and it is settled by asking that string of the other two: each
 * pattern is still made — a pattern is a machine and asking it anything means having it — and the
 * product of the two is not.
 *
 * <p>Which is only true of a reading that has all three before it builds anything. Read as it goes,
 * the two patterns meet first wherever the author happened to write them first — a machine of some
 * ninety thousand states — and a compiler that would not build it reports the position as one it
 * could not work out. The same rules, written in another order, build six hundred states and stop.
 *
 * <p>So this is the same model six ways: the three rules in every order they can be written in. The
 * answer has to be the same, and so does what the reading says about itself. What is asserted is
 * both, because either alone would pass for a compiler that got the values right and the account of
 * them wrong.
 *
 * <p>Held with an ordinary allowance rather than a small one. The point is not that a large machine
 * is refused — that is {@code WhatOneAnswerIsAllowedIsSpentOnce} — but that no product of two
 * patterns is built at all, whichever way round the rules arrive.
 */
class WhatARuleCostsDoesNotTurnOnWhereItIsWrittenTest {

    /**
     * Two patterns that share one string and nothing else short, and the string itself.
     *
     * <p>The two arms beside {@code x} repeat over runs of one length and of another that share no
     * factor, so a walk over both at once is in a different pair at every step until the lengths
     * come round together — which is past what one machine may be. {@code x} is what both of them
     * accept. A reading that meets the two patterns is refused, and one that reaches the written
     * value first has a question about one string.
     */
    private static final List<String> RULES = List.of(
            "    invariant one = String.matches(\"x|(a{251})*b\", value)",
            "    invariant two = String.matches(\"x|(a{223})*c\", value)",
            "    invariant three = value == \"x\"");

    /**
     * The same three, with the third stated as a pattern rather than as a written value.
     *
     * <p>Which takes away the one thing that made the first set easy. A written value is met by
     * asking it of the other sides, so a reading that puts the written values first is in the same
     * place whatever order they arrived in — and a reading that did nothing else would pass the
     * test above while every pair of patterns still met in the order somebody wrote them.
     */
    private static final List<String> ALL_PATTERNS = List.of(
            "    invariant one = String.matches(\"x|(a{251})*b\", value)",
            "    invariant two = String.matches(\"x|(a{223})*c\", value)",
            "    invariant three = String.matches(\"x\", value)");

    private static String model(List<String> rules, List<Integer> order) {
        StringBuilder out = new StringBuilder("module demo\n\ndata Code = String\n");
        order.forEach(each -> out.append(rules.get(each)).append('\n'));
        return out.toString();
    }

    private static AdmissibleSet admitted(List<String> rules, List<Integer> order) {
        Compilation compilation = Compilation.ofSource(model(rules, order), "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write: " + order);
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "Code"));
        return FieldDomains.of(name,
                RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES)
                .admits(RuleKey.THE_VALUE);
    }

    private static final List<List<Integer>> ORDERS = List.of(
            List.of(0, 1, 2), List.of(0, 2, 1), List.of(1, 0, 2),
            List.of(1, 2, 0), List.of(2, 0, 1), List.of(2, 1, 0));

    /** The same three rules in every order, and one answer between them. */
    @Test
    void everyOrderOfTheSameRulesLeavesTheSameAnswer() {
        AdmissibleSet first = sameInEveryOrder(RULES);

        assertEquals(AdmissibleSet.READ_IN_FULL, first.completeness(),
                "and every one of them is read in full, since no product of patterns is built");
    }

    /**
     * What {@code rules} admit, which every order of them comes to.
     *
     * <p>The first order is what the rest are held against, so it is read before the loop and the
     * loop is over the other five. Read inside the loop as well it would be read twice, and what
     * the two readings of it are held against each other for is that one order comes out the same
     * way twice — which is a claim about a compile repeating itself and not about where the rules
     * are written. That is what is given up here, for the two machines a reading of an order costs.
     */
    private static AdmissibleSet sameInEveryOrder(List<String> rules) {
        AdmissibleSet first = admitted(rules, ORDERS.getFirst());

        for (List<Integer> order : ORDERS.subList(1, ORDERS.size())) {
            assertEquals(first, admitted(rules, order),
                    "the values and the account of them, written as " + order);
        }
        return first;
    }

    /**
     * And the same where every rule is a pattern, which is where the order is the whole of it.
     *
     * <p>Nothing can be hoisted here: all three sides are machines, so whichever two meet first is
     * a machine somebody has to make, and the three ways of pairing them cost three different
     * things. What is asserted is that the reading picks one of them from the rules and not from
     * the writing — so the answer and the account of it are the same six times.
     *
     * <p>Not that it picks the cheapest. Which pairing is cheapest is a question about the machines
     * and cannot be asked without building them, which is the spending being arranged; what a model
     * is owed is that its answer does not turn on where its author put the rules.
     */
    @Test
    void theSameHoldsWhereEveryRuleIsAPattern() {
        AdmissibleSet first = sameInEveryOrder(ALL_PATTERNS);

        assertEquals(AdmissibleSet.READ_IN_FULL, first.completeness(),
                "and the small one is met first, so nothing large is built");
    }
}
