package souther.compiler.check;

import java.util.concurrent.atomic.LongAdder;

/**
 * What this compiler has done with the choices it read, counted as it read them.
 *
 * <p>Counted rather than timed, for the same reason {@link InvariantChecker#readingsMade()} is: what
 * a caller is held to here is that a workload reaches the reading of a choice at all, and reaches it
 * in the shape the workload was written to have — which is a fact about the reading and not about
 * how long it took. A measurement of the time would pass on a workload that never arrived.
 *
 * <p><b>What is published is what became of a choice, not which method ran.</b> Every figure below
 * is a sentence about the model: how many choices an author wrote were taken in, how many of them
 * the descriptions alone settled, how many places distribution put a branch, and what was left
 * standing. None of them names a walk, so two walks fused into one — or one split in two — leave
 * every figure where it was.
 *
 * <p><b>Nothing is charged to the walk it counts.</b> A place a branch stands in is counted in a
 * plain field of the {@link Tally} that reading holds, and the totals are added once the reading is
 * a value ({@link Tally#publish()}). Written straight to a shared counter, an instrument that counts
 * occurrences would put a synchronised write on the very walk whose cost is what a measurement of
 * this is about — growing with the same axis the measurement varies, which does not shift a curve
 * so much as bend it.
 *
 * <p><b>What a reader may take a difference of.</b> Each figure is published as its reading
 * finishes, and {@link #snapshot()} reads them one after another, so what comes back is what has
 * been published rather than the state of any single instant. Two snapshots either side of one
 * compile are a reading of that compile where the process is compiling one thing at a time, and are
 * a reading of whatever else was running otherwise. This is an instrument for a test and for a
 * measurement, both of which run a compile and wait for it.
 *
 * <p><b>And it stops here.</b> Seven monotonic figures and one snapshot; nothing that says where
 * time went, nothing keyed by declaration, nothing a report is built out of. What a reader wants of
 * a compile beyond this is a measurement, and measurements are taken outside the compiler.
 */
public final class ChoicesRead {

    private ChoicesRead() {}

    private static final LongAdder STATED = new LongAdder();
    private static final LongAdder SETTLED_OFF_DESCRIPTIONS = new LongAdder();
    private static final LongAdder PLACES_MET = new LongAdder();
    private static final LongAdder EVERY_ALTERNATIVE_STOOD = new LongAdder();
    private static final LongAdder ONE_ALTERNATIVE_STOOD = new LongAdder();
    private static final LongAdder NO_ALTERNATIVE_STOOD = new LongAdder();
    private static final LongAdder MERGED = new LongAdder();

    /**
     * What became of the choices read up to some moment, as one value.
     *
     * <p>One snapshot and not an accessor apiece. A reader comparing what a compile did against what
     * it did before takes two of these and subtracts, and figures fetched one at a time around a
     * running compile would be an observation of no single moment at all.
     *
     * @param stated choices an author wrote that a reading took in. One per written {@code ||},
     *               however many places distribution afterwards put its branches
     * @param settledOffDescriptions choices decided before anything was built, out of what the
     *                               descriptions of the branches already showed. What was carried to
     *                               the settlement instead is the rest of {@code stated}
     * @param placesMet places a branch of a choice carried to the settlement stood once the clauses
     *                  written beside it had been distributed into it: one where nothing was met
     *                  with it, and the product of the alternatives of the choices it was met with
     *                  otherwise. A choice the descriptions settled has none — its branches were
     *                  answered before the tree the settlement walks was built
     * @param everyAlternativeStood choices both of whose alternatives somebody can be in, so the
     *                              choice is held open
     * @param oneAlternativeStood choices one alternative of which admits nothing, so the answer is
     *                            the other. Which one is not here: a choice that lost its left and
     *                            a choice that lost its right came to the same thing as far as what
     *                            a compile did with them goes, and a reader owed something per side
     *                            is a reader of the branch that is left rather than of this
     * @param noAlternativeStood choices no alternative of which admits anything, so the choice
     *                           admits nothing with none of its alternatives at fault
     * @param merged declarations read with their alternatives merged into the one product containing
     *               them, having expanded past what the policy holds apart
     */
    public record Snapshot(long stated, long settledOffDescriptions, long placesMet,
                           long everyAlternativeStood, long oneAlternativeStood,
                           long noAlternativeStood, long merged) {

        /** What has happened since {@code earlier}, which is what ran between the two. */
        public Snapshot since(Snapshot earlier) {
            return new Snapshot(stated - earlier.stated,
                    settledOffDescriptions - earlier.settledOffDescriptions,
                    placesMet - earlier.placesMet,
                    everyAlternativeStood - earlier.everyAlternativeStood,
                    oneAlternativeStood - earlier.oneAlternativeStood,
                    noAlternativeStood - earlier.noAlternativeStood,
                    merged - earlier.merged);
        }

        /** Choices the descriptions left open, which the settlement had to decide. */
        public long carriedToSettlement() {
            return stated - settledOffDescriptions;
        }
    }

    /** What became of every choice read so far. */
    public static Snapshot snapshot() {
        return new Snapshot(STATED.sum(), SETTLED_OFF_DESCRIPTIONS.sum(), PLACES_MET.sum(),
                EVERY_ALTERNATIVE_STOOD.sum(), ONE_ALTERNATIVE_STOOD.sum(),
                NO_ALTERNATIVE_STOOD.sum(), MERGED.sum());
    }


    /**
     * What one reading of one declaration did, gathered as it goes and published when it is done.
     *
     * <p>Plain fields and no synchronisation. One of these belongs to the reading that made it and
     * is written by nothing else, so what it costs the walk it counts is an increment of a local —
     * which is the whole of why the walk can be counted at all without the count being part of what
     * a measurement of the walk reports.
     */
    static final class Tally {

        private long stated;
        private long settledOffDescriptions;
        private long placesMet;
        private long everyAlternativeStood;
        private long oneAlternativeStood;
        private long noAlternativeStood;

        /**
         * Whether this reading holds its alternatives merged rather than apart.
         *
         * <p>Taken when the tally is made, and not told to it later. Which of the two a reading is
         * is settled before a clause is read, so there is nothing to wait for; told later, the
         * reading on one side of the guardrail would make a call the reading on the other side does
         * not, in the one series written to look for a step there.
         */
        private final boolean merged;

        Tally(boolean merged) {
            this.merged = merged;
        }

        /** One more place a branch stood, which is one node of the tree the settlement walks. */
        void placeMet() {
            placesMet++;
        }

        /** How many of this declaration's choices the descriptions alone settled. */
        void settledOffDescriptions(int settled) {
            settledOffDescriptions += settled;
        }

        /**
         * One written choice, and how its alternatives fell.
         *
         * <p>Told rather than asking. The reading walks the choices of each rule once, with the
         * fates applied, and works out how they fell because its own answer turns on it — so a
         * count taken here is the walk that is happening saying what it found, and a count taken
         * anywhere else is a second walk over the same choices. That would be an instrument doing
         * work in proportion to how many choices a declaration has, which is the axis a measurement
         * of what a choice costs varies.
         *
         * <p>Sorted into three where the word has four. Which side fell is the business of whatever
         * goes on reading the branch that is left; what a compile did with a choice is the same
         * either way, so a fifth way for two alternatives to fall arrives here as a case with
         * nowhere to go and says so.
         */
        void choiceCame(souther.compiler.values.Emptiness.Alternatives standing) {
            stated++;
            switch (standing) {
                case BOTH_STAND -> everyAlternativeStood++;
                case ONLY_THE_LEFT, ONLY_THE_RIGHT -> oneAlternativeStood++;
                case NEITHER_STANDS -> noAlternativeStood++;
            }
        }

        /**
         * What this reading did, added to what every reading before it did.
         *
         * <p>Nothing at all where the declaration stated no choice, which is nearly every
         * declaration anybody writes. A shared counter told that nothing happened is still a shared
         * counter written to, and a compile of a model with no choice in it would be paying an
         * instrument for the choices it does not have.
         *
         * <p>And where a declaration did state one, the same writes whichever way its alternatives
         * were held. A figure added only where it is not nought is a figure whose reading costs one
         * shared write more than the reading beside it — and the reading beside it is the other side
         * of the guardrail, which is what one of these series exists to measure the step at.
         */
        void publish() {
            if (stated == 0 && placesMet == 0 && !merged) {
                return;
            }
            STATED.add(stated);
            SETTLED_OFF_DESCRIPTIONS.add(settledOffDescriptions);
            PLACES_MET.add(placesMet);
            EVERY_ALTERNATIVE_STOOD.add(everyAlternativeStood);
            ONE_ALTERNATIVE_STOOD.add(oneAlternativeStood);
            NO_ALTERNATIVE_STOOD.add(noAlternativeStood);
            MERGED.add(merged ? 1 : 0);
        }
    }
}
