package souther.compiler.check;

import souther.compiler.inputs.RuleSite;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.values.UnreadReason;

/**
 * One thing a rule of the model is answerable for, at one position, about one part its author
 * wrote.
 *
 * <p>Four and not three. What a reading was short of and where it left the position short are what
 * a place's own account holds; which written thing it is about is what an account of a rule needs
 * and is what a place cannot say — every rule reaching a position pays into its answer, so a place
 * has as many claimants as it has rules and names none of them. The fourth is what kind of decision
 * it was, which is what a reader is told rather than what tells two apart.
 *
 * <p><b>What an author wrote, and not the tree a reading was built over.</b> A clause is read once
 * for every place the walk opens a value at, over whatever tree the substitution built there, so one
 * written construct is met as many objects at as many coordinates ({@link ClauseOccurrence}). What
 * crosses out of a reading is what the author wrote and which copy of it this is
 * ({@link ConstructOccurrence}) — the same whichever reading met it, and holding no place.
 *
 * <p>And two constructs are two, however alike. Two choices written in one part agree about the
 * rule, the position and the reason, and what tells them apart is that their author wrote two of
 * them — which the construct says and the part around them cannot.
 *
 * <p><b>Two questions about one operator, and two answers.</b> A helper is copied into every call
 * that reaches it, and each copy leaves its own position open: lifting one leaves the other exactly
 * where it was, so they are two things a reader is owed. What they are owed is one place — the
 * operator its author wrote, which rewriting answers every copy. So the copy is what makes two of
 * these two ({@link #occurrence}) and the construct is what they are sent to ({@link #site}), and
 * neither is read for the other's question.
 *
 * <p><b>Made where the reading decided, and never read back out of a place.</b> A position's
 * standing is what it was left holding and is right to hold every reason there is; sifting it for
 * the ones a rule could be answerable for gives a list of reasons and no rule. So each of these is
 * made at the point the reading made the decision ({@link ReadingShortfall}) and named for its part
 * where the reading is filed, and what travels afterwards is this.
 *
 * <p><b>And no place.</b> Where the part is written follows from the declaration and from nothing
 * the reading did, so it is asked of the declaration by whoever is about to point somewhere
 * ({@link PartLocations}). Carried here, an edit that moves a declaration and changes nothing it
 * states would change every answer built out of this, in every module that imports it.
 *
 * <p>No order. Which of two of these an author wrote first is a fact about the model, and saying it
 * needs what the author wrote rather than what a walk met; nothing here is in an order anybody may
 * read.
 *
 * @param occurrence what the reading decided this about: the construct its author wrote and the
 *                   copy of it this is. Both, because two questions are asked of one operator and
 *                   they have two answers — a helper expanded at two calls leaves two things to
 *                   lift and one place to go
 * @param site       where a reader goes about it, which is what the author wrote and never which
 *                   copy of it a walk met
 * @param kind       what kind of decision it was, which is what a reader is sent by and not what
 *                   tells two of these apart
 * @param why        what it was short of, which a rule is answerable for
 *                   ({@link UnreadReason.About#A_RULE})
 * @param position   what the reading was left unable to say the values of
 */
record RuleShortfall(ConstructOccurrence occurrence, RuleSite site, RuleShortfall.Kind kind,
                     UnreadReason why, FactSubject position) {

    RuleShortfall {
        if (occurrence == null || site == null || kind == null || why == null
                || position == null) {
            throw new IllegalArgumentException(
                    "a shortfall about a rule says what it is about, what kind, what it was, and"
                            + " where");
        }
        if (site instanceof RuleSite.TheRuleItself) {
            throw new IllegalArgumentException(
                    "a reading decides about something somebody wrote, and the whole of a rule is"
                            + " where a reader is sent rather than what was decided");
        }
        if (why.about() != UnreadReason.About.A_RULE) {
            throw new IllegalArgumentException(
                    "a reason about " + why.about() + " names no rule to be about: " + why);
        }
    }

    /**
     * What kind of decision a reading made, which is what a reader is sent by.
     *
     * <p>Two, because two kinds of decision are made about two kinds of thing. A reading gives up
     * on a clause it has no word for, which is the part itself and is what an author rewrites. A
     * choice offers an alternative nothing could read, which is one fact about the choice however
     * many positions it reaches — and what an author acts on is the {@code ||} inside a part that
     * otherwise reads perfectly well.
     *
     * <p>Not what tells two of these apart on its own. Which part it is about is
     * {@link #site}, and two decisions of different kinds about one thing are two of these because
     * they are two things for a reader to do.
     */
    enum Kind {

        /** A part of the clause the reading had no word for. */
        LEAF,

        /** A choice inside the part, offering an alternative nothing could read. */
        CHOICE
    }
}
