package souther.compiler.coverage;

import java.util.Optional;

/**
 * Something a run can be held to: that it passed one place, coming out one way.
 *
 * <p>The one thing a reading of the model may assert about a run, and the only shape a certification
 * has to know. A claim is a place and nothing about why anyone is interested in it — which is what
 * keeps whatever is doing the reading and whatever records the running from growing into each other.
 * The reading's own vocabulary changes as the reading gets better; that a comparison came out false
 * does not.
 *
 * <p>Not every place is one of these. A place no run could be recorded at is one a claim could never
 * be satisfied at, which is worse than no claim at all: it reads as a combination that was tried and
 * missed. {@link #of} is where that is decided, so a claim that exists is one an observation can
 * answer.
 */
public record ControlClaim(ControlPlace at) {

    public ControlClaim {
        if (at == null) {
            throw new IllegalArgumentException("a claim is a claim about somewhere");
        }
    }

    /**
     * The claim that a run passed {@code at}, or empty where no run could be recorded there.
     *
     * <p>Empty is an ordinary answer and the safe direction: what cannot be witnessed cannot be
     * claimed, so whatever was going to be built on it is left unbuilt rather than built and never
     * satisfiable.
     *
     * <p>One rule, read off whichever half of the place answers it. An arm carries its probe and
     * says outright whether it has one. A comparison coming out one way exists only where the plan
     * numbered the comparison — which the plan does for a comparison standing where a row can get to
     * and where what it stands in answers a value — so a {@link ControlPlace.Outcome} that exists is
     * one a run can be recorded at, and one in a position no run reaches is a place the plan makes
     * no outcome for at all.
     */
    public static Optional<ControlClaim> of(ControlPlace at) {
        return switch (at) {
            case ControlPlace.Arm arm ->
                    arm.isMeasured() ? Optional.of(new ControlClaim(arm)) : Optional.empty();
            case ControlPlace.Outcome outcome -> Optional.of(new ControlClaim(outcome));
        };
    }

    /**
     * Whether {@code seen} did this.
     *
     * <p>Existential, the way every measure over a run here is: a row that passed the place passed
     * it. What this does not say is how many times, which is why a claim is only ever made where a
     * run passes the place once ({@link CoverageSites.Plan#mayRepeat}).
     */
    public boolean satisfiedBy(AlignedObservation seen) {
        return switch (at) {
            case ControlPlace.Arm arm ->
                    arm.probe().isPresent() && seen.lit(arm.probe().get());
            case ControlPlace.Outcome outcome -> seen.saw(outcome.at(), outcome.held());
        };
    }

    @Override
    public String toString() {
        return switch (at) {
            case ControlPlace.Arm arm -> "arm " + arm.arm();
            case ControlPlace.Outcome outcome -> outcome.toString();
        };
    }
}
