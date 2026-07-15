package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pipeline can start with a decoder and end with an encoder (spec 14.1): the whole boundary
 * flow Raw -> decode -> behavior -> encode -> Raw is expressible in the language. A decode
 * failure propagates as the 復号失敗 arm through the behavior stage.
 */
class CompileCodecStageTest {

    private static final String MODULE = """
            module demo

            data In { value: String }
            data Out { value: String }

            required behavior process(In) -> Out

            behavior handle = In.decoder >> process >> Out.encoder
            """;

    private static final String IMPL = """
            package demo;
            import net.unit8.souther.runtime.Raw;
            import net.unit8.souther.runtime.Decoder;
            public final class ProcessImpl extends process {
                public Object apply(Object in) {
                    Decoder<?> d = Out.decoder();
                    return d.decode(Raw.text("done"));
                }
            }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void decoderStartsAndEncoderEndsThePipeline() throws Exception {
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(MODULE));
        classes.put("demo.ProcessImpl", compileSubclass(classes, "demo.ProcessImpl", IMPL));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> process = loader.loadClass("demo.process");
        var bind = loader.loadClass("demo.handle").getMethod("bind", process);
        Object handle = bind.invoke(null, loader.loadClass("demo.ProcessImpl").getConstructor().newInstance());

        // valid Raw text decodes, runs, and re-encodes to Raw
        Object ok = ((Behavior) handle).apply(Raw.text("x"));
        assertEquals(Raw.text("done"), ok);

        // a Raw of the wrong shape fails to decode; 復号失敗 propagates past the behavior stage
        Object bad = ((Behavior) handle).apply(Raw.integer(5));
        assertTrue(bad instanceof DecodeFailure, "a decode failure propagates to the output");
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
