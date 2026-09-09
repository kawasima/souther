package souther.bench;

import souther.compiler.query.Compilation;

/**
 * What one compile costs, measured twice because the two numbers answer to different people.
 *
 * <p>Cold is what a build pays: a JVM that has never compiled anything, running the compiler
 * interpreted until the JIT catches up. It is the only compile most invocations of the command line
 * ever do, and it is several times the steady-state figure. It can be taken once per process, so a
 * run reports it for the first corpus only.
 *
 * <p>Warm is what the compiler itself costs once that is paid — the number to watch when a change is
 * meant to make the work smaller rather than the start-up cheaper. It is what a language server
 * spends, and what a build spends per module after the first.
 */
final class WholeCompile {

    private WholeCompile() {}

    /**
     * The first compile of {@code corpus} in this process, timed, and returned so the caller can
     * check it. Reporting is the caller's: the number is taken whether or not it was asked for,
     * because it can only be taken once and something has to compile the corpus to check it.
     */
    static Cold cold(Corpus corpus) {
        long start = System.nanoTime();
        Compilation compilation = corpus.compile();
        int classes = compilation.classes().size();
        return new Cold(compilation, classes, (System.nanoTime() - start) / 1_000_000.0);
    }

    record Cold(Compilation compilation, int classes, double millis) {}

    static void report(Report report, Corpus corpus, Cold cold) {
        report.line("COLD  %-14s %d files %5d lines  %4d classes  %8.1f ms",
                corpus.name(), corpus.sources().size(), corpus.lines(), cold.classes(),
                cold.millis());
    }

    /**
     * One round of what the warm figure is the time of.
     *
     * <p>Named rather than written into the timing call, so that a reader asking what the figure
     * covers runs the same thing the figure is the time of. A test that compiled the corpus its own
     * way would be answering about its own compile.
     */
    static void warmRound(Corpus corpus) {
        corpus.compile();
    }

    static void warm(Report report, Corpus corpus) {
        Timing timing = Timing.of(10, 20, () -> warmRound(corpus));
        report.line("WARM  %-14s %d files %5d lines  median %7.1f ms  min %7.1f ms  p90 %7.1f ms",
                corpus.name(), corpus.sources().size(), corpus.lines(),
                timing.medianMillis(), timing.minMillis(), timing.p90Millis());
    }
}
