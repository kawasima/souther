package souther.bench;

import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.query.Compilation;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.GeneratedClasses;
import souther.runtime.Behavior;
import souther.runtime.PersistentVector;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the generated code costs to run, as against what it costs to produce.
 *
 * <p>The behaviors are driven through {@link Behavior#apply} on the class the backend emitted, with
 * the instance and the input built once outside the timing, so nothing here measures reflection or
 * decoding. Each number is per element, because that is the figure that says whether the shape of
 * the generated code is right — a per-call figure only says how big the input was.
 *
 * <p>Two of them are the same traversal over the same list and differ in what they leave behind:
 * {@code sumAll} accumulates a number, {@code evensDoubled} accumulates lists. The distance between
 * them is what building a list costs, which is the cost the standard library's {@code map} and
 * {@code filter} pay per element.
 */
final class Generated {

    private Generated() {}

    private static final int[] SIZES = {100, 10_000};

    /**
     * Where every result goes. A behavior is a pure function of its input, so a loop that throws its
     * results away is a loop the JIT is entitled to delete — and a deleted loop measures as free.
     * Keeping the last one is enough to stop that without adding work worth measuring.
     */
    @SuppressWarnings("unused")
    private static volatile Object sink;

    static void measure(Report report) {
        Corpus corpus = Corpus.load("runtime");
        Compilation compilation = corpus.compile();
        corpus.check(compilation);
        Map<String, ClassFileImage> classes = compilation.classes();
        ClassLoader loader = new MemoryClassLoader(classes, Generated.class.getClassLoader());

        Behavior<Object, Object> sumAll = behavior(loader, "SumAll");
        Behavior<Object, Object> evensDoubled = behavior(loader, "EvensDoubled");
        Behavior<Object, Object> tally = behavior(loader, "Tally");
        Behavior<Object, Object> flipped = behavior(loader, "Flipped");
        Behavior<Object, Object> kept = behavior(loader, "Kept");
        Behavior<Object, Object> held = behavior(loader, "Held");
        Behavior<Object, Object> keyed = behavior(loader, "Keyed");
        Behavior<Object, Object> modded = behavior(loader, "Modded");
        Behavior<Object, Object> parted = behavior(loader, "Parted");
        Behavior<Object, Object> priced = behavior(loader, "Priced");
        Behavior<Object, Object> amountOf = behavior(loader, "AmountOf");

        for (int elements : SIZES) {
            PersistentVector<Long> input = counting(elements);
            perElement(report, "sumAll", elements, sumAll, input);
            perElement(report, "evensDoubled", elements, evensDoubled, input);
            perElement(report, "tally", elements, tally, input);
            perElement(report, "flipped", elements, flipped, input);
            perElement(report, "kept", elements, kept, input);
            perElement(report, "held", elements, held, input);
            perElement(report, "keyed", elements, keyed, input);
            perElement(report, "modded", elements, modded, input);
            perElement(report, "parted", elements, parted, input);
            perElement(report, "priced", elements, priced, decimals(elements));
        }
        perElement(report, "amountOf", 1, amountOf, 42L);
    }

    private static void perElement(Report report, String name, int elements,
                                   Behavior<Object, Object> behavior, Object input) {
        // Driven hard before anything is timed: the body of a behavior is small enough that an
        // interpreted run is an order of magnitude out, and what is being compared here is bytecode
        // the JIT has seen.
        for (int i = 0; i < 20_000; i++) {
            sink = behavior.apply(input);
        }
        int repetitions = Math.max(200, 2_000_000 / elements);
        Timing timing = Timing.of(2, 7, () -> {
            for (int i = 0; i < repetitions; i++) {
                sink = behavior.apply(input);
            }
        });
        double perCall = timing.medianMillis() * 1_000_000.0 / repetitions;
        report.line("RUN   %-14s n=%-6d %9.1f ns/call   %7.2f ns/element",
                name, elements, perCall, perCall / elements);
    }

    /** {@code 0..elements}, as the list a behavior taking {@code List<Int>} is applied to. */
    private static PersistentVector<Long> counting(int elements) {
        PersistentVector<Long> input = PersistentVector.empty();
        for (long i = 0; i < elements; i++) {
            input = input.append(i);
        }
        return input;
    }

    /** The same numbers as Decimals, for a behavior taking {@code List<Decimal>}: built outside
     *  the timing so that no conversion is in the loop being measured. */
    private static PersistentVector<BigDecimal> decimals(int elements) {
        PersistentVector<BigDecimal> input = PersistentVector.empty();
        for (long i = 0; i < elements; i++) {
            input = input.append(BigDecimal.valueOf(i));
        }
        return input;
    }

    /**
     * A behavior of the corpus, ready to apply. The public name is an interface and the body is on
     * its {@code $Impl}, whose constructor is not public — a behavior a module does not expose is
     * reached the same way, so the constructor is opened rather than the name looked up twice.
     */
    @SuppressWarnings("unchecked")
    private static Behavior<Object, Object> behavior(ClassLoader loader, String name) {
        try {
            Class<?> emitted = GeneratedClasses.load(loader,
                    new GeneratedClass.BehaviorImpl("bench.runtime", name));
            Constructor<?> ctor = emitted.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (Behavior<Object, Object>) ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the runtime corpus does not emit `" + name + "`", e);
        }
    }

    /** The list operations both build with, measured on their own so the figure above can be read
     * against what the structure itself costs. */
    static void structure(Report report) {
        int elements = 10_000;
        List<Long> source = new java.util.ArrayList<>();
        for (long i = 0; i < elements; i++) {
            source.add(i);
        }
        // Many builds inside one timed sample, as above: a single build of ten thousand elements is
        // short enough that a collection landing in one sample moves the median of seven.
        int repetitions = 500;
        Timing appending = Timing.of(4, 7, () -> {
            for (int r = 0; r < repetitions; r++) {
                PersistentVector<Long> v = PersistentVector.empty();
                for (long i = 0; i < elements; i++) {
                    v = v.append(i);
                }
                sink = v;
            }
        });
        Timing building = Timing.of(4, 7, () -> {
            for (int r = 0; r < repetitions; r++) {
                sink = PersistentVector.from(source);
            }
        });
        double scale = 1_000_000.0 / (repetitions * (double) elements);
        report.line("RUN   %-14s %7.2f ns/element   (what map and filter build with)",
                "append", appending.medianMillis() * scale);
        report.line("RUN   %-14s %7.2f ns/element   (the builder, for comparison)",
                "from", building.medianMillis() * scale);

        // What `tally` is doing, written by hand against the JDK's own hash map: the same sixteen
        // keys, the same count per key, the same boxing of both. It says what the figure above is to
        // be read against — a Souther map is persistent and a HashMap is not, so this is a floor
        // rather than a rival.
        Timing counting = Timing.of(4, 7, () -> {
            for (int r = 0; r < repetitions; r++) {
                java.util.Map<Long, Long> counts = new java.util.HashMap<>();
                for (long i = 0; i < elements; i++) {
                    counts.merge(i % 16, 1L, Long::sum);
                }
                sink = counts;
            }
        });
        report.line("RUN   %-14s %7.2f ns/element   (the same counting, on a java.util.HashMap)",
                "merge", counting.medianMillis() * scale);

        // What `priced` is doing, written by hand against BigDecimal: the same product and sum per
        // element, the rate held once. What the figure above is to be read against — the distance
        // between the two is what the run time puts around a Decimal operation, since the
        // arithmetic underneath is the same.
        List<BigDecimal> decimals = new ArrayList<>();
        for (long i = 0; i < elements; i++) {
            decimals.add(BigDecimal.valueOf(i));
        }
        BigDecimal rate = new BigDecimal("1.1");
        Timing pricing = Timing.of(4, 7, () -> {
            for (int r = 0; r < repetitions; r++) {
                BigDecimal acc = BigDecimal.ZERO;
                for (BigDecimal x : decimals) {
                    acc = acc.add(x.multiply(rate));
                }
                sink = acc;
            }
        });
        report.line("RUN   %-14s %7.2f ns/element   (the same pricing, on java.math.BigDecimal)",
                "pricing", pricing.medianMillis() * scale);
    }

}
