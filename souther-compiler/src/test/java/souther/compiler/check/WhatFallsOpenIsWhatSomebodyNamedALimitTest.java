package souther.compiler.check;

import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.check.InvariantChecker.GaveUp;
import souther.compiler.core.Core;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.query.Shapes;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.Diagnostic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The discharge check falls open on the limits it named and on nothing else.
 *
 * <p>It used to fall open on whatever a walk threw, so a representation refusing to be built and a
 * shape the walk has no rule for arrived as one thing: both a {@code RuntimeException} from the same
 * call, both read as "nothing could be analysed here", and the compile went on with the invariant
 * quietly undischarged. A reader of what came out had no way back to which of the two it was.
 *
 * <p>What is held here is the other direction. A limit exists because something with the standing to
 * say so made one, and a failure nobody made one of is not a limit — it leaves the analysis as what
 * it is. The two halves of that are held apart, because either alone is satisfied by a check that
 * gives up on everything or by one that gives up on nothing.
 */
class WhatFallsOpenIsWhatSomebodyNamedALimitTest {

    /** A recursive helper is admissible in an invariant and is left standing by the expansion, and
     *  a clause is read over the declaration's fields, which name no helper. */
    private static final String STANDING = """
            module demo
            data 木 = { 子: Option<木> }
            let 深さ (t: 木): Int = match t.子 with
                | Some c -> 深さ(c) + 1
                | None -> 0
            data 制限木 = { root: 木 } invariant 深さ(root) >= 0
            """;

    /**
     * The same helper left standing in a behavior's rule, where the reading is over the behavior's
     * signature and reaches the helper's own.
     */
    private static final String STANDING_AND_READABLE = """
            module demo
            data 木 = { 子: Option<木> }
            let 深さ (t: 木): Int = match t.子 with
                | Some c -> 深さ(c) + 1
                | None -> 0
            data Depth = Int
            behavior measure : (t: 木) -> Depth
                constructs Depth
                ensures deep = value.value == 深さ(t)
            let measure (t) = Depth { value = 深さ(t) }
            """;

    /** Two recursive helpers, both left standing, both named in one clause. */
    private static final String TWO_STANDING = """
            module demo
            data 木 = { 子: Option<木> }
            let 深さ (t: 木): Int = match t.子 with
                | Some c -> 深さ(c) + 1
                | None -> 0
            let 幅 (t: 木): Int = match t.子 with
                | Some c -> 幅(c)
                | None -> 1
            data 制限木 = { root: 木 } invariant 深さ(root) >= 0 && 幅(root) >= 1
            """;

    /**
     * An expansion's answer naming only the helpers {@code kept} spells — what an expansion that
     * failed to remove the others would have left behind.
     *
     * <p>Read off the clause rather than off the answer, so that the test builds the state it is
     * about from what is written there.
     */
    private static CallsLeftStanding only(Hir.Expr clause, String kept) {
        java.util.SequencedSet<souther.compiler.types.ReachName.Declaration> named =
                new java.util.LinkedHashSet<>();
        helpersApplied(clause, kept, named);
        assertFalse(named.isEmpty(), "`" + kept + "` is applied in the clause this reads");
        return CallsLeftStanding.of(named);
    }

    private static void helpersApplied(Hir.Expr read, String kept,
            java.util.SequencedSet<souther.compiler.types.ReachName.Declaration> out) {
        if (read instanceof Hir.Apply call
                && call.answered() instanceof Hir.Var.Denoting callee
                && kept.equals(call.written())) {
            out.add(callee.reachesADeclaration());
        }
        Hir.forEachChild(read, child -> helpersApplied(child, kept, out));
    }

    /** The same shape with nothing standing: the clause reads a field and the language's own
     *  operations, which is what the analysis has rules about. */
    private static final String READABLE = """
            module demo
            data 制限木 = { root: Int } invariant root >= 0
            """;

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final ReadingPolicy POLICY = new ReadingPolicy(64, 12,
            souther.compiler.values.AsACompilationAllows.admittedValues(),
            souther.compiler.values.AsACompilationAllows.whatARuleLeaves());

    private static List<GaveUp> compiling(String source) {
        List<GaveUp> gaveUp = Collections.synchronizedList(new ArrayList<>());
        InvariantChecker.GAVE_UP = gaveUp;
        try {
            Compiler.compileWithWarnings(source);
        } finally {
            InvariantChecker.GAVE_UP = null;
        }
        return gaveUp;
    }

    // --- what is a limit -------------------------------------------------------------------------

    /**
     * A call the expansion left standing that the clause's own reading cannot name.
     *
     * <p>Both halves are what makes it one. The expansion left the call standing because the helper
     * recurses, which says the tree is the one it meant to produce; the reading is over the
     * declaration's fields, which name no helper. Neither on its own is a limit, and the negative
     * control below is a clause of the same shape with nothing standing in it.
     */
    @Test
    void aStandingCallThisReadingCannotNameIsALimitItFallsOpenOn() {
        List<GaveUp> gaveUp = compiling(STANDING);

        assertFalse(gaveUp.isEmpty(), "the reading stopped on the standing call");
        assertTrue(gaveUp.stream().allMatch(each -> each.why().said().contains("深さ")),
                () -> "and every limit it met names that call: "
                        + gaveUp.stream().map(GaveUp::why).toList());
    }

    /** And a clause with nothing standing in it is read to the end, so the answer above is about
     *  the standing call and not about every clause this check meets. */
    @Test
    void andAClauseWithNothingStandingIsReadToTheEnd() {
        assertTrue(compiling(READABLE).isEmpty(),
                "nothing was left standing and nothing was refused, so nothing was owed");
    }

    /**
     * And a standing call the reading <em>can</em> name is read like any other.
     *
     * <p>The half that keeps the answer above from being "a standing call stops this reading". The
     * expansion leaves the same helper standing in a behavior's rule, and there the reading is over
     * the behavior's signature, which reaches that helper's own — so nothing stops, and being left
     * standing is not on its own a reason to.
     */
    @Test
    void andAStandingCallThisReadingCanNameIsReadLikeAnyOther() {
        assertTrue(compiling(STANDING_AND_READABLE).stream()
                        .noneMatch(each -> each.where().startsWith("typing measure")),
                () -> "the rule reaches the helper's signature, so reading it stops on nothing: "
                        + compiling(STANDING_AND_READABLE));
    }

    /**
     * And what a clause is read over is worked out inside the reading.
     *
     * <p>A declaration whose fields this compiler refuses is refused by the check that answers for
     * the program. Working the fields out before the reading begins would let that refusal leave by
     * a door this policy has no name for, though it is the same reading failing — so this holds
     * that the compile ends the way the authoritative check ends it, and not with something thrown
     * out of the analysis.
     */
    @Test
    void whatAClauseIsReadOverIsWorkedOutInsideTheReading() {
        Compilation c = answered(STANDING);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey("demo", "制限木"));
        TypeOps.Declared clause = expandedClauseOf(c, named);
        List<GaveUp> gaveUp = new ArrayList<>();
        InvariantChecker.GAVE_UP = gaveUp;
        try {
            TypedClause read = SecondaryClauseReading.of(clause.asExpanded(), () -> {
                throw CompileException.of(Diagnostic
                        .say(new souther.compiler.diag.msg.ModuleMessage.DuplicateModule("demo")).nowhere().build());
            }, "a test");

            assertInstanceOf(TypedClause.Stopped.class, read,
                    "what a clause is read over is part of reading it, so a refusal working it out"
                            + " is this reading not finishing");
            assertEquals(1, gaveUp.size(), () -> "and it is recorded as the limit it is: " + gaveUp);
        } finally {
            InvariantChecker.GAVE_UP = null;
        }
    }

    // --- what is not ------------------------------------------------------------------------------

    /**
     * A failure nobody named a limit leaves the analysis rather than being recorded as one.
     *
     * <p>Raised where this compiler reads its own answers — a lookup of what a declaration says —
     * because that is what the swallowed failures were: not the program being unreadable, but this
     * compiler failing to produce what it says it produces. Swallowed, it would leave a behavior
     * with no findings, which is what a behavior whose every construction is proven leaves.
     */
    @Test
    void aFailureNobodyNamedALimitLeavesTheAnalysis() {
        Compilation c = answered(READABLE);
        Core body = new Core.Int(1, Type.INT, POS);
        // A parameter of a declared type, which is what makes the analysis read that declaration's
        // clauses before it walks anything.
        Scope params = heldTo(new Type.Ref(TypeSymbols.declared(new TypeKey("demo", "制限木"))));

        assertEquals(InvariantChecker.Status.COMPLETE,
                InvariantChecker.analyze(body, readingOf(c, saidBy(c)),
                        DeclarationReadings.NONE, Map.of(), params, POLICY).status(),
                "the control: read through a lookup that answers, this analysis runs to the end");

        PublishedDeclarations broken = _ -> {
            throw new IllegalStateException("this compiler could not read its own answer");
        };
        IllegalStateException why = assertThrows(IllegalStateException.class,
                () -> InvariantChecker.analyze(body, readingOf(c, broken),
                        DeclarationReadings.NONE, Map.of(), params, POLICY),
                "the analysis has no rule that makes this the program's problem");

        assertEquals("this compiler could not read its own answer", why.getMessage(),
                "and it arrives as what it is, rather than as an analysis that read nothing");
    }

    private static Scope heldTo(Type type) {
        BindingId id = new BindingId(new BindingOwner.OfValue("demo", "f"), 0);
        return Scope.of(Map.of(id, new Scope.Binding("v", type)));
    }

    /**
     * And one call the expansion named does not cover another it did not.
     *
     * <p>The clause holds two standing calls this reading cannot name, and the expansion's answer
     * names only one of them. Stopping on the one it named would report this compiler's own failure
     * — a call left in a tree nothing meant to leave it in — as an ordinary limit, which is what
     * every other test here is about, met one clause further in rather than at the boundary.
     *
     * <p>Reached by telling the reading that the expansion left only the second of the two, which
     * is what an expansion that failed to remove the first would leave behind. Nothing an author
     * can write makes this compiler fail to expand what it says it expands, which is what makes the
     * reading the only place the answer can be held to.
     */
    @Test
    void aCallNoExpansionNamedIsNotCoveredByOneItDid() {
        Compilation c = answered(TWO_STANDING);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey("demo", "制限木"));
        Hir.Data data = declarationOf(c, named);
        TypeOps.Declared clause = expandedClauseOf(c, named);
        Supplier<SecondaryClauseReading.Over> over =
                () -> new SecondaryClauseReading.Over(
                        DataChecker.fieldScope(named, data, symbolsOf(c)),
                        CheckContext.of(symbolsOf(c)).forData(data).forDischarge());

        assertInstanceOf(TypedClause.Stopped.class,
                SecondaryClauseReading.of(clause.asExpanded(), over, "a test"),
                "the control: the expansion named both, so both are limits and this reading stops");

        assertThrows(IllegalStateException.class,
                () -> SecondaryClauseReading.of(
                        new ClauseAsExpanded(clause.clause().expr(),
                                only(clause.clause().expr(), "幅")),
                        over, "a test"),
                "and where one of them is a call no expansion named, this reading does not stop on"
                        + " the other: what the unnamed one is, the typing says");
    }

    /**
     * A call standing in a tree no expansion named is not one of these limits either.
     *
     * <p>The same clause and the same reading, differing only in whether the expansion says it left
     * the call there. Told that it did, this reading stops and the run-time check stands; told that
     * it did not, the tree is one this compiler failed to expand, and the elaborator says so.
     */
    @Test
    void aStandingCallNoExpansionNamedIsNotALimitButAFailure() {
        Compilation c = answered(STANDING);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey("demo", "制限木"));
        Hir.Data data = declarationOf(c, named);
        TypeOps.Declared clause = expandedClauseOf(c, named);
        java.util.function.Supplier<SecondaryClauseReading.Over> over =
                () -> new SecondaryClauseReading.Over(
                        DataChecker.fieldScope(named, data, symbolsOf(c)),
                        CheckContext.of(symbolsOf(c)).forData(data).forDischarge());

        assertInstanceOf(TypedClause.Stopped.class,
                SecondaryClauseReading.of(clause.asExpanded(), over, "a test"),
                "the expansion says it left the call standing, so this reading stops on it");

        assertThrows(IllegalStateException.class,
                () -> SecondaryClauseReading.of(
                        new ClauseAsExpanded(clause.clause().expr(), CallsLeftStanding.NONE),
                        over, "a test"),
                "and where no expansion left it there, it is this compiler that failed");
    }

    // --- what the expansion answers ---------------------------------------------------------------

    /** The standing calls travel with the tree the expansion produced, rather than being worked out
     *  again by a reader looking at a helper applied. */
    @Test
    void anExpansionSaysWhatItLeftStanding() {
        Compilation c = answered(STANDING);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey("demo", "制限木"));

        assertFalse(expandedClauseOf(c, named).standing().leftNothing(),
                "the recursive helper the clause names is left standing");
    }

    // --- reading the compilation ------------------------------------------------------------------

    private static Compilation answered(String source) {
        Compilation c = Compilation.ofSource(source, "Main");
        c.answerEverything();
        return c;
    }

    private static Symbols symbolsOf(Compilation c) {
        return Scopes.derived(c.db(), c.modules().get(0)).value();
    }

    private static ExpandedClauseLookup lookupOf(Compilation c) {
        return RuleReadings.declaredBy(c.db(), c.modules().get(0));
    }

    /** Where what {@code c}'s declarations say is answered from. */
    private static PublishedDeclarations saidBy(Compilation c) {
        return Shapes.publishedDeclarations(c.db());
    }

    /** {@code c}'s scope, reading what its declarations say from {@code said} — a source this test
     *  assembles, which is what lets it put a lookup of its own where the compilation's would be. */
    private static RuleReadingSource readingOf(Compilation c, PublishedDeclarations said) {
        return new RuleReadingSource(symbolsOf(c), lookupOf(c), said, ClauseLocations.NONE);
    }

    private static Hir.Data declarationOf(Compilation c, TypeSymbol.AtModule named) {
        Hir.Data data = symbolsOf(c).declaredNode(named) instanceof Hir.Data it ? it : null;
        assertNotNull(data, () -> named + " is declared by the source this reads");
        return data;
    }

    private static TypeOps.Declared expandedClauseOf(Compilation c, TypeSymbol.AtModule named) {
        List<TypeOps.Declared> reached = TypeOps.expandedInvariants(named, symbolsOf(c),
                lookupOf(c)).reached();
        assertEquals(1, reached.size(), () -> named + " writes the one clause this reads");
        return reached.get(0);
    }
}
