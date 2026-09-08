package souther.compiler.values;

import org.junit.jupiter.api.Test;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading is in one of the states its operations reach, and the parts are not a way in.
 *
 * <p>What each of these holds is several parts that state relations to each other — a whole that
 * holds nothing is not a position that holds nothing, the alternatives are never an empty union, a
 * promise is about the blocks every alternative agrees on, a refusal is one the work that built the
 * reading noted. None of those is a property of one part, so a caller handed the parts side by side
 * can write down a combination nothing read, and nothing says so. What holds the relations up is
 * that the operations are the only things that make one.
 *
 * <p>So the propositions are about the ways in. A reading answers with a reading — that is what an
 * algebra is — and what is asked of each of those is that it composes or narrows one, rather than
 * taking the parts and handing the reading back. The second shape is the one that lets a state be
 * asserted of parts nothing put together, and it is also the one that lets a fact live in an
 * operation rather than in the value: remove the operation and the way to say the fact is gone with
 * it.
 *
 * <p><b>Nothing outside the type can mint one</b>, since every constructor is its own, so the ways
 * in are what the type declares and the lists below are the whole of them. A way added tomorrow
 * arrives here as a difference and is a question somebody answers, rather than a hole nobody sees.
 *
 * <p><b>Which types those are is found and not listed.</b> A state added tomorrow and left off a
 * list here would be one nothing says anything about, which is the shape of the very thing these
 * propositions are for. So the family is walked to from the two languages a reading of a
 * declaration joins, through what each state is made of and what its operations take and answer
 * with, and what makes a type one of them is that it holds its parts the way these do.
 */
class AReadingIsWhatItsOperationsReachTest {

    /**
     * The two languages a declaration's reading joins, which is where the walk starts.
     *
     * <p>Roots and not the family: what a rule of a value leaves is said in these two and in
     * nothing else ({@code Confinement}), and everything the two of them are made of, take, or
     * answer with is reached from here.
     */
    private static final List<Class<?>> ROOTS =
            List.of(AdmissibleValues.class, OrderedIntervals.class);

    /**
     * Every state the readings reach, in the order their names sort.
     *
     * <p>A state is one that keeps its parts as this design says a state keeps them: one thing, and
     * that thing a record of the parts. Which is what makes the family findable at all — a type
     * that publishes its parts as a constructor is exactly what these propositions refuse, and one
     * that is reached from here and does not hold them this way is a difference in
     * {@link #theFamilyIsTheOneTheReadingsReach}.
     */
    private static final List<Class<?>> FAMILY = family();

    private static List<Class<?>> family() {
        List<Class<?>> found = new java.util.ArrayList<>();
        Set<Class<?>> walked = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>(ROOTS);
        while (!pending.isEmpty()) {
            Class<?> one = pending.poll();
            if (!ofTheReadings(one) || !walked.add(one)) {
                continue;
            }
            if (!keepsItsPartsAsAState(one)) {
                continue;
            }
            found.add(one);
            for (RecordComponent part : WhatAReadingIsMadeOf.of(one)) {
                pending.add(part.getType());
            }
            for (Method each : one.getDeclaredMethods()) {
                pending.add(each.getReturnType());
                pending.addAll(List.of(each.getParameterTypes()));
            }
            for (Class<?> nested : one.getDeclaredClasses()) {
                pending.add(nested);
            }
        }
        found.sort(Comparator.comparing(Class::getName));
        return List.copyOf(found);
    }

    /** Whether the type is one of the two packages a reading of a value is written in. */
    private static boolean ofTheReadings(Class<?> type) {
        return "souther.compiler.values".equals(type.getPackageName())
                || "souther.compiler.numeric".equals(type.getPackageName());
    }

    /** Whether it holds one thing and that thing is a record of its parts. */
    private static boolean keepsItsPartsAsAState(Class<?> type) {
        List<Field> mine = new java.util.ArrayList<>();
        for (Field each : type.getDeclaredFields()) {
            if (!Modifier.isStatic(each.getModifiers())) {
                mine.add(each);
            }
        }
        return mine.size() == 1 && mine.getFirst().getType().isRecord()
                && "Parts".equals(mine.getFirst().getType().getSimpleName());
    }

    /**
     * The family is the one the readings reach, and it is these.
     *
     * <p>Written down so that a state added to it is somebody saying so. What each of the four is
     * for is its own type's business; that they are four is this walk's answer and not a list kept
     * here — a fifth reached from the readings arrives as a difference, and one that stops being
     * reachable does too.
     */
    @Test
    void theFamilyIsTheOneTheReadingsReach() {
        assertEquals(List.of(AdmissibleValues.class, PlannedValues.Settled.class, Realized.class,
                        OrderedIntervals.class).stream()
                        .sorted(Comparator.comparing(Class::getName)).toList(),
                FAMILY,
                "the states the readings reach are not the ones this says they are. A state added"
                        + " here holds its parts the way these do, and the propositions below are"
                        + " asked of it too");
    }

    /** And none of them may be made from outside, whichever of them it is. */
    @Test
    void noneOfThemMayBeMadeFromOutside() {
        for (Class<?> state : FAMILY) {
            assertEquals(0, state.getConstructors().length,
                    () -> state.getSimpleName() + " publishes a way to write its parts down, and"
                            + " the relations they state to each other are held up by nothing");
        }
    }

    /**
     * Every way to get one, read off what declares them.
     *
     * <p>Constructors whatever their access, because a maker handed the parts is the thing being
     * asked about and being private is not what settles it; methods unless they are private, since
     * a private one is this type composing itself. Read off the declaring types and not off the
     * compiler: a reading's constructor is its own, so nothing else can answer with one it did not
     * get from here.
     */
    private static Set<String> waysInto(Class<?> state, Class<?>... alsoDeclaring) {
        Set<String> ways = new LinkedHashSet<>();
        Set<Class<?>> declaring = new LinkedHashSet<>();
        declaring.add(state);
        declaring.addAll(Set.of(alsoDeclaring));
        for (Class<?> each : declaring) {
            // A constructor is a way in where what it makes is one of these, which takes in the
            // class a sealed reading permits and leaves out a type that only answers with one.
            if (state.isAssignableFrom(each)) {
                for (Constructor<?> made : each.getDeclaredConstructors()) {
                    ways.add(access(made) + signature(made, each.getSimpleName()));
                }
            }
            for (Method m : each.getDeclaredMethods()) {
                if (!Modifier.isPrivate(m.getModifiers()) && m.getReturnType() == state) {
                    ways.add(access(m) + signature(m, m.getName()));
                }
            }
        }
        return ways;
    }

    private static String access(Executable of) {
        int modifiers = of.getModifiers();
        if (Modifier.isPublic(modifiers)) {
            return "public ";
        }
        return Modifier.isPrivate(modifiers) ? "private " : "";
    }

    private static String signature(Executable of, String named) {
        StringBuilder out = new StringBuilder(named).append('(');
        for (int i = 0; i < of.getParameterTypes().length; i++) {
            out.append(i == 0 ? "" : ", ").append(of.getParameterTypes()[i].getSimpleName());
        }
        return out.append(')').toString();
    }

    /**
     * A reading of the values is reached by reading something.
     *
     * <p>Where a reading starts, a conjunction of two or of several, the same reading with more
     * said about what a choice left open, and the same blocks under other names. A rule of the
     * values is not among them: it enters as a description and is worked out, which is the one way
     * across and does the work rather than being handed what the work would have left.
     */
    @Test
    void everyWayToAReadingOfTheValuesIsSomethingReadingIt() {
        assertEquals(Set.of(
                        "private AdmissibleValues(Parts)",
                        "public top()",
                        "public meet(AdmissibleValues, Allowance)",
                        "public metAll(List, Allowance)",
                        "public renamed(Function)",
                        "public alsoOpenedAt(Set)"),
                waysInto(AdmissibleValues.class),
                "a way into a reading of the values that is not one of the operations it is"
                        + " composed by. If it takes the parts and hands a reading back, the"
                        + " relations they state to each other are held up by nothing");
    }

    /**
     * And realization answers with the reading beside what could not be built while making it.
     *
     * <p>The two are settled by one piece of work and are handed over together, so writing them
     * side by side is writing down a refusal no work made — and handing over the reading alone is
     * saying of a reading that came from somewhere that nothing was refused while it was made,
     * which is a claim about work that was not done. So the work is what it is made from: the one
     * way in takes what noted the shortfalls, and there is no way in that takes the reading.
     */
    @Test
    void andADescriptionIsWorkedOutByTheReadingItComesTo() {
        assertEquals(Set.of(
                        "private Realized(Parts)",
                        "of(AdmissibleValues, Unbuilt)",
                        "public alsoOpenedAt(Set)",
                        "realize(Settled, Allowance)"),
                waysInto(Realized.class, AdmissibleValues.class),
                "realization is the one way across, and it is the reading's own: handed the"
                        + " description and the allowance it builds the sets, decides which of"
                        + " them nobody could work out, and settles the rest against what came out");
    }

    /**
     * A description is reached the same way, over plans rather than sets.
     *
     * <p>Asked of the interface and the one reading it permits together, since what a caller holds
     * is the interface and every way to one of those answers with it.
     */
    @Test
    void everyWayToADescriptionIsSomethingReadingIt() {
        assertEquals(Set.of(
                        "private Settled(Parts)",
                        "public top()",
                        "public at(Object, AdmittedPlan)",
                        "public holdingAsOne(Object, Object)",
                        "public heldApart(Object, Object)",
                        "public unreadable(Set, UnreadReason)",
                        "public meet(PlannedValues)",
                        "public alsoStanding(Standing)",
                        "public bothDead(PlannedValues)",
                        "public joinLive(PlannedValues)",
                        "public joinLiveApart(PlannedValues)"),
                waysInto(PlannedValues.class, PlannedValues.Settled.class),
                "a way into a description that is not one of the operations it is composed by");
    }

    /** And so is a reading of the ordered rules. */
    @Test
    void everyWayToAReadingOfTheOrderIsSomethingReadingIt() {
        assertEquals(Set.of(
                        "private OrderedIntervals(Parts)",
                        "public top()",
                        "public at(Object, OrderedInterval)",
                        "public meet(OrderedIntervals)",
                        "public renamed(Function)",
                        "public bothDead(OrderedIntervals)",
                        "public joinLive(OrderedIntervals)"),
                waysInto(OrderedIntervals.class),
                "a way into a reading of the ordered rules that is not one of the operations it is"
                        + " composed by");
    }

    /**
     * And a reading is closed because it is closed, not because nothing here can see a way in.
     *
     * <p>{@link OrderedInterval} is a pair of ends, which is a product with nothing to hold up
     * between its parts: any pair of them is an interval somebody can read, and it publishes the
     * way to write one down. Read by the same walk, it comes back with that way — so a reading
     * reported as having none is a fact about the reading.
     */
    @Test
    void aTypeThatDoesPublishItsPartsIsSeenToPublishThem() {
        Set<String> ways = waysInto(OrderedInterval.class);

        assertTrue(ways.contains("public OrderedInterval(Endpoint, Endpoint)"),
                () -> "the walk did not find the way in a product publishes, and would report a"
                        + " reading closed whatever the reading did: " + ways);
    }
}
