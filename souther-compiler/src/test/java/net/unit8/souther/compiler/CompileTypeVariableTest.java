package net.unit8.souther.compiler;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Lower;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.codegen.Backend;
import net.unit8.souther.compiler.derive.Deriver;
import net.unit8.souther.compiler.syntax.Parser;
import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Type variables {@code 'a} (ADR-0028) are written only in the shipped core. A non-recursive core
 * helper that carries one is monomorphised by inline expansion — the variable resolves to the
 * concrete argument type at each call site — so no polymorphic method is emitted. A user module may
 * not write a type variable at all; that is what keeps user models bounded.
 */
class CompileTypeVariableTest {

    /** Compiles a core (reserved-namespace) module directly, bypassing the user-facing guard. */
    private static Map<String, byte[]> compileCore(String src) {
        Ast.Module m = Deriver.derive(Parser.parse(src));
        TypeChecker.check(m);
        return Backend.generate(Lower.run(m));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCoreGenericHelperMonomorphisesByInlining() throws Exception {
        // `identity` is written once with `'a`; the `Int` call site resolves it to Int on inlining.
        String core = """
                module souther.gen

                data In = { v: Int }
                data Out = { v: Int }

                behavior echo : (i: In) -> Out constructs Out

                let identity (x: 'a) = x
                let echo (i) = Out { v = identity(i.v) }
                """;
        BytesClassLoader loader = new BytesClassLoader(compileCore(core), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("souther.gen.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) d.decode(Map.of("v", 7L), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("souther.gen.Echo")
                .getConstructor().newInstance()).apply(in);
        Encoder enc = (Encoder) loader.loadClass("souther.gen.Out").getMethod("encoder").invoke(null);
        assertEquals(7L, ((Map<?, ?>) enc.encode(out)).get("v"), "identity returns its argument unchanged");
    }

    @Test
    void aTypeVariableInAListPositionIsAllowedInTheCore() {
        String core = """
                module souther.gen
                let firstOr (xs: List<'a>, fallback: 'a) = fallback
                """;
        assertDoesNotThrow(() -> compileCore(core));
    }

    @Test
    void aUserModuleCannotWriteATypeVariable() {
        String user = """
                module demo
                data Out = { v: Int }
                behavior echo : (i: Out) -> Out constructs Out
                let identity (x: 'a) = x
                let echo (i) = i
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(user));
        assertEquals(true, e.getMessage().contains("type variable"), e.getMessage());
    }
}
