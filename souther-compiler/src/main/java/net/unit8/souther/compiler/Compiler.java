package net.unit8.souther.compiler;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.codegen.Backend;
import net.unit8.souther.compiler.derive.Deriver;
import net.unit8.souther.compiler.syntax.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The compiler pipeline facade: source → parse → type check → ClassFile bytecode
 * (spec section 20). Slice 1.
 */
public final class Compiler {

    private Compiler() {}

    /** Compiles source into a map of binary class name → bytecode. */
    public static Map<String, byte[]> compile(String source) {
        Ast.Module module = Deriver.derive(Parser.parse(source));
        TypeChecker.check(module);
        return Backend.generate(module);
    }

    /** Compiles source and writes each generated class under {@code outDir}. */
    public static void compileToDir(String source, Path outDir) throws IOException {
        for (Map.Entry<String, byte[]> entry : compile(source).entrySet()) {
            Path file = outDir.resolve(entry.getKey().replace('.', '/') + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
    }
}
