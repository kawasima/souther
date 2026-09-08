package souther.compiler.sites;

import org.junit.jupiter.api.Test;
import souther.compiler.ast.Hir;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.query.Sites;
import souther.compiler.source.SourceId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An editor asks what a semantic fact is about and can only point with a position, so something has
 * to say which occurrence a stretch of source is. The characters do, on the authored tree, and this
 * is what makes that a checked property of the revision rather than a hope.
 *
 * <p>What is not covered here: a position a copy wears. A resolved module holds no copy of anything —
 * expansion runs below it — so the case has nothing to be built out of at this level, and what stands
 * for it is that the walk takes no site from an expansion and none from a copied position.
 */
class AnOccurrenceIsNamedByWhatItWasWrittenOverTest {

    private static final String SOURCE = """
            module m

            data D = { v: Int }

            behavior f : (d: D) -> Int
            let f (d) = d.v
            """;

    @Test
    void anExpressionIsFoundByTheCharactersItWasWrittenOver() {
        Hir.Module module = resolved(SOURCE);
        AuthoredSites sites = identified(module);

        Hir.Expr body = bodyOf(module, "f");
        assertNotNull(sites.site(body.region()),
                "the body `d.v` is written in the source, so it is an occurrence");
        assertNotNull(sites.site(((Hir.FieldAccess) body).target().region()),
                "and so is the `d` it is taken off");
    }

    @Test
    void aStretchNothingWasWrittenOverIsNoOccurrence() {
        AuthoredSites sites = identified(resolved(SOURCE));
        SourcePos nowhere = new SourcePos(400, 1, new SourceId("m.sou"));
        assertNull(sites.site(new Region(nowhere, nowhere.along(3))),
                "nothing is written on line 400");
        assertNull(sites.site(null), "and a caller with no extent is asking about nothing");
    }

    @Test
    void twoExpressionsOverOneStretchLeaveNeitherNameable() {
        Hir.Module module = resolved(SOURCE);
        Hir.FnDef f = fn(module, "f");
        // The same definition twice: two occurrences of every expression in it, each pair written
        // over one stretch of source, which is exactly what an extent may not be an identity for.
        Hir.Module doubled = module.withFns(List.of(f, f));

        assertInstanceOf(AuthoredSites.Census.TwoOccurrencesOneExtent.class,
                AuthoredSites.of(doubled));
    }

    @Test
    void aRegionThatBeginsInOneSourceAndEndsInAnotherIsRefused() {
        Hir.Module module = resolved(SOURCE);
        Hir.FnDef f = fn(module, "f");
        SourcePos opens = new SourcePos(6, 13, new SourceId("m.sou"));
        SourcePos closes = new SourcePos(6, 16, new SourceId("elsewhere.sou"));
        Hir.Expr straddling = new Hir.StringLit("x", opens, new Region(opens, closes));
        Hir.Module bent = module.withFns(List.of(new Hir.FnDef(f.written(), f.declaredIn(),
                f.params(), f.declaredReturn(), new Hir.FnBody.Written(straddling), f.modifiers(),
                f.role(), f.pos())));

        assertInstanceOf(AuthoredSites.Census.OneRegionTwoSources.class, AuthoredSites.of(bent));
    }

    @Test
    void aHelperCalledTwiceIsStillOneOccurrenceOfEachOfItsExpressions() {
        // Two calls of one helper. Below this the check will hold two elaborations of the helper's
        // body — one per call, each settling what the other does not — and the source it was written
        // in still wrote it once.
        Hir.Module module = resolved("""
                module m

                let double (n: Int) : Int = n + n

                behavior f : (a: Int, b: Int) -> Int
                let f (a, b) = double(a) + double(b)
                """);

        assertInstanceOf(AuthoredSites.Census.Identified.class, AuthoredSites.of(module));
    }

    /**
     * And a row that answers for anything is a row of the table.
     *
     * <p>What it answers with is written in the source like any other row's, so it is an occurrence
     * like any other row's. What it answers <em>for</em> is not written at all — a walk asks the row
     * which of the two it is, and never asks a row that names no arguments for its arguments.
     */
    @Test
    void aRowThatAnswersForAnythingIsWalkedLikeTheRowsBesideIt() {
        Hir.Module module = resolved("""
                module m

                behavior dep : (x: Int) -> Int

                behavior use : (x: Int) -> Int
                    depends on dep
                let use (x, dep) = dep(x)

                fake dep
                    | (1) -> 2
                    | _   -> 0
                """);
        AuthoredSites sites = identified(module);

        List<Hir.FakeRow> rows = module.fakes().getFirst().rows();
        Hir.Matched.Arguments named =
                assertInstanceOf(Hir.Matched.Arguments.class, rows.getFirst().matched());
        assertNotNull(sites.site(named.inputs().getFirst().region()), "the `1` is written");
        assertNotNull(sites.site(rows.getFirst().output().region()), "and so is the `2`");
        assertInstanceOf(Hir.Matched.Anything.class, rows.get(1).matched());
        assertNotNull(sites.site(rows.get(1).output().region()), "and so is the `0` under the `_`");
    }

    @Test
    void theSameSourceCensusesToTheSameAnswer() {
        // What stops work in the query graph: an answer equal to the one it replaces is an edit
        // nothing downstream has to see.
        assertEquals(identified(resolved(SOURCE)), identified(resolved(SOURCE)));
        assertFalse(identified(resolved(SOURCE)).equals(identified(resolved("""
                module m

                data D = { v: Int }

                behavior f : (d: D) -> Int
                let f (d) = d.v + 1
                """))), "an added expression is an added occurrence");
    }

    @Test
    void theQueryAnswersWhatTheWalkFound() {
        Compilation compilation = compile(SOURCE);
        AuthoredSites answered = compilation.db().ask(new Sites.Authored("m")).value();

        assertNotNull(answered, "a module that resolves has its occurrences");
        assertTrue(answered.count() > 0, "and `d.v` is at least two of them");
        assertEquals(identified(compilation.db().ask(new Names.Resolved("m")).value()), answered,
                "asked through the graph or walked directly, it is one answer");
    }

    private static AuthoredSites identified(Hir.Module module) {
        return assertInstanceOf(AuthoredSites.Census.Identified.class, AuthoredSites.of(module))
                .sites();
    }

    private static Hir.Expr bodyOf(Hir.Module module, String name) {
        return assertInstanceOf(Hir.FnBody.Written.class, fn(module, name).body()).expr();
    }

    private static Hir.FnDef fn(Hir.Module module, String name) {
        for (Hir.FnDef def : module.fns()) {
            if (def.written().canonical().equals(name)) {
                return def;
            }
        }
        throw new AssertionError("the module declares no `let " + name + "`");
    }

    private static Hir.Module resolved(String source) {
        return compile(source).db().ask(new Names.Resolved("m")).value();
    }

    private static Compilation compile(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", source);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
    }
}
