package souther.compiler.check;

import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a rule leaves a position without is one fact about the rule, however many readings met it.
 *
 * <p>A declaration's clauses are read once per place the walk opens a value at, so a record naming
 * one type twice has that type's rules read twice. What each of those readings made of a part is
 * the reading's own; what the rule left unrepresented is not, and a reader handed it twice would be
 * told about one rule as though it were two — which is what a report prints and what a reader
 * counting the causes of a declaration counts.
 *
 * <p>The two fields are the whole of the fixture. One field is the same clause read once and says
 * nothing about which of the two the answer belongs to.
 */
class WhatARuleLeavesUnrepresentedIsSaidOncePerRuleTest {

    /** A rule the bounds hold nothing of, on a type a record names twice. */
    private static final String TWO_READINGS = """
            module example.rooms

            data Side = String
                invariant shape = String.matches("[a-z]+", value)

            data Length = { a: Side, b: Side }
            """;

    @Test
    void aRuleReadAtTwoPlacesLeavesItsPositionsWithoutARepresentationOnce() {
        List<ProjectionEvidence.Cause> causes = causesOf(TWO_READINGS);
        assertEquals(new ArrayList<>(new LinkedHashSet<>(causes)), causes,
                "one rule is named twice for one position, so a reader is told about one rule as"
                        + " though it were two: " + causes);
    }

    /**
     * And the reading did meet the rule twice.
     *
     * <p>Without this the test above is met by a reading that never opened the second field, which
     * is the other way for a list to hold no duplicate.
     */
    @Test
    void andBothPlacesWereRead() {
        List<String> paths = causesOf(TWO_READINGS).stream()
                .filter(each -> each instanceof ProjectionEvidence.Cause.Unrepresented)
                .map(each -> ((ProjectionEvidence.Cause.Unrepresented) each).path())
                .toList();
        assertFalse(paths.isEmpty(), "the rule left nothing unrepresented, so this fixture holds"
                + " the projection to nothing");
        assertEquals(List.of("a", "b"), paths.stream().distinct().sorted().toList(),
                "the rule was not read at both fields, so the duplicate the test above refuses"
                        + " could not arise: " + paths);
    }

    private static List<ProjectionEvidence.Cause> causesOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, "Length"));
        assertNotNull(symbols.declaredNode(named.key()), "no `Length` declared");
        return FieldDomains.of(named, RuleReadings.of(compilation, module),
                ReadAs.THE_COMPILATION_DOES).projection().causes();
    }
}
