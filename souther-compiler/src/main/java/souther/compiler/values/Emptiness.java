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
 * {@link SidesShownEmpty} where the question is about two answers at once: which of the two were
 * shown empty is the observation, and what a connective does about that is the connective's
 * ({@link Alternatives} for a choice).
 *
 * <p><b>And what an operation over many of them starts from and can no longer be moved from is the
 * operation's.</b> A walk that seeded itself with a constant, or stopped on reaching one, would be
 * saying where {@link #met} or {@link #joined} begins and ends in its own words — true of the three
 * by arithmetic nobody wrote down, and left standing by an answer added to them. So each operation
 * is asked ({@link #identityForMeet} and {@link #endsAMeet}, {@link #identityForJoin} and
 * {@link #endsAJoin}), and a walk is told nothing about which answers those come to.
 */
public enum Emptiness {

    /** Nothing is admitted, and that is settled. */
    EMPTY,

    /** Something is admitted, and that is settled. */
    NONEMPTY,

    /** Neither, until what the position admits has been worked out. */
    UNDECIDED;

    /**
     * Whether this is the settled answer that nothing is admitted.
     *
     * <p>A fact about the answer, and not about whether an operation can still be moved from it. A
     * walk holding what it has taken in so far asks {@link #endsAMeet} or {@link #endsAJoin}, which
     * are about the arithmetic it is walking under: that those two and this agree on any of the
     * three is the arithmetic's doing, and a walk reading it here would be deciding the shape of an
     * operation from one of its answers.
     */
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
     *
     * <p><b>Every pair is answered, and none of them from the answer on one side alone.</b> An
     * answer that settles this whatever stands beside it settles it because of what the other
     * answers are, and an arm written to say so would go on saying it about an answer added to the
     * three — which is the one thing nobody would have decided. So each answer is asked about each
     * of them, and an answer added here is a row and a column and not a row.
     */
    public Emptiness met(Emptiness other) {
        return switch (this) {
            case EMPTY -> switch (other) {
                case EMPTY -> EMPTY;
                case NONEMPTY -> EMPTY;
                case UNDECIDED -> EMPTY;
            };
            case NONEMPTY -> switch (other) {
                case EMPTY -> EMPTY;
                case NONEMPTY -> NONEMPTY;
                case UNDECIDED -> UNDECIDED;
            };
            case UNDECIDED -> switch (other) {
                case EMPTY -> EMPTY;
                case NONEMPTY -> UNDECIDED;
                case UNDECIDED -> UNDECIDED;
            };
        };
    }

    /**
     * What nothing at all held together comes to, which is where a walk over such a pairing starts.
     *
     * <p>The answer {@link #met} leaves whatever it is put beside, so a walk that has taken nothing
     * in yet stands where this does. Asked of the operation and not written at the walk: which
     * answer that is follows from what holding things together means, and a walk naming it would be
     * saying the arithmetic's own fact in the walk's words.
     *
     * <p>Which is not the same fact as what an empty conjunction admits. That nothing was read
     * leaves every value at every position, and the reading saying so is the reading's — this says
     * only that such a reading is what the arithmetic starts from.
     */
    public static Emptiness identityForMeet() {
        return NONEMPTY;
    }

    /**
     * Whether anything held together with this could come to anything else.
     *
     * <p>What a walk asks to stop early: an answer this cannot be moved from is the answer, and the
     * conjuncts after it are conjuncts nobody has to look at. A fact about {@link #met} and not
     * about which of the three this is — said the second way it would claim the same answer settles
     * every operation, which {@link #joined} is the counterexample to.
     */
    public boolean endsAMeet() {
        return switch (this) {
            case EMPTY -> true;
            case NONEMPTY, UNDECIDED -> false;
        };
    }

    /**
     * What a choice between two comes to.
     *
     * <p>Something is admitted where either side admits something, and nothing where both admit
     * nothing. Where one is settled empty and the other is not known, the choice is what the other
     * one is — which is not known either.
     *
     * <p>Every pair is answered here too, and for the reason {@link #met} says.
     */
    public Emptiness joined(Emptiness other) {
        return switch (this) {
            case EMPTY -> switch (other) {
                case EMPTY -> EMPTY;
                case NONEMPTY -> NONEMPTY;
                case UNDECIDED -> UNDECIDED;
            };
            case NONEMPTY -> switch (other) {
                case EMPTY -> NONEMPTY;
                case NONEMPTY -> NONEMPTY;
                case UNDECIDED -> NONEMPTY;
            };
            case UNDECIDED -> switch (other) {
                case EMPTY -> UNDECIDED;
                case NONEMPTY -> NONEMPTY;
                case UNDECIDED -> UNDECIDED;
            };
        };
    }

    /**
     * What a choice between nothing at all comes to, which is where a walk over one starts.
     *
     * <p>{@link #identityForMeet}'s counterpart, and owned here for the same reason: the answer
     * {@link #joined} leaves whatever it is put beside is the arithmetic's fact about itself.
     */
    public static Emptiness identityForJoin() {
        return EMPTY;
    }

    /**
     * Whether a further alternative could reach anything a choice ending in this has not.
     *
     * <p>{@link #endsAMeet}'s counterpart, and a different answer: what a choice cannot be moved
     * from is not what a conjunction cannot be moved from, and a walk that asked one question for
     * both would be reading the alternatives under the conjunction's arithmetic.
     */
    public boolean endsAJoin() {
        return switch (this) {
            case NONEMPTY -> true;
            case EMPTY, UNDECIDED -> false;
        };
    }

    /**
     * Which of two answers were shown empty, in the order they were written.
     *
     * <p>The observation two connectives share, and nothing either of them does with it. A conjunct
     * shown empty decides the conjunction and carries its proof forward; an alternative shown empty
     * drops out of a choice and its proof goes with it. So what is held here is the sides, and the
     * valuing of them is not: named for which side survives, this would be the choice's reading
     * under a word a conjunction takes backwards, and the four cases are the same four either way.
     *
     * <p><b>Whether one answer was shown empty is {@link #isEmpty()}'s, and this holds which of the
     * two gave that answer.</b> So {@link #UNDECIDED} is not on the shown-empty side because
     * {@link #isEmpty()} says it is not — nobody has shown that nothing satisfies such an answer, and
     * a reader that sorted it with {@link #EMPTY} would be acting on not having looked. Nothing here
     * decides that a second time, and an answer added to the three is told which side it falls on
     * there, before anything compiles.
     *
     * <p>What this owns is the lifting of that decision to two answers: the sides, kept in the order
     * they were written. Read off the constants at each reader of a pair, the sides would be as many
     * rules as there are readers.
     *
     * <p><b>And falling on one side of this is not being one answer.</b> {@link #NONEMPTY} and
     * {@link #UNDECIDED} were both not shown empty, which is the whole of what this says about
     * them: whether the question was answered at all is {@link #isDecided()}, and a reader that
     * took this classification for what the two answers are would call a reading exact that is
     * waiting on a decision.
     */
    public enum SidesShownEmpty {

        /** Neither of them was shown empty. */
        NEITHER,

        /** The left, and nothing showed the right empty. */
        THE_LEFT,

        /** The right, and nothing showed the left empty. */
        THE_RIGHT,

        /** Both of them. */
        BOTH;

        /** Which of two answers, read in the order they were written, were shown empty. */
        public static SidesShownEmpty of(Emptiness left, Emptiness right) {
            if (left.isEmpty()) {
                return right.isEmpty() ? BOTH : THE_LEFT;
            }
            return right.isEmpty() ? THE_RIGHT : NEITHER;
        }
    }

    /**
     * Which alternatives of a choice anybody can still be in.
     *
     * <p>One reading of {@link SidesShownEmpty}, which is where a branch standing unless something
     * showed it empty is settled. What is added here is the choice's own valuing of that
     * observation, and it runs the other way from the observation's sides — a side shown empty is
     * the side that drops.
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

        /**
         * Which alternatives stand, read off which sides were shown empty.
         *
         * <p>An exchange, and the one place it is written: the side that was shown empty is the side
         * that drops, so what stands is the other one. Carried across as the same word, the
         * observation's left would name the alternative a choice has lost.
         */
        public static Alternatives from(SidesShownEmpty shown) {
            return switch (shown) {
                case NEITHER -> BOTH_STAND;
                case THE_LEFT -> ONLY_THE_RIGHT;
                case THE_RIGHT -> ONLY_THE_LEFT;
                case BOTH -> NEITHER_STANDS;
            };
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
    }
}
