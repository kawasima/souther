package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * One rule written out, the same rule reached through a helper, and the same rule written as the
 * denial of its opposite are one statement of one conjunct, and the reading arrives at the same
 * comparison from each.
 *
 * <p>A binding is where the environment a reading carries changes and states nothing of its own, and
 * a denial is carried to the leaves and spent there — so what is left at a leaf is the rule, not the
 * shape its author happened to type. What a reader asks for is which statement of the conjunct it
 * is, and that is the same number in all three.
 *
 * <p>Which tree the comparison sits in is no part of this. The helper's bindings stand between the
 * conjunct and the comparison and the denied spelling is an application where the written-out one is
 * a binary, so a position is three answers where a statement is one.
 */
class OneRuleIsOneStatementHoweverItWasSpeltTest {

    private static FieldDomains read(String helpers, String base, String clause) {
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
                ReadAs.THE_COMPILATION_DOES);
    }

    /** Each end the reading placed on the value, as which statement placed it and where it sits. */
    private static List<String> statements(FieldDomains of) {
        return of.placed().stream().map(each -> {
            assertNotNull(each.from().placedBy(),
                    "a comparison placed this end, so the reading names the statement it read");
            return each.from().placedBy() + " " + each.lower() + " " + each.end();
        }).toList();
    }

    // --- a bound on a numeric newtype's own value ---

    private static final String OWN_VALUE_HELPER = "let atLeastThree (n: Int) = n >= 3";

    @Test
    void anOwnValueBoundIsTheFirstStatementOfTheFirstConjunctWrittenOut() {
        assertEquals(List.of("Invariant[clause=N#0]#0/0 true Endpoint[at=3, inclusive=true]"),
                statements(read("", "Int", "value >= 3")));
    }

    @Test
    void anOwnValueBoundReachedThroughAHelperIsTheSameStatement() {
        assertEquals(statements(read("", "Int", "value >= 3")),
                statements(read(OWN_VALUE_HELPER, "Int", "atLeastThree(value)")));
    }

    @Test
    void anOwnValueBoundWrittenAsADenialIsTheSameStatement() {
        assertEquals(statements(read("", "Int", "value >= 3")),
                statements(read("", "Int", "Bool.not(value < 3)")));
    }

    // --- a floor on a string's length ---

    private static final String LENGTH_HELPER = "let atLeastOne (s: String) = String.length(s) >= 1";

    @Test
    void aLengthFloorIsTheFirstStatementOfTheFirstConjunctWrittenOut() {
        assertEquals(List.of("Invariant[clause=N#0]#0/0 true Endpoint[at=1, inclusive=true]"),
                statements(read("", "String", "String.length(value) >= 1")));
    }

    @Test
    void aLengthFloorReachedThroughAHelperIsTheSameStatement() {
        assertEquals(statements(read("", "String", "String.length(value) >= 1")),
                statements(read(LENGTH_HELPER, "String", "atLeastOne(value)")));
    }

    @Test
    void aLengthFloorWrittenAsADenialIsTheSameStatement() {
        assertEquals(statements(read("", "String", "String.length(value) >= 1")),
                statements(read("", "String", "Bool.not(String.length(value) < 1)")));
    }

    /**
     * The control: a conjunct stating two comparisons numbers them apart.
     *
     * <p>What says the ordinal above is an answer rather than the only number the reading ever
     * writes down. A denied choice is one conjunct and states one comparison per leaf, so the two
     * ends here are the two statements of the conjunct the author wrote — and a reading that named
     * the conjunct alone would say the same of both.
     */
    @Test
    void aConjunctStatingTwoComparisonsNumbersThemApart() {
        assertEquals(List.of("Invariant[clause=N#0]#0/0 true Endpoint[at=1, inclusive=true]",
                        "Invariant[clause=N#0]#0/1 false Endpoint[at=10, inclusive=true]"),
                statements(read("", "Int", "Bool.not(value < 1 || value > 10)")));
    }
}
