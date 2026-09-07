package souther.compiler.semantics;

/**
 * Which side of what an operation answers a statement is about.
 *
 * <p>Two, because two are what a reader asks for. A fork tests whether something holds, and a rule
 * inside what it tests reaches it only where that rule decides the answer's truth; a truth about a
 * container is often the question of whether it holds anything, which is where the second comes in.
 *
 * <p><b>Whether it holds anything, and not how many it holds.</b> These are two questions and only
 * the first is asked: {@code List.distinctBy} answers fewer where its key sends two elements to one,
 * so what the key says changes the count — and it never changes whether the answer is empty, since
 * the first element of a list it walked is always kept. A statement about the count read as one
 * about emptiness would credit that key with deciding a fork on {@code List.isEmpty}, which it does
 * not.
 *
 * <p><b>Not every side an answer has.</b> How many it holds is a third, whether an optional is
 * present is a fourth, and both are deliberately absent: nothing asks them yet, and a word here that
 * nothing reads would be one a later reader takes for an answer somebody worked out. An aspect is
 * added when a question needs it.
 */
public enum AnswerAspect {

    /** Whether what the operation answers holds. */
    TRUTH,

    /** Whether what the operation answers holds anything at all. */
    EMPTINESS
}
