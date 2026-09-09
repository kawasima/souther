package souther.compiler.check;

import java.util.concurrent.atomic.AtomicLong;

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
 * the descriptions alone settled, how many places distribution put a branch, and how many
 * alternatives were left standing. None of them names a walk, so two walks fused into one — or one
 * split in two — leave every figure where it was.
 *
 * <p><b>And it stops here.</b> Seven monotonic counters and one snapshot; nothing that says where
 * time went, nothing keyed by declaration, nothing a report is built out of. What a reader wants of
 * a compile beyond this is a measurement, and measurements are taken outside the compiler.
 */
public final class ChoicesRead {

    private ChoicesRead() {}

    private static final AtomicLong STATED = new AtomicLong();
    private static final AtomicLong SETTLED_OFF_DESCRIPTIONS = new AtomicLong();
    private static final AtomicLong PLACES_MET = new AtomicLong();
    private static final AtomicLong EVERY_ALTERNATIVE_STOOD = new AtomicLong();
    private static final AtomicLong ONE_ALTERNATIVE_STOOD = new AtomicLong();
    private static final AtomicLong NO_ALTERNATIVE_STOOD = new AtomicLong();
    private static final AtomicLong MERGED = new AtomicLong();

    /**
     * What became of the choices read up to some moment, as one value.
     *
     * <p>One snapshot and not an accessor apiece. A reader comparing what a compile did against what
     * it did before takes two of these and subtracts, and figures read one at a time around a
     * running compile would be an observation of no single moment.
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
     *                            the other
     * @param noAlternativeStood choices no alternative of which admits anything, so the choice
     *                           admits nothing with none of its alternatives at fault
     * @param merged declarations read with their alternatives merged into the one product containing
     *               them, having expanded past what the policy holds apart
     */
    public record Snapshot(long stated, long settledOffDescriptions, long placesMet,
                           long everyAlternativeStood, long oneAlternativeStood,
                           long noAlternativeStood, long merged) {

        /** What has happened since {@code earlier}, which is what one compile between the two did. */
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
        return new Snapshot(STATED.get(), SETTLED_OFF_DESCRIPTIONS.get(), PLACES_MET.get(),
                EVERY_ALTERNATIVE_STOOD.get(), ONE_ALTERNATIVE_STOOD.get(),
                NO_ALTERNATIVE_STOOD.get(), MERGED.get());
    }

    /** One more place a branch stood, which is one node of the tree the settlement walks. */
    static void placeMet() {
        PLACES_MET.incrementAndGet();
    }

    /** A declaration whose alternatives were merged rather than held apart. */
    static void merged() {
        MERGED.incrementAndGet();
    }

    /**
     * What one declaration's choices came to, taken once its settlement is a value.
     *
     * <p>Read off the fates and not off the walk that made them. A fate is aggregated over every
     * place distribution put the branch, so a count taken per place would say a choice stood in one
     * place and not in another — which is a sentence about the walk and not about the choice.
     *
     * @param settledOffDescriptions how many of them were decided before anything was built
     */
    static void settled(Settlement settlement, int settledOffDescriptions) {
        STATED.addAndGet(settlement.outcomes().size());
        SETTLED_OFF_DESCRIPTIONS.addAndGet(settledOffDescriptions);
        for (Settlement.OfAChoice fate : settlement.outcomes().values()) {
            switch (souther.compiler.values.Emptiness.Alternatives.from(
                    souther.compiler.values.Emptiness.SidesShownEmpty.of(
                            fate.left().emptiness(), fate.right().emptiness()))) {
                case BOTH_STAND -> EVERY_ALTERNATIVE_STOOD.incrementAndGet();
                case ONLY_THE_LEFT, ONLY_THE_RIGHT -> ONE_ALTERNATIVE_STOOD.incrementAndGet();
                case NEITHER_STANDS -> NO_ALTERNATIVE_STOOD.incrementAndGet();
            }
        }
    }
}
