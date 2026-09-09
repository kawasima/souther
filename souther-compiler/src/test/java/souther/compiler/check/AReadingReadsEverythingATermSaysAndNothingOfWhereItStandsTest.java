package souther.compiler.check;

import souther.compiler.WhatWasCompiled;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ReferenceOrigin;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A reading reads everything a term says, and nothing of where it stands.
 *
 * <p>Two things a reading has to be, and only one of them is the one this issue was about. A reading
 * that read where a term stands makes a caller depend on an edit it cannot see. A reading that left
 * out something the term says makes two terms that say different things one dependency, and the
 * store then stops the work of everything that read the one it kept — which is the worse of the two,
 * and the one a check written only about what a reading ignores would never fail on.
 *
 * <p>Neither is held by the switch being over a sealed type. That says a node kind added later
 * arrives as a compile error; it says nothing about a component added to a kind that is already
 * there. So the population is the components themselves, as the record declares them, and each is
 * asked which of the two it is by its type. What says where a node stands is the position every node
 * carries, the occurrence a comparison is of the model, the construction a written temporal was
 * spelled with, and the two values a fork and a kept call hold their pairs in. Everything else is
 * what the term says.
 *
 * <p>Read off the compiled projection rather than off its text: which accessors it calls is what the
 * class file says, and reading the source would be answering the same question a second way. An
 * accessor called anywhere in the walk counts, because a component is read either by going into what
 * it holds or by putting it among what is compared, and both are reading it.
 */
class AReadingReadsEverythingATermSaysAndNothingOfWhereItStandsTest {

    /** The reading whose identity this is about. */
    private static final String THE_READING = "souther.compiler.check.TermMeaning";

    /** What a component of this type says is where the node stands, and not what it says. */
    private static final Set<Class<?>> WHERE_IT_STANDS = Set.of(
            SourcePos.class,
            ConstructOccurrence.class,
            ApplicationOrigin.class,
            ReferenceOrigin.class,
            Core.ForkPlace.class,
            Core.KeptCallPlace.class);

    /**
     * The records a reading walks: every kind of term, and the three a term holds that are not terms
     * themselves.
     *
     * <p>The kinds come from the language's own list of what a term can be, so one added later is
     * here without anybody adding it. The other three are named because nothing declares them as a
     * family — an arm, a field's value and a departure are parts of the three kinds that have them.
     */
    private static List<Class<?>> walked() {
        List<Class<?>> out = new ArrayList<>(List.of(Core.class.getPermittedSubclasses()));
        out.add(Core.Case.class);
        out.add(Core.FieldValue.class);
        out.add(Core.ElseArm.class);
        return out;
    }

    @Test
    void everythingATermSaysIsRead() {
        Set<String> unread = new TreeSet<>();
        Set<String> called = componentsReadByTheIdentity();
        for (Class<?> kind : walked()) {
            for (RecordComponent component : kind.getRecordComponents()) {
                if (!WHERE_IT_STANDS.contains(component.getType())
                        && !called.contains(kind.getSimpleName() + "." + component.getName())) {
                    unread.add(kind.getSimpleName() + "." + component.getName());
                }
            }
        }

        assertEquals(Set.of(), unread,
                "a term saying this differently is a term a reading calls the same, and everything"
                        + " that read the one the store kept goes on holding it");
    }

    @Test
    void andNothingOfWhereItStandsIs() {
        Set<String> read = new TreeSet<>();
        Set<String> called = componentsReadByTheIdentity();
        for (Class<?> kind : walked()) {
            for (RecordComponent component : kind.getRecordComponents()) {
                if (WHERE_IT_STANDS.contains(component.getType())
                        && called.contains(kind.getSimpleName() + "." + component.getName())) {
                    read.add(kind.getSimpleName() + "." + component.getName());
                }
            }
        }

        assertEquals(Set.of(), read,
                "a caller depending on a reading that read this is a caller a blank line above the"
                        + " declaration reaches");
    }

    /**
     * And what it reads of a term are its components, so the two questions above are about all of
     * it.
     *
     * <p>A record's components are what it is made of; anything else on it is worked out from them,
     * and a reading that asked one of those would be reading whatever it happens to be derived from.
     * A comparison's {@code origin} is its occurrence's, so an identity calling it reads where the
     * comparison stands while nothing above sees a place component being read.
     */
    @Test
    void andWhatItReadsOfATermAreItsComponents() {
        Set<String> components = new TreeSet<>();
        for (Class<?> kind : walked()) {
            for (RecordComponent component : kind.getRecordComponents()) {
                components.add(kind.getSimpleName() + "." + component.getName());
            }
        }

        Set<String> derived = new TreeSet<>(componentsReadByTheIdentity());
        derived.removeAll(components);

        assertEquals(Set.of(), derived,
                "an identity that asks a term something worked out from its components is one the"
                        + " two questions above cannot see the answer of");
    }

    /** And the population is not empty, so what the questions above ask is asked of something. */
    @Test
    void bothKindsOfComponentAreThere() {
        int says = 0;
        int stands = 0;
        for (Class<?> kind : walked()) {
            for (RecordComponent component : kind.getRecordComponents()) {
                if (WHERE_IT_STANDS.contains(component.getType())) {
                    stands++;
                } else {
                    says++;
                }
            }
        }

        assertFalse(says == 0 || stands == 0,
                "one of the two kinds of component was not found at all, so one of the questions"
                        + " above was asked of nothing");
    }

    /**
     * Every component of a term the identity reads, by the record it is declared on.
     *
     * <p>From {@code equals} and {@code hashCode} and through the calls they make of their own
     * class, because what the identity reads is the question. Collected over the whole class
     * instead, a component that only some other operation reads would count as read — and a
     * component the identity leaves out is exactly the one that makes two readings the store called
     * one answer differently, whoever else reads it.
     */
    private static Set<String> componentsReadByTheIdentity() {
        ClassModel model = WhatWasCompiled.compiled().find(THE_READING)
                .orElseThrow(() -> new IllegalStateException(
                        THE_READING + " is not a class this module compiled"));
        Set<String> read = new LinkedHashSet<>();
        Set<String> walked = new LinkedHashSet<>();
        Deque<MethodModel> pending = new ArrayDeque<>();
        for (MethodModel method : model.methods()) {
            String name = method.methodName().stringValue();
            if (name.equals("equals") || name.equals("hashCode")) {
                pending.add(method);
            }
        }
        String self = model.thisClass().asInternalName();
        while (!pending.isEmpty()) {
            MethodModel method = pending.removeFirst();
            if (!walked.add(method.methodName().stringValue()
                    + method.methodType().stringValue())) {
                continue;
            }
            method.code().ifPresent(code -> code.forEach(element -> {
                if (element instanceof InvokeInstruction call) {
                    String owner = call.owner().asInternalName();
                    if (owner.equals(self)) {
                        for (MethodModel candidate : model.methods()) {
                            if (candidate.methodName().stringValue()
                                            .equals(call.name().stringValue())
                                    && candidate.methodType().stringValue()
                                            .equals(call.type().stringValue())) {
                                pending.add(candidate);
                            }
                        }
                    }
                    int nested = owner.lastIndexOf('$');
                    if (owner.startsWith("souther/compiler/core/Core") && nested >= 0) {
                        read.add(owner.substring(nested + 1) + "." + call.name().stringValue());
                    }
                }
            }));
        }
        return read;
    }

}
