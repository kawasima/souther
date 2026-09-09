package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a branch nobody can be in leaves is said once, and every account of a choice is that rule
 * applied to a branch.
 *
 * <p>The rule is a fact about one branch: nothing satisfies it, so nothing it said narrows a value
 * and nothing it could not read is missing from what a value is under, and what is left is that the
 * positions it named are settled. Said again for a pair of branches, the second statement is free
 * to disagree with the first — and a choice neither alternative of which anybody can be in is a
 * choice nothing publishes an account of, so nothing would notice that it had.
 *
 * <p>So a fate is spent on a branch in one place, and what composes two branches afterwards is not
 * handed one. Which of the two compositions a choice gets is read off the two fates beside that,
 * and that is the whole of what a pair of them decides — so a choice one alternative of which is
 * dead and a choice neither alternative of which anybody can be in are the same line with the rule
 * applied once and twice. The one that no report reads travels the road the one every report reads
 * travels.
 *
 * <p>Held over the compiled classes and per calling method, because that is the grain the claim is
 * at. Two methods of one class are two places, and a rule stated at the class would let the second
 * of them be written. A method reference is a call here: a rule handed to something that will apply
 * it is applied.
 */
class OnePlaceSaysWhatABranchNobodyCanBeInLeavesTest {

    private static final String ADOPTION = "souther/compiler/check/Adoption";

    private static final String PART = "souther/compiler/check/StatedByClauses$Part";

    private static final String TAKEN = "souther/compiler/check/StatedByClauses$Taken";

    /** A method that may apply the rule or spend a fate on a branch, how often, and why. */
    private record Licence(String who, int calls, String why) { }

    /**
     * Who says what a dead branch leaves each language a clause was read in.
     *
     * <p>The part it was read as, and nothing above or below it. Each language is told once,
     * because a part is what holds both of them and the rule is the same for either.
     */
    private static final List<Licence> DEADENING_AN_ADOPTION = List.of(
            new Licence("souther.compiler.check.StatedByClauses.Part.inADeadBranch", 2,
                    "the part, telling each of the two languages it was read in the one rule"));

    /**
     * Who says it of a written part, and who of the whole of a subtree.
     *
     * <p>A subtree's parts are the same rule over every one of them, so the walk that says it of a
     * subtree is the only caller of the part's — and it is where what a choice inside it left open
     * goes, since there is no branch there for a position to be open in.
     */
    private static final List<Licence> DEADENING_A_PART = List.of(
            new Licence("souther.compiler.check.StatedByClauses.Taken.inADeadBranch", 1,
                    "the subtree, handing the rule to the walk over every part it holds"));

    private static final List<Licence> DEADENING_A_TAKEN = List.of(
            new Licence("souther.compiler.check.StatedByClauses.Taken.under", 1,
                    "the one place a branch is answered for by what became of it"));

    /**
     * And who spends one on a branch, which is the walk over the tree the author wrote.
     *
     * <p>Twice, for the two alternatives of a choice. A third caller is a second place deciding
     * what a fate does to an account, and what a branch left would be back to turning on the pair
     * rather than on the branch.
     *
     * <p>Reading a fate is not that and is not counted here. The walk beside this one reads the two
     * of them to pick which composition the choice gets, which is a question about the choice: the
     * compositions are handed no fate, so what one of them does to a branch is what this method
     * already did.
     */
    private static final List<Licence> SPENDING_A_FATE_ON_A_BRANCH = List.of(
            new Licence("souther.compiler.check.StatedByClauses.Reading.accounted", 2,
                    "the account of a rule, asking each alternative of a choice what became of it"));

    @Test
    void theRuleForABranchNobodyCanBeInIsAppliedDownOneRoad() {
        assertEquals(declared(DEADENING_AN_ADOPTION), callsTo(ADOPTION, "inADeadBranch"),
                "a second caller states the rule for a language a second time, and the two are free"
                        + " to disagree: " + why(DEADENING_AN_ADOPTION));
        assertEquals(declared(DEADENING_A_PART), callsTo(PART, "inADeadBranch"),
                "and a part deadened anywhere but over the whole subtree is a part answered for by"
                        + " a caller that has not answered for the rest: " + why(DEADENING_A_PART));
        assertEquals(declared(DEADENING_A_TAKEN), callsTo(TAKEN, "inADeadBranch"),
                "and a subtree deadened outside the spending of a fate is one deadened for a reason"
                        + " no fate gave: " + why(DEADENING_A_TAKEN));
    }

    @Test
    void andAFateIsSpentOnOneBranchInOnePlace() {
        assertEquals(declared(SPENDING_A_FATE_ON_A_BRANCH), callsTo(TAKEN, "under"),
                "a second caller of this is a second place deciding what a fate does to an account,"
                        + " and what a branch left would be back to turning on the pair: "
                        + why(SPENDING_A_FATE_ON_A_BRANCH));
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

    /** How many times each method of the compiler names {@code owner.name}. */
    private static Map<String, Integer> callsTo(String owner, String name) {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                String where = from + "." + method.methodName().stringValue();
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (names(element, owner, name)) {
                        calls.merge(where, 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }

    /**
     * Whether one instruction names {@code owner.name}, called or handed over.
     *
     * <p>A method reference compiles to an {@code invokedynamic} whose bootstrap carries the target
     * as a handle, and the method's instructions name it nowhere else — so a walk over calls alone
     * would find no caller of a rule that is applied to every part of a subtree, and would pass on
     * an empty answer.
     */
    private static boolean names(Object element, String owner, String name) {
        if (element instanceof InvokeInstruction call) {
            return call.owner().asInternalName().equals(owner)
                    && call.name().stringValue().equals(name);
        }
        if (element instanceof InvokeDynamicInstruction handed) {
            for (LoadableConstantEntry argument
                    : handed.invokedynamic().bootstrap().arguments()) {
                if (argument instanceof MethodHandleEntry handle
                        && handle.reference().owner().name().stringValue().equals(owner)
                        && handle.reference().name().stringValue().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
