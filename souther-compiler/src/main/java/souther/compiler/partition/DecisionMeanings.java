package souther.compiler.partition;

import souther.compiler.core.Core;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Rel;

import java.util.ArrayList;
import java.util.List;

/**
 * What the conditions of one body decide, as the columns of its decision table.
 *
 * <p>Beside {@link ReachingCuts} and not inside it. That one answers what may safely narrow the
 * region a search looks in, and gives up whole facts to stay safe; this one answers which
 * distinctions a rule turns on, where a fact given up is a column the table admits it cannot say
 * the meaning of. The two questions have different right answers about one condition — a truth
 * narrows no region and is a distinction all the same — so they are asked apart, and what a
 * comparison over the input states is asked of the arithmetic rather than read a second way here.
 *
 * <p>Both halves come back together. A column is what tells one rule from another, and what the
 * same condition states about the input is what a search composes a row against; a reader holding
 * one without the other would have to pair them again from the order two walks happened to answer
 * in.
 *
 * @param states      what every condition of this body states about its input
 * @param subjects    what the body's expressions name that a row can control
 * @param comparisons how a comparison over one of them is read
 */
record DecisionMeanings(ConditionMeanings states, DecisionSubjects subjects,
                        DecisionComparison comparisons) {

    /** One condition of a path: the column it is, and what it states about the input. */
    record Read(DecidedCondition answer, OnTheWay onTheWay) {}

    /**
     * What {@code condition} coming out {@code held} decides, one entry per condition consulted.
     *
     * <p>The connective is walked here for the reason {@link ReachingCuts#stating} walks it: a
     * joined condition coming out the way that gives both halves is both halves having come out
     * that way, and the other composition says one of them failed and names neither. So the two
     * readings meet the same conditions, and a column and the region a row for it is looked for in
     * are about one of them.
     */
    List<Read> deciding(Condition condition, boolean held) {
        if (condition instanceof Condition.Joined joined
                && joined.how().under(held) == souther.compiler.semantics.ConditionJoin.BOTH) {
            List<Read> out = new ArrayList<>(deciding(joined.left(), held));
            out.addAll(deciding(joined.right(), held));
            return List.copyOf(out);
        }
        List<Read> out = new ArrayList<>();
        for (OnTheWay each : states.stating(condition, held)) {
            out.add(new Read(answerOf(condition, each, held), each));
        }
        return List.copyOf(out);
    }

    /**
     * One thing on the way, as the column it is.
     *
     * <p>What the arithmetic took in is a comparison and what it narrowed is a position, and both
     * are read straight off. What it declined is where this reading has something of its own to
     * say: a truth of a subject a row controls is a column, and everything else is a condition this
     * compiler read nothing of, which the rule carries as that rather than leaving off.
     */
    private DecidedCondition answerOf(Condition condition, OnTheWay one, boolean held) {
        return switch (one) {
            case OnTheWay.TakenIn taken -> {
                Rel proposition = taken.cut().rel().orItsDenial();
                yield new DecidedCondition.Compared(new DecisionCondition.AComparison(
                        DecisionComparison.ofTheInput(taken.cut().form()), proposition),
                        taken.cut().rel() == proposition);
            }
            case OnTheWay.Narrowed narrowed -> {
                TermPath at = narrowed.position();
                yield new DecidedCondition.Narrowed(new DecisionCondition.ACase(
                        new DecisionSubject.AnInput(at.narrowedFrom())), at.narrowing());
            }
            case OnTheWay.Declined declined -> {
                // A fork brings no condition of the boolean grammar, and what it is about was
                // asked where the arm is.
                DecidedCondition read = condition == null ? null : ofASubject(condition, held);
                yield read != null ? read
                        : new DecidedCondition.Unread(new DecisionCondition.AConditionNotRead(
                                declined.condition(), declined.why()), held);
            }
        };
    }

    /**
     * The column {@code condition} is where what it is about is a subject a row controls, or null.
     *
     * <p>Where the arithmetic of the input declined, which is where this reading has something of
     * its own to say: the truth of such a subject, and a comparison over what one of them answered.
     * A condition of any other shape is one this compiler read nothing of.
     */
    private DecidedCondition ofASubject(Condition condition, boolean held) {
        return switch (condition) {
            case Condition.Truth truth -> {
                DecisionSubject subject = subjects.of(truth.value(), truth.reads());
                yield subject == null ? null
                        : new DecidedCondition.Stood(new DecisionCondition.ATruth(subject), held);
            }
            case Condition.Compares compares ->
                    comparisons.of(compares.comparison(), compares.reads(), held);
            case Condition.Joined _ -> null;
        };
    }

    /**
     * What entering {@code part} of {@code match} decides, and what it states about the input.
     *
     * <p>A fork is not a condition of the boolean grammar, so nothing here reads it as a truth: the
     * arm establishes which case the scrutinee turned out to be, and where that could not be said
     * as a narrowing the column says so.
     */
    Read entering(Core.Match match, int part, InputReads reads, ConditionNumbering numbering) {
        OnTheWay onTheWay = states.entering(match, part, reads, numbering);
        if (!(onTheWay instanceof OnTheWay.Declined)) {
            return new Read(answerOf(null, onTheWay, true), onTheWay);
        }
        // Where the arithmetic had no position to narrow, the fork may still be on something a row
        // controls. The arm says which case it turned out to be either way, and the two arms of one
        // fork are answers about the one subject — which is what keeps a table from admitting a
        // value that is two cases at once.
        DecisionSubject subject = subjects.of(match.scrutinee(), reads);
        Refinement narrowing = match.cases().get(part).selectedCase().map(Refinement::of)
                .orElse(null);
        return subject == null || narrowing == null
                ? new Read(answerOf(null, onTheWay, true), onTheWay)
                : new Read(new DecidedCondition.Narrowed(
                        new DecisionCondition.ACase(subject), narrowing), onTheWay);
    }
}
