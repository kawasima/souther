package souther.compiler.partition;

import souther.compiler.check.CallArguments;
import souther.compiler.check.DeclaredArgument;
import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.core.Core;
import souther.compiler.semantics.AnswerAspect;
import souther.compiler.types.ValueName;

import java.util.List;
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
 * ({@link souther.compiler.semantics.OperationFact.TurnsOnWhatAnArgumentAnswers}), so it is asked
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
     * Whether some expression the truth of {@code atom} turns on satisfies {@code rule}.
     *
     * <p>{@code atom} itself first, since a fork testing a comparison tests that comparison; then
     * the closures the library says the answer turns on, each asked for its own truth.
     */
    static boolean turnsOnSomething(Core atom, Predicate<Core> rule) {
        return turnsOn(atom, AnswerAspect.TRUTH, rule, 0);
    }

    private static boolean turnsOn(Core e, AnswerAspect aspect, Predicate<Core> rule, int deep) {
        // A depth, because an operation the library declares to turn on its closure may be handed a
        // closure that calls it again. The edges are the library's and there are finitely many, but
        // what they are applied to is a body, and a body may nest as far as an author wrote.
        if (e == null || deep > 32) {
            return false;
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
                    if (turnsOn(part, AnswerAspect.TRUTH, rule, deep + 1)) {
                        return true;
                    }
                }
                return false;
            }
            if (rule.test(e)) {
                return true;
            }
            // A choice answers with one of its arms, so what it comes to is what they come to and
            // which of them was taken. Both reach the answer: a rule in an arm decides it where
            // that arm is taken, and the condition decides which arm that is. Beside the cut above
            // rather than in it — what a fork tests is one thing however it was computed, and this
            // is the other question, about what deciding it turns on.
            switch (e) {
                case Core.If iff -> {
                    if (turnsOn(iff.cond(), AnswerAspect.TRUTH, rule, deep + 1)
                            || turnsOn(iff.then(), AnswerAspect.TRUTH, rule, deep + 1)
                            || turnsOn(iff.els(), AnswerAspect.TRUTH, rule, deep + 1)) {
                        return true;
                    }
                }
                case Core.Match match -> {
                    for (Core.Case arm : match.cases()) {
                        if (turnsOn(arm.body(), AnswerAspect.TRUTH, rule, deep + 1)) {
                            return true;
                        }
                    }
                }
                default -> { }
            }
        }
        ValueName operation = operationOf(e);
        if (operation == null) {
            return false;
        }
        // A truth about a container that is a question about how many it holds. The library says
        // which operations mean that, and which number they mean it of.
        if (aspect == AnswerAspect.TRUTH
                && DefaultBoundOperationFacts.get().meansTheSameAsASizeOfNought(operation) != null) {
            return turnsOn(only(e), AnswerAspect.CARDINALITY, rule, deep + 1);
        }
        // And the argument this side of the answer turns on, asked for its own truth. A closure
        // answers what its body comes to, so that is what is read where one stands there; anything
        // else answers itself.
        var turns = DefaultBoundOperationFacts.get()
                .turnsOnWhatAnArgumentAnswers(operation, aspect);
        return turns != null && turnsOn(answerOf(argument(e, turns.argument())),
                AnswerAspect.TRUTH, rule, deep + 1);
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

    /** What {@code e} answers with: the body of the block, where it is one, and otherwise itself. */
    private static Core answerOf(Core e) {
        return e instanceof Core.Block block ? block.body() : e;
    }
}
