package souther.compiler.cst;

import souther.compiler.diag.Localizable;

import java.util.Locale;
import java.util.Optional;

/**
 * The single kind space of the concrete syntax tree: leaf (token) kinds, trivia kinds, and
 * internal node kinds, all in one enum (the rowan/Roslyn model). A {@link GreenToken} carries a
 * leaf or trivia kind; a {@link GreenNode} carries an internal-node kind.
 *
 * <p>Unlike the compiler's older {@code TokenType}, this space also names whitespace and comments
 * (trivia), so the tree is lossless — concatenating every leaf's text reproduces the source
 * exactly. That invariant is what the formatter and incremental reparse rest on.
 */
public enum SyntaxKind {
    // --- trivia (kept in the tree; skipped by the parser's token cursor) ---
    WHITESPACE,
    LINE_COMMENT,

    // --- keywords ---
    // `example` / `examples` / `for` are NOT reserved (they would collide with the `example.*`
    // package/module names): the parser recognizes them by text at top-level position, like the
    // contextual `intrinsic`. `on`, the second word of `depends on`, is read the same way, so a
    // field or parameter may still be named on.
    MODULE_KW, IMPORT_KW, EXPOSING_KW, DATA_KW, INVARIANT_KW, ENSURES_KW, AS_KW, LET_KW, GUARD_KW, ELSE_KW,
    TRUE_KW, FALSE_KW, IF_KW, THEN_KW, BEHAVIOR_KW, DEPENDS_KW, CONSTRUCTS_KW, MATCH_KW, WITH_KW,
    UNREACHABLE_KW,

    // --- literals and identifiers ---
    IDENT, INT_LIT, DECIMAL_LIT, STRING_LIT, TYPEVAR,

    // --- punctuation ---
    // `_` is punctuation and not a name: it carries a name on (`foo_bar`) and begins none, so where
    // it stands alone it is the discard the language writes and has a token of its own.
    //
    // `<?>` is one token and not `<` `?` `>`. It stands where an example row's answer goes and is
    // written one way: lexed as three, `< ? >` would be a second spelling of it, and a formatter
    // would be free to put the halves anywhere it puts an operator.
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET, COLON, COMMA, DOT, SPREAD, ASSIGN, PIPE,
    ARROW, PIPEFWD, VPIPE, QUESTION, PLUSPLUS, UNDERSCORE, UNANSWERED,

    // --- operators ---
    EQ, NE, LT, LE, GT, GE, AND, OR, PLUS, MINUS, STAR, SLASH,

    // --- end of input / lexical error ---
    EOF, ERROR_TOKEN,

    // --- nodes: top level ---
    SOURCE_FILE,
    MODULE_HEADER,
    EXPOSING_CLAUSE,
    EXPOSED_ENTRY,
    IMPORT_DECL,
    IMPORT_ALIAS,       // `as B` on an import: the qualifier this module reads the module under
    NAME_LIST,          // parenthesised comma list of names (import/exposing bodies)
    QUALIFIED_NAME,     // dotted module or member name

    // --- nodes: data ---
    DATA_DEF,
    PRODUCT_BODY,
    FIELD,
    SPREAD_MEMBER,
    SUM_BODY,
    NEWTYPE_BODY,
    INVARIANT_CLAUSE,

    // --- nodes: behavior ---
    BEHAVIOR_DEF,
    BEHAVIOR_SIG,
    PIPE_BEHAVIOR,
    PARAM_LIST,
    PARAM,
    CONSTRUCTS_CLAUSE,
    DEPENDS_CLAUSE,
    ENSURES_CLAUSE,
    ENSURES_ARM,
    STAGE,

    // --- nodes: fn ---
    FN_DEF,
    FN_PARAM_LIST,
    FN_PARAM,
    INTRINSIC_BODY,
    PARTIAL_MODIFIER,      // `partial` before a helper `let` — opts out of the totality check
    PRIVATE_MODIFIER,      // `private` before a `let` — keeps it out of the published surface

    // --- nodes: example ---
    EXAMPLE_DEF,            // example <target> | row | row ...
    EXAMPLE_ROW,            // [ "desc" : ] ( args ) [with binding, ...] -> expected
    EXAMPLES_FILE_HEADER,   // examples for <module.path>   (attached example file)
    WITH_CLAUSE,            // with <dep> = <expr> (, <dep> = <expr>)*
    WITH_BINDING,           // <dep> = <expr>

    // --- nodes: fake ---
    FAKE_DEF,              // fake <injected> | row | row ...
    FAKE_ROW,             // ( args ) -> output   |   _ -> output (a default)

    // --- nodes: types ---
    RET_TYPE,
    TYPE_REF,
    TYPE_ARGS,
    TUPLE_TYPE,
    FN_TYPE,

    // --- nodes: statements inside a behavior body / block ---
    LET_STMT,
    LET_DESTRUCTURE,    // let <pattern> = e
    GUARD_STMT,

    // --- nodes: binding patterns (a `let` statement, a helper or lambda parameter) ---
    PATTERN_NAME,       // x
    PATTERN_TUPLE,      // ( p, p, ... )
    PATTERN_CTOR,       // Name( p )  — a newtype opened by its constructor
    PATTERN_RECORD,     // { f, f = x }
    PATTERN_FIELD,      // f  or  f = x

    // --- nodes: expressions ---
    BLOCK_EXPR,         // { stmts result } and the bare-brace block form
    PIPE_EXPR,          // a |> b
    BINARY_EXPR,        // a <op> b (all precedence levels)
    UNARY_EXPR,         // -a
    APPLY_EXPR,         // <expr>(args) — what is applied is any expression
    ARG_LIST,
    FIELD_ACCESS,       // a.b
    VAR_EXPR,           // a bare identifier
    LITERAL_EXPR,       // int / decimal / string / true / false
    PAREN_EXPR,         // ( e )
    TUPLE_EXPR,         // ( e1, e2, ... )
    LIST_EXPR,          // [ e, ... ]
    LIST_COMP,          // [ e | guard, ... ]
    IF_EXPR,
    ELSE_ARMS,          // else | clause -> e | clause -> e ...  (an attempt's per-clause departures)
    ELSE_ARM,           // | <clause name> -> e   and   | _ -> e
    MATCH_EXPR,
    MATCH_CASE,
    LAMBDA_EXPR,        // x -> e  and  (a, b) -> e
    FIELD_GETTER,       // .field  = the getter (x) -> x.field
    NEW_DATA_EXPR,      // TypeName { field = e, ... }
    FIELD_INIT,
    UNREACHABLE_EXPR;   // unreachable "reason"

    /**
     * What a kind is made of: an internal node, a token the kind itself spells, or a token whose
     * text the source supplies.
     *
     * <p>This is the kind's lexical identity, and it is deliberately not {@link #display()}, which
     * is how a kind is shown to a reader. Recovering the first from the second means reading the
     * shown text for letters, which answers for kinds it was never asked about and says nothing at
     * all about a token added with no shown form.
     */
    public enum Lexis {
        /** An internal node. It covers text, but the leaves under it are what carry it. */
        NODE,
        /** A token the kind spells. Every occurrence reads the same, so the kind can be asked how. */
        FIXED_TOKEN,
        /** A token the source spells — a name, a literal, trivia, the fragment a lexical error
         *  covers — or end of input, which covers no text at all. The kind names what it is and not
         *  how it reads. */
        OPEN_TOKEN,
    }

    /**
     * Which of the three this kind is.
     *
     * <p>Written with no default arm on purpose: a constant added to this enum does not compile
     * until it is placed here. Everything below that asks what a kind is made of rests on this,
     * so it is the one place where forgetting has to be impossible rather than merely tested.
     */
    public Lexis lexis() {
        return switch (this) {
            case WHITESPACE, LINE_COMMENT, IDENT, INT_LIT, DECIMAL_LIT, STRING_LIT, TYPEVAR, EOF,
                 ERROR_TOKEN -> Lexis.OPEN_TOKEN;

            case MODULE_KW, IMPORT_KW, EXPOSING_KW, DATA_KW, INVARIANT_KW, ENSURES_KW, AS_KW, LET_KW, GUARD_KW,
                 ELSE_KW, TRUE_KW, FALSE_KW, IF_KW, THEN_KW, BEHAVIOR_KW, DEPENDS_KW, CONSTRUCTS_KW,
                 MATCH_KW, WITH_KW, UNREACHABLE_KW,
                 LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET, COLON, COMMA, DOT, SPREAD,
                 ASSIGN, PIPE, ARROW, PIPEFWD, VPIPE, QUESTION, PLUSPLUS, UNDERSCORE, UNANSWERED,
                 EQ, NE, LT, LE, GT, GE, AND, OR, PLUS, MINUS, STAR, SLASH -> Lexis.FIXED_TOKEN;

            case SOURCE_FILE, MODULE_HEADER, EXPOSING_CLAUSE, EXPOSED_ENTRY, IMPORT_DECL,
                 IMPORT_ALIAS, NAME_LIST, QUALIFIED_NAME,
                 DATA_DEF, PRODUCT_BODY, FIELD, SPREAD_MEMBER, SUM_BODY, NEWTYPE_BODY,
                 INVARIANT_CLAUSE,
                 BEHAVIOR_DEF, BEHAVIOR_SIG, PIPE_BEHAVIOR, PARAM_LIST, PARAM, CONSTRUCTS_CLAUSE,
                 DEPENDS_CLAUSE, ENSURES_CLAUSE, ENSURES_ARM, STAGE,
                 FN_DEF, FN_PARAM_LIST, FN_PARAM, INTRINSIC_BODY, PARTIAL_MODIFIER,
                 PRIVATE_MODIFIER,
                 EXAMPLE_DEF, EXAMPLE_ROW, EXAMPLES_FILE_HEADER, WITH_CLAUSE, WITH_BINDING,
                 FAKE_DEF, FAKE_ROW,
                 RET_TYPE, TYPE_REF, TYPE_ARGS, TUPLE_TYPE, FN_TYPE,
                 LET_STMT, LET_DESTRUCTURE, GUARD_STMT,
                 PATTERN_NAME, PATTERN_TUPLE, PATTERN_CTOR, PATTERN_RECORD, PATTERN_FIELD,
                 BLOCK_EXPR, PIPE_EXPR, BINARY_EXPR, UNARY_EXPR, APPLY_EXPR, ARG_LIST, FIELD_ACCESS,
                 VAR_EXPR, LITERAL_EXPR, PAREN_EXPR, TUPLE_EXPR, LIST_EXPR, LIST_COMP, IF_EXPR,
                 ELSE_ARMS, ELSE_ARM, MATCH_EXPR, MATCH_CASE, LAMBDA_EXPR, FIELD_GETTER,
                 NEW_DATA_EXPR, FIELD_INIT, UNREACHABLE_EXPR -> Lexis.NODE;
        };
    }

    /** A leaf, trivia included. What a node covers is the text of the leaves under it. */
    public boolean isToken() {
        return lexis() != Lexis.NODE;
    }

    /** Whitespace and comments: kept in the tree, invisible to the parser's token cursor. */
    public boolean isTrivia() {
        return this == WHITESPACE || this == LINE_COMMENT;
    }

    /**
     * How this kind is written, where the kind is what decides that.
     *
     * <p>Empty for a node and for an {@link Lexis#OPEN_TOKEN}. Every {@link Lexis#FIXED_TOKEN} has
     * one, and a build rule requires it rather than leaving it to whoever adds the next one. The two
     * lists are written separately so that rule can fail — deriving either from the other would make
     * it true by construction and catch nothing.
     */
    public Optional<String> fixedSpelling() {
        return Optional.ofNullable(switch (this) {
            case LBRACE -> "{";
            case RBRACE -> "}";
            case LPAREN -> "(";
            case RPAREN -> ")";
            case LBRACKET -> "[";
            case RBRACKET -> "]";
            case COLON -> ":";
            case COMMA -> ",";
            case DOT -> ".";
            case SPREAD -> "...";
            case ASSIGN -> "=";
            case PIPE -> "|";
            case ARROW -> "->";
            case PIPEFWD -> ">->";
            case VPIPE -> "|>";
            case QUESTION -> "?";
            case PLUSPLUS -> "++";
            case UNDERSCORE -> "_";
            case UNANSWERED -> "<?>";
            case EQ -> "==";
            case NE -> "/=";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case AND -> "&&";
            case OR -> "||";
            case PLUS -> "+";
            case MINUS -> "-";
            case STAR -> "*";
            case SLASH -> "/";
            case MODULE_KW -> "module";
            case IMPORT_KW -> "import";
            case EXPOSING_KW -> "exposing";
            case DATA_KW -> "data";
            case INVARIANT_KW -> "invariant";
            case ENSURES_KW -> "ensures";
            case AS_KW -> "as";
            case LET_KW -> "let";
            case GUARD_KW -> "guard";
            case ELSE_KW -> "else";
            case TRUE_KW -> "true";
            case FALSE_KW -> "false";
            case IF_KW -> "if";
            case THEN_KW -> "then";
            case BEHAVIOR_KW -> "behavior";
            case DEPENDS_KW -> "depends";
            case CONSTRUCTS_KW -> "constructs";
            case MATCH_KW -> "match";
            case WITH_KW -> "with";
            case UNREACHABLE_KW -> "unreachable";
            default -> null;
        });
    }


    /**
     * Whether a token of this kind stands where a name stands.
     *
     * <p>{@code _} is not a name — a name may not begin with one — but it is written where one
     * would be: a binding pattern, a departure arm, a fake table's default row. Every reader that
     * walks a node for the name it was written with reads this rather than asking for {@code IDENT},
     * because each of them asking separately is how the discard would go missing from one of them:
     * the builder would lose the arm, or the formatter would leave it out of what it writes back.
     */
    public boolean standsWhereANameStands() {
        return this == IDENT || this == UNDERSCORE;
    }

    /** A reader-facing name for an "expected X but found Y" message. A kind that spells itself is
     * locale-neutral and shown as that spelling in backticks ({@code `:`}); a category (a name, a
     * literal, end of input) is language-dependent and returned as a {@link Localizable}, localized
     * when the message is rendered. */
    public Object display() {
        Optional<String> spelled = fixedSpelling();
        if (spelled.isPresent()) {
            return "`" + spelled.get() + "`";
        }
        return switch (this) {
            case IDENT -> Localizable.of("tok.name");
            case INT_LIT -> Localizable.of("tok.integer");
            case DECIMAL_LIT -> Localizable.of("tok.decimal");
            case STRING_LIT -> Localizable.of("tok.string");
            case TYPEVAR -> Localizable.of("tok.typevar");
            case EOF -> Localizable.of("tok.eof");
            default -> "`" + name().toLowerCase(Locale.ROOT) + "`";
        };
    }
}
