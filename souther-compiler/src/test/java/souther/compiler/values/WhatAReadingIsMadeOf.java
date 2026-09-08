package souther.compiler.values;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * The parts a reading is made of, found where the reading keeps them.
 *
 * <p>A reading holds one thing, and that thing is a record of its parts — which is what makes the
 * list of parts, the equality of two readings and the order several of them are put in one
 * declaration rather than three. So a proposition about every part finds them by asking what the
 * reading is made of, and not by being handed a list beside it: a list written here is one a part
 * added later is missing from, which is the failure these propositions are about.
 *
 * <p>Read off the field and not off a name. The parts are the reading's own and are named nowhere
 * else, so what is asked here is what a reading holds — a class that came to hold two things, or to
 * hold something that is not a record of parts, is one nothing here can answer about, and it says
 * so rather than answering about the first thing it finds.
 */
final class WhatAReadingIsMadeOf {

    private WhatAReadingIsMadeOf() {
    }

    /** The parts of {@code reading}, in the order they are declared. */
    static RecordComponent[] of(Class<?> reading) {
        return held(reading).getRecordComponents();
    }

    /** The record a reading keeps its parts in, which is the type of the one thing it holds. */
    static Class<?> held(Class<?> reading) {
        List<Field> mine = new ArrayList<>();
        for (Field each : reading.getDeclaredFields()) {
            if (!Modifier.isStatic(each.getModifiers())) {
                mine.add(each);
            }
        }
        if (mine.size() != 1 || !mine.getFirst().getType().isRecord()) {
            throw new IllegalStateException(reading.getSimpleName() + " holds " + mine
                    + ", and a reading holds one record of the parts it is made of. Whatever it"
                    + " holds now, that is where its parts, its equality and the order several of"
                    + " them are put came from, and they have to come from one place");
        }
        return mine.getFirst().getType();
    }
}
