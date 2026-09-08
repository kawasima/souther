package souther.compiler.cst;

import souther.compiler.diag.msg.ParseMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            Map.entry("ensures", SyntaxKind.ENSURES_KW),
            // `intrinsic` is not reserved: it lexes as an identifier and is read by position.
            Map.entry("as", SyntaxKind.AS_KW),
            Map.entry("let", SyntaxKind.LET_KW),
            Map.entry("guard", SyntaxKind.GUARD_KW),
            Map.entry("else", SyntaxKind.ELSE_KW),
            Map.entry("true", SyntaxKind.TRUE_KW),
            Map.entry("false", SyntaxKind.FALSE_KW),
            Map.entry("if", SyntaxKind.IF_KW),
            Map.entry("then", SyntaxKind.THEN_KW),
            Map.entry("behavior", SyntaxKind.BEHAVIOR_KW),
            // `on` is not reserved: it is read as a contextual word after `depends`, the way
            // `for` is after `examples`, so an ordinary field or parameter may still be named on.
            Map.entry("depends", SyntaxKind.DEPENDS_KW),
            Map.entry("constructs", SyntaxKind.CONSTRUCTS_KW),
            Map.entry("match", SyntaxKind.MATCH_KW),
            Map.entry("with", SyntaxKind.WITH_KW),
            Map.entry("unreachable", SyntaxKind.UNREACHABLE_KW));

    /** The reserved keywords, the single source of truth a syntax-highlighter grammar derives from. */
    public static Set<String> keywords() {
        return KEYWORDS.keySet();
    }

    /** The characters a backslash may be written before. */
    private static final String ESCAPES = "ntr\"\\";

    /**
     * The characters a backslash may be written before, each as it is written.
     *
     * <p>The same list the scan refuses by, so a highlighter marking what the compiler refuses reads
     * this rather than restating it. Two lists would disagree the first time one of them moved, and
     * the editor would colour as an escape what a compile then rejects.
     */
    public static Set<String> escapes() {
        return ESCAPES.chars().mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The characters a string literal denotes: its quotes dropped and its escapes read.
     *
     * <p>Here rather than where a value is built, because which backslash pairs mean something is
     * what {@link #escapes} says and a second reading of them would be a second answer to it. Both
     * questions asked of a literal are asked of the text it denotes rather than of the text it is
     * written as — what a value holds, and whether a name written on an example row names anything.
     *
     * <p>A backslash before anything else is read as that character. The scan refuses such a literal
     * where it meets it, so this is what a literal already reported on denotes rather than a second
     * opinion about it.
     */
    public static String textOf(String raw) {
        int from = raw.startsWith("\"") ? 1 : 0;
        int to = raw.length() >= 2 && raw.endsWith("\"") ? raw.length() - 1 : raw.length();
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < to) {
                char e = raw.charAt(++i);
                sb.append(switch (e) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> e;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The lexer's result: the token stream (trivia and a trailing {@code EOF} included) and any
     * lexical errors, positioned by offset. */
    public record Result(List<GreenToken> tokens, List<CstError<?>> errors) {}

    private final String src;
    private int pos = 0;
    private final List<GreenToken> tokens = new ArrayList<>();
    private final List<CstError<?>> errors = new ArrayList<>();

    private CstLexer(String src) {
        this.src = src;
    }

    public static Result lex(String src) {
        CstLexer lexer = new CstLexer(src);
        lexer.run();
        return new Result(List.copyOf(lexer.tokens), List.copyOf(lexer.errors));
    }

    /**
     * The scan, one Unicode code point at a time.
     *
     * <p>{@code pos} stays an offset into the UTF-16 text, so a token's text is still the source
     * slice it covers and every position a diagnostic carries is still the file's own. What moves
     * by code point is the reading: a character outside the basic plane is one character to the
     * language, and a scan by {@code char} would see two halves of it and call each unreadable.
     */
    private void run() {
        while (pos < src.length()) {
            int start = pos;
            int c = src.codePointAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                whitespace(start);
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                lineComment(start);
            } else if (IdentifierAlphabet.isStart(c)) {
                identifier(start);
            } else if (c == '_') {
                underscore(start);
            } else if (beginsANumber(c)) {
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
        pos += Character.charCount(src.codePointAt(pos));   // the character it begins with
        carryOn();
        String text = src.substring(start, pos);
        SyntaxKind kw = KEYWORDS.get(text);
        tokens.add(new GreenToken(kw != null ? kw : SyntaxKind.IDENT, text));
    }

    /** Runs {@code pos} to the end of a name: every code point a name may carry on with. */
    private void carryOn() {
        while (pos < src.length()) {
            int c = src.codePointAt(pos);
            if (!IdentifierAlphabet.isContinue(c)) {
                return;
            }
            pos += Character.charCount(c);
        }
    }

    /**
     * {@code _}, which is a token of its own and not a name.
     *
     * <p>It carries on a name written {@code foo_bar} and begins none, so what follows decides
     * which of the two this is: nothing a name carries on with, and the underscore stands alone as
     * the discard the language already writes; anything else, and the author wrote a name
     * beginning with {@code _}, which is refused whole rather than split into a discard and a name
     * that was never written.
     */
    private void underscore(int start) {
        pos++;   // `_`
        if (pos < src.length() && IdentifierAlphabet.isContinue(src.codePointAt(pos))) {
            carryOn();
            errors.add(CstError.of(start, pos - start,
                    new ParseMessage.ANameDoesNotBeginWithAnUnderscore(src.substring(start, pos))));
            emit(SyntaxKind.ERROR_TOKEN, start);
            return;
        }
        emit(SyntaxKind.UNDERSCORE, start);
    }

    /**
     * Whether a numeric literal begins here.
     *
     * <p>{@link #number} reads a literal by UTF-16 unit, and this says what it can read, because a
     * scan that began a literal on a character that function then reads nothing of would leave
     * {@code pos} where it was and go round again. A digit outside the basic plane is therefore an
     * unexpected character, which is what it was before the scan moved to code points. Which digits
     * a literal may be written with at all is the numeric literal's question, and it is not settled
     * here — this only keeps the two ends of one decision from disagreeing.
     */
    private static boolean beginsANumber(int c) {
        return Character.charCount(c) == 1 && Character.isDigit((char) c);
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
            errors.add(CstError.of(start, pos - start,
                    new ParseMessage.AFractionalLiteralNeedsTheMSuffix(src.substring(start, pos))));
            emit(SyntaxKind.DECIMAL_LIT, start);
            return;
        }
        emit(SyntaxKind.INT_LIT, start);
    }

    /**
     * A string literal, which ends on the line it began on.
     *
     * <p>A newline closes nothing: a literal whose quote is missing is one line's mistake, and a
     * scan that ran past the line would take the rest of the file into it and report the loss
     * wherever it finally stopped. Written this way the reader is told where the quote is missing.
     * A newline in a value is written {@code \n}.
     */
    private void string(int start) {
        pos++;   // opening quote
        while (pos < src.length() && src.charAt(pos) != '"' && !endsTheLine(src.charAt(pos))) {
            if (src.charAt(pos) == '\\' && pos + 1 < src.length() && !endsTheLine(src.charAt(pos + 1))) {
                char escaped = src.charAt(pos + 1);
                if (ESCAPES.indexOf(escaped) < 0) {
                    errors.add(CstError.of(pos, 2,
                            new ParseMessage.AnEscapeIsNotOneTheLanguageReads(String.valueOf(escaped))));
                }
                pos += 2;   // the backslash and what it was written before
            } else {
                pos++;
            }
        }
        if (pos >= src.length() || endsTheLine(src.charAt(pos))) {
            errors.add(CstError.of(start, pos - start, new ParseMessage.AStringLiteralIsNotClosed()));
            emit(SyntaxKind.STRING_LIT, start);   // stops at the line, keeping the tree lossless
            return;
        }
        pos++;   // closing quote
        emit(SyntaxKind.STRING_LIT, start);
    }

    /** Whether {@code c} ends the line, either spelling of it. */
    private static boolean endsTheLine(char c) {
        return c == '\n' || c == '\r';
    }

    /** A type variable {@code 'a}. Only the core writes these; the parser gates their use. The name
     *  after the apostrophe is a name, held to the alphabet every other name is held to. */
    private void typeVar(int start) {
        pos++;   // the apostrophe
        if (pos >= src.length() || !IdentifierAlphabet.isStart(src.codePointAt(pos))) {
            errors.add(CstError.of(start, pos - start,
                    new ParseMessage.ATypeVariableNeedsANameAfterTheApostrophe()));
            emit(SyntaxKind.ERROR_TOKEN, start);
            return;
        }
        pos += Character.charCount(src.codePointAt(pos));
        carryOn();
        emit(SyntaxKind.TYPEVAR, start);
    }

    private void symbol(int start) {
        int point = src.codePointAt(pos);
        pos += Character.charCount(point);
        // Every symbol the language writes is ASCII, so anything above it is unreadable here and
        // is reported as the one character it is rather than as the halves it is stored in.
        char c = point < 128 ? (char) point : 0;
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
            case '<' -> {
                if (take('=')) {
                    yield SyntaxKind.LE;
                }
                // `<?>` — where an example row's answer goes before anyone has written it. One
                // token, so the language spells it one way.
                if (peekIs('?') && peekIs2('>')) {
                    pos += 2;
                    yield SyntaxKind.UNANSWERED;
                }
                yield SyntaxKind.LT;
            }
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
            errors.add(CstError.of(start, pos - start, c == '$'
                    ? new ParseMessage.ADollarIsNotWrittenInAName()
                    : new ParseMessage.AnUnexpectedCharacter(src.substring(start, pos))));
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
