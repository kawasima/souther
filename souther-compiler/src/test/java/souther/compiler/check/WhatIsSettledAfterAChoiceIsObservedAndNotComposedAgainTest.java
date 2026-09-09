package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a choice leaves a position is composed once, and what leaves that reading is an observation.
 *
 * <p>The alternatives of a choice are readings of the whole value, so what one leaves a position is
 * settled where both of the languages that decide a branch's fate are held ({@link Confinement}).
 * A second composition of the same choice is a second answer about one model, and the two agree
 * only until somebody changes one of them — which is what this stops, on the one thing that leaves
 * that reading.
 *
 * <p><b>Said of the operations and not of their names.</b> Forbidding {@code join} and {@code
 * either} leaves the next algebra to be written under another name; what an algebra is, is an
 * operation over its own type, and there is none here. What may be asked of
 * {@link SettledOrderEnvelope} is where a position stops, and the answer to that is not another one
 * of these.
 */
class WhatIsSettledAfterAChoiceIsObservedAndNotComposedAgainTest {

    /**
     * Nothing anywhere takes one of these and answers with another.
     *
     * <p>Over every class this compiler holds and not over the type alone: an algebra written
     * beside it — a static method over two of them, a builder holding one and returning one — is
     * the same second answer arrived at from outside.
     */
    @Test
    void nothingTakesASettledOrderAndAnswersWithAnother() {
        Set<String> composing = new TreeSet<>();
        int mentioning = 0;
        for (Class<?> each : compiled()) {
            for (Method method : each.getDeclaredMethods()) {
                boolean takes = takesOne(method.getParameterTypes());
                boolean answers = method.getReturnType() == SettledOrderEnvelope.class;
                if (!takes && !answers) {
                    continue;
                }
                mentioning++;
                if (takes && answers) {
                    composing.add(each.getName() + "." + method.getName());
                }
            }
            // A constructor answers with the class it is of, so one taking a settled order is a
            // reader holding what it was handed. The exception is this type's own: made from
            // another of these, it would be the same operation under the one name that does not
            // read as one.
            for (Constructor<?> made : each.getDeclaredConstructors()) {
                if (takesOne(made.getParameterTypes())) {
                    mentioning++;
                    if (each == SettledOrderEnvelope.class) {
                        composing.add(each.getName() + ".<init>");
                    }
                }
            }
        }
        assertTrue(mentioning > 0,
                "found nothing that is asked about a settled order at all — the scan missed it");
        assertEquals(Set.of(), composing,
                "an operation over two of these is a second composition of one choice, and the"
                        + " model it answers about is the same one the first composed");
    }

    /**
     * And the one that makes them is asked for an order, which only the reading that composes one
     * holds.
     *
     * <p>The other half of the same rule. What is here came from a reading that had spent the
     * connectives, and the way that is kept true is that a caller cannot arrive with an envelope of
     * its own: the way in wants what {@code WhatDecidesWhetherAValueExistsHoldsBothLanguagesTest}
     * lets only {@link Confinement} hold.
     */
    @Test
    void andTheOneWayInWantsAnOrderNobodyButTheReadingHolds() {
        List<Method> made = new ArrayList<>();
        for (Method each : SettledOrderEnvelope.class.getDeclaredMethods()) {
            if (each.getReturnType() == SettledOrderEnvelope.class
                    && each.getParameterCount() > 0) {
                made.add(each);
            }
        }
        assertEquals(1, made.size(),
                "one way to make one of these, and it is the one that is looked at: " + made);
        assertEquals(souther.compiler.numeric.OrderedIntervals.class,
                made.get(0).getParameterTypes()[0],
                "what it is made from is an order, and an order is held by the reading that"
                        + " composed the connectives over it");
    }

    private static boolean takesOne(Class<?>[] these) {
        for (Class<?> each : these) {
            if (each == SettledOrderEnvelope.class) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every class of this compiler, loaded for what it declares.
     *
     * <p>Not initialised, for the reason the reading of orders beside it gives: what is read here is
     * what a type declares, and running its static initialisers would make this test depend on what
     * they touch.
     */
    private static List<Class<?>> compiled() {
        List<Class<?>> found = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            Path root = Path.of(entry);
            if (!entry.contains("souther-") || !Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(each -> each.toString().endsWith(".class"))
                        .map(each -> root.relativize(each).toString())
                        .forEach(name -> load(name, found));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return found;
    }

    private static void load(String path, List<Class<?>> into) {
        String named = path.substring(0, path.length() - ".class".length())
                .replace(File.separatorChar, '.').replace('/', '.');
        if (!named.startsWith("souther.") || named.endsWith("package-info")
                || named.endsWith("module-info")) {
            return;
        }
        try {
            into.add(Class.forName(named, false,
                    WhatIsSettledAfterAChoiceIsObservedAndNotComposedAgainTest.class
                            .getClassLoader()));
        } catch (ClassNotFoundException | LinkageError _) {
            // a class this test's path cannot resolve says nothing about the rule
        }
    }
}
