package souther.compiler.query;

import souther.compiler.partition.Generator;
import souther.compiler.partition.StoodInAnswer;

import java.util.List;

/**
 * What a row takes to stand its target's dependencies in with, or why nothing does.
 *
 * <p>An answer and never an absence. A behavior that requires nothing is stood in by nothing and a
 * behavior whose stand-in nothing composed has rows that cannot go out, and the two read alike as
 * an empty list — after which a reader downstream decides for itself which it had, and the one that
 * decided wrongly offered a row nothing applies.
 *
 * <p>Carried from where a row is composed to where the block is written, so that what a block says
 * about work it did not offer is what the composition came to rather than something read off the
 * absence of a row.
 */
public sealed interface AnswersStoodIn {

    /** Every dependency answered, in the order they are required. */
    record Stood(List<StoodInAnswer> answers) implements AnswersStoodIn {

        public Stood {
            answers = List.copyOf(answers);
        }
    }

    /**
     * Nothing came of it, in the words a search comes back with.
     *
     * <p>Never a statement that no row exists: what a dependency answers may be a shape nothing
     * here writes a value of, and an author writes one by hand.
     */
    record NothingComposed(Generator.UnresolvedCombination.Reason why) implements AnswersStoodIn {

        public NothingComposed {
            if (why == null) {
                throw new IllegalArgumentException("a composition that came to nothing says what of");
            }
        }
    }
}
