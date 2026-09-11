package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An application is typed against a declared signature by one routine, whichever holds the
 * signature — a kernel declaration or a function type in scope (issue #276). These programs walk
 * both holders through the same shapes: a function argument typed against parameters the value
 * arguments settled, and the empty-seed re-pointing that used to belong to only one of them.
 */
class CompileSignatureApplicationTest {

    private static Diagnostic diagnosticOf(String src) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        return e.diagnostic();
    }

    /** The same application shape through both holders in one program: {@code Option.map} is a
     *  kernel taking a function, {@code g} is a function type in scope. Both settle the function
     *  argument's parameter from a value argument and both answer the declared result. */
    @Test
    void aKernelAndAFunctionValueTypeTheSameApplication() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data In = { xs: List<Int> }
                data Out = { viaKernel: Int, viaValue: Int }

                behavior run : (i: In) -> Out constructs Out

                let twice (g: (Int) -> Int, n: Int) = g(g(n))

                let run (i) = {
                    Out {
                        viaKernel = List.get(0, i.xs) |> Option.map(n -> n + 1) |> Option.withDefault(0),
                        viaValue = twice(n -> n + 1, 40)
                    }
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("xs", List.of(1L)));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(behavior, in));

        assertEquals(2L, out.get("viaKernel"), "the kernel typed its step against the settled element");
        assertEquals(42L, out.get("viaValue"), "the function value typed its argument the same way");
    }

    /** A kernel taking a function over an empty seed it cannot type: the failure is re-pointed at
     *  the seed, exactly as it is for a fold written against a signature in scope. The re-pointing
     *  used to live only on the in-scope side. */
    @Test
    void anEmptySeedFailureInAKernelStepIsRepointedAtTheSeed() {
        String source = """
                module demo
                data Out = { xs: List<Int> }
                behavior run : (i: Int) -> Out constructs Out
                let run (i) = {
                    let sorted = List.sortBy(x -> x + 1, [])
                    Out { xs = sorted }
                }
                """;
        Diagnostic d = diagnosticOf(source);
        assertEquals("check.fold.seed.title", d.titleKey());
        assertEquals(5, WhereItSits.in(source, ((Primary.InSource) d.primary()).place().region()).start().line());
    }
}
