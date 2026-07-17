package net.unit8.souther.compiler.syntax;

import net.unit8.souther.compiler.CompileException;
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
     * {@code exposing { name, name : A | B, ... }} — the module's public surface (spec 4). An
     * exposed composition behavior carries its output signature here ({@code name : A | B}, spec
     * 14.5); it is collected into {@code outputs} keyed by name. Other entries are bare names.
     */
    private List<String> parseExposing(Map<String, Ast.RetType> outputs) {
        expect(TokenType.EXPOSING);
        expect(TokenType.LBRACE);
        List<String> names = new ArrayList<>();
        parseExposedEntry(names, outputs);
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RBRACE)) {
                break;
            }
            parseExposedEntry(names, outputs);
        }
        expect(TokenType.RBRACE);
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

    /** {@code import <module> { name, ... }} — explicit imports only (no wildcards, spec 4). */
    private Ast.Import parseImport() {
        Token kw = expect(TokenType.IMPORT);
        String module = qualifiedName();
        expect(TokenType.LBRACE);
        List<String> names = new ArrayList<>();
        names.add(expect(TokenType.IDENT).text());
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RBRACE)) {
                break;
            }
            names.add(expect(TokenType.IDENT).text());
        }
        expect(TokenType.RBRACE);
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
        // an optional trailing `-> arms` declares the composition's output (spec 14.5); the
        // stages are behavior names, so this `->` reads unambiguously as the pipeline's own
        Ast.RetType declaredOut = match(TokenType.ARROW) ? parseRetType() : null;
        return new Ast.PipeBehavior(name, stages, declaredOut, kw.pos());
    }

    /** A comma-separated list of identifiers, appended to {@code out} (at least one). */
    private void parseNameList(List<String> out) {
        out.add(expect(TokenType.IDENT).text());
        while (match(TokenType.COMMA)) {
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
                params.add(parseFnParam());
            }
        }
        expect(TokenType.RPAREN);
        expect(TokenType.ASSIGN);
        Ast.Expr body;
        if (match(TokenType.LBRACE)) {
            body = parseBody();
            expect(TokenType.RBRACE);
        } else {
            body = parseExpr();
        }
        return new Ast.FnDef(name, params, body, kw.pos());
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
        List<Ast.TypeRef> arms = new ArrayList<>();
        arms.add(parseTypeRef());
        while (match(TokenType.PIPE)) {
            arms.add(parseTypeRef());
        }
        return new Ast.RetType(arms, arms.get(0).pos());
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
     * arms ({@code A | B}); a name with no rhs is a unit. One token after {@code =} tells the
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
     * newtype — there is no one-arm sum.
     */
    private Ast.Def parseSumOrNewtype(Token kw, String name) {
        Token first = expect(TokenType.IDENT);
        if (check(TokenType.PIPE)) {
            List<String> arms = new ArrayList<>();
            arms.add(first.text());
            while (match(TokenType.PIPE)) {
                arms.add(expect(TokenType.IDENT).text());
            }
            // decoders/encoders (incl. the sum discriminator) are derived, not written
            return new Ast.SumData(name, arms, Optional.empty(), Optional.empty(), kw.pos());
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
        while (check(TokenType.INCLUDE) || check(TokenType.IDENT)) {
            if (match(TokenType.INCLUDE)) {
                includes.add(expect(TokenType.IDENT).text());
            } else {
                fields.add(parseField());
            }
        }
        if (includes.isEmpty() && fields.isEmpty()) {
            throw error(peek(), "data `" + name + "` must declare at least one field or include");
        }

        Optional<Ast.Expr> invariant = Optional.empty();
        while (!check(TokenType.RBRACE)) {
            if (check(TokenType.INVARIANT)) {
                advance();
                // several `invariant` lines all apply: conjoin them so none is silently dropped
                invariant = Optional.of(conjoin(invariant, parseExpr()));
            } else {
                throw error(peek(), "expected invariant or '}'");
            }
        }
        expect(TokenType.RBRACE);
        // decoders/encoders are derived, not written
        return new Ast.Data(name, false, includes, fields, invariant,
                Optional.empty(), Optional.empty(), kw.pos());
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
        Token n = expect(TokenType.IDENT);
        if (n.text().equals("List") && check(TokenType.LT)) {
            advance();
            Ast.TypeRef arg = parseTypeRef();
            expect(TokenType.GT);
            return new Ast.TypeRef("List", arg, n.pos());
        }
        if (n.text().equals("Map") && check(TokenType.LT)) {
            advance();
            Ast.TypeRef key = parseTypeRef();
            if (!key.name().equals("String") || key.arg() != null) {
                throw error(peek(), "Map key type must be String");
            }
            expect(TokenType.COMMA);
            Ast.TypeRef value = parseTypeRef();
            expect(TokenType.GT);
            return new Ast.TypeRef("Map", value, n.pos());   // key is always String; carry the value type
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
        if (match(TokenType.DOTDOT)) {
            spreads.add(expect(TokenType.IDENT).text());
        } else {
            fields.add(parseFieldInit());
        }
    }

    private Ast.FieldInit parseFieldInit() {
        Token n = expect(TokenType.IDENT);
        if (match(TokenType.COLON)) {
            return new Ast.FieldInit(n.text(), parseExpr(), n.pos());
        }
        // shorthand: `field` means `field: field`
        return new Ast.FieldInit(n.text(), new Ast.Var(n.text(), n.pos()), n.pos());
    }

    // --- expressions ---

    private Ast.Expr parseExpr() {
        return parseOr();
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
        while (check(TokenType.STAR)) {
            Token op = advance();
            left = new Ast.Binary(Ast.BinOp.MUL, left, parseUnary(), op.pos());
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
                Ast.Expr e = parseExpr();
                expect(TokenType.RPAREN);
                return e;
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
                    return call(t);
                }
                if (!noConstruct && check(TokenType.LBRACE)) {
                    return newData(t);
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
        Ast.Expr first = parseExpr();
        if (match(TokenType.PIPE)) {
            List<Ast.Expr> guards = new ArrayList<>();
            guards.add(parseExpr());
            while (match(TokenType.COMMA)) {
                guards.add(parseExpr());
            }
            expect(TokenType.RBRACKET);
            return new Ast.ListComp(first, guards, lb.pos());
        }
        List<Ast.Expr> elems = new ArrayList<>();
        elems.add(first);
        while (match(TokenType.COMMA)) {
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
        // F#-form arms: `| A [| B ...] [as x] -> body`, one arm per leading `|`
        // (the leading `|` on the first arm is optional). The `->` delimits an arm's
        // or-pattern alternatives from the arm separator, so the two uses of `|` never
        // clash even though Souther is layout-independent.
        List<Ast.Case> cases = new ArrayList<>();
        match(TokenType.PIPE); // optional leading `|` before the first arm
        do {
            Token arm = expect(TokenType.IDENT);
            // `A | B ... [as x] -> body` — one arm, or several joined by `|` (spec 16.3)
            List<String> armTypes = new ArrayList<>();
            armTypes.add(arm.text());
            while (match(TokenType.PIPE)) {
                armTypes.add(expect(TokenType.IDENT).text());
            }
            String binding = match(TokenType.AS) ? expect(TokenType.IDENT).text() : null;
            expect(TokenType.ARROW);
            Ast.Expr body = parseExpr();
            cases.add(new Ast.Case(armTypes, binding, body, arm.pos()));
        } while (match(TokenType.PIPE)); // a leading `|` starts the next arm
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

    private Ast.Expr call(Token name) {
        expect(TokenType.LPAREN);
        List<Ast.Expr> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(parseArg());
            while (match(TokenType.COMMA)) {
                args.add(parseArg());
            }
        }
        expect(TokenType.RPAREN);
        return new Ast.Call(name.text(), args, name.pos());
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
