package souther.compiler;

import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ReachName;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An argument list applies to the expression before it, not only to a name. What is applied is
 * decided by that expression, which is what {@code Hir.Apply} holds.
 *
 * <p>Souther's grammar is not newline sensitive — a leading `.` or operator continues the line above
 * — but an argument list is the one thing that cannot follow a line break. A block whose statement
 * ends in a list or a tuple is followed by a result expression that often opens with `(`, and
 * reading that as an application takes the block's result away. The standard library is written
 * that way, so the rule is written down here.
 */
class CompilePostfixApplicationTest {

    /**
     * A helper is expanded into what calls it, and the expansion renames the body's bindings. A
     * callee that is not a name is renamed as the expression it is: rebuilding it from a name would
     * leave nothing behind, and the same application written in a behavior would keep working while
     * the one reached through a helper did not.
     */
    @Test
    void anAppliedExpressionSurvivesBeingExpandedIntoACallSite() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module demo
                let inc (x: Int) = x + 1
                let dec (x: Int) = x - 1
                let applySelected (flag: Bool, x: Int) = (if flag then inc else dec)(x)
                data In = { flag: Bool, n: Int }
                data Out = { n: Int }
                behavior go : (i: In) -> Out constructs Out
                let go (i) = Out { n = applySelected(i.flag, i.n) }
                """));
    }

    private static Map<?, ?> run(String source, Map<String, Object> in) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(source),
                CompilePostfixApplicationTest.class.getClassLoader());
        Object behavior = Emitted.behavior(loader, "demo", "go").getConstructor().newInstance();
        return (Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(behavior, Codecs.decoded(loader, "demo.In", in)));
    }

    /**
     * The one that exercises the whole path at once: a library name read as a value, a written block
     * it is chosen against, the choice made at run time, the result applied where it stands, the
     * synthetic binding that holds it, and the {@code Fn} the backend applies.
     */
    @Test
    void aChoiceBetweenAFunctionNameAndABlockIsAppliedWhereItStands() throws Exception {
        String src = """
                module demo

                data In = { s: String, flag: Bool }
                data Out = { t: String }

                behavior go : (i: In) -> Out constructs Out

                let go (i) = Out {
                    t = (if i.flag then String.trim else (s) -> String.append(s, "!"))(i.s)
                }
                """;

        assertEquals("a", run(src, Map.of("s", " a ", "flag", true)).get("t"));
        assertEquals("a!", run(src, Map.of("s", "a", "flag", false)).get("t"));
    }

    /** A function answered by an application is applied in turn. */
    @Test
    void anApplicationIsAppliedInTurn() throws Exception {
        Map<?, ?> out = run("""
                module demo

                data In = { n: Int }
                data Out = { m: Int }

                let bumpBy (k: Int) : (Int) -> Int = (x) -> x + k

                behavior go : (i: In) -> Out constructs Out

                let go (i) = Out { m = bumpBy(10)(i.n) }
                """, Map.of("n", 5L));

        assertEquals(15L, out.get("m"));
    }

    /**
     * An application of a function an expression worked out answers what applying that function
     * answers. Both sides are arithmetic, so this says the result is right and nothing about the
     * order the two sides ran in — which arithmetic cannot show. That the callee is worked out once
     * is said below, where it can be looked at.
     */
    @Test
    void anApplicationOfAComputedFunctionWorks() throws Exception {
        Map<?, ?> out = run("""
                module demo

                data In = { n: Int }
                data Out = { m: Int }

                let pick (k: Int) : (Int) -> Int = (x) -> x * k

                behavior go : (i: In) -> Out constructs Out

                let go (i) = Out { m = pick(i.n + 1)(i.n + 2) }
                """, Map.of("n", 3L));

        assertEquals(20L, out.get("m"), "(3+1) * (3+2)");
    }

    /**
     * The callee expression stands once in the lowered body. A pass that rebuilt the application
     * per argument, or read the callee once for its type and again for its value, would leave two —
     * and with a callee that constructs, the second is a construction the behavior never wrote.
     */
    @Test
    void theCalleeExpressionStandsOnce() {
        Map<String, String> byId = new java.util.LinkedHashMap<>();
        byId.put("a.sou", """
                module demo
                data In = { n: Int }
                data Out = { m: Int }
                let pick (k: Int) : (Int) -> Int = (x) -> x * k
                behavior go : (i: In) -> Out constructs Out
                let go (i) = Out { m = pick(i.n + 1)(i.n + 2) }
                """);
        Hir.FnDef def = souther.compiler.query.Compilation
                .ofDocuments(byId, java.util.Set.of(), souther.compiler.meta.ModulePath.EMPTY)
                .db().ask(new souther.compiler.query.Bodies.SettledFn("demo", "go")).value();
        assertEquals(1, occurrences(def.writtenBody(), "pick"), "the callee was worked out more than once");
    }

    private static int occurrences(Hir.Expr e, String callee) {
        int[] n = {e instanceof Hir.Apply a && a.answered() != null
                && callee.equals(a.answered().reaches()) ? 1 : 0};
        Hir.forEachChild(e, c -> n[0] += occurrences(c, callee));
        return n[0];
    }

    /** A field is still taken off what an application answered. */
    @Test
    void aFieldIsTakenOffWhatAnApplicationAnswered() throws Exception {
        Map<?, ?> out = run("""
                module demo

                data Amount = Int
                data In = { n: Int }
                data Out = { m: Int }

                let amountOf (n: Int) = Amount(n)

                behavior go : (i: In) -> Out constructs Out, Amount

                let go (i) = Out { m = amountOf(i.n).value }
                """, Map.of("n", 7L));

        assertEquals(7L, out.get("m"));
    }

    /**
     * A line break ends the reach of an argument list. Without this the result of a block whose
     * previous statement ends in a list or a tuple is read as an application of it, and the block is
     * left with no result at all.
     */
    @Test
    void anArgumentListDoesNotReachAcrossALineBreak() throws Exception {
        Map<?, ?> out = run("""
                module demo

                data In = { n: Int }
                data Out = { m: Int }

                behavior go : (i: In) -> Out constructs Out

                let go (i) = {
                    let xs = [i.n, i.n]
                    (Out { m = List.length(xs) })
                }
                """, Map.of("n", 1L));

        assertEquals(2L, out.get("m"));
    }

    /** Applying something that is not a function is told so, whatever expression answered it. */
    @Test
    void applyingSomethingThatIsNotAFunctionIsRefused() {
        assertThrows(Exception.class, () -> Compiler.compile("""
                module demo

                data In = { n: Int }
                data Out = { m: Int }

                behavior go : (i: In) -> Out constructs Out

                let go (i) = Out { m = (i.n)(i.n) }
                """));
    }

    /** The list literal is a value, not something an argument list on the next line applies to. */
    @Test
    void aListLiteralFollowedByAParenthesisedResultKeepsBoth() throws Exception {
        Map<?, ?> out = run("""
                module demo

                data In = { n: Int }
                data Out = { m: Int }

                behavior go : (i: In) -> Out constructs Out

                let go (i) = {
                    let pair = (i.n, i.n)
                    let (a, b) = pair
                    Out { m = a + b }
                }
                """, Map.of("n", 4L));

        assertEquals(8L, out.get("m"));
    }

    /**
     * A line comment ends the line, so what follows it is a new expression — the same answer a bare
     * newline gives. Souther has no comment that stays inside a line, so there is no third case.
     */
    @Test
    void aLineCommentEndsTheReachOfAnArgumentList() throws Exception {
        Map<?, ?> out = run("""
                module demo

                data In = { n: Int }
                data Out = { m: Int }

                behavior go : (i: In) -> Out constructs Out

                let go (i) = {
                    let ys = [i.n]   // the list this walks
                    (Out { m = List.length(ys) })
                }
                """, Map.of("n", 3L));

        assertEquals(1L, out.get("m"));
    }

    /**
     * The shape the standard library is written in, which is what decided the rule: a statement
     * ending in a list, and a result opening with a parenthesis.
     */
    @Test
    void aStatementEndingInAListIsNotAppliedToTheResultBelowIt() throws Exception {
        Map<?, ?> out = run("""
                module demo

                data In = { n: Int }
                data Out = { m: Int }

                behavior go : (i: In) -> Out constructs Out

                let go (i) = {
                    let ys = [i.n] ++ [i.n]
                    (Out { m = List.length(ys) })
                }
                """, Map.of("n", 2L));

        assertEquals(2L, out.get("m"));
    }

    /** A field access still continues the line above, as it always has. */
    @Test
    void aFieldAccessStillReachesAcrossALineBreak() throws Exception {
        Map<?, ?> out = run("""
                module demo

                data Amount = Int
                data In = { n: Int }
                data Out = { m: Int }

                let amountOf (n: Int) = Amount(n)

                behavior go : (i: In) -> Out constructs Out, Amount

                let go (i) = Out { m = amountOf(i.n)
                    .value }
                """, Map.of("n", 9L));

        assertEquals(9L, out.get("m"));
    }

    /**
     * What an application applies is asked for as what it is. A reader keyed by name gets the miss
     * it wants; a reader that would do something else with the absence is handed nothing to mistake
     * for a name.
     */
    @Test
    void anApplicationSaysWhetherItAppliesAName() {
        SourcePos at = new SourcePos(1, 1);
        souther.compiler.types.ValueName.Stdlib.Operation map =
                souther.compiler.types.ValueName.Stdlib.operation("List", "map");
        Hir.Apply named = Hir.Apply.synthetic("List.map",
                new ReachName.OfLibrary(map),
                new souther.compiler.types.SourceReferenceOrigin(
                        new souther.compiler.types.WrittenOwner.Body("m", "b"), 0),
                new souther.compiler.types.ApplicationOrigin.ComposedFixture(),
                java.util.List.of(), at, null);
        Hir.Apply nameless = Hir.Apply.synthetic(new Hir.Block(java.util.List.of(),
                new Hir.IntLit(1, at, null), souther.compiler.types.RuleOrigin.unwritten(), at, null),
                java.util.List.of(),
                new souther.compiler.types.ApplicationOrigin.ComposedFixture(), at, null);

        assertTrue(named.applied().isAName());
        assertEquals("List.map", named.written());
        assertEquals("List.map", named.answered().reaches());

        assertFalse(nameless.applied().isAName());
        assertEquals("", nameless.written(), "there is no spelling to quote");
        assertNull(nameless.answered(), "and no declaration to look up");
    }

    /**
     * What a lowering put in the callee position and what a report quotes are separate slots. An
     * application of something other than a name binds it first, so what this reaches is that
     * binding — and every table in the compiler is keyed by declarations, so a spelling reaching one
     * would be a miss that looked like a hit for whatever happened to be filed under it.
     *
     * <p>The rewrite is where the two part company, and it is the rewrite that keeps them together:
     * what the author applied was settled when the application was read, and putting the binding in
     * the callee position does not re-settle it.
     */
    @Test
    void whatAnApplicationReachesIsNeverTheSpellingAReportQuotes() {
        SourcePos at = new SourcePos(1, 1);
        BindingId id = new BindingId(new BindingOwner.OfValue("demo", "go"), 0);
        ValueName.Local fn0 = new ValueName.Local("$fn0", id);
        WrittenName applied = WrittenName.of("d", at)
                .then(WrittenName.of("count", new SourcePos(1, 3)));
        Hir.Apply lowered = readApplying(applied, at)
                .replacedBy(Hir.Var.denoting("$fn0", new ReachName.InScope(fn0), at));

        assertEquals("d.count", lowered.written(), "a report quotes what the author wrote");
        assertEquals(applied.region(), lowered.appliedAt(),
                "and underlines the characters they wrote it over");
        assertEquals("$fn0", lowered.answered().reaches(),
                "a table is looked up with the binding");
    }

    /** An application a source wrote, applying {@code applied} — as much of the reading as a
     *  question about what a report quotes needs. */
    private static Hir.Apply readApplying(WrittenName applied, SourcePos at) {
        Ast.Expr callee = new Ast.Var(applied, new SourceReferenceOrigin(new souther.compiler.types.WrittenOwner.Body("m", "f"), 0),
                applied.region());
        return Hir.Apply.read(new Ast.Apply(callee, java.util.List.of(),
                        SourceConstructOrigin.written(new souther.compiler.types.WrittenOwner.Body("m", "f"),
                                0, SourceConstruct.CALL), at, null),
                new Hir.AppliedCallee(applied, callee.reportedAt()),
                new Hir.Var.Unanswered(applied, null, applied.region()), java.util.List.of());
    }
}
