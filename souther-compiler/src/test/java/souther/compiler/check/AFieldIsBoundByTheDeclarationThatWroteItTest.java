package souther.compiler.check;

import souther.compiler.query.Scopes;
import souther.compiler.Compiler;
import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Severity;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A declaration's fields are bound where the declaration is written, and every reader gets those
 * bindings.
 *
 * <p>A clause reads its declaration's fields as the bindings they are, and it is resolved once, by
 * the module that wrote it. A reader that computes those bindings under its own module name gets a
 * different binding for the same field, so the clause it was carried in with resolves against
 * nothing — and {@code Clauses.typed} answers null, which every caller reads as a rule the language
 * cannot express rather than one that was looked up in the wrong place.
 *
 * <p>Held over each way a reader can reach a declaration another module wrote. They break together
 * today and need not stay that way: how a name arrives is the import representation's business, and
 * a binding is not a function of it.
 *
 * <p>Held at three depths, because one alone would pass while the defect stood. The identity is
 * where it goes wrong; the elaboration is what stops working; the diagnostic is what an author sees.
 */
class AFieldIsBoundByTheDeclarationThatWroteItTest {

    private static final TypeSymbol.AtModule RANGE = TypeSymbols.declared(new TypeKey("up", "Range"));

    private static final String DECLARING = """
            module up exposing ( Range )
            data Range = { lo: Int, hi: Int }
                invariant ordered = lo <= hi
            """;

    /** What the reader writes to reach {@code up.Range}, and what it calls it once it has. */
    private enum Reached {
        BY_NAME("import up ( Range )", "Range"),
        QUALIFIED("", "up.Range"),
        THROUGH_AN_ALIAS("import up as u", "u.Range"),
        BESIDE_THE_READERS_OWN("data Range = { lo: Int, hi: Int }", "up.Range");

        private final String preamble;
        private final String written;

        Reached(String preamble, String written) {
            this.preamble = preamble;
            this.written = written;
        }

        /** A reader whose own obligation is discharged only by what {@code up.Range} states. */
        String reader() {
            return "module demo\n" + preamble + """

                    data Gap = Int
                        invariant value >= 0

                    behavior measure : (r: %s) -> Gap constructs Gap
                    let measure (r) = Gap(r.hi - r.lo)
                    """.formatted(written);
        }
    }

    private static Compilation compiled(Reached reached) {
        Compilation c = Compilation.ofSources(List.of(DECLARING, reached.reader()),
                ModulePath.EMPTY);
        c.answerEverything();
        return c;
    }

    private static Hir.Data range(Compilation c) {
        Hir.Def def = c.db().ask(new Names.ResolvedDeclaration(RANGE.key())).value();
        assertNotNull(def, "the model under test compiles");
        return (Hir.Data) def;
    }

    /** The clauses as the module named {@code reading} reads them. */
    private static Clauses readBy(Compilation c, String reading) {
        return new Clauses(Scopes.resolved(c.db(), reading).value(),
                RuleReadings.declaredBy(c.db(), reading), ClauseLocations.NONE,
                DeclarationReadings.NONE);
    }

    /**
     * The identity, where it is decided. A field of {@code up.Range} is {@code up.Range}'s field
     * however it was reached, and the reader's own module name never stands in for the declaring one.
     */
    @Test
    void aReaderBindsAnImportedDeclarationsFieldsWhereTheyWereWritten() {
        BindingOwner declaring = new BindingOwner.OfFields(RANGE);

        for (Reached reached : Reached.values()) {
            Compilation c = compiled(reached);

            assertEquals(Map.of("lo", new BindingId(declaring, 0), "hi", new BindingId(declaring, 1)),
                    readBy(c, "demo").bindingsOf(RANGE),
                    "reached " + reached + ": a field is bound by the declaration that wrote it");
        }
    }

    /**
     * What stops working when it goes wrong: the clause no longer resolves, and E1023 says the field
     * is unknown though the scope has it under another identity.
     *
     * <p>Asserted as the same {@code Core} the declaring module reads, not merely as something: a
     * clause that typed to a different term would be a second reading of one rule. Asserted non-null
     * beside it, because two readings that both answer nothing are also equal.
     */
    @Test
    void anImportedClauseIsTypedAsItIsWhereItIsDeclared() {
        for (Reached reached : Reached.values()) {
            Compilation c = compiled(reached);
            Hir.Data range = range(c);
            Hir.Expr clause = range.invariants().get(0).expr();

            ClauseAsExpanded read = new ClauseAsExpanded(clause, CallsLeftStanding.NONE);
            Core athome = readBy(c, "up").typed(read, RANGE).orNull();
            assertNotNull(athome, "the declaring module reads its own rule");
            assertEquals(athome, readBy(c, "demo").typed(read, RANGE).orNull(),
                    "reached " + reached + ": one rule, read the same either side of the boundary");
        }
    }

    /**
     * What an author sees. The obligation is the reader's own ({@code Gap.value >= 0}) and what
     * discharges it is the imported declaration's ({@code lo <= hi}), so the two do not go missing
     * together — which is how this stayed invisible: a check that loses both reports nothing either
     * way.
     */
    @Test
    void anImportedRuleDischargesWhatItWouldDischargeAtHome() {
        for (Reached reached : Reached.values()) {
            assertEquals(List.of(), warnings(Compiler.compileModulesWithWarnings(
                            List.of(DECLARING, reached.reader()))),
                    "reached " + reached + ": `lo <= hi` discharges `hi - lo >= 0`");
        }
    }

    /** The same model in one module, which is what the four above are held against. */
    @Test
    void theSameModelInOneModuleDischargesToo() {
        assertEquals(List.of(), warnings(Compiler.compileModulesWithWarnings(List.of("""
                module demo
                data Range = { lo: Int, hi: Int }
                    invariant ordered = lo <= hi

                data Gap = Int
                    invariant value >= 0

                behavior measure : (r: Range) -> Gap constructs Gap
                let measure (r) = Gap(r.hi - r.lo)
                """))));
    }

    /**
     * Two declarations of one spelling are two declarations. A reader that writes its own
     * {@code Range} and reaches {@code up.Range} beside it must not have them share a binding: a
     * field standing for two values is a clause read against the wrong one, which is worse than a
     * clause not read at all.
     */
    @Test
    void aReadersOwnDeclarationDoesNotShareABindingWithAnImportedNamesake() {
        Compilation c = compiled(Reached.BESIDE_THE_READERS_OWN);
        Clauses read = readBy(c, "demo");

        TypeSymbol.AtModule ours = TypeSymbols.declared(new TypeKey("demo", "Range"));
        assertTrue(Collections.disjoint(read.bindingsOf(RANGE).values(),
                        read.bindingsOf(ours).values()),
                "two declarations of `Range` bind two sets of fields");
    }

    private static List<String> warnings(Compiler.Compiled c) {
        return c.warnings().stream().filter(d -> d.severity() == Severity.WARNING)
                .map(Diagnostic::code).sorted().toList();
    }
}
