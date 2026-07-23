package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sum may be compared with one of its cases (`役職 == 一般社員`): a case value is a value of its
 * sum (case→sum is transparent, spec §sum-data), so this is a sum-vs-sum comparison by case (spec
 * §equality) — the same as Elm's `x == Idle` on a no-argument variant. Unrelated types (two
 * different newtypes) still do not compare.
 */
class CompileSumCaseEqualityTest {

    private Object behavior(BytesClassLoader loader) throws Exception {
        return loader.loadClass("demo.Run$Impl").getConstructor().newInstance();
    }

    @Test
    void aSumComparesByCaseAgainstOneOfItsCases() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data 一般社員
                data 管理職
                data 役職 = 一般社員 | 管理職

                data In = { senior: Bool }
                data Out = { junior: Bool, notJunior: Bool }

                behavior run : (i: In) -> Out constructs Out, 一般社員, 管理職

                let run (i) = {
                    let r = if i.senior then 管理職 else 一般社員
                    Out { junior = r == 一般社員, notJunior = r /= 一般社員 }
                }
                """), getClass().getClassLoader());
        Object b = behavior(loader);

        Map<?, ?> juniorRole = (Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(b, Codecs.decoded(loader, "demo.In", Map.of("senior", false))));
        assertEquals(true, juniorRole.get("junior"), "役職 == 一般社員 is true when the case matches");
        assertEquals(false, juniorRole.get("notJunior"), "/= is its negation");

        Map<?, ?> seniorRole = (Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(b, Codecs.decoded(loader, "demo.In", Map.of("senior", true))));
        assertEquals(false, seniorRole.get("junior"), "役職 == 一般社員 is false for a different case");
        assertEquals(true, seniorRole.get("notJunior"), "/= is true for a different case");
    }

    @Test
    void twoUnrelatedNewtypesStillDoNotCompare() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                data Money = Int
                data Qty = Int
                data P = { m: Money, q: Qty }
                behavior run : (p: P) -> Bool
                let run (p) = p.m == p.q
                """));
        assertTrue(e.getMessage().contains("compare") || e.getMessage().contains("Money"),
                "unrelated newtypes must still not compare: " + e.getMessage());
    }
}
