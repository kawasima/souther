package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which position a reading that admits nothing leaves empty does not follow the brackets.
 *
 * <p>A reading may admit nothing because some position is left no value, or because the rules
 * cannot hold together with no one position at fault. The two are different answers and a reader
 * acts on which it is — so the answer has to be about the rules and not about the order they were
 * met in.
 *
 * <p>It is a pointwise question, and the alternatives cannot answer it. A conjunction meets them
 * pairwise and drops the pairs nothing stands in, so a pair that died leaves no trace once another
 * survives — and what it was going to say about a position leaves with it. Met in another order the
 * same rules blame a position no rule of theirs leaves empty:
 *
 * <pre>
 *     X = a == 0 &amp;&amp; b == 0
 *     Y = a == 0
 *     Z = (a == 1 &amp;&amp; b == 0) || (a == 0 &amp;&amp; b == 1)
 * </pre>
 *
 * <p>Nothing satisfies the three of them. Read one position at a time they leave {@code a} at
 * {@code 0} and {@code b} at {@code 0}, so neither is a position the rules leave nothing at — what
 * they cannot do is hold together. A reading that answered {@code b} empty would send an author to
 * a rule about {@code b} that is not the reason.
 */
class WhichPositionIsLeftNothingDoesNotFollowTheBracketsTest {

    private static final String A = "a";
    private static final String B = "b";
    private static final Value ZERO = Value.number(0);
    private static final Value ONE = Value.number(1);

    /** What puts the sets of one reading together. Every set here is written out, so nothing is
     *  built and no allowance is spent. */
    private final Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

    /** One rule about one position, worked out. */
    private AdmissibleValues<String> at(String atom, Value value) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(value)))
                .resolve(sets).values();
    }

    private AdmissibleValues<String> pair(Value a, Value b) {
        return at(A, a).meet(at(B, b), sets);
    }

    /** The same pair while it is still a description, which is where a choice between two of them
     *  is taken. */
    private static PlannedValues<String> planned(Value a, Value b) {
        return PlannedValues.at(A, AdmittedPlan.of(ValueSet.just(a)))
                .meet(PlannedValues.at(B, AdmittedPlan.of(ValueSet.just(b))));
    }

    /** The three above, in the orders a conjunction of them can be written in. */
    private List<AdmissibleValues<String>> everyOrder() {
        AdmissibleValues<String> x = pair(ZERO, ZERO);
        AdmissibleValues<String> y = at(A, ZERO);
        AdmissibleValues<String> z = planned(ONE, ZERO).joinLiveApart(planned(ZERO, ONE))
                .resolve(sets).values();
        List<AdmissibleValues<String>> out = new ArrayList<>();
        out.add(x.meet(y, sets).meet(z, sets));
        out.add(x.meet(z, sets).meet(y, sets));
        out.add(y.meet(z, sets).meet(x, sets));
        out.add(y.meet(x, sets).meet(z, sets));
        out.add(z.meet(x, sets).meet(y, sets));
        out.add(z.meet(y, sets).meet(x, sets));
        out.add(x.meet(y.meet(z, sets), sets));
        out.add(y.meet(x.meet(z, sets), sets));
        out.add(z.meet(x.meet(y, sets), sets));
        return out;
    }

    @Test
    void aReadingThatHoldsTogetherNowhereIsStillTheSameReading() {
        for (AdmissibleValues<String> each : everyOrder()) {
            assertTrue(each.isBottom(), "nothing satisfies the three of them: " + each);
        }
    }

    /**
     * And it leaves the same position nothing whichever way it was met, which here is none of them.
     *
     * <p>Both are left a value by the rules read one position at a time. What the three cannot do is
     * hold together, and that is not a fact about {@code a} or about {@code b}.
     */
    @Test
    void andLeavesTheSamePositionNothingWhicheverWayItWasMet() {
        for (AdmissibleValues<String> each : everyOrder()) {
            assertEquals(ValueSet.just(ZERO), each.at(A), "a is left 0 by the rules: " + each);
            assertEquals(ValueSet.just(ZERO), each.at(B), "and so is b: " + each);
        }
    }

    /**
     * Where a position really is left nothing, it is said, and the same way round.
     *
     * <p>The other half: a reading answering no position for every bottom would be as wrong as one
     * answering the wrong position, and would say nothing an author could act on.
     */
    @Test
    void andWhereAPositionIsLeftNothingItIsSaid() {
        AdmissibleValues<String> here = at(A, ZERO).meet(at(A, ONE), sets);
        AdmissibleValues<String> beside = at(B, ZERO);

        for (AdmissibleValues<String> each
                : List.of(here.meet(beside, sets), beside.meet(here, sets))) {
            assertTrue(each.isBottom());
            assertEquals(ValueSet.NONE, each.at(A), "the rules leave a nothing: " + each);
            assertEquals(ValueSet.just(ZERO), each.at(B), "and b is left where its rule put it");
        }
    }
}
