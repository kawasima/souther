package souther.compiler.check;

import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.NumberingIdentity;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.inputs.Admits;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.PathResolution;
import souther.compiler.inputs.Position;
import souther.compiler.inputs.PositionBounds;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.reach.PathDecision;
import souther.compiler.reach.Proof;
import souther.compiler.reach.Reachability;
import souther.compiler.reach.Witness;
import souther.compiler.reach.WhyUnsettled;
import souther.compiler.types.BindingId;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the model's own rules say arrives at each place in a behavior's body.
 *
 * <p>The reading the invariant-discharge check already makes, answered as a fact instead of
 * consumed as a step. That check threads what holds where it stands and returns at a place the
 * conditions cannot all hold — which is exactly the question every measure asks about an arm, and
 * every measure was answering it again from the declarations alone, with no way to know what the
 * guards above had established. Two guards on one position left the second one's departure owed a
 * row that nothing can write.
 *
 * <p>Held by {@link ControlPlace} and not by probe number. An arm answering {@code unreachable}
 * has no probe, and it is the arm a claim is about.
 *
 * <p><b>Only a proof excludes, and nothing here proves that something arrives.</b> A state the
 * domains found no contradiction in is a state they had nothing to say about, so this answers
 * {@link Reachability.Unsettled} there rather than {@link Reachability.Reachable} — what earns the
 * latter is a run, or a rule that settles the question completely, and neither is read here.
 *
 * <p>A place this walk did not get to is absent, which {@link Answers#at} reads as unsettled. That
 * is the fail-open direction and it is the one that costs nothing: an obligation stays owed and
 * nothing is reported.
 */
public final class PathReachability {

    /**
     * What was found, and what a place nothing was found about comes to.
     *
     * @param numbering which plan's places these are filed under, or empty where no reading was
     *                  made at all. Not read off the entries: a reading of a body with nothing to
     *                  say about it is a reading and answers for that plan, while what a caller
     *                  holds where a module's reading could not be made is no reading and answers
     *                  for none. The two are the same map and are not the same fact
     */
    public record Answers(Optional<NumberingIdentity> numbering,
                          Map<ControlPlace, Reachability> found,
                          Map<ConstructOccurrence,
                                  souther.compiler.reach.ComparisonArrival> arriving) {

        /** No reading, which every consumer takes the way it takes a place nothing was found
         *  about, and which goes with any plan because it says nothing about one. */
        public static final Answers NONE = new Answers(Optional.empty(), Map.of(), Map.of());

        public Answers {
            if (numbering == null) {
                throw new IllegalArgumentException(
                        "a reading either was made under some numbering or was not made");
            }
            if (numbering.isEmpty() && !(found.isEmpty() && arriving.isEmpty())) {
                throw new IllegalArgumentException(
                        "a reading that answered about " + found.size() + " places and "
                                + arriving.size() + " comparisons was made under some numbering;"
                                + " its places are of a plan and nothing here would say which");
            }
            // The order the walk found them in. `Map.copyOf` is unordered and its iteration is
            // salted per run, so the warnings read off this came out in a different order on every
            // JVM — a diagnostic whose place in the output is not a function of the source.
            found = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(found));
            arriving = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(arriving));
        }

        /**
         * That the places {@code of} names are the places this reading answered about.
         *
         * <p><b>Said once, where a plan and a reading of it are put together, and not at each
         * lookup.</b> Absent here reads as unsettled and that is the right answer for a place the
         * walk did not get to — and the same answer for a place of another module's plan, which is
         * not an answer about this reading at all. A place cannot be asked which plan it is of: an
         * arm no run can be recorded in carries no numbering, and giving it one would put the
         * plan's own address back inside the identity. So the pairing is what is checked, and it is
         * checked where the two halves meet.
         *
         * <p>Beside {@code AlignedObservation}, which refuses a place of another numbering put to a
         * recording. Both are read back under a numbering, and now both say so.
         *
         * <p>{@link #NONE} goes with any plan. What a caller holds where no reading was made says
         * nothing about any module's places, and every consumer already treats it the way it treats
         * a place nothing was found about.
         */
        public void requireNumbering(NumberingIdentity of) {
            if (numbering.isPresent() && !numbering.get().equals(of)) {
                throw new IllegalArgumentException("this reading was made under " + numbering.get()
                        + " and is being read against " + of
                        + "; what one plan's reading says about another's places is nothing, and"
                        + " answering it would report every place of a body as one nothing"
                        + " reached");
            }
        }

        /**
         * What arrives at {@code where}.
         *
         * <p>A place this reading has nothing filed under is one the walk did not get to, which is
         * an answer and not a gap: it is {@link WhyUnsettled.TheWalkDidNotReachIt}, and every
         * consumer treats it as it treats any other unsettled place. Which is why the reading and
         * the plan are held to being one another's first ({@link #requireNumbering}).
         *
         * <p><b>Asked with the place and never with an address of one.</b> A probe says where a run
         * through an arm is recorded, and which arm that is is the numbering's answer, given where
         * the number was handed out. Asked here, it would be that correspondence worked out again
         * by search — over the entries of one reading, which knows of no numbering and could not
         * tell two arms answering to one probe apart if a walk ever made them.
         */
        public Reachability at(ControlPlace where) {
            Reachability answer = found.get(where);
            return answer != null ? answer
                    : new Reachability.Unsettled(WhyUnsettled.theWalkDidNotReachIt());
        }

        /**
         * What arrives at {@code at} — one comparison, before it is taken either way.
         *
         * <p>An entry's absence reads as {@link souther.compiler.reach.ComparisonArrival
         * .NoProjection}, which restricts nothing. Two ways to have none, and one answer for both:
         * this reading was never made at all — the caller holds {@link #NONE}, which is what a
         * reader of a comparison outside a measured body is given — or the walk fell over before
         * finishing, since a walk that finished is held to an entry per numbered comparison
         * ({@link PathReachability#unanswered}). A partial reading is owed the fail-open answer,
         * the same one every other consumer of one gets.
         *
         * <p>What is not among them is a comparison this plan numbers no site for. There is no
         * occurrence to ask about then, and a caller that has one has it from the plan — asked with
         * something worked out another way, an absence here would be this reading and the plan
         * disagreeing, dressed as a fact about the model.
         */
        public souther.compiler.reach.ComparisonArrival arrivalAt(
                ConstructOccurrence at) {
            souther.compiler.reach.ComparisonArrival answer = arriving.get(at);
            return answer != null ? answer
                    : new souther.compiler.reach.ComparisonArrival.NoProjection();
        }

        /**
         * These answers with what the rows actually did taken in, and the proofs they took back.
         *
         * <p>A row that went through an arm went through it. So a run is a witness — the plainest
         * of them — and it settles an arm this reading had proven nothing arrives at: what happened
         * happened, and the proof was wrong rather than the row. Both come out of one act because
         * neither is derivable from the other afterwards: the corrected answers no longer say which
         * arms were corrected, and the corrections do not say what everything else came to.
         *
         * @param lit the probes a row was recorded at, which are places of the numbering this
         *            reading was made under. A probe of another is one no arm here answers to, so
         *            the run would correct nothing and every proof would stand — the same answer as
         *            a run that went nowhere
         */
        public AsRun asRunWith(java.util.Set<ArmProbe> lit) {
            lit.forEach(probe -> requireNumbering(probe.numbering()));
            Map<ControlPlace, Reachability> out = new LinkedHashMap<>(found);
            java.util.Set<ArmProbe> provedWrong =
                    new java.util.LinkedHashSet<>();
            found.forEach((where, said) -> {
                if (!(where instanceof ControlPlace.Arm arm)
                        || arm.probe().isEmpty() || !lit.contains(arm.probe().get())) {
                    return;
                }
                if (said instanceof Reachability.Unreachable) {
                    provedWrong.add(arm.probe().get());
                }
                out.put(where, new Reachability.Reachable(
                        Witness.aRunWentThrough(arm.probe().get())));
            });
            // The arrivals as they were: a run corrects what an arm's denominator counts, and the
            // geometry the arrivals decide was settled from the model before any row ran.
            return new AsRun(new Answers(numbering, out, arriving), provedWrong);
        }

        /**
         * What arrives once the rows have run, and where this reading was shown wrong.
         *
         * @param provedWrong arms proven unreachable that a row went through anyway. Nothing about
         *                    the model is wrong then — this reading is — and a measure says so
         *                    rather than quietly counting the arm again
         */
        public record AsRun(Answers answers,
                            java.util.Set<ArmProbe> provedWrong) {

            public AsRun {
                provedWrong = java.util.Set.copyOf(provedWrong);
            }
        }

        /** Whether this reading proved nothing arrives anywhere, which is what a walk that would
         *  only ever answer "keep everything" can skip on. */
        public boolean provesNothingUnreached() {
            return found.values().stream().noneMatch(Reachability.Unreachable.class::isInstance);
        }
    }

    /**
     * The places of one behavior's body, read against what its declared inputs guarantee.
     *
     * <p>The bindings are the implementation's own and the types are the declaration's, paired by
     * position and stopping at the business parameters — which is the rule the check that types the
     * body reads them by, asked here rather than worked out a second way. A trailing parameter
     * stands for a behavior this one depends on and guarantees nothing about a value.
     *
     * <p><b>The reading is required.</b> What a caller has where a module's could not be made is no
     * reading, and a walk of a body against one made up says about this compilation having stopped
     * what it would say about the model. A caller without one measures nothing here.
     */
    public static Answers of(Core body, ReadingPolicy policy,
                             SpecImplementation.Implemented implemented,
                             CoverageSites.Plan plan, InputDomain read, RuleReadingSource source) {
        Objects.requireNonNull(read, "a reachability reading is made against an input that was read");
        if (implemented == null) {
            return Answers.NONE;
        }
        // Which binder each declared input arrives in, asked of the reading that divides an
        // implementation's parameters. A behavior takes the behaviors it depends on beside its
        // inputs, so which of the binders are the inputs is that reading's and not a prefix
        // measured here.
        Scope params = Scope.NONE;
        for (SpecImplementation.ParameterBinding.AnInput input : implemented.declaredInputs()) {
            params = params.with(input.written().binder(),
                    TypeOps.successType(input.declared().type()));
        }
        return of(body, params, plan, read, source, policy);
    }

    /**
     * The same, told the bindings outright.
     *
     * <p>Fail-open throughout. A condition of a shape the rules have no word for narrows nothing,
     * so the arms under it come out unsettled rather than proven either way; a walk that falls over
     * answers about what it had reached and no more.
     */
    public static Answers of(Core body, Scope params, CoverageSites.Plan plan, InputDomain read,
                             RuleReadingSource source, ReadingPolicy policy) {
        Objects.requireNonNull(read, "a reachability reading is made against an input that was read");
        if (body == null) {
            return Answers.NONE;
        }
        PathEngine engine = new PathEngine(source, Terms.Of.THE_TREE_THAT_RUNS, policy);
        Map<ControlPlace, Reachability> out = new LinkedHashMap<>();
        Map<ConstructOccurrence,
                souther.compiler.reach.ComparisonArrival> arriving = new LinkedHashMap<>();
        PathEngine.Entered in = PathEngine.Entered.nothing();
        for (Map.Entry<BindingId, Scope.Binding> p : params.bindings().entrySet()) {
            in = engine.enter(new Core.Read(p.getValue().name(), p.getKey(),
                    p.getValue().type(), body.pos()), in.known(), in.at());
        }
        PathReachability reading =
                new PathReachability(engine, plan, read, source.symbols(), out, arriving);
        reading.entry = in.known();
        reading.entered = in.at();
        reading.walk(body, in.known(), in.at(),
                        InputReads.ofParameters(read.parameterReads(), ElementBindings.NONE),
                        List.of(), true);
        // A walk that ran to the end and made none of the answers it is written to produce. Its own
        // limit and said as one: the analysis this borrows is open about what it reads, so a
        // comparison it reached and settled nothing about leaves the obligation standing. A failure
        // of the walk itself is not this and is not caught — it is this compiler's.
        Optional<String> unmade = unanswered(body, plan, out, arriving);
        if (unmade.isPresent()) {
            InvariantChecker.gaveUp("reachability",
                    WhatTheCheckCannotRead.theWalkLeftAnAnswerUnmade(unmade.get()));
        }
        return new Answers(Optional.of(plan.identity()), out, arriving);
    }

    /**
     * Which comparison {@code node} is, where this plan instruments it, or null.
     *
     * <p>Two questions and two answers, asked of whichever holds each. Which comparison a node is,
     * the catalog says, for every comparison the bodies hold; whether a run through it is written
     * down anywhere, the plan says, for the ones it numbered. What is filed under one of these is
     * about a place a run can be observed at, so both have to answer.
     */
    private static ConstructOccurrence numbered(Core.Binary node, CoverageSites.Plan plan) {
        return plan.comparisons().occurrenceAt(node).filter(plan::instruments).orElse(null);
    }

    /**
     * That the walk answered for every comparison the plan numbered in this body.
     *
     * <p>What the reading owes, checked rather than left to the shape of the walk. Absent and
     * unsettled are the same answer to every reader below — a line is dropped only by a proof — so a
     * comparison the walk never reached is a claim nobody can find missing. The walk stops at a
     * place nothing arrives at, and a node it stops at can stand over comparisons: which ones
     * depends on how a condition was bracketed, which is not a thing to keep in step by reading.
     *
     * <p>Only where the walk finished, and reported rather than thrown. This reading is fail-open
     * by contract — what it does when the analysis it borrows falls over is answer about what it
     * reached and no more — so a walk that missed one says less, the way it does for everything
     * else it cannot settle. Thrown from here it would abort a compile on one path
     * ({@code Adequacy.PathReached}) and come back as a build with no classes and no reason on
     * another ({@code Output.Evaluated} takes an absent answer for a failure).
     *
     * @return what went unanswered, or empty where nothing did
     */
    private static Optional<String> unanswered(
            Core body, CoverageSites.Plan plan, Map<ControlPlace, Reachability> out,
            Map<ConstructOccurrence,
                    souther.compiler.reach.ComparisonArrival> arriving) {
        // Which comparison this is, and only where the plan numbers one there: what is owed is
        // owed for the places a run could be recorded at, and a node that is no comparison of this
        // plan is a node there was nothing to answer about.
        if (body instanceof Core.Binary comparison) {
            Optional<String> here = unansweredAt(comparison, plan, out, arriving);
            if (here.isPresent()) {
                return here;
            }
        }
        List<String> missed = new ArrayList<>();
        Core.forEachChild(body, child ->
                unanswered(child, plan, out, arriving).ifPresent(missed::add));
        return missed.isEmpty() ? Optional.empty()
                : Optional.of(missed.get(0));
    }

    /**
     * What one comparison of the body was owed and did not get, or empty where it was owed nothing.
     *
     * <p>Owed only where the plan numbers one here. A node that is no comparison of this plan is a
     * place no run is recorded at, so there was never an answer for the walk to have missed.
     */
    private static Optional<String> unansweredAt(
            Core.Binary comparison, CoverageSites.Plan plan,
            Map<ControlPlace, Reachability> out,
            Map<ConstructOccurrence,
                    souther.compiler.reach.ComparisonArrival> arriving) {
        ConstructOccurrence which = numbered(comparison, plan);
        if (which == null) {
            return Optional.empty();
        }
        for (boolean result : new boolean[] {true, false}) {
            ControlPlace where = plan.outcomeOf(which, result).orElse(null);
            if (where != null && !out.containsKey(where)) {
                return Optional.of(
                        "this reading answered for no run through " + comparison.op()
                                + " at " + comparison.pos() + " coming out " + result
                                + "; the plan numbered it and a reader below cannot tell an "
                                + "answer that was never made from one that settled nothing");
            }
        }
        // And the arrival beside the outcomes: filed with them or not at all, and a finished walk
        // owes it for the same reason it owes them — a reader below reads an absence as the answer
        // that restricts nothing, so only an audit here can tell the two apart.
        if (!arriving.containsKey(which)) {
            return Optional.of(
                    "this reading said nothing about what arrives at " + comparison.op()
                            + " at " + comparison.pos()
                            + "; the plan numbered it and a reader below cannot tell an answer"
                            + " that was never made from one that restricts nothing");
        }
        return Optional.empty();
    }

    private final PathEngine engine;
    private final CoverageSites.Plan plan;
    /** What the declarations leave each position, which is what a {@code match} arm is held
     *  against. A condition narrows a path; a case is refused or left by the rules themselves. */
    private final InputDomain read;
    private final Symbols symbols;
    private final Map<ControlPlace, Reachability> out;
    private final Map<ConstructOccurrence,
            souther.compiler.reach.ComparisonArrival> arriving;
    /**
     * What holds where the body begins: the inputs entered and seeded, and no condition taken.
     *
     * <p>Kept so a proof can say which of two things contradicts a branch. A condition that leaves
     * nothing against this alone is one the declarations rule out wherever it stands, and an author
     * told that the conditions on the way cannot all hold would go looking at the guards above for
     * something that is not there.
     */
    private Known entry = Known.top();
    private Denotations entered = Denotations.none();

    private PathReachability(PathEngine engine, CoverageSites.Plan plan, InputDomain read,
                             Symbols symbols, Map<ControlPlace, Reachability> out,
                             Map<ConstructOccurrence,
                                     souther.compiler.reach.ComparisonArrival> arriving) {
        this.engine = engine;
        this.plan = plan;
        // Here as well as at the ways in, so that nothing inside this class is written against a
        // reading that might not be one.
        this.read = Objects.requireNonNull(read);
        this.symbols = symbols;
        this.out = out;
        this.arriving = arriving;
    }

    /**
     * Reads {@code e} under what holds where it stands.
     *
     * <p>{@code decided} is the conditions taken in on the way here, in the order they were
     * assumed. Carried rather than recovered from where a place sits, because what a proof is
     * allowed to name is what the domains were actually given: a condition of a shape nothing could
     * take in narrowed nothing and is not on the list.
     */
    private void walk(Core e, Known k, Denotations at, InputReads reads,
                      List<PathDecision> decided, boolean nothingAbove) {
        if (e == null) {
            return;
        }
        // Every comparison this plan numbers is answered for, and answered under what holds where it
        // stands. Before the reach test below, because a comparison in a region nothing arrives at
        // is a comparison nothing arrives at — which is the fact, and the fact a boundary drawn on
        // it is dropped by.
        if (e instanceof Core.Binary comparison) {
            outcomesAt(comparison, k, at, reads, decided);
        }
        if (k.reachesNothing()) {
            // Nothing stands here, so nothing below is a place anything arrives at either. The arms
            // under this are left absent, which reads as unsettled: the arm that made it so was
            // answered where it was entered, and saying it again at every fork inside would be the
            // same finding several times over.
            //
            // The comparisons are not. What this reading owes is one answer per comparison the plan
            // numbered, and a comparison nothing arrives at is exactly a comparison nothing arrives
            // at — so the walk goes on for those and stops for everything else.
            unreached(e, k, at, reads, decided);
            return;
        }
        switch (e) {
            // A short circuit runs its right side only where the left settled nothing, so the two
            // sides do not stand under the same conditions. Threaded here rather than in a walk of
            // its own over a fork's condition: a chain is a chain wherever it is written, and one
            // read only under the fork it was written into left the same operators unread a line
            // above.
            case Core.Binary binary when binary.op().stopsWhenItsAnswerIsSettled() -> {
                walk(binary.left(), k, at, reads, decided, nothingAbove);
                // Which way the left has to come out for the right to run is the operator's own
                // answer, and the same answer says there is a right side that runs only sometimes.
                // Read the other way round, a comparison guarded by its neighbour would be read
                // against conditions nothing on the way to it established.
                boolean reachedWhen = binary.op().rightRunsWhenLeftIs();
                Predicates.Assumed reaching = engine.assuming(binary.left(), k, at, reachedWhen);
                walk(binary.right(), reaching.known(), at, reads,
                        with(decided, reaching, binary.left().pos(), reachedWhen), nothingAbove);
            }
            case Core.If iff -> {
                walk(iff.cond(), k, at, reads, decided, nothingAbove);
                ControlPlace.Arm[] arms = plan.armsOf(iff);
                enterArm(arms, 0, iff, iff.then(), k, at, reads, decided, true);
                enterArm(arms, 1, iff, iff.els(), k, at, reads, decided, false);
            }
            case Core.IfConstructed ic -> {
                // The construction's own arguments, then the arms. Reaching the success branch is
                // the construction having held, so its binding carries what the type guarantees —
                // which is the whole of what a guard inside that branch has to read against. Each
                // departure stands where nothing was built, so none is entered with any of it.
                ic.construct().values().forEach(given ->
                        walk(given.value(), k, at, reads, decided, nothingAbove));
                PathEngine.Entered built = engine.enteringBuilt(ic, k, at);
                walk(ic.then(), built.known(), built.at(), reads, decided, false);
                for (Core.ElseArm arm : ic.els()) {
                    walk(arm.body(), k, at, reads, decided, false);
                }
            }
            case Core.LetIn li -> {
                walk(li.value(), k, at, reads, decided, nothingAbove);
                PathEngine.Entered in = engine.bindLet(li, k, at);
                // Inside what the `let` binds, so an arm of an expanded helper is read against the
                // position the call handed it. A binding is not a fork, so what stands above the
                // body is what stood above the binding.
                walk(li.body(), in.known(), in.at(), reads.and(li.binder(), li.value()), decided,
                        nothingAbove);
            }
            case Core.Match match -> {
                walk(match.scrutinee(), k, at, reads, decided, nothingAbove);
                cases(match, reads, nothingAbove);
                // The scrutinee once and then the arms, written out rather than left to the generic
                // walk. A `Match`'s children are its scrutinee as well as its arm bodies, so
                // walking them all reads the scrutinee a second time — under a fork this time,
                // which takes back what was answered about anything in it.
                //
                // Each arm binds a value of the case it matched, which is a location holding
                // whatever that type guarantees: an arm of `| Paid p ->` reads `p`'s invariant, and
                // a guard inside it settles against that. Entering it is how the arm gets it, the
                // same act a parameter gets it by.
                //
                // Under an arm, this fork stands above whatever is inside it. Which arm was taken
                // is not recorded as a decision: nothing was assumed from it, and a proof naming it
                // would claim work the domains did not do.
                for (Core.Case arm : match.cases()) {
                    PathEngine.Entered in = engine.enteringArm(arm, match.scrutinee(), k, at);
                    // And the name the arm binds stands for the scrutinee's position narrowed to
                    // the case it selects, which is where a comparison written inside the arm draws
                    // its line.
                    walk(arm.body(), in.known(), in.at(), reads.insideArm(match, arm, symbols),
                            decided, false);
                }
            }
            default -> {
                Core.forEachChild(e, child -> walk(child, k, at, reads, decided, nothingAbove));
            }
        }
    }

    /**
     * Every comparison below a place nothing arrives at, answered as one nothing arrives at.
     *
     * <p>A separate descent because what it does is not what the walk does: no arm is filed, and
     * nothing here narrows anything — the state was already empty when this began, and it stays
     * that way for every node under it. Whether one operand of a short circuit was reached is a
     * question about a run, and there are none.
     *
     * <p>Written out rather than folded into the walk, because the walk stops here and stopping is
     * what it is for. Read as one thing, the walk answered for whatever it happened to reach:
     * {@code A && (B || C)} with {@code A} ruled out stops at the operator, which is numbered
     * nowhere, and left {@code B} and {@code C} unanswered — the shape of a claim nothing made.
     */
    private void unreached(Core e, Known k, Denotations at, InputReads reads,
                           List<PathDecision> decided) {
        Core.forEachChild(e, child -> {
            if (child instanceof Core.Binary comparison) {
                outcomesAt(comparison, k, at, reads, decided);
            }
            unreached(child, k, at, reads, decided);
        });
    }

    /**
     * One comparison, answered both ways, under what holds where it stands.
     *
     * <p>Which nodes get an answer is the plan's to say and not this walk's. Read off the shape of
     * a fork's condition, the reading answered for the comparisons a fork was written directly
     * around and silently for no others — and a comparison nothing answered for reads, everywhere
     * below, exactly like one answered "nothing is known".
     *
     * <p>Empty is an ordinary answer here: the node is not a comparison, or is one this plan
     * numbered nothing at, and either way there is no place a run through it would be recorded.
     */
    private void outcomesAt(Core.Binary comparison, Known k, Denotations at, InputReads reads,
                            List<PathDecision> decided) {
        // What arrives is about the comparison and not about either way out of it, so it is filed
        // under the comparison the plan names and asked of the plan directly.
        ConstructOccurrence which = numbered(comparison, plan);
        if (which == null) {
            return;
        }
        arriving.put(which, arrivalAt(comparison, k, at, reads));
        for (boolean result : new boolean[] {true, false}) {
            Optional<ControlPlace.Outcome> where =
                    plan.outcomeOf(which, result);
            if (where.isEmpty()) {
                continue;
            }
            Predicates.Assumed taken = engine.assuming(comparison, k, at, result);
            out.put(where.get(), taken.known().reachesNothing()
                    ? new Reachability.Unreachable(Proof.conditionsThatCannotAllHold(
                            with(decided, taken, comparison.pos(), result)))
                    : new Reachability.Unsettled(whyNot(taken, comparison)));
        }
    }

    /**
     * What arrives at {@code comparison}, from the state before the comparison is taken either way.
     *
     * <p>The one place a {@link souther.compiler.reach.ComparisonArrival} is made, so the order of
     * the questions is fixed here and nowhere has to remember it. The whole state answers first:
     * bounds read off an empty state say nothing — the predicates alone can empty it and leave every
     * numeric reading untouched — so a {@code Values} built without asking would publish a wide-open
     * projection of an arrival that is a contradiction.
     *
     * <p>The position and the interval come off the same side of the comparison. What the fact
     * means is "the value at this path, among what arrives, lies here", and a reader applies it only
     * where its own quantity is that path's value — so a comparison this cannot say that of answers
     * {@code NoProjection}, which restricts nothing.
     */
    private souther.compiler.reach.ComparisonArrival arrivalAt(Core.Binary comparison, Known k,
                                                               Denotations at, InputReads reads) {
        if (k.reachesNothing()) {
            return new souther.compiler.reach.ComparisonArrival.NothingArrives();
        }
        Core side = comparedSideIn(comparison, reads);
        TermPath position = side == null ? null : pathUnder(side, reads);
        FactSubject atom = side == null ? null : engine.terms().atomOf(side, at);
        if (position == null || atom == null) {
            return new souther.compiler.reach.ComparisonArrival.NoProjection();
        }
        return new souther.compiler.reach.ComparisonArrival.Values(position,
                k.numbers().boundsOf(atom));
    }

    /**
     * How it was shown that nothing arrives at an arm.
     *
     * <p>Two things can contradict a branch and they send an author to different places. What the
     * declarations guarantee of an input holds wherever the branch stands, so a condition that
     * leaves nothing against the entry facts alone is ruled out by the position and not by anything
     * on the way; everything else is the conditions on the way, taken together.
     *
     * <p>Asked by putting the condition to the entry facts again rather than by reading which
     * domain went empty: what is wanted is whether the guards above did any of the work, and that
     * is a question about those two states and not about how either was reached.
     */
    private Proof why(Core cond, boolean holds,
                      List<PathDecision> under, InputReads reads) {
        if (engine.assuming(cond, entry, entered, holds).known().reachesNothing()) {
            TermPath position = comparedPositionIn(cond, reads);
            NumericDomain.Bounds admits = position == null ? null : valueBoundsAt(position);
            if (admits != null && !under.isEmpty()) {
                return Proof.outsideInputDomain(position, admits, under.get(under.size() - 1));
            }
        }
        return Proof.conditionsThatCannotAllHold(under);
    }

    /** The position a comparison turns on, where it turns on exactly one this reading knows. */
    private TermPath comparedPositionIn(Core cond, InputReads reads) {
        Core side = comparedSideIn(cond, reads);
        return side == null ? null : pathUnder(side, reads);
    }

    /** The side of a comparison that is that one position, where there is exactly one. One
     *  decision for the proof above and the arrival, so the two cannot name different sides. */
    private Core comparedSideIn(Core cond, InputReads reads) {
        if (!(cond instanceof Core.Binary b)) {
            return null;
        }
        TermPath left = pathUnder(b.left(), reads);
        TermPath right = pathUnder(b.right(), reads);
        return left != null && right == null ? b.left()
                : right != null && left == null ? b.right() : null;
    }

    /** Where a side of a comparison sits, reading through a newtype's own value. */
    private TermPath pathUnder(Core side, InputReads reads) {
        TermPath here = positionOf(side, reads);
        return here != null ? here
                : side instanceof Core.FieldAccess field ? positionOf(field.target(), reads) : null;
    }

    /** Where {@code e} stands, and null where it stands nowhere or was not read — which are one
     *  answer to a reader asking whether the guards above reach a position. */
    private TermPath positionOf(Core e, InputReads reads) {
        return switch (reads.pathOf(e, symbols)) {
            case PathResolution.At(var at) -> at;
            case PathResolution.NotAPosition _ -> null;
            // And one of several is not this position either. What the caller does with an answer
            // is prove a branch unreachable by what the rules leave at the position — a proof about
            // one place — so a name that stands at one of two places on two runs is one no such
            // proof can be made about, whichever of them it turns out to be.
            case PathResolution.MayStandAt _ -> null;
        };
    }

    /**
     * What the rules leave the value standing at {@code position}, where they leave it a range at
     * all.
     *
     * <p>That number and never another of the place. What the caller has is a comparison one side of
     * which is this position, and what it goes on to say is that the branch asks for values the
     * position cannot hold — so the range wanted is the range of what stands there. A count taken of
     * the position is a number of its own with a range of its own, and offered here it would prove a
     * branch unreachable by where a length falls.
     *
     * <p>No falling back to whatever range the position happens to have. A position with no answer
     * about the values standing at it is one this says nothing about, which is what the caller does
     * where it is handed null.
     */
    private NumericDomain.Bounds valueBoundsAt(TermPath position) {
        Position at = read.at(position);
        PositionBounds bounds = at == null ? null : at.boundsFor(new NumericTerm.ValueOf(position));
        NumericDomain.Bounds admits = bounds == null ? null : bounds.admissible();
        return admits == null || admits.saysNothing() ? null : admits;
    }

    /**
     * Why an arm this could not prove is left as it was.
     *
     * <p>Two ways of settling nothing, and they are owed different things. A condition of a shape
     * no rule here reads is this compiler's limit, and widening the reading removes it; a condition
     * read to no effect is the ordinary state of a branch nobody built a value for, and no widening
     * touches it. Told apart by asking the reading, not by comparing the state it answered with to
     * the state it was given — that comparison says whether anything changed, which is neither.
     *
     * <p>The shape and not what was taken in. Since every value has an identity, a condition this
     * could not read still narrows the state through the subject it names — so "something was taken
     * in" stopped telling the two apart, and what an author is owed here is whether the reading
     * reached what the condition says.
     */
    private static WhyUnsettled whyNot(Predicates.Assumed taken, Core cond) {
        return taken.shapeRead() ? WhyUnsettled.noWitness()
                : WhyUnsettled.aConditionWasNotRead(cond.pos());
    }

    /**
     * The conditions on the way here, with this one — where it was taken in at all.
     *
     * <p>The only way one of these is made, so that a proof cannot name a condition the domains
     * never took in. A condition nothing was taken from narrowed nothing: what came out is what went
     * in, and listing it among the reasons would say a line was holding when nothing here could tell
     * whether it was.
     *
     * <p>What was taken in, and not what was read. A condition whose shape ran out still narrows the
     * state through the subject it names, and it may be the whole of why nothing stands here — left
     * out, this proof would name the conditions that <em>were</em> read and say they cannot all hold
     * when they plainly can, which is a claim about the model made out of a limit of this compiler.
     */
    private static List<PathDecision> with(List<PathDecision> decided, Predicates.Assumed taken,
                                           SourcePos at, boolean held) {
        if (!taken.taken()) {
            return decided;
        }
        List<PathDecision> out = new ArrayList<>(decided);
        out.add(new PathDecision(at, held));
        return out;
    }

    /**
     * One arm of a fork, read under its own side of the condition.
     *
     * <p>The answer is filed at the arm and the walk goes on inside it. Where the condition leaves
     * nothing, the arm is proven and what is under it is not walked: everything there is unreachable
     * for the same reason, and one finding is what an author is owed.
     */
    private void enterArm(ControlPlace.Arm[] arms, int index, Core.If iff, Core arm,
                          Known k, Denotations at, InputReads reads, List<PathDecision> decided,
                          boolean holds) {
        Predicates.Assumed taken = engine.assuming(iff.cond(), k, at, holds);
        Known inside = taken.known();
        List<PathDecision> under = with(decided, taken, iff.cond().pos(), holds);
        if (arms != null && index < arms.length) {
            out.put(arms[index], inside.reachesNothing()
                    ? new Reachability.Unreachable(
                            why(iff.cond(), holds, under, reads))
                    : new Reachability.Unsettled(whyNot(taken, iff.cond())));
        }
        walk(arm, inside, at, reads, under, false);
    }

    /**
     * Every arm of a {@code match}, held against what the rules leave the position matched on.
     *
     * <p>A different question from a condition's, and answered from a different place. A condition
     * narrows the path to an arm; a case is one the rules of the position refuse or leave, which is
     * as true three arms deep as at the first fork — a refusal is unconditional.
     *
     * <p>The other direction is not. That the rules <em>leave</em> a case says a caller can supply
     * one, which says something about arriving here only where nothing stands above: a fork the
     * body reaches first is reached by the behavior being applied at all. So the witness takes both
     * — every rule of the position read and leaving the case, and nothing decided on the way — and
     * is built here where both are in hand rather than assembled by whoever asks.
     */
    private void cases(Core.Match match, InputReads reads, boolean nothingAbove) {
        ControlPlace.Arm[] arms = plan.armsOf(match);
        if (arms == null) {
            return;
        }
        // Not a position of this input, or not one this reading reached: either way nothing here
        // has rules about it to carry.
        TermPath path = positionOf(match.scrutinee(), reads);
        if (path == null) {
            return;
        }
        // The reading this walk was given, which is the one held here. Which location the name
        // stands for is the environment's answer and what the rules leave there is the reading's,
        // and neither is asked of the other.
        Position at = read.at(path);
        for (int i = 0; i < match.cases().size() && i < arms.length; i++) {
            // A position this reading never got to — deeper than it reads into what a parameter
            // holds — states no such distinction, which is the position's own answer and not this
            // walk's. Said in its words so that a claim below the depth is told what it is told
            // everywhere else.
            Reachability said = at == null
                    ? new Reachability.Unsettled(WhyUnsettled.thePositionDidNotSettleIt(
                            new souther.compiler.inputs.Unsettlement.NoSuchDistinction()))
                    : saidOf(at, path, match.cases().get(i), nothingAbove);
            if (said != null) {
                out.put(arms[i], said);
            }
        }
    }

    /**
     * What the rules leave one arm, or null where the arm names no case — a binding of the whole
     * value, which the rules of the position say nothing about.
     *
     * <p><b>Asked of the distinctions the arm reaches and not of the names it is written by.</b> A
     * name is not a distinction of a position: an optional's carriers name none of them, and a case
     * that is itself a sum names the leaves under it rather than any one of them. Asked by name,
     * every such arm came back as a position that had settled nothing — this compiler reporting a
     * limit as an answer about the model, and `unreachable` written on an arm the rules admit
     * going unreported (#1252). What the arm reaches is the checker's resolution of it, read as
     * distinctions where the two vocabularies agree.
     */
    private Reachability saidOf(Position at, TermPath path, Core.Case arm, boolean nothingAbove) {
        List<TypeSymbol> named = arm.caseTypes();
        if (named.isEmpty()) {
            return null;
        }
        List<souther.compiler.inputs.Refinement> reaches = new java.util.ArrayList<>();
        for (souther.compiler.types.ResolvedCase each : arm.pattern().cases()) {
            reaches.addAll(souther.compiler.inputs.Refinement.allOf(each));
        }
        if (reaches.stream().allMatch(each -> at.admissionOf(each) instanceof Admits.Refused)) {
            // Every case it is written for is one the rules refuse, so an arm a row could still
            // take is not among these: an arm goes only where all of them go.
            return new Reachability.Unreachable(
                    Proof.everyCaseRefused(path.toString(), named));
        }
        for (souther.compiler.inputs.Refinement each : reaches) {
            if (at.admissionOf(each) instanceof Admits.Unsettled unsettled) {
                return new Reachability.Unsettled(
                        WhyUnsettled.thePositionDidNotSettleIt(unsettled.why()));
            }
        }
        // Every one of them left standing, so a caller can supply one. Whether it arrives at this
        // fork as well is the other half, and nothing standing above is the one answer that settles
        // it: a fork the body reaches first is reached by the behavior being applied at all.
        return nothingAbove
                ? new Reachability.Reachable(
                        Witness.everyRuleReadAndNothingAbove(path.toString()))
                : new Reachability.Unsettled(WhyUnsettled.noWitness());
    }
}
