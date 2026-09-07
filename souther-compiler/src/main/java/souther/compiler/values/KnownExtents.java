package souther.compiler.values;

/**
 * Where the strings of each set stop, as worked out anywhere in one revision.
 *
 * <p>Knowledge and not a cache. Where a set's strings stop is settled by the set: the walk that
 * answers it is handed nothing else, and what it may spend is an allowance of its own, minted for
 * that set and for no other question ({@link TextExtents}). So the answer is the same whoever
 * asked, and one worked out under one declaration is the answer under every other — which is what
 * this is for, since a rule many declarations reach is otherwise walked once for each of them.
 *
 * <p><b>Including the ones that were not built.</b> A set whose extent ran past what the walk may
 * spend comes back saying so, and that too is settled by the set, because the allowance it ran past
 * is the one this compiler mints for every set alike. Left out, the sets that cost the most would
 * be the ones walked again by every declaration that reaches them.
 *
 * <p>Which is what separates this from what a reading keeps. A reading's own answers are what its
 * declaration came to and are kept as the declaration's ({@link StringMachineAnswers}), where an
 * extent nobody could build is not something the declaration came to. Here it is knowledge like any
 * other: this revision has asked, and the asking is done.
 *
 * <p>For a revision and no longer. What the sets of a revision are is settled by what the sources
 * say, and a revision that has moved is a world whose sets may be other sets — so nothing here is
 * carried across one. Held by whoever knows which revision is current, and handed to every reading
 * made under it.
 */
public interface KnownExtents {

    /** Where {@code set}'s strings stop as this revision already worked it out, or null. */
    TextExtent of(ValueSet set);

    /** Says that {@code set}'s extent came to {@code extent}, for the rest of the revision. */
    void remember(ValueSet set, TextExtent extent);

    /**
     * Nothing known and nothing remembered, for a reading with no revision behind it.
     *
     * <p>A question asked outside a store has no revision to share with, and one in hand for the
     * length of a single reading would answer only that reading. Both work everything out, which is
     * what they did before there was anything to share.
     */
    KnownExtents NONE = new KnownExtents() {

        @Override
        public TextExtent of(ValueSet set) {
            return null;
        }

        @Override
        public void remember(ValueSet set, TextExtent extent) {
        }
    };
}
