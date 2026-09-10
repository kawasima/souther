package souther.compiler.check;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the guards settled, read back as the predicates a path holds.
 *
 * <p>The settlings are composed as they are made and read out where a reader asks, so what every
 * reader of a state sees is decided here and nowhere else. Two things follow from that and neither
 * is visible from a path that settled a handful of predicates: that a predicate settled again is
 * read once and in the place it was first settled, which is what makes an answer a function of which
 * predicates were settled rather than of how often or in what order a caller happened to settle
 * them; and that reading them back is not a limit on how many there may be.
 *
 * <p>The second is why the depth and the sharing are both here. A guard settles one predicate at a
 * time, so the composition a long path leaves is as deep as the path is long — read by calling down
 * it, a path settling enough predicates would be one the reader could not answer about at all. And
 * nothing is copied when two paths' predicates are said together, so what a caller says twice is
 * reached twice and not held twice — read by what it reaches, a caller doubling what it says doubles
 * what reading it costs. Neither failure is one any of these rules is about.
 */
class PredicatesAreReadOutOnceEachHoweverDeeplyTheyWereSettledTest {

    /** A path settling enough predicates for a reader that called down what they compose to not
     *  answer. */
    private static final int LONGER_THAN_A_STACK = 100_000;

    /**
     * Doublings enough that a reader paying per reach rather than per part takes far longer than
     * this test is given, and few enough that it still stops and says so.
     */
    private static final int MORE_REACHES_THAN_THERE_ARE_PARTS = 27;

    private static SettledPredicates<String> holding(String key) {
        return SettledPredicates.of(key, true);
    }

    private static SettledPredicates<String> failing(String key) {
        return SettledPredicates.of(key, false);
    }

    private static SettledPredicates<String> settled(List<SettledPredicates<String>> these) {
        SettledPredicates<String> out = SettledPredicates.none();
        for (SettledPredicates<String> each : these) {
            out = out.and(each);
        }
        return out;
    }

    /** A predicate settled again is the same settling, and it is read where it was first made. */
    @Test
    void aPredicateSettledAgainIsReadOnceAndWhereItWasFirstSettled() {
        assertEquals(List.of(holding("a"), failing("b"), holding("c")),
                settled(List.of(holding("a"), failing("b"), holding("a"), holding("c"),
                        failing("b"))).distinct());
    }

    /** The same predicate settled the other way round is another settling, and both are read. */
    @Test
    void aPredicateSettledBothWaysIsTwoSettlings() {
        assertEquals(List.of(holding("a"), failing("a")),
                settled(List.of(holding("a"), failing("a"))).distinct());
    }

    /**
     * Two paths' predicates said together are read as the first path's and then what the second
     * adds.
     *
     * <p>Which is what a caller holding the readings of two values at once is owed: what the two of
     * them settled does not depend on which of them the caller came by first having settled
     * something the other settled too.
     */
    @Test
    void twoPathsPredicatesAreReadAsTheFirstsAndThenWhatTheSecondAdds() {
        assertEquals(List.of(holding("a"), failing("b"), holding("c")),
                settled(List.of(holding("a"), failing("b")))
                        .and(settled(List.of(failing("b"), holding("c")))).distinct());
    }

    /** Nothing settled, which is what a path whose guards said nothing holds. */
    @Test
    void aPathThatSettledNothingReadsBackNothing() {
        assertEquals(List.of(), SettledPredicates.<String>none().distinct());
    }

    /**
     * A path longer than a reader could call down is read back whole.
     *
     * <p>The predicate settled last is reached, and so is the one settled first — which is under
     * everything else that was settled, and is the one a reader that stopped anywhere would not
     * have.
     */
    @Test
    void aPathLongerThanAStackIsReadBackWhole() {
        SettledPredicates<String> deep = holding("first");
        for (int settled = 0; settled < LONGER_THAN_A_STACK; settled++) {
            deep = deep.and(holding("first"));
        }

        assertEquals(List.of(holding("first"), failing("last")),
                deep.and(failing("last")).distinct());
    }

    /**
     * What was settled once and reached many times is read once.
     *
     * <p>Nothing is copied when two of these are said together, so two paths carrying on from one
     * hold the one they carried on from rather than a copy of it, and saying those two together
     * reaches it twice. Read by what it reaches, a caller doubling what it says would be read a
     * number of times that doubles with it — which is the cost the rest of this is about, arrived
     * at from the other end.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void whatWasSettledOnceIsReadOnceHoweverManyTimesItIsReached() {
        SettledPredicates<String> shared = holding("a");
        for (int doubled = 0; doubled < MORE_REACHES_THAN_THERE_ARE_PARTS; doubled++) {
            shared = shared.and(shared);
        }

        assertEquals(List.of(holding("a")), shared.distinct());
    }

    /** A path deeper than a reader could call down answers what it settled. */
    @Test
    void aStateDeeperThanAStackAnswersWhatItSettled() {
        PredicateFacts<String> facts = PredicateFacts.none();
        for (int settled = 0; settled < LONGER_THAN_A_STACK; settled++) {
            facts = facts.assume("p" + (settled % 3), true);
        }

        assertFalse(facts.isBottom());
        assertTrue(facts.entails("p0", true));
        assertTrue(facts.refutes("p1", false));
        assertFalse(facts.entails("p3", true));
    }

    /** One key settled both ways is a contradiction however far apart the two were settled. */
    @Test
    void aKeySettledBothWaysIsAContradictionHoweverFarApartTheyWereSettled() {
        PredicateFacts<String> facts = PredicateFacts.<String>none().assume("p", true);
        for (int settled = 0; settled < LONGER_THAN_A_STACK; settled++) {
            facts = facts.assume("q" + settled, true);
        }

        assertTrue(facts.assume("p", false).isBottom());
    }

    /** And when the two settlings are one reading's and another's. */
    @Test
    void aKeyTwoReadingsSettleTwoWaysIsAContradictionWhereTheyAreMet() {
        assertTrue(PredicateFacts.<String>none().assume("p", true)
                .meet(PredicateFacts.<String>none().assume("p", false))
                .isBottom());
    }

    /**
     * Guards that cannot all hold are still guards that cannot all hold in another vocabulary, and
     * no subject of theirs is handed to the naming.
     *
     * <p>A path nothing reaches says the same thing under any names. Handing its subjects over would
     * have a renaming refusing two of them over a disagreement on a path the program never takes.
     */
    @Test
    void aContradictionCrossesIntoAnotherVocabularyWithoutNamingItsSubjects() {
        AtomicInteger named = new AtomicInteger();
        PredicateFacts<String> nothing =
                PredicateFacts.<String>none().assume("p", true).assume("p", false);

        PredicateFacts<String> said = nothing.renamed(key -> {
            named.incrementAndGet();
            return "elsewhere." + key;
        });

        assertTrue(said.isBottom());
        assertEquals(0, named.get(), "a path nothing reaches has no subject to name");
    }

    /** And a path that is reached takes every subject it settled through the naming. */
    @Test
    void whatIsSettledCrossesIntoAnotherVocabularyEachWayRound() {
        PredicateFacts<String> said = PredicateFacts.<String>none()
                .assume("p", true).assume("q", false)
                .renamed(key -> "elsewhere." + key);

        assertFalse(said.isBottom());
        assertTrue(said.entails("elsewhere.p", true));
        assertTrue(said.refutes("elsewhere.q", true));
        assertFalse(said.entails("p", true), "the old names are not this vocabulary's");
    }
}
