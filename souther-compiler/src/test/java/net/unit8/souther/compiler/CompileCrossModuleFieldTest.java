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

/**
 * Spec 8.5 / 19.2: a behavior can read a field of an imported data. Module = package, so the
 * field itself is private across the boundary; the read must go through the public accessor the
 * exposed data generates (19.2), not a raw {@code getfield}, which would fail JVM access checks.
 */
class CompileCrossModuleFieldTest {

    private static final String A = """
            module a exposing ( 従業員, 従業員ID )

            data 従業員ID = String
            data 従業員 = { id: 従業員ID, 上長ID: 従業員ID }
            """;

    private static final String B = """
            module b

            import a ( 従業員, 従業員ID )

            data 申請 = { 申請者: 従業員 }

            behavior 上長IDを得る : (req: 申請) -> 従業員ID
            let 上長IDを得る (req) = req.申請者.上長ID
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aBehaviorReadsAFieldOfAnImportedData() throws Exception {
        Map<String, byte[]> classes = Compiler.compileModules(List.of(A, B));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Decoder reqDec = (Decoder) loader.loadClass("b.申請").getMethod("decoder").invoke(null);
        Object req = ((Ok) reqDec.decode(
                Map.of("申請者", Map.of("id", "e-1", "上長ID", "e-2")), Path.ROOT)).value();

        Object beh = loader.loadClass("b.上長IDを得る").getConstructor().newInstance();
        Object r = ((Behavior) beh).apply(req);

        // 上長ID is 従業員ID (a newtype from module a); its encoder yields the bare String.
        Encoder enc = (Encoder) loader.loadClass("a.従業員ID").getMethod("encoder").invoke(null);
        assertEquals("e-2", enc.encode(r));
    }

    private static final String BASE = """
            module base_m exposing ( Base )

            data Base = { a: String, b: String }
            """;

    private static final String DERIVED = """
            module derived_m

            import base_m ( Base )

            data Derived = { a: String, b: String, c: String }

            behavior 拡張する : (base: Base) -> Derived constructs Derived
            let 拡張する (base) = Derived { ...base, c = "x" }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void spreadingAnImportedDataReadsItsFieldsThroughAccessors() throws Exception {
        Map<String, byte[]> classes = Compiler.compileModules(List.of(BASE, DERIVED));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        Decoder baseDec = (Decoder) loader.loadClass("base_m.Base").getMethod("decoder").invoke(null);
        Object base = ((Ok) baseDec.decode(Map.of("a", "A", "b", "B"), Path.ROOT)).value();

        Object beh = loader.loadClass("derived_m.拡張する").getConstructor().newInstance();
        Object r = ((Behavior) beh).apply(base);

        Encoder enc = (Encoder) loader.loadClass("derived_m.Derived").getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(r);
        assertEquals("A", out.get("a"));
        assertEquals("B", out.get("b"));
        assertEquals("x", out.get("c"));
    }
}
