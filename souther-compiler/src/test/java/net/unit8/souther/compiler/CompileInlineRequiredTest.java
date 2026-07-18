package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
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
 * A behavior may call a required behavior inline in an expression — e.g. as a record-literal
 * field value — not only bound to a {@code let}. This is how the section 23 example writes
 * {@code 事前承認日時: 現在時刻()}. The requirement is still inferred and injected via bind().
 */
class CompileInlineRequiredTest {

    private static final String MODULE = """
            module demo

            data Id = String
            data Member = { id: Id }
            data Resp = { m: Member }

            behavior findMember : (id: Id) -> Member

            behavior handle : (id: Id) -> Resp constructs Resp requires findMember

            let handle (id, findMember) = {
                Resp { m = findMember(id) }
            }
            """;

    private static final String IMPL_SRC = """
            package demo;
            import net.unit8.raoh.Ok;
            import net.unit8.raoh.Path;
            import net.unit8.raoh.Result;
            import net.unit8.raoh.decode.Decoder;
            import java.util.Map;
            public final class FindMemberImpl extends FindMember {
                public Member apply(Id in) {
                    Decoder d = Member.decoder();
                    Result r = d.decode(Map.of("id", "m-1"), Path.ROOT);
                    return (Member) ((Ok) r).value();
                }
            }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void requiredCalledInlineInARecordLiteral() throws Exception {
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(MODULE));
        classes.put("demo.FindMemberImpl", compileSubclass(classes, "demo.FindMemberImpl", IMPL_SRC));

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> findMember = loader.loadClass("demo.FindMember");
        Class<?> handleClass = loader.loadClass("demo.Handle");
        var bind = handleClass.getMethod("bind", findMember);
        Object impl = loader.loadClass("demo.FindMemberImpl").getConstructor().newInstance();
        Object handle = bind.invoke(null, impl);

        Decoder idDecoder = (Decoder) loader.loadClass("demo.Id").getMethod("decoder").invoke(null);
        Object id = ((Ok) idDecoder.decode("q", Path.ROOT)).value();
        Object r = ((Behavior) handle).apply(id);

        Encoder enc = (Encoder) loader.loadClass("demo.Resp").getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(r);
        Map<?, ?> m = (Map<?, ?>) out.get("m");
        assertEquals("m-1", m.get("id"));
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
