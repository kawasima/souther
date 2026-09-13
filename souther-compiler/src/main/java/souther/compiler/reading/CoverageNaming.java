package souther.compiler.reading;

import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.flow.Naming;
import souther.compiler.inputs.ComparedNumber;
import souther.compiler.inputs.ComparedNumbers;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.inputs.PathResolution;
import souther.compiler.inputs.TermPath;

import java.util.ArrayList;
import java.util.List;

/**
 * The conditions along a path, said of the input positions they are about and of the places a run
 * that took them would be seen at.
 *
 * <p>What a group is composed against, and the only part of reading a body that the numbering has
 * anything to do with. Nothing here decides whether an expression arrives at a value or what a value
 * comes to: a condition this has no words for comes back as null, the reading marks the path
 * {@link souther.compiler.flow.Completeness#PARTIAL}, and the arm or the way is still there. That is
 * the whole of the separation — before it, a comparison the plan could not place was read as a
 * comparison with no value, and everything standing under it went with it.
 *
 * <p>A value per position and not one object walked along: what a name reads widens inside a
 * {@code let}, so {@link #under} answers a new one and the reading holds each where it belongs.
 */
final class CoverageNaming implements Naming<Outcome> {

    /**
     * How many ways one value is read as being settled before the reading gives up on enumerating
     * them.
     *
     * <p>A product taken at one node. What bounds how many ways in a position is read under is a
     * different bound on a different product, and the two multiply with the nesting of a body.
     */
    static final int MOST_OUTCOMES = 64;

    private final CoverageSites.Plan plan;
    private final Symbols symbols;

    /** Which declarations wear one value, which is what says whether reading a field reaches
     *  somewhere else ({@link souther.compiler.check.Location#isStep}). */
    private final DeclarationNewtypes newtypes;
    private final InputReads reads;

    /** What each comparison of this body is about, read once and shared with whatever else asks. */
    private final ComparedNumbers numbers;

    CoverageNaming(CoverageSites.Plan plan, Symbols symbols, DeclarationNewtypes newtypes,
                   InputReads reads, ComparedNumbers numbers) {
        this.plan = plan;
        this.symbols = symbols;
        this.newtypes = newtypes;
        this.reads = reads;
        this.numbers = numbers;
    }

    /** What a name reads here, which a caller walking the body needs for its own naming. */
    InputReads reads() {
        return reads;
    }

    @Override
    public Outcome nowhere() {
        return new Outcome(List.of());
    }

    @Override
    public Outcome join(Outcome held, Outcome more) {
        List<Decision> both = merge(held.holds(), more.holds());
        return both == null ? null : new Outcome(both);
    }

    // The environment moves and the reading of the comparisons does not: what a name reads here is
    // this naming's own, and what each comparison came to is one answer for the whole body.
    @Override
    public CoverageNaming under(Core.Binder binder, Core value) {
        return new CoverageNaming(plan, symbols, newtypes, reads.and(binder, value), numbers);
    }

    @Override
    public CoverageNaming insideArm(Core.Match match, Core.Case arm) {
        return new CoverageNaming(plan, symbols, newtypes,
                reads.insideArm(match, arm, symbols, newtypes), numbers);
    }

    /**
     * That {@code comparison} came out {@code held}, said of the position it is about, or null where
     * this cannot say which position that is or where no run through it could be recorded.
     *
     * <p>The comparison and not the arm. A condition stops as soon as it is settled, so under
     * {@code A && B} the arm taken when the condition fails is reached both by a value that made
     * {@code B} false and by one that never evaluated {@code B}: a row is steered by getting the
     * comparison to answer, which no arm records.
     */
    @Override
    public Outcome side(Core value, boolean held) {
        if (!(value instanceof Core.Binary comparison)) {
            // A position holding a truth comes out both ways and the plan places no comparison at
            // it, so there is nothing here to say. The fork on it is named where the way in is.
            return null;
        }
        ConstructOccurrence site = plan.comparisons().occurrenceAt(comparison)
                .filter(plan::instruments).orElse(null);
        // The one reading of this comparison, which is the reading whatever admitted the way used.
        // Read again here, the decision would be said of a number the admission never saw.
        ComparedNumber drawn = numbers.of(comparison, reads);
        if (site == null || drawn == null) {
            return null;
        }
        NumericTerm at = drawn.term();
        ControlPlace.Outcome outcome = plan.outcomeOf(site, held).orElse(null);
        if (outcome == null) {
            return null;
        }
        // The condition and the claim are about one comparison, so they are read off one value. The
        // place the claim is made at says which comparison it is a way out of; asked of the node
        // instead, the two halves of this decision would be two answers, and a decision whose
        // condition named one comparison and whose claim was recorded at another is one nothing
        // here reads both halves of to notice.
        return ControlClaim.of(outcome)
                .map(claim -> one(new Decision(
                        new Condition.Side(at, outcome.comparison(), held), claim)))
                .orElse(null);
    }

    /** Which case of the union this arm is, said of the position matched on where there is one, or
     *  null where no run through the arm could be recorded. */
    @Override
    public Outcome matchCase(Core.Match match, int part) {
        ControlPlace.Arm place = armPoint(match, part);
        ControlClaim claim = claimAt(place);
        if (claim == null) {
            return null;
        }
        // What a fork is named by is the position it is on, and a scrutinee at none names nothing —
        // which is also what a scrutinee this reading did not follow leaves to name it with.
        TermPath at = switch (reads.pathOf(match.scrutinee(), newtypes)) {
            case PathResolution.At(var stands) -> stands;
            case PathResolution.NotAPosition _ -> null;
            // A name that only may stand at a position names no one position for a fork to be on,
            // and a name built out of the ones it may be would be a place no reader could look up.
            case PathResolution.MayStandAt _ -> null;
        };
        if (at == null) {
            return one(new Decision(new Condition.Arm(place.arm()), claim));
        }
        List<String> names = match.cases().get(part).pattern().selectors().stream()
                .map(selector -> selector.name().name()).toList();
        return one(new Decision(new Condition.Case(at, String.join("|", names)), claim));
    }

    /**
     * That a run went down arm {@code part}, where nothing said which comparison sent it there.
     *
     * <p>What the reading falls back on. Where the condition's ways can be enumerated they are what
     * names the way in, on the comparisons a row is steered by; this is the answer for a condition
     * whose ways cannot all be written down, and it is the answer every fork used to get.
     *
     * <p>An arm places at no class of any input, so a group offered under one of these goes. That it
     * is here at all is what says the reading found a way in it could not name.
     */
    @Override
    public Outcome forkArm(Core fork, int part) {
        ControlPlace.Arm place = armPoint(fork, part);
        ControlClaim claim = claimAt(place);
        if (claim == null) {
            return null;
        }
        if (fork instanceof Core.If iff) {
            TermPath read = switch (reads.pathOf(iff.cond(), newtypes)) {
                case PathResolution.At(var stands) -> stands;
                case PathResolution.NotAPosition _ -> null;
                case PathResolution.MayStandAt _ -> null;
            };
            Condition what = read == null ? new Condition.Arm(place.arm())
                    : new Condition.Case(read, part == 0 ? "true" : "false");
            return one(new Decision(what, claim));
        }
        return one(new Decision(new Condition.Arm(place.arm()), claim));
    }

    @Override
    public int mostArrivals() {
        return MOST_OUTCOMES;
    }

    /**
     * The place arm {@code part} of {@code fork} is, or null where the plan numbered no such arm.
     *
     * <p>Which arm it is comes back with it, so a reading that has to name the decision without a
     * position names it off the place it has already asked for. Asked again of the fork, the answer
     * would be this plan's arms read a second time to say what the first read already said.
     */
    private ControlPlace.Arm armPoint(Core fork, int part) {
        ControlPlace.Arm[] arms = plan.armsOf(fork);
        return arms == null || part >= arms.length ? null : arms[part];
    }

    /** What a run through {@code place} would be seen doing, and null where nothing records one. */
    private static ControlClaim claimAt(ControlPlace.Arm place) {
        return place == null ? null : ControlClaim.of(place).orElse(null);
    }

    private static Outcome one(Decision decision) {
        return new Outcome(List.of(decision));
    }

    /**
     * Both sets of conditions, or null where between them they settle one decision two ways.
     *
     * <p>One rule for every place two of these are put together: the parts of a value, a way in held
     * to the way in above it, the left of an operator that stops early held to what runs after it. A
     * decision read twice is one decision, and one settled two ways is no path.
     */
    static List<Decision> merge(List<Decision> holds, List<Decision> more) {
        List<Decision> both = new ArrayList<>(holds);
        for (Decision each : more) {
            if (both.contains(each)) {
                continue;
            }
            if (disagrees(both, each)) {
                return null;
            }
            both.add(each);
        }
        return both;
    }

    /**
     * Whether {@code added} settles a decision the conditions already settle the other way.
     *
     * <p>The same decision and a different way out of it, which is two things and not one. A decision
     * named twice and settled the same way is one run doing one thing twice over —
     * {@code if a then (if a then …)} is written as two forks and no row takes one of them without
     * the other — and reading that as a contradiction would take away a path the body has.
     *
     * <p>Which decision it is is not which place a run is recorded at. Two forks on one flag are two
     * places and one decision, so what is compared is what the condition is about and never the claim
     * beside it.
     */
    private static boolean disagrees(List<Decision> holds, Decision added) {
        for (Decision already : holds) {
            Condition each = already.constrains();
            boolean otherWay = switch (added.constrains()) {
                case Condition.Case one -> each instanceof Condition.Case other
                        && other.at().equals(one.at()) && !other.name().equals(one.name());
                case Condition.Side one -> each instanceof Condition.Side other
                        && other.comparison().equals(one.comparison()) && other.held() != one.held();
                case Condition.Arm one -> each instanceof Condition.Arm other
                        && other.arm().fork().equals(one.arm().fork())
                        && other.arm().part() != one.arm().part();
            };
            if (otherWay) {
                return true;
            }
        }
        return false;
    }
}
