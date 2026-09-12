package souther.compiler.partition;

import souther.compiler.check.AnalysisBody;
import souther.compiler.check.ElementBindings;
import souther.compiler.check.PredicateStatement;
import souther.compiler.check.RuleRef;
import souther.compiler.check.StatedContract;
import souther.compiler.check.StringPredicates;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.diag.Citation;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.InputReads;

import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.BindingId;
import souther.compiler.types.SourceConstructOrigin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One reading of the predicates a body applies to the strings at its positions.
 *
 * <p><b>Of the body the analysis reads</b> ({@link AnalysisBody}), which is the tree such a rule is
 * in. {@code String.startsWith(p, code)} is one of the language's own operations, and the tree a
 * backend emits has it expanded into the walk it turns into — read there, a body full of rules about
 * its strings holds none. So this takes the representation where the operation stands and reads it
 * as what it states.
 *
 * <p><b>Beside {@link ComparisonReadings} and not inside it.</b> Both read the tree the language's
 * operations stand in; what differs is the authority. A comparison puts a line on the order the
 * values at a position are counted on, and a predicate tells a set of them from the rest — two
 * questions with two answers, and a walk that asked them together would be one reader deciding
 * which of the two a rule is by looking at what it turned out to do.
 *
 * <p>This said the two read two trees, which is what they used to do: a rule stated as one of the
 * language's own operations was read where those stand and a comparison where they are expanded.
 * A comparison is read where they stand now, so the difference between the two readers is the
 * question and not the representation.
 *
 * <p><b>What a rule means and what it does to a position are still apart.</b> This says which
 * predicate was applied, which strings it states and what the subject is. Whether that restricts the
 * position or divides it turns on where the rule is written, and a rule in a body divides — so the
 * division is worked out where every rule reaching a position is held together, out of the sets this
 * left.
 *
 * <p><b>A rule nothing reads is no rule.</b> A predicate bound to a name the body never reads states
 * nothing about what the behavior answers, and a class divided off a position by it would be one no
 * value of the model is ever on either side of. So a reading is made only where what is computed is
 * read on the way to the answer, which is the one thing carried down this walk.
 */
record PredicateReadings(List<Reading> predicates, Set<Core> statedAt,
                         souther.compiler.coverage.Arrivals arrivals) {

    PredicateReadings {
        predicates = List.copyOf(predicates);
    }

    /**
     * Whether one of these was read at {@code e}.
     *
     * <p>The node itself and never one holding the same thing: a body writing one predicate twice
     * writes two rules, and either of them may be part of a condition the other is not.
     *
     * <p>At and not inside. Which expressions inside a call its answer turns on is the library's to
     * say ({@link WhatAForkTests}), and a reader that went looking through the tree for one would
     * credit a predicate inside a mapping with deciding whether the mapping is empty.
     */
    boolean statesOneAt(Core e) {
        for (Core each : statedAt) {
            if (each == e) {
                return true;
            }
        }
        return false;
    }

    /**
     * One predicate of the body, read where it is applied.
     *
     * @param origin which rule this is, which of the body's readings of it, and where a reader is
     *               sent to find it
     * @param stated the argument the rule is about and what the predicate states of it
     * @param reads  what the names in force here stand for, which is what resolves the subject to a
     *               position. It travels with the reading because it is not the same at every
     *               application: a predicate inside an expanded helper is about the argument the
     *               call handed it
     */
    record Reading(PredicateOrigin origin, StringPredicates.Stated stated, InputReads reads) {

        Reading {
            if (origin == null || stated == null || reads == null) {
                throw new IllegalArgumentException(
                        "a predicate that was read is some rule, read as something, somewhere");
            }
        }

        /** The argument the rule is about, for a caller that resolves it to a position. */
        Core subject() {
            return stated.subject();
        }

        /** What this compiler made of the strings the predicate states. */
        StringPredicates.Reading reading() {
            return stated.reading();
        }

        /** What the rule states, for a reader that names what it divides a position into. Null
         *  only where the text an author wrote was not worked out. */
        PredicateStatement statement() {
            return stated.statement();
        }
    }

    /**
     * One reading of {@code body}.
     *
     * <p>Whose body it is is asked for, which is where this differs from the walk of comparisons.
     * There a reading is of a comparison the catalog named, and the name says whose body it stands
     * in; here there is no catalog and nothing else names the behavior, so a rule this finds could
     * be filed under no behavior at all.
     *
     * <p><b>The names are read where this walk starts and are not handed in.</b> Which tree a
     * reading of the names is of is part of what it answers — an operation standing names no
     * position here, and says the wrong tree arrived in the body a backend emits — so the walk that
     * knows which tree it is over is what makes the reading. Taken as a parameter, this walk could
     * be handed the emitted body's reading and the first rule about a value an operation made would
     * come back as this compiler having failed to expand something.
     */
    static PredicateReadings of(String behavior, AnalysisBody body, StatedContract stated,
                                InputReading read, Map<BindingId, String> parameters,
                                ElementBindings elements, RuleReachNumbering reaches) {
        List<Reading> predicates = new ArrayList<>();
        // Where each of these was read, for the reader that decides which parts of a fork's
        // condition already state a rule. The node and not a fork: which parts a condition has is
        // cut in one place ({@link ConditionSkeleton}), and a walk that decided for itself which of
        // them it was inside would be a second cutting of the same shape.
        Set<Core> statedAt =
                Collections.newSetFromMap(new IdentityHashMap<>());
        if (body != null) {
            walk(body.core(), behavior, read,
                    InputReads.ofParametersWhereCallsStand(parameters, elements),
                    LiveFlow.of(body.core()), true, predicates, reaches, statedAt);
        }
        // And what the behavior states about its own answer, which is the same kind of rule written
        // somewhere else. Two walks and one list: a body and an `ensures` may write a rule about one
        // position, and what that position is divided into is what they come to between them — so
        // they meet before anything is built, and not in two groups a term is measured by twice.
        //
        // The walks are apart because what counts as a rule differs. A body states one wherever it
        // computes something it reads; a clause states its own and those of both sides of every
        // `&&` above them. Which statements a clause has is neither reader's answer and is asked
        // once ({@link ClauseStatements}), so what this walk does is read each of them.
        if (stated != null && !stated.isEmpty()) {
            InputReads reads = InputReads.ofWhatIsDeclared(
                    EnsuresThresholds.rootsOf(stated.params()));
            for (StatedContract.StatedRule rule : stated.rules()) {
                for (StatedContract.Conjunct conjunct : rule.conjuncts()) {
                    Core one = conjunct.stated().orNull();
                    if (one == null) {
                        continue;
                    }
                    for (ClauseStatements.Stated said : ClauseStatements.of(
                            conjunct.part(), one, reads, read.symbols())) {
                        // The statements this reader owns, and nothing said about the rest. What a
                        // statement of another kind came to is answered by the reader that owns it,
                        // once.
                        // The call is one the author wrote, which is what `ClauseStatements` reads
                        // these off; the construct is taken here rather than worked out inside.
                        if (said.statement()
                                        instanceof ClauseStatements.Statement.TellsStringsApart it
                                && it.stated().application()
                                        instanceof ApplicationOrigin.Written wrote) {
                            read(it.stated(), wrote.application(), behavior, it.states(), it.reads(),
                                    predicates, reaches);
                        }
                    }
                }
            }
        }
        // Whether an expression answers a value, of this body. Rooted there and not at whatever a
        // reader happens to hand over: a subtree read as a body of its own has every name in it
        // free, and a name bound to something that aborts is what makes the difference.
        return new PredicateReadings(predicates, statedAt, body == null
                ? souther.compiler.coverage.Arrivals.whereNothingStandsAbove()
                : souther.compiler.coverage.Arrivals.inTheBody(body.core()));
    }

    /**
     * One predicate, wherever a walk met it.
     *
     * <p>The one crossing from a node to what it states, so that a rule written in a body and one
     * written in an {@code ensures} come back as the same thing. What differs between the two is
     * which nodes a walk reaches, which is each walk's own answer and is above.
     *
     * <p>The text an author wrote is reached through the names in force here, which is what a walk
     * holds and the table does not. Handed a fold of its own, the table would report a rule under
     * {@code let prefix = "JP"} as one whose argument nothing worked out while the walk had the
     * answer.
     */
    private static void found(Core e, String behavior, InputReading read, InputReads reads,
                              List<Reading> out, RuleReachNumbering reaches) {
        // A rule is read off a call the author wrote; what a pass composed states nothing an author
        // owes rows for.
        if (!(e instanceof Core.PreservedCall call)
                || !(call.application() instanceof ApplicationOrigin.Written written)) {
            return;
        }
        Symbols symbols = read.symbols();
        StringPredicates.Stated stated =
                StringPredicates.statedBy(call, symbols, at -> reads.writtenStringOf(at, symbols));
        if (stated != null) {
            read(call, written.application(), behavior, stated, reads, out, reaches);
        }
    }

    /**
     * One predicate as a reading of it, out of what it was already read to state.
     *
     * <p>Where the two walks meet. What a predicate means is read once — by the walk of the body
     * here, and by the reading of a clause's statements for the other — and this turns that into a
     * reading of the rule. Read again on the way in, the reading and what it is a reading of would
     * be free to disagree about what the author wrote.
     */
    private static void read(Core.PreservedCall call, SourceConstructOrigin written, String behavior,
                             StringPredicates.Stated states, InputReads reads, List<Reading> out,
                             RuleReachNumbering reaches) {
        out.add(new Reading(
                new PredicateOrigin(new PredicateOccurrence(out.size()),
                        new RuleRef.Predicate(behavior, written),
                        reaches.anchorOf(written, Citation.of(call.pos()))),
                states, reads));
    }

    /**
     * @param live whether what is computed here is read on the way to what the behavior answers
     *             with. Carried down because everything inside a value nothing reads is read by
     *             nothing either
     */
    private static void walk(Core e, String behavior, InputReading read, InputReads reads,
                             LiveFlow flow, boolean live, List<Reading> out,
                             RuleReachNumbering reaches, Set<Core> statedAt) {
        int before = out.size();
        if (live) {
            found(e, behavior, read, reads, out, reaches);
        }
        if (out.size() != before) {
            statedAt.add(e);
        }
        switch (e) {
            // What a `let` computes is read on the way to the answer only where the name is read;
            // everywhere else a value stands in a body it is consumed by what it stands in. And its
            // body is where the name stands for what was bound to it.
            case Core.LetIn let -> {
                walk(let.value(), behavior, read, reads, flow, live && flow.reads(let), out,
                        reaches, statedAt);
                walk(let.body(), behavior, read, reads.and(let.binder(), let.value()), flow, live,
                        out, reaches, statedAt);
            }
            // And each arm under what the arm says the value it matched turned out to be. A name
            // the arm binds is the scrutinee's position narrowed to that case, so a predicate
            // written inside an arm is about a position the reading of the input has — read
            // without it, every rule an author writes inside a `match` was about nothing.
            case Core.Match match -> {
                walk(match.scrutinee(), behavior, read, reads, flow, live, out, reaches, statedAt);
                for (Core.Case arm : match.cases()) {
                    walk(arm.body(), behavior, read, reads.insideArm(match, arm, read.symbols()),
                            flow, live, out, reaches, statedAt);
                }
            }
            default -> Core.forEachChild(e, child ->
                    walk(child, behavior, read, reads, flow, live, out, reaches, statedAt));
        }
    }
}
