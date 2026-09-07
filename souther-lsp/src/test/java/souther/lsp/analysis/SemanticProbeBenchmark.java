package souther.lsp.analysis;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import souther.compiler.ast.Hir;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Abandonment;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What it costs to answer about a line the author has not finished — the measurement the decision to
 * keep a second store rests on.
 *
 * <p>To re-measure, take the {@code @Disabled} off and run
 * {@code mvn -o test -Dtest=SemanticProbeBenchmark -Dsurefire.failIfNoSpecifiedTests=false}. Nothing
 * on the command line turns a disabled test back on, and the run's JVM flags are the reactor's —
 * {@code -XX:TieredStopAtLevel=1 -XX:+UseSerialGC}, which is what every test here runs under. So
 * these are C1-only, serial-collector numbers: what they are good for is the increment between two
 * of them, and not for saying whether a millisecond figure fits an editor's budget.
 *
 * <p><b>What is measured, and against what.</b> Four numbers, all taken in one run on one workspace,
 * because a millisecond on its own says nothing and an increment between two says something. The
 * floor is what a keystroke already costs: the workspace's store catching up and the question being
 * put again. Beside it is the same keystroke on a line that stops at a {@code .}, which the workspace
 * cannot hold at all — that one goes through the probe's own store. Then the same with nothing kept
 * between rounds, which is what a store built per keystroke rather than kept would cost every time.
 * Then what the second store keeps alive, measured with it present and absent.
 *
 * <p><b>Why the workspace here is generated.</b> The figure in {@code Analyzer.compileOf} — 7.3 of
 * 9.3 milliseconds, on a crm workspace of seven sources and 3898 lines — was taken against sources
 * this repository does not hold in that shape, so it is a rough reference and not a baseline these
 * numbers continue. The corpus that is held, in {@code souther-bench}, cannot be reached from here:
 * {@code souther-bench} depends on {@code souther-lsp}, so the edge cannot run the other way. What is
 * generated instead states its own size, and every number below is compared with another taken in
 * the same run on the same sources.
 *
 * <p>Observed, three runs on 7 modules and 3261 lines, medians:
 * <pre>
 *   steady:    workspace  9.8 ms    probe 11.3 ms
 *   cold:      workspace 18.8 ms    probe 20.9 ms
 *   by turns:  workspace  9.1 ms    probe 11.2 ms
 *   retained:  workspace 14.3 MB    workspace and probe 17.2 MB
 * </pre>
 *
 * <p>A probe costs about a millisecond and a half a keystroke, and about three megabytes on a
 * workspace of this size. What each of the other two rows is there to refuse did not happen. The
 * workspace's own keystroke costs no more with a probe running beside it than without — 9.1 against
 * 9.8, which is the same number — so the probe takes nothing from the store it is beside; the two
 * stores are separate, and this is what says the separation holds in practice as well as on paper.
 * And the probe's store keeps what it answered: 11.3 against 20.9 from cold is the same 54% the
 * workspace's own store shows, 9.8 against 18.8. The ratio is the compiler's memoisation and not the
 * probe's, and the point is that the probe gets all of it.
 */
@Disabled("manual benchmark; run explicitly to re-measure — see class javadoc")
class SemanticProbeBenchmark {

    /** Rounds per timing, warmed for as long as it is measured. An edit reaches less code than a
     *  compile does and takes longer to settle. */
    private static final int ROUNDS = 40;

    /** A workspace of the order the reference figure was taken on: a handful of modules, most of a
     *  few thousand lines between them, all importing one that declares the data. */
    private static final int MODULES = 7;
    private static final int BEHAVIORS = 180;

    @Test
    void measureWhatAProbeAdds() {
        Map<String, String> byId = workspace();
        List<String> ids = List.copyOf(byId.keySet());
        String edited = ids.getLast();
        String module = moduleOf(edited);
        System.out.printf("workspace: %d modules, %d lines%n", byId.size(), lines(byId));

        Compilation workspace = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        resolve(workspace, module);

        double settled = medianMillis(round -> {
            Map<String, String> now = new LinkedHashMap<>(byId);
            now.put(edited, byId.get(edited) + finished(round));
            workspace.update(now, Set.of());
            resolve(workspace, module);
        }, ROUNDS);

        SemanticProbe kept = new SemanticProbe();
        double probed = medianMillis(round -> through(kept, byId, edited, module, round), ROUNDS);

        // A probe that keeps nothing between rounds, which is what one built per keystroke would be.
        double first = medianMillis(
                round -> through(new SemanticProbe(), byId, edited, module, round), ROUNDS / 4);

        // The workspace built from nothing, so that what a kept store is worth can be read off both
        // of them the same way. A ratio of steady state to cold that is the same for the two says the
        // probe's store is as incremental as the one it is beside — which is the question, the
        // absolute ratio being a property of the compiler's memoisation and not of the probe.
        double workspaceCold = medianMillis(round -> {
            Map<String, String> now = new LinkedHashMap<>(byId);
            now.put(edited, byId.get(edited) + finished(round));
            resolve(Compilation.ofDocuments(now, Set.of(), ModulePath.EMPTY), module);
        }, ROUNDS / 4);

        System.out.printf("steady: workspace %6.2f ms   probe %6.2f ms%n", settled, probed);
        System.out.printf("cold:   workspace %6.2f ms   probe %6.2f ms%n", workspaceCold, first);

        // And the two by turns, which is what an author does: type into the document, ask about the
        // line, type again. The workspace's store is what this is watching — a store fed the real
        // text and a repaired one alternately would re-answer the edited module every round, and
        // what would say so is this number standing above the one measured with nothing else running.
        List<Long> workspaceTurns = new ArrayList<>();
        List<Long> probeTurns = new ArrayList<>();
        SemanticProbe alongside = new SemanticProbe();
        for (int round = 0; round < ROUNDS * 2; round++) {
            Map<String, String> now = new LinkedHashMap<>(byId);
            now.put(edited, byId.get(edited) + finished(round));
            long opened = System.nanoTime();
            workspace.update(now, Set.of());
            resolve(workspace, module);
            long between = System.nanoTime();
            through(alongside, byId, edited, module, round);
            long closed = System.nanoTime();
            if (round >= ROUNDS) {
                workspaceTurns.add(between - opened);
                probeTurns.add(closed - between);
            }
        }
        System.out.printf("by turns: workspace %6.2f ms   probe %6.2f ms%n",
                median(workspaceTurns), median(probeTurns));

        long alone = retainedBytes(workspace);
        SemanticProbe beside = new SemanticProbe();
        through(beside, byId, edited, module, 0);
        long together = retainedBytes(workspace, beside);
        System.out.printf("retained: workspace %5.1f MB   workspace and probe %5.1f MB%n",
                megabytes(alone), megabytes(together));
    }

    /** One round of what an editor does: the buffer as it stands, finished off, compiled in the
     *  probe's own store, and the question put. */
    private static void through(SemanticProbe probe, Map<String, String> byId, String edited,
                                String module, int round) {
        Map<String, String> rest = new LinkedHashMap<>(byId);
        rest.remove(edited);
        String text = byId.get(edited) + halfWritten(round);
        SemanticProbe.Reading reading = probe.of(rest, Set.of(), ModulePath.EMPTY, edited, text,
                text.length(), Abandonment.NEVER);
        if (reading == null) {
            throw new IllegalStateException("the half-written line is one the probe finishes off");
        }
        resolve(reading.compilation(), module);
    }

    /** A finished behavior, so what it costs is the workspace answering and nothing else. */
    private static String finished(int round) {
        return "\nbehavior probed" + round + " : (d: D) -> Int\nlet probed" + round + " (d) = d.v\n";
    }

    /** The same behavior, stopped where a cursor is when a field list is wanted. */
    private static String halfWritten(int round) {
        return "\nbehavior probed" + round + " : (d: D) -> Int\nlet probed" + round + " (d) = d.";
    }

    private static Map<String, String> workspace() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("file:///m0.sou", "module m0 exposing ( D )\n\ndata D = { v: Int, w: Text }\n");
        for (int m = 1; m < MODULES; m++) {
            StringBuilder source = new StringBuilder("module m" + m + "\n\nimport m0 ( D )\n");
            for (int b = 0; b < BEHAVIORS; b++) {
                source.append("\nbehavior f").append(m).append('_').append(b)
                        .append(" : (d: D) -> Int\nlet f").append(m).append('_').append(b)
                        .append(" (d) = d.v\n");
            }
            byId.put("file:///m" + m + ".sou", source.toString());
        }
        return byId;
    }

    private static int lines(Map<String, String> byId) {
        int total = 0;
        for (String text : byId.values()) {
            total += (int) text.lines().count();
        }
        return total;
    }

    private static String moduleOf(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1).replace(".sou", "");
    }

    private static void resolve(Compilation compilation, String module) {
        Hir.Module answered = compilation.db().ask(new Names.Resolved(module)).value();
        if (answered == null) {
            throw new IllegalStateException("the module does not resolve: " + module);
        }
    }

    /** Warmed for as many rounds as it is measured, and the median of the measured ones. */
    private static double medianMillis(java.util.function.IntConsumer work, int rounds) {
        for (int i = 0; i < rounds; i++) {
            work.accept(i);
        }
        List<Long> nanos = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            long began = System.nanoTime();
            work.accept(rounds + i);
            nanos.add(System.nanoTime() - began);
        }
        return median(nanos);
    }

    private static double median(List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        sorted.sort(Long::compareTo);
        return sorted.get(sorted.size() / 2) / 1_000_000.0;
    }

    /**
     * What is still held once everything else has been let go.
     *
     * <p>Collected rather than counted. A query store's answers are ordinary objects reached from the
     * store, so what a second store keeps alive is a question about the heap; the stores are handed
     * in and touched at the end so that none of them may be collected while this runs.
     */
    private static long retainedBytes(Object... held) {
        for (int i = 0; i < 5; i++) {
            System.gc();
        }
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        for (Object each : held) {
            if (each == null) {
                throw new IllegalStateException("nothing was being held");
            }
        }
        return used;
    }

    private static double megabytes(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
}
