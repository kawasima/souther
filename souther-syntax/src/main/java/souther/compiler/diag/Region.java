package souther.compiler.diag;


/**
 * A source range from {@code start} (inclusive) to {@code end} (exclusive), each a
 * {@link SourcePos}. A single token is {@code start} plus its text length; when only a point is
 * known, {@code start == end}.
 *
 * <p>Everything here is measured in UTF-16 code units, because that is what a {@link SourcePos}
 * measures a place in — what the LSP exchanges and what a JSON diagnostic publishes. It is not a
 * count of screen columns and not a number of characters to draw. A full-width character is one unit
 * and two columns, so a caller that handed a screen width to {@link #ofWidth} would move the end of
 * a published range by the wrong amount. Turning any of this into a place on the screen is
 * {@code HumanRenderer}'s, and it needs the source line to do it.
 *
 * <p>How wide a region is on a line is not asked here. A region says which of the things written in
 * a text it runs between, and how far apart those are is a fact about how that text is laid out
 * now — {@code SourceLayout.resolve} answers with a {@link PhysicalRegion}, which is what a renderer
 * measures.
 */
public record Region(SourcePos start, SourcePos end) {

    /** A zero-width region at a single point. */
    public static Region point(SourcePos p) {
        return new Region(p, p);
    }

    /** A region beginning at {@code start} and running {@code width} UTF-16 code units along the
     * same line — the length of the text it covers, not the room that text takes on a screen. The
     * end is in the file the start is in: a region does not leave the source it began in. */
    public static Region ofWidth(SourcePos start, int width) {
        return new Region(start, start.along(Math.max(0, width)));
    }

    /**
     * Whether {@code inner} lies within {@code outer}, ends allowed to meet — and vacuously so where
     * either of them is nowhere, there being no two places to disagree about.
     *
     * <p>What a caller holding a form and something written inside it checks: an expression and the
     * name it consists of, a construction and the field it sets. Regions in two files are not one
     * inside the other however their numbers compare.
     */
    public static boolean encloses(Region outer, Region inner) {
        if (outer == null || inner == null) {
            return true;
        }
        if (!outer.start.isInTheSameTextAs(inner.start)) {
            return false;
        }
        return !inner.start.isBefore(outer.start) && !outer.end.isBefore(inner.end);
    }

}
