package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.AsACompilationAllows;
import souther.compiler.values.Emptiness;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a choice with a branch nobody can be in leaves does not follow the brackets.
 *
 * <p>A choice between two branches that stand is composed by the values, and that it is one
 * connective rather than a tree is held of that operation
 * ({@code AChoiceIsOneConnectiveAndNotATreeTest}). A branch nobody can be in is not composed at
 * all: which branches those are is a question about the values and the order together, so it is
 * settled by the holder of both, and what the values are asked for is either the settlement of two
 * dead branches or nothing at all.
 *
 * <p>So the property has two halves and this is the other one. Written the way the holder settles
 * them — every branch dead is a settlement, one dead leaves the standing branch as it stands —
 * three alternatives leave the same answer however they are bracketed and whatever order they were
 * written in. Lost, a declaration refused for reasons nobody can be in would answer one way to the
 * left of the brackets and another to the right.
 *
 * <p>The same property over what a report may say of such a choice is
 * {@link AChoiceReadsTheRuleAndNotTheTreeItIsWrittenAsTest}.
 */
class ADeadBranchIsSettledTheSameHoweverTheChoiceWasBracketedTest {

    private static final String X = "x";
    private static final String Y = "y";
    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** What every dead branch here was shown dead by, which is not what is under test. */
    private static final Confinement.Admission<String> SHOWN =
            Confinement.Admission.left(Emptiness.EMPTY);

    private final Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

    private static PlannedValues<String> says(String atom, Value value) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(value)));
    }

    private static Confinement.Planned<String> reading(PlannedValues<String> values) {
        return new Confinement.Planned<>(values, OrderedIntervals.top(), Map.of());
    }

    /**
     * One alternative and whether anybody can be in it, composed the way the holder of both
     * languages composes them.
     *
     * <p>Whether a branch admits anything is settled over the values and the order together, so it
     * is carried beside here rather than asked of either. What is under test is what the four cases
     * leave, not which of them a clause falls into.
     */
    private record Branch(Confinement.Planned<String> reading, boolean dead) {

        Branch or(Branch other) {
            if (dead && other.dead) {
                return new Branch(reading.bothDead(other.reading, SHOWN), true);
            }
            // Neither language is asked what a choice with one dead branch leaves: what it leaves
            // is the standing branch, which the holder has in hand.
            if (dead) {
                return other;
            }
            if (other.dead) {
                return this;
            }
            return new Branch(reading.either(other.reading, false), false);
        }
    }

    /** A branch somebody can be in, and one nobody can. */
    private Map<String, Branch> branches() {
        Map<String, Branch> out = new LinkedHashMap<>();
        out.put("x == A", new Branch(reading(says(X, A)), false));
        out.put("y == B", new Branch(reading(says(Y, B)), false));
        out.put("x == A && x == B", new Branch(
                reading(says(X, A).meet(says(X, B)).leavingNothing()), true));
        out.put("y == A && y == B", new Branch(
                reading(says(Y, A).meet(says(Y, B)).leavingNothing()), true));
        return out;
    }

    /** What a caller reads off the values a choice left, as one comparable value. */
    private List<Object> answers(Branch branch) {
        AdmissibleValues<String> values = branch.reading().resolve(sets).values();
        List<Object> out = new ArrayList<>();
        for (String position : List.of(X, Y)) {
            out.add(values.at(position));
            out.add(values.speaksFor(position));
            out.add(values.guaranteedAt(position));
        }
        out.add(values.isBottom());
        return out;
    }

    /** Three alternatives leave what they leave, however they are bracketed. */
    @Test
    void threeAlternativesLeaveTheSameHoweverTheyAreBracketed() {
        Map<String, Branch> branches = branches();
        branches.forEach((leftName, left) -> branches.forEach((middleName, middle) ->
                branches.forEach((rightName, right) -> assertEquals(
                        answers(left.or(middle).or(right)),
                        answers(left.or(middle.or(right))),
                        () -> "(" + leftName + " || " + middleName + ") || " + rightName
                                + "   against   " + leftName + " || (" + middleName + " || "
                                + rightName + ")"))));
    }

    /** And however they are ordered. */
    @Test
    void twoAlternativesLeaveTheSameHoweverTheyAreOrdered() {
        Map<String, Branch> branches = branches();
        branches.forEach((leftName, left) -> branches.forEach((rightName, right) ->
                assertEquals(answers(left.or(right)), answers(right.or(left)),
                        () -> leftName + " || " + rightName + "   against   "
                                + rightName + " || " + leftName)));
    }

    /**
     * And the control: the branches this is written about really are what they are said to be.
     *
     * <p>Every case above is reached only because some branch is dead and some is not. Were they
     * all standing, the two tests would be the property held of live choices alone and would say
     * nothing about a settlement.
     */
    @Test
    void theBranchesAreTheOnesThePropertyNeeds() {
        Map<String, Branch> branches = branches();
        assertTrue(branches.values().stream().anyMatch(Branch::dead), "a branch nobody can be in");
        assertTrue(branches.values().stream().anyMatch(each -> !each.dead()), "and one somebody can");
        branches.forEach((name, branch) -> assertEquals(branch.dead(),
                branch.reading().resolve(sets).values().isBottom(),
                () -> name + " is not the branch it is filed as"));
    }
}
