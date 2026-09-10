package souther.compiler.check;

import souther.compiler.diag.SourcePos;
import souther.compiler.values.UnreadReason;

/**
 * One thing a rule of the model is answerable for, at one position, written at one place.
 *
 * <p>Three and not two. What a reading was short of and where it left the position short are what a
 * place's own account holds; which written thing it is about is what an account of a rule needs and
 * is what a place cannot say — every rule reaching a position pays into its answer, so a place has
 * as many claimants as it has rules and names none of them.
 *
 * <p><b>Made where the reading decided, and never read back out of a place.</b> A position's
 * standing is what it was left holding and is right to hold every reason there is; sifting it for
 * the ones a rule could be answerable for gives a list of reasons and no rule. So each of these is
 * made at the point the reading made the decision, where the written thing is still in hand, and
 * what travels afterwards is this.
 *
 * <p>Told apart by all three. The same reason at the same position, decided at two written places,
 * is two of these — an author has two things to look at, and a carrier that kept one would be
 * choosing between them by whichever was met first.
 *
 * <p>No order. Which of two of these an author wrote first is a fact about the model, and saying it
 * needs the source rather than the reading; nothing here is in an order anybody may read.
 *
 * @param position what the reading was left unable to say the values of
 * @param why what it was short of, which a rule is answerable for ({@link UnreadReason.About#A_RULE})
 * @param site the written place the reading decided it at
 */
record RuleShortfall(FactSubject position, UnreadReason why, RuleShortfall.Site site) {

    RuleShortfall {
        if (position == null || why == null || site == null) {
            throw new IllegalArgumentException(
                    "a shortfall about a rule says where, what it was, and where it was written");
        }
        if (why.about() != UnreadReason.About.A_RULE) {
            throw new IllegalArgumentException(
                    "a reason about " + why.about() + " names no rule to be about: " + why);
        }
    }

    /**
     * A written place a reading can be short of something at.
     *
     * <p>Two, because two kinds of decision are made about two kinds of thing. A reading gives up on
     * a clause it has no word for, which is one leaf somebody wrote. A choice offers an alternative
     * nothing could read, which is one fact about the choice however many positions it reaches and
     * however many leaves are under it — filed at a leaf, an author would be sent to the branch that
     * was read.
     *
     * <p>Identity is what tells two apart: two of these are the same written place or are not, and
     * that is settled by what each of them is rather than by where it is. Where it is, is what
     * {@link #writtenAt} answers — asked of every kind, so that whoever puts these in the order
     * somebody wrote them asks one question of all of them and a kind added later has to answer it.
     */
    sealed interface Site permits Site.AtALeaf, ChoiceSite {

        /** Where an author wrote it, which is what an order among them is taken over. */
        SourcePos writtenAt();

        /**
         * One clause the reading had no word for, as where in its clause the author wrote it.
         *
         * <p>The identity and the place beside each other, as a choice holds them
         * ({@link ChoiceSite}). Which part of the clause this is, is what
         * {@link ClauseOccurrence} says and a node cannot: a clause is read once for every
         * place the walk opens a value at, over whatever tree the substitution built there, so two
         * readings of one rule meet the same written part as two objects. Told apart by the tree
         * they landed in, one thing an author wrote came back as two things to look at.
         *
         * <p>Which rule's clause it is an occurrence of is the carrier's — what a rule is
         * answerable for is filed under that rule ({@link ReadingEvidence}) and never met with
         * another's — and this says the rest.
         *
         * <p>The place is the part with the denials above it taken off, which is one of the two
         * spellings the shape holds ({@link ClauseExpr#spelled}). Which of them an author is shown
         * — the comparison, or the {@code not} written over it — is a question about what a report
         * points at and is answered where the site is made, so it stays a choice among what the
         * clause was written as rather than becoming a fact read off whatever tree was in hand.
         *
         * @param at which part of the clause it is, in the clause's own numbering
         * @param writtenAt where that part stands, which is settled by {@code at} and carried
         *                  because the reader that puts these in the author's order has no clause
         *                  left to ask
         */
        record AtALeaf(ClauseOccurrence at, SourcePos writtenAt) implements Site {

            public AtALeaf {
                if (at == null || writtenAt == null) {
                    throw new IllegalArgumentException(
                            "a leaf is some part of a clause, written somewhere");
                }
            }

            /** This part of the clause and no other, whichever reading met it. */
            @Override
            public boolean equals(Object other) {
                return other instanceof AtALeaf it && at.equals(it.at);
            }

            @Override
            public int hashCode() {
                return at.hashCode();
            }
        }

    }
}
