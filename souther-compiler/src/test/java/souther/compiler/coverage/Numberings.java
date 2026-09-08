package souther.compiler.coverage;

import souther.compiler.coverage.ArmReportAnchor;
import souther.compiler.types.SourceConstructOrigin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A numbering for a fixture that has places in mind and no bodies to walk.
 *
 * <p>What a test writing "the arm numbered 3" needs. An address is issued by a numbering and
 * nothing else, which is what stops one being made up next to a run it is not about; a test that
 * writes a number is standing in for the walk that would have handed it out, and this is that
 * stand-in said out loud rather than a way round the rule.
 *
 * <p>Two calls asking for the same places come to the same numbering, so two fixtures that both
 * write "arm 3" hold one address. That is what a numbering is — a value two derivations agree on —
 * and it is why a fixture need not pass one construction around to keep its places together. Two
 * calls asking for <em>different</em> places are different numberings, and an address of one is no
 * address of the other.
 */
public final class Numberings {

    /**
     * A numbering of {@code many} places, alternating nothing: every one of them is an arm.
     *
     * <p>The addresses are of a body this fixture does not have, so what each is <em>of</em> is a
     * path nothing walked. What a fixture uses them for is the numbers, and the places are what
     * makes them addresses at all.
     */
    public static SiteNumbering ofArms(int many) {
        Family[] families = new Family[many];
        java.util.Arrays.fill(families, Family.ARM);
        return of(families);
    }

    /** The arm {@code raw} of a numbering of {@code many} arms. */
    public static ArmProbe arm(int many, int raw) {
        return ofArms(many).arm(raw);
    }

    /**
     * The place {@code probe} addresses, as a fixture with no body to walk has it.
     *
     * <p>A site of an arm holds the place and takes its address off it, which is the walk's doing
     * and cannot be had without one. So a fixture writing "the arm numbered 3" says here what that
     * arm is — the same stand-in the numbers themselves are, and said in one place rather than in
     * each fixture that needs a site.
     *
     * <p>Which place it is comes in rather than being read off the number. A walk hands out control
     * points and probe numbers from counters of their own and a place is not its address, so a
     * fixture that let one stand for the other would be writing down a correspondence no numbering
     * makes.
     */
    public static ControlPointId.ArmOccurrence armPlace(int controlId, ArmProbe probe,
                                                        SourceConstructOrigin origin,
                                                        ArmReportAnchor anchor) {
        return new ControlPointId.ArmOccurrence(controlId, Optional.of(probe), anchor, origin);
    }

    /** The arms of one numbering, by their numbers, so a fixture holds addresses of one. */
    public static Map<Integer, ArmProbe> arms(int many) {
        SiteNumbering numbering = ofArms(many);
        Map<Integer, ArmProbe> out = new LinkedHashMap<>();
        for (int at = 0; at < many; at++) {
            out.put(at, numbering.arm(at));
        }
        return out;
    }

    /**
     * A numbering of {@code many} places, every one of them a comparison.
     *
     * <p>Beside {@link #ofArms} and not mixed with it, because a fixture that wants both wants to
     * say which number is which — {@link #of} takes that.
     */
    public static SiteNumbering ofComparisons(int many) {
        Family[] families = new Family[many];
        java.util.Arrays.fill(families, Family.COMPARISON);
        return of(families);
    }

    /** The comparison {@code raw} of a numbering of {@code many} comparisons. */
    public static ComparisonEmissionSite comparison(int many, int raw) {
        return ofComparisons(many).comparison(raw);
    }

    /** The comparisons of one numbering, by their numbers. */
    public static Map<Integer, ComparisonEmissionSite> comparisons(int many) {
        SiteNumbering numbering = ofComparisons(many);
        Map<Integer, ComparisonEmissionSite> out = new LinkedHashMap<>();
        for (int at = 0; at < many; at++) {
            out.put(at, numbering.comparison(at));
        }
        return out;
    }

    /** Which of the two families a fixture's number was handed out to. */
    public enum Family { ARM, COMPARISON }

    /**
     * A numbering whose numbers were handed out to the families named, in that order.
     *
     * <p>For a fixture whose point is that the two are told apart: which number addresses which
     * kind of place is what the numbering answers, so a fixture that means "3 is a comparison" says
     * it here rather than by what it later passes 3 to.
     */
    public static SiteNumbering of(Family... families) {
        java.util.List<SiteAddress> byNumber = new java.util.ArrayList<>();
        for (int at = 0; at < families.length; at++) {
            byNumber.add(switch (families[at]) {
                case ARM -> armAt(at);
                case COMPARISON -> new SiteAddress.Comparison(
                        new NodeAddress("fixture", java.util.Set.of(pathTo(at))));
            });
        }
        return SiteNumbering.of(new NumberingIdentity("fixture", Map.of(), byNumber));
    }

    private static SiteAddress.Arm armAt(int at) {
        return new SiteAddress.Arm(
                new NodeAddress("fixture", java.util.Set.of(pathTo(at))), 0);
    }

    /** A way down nothing walked, distinct per place so that no two are one. */
    private static CorePath pathTo(int at) {
        CorePath path = CorePath.ROOT;
        for (int step = 0; step <= at; step++) {
            path = path.then(new CoreStructure.Edge.CallArgument(step));
        }
        return path;
    }

    private Numberings() {}
}
