package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.AdmissibleSet;
import souther.compiler.values.UnreadReason;
import souther.compiler.values.ValueSet;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a choice admits and which rules were read are two answers, and one model gives opposite ones.
 *
 * <p>An alternative that admits every value at a position settles it, so a rule offered beside it
 * adds nothing a value could fail — and the position is one the reading speaks for however little
 * of that rule it could take in. The rule is still one nobody read, and the accounting says so.
 *
 * <p><b>The two are not in disagreement and are not to be made to agree.</b> Which values may stand
 * at a position is about the values, and a rule that could have narrowed them but did not is no
 * part of that answer. Which reading took a rule in is about the rules, and a rule nothing had a
 * word for was taken in by nothing whatever the values came to. A reading that carried the first
 * answer into the second would report a rule as read on the evidence that it did not matter.
 */
class ARuleNoAlternativeNeededIsStillOneNobodyReadTest {

    private static FieldDomains read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(each -> each.diagnostic().code())
                .toList(), "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    /** An equality and a denial of the same value between them admit every value there is, so the
     *  call stated as a third alternative excludes nothing. */
    private static final String LEFT_ASSOCIATED = """
            module demo

            data N = { n: Int }
                invariant said = (n == 5 || n /= 5) || Int.abs(n) >= 2
            """;

    /** The same three alternatives, bracketed the other way. */
    private static final String RIGHT_ASSOCIATED = """
            module demo

            data N = { n: Int }
                invariant said = n == 5 || (n /= 5 || Int.abs(n) >= 2)
            """;

    /** The position holds every value, and that is the whole of what the rules leave it. */
    @Test
    void thePositionIsSpokenForWhateverTheThirdAlternativeSays() {
        assertEquals(AdmissibleSet.complete(ValueSet.ANY), read(LEFT_ASSOCIATED).admits(RuleKey.of("n")));
    }

    /** However the alternatives are bracketed. {@code ||} is one connective and not a tree. */
    @Test
    void andHoweverTheAlternativesAreBracketed() {
        assertEquals(AdmissibleSet.complete(ValueSet.ANY), read(RIGHT_ASSOCIATED).admits(RuleKey.of("n")));
    }

    /** And the rule is still one nothing read, which is what the accounting is for. */
    @Test
    void andTheRuleNothingReadIsStillLeftStanding() {
        assertEquals(List.of(UnreadReason.FORM_NOT_READ), leftStanding(read(LEFT_ASSOCIATED)),
                "a rule no reading took in is left standing however little the values needed it");
        assertEquals(List.of(UnreadReason.FORM_NOT_READ), leftStanding(read(RIGHT_ASSOCIATED)),
                "and either way round");
    }

    /**
     * What stopped each question of each rule of this value that nothing answered.
     *
     * <p>The reading of values, which is the one these questions are about. A reason is said in the
     * vocabulary of whichever reading would have answered, and a line about an end is not written in
     * the words of a set of values — so this asks for the arm it is about rather than for whatever
     * came back.
     */
    private static List<UnreadReason> leftStanding(FieldDomains read) {
        return read.accounting().values().stream()
                .flatMap(each -> each.answers().values().stream())
                .filter(RuleAccounting.Outcome.Unaccounted.class::isInstance)
                .map(each -> ((RuleAccounting.Outcome.Unaccounted) each).why())
                .filter(RuleAccounting.Why.TheValueReadingSays.class::isInstance)
                // Every reason each question was left with, and not one apiece: a question stands
                // with as many reasons as there are parts of the clause nothing took in, and a
                // helper taking the first would pass whichever of them the reading met first.
                .flatMap(each -> ((RuleAccounting.Why.TheValueReadingSays) each).why().stream())
                .toList();
    }

    /** Read off the accounting and not off the completeness beside it, so that the two answers stay
     *  two. */
    @Test
    void theAccountingIsNotReadOffTheCompleteness() {
        FieldDomains read = read(LEFT_ASSOCIATED);
        Map<RuleRef.Invariant, RuleAccounting> accounting = read.accounting();

        assertEquals(1, accounting.size(), "one clause, so one rule to account for");
        assertEquals(AdmissibleSet.READ_IN_FULL, read.admits(RuleKey.of("n")).completeness());
        assertTrue(accounting.values().stream().anyMatch(each -> !each.unaccounted().isEmpty()),
                "the values being spoken for did not answer the rule's question");
    }
}
