package souther.compiler.check;

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
 * <p><b>What a member says, which is the weaker of the two things it could.</b> A position is here
 * where this reading could not establish that the alternatives leave it what the choice leaves it —
 * not where it established that they do not. So the semantic opening is contained in this and is
 * not this, and the cost of the difference is a reading declining to speak for a position it could
 * have, never an answer handed out as exact when it is not.
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
 */
// Unused in what this holds, which is the whole of what it is for: the tag is here so that the
// answer cannot be handed to a reading that did not work it out, and a parameter this record read
// would be one it could answer from.
@SuppressWarnings("UnusedTypeParameter")
record Opening<A, L extends ReadingLanguage>(Set<A> positions) {

    Opening {
        positions = Set.copyOf(positions);
    }

    /** A choice shown to leave every position what it leaves without either alternative. */
    static <A, L extends ReadingLanguage> Opening<A, L> nothing() {
        return new Opening<>(Set.of());
    }
}
