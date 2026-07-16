package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A behavior parameter may be an anonymous union (spec 12.2), and its value is consumed by
 * matching each arm (spec 14.2, 16.3): {@code (app: Sub | Pre) -> ...} with a `match app`.
 */
class CompileUnionParamTest {

    private static final String MODULE = """
            module demo

            data Sub = { x: Int }
            data Pre = { y: Int }
            data Done = { v: Int }

            behavior finish = (app: Sub | Pre) -> Done constructs Done

            fn finish (app) =
                match app {
                    case Sub as s => Done { v: s.x }
                    case Pre as p => Done { v: p.y }
                }
            """;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private long finish(String armType, long n) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo." + armType).getMethod("decoder").invoke(null);
        Object arg = ((Ok) d.decode(n, Path.ROOT)).value();
        Object done = ((Behavior<Object, Object>) loader.loadClass("demo.finish")
                .getConstructor().newInstance()).apply(arg);
        // Done is a single-field newtype, so its encoder yields the bare Long.
        Encoder enc = (Encoder) loader.loadClass("demo.Done").getMethod("encoder").invoke(null);
        return (Long) enc.encode(done);
    }

    @Test
    void matchesTheSubArm() throws Exception {
        assertEquals(3, finish("Sub", 3));
    }

    @Test
    void matchesThePreArm() throws Exception {
        assertEquals(7, finish("Pre", 7));
    }
}
