package souther.compiler.types;

/**
 * Which copy of a body a construct stands in, said as the copies it was made by.
 *
 * <p>The second half of what tells one construct from another. {@link SourceConstructOrigin} says
 * which construct the source wrote and carries through every copy of it, so a helper spliced into
 * two calls holds one construct twice under one origin. What separates them is this: the calls the
 * splicing went through, as a chain of {@link Expansion}s.
 *
 * <p><b>A copy is the only thing that makes a step.</b> A pass that rewrites a construct in place
 * leaves this alone, and so does the choice of which representation a body is being built as. The
 * two representations of one body expand different sets of calls, so one has steps the other does
 * not; what they cannot do is disagree about a step they both took, because a step is a place the
 * source settles and that is settled before either tree exists.
 *
 * <p><b>No body is named here.</b> A construct never copied is {@link #ORIGINAL}, and which body
 * that is is what its origin's owner says; one that was copied is under a chain whose outermost step
 * is a site, and which body that site is written in is what the site's own owner says. A component
 * naming the body would be a second answer to a question already answered.
 *
 * <p>Nothing about how a copy was reached, either. Which pass performed the splice, how many
 * splices it had performed before, which of the trees it was building: a chain holding any of them
 * would move when the compiler is asked to do the same work in another order.
 */
public sealed interface ExpansionLineage {

    /** The construct as the source wrote it, in the body that wrote it. */
    ExpansionLineage ORIGINAL = new Original();

    /**
     * A construct no copy stands between: it is where it was written.
     *
     * <p>One value, because there is nothing to tell two of them apart. Which body it is in is the
     * construct's own origin's answer, and a component for it here would be that answer again.
     */
    record Original() implements ExpansionLineage {

        @Override
        public String toString() {
            return "as written";
        }
    }

    /**
     * A copy of {@code expanded}'s body, made at {@code at}, inside {@code within}.
     *
     * @param within   which copy the site itself stands in. One call written inside a helper that is
     *                 expanded twice is one call and two copies, and this is what tells them apart
     * @param expanded what the call reached, which is not read off the site: the same characters may
     *                 come to call something else, and that is another copy of another body there
     * @param at       where the copy was made
     */
    record Expansion(ExpansionLineage within, ValueName expanded, ExpansionSite at)
            implements ExpansionLineage {

        public Expansion {
            if (within == null || expanded == null || at == null) {
                throw new IllegalArgumentException(
                        "a copy is of something, at some site, inside something: " + expanded
                                + " at " + at + " in " + within);
            }
        }

        /** This copy said without what it stands in. */
        public Step step() {
            return new Step(expanded, at);
        }

        @Override
        public String toString() {
            return within + " / " + expanded + " @ " + at;
        }
    }

    /**
     * A copy said without what it stands in: what was expanded, and where.
     *
     * <p>What one copy of a body is, told from the others a chain holds without carrying the chain.
     * A chain is derived rather than stored — a body already expanded is walked again when a copy of
     * it is made, and every step is rebuilt against wherever it is being walked — so a value that
     * held a chain would be answering about wherever it was first built. This holds neither and is
     * the same value both times.
     *
     * <p>And it tells the copies of one walk apart, because a step cannot occur twice on a chain: a
     * step is a call written in some body, so the same one deeper down would be that body expanded
     * inside its own expansion, and a declaration that can reach itself is left standing rather than
     * inlined ({@link souther.compiler.check.HelperGraph#recurses}).
     */
    record Step(ValueName expanded, ExpansionSite at) {

        public Step {
            if (expanded == null || at == null) {
                throw new IllegalArgumentException(
                        "a copy is of something, at some site: " + expanded + " at " + at);
            }
        }

        @Override
        public String toString() {
            return expanded + " @ " + at;
        }
    }

    /** This lineage with one more copy on it, for the pass that makes the copy. */
    default Expansion copiedInto(ValueName expanded, ExpansionSite at) {
        return new Expansion(this, expanded, at);
    }
}
