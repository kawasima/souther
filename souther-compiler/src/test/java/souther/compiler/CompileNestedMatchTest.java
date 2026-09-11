package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A match inside a match arm ends where the enclosing match's arms resume: an arm's {@code |} belongs
 * to the innermost match it is indented past. Without the rule the inner match swallowed every arm
 * that followed it — the enclosing match's own cases — and the error named the inner sum
 * (`Q is not a case of data Inner`), pointing nowhere near the layout that caused it.
 */
class CompileNestedMatchTest {

    private static final String MODULE = """
            module demo

            data A = { n: Int }
            data B = { n: Int }
            data 内 = A | B

            data P = { p: 内 }
            data Q
            data 外 = P | Q

            data Out = { n: Int }

            behavior run : (o: 外) -> Out constructs Out

            let run (o) =
                match o with
                    | P { p } ->
                        match p with
                            | A { n } -> Out { n = n }
                            | B { n } -> Out { n = 0 - n }
                    | Q -> Out { n = 99 }
            """;

    private static String rendered(String src) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        return new HumanRenderer(false)
                .render(e.diagnostic(), new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
    }

    private long runWith(BytesClassLoader loader, Object raw) throws Exception {
        Object in = Codecs.decoded(loader, "demo.外", raw);
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        return (Long) ((Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(behavior, in))).get("n");
    }

    @Test
    void anArmAfterANestedMatchBelongsToTheOuterMatch() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        assertEquals(5L, runWith(loader, Map.of("type", "P", "p", Map.of("type", "A", "n", 5L))));
        assertEquals(-5L, runWith(loader, Map.of("type", "P", "p", Map.of("type", "B", "n", 5L))));
        assertEquals(99L, runWith(loader, Map.of("type", "Q")),
                "`| Q` reaches back to the outer match's column, so it is the outer match's arm");
    }

    @Test
    void aBracedNestedMatchStillReads() throws Exception {
        // The block form is what the layout rule saves you from writing, not something it forbids.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE.replace("""
                    | P { p } ->
                        match p with
                            | A { n } -> Out { n = n }
                            | B { n } -> Out { n = 0 - n }
                """, """
                    | P { p } -> {
                        match p with
                            | A { n } -> Out { n = n }
                            | B { n } -> Out { n = 0 - n }
                    }
                """)), getClass().getClassLoader());

        assertEquals(5L, runWith(loader, Map.of("type", "P", "p", Map.of("type", "A", "n", 5L))));
        assertEquals(99L, runWith(loader, Map.of("type", "Q")));
    }

    @Test
    void twoMatchesOnOneLineCannotBeSeparatedByColumns_soTheReportSaysSo() {
        // The column rule needs the arms on their own lines. Written on one line, the inner match
        // still takes `| Q`, and the report is what has to explain it.
        String src = """
                module demo

                data A = { n: Int }
                data B = { n: Int }
                data 内 = A | B
                data P = { p: 内 }
                data Q
                data 外 = P | Q
                data Out = { n: Int }

                behavior run : (o: 外) -> Out constructs Out

                let run (o) = match o with | P { p } -> match p with | A { n } -> Out { n = n } | B { n } -> Out { n = n } | Q -> Out { n = 0 }
                """;
        String out = rendered(src);

        assertTrue(out.contains("`Q` is not a case of data `内`"), out);
        assertTrue(out.contains("`Q` is a case of `外`"), out);
        assertTrue(out.contains("{ match ... }"), out);          // how to end the inner match
    }

    @Test
    void anArmForAnotherSumOnItsOwnLineNamesThatSum() {
        // Not the layout trap (the arms are on their own lines) — a plain mistake. Naming the sum the
        // case does belong to is the useful half; the layout advice would be noise here.
        String src = """
                module demo

                data A = { n: Int }
                data B = { n: Int }
                data 内 = A | B
                data P = { p: 内 }
                data Q
                data 外 = P | Q
                data Out = { n: Int }

                behavior run : (i: 内) -> Out constructs Out

                let run (i) =
                    match i with
                        | A { n } -> Out { n = n }
                        | Q -> Out { n = 0 }
                """;
        String out = rendered(src);

        assertTrue(out.contains("`Q` is a case of `外`"), out);
        assertFalse(out.contains("on the same line"), out);
    }

    @Test
    void aNameThatIsNoSumsCaseGetsThePlainReport() {
        String src = """
                module demo

                data A = { n: Int }
                data B = { n: Int }
                data 内 = A | B
                data 単独 = { m: Int }
                data Out = { n: Int }

                behavior run : (i: 内) -> Out constructs Out

                let run (i) =
                    match i with
                        | A { n } -> Out { n = n }
                        | 単独 -> Out { n = 0 }
                """;
        String out = rendered(src);

        assertTrue(out.contains("`単独` is not a case of data `内`"), out);
        assertFalse(out.contains("is a case of"), out);
    }

    @Test
    void aNestedMatchIndentedPastTheArmKeepsItsOwnArms() throws Exception {
        // The inner arms sit deeper than the outer ones, so they are the inner match's — including
        // the last one, after which nothing returns to the outer level.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data A = { n: Int }
                data B = { n: Int }
                data 内 = A | B

                data P = { p: 内 }
                data Q
                data 外 = P | Q

                data Out = { n: Int }

                behavior run : (o: 外) -> Out constructs Out

                let run (o) =
                    match o with
                        | Q -> Out { n = 99 }
                        | P { p } ->
                            match p with
                                | A { n } -> Out { n = n }
                                | B { n } -> Out { n = 0 - n }
                """), getClass().getClassLoader());

        assertEquals(7L, runWith(loader, Map.of("type", "P", "p", Map.of("type", "A", "n", 7L))));
        assertEquals(99L, runWith(loader, Map.of("type", "Q")));
    }
}
