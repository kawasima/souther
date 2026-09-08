package souther.compiler.editor;

import souther.compiler.cst.SyntaxKind;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * How an editor classifies each symbol the language writes.
 *
 * <p>Every consumer that colours Souther reads this: the TextMate grammar, which matches text, and
 * the language server's semantic tokens, which classify by kind. Each used to carry its own list,
 * written by hand in its own terms, and the two had drifted three symbols apart.
 *
 * <p>Saying which symbols are <em>not</em> painted is as much of the classification as saying which
 * are. A consumer that only knew the operators could not tell a symbol left alone on purpose from
 * one nobody had got to yet, and would go on compiling once a symbol was added to the language.
 * Here the two are the same answer, and every symbol {@link SyntaxKind} spells has to be given one.
 */
public final class EditorSymbols {

    private EditorSymbols() {
    }

    /**
     * How an editor classifies {@code kind}, or empty where the question does not arise — a node, a
     * name, a literal, a comment, and the reserved words, which are coloured as words rather than as
     * symbols.
     */
    public static Optional<EditorSymbolClass> classOf(SyntaxKind kind) {
        return Optional.ofNullable(switch (kind) {
            // `<?>` is painted rather than left in the colour of the text, and it is here rather
            // than below because of what painting it takes: it opens with `<`, so an editor that
            // left it alone would paint that character as the operator it also spells and the rest
            // of the mark as text. What is painted whole is what is painted at all.
            case SPREAD, ASSIGN, PIPE, ARROW, PIPEFWD, VPIPE, QUESTION, PLUSPLUS, EQ, NE, LT, LE,
                 GT, GE, AND, OR, PLUS, MINUS, STAR, SLASH, UNANSWERED ->
                    EditorSymbolClass.OPERATOR;
            // `_` is here with the brackets and the commas: it stands where a name stands, and a
            // name is left in the colour of the text too.
            case LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET, COLON, COMMA, DOT, UNDERSCORE ->
                    EditorSymbolClass.PUNCTUATION;
            default -> null;
        });
    }

    /** Whether an editor paints {@code kind} as an operator. */
    public static boolean isOperator(SyntaxKind kind) {
        return classOf(kind).orElse(null) == EditorSymbolClass.OPERATOR;
    }

    /** The kinds an editor paints as operators. */
    public static Set<SyntaxKind> operators() {
        return of(EditorSymbolClass.OPERATOR);
    }

    /** The kinds an editor leaves in the colour of the text. */
    public static Set<SyntaxKind> punctuation() {
        return of(EditorSymbolClass.PUNCTUATION);
    }

    private static Set<SyntaxKind> of(EditorSymbolClass wanted) {
        EnumSet<SyntaxKind> kinds = EnumSet.noneOf(SyntaxKind.class);
        for (SyntaxKind kind : SyntaxKind.values()) {
            if (classOf(kind).orElse(null) == wanted) {
                kinds.add(kind);
            }
        }
        return Collections.unmodifiableSet(kinds);
    }
}
