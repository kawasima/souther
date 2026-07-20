package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** End-to-end test for {@code if cond then a else b} expressions (spec 16.2). */
class CompileIfTest {

    private static final String MODULE = """
            module demo

            data In = Int
            data Out = String

            behavior classify : (x: In) -> Out constructs Out

            let classify (x) = Out { value = if x.value >= 100 then "high" else "low" }
            """;

    private String classify(BytesClassLoader loader, long n) throws Exception {
        Object in = Codecs.decoded(loader, "demo.In", n);
        Object behavior = loader.loadClass("demo.Classify").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);
        // Out is a single-field newtype, so its encoder yields the bare String.
        return (String) Codecs.encode(loader, "demo.Out", out);
    }

    @Test
    void choosesBranchByCondition() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        assertEquals("high", classify(loader, 200));
        assertEquals("low", classify(loader, 5));
    }

    @Test
    void branchesMustAgreeOnType() {
        String src = """
                module demo
                data Out = Int
                behavior bad : (x: Int) -> Out constructs Out

                let bad (x) = Out { value = if x >= 0 then 1 else "no" }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
