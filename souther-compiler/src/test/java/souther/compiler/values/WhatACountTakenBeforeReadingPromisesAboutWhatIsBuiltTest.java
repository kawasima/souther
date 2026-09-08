package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading builds no more alternatives than were counted for it before it started.
 *
 * <p>A declaration is admitted under a count taken from its clauses before any of them is read, and
 * the whole of why that is enough is this. The count is a fold — a leaf costs one, a choice adds
 * and a conjunction multiplies — so the promise is an induction, and what is held here are its
 * steps. The base is that a leaf holds one alternative; the steps are that a choice holds at most
 * the sum and a conjunction at most the product.
 *
 * <p>Held at the connectives rather than only at the end of a real reading. The end of a reading is
 * where the two are tied together (an assertion in the check, over declarations a corpus happens to
 * hold), and what that catches is that they came apart; this is where they are kept from coming
 * apart, at the two operations either of them could be changed at.
 *
 * <p>What makes the induction reach the reading at all is that the count and the reading are one
 * {@link souther.compiler.check.ClauseReading} fold over the same clauses: a connective is
 * interpreted in one place, so there is no second walk to disagree about which shape is a choice.
 */
class WhatACountTakenBeforeReadingPromisesAboutWhatIsBuiltTest {

    private static final String A = "a";
    private static final String B = "b";
    private static final Value FIVE = Value.text("5");
    private static final Value SIX = Value.text("6");
    private static final Value ZERO = Value.text("0");
    private static final Value ONE = Value.text("1");

    /** How many alternatives a reading came to. One that admits nothing holds none of them, which
     *  is below every bound and is why it needs no case of its own. */
    private static int held(AdmissibleValues<String> values) {
        return values.held() instanceof AdmissibleValues.Held.Alternatives<String> it
                ? it.boxes().size() : 0;
    }

    /** What a clause of no connective is read into, whichever of them it is. */
    private static List<PlannedValues<String>> leaves() {
        return List.of(
                PlannedValues.top(),
                PlannedValues.at(A, AdmittedPlan.of(ValueSet.just(FIVE))),
                PlannedValues.at(A, AdmittedPlan.of(ValueSet.allBut(FIVE))),
                PlannedValues.at(B, AdmittedPlan.of(ValueSet.just(ZERO))),
                PlannedValues.at(A, AdmittedPlan.NONE),
                PlannedValues.unreadable(Set.of(A), UnreadReason.FORM_NOT_READ),
                PlannedValues.unreadable(Set.of(), UnreadReason.FORM_NOT_READ));
    }

    /** What puts the sets of one reading together. Every set here is written out, so nothing is
     *  built and no allowance is spent. */
    private final Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

    /** A description worked out, which is where the alternatives are counted. */
    private AdmissibleValues<String> built(PlannedValues<String> planned) {
        return planned.resolve(sets).values();
    }

    /**
     * Either reading holding, as the holder of both languages settles it.
     *
     * <p>A branch nobody can be in is not composed — what the choice leaves is the branch that
     * stands — and where neither can be there is nothing to compose and the settlement says so.
     * The induction is over what the fold does, so it is over these four cases and not over an
     * operation the fold never reaches for.
     */
    private PlannedValues<String> either(PlannedValues<String> one, PlannedValues<String> other,
                                         boolean apart) {
        return switch (Emptiness.Alternatives.of(said(one), said(other))) {
            case NEITHER_STANDS -> one.bothDead(other);
            case ONLY_THE_RIGHT -> other;
            case ONLY_THE_LEFT -> one;
            case BOTH_STAND -> apart ? one.joinLiveApart(other) : one.joinLive(other);
        };
    }

    /** This branch's fate, in the words the classification is read in. */
    private Emptiness said(PlannedValues<String> planned) {
        return planned.holdsNothingAsBuilt(sets) ? Emptiness.EMPTY : Emptiness.NONEMPTY;
    }

    /** The same, and everything one connective reaches from them. */
    private List<PlannedValues<String>> readings() {
        List<PlannedValues<String>> out = new ArrayList<>(leaves());
        for (PlannedValues<String> one : leaves()) {
            for (PlannedValues<String> other : leaves()) {
                out.add(one.meet(other));
                out.add(either(one, other, true));
                out.add(either(one, other, false));
            }
        }
        return out;
    }

    /** The base: a clause this reads is one alternative, and a clause it cannot read is one too —
     *  what a rule with no word for it leaves is every value, which is a product like any other. */
    @Test
    void aLeafIsOneAlternative() {
        for (PlannedValues<String> each : leaves()) {
            assertTrue(held(built(each)) <= 1, each + " is more than one alternative");
        }
        assertEquals(1, held(built(PlannedValues.at(A, AdmittedPlan.of(ValueSet.just(FIVE))))));
        assertEquals(1, held(built(
                PlannedValues.unreadable(Set.of(A), UnreadReason.FORM_NOT_READ))));
    }

    /** A choice holds at most the sum, which is what the count adds for one. */
    @Test
    void aChoiceHoldsAtMostTheSum() {
        for (PlannedValues<String> one : readings()) {
            for (PlannedValues<String> other : readings()) {
                int apart = held(built(one)) + held(built(other));
                assertTrue(held(built(either(one, other, true))) <= apart, one + " || " + other);
                assertTrue(held(built(either(one, other, false))) <= apart,
                        "and merged it holds no more than that: " + one + " || " + other);
            }
        }
    }

    /** And a conjunction at most the product, which is what the count multiplies for one. */
    @Test
    void aConjunctionHoldsAtMostTheProduct() {
        for (PlannedValues<String> one : readings()) {
            for (PlannedValues<String> other : readings()) {
                assertTrue(held(built(one.meet(other))) <= held(built(one)) * held(built(other)),
                        one + " && " + other);
            }
        }
    }

    /**
     * And both bounds are reached, so they are bounds and not artefacts of what these hold.
     *
     * <p>An inequality on its own is one a reading holding nothing would pass.
     */
    @Test
    void andBothBoundsAreReached() {
        PlannedValues<String> here = PlannedValues.at(A, AdmittedPlan.of(ValueSet.just(FIVE)))
                .joinLiveApart(PlannedValues.at(A, AdmittedPlan.of(ValueSet.just(SIX))));
        PlannedValues<String> there = PlannedValues.at(B, AdmittedPlan.of(ValueSet.just(ZERO)))
                .joinLiveApart(PlannedValues.at(B, AdmittedPlan.of(ValueSet.just(ONE))));

        assertEquals(2, held(built(here)), "a choice of two, and the sum of one and one is two");
        assertEquals(4, held(built(here.meet(there))),
                "and a conjunction of two by two, none of whose pairs is empty, is four");
    }
}
