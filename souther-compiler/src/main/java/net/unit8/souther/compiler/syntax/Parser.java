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
        List<String> exposing = check(TokenType.EXPOSING) ? parseExposing() : new ArrayList<>();
        List<Ast.Import> imports = new ArrayList<>();
        while (check(TokenType.IMPORT)) {
            imports.add(parseImport());
        }
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
        return new Ast.Module(name, exposing, imports, defs, behaviors, requireds, m.pos());
    }

    /** {@code exposing { name, name.decoder, ... }} — the module's public surface (spec 4). */
    private List<String> parseExposing() {
        expect(TokenType.EXPOSING);
        expect(TokenType.LBRACE);
        List<String> names = new ArrayList<>();
        names.add(dottedName());
        while (match(TokenType.COMMA)) {
            if (check(TokenType.RBRACE)) {
                break;
            }
            names.add(dottedName());
        }
        expect(TokenType.RBRACE);
        return names;
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
     * {@code behavior name = <rhs>} where the rhs is either a pipeline ({@code a >> b}) or a
     * parameter list with a body. One token after {@code =} tells them apart: {@code (} is a body.
     */
    private Ast.BehaviorDef parseBehavior() {
        Token kw = expect(TokenType.BEHAVIOR);
        String name = expect(TokenType.IDENT).text();
        if (check(TokenType.LPAREN)) {
            throw error(peek(), "behavior `" + name + "` needs `=` before its parameters: "
                    + "`behavior " + name + " = (...) -> ...`");
        }
        expect(TokenType.ASSIGN);
        if (!check(TokenType.LPAREN)) {
            List<String> stages = new ArrayList<>();
            stages.add(parseStage());
            while (match(TokenType.GTGT)) {
                stages.add(parseStage());
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

    /** A pipeline stage: a behavior name, or {@code Type.decoder} / {@code Type.encoder}. */
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
        if (check(TokenType.LPAREN)) {
            throw error(peek(), "required behavior `" + name + "` needs `=` before its parameter: "
                    + "`required behavior " + name + " = (...) -> ...`");
        }
        expect(TokenType.ASSIGN);
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
            return check(TokenType.LBRACE) ? parseProduct(kw, name) : parseSum(kw, name);
        }
        return new Ast.UnitData(name, kw.pos());
    }

    private Ast.SumData parseSum(Token kw, String name) {
        List<String> arms = new ArrayList<>();
        arms.add(expect(TokenType.IDENT).text());
        while (match(TokenType.PIPE)) {
            arms.add(expect(TokenType.IDENT).text());
        }
        // decoders/encoders (incl. the sum discriminator) are derived, not written
        return new Ast.SumData(name, arms, Optional.empty(), Optional.empty(), kw.pos());
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
                invariant = Optional.of(parseExpr());
            } else {
                throw error(peek(), "expected invariant or '}'");
            }
        }
        expect(TokenType.RBRACE);
        // decoders/encoders are derived, not written
        return new Ast.Data(name, includes, fields, invariant, Optional.empty(), Optional.empty(), kw.pos());
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
            case IF -> {
                return parseIf();
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
            case LBRACKET -> {
                return parseList();
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
        expect(TokenType.LBRACE);
        List<Ast.Case> cases = new ArrayList<>();
        while (check(TokenType.CASE)) {
            advance();
            Token arm = expect(TokenType.IDENT);
            String binding = match(TokenType.AS) ? expect(TokenType.IDENT).text() : null;
            expect(TokenType.FATARROW);
            Ast.Expr body = parseExpr();
            cases.add(new Ast.Case(arm.text(), binding, body, arm.pos()));
        }
        expect(TokenType.RBRACE);
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
