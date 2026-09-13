package souther.compiler.query;

import souther.compiler.check.Derived;
import souther.compiler.check.Registry;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Being declared is one fact, and having a representation is another.
 *
 * <p>A registry holds declarations at one rung, and a rung is how far the work on them has got. What
 * it could not answer for is not a name the compilation stopped declaring — so a reader asking
 * whether something is declared there is answered from what declaring settled, and a reader asking
 * for the declaration is answered by the rung it named.
 *
 * <p>The two are held apart here by the state where they come apart: a product one of whose fields
 * names no type has no representation to derive, and is still a type its module declares.
 */
class WhetherSomethingIsDeclaredIsNotAFactAboutARepresentationTest {

    private static final String DECLARING = """
            module shop.prices exposing ( Amount )

            data Amount = Int
                invariant value >= 0

            data Note = Int
            """;

    /** The same two declarations, written the other way round. Nothing is added or taken away. */
    private static final String MOVED = """
            module shop.prices exposing ( Amount )

            data Note = Int

            data Amount = Int
                invariant value >= 0
            """;

    /** Imports it and names it in a signature, so what it asks of it is asked. */
    private static final String IMPORTING = """
            module shop.cart exposing ( Basket )

            import shop.prices ( Amount )

            data Basket = { paid: Amount }
            """;

    private static final TypeKey AMOUNT = new TypeKey("shop.prices", "Amount");

    /**
     * A declaration moving does not reach a reader that asked only whether it is declared.
     *
     * <p>Measured on the reader and not on the answer it reads. Whether something is declared is
     * worked out again when the declaration it is read off moves — its input did change — and comes
     * out the same, which is what leaves the reader alone. The importing module asks this of the
     * declaration it names, and of that declaration asks nothing else.
     */
    @Test
    void aDeclarationMovingDoesNotReachAReaderThatAskedOnlyWhetherItIsDeclared() {
        Compilation c = started();
        Answer<?> declared = c.db().ask(new Names.CompilationDeclares(AMOUNT));
        Answer<?> asking = c.db().ask(new Shapes.DerivedDeclarations("shop.cart"));
        assertEquals(Boolean.TRUE, declared.value(), "it is declared to begin with");

        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", MOVED);
        edited.put("cart.sou", IMPORTING);
        c.update(edited, Set.of());
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the edited workspace compiles: "
                + c.db().allReports());

        assertEquals(Boolean.TRUE, c.db().ask(new Names.CompilationDeclares(AMOUNT)).value(),
                "and it is declared still");
        assertSame(asking, c.db().ask(new Shapes.DerivedDeclarations("shop.cart")),
                "so the importer, which asks that of it and nothing more, was not worked out again");
    }

    /**
     * And nothing declares what nothing declares — including the language's own vocabulary, which
     * resolves and types like any other name and belongs to no module of a compilation.
     */
    @Test
    void nothingIsDeclaredWhereNoModuleOfTheCompilationDeclaresIt() {
        Compilation c = started();
        assertEquals(Boolean.FALSE,
                c.db().ask(new Names.CompilationDeclares(
                        new TypeKey("shop.prices", "Nothing"))).value(),
                "a name its module does not write");
        assertEquals(Boolean.FALSE,
                c.db().ask(new Names.CompilationDeclares(
                        new TypeKey("souther.decimal", "RoundingMode"))).value(),
                "and one the language declares, which no module of this compilation does");
    }

    /**
     * A product no representation could be derived for is a type its module declares.
     *
     * <p>Both halves, because either alone is met by the wrong thing: that the derived world has no
     * declaration to give says the state was reached, and that an identity comes back says the
     * question being answered is not that one.
     */
    @Test
    void anIdentityComesBackWhereTheDerivedWorldHasNoDeclarationToGive() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", """
                module shop.prices exposing ( Bag )

                data Bag = { held: Missing }
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();

        TypeKey bag = new TypeKey("shop.prices", "Bag");
        Registry<Derived.Def> derived = Names.derivedRegistry(c.db());

        assertNull(derived.declaration(bag),
                "nothing derived a representation for a product whose field names no type");
        assertNotNull(derived.identify(bag),
                "and it is a type its module declares all the same");
        assertNull(derived.identify(new TypeKey("shop.prices", "Nothing")),
                "while an address nothing declares is still nothing");
    }

    private static Compilation started() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", DECLARING);
        byId.put("cart.sou", IMPORTING);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with: "
                + c.db().allReports());
        return c;
    }
}
