package souther.compiler.report;

import souther.compiler.observe.Target;
import souther.compiler.partition.ClosureGap;

/**
 * What each thing a report can be about is about, projected to the one vocabulary a reader is sent
 * in.
 *
 * <p>A {@code switch} per sum and no {@code default} anywhere. Every arm this file reads is a sum
 * whose members carry the identity, and twice now an arm was added to one of them while whoever
 * read it kept naming the outer shape — a row that did not come back was answered as its behavior,
 * and a position the reading never reached into was answered as a rule. Both are the same mistake,
 * and it is one javac can make instead: an arm added to any sum below stops this compiling, and
 * whoever adds it has to say what a reader is sent to for it.
 *
 * <p>What is not here is the two subjects no payload holds. A measurement nobody made and an
 * obligation's disposition carry what they went without and nothing about which measure or which
 * point they are of, so those subjects are the walk's to hand in ({@link Subject}).
 */
final class Subjects {

    private Subjects() {
    }

    /**
     * Where an incompleteness was met, as the place a reader goes back to.
     *
     * <p>The target and not the fact. What happened is the code beside it and travels as the reason,
     * so two codes met at one place are two entries with one subject — which is the shape the
     * document already writes wherever a reason has a place.
     */
    static Subject of(Target target) {
        return switch (target) {
            case Target.OfBehavior it -> new Subject.OfABehavior(it.behavior());
            case Target.OfModule it -> new Subject.OfAModule(it.module());
            case Target.OfSource it -> new Subject.OfASource(it.sourceId());
            // The row and not the behavior it is a row of. Two rows of one behavior that did not
            // come back are two facts, and this is the arm that keeps them two.
            case Target.OfRow it -> new Subject.OfARow(it.rowRef());
            // The path as a reason spells it, which is not the address the reading of the model
            // works in — the two are different arms for that reason.
            case Target.AtPosition it ->
                    new Subject.AtASpelledPosition(it.behavior(), it.path());
        };
    }

    /**
     * Where a reading of the model that did not run out leaves a reader.
     *
     * <p>Only one of the three is about a rule. A question nothing answered names the rule it was
     * raised by; the other two name a position the walk did not reach, and what stopped it is the
     * reason beside them. Answered as a rule for all three, two thirds of these would send a reader
     * after a rule the fact never named.
     */
    static Subject of(ClosureGap gap) {
        return switch (gap) {
            case ClosureGap.QuestionUnanswered it -> new Subject.AtARule(it.question());
            case ClosureGap.RulesNotReached it ->
                    new Subject.AtAPosition(it.behavior(), it.at());
            case ClosureGap.PositionNotReachedInto it ->
                    new Subject.AtAPosition(it.behavior(), it.at());
        };
    }
}
