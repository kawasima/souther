package souther.compiler.check;

/**
 * Which of the two readings a fact about a clause is a fact of.
 *
 * <p>A name and nothing else. Nothing of this type is ever made — what it is for is standing in the
 * type of a value so that a fact one reading worked out cannot be handed to the other. The two are
 * short of different things ({@link Confinement}): which values may stand at a position is a set
 * and where its order stops is a range, and neither has a word for what the other holds. So a
 * position one of them says a choice left open is not a position the other says anything about, and
 * a value carrying such an answer is only meaningful beside the reading that gave it.
 *
 * <p><b>Which the type says rather than a comment.</b> Both answers are sets of positions, so the
 * two are the same Java type once the tag is dropped and either can be passed where the other
 * belongs. What that costs is not an exception: the reading that receives the wrong answer composes
 * it perfectly happily and reports a clause it read as one it did not.
 *
 * <p>The tag is on the answer and on what consumes it, so an accidental crossing does not compile.
 * It does not make the underlying set of positions a different type: written out, the positions of
 * one language's answer can still be put in a value tagged as the other's. What is closed is
 * handing one to the other, which is the way the two meet in an expression.
 */
sealed interface ReadingLanguage {

    /** The reading that says which values may stand at a position. */
    final class Values implements ReadingLanguage {

        private Values() {}
    }

    /** The reading that says where a position's order stops. */
    final class Order implements ReadingLanguage {

        private Order() {}
    }
}
