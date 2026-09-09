package souther.compiler.coverage;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Whether a run was at a place is asked of the run, and not of the set it holds.
 *
 * <p>{@link AlignedObservation#lit} and {@link AlignedObservation#saw} refuse a place of another
 * numbering: what one numbering's run did at another's places is nothing, and an ordinary "no"
 * there says a row missed somewhere it was never asked about. Reached through
 * {@link AlignedObservation#arms} instead, the same question is a {@code Set.contains} and answers
 * that "no" — so the refusal holds only for as long as every reader asks the run rather than
 * reaching past it.
 *
 * <p><b>Which is not a reason to take the sets away.</b> Gathering what a run holds is a different
 * act from asking it about a place: a caller unioning the arms of every row is not naming a place
 * at all, and has nothing to be wrong about. So what is fixed is who may take the set, and each of
 * them says which of the two it is doing.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it — a call written inside a lambda belongs to the lambda, and
 * this says so.
 *
 * <p><b>What it does not see, said rather than left to be found.</b> The tests are not in
 * {@code target/classes}, and what is read is this module's classes: a reader written in the CLI or
 * the language server would not be counted here. Both are things to be told about rather than
 * things this can be widened to cover.
 */
class WhatARunSaysAboutAPlaceIsAskedOfTheRunTest {

    private static final String A_RUN = "souther/compiler/coverage/AlignedObservation";

    /** The two that hand the places over whole, rather than answering about one. */
    private static final Set<String> HANDS_THEM_OVER = Set.of("arms", "comparisons");

    /** A method that may take them, how many times it does, and why it is gathering rather than
     *  asking. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_GATHER = List.of(
            new Licence("souther.compiler.query.Adequacy.armsSeenIn -> arms", 1,
                    "the arms some row of the module reached, unioned over the rows: what it is"
                            + " building is a set of places, and it names none of its own to be"
                            + " wrong about"));

    @Test
    void whatTakesThePlacesWholeIsGatheringThemAndSaysSo() {
        assertEquals(declared(), taken(),
                "a reader that takes the set to ask whether one place is in it has gone round the"
                        + " refusal that makes an aligned run mean anything, and gets an ordinary"
                        + " no for a place of another numbering. What may take them, and why: "
                        + why());
    }

    private static Map<String, Integer> declared() {
        Map<String, Integer> out = new TreeMap<>();
        MAY_GATHER.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why() {
        Map<String, String> out = new LinkedHashMap<>();
        MAY_GATHER.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler takes a run's places whole. */
    private static Map<String, Integer> taken() {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(A_RUN)
                            && HANDS_THEM_OVER.contains(call.name().stringValue())) {
                        calls.merge(from + "." + method.methodName().stringValue()
                                + " -> " + call.name().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }
}
