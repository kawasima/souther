package souther.compiler.coverage;

import souther.compiler.core.Core;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.WrittenOwner;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

/**
 * The arms of a behavior's body that an {@code example} row can be in or not in.
 *
 * <p>What this counts is <em>branch-arm coverage</em>, and calling it anything larger would overstate
 * it. Passing through all four arms of
 *
 * <pre>if A { if B { X } else { Y } } else { Z }</pre>
 *
 * does not mean the interactions between A and B were tried, nor that every reachable path was. Branch
 * coverage is a lower bound on covering the paths a body has, not the same thing, and a report that
 * says "code paths" invites the author to stop looking.
 *
 * <p>An arm that answers nothing gets no site. Reaching one is already an error ({@code E1911}), so a
 * probe there would leave every correct model permanently one arm short and make the denominator lie.
 * Which arms those are is {@link NormalReturn}'s answer, so an arm whose every path ends in an
 * {@code unreachable} is as much not an arm as one written as a bare {@code unreachable}. An
 * invariant clause gets none either: it is a property of a type, not a fork in this body, and whether
 * the rows reach its edges is a different measure asked another way.
 *
 * <p>A {@code guard … else} and a comprehension's condition are both an {@code if} by the time they
 * get here, so the walk has one case for the three of them — and what they are is not read off that
 * case. Which construct the author wrote is carried from where the source was read
 * ({@link souther.compiler.types.SourceConstruct}), and what one way through it means is a
 * {@link SourceOutcome} beside it. Deciding either from the shape of the lowered node is answering a
 * question about the source out of the tree that runs, which is how a comprehension came to be
 * reported as a {@code guard} with a {@code then} arm.
 *
 * <p>Helper bodies are not walked: a non-recursive helper is already inlined into the body that uses it,
 * and a recursive one is a shared method rather than a fork in any one behavior.
 */
public final class CoverageSites {

    /**
     * Which rule one occurrence of a fork decides by.
     *
     * <p>Beside the fork's own construct in what an arm is counted under. A non-recursive helper is
     * spliced into each body that calls it, and the copies are one arm the author wrote — which
     * holds while the helper decides for itself. Where the caller hands the rule in, each call
     * decides by a different rule, and those are different things to cover: a row through one of
     * them establishes nothing about the next.
     *
     * <p>Which of the two a fork is, is {@link DecisionSources}'s answer, read off the declaration
     * that wrote the fork. Not read off the condition standing here: after expansion the argument
     * the call site supplied is standing where the parameter was, whether it was the rule or what
     * the rule reads, and no description of what is left can tell those apart. Two descriptions
     * that came out alike were counted as one obligation, and a rule nothing exercised was reported
     * as covered.
     */
    private static DecidedBy decidedAt(SourceConstructOrigin fork, List<BindingOwner> within,
                                       DecisionSources decisions, SuppliedRules supplied) {
        if (!(decisions.at(fork) instanceof DecisionSource.Supplied by)) {
            return DecidedBy.THE_DECLARATION;
        }
        // The rules this copy was handed, asked of the copy it is and of the parameters the
        // declaration named. Both are answers somebody else already had: which copy this is, the
        // elaboration stamped on the fork; what was handed to each parameter, the call site
        // recorded. Worked out here instead — from whatever names the fork's own subtree happens to
        // hold — a fork deciding by a rule that reduced to a constant is a fork with nothing to ask
        // about, and a combinator nested inside one answered for the fork above it.
        // The copy of the declaration that wrote this fork, which is not always the innermost copy
        // the fork stands in: a helper's own fork can be written inside a block it hands to
        // something else, and what is nearest round it is then that something else's. Which copy is
        // meant is a copy of that declaration — asked by the names of its parameters instead, a copy
        // of anything else that spells one the same way answers first, and it answers with its own
        // rule.
        for (BindingOwner owner : within) {
            // Both sides say which declaration by the name this module reaches it under, which is
            // one name because both took it from one place: the reading walked the table of what
            // this body can reach, and the expansion looked its callee up in that same table under
            // the name the call resolved to. A second way of saying which declaration -- one side
            // naming it, the other spelling it -- would agree until a declaration came to be reached
            // under two names, and nothing would say which of them a copy was of.
            if (!by.declaration().equals(supplied.declarationOf(owner))) {
                continue;
            }
            List<SuppliedRules.RuleIdentity> rules = new ArrayList<>();
            for (String parameter : by.parameters()) {
                SuppliedRules.RuleIdentity rule = supplied.at(owner, parameter);
                if (rule != null) {
                    rules.add(rule);
                }
            }
            if (rules.size() == by.parameters().size()) {
                return new DecidedBy.BySupplied(rules);
            }
        }
        return DecidedBy.NOT_SAID;
    }

    /**
     * What stands in an arm's place where the arm answers nothing.
     *
     * <p>The arms of a node are handed to the emitter as an array it indexes by the arm's position, so
     * an arm without a probe has to keep its place. Compacting the array would move every arm after
     * it down one and the emitter would light its neighbour's probe — a hit recorded against an arm
     * nothing ran, which no count can be told apart from a real one.
     */
    public static final int NO_SITE = -1;

    /**
     * What a row is owed for, which is not the same as where one runs.
     *
     * <p>A non-recursive helper is spliced into each body that calls it, so one arm the author wrote
     * is several arms in the tree that runs. Each of those is emitted and probed on its own, and each
     * is asked separately whether anything can reach it — so the occurrences stay apart everywhere
     * below. What they are not is several things to write rows for: covering the same arm through a
     * second call site establishes nothing the first did not. This is the key those occurrences are
     * one under.
     *
     * <p>Per behavior, because the measurement is per behavior. Two behaviors calling one helper each
     * owe its arms, and a row written for one of them is not a row written for the other.
     *
     * @param part which arm of the fork — its place among the fork's arms. Always zero for a
     *             comparison, whose origin is its own rather than the fork's: a comparison is one
     *             construct, and what a fork holds several of is arms
     */
    public record Obligation(String behavior, SourceConstructOrigin origin, int part,
                             DecidedBy decided) {

        /**
         * A row is owed for what a definition's body wrote, which is asked where the value is made
         * rather than where one is published.
         *
         * <p>Here rather than at the numbering, which is where it stood: the numbering refused an
         * origin no source wrote as it handed out a number, and a fork read back through
         * {@link GuardRef} is made without going that way, so the refusal covered one of the two
         * places an obligation comes from. Stated of the value, there is no such place.
         */
        public Obligation {
            WrittenOwner.theBodyThatWrote(origin.owner());
        }

        /** The definition whose body wrote the construct. */
        public WrittenOwner.Body writtenIn() {
            return WrittenOwner.theBodyThatWrote(origin.owner());
        }
    }

    /**
     * One outcome of one construct, as it stands in the tree that runs.
     *
     * <p>Two halves and one meaning. {@link #obligation} carries the construct the source wrote, and
     * {@link #outcome} what this way through it means; neither says the other, so nothing here can
     * hold two answers about one construct. Which of them the pair comes to is {@link #name}, and
     * that is as far as this goes — a condition holding is a {@code then} under an {@code if} and the
     * rest of the block under a {@code guard}, and both of those are said in a language, by whoever
     * has a reader in front of them.
     *
     */
    public sealed interface Site permits ArmSite, ComparisonSite {

        String behavior();

        /** What this way through the construct means, in the source's terms. */
        SourceOutcome outcome();

        /** Where the arm is written, as a report may say it. A {@link Citation} and not a place,
         *  because an arm of a body spliced in from out of sight is at a call in the caller's file
         *  and is not written there — a report handed the coordinate said it was, in both of its
         *  renderings. */
        Citation at();

        /** Where a run through this is recorded, of whichever family it was issued to. What
         *  identifies it in this run — the probe number, and what a hit set holds. One per
         *  occurrence: the emitter lights this one, and the reachability analysis proves things
         *  about this one. */
        RunSite index();

        /** Where it comes in its behavior, for display. */
        int ordinal();

        /** What a row would be owed for, which several occurrences share. */
        Obligation obligation();

        /** What the author wrote this an outcome of. */
        default SourceConstruct construct() {
            return obligation().origin().kind();
        }

        /** What a reader is told this is, which the two halves settle together. */
        default OutcomeName name() {
            return OutcomeName.of(construct(), outcome());
        }
    }

    /**
     * One arm of one fork, as it stands in the tree that runs.
     *
     * <p>The way through a fork a branch measure counts, and what it is is said by its type rather
     * than asked of it. A reader that only ever has arms — a denominator, a row owed at one, the
     * words a report writes for one — takes one of these, and a comparison cannot be handed where
     * one is wanted.
     */
    public record ArmSite(String behavior, SourceOutcome.Arm outcome,
                          ControlPointId.ArmOccurrence occurrence, int ordinal,
                          Obligation obligation) implements Site {

        public ArmSite {
            // The pair is what carries the meaning, so the pair is what is checked. Not every
            // combination is a construct of the language — a comprehension attempts no construction,
            // a `match` settles no condition — and a walk that put an outcome on the wrong construct
            // is the defect this whole value exists to make impossible.
            OutcomeName.of(obligation.origin().kind(), outcome);
            if (occurrence == null) {
                throw new IllegalArgumentException("a site of an arm is the arm's own place, and"
                        + " this one is being made without it");
            }
            if (occurrence.probe().isEmpty()) {
                throw new IllegalArgumentException("a site is a place a run is recorded at, and "
                        + occurrence + " is a place nothing records one at");
            }
        }

        /**
         * Where a run through this arm is recorded.
         *
         * <p>The occurrence's, because the occurrence is the place and an address is what the
         * numbering gave the place. Held here as well it would be the same fact written twice, and
         * a site whose two halves addressed different arms would be a value nothing could refuse.
         */
        @Override
        public ArmProbe index() {
            return occurrence.probe().orElseThrow();
        }

        /** Where the fork this is an arm of is, as the occurrence has it. */
        @Override
        public Citation at() {
            return occurrence.at();
        }
    }

    /**
     * One comparison, as a place a run through it is recorded.
     *
     * <p>Not an arm and counted as one nowhere. A condition stops as soon as it is settled, so which
     * arm a row landed in does not say which comparison ran — and a measure over arms that took one
     * of these would be counting a place its denominator was never about.
     */
    public record ComparisonSite(String behavior, SourceOutcome.Compared outcome, Citation at,
                                 ComparisonEmissionSite index, int ordinal, Obligation obligation)
            implements Site {

        public ComparisonSite {
            OutcomeName.of(obligation.origin().kind(), outcome);
        }
    }

    /**
     * The two arms of one {@code if}, so that a threshold read off its condition can ask whether the
     * comparison was ever evaluated.
     *
     * <p>Reaching a value is not reaching a comparison. A row can hand a behavior the exact boundary
     * value and never get to the guard that cares about it, because an earlier branch went the other
     * way — so a boundary drawn by a guard is met only by a row that also lit one of these.
     *
     * <p>Either side may be {@link #NO_SITE}, where that arm answers nothing: the comparison was
     * still evaluated to reach the arm beside it. An {@code if} whose arms both answer nothing has no
     * {@code GuardRef} at all — there is nothing left for a row to reach, and a reference with two
     * absent sides would report the line as never met however the model is exercised.
     *
     * @param at the fork's own place. A coordinate and not a reference over one: a reference
     *           carries a source beside the one the coordinate has, and a walk over one module
     *           pairs its own with a position from a helper another module wrote. Nothing reads
     *           the pair, and a value that can hold two answers about one place is one a reader
     *           can pick the wrong half of.
     */
    public record GuardRef(String behavior, SourceConstructOrigin origin, DecidedBy decided,
                           java.util.Optional<ArmProbe> whereThen,
                           java.util.Optional<ArmProbe> whereElse, SourcePos at) {

        /** The fork this is one occurrence of. Two calls of one helper give two of these, and a line
         * drawn on the condition is one line however many of them there are. */
        public Obligation fork() {
            return new Obligation(behavior, origin, 0, decided);
        }
    }

    /**
     * Every site of a module, and how to find the ones belonging to a node.
     *
     * <p>{@code byNode} is keyed by identity. Core nodes are records, so two arms that look the same
     * are equal, and a value-keyed map would hand the emitter the wrong arm's probe. The instances
     * here must be the ones the emitter is walking — the same answer, not an equal one.
     *
     * <p>Which is why this is not a value and says nothing of itself. A plan of one graph and a
     * plan of another built from the same source hold everything alike and answer for different
     * objects, so there is no equality to write that would be true of both: two of these are one
     * where they are one object. What is a value is {@link NumberingIdentity} — the same places
     * under the same numbers over the same executable — and that is what two builds of a module are
     * held against each other by, and what an answer holding a plan is compared by.
     */
    public static final class Plan {

        private final List<Site> sites;
        private final List<GuardRef> guards;
        private final IdentityHashMap<Core, int[]> byNode;
        private final Map<ComparisonOccurrence, ComparisonEmissionSite> byComparison;
        private final IdentityHashMap<Core, ControlPointId.ArmOccurrence[]> armsByNode;
        private final Map<ComparisonOccurrence, Integer> controlByComparison;
        private final java.util.Set<Core> mayRepeat;
        private final IdentityHashMap<Core, ForkOccurrence> forkByNode;
        private final ComparisonCatalog comparisons;
        private final SiteNumbering numbering;

        /**
         * Made where the bodies are walked, and nowhere a caller can reach.
         *
         * <p>Not public, and that is the whole of what keeps a module to one plan. A plan is filed
         * by which {@code Core} objects were put in it, so one built by a caller out of parts is an
         * index into a graph that caller does not own — and nothing downstream can see the
         * difference, because a lookup that misses and a place that is not numbered come back
         * alike.
         *
         * <p>Which also settles what the checks below are for. They no longer answer a caller
         * assembling a plan by hand; they answer {@link #asPlan}, which puts one together field by
         * field out of what one walk found, and can put a numbering beside a catalog it does not
         * go with.
         */
        Plan(List<Site> sites, List<GuardRef> guards, IdentityHashMap<Core, int[]> byNode,
             Map<ComparisonOccurrence, ComparisonEmissionSite> byComparison,
             IdentityHashMap<Core, ControlPointId.ArmOccurrence[]> armsByNode,
             Map<ComparisonOccurrence, Integer> controlByComparison,
             java.util.Set<Core> mayRepeat,
             IdentityHashMap<Core, ForkOccurrence> forkByNode,
             ComparisonCatalog comparisons,
             SiteNumbering numbering) {
            // Half of what a numbering could get wrong is the key's own answer now: an occurrence
            // names a comparison and nothing else, so there is no number to put on an `&&` or on
            // arithmetic, which is what would have had the emitter copy half a `long` off the
            // stack.
            //
            // The other half is not. An occurrence is a module, a behavior and a number — ordinary
            // values — so one issued by another module's catalog is a key like any other, and a
            // plan assembled out of parts can be numbering comparisons its own catalog never held.
            // Every reader below joins on the catalog to tell a comparison this plan does not
            // instrument from one that was never this plan's, and that only answers while the
            // numbering is of the catalog beside it.
            for (ComparisonOccurrence numbered : byComparison.keySet()) {
                requireHeld(numbered, comparisons, "numbered");
            }
            for (ComparisonOccurrence numbered : controlByComparison.keySet()) {
                requireHeld(numbered, comparisons, "given a control point");
            }
            // And the numbering says what each number it handed out addresses, so there is one
            // address per site and it is at the site's own number. A plan is put together field by
            // field, so the two can be handed over out of step — and a reader asking what a hit
            // means would be told about a place the number was never issued to, or about none.
            if (numbering.identity().byNumber().size() != sites.size()) {
                throw new IllegalArgumentException("this plan numbered " + sites.size()
                        + " places and says what " + numbering.identity().byNumber().size()
                        + " of them are; the sites and the addresses are one answer or they are"
                        + " two");
            }
            this.sites = sites;
            this.guards = guards;
            this.byNode = byNode;
            this.byComparison = byComparison;
            this.armsByNode = armsByNode;
            this.controlByComparison = controlByComparison;
            this.mayRepeat = mayRepeat;
            this.forkByNode = forkByNode;
            this.comparisons = comparisons;
            this.numbering = numbering;
        }

        /** Every place of this module, in the order they were numbered. */
        public List<Site> sites() {
            return sites;
        }

        /** Which comparisons these bodies hold, under the names this plan numbers them by. */
        public ComparisonCatalog comparisons() {
            return comparisons;
        }

        /** The numbering these addresses are of. */
        public SiteNumbering numbering() {
            return numbering;
        }

        /** What a run was recorded on the way out of, by the condition it stood on. */
        List<GuardRef> guards() {
            return guards;
        }

        /** The nodes this plan numbered arms for, and their probes. */
        IdentityHashMap<Core, int[]> byNode() {
            return byNode;
        }

        Map<ComparisonOccurrence, ComparisonEmissionSite> byComparison() {
            return byComparison;
        }

        IdentityHashMap<Core, ControlPointId.ArmOccurrence[]> armsByNode() {
            return armsByNode;
        }

        Map<ComparisonOccurrence, Integer> controlByComparison() {
            return controlByComparison;
        }

        java.util.Set<Core> mayRepeat() {
            return mayRepeat;
        }

        IdentityHashMap<Core, ForkOccurrence> forkByNode() {
            return forkByNode;
        }

        /** What this plan is a numbering of, as two builds can be held against each other by. */
        public NumberingIdentity identity() {
            return numbering.identity();
        }

        private static void requireHeld(ComparisonOccurrence which, ComparisonCatalog comparisons,
                                        String what) {
            if (!comparisons.holds(which)) {
                throw new IllegalArgumentException("a comparison this plan's catalog does not hold "
                        + "was " + what + ": " + which
                        + "; the numbering and the catalog are one answer or they are two");
            }
        }

        public static final Plan NONE = new Plan(List.of(), List.of(), new IdentityHashMap<>(),
                new LinkedHashMap<>(), new IdentityHashMap<>(), new LinkedHashMap<>(),
                java.util.Set.of(), new IdentityHashMap<>(),
                ComparisonCatalog.of(ModuleBodies.none()),
                SiteNumbering.of(NumberingIdentity.forThePlanOfNothing()));

        /**
         * Whether one run of the behavior can pass {@code node} more than once.
         *
         * <p>What decides whether a set is enough to say what a run did. A recording holds that a
         * place was passed and not how many times it was, so two facts recorded about a place a run
         * passes twice cannot be told from two facts about one passing — and a statement about
         * several places meeting is about their meeting once, which such a recording cannot answer.
         *
         * <p>Inherited downwards, so a node this is false of stands under nothing this is true of.
         * That is what lets one question asked at a meeting answer for everything the meeting names.
         */
        public boolean mayRepeat(Core node) {
            return mayRepeat.contains(node);
        }

        /**
         * The arms of {@code node}, in the order the emitter emits them, or null where this node has
         * none.
         *
         * <p>Positional and parallel to {@link #probesOf}, and longer in what it can say: every arm
         * is here whether or not a run through it could be recorded. A reader counting what rows are
         * owed wants the probed ones and can ask each; a reader judging what an arm declares wants
         * the arm, which is the one this has and the other does not.
         */
        public ControlPointId.ArmOccurrence[] armsOf(Core node) {
            return armsByNode.get(node);
        }

        /** Which fork {@code node} is, or null where this plan made no arms for it. */
        public ForkOccurrence forkAt(Core node) {
            return forkByNode.get(node);
        }

        /** Which way {@code comparison} coming out {@code result} is, or empty where this plan
         *  numbered no comparison there. */
        public java.util.Optional<ControlPointId.ComparisonPoint> outcomeOf(
                ComparisonOccurrence which, boolean result) {
            Integer control = controlByComparison.get(ofThisPlan(which));
            return control == null ? java.util.Optional.empty()
                    : emissionSiteOf(which).map(site ->
                            new ControlPointId.ComparisonPoint(control, site, result));
        }

        /**
         * Whether this plan numbered any site a run can be recorded at.
         *
         * <p>Not whether it numbered anything. A body whose every arm answers {@code unreachable}
         * has arms, and control points for them, and no site at all — so a reader asking this to
         * find out whether there is anything to be about would skip exactly the bodies a claim is
         * made in.
         */
        public boolean hasNoProbes() {
            return sites.isEmpty();
        }

        /** The probe numbers of {@code node}'s arms, in the order the emitter emits them, or null
         * where this node has none. An entry is {@link #NO_SITE} where that arm answers nothing.
         *
         * <p>Arms only, and positional. A comparison's site is not in here and must not be put in
         * here: the emitter and the readers index this array by an arm's place among its siblings,
         * and anything else in it lights a neighbour's probe. */
        public int[] probesOf(Core node) {
            return byNode.get(node);
        }

        /**
         * Where a comparison's value is recorded, for a comparison that has such a site.
         *
         * <p>Empty is an ordinary answer. What gets a site is decided from the shape of the code —
         * every comparison of a guard's condition — while whether a line is drawn on one is decided
         * later and from more: a comparison the partition reads nothing off keeps its site and nobody
         * asks about it. The emitter walks comparisons in both cases and asks this of each.
         */
        public java.util.Optional<ComparisonEmissionSite> emissionSiteOf(
                ComparisonOccurrence which) {
            return java.util.Optional.ofNullable(byComparison.get(ofThisPlan(which)));
        }

        /**
         * {@code which}, where it is a comparison this plan is about.
         *
         * <p>The one place a name from outside is turned away, because it is the one thing every
         * question below cannot tell for itself. A comparison this plan numbers no site for and one
         * belonging to another module both come back with no site, and they are not the same thing:
         * the first is an answer about this module and the second is a question that was never
         * about it. Answered alike, a reading of one module joins to another module's comparison
         * and says nothing about either — which is what naming an occurrence was for.
         */
        private ComparisonOccurrence ofThisPlan(ComparisonOccurrence which) {
            if (!comparisons.holds(which)) {
                throw new IllegalArgumentException(
                        "this plan is not about " + which + "; it holds "
                                + comparisons.all().size() + " comparisons of its own");
            }
            return which;
        }

        /**
         * Whether this plan instruments {@code which}, which is what a reader asking about runs
         * wants to know before it asks anything else.
         *
         * <p>Named here rather than spelled as "it has a site" wherever it is wanted. Every reader
         * that files something under a comparison a run could be observed at asks this, and four of
         * them working it out from the site each time are four places that can come to ask it
         * differently — which is the shape this whole reading was written against.
         */
        public boolean instruments(ComparisonOccurrence which) {
            return byComparison.containsKey(ofThisPlan(which));
        }

        /**
         * The same, where the caller's own construction says there is one.
         *
         * <p>A boundary is read off a comparison this plan numbers, so the site was planned before
         * the line was. Absent here is not a boundary that cannot be measured — it is this plan and
         * the reader that found the comparison disagreeing about which comparisons are instrumented,
         * which no measurement should paper over.
         */
        public ComparisonEmissionSite requireEmissionSiteOf(ComparisonOccurrence which) {
            return emissionSiteOf(which).orElseThrow(() -> new IllegalStateException(
                    "no comparison site was planned for " + which
                            + "; a line is read off a comparison this plan does not instrument"));
        }

        /** The arms of one behavior, which is what a branch measure counts. */
        public List<ArmSite> arms(String behavior) {
            return sites.stream()
                    .filter(site -> site.behavior().equals(behavior))
                    .filter(ArmSite.class::isInstance).map(ArmSite.class::cast)
                    .toList();
        }
    }

    /**
     * The sites of every behavior body in one module, numbered in the order the bodies are declared
     * and, within one, in the order the arms are written — the numbering being decided here.
     *
     * <p>The only way to a plan of a module, and asked in one place: the check that holds the
     * bodies asks it, keeps what comes back, and hands that to every reader. There is no second way
     * in that takes a numbering already issued and walks the bodies again to meet it — a plan is
     * filed by which {@code Core} objects were put in it, so one made anywhere but where those
     * objects are held is an index into a graph its holder is not the owner of.
     */
    public static Plan of(ModuleBodies of, DecisionSources decisions, SuppliedRules supplied) {
        Walked walked = walked(of, decisions, supplied);
        return asPlan(walked,
                walked.walk().numbering.finish(of.module(), walked.executable()));
    }

    /**
     * What one walk of a module's bodies found, before its numbers mean places.
     *
     * <p>Held apart from the plan because the two ways in differ in one step and share the rest.
     * Which step is the whole of what this change is about: whether the numbers this walk handed
     * out are what decides the numbering, or are held against a numbering already decided. Written
     * as a walk that takes what to do next, the step would be a call nothing reading the compiled
     * classes could see, and who may decide a numbering is held by reading them.
     */
    private record Walked(ComparisonCatalog comparisons, Walk walk,
                          Map<String, ExecutableIdentity> executable) { }

    private static Walked walked(ModuleBodies of, DecisionSources decisions,
                                 SuppliedRules supplied) {
        // Which comparisons there are is not this walk's to decide. Asked here and answered once,
        // so that what gets a number and what a line is drawn on are the same collection read twice
        // rather than two descents that happen to agree.
        ComparisonCatalog comparisons = ComparisonCatalog.of(of);
        Walk walk = new Walk(comparisons, decisions, supplied);
        Map<String, ExecutableIdentity> executable = new LinkedHashMap<>();
        for (Map.Entry<String, Core> body : of.bodies().entrySet()) {
            walk.behavior(body.getKey(), body.getValue());
            // What the body does, beside where its places are. A numbering is two numberings when
            // the numbers address different places, and it is two when the code at those places
            // does different things; neither half says the other.
            //
            // Off the addresses the walk just took, rather than taking them again: where a body's
            // places are is one answer, and a second descent for it would be a second.
            executable.put(body.getKey(), ExecutableIdentity.of(body.getValue(),
                    Binders.of(of.module(), walk.places)));
        }
        return new Walked(comparisons, walk, executable);
    }

    /**
     * What the walk found, with its numbers read back as places of {@code numbering}.
     *
     * <p>One direction and one moment: nothing before this could have made an address, and nothing
     * after it sees a number.
     */
    private static Plan asPlan(Walked found, SiteNumbering numbering) {
        ComparisonCatalog comparisons = found.comparisons();
        Walk walk = found.walk();
        // One occurrence per arm the walk made, whoever asks for it. A site of an arm and the arms
        // of its fork are the same place, and they are the same value: made twice, the two would be
        // two answers about one arm, and every reader below would be free to have either.
        IdentityHashMap<DraftArm, ControlPointId.ArmOccurrence> issued = new IdentityHashMap<>();
        List<Site> sites = new ArrayList<>();
        for (DraftSite draft : walk.sites) {
            sites.add(switch (draft) {
                case DraftArmSite arm -> new ArmSite(arm.behavior(), arm.outcome(),
                        occurrenceOf(arm.arm(), numbering, issued), arm.ordinal(),
                        arm.obligation());
                case DraftComparisonSite compared -> new ComparisonSite(compared.behavior(),
                        compared.outcome(), compared.at(),
                        numbering.comparison(compared.raw()), compared.ordinal(),
                        compared.obligation());
            });
        }
        List<GuardRef> guards = new ArrayList<>();
        for (DraftGuard draft : walk.guards) {
            guards.add(new GuardRef(draft.behavior(), draft.origin(), draft.decided(),
                    armAt(numbering, draft.whereThen()), armAt(numbering, draft.whereElse()),
                    draft.at()));
        }
        Map<ComparisonOccurrence, ComparisonEmissionSite> byComparison = new LinkedHashMap<>();
        walk.byComparison.forEach((which, raw) ->
                byComparison.put(which, numbering.comparison(raw)));
        IdentityHashMap<Core, ControlPointId.ArmOccurrence[]> armsByNode = new IdentityHashMap<>();
        walk.armsByNode.forEach((node, arms) -> {
            ControlPointId.ArmOccurrence[] here =
                    new ControlPointId.ArmOccurrence[arms.length];
            for (int i = 0; i < arms.length; i++) {
                here[i] = occurrenceOf(arms[i], numbering, issued);
            }
            armsByNode.put(node, here);
        });
        return new Plan(List.copyOf(sites), List.copyOf(guards), walk.byNode,
                byComparison, armsByNode, walk.controlByComparison, walk.mayRepeat,
                walk.forkByNode, comparisons, numbering);
    }

    /** The arm {@code raw} addresses, where an arm was numbered at all. */
    private static Optional<ArmProbe> armAt(SiteNumbering numbering, java.util.OptionalInt raw) {
        return raw.isPresent() ? Optional.of(numbering.arm(raw.getAsInt())) : Optional.empty();
    }

    /**
     * The place {@code draft} is, made once however many readers of it there are.
     *
     * <p>Kept by the draft's identity and not by what it holds. Two arms of one shape are two
     * places, and what is wanted here is the occurrence this draft became rather than the one some
     * draft equal to it did.
     */
    private static ControlPointId.ArmOccurrence occurrenceOf(
            DraftArm draft, SiteNumbering numbering,
            IdentityHashMap<DraftArm, ControlPointId.ArmOccurrence> issued) {
        return issued.computeIfAbsent(draft, each -> new ControlPointId.ArmOccurrence(
                each.controlId(), armAt(numbering, each.raw()), each.at(), each.origin()));
    }

    /**
     * One site as the walk has it, before there is a numbering for its number to be a place of.
     *
     * <p>The walk carries numbers because it is what makes the numbering: what a number means is
     * fixed by every place still to be reached and by what the bodies do, so no address of it can
     * exist until the walk is over. The numbers become places once, at {@link #of}, and the
     * places are what everything downstream is handed.
     *
     * <p>Two kinds and not one with the arm's half left optional. What a site of an arm has that a
     * site of a comparison has not is the arm, and a draft carrying one only sometimes would be a
     * draft whose readers ask whether it is there.
     *
     * <p>Nothing is declared here for the two to share. What is made of a draft is made in a switch
     * over which kind it is, so a name held in common would be a way of reading one without knowing
     * that — and the parts that go into a site are not the same parts.
     */
    private sealed interface DraftSite {
    }

    /**
     * A site of one arm, and the arm it is a site of.
     *
     * <p>The two are made in one act, where the arm is, and the arm is carried from it rather
     * than paired up afterwards by the number they share. A number is what the emitter records at,
     * and reading it back to find the place a site is about would be that correspondence worked out
     * again by whoever needed it.
     */
    private record DraftArmSite(DraftArm arm, String behavior, SourceOutcome.Arm outcome,
                                int ordinal, Obligation obligation) implements DraftSite {}

    /** A site of one comparison, which no arm stands at. */
    private record DraftComparisonSite(String behavior, SourceOutcome.Compared outcome, Citation at,
                                       int raw, int ordinal, Obligation obligation)
            implements DraftSite {}

    /** One arm as the walk has it: the control point it is, and the number its place was given
     *  where the emitter records one. */
    private record DraftArm(int controlId, java.util.OptionalInt raw, Citation at,
                            SourceConstructOrigin origin) {

        boolean isMeasured() {
            return raw.isPresent();
        }
    }

    /** The two arms of one {@code if} as the walk has them. */
    private record DraftGuard(String behavior, SourceConstructOrigin origin, DecidedBy decided,
                              java.util.OptionalInt whereThen, java.util.OptionalInt whereElse,
                              SourcePos at) {}

    private static final class Walk {

        private final List<DraftSite> sites = new ArrayList<>();
        /** What hands the numbers out, and says what each addresses in the same act. */
        private final SiteNumbering.Building numbering = SiteNumbering.begin();
        /** Where the places of the body being walked are. Made per body, since a path is a way down
         *  from one body's root and means a different place in every other. */
        private NodeAddresses places;
        private final List<DraftGuard> guards = new ArrayList<>();
        private final IdentityHashMap<Core, int[]> byNode = new IdentityHashMap<>();
        private final Map<ComparisonOccurrence, Integer> byComparison = new LinkedHashMap<>();
        private final IdentityHashMap<Core, DraftArm[]> armsByNode = new IdentityHashMap<>();
        private final IdentityHashMap<Core, ForkOccurrence> forkByNode = new IdentityHashMap<>();
        private final Map<ComparisonOccurrence, Integer> controlByComparison =
                new LinkedHashMap<>();
        /** The reading of the body being walked, which every question about a node in it is asked
         *  of. Rooted at the body because what a name reads is settled by what bound it. */
        private NormalReturn answering = NormalReturn.ofBody(null);
        /** The nodes one run can pass more than once. Kept by identity, like everything else here:
         *  two arms that look the same are equal records and this is about this one. */
        private final java.util.Set<Core> mayRepeat =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        /** Whether the walk is somewhere a run may come back to. Held rather than passed down
         *  because every node below such a place is one, which is what makes it a state of the walk
         *  and not a property of the call. */
        private boolean repeating;
        /** The outcomes that carry nothing of their own. What each of them means is settled with
         *  the construct beside it, so one instance stands for every occurrence. */
        private static final SourceOutcome.Arm HELD =
                new SourceOutcome.Held(new SourceOutcome.HeldBy.Condition());
        private static final SourceOutcome.Arm FAILED =
                new SourceOutcome.Failed(new SourceOutcome.FailedBy.Condition());
        private static final SourceOutcome.Arm BUILT =
                new SourceOutcome.Held(new SourceOutcome.HeldBy.Construction());
        /** Which nodes are comparisons, which this walk asks rather than reads off their shape. */
        private final ComparisonCatalog comparisons;
        private String behavior;
        private int ordinal;
        /** Numbered across the whole plan and never reused, so that one number names one place
         *  whichever behavior it is in — the same rule the probe numbers are under. */
        private int controls;

        private final DecisionSources decisions;
        private final SuppliedRules supplied;

        Walk(ComparisonCatalog comparisons, DecisionSources decisions, SuppliedRules supplied) {
            this.comparisons = comparisons;
            this.decisions = decisions;
            this.supplied = supplied;
        }

        void behavior(String name, Core body) {
            this.behavior = name;
            this.ordinal = 0;
            this.answering = NormalReturn.ofBody(body);
            // Where each place of this body is, worked out before anything is numbered: a number is
            // handed out for a place, and this is what says which place that is in something a
            // second walk of the same body also arrives at.
            this.places = NodeAddresses.of(name, body);
            walk(body, true);
        }

        /**
         * One arm, or {@link #NO_SITE} where no row that stands can be in it.
         *
         * <p>Three things have to hold. The arm has to be able to answer a value; it has to stand
         * somewhere a row can get to — an arm of a {@code match} written after a binding that aborts
         * is a fork nothing reaches, however ordinary the arm itself looks; and the condition above
         * it has to be able to come out its way, since a fork on something the reading settles to one
         * truth sends every run down one arm and leaves the other an arm nothing enters. A row that
         * gets as far as an {@code unreachable} is E1911 and states nothing, so an arm only such a
         * row could go through is an arm no row will ever be recorded in.
         */
        // The `unreachable` above is the language's own word, not this method's `reachable`.
        @SuppressWarnings("InvalidParam")
        private DraftArm armOf(SourceOutcome.Arm outcome, Core owner,
                               SourceConstructOrigin origin, int part, Core arm,
                               boolean reachable,
                               DecidedBy decided) {
            // The arm is made either way. Whether a run through it can be recorded is the second
            // question and only the probe turns on it — an arm nothing could record is still an arm,
            // and the readings that judge one need to be able to name it.
            //
            // The fork's own coordinate, as a site takes it: an arm's body is what lowering
            // rewrites and carries whatever position it was built from, so quoting it sends an
            // author somewhere else in the file.
            Citation at = Citation.of(owner.pos());
            if (!(reachable && answers(arm) && answering.mayEnter(owner, part))) {
                return new DraftArm(controls++, java.util.OptionalInt.empty(), at, origin);
            }
            // Asked before the place is numbered, so that a tree nothing wrote is refused for being
            // that rather than for whatever the numbering noticed about it first.
            written(origin, owner);
            DraftArm draft = new DraftArm(controls++,
                    java.util.OptionalInt.of(
                            numbering.number(new SiteAddress.Arm(places.of(owner), part))),
                    at, origin);
            // The site of the arm, holding the arm rather than the number they share. One act, so
            // there is no moment at which a site exists and which place it is about is still to be
            // worked out.
            sites.add(new DraftArmSite(draft, behavior, outcome, ordinal++,
                    new Obligation(behavior, origin, part, decided)));
            return draft;
        }

        /**
         * The arms of one fork, and the fork they are arms of.
         *
         * <p>Both written here, in one act. What names the fork is the first of its arms, and it is
         * a name rather than a lookup for exactly that reason: made apart, the fork's identity would
         * be something each reader worked out again from whatever component it had to hand.
         */
        private void arms(Core fork, DraftArm[] arms) {
            armsByNode.put(fork, arms);
            if (arms.length > 0) {
                forkByNode.put(fork, new ForkOccurrence(arms[0].controlId()));
            }
        }

        /** The probe numbers of {@code arms}, in their order, {@link #NO_SITE} where an arm has
         *  none. What the emitter indexes and what the branch measure counts. */
        private static int[] probesOf(DraftArm... arms) {
            int[] out = new int[arms.length];
            for (int i = 0; i < arms.length; i++) {
                out[i] = arms[i].raw().orElse(NO_SITE);
            }
            return out;
        }

        /**
         * Whether {@code e} answers a value, asked of the reading of the body it stands in.
         *
         * <p>The walk asks this of every node it passes and of every arm it makes, and the answer is
         * read off the whole subtree — so the reading settles every occurrence in the body once and
         * the walk asks it rather than reading a subtree again per node.
         */
        private boolean answers(Core e) {
            return answering.at(e);
        }

        private int comparisonSite(SourceOutcome.Compared outcome, Core owner,
                                   SourceConstructOrigin origin, DecidedBy decided) {
            written(origin, owner);
            int raw = numbering.number(new SiteAddress.Comparison(places.of(owner)));
            sites.add(new DraftComparisonSite(behavior, outcome, Citation.of(owner.pos()), raw,
                    ordinal++, new Obligation(behavior, origin, 0, decided)));
            return raw;
        }

        private void written(SourceConstructOrigin origin, Core owner) {
            // Said here because this is where anything is numbered, and the rule is about numbering
            // rather than about comparisons: an arm of a fork nothing wrote is as much a row nobody
            // can be owed as a comparison of one. Stated for the comparisons alone, it left the arms
            // to be refused further down by whatever noticed first — which was the pair check on
            // `Site`, saying something true about the pair and nothing about the tree.
            if (!origin.isWritten()) {
                throw new IllegalStateException("a construct with no source wrote it is being "
                        + "numbered at " + owner.pos()
                        + "; a tree rebuilt for an analysis is not the tree that runs");
            }
        }

        /**
         * Written out rather than reusing {@link Core#forEachChild}: what is being numbered is a
         * {@code Case} and an {@code ElseArm}, which are not {@code Core} and so are not children the
         * generic walk hands over. The switch is exhaustive on purpose — a node added to the IR should
         * stop here and be decided about, not fall silently into a default and go uncounted.
         *
         * <p>Every fork is registered in {@link Plan#byNode} whether or not it is numbered. The
         * emitter generates the bytecode of a body that aborts as it generates any other, and asks
         * this plan for the arms of each fork it meets while doing so; a fork the plan has no entry
         * for stops that generation, which is what makes an omission loud. What a fork nothing
         * reaches has is an entry of {@link #NO_SITE}s — the structure, without the obligation.
         *
         * @param reachable whether a row that stands can get this far. It cannot get past something
         *                  that aborts, so nothing below such a point is a fork a row takes: the row
         *                  that would take it is E1911 and states nothing
         */
        private void walk(Core e, boolean reachable) {
            // Everything below this node is reached by way of the node, so what the node cannot do
            // nothing inside it can do either.
            boolean inside = reachable && answers(e);
            if (repeating) {
                mayRepeat.add(e);
            }
            // The slots this node has, taken one at a time below. The walk decides something at
            // each of them and so descends them itself; what it may not also do is be a second
            // answer to which slots there are.
            CoreStructure.Children structural = CoreStructure.Children.of(e);
            switch (e) {
                case Core.Int _, Core.Decimal _, Core.Str _, Core.Bool _, Core.Temporal _,
                     Core.Read _, Core.UnitValue _, Core.OptionNone _ -> { }
                // A leaf, and one holding no fork. Whether the arm it stands in is an arm to cover is
                // decided where that arm is made, not here.
                case Core.Unreachable _ -> { }
                case Core.Neg n ->
                        walk(structural.take(new CoreStructure.Edge.NegOperand(), n.operand()),
                                inside);
                case Core.FieldAccess fa ->
                        walk(structural.take(new CoreStructure.Edge.FieldTarget(), fa.target()),
                                inside);
                case Core.Binary b -> {
                    number(b, inside);
                    walk(structural.take(new CoreStructure.Edge.BinaryLeft(), b.left()), inside);
                    walk(structural.take(new CoreStructure.Edge.BinaryRight(), b.right()), inside);
                }
                case Core.Call c -> {
                    for (int i = 0; i < c.args().size(); i++) {
                        walk(structural.take(new CoreStructure.Edge.CallArgument(i),
                                c.args().get(i)), inside);
                    }
                }
                // What a representation kept standing for an analysis to read. Coverage is measured
                // over the tree that runs, which keeps none of these, so reaching one would mean this
                // count was taken over a tree nothing executes.
                case Core.PreservedCall p -> throw p.unexpectedIn("coverage numbering");
                case Core.Apply a -> {
                    // The binding what is applied is loaded from. Taken and not gone into: it is a
                    // name, so it numbers nothing and has nothing under it to number.
                    structural.take(new CoreStructure.Edge.AppliedFunction(), a.fn());
                    for (int i = 0; i < a.args().size(); i++) {
                        walk(structural.take(new CoreStructure.Edge.ApplyArgument(i),
                                a.args().get(i)), inside);
                    }
                }
                case Core.LetIn li -> {
                    walk(structural.take(new CoreStructure.Edge.LetValue(), li.value()), inside);
                    walk(structural.take(new CoreStructure.Edge.LetBody(), li.body()), inside);
                }
                // A function value, and its arms are arms: what is written here runs when whatever
                // this is handed to applies it, and the rows that make that happen go through them.
                //
                // How many times it applies it is that caller's business and nothing here can say —
                // a comprehension applies one of these per element — so everything inside is a place
                // one run may come back to. Recorded rather than assumed anywhere else: what a set
                // of places can be asked turns on it.
                case Core.Block b -> {
                    boolean outside = repeating;
                    repeating = true;
                    walk(structural.take(new CoreStructure.Edge.BlockBody(), b.body()), inside);
                    repeating = outside;
                }
                case Core.ListLit lit -> {
                    for (int i = 0; i < lit.elements().size(); i++) {
                        walk(structural.take(new CoreStructure.Edge.ListElement(i),
                                lit.elements().get(i)), inside);
                    }
                }
                case Core.OptionSome s ->
                        walk(structural.take(new CoreStructure.Edge.SomeValue(), s.value()),
                                inside);
                case Core.Tuple t -> {
                    for (int i = 0; i < t.elements().size(); i++) {
                        walk(structural.take(new CoreStructure.Edge.TupleElement(i),
                                t.elements().get(i)), inside);
                    }
                }
                case Core.TupleGet tg ->
                        walk(structural.take(new CoreStructure.Edge.TupleSource(), tg.tuple()),
                                inside);
                case Core.Construct nd -> {
                    for (int i = 0; i < nd.values().size(); i++) {
                        walk(structural.take(new CoreStructure.Edge.FieldValue(i),
                                nd.values().get(i).value()), inside);
                    }
                }
                case Core.If iff -> {
                    walk(structural.take(new CoreStructure.Edge.IfCondition(), iff.cond()),
                            inside);
                    // Which rule this fork decides by, taken before its arms are numbered: two
                    // calls of one library combinator are one fork inlined twice and are not one
                    // thing to cover, and what tells them apart is the rule each was handed.
                    DecidedBy decided =
                            decidedAt(iff.origin(), iff.expansion(), decisions, supplied);
                    DraftArm then =
                            armOf(HELD, iff, iff.origin(), 0, iff.then(), inside, decided);
                    walk(structural.take(new CoreStructure.Edge.IfThen(), iff.then()), inside);
                    DraftArm els =
                            armOf(FAILED, iff, iff.origin(), 1, iff.els(), inside, decided);
                    walk(structural.take(new CoreStructure.Edge.IfElse(), iff.els()), inside);
                    byNode.put(iff, probesOf(then, els));
                    arms(iff, new DraftArm[] {then, els});
                    if (then.isMeasured() || els.isMeasured()) {
                        guards.add(new DraftGuard(behavior, iff.origin(), decided,
                                then.raw(), els.raw(), iff.pos()));
                    }
                }
                case Core.Match m -> {
                    walk(structural.take(new CoreStructure.Edge.MatchScrutinee(), m.scrutinee()),
                            inside);
                    // What a `match` decides by is its subject, as an `if` decides by its condition.
                    // A subject the caller's rule answered is a decision the caller made, and its
                    // arms are one obligation per rule handed in.
                    DecidedBy decided =
                            decidedAt(m.origin(), m.expansion(), decisions, supplied);
                    DraftArm[] arms = new DraftArm[m.cases().size()];
                    for (int i = 0; i < m.cases().size(); i++) {
                        Core.Case arm = m.cases().get(i);
                        arms[i] = armOf(matched(arm), m, m.origin(), i, arm.body(), inside,
                                decided);
                        walk(structural.take(new CoreStructure.Edge.MatchCase(i), arm.body()),
                                inside);
                    }
                    byNode.put(m, probesOf(arms));
                    arms(m, arms);
                }
                case Core.IfConstructed ic -> {
                    // The construction the attempt tests. Taken as the one slot it is, and gone
                    // into a field at a time: what is numbered is inside the fields, and the
                    // construction itself is where the attempt's own arms are made below.
                    structural.take(new CoreStructure.Edge.ConstructedAttempt(), ic.construct());
                    // And the construction's own slots, taken from the same place: this is where
                    // the walk goes two levels at once, so it is where the slots of the level it
                    // passes through would go unvisited with nothing to say so.
                    CoreStructure.Children built =
                            CoreStructure.Children.of(ic.construct());
                    for (int i = 0; i < ic.construct().values().size(); i++) {
                        walk(built.take(new CoreStructure.Edge.FieldValue(i),
                                ic.construct().values().get(i).value()), inside);
                    }
                    built.requireExhausted();
                    // And what an attempted construction decides by is the value it is given.
                    DecidedBy decided =
                            decidedAt(ic.origin(), ic.expansion(), decisions, supplied);
                    DraftArm[] arms = new DraftArm[1 + ic.els().size()];
                    arms[0] = armOf(BUILT, ic, ic.origin(), 0, ic.then(), inside, decided);
                    walk(structural.take(new CoreStructure.Edge.ConstructedThen(), ic.then()),
                            inside);
                    for (int i = 0; i < ic.els().size(); i++) {
                        Core.ElseArm arm = ic.els().get(i);
                        arms[i + 1] = armOf(refused(arm), ic, ic.origin(), i + 1,
                                arm.body(), inside, decided);
                        walk(structural.take(new CoreStructure.Edge.ConstructedElse(i),
                                arm.body()), inside);
                    }
                    byNode.put(ic, probesOf(arms));
                    arms(ic, arms);
                }
            }
            // That the walk went to every slot the node has. A node kind that grows a child stops
            // the switch above from compiling; a case here that forgot to descend one stops nothing,
            // and what it leaves is a place the numbering never reaches and nothing to say so.
            structural.requireExhausted();
        }

        /**
         * One comparison, numbered where a run through it could be recorded.
         *
         * <p>Which nodes are comparisons is {@link ComparisonCatalog}'s answer and not this walk's.
         * Read off the shape of the node here, the numbering came out as the comparisons a fork
         * happened to be written around: a comparison given a name a line above the fork that tests
         * it, or written inside a function value handed to a combinator, is the same construct and
         * got no number, so nothing that joins on one could name it.
         *
         * <p>Deliberately wider than what gets read. Which comparisons a line is drawn on takes the
         * behavior's parameters and the module's symbols to answer, and neither is here — nor should
         * be. A plan's numbering has to be a function of the bodies alone, because the emitter builds
         * one plan and a measurement builds another and the two are the same numbering or the probes
         * mean nothing. A comparison no boundary is drawn on keeps a site nobody asks about, which
         * costs two instructions in a measuring build.
         *
         * @param inside whether a row that stands can get this far and what the comparison stands in
         *               answers a value. A comparison behind an abort is one no run reaches, and a
         *               site for it would be one the emitter lights on no run
         */
        private void number(Core.Binary comparison, boolean inside) {
            // Which comparison this node is, asked of the catalog, which is where that is decided.
            // Numbered once. A node reached twice is one comparison written once, and a second number
            // for it would be a site the emitter never lights — which is the shape of a real omission
            // and would be reported as one.
            ComparisonOccurrence which = comparisons.occurrenceAt(comparison).orElse(null);
            if (!inside || which == null || byComparison.containsKey(which)) {
                return;
            }
            // Keyed on which comparison it is and not on what tests it. A condition can be an
            // application of a function parameter, and then the comparison is the caller's: two
            // predicates written separately are two lines, and one predicate handed to two calls is
            // one, neither of which a fork can say.
            byComparison.put(which,
                    comparisonSite(new SourceOutcome.Compared(comparison.op()), comparison,
                            comparison.origin(), DecidedBy.THE_DECLARATION));
            controlByComparison.put(which, controls++);
        }

        private static SourceOutcome.Arm matched(Core.Case arm) {
            return new SourceOutcome.Matched(arm.caseTypes());
        }

        private static SourceOutcome.Arm refused(Core.ElseArm arm) {
            return new SourceOutcome.Failed(
                    new SourceOutcome.FailedBy.Construction(arm.clause()));
        }
    }

    private CoverageSites() {}
}
