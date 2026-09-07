package souther.lsp.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Abandonment;
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

    /** What stops a walk of the workspace short. Reading the files under a root is not a question put
     * to a store, so what abandons the compile does not reach it; this is where it is asked, at every
     * path a walk reaches rather than at the ones it was looking for. */
    private Abandonment abandonment = Abandonment.NEVER;

    /** What makes reading this workspace stop short of an answer. */
    public void abandonWhen(Abandonment abandonment) {
        this.abandonment = abandonment;
    }

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
            } catch (IllegalArgumentException _) {
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

    /**
     * Where a module the workspace imports but does not contain is looked for: the class output of
     * every project under the roots. A project that has been built once is then importable in the
     * editor, so an import the build resolves is not underlined here as unknown.
     *
     * <p>This finds what has been built beside the source being edited. A dependency that exists only
     * as a jar in the local repository is not found — knowing about that means reading the build,
     * which the language server does not do.
     */
    public ModulePath modulePath() {
        List<Path> outputs = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root, CLASS_OUTPUT_DEPTH)) {
                // Asked of every path the walk reaches, and not of the ones that turn out to be
                // what is wanted. What costs time is the walk, and a root with no class output in
                // it is a root this would read to the end after being told to stop.
                walk.forEach(path -> {
                    abandonment.stopIfAsked();
                    if (Files.isDirectory(path) && isClassOutput(path)) {
                        outputs.add(path);
                    }
                });
            } catch (IOException _) {
                // a root that cannot be walked contributes nothing; the workspace still works
            }
        }
        return outputs.isEmpty() ? ModulePath.EMPTY : ModulePath.ofClassPath(outputs);
    }

    /** How far under a root a project's class output is looked for: `<root>/<project>/target/classes`
     * is three, and a root that is itself the project is one. */
    private static final int CLASS_OUTPUT_DEPTH = 4;

    private static boolean isClassOutput(Path dir) {
        Path parent = dir.getParent();
        return parent != null
                && ((dir.getFileName().toString().equals("classes")
                                && parent.getFileName().toString().equals("target"))
                        || dir.endsWith(Path.of("build", "classes", "java", "main")));
    }

    private Map<String, String> scanDisk() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                // Every path, as above: the walk is what a workspace of a hundred thousand files
                // spends its time on, and how many of them end in `.sou` says nothing about that.
                walk.forEach(path -> {
                    abandonment.stopIfAsked();
                    if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(SUFFIX)) {
                        sources.put(path.toUri().toString(), readOrEmpty(path));
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return sources;
    }

    private static String readOrEmpty(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException _) {
            return "";   // a file that cannot be read contributes nothing, but never crashes the scan
        }
    }
}
