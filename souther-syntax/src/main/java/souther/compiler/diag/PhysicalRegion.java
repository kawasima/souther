package souther.compiler.diag;

/**
 * A {@link Region} resolved against the text as it now stands: where it starts and where it ends, in
 * lines and columns.
 *
 * <p>What a renderer underlines and what an editor selects. Held only for as long as it takes to
 * draw: the numbers are of the text that was resolved against, and the region itself is what
 * survives an edit that moved it.
 */
public record PhysicalRegion(PhysicalPos start, PhysicalPos end) {

    /**
     * How much of the start line the region covers, in UTF-16 code units, and at least one.
     *
     * <p>A region that ends on a later line answers one. How much of the first line such a region
     * covers is not written down anywhere here, and no reader has needed it: the one caller cuts
     * this many units out of the line and measures what it cut.
     */
    public int sourceSpan() {
        if (end.line() != start.line()) {
            return 1;
        }
        return Math.max(1, end.column() - start.column());
    }
}
