package net.unit8.souther.compiler.apt;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.Compiler;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A javac annotation processor that compiles Souther {@code .sou} sources to {@code .class} as a
 * side effect of an ordinary {@code javac} run (spec 4, 20). Point it at a directory (or a single
 * {@code .sou} file) with {@code -Asouther.source=<path>}; each module found is compiled and its
 * classes are emitted through the {@link Filer}, so hand-written Java in the same compilation can
 * reference the generated types directly.
 *
 * <p>This needs no build-tool plugin: it is discovered the standard way (Maven
 * {@code annotationProcessorPaths}, Gradle {@code annotationProcessor}, or plain
 * {@code javac -processorpath}). With no {@code souther.source} option it is a no-op, so it is
 * harmless to have on any classpath.
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
        return Set.of("souther.source");
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
        try {
            List<String> sources = readSources(Path.of(configured));
            if (sources.isEmpty()) {
                return false;
            }
            Map<String, byte[]> classes = sources.size() == 1
                    ? Compiler.compile(sources.get(0))
                    : Compiler.compileModules(sources);
            Filer filer = processingEnv.getFiler();
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                JavaFileObject file = filer.createClassFile(entry.getKey());
                try (OutputStream out = file.openOutputStream()) {
                    out.write(entry.getValue());
                }
            }
        } catch (CompileException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "souther: " + e.getMessage());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "souther: io error: " + e.getMessage());
        }
        return false;
    }

    /** Reads a single {@code .sou} file, or every {@code .sou} under a directory (path-sorted). */
    private static List<String> readSources(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                List<Path> files = walk.filter(p -> p.toString().endsWith(".sou")).sorted().toList();
                List<String> sources = new ArrayList<>();
                for (Path file : files) {
                    sources.add(Files.readString(file));
                }
                return sources;
            }
        }
        return List.of(Files.readString(path));
    }
}
