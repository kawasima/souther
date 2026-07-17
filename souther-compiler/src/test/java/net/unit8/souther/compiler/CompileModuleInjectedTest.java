package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Spec 4 / 13.2 / 14.3: a module can import an injected behavior (one another module declares
 * without a fn) and compose it with {@code >->}. The composition's inferred requirement is the
 * imported behavior, so its generated {@code bind(...)} injects the Java implementation of the
 * declaring module's abstract base.
 */
class CompileModuleInjectedTest {

    private static final String A = """
            module a exposing { N, produce }

            data N = Int

            behavior produce = (n: N) -> N
            """;

    private static final String B = """
            module b

            import a { N, produce }

            behavior flow = produce >-> produce
            """;

    // A Java implementation of module a's injected behavior base `a.produce` (Behavior<N, N>).
    private static final String IMPL = """
            package a;
            public final class ProduceImpl extends Produce {
                public N apply(N n) { return n; }
            }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void importsAnInjectedBehaviorAndBindsItAcrossModules() throws Exception {
        Map<String, byte[]> classes = new HashMap<>(Compiler.compileModules(List.of(A, B)));
        classes.put("a.ProduceImpl", compileSubclass(classes, "a.ProduceImpl", IMPL));

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> produce = loader.loadClass("a.Produce");
        var bind = loader.loadClass("b.Flow").getMethod("bind", produce);
        Object impl = loader.loadClass("a.ProduceImpl").getConstructor().newInstance();
        Object flow = bind.invoke(null, impl);

        Decoder nDec = (Decoder) loader.loadClass("a.N").getMethod("decoder").invoke(null);
        Object five = ((Ok) nDec.decode(5L, Path.ROOT)).value();
        Object r = ((Behavior) flow).apply(five);

        Encoder enc = (Encoder) loader.loadClass("a.N").getMethod("encoder").invoke(null);
        assertEquals(5L, enc.encode(r));
    }

    /** Compiles {@code source} against the generated classes and returns the class bytes. */
    private static byte[] compileSubclass(Map<String, byte[]> generated, String className, String source)
            throws Exception {
        java.nio.file.Path classesDir = Files.createTempDirectory("souther-gen");
        for (Map.Entry<String, byte[]> e : generated.entrySet()) {
            java.nio.file.Path p = classesDir.resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        java.nio.file.Path srcFile = classesDir.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, source);
        java.nio.file.Path outDir = Files.createTempDirectory("souther-impl");
        String cp = classesDir + File.pathSeparator + System.getProperty("java.class.path");
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-classpath", cp, "-d", outDir.toString(), srcFile.toString());
        if (rc != 0) {
            throw new IllegalStateException("javac failed for " + className + " (rc=" + rc + ")");
        }
        return Files.readAllBytes(outDir.resolve(className.replace('.', '/') + ".class"));
    }
}
