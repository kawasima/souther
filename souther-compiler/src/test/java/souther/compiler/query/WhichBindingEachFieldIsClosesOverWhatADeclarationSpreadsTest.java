package souther.compiler.query;

import souther.compiler.meta.ModulePath;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which binding each field of a declaration is, and what moves it.
 *
 * <p>The closure a walk from one declaration makes — its own fields and the fields its spreads bring
 * in — answered as one thing because which field a name means depends on what the walk reached
 * first. What each field <em>holds</em> is not here, and that is the whole of why this answer stays
 * put: a field's type is what a reader of types asks about, and a reader of bindings is not.
 */
class WhichBindingEachFieldIsClosesOverWhatADeclarationSpreadsTest {

    private static final TypeKey LINE = new TypeKey("shop.prices", "Line");
    private static final TypeKey MONEY = new TypeKey("shop.prices", "Money");

    private static final String SPREADING = """
            module shop.prices exposing ( Line )

            data Money = { cents: Int }

            data Line = { ...Money, qty: Int }
            """;

    /** The same declarations with a field of the spread one written as something else. */
    private static final String ANOTHER_TYPE = """
            module shop.prices exposing ( Line )

            data Money = { cents: Decimal }

            data Line = { ...Money, qty: Int }
            """;

    /** The spread written after the field it takes, which is the same fields in another order. */
    private static final String REORDERED = """
            module shop.prices exposing ( Line )

            data Money = { cents: Int }

            data Line = { qty: Int, ...Money }
            """;

    /** Two fields of one declaration, and the same two written the other way round. */
    private static final String TWO_OF_ITS_OWN = """
            module shop.prices exposing ( Line )

            data Line = { qty: Int, note: Int }
            """;

    private static final String TWO_OF_ITS_OWN_SWAPPED = """
            module shop.prices exposing ( Line )

            data Line = { note: Int, qty: Int }
            """;

    /** The same declarations, moved down the file. */
    private static final String MOVED = """
            module shop.prices exposing ( Line )

            data Note = Int

            data Money = { cents: Int }

            data Line = { ...Money, qty: Int }
            """;

    /** A field a spread brought in keeps the binding of the declaration that wrote it. */
    @Test
    void aFieldASpreadBringsInIsBoundByTheDeclarationThatWroteIt() {
        Compilation c = compiling(SPREADING);

        assertEquals(new BindingOwner.OfFields(TypeSymbols.declared(LINE)),
                bindings(c, LINE).get("qty").owner(),
                "the taking declaration binds what it writes");
        assertEquals(new BindingOwner.OfFields(TypeSymbols.declared(MONEY)),
                bindings(c, LINE).get("cents").owner(),
                "and the declaration that wrote the spread field still binds that one, because the"
                        + " clause that reads it was written there");
    }

    /** What a field holds is a different answer, and moving it leaves every binding where it was. */
    @Test
    void changingWhatAFieldHoldsLeavesEveryBindingWhereItWas() {
        Compilation c = compiling(SPREADING);
        Map<String, BindingId> before = bindings(c, LINE);

        edit(c, ANOTHER_TYPE);

        assertEquals(before, bindings(c, LINE),
                "a field's type is not what a binding is");
    }

    /**
     * A declaration's own fields come first however the spread was written.
     *
     * <p>The walk binds what the declaration writes and then goes down what it spreads, so where the
     * spread stands among the fields decides nothing: not which binding a field is — an ordinal
     * counts a declaration's own fields, and a spread contributes none of them — and not the order
     * the closure is listed in.
     */
    @Test
    void aDeclarationsOwnFieldsComeFirstHoweverTheSpreadWasWritten() {
        Compilation c = compiling(SPREADING);
        Map<String, BindingId> before = bindings(c, LINE);
        assertEquals(List.of("qty", "cents"), List.copyOf(before.keySet()),
                "what the declaration writes, then what it spreads in");

        edit(c, REORDERED);

        assertEquals(before, bindings(c, LINE),
                "the spread written after the field it takes is the same closure");
        assertEquals(List.of("qty", "cents"), List.copyOf(bindings(c, LINE).keySet()),
                "listed in the same order, which is the walk's and not the text's");
    }

    /** And reordering two fields of one declaration is what does move them. */
    @Test
    void reorderingTwoOfOneDeclarationsOwnFieldsMovesThem() {
        Compilation c = compiling(TWO_OF_ITS_OWN);
        Map<String, BindingId> before = bindings(c, LINE);
        assertEquals(0, before.get("qty").ordinal(), "written first");
        assertEquals(1, before.get("note").ordinal(), "and written second");

        edit(c, TWO_OF_ITS_OWN_SWAPPED);

        assertEquals(1, bindings(c, LINE).get("qty").ordinal(),
                "which field of its owner a field is, is what the order decides");
        assertEquals(0, bindings(c, LINE).get("note").ordinal());
    }

    /** Moving the declarations down the file moves no binding. */
    @Test
    void movingTheDeclarationsLeavesEveryBindingWhereItWas() {
        Compilation c = compiling(SPREADING);
        Map<String, BindingId> before = bindings(c, LINE);

        edit(c, MOVED);

        assertEquals(before, bindings(c, LINE),
                "a binding is not where a declaration stands");
    }

    /**
     * What it is read off: the declaration it was asked about, and the ones it spreads.
     *
     * <p>A closure and not a question asked again per declaration — what the walk reached first
     * decides which binding a repeated name keeps, and that is nobody's answer if the walk is
     * several. Nothing of what the declarations say or where they stand is read.
     */
    @Test
    void itIsReadOffTheDeclarationsTheWalkReachesAndNothingElse() {
        Compilation c = compiling(SPREADING);
        bindings(c, LINE);

        Set<String> asked = c.db().dependenciesOf(new Shapes.FieldBindingsOf(LINE)).stream()
                .map(Object::toString)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("ResolvedDeclaration[named=shop.prices.Line]",
                        "ResolvedDeclaration[named=shop.prices.Money]"), asked,
                "the walk read the declaration it was asked about and the one it spreads");
    }

    /** A declaration that spreads nothing reads only itself. */
    @Test
    void aDeclarationThatSpreadsNothingReadsOnlyItself() {
        Compilation c = compiling(SPREADING);
        bindings(c, MONEY);

        assertEquals(Set.of("ResolvedDeclaration[named=shop.prices.Money]"),
                c.db().dependenciesOf(new Shapes.FieldBindingsOf(MONEY)).stream()
                        .map(Object::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static Map<String, BindingId> bindings(Compilation c, TypeKey named) {
        Answer<Map<String, BindingId>> answer = c.db().ask(new Shapes.FieldBindingsOf(named));
        return answer.present() ? answer.value() : Map.of();
    }

    private static Compilation compiling(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", source);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with: "
                + c.db().allReports());
        return c;
    }

    private static void edit(Compilation c, String source) {
        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", source);
        c.update(edited, Set.of());
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the edited workspace compiles: "
                + c.db().allReports());
    }
}
