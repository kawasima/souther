package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Map keyed by a String-backed newtype ([#stdlib-map], ADR-0040): the key carries its domain
 *  type, and the map is keyed by the newtype value (value equality, ADR-0009). */
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

    @Test
    void aNewtypeKeyedMapCannotYetBeAField() {
        // the typed-key codec is not implemented yet: such a map is usable in a body, not at the
        // boundary. A data field of that type is a clear error, not a mis-encoding (ADR-0040).
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data 商品ID = String
                data Bad = { stock: Map<商品ID, Int> }
                """));
        assertTrue(e.getMessage().contains("typed-key codec"), e.getMessage());
    }
}
