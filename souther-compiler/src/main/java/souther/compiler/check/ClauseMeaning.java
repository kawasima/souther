package souther.compiler.check;

import java.util.Objects;
import java.util.Set;

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

    /**
     * It has a form, this is what it states, and these are the fields of the declaration it reads.
     *
     * <p><b>Which fields, said here rather than worked out from the form.</b> What a construction
     * has to have filled for the clause to be read at all is a fact about the declaration, and a
     * reader that works it out walks a tree of its own to answer a question the declaring module
     * had already answered — against whichever bindings its own reading made.
     *
     * <p><b>Every field the declaration's value has, and not only the ones it writes.</b> A clause
     * may name a field a spread brought in, and a construction of the declaration fills that field
     * like any other; the question this answers is which of them have to be filled, so where a
     * field was written is not part of it. That is a different question, and the fields a
     * declaration writes are held apart from the ones it reaches for the sake of it
     * ({@link DeclarationMeaning.Product#fields}).
     *
     * <p>Named as the field is reached through the declaration, which is why this can be published.
     * A binding is one reading's way of reaching a field, so two readings of one declaration reach
     * the same field through two of them and a set of bindings would mean something only to the
     * reading that built it. The names are the same names in every reading there will ever be.
     *
     * @param ref which clause of which declaration this is
     * @param states what its form says
     * @param fieldsRead the fields of the declaration's value that its form reads, including the
     *     ones reached through what the declaration spreads
     */
    record Stated(Clause.Ref ref, TermMeaning states, Set<String> fieldsRead)
            implements ClauseMeaning {

        public Stated {
            Objects.requireNonNull(ref, "a clause that states something is some clause");
            Objects.requireNonNull(states, "a clause that has a form states what the form says");
            fieldsRead = Set.copyOf(fieldsRead);
        }
    }

    /** The reading has no form for it. */
    record Stopped(Clause.Ref ref) implements ClauseMeaning {

        public Stopped {
            Objects.requireNonNull(ref, "a clause with no form is still some clause");
        }
    }
}
