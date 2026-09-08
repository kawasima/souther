package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.KeptCalls;
import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.compiler.types.ReferenceOrigin;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A term read for what it says, over every kind of term there is.
 *
 * <p>{@link TermMeaning} is a projection written a case per node kind, and a case is wrong in two
 * ways a compiler cannot see: it can read something that says where the node stands, and it can
 * leave out something the node says. The first makes a caller depend on an edit it cannot see; the
 * second makes two terms that say different things one dependency, which is the worse of the two —
 * a projection that read nothing at all would pass every test that only asks what it ignores.
 *
 * <p>So both are asked of each of the five things that say where a node stands: the position, the
 * occurrence a comparison or a fork is of the model, the copy of the body a fork stands in, and the
 * name and application a kept call was written as. Each is asked twice — moving it leaves the
 * reading alone, and moving what the node says does not.
 *
 * <p>Which kinds of term this reaches is measured rather than assumed. The corpus is a set of
 * models, and a model has no reason to write every kind, so a module written to reach the rest
 * stands beside it and what is still unreached is named.
 */
@Tag("population")
class EveryTermIsReadForWhatItSaysTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final SourcePos ELSEWHERE = new SourcePos(9, 4);
    private static final WrittenOwner OWNER = new WrittenOwner.Body("demo", "b");

    /**
     * A module written to reach what the corpus does not. The corpus is a set of models, and a model
     * has no reason to write every kind of term; this has no reason to be a model.
     */
    private static final String WIDE = """
            module wide.terms exposing ( In, Out, Small, run )

            import List ( all, get, find, length )
            import Option ( map, withDefault )

            data Small = Int
                invariant value > 0

            data In  = { xs: List<Int>, s: String, k: Int, note: String?, on: Date }
            data Out = { n: Int, label: String, pair: Int, kept: String? }
            data Bag = { items: List<Int> }

            data Yes = { n: Int }
            data No  = { n: Int }

            let doubled (v) = v * 2


            behavior bagged : (i: In) -> Bag
                constructs Bag
                ensures length(value.items) >= i.k - i.k
            let bagged (i) = Bag { items = i.xs }

            behavior pick : (i: In) -> Yes | No
                constructs Yes, No
            let pick (i) = if i.k > 0 then Yes { n = i.k } else No { n = 0 - i.k }

            behavior only : (i: In) -> Small
                constructs Small
            let only (i) = match pick(i) with
                | Yes -> Small(1)
                | No  -> unreachable "the corpus only ever hands this a positive"

            behavior run : (i: In) -> Out
                constructs Out, Small
            let run (i) = {
                let negated = -doubled(i.k)
                let some = get(0, i.xs) |> map(n -> n + 1) |> withDefault(0)
                let none = find(n -> n > 1000000, i.xs) |> map(n -> n) |> withDefault(0)
                let every = all(n -> n >= 0, i.xs)
                let (left, right) = (some, none)
                let noted = match i.note with
                    | Some t -> String.length(t)
                    | None   -> 0
                let picked = if Small(negated) as ok then ok.value else noted
                let fs: List<(Int) -> Int> = [(x) -> x + 1, (x) -> x + 2]
                let applied = all((f) -> f(i.k) > 0, fs)
                let dated = if i.on < Date("2026-01-01") then 1 else 0
                Out {
                    n = left + right + picked + length([1, 2, 3])
                            + (if every then 1 else 0) + (if applied then 1 else 0) + dated,
                    label = "fixed",
                    kept = "here",
                    pair = negated
                }
            }
            """;

    /** Every checked body of every corpus, and the same again with every line moved down. */
    private static List<Core> bodies(String before) {
        List<Core> out = new ArrayList<>();
        List<Map<String, String>> workspaces = new ArrayList<>();
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            Map<String, String> byId = new LinkedHashMap<>();
            for (int i = 0; i < corpus.sources().size(); i++) {
                byId.put(corpus.files().get(i), before + corpus.sources().get(i));
            }
            workspaces.add(byId);
        }
        workspaces.add(Map.of("wide.sou", before + WIDE));
        boolean wide = false;
        for (Map<String, String> byId : workspaces) {
            wide = byId.containsKey("wide.sou");
            Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
            c.answerEverything();
            if (wide) {
                assertEquals(List.of(), c.db().allReports().stream().map(Object::toString).toList(),
                        "the module written to reach the rest of the kinds compiles");
            }
            for (String module : c.modules()) {
                // The behaviors a module declares, asked through the accessor a build asks it
                // through. There was a query of its own for this and nothing but this reached it.
                Set<String> names = c.declaredBehaviors(module);
                if (names == null) {
                    continue;
                }
                for (String behavior : new TreeSet<>(names)) {
                    Bodies.CheckedBody checked =
                            c.db().ask(new Bodies.CheckedBehavior(module, behavior)).value();
                    if (checked != null && checked.body() != null) {
                        out.add(checked.body());
                    }
                }
                Map<String, StatedContract> stated =
                        c.db().ask(new Bodies.StatedContracts(module)).value();
                if (stated == null) {
                    continue;
                }
                for (String behavior : new TreeSet<>(stated.keySet())) {
                    for (StatedContract.StatedRule rule : stated.get(behavior).rules()) {
                        for (StatedContract.Conjunct each : rule.conjuncts()) {
                            if (each.stated().orNull() != null) {
                                out.add(each.stated().orNull());
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void each(Core e, java.util.function.Consumer<Core> f) {
        if (e == null) {
            return;
        }
        f.accept(e);
        Core.forEachChild(e, child -> each(child, f));
    }

    private static Set<String> kindsIn(List<Core> terms) {
        Set<Class<?>> found = new LinkedHashSet<>();
        for (Core term : terms) {
            each(term, node -> found.add(node.getClass()));
        }
        return named(found);
    }

    private static Set<String> named(Set<Class<?>> kinds) {
        Set<String> out = new TreeSet<>();
        for (Class<?> kind : kinds) {
            out.add(kind.getSimpleName());
        }
        return out;
    }

    private static List<TermMeaning> read(List<Core> terms) {
        return terms.stream().map(TermMeaning::of).toList();
    }

    /**
     * The one kind no source reaches, and why.
     *
     * <p>A model cannot write an absence. An optional stands on a data field, a construction has to
     * give that field a value, and there is no way to spell the empty one outside a fixture — which
     * is never elaborated, so it makes no term. E1402 says as much where a model tries: answer a
     * list of nought or one instead. So this is here rather than reached, and a kind that turns up
     * beside it is one someone has to say the same about.
     */
    private static final Set<String> WRITTEN_BY_NOTHING = new TreeSet<>(Set.of("OptionNone"));

    @Test
    void everyKindOfTermTheCorpusWritesIsReached() {
        Set<String> reached = kindsIn(bodies(""));
        Set<String> declared = named(Set.of(Core.class.getPermittedSubclasses()));
        declared.removeAll(reached);

        assertEquals(WRITTEN_BY_NOTHING, declared,
                "a kind of term nothing here reaches is one nothing here reads");
    }

    /**
     * And two readings of one corpus that differ only in where its lines are come to one value. The
     * property the rest of this is for: a caller depending on what a term says is not an edit away
     * from a blank line somewhere above it.
     */
    @Test
    void movingEveryLineChangesNoReading() {
        List<TermMeaning> where = read(bodies(""));
        List<TermMeaning> moved = read(bodies("\n\n\n"));

        assertEquals(where.size(), moved.size(), "the same bodies compile either way");
        assertEquals(where, moved, "and each says what it said, three lines further down");
    }

    @Test
    void aPositionIsNotRead() {
        assertEquals(TermMeaning.of(new Core.Int(1, Type.INT, POS)),
                TermMeaning.of(new Core.Int(1, Type.INT, ELSEWHERE)),
                "one term written twice over says one thing");
    }

    @Test
    void andWhatTheTermSaysIs() {
        assertNotEquals(TermMeaning.of(new Core.Int(1, Type.INT, POS)),
                TermMeaning.of(new Core.Int(2, Type.INT, POS)),
                "two terms saying different things are two readings");
    }

    @Test
    void whichComparisonOfTheModelAComparisonIsIsNotRead() {
        assertEquals(TermMeaning.of(compared(BinOp.GT, ConstructOccurrence.unwritten())),
                TermMeaning.of(compared(BinOp.GT, written(SourceConstruct.BINARY))),
                "a comparison states what it states wherever the module counted it");
    }

    @Test
    void andWhatItComparesWithIs() {
        assertNotEquals(TermMeaning.of(compared(BinOp.GT, ConstructOccurrence.unwritten())),
                TermMeaning.of(compared(BinOp.LT, ConstructOccurrence.unwritten())),
                "two operators are two comparisons");
    }

    @Test
    void whichCopyOfTheBodyAForkStandsInIsNotRead() {
        assertEquals(TermMeaning.of(forked(List.of())),
                TermMeaning.of(forked(List.of(new BindingOwner.OfValue("demo", "helper")))),
                "a fork states what it states in whichever copy it was read out of");
    }

    @Test
    void andWhatTheForkAsksDoesIs() {
        assertNotEquals(TermMeaning.of(forked(List.of())),
                TermMeaning.of(new Core.If(new Core.Bool(false, Type.BOOL, POS),
                        new Core.Int(1, Type.INT, POS), new Core.Int(0, Type.INT, POS),
                        ConstructOccurrence.unwritten(), Type.INT, POS, List.of())),
                "two forks asking different things are two readings");
    }

    /**
     * A kept call's two, which go together and go together here: what it applies and why it is here
     * are both about where the call stands, and a reading that took either would move with an edit
     * above the declaration.
     */
    @Test
    void whatAKeptCallWasWrittenAsIsNotRead() {
        assertEquals(TermMeaning.of(kept(new FixtureReferenceOrigin(0),
                        new ApplicationOrigin.ComposedFixture())),
                TermMeaning.of(kept(new SourceReferenceOrigin(OWNER, 0),
                        new ApplicationOrigin.Written(written(SourceConstruct.CALL).origin()))),
                "a call applies the operation it applies however the module reached it");
    }

    @Test
    void andWhatItAppliesIs() {
        assertNotEquals(TermMeaning.of(kept(new FixtureReferenceOrigin(0),
                        new ApplicationOrigin.ComposedFixture())),
                TermMeaning.of(KeptCalls.to(ValueName.Stdlib.operation("List", "length"),
                        List.of(new Core.Str("", Type.STRING, POS)), Type.INT, POS)),
                "two operations are two calls");
    }

    @Test
    void whatAWrittenTemporalWasSpelledAsIsNotRead() {
        assertEquals(TermMeaning.of(new Core.Temporal(Type.Prim.DATE, "2026-01-01",
                        new ApplicationOrigin.ComposedFixture(), POS)),
                TermMeaning.of(new Core.Temporal(Type.Prim.DATE, "2026-01-01",
                        new ApplicationOrigin.Written(written(SourceConstruct.CALL).origin()),
                        ELSEWHERE)),
                "a temporal denotes the value it denotes however it was constructed");
    }

    @Test
    void andWhichTemporalItIsIs() {
        assertNotEquals(TermMeaning.of(new Core.Temporal(Type.Prim.DATE, "2026-01-01",
                        new ApplicationOrigin.ComposedFixture(), POS)),
                TermMeaning.of(new Core.Temporal(Type.Prim.DATE, "2026-01-02",
                        new ApplicationOrigin.ComposedFixture(), POS)),
                "two days are two values");
    }

    /**
     * And two readings that come to one value are assumed alike.
     *
     * <p>The property the projection is for. Comparing equal is worth nothing on its own: what it
     * has to mean is that everything a caller may do with one of these answers the same of both, or
     * the store has decided two answers are one and left a reader able to tell them apart. The
     * reading a caller takes goes to the predicates, and what came back used to be the node the walk
     * stopped on — a term, with the place it was written at on it.
     *
     * <p>What the reading carries is a term of the check's own, which names a value by how it is
     * built and not by where it was written. That is what makes this hold, and it is a fact about
     * that naming rather than about this reading — so it is asked here, where breaking it would make
     * two readings the store called one answer with different things.
     */
    @Test
    void twoReadingsOfOneTermAreAssumedAlike() {
        PathEngine engine = new PathEngine(Symbols.none(DefaultStdlib.get()),
                RuleReadings.noClauseFiled(), DeclarationReadings.NONE,
                Terms.Of.THE_DISCHARGE_TREE, ReadAs.THE_COMPILATION_DOES);
        Predicates predicates = engine.predicates();

        Predicates.Owed one = TermMeaning.of(containment(POS))
                .assumedBy(predicates, Denotations.none(), false);
        Predicates.Owed other = TermMeaning.of(containment(ELSEWHERE))
                .assumedBy(predicates, Denotations.none(), false);

        assertEquals(TermMeaning.of(containment(POS)), TermMeaning.of(containment(ELSEWHERE)),
                "one rule written twice over is one reading");
        assertTrue(one.parts().get(0) instanceof Predicates.Part.Carried,
                "this asks about the answer a rule the reading carries comes to");
        assertEquals(one, other, "and what the predicates make of one reading is one answer");
    }

    /**
     * {@code List.contains(1, [2])}, which this check reads as a value and not as a term.
     *
     * <p>Over literals and not over a name: a rule naming a value states something of that value,
     * and what this wants is a rule the reading makes nothing at all of.
     */
    private static Core containment(SourcePos pos) {
        return KeptCalls.to(ValueName.Stdlib.operation("List", "contains"),
                List.of(new Core.Int(1, Type.INT, pos),
                        new Core.ListLit(List.of(new Core.Int(2, Type.INT, pos)),
                                Type.list(Type.INT), pos)),
                Type.BOOL, pos);
    }

    private static ConstructOccurrence written(SourceConstruct kind) {
        return ConstructOccurrence.asWritten(SourceConstructOrigin.written(OWNER, 0, kind));
    }

    private static Core compared(BinOp op, ConstructOccurrence occurrence) {
        return new Core.Binary(op, new Core.Int(1, Type.INT, POS), new Core.Int(2, Type.INT, POS),
                occurrence, Type.BOOL, POS);
    }

    private static Core forked(List<BindingOwner> expansion) {
        return new Core.If(new Core.Bool(true, Type.BOOL, POS),
                new Core.Int(1, Type.INT, POS), new Core.Int(0, Type.INT, POS),
                ConstructOccurrence.unwritten(), Type.INT, POS, expansion);
    }

    private static Core kept(ReferenceOrigin reference, ApplicationOrigin application) {
        Core.PreservedCall call = KeptCalls.to(ValueName.Stdlib.operation("List", "isEmpty"),
                List.of(new Core.Str("", Type.STRING, POS)), Type.BOOL, POS);
        return new Core.PreservedCall(call.declared(), call.args(), reference, application,
                call.type(), POS);
    }
}
