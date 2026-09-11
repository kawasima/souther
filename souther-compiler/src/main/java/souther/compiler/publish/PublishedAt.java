package souther.compiler.publish;

import souther.compiler.diag.Citation;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourcePos;
import souther.compiler.source.SourceId;

import java.util.Optional;
import java.util.SequencedMap;

/**
 * A place a document sends a reader to, as the document writes it.
 *
 * <p>The projection of a {@link Citation} onto the three things the shipped schema has room for: a
 * source identity, the place in it, and what the code at that place is to the module the document is
 * about. Everything else a citation carries is how this compiler came to be holding it.
 *
 * <p>The place and not a line and a column. One of these travels in what a compilation answers — a
 * rule handle is one — and a line number is what a file is laid out as at the moment rather than
 * anything about the rule. The numbers are worked out where the document is written, by whoever
 * holds the text.
 *
 * <p><b>Made before anything is put in order.</b> A citation is a sum whose arms are not all places
 * — a position in a text no reader holds is not one, and neither is code out of sight with no
 * position inside it — so there is no order over citations to be had, and asking for one would be
 * asking which of two things that are not places comes first. What has an order is this: every one
 * of these is a place, and what one is written as is what tells two of them apart.
 *
 * <p>So this and never {@link Citation} is what a canonical order is over, and what a witness is
 * chosen from, and {@code PublicationOrders.PLACES} is the order.
 *
 * <p><b>It names the source and does not resolve it.</b> What a document calls a source is the
 * renderer's, recorded as the document writes it; asked here, the choosing of one place out of
 * several would decide which sources a document explains by the order it compared them in.
 */
public record PublishedAt(SourcePos at, Where writtenAt) {

    public PublishedAt {
        if (at == null || writtenAt == null) {
            throw new IllegalArgumentException("a place a reader is sent to is in some source");
        }
        if (!(at.quotedFrom() instanceof QuotedFrom.ASourceThisCompileHolds)) {
            throw new IllegalArgumentException(
                    "a place a reader is sent to is in a source this compile holds: " + at);
        }
    }

    /**
     * Which source this is in, which the place says.
     *
     * <p>Read off it rather than carried beside it. A source and a place that answers for one are
     * two answers to one question, and nothing keeps them the same — a marker in one file with its
     * line read from another is what that comes to.
     */
    public SourceId source() {
        return ((QuotedFrom.ASourceThisCompileHolds) at.quotedFrom()).source();
    }

    /**
     * What the code at this place is to the module the document is about: written here, or reached
     * from here and written somewhere this compile holds no file for.
     */
    public sealed interface Where {

        /** What a document writes about this, under the fields it writes them under — the same
         *  words a citation is written as, asked of the one place that has them. */
        default SequencedMap<String, String> fields() {
            return switch (this) {
                case Here _ -> Citation.hereFields();
                case OutOfSight it -> Citation.outOfSightFields(it.declaration());
            };
        }

        /** The code is at this place. */
        record Here() implements Where {}

        /** The code is elsewhere, and this is where this compile met it — the call a body was
         *  spliced into, the import line a report was moved to. */
        record OutOfSight(String declaration) implements Where {

            public OutOfSight {
                if (declaration == null) {
                    throw new IllegalArgumentException(
                            "code out of sight is reached by some declaration");
                }
            }
        }
    }

    /**
     * Where {@code cited} sends a reader, or nothing where it sends them nowhere.
     *
     * <p>Empty for the three arms that have no place to write and for a position in a text this
     * compilation cannot name. That is a citation a reader cannot be sent to rather than one this
     * refuses: what a document does about a fact with none of them is the document's to say.
     */
    public static Optional<PublishedAt> of(Citation cited) {
        SourcePos pos = switch (cited) {
            case Citation.Written it -> it.at();
            case Citation.Reached it -> it.at();
            case Citation.Unplaced _, Citation.UnplacedElsewhere _, Citation.OutOfSight _ -> null;
        };
        if (pos == null
                || !(pos.quotedFrom() instanceof QuotedFrom.ASourceThisCompileHolds(SourceId in))) {
            return Optional.empty();
        }
        Where written = switch (cited) {
            case Citation.Elsewhere it -> new Where.OutOfSight(it.provenance().reachedBy());
            case Citation.Written _, Citation.Unplaced _ -> new Where.Here();
        };
        return Optional.of(new PublishedAt(pos, written));
    }
}
