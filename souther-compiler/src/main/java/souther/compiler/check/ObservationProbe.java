package souther.compiler.check;

import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A probe, and nothing this repository keeps.
 *
 * <p>It puts what a count observes by standing in the way of the clause lookup beside what the
 * readings that count reached already record, and writes down where the two disagree. It goes when
 * the wrapper does.
 */
final class ObservationProbe {

    private ObservationProbe() {}

    private static final Path OUT = Path.of(out());

    private static String out() {
        String said = System.getProperty("souther.probe.out", System.getenv("SOUTHER_PROBE_OUT"));
        return said == null ? "/tmp/souther-1554-probe.log" : said;
    }

    private static final AtomicLong RUNS = new AtomicLong();
    private static final AtomicLong MISMATCHES = new AtomicLong();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                write("SUMMARY pid=" + ProcessHandle.current().pid()
                        + " runs=" + RUNS.get() + " mismatches=" + MISMATCHES.get())));
    }

    /** One run of the count, and what each of the two sides saw during it. */
    static final class Run {

        private final Run outer;
        /** The declarations whose clauses the wrapped lookup was told it could not have. */
        private final Set<TypeKey> unavailable = new LinkedHashSet<>();
        /** Every reading the run reached, and whether it records a clause that never expanded. */
        private final Map<TypeSymbol.AtModule, Boolean> readings = new LinkedHashMap<>();

        private Run(Run outer) {
            this.outer = outer;
        }
    }

    private static final ThreadLocal<Run> RUNNING = new ThreadLocal<>();

    static Run begin() {
        Run run = new Run(RUNNING.get());
        RUNNING.set(run);
        return run;
    }

    static void end(Run run) {
        RUNNING.set(run.outer);
    }

    /** Said where the wrapped lookup sees a declaration's clauses could not be worked out. */
    static void sawUnavailable(TypeKey declaration) {
        for (Run run = RUNNING.get(); run != null; run = run.outer) {
            run.unavailable.add(declaration);
        }
    }

    /** Said wherever a reading is handed to anybody, whether it was made now or lent. */
    static void sawReading(TypeSymbol.AtModule named, InvariantChecker.Seeded seeded) {
        if (RUNNING.get() == null) {
            return;
        }
        boolean missed = seeded.notGathered().values().stream()
                .flatMap(Set::stream)
                .anyMatch(why -> why instanceof RulesMissed.ClausesNotExpanded);
        for (Run run = RUNNING.get(); run != null; run = run.outer) {
            run.readings.merge(named, missed, (had, now) -> had || now);
        }
    }

    /**
     * Puts the two answers together for one run.
     *
     * @param observed what the wrapper says: every rule the count asked for could be given
     */
    static void compare(Run run, boolean observed) {
        RUNS.incrementAndGet();
        boolean derived = run.readings.values().stream().noneMatch(Boolean::booleanValue);
        Set<TypeSymbol.AtModule> flagged = new LinkedHashSet<>();
        run.readings.forEach((named, missed) -> {
            if (missed) {
                flagged.add(named);
            }
        });
        if (derived != observed) {
            MISMATCHES.incrementAndGet();
        } else if (observed && run.unavailable.isEmpty()) {
            // A run where neither side saw anything. Written down as a number and not as a line.
            return;
        }
        write((derived == observed ? "RUN" : "MISMATCH")
                + " pid=" + ProcessHandle.current().pid()
                + " observed=" + observed + " derived=" + derived
                + " unavailable=" + run.unavailable
                + " readingsFlagged=" + flagged
                + " readingsReached=" + run.readings.size());
    }

    private static synchronized void write(String line) {
        try {
            Files.writeString(OUT, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
