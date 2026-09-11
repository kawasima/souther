package souther.compiler.check;

import souther.compiler.WhereItSits;
import souther.compiler.ast.Hir;
import souther.compiler.diag.Region;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A clause is addressed by where the clause was written, which is not where its condition was.
 *
 * <p>Two regions are in reach at every site that reports one of these, and they are answers to
 * different questions. The expression's covers the condition and stops there, so the name the author
 * gave the clause is outside it — and that name is what the message says. The clause's covers the
 * clause, name and all. A report that named {@code even} and underlined {@code isEven(value)} would
 * leave the reader to work out that the two are one clause.
 *
 * <p>The second half is that the clause keeps its own answer through the rewriting the discharge
 * analysis does. The clauses that analysis reads have had the helpers they name expanded into them,
 * and every stage that rewrites one goes through {@link Hir.InvariantClause#with}. Nothing about a
 * diagnostic makes that visible: a clause whose address moved would still be reported, at a place
 * that reads as a rule.
 */
class AClauseIsAddressedWhereItWasWrittenTest {

    /**
     * A clause with a name, and a condition that is wholly a call to a helper written elsewhere.
     *
     * <p>Wholly, so the expansion replaces the clause's outermost expression rather than something
     * nested under an operator the clause wrote itself.
     */
    private static final String SOURCE = """
            module demo

            let isEven (n: Int) : Bool = Int.floorMod(n, 2) == 0

            data Even = Int
                invariant even = isEven(value)

            behavior f : (e: Even) -> Int
            let f (e) = e.value
            """;

    /** The text {@code region} underlines, cut out of {@code source}. */
    private static String underlined(String source, Region region) {
        String line = source.split("\n", -1)[WhereItSits.in(source, region).start().line() - 1];
        int from = WhereItSits.in(source, region).start().column() - 1;
        int to = WhereItSits.in(source, region).end().line() == WhereItSits.in(source, region).start().line()
                ? WhereItSits.in(source, region).end().column() - 1 : line.length();
        return line.substring(Math.min(from, line.length()), Math.min(to, line.length()));
    }

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();
        return compilation;
    }

    private static TypeSymbol even(Compilation compilation) {
        return TypeSymbols.declared(new TypeKey(compilation.modules().get(0), "Even"));
    }

    /** {@code Even}'s clauses as the discharge analysis reads them: helpers expanded. */
    private static List<Hir.InvariantClause> asExpanded(Compilation compilation) {
        String module = compilation.modules().get(0);
        TypeSymbol.AtModule even = TypeSymbols.declared(new TypeKey(module, "Even"));
        ExpandedClauseResult declared =
                RuleReadings.of(compilation, module).invariants().of(even.key());
        assertInstanceOf(ExpandedClauseResult.Found.class, declared);
        return ((ExpandedClauseResult.Found) declared).clauses().clauses().stream()
                .map(ExpandedClauses.Expanded::clause).toList();
    }

    /** {@code Even}'s clauses as the declaration writes them, with no helper expanded into them. */
    private static List<Hir.InvariantClause> asDeclared(Compilation compilation) {
        Prepared prepared = compilation.db()
                .ask(new Shapes.Prepared(compilation.modules().get(0))).value();
        assertNotNull(prepared);
        for (Derived.Def def : prepared.defs()) {
            if (def instanceof Derived.Data derived
                    && derived.declaration().node().declares().equals(even(compilation))) {
                return derived.declaration().node().invariants();
            }
        }
        throw new AssertionError("the module is supposed to declare `Even`");
    }

    @Test
    void aClauseIsUnderlinedOverTheClauseAndNotOverItsCondition() {
        Hir.InvariantClause clause = asExpanded(compiled()).get(0);

        assertEquals("invariant even = isEven(value)", underlined(SOURCE, clause.reportedAt()),
                "a report about the clause points at the clause, the name it was given included");
        assertEquals("isEven(value)", underlined(SOURCE, clause.expr().reportedAt()),
                "the expression answers where the condition was written, which leaves the name out");
    }

    /**
     * Each representation against the source, and not against the other one. Both of them reach a
     * reader through {@link Hir.InvariantClause#with}, so a rewrite that dropped what a clause is
     * addressed by would move them together and they would still agree.
     */
    @Test
    void everyRepresentationAddressesTheClauseAtTheSameText() {
        Compilation compilation = compiled();
        List<Hir.InvariantClause> declared = asDeclared(compilation);
        List<Hir.InvariantClause> expanded = asExpanded(compilation);
        assertEquals(1, declared.size(), "the declaration writes one clause");
        assertEquals(declared.size(), expanded.size(), "expanding a clause answers with one clause");

        assertEquals("invariant even = isEven(value)",
                underlined(SOURCE, declared.get(0).reportedAt()),
                "the clause as the declaration holds it");
        assertEquals("invariant even = isEven(value)",
                underlined(SOURCE, expanded.get(0).reportedAt()),
                "the expansion rewrote the condition, not where the clause was written");
    }
}
