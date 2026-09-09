package souther.compiler.coverage;

import souther.compiler.reach.ComparisonArrival;
import souther.compiler.types.ConstructOccurrence;

import java.util.List;

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
 * <p><b>One construct, several places it is watched at.</b> A library operation may evaluate a
 * closure it was handed more than once, so a comparison the author wrote once is written into the
 * tree that runs more than once and each of them may be numbered. The model states one rule, so
 * this is one state — and what it holds is every place that rule is watched at, because what a row
 * meets is any of them and what would drop a line is all of them proving nothing arrives.
 *
 * <p><b>There is no third state for a construct the emitted tree does not hold.</b> The model states
 * a construct and the emitted tree holds it, or the two readings of one body disagree about what the
 * body holds — which is a finding, and is refused where the join is made rather than carried as a
 * value somebody downstream has to handle.
 */
public sealed interface EmittedComparisonState {

    /**
     * The emitter numbered a place for at least one of its materialisations, and these are the
     * places and what arrives at the line at each.
     *
     * <p>Only the ones it numbered. A materialisation with no site is nowhere a run is watched, and
     * carrying it here would put a place in a list of places that has none.
     */
    record Instrumented(List<Observation> observations)
            implements EmittedComparisonState {

        public Instrumented {
            observations = List.copyOf(observations);
            if (observations.isEmpty()) {
                throw new IllegalArgumentException(
                        "a comparison the emitter numbered is watched somewhere");
            }
        }
    }

    /**
     * One place a construct of the model is watched at, and what arrives at its line there.
     *
     * <p>The occurrence beside the site because a walk of the paths is keyed by what the numbering
     * called the materialisation, and the site is where a run through it is written down. Two facts
     * about one place, kept together so that a reader asking either has the other.
     */
    record Observation(ConstructOccurrence occurrence, ComparisonEmissionSite site,
                       ComparisonArrival arrival) {

        public Observation {
            if (occurrence == null || site == null || arrival == null) {
                throw new IllegalArgumentException(
                        "a place a comparison is watched at is one the numbering counted, with a"
                                + " site and something arriving at it");
            }
        }
    }

    /**
     * The emitter numbered no place for it.
     *
     * <p>What this says is that and no more: the emitted plan numbered no site for any
     * materialisation of this construct of the model. What a reader does about it — whether a line may still be drawn, and
     * what a report says — is that reader's rule and not this value's meaning.
     */
    record NotInstrumented() implements EmittedComparisonState {}
}
