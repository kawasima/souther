package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who reads an operator for what it places, and how often.
 *
 * <p>A reader below the point where a binary was recognised as a comparison holds
 * {@link ComparisonClaim}, which has no case for an operator that places nothing. That stands only
 * while nothing down there goes back to the operator: one call to {@link ComparisonPlacement#of}
 * hands back the wide answer, and whoever made it has a case to invent an answer for again — which
 * is how six readings came to answer for a comparison that was never one.
 *
 * <p><b>How often, and not only where.</b> A method is licensed here for the question it asks, and
 * a second call inside it is a second question wearing the first one's licence — a reading that
 * reaches the wide answer twice has somewhere in it that could disagree with itself. So the count
 * is part of what is declared, and a call added anywhere lands as a number that does not match.
 *
 * <p>What a count cannot say is who came through a door: a licence given to a method covers every
 * caller of it. So the doors are held apart by the language instead. The wide ones are the readers
 * that meet a binary nothing has recognised yet, and each is shut to a caller holding a comparison.
 * {@link souther.compiler.inputs.ComparedNumber#of} is package-private, which makes
 * {@code ComparedNumbers} the one way to it from outside and a body's binaries the one thing that
 * arrives; a reader holding a comparison takes {@code lineOf} and hands over the claim it already
 * has. {@code ConstEval.binary} is private, and the fold is the one thing that reaches it.
 *
 * <p>Read off the compiled classes, because what a method calls is what the class file says. A
 * reading of the sources would answer the same question a second way, and would not see a call
 * written inside a lambda as the method that holds it.
 */
class AnOperatorIsAskedWhatItPlacesInOnePlaceTest {

    private static final String PLACEMENT = "souther/compiler/check/ComparisonPlacement";

    /** A method that may read an operator for what it places, how many times it does, and why. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_ASK = List.of(
            new Licence("souther.compiler.check.Comparison.of", 1,
                    "the one place a node becomes a comparison, which is what carries the claim to"
                            + " every reader below it"),
            new Licence("souther.compiler.inputs.ComparedNumber.of", 1,
                    "reads any binary a walk met, so an operator that places nothing arrives here"
                            + " and is answered rather than excluded"),
            new Licence("souther.compiler.check.ConstEval.binary", 1,
                    "folds any binary an expression is written with, so an operator that places"
                            + " nothing arrives here too; what it placed is spent on the relation"
                            + " the fold answers and is handed to nobody"));

    @Test
    void onlyARecognitionReadsAnOperatorForWhatItPlaces() {
        assertEquals(declared(MAY_ASK), callsTo(PLACEMENT, "of"),
                "a reader below a recognised comparison that asks the operator again holds the"
                        + " wide answer, and has a case to invent an answer for; a second call in a"
                        + " licensed reader is a second question under the first one's licence."
                        + " What each of these may ask, and why: " + why(MAY_ASK));
    }

    private static Map<String, Integer> declared(List<Licence> licences) {
        Map<String, Integer> out = new TreeMap<>();
        licences.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why(List<Licence> licences) {
        Map<String, String> out = new LinkedHashMap<>();
        licences.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler calls {@code owner.name}. */
    private static Map<String, Integer> callsTo(String owner, String name) {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(owner)
                            && call.name().stringValue().equals(name)) {
                        calls.merge(from + "." + method.methodName().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }
}
