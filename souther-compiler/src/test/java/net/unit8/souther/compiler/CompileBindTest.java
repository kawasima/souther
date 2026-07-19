package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
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

            data Id = String
            data Member = { id: Id }
            data Resp = { id: Id }

            behavior findMember : (id: Id) -> Member

            behavior handle : (id: Id) -> Resp constructs Resp requires findMember

            let handle (id, findMember) = {
                let m = findMember(id)
                Resp { id = m.id }
            }
            """;

    // A Java-side implementation of the generated abstract base `demo.findMember`.
    // Member is decode-sourced, so no case factory is needed.
    private static final String IMPL_SRC = """
            package demo;
            import net.unit8.raoh.Ok;
            import net.unit8.raoh.Path;
            import net.unit8.raoh.decode.Decoder;
            import java.util.Map;
            public final class FindMemberImpl extends FindMember {
                public Member apply(Id in) {
                    Decoder d = Member.decoder();
                    return (Member) ((Ok) d.decode(Map.of("id", "m-1"), Path.ROOT)).value();
                }
            }
            """;

    @Test
    void generatesAnAbstractBaseAndBindFactory() throws Exception {
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(MODULE));
        classes.put("demo.FindMemberImpl", compileSubclass(classes, "demo.FindMemberImpl", IMPL_SRC));

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> findMember = loader.loadClass("demo.FindMember");
        assertTrue(Modifier.isAbstract(findMember.getModifiers()),
                "the named required behavior is an abstract base class");
        assertTrue(Behavior.class.isAssignableFrom(findMember),
                "the named required base is a Behavior");

        Class<?> handleClass = loader.loadClass("demo.Handle");
        var bind = handleClass.getMethod("bind", findMember); // bind(findMember) -> handle

        Object findMemberImpl = loader.loadClass("demo.FindMemberImpl").getConstructor().newInstance();
        Object handle = bind.invoke(null, findMemberImpl);

        Object id = Codecs.decoded(loader, "demo.Id", "q");
        Object r = Codecs.apply(handle, id);

        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Resp", r);
        assertEquals("m-1", out.get("id"));
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
