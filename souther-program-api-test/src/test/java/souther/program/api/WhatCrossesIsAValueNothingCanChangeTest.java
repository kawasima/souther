package souther.program.api;

import souther.compiler.diag.SourcePos;
import souther.compiler.observe.Asserted;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.Mismatch;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.PathElement;
import souther.compiler.observe.Position;
import souther.compiler.observe.RowStatement;
import souther.compiler.observe.RowStatements;
import souther.compiler.observe.StoodIn;
import souther.compiler.observe.Verdict;
import souther.compiler.program.CheckedRow;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Nothing an output is handed can be changed by whoever holds it.
 *
 * <p>A snapshot is what a compile decided, and a value of it that could be reached into is one whose
 * meaning moves after it was taken: a row's expectation edited through the collection it was built
 * with makes the same program answer differently about the same row. So every part of it that a
 * caller could still be holding is copied as it is taken.
 *
 * <p><b>Asked of the vocabulary rather than of the parts that were noticed.</b> Written out as a
 * list of types to check, this would be a copy of the constructors with the same hole in it — and
 * the hole would be in whichever one was written next. The types are reached from what an output
 * holds, and every one of them that keeps a collection is asked; a part in a shape this has no
 * sample of stops the walk rather than being passed over, so a type added later is one somebody has
 * to answer for.
 */
class WhatCrossesIsAValueNothingCanChangeTest {

    private static final TypeSymbol.AtModule A_DATA =
            TypeSymbols.declared(new TypeKey("demo", "Receipt"));

    /** Every type an output reaches through what a row states, and what asking about one answers. */
    private static List<Class<?>> theVocabulary() {
        List<Class<?>> reached = new ArrayList<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>(List.of(CheckedRow.class, RowStatement.class,
                StoodIn.class,
                Expectation.class, Asserted.class, ObservedValue.class, Verdict.class,
                Mismatch.class, PathElement.class, Position.class));
        while (!pending.isEmpty()) {
            Class<?> here = pending.poll();
            if (!seen.add(here)) {
                continue;
            }
            reached.add(here);
            Class<?>[] arms = here.getPermittedSubclasses();
            if (arms != null) {
                pending.addAll(List.of(arms));
            }
            for (Class<?> nested : here.getDeclaredClasses()) {
                pending.add(nested);
            }
        }
        return reached;
    }

    @Test
    void everyValueAnOutputHoldsCopiesWhatItWasHanded() throws Exception {
        List<String> asked = new ArrayList<>();
        for (Class<?> each : theVocabulary()) {
            if (!each.isRecord() || !keepsACollection(each)) {
                continue;
            }
            asked.add(each.getSimpleName());
            heldApartFromWhatItWasHandedWith(each);
        }
        // Said out loud, so that a type that stops being asked about is a line to change rather
        // than a check that quietly walks fewer things than it used to.
        //
        // `Entry` is what a stand-in states one call to a dependency as: the arguments it answers
        // for are a list because a dependency takes as many as it declares, and an output holding
        // one asks what it answers by handing over as many.
        assertEquals(List.of("Mismatch", "Entry", "Built", "Elements", "Entries",
                        "Constructed", "Sequence", "Mapping"),
                asked, "what an output holds that keeps a collection");
    }

    /** And the one that is not a record answers the same way, through the one way of making it. */
    @Test
    void andSoDoesTheOneThatIsMadeByAsking() {
        List<ObservedValue> given = new ArrayList<>();
        given.add(new ObservedValue.Integer(1));
        RowStatement stated = RowStatements.read(List.of(), given,
                new Expectation.TheValue(new Asserted.Value(new ObservedValue.Integer(2))));
        given.add(new ObservedValue.Integer(3));

        assertEquals(1, ((RowStatement.Stated) stated).inputs().size(),
                "what a row states is copied as it is taken");
    }

    /**
     * One value, made with a collection the maker keeps, and changed after the fact.
     *
     * <p>Held apart by what it answers rather than by what it holds: two values built from the same
     * parts are equal, so the one built before the change and the one built after are equal only if
     * the change did not reach the first.
     */
    private static void heldApartFromWhatItWasHandedWith(Class<?> type) throws Exception {
        RecordComponent[] parts = type.getRecordComponents();
        List<Object> handed = new ArrayList<>();
        List<Collection<Object>> mutable = new ArrayList<>();
        for (RecordComponent part : parts) {
            Object sample = sampleFor(part.getType());
            handed.add(sample);
            if (sample instanceof Collection<?> collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> more = (Collection<Object>) collection;
                mutable.add(more);
            } else if (sample instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> more = (Map<Object, Object>) map;
                mutable.add(new ArrayList<>() {
                    @Override
                    public boolean add(Object what) {
                        more.put("added", new Asserted.Value(new ObservedValue.Integer(9)));
                        return true;
                    }
                });
            }
        }
        Constructor<?> canonical = canonicalOf(type, parts);
        canonical.setAccessible(true);
        // What it was made of, taken before anything changes. Two values built from the one set of
        // parts would both move where a part is kept rather than copied, and would go on being
        // equal to each other — which is the reading that says nothing.
        Object asItWasMade = canonical.newInstance(takenNow(handed));
        Object made = canonical.newInstance(handed.toArray());

        for (Collection<Object> more : mutable) {
            more.add(new ObservedValue.Integer(7));
        }
        assertEquals(asItWasMade, made,
                () -> type.getSimpleName() + " was changed by whoever handed its parts over");
        assertNotEquals(asItWasMade, canonical.newInstance(handed.toArray()),
                () -> type.getSimpleName() + " reads the same after the change as before it, so"
                        + " nothing here would have seen one");
    }

    /** The parts as they stand now, so that what is built from them is not built from what they
     *  become. */
    private static Object[] takenNow(List<Object> handed) {
        Object[] taken = new Object[handed.size()];
        for (int i = 0; i < taken.length; i++) {
            Object part = handed.get(i);
            taken[i] = switch (part) {
                case List<?> list -> new ArrayList<>(list);
                case Map<?, ?> map -> new LinkedHashMap<>(map);
                default -> part;
            };
        }
        return taken;
    }

    private static boolean keepsACollection(Class<?> type) {
        for (RecordComponent part : type.getRecordComponents()) {
            if (Collection.class.isAssignableFrom(part.getType())
                    || Map.class.isAssignableFrom(part.getType())) {
                return true;
            }
        }
        return false;
    }

    private static Constructor<?> canonicalOf(Class<?> type, RecordComponent[] parts)
            throws NoSuchMethodException {
        Class<?>[] taken = new Class<?>[parts.length];
        for (int i = 0; i < parts.length; i++) {
            taken[i] = parts[i].getType();
        }
        return type.getDeclaredConstructor(taken);
    }

    /**
     * A part to build one with, in a shape a caller could still be holding where it is a collection.
     *
     * <p>Refuses what it has no sample of, so a part in a new shape is a thing somebody says what to
     * hand over for rather than a part this walks past.
     */
    private static Object sampleFor(Class<?> type) {
        if (type == List.class) {
            List<Object> out = new ArrayList<>();
            out.add(new ObservedValue.Integer(1));
            return out;
        }
        if (type == Map.class) {
            Map<Object, Object> out = new LinkedHashMap<>();
            out.put("one", new Asserted.Value(new ObservedValue.Integer(1)));
            return out;
        }
        if (type == TypeSymbol.class || type == TypeSymbol.AtModule.class) {
            return A_DATA;
        }
        if (type == Asserted.Container.class) {
            return Asserted.Container.LIST;
        }
        if (type == boolean.class) {
            return true;
        }
        if (type == int.class) {
            return 1;
        }
        if (type == long.class) {
            return 1L;
        }
        if (type == String.class) {
            return "a";
        }
        if (type == Asserted.class) {
            return new Asserted.Value(new ObservedValue.Integer(1));
        }
        if (type == ObservedValue.class) {
            return new ObservedValue.Integer(1);
        }
        // What a row states of the answer, and the two grains of it an answer can be held to. A
        // comparison takes the second, so both shapes are met on the way across and each needs a
        // sample of its own — a row whose answer is owed states one and not the other.
        if (type == Expectation.class || type == Expectation.Asserts.class) {
            return new Expectation.TheCase(A_DATA);
        }
        if (type == Mismatch.Reason.class) {
            return Mismatch.Reason.VALUE;
        }
        if (type == Position.class) {
            return Position.at(Type.Prim.named("Int"));
        }
        if (type == Type.class) {
            return Type.Prim.named("Int");
        }
        if (type == ValueName.Behavior.class) {
            return new ValueName.Behavior("demo", "billFor");
        }
        if (type == SourcePos.class) {
            return new SourcePos(1, 1);
        }
        if (type == java.math.BigDecimal.class) {
            return java.math.BigDecimal.ONE;
        }
        throw new IllegalArgumentException("nothing here knows what to hand over for a " + type
                + ": a part of what crosses is in a shape this has no sample of");
    }
}
