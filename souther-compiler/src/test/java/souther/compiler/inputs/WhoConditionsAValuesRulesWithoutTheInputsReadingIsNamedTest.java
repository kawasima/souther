package souther.compiler.inputs;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Everything that settles a position of a value's rules without going through this reading is
 * written down here.
 *
 * <p>Asking what the rules leave once something is fixed is what every search that builds a value
 * needs, and a search that reaches for it directly brings its own way of naming a position. One that
 * does not find it at all walks the box around the positions instead, which is a set the rules have
 * a corner cut out of — and that is what {@link Quantities} exists to stop, for a search whose
 * subject is a behavior's input. It stops it only while what may reach past it is known.
 *
 * <p><b>A register, and not a list of exceptions.</b> Each name below is here for its own reason,
 * and the reasons are not the same one: what an input's rules leave it is one question, and what a
 * declaration leaves a value somebody is building is another. Adding a name is a new place the
 * question is asked from, which is worth knowing whether or not it turns out to be right.
 *
 * <p><b>Read off the compiled classes.</b> What is checked is who calls the conditioning member,
 * told apart from reading a declaration for the first time by what it takes rather than by its
 * name.
 *
 * <p><b>Both directions.</b> A check that only counts trespassers passes when it reads nothing at
 * all, so what is expected to be found is named as well.
 */
class WhoConditionsAValuesRulesWithoutTheInputsReadingIsNamedTest {

    private static final String CONDITIONS = "souther.compiler.check.FieldDomains";

    /** Who settles a position of a value's rules, and why each of them does. */
    private static final Set<String> MAY_CONDITION = Set.of(
            // The reading of an input asking on the input's behalf, which is what the boundary is.
            "souther.compiler.inputs.PlacedRules",
            // A representative value of a declaration, composed field by field. Its subject is the
            // declaration and not an input — no behavior, no parameter, no path rooted at one — so
            // the declaration's own words are the right ones and there is nothing to translate.
            "souther.compiler.partition.Partitions",
            // A row's value for one parameter, composed a position at a time. Its positions are
            // where a value has to be built and not what the behavior declares it takes: a class
            // naming a constructor for a sum puts positions under it that no reading of the
            // declaration holds, however deep it went. So it has nothing to ask the reading of an
            // input about, and the rules it settles are the rules of whatever it is building.
            "souther.compiler.partition.Generator");

    @Test
    void nothingElseSettlesAPositionOfAValuesRules() {
        Set<String> found = new TreeSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = nestOf(model.thisClass().asInternalName().replace('/', '.'));
            if (from.equals(CONDITIONS)) {
                continue;   // what the reading does with itself is its own business
            }
            for (var method : model.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                for (var element : code) {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().replace('/', '.').equals(CONDITIONS)
                            && conditions(call.name().stringValue(), call.typeSymbol())) {
                        found.add(from);
                    }
                }
            }
        }
        assertFalse(found.isEmpty(),
                "nothing settles a position at all; this check is reading no calls");
        assertEquals(new TreeSet<>(MAY_CONDITION), found,
                "who settles a position of a value's rules");
    }

    /**
     * Which of the reading's members hand back rules with a position settled.
     *
     * <p>Told apart by what they take and not by their name. Reading a declaration and reading it
     * with a position settled are both called {@code of}, and the second is the one that takes the
     * settlings — so a check on the name alone would report every reader of a declaration as one
     * that settles a position of it.
     */
    private static boolean conditions(String member, java.lang.constant.MethodTypeDesc taken) {
        if (member.equals("given")) {
            return true;
        }
        return member.equals("of") && taken.parameterCount() > 0
                && taken.parameterType(taken.parameterCount() - 1).displayName().equals("Map");
    }

    /** The nest a class belongs to: a lambda written inside a reader is that reader. */
    private static String nestOf(String binaryName) {
        int nested = binaryName.indexOf('$');
        return nested < 0 ? binaryName : binaryName.substring(0, nested);
    }

}
