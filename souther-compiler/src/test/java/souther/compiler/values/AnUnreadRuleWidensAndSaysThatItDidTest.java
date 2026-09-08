package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading that could not take a rule in leaves, and what it says about having left it.
 *
 * <p>The whole risk of reading values out of rules is here. A reading that answers "no value" from a
 * rule it did not read refuses a model somebody can write, so a rule left unread has to widen and
 * never narrow; and a position left wide because a rule was unread is not a position the model says
 * nothing about, so the two have to be told apart.
 *
 * <p>The connectives do not treat an unread rule alike, which is what these are written around. A
 * rule stated beside others only narrows, so one that went unread costs precision at the positions
 * it named. A rule stated as an alternative to others widens, so one that went unread takes back
 * everything the other alternative said — including at positions it never mentions.
 */
class AnUnreadRuleWidensAndSaysThatItDidTest {

    private static final String VALUE = "value";
    private static final String OTHER = "other";
    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** What puts the sets of these readings together. Every set here is values written out, so
     *  nothing is built and no allowance is spent. */
    private final Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

    private AdmissibleValues<String> says(String atom, Value value) {
        return built(plans(atom, value));
    }

    /** The same rule while it is still a description, which is where a choice between two of them
     *  is taken. */
    private static PlannedValues<String> plans(String atom, Value value) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(value)));
    }

    private static PlannedValues<String> unread(Set<String> named) {
        return PlannedValues.unreadable(named, UnreadReason.FORM_NOT_READ);
    }

    /** A description worked out, which is how a reading is come by. */
    private AdmissibleValues<String> built(PlannedValues<String> planned) {
        return planned.resolve(sets).values();
    }

    /** A description worked out and told what its unread alternatives left open, which is what a
     *  reader is handed. Said once, where the whole of what the clauses came to is in hand. */
    private AdmissibleValues<String> opened(PlannedValues<String> planned, Set<String> these) {
        return planned.resolve(sets).values().alsoOpenedAt(these);
    }

    /** A rule read and nothing else is what it says, and this can speak for the position. */
    @Test
    void aRuleReadIsWhatItSays() {
        AdmissibleValues<String> read = says(VALUE, A);
        assertEquals(ValueSet.just(A), read.at(VALUE));
        assertTrue(read.speaksFor(VALUE));
        assertFalse(read.isBottom());
    }

    /** Two of them stated together leave nothing, and that is a refusal. */
    @Test
    void twoRulesStatedTogetherCanLeaveNothing() {
        assertTrue(says(VALUE, A).meet(says(VALUE, B), sets).isBottom());
    }

    /** Stated as alternatives the same two leave both values, and refuse nothing. */
    @Test
    void theSameTwoAsAlternativesLeaveBoth() {
        AdmissibleValues<String> either = opened(plans(VALUE, A).joinLive(plans(VALUE, B)),
                Set.of());
        assertEquals(ValueSet.oneOf(Set.of(A, B)), either.at(VALUE));
        assertFalse(either.isBottom());
        assertTrue(either.speaksFor(VALUE));
    }

    /**
     * A rule this could not read, stated beside one it could, narrows nothing and refuses nothing.
     *
     * <p>And leaves the position it does not name spoken for. Nothing here relates one position to
     * another, so a rule that narrows a position names it — which is what makes this safe rather
     * than merely convenient.
     */
    @Test
    void anUnreadRuleStatedBesideAnotherNarrowsNothing() {
        AdmissibleValues<String> both = says(VALUE, A)
                .meet(built(unread(Set.of())), sets);
        assertEquals(ValueSet.just(A), both.at(VALUE));
        assertFalse(both.isBottom());
        assertTrue(both.speaksFor(VALUE));
    }

    /** And where it names a position, that position is one this can no longer speak for. */
    @Test
    void anUnreadRuleNamingAPositionLeavesItSpokenForByNothing() {
        AdmissibleValues<String> both =
                says(VALUE, A).meet(built(unread(Set.of(OTHER))), sets);
        assertTrue(both.speaksFor(VALUE));
        assertFalse(both.speaksFor(OTHER));
        assertEquals(ValueSet.ANY, both.at(OTHER));
    }

    /**
     * A rule this could not read, stated as an alternative, takes back what the other alternative
     * said.
     *
     * <p>Even where it names nothing. A value satisfying the branch this could not read is under no
     * obligation from the branch it could, so the position is open — and a reading that merged the
     * two maps would answer that the model admits one value there.
     */
    @Test
    void anUnreadAlternativeTakesBackWhatTheOtherSaid() {
        AdmissibleValues<String> either =
                opened(plans(VALUE, A).joinLive(unread(Set.of())), Set.of(VALUE));
        assertEquals(ValueSet.ANY, either.at(VALUE));
        assertFalse(either.isBottom());
    }

    /**
     * And says so: the position is open because of this reading and not because of the model.
     *
     * <p>The two are the same answer about the values and opposite answers to a reader asking what
     * the model divides. Whichever side the unread alternative is written on.
     */
    @Test
    void aPositionLeftOpenByAnUnreadAlternativeIsNotOneNothingWasSaidAbout() {
        assertFalse(opened(plans(VALUE, A).joinLive(unread(Set.of())), Set.of(VALUE))
                .speaksFor(VALUE));
        assertFalse(opened(unread(Set.of()).joinLive(plans(VALUE, A)), Set.of(VALUE))
                .speaksFor(VALUE));
    }

    /** A position neither alternative spoke about is open because neither said anything, which this
     * can speak for. */
    @Test
    void aPositionNeitherAlternativeSpokeAboutIsOpenAndSpokenFor() {
        AdmissibleValues<String> either = opened(plans(VALUE, A).joinLive(plans(VALUE, B)),
                Set.of());
        assertEquals(ValueSet.ANY, either.at(OTHER));
        assertTrue(either.speaksFor(OTHER));
    }

    /**
     * A position only one alternative spoke about is open, and open because of the model.
     *
     * <p>The other branch admits every value there, so every value is admitted — and nothing about
     * that answer was lost by this reading.
     */
    @Test
    void aPositionOneAlternativeLeftOpenIsOpenBecauseTheModelLeavesItSo() {
        AdmissibleValues<String> either =
                opened(plans(VALUE, A).joinLive(PlannedValues.top()), Set.of());
        assertEquals(ValueSet.ANY, either.at(VALUE));
        assertTrue(either.speaksFor(VALUE));
    }

    /** Nothing read at all says nothing about anything and speaks for all of it. */
    @Test
    void aReadingOfNoRulesSaysNothingAndMissedNothing() {
        assertEquals(ValueSet.ANY, AdmissibleValues.<String>top().at(VALUE));
        assertTrue(AdmissibleValues.<String>top().speaksFor(VALUE));
        assertFalse(AdmissibleValues.<String>top().isBottom());
    }

    /**
     * What stopped a rule travels with the position it named.
     *
     * <p>Recovered afterwards, it could only be recovered from the rules again — and the reading
     * that could tell a rule relating two positions from one written in a form it does not take
     * apart is this one, at the moment it gave up.
     */
    @Test
    void whatStoppedARuleIsHeldWithThePositionItNamed() {
        AdmissibleValues<String> read = built(
                PlannedValues.unreadable(Set.of(VALUE), UnreadReason.RELATES_TWO_POSITIONS));

        assertEquals(List.of(UnreadReason.RELATES_TWO_POSITIONS), read.whyUnread(VALUE));
        assertEquals(List.of(), read.whyUnread(OTHER),
                "a position no rule named is one nothing stopped");
    }

    /**
     * A position an alternative takes back says that an alternative took it back.
     *
     * <p>What happened to it, and not what the unread rule was about. The position is open because
     * a value satisfying the other branch is under no obligation from this one — and the rule over
     * there may name positions this one never mentions, so borrowing its reason would say of this
     * position something no rule said about it.
     *
     * <p>The position that rule named is a different matter, and the choice speaks for it. The
     * alternative that was read says nothing about {@code other}, so it admits every value there —
     * and a choice one of whose alternatives admits every value at a position admits every value at
     * it, whatever the alternative beside it turned out to say. The rule is still one nothing read,
     * and where that is the question the accounting of rules answers it rather than this. Which
     * position keeps a reason of its own is {@link #aRuleThatNamedThePositionOutranksTheBranchThatWidenedIt}.
     */
    @Test
    void aPositionTakenBackByAnAlternativeSaysAnAlternativeTookItBack() {
        AdmissibleValues<String> either = opened(plans(VALUE, A).joinLive(
                PlannedValues.unreadable(Set.of(OTHER), UnreadReason.RELATES_TWO_POSITIONS)),
                Set.of(VALUE));

        assertEquals(List.of(UnreadReason.ALTERNATIVE_NOT_READ), either.whyUnread(VALUE));
        assertTrue(either.speaksFor(OTHER),
                "the alternative that was read admits every value there, so the choice does");
    }

    /** And where a rule already named the position, that is what is said: a rule written about this
     *  position is nearer than a branch that widened it from outside. */
    @Test
    void aRuleThatNamedThePositionOutranksTheBranchThatWidenedIt() {
        AdmissibleValues<String> either = opened(
                PlannedValues.<String>unreadable(Set.of(VALUE),
                                UnreadReason.RELATES_TWO_POSITIONS)
                        .meet(plans(OTHER, A))
                        // The alternative beside the unread one holds a clause nothing read as
                        // well, so it promised nothing for the other to take back and the choice
                        // opened nowhere.
                        .joinLive(unread(Set.of())), Set.of());

        assertEquals(List.of(UnreadReason.RELATES_TWO_POSITIONS), either.whyUnread(VALUE));
    }
}
