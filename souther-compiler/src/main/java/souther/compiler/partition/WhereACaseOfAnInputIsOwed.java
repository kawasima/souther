package souther.compiler.partition;

/**
 * The identities a case of an input can be owed at.
 *
 * <p>Two, and which one it is is a fact about the behavior rather than about the case: where it has
 * a position of its own, the case and the class that position divides into are one thing a row is
 * owed for; where its input is read at its stages, nothing divides a position of its own and the
 * case is what it is owed at.
 *
 * <p>Closed so that whoever makes such a finding is held to the two. Checked instead of closed, a
 * maker reaching for an arm or a point of a line would say a case of an input is owed at one of
 * those, and nothing would refuse it until a run met it.
 */
public sealed interface WhereACaseOfAnInputIsOwed extends ObligationIdentity
        permits ObligationIdentity.OfAClass, ObligationIdentity.OfAnInputCase {}
