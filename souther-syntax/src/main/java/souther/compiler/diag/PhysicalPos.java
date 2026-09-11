package souther.compiler.diag;

/**
 * A line and a column in some text, as that text stands now.
 *
 * <p>Where a reader is sent and what a file's line numbers are — what a renderer underlines, what an
 * editor scrolls to, what a class file's line table records, what a jar publishes for another
 * compilation to read back. Nothing decides anything about a program from one of these.
 *
 * <p>Worked out from a {@link SourcePos} and the text it is in ({@code SourceLayout}), and never
 * kept. A line number is what the text was laid out as when it was asked for; the same place in the
 * same program is a different line after somebody presses return above it, and a number remembered
 * across that is a number about a text nobody has any more. What may be remembered is the place,
 * which says which of the things written in that text it is and says nothing about where they sit.
 *
 * @param line the 1-based line
 * @param column the 1-based column, in UTF-16 code units
 */
public record PhysicalPos(int line, int column) {

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
