package souther.compiler.diag;

import souther.compiler.source.SourceId;

import java.util.Comparator;
import java.util.Objects;

/**
 * Which of the things written in a text a node is at, and which text that is ({@link Placement}).
 * Every AST node and token carries one so that compile errors can point at the source
 * (spec §non-functional).
 *
 * <p>Not a line and a column. What a place is in a text is one of the things written in it, and a
 * line number is where that thing happens to sit — press return above it and every number below
 * moves while nothing written there says anything different. Held as numbers, a position made every
 * answer that carried one a different answer after an edit that changed nothing, so the whole of
 * what a module means was worked out again to arrive at what it already was.
 *
 * <p>So a place is the meaningful token it is at or after, counted from the start of its text, and
 * how far past that token's start it sits. Whitespace, a line break and a comment move nothing: a
 * token that was the ninth is the ninth still. Writing a tenth token before it does move it, and
 * that is the whole of what is conservative here — <b>this is not an identity for what is written
 * at it</b>, and nothing may file anything under one and look it up against another text. What it is
 * is a coordinate whose equality survives the edits that say nothing, and which changes when the
 * syntax before it changes, so that the answers holding one are worked out again exactly then.
 *
 * <p>What sends a reader somewhere is a {@link PhysicalPos}, which is this place resolved against
 * the text as it now stands. Resolving is somebody's to do who holds that text, and what comes back
 * is never kept.
 *
 * <p>These places are <b>where this compile placed the node</b>, which is where the code is written
 * for everything a source was read for and is not for a copy that could not keep its own positions.
 * So this answers where to send a reader and never, on its own, where the code is written. What
 * answers that is a {@link Citation}, which a report holds instead of a position; a pass that wants
 * only to know whether the position was borrowed asks {@link #wasCopiedHere()}. Neither is inferred
 * from where it sits: inferring it is what {@code BottomInfer} did, by comparing an argument's
 * position with its call's, and what {@code HelperInliner} did, by comparing the declaring module
 * with its own.
 *
 * <p>A place in a text is enough while one file is being read and not enough afterwards. A module's
 * {@code example} rows, fake tables and values are written in the module's own source and in any
 * number of attached {@code examples for} files, and once they are gathered under one name a
 * token count on its own no longer says which file it came from — so what is quoted is whatever
 * happens to sit there in the file the reader guessed at.
 *
 * <p>Which is why the text is part of the position and part of what makes two positions the same
 * one. The ninth token of two files is the same count and is not the same place; a value whose
 * identity denied one of its components would leave "the same position" meaning something different
 * in every container that held one.
 *
 * <p>One component and not two. This used to be a source identity that could be null beside a
 * separate answer about the code, which is nine combinations for the places this compiler makes, and
 * every reader worked the classification out again from whichever half it had: a null source meant
 * "out of sight" to one of them, "the diagnostic's own file" to another and "drop this" to two more.
 * What a {@link Placement} holds is which text and whose code, and every pair of those is legal.
 */
public final class SourcePos {

    private final int construct;

    private final int token;

    private final int within;

    private final Placement placement;

    /**
     * The place {@code within} UTF-16 code units past the start of the {@code token}-th meaningful
     * token of the {@code construct}-th top-level construct of {@code placement}'s text, each
     * counted from zero.
     *
     * <p>Two counts and not one. Counted over the whole text, a token written anywhere moves every
     * token after it — so typing inside one body moved the places of every declaration below it,
     * which is the commonest edit there is and the one a compilation most wants to keep local.
     * Counted from the construct it is in, what such an edit moves stops at that construct's end.
     *
     * <p>Made where a text becomes places and nowhere else, which is {@code SourceLayout}. A caller
     * spelling one out of numbers it worked out for itself has counted the tokens of that text a
     * second time, and the two go on counting separately.
     *
     * <p>Which is why this is the package's and not everyone's: what is offered outside it is
     * {@link Placement#at}, where saying which text a hand-spelled place is in is the thing the
     * caller has to write down.
     */
    SourcePos(int construct, int token, int within, Placement placement) {
        this.construct = construct;
        this.token = token;
        this.within = within;
        this.placement = Objects.requireNonNull(placement,
                "a position is in some text and says which");
    }

    /** Which top-level construct of its text this is in, counting from zero. */
    public int construct() {
        return construct;
    }

    /** Which meaningful token of that construct this is at or after, counting from zero. */
    public int token() {
        return token;
    }

    /** How far past that token's start this sits, in UTF-16 code units. */
    public int within() {
        return within;
    }

    public Placement placement() {
        return placement;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SourcePos it && construct == it.construct && token == it.token
                && within == it.within && placement.equals(it.placement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(construct, token, within, placement);
    }

    /**
     * A position read from no source.
     *
     * <p>The one place spelling left. A place in a text somebody holds is read off that text
     * ({@code SourceLayout}), and one spelled out of numbers against a named file is a count made
     * against nothing that has to agree with the count that text was laid out by — which is how a
     * cursor arrived at the token four along from wherever the editor's fourth column happened to
     * be. A place in no text says which of nothing it is, resolves nowhere, and is equal to the
     * other places minted to mean nowhere, which is the whole of what its callers want.
     *
     * <p>A caller that does want to say which text is saying something about that text, and says it
     * there: {@link Placement#at}.
     */
    public SourcePos(int token, int within) {
        this(0, token, within, Placement.aTextWithNoIdentity());
    }

    /**
     * Which source of this compilation this is read from, and what to say where it is none.
     *
     * <p>The one way to ask which file a position is in. It used to be asked of a source identity
     * that could be null, by five consumers that each read the absence as an answer to a question of
     * their own — this says why there is no file, and leaves what to do about it where it belongs.
     */
    public QuotedFrom quotedFrom() {
        return placement.quotedFrom();
    }

    /** Whether this is a position in {@code source}. */
    public boolean isIn(SourceId source) {
        return placement.quotedFrom()
                instanceof QuotedFrom.ASourceThisCompileHolds(SourceId file)
                && file.equals(source);
    }

    /**
     * Whether this and {@code other} are in the same text, whatever is written in either: a body
     * spliced into a file is in that file.
     *
     * <p>Answered by name, so two positions in texts this compilation has no name for come back the
     * same. That is not a claim that they are: an unnamed text carries nothing to tell two of them
     * apart by, because inside a compile the only unnamed positions are the ones a pass mints to
     * mean nowhere, and what makes those useful is that two of them are equal. Telling a text
     * somebody handed over from a position nobody placed is the open question about whether such a
     * position is a place at all, and until it is answered this — and {@link #equals} with it —
     * says nothing about two separate readings.
     *
     * <p>Every caller today compares positions from one reading, where the two agree.
     */
    public boolean isInTheSameTextAs(SourcePos other) {
        return placement.isTheSameTextAs(other.placement);
    }

    /**
     * Whether this comes before {@code other} in the text they are in.
     *
     * <p>Which text they are in is not asked here. Two places in different files have no order
     * between them, and a caller comparing them has already settled that they are comparable, which
     * {@link #isInTheSameTextAs} answers.
     *
     * <p>Here rather than in each reader that has to know. A region asking whether a place falls
     * inside it, a walk asking which occurrence is the narrowest one written over a cursor, a
     * reading asking how far into a source an answer reaches — all of them are asking what two
     * places in one text mean in order, and three accounts of that are three chances for one of them
     * to be written the other way round.
     */
    public boolean isBefore(SourcePos other) {
        if (construct != other.construct) {
            return construct < other.construct;
        }
        return token != other.token ? token < other.token : within < other.within;
    }

    /**
     * Two places in the order they are written, for a caller sorting what it holds into the order an
     * author reads it.
     *
     * <p>{@link #isBefore} is the answer and this is the shape a sort wants it in. Written here for
     * the reason that one is: a caller turning the question back into a line and a column to hand a
     * comparator two numbers has written the order down a second time, and the two go on answering
     * separately. Which text the two are in is the caller's to have settled, as it is there.
     */
    public static final Comparator<SourcePos> IN_WRITTEN_ORDER = (one, other) -> {
        if (one.isBefore(other)) {
            return -1;
        }
        return other.isBefore(one) ? 1 : 0;
    };

    /** Whether the code at this position was copied here rather than written at it — what sizes an
     *  underline, and what tells an empty literal the author wrote from one that arrived inside a
     *  copied body. Not whether a reader can be sent here, which is {@link DiagnosticPlace}'s. */
    public boolean wasCopiedHere() {
        return placement.code() instanceof Placement.CopiedFrom;
    }

    /**
     * This position, standing in for code written where {@code declaring} says — what an expansion
     * gives a copy it cannot give its own positions, and what moving a report's caret gives the
     * place it moved to.
     *
     * <p>Not where a stand-in is first decided. Whether code is out of sight is settled where a text
     * becomes positions, by the caller that knows what the text was, and a position a parser made
     * says so from the start — a text put back together out of what a module published is read by a
     * parser like any other, and line 4 of it is a line of nothing anybody holds. What this does is
     * carry an answer already given to a position somewhere else: the call a body was spliced into,
     * the import line a report was moved to.
     *
     * <p>What it keeps is which text this is in, and what it replaces is what the code in it is. The
     * two are the two questions a {@link Placement} answers, and a splice moves exactly one of them.
     *
     */
    public SourcePos standingInFor(DeclaringCode declaring) {
        return new SourcePos(construct, token, within, placement.standingInFor(declaring));
    }

    /**
     * Where the code this position names is written, reached by {@code name} — what a splice is
     * told, when it has learned the name the call reaches.
     *
     * @throws NotWrittenElsewhere where the code this names is written at it in a text a reader
     *         holds. There is no declaration to name for code the reader is already looking at, and
     *         a caller that got here was branching on something other than the question
     */
    public DeclaringCode reachedBy(String name) {
        SourceProvenance written = placement.codeIsWrittenIn();
        if (written == null) {
            throw new NotWrittenElsewhere(this);
        }
        return new DeclaringCode(written.reachedBy(name));
    }

    /**
     * A position asked where its code came from when its code is written at it.
     *
     * <p>This position's own state and not a bad argument: where the code is written is settled when
     * the position is made, and a caller asking where else it came from has already been told the
     * answer is here. What such a caller branched on is not the question it is asking.
     */
    public static final class NotWrittenElsewhere extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NotWrittenElsewhere(SourcePos of) {
            super("the code this names is written at it, so there is nothing to say about where it"
                    + " came from: " + of);
        }
    }

    /** The same place, {@code units} of text further on — the other end of a region of that width.
     *  It stands in for whatever this stands in for: the two ends are one place. */
    public SourcePos along(int units) {
        return new SourcePos(construct, token, within + units, placement);
    }

    /** What this is, for a reader of a stack trace or a message about the compiler itself. Not
     *  something to show an author: where they would be sent is a {@link PhysicalPos}. */
    @Override
    public String toString() {
        return "construct " + construct + " token " + token
                + (within == 0 ? "" : "+" + within);
    }
}
