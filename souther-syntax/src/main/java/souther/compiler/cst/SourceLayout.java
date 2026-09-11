package souther.compiler.cst;

import souther.compiler.source.SourceId;

import souther.compiler.diag.LaidOutText;
import souther.compiler.diag.PhysicalPos;
import souther.compiler.diag.Placement;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.SourceProvenance;

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

    /**
     * {@code tokenStart[c][t]} is the offset of the {@code t}-th meaningful token of the
     * {@code c}-th top-level construct. Ascending in both.
     *
     * <p>Counted per construct and not over the whole text, which is what keeps an edit inside one
     * body from moving the places of every declaration under it ({@link SourcePos}).
     */
    private final List<List<Integer>> tokenStart;

    /** Which text this is, asked once — a placement answers it and does not publish it. */
    private final QuotedFrom text;

    /**
     * Where each meaningful token sits, one line and column packed into a long — by construct, in
     * order, the way a place addresses them.
     *
     * <p>What this layout can be asked and the whole of it, so it is what one layout being another
     * comes to. Held rather than worked out on a comparison: what holds one of these compares it
     * against the one it replaces on every edit.
     *
     * <p>The constructs are kept apart rather than run together. A place names a token of a
     * construct, so where one construct ends and the next begins is part of what this answers:
     * flattened, two texts whose tokens sit in the same places but fall into constructs differently
     * came out equal while answering different lines for the same place, and what held a layout
     * would have kept the one it had.
     */
    private final List<List<Long>> sits;

    private SourceLayout(String source, Placement read, List<List<Integer>> tokenStart) {
        this.source = source;
        this.read = read;
        this.text = read.at(0, 0).quotedFrom();
        this.lines = new LineIndex(source);
        this.tokenStart = tokenStart;
        List<List<Long>> where = new ArrayList<>();
        for (List<Integer> construct : tokenStart) {
            List<Long> its = new ArrayList<>();
            for (int offset : construct) {
                its.add(((long) lines.lineOf(offset) << 32) | lines.columnOf(offset));
            }
            where.add(List.copyOf(its));
        }
        this.sits = List.copyOf(where);
    }

    /**
     * Two texts laid out the same way are one layout.
     *
     * <p>What this answers is where each of a text's places sits, so that is what two of them being
     * one comes to. Rewording a comment moves no token to another line or column, so the layout it
     * was read from is equal to the one it replaces, and what depends on the layout — a debug
     * table, a document that writes line numbers — is not worked out again for it. Compared by the
     * text, that dependency would be on everything the file says rather than on how it is laid
     * out, which is the wider question and not the one anything here asks.
     *
     * <p>Said at all because one of these travels in what a compilation remembers about a module it
     * read back, and what a compilation remembers is a value.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof SourceLayout it && read.equals(it.read) && sits.equals(it.sits);
    }

    @Override
    public int hashCode() {
        return sits.hashCode() * 31 + read.hashCode();
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

    /**
     * The same, for a caller that has already parsed the text and holds the tree.
     *
     * <p>What a construct is is read off the syntax and not off what the constructs come to mean:
     * a module header, an import line, a declaration and an {@code example} block each get one, and
     * so does anything the grammar grows later. Read off the semantic declarations instead, a place
     * in something the front end rewrites — an implicit unit, a desugared clause — would have to be
     * matched back to a construct that no longer stands in the same relation to it.
     */
    public static SourceLayout of(SyntaxNode root, String text, Placement read) {
        List<List<Integer>> constructs = new ArrayList<>();
        for (SyntaxElement child : root.children()) {
            if (!(child instanceof SyntaxNode construct)) {
                continue;   // a token at the top level is trivia between constructs
            }
            List<Integer> starts = new ArrayList<>();
            collect(construct, starts);
            if (starts.isEmpty()) {
                continue;   // nothing meaningful in it, so nothing to be at
            }
            constructs.add(List.copyOf(starts));
        }
        return new SourceLayout(text, read, List.copyOf(constructs));
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

    /** The text itself. */
    public String text() {
        return source;
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

    /**
     * Where {@code token} ends — the other end of the region it covers.
     *
     * <p>That token's own place, carried its width along, and never the place the offset after it
     * lands in. The two are the same offset and are not the same question: what is written at
     * {@code token.end()} is the next token where nothing separates them, so read as an offset this
     * end was the next token's start, and writing a space between the two moved it back onto this
     * one. A region's end is on the node the region is of, whatever is written after it.
     */
    public SourcePos after(SyntaxToken token) {
        return at(token).along(token.end() - token.start());
    }

    /**
     * The place at {@code offset}: the meaningful token starting at or before it, and how far past
     * that token's start the offset is.
     *
     * <p>For a caller holding an offset and no token — a parser reporting where it stopped, an
     * editor asking what is under a cursor. A caller holding a token asks {@link #at} or
     * {@link #after}, which answer about that token; this one answers about the offset, and at the
     * boundary between two tokens those are different answers.
     */
    public SourcePos placeAt(int offset) {
        if (tokenStart.isEmpty()) {
            return read.at(0, 0, Math.max(0, offset));
        }
        int construct = constructAt(offset);
        List<Integer> tokens = tokenStart.get(construct);
        int token = tokenAt(tokens, offset);
        return read.at(construct, token, offset - tokens.get(token));
    }

    /**
     * Where {@code place} is in this text, in UTF-16 code units from its start.
     *
     * <p>Refused rather than nudged onto the nearest thing there is. A place naming a construct or
     * a token this text does not hold is a place in some other text — the same mistake
     * {@link NotThisText} is about, arriving from a text that gave no name to tell it by, or from a
     * revision this layout is not of. Answered by moving it to the last token there is, it came
     * back as a line and a column that read like an answer, which is what a caller does something
     * wrong with.
     *
     * @throws NotThisText where {@code place} is in a text this is not the layout of
     * @throws NoSuchPlace where this text holds no such place
     */
    public int offsetOf(SourcePos place) {
        refuseAnotherText(place);
        if (tokenStart.isEmpty()) {
            // Nothing meaningful is written here, so there is one place and the offset is the whole
            // of it. Every place of such a text is that one, which is what `placeAt` makes.
            return Math.max(0, place.within());
        }
        if (place.construct() < 0 || place.construct() >= tokenStart.size()) {
            throw new NoSuchPlace(place, tokenStart.size() + " constructs");
        }
        List<Integer> tokens = tokenStart.get(place.construct());
        if (place.token() < 0 || place.token() >= tokens.size()) {
            throw new NoSuchPlace(place,
                    tokens.size() + " tokens in construct " + place.construct());
        }
        // And not bounded by the length of the text. How far past a token's start a place sits is
        // not a question about which of this text's things it is, and a report about a source that
        // stops in the middle of something points at or past the end of it — a caret drawn over
        // what was never typed. Where that reader is sent is {@link #resolve}'s to settle.
        return tokens.get(place.token()) + place.within();
    }

    /**
     * Where {@code place} sits in this text as it now stands.
     *
     * <p>Answered against this layout and never remembered. A caller holding the number after the
     * text has been written in again is holding a number about a text nobody has.
     *
     * <p>Held to the text, which {@link #offsetOf} is not. This answers where to send a reader, and
     * a place running past the end of the text — a caret over a construct the author stopped in the
     * middle of — is a reader sent to the end of what they wrote. Which of this text's things a
     * place is is the other question, and one this text does not hold is refused there rather than
     * moved to the nearest one.
     */
    @Override
    public PhysicalPos resolve(SourcePos place) {
        int at = Math.max(0, Math.min(offsetOf(place), source.length()));
        return new PhysicalPos(lines.lineOf(at), lines.columnOf(at));
    }

    /**
     * Refuses a place in a text this is not the layout of.
     *
     * <p>A place says which of the things written in its text it is, and the count means nothing
     * against another text — the same numbers are a different place there. Read without saying so,
     * the two came back as a line and a column that looked like an answer, and a report quoted a
     * line of whatever text the caller had in hand.
     *
     * <p>Asked of which text, and not of which file. A text this compile has no file for is still a
     * text that can be told from another: one put back together out of what a module published is
     * that module's, and reading a place in one module's published text against another's is the
     * same mistake as reading a place in one file against another. Written as a question about
     * files, this let every pair that crossed the published arm through — which is a pair a
     * compilation now makes, because what a module was read back from travels beside its reading
     * and is laid out here.
     *
     * <p>A text nobody named is the one that cannot be checked: it carries nothing to tell two of
     * them apart by, so a place in one and a layout of one match as far as anything here can see.
     */
    private void refuseAnotherText(SourcePos place) {
        QuotedFrom asked = place.quotedFrom();
        if (!(text instanceof QuotedFrom.TextItCannotName)
                && !(asked instanceof QuotedFrom.TextItCannotName)
                && !text.equals(asked)) {
            throw new NotThisText(text, asked);
        }
    }

    /**
     * A place this text does not hold.
     *
     * <p>Beside {@link NotThisText} and not under it, because they say different things to whoever
     * reads the message: one is a place from another text, and this is a place from another text or
     * another revision that carried nothing to say so. What they share is that neither is a place
     * here, and neither is answered.
     */
    public static final class NoSuchPlace extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        NoSuchPlace(SourcePos place, String held) {
            super(place + " read against a text holding " + held);
        }
    }

    /** A place read against a text it is not in. */
    public static final class NotThisText extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        NotThisText(QuotedFrom laidOut, QuotedFrom asked) {
            super("a place in " + said(asked) + " read against the layout of " + said(laidOut));
        }

        private static String said(QuotedFrom text) {
            return switch (text) {
                case QuotedFrom.ASourceThisCompileHolds(SourceId source) -> String.valueOf(source);
                case QuotedFrom.TextItCannotShow(SourceProvenance by) -> "what " + by + " published";
                case QuotedFrom.TextItCannotName _ -> "a text with no name";
            };
        }
    }

    /** The last construct beginning at or before {@code offset}, and the first where none does. */
    private int constructAt(int offset) {
        int lo = 0;
        int hi = tokenStart.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (tokenStart.get(mid).get(0) <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static int tokenAt(List<Integer> tokens, int offset) {
        int lo = 0;
        int hi = tokens.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (tokens.get(mid) <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
