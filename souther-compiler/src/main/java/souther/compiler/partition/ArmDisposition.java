package souther.compiler.partition;

import souther.compiler.reading.PathAccess;

import java.util.List;

/**
 * What one run of the generator did about one arm it was asked for.
 *
 * <p>Which arm is the key it is filed under, the same way a class's is.
 *
 * <p>Three answers because they are three pieces of news. A row was composed; there is a reason
 * there is none; or there was nowhere to look at all, which the reading of the body settles and no
 * search is involved in. Told as one, a reader could not tell an arm the model refuses from one
 * this compiler could not state a way into.
 */
public sealed interface ArmDisposition {

    /**
     * A row was composed for a combination that takes it, or along the way into it.
     *
     * @param rowId which row
     * @param at    which place a run through the arm it was steered to is recorded at. An arm the
     *              author wrote stands in the running tree once per call site of the helper
     *              carrying it, and the row went down one of them — so a reader naming another
     *              would say the row does what it does not
     */
    record Built(RowId rowId, souther.compiler.coverage.ArmProbe at) implements ArmDisposition {

        public Built {
            if (rowId == null || at == null) {
                throw new IllegalArgumentException(
                        "an arm a row was composed for names the row and where it went");
            }
        }
    }

    /**
     * No row, and the reasons there is none.
     *
     * <p>Not only the reasons a search came back with. A run that never searched has one too — the
     * rows could not be read, the classes would not link — and it is as much an answer about this
     * arm as a refusal is. What this says is that there is no row and that the run can say why;
     * whether anything was tried is in the words, where {@code THE_ROWS_WERE_NOT_READ} and
     * {@code LINKAGE_FAILED} say it outright.
     *
     * <p>All of the reasons, because they are not one fact. One place stopping at the search's
     * budget and another the model's own rules refuse are different news — the first says a row may
     * still be writable and the second says the model settles it — and the arm is answered by the
     * whole of what was tried rather than by whichever was walked first.
     */
    record Unresolved(List<Generator.UnresolvedCombination> why) implements ArmDisposition {

        public Unresolved {
            why = List.copyOf(why);
            if (why.isEmpty()) {
                throw new IllegalArgumentException(
                        "an arm with no row and no reason for it is one nothing answered for");
            }
        }
    }

    /**
     * Nothing was tried, because the reading of the body has no way into this arm to try.
     *
     * <p>Which is two pieces of news and the reading says which: no run reaches the arm at all, or
     * this compiler cannot state what steers a row there. Neither is a search that failed, and
     * carrying either as one would tell a reader a value was looked for.
     *
     * <p><b>What the reading made of every place the arm stands in, and not one of them.</b> An arm
     * the author wrote stands in the running tree once per call site of the helper carrying it, and
     * the two are read separately: one splice may be somewhere the model proves no run reaches
     * while another is somewhere this compiler cannot name the way to. Carrying one, the answer was
     * whichever place the walk wrote first, and swapping two call sites of one body changed a fact
     * about the model into a shortfall of ours.
     *
     * @param access what the reading made of each place, each kind of answer once and in the order
     *               the plan records the places in
     */
    record NoWayIn(List<PathAccess> access) implements ArmDisposition {

        public NoWayIn {
            access = List.copyOf(access);
            if (access.isEmpty()) {
                throw new IllegalArgumentException(
                        "an arm nothing was tried at says what the reading made of it");
            }
            if (access.stream().anyMatch(PathAccess.Ways.class::isInstance)) {
                throw new IllegalArgumentException(
                        "an arm with ways into it is one this search had somewhere to look");
            }
        }

        /** An arm the caller has one place for, which is what a search stood up on its own has. */
        public NoWayIn(PathAccess access) {
            this(List.of(access));
        }

        /**
         * Whether the model settles every place of this arm, which is what says a reader may act
         * on it.
         *
         * <p>All of them and not any: a place this compiler could not read the way to is a place a
         * row may yet be written for, so one of those leaves the arm this compiler's shortfall
         * however many of the rest the model refuses.
         */
        public boolean theModelSettlesIt() {
            return access.stream().allMatch(PathAccess.Unreachable.class::isInstance);
        }
    }
}
