package souther.compiler;

import souther.compiler.cst.SourceLayout;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code List.sum} / {@code List.product} over a numeric element. The element is {@code Int} or
 * {@code Decimal} — the two types the arithmetic operators are defined for — and the empty-list
 * literal takes which of the two from the position it is written in.
 */
class CompileNumericListFoldTest {

    @Test
    void sumsAndMultipliesADecimalList() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sum, product )

                data In = { xs: List<Decimal> }
                data Out = { total: Decimal, prod: Decimal }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { total = sum(i.xs), prod = product(i.xs) }
                """), getClass().getClassLoader());

        Map<?, ?> m = run(loader, List.of(new BigDecimal("1.5"), new BigDecimal("2.25")));
        assertEquals(0, new BigDecimal("3.75").compareTo((BigDecimal) m.get("total")));
        assertEquals(0, new BigDecimal("3.375").compareTo((BigDecimal) m.get("prod")));
    }

    @Test
    void sumsAndMultipliesAnIntList() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sum, product )

                data In = { xs: List<Int> }
                data Out = { total: Int, prod: Int }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { total = sum(i.xs), prod = product(i.xs) }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("xs", List.of(2L, 3L, 4L)));
        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(Emitted.behavior(loader, "demo", "run").getConstructor().newInstance(), in));
        assertEquals(9L, m.get("total"));
        assertEquals(24L, m.get("prod"));
    }

    @Test
    void theEmptyLiteralTakesItsElementFromTheExpectedType() throws Exception {
        // `sum([])` answers the seed, and which seed — `0` or `0.0m` — is the field's type, not a
        // default the language picks. Both fields hold the same written call.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sum, product )

                data In = { xs: List<Decimal> }
                data Out = {
                    intSum: Int
                    , intProduct: Int
                    , decimalSum: Decimal
                    , decimalProduct: Decimal
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    intSum = sum([]),
                    intProduct = product([]),
                    decimalSum = sum([]),
                    decimalProduct = product([])
                }
                """), getClass().getClassLoader());

        Map<?, ?> m = run(loader, List.of());
        assertEquals(0L, m.get("intSum"));
        assertEquals(1L, m.get("intProduct"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) m.get("decimalSum")));
        assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal) m.get("decimalProduct")));
    }

    @Test
    void anAnnotatedBindingGivesTheEmptyLiteralItsElement() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sum )

                data In = { xs: List<Decimal> }
                data Out = { total: Decimal }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    let total: Decimal = sum([])
                    Out { total = total }
                }
                """), getClass().getClassLoader());

        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) run(loader, List.of()).get("total")));
    }

    @Test
    void theEmptyLiteralWithNothingToTakeItsElementFromIsRejected() {
        // No expected type here, so neither `0` nor `0.0m` is the answer. Picking Int would be a
        // numeric default rule, so the compiler asks for the annotation instead.
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                import List ( sum )

                data In = { xs: List<Int> }
                data Out = { total: Int }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    let total = sum([])
                    Out { total = total }
                }
                """));
        assertTrue(e.getMessage().contains("sum"), e.getMessage());
        assertTrue(e.getMessage().contains("Int") && e.getMessage().contains("Decimal"),
                "the report names the two elements the annotation may choose between: " + e.getMessage());
    }

    @Test
    void aPositionThatIsNotNumericIsAMismatchRatherThanAMissingAnnotation() {
        // The field says String, so nothing about the element is unknown here. Asking for the
        // annotation would send the reader after something the position already carries.
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                import List ( sum )

                data In = { xs: List<Int> }
                data Out = { name: String }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { name = sum([]) }
                """));
        assertTrue(e.getMessage().contains("String"), e.getMessage());
        assertTrue(!e.getMessage().toLowerCase(Locale.ROOT).contains("annotate"),
                "the position is annotated; annotating it again changes nothing: " + e.getMessage());
    }

    @Test
    void aNewtypePositionIsAMismatchRatherThanAMissingAnnotation() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                import List ( sum )

                data Hours = Decimal
                data In = { xs: List<Decimal> }
                data Out = { total: Hours }

                behavior run : (i: In) -> Out constructs Out, Hours

                let run (i) = Out { total = sum([]) }
                """));
        assertTrue(e.getMessage().contains("Hours"), e.getMessage());
        assertTrue(!e.getMessage().toLowerCase(Locale.ROOT).contains("annotate"),
                "the position is annotated; annotating it again changes nothing: " + e.getMessage());
    }

    @Test
    void anOptionalFieldAsksForTheValueItWraps() throws Exception {
        // A `?` field being given a value asks for the value, not for an optional, so it says which
        // of Int and Decimal the seed is exactly as a plain field does.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sum )

                data In = { xs: List<Decimal> }
                data Out = { total: Decimal? }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { total = sum([]) }
                """), getClass().getClassLoader());

        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) run(loader, List.of()).get("total")));
    }

    @Test
    void aNewtypeOverDecimalIsNotANumericElement() {
        // `Hours` wraps a Decimal but declares no addition and no zero of its own, so a list of them
        // has no sum. The value is summed by mapping to the wrapped field first.
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                import List ( sum )

                data Hours = Decimal
                data In = { xs: List<Hours> }
                data Out = { total: Decimal }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { total = sum(i.xs) }
                """));
        assertTrue(e.getMessage().contains("`sum` needs a list of Int or Decimal"), e.getMessage());
        assertTrue(e.getMessage().contains("Hours"), e.getMessage());
    }

    @Test
    void mappingToTheWrappedFieldIsHowANewtypeListIsSummed() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List ( sum, map )

                data Hours = Decimal
                data In = { xs: List<Hours> }
                data Out = { total: Decimal }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { total = sum(map(h -> h.value, i.xs)) }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In",
                Map.of("xs", List.of(new BigDecimal("7.5"), new BigDecimal("8.0"))));
        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out",
                Codecs.apply(Emitted.behavior(loader, "demo", "run").getConstructor().newInstance(), in));
        assertEquals(0, new BigDecimal("15.5").compareTo((BigDecimal) m.get("total")));
    }

    @Test
    void aNonNumericElementIsRejected() {
        String module = """
                module demo

                import List ( product )

                data In = { xs: List<String> }
                data Out = { total: Int }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { total = product(i.xs) }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(module));
        assertTrue(e.getMessage().contains("`product` needs a list of Int or Decimal"), e.getMessage());
        assertTrue(e.getMessage().contains("String"), e.getMessage());
        assertProductIsNotCalledSum(e, module);
    }

    @Test
    void anEmptyProductInANonNumericPositionIsReportedAboutProduct() {
        String module = """
                module demo

                import List ( product )

                data In = { xs: List<Int> }
                data Out = { name: String }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { name = product([]) }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(module));
        assertProductIsNotCalledSum(e, module);
    }

    /** The two folds share their reports, so the reports must name the one that was written. A hint
     * telling the author of `product` to sum something sends them to a different operation. */
    private static void assertProductIsNotCalledSum(CompileException e, String module) {
        String rendered = new HumanRenderer(false)
                .render(e.diagnostic(), new SourceContext("demo.sou", module, SourceLayout.of(module)), Locale.ENGLISH);
        assertTrue(rendered.contains("product"), rendered);
        assertFalse(rendered.contains("sum"), "the report is about `product`: " + rendered);
        assertFalse(e.getMessage().contains("sum"), e.getMessage());
    }

    private static Map<?, ?> run(BytesClassLoader loader, List<BigDecimal> xs) throws Exception {
        Object in = Codecs.decoded(loader, "demo.In", Map.of("xs", xs));
        Object behavior = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();
        return (Map<?, ?>) Codecs.encode(loader, "demo.Out", Codecs.apply(behavior, in));
    }
}
