package net.unit8.souther.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI entry point with two subcommands: {@code souther compile <file.sou>... -d <outdir>} writes
 * {@code .class} files, and {@code souther run <file.sou> [--behavior <name>] [--input <json>]}
 * drives one behavior and prints its output (see {@link Runner}).
 */
public final class Main {

    private static final String USAGE = """
            usage: souther <command> [args]
            commands:
              compile <file.sou>... -d <outdir>                    compile to .class files
              run <file.sou> [--behavior <name>] [--input <json>]  run a behavior, print its output""";

    public static void main(String[] args) {
        String command = args.length == 0 ? "" : args[0];
        String[] rest = args.length == 0 ? args : java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (command) {
            case "run" -> runSubcommand(rest);
            case "compile" -> compileSubcommand(rest);
            default -> {
                String hint = command.endsWith(".sou")
                        ? "no command given — did you mean `souther compile " + command
                                + " …` or `souther run " + command + " …`?"
                        : command.isEmpty() ? "no command given" : "unknown command `" + command + "`";
                System.err.println(hint);
                System.err.println(USAGE);
                System.exit(2);
            }
        }
    }

    /** {@code souther compile <file.sou>... -d <outdir>}: writes the generated {@code .class} files. */
    private static void compileSubcommand(String[] args) {
        List<Path> sources = new ArrayList<>();
        Path outDir = Path.of(".");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-d")) {
                if (++i >= args.length) {
                    System.err.println("`-d` needs an output directory");
                    System.err.println(USAGE);
                    System.exit(2);
                    return;
                }
                outDir = Path.of(args[i]);
            } else {
                sources.add(Path.of(args[i]));
            }
        }
        if (sources.isEmpty()) {
            System.err.println("compile takes at least one .sou file");
            System.err.println(USAGE);
            System.exit(2);
            return;
        }
        try {
            for (Path file : compileToDir(sources, outDir)) {
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

    /** {@code souther run <file.sou> [--behavior <name>] [--input <json>]}: compiles the file in
     * memory and drives one behavior, printing its output as JSON (see {@link Runner}). */
    private static void runSubcommand(String[] args) {
        try {
            System.out.println(Runner.runCli(args));
        } catch (Runner.RunException e) {
            System.err.println(e.getMessage());
            System.exit(e.exitCode);
        } catch (CompileException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Compiles the given source files together — a single file, or several linked through their
     * imports (spec 4) — and writes each generated class under {@code outDir}. Returns the paths
     * written, in order.
     */
    public static List<Path> compileToDir(List<Path> sources, Path outDir) throws IOException {
        List<String> texts = new ArrayList<>();
        for (Path source : sources) {
            texts.add(Files.readString(source));
        }
        // A single header-less file is named after the file (F#/Elm; ADR-0043); a multi-file build
        // links by imports, so each must declare its own module header.
        Map<String, byte[]> classes = texts.size() == 1
                ? Compiler.compile(texts.get(0), Runner.moduleName(sources.get(0)))
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
