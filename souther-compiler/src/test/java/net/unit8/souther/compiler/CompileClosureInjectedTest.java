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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An escaping closure may call an injected behavior. The lambda is chosen at runtime, so it becomes
 * a runtime {@code Fn}; the injected behavior it calls is not a local, so the closure captures the
 * enclosing behavior's injected field into one of its own and dispatches through it.
 */
class CompileClosureInjectedTest {

    private static final String MODULE = """
            module demo

            data In = { name: String, flag: Bool }
            data Out = { v: String }

            behavior dep : (i: In) -> Out

            behavior check : (o: In) -> Out
                requires dep

            let check (o, dep) = {
                let f = if o.flag then (x) -> dep(x) else (x) -> dep(x)
                f(o)
            }
            """;

    private static final String IMPL_SRC = """
            package demo;
            import net.unit8.raoh.Ok;
            import net.unit8.raoh.Path;
            import net.unit8.raoh.Result;
            import net.unit8.raoh.decode.Decoder;
            import java.util.Map;
            public final class DepImpl extends Dep {
                public Out apply(In in) {
                    Decoder d = Out.decoder();
                    Result r = d.decode(Map.of("v", "got:" + in.name()), Path.ROOT);
                    return (Out) ((Ok) r).value();
                }
            }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aClosureCapturesAndCallsAnInjectedBehavior() throws Exception {
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(MODULE));
        classes.put("demo.DepImpl", compileSubclass(classes, "demo.DepImpl", IMPL_SRC));

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());
        Class<?> dep = loader.loadClass("demo.Dep");
        Object impl = loader.loadClass("demo.DepImpl").getConstructor().newInstance();
        Object check = loader.loadClass("demo.Check").getMethod("bind", dep).invoke(null, impl);

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(Map.of("name", "kawa", "flag", true), Path.ROOT)).value();
        Object r = ((Behavior) check).apply(in);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals("got:kawa", ((Map<?, ?>) enc.encode(r)).get("v"));
    }

    private static byte[] compileSubclass(Map<String, byte[]> generated, String className, String source)
            throws Exception {
        java.nio.file.Path classesDir = Files.createTempDirectory("souther-gen");
        for (Map.Entry<String, byte[]> e : generated.entrySet()) {
            java.nio.file.Path p = classesDir.resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        java.nio.file.Path srcFile = classesDir.resolve(className.replace('.', '/') + ".java");
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
