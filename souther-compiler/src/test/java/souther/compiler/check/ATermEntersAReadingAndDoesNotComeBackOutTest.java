package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A term may enter a reading. Nothing takes one back out.
 *
 * <p>{@link TermMeaning} is what a caller of a behavior depends on, and two of them are equal where
 * the terms they hold say the same thing — whatever the places on those terms are. That holds only
 * for as long as nothing hands the term over: two readings the store has called one answer would
 * then be two a reader can tell apart, which is the thing an answer is not allowed to be
 * ({@link souther.compiler.query.Db}).
 *
 * <p>So the asymmetry is what is checked, and not the absence of one accessor. A term goes in
 * through {@code of}; what comes out is another reading, or what the predicates made of one. Written
 * as "no method returns a {@code Core}", the check would pass the day someone writes
 * {@code void inspect(Consumer<Core>)} or {@code <T> T read(Function<Core, T>)} — a term handed out
 * through the parameter list rather than the answer. So what is written down is the operations there
 * are, and anything else is a line to be added here with what it is for.
 *
 * <p>Nothing here reads the field list for its own sake: a field nothing outside the class can reach
 * is not a way out. What is asked of the fields is only that none of them is reachable.
 */
class ATermEntersAReadingAndDoesNotComeBackOutTest {

    /**
     * Every operation a reading has, and what each is for.
     *
     * <p>The signature and not only the name, so that a second {@code substituted} answering a term
     * is a line to be added here rather than one that slips through under a name already written
     * down.
     */
    private static final Set<String> OPERATIONS = new TreeSet<>(Set.of(
            "TermMeaning of(Core)"
                    + " — a term read for what it says, which is the way in",
            "TermMeaning substituted(Map)"
                    + " — the same reading with a call's arguments put in, which is a reading",
            "Predicates.Owed assumedBy(Predicates,Denotations,boolean)"
                    + " — what the predicates make of it where it is taken as holding",
            "boolean equals(Object)"
                    + " — two readings are equal where the terms they hold say the same thing",
            "int hashCode()"
                    + " — of the same projection equals compares",
            "String toString()"
                    + " — what it read, for whoever is looking at two that differ"));

    @Test
    void theOperationsOnAReadingAreTheOnesWrittenDown() {
        Set<String> found = new TreeSet<>();
        for (Method method : TermMeaning.class.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            found.add(said(method));
        }

        assertEquals(named(OPERATIONS), found,
                "an operation on a reading is a way for what it holds to leave it, so each one is"
                        + " written down with what it is for");
    }

    @Test
    void nothingAReadingHoldsIsReachable() {
        List<String> reachable = new ArrayList<>();
        for (Field field : TermMeaning.class.getDeclaredFields()) {
            if (!Modifier.isPrivate(field.getModifiers()) && !field.isSynthetic()) {
                reachable.add(field.getName());
            }
        }

        assertEquals(List.of(), reachable,
                "a reading holds the term it is of, and a field anything else can read is that term"
                        + " handed over without an operation to be written down");
    }

    /** The operation as this names one: what it answers, what it is called, and what it takes. */
    private static String said(Method method) {
        StringBuilder out = new StringBuilder(simple(method.getReturnType()));
        out.append(' ').append(method.getName()).append('(');
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(simple(params[i].getType()));
        }
        return out.append(')').toString();
    }

    private static String simple(Type type) {
        String name = type.getTypeName();
        return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
    }

    /** The written-down operations, less what each is for. */
    private static Set<String> named(Set<String> written) {
        Set<String> out = new TreeSet<>();
        for (String each : written) {
            out.add(each.substring(0, each.indexOf(" —")));
        }
        return out;
    }
}
