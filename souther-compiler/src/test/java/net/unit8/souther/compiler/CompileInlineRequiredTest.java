package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
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
 * A behavior may call a required behavior inline in an expression — e.g. as a record-literal
 * field value — not only bound to a {@code let}. This is how the section 23 example writes
 * {@code 事前承認日時: 現在時刻()}. The requirement is still inferred and injected via bind().
 */
class CompileInlineRequiredTest {

    private static final String MODULE = """
            module demo

            data Id { value: String }
            data Member { id: Id }
            data Resp { m: Member }

            required behavior findMember(Id) -> Member

            behavior handle(id: Id) -> Resp constructs Resp {
                Resp { m: findMember(id) }
            }
            """;

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
    void requiredCalledInlineInARecordLiteral() throws Exception {
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(MODULE));
        classes.put("demo.FindMemberImpl", compileSubclass(classes, "demo.FindMemberImpl", IMPL_SRC));

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> findMember = loader.loadClass("demo.findMember");
        Class<?> handleClass = loader.loadClass("demo.handle");
        var bind = handleClass.getMethod("bind", findMember);
        Object impl = loader.loadClass("demo.FindMemberImpl").getConstructor().newInstance();
        Object handle = bind.invoke(null, impl);

        Decoder<?> idDecoder = (Decoder<?>) loader.loadClass("demo.Id").getMethod("decoder").invoke(null);
        Object id = idDecoder.decode(Raw.text("q"));
        Object r = ((Behavior) handle).apply(id);

        Encoder enc = (Encoder) loader.loadClass("demo.Resp").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(r);
        Raw.ObjectValue m = (Raw.ObjectValue) out.value().get("m");
        assertEquals(Raw.text("m-1"), m.value().get("id"));
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
