package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A pipeline stage may be a body behavior that has its own required dependency (spec 14.3):
 * the pipeline collects the union of every stage's requirements and injects them via bind().
 * Here {@code handle = fetch >> enrich}, where {@code fetch} is required and {@code enrich}
 * internally calls the required {@code tag}.
 */
class CompilePipeDepsTest {

    private static final String MODULE = """
            module demo

            data In { value: String }
            data Mid { value: String }
            data Out { a: String  b: String }

            required behavior fetch(In) -> Mid
            required behavior tag(Mid) -> Mid

            behavior enrich(m: Mid) -> Out constructs Out {
                let t = tag(m)
                Out { a: m.value, b: t.value }
            }

            behavior handle = fetch >> enrich
            """;

    private static String impl(String cls, String param, String midValue) {
        return """
                package demo;
                import net.unit8.souther.runtime.Raw;
                import net.unit8.souther.runtime.Decoder;
                public final class %s extends %s {
                    public Object apply(Object in) {
                        Decoder<?> d = Mid.decoder();
                        return d.decode(Raw.text("%s"));
                    }
                }
                """.formatted(cls, param, midValue);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pipelineCollectsEveryStagesRequirements() throws Exception {
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(MODULE));
        classes.put("demo.FetchImpl", compileSubclass(classes, "demo.FetchImpl", impl("FetchImpl", "fetch", "m")));
        classes.put("demo.TagImpl", compileSubclass(classes, "demo.TagImpl", impl("TagImpl", "tag", "T")));

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> fetch = loader.loadClass("demo.fetch");
        Class<?> tag = loader.loadClass("demo.tag");
        Class<?> handleClass = loader.loadClass("demo.handle");
        var bind = handleClass.getMethod("bind", fetch, tag); // union of requirements, first-seen order

        Object handle = bind.invoke(null,
                loader.loadClass("demo.FetchImpl").getConstructor().newInstance(),
                loader.loadClass("demo.TagImpl").getConstructor().newInstance());

        Object in = loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object inVal = ((net.unit8.souther.runtime.Decoder<?>) in).decode(Raw.text("x"));
        Object out = ((Behavior) handle).apply(inVal);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Raw.ObjectValue o = (Raw.ObjectValue) enc.encode(out);
        assertEquals(Raw.text("m"), o.value().get("a"));
        assertEquals(Raw.text("T"), o.value().get("b"));
    }

    private static byte[] compileSubclass(Map<String, byte[]> generated, String className, String source)
            throws Exception {
        Path classesDir = Files.createTempDirectory("souther-gen");
        for (Map.Entry<String, byte[]> e : generated.entrySet()) {
            Path p = classesDir.resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        Path srcFile = classesDir.resolve(className.replace('.', '/') + ".java");
        Files.writeString(srcFile, source);
        Path outDir = Files.createTempDirectory("souther-impl");
        String cp = classesDir + File.pathSeparator + System.getProperty("java.class.path");
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-classpath", cp, "-d", outDir.toString(), srcFile.toString());
        if (rc != 0) {
            throw new IllegalStateException("javac failed for " + className + " (rc=" + rc + ")");
        }
        return Files.readAllBytes(outDir.resolve(className.replace('.', '/') + ".class"));
    }
}
