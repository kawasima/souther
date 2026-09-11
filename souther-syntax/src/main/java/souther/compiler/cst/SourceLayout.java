package souther.compiler.cst;

import souther.compiler.source.SourceId;

import souther.compiler.diag.LaidOutText;
import souther.compiler.diag.PhysicalPos;
import souther.compiler.diag.Placement;
import souther.compiler.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;

/**
 * How one text is laid out: which meaningful tokens it is made of, and where each of them sits.
 *
 * <p>The one place a text becomes places, and the one place a place becomes a line and a column.
 * Both halves are here because both are about this text and nothing else: a place says which of the
 * things written here it is, and a line number says where that thing is at the moment. Worked out
 * somewhere else, the second would be a count of tokens made against one text and read against
 * another.
 *
 * <p>What it is an index of is the <b>meaningful</b> tokens. Whitespace, a line break and a comment
 * are not among them, which is what makes a place survive an edit that only writes those — and what
 * makes writing a meaningful token move every place after it, which is the conservative half
 * ({@link SourcePos}).
 *
 * <p>An offset that is not the start of a meaningful token belongs to the last one that started at
 * or before it. That is total: the end of a node is its last token's start plus that token's width,
 * so a comment written between two nodes is inside neither of them and moves neither.
 */
public final class SourceLayout implements LaidOutText {

    private final String source;

    private final Placement read;

    private final LineIndex lines;

    /** {@code tokenStart[i]} is the offset of the {@code i}-th meaningful token. Ascending. */
    private final int[] tokenStart;

    private SourceLayout(String source, Placement read, int[] tokenStart) {
        this.source = source;
        this.read = read;
        this.lines = new LineIndex(source);
        this.tokenStart = tokenStart;
    }

    /**
     * Two layouts of one text are one layout.
     *
     * <p>Said, because one of these travels in what a compilation remembers about a module it read
     * back, and what a compilation remembers is a value. Held as the object it happens to be, a
     * module read twice from one unchanged artifact would come back as a module that changed.
     *
     * <p>The tokens are not compared. They are what this text is made of, worked out here and
     * nowhere else, so two layouts of one text agree about them or this class is wrong.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof SourceLayout it && source.equals(it.source)
                && read.equals(it.read);
    }

    @Override
    public int hashCode() {
        return source.hashCode() * 31 + read.hashCode();
    }

    /** The layout of {@code text}, parsed to find its tokens. */
    public static SourceLayout of(String text, Placement read) {
        return of(CstParser.parse(text).root(), text, read);
    }

    /** The layout of a text this caller has no name for — a buffer, a snippet, a source a test
     *  wrote out. The places it makes name no file. */
    public static SourceLayout of(String text) {
        return of(text, Placement.aTextWithNoIdentity());
    }

    /** The layout of a file this compile holds, or of no named file when {@code sourceId} is null. */
    public static SourceLayout of(String text, SourceId sourceId) {
        return of(text, sourceId == null ? Placement.aTextWithNoIdentity()
                : Placement.aFileOfThisCompile(sourceId));
    }

    /** The same, for a caller that has already parsed the text and holds the tree. */
    public static SourceLayout of(SyntaxNode root, String text, Placement read) {
        List<Integer> starts = new ArrayList<>();
        collect(root, starts);
        int[] offsets = new int[starts.size()];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = starts.get(i);
        }
        return new SourceLayout(text, read, offsets);
    }

    private static void collect(SyntaxNode node, List<Integer> into) {
        for (SyntaxElement child : node.children()) {
            if (child instanceof SyntaxNode inner) {
                collect(inner, into);
            } else if (child instanceof SyntaxToken token && !token.isTrivia()) {
                into.add(token.start());
            }
        }
    }

    /** Which text this is the layout of. */
    public Placement read() {
        return read;
    }

    /**
     * Offsets into this text as lines and columns, and back.
     *
     * <p>For a caller working in the editor's own numbers — which is what an editor sends and what
     * it is sent. Nothing about the program is decided from these.
     */
    public LineIndex lines() {
        return lines;
    }

    /** Where {@code token} begins. */
    public SourcePos at(SyntaxToken token) {
        return placeAt(token.start());
    }

    /** Where {@code token} ends — the other end of the region it covers. */
    public SourcePos after(SyntaxToken token) {
        return placeAt(token.end());
    }

    /**
     * The place at {@code offset}: the meaningful token starting at or before it, and how far past
     * that token's start the offset is.
     *
     * <p>For a caller holding an offset and no token — a parser reporting where it stopped, an
     * editor asking what is under a cursor. A caller that has the token asks {@link #at}, which
     * cannot land one token out.
     */
    public SourcePos placeAt(int offset) {
        if (tokenStart.length == 0) {
            return new SourcePos(0, Math.max(0, offset), read);
        }
        int token = tokenAt(offset);
        return new SourcePos(token, offset - tokenStart[token], read);
    }

    /** Where {@code place} is in this text, in UTF-16 code units from its start. */
    public int offsetOf(SourcePos place) {
        if (tokenStart.length == 0) {
            return Math.max(0, place.within());
        }
        int token = Math.min(Math.max(place.token(), 0), tokenStart.length - 1);
        return tokenStart[token] + place.within();
    }

    /**
     * Where {@code place} sits in this text as it now stands.
     *
     * <p>Answered against this layout and never remembered. A caller holding the number after the
     * text has been written in again is holding a number about a text nobody has.
     */
    @Override
    public PhysicalPos resolve(SourcePos place) {
        int offset = offsetOf(place);
        return new PhysicalPos(lines.lineOf(offset), lines.columnOf(offset));
    }

    private int tokenAt(int offset) {
        int lo = 0;
        int hi = tokenStart.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (tokenStart[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
