package souther.compiler.values;

/**
 * Whether working a reading out left a position nobody could build.
 *
 * <p>What {@link Unbuilt} holds, projected onto the only thing an answer about the reading turns
 * on. A shortfall is one occurrence — a position, and what was refused there, owed to a rule or to
 * the answer — and a report is written out of those. What a verdict turns on is none of that: a
 * position left holding every value is one the walk found no refusal at because nothing asked, and
 * whether there is such a position is the whole of what the verdict has to know.
 *
 * <p><b>Two answers and not a count.</b> A second position nobody could build takes nothing further
 * off an answer already waiting on the first, so a number here would be a difference no reader
 * could spend. What is asked is existential.
 *
 * <p><b>And this owns the one place where that meets a verdict.</b> A reading short of a position
 * stands for more values than its rules do, so a settled positive answer about it is about less
 * than the rules say and is not settled; a settled empty one is settled all the same, since a
 * narrower reading refuses no less. Both of those are {@link Emptiness#met} against the answer this
 * embeds into — the identity where nothing was left unbuilt, and the answer nobody has worked out
 * where something was. Written at a reader instead, the two would be a rule about verdicts kept
 * somewhere that holds no verdicts, and an answer added to the three would fall through it.
 */
public enum LeftUnbuilt {

    /** Every position the reading names was worked out. */
    NOTHING(Emptiness.identityForMeet()),

    /** At least one of them was not, and stands for every value because of it. */
    A_POSITION(Emptiness.UNDECIDED);

    private final Emptiness limit;

    LeftUnbuilt(Emptiness limit) {
        this.limit = limit;
    }

    /**
     * What {@code answer} comes to once what the reading could not build is known.
     *
     * <p>The embedding and the meet together, so that the answer this becomes is never in anybody's
     * hand. Handed out on its own it would read as a verdict about the reading, which it is not:
     * nothing here has looked at what the reading admits.
     */
    public Emptiness hold(Emptiness answer) {
        return answer.met(limit);
    }

    /**
     * What two readings asked as one were left with.
     *
     * <p>A composition of readings is short of a position where either of them is. Named for the
     * meet because that is what it is: this embeds into {@link Emptiness#met} on both sides, so
     * what two of these come to holds the answer that what they come to under the word does, and a
     * caller may take either route.
     */
    public LeftUnbuilt met(LeftUnbuilt other) {
        return switch (this) {
            case NOTHING -> other;
            case A_POSITION -> A_POSITION;
        };
    }
}
