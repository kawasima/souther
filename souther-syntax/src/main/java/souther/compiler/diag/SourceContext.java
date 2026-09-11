package souther.compiler.diag;

/**
 * The source text a diagnostic points into, a display file name, and how that text is laid out, so
 * the renderer can quote the offending line and underline the right part of it.
 *
 * <p>Attached at the compilation-unit boundary rather than threaded through every AST node. The
 * layout comes with the text because it is about that text: a {@link SourcePos} says which of the
 * things written there it is, and turning that into a line and a column is a question only whoever
 * holds the text can answer. Handed one without the other, a renderer would be quoting a line number
 * it had to invent.
 *
 * @param fileName what to call the file in a report, or null where there is nothing to call it
 * @param text the source, or null where the report cannot quote one
 * @param laidOut where each of that text's places sits, or null where there is no text to ask
 */
public record SourceContext(String fileName, String text, LaidOutText laidOut) {

    /** A context with nothing to quote — a name for a file whose text the reporter does not hold. */
    public SourceContext(String fileName) {
        this(fileName, null, null);
    }

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

    /** Where {@code place} sits in this text, or null where there is no text to ask. */
    public PhysicalPos resolve(SourcePos place) {
        return laidOut == null || place == null ? null : laidOut.resolve(place);
    }

    /** The same, for both ends of a region. */
    public PhysicalRegion resolve(Region region) {
        return laidOut == null || region == null ? null : laidOut.resolve(region);
    }
}
