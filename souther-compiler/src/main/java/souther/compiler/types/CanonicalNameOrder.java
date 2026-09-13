package souther.compiler.types;

import java.util.List;
import java.util.Set;

/**
 * The order this compiler shows a set of names in.
 *
 * <p>A union is a set: {@code Adult | Minor} and {@code Minor | Adult} are one value, and the value
 * does not hold which of the two was written. So the order a reader is shown cannot be the author's
 * — there is no author's order on a value two authors reach — and it is this compiler's decision
 * instead. This is where that decision is written down.
 *
 * <p>{@link TypeSymbol}'s own order is the one taken: the name first, because that is what a reader
 * looking down a list reads, and then what tells two of one spelling apart. Named here rather than
 * reached for wherever a set of names becomes a sentence, so that the order is one thing to change
 * and changing it reads as what it is — a change to what readers are shown, not a tidy-up.
 *
 * <p><b>There is no author's order here to keep.</b> The members are read in the order they were
 * written and put in a set, and past that nothing holds which writing it was — a sequence of them
 * further on is one taken back off the set. So a reader that means to quote what somebody wrote
 * cannot be served out of a type at all, and would have to be handed the order before the members
 * reach one.
 *
 * <p>Nothing comes back that says it is in this order. A plurality that crosses into a report says
 * which order it is in by its type, because between being handed one and reading it lies everything
 * a consumer might do with the order; a sequence here becomes the sentence in the expression that
 * asked for it and is never held by anybody. The claim is the call.
 */
public final class CanonicalNameOrder {

    private CanonicalNameOrder() {
    }

    /** {@code names}, in the order they are shown. */
    public static List<TypeSymbol> shown(Set<TypeSymbol> names) {
        return names.stream().sorted().toList();
    }
}
