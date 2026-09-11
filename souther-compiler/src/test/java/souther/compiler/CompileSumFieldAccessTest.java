package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a field off a sum is a compile error — a sum has no fields of its own, and which case it is
 * is not known until it is opened. Two cases that happen to declare a field of the same name have not
 * shared it: that reading would be structural, and only a field every case spreads is the sum's own
 * (issue #160, {@link CompileSumCommonFieldTest}). The report says that, rather than "cannot access
 * field `f` on this value", which leaves the author to guess what the value even was.
 */
class CompileSumFieldAccessTest {

    private static String rendered(String src, Locale locale) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        Diagnostic d = e.diagnostic();
        return new HumanRenderer(false).render(d, new SourceContext("demo.sou", src, SourceLayout.of(src)), locale);
    }

    private static final String SAME_NAME_IN_EVERY_CASE = """
            module demo

            data A = { title: String, x: Int }
            data B = { title: String, y: Int }
            data S = A | B
            data Out = { t: String }

            behavior run : (s: S) -> Out constructs Out

            let run (s) = Out { t = s.title }
            """;

    @Test
    void aFieldEveryCaseDeclaresSeparatelyIsStillReadThroughMatch() {
        String out = rendered(SAME_NAME_IN_EVERY_CASE, Locale.ENGLISH);

        assertTrue(out.contains("`title`"), out);
        assertTrue(out.contains("`S`"), out);                  // which value it was
        assertTrue(out.contains("`match`"), out);              // what to write instead
        assertFalse(out.contains("These cases have no"), out); // every case has it, so no such hint
    }

    @Test
    void aFieldEveryCaseDeclaresSeparatelyIsSaidNotToBeShared() {
        String out = rendered(SAME_NAME_IN_EVERY_CASE, Locale.ENGLISH);

        // otherwise the author sees "a sum has no fields" while looking at a `title` in every case
        assertTrue(out.contains("each case declares its own"), out);
        assertTrue(out.contains("spread"), out);
    }

    @Test
    void theCasesMissingTheFieldAreNamed() {
        String src = """
                module demo

                data A = { タイトル: String }
                data B = { y: Int }
                data S = A | B
                data Out = { t: String }

                behavior run : (s: S) -> Out constructs Out

                let run (s) = Out { t = s.タイトル }
                """;
        String out = rendered(src, Locale.ENGLISH);

        assertTrue(out.contains("These cases have no `タイトル` field: B"), out);
    }

    @Test
    void theJapaneseReportCarriesTheSameThreeParts() {
        String out = rendered(SAME_NAME_IN_EVERY_CASE, Locale.JAPANESE);

        assertTrue(out.contains("`title`"), out);
        assertTrue(out.contains("`S`"), out);
        assertTrue(out.contains("`match`"), out);
    }

    @Test
    void aValueThatIsNotADataKeepsThePlainReport() {
        String src = """
                module demo

                data In = { n: Int }
                data Out = { n: Int }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { n = i.n.missing }
                """;
        String out = rendered(src, Locale.ENGLISH);

        assertTrue(out.contains("`missing`"), out);
        assertFalse(out.contains("sum"), out);
    }
}
