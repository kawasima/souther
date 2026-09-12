package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No value reaches one declaration by two of the paths its spreads make.
 *
 * <p>A rule of the language, written down here because a reading of rules is built on it. The walk
 * over what governs a value remembers what it has worked out about a declaration and does not walk
 * it again ({@link PublishedRules}); whether that could ever be the same clause arriving by two
 * paths, and so a clause a caller might count once where its author wrote it once, is settled by
 * this and not by the walk.
 *
 * <p>What settles it is the fields. A spread brings the fields of what it spreads, and a declaration
 * a value reaches twice brings its fields twice — which is a field written twice, and refused. So
 * the paths never meet, and there is no clause arriving by two of them.
 *
 * <p><b>Held as a check rather than said in a comment.</b> The walk's remembering is right either
 * way today; what turns on this is whether it has a question to answer at all. Written as prose
 * beside the walk, a language that later let two spreads bring one declaration in — merging the
 * fields, or naming them apart — would leave the sentence standing and the question unasked. Here it
 * comes back as a red line, in the place that says what to do about it.
 */
class TwoSpreadsThatMeetOneDeclarationAreRefusedTest {

    /** Two declarations spread one, and a third spreads both — the shape that would make a clause
     *  arrive twice if it were admitted. */
    private static final String THE_PATHS_MEET = """
            module demo exposing ( Held, Left, Right, Both )

            data Held = { n: Int }
                invariant n >= 1

            data Left = { ...Held, l: Bool }

            data Right = { ...Held, r: Bool }

            data Both = { ...Left, ...Right }
            """;

    /** And the same declaration spread twice by one, which is the shape said in one line. */
    private static final String THE_SAME_ONE_TWICE = """
            module demo exposing ( Held, Both )

            data Held = { n: Int }
                invariant n >= 1

            data Both = { ...Held, ...Held }
            """;

    @Test
    void aValueThatWouldReachOneDeclarationByTwoPathsIsRefused() {
        assertRefused(THE_PATHS_MEET);
        assertRefused(THE_SAME_ONE_TWICE);
    }

    /**
     * And a chain compiles, which is what says the refusal above is about the paths meeting.
     *
     * <p>Without it the lines above are met by a language that refuses every spread, and the walk
     * they are written for would have nothing to walk.
     */
    @Test
    void andOneThatReachesItByOnePathIsNot() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", """
                module demo exposing ( Held, Left, Both )

                data Held = { n: Int }
                    invariant n >= 1

                data Left = { ...Held, l: Bool }

                data Both = { ...Left, b: Bool }
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();

        assertEquals(List.of(), c.db().allReports().stream().map(Object::toString).toList(),
                "a declaration reached down one chain of spreads is written as it reads");
    }

    /** That {@code source} is refused, and refused for the field the second path brings. */
    private static void assertRefused(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", source);
        String said = whatItSays(byId);

        assertTrue(said.contains("conflicts with"),
                () -> "a value that reaches one declaration twice is told so, and about the field"
                        + " the second path brought: " + said);
    }

    /** What compiling says, whether it is reported or raised — the refusal arrives either way, and
     *  which way is not what this is about. */
    private static String whatItSays(Map<String, String> byId) {
        try {
            Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
            c.answerEverything();
            return c.db().allReports().toString();
        } catch (RuntimeException e) {
            return String.valueOf(e.getMessage());
        }
    }
}
