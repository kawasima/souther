package souther.lsp.analysis;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import souther.compiler.Compiler;
import souther.compiler.diag.CompileException;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.source.SourceId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a code action request costs, by which compilation it asks.
 *
 * <p>To re-measure, take the {@code @Disabled} off and run
 * {@code mvn -o test -Dtest=WhatACodeActionCostsBenchmark -Dsurefire.failIfNoSpecifiedTests=false}.
 * The run's JVM flags are the reactor's — {@code -XX:TieredStopAtLevel=1 -XX:+UseSerialGC} — so
 * these are C1-only, serial-collector numbers, good for the increment between two of them and not
 * for saying whether a figure fits an editor's budget.
 *
 * <p><b>What is measured.</b> Three ways of getting the compiler's did-you-mean to a quick fix, on
 * one workspace in one run. {@code alone} is a compile of the one document, started from nothing,
 * which is what the offer used to do on every request. {@code diagnostics} and {@code repairs} both
 * ask the workspace's own compilation — the one a diagnose keeps up to date — and differ in how much
 * of it they ask for: every marker for every file, against the edits that land in one file. The two
 * are taken by turns in both orders, so a drift over the run cannot land on one of them.
 *
 * <p>Four states, because what a request costs depends on what has happened since the last one:
 * settled on the revision the last diagnose answered, after an edit to the file being asked about,
 * after an edit to another file, and from nothing.
 *
 * <p>{@code alone} is measured on a self-contained document because that is the only kind it ever
 * ran on: given a document with an import it returned before compiling, and the offer was not made
 * at all. So its column is what the old path cost where it worked, and the row it does not have is
 * every workspace of more than one module.
 *
 * <p>Observed, on the workspace this generates (7 modules, 3264 lines), medians:
 * <pre>
 *   settled:   diagnostics    0.03 ms   repairs    0.03 ms
 *   asked:     diagnostics   54.53 ms   repairs   54.58 ms
 *   elsewhere: diagnostics   52.70 ms   repairs   53.94 ms
 *   cold:      diagnostics  882.63 ms   repairs  869.07 ms
 *   alone (one self-contained document, a third the size):  145.24 ms
 * </pre>
 *
 * <p>Settled is the state a cursor move arrives in, and it is where the old path spent its whole
 * cost: a compile of the document from nothing, every request, against a store that already held
 * the answer. The other rows are an edit being paid for once — by whichever of the diagnose and the
 * request reaches the store first, since what either of them answers is kept.
 *
 * <p>The two queries do not differ. Asking for every marker in the workspace costs what asking for
 * one file's edits costs, because both are {@code answerEverything} and the publication map beside
 * it is nothing. So the narrower query is not here for its cost: it is here because publication and
 * applicability are different questions, and a query that answered one while being read for the
 * other would be right only while every finding stayed in the file it was published in.
 */
@Disabled("manual benchmark; run explicitly to re-measure — see class javadoc")
class WhatACodeActionCostsBenchmark {

    private static final int ROUNDS = 40;
    private static final int MODULES = 7;
    private static final int BEHAVIORS = 180;

    /** The document the request is about, and the one the misspelling is in. */
    private static final String ASKED = "file:///m" + (MODULES - 1) + ".sou";

    @Test
    void measureWhatEachWayOfAskingCosts() {
        Map<String, String> byId = workspace();
        System.out.printf("workspace: %d modules, %d lines%n", byId.size(), lines(byId));

        Compilation compilation = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        SourceId asked = new SourceId(ASKED);
        if (compilation.repairs(asked).isEmpty()) {
            throw new IllegalStateException("the corpus is meant to hold a misspelling to repair");
        }

        System.out.printf("settled:  %s%n", byTurns(compilation, asked, byId, Edit.NONE));
        System.out.printf("asked:    %s%n", byTurns(compilation, asked, byId, Edit.THE_FILE_ASKED));
        System.out.printf("elsewhere:%s%n", byTurns(compilation, asked, byId, Edit.ANOTHER_FILE));
        System.out.printf("cold:      diagnostics %6.2f ms   repairs %6.2f ms%n",
                medianMillis(round -> fresh(byId).diagnostics(), ROUNDS / 4),
                medianMillis(round -> fresh(byId).repairs(asked), ROUNDS / 4));

        String own = selfContained();
        System.out.printf("alone (one self-contained document): %6.2f ms%n",
                medianMillis(round -> compileAlone(own), ROUNDS / 4));
    }

    /** What has happened to the documents since the last request. */
    private enum Edit { NONE, THE_FILE_ASKED, ANOTHER_FILE }

    /**
     * The two queries alternated, and in both orders, so that whichever runs first in a round is not
     * what the difference between them measures.
     */
    private static String byTurns(Compilation compilation, SourceId asked,
                                  Map<String, String> byId, Edit edit) {
        List<Long> diagnosing = new ArrayList<>();
        List<Long> repairing = new ArrayList<>();
        for (int round = 0; round < ROUNDS * 2; round++) {
            boolean repairsFirst = round % 2 == 0;
            apply(compilation, byId, edit, round);
            long opened = System.nanoTime();
            if (repairsFirst) {
                compilation.repairs(asked);
            } else {
                compilation.diagnostics();
            }
            long between = System.nanoTime();
            if (repairsFirst) {
                compilation.diagnostics();
            } else {
                compilation.repairs(asked);
            }
            long closed = System.nanoTime();
            if (round >= ROUNDS) {
                repairing.add(repairsFirst ? between - opened : closed - between);
                diagnosing.add(repairsFirst ? closed - between : between - opened);
            }
        }
        return String.format(" diagnostics %6.2f ms   repairs %6.2f ms",
                median(diagnosing), median(repairing));
    }

    private static void apply(Compilation compilation, Map<String, String> byId, Edit edit,
                              int round) {
        if (edit == Edit.NONE) {
            return;
        }
        String touched = edit == Edit.THE_FILE_ASKED ? ASKED : "file:///m1.sou";
        Map<String, String> now = new LinkedHashMap<>(byId);
        now.put(touched, byId.get(touched) + added(round));
        compilation.update(now, Set.of());
    }

    private static String added(int round) {
        return "\nbehavior added" + round + " : (d: D) -> Int\nlet added" + round + " (d) = d.v\n";
    }

    private static Compilation fresh(Map<String, String> byId) {
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
    }

    /** What the offer used to do: a compile of the one document, from nothing, thrown away. */
    private static void compileAlone(String text) {
        try {
            Compiler.compile(text, "Main");
        } catch (CompileException _) {
            // the misspelling, which is what it was compiling to find
        }
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
            if (m == MODULES - 1) {
                // the misspelling the request is about: `writen` for the parameter `written`
                source.append("\nbehavior spelt : (written: D) -> Int\n")
                        .append("let spelt (written) = writen.v\n");
            }
            byId.put("file:///m" + m + ".sou", source.toString());
        }
        return byId;
    }

    /** One document that resolves on its own, which is the only kind the old path compiled. */
    private static String selfContained() {
        StringBuilder source = new StringBuilder(
                "module Main\n\ndata D = { v: Int, w: Text }\n");
        for (int b = 0; b < BEHAVIORS; b++) {
            source.append("\nbehavior g").append(b)
                    .append(" : (d: D) -> Int\nlet g").append(b).append(" (d) = d.v\n");
        }
        source.append("\nbehavior spelt : (written: D) -> Int\nlet spelt (written) = writen.v\n");
        return source.toString();
    }

    private static int lines(Map<String, String> byId) {
        int total = 0;
        for (String text : byId.values()) {
            total += (int) text.lines().count();
        }
        return total;
    }

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
}
