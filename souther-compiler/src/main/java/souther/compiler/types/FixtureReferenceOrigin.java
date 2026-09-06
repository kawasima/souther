package souther.compiler.types;

/**
 * A reference the generator composed, which no source wrote and nothing derived from one.
 *
 * <p>A row offered for a position names a value the module already states, and that name is a
 * reference: it reaches a declaration, and two of them are two. No author wrote it — the row is
 * this compiler's — and there is no construct of a source behind it either, the way the operation
 * an empty collection stands for has the collection. The occurrence begins when the generator
 * composes it, so what says which one it is, is the generator saying so.
 *
 * <p><b>Why a count is an identity here and is not elsewhere.</b> A count over a walk is refused
 * for the things a source wrote, because those have an occurrence that outlives any one walk: the
 * same construct is met in a different order under a different policy, and an identity that moved
 * with the order would tell one occurrence as two. Here there is no such occurrence to be faithful
 * to. This reference exists because the generator made it, and the authority that made it is the
 * one that can say which it is.
 *
 * <p><b>What {@code ordinal} does and does not promise.</b> It tells one composed reference from
 * another within one run of the generator, and nothing else. It is not stable against an edit to
 * the source; it is not the order the rows are offered in, nor a {@link souther.compiler.partition.RowId};
 * and it is not the name reached, which is what the reach name already answers. A reference
 * composed for a candidate the check then turned down was an occurrence all the same and keeps its
 * number — the numbering is over what was composed, not over what became a row.
 *
 * <p>Its own arm, and not a place for anything a pass composes. What is here was found by asking of
 * every producer of a name whether it has a source behind it, and this was the one that has none.
 * A producer added later that also has none is a question about what that one's occurrences are,
 * and the way to be asked it is an arm of its own that the compiler demands.
 *
 * @param ordinal  which reference this run has composed, by the run's own count over them
 */
public record FixtureReferenceOrigin(int ordinal) implements ReferenceOrigin {

    public FixtureReferenceOrigin {
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "what a run composed is counted from zero: " + ordinal);
        }
    }

    @Override
    public String toString() {
        return "fixture#ref" + ordinal;
    }
}
