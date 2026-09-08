package souther.compiler.check;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What one choice left open, as one reading measures it.
 *
 * <p>A choice offering an alternative nothing could read leaves the branch beside it holding
 * nothing down at some of the positions it spoke of: a value satisfying the unread branch owes the
 * read one nothing. Which positions those are is a question about what the two branches leave, and
 * it is settled where the branches are ({@link Settlement.WidthDependency}). This is that answer on
 * its way to whoever applies it.
 *
 * <p><b>Two things are owed about it and this holds both.</b> A position has to be told it may be
 * wider than the rules leave it, and an author has to be sent to the choice they wrote. The two are
 * not the same set — a position hears about it only where nothing showed the choice leaves it what
 * it would without the unread alternative, and an author is sent wherever the alternative beside
 * the unread one <em>reached</em> a position, whether or not the answer there turned on it. Both
 * are read off this reading's account of this reading's clause, and holding them apart from one
 * another but together under one {@code L} is what keeps a half of one from being answered with a
 * half of the other's.
 *
 * <p><b>What a member of {@link #positions} says, which is the weaker of the two things it could.</b>
 * A position is there where this reading could not establish that the alternatives leave it what
 * the choice leaves it — not where it established that they do not. So the semantic opening is
 * contained in it and is not it, and the cost of the difference is a reading declining to speak for
 * a position it could have, never an answer handed out as exact when it is not.
 *
 * <p>Stated at the weaker end on purpose. A reading whose descriptions are canonical can answer the
 * question exactly, and one whose descriptions are written more ways than they are meant can only
 * answer it one way round; both belong in the same type, and a contract pitched at whichever
 * reading is sharpest today would have to move the day a third one arrives.
 *
 * @param <A>       what a position is called
 * @param <L>       which reading measured this. A position one reading says a choice left open is
 *                  not a position the other says anything about, and the two answers are the same
 *                  Java type once the tag is dropped ({@link ReadingLanguage})
 * @param positions the positions this reading could not show the alternatives preserve
 * @param byTheLeftGoingUnread  the positions this reading reached in the right alternative, where
 *                              the left is one it had no word for. Empty where it read the left
 * @param byTheRightGoingUnread the same the other way round
 */
// L unused in what this holds, which is the whole of what it is for: the tag is here so that the
// answer cannot be handed to a reading that did not work it out, and a parameter this record read
// would be one it could answer from.
@SuppressWarnings("UnusedTypeParameter")
record Opening<A, L extends ReadingLanguage>(Set<A> positions, Set<A> byTheLeftGoingUnread,
                                             Set<A> byTheRightGoingUnread) {

    // Copied on the way in, as everything a reading publishes is: what is here is handed to the
    // positions and kept in what they came to, so a maker that went on writing to the set it built
    // one from would be changing what an answer already given says.
    Opening {
        positions = held(positions);
        byTheLeftGoingUnread = held(byTheLeftGoingUnread);
        byTheRightGoingUnread = held(byTheRightGoingUnread);
    }

    /**
     * A choice this reading has nothing to say about.
     *
     * <p>Every position where the choice leaves what it leaves without either alternative, and
     * nobody to send an author to — which is what a choice both of whose alternatives this reading
     * read comes to, and the only state in which all three are empty together.
     */
    static <A, L extends ReadingLanguage> Opening<A, L> nothing() {
        return new Opening<>(Set.of(), Set.of(), Set.of());
    }

    private static <A> Set<A> held(Set<A> these) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(these));
    }
}
