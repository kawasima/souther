package souther.compiler.types;

/**
 * A reference a pass wrote, said by what made it write one.
 *
 * <p>The language has no syntax for some of what a body means — the operation an empty collection
 * stands for, the library call a checked value is written back as — so a pass writes the name. That
 * name is a reference: two of them are two, and expanding each of them as a value writes a block of
 * its own. This is what tells them apart.
 *
 * <p><b>The cause and the producer's count over it, and neither alone.</b> One construct may make a
 * pass write several names, so the cause answers which construct is behind them and not which of
 * them this is. And a count on its own moves with whatever else the pass met first.
 *
 * <p><b>{@code ordinal} is over what this cause derived, and never over a walk.</b> The count a walk
 * keeps runs differently under a different policy — which calls a pass expands is what the policy it
 * runs under decides — so a reference numbered by one moves for an edit nothing about it can see. A
 * count over what one cause produced is a function of the syntax that cause is, which is what an
 * identity has to be. The empty collection at one pair of brackets is the first thing that pair
 * derived, whatever the body around it holds.
 *
 * @param cause    what the source wrote that made a pass write this reference
 * @param ordinal  which of the references that cause derived this is, by the producer's own count
 */
public record DerivedReferenceOrigin(DerivationCause cause, int ordinal) implements ReferenceOrigin {

    public DerivedReferenceOrigin {
        if (cause == null) {
            throw new IllegalArgumentException(
                    "a reference a pass wrote was written because a source wrote something");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "what a cause derived is counted from zero: " + ordinal);
        }
    }

    @Override
    public String toString() {
        return cause + "#derived" + ordinal;
    }
}
