package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.check.ClauseMeaning;
import souther.compiler.check.DeclarationMeaning;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which fields a clause reads is answered by the declaration that wrote it.
 *
 * <p>What a construction has to have filled for a clause to be read at all is a fact about the
 * declaration, and every reader of the clause needs it. Worked out by each of them instead, a
 * reader walks a tree of its own to answer a question the declaring module has already answered —
 * and answers it against the bindings its own reading made, which are not the ones the declaring
 * reading made.
 *
 * <p>So it is published, and named as a field is reached through the declaration. The two things
 * that could go wrong with a published answer are held here together: it may move when nothing was
 * said, and it may stand still when something was. Which fields count is held beside them, because
 * a reader building a construction's fields from this is the next thing to be written.
 */
class WhichFieldsAClauseReadsIsTheDeclarationsAnswerTest {

    /** Two fields and two clauses, each clause reading a different one of them. */
    private static final String DECLARING = """
            module shop.prices exposing ( Range )

            data Range = { low: Int, high: Int }
                invariant low >= 0
                invariant high >= low
            """;

    /** The same, with a line written above it that says nothing. */
    private static final String WITH_A_COMMENT = "// what a range is\n" + DECLARING;

    /** The same, with the first clause written about the other field. */
    private static final String READING_THE_OTHER_FIELD = """
            module shop.prices exposing ( Range )

            data Range = { low: Int, high: Int }
                invariant high >= 0
                invariant high >= low
            """;

    /** One field spread in and one written here, with a clause that reads both. */
    private static final String SPREADING_A_FIELD = """
            module shop.prices exposing ( Range )

            data Bounds = { low: Int }

            data Range = { ...Bounds, high: Int }
                invariant high >= low
            """;

    private static final TypeKey RANGE = new TypeKey("shop.prices", "Range");

    /** A line that says nothing moves every position under it and moves none of this. */
    @Test
    void aCommentWrittenAboveItDoesNotChangeWhichFieldsAClauseReads() {
        Compilation c = started(DECLARING);
        List<Set<String>> before = fieldsRead(c);

        edit(c, WITH_A_COMMENT);

        assertEquals(before, fieldsRead(c),
                "which fields a clause reads is what the declaration says, and the declaration says"
                        + " the same thing");
    }

    /** And a clause written about another field reads another field. */
    @Test
    void andAClauseWrittenAboutAnotherFieldReadsIt() {
        Compilation c = started(DECLARING);
        assertEquals(List.of(Set.of("low"), Set.of("high", "low")), fieldsRead(c),
                "the first names one field and the second names both");

        edit(c, READING_THE_OTHER_FIELD);

        assertEquals(List.of(Set.of("high"), Set.of("high", "low")), fieldsRead(c),
                "the first clause is about the other field now");
        assertNotEquals(Set.of("low"), fieldsRead(c).getFirst(),
                "a published answer that never moves is not an answer about the declaration");
    }

    /**
     * And a field a spread brought in is one of them.
     *
     * <p>Which fields have to be filled is the question, and a construction of this declaration
     * fills a field it spread in like any it wrote. Where the field was written is a different
     * question, asked of what the declaration writes ({@code DeclarationMeaning.Product#fields})
     * and answered there — so a reader building a construction out of this one is reading the
     * fields of a value and not a list of what somebody typed on this line.
     */
    @Test
    void andAFieldASpreadBroughtInIsOneOfThem() {
        assertEquals(List.of(Set.of("high", "low")), fieldsRead(started(SPREADING_A_FIELD)),
                "the clause reads the field this declaration wrote and the one it spread in");
    }

    /** What each clause of {@code Range} says it reads, in the order the declaration writes them. */
    private static List<Set<String>> fieldsRead(Compilation c) {
        DeclarationMeaning meaning = c.db().ask(new Shapes.MeaningOf(RANGE)).value();
        return ((DeclarationMeaning.Product) meaning).clauses().stream()
                .map(ClauseMeaning.Stated.class::cast)
                .map(ClauseMeaning.Stated::fieldsRead)
                .toList();
    }

    private static void edit(Compilation c, String prices) {
        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", prices);
        c.update(edited, Set.of());
        c.answerEverything();
    }

    private static Compilation started(String prices) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", prices);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with: "
                + c.db().allReports());
        return c;
    }
}
