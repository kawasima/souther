package souther.bench;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 */
final class Incremental {

    private Incremental() {}

    static void measure(Report report, Corpus corpus) {
        Map<String, String> byId = new LinkedHashMap<>();
        for (int i = 0; i < corpus.sources().size(); i++) {
            byId.put("f" + i, corpus.sources().get(i));
        }
        Compilation compilation = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        compilation.diagnostics();

        List<String> ids = List.copyOf(byId.keySet());
        String imported = ids.getFirst();
        String leaf = ids.getLast();

        Timing reask = Timing.ofRounds(40, 40, _ -> {
            compilation.update(byId, Set.of());
            compilation.diagnostics();
        });
        Timing comment = Timing.ofRounds(40, 40, round ->
                apply(compilation, byId, imported, byId.get(imported) + "\n// round " + round + "\n"));
        Timing atImported = Timing.ofRounds(40, 40, round ->
                apply(compilation, byId, imported, added(byId.get(imported), round)));
        Timing atLeaf = Timing.ofRounds(40, 40, round ->
                apply(compilation, byId, leaf, added(byId.get(leaf), round)));

        report.line("EDIT  %-14s re-ask %6.2f ms   comment %6.2f ms   "
                        + "definition in an imported module %6.2f ms   in a leaf %6.2f ms",
                corpus.name(), reask.medianMillis(), comment.medianMillis(),
                atImported.medianMillis(), atLeaf.medianMillis());
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
