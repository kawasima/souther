package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.types.Type;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A named sum the language calls an enumeration is one the boundary writes as a bare tag, and the
 * two are asked of different places.
 *
 * <p>{@link TypeOps#isUnitOnlySum} answers a question about the language — a type whose values can
 * be written out ({@link ValueUniverse}) and whose declaration orders them ({@code TypeOps
 * .orderingEnumeration}). {@link Boundary} answers a question about how a value crosses. They are
 * kept apart on purpose: folding them would make one answer decide both, and the day either moved
 * the other would move with it unasked.
 *
 * <p>Kept apart, they walk the atoms twice, so this holds the relation the spec states between them
 * (spec §sum-discrimination): a unit-only sum crosses as its case's name. It is not a test that two
 * implementations agree — it is the rule that lets them be two. Breaking it deliberately means
 * saying, here, that a sum can be an enumeration in the language and a discriminated object on the
 * wire.
 */
class AnEnumerationIsOneAnswerToTheLanguageAndTheBoundaryTest {

    private static final String MODULE = """
            module m

            data Prospecting
            data Negotiation
            data Won
            data Stage = Prospecting | Negotiation | Won

            data Draft
            data Card = { no: String }
            data Cash = Int
            data Payment = Draft | Card | Cash

            data Domestic
            data Overseas
            data Region = Domestic | Overseas
            data Where  = Region | Draft
            """;

    private final Hir.Module module = derive(MODULE);
    private final Symbols symbols = TypeChecker.symbols(module, DefaultStdlib.get());

    private final PublishedDeclarations said = ScopedDeclarations.of(symbols);

    private final DeclarationKinds forms = ScopedDeclarations.kindsOf(symbols);

    @Test
    void everyNamedSumIsAnEnumerationToBothOrToNeither() {
        for (Hir.Def def : module.defs()) {
            if (def instanceof Hir.SumData sum) {
                Type type = Type.ref(sum.declares());
                assertEquals(TypeOps.isUnitOnlySum(type, forms, said),
                        Boundary.of(type, forms, said).representation()
                                instanceof Boundary.Representation.Enumeration,
                        sum.name() + ": the language and the boundary disagree about the form");
            }
        }
    }

    /** And the sums are not all of one kind, so the test above is not two constants agreeing. */
    @Test
    void theModuleHasOneOfEach() {
        assertEquals(true, isEnumeration("Stage"));
        assertEquals(true, isEnumeration("Where"));
        assertEquals(false, isEnumeration("Payment"));
    }

    /**
     * A unit data on its own is not an enumeration, though its only atom is a unit.
     *
     * <p>The atoms alone would say it is. A unit crosses on its own as an empty object — the tag is
     * what admitting it into a sum adds — so being a set of alternatives is a term of the form and
     * not a guard on asking. Held here because it is the one case where reading the atoms without
     * that term gives the wrong answer, and a reader tidying the term away would pass every other
     * test in this file.
     */
    @Test
    void aUnitDataOnItsOwnIsNotAnEnumeration() {
        assertFalse(Boundary.of(Type.ref(named("Draft")), forms, said).representation()
                instanceof Boundary.Representation.Enumeration);
        assertFalse(TypeOps.isUnitOnlySum(Type.ref(named("Draft")), forms, said));
    }

    /**
     * A union of units is written as a bare tag though it is no named sum.
     *
     * <p>{@link TypeOps#isUnitOnlySum} says no, and is right to: it answers about a declaration, and
     * a union is not one. {@link Boundary} says yes, and is right to: the generated union encoder
     * writes the member's name and nothing else. The two are not in disagreement — they are the two
     * questions, and this is where holding them apart earns itself.
     *
     * <p>What used to read the boundary's question through the language's one is
     * {@code NeutralForm.readsABareName}, which decides the neutral form of a value standing at a
     * position. Answered by {@code isUnitOnlySum}, a union of units came out as {@code {}} — the
     * empty object a unit writes on its own — where its generated encoder writes {@code "Denied"}.
     * No verification outcome moved when it changed, both the row's value and the answer being
     * normalised through the same walk; what changed is that the neutral form is now the form the
     * boundary actually reads.
     */
    @Test
    void aUnionOfUnitsIsWrittenAsABareTagAndIsNoNamedSum() {
        Type union = Type.union(new java.util.LinkedHashSet<>(
                java.util.List.of(named("Prospecting"), named("Won"))));
        assertTrue(Boundary.of(union, forms, said).representation()
                instanceof Boundary.Representation.Enumeration);
        assertFalse(TypeOps.isUnitOnlySum(union, forms, said),
                "a union is not a declaration, so the language's question does not reach it");
    }

    private boolean isEnumeration(String sum) {
        return Boundary.of(Type.ref(named(sum)), forms, said).representation()
                instanceof Boundary.Representation.Enumeration;
    }

    private souther.compiler.types.TypeSymbol named(String name) {
        for (Hir.Def d : module.defs()) {
            if (d.name().equals(name)) {
                return d.declares();
            }
        }
        throw new AssertionError("the module does not declare " + name);
    }

    private static Hir.Module derive(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", source);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY)
                .db().ask(new Names.Resolved("m")).value();
    }
}
