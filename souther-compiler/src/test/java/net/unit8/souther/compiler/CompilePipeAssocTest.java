package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code >->} is associative: naming an intermediate composition does not change the result (spec
 * 14.2). {@code half = split >-> work} remembers that {@code Off} left the main line at {@code work};
 * {@code half >-> finish} must therefore keep {@code Off} retired, exactly as the flat
 * {@code split >-> work >-> finish} would, even though {@code finish} could accept an {@code Off}.
 */
class CompilePipeAssocTest {

    private static final String MODULE = """
            module demo

            data In = Int
            data Mid = Int
            data Off = Int
            data Out = Int
            data OffOut = Off | Out

            behavior split : (i: In) -> Mid | Off constructs Mid, Off
            let split (i) = {
                require i.value <= 100 else Off { value = i.value }
                Mid { value = i.value }
            }

            behavior work : (m: Mid) -> Out constructs Out
            let work (m) = Out { value = m.value }

            behavior finish : (x: OffOut) -> Out constructs Out
            let finish (x) = Out { value = 0 }

            behavior half = split >-> work
            behavior flow = half >-> finish
            """;

    private String run(BytesClassLoader loader, long n) throws Exception {
        Object in = Codecs.decoded(loader, "demo.In", n);
        Object flow = loader.loadClass("demo.Flow" + "$Impl").getConstructor().newInstance();
        return Codecs.apply(flow, in).getClass().getName();
    }

    @Test
    void aRetiredCaseStaysRetiredAcrossANamedIntermediate() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        // 500 > 100: split yields Off, which left the main line at work — finish must not pick it up
        assertEquals("demo.Off", run(loader, 500),
                "half >-> finish must equal split >-> work >-> finish; Off stays retired");
        // 50 <= 100: split yields Mid, work makes Out, finish consumes it
        assertEquals("demo.Out", run(loader, 50));
    }
}
