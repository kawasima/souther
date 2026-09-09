package souther.compiler.semantics;

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
 * Who reads an operator for what a connective composes, and how often.
 *
 * <p>Beside {@link souther.compiler.check.AnOperatorIsAskedWhatItPlacesInOnePlaceTest}, which holds
 * the recognition of a comparison to one call each. This holds the other recognition an operator is
 * read for, and for the same reason: below the point where a binary was recognised as a connective
 * a reader holds {@link ConditionJoin}, which has no case for an operator that composes nothing, and
 * that stands only while nothing down there goes back to the operator.
 *
 * <p><b>How often, and not only where.</b> A method is licensed here for the question it asks, and
 * a second call inside it is that same node recognised twice — a walk with an arm per composition
 * asks the operator once per arm, which is the arrangement this value exists to end, one method
 * further in. So the count is part of what is declared.
 *
 * <p>What is not counted is what a reader does with the answer afterwards. Denying it and asking
 * for it under a polarity are operations on the value, and a reader may do either as often as it
 * meets a negation — that is the value being carried, which is the point.
 *
 * <p>Read off the compiled classes, because what a method calls is what the class file says. A
 * reading of the sources would answer the same question a second way, and would not see a call
 * written inside a lambda as the method that holds it.
 */
class AnOperatorIsAskedWhatItComposesInOnePlaceTest {

    private static final String JOIN = "souther/compiler/semantics/ConditionJoin";

    /** A method that may read an operator for what it composes, how many times it does, and why. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_ASK = List.of(
            new Licence("souther.compiler.partition.Condition.of", 1,
                    "the one place a condition of a body becomes a shape, which carries the"
                            + " composition to every reader of that shape"),
            new Licence("souther.compiler.check.ClauseExpr.of", 1,
                    "the same for a clause, with the denial the tree is read under applied as the"
                            + " shape is made"),

            new Licence("souther.compiler.check.ClauseHelpers.shaped", 1,
                    "the shape an author wrote a clause in, which is walking into what composes"
                            + " both halves. The one place an authored part is recognised: which"
                            + " parts there are, where each stands among them, the clause with each"
                            + " expanded where it stands, and which subtree of a reading each of"
                            + " them became are all answers over that one shape"),
            new Licence("souther.compiler.check.FieldDomains.lambda$projection$7", 1,
                    "the clause that bounds a field, whose halves are answered beside it"),
            new Licence("souther.compiler.partition.ClauseStatements.walk", 1,
                    "what a behavior's clause states outright: one recognition, and both of what a"
                            + " connective can compose read off it. The comparisons a clause draws"
                            + " lines with and the predicates it tells sets of strings apart with"
                            + " are read off the statements this leaves, so neither reader is a"
                            + " second place the same `&&` is recognised in"));

    @Test
    void onlyARecognitionReadsAnOperatorForWhatItComposes() {
        assertEquals(declared(MAY_ASK), callsTo(JOIN, "of"),
                "a reader that has recognised a connective and asks the operator again has"
                        + " somewhere in it that could disagree with itself, and a second call in a"
                        + " licensed reader is that same recognition made twice. What each of these"
                        + " may ask, and why: " + why(MAY_ASK));
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
