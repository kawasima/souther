package net.unit8.souther.compiler.syntax;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.List;
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
        List<Ast.Def> defs = new ArrayList<>();
        List<Ast.BehaviorDef> behaviors = new ArrayList<>();
        List<Ast.RequiredBehavior> requireds = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            if (check(TokenType.DATA)) {
                defs.add(parseDef());
            } else if (check(TokenType.BEHAVIOR)) {
                behaviors.add(parseBehavior());
            } else if (check(TokenType.REQUIRED)) {
                requireds.add(parseRequired());
            } else {
                throw error(peek(), "expected data, behavior, or required");
            }
        }
        return new Ast.Module(name, defs, behaviors, requireds, m.pos());
    }

    private Ast.BehaviorDef parseBehavior() {
        Token kw = expect(TokenType.BEHAVIOR);
        String name = expect(TokenType.IDENT).text();
        if (match(TokenType.ASSIGN)) {
            List<String> stages = new ArrayList<>();
            stages.add(expect(TokenType.IDENT).text());
            while (match(TokenType.GTGT)) {
                stages.add(expect(TokenType.IDENT).text());
            }
            return new Ast.PipeBehavior(name, stages, kw.pos());
        }
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
        List<String> constructs = new ArrayList<>();
        if (match(TokenType.CONSTRUCTS)) {
            constructs.add(expect(TokenType.IDENT).text());
            while (match(TokenType.COMMA)) {
                constructs.add(expect(TokenType.IDENT).text());
            }
        }
        expect(TokenType.LBRACE);
        List<Ast.BStmt> stmts = new ArrayList<>();
        while (check(TokenType.LET) || check(TokenType.REQUIRE)) {
            stmts.add(check(TokenType.LET) ? parseLet() : parseGuard());
        }
        Ast.Expr result = parseExpr();
        expect(TokenType.RBRACE);
        return new Ast.BodyBehavior(name, params, ret, constructs, stmts, result, kw.pos());
    }

    private Ast.Param parseParam() {
        Token n = expect(TokenType.IDENT);
        expect(TokenType.COLON);
        return new Ast.Param(n.text(), parseTypeRef(), n.pos());
    }

    private Ast.RetType parseRetType() {
        if (check(TokenType.IDENT) && peek().text().equals("Result") && peekAt(1).type() == TokenType.LT) {
            Token kw = advance();
            expect(TokenType.LT);
            Ast.TypeRef succ = parseTypeRef();
            expect(TokenType.COMMA);
            Ast.TypeRef err = parseTypeRef();
            expect(TokenType.GT);
            return new Ast.RetType(succ, Optional.of(err), kw.pos());
        }
        Ast.TypeRef s = parseTypeRef();
        return new Ast.RetType(s, Optional.empty(), s.pos());
    }

    private Ast.Guard parseGuard() {
        Token kw = expect(TokenType.REQUIRE);
        Ast.Expr cond = parseExpr();
        expect(TokenType.ELSE);
        Ast.Expr failure = parseExpr();
        return new Ast.Guard(cond, failure, kw.pos());
    }

    private Ast.RequiredBehavior parseRequired() {
        Token kw = expect(TokenType.REQUIRED);
        expect(TokenType.BEHAVIOR);
        String name = expect(TokenType.IDENT).text();
        expect(TokenType.LPAREN);
        // required behavior takes one input: (name: Type) or (Type)
        if (peekAt(1).type() == TokenType.COLON) {
            expect(TokenType.IDENT);
            expect(TokenType.COLON);
        }
        Ast.TypeRef paramType = parseTypeRef();
        expect(TokenType.RPAREN);
        expect(TokenType.ARROW);
        Ast.RetType ret = parseRetType();
        return new Ast.RequiredBehavior(name, paramType, ret, kw.pos());
    }

    private String qualifiedName() {
        StringBuilder sb = new StringBuilder(expect(TokenType.IDENT).text());
        while (match(TokenType.DOT)) {
            sb.append('.').append(expect(TokenType.IDENT).text());
        }
        return sb.toString();
    }

    // --- data ---

    private Ast.Def parseDef() {
        Token kw = expect(TokenType.DATA);
        String name = expect(TokenType.IDENT).text();
        if (match(TokenType.ASSIGN)) {
            return parseSum(kw, name);
        }
        if (check(TokenType.LBRACE)) {
            return parseProduct(kw, name);
        }
        return new Ast.UnitData(name, kw.pos());
    }

    private Ast.SumData parseSum(Token kw, String name) {
        List<String> arms = new ArrayList<>();
        arms.add(expect(TokenType.IDENT).text());
        while (match(TokenType.PIPE)) {
            arms.add(expect(TokenType.IDENT).text());
        }
        Optional<Ast.Discriminate> decoder = Optional.empty();
        if (match(TokenType.LBRACE)) {
            decoder = Optional.of(parseDiscriminate());
            expect(TokenType.RBRACE);
        }
        return new Ast.SumData(name, arms, decoder, kw.pos());
    }

    private Ast.Discriminate parseDiscriminate() {
        expect(TokenType.DECODER);
        expect(TokenType.FROM);
        contextual("Object");
        Token disc = peek();
        contextual("discriminate");
        contextual("on");
        String key = expect(TokenType.STRING_LIT).text();
        expect(TokenType.LBRACE);
        List<Ast.Variant> variants = new ArrayList<>();
        while (check(TokenType.STRING_LIT)) {
            Token tag = advance();
            expect(TokenType.FATARROW);
            Token arm = expect(TokenType.IDENT);
            expect(TokenType.DOT);
            expect(TokenType.DECODER);
            variants.add(new Ast.Variant(tag.text(), arm.text(), tag.pos()));
        }
        expect(TokenType.RBRACE);
        return new Ast.Discriminate(key, variants, disc.pos());
    }

    /** Consumes an identifier that must have the given text (a contextual keyword). */
    private void contextual(String word) {
        Token t = peek();
        if (t.type() != TokenType.IDENT || !t.text().equals(word)) {
            throw error(t, "expected `" + word + "`");
        }
        advance();
    }

    private Ast.Data parseProduct(Token kw, String name) {
        expect(TokenType.LBRACE);

        List<Ast.Field> fields = new ArrayList<>();
        while (check(TokenType.IDENT)) {
            fields.add(parseField());
        }
        if (fields.isEmpty()) {
            throw error(peek(), "data `" + name + "` must declare at least one field");
        }

        Optional<Ast.Expr> invariant = Optional.empty();
        Optional<Ast.DecoderDef> decoder = Optional.empty();
        Optional<Ast.EncoderDef> encoder = Optional.empty();
        while (!check(TokenType.RBRACE)) {
            if (check(TokenType.INVARIANT)) {
                advance();
                invariant = Optional.of(parseExpr());
            } else if (check(TokenType.DECODER)) {
                decoder = Optional.of(parseDecoder());
            } else if (check(TokenType.ENCODER)) {
                encoder = Optional.of(parseEncoder());
            } else {
                throw error(peek(), "expected invariant, decoder, encoder, or '}'");
            }
        }
        expect(TokenType.RBRACE);
        return new Ast.Data(name, fields, invariant, decoder, encoder, kw.pos());
    }

    private Ast.Field parseField() {
        Token n = expect(TokenType.IDENT);
        expect(TokenType.COLON);
        return new Ast.Field(n.text(), parseTypeRef(), n.pos());
    }

    private Ast.TypeRef parseTypeRef() {
        Token n = expect(TokenType.IDENT);
        if (n.text().equals("List") && check(TokenType.LT)) {
            advance();
            Ast.TypeRef arg = parseTypeRef();
            expect(TokenType.GT);
            return new Ast.TypeRef("List", arg, n.pos());
        }
        return new Ast.TypeRef(n.text(), null, n.pos());
    }

    // --- decoder ---

    private Ast.DecoderDef parseDecoder() {
        Token kw = expect(TokenType.DECODER);
        expect(TokenType.FROM);
        Token from = expect(TokenType.IDENT);
        if (from.text().equals("Object")) {
            return parseObjectDecoder(kw);
        }
        Ast.RawKind kind = rawKind(from);
        expect(TokenType.AS);
        String input = expect(TokenType.IDENT).text();
        expect(TokenType.LBRACE);
        List<Ast.DecStmt> stmts = new ArrayList<>();
        while (check(TokenType.LET) || check(TokenType.REQUIRE)) {
            stmts.add(check(TokenType.LET) ? parseLet() : parseRequire());
        }
        Ast.Construct result = parseConstruct();
        expect(TokenType.RBRACE);
        return new Ast.PrimDecoder(kind, input, stmts, result, kw.pos());
    }

    private Ast.ObjectDecoder parseObjectDecoder(Token kw) {
        expect(TokenType.LBRACE);
        List<Ast.Bind> binds = new ArrayList<>();
        while (check(TokenType.IDENT) && peekAt(1).type() == TokenType.LARROW) {
            binds.add(parseBind());
        }
        Ast.Construct result = parseConstruct();
        expect(TokenType.RBRACE);
        return new Ast.ObjectDecoder(binds, result, kw.pos());
    }

    private Ast.Bind parseBind() {
        Token name = expect(TokenType.IDENT);
        expect(TokenType.LARROW);
        Token fn = expect(TokenType.IDENT);
        if (!fn.text().equals("field")) {
            throw error(fn, "expected `field(...)` on the right of `<-`");
        }
        expect(TokenType.LPAREN);
        String key = expect(TokenType.STRING_LIT).text();
        expect(TokenType.COMMA);
        Ast.DecRef ref = parseDecRef();
        expect(TokenType.RPAREN);
        return new Ast.Bind(name.text(), key, ref, name.pos());
    }

    private Ast.DecRef parseDecRef() {
        Token t = expect(TokenType.IDENT);
        if (match(TokenType.DOT)) {
            // `.decoder`: `decoder` lexes as a keyword, not an identifier
            if (!check(TokenType.DECODER)) {
                throw error(peek(), "expected `" + t.text() + ".decoder`");
            }
            advance();
            return new Ast.DataDecRef(t.text(), t.pos());
        }
        return switch (t.text()) {
            case "string" -> new Ast.PrimDecRef(Ast.PrimKind.STRING, t.pos());
            case "int" -> new Ast.PrimDecRef(Ast.PrimKind.INT, t.pos());
            case "list" -> {
                expect(TokenType.LPAREN);
                Ast.DecRef element = parseDecRef();
                expect(TokenType.RPAREN);
                yield new Ast.ListDecRef(element, t.pos());
            }
            default -> throw error(t, "expected string, int, list(...), or Type.decoder");
        };
    }

    private Ast.Let parseLet() {
        Token kw = expect(TokenType.LET);
        String name = expect(TokenType.IDENT).text();
        expect(TokenType.ASSIGN);
        Ast.Expr value = parseExpr();
        return new Ast.Let(name, value, kw.pos());
    }

    private Ast.Require parseRequire() {
        Token kw = expect(TokenType.REQUIRE);
        Ast.Expr cond = parseExpr();
        expect(TokenType.ELSE);
        String code = expect(TokenType.IDENT).text();
        return new Ast.Require(cond, code, kw.pos());
    }

    private Ast.Construct parseConstruct() {
        Token type = expect(TokenType.IDENT);
        expect(TokenType.LBRACE);
        List<Ast.FieldInit> inits = parseFieldInits();
        expect(TokenType.RBRACE);
        return new Ast.Construct(type.text(), inits, type.pos());
    }

    private Ast.Expr newData(Token type) {
        expect(TokenType.LBRACE);
        List<Ast.FieldInit> inits = parseFieldInits();
        expect(TokenType.RBRACE);
        return new Ast.NewData(type.text(), inits, type.pos());
    }

    private List<Ast.FieldInit> parseFieldInits() {
        List<Ast.FieldInit> inits = new ArrayList<>();
        if (!check(TokenType.RBRACE)) {
            inits.add(parseFieldInit());
            while (match(TokenType.COMMA)) {
                if (check(TokenType.RBRACE)) {
                    break; // trailing comma
                }
                inits.add(parseFieldInit());
            }
        }
        return inits;
    }

    private Ast.FieldInit parseFieldInit() {
        Token n = expect(TokenType.IDENT);
        if (match(TokenType.COLON)) {
            return new Ast.FieldInit(n.text(), parseExpr(), n.pos());
        }
        // shorthand: `field` means `field: field`
        return new Ast.FieldInit(n.text(), new Ast.Var(n.text(), n.pos()), n.pos());
    }

    // --- encoder ---

    private Ast.EncoderDef parseEncoder() {
        Token kw = expect(TokenType.ENCODER);
        String self = expect(TokenType.IDENT).text();
        expect(TokenType.LBRACE);
        Ast.RawExpr result = parseRawExpr();
        expect(TokenType.RBRACE);
        return new Ast.EncoderDef(self, result, kw.pos());
    }

    private Ast.RawExpr parseRawExpr() {
        Token k = expect(TokenType.IDENT);
        switch (k.text()) {
            case "Text" -> {
                expect(TokenType.LPAREN);
                Ast.Expr arg = parseExpr();
                expect(TokenType.RPAREN);
                return new Ast.TextRaw(arg, k.pos());
            }
            case "Int" -> {
                expect(TokenType.LPAREN);
                Ast.Expr arg = parseExpr();
                expect(TokenType.RPAREN);
                return new Ast.IntRaw(arg, k.pos());
            }
            case "Object" -> {
                expect(TokenType.LBRACE);
                List<Ast.RawEntry> entries = new ArrayList<>();
                if (!check(TokenType.RBRACE)) {
                    entries.add(parseRawEntry());
                    while (match(TokenType.COMMA)) {
                        if (check(TokenType.RBRACE)) {
                            break;
                        }
                        entries.add(parseRawEntry());
                    }
                }
                expect(TokenType.RBRACE);
                return new Ast.ObjectRaw(entries, k.pos());
            }
            default -> {
                // TypeName.encode(expr)
                expect(TokenType.DOT);
                Token m = expect(TokenType.IDENT);
                if (!m.text().equals("encode")) {
                    throw error(m, "expected `" + k.text() + ".encode(...)`");
                }
                expect(TokenType.LPAREN);
                Ast.Expr arg = parseExpr();
                expect(TokenType.RPAREN);
                return new Ast.EncodeRaw(k.text(), arg, k.pos());
            }
        }
    }

    private Ast.RawEntry parseRawEntry() {
        Token key = expect(TokenType.STRING_LIT);
        expect(TokenType.COLON);
        return new Ast.RawEntry(key.text(), parseRawExpr(), key.pos());
    }

    private Ast.RawKind rawKind(Token t) {
        return switch (t.text()) {
            case "Text" -> Ast.RawKind.TEXT;
            case "Int" -> Ast.RawKind.INT;
            default -> throw error(t, "unknown Raw kind `" + t.text() + "` (expected Text or Int)");
        };
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
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token op = advance();
            Ast.BinOp o = op.type() == TokenType.PLUS ? Ast.BinOp.ADD : Ast.BinOp.SUB;
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
        if (check(TokenType.NOT)) {
            Token op = advance();
            return new Ast.Not(parseUnary(), op.pos());
        }
        return parsePrimary();
    }

    private Ast.Expr parsePrimary() {
        Token t = peek();
        switch (t.type()) {
            case MATCH -> {
                return parseMatch();
            }
            case INT_LIT -> {
                advance();
                return new Ast.IntLit(Long.parseLong(t.text()), t.pos());
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

    private Ast.Expr parseMatch() {
        Token kw = expect(TokenType.MATCH);
        boolean saved = noConstruct;
        noConstruct = true;
        Ast.Expr scrutinee = parseExpr();
        noConstruct = saved;
        expect(TokenType.LBRACE);
        List<Ast.Case> cases = new ArrayList<>();
        while (check(TokenType.CASE)) {
            advance();
            Token arm = expect(TokenType.IDENT);
            expect(TokenType.AS);
            Token binding = expect(TokenType.IDENT);
            expect(TokenType.FATARROW);
            Ast.Expr body = parseExpr();
            cases.add(new Ast.Case(arm.text(), binding.text(), body, arm.pos()));
        }
        expect(TokenType.RBRACE);
        return new Ast.Match(scrutinee, cases, kw.pos());
    }

    private Ast.Expr call(Token name) {
        expect(TokenType.LPAREN);
        List<Ast.Expr> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(parseExpr());
            while (match(TokenType.COMMA)) {
                args.add(parseExpr());
            }
        }
        expect(TokenType.RPAREN);
        return new Ast.Call(name.text(), args, name.pos());
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
