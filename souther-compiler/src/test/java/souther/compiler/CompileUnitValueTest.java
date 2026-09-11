package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.Primary;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A unit data is constructed by writing its name bare — the functional-language idiom for a
 * nullary constructor (spec §unit-data). It is in no behavior's construction set, so no
 * {@code constructs} entry is written for it (spec §constructs-excludes-unit-data).
 */
class CompileUnitValueTest {

    private static final String MODULE = """
            module demo

            data Mark
            data Flag = Bool

            behavior marks : (f: Flag) -> List<Mark>

            let marks (f) = [Mark | f.value]
            """;

    private List<?> marks(boolean on) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object flag = Codecs.decoded(loader, "demo.Flag", on);   // Flag is a single-Bool newtype: bare bool
        return (List<?>) Codecs.apply(Emitted.behavior(loader, "demo", "marks")
                .getConstructor().newInstance(), flag);
    }

    @Test
    void bareUnitNameConstructsTheUnit() throws Exception {
        List<?> present = marks(true);
        assertEquals(1, present.size());
        assertEquals("demo.Mark", present.get(0).getClass().getName());

        assertEquals(0, marks(false).size());
    }

    /**
     * Constructing a unit needs no entry, and writing one is refused.
     *
     * <p>Both answers, because a check that had stopped reading the clause at all would pass the
     * first on its own. What the second says is that the entry is meaningless rather than optional:
     * were it accepted, one body would have two correct clauses and the exact match E1002 and E1006
     * keep would say nothing (spec §constructs-excludes-unit-data).
     */
    @Test
    void constructingAUnitNeedsNoConstructsEntry() {
        String src = """
                module demo
                data Mark
                data Note
                data Flag = Bool
                behavior marks : (f: Flag) -> Mark | Note
                let marks (f) = if f.value then Mark else Note
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(src.replace("-> Mark | Note",
                        "-> Mark | Note\n                    constructs Mark")));
        assertEquals("E1026", e.code());
    }

    /**
     * Every unit entry the module writes, and not the first of them.
     *
     * <p>Two clauses here and two entries in one of them. A wrong clause is one thing to rewrite, so
     * an author holding three of these should not learn the second by building again — which is what
     * E1002 and E1006 already do within one clause, and this is that one frame out.
     */
    @Test
    void everyUnitEntryTheModuleWritesIsReported() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data Mark
                data Note
                data Out = { n: Int }
                behavior one : (n: Int) -> Out | Mark | Note
                    constructs Out, Mark, Note
                let one (n) = if n > 0 then Out { n = n } else Mark
                behavior two : (n: Int) -> Out | Mark
                    constructs Out, Mark
                let two (n) = if n > 0 then Out { n = n } else Mark
                """));

        List<String> said = e.diagnostics().stream()
                .map(souther.compiler.diag.DiagnosticRenderer::legacyBody).toList();
        assertEquals(3, said.size(), "two from the first clause and one from the second: " + said);
        e.diagnostics().forEach(d -> assertEquals("E1026", d.code(), said.toString()));
        assertTrue(said.get(0).contains("Mark") && said.get(1).contains("Note"), said.toString());
        assertTrue(said.get(2).contains("Mark"), said.toString());
    }

    /**
     * Asking one body of such a module does not turn the entry into an over-declaration.
     *
     * <p>The per-behavior query asks about a body and not about a module, so the clause check has
     * not run when the body's own reader meets the entry. What that reader would otherwise say is
     * E1006 — "declares `constructs Mark` but never builds Mark" — and the body does build the unit,
     * so the sentence would be false and the hint would send an author to the wrong edit.
     *
     * <p>This is the reachable case that the skip in the E1006 loop is for. Through
     * {@code Compiler.compile} the module check raises E1026 first and nothing gets that far, which
     * is why the skip has to be measured from here.
     */
    @Test
    void askingOneBodyOfSuchAModuleReportsNoOverDeclaration() {
        Compilation compilation = Compilation.ofSource("""
                module demo
                data Mark
                data Out = { n: Int }
                behavior f : (n: Int) -> Out | Mark
                    constructs Out, Mark
                let f (n) = if n > 0 then Out { n = n } else Mark
                """, "Main");

        assertNotNull(compilation.db().ask(new Bodies.CheckedBehavior("demo", "f")).value(),
                "the body is read, whatever the clause around it says");
        assertTrue(compilation.reports().stream()
                        .map(found -> DiagnosticRenderer.legacyBody(found.report().diagnostic()))
                        .noneMatch(said -> said.contains("never builds")),
                "and the entry is not reported as one the body never built");
    }

    /** A unit has no fields, so an invariant on it has nothing to observe and nothing it could
     *  reject (spec §unit-data). It has to be refused where it is written: `Ast.UnitData` has no
     *  slot for one, so anything not caught here is dropped without a word. */
    @Test
    void aUnitDataCannotCarryAnInvariant() {
        String src = """
                module demo
                data Mark
                    invariant false
                data Out = { s: String }
                behavior k : (n: Int) -> Out | Mark constructs Out, Mark
                let k (n) = if n > 0 then Out { s = "x" } else Mark
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("invariant"), e.getMessage());
        assertEquals(3, WhereItSits.in(src, e.pos()).line(), "must point at the clause, not the data");
    }

    /** The clause is refused before it is elaborated, so this would otherwise be accepted with an
     *  unbound name inside it — which is how the silent drop showed up. */
    @Test
    void aUnitDataInvariantIsRefusedEvenWhenItsExpressionIsNonsense() {
        String src = """
                module demo
                data Mark
                    invariant nonexistent > 0
                data Out = { s: String }
                behavior k : (n: Int) -> Out constructs Out
                let k (n) = Out { s = "x" }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    /** An empty body names a type with one value, which is what a unit data is, so it is the second
     *  way to write one — and the two reject each other's construction. Only the unit form remains. */
    @Test
    void anEmptyProductBodyIsRefused() {
        String src = """
                module demo
                data Mark = {}
                data Out = { s: String }
                behavior k : (n: Int) -> Out constructs Out
                let k (n) = Out { s = "x" }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("empty body"), e.getMessage());
        assertEquals(2, WhereItSits.in(src, e.pos()).line(), "must point at the body");
        assertEquals(13, WhereItSits.in(src, e.pos()).column(), "must start at the opening brace");
        String out = new HumanRenderer(false)
                .render(e.diagnostic(), new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
        assertTrue(out.contains("^^"), out);   // the whole `{}`, not just the opening brace
    }

    /** A body written open is still underlined from its opening brace: the region spans to the
     *  closing brace wherever it sits, and the renderer draws one caret across lines. */
    @Test
    void anEmptyBodySpanningLinesIsUnderlinedFromItsBrace() {
        String src = """
                module demo
                data Mark = {
                }
                data Out = { s: String }
                behavior k : (n: Int) -> Out constructs Out
                let k (n) = Out { s = "x" }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("empty body"), e.getMessage());
        assertEquals(2, WhereItSits.in(src, e.pos()).line(), "must point at the opening brace");
        assertEquals(1, WhereItSits.in(src,
                ((Primary.InSource) e.diagnostic().primary()).place().region()).sourceSpan(), "a multi-line region draws one caret");
    }

    /** A spread body is a body: what it includes decides the fields, and an empty one cannot be
     *  written any more, so this stays a data with fields. */
    @Test
    void aSpreadOnlyBodyIsStillAProduct() {
        String src = """
                module demo
                data Base = { s: String }
                data Mark = { ...Base }
                behavior k : (n: Int) -> Mark constructs Mark
                let k (n) = Mark { s = "x" }
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }
}
