package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.runtime.Behavior;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An injected behavior (a {@code behavior} with no {@code let}) may take more than one input, like a
 * fn behavior. Declaring 2+ params and calling with all of them type-checks; calling with the wrong
 * count is an arity error rather than a silent drop of the extra arguments (issue #57).
 */
class CompileInjectedMultiArgTest {

    private static final String HEAD = """
            module demo

            data A = { x: Int }
            data B = { y: Int }
            data R = { z: Int }

            behavior send : (a: A, b: B) -> R
                constructs R

            behavior use : (a: A, b: B) -> R
                requires send

            """;

    @Test
    void aTwoArgInjectedCallTypechecks() {
        // the natural call send(a, b) must type-check — today it errors "expects 1 argument, got 2"
        String src = HEAD + "let use (a, b, send) = send(a, b)\n";
        Compiler.compile(src);   // throws today; must not after the fix
    }

    @Test
    void callingATwoArgInjectedWithOneArgIsAnArityError() {
        // the silent-drop hole: send(a) on a 2-input spec used to compile, dropping b
        String src = HEAD + "let use (a, b, send) = send(a)\n";
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("send") || e.getMessage().toLowerCase().contains("argument"),
                e.getMessage());
    }

    @Test
    void aTwoArgInjectedBaseIsAStandaloneAbstractClassWithTypedApply() throws Exception {
        // 2+ inputs → standalone abstract class, no Behavior supertype (not a >-> stage), typed apply(A,B)
        String src = HEAD + "let use (a, b, send) = send(a, b)\n";
        Map<String, byte[]> classes = Compiler.compile(src);
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());
        Class<?> send = loader.loadClass("demo.Send");
        assertTrue(Modifier.isAbstract(send.getModifiers()), "the injected base is abstract");
        assertFalse(Behavior.class.isAssignableFrom(send),
                "a multi-input injected behavior does not implement the unary Behavior contract");
        Class<?> a = loader.loadClass("demo.A");
        Class<?> b = loader.loadClass("demo.B");
        assertNotNull(send.getMethod("apply", a, b), "declares a typed apply(A, B)");
    }

    @Test
    void aSingleArgInjectedBaseStillImplementsBehavior() throws Exception {
        // orthogonality: a 1-input injected behavior is unchanged — it still composes with >->
        String src = """
                module demo
                data A = { x: Int }
                data R = { z: Int }
                behavior one : (a: A) -> R
                behavior use : (a: A) -> R requires one
                let use (a, one) = one(a)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Class<?> one = loader.loadClass("demo.One");
        assertTrue(Behavior.class.isAssignableFrom(one), "a single-input injected base stays a Behavior");
    }

    @Test
    void aPipelineStageDependingOnAMultiArgInjectedLoadsAndRuns() throws Exception {
        // a `>->` stage whose body requires a multi-arg injected dep: the pipeline stores that dep as
        // its base class, so pushStage must read/wire it as the base class, not the unary Behavior
        String src = """
                module demo

                data A = { x: Int }
                data B = { y: Int }
                data R = { z: Int }
                data S = { w: Int }

                behavior send : (a: A, b: B) -> R
                    constructs R

                behavior stage1 : (s: S) -> R
                    constructs A, B
                    requires send

                let stage1 (s, send) = send(A { x = s.w }, B { y = s.w })

                behavior stage2 : (r: R) -> R
                    constructs R

                let stage2 (r) = R { z = r.z + 1 }

                behavior pipe = stage1 >-> stage2
                """;
        String impl = """
                package demo;
                public final class SendImpl extends Send {
                    public R apply(A a, B b) { return R(a.x() + b.y()); }
                }
                """;
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(src));
        classes.put("demo.SendImpl", compileSubclass(classes, "demo.SendImpl", impl));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Object pipe = loader.loadClass("demo.Pipe").getMethod("bind", loader.loadClass("demo.Send"))
                .invoke(null, loader.loadClass("demo.SendImpl").getConstructor().newInstance());
        Object s = Codecs.decoded(loader, "demo.S", Map.of("w", 20L));
        Object r = Codecs.apply(pipe, s);   // send(A{20}, B{20}) = 40, stage2 +1 = 41
        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.R", r);
        assertEquals(41L, out.get("z"));
    }

    private static final String IMPL_SRC = """
            package demo;
            public final class SendImpl extends Send {
                public R apply(A a, B b) {
                    return R(a.x() + b.y());   // inherited protected factory for `constructs R`
                }
            }
            """;

    @Test
    void aTwoArgInjectedIsBoundAndCalledWithBothArguments() throws Exception {
        String src = HEAD + "let use (a, b, send) = send(a, b)\n";
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(src));
        classes.put("demo.SendImpl", compileSubclass(classes, "demo.SendImpl", IMPL_SRC));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> send = loader.loadClass("demo.Send");
        Class<?> a = loader.loadClass("demo.A");
        Class<?> b = loader.loadClass("demo.B");
        Object useSvc = loader.loadClass("demo.Use").getMethod("bind", send)
                .invoke(null, loader.loadClass("demo.SendImpl").getConstructor().newInstance());

        Object aVal = Codecs.decoded(loader, "demo.A", Map.of("x", 2L));
        Object bVal = Codecs.decoded(loader, "demo.B", Map.of("y", 40L));
        Object r = useSvc.getClass().getMethod("apply", a, b).invoke(useSvc, aVal, bVal);

        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.R", r);
        assertEquals(42L, out.get("z"), "both arguments reach the Java implementation");
    }

    @Test
    void aMultiArgInjectedMixesAPrimitiveAndADataArgViaALetBinding() throws Exception {
        // exercises primitive boxing (Int -> Long) alongside a data ref, and the let-binding tail path
        String head = """
                module demo

                data A = { x: Int }
                data R = { z: Int }

                behavior scale : (n: Int, a: A) -> R
                    constructs R

                behavior use : (n: Int, a: A) -> R
                    requires scale

                let use (n, a, scale) = {
                    let m = scale(n, a)
                    m
                }
                """;
        String impl = """
                package demo;
                public final class ScaleImpl extends Scale {
                    public R apply(Long n, A a) {
                        return R(n * a.x());
                    }
                }
                """;
        Map<String, byte[]> classes = new HashMap<>(Compiler.compile(head));
        classes.put("demo.ScaleImpl", compileSubclass(classes, "demo.ScaleImpl", impl));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Class<?> scale = loader.loadClass("demo.Scale");
        Object useSvc = loader.loadClass("demo.Use").getMethod("bind", scale)
                .invoke(null, loader.loadClass("demo.ScaleImpl").getConstructor().newInstance());
        Object aVal = Codecs.decoded(loader, "demo.A", Map.of("x", 14L));
        Object r = useSvc.getClass().getMethod("apply", Long.class, loader.loadClass("demo.A"))
                .invoke(useSvc, 3L, aVal);
        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.R", r);
        assertEquals(42L, out.get("z"));
    }

    @Test
    void aMultiArgInjectedDependencyIsFakedByATupleTable() {
        // CTFE: an example may evaluate a behavior whose 2-arg dependency is faked by an input tuple
        String src = HEAD + """
                let use (a, b, send) = send(a, b)

                fake send
                  | (A { x = 1 }, B { y = 2 }) -> R { z = 3 }

                example use
                  | (A { x = 1 }, B { y = 2 }) -> R { z = 3 }
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void aMultiArgDependencyIsFakedByAConstantWith() {
        // a `with dep = value` constant fake must also work for a multi-arg dep (not only a table)
        String src = HEAD + """
                let use (a, b, send) = send(a, b)

                example use
                  | (A { x = 1 }, B { y = 2 }) with send = R { z = 7 } -> R { z = 7 }
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void aMultiArgFakeRowWithWrongInputCountIsReported() {
        // a fake row that writes fewer inputs than the dependency's arity is a diagnostic, not a crash
        String src = HEAD + """
                let use (a, b, send) = send(a, b)

                fake send
                  | (A { x = 1 }) -> R { z = 3 }

                example use
                  | (A { x = 1 }, B { y = 2 }) -> R { z = 3 }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    @Test
    void aMultiArgFakeTupleMissIsReported() {
        String src = HEAD + """
                let use (a, b, send) = send(a, b)

                fake send
                  | (A { x = 1 }, B { y = 2 }) -> R { z = 3 }

                example use
                  | (A { x = 9 }, B { y = 2 }) -> R { z = 3 }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
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

    @Test
    void callingATwoArgInjectedWithAWronglyTypedArgIsRejected() {
        // arg 2 must be type-checked against param b: B, not ignored
        String src = HEAD + "let use (a, b, send) = send(a, a)\n";
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("B") || e.getMessage().contains("A"), e.getMessage());
    }
}
