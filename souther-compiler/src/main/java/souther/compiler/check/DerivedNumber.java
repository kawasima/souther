package souther.compiler.check;

import souther.compiler.types.ValueName;

/**
 * A number an operation answers of what stands at a place, and never what stands there.
 *
 * <p>{@link NumberAt} says which of the numbers at a place a subject is, and both kinds are one type
 * there because a reader that has to say what two readings of one name came to needs them under one
 * subject. This is the half an operation answers, and it is its own type because one order has one
 * owner: where the values at a position stop is the reading of ends' ({@link OrderedReading}), and
 * how long the string standing there is is another order this reading holds.
 *
 * <p><b>The rule is fixed by what can be built and not by a convention.</b> There is no way to make
 * one of these out of a place's own value — {@link #of} answers with nothing for one — so a reading
 * keyed by these cannot come to hold a range the ends already own. Left as a {@code NumberAt} under
 * a comment, a position's own value put in by mistake would join and meet perfectly happily, and
 * what came out is two mechanisms settling one order and a report answering from whichever ran last.
 *
 * @param position where the number is read from
 * @param operation the operation that answers it, as it resolved. Two spellings reaching one
 *                  operation are one number, and two operations over one place are two
 */
record DerivedNumber(RuleKey position, ValueName operation) {

    DerivedNumber {
        if (position == null || operation == null) {
            throw new IllegalArgumentException("a derived number is what some operation answers"
                    + " of somewhere");
        }
    }

    /** The number {@code at} is, or null where it is what stands at the place rather than
     *  something answered of it. */
    static DerivedNumber of(NumberAt<RuleKey> at) {
        return at.of() instanceof NumberAt.OfWhatNumber.OfWhatAnOperationAnswers taken
                ? new DerivedNumber(at.position(), taken.operation()) : null;
    }

    /** The same number as the subject every other reader knows it by. */
    NumberAt<RuleKey> asNumber() {
        return NumberAt.takenOf(position, operation);
    }

    @Override
    public String toString() {
        return operation + "(" + position + ")";
    }
}
