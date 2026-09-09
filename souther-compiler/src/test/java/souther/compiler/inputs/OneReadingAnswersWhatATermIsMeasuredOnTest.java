package souther.compiler.inputs;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which orders a term stands on is worked out in one place, from what one reading resolved.
 *
 * <p>A term is a value that travels and a type is in every reader's hand, so anything that can put
 * the two together can answer what a number is measured on — about wherever that type came from,
 * and about no reading in particular. The answer is right only while the type the caller holds and
 * the type the reading resolved agree, and nothing says when they stop agreeing.
 *
 * <p>Which is why the derivation is package-private here and the way to it is
 * {@link Quantities#ordersOf}. Visibility settles who may call it; it does not settle how many
 * places inside this package do, and a second one is the same defect written where the compiler
 * cannot see it. So this counts them.
 *
 * <p><b>Counted in the sources rather than declared in a list.</b> A list of allowed callers is a
 * thing to keep up to date, and the first person to add one keeps it up to date by adding
 * themselves. The count is what the rule says.
 */
class OneReadingAnswersWhatATermIsMeasuredOnTest {

    /** Where both orders of a term are worked out from a type. */
    private static final String DERIVES = "souther.compiler.inputs.TermOrdering";

    /** The pair itself, whose constructor and factory are the two ways to make one. */
    private static final String MADE = "souther.compiler.inputs.TermOrders";

    /**
     * Read off what was compiled rather than off the sources.
     *
     * <p>A check on the spelling reads what a call looks like, and a call can look like anything: a
     * static import drops the class name, and a lambda that derives puts the call in a class of its
     * own. What the class files carry is the call, whatever it was written as.
     */
    @Test
    void oneProductionPlaceDerivesATermsOrders() {
        Set<String> derives = new TreeSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = nestOf(model.thisClass().asInternalName().replace('/', '.'));
            if (from.equals(DERIVES)) {
                continue;   // what the derivation does with itself is its own business
            }
            for (var method : model.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                for (var element : code) {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().replace('/', '.').equals(DERIVES)) {
                        derives.add(from);
                    }
                }
            }
        }

        assertFalse(derives.isEmpty(),
                "nothing works a term's orders out at all; this check is reading no calls");
        assertEquals(Set.of("souther.compiler.inputs.ReadQuantities"), derives,
                "a term's orders are worked out where the reading that resolved its subject is,"
                        + " and a second place is a reader answering for a reading of its own");
    }

    /**
     * And one place makes one, which is not the same question.
     *
     * <p>Closing the constructor says who may make a pair and the count above says who works one
     * out from a type; neither says how many places inside this package put two carriers together.
     * A line added to any class here would be a pair about no reading, made where the compiler has
     * nothing left to refuse, and both other checks would stay green.
     *
     * <p>Both ways in are counted, and separately: a constructor and a factory beside it are two
     * things a reader can reach for, and a check that added them up would go on passing while one
     * moved to the other. There is no factory today, which is the stronger of the two states and is
     * held to here rather than left to be noticed.
     */
    @Test
    void oneProductionPlaceMakesAPair() {
        Set<String> built = new TreeSet<>();
        Set<String> named = new TreeSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = nestOf(model.thisClass().asInternalName().replace('/', '.'));
            if (from.equals(MADE)) {
                continue;   // what the pair does with itself is its own business
            }
            for (var method : model.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                for (var element : code) {
                    if (!(element instanceof InvokeInstruction call)
                            || !call.owner().asInternalName().replace('/', '.').equals(MADE)) {
                        continue;
                    }
                    if (call.name().stringValue().equals("<init>")) {
                        built.add(from);
                    } else if (call.typeSymbol().returnType().displayName().equals("TermOrders")) {
                        named.add(from);
                    }
                }
            }
        }

        assertEquals(Set.of(DERIVES), built,
                "a pair of orders is put together where a term's orders are worked out, and"
                        + " a second place is a pair about no reading in particular");
        assertEquals(Set.of(), named,
                "and nothing hands a pair back beside the constructor, so there is one way in");
    }

    /** The nest a class belongs to: a lambda written inside a reader is that reader. */
    private static String nestOf(String binaryName) {
        int nested = binaryName.indexOf('$');
        return nested < 0 ? binaryName : binaryName.substring(0, nested);
    }


    /**
     * And the way in stays shut, which is what makes the count above the whole of the rule.
     *
     * <p>Asked of the class rather than of the sources. What stops a reader elsewhere from pairing
     * two carriers of its own is that it cannot name the constructor, and that is a property of
     * {@link TermOrders} rather than of anything written at a call site — widen either of these and
     * every source in the compiler may make one, with nothing else here failing.
     */
    @Test
    void theWayToMakeAPairIsNotOpenToOtherPackages() {
        for (java.lang.reflect.Constructor<?> made : TermOrders.class.getDeclaredConstructors()) {
            assertTrue(!java.lang.reflect.Modifier.isPublic(made.getModifiers()),
                    () -> "a term's orders are made from what a reading resolved, and " + made
                            + " lets any caller pair two carriers it happens to hold");
        }
        assertEquals(List.of(), java.util.Arrays.stream(TermOrders.class.getDeclaredMethods())
                        .filter(each -> each.getReturnType() == TermOrders.class)
                        .map(java.lang.reflect.Method::getName).sorted().toList(),
                "and there is no factory beside it at all, open or not: a second way to make one is"
                        + " a second place to count, and the count above reads calls rather than"
                        + " declarations");
    }
}
