package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A partition of a position is what the model treats alike, and not what this compiler can name.
 *
 * <p>{@code 3 * d <= 1} tells 0.3 from 0.34: the behavior answers one way for one and the other way
 * for the other, so the position has two classes. What it does not have is a number to name the line
 * by — a third is no decimal this language writes — and a measure that counted the classes it could
 * write a boundary for would be reporting how far this compiler's own numbers reach rather than what
 * the model distinguishes. Which is the confusion #880 is about, one level in.
 *
 * <p>Naming them is a separate question with a separate answer. Where the line falls on a value of
 * the position, the classes are named by that value as they always were; where it does not, they are
 * named by the rule that drew it. What tells two classes apart is neither of those spellings: two
 * rules that divide a position in one place make one division however they were written.
 */
class AnEquivalencePartitionIsWhatTheModelDistinguishesTest {

    private static souther.compiler.query.PartitionEvidence measured(String type, String guards) {
        return measured(type, guards,
                "    | \"one\" : (" + (type.equals("Decimal") ? "0.1m" : "1") + ") -> Yes { v = 1 }");
    }

    /** The lines the behavior's positions met, whosever the row at each point is. */
    private static List<BorderAssessment> lines(String type, String guards, String rows) {
        Compilation compilation = compiled(type, guards, rows);
        Map<String, List<BorderAssessment>> all =
                Adequacy.readingsOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(all, "the model under test compiles");
        return all.get("f");
    }

    private static PartitionEvidence measured(String type, String guards, String rows) {
        Compilation compilation = compiled(type, guards, rows);
        Map<String, PartitionEvidence> all = compilation.db()
                .ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
        assertNotNull(all, "the model under test compiles");
        return all.get("f");
    }

    private static Compilation compiled(String type, String guards, String rows) {
        Compilation compilation = Compilation.ofSource("""
                module example.exact

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (n: %s) -> Result
                    constructs Yes, No

                let f (n) = {
                %s
                    Yes { v = 1 }
                }

                example f
                %s
                """.formatted(type, guards, rows), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static List<String> classesOf(String type, String guards) {
        PartitionEvidence evidence = measured(type, guards);
        assertEquals(1, evidence.axes().size(),
                "the behavior takes one position and the model divides it");
        return evidence.axes().get(0).classes();
    }

    /**
     * A line at a place the position holds no value at still divides it.
     *
     * <p>Two classes, because the behavior answers two ways. The line is at a third and no decimal
     * is a third, so what names the classes is the rule.
     */
    @Test
    void aLineTheCarrierNamesNoValueAtStillDividesThePosition() {
        assertEquals(List.of("n/3 * x <= 1", "n/1 < 3 * x"),
                classesOf("Decimal", "    guard 3m * n <= 1m else No { why = 0 }"),
                "named by the rule, in numbers this language has, rather than by a third");
    }

    /**
     * And two rules that draw that line at two scales draw one line.
     *
     * <p>A third and two sixths are one place. Told apart by the numbers the rules carry, the
     * position would have three classes and the one between them would hold nothing.
     */
    @Test
    void twoScalesOfOneLineAtSuchAPlaceAreOneLine() {
        assertEquals(classesOf("Decimal", "    guard 3m * n <= 1m else No { why = 0 }"),
                classesOf("Decimal", "    guard 6m * n <= 2m else No { why = 0 }"),
                "a third and two sixths are one place, and the classes either side are the same"
                        + " two");
        assertEquals(List.of("n/3 * x <= 1", "n/1 < 3 * x"),
                classesOf("Decimal", "    guard 6m * n <= 2m else No { why = 0 }"),
                "and the name is the reduced one, so the rule that wrote it in sixths does not"
                        + " leave a class nothing else can be compared against");
    }

    /**
     * And two lines at two such places leave three classes.
     *
     * <p>The control for the test above: a reading that made every line at an unnameable place one
     * line would pass that one and fail this.
     */
    @Test
    void twoLinesAtTwoSuchPlacesLeaveThreeClasses() {
        assertEquals(List.of("n/3 * x <= 1", "n/1 < 3 * x <= 2", "n/2 < 3 * x"),
                classesOf("Decimal", """
                    guard 3m * n > 1m else No { why = 0 }
                    guard 3m * n > 2m else No { why = 1 }"""));
    }

    /**
     * And a row either side of such a line falls in the class its side is.
     *
     * <p>Which is what a class is for. Naming two of them is worth nothing if both hold every value:
     * the classifier answers with the first class that says yes, so two classes that both say yes to
     * everything are one class counted twice, and a report saying two of two are covered has
     * measured nothing.
     */
    @Test
    void aRowEitherSideOfSuchALineFallsInTheClassItsSideIs() {
        PartitionEvidence measured = measured("Decimal", """
                    guard 3m * n <= 1m else No { why = 0 }""",
                """
                    | "under" : (0.30m) -> Yes { v = 1 }
                    | "over" : (0.34m) -> No { why = 0 }""");

        assertEquals(2, WhatTheRowsReached.at(measured.axes().get(0)).covered().size(),
                "three tenths is under a third and thirty-four hundredths is over it: "
                        + WhatTheRowsReached.at(measured.axes().get(0)).covered());
    }

    /**
     * And a line the position can name does not swallow the values past a line it cannot.
     *
     * <p>{@code n > 0.2} and {@code 3 * n > 1} draw two lines through one position, and the classes
     * between them are three. The second line falls at a third, which no decimal is — and a class
     * that could not say where it stops ran to the end of the order, so a half was in the class
     * below the third and in the class above it at once.
     */
    @Test
    void aClassStopsAtALineThePositionCannotName() {
        List<String> covered = WhatTheRowsReached.at(measured("Decimal", """
                    guard n > 0.2m else No { why = 0 }
                    guard 3m * n > 1m else No { why = 1 }""",
                """
                    | "low" : (0.1m) -> No { why = 0 }
                    | "mid" : (0.3m) -> No { why = 1 }
                    | "high" : (0.5m) -> Yes { v = 1 }""")
                .axes().get(0)).covered().stream().sorted().toList();

        assertEquals(3, covered.size(), "one row in each of the three classes: " + covered);
        assertEquals(List.of("n/0.2 < x and 3 * x <= 1", "n/1 < 3 * x", "n/x <= 0.2"), covered,
                "and the class between the two lines says where it stops, in the words each line"
                        + " can be said in");
    }

    /**
     * And a row can be composed for a class between two such lines.
     *
     * <p>Which is what tells a class the rules leave nothing in from one this compiler was looking
     * for values of in the wrong places. A third and two thirds have every decimal between them and
     * no whole number, so a search that looked between the whole numbers either side of them looked
     * between one and nothing — and reported a class with a half in it as one nothing can be
     * written in.
     */
    @Test
    void aRowIsComposedForAClassBetweenTwoSuchLines() {
        Compilation compilation = Compilation.ofSource("""
                module example.between

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (n: Decimal) -> Result
                    constructs Yes, No

                let f (n) = {
                    guard 3m * n > 1m else No { why = 0 }
                    guard 3m * n > 2m else No { why = 1 }
                    Yes { v = 1 }
                }

                example f
                    | "one" : (0.1m) -> No { why = 0 }
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String rows = souther.compiler.report.GeneratedRows.of(compilation, "example.between", "f",
                true, souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();

        assertTrue(rows.contains("\"n=1 < 3 * x <= 2\""),
                "the class between the two lines is offered a row:\n" + rows);
        assertFalse(rows.contains("no row for `n=1 < 3 * x <= 2`"),
                "and is not reported as one nothing can be written in:\n" + rows);
    }

    /**
     * And a run narrower than any scale anybody would guess still gets a row.
     *
     * <p>A third and a third and a hundred-billionth are a definite distance apart, and every pair
     * of decimals has a decimal between them. Looked for at a fixed handful of scales, the two lines
     * rounded past each other at every one of them and the class between was reported as one no
     * value of the position lies inside — which is a false answer and not a search that gave up.
     */
    @Test
    void aRunNarrowerThanAnyScaleAnybodyWouldGuessStillGetsARow() {
        Compilation compilation = Compilation.ofSource("""
                module example.narrow

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (n: Decimal) -> Result
                    constructs Yes, No

                let f (n) = {
                    guard 3m * n > 1m else No { why = 0 }
                    guard 30000000000000m * n > 10000000000001m else No { why = 1 }
                    Yes { v = 1 }
                }

                example f
                    | "one" : (0.1m) -> No { why = 0 }
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String rows = souther.compiler.report.GeneratedRows.of(compilation, "example.narrow", "f",
                true, souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();

        assertFalse(rows.contains("no row for `n=1 < 3 * x and"),
                "a decimal lies between the two lines, so the class between them is not one nothing"
                        + " can be written in:\n" + rows);
    }

    /**
     * A class and the point a border owes inside it are the same run, and are said the same way.
     *
     * <p>Which is the whole of why the two measures were put on one arrangement. A reader told that
     * a class has no row and that a point inside it has none is being told about one run twice, and
     * two spellings of it are two runs as far as anybody reading can tell.
     *
     * <p>Read off the line rather than off the values beside it, the point came back with both its
     * ends turned round — {@code 1 <= 3 * n < 2} for a run the class calls
     * {@code 1 < 3 * x <= 2} — because a line the quantity never reaches has no value below it to
     * be on the low side of.
     */
    @Test
    void aClassAndThePointInsideItAreSaidTheSameWay() {
        PartitionEvidence measured = measured("Decimal", """
                    guard 3m * n > 1m else No { why = 0 }
                    guard 3m * n > 2m else No { why = 1 }""",
                "    | \"one\" : (0.1m) -> No { why = 0 }");
        String middle = measured.axes().get(0).classes().get(1);

        assertEquals("n/1 < 3 * x <= 2", middle);
        assertEquals("1 < 3 * n <= 2",
                lines("Decimal", """
                            guard 3m * n > 1m else No { why = 0 }
                            guard 3m * n > 2m else No { why = 1 }""",
                        "    | \"one\" : (0.1m) -> No { why = 0 }").stream()
                        .filter(each -> each.label().equals("3 * n = 1")).findFirst()
                        .orElseThrow().against(souther.compiler.partition.PointRole.IN),
                "the run the border at a third bounds is the class between the two lines, said in"
                        + " the same words: " + middle);
    }

    /**
     * And a run over a multiple of the quantity says how much of it, once.
     *
     * <p>How much of a quantity a rule is about is the quantity's own answer: twice
     * {@code 3 * a + 6 * b} is {@code 6 * a + 12 * b}, and nobody outside it can compose that.
     * Written by putting the multiple in front of whatever the quantity calls itself, a point came
     * back asking for a row against {@code 2 * 3 * n <= 5} while the class that is the same run
     * called it {@code 6 * x <= 5}.
     */
    @Test
    void aRunOverAMultipleOfTheQuantitySaysHowMuchOfItOnce() {
        PartitionEvidence measured = measured("Decimal", """
                    guard 3m * n > 1m else No { why = 0 }
                    guard 6m * n > 5m else No { why = 1 }""",
                "    | \"one\" : (0.1m) -> No { why = 0 }");

        assertEquals("1 < 3 * n and 6 * n <= 5",
                lines("Decimal", """
                            guard 3m * n > 1m else No { why = 0 }
                            guard 6m * n > 5m else No { why = 1 }""",
                        "    | \"one\" : (0.1m) -> No { why = 0 }").stream()
                        .filter(each -> each.label().equals("3 * n = 1")).findFirst()
                        .orElseThrow().against(souther.compiler.partition.PointRole.IN),
                "and not `2 * 3 * n`, which is the same rule said twice over");
        assertEquals("n/1 < 3 * x and 6 * x <= 5", measured.axes().get(0).classes().get(1),
                "which is what the class that is this run says");
    }

    /**
     * And a run between a bound and such a line is looked in as closely as one between two lines.
     *
     * <p>A run ends where a rule parts the values or where the rules leave the quantity, and how
     * closely to look for a value of it is a question about whichever two of those it lies between.
     * Asked of its lines alone, a run with a bound at one end was looked for at whole numbers — and
     * a class holding a decimal a ten-billionth under a third was reported as one no value of the
     * position lies inside, which is a false answer about a class an author can write a row in.
     */
    @Test
    void aRunBetweenABoundAndSuchALineIsLookedInAsCloselyAsOneBetweenTwoLines() {
        for (String facing : List.of("value >= 0.33333333333330m", "value <= 0.33333333333340m")) {
            Compilation compilation = Compilation.ofSource("""
                    module example.bounded

                    data Narrow = Decimal
                        invariant %s

                    data No = { why: Int }
                    data Yes = { v: Int }
                    data Result = No | Yes

                    behavior f : (n: Narrow) -> Result
                        constructs Yes, No

                    let f (n) = {
                        guard 3m * n.value > 1m else No { why = 0 }
                        Yes { v = 1 }
                    }

                    example f
                        | "one" : (Narrow(0.1m)) -> No { why = 0 }
                    """.formatted(facing), "Main");
            compilation.measure(Adequacy.Asked.fullReport());
            compilation.answerEverything();
            String rows = souther.compiler.report.GeneratedRows.of(compilation, "example.bounded",
                    "f", true, souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();

            assertFalse(rows.contains("no value this position can hold lies inside this range"),
                    "a decimal lies between the bound and the third, so the class between them is"
                            + " not one nothing can be written in (" + facing + "):\n" + rows);
        }
    }

    /**
     * And a line that does fall on a value of the position is still named by that value.
     *
     * <p>{@code 2 * n <= 9} cuts the whole numbers between four and five, and four is a value the
     * position holds. Naming every class by the rule that drew it would spell this one
     * {@code 2 * n <= 9}, which is the same set said less plainly.
     */
    @Test
    void aLineThatFallsOnAValueOfThePositionIsNamedByThatValue() {
        assertEquals(List.of("n/x <= 4", "n/4 < x"),
                classesOf("Int", "    guard 2 * n <= 9 else No { why = 0 }"));
    }
}
