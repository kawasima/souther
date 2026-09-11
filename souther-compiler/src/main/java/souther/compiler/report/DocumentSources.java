package souther.compiler.report;

import souther.compiler.source.SourceId;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The sources a document writes the identities of, and what each of them is called.
 *
 * <p>A document for a machine carries what identifies a source rather than what to call it: an
 * identity is what the compilation refers to the source by, and so what makes two statements about
 * one file the same statement, while a name is the shortest thing that tells one reader's files
 * apart and is no key at all. Carrying the identity is right and leaves the document unreadable on
 * its own — nothing in it says which file a position in a list was. So the identities are carried and
 * the document explains them, and this is what collects the second half while the first is being
 * written.
 *
 * <p>Lives with the renderer and not with what is being rendered. What a reason is about is a fact
 * about the reason; that a document has written it down and now owes an explanation for it is a fact
 * about the document. The sums being rendered answer the first and never hold this.
 *
 * <p>Registered by the act of writing rather than gathered afterwards. A writer that gathered the
 * fields it knew about would be a list of the places an identity is written today, and the next field
 * to carry one would be outside it — which is the shape of the thing this is here to stop. Everything
 * that writes an identity into a document asks {@link #written} for the string to write, so a field
 * that is emitted is a field that is explained.
 *
 * <p>The table keeps the order the identities were first written in. Two runs over the same sources
 * write the same document and so build the same table, which is what a reader comparing two runs
 * needs; sorting would order a list of numbers as text and a list of URIs by nothing in particular.
 */
public final class DocumentSources {

    private final SourceRendering rendering;
    /** The texts, for the fields that write a line and a column. Here beside the names for the
     *  reason the names are here: what a place is at is what its file is laid out as at the moment,
     *  which is the document's to ask as it writes and not the result's to have carried. */
    private final SourceLayouts layouts;
    private final Set<SourceId> referenced = new LinkedHashSet<>();

    public DocumentSources(SourceRendering rendering) {
        this.rendering = rendering;
        this.layouts = rendering.layouts();
    }

    /** The texts this document is written against. */
    public SourceLayouts layouts() {
        return layouts;
    }

    /**
     * What this document writes a place against: the identities it writes for sources, and the
     * texts they are laid out in.
     *
     * <p>Its own naming and not the one a person reads. A document for a machine writes what
     * identifies a source, and a sentence inside such a document names it the same way — which is
     * what {@link #written} answers and what a reader of the document resolves against its table.
     */
    public SourceRendering rendering() {
        return new SourceRendering(this::written, layouts);
    }

    /** The identity to write, recorded as one this document has to explain — or nothing, for a
     *  place that names no source, which is a document saying it does not know rather than one
     *  naming a file it has no id for. */
    public String written(SourceId sourceId) {
        if (sourceId == null) {
            return null;
        }
        referenced.add(sourceId);
        return sourceId.value();
    }

    /**
     * What each identity written so far is called.
     *
     * <p>The identities this document carries and not the sources the compile was handed. A document
     * that listed everything given to the compile would be saying what was compiled, which is a
     * second thing for it to be about; what a reader of this one needs is that every identity in
     * front of them can be looked up.
     */
    public Map<String, String> table() {
        Map<String, String> table = new LinkedHashMap<>();
        for (SourceId sourceId : referenced) {
            table.put(sourceId.value(), rendering.names().nameOf(sourceId));
        }
        return table;
    }
}
