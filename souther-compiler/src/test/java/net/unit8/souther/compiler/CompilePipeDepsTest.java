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
 * A pipeline stage may be a body behavior that has its own required dependency (spec 14.3):
 * the pipeline collects the union of every stage's requirements and injects them via bind().
 * Here {@code handle = fetch >> enrich}, where {@code fetch} is required and {@code enrich}
 * internally calls the required {@code tag}.
 */
class CompilePipeDepsTest {

    private static final String MODULE = """
            module demo

            data In = { value: String }
            data Mid = { value: String }
            data Out = { a: String  b: String }

            required behavior fetch = (In) -> Mid
            required behavior tag = (Mid) -> Mid

            behavior enrich = (m: Mid) -> Out constructs Out {
                let t = tag(m)
                Out { a: m.value, b: t.value }
            }

            behavior handle = fetch >> enrich
            """;

    private static String impl(String cls, String param, String midValue) {
        return """
                package demo;
                import net.unit8.raoh.Ok;
                import net.unit8.raoh.Path;
                import net.unit8.raoh.decode.Decoder;
                public final class %s extends %s {
                    public Object apply(Object in) {
                        Decoder d = Mid.decoder();
                        return ((Ok) d.decode("%s", Path.ROOT)).value();
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

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object inVal = ((Ok) inDec.decode("x", Path.ROOT)).value();
        Object out = ((Behavior) handle).apply(inVal);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> o = (Map<?, ?>) enc.encode(out);
        assertEquals("m", o.get("a"));
        assertEquals("T", o.get("b"));
    }

    /**
     * Regression: a pipeline used as a stage of another pipeline contributed no requirements,
     * because only body behaviors were resolved. The outer pipeline got a no-arg constructor and
     * called the inner one's — which does not exist — so it compiled clean and then threw
     * NoSuchMethodError on apply, with no way to inject the dependency at all.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aPipelineOfAPipelineStillCollectsTheInnerRequirements() throws Exception {
        String src = MODULE + """

                behavior relabel = (o: Out) -> Out constructs Out {
                    Out { a: o.b, b: o.a }
                }

                behavior outer = handle >> relabel
                """;
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(src));
        classes.put("demo.FetchImpl", compileSubclass(classes, "demo.FetchImpl", impl("FetchImpl", "fetch", "m")));
        classes.put("demo.TagImpl", compileSubclass(classes, "demo.TagImpl", impl("TagImpl", "tag", "T")));

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());
        Class<?> outerClass = loader.loadClass("demo.outer");
        // outer requires what handle requires, transitively — not nothing
        var bind = outerClass.getMethod("bind", loader.loadClass("demo.fetch"), loader.loadClass("demo.tag"));

        Object outer = bind.invoke(null,
                loader.loadClass("demo.FetchImpl").getConstructor().newInstance(),
                loader.loadClass("demo.TagImpl").getConstructor().newInstance());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object out = ((Behavior) outer).apply(((Ok) inDec.decode("x", Path.ROOT)).value());

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> o = (Map<?, ?>) enc.encode(out);
        assertEquals("T", o.get("a"), "relabel swaps the fields handle produced");
        assertEquals("m", o.get("b"));
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
