package souther.compiler.flow;

import souther.compiler.core.Core;
import souther.compiler.types.BindingId;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ways an expression arrives at a value, read off the body it is written in.
 *
 * <p>One reading for three questions that were being answered separately and had to agree: whether
 * an expression can be evaluated to a value at all, what that value is where it is a truth, and
 * under what conditions each way there holds.
 *
 * <p><b>Two halves, and only one of them has a naming in it.</b> {@link Comes} says what the body
 * does and is computed with no naming at all — it is what the reading with no numbering behind it
 * answers, and every reader asking whether a value arrives or what it comes to is answered from
 * there. {@link Paths} says what the ways are called, and it is the only half a naming touches: a
 * condition it has no words for leaves a way {@link Completeness#PARTIAL}, a pair of conditions it
 * sees settle one decision opposite ways leaves a way out, and more ways than it will hold apart
 * leave {@link Paths.Beyond}. None of those can move the other half, because the other half was
 * never computed with a naming to begin with.
 *
 * <p>Which is stronger than saying it. Before, the naming reached the answer three ways — a way it
 * could not name, a pair of ways it refused, a count it would not hold — and two of them were still
 * changing whether a value arrives. A reading of the numbering was deciding what the body does,
 * which is the thing this was made to end.
 *
 * <p><b>Rooted at a body.</b> What a name reads is not a fact about the node that reads it: the same
 * {@code Core.Read} is a position of the input in one body and a {@code let} away from a number
 * written out in another. So the body is read once, every occurrence in it is settled under what the
 * binders above it bound, and a caller asks about a node it already has.
 *
 * <p><b>An arrival is a value.</b> Two that say the same thing are one way, so a way found twice by a
 * reading that got there two ways is still one way, and anything counting these is counting what the
 * body does.
 */
public final class ValueArrivals<P> {

    private final Naming<P> naming;

    /**
     * The reading with no naming behind it, which is what this one's answers about the body are.
     *
     * <p>Null in that reading itself, where the question is not passed on because this is where it
     * stops: what a value arrives at is what this half says it arrives at.
     */
    private final ValueArrivals<AnonymousPath> semantics;

    /**
     * What each occurrence was settled as, keyed by identity.
     *
     * <p>Sound because a node occurs once, and what is in scope at it is what the reading had when
     * it got there — so the environment is a function of the position and not a second key.
     */
    private final IdentityHashMap<Core, Paths<P>> settled = new IdentityHashMap<>();

    /**
     * Which occurrences have been descended into.
     *
     * <p>Not the same as which have been settled, and held apart because running them together left
     * the reading short. Settling a node reads what it is made of, which is not everything written
     * inside it — a fork's arm the condition never comes out the way of is not read, and neither is
     * the body of a block. So an ancestor settling a node would have stopped the descent into it,
     * and the nodes below it would have been left with no answer while the claim that every
     * occurrence has one went on being made.
     */
    private final Set<Core> walked =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** Whether a call kept standing is a defect here or an operation the model names. */
    private final WhereTheOperationsAre operations;

    private ValueArrivals(Naming<P> naming, ValueArrivals<AnonymousPath> semantics,
                          WhereTheOperationsAre operations) {
        this.naming = naming;
        this.semantics = semantics;
        this.operations = operations;
    }

    /** The reading of {@code body} against what the body's own text says of its comparisons. */
    public static <P> ValueArrivals<P> ofBody(Core body, Naming<P> naming) {
        return ofBody(body, naming, ComparisonWays.OF_THE_TREE);
    }

    /** The same, of a tree the language's own operations stand in. */
    public static <P> ValueArrivals<P> ofBodyWhereTheOperationsStand(Core body, Naming<P> naming) {
        return ofBody(body, naming, ComparisonWays.OF_THE_TREE,
                WhereTheOperationsAre.STAND_IN_IT);
    }

    /**
     * The reading of {@code body}, with every occurrence in it settled under what binds it.
     *
     * <p>Both halves against the one {@code ways}. What the body does is the half computed with no
     * naming, so a reading whose halves were asked different things about a comparison would answer
     * one thing about what the body does and another about which ways it has.
     */
    public static <P> ValueArrivals<P> ofBody(Core body, Naming<P> naming, ComparisonWays ways) {
        return ofBody(body, naming, ways, WhereTheOperationsAre.ARE_EXPANDED_IN_IT);
    }

    /**
     * The same, of a tree that says whether the language's own operations stand in it.
     *
     * <p>Both halves against the one answer, for the reason both are against one {@code ways}: a
     * reading whose halves read a kept call differently would answer one thing about what the body
     * does and another about which ways it has.
     */
    public static <P> ValueArrivals<P> ofBody(Core body, Naming<P> naming, ComparisonWays ways,
                                              WhereTheOperationsAre operations) {
        ValueArrivals<AnonymousPath> semantics = naming == Anonymous.NAMING
                ? null : ofBody(body, Anonymous.NAMING, ways, operations);
        ValueArrivals<P> reading = new ValueArrivals<>(naming, semantics, operations);
        if (body != null) {
            reading.fill(body, naming, ways, Map.of());
        }
        return reading;
    }

    /**
     * The ways {@code e} arrives at a value, as far as this naming holds them apart.
     *
     * <p>Asked of a node this was rooted at, and it raises for one it was not. Reading such a node as
     * a body of its own would answer with every name in it free, which is a different question and
     * one whose answer can differ — so a caller holding a node from somewhere else would be told
     * something true about a body it is not asking about. Rooting a reading at that node is how to
     * ask it.
     */
    public Paths<P> waysAt(Core e) {
        if (e == null) {
            return new Paths.Held<>(List.of());
        }
        Paths<P> already = settled.get(e);
        if (already == null) {
            throw new IllegalArgumentException(
                    "this reading was not rooted at the body holding this "
                            + e.getClass().getSimpleName() + " at " + e.pos());
        }
        return already;
    }

    /** What the body does at {@code e}: whether a run arrives, and what it comes to. */
    public Comes comesAt(Core e) {
        if (e == null) {
            return Comes.NOWHERE;
        }
        if (semantics != null) {
            return semantics.comesAt(e);
        }
        Set<Truth> truths = EnumSet.noneOf(Truth.class);
        waysAt(e).orNone().forEach(each -> truths.add(each.value()));
        return new Comes(truths);
    }

    /** Whether {@code e} can be evaluated to a value. */
    public boolean arrivesAt(Core e) {
        return comesAt(e).arrives();
    }

    /**
     * The ways {@code e} is settled to {@code want}, or that this reading cannot enumerate them.
     *
     * <p>All of them or none. One arrival whose value is unread may be among the ways to either
     * truth, and one whose path the naming could not write down whole is a way nothing can be
     * steered along — either way no list of them is complete, and an incomplete list is worse here
     * than no list because whatever takes one reads the ways it does not hold as ways the body has
     * not got.
     *
     * <p>An empty list says the value is never settled that way, so it is only ever answered where
     * the reading of what the body does agrees. A naming that dropped every way to a truth the body
     * comes to has enumerated nothing, not established an absence.
     */
    public Ways<P> waysTo(Core e, boolean want) {
        if (!(waysAt(e) instanceof Paths.Held<P> held)) {
            return new Ways.Unknown<>();
        }
        List<P> paths = new ArrayList<>();
        for (Arrival<P> each : held.arrivals()) {
            if (each.value() == Truth.UNREAD || !each.isComplete()) {
                return new Ways.Unknown<>();
            }
            if (each.value() == Truth.of(want)) {
                paths.add(each.path());
            }
        }
        if (paths.isEmpty() && comesAt(e).mayCome(want)) {
            return new Ways.Unknown<>();
        }
        return new Ways.Known<>(paths);
    }

    // ---------------------------------------------------------------- filling

    /**
     * Settle {@code e} and everything written inside it, each under what binds it.
     *
     * <p>Into the parts as they are written and not as they are evaluated. A {@code Block} is a
     * function value and what it comes to is not read through, but its body is written here and a
     * caller may ask about a node in it — so it is settled too, under its own parameters, and the
     * answer for the block itself stays what it is.
     */
    private void fill(Core e, Naming<P> naming, ComparisonWays comparisons,
                            Map<BindingId, Bound<P>> bound) {
        if (e == null || !walked.add(e)) {
            return;
        }
        settle(e, naming, comparisons, bound);
        switch (e) {
            case Core.LetIn let -> {
                fill(let.value(), naming, comparisons, bound);
                fill(let.body(), naming.under(let.binder(), let.value()),
                        comparisons.under(let.binder(), let.value()),
                        with(bound, let.binder(),
                                settle(let.value(), naming, comparisons, bound), let.value()));
            }
            case Core.Block block -> {
                Map<BindingId, Bound<P>> inner = bound;
                for (Core.Binder param : block.params()) {
                    inner = with(inner, param, oneWay(), null);
                }
                fill(block.body(), naming, comparisons, inner);
            }
            case Core.Match match -> {
                fill(match.scrutinee(), naming, comparisons, bound);
                for (Core.Case arm : match.cases()) {
                    fill(arm.body(), naming.insideArm(match, arm),
                            comparisons.insideArm(match, arm),
                            with(bound, arm.binder(), oneWay(), null));
                }
            }
            case Core.IfConstructed constructed -> {
                fill(constructed.construct(), naming, comparisons, bound);
                fill(constructed.then(), naming, comparisons,
                        with(bound, constructed.binder(), oneWay(), null));
                constructed.els().forEach(arm -> fill(arm.body(), naming, comparisons, bound));
            }
            // Everything else through the enumeration the language keeps for itself. A list written
            // out here would be a copy of that one, agreeing with it until one of them changed — and
            // the two slots it had already stopped agreeing about are exactly the ones nothing here
            // could have noticed: a name a call applies, and the construction an attempt is of.
            default -> Core.forEachChild(e, child -> fill(child, naming, comparisons, bound));
        }
    }

    /**
     * {@code bound} widened by one binder, or itself where the binder is not there.
     *
     * <p>Two things about the name and not one. What it was settled to is what a reading of its value
     * takes; what it was written as is what a reading asking which position it is about takes, and a
     * name bound to nothing this can read — a parameter, an arm's binding — is a position in its own
     * right rather than a name with no answer.
     */
    private Map<BindingId, Bound<P>> with(Map<BindingId, Bound<P>> bound, Core.Binder binder,
                                          Paths<P> ways, Core settledBy) {
        if (binder == null) {
            return bound;
        }
        Map<BindingId, Bound<P>> inner = new java.util.HashMap<>(bound);
        inner.put(binder.binding(), new Bound<>(ways, settledBy));
        return Map.copyOf(inner);
    }

    /**
     * What a name in scope stands for: the ways its value arrives, and the expression it was settled
     * to where the body settled it to one.
     *
     * @param settledBy null for a name nothing here bound a value to, which is a position of whatever
     *                  the body is handed
     */    private record Bound<P>(Paths<P> ways, Core settledBy) { }

    // ---------------------------------------------------------------- reading

    private Paths<P> settle(Core e, Naming<P> naming, ComparisonWays comparisons,
                            Map<BindingId, Bound<P>> bound) {
        if (e == null) {
            // A body the checker refused a clause of arrives with a hole where the clause was.
            // Nothing is evaluated there and nothing arrives.
            return new Paths.Held<>(List.of());
        }
        Paths<P> already = settled.get(e);
        if (already != null) {
            return already;
        }
        Paths<P> answer = normalised(e, reading(e, naming, comparisons, bound));
        settled.put(e, answer);
        return answer;
    }

    /**
     * A list the naming emptied is one it enumerated nothing of, and not an absence.
     *
     * <p>Where every way out of a node settles a decision the way in settled the other way, what is
     * missing is this half following a correlation that the other half does not — the body arrives
     * all the same, and a list saying otherwise would be the naming answering a question that is not
     * its. Said at the one place a reading is answered rather than at each shape that can produce it.
     */
    private Paths<P> normalised(Core e, Paths<P> answer) {
        if (semantics == null) {
            // This reading is what arriving means, so there is nothing here to be held to.
            return answer;
        }
        return answer.orNone().isEmpty() && semantics.arrivesAt(e)
                ? new Paths.Beyond<>() : answer;
    }

    private Paths<P> reading(Core e, Naming<P> naming, ComparisonWays comparisons,
                            Map<BindingId, Bound<P>> bound) {
        return switch (e) {
            case Core.Unreachable ignored -> new Paths.Held<>(List.of());
            case Core.Bool literal -> one(Truth.of(literal.value()), whole(naming.nowhere()));
            case Core.Read read when bound.containsKey(read.binding()) ->
                    bound.get(read.binding()).ways();
            // A function value, not a body being run here. What happens when a call applies it is
            // that call's business, and a call is not read through either.
            case Core.Block ignored -> oneWay();
            // The value is evaluated before the body it binds is.
            case Core.LetIn let -> {
                Paths<P> value = settle(let.value(), naming, comparisons, bound);
                yield arrivesAt(let.value())
                        ? settle(let.body(), naming.under(let.binder(), let.value()),
                                comparisons.under(let.binder(), let.value()),
                                with(bound, let.binder(), value, let.value()))
                        : new Paths.Held<>(List.of());
            }
            case Core.Binary binary when binary.op().stopsWhenItsAnswerIsSettled() ->
                    through(binary, naming, comparisons, bound);
            case Core.If iff -> fork(iff, naming, comparisons, bound);
            case Core.Match match -> arms(match, naming, comparisons, bound);
            case Core.IfConstructed constructed -> attempted(constructed, naming, comparisons, bound);
            // A call kept standing where the operations are expanded is one this compiler failed to
            // expand, and is refused. Where they stand, it is the model naming an operation: its
            // arguments are evaluated and what it answers is a value with no truth this reading
            // says, which is what a value made of several already comes to.
            case Core.PreservedCall preserved -> {
                if (operations == WhereTheOperationsAre.ARE_EXPANDED_IN_IT) {
                    throw preserved.unexpectedIn("value-flow analysis");
                }
                yield built(e, naming, comparisons, bound);
            }
            default -> built(e, naming, comparisons, bound);
        };
    }

    /**
     * A value made out of several, settled every way its parts are settled together.
     *
     * <p>One rule and not a case each: what it does not cover is exactly what forks, what stops
     * early, what names, and what comes out one of two ways against another value. A part that
     * arrives at no value leaves the whole arriving at none, which is what makes this the reading of
     * everything strict as well.
     */
    private Paths<P> built(Core e, Naming<P> naming, ComparisonWays comparisons,
                            Map<BindingId, Bound<P>> bound) {
        Paths<P> out = oneWay();
        for (Core part : partsOf(e)) {
            Paths<P> each = settle(part, naming, comparisons, bound);
            if (!arrivesAt(part)) {
                return new Paths.Held<>(List.of());
            }
            out = product(out, each, naming);
            if (out instanceof Paths.Beyond) {
                return out;
            }
        }
        if (out.orNone().size() == 1) {
            Paths<P> settledTwoWays = comparedOut(e, out.orNone().get(0), naming, comparisons,
                    read -> {
                        Bound<P> named = bound.get(read.binding());
                        return named == null ? null : named.settledBy();
                    });
            if (settledTwoWays != null) {
                return settledTwoWays;
            }
        }
        return out;
    }

    /**
     * A value split into the ways it comes out, or null where none of them is witnessed.
     *
     * <p>Asked one way at a time. There is no rule here that a comparison comes out both ways: each
     * way is answered for on its own, by whether a value of what is compared brings it out that way,
     * and a way nothing stands behind is not among these. That is what keeps {@code 1 > 2} and
     * {@code a == a} from being read as values that vary.
     *
     * <p>Asked where nothing under the value was already saying it varies, so what is read here is
     * added to the reading and nothing is taken out of it.
     */
    private Paths<P> comparedOut(Core value, Arrival<P> under, Naming<P> naming,
                                 ComparisonWays comparisons,
                                 java.util.function.Function<Core.Read, Core> settledBy) {
        boolean holds = comparisons.comesOut(value, true, settledBy);
        boolean fails = comparisons.comesOut(value, false, settledBy);
        if (!holds && !fails) {
            return null;
        }
        Gathered out = new Gathered();
        if (holds) {
            side(value, true, under, naming, out);
        }
        if (fails) {
            side(value, false, under, naming, out);
        }
        return out.none() ? null : out.paths();
    }

    private void side(Core value, boolean held, Arrival<P> under, Naming<P> naming, Gathered out) {
        P named = naming.side(value, held);
        if (named == null) {
            // The way is there and this naming has no words for it.
            out.add(new Arrival<>(Truth.of(held), under.provenance().partial()));
            return;
        }
        P both = naming.join(under.path(), named);
        if (both == null) {
            return;
        }
        out.add(new Arrival<>(Truth.of(held),
                new Provenance<>(both, under.provenance().completeness())));
    }

    /**
     * The ways to the value of an operator that stops as soon as its answer is settled.
     *
     * <p>Not a product of the two sides. {@code &&} comes to false wherever the left does and never
     * looks at the right, and comes to whatever the right does wherever the left went through — so
     * the ways are the left's settling ones as they stand, and the left's going-through ones each
     * extended by every way of the right.
     *
     * <p>A left way this reading cannot value is not one of either. Whether the right ran at all is
     * what the left's value would have said, so the way arrives where the right does and nowhere
     * this reading can point at otherwise: where the right arrives at no value, claiming this way
     * arrives would be reading "I cannot say which" as "it comes out the way that stops here".
     */
    private Paths<P> through(Core.Binary binary, Naming<P> naming, ComparisonWays comparisons,
                             Map<BindingId, Bound<P>> bound) {
        Truth goesOn = Truth.of(binary.op().rightRunsWhenLeftIs());
        Paths<P> left = settle(binary.left(), naming, comparisons, bound);
        Paths<P> right = settle(binary.right(), naming, comparisons, bound);
        if (left instanceof Paths.Beyond) {
            return left;
        }
        boolean rightArrives = arrivesAt(binary.right());
        Gathered out = new Gathered();
        for (Arrival<P> each : left.orNone()) {
            if (each.value() == goesOn) {
                if (right instanceof Paths.Beyond) {
                    return right;
                }
                out.under(each.provenance(), right.orNone(), naming);
            } else if (each.value() == Truth.UNREAD) {
                if (rightArrives) {
                    out.add(each);
                }
            } else {
                out.add(each);
            }
            if (out.isBeyond()) {
                return out.paths();
            }
        }
        return out.paths();
    }

    private Paths<P> fork(Core.If iff, Naming<P> naming, ComparisonWays comparisons,
                            Map<BindingId, Bound<P>> bound) {
        // Numbered where they are written and not where they survive: the arm a fork answers on is
        // its place among the arms, and one that answers nothing is skipped rather than closing the
        // gap and letting the next arm be called the first.
        Core[] arms = {iff.then(), iff.els()};
        settle(iff.cond(), naming, comparisons, bound);
        Comes cond = comesAt(iff.cond());
        Gathered out = new Gathered();
        for (int part = 0; part < arms.length; part++) {
            boolean want = part == 0;
            if (!cond.mayCome(want)) {
                continue;
            }
            Paths<P> body = settle(arms[part], naming, comparisons, bound);
            if (!arrivesAt(arms[part])) {
                continue;
            }
            if (body instanceof Paths.Beyond) {
                return body;
            }
            for (Provenance<P> way : waysIn(iff, part, want, naming)) {
                out.under(way, body.orNone(), naming);
                if (out.isBeyond()) {
                    return out.paths();
                }
            }
        }
        return out.paths();
    }

    /**
     * The ways into arm {@code part}, as the condition's ways of coming out that way where they can
     * all be written down, and as the arm itself where they cannot.
     *
     * <p>Which arms there are is settled before this, off what the condition comes out as. This
     * settles only how a way into one is written, so a naming with no words for the fork leaves the
     * way {@link Completeness#PARTIAL} and takes no arm away.
     */
    private List<Provenance<P>> waysIn(Core.If iff, int part, boolean want, Naming<P> naming) {
        if (waysTo(iff.cond(), want) instanceof Ways.Known<P> known && !known.paths().isEmpty()) {
            return known.paths().stream().map(this::whole).toList();
        }
        return List.of(armWay(iff, part, naming));
    }

    /** The arm itself as the one way in, for a condition whose ways cannot all be written down. */
    private Provenance<P> armWay(Core fork, int part, Naming<P> naming) {
        P named = naming.forkArm(fork, part);
        return named == null
                ? new Provenance<>(naming.nowhere(), Completeness.PARTIAL) : whole(named);
    }

    private Paths<P> arms(Core.Match match, Naming<P> naming, ComparisonWays comparisons,
                            Map<BindingId, Bound<P>> bound) {
        settle(match.scrutinee(), naming, comparisons, bound);
        if (!arrivesAt(match.scrutinee())) {
            return new Paths.Held<>(List.of());
        }
        Gathered out = new Gathered();
        for (int part = 0; part < match.cases().size(); part++) {
            Core.Case arm = match.cases().get(part);
            Paths<P> body =
                    settle(arm.body(), naming.insideArm(match, arm),
                            comparisons.insideArm(match, arm),
                            with(bound, arm.binder(), oneWay(), null));
            if (!arrivesAt(arm.body())) {
                continue;
            }
            if (body instanceof Paths.Beyond) {
                return body;
            }
            P named = naming.matchCase(match, part);
            Provenance<P> way = named == null
                    ? new Provenance<>(naming.nowhere(), Completeness.PARTIAL) : whole(named);
            out.under(way, body.orNone(), naming);
            if (out.isBeyond()) {
                return out.paths();
            }
        }
        return out.paths();
    }

    private Paths<P> attempted(Core.IfConstructed constructed, Naming<P> naming,
                               ComparisonWays comparisons, Map<BindingId, Bound<P>> bound) {
        for (Core.FieldValue given : constructed.construct().values()) {
            settle(given.value(), naming, comparisons, bound);
            if (!arrivesAt(given.value())) {
                return new Paths.Held<>(List.of());
            }
        }
        List<Core> arms = new ArrayList<>();
        arms.add(constructed.then());
        constructed.els().forEach(arm -> arms.add(arm.body()));
        Gathered out = new Gathered();
        for (int part = 0; part < arms.size(); part++) {
            Map<BindingId, Bound<P>> inner =
                    part == 0 ? with(bound, constructed.binder(), oneWay(), null) : bound;
            Paths<P> body = settle(arms.get(part), naming, comparisons, inner);
            if (!arrivesAt(arms.get(part))) {
                continue;
            }
            if (body instanceof Paths.Beyond) {
                return body;
            }
            out.under(armWay(constructed, part, naming), body.orNone(), naming);
            if (out.isBeyond()) {
                return out.paths();
            }
        }
        return out.paths();
    }

    // ------------------------------------------------------------ putting ways together

    /**
     * The ways as they are being put together, which stops being a list the moment there are too
     * many of them.
     *
     * <p>Where the bound lives, and it lives here because it is a rule about the work and not a
     * property of the answer. Checked after the fact it stops nothing: fifteen independently forked
     * parts of one value have thirty-two thousand combinations, and a reading that builds them all
     * and then says it will not hold them apart has already done what the bound was written to
     * prevent. So nothing here can be added past it — every rule puts its ways in through this, and
     * once there are too many the list is gone and further adds do nothing.
     *
     * <p>{@link Comes} is no part of this. What the body does goes on being read exactly, and what
     * stops is the enumeration of the ways to it.
     */
    private final class Gathered {

        private final List<Arrival<P>> ways = new ArrayList<>();
        private boolean beyond;

        boolean isBeyond() {
            return beyond;
        }

        boolean none() {
            return !beyond && ways.isEmpty();
        }

        /** One way, where it is not one this already holds and there is still room for it. */
        void add(Arrival<P> way) {
            if (beyond || ways.contains(way)) {
                return;
            }
            if (ways.size() >= naming.mostArrivals()) {
                beyond = true;
                ways.clear();
                return;
            }
            ways.add(way);
        }

        /** Each arrival held to what already holds along the way to it. */
        void under(Provenance<P> holds, List<Arrival<P>> arrivals, Naming<P> naming) {
            for (Arrival<P> each : arrivals) {
                if (beyond) {
                    return;
                }
                P both = naming.join(holds.path(), each.path());
                if (both != null) {
                    add(new Arrival<>(each.value(), new Provenance<>(both,
                            holds.completeness().and(each.provenance().completeness()))));
                }
            }
        }

        Paths<P> paths() {
            return beyond ? new Paths.Beyond<>() : new Paths.Held<>(ways);
        }
    }

    /**
     * Every way the two can be settled together, which is not every pairing of them.
     *
     * <p>A binding read twice is one decision read twice, and pairing its ways without asking
     * whether the two agree would report a value settled nine ways that is settled three.
     *
     * <p>What the parts of a value come to is not what the value comes to. A thing built out of
     * several is the constructor's answer and no way to it carries a truth of its own, so these
     * arrive with nothing said about which of the two they are.
     *
     * <p>A side this reading will not hold the ways of leaves the product one it will not hold
     * either, and it is answered before the pairing is taken rather than after.
     */
    private Paths<P> product(Paths<P> left, Paths<P> right, Naming<P> naming) {
        if (left instanceof Paths.Beyond || right instanceof Paths.Beyond) {
            return new Paths.Beyond<>();
        }
        Gathered out = new Gathered();
        for (Arrival<P> one : left.orNone()) {
            for (Arrival<P> other : right.orNone()) {
                P both = naming.join(one.path(), other.path());
                if (both != null) {
                    out.add(new Arrival<>(Truth.UNREAD, new Provenance<>(both,
                            one.provenance().completeness()
                                    .and(other.provenance().completeness()))));
                }
                if (out.isBeyond()) {
                    return out.paths();
                }
            }
        }
        return out.paths();
    }

    /** What a value nothing forks is settled by, which is the same thing however it is written. */
    private Paths<P> oneWay() {
        return one(Truth.UNREAD, whole(naming.nowhere()));
    }

    private Paths<P> one(Truth value, Provenance<P> by) {
        return new Paths.Held<>(List.of(new Arrival<>(value, by)));
    }

    private Provenance<P> whole(P path) {
        return new Provenance<>(path, Completeness.COMPLETE);
    }

    // ------------------------------------------------------------------- the tree

    /**
     * Every value this node is built out of, in the order it is written.
     *
     * <p>Exhaustive and with no fallback. A node kind added to the IR should stop here and be
     * decided about rather than fall in with the ones every part of which is evaluated whenever the
     * node is.
     */
    private static List<Core> partsOf(Core node) {
        return switch (node) {
            case Core.Int ignored -> List.of();
            case Core.Decimal ignored -> List.of();
            case Core.Str ignored -> List.of();
            case Core.Bool ignored -> List.of();
            case Core.Temporal ignored -> List.of();
            case Core.Read ignored -> List.of();
            case Core.UnitValue ignored -> List.of();
            case Core.OptionNone ignored -> List.of();
            case Core.Unreachable ignored -> List.of();
            case Core.Neg neg -> present(neg.operand());
            case Core.FieldAccess access -> present(access.target());
            case Core.TupleGet get -> present(get.tuple());
            case Core.OptionSome option -> present(option.value());
            case Core.Binary binary -> present(binary.left(), binary.right());
            // The callee's own body is not read; its arguments are evaluated before it is reached.
            case Core.Call call -> call.args();
            case Core.PreservedCall call -> call.args();
            // What is applied is a name holding a function, which is loaded and not run here.
            case Core.Apply apply -> apply.args();
            case Core.ListLit list -> list.elements();
            case Core.Tuple tuple -> tuple.elements();
            case Core.Construct construct ->
                    construct.values().stream().map(Core.FieldValue::value).toList();
            case Core.If iff -> present(iff.cond(), iff.then(), iff.els());
            case Core.LetIn let -> present(let.value(), let.body());
            // Not the body. Evaluating this makes the function.
            case Core.Block ignored -> List.of();
            case Core.Match match -> {
                List<Core> out = new ArrayList<>();
                out.add(match.scrutinee());
                match.cases().forEach(each -> out.add(each.body()));
                yield out;
            }
            case Core.IfConstructed constructed -> {
                List<Core> out = new ArrayList<>();
                out.add(constructed.construct());
                out.add(constructed.then());
                constructed.els().forEach(arm -> out.add(arm.body()));
                yield out;
            }
        };
    }

    /**
     * The parts that are there.
     *
     * <p>A body the checker refused a clause of arrives with a hole where the clause was, and a
     * reading that is not the checker has nothing to say about it. Used where the parts are read
     * together and not where they are numbered — an arm's place among the arms is what says which
     * way the fork came out, and a list with the missing one taken out would call the second arm the
     * first.
     */
    private static List<Core> present(Core... parts) {
        List<Core> out = new ArrayList<>();
        for (Core each : parts) {
            if (each != null) {
                out.add(each);
            }
        }
        return out;
    }
}
