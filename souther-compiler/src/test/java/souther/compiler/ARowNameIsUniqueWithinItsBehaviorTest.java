package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Located;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.ParseMessage;
import souther.compiler.meta.ModulePath;
import souther.compiler.observe.RowIdentity;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row's written description is its name, and a name is what something outside the file uses to say
 * which row it means. Two rows of one behavior carrying one name leave that unanswerable, and the
 * failure it would produce is the silent one: a reader keys on the name, finds one of the two, and
 * nothing says the other was meant.
 *
 * <p>So the name is held to being unique among the rows of one behavior, over everything that writes
 * that behavior's rows — the module's own source and every {@code examples for} file attached to it.
 * Both rows of a collision are reported, each in the source it is written in. Which of them is "the
 * duplicate" is not a question the language answers: source order is what a caller hands the compiler
 * (a build sorts its files, a command line does not), so a rule that named one of the two would say
 * different things about one model depending on how the compile was started.
 *
 * <p>A row written without a name is not compared with anything. It cannot be keyed from outside, and
 * that is the whole of what being unnamed means here.
 */
class ARowNameIsUniqueWithinItsBehaviorTest {

    private static final String MODEL = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Draft = { cost: Amount }
            data Submitted = { cost: Amount }
            data Rejected = { reason: String }

            behavior submit : (request: Draft) -> Submitted | Rejected
                constructs Submitted, Rejected

            let submit (request) = {
                guard request.cost.value <= 100 else Rejected { reason = "over" }
                Submitted { cost = request.cost }
            }

            behavior reject : (request: Draft) -> Rejected
                constructs Rejected

            let reject (request) = Rejected { reason = "asked to" }
            """;

    private static final String ATTACHED_HEADER = "examples for example.trip\n";

    /** Every diagnostic these sources have, by the source it is filed under. */
    private static Map<SourceId, List<Diagnostic>> bySource(String... sources) {
        Compilation compilation = Compilation.ofSources(List.of(sources), ModulePath.EMPTY);
        Map<SourceId, List<Diagnostic>> out = new LinkedHashMap<>();
        compilation.diagnostics().forEach((id, located) -> out.put(id, Located.diagnosticsOf(located)));
        return out;
    }

    private static List<Diagnostic> all(String... sources) {
        List<Diagnostic> out = new ArrayList<>();
        bySource(sources).values().forEach(out::addAll);
        return out;
    }

    /** The ones that say a name is on more than one row. */
    private static List<Diagnostic> collisions(List<Diagnostic> found) {
        return found.stream()
                .filter(d -> d.said() instanceof ExampleMessage.TheNameIsOnMoreThanOneRow)
                .toList();
    }

    /** The 1-based line {@code needle} is written on. */
    private static int lineOf(String source, String needle) {
        List<String> lines = source.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i + 1;
            }
        }
        throw new AssertionError("not written in this source: " + needle);
    }

    // --- what is refused -----------------------------------------------------------------------

    @Test
    void twoRowsOfOneBehaviorWrittenInOneSourceAreBothReported() {
        String source = MODEL + """

                example submit
                    | "over the ceiling" : (Draft { cost = Amount(200) }) -> Rejected
                    | "over the ceiling" : (Draft { cost = Amount(300) }) -> Rejected
                """;

        List<Diagnostic> said = collisions(all(source));

        assertEquals(2, said.size(), "both rows carry the name, so both are told");
        assertEquals(List.of(lineOf(source, "Amount(200)"), lineOf(source, "Amount(300)")),
                said.stream().map(d -> WhereItSits.in(source, ((Primary.InSource) d.primary()).place().region()).start().line()).sorted().toList(),
                "each is said at the row it is about");
        ExampleMessage.TheNameIsOnMoreThanOneRow first =
                assertInstanceOf(ExampleMessage.TheNameIsOnMoreThanOneRow.class, said.get(0).said());
        assertEquals("over the ceiling", first.name());
        assertEquals("submit", first.target());
    }

    @Test
    void aNameAModuleAndItsAttachedFileBothUseIsReportedInBothFiles() {
        String model = MODEL + """

                example submit
                    | "over the ceiling" : (Draft { cost = Amount(200) }) -> Rejected
                """;
        String attached = ATTACHED_HEADER + """

                example submit
                    | "over the ceiling" : (Draft { cost = Amount(300) }) -> Rejected
                """;

        Map<SourceId, List<Diagnostic>> said = bySource(model, attached);

        assertEquals(1, collisions(said.get(new SourceId("0"))).size(), "the module's own row is told, on the module");
        assertEquals(1, collisions(said.get(new SourceId("1"))).size(),
                "the attached file's row is told, on the attached file");
        assertEquals(lineOf(attached, "Amount(300)"), WhereItSits.in(attached, ((Primary.InSource) collisions(said.get(new SourceId("1"))).get(0).primary()).place().region()).start().line(),
                "a position is a line of the file the row is written in");
    }

    @Test
    void threeRowsOfOneNameAreThreeReportsAndNotOnePerPair() {
        String source = MODEL + """

                example submit
                    | "over the ceiling" : (Draft { cost = Amount(200) }) -> Rejected
                    | "over the ceiling" : (Draft { cost = Amount(300) }) -> Rejected
                    | "over the ceiling" : (Draft { cost = Amount(400) }) -> Rejected
                """;

        assertEquals(3, collisions(all(source)).size(),
                "one report per row that carries the name — how they were compared is not the reader's");
    }

    @Test
    void aNameWithNothingInItIsRefusedWhereItIsWritten() {
        String source = MODEL + """

                example submit
                    | "" : (Draft { cost = Amount(200) }) -> Rejected
                """;

        List<Diagnostic> said = all(source).stream()
                .filter(d -> d.said() instanceof ParseMessage.ARowNameSaysNothing)
                .toList();

        assertEquals(1, said.size(), "a name was written and it names nothing");
        assertEquals(lineOf(source, "Amount(200)"), WhereItSits.in(source, ((Primary.InSource) said.get(0).primary()).place().region()).start().line());
    }

    @Test
    void aNameOfNothingButSpacesIsRefusedTheSameWay() {
        String source = MODEL + """

                example submit
                    | "   " : (Draft { cost = Amount(200) }) -> Rejected
                """;

        assertTrue(all(source).stream().anyMatch(d -> d.said() instanceof ParseMessage.ARowNameSaysNothing),
                "spaces are not a name either");
    }

    /**
     * The two are held apart in the type as well. Writing no name and writing one that names nothing
     * are different things, and reading the second as the first would turn a row the compiler refused
     * into an unnamed row and carry it past the refusal.
     */
    @Test
    void aNameThatNamesNothingIsNotReadAsARowWrittenWithoutOne() {
        assertEquals(new RowIdentity.Unnamed(1), RowIdentity.of(null, 1),
                "a row that wrote no name is the row without one");
        assertThrows(IllegalArgumentException.class, () -> RowIdentity.of("   ", 1),
                "and one that wrote a name naming nothing is refused where it is written, so reaching "
                        + "here with one is a defect rather than a state of the source");
    }

    // --- what is not ---------------------------------------------------------------------------

    @Test
    void twoBehaviorsMayHaveARowOfTheSameName() {
        String source = MODEL + """

                example submit
                    | "over the ceiling" : (Draft { cost = Amount(200) }) -> Rejected

                example reject
                    | "over the ceiling" : (Draft { cost = Amount(200) }) -> Rejected
                """;

        assertEquals(List.of(), collisions(all(source)),
                "a name says which row of one behavior, so two behaviors do not collide");
    }

    @Test
    void rowsWrittenWithNoNameAreNotComparedWithEachOther() {
        String source = MODEL + """

                example submit
                    | (Draft { cost = Amount(200) }) -> Rejected
                    | (Draft { cost = Amount(300) }) -> Rejected
                    | (Draft { cost = Amount(50) }) -> Submitted
                """;

        assertEquals(List.of(), collisions(all(source)),
                "an unnamed row carries no name, so there is nothing for two of them to share");
    }
}
