package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every value a reduction's answer is made of says how it is written.
 *
 * <p>What these values hold unordered is kept in the order it arrived, and two of them that are
 * equal were built two ways — so one that let the order it was built in be written out would show
 * what its own equality says it is not. Which order it is written in is settled where it is written
 * ({@code InOneOrder}), and a value that holds something unordered has to say so.
 *
 * <p><b>Asked of the values an answer is made of, and found by walking them.</b> A rule kept as a
 * list of the values somebody remembered is a rule the next one is written without: the value that
 * went unwritten here was reached through a reading, which is a value this walk reaches and a list
 * did not. So the walk starts at what a reduction answers with and takes what each of them holds,
 * to the end.
 *
 * <p>It stops where these values do. A value that names something declared elsewhere is a value
 * about that thing, and how a symbol or a source is written out is that package's own question.
 */
class EveryValueAnAnswerIsMadeOfSaysHowItIsWrittenTest {

    /** A relation and what a reader of one is answered with, which is where the walk starts. */
    private static final List<Class<?>> ANSWERED_WITH = List.of(
            Apartness.class, Apartness.Reduction.class, Closure.class, Refusal.class,
            Domains.class);

    /** Where these values are, which is where the walk stops. */
    private static final String HERE = Apartness.class.getPackageName();

    /**
     * Every value the answer is made of that holds something unordered writes itself out.
     *
     * <p>A record that does not is written out by the one Java gives it, which is its components as
     * they are held — and what a set of blocks holds them as is the order they were added in.
     */
    @Test
    void everyValueThatHoldsSomethingUnorderedWritesItselfOut() {
        List<String> unsaid = new ArrayList<>();
        for (Class<?> made : everythingAnAnswerIsMadeOf()) {
            if (holdsSomethingUnordered(made) && !writesItselfOut(made)) {
                unsaid.add(made.getSimpleName());
            }
        }
        assertEquals(List.of(), unsaid.stream().sorted().toList(),
                "each of these holds something no order of which is part of it, and is written out"
                        + " by what Java gives a record: its components as they are held");
    }

    /** And the walk reaches what it is about, which is what says the answer above is about
     *  something. */
    @Test
    void andTheWalkReachesWhatAnAnswerIsMadeOf() {
        Set<Class<?>> made = everythingAnAnswerIsMadeOf();

        assertFalse(made.isEmpty());
        for (Class<?> reached : List.of(Lacks.class, RelationalLack.TooFewValuesBetweenThem.class,
                RelationalEvidence.class, Provenance.class, Provenance.Removal.class,
                Domains.class, Admits.These.class, Apartness.Edge.class, Sameness.Block.class)) {
            assertEquals(true, made.contains(reached),
                    reached.getSimpleName() + " is what an answer is made of");
        }
    }

    /**
     * Every value reached from what a reduction answers with, by what each of them holds.
     *
     * <p>Through the arms of a sealed value as well as through what a record holds, since which arm
     * a reader has is the value itself. And through what a collection holds, which is where a
     * reading's blocks and the values each is left are.
     */
    private static Set<Class<?>> everythingAnAnswerIsMadeOf() {
        Set<Class<?>> found = new LinkedHashSet<>();
        Deque<Class<?>> asking = new ArrayDeque<>(ANSWERED_WITH);
        while (!asking.isEmpty()) {
            Class<?> made = asking.removeFirst();
            if (!made.getPackageName().equals(HERE) || !found.add(made)) {
                continue;
            }
            for (Class<?> arm : made.getPermittedSubclasses() == null
                    ? new Class<?>[0] : made.getPermittedSubclasses()) {
                asking.add(arm);
            }
            for (Type held : whatItHolds(made)) {
                asking.addAll(namedIn(held));
            }
        }
        return found;
    }

    /** What one value holds: a record's components, or the fields a class of its own keeps. */
    private static List<Type> whatItHolds(Class<?> made) {
        List<Type> out = new ArrayList<>();
        if (made.isRecord()) {
            for (RecordComponent each : made.getRecordComponents()) {
                out.add(each.getGenericType());
            }
            return out;
        }
        for (Field each : made.getDeclaredFields()) {
            if (!Modifier.isStatic(each.getModifiers())) {
                out.add(each.getGenericType());
            }
        }
        return out;
    }

    /** Every class a type names, including the ones a collection holds. */
    private static List<Class<?>> namedIn(Type held) {
        List<Class<?>> out = new ArrayList<>();
        switch (held) {
            case Class<?> it -> out.add(it);
            case ParameterizedType it -> {
                out.addAll(namedIn(it.getRawType()));
                for (Type each : it.getActualTypeArguments()) {
                    out.addAll(namedIn(each));
                }
            }
            case WildcardType it -> {
                for (Type each : it.getUpperBounds()) {
                    out.addAll(namedIn(each));
                }
            }
            case TypeVariable<?> _ -> { }
            default -> { }
        }
        return out;
    }

    /** Whether a value holds something whose order is not part of what it is. */
    private static boolean holdsSomethingUnordered(Class<?> made) {
        for (Type held : whatItHolds(made)) {
            for (Class<?> each : namedIn(held)) {
                if (Collection.class.isAssignableFrom(each) || Map.class.isAssignableFrom(each)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether it says how it is written, rather than being written out by what Java gives it.
     *
     * <p>Read off the class file and not off the declared methods. A record has a {@code toString}
     * either way — the one it is given is a method like any other to a reader of the declarations,
     * and it is the one this is about. What tells them apart is that the given one is written as a
     * call to what puts a record's components in a line, so that is what is looked for.
     */
    private static boolean writesItselfOut(Class<?> made) {
        for (MethodModel method : classFileOf(made).methods()) {
            if (!method.methodName().equalsString("toString")) {
                continue;
            }
            return method.code().stream()
                    .flatMap(code -> code.elementStream())
                    .noneMatch(EveryValueAnAnswerIsMadeOfSaysHowItIsWrittenTest::putsInALine);
        }
        return false;
    }

    /** Whether one step of a method is the call that puts a record's components in a line. */
    private static boolean putsInALine(CodeElement step) {
        return step instanceof InvokeDynamicInstruction it
                && it.bootstrapMethod().kind() == DirectMethodHandleDesc.Kind.STATIC
                && it.bootstrapMethod().owner().displayName().equals("ObjectMethods");
    }

    private static ClassModel classFileOf(Class<?> made) {
        try (InputStream read = made.getResourceAsStream(
                "/" + made.getName().replace('.', '/') + ".class")) {
            return ClassFile.of().parse(read.readAllBytes());
        } catch (IOException unread) {
            throw new UncheckedIOException(unread);
        }
    }
}
