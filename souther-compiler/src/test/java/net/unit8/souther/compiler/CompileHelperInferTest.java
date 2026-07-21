package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A non-recursive helper is inline-expanded at each call site, so a value parameter's type can be
 * inferred from how it is called. Annotating it is no longer required. The helper stays monomorphic:
 * a parameter used at two incompatible types across its call sites is rejected. A function-typed
 * parameter still needs its annotation (it cannot be inferred from a bare name), and a recursive
 * helper — lowered to a method, never inlined — still declares its parameter types.
 */
class CompileHelperInferTest {

    @Test
    void aValueParameterTypeIsInferredFromTheCallSite() throws Exception {
        // `double`'s `n` is unannotated; it is called with `x.value` (an Int), so `n` is Int.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let double (n) = n * 2
                let f (x) = X(double(x.value))
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object x = Codecs.decoded(loader, "demo.X", 3L);

        Object behavior = loader.loadClass("demo.F$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, x);

        assertEquals(6L, (long) Codecs.encode(loader, "demo.X", out));
    }

    @Test
    void aParameterUsedAtConflictingTypesIsRejected() {
        // `id`'s `v` is Int at one call site and String at another; a helper is monomorphic, so the
        // divergence is a compile error rather than an inline-expanded polymorphism.
        String src = """
                module demo
                data X = Int
                data S = String
                behavior f : (x: X) -> X constructs X
                behavior g : (s: S) -> S constructs S
                let id (v) = v
                let f (x) = X(id(x.value))
                let g (s) = S(id(s.value))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("id")
                && (ex.getMessage().contains("conflict") || ex.getMessage().contains("Int")),
                ex.getMessage());
    }

    @Test
    void anUncalledUnannotatedHelperIsRejected() {
        // `unused` is never called, so there is no call site to infer its parameter from; the user
        // must annotate it (or remove the helper). The error points at the helper by name.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let unused (n) = n * 2
                let f (x) = x
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("unused")
                && (ex.getMessage().contains("infer") || ex.getMessage().contains("推論")),
                ex.getMessage());
    }

    @Test
    void anUnannotatedFunctionParameterIsRejected() {
        // `apply`'s `g` receives a function; a function type cannot be inferred from the call, so it
        // must be annotated. The value parameter `v` is still inferred.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let apply (g, v) = g(v)
                let f (x) = X(apply((n) -> n * 2, x.value))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("apply")
                && (ex.getMessage().contains("function") || ex.getMessage().contains("関数")
                    || ex.getMessage().contains("annotate")),
                ex.getMessage());
    }

    @Test
    void aLyingReturnTypeOnAnInferredHelperIsRejected() {
        // A declared return type must match the body even when the parameter type is inferred, not
        // only when it is annotated — the check runs on the completed (inferred) environment.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let g (n): String = n
                let f (x) = X(g(x.value))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("g") && ex.getMessage().contains("declares"), ex.getMessage());
    }

    @Test
    void anAnnotatedHelperStillCompiles() {
        // The existing form — an explicitly annotated helper — is unchanged.
        String src = """
                module demo
                data X = Int
                behavior f : (x: X) -> X constructs X
                let positive (v: Int) = v >= 0
                let f (x) = if positive(x.value) then x else X(0)
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.F"), "the annotated-helper form still compiles");
    }

    @Test
    void aRecursiveHelperStillRequiresParameterAnnotations() {
        // A recursive helper is lowered to a method, not inlined, so there is no call-site expansion to
        // infer from; its parameter types stay required.
        String src = """
                module demo
                data N = Int
                behavior f : (n: N) -> N constructs N
                let count (n): Int = if n == 0 then 0 else count(n - 1) + 1
                let f (n) = N(count(n.value))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("count")
                && (ex.getMessage().contains("annotate") || ex.getMessage().contains("型注釈")),
                ex.getMessage());
    }
}
