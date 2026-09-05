package souther.compiler.values;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two ends of a reading, whichever way one was built.
 *
 * <p>{@link AdmissibleValues#guaranteedAt} is a lower approximation and {@link AdmissibleValues#at}
 * an upper one, so what is guaranteed is admitted. A state where it is not says a value stands at a
 * position its own answer excludes, and the reading that says so would discharge an unread rule on
 * evidence of nothing.
 *
 * <p>Written over the ways a state is made rather than over an example of one. This is what the
 * representation has to keep, not what one reading of one model comes to, and a constructor added
 * without it is a state the join would read as covering more than it admits.
 */
class WhatIsGuaranteedIsNeverMoreThanWhatIsAdmittedTest {

    private static final String VALUE = "value";
    private static final String OTHER = "other";
    private static final String NEITHER = "neither";
    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /**
     * What puts the sets of these readings together.
     *
     * <p>One for the file, since the states are made where a parameter source can reach them. Every
     * set here is values written out, so nothing is built and no allowance is spent — a composer
     * shared between two of these is spending nothing either of them would have wanted.
     */
    private static final Allowance<String> SETS = AsACompilationAllows.forAdmittedValues();

    private static AdmissibleValues<String> says(String atom, Value value) {
        return AdmissibleValues.at(atom, ValueSet.just(value));
    }

    private static AdmissibleValues<String> unreadable(Set<String> named) {
        return AdmissibleValues.unreadable(named, UnreadReason.FORM_NOT_READ);
    }

    private static Arguments made(String how, AdmissibleValues<String> state) {
        return Arguments.of(Named.of(how, state));
    }

    static Stream<Arguments> states() {
        return Stream.of(
                made("top", AdmissibleValues.top()),
                made("at", says(VALUE, A)),
                made("unreadable naming a position", unreadable(Set.of(VALUE))),
                made("unreadable naming none", unreadable(Set.of())),
                made("leavingNothing", says(VALUE, A).leavingNothing()),
                made("meet of two read", says(VALUE, A).meet(says(OTHER, B), SETS)),
                made("meet with an unread", says(VALUE, A).meet(unreadable(Set.of(OTHER)), SETS)),
                made("meet leaving nothing", says(VALUE, A).meet(says(VALUE, B), SETS)),
                made("join of two read", says(VALUE, A).join(says(VALUE, B), SETS)),
                made("join with an unread", says(VALUE, A).join(unreadable(Set.of(VALUE)), SETS)
                        .alsoOpenedAt(Set.of(VALUE))),
                made("join covering the position",
                        AdmissibleValues.at(VALUE, ValueSet.just(A))
                                .join(AdmissibleValues.at(VALUE, ValueSet.allBut(A)), SETS)),
                made("join of two that leave nothing",
                        says(VALUE, A).meet(says(VALUE, B), SETS)
                                .join(says(OTHER, A).meet(says(OTHER, B), SETS), SETS)),
                made("join of a meet and an unread",
                        says(VALUE, A).meet(unreadable(Set.of()), SETS)
                                .join(says(VALUE, B), SETS).alsoOpenedAt(Set.of(VALUE))),
                made("choice over two positions",
                        says(VALUE, A).meet(says(OTHER, A), SETS)
                                .join(says(VALUE, B).meet(says(OTHER, B), SETS), SETS)),
                made("choice over two positions, met",
                        says(VALUE, A).meet(says(OTHER, A), SETS)
                                .join(says(VALUE, B).meet(says(OTHER, B), SETS), SETS)
                                .meet(says(VALUE, A), SETS)),
                made("heldApart", AdmissibleValues.heldApart(VALUE, OTHER)),
                made("meet with heldApart",
                        says(VALUE, A).meet(AdmissibleValues.heldApart(VALUE, OTHER), SETS)),
                made("join with heldApart",
                        says(VALUE, A).join(AdmissibleValues.heldApart(VALUE, OTHER), SETS)),
                made("join nested under a join",
                        says(VALUE, A).join(says(VALUE, B)
                                .join(unreadable(Set.of(VALUE)), SETS).alsoOpenedAt(Set.of(VALUE)),
                                SETS).alsoOpenedAt(Set.of(VALUE))));
    }

    /**
     * A reading that admits nothing guarantees nothing, at every position and not only at the one
     * that emptied.
     *
     * <p>{@link AdmissibleValues#at} answers ANY at a position such a reading never named, which is
     * what a caller reading one position at a time is owed and is not what stands there: no value
     * of this type exists at all, so nothing is admitted anywhere. A guarantee left standing beside
     * that would be a lower approximation of a set with nothing in it.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("admittingNothing")
    void aReadingThatAdmitsNothingGuaranteesNothing(AdmissibleValues<String> state) {
        for (String atom : List.of(VALUE, OTHER, NEITHER)) {
            assertEquals(ValueSet.NONE, state.guaranteedAt(atom),
                    () -> "at " + atom + " of a reading that admits nothing");
        }
    }

    /**
     * A position held apart from another is guaranteed nothing, and one beside it keeps what it had.
     *
     * <p>A guarantee is a lower approximation: these values stand there whatever else is read. No
     * value can be shown to stand at one of two positions held apart without an assignment for the
     * other — over a carrier of one value a denial admits nothing at all — so the honest guarantee
     * at both of them is none. What the rules leave them is still every value, which is the upper
     * approximation and a different question.
     *
     * <p>Said where the rule is read rather than wherever a denial is met with something. Meeting a
     * guarantee with nothing leaves nothing, so every conjunction the rule reaches has it without a
     * second rule saying so — and a choice beside it keeps what its other branch guarantees, which
     * is right, because a value satisfying that branch is under no denial.
     */
    @Test
    void aPositionHeldApartFromAnotherIsGuaranteedNothing() {
        AdmissibleValues<String> apart = AdmissibleValues.heldApart(VALUE, OTHER);

        assertEquals(ValueSet.NONE, apart.guaranteedAt(VALUE));
        assertEquals(ValueSet.NONE, apart.guaranteedAt(OTHER));
        assertEquals(ValueSet.ANY, apart.at(VALUE), "and the rules leave it every value");

        assertEquals(ValueSet.NONE,
                says(VALUE, A).meet(apart, SETS).guaranteedAt(VALUE),
                "a conjunction the rule reaches guarantees nothing there");
        assertEquals(ValueSet.just(A),
                says(VALUE, A).join(apart, SETS).guaranteedAt(VALUE),
                "and a branch beside it keeps what it guarantees on its own");
    }

    static Stream<Arguments> admittingNothing() {
        return Stream.of(
                made("a position left no value", AdmissibleValues.at(VALUE, ValueSet.NONE)),
                made("two rules leaving nothing", says(VALUE, A).meet(says(VALUE, B), SETS)),
                made("shown impossible from outside", says(VALUE, A).leavingNothing()),
                made("a meet under a wider one",
                        says(OTHER, A).meet(says(VALUE, A), SETS).meet(says(VALUE, B), SETS)),
                made("two alternatives neither of which can be taken",
                        says(VALUE, A).meet(says(VALUE, B), SETS)
                                .join(says(OTHER, A).meet(says(OTHER, B), SETS), SETS)));
    }

    /**
     * A guarantee a conjunction was read from is a guarantee about the conjunction.
     *
     * <p>What is held is one set per position, which stands for the product of those sets — every
     * choice of one value per position standing together. A choice between two alternatives that
     * each name two positions is not that: the union of two products is not a product, and the
     * smallest one containing it holds pairs neither alternative does.
     *
     * <p>Left as a guarantee, the pairs it gained are what a conjunction beside it is then unable to
     * refuse. Below, each choice leaves {@code value} open on its own and the two of them together
     * leave only {@code value = A}: an {@code other} of {@code B} is asked for by one and refused by
     * the other. A reading that guaranteed every value at {@code value} here would hand a choice
     * around it a cover for a position the rules hold to one value.
     */
    @Test
    void aChoiceOverTwoPositionsLeavesNoGuaranteeForAConjunctionToRestOn() {
        AdmissibleValues<String> together = says(VALUE, A).meet(says(OTHER, A), SETS);
        AdmissibleValues<String> apart = says(VALUE, B).meet(says(OTHER, B), SETS);
        AdmissibleValues<String> otherB = says(VALUE, B).meet(says(OTHER, A), SETS);

        AdmissibleValues<String> one = together.join(apart, SETS);
        AdmissibleValues<String> two = together.join(otherB, SETS);
        AdmissibleValues<String> both = one.meet(two, SETS);

        assertEquals(ValueSet.oneOf(Set.of(A, B)), both.at(VALUE),
                "read one position at a time, both choices leave value open to either");
        assertEquals(ValueSet.NONE, both.guaranteedAt(VALUE),
                "but only value = A stands, so neither value is one this reading can promise");
    }

    /**
     * A conjunction always leaves a promise about whole values.
     *
     * <p>Two of them met is one: a value taken from each position of both stands in both readings.
     * And where a side of it promised nothing about whole values, what comes out promises nothing
     * at all, which is a promise about whole values for want of anything to promise. Neither case
     * has a conjunction saying its promise is not one — a reading that said so would have a later
     * conjunction refuse to compose promises that were there to compose.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    void aConjunctionAlwaysLeavesAPromiseAboutWholeValues(AdmissibleValues<String> state) {
        assertTrue(state.meet(says(OTHER, B), SETS).guaranteedTogether(),
                "met with a rule about one position");
        assertTrue(state.meet(state, SETS).guaranteedTogether(), "and met with itself");
        assertTrue(state.meet(AdmissibleValues.<String>unreadable(Set.of(VALUE),
                UnreadReason.FORM_NOT_READ), SETS).guaranteedTogether(),
                "and met with a rule nothing could read");
    }

    /** Every value guaranteed at a position is a value admitted there. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    void whatIsGuaranteedIsAdmitted(AdmissibleValues<String> state) {
        for (String atom : List.of(VALUE, OTHER, NEITHER)) {
            ValueSet guaranteed = state.guaranteedAt(atom);
            assertEquals(guaranteed,
                    SETS.meet(state.blockOf(atom), guaranteed, state.at(atom)).set(),
                    () -> "at " + atom + ": guaranteed " + guaranteed
                            + " is not within admitted " + state.at(atom));
        }
    }
}
