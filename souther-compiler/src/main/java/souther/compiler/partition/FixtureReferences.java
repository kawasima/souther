package souther.compiler.partition;

import souther.compiler.types.FixtureReferenceOrigin;

/**
 * Mints the references one run of the generator composes.
 *
 * <p>A row naming a value the module states holds a name reaching a declaration, and such a name is
 * some reference of it. No source wrote this one and nothing derived it from a construct that was
 * written, so the occurrence begins here — and the authority that made it is the one that can say
 * which it is ({@link FixtureReferenceOrigin}).
 *
 * <p>One per run, held by the run and handed to whatever composes. A minter per composer would
 * start each of them at nought and hand one number to several references; a static counter would
 * carry one run's numbering into the next, which is the same value meaning two things. So it is
 * passed the way the check and the axes are, and what a reference is numbered among is the run that
 * composed it.
 *
 * <p>Counted over what was composed and never over what became a row. A reference made for a
 * candidate the check then turned down was an occurrence while it stood, and giving its number to
 * the next one would put two occurrences under one. {@link RowId} answers the other question — which
 * composed row this is — and the two are not made to agree.
 */
public final class FixtureReferences {

    private int next;

    /** A reference nothing else in this run has. */
    public FixtureReferenceOrigin next() {
        return new FixtureReferenceOrigin(next++);
    }

    /** How many this run has composed, which is what the next one will be numbered. */
    public int composed() {
        return next;
    }
}
