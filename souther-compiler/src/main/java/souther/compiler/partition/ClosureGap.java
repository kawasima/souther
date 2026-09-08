package souther.compiler.partition;

import souther.compiler.check.RuleRef;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.PositionId;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.observe.RunSensitivity;

/**
 * One thing that stopped a measure's reading of the model from running out.
 *
 * <p>What {@link MeasureClosure} used to answer with a boolean. Whether the reading ran out and what
 * was left of it when it did not are one question asked twice, and only the first of them had a
 * value: a measure that came back weaker than complete said so and nothing said why, so every reader
 * that wanted the why rebuilt it from the lists kept beside the measure (issue #953).
 *
 * <p><b>The fact itself, not a code and a name for what it is about.</b> Each arm holds what the
 * reader that found it produced. Written as a code and a subject the two would have to be taken
 * apart again by whoever wanted the rule, and a subject wide enough to hold a rule, a position and
 * an omitted axis is one every reader has to guess the shape of.
 *
 * <p>Which measures each of these costs is not asked here. That is
 * {@link MeasureClosure#of}'s to decide and the answers differ — a rule relating two positions
 * leaves neither measure short, and an omitted axis leaves the border measure short only where it
 * was carrying a line.
 */
public sealed interface ClosureGap {

    /**
     * Whether a run of this compiler that allows more could come to a different answer here.
     *
     * <p>Delegated and never decided. Three of the four hold what stopped them, so they ask it; the
     * fourth holds no reason on purpose and answers from what can reach it, which
     * {@code WhatEachClosureGapSaysAboutAWiderRunTest} holds to the reasons that do.
     */
    RunSensitivity runSensitivity();

    /**
     * What tells one of these from another.
     *
     * <p>The value itself, wherever everything the arm holds is what a reader is told. Where the
     * arm holds what evidenced the fact as well — the handle a reader is sent to, what a reading
     * was short of — the fact alone, so that one thing that went wrong, found twice, is one thing.
     *
     * <p>Asked of the arm and not written down by whoever gathers these. Two places put these
     * together — what one measure's reading came to, and what a measurement went without — and a
     * quotient written at each would be two answers to the question of what one gap is.
     */
    Object fact();

    /**
     * Two of these under one fact, as one.
     *
     * <p>What each arm does with what it holds beside the fact is that arm's own: a handle joins,
     * because two of them are two ways to one place; what an author wrote is held to, because two
     * accounts of it that differ are two accounts one of which is wrong.
     *
     * <p>Commutative, so which of the two a walk met first decides nothing about the result. A
     * {@code switch} with no {@code default}, so an arm added later has to say whether it carries
     * anything to accumulate before anything can put two of them together.
     */
    static ClosureGap merged(ClosureGap had, ClosureGap also) {
        if (!had.fact().equals(also.fact())) {
            throw new IllegalArgumentException("two gaps put together are two of one fact: "
                    + had.fact() + " and " + also.fact());
        }
        return switch (had) {
            case QuestionUnanswered it -> it.mergedWith(it.andAlso(also));
            // Equal under the fact and holding nothing else, so both are the same value.
            case RulesNotReached _, PositionNotReachedInto _, LineNotDerived _ -> had;
        };
    }

    /**
     * A rule of the model that leaves a measure of coverage open.
     *
     * <p>Either kind, because a measure is held open by either and the difference between them is
     * about what a reader is told rather than about whether anything is missing. A rule whose
     * reading finished raises a question about a subject and nothing answered it; a rule whose
     * reading did not is one nothing worked out the questions of. Which measures each of them holds
     * open is {@link MeasureClosure}'s to answer, and it asks the question rather than the reason it
     * stands for.
     */
    record QuestionUnanswered(StandingQuestion question) implements ClosureGap {

        /** What a reading found, whole. Taken apart into the rule and the handles, this would hold
         *  a second answer to what makes two of these one and a second way of putting two together
         *  — and the type that has those is the one the readers produced. */
        public static QuestionUnanswered of(StandingQuestion asked) {
            return new QuestionUnanswered(asked);
        }

        /** The question's own, which is the rule that raised it and what it asks. */
        @Override
        public Object fact() {
            return question.fact();
        }

        /** The other one, where it really is one of these. Two gaps filed under one fact and not
         *  of one kind is a fact two arms answer with, which nothing here can put together. */
        QuestionUnanswered andAlso(ClosureGap other) {
            if (other instanceof QuestionUnanswered it) {
                return it;
            }
            throw new IllegalArgumentException("a question that stands and " + other
                    + " are filed under one fact");
        }

        /** Both readings' accounts, as one, which the question itself says how to do. */
        public QuestionUnanswered mergedWith(QuestionUnanswered other) {
            return new QuestionUnanswered(question.mergedWith(other.question));
        }

        /**
         * What the reasons the question is short for come to, and it takes all of them.
         *
         * <p>A question stands until every reason it stands for is gone, so a run that allows more
         * answers it only where every one of those is a stop such a run gets past. Read as "one of
         * them was", a question short for a figure and for a form nothing reads would send a person
         * to allow more and leave the form exactly as unread.
         *
         * <p>Empty is {@link RunSensitivity#UNAFFECTED} for the same
         * reason it is not {@code MAY_CHANGE}: nothing here is a figure a run may allow more of.
         */
        @Override
        public RunSensitivity runSensitivity() {
            return question.stopped().all().stream()
                    .allMatch(each -> each.runSensitivity()
                            == RunSensitivity.MAY_CHANGE)
                    ? RunSensitivity.MAY_CHANGE
                    : RunSensitivity.UNAFFECTED;
        }
    }

    /**
     * A position whose rules nothing enumerated. It raises no question, so it cannot be short of
     * one — which is why it is a gap of its own and not one of the two above.
     *
     * <p><b>Not every way the rules go unread reaches this.</b> A handing over left standing because
     * the walk could not go into the position is the same stop {@link PositionNotReachedInto}
     * reports, and it is written there once. Which of the ways this is comes off the arm the reading
     * settled and never off what else was found at the path
     * ({@link souther.compiler.inputs.RulesLeftUnread}): a position the reading entered and lost a
     * clause of its own is an independent finding, and a fold on the path would take it with the
     * other (issue #1084).
     *
     * <p><b>And two of those ways reach a document as this one word.</b> A reading that lost a
     * clause of its own and a recipient that got no reading are different causes and both are
     * things an author is told about; what they are told is that the rules at this position were
     * not all reached, which is what this says. The reason stays inside
     * ({@link souther.compiler.inputs.RulesLeftUnread}) because a document naming it would make a
     * change to how this compiler traverses a model into a change to what its documents carry. Two
     * of these at one position are one entry, which is that sentence said once.
     *
     * <p><b>And whose position it is.</b> An account of one behavior is put together with another's
     * — a module's is the union of them — and a union keeps one of two equal facts. Two behaviors
     * taking one type have positions spelled alike, so without the behavior the second of them
     * would be the first said again.
     */
    record RulesNotReached(String behavior, souther.compiler.inputs.PositionId at)
            implements ClosureGap {

        /**
         * The one arm that answers rather than asks, because it holds no reason to ask.
         *
         * <p>What reaches it is a {@link souther.compiler.inputs.RulesLeftUnread}: a clause this
         * reading lost, and a handing over nobody took over. Neither is a figure this compiler
         * compared anything against, so a run that allows more meets both again — and the reason
         * stays inside for the reason given above, which is that a document naming it would make a
         * change to how this compiler traverses a model into a change to what its documents carry.
         *
         * <p>Said here and held elsewhere. A third way of leaving rules unread that a wider run
         * does get past would make this answer wrong with nothing in this file to say so, so
         * {@code WhatEachClosureGapSaysAboutAWiderRunTest} asks every arm of that type.
         */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }

        /** Everything it holds is what a reader is told: whose position, and which. */
        @Override
        public Object fact() {
            return this;
        }
    }

    /**
     * A position the walk could not reach into, with what the structural reading found instead.
     *
     * <p>The one arm for a position with no rule to name. There were two for a while — this and a
     * {@code PositionBlocked} taking {@code PositionReadingBlocked}, which is the same fact keyed by
     * path rather than by axis. It was written from the list {@code PartitionEvidence} keeps beside
     * the measures rather than from what {@link MeasureClosure#of} finds, so nothing ever built one:
     * an arm taken from a second representation of a fact, which is the mistake #953 is about, in
     * miniature.
     *
     * <p><b>{@code at} names the position, and not a number measured of it.</b> A location is
     * measured at as many numbers as the rules name of it, and one stop under the location is one
     * thing that went wrong however many of those there are. Named for a measure, it is one entry
     * per number and no reader can tell that from several stops. What weakens one measurement is
     * said per measurement elsewhere; these are one behavior's account of what its reading of the
     * model came to, and a reader holding a measure reaches its position by the path it reads from.
     *
     * <p>{@link RulesNotReached} is keyed the same way, so the two are one vocabulary, and both
     * carry whose position it is for the reason given there.
     *
     * <p>Written from {@link souther.compiler.inputs.BlockedDescent} and never from what the axis is
     * still waiting on. A position something answered for keeps no continuation, and was still never
     * entered.
     */
    record PositionNotReachedInto(String behavior, souther.compiler.inputs.PositionId at,
                                  BlockReason.AboutThePosition why) implements ClosureGap {

        /** The stop's own answer. */
        @Override
        public RunSensitivity runSensitivity() {
            return why.runSensitivity();
        }

        /** Everything it holds is what a reader is told: whose position, which, and what the walk
         *  met there. */
        @Override
        public Object fact() {
            return this;
        }
    }

    /**
     * A rule saying where the values at a position stop, one end of which a choice in it left open.
     *
     * <p><b>The one arm that is the border's alone.</b> Everything else here is a rule or a
     * position the reading did not get to, which leaves both measures short. This one leaves the
     * classes either side of the line exactly as they were read: what a choice offering an
     * alternative nothing reads takes back is where the values stop, and the values themselves are
     * what each alternative admits — which the reading of values answered.
     *
     * <p><b>A rule and a position, and not the choice.</b> One rule may hold two choices that each
     * leave one position's end open, and lifting one of them leaves the line as underivable as it
     * was — so what went wrong is that the end at this position was not derived, once, however many
     * choices are behind it. Which choices those are is what the findings beside this name, and a
     * reader is sent to the rule.
     *
     * <p>Not folded into the question a rule leaves standing. That question is raised where the
     * walk over the written clause reaches a comparison, and the walk stops at a choice — so a
     * position carrying both is carrying two rules' worth of trouble, and lifting the one the
     * question is about leaves this where it was.
     */
    record LineNotDerived(String behavior, PositionId at, RuleRef rule) implements ClosureGap {

        /**
         * What an author lifts here is a form this compiler does not enter, which a run allowed
         * more of everything meets again.
         *
         * <p>The same answer {@link BlockReason.EndLeftOpenByAChoice} gives, and for the same
         * reason: the reading of ends stops on a shape rather than at a figure, so there is nothing
         * for a wider run to allow more of.
         */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }

        /** Everything it holds is what a reader is told: whose position, which, and which rule. */
        @Override
        public Object fact() {
            return this;
        }
    }
}
