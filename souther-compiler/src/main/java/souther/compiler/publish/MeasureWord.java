package souther.compiler.publish;

/**
 * What a document calls one of the measurements a behavior has exactly one of.
 *
 * <p>Which measure, and never why it has no number. A measure nobody made says what it was waiting
 * for in {@link NotMeasuredWord}, and the two are asked of different things: a behavior no row
 * names has every one of these waiting on the same thing, so a reader given only the reason is told
 * one sentence as many times as the behavior has measures and cannot tell which of them it is
 * about.
 *
 * <p><b>One per measurement a behavior has one of, and no word for the ones it has many of.</b>
 * What the rows reach of a position is measured once per position, so which of those a fact is
 * about is the position's identity and not a word — a word for it would put every position's
 * measure of one behavior under one name, which is the collapse this vocabulary exists to have
 * avoided. Those are named by the axis instead, and the sum that names subjects says so by having
 * an arm of its own for them.
 *
 * <p>An enum for the reason {@link WeakeningWord} is one: the shipped schema names these words in
 * its own file and is held against this, so a word added here has to be taught to the schema before
 * it can be written.
 */
public enum MeasureWord {

    /** How far the rows of the behavior were read, which is what every measure over them is worth. */
    READING,

    /** The cases of the behavior's declared inputs and output. */
    SIGNATURE,

    /** The arms of the behavior's body, and which of them a row runs through. */
    BRANCH,

    /** The lines the behavior's rules draw, and the points either side of them. */
    BOUNDARY,

    /** Which positions the model divides the behavior's input into. Not what the rows reach of any
     *  one of them, which is measured once per position and named by that position. */
    PARTITION
}
