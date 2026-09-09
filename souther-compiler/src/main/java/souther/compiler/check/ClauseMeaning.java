package souther.compiler.check;

import java.util.Objects;

/**
 * One clause of a declaration, as what it states rather than as where it is written.
 *
 * <p>What it states is a {@link TermMeaning}, which is the reading a term has when where it stands
 * is not among the questions it answers. Where the clause is written is
 * {@link ClauseLocations}'s, asked of the declaration by the reader that puts a caret under it.
 *
 * <p><b>Two arms, and they are the two {@link TypedClause} has.</b> A clause the discharge reader
 * has no form for is not a clause that states nothing: from "this reading has no form for it"
 * follows that its run-time check is the whole of its enforcement, and from "it states nothing"
 * follows something about the author's model that is not true. Written as one arm and an empty
 * reading, the second would be published wherever the first happened.
 *
 * <p>What a stopped clause carries is which clause it was, and nothing else. What the reading met is
 * about the run and not about the model, and two runs over one unedited source that met two limits
 * would answer with two values that never compare equal — which is what an answer a store keeps may
 * not be. {@link TypedClause.Stopped} is written the same way and for the same reason.
 */
public sealed interface ClauseMeaning permits ClauseMeaning.Stated, ClauseMeaning.Stopped {

    /** Which clause of which declaration this is, and what a sentence calls it. */
    Clause.Ref ref();

    /** It has a form, and this is what it states. */
    record Stated(Clause.Ref ref, TermMeaning states) implements ClauseMeaning {

        public Stated {
            Objects.requireNonNull(ref, "a clause that states something is some clause");
            Objects.requireNonNull(states, "a clause that has a form states what the form says");
        }
    }

    /** The reading has no form for it. */
    record Stopped(Clause.Ref ref) implements ClauseMeaning {

        public Stopped {
            Objects.requireNonNull(ref, "a clause with no form is still some clause");
        }
    }
}
