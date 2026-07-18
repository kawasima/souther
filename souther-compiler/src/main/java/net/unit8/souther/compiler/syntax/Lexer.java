package net.unit8.souther.compiler.syntax;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.SourcePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The slice-1 lexer. Turns Souther source into a token list. Supports Unicode
 * identifiers (so Japanese domain vocabulary can be written directly, spec section 4),
 * {@code //} line comments, integer and double-quoted string literals, and the
 * punctuation/operators the slice-1 grammar needs.
 */
public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("module", TokenType.MODULE),
            Map.entry("import", TokenType.IMPORT),
            Map.entry("exposing", TokenType.EXPOSING),
            Map.entry("data", TokenType.DATA),
            Map.entry("invariant", TokenType.INVARIANT),
            // decoder / encoder / from are not reserved (spec 5): they read as identifiers,
            // e.g. the pipeline stages `Member.decoder` / `MemberResponse.encoder`
            Map.entry("as", TokenType.AS),
            Map.entry("let", TokenType.LET),
            Map.entry("require", TokenType.REQUIRE),
            Map.entry("else", TokenType.ELSE),
            Map.entry("true", TokenType.TRUE),
            Map.entry("false", TokenType.FALSE),
            Map.entry("if", TokenType.IF),
            Map.entry("then", TokenType.THEN),
            Map.entry("behavior", TokenType.BEHAVIOR),
            Map.entry("requires", TokenType.REQUIRES),
            Map.entry("constructs", TokenType.CONSTRUCTS),
            Map.entry("match", TokenType.MATCH),
            Map.entry("with", TokenType.WITH));

    private final String src;
    private int pos = 0;
    private int line = 1;
    private int col = 1;

    public Lexer(String src) {
        this.src = src;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = next();
            tokens.add(t);
        } while (t.type() != TokenType.EOF);
        return tokens;
    }

    private Token next() {
        skipTrivia();
        if (atEnd()) {
            return new Token(TokenType.EOF, "", here());
        }
        SourcePos start = here();
        char c = peek();

        if (Character.isJavaIdentifierStart(c)) {
            return identifier(start);
        }
        if (Character.isDigit(c)) {
            return number(start);
        }
        if (c == '"') {
            return string(start);
        }
        if (c == '\'') {
            return typeVar(start);
        }
        return symbol(start);
    }

    /** A type variable {@code 'a}: an apostrophe (F#/OCaml) followed by an identifier. The core
     * writes these; a user module is rejected in the parser (ADR-0028). */
    private Token typeVar(SourcePos start) {
        StringBuilder sb = new StringBuilder();
        sb.append(advance());   // the apostrophe
        if (atEnd() || !Character.isJavaIdentifierStart(peek())) {
            throw new CompileException(start, "a type variable needs a name after `'`, e.g. `'a`");
        }
        while (!atEnd() && Character.isJavaIdentifierPart(peek())) {
            sb.append(advance());
        }
        return new Token(TokenType.TYPEVAR, sb.toString(), start);
    }

    private Token identifier(SourcePos start) {
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && Character.isJavaIdentifierPart(peek())) {
            sb.append(advance());
        }
        String text = sb.toString();
        TokenType kw = KEYWORDS.get(text);
        return new Token(kw != null ? kw : TokenType.IDENT, text, start);
    }

    private Token number(SourcePos start) {
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && Character.isDigit(peek())) {
            sb.append(advance());
        }
        // a `.` followed by a digit makes it a Decimal literal (spec 7.1); a `.` followed by
        // anything else is a field access on an Int, left for the parser.
        if (!atEnd() && peek() == '.' && Character.isDigit(peekNext())) {
            sb.append(advance());                       // the dot
            while (!atEnd() && Character.isDigit(peek())) {
                sb.append(advance());
            }
            return new Token(TokenType.DECIMAL_LIT, sb.toString(), start);
        }
        return new Token(TokenType.INT_LIT, sb.toString(), start);
    }

    private Token string(SourcePos start) {
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && peek() != '"') {
            char c = advance();
            if (c == '\\') {
                if (atEnd()) break;
                char e = advance();
                sb.append(switch (e) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> e;
                });
            } else {
                sb.append(c);
            }
        }
        if (atEnd()) {
            throw new CompileException(start, "unterminated string literal");
        }
        advance(); // closing quote
        return new Token(TokenType.STRING_LIT, sb.toString(), start);
    }

    private Token symbol(SourcePos start) {
        char c = advance();
        switch (c) {
            case '{': return new Token(TokenType.LBRACE, "{", start);
            case '}': return new Token(TokenType.RBRACE, "}", start);
            case '(': return new Token(TokenType.LPAREN, "(", start);
            case ')': return new Token(TokenType.RPAREN, ")", start);
            case '[': return new Token(TokenType.LBRACKET, "[", start);
            case ']': return new Token(TokenType.RBRACKET, "]", start);
            case ':': return new Token(TokenType.COLON, ":", start);
            case ',': return new Token(TokenType.COMMA, ",", start);
            case '.':
                if (match('.')) {
                    if (match('.')) return new Token(TokenType.SPREAD, "...", start);
                    throw new CompileException(start, "spread is written `...` (three dots)");
                }
                return new Token(TokenType.DOT, ".", start);
            case '=':
                if (match('=')) return new Token(TokenType.EQ, "==", start);
                return new Token(TokenType.ASSIGN, "=", start);
            case '/':
                // `/=` is inequality (Elm/Haskell form). There is no division operator,
                // so a lone `/` is an error. (`//` is a comment, consumed in skipTrivia.)
                if (match('=')) return new Token(TokenType.NE, "/=", start);
                break;
            case '<':
                if (match('=')) return new Token(TokenType.LE, "<=", start);
                if (match('-')) return new Token(TokenType.LARROW, "<-", start);
                return new Token(TokenType.LT, "<", start);
            case '>':
                if (match('=')) return new Token(TokenType.GE, ">=", start);
                // `>->` is the pipeline composition operator (spec 14). `>>` is no longer a token,
                // so nested generics like `Map<String, List<T>>` lex as two `>` naturally.
                if (!atEnd() && peek() == '-' && peekNext() == '>') {
                    advance();  // '-'
                    advance();  // '>'
                    return new Token(TokenType.PIPEFWD, ">->", start);
                }
                return new Token(TokenType.GT, ">", start);
            case '&':
                if (match('&')) return new Token(TokenType.AND, "&&", start);
                break;
            case '|':
                if (match('|')) return new Token(TokenType.OR, "||", start);
                return new Token(TokenType.PIPE, "|", start);
            case '-':
                if (match('>')) return new Token(TokenType.ARROW, "->", start);
                return new Token(TokenType.MINUS, "-", start);
            case '+':
                if (match('+')) return new Token(TokenType.PLUSPLUS, "++", start);
                return new Token(TokenType.PLUS, "+", start);
            case '*':
                return new Token(TokenType.STAR, "*", start);
            case '?':
                return new Token(TokenType.QUESTION, "?", start);
            default:
                break;
        }
        throw new CompileException(start, "unexpected character '" + c + "'");
    }

    private void skipTrivia() {
        while (!atEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                while (!atEnd() && peek() != '\n') {
                    advance();
                }
            } else {
                break;
            }
        }
    }

    private boolean match(char expected) {
        if (atEnd() || peek() != expected) {
            return false;
        }
        advance();
        return true;
    }

    private char peek() {
        return src.charAt(pos);
    }

    private char peekNext() {
        return pos + 1 < src.length() ? src.charAt(pos + 1) : '\0';
    }

    private char advance() {
        char c = src.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }

    private boolean atEnd() {
        return pos >= src.length();
    }

    private SourcePos here() {
        return new SourcePos(line, col);
    }
}
