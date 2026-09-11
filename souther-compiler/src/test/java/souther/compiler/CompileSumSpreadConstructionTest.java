package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sum whose every case spreads the same data is a spread source, and the fields it contributes are
 * that shared data's — the mirror of reading one off the sum ({@link CompileSumCommonFieldTest}). The
 * sharing is nominal, so cases that happen to declare a field of the same name contribute nothing, and
 * a field only some cases carry is not offered.
 */
class CompileSumSpreadConstructionTest {

    private static final String MODULE = """
            module demo

            data Common = { id: String }
            data Draft = { ...Common }
            data Sent = { ...Common, at: String }
            data Doc = Draft | Sent
            data Out = { ...Common, tag: String }

            behavior run : (d: Doc, tag: String) -> Out constructs Out

            let run (d, tag) = Out { ...d, tag = tag }
            """;

    private static String rendered(String src, Locale locale) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        Diagnostic d = e.diagnostic();
        return new HumanRenderer(false).render(d, new SourceContext("demo.sou", src, SourceLayout.of(src)), locale);
    }

    private static Map<?, ?> runWith(BytesClassLoader loader, Object raw) throws Exception {
        Object in = Codecs.decoded(loader, "demo.Doc", raw);
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        Object out = behavior.getClass().getMethod("apply", Object.class, Object.class)
                .invoke(behavior, in, "t");
        return (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
    }

    @Test
    void aSumWhoseCasesShareASpreadIsSpreadIntoAConstruction() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        assertEquals("d-1", runWith(loader, Map.of("type", "Draft", "id", "d-1")).get("id"));
        assertEquals("s-1",
                runWith(loader, Map.of("type", "Sent", "id", "s-1", "at", "10:00")).get("id"));
    }

    @Test
    void anIntFieldIsCarriedWithTheDescriptorTheCaseRecordDeclares() throws Exception {
        String src = """
                module demo

                data Common = { n: Int }
                data A = { ...Common }
                data B = { ...Common, s: String }
                data S = A | B
                data Out = { ...Common, tag: String }

                behavior run : (s: S) -> Out constructs Out

                let run (s) = Out { ...s, tag = "t" }
                """;
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.S", Map.of("type", "A", "n", 7L));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        assertEquals(7L, ((Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(behavior, in))).get("n"));
    }

    @Test
    void aSpreadReachedThroughAnotherSpreadIsStillCommon() throws Exception {
        String src = """
                module demo

                data Common = { id: String }
                data Base = { ...Common, at: String }
                data Draft = { ...Base }
                data Sent = { ...Common, to: String }
                data Doc = Draft | Sent
                data Out = { ...Common, tag: String }

                behavior run : (d: Doc) -> Out constructs Out

                let run (d) = Out { ...d, tag = "t" }
                """;
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.Doc",
                Map.of("type", "Draft", "id", "d-1", "at", "10:00"));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        assertEquals("d-1", ((Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(behavior, in))).get("id"));
    }

    @Test
    void aCaseThatIsItselfASumCarriesTheFieldsThroughBothInterfaces() throws Exception {
        String src = """
                module demo

                data Common = { id: String }
                data Draft = { ...Common }
                data Sent = { ...Common, at: String }
                data Filed = { ...Common, by: String }
                data Closed = Sent | Filed
                data Doc = Draft | Closed
                data Out = { ...Common, tag: String }

                behavior run : (d: Doc) -> Out constructs Out

                let run (d) = Out { ...d, tag = "t" }
                """;
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.Doc",
                Map.of("type", "Filed", "id", "f-1", "by", "kawasima"));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        assertEquals("f-1", ((Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(behavior, in))).get("id"));
    }

    @Test
    void anImportedSumIsSpreadTheSameWay() throws Exception {
        String docs = """
                module docs exposing ( Doc, Draft, Sent, Common )

                data Common = { id: String }
                data Draft = { ...Common }
                data Sent = { ...Common, at: String }
                data Doc = Draft | Sent
                """;
        String app = """
                module app
                import docs ( Doc, Common )

                data Out = { ...Common, tag: String }

                behavior run : (d: Doc) -> Out constructs Out

                let run (d) = Out { ...d, tag = "t" }
                """;
        BytesClassLoader loader = new BytesClassLoader(
                Compiler.compileModules(List.of(docs, app)), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "docs.Doc", Map.of("type", "Draft", "id", "d-1"));
        Object behavior = Emitted.behavior(loader, "app", "run").getConstructor().newInstance();
        assertEquals("d-1", ((Map<?, ?>) Codecs.encode(loader, "app.Out",
                Codecs.apply(behavior, in))).get("id"));
    }

    @Test
    void anExampleRowWalksTheSameConstruction() {
        String src = """
                module demo

                data Common = { id: String }
                data Draft = { ...Common }
                data Sent = { ...Common, at: String }
                data Doc = Draft | Sent
                data Out = { ...Common, tag: String }

                behavior tag : (d: Doc) -> Out constructs Out

                let tag (d) = Out { ...d, tag = "t" }

                example tag
                    | "a case with nothing of its own"
                        : (Draft { id = "d-1" }) -> Out { id = "d-1", tag = "t" }
                    | "a case carrying more than the shared part"
                        : (Sent { id = "s-1", at = "10:00" }) -> Out { id = "s-1", tag = "t" }
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void aFieldOnlySomeCasesCarryIsNotOffered() {
        String src = """
                module demo

                data Common = { id: String }
                data Draft = { ...Common }
                data Sent = { ...Common, at: String }
                data Doc = Draft | Sent
                data Out = { ...Common, at: String }

                behavior run : (d: Doc) -> Out constructs Out

                let run (d) = Out { ...d }
                """;
        String out = rendered(src, Locale.ENGLISH);

        assertTrue(out.contains("`at`"), out);
        // the plain hint says to supply it; this one says why the spread did not
        assertTrue(out.contains("every case of `Doc` spreads"), out);
    }

    @Test
    void severalSumsInOneConstructionAreAllNamedRatherThanOnePickedByPosition() {
        String src = """
                module demo

                data CommonA = { id: String }
                data CommonB = { at: String }
                data A1 = { ...CommonA }
                data A2 = { ...CommonA, x: String }
                data S1 = A1 | A2
                data B1 = { ...CommonB }
                data B2 = { ...CommonB, y: String }
                data S2 = B1 | B2
                data Out = { id: String, at: String, x: String }

                behavior run : (s1: S1, s2: S2) -> Out constructs Out

                let run (s1, s2) = Out { ...s1, ...s2 }
                """;
        String out = rendered(src, Locale.ENGLISH);

        // `x` is a field of A2, a case of S1. Naming the last sum spread would send the author to open
        // S2, whose cases never had it.
        assertTrue(out.contains("S1, S2"), out);
        assertFalse(out.contains("open `S2` with `match`"), out);
    }

    @Test
    void aSumWhoseCasesShareNoSpreadIsNotASpreadSource() {
        String src = """
                module demo

                data A = { id: String, x: Int }
                data B = { id: String, y: Int }
                data S = A | B
                data Out = { id: String }

                behavior run : (s: S) -> Out constructs Out

                let run (s) = Out { ...s }
                """;
        String out = rendered(src, Locale.ENGLISH);

        assertTrue(out.contains("`S`"), out);
        assertTrue(out.contains("spread"), out);
        assertTrue(out.contains("`match`"), out);
    }

    @Test
    void anAnonymousUnionIsNotASpreadSource() {
        String src = """
                module demo

                data Common = { id: String }
                data Draft = { ...Common }
                data Sent = { ...Common, at: String }
                data Out = { ...Common, tag: String }

                behavior run : (flag: Bool) -> Out constructs Out

                let run (flag) = {
                    let d = if flag then Draft { id = "d" } else Sent { id = "s", at = "10:00" }
                    Out { ...d, tag = "t" }
                }
                """;
        String out = rendered(src, Locale.ENGLISH);

        assertTrue(out.contains("`d`"), out);
        // naming the union is what makes its shared part reachable, and the report says so
        assertTrue(out.contains("Name the union with a `data` declaration"), out);
    }

    @Test
    void theJapaneseReportSaysWhyTheSumHasNoSharedPart() {
        String src = """
                module demo

                data A = { id: String, x: Int }
                data B = { id: String, y: Int }
                data S = A | B
                data Out = { id: String }

                behavior run : (s: S) -> Out constructs Out

                let run (s) = Out { ...s }
                """;
        String out = rendered(src, Locale.JAPANESE);

        assertTrue(out.contains("`S`"), out);
        assertTrue(out.contains("スプレッド"), out);
    }
}
