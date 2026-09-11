package souther.compiler.query;

import souther.compiler.diag.Located;
import souther.compiler.diag.Primary;
import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A report about a declaration is sent where the declaration is now.
 *
 * <p>The half of the boundary that has to keep working. What a declaration says is answered apart
 * from where it is written so that moving one reaches nobody; a reader that puts a caret under the
 * declaration still means where it is, and it asks for that separately. Left depending on the
 * meaning for both, such a reader would go on pointing at the line the declaration used to be on for
 * as long as it kept saying the same thing — and the report would be wrong in a way no test of what
 * the compiler says can see, because what it says would not have changed.
 *
 * <p>Written now rather than when the cut starts saving work. A check that a caret follows its
 * declaration passes today whatever the reader depends on, and it is the one that would fail the
 * day the answer it depends on stops moving.
 */
class AReportAboutADeclarationFollowsItWhenItMovesTest {

    /** A product with no value: nothing satisfies both of its rules, so it is refused. */
    private static final String EMPTY = """
            module shop.empty exposing ( Impossible )

            data Impossible = Int
                invariant value >= 1 && value <= 0
            """;

    @Test
    void movingTheDeclarationMovesTheCaretUnderIt() {
        Compilation c = Compilation.ofDocuments(Map.of("empty.sou", EMPTY), Set.of(),
                ModulePath.EMPTY);
        c.answerEverything();
        int before = whereItIsReported(c);

        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("empty.sou", EMPTY.replace("data Impossible",
                "// two lines written above it\n// which move it down the file\ndata Impossible"));
        c.update(edited, Set.of());
        c.answerEverything();

        assertNotEquals(before, whereItIsReported(c),
                "the declaration was written two lines further down and the report still points at"
                        + " the line it used to be on");
        assertEquals(before + 2, whereItIsReported(c),
                "the report followed the declaration, but not to where it went");
    }

    /** The line the one report about this workspace puts its caret on. */
    private static int whereItIsReported(Compilation c) {
        List<Located> errors = c.errors();
        assertEquals(1, errors.size(),
                "this workspace is supposed to be refused for exactly one thing: " + errors);
        if (errors.getFirst().diagnostic().primary() instanceof Primary.InSource in) {
            // The line a reader is sent to, which is what the file is laid out as now — the place
            // itself is what the declaration is, and writing above it leaves that where it was.
            return c.texts().resolve(in.place().region().start()).line();
        }
        throw new AssertionError("the report is supposed to point at the declaration, and points "
                + errors.getFirst().diagnostic().primary() + " instead");
    }
}
