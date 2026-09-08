package souther.compiler.query;

import org.junit.jupiter.api.Test;
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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A finding says what a measure established. Where a report about one belongs is asked of the
 * module that wrote the code, and what is left holding a place says why.
 *
 * <p>Asked of the finding and not of the thing last put right. A place is taken out of one value at
 * a time, and a check rooted at whichever one that was cannot see the rest — which is how a place
 * came to be left under {@code APointOfADeclaredBorder} while everything about the arms was green.
 * So the walk starts where the answer is and reports every place under it.
 *
 * <p><b>What is left is written down with its reason.</b> Not because the list is the rule — the
 * rule is that a finding carries none — but because a place still under one is either a fact this
 * compiler has measured and left, or one nobody has looked at. An entry added here is a finding of
 * its own: whoever adds it is saying which of the two it is.
 */
class AFindingSaysWhatWasFoundAndNotWhereToPrintItTest {

    /** What a report points with, which is what a finding may not hold. */
    private static final Set<String> A_PLACE = Set.of(
            "souther.compiler.diag.SourcePos",
            "souther.compiler.diag.Citation",
            "souther.compiler.diag.Region",
            "souther.compiler.diag.DiagnosticPlace");

    /**
     * Every place still under a finding, and what each of them is.
     *
     * <p>Three readings and no fourth. A place that tells one value from another cannot be taken
     * away without merging things this compiler tells apart; a place nothing has measured is one
     * nobody has looked at, and moving it would be acting on nothing; and a place in the tree the
     * author wrote is the source itself, which is not what this is about.
     */
    private static final Map<String, String> WHAT_IS_LEFT = new TreeMap<>(Map.of(
            "souther.compiler.partition.OnTheWay$Declined.at",
            "the place is what tells one declined condition from another, and taking it away would"
                    + " merge conditions this compiler tells apart",
            "souther.compiler.query.About$AnUnansweredRow.at",
            "a row is written in the module's own source, which is not a boundary anything crosses",
            "souther.compiler.check.RuleCitation$WrittenAt.at",
            "a citation is the handle a report writes for a rule with no name: what it names and how"
                    + " a reader is sent to it are one answer by design",
            "souther.compiler.partition.LineOrigin$ComparisonOrigin$Read.writtenAt",
            "the same, for the rule a comparison reads",
            "souther.compiler.partition.PredicateOrigin.writtenAt",
            "the same, for a predicate applied in a body",
            "souther.compiler.partition.OnTheWay$Narrowed.at",
            "nothing has been measured about it: no corpus reaches one",
            "souther.compiler.partition.OnTheWay$TakenIn.at",
            "nothing has been measured about it: no corpus reaches one",
            "souther.compiler.inputs.RuleWithoutALine.reachedAt",
            "nothing has been measured about it: no corpus reaches one",
            "souther.compiler.observe.Incompleteness$Met.citations",
            "nothing has been measured about it: no corpus reaches one"));

    /** The same, for the three a standing question carries. Held apart only because a map literal
     *  takes ten pairs at most. */
    private static final Map<String, String> AND_THE_STANDING_QUESTIONS = Map.of(
            "souther.compiler.inputs.StandingQuestion$BoundaryUndetermined.reachedAt",
            "nothing has been measured about it: no corpus reaches one",
            "souther.compiler.inputs.StandingQuestion$Exact.reachedAt",
            "nothing has been measured about it: no corpus reaches one",
            "souther.compiler.inputs.StandingQuestion$NothingClassifiesIt.reachedAt",
            "nothing has been measured about it: no corpus reaches one");

    @Test
    void everyPlaceLeftUnderAFindingIsOneSomebodyAnsweredFor() {
        Set<String> left = new TreeSet<>(WHAT_IS_LEFT.keySet());
        left.addAll(AND_THE_STANDING_QUESTIONS.keySet());
        assertEquals(left, placesUnder(Adequacy.Finding.class),
                "a place under a finding is one somebody said why about, or one this says to take"
                        + " out");
    }

    /**
     * The walk says no because there is none, and not because it says no.
     *
     * <p>A site of a comparison still carries where it is, which is a question of its own and not
     * this one. Named here so that the answer above is an answer: a walk that had stopped finding
     * places would give it whatever it was asked.
     */
    @Test
    void andTheWalkFindsOneWhereThereIsOne() {
        assertEquals(Set.of("souther.compiler.coverage.CoverageSites$ComparisonSite.at"),
                placesUnder(CoverageSites.ComparisonSite.class),
                "a comparison's site is where a report about it points, and still holds it");
    }

    /**
     * Every place declared anywhere under {@code root}, by where it sits.
     *
     * <p>The tree the author wrote is not walked into. Every node of it carries where it is, which
     * is the source saying where it is rather than an answer carrying a caret, and walking in would
     * bury the question this asks under the whole of the language.
     */
    private static Set<String> placesUnder(Class<?> root) {
        Set<String> found = new TreeSet<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Type> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Class<?> at = raw(queue.poll());
            if (at == null || !seen.add(at) || at.getName().startsWith("java.")
                    || at.getName().startsWith("souther.compiler.ast.")
                    || at.getName().startsWith("souther.compiler.core.")
                    || isAPlace(at)) {
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
                    if (of != null && isAPlace(of)) {
                        found.add(at.getName() + "." + field.getName());
                    }
                    queue.add(each);
                }
            }
        }
        return found;
    }

    /**
     * Whether {@code of} is one of the things a report points with.
     *
     * <p>What one is made of is not walked into. A citation holds the position it projects, so a
     * walk that went in would report a place inside every place and say nothing about which values
     * hold one.
     */
    private static boolean isAPlace(Class<?> of) {
        if (A_PLACE.contains(of.getName())) {
            return true;
        }
        for (Class<?> each : of.getInterfaces()) {
            if (A_PLACE.contains(each.getName())) {
                return true;
            }
        }
        return false;
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
