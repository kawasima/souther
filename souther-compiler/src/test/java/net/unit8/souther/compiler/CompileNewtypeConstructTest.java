package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import net.unit8.souther.runtime.ConstraintViolation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A newtype name applied to one argument constructs the wrapper: {@code 金額(500)} — the type name
 * in call position is its constructor. A constant argument is checked against the invariant at
 * compile time, so {@code 金額(-5)} is a compile error rather than a runtime abort. A runtime
 * argument goes through the invariant-checking {@code __construct} wherever it is written — the
 * behavior's result, a non-tail {@code let}, or a {@code match} arm — holding when valid and
 * aborting on violation. A newtype with no invariant wraps any value, constant or runtime.
 */
class CompileNewtypeConstructTest {

    private static final String MODULE = """
            module demo
            import String ( length )
            data 金額 = Int
                invariant value >= 0
            data 会員ID = String
                invariant length(value) > 0
            data Tag = String

            behavior makeMoney : (x: Int) -> 金額 constructs 金額
            let makeMoney (x) = 金額(500)

            behavior makeId : (x: Int) -> 会員ID constructs 会員ID
            let makeId (x) = 会員ID("m-01")

            behavior nonTail : (x: Int) -> 金額 constructs 金額
            let nonTail (x) = {
                let m = 金額(300)
                m
            }

            behavior rewrap : (t: Tag) -> Tag constructs Tag
            let rewrap (t) = Tag(t.value)

            behavior halve : (m: 金額) -> 金額 constructs 金額
            let halve (m) = 金額(m.value - 100)
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object apply(BytesClassLoader loader, String cls, Object in) throws Exception {
        Object b = loader.loadClass("demo." + cls + "$Impl").getConstructor().newInstance();
        return Codecs.apply(b, in);
    }

    private Object encode(BytesClassLoader loader, String type, Object v) throws Exception {
        return Codecs.encode(loader, "demo." + type, v);
    }

    @Test
    void constantConstructionEncodesToItsPayload() throws Exception {
        BytesClassLoader loader = loader();
        Object money = apply(loader, "MakeMoney", 0L);
        assertEquals("demo.金額", money.getClass().getName());
        assertEquals(500L, encode(loader, "金額", money));
    }

    @Test
    void stringNewtypeConstantConstruction() throws Exception {
        BytesClassLoader loader = loader();
        Object id = apply(loader, "MakeId", 0L);
        assertEquals("m-01", encode(loader, "会員ID", id));
    }

    @Test
    void constantConstructionIsLegalInANonTailLet() throws Exception {
        BytesClassLoader loader = loader();
        Object money = apply(loader, "NonTail", 0L);
        assertEquals(300L, encode(loader, "金額", money));
    }

    @Test
    void constantWithAContainsInvariantIsCheckedInTheRightArgumentOrder() throws Exception {
        // ConstEval runs `contains("@", value)` at compile time; the string searched is the last
        // argument (spec §pipe). A constant that satisfies the invariant compiles and encodes.
        String ok = """
                module demo
                import String ( contains )
                data Email = String
                    invariant contains("@", value)
                behavior make : (x: Int) -> Email constructs Email
                let make (x) = Email("a@b.com")
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(ok), getClass().getClassLoader());
        assertEquals("a@b.com", encode(loader, "Email", apply(loader, "Make", 0L)));

        // and a constant that does not contain "@" is rejected at compile time (not "@".contains(...))
        String bad = ok.replace("Email(\"a@b.com\")", "Email(\"nope\")");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(bad));
        assertTrue(e.getMessage().contains("Email"), e.getMessage());
    }

    @Test
    void noInvariantNewtypeWrapsARuntimeValue() throws Exception {
        BytesClassLoader loader = loader();
        Object tag = Codecs.decoded(loader, "demo.Tag", "hello");
        Object out = apply(loader, "Rewrap", tag);
        assertEquals("hello", encode(loader, "Tag", out));
    }

    @Test
    void constantViolatingTheInvariantIsACompileError() {
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                behavior make : (x: Int) -> 金額 constructs 金額
                let make (x) = 金額(-5)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("金額"), e.getMessage());
    }

    @Test
    void emptyStringViolatingLengthInvariantIsACompileError() {
        String src = """
                module demo
                import String ( length )
                data 会員ID = String
                    invariant length(value) > 0
                behavior make : (x: Int) -> 会員ID constructs 会員ID
                let make (x) = 会員ID("")
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    @Test
    void runtimeConstructionInTailConstructsAndAbortsOnViolation() throws Exception {
        BytesClassLoader loader = loader();
        // 500 - 100 = 400 >= 0: the runtime construction holds and comes back
        Object big = Codecs.decoded(loader, "demo.金額", 500L);
        assertEquals(400L, encode(loader, "金額", apply(loader, "Halve", big)));
        // 50 - 100 = -50 breaks value >= 0: the tail construction goes through __construct and aborts
        Object small = Codecs.decoded(loader, "demo.金額", 50L);
        assertThrows(ConstraintViolation.class, () -> apply(loader, "Halve", small));
    }

    @Test
    void aHelperCallingInvariantIsInlinedAndCtfeChecksIt() {
        // an invariant may name a rule with a `let`; it is inlined before checking and emission, so
        // it type-checks and CTFE runs the real compiled rule against a constant argument
        String src = """
                module demo
                data 金額 = Int
                    invariant 正の数(value)
                let 正の数 (v: Int) = v >= 0
                behavior make : (x: Int) -> 金額 constructs 金額
                let make (x) = 金額(-5)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("金額"), e.getMessage());
        // the same rule holds for 500, so it compiles
        Compiler.compile(src.replace("金額(-5)", "金額(500)"));
    }

    @Test
    void aConstantConstructionInsideALambdaIsStillCtfeChecked() {
        // a violating constant newtype construction inside a lambda body must not slip past CTFE —
        // the collector traverses lambda/block bodies (forEachChild covers Ast.Block)
        String src = """
                module demo
                import List ( map )
                data 金額 = Int
                    invariant value >= 0
                data 袋 = { xs: List<金額> }
                behavior make : (raw: List<Int>) -> 袋 constructs 金額, 袋
                let make (raw) = 袋 { xs = map(raw, x -> 金額(-5)) }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    @Test
    void theRecordFormOfANewtypeConstructionGetsTheSameVerdict() {
        // 金額 { value = -5 } is the record spelling of 金額(-5); after the desugar both are one
        // NewData, so the record form is CTFE-checked too (previously it aborted only at run time)
        String violate = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                behavior make : (x: Int) -> 金額 constructs 金額
                let make (x) = 金額 { value = -5 }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(violate));
        // and the holding record form is legal in a non-tail let, like the call form
        Compiler.compile(violate.replace("金額 { value = -5 }", "{\n    let m = 金額 { value = 5 }\n    m\n}"));
    }

    @Test
    void runtimeConstructionInANonTailLetConstructsAndAbortsOnViolation() throws Exception {
        // a runtime-argument invariant construction is no longer restricted to tail position: bound in
        // a non-tail let it still goes through __construct — holding when valid, aborting on violation
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                behavior bad : (m: 金額) -> 金額 constructs 金額
                let bad (m) = {
                    let y = 金額(m.value - 100)
                    y
                }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object big = Codecs.decoded(loader, "demo.金額", 500L);
        assertEquals(400L, encode(loader, "金額", apply(loader, "Bad", big)));
        Object small = Codecs.decoded(loader, "demo.金額", 50L);
        assertThrows(ConstraintViolation.class, () -> apply(loader, "Bad", small));
    }

    @Test
    void runtimeConstructionInAMatchArmConstructsAndAbortsOnViolation() throws Exception {
        // a match arm is not tail, yet an invariant-bearing construction inside one now goes through
        // __construct: it holds for a valid runtime value and aborts on a violation
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                data Add = { n: Int }
                data Sub = { n: Int }
                data Op = Add | Sub
                behavior run : (op: Op) -> 金額 constructs 金額
                let run (op) =
                    match op with
                        | Add as a -> 金額(a.n)
                        | Sub as s -> 金額(s.n)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object add5 = Codecs.decoded(loader, "demo.Op", Map.of("type", "Add", "n", 5L));
        assertEquals(5L, encode(loader, "金額", apply(loader, "Run", add5)));
        Object addNeg = Codecs.decoded(loader, "demo.Op", Map.of("type", "Add", "n", -5L));
        assertThrows(ConstraintViolation.class, () -> apply(loader, "Run", addNeg));
    }

    @Test
    void runtimeProductConstructionInAMatchArmConstructsAndAbortsOnViolation() throws Exception {
        // the value-position path keys on isInvariantBearing, which also covers a multi-field product:
        // 値引き済み { 額 = ... } built in a match arm holds for a valid value and aborts on a violation
        String src = """
                module demo
                data 値引き済み = { 額: Int }
                    invariant 額 >= 0
                data Add = { n: Int }
                data Sub = { n: Int }
                data Cmd = Add | Sub
                behavior run : (c: Cmd) -> 値引き済み constructs 値引き済み
                let run (c) =
                    match c with
                        | Add as a -> 値引き済み { 額 = a.n }
                        | Sub as s -> 値引き済み { 額 = s.n }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object add5 = Codecs.decoded(loader, "demo.Cmd", Map.of("type", "Add", "n", 5L));
        Map<?, ?> encoded = (Map<?, ?>) encode(loader, "値引き済み", apply(loader, "Run", add5));
        assertEquals(5L, encoded.get("額"));
        Object addNeg = Codecs.decoded(loader, "demo.Cmd", Map.of("type", "Add", "n", -5L));
        assertThrows(ConstraintViolation.class, () -> apply(loader, "Run", addNeg));
    }
}
