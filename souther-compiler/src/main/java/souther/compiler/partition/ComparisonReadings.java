package souther.compiler.partition;

import souther.compiler.check.Comparison;
import souther.compiler.diag.Citation;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.types.BinOp;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.PathResolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One reading of a body's comparisons: where each stands, what its names point at, what a row had
 * already satisfied to get there, whether a line may be drawn on it, and what it came to where one
 * may.
 *
 * <p><b>Of the body the analysis reads, which is the tree the language's own operations stand
 * in.</b> A rule written over what such an operation answers is a rule of the model, and the tree
 * the backend emits from has the operation expanded into what it does — so a reading made there
 * met the comparison after the call was gone, while the rules about strings in the same body were
 * read where they stand. Two facts about one position came from two walks of two trees, and a
 * reader wanting both had to know which tree each came from.
 *
 * <p>Which leaves the comparisons inside such an operation out, and that is what it means to leave
 * them out: they are that operation's implementation, and a caller answers for the rules a caller
 * wrote. Where a run through a comparison is recorded is still the emitted tree's, and the two are
 * joined on the construct of the model they agree about
 * ({@link souther.compiler.types.ModelOccurrence}).
 *
 * <p><b>Each comparison is read once, here, and what it came to travels with it.</b> A reader that
 * reports why a comparison bears no line reads the answer off the standing and never reads the
 * arithmetic again: read again downstream, the second reading answers about the form when the
 * question was about where the comparison stands, and a form read to the end is described as one
 * nobody could read.
 *
 * <p><b>One walk, because there is one thing being walked.</b> Three facts about a comparison are
 * settled by where it stands in the body, and each was being worked out by a walk of its own: which
 * names are in force, whether what is computed there is read on the way to an answer, and what the
 * evaluation before it established. Two walks threading the same {@code let} rule through the same
 * tree are two chances to disagree about it, and a fact one of them learned to carry is one the
 * other still drops.
 *
 * <p><b>What stands when a comparison runs is a fact about the position, not about a condition.</b>
 * It was read off {@link Condition}, which is a reading of a subtree and so needs a root — and the
 * root anything ever gave it was a fork's condition. So {@code A && B} established nothing for
 * {@code B} while {@code if A then B else false} did, one model spelled two ways with two answers
 * about where a row for a border on {@code B} is looked for; a name standing for {@code A}
 * established nothing either, because a subtree read on its own has no reading of the names above
 * it. A walk of the body has no root to be given and reaches every position, so what a condition is
 * asked is what it can answer: what a subtree coming out one way establishes
 * ({@link ReachingCuts#stating}), and never where the comparisons are.
 *
 * <p>The two operators are where a walk of the body is not a walk of what runs. {@code &&} and
 * {@code ||} settle as soon as they can, so the right operand runs under what the left one coming
 * out its way established — which is the whole of what makes this per comparison rather than per
 * fork. Everything else evaluates its parts under what stood at it.
 */
record ComparisonReadings(List<Reading> comparisons, List<ForkMet> forks,
                          Map<ConditionOccurrence, Citation> conditionsMet) {

    ComparisonReadings {
        comparisons = List.copyOf(comparisons);
        forks = List.copyOf(forks);
        conditionsMet = Map.copyOf(conditionsMet);
    }

    /**
     * A fork the author wrote, the parts of what it tests, and which of them this walk owns.
     *
     * <p>Facts and not a verdict. A fork states a rule of its own only where a part of what it
     * tests is one nothing else answers for, and the readers that could answer are several — a
     * comparison of the values, the position a part is, a predicate over the strings there — so no
     * one of them may settle it. What is here is this walk's half: the parts
     * ({@link ConditionSkeleton}), and the ones this reading claims.
     *
     * <p>Read for every author-written fork and not only the ones that look opaque. A reader
     * joining these with what the other readers found needs the forks they say nothing about as
     * much as the ones they do, and a list already narrowed here would be this walk deciding the
     * question on its own evidence.
     *
     * @param atoms      what the fork tests, cut into the parts a rule can be about
     * @param ownedHere  the parts this reading answers for: one it read a comparison at, and one
     *                   that is a position of the input — a fork on such a part tests the values
     *                   standing there and its arms are their classes, so the question is the
     *                   position's
     */
    record ForkMet(ConstructOccurrence occurrence, Core condition, Citation at, InputReads reads,
                   List<Core> atoms, Set<Core> ownedHere) {

        ForkMet {
            if (occurrence == null || condition == null || at == null) {
                throw new IllegalArgumentException("a fork of the model is written somewhere");
            }
            atoms = List.copyOf(atoms);
        }

        /**
         * The parts of this fork this reading does not answer for.
         *
         * <p>What the other readers claim is subtracted by whoever holds their answers, which is
         * where the three meet ({@link BehaviorSetStatements}); this says only which parts are left
         * over from what was found here.
         *
         * <p>Compared by being the nodes the walk met and never by what they hold: a condition
         * writing one comparison twice writes two parts, and parts compared by their contents would
         * be one.
         */
        List<Core> leftHere() {
            List<Core> out = new ArrayList<>();
            for (Core each : atoms) {
                boolean owned = false;
                for (Core one : ownedHere) {
                    owned |= one == each;
                }
                if (!owned) {
                    out.add(each);
                }
            }
            return out;
        }
    }

    /**
     * One comparison of the body, read where it is written.
     *
     * <p>The reading travels with the comparison because it is not the same at every one of them: a
     * comparison inside an expanded helper is about the argument the call handed it, and read
     * against the names outside the binding it is about nothing at all.
     *
     * @param assumed every condition on the way here, each with what became of it. Empty says
     *                nothing stood on the way, which a comparison at the top of a body is; one this
     *                reading has no arithmetic for is on the list as a decline, so the two are not
     *                one answer
     */
    record Reading(ConstructOccurrence occurrence, Comparison comparison, Citation at,
                   InputReads reads,
                   List<OnTheWay> assumed, BoundaryPolicy.Standing standing) {

        Reading {
            if (occurrence == null || comparison == null || at == null) {
                throw new IllegalArgumentException(
                        "a reading is of some comparison of the model, placed somewhere");
            }
        }
    }


    /**
     * What is the same at every comparison of one body: whose body it is, what the plan numbered,
     * the module's names, the reading of the input, what the input's rules leave each quantity, and
     * what the paths leave arriving at each comparison.
     *
     * <p>Where the reading belongs, and it is not in the environment the walk carries. That
     * environment is a function of the program point — a binding met, an arm entered — and the
     * reading is one value for the whole of this walk. Put in it, the reading would be copied at
     * every step and asked of whichever copy a reader happened to hold.
     */
    private record Body(String behavior, InputReading read,
                        souther.compiler.coverage.Arrivals answering) {

        Symbols symbols() {
            return read.symbols();
        }

        RuleReadingSource rules() {
            return read.rules();
        }
    }

    /**
     * One reading of {@code body}.
     *
     * <p>Whose body it is is asked for, and it is what a report calls the rules read here. Which
     * comparison of the model each is comes off the node ({@link ModelOccurrence}) and not from the
     * name, so the two cannot come apart: a body read under another's name says the wrong thing in a
     * report and names no other comparison.
     *
     * <p><b>Nothing here is asked about the tree that runs.</b> Where a run through a comparison is
     * recorded, and what a run leaves arriving at its line, are read where the language's operations
     * are expanded — and this reads where they stand. A reading that took either would be one no
     * tree could make on its own.
     */
    static ComparisonReadings of(String behavior, Core body, InputReading read, InputReads reads) {
        List<Reading> readings = new ArrayList<>();
        List<ForkMet> forks = new ArrayList<>();
        // The names the conditions of this body take, handed out as the walk meets them. One of
        // these per body, because what a name is counted within is the body: counted over the
        // module, an edit to one behavior would rename the conditions of every one after it. Whose
        // reading it is comes off the names it is made against, which is the module being compiled
        // — a condition inside a helper spliced in from elsewhere is still one this reading met.
        ConditionNumbering numbering =
                new ConditionNumbering(read.symbols().module(), behavior);
        walk(body, new Body(behavior, read, souther.compiler.coverage.Arrivals.inTheBody(body)),
                reads,
                LiveFlow.of(body), List.of(), true, readings, forks, numbering);
        return new ComparisonReadings(readings, forks, numbering.metAt());
    }

    /**
     * @param assumed what evaluating everything before this position established
     * @param live    whether what is computed here is read on the way to what the behavior answers
     *                with. Carried down because everything inside a value nothing reads is read by
     *                nothing either
     */
    private static void walk(Core e, Body in, InputReads reads, LiveFlow flow,
                             List<OnTheWay> assumed, boolean live, List<Reading> out,
                             List<ForkMet> forks, ConditionNumbering numbering) {
        Symbols symbols = in.symbols();
        RuleReadingSource ruleSource = in.rules();
        // A comparison the source wrote, recognised by the one thing that says what one is
        // ({@link Comparison#of}). A binary this compiler composed states no rule of the model and
        // is not one, which is what an unwritten construct says of itself.
        Comparison comparison = comparisonAt(e);
        if (comparison != null) {
            Core.Binary binary = (Core.Binary) e;
            // Which comparison of the model it is, off the node. The two readings of a body hold
            // different comparisons and agree about this, so it is what a reader below joins on.
            ConstructOccurrence stands = binary.occurrence();
            Citation where = Citation.of(binary.pos());
            // Read only where the policy admits it, and under the names in force here, which is
            // the one environment the comparison is about. `answer` is null: a body has nothing
            // that is the answer.
            BoundaryPolicy.Standing standing = BoundaryPolicy.refuses(live)
                    .<BoundaryPolicy.Standing>map(BoundaryPolicy.Standing.Refused::new)
                    .orElseGet(() -> new BoundaryPolicy.Standing.Admitted(
                            ComparisonAssessment.of(in.behavior(), comparison.stated(), where,
                                    in.read(), reads, null, in.answering(), false)));
            out.add(new Reading(stands, comparison, where, reads, assumed, standing));
        }
        switch (e) {
            // The right operand runs only where the left came out the way that leaves the answer
            // unsettled, so what it stands under is what that says. Asked of the operand and not of
            // any fork above it: there need not be one, and where there is, this is what the fork
            // would have been reading anyway.
            case Core.Binary both when both.op() == BinOp.AND -> {
                walk(both.left(), in, reads, flow, assumed, live, out, forks, numbering);
                walk(both.right(), in, reads, flow,
                        taking(Condition.of(both.left(), reads, symbols, numbering), true,
                                in.read().domain(), assumed, ruleSource),
                        live, out, forks, numbering);
            }
            case Core.Binary either when either.op() == BinOp.OR -> {
                walk(either.left(), in, reads, flow, assumed, live, out, forks, numbering);
                walk(either.right(), in, reads, flow,
                        taking(Condition.of(either.left(), reads, symbols, numbering), false,
                                in.read().domain(), assumed, ruleSource),
                        live, out, forks, numbering);
            }
            // The condition under what stood above the fork, and each arm under what that arm proves
            // of it. A comparison inside a condition is not below the fork: it runs to decide it.
            case Core.If iff -> {
                walk(iff.cond(), in, reads, flow, assumed, live, out, forks, numbering);
                // Read once, whichever arm is being entered. Reaching the `then` and reaching the
                // `els` are two things one condition says, and a second reading for the second arm
                // would name that one condition twice.
                Condition condition = Condition.of(iff.cond(), reads, symbols, numbering);
                // What this walk found in the condition, for the reader that decides whether the
                // fork states a rule of its own. Said of every fork an author wrote, and of none
                // this compiler composed — a `guard`'s supplied arm and a lowering's test state
                // nothing about the model.
                //
                // And of none whose answer nothing reads. What is computed where no run reads it
                // divides nothing a row could be held to, which is the same reason a comparison
                // there draws no line ({@link NotABoundary#NOTHING_READS_IT}) — said of the fork
                // too, since a fork is a rule for having been written and a rule nothing reaches
                // is a measure held open over a question no row can answer.
                if (live && iff.occurrence() != null && iff.origin() != null
                        && iff.origin().isWritten()) {
                    // What each part stands for, which is where the readers of it look. The
                    // cutting is of the shape ({@link ConditionSkeleton}) and stops at a name; what
                    // the name denotes is the owner's question, and a reader that could not answer
                    // it would call a fork on a named comparison one nobody read.
                    List<Core> atoms = new ArrayList<>();
                    for (Core part : ConditionSkeleton.atoms(iff.cond())) {
                        atoms.add(reads.denotes(part, symbols).value());
                    }
                    Set<Core> owned = Collections.newSetFromMap(new IdentityHashMap<>());
                    for (Core atom : atoms) {
                        if (statedElsewhere(atom, reads, symbols).isEmpty()) {
                            owned.add(atom);
                        }
                    }
                    forks.add(new ForkMet(iff.occurrence(), iff.cond(), Citation.of(iff.pos()),
                            reads, atoms, owned));
                }
                walk(iff.then(), in, reads, flow,
                        taking(condition, true, in.read().domain(), assumed, ruleSource),
                        live, out, forks, numbering);
                walk(iff.els(), in, reads, flow,
                        taking(condition, false, in.read().domain(), assumed, ruleSource),
                        live, out, forks, numbering);
            }
            // What a `let` computes is read on the way to the answer only where the name is read;
            // everywhere else a value stands in a body it is consumed by what it stands in. And its
            // body is where the name stands for what was bound to it.
            case Core.LetIn let -> {
                walk(let.value(), in, reads, flow, assumed, live && flow.reads(let), out, forks,
                        numbering);
                walk(let.body(), in, reads.and(let.binder(), let.value()), flow, assumed, live,
                        out, forks, numbering);
            }
            // And each arm under what the arm says the value it matched turned out to be. A name
            // the arm binds is the scrutinee's position narrowed to that case, so a comparison
            // written inside an arm draws its line on a position the reading of the input has —
            // read without it, every rule an author writes inside a `match` was about nothing.
            //
            // The same narrowing is what a row has to be for the arm to be reached at all, so it
            // goes onto the account beside the conditions a guard states. Walked without it, a line
            // inside an arm was owed a row by a walk that had been told nothing stood on the way to
            // it, and the row composed for it was written in whichever arm the values fell in.
            case Core.Match match -> {
                walk(match.scrutinee(), in, reads, flow, assumed, live, out, forks, numbering);
                for (int part = 0; part < match.cases().size(); part++) {
                    Core.Case arm = match.cases().get(part);
                    walk(arm.body(), in, reads.insideArm(match, arm, symbols), flow,
                            entering(match, arm, part, in.read().domain(), reads, assumed,
                                    ruleSource, numbering),
                            live, out, forks, numbering);
                }
            }
            default -> Core.forEachChild(e, child ->
                    walk(child, in, reads, flow, assumed, live, out, forks, numbering));
        }
    }

    /**
     * What stands past {@code node} coming out {@code holding}: what stood before it, and what that
     * says.
     *
     * <p>Asked of {@link ReachingCuts#stating}, which is the same rule that says what reaching an
     * arm of a fork establishes. Both are "this subtree came out this way, so what follows", and
     * written apart they would agree by having been derived alike — until one of them learned to
     * read a shape of condition the other did not.
     */
    private static List<OnTheWay> taking(Condition condition, boolean holding,
                                         souther.compiler.inputs.InputDomain inputs,
                                         List<OnTheWay> assumed,
                                         RuleReadingSource ruleSource) {
        List<OnTheWay> out = new ArrayList<>(assumed);
        out.addAll(ReachingCuts.stating(condition, inputs, holding, ruleSource));
        return List.copyOf(out);
    }

    /** The same, for what standing inside one arm of a fork establishes ({@link
     *  ReachingCuts#entering}). */
    private static List<OnTheWay> entering(Core.Match match, Core.Case arm, int part,
                                           souther.compiler.inputs.InputDomain inputs,
                                           InputReads reads, List<OnTheWay> assumed,
                                           RuleReadingSource ruleSource,
                                           ConditionNumbering numbering) {
        List<OnTheWay> out = new ArrayList<>(assumed);
        out.add(ReachingCuts.entering(match, arm, part, inputs, reads, ruleSource, numbering));
        return List.copyOf(out);
    }

    /**
     * The comparison of the model {@code e} is, or null where it is not one.
     *
     * <p>The one thing that says what a comparison is ({@link Comparison#of}), asked once. A binary
     * this compiler composed states no rule of the model and is not one, which is what an unwritten
     * construct says of itself.
     */
    private static Comparison comparisonAt(Core e) {
        return e instanceof Core.Binary binary && binary.origin() != null
                && binary.origin().isWritten() ? Comparison.of(binary).orElse(null) : null;
    }


    /**
     * Whether something other than the fork itself states what {@code atom} decides.
     *
     * <p>Three readers could own it and each of them is asked the same way: a comparison of the
     * model, a position of the input the part is the value at, and another fork the source wrote.
     * Asked of the part rather than of the condition — a fork testing two things owns one of them
     * and leaves the other, and an answer about the whole would lose that.
     *
     * <p><b>Along what the part's answer turns on, for every one of them.</b> A rule written in a
     * closure one of the language's operations is handed reaches the fork where the library says
     * the answer turns on what that closure said ({@link WhatAForkTests}) — a filter answers fewer
     * for exactly that reason and a mapping does not, and the two calls are the same shape. Which
     * is a fact about the operation and not about the kind of rule on the other side of it: asked
     * along the edge for a comparison and at the part itself for a position,
     * {@code List.any(x -> x > 0, xs)} states nothing of its own while
     * {@code List.any(p -> p.active, xs)} states a rule nobody wrote.
     *
     * <p><b>Two of the three and not the third.</b> A fork the answer turns on may state a rule of
     * its own, and whether it does is not a fact about the fork's shape: a condition nobody else
     * answers for and that is about no position of the input states nothing, so a source-written
     * fork there is no rule for an outer one to be owned by. Read as one, a fork over such a
     * closure would be owned by a rule that does not exist and the question it leaves would go with
     * it. So that owner is asked where the rules themselves are known
     * ({@code BehaviorSetStatements#ofTheirOwn}), of the rules and not of the source.
     *
     * <p><b>Answered as the parts nobody else states, and not as whether somebody states one.</b>
     * What an operation's answer turns on is as many things as the closure states
     * ({@link WhatAForkTests#partsOfTheAnswer}): a closure answering
     * {@code p.age > 18 && List.isEmpty(p.tags)} states a comparison and something nothing here
     * reads, and the fork around the operation states the second whoever owns the first. Answered
     * as "something in there is owned", the second went with the first — which is the same partial
     * ownership a condition's own parts are cut along, lost one step past the operation.
     */
    static List<Core> statedElsewhere(Core atom, InputReads reads, Symbols symbols) {
        List<Core> left = new ArrayList<>();
        for (Core part : WhatAForkTests.partsOfTheAnswer(atom,
                one -> reads.denotes(one, symbols).value())) {
            if (comparisonAt(part) == null
                    && !(reads.pathOf(part, symbols) instanceof PathResolution.At)) {
                left.add(part);
            }
        }
        return left;
    }

    /** Whether the truth of {@code atom} turns on a predicate {@code read} took in, which is the
     *  same walk a comparison is looked for along and is here so that the two agree about it. */
    static boolean turnsOnAPredicate(Core atom, PredicateReadings read, InputReads reads,
                                     Symbols symbols) {
        return leftUnread(atom, read, reads, symbols).isEmpty();
    }

    /** The parts of what {@code atom} decides that none of the three readers answers for, which is
     *  what a fork over it is left stating. */
    static List<Core> leftUnread(Core atom, PredicateReadings read, InputReads reads,
                                 Symbols symbols) {
        return statedElsewhere(atom, reads, symbols).stream()
                .filter(part -> !read.statesOneAt(part))
                .toList();
    }
}
