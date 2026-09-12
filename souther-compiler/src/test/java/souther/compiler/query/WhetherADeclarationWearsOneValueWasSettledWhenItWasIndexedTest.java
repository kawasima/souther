package souther.compiler.query;

import souther.compiler.check.DeclarationKind;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import souther.test.RepositoryLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a declaration wears one value, asked apart from which form it is.
 *
 * <p>Both were settled when the module was indexed, and they still move separately: writing the same
 * address as a sum changes the form and leaves this alone, because a sum is no more one value wearing
 * a name than a product was. Answered together as one four-valued form, every reader that only asks
 * this would be worked out again by that edit — and there are more of those than of any other
 * question a reader asks about somebody else's declaration.
 *
 * <p>What it wraps is not this question. That is the type its one field was written as, which takes
 * the names in the declaration resolving, and nothing here reads it.
 */
class WhetherADeclarationWearsOneValueWasSettledWhenItWasIndexedTest {

    /** Read once: what this asks of it does not change between its checks. */
    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

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

    /** The same newtype over something else, which is what it wraps and not whether it wears one. */
    private static final String WRAPPING_SOMETHING_ELSE = """
            module shop.prices exposing ( Amount )

            data Amount = Decimal
                invariant value >= 0.0m

            data Note = Int
            """;

    /** The same address written as a sum: another form, and no more one value wearing a name. */
    private static final String AS_A_SUM = """
            module shop.prices exposing ( Amount )

            data Amount = Small | Large

            data Small
            data Large

            data Note = Int
            """;

    /** The same address written as a record, which is the edit that does change this. */
    private static final String AS_A_RECORD = """
            module shop.prices exposing ( Amount )

            data Amount = { value: Int }
                invariant value >= 0

            data Note = Int
            """;

    /** Moving the declaration leaves it alone: it is not where the declaration stands. */
    @Test
    void movingANewtypeLeavesItWearingOneValue() {
        Compilation c = compiling(NEWTYPE);
        assertEquals(Boolean.TRUE, wearsOneValue(c), "one value wearing a name to begin with");

        edit(c, MOVED);

        assertEquals(Boolean.TRUE, wearsOneValue(c), "and still one where it now stands");
    }

    /** Nor is it what the one value is: the wrapped type changes and this does not. */
    @Test
    void changingWhatItWrapsLeavesItWearingOneValue() {
        Compilation c = compiling(NEWTYPE);

        edit(c, WRAPPING_SOMETHING_ELSE);

        assertEquals(Boolean.TRUE, wearsOneValue(c),
                "what the one value is, is a different question from whether there is one");
    }

    /**
     * The edit that moves the form and not this one — which is the whole reason the two are apart.
     *
     * <p>Both answers are read here, so what is held is not that each is right on its own but that
     * one edit moved one of them.
     */
    @Test
    void writingTheSameAddressAsASumChangesTheFormAndNotThis() {
        Compilation c = compiling(NEWTYPE);
        assertEquals(DeclarationKind.PRODUCT, kindOf(c), "a product to begin with");
        assertEquals(Boolean.TRUE, wearsOneValue(c), "and one value wearing a name");

        edit(c, AS_A_SUM);

        assertEquals(DeclarationKind.SUM, kindOf(c), "the form did move");
        assertEquals(Boolean.FALSE, wearsOneValue(c),
                "and a sum wears no one value, which is this answer moving with it");
    }

    /** And the edit that does move this one alone: the same form, written out as a field. */
    @Test
    void writingTheOneValueAsAFieldLeavesTheFormAndChangesThis() {
        Compilation c = compiling(NEWTYPE);

        edit(c, AS_A_RECORD);

        assertEquals(DeclarationKind.PRODUCT, kindOf(c), "a product either way");
        assertEquals(Boolean.FALSE, wearsOneValue(c),
                "and a record of one field is not one value wearing a name");
    }

    /** Nothing wears anything where nothing declares it, which is not the same as wearing none. */
    @Test
    void anAddressNothingDeclaresAnswersNothing() {
        Compilation c = compiling(NEWTYPE);
        assertNull(wearsOneValue(c, new TypeKey("shop.prices", "Nothing")),
                "a name its module does not write");
    }

    /**
     * What the language declares is answered too, and answered {@code false} rather than nothing.
     *
     * <p>Beside {@link Names.CompilationDeclares}, which answers no for the same name. A reader
     * asking whether a library declaration wears one value is asking about a declaration; told
     * nothing instead, it would read the answer as a name nothing declares.
     */
    @Test
    void whatTheLanguageDeclaresIsAnsweredAsWell() {
        Compilation c = compiling(NEWTYPE);
        TypeKey rounding = new TypeKey("souther.decimal", "RoundingMode");

        assertEquals(Boolean.FALSE, wearsOneValue(c, rounding),
                "the language's rounding modes are a sum, and a sum wears no one value");
        assertEquals(Boolean.FALSE, c.db().ask(new Names.CompilationDeclares(rounding)).value(),
                "and no module of this compilation declares it, which is the other question");
    }

    /**
     * What this is read off, said as what it depends on rather than as prose.
     *
     * <p>The declaration as it was indexed, and the library for the names no module wrote. Nothing
     * of what resolving, normalising or publishing made of it.
     */
    @Test
    void itIsReadOffTheDeclarationAsItWasIndexedAndNothingFurtherUp() {
        Compilation c = compiling(NEWTYPE);
        wearsOneValue(c);

        Set<String> asked = c.db().dependenciesOf(new Names.DeclarationIsNewtype(AMOUNT)).stream()
                .map(each -> each.getClass().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("souther.compiler.query.Names$Declaration"), asked,
                "it comes off the indexed declaration and off nothing that reads one");
    }

    /**
     * Which readers still take this off a declaration they are holding.
     *
     * <p>{@code Location.isStepAsWritten} is how a walk that has not been handed the compilation's
     * answer asks, and every call of it is a reader whose dependency on the declaration this cut has
     * not removed. Listed by file, so moving one across the fence is what shortens the list and
     * writing a new one is what reddens this.
     *
     * <p>It counts calls and not files, because a walk already holding a declaration is where the
     * next such reading is cheapest to add.
     */
    @Test
    void theReadersStillTakingThisOffADeclarationAreListed() throws IOException {
        List<Path> sources = REPOSITORY.mainJavaSources();
        assertTrue(sources.size() > 100,
                () -> "the scan found only " + sources.size() + " sources, which is not the tree");

        List<String> calls = new ArrayList<>();
        for (Path source : sources) {
            String code = withoutComments(Files.readString(source, StandardCharsets.UTF_8));
            assertFalse(code.contains("import static souther.compiler.check.Location.isStepAsWritten"),
                    () -> source.getFileName() + " takes the question under a bare name, which is a"
                            + " call site this counts by the spelling it is written in");
            int here = 0;
            int at = code.indexOf(ASKED_OF_THE_DECLARATION);
            while (at >= 0) {
                here++;
                at = code.indexOf(ASKED_OF_THE_DECLARATION, at + ASKED_OF_THE_DECLARATION.length());
            }
            if (source.getFileName().toString().equals(DECLARES_IT)) {
                here = 0;   // where it is declared, which is no call of it
            }
            for (int each = 0; each < here; each++) {
                calls.add(source.getFileName().toString());
            }
        }

        assertEquals(List.of("InputPath.java"), calls,
                "one walk still reads this off the declaration it holds, and it reads that"
                        + " declaration for what a name stands for as well");
    }

    private static final String ASKED_OF_THE_DECLARATION = "isStepAsWritten(";

    /** Where it is declared, whose own mention of it is the declaration and no call. */
    private static final String DECLARES_IT = "Location.java";

    /**
     * The source with its comments taken out, so that a javadoc naming the question does not read as
     * a call of it.
     */
    private static String withoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int at = 0;
        while (at < source.length()) {
            char here = source.charAt(at);
            char next = at + 1 < source.length() ? source.charAt(at + 1) : '\0';
            if (here == '/' && next == '/') {
                while (at < source.length() && source.charAt(at) != '\n') {
                    at++;
                }
            } else if (here == '/' && next == '*') {
                at += 2;
                while (at + 1 < source.length()
                        && !(source.charAt(at) == '*' && source.charAt(at + 1) == '/')) {
                    at++;
                }
                at = Math.min(source.length(), at + 2);
            } else {
                out.append(here);
                at++;
            }
        }
        return out.toString();
    }

    private static Boolean wearsOneValue(Compilation c) {
        return wearsOneValue(c, AMOUNT);
    }

    private static Boolean wearsOneValue(Compilation c, TypeKey named) {
        Answer<Boolean> answer = c.db().ask(new Names.DeclarationIsNewtype(named));
        return answer.present() ? answer.value() : null;
    }

    private static DeclarationKind kindOf(Compilation c) {
        Answer<DeclarationKind> answer = c.db().ask(new Names.DeclarationKindOf(AMOUNT));
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
