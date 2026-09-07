package souther.compiler.coverage;

import souther.compiler.types.ModelOccurrence;

import java.util.Map;

/**
 * What the walk that numbered the emitted sites called each construct of the model.
 *
 * <p>A bridge for a migration and nothing else. What names a construct is
 * {@link ModelOccurrence}; readers written before that was so name one by
 * {@link ComparisonOccurrence}, which is the walk's own count over the emitted bodies, and they are
 * moved off it one at a time rather than all at once.
 *
 * <p><b>One way.</b> There is no asking which construct of the model an emitted name is. That
 * question is what the emitted walk must stop being asked, so a reader holding the old name cannot
 * get back to the new one and has to be moved rather than adapted.
 *
 * <p><b>Apart from {@link ComparisonEmissionIndex}.</b> The index answers where a run through a
 * construct is recorded, which is a relation the compiler keeps; this answers what something used to
 * be called, which is a fact about the state of a change. Held together, the second would be part of
 * what the first means, and removing it would be changing what the index is.
 */
public final class LegacyComparisonAddresses {

    private final Map<ModelOccurrence, ComparisonOccurrence> emitted;

    private LegacyComparisonAddresses(Map<ModelOccurrence, ComparisonOccurrence> emitted) {
        this.emitted = emitted;
    }

    /** The names the walk behind {@code index} handed out, read off that one walk rather than taken
     *  over the bodies again. */
    public static LegacyComparisonAddresses of(ComparisonEmissionIndex index) {
        return new LegacyComparisonAddresses(index.emittedNames());
    }

    /**
     * What the emitted walk called {@code occurrence}.
     *
     * @throws IllegalStateException where the emitted bodies hold no comparison for it — the two
     *                               readings disagreeing about what a body holds, which is a
     *                               finding and not a thing to answer around
     */
    public ComparisonOccurrence of(ModelOccurrence occurrence) {
        ComparisonOccurrence which = emitted.get(occurrence);
        if (which == null) {
            throw new IllegalStateException(
                    "the emitted bodies hold no comparison for " + occurrence);
        }
        return which;
    }

    /** Whether the emitted bodies hold a comparison for it at all. */
    public boolean holds(ModelOccurrence occurrence) {
        return emitted.containsKey(occurrence);
    }
}
