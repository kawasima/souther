package souther.compiler.partition;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Where a written value has a part is asked by the one walk that writes values.
 *
 * <p>{@code BehaviorInputs.stepWrittenValue} answers one of the two relations a path step stands
 * for: where a value written at a position has the part a step names. The other is what a value
 * standing there may be read as, and the two are one answer at a record and part at a sum whose
 * cases share a spread — a name every case spreads is readable at the sum and no row writes
 * anything at it.
 *
 * <p>So a reader that wants the second and takes this one reads no value at every name a model
 * reads through a sum. That is what the walk over a row's values did, and nothing said so: the
 * census in {@code NoQuestionAboutAShapeIsAnsweredOutOfAnothersAnswerTest} leaves out the class
 * that declares an answer and everything nested in it, which is right for the question it asks —
 * whether the answer left its owner — and blind to the two readers that live inside
 * {@link BehaviorInputs}. One of them wrote the defect.
 *
 * <p><b>So this counts methods and not classes.</b> The two callers were in one class file and a
 * class-level count cannot tell them apart. Read off what javac made rather than off the sources,
 * for the reason every check here is: a call is in the code however it was spelled.
 */
class OnlyTypeAtWrittenPathStepsIntoAWrittenValueTest {

    private static final String OWNER = "souther.compiler.partition.BehaviorInputs";

    /** The same class as {@link #OWNER}, as a method handle spells its owner. */
    private static final String SHORT = "BehaviorInputs";

    private static final String ANSWERS = "stepWrittenValue";

    /** The walk of it, which is the whole relation rather than one step of it. */
    private static final String WALKS = "typeAtWrittenPath";

    /**
     * The one method that reads a path as where a value goes.
     *
     * <p>With the descriptor, so that a second method of the name would be a second entry rather
     * than the same one seen twice.
     */
    private static final String COMPOSES =
            OWNER + "#" + WALKS + "(Lsouther/compiler/inputs/TermPath;)"
                    + "Lsouther/compiler/types/Type;";

    /**
     * And it is the only one, wherever it is written.
     *
     * <p>The walk that reads a row at a position takes its own steps ({@code BehaviorInputs
     * .Standing.step}) and appears here if it ever stops. What it would look like is this test
     * naming a second method — which is the reading of a row deciding where a value would be
     * written, one relation standing in for the other again.
     */
    @Test
    void oneMethodAsksWhereAWrittenValueHasItsParts() {
        Set<String> asks = callersOf(ANSWERS);

        assertFalse(asks.isEmpty(),
                "nothing asks where a written value has its parts at all; this check is reading no"
                        + " calls");
        assertEquals(Set.of(COMPOSES), asks,
                "a path is read as where a value goes in the one place that composes one, and a"
                        + " second reader is the written relation standing in for the read one");
    }

    /**
     * And the walk that takes those steps is asked where a value is composed, and nowhere else.
     *
     * <p>The step is the thing this began about and it is not the whole of what has to be shut. A
     * reader wanting to know what a path names could call the walk rather than the step, and the
     * count above would go on naming one caller while a reading of a row was again being answered
     * out of where a value is written. At a field that would fail the reading's own law; at an
     * element or a narrowing it would not, because the two relations agree there today — which is
     * exactly the agreement that is a law and not a reason to share.
     *
     * <p>So what is named here is on the composing side, and being on it is what the entry is for.
     * {@code Generator.edgeAt} takes the root a value is to be written at and hands what it finds
     * to the composition; a method that arrived here having read a row would be the same category
     * error one call further out.
     */
    @Test
    void theWalkIsAskedWhereAValueIsComposed() {
        Set<String> asks = callersOf(WALKS);

        assertFalse(asks.isEmpty(),
                "nothing asks where a value at a path is written at all; this check is reading no"
                        + " calls");
        assertEquals(Set.of("souther.compiler.partition.Generator#edgeAt("
                        + "Lsouther/compiler/partition/MeasuredInput;"
                        + "Lsouther/compiler/partition/RealizationTarget;"
                        + "Lsouther/compiler/numeric/Place;Z"
                        + "Lsouther/compiler/inputs/SearchRegion;)"
                        + "Lsouther/compiler/partition/Generator$Edge;"), asks,
                "where a value is written is asked where one is composed, and a second asker is a"
                        + " reader answering its own question out of that one");
    }

    /** Every production method that reaches {@code answer} on the owner, however it spelled it. */
    private static Set<String> callersOf(String answer) {
        Set<String> asks = new TreeSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.');
            for (var method : model.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                for (var element : code) {
                    if (reaches(element, answer)) {
                        asks.add(from + "#" + method.methodName().stringValue()
                                + method.methodType().stringValue());
                    }
                }
            }
        }
        return asks;
    }

    /**
     * And there is one of it to ask, so the count above is the whole of the rule.
     *
     * <p>An overload beside it would be a second answer under one name: the count would go on
     * naming one caller while two questions were being answered, and which of them a caller meant
     * would be settled by the types it happened to hold.
     */
    @Test
    void thereIsOneAnswerUnderThatName() {
        Set<String> declared = new TreeSet<>();
        ClassModel model = compiled(OWNER);
        for (var method : model.methods()) {
            if (method.methodName().stringValue().equals(ANSWERS)) {
                declared.add(method.methodType().stringValue());
            }
        }

        assertEquals(Set.of("(Lsouther/compiler/inputs/TermPath$Step;Lsouther/compiler/types/Type;"
                        + "Lsouther/compiler/check/Symbols;)Lsouther/compiler/types/Type;"),
                declared,
                "where a written value has a part is one question, so it is answered by one method");
    }

    /**
     * Whether this piece of a method's code reaches the answer, however it was written.
     *
     * <p>Two encodings and they are one reading. A call written as a call is an invocation naming
     * the method; a method reference is an {@code invokedynamic} whose bootstrap carries the same
     * method as a handle, and the name of the method appears nowhere in the instruction. Read for
     * the first alone, {@code BehaviorInputs::stepWrittenValue} handed to a {@code map} would put
     * the written relation back inside the reading walk and this would go on naming one caller.
     */
    private static boolean reaches(java.lang.classfile.CodeElement element, String answer) {
        if (element instanceof InvokeInstruction call) {
            return names(call.owner().asInternalName().replace('/', '.'),
                    call.name().stringValue(), answer);
        }
        if (!(element instanceof InvokeDynamicInstruction made)) {
            return false;
        }
        for (ConstantDesc argument : made.bootstrapArgs()) {
            if (argument instanceof DirectMethodHandleDesc handle
                    && names(handle.owner().displayName(), handle.methodName(), answer)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a call names the answer. The owner by its display name where a handle carries it and
     *  by its binary name where an instruction does, which are the same name for a nested class. */
    private static boolean names(String owner, String method, String answer) {
        return (owner.equals(OWNER) || owner.equals(SHORT)) && method.equals(answer);
    }

    private static ClassModel compiled(String binaryName) {
        return WhatWasCompiled.compiled().find(binaryName)
                .orElseThrow(() -> new IllegalStateException(
                        binaryName + " is not a class this module compiled"));
    }
}
