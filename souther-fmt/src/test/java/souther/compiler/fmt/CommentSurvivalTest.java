package souther.compiler.fmt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A comment survives formatting wherever it is written. Comment handling used to be per construct
 * and only three constructs had it, so a comment disappeared on the first {@code souther fmt} in
 * eight of the nine places one can be written — silently, which is worse than a rejection.
 *
 * <p>The constructs are listed rather than sampled, so adding one without handling its comments
 * fails here. Each case also asserts the comment is written exactly once: it is reachable both from
 * the parent's child list and from the front of the member's own subtree, and the two overlap.
 */
class CommentSurvivalTest {

    static Stream<Arguments> everyPlaceACommentCanGo() {
        return Stream.of(
                Arguments.of("top-level item", """
                        module m
                        // the comment
                        data D = { a: Int }
                        """),
                Arguments.of("data field", """
                        module m
                        data D = {
                          // the comment
                          a: Int
                        }
                        """),
                Arguments.of("sum case", """
                        module m
                        data A
                        data B
                        data S =
                          // the comment
                          A | B
                        """),
                Arguments.of("behavior parameter", """
                        module m
                        data O = { n: Int }
                        behavior f : (
                          // the comment
                          n: Int) -> O constructs O
                        let f (n) = O { n = n }
                        """),
                Arguments.of("block statement", """
                        module m
                        data O = { n: Int }
                        behavior f : (n: Int) -> O constructs O
                        let f (n) = {
                          // the comment
                          let m = n * 2
                          O { n = m }
                        }
                        """),
                Arguments.of("record literal field", """
                        module m
                        data O = { a: Int, b: Int }
                        behavior f : (n: Int) -> O constructs O
                        let f (n) = O {
                          a = n,
                          // the comment
                          b = n
                        }
                        """),
                Arguments.of("list element", """
                        module m
                        data O = { xs: List<Int> }
                        behavior f : (n: Int) -> O constructs O
                        let f (n) = O { xs = [
                          // the comment
                          1, 2] }
                        """),
                Arguments.of("match arm", """
                        module m
                        data A
                        data B
                        data S = A | B
                        data O = { n: Int }
                        behavior f : (s: S) -> O constructs O
                        let f (s) = O { n = match s with
                          // the comment
                          | A -> 1
                          | B -> 2 }
                        """),
                Arguments.of("example row", """
                        module m
                        data O = { n: Int }
                        behavior f : (n: Int) -> O constructs O
                        let f (n) = O { n = n }
                        example f
                          // the comment
                          | "a" : (1) -> O { n = 1 }
                        """),
                // What `souther examples --generate` writes: rows whose answers are owed, with the
                // prose about them written as comments between them. The rows and the comments are
                // new together, so what this holds is the pair — a row that keeps its mark under a
                // comment that moved is a block an author cannot read.
                Arguments.of("a row whose answer is owed", """
                        module m
                        data O = { n: Int }
                        behavior f : (n: Int) -> O constructs O
                        let f (n) = O { n = n }
                        example f
                          // the comment
                          | "a" : (1) -> <?>
                          // fills something
                          | "b" : (2) -> <?>
                        """),
                Arguments.of("exposing entry", """
                        module m exposing (
                          // the comment
                          O )
                        data O = { n: Int }
                        """),
                Arguments.of("call argument", """
                        module m
                        data O = { n: Int }
                        behavior f : (n: Int) -> O constructs O
                        let g (x: Int) = x
                        let f (n) = O { n = g(
                          // the comment
                          n) }
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPlaceACommentCanGo")
    void theCommentSurvives(String where, String source) {
        String formatted = Formatter.format(source);

        assertTrue(formatted.contains("// the comment"),
                "the comment written at a " + where + " was dropped:\n" + formatted);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPlaceACommentCanGo")
    void theCommentIsWrittenOnce(String where, String source) {
        String formatted = Formatter.format(source);

        assertEquals(1, occurrences(formatted, "// the comment"),
                "the comment at a " + where + " is reachable two ways and must be written once:\n"
                        + formatted);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPlaceACommentCanGo")
    void formattingIsStable(String where, String source) {
        String once = Formatter.format(source);

        assertEquals(once, Formatter.format(once),
                "a second format changed the " + where + " case");
    }

    /**
     * A row whose answer is owed comes back written the way it went in.
     *
     * <p>Beside the cases above rather than among them, because what those hold is the comment and
     * this holds the row. The mark is one token and the formatter writes it back as one: split into
     * {@code <}, {@code ?} and {@code >}, it would be laid out as three operators and the row would
     * come out of a {@code fmt} run saying something the parser refuses.
     */
    @Test
    void aRowWhoseAnswerIsOwedKeepsItsMark() {
        String source = """
                module m
                data O = { n: Int }
                behavior f : (n: Int) -> O constructs O
                let f (n) = O { n = n }
                example f
                    | "a" : (1) -> <?>
                """;

        String formatted = Formatter.format(source);

        assertTrue(formatted.contains("-> <?>"),
                "the mark is written back where the answer goes:\n" + formatted);
        assertEquals(formatted, Formatter.format(formatted), "and a second format leaves it alone");
    }

    /** Two comment lines above one member stay in that order — building them one at a time reverses
     *  them, and a reversal shows only on the second format. */
    @Test
    void twoLinesKeepTheirOrder() {
        String formatted = Formatter.format("""
                module m
                data O = { a: Int, b: Int }
                behavior f : (n: Int) -> O constructs O
                let f (n) = O {
                  a = n,
                  // first line
                  // second line
                  b = n
                }
                """);

        assertTrue(formatted.indexOf("// first line") < formatted.indexOf("// second line"), formatted);
        assertEquals(formatted, Formatter.format(formatted));
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
