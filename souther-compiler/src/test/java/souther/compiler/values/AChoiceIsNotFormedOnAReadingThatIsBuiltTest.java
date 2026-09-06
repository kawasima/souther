package souther.compiler.values;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A choice is taken while a reading is a description, and a built one has no way to form another.
 *
 * <p>Which alternatives anybody can be in is a question about the values and the order together, so
 * it is settled over {@link PlannedValues} and what is built afterwards is built from a description
 * a choice has already been taken in. A built reading keeps what that choice left, is asked about
 * it, and is conjoined with the readings of other declarations. Given an operation that turns two
 * of them into one that is not a conjunction, a caller could take a choice on this side of
 * {@link PlannedValues#resolve} — where neither the order nor which branches stand is in hand — and
 * the answer would be settled by whichever half of the question that caller happened to hold.
 *
 * <p><b>Said positively.</b> What is fixed is which operations may turn two readings into one, and
 * not that no method is called {@code join}: a second spelling under another name would pass a rule
 * written about the name. Every entry below is a conjunction or a lifting of one reading into a
 * conjunction of one, and a new one is a decision somebody has to make rather than a line to add.
 *
 * <p>What is not fixed here is that a reading never reaches the arithmetic a choice is realized by.
 * It does, legitimately: {@link AdmissibleValues.Held.Alternatives} asks the allowance what a block
 * holds across the alternatives it already has, which is a fact derived from a choice already taken
 * and not a choice being taken. The two are told apart by whether two readings go in, which is what
 * is read below.
 */
class AChoiceIsNotFormedOnAReadingThatIsBuiltTest {

    /** What a reading of worked-out values is, in either of the shapes one is held in. */
    private static final Set<Class<?>> READINGS =
            Set.of(AdmissibleValues.class, ConjoinedAdmissibleValues.class);

    /**
     * The operations a caller may turn readings into a reading by.
     *
     * <p>A method that hands one back and takes one — which, on an instance, is the two a caller
     * holds, and on a static is however many it was given. Private methods are left out: what is
     * fixed is the surface a caller can reach for, and a step inside one of these operations is
     * that operation.
     */
    private static Set<String> composing(Class<?> reading) {
        Set<String> out = new TreeSet<>();
        for (Method method : reading.getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isPrivate(method.getModifiers())
                    || !mentionsAReading(method.getGenericReturnType())) {
                continue;
            }
            for (Type parameter : method.getGenericParameterTypes()) {
                if (mentionsAReading(parameter)) {
                    out.add(reading.getSimpleName() + "#" + method.getName());
                }
            }
        }
        return out;
    }

    /** Whether {@code type} is a reading, or names one as an argument — a list of them is a
     *  caller's several answers however it was spelled. */
    private static boolean mentionsAReading(Type type) {
        if (type instanceof Class<?> it) {
            return READINGS.stream().anyMatch(each -> each.isAssignableFrom(it));
        }
        if (type instanceof ParameterizedType it) {
            if (mentionsAReading(it.getRawType())) {
                return true;
            }
            for (Type argument : it.getActualTypeArguments()) {
                if (mentionsAReading(argument)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The whole of what a built reading may be composed with another by.
     *
     * <p>{@code meet} is the conjunction, which is the one connective that outlives a description:
     * a caller relating two declarations holds what each of them came to. {@code metAll} is that
     * conjunction over several at once, so that what a block costs does not turn on how the fold
     * was bracketed. {@code of} lifts one reading into a conjunction of one factor.
     */
    private static final Set<String> COMPOSING = Set.of(
            "AdmissibleValues#meet",
            "AdmissibleValues#metAll",
            "ConjoinedAdmissibleValues#meet",
            "ConjoinedAdmissibleValues#of");

    @Test
    void nothingButAConjunctionTurnsTwoBuiltReadingsIntoOne() {
        Set<String> found = new LinkedHashSet<>(composing(AdmissibleValues.class));
        found.addAll(composing(ConjoinedAdmissibleValues.class));

        assertEquals(new TreeSet<>(COMPOSING), new TreeSet<>(found),
                "a built reading composes with another by a conjunction and by nothing else");
    }

    /**
     * The one thing that realizes a choice of value sets.
     *
     * <p>A choice of two sets is a machine where either side is a language, and what a machine
     * costs is charged to the position it is built for. Realized anywhere else, a compilation would
     * pay for one twice or build one under an allowance nobody granted — so the arithmetic has one
     * caller, and every way of asking for it goes through the position's own realizer.
     *
     * <p>{@link Sets} is the table itself: what it reaches is the free half of it, under a meter
     * that pays for nothing, and a language handed to it is refused where it is asked for.
     */
    @Test
    void aChoiceOfValueSetsIsRealizedInOnePlace() {
        assertEquals(Set.of("souther.compiler.values.Realizer", "souther.compiler.values.Sets"),
                new TreeSet<>(WhatWasCompiled.callersOf(Sets.class, "joinedUnder")),
                "the join of two sets is built where a position's allowance pays for it");
    }
}
