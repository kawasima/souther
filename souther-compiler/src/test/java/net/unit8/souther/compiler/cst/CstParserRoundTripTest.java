package net.unit8.souther.compiler.cst;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parser's lossless invariant: the red tree's text reproduces the source, and every real
 * example parses with no syntax errors. This is the corpus the CST→AST lowering and the formatter
 * are built against.
 */
class CstParserRoundTripTest {

    static Stream<Path> exampleSources() throws IOException {
        List<Path> roots = List.of(Path.of("..", "examples"),
                Path.of("src", "main", "resources", "souther"));   // the bundled prelude — the hardest corpus
        List<Path> sources = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".sou")).forEach(sources::add);
            }
        }
        return sources.stream();
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
    void aBrokenBufferStillRoundTripsAndDoesNotThrow() {
        String broken = "module A\ndata = = {{{ \nlet f ( = \nbehavior x";
        CstParser.Result result = CstParser.parse(broken);
        assertEquals(broken, result.root().text());
        assertTrue(!result.errors().isEmpty(), "a broken buffer should record errors");
    }
}
