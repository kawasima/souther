package souther.compiler.coverage;

import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.SourceConstructOrigin;

import java.util.Optional;

/**
 * Which control alternative of the tree that runs this is, with what a plan has worked out for
 * observing it and for reporting about it.
 *
 * <p>Which one it is is the tree's answer. An arm is {@link ArmOccurrence} and a comparison coming
 * out one way is the comparison's own {@link ConstructOccurrence} beside the way it came out;
 * neither is a number anything handed out. What this adds is the plan's part: where a run through
 * the place is recorded, and which question locates a report about it. None of those addresses
 * names the place, and a reading that took one for the place would be reading the emitter.
 *
 * <p><b>Not the probe number.</b> A probe is made where a row can be recorded, which takes two
 * things: the place has to answer a value, and it has to stand where a row can get to. So the arms
 * an author writes and the arms a run can be observed in are different collections, and the ones
 * with no probe are exactly the ones a claim is about — an arm answering {@code unreachable}
 * answers no value, so it never had a number, and the reading that judges what the author declared
 * there was looking for it under one.
 *
 * <p>The two halves are made together and here only. Derived apart, they would answer for different
 * collections of places: a claim is judged at the place, a branch denominator counts the arms that
 * carry a probe, and a line drawn on a comparison asks about the outcome that leads into an arm
 * rather than about the arm.
 *
 * <p><b>One of these is of one plan, and nothing in it says which.</b> An arm a run cannot be
 * recorded in carries no numbering at all, so a place cannot be asked what plan it is of. What
 * holds two readings apart is the pairing rather than the place: a reader that has both a plan and
 * a reading of it says so once, at the seam where they meet
 * ({@link souther.compiler.check.PathReachability.Answers#requireNumbering}). Asked of each place
 * instead, the question would be one an unprobed arm has no answer to, and giving it one would put
 * the plan's own address back inside the identity.
 */
public sealed interface ControlPlace {

    /**
     * One arm, as it stands in the tree that runs.
     *
     * @param arm    which arm this is. Two calls of one helper are two arms: each is reached under
     *               its caller's own conditions, so what can arrive at one says nothing about the
     *               other. What they share is the obligation ({@link CoverageSites.Obligation}),
     *               which is what a row is owed for and is not this
     * @param probe  where a run is recorded, or empty where no row that stands can be in this arm.
     *               Empty is an ordinary answer and not a gap: the arm is still an arm, still
     *               written, and still something a reading can prove nothing arrives at
     * @param anchor what a report about this arm points at, said without a place
     *               ({@link ArmReportAnchor}). Settled with the rest of the arm, because which of
     *               the two a reader is shown turns on what the position the walk had in hand was
     *               in — and that is the last moment anything here has one
     */
    record Arm(ArmOccurrence arm, Optional<ArmProbe> probe, ArmReportAnchor anchor)
            implements ControlPlace {

        public Arm {
            if (arm == null) {
                throw new IllegalArgumentException("a place an arm is is some arm");
            }
            if (probe == null) {
                throw new IllegalArgumentException(
                        "an arm with no answer about its probe is one nothing numbered");
            }
            // The two say one thing where they both say anything, so they are held to it here. An
            // arm reported at the fork it is written at, whose anchor names some other construct,
            // would send a reader to a fork this arm is not one of — and nothing downstream reads
            // both halves to notice.
            if (anchor instanceof ArmReportAnchor.WhereItIsWritten written
                    && !written.origin().equals(arm.origin())) {
                throw new IllegalArgumentException("an arm is an arm of one fork: " + arm.origin()
                        + " reported at " + written.origin());
            }
        }

        /** Which fork of the source this is an arm of. What a row is owed for, and what a report
         *  about the arm is written against. */
        public SourceConstructOrigin origin() {
            return arm.origin();
        }

        /** Which of its fork's arms this is, by where the arm stands in the fork. */
        public int part() {
            return arm.part();
        }

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
            SourceConstructOrigin origin = origin();
            return origin != null && origin.isWritten() && origin.module().equals(module);
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
     * <p><b>Which comparison, and separately where a run through it is written down.</b> The
     * comparison is a construct of the tree that runs and stands there whether anything instruments
     * it or not; the site is an address the emitter issued, and its own account of itself is that it
     * is an address and not an identity. Held by the site alone, this said which comparison it was
     * about only for as long as every comparison anyone asked after was one the emitter had
     * numbered — which is the reading the arm side stopped making when an arm came to name its fork
     * rather than its probe.
     *
     * <p>One place and one way, so there is nothing here for a caller to pair wrongly. What pairs
     * the comparison with the site is {@link CoverageSites.Plan#outcomeOf}, which is the only maker
     * of one of these and takes the site from the plan that holds the comparison.
     *
     * <p><b>Only where a run through the comparison could be recorded.</b> The plan numbers a
     * comparison standing where a row can get to and where what it stands in answers a value, so a
     * comparison with no site is one in a position no run reaches. There is no place here for it and
     * no claim to make about it, which is the same rule an arm with no probe is refused a claim by.
     *
     * @param comparison which comparison of the tree that runs
     * @param at         where this plan records a run through it
     * @param held       the way it came out
     */
    record Outcome(ConstructOccurrence comparison, ComparisonEmissionSite at, boolean held)
            implements ControlPlace {

        public Outcome {
            if (comparison == null) {
                throw new IllegalArgumentException(
                        "a comparison coming out one way is some comparison");
            }
            if (at == null) {
                throw new IllegalArgumentException(
                        "a place a comparison comes out one way is a place");
            }
        }

        @Override
        public String toString() {
            return comparison + (held ? " holds" : " fails");
        }
    }
}
