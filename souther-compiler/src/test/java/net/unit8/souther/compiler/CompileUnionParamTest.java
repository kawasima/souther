package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A behavior parameter may be an anonymous union (spec 12.2), and its value is consumed by
 * matching each arm (spec 14.2, 16.3): {@code (app: Sub | Pre) -> ...} with a `match app`.
 */
class CompileUnionParamTest {

    private static final String MODULE = """
            module demo

            data Sub { x: Int }
            data Pre { y: Int }
            data Done { v: Int }

            behavior finish(app: Sub | Pre) -> Done constructs Done {
                match app {
                    case Sub as s => Done { v: s.x }
                    case Pre as p => Done { v: p.y }
                }
            }
            """;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private long finish(String armType, long n) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo." + armType).getMethod("decoder").invoke(null);
        Object arg = d.decode(Raw.integer(n));
        Object done = ((Behavior<Object, Object>) loader.loadClass("demo.finish")
                .getConstructor().newInstance()).apply(arg);
        Encoder enc = (Encoder) loader.loadClass("demo.Done").getMethod("encoder").invoke(null);
        return ((Raw.IntValue) enc.encode(done)).value();
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
