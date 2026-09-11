package souther.compiler.coverage;

import souther.compiler.WhereItSits;
import souther.compiler.observe.ArmObservation;
import souther.compiler.Emitted;
import org.junit.jupiter.api.Test;

import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.query.Output;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A comparison the plan numbers is one a run through it records, wherever it is written.
 *
 * <p>The numbering and the emission are one agreement, and this is the side of it a plan cannot
 * check. What a probe does is copy the value the comparison left on the stack, so a number handed to
 * a place that leaves something else there is a class the verifier refuses — which is what happened
 * the first time this numbering was widened without asking what the operator was.
 *
 * <p>Run rather than read, and over the shapes the numbering gained. A comparison given a name
 * before the fork that tests it, one inside a function value handed to a combinator, and one a
 * behavior answers with were numbered nowhere before, so nothing had ever asked the emitter to light
 * one — and a plan that names a site the emitter never lights is a hit no count can tell from a real
 * one.
 */
class AComparisonIsLitWhereverItIsWrittenTest {

    private static final String MODEL = """
            module example.written

            behavior fee : (a: Int, xs: List<Int>) -> Int
            behavior positive : (a: Int) -> Bool

            let fee (a, xs) = {
                let high = a > 10

                (if high then 1 else 0) + List.length(List.filter(x -> x > 0, xs))
            }

            let positive (a) = a > 0
            """;

    /**
     * A comparison inside a function value comes out both ways in one run.
     *
     * <p>What only a run says. That every site the plan named was written into the bytecode is the
     * emitter's own check and fires without anything being applied; this is the comparison being
     * passed once per element and answering each time, which is the whole of why such a place is
     * numbered and marked as one a run may come back to.
     *
     * <p>Held against a run that does not do it. Both ways out of one site in one run reads like a
     * fact about the numbering unless the same site under a list of one comes out one way — which
     * is what says the two records are two passes rather than two sites.
     */
    @Test
    void oneRunPassesTheComparisonInsideAFunctionValueOncePerElement() {
        Compilation compilation = compiled();
        CoverageSites.Plan plan = checkedPlanOf(compilation);
        ComparisonEmissionSite perElement = comparisonAt(plan, "fee", 9);
        Map<String, ClassFileImage> classes = probed(compilation);

        AlignedObservation mixed =
                new Behavior(classes, plan, "fee").observing(20L, List.of(1L, -1L));
        AlignedObservation positives =
                new Behavior(classes, plan, "fee").observing(20L, List.of(1L, 2L));

        assertEquals(List.of(true, false), waysOut(mixed, perElement),
                "one element over the line and one under it, at one comparison, in one run");
        assertEquals(List.of(true), waysOut(positives, perElement),
                "and two elements the same side of it answer one way");
    }

    /**
     * A comparison a name stands for is recorded at the name, not at the fork that tests the name.
     *
     * <p>The fork answers whichever arm the name's value sent it down, and that is a different
     * place: told from the arm, a row that never reached the comparison and one that reached it and
     * came out false are one observation.
     */
    @Test
    void aComparisonGivenANameIsRecordedWhereItIsWritten() {
        Compilation compilation = compiled();
        CoverageSites.Plan plan = checkedPlanOf(compilation);
        ComparisonEmissionSite named = comparisonAt(plan, "fee", 7);
        Map<String, ClassFileImage> classes = probed(compilation);

        assertEquals(List.of(true),
                waysOut(new Behavior(classes, plan, "fee").observing(20L, List.of(1L)), named));
        assertEquals(List.of(false),
                waysOut(new Behavior(classes, plan, "fee").observing(1L, List.of(1L)), named));
    }

    /** The same where the comparison is the whole of what the behavior answers. */
    @Test
    void aComparisonABehaviorAnswersWithIsRecordedToo() {
        Compilation compilation = compiled();
        CoverageSites.Plan plan = checkedPlanOf(compilation);
        ComparisonEmissionSite answered = comparisonAt(plan, "positive", 12);
        Map<String, ClassFileImage> classes = probed(compilation);

        assertEquals(List.of(true),
                waysOut(new Behavior(classes, plan, "positive").observing(5L), answered));
        assertEquals(List.of(false),
                waysOut(new Behavior(classes, plan, "positive").observing(-5L), answered));
    }

    /** The ways {@code seen} records out of {@code comparison}, held true first. */
    private static List<Boolean> waysOut(AlignedObservation seen,
                                         ComparisonEmissionSite comparison) {
        List<Boolean> out = new ArrayList<>();
        for (boolean held : new boolean[] {true, false}) {
            if (seen.saw(comparison, held)) {
                out.add(held);
            }
        }
        return out;
    }

    /** The comparison {@code behavior} writes on {@code line}, which is what a run is asked about. */
    private static ComparisonEmissionSite comparisonAt(CoverageSites.Plan plan, String behavior,
                                                     int line) {
        List<CoverageSites.ComparisonSite> found = plan.sites().stream()
                .filter(site -> site.behavior().equals(behavior))
                .filter(site -> site instanceof CoverageSites.ComparisonSite)
                .map(CoverageSites.ComparisonSite.class::cast)
                .filter(site -> site.at() instanceof souther.compiler.diag.Citation.Written written
                        && WhereItSits.in(MODEL, written.at()).line() == line)
                .toList();
        assertEquals(1, found.size(),
                () -> "one comparison of " + behavior + " on line " + line + ": " + plan.sites());
        return found.get(0).index();
    }

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

    /** One set of generated classes, loaded and applied. */
    private static final class Behavior {

        private final Object instance;
        private final Method apply;
        private final CoverageSites.Plan plan;

        Behavior(Map<String, ClassFileImage> classes, CoverageSites.Plan plan, String named) {
            assertNotNull(classes, "the model under test compiles");
            this.plan = plan;
            ClassLoader loader = new MemoryClassLoader(classes,
                    AComparisonIsLitWhereverItIsWrittenTest.class.getClassLoader());
            try {
                Class<?> impl = Emitted.behavior(loader, "example.written", named);
                Constructor<?> ctor = impl.getDeclaredConstructor();
                ctor.setAccessible(true);
                this.instance = ctor.newInstance();
                this.apply = java.util.Arrays.stream(impl.getDeclaredMethods())
                        .filter(each -> each.getName().equals("apply"))
                        .findFirst().orElseThrow();
                this.apply.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        AlignedObservation observing(Object... given) {
            // Under the numbering the classes were emitted with, and read back under the same one.
            // What the alignment is for is that those two are one, so a fixture that took them
            // from two places would be the thing this cannot check.
            Probe.begin(plan.identity());
            try {
                apply.invoke(instance, given);
                return plan.numbering().align(Probe.snapshot());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            } finally {
                Probe.end();
            }
        }
    }

}
