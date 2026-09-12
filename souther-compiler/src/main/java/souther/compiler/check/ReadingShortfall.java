package souther.compiler.check;

import souther.compiler.inputs.RuleSite;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.values.UnreadReason;

/**
 * The same thing a rule is answerable for, while the reading that decided it is still running.
 *
 * <p>Told apart by where in its own tree it was met, which is what a reading has and what nothing
 * outside it may be keyed by ({@link ClauseOccurrence}). A clause is read once per place the walk
 * opens a value at, and the trees those readings walk are the ones each substitution built — so two
 * readings of one written part are two of these, and become one {@link RuleShortfall} where the
 * reading is filed.
 *
 * <p>The part and not the node the decision was about. A choice is written inside a part, and what
 * an author is answerable for is that part: {@code at} is the enclosing part for a choice as it is
 * for a leaf, so the crossing out of the reading is one lookup for both and a reader downstream is
 * never holding a coordinate that names no part.
 *
 * @param at        the part of this reading's tree the decision was made under
 * @param writtenAs what the author wrote there, where they wrote anything: the construct the
 *                  decision is about, which a pass that copied it carried with it and which is
 *                  {@link ConstructOccurrence#unwritten()} for a shape no author wrote. Held
 *                  whole so that the copy is dropped where this crosses and never before — two
 *                  readings of one expansion are two of these and one fact
 * @param kind      what kind of decision it was
 * @param why       what the reading was short of
 * @param position  what it was left unable to say the values of
 */
record ReadingShortfall(ClauseOccurrence at, ConstructOccurrence writtenAs,
                        RuleShortfall.Kind kind, UnreadReason why, FactSubject position) {

    ReadingShortfall {
        if (at == null || writtenAs == null || kind == null || why == null || position == null) {
            throw new IllegalArgumentException(
                    "a shortfall met in a reading says where in it, what kind, what, and about"
                            + " which position");
        }
        if (why.about() != UnreadReason.About.A_RULE) {
            throw new IllegalArgumentException(
                    "a reason about " + why.about() + " names no rule to be about: " + why);
        }
    }

    /**
     * The same, as what crosses out of the reading: about the construct its author wrote, or about
     * {@code standingIn} where they wrote none.
     *
     * <p>Where this reading's coordinate is dropped and what the author wrote is kept whole. Two
     * readings of one copy of one construct become one of these; two copies of it stay two, which
     * is how many things a reader is owed.
     */
    RuleShortfall of(PartId<RuleRef.Invariant> standingIn) {
        return new RuleShortfall(writtenAs, RuleSite.at(writtenAs.origin(), standingIn),
                kind, why, position);
    }
}
