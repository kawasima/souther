package souther.compiler.fmt;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import souther.compiler.Reserved;
import souther.compiler.cst.CstParser;
import souther.compiler.cst.SyntaxElement;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.cst.SyntaxNode;
import souther.compiler.cst.SyntaxToken;
import souther.test.RepositoryLayout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the canonical form writes between two code tokens that share a line, as a function and not as
 * an observation. Three things, in order.
 *
 * <p><b>The range.</b> There is no third answer: whatever the source wrote between two tokens, the
 * canonical form writes nothing or one space. That is where the alignment goes.
 *
 * <p><b>The rule.</b> Which of the two is decided by the construct that joins the tokens — their
 * lowest common ancestor in the tree — together with the kind on each side. Over a corpus that builds
 * every kind of node the grammar has, that key never takes both answers. So the function exists, and
 * this is what it reads.
 *
 * <p><b>Where the construct is load-bearing.</b> For all but nine pairs of token kinds the answer is
 * the same from every construct that can join them, so the pair alone decides. Those nine are written
 * out with each construct's answer, and the rest with theirs — the whole function is transcribed,
 * because it does not compress. Grouping the kinds into words, brackets, commas, dots and operators
 * leaves eight of the twenty-four class pairs taking both answers, and splitting the classes far
 * enough to fix that arrives back at the kind. So the table is the rule, and a pair reaching the
 * corpus that no row holds fails: a new adjacency is a decision, and it is made here rather than by
 * whichever construct writes it first.
 *
 * <p>The corpus is not a sample of Souther. It is a set of sources chosen so that every node kind in
 * {@link SyntaxKind} is built at least once, and that is asserted — a kind added to the grammar
 * without a source here fails rather than going unmeasured.
 */
@Tag("population")
class WhatGoesBetweenTwoTokensOnALineTest {

    /** Sources written to reach the constructs the bundled standard library does not use. */
    private static final List<String> WRITTEN = List.of(
            """
            module m exposing ( f, T )

            import some.place as P ( alpha )

            behavior pay : (a: Amount, b: Int) -> Paid | Refused
                depends on clock, audit
                constructs Paid, Refused
                ensures same = Paid | Refused -> value.id == a
            """,
            """
            module m

            data W = Int

            let f (w: W): Int = {
                let W(inner) = w
                let (x, y) = pair
                let { a, b = c } = record
                guard Amount(inner) as amount else
                    | one -> Refused
                    | two -> Denied
                amount
            }
            """,
            """
            module m

            data A

            data B

            let f (o: Option<W>): Int = match o with
                | Some(W(x)) -> x
                | Mod.None { a, b = c } as whole -> 0
                | A | B -> unreachable "neither reaches this rule"
            """,
            """
            module m

            let f (a: Int, g: (Int, Int) -> Int, t: (Int, Int)): Int = -(a + 1) * g(1, 2)
            """,
            """
            module m

            let f (a: Int?): Int = if a > 0 then 1 else 0

            let each (xs: List<Int>): List<Int> = [x | x > 0]

            let by: (W) -> Int = .value

            let piped (a: Int): Int = a |> double |> triple

            partial let part (a: Int): Int = a

            private let hidden (a: Int): Int = a
            """,
            """
            module m

            data P =
                { a: Int
                }

            data Q =
                { ...P
                , b: Int
                }

            data S = A | B

            data N = Int
                invariant value >= 0

            let f (p: P): Q = Q { ...p, b = 1 }
            """,
            """
            module m exposing ( sized : (Int) -> Int, T )

            let sized (n: Int): Int = -n

            let cmp (a: Int, b: Int): Bool = double(a) > b
            """,
            """
            module m

            behavior b = alpha >-> beta -> Int
            """,
            // every bracketed construct with nothing between its brackets, with one member, and with
            // more than one — see AllOfABracketedConstructsCardinalitiesAreSweptTest
            """
            module m exposing ()

            import other.mod ()
            """,
            """
            module m exposing ( f )

            import other.mod ( a )

            data Empty = {}

            data Held =
                { a: Int
                }

            let f (a: Int): Int = g()

            let one (a: Int): Int = g(a)

            let held (r: R): Int = {
                let {} = r
                let { a } = r
                let (b) = r
                b
            }

            let made (a: Int): R = R {}

            let filled (a: Int): R = R { a = 1 }

            let listed (a: Int): List<Int> = []

            let single (a: Int): List<Int> = [a]

            let takes (t: () -> Int, u: (Int) -> Int): Int = 1

            behavior none : () -> R

            let parened (a: Int): Int = (a)

            let manyNames (a: Int): Int = 1
            """,
            """
            module m

            import other.mod ( a, b )

            behavior one : (a: A) -> R

            let comp (a: Int): List<Int> = [x | x > 0, x < 9]

            let lambdaOne (a: Int): Int = call((x) -> x)

            let listMany (a: Int): List<Int> = [a, a]

            let tupleType (t: (Int)): Int = 1

            let emptyTupleType (t: ()): Int = 1

            let emptyTuplePattern (r: R): Int = {
                let () = r
                1
            }

            let armBraces (a: Int): Int =
                match a with
                    | A {} -> 1
                    | B { a } -> 2
                    | C -> 3
            """,
            // one bracket closing or opening against another, which node-kind coverage does not
            // reach on its own: a construct nested directly inside another's brackets
            """
            module m

            let ints (a: Int): List<Int> = [1, 2]

            let listed (a: Int): R = R { a = [1] }

            let called (a: Int): R = R { a = f(1) }

            let inside (a: Int): Int = f(R { a = 1 })

            let nested (a: Int): R = R { a = S { b = 1 } }

            let described (a: Int): Int = f("x")

            let stringed (a: Int): R = R { a = "x" }

            let flagged (a: Int): R = R { a = true }

            let unflagged (a: Int): R = R { a = false }

            let two (a: Int): Int = f("x", "y")

            let parenArg (a: Int): Int = f(a, (a))

            let annotated (a: Int): Int = {
                let x: Int = 1
                x
            }

            let opened (a: Int): Int = call(({ a }) -> a)

            let arith (a: Int): Int = 1 + a * 2 - a

            let compared (a: Int): Bool = a <= 1

            let after (a: Int): Int = f(a) + 1

            let atLeast (a: Int): Bool = f(a) >= 1

            let piped (a: Int): Int = 1 |> double
            """,
            """
            examples for m

            example helper
                | "a description" : (1) with dep = two, other = three -> 2

            fake helper
                | (1) -> 2
                | _ -> 0
            """);

    /** Read once, because the repository does not move while a run happens: reading it is a pom to
     *  parse and a walk, and {@link #corpus} is asked for by every rule this module holds. */
    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * One bundled standard-library source, asked for by the name the library gives it.
     *
     * <p>Read from the repository rather than looked up on the class path. The formatter depends on
     * the syntax and not on the compiler, so the sources the compiler bundles are not on this
     * module's class path — and a corpus that quietly came up short would leave every rule below
     * holding over less text than it says it does. A name the library does not have is refused
     * where the library is handed out.
     */
    private static String stdlib(String module) {
        Path source = REPOSITORY.preludeSourceOf(module);
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every source the rule is read from: the bundled standard library, and the written sources. */
    static List<String> corpus() {
        List<String> out = new ArrayList<>();
        for (Reserved.StdlibModule m : Reserved.MODULES) {
            out.add(stdlib(m.moduleName().substring("souther.".length())));
        }
        out.addAll(WRITTEN);
        return out;
    }

    private static List<SyntaxToken> tokens(SyntaxNode n) {
        List<SyntaxToken> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxNode c) {
                out.addAll(tokens(c));
            } else if (e instanceof SyntaxToken t) {
                out.add(t);
            }
        }
        return out;
    }

    private static void nodeKindsOf(SyntaxNode n, Set<SyntaxKind> out) {
        out.add(n.kind());
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxNode c) {
                nodeKindsOf(c, out);
            }
        }
    }

    /** The construct that joins two tokens: the deepest node holding both of them. */
    private static SyntaxNode joining(SyntaxToken left, SyntaxToken right) {
        Set<SyntaxNode> above = new LinkedHashSet<>();
        for (SyntaxNode p = left.parent(); p != null; p = p.parent()) {
            above.add(p);
        }
        for (SyntaxNode p = right.parent(); p != null; p = p.parent()) {
            if (above.contains(p)) {
                return p;
            }
        }
        throw new AssertionError("two tokens of one file with no node holding both");
    }

    /** One adjacency in a canonical form: what joins the two tokens, their kinds, and the
     * separator written between them — which is what the spacing rule answers and not the whole run,
     * where a table's column has padded it out. */
    record Adjacency(SyntaxKind joins, SyntaxKind left, SyntaxKind right, String separator) {
        String pair() {
            return left + " " + right;
        }
    }

    /** Every adjacency in the canonical form of every source, excluding the pairs a break separated,
     * which are the other rules' business. Read by
     * {@link TheSpacingRuleAgreesWithTheCanonicalFormTest} as well: what is measured — the rendered
     * text — is the same, and what each of the two holds it against is not.
     *
     * <p>Where a table's column stands at an adjacency, the run before the connector is the
     * separator and then the padding that carries the row out to the column. The two are different
     * rules' and the split between them is the layout's own answer, read from where it wrote the
     * stop rather than guessed at from the spaces. */
    static List<Adjacency> adjacencies() {
        List<Adjacency> out = new ArrayList<>();
        for (String source : corpus()) {
            Formatter.CanonicalForm form = Formatter.canonicalize(CstParser.parse(source).root());
            String canonical = form.text();
            CstParser.Result parsed = CstParser.parse(canonical);
            assertTrue(parsed.errors().isEmpty(), "a canonical form did not reparse:\n" + canonical);
            List<SyntaxToken> code = tokens(parsed.root()).stream().filter(t -> !t.isTrivia()).toList();
            Map<Integer, Integer> padsFrom = new LinkedHashMap<>();
            for (Witnesses.CanonicalStop stop : Witnesses.stops(form, code)) {
                padsFrom.put(stop.adjacency(), stop.occurrence().at());
            }
            for (int i = 0; i + 1 < code.size(); i++) {
                SyntaxToken a = code.get(i);
                SyntaxToken b = code.get(i + 1);
                String gap = canonical.substring(a.end(), b.start());
                if (!gap.contains("\n")) {
                    out.add(new Adjacency(joining(a, b).kind(), a.kind(), b.kind(),
                            canonical.substring(a.end(), padsFrom.getOrDefault(i, b.start()))));
                }
            }
        }
        return out;
    }

    // --- the range ---

    /** Nothing, or one space. What this rules out is a construct spacing its own tokens by however
     * many columns it likes: the one place a run of spaces is written is a table's column, and there
     * it is written after this answer rather than instead of it. */
    @Test
    void whatIsWrittenBetweenThemIsNothingOrOneSpace() {
        Set<String> seen = new LinkedHashSet<>();
        adjacencies().forEach(a -> seen.add(a.separator()));
        assertTrue(Set.of("", " ").containsAll(seen), "the separators written are " + seen);
    }

    /** And both of them are written, so the rule below has two answers to choose between. */
    @Test
    void andBothOfThemAreWritten() {
        Set<String> seen = new LinkedHashSet<>();
        adjacencies().forEach(a -> seen.add(a.separator()));
        assertEquals(Set.of("", " "), seen, "over the whole corpus");
    }

    // --- the rule ---

    /**
     * The construct that joins the two tokens, and the kind on each side, decide what goes between
     * them. Nothing else is read: no two adjacencies with that key are written differently.
     */
    @Test
    void theConstructThatJoinsThemAndTheirKindsDecideIt() {
        Map<String, Set<String>> answers = new LinkedHashMap<>();
        for (Adjacency a : adjacencies()) {
            answers.computeIfAbsent(a.joins() + " " + a.pair(), _ -> new LinkedHashSet<>())
                    .add(a.separator());
        }
        List<String> disagreeing = answers.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " -> " + e.getValue()).toList();
        assertEquals(List.of(), disagreeing, "keys written both ways");
    }

    // --- where the construct is load-bearing ---

    /**
     * The pairs of token kinds whose answer the joining construct decides, and what each construct
     * answers. Every other pair takes one answer wherever it occurs.
     */
    private static final Map<String, Map<String, String>> POSITION_DECIDES = positionDecides();

    /** A row: the constructs that can join this pair, each with what it writes between them. */
    private static Map<String, String> row(String... constructAndSeparator) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < constructAndSeparator.length; i += 2) {
            out.put(constructAndSeparator[i], constructAndSeparator[i + 1]);
        }
        return out;
    }

    private static Map<String, Map<String, String>> positionDecides() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        // A list of names is written open — `exposing ( a, b )`, `import m ( a, b )` — and every
        // other bracketed construct is not.
        out.put("LPAREN IDENT", row(
                "EXPOSING_CLAUSE", " ", "NAME_LIST", " ",
                "ARG_LIST", "", "PARAM_LIST", "", "FN_PARAM_LIST", "", "FN_TYPE", "",
                "LAMBDA_EXPR", "", "MATCH_CASE", "", "PAREN_EXPR", "", "PATTERN_CTOR", "",
                "PATTERN_TUPLE", "", "TUPLE_EXPR", "", "TUPLE_TYPE", ""));
        out.put("IDENT RPAREN", row(
                "EXPOSING_CLAUSE", " ", "NAME_LIST", " ",
                "ARG_LIST", "", "PARAM_LIST", "", "FN_PARAM_LIST", "", "FN_TYPE", "",
                "LAMBDA_EXPR", "", "MATCH_CASE", "", "PAREN_EXPR", "", "PATTERN_CTOR", "",
                "PATTERN_TUPLE", "", "TUPLE_EXPR", "", "TUPLE_TYPE", ""));
        // A declaration's name is spaced from what follows it; what is applied or opened is not.
        out.put("IDENT LPAREN", row(
                "FN_DEF", " ", "IMPORT_DECL", " ",
                "APPLY_EXPR", "", "MATCH_CASE", "", "PATTERN_CTOR", ""));
        // A type ascribed to a name is written tight against it; a signature is spaced from its name.
        out.put("IDENT COLON", row(
                "BEHAVIOR_DEF", " ", "EXPOSED_ENTRY", " ",
                "FIELD", "", "FN_DEF", "", "FN_PARAM", "", "LET_STMT", "", "PARAM", ""));
        // `<` and `>` are a type's brackets or a comparison's operator.
        out.put("IDENT LT", row("TYPE_REF", "", "BINARY_EXPR", " "));
        out.put("LT IDENT", row("TYPE_ARGS", "", "BINARY_EXPR", " "));
        out.put("IDENT GT", row("TYPE_ARGS", "", "BINARY_EXPR", " "));
        out.put("RPAREN GT", row("TYPE_ARGS", "", "BINARY_EXPR", " "));
        // `-` negates or subtracts.
        out.put("MINUS IDENT", row("UNARY_EXPR", "", "BINARY_EXPR", " "));
        return out;
    }

    /**
     * The pairs written with nothing between them wherever they occur, and the pairs written with a
     * space. Together with the nine above this is the whole function, and it is transcribed because
     * it does not compress: token classes do not decide it either. Grouping the kinds into words,
     * brackets, commas, dots and operators leaves eight of the twenty-four class pairs taking both
     * answers — `):` against `) ->`, `-n` against `+ 1`, `Amount(` against `P (` — so a rule written
     * over classes would need so many exceptions that the pair is the smaller statement.
     *
     * <p>A pair reaching the corpus that is in neither list fails, which is the point: a new
     * adjacency is a decision, and it should be made here rather than by whichever construct wrote
     * it first.
     */
    private static final String TIGHT = """
            DOT IDENT
            FALSE_KW COMMA
            GT COMMA
            GT GT
            GT RPAREN
            IDENT COMMA
            IDENT DOT
            IDENT QUESTION
            IDENT RBRACKET
            INT_LIT COMMA
            LPAREN UNDERSCORE
            INT_LIT RBRACKET
            INT_LIT RPAREN
            LBRACE RBRACE
            LBRACKET IDENT
            LBRACKET INT_LIT
            LBRACKET LPAREN
            LBRACKET RBRACKET
            LPAREN INT_LIT
            LPAREN LBRACE
            LPAREN LBRACKET
            LPAREN LPAREN
            LPAREN RPAREN
            LPAREN STRING_LIT
            LPAREN TYPEVAR
            LT LPAREN
            LT TYPEVAR
            MINUS LPAREN
            QUESTION COMMA
            QUESTION RPAREN
            RBRACE COMMA
            RBRACE RPAREN
            RBRACKET COMMA
            RBRACKET RPAREN
            RPAREN COLON
            RPAREN COMMA
            RPAREN RBRACKET
            RPAREN RPAREN
            SPREAD IDENT
            STRING_LIT COMMA
            STRING_LIT RPAREN
            TRUE_KW COMMA
            TYPEVAR COMMA
            TYPEVAR GT
            TYPEVAR QUESTION
            TYPEVAR RPAREN
            UNDERSCORE COMMA
            UNDERSCORE RPAREN
            """;

    private static final String SPACED = """
            AND IDENT
            ARROW IDENT
            ARROW IF_KW
            ARROW INT_LIT
            ARROW LBRACE
            ARROW LBRACKET
            ARROW LPAREN
            ARROW MATCH_KW
            ARROW TYPEVAR
            ARROW UNREACHABLE_KW
            ASSIGN DOT
            ASSIGN FALSE_KW
            ASSIGN IDENT
            ASSIGN IF_KW
            ASSIGN INT_LIT
            ASSIGN LBRACE
            ASSIGN LBRACKET
            ASSIGN LPAREN
            ASSIGN MINUS
            ASSIGN STRING_LIT
            ASSIGN TRUE_KW
            AS_KW IDENT
            BEHAVIOR_KW IDENT
            COLON IDENT
            COLON LPAREN
            COLON TYPEVAR
            COMMA FALSE_KW
            COMMA IDENT
            COMMA UNDERSCORE
            COMMA INT_LIT
            COMMA LBRACKET
            COMMA LPAREN
            COMMA STRING_LIT
            COMMA TRUE_KW
            COMMA TYPEVAR
            CONSTRUCTS_KW IDENT
            DATA_KW IDENT
            DECIMAL_LIT MINUS
            DECIMAL_LIT THEN_KW
            DEPENDS_KW IDENT
            ELSE_KW IDENT
            ELSE_KW IF_KW
            ELSE_KW INT_LIT
            ELSE_KW LPAREN
            ELSE_KW TRUE_KW
            ENSURES_KW IDENT
            EQ IDENT
            EQ INT_LIT
            EXPOSING_KW LPAREN
            FALSE_KW ELSE_KW
            FALSE_KW RBRACE
            GE IDENT
            GE INT_LIT
            GT ASSIGN
            GT IDENT
            GT INT_LIT
            GUARD_KW IDENT
            IDENT AND
            IDENT ARROW
            IDENT ASSIGN
            IDENT AS_KW
            IDENT ELSE_KW
            IDENT EQ
            IDENT EXPOSING_KW
            IDENT GE
            IDENT IDENT
            IDENT LBRACE
            IDENT LE
            IDENT LET_KW
            IDENT OR
            IDENT PIPE
            IDENT PIPEFWD
            IDENT PLUS
            IDENT PLUSPLUS
            IDENT RBRACE
            IDENT STAR
            IDENT STRING_LIT
            IDENT THEN_KW
            IDENT VPIPE
            IDENT WITH_KW
            IF_KW IDENT
            IMPORT_KW IDENT
            INT_LIT ELSE_KW
            INT_LIT MINUS
            INT_LIT PLUS
            INT_LIT RBRACE
            INT_LIT THEN_KW
            INT_LIT VPIPE
            INVARIANT_KW IDENT
            LBRACE IDENT
            LBRACE SPREAD
            LE INT_LIT
            LET_KW IDENT
            LET_KW LBRACE
            LET_KW LPAREN
            LT DECIMAL_LIT
            LT INT_LIT
            MATCH_KW IDENT
            MODULE_KW IDENT
            OR IDENT
            PIPE IDENT
            PIPE LPAREN
            PIPE UNDERSCORE
            PIPE STRING_LIT
            PIPEFWD IDENT
            PLUS IDENT
            PLUS INT_LIT
            PLUSPLUS IDENT
            PLUSPLUS LBRACKET
            RBRACE ARROW
            RBRACE ASSIGN
            RBRACE AS_KW
            RBRACE RBRACE
            RBRACKET ELSE_KW
            RBRACKET RBRACE
            RPAREN ARROW
            RPAREN ASSIGN
            RPAREN AS_KW
            RPAREN ELSE_KW
            RPAREN EQ
            RPAREN GE
            RPAREN PLUS
            RPAREN RBRACE
            RPAREN STAR
            RPAREN THEN_KW
            RPAREN WITH_KW
            STAR IDENT
            STAR INT_LIT
            STRING_LIT COLON
            STRING_LIT RBRACE
            THEN_KW DECIMAL_LIT
            THEN_KW FALSE_KW
            THEN_KW IDENT
            THEN_KW INT_LIT
            THEN_KW LPAREN
            TRUE_KW RBRACE
            TYPEVAR ASSIGN
            UNDERSCORE ARROW
            UNREACHABLE_KW STRING_LIT
            VPIPE IDENT
            WITH_KW IDENT
            """;

    private static Set<String> pairsIn(String block) {
        Set<String> out = new TreeSet<>();
        for (String line : block.lines().toList()) {
            if (!line.isBlank()) {
                out.add(line.strip());
            }
        }
        return out;
    }

    /** Every pair the corpus writes is one of the three lists, and each is written as its list says. */
    @Test
    void everyPairIsWrittenAsItsRowSays() {
        Set<String> tight = pairsIn(TIGHT);
        Set<String> spaced = pairsIn(SPACED);
        Set<String> decided = POSITION_DECIDES.keySet();
        Set<String> unlisted = new TreeSet<>();
        Set<String> wrong = new TreeSet<>();
        Set<String> reached = new TreeSet<>();
        for (Adjacency a : adjacencies()) {
            reached.add(a.pair());
            if (decided.contains(a.pair())) {
                continue;
            }
            if (tight.contains(a.pair())) {
                if (!a.separator().isEmpty()) {
                    wrong.add(a.pair() + " is listed tight and was written spaced");
                }
            } else if (spaced.contains(a.pair())) {
                if (!a.separator().equals(" ")) {
                    wrong.add(a.pair() + " is listed spaced and was written tight");
                }
            } else {
                unlisted.add(a.pair());
            }
        }
        assertEquals(new TreeSet<String>(), unlisted, "pairs the corpus writes that no list holds");
        assertEquals(new TreeSet<String>(), wrong, "pairs written against their row");
        Set<String> unreached = new TreeSet<>(tight);
        unreached.addAll(spaced);
        unreached.addAll(decided);
        unreached.removeAll(reached);
        assertEquals(new TreeSet<String>(), unreached, "rows no source in the corpus reaches");
    }

    /** These pairs, and no others, are written both ways somewhere in the corpus. */
    @Test
    void theseArePairsTheJoiningConstructDecides() {
        Map<String, Set<String>> byPair = new LinkedHashMap<>();
        for (Adjacency a : adjacencies()) {
            byPair.computeIfAbsent(a.pair(), _ -> new LinkedHashSet<>()).add(a.separator());
        }
        Set<String> both = new TreeSet<>();
        byPair.forEach((k, v) -> {
            if (v.size() > 1) {
                both.add(k);
            }
        });
        assertEquals(new TreeSet<>(POSITION_DECIDES.keySet()), both);
    }

    /** And each construct that joins one of them writes what the table says. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("decidedPairs")
    void andEachConstructAnswersAsTheTableSays(String pair) {
        Map<String, String> expected = POSITION_DECIDES.get(pair);
        Map<String, String> seen = new LinkedHashMap<>();
        for (Adjacency a : adjacencies()) {
            if (a.pair().equals(pair)) {
                seen.put(a.joins().name(), a.separator());
            }
        }
        assertEquals(new TreeSet<>(expected.keySet()), new TreeSet<>(seen.keySet()),
                pair + ": the constructs that join this pair");
        expected.forEach((construct, separator) ->
                assertEquals(separator, seen.get(construct), pair + " under " + construct));
    }

    static Stream<String> decidedPairs() {
        return POSITION_DECIDES.keySet().stream();
    }

    /**
     * The same pattern, written as a match arm and as a {@code let}. The grammar writes a match arm's
     * pattern as a run of tokens rather than as a node, so the formatter joins those tokens itself
     * instead of a construct writing what goes between them — the one place in the formatter where
     * that happens. It has to answer as the construct that writes the same syntax elsewhere does, or
     * the same pattern reads two ways depending on where it is.
     *
     * <p>It did not: a newtype opened by its constructor was written {@code Some ( x )} in a match
     * arm and {@code Some(x)} in a {@code let}, and no fixture in the suite observed the difference.
     */
    @Test
    void aPatternReadsTheSameWhereverItIsWritten() {
        String canonical = Formatter.format("""
                module m

                data W = Int

                let opened (o: Option<W>): Int = match o with
                    | Some(W(x)) -> x
                    | Held { a, b = c } -> a
                    | None -> 0

                let bound (w: W): Int = {
                    let W(inner) = w
                    let { a, b = c } = inner
                    a
                }
                """);
        for (String written : List.of(
                "| Some(W(x)) -> x", "let W(inner) = w",
                "| Held { a, b = c } -> a", "let { a, b = c } = inner")) {
            assertTrue(canonical.contains(written),
                    "nothing holds `" + written + "` in:\n" + canonical);
        }
    }

    // --- the corpus covers the grammar ---

    /**
     * Every node kind the grammar builds occurs in the corpus. Without this the rule above is a
     * statement about the sources that happened to be read: a construct absent from all of them
     * could join two tokens in a way nothing here would have seen.
     */
    @Test
    void thereIsASourceForEveryKindOfNode() {
        Set<SyntaxKind> built = new LinkedHashSet<>();
        for (String source : corpus()) {
            nodeKindsOf(CstParser.parse(Formatter.format(source)).root(), built);
        }
        Set<String> missing = new TreeSet<>();
        for (SyntaxKind k : SyntaxKind.values()) {
            if (k.compareTo(SyntaxKind.SOURCE_FILE) >= 0 && !built.contains(k)) {
                missing.add(k.name());
            }
        }
        assertEquals(new TreeSet<String>(), missing, "node kinds no source in the corpus builds");
    }

    /**
     * The node kinds that never join two tokens on a line, which is why they name no row above. Each
     * either holds one token, or writes a break between every two it holds. Listed rather than left
     * out, so that one which starts joining tokens is noticed.
     */
    @Test
    void andTheseKindsOfNodeJoinNoTwoTokensOnALine() {
        Set<SyntaxKind> built = new LinkedHashSet<>();
        for (String source : corpus()) {
            nodeKindsOf(CstParser.parse(Formatter.format(source)).root(), built);
        }
        Set<String> joins = new TreeSet<>();
        adjacencies().forEach(a -> joins.add(a.joins().name()));
        Set<String> never = new TreeSet<>();
        built.forEach(k -> {
            if (!joins.contains(k.name())) {
                never.add(k.name());
            }
        });
        assertEquals(new TreeSet<>(List.of(
                "BLOCK_EXPR",          // its statements are one to a line
                "ELSE_ARMS",           // its arms are one to a line
                "LITERAL_EXPR",        // one token
                "NEWTYPE_BODY",        // one type reference
                "PARTIAL_MODIFIER",    // one token
                "PATTERN_NAME",        // one token
                "PRIVATE_MODIFIER",    // one token
                "SOURCE_FILE",         // its items are one to a line
                "STAGE",               // one stage of a pipeline, one to a line
                "VAR_EXPR")),          // one token
                never);
    }
}
