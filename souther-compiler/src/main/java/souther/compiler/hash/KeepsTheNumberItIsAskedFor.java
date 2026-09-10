package souther.compiler.hash;

/**
 * A value whose own number is worked out once, from what stands for it, and kept.
 *
 * <p>For a value asked its number far more often than one of it is made — one used as a key, whose
 * parts are values of the same kind, so that working the number out walks everything under it. What
 * is walked cannot change while the value exists, so it is walked once.
 *
 * <p><b>Said here rather than read off the shape of a {@code hashCode}.</b> A rule about these
 * values that found them by looking for a number handed back out of a field would let a value that
 * stopped keeping one leave the rule without a word — and a value that stopped keeping one is the
 * whole of what the rule is against. So the value declares that it is one of these, and dropping
 * the declaration is an edit somebody makes on purpose.
 *
 * <p>What is held to it, and checked against what the class file does:
 *
 * <ul>
 *   <li>its {@code hashCode} hands back a number it holds;
 *   <li>nothing puts a number there but {@link ValueHash}, over what {@link #standsFor} answers and
 *       over nothing else it holds;
 *   <li>it holds what stands for it, that number, and nothing more — which is what a record gave
 *       for nothing and what a class of this kind has to say for itself.
 * </ul>
 */
public interface KeepsTheNumberItIsAskedFor extends SaysWhatStandsForIt {
}
