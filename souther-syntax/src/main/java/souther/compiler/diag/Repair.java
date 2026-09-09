package souther.compiler.diag;

import java.util.Objects;

/**
 * What would answer a finding: the text to write, and — where the finding knows it — the characters
 * to write it over.
 *
 * <p>Two shapes because a check can know the word without there being anywhere here to put it. A
 * name inside a body an expansion copied in sits at a line of the author's file and says nothing
 * about what is written there, and a name nobody wrote has no place at all; the reader is still
 * better told what was probably meant. So the word travels either way and the edit does not, and a
 * caller that needs to write something has to say which of the two it is looking at.
 *
 * <p>{@link AnEdit#target} is the answer to a different question from a diagnostic's
 * {@link Primary}. What a finding is said about and what makes it go away are the same stretch often
 * enough to be mistaken for one rule, and they are not: a qualified name nothing denotes is reported
 * over the whole name and repaired by rewriting the one part that is wrong, and a report moved to
 * where a reader can reach it ({@link Diagnostic#reachedFrom}) is said at an import while the text
 * to change stays where it was written.
 *
 * <p>And both are a third question again from where an offer to apply it stands, which is where the
 * problem is marked. Nothing here answers that: a repair is what an author gets for saying yes, not
 * what puts the question in front of them.
 */
public sealed interface Repair {

    /** The text that answers the finding — what a reader is told either way. */
    String with();

    /** The word alone, there being nowhere in a file anyone holds to write it over. */
    record AWord(String with) implements Repair {

        public AWord {
            Objects.requireNonNull(with, "a repair says what to write");
        }
    }

    /**
     * The word and the characters to write it over.
     *
     * <p>{@code target} is code somebody typed, because an edit rewrites characters. A position
     * standing in for code written elsewhere is a place to send a reader and not a place to write,
     * and is refused here rather than described — so a caller holding one of these is holding an
     * edit that can be applied, and nothing downstream asks again.
     */
    record AnEdit(Region target, String with) implements Repair {

        public AnEdit {
            Objects.requireNonNull(target, "an edit says where it applies");
            Objects.requireNonNull(with, "a repair says what to write");
            if (target.start().wasCopiedHere() || target.end().wasCopiedHere()) {
                throw new IllegalArgumentException(
                        "an edit rewrites code that was written where it points, and this was"
                                + " copied here: " + target);
            }
        }
    }
}
