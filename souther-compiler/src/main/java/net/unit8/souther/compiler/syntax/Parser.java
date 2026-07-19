package net.unit8.souther.compiler.syntax;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.SourcePos;
import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A recursive-descent parser for the slice-2 grammar: multi-field product {@code data}
 * with an optional {@code invariant}, a {@code from Text|Int} or {@code from Object}
 * decoder, and a single-value or {@code Object { ... }} encoder.
 */
public final class Parser {

    private final List<Token> tokens;
    private int index = 0;
    /** When set, a bare {@code IDENT {} } is not read as a construction (used for match scrutinees). */
    private boolean noConstruct = false;
    /** Counter for the synthetic whole-case binding a field-destructuring match case desugars to. */
    private int matchWholeCounter = 0;
    /** Counter for the synthetic whole-tuple binding a {@code let (x, y) = t} destructure desugars to. */
    private int tupleCounter = 0;
    /** The module's name, known once its header is read; gates type-variable use to the core. */
    private String moduleName = "";

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Ast.Module parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parseModule();
    }

    // --- module ---

    public Ast.Module parseModule() {
        Token m = expect(TokenType.MODULE);
        String name = qualifiedName();
        moduleName = name;
        Map<String, Ast.RetType> exposedOutputs = new HashMap<>();
        List<String> exposing = check(TokenType.EXPOSING) ? parseExposing(exposedOutputs) : new ArrayList<>();
        List<Ast.Import> imports = new ArrayList<>();
        while (check(TokenType.IMPORT)) {
            imports.add(parseImport());
        }
        List<Ast.Def> defs = new ArrayList<>();
        List<Ast.BehaviorDef> behaviors = new ArrayList<>();
        List<Ast.FnDef> fns = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            if (check(TokenType.DATA)) {
                defs.add(parseDef());
            } else if (check(TokenType.BEHAVIOR)) {
                behaviors.add(parseBehavior());
            } else if (check(TokenType.LET)) {
                fns.add(parseFn());
            } else {
                throw error(peek(), "expected data, behavior, or let");
            }
        }
        return new Ast.Module(name, exposing, exposedOutputs, imports, defs, behaviors, fns, m.pos());
    }

    /**
     * {@code exposing ( name, name : A | B, ... )} — the module's public surface (spec 4). An
     * exposed composition behavior carries its output signature here ({@code name : A | B}, spec
     * 14.5); it is collected into {@code outputs} keyed by name. Other entries are bare names.
     */
    private List<String> parseExposing(Map<String, Ast.RetType> outputs) {
        expect(TokenType.EXPOSING);
        expect(TokenType.LPAREN);   // `exposing ( a, b )` — parentheses, as Elm does (ADR-0036)
        List<String> names = new ArrayList<>();
        parseExposedEntry(names, outputs);
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RPAREN)) {
                break;
            }
            parseExposedEntry(names, outputs);
        }
        expect(TokenType.RPAREN);
        return names;
    }

    /** One {@code exposing} entry: a name, optionally followed by {@code : A | B} (an exposed
     * composition's output, spec 14.5). */
    private void parseExposedEntry(List<String> names, Map<String, Ast.RetType> outputs) {
        String name = dottedName();
        names.add(name);
        if (match(TokenType.COLON)) {
            outputs.put(name, parseRetType());
        }
    }

    /** {@code import <module> ( name, ... )} — explicit imports only (no wildcards, spec 4). */
    private Ast.Import parseImport() {
        Token kw = expect(TokenType.IMPORT);
        String module = qualifiedName();
        expect(TokenType.LPAREN);   // `import M ( a, b )` — parentheses, as Elm does (ADR-0036)
        List<String> names = new ArrayList<>();
        names.add(expect(TokenType.IDENT).text());
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RPAREN)) {
                break;
            }
            names.add(expect(TokenType.IDENT).text());
        }
        expect(TokenType.RPAREN);
        return new Ast.Import(module, names, kw.pos());
    }

    /** An exposed entry: a bare name or a {@code Name.decoder}/{@code Name.encoder} member. */
    private String dottedName() {
        StringBuilder sb = new StringBuilder(expect(TokenType.IDENT).text());
        while (match(TokenType.DOT)) {
            sb.append('.').append(expect(TokenType.IDENT).text());
        }
        return sb.toString();
    }

    /**
     * {@code behavior name = <rhs>} where the rhs is either a pipeline ({@code a >-> b}) or a
     * signature ({@code (params) -> ret [constructs ...] [requires ...]}). One token after {@code =}
     * tells them apart: {@code (} is a signature. A behavior has no body — the implementation is a
     * same-named {@code fn} (13.1), a {@code >->} composition, or Java injection (12, 13.2).
     */
    private Ast.BehaviorDef parseBehavior() {
        Token kw = expect(TokenType.BEHAVIOR);
        String name = expect(TokenType.IDENT).text();
        // System 2 (ML): `:` gives a value a type (the signature), `=` binds a definition
        // (the composition). A behavior signature reads `behavior name : (...) -> ...`; a
        // composition reads `behavior name = stage >-> stage`.
        if (check(TokenType.LPAREN)) {
            throw error(peek(), "a behavior signature uses `:` before its parameters: "
                    + "`behavior " + name + " : (...) -> ...`");
        }
        if (match(TokenType.COLON)) {
            expect(TokenType.LPAREN);
            List<Ast.Param> params = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                params.add(parseParam());
                while (match(TokenType.COMMA)) {
                    if (check(TokenType.RPAREN)) {
                        break;
                    }
                    params.add(parseParam());
                }
            }
            expect(TokenType.RPAREN);
            expect(TokenType.ARROW);
            Ast.RetType ret = parseRetType();
            // `constructs` and `requires` may appear in either order after the return type.
            List<String> constructs = new ArrayList<>();
            List<String> requires = new ArrayList<>();
            boolean more = true;
            while (more) {
                if (match(TokenType.CONSTRUCTS)) {
                    parseNameList(constructs);
                } else if (match(TokenType.REQUIRES)) {
                    parseNameList(requires);
                } else {
                    more = false;
                }
            }
            return new Ast.SpecBehavior(name, params, ret, constructs, requires, kw.pos());
        }
        expect(TokenType.ASSIGN);
        List<String> stages = new ArrayList<>();
        stages.add(parseStage());
        while (match(TokenType.PIPEFWD)) {
            stages.add(parseStage());
        }
        // an optional trailing `-> cases` declares the composition's output (spec 14.5); the
        // stages are behavior names, so this `->` reads unambiguously as the pipeline's own
        Ast.RetType declaredOut = match(TokenType.ARROW) ? parseRetType() : null;
        return new Ast.PipeBehavior(name, stages, declaredOut, kw.pos());
    }

    /** A comma-separated list of identifiers, appended to {@code out} (at least one). */
    private void parseNameList(List<String> out) {
        out.add(expect(TokenType.IDENT).text());
        while (match(TokenType.COMMA)) {
            // no closing bracket terminates this list, so a trailing comma is one whose next token
            // is not another name (the clause ends, or `constructs`/`requires` switches).
            if (!check(TokenType.IDENT)) {
                break;
            }
            out.add(expect(TokenType.IDENT).text());
        }
    }

    /**
     * {@code let name (a1, ...) = body} — a behavior's implementation (spec 13.1). Parameters are
     * bare names when the definition implements a same-named behavior (types come from it), or
     * {@code name: type} when it is a helper. The body is a single expression; {@code { ... }} is a
     * block expression (16.5). A top-level {@code let} always carries a {@code (params)} list, which
     * distinguishes it from a block-local {@code let name = expr} binding.
     */
    private Ast.FnDef parseFn() {
        Token kw = expect(TokenType.LET);
        String name = expect(TokenType.IDENT).text();
        expect(TokenType.LPAREN);
        List<Ast.FnParam> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(parseFnParam());
            while (match(TokenType.COMMA)) {
                if (check(TokenType.RPAREN)) {
                    break;
                }
                params.add(parseFnParam());
            }
        }
        expect(TokenType.RPAREN);
        // A core intrinsic declares its return type (it cannot be inferred from `intrinsic`).
        Ast.RetType declaredReturn = match(TokenType.COLON) ? parseRetType() : null;
        expect(TokenType.ASSIGN);
        // `= intrinsic "key"` names a primitive; only the core may write it (ADR-0028).
        if (isReservedNamespace(moduleName) && check(TokenType.IDENT) && peek().text().equals("intrinsic")) {
            advance();
            String key = expect(TokenType.STRING_LIT).text();
            if (declaredReturn == null) {
                throw error(kw, "intrinsic `" + name + "` must declare its return type: "
                        + "`let " + name + " (...) : <type> = intrinsic \"" + key + "\"`");
            }
            return new Ast.FnDef(name, params, declaredReturn, key, null, kw.pos());
        }
        // A helper may declare its return type: `let 深さ (s: 社員): Int = ...`. It is required only
        // when the helper recurses — the cycle can't be inferred through — and otherwise inferred, so
        // a declared return on a behavior-implementing fn (whose type comes from the behavior) is
        // rejected later, in the type checker (spec 13.1).
        Ast.Expr body;
        if (match(TokenType.LBRACE)) {
            body = parseBody();
            expect(TokenType.RBRACE);
        } else {
            body = parseExpr();
        }
        return new Ast.FnDef(name, params, declaredReturn, null, body, kw.pos());
    }

    /** A {@code fn} parameter: {@code name} (type from the behavior) or {@code name: type} (helper).
     * A helper's type may be a function type {@code (A, ...) -> B} (spec §fn-declaration). */
    private Ast.FnParam parseFnParam() {
        Token n = expect(TokenType.IDENT);
        Ast.ParamType type = match(TokenType.COLON) ? parseParamType() : null;
        return new Ast.FnParam(n.text(), type, n.pos());
    }

    /** A helper parameter's written type: a function type when it opens with {@code (}, else an
     * ordinary type. A function type is the only place a {@code (} may start a type (spec §fn-declaration). */
    private Ast.ParamType parseParamType() {
        if (check(TokenType.LPAREN)) {
            Token open = expect(TokenType.LPAREN);
            List<Ast.RetType> params = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                params.add(parseRetType());
                while (match(TokenType.COMMA)) {
                    if (check(TokenType.RPAREN)) {
                        break;
                    }
                    params.add(parseRetType());
                }
            }
            expect(TokenType.RPAREN);
            expect(TokenType.ARROW);
            return new Ast.FnType(params, parseRetType(), open.pos());
        }
        return parseRetType();
    }

    /**
     * A behavior body: {@code let} / {@code require} statements followed by a result expression,
     * desugared on the spot into one expression (spec 16.4).
     *
     * <pre>
     * let x = v      &lt;rest&gt;   =&gt;  LetIn(x, v, &lt;rest&gt;)
     * require c else f &lt;rest&gt; =&gt;  If(c, &lt;rest&gt;, f)
     * </pre>
     *
     * Recursing (rather than folding a flat list) puts {@code rest} inside the branch, so a
     * {@code let} after a {@code require} is not evaluated when the guard fails.
     */
    private Ast.Expr parseBody() {
        if (check(TokenType.LET)) {
            if (peekAt(1).type() == TokenType.LPAREN) {
                return parseTupleDestructure();
            }
            Ast.Let let = parseLet();
            return new Ast.LetIn(let.name(), let.value(), parseBody(), let.pos());
        }
        if (check(TokenType.REQUIRE)) {
            Token kw = expect(TokenType.REQUIRE);
            Ast.Expr cond = parseExpr();
            expect(TokenType.ELSE);
            Ast.Expr failure = parseExpr();
            return new Ast.If(cond, parseBody(), failure, kw.pos());
        }
        return parseExpr();
    }

    /** A pipeline stage: a behavior name. A dotted {@code Type.decoder} / {@code Type.encoder} also
     * parses here, only so {@link net.unit8.souther.compiler.check.TypeChecker#stageSig} can reject
     * it with a clear message — {@code >->} composes behaviors, not boundary codecs (spec 14.1). */
    private String parseStage() {
        String s = expect(TokenType.IDENT).text();
        if (match(TokenType.DOT)) {
            s = s + "." + expect(TokenType.IDENT).text();
        }
        return s;
    }

    private Ast.Param parseParam() {
        Token n = expect(TokenType.IDENT);
        expect(TokenType.COLON);
        return new Ast.Param(n.text(), parseRetType(), n.pos());   // may be a union: A | B
    }

    private Ast.RetType parseRetType() {
        List<Ast.TypeRef> cases = new ArrayList<>();
        cases.add(parseTypeRef());
        while (match(TokenType.PIPE)) {
            cases.add(parseTypeRef());
        }
        return new Ast.RetType(cases, cases.get(0).pos());
    }

    /** Whether {@code name} sits in the compiler-shipped {@code souther} namespace (ADR-0028). Only
     * those modules may write type variables; {@link net.unit8.souther.compiler.Compiler} keeps user
     * modules out of the namespace, so this is also what limits generics to the core. */
    private static boolean isReservedNamespace(String name) {
        return name.equals("souther") || name.startsWith("souther.");
    }

    private String qualifiedName() {
        StringBuilder sb = new StringBuilder(expect(TokenType.IDENT).text());
        while (match(TokenType.DOT)) {
            sb.append('.').append(expect(TokenType.IDENT).text());
        }
        return sb.toString();
    }

    // --- data ---

    /**
     * {@code data name = <rhs>} where the rhs is either a product body ({@code { ... }}) or a sum's
     * cases ({@code A | B}); a name with no rhs is a unit. One token after {@code =} tells the
     * product from the sum.
     */
    private Ast.Def parseDef() {
        Token kw = expect(TokenType.DATA);
        String name = expect(TokenType.IDENT).text();
        if (check(TokenType.LBRACE)) {
            throw error(peek(), "data `" + name + "` needs `=` before its body: "
                    + "`data " + name + " = { ... }`");
        }
        if (match(TokenType.ASSIGN)) {
            if (check(TokenType.LBRACE)) {
                return parseProduct(kw, name);
            }
            return parseSumOrNewtype(kw, name);
        }
        return new Ast.UnitData(name, kw.pos());
    }

    /**
     * {@code data X = A | B ...} is a sum; {@code data X = Y} (a single name, no {@code |}) is a
     * newtype over {@code Y} (spec 8.7). A sum always has a {@code |}, so a lone name can only be a
     * newtype — there is no one-case sum.
     */
    private Ast.Def parseSumOrNewtype(Token kw, String name) {
        Token first = expect(TokenType.IDENT);
        if (check(TokenType.PIPE)) {
            List<String> cases = new ArrayList<>();
            cases.add(first.text());
            while (match(TokenType.PIPE)) {
                cases.add(expect(TokenType.IDENT).text());
            }
            // decoders/encoders (incl. the sum discriminator) are derived, not written
            return new Ast.SumData(name, cases, Optional.empty(), Optional.empty(), kw.pos());
        }
        // newtype: one implicit field `value` of type Y, encoded bare (spec 8.7)
        Ast.TypeRef inner = new Ast.TypeRef(first.text(), null, first.pos());
        List<Ast.Field> fields = List.of(new Ast.Field("value", inner, first.pos()));
        Optional<Ast.Expr> invariant = Optional.empty();
        while (match(TokenType.INVARIANT)) {
            invariant = Optional.of(conjoin(invariant, parseExpr()));
        }
        return new Ast.Data(name, true, List.of(), fields, invariant,
                Optional.empty(), Optional.empty(), kw.pos());
    }

    private Ast.Data parseProduct(Token kw, String name) {
        expect(TokenType.LBRACE);

        List<String> includes = new ArrayList<>();
        List<Ast.Field> fields = new ArrayList<>();
        // The brace holds a `,`-separated list of members: a field `name: Type`, or a `...Type`
        // spread that flattens another data's fields and inherits its invariants (ADR-0012; the
        // notation follows ReScript's record type spread). A spread is not a field but sits in the
        // same comma list, conventionally leading. The `invariant` clause is not a member — it
        // follows the `}` (the type body), like a newtype's invariant follows its `= Y`.
        if (!check(TokenType.RBRACE)) {
            parseProductMember(includes, fields);
            while (match(TokenType.COMMA)) {
                if (check(TokenType.RBRACE)) {
                    break; // trailing comma before the close
                }
                parseProductMember(includes, fields);
            }
        }
        if (includes.isEmpty() && fields.isEmpty()) {
            throw error(peek(), "data `" + name + "` must declare at least one field or spread");
        }
        expect(TokenType.RBRACE);

        // an `invariant` clause follows the `}` (the type body); several lines all apply,
        // conjoined so none is silently dropped
        Optional<Ast.Expr> invariant = Optional.empty();
        while (match(TokenType.INVARIANT)) {
            invariant = Optional.of(conjoin(invariant, parseExpr()));
        }
        // decoders/encoders are derived, not written
        return new Ast.Data(name, false, includes, fields, invariant,
                Optional.empty(), Optional.empty(), kw.pos());
    }

    /** A product member: a `...Type` spread (flattened into {@code includes}) or a field. */
    private void parseProductMember(List<String> includes, List<Ast.Field> fields) {
        if (match(TokenType.SPREAD)) {
            includes.add(expect(TokenType.IDENT).text());
        } else {
            fields.add(parseField());
        }
    }

    /** Combines an accumulated invariant with the next one under {@code &&}; every line must hold. */
    private Ast.Expr conjoin(Optional<Ast.Expr> acc, Ast.Expr next) {
        return acc.map(prev -> (Ast.Expr) new Ast.Binary(Ast.BinOp.AND, prev, next, next.pos()))
                .orElse(next);
    }

    private Ast.Field parseField() {
        Token n = expect(TokenType.IDENT);
        expect(TokenType.COLON);
        Ast.TypeRef type = parseTypeRef();
        if (match(TokenType.QUESTION)) {
            // `T?` desugars to Option<T> (spec 7.4); `?` is only field optionality
            type = new Ast.TypeRef("Option", type, type.pos());
        }
        return new Ast.Field(n.text(), type, n.pos());
    }

    private Ast.TypeRef parseTypeRef() {
        if (check(TokenType.LPAREN)) {
            // A tuple type `(A, B, ...)` (ADR-0036): a type argument (`List<(String, 'a)>`) or a
            // helper/stdlib return type. A bare tuple parameter is not reached here — a helper
            // parameter's `(` is read as a function type (parseParamType).
            Token open = advance();
            List<Ast.TypeRef> elems = new ArrayList<>();
            elems.add(parseTypeRef());
            while (match(TokenType.COMMA)) {
                if (check(TokenType.RPAREN)) {
                    break;
                }
                elems.add(parseTypeRef());
            }
            expect(TokenType.RPAREN);
            if (elems.size() < 2) {
                throw error(open, "a tuple type needs at least two elements; `(T)` is not a type");
            }
            return new Ast.TypeRef(null, null, elems, open.pos());
        }
        if (check(TokenType.TYPEVAR)) {
            Token v = advance();
            if (!isReservedNamespace(moduleName)) {
                throw error(v, "type variable `" + v.text() + "` is only allowed in the core "
                        + "(the reserved `souther` namespace); a user model stays bounded (ADR-0028)");
            }
            return new Ast.TypeRef(v.text(), null, v.pos());   // name begins with `'` → Type.Var
        }
        Token n = expect(TokenType.IDENT);
        if (n.text().equals("List") && check(TokenType.LT)) {
            advance();
            Ast.TypeRef arg = parseTypeRef();
            expect(TokenType.GT);
            return new Ast.TypeRef("List", arg, n.pos());
        }
        if (n.text().equals("Set") && check(TokenType.LT)) {
            advance();
            Ast.TypeRef arg = parseTypeRef();
            expect(TokenType.GT);
            return new Ast.TypeRef("Set", arg, n.pos());
        }
        if (n.text().equals("Map") && check(TokenType.LT)) {
            advance();
            Ast.TypeRef key = parseTypeRef();
            expect(TokenType.COMMA);
            Ast.TypeRef value = parseTypeRef();
            expect(TokenType.GT);
            // carry the value in `arg` and the key in `tupleElems` (ADR-0040); the checker validates
            // the key is String or a String-backed newtype.
            return new Ast.TypeRef("Map", value, List.of(key), n.pos());
        }
        return new Ast.TypeRef(n.text(), null, n.pos());
    }

    private Ast.Let parseLet() {
        Token kw = expect(TokenType.LET);
        String name = expect(TokenType.IDENT).text();
        expect(TokenType.ASSIGN);
        Ast.Expr value = parseExpr();
        return new Ast.Let(name, value, kw.pos());
    }

    /** {@code let (x, y) = t <rest>} desugars to a whole binding plus positional element reads
     * (ADR-0036): {@code let $t = t in let x = $t.0 in let y = $t.1 in <rest>}. */
    private Ast.Expr parseTupleDestructure() {
        Token kw = expect(TokenType.LET);
        expect(TokenType.LPAREN);
        List<String> names = new ArrayList<>();
        names.add(expect(TokenType.IDENT).text());
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RPAREN)) {
                break;
            }
            names.add(expect(TokenType.IDENT).text());
        }
        expect(TokenType.RPAREN);
        expect(TokenType.ASSIGN);
        Ast.Expr value = parseExpr();
        Ast.Expr body = parseBody();
        if (names.size() == 1) {
            return new Ast.LetIn(names.get(0), value, body, kw.pos());   // `let (x) = e` is `let x = e`
        }
        String whole = "$t" + (tupleCounter++);
        for (int i = names.size() - 1; i >= 0; i--) {
            body = new Ast.LetIn(names.get(i),
                    new Ast.TupleGet(new Ast.Var(whole, kw.pos()), i, names.size(), kw.pos()), body, kw.pos());
        }
        return new Ast.LetIn(whole, value, body, kw.pos());
    }

    private Ast.Expr newData(Token type) {
        expect(TokenType.LBRACE);
        Inits in = parseInits();
        expect(TokenType.RBRACE);
        return new Ast.NewData(type.text(), in.fields(), in.spreads(), type.pos());
    }

    private record Inits(List<Ast.FieldInit> fields, List<String> spreads) {}

    private Inits parseInits() {
        List<Ast.FieldInit> fields = new ArrayList<>();
        List<String> spreads = new ArrayList<>();
        if (!check(TokenType.RBRACE)) {
            parseInitElem(fields, spreads);
            while (match(TokenType.COMMA)) {
                if (check(TokenType.RBRACE)) {
                    break; // trailing comma
                }
                parseInitElem(fields, spreads);
            }
        }
        return new Inits(fields, spreads);
    }

    private void parseInitElem(List<Ast.FieldInit> fields, List<String> spreads) {
        if (match(TokenType.SPREAD)) {
            spreads.add(expect(TokenType.IDENT).text());
        } else {
            fields.add(parseFieldInit());
        }
    }

    private Ast.FieldInit parseFieldInit() {
        Token n = expect(TokenType.IDENT);
        if (match(TokenType.ASSIGN)) {
            // a literal binds a value to a field, so it is `=` (a definition), not `:` (a type) —
            // ADR-0026. The field's type lives in the `data` declaration (spec 12.4).
            return new Ast.FieldInit(n.text(), parseExpr(), n.pos());
        }
        // shorthand: `field` means `field = field`
        return new Ast.FieldInit(n.text(), new Ast.Var(n.text(), n.pos()), n.pos());
    }

    // --- expressions ---

    private Ast.Expr parseExpr() {
        return parsePipe();
    }

    /** The value pipe {@code |>} binds looser than everything else and is left-associative, so
     * {@code a |> f |> g} reads {@code g(f(a))}. It desugars here, at parse time (like {@code require}
     * → {@code if}), so no later pass sees a pipe: the left operand becomes the last argument of the
     * right-hand call. {@code e |> f(a)} is {@code f(a, e)}, {@code e |> f} is {@code f(e)}. */
    private Ast.Expr parsePipe() {
        Ast.Expr left = parseOr();
        while (check(TokenType.VPIPE)) {
            advance();
            Ast.Expr right = parseOr();
            if (right instanceof Ast.Call c) {
                List<Ast.Expr> args = new ArrayList<>(c.args());
                args.add(left);
                left = new Ast.Call(c.fn(), args, c.pos());
            } else if (right instanceof Ast.Var v) {
                left = new Ast.Call(v.name(), List.of(left), v.pos());
            } else if (right instanceof Ast.FieldAccess fa && fa.target() instanceof Ast.Var base) {
                // a qualified function name written without parens, e.g. `x |> List.sort`: a dotted
                // name with no `(` parsed as a field access, but in pipe position it names the
                // function to call, folded into `Module.name` as a parenthesized call would be.
                left = new Ast.Call(base.name() + "." + fa.field(), List.of(left), fa.pos());
            } else {
                throw new CompileException(right.pos(),
                        "the right side of `|>` must be a function call or a function name");
            }
        }
        return left;
    }

    private Ast.Expr parseOr() {
        Ast.Expr left = parseAnd();
        while (check(TokenType.OR)) {
            Token op = advance();
            left = new Ast.Binary(Ast.BinOp.OR, left, parseAnd(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseAnd() {
        Ast.Expr left = parseCmp();
        while (check(TokenType.AND)) {
            Token op = advance();
            left = new Ast.Binary(Ast.BinOp.AND, left, parseCmp(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseCmp() {
        Ast.Expr left = parseAdd();
        Ast.BinOp op = switch (peek().type()) {
            case EQ -> Ast.BinOp.EQ;
            case NE -> Ast.BinOp.NE;
            case LT -> Ast.BinOp.LT;
            case LE -> Ast.BinOp.LE;
            case GT -> Ast.BinOp.GT;
            case GE -> Ast.BinOp.GE;
            default -> null;
        };
        if (op == null) {
            return left;
        }
        Token t = advance();
        return new Ast.Binary(op, left, parseAdd(), t.pos());
    }

    private Ast.Expr parseAdd() {
        Ast.Expr left = parseMul();
        while (check(TokenType.PLUS) || check(TokenType.MINUS) || check(TokenType.PLUSPLUS)) {
            Token op = advance();
            Ast.BinOp o = switch (op.type()) {
                case PLUS -> Ast.BinOp.ADD;
                case MINUS -> Ast.BinOp.SUB;
                default -> Ast.BinOp.CONCAT;      // ++
            };
            left = new Ast.Binary(o, left, parseMul(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseMul() {
        Ast.Expr left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            Token op = advance();
            Ast.BinOp binOp = op.type() == TokenType.STAR ? Ast.BinOp.MUL : Ast.BinOp.DIV;
            left = new Ast.Binary(binOp, left, parseUnary(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseUnary() {
        if (check(TokenType.MINUS)) {
            Token op = advance();
            return new Ast.Neg(parseUnary(), op.pos());
        }
        return parsePrimary();
    }

    private Ast.Expr parsePrimary() {
        Token t = peek();
        // a lambda used as a value: `x -> e` or `(x, ...) -> e` (spec §blocks). The same block form
        // that a combinator argument takes, now allowed anywhere an expression is.
        if (t.type() == TokenType.IDENT && peekAt(1).type() == TokenType.ARROW) {
            advance();
            expect(TokenType.ARROW);
            return new Ast.Block(List.of(t.text()), parseExpr(), t.pos());
        }
        if (t.type() == TokenType.LPAREN && isBlockParams()) {
            return parseParenLambda();
        }
        switch (t.type()) {
            case MATCH -> {
                return parseMatch();
            }
            case IF -> {
                return parseIf();
            }
            case INT_LIT -> {
                advance();
                return new Ast.IntLit(Long.parseLong(t.text()), t.pos());
            }
            case DECIMAL_LIT -> {
                advance();
                return new Ast.DecimalLit(new java.math.BigDecimal(t.text()), t.pos());
            }
            case STRING_LIT -> {
                advance();
                return new Ast.StringLit(t.text(), t.pos());
            }
            case TRUE -> {
                advance();
                return new Ast.BoolLit(true, t.pos());
            }
            case FALSE -> {
                advance();
                return new Ast.BoolLit(false, t.pos());
            }
            case LPAREN -> {
                advance();
                Ast.Expr first = parseExpr();
                if (check(TokenType.COMMA)) {
                    // a tuple `(e1, e2, ...)` of two or more values (ADR-0036). A lambda `(a, b) -> ...`
                    // is taken earlier by isBlockParams, so a comma here starts a tuple, not params.
                    List<Ast.Expr> elements = new ArrayList<>();
                    elements.add(first);
                    while (match(TokenType.COMMA)) {
                        if (check(TokenType.RPAREN)) {
                            break;
                        }
                        elements.add(parseExpr());
                    }
                    expect(TokenType.RPAREN);
                    return new Ast.Tuple(elements, t.pos());
                }
                expect(TokenType.RPAREN);
                return first;
            }
            case LBRACKET -> {
                return parseList();
            }
            case LBRACE -> {
                // a bare `{` is a block expression (spec 16.5); it desugars via parseBody. A record
                // literal is never bare — it is prefixed by a type name (12.4), handled under IDENT.
                advance();
                Ast.Expr e = parseBody();
                expect(TokenType.RBRACE);
                return e;
            }
            case IDENT -> {
                advance();
                if (check(TokenType.LPAREN)) {
                    return call(t.text(), t.pos());
                }
                if (!noConstruct && check(TokenType.LBRACE)) {
                    return newData(t);
                }
                // A qualified stdlib call `Module.name(args)` — e.g. `List.map(...)`, `String.trim(...)`.
                // The module qualifier is folded into the call's function name as `Module.name`, since
                // an identifier never contains a dot (spec §stdlib). A dotted chain that is NOT followed
                // by `(` is an ordinary field access (`会員.id.value`), handled below.
                if (check(TokenType.DOT) && peekAt(1).type() == TokenType.IDENT
                        && peekAt(2).type() == TokenType.LPAREN) {
                    expect(TokenType.DOT);
                    Token name = expect(TokenType.IDENT);
                    return call(t.text() + "." + name.text(), t.pos());
                }
                Ast.Expr base = new Ast.Var(t.text(), t.pos());
                while (match(TokenType.DOT)) {
                    Token field = expect(TokenType.IDENT);
                    base = new Ast.FieldAccess(base, field.text(), field.pos());
                }
                return base;
            }
            default -> throw error(t, "expected an expression");
        }
    }

    /** {@code [e1, e2, ...]} (a literal) or {@code [element | guard, ...]} (a guard comprehension). */
    private Ast.Expr parseList() {
        Token lb = expect(TokenType.LBRACKET);
        if (check(TokenType.RBRACKET)) {
            advance();   // the empty list `[]`; its element type is fixed by context (ADR-0028)
            return new Ast.ListLit(new ArrayList<>(), lb.pos());
        }
        Ast.Expr first = parseExpr();
        if (match(TokenType.PIPE)) {
            List<Ast.Expr> guards = new ArrayList<>();
            guards.add(parseExpr());
            while (match(TokenType.COMMA)) {
                if (check(TokenType.RBRACKET)) {
                    break;
                }
                guards.add(parseExpr());
            }
            expect(TokenType.RBRACKET);
            return new Ast.ListComp(first, guards, lb.pos());
        }
        List<Ast.Expr> elems = new ArrayList<>();
        elems.add(first);
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RBRACKET)) {
                break;
            }
            elems.add(parseExpr());
        }
        expect(TokenType.RBRACKET);
        return new Ast.ListLit(elems, lb.pos());
    }

    private Ast.Expr parseMatch() {
        Token kw = expect(TokenType.MATCH);
        boolean saved = noConstruct;
        noConstruct = true;
        Ast.Expr scrutinee = parseExpr();
        noConstruct = saved;
        expect(TokenType.WITH);
        // F#-form cases: `| A [| B ...] [as x] -> body`, one case per leading `|`
        // (the leading `|` on the first case is optional). The `->` delimits a case's
        // or-pattern alternatives from the case separator, so the two uses of `|` never
        // clash even though Souther is layout-independent.
        List<Ast.Case> cases = new ArrayList<>();
        match(TokenType.PIPE); // optional leading `|` before the first case
        do {
            Token caseName = expect(TokenType.IDENT);
            // `A | B ... [as x] -> body` — one case, or several joined by `|` (spec 16.3)
            List<String> caseTypes = new ArrayList<>();
            caseTypes.add(caseName.text());
            while (match(TokenType.PIPE)) {
                caseTypes.add(expect(TokenType.IDENT).text());
            }
            // `Some v` binds Option's wrapped value positionally (F#/Elm; spec §match). Option is the
            // one built-in case with an anonymous payload, so `as` — which binds the whole matched
            // value everywhere else — is not how you reach it.
            boolean isSome = caseTypes.size() == 1 && caseTypes.get(0).equals("Some");
            String someBinding = isSome && check(TokenType.IDENT) ? advance().text() : null;
            // field destructuring: `Case { field [= var], ... }`, mirroring record construction and
            // reusing the record literal's `,`/`=`/`{ f }` shorthand. Only a single named case can be
            // destructured — an or-pattern binds the sum type and has no case fields (spec §match).
            List<String> fieldNames = new ArrayList<>();
            List<String> fieldVars = new ArrayList<>();
            if (check(TokenType.LBRACE)) {
                if (caseTypes.size() > 1) {
                    throw error(peek(), "an or-pattern binds the sum type and cannot destructure a case's fields");
                }
                advance();  // `{`
                if (!check(TokenType.RBRACE)) {
                    do {
                        Token field = expect(TokenType.IDENT);
                        fieldNames.add(field.text());
                        fieldVars.add(match(TokenType.ASSIGN) ? expect(TokenType.IDENT).text() : field.text());
                    } while (match(TokenType.COMMA));
                }
                expect(TokenType.RBRACE);
            }
            String binding;
            if (someBinding != null) {
                binding = someBinding;
            } else if (isSome && check(TokenType.AS)) {
                throw error(peek(), "Option's wrapped value is bound positionally: write `| Some v`, not `| Some as v`");
            } else {
                binding = match(TokenType.AS) ? expect(TokenType.IDENT).text() : null;
            }
            expect(TokenType.ARROW);
            Ast.Expr body = parseExpr();
            // desugar the destructuring: bind the whole case (the `as` name, or a fresh synthetic),
            // then read each field into its variable with a `let` wrapping the body. The type checker
            // then validates the field reads and the backend needs no match-specific handling.
            if (!fieldNames.isEmpty()) {
                String whole = binding != null ? binding : "$m" + (matchWholeCounter++);
                for (int i = fieldNames.size() - 1; i >= 0; i--) {
                    body = new Ast.LetIn(fieldVars.get(i),
                            new Ast.FieldAccess(new Ast.Var(whole, caseName.pos()), fieldNames.get(i), caseName.pos()),
                            body, caseName.pos());
                }
                binding = whole;
            }
            cases.add(new Ast.Case(caseTypes, binding, body, caseName.pos()));
        } while (match(TokenType.PIPE)); // a leading `|` starts the next case
        return new Ast.Match(scrutinee, cases, kw.pos());
    }

    private Ast.Expr parseIf() {
        Token kw = expect(TokenType.IF);
        Ast.Expr cond = parseExpr();
        expect(TokenType.THEN);
        Ast.Expr then = parseExpr();
        expect(TokenType.ELSE);
        Ast.Expr els = parseExpr();
        return new Ast.If(cond, then, els, kw.pos());
    }

    private Ast.Expr call(String fn, SourcePos pos) {
        expect(TokenType.LPAREN);
        List<Ast.Expr> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(parseArg());
            while (match(TokenType.COMMA)) {
                if (check(TokenType.RPAREN)) {
                    break;
                }
                args.add(parseArg());
            }
        }
        expect(TokenType.RPAREN);
        return new Ast.Call(fn, args, pos);
    }

    /**
     * An argument: an expression, or a lambda block (spec 12.5). A lambda also parses as a primary
     * expression (see {@link #parsePrimary}); this site just lets a bare {@code (a, b) -> ...} sit
     * directly in an argument list without extra parentheses.
     */
    private Ast.Expr parseArg() {
        // x -> body
        if (check(TokenType.IDENT) && peekAt(1).type() == TokenType.ARROW) {
            Token p = expect(TokenType.IDENT);
            expect(TokenType.ARROW);
            return new Ast.Block(List.of(p.text()), parseExpr(), p.pos());
        }
        // (acc, x) -> body
        if (check(TokenType.LPAREN) && isBlockParams()) {
            return parseParenLambda();
        }
        return parseExpr();
    }

    /** {@code (x, ...) -> body} — a parenthesised lambda; the caller has confirmed the shape. */
    private Ast.Block parseParenLambda() {
        Token open = expect(TokenType.LPAREN);
        List<String> params = new ArrayList<>();
        params.add(expect(TokenType.IDENT).text());
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RPAREN)) {
                break;
            }
            params.add(expect(TokenType.IDENT).text());
        }
        expect(TokenType.RPAREN);
        expect(TokenType.ARROW);
        return new Ast.Block(params, parseExpr(), open.pos());
    }

    /** Distinguishes {@code (a, b) ->} from a parenthesised expression by scanning to the `)`. */
    private boolean isBlockParams() {
        int i = 1;
        if (peekAt(i).type() != TokenType.IDENT) {
            return false;
        }
        i++;
        while (peekAt(i).type() == TokenType.COMMA) {
            i++;
            if (peekAt(i).type() == TokenType.RPAREN) {
                break;   // a trailing comma: `(a, b,) -> ...`
            }
            if (peekAt(i).type() != TokenType.IDENT) {
                return false;
            }
            i++;
        }
        return peekAt(i).type() == TokenType.RPAREN
                && peekAt(i + 1).type() == TokenType.ARROW;
    }

    // --- token helpers ---

    private Token peek() {
        return tokens.get(index);
    }

    private Token peekAt(int ahead) {
        return tokens.get(Math.min(index + ahead, tokens.size() - 1));
    }

    private Token advance() {
        Token t = tokens.get(index);
        if (t.type() != TokenType.EOF) {
            index++;
        }
        return t;
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(TokenType type) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), "expected " + type + " but found " + peek().type());
    }

    private CompileException error(Token t, String message) {
        return new CompileException(t.pos(), message);
    }
}
