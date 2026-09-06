package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A rule that draws a line draws it wherever the rule is written.
 *
 * <p>What a position admits and what a construction owes were the readings #1333 was about, and a
 * line is a third answer with a reader of its own: which authored rule placed which end
 * ({@link FieldDomains.Placed}) is what the declared bounds and the partition's lines are built
 * from. That reader classifies the clause it was handed, and a clause stating its rule through a
 * helper is that rule under a binding — so it reached no comparison and the declaration came back
 * with no line drawn on it, which is the difference the issue is written about.
 *
 * <p>Held on the lines themselves rather than on a verdict. A construction reads the rule through
 * one more reader, so the two spellings can come to one verdict while the lines under them differ.
 *
 * <p><b>The conjuncts stay the author's.</b> Which parts a clause has is what its author wrote
 * ({@link ClauseHelpers#conjunctsOf}, over the tree before any expansion), so a helper whose body
 * joins two rules is one conjunct and not two. What that one part states is still two rules, and
 * both of them draw their line: which parts there are and which rules one of them states are two
 * questions, and the answers below are named by the part either way.
 */
class ALineIsDrawnWhereverTheRuleThatDrawsItIsWrittenTest {

    private static List<FieldDomains.Placed> linesOf(String base, String helpers, String clause) {
        String source = """
                module demo

                %s

                data N = %s
                    invariant %s
                """.formatted(helpers, base, clause);
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES).placed();
    }

    @Test
    void anEndPlacedThroughAHelperIsTheEndTheRuleWrittenOutPlaces() {
        assertEquals(linesOf("Int", "", "value >= 1"),
                linesOf("Int", "let atLeast (n: Int, floor: Int) = n >= floor",
                        "atLeast(value, 1)"),
                "the same end, at the same coordinate, from the same conjunct");
    }

    /** The rule #1333 was written about, which draws its line on a length. */
    @Test
    void theEndOnALengthIsPlacedWhicheverWayTheRuleReachesIt() {
        assertEquals(linesOf("String", "", "String.length(value) >= 1"),
                linesOf("String", "let atLeastOne (s: String) = String.length(s) >= 1",
                        "atLeastOne(value)"),
                "a newtype whose rule is a length reaches the same line through a helper");
    }

    /** And a helper of a helper is bindings all the way down, which changes nothing. */
    @Test
    void aHelperOfAHelperPlacesTheSameEnd() {
        assertEquals(linesOf("Int", "", "value >= 1"),
                linesOf("Int", """
                        let notBelow (n: Int, floor: Int) = n >= floor
                        let atLeast (n: Int, floor: Int) = notBelow(n, floor)
                        """, "atLeast(value, 1)"),
                "crossing two bindings is crossing one twice");
    }

    /**
     * A helper whose body joins two rules is one authored part stating both, and both ends are
     * drawn.
     *
     * <p>Which parts a clause has is the author's answer and which rules a part states is the
     * language's, and they are not the same question: the author wrote one part here and the part
     * states two rules. Read as one rule, the part drew neither end; numbered as two parts, the
     * lines would be named as parts no reader beside this one has.
     *
     * <p>So both ends are drawn and both are named by the one part. What a report prints for them
     * is the same conjunct twice, which is what a part stating two rules is.
     */
    @Test
    void aHelperWhoseBodyJoinsTwoRulesStatesBothOfThem() {
        assertEquals(2, linesOf("Int", "", "value >= 1 && value <= 9").size(),
                "written out, the two conjuncts place an end each");
        assertEquals(2,
                linesOf("Int", "let inRange (n: Int) = n >= 1 && n <= 9", "inRange(value)").size(),
                "and one authored conjunct stating both rules places both ends");
        assertEquals(1,
                linesOf("Int", "let inRange (n: Int) = n >= 1 && n <= 9", "inRange(value)").stream()
                        .map(FieldDomains.Placed::part).distinct().count(),
                "which is one part with two ends and not two parts");
    }

    /** And the corpus above is about lines, so the written-out spellings have to draw some. */
    @Test
    void theSpellingsHeldAboveDrawLines() {
        assertFalse(linesOf("Int", "", "value >= 1").isEmpty(),
                "a comparison written out draws a line, or nothing above is being compared");
    }
}
