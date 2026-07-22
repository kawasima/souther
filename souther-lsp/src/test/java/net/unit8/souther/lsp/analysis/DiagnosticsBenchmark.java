package net.unit8.souther.lsp.analysis;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A manual latency benchmark for whole-workspace diagnostics — the measurement that decided against
 * module-level incremental recompilation (issue #38). Disabled in the normal suite; run explicitly to
 * re-measure: {@code mvn -pl souther-lsp test -Dtest=DiagnosticsBenchmark -DexcludedGroups=}.
 *
 * <p>Observed (JIT-warmed, whole-workspace parse + full compile of every module):
 * <pre>
 *   N=20    independent=14 ms   import-chain= 5 ms
 *   N=50    independent=10 ms   import-chain= 8 ms
 *   N=100   independent=11 ms   import-chain= 8 ms
 *   N=200   independent=23 ms   import-chain=21 ms
 * </pre>
 * 200 modules recompile in ~23 ms — well under an editor's latency budget — so the full-recompute is
 * not the bottleneck and an incremental engine would optimize a non-problem. The per-keystroke cost
 * that mattered was the disk walk + read, removed by {@link Workspace}'s scan cache.
 */
@Disabled("manual benchmark; run explicitly to re-measure — see class javadoc")
class DiagnosticsBenchmark {

    private final Analyzer analyzer = new Analyzer();

    @Test
    void measureFullWorkspaceDiagnostics() {
        for (int n : new int[]{20, 50, 100, 200}) {
            ModuleGraph independent = graphOf(independentModules(n));
            ModuleGraph chain = graphOf(chainModules(n));

            // warm up the JIT so the reported numbers are steady-state
            for (int i = 0; i < 3; i++) {
                analyzer.diagnostics(independent);
                analyzer.diagnostics(chain);
            }

            long indep = timeMillis(() -> analyzer.diagnostics(independent));
            long ch = timeMillis(() -> analyzer.diagnostics(chain));
            System.out.printf("N=%-4d  independent=%5d ms   import-chain=%5d ms%n", n, indep, ch);
        }
    }

    /** {@code n} self-contained modules, each a data + an identity behavior — no imports. */
    private static Map<String, String> independentModules(int n) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            sources.put("file:///m" + i + ".sou",
                    "module m" + i + "\n"
                    + "data D" + i + " = { v: Int }\n"
                    + "behavior f" + i + " : (d: D" + i + ") -> D" + i + "\n"
                    + "let f" + i + " (d) = d\n");
        }
        return sources;
    }

    /** {@code n} modules in an import chain: m{i} imports the data type of m{i-1}. */
    private static Map<String, String> chainModules(int n) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("file:///c0.sou", "module c0 exposing ( D0 )\ndata D0 = { v: Int }\n");
        for (int i = 1; i < n; i++) {
            sources.put("file:///c" + i + ".sou",
                    "module c" + i + " exposing ( D" + i + " )\n"
                    + "import c" + (i - 1) + " ( D" + (i - 1) + " )\n"
                    + "data D" + i + " = { v: Int }\n");
        }
        return sources;
    }

    private static ModuleGraph graphOf(Map<String, String> sources) {
        return ModuleGraph.of(sources);
    }

    private long timeMillis(Runnable r) {
        long start = System.nanoTime();
        r.run();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
