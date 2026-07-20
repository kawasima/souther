package net.unit8.souther.lsp.analysis;

import java.util.HashMap;
import java.util.Map;

/** The open documents, keyed by URI. Full-sync only: each change replaces the whole text. */
public final class DocumentStore {

    private final Map<String, String> texts = new HashMap<>();

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
}
