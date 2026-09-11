package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Shape;
import souther.compiler.check.TypeView;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name written over a record takes its fields from the record and its rules from both.
 *
 * <p>Two declarations meet at such a position and they answer different questions. What the value
 * is made of is the record's answer, and the reading names that record — a walk stopping at the
 * outer name would find a position with no fields at all. What the value must satisfy is written on
 * either declaration and both reach it, so a reader taking only one of them offers a value the
 * other refuses.
 *
 * <p>Which is why the two are pinned against written-out rows rather than against each other. The
 * field structure and the rules are read by different parts of this compiler, and they agree here
 * only because each asks the declaration its own question is about. A reader that took the fields
 * from one declaration and the rules from the other would pass every test that compared the two
 * readings with one another.
 */
class AWrappedRecordTakesItsFieldsFromWhatItWrapsTest {

    /** A rule on the record, and the position written as a name over it. */
    private static final String WRAPPED = """
            module demo

            data Ok

            data Inner = { x: Int }
                invariant small = x >= 11 && x <= 13

            data Outer = Inner
            data Twice = Outer

            behavior run : (o: Outer) -> Ok
            let run (o) = Ok
            """;

    /** The same rule, with the position written as the record itself. */
    private static final String PLAIN = """
            module demo

            data Ok

            data Inner = { x: Int }
                invariant small = x >= 11 && x <= 13

            behavior run : (o: Inner) -> Ok
            let run (o) = Ok
            """;

    /** A rule on the name as well, which leaves less than the record's own does. */
    private static final String BOTH = """
            module demo

            data Ok

            data Inner = { x: Int }
                invariant small = x >= 11 && x <= 13

            data Outer = Inner
                invariant tighter = value.x >= 12

            behavior run : (o: Outer) -> Ok
            let run (o) = Ok
            """;

    /** Two names each written as the other, so the walk over them reaches no record at all. */
    private static final String IN_TERMS_OF_ITSELF = """
            module demo

            data Ok

            data A = B
            data B = A

            behavior run : (a: A) -> Ok
            let run (a) = Ok
            """;

    private static TypeView view(String source, String name) {
        RuleReadingSource rules = RuleReadings.ofSource(source);
        return TypeView.of(Type.ref(TypeSymbols.declared(
                new TypeKey(rules.symbols().module(), name))), rules.symbols());
    }

    private static String rowsOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return GeneratedRows.of(compilation, "demo", "run", true,
                SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();
    }

    /**
     * The fields, and which declaration they were read off.
     *
     * <p>The name in the shape is the record's and not the position's. What is made of those fields
     * is the record, so a reader going back to the declaration for what is written about one of them
     * has to go back to that record — going back to the name the position was written as would be
     * asking a declaration that has no such field.
     */
    @Test
    void theFieldsAndTheirDeclarationComeFromTheRecordTheNameWraps() {
        Shape.Product under = assertInstanceOf(Shape.Product.class, view(WRAPPED, "Outer").shape());
        assertEquals("Inner", under.name().name());
        assertEquals(Set.of("x"), under.fields().keySet());

        Shape.Product twice = assertInstanceOf(Shape.Product.class, view(WRAPPED, "Twice").shape());
        assertEquals("Inner", twice.name().name());
    }

    /** And the names, which are what the value is written under rather than what it is made of. */
    @Test
    void theNamesAreWhatThePositionIsWrittenAs() {
        assertEquals(List.of("Outer"), worn(WRAPPED, "Outer"));
        assertEquals(List.of("Twice", "Outer"), worn(WRAPPED, "Twice"));
        assertTrue(view(WRAPPED, "Inner").wrappers().isEmpty());
    }

    private static List<String> worn(String source, String name) {
        return view(source, name).wrappers().stream().map(TypeSymbol::name).toList();
    }

    /**
     * A row at the wrapped position stands where the record's rule leaves it, and is written under
     * the name.
     *
     * <p>Against the unwrapped model rather than against a figure written here. What the rule leaves
     * is the record's answer either way, and a name round the position changes how the row is
     * written and nothing else.
     */
    @Test
    void aRowUnderTheNameStandsWhereTheRecordsRuleLeavesIt() {
        String wrapped = rowsOf(WRAPPED);
        assertTrue(wrapped.contains("(Outer(Inner { x = 11 }))"), wrapped);
        assertTrue(wrapped.contains("(Outer(Inner { x = 13 }))"), wrapped);

        String plain = rowsOf(PLAIN);
        assertTrue(plain.contains("(Inner { x = 11 })"), plain);
        assertTrue(plain.contains("(Inner { x = 13 })"), plain);
    }

    /**
     * A rule written on the name is read too, and narrows what the record left.
     *
     * <p>The case that tells the two declarations apart. Both are read here — the floor moves to the
     * name's and the ceiling stays the record's — so a reader that dropped either would be offering
     * a value the other declaration refuses. Written at a figure the record's own rule does not
     * reach, because a bound the two agree on would pass whichever of them was read.
     */
    @Test
    void aRuleOnTheNameNarrowsWhatTheRecordLeft() {
        String rows = rowsOf(BOTH);
        assertTrue(rows.contains("(Outer(Inner { x = 12 }))"), rows);
        assertTrue(rows.contains("(Outer(Inner { x = 13 }))"), rows);
        assertTrue(!rows.contains("x = 11"), rows);
    }

    /**
     * A name written in terms of itself ends the walk over the names rather than repeating it.
     *
     * <p>Both names come off — each is written as the other, so both are worn — and what is left
     * underneath is a name that stands for no shape. The answer is that the position could not be
     * interpreted, which is a reading, and the walk that produced it terminated.
     */
    @Test
    void aNameWrittenInTermsOfItselfEndsTheWalk() {
        TypeView cycle = view(IN_TERMS_OF_ITSELF, "A");
        assertEquals(List.of("A", "B"), worn(IN_TERMS_OF_ITSELF, "A"));
        assertInstanceOf(Shape.Unresolved.class, cycle.shape());
        rowsOf(IN_TERMS_OF_ITSELF);   // reaches an answer rather than walking the names forever
    }
}
