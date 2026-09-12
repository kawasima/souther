package souther.compiler.check;

import souther.compiler.query.Scopes;
import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A clause the declaration check accepts is one this analysis can type.
 *
 * <p>The check reads clauses in a representation of their own and types them there, and a clause it
 * cannot type is answered with silence: the run-time check stands for it and nothing is reported.
 * That is the right answer for a clause outside the fragment and the wrong one for a clause this
 * compiler simply failed to type, and the two are indistinguishable from the outside — a suite stays
 * green either way, with one warning fewer. So the property is held here rather than left to be
 * noticed, one lost discharge at a time.
 *
 * <p>What is fixed is the property and not a count. A clause that names something the fragment does
 * not reach may still be answered with silence; what may not happen is a clause of a declaration this
 * compiler accepted failing to become typed Core.
 */
class EveryClauseADeclarationPassesTypesInTheDischargeRepresentationTest {

    private static final List<String> DECLARATIONS = List.of(
            """
            module demo
            data Amount = Decimal
                invariant value >= 0m
            data Positive = Int
                invariant value >= 1
            behavior f : (a: Amount) -> Amount
            let f (a) = a
            """,
            """
            module demo
            data Code = String
                invariant String.length(value) >= 2 && String.startsWith("x", value)
            behavior f : (c: Code) -> Code
            let f (c) = c
            """,
            """
            module demo
            data Line = { product: String, qty: Int }
            data Order = { lines: List<Line> }
                invariant List.length(lines) >= 1
                    && List.all(l -> l.qty >= 1, lines)
                    && List.allDistinctBy(.product, lines)
            behavior f : (o: Order) -> Int
            let f (o) = List.length(o.lines)
            """,
            """
            module demo
            data Range = { low: Int, high: Int }
                invariant low <= high
            data Window = { at: Range, label: String? }
                invariant String.length(Option.withDefault("x", label)) >= 1
            behavior f : (w: Window) -> Int
            let f (w) = w.at.high
            """,
            """
            module demo
            data Named = { name: String }
                invariant String.length(name) >= 1
            data Priced = { amount: Int }
                invariant amount >= 0
            data Item = { ...Named, ...Priced }
            behavior f : (i: Item) -> Int
            let f (i) = i.amount
            """,
            """
            module demo
            let remainder (n: Int) : Int = Int.floorMod(n, 2)
            data Even = Int
                invariant remainder(value) == 0
            behavior f : (e: Even) -> Int
            let f (e) = e.value
            """,
            """
            module demo
            data Tag = Small | Large
            data Batch = { tag: Tag, sizes: List<Int> }
                invariant List.all(s -> s >= 0, sizes)
            behavior f : (b: Batch) -> Int
            let f (b) = List.length(b.sizes)
            """);

    @Test
    void everyClauseOfAnAcceptedDeclarationBecomesTypedCore() {
        for (String source : DECLARATIONS) {
            // it compiles at all — a clause of a declaration this compiler refused says nothing
            assertFalse(Compiler.compile(source).isEmpty(), "this program is supposed to compile");
            Compilation compilation = Compilation.ofSource(source, "Main");
            compilation.answerEverything();

            String module = compilation.modules().get(0);
            Symbols symbols = Scopes.derived(compilation.db(), module).value();
            ExpandedClauseLookup declared = RuleReadings.declaredBy(compilation.db(), module);
            Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
            assertNotNull(symbols);
            assertNotNull(declared);
            assertNotNull(prepared);

            Clauses clauses = new Clauses(new RuleReadingSource(symbols, declared,
                    PublishedDeclarations.NONE, ScopedDeclarations.kindsOf(symbols),
                    ClauseLocations.NONE));
            int read = 0;
            for (Hir.Def def : prepared.defs().stream().map(each -> each.declaration().node()).toList()) {
                if (!(def instanceof Hir.Data data)) {
                    continue;
                }
                TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, data.name()));
                for (TypeOps.Declared each : clauses.declaredHere(named)) {
                    assertNotNull(clauses.typed(each.asExpanded(), named),
                            "`" + data.name() + "` declares a clause this check could not type:\n"
                                    + source);
                    read++;
                }
            }
            assertTrue(read > 0, "no clause was read at all, so nothing was held:\n" + source);
        }
    }
}
