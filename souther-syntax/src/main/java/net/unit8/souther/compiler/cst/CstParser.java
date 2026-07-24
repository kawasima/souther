package net.unit8.souther.compiler.cst;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A recursive-descent parser that builds a lossless concrete syntax tree from the
 * {@link CstLexer}'s token stream. It never throws: a mismatch becomes an {@link CstError} plus a
 * best-effort tree, and stray tokens are wrapped in {@code ERROR_TOKEN}-bearing nodes rather than
 * dropped, so the tree always covers the whole source.
 *
 * <p>The parser does no desugaring — {@code |>}, {@code require}, {@code let}, {@code ?}, and the
 * match destructuring stay as surface nodes. Lowering those to the compiler's {@code Ast} happens
 * in a later CST→AST pass, so this one tree serves the compiler, the formatter, and the LSP.
 */
public final class CstParser {

    /** The parse result: the red-tree root and the syntax errors gathered along the way. */
    public record Result(SyntaxNode root, List<CstError> errors) {
        public GreenNode green() {
            return root.green();
        }
    }

    private static final class Frame {
        final SyntaxKind kind;
        final List<Green> children = new ArrayList<>();

        Frame(SyntaxKind kind) {
            this.kind = kind;
        }
    }

    private final List<GreenToken> tokens;
    private final int[] offset;   // offset[i] = start offset of token i; offset[n] = source length
    private int pos = 0;          // index into tokens (may point at trivia)
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final List<CstError> errors = new ArrayList<>();

    private CstParser(List<GreenToken> tokens) {
        this.tokens = tokens;
        this.offset = new int[tokens.size() + 1];
        for (int i = 0; i < tokens.size(); i++) {
            offset[i + 1] = offset[i] + tokens.get(i).width();
        }
    }

    public static Result parse(String source) {
        CstLexer.Result lexed = CstLexer.lex(source);
        CstParser parser = new CstParser(lexed.tokens());
        parser.errors.addAll(lexed.errors());
        GreenNode root = parser.parseSourceFile();
        return new Result(SyntaxNode.root(root), List.copyOf(parser.errors));
    }

    // --- top level ---

    private GreenNode parseSourceFile() {
        Frame file = new Frame(SyntaxKind.SOURCE_FILE);
        stack.push(file);
        if (atContextual("examples")) {
            examplesFileHeader();
        } else if (at(SyntaxKind.MODULE_KW)) {
            moduleHeader();
        }
        while (at(SyntaxKind.IMPORT_KW)) {
            importDecl();
        }
        while (!at(SyntaxKind.EOF)) {
            if (at(SyntaxKind.DATA_KW)) {
                dataDef();
            } else if (at(SyntaxKind.BEHAVIOR_KW)) {
                behaviorDef();
            } else if (at(SyntaxKind.LET_KW)
                    || (atContextual("partial") && nth(1) == SyntaxKind.LET_KW)) {
                fnDef();
            } else if (atContextual("example")) {
                exampleDef();
            } else if (atContextual("fake")) {
                fakeDef();
            } else {
                recoverTopLevel();
            }
        }
        bumpEof();
        stack.pop();
        return Green.node(file.kind, file.children);
    }

    /** Wraps stray tokens (until the next top-level starter) in an ERROR node so the tree stays
     * whole even when the source is malformed. */
    private void recoverTopLevel() {
        error("parse.topdef", "expected data, behavior, let, or example");
        start(SyntaxKind.ERROR_TOKEN);
        do {
            bump();
        } while (!at(SyntaxKind.EOF) && !at(SyntaxKind.DATA_KW) && !at(SyntaxKind.BEHAVIOR_KW)
                && !at(SyntaxKind.LET_KW) && !at(SyntaxKind.IMPORT_KW)
                && !atContextual("example") && !atContextual("fake"));
        finish();
    }

    private void moduleHeader() {
        start(SyntaxKind.MODULE_HEADER);
        bump();   // module
        qualifiedName();
        if (at(SyntaxKind.EXPOSING_KW)) {
            exposingClause();
        }
        finish();
    }

    private void exposingClause() {
        start(SyntaxKind.EXPOSING_CLAUSE);
        bump();   // exposing
        expect(SyntaxKind.LPAREN);
        if (!at(SyntaxKind.RPAREN)) {
            exposedEntry();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                exposedEntry();
            }
        }
        expect(SyntaxKind.RPAREN);
        finish();
    }

    private void exposedEntry() {
        start(SyntaxKind.EXPOSED_ENTRY);
        qualifiedName();
        if (eat(SyntaxKind.COLON)) {
            retType();
        }
        finish();
    }

    private void importDecl() {
        start(SyntaxKind.IMPORT_DECL);
        bump();   // import
        qualifiedName();
        start(SyntaxKind.NAME_LIST);
        expect(SyntaxKind.LPAREN);
        if (!at(SyntaxKind.RPAREN)) {
            expect(SyntaxKind.IDENT);
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                expect(SyntaxKind.IDENT);
            }
        }
        expect(SyntaxKind.RPAREN);
        finish();   // NAME_LIST
        finish();   // IMPORT_DECL
    }

    private void qualifiedName() {
        start(SyntaxKind.QUALIFIED_NAME);
        expect(SyntaxKind.IDENT);
        while (at(SyntaxKind.DOT) && nth(1) == SyntaxKind.IDENT) {
            bump();   // .
            bump();   // ident
        }
        finish();
    }

    // --- data ---

    private void dataDef() {
        start(SyntaxKind.DATA_DEF);
        bump();   // data
        expect(SyntaxKind.IDENT);
        if (eat(SyntaxKind.ASSIGN)) {
            if (at(SyntaxKind.LBRACE)) {
                productBody();
            } else {
                sumOrNewtypeBody();
            }
        }
        while (at(SyntaxKind.INVARIANT_KW)) {
            invariantClause();
        }
        finish();
    }

    private void productBody() {
        start(SyntaxKind.PRODUCT_BODY);
        expect(SyntaxKind.LBRACE);
        if (!at(SyntaxKind.RBRACE)) {
            productMember();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RBRACE)) {
                    break;
                }
                productMember();
            }
        }
        expect(SyntaxKind.RBRACE);
        finish();
    }

    private void productMember() {
        if (at(SyntaxKind.SPREAD)) {
            start(SyntaxKind.SPREAD_MEMBER);
            bump();   // ...
            expect(SyntaxKind.IDENT);
            finish();
        } else {
            field();
        }
    }

    private void field() {
        start(SyntaxKind.FIELD);
        expect(SyntaxKind.IDENT);
        expect(SyntaxKind.COLON);
        typeRef();
        eat(SyntaxKind.QUESTION);   // `T?` optional field (Option<T>), lowered later
        finish();
    }

    /** {@code data X = A | B} is a sum; {@code data X = Y} (no {@code |}) is a newtype over Y. */
    private void sumOrNewtypeBody() {
        // one or more names separated by `|` → sum; a lone name → newtype
        if (nth(1) == SyntaxKind.PIPE) {
            start(SyntaxKind.SUM_BODY);
            expect(SyntaxKind.IDENT);
            while (eat(SyntaxKind.PIPE)) {
                expect(SyntaxKind.IDENT);
            }
            finish();
        } else {
            start(SyntaxKind.NEWTYPE_BODY);
            expect(SyntaxKind.IDENT);
            finish();
        }
    }

    private void invariantClause() {
        start(SyntaxKind.INVARIANT_CLAUSE);
        bump();   // invariant
        expr();
        finish();
    }

    // --- behavior ---

    private void behaviorDef() {
        start(SyntaxKind.BEHAVIOR_DEF);
        bump();   // behavior
        expect(SyntaxKind.IDENT);
        if (eat(SyntaxKind.COLON)) {
            behaviorSig();
        } else if (eat(SyntaxKind.ASSIGN)) {
            pipeBehavior();
        } else {
            error("parse.behavior.colon", "a behavior needs `:` (signature) or `=` (composition)");
        }
        finish();
    }

    private void behaviorSig() {
        start(SyntaxKind.BEHAVIOR_SIG);
        paramList();
        expect(SyntaxKind.ARROW);
        retType();
        boolean more = true;
        while (more) {
            if (at(SyntaxKind.CONSTRUCTS_KW)) {
                nameClause(SyntaxKind.CONSTRUCTS_CLAUSE);
            } else if (at(SyntaxKind.REQUIRES_KW)) {
                nameClause(SyntaxKind.REQUIRES_CLAUSE);
            } else {
                more = false;
            }
        }
        finish();
    }

    private void paramList() {
        start(SyntaxKind.PARAM_LIST);
        expect(SyntaxKind.LPAREN);
        if (!at(SyntaxKind.RPAREN)) {
            param();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                param();
            }
        }
        expect(SyntaxKind.RPAREN);
        finish();
    }

    private void param() {
        start(SyntaxKind.PARAM);
        expect(SyntaxKind.IDENT);
        expect(SyntaxKind.COLON);
        retType();
        finish();
    }

    /** A {@code constructs}/{@code requires} clause: the keyword then a bare comma list of names,
     * tolerating a trailing comma (which has no closing bracket to bound it). */
    private void nameClause(SyntaxKind kind) {
        start(kind);
        bump();   // constructs / requires
        expect(SyntaxKind.IDENT);
        while (eat(SyntaxKind.COMMA)) {
            if (!at(SyntaxKind.IDENT)) {
                break;   // a trailing comma is consumed and ends the list
            }
            bump();   // ident
        }
        finish();
    }

    private void pipeBehavior() {
        start(SyntaxKind.PIPE_BEHAVIOR);
        stage();
        while (eat(SyntaxKind.PIPEFWD)) {
            stage();
        }
        if (eat(SyntaxKind.ARROW)) {
            retType();
        }
        finish();
    }

    private void stage() {
        start(SyntaxKind.STAGE);
        expect(SyntaxKind.IDENT);
        if (at(SyntaxKind.DOT) && nth(1) == SyntaxKind.IDENT) {
            bump();   // .
            bump();   // ident
        }
        finish();
    }

    // --- fn ---

    private void fnDef() {
        start(SyntaxKind.FN_DEF);
        if (atContextual("partial")) {
            start(SyntaxKind.PARTIAL_MODIFIER);
            bump();   // partial (a contextual soft-keyword, kept out of the fn name)
            finish();
        }
        bump();   // let
        expect(SyntaxKind.IDENT);
        fnParamList();
        if (eat(SyntaxKind.COLON)) {
            retType();
        }
        expect(SyntaxKind.ASSIGN);
        if (at(SyntaxKind.IDENT) && current() == SyntaxKind.IDENT
                && tokenText(mi(0)).equals("intrinsic") && nth(1) == SyntaxKind.STRING_LIT) {
            start(SyntaxKind.INTRINSIC_BODY);
            bump();   // intrinsic
            bump();   // "key"
            finish();
        } else if (at(SyntaxKind.LBRACE)) {
            blockExpr();
        } else {
            expr();
        }
        finish();
    }

    private void fnParamList() {
        start(SyntaxKind.FN_PARAM_LIST);
        expect(SyntaxKind.LPAREN);
        if (!at(SyntaxKind.RPAREN)) {
            fnParam();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                fnParam();
            }
        }
        expect(SyntaxKind.RPAREN);
        finish();
    }

    private void fnParam() {
        start(SyntaxKind.FN_PARAM);
        expect(SyntaxKind.IDENT);
        if (eat(SyntaxKind.COLON)) {
            paramType();
        }
        finish();
    }

    /** A helper parameter's type: a function type when it opens with {@code (}, else an ordinary type. */
    private void paramType() {
        if (at(SyntaxKind.LPAREN)) {
            start(SyntaxKind.FN_TYPE);
            expect(SyntaxKind.LPAREN);
            if (!at(SyntaxKind.RPAREN)) {
                retType();
                while (eat(SyntaxKind.COMMA)) {
                    if (at(SyntaxKind.RPAREN)) {
                        break;
                    }
                    retType();
                }
            }
            expect(SyntaxKind.RPAREN);
            expect(SyntaxKind.ARROW);
            retType();
            finish();
        } else {
            retType();
        }
    }

    // --- example ---

    /** {@code examples for <module.path>} — the header of an attached example-only file. {@code for}
     * is a contextual soft-keyword (a bare identifier), so the {@code example.*} module namespace is
     * unaffected. */
    private void examplesFileHeader() {
        start(SyntaxKind.EXAMPLES_FILE_HEADER);
        bump();   // examples
        if (atContextual("for")) {
            bump();   // for
        } else {
            error("parse.examples.for", "expected `for` after `examples`");
        }
        qualifiedName();   // target module path
        finish();
    }

    /** {@code example <target>} then one-or-more {@code |}-led rows. {@code example} is a contextual
     * soft-keyword; the target names a behavior or a pure helper in this module. */
    private void exampleDef() {
        start(SyntaxKind.EXAMPLE_DEF);
        bump();   // example
        expect(SyntaxKind.IDENT);   // target name
        if (!at(SyntaxKind.PIPE)) {
            error("parse.example.row", "an example needs at least one `|` row");
        }
        while (eat(SyntaxKind.PIPE)) {
            exampleRow();
        }
        finish();
    }

    /** {@code [ "desc" : ] ( args ) -> expected} — an argument list, then the expected result
     * (a bare type name asserts the arm; a construction/literal asserts the whole value). */
    private void exampleRow() {
        start(SyntaxKind.EXAMPLE_ROW);
        if (at(SyntaxKind.STRING_LIT) && nth(1) == SyntaxKind.COLON) {
            bump();   // "description"
            bump();   // :
        }
        argList();   // the input tuple, reusing ARG_LIST
        if (at(SyntaxKind.WITH_KW)) {
            withClause();   // supplies fakes for the target's requires (value dependencies)
        }
        expect(SyntaxKind.ARROW);
        expr();      // expected
        finish();
    }

    /** {@code with <dep> = <expr> (, <dep> = <expr>)*} — value fakes for a behavior's requires. */
    private void withClause() {
        start(SyntaxKind.WITH_CLAUSE);
        bump();   // with
        withBinding();
        while (eat(SyntaxKind.COMMA)) {
            withBinding();
        }
        finish();
    }

    private void withBinding() {
        start(SyntaxKind.WITH_BINDING);
        expect(SyntaxKind.IDENT);   // the injected dependency name
        expect(SyntaxKind.ASSIGN);
        expr();                     // its faked value
        finish();
    }

    /** {@code fake <injected> | rows} — a function fake: a table of input→output rows for an injected
     * dependency. {@code fake} is contextual; the target is the second identifier. */
    private void fakeDef() {
        start(SyntaxKind.FAKE_DEF);
        bump();   // fake
        expect(SyntaxKind.IDENT);   // target injected behavior
        if (!at(SyntaxKind.PIPE)) {
            error("parse.fake.row", "a fake needs at least one `|` row");
        }
        while (eat(SyntaxKind.PIPE)) {
            fakeRow();
        }
        finish();
    }

    /** {@code ( args ) -> output} or {@code _ -> output} (a default). */
    private void fakeRow() {
        start(SyntaxKind.FAKE_ROW);
        if (atContextual("_")) {
            bump();   // _  (the wildcard default; `_` lexes as an identifier)
        } else {
            argList();
        }
        expect(SyntaxKind.ARROW);
        expr();
        finish();
    }

    // --- types ---

    private void retType() {
        start(SyntaxKind.RET_TYPE);
        typeRef();
        while (eat(SyntaxKind.PIPE)) {
            typeRef();
        }
        finish();
    }

    private void typeRef() {
        if (at(SyntaxKind.LPAREN)) {
            start(SyntaxKind.TUPLE_TYPE);
            bump();   // (
            if (!at(SyntaxKind.RPAREN)) {
                typeRef();
                while (eat(SyntaxKind.COMMA)) {
                    if (at(SyntaxKind.RPAREN)) {
                        break;
                    }
                    typeRef();
                }
            }
            expect(SyntaxKind.RPAREN);
            finish();
            return;
        }
        start(SyntaxKind.TYPE_REF);
        if (at(SyntaxKind.TYPEVAR)) {
            bump();
        } else {
            expect(SyntaxKind.IDENT);
            if (at(SyntaxKind.LT)) {
                typeArgs();
            }
        }
        finish();
    }

    private void typeArgs() {
        start(SyntaxKind.TYPE_ARGS);
        expect(SyntaxKind.LT);
        typeRef();
        while (eat(SyntaxKind.COMMA)) {
            if (at(SyntaxKind.GT)) {
                break;
            }
            typeRef();
        }
        expect(SyntaxKind.GT);
        finish();
    }

    // --- behavior body / block ---

    /** A brace-delimited block: {@code let}/{@code require} statements then a result expression. */
    private void blockExpr() {
        start(SyntaxKind.BLOCK_EXPR);
        expect(SyntaxKind.LBRACE);
        blockStatements();
        expect(SyntaxKind.RBRACE);
        finish();
    }

    /** The statement sequence a behavior body is: {@code let}/{@code require} lines then one result. */
    private void blockStatements() {
        while (true) {
            if (at(SyntaxKind.LET_KW)) {
                if (nth(1) == SyntaxKind.LPAREN) {
                    tupleDestructure();
                } else {
                    letStmt();
                }
            } else if (at(SyntaxKind.REQUIRE_KW)) {
                requireStmt();
            } else {
                break;
            }
        }
        if (!at(SyntaxKind.RBRACE) && !at(SyntaxKind.EOF)) {
            expr();   // the result expression
        }
    }

    private void letStmt() {
        start(SyntaxKind.LET_STMT);
        bump();   // let
        expect(SyntaxKind.IDENT);
        expect(SyntaxKind.ASSIGN);
        expr();
        finish();
    }

    private void tupleDestructure() {
        start(SyntaxKind.TUPLE_DESTRUCTURE);
        bump();   // let
        expect(SyntaxKind.LPAREN);
        if (!at(SyntaxKind.RPAREN)) {
            expect(SyntaxKind.IDENT);
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                expect(SyntaxKind.IDENT);
            }
        }
        expect(SyntaxKind.RPAREN);
        expect(SyntaxKind.ASSIGN);
        expr();
        finish();
    }

    private void requireStmt() {
        start(SyntaxKind.REQUIRE_STMT);
        bump();   // require
        expr();
        expect(SyntaxKind.ELSE_KW);
        expr();
        finish();
    }

    // --- expressions (precedence ladder; left-associative via wrap) ---

    private boolean noConstruct = false;

    private void expr() {
        pipeExpr();
    }

    private void pipeExpr() {
        int m = mark();
        orExpr();
        while (at(SyntaxKind.VPIPE)) {
            wrap(m, SyntaxKind.PIPE_EXPR);
            bump();   // |>
            orExpr();
            finish();
        }
    }

    private void orExpr() {
        int m = mark();
        andExpr();
        while (at(SyntaxKind.OR)) {
            wrap(m, SyntaxKind.BINARY_EXPR);
            bump();
            andExpr();
            finish();
        }
    }

    private void andExpr() {
        int m = mark();
        cmpExpr();
        while (at(SyntaxKind.AND)) {
            wrap(m, SyntaxKind.BINARY_EXPR);
            bump();
            cmpExpr();
            finish();
        }
    }

    private void cmpExpr() {
        int m = mark();
        addExpr();
        if (isCmpOp(current())) {
            wrap(m, SyntaxKind.BINARY_EXPR);
            bump();
            addExpr();
            finish();
        }
    }

    private void addExpr() {
        int m = mark();
        mulExpr();
        while (at(SyntaxKind.PLUS) || at(SyntaxKind.MINUS) || at(SyntaxKind.PLUSPLUS)) {
            wrap(m, SyntaxKind.BINARY_EXPR);
            bump();
            mulExpr();
            finish();
        }
    }

    private void mulExpr() {
        int m = mark();
        unaryExpr();
        while (at(SyntaxKind.STAR) || at(SyntaxKind.SLASH)) {
            wrap(m, SyntaxKind.BINARY_EXPR);
            bump();
            unaryExpr();
            finish();
        }
    }

    private void unaryExpr() {
        if (at(SyntaxKind.MINUS)) {
            start(SyntaxKind.UNARY_EXPR);
            bump();   // -
            unaryExpr();
            finish();
            return;
        }
        primaryExpr();
    }

    private static boolean isCmpOp(SyntaxKind k) {
        return k == SyntaxKind.EQ || k == SyntaxKind.NE || k == SyntaxKind.LT
                || k == SyntaxKind.LE || k == SyntaxKind.GT || k == SyntaxKind.GE;
    }

    private void primaryExpr() {
        SyntaxKind k = current();
        // a lambda: `x -> e`
        if (k == SyntaxKind.IDENT && nth(1) == SyntaxKind.ARROW) {
            start(SyntaxKind.LAMBDA_EXPR);
            bump();   // param
            bump();   // ->
            expr();
            finish();
            return;
        }
        // a parenthesised lambda: `(a, b) -> e`
        if (k == SyntaxKind.LPAREN && isBlockParams()) {
            parenLambda();
            return;
        }
        switch (k) {
            case MATCH_KW -> matchExpr();
            case IF_KW -> ifExpr();
            case INT_LIT, DECIMAL_LIT, STRING_LIT, TRUE_KW, FALSE_KW -> {
                start(SyntaxKind.LITERAL_EXPR);
                bump();
                finish();
            }
            case LPAREN -> parenOrTuple();
            case LBRACKET -> listExpr();
            case LBRACE -> blockExpr();
            case IDENT -> identExpr();
            default -> {
                error("parse.expr", "expected an expression");
                start(SyntaxKind.ERROR_TOKEN);
                finish();   // zero-width error node; the caller resynchronises
            }
        }
    }

    /** {@code (x, ...) -> body} — the caller has confirmed the shape via {@link #isBlockParams}. */
    private void parenLambda() {
        start(SyntaxKind.LAMBDA_EXPR);
        expect(SyntaxKind.LPAREN);
        if (!at(SyntaxKind.RPAREN)) {
            expect(SyntaxKind.IDENT);
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                expect(SyntaxKind.IDENT);
            }
        }
        expect(SyntaxKind.RPAREN);
        expect(SyntaxKind.ARROW);
        expr();
        finish();
    }

    /** {@code ( e )} or {@code ( e1, e2, ... )} — a parenthesised expression or a tuple. */
    private void parenOrTuple() {
        start(SyntaxKind.PAREN_EXPR);
        bump();   // (
        expr();
        if (at(SyntaxKind.COMMA)) {
            // a tuple: retag the just-opened PAREN_EXPR as a TUPLE_EXPR
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                expr();
            }
            expect(SyntaxKind.RPAREN);
            retagTop(SyntaxKind.TUPLE_EXPR);
            finish();
            return;
        }
        expect(SyntaxKind.RPAREN);
        finish();
    }

    /** {@code [e, ...]} (a literal) or {@code [element | guard, ...]} (a guard comprehension). */
    private void listExpr() {
        start(SyntaxKind.LIST_EXPR);
        bump();   // [
        if (at(SyntaxKind.RBRACKET)) {
            bump();
            finish();
            return;
        }
        expr();
        if (eat(SyntaxKind.PIPE)) {
            expr();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RBRACKET)) {
                    break;
                }
                expr();
            }
            expect(SyntaxKind.RBRACKET);
            retagTop(SyntaxKind.LIST_COMP);
            finish();
            return;
        }
        while (eat(SyntaxKind.COMMA)) {
            if (at(SyntaxKind.RBRACKET)) {
                break;
            }
            expr();
        }
        expect(SyntaxKind.RBRACKET);
        finish();
    }

    private void ifExpr() {
        start(SyntaxKind.IF_EXPR);
        bump();   // if
        expr();
        expect(SyntaxKind.THEN_KW);
        expr();
        expect(SyntaxKind.ELSE_KW);
        expr();
        finish();
    }

    private void matchExpr() {
        start(SyntaxKind.MATCH_EXPR);
        bump();   // match
        boolean saved = noConstruct;
        noConstruct = true;
        expr();   // scrutinee
        noConstruct = saved;
        expect(SyntaxKind.WITH_KW);
        eat(SyntaxKind.PIPE);   // optional leading `|`
        matchCase();
        while (eat(SyntaxKind.PIPE)) {
            matchCase();
        }
        finish();
    }

    /** {@code A [| B ...] [binding] [{ fields }] [as x] -> body} — kept structural for lowering. */
    private void matchCase() {
        start(SyntaxKind.MATCH_CASE);
        expect(SyntaxKind.IDENT);
        while (at(SyntaxKind.PIPE) && nth(1) == SyntaxKind.IDENT) {
            // an or-pattern alternative; a `|` that begins the next case is followed by the arrow
            // path instead. Only consume `|` here when another case name follows.
            bump();   // |
            bump();   // ident
        }
        // newtype constructor destructuring `X(inner)`, nestable `X(Y(s))` — the inverse of
        // construction `X(v)`. It opens the case's newtype value; the inner `Y(...)` opens another.
        // A destructure and Option's positional binding `Some v` are mutually exclusive, so a stray
        // ident after the parens is left for `expect(ARROW)` to reject rather than silently consumed.
        if (at(SyntaxKind.LPAREN)) {
            casePattern();
        } else if (at(SyntaxKind.IDENT)) {
            bump();
        }
        // field destructuring `{ field [= var], ... }`
        if (at(SyntaxKind.LBRACE)) {
            bump();   // {
            if (!at(SyntaxKind.RBRACE)) {
                expect(SyntaxKind.IDENT);
                if (eat(SyntaxKind.ASSIGN)) {
                    expect(SyntaxKind.IDENT);
                }
                while (eat(SyntaxKind.COMMA)) {
                    if (at(SyntaxKind.RBRACE)) {
                        break;
                    }
                    expect(SyntaxKind.IDENT);
                    if (eat(SyntaxKind.ASSIGN)) {
                        expect(SyntaxKind.IDENT);
                    }
                }
            }
            expect(SyntaxKind.RBRACE);
        }
        // whole-value binding `as x`
        if (eat(SyntaxKind.AS_KW)) {
            expect(SyntaxKind.IDENT);
        }
        expect(SyntaxKind.ARROW);
        expr();
        finish();
    }

    /** {@code ( IDENT [casePattern] )} — a newtype-destructuring sub-pattern, nestable for a
     * newtype over a newtype. Kept structural (every token bumped) so the tree stays lossless. */
    private void casePattern() {
        expect(SyntaxKind.LPAREN);
        expect(SyntaxKind.IDENT);
        if (at(SyntaxKind.LPAREN)) {
            casePattern();
        }
        expect(SyntaxKind.RPAREN);
    }

    /** An identifier-led primary: a call, a qualified call, a construction, or a field-access chain. */
    private void identExpr() {
        // qualified call `Mod.name(args)`
        if (nth(1) == SyntaxKind.DOT && nth(2) == SyntaxKind.IDENT && nth(3) == SyntaxKind.LPAREN) {
            start(SyntaxKind.CALL_EXPR);
            bump();   // Mod
            bump();   // .
            bump();   // name
            argList();
            finish();
            return;
        }
        // plain call `name(args)`
        if (nth(1) == SyntaxKind.LPAREN) {
            start(SyntaxKind.CALL_EXPR);
            bump();   // name
            argList();
            finish();
            return;
        }
        // construction `Type { ... }` (unless suppressed, as in a match scrutinee)
        if (!noConstruct && nth(1) == SyntaxKind.LBRACE) {
            newDataExpr();
            return;
        }
        // a bare variable or a field-access chain `a.b.c`
        start(SyntaxKind.VAR_EXPR);
        bump();   // ident
        finish();
        while (at(SyntaxKind.DOT) && nth(1) == SyntaxKind.IDENT) {
            int m = markForFieldAccess();
            wrap(m, SyntaxKind.FIELD_ACCESS);
            bump();   // .
            bump();   // field
            finish();
        }
    }

    private void newDataExpr() {
        start(SyntaxKind.NEW_DATA_EXPR);
        bump();   // Type
        expect(SyntaxKind.LBRACE);
        if (!at(SyntaxKind.RBRACE)) {
            initElem();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RBRACE)) {
                    break;
                }
                initElem();
            }
        }
        expect(SyntaxKind.RBRACE);
        finish();
    }

    private void initElem() {
        if (at(SyntaxKind.SPREAD)) {
            start(SyntaxKind.SPREAD_MEMBER);
            bump();   // ...
            expect(SyntaxKind.IDENT);
            finish();
        } else {
            start(SyntaxKind.FIELD_INIT);
            expect(SyntaxKind.IDENT);
            if (eat(SyntaxKind.ASSIGN)) {
                expr();
            }
            finish();
        }
    }

    private void argList() {
        start(SyntaxKind.ARG_LIST);
        expect(SyntaxKind.LPAREN);
        if (!at(SyntaxKind.RPAREN)) {
            arg();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                arg();
            }
        }
        expect(SyntaxKind.RPAREN);
        finish();
    }

    /** An argument is an expression, including a bare lambda {@code x -> e} / {@code (a, b) -> e}. */
    private void arg() {
        expr();
    }

    // --- the wrap marker used by the binary builders and field access ---

    /** The number of children currently in the open frame — a checkpoint the left operand sits after. */
    private int mark() {
        return stack.peek().children.size();
    }

    /** Field access wraps the immediately preceding primary (the last child), so its checkpoint is
     * one before the end. */
    private int markForFieldAccess() {
        return stack.peek().children.size() - 1;
    }

    /**
     * Wraps the children from {@code mark} onward into a new open node of {@code kind} (left-recursive
     * builder pattern): the moved children become the new node's first children, and the node is
     * appended back at the same position when {@link #finish()} closes it.
     */
    private void wrap(int mark, SyntaxKind kind) {
        List<Green> top = stack.peek().children;
        Frame f = new Frame(kind);
        List<Green> tail = top.subList(mark, top.size());
        f.children.addAll(tail);
        tail.clear();
        stack.push(f);
    }

    /** Replaces the open frame's kind (used to retag a PAREN_EXPR as a TUPLE_EXPR once a comma is
     * seen, or a LIST_EXPR as a LIST_COMP). */
    private void retagTop(SyntaxKind kind) {
        Frame old = stack.pop();
        Frame f = new Frame(kind);
        f.children.addAll(old.children);
        stack.push(f);
    }

    // --- builder / cursor primitives ---

    private void start(SyntaxKind kind) {
        stack.push(new Frame(kind));
    }

    private void finish() {
        Frame f = stack.pop();
        stack.peek().children.add(Green.node(f.kind, f.children));
    }

    /** Flushes trivia preceding the next meaningful token, then emits that token. */
    private void bump() {
        while (pos < tokens.size() && tokens.get(pos).kind().isTrivia()) {
            stack.peek().children.add(tokens.get(pos));
            pos++;
        }
        if (pos < tokens.size()) {
            stack.peek().children.add(tokens.get(pos));
            if (tokens.get(pos).kind() != SyntaxKind.EOF) {
                pos++;
            }
        }
    }

    /** Flushes trailing trivia and the final EOF token into the (root) frame. */
    private void bumpEof() {
        while (pos < tokens.size() && tokens.get(pos).kind().isTrivia()) {
            stack.peek().children.add(tokens.get(pos));
            pos++;
        }
        if (pos < tokens.size() && tokens.get(pos).kind() == SyntaxKind.EOF) {
            stack.peek().children.add(tokens.get(pos));
            pos++;
        }
    }

    private boolean at(SyntaxKind kind) {
        return current() == kind;
    }

    /** True when the current meaningful token is an identifier with the given text — used for the
     * contextual soft-keywords {@code example} / {@code examples} / {@code for}, which stay ordinary
     * identifiers everywhere else. */
    private boolean atContextual(String text) {
        return at(SyntaxKind.IDENT) && tokenText(mi(0)).equals(text);
    }

    private boolean eat(SyntaxKind kind) {
        if (at(kind)) {
            bump();
            return true;
        }
        return false;
    }

    private void expect(SyntaxKind kind) {
        if (at(kind)) {
            bump();
            return;
        }
        SyntaxKind found = current();
        error("parse.expected", "expected " + kind + " but found " + found,
                kind.display(), found.display());
    }

    /** The kind of the next meaningful token. */
    private SyntaxKind current() {
        return tokens.get(mi(0)).kind();
    }

    /** The kind of the nth meaningful token ahead (0 = current), stopping at EOF. */
    private SyntaxKind nth(int n) {
        return tokens.get(mi(n)).kind();
    }

    /** The token index of the nth meaningful token ahead of {@code pos}. */
    private int mi(int n) {
        int i = pos;
        int seen = 0;
        while (i < tokens.size()) {
            if (!tokens.get(i).kind().isTrivia()) {
                if (seen == n) {
                    return i;
                }
                if (tokens.get(i).kind() == SyntaxKind.EOF) {
                    return i;   // never advance past EOF
                }
                seen++;
            }
            i++;
        }
        return tokens.size() - 1;
    }

    private String tokenText(int index) {
        return tokens.get(index).text();
    }

    /** Distinguishes {@code (a, b) ->} from a parenthesised expression by scanning to the {@code )}. */
    private boolean isBlockParams() {
        int n = 1;
        if (nth(n) != SyntaxKind.IDENT) {
            return false;
        }
        n++;
        while (nth(n) == SyntaxKind.COMMA) {
            n++;
            if (nth(n) == SyntaxKind.RPAREN) {
                break;   // trailing comma
            }
            if (nth(n) != SyntaxKind.IDENT) {
                return false;
            }
            n++;
        }
        return nth(n) == SyntaxKind.RPAREN && nth(n + 1) == SyntaxKind.ARROW;
    }

    private void error(String messageKey, String legacyMessage, Object... args) {
        int i = mi(0);
        int width = Math.max(1, tokens.get(i).width());
        errors.add(new CstError(offset[i], width, messageKey, legacyMessage, args));
    }
}
