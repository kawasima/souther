package net.unit8.souther.compiler.cst;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A trivia-preserving, non-throwing lexer. It turns source into a flat stream of {@link GreenToken}s
 * in which every source character — whitespace, comments, and even unexpected characters — belongs
 * to exactly one token. Each token's text is the exact source slice it covers, so concatenating the
 * stream reproduces the source (the lossless invariant).
 *
 * <p>Unlike the compiler's original {@code Lexer}, it never throws: a lexical problem becomes an
 * {@link CstError} plus a best-effort token, so a broken buffer in an editor still tokenizes whole.
 * String and decimal literals keep their raw source text (quotes, escapes, the {@code m} suffix);
 * unescaping and value parsing happen later, in CST→AST lowering.
 */
public final class CstLexer {

    private static final Map<String, SyntaxKind> KEYWORDS = Map.ofEntries(
            Map.entry("module", SyntaxKind.MODULE_KW),
            Map.entry("import", SyntaxKind.IMPORT_KW),
            Map.entry("exposing", SyntaxKind.EXPOSING_KW),
            Map.entry("data", SyntaxKind.DATA_KW),
            Map.entry("invariant", SyntaxKind.INVARIANT_KW),
            // decoder / encoder / from / intrinsic are not reserved: they lex as identifiers.
            Map.entry("as", SyntaxKind.AS_KW),
            Map.entry("let", SyntaxKind.LET_KW),
            Map.entry("require", SyntaxKind.REQUIRE_KW),
            Map.entry("else", SyntaxKind.ELSE_KW),
            Map.entry("true", SyntaxKind.TRUE_KW),
            Map.entry("false", SyntaxKind.FALSE_KW),
            Map.entry("if", SyntaxKind.IF_KW),
            Map.entry("then", SyntaxKind.THEN_KW),
            Map.entry("behavior", SyntaxKind.BEHAVIOR_KW),
            Map.entry("requires", SyntaxKind.REQUIRES_KW),
            Map.entry("constructs", SyntaxKind.CONSTRUCTS_KW),
            Map.entry("match", SyntaxKind.MATCH_KW),
            Map.entry("with", SyntaxKind.WITH_KW));

    /** The reserved keywords, the single source of truth a syntax-highlighter grammar derives from. */
    public static Set<String> keywords() {
        return KEYWORDS.keySet();
    }

    /** The lexer's result: the token stream (trivia and a trailing {@code EOF} included) and any
     * lexical errors, positioned by offset. */
    public record Result(List<GreenToken> tokens, List<CstError> errors) {}

    private final String src;
    private int pos = 0;
    private final List<GreenToken> tokens = new ArrayList<>();
    private final List<CstError> errors = new ArrayList<>();

    private CstLexer(String src) {
        this.src = src;
    }

    public static Result lex(String src) {
        CstLexer lexer = new CstLexer(src);
        lexer.run();
        return new Result(List.copyOf(lexer.tokens), List.copyOf(lexer.errors));
    }

    private void run() {
        while (pos < src.length()) {
            int start = pos;
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                whitespace(start);
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                lineComment(start);
            } else if (Character.isJavaIdentifierStart(c)) {
                identifier(start);
            } else if (Character.isDigit(c)) {
                number(start);
            } else if (c == '"') {
                string(start);
            } else if (c == '\'') {
                typeVar(start);
            } else {
                symbol(start);
            }
        }
        tokens.add(new GreenToken(SyntaxKind.EOF, ""));
    }

    private void whitespace(int start) {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                pos++;
            } else {
                break;
            }
        }
        emit(SyntaxKind.WHITESPACE, start);
    }

    private void lineComment(int start) {
        while (pos < src.length() && src.charAt(pos) != '\n') {
            pos++;
        }
        emit(SyntaxKind.LINE_COMMENT, start);
    }

    private void identifier(int start) {
        while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
            pos++;
        }
        String text = src.substring(start, pos);
        SyntaxKind kw = KEYWORDS.get(text);
        tokens.add(new GreenToken(kw != null ? kw : SyntaxKind.IDENT, text));
    }

    private void number(int start) {
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            pos++;
        }
        boolean fractional = false;
        if (pos + 1 < src.length() && src.charAt(pos) == '.' && Character.isDigit(src.charAt(pos + 1))) {
            fractional = true;
            pos++;   // the dot
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        // A Decimal literal carries the `m` suffix (F# form: `500m`, `1.5m`), counted only when it
        // ends the literal — a following letter/digit makes it an identifier (`500money`).
        boolean hasSuffix = pos < src.length() && src.charAt(pos) == 'm'
                && !(pos + 1 < src.length() && Character.isLetterOrDigit(src.charAt(pos + 1)));
        if (hasSuffix) {
            pos++;   // consume the `m`; the raw text keeps it, Lower strips it
            emit(SyntaxKind.DECIMAL_LIT, start);
            return;
        }
        if (fractional) {
            // A fractional literal with no `m` is not a Decimal and there is no float type. Keep the
            // whole slice as one token so the tree stays lossless, and record the error.
            errors.add(CstError.of(start, pos - start, "lex.decimal.m",
                    "a fractional literal is a Decimal and needs the `m` suffix (write `"
                            + src.substring(start, pos) + "m`); Souther has no floating-point type",
                    src.substring(start, pos)));
            emit(SyntaxKind.DECIMAL_LIT, start);
            return;
        }
        emit(SyntaxKind.INT_LIT, start);
    }

    private void string(int start) {
        pos++;   // opening quote
        while (pos < src.length() && src.charAt(pos) != '"') {
            if (src.charAt(pos) == '\\' && pos + 1 < src.length()) {
                pos += 2;   // skip the escaped character
            } else {
                pos++;
            }
        }
        if (pos >= src.length()) {
            errors.add(CstError.of(start, pos - start, "lex.string.unterminated",
                    "unterminated string literal"));
            emit(SyntaxKind.STRING_LIT, start);   // covers to EOF, keeping the tree lossless
            return;
        }
        pos++;   // closing quote
        emit(SyntaxKind.STRING_LIT, start);
    }

    /** A type variable {@code 'a}. Only the core writes these; the parser gates their use. */
    private void typeVar(int start) {
        pos++;   // the apostrophe
        if (pos >= src.length() || !Character.isJavaIdentifierStart(src.charAt(pos))) {
            errors.add(CstError.of(start, pos - start, "lex.typevar",
                    "a type variable needs a name after `'`, e.g. `'a`"));
            emit(SyntaxKind.ERROR_TOKEN, start);
            return;
        }
        while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
            pos++;
        }
        emit(SyntaxKind.TYPEVAR, start);
    }

    private void symbol(int start) {
        char c = src.charAt(pos++);
        SyntaxKind kind = switch (c) {
            case '{' -> SyntaxKind.LBRACE;
            case '}' -> SyntaxKind.RBRACE;
            case '(' -> SyntaxKind.LPAREN;
            case ')' -> SyntaxKind.RPAREN;
            case '[' -> SyntaxKind.LBRACKET;
            case ']' -> SyntaxKind.RBRACKET;
            case ':' -> SyntaxKind.COLON;
            case ',' -> SyntaxKind.COMMA;
            case '?' -> SyntaxKind.QUESTION;
            case '*' -> SyntaxKind.STAR;
            case '.' -> {
                // `...` is spread; a lone `.` is field access. `..` naturally lexes as two dots.
                if (peekIs('.') && peekIs2('.')) {
                    pos += 2;
                    yield SyntaxKind.SPREAD;
                }
                yield SyntaxKind.DOT;
            }
            case '=' -> take('=') ? SyntaxKind.EQ : SyntaxKind.ASSIGN;
            case '/' -> take('=') ? SyntaxKind.NE : SyntaxKind.SLASH;   // `//` is handled as a comment
            case '<' -> take('=') ? SyntaxKind.LE : take('-') ? SyntaxKind.LARROW : SyntaxKind.LT;
            case '>' -> {
                if (take('=')) {
                    yield SyntaxKind.GE;
                }
                if (peekIs('-') && peekIs2('>')) {   // `>->` composes behaviors
                    pos += 2;
                    yield SyntaxKind.PIPEFWD;
                }
                yield SyntaxKind.GT;
            }
            case '&' -> take('&') ? SyntaxKind.AND : null;
            case '|' -> take('|') ? SyntaxKind.OR : take('>') ? SyntaxKind.VPIPE : SyntaxKind.PIPE;
            case '-' -> take('>') ? SyntaxKind.ARROW : SyntaxKind.MINUS;
            case '+' -> take('+') ? SyntaxKind.PLUSPLUS : SyntaxKind.PLUS;
            default -> null;
        };
        if (kind == null) {
            errors.add(CstError.of(start, pos - start, "lex.unexpected",
                    "unexpected character '" + c + "'", String.valueOf(c)));
            emit(SyntaxKind.ERROR_TOKEN, start);
            return;
        }
        emit(kind, start);
    }

    private boolean take(char expected) {
        if (pos < src.length() && src.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean peekIs(char c) {
        return pos < src.length() && src.charAt(pos) == c;
    }

    private boolean peekIs2(char c) {
        return pos + 1 < src.length() && src.charAt(pos + 1) == c;
    }

    private void emit(SyntaxKind kind, int start) {
        tokens.add(new GreenToken(kind, src.substring(start, pos)));
    }
}
