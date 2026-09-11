package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.GeneratedRows;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior comparing for equality distinguishes two things, and the rows are asked for two.
 *
 * <p>{@code retries == 3} tells the value from every other value. Written as three ranges — below,
 * at, above — the rows would be asked to cover a distinction the model does not draw, and two rows
 * that already exercise everything the behavior does would be reported one short. The second class
 * is not a range and does not have to be: what a class needs is a way to tell whether a value is in
 * it and a value that stands for it, and neither wants an interval.
 *
 * <p>Where the same position also carries an ordering comparison the ranges come back, and rightly:
 * the model has drawn the further distinction itself.
 */
class AnEqualityDividesTheValuesInTwoTest {

    private static final String MODEL = """
            module example.equal

            data GiveUp
            data Again

            behavior verdict : (retries: Int) -> GiveUp | Again
            let verdict (retries) = if retries == 3 then GiveUp else Again

            example verdict
                | "the last try" : (3) -> GiveUp
                | "not the last" : (2) -> Again
            """;

    /**
     * The same behavior with the equality written as an ordering, which is the fault the points
     * beside the line are asked for.
     *
     * <p>Written with no rows, so that a test says which rows it holds this against. Every row a
     * test adds is one an author could have written for the rule as intended.
     */
    private static String writtenAs(String comparison) {
        return """
                module example.equal

                data GiveUp
                data Again

                behavior verdict : (retries: Int) -> GiveUp | Again
                let verdict (retries) = if retries %s 3 then GiveUp else Again

                example verdict
                """.formatted(comparison);
    }

    private static final String ALSO_ORDERED = """
            module example.both

            data GiveUp
            data Again
            data Never

            behavior verdict : (retries: Int) -> GiveUp | Again | Never
            let verdict (retries) =
                if retries == 3 then GiveUp
                else if retries <= 0 then Never
                else Again

            example verdict
                | "the last try" : (3) -> GiveUp
            """;

    private static final String BOUNDED_DECIMAL = """
            module example.ratio

            data A
            data B

            data Ratio = Decimal
                invariant lo = value >= 0m
                invariant hi = value <= 1m

            behavior pick : (x: Ratio) -> A | B

            let pick (x) = if x.value == 0.5m then A else B

            example pick
                | "at the half" : (Ratio(0.5m)) -> A
            """;

    /**
     * A row at the value beside the line catches an equality written as an ordering, and the value
     * on the line with a value far outside does not.
     *
     * <p>Which is what the two points beside the line are for. {@code retries <= 3} answers as
     * {@code retries == 3} does at three and at twenty, so rows there run green against a behavior
     * that is not the one the author meant; two is where the two rules part, and it is the value
     * below the line. {@code retries >= 3} parts from it at four, above the line — so one neighbour
     * catches one of the faults and the other catches the other, and a border owing a single
     * neighbour would have to choose which fault to be able to find.
     *
     * <p>The right behavior is held against the same rows, so that what the rows catch is the fault
     * and not a row this compiler disagrees with wherever it is put.
     */
    @Test
    void theValueBesideTheLineCatchesAnEqualityWrittenAsAnOrdering() {
        String onTheLine = "    | \"the last try\" : (3) -> GiveUp\n";
        String wellAbove = "    | \"well above\"   : (20) -> Again\n";
        String wellBelow = "    | \"well below\"   : (0) -> Again\n";
        String below = "    | \"below the line\" : (2) -> Again\n";
        String above = "    | \"above the line\" : (4) -> Again\n";

        // The rows the fault gets past are rows this compiler reports the border short of, and the
        // values it asks for are the ones below. Held first, because what the rest of this test
        // shows is that those values catch the fault — and a reading that stopped asking for them
        // would leave that true and useless.
        String short0f = reportOf(writtenAs("==") + onTheLine + wellBelow + wellAbove);
        assertTrue(short0f.contains("no row is at the OFF point below the line"), short0f);
        assertTrue(short0f.contains("no row is at the OFF point above the line"), short0f);
        assertTrue(short0f.contains("= 2") && short0f.contains("= 4"),
                "and the values it asks for there are the two beside the line:\n" + short0f);

        assertFalse(refused(writtenAs("<=") + onTheLine + wellAbove),
                "the value on the line and a value well outside the partition answer as `== 3`"
                        + " does, so `<= 3` runs green against them");
        assertFalse(refused(writtenAs(">=") + onTheLine + wellBelow),
                "and the same the other way round");

        assertTrue(refused(writtenAs("<=") + onTheLine + wellAbove + below),
                "the value below the line is where `<= 3` parts from `== 3`");
        assertTrue(refused(writtenAs(">=") + onTheLine + wellBelow + above),
                "and the value above it is where `>= 3` does");

        assertFalse(refused(writtenAs("==") + onTheLine + wellBelow + wellAbove + below + above),
                "and the rule as it was meant answers every one of those rows");
    }

    /** Whether a compile of {@code model} refuses it for a row the behavior answers otherwise. */
    private static boolean refused(String model) {
        try {
            Compiler.compiled(model, "Main", new java.util.ArrayList<>());
            return false;
        } catch (souther.compiler.diag.CompileException refusal) {
            return refusal.diagnostics().stream()
                    .anyMatch(each -> "E1905".equals(each.code()));
        }
    }

    private static String reportOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    /** The position is divided, and into the two classes the behavior tells apart. */
    @Test
    void theValueAndEverythingElseAreTheTwoClasses() {
        String human = reportOf(MODEL);

        assertTrue(human.contains("partition   axes 1"), human);
        assertFalse(notReadAbout(human, "retries"), human);
    }

    /** Two rows on either side of the equality cover it, with nothing left owed. */
    @Test
    void rowsOnEitherSideCoverIt() {
        String human = reportOf(MODEL);

        assertTrue(human.contains("equivalence partitions 2/2"), human);
        assertFalse(human.contains("no row is in"), human);
    }

    /**
     * The value itself is a row somebody owes, and so is the value beside it on each side.
     *
     * <p>Both neighbours are outside what the rule names, and a row at one says nothing about the
     * other: an implementation that answered as {@code retries <= 3} would would be caught by the 2
     * and pass the 4, and one that answered as {@code retries >= 3} the other way about. So the two
     * are two pieces of work, and a report that named them alike would ask twice for a row without
     * saying which value either time.
     */
    @Test
    void theValueIsOwedAndSoIsTheValueOnEachSideOfIt() {
        String human = reportOf(MODEL);

        assertTrue(human.contains("border      borders 1   obligations 2/5"), human);
        assertTrue(human.contains("no row is at the OFF point above the line"), human);
        assertTrue(human.contains("no row is at an OUT point below the line"), human);
        assertTrue(human.contains("no row is at an OUT point above the line"), human);
        assertFalse(human.contains("no row is at the OFF point below the line"),
                "the row at two is at the value below the line, so nothing is owed there:\n"
                        + human);
    }

    /**
     * The complement of a value in a bounded range has values, and stepping by one does not find them.
     *
     * <p>`0.5` in `[0, 1]` steps to `1.5` and `-0.5`, both outside. Neither is the answer to whether
     * the class has values — `0`, `0.1` and `1` are all in it — and a class reported as one nothing
     * can write a value for is the mistake this issue is about, one field over.
     */
    @Test
    void theComplementOfADecimalHasValuesTheStepDoesNotReach() {
        String block = generatedFor(BOUNDED_DECIMAL);

        assertFalse(block.contains("other than the ones singled out"), block);
        assertTrue(block.contains("example pick"), block);
    }

    private static String generatedFor(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, Adequacy.Filling> all = Adequacy.generatedOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(all, "the model under test compiles");
        return GeneratedRows.of(Adequacy.offeredFor(compilation.db(),
                        souther.compiler.query.OfferingRequest.overTheModule(
                                "example.ratio", false)),
                Map.of(), SourceRendering.namedByIdentity(SourceLayouts.NONE), compilation.db()).text();
    }

    /** An ordering comparison beside it is a distinction the model does draw, and is kept. */
    @Test
    void anOrderingComparisonBesideItStillDividesTheRanges() {
        String human = reportOf(ALSO_ORDERED);

        assertTrue(human.contains("partition   axes 1"), human);
        // The ordering line is still a line, so the ranges either side of it are still classes and
        // the equality's value is one more distinction on top of them rather than instead of them.
        assertTrue(human.contains("x <= 0"), human);
        assertTrue(human.contains("no row is in"), human);
    }

    /**
     * Whether any {@code not read} line of {@code block} is about {@code position}.
     *
     * <p>Asked as a line rather than as a prefix. A finding about a rule names the rule first and
     * the position after it, and one about a position names the position — so a test matching
     * `+not read: <position>+` stopped meaning anything for the first kind rather than failing,
     * which is a negative assertion that passes because the words moved.
     */
    private static boolean notReadAbout(String block, String position) {
        return block.lines().anyMatch(line -> line.contains("not read:")
                && (line.contains("not read: " + position + " ")
                        || line.contains("about `" + position + "`")));
    }
}
