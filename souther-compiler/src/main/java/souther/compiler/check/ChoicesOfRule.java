package souther.compiler.check;

import java.util.Map;

/**
 * The fate of every choice of one rule, by where in its clause the author wrote it.
 *
 * <p>Which rule these are of is not here. A coordinate of a clause means something only beside the
 * clause it is of, and what supplies that is whoever chose this table out of {@link ChoicesDecided}
 * — so a reader holding one of these has no rule to get wrong and nothing to pair an occurrence
 * with. Held here as well, the rule would be a second answer to which rule a reading is about, and
 * two of them can be built disagreeing.
 */
final class ChoicesOfRule {

    static final ChoicesOfRule NONE = new ChoicesOfRule(Map.of());

    private final Map<ClauseOccurrence, Settlement.OfAChoice> byOccurrence;

    ChoicesOfRule(Map<ClauseOccurrence, Settlement.OfAChoice> byOccurrence) {
        this.byOccurrence = Map.copyOf(byOccurrence);
    }

    /** What the choice written at {@code at} came to, or null where nothing settled one there. */
    Settlement.OfAChoice at(ClauseOccurrence at) {
        return byOccurrence.get(at);
    }
}
