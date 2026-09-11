package souther.compiler.apt;

import souther.compiler.source.SourceId;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.Located;
import souther.compiler.diag.Messages;
import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.SourceContext;
import souther.compiler.diag.SourceContextResolver;
import souther.compiler.diag.SourceNames;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.Compiler;
import souther.compiler.query.Compilation;
import souther.compiler.meta.ModulePath;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A javac annotation processor that compiles Souther {@code .sou} sources to {@code .class} as a side effect
 * of an ordinary {@code javac} run (spec §modules, §compiler-pipeline). Point it at a directory (or a single
 * {@code .sou} file) with {@code -Asouther.source=<path>}; each module found is compiled and its classes are
 * emitted through the {@link Filer}, so hand-written Java in the same compilation can reference the generated
 * types directly.
 *
 * <p>This needs no build-tool plugin: it is discovered the standard way (Maven
 * {@code annotationProcessorPaths}, Gradle {@code annotationProcessor}, or plain
 * {@code javac -processorpath}). With no {@code souther.source} option it is a no-op, so it is
 * harmless to have on any classpath.
 *
 * <p>A compile error is rendered as the CLI renders it and handed to the {@code Messager}, so the
 * build log carries the snippet and the hint rather than an exception's detail string.
 * {@code -Asouther.lang=<tag>} chooses the language, defaulting the same way {@code souther --lang}
 * does.
 */
@SupportedAnnotationTypes("*")
public final class SoutherProcessor extends AbstractProcessor {

    private boolean done = false;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of("souther.source", "souther.lang");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (done) {
            return false;
        }
        String configured = processingEnv.getOptions().get("souther.source");
        if (configured == null || configured.isBlank()) {
            return false;   // not configured: no-op
        }
        done = true;
        List<Source> sources = List.of();
        try {
            sources = readSources(Path.of(configured));
            if (sources.isEmpty()) {
                return false;
            }
            List<String> texts = sources.stream().map(Source::text).toList();
            // A module these sources import but do not contain is looked for on the compile
            // classpath — which is what depending on another project's jar already puts there, so
            // there is nothing to configure.
            ModulePath path = compileClassPath();
            // One source with no `module` header is a self-contained module and can import nothing;
            // one that names itself is a module set of one, and may import a module off the path.
            boolean selfContained =
                    texts.size() == 1 && Compiler.moduleNameFromHeader(texts.get(0)) == null;
            Compiler.Compiled compiled = selfContained
                    ? Compiler.compileWithWarnings(texts.get(0))
                    : Compiler.compileModulesWithWarnings(texts, path);
            // A warning is the whole of what the checker has to say about an unproven construction,
            // so a build that never reports one lets them accumulate while staying green.
            for (String reported : render(compiled.locatedWarnings(), sources)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, reported);
            }
            Map<String, ClassFileImage> classes = compiled.classes();
            Filer filer = processingEnv.getFiler();
            for (Map.Entry<String, ClassFileImage> entry : classes.entrySet()) {
                JavaFileObject file = filer.createClassFile(entry.getKey());
                try (OutputStream out = file.openOutputStream()) {
                    out.write(entry.getValue().bytes());
                }
            }
        } catch (CompileException e) {
            for (String reported : render(e, sources)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, reported);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "souther: io error: " + e.getMessage());
        }
        return false;
    }

    /**
     * The compile error as the CLI would print it: an Elm-style snippet in the chosen locale, with
     * no color since this goes to a build log. The snippet comes from the source the compiler names
     * — the only one, or the module it was working on when a module set is linked. An error that
     * carries several diagnostics — every failing {@code example} row — is reported once per row.
     */
    private List<String> render(CompileException e, List<Source> sources) {
        if (e.diagnostic() == null) {
            return List.of("souther: " + e.getMessage());   // not yet structured
        }
        return render(e.locatedDiagnostics(), sources);
    }

    /** The same rendering for a warning, which arrives already carrying the source it belongs to. */
    private List<String> render(List<Located> located, List<Source> sources) {
        return DiagnosticRenderer.renderAll(
                located, sourcesOf(sources), new HumanRenderer(false), locale());
    }

    /**
     * The language this compile answers in: the {@code souther.lang} processor option if the build
     * passes one, then {@code SOUTHER_LANG}, then the default.
     *
     * <p>The processor's policy, in the processor. javac tells a processor nothing about who reads
     * its output, so the option is what there is to read; a build that passes none is answered like
     * a CLI invocation that named none.
     */
    private Locale locale() {
        return Messages.resolveLocale(processingEnv.getOptions().get("souther.lang"));
    }

    /** What to quote for each source a diagnostic points into, under names no two of these files
     *  share. The text is already in hand, so this memoizes only to keep one answer per id. */
    private static SourceContextResolver sourcesOf(List<Source> sources) {
        List<String> names = SourceNames.of(
                sources.stream().map(source -> source.path().toString()).toList());
        return SourceContextResolver.memoized(id -> {
            int at = indexOf(sources, id);
            return at < 0 ? null
                    : new SourceContext(names.get(at), sources.get(at).text(),
                            SourceLayout.of(sources.get(at).text(), id));
        });
    }

    /** Which of the sources handed over an id names, or -1 when it names none of them. One file
     *  handed over is the answer however a diagnostic is tagged, including one this compile could
     *  pin on no source. */
    private static int indexOf(List<Source> sources, SourceId sourceId) {
        if (sources.size() == 1) {
            return 0;
        }
        for (int i = 0; i < sources.size(); i++) {
            if (Compilation.idOfSourceIndex(i).equals(sourceId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The classes of the projects this compilation depends on, read through the {@link Filer}: javac
     * has already resolved the compile classpath, so nothing here has to be told where the jars are.
     * A class the classpath does not have reads as absent, which is how an import of a module that is
     * genuinely not there stays an unknown-module error where it is written.
     */
    private ModulePath compileClassPath() {
        Filer filer = processingEnv.getFiler();
        return binaryName -> {
            int lastDot = binaryName.lastIndexOf('.');
            String pkg = lastDot < 0 ? "" : binaryName.substring(0, lastDot);
            String simple = binaryName.substring(lastDot + 1) + ".class";
            try (InputStream in = filer.getResource(StandardLocation.CLASS_PATH, pkg, simple)
                    .openInputStream()) {
                return in.readAllBytes();
            } catch (IOException | IllegalArgumentException | UnsupportedOperationException _) {
                return null;
            }
        };
    }

    /** Reads a single {@code .sou} file, or every {@code .sou} under a directory (path-sorted). */
    private static List<Source> readSources(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                List<Path> files = walk.filter(p -> p.toString().endsWith(".sou")).sorted().toList();
                List<Source> sources = new ArrayList<>();
                for (Path file : files) {
                    sources.add(new Source(file, Files.readString(file)));
                }
                return sources;
            }
        }
        return List.of(new Source(path, Files.readString(path)));
    }

    /** A {@code .sou} file and its text. The path is kept so a diagnostic can quote the line. */
    private record Source(Path path, String text) {}
}
