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
 * A place is asked for its number where a number is what has to be written, and nowhere else.
 *
 * <p>The whole of the typed vocabulary rests on the number being a thing a caller cannot get hold
 * of. A number is a place only under the numbering that handed it out, so a caller holding a bare
 * {@code int} can pair it with anything — another plan's site, another family's, one nothing
 * issued — and no later check can tell what it did from a right answer. That is the defect this
 * numbering exists to close, and it comes back the moment a reader takes the number out.
 *
 * <p>What is left is the one place a number is the answer: the call the emitter writes into a
 * probed class. A probed class is handed an {@code int} because that is what {@code invokestatic}
 * carries, and it has no numbering to ask anything of — so the emitter turns the place back into a
 * number, and the check beside it that says every place the plan named was written is asking the
 * same question in the same words.
 *
 * <p><b>Held here rather than said in a comment.</b> {@link RunSite#raw()} says it is the one way
 * out of the typed vocabulary; a sentence saying so is true of the code that was there when it was
 * written, and the next reader that wants a number has nothing to stop it. What stops it is this.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it — a call written inside a lambda belongs to the lambda, and
 * this says so.
 *
 * <p><b>What it does not see, said rather than left to be found.</b> The tests are not in
 * {@code target/classes}: a fixture comparing what two numberings made of one number is standing in
 * for a reader that has both, which is not something inside the compiler. And what is read is this
 * module's classes — a reader written in the CLI or the language server would not be counted here.
 * Both are things to be told about rather than things this can be widened to cover.
 */
class OnlyWhatWritesTheCallAsksAPlaceForItsNumberTest {

    /** The three ways a place can be asked: through either family, or through what they share. */
    private static final Set<String> A_PLACE = Set.of(
            "souther/compiler/coverage/RunSite",
            "souther/compiler/coverage/ArmProbe",
            "souther/compiler/coverage/ComparisonEmissionSite");

    /** A method that may ask, how many times it does, and why it is one of the ones that does. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_ASK = List.of(
            new Licence("souther.compiler.codegen.BodyGen.lambda$comparisonProbe$0", 2,
                    "where the call is written: a probed class is handed the number because that is"
                            + " what the instruction carries, and it has no numbering to ask what"
                            + " the number addresses. Twice for the one act — the emitter records"
                            + " that it wrote this number, and writes it"),
            new Licence("souther.compiler.codegen.CodegenContext.plannedButNotEmitted", 2,
                    "the emitter's own check that every place the plan named got a call written"
                            + " for it, which is that same act asked the other way round and so is"
                            + " asked in the same words"));

    @Test
    void onlyTheEmitterTakesTheNumberOutOfAPlace() {
        assertEquals(declared(), asked(),
                "a number taken out anywhere else is a number a caller can pair with a place it"
                        + " was not issued for, and nothing downstream can tell that from a right"
                        + " answer. What may ask, and why: " + why());
    }

    private static Map<String, Integer> declared() {
        Map<String, Integer> out = new TreeMap<>();
        MAY_ASK.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why() {
        Map<String, String> out = new LinkedHashMap<>();
        MAY_ASK.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler asks a place for its number. */
    private static Map<String, Integer> asked() {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && A_PLACE.contains(call.owner().asInternalName())
                            && call.name().stringValue().equals("raw")) {
                        calls.merge(from + "." + method.methodName().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }
}
