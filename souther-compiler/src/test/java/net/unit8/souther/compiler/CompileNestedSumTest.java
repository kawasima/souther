package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A sum may have a sum as an arm — spec 8.3's 費用負担区分 = 自社負担 | 先方負担, where 自社負担 is
 * itself 立替 | 仮払い | 会社カード. The derived codecs used to reject that: an arm had to be a
 * product or a unit, so the spec's own model would not compile.
 */
class CompileNestedSumTest {

    private static final String MODULE = """
            module demo

            data 立替
            data 仮払い
            data 会社カード
            data 先方負担
            data 自社負担     = 立替 | 仮払い | 会社カード
            data 費用負担区分 = 自社負担 | 先方負担
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    /**
     * The derived codec dispatches over the leaves, so a nested arm is tagged directly. Tagging
     * the direct arm instead put two levels on one "type" key: the encoder wrote
     * {@code {type: 自社負担}}, which lost the leaf and which the decoder then rejected.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aLeafOfANestedSumIsTaggedDirectly() throws Exception {
        BytesClassLoader loader = loader();
        Decoder<Map<String, Object>, Object> dec =
                (Decoder<Map<String, Object>, Object>) loader.loadClass("demo.費用負担区分")
                        .getMethod("decoder").invoke(null);

        Object v = ((Ok<Object>) dec.decode(Map.of("type", "立替"), Path.ROOT)).value();
        assertInstanceOf(loader.loadClass("demo.立替"), v);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aNestedSumRoundTrips() throws Exception {
        BytesClassLoader loader = loader();
        Encoder enc = (Encoder) loader.loadClass("demo.費用負担区分").getMethod("encoder").invoke(null);
        Decoder dec = (Decoder) loader.loadClass("demo.費用負担区分").getMethod("decoder").invoke(null);

        java.lang.reflect.Constructor<?> c = loader.loadClass("demo.仮払い").getDeclaredConstructors()[0];
        c.setAccessible(true);
        Object 仮払い = c.newInstance();

        Object back = ((Ok) dec.decode(enc.encode(仮払い), Path.ROOT)).value();
        assertInstanceOf(loader.loadClass("demo.仮払い"), back, "decode(encode(v)) == v (spec 11.3)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDirectArmOfTheOuterSumStillDecodes() throws Exception {
        BytesClassLoader loader = loader();
        Decoder<Map<String, Object>, Object> dec =
                (Decoder<Map<String, Object>, Object>) loader.loadClass("demo.費用負担区分")
                        .getMethod("decoder").invoke(null);
        Object v = ((Ok<Object>) dec.decode(Map.of("type", "先方負担"), Path.ROOT)).value();
        assertInstanceOf(loader.loadClass("demo.先方負担"), v);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void theOuterAndInnerSumAgreeOnTheTag() throws Exception {
        BytesClassLoader loader = loader();
        Encoder outer = (Encoder) loader.loadClass("demo.費用負担区分").getMethod("encoder").invoke(null);
        Encoder inner = (Encoder) loader.loadClass("demo.自社負担").getMethod("encoder").invoke(null);

        java.lang.reflect.Constructor<?> c = loader.loadClass("demo.立替").getDeclaredConstructors()[0];
        c.setAccessible(true);
        Object 立替 = c.newInstance();

        assertEquals("立替", ((Map<?, ?>) outer.encode(立替)).get("type"));
        assertEquals("立替", ((Map<?, ?>) inner.encode(立替)).get("type"),
                "both levels tag the leaf, so a value encoded at one decodes at the other");
    }
}
