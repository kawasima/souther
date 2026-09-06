package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An alternative that already admits every value at a position settles it, whatever went unread
 * beside it.
 *
 * <p>ANY is the top of this lattice. A choice one of whose alternatives admits every value there is
 * admits every value there is, so there is nothing wider for an unread rule to widen it to — and a
 * reading that answered otherwise would report a position the model leaves open as one it could not
 * follow.
 *
 * <p><b>Whichever way the alternatives are bracketed.</b> {@code ||} is one connective and not a
 * tree, so a reading that says one thing about {@code (a || b) || c} and another about
 * {@code a || (b || c)} is reading the brackets. That is what the guarantee is carried for: the
 * alternatives that can be read cover the position between them, and the reading has to still know
 * it after one of them has been joined with something it could not read.
 */
class AChoiceThatAlreadyAdmitsEverythingIsNotWidenedTest {

    private static final String VALUE = "value";
    private static final String OTHER = "other";
    private static final Value FIVE = Value.text("5");

    /** What puts the sets of one reading together. Every set here is written out, so nothing is
     *  built and no allowance is spent. */
    private final Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

    /** {@code value == 5}, while it is still a description — which is where a choice is taken. */
    private static PlannedValues<String> is5() {
        return PlannedValues.at(VALUE, AdmittedPlan.of(ValueSet.just(FIVE)));
    }

    /** {@code value /= 5}. */
    private static PlannedValues<String> not5() {
        return PlannedValues.at(VALUE, AdmittedPlan.of(ValueSet.allBut(FIVE)));
    }

    /** A rule about the position that this reading has no word for. */
    private static PlannedValues<String> unread() {
        return PlannedValues.unreadable(Set.of(VALUE), UnreadReason.FORM_NOT_READ);
    }

    /** A description worked out and told what its unread alternatives left open, which is what a
     *  reader is handed. Said once, where the whole of what the clauses came to is in hand. */
    private AdmissibleValues<String> opened(PlannedValues<String> planned, Set<String> these) {
        return planned.resolve(sets).values().alsoOpenedAt(these);
    }

    /** What a choice beside {@link #unread()} left open, which is what the alternative beside it
     *  promised. Decided where the clause an author wrote meets what became of its branches, and
     *  told to the answer once it is one. */
    private static final Set<String> OPENED_AT_VALUE = Set.of(VALUE);

    /** Two alternatives that cover the position between them leave nothing to widen. */
    @Test
    void twoAlternativesCoveringThePositionLeaveNothingForAnUnreadOneToWiden() {
        AdmissibleValues<String> either =
                opened(is5().joinLive(not5()).joinLive(unread()), OPENED_AT_VALUE);

        assertEquals(ValueSet.ANY, either.at(VALUE));
        assertTrue(either.speaksFor(VALUE),
                "the two readable alternatives admit every value, so the choice does");
    }

    /** And the same however the alternatives are bracketed. */
    @Test
    void theSameHoweverTheAlternativesAreBracketed() {
        AdmissibleValues<String> either =
                opened(is5().joinLive(not5().joinLive(unread())), OPENED_AT_VALUE);

        assertEquals(ValueSet.ANY, either.at(VALUE));
        assertTrue(either.speaksFor(VALUE),
                "what the readable alternatives cover is not lost by joining one of them first");
    }

    /**
     * A conjunction is not read this way, and that is the boundary of the rule.
     *
     * <p>{@link AdmissibleValues#guaranteedAt} falls to nothing under a meet with a rule this could
     * not read, because such a rule may exclude as much as it likes. Discharging unreadness by
     * comparing the two ends there would report every position of every value with one unreadable
     * clause as one this reading is short of — and what an unread rule stated beside others costs
     * is answered by the positions it names, which is the account a conjunction has always kept.
     */
    @Test
    void aConjunctionKeepsItsOwnAccountOfWhatWentUnread() {
        AdmissibleValues<String> both = opened(is5().meet(
                PlannedValues.unreadable(Set.of(), UnreadReason.FORM_NOT_READ)), Set.of());

        assertEquals(ValueSet.NONE, both.guaranteedAt(VALUE),
                "an unread conjunct may exclude anything, so nothing is guaranteed under it");
        assertEquals(ValueSet.just(FIVE), both.at(VALUE));
        assertTrue(both.speaksFor(VALUE),
                "and the position is still spoken for: the unread rule names none");
    }

    /**
     * An alternative that may itself admit nothing covers nothing, and cannot settle a position for
     * the choice.
     *
     * <p>The boundary of the rule on the other side. {@code value == 5} read whole admits every
     * value at {@code other} and settles it; the same rule stated beside one this could not read
     * does not, because the rule beside it may be one nothing satisfies and then the choice is the
     * alternative that was left. What an alternative guarantees is what it guarantees having read
     * everything it was given, and a conjunct nothing could read is exactly what it was not given.
     */
    @Test
    void anAlternativeThatMayAdmitNothingSettlesNothing() {
        AdmissibleValues<String> either = opened(is5()
                .meet(PlannedValues.unreadable(Set.of(), UnreadReason.FORM_NOT_READ))
                // The alternative beside the unread one holds a clause nothing read as well, so it
                // promised nothing for the other to take back and the choice opened nowhere.
                .joinLive(PlannedValues.unreadable(Set.of(OTHER), UnreadReason.FORM_NOT_READ)),
                Set.of());

        assertEquals(List.of(UnreadReason.FORM_NOT_READ), either.whyUnread(OTHER),
                "the alternative beside the unread one may admit nothing, so it vouches for nothing");
    }

    /**
     * A position a choice has settled stays settled under a further alternative.
     *
     * <p>The choice still knows a rule went unread somewhere in it, which is what the accounting of
     * rules is owed and what a further alternative reads to spoil the positions beside it. What is
     * carried past that is the cover: a position every value stands at is one no alternative can
     * widen, so writing one more of them leaves it where it was. Whichever side the further
     * alternative is written on.
     */
    @Test
    void aPositionTheChoiceHasSettledStaysSettledUnderAFurtherAlternative() {
        PlannedValues<String> covered = is5().joinLive(not5()).joinLive(unread());
        PlannedValues<String> beside =
                PlannedValues.at(OTHER, AdmittedPlan.of(ValueSet.just(Value.text("A"))));
        Set<String> openedAtBoth = Set.of(VALUE, OTHER);

        assertFalse(opened(covered, OPENED_AT_VALUE).standing().isEmpty(),
                "a rule of it did go unread, and that is not taken back");
        assertTrue(opened(covered.joinLive(beside), openedAtBoth).speaksFor(VALUE));
        assertTrue(opened(beside.joinLive(covered), openedAtBoth).speaksFor(VALUE),
                "and either way round");
        assertTrue(opened(covered.joinLive(beside), openedAtBoth).speaksFor(OTHER),
                "and the position the further alternative names is covered by the settled one");
    }

    /** The alternative that could not be read covers nothing, so a position only it reaches is one
     *  the choice is still short of. */
    @Test
    void anAlternativeNothingCouldReadCoversNothing() {
        AdmissibleValues<String> either = opened(is5().joinLive(unread()), OPENED_AT_VALUE);

        assertEquals(ValueSet.ANY, either.at(VALUE));
        assertEquals(List.of(UnreadReason.FORM_NOT_READ), either.whyUnread(VALUE),
                "one alternative admits one value and the other is unknown, so nothing covers it");
    }
}
