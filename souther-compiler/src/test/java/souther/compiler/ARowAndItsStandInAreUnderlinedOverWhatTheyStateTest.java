package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.LabeledRegion;
import souther.compiler.diag.Located;
import souther.compiler.diag.Region;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A report about two statements that disagree underlines what each of them states.
 *
 * <p>The example machinery is the reader of an expression's extent that is not the type checker, and
 * it held the older form of the same mistake for longer: a position and a number of columns, laid
 * off from the position with {@code Region.ofWidth}. A region built that way ends on the line it
 * began on whatever it was measured from, so a row stating a construction written over several lines
 * was marked at its first character and the reader was left to find the rest of it.
 *
 * <p>The other half of that shape — a position that is not where the value begins — cannot arise
 * here: a fixture is a literal or a construction (E1908), and each of those is anchored where it
 * starts. So what is held below is the half that can.
 */
class ARowAndItsStandInAreUnderlinedOverWhatTheyStateTest {

    private static final String BASE = """
            module demo

            data MemberId = String
            data Found = { id: MemberId }
            data Missing = { why: String }

            behavior findMember : (id: MemberId) -> Found | Missing

            behavior place : (id: MemberId) -> Found | Missing
                depends on findMember

            let place (id, findMember) = findMember(id)
            """;

    /**
     * The row states a construction written over three lines, and says so over three lines. What a
     * terminal draws under it is the renderer's; what the region says is the compiler's.
     */
    @Test
    void aRowStatingAConstructionOverSeveralLinesIsMarkedOverAllOfIt() {
        String source = BASE + """

                example findMember
                    | "found" : (MemberId("m-1")) -> Found {
                        id = MemberId("m-1")
                    }

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }

                example place
                    | "placed" : (MemberId("m-1")) -> Missing { why = "no such member" }
                """;

        Region region = primary(source);
        assertEquals("""
                Found {
                        id = MemberId("m-1")
                    }""", underlined(source, region));
    }

    /** And the stand-in beside it, over the whole of what the fake row answers. */
    @Test
    void theStandInIsMarkedOverTheWholeOfWhatItAnswers() {
        String source = BASE + """

                example findMember
                    | "found" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Missing {
                        why = "no such member"
                    }

                example place
                    | "placed" : (MemberId("m-1")) -> Missing { why = "no such member" }
                """;

        assertEquals("""
                Missing {
                        why = "no such member"
                    }""", underlined(source, standIn(source)));
    }

    /** The primary region of the one disagreement {@code source} is warned about. */
    private static Region primary(String source) {
        return ((Primary.InSource) disagreement(source).primary()).place().region();
    }

    /** The region said beside it — where the stand-in is written. */
    private static Region standIn(String source) {
        List<LabeledRegion> labels = disagreement(source).secondary();
        assertEquals(1, labels.size(), "the stand-in is the one place said beside it: " + labels);
        return ((souther.compiler.diag.DiagnosticPlace.InSource) labels.get(0).place()).region();
    }

    private static Diagnostic disagreement(String source) {
        List<Located> out = new ArrayList<>();
        assertDoesNotThrow(() -> Compiler.compiled(source, "Main", out));
        List<Diagnostic> said = new ArrayList<>();
        for (Located located : out) {
            if ("E1919".equals(located.diagnostic().code())) {
                said.add(located.diagnostic());
            }
        }
        assertEquals(1, said.size(), "one disagreement, one report: " + out);
        return said.get(0);
    }

    /** The characters {@code region} covers, cut out of the source it was read from. */
    private static String underlined(String source, Region region) {
        List<String> lines = List.of(source.split("\n", -1));
        if (WhereItSits.in(source, region).start().line() == WhereItSits.in(source, region).end().line()) {
            return lines.get(WhereItSits.in(source, region).start().line() - 1)
                    .substring(WhereItSits.in(source, region).start().column() - 1, WhereItSits.in(source, region).end().column() - 1);
        }
        StringBuilder out =
                new StringBuilder(lines.get(WhereItSits.in(source, region).start().line() - 1).substring(WhereItSits.in(source, region).start().column() - 1));
        for (int line = WhereItSits.in(source, region).start().line() + 1; line < WhereItSits.in(source, region).end().line(); line++) {
            out.append('\n').append(lines.get(line - 1));
        }
        return out.append('\n').append(lines.get(WhereItSits.in(source, region).end().line() - 1), 0, WhereItSits.in(source, region).end().column() - 1)
                .toString();
    }
}
