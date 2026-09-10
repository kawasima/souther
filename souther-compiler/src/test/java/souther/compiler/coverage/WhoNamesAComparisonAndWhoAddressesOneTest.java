package souther.compiler.coverage;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a run through a comparison is written down is handed out by one place.
 *
 * <p><b>Two questions, and only one of them is answered by handing something out.</b> Which
 * comparison a reading is talking about is the {@link souther.compiler.types.ConstructOccurrence}
 * the tree carries, so there is nothing to hand out and no second place that could; what has a
 * {@link ComparisonEmissionSite} is what the plan instruments, which is fewer — a comparison behind
 * an abort is one no run reaches. Held as one value the two were the same number, so a reading
 * asking which comparison it was looking at got an answer that was true only while every comparison
 * the catalog held had been numbered.
 *
 * <p>Which makes who may make an address the thing to hold. A second place handing one out is a
 * number the emitter never wrote, and a claim about a run that no run can satisfy.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it — a call written inside a lambda belongs to the lambda, and
 * this says so. A constructor handed over rather than called belongs to the method that hands it
 * over, which is where the {@code invokedynamic} carrying it stands.
 *
 * <p><b>What it does not see, said rather than left to be found.</b> The tests are not in
 * {@code target/classes}: a fixture that writes a report about a comparison nothing compiled makes
 * one of these by hand, and that is a fixture standing in for a catalog rather than a second answer
 * inside the compiler. And what is read is this module's classes — a maker written in the CLI or
 * the language server would not be counted here. Widening it is not free: those modules are built
 * after this one, so a walk over their output would read whatever the last build left and would say
 * nothing at all on a clean one. Both are things to be told about rather than things this can be
 * widened to cover.
 */
class WhoNamesAComparisonAndWhoAddressesOneTest {

    private static final String SITE = "souther/compiler/coverage/ComparisonEmissionSite";

    private static final String ARM = "souther/compiler/coverage/ArmProbe";

    private static final String BODIES = "souther/compiler/coverage/ModuleBodies";

    private static final String CATALOGUED = "souther/compiler/coverage/ComparisonCatalog$Catalogued";

    private static final String READ = "souther/compiler/partition/LineOrigin$ComparisonOrigin$Read";

    /** A method that may make one, how many times it does, and why it is the one that does. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_ADDRESS = List.of(
            new Licence("souther.compiler.coverage.SiteNumbering.comparison", 1,
                    "the numbering asked what a number of its own addresses, which is the one way"
                            + " a number becomes a place — and it is refused where that numbering"
                            + " handed the number out to something other than a comparison, or"
                            + " never handed it out at all. Everything holding one of these got it"
                            + " here: the walk that numbers the places carries numbers until the"
                            + " numbering is finished, because until then there is no numbering"
                            + " for an address to be of"));

    private static final List<Licence> MAY_ADDRESS_AN_ARM = List.of(
            new Licence("souther.compiler.coverage.SiteNumbering.arm", 1,
                    "the numbering asked what a number of its own addresses, which is the one way"
                            + " a number becomes a place — and it is refused where that numbering"
                            + " handed the number out to something other than an arm, or never"
                            + " handed it out at all"));

    /**
     * What holds a value whose parts have to agree, and where each is put together.
     *
     * <p>Beside the two above and the same rule. A value made of parts that are only true together
     * is one a caller can pair wrongly — one module's name beside another's trees, a comparison
     * beside a citation of somewhere else, an occurrence beside the emission site of another plan.
     * What keeps them true is that one place makes each, so what is fixed is where that place is.
     */
    private static final List<Licence> MAY_PAIR = List.of(
            new Licence("souther.compiler.query.Bodies.Checked.compute", 1,
                    "where a module's name and its trees are both in hand for the first and only"
                            + " time: the check was asked under the name and produced the trees, so"
                            + " nothing else has to be trusted to put the two together"),
            new Licence("souther.compiler.coverage.ModuleBodies.none", 1,
                    "the module with nothing in it, which is what a check that did not finish"
                            + " leaves and is a pair of nothing with nobody"));

    private static final List<Licence> MAY_CATALOGUE = List.of(
            new Licence("souther.compiler.coverage.ComparisonCatalog.lambda$walk$0", 1,
                    "the one walk that recognises a comparison, names it and says where it is"
                            + " written, all from the node it is standing at"));

    private static final List<Licence> MAY_READ = List.of(
            new Licence("souther.compiler.partition.GuardThresholds.originOf", 1,
                    "which comparison a rule is about, where it is written and where a run through"
                            + " it is recorded, taken together from the catalog and the plan that"
                            + " numbered it"));

    @Test
    void onlyACheckPairsAModuleWithItsBodies() {
        assertEquals(declared(MAY_PAIR), callsToConstructor(BODIES),
                "a module's name beside another module's trees has the catalog issue names true of"
                        + " nothing, and no later check can refuse them. What may pair them, and"
                        + " why: " + why(MAY_PAIR));
    }

    @Test
    void onlyTheWalkPutsACataloguedComparisonTogether() {
        assertEquals(declared(MAY_CATALOGUE), callsToConstructor(CATALOGUED),
                "a name, a recognition and a place are true together or not at all. What may put"
                        + " them together, and why: " + why(MAY_CATALOGUE));
    }

    @Test
    void onlyOnePlaceSaysWhichComparisonARuleIsReadOff() {
        assertEquals(declared(MAY_READ), callsToConstructor(READ),
                "an occurrence of one plan beside the emission site of another is a rule pointing"
                        + " at two places. What may pair them, and why: " + why(MAY_READ));
    }

    @Test
    void onlyTheNumberingAddressesAComparisonOfARun() {
        assertEquals(declared(MAY_ADDRESS), callsToConstructor(SITE),
                "an address made anywhere else is a place no run was recorded at. What may make"
                        + " one, and why: " + why(MAY_ADDRESS));
    }

    /**
     * And the same for the other family, which is the same rule and not a second one.
     *
     * <p>Both or neither: the two are numbers out of one counter, and a rule that held for the
     * comparisons alone would leave the arms — the family every branch measure counts — free to be
     * paired with a numbering that never handed them out.
     */
    @Test
    void onlyTheNumberingAddressesAnArmOfARun() {
        assertEquals(declared(MAY_ADDRESS_AN_ARM), callsToConstructor(ARM),
                "an address made anywhere else is a place no run was recorded at. What may make"
                        + " one, and why: " + why(MAY_ADDRESS_AN_ARM));
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

    /** How many times each method of the compiler makes one of {@code owner}. */
    private static Map<String, Integer> callsToConstructor(String owner) {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                String in = from + "." + method.methodName().stringValue();
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (makesOne(element, owner)) {
                        calls.merge(in, 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }

    /**
     * Whether {@code element} makes one of {@code owner}, by calling the constructor or by handing
     * it to something that will.
     *
     * <p>A constructor reference calls nothing. What is written where the reference stands is an
     * {@code invokedynamic} carrying the constructor as a handle among its bootstrap arguments, and
     * what that hands out makes one as surely as making it here would — so a walk over the call
     * instructions alone leaves a class the rows above do not name free to hold a maker of the
     * places a run is recorded at.
     *
     * <p>Only the handle that makes one. What a reference to anything else says is that a method is
     * named, and the rows above are about who may make one of these, which is what a constructor
     * answers with and nothing else does.
     */
    private static boolean makesOne(CodeElement element, String owner) {
        if (element instanceof InvokeInstruction call) {
            return call.owner().asInternalName().equals(owner)
                    && call.name().stringValue().equals("<init>");
        }
        if (element instanceof InvokeDynamicInstruction reference) {
            for (ConstantDesc argument : reference.invokedynamic().asSymbol().bootstrapArgs()) {
                if (argument instanceof DirectMethodHandleDesc handle
                        && handle.kind() == DirectMethodHandleDesc.Kind.CONSTRUCTOR
                        && handle.owner().descriptorString().equals("L" + owner + ";")) {
                    return true;
                }
            }
        }
        return false;
    }
}
