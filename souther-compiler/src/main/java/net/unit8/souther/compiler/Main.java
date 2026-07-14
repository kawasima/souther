package net.unit8.souther.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * CLI entry point: {@code souther <file.mdl> -d <outdir>}. Compiles one source file to
 * {@code .class} files under the output directory.
 */
public final class Main {

    public static void main(String[] args) {
        Path source = null;
        Path outDir = Path.of(".");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-d")) {
                outDir = Path.of(args[++i]);
            } else {
                source = Path.of(args[i]);
            }
        }
        if (source == null) {
            System.err.println("usage: souther <file.mdl> -d <outdir>");
            System.exit(2);
            return;
        }
        try {
            String src = Files.readString(source);
            Map<String, byte[]> classes = Compiler.compile(src);
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                Path file = outDir.resolve(entry.getKey().replace('.', '/') + ".class");
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
                System.out.println("wrote " + file);
            }
        } catch (CompileException e) {
            System.err.println(source + ": " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("io error: " + e.getMessage());
            System.exit(1);
        }
    }

    private Main() {}
}
