package souther.compiler.observe;

/**
 * The one comparison, for the two faces that are bound to a reading of the declarations.
 *
 * <p>Whether an answer is what a text stated is the language's to settle, and settling it takes
 * what the declarations say stands inside a value. Whoever supplies that decides part of the
 * answer — a field declared a set is compared without its order, and one nothing declares is
 * compared in the order it stands — so where the reading comes from is what makes a verdict the
 * language's rather than the reader's.
 *
 * <p>Which is why this is not reachable from a statement. A comparison hanging off what a row
 * states is one a reader can make with declarations of its own, and two readings of one row would
 * answer differently about one answer. The faces that ask are bound where they are made: the
 * compile asks with the declarations it read the text against, and a checked program's row asks
 * with the ones that program publishes.
 */
public final class Comparisons {

    private Comparisons() {}

    /**
     * Whether {@code answered} keeps what {@code stated} states, read with {@code types} and at
     * {@code answers}.
     *
     * <p>For whoever holds a reading of the declarations to bind to. What is answered here is the
     * same for both faces, which is what makes a row mean one thing wherever it is read.
     */
    public static Verdict verdict(Expectation.Asserts stated, ObservedValue answered,
                                  ValueTypes types, Position answers) {
        if (stated == null || answered == null || types == null || answers == null) {
            throw new IllegalArgumentException("a comparison is of a statement against an answer,"
                    + " read with what the declarations say and where the answer stands");
        }
        return new ValueMatch(types).verdict(stated, answered, answers);
    }

    /**
     * Whether {@code left} and {@code right} are the same value, read with {@code types} and at
     * {@code position}.
     *
     * <p>The other question, and it is not the one above with a statement made up for one side. A
     * statement is what a text wrote and carries what it wrote it as; two values that were both
     * arrived at wrote nothing, and holding one of them up as what was expected would answer a
     * question about a text where there is no text — a value with parts, offered as a value with
     * none, is read as a value of no type at all and is the same as nothing.
     *
     * <p>Yes or no, and the same walk. What being the same value means — a set is its elements, a
     * decimal is the amount it stands for, a value is of its type first — is answered for both
     * questions in one place, so a fake's table picks the row an execution picks.
     *
     * <p>Of two values that are there, which is what makes yes or no the whole of the answer. An
     * observation that stopped, or that could not read what it met, is not a value: what stands
     * where it stopped may be anything, including the value it is being compared with. Answered
     * "no" for one, this would hand a caller the difference between two values where what it has is
     * the absence of one — and the caller would go on to whatever it does when two values differ.
     * That is what {@link #verdict} says as {@code UNREADABLE} rather than as a value that did not
     * hold, and it is refused here because a yes-or-no has nowhere to say it.
     *
     * @throws IllegalArgumentException if either side is not a value that is there in full
     */
    public static boolean same(ObservedValue left, ObservedValue right, ValueTypes types,
                               Position position) {
        if (left == null || right == null || types == null || position == null) {
            throw new IllegalArgumentException("two values are the same or not, read with what the"
                    + " declarations say and where they stand");
        }
        // Held to what admits a value that is there and not to what a snapshot keeps. Whether two
        // values are the same does not depend on how much of one may be carried somewhere, and a
        // large whole value refused here would put that question inside this one.
        refuseWhatIsNotThere(left, "the first");
        refuseWhatIsNotThere(right, "the second");
        return new ValueMatch(types).same(left, right, position);
    }

    /**
     * Which part of {@code answered} stands for which part of {@code stated}, read with
     * {@code types} and at {@code answers}.
     *
     * <p>For a report writing the two side by side. A map's entries are paired by key and a set's
     * elements without an order, and which pairing that comes to is a question about what being the
     * same value means — asked here, so that a report does not answer it from what the two are
     * written as. It says nothing about whether they are the same, which is {@link #verdict}'s
     * answer and is reported as one.
     *
     * <p>Asked when a report wants it. A verdict is asked of every row and nearly all of them hold,
     * so the correspondence under one is work almost nobody has a use for; this walks the two again
     * for the ones that did not, by the step {@link #verdict} finds its own pairings with.
     */
    public static Alignment alignment(Asserted stated, ObservedValue answered, ValueTypes types,
                                      Position answers) {
        if (stated == null || answered == null || types == null || answers == null) {
            throw new IllegalArgumentException("a correspondence is of a statement with an answer,"
                    + " read with what the declarations say and where the answer stands");
        }
        return new ValueMatch(types).align(stated, answered, answers);
    }

    private static void refuseWhatIsNotThere(ObservedValue value, String which) {
        Incompleteness.Code stopped = Limits.UNBOUNDED.stoppedBy(value);
        if (stopped != null) {
            throw new IllegalArgumentException(which + " of two values being compared is not a"
                    + " value: " + stopped);
        }
    }
}
