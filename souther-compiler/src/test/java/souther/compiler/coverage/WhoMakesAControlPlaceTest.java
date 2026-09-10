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
 * A control place is made by the plan whose answer it is, and by nothing else.
 *
 * <p>Both halves of one of these are the plan's: which alternative of the tree it is, and what the
 * plan worked out about observing it and reporting on it. Nothing checks the two against each other
 * afterwards, and nothing could — the arm's fork and the arm's probe are answered by different
 * things, and so are the comparison and the address a run through it is recorded at. What holds
 * them together is that one walk settles both at once.
 *
 * <p><b>The docs say so and the types cannot.</b> {@code ControlPlace} is public and its arms are
 * records, so their canonical constructors are public and a caller can put any comparison beside
 * any address. Downstream reads the pair as the plan's answer: a claim is made at the address and a
 * condition is written about the comparison, so a pair nothing issued together sends a run to be
 * recorded at one comparison and reported about another. {@link ComparisonEmissionSite} keeps its
 * own constructor to its package for this reason; a record cannot, so the maker is counted here
 * instead.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it — and a place built through a method reference is a call like
 * any other, which no reading of the text would have found. The tests are not in
 * {@code target/classes}, and what is read is this module's classes.
 */
class WhoMakesAControlPlaceTest {

    private static final Set<String> A_CONTROL_PLACE = Set.of(
            "souther/compiler/coverage/ControlPlace$Arm",
            "souther/compiler/coverage/ControlPlace$Outcome");

    /** A method that may make one, how many it makes, and what says the halves go together. */
    private record Licence(String who, int makes, String why) { }

    private static final List<Licence> MAY_MAKE = List.of(
            new Licence("souther.compiler.coverage.CoverageSites.placeOf"
                            + " -> ControlPlace$Arm", 1,
                    "the one place an arm the walk drafted becomes a place, made once per draft so"
                            + " that a site of an arm and the arms of its fork are the same value."
                            + " The probe and the anchor come off that draft, so there is no pair"
                            + " here for a caller to have assembled"),
            new Licence("souther.compiler.coverage.CoverageSites.Plan.outcomeOf"
                            + " -> ControlPlace$Outcome", 1,
                    "the address is taken from this plan for the comparison being asked about, so"
                            + " the two are the one answer. Handed in separately they would be two,"
                            + " and a claim recorded at one comparison would carry a condition"
                            + " written about another"));

    @Test
    void everyMakerOfAControlPlaceIsWrittenDownWithWhatSaysItsHalvesGoTogether() {
        assertEquals(declared(), made(),
                "a control place holds what one walk settled together, and nothing downstream reads"
                        + " both halves to notice a pair that was assembled instead. So a maker"
                        + " arriving here is one to look at rather than one this can decide about."
                        + " What makes one today, and what says its halves go together: " + why());
    }

    private static Map<String, Integer> declared() {
        Map<String, Integer> out = new TreeMap<>();
        MAY_MAKE.forEach(each -> out.put(each.who(), each.makes()));
        return out;
    }

    private static Map<String, String> why() {
        Map<String, String> out = new LinkedHashMap<>();
        MAY_MAKE.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many control places each method of the compiler makes. */
    private static Map<String, Integer> made() {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.name().stringValue().equals("<init>")
                            && A_CONTROL_PLACE.contains(call.owner().asInternalName())) {
                        String which = call.owner().asInternalName();
                        calls.merge(from + "." + wroteIt(method.methodName().stringValue())
                                        + " -> " + which.substring(which.lastIndexOf('/') + 1),
                                1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }

    /**
     * The method a person wrote, for a name javac may have made up.
     *
     * <p>A lambda is compiled to a method named after the one it was written inside and numbered by
     * where it fell among that class's lambdas. The number moves when a lambda is added anywhere in
     * the class, which has nothing to do with who makes a control place.
     */
    private static String wroteIt(String compiled) {
        if (!compiled.startsWith("lambda$")) {
            return compiled;
        }
        return compiled.substring("lambda$".length(), compiled.lastIndexOf('$'));
    }
}
