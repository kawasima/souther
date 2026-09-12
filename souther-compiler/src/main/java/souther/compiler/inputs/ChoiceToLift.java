package souther.compiler.inputs;

import souther.compiler.types.ConstructOccurrence;

/**
 * One choice a reader is owed something about: which one it is, and where they go about it.
 *
 * <p>Two questions about one operator, and they have two answers. A helper is copied into every
 * call that reaches it, and each copy leaves its own end open: lifting one leaves the other exactly
 * where it was, so a reader is owed as many things as there are copies. What they are owed is one
 * place — the operator its author wrote, which rewriting answers every copy of it.
 *
 * <p>So the copy is what makes two of these two, and what they are sent to is what the author
 * wrote. Held as the destination alone, two copies came back as one thing to lift and a reader was
 * told one end was open where two were; held as the copy alone, one operator came back as as many
 * places to go as this compiler happened to expand it into.
 *
 * <p>Neither half is a place. Which construct and which copy are counted over what the source
 * wrote, so an edit that moves a declaration and changes nothing it says changes neither.
 *
 * @param met    which choice, and which copy of it
 * @param sentTo where inside the rule a reader goes about it
 */
public record ChoiceToLift(ConstructOccurrence met, RuleSite sentTo) {

    public ChoiceToLift {
        if (met == null || sentTo == null) {
            throw new IllegalArgumentException(
                    "a choice a reader is owed something about is some choice, somewhere in its"
                            + " rule");
        }
    }
}
