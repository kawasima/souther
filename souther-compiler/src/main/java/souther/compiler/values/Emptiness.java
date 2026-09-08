package souther.compiler.values;

/**
 * Whether a position admits anything, as far as that can be said without building.
 *
 * <p>Three answers and not two, because a reading that has said what a position admits has not
 * always worked out what that comes to. A plan naming two patterns admits whatever their strings
 * have in common, and which strings those are is a machine somebody has to make — so until it is
 * made, the honest answer is neither yes nor no.
 *
 * <p><b>{@link #UNDECIDED} is not "no".</b> A reader that took it for one would drop a branch
 * nothing had shown impossible, and one that took it for the other would keep a branch and call the
 * reading exact. What it means is that the decision has to wait: the whole of what the position
 * admits is not described yet, and deciding now would decide on less than the rules say.
 *
 * <p>Which is the whole reason it exists. Answered as a boolean, the only way to be honest was to
 * build the machine there and then — and what a branch costs to decide would depend on where the
 * author put the brackets, since what is in hand at that moment is what the walk happened to have
 * reached.
 *
 * <p><b>What one of these means is worked out here and asked of here.</b> Every reading of one is
 * written as a switch over all of them, so an answer added to the three has to be told what it
 * means to each of these before anything compiles. Written as a comparison against a constant, each
 * of them would hand a fourth answer whichever meaning the comparison happened to leave it, and
 * nobody would have decided that. A reader outside asks one of these operations, and
 * {@link Alternatives} for the one reading that is about two of them at once.
 */
public enum Emptiness {

    /** Nothing is admitted, and that is settled. */
    EMPTY,

    /** Something is admitted, and that is settled. */
    NONEMPTY,

    /** Neither, until what the position admits has been worked out. */
    UNDECIDED;

    /** Whether this is the settled answer that nothing is admitted. */
    public boolean isEmpty() {
        return switch (this) {
            case EMPTY -> true;
            case NONEMPTY, UNDECIDED -> false;
        };
    }

    /**
     * Whether the question was answered at all, either way.
     *
     * <p>The word this enum already has for the third answer, said of the other two. A reader
     * waiting on a decision is asking this and not which of the settled answers it is: what it does
     * next turns on whether anybody has looked, and both of the settled answers are somebody having
     * looked.
     */
    public boolean isDecided() {
        return switch (this) {
            case EMPTY, NONEMPTY -> true;
            case UNDECIDED -> false;
        };
    }

    /**
     * What two that have to hold together come to.
     *
     * <p>Nothing is admitted where either admits nothing, and something where both admit something.
     * Where one is settled that something is admitted and the other is not known, the two together
     * are what the second one is — which is not known either, since what each of them says is about
     * a different part of the same value.
     */
    public Emptiness met(Emptiness other) {
        return switch (this) {
            case EMPTY -> EMPTY;
            case NONEMPTY -> switch (other) {
                case EMPTY -> EMPTY;
                case NONEMPTY -> NONEMPTY;
                case UNDECIDED -> UNDECIDED;
            };
            case UNDECIDED -> switch (other) {
                case EMPTY -> EMPTY;
                case NONEMPTY, UNDECIDED -> UNDECIDED;
            };
        };
    }

    /**
     * What a choice between two comes to.
     *
     * <p>Something is admitted where either side admits something, and nothing where both admit
     * nothing. Where one is settled empty and the other is not known, the choice is what the other
     * one is — which is not known either.
     */
    public Emptiness joined(Emptiness other) {
        return switch (this) {
            case NONEMPTY -> NONEMPTY;
            case EMPTY -> switch (other) {
                case EMPTY -> EMPTY;
                case NONEMPTY -> NONEMPTY;
                case UNDECIDED -> UNDECIDED;
            };
            case UNDECIDED -> switch (other) {
                case NONEMPTY -> NONEMPTY;
                case EMPTY, UNDECIDED -> UNDECIDED;
            };
        };
    }

    /**
     * Which alternatives of a choice anybody can still be in.
     *
     * <p>A branch stands unless something showed it empty, so {@link #UNDECIDED} stands: nobody
     * has shown that nothing satisfies it, and a walk that dropped it would be dropping a branch on
     * the strength of not having looked. That rule is written here and nowhere else — it is the one
     * thing every reader of a choice needs before it can do anything, and read off the constants at
     * each of them it would be as many rules as there are readers.
     *
     * <p><b>A reading of two answers, and not a settlement of a choice.</b> What this says is which
     * of the alternatives are still candidates. What a choice then leaves, which branch the caller
     * takes, whether the values are merged or held apart, whether the question waits — each of
     * those is the caller's, and each of them differs by what the caller is composing. So there is
     * no branch here to be handed one, nothing generic to fold with, and no way to ask this which
     * alternative to take: a choice one alternative of which stands leaves that alternative, which
     * the caller has in hand, and an operation for saying so would be a second place composing it.
     *
     * <p><b>Which alternatives stand and whether the answers are final are two questions.</b> A
     * branch nobody has worked out stands, so a choice both of whose branches stand may still be
     * one the answers have not settled — {@link #isDecided()} says that, of one answer at a time.
     * Held as one word, the two would be a product, and a third question about a choice would
     * multiply it again.
     */
    public enum Alternatives {

        /** Nobody can be in either of them. */
        NEITHER_STANDS,

        /** The left alternative, and nobody can be in the right. */
        ONLY_THE_LEFT,

        /** The right alternative, and nobody can be in the left. */
        ONLY_THE_RIGHT,

        /** Both are alternatives somebody may still be in. */
        BOTH_STAND;

        /** Which of two alternatives, read in the order they were written, still stand. */
        public static Alternatives of(Emptiness left, Emptiness right) {
            boolean here = stands(left);
            boolean there = stands(right);
            if (here) {
                return there ? BOTH_STAND : ONLY_THE_LEFT;
            }
            return there ? ONLY_THE_RIGHT : NEITHER_STANDS;
        }

        /**
         * Whether there is a choice here at all.
         *
         * <p>What a reader composing two things and not four asks: an occurrence one alternative of
         * which nobody can be in is no choice there, and the three ways that happens are one answer
         * to such a reader. Said once, so that an alternative topology added later is told here
         * whether it is a choice rather than at each of them.
         */
        public boolean bothStand() {
            return switch (this) {
                case NEITHER_STANDS, ONLY_THE_LEFT, ONLY_THE_RIGHT -> false;
                case BOTH_STAND -> true;
            };
        }

        /**
         * Whether anybody may still be in an alternative this is the answer about.
         *
         * <p>Not published. What a settled answer means to an alternative is this classification's,
         * and a name for it beside the answers themselves would say that "empty" and "nobody can be
         * in it" are one thing wherever an {@code Emptiness} is read. They are one thing here.
         */
        private static boolean stands(Emptiness said) {
            return switch (said) {
                case EMPTY -> false;
                case NONEMPTY, UNDECIDED -> true;
            };
        }
    }
}
