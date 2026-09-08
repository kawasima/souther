package souther.compiler.coverage;

import souther.compiler.types.SourceConstructOrigin;

import java.util.Optional;

/**
 * A place in a body that something can or cannot arrive at, named once.
 *
 * <p>Not the probe number. A probe is made where a row can be recorded, which takes two things:
 * the place has to answer a value, and it has to stand where a row can get to. So the arms an
 * author writes and the arms a run can be observed in are different collections, and the ones with
 * no probe are exactly the ones a claim is about — an arm answering {@code unreachable} answers no
 * value, so it never had a number, and the reading that judges what the author declared there was
 * looking for it under one.
 *
 * <p>Two layers, made together and here only. Everything below reads whichever it needs: a claim is
 * judged at the {@code controlId}, a branch denominator counts the arms that carry a probe, and a
 * line drawn on a comparison asks about the outcome that leads into an arm rather than about the
 * arm. Derived apart, each of them would answer for a different collection of places, which is what
 * a measurement number standing in for an identity already did once.
 *
 * <p>Numbered per plan, in the order the walk makes them. Two calls of one helper are two
 * occurrences and two ids: each is reached under its caller's own conditions, so what can arrive at
 * one says nothing about the other. What they share is the obligation ({@link
 * CoverageSites.Obligation}), which is what a row is owed for and is not this.
 */
public sealed interface ControlPointId {

    /** Which place this is, among all the places of one plan. */
    int controlId();

    /**
     * One arm, as it stands in the tree that runs.
     *
     * @param probe where a run is recorded, or empty where no row that stands can be in this arm.
     *              Empty is an ordinary answer and not a gap: the arm is still an arm, still
     *              written, and still something a reading can prove nothing arrives at
     * @param anchor what a report about this arm points at, said without a place
     *               ({@link ArmReportAnchor}). Settled here with the rest of the arm, because
     *               which of the two a reader is shown turns on what the position the walk had in
     *               hand was in — and that is the last moment anything here has one
     * @param origin what wrote the fork, and which module's source that was. Carried because a
     *               fork spliced in from another module is not this module's to be told about:
     *               {@code Int.max} has a fork of its own, and a call handing it an argument one
     *               side of it can never take makes that side dead <em>here</em> while it is alive
     *               wherever else the library is used
     */
    record ArmOccurrence(int controlId, Optional<ArmProbe> probe, ArmReportAnchor anchor,
                         SourceConstructOrigin origin) implements ControlPointId {

        /**
         * Whether {@code module}'s own source wrote the fork this is an arm of.
         *
         * <p>What a report about the arm turns on. A fork reached through a call into another
         * module is that module's construct standing here: nothing about it is this author's to
         * change, and a proof that nothing takes one of its arms is a fact about this call site
         * rather than a defect in either module. What a denominator does with such an arm is the
         * other question — nobody can write a row through it wherever it was written, so it goes.
         */
        public boolean writtenBy(String module) {
            return origin != null && origin.isWritten() && origin.module().equals(module);
        }

        public ArmOccurrence {
            if (probe == null) {
                throw new IllegalArgumentException(
                        "an arm with no answer about its probe is one nothing numbered");
            }
            // The two say one thing where they both say anything, so they are held to it here. An
            // arm reported at the fork it is written at, whose anchor names some other construct,
            // would send a reader to a fork this arm is not one of — and nothing downstream reads
            // both halves to notice.
            if (anchor instanceof ArmReportAnchor.WhereItIsWritten written
                    && !written.origin().equals(origin)) {
                throw new IllegalArgumentException("an arm is an arm of one fork: " + origin
                        + " reported at " + written.origin());
            }
        }

        /** Whether a run through this arm can be observed, which is what a branch denominator
         *  counts and what could show a proof about it wrong. */
        public boolean isMeasured() {
            return probe.isPresent();
        }
    }

    /**
     * The place a comparison comes out one way.
     *
     * <p>Which arm that leads to is not this, and the two are not each other's. A condition stops as
     * soon as it is settled, so under {@code A && B} the arm taken when the condition fails is
     * reached both by a value that made {@code B} false and by one that never reached {@code B} —
     * the arm cannot say which comparison came out which way, and a line is drawn on the comparison.
     *
     * <p>What a plan may claim, written in the plan's own vocabulary: the address this numbering
     * issued for the comparison, and the way it came out. A running class records the number
     * instead, having no numbering to ask what it addresses, and putting the two together is the
     * boundary between a recording and a numbering rather than anything this holds.
     *
     * <p>One place and one way, so there is nothing here for a caller to pair wrongly. Two halves
     * carrying a place each — an address beside a recorded number — would be a control point saying
     * where it is twice.
     *
     * @param at   where this numbering records a run through the comparison
     * @param held the way it came out
     */
    record ComparisonPoint(int controlId, ComparisonEmissionSite at, boolean held)
            implements ControlPointId {

        public ComparisonPoint {
            if (at == null) {
                throw new IllegalArgumentException(
                        "a place a comparison comes out one way is a place");
            }
        }
    }
}
