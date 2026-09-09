package souther.compiler.fmt;

import souther.compiler.cst.CstLexer;
import souther.compiler.cst.CstParser;
import souther.compiler.cst.GreenToken;
import souther.compiler.cst.SyntaxKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import souther.test.RepositoryLayout;

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
 * The formatter's contract: it is idempotent, its output re-parses without error, the comments
 * survive, and the non-trivia token stream survives — over this corpus. The last of those does not
 * hold of the language: the canonical form removes a trailing comma, writes the bar in front of a
 * match's first arm, and moves a definition's lambda parameters to the left of its `=`. See
 * {@link ACanonicalFormCanRewriteCodeTokensTest}, which is where those are written down.
 * Verified over the real example and prelude corpus, plus a pinned snapshot of the canonical form.
 */
class FormatterTest {

    /** The bundled prelude, asked for rather than found: a corpus that is not there refuses where
     *  it is handed out, and does so the same way for every check that sweeps it. */
    static Stream<Path> corpus() {
        return RepositoryLayout.ofWorkingDirectory().preludeSources().stream();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The non-trivia token stream (kind + text), identical before and after for every source here:
     * none of them writes a trailing comma, an unbarred first match arm, or a lambda on the right of
     * a definition, which are what the canonical form rewrites. */
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

    /**
     * An argument list applies to the expression before it, and that expression may be anything.
     * Reassembling the callee from the identifiers under the node would print `adder(1)(x)` as
     * `adder(1, x)` and a parenthesised choice as nothing at all.
     */
    @Test
    void anAppliedExpressionSurvivesFormatting() {
        String src = """
                module demo
                let adder (n: Int): (Int) -> Int = (x) -> x + n
                let pick (flag: Bool): (String) -> String =
                    if flag then String.trim else String.uppercase
                data In = { n: Int, s: String, flag: Bool }
                data Out = { m: Int, t: String }
                behavior go : (i: In) -> Out constructs Out
                let go (i) = Out {
                    m = adder(1)(i.n),
                    t = (if i.flag then String.trim else String.uppercase)(i.s)
                }
                """;
        String formatted = Formatter.format(src);
        assertEquals(code(src), code(formatted), "the applied expression was lost:\n" + formatted);
        assertEquals(formatted, Formatter.format(formatted), "formatting is not idempotent here");
    }

    /**
     * A qualified callee is a field read the argument list is written after, so the formatter prints
     * the callee as the expression it is. Nothing reassembles a name from the identifier tokens
     * under the node — there is no node left that holds them (issue #274).
     */
    @Test
    void aQualifiedCalleeSurvivesFormatting() {
        String src = """
                module demo
                data In = { s: String, ns: List<Int> }
                data Out = { t: String, m: Int }
                behavior go : (i: In) -> Out constructs Out
                let go (i) = Out {
                    t = String.trim(i.s),
                    m = List.length(List.map((n) -> n + 1, i.ns))
                }
                """;
        String formatted = Formatter.format(src);
        assertEquals(code(src), code(formatted), "the qualified callee was lost:\n" + formatted);
        assertEquals(formatted, Formatter.format(formatted), "formatting is not idempotent here");
    }

    /**
     * The formatter never puts an argument list at the start of a line: there it would be a
     * parenthesised expression, and a block's tuple result would be read as applying the line above.
     */
    @Test
    void anArgumentListStaysOnItsCalleesLine() {
        String src = """
                module demo
                let widen (a: Int, bbbbbbbbbbbbbbbbbb: Int, cccccccccccccccccc: Int): (Int, Int) = {
                    let total = Int.add(a, Int.add(bbbbbbbbbbbbbbbbbb, cccccccccccccccccc))
                    (total, total)
                }
                """;
        String formatted = Formatter.format(src);
        for (String line : formatted.lines().toList()) {
            assertTrue(!line.strip().startsWith("(") || !line.strip().endsWith(")")
                            || line.strip().contains(","),
                    "an argument list opened a line:\n" + formatted);
        }
        assertEquals(code(src), code(formatted), "tokens changed:\n" + formatted);
    }

    /** An attempted construction's `as x` is code, not whitespace: dropping it would silently turn a
     * bound success branch into one with an unknown name, or a departure into a plain condition. */
    @Test
    void anAttemptedConstructionKeepsItsBinder() {
        String src = """
                module demo
                data Code = String
                    invariant String.matches("[A-Z]{2}", value)
                data Kept = { code: Code }
                data Skipped
                behavior take : (raw: String) -> Kept | Skipped
                    constructs Kept, Code, Skipped
                let take (raw) = {
                    guard Code(raw) as c else Skipped
                    Kept { code = c }
                }
                let accept (raw: String): List<Code> =
                    if Code(raw) as c then [ c ] else []
                data Blocked = { reasons: List<Code> }
                    invariant List.length(reasons) >= 1
                let report (cs: List<Code>): List<Blocked> =
                    if Blocked { reasons = cs } as b then [ b ] else []
                """;
        assertEquals(code(src), code(Formatter.format(src)),
                "the `as` binder was lost:\n" + Formatter.format(src));
    }

    /**
     * A clause's name and the arms that answer it are code. Written back without them, a departure per
     * rule would come back as a departure by any rule — and the arms' cases would be lost outright.
     */
    @Test
    void canonicalFormOfNamedClausesAndTheirDepartures() {
        String messy = "module demo\n"
                + "data NoItems\ndata Duplicate\ndata Accepted={ items:Items }\n"
                + "data Items=List<Int>\n"
                + "  invariant nonEmpty=List.length(value)>=1\n"
                + "  invariant unique=List.allDistinctBy(x->x,value)\n"
                + "behavior build:(xs:List<Int>)->Accepted|NoItems|Duplicate\n"
                + "  constructs Accepted,Items,NoItems,Duplicate\n"
                + "let build (xs)={\n"
                + "guard Items(xs) as items else | nonEmpty->NoItems | unique->Duplicate\n"
                + "Accepted { items=items }\n}\n";
        String expected = """
                module demo

                data NoItems
                data Duplicate
                data Accepted =
                    { items: Items
                    }
                data Items = List<Int>
                    invariant nonEmpty = List.length(value) >= 1
                    invariant unique = List.allDistinctBy(x -> x, value)
                behavior build : (xs: List<Int>) -> Accepted | NoItems | Duplicate
                    constructs Accepted, Items, NoItems, Duplicate
                let build (xs) = {
                    guard Items(xs) as items else
                        | nonEmpty -> NoItems
                        | unique -> Duplicate
                    Accepted { items = items }
                }
                """;
        assertEquals(expected, Formatter.format(messy));
        assertEquals(expected, Formatter.format(expected), "and it is a fixed point");
    }

    /** A spread naming a field path keeps its path: `...c.address`, not `...c`. */
    @Test
    void aNestedSpreadPathSurvivesFormatting() {
        String src = """
                module demo

                data Address = { city: String, street: String }
                data Customer = { name: String, address: Address }

                behavior move : (c: Customer, city: String) -> Customer constructs Customer, Address

                let move (c, city) = Customer { ...c, address = Address { ...c.address, city = city } }
                """;
        String formatted = Formatter.format(src);
        assertTrue(formatted.contains("...c.address"), formatted);
        assertEquals(code(src), code(formatted));
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

    /**
     * The formatter writes both spellings of a keyword back as literal text rather than copying the
     * tokens, so nothing but a pinned form catches a wrong one. {@code depends on} is the only
     * two-word keyword, and the {@code on} lexes as an ordinary identifier — read as a name it would
     * come back as an extra dependency, and dropped it would come back as none.
     */
    @Test
    void canonicalFormOfAGuardAndATwoWordDependsOn() {
        String messy = "module demo\n"
                + "data Amount=Int\n  invariant value>=0\n"
                + "data Paid=Int\ndata Refused\n"
                + "behavior clock:()->Amount constructs Amount\n"
                + "behavior audit:(a:Amount)->Amount constructs Amount\n"
                + "behavior pay:(a:Amount)->Paid|Refused depends on clock,audit constructs Paid,Refused\n"
                + "let pay (a,clock,audit)={\n"
                + "guard a.value<=100 else Refused\n"
                + "guard Amount(a.value-1) as charged else Refused\n"
                + "Paid(charged.value) }\n";
        String expected = """
                module demo

                data Amount = Int
                    invariant value >= 0
                data Paid = Int
                data Refused
                behavior clock : () -> Amount
                    constructs Amount
                behavior audit : (a: Amount) -> Amount
                    constructs Amount
                behavior pay : (a: Amount) -> Paid | Refused
                    depends on clock, audit
                    constructs Paid, Refused
                let pay (a, clock, audit) = {
                    guard a.value <= 100 else Refused
                    guard Amount(a.value - 1) as charged else Refused
                    Paid(charged.value)
                }
                """;
        assertEquals(expected, Formatter.format(messy));
        assertEquals(expected, Formatter.format(expected), "formatting is not idempotent");
    }

    /** A value writes no parameter list, and a lambda written on the right of {@code =} is the
     * parameter-list form — one definition, so the formatter writes one spelling of it. */
    @Test
    void canonicalFormOfAValueAndALambdaBoundToAName() {
        String messy = "module demo\n"
                + "data Amount=Int\n"
                + "let cap=Amount(100)\n"
                + "let raise=(a)->a.value+1\n"
                + "behavior pay:(a:Amount)->Amount constructs Amount\n"
                + "let pay (a)=Amount(raise(a)+cap.value)\n";
        String expected = """
                module demo

                data Amount = Int
                let cap = Amount(100)
                let raise (a) = a.value + 1
                behavior pay : (a: Amount) -> Amount
                    constructs Amount
                let pay (a) = Amount(raise(a) + cap.value)
                """;
        assertEquals(expected, Formatter.format(messy));
        assertEquals(expected, Formatter.format(expected), "formatting is not idempotent");
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
    void formattingKeepsAnUnreachableArmAndItsReason() {
        String source = "module demo\n"
                + "data A\n"
                + "data B\n"
                + "data AB = A | B\n"
                + "data Out = Int\n"
                + "behavior run : (v: AB) -> Out constructs Out\n"
                + "let run (v) =\n"
                + "    match v with\n"
                + "        | A -> Out(1)\n"
                + "        | B -> unreachable \"a B never reaches this rule\"\n";
        String formatted = Formatter.format(source);
        assertEquals(code(source), code(formatted), "the code token stream changed");
        assertTrue(formatted.contains("unreachable \"a B never reaches this rule\""),
                "formatter lost the reason:\n" + formatted);
        assertEquals(formatted, Formatter.format(formatted), "formatting is not idempotent");
        assertTrue(CstParser.parse(formatted).errors().isEmpty(), "formatted output does not re-parse");
    }

    @Test
    void unreachableIsOneSpaceFromItsReason() {
        String messy = "module demo\n"
                + "data A\n"
                + "data B\n"
                + "data AB = A | B\n"
                + "data Out = Int\n"
                + "behavior run : (v: AB) -> Out constructs Out\n"
                + "let run (v) =\n"
                + "    match v with\n"
                + "        | A -> Out(1)\n"
                + "        | B ->     unreachable      \"a B never reaches this rule\"\n";
        assertTrue(Formatter.format(messy).contains("| B -> unreachable \"a B never reaches this rule\""),
                "the reason is not one space from the keyword:\n" + Formatter.format(messy));
    }

    @Test
    void formattingKeepsACollectionNewtypeBase() {
        // the newtype body used to be one identifier, so the type arguments had nowhere to be
        // written; dropping them would turn `List<String>` into a `List` with no element type
        String source = "module demo\n"
                + "data Tags = List<String>\n"
                + "data Stock = Map<String, Int>\n"
                + "data Labels = Set<String>\n";
        String formatted = Formatter.format(source);
        assertEquals(code(source), code(formatted), "the code token stream changed");
        assertEquals(formatted, Formatter.format(formatted), "formatting is not idempotent");
        assertTrue(formatted.contains("data Tags = List<String>"), formatted);
        assertTrue(formatted.contains("data Stock = Map<String, Int>"), formatted);
        assertTrue(CstParser.parse(formatted).errors().isEmpty(), "formatted output does not re-parse");
    }

    @Test
    void formattingKeepsBindingPatterns() {
        // a pattern is what the author wrote about the shape of a value; rewriting it as a name
        // would drop the bindings its parts make
        String source = "module demo\n"
                + "import List ( length, map )\n"
                + "data Tags = List<String>\n"
                + "data Line = { sku: String, qty: Int }\n"
                + "data In = { lines: List<Line> }\n"
                + "data Out = { v: Int }\n"
                + "let count (Tags(xs)) = length(xs)\n"
                + "behavior run : (i: In) -> Out constructs Out\n"
                + "let run (i) = {\n"
                + "let { lines } = i\n"
                + "let (a, b) = (1, 2)\n"
                + "Out { v = a + b + length(map(({ qty }) -> qty, lines)) }\n"
                + "}\n";
        String formatted = Formatter.format(source);
        assertEquals(code(source), code(formatted), "the code token stream changed");
        assertEquals(formatted, Formatter.format(formatted), "formatting is not idempotent");
        assertTrue(formatted.contains("let count (Tags(xs)) ="), formatted);
        assertTrue(formatted.contains("let { lines } = i"), formatted);
        assertTrue(formatted.contains("let (a, b) = (1, 2)"), formatted);
        assertTrue(formatted.contains("({ qty }) -> qty"), formatted);
        assertTrue(CstParser.parse(formatted).errors().isEmpty(), "formatted output does not re-parse");
    }

    @Test
    void formattingKeepsALocalLetTypeAnnotation() {
        // the annotation is what pins an empty-collection value at the binding (issue #71); dropping it
        // would turn a compiling module into one that cannot infer the accumulator's type.
        String source = "module demo\n"
                + "data In = { keys: List<String> }\n"
                + "data Out = { m: Map<String, Int> }\n"
                + "behavior run : (i: In) -> Out constructs Out\n"
                + "let run (i) = {\n"
                + "let counted:Map<String,Int> = Map.empty\n"
                + "Out { m = counted }\n"
                + "}\n";
        String formatted = Formatter.format(source);
        assertEquals(code(source), code(formatted), "the code token stream changed");
        assertTrue(formatted.contains("let counted: Map<String, Int> ="),
                "formatter dropped the annotation:\n" + formatted);
        assertTrue(CstParser.parse(formatted).errors().isEmpty(), "formatted output does not re-parse");
    }

    @Test
    void formattingKeepsAReadOffACallResult() {
        // the read hangs off the call, so the formatter has to render the chain from its left operand
        // rather than from a name (issue #158)
        String source = "module demo\n"
                + "data Amount = Int invariant value >= 0\n"
                + "data In = { n: Int }\n"
                + "data Out = { m: Int }\n"
                + "let amountOf (i: In) = Amount(i.n)\n"
                + "behavior run : (i: In) -> Out constructs Out, Amount\n"
                + "let run (i) = Out { m=amountOf(i).value }\n";
        String formatted = Formatter.format(source);
        assertEquals(code(source), code(formatted), "the code token stream changed");
        assertEquals(formatted, Formatter.format(formatted), "formatting is not idempotent");
        assertTrue(formatted.contains("amountOf(i).value"), formatted);
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
                + "behavior f:(r:R)->R depends on clock constructs R\n"
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
                    depends on clock
                    constructs R
                let f (r, clock) = r
                fake lookup
                    | (R { x = 1 }) -> R { x = 2 }
                    | _             -> R { x = 0 }
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
