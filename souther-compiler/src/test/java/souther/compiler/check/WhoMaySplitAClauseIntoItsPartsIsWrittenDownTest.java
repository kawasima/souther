package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who splits a clause into the parts its author wrote, and where such a split may happen.
 *
 * <p>Splitting is how the parts are made. Holding the identity they are named by to one call site
 * says the same name is not minted twice; it does not say the parts a name is minted for are the
 * parts of one split, and they were not — a reader wanting the parts of a clause split the clause
 * again, and the tree it had was the tree an expansion left. What a helper's body joined is a
 * conjunction there, so the second split answers a question the first already answered, with a
 * different answer.
 *
 * <p>So the split happens before an expansion and nowhere else. What it leaves is the shape the
 * clause was written in, and every reader after that asks the shape rather than a tree: which parts
 * there are is settled once, and recovering the subtree each part became is a walk the shape drives
 * ({@link AuthoredShape}).
 *
 * <p>Read off the compiled classes, because what a method calls is what the class file says.
 */
class WhoMaySplitAClauseIntoItsPartsIsWrittenDownTest {

    private static final String HELPERS = "souther/compiler/check/ClauseHelpers";

    /** A method that may split a clause, how many times it does, and why. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_SPLIT = List.of(
            new Licence("souther.compiler.check.ClauseHelpers.inlinedClause", 1,
                    "the expansion of a declaration's clauses, which splits before it expands so"
                            + " that each part is expanded where it stands and the shape it leaves"
                            + " is what every reader after it asks"),
            new Licence("souther.compiler.check.ClauseHelpers.conjunctsOf", 1,
                    "the parts alone, for an expansion that drives itself a part at a time rather"
                            + " than writing them back into one tree"));

    /** And who asks for the parts without the shape, which is the same split read as a list. */
    private static final List<Licence> MAY_ASK_FOR_THE_PARTS = List.of(
            new Licence("souther.compiler.check.ClausesForDischarge.conjunctsOf", 1,
                    "the reading a behavior's rules and a declaration's are prepared for together,"
                            + " which splits the written tree and expands one part at a time"),
            new Licence("souther.compiler.check.ClauseHelpers.placesOfParts", 1,
                    "where the parts are written, for a reader holding the name of one and no"
                            + " tree — the same split, so that the parts it answers about are the"
                            + " parts everybody else is holding"));

    @Test
    void onlyAnExpansionSplitsAClauseIntoTheParts() {
        assertEquals(declared(MAY_SPLIT), callsTo(HELPERS, "shapeOf"),
                "a clause split anywhere else is a second answer to which parts it has, and the"
                        + " tree such a reader holds is one an expansion has been over. What may"
                        + " split one, and why: " + why(MAY_SPLIT));
        assertEquals(declared(MAY_ASK_FOR_THE_PARTS), callsTo(HELPERS, "conjunctsOf"),
                "and the parts as a list are the same split, asked for by an expansion that has no"
                        + " tree to write them back into: " + why(MAY_ASK_FOR_THE_PARTS));
    }

    private static Map<String, Integer> declared(List<Licence> licences) {
        Map<String, Integer> out = new TreeMap<>();
        licences.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why(List<Licence> licences) {
        Map<String, String> out = new TreeMap<>();
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
