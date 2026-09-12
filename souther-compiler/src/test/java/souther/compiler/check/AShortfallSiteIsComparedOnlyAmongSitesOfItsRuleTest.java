package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourcePos;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.UnreadReason;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A written place a reading gave up at is one occurrence of one clause, and two of them are compared
 * where the clause is already chosen.
 *
 * <p>An occurrence is counted within one clause, so the site of a shortfall says which part of its
 * clause it is and not which clause. What supplies the clause is whoever filed the shortfall:
 * {@link ReadingEvidence} keeps them under the rule they are of, and the set that tells two of them
 * apart is reached only after a rule is named. A site holding the rule as well would be a second
 * answer to which rule the filing is about, and two of them can be built disagreeing.
 *
 * <p><b>What this does not say.</b> Not that shortfalls of two rules never meet — a report listing
 * what a declaration left open puts them in one list and is right to. What it says is that nothing
 * decides whether two of them are one thing by comparing sites across rules, which is the operation
 * an occurrence cannot answer.
 */
class AShortfallSiteIsComparedOnlyAmongSitesOfItsRuleTest {

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject POSITION = FactSubject.of(NAMES.written("position"));

    private static final RuleRef.Invariant ONE = rule(0);
    private static final RuleRef.Invariant OTHER = rule(1);

    /**
     * Two shortfalls alike in everything but the rule they are of, each kept where it was filed.
     *
     * <p>Alike on purpose. The position, the reason and the occurrence are the whole of what a
     * shortfall is, so these two compare equal and a single set would hold one of them — which is
     * why the rule is what the set is reached through rather than what it contains.
     */
    @Test
    void whatWasFiledUnderOneRuleIsNotAnsweredFromTheOther() {
        RuleShortfall mine = shortfall();
        RuleShortfall theirs = shortfall();

        ReadingEvidence evidence = new ReadingEvidence();
        evidence.stoppedBy(ONE, Set.of(mine));
        evidence.stoppedBy(OTHER, Set.of(theirs));

        assertEquals(Set.of(mine), evidence.stoppedBy(ONE, List.of(POSITION)),
                "what stopped the reading of one rule is that rule's");
        assertEquals(Set.of(theirs), evidence.stoppedBy(OTHER, List.of(POSITION)),
                "and the other rule is answered out of its own filing and not out of the first's");
    }

    /**
     * And a rule nothing was filed under is told so rather than handed a neighbour's.
     *
     * <p>The half the assertion above cannot reach, the two being equal: a table that answered
     * every rule out of one filing would pass it. A rule with nothing of its own has nothing to
     * compare, so what is asked is that nothing comes back.
     */
    @Test
    void andARuleNothingStoppedIsAnsweredWithNothing() {
        ReadingEvidence evidence = new ReadingEvidence();
        evidence.stoppedBy(ONE, Set.of(shortfall()));

        assertEquals(Set.of(), evidence.stoppedBy(OTHER, List.of(POSITION)),
                "nothing was filed under the other rule, and the first rule's is not its answer");
    }

    /** One position left open at one occurrence, for whichever rule it is filed under. */
    private static RuleShortfall shortfall() {
        return new RuleShortfall(POSITION, UnreadReason.ALTERNATIVE_NOT_READ,
                new ChoiceSite(new ClauseOccurrence(0), new SourcePos(1, 1)));
    }

    private static RuleRef.Invariant rule(int ordinal) {
        return new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("demo", "Held")), ordinal),
                Optional.empty()));
    }
}
