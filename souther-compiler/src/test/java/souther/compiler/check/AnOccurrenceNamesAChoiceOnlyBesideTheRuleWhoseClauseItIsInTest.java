package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.Refusal;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Where in a clause a choice stands names it, and it names it beside that clause and nowhere else.
 *
 * <p>An occurrence is counted within one clause, so two rules of one declaration each write a
 * choice at their own occurrence nought and they are two choices. What pairs an occurrence with the
 * clause it is of is {@link ChoicesDecided}, which is the only thing holding both — everything that
 * reads a fate is handed a {@link ChoicesOfRule}, which is this table already scoped and has no
 * rule in it to get wrong.
 *
 * <p>Written here and not left to a corpus. Collapsing the rule out of the table leaves every
 * conformance source of this repository answering exactly as it did: no two clauses of one
 * declaration were found writing a choice at one occurrence and coming to different fates. A
 * property nothing written can reach is one a test has to state, or the scope holds by nothing but
 * the shape of what somebody happened to write.
 */
class AnOccurrenceNamesAChoiceOnlyBesideTheRuleWhoseClauseItIsInTest {

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject LEFT_ALONE = FactSubject.of(NAMES.written("leftAlone"));
    private static final FactSubject HELD_DOWN = FactSubject.of(NAMES.written("heldDown"));

    private static final RuleRef.Invariant ONE = rule(0);
    private static final RuleRef.Invariant OTHER = rule(1);

    /** The same coordinate of two clauses, which two rules number alike and write apart. */
    private static final ClauseOccurrence AT = new ClauseOccurrence(0);

    @Test
    void twoRulesWritingAChoiceAtOneOccurrenceHaveTwoFates() {
        Settlement.OfAChoice mine = fateLeaving(LEFT_ALONE);
        Settlement.OfAChoice theirs = fateLeaving(HELD_DOWN);

        ChoicesDecided decided = new ChoicesDecided();
        decided.settled(ONE, AT, mine);
        decided.settled(OTHER, AT, theirs);

        assertEquals(mine, decided.of(ONE).at(AT),
                "the fate of the choice one rule wrote is that rule's");
        assertEquals(theirs, decided.of(OTHER).at(AT),
                "and the fate of the choice the other wrote is the other's");
    }

    /**
     * And a rule that wrote no choice there is told so rather than handed a neighbour's.
     *
     * <p>The half the assertion above cannot reach: two fates that happened to agree would pass it
     * whichever table answered. A rule with nothing at the occurrence has no fate of its own to
     * compare with, so what is asked is that nothing is what comes back.
     */
    @Test
    void andARuleThatWroteNoChoiceThereIsAnsweredWithNone() {
        ChoicesDecided decided = new ChoicesDecided();
        decided.settled(ONE, AT, fateLeaving(LEFT_ALONE));

        assertNull(decided.of(OTHER).at(AT),
                "the other rule wrote no choice at this occurrence of its own clause");
    }

    /** A fate told from the next one by what its alternatives were left holding down. */
    private static Settlement.OfAChoice fateLeaving(FactSubject position) {
        // Qualified, this package declaring an `Emptiness` of its own.
        Settlement.Sided side = Settlement.Sided.settledAs(new Confinement.Admission<>(
                souther.compiler.values.Emptiness.NONEMPTY, Confinement.EmptyBy.NOTHING_SHOWN,
                Refusal.nowhere(), Confinement.Shown.BY_THE_READINGS));
        return new Settlement.OfAChoice(side, side, Settlement.WidthDependency.none(),
                new WhatTheAlternativesLeave(Set.of(position), Set.of(), Set.of(), true));
    }

    private static RuleRef.Invariant rule(int ordinal) {
        return new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("demo", "Held")), ordinal),
                Optional.empty()));
    }
}
