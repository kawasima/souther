package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bare field access {@code .field} in an expression is sugar for the getter {@code (x) -> x.field}
 * (Elm-style), so a field can be projected point-free: {@code List.map(.value, xs)}. It desugars to an
 * ordinary second-class block, so it flows through the combinator machinery like any lambda.
 */
class CompileFieldGetterTest {

    private static final String NEWTYPE = """
            module demo

            data Id = String
            data In = { ids: List<Id> }
            data Out = { vs: List<String> }

            behavior run : (i: In) -> Out constructs Out

            let run (i) = Out { vs = %s }
            """;

    @SuppressWarnings("unchecked")
    private List<Object> runNewtype(String mapExpr) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(
                Compiler.compile(NEWTYPE.formatted(mapExpr)), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In", Map.of("ids", List.of("a", "b", "c")));
        Object behavior = loader.loadClass("demo.Run$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);
        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        return (List<Object>) m.get("vs");
    }

    @Test
    void getterProjectsANewtypeBaseValue() throws Exception {
        assertEquals(List.of("a", "b", "c"), runNewtype("List.map(.value, i.ids)"));
    }

    @Test
    void getterComposesWithThePipe() throws Exception {
        assertEquals(List.of("a", "b", "c"), runNewtype("i.ids |> List.map(.value)"));
    }

    @Test
    void aLetBoundGetterCanBeApplied() throws Exception {
        // a getter is lambda-equivalent: it may be let-bound and applied, like any block
        assertEquals(List.of("a", "b", "c"), runNewtype("{ let g = .value  List.map(g, i.ids) }"));
    }

    @Test
    void aGetterInValuePositionIsRejected() {
        // a getter is a second-class block, so storing it (not applying) is `block is not a value`
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(NEWTYPE.formatted(".value")));
        assertTrue(e.getMessage().contains("not a value"), e.getMessage());
    }

    @Test
    void getterOnANonexistentFieldIsRejected() {
        // a getter is an ordinary field access under the hood, so an unknown field is a clean error
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(NEWTYPE.formatted("List.map(.nope, i.ids)")));
        assertTrue(e.getMessage().contains("nope") || e.getMessage().contains("field"), e.getMessage());
    }

    @Test
    void getterProjectsAPlainRecordField() throws Exception {
        // the getter is general: it works for any field, not only a newtype's `value`
        String src = """
                module demo

                data Row = { n: Int }
                data In = { rows: List<Row> }
                data Out = { ns: List<Int> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { ns = List.map(.n, i.rows) }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In",
                Map.of("rows", List.of(Map.of("n", 1L), Map.of("n", 2L))));
        Object behavior = loader.loadClass("demo.Run$Impl").getConstructor().newInstance();
        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(behavior, in));
        assertEquals(List.of(1L, 2L), m.get("ns"));
    }
}
