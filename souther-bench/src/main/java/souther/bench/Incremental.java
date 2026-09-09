package souther.bench;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What an edit costs a store that already holds the answers — the latency an author feels between a
 * keystroke and the diagnostics catching up.
 *
 * <p>Re-asking without an edit is the floor: every answer still holds, and what is left is the walk
 * that establishes that. A trailing comment reaches the parse and stops there, because the parse of
 * a file with a comment added at the end is the same parse. Adding a definition changes what the
 * module declares, so it reaches everything that reads the declaration.
 *
 * <p>Each is warmed for as long as it is measured. An edit reaches less code than a compile, so it
 * is slower to reach steady state — a handful of rounds reports a number several times the one an
 * author would see.
 *
 * <p>Which file the definition is added to is most of the answer, so it is added twice: to the
 * first source, which the others import, and to the last, which nothing imports. The two numbers
 * bound what an author pays while typing — the first is the cost of editing the type everything is
 * built on, the second the cost of editing a leaf, and a workspace is mostly leaves.
 *
 * <p>Restating a relation is a fifth, and it is the one the other four cannot stand in for. Adding a
 * definition gives the module something it did not declare, so what is re-asked is everything that
 * reads the declaration list. Rewriting a rule of an {@code ensures} leaves the declaration where it
 * was and changes what a caller of that behavior may assume, while what the compiler says about the
 * corpus and how well its rows cover it come back as they were. So the bodies that call it are what
 * has anything to learn, and this is where an answer held per behavior rather than per module would
 * show, and where losing one would show as well. The two edits are the same keystroke to an author,
 * and what separates them is only visible if both are timed.
 */
final class Incremental {

    /**
     * The rule a relation edit rewrites, written out as the corpus writes it.
     *
     * <p>Matched as text rather than found by walking the parse, because what is being edited here is
     * text: an author types into a file, and the measurement is of what the store does with the file
     * that comes out. A rule located any other way would still have to be turned back into the line
     * it replaces.
     *
     * <p>That the corpus still writes this line is held by
     * {@code TheRelationAnEditRestatesIsOneACallerReadsTest} rather than found here at run time. A
     * corpus that stopped writing it would leave this measurement silently unreported, and a line
     * that is sometimes absent is one nobody notices the absence of.
     */
    static final String STATED_RULE =
            "    ensures onTheDomainAsked = Account -> accountHasDomain(domain, value)";

    /** What the rule already says, said again — see {@link #restated}. */
    private static final String RESTATED_CONJUNCT = " && accountHasDomain(domain, value)";

    private Incremental() {}

    /**
     * One edit that is timed, as the round that makes it.
     *
     * <p>What is timed and nothing standing for it. A reader asking what an edit figure covers runs
     * this, and a reader taking the figure runs this, so the two cannot come to be about different
     * edits — which is the whole of what {@code CorpusTest} needs to be able to say anything about
     * what these numbers reach.
     */
    record Edit(String name, Consumer<Integer> round) {}

    /**
     * The store an edit figure is taken against, and the edits taken on it.
     *
     * <p>One store for all of them, as a language server has one: an edit is timed against answers
     * that are already there, and a store per edit would be timing the first compile each time.
     */
    record Edits(Compilation compilation, List<Edit> edits) {}

    static void measure(Report report, Corpus corpus) {
        Edits taken = edits(corpus);
        for (Edit edit : taken.edits()) {
            Timing timing = Timing.ofRounds(40, 40, edit.round());
            report.line("EDIT  %-14s %-38s %6.2f ms", corpus.name(), edit.name(),
                    timing.medianMillis());
        }
    }

    /** The store warmed to the point an edit is timed from, and every edit that is timed on it. */
    static Edits edits(Corpus corpus) {
        Map<String, String> byId = new LinkedHashMap<>();
        for (int i = 0; i < corpus.sources().size(); i++) {
            byId.put("f" + i, corpus.sources().get(i));
        }
        Compilation compilation = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        compilation.diagnostics();

        List<String> ids = List.copyOf(byId.keySet());
        String imported = ids.getFirst();
        String leaf = ids.getLast();

        List<Edit> edits = new ArrayList<>();
        edits.add(new Edit("re-ask", _ -> {
            compilation.update(byId, Set.of());
            compilation.diagnostics();
        }));
        edits.add(new Edit("comment", round ->
                apply(compilation, byId, imported,
                        byId.get(imported) + "\n// round " + round + "\n")));
        edits.add(new Edit("definition in an imported module", round ->
                apply(compilation, byId, imported, added(byId.get(imported), round))));
        edits.add(new Edit("definition in a leaf", round ->
                apply(compilation, byId, leaf, added(byId.get(leaf), round))));

        String stating = statingSource(byId);
        if (stating != null) {
            edits.add(new Edit("a rule of a relation its callers read", round ->
                    apply(compilation, byId, stating, restated(byId.get(stating), round))));
        }
        return new Edits(compilation, edits);
    }

    /** Which source states the rule, or null where this corpus states none. */
    private static String statingSource(Map<String, String> byId) {
        for (Map.Entry<String, String> source : byId.entrySet()) {
            if (source.getValue().contains(STATED_RULE)) {
                return source.getKey();
            }
        }
        return null;
    }

    /**
     * {@code source} with the rule restated, alternating between rounds so that no round writes what
     * the round before it wrote — two rounds writing one text is no edit, and the second would find
     * every answer holding and time the floor.
     *
     * <p>The conjunct the rule already states is written a second time and taken away again. It says
     * what the rule said, which is the point: what a caller may assume is a different value and every
     * other reading of the corpus is the one it was. An edit that added a relation instead — a
     * comparison of the parameter against a bound, which is what an author tightening a rule writes —
     * draws a line on what it compares (spec §a-clause-draws-a-line-on-what-it-compares-an-input-against)
     * and moves the adequacy reading with it, so the round would be timing two things and the number
     * would be attributable to neither. Both halves of that are held by
     * {@code TheRelationAnEditRestatesIsOneACallerReadsTest}.
     */
    static String restated(String source, int round) {
        return round % 2 != 0 ? source
                : source.replace(STATED_RULE, STATED_RULE + RESTATED_CONJUNCT);
    }

    private static String added(String source, int round) {
        return source
                + "\nbehavior benchAdded" + round + " : (x: Int) -> Int\n"
                + "let benchAdded" + round + " (x) = x\n";
    }

    private static void apply(Compilation compilation, Map<String, String> byId, String id,
                              String text) {
        Map<String, String> now = new LinkedHashMap<>(byId);
        now.put(id, text);
        compilation.update(now, Set.of());
        compilation.diagnostics();
    }
}
