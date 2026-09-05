package souther.compiler;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Located;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.Message;
import souther.compiler.meta.ModulePath;
import souther.compiler.observe.Applied;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.FailurePhase;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Stage;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.query.InputCaseEvidence;
import souther.compiler.source.SourceId;
import souther.compiler.types.TypeSymbol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior is stood in for by at most one {@code fake} block, and more than one is refused where
 * each is written.
 *
 * <p>The rows of a block are an ordered table: the first row stating an input is the one that
 * answers, and a {@code _} row is what the rest falls through to. There is no order between a
 * module's own source and the {@code examples for} files attached to it, so two blocks cannot be
 * composed into one table — which is why an {@code example}'s rows may be written in as many blocks
 * as an author likes and a stand-in's may not.
 *
 * <p>What none of them may do is win. A block that stood in because it was read first would be
 * chosen by the order this compile was handed its files, and the author of the other one is told
 * nothing: the rows they wrote are read by nothing, and a row that needed them is told the table
 * has no output for an input it can see written in front of it.
 */
class ABehaviorHasAtMostOneStandInTableTest {

    private static final String BASE = """
            module example.approvals

            import String ( length )

            data EmployeeId = String
                invariant length(value) > 0

            data Decision = { approver: EmployeeId }

            behavior findManager : (id: EmployeeId) -> EmployeeId

            behavior decideApprover : (applicant: EmployeeId) -> Decision
                depends on findManager
                constructs Decision

            let decideApprover (applicant, findManager) =
                Decision { approver = findManager(applicant) }
            """;

    /** A module declaring the behavior, for the blocks another module writes about it. */
    private static final String DECLARES = """
            module example.people exposing ( EmployeeId, findManager )

            import String ( length )

            data EmployeeId = String
                invariant length(value) > 0

            behavior findManager : (id: EmployeeId) -> EmployeeId
            """;

    private static final String IN_TWO_BLOCKS = """

            fake findManager
              | (EmployeeId("e-1")) -> EmployeeId("boss-1")

            fake findManager
              | (EmployeeId("e-2")) -> EmployeeId("boss-2")
            """;

    private static final String IN_ONE_BLOCK = """

            fake findManager
              | (EmployeeId("e-1")) -> EmployeeId("boss-1")
              | (EmployeeId("e-2")) -> EmployeeId("boss-2")
            """;

    private static final String ROWS = """

            example decideApprover
              | "one" : (EmployeeId("e-1")) -> Decision { approver = EmployeeId("boss-1") }
              | "two" : (EmployeeId("e-2")) -> Decision { approver = EmployeeId("boss-2") }
            """;

    private static Compilation compiled(List<String> sources) {
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static List<Diagnostic> diagnosticsOf(List<String> sources) {
        List<Diagnostic> out = new ArrayList<>();
        compiled(sources).diagnostics().forEach((_, found) -> out.addAll(Located.diagnosticsOf(found)));
        return out;
    }

    private static List<Diagnostic> diagnosticsOf(String source) {
        return diagnosticsOf(List.of(source));
    }

    private static List<String> codesOf(List<Diagnostic> said) {
        return said.stream().map(Diagnostic::code).toList();
    }

    /** Which file each report of {@code code} is written in, in the order they are said. */
    private static List<SourceId> filesSaying(List<String> sources, String code) {
        List<SourceId> files = new ArrayList<>();
        compiled(sources).diagnostics().forEach((id, found) -> {
            for (Diagnostic said : Located.diagnosticsOf(found)) {
                if (code.equals(said.code())) {
                    files.add(id);
                }
            }
        });
        return files;
    }

    private static List<RowOutcome> rowsOf(String source) {
        Compilation compilation = compiled(List.of(source));
        List<RowOutcome> rows = new ArrayList<>();
        for (String module : compilation.modules()) {
            for (SourceId id : compilation.exampleSourcesOf(module)) {
                Output.Examples.Of ran = compilation.db()
                        .ask(Output.Examples.asked(compilation.db(), module, id)).value();
                if (ran != null) {
                    rows.addAll(ran.rows());
                }
            }
        }
        return rows;
    }

    // --- what is refused -----------------------------------------------------------------------

    @Test
    void everyBlockNamingOneBehaviorIsReported() {
        assertEquals(List.of("E1933", "E1933"), codesOf(diagnosticsOf(BASE + IN_TWO_BLOCKS + ROWS)),
                "both blocks name `findManager`, and neither of them is the one the language keeps");
    }

    @Test
    void threeBlocksAreThreeReportsAndNotEveryPairOfThem() {
        String thrice = BASE + IN_TWO_BLOCKS + """

                fake findManager
                  | (EmployeeId("e-3")) -> EmployeeId("boss-3")
                """ + ROWS;
        assertEquals(List.of("E1933", "E1933", "E1933"), codesOf(diagnosticsOf(thrice)),
                "each block is reported once, not once for every other block it disagrees with");
    }

    @Test
    void theRowsWrittenAsOneBlockAreRead() {
        assertEquals(List.of(), codesOf(diagnosticsOf(BASE + IN_ONE_BLOCK + ROWS)),
                "the same rows in one block are the table, so both rows of the example hold");
    }

    /** And a block for each of two behaviors is two tables, which is what the rule is not about. */
    @Test
    void twoBlocksForTwoBehaviorsAreNotAConflict() {
        String twoDependencies = BASE
                .replace("behavior findManager : (id: EmployeeId) -> EmployeeId",
                        "behavior findManager : (id: EmployeeId) -> EmployeeId\n\n"
                                + "behavior findDeputy : (id: EmployeeId) -> EmployeeId")
                .replace("    depends on findManager",
                        "    depends on findManager\n    depends on findDeputy")
                .replace("let decideApprover (applicant, findManager) =\n"
                                + "    Decision { approver = findManager(applicant) }",
                        "let decideApprover (applicant, findManager, findDeputy) =\n"
                                + "    Decision { approver = findManager(findDeputy(applicant)) }")
                + """

                fake findManager
                  | (EmployeeId("d-1")) -> EmployeeId("boss-1")

                fake findDeputy
                  | (EmployeeId("e-1")) -> EmployeeId("d-1")

                example decideApprover
                  | "one" : (EmployeeId("e-1")) -> Decision { approver = EmployeeId("boss-1") }
                """;
        assertEquals(List.of(), codesOf(diagnosticsOf(twoDependencies)));
    }

    /** A module faking one borrowed behavior twice, once bare and once through its module. */
    private static final String TWO_SPELLINGS = """
            module example.approvals

            import example.people ( EmployeeId, findManager )

            data Decision = { approver: EmployeeId }

            behavior decideApprover : (applicant: EmployeeId) -> Decision
                depends on findManager
                constructs Decision

            let decideApprover (applicant, findManager) =
                Decision { approver = findManager(applicant) }

            fake findManager
              | (EmployeeId("e-1")) -> EmployeeId("boss-1")

            fake example.people.findManager
              | (EmployeeId("e-2")) -> EmployeeId("boss-2")
            """;

    /**
     * The behavior each block names is what resolution answered, and not how the target reads.
     *
     * <p>A dependency another module declares may be written bare where the scope settles it or
     * qualified through the module that declares it, and both are the same behavior. A rule read
     * off the characters would let an author write one table under each spelling and be told
     * nothing.
     */
    @Test
    void twoSpellingsOfOneBehaviorAreOneBehavior() {
        assertEquals(List.of("E1933", "E1933"),
                codesOf(diagnosticsOf(List.of(DECLARES, TWO_SPELLINGS))),
                "the bare name and the qualified one reach one declaration");
    }

    /**
     * And each report names the behavior, not the spelling its own block was written under.
     *
     * <p>What makes two blocks one refusal is the behavior they reach, and it is the one thing a
     * reader has to be given: reports naming two spellings read as two unrelated problems, and a
     * hint telling an author to merge the blocks written under theirs names a spelling only one
     * block uses. Each also points at the others, so what is said is one thing said in two places.
     */
    @Test
    void bothReportsNameTheBehaviorAndPointAtEachOther() {
        List<Diagnostic> said = diagnosticsOf(List.of(DECLARES, TWO_SPELLINGS)).stream()
                .filter(each -> "E1933".equals(each.code())).toList();
        assertEquals(2, said.size(), "two blocks, two reports");

        Set<Message> subjects = new LinkedHashSet<>();
        for (Diagnostic each : said) {
            subjects.add(each.said());
            assertEquals(1, each.secondary().size(),
                    "each report points at the other block: " + each.said());
        }
        assertEquals(Set.of(new ExampleMessage.MoreThanOneFakeStandsInForOneBehavior(
                        "example.people.findManager")),
                subjects,
                "one behavior, so one sentence naming it as this module writes it, however either"
                        + " block spelt its own target");
    }

    /** And one block in each of two modules is one block each: the count is a module's own. */
    @Test
    void aBlockInAnotherModuleIsNotASecondBlockHere() {
        assertEquals(List.of(), codesOf(diagnosticsOf(List.of(DECLARES, """
                module example.approvals

                import example.people ( EmployeeId, findManager )

                data Decision = { approver: EmployeeId }

                behavior decideApprover : (applicant: EmployeeId) -> Decision
                    depends on findManager
                    constructs Decision

                let decideApprover (applicant, findManager) =
                    Decision { approver = findManager(applicant) }

                fake findManager
                  | (EmployeeId("e-1")) -> EmployeeId("boss-1")

                example decideApprover
                  | "one" : (EmployeeId("e-1")) -> Decision { approver = EmployeeId("boss-1") }
                """, """
                module example.audits

                import example.people ( EmployeeId, findManager )

                data Note = { of: EmployeeId }

                behavior noteApprover : (applicant: EmployeeId) -> Note
                    depends on findManager
                    constructs Note

                let noteApprover (applicant, findManager) =
                    Note { of = findManager(applicant) }

                fake findManager
                  | (EmployeeId("e-1")) -> EmployeeId("boss-1")

                example noteApprover
                  | "one" : (EmployeeId("e-1")) -> Note { of = EmployeeId("boss-1") }
                """))),
                "each module writes one block for the behavior it borrows");
    }

    // --- where it is said ----------------------------------------------------------------------

    @Test
    void aBlockIsSaidInTheFileItIsWrittenIn() {
        List<String> across = List.of(BASE + """

                fake findManager
                  | (EmployeeId("e-1")) -> EmployeeId("boss-1")
                """, """
                examples for example.approvals

                fake findManager
                  | (EmployeeId("e-2")) -> EmployeeId("boss-2")
                """);
        assertEquals(List.of(new SourceId("0"), new SourceId("1")), filesSaying(across, "E1933"),
                "a module's blocks are its own source's and its attached files', and each report is"
                        + " quoted from the file its block is written in");
    }

    /**
     * Two attached files are two blocks as much as a module's source and one of them are.
     *
     * <p>The module's own source writes neither. A rule that had grown to expect one side of the
     * count to be the module's own would read this as one block each and say nothing, and the files
     * an author split their rows across are exactly where that would happen.
     */
    @Test
    void twoAttachedFilesAreTwoBlocks() {
        List<String> across = List.of(BASE, """
                examples for example.approvals

                fake findManager
                  | (EmployeeId("e-1")) -> EmployeeId("boss-1")
                """, """
                examples for example.approvals

                fake findManager
                  | (EmployeeId("e-2")) -> EmployeeId("boss-2")
                """);
        assertEquals(List.of(new SourceId("1"), new SourceId("2")), filesSaying(across, "E1933"),
                "neither block is written in the module's own source, and both are the module's");
    }

    /**
     * And which file was handed to the compile first decides nothing.
     *
     * <p>The two sources swapped: the same two blocks are reported, in the file each is written in.
     * A rule that kept the one read first would say something different here, and the swap is what
     * shows it does not — a rule read off the order agrees with this one until the day the order
     * changes.
     */
    @Test
    void theOrderTheSourcesArriveInDecidesNothing() {
        String own = BASE + """

                fake findManager
                  | (EmployeeId("e-1")) -> EmployeeId("boss-1")
                """;
        String attached = """
                examples for example.approvals

                fake findManager
                  | (EmployeeId("e-2")) -> EmployeeId("boss-2")
                """;
        assertEquals(List.of("E1933", "E1933"), codesOf(diagnosticsOf(List.of(own, attached))));
        assertEquals(List.of("E1933", "E1933"), codesOf(diagnosticsOf(List.of(attached, own))),
                "the module's source read second is the same two blocks");
    }

    // --- what the rows are told ----------------------------------------------------------------

    /**
     * A row requiring the behavior is not told a stand-in is missing, nor that a table had no
     * output for its input.
     *
     * <p>Both would name the author's own rows as the problem: one says nothing was written where
     * two were, and the other quotes a row of a table that stands in for nothing.
     */
    @Test
    void aRowIsNotToldItsStandInIsMissingOrShort() {
        List<String> codes = codesOf(diagnosticsOf(BASE + IN_TWO_BLOCKS + ROWS));
        assertTrue(codes.stream().noneMatch(code -> code.equals("E1908") || code.equals("E1909")),
                "said instead of the refusal: " + codes);
    }

    /** It stops where a row whose table would not build stops, having never entered the behavior. */
    @Test
    void aRowStopsWithoutApplyingTheBehavior() {
        List<RowOutcome> rows = rowsOf(BASE + IN_TWO_BLOCKS + ROWS);
        assertEquals(2, rows.size(), "both rows are read, or the assertions below say nothing");
        for (RowOutcome row : rows) {
            assertEquals(Stage.FIXTURES_VALIDATED, row.stage(),
                    "the fixtures were read; what could not be reached is the stand-in");
            assertEquals(Disposition.FAILED, row.disposition());
            assertEquals(FailurePhase.FAKE_RESOLUTION, row.failurePhase());
            assertInstanceOf(Applied.Nothing.class, row.run().applied(),
                    "nothing applied the behavior, which is why no answer of its own is reported");
        }
    }

    /**
     * A model whose target takes a sum, so that a measure of what the rows cover has cases to count.
     */
    private static final String OVER_A_SUM = """
            module example.approvals

            import String ( length )

            data EmployeeId = String
                invariant length(value) > 0

            data Active = { id: EmployeeId }
            data Suspended = { id: EmployeeId }
            data Status = Active | Suspended

            data Decision = { approver: EmployeeId }

            behavior findManager : (id: EmployeeId) -> EmployeeId

            behavior decideApprover : (applicant: Status) -> Decision
                depends on findManager
                constructs Decision

            let decideApprover (applicant, findManager) = match applicant with
                | Active as a    -> Decision { approver = findManager(a.id) }
                | Suspended as s -> Decision { approver = findManager(s.id) }

            example decideApprover
              | "one" : (Active { id = EmployeeId("e-1") })
                    -> Decision { approver = EmployeeId("boss-1") }
            """;

    /**
     * And the measure counts such a row as written and not as run.
     *
     * <p>Read from what a report of this model holds rather than from the row's own state. The two
     * agree today because the measure reads the stage the row stopped at, and a reading that later
     * took a row this far for evidence of what the behavior was applied to would be counting a run
     * that never happened — which the row's state alone would not say.
     */
    @Test
    void theMeasureCountsSuchARowAsWrittenAndNotAsRun() {
        Compilation compilation = compiled(List.of(OVER_A_SUM + IN_TWO_BLOCKS));
        Map<String, Adequacy.SignatureEvidence> witnesses = compilation.db()
                .ask(new Adequacy.Witnesses(compilation.modules().get(0))).value();
        assertNotNull(witnesses, "the module was measured, or this says nothing");
        InputCaseEvidence.Cases seen = witnesses.get("decideApprover").inputs().made()
                .orElseThrow().get(0).cases().made().orElseThrow();

        assertEquals(List.of("Active"), names(seen.specified()),
                "the row wrote the case it is about, and its fixtures were read");
        assertEquals(List.of(), names(seen.executed()),
                "nothing applied the behavior, so no case here was one a run reached");
        assertEquals(List.of(), names(seen.verified()));
    }

    /** And the same model with the blocks written as one runs the row, so the two above are read of
     *  a measure that does say something when there is something to say. */
    @Test
    void andTheSameModelWithOneBlockReachesTheRun() {
        Compilation compilation = compiled(List.of(OVER_A_SUM + """

                fake findManager
                  | (EmployeeId("e-1")) -> EmployeeId("boss-1")
                """));
        Map<String, Adequacy.SignatureEvidence> witnesses = compilation.db()
                .ask(new Adequacy.Witnesses(compilation.modules().get(0))).value();
        InputCaseEvidence.Cases seen = witnesses.get("decideApprover").inputs().made()
                .orElseThrow().get(0).cases().made().orElseThrow();

        assertEquals(List.of("Active"), names(seen.executed()),
                "one block stands in, so the row runs and the case it wrote is one a run reached");
    }

    private static List<String> names(Set<TypeSymbol> cases) {
        return cases.stream().map(TypeSymbol::name).sorted().toList();
    }

    // --- what it is not ------------------------------------------------------------------------

    /**
     * A block whose target reached no behavior is not one of them.
     *
     * <p>It names nothing, so there is no behavior for a second block to be a second block of. What
     * is wrong with it is said where the name is read.
     */
    @Test
    void aBlockNamingNoBehaviorIsNotCounted() {
        List<String> codes = codesOf(diagnosticsOf(BASE + """

                fake findManager
                  | (EmployeeId("e-1")) -> EmployeeId("boss-1")

                fake findNobody
                  | (EmployeeId("e-2")) -> EmployeeId("boss-2")
                """ + ROWS));
        assertTrue(codes.contains("E1932"), "the name reached no behavior: " + codes);
        assertTrue(codes.stream().noneMatch("E1933"::equals),
                "one block names `findManager` and the other names nothing: " + codes);
    }

    /**
     * And what is wrong inside a block is still said, whether or not the block stands in for
     * anything.
     *
     * <p>A table is built because it is written. A refusal about how many blocks name a behavior is
     * about the module; a row of one of them that the dispatch can never answer with is about that
     * block, and dropping it would leave an author who merged the blocks a fault they had not been
     * shown.
     */
    @Test
    void aFaultInsideARefusedBlockIsStillSaid() {
        List<String> codes = codesOf(diagnosticsOf(BASE + """

                fake findManager
                  | (EmployeeId("e-1")) -> EmployeeId("boss-1")

                fake findManager
                  | (EmployeeId("e-2")) -> EmployeeId("boss-2")
                  | (EmployeeId("e-2")) -> EmployeeId("boss-3")
                """ + ROWS));
        assertTrue(codes.contains("E1926"),
                "the second row of the second block answers nothing: " + codes);
    }

    /**
     * A refusal elsewhere does not hide it.
     *
     * <p>The count is over what was written and takes nothing from further down the compile. Read
     * from a state a failed declaration leaves unbuilt, it would be a refusal that appears once the
     * unrelated one is fixed.
     */
    @Test
    void somethingElseBeingWrongDoesNotHideIt() {
        List<String> codes = codesOf(diagnosticsOf(BASE + IN_TWO_BLOCKS + ROWS + """

                data Unbuildable = { n: Int }
                    invariant n > 0
                    invariant n < 0
                """));
        assertTrue(codes.contains("E1933"), "said with something else wrong: " + codes);
    }
}
