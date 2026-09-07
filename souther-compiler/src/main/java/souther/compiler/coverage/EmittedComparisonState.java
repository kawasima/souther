package souther.compiler.coverage;

import souther.compiler.reach.ComparisonArrival;

/**
 * What the tree that runs says about one construct the model states.
 *
 * <p>Two states, and they are not one absence. The emitter numbers a place for a comparison a run
 * can answer through and numbers none for one behind an abort — and where it numbered one, a walk of
 * the paths says what arrives at the comparison's line. A reader that had an arrival or nothing
 * could not tell "there is nowhere to watch this" from "there is somewhere and the walk proved
 * nothing", and the two say opposite things about what a measurement is short of.
 *
 * <p>What the walk could not settle is the arrival's own answer
 * ({@link ComparisonArrival.NoProjection}) and not this one's. So an arrival is asked only of a
 * comparison the emitter numbered, and it always answers.
 *
 * <p><b>There is no third state for a construct the emitted tree does not hold.</b> The model states
 * a construct and the emitted tree holds it, or the two readings of one body disagree about what the
 * body holds — which is a finding, and is refused where the join is made rather than carried as a
 * value somebody downstream has to handle.
 */
public sealed interface EmittedComparisonState {

    /** The emitter numbered a place for it, and this is what arrives at its line there. */
    record Instrumented(ComparisonEmissionSite site, ComparisonArrival arrival)
            implements EmittedComparisonState {

        public Instrumented {
            if (site == null || arrival == null) {
                throw new IllegalArgumentException(
                        "a comparison the emitter numbered has a place and something arriving at"
                                + " it: " + site + " " + arrival);
            }
        }
    }

    /**
     * The emitter numbered no place for it.
     *
     * <p>What this says is that and no more: the emitted plan has no comparison site for this
     * construct of the model. What a reader does about it — whether a line may still be drawn, and
     * what a report says — is that reader's rule and not this value's meaning.
     */
    record NotInstrumented() implements EmittedComparisonState {}
}
