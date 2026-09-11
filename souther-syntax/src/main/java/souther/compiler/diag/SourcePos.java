package souther.compiler.diag;

import souther.compiler.source.SourceId;

import java.util.Comparator;
import java.util.Objects;

/**
 * Where a node is placed in this compile: a 1-based line and column, and the text they are in
 * ({@link Placement}). Every AST node and token carries one so that compile errors can point at the
 * source (spec §non-functional).
 *
 * <p>The line and the column are <b>where this compile placed the node</b>, which is where the code
 * is written for everything a source was read for and is not for a copy that could not keep its own
 * positions. So this answers where to send a reader and never, on its own, where the code is
 * written. What answers that is a {@link Citation}, which a report holds instead of a position; a
 * pass that wants only to know whether the position was borrowed asks {@link #wasCopiedHere()}.
 * Neither is inferred from the line: inferring it is what {@code BottomInfer} did, by comparing an
 * argument's position with its call's, and what {@code HelperInliner} did, by comparing the
 * declaring module with its own.
 *
 * <p>A line and a column are enough while one file is being read and not enough afterwards. A
 * module's {@code example} rows, fake tables and values are written in the module's own source and
 * in any number of attached {@code examples for} files, and once they are gathered under one name a
 * line and a column on their own no longer say which file they came from — so what is quoted is
 * whatever happens to sit at those numbers in the file the reader guessed at.
 *
 * <p>Which is why the text is part of the position and part of what makes two positions the same
 * one. Line 25 of two files is the same pair of numbers and is not the same place; a value whose
 * identity denied one of its components would leave "the same position" meaning something different
 * in every container that held one. Where a caller wants the numbers compared without the text — an
 * editor asking what is under a cursor — it compares the numbers, which is what {@code Names.spans}
 * does.
 *
 * <p>One component and not two. This used to be a source identity that could be null beside a
 * separate answer about the code, which is nine combinations for the places this compiler makes, and
 * every reader worked the classification out again from whichever half it had: a null source meant
 * "out of sight" to one of them, "the diagnostic's own file" to another and "drop this" to two more.
 * What a {@link Placement} holds is which text and whose code, and every pair of those is legal.
 *
 * @param line the 1-based line this node is placed at
 * @param column the 1-based column this node is placed at, in UTF-16 code units
 * @param placement the text these numbers are in, and what this compilation knows about it
 */
public record SourcePos(int line, int column, Placement placement) {

    public SourcePos {
        Objects.requireNonNull(placement, "a position is in some text and says which");
    }

    /** A place a source was read for, where the code it names is written — or a text with no
     *  identity where {@code sourceId} is none. */
    public SourcePos(int line, int column, SourceId sourceId) {
        this(line, column, sourceId == null ? Placement.aTextWithNoIdentity()
                : Placement.aFileOfThisCompile(sourceId));
    }

    /** A position read from no source. */
    public SourcePos(int line, int column) {
        this(line, column, Placement.aTextWithNoIdentity());
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
     * reading asking how far into a source an answer reaches — all of them are asking what a line
     * and a column mean in order, and three accounts of that are three chances for one of them to be
     * written the other way round.
     */
    public boolean isBefore(SourcePos other) {
        return line != other.line ? line < other.line : column < other.column;
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
        return new SourcePos(line, column, placement.standingInFor(declaring));
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

    /** The same place, {@code units} along the same line — the other end of a region of that width.
     *  It stands in for whatever this stands in for: the two ends are one place. */
    public SourcePos along(int units) {
        return new SourcePos(line, column + units, placement);
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
