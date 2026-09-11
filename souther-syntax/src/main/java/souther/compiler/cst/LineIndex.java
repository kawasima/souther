package souther.compiler.cst;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps an absolute character offset to a line and a column and back. The CST works in offsets (what
 * green widths accumulate to); a renderer and the LSP work in lines and columns. This is the one
 * place the two meet.
 *
 * <p>Lines and columns are 1-based here (the compiler's convention); the LSP accessors return
 * 0-based ones. Columns count UTF-16 code units, which matches both Java {@code char} offsets and
 * the LSP's default position encoding.
 *
 * <p>It knows nothing of syntax, and where a place in a program is — which of the things written in
 * a text it is — is not a question it can answer. That is {@link SourceLayout}'s, which holds one of
 * these for the half of its work that is arithmetic over a string.
 */
public final class LineIndex {

    private final String source;
    /** {@code lineStart.get(i)} is the offset at which line {@code i} (0-based) begins. */
    private final List<Integer> lineStart;

    public LineIndex(String source) {
        this.source = source;
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        this.lineStart = List.copyOf(starts);
    }

    /** The 1-based line containing {@code offset}. */
    public int lineOf(int offset) {
        return lineIndex(offset) + 1;
    }

    /** The 1-based column of {@code offset} within its line. */
    public int columnOf(int offset) {
        return offset - lineStart.get(lineIndex(offset)) + 1;
    }

    /** The 0-based line of {@code offset} (LSP). */
    public int lspLine(int offset) {
        return lineIndex(offset);
    }

    /** The 0-based column of {@code offset} within its line (LSP, UTF-16 units). */
    public int lspColumn(int offset) {
        return offset - lineStart.get(lineIndex(offset));
    }

    /** The offset of a 0-based (line, column) LSP position, clamped into the source. */
    public int offsetOf(int lspLine, int lspColumn) {
        if (lspLine < 0) {
            return 0;
        }
        if (lspLine >= lineStart.size()) {
            return source.length();
        }
        int base = lineStart.get(lspLine);
        int lineEnd = lspLine + 1 < lineStart.size() ? lineStart.get(lspLine + 1) : source.length();
        return Math.min(base + Math.max(0, lspColumn), lineEnd);
    }

    /** Two indexes of one text are one index. Said because one of these travels in what a
     *  compilation remembers, and what a compilation remembers is a value. */
    @Override
    public boolean equals(Object other) {
        return other instanceof LineIndex it && source.equals(it.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    private int lineIndex(int offset) {
        int lo = 0;
        int hi = lineStart.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStart.get(mid) <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
