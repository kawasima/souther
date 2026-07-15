package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for the named required-behavior base class and the {@code bind(...)} factory
 * (spec 13.3, 19.5). A required behavior is generated as an abstract {@code Behavior}
 * subclass; a Java implementation {@code extends} it. Here the implementation is compiled
 * at runtime and injected through {@code bind}.
 */
class CompileBindTest {

    private static final String MODULE = """
            module demo

            data Id { value: String }
            data Member { id: Id }
            data Resp { id: Id }

            required behavior findMember(Id) -> Member

            behavior handle(id: Id) -> Resp constructs Resp {
                let m = findMember(id)
                Resp { id: m.id }
            }
            """;

    // A Java-side implementation of the generated abstract base `demo.findMember`.
    // Member is decode-sourced, so no arm factory is needed.
    private static final String IMPL_SRC = """
            package demo;
            import net.unit8.souther.runtime.Raw;
            import net.unit8.souther.runtime.Decoder;
            import java.util.Map;
            public final class FindMemberImpl extends findMember {
                public Object apply(Object in) {
                    Decoder<?> d = Member.decoder();
                    return d.decode(Raw.object(Map.of("id", Raw.text("m-1"))));
                }
            }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void generatesAnAbstractBaseAndBindFactory() throws Exception {
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(MODULE));
        classes.put("demo.FindMemberImpl", compileSubclass(classes, "demo.FindMemberImpl", IMPL_SRC));

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> findMember = loader.loadClass("demo.findMember");
        assertTrue(Modifier.isAbstract(findMember.getModifiers()),
                "the named required behavior is an abstract base class");
        assertTrue(Behavior.class.isAssignableFrom(findMember),
                "the named required base is a Behavior");

        Class<?> handleClass = loader.loadClass("demo.handle");
        var bind = handleClass.getMethod("bind", findMember); // bind(findMember) -> handle

        Object findMemberImpl = loader.loadClass("demo.FindMemberImpl").getConstructor().newInstance();
        Object handle = bind.invoke(null, findMemberImpl);

        Decoder<?> idDecoder = (Decoder<?>) loader.loadClass("demo.Id").getMethod("decoder").invoke(null);
        Object id = idDecoder.decode(Raw.text("q"));
        Object r = ((Behavior) handle).apply(id);

        Encoder enc = (Encoder) loader.loadClass("demo.Resp").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(r);
        assertEquals(Raw.text("m-1"), out.value().get("id"));
    }

    /** Compiles {@code source} against the generated classes and returns the class bytes. */
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
