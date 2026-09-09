package souther.compiler.check;

import souther.compiler.core.Core;

import java.util.Set;

/**
 * What one leaf states, and on which of a value's numbers.
 *
 * <p>What a leaf states is one answer, and it is the same answer wherever the leaf is written. The
 * reading of ends composes the connectives an author wrote and has no arithmetic for the sides of a
 * comparison: it can say it did not work a line out and cannot say whether there was one to work
 * out. So a rule holding every row there is, one restricting which values may stand somewhere, and
 * one bounding a number an operation answers all read alike there — as rules whose end nobody
 * worked out — and under a choice that is what they were published as.
 *
 * <p>They are told apart here, by the reading that has the arithmetic, and told apart the way the
 * attribution of an end to a conjunct already tells them apart: by the numbers the canonical form
 * of the comparison is over. Read off which side happens to be spelled as a name, a rule whose
 * coordinate is written inside an expression — {@code String.length(s) * 2 >= 4} — is a rule about
 * nothing, and under a choice that comes back as a model that draws no line.
 *
 * <p><b>What a leaf states, and never what became of it.</b> Whether a range was read for the line
 * is the reading that holds that order's answer, and this hands its own out so that the two can be
 * put together where both are in hand ({@link #waitingOnAReader}). Decided here, a leaf would be
 * classified by what some reader managed with it, which is the confusion the whole type is against.
 */
interface StatedLines {

    /** What {@code leaf} states, read as written where {@code positive} and denied where it is
     *  not. */
    Statement of(Core leaf, boolean positive, Denotations at);

    /**
     * The positions of {@code named} whose end nothing here worked out.
     *
     * <p>Empty where the leaf states no line, and where the line it states runs between several of
     * the value's numbers and falls at none of them. All of {@code named} where it states one on a
     * number this reading cannot name, since which of the positions it is about is what reading
     * further would say.
     *
     * <p>And empty where the line is on a number an operation answers: such a line leaves the
     * position's own order exactly where it was, and whether it was placed is filed under the
     * number by the reading that holds it ({@link BoundaryReading}).
     */
    Set<FactSubject> waitingOnAReader(Statement stated, Set<FactSubject> named);

    /** Which of a value's numbers a leaf says the values stop on. */
    sealed interface Statement {

        /** None of them: a rule holding of every row, one saying which values may stand somewhere
         *  without ordering them, a denial of one value, a shape that is no comparison. */
        record NoLine() implements Statement {}

        /** The value standing at a position, named so that a caller can say which one. */
        record OnWhatStandsAtAPosition(NumberAt<RuleKey> number) implements Statement {

            public OnWhatStandsAtAPosition {
                if (number == null) {
                    throw new IllegalArgumentException("this one is about some position's value");
                }
            }
        }

        /** A number an operation answers of what stands at a position. */
        record OnADerivedNumber(DerivedNumber number) implements Statement {

            public OnADerivedNumber {
                if (number == null) {
                    throw new IllegalArgumentException("this one is about some derived number");
                }
            }
        }

        /** Several of them, held to each other: the line runs between them and falls at none. */
        record Between() implements Statement {}

        /** One this reading cannot name — an absolute value, a difference. */
        record OnANumberNotNamed() implements Statement {}
    }
}
