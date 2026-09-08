package souther.compiler.coverage;

import souther.compiler.observe.ArmObservation;
import souther.compiler.Emitted;
import org.junit.jupiter.api.Test;

import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.query.Output;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Classes generated to record where a run went, and classes generated to ship.
 *
 * <p>Two claims are being held to, and they pull against each other. The measured classes have to
 * record the arms a run actually takes, which is asked by running them and reading what comes back.
 * And the shipped classes have to have no idea any of this exists: a jar that mentioned {@code Probe}
 * would resolve it nowhere the compiler is absent, which is every machine a Souther program runs on.
 */
class ProbedBytecodeTest {

    /** Four arms: two guards, each with a departure. Inputs are bare numbers so that driving the
     * generated class needs no value built by hand — what is under test is the arms, not the decoder. */
    private static final String MODEL = """
            module example.trip

            data Submitted = { cost: Int }
            data Waiting = { cost: Int }

            behavior submit : (cost: Int) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (cost) = {
                guard cost >= 0 else Waiting { cost = 0 }
                guard cost <= 100 else Waiting { cost = cost }
                Submitted { cost = cost }
            }
            """;

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        return compilation;
    }

    /** The numbering of the bodies the classes below were generated from, asked of the check the
     *  emitter reads too. Named apart from the plans this package's other tests build straight from
     *  bodies, which have no compile behind them at all. */
    private static CoverageSites.Plan checkedPlanOf(Compilation compilation) {
        Bodies.Elaborated checked = compilation.db()
                .ask(new Bodies.Checked(compilation.modules().get(0))).value();
        assertNotNull(checked, "the model under test compiles");
        return checked.plan();
    }

    private static Map<String, ClassFileImage> probed(Compilation compilation) {
        souther.compiler.generated.EvaluationArtifact artifact = compilation.db()
                .ask(new Output.Evaluated(compilation.modules().get(0),
                        ArmObservation.RECORD)).value();
        assertNotNull(artifact, "the model under test compiles");
        return artifact.classes();
    }

    /** The classes a class names — what a loader will go looking for when it loads this one. */
    private static Set<String> referencedClasses(byte[] bytes) {
        Set<String> names = new LinkedHashSet<>();
        for (PoolEntry entry : ClassFile.of().parse(bytes).constantPool()) {
            if (entry instanceof ClassEntry cls) {
                names.add(cls.asInternalName());
            }
        }
        return names;
    }

    private static final String PROBE = "souther/compiler/coverage/Probe";

    // --- what the measured classes record --------------------------------------------------------

    /**
     * The arms a run takes, read off a run.
     *
     * <p>Three inputs, each going a different way through the two guards. Each has to come back with a
     * different set, and between them they have to reach every site the plan numbered — the arms and
     * the comparisons of the guards' conditions — which is what makes this about what was recorded
     * and not about three calls that happened to return.
     */
    @Test
    void aRunRecordsTheArmsItTook() {
        Compilation compilation = compiled();
        CoverageSites.Plan plan = checkedPlanOf(compilation);
        Behavior submit = new Behavior(probed(compilation), plan.identity());

        Set<Integer> negative = submit.armsFor(-1L);
        Set<Integer> cheap = submit.armsFor(50L);
        Set<Integer> dear = submit.armsFor(500L);

        assertNotEquals(negative, cheap);
        assertNotEquals(cheap, dear);
        assertNotEquals(negative, dear);
        Set<Integer> between = new LinkedHashSet<>(negative);
        between.addAll(cheap);
        between.addAll(dear);
        // Both families, because the sites are both and a run records each in its own. Counted
        // over the arms alone this would be short by every comparison the plan numbered, and
        // counted over one set holding both it would be the number this whole numbering exists to
        // stop a reader working out for itself.
        for (long cost : new long[] {-1L, 50L, 500L}) {
            submit.comparisonsFor(cost).forEach(way -> between.add(way.at()));
        }
        assertEquals(plan.sites().size(), between.size(),
                "between them the three rows reach every site the plan numbered");
    }

    /** A hit belongs to the thread that made it. Rows are evaluated on their own workers, and a set
     * shared between them would put every row's arms on every row. */
    @Test
    void oneThreadsArmsAreNotAnothers() throws Exception {
        Compilation compilation = compiled();
        NumberingIdentity under = checkedPlanOf(compilation).identity();
        Behavior submit = new Behavior(probed(compilation), under);
        ExecutorService elsewhere = Executors.newSingleThreadExecutor();
        try {
            Probe.begin(under);
            submit.apply(50L);
            Set<Integer> there = elsewhere.submit(() -> submit.armsFor(500L)).get();
            Set<Integer> here = Probe.snapshot().arms();
            Probe.end();

            assertNotEquals(here, there);
            assertFalse(here.containsAll(there), "what the other thread took did not land here");
        } finally {
            elsewhere.shutdownNow();
        }
    }

    /**
     * A run nobody is measuring lands nowhere, and there is no account of it to be had.
     *
     * <p>Both halves. The hits go nowhere rather than into whatever collected last, which is what
     * the next row on this thread would otherwise start inside of; and asking what such a run did
     * is refused rather than answered with an empty account. A number means a place under the
     * numbering that handed it out, and a thread nothing began has none to name — so a snapshot
     * there could only be a run under a numbering of nothing, which reads everywhere downstream as
     * a row shown to have passed nowhere.
     */
    @Test
    void aRunNobodyIsMeasuringLeavesNoAccountAtAll() {
        Compilation compilation = compiled();
        NumberingIdentity under = checkedPlanOf(compilation).identity();
        Behavior submit = new Behavior(probed(compilation), under);

        submit.apply(50L);   // outside begin()/end()

        assertThrows(IllegalStateException.class, Probe::snapshot,
                "a row nobody watched has no account, which is not an account of nothing");
        // And the hits landed nowhere: a recording begun now is a recording of what happens now.
        Probe.begin(under);
        try {
            assertEquals(Set.of(), Probe.snapshot().arms());
        } finally {
            Probe.end();
        }
    }

    // --- what the shipped classes do not mention -------------------------------------------------

    /**
     * A shipped class does not refer to the probe.
     *
     * <p>Not a matter of taste. {@code Probe} is the compiler's own class, and a jar that named it
     * would fail to resolve it anywhere the compiler is not on the path. Asked of the constant pool,
     * because that is the list a loader works through.
     */
    @Test
    void nothingThatShipsMentionsTheProbe() {
        Map<String, ClassFileImage> shipped = compiled().db().ask(new Output.All()).value();
        assertNotNull(shipped);
        assertFalse(shipped.isEmpty());

        for (Map.Entry<String, ClassFileImage> each : shipped.entrySet()) {
            assertFalse(referencedClasses(each.getValue().bytes()).contains(PROBE),
                    each.getKey() + " refers to the probe");
        }
    }

    /** And that check is not vacuous: the measured classes do name it. */
    @Test
    void theMeasuredClassesDoMentionIt() {
        Map<String, ClassFileImage> classes = probed(compiled());

        assertTrue(classes.values().stream()
                        .anyMatch(image -> referencedClasses(image.bytes()).contains(PROBE)),
                "something in there records where a run went");
    }

    /**
     * Measuring does not change what the program answers.
     *
     * <p>A measurement whose subject behaves differently under it is measuring something else. The
     * probe takes an {@code int} and returns nothing, so this ought to hold by construction — which is
     * a reason to check it rather than a reason not to.
     */
    @Test
    void aProbedClassAnswersWhatThePlainOneDoes() {
        Compilation compilation = compiled();
        NumberingIdentity under = checkedPlanOf(compilation).identity();
        Behavior measured = new Behavior(probed(compilation), under);
        Behavior plain = new Behavior(compilation.db()
                .ask(new Output.Linked(compilation.modules().get(0))).value(), under);

        for (long cost : new long[] {-1L, 0L, 50L, 100L, 101L, 500L}) {
            assertEquals(String.valueOf(plain.apply(cost)), String.valueOf(measured.apply(cost)),
                    "at cost " + cost);
        }
    }

    /**
     * What a probed evaluation loads: this module measured, everything else as it is.
     *
     * <p>An import's arms belong to its own module and are numbered against its own plan. Measuring
     * them here would put hits into a run whose report has no plan to read them by, so the linked set
     * replaces this module's classes and leaves the rest alone.
     *
     * <p>Every class that ships and can be run, and the one that cannot. A module's declarations are
     * written onto a class of their own for an importer to read them off, and nothing loads it to
     * run it, so what an evaluation is handed is what ships less that one.
     */
    @Test
    void onlyThisModulesClassesAreTheMeasuredOnes() {
        Compilation compilation = compiled();
        String module = compilation.modules().get(0);
        Map<String, ClassFileImage> plain = compilation.db().ask(new Output.Linked(module)).value();
        Map<String, ClassFileImage> measured = compilation.db()
                .ask(new Output.Evaluated(module, ArmObservation.RECORD)).value().classes();
        Map<String, ClassFileImage> linked = compilation.db()
                .ask(new Output.EvaluationLinked(module, ArmObservation.RECORD)).value().classes();
        assertNotNull(plain);
        assertNotNull(linked);

        Set<String> loadable = new LinkedHashSet<>(plain.keySet());
        assertTrue(loadable.remove(Emitted.declarations(module)),
                "what ships carries the class an importer reads the declarations off");
        assertEquals(loadable, linked.keySet(), "the same classes are loadable");
        for (Map.Entry<String, ClassFileImage> each : linked.entrySet()) {
            ClassFileImage want = measured.containsKey(each.getKey())
                    ? measured.get(each.getKey()) : plain.get(each.getKey());
            assertEquals(referencedClasses(want.bytes()),
                    referencedClasses(each.getValue().bytes()), each.getKey());
        }
        assertTrue(measured.keySet().stream().anyMatch(name ->
                        referencedClasses(linked.get(name).bytes()).contains(PROBE)),
                "this module's own classes are the measured ones");
    }

    // --- driving the generated classes -----------------------------------------------------------

    /** One set of generated classes, loaded and applied. */
    private static final class Behavior {

        private final Object instance;
        private final Method apply;
        private final NumberingIdentity under;

        Behavior(Map<String, ClassFileImage> classes, NumberingIdentity under) {
            this.under = under;
            assertNotNull(classes, "the model under test compiles");
            ClassLoader loader = new MemoryClassLoader(classes,
                    ProbedBytecodeTest.class.getClassLoader());
            try {
                // The public name is an interface; the constructor and the erased apply are on $Impl.
                Class<?> impl = Emitted.behavior(loader, "example.trip", "submit");
                Constructor<?> ctor = impl.getDeclaredConstructor();
                ctor.setAccessible(true);
                this.instance = ctor.newInstance();
                this.apply = impl.getDeclaredMethod("apply", Object.class);
                this.apply.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        Object apply(long cost) {
            try {
                return apply.invoke(instance, cost);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        /** The ways out of the comparisons this run evaluated. */
        Set<ComparisonOutcome> comparisonsFor(long cost) {
            Probe.begin(under);
            try {
                apply(cost);
                return Probe.snapshot().comparisons();
            } finally {
                Probe.end();
            }
        }

        Set<Integer> armsFor(long cost) {
            Probe.begin(under);
            try {
                apply(cost);
                return Probe.snapshot().arms();
            } finally {
                Probe.end();
            }
        }
    }
}
