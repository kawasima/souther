package net.unit8.souther.lsp.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * An immutable snapshot of the Souther sources in a workspace, keyed by document URI. The
 * {@link net.unit8.souther.lsp.LspServer} builds it by scanning the workspace root and overlaying the
 * text of any open buffer; the {@link Analyzer} reads it to resolve names and diagnostics across the
 * whole module set, the way the batch compiler does.
 */
public final class ModuleGraph {

    private final Map<String, String> sources;

    private ModuleGraph(Map<String, String> sources) {
        this.sources = sources;
    }

    /** A graph over the given {@code uri -> source text} map. */
    public static ModuleGraph of(Map<String, String> sources) {
        return new ModuleGraph(new LinkedHashMap<>(sources));
    }

    /** Every document URI in the workspace. */
    public Set<String> uris() {
        return sources.keySet();
    }

    /** The source text of {@code uri}, or {@code null} if it is not in the graph. */
    public String text(String uri) {
        return sources.get(uri);
    }
}
