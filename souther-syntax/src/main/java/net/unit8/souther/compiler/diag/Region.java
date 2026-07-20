package net.unit8.souther.compiler.diag;


/**
 * A source range from {@code start} (inclusive) to {@code end} (exclusive), each a
 * {@link SourcePos}. A single token is {@code start} plus its text length; when only a point is
 * known, {@code start == end} and the renderer underlines one column.
 */
public record Region(SourcePos start, SourcePos end) {

    /** A zero-width region at a single point. */
    public static Region point(SourcePos p) {
        return new Region(p, p);
    }

    /** A region beginning at {@code start} and spanning {@code width} columns on the same line. */
    public static Region ofWidth(SourcePos start, int width) {
        int w = Math.max(0, width);
        return new Region(start, new SourcePos(start.line(), start.column() + w));
    }

    /** The number of caret characters to draw on the start line: at least one. */
    public int caretWidth() {
        if (end.line() != start.line()) {
            return 1;
        }
        return Math.max(1, end.column() - start.column());
    }
}
