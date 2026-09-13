package souther.compiler.query;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.Type;
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
 * What a name wraps, asked apart from whether it wraps anything.
 *
 * <p>The two are answerable at different times, which is why they are two answers. Whether a
 * declaration wears one value was settled when its module was indexed; what the one value is takes
 * the names in the declaration resolving, so this is read off the resolved declaration — the lowest
 * rung that can say.
 *
 * <p>And it is read off that and nothing above it. An edit that moves the declaration does work this
 * out again and comes out with the same type, so a reader that only asks what a name wraps keeps
 * what it had.
 */
class WhatANameWrapsIsReadOffTheDeclarationOnceItsNamesResolveTest {

    private static final TypeKey AMOUNT = new TypeKey("shop.prices", "Amount");

    private static final String NEWTYPE = """
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

    /** The one value written as something else, which is the edit this does answer differently. */
    private static final String WRAPPING_SOMETHING_ELSE = """
            module shop.prices exposing ( Amount )

            data Amount = Decimal
                invariant value >= 0.0m

            data Note = Int
            """;

    /** The same address written as a record of one field, which wears no one value. */
    private static final String AS_A_RECORD = """
            module shop.prices exposing ( Amount )

            data Amount = { value: Int }
                invariant value >= 0

            data Note = Int
            """;

    /** Moving the declaration leaves what it wraps alone. */
    @Test
    void movingANewtypeLeavesWhatItWraps() {
        Compilation c = compiling(NEWTYPE);
        assertEquals(Type.INT, wraps(c), "an Int under a name to begin with");

        edit(c, MOVED);

        assertEquals(Type.INT, wraps(c), "and the same Int where it now stands");
    }

    /** And writing the one value as another type is the edit that does move it. */
    @Test
    void writingTheOneValueAsAnotherTypeChangesWhatItWraps() {
        Compilation c = compiling(NEWTYPE);

        edit(c, WRAPPING_SOMETHING_ELSE);

        assertEquals(Type.DECIMAL, wraps(c), "what it wraps is what its one value was written as");
    }

    /**
     * A record of one field called {@code value} wraps nothing.
     *
     * <p>What a newtype wraps is the value it was written as, not a field that happens to be spelled
     * that way — so this is answered from whether the declaration wears one value, which is asked
     * first and asked of the index.
     */
    @Test
    void aRecordWhoseFieldIsCalledValueWrapsNothing() {
        Compilation c = compiling(AS_A_RECORD);
        assertNull(wraps(c), "a record of one field is not one value wearing a name");
    }

    /** Nothing is wrapped where nothing declares it. */
    @Test
    void anAddressNothingDeclaresWrapsNothing() {
        Compilation c = compiling(NEWTYPE);
        assertNull(wraps(c, new TypeKey("shop.prices", "Nothing")));
    }

    /**
     * What it is read off, said as what it depends on rather than as prose.
     *
     * <p>Whether the declaration wears one value, and — only then — the declaration with its names
     * resolved. Nothing of what normalising or publishing made of it, which is what lets a reader of
     * this be left alone by an edit that changes what the declaration says.
     */
    @Test
    void itIsReadOffWhetherItWearsOneAndThenOffTheResolvedDeclaration() {
        Compilation c = compiling(NEWTYPE);
        wraps(c);

        Set<String> asked = c.db().dependenciesOf(new Shapes.NewtypeInnerOf(AMOUNT)).stream()
                .map(each -> each.getClass().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("souther.compiler.query.Names$DeclarationIsNewtype",
                        "souther.compiler.query.Names$ResolvedDeclaration"), asked,
                "what it wraps comes off the resolved declaration and off nothing that reads one");
    }

    /**
     * A declaration that wears no one value is answered without the resolved declaration being read.
     *
     * <p>Which is most of the asking: a reader walking the names off a position asks this of every
     * type it meets, and a product is the usual answer. Read through the declaration anyway, every
     * one of those readers would depend on a tree that moves when the declaration does.
     */
    @Test
    void aDeclarationWearingNoOneValueIsAnsweredWithoutReadingIt() {
        Compilation c = compiling(AS_A_RECORD);
        wraps(c);

        Set<String> asked = c.db().dependenciesOf(new Shapes.NewtypeInnerOf(AMOUNT)).stream()
                .map(each -> each.getClass().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("souther.compiler.query.Names$DeclarationIsNewtype"), asked,
                "a declaration that wears no one value has nothing under it to read");
    }

    /**
     * The language declares no product, so there is no library newtype for this to answer about.
     *
     * <p>Held as a check rather than said in a comment: the day the library declares one, this goes
     * red and what a reader of a library name is told stops being nothing by accident.
     */
    @Test
    void theLanguageDeclaresNoProductForThisToAnswerAbout() {
        Map<TypeKey, Hir.Def> language = DefaultStdlib.get().languageDeclarations();
        assertTrue(language.size() > 4, "the library was read: " + language.size());

        assertEquals(Set.of(), language.entrySet().stream()
                        .filter(each -> each.getValue() instanceof Hir.Data)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                "the language declares sums and units, so nothing it declares wraps one value");
    }

    private static Type wraps(Compilation c) {
        return wraps(c, AMOUNT);
    }

    private static Type wraps(Compilation c, TypeKey named) {
        Answer<Type> answer = c.db().ask(new Shapes.NewtypeInnerOf(named));
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
