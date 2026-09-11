package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.PhysicalPos;
import souther.compiler.diag.PhysicalRegion;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;

/**
 * Where a place drawn on a source sits in it, for a test that is about the lines and columns a
 * reader is shown.
 *
 * <p>A place says which of the things written in a text it is, and what line that is at is what the
 * text is laid out as. So a test asking about a line says which text it means, and this is how it
 * says it. A test that is about the place itself — that two reports point at one thing, that one
 * region encloses another — compares the places and does not come here.
 *
 * <p>The text is laid out afresh on each call. A test holds a source of a few dozen lines and asks
 * about a handful of places in it, and one that asked about enough of them to notice would be a test
 * measuring the compiler rather than reading it.
 */
public final class WhereItSits {

    private WhereItSits() {
    }

    /** Where {@code place} sits in {@code source}. */
    public static PhysicalPos in(String source, SourcePos place) {
        return laidOutAs(source, place).resolve(place);
    }

    /** Where {@code region} runs from and to in {@code source}. */
    public static PhysicalRegion in(String source, Region region) {
        return laidOutAs(source, region.start()).resolve(region);
    }

    /**
     * {@code source} laid out as the text {@code place} is in.
     *
     * <p>Said, rather than laid out as a text with no name: a place in a file read against a layout
     * of another file is refused, and a test handing over the wrong source is what that refusal is
     * for. The text is the caller's to get right; which file it is being read as is not.
     */
    private static SourceLayout laidOutAs(String source, SourcePos place) {
        return SourceLayout.of(source, place.placement());
    }

    /**
     * The characters {@code region} covers in {@code source} — what a report drew under.
     *
     * <p>What most of these tests were spelling out of a line number, a column and a substring. Said
     * once here, a test that reads what was underlined says that and nothing about how the lines of
     * the file happen to fall.
     */
    public static String underlined(String source, Region region) {
        PhysicalRegion sits = in(source, region);
        String[] lines = source.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int line = sits.start().line(); line <= sits.end().line(); line++) {
            String text = lines[line - 1];
            int from = line == sits.start().line() ? sits.start().column() - 1 : 0;
            int to = line == sits.end().line() ? sits.end().column() - 1 : text.length();
            out.append(text, Math.min(from, text.length()), Math.min(to, text.length()));
            if (line < sits.end().line()) {
                out.append('\n');
            }
        }
        return out.toString();
    }
}
