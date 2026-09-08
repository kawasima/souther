package souther.compiler.check;

import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.core.Core;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

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

/**
 * A term read for what it says, over every kind of term a model writes.
 *
 * <p>{@link TermMeaning} is a projection written a case per node kind, and which kinds anything here
 * reaches is measured rather than assumed: the permitted subtypes are the language's own list of
 * what a term can be, so a kind added later is unreached here until someone says what a reading of
 * it leaves out. A module written to reach what the corpus does not stands beside the corpus,
 * because the corpus is a set of models and a model has no reason to write every kind.
 *
 * <p>Over the whole corpus twice, so it is here rather than beside the checks a build runs: what one
 * of those asks of a fixture, this asks of every term two compilations of one corpus produce.
 * {@code EveryTermIsReadForWhatItSaysTest} is where a reading is held to each thing that says where
 * a node stands, one at a time.
 *
 * <p>Moving every line is the edit this can make. It moves every position and reaches no ordinal —
 * a module numbers its constructs after the ones the blank lines did not add — so what says where a
 * construct stands among its neighbours is held by the fixtures and not here.
 */
@Tag("population")
class EveryKindOfTermACorpusWritesIsReadForWhatItSaysTest {

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
     * property the fixtures are for, asked of everything a model writes.
     */
    @Test
    void movingEveryLineChangesNoReading() {
        List<TermMeaning> where = read(bodies(""));
        List<TermMeaning> moved = read(bodies("\n\n\n"));

        assertEquals(where.size(), moved.size(), "the same bodies compile either way");
        assertEquals(where, moved, "and each says what it said, three lines further down");
    }
}
