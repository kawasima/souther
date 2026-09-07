package souther.compiler.partition;

import souther.compiler.check.CallArguments;
import souther.compiler.check.DeclaredArgument;
import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.core.Core;
import souther.compiler.semantics.AnswerAspect;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which expressions a fork's answer turns on, following only what the library says it does.
 *
 * <p>A rule written somewhere inside what a fork tests is a rule the fork tests only where the
 * value it decides reaches the fork's answer. {@code List.isEmpty(List.filter(p, xs))} turns on
 * what {@code p} answered — filtering answers fewer for exactly that reason — and
 * {@code List.isEmpty(List.map(p, xs))} does not: a mapping answers one per element whatever the
 * closure said, so what {@code p} decides is what the answers are and never how many.
 *
 * <p><b>Along declared edges and never down the tree.</b> The two calls above are the same shape,
 * so a walk that went wherever it could go would credit both alike — and a rule credited to a fork
 * it says nothing about is a fork this compiler did not read reported as one it did. What an
 * operation's answer turns on is a fact about the operation
 * ({@link souther.compiler.semantics.OperationFact.TurnsOnWhetherAnArgumentHolds}), so it is asked
 * there.
 *
 * <p><b>Stopping is the answer, not a gap.</b> An operation the library says nothing about is one
 * this cannot follow, and the walk ends. What that costs is a fork left stating a rule of its own,
 * which leaves the measure open; what following anyway would cost is a model nothing read reported
 * as read to the end, and the two are not the same kind of wrong.
 */
final class WhatAForkTests {

    private WhatAForkTests() {}

    /**
     * The expressions the truth of {@code atom} turns on, each of them one thing a reader could
     * answer for.
     *
     * <p>{@code atom} itself where nothing takes the walk further, since a fork testing a
     * comparison tests that comparison; the parts of it where it is written out of parts; and
     * beyond that whatever the library says the answer turns on, each read the way a condition is.
     *
     * <p><b>The parts and not whether one of them was found.</b> Which of them a reader owns is a
     * question about each of them: a closure answering {@code p.age > 18 && List.isEmpty(p.tags)}
     * states a comparison and something nothing here reads, and the fork around the operation
     * states the second whoever owns the first. Answered as "something in there is owned", the
     * second went with the first — and that is the same partial ownership a condition's own parts
     * are cut along, lost one step past the operation.
     */
    static List<Core> partsOfTheAnswer(Core atom,
                                       java.util.function.UnaryOperator<Core> denotes) {
        List<Core> out = new ArrayList<>();
        turnsOn(atom, AnswerAspect.TRUTH, denotes, new HashSet<>(), out);
        return out;
    }

    /**
     * The same, as a question about one thing: whether {@code rule} holds of any part of the
     * answer.
     *
     * <p>For a reader whose question is about the atom rather than about the parts — whether what
     * a fork tests reaches some rule at all. A reader deciding what to do with each part asks for
     * the parts.
     */
    static boolean turnsOnSomething(Core atom, Predicate<Core> rule,
                                    java.util.function.UnaryOperator<Core> denotes) {
        return partsOfTheAnswer(atom, denotes).stream().anyMatch(rule);
    }

    private static void turnsOn(Core e, AnswerAspect aspect,
                                java.util.function.UnaryOperator<Core> denotes,
                                Set<Asked> met, List<Core> out) {
        // By what has been asked, which is what makes it stop. The tree is finite and so are the
        // library's edges, and a name a walk followed may lead back to where it started — so a
        // question already asked is one already answered rather than one to ask again. Not a depth:
        // a number would make a walk of thirty-two steps answer and one of thirty-three come back
        // saying nothing was found, which is the shape of an answer nobody decided.
        if (e == null || !met.add(new Asked(e, aspect))) {
            return;
        }
        // Whether it holds is decided by the parts of it that decide it, which is the same cut a
        // fork's own condition is made along ({@link ConditionSkeleton}): a closure answering
        // `a > 0 && b > 0` states two rules, and one written under a name it binds is what the
        // closure answers with. Asked here so that a closure is read the way a condition is,
        // rather than only where its whole body is the rule.
        if (aspect == AnswerAspect.TRUTH) {
            List<Core> parts = ConditionSkeleton.atoms(e);
            if (parts.size() != 1 || parts.get(0) != e) {
                for (Core part : parts) {
                    turnsOn(part, AnswerAspect.TRUTH, denotes, met, out);
                }
                return;
            }
            // A choice answers with one of its arms, so what it comes to is what they come to and
            // which of them was taken. Both reach the answer: a rule in an arm decides it where
            // that arm is taken, and the condition decides which arm that is. Beside the cut above
            // rather than in it — what a fork tests is one thing however it was computed, and this
            // is the other question, about what deciding it turns on.
            switch (e) {
                case Core.If iff -> {
                    turnsOn(iff.cond(), AnswerAspect.TRUTH, denotes, met, out);
                    turnsOn(iff.then(), AnswerAspect.TRUTH, denotes, met, out);
                    turnsOn(iff.els(), AnswerAspect.TRUTH, denotes, met, out);
                    return;
                }
                case Core.Match match -> {
                    for (Core.Case arm : match.cases()) {
                        turnsOn(arm.body(), AnswerAspect.TRUTH, denotes, met, out);
                    }
                    return;
                }
                default -> { }
            }
        }
        // And beyond an operation the library says the answer turns on, what it turns on.
        int before = out.size();
        Core beyond = beyond(e, aspect, denotes);
        if (beyond != null) {
            turnsOn(beyond, beyondIsAboutEmptiness(e, aspect)
                    ? AnswerAspect.EMPTINESS : AnswerAspect.TRUTH, denotes, met, out);
        }
        // Where nothing came back, the expression is where the walk stopped and is the thing the
        // answer turns on — which is what a reader is offered to own or to leave.
        //
        // <p>Of a truth and never of an emptiness. What a walk crosses into on the emptiness side
        // is a container, and whether a container holds anything is not what stands at the position
        // it names: emitted there, a fork on {@code List.isEmpty(xs)} would be owned by the
        // position {@code xs} and come out as a rule about the values in it. So the emptiness side
        // is crossed to look for the truths beyond it, and where there are none the truth this was
        // reached from is what a reader is offered.
        if (out.size() == before && aspect == AnswerAspect.TRUTH && !writtenOut(e)) {
            out.add(e);
        }
    }

    /**
     * Whether {@code e} is a value the source wrote out, which is an answer and not a question.
     *
     * <p>An arm of a choice may be a value rather than a test — {@code if p then true else false} —
     * and what the answer turns on there is which arm was taken and not the values the arms are.
     * Offered as a part, such a value is one nobody answers for and nobody can: it states nothing
     * and there is nothing about it to read, so a fork over a condition every other part of which
     * was read would come back unread on account of a constant.
     */
    private static boolean writtenOut(Core e) {
        return switch (e) {
            case Core.Int _, Core.Decimal _, Core.Str _, Core.Bool _, Core.Temporal _,
                 Core.UnitValue _, Core.ListLit _, Core.Tuple _, Core.OptionSome _,
                 Core.OptionNone _, Core.Construct _ -> true;
            default -> false;
        };
    }

    /** Whether what the library says this answer turns on is the emptiness of what it was given. */
    private static boolean beyondIsAboutEmptiness(Core e, AnswerAspect aspect) {
        return aspect == AnswerAspect.TRUTH && DefaultBoundOperationFacts.get()
                .meansTheSameAsASizeOfNought(operationOf(e)) != null;
    }

    /**
     * What the library says this side of {@code e}'s answer turns on, or null where it says
     * nothing.
     *
     * <p>Two edges and no third. A truth about a container that is the question of whether it holds
     * anything, said by the library naming the size such an operation compares against nought; and
     * the argument a side of the answer turns on, whose closure answers what its body comes to.
     */
    private static Core beyond(Core e, AnswerAspect aspect,
                               java.util.function.UnaryOperator<Core> denotes) {
        ValueName operation = operationOf(e);
        if (operation == null) {
            return null;
        }
        if (aspect == AnswerAspect.TRUTH
                && DefaultBoundOperationFacts.get().meansTheSameAsASizeOfNought(operation) != null) {
            return only(e);
        }
        var turns = DefaultBoundOperationFacts.get()
                .turnsOnWhetherAnArgumentHolds(operation, aspect);
        return turns == null ? null : answerOf(argument(e, turns.argument()), denotes);
    }

    /** Which library operation {@code e} applies, in either shape a representation gives one, or
     *  null where it applies none. */
    private static ValueName operationOf(Core e) {
        return switch (e) {
            case Core.PreservedCall kept -> kept.declared().operation();
            case Core.Call call when call.fn() instanceof Core.Reached reached -> reached.denotes();
            default -> null;
        };
    }

    private static List<Core> argumentsOf(Core e) {
        return switch (e) {
            case Core.PreservedCall kept -> kept.args();
            case Core.Call call -> call.args();
            default -> List.of();
        };
    }

    /** The one argument an operation of one value was given, or null where it took another
     *  number of them. */
    private static Core only(Core e) {
        List<Core> args = argumentsOf(e);
        return args.size() == 1 ? args.get(0) : null;
    }

    /** What {@code e} passes where {@code which} stands, or null where it passes nothing there. */
    private static Core argument(Core e, DeclaredArgument which) {
        List<Core> args = argumentsOf(e);
        int at = CallArguments.positionOf(which, operationOf(e));
        return at < 0 || at >= args.size() ? null : args.get(at);
    }

    /**
     * What {@code e} answers with: the body of the block, where it is one, and otherwise itself.
     *
     * <p>What a name stands for is asked first, of whoever owns that question. A closure written as
     * a name is the block that name was bound to, so a reading that stopped at the name would say a
     * rule inside it decides nothing — and one model would be read two ways depending on whether the
     * author bound the closure before handing it over.
     */
    private static Core answerOf(Core e, java.util.function.UnaryOperator<Core> denotes) {
        Core stands = denotes.apply(e);
        return stands instanceof Core.Block block ? block.body() : stands;
    }

    /** One question this walk has been asked: an expression, and which side of what it answers.
     *  Told apart by the node itself, so that two of one shape written twice are two questions —
     *  which is what this says, and what holds it is that whatever collects these asks it. A
     *  collection comparing keys by identity would be answering with the identity of the question
     *  rather than of the expression, and a question built afresh to ask with is a new object every
     *  time. */
    private record Asked(Core e, AnswerAspect aspect) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Asked it && it.e == e && it.aspect == aspect;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(e) * 31 + aspect.hashCode();
        }
    }
}
