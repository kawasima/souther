package net.unit8.souther.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI entry point: {@code souther <file.sou>... -d <outdir>}. Compiles one or more source files
 * to {@code .class} files under the output directory, resolving imports across the given files.
 */
public final class Main {

    public static void main(String[] args) {
        List<Path> sources = new ArrayList<>();
        Path outDir = Path.of(".");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-d")) {
                outDir = Path.of(args[++i]);
            } else {
                sources.add(Path.of(args[i]));
            }
        }
        if (sources.isEmpty()) {
            System.err.println("usage: souther <file.sou>... -d <outdir>");
            System.exit(2);
            return;
        }
        try {
            for (Path file : run(sources, outDir)) {
                System.out.println("wrote " + file);
            }
        } catch (CompileException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("io error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Compiles the given source files together — a single file, or several linked through their
     * imports (spec 4) — and writes each generated class under {@code outDir}. Returns the paths
     * written, in order.
     */
    public static List<Path> run(List<Path> sources, Path outDir) throws IOException {
        List<String> texts = new ArrayList<>();
        for (Path source : sources) {
            texts.add(Files.readString(source));
        }
        Map<String, byte[]> classes = texts.size() == 1
                ? Compiler.compile(texts.get(0))
                : Compiler.compileModules(texts);
        List<Path> written = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            Path file = outDir.resolve(entry.getKey().replace('.', '/') + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
            written.add(file);
        }
        return written;
    }

    private Main() {}
}
