package souther.bench;

import souther.compiler.source.SourceId;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Front;
import souther.compiler.query.Names;
import souther.compiler.query.Output;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a compile's time goes, by asking the questions of one phase and then the next in a single
 * store and reporting the difference.
 *
 * <p>Nothing here runs a pipeline — the compiler has no phases to instrument. What it has is
 * questions, and asking a later one answers whatever earlier ones it needs. So a phase's figure is
 * what was left for it to do: ask for every module's names, and name resolution is charged for
 * parsing too; ask for the classes afterwards, and the classes cost only what names did not already
 * pay for. Reading the list top to bottom gives the whole compile, and no line double-counts
 * another.
 *
 * <p>The order is therefore the order of the compiler's own dependencies, and a line moving because
 * a later phase started asking an earlier question is a real finding, not an artefact.
 */
final class Phases {

    private Phases() {}

    private static final int WARMUP = 10;
    private static final int MEASURED = 20;

    static void measure(Report report, Corpus corpus) {
        report(report, corpus, timeWalks(corpus, WARMUP, MEASURED).figure());
    }

    /**
     * The phase figures, and what the walks they were taken over read.
     *
     * <p>The one way these are run, so that what is held to arriving is what is timed. The warm-up
     * is outside the reading for the reason it is outside the figures: a walk that only warmed the
     * JIT is not one anything is reported about.
     */
    static Taken<Map<String, Long>> timeWalks(Corpus corpus, int warmup, int measured) {
        // A phase list taken from a cold walk says the phases that ran first are the expensive
        // ones, which is the JIT's shape and not the compiler's.
        for (int i = 0; i < warmup; i++) {
            walk(corpus);
        }
        return Taken.of(() -> medians(corpus, measured));
    }

    /** A phase at a time, each the median of many walks. One walk puts a collection wherever it
     *  happens to fall and charges whichever phase was running, which moves a line by half its own
     *  size — enough to read a phase as having grown when nothing did. */
    private static Map<String, Long> medians(Corpus corpus, int measured) {
        Map<String, List<Long>> runs = new LinkedHashMap<>();
        for (int i = 0; i < measured; i++) {
            walk(corpus).forEach((phase, spent) ->
                    runs.computeIfAbsent(phase, _ -> new ArrayList<>()).add(spent));
        }
        Map<String, Long> median = new LinkedHashMap<>();
        runs.forEach((phase, spent) -> {
            List<Long> sorted = new ArrayList<>(spent);
            Collections.sort(sorted);
            median.put(phase, sorted.get(sorted.size() / 2));
        });
        return median;
    }

    private static void report(Report report, Corpus corpus, Map<String, Long> median) {
        long total = median.values().stream().mapToLong(Long::longValue).sum();
        report.line("PHASE %-14s total %7.1f ms (median of %d walks)", corpus.name(),
                total / 1000.0, MEASURED);
        median.forEach((phase, spent) -> report.line("        %-20s %7.2f ms  %5.1f%%",
                phase, spent / 1000.0, 100.0 * spent / total));
    }

    /** One walk of what the phase figures are the times of, which is what a reader asking what they
     *  cover runs. */
    static Map<String, Long> walk(Corpus corpus) {
        Compilation compilation = Compilation.ofSources(corpus.sources(), ModulePath.EMPTY);
        Map<String, Long> micros = new LinkedHashMap<>();
        long mark = System.nanoTime();

        List<SourceId> ids = compilation.sourceIds();
        for (SourceId id : ids) {
            compilation.db().ask(new Front.Parsed(id));
        }
        mark = charge(micros, "parse", mark);

        compilation.structuralReports();
        mark = charge(micros, "layout", mark);

        List<String> modules = new ArrayList<>(compilation.modules());
        for (String module : modules) {
            compilation.db().ask(new Names.Resolution(module));
        }
        mark = charge(micros, "resolve names", mark);

        for (String module : modules) {
            compilation.db().ask(new Bodies.Settled(module));
        }
        mark = charge(micros, "settle bodies", mark);

        for (String module : modules) {
            compilation.db().ask(new Bodies.Checked(module));
        }
        mark = charge(micros, "typecheck", mark);

        compilation.db().ask(new Output.All());
        mark = charge(micros, "codegen", mark);

        for (String module : modules) {
            compilation.db().ask(new Output.ConstConstructions(module));
        }
        mark = charge(micros, "const construction", mark);

        for (String module : modules) {
            for (SourceId id : compilation.exampleSourcesOf(module)) {
                compilation.db().ask(Output.Examples.asked(compilation.db(), module, id));
            }
        }
        charge(micros, "examples", mark);
        return micros;
    }

    private static long charge(Map<String, Long> micros, String phase, long from) {
        long now = System.nanoTime();
        micros.put(phase, (now - from) / 1000);
        return now;
    }
}
