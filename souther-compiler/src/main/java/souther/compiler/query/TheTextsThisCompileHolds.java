package souther.compiler.query;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.LaidOutText;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.meta.ReadableModule;
import souther.compiler.source.SourceId;

import java.util.HashMap;
import java.util.Map;

/**
 * The texts a compilation can lay a place out against: its own sources, and the texts it put back
 * together out of what the modules it imports published.
 *
 * <p>Made for the length of one pass and handed to it. What it reads is the store, and a store is
 * what a workspace is at the moment — so this is never part of an answer: an answer holding one
 * would be an answer holding a way to ask about sources that have since been written in again.
 *
 * <p>A text is laid out once per pass and kept for as long as the pass runs. Laying one out reads it
 * from end to end, and a pass that asks about a thousand instructions of one body would otherwise
 * read that body's text a thousand times.
 */
public final class TheTextsThisCompileHolds implements SourceLayouts {

    private final Db db;

    private final Map<QuotedFrom, LaidOutText> laidOut = new HashMap<>();

    TheTextsThisCompileHolds(Db db) {
        this.db = db;
    }

    @Override
    public LaidOutText of(SourcePos place) {
        if (place == null) {
            return null;
        }
        return laidOut.computeIfAbsent(place.quotedFrom(), this::lay);
    }

    /**
     * The text of one of the two kinds this compile can read, or null for the third.
     *
     * <p>A text nobody named is one somebody handed in and this compilation never held, so there is
     * nothing here to lay out. What a pass does about that is the pass's: a debug table writes no
     * line, a sentence says where the code is and not what line it is at.
     */
    private LaidOutText lay(QuotedFrom text) {
        return switch (text) {
            case QuotedFrom.ASourceThisCompileHolds(SourceId source) -> {
                // How it is laid out, not what it says: a reader turning a place into a line has no
                // business moving for a word written in a comment.
                Answer<SourceLayout> held = db.ask(new Front.LayoutOf(source));
                yield held.present() ? held.value() : null;
            }
            // The module's own, as it was read back. The text was put together out of what the
            // module carries, so laying one out here would be laying out a second text and taking
            // it for the one the places are in.
            case QuotedFrom.TextItCannotShow(SourceProvenance published) ->
                    laidOutAsRead(published.module());
            case QuotedFrom.TextItCannotName _ -> null;
        };
    }

    private LaidOutText laidOutAsRead(String module) {
        Answer<Front.FromPath.Of> path = db.ask(new Front.FromPath());
        if (!path.present()) {
            return null;
        }
        Front.FromPath.OnThePath found = path.value().modules().get(module);
        if (found == null) {
            return null;
        }
        ReadableModule read = found.read();
        return read == null ? null : read.laidOutText();
    }
}
