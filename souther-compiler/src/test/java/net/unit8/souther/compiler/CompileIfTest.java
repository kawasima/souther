package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** End-to-end test for {@code if cond then a else b} expressions (spec 16.2). */
class CompileIfTest {

    private static final String MODULE = """
            module demo

            data In  { value: Int }
            data Out { label: String }

            behavior classify(x: In) -> Out constructs Out {
                Out { label: if x.value >= 100 then "high" else "low" }
            }
            """;

    @SuppressWarnings("unchecked")
    private String classify(BytesClassLoader loader, long n) throws Exception {
        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(n, Path.ROOT)).value();
        Object behavior = loader.loadClass("demo.classify").getConstructor().newInstance();
        Object out = ((Behavior<Object, Object>) behavior).apply(in);
        // Out is a single-field newtype, so its encoder yields the bare String.
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        return (String) enc.encode(out);
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
                data Out { value: Int }
                behavior bad(x: Int) -> Out constructs Out {
                    Out { value: if x >= 0 then 1 else "no" }
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
