package souther.compiler.types;

/**
 * One construct of one body, wherever a reader meets it: which construct the source wrote, and which
 * copy of it this is.
 *
 * <p>Which materialisation of a construct a reader is holding, in the tree that reader walks. A
 * rule read off it, a line drawn on it and a run recorded at it are three readers of one place, and
 * each needs an answer to "which one is this" — the two representations of a body are two
 * expansions, and each of them copies what it copies.
 *
 * <p><b>Not what the two trees agree about, and never join two readings on it.</b> A body whose
 * language operations stand and one whose operations have been expanded into what they do copy
 * different things, so a construct inside such an operation has a lineage in one tree and is absent
 * from the other. What the two agree about is which construct <em>of the model</em> a
 * materialisation is one of, which is a partial projection of this
 * ({@link ModelOccurrence#statedAt}) — partial because a copy made inside a library operation is
 * that operation's and no construct the caller's model states.
 *
 * <p><b>Neither half answers on its own.</b> The origin alone puts every copy of a spliced helper's
 * construct under one name, so a reading of one call's copy would be a reading of the other's. The
 * lineage alone puts every construct of one copy under one name.
 *
 * <p><b>Not where a run through it is recorded.</b> That is a number the emitter hands out over the
 * tree it emits, and only for what it instruments — a construct behind an abort has none. Held as
 * one value with this, "which construct is this" would be as complete as "what was measured about
 * it", and a construct nothing measures would have no name.
 *
 * <p><b>And not where it stands.</b> Which fork it was read under, which names were in force, what a
 * row had satisfied to get there: those are facts about a position in one tree and are the readings'
 * to answer. A key holding any of them files one construct under several.
 *
 * @param origin  which construct of which owner the source wrote
 * @param lineage which copy of it this is
 */
public record ConstructOccurrence(SourceConstructOrigin origin, ExpansionLineage lineage) {

    public ConstructOccurrence {
        if (origin == null || lineage == null) {
            throw new IllegalArgumentException(
                    "a construct of a body is some construct, in some copy of the body that wrote"
                            + " it: " + origin + " in " + lineage);
        }
    }

    /** The construct as the source wrote it, in the body that wrote it. */
    public static ConstructOccurrence asWritten(SourceConstructOrigin origin) {
        return new ConstructOccurrence(origin, ExpansionLineage.ORIGINAL);
    }

    /**
     * A construct no source wrote, which is one nothing is owed for.
     *
     * <p>In no copy, because there is nothing it is a copy of: what a pass composes it composes
     * where it stands. Put under the copy it happens to have been composed inside, it would answer
     * that a construct exists there per call of the body — which is true of what the source wrote
     * and says nothing about this.
     */
    public static ConstructOccurrence unwritten() {
        return UNWRITTEN;
    }

    private static final ConstructOccurrence UNWRITTEN =
            new ConstructOccurrence(SourceConstructOrigin.unwritten(), ExpansionLineage.ORIGINAL);

    /** Whether the source wrote this at all, which is what its origin says. */
    public boolean isWritten() {
        return origin.isWritten();
    }

    @Override
    public String toString() {
        return lineage instanceof ExpansionLineage.Original ? String.valueOf(origin)
                : origin + " in " + lineage;
    }
}
