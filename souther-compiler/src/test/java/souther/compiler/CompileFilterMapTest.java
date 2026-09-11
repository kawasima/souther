package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code List.filterMap} (Elm) / {@code List.choose} (F#): the step answers with an optional and only
 * what is there survives. It is the one stdlib signature that says {@code 'b?}, an optional written in
 * a type — a core privilege like the type variables beside it. A caller passes a lambda that reads an
 * optional (a {@code ?} field, {@code Map.get}, {@code List.find}) and never names the type.
 */
class CompileFilterMapTest {

    @Test
    void filterMapOverAnOptionalField() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( filterMap )

                data 担当者 = String
                data Issue = { id: String, assignee: 担当者? }
                data In = { issues: List<Issue> }
                data Out = { names: List<担当者> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { names = filterMap(x -> x.assignee, i.issues) }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("issues", List.of(
                Map.of("id", "i-1", "assignee", "kawasima"),
                Map.of("id", "i-2"),
                Map.of("id", "i-3", "assignee", "aoyagi"))));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(behavior, in));

        assertEquals(List.of("kawasima", "aoyagi"), out.get("names"),
                "the unassigned issue contributes nothing, and no sentinel is written");
    }

    @Test
    void filterMapOverAMapLookup() throws Exception {
        // The other everyday source of an optional: a lookup that may miss.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( filterMap )

                data In = { wanted: List<String>, stock: Map<String, Int> }
                data Out = { found: List<Int> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { found = filterMap(k -> Map.get(k, i.stock), i.wanted) }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of(
                "wanted", List.of("apple", "nope", "orange"),
                "stock", Map.of("apple", 3L, "orange", 5L)));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(behavior, in));

        assertEquals(List.of(3L, 5L), out.get("found"));
    }

    @Test
    void aBlockAnsweringWithAPlainValueIsRejected() {
        // `'b?` is open in `'b` but not in its shape: what comes back has to be an optional.
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                import List ( filterMap )

                data 担当者 = String
                data Issue = { id: String, assignee: 担当者? }
                data In = { issues: List<Issue> }
                data Out = { ids: List<String> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { ids = filterMap(x -> x.id, i.issues) }
                """));
        assertTrue(e.getMessage().contains("filterMap") || e.getMessage().contains("block"),
                e.getMessage());
    }

    @Test
    void aUserModuleCannotWriteAnOptionalInASignature() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                data 担当者 = String
                data Out = { v: Int }

                behavior run : (o: Out) -> Out constructs Out

                let 名前 (a: 担当者?): 担当者? = a
                let run (o) = o
                """));
        assertTrue(e.getMessage().contains("optional"), e.getMessage());
    }

    @Test
    void theOptionalInASignatureErrorSaysWhatToWriteInstead() {
        // The rule alone left no way forward: the model wrote the annotation because it wanted a
        // step for filterMap, and both ways to get one are ways of not writing the type (issue #166).
        String src = """
                module demo

                data Name = String
                data Issue = { id: String, assignee: Name? }

                behavior run : (i: Issue) -> Issue

                let pick (x: Issue): Name? = x.assignee
                let run (i) = i
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        String out = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
        assertTrue(out.contains("flatMap"), out);
    }

    @Test
    void aHelperWithAnInferredResultIsAStep() throws Exception {
        // The step can be named: a helper leaves its result type off and the optional is inferred,
        // so factoring the projection out of the lambda does not need `Name?` to be writable.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( filterMap )

                data Name = String
                data Issue = { id: String, assignee: Name? }
                data In = { issues: List<Issue> }
                data Out = { names: List<Name> }

                behavior run : (i: In) -> Out constructs Out

                let assigneeOf (x: Issue) = x.assignee

                let run (i) = Out { names = filterMap(assigneeOf, i.issues) }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("issues", List.of(
                Map.of("id", "i-1", "assignee", "kawasima"),
                Map.of("id", "i-2"),
                Map.of("id", "i-3", "assignee", "aoyagi"))));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(behavior, in));

        assertEquals(List.of("kawasima", "aoyagi"), out.get("names"));
    }

    @Test
    void anOptionalFieldIsStillWritten() throws Exception {
        // The field form is untouched by the core privilege: this is a user module.
        Compiler.compile("""
                module demo

                data 担当者 = String
                data Issue = { id: String, assignee: 担当者? }

                behavior run : (i: Issue) -> Issue

                let run (i) = i
                """);
    }
}
