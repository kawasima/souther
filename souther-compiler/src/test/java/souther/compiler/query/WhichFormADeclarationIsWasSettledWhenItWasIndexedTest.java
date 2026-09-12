package souther.compiler.query;

import souther.compiler.check.DeclarationKind;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which form a declaration is, and what it says, are two facts with two lifetimes.
 *
 * <p>The form is settled when the module is indexed: what the author wrote is a product, a sum or a
 * unit, and nothing below changes which. What the declaration says is worked out much further up,
 * out of the names in it resolving — so it moves when a field's type changes, when a clause is
 * rewritten, and when a line above the declaration moves.
 *
 * <p>Held as one answer, a reader that only had to tell a sum from a product would be told about
 * every one of those. Held apart, it is told about the one thing it asked: this pins each half at
 * the edit that separates them.
 */
class WhichFormADeclarationIsWasSettledWhenItWasIndexedTest {

    private static final TypeKey AMOUNT = new TypeKey("shop.prices", "Amount");

    private static final String PRODUCT = """
            module shop.prices exposing ( Amount )

            data Amount = { value: Int }
                invariant value >= 0

            data Note = Int
            """;

    /** The same two declarations, written the other way round. Nothing is added or taken away. */
    private static final String MOVED = """
            module shop.prices exposing ( Amount )

            data Note = Int

            data Amount = { value: Int }
                invariant value >= 0
            """;

    /** The same product, with another field and another rule — what it says, not which form it is. */
    private static final String SAID_DIFFERENTLY = """
            module shop.prices exposing ( Amount )

            data Amount = { value: Int, note: Int }
                invariant value >= 1

            data Note = Int
            """;

    /** The same address, written as a sum. */
    private static final String AS_A_SUM = """
            module shop.prices exposing ( Amount )

            data Amount = Small | Large

            data Small
            data Large

            data Note = Int
            """;

    /** Moving the declaration leaves the form alone: it is not where the declaration stands. */
    @Test
    void movingAProductLeavesTheFormItWasWrittenIn() {
        Compilation c = compiling(PRODUCT);
        assertEquals(DeclarationKind.PRODUCT, kindOf(c), "a product to begin with");

        edit(c, MOVED);

        assertEquals(DeclarationKind.PRODUCT, kindOf(c), "and a product where it now stands");
    }

    /** Nor is it what the declaration says: fields and clauses change and the form does not. */
    @Test
    void changingWhatAProductSaysLeavesTheFormItWasWrittenIn() {
        Compilation c = compiling(PRODUCT);
        assertEquals(DeclarationKind.PRODUCT, kindOf(c), "a product to begin with");

        edit(c, SAID_DIFFERENTLY);

        assertEquals(DeclarationKind.PRODUCT, kindOf(c),
                "a field added and a rule rewritten are what it says, not which form it is");
    }

    /** And the edit that does move it is the one that writes the same address as another form. */
    @Test
    void writingTheSameAddressAsASumChangesTheForm() {
        Compilation c = compiling(PRODUCT);
        assertEquals(DeclarationKind.PRODUCT, kindOf(c), "a product to begin with");

        edit(c, AS_A_SUM);

        assertEquals(DeclarationKind.SUM, kindOf(c), "and a sum once it is written as one");
    }

    /** Nothing is any form where nothing declares it. */
    @Test
    void anAddressNothingDeclaresHasNoForm() {
        Compilation c = compiling(PRODUCT);
        assertNull(kind(c, new TypeKey("shop.prices", "Nothing")),
                "a name its module does not write");
    }

    /**
     * The form of what the language declares, which no module of this compilation wrote.
     *
     * <p>Beside {@link Names.CompilationDeclares}, which answers no for the same name: whether
     * <em>this compilation</em> declares something and which form a declaration is are two
     * questions, and a reader asking a library name's form is asking the second.
     */
    @Test
    void whatTheLanguageDeclaresHasAFormToo() {
        Compilation c = compiling(PRODUCT);
        assertEquals(DeclarationKind.SUM,
                kind(c, new TypeKey("souther.decimal", "RoundingMode")),
                "the language's rounding modes are a sum, whoever is asking");
        assertEquals(Boolean.FALSE,
                c.db().ask(new Names.CompilationDeclares(
                        new TypeKey("souther.decimal", "RoundingMode"))).value(),
                "and no module of this compilation declares it, which is the other question");
    }

    /**
     * What the form is read off, said as what it depends on rather than as prose.
     *
     * <p>The declaration as it was indexed, and the library for the names no module wrote. Nothing
     * of what resolving, normalising or publishing made of it — which is the whole of why a reader
     * of the form is left alone by an edit that moves the declaration or changes what it says. Held
     * as a sentence in a comment this would go quiet the day something else was asked; held here it
     * reddens.
     */
    @Test
    void theFormIsReadOffTheDeclarationAsItWasIndexedAndNothingFurtherUp() {
        Compilation c = compiling(PRODUCT);
        kindOf(c);

        Set<String> asked = c.db().dependenciesOf(new Names.DeclarationKindOf(AMOUNT)).stream()
                .map(each -> each.getClass().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("souther.compiler.query.Names$Declaration"), asked,
                "the form comes off the indexed declaration and off nothing that reads one");
    }

    private static DeclarationKind kindOf(Compilation c) {
        return kind(c, AMOUNT);
    }

    private static DeclarationKind kind(Compilation c, TypeKey named) {
        Answer<DeclarationKind> answer = c.db().ask(new Names.DeclarationKindOf(named));
        return answer.present() ? answer.value() : null;
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
