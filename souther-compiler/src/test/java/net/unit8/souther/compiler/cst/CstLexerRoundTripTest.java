package net.unit8.souther.compiler.cst;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lossless invariant of the trivia-preserving lexer: concatenating every token's text (trivia,
 * comments, and error tokens included) reproduces the source exactly. This is the property the
 * formatter and incremental reparse rest on, so it is exercised over the real example corpus and a
 * set of tricky literals.
 */
class CstLexerRoundTripTest {

    private static String relex(String source) {
        StringBuilder sb = new StringBuilder();
        for (GreenToken t : CstLexer.lex(source).tokens()) {
            sb.append(t.text());
        }
        return sb.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "module A",
            "// just a comment\n",
            "data X = String\n    invariant length(value) > 0\n",
            "let f (x) = x |> g |> h",
            "\"a\\nb\\t\\\"c\"",              // escapes stay raw in the token text
            "500m + 1.5m - 3",
            "x -> x ++ \"!\"",
            "a >-> b >-> c",
            "match x with | A -> 1 | B as y -> 2",
            "Map<String, List<T>>",
            "  \r\n\t  leading trivia",
            "trailing trivia   \n\n",
            "unexpected # char",           // an unknown char becomes an ERROR_TOKEN, still lossless
            "\"unterminated",              // an unterminated string still covers to EOF
    })
    void relexingReproducesTheSource(String source) {
        assertEquals(source, relex(source));
    }

    static Stream<Path> exampleSources() throws IOException {
        Path examples = Path.of("..", "examples");
        if (!Files.isDirectory(examples)) {
            return Stream.empty();
        }
        try (Stream<Path> walk = Files.walk(examples)) {
            return walk.filter(p -> p.toString().endsWith(".sou")).toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("exampleSources")
    void relexingReproducesEveryExample(Path source) {
        String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(text, relex(text), "round-trip mismatch for " + source);
    }

    @Test
    void everyOffsetIsCoveredExactlyOnceInOrder() {
        String source = "let f (x) = x + 1  // tail\n";
        List<GreenToken> tokens = CstLexer.lex(source).tokens();
        int at = 0;
        for (GreenToken t : tokens) {
            assertEquals(source.substring(at, at + t.width()), t.text());
            at += t.width();
        }
        assertEquals(source.length(), at);
        assertTrue(tokens.get(tokens.size() - 1).kind() == SyntaxKind.EOF);
    }
}
