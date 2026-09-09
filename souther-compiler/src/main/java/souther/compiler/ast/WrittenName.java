package souther.compiler.ast;

import souther.compiler.Reserved;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;

/**
 * A name, and the occurrence of it in the source that this one came from.
 *
 * <p>These are two questions with two answers, and a name is asked both. Which name is it — the one
 * a declaration and a reference have to agree on, the one a wire tag is written from, the one a
 * {@code --behavior} argument is matched against — is {@link #canonical()}, and canonically
 * equivalent spellings give one answer because they are one text (ADR-0095). Where is it written —
 * what a report underlines, what a rename rewrites, what a cursor is on — is {@link #segments()}.
 *
 * <p>The two travel together because the second cannot be recovered from the first. Reading a width
 * off the canonical name puts every range short by however many combining marks the author typed,
 * and the failure is invisible until someone writes one: the underline stops mid-name, a rename
 * leaves the marks behind, a cursor on the last character is answered about nothing. Any caller
 * holding a name and a position as two values is a caller that can pair a name with somewhere it is
 * not written, so there is no form here that takes them apart.
 *
 * <p>The place is <b>the tokens</b> and not the stretch of file they lie in. A qualified name is read
 * over meaningful tokens, so {@code up . Amount} is one name and so is one a comment carries over a
 * line break — and the whitespace, the dots and the comment between the parts are not the name. Two
 * questions want two answers here as well, and this is the second pair the design got wrong:
 * {@link #region()} is what a report underlines, one continuous stretch, comment and all, because an
 * underline with holes in it is not something to read. {@link #covers} is whether a cursor is on the
 * name, and it is on it only where a token is. Answering the second with the first put
 * go-to-definition and rename under the cursor in the middle of a comment.
 *
 * <p>{@link #spelling()} is what a report quotes: the parts joined by dots, which is the name as its
 * author means it whatever sits between them. It is a claim about the name and not about the file.
 *
 * <p>A name no one wrote — one a desugaring minted, one a pass synthesized — is {@link #synthetic}:
 * it has a canonical form and somewhere to complain at, and no spelling, because there is no place
 * in the source where those characters are.
 *
 * <p>{@code anchor} is that somewhere, and it is a point rather than a stretch. Where a name is
 * written the stretch is read off the tokens; where no one wrote it there is no stretch to read, and
 * the only honest width is none. Taking the canonical name's length from the anchor — which is what
 * this did — states in characters of the file a fact about the name, and the two are the same number
 * only until an author writes a combining mark or a lowering picks a name longer than the form it
 * replaced.
 */
public record WrittenName(String canonical, String spelling, List<Region> segments,
                          SourcePos anchor) {

    public WrittenName {
        if (canonical == null) {
            throw new IllegalArgumentException("a name has a canonical form");
        }
        segments = segments == null ? List.of() : List.copyOf(segments);
        if (spelling != null && !canonical.equals(Reserved.name(spelling))) {
            throw new IllegalArgumentException(
                    "`" + spelling + "` is not a spelling of `" + canonical + "`");
        }
        if (spelling != null && segments.isEmpty()) {
            throw new IllegalArgumentException(
                    "`" + spelling + "` is spelled somewhere, or it is not a spelling");
        }
        // The anchor answers where there is nothing written to answer from, and only there. A name
        // with segments already says where it is, twice over — where it starts and how far it runs —
        // and a second answer beside them is one a caller could set to somewhere else.
        if (!segments.isEmpty() && anchor != null) {
            throw new IllegalArgumentException(
                    "`" + canonical + "` is written somewhere and is anchored somewhere else");
        }
        // And there are two states, not three. Segments without a spelling was the third: a name
        // nobody wrote holding the places some of it was written at, which reads as unwritten to
        // every caller that asks and as written to every caller that underlines.
        if (spelling == null && !segments.isEmpty()) {
            throw new IllegalArgumentException(
                    "`" + canonical + "` is spelled nowhere and is written in " + segments.size()
                            + " places");
        }
        for (Region segment : segments) {
            if (!segment.start().isInTheSameTextAs(segments.get(0).start())) {
                throw new IllegalArgumentException("`" + canonical + "` is written in one file");
            }
        }
    }

    /**
     * A name the author wrote as one token, read off the source that spells it. The one door in for
     * a spelling that occupies a single run of characters: canonicalizing happens here, so the name
     * and the place it was written come from one place and cannot be given separately.
     */
    public static WrittenName of(String spelling, SourcePos pos) {
        return new WrittenName(Reserved.name(spelling), spelling,
                pos == null ? List.of() : List.of(Region.ofWidth(pos, spelling.length())), null);
    }

    /**
     * A name no one wrote, anchored where a complaint about it belongs. {@code anchor} is a form in
     * the source holding something else — so it says where to report and not what to underline,
     * there being no characters here that spell this name.
     */
    public static WrittenName synthetic(String name, SourcePos anchor) {
        return new WrittenName(Reserved.name(name), null, List.of(), anchor);
    }

    /** Whether the source spells this name — false for one a pass minted. */
    public boolean authored() {
        return spelling != null;
    }

    /** The text to quote: what the author typed, or the name itself where no one typed it. */
    public String quoted() {
        return spelling == null ? canonical : spelling;
    }

    /** Where the name starts, or where a complaint about one nobody wrote belongs — null only for a
     *  name with no place of either kind. */
    public SourcePos pos() {
        return segments.isEmpty() ? anchor : segments.get(0).start();
    }

    /**
     * The stretch of file a report underlines: from where the name starts to where it ends, whatever
     * lies between its parts. Null where the name has no place at all, which is a report with
     * nothing to point at rather than one pointing at zero.
     *
     * <p>Not what {@link #covers} answers. An underline is read as one mark and a cursor is on one
     * character, so a comment inside a qualified name is under the underline and is not on the name.
     */
    public Region region() {
        return segments.isEmpty() ? null
                : new Region(segments.get(0).start(), segments.get(segments.size() - 1).end());
    }

    /**
     * Where a report about this name points: the characters that spell it, or the point one nobody
     * wrote is anchored at.
     *
     * <p>What every report asks, {@link #region()} being what an underline covers. A name a lowering
     * minted has somewhere a complaint about it belongs and nothing to draw a line under, and the
     * two answers are not the same answer: a report with no place at all says only which file it is
     * about, and that is a different report from one that says which line.
     */
    public Region reportedAt() {
        Region written = region();
        if (written != null) {
            return written;
        }
        return anchor == null ? null : Region.point(anchor);
    }

    /** Where the last dot-separated part is written — the only part a rename rewrites, since
     *  renaming a type rewrites the {@code Amount} of {@code up.Amount} and never the {@code up}. */
    public Region lastSegment() {
        return segments.isEmpty() ? null : segments.get(segments.size() - 1);
    }

    /**
     * Where the parts before the last are written — the {@code up} of {@code up.Amount} — or null
     * where the name has no qualifier or nobody wrote it.
     *
     * <p>One stretch, dots and all, because a qualifier of several parts is one answer to one
     * question: which module. Whatever separates its parts is under it for the same reason
     * {@link #region()} is continuous.
     */
    public Region qualifier() {
        return segments.size() < 2 ? null
                : new Region(segments.get(0).start(), segments.get(segments.size() - 2).end());
    }

    /**
     * Whether a cursor at {@code at} is on this name.
     *
     * <p>Inside a part, plainly. And at the end of the last part, because that is where a caret
     * rests when its author has just finished typing the name, and an editor that answers nothing
     * there falls back to matching the spelling — which answers, about whatever else in the
     * workspace is spelled the same.
     *
     * <p>Not at the end of a part that is not the last. A {@link Region} ends where the next thing
     * begins, so the character there belongs to what separates the parts: the {@code .} of
     * {@code up.Amount}, the space of {@code up . Amount}, the comment in one written over a line
     * break. Reaching the end of the whole name is one boundary and reaching the end of a qualifier
     * is another, and only the first is somewhere an author's caret means the name.
     */
    public boolean covers(SourcePos at) {
        return partAt(at) >= 0;
    }

    /**
     * Which part of this name a cursor at {@code at} is on, counting from 0, or {@code -1} where it
     * is on none.
     *
     * <p>What {@link #covers} answers, for a caller that also has to say which token. Reading the
     * two off one walk is the point: a caller that worked out the part for itself would be a second
     * place where the rule about the ends lives, and that rule being in two places is what three
     * rounds of review were about.
     */
    public int partAt(SourcePos at) {
        int within = partWithin(at);
        if (within >= 0) {
            return within;
        }
        Region last = lastSegment();
        return last != null && containsCharacterOrEnd(last, at) ? segments.size() - 1 : -1;
    }

    /**
     * Which part of this name the character at {@code at} belongs to, or {@code -1} where it
     * belongs to none — the end of every part included in "none", the last one's as well.
     *
     * <p>The question to ask of a name that is not finished being written. {@link #partAt} counts
     * the end of the last part because that is where a caret rests when its author has just typed
     * the name; a caret there means the name only if there is no more of it to come, and whether
     * there is more to come is a fact about the file that a name has no way to hold.
     */
    public int partWithin(SourcePos at) {
        for (int i = 0; i < segments.size(); i++) {
            if (containsCharacter(segments.get(i), at)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether this name is written inside {@code other}'s — which of two names that both cover a
     * cursor is the one the cursor is on, a container writing its element's name inside its own.
     *
     * <p>This compares two stretches rather than asking about a character, so a name that ends
     * exactly where the one around it ends is inside it. The last element of a container is written
     * that way.
     */
    public boolean within(WrittenName other) {
        Region mine = region();
        return other != null && encloses(other.region(), mine);
    }

    /** Whether the character at {@code at} is in {@code region}: from its start, up to but not
     *  including its end, in the file it begins in. */
    private static boolean containsCharacter(Region region, SourcePos at) {
        return placed(region, at) && before(at, region.end());
    }

    /** The same, and the boundary at the end — where a caret rests after the last character. */
    private static boolean containsCharacterOrEnd(Region region, SourcePos at) {
        return placed(region, at) && !before(region.end(), at);
    }

    /** Whether {@code at} is a place in {@code region}'s file, at or after its start. */
    private static boolean placed(Region region, SourcePos at) {
        SourcePos start = region.start();
        return start != null && region.end() != null && at != null
                && at.isInTheSameTextAs(start)
                && !before(at, start);
    }

    /** Whether {@code inner} lies within {@code outer}, ends allowed to meet. */
    private static boolean encloses(Region outer, Region inner) {
        if (outer == null || inner == null
                || !inner.start().isInTheSameTextAs(outer.start())) {
            return false;
        }
        return !before(inner.start(), outer.start()) && !before(outer.end(), inner.end());
    }

    /** Whether {@code a} comes before {@code b} in the file they share. */
    private static boolean before(SourcePos a, SourcePos b) {
        return a.line() != b.line() ? a.line() < b.line() : a.column() < b.column();
    }

    /**
     * This name and a member taken off it, as the one qualified name {@code a.b} that the two spell
     * together.
     *
     * <p>The parts are kept as the parts they are rather than merged into the stretch they span, so
     * what a report underlines and what a cursor is on stay two answers. A part nobody wrote makes
     * the whole unwritten — no run of characters spells it — and what is left is somewhere to
     * complain at.
     */
    public WrittenName then(WrittenName member) {
        String joined = canonical + "." + member.canonical();
        // A part nobody wrote makes the whole unwritten, and what is left is somewhere to complain
        // at. Keeping the written part's places would leave a name that answers "no one wrote this"
        // and underlines the half of it that somebody did.
        if (!authored() || !member.authored()) {
            return new WrittenName(joined, null, List.of(), pos());
        }
        List<Region> both = new ArrayList<>(segments);
        both.addAll(member.segments);
        return new WrittenName(joined, spelling + "." + member.spelling(), both, null);
    }

    @Override
    public String toString() {
        return canonical;
    }
}
