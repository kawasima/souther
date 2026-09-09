package souther.compiler.cst;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import souther.test.RepositoryLayout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parser's lossless invariant: the red tree's text reproduces the source, and every real source
 * parses with no syntax errors. This is the corpus the CST→AST lowering and the formatter are built
 * against.
 */
class CstParserRoundTripTest {

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /** The bundled prelude — the hardest corpus, and asked for rather than gone looking for, so a
     *  source added to it is swept here without this being edited and a corpus that is not there
     *  refuses rather than leaving a sweep of nothing to pass. */
    static Stream<Path> exampleSources() {
        return REPOSITORY.preludeSources().stream();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("exampleSources")
    void treeReproducesTheSource(Path source) {
        String text = read(source);
        CstParser.Result result = CstParser.parse(text);
        assertEquals(text, result.root().text(), "round-trip mismatch for " + source);
    }

    @ParameterizedTest
    @MethodSource("exampleSources")
    void everyExampleParsesWithoutSyntaxErrors(Path source) {
        String text = read(source);
        CstParser.Result result = CstParser.parse(text);
        assertTrue(result.errors().isEmpty(),
                "unexpected syntax errors in " + source + ": " + result.errors());
    }

    @Test
    void aNestedNewtypeDestructuringPatternRoundTrips() {
        String src = """
                module A
                let f (m) =
                    match m with
                        | アクティベート済み(メールアドレス(s)) -> s
                        | 未アクティベート(a) -> a.value
                """;
        CstParser.Result result = CstParser.parse(src);
        assertEquals(src, result.root().text());
        assertTrue(result.errors().isEmpty(), "unexpected syntax errors: " + result.errors());
    }

    @Test
    void aBareFieldGetterRoundTrips() {
        String src = """
                module A
                let f (xs) = List.map(.value, xs)
                """;
        CstParser.Result result = CstParser.parse(src);
        assertEquals(src, result.root().text());
        assertTrue(result.errors().isEmpty(), "unexpected syntax errors: " + result.errors());
    }

    @Test
    void aBrokenBufferStillRoundTripsAndDoesNotThrow() {
        String broken = "module A\ndata = = {{{ \nlet f ( = \nbehavior x";
        CstParser.Result result = CstParser.parse(broken);
        assertEquals(broken, result.root().text());
        assertTrue(!result.errors().isEmpty(), "a broken buffer should record errors");
    }
}
