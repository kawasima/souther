package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A state a query answers with is a value, and its identity is everything it answers with.
 *
 * <p>The store decides whether anything downstream has to be asked again by comparing the answer it
 * recomputed against the one it held: equal means nothing changed, and nothing that read it is asked
 * again. So a state whose {@code equals} leaves out something a reader can get out of it is a state
 * the store cannot tell has changed — the reader keeps the old one, and nothing anywhere reports
 * that it did.
 *
 * <p>Written by hand, an identity is a copy of the field list, and a field added later is not in it.
 * That has happened twice: a settled module gained the recursions its clauses left standing, and a
 * prepared module gained the definitions minted for its rows, and both were added beside an identity
 * that went on comparing what it compared before. Neither is visible in a diff of the identity,
 * because the identity is what did not change.
 *
 * <p>So it is read off the compiled class rather than off the source: which fields a class has is
 * the class's own answer, and which of them {@code equals} reaches is what the method actually
 * does. Reaches, and not loads directly — an identity here is usually written of a projection, and
 * comparing what a state hands over as a tree covers everything that tree is built from. A field is
 * uncovered only where nothing {@code equals} reaches ever loads it.
 *
 * <p>Two kinds of field are not part of an identity and are named here rather than guessed at. A
 * {@code volatile} field is a worked-out answer kept beside the state — writing it changes nothing
 * anybody can observe, and it is derived from the fields that are covered. A field a record
 * declares is covered by the identity the record already has, so a record is not asked about at all.
 */
class AnAnswersIdentityCoversEverythingItAnswersWithTest {

    /** The package whose states the store compares, which is what this rule is about. */
    private static final String THE_CHECK = "souther.compiler.check";

    /** Where a state's identity may leave a field out, and why. Nothing else may. */
    private static final Map<String, String> ALLOWED = Map.of(
            "souther.compiler.check.Term.hash",
            "the hash is of the shape and the parts, and is compared first as a way of saying no",
            "souther.compiler.check.CheckSurface.operandMethods",
            "keyed on operand identity, over the very nodes the tree hands out — it says nothing"
                    + " the tree and the definitions built from it do not already say",
            "souther.compiler.check.DeclaredArgument.stands",
            "the type at a position is a fact the library settled about that position and not a"
                    + " second thing to tell two arguments apart by: two readings of one declaration"
                    + " that disagreed about it would be a binding that has come apart, not two"
                    + " arguments — the same reason a declared operation's arity is outside its"
                    + " identity");

    @Test
    void everyHandWrittenIdentityReadsEveryFieldItsStateHolds() {
        Map<String, String> uncovered = new TreeMap<>();
        int asked = 0;
        for (ClassModel model : WhatWasCompiled.compiled().inPackage(THE_CHECK)) {
            if (model.superclass().isPresent()
                    && model.superclass().get().asInternalName().equals("java/lang/Record")) {
                // A record's identity is written for it out of its components, and what such a one
                // covers is not a question about what anybody wrote.
                continue;
            }
            MethodModel equals = declaredEquals(model);
            if (equals == null) {
                continue;
            }
            asked++;
            Set<String> read = fieldsReadBy(model, equals);
            for (String field : instanceFieldsOf(model)) {
                String qualified = model.thisClass().asInternalName().replace('/', '.')
                        .replace('$', '.') + "." + field;
                if (!read.contains(field) && !ALLOWED.containsKey(qualified)) {
                    uncovered.put(qualified, "held but never read by `equals`");
                }
            }
        }
        assertFalse(asked == 0, "no hand-written identity was read at all, so this says nothing");
        assertEquals(Map.of(), uncovered,
                "a state that answers with something its identity leaves out is one the store"
                        + " cannot tell has changed, and every reader of it keeps the old answer");
    }

    /** The class's own {@code equals(Object)}, or null where it inherits one. */
    private static MethodModel declaredEquals(ClassModel model) {
        for (MethodModel method : model.methods()) {
            if (method.methodName().stringValue().equals("equals")
                    && method.methodType().stringValue().equals("(Ljava/lang/Object;)Z")) {
                return method;
            }
        }
        return null;
    }

    /**
     * Every field {@code equals} reaches, through the methods of its own class that it calls.
     *
     * <p>Following the calls, because an identity here is usually written of a projection rather
     * than of the fields: comparing what the state hands over as a tree covers everything the tree
     * is built from. A field is left out only where nothing {@code equals} reaches ever loads it.
     */
    private static Set<String> fieldsReadBy(ClassModel model, MethodModel equals) {
        Set<String> read = new LinkedHashSet<>();
        Set<String> walked = new LinkedHashSet<>();
        Deque<MethodModel> pending = new ArrayDeque<>();
        pending.add(equals);
        String self = model.thisClass().asInternalName();
        while (!pending.isEmpty()) {
            MethodModel method = pending.removeFirst();
            if (!walked.add(method.methodName().stringValue()
                    + method.methodType().stringValue())) {
                continue;
            }
            method.code().ifPresent(code -> code.forEach(element -> {
                if (element instanceof FieldInstruction field) {
                    read.add(field.name().stringValue());
                }
                if (element instanceof InvokeInstruction call
                        && call.owner().asInternalName().equals(self)) {
                    for (MethodModel candidate : model.methods()) {
                        if (candidate.methodName().stringValue().equals(call.name().stringValue())
                                && candidate.methodType().stringValue()
                                        .equals(call.type().stringValue())) {
                            pending.add(candidate);
                        }
                    }
                }
            }));
        }
        return read;
    }

    /**
     * The instance fields the state holds, less the ones an identity is not made of: a
     * {@code volatile} answer worked out from the others, and the synthetic references a nested
     * class carries.
     */
    private static List<String> instanceFieldsOf(ClassModel model) {
        List<String> fields = new ArrayList<>();
        model.fields().forEach(field -> {
            java.lang.reflect.AccessFlag[] flags =
                    field.flags().flags().toArray(new java.lang.reflect.AccessFlag[0]);
            boolean statik = false;
            boolean unstable = false;
            boolean synthetic = false;
            for (java.lang.reflect.AccessFlag flag : flags) {
                statik |= flag == java.lang.reflect.AccessFlag.STATIC;
                unstable |= flag == java.lang.reflect.AccessFlag.VOLATILE;
                synthetic |= flag == java.lang.reflect.AccessFlag.SYNTHETIC;
            }
            if (!statik && !unstable && !synthetic
                    && !field.fieldName().stringValue().startsWith("this$")) {
                fields.add(field.fieldName().stringValue());
            }
        });
        return fields;
    }

}
