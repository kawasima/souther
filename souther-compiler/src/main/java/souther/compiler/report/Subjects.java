package souther.compiler.report;

import souther.compiler.observe.Target;
import souther.compiler.partition.ClosureGap;
import souther.compiler.query.Weakening;

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
     * Where one thing a measure went without leaves a reader.
     *
     * <p>Each arm answers with what it holds and no more. Five of them name a behavior and nothing
     * else, and that is the fact's own identity rather than a shortage: what a measure of that
     * behavior went without is one thing however many measures noticed it, and a subject naming the
     * measure would make it as many facts as the behavior has measures.
     *
     * <p>What is not the subject is what happened. A border read in two ways is one line and two
     * reasons; a position the walk did not reach into is one place and one of five stops. Those
     * travel beside the subject, so the entries stay two and the reader is sent to one place.
     */
    static Subject of(Weakening cause) {
        return switch (cause) {
            // The fact is the incompleteness, and the incompleteness says which half of it is the
            // place. Where it was met is evidence and is no part of either.
            case Weakening.ObservationIncomplete it -> of(it.met().fact().target());
            case Weakening.OutputCasesUnreadable it -> new Subject.OfABehavior(it.behavior());
            // The input as well as the behavior. Two of a behavior's inputs whose cases could not
            // be read are two facts, and the position among them is what says so.
            case Weakening.InputCasesUnreadable it ->
                    new Subject.AtAnInput(it.behavior(), it.at());
            case Weakening.BorderValueUnreadable it -> new Subject.AtABorder(it.border());
            case Weakening.ModelReadingIncomplete it -> of(it.cause());
            case Weakening.BodiesNotElaborated it -> new Subject.OfAModule(it.module());
            case Weakening.BoundaryNotDerived it -> new Subject.OfABehavior(it.behavior());
            case Weakening.InputNotRead it -> new Subject.OfABehavior(it.behavior());
            // The behavior, and not how large the space was or what it was walked against. Those
            // are what the walk met and are the reason; a behavior has one pair space, so they
            // tell no two of these apart.
            case Weakening.PairSpaceTruncated it -> new Subject.OfABehavior(it.behavior());
            // The arm as the source wrote it, which is what the fact now carries: the number a run
            // through it was recorded at is resolved where the sites are, and nothing this far
            // along has them.
            case Weakening.ProofContradicted it -> new Subject.AtAnArm(it.arm());
            case Weakening.ArmsUnsettled it -> new Subject.AtAFork(it.fork());
            // The behavior, and not the rule or the row. A run that could not be placed is one
            // fact about the reading of that behavior's runs, and every rule of its decision rests
            // on it — named by a rule, one shortfall would be as many facts as the body has ways.
            case Weakening.DecisionOfRowUnreadable it -> new Subject.OfABehavior(it.behavior());
            // The behavior whose ways could not be written down. Which figure stopped the reading
            // is what happened and travels as the reason; one body has one decision, so it tells
            // no two of these apart.
            case Weakening.DecisionReadingIncomplete it -> new Subject.OfABehavior(it.behavior());
        };
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
            // The position, and not the rule it names. A reader is sent where the line was to have
            // been, and which rule was to have drawn it is what the finding there says — sent to
            // the rule, two of these about one position would be two places to go for one line.
            case ClosureGap.LineNotDerived it ->
                    new Subject.AtAPosition(it.behavior(), it.at());
        };
    }
}
