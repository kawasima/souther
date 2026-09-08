package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.coverage.ArmReportAnchor;
import souther.compiler.coverage.ControlPointId;
import souther.compiler.coverage.CoverageSites;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An arm crosses a module boundary saying which fork it is one of, and nothing about where that
 * fork is written.
 *
 * <p>The rule this holds is the one {@code Db} states of every edge: what a consumer depends on is
 * what it means. A reading of another module's rules means what those rules say; where they are
 * written is a second question, asked by whoever is about to put a caret somewhere. Held in the arm,
 * the two are one value, and an edit that moves a helper and changes nothing it does arrives at
 * every module that calls it as a fork that says something different — which no test of what this
 * compiler answers can see, because every answer is new and every answer says what it said.
 *
 * <p>Read off the types and not off a list somebody keeps. What an arm may hold is what its
 * declarations allow, so a place added to any of them is a place this finds, whether or not a
 * corpus happens to reach one.
 */
class AnArmSaysWhichPlaceNamesItAndNotWhichPlaceItIsTest {

    /** What a report points with, which is what may not sit inside an arm. */
    private static final Set<String> A_PLACE = Set.of(
            "souther.compiler.diag.SourcePos",
            "souther.compiler.diag.Citation",
            "souther.compiler.diag.Region",
            "souther.compiler.diag.DiagnosticPlace");

    @Test
    void nothingAnArmHoldsIsAPlace() {
        assertEquals(Set.of(), placesUnder(ControlPointId.ArmOccurrence.class),
                "an arm says which fork it is one of; where that fork is written is asked of the"
                        + " module that wrote it");
    }

    @Test
    void nothingAnArmsSiteHoldsIsAPlace() {
        assertEquals(Set.of(), placesUnder(CoverageSites.ArmSite.class),
                "and a site of one is the arm and what it is owed for, which are the same two"
                        + " questions");
    }

    /**
     * The anchor is what the arm carries instead, and it is the discriminator rather than a place.
     *
     * <p>Written out because the walk above cannot say it: a value holding no place passes whether
     * it says which of the two places names it or says nothing at all, and the second is a reader
     * left to guess.
     */
    @Test
    void whatItHoldsInsteadIsWhichQuestionPlacesIt() {
        assertTrue(ArmReportAnchor.class.isSealed(),
                "there are two places a report about an arm points at, and no third");
        assertEquals(List.of("WhereItIsWritten", "WhereItWasReached"),
                List.of(ArmReportAnchor.class.getPermittedSubclasses()).stream()
                        .map(Class::getSimpleName).toList(),
                "the fork's own file, or the call this compilation came in through");
        assertEquals(Set.of(), placesUnder(ArmReportAnchor.class),
                "and neither of them carries one");
    }

    /**
     * The walk says no because there is none, and not because it says no.
     *
     * <p>A site of a comparison still carries where it is, which is a question of its own and not
     * this one. Named here so that the three answers above are answers: a walk that had stopped
     * finding places would give them whatever it was asked.
     */
    @Test
    void andTheWalkFindsOneWhereThereIsOne() {
        assertEquals(Set.of("ComparisonSite.at", "ReachedCitation.at", "UnplacedCitation.at",
                        "UnplacedElsewhereCitation.at", "WrittenCitation.at"),
                placesUnder(CoverageSites.ComparisonSite.class),
                "a comparison's site is where a report about it points, and still holds it —"
                        + " and the walk goes on into what a citation itself is made of");
    }

    /** Every place declared anywhere under {@code root}, by where it sits. */
    private static Set<String> placesUnder(Class<?> root) {
        Set<String> found = new TreeSet<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Type> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Class<?> at = raw(queue.poll());
            if (at == null || !seen.add(at) || at.getName().startsWith("java.")) {
                continue;
            }
            Class<?>[] cases = at.getPermittedSubclasses();
            if (cases != null) {
                queue.addAll(List.of(cases));
            }
            for (Field field : at.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                for (Type each : withArguments(field.getGenericType())) {
                    Class<?> of = raw(each);
                    if (of != null && A_PLACE.contains(of.getName())) {
                        found.add(at.getSimpleName() + "." + field.getName());
                    }
                    queue.add(each);
                }
            }
        }
        return found;
    }

    /** A written type and whatever was written inside it, so a place in a list is a place. */
    private static List<Type> withArguments(Type type) {
        return type instanceof ParameterizedType wrote
                ? java.util.stream.Stream.concat(java.util.stream.Stream.of(type),
                        java.util.Arrays.stream(wrote.getActualTypeArguments())).toList()
                : List.of(type);
    }

    private static Class<?> raw(Type type) {
        return switch (type) {
            case Class<?> at -> at;
            case ParameterizedType wrote -> raw(wrote.getRawType());
            case WildcardType any -> any.getUpperBounds().length == 0
                    ? null : raw(any.getUpperBounds()[0]);
            case TypeVariable<?> letter -> letter.getBounds().length == 0
                    ? null : raw(letter.getBounds()[0]);
            default -> null;
        };
    }
}
