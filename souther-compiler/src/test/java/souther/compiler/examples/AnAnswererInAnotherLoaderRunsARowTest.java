package souther.compiler.examples;

import souther.compiler.check.CheckedEnsures;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.execute.jvm.JvmDeadlines;
import souther.compiler.observe.Observations;
import souther.compiler.observe.ArmObservation;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.GeneratedClasses;
import souther.compiler.observe.Applied;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Stage;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.query.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row is decided the same way when what applied the behavior is not in the loader the row's values
 * were built in.
 *
 * <p>This is what the seam is for, and the only thing that establishes it is running one. The answerer
 * here defines the same classes a second time and applies those: its {@code 金額} and this run's
 * {@code 金額} are different types under one binary name, which is exactly the position an
 * implementation compiled against a published module is in. Nothing may be shared between them, so
 * every step of the crossing is exercised — the argument goes over as the form a derived decoder reads
 * ({@link Handed#neutral}) and is decoded by the other loader's own decoder, and the answer comes back
 * as one of the other loader's values and is read by the one reading a run has.
 *
 * <p>Written with the same compile's bytecode rather than a second compilation because what is being
 * held is the crossing and not the two compiles agreeing. Same bytes, two loaders, and the JVM makes
 * them two type universes on its own.
 *
 * <p>An answerer that read a value by its class identity, or one handed the values as this compile
 * built them, fails here and passes everything else.
 */
class AnAnswererInAnotherLoaderRunsARowTest {

    private static final String MODEL = """
            module example.crossing

            data 金額 = Int
                invariant value >= 0

            data 倍額 = { もと: 金額, 倍: 金額 }

            behavior 倍にする : (額: 金額) -> 倍額
                constructs 倍額, 金額

            let 倍にする (額) = 倍額 { もと = 額, 倍 = 金額(額.value * 2) }

            example 倍にする
              | "twice over" : (金額(21)) -> 倍額 { もと = 金額(21), 倍 = 金額(42) }
            """;

    @Test
    void aRowHeldByAnImplementationInAnotherLoaderHolds() {
        Crossed crossed = new Crossed();
        Observations observed = evaluated(crossed);

        assertEquals(List.of(), reasons(observed), "the row holds, and holds for its own reason");
        assertEquals(1, observed.rows().size());
        RowOutcome row = observed.rows().get(0);
        assertEquals(Disposition.HELD, row.disposition());
        assertEquals(Stage.COMPARED, row.stage());
        assertNotNull(row.resultArm(), "the answer's case is read off a class this run never defined");
        assertEquals("倍額", row.resultArm().name());
        assertTrue(crossed.applied, "the row went through the seam rather than round it");
    }

    /** The two loaders really are two type universes; otherwise the test above holds for no reason. */
    @Test
    void theAnswerIsNotOfAClassThisRunDefined() {
        Crossed crossed = new Crossed();
        evaluated(crossed);

        assertNotNull(crossed.answer);
        assertEquals(crossed.answer.getClass().getName(), crossed.hereNamed,
                "one binary name");
        assertNotEquals(crossed.answer.getClass(), crossed.hereLoaded,
                "and two classes: nothing of the answer could have been shared");
    }

    /**
     * What applied the behavior is the answerer's own answer, not something read off the run.
     *
     * <p>Held by identity, because the arms of {@link Applied} are what a compile can produce and this
     * change adds none: two answerers naming the same arm are equal values, and what has to be
     * established is where the value came from.
     */
    @Test
    void theOutcomeSaysWhatTheAnswererSaysAppliedIt() {
        Crossed crossed = new Crossed();
        RowOutcome row = evaluated(crossed).rows().get(0);

        assertSame(crossed.itsOwn, row.run().applied(),
                "a row records the answerer's answer to what applied it");
    }

    /**
     * An answerer that defines this compile's classes a second time and applies those.
     *
     * <p>It constructs nothing of this run's: the argument arrives as a neutral form and is decoded by
     * its own decoder, and the {@code $Impl} it applies is its own. Handed the values as this compile
     * built them it would fail on the first cast inside the behavior.
     */
    private static final class Crossed implements Answerer {

        private MemoryClassLoader mine;
        private boolean applied;
        private Object answer;
        /** The one value this answerer says applied a behavior, kept so the outcome can be held to
         * having come from here. */
        private final Applied itsOwn = new Applied.GeneratedHere();
        /** What this run's own {@code 倍額} is, for the test to hold the two apart afterwards. */
        private Class<?> hereLoaded;
        private String hereNamed;

        private Answering asAnswering(Map<String, ClassFileImage> classes, ClassLoader parent) {
            return (generated, compiled) -> {
                mine = new MemoryClassLoader(classes, parent);
                return this;
            };
        }

        @Override
        public Answer of(String behavior) {
            return new Answer.Something() {

                @Override
                public Origin origin() {
                    return new TheCompilesOwn();
                }

                @Override
                public Applying applying(List<DependencyStandin> standins) {
                    return Crossed.this.applying(behavior, standins);
                }
            };
        }

        private Applying applying(String behavior, List<DependencyStandin> standins) {
            assertEquals(List.of(), standins, "an example of a `let` body depends on nothing");
            return new Applying() {

                @Override
                public Applied applied() {
                    return itsOwn;
                }

                @Override
                public Object to(List<Handed> arguments) {
                    applied = true;
                    Object[] args = new Object[arguments.size()];
                    for (int i = 0; i < args.length; i++) {
                        args[i] = decodedHere(arguments.get(i).neutral());
                    }
                    answer = applyHere(behavior, args);
                    return answer;
                }
            };
        }

        /** A neutral form, read by this answerer's own derived decoder. */
        private Object decodedHere(NeutralValue neutral) {
            try {
                Class<?> amount = mine.loadClass("example.crossing.金額");
                java.lang.reflect.Method decoder = amount.getDeclaredMethod("decoder");
                decoder.setAccessible(true);
                @SuppressWarnings("unchecked")
                Decoder<Object, ?> d = (Decoder<Object, ?>) decoder.invoke(null);
                Result<?> read = d.decode(neutral.read(), net.unit8.raoh.Path.ROOT);
                assertInstanceOf(Ok.class, read, "the neutral form is what a derived decoder reads");
                return ((Ok<?>) read).value();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private Object applyHere(String behavior, Object[] args) {
            try {
                Class<?> impl = GeneratedClasses.load(mine,
                        new GeneratedClass.BehaviorImpl("example.crossing", behavior));
                java.lang.reflect.Constructor<?> ctor = impl.getDeclaredConstructor();
                ctor.setAccessible(true);
                Class<?>[] params = new Class<?>[args.length];
                java.util.Arrays.fill(params, Object.class);
                java.lang.reflect.Method apply = impl.getDeclaredMethod("apply", params);
                apply.setAccessible(true);
                return apply.invoke(ctor.newInstance(), args);
            } catch (java.lang.reflect.InvocationTargetException ite) {
                throw new InvocationFailure(ite.getCause());
            } catch (ReflectiveOperationException e) {
                throw new ImplementationNotReached(String.valueOf(e.getMessage()), e);
            }
        }
    }

    /** The row, evaluated the way the query asks it, against an answerer the test chooses. */
    private static Observations evaluated(Crossed answerer) {
        Compilation c = Compilation.ofSource(MODEL, "Main");
        c.db().ask(new Output.All());
        String name = c.modules().get(0);
        souther.compiler.generated.EvaluationArtifact artifact = c.db()
                .ask(new Output.EvaluationLinked(name, ArmObservation.OMIT)).value();
        Map<String, ClassFileImage> classes = artifact.classes();
        ClassLoader parent = ExampleVerifier.class.getClassLoader();
        answerer.hereNamed = "example.crossing.倍額";
        answerer.hereLoaded = loaded(new MemoryClassLoader(classes, parent), answerer.hereNamed);
        return ExampleVerifier.check(
                c.db().ask(new Shapes.Prepared(name)).value().forExamples(),
                Scopes.derived(c.db(), name).value(),
                Shapes.publishedDeclarations(c.db()),
                Shapes.declarationKinds(c.db()),
                souther.compiler.query.ExampleExecutions.of(c.db(), name).fieldTypes(),
                c.db().ask(new Bodies.Reachable(name)).value(),
                artifact,
                // The crossing here is between two loaders of one build, so what the answerer reads
                // values by is this compile's declarations and nothing is held against them.
                () -> {
                    throw new AssertionError("an answer of this compile's own read declarations");
                },
                c.db().ask(new Bodies.Requirements(name)).value(),
                parent,
                c.db().ask(new Bodies.ModuleDefinitions(name)).value(),
                JvmDeadlines.of(EvaluationPolicy.DEFAULT.compilerTimeout()),
                EvaluationPolicy.DEFAULT,
                answerer.asAnswering(classes, parent),
                CheckedEnsures.executableOf(
                        c.db().ask(new Bodies.ReachableContracts(name)).value()));
    }

    private static Class<?> loaded(ClassLoader loader, String name) {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> reasons(Observations observed) {
        List<String> said = new ArrayList<>();
        observed.failures().forEach(f -> said.add(String.valueOf(f.code())));
        return said;
    }
}
