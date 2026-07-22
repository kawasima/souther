package net.unit8.souther.lsp.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The Souther sources of a workspace: the {@code .sou} files under the roots the client announced at
 * initialize, with any open editor buffer overlaid on the on-disk text. A {@link #snapshot} is the
 * immutable {@link ModuleGraph} the {@link Analyzer} resolves diagnostics and names against — the
 * same module set the batch compiler would link.
 */
public final class Workspace {

    private static final String SUFFIX = ".sou";

    private final List<Path> roots = new ArrayList<>();

    /** The last on-disk scan ({@code uri -> text}), or {@code null} when it must be re-read. Cached so
     * an edit to an open buffer does not re-walk and re-read the whole workspace on every keystroke. */
    private Map<String, String> diskScan;

    /** Records the workspace roots from their {@code file://} URIs; non-file URIs are ignored. */
    public void setRoots(List<String> rootUris) {
        diskScan = null;   // the set of files to scan changed
        roots.clear();
        for (String uri : rootUris) {
            if (uri == null) {
                continue;
            }
            try {
                URI parsed = URI.create(uri);
                if ("file".equals(parsed.getScheme())) {
                    roots.add(Path.of(parsed));
                }
            } catch (IllegalArgumentException e) {
                // a malformed root URI is skipped rather than failing the session
            }
        }
    }

    /**
     * The current module graph: every {@code .sou} file under the roots, read from disk, with the
     * given {@code openBuffers} (keyed by document URI) overlaid — an open buffer's unsaved text wins,
     * and an open document outside the roots is still included.
     */
    public ModuleGraph snapshot(Map<String, String> openBuffers) {
        if (diskScan == null) {
            diskScan = scanDisk();
        }
        Map<String, String> sources = new LinkedHashMap<>(diskScan);
        sources.putAll(openBuffers);
        return ModuleGraph.of(sources);
    }

    /** Invalidates the cached disk scan, so the next {@link #snapshot} re-reads the workspace. Called
     * when the client reports on-disk changes ({@code workspace/didChangeWatchedFiles}). */
    public void markChanged() {
        diskScan = null;
    }

    private Map<String, String> scanDisk() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(SUFFIX))
                        .forEach(p -> sources.put(p.toUri().toString(), readOrEmpty(p)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return sources;
    }

    private static String readOrEmpty(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";   // a file that cannot be read contributes nothing, but never crashes the scan
        }
    }
}
