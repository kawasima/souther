package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.AsACompilationAllows;
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
    /** The position every branch's ranges speak about, which no branch's values do. */
    private static final String COUNTED = "counted";
    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    private final Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

    private static PlannedValues<String> says(String atom, Value value) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(value)));
    }

    /**
     * A branch, whose ranges are ends somebody could be at whether or not its values leave
     * anything.
     *
     * <p>The ranges are what a settlement has to be told about. A branch the values refused is one
     * the order found nothing wrong with, so a choice that let the standing ends of a dead branch
     * through would go on ruling things out on their strength — and with every branch at
     * {@link OrderedIntervals#top} the two ways of composing two dead ones leave the same answer
     * and nothing here would tell them apart.
     */
    private static Confinement.Planned<String> reading(PlannedValues<String> values,
                                                       int low, int high) {
        return new Confinement.Planned<>(values,
                OrderedIntervals.at(COUNTED, new OrderedInterval(
                        Endpoint.inclusive(Count.of(low)), Endpoint.inclusive(Count.of(high)))),
                Map.of(COUNTED, Carrier.WHOLE));
    }

    /**
     * One alternative and whether anybody can be in it, composed the way the holder of both
     * languages composes them.
     *
     * <p>Whether a branch admits anything is settled over the values and the order together, so it
     * is carried beside here rather than asked of either. What is under test is what the four cases
     * leave, not which of them a clause falls into — which is why the case is not worked out here
     * either, but asked of the one place that says which alternatives a pair of answers leaves
     * standing.
     */
    private record Branch(Confinement.Planned<String> reading, boolean dead) {

        Branch or(Branch other) {
            return switch (souther.compiler.values.Emptiness.Alternatives.of(said(),
                    other.said())) {
                // What showed the choice dead is what showed both of its branches dead, which is
                // where the holder of both languages takes it from as well.
                case NEITHER_STANDS -> new Branch(reading.bothDead(other.reading,
                        Confinement.Admission.bothShown(reading.admission(),
                                other.reading.admission())), true);
                // Neither language is asked what a choice with one dead branch leaves: what it
                // leaves is the standing branch, which the holder has in hand. Composed instead,
                // the choice would keep only what both sides spoke about — and a side nobody can be
                // in spoke about positions the other did not, so the whole would come back saying
                // nothing at all about them.
                case ONLY_THE_RIGHT -> other;
                case ONLY_THE_LEFT -> this;
                case BOTH_STAND -> new Branch(reading.either(other.reading, false), false);
            };
        }

        /** This branch's fate, in the words the classification is read in. */
        private souther.compiler.values.Emptiness said() {
            return dead ? souther.compiler.values.Emptiness.EMPTY
                    : souther.compiler.values.Emptiness.NONEMPTY;
        }
    }

    /**
     * A branch somebody can be in, and one nobody can.
     *
     * <p>Dead two ways, because a branch is dead by whatever showed it so: two rules about one
     * position leaving it no value, and an equality met with a denial of the same pair, which
     * leaves every position a value and no assignment standing.
     */
    private Map<String, Branch> branches() {
        Map<String, Branch> out = new LinkedHashMap<>();
        out.put("x == A", new Branch(reading(says(X, A), 1, 2), false));
        out.put("y == B", new Branch(reading(says(Y, B), 3, 4), false));
        out.put("x == A && x == B", new Branch(
                reading(says(X, A).meet(says(X, B)), 100, 200), true));
        out.put("x == y && x /= y", new Branch(
                reading(PlannedValues.<String>holdingAsOne(X, Y)
                        .meet(PlannedValues.heldApart(X, Y)), 300, 400), true));
        return out;
    }

    /** A rule about the position the ranges above speak about, which the ends of a dead branch
     *  would rule out and the ends of a standing one do. */
    private Confinement.Planned<String> afterwards() {
        return new Confinement.Planned<>(PlannedValues.top(),
                OrderedIntervals.at(COUNTED, new OrderedInterval(
                        Endpoint.inclusive(Count.of(1)), Endpoint.inclusive(Count.of(2)))),
                Map.of(COUNTED, Carrier.WHOLE));
    }

    /**
     * What a caller reads off what a choice left, as one comparable value.
     *
     * <p>The values, what already showed the reading empty, and what a rule met after the choice
     * comes to. What is held of them is that the two sides of an equation agree, so what widening
     * this buys is a bracketing or an ordering that tells them apart in any of the three — not a
     * claim that one operation is being told from another, which no equation between two
     * compositions can make.
     */
    private List<Object> answers(Branch branch) {
        AdmissibleValues<String> values = branch.reading().resolve(sets).values();
        List<Object> out = new ArrayList<>();
        for (String position : List.of(X, Y)) {
            out.add(values.at(position));
            out.add(values.speaksFor(position));
            out.add(values.guaranteedAt(position));
        }
        out.add(values.isBottom());
        out.add(branch.reading().admission());
        // And what the choice leaves a rule met after it, which is where ends left answering for a
        // branch nobody can be in would rule something out on their own strength.
        out.add(branch.reading().meet(afterwards()).resolve(sets).holdingNothing());
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
