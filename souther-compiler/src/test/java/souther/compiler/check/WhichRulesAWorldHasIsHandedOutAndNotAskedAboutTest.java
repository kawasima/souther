package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * Which of a clause's parts a reading's world holds is handed to the reading, never asked about.
 *
 * <p>A reader asking what one conjunct was holding reads the declaration in a world the author did
 * not write, and every walk over that declaration has to be a walk of the same world: a part one of
 * them reached that another never read is a value whose rules were not gathered.
 *
 * <p>That agreement used to rest on each walk remembering to consult a rule. Four of them did and
 * one did not, so the connectives were composed over conjuncts every walk beside them had left out,
 * the settled answer was the same with a conjunct and without it, and every end a choice was
 * holding came back held by nobody.
 *
 * <p><b>So the rule is gone and what is left is a producer.</b> A world hands out the clause it has
 * ({@link PartsLeftOut#viewOf}), and there is nowhere to ask whether one part is in it. A walk
 * written tomorrow reads what it is given; it cannot read the whole clause by forgetting to ask,
 * because forgetting is no longer a thing it can do.
 */
class WhichRulesAWorldHasIsHandedOutAndNotAskedAboutTest {

    /**
     * Nothing can ask a world about one part.
     *
     * <p>Said of what the type offers rather than of who calls it. A predicate over a part is a
     * question every walk has to be written to ask, and the walk that is written without asking it
     * is the one this is about — so what is checked is that the question cannot be put.
     */
    @Test
    void nothingCanAskAWorldAboutOnePart() {
        Set<String> asking = new TreeSet<>();
        for (Method each : PartsLeftOut.class.getDeclaredMethods()) {
            for (Class<?> takes : each.getParameterTypes()) {
                if (takes == PartId.class) {
                    asking.add(each.getName());
                }
            }
        }
        assertEquals(Set.of(), asking,
                "a world answering about one part is one every walk over a clause has to be"
                        + " written to consult, and the walk that is not written to is the defect");
    }

    /**
     * And what it hands out is the clause, which is the one thing a walk can be given.
     *
     * <p>The other half: a world that offered nothing would leave every walk to work out what it
     * holds, which is the same defect reached from the other side.
     */
    @Test
    void andWhatItHandsOutIsTheClauseThisWorldHas() {
        List<Method> handing = new ArrayList<>();
        for (Method each : PartsLeftOut.class.getDeclaredMethods()) {
            if (each.getReturnType() == ClauseView.class) {
                handing.add(each);
            }
        }
        assertEquals(1, handing.size(),
                "one way to be told what a world holds of a clause, and it is the one every walk"
                        + " is given: " + handing);
    }

    /**
     * And every walk over a declaration's clauses is given one.
     *
     * <p>Over the compiler and not over the walks somebody listed. What a walk of a world reads is
     * the parts that world holds, so a walk holding a world and working out the parts for itself is
     * a second answer to a question with one — and the shape that answer takes is a method that has
     * both in hand.
     */
    @Test
    void andNothingHoldsAWorldBesideThePartsItWouldHaveHandedOut() {
        Set<String> both = new TreeSet<>();
        int holding = 0;
        for (Class<?> each : compiled()) {
            if (each == PartsLeftOut.class || PartsLeftOut.class.isAssignableFrom(each)) {
                continue;
            }
            for (Method method : each.getDeclaredMethods()) {
                boolean world = false;
                boolean parts = false;
                for (Class<?> takes : method.getParameterTypes()) {
                    world |= takes == PartsLeftOut.class;
                    parts |= takes == ClauseView.class;
                }
                if (world) {
                    holding++;
                }
                if (world && parts) {
                    both.add(each.getName() + "." + method.getName());
                }
            }
        }
        assertTrue(holding > 0, "found nothing that is handed a world at all — the scan missed it");
        assertEquals(Set.of(), both,
                "a reader given a world and the clause it holds can read one and walk the other,"
                        + " which is the two readings of one world this arrangement is against");
    }

    /**
     * Every class of this compiler, loaded for what it declares.
     *
     * <p>Not initialised, for the reason the readings of orders beside it give: what is read here is
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
                    WhichRulesAWorldHasIsHandedOutAndNotAskedAboutTest.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError _) {
            // a class this test's path cannot resolve says nothing about the rule
        }
    }
}
