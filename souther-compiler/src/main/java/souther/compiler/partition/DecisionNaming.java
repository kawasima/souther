package souther.compiler.partition;

import souther.compiler.core.Core;
import souther.compiler.flow.Naming;
import souther.compiler.inputs.InputReads;
import souther.compiler.numeric.Rel;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.ModelOccurrence;

/**
 * How the decision a body states writes down what got a run to an answer.
 *
 * <p>The reader-specific half of {@link souther.compiler.flow.ValueArrivals}. What the ways through
 * a body are is the body's own answer and is computed with no naming at all; which distinctions each
 * way turns on, and what tells one from another, is this.
 *
 * <p><b>Its own naming beside the one the combinations use.</b> That one writes a way as a class of
 * one position and tells two readings of one proposition apart by where each is written, which is
 * right for a reading whose places are run-time events. A decision rule is told apart by the
 * proposition: two writings of one inequality are one column, and a table with a column apiece
 * admits an assignment where a proposition holds and does not.
 *
 * <p><b>A condition this has no words for is a column and not a silence.</b> Answering null would
 * leave the way carrying every other condition and saying nothing about this one, which reads as a
 * rule that turns on less than it does. So a shape the arithmetic cannot state comes back as a
 * column the reading admits it cannot say the meaning of, and the way stays whole.
 *
 * <p>What does answer null is a fork whose ways could not all be written down. There is no condition
 * there to name — the reading fell short of enumerating them — so the way is
 * {@link souther.compiler.flow.Completeness#PARTIAL}, which is what a measurement that could not be
 * made in full says of itself.
 */
final class DecisionNaming implements Naming<DecisionPath> {

    /**
     * What a condition of this body states, made once beside the walk.
     *
     * <p>Not held as the reading of the input itself. This value is at a program point the way the
     * environment is, so a reading kept in it would be copied into every step and asked of whichever
     * copy a reader holds.
     */
    private final ConditionMeanings meanings;

    private final InputReads reads;

    /**
     * The names the conditions of this reading go by, shared down every scope.
     *
     * <p>One register for the body, because a condition met under a binding and the same condition
     * met outside it are one condition. A register per scope would name it once per scope, and two
     * accounts of one condition agree about nothing.
     */
    private final ConditionNumbering numbering;

    private final int mostArrivals;

    DecisionNaming(ConditionMeanings meanings, InputReads reads, ConditionNumbering numbering,
                   int mostArrivals) {
        this.meanings = meanings;
        this.reads = reads;
        this.numbering = numbering;
        this.mostArrivals = mostArrivals;
    }

    @Override
    public DecisionPath nowhere() {
        return DecisionPath.NOWHERE;
    }

    @Override
    public DecisionPath join(DecisionPath held, DecisionPath more) {
        return held.and(more);
    }

    @Override
    public Naming<DecisionPath> under(Core.Binder binder, Core value) {
        return new DecisionNaming(meanings, reads.and(binder, value), numbering, mostArrivals);
    }

    @Override
    public Naming<DecisionPath> insideArm(Core.Match match, Core.Case arm) {
        return new DecisionNaming(meanings, reads.insideArm(match, arm, meanings.symbols()),
                numbering, mostArrivals);
    }

    /**
     * That {@code value} came out {@code held}, as the distinction the body draws by it.
     *
     * <p>Read through {@link Condition}, which is the one reading of what a boolean subtree of a
     * body means, and turned into what it states by {@link ReachingCuts#stating} — the same
     * arithmetic every other reader of a comparison takes, so a line drawn on one and a column here
     * cannot come apart.
     *
     * <p>The connectives are not reached here: the reading of the ways settles an operator that
     * stops when its answer is settled, which is what puts the short-circuit in the rules. What
     * arrives is one condition, and what comes back is the columns it states.
     */
    @Override
    public DecisionPath side(Core value, boolean held) {
        Condition condition = Condition.of(value, reads, meanings.symbols(), numbering);
        DecisionPath path = DecisionPath.NOWHERE;
        for (OnTheWay each : meanings.stating(condition, held)) {
            DecidedCondition answer = answerOf(each, held);
            path = path.and(answer, shownBy(answer, condition, held), each);
            if (path == null) {
                return null;
            }
        }
        return path;
    }

    @Override
    public DecisionPath matchCase(Core.Match match, int part) {
        OnTheWay states = meanings.entering(match, part, reads, numbering);
        DecidedCondition answer = answerOf(states, true);
        ModelOccurrence fork =
                ModelOccurrence.statedAt(match.place().occurrence()).orElse(null);
        return DecisionPath.NOWHERE.and(answer, fork == null
                ? new ShownBy.NothingIsRecorded(answer.condition())
                : new ShownBy.AtAnArm(fork, part), states);
    }

    /**
     * The fork's own condition, coming out the way this arm is under.
     *
     * <p>Asked where the ways the condition comes out could not all be written down, which is what a
     * condition whose value this reading cannot work out leaves — a call to one of the language's
     * own operations among them. The fork is still one distinction with two answers, and the arms
     * are those answers: run together they would report a distinction the body draws as one it does
     * not, and both arms would carry the same empty path.
     *
     * <p>The same condition the reading would have named had it been able to value it, which is why
     * it is asked for the same way. A column minted here instead would be a second name for one
     * condition, and the two accounts of it would agree about nothing.
     */
    @Override
    public DecisionPath forkArm(Core fork, int part) {
        return fork instanceof Core.If iff ? side(iff.cond(), part == 0) : null;
    }

    @Override
    public int mostArrivals() {
        return mostArrivals;
    }

    /** One thing on the way, read as a column and an answer about it. */
    private static DecidedCondition answerOf(OnTheWay one, boolean held) {
        return switch (one) {
            case OnTheWay.TakenIn taken -> {
                Rel proposition = taken.cut().rel().orItsDenial();
                yield new DecidedCondition.Compared(
                        new DecisionCondition.AComparison(taken.cut().form(), proposition),
                        taken.cut().rel() == proposition);
            }
            case OnTheWay.Narrowed narrowed -> {
                TermPath at = narrowed.position();
                yield new DecidedCondition.Narrowed(
                        new DecisionCondition.APosition(at.narrowedFrom()), at.narrowing());
            }
            case OnTheWay.Declined declined -> new DecidedCondition.Unread(
                    new DecisionCondition.AConditionNotRead(
                            declined.condition(), declined.why()), held);
        };
    }

    /**
     * Where a run through one condition is recorded, said in the model's words.
     *
     * <p>The comparison's own construct of the model, which is what the tree the rules are read off
     * and the tree that runs agree about. A condition of any other shape has none to be seen at, and
     * says so rather than being left off the path.
     */
    private static ShownBy shownBy(DecidedCondition answer, Condition condition, boolean held) {
        return condition instanceof Condition.Compares one && one.states().isPresent()
                ? new ShownBy.AtAComparison(one.states().orElseThrow(), held)
                : new ShownBy.NothingIsRecorded(answer.condition());
    }
}
