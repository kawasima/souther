package souther.compiler.check;

import souther.compiler.inputs.RuleSite;
import souther.compiler.types.ConstructOccurrence;

/**
 * One choice a reading met: what its author wrote, and which part of this reading's tree it stands
 * in.
 *
 * <p>Both, because either alone answers half. What an author wrote is the {@code ||} itself, which
 * is what they go and rewrite and is the same construct at every call a helper was expanded at
 * ({@link ConstructOccurrence#origin}). Which part it stands in is what a reader is offered where
 * nobody wrote it — a substitution puts shapes in the tree its author did not write, and a choice
 * among those has no operator anybody can edit.
 *
 * <p>Reading-local, and only the first half crosses. The coordinate is of the tree this reading was
 * built over ({@link ClauseOccurrence}) and means nothing outside it; what is made where the
 * reading is filed is a {@link RuleSite}, which says what the author wrote and holds no coordinate
 * of anybody's walk.
 *
 * @param writtenIn the part of this reading's tree the choice stands in
 * @param writtenAs the construct its author wrote, or {@link ConstructOccurrence#unwritten()}
 */
record ChoiceMet(ClauseOccurrence writtenIn, ConstructOccurrence writtenAs) {

    ChoiceMet {
        if (writtenIn == null || writtenAs == null) {
            throw new IllegalArgumentException(
                    "a choice a reading met stands somewhere in it, and says what was written");
        }
    }
}
