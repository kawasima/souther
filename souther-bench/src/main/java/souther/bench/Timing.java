package souther.bench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A repeated measurement, reported as an order statistic rather than a mean.
 *
 * <p>A compile allocates enough to be interrupted by a collection, so the distribution has a tail
 * and a mean sits inside it. The median says what a run usually costs, the minimum says what it
 * costs when nothing interferes, and the difference between them is how much of the number is the
 * machine rather than the compiler.
 */
public record Timing(List<Long> micros) {

    /** Runs {@code work} to warm the JIT, then times it, discarding the warm-up. */
    public static Timing of(int warmup, int measured, Runnable work) {
        return of(warmup, measured, work, () -> {});
    }

    /**
     * The same, telling {@code measuring} when the runs a figure is taken over are about to start.
     *
     * <p>Here because this is where the warm-up ends and nowhere else knows. A caller asking what
     * the measured runs did — rather than what they cost — would otherwise have to count the rounds
     * again on its own, and would be asking about the runs it happened to count rather than the ones
     * the figure came from.
     */
    public static Timing of(int warmup, int measured, Runnable work, Runnable measuring) {
        for (int i = 0; i < warmup; i++) {
            work.run();
        }
        measuring.run();
        List<Long> micros = new ArrayList<>();
        for (int i = 0; i < measured; i++) {
            long start = System.nanoTime();
            work.run();
            micros.add((System.nanoTime() - start) / 1000);
        }
        return new Timing(micros);
    }

    /**
     * The same, where each run is handed its round number because it must differ from the one before
     * it — an edit is measured this way, since applying the same edit twice is not an edit and the
     * second one would measure a store that already holds the answer. Everything the run does is
     * timed; there is no set-up phase here to leave out.
     */
    public static Timing ofRounds(int warmup, int measured, Consumer<Integer> work) {
        return ofRounds(warmup, measured, work, () -> {});
    }

    /** The same, telling {@code measuring} when the rounds a figure is taken over begin. */
    public static Timing ofRounds(int warmup, int measured, Consumer<Integer> work,
                                  Runnable measuring) {
        List<Long> micros = new ArrayList<>();
        for (int i = 0; i < warmup + measured; i++) {
            if (i == warmup) {
                measuring.run();
            }
            long start = System.nanoTime();
            work.accept(i);
            long spent = (System.nanoTime() - start) / 1000;
            if (i >= warmup) {
                micros.add(spent);
            }
        }
        return new Timing(micros);
    }

    public double medianMillis() {
        return at(0.5);
    }

    public double minMillis() {
        return at(0.0);
    }

    public double p90Millis() {
        return at(0.9);
    }

    private double at(double quantile) {
        List<Long> sorted = new ArrayList<>(micros);
        Collections.sort(sorted);
        int index = Math.min(sorted.size() - 1, (int) (sorted.size() * quantile));
        return sorted.get(index) / 1000.0;
    }
}
