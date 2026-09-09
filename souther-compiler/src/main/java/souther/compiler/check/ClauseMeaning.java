package souther.compiler.check;

import java.util.ArrayList;
import java.util.List;
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
     * @param parts the rules its author wrote it as, and how they are joined
     */
    record Stated(Clause.Ref ref, TermMeaning states, Set<String> fieldsRead, Parts parts)
            implements ClauseMeaning {

        public Stated {
            Objects.requireNonNull(ref, "a clause that states something is some clause");
            Objects.requireNonNull(states, "a clause that has a form states what the form says");
            Objects.requireNonNull(parts, "a clause is written as some rules");
            fieldsRead = Set.copyOf(fieldsRead);
        }
    }

    /**
     * The rules an author wrote a clause as, and how they are joined.
     *
     * <p>Which parts a clause has is settled where the clause is split ({@link ClauseHelpers}) and
     * is published because every reading of the clause needs it: a reader that recovered the parts
     * from a tree it typed would be a second answer to how many there are, and two answers to that
     * is what numbering a clause in two walks came to.
     *
     * <p>The shape and the ordinals, and nothing an author wrote. What the split also holds is the
     * node each part was written as ({@link AuthoredShape}), which the expansion needs and which no
     * reader across a boundary may have — a node says where it stands, so a declaration moved down
     * its file would publish parts that had changed.
     */
    sealed interface Parts permits Parts.Both, Parts.One {

        /** Two rules, written as one clause. */
        record Both(Parts left, Parts right) implements Parts {

            public Both {
                Objects.requireNonNull(left, "a clause of two rules has a left");
                Objects.requireNonNull(right, "a clause of two rules has a right");
            }
        }

        /**
         * One rule, written as itself, under the name the split that found it issued.
         *
         * <p>The name and not the number it holds. A part is named where the clause was split
         * ({@code ClauseHelpers.AuthoredPart#idFor}), which is the one place that may put a number
         * beside a rule; carried as a number here, every reader that wanted the name would be
         * putting one beside whichever rule it happened to be holding.
         */
        record One(PartId<RuleRef.Invariant> id) implements Parts {

            public One {
                Objects.requireNonNull(id, "a rule an author wrote is some part of a clause");
            }
        }

        /**
         * Each part with the subtree of {@code read} it was read into.
         *
         * <p>{@code read} is what the whole clause came to, and every part of it is a subtree of
         * that one reading rather than a tree read alongside it. It says where to go and never
         * asks: where the author wrote two rules the reading has two sides, and a reading that has
         * one there is this compiler disagreeing with itself.
         */
        default List<Clauses.StatedPart> onto(ClauseExpr read) {
            List<Clauses.StatedPart> out = new ArrayList<>();
            found(read, out);
            return List.copyOf(out);
        }

        private void found(ClauseExpr read, List<Clauses.StatedPart> out) {
            switch (this) {
                case One it -> out.add(new Clauses.StatedPart(it.id(), read));
                case Both it -> {
                    if (!(read instanceof ClauseExpr.Joined joined)) {
                        throw new IllegalStateException("an author wrote two rules where this"
                                + " reading has one " + read.getClass().getSimpleName() + ", so the"
                                + " clause was read into a shape it was not written in");
                    }
                    it.left().found(joined.left(), out);
                    it.right().found(joined.right(), out);
                }
            }
        }
    }

    /** The reading has no form for it. */
    record Stopped(Clause.Ref ref) implements ClauseMeaning {

        public Stopped {
            Objects.requireNonNull(ref, "a clause with no form is still some clause");
        }
    }
}
