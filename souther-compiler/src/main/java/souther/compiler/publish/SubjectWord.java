package souther.compiler.publish;

/**
 * What a document calls the kind of thing an entry is about.
 *
 * <p>One word per kind of place a reader can be sent, and not one per kind of news. What happened
 * is said by the entry's own word and by the reason beside it; this says what it happened to, and
 * the two are separate because one place can be met in more than one way — a source read twice for
 * two reasons is one place and two entries.
 *
 * <p><b>Three of these name a position and none of them can be folded into the others.</b> A path
 * as a reason spells it, a position as the reading of the model addresses it, and one of a
 * behavior's declared inputs are three address spaces that describe the same kind of thing. Read as
 * one word, an entry would send a reader to a place spelled in a vocabulary they were not handed.
 *
 * <p>An enum for the reason {@link WeakeningWord} is one: the shipped schema names these words in
 * its own file and is held against this.
 */
public enum SubjectWord {

    /** One module, and so everything in it. */
    MODULE,

    /** One behavior. */
    BEHAVIOR,

    /** One source, as the compilation that was handed it identifies it. */
    SOURCE,

    /** One row of one behavior. */
    ROW,

    /** A position inside a behavior's input, as a reason spells it for a person. */
    POSITION,

    /** The same place as the reading of the model addresses it, which is a different vocabulary. */
    READ_POSITION,

    /** One of a behavior's declared inputs, counted from zero. */
    INPUT,

    /** One rule of the model, at the position it was read at. */
    RULE,

    /** One line the rules drew. */
    BORDER,

    /** One thing a line asks a row at, which is not the line. */
    POINT,

    /** One decision of a body. */
    FORK,

    /** One arm of a body, as the source wrote it. */
    ARM,

    /** One of the measurements a behavior has exactly one of. */
    MEASURE,

    /** What the rows reach of one position, which a behavior has one of per position. */
    AXIS_MEASURE
}
