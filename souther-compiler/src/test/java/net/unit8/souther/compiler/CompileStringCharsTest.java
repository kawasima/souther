package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Character access without a {@code Char} type (Issue #52): a character is a length-1 {@code String}.
 * {@code String.toChars} lists them (Elm {@code String.toList}), {@code String.toCode} is the code
 * point (Elm {@code Char.toCode}), and {@code String.toInt : Int | NotANumber} parses (Elm
 * {@code String.toInt}'s {@code Maybe} as a named case). Together with {@code String.matches} (#51)
 * and {@code Int.modBy} (#50) these turn a checksum into a plain fold in a behavior — no digit table,
 * no {@code partial} index recursion.
 */
class CompileStringCharsTest {

    private long runInt(String module, String behavior, Object input) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In", input);
        String cls = Character.toUpperCase(behavior.charAt(0)) + behavior.substring(1);
        Object b = loader.loadClass("demo." + cls + "$Impl").getDeclaredConstructor().newInstance();
        return (long) Codecs.encode(loader, "demo.Out", Codecs.apply(b, in));
    }

    @Test
    void toCharsListsCharactersAndToCodeReadsTheCodePoint() throws Exception {
        // 桁和: sum of digit values via a fold over the characters. digit = toCode(ch) - toCode("0").
        String src = """
                module demo
                import List ( fold )
                data In = { s: String }
                data Out = Int
                behavior calc : (i: In) -> Out constructs Out
                let 桁 (ch: String) = String.toCode(ch) - String.toCode("0")
                let calc (i) = Out(fold((acc, ch) -> acc + 桁(ch), 0, String.toChars(i.s)))
                """;
        assertEquals(12L, runInt(src, "calc", Map.of("s", "129")));   // 1+2+9
        assertEquals(0L, runInt(src, "calc", Map.of("s", "")));       // no characters
    }

    @Test
    void toCharsHandlesMultiByteCodePoints() throws Exception {
        String src = """
                module demo
                data In = { s: String }
                data Out = Int
                behavior calc : (i: In) -> Out constructs Out
                let calc (i) = Out(List.length(String.toChars(i.s)))
                """;
        assertEquals(3L, runInt(src, "calc", Map.of("s", "a1z")));
        assertEquals(2L, runInt(src, "calc", Map.of("s", "🍎x")));   // one code point + one
    }

    @Test
    void toIntParsesOrTakesTheNotANumberCase() throws Exception {
        String src = """
                module demo
                data In = { s: String }
                data Out = Int
                behavior parse : (i: In) -> Out constructs Out
                let parse (i) = match String.toInt(i.s) with
                    | Int as n -> Out(n)
                    | NotANumber -> Out(-1)
                """;
        assertEquals(42L, runInt(src, "parse", Map.of("s", "42")));
        assertEquals(7L, runInt(src, "parse", Map.of("s", "007")));   // leading zeros kept
        assertEquals(-5L, runInt(src, "parse", Map.of("s", "-5")));   // a leading sign parses
        assertEquals(-1L, runInt(src, "parse", Map.of("s", "12x")));  // NotANumber
        assertEquals(-1L, runInt(src, "parse", Map.of("s", "")));     // NotANumber
        assertEquals(-1L, runInt(src, "parse", Map.of("s", " 5")));   // surrounding space: NotANumber
        assertEquals(-1L, runInt(src, "parse", Map.of("s", "99999999999999999999")));  // > Int64: NotANumber
    }

    @Test
    void toCodeOfTheEmptyStringIsMinusOne() throws Exception {
        String src = """
                module demo
                data In = { s: String }
                data Out = Int
                behavior calc : (i: In) -> Out constructs Out
                let calc (i) = Out(String.toCode(i.s))
                """;
        assertEquals(-1L, runInt(src, "calc", Map.of("s", "")));    // no first character
        assertEquals(48L, runInt(src, "calc", Map.of("s", "0")));   // '0' is code point 48
    }

    // The Issue #52 payoff: a mod-10 check digit as a plain fold in a behavior — the invariant proves
    // all-digits (#51), so the digit value is total; no digit table, no `partial` recursion.
    private static final String CHECKSUM = """
            module demo
            import List ( fold )
            data 符号 = String invariant String.matches("[0-9]+", value)
            data 妥当
            data 不正
            behavior 検証 : (s: 符号) -> 妥当 | 不正 constructs 妥当, 不正
            let 桁和 (s: String) = fold((acc, ch) -> acc + (String.toCode(ch) - String.toCode("0")), 0, String.toChars(s))
            let 検証 (s) = {
                require Int.modBy(10, 桁和(s.value)) == 0 else 不正
                妥当
            }
            """;

    @Test
    void checksumValidatesInABehaviorWithNoBoilerplate() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(CHECKSUM), getClass().getClassLoader());
        Object behavior = loader.loadClass("demo.検証$Impl").getDeclaredConstructor().newInstance();
        // "12340" digit-sum 10 ≡ 0 mod 10 → 妥当; "12345" sum 15 → 不正.
        Object ok = Codecs.apply(behavior, Codecs.decoded(loader, "demo.符号", "12340"));
        Object bad = Codecs.apply(behavior, Codecs.decoded(loader, "demo.符号", "12345"));
        assertEquals("demo.妥当", ok.getClass().getName());
        assertEquals("demo.不正", bad.getClass().getName());
    }

    @Test
    void toCharsFoldWorksWithoutImportViaQualifiedList() throws Exception {
        // Same fold, calling List.fold qualified (no import) — the seam works either way.
        String src = """
                module demo
                data In = { s: String }
                data Out = Int
                behavior calc : (i: In) -> Out constructs Out
                let calc (i) = Out(List.fold((acc, ch) -> acc + String.toCode(ch), 0, String.toChars(i.s)))
                """;
        assertEquals(49L + 50L, runInt(src, "calc", Map.of("s", "12")));   // '1'=49, '2'=50
    }
}
