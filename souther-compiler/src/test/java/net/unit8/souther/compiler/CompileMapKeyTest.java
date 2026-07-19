package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** A Map keyed by a String-backed newtype ([#stdlib-map]): the key carries its domain type, and the
 *  map is keyed by the newtype value (value equality). */
class CompileMapKeyTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapKeyedByANewtype() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Map ( singleton, insert, containsKey, size, keys )

                data 商品ID = String
                data 在庫 = { 数量: Int }

                data In = { seed: Int }
                data Out = {
                    total: Int
                    , hasP1: Bool
                    , hasP9: Bool
                    , keyList: List<商品ID>
                }

                behavior run : (i: In) -> Out constructs Out, 在庫, 商品ID

                let run (i) = {
                    let m = insert(商品ID("P-02"), 在庫 { 数量 = 5 },
                                   singleton(商品ID("P-01"), 在庫 { 数量 = 3 }))
                    Out {
                        total = size(m),
                        hasP1 = containsKey(商品ID("P-01"), m),
                        hasP9 = containsKey(商品ID("P-09"), m),
                        keyList = keys(m)
                    }
                }
                """), getClass().getClassLoader());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(Map.of("seed", 0L), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(in);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> m = (Map<?, ?>) enc.encode(out);
        assertEquals(2L, m.get("total"));
        assertEquals(true, m.get("hasP1"), "a fresh 商品ID(\"P-01\") matches by value equality");
        assertEquals(false, m.get("hasP9"));
        assertEquals(List.of("P-01", "P-02"), m.get("keyList"), "keys are 商品ID, encoded bare");
    }

    /** A {@code Map<商品ID, V>} crosses the codec boundary: the derived decoder reads a JSON object
     *  whose string keys are constructed into 商品ID (invariant-checked), and the encoder writes the
     *  map back with keys rendered bare. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void newtypeKeyedMapCrossesTheBoundary() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Map ( insert, size, containsKey )

                data 商品ID = String

                data In = { stock: Map<商品ID, Int> }
                data Out = {
                    stock: Map<商品ID, Int>
                    , n: Int
                    , hasP1: Bool
                    , more: Map<商品ID, Int>
                }

                behavior run : (i: In) -> Out constructs Out, 商品ID

                let run (i) = Out {
                    stock = i.stock,
                    n = size(i.stock),
                    hasP1 = containsKey(商品ID("P-01"), i.stock),
                    more = insert(商品ID("P-03"), 9, i.stock)
                }
                """), getClass().getClassLoader());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(
                Map.of("stock", Map.of("P-01", 3L, "P-02", 5L)), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(in);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> m = (Map<?, ?>) enc.encode(out);
        assertEquals(Map.of("P-01", 3L, "P-02", 5L), m.get("stock"), "keys are rendered bare");
        assertEquals(2L, m.get("n"));
        assertEquals(true, m.get("hasP1"), "the decoded key equals a fresh 商品ID(\"P-01\")");
        assertEquals(Map.of("P-01", 3L, "P-02", 5L, "P-03", 9L), m.get("more"));
    }

    /** A key that violates the key newtype's invariant fails the decode at the key's path, rather than
     *  aborting: at the boundary an invariant is a decode failure. */
    @Test
    void aKeyViolatingItsInvariantFailsDecode() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data コード = String
                invariant String.length(value) == 4

                data In = { m: Map<コード, Int> }
                """), getClass().getClassLoader());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object result = inDec.decode(Map.of("m", Map.of("AB", 1L)), Path.ROOT);
        assertInstanceOf(Err.class, result, "the key \"AB\" is not length 4, so the decode fails");
    }
}
