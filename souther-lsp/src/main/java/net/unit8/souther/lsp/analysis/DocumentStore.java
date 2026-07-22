package net.unit8.souther.lsp.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The open documents, keyed by URI. Full-sync only: each change replaces the whole text. */
public final class DocumentStore {

    private final Map<String, String> texts = new LinkedHashMap<>();

    public void open(String uri, String text) {
        texts.put(uri, text);
    }

    public void change(String uri, String text) {
        texts.put(uri, text);
    }

    public void close(String uri) {
        texts.remove(uri);
    }

    /** The current text of {@code uri}, or {@code null} if it is not open. */
    public String get(String uri) {
        return texts.get(uri);
    }

    /** The URIs of every open document. */
    public Set<String> uris() {
        return texts.keySet();
    }

    /** A snapshot of every open document's text, keyed by URI — the overlay a {@link Workspace}
     * applies over the on-disk sources. */
    public Map<String, String> openDocuments() {
        return new LinkedHashMap<>(texts);
    }
}
