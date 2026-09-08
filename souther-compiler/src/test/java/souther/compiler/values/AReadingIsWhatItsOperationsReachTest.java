package souther.compiler.values;

import org.junit.jupiter.api.Test;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading is in one of the states its operations reach, and the parts are not a way in.
 *
 * <p>What each of these holds is several parts that state relations to each other — a whole that
 * holds nothing is not a position that holds nothing, the alternatives are never an empty union, a
 * promise is about the blocks every alternative agrees on. None of those is a property of one part,
 * so a caller handed the parts side by side can write down a combination nothing read, and nothing
 * says so. What holds the relations up is that the operations are the only things that make one.
 *
 * <p>So the propositions are about the ways in. A reading answers with a reading — that is what an
 * algebra is — and what is asked of each of those is that it composes or narrows one, rather than
 * taking the parts and handing the reading back. The second shape is the one that lets a state be
 * asserted of parts nothing put together, and it is also the one that lets a fact live in an
 * operation rather than in the value: remove the operation and the way to say the fact is gone with
 * it.
 *
 * <p><b>Nothing outside the type can mint one</b>, since every constructor is its own, so the ways
 * in are what the type declares and the list below is the whole of it. A way added tomorrow arrives
 * here as a difference and is a question somebody answers, rather than a hole nobody sees.
 */
class AReadingIsWhatItsOperationsReachTest {

    /**
     * Every way to get one, read off what declares them.
     *
     * <p>Constructors whatever their access, because a way in that is published is the thing being
     * asked about; methods unless they are private, because a private one is this type composing
     * itself. Read off the declaring types and not off the compiler: a reading's constructor is its
     * own, so nothing else can answer with one it did not get from here.
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
     * <p>A leaf, a rule read at a position, two positions held as one value or held apart, a rule
     * nothing could read, a conjunction, the same reading with more said about what a choice left
     * open, the same blocks under other names — and a description worked out, which is the one way
     * across from {@link PlannedValues} and does the work rather than being handed what the work
     * would have left.
     */
    @Test
    void everyWayToAReadingOfTheValuesIsSomethingReadingIt() {
        assertEquals(Set.of(
                        "private AdmissibleValues(Parts)",
                        "private AdmissibleValues(Held, Map, Standing, Map, ValueSet, boolean,"
                                + " Set, Set)",
                        "public top()",
                        "public at(Object, ValueSet)",
                        "public holdingAsOne(Object, Object)",
                        "public heldApart(Object, Object)",
                        "public unreadable(Set, UnreadReason)",
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
     * And realization answers with the reading beside what could not be built while making it,
     * which is why it is not in the list above.
     *
     * <p>The two are settled by one piece of work and are handed over together, so writing them
     * side by side is writing down a refusal no work made. One way in does the work; the other says
     * there was none to do, and says it by having nothing to say it about.
     */
    @Test
    void andADescriptionIsWorkedOutByTheReadingItComesTo() {
        assertEquals(Set.of(
                        "private Realized(AdmissibleValues, Set, List)",
                        "of(AdmissibleValues, Unbuilt)",
                        "public workedOut(AdmissibleValues)",
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
                        "private Settled(PlannedHeld, Map, Standing, Map, AdmittedPlan, boolean,"
                                + " Set, Set)",
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
                        "private OrderedIntervals(Map, boolean)",
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
     * And the reading is closed because it is closed, not because nothing here can see a way in.
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
