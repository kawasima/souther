package souther.compiler.check;

import souther.compiler.core.Core;

import java.util.Set;

/**
 * Which positions a leaf states a line about, on the value standing at the position itself.
 *
 * <p>What a leaf states is one answer, and it is the same answer wherever the leaf is written. The
 * reading of ends composes the connectives an author wrote and has no arithmetic for the sides of a
 * comparison: it can say it did not work a line out and cannot say whether there was one to work
 * out. So a rule holding every row there is, one restricting which values may stand somewhere, and
 * one bounding a number an operation answers all read alike there — as rules whose end nobody
 * worked out — and under a choice that is what they were published as.
 *
 * <p>The three of them are told apart here, by the reading that has the arithmetic. What comes back
 * is the positions whose own order the leaf says stops somewhere; the reading of ends is then asked
 * which of those it managed, which is its own answer and stays its own.
 *
 * <p><b>The position's own value and never a number taken of it.</b> A line on what an operation
 * answers — how long the string at a position is — is a line on another order, and where the values
 * at the position stop is untouched by it. Answered with the position, a bound on a length came out
 * as an end of the string order that nothing worked out, and a choice between two such bounds as a
 * border this compiler could not measure.
 */
interface StatedLines {

    /**
     * The positions of {@code named} whose own order {@code leaf} says the values stop on.
     *
     * <p>Empty where the leaf states no line at all: a rule that holds of every row, one that says
     * which values may stand somewhere without ordering them, a denial of one value. All of
     * {@code named} where it states one on a number this reading cannot name — an absolute value, a
     * difference — since which of the positions it is about is then what reading further would say.
     *
     * @param leaf     the clause of no connective, as the author wrote it
     * @param positive whether it stands as written or under a denial. A denial of a rule stating one
     *                 end states the other, and a denial of one ruling a value out states that value
     * @param named    the positions the leaf writes about, which is the clause's own answer
     */
    Set<FactSubject> ownValuesALineIsStatedOn(Core leaf, boolean positive, Denotations at,
                                              Set<FactSubject> named);
}
