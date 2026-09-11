package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.Primary;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for the {@code ...} spread (flattened fields + inherited invariants) and value spread
 *  {@code ...src} (spec §field-spread, §record-literal). */
class CompileIncludeTest {

    /**
     * A collision names both groups that supply the field.
     *
     * <p>The taking data declares no `issuedAt` of its own — both came through spreads — so naming
     * it as the other side of the collision sent a reader to a declaration where nothing is written.
     * What they need is the group that supplied the earlier one, and the caret on the spread that
     * brought the second.
     */
    @org.junit.jupiter.api.Test
    void aCollisionBetweenTwoSpreadsNamesBothGroups() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                data Issued = { issuedAt: Int }
                data Sold = { issuedAt: Int }
                data Ticket =
                    { ...Issued
                    , ...Sold
                    }
                """));
        assertEquals("E1004", e.code());
        assertTrue(e.getMessage().contains("`...Sold`"), e.getMessage());
        assertTrue(e.getMessage().contains("`...Issued`"), e.getMessage());
        assertFalse(e.getMessage().contains("Ticket"),
                "the taking data declares no such field: " + e.getMessage());
    }

    // the ... spread flattens Common's fields into each state; a behavior transitions Draft -> Submitted by spreading it.
    private static final String FLOW = """
            module demo

            data Common = {
                applicant: String
                , cost: Int
            }

            data Draft = {
                ...Common
            }

            data Submitted = {
                ...Common
                , submittedAt: String
            }

            behavior submit : (d: Draft, at: String) -> Submitted constructs Submitted

            let submit (d, at) = Submitted { ...d, submittedAt = at }
            """;

    @Test
    void spreadCarriesIncludedFieldsThroughATransition() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(FLOW), getClass().getClassLoader());

        Result<?> r = Codecs.decode(loader, "demo.Draft", Map.of("applicant", "bob", "cost", 100L));
        assertTrue(r instanceof Ok);
        Object draft = ((Ok<?>) r).value();

        Object submit = Emitted.behavior(loader, "demo", "submit").getConstructor().newInstance();
        Object submitted = submit.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(submit, draft, "2026");

        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Submitted", submitted);
        assertEquals("bob", out.get("applicant"));  // from ...d (included field)
        assertEquals(100L, out.get("cost"));         // from ...d (included field)
        assertEquals("2026", out.get("submittedAt"));
    }

    @Test
    void includedInvariantRunsOnConstruction() throws Exception {
        String src = """
                module demo
                data Common = { applicant: String, cost: Int } invariant cost >= 0
                data Draft = { ...Common }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        Result<?> good = Codecs.decode(loader, "demo.Draft", Map.of("applicant", "bob", "cost", 5L));
        assertTrue(good instanceof Ok);

        Result<?> bad = Codecs.decode(loader, "demo.Draft", Map.of("applicant", "bob", "cost", -5L));
        assertTrue(bad instanceof Err, "Common's invariant is inherited by Draft");
        assertEquals("invariant_violation",
                ((Err<?>) bad).issues().asList().get(0).code());
    }

    // A spread naming nothing in scope used to reach `Symbols.get(null)` and throw a
    // NullPointerException instead of being reported (issue #154).
    @Test
    void aSpreadOfATypeNotInScopeIsReportedAtTheName() {
        String source = """
                module demo exposing ( Invoice )
                data Invoice = { ...Common, total: Int }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(source));

        assertTrue(e.getMessage().contains("cannot find a type named `Common`"), e.getMessage());
        assertEquals(2, WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().line());
        assertEquals(21, WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().column(), "the caret is on the spread's name");
        assertEquals(27, WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).end().column());
    }

    // The same miss written as a field's type reports the same thing at the same kind of position —
    // the two paths differing is what issue #154 was about.
    @Test
    void aFieldOfATypeNotInScopeIsReportedTheSameWay() {
        String source = """
                module demo exposing ( Invoice )
                data Invoice = { c: Common, total: Int }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(source));

        assertTrue(e.getMessage().contains("cannot find a type named `Common`"), e.getMessage());
        assertEquals(21, WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().column());
    }

    @Test
    void aSpreadOfANameInScopeThatIsNotAProductStillSaysSo() {
        String source = """
                module demo exposing ( Invoice )
                data Common = A | B
                data Invoice = { ...Common, total: Int }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(source));

        assertTrue(e.getMessage().contains("not a product data"), e.getMessage());
        assertEquals(3, WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().line());
        assertEquals(21, WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().column());
    }

    // Every reader of a spread goes through the same resolution, so none of them may reach a null
    // name: a module whose fields and invariant are read before the derive step must report too.
    @Test
    void aSpreadOfATypeNotInScopeIsReportedWithAFieldReadAndAnInvariant() {
        for (String src : new String[] {
                """
                module demo exposing ( Invoice, total )
                data Invoice = { ...Common, total: Int }
                behavior total : (i: Invoice) -> Int
                let total (i) = i.total
                """,
                """
                module demo exposing ( Invoice )
                data Invoice = { ...Common, total: Int } invariant total > 0
                """}) {
            CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
            assertTrue(e.getMessage().contains("cannot find a type named `Common`"), e.getMessage());
        }
    }

    @Test
    void aMisspeltSpreadSuggestsTheNameItMeant() {
        String src = """
                module demo exposing ( Invoice )
                data Common = { applicant: String }
                data Invoice = { ...Commin, total: Int }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        String out = new HumanRenderer(false)
                .render(e.diagnostic(), new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);

        assertTrue(out.contains("`Commin`"), out);
        assertTrue(out.contains("Common"), out);   // the name it meant
        assertTrue(out.contains("data Invoice = { ...Commin, total: Int }"), out);
    }
}
