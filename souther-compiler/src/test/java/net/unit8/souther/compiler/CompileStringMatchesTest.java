package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code String.matches(pattern, s)} — whole-string regex match (Java Pattern flavour), for
 * format-constrained values in an invariant (spec §stdlib-string). The pattern must be a string
 * literal, validated at compile time, so a malformed regex is a compile error and the call reads as
 * a plain Bool. Declared in {@code souther.string}, backed by the {@code Strings.matches} kernel.
 */
class CompileStringMatchesTest {

    // A postal code newtype constrained to NNN-NNNN by a regex invariant.
    private static final String POSTAL = """
            module demo
            data 郵便 = String invariant String.matches("[0-9]{3}-[0-9]{4}", value)
            """;

    private Decoder<Object, ?> postalDecoder() throws Exception {
        ClassLoader loader = new BytesClassLoader(Compiler.compile(POSTAL), getClass().getClassLoader());
        return Codecs.decoder(loader, "demo.郵便");
    }

    @Test
    void matchingValueDecodes() throws Exception {
        Result<?> r = postalDecoder().decode("123-4567", Path.ROOT);
        assertTrue(r instanceof Ok, "123-4567 matches the format, so it decodes");
    }

    @Test
    void nonMatchingValueIsRejected() throws Exception {
        assertTrue(postalDecoder().decode("12-345", Path.ROOT) instanceof Err, "wrong shape");
        assertTrue(postalDecoder().decode("1234567", Path.ROOT) instanceof Err, "missing hyphen");
        assertTrue(postalDecoder().decode("123-4567x", Path.ROOT) instanceof Err, "matches is anchored");
    }

    @Test
    void matchesInABehaviorReturnsBool() throws Exception {
        String src = """
                module demo
                data In = { s: String }
                data Out = Bool
                behavior check : (i: In) -> Out constructs Out
                let check (i) = Out(String.matches("[a-z]+", i.s))
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object behavior = loader.loadClass("demo.Check$Impl").getDeclaredConstructor().newInstance();
        assertEquals(true, run(loader, behavior, "abc"));
        assertEquals(false, run(loader, behavior, "abc1"));   // anchored: the whole string must match
    }

    private boolean run(BytesClassLoader loader, Object behavior, String s) throws Exception {
        Object in = Codecs.decoded(loader, "demo.In", java.util.Map.of("s", s));
        return (boolean) Codecs.encode(loader, "demo.Out", Codecs.apply(behavior, in));
    }

    // --- compile-time constant construction (ADR-0032): a constant value is checked against the
    // regex invariant at compile time ---
    private static final String CONST_POSTAL = """
            module demo
            data 郵便 = String invariant String.matches("[0-9]{3}-[0-9]{4}", value)
            behavior mk : (x: String) -> 郵便 constructs 郵便
            let mk (x) = 郵便("%s")
            """;

    @Test
    void constantConstructionCompilesWhenItMatches() {
        Compiler.compile(CONST_POSTAL.formatted("123-4567"));
    }

    @Test
    void constantConstructionIsACompileErrorWhenItDoesNotMatch() {
        assertThrows(CompileException.class, () -> Compiler.compile(CONST_POSTAL.formatted("nope")));
    }

    // --- the pattern must be a compile-time-validated literal ---
    @Test
    void aNonLiteralPatternIsRejected() {
        String src = """
                module demo
                data In = { pat: String, s: String }
                data Out = Bool
                behavior check : (i: In) -> Out constructs Out
                let check (i) = Out(String.matches(i.pat, i.s))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("literal"), ex.getMessage());
    }

    @Test
    void aMalformedRegexLiteralIsACompileError() {
        String src = """
                module demo
                data X = String invariant String.matches("[0-9", value)
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("regular expression") || ex.getMessage().contains("正規表現"),
                ex.getMessage());
    }
}
