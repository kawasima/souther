package net.unit8.souther.compiler.syntax;

/** The lexical token kinds recognised by the slice-1 lexer. */
public enum TokenType {
    // keywords
    MODULE, IMPORT, EXPOSING, DATA, INVARIANT, DECODER, ENCODER, FROM, AS, LET, REQUIRE, ELSE,
    TRUE, FALSE, IF, THEN, BEHAVIOR, REQUIRES, CONSTRUCTS, MATCH, WITH,
    // literals and identifiers
    IDENT, INT_LIT, DECIMAL_LIT, STRING_LIT, TYPEVAR,
    // punctuation
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET, COLON, COMMA, DOT, SPREAD, ASSIGN, PIPE,
    ARROW, LARROW, PIPEFWD, VPIPE, QUESTION, PLUSPLUS,
    // operators
    EQ, NE, LT, LE, GT, GE, AND, OR, PLUS, MINUS, STAR, SLASH,
    // end of input
    EOF;

    /** A reader-facing name for this kind, for a "expected X but found Y" message. A literal symbol
     * or keyword is locale-neutral and returned as a backtick string ({@code `:`}); a category is
     * language-dependent and returned as a {@link net.unit8.souther.compiler.diag.Localizable}
     * ({@code tok.name}), localized when the message is rendered. Unlike {@link #name()} it does not
     * leak the enum constant ({@code LPAREN}). */
    public Object display() {
        return switch (this) {
            case IDENT -> net.unit8.souther.compiler.diag.Localizable.of("tok.name");
            case INT_LIT -> net.unit8.souther.compiler.diag.Localizable.of("tok.integer");
            case DECIMAL_LIT -> net.unit8.souther.compiler.diag.Localizable.of("tok.decimal");
            case STRING_LIT -> net.unit8.souther.compiler.diag.Localizable.of("tok.string");
            case TYPEVAR -> net.unit8.souther.compiler.diag.Localizable.of("tok.typevar");
            case EOF -> net.unit8.souther.compiler.diag.Localizable.of("tok.eof");
            case LBRACE -> "`{`";
            case RBRACE -> "`}`";
            case LPAREN -> "`(`";
            case RPAREN -> "`)`";
            case LBRACKET -> "`[`";
            case RBRACKET -> "`]`";
            case COLON -> "`:`";
            case COMMA -> "`,`";
            case DOT -> "`.`";
            case SPREAD -> "`...`";
            case ASSIGN -> "`=`";
            case PIPE -> "`|`";
            case ARROW -> "`->`";
            case LARROW -> "`<-`";
            case PIPEFWD -> "`>->`";
            case VPIPE -> "`|>`";
            case QUESTION -> "`?`";
            case PLUSPLUS -> "`++`";
            case EQ -> "`==`";
            case NE -> "`!=`";
            case LT -> "`<`";
            case LE -> "`<=`";
            case GT -> "`>`";
            case GE -> "`>=`";
            case AND -> "`&&`";
            case OR -> "`||`";
            case PLUS -> "`+`";
            case MINUS -> "`-`";
            case STAR -> "`*`";
            case SLASH -> "`/`";
            default -> "`" + name().toLowerCase(java.util.Locale.ROOT) + "`";
        };
    }
}
