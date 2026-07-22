package net.unit8.souther.compiler.fmt;

import net.unit8.souther.compiler.cst.CstLexer;
import net.unit8.souther.compiler.cst.CstParser;
import net.unit8.souther.compiler.cst.GreenToken;
import net.unit8.souther.compiler.cst.SyntaxKind;
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
 * The formatter's contract: it is idempotent, it changes only whitespace (the non-trivia token
 * stream and the comments survive), and its output re-parses without error. Verified over the real
 * example and prelude corpus, plus a pinned snapshot of the canonical form.
 */
class FormatterTest {

    static Stream<Path> corpus() throws IOException {
        List<Path> roots = List.of(Path.of("..", "examples"),
                Path.of("src", "main", "resources", "souther"));
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

    /** The non-trivia token stream (kind + text), which must be identical before and after — the
     * proof that formatting rewrote only whitespace, never code. */
    private static List<String> code(String source) {
        List<String> out = new ArrayList<>();
        for (GreenToken t : CstLexer.lex(source).tokens()) {
            if (!t.kind().isTrivia() && t.kind() != SyntaxKind.EOF) {
                out.add(t.kind() + ":" + t.text());
            }
        }
        return out;
    }

    private static List<String> comments(String source) {
        List<String> out = new ArrayList<>();
        for (GreenToken t : CstLexer.lex(source).tokens()) {
            if (t.kind() == SyntaxKind.LINE_COMMENT) {
                out.add(t.text().stripTrailing());
            }
        }
        return out;
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void formattingPreservesEveryTokenOfCode(Path source) {
        String text = read(source);
        assertEquals(code(text), code(Formatter.format(text)),
                "the code token stream changed for " + source);
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void formattingPreservesEveryComment(Path source) {
        String text = read(source);
        assertEquals(comments(text), comments(Formatter.format(text)),
                "a comment was lost or reordered for " + source);
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void formattingIsIdempotent(Path source) {
        String once = Formatter.format(read(source));
        assertEquals(once, Formatter.format(once), "formatting is not idempotent for " + source);
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void formattedOutputReParsesWithoutErrors(Path source) {
        String formatted = Formatter.format(read(source));
        assertTrue(CstParser.parse(formatted).errors().isEmpty(),
                "formatted output does not re-parse for " + source + ":\n" + formatted);
    }

    @Test
    void canonicalFormOfASmallModule() {
        String messy = "module demo\n\n\ndata Id=String\n  invariant length(value)>0\n"
                + "data M={ id:Id ,name:String }\n"
                + "behavior f:(x:M)->M constructs M\n"
                + "let f (x)=M{id=x.id,name=x.name}\n";
        String expected = """
                module demo

                data Id = String
                    invariant length(value) > 0

                data M =
                    { id: Id
                    , name: String
                    }

                behavior f : (x: M) -> M
                    constructs M

                let f (x) = M { id = x.id, name = x.name }
                """;
        assertEquals(expected, Formatter.format(messy));
    }

    @Test
    void formattingKeepsThePartialModifier() {
        // `partial` is a helper modifier; dropping it flips the helper from opted-out to
        // totality-checked, which changes program meaning. The formatter must emit it.
        String source = "module demo\n"
                + "data N = Int\n"
                + "data Out = Int\n"
                + "partial let spin (n: Int): Int = spin(n)\n"
                + "behavior run : (n: N) -> Out constructs Out\n"
                + "let run (n) = Out(spin(n.value))\n";
        String formatted = Formatter.format(source);
        assertEquals(code(source), code(formatted), "the code token stream changed");
        assertTrue(formatted.contains("partial let spin"), "formatter dropped `partial`:\n" + formatted);
        assertTrue(CstParser.parse(formatted).errors().isEmpty(), "formatted output does not re-parse");
    }

    @Test
    void canonicalFormOfAnExample() {
        String messy = "module demo\n"
                + "data M={ n:Int }\n"
                + "behavior f:(x:M)->M constructs M\n"
                + "let f (x)=x\n"
                + "example f\n"
                + "| \"holds\":(M{n=1})->M{n=1}\n";
        String expected = """
                module demo

                data M =
                    { n: Int
                    }

                behavior f : (x: M) -> M
                    constructs M

                let f (x) = x

                example f
                    | "holds" : (M { n = 1 }) -> M { n = 1 }
                """;
        assertEquals(expected, Formatter.format(messy));
    }

    @Test
    void canonicalFormOfAFakeAndWith() {
        String messy = "module demo\n"
                + "data R={ x:Int }\n"
                + "behavior clock:()->String\n"
                + "behavior f:(r:R)->R requires clock constructs R\n"
                + "let f (r,clock)=r\n"
                + "fake lookup\n|(R{x=1})->R{x=2}\n| _ ->R{x=0}\n"
                + "example f\n|(R{x=1}) with clock=\"t\" ->R{x=1}\n";
        String expected = """
                module demo

                data R =
                    { x: Int
                    }

                behavior clock : () -> String

                behavior f : (r: R) -> R
                    requires clock
                    constructs R

                let f (r, clock) = r

                fake lookup
                    | (R { x = 1 }) -> R { x = 2 }
                    | _ -> R { x = 0 }

                example f
                    | (R { x = 1 }) with clock = "t" -> R { x = 1 }
                """;
        assertEquals(expected, Formatter.format(messy));
    }

    @Test
    void canonicalFormOfAnAttachedExampleFile() {
        String messy = "examples for demo\nexample f\n|(M{n=1})->M{n=1}\n";
        String expected = """
                examples for demo

                example f
                    | (M { n = 1 }) -> M { n = 1 }
                """;
        assertEquals(expected, Formatter.format(messy));
    }
}
