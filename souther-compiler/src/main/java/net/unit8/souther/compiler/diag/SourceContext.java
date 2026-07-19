package net.unit8.souther.compiler.diag;

/**
 * The source text a diagnostic points into, plus a display file name, so the renderer can quote the
 * offending line. Attached at the compilation-unit boundary rather than threaded through every AST
 * node, which keeps {@link net.unit8.souther.compiler.SourcePos} a plain line/column.
 */
public record SourceContext(String fileName, String text) {

    /** The 1-based {@code n}-th line of the source, or {@code null} when out of range or unknown. */
    public String line(int n) {
        if (text == null || n < 1) {
            return null;
        }
        String[] lines = text.split("\n", -1);
        if (n > lines.length) {
            return null;
        }
        String line = lines[n - 1];
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
