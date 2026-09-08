package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An account of the rules applies what a choice left open and never works it out.
 *
 * <p>What each component of a choice's account is allowed to depend on, said as laws rather than as
 * cases. An account holds two things that came from two places — what this reading did with the
 * clause, and what the settlement decided the choice does to that — and the whole of what keeps
 * them apart is that neither is reachable from the other. A rule that walked from one to the other
 * would be a second answer to a question something else has already answered, and the two would
 * agree only until one of them changed.
 *
 * <p><b>Which is the shape the defect had.</b> Told from the flag for a clause nothing read, the
 * answer that comes out is that a branch narrowing a position its neighbour narrows the same way
 * binds nothing there. The example that shows it is a model
 * ({@code WhetherAConstraintStillBindsIsReadOffWhatTheAlternativesLeaveTest}); what is here is the
 * rule the example is an instance of, so a derivation reintroduced by another road is caught by the
 * same test.
 */
class AnOpeningIsAppliedToAnAccountAndNeverDerivedFromItTest {

    private static final String CONSTRAINED = "constrained";
    private static final String SETTLED = "settled";
    private static final String LEFT_OUT = "left out";

    /** A branch that put a constraint on one position, settled another, and missed a third. */
    private static Adoption<String, ReadingLanguage.Values> branch(boolean unread) {
        return new Adoption<>(Set.of(CONSTRAINED), Set.of(SETTLED), Set.of(LEFT_OUT), unread,
                Set.of());
    }

    private static Opening<String, ReadingLanguage.Values> opening(String... positions) {
        return new Opening<>(Set.of(positions));
    }

    /**
     * Whether a clause of a branch went unread does not reach what the choice records reading.
     *
     * <p>The law the derivation broke. Flipping the flag on either side moves what the choice says
     * about the clause being unread and nothing else — what was constrained, what was settled and
     * what was missed are this reading's work, and an alternative going unread is not a thing that
     * happened to it.
     */
    @Test
    void whetherAPartWentUnreadReachesNothingButItself() {
        for (boolean left : new boolean[] {false, true}) {
            for (boolean right : new boolean[] {false, true}) {
                Adoption<String, ReadingLanguage.Values> said =
                        branch(left).either(opening(CONSTRAINED), branch(right));

                assertEquals(Set.of(CONSTRAINED), said.read(), "read is the branches' own");
                assertEquals(Set.of(SETTLED), said.settled(), "and so is settled");
                assertEquals(Set.of(LEFT_OUT), said.missed(), "and so is missed");
                assertEquals(Set.of(CONSTRAINED), said.opened(),
                        "and what the choice opened is what it was handed");
                assertEquals(left || right, said.hasUnreadPart(),
                        "which is the one thing the flags decide");
            }
        }
    }

    /**
     * And what a choice left open does not reach what the branches were read at.
     *
     * <p>The same law the other way round. An opening says a constraint does not bind; it does not
     * say the clause went unread, and an account that wrote it down as one would send an author to
     * a rule this compiler read whole.
     */
    @Test
    void whatAChoiceLeftOpenReachesNothingButItself() {
        Adoption<String, ReadingLanguage.Values> shut =
                branch(true).either(Opening.nothing(), branch(true));
        Adoption<String, ReadingLanguage.Values> open =
                branch(true).either(opening(CONSTRAINED), branch(true));

        assertEquals(shut.read(), open.read(), "read is the branches' own");
        assertEquals(shut.settled(), open.settled(), "and so is settled");
        assertEquals(shut.missed(), open.missed(), "and so is missed");
        assertEquals(shut.hasUnreadPart(), open.hasUnreadPart(), "and so is the flag");
        assertEquals(Set.of(), shut.opened());
        assertEquals(Set.of(CONSTRAINED), open.opened());
    }

    /**
     * What is opened is out of what was constrained, and the account refuses to hold anything else.
     *
     * <p>The settlement answers over the branches as they were met, which is not this one written
     * part, so it names positions this part never put a constraint on. Written down all the same,
     * the set would grow into a second account of what the clause was about — and the position it
     * named would be read as one this clause had something to say about.
     */
    @Test
    void whatIsOpenedIsOutOfWhatWasConstrained() {
        Adoption<String, ReadingLanguage.Values> said =
                branch(true).either(opening(CONSTRAINED, SETTLED, "elsewhere"), branch(true));

        assertEquals(Set.of(CONSTRAINED), said.opened(),
                "a position this clause imposes nothing on has nothing to be opened");
        assertThrows(IllegalArgumentException.class,
                () -> new Adoption<String, ReadingLanguage.Values>(Set.of(), Set.of(), Set.of(),
                        false, Set.of(CONSTRAINED)),
                "and one cannot be made holding an opening of a constraint it does not have");
    }

    /**
     * What the choice reached is what its branches reached, opened or not.
     *
     * <p>An author is sent to a choice wherever the branch beside the unread one reached a
     * position, which is read off {@code read} and {@code missed} together. So an opening moves a
     * position from one answer to another and never out of both, and how much of a clause this
     * compiler could work out cannot change which choices an author is told to look at.
     */
    @Test
    void whatTheChoiceReachedIsNotMovedByWhatItOpened() {
        Adoption<String, ReadingLanguage.Values> shut =
                branch(true).either(Opening.nothing(), branch(true));
        Adoption<String, ReadingLanguage.Values> open =
                branch(true).either(opening(CONSTRAINED), branch(true));

        assertEquals(reached(shut), reached(open));
        assertTrue(reached(open).contains(CONSTRAINED),
                "the position is still one the branches reached");
    }

    /**
     * A conjunction beside a choice does not put back what the choice left open.
     *
     * <p>{@code (x == 7 || f(y)) && z > 1} says nothing about {@code x}: everything holds, so what
     * the choice left open is left open under the conjunction too. Dropped there, an opening would
     * survive only as long as no author wrote another clause beside it.
     */
    @Test
    void aConjunctionBesideAChoiceKeepsWhatItLeftOpen() {
        Adoption<String, ReadingLanguage.Values> choice =
                branch(true).either(opening(CONSTRAINED), branch(true));

        assertEquals(Set.of(CONSTRAINED), choice.both(branch(false)).opened());
        assertEquals(Set.of(CONSTRAINED), branch(false).both(choice).opened());
    }

    /**
     * A position given up on takes its opening with it.
     *
     * <p>What is held is an opening applied to a constraint that still stands. Once the constraint
     * is gone — the position is one whose answer nothing built — there is nothing left for the
     * opening to be about, and a set that kept it would name a position this account no longer
     * reads.
     */
    @Test
    void aPositionGivenUpOnTakesItsOpeningWithIt() {
        Adoption<String, ReadingLanguage.Values> choice =
                branch(true).either(opening(CONSTRAINED), branch(true));

        assertEquals(Set.of(), choice.unbuiltAt(Set.of(CONSTRAINED)).opened());
        assertEquals(Set.of(), choice.unbuiltAt(Set.of(CONSTRAINED)).read());
    }

    /**
     * A branch standing beside a dead one keeps what a choice inside it left open.
     *
     * <p>The dead branch is not what opened it and does not take it back: what a choice below this
     * one was settled to have opened is still open, and the branch that stands is the one whose
     * account outranks. Measured over the models this repository holds, nothing reaches this — so
     * what says it is this and there is nothing else to notice if it stops.
     */
    @Test
    void aBranchBesideADeadOneKeepsWhatAChoiceInsideItLeftOpen() {
        Adoption<String, ReadingLanguage.Values> choice =
                branch(true).either(opening(CONSTRAINED), branch(true));

        assertEquals(Set.of(CONSTRAINED), choice.beside(branch(false)).opened());
    }

    /**
     * And a branch nobody can be in has nothing left open, however much stood open in it.
     *
     * <p>Nothing satisfies it, so what it said narrows no value and there is no constraint left for
     * an alternative to have widened. The positions it named are settled, which is an answer and
     * not a gap — and an opening kept beside that answer would say a constraint stood there.
     */
    @Test
    void aBranchNobodyCanBeInHasNothingLeftOpen() {
        Adoption<String, ReadingLanguage.Values> choice =
                branch(true).either(opening(CONSTRAINED), branch(true));

        assertEquals(Set.of(), choice.inADeadBranch().opened());
        assertEquals(Set.of(), choice.bothDead(branch(false)).opened());
    }

    /** The positions the account reached: what it constrained, and what it could not manage. */
    private static Set<String> reached(Adoption<String, ReadingLanguage.Values> said) {
        Set<String> out = new java.util.LinkedHashSet<>(said.read());
        out.addAll(said.missed());
        return out;
    }
}
