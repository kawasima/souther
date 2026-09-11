package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sum every one of whose cases is a unit data is ordered by the order its cases are declared —
 * the order a deal moves through its stages, which nothing else in the model carries. This is what
 * F# gives a discriminated union, Haskell a derived {@code Ord}, Rust a derived {@code Ord} over
 * variants, and Java an enum's ordinal; Elm is the one language that withholds it, and withholding
 * it is why a report has to project the cases onto {@code Int} by hand (issue #161).
 */
class CompileEnumerationOrderTest {

    private Object run(String src, String outType) throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In", Map.of("n", 1L));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        return Codecs.encode(loader, outType, Codecs.apply(behavior, in));
    }

    private static String rendered(String src) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        return new HumanRenderer(false)
                .render(e.diagnostic(), new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
    }

    /**
     * The set of ordered values grew, so the reports that name it have to name the new member too —
     * otherwise a reader comparing two enumerations is told only primitives and newtypes are ordered,
     * which is no longer what the compiler believes.
     */
    @Test
    void theComparisonReportNamesTheEnumerationAndWhatMakesTwoOfThemComparable() {
        String src = """
                module demo

                data Stage = Prospecting | Won
                data Tier = Gold | Silver
                data In = { n: Int }
                data Out = { b: Bool }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { b = Prospecting < Gold }
                """;
        String out = rendered(src);

        assertTrue(out.contains("enumeration"), out);
        assertTrue(out.contains("the same"), out);   // two enumerations do not compare with each other
    }

    @Test
    void theSortReportNamesTheEnumeration() {
        String src = """
                module demo

                data Row = { n: Int }
                data In = { rows: List<Row> }
                data Out = { rows: List<Row> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { rows = List.sort(i.rows) }
                """;
        String out = rendered(src);

        assertTrue(out.contains("enumeration"), out);
    }

    @Test
    void sortOrdersTheCasesAsDeclared() throws Exception {
        String src = """
                module demo

                data Stage = Prospecting | Negotiation | Won | Lost
                data In = { n: Int }
                data Out = { stages: List<Stage> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { stages = List.sort([ Lost, Won, Prospecting ]) }
                """;

        assertEquals(Map.of("stages", List.of("Prospecting", "Won", "Lost")), run(src, "demo.Out"));
    }

    @Test
    void anEarlierCaseIsLessThanALaterOne() throws Exception {
        String src = """
                module demo

                data Stage = Prospecting | Negotiation | Won | Lost
                data In = { n: Int }
                data Out = { early: Bool, late: Bool }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { early = Prospecting < Won, late = Won < Prospecting }
                """;

        assertEquals(Map.of("early", true, "late", false), run(src, "demo.Out"));
    }

    /**
     * A unit data may be a case of two sums, which place it differently, so the case value alone
     * carries no order. The sum being compared against says which order applies.
     */
    @Test
    void aCaseSharedByTwoEnumerationsIsOrderedByTheSumItIsComparedWith() throws Exception {
        String src = """
                module demo

                data Stage = Prospecting | Won
                data Terminal = Won | Prospecting
                data In = { n: Int }
                data Out = { early: Bool }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    let s: Stage = Prospecting
                    Out { early = s < Won }
                }
                """;

        assertEquals(Map.of("early", true), run(src, "demo.Out"));
    }

    /**
     * A list of case values is typed as the union of their types, and what orders it is the one
     * enumeration that lists all of them — not one that each of them, on its own, belongs to alone.
     * {@code Prospecting} is a case of two sums and carries no order by itself; together with
     * {@code Negotiation}, which only {@code Stage} lists, the pair has exactly one.
     */
    @Test
    void aUnionIsOrderedByTheOneEnumerationThatListsAllOfIt() throws Exception {
        String src = """
                module demo

                data Stage = Prospecting | Negotiation | Won
                data Entry = Prospecting | Won
                data In = { n: Int }
                data Out = { stages: List<Stage> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { stages = List.sort([ Negotiation, Prospecting ]) }
                """;

        assertEquals(Map.of("stages", List.of("Prospecting", "Negotiation")), run(src, "demo.Out"));
    }

    @Test
    void aUnionTwoEnumerationsBothListIsNotOrdered() {
        String src = """
                module demo

                data Stage = Prospecting | Won
                data Entry = Prospecting | Won
                data In = { n: Int }
                data Out = { stages: List<Stage> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { stages = List.sort([ Won, Prospecting ]) }
                """;
        String out = rendered(src);

        // two enumerations list both cases, so neither one is the order this list has
        assertTrue(out.contains("ordered"), out);
    }

    /**
     * And the other two kernels that take the enumeration's order: the extremes of a list of cases.
     *
     * <p>Here because which kernels reach their runtime method with a comparator is a decision
     * {@code codegen.BodyGen} makes, and whether it made the right one shows in what a program
     * answers rather than in what descriptor it was emitted at. A kernel routed there and never run
     * over an enumeration is one nothing would notice being routed somewhere else.
     */
    @Test
    void maxAndMinAnswerTheLastAndFirstCaseDeclared() throws Exception {
        String src = """
                module demo

                data Stage = Prospecting | Negotiation | Won | Lost
                data In = { n: Int }
                data Out = { furthest: Stage?, earliest: Stage? }

                behavior run : (i: In) -> Out constructs Out

                let run (i) =
                    Out { furthest = List.max([ Prospecting, Won, Negotiation ]),
                          earliest = List.min([ Prospecting, Won, Negotiation ]) }
                """;

        assertEquals(Map.of("furthest", "Won", "earliest", "Prospecting"), run(src, "demo.Out"));
    }

    @Test
    void sortByOrdersRowsOnTheirStage() throws Exception {
        String src = """
                module demo

                data Stage = Prospecting | Negotiation | Won | Lost
                data Deal = { stage: Stage }
                data In = { n: Int }
                data Out = { deals: List<Deal> }

                behavior run : (i: In) -> Out constructs Out, Deal

                let run (i) =
                    Out { deals = List.sortBy(d -> d.stage,
                            [ Deal { stage = Won }, Deal { stage = Prospecting },
                              Deal { stage = Negotiation } ]) }
                """;

        assertEquals(Map.of("deals", List.of(
                        Map.of("stage", "Prospecting"),
                        Map.of("stage", "Negotiation"),
                        Map.of("stage", "Won"))),
                run(src, "demo.Out"));
    }
}
