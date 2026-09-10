package souther.compiler.coverage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A run made up to be exactly what some claims name.
 *
 * <p>What a fixture reaches for when the question is what a reader does with a run, rather than what
 * running produces. The places come off the claims, so a fixture cannot write a run at a place no
 * claim is about — which is the mistake that makes such a test pass for a reason nobody chose.
 *
 * <p>Which numbering it is a run of is said rather than gathered from whatever the claims turned out
 * to hold: a run of no claims is still a run of some numbering, and a fixture that let the claims
 * decide would have none to name for it. A claim about a place of another numbering is refused
 * here, because that is a fixture saying two things at once.
 *
 * <p>Already aligned, because there is nothing to align: the places are places. A fixture that
 * wants the crossing itself tested writes an {@link Observation} and calls
 * {@link SiteNumbering#align}.
 */
public final class Runs {

    /** A run of {@code numbering} that did everything {@code claims} names and nothing else. */
    public static AlignedObservation doing(SiteNumbering numbering, List<ControlClaim> claims) {
        Set<ArmProbe> arms = new LinkedHashSet<>();
        Set<SeenComparison> ways = new LinkedHashSet<>();
        for (ControlClaim claim : claims) {
            switch (claim.at()) {
                case ControlPlace.Arm arm -> arm.probe().ifPresent(arms::add);
                case ControlPlace.Outcome point ->
                        ways.add(new SeenComparison(point.at(), point.held()));
            }
        }
        return new AlignedObservation(numbering.identity(), arms, ways);
    }

    /** A run of {@code numbering} at those places. */
    public static AlignedObservation of(SiteNumbering numbering, Set<ArmProbe> arms,
                                        Set<SeenComparison> ways) {
        return new AlignedObservation(numbering.identity(), arms, ways);
    }

    /** A run of {@code numbering} that was at those arms and nowhere else. */
    public static AlignedObservation at(SiteNumbering numbering, Set<ArmProbe> arms) {
        return of(numbering, arms, Set.of());
    }

    /** A run of {@code numbering} that nothing was recorded of — which is a run, and not the
     *  absence of one. What says nobody watched a row is said where the row is handed over. */
    public static AlignedObservation nowhere(SiteNumbering numbering) {
        return new AlignedObservation(numbering.identity(), Set.of(), Set.of());
    }

    private Runs() {}
}
