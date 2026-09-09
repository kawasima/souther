package souther.compiler.coverage;

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
 * A plan is made where its bodies are numbered, and nowhere else in the compiler.
 *
 * <p>A plan holds parts that are only true together: a numbering of comparisons and the catalog
 * that issued their names, and what each number addresses beside the sites it was handed out for.
 * Its own constructor refuses the pairs it can see — a comparison the catalog never held, an
 * address list of another length — and there are agreements no constructor can see. What each
 * number is an address <em>of</em> is one: a list of the right length, of addresses of the right
 * places, is a value anybody could build and only the walk that handed the numbers out knows.
 *
 * <p>So what is fixed is who may put one together. One walk numbers a module's bodies and makes the
 * plan of them in the same breath, and a plan that exists is that walk's answer rather than parts a
 * caller believed went together.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it.
 *
 * <p><b>What it does not see, said rather than left to be found.</b> The tests are not in
 * {@code target/classes}: a fixture that hands the emitter a plan with one arm too many is standing
 * in for a numbering that got it wrong, which is the thing under test there and is not a second
 * answer inside the compiler. And what is read is this module's classes — a caller written in the
 * CLI or the language server would not be counted here, and those modules are built after this one,
 * so a walk over their output would read whatever the last build left.
 */
class WhatMakesAPlanIsWhatNumberedItTest {

    private static final String PLAN = "souther/compiler/coverage/CoverageSites$Plan";

    /** A method that may make one, how many times it does, and why it is one of the ones that
     *  does. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_MAKE = List.of(
            new Licence("souther.compiler.coverage.CoverageSites.asPlan", 1,
                    "the one walk of a module's bodies: it hands the numbers out and says what each"
                            + " addresses in the same breath, so the two cannot have been put"
                            + " together by anybody who believed they went together. Whether the"
                            + " numbering it reads them back under is decided here or was decided"
                            + " when the bodies were checked is the two ways in, and neither of"
                            + " them assembles a plan"),
            new Licence("souther.compiler.coverage.CoverageSites.Plan.<clinit>", 1,
                    "the plan of nothing, which is what a module the check has no answer for gets."
                            + " It numbers no place and addresses none, so its parts agree by"
                            + " there being none of them"));

    @Test
    void onlyTheWalkThatNumberedThemMakesAPlanOfABodysArms() {
        assertEquals(declared(), made(),
                "a plan put together anywhere else is parts a caller believed went together, and"
                        + " the agreements between them are what a reader of a run rests on."
                        + " What may make one, and why: " + why());
    }

    private static Map<String, Integer> declared() {
        Map<String, Integer> out = new TreeMap<>();
        MAY_MAKE.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why() {
        Map<String, String> out = new LinkedHashMap<>();
        MAY_MAKE.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler makes one. */
    private static Map<String, Integer> made() {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(PLAN)
                            && call.name().stringValue().equals("<init>")) {
                        calls.merge(from + "." + method.methodName().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }
}
