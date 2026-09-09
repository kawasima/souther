package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What one reading promises, read at a block holding more positions than its own did.
 *
 * <p>A conjunction leaves a coarser relation: {@code p == q} met with {@code q == r} holds the
 * three as one value. So a side that stated only the first is asked about a block it never had, and
 * what it promises there is what it promises at every one of its own blocks those positions fall
 * in. A value at the three of them is a value at each.
 *
 * <p>Every one of them and not one, because a side's default is every value: a promise read at one
 * of its blocks and not the rest says a value stands wherever that block's positions are silent,
 * which nothing proved.
 */
class APromiseIsReadAtEveryBlockOfItsOwnACoarserOneCoversTest {

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");
    private static final Value C = Value.text("C");

    /** What builds the sets of these readings — every one of them is written out, so nothing here
     *  is refused for want of allowance. */
    private static final Allowance<String> SETS = AsACompilationAllows.forAdmittedValues();

    /** A reading holding {@code p} and {@code q} as one value and promising {@code A} or {@code B}
     *  there, saying nothing about {@code r}. */
    private static PlannedValues<String> here() {
        return PlannedValues.<String>holdingAsOne("p", "q")
                .meet(PlannedValues.at("p", AdmittedPlan.of(ValueSet.oneOf(Set.of(A, B)))));
    }

    /** And one holding {@code q} and {@code r} as one value and promising {@code B} or {@code C}
     *  there, saying nothing about {@code p}. */
    private static PlannedValues<String> there() {
        return PlannedValues.<String>holdingAsOne("q", "r")
                .meet(PlannedValues.at("r", AdmittedPlan.of(ValueSet.oneOf(Set.of(B, C)))));
    }

    /**
     * The conjunction promises what both sides promise of the one value the three positions hold.
     *
     * <p>{@code B} and neither side's own promise: read at one block of a side, the answer would be
     * that side's promise alone or every value, and both of those promise a value the other side's
     * rules leave nowhere.
     */
    @Test
    void aConjunctionPromisesWhatBothSidesPromiseOfTheOneValue() {
        AdmissibleValues<String> both = built(here()).meet(built(there()), SETS);

        assertEquals(Set.of("p", "q", "r"), both.blockOf("p").members());
        assertEquals(ValueSet.just(B), both.guaranteedAt("p"));
        assertEquals(ValueSet.just(B), both.guaranteedAt("r"));
    }

    /** And the same while it is still a description, where putting two promises together costs
     *  nothing and the same rule is written a second time. */
    @Test
    void andTheSameOfTheDescriptionTheConjunctionIsBuiltFrom() {
        AdmissibleValues<String> both = built(here().meet(there()));

        assertEquals(ValueSet.just(B), both.guaranteedAt("q"));
    }

    /** And whichever side is met with which, since a conjunction is one reading however it was
     *  written. */
    @Test
    void andWhicheverSideWasMetWithWhich() {
        AdmissibleValues<String> one = built(here()).meet(built(there()), SETS);
        AdmissibleValues<String> back = built(there()).meet(built(here()), SETS);

        assertEquals(one.guaranteedAt("q"), back.guaranteedAt("q"));
        assertEquals(String.valueOf(one.sameness()), String.valueOf(back.sameness()));
    }

    private static AdmissibleValues<String> built(PlannedValues<String> planned) {
        return planned.resolve(SETS).values();
    }
}
