package souther.compiler.types;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a reference is held, it is held as narrowly as what holds it means.
 *
 * <p>{@link ReachName} covers every route a name in a body takes, and only three of them reach a
 * declaration ({@link ReachName.Declaration}). Most things that carry a reference carry one of
 * those — a definition a module took on, a table of what a call expands to, what an expansion left
 * standing — and where such a carrier takes the wider type, the reference it is handed can be one
 * that reaches no declaration at all. What follows is a check inside the carrier, or a lookup that
 * quietly finds nothing.
 *
 * <p>Read off the compiled classes rather than the sources, and asserted as a list, because a list
 * is what makes a new one a decision. A position added to it is somebody saying that this one
 * genuinely takes any route — which is true of a body's own names and of the one place a route is
 * worked out, and is worth writing down when it is claimed.
 *
 * <p>Written after three rounds of review found the same shape three times, each in a place the
 * previous round's acceptance did not reach. What none of those rounds had was anything that says
 * where the frontier is; this does, so the next one is a failing test rather than a reading.
 */
class WhatCarriesADeclarationSaysSoInItsTypeTest {

    /**
     * The positions that carry any route, and why each of them does.
     *
     * <p>A name a body wrote may reach a binding, a type used as a value or the library's namespace
     * as easily as a declaration, so what holds one of those holds the whole of {@link ReachName};
     * and the place that works a route out from a denotation answers with whatever that denotation
     * turns out to be.
     */
    private static final Set<String> ANY_ROUTE = Set.of(
            // A name written in a body, which is answered before anything knows what it reaches.
            "souther.compiler.ast.Hir$Var$Denoting.reachedAs",
            "souther.compiler.ast.Hir$Var.denoting",
            "souther.compiler.ast.Hir$Var.respelled",
            "souther.compiler.ast.Hir$Apply.synthetic",
            // The one place a route is worked out, and the copy of one a rewrite moves.
            "souther.compiler.types.ReachName.of",
            "souther.compiler.check.HelperInliner$Copy.of",
            // What stands in an expanded body for one of the callee's parameters. The caller may
            // hand over a declaration or a binding of its own, and this carries across whichever it
            // was rather than deciding.
            "souther.compiler.check.HelperInliner$Substituted.reachedAs");

    @Test
    void everyPositionThatCarriesAReferenceIsAsNarrowAsWhatHoldsIt() {
        List<String> wide = new ArrayList<>();
        for (Class<?> each : compiled()) {
            for (Field field : each.getDeclaredFields()) {
                if (field.getType() == ReachName.class) {
                    wide.add(each.getName() + "." + field.getName());
                }
            }
            for (Method method : each.getDeclaredMethods()) {
                if (method.getReturnType() == ReachName.class) {
                    wide.add(each.getName() + "." + method.getName());
                }
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.getType() == ReachName.class) {
                        wide.add(each.getName() + "." + method.getName());
                    }
                }
            }
        }

        assertEquals(List.of(), wide.stream().filter(each -> !ANY_ROUTE.contains(each)).sorted()
                        .distinct().toList(),
                "each of these holds a reference that may reach no declaration. Narrow it to"
                        + " `ReachName.Declaration`, or add it above with the reason it takes any"
                        + " route");
    }

    /** The control: the walk reads the classes, so an empty answer above is an answer. */
    @Test
    void andTheWalkReadsTheClassesTheseArePositionsIn() {
        List<Class<?>> read = compiled();

        assertTrue(read.size() > 100, () -> "read only " + read.size() + " compiled classes");
        assertTrue(read.stream().anyMatch(each -> each.getName().endsWith("Hir$Var$Denoting")),
                "and reaches the one that holds a name a body wrote");
    }

    private static List<Class<?>> compiled() {
        List<Class<?>> classes = new ArrayList<>();
        for (String name : WhatWasCompiled.classes()) {
            try {
                classes.add(Class.forName(name, false,
                        WhatCarriesADeclarationSaysSoInItsTypeTest.class.getClassLoader()));
            } catch (ClassNotFoundException | NoClassDefFoundError _) {
                // A class the test classpath cannot load says nothing about what it holds.
            }
        }
        return classes;
    }
}
