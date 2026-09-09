package souther.compiler.partition;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.PathResolution;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.SearchRegion;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.semantics.ConditionJoin;
import souther.compiler.types.ModelOccurrence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a row has already had to satisfy by the time it arrives at one comparison.
 *
 * <p>The region a border a guard owes is searched in. A guard states a threshold and narrows no
 * declaration, so what the positions are declared to hold is the same everywhere in a body while
 * what actually arrives at a comparison deep in one is not — and a row for a border down there,
 * looked for over the declared domains, is looked for outside the region as readily as inside it.
 *
 * <p><b>Collected where the condition is assumed, and never worked out afterwards from where a
 * comparison sits.</b> A reading that walked back up the tree would be a second account of what was
 * assumed, free to name a condition nothing here could take in — and a region narrowed on a
 * condition nothing established is narrower than the rows that arrive, which is the one direction
 * that takes a coverage item away. So what is here is what the walk itself put in.
 *
 * <p><b>Including what it could not put in.</b> A condition the arithmetic has no word for narrows
 * nothing, and it is written down all the same: what a region is is one question and how it came to
 * be that region is another, and only the second can tell a search that ran over what the
 * declarations leave because nothing stood in the way from one that ran over the same box because a
 * guard above could not be read. Held as {@link OnTheWay}, so the two are one sequence in the order
 * the walk met them rather than a list and a silence.
 *
 * <p><b>Its own value and not its arm.</b> Under {@code A && B} the {@code then} arm is reached with
 * both holding, and the {@code else} arm with neither settled — a row can be in it for failing
 * either. Under {@code A || B} it is the other way round. Which of the two a comparison is in is
 * {@link GuardThresholds}'s reading of the condition, and taking an arm for a conjunct is how a
 * region would come to exclude rows that reach the guard.
 *
 * <p>Nothing here says a row that satisfies all of this arrives. A condition of a shape the
 * arithmetic cannot read is on the list without narrowing anything, so a region built from this
 * holds every row that arrives and may hold rows that do not — which is what {@link SearchRegion}
 * promises and what a proof of unreachability rests on. It is the inclusion and not a strict one:
 * a condition nothing took in may be implied by the ones that were, or hold of every row. Being on
 * the list is what lets a report say a condition is unaccounted for; it is not what the region is
 * built from.
 */
public record ReachingCuts(Map<ModelOccurrence, List<OnTheWay>> byComparison) {

    public static final ReachingCuts NONE = new ReachingCuts(Map.of());

    public ReachingCuts {
        byComparison = Map.copyOf(byComparison);
    }

    /** One thing a row had to satisfy to get here: {@code form rel 0} over this input's terms. */
    public record Cut(LinearForm<NumericTerm> form, Rel rel) {}

    /**
     * How a row for a border on the rule stated at {@code states} came to be looked for where it is:
     * the whole account of the walk to it.
     *
     * <p>Empty where nothing was collected there — and the answer says so, rather than leaving a
     * reader to tell a comparison at the top of a body from one this could read nothing on the way
     * to. Both leave a region as wide as the declarations and both are sound; only one of them is a
     * limit of this compiler, and an author who is told nothing has no way to find out which they
     * are looking at.
     */
    public WayToTheBorder wayTo(ModelOccurrence states) {
        return new WayToTheBorder(byComparison.getOrDefault(states, List.of()));
    }

    /**
     * What {@code node} coming out {@code holding} says about this input, and where it says
     * nothing, that.
     *
     * <p>Never empty. Every shape has an answer — a cut where one could be made and a decline
     * where none could — because an empty answer is what made a comparison reached under nothing
     * and one reached past something unreadable into the same reading.
     *
     * <p>One rule for two questions, because they are one question. What reaching the right operand
     * of a condition establishes and what reaching an arm of the fork establishes are both "this
     * subtree came out this way, so what follows" — written apart, the two agreed by having been
     * derived alike, and the day one of them learned to read a new shape of condition would be the
     * day they stopped agreeing.
     *
     * <p>A joined condition that came out the way its connective gives both halves is both halves
     * having come out that way, and which way that is comes from the composition under the outcome
     * rather than from the operator. The other composition says a disjunction of things, which is
     * not a list of cuts and is not approximated into one: {@code A && B} being false says one of
     * them failed and names neither, and narrowing on either would exclude rows that arrive. So it
     * is declined whole, at the condition rather than at an operand — neither operand is what could
     * not be carried.
     */
    static List<OnTheWay> stating(Condition node, InputDomain inputs, boolean holding,
                                  RuleReadingSource ruleSource) {
        return switch (node) {
            // Coming out the way that gives both halves, each of them came out that way too. The
            // other composition says a disjunction of things, which is not a list of cuts and is
            // not approximated into one: `A && B` being false says one of them failed and names
            // neither, and narrowing on either would exclude rows that arrive. So the whole node is
            // declined, at the whole node's place.
            case Condition.Joined joined -> joined.how().under(holding) == ConditionJoin.BOTH
                    ? and(stating(joined.left(), inputs, holding, ruleSource),
                            stating(joined.right(), inputs, holding, ruleSource))
                    : List.of(new OnTheWay.Declined(joined.occurrence(), joined.anchor(),
                            new OnTheWay.Why.OneOfTwoThings()));
            case Condition.Compares one -> List.of(of(one, inputs, holding, ruleSource));
            case Condition.NotRead not -> List.of(new OnTheWay.Declined(
                    not.occurrence(), not.anchor(), new OnTheWay.Why.NoWordsForTheShape()));
        };
    }

    /**
     * What reaching {@code arm} of {@code match} establishes about this input.
     *
     * <p>Beside {@link #stating} because it is the other half of one question. Both say what a row
     * that got here has already turned out to be; they differ in the vocabulary the answer lands in,
     * and a fork's answer is not a cut — which case a value is has no arithmetic and states nothing
     * about any order.
     *
     * <p><b>The narrowing and never the arm.</b> What a search can compose against is a position
     * read as one of its cases; "the second arm was taken" is a fact about the text. So what is
     * carried is the scrutinee's position with the arm's case on it, and where this reading cannot
     * arrive at one — a scrutinee no position holds, an arm answering for several cases, an arm
     * naming a case that is itself a sum, a case the declarations leave no position at — nothing is
     * invented and the arm is declined.
     *
     * <p>And the narrowing is the one the checker's resolution of the arm settles, taken as it is
     * rather than built again from the case's name: the name says neither whether an optional's
     * present carrier or a sum's case was selected nor how many leaves selecting it covers, and a
     * narrowing spelled the wrong way is a position the reading of the input never holds.
     *
     * <p>Never empty, for the reason {@link #stating} is never empty: an arm that established
     * nothing and an arm nothing could be read of are the two answers a walk has to tell apart, and
     * a silence is both of them.
     */
    static OnTheWay entering(Core.Match match, Core.Case arm, int part, InputDomain inputs,
                             InputReads reads, RuleReadingSource ruleSource,
                             ConditionNumbering numbering) {
        ConditionOccurrence met = numbering.met();
        ConditionReportAnchor at =
                numbering.anchorOfArm(match.origin(), part, arm.pos(), met);
        Refinement narrowing = arm.selectedCase().map(Refinement::of).orElse(null);
        if (narrowing == null) {
            return new OnTheWay.Declined(met, at,
                    new OnTheWay.Why.ForkArmNotReadAsANarrowing());
        }
        // The arm is declined for either answer: a search composes against a position read as one
        // of its cases, and there is no position to narrow whether the scrutinee stands at none or
        // this reading did not follow it to one.
        TermPath scrutinee = switch (reads.pathOf(match.scrutinee(), ruleSource.symbols())) {
            case PathResolution.At(var stands) -> stands;
            case PathResolution.NotAPosition _ -> null;
            // And declined for a scrutinee that only may stand at one. What a narrowing is composed
            // against is one position; narrowing each of the ones it may be would say a row
            // reaching this arm stands at a case of every one of them, which is a region narrower
            // than the rows that arrive — the one direction that takes a coverage item away.
            case PathResolution.MayStandAt _ -> null;
        };
        // The position that is narrowed, and not the narrowed one. A case declaring no field has
        // nothing under it and this reading holds no position there, which is what it is for; what
        // has to exist is the position the case is a case of, since that is what a row writes a
        // value at and what a requirement on the way is keyed by.
        //
        // Two values and not one: where the name stands is what the environment answers, and
        // whether the input's rules hold a position there is the reading's.
        if (scrutinee == null || inputs.at(scrutinee) == null) {
            return new OnTheWay.Declined(met, at,
                    new OnTheWay.Why.ForkArmNotReadAsANarrowing());
        }
        return new OnTheWay.Narrowed(at, scrutinee.refine(narrowing));
    }

    /**
     * What {@code comparison} states about this input, coming out {@code holding} — or a decline
     * where the arithmetic reads nothing here.
     *
     * <p>Read once, off the same {@link AffineReading} every other reader of a comparison uses. A
     * second reading of what a comparison says is a second thing to keep in step with how a border
     * is drawn, and the two disagreeing is a region that excludes the very level the border is at.
     *
     * <p>And where it comes back with nothing, that is the whole of what is said. The reason the
     * same comparison gets for drawing no line is {@link UnreadComparison}'s and answers another
     * question: {@code 1 < 2} is a form nothing reads over there and constrains no position here,
     * and a form this arithmetic cannot carry is a comparison between two positions over there
     * while a relation between two positions is exactly what a cut carries here. What would tell
     * this end's cases apart is {@link AffineReading} saying why it read nothing, which it does
     * not.
     */
    private static OnTheWay of(Condition.Compares comparison, InputDomain inputs, boolean holding,
                               RuleReadingSource ruleSource) {
        ConditionReportAnchor at = comparison.anchor();
        AffineReading read = AffineReading.of(
                comparison.comparison(), inputs, comparison.reads(), ruleSource);
        if (read == null) {
            return new OnTheWay.Declined(comparison.occurrence(), at,
                    new OnTheWay.Why.ComparisonNotRepresentedAsACut());
        }
        // What the comparison states, in the words a domain is told things in. Taken the way the
        // path met it: an arm reached by the condition failing has what holds exactly where the
        // comparison does not.
        Rel states = read.claim().statedRelation();
        // The form with the threshold moved into it, since what a domain is told is `f rel 0`.
        LinearForm<NumericTerm> against =
                read.form().minus(LinearForm.constant(read.cut()));
        return new OnTheWay.TakenIn(at, new Cut(against, holding ? states : states.denied()));
    }

    /** These conditions, with the rule stated at {@code states} reached under {@code assumed}. */
    static final class Collected {

        private final Map<ModelOccurrence, List<OnTheWay>> byComparison = new LinkedHashMap<>();

        void reached(ModelOccurrence states, List<OnTheWay> assumed) {
            // Once per construct of the model, because that is what the walk reads: a comparison
            // inside a non-recursive helper is read once per call of it and each of those calls is
            // a construct of its own. Two arriving under one would be the reading holding two
            // comparisons the model states at one place, which is what nothing downstream could
            // then tell apart — so it is refused here rather than resolved by keeping one of them.
            List<OnTheWay> already = byComparison.putIfAbsent(states, List.copyOf(assumed));
            if (already != null) {
                throw new IllegalStateException(
                        "two comparisons of one reading state one construct of the model: "
                                + states);
            }
        }

        ReachingCuts made() {
            return new ReachingCuts(byComparison);
        }
    }

    /** What a caller is carrying, with more added, keeping what was already there. */
    private static List<OnTheWay> and(List<OnTheWay> assumed, List<OnTheWay> more) {
        List<OnTheWay> out = new java.util.ArrayList<>(assumed);
        out.addAll(more);
        return List.copyOf(out);
    }

}
