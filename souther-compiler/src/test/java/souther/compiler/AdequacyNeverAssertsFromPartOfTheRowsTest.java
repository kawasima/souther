package souther.compiler;

import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Measurement;
import souther.compiler.execute.jvm.JvmExampleDeadlines;
import org.junit.jupiter.api.Test;

import souther.compiler.observe.MeasurementStatus;
import souther.compiler.publish.RuleHandleProse;
import souther.compiler.query.Adequacy;
import souther.compiler.query.ArmObligation;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.ArmVocabulary;
import souther.compiler.report.GeneratedRows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One rule, asked of every measure at once.
 *
 * <p>The rule is: <em>found</em> may be said from part of the rows, and <em>not found</em> may not.
 * Every measure here answers over a population, and each of them has at some point claimed the second
 * from an incomplete one — the signature, the positions, the combinations, the boundaries, the arms,
 * and the generator, each found separately, each fixed separately.
 *
 * <p>So this asks all of them together. It walks what a report can say about one behavior and fails on
 * any measure that reports a gap while calling itself complete, over models where something was
 * demonstrably not read. A new measure added later is covered by the same walk, and a new way for a
 * reading to go missing shows up in every measure at once rather than in whichever one someone
 * happened to look at.
 */
class AdequacyNeverAssertsFromPartOfTheRowsTest {

    /**
     * A model, with the work the compile that reads it does not get back from.
     *
     * <p>Carried beside the source because the models here differ in it. One holds a row that never
     * comes back, which is said here rather than timed; the others come back from everything — one
     * spends the observation budget, one is past what the backend can emit — so nothing about them
     * overruns.
     */
    private record Unreadable(String source, JvmExampleDeadlines overrun) {}

    /** Models where something a measure would want to read was not read, each in a different way. */
    private static List<Unreadable> unreadableInSomeWay() {
        return List.of(
                // a row that never finishes: its state is dropped rather than read
                new Unreadable("""
                module example.a

                data Yes
                data No
                data Flag = Yes | No

                data Amount = Int
                    invariant value >= 0

                data Draft = { flag: Flag, cost: Amount }
                data Ok = { n: Int }
                data Big = { n: Int }

                partial let spin (n: Int): Int = spin(n)

                behavior take : (request: Draft) -> Ok | Big
                    constructs Ok, Big

                let take (request) = {
                    guard request.cost.value <= 100 else Big { n = spin(1) }
                    Ok { n = request.cost.value }
                }

                example take
                    | (Draft { flag = Yes, cost = Amount(500) }) -> Big { n = 0 }
                """, DoesNotComeBack.overrunningOn(DoesNotComeBack.everyRowOf("take"))),
                // a value past the observation's limits: the position is there and unreadable
                new Unreadable(budgetSpent(), null),
                // a module whose classes could not be made: nothing was read, and nothing says
                // which row would have covered what
                new Unreadable(classesNotMade(), null));
    }

    /**
     * A module the backend cannot emit: the row's operand is compiled as a method of its own, and
     * this one is past what a JVM method holds.
     *
     * <p>The third way, and the one that was missed. A source nothing observed and an example block
     * whose classes would not load were listed where a reader asked whether anything was read;
     * a module whose classes were not made reads the same to every measure and was not among them,
     * so the generator offered work for a behavior whose rows nothing had read.
     */
    private static String classesNotMade() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            items.append(i == 0 ? "" : ", ").append("Item { a = \"").append(i)
                    .append("\", b = \"").append(i).append("\", c = \"").append(i).append("\" }");
        }
        return """
                module example.c

                data Amount = Int
                    invariant value >= 0 && value <= 1000

                data Yes
                data No
                data Flag = Yes | No

                data Item = { a: String, b: String, c: String }

                data Draft = { items: List<Item>, cost: Amount, flag: Flag }
                data Ok = { n: Int }

                behavior take : (request: Draft) -> Ok
                    constructs Ok

                let take (request) = Ok { n = request.cost.value }

                example take
                    | (Draft { items = [ %s ], cost = Amount(0), flag = Yes }) -> Ok { n = 0 }
                """.formatted(items);
    }

    private static String budgetSpent() {
        // Computed rather than spelled: a literal this size is a method past the JVM's code-size
        // limit (E2102), and what this model exists to hit is the observation's limit.
        String groups = "someGroups(64)";
        return """
                module example.b

                data Amount = Int
                    invariant value >= 0 && value <= 1000

                data Yes
                data No
                data Flag = Yes | No

                data Item = { a: String, b: String, c: String }
                data Group = { items: List<Item> }

                data Draft = { groups: List<Group>, cost: Amount, flag: Flag }
                data Ok = { n: Int }

                behavior take : (request: Draft) -> Ok
                    constructs Ok

                let take (request) = Ok { n = request.cost.value }


                let someItems (n: Int): List<Item> =
                    List.map({ (i) -> Item { a = "x", b = "x", c = "x" } }, List.rangeInclusive(1, n))

                let someGroups (n: Int): List<Group> =
                    List.map({ (i) -> Group { items = someItems(64) } }, List.rangeInclusive(1, n))

                example take
                    | (Draft { groups = %s, cost = Amount(0), flag = Yes }) -> Ok { n = 0 }
                """.formatted(groups);
    }

    /** Compiles are shared between the cases that read the same model: each of the three walks the
     * same models, and a compilation answers the same questions however many times it is asked. */
    private static final Map<String, Compilation> COMPILED = new java.util.LinkedHashMap<>();

    private static Compilation measured(Unreadable model) {
        return COMPILED.computeIfAbsent(model.source(),
                _ -> measured(model.source(), model.overrun()));
    }

    private static Compilation measured(String source) {
        return measured(source, null);
    }

    private static Compilation measured(String source, JvmExampleDeadlines overrun) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        if (overrun != null) {
            compilation.withJvmExampleDeadlines(overrun);
        }
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /**
     * No measure names a gap while calling itself complete, where something went unread.
     *
     * <p>What each measure calls a gap is its own — an unspecified case, an uncovered class, an unmet
     * boundary, an unreached arm, an untried combination — and what they have in common is that all of
     * them are the sentence "nothing does this", which needs every row to have been read.
     */
    @Test
    void noMeasureNamesAGapWhileCallingItselfComplete() {
        for (Unreadable model : unreadableInSomeWay()) {
            Compilation compilation = measured(model);
            String module = compilation.modules().get(0);
            List<String> wrong = new ArrayList<>();

            Adequacy.SignatureEvidence signature = compilation.db()
                    .ask(new Adequacy.Witnesses(module)).value().get("take");
            if (signature.counted() instanceof Measurement.Complete<?>) {
                if (!signature.output().unspecified().isEmpty()) {
                    wrong.add("signature output: " + signature.output().unspecified());
                }
                signature.positions().stream().filter(in -> !in.unspecified().isEmpty())
                        .forEach(in -> wrong.add("signature input: " + in.unspecified()));
            }

            PartitionEvidence partition = compilation.db()
                    .ask(new Adequacy.Coverage(module)).value().get("take");
            for (PartitionEvidence.AxisCoverage axis : partition.axes()) {
                if (axis.reached() instanceof Measurement.Complete<?> && !axis.uncovered().isEmpty()) {
                    wrong.add("axis " + axis.path() + ": " + axis.uncovered());
                }
            }
            for (souther.compiler.query.BorderObligationPointAssessment point
                    : compilation.db().ask(new Adequacy.BodyBorders(module)).value().get("take")
                            .made().orElseGet(List::of)) {
                if (point.item().coverage().settled() && !point.owed().hasRowWitness()) {
                    wrong.add("boundary "
                            + RuleHandleProse.said(
                                    point.said(cited -> souther.compiler.query.Sites.placeOf(
                                            compilation.db(), cited)),
                                    new souther.compiler.diag.SourceRendering(
                                            souther.compiler.source.SourceId::value,
                                            compilation.texts()), null));
                }
            }
            if (partition.pairs().counted() instanceof Measurement.Complete<?>
                    && partition.pairs().unknown() > 0) {
                wrong.add("pairs: " + partition.pairs().unknown() + " unknown");
            }

            // Per arm, the way the boundary above is per point: an arm this account calls unmet is
            // one it read every row against and found nothing going through, so the sentence is
            // asserted there and nowhere else. Where a row of this behavior did not come back the
            // arms it may have lit come back undecided instead, which asserts nothing.
            Adequacy.BranchEvidence branch = compilation.db()
                    .ask(new Adequacy.BranchCoverage(module)).value().get("take");
            if (branch.measured().made().isPresent()) {
                for (ArmObligation.Counted arm : branch.arms().unmet()) {
                    wrong.add("branch: " + ArmVocabulary.label(arm.display()) + " unreached");
                }
            }

            assertEquals(List.of(), wrong, module + " asserted a gap over rows it did not read");
        }
    }

    /** And the status over them says so, so a reader who only looks at the top is not misled. */
    @Test
    void theReportSaysItCouldNotBeMadeCompletely() {
        for (Unreadable model : unreadableInSomeWay()) {
            AdequacyReport report = AdequacyReport.of(measured(model));
            assertEquals(MeasurementStatus.PARTIAL, report.status(),
                    report.modules().get(0).module());
            assertEquals(MeasurementStatus.PARTIAL,
                    report.modules().get(0).behaviors().get(0).status());
        }
    }

    /**
     * Nor does the generator hand out work from it.
     *
     * <p>The strongest form of the same claim. A warning says nothing does this; a generated row says
     * <em>go and write this</em>, and the row may be one that is already there.
     */
    @Test
    void nothingIsGeneratedFromRowsThatWereNotRead() {
        for (Unreadable model : unreadableInSomeWay()) {
            Compilation compilation = measured(model);
            String module = compilation.modules().get(0);
            Map<String, Adequacy.Filling> generated =
                    Adequacy.generatedOf(compilation.db(), module);
            assertNotNull(generated);

            String written = GeneratedRows.of(Adequacy.offeredFor(compilation.db(),
                            souther.compiler.query.OfferingRequest.overTheModule(module)),
                    Map.of(), SourceRendering.namedByIdentity(compilation.texts()), compilation.db()).text();
            assertFalse(written.contains("example "),
                    module + " offers a row that may already be written: " + written);
            // Either word, because the two models get here differently: one has rows nothing read
            // and the generation never began, and the other read its rows and could not place a
            // value at any position it had. What is held to is that neither writes nothing in
            // silence.
            assertTrue(written.contains("generation stopped")
                            || written.contains("no rows offered at"),
                    module + " says nothing about why it wrote nothing: " + written);
        }
    }

    /** Where everything was read, the measures do speak — or this would pass by saying nothing. */
    @Test
    void aModelNothingWentWrongInStillReportsItsGaps() {
        Compilation compilation = measured("""
                module example.fine

                data Yes
                data No
                data Flag = Yes | No

                data Amount = Int
                    invariant value >= 0

                data Draft = { flag: Flag, cost: Amount }
                data Ok = { n: Int }
                data Big = { n: Int }

                behavior take : (request: Draft) -> Ok | Big
                    constructs Ok, Big

                let take (request) = {
                    guard request.cost.value <= 100 else Big { n = 0 }
                    Ok { n = request.cost.value }
                }

                example take
                    | (Draft { flag = Yes, cost = Amount(50) }) -> Ok { n = 50 }
                """);
        String module = compilation.modules().get(0);
        PartitionEvidence partition = compilation.db()
                .ask(new Adequacy.Coverage(module)).value().get("take");

        assertEquals(MeasurementStatus.COMPLETE, AdequacyReport.of(compilation).status());
        assertTrue(partition.axes().stream().anyMatch(a -> !a.uncovered().isEmpty()),
                "a class nothing is in");
        assertTrue(compilation.db().ask(new Adequacy.BodyBorders(module)).value().get("take")
                        .made().orElseGet(List::of).stream()
                        .anyMatch(p -> !p.owed().hasRowWitness()),
                "a boundary nothing is at");
        assertTrue(partition.pairs().unknown() > 0, "a combination nothing reaches");
        assertFalse(compilation.db().ask(new Adequacy.BranchCoverage(module)).value()
                .get("take").arms().unmet().isEmpty(), "an arm nothing goes through");
        assertFalse(GeneratedRows.of(Adequacy.offeredFor(compilation.db(),
                        souther.compiler.query.OfferingRequest.overTheModule(module)),
                Map.of(), SourceRendering.namedByIdentity(compilation.texts()), compilation.db()).text().isEmpty(),
                "and rows offered for them");
    }
}
