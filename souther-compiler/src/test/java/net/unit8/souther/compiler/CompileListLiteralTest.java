package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * List literals, guard-only comprehensions {@code [e | cond]}, and concat {@code ++} let a
 * behavior build a {@code List} — including the conditional-accumulation shape of spec 23's
 * {@code 事前承認要否を判定する -> List<事前承認理由>}, previously unbuildable.
 */
class CompileListLiteralTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<?> pick(long a, long b) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data In = { a: Int  b: Int }

                behavior pick = (x: In) -> List<Int>

                fn pick (x) = [x.a] ++ [x.b | x.b > 0]
                """), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) d.decode(Map.of("a", a, "b", b), Path.ROOT)).value();
        return (List<?>) ((Behavior<Object, Object>) loader.loadClass("demo.Pick")
                .getConstructor().newInstance()).apply(in);
    }

    @Test
    void literalAndGuardComprehensionAndConcat() throws Exception {
        assertEquals(List.of(5L, 3L), pick(5, 3));   // guard passes: both elements
        assertEquals(List.of(5L), pick(5, -1));      // guard fails: only the literal
    }

    // spec 23 shape: a List<sum> built from conditionally-included arm values.
    private static final String REASONS = """
            module demo

            data Amount = Int
            data High = { threshold: Amount }
            data LowRole = { note: String }
            data Reason = High | LowRole
            data In = { cost: Amount }

            behavior reasons = (x: In) -> List<Reason>
                constructs High, LowRole

            fn reasons (x) =
                [High { threshold: x.cost } | x.cost.value > 100] ++ [LowRole { note: "role" }]
            """;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<?> reasons(long cost) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(REASONS), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) d.decode(Map.of("cost", cost), Path.ROOT)).value();
        return (List<?>) ((Behavior<Object, Object>) loader.loadClass("demo.Reasons")
                .getConstructor().newInstance()).apply(in);
    }

    @Test
    void buildsAListOfSumArms() throws Exception {
        List<?> high = reasons(150);
        assertEquals(2, high.size());
        assertEquals("demo.High", high.get(0).getClass().getName());
        assertEquals("demo.LowRole", high.get(1).getClass().getName());

        List<?> low = reasons(50);
        assertEquals(1, low.size());
        assertEquals("demo.LowRole", low.get(0).getClass().getName());
    }
}
