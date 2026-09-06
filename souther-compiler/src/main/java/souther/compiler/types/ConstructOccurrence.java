package souther.compiler.types;

/**
 * One construct of one body, wherever a reader meets it: which construct the source wrote, and which
 * copy of it this is.
 *
 * <p>What a reading of a construct joins on. A rule read off it, a line drawn on it and a run
 * recorded at it are three readers of one place, and each needs the same answer to "which one is
 * this" — an answer that holds across the representations of a body, because what a rule states is
 * read from the tree the operations stand in and where a run is recorded is numbered over the tree
 * that runs.
 *
 * <p><b>Neither half answers on its own.</b> The origin alone puts every copy of a spliced helper's
 * construct under one name, so a reading of one call's copy would be a reading of the other's. The
 * lineage alone puts every construct of one copy under one name. Together they are what the two
 * trees agree about, which is the whole of what this is for.
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
