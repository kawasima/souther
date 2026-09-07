package souther.compiler.semantics;

/**
 * Which side of what an operation answers a statement is about.
 *
 * <p>Two, because two are what a reader asks for. A fork tests whether something holds, and a rule
 * inside what it tests reaches it only where that rule decides the answer's truth; a truth about a
 * container is often a question about how many it holds, which is where the second comes in.
 *
 * <p><b>Not every side an answer has.</b> What the values in a container are is a third, and it is
 * deliberately absent: nothing asks it yet, and a word here that nothing reads would be one a later
 * reader takes for an answer somebody worked out. An aspect is added when a question needs it.
 */
public enum AnswerAspect {

    /** Whether what the operation answers holds. */
    TRUTH,

    /** How many values what the operation answers holds. */
    CARDINALITY
}
