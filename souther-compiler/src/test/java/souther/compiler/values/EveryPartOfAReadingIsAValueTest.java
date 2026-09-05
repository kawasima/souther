package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading is a value: nothing it holds can be changed after it is made.
 *
 * <p>What a query graph stops work on is whether an answer equals the one before it, so an answer
 * whose parts a caller can reach into is not an answer. A reading handed out with the map its maker
 * happened to be holding is one whose {@code equals} moves after it was compared, and — where it
 * admits nothing — one whose {@link AdmissibleValues#at} moves too.
 *
 * <p><b>Asked of every part, counted from the record itself.</b> The constructor copies the parts by
 * name, so a part added later is one it does not copy and nobody notices: the reading is still a
 * record, still equal to itself, and wrong only for a caller that kept the map it passed in. Written
 * out here as a list of parts to check, this test would be a copy of that constructor with the same
 * hole in it. Read off {@link Class#getRecordComponents}, a part added later is one it asks about.
 */
class EveryPartOfAReadingIsAValueTest {

    /**
     * A value to hand over, and every way its maker is still able to write into what it is made of.
     *
     * <p>The writes are a list because the value is a tree. A part of a reading is a map of
     * something to something, and a maker holding the map holds whatever is inside it as well — so
     * a reading that copied the map and kept the caller's lists is one whose {@code equals} still
     * moves after it was compared, and a pair naming one mutable thing has nowhere to say so.
     *
     * <p>Which is not hypothetical. The reasons a position was left standing with became a list
     * inside a map, and the copy of the inner one could be deleted with every test here passing.
     */
    private record Sample(Object value, List<Runnable> writes) {}

    /**
     * A value of whatever {@code declared} is, read as the shape it is declared to be, and every
     * way its maker could write into it afterwards.
     *
     * <p>Recursive over the declared type and not a table of the shapes met so far. A part of a
     * reading is a map of something to something, and what those are is part of what the part is —
     * answered by a rule per outer shape, {@code Map<?, List<Reason>>} is handed the value a
     * {@code Map<?, ValueSet>} wants and the constructor throws on the way in, so the part goes
     * unasked while the failure reads as this test's own. That happened once for the erased type
     * and would happen again one argument deeper.
     *
     * <p>The writes are gathered by the same recursion, so what may be written into is settled
     * wherever the shape is. Asked afterwards by the test, whether a value is one something can be
     * put into is a second reading of the same declared type, and the two agree only until one of
     * them meets a shape the other has not.
     *
     * <p>A type variable is a position a reading is generic over, and a string stands for one:
     * nothing here reads what a position is, only that two of them are told apart.
     */
    private static Sample sample(java.lang.reflect.Type declared) {
        Class<?> type = erased(declared);
        if (Map.class.isAssignableFrom(type)) {
            Sample key = sample(held(declared, 0));
            Sample value = sample(held(declared, 1));
            Map<Object, Object> out = new LinkedHashMap<>();
            out.put(key.value(), value.value());
            return new Sample(out, writes(key, value, () -> out.put("z", value.value())));
        }
        if (Collection.class.isAssignableFrom(type)) {
            Sample element = sample(held(declared, 0));
            Collection<Object> out = Set.class.isAssignableFrom(type)
                    ? new LinkedHashSet<>() : new ArrayList<>();
            out.add(element.value());
            return new Sample(out, writes(element, null, () -> out.add("z")));
        }
        if (type == boolean.class) {
            return new Sample(false, List.of());
        }
        if (type == ValueSet.class) {
            return new Sample(ValueSet.just(Value.text("5")), List.of());
        }
        if (type == UnreadReason.class) {
            return new Sample(UnreadReason.FORM_NOT_READ, List.of());
        }
        // What a rule left standing, which holds its own entries and hands none of them out. There
        // is nothing here for a caller to write into afterwards, and no way to ask it for one.
        if (type == Standing.class) {
            return new Sample(Standing.of(Set.of("a"), UnreadReason.FORM_NOT_READ), List.of());
        }
        // A block, which is what an answer of a reading is about. Its own members are a set this
        // holds, and it makes its own copy of them, so there is nothing here for a caller to write
        // into afterwards.
        if (type == Sameness.Block.class) {
            return new Sample(Sameness.Block.of("a"), List.of());
        }
        if (type == AdmissibleValues.Held.class) {
            return new Sample(AdmissibleValues.Held.Alternatives.of(
                    AdmissibleValues.Alternative.of(
                            new AdmissibleValues.Box<String>(Map.of()))), List.of());
        }
        // A position, which this reading is generic over. What one is is the caller's; that two of
        // them are not the same one is all this needs.
        if (declared instanceof java.lang.reflect.TypeVariable<?> || type == Object.class) {
            return new Sample("a", List.of());
        }
        throw new IllegalArgumentException("nothing here knows how to hand over a " + declared
                + ": a part of a reading was added in a shape this has no sample of");
    }

    /** What is inside, and this container itself. The inner ones first: a copy that stops at the
     *  outside passes the last of them and fails the first. */
    private static List<Runnable> writes(Sample inside, Sample also, Runnable here) {
        List<Runnable> out = new ArrayList<>(inside.writes());
        if (also != null) {
            out.addAll(also.writes());
        }
        out.add(here);
        return out;
    }

    /** What {@code declared} holds at argument {@code which}, or {@code Object} where it says. */
    private static java.lang.reflect.Type held(java.lang.reflect.Type declared, int which) {
        return declared instanceof java.lang.reflect.ParameterizedType parameterized
                ? parameterized.getActualTypeArguments()[which] : Object.class;
    }

    /** The class a declared type is written in terms of. */
    private static Class<?> erased(java.lang.reflect.Type declared) {
        return declared instanceof java.lang.reflect.ParameterizedType parameterized
                ? (Class<?>) parameterized.getRawType()
                : declared instanceof Class<?> type ? type : Object.class;
    }

    /** Every part of one, and what was handed over for each, in the order the record declares. */
    private static List<Sample> handedOver(RecordComponent[] parts) {
        List<Sample> out = new ArrayList<>();
        for (RecordComponent part : parts) {
            out.add(sample(part.getGenericType()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object made(Class<?> of, List<Sample> handed) throws Exception {
        Constructor<?>[] every = of.getDeclaredConstructors();
        Constructor<?> canonical = every[0];
        for (Constructor<?> each : every) {
            if (each.getParameterCount() == handed.size()) {
                canonical = each;
            }
        }
        Object[] args = handed.stream().map(Sample::value).toArray();
        canonical.setAccessible(true);
        return canonical.newInstance(args);
    }

    /**
     * Nothing a reading holds may be changed once it has been made.
     *
     * <p>Both halves, because one of them alone is passed by a copy that was wrapped and by a wrap
     * that was not copied: what comes out refuses to be written to, and what the maker kept is no
     * longer the same thing.
     *
     * <p>The second half is asked of every way the maker could still write, and not of the
     * outermost one. A part is a map of something to something, so a maker holding the map holds
     * whatever is inside it too — and a reading that copied the map and kept the caller's lists
     * passes a check that only ever writes into the map.
     */
    @Test
    void nothingAReadingHoldsMayBeChangedAfterItIsMade() throws Exception {
        RecordComponent[] parts = AdmissibleValues.class.getRecordComponents();
        assertTrue(parts.length > 0);

        for (int i = 0; i < parts.length; i++) {
            RecordComponent part = parts[i];
            if (!Collection.class.isAssignableFrom(part.getType())
                    && !Map.class.isAssignableFrom(part.getType())) {
                continue;
            }
            List<Sample> handed = handedOver(parts);
            Object reading = made(AdmissibleValues.class, handed);
            Object held = part.getAccessor().invoke(reading);

            assertThrows(UnsupportedOperationException.class, () -> add(held),
                    part.getName() + " may be written to after the reading was made");

            // What it holds, before its maker writes anything. As words, because what is being
            // asked is whether anything inside moved: a value compared with itself is equal to
            // itself however far in it changed.
            String said = String.valueOf(held);
            List<Runnable> writes = handed.get(i).writes();
            assertTrue(!writes.isEmpty(), part.getName() + " is a part with nothing to write into");
            for (Runnable write : writes) {
                write.run();
                assertEquals(said, String.valueOf(part.getAccessor().invoke(reading)),
                        part.getName() + " moved when the maker wrote into what it was made of");
            }
        }
    }

    /** And the same of what an alternative holds, which is a value for the same reason. */
    @Test
    void andNorMayAnAlternative() {
        Map<String, ValueSet> mine = new LinkedHashMap<>();
        mine.put("a", ValueSet.just(Value.text("5")));
        AdmissibleValues.Box<String> box = AdmissibleValues.Box.at(mine);

        assertThrows(UnsupportedOperationException.class,
                () -> box.at().put(Sameness.Block.of("b"), ValueSet.ANY));
        mine.put("b", ValueSet.just(Value.text("6")));
        assertEquals(Set.of(Sameness.Block.of("a")), box.at().keySet(),
                "what was said is what was said then");

        Set<AdmissibleValues.Alternative<String>> boxes = new LinkedHashSet<>();
        boxes.add(AdmissibleValues.Alternative.of(box));
        AdmissibleValues.Held.Alternatives<String> held =
                AdmissibleValues.Held.Alternatives.of(boxes, AsACompilationAllows.forAdmittedValues()).held();

        boxes.add(AdmissibleValues.Alternative.at(Map.of("b", ValueSet.ANY)));
        assertEquals(1, held.boxes().size(), "the alternatives are the ones it was made of");
    }

    @SuppressWarnings("unchecked")
    private static void add(Object collection) {
        if (collection instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).put("z", ValueSet.ANY);
            return;
        }
        ((Set<Object>) collection).add("z");
    }
}
