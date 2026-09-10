package souther.compiler.query;

import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.partition.ClassOfAPosition;
import souther.compiler.partition.Generator;

/**
 * One thing a run of the generator is asked to offer a row for.
 *
 * <p>Three kinds in one set, which is the whole of what this is for. What a class, an arm and a
 * point of a line are is already settled — each of them is a value some other part of this compiler
 * owns — and this puts them where one question can be asked of all three: which of them does a row
 * this run offers settle?
 *
 * <p><b>Nothing of an identity is written again here.</b> A wrapper that spelled out an axis and a
 * class id, a probe, or a line and a role would be a second answer to what tells two of them apart —
 * and the third would be wrong the day a point stopped being a line and a role, which it already
 * has: two points of one line and one role are two things where the sides they are inside of are
 * different. So each arm holds the value and adds nothing to it, and every arm holds exactly one.
 */
public sealed interface OfferItem {

    /** One class of one position. The axis names the behavior, so two behaviors dividing their own
     *  positions the same way are two of these. */
    record AClass(ClassOfAPosition owed) implements OfferItem {}

    /** One arm of one body. The sites of a module are numbered across it, so the probe is the whole
     *  of what tells two arms apart. */
    record AnArm(Generator.ArmOwed owed) implements OfferItem {}

    /** One point of one authored line: what a row standing there answers, however many positions
     *  read the line and whichever of them a row is written at. */
    record APointOfALine(BorderObligationPoint point) implements OfferItem {}
}
