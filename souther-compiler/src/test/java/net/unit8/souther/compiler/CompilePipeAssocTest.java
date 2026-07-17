package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code >>} is associative: naming an intermediate composition does not change the result (spec
 * 14.2). {@code half = split >> work} remembers that {@code Off} left the main line at {@code work};
 * {@code half >> finish} must therefore keep {@code Off} retired, exactly as the flat
 * {@code split >> work >> finish} would, even though {@code finish} could accept an {@code Off}.
 */
class CompilePipeAssocTest {

    private static final String MODULE = """
            module demo

            data In = Int
            data Mid = Int
            data Off = Int
            data Out = Int

            behavior split = (i: In) -> Mid | Off constructs Mid, Off
            fn split (i) = {
                require i.value <= 100 else Off { value: i.value }
                Mid { value: i.value }
            }

            behavior work = (m: Mid) -> Out constructs Out
            fn work (m) = Out { value: m.value }

            behavior finish = (x: Off | Out) -> Out constructs Out
            fn finish (x) = Out { value: 0 }

            behavior half = split >> work
            behavior flow = half >> finish
            """;

    @SuppressWarnings("unchecked")
    private String run(BytesClassLoader loader, long n) throws Exception {
        Decoder dec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) dec.decode(n, Path.ROOT)).value();
        Object flow = loader.loadClass("demo.Flow").getConstructor().newInstance();
        return ((Behavior<Object, Object>) flow).apply(in).getClass().getName();
    }

    @Test
    void aRetiredArmStaysRetiredAcrossANamedIntermediate() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        // 500 > 100: split yields Off, which left the main line at work — finish must not pick it up
        assertEquals("demo.Off", run(loader, 500),
                "half >> finish must equal split >> work >> finish; Off stays retired");
        // 50 <= 100: split yields Mid, work makes Out, finish consumes it
        assertEquals("demo.Out", run(loader, 50));
    }
}
