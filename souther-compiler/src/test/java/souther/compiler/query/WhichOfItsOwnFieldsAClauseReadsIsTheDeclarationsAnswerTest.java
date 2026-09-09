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
 * Which of its own fields a clause reads is answered by the declaration that wrote it.
 *
 * <p>What a construction has to have filled for a clause to be read at all is a fact about the
 * declaration, and every reader of the clause needs it. Worked out by each of them instead, a
 * reader walks a tree of its own to answer a question the declaring module has already answered —
 * and answers it against the bindings its own reading made, which are not the ones the declaring
 * reading made.
 *
 * <p>So it is published, and named as the declaration writes its fields. The two things that could
 * go wrong with a published answer are held here together: it may move when nothing was said, and
 * it may stand still when something was.
 */
class WhichOfItsOwnFieldsAClauseReadsIsTheDeclarationsAnswerTest {

    /** Two fields and two clauses, each clause reading a different one of them. */
    private static final String DECLARING = """
            module shop.prices exposing ( Range )

            data Range = { low: Int, high: Int }
                invariant low >= 0
                invariant high >= low
            """;

    /** The same, with a line written above it that says nothing. */
    private static final String WITH_A_COMMENT =
            "// what a range is\n" + DECLARING;

    /** The same, with the first clause written about the other field. */
    private static final String READING_THE_OTHER_FIELD = """
            module shop.prices exposing ( Range )

            data Range = { low: Int, high: Int }
                invariant high >= 0
                invariant high >= low
            """;

    private static final TypeKey RANGE = new TypeKey("shop.prices", "Range");

    /** A line that says nothing moves every position under it and moves none of this. */
    @Test
    void aCommentWrittenAboveItDoesNotChangeWhichFieldsAClauseReads() {
        Compilation c = started();
        List<Set<String>> before = fieldsRead(c);

        edit(c, WITH_A_COMMENT);

        assertEquals(before, fieldsRead(c),
                "which fields a clause reads is what the declaration says, and the declaration says"
                        + " the same thing");
    }

    /** And a clause written about another field reads another field. */
    @Test
    void andAClauseWrittenAboutAnotherFieldReadsIt() {
        Compilation c = started();
        assertEquals(List.of(Set.of("low"), Set.of("high", "low")), fieldsRead(c),
                "the first names one field and the second names both");

        edit(c, READING_THE_OTHER_FIELD);

        assertEquals(List.of(Set.of("high"), Set.of("high", "low")), fieldsRead(c),
                "the first clause is about the other field now");
        assertNotEquals(Set.of("low"), fieldsRead(c).getFirst(),
                "a published answer that never moves is not an answer about the declaration");
    }

    /** What each clause of {@code Range} says it reads, in the order the declaration writes them. */
    private static List<Set<String>> fieldsRead(Compilation c) {
        DeclarationMeaning meaning = c.db().ask(new Shapes.MeaningOf(RANGE)).value();
        return ((DeclarationMeaning.Product) meaning).clauses().stream()
                .map(ClauseMeaning.Stated.class::cast)
                .map(ClauseMeaning.Stated::ownFieldsRead)
                .toList();
    }

    private static void edit(Compilation c, String prices) {
        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", prices);
        c.update(edited, Set.of());
        c.answerEverything();
    }

    private static Compilation started() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", DECLARING);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with: "
                + c.db().allReports());
        return c;
    }
}
