package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shipped intrinsic (ADR-0028) is a stdlib function whose body is a named primitive — its
 * signature lives in the prelude ({@code souther.string}) instead of hard-coded in the checker. It
 * behaves like a built-in: a call is checked against that signature and the backend emits the
 * primitive. {@code trim} is the first such function moved out of the hard-coded switch.
 */
class CompileIntrinsicTest {

    private static final String MODULE = """
            module demo
            import String ( trim )

            data In = { s: String }
            data Out = { s: String }

            behavior clean : (i: In) -> Out constructs Out

            let clean (i) = Out { s = trim(i.s) }
            """;

    @Test
    void theTrimIntrinsicRunsAsAStdlibFunction() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In", Map.of("s", "  hi  "));
        Object out = Codecs.apply(loader.loadClass("demo.Clean" + "$Impl")
                .getConstructor().newInstance(), in);
        assertEquals("hi", ((Map<?, ?>) Codecs.encode(loader, "demo.Out", out)).get("s"), "trim strips surrounding whitespace");
    }

    /** The intrinsic keeps a real signature: a non-String argument is still rejected. */
    @Test
    void trimStillTypeChecksItsArgument() {
        String src = MODULE.replace("trim(i.s)", "trim(5)");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("argument"), e.getMessage());
    }

    /**
     * A generic intrinsic monomorphises at the call site: {@code values(m: Map<String, 'a>):
     * List<'a>} learns {@code 'a} from the argument, so the result's element type is concrete and
     * an element read type-checks. If the result stayed {@code List<'a>}, {@code x.n} would not.
     */
    @Test
    void aGenericIntrinsicResolvesItsResultElementType() throws Exception {
        String src = """
                module demo
                import List ( get )
                import Map ( values )

                data Item = { n: Int }
                data Store = { byName: Map<String, Item> }
                data Out = { n: Int }

                behavior firstValue : (s: Store) -> Out constructs Out

                let firstValue (s) =
                    match get(0, values(s.byName)) with
                        | Some x -> Out { n = x.n }
                        | None -> Out { n = 0 }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object s = Codecs.decoded(loader, "demo.Store", Map.of("byName", Map.of("a", Map.of("n", 42L))));
        Object out = Codecs.apply(loader.loadClass("demo.FirstValue" + "$Impl")
                .getConstructor().newInstance(), s);
        assertEquals(42L, ((Map<?, ?>) Codecs.encode(loader, "demo.Out", out)).get("n"), "values(m) is List<Item>, so x.n is an Int");
    }

    /** A user module cannot write `intrinsic`; it is a core privilege. */
    @Test
    void aUserModuleCannotDeclareAnIntrinsic() {
        String src = """
                module demo
                data Out = { s: String }
                behavior clean : (i: Out) -> Out constructs Out
                let strip (s: String): String = intrinsic "string.trim"
                let clean (i) = i
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
