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
 * <p>Not every place is one of these. An arm no row that stands can be in carries no probe, so a run
 * through it is not something any recording could hold — and a claim on it would be one nothing could
 * ever satisfy, which is worse than no claim at all: it reads as a combination that was tried and
 * missed. {@link #of} is where that is decided, so a claim that exists is one an observation can
 * answer.
 */
public record ControlClaim(ControlPointId at) {

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
     */
    public static Optional<ControlClaim> of(ControlPointId at) {
        return switch (at) {
            case ControlPointId.ArmPoint arm ->
                    arm.isMeasured() ? Optional.of(new ControlClaim(arm)) : Optional.empty();
            // A comparison is numbered only where the fork it belongs to has an arm a run can be
            // recorded in, so one that exists is one a run can be recorded at.
            case ControlPointId.ComparisonPoint point -> Optional.of(new ControlClaim(point));
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
            case ControlPointId.ArmPoint arm ->
                    arm.probe().isPresent() && seen.lit(arm.probe().get());
            case ControlPointId.ComparisonPoint point -> seen.saw(point.at(), point.held());
        };
    }

    @Override
    public String toString() {
        return switch (at) {
            case ControlPointId.ArmPoint arm -> "arm " + arm.arm();
            case ControlPointId.ComparisonPoint point ->
                    point.at() + (point.held() ? " held" : " failed");
        };
    }
}
