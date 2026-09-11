package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a boundary is drawn on is a number a rule compares, and where that number is read from is a
 * property of the term rather than of the position.
 *
 * <p>A newtype's own value is one such number. So is the length of a string and the size of a
 * container, which the discharge procedure has always named the same way (spec
 * §invariant-discharge-terms). Read as positions they are not numbers at all, and every position
 * holding one came back as a place the model draws no line through — said of a position whose own
 * type bounds it, and said again of one the body compares in the line above.
 */
class ABoundaryIsDrawnOnATermAndNotAPositionTest {

    /**
     * One model stating the same bound four ways, and a numeric one beside them to be read against.
     *
     * <p>Beside them on purpose: what a length is owed is what a number is owed, and the way to say
     * so is to measure both in one run rather than to write down what each should say.
     */
    private static final String MODEL = """
            module example.terms

            data Label = String
                invariant nonEmpty = String.length(value) > 0

            data Names = List<String>
                invariant twoNames = List.length(value) >= 2

            data Codes = Set<String>
                invariant someCodes = Set.size(value) >= 1

            data Props = Map<String, String>
                invariant someProps = Map.size(value) >= 1

            data Size = Int
                invariant atLeastOne = value >= 1

            data T = { label: Label, names: Names, codes: Codes, props: Props, size: Size }

            behavior look : (t: T) -> Int
            let look (t) = t.size.value

            example look
                | (T { label = Label("a")
                     , names = Names([ "p", "q", "r" ])
                     , codes = Codes([ "c" ])
                     , props = Props([ ("k", "v") ])
                     , size = Size(1) }) -> 1
            """;

    private static Compilation compiled(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static Map<String, PartitionEvidence> partitions(String source) {
        Compilation compilation = compiled(source);
        return compilation.db().ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
    }

    /** The lines each behavior of the one module in {@code source} was measured at. */
    private static Map<String, List<BorderAssessment>> lines(String source) {
        Compilation compilation = compiled(source);
        return Adequacy.readingsOf(compilation.db(), compilation.modules().get(0));
    }

    private static List<String> labels(List<BorderAssessment> lines) {
        return lines.stream().map(BorderAssessment::label).sorted().toList();
    }

    /** A size call in an invariant draws the line its number names, and the line is named by the term
     * it was drawn on. */
    @Test
    void anInvariantOverASizeDrawsItsEdge() {
        assertEquals(List.of(
                        "List.length(t.names) = 2",
                        "Map.size(t.props) = 1",
                        "Set.size(t.codes) = 1",
                        "String.length(t.label) = 1",
                        "t.size = 1"),
                labels(lines(MODEL).get("look")));
    }

    /** One point against the line each, and it is the {@code ON} point. Everything outside an
     * invariant is refused at construction, so there is no row beyond it to ask for — which is what
     * the numeric bound beside them has always done, and what the border now says in so many
     * words. */
    @Test
    void anInvariantsEdgeIsOwedOnceAndNotOnBothSides() {
        for (BorderAssessment line : lines(MODEL).get("look")) {
            assertNotNull(line.owedAt(souther.compiler.partition.PointRole.ON), line.label());
            assertEquals(new ItemAssessment.NotOwed(
                            souther.compiler.partition.NotOwedReason.THE_RULES_REFUSE_IT),
                    line.at(souther.compiler.partition.PointRole.OFF), line.label());
            assertEquals(new ItemAssessment.NotOwed(
                            souther.compiler.partition.NotOwedReason.THE_RULES_REFUSE_IT),
                    line.at(souther.compiler.partition.PointRole.OUT), line.label());
        }
    }

    /** The row is read through the term, so a row whose string is one character long is at the length
     * boundary and a row of three names is not at the one that wants two. */
    @Test
    void aRowIsReadThroughTheTermThatDrewTheLine() {
        Map<String, BorderAssessment> byLabel = new java.util.LinkedHashMap<>();
        lines(MODEL).get("look").forEach(b -> byLabel.put(b.label(), b));

        assertTrue(onPointOf(byLabel, "String.length(t.label) = 1").hasRowWitness(),
                "the row's label is one character long");
        assertTrue(onPointOf(byLabel, "Set.size(t.codes) = 1").hasRowWitness(),
                "the row's set holds one code");
        assertEquals(ItemAssessment.Coverage.NoHit.class,
                onPointOf(byLabel, "List.length(t.names) = 2").coverage().made().orElseThrow().getClass(),
                "the row holds three names, and nothing was unreadable about it");
    }

    /** The borders of {@code behavior} in {@code source}, by the line each is at. */
    private static Map<String, BorderAssessment> byLabel(String source, String behavior) {
        Map<String, BorderAssessment> out = new java.util.LinkedHashMap<>();
        lines(source).get(behavior).forEach(b -> out.put(b.label(), b));
        return out;
    }

    /** The point on the line of the border named {@code label}. */
    private static ItemAssessment.Owed onPointOf(Map<String, BorderAssessment> byLabel,
                                                 String label) {
        return byLabel.get(label).owedAt(souther.compiler.partition.PointRole.ON);
    }

    /**
     * The report stops saying the model draws no line through a position its own type bounds.
     *
     * <p>Of the positions this is about, which are the ones a clause bounds. What the collections
     * hold is a position too and no clause of this model says anything about it, so the model
     * really does divide those no way — that sentence is the true one there, and it is what the
     * lines below are held to being.
     */
    @Test
    void noneOfThemIsReportedAsAPositionNothingDivides() {
        String human = AdequacyReport.of(compiled(MODEL)).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
        List<String> said = human.lines().map(String::strip)
                .filter(line -> line.startsWith("· not derivable:")).toList();

        assertEquals(List.of(), said.stream()
                        .filter(line -> !line.contains("[*]")).toList(),
                () -> "every position a clause bounds is one the report says is divided: " + human);
    }

    private static final String GUARDED = """
            module example.guarded

            data Label = String
                invariant nonEmpty = String.length(value) > 0

            data T = { label: Label }

            behavior guarded : (t: T) -> Int
            let guarded (t) = {
                guard String.length(t.label.value) > 3 else 0
                1
            }

            example guarded
                | (T { label = Label("abcd") }) -> 1
            """;

    /**
     * A guard over the same term draws the same kind of line, and its own value has values on both
     * sides of it.
     *
     * <p>The invariant's edge is still there beside it: two rules at one position are two lines, and
     * a length is no different there either.
     */
    @Test
    void aGuardOverATermDrawsTheLineAndItsNeighbour() {
        assertEquals(List.of(
                        "String.length(t.label) = 1",
                        "String.length(t.label) = 3"),
                labels(lines(GUARDED).get("guarded")),
                "two borders, and the guard's owes a row at 4 as its OFF point rather than being a"
                        + " line of its own");
        // `> 3` puts the 3 outside the partition it names, so the row there is the border's OFF
        // point and the 4 is its ON point — one border, two points, and neither is a line of its
        // own.
        assertEquals("3", byLabel(GUARDED, "guarded").get("String.length(t.label) = 3")
                        .against(souther.compiler.partition.PointRole.OFF));
        assertEquals("4", byLabel(GUARDED, "guarded").get("String.length(t.label) = 3")
                        .against(souther.compiler.partition.PointRole.ON));
    }

    /** And the position is measured, where before the body compared a number at a position the report
     * called undivided. */
    @Test
    void theGuardedPositionIsAnAxis() {
        PartitionEvidence partition = partitions(GUARDED).get("guarded");
        assertEquals(List.of("String.length(t.label)"),
                partition.axes().stream().map(PartitionEvidence.AxisCoverage::path).toList());
    }

    private static final String IMPORTED = """
            module example.imported

            import String ( length )

            data Label = String
                invariant nonEmpty = length(value) > 0

            data T = { label: Label }

            behavior look : (t: T) -> Int
            let look (t) = {
                guard length(t.label.value) > 3 else 0
                1
            }
            """;

    /**
     * The same rule written without its qualifier.
     *
     * <p>An import lets a library operation be written bare (spec §imports), so a reader comparing
     * the text {@code "String.length"} would answer "no rule here" to a clause that plainly draws a
     * line — and would look, from the report, exactly like a reader that had read it and found
     * nothing. Both ends ask the name the call resolved to instead, and this is what says so.
     */
    @Test
    void aMeasureWrittenWithoutItsQualifierIsTheSameTerm() {
        assertEquals(List.of(
                        "String.length(t.label) = 1",
                        "String.length(t.label) = 3"),
                labels(lines(IMPORTED).get("look")));
    }

    private static final String UNBOUNDED = """
            module example.unbounded

            data T = { names: List<String> }

            behavior atLeastNone : (t: T) -> Int
            let atLeastNone (t) = {
                guard List.length(t.names) >= 0 else 0
                1
            }

            example atLeastNone
                | (T { names = [] }) -> 1
            """;

    /**
     * A size is never negative, and the term says so rather than the rules that happen to be written
     * about it.
     *
     * <p>The guard's line is at zero and its neighbour below is minus one, which is a row nobody can
     * write. Nothing in this model states a lower bound, so what keeps that obligation from being
     * asked for is what the term knows about its own values.
     */
    @Test
    void aSizeIsNeverNegativeAndNoRowIsAskedForBelowZero() {
        assertEquals(List.of("List.length(t.names) = 0"),
                labels(lines(UNBOUNDED).get("atLeastNone")));
    }
}
