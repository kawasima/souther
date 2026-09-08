package souther.compiler.cst;

import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.Message;
import souther.compiler.diag.msg.Reported;
import souther.compiler.diag.msg.ParseMessage;

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
 * <p>A source that nests deeper than {@code MAX_DEPTH} is one of those mismatches: the tree comes
 * back bounded to that depth and an error says why. That bound is the parser's, but it is not kept
 * by the parser — every walk over this tree descends it by recursion and so costs stack in
 * proportion to {@link Green#depth()}, and a walker that takes a tree from here has the room for it
 * without asking. A source nested past what the walks can follow is refused once, here, where the
 * level that overran is a token with a position, rather than found again by each walk where the
 * stack happens to end.
 *
 * <p>The parser does no desugaring — {@code |>}, {@code guard}, {@code let}, {@code ?}, and the
 * match destructuring stay as surface nodes. Lowering those to the compiler's {@code Ast} happens
 * in a later CST→AST pass, so this one tree serves the compiler, the formatter, and the LSP.
 */
public final class CstParser {

    /** The parse result: the red-tree root and the syntax errors gathered along the way. */
    public record Result(SyntaxNode root, List<CstError<?>> errors) {
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

    /**
     * Raised where the source nests past {@link #MAX_DEPTH}, and caught by {@link #parseSourceFile}.
     *
     * <p>It is control flow, not a failure: it ends a descent that thirty-six productions are in the
     * middle of, without each of them having to agree to end it. A limit every production has to
     * remember is a limit the next production written will not, and the grammar closes its cycles in
     * more places than one reading finds — an expression through a parenthesis, a lambda or an arm, a
     * type through its arguments, a pattern through a tuple, and a unary minus straight back into
     * itself, which is a cycle that never passes through {@code expr} at all.
     *
     * <p>What every one of them does pass through is {@link #start}: to nest, a production must open
     * a node. Refusing there is the one refusal none of them can miss, and a production added later
     * inherits it without knowing it is there.
     *
     * <p>It never leaves this class, so the parser's promise not to throw is kept. No stack trace is
     * filled in: nothing reads it, and the depth this unwinds is the reason to not pay for one.
     */
    private static final class TooDeep extends RuntimeException {
        private static final long serialVersionUID = 1L;
        TooDeep() {
            super(null, null, false, false);
        }
    }

    /**
     * The deepest tree this parser will build. Past it the parse stops nesting and says so.
     *
     * <p>Left unwritten, the limit is still there — it is the stack, and every walk over the tree
     * finds it by running out. What "too deep" means is then the thread's stack divided by whatever
     * frame the JIT chose for the walk, so the same source is accepted on one run and refused on the
     * next: measured before this existed, {@code souther fmt --check} on one unchanged file came
     * back clean seven times out of fourteen. A written limit is the same answer everywhere, it is
     * reached at a token and so has a position to report, and a walker downstream inherits it
     * without knowing it is there.
     *
     * <p>Set well above what written code reaches and well below what survives the deepest thing
     * done with the result: parsing and then formatting a nest costs about thirteen stack frames per
     * level, and manages 91 levels on a 256 KB stack — less room than any thread the compiler runs
     * on, and the least any test here gives it. Sixty-four leaves that margin whole, and leaves an
     * expression far more nesting than one a reader can follow.
     *
     * <p>Published because it is what a caller inherits: {@link Green#depth()} of a tree from
     * {@link #parse} never exceeds it. A walk that descends this tree by recursion — which is every
     * walk there is — needs no limit of its own.
     */
    public static final int MAX_DEPTH = 64;

    private final List<GreenToken> tokens;
    private final int[] offset;   // offset[i] = start offset of token i; offset[n] = source length
    private int pos = 0;          // index into tokens (may point at trivia)
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final List<CstError<?>> errors = new ArrayList<>();
    /** The arm column of each match currently parsing its arms, innermost on top. A nested match
     * stops at a `|` that reaches back to one of these columns. */
    private final Deque<Integer> matchArmColumns = new ArrayDeque<>();

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
        try {
            topLevelItems();
        } catch (TooDeep _) {
            closeOverTheRest(file);
        }
        stack.pop();
        return Green.node(file.kind, file.children);
    }

    /**
     * Ends a parse that reached {@link #MAX_DEPTH}: every frame still open hands its children to the
     * one below rather than becoming a node, and the tokens the descent never reached are taken as
     * they were written.
     *
     * <p>Nothing is dropped, so the tree still covers the whole source — an editor keeps showing the
     * file it could not read — and nothing is added, so the depth the refusal was made at is the
     * depth the tree comes back with.
     */
    private void closeOverTheRest(Frame file) {
        while (stack.peek() != file) {
            Frame f = stack.pop();
            stack.peek().children.addAll(f.children);
        }
        List<Green> unread = new ArrayList<>();
        while (pos < tokens.size()) {
            unread.add(tokens.get(pos));
            pos++;
        }
        if (!unread.isEmpty()) {
            file.children.add(Green.node(SyntaxKind.ERROR_TOKEN, unread));
        }
    }

    /**
     * A file: its header, then its imports, then everything else.
     *
     * <p>Which words open which form is {@link TopLevelForm}'s, so the conditions here are asked of
     * it rather than spelled again. What each form is read by is this parser's, and it is the one
     * thing said here.
     */
    private void topLevelItems() {
        if (starts(TopLevelForm.EXAMPLES_FILE_HEADER)) {
            examplesFileHeader();
        } else if (starts(TopLevelForm.MODULE_HEADER)) {
            moduleHeader();
        }
        while (starts(TopLevelForm.IMPORT)) {
            importDecl();
        }
        while (!at(SyntaxKind.EOF)) {
            if (starts(TopLevelForm.DATA)) {
                dataDef();
            } else if (starts(TopLevelForm.BEHAVIOR)) {
                behaviorDef();
            } else if (starts(TopLevelForm.FN)) {
                fnDef();
            } else if (starts(TopLevelForm.EXAMPLE)) {
                exampleDef();
            } else if (starts(TopLevelForm.FAKE)) {
                fakeDef();
            } else {
                recoverTopLevel();
            }
        }
        bumpEof();
    }

    /**
     * Wraps stray tokens in an ERROR node so the tree stays whole even when the source is malformed,
     * up to the point the parse can pick itself up again.
     *
     * <p>Where that is, is this parser's answer about a mistake rather than a fact about the
     * language, so it is written here as a rule over the catalog: at anything that may open
     * something after the header. A header form does not stop it — a file has one header and it is
     * at the top, so a {@code module} line further down is not a beginning to resume at.
     */
    private void recoverTopLevel() {
        error(new ParseMessage.ATopLevelDefinitionStartsWithAKeyword());
        start(SyntaxKind.ERROR_TOKEN);
        do {
            bump();
        } while (!at(SyntaxKind.EOF) && !atResumePoint());
        finish();
    }

    /** Whether something that may follow a file's header opens here. */
    private boolean atResumePoint() {
        return TopLevelForm.at(ahead)
                .filter(form -> form.region() != TopLevelForm.Region.FILE_HEADER)
                .isPresent();
    }

    private boolean starts(TopLevelForm form) {
        return form.startsAt(ahead);
    }

    /** The meaningful tokens ahead of the cursor, as the catalog reads them. */
    private final TopLevelForm.Lookahead ahead = new TopLevelForm.Lookahead() {
        @Override
        public SyntaxKind kindAt(int i) {
            return nth(i);
        }

        @Override
        public String textAt(int i) {
            return tokenText(mi(i));
        }
    };

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
        expect(SyntaxKind.LPAREN, Reading.A_DECLARATION);
        if (!at(SyntaxKind.RPAREN)) {
            exposedEntry();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                exposedEntry();
            }
        }
        expect(SyntaxKind.RPAREN, Reading.A_DECLARATION);
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
        if (at(SyntaxKind.AS_KW)) {
            start(SyntaxKind.IMPORT_ALIAS);
            bump();   // as
            expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
            finish();
        }
        // The name list is what an import adds to the bare names in scope; an import that only
        // renames the module (`import a.b as B`) or only names the dependency has none.
        if (at(SyntaxKind.LPAREN)) {
            start(SyntaxKind.NAME_LIST);
            bump();   // (
            if (!at(SyntaxKind.RPAREN)) {
                expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
                while (eat(SyntaxKind.COMMA)) {
                    if (at(SyntaxKind.RPAREN)) {
                        break;
                    }
                    expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
                }
            }
            expect(SyntaxKind.RPAREN, Reading.A_DECLARATION);
            finish();   // NAME_LIST
        }
        finish();   // IMPORT_DECL
    }

    private void qualifiedName() {
        start(SyntaxKind.QUALIFIED_NAME);
        expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
        dottedTail();
        finish();
    }

    // --- data ---

    private void dataDef() {
        start(SyntaxKind.DATA_DEF);
        bump();   // data
        expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
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
        expect(SyntaxKind.LBRACE, Reading.A_DECLARATION);
        if (!at(SyntaxKind.RBRACE)) {
            productMember();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RBRACE)) {
                    break;
                }
                productMember();
            }
        }
        expect(SyntaxKind.RBRACE, Reading.A_DECLARATION);
        finish();
    }

    private void productMember() {
        if (at(SyntaxKind.SPREAD)) {
            start(SyntaxKind.SPREAD_MEMBER);
            bump();   // ...
            expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
            finish();
        } else {
            field();
        }
    }

    private void field() {
        start(SyntaxKind.FIELD);
        String name = at(SyntaxKind.IDENT) ? tokenText(mi(0)) : "?";
        expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
        expect(SyntaxKind.COLON, Reading.A_DECLARATION);
        // `T?` is a field's own optional (Option<T>), lowered later; a `|` is not a field's to write
        narrowType(true, new ParseMessage.AFieldTypeIsNotAnAnonymousUnion(name));
        finish();
    }

    /** {@code data X = A | B} is a sum; {@code data X = Y} (no {@code |}) is a newtype over Y. */
    private void sumOrNewtypeBody() {
        // a sum case is always a bare, undotted, ungeneric name, so `|` at the second token is an
        // exact test for a sum; anything else opens a newtype over a written type
        if (nth(1) == SyntaxKind.PIPE) {
            start(SyntaxKind.SUM_BODY);
            expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
            while (eat(SyntaxKind.PIPE)) {
                expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
            }
            finish();
        } else {
            start(SyntaxKind.NEWTYPE_BODY);
            newtypeBase();
            finish();
        }
    }

    /** The type a newtype wraps. It is written like any other type, but it must have an external
     * representation to hand up — the newtype takes it as its own — and it must be one type rather
     * than a choice between several. A shape the parser can see is refused where it is written,
     * ahead of the codec derivation that would otherwise report it as a field named {@code value};
     * what only the resolved type tells apart is left to the checker. */
    private void newtypeBase() {
        if (at(SyntaxKind.LPAREN)) {
            if (atFnTypeParams()) {
                error(new ParseMessage.ANewtypeCannotWrapAFunction());
            } else {
                error(new ParseMessage.ANewtypeCannotWrapATuple());
            }
        }
        typeRef();
        eat(SyntaxKind.QUESTION);   // `Y?`, kept for the AST to read as Option<Y> and the checker to refuse
        if (at(SyntaxKind.PIPE)) {
            error(new ParseMessage.ASumsCasesAreDeclaredNamedData());
        }
    }

    /** {@code invariant [ <name> = ] <expr>} — a clause, named or not. A name is what an attempt's
     * departure arm and a boundary issue call the rule, so only a named clause can be told apart from
     * the others; an unnamed one is enforced and never classified. Souther has no assignment and
     * spells equality {@code ==}, so an unnamed clause cannot begin {@code <name> =}. */
    private void invariantClause() {
        start(SyntaxKind.INVARIANT_CLAUSE);
        bump();   // invariant
        // `_` is read here as well as a name, so that a clause written `_ =` is answered by the rule
        // it breaks — a clause named `_` could never be answered by name — rather than by a reader
        // being told that `_` is not a name and left to work out why one would have wanted it.
        if ((at(SyntaxKind.IDENT) || at(SyntaxKind.UNDERSCORE)) && nth(1) == SyntaxKind.ASSIGN) {
            bump();   // the clause name
            bump();   // =
        }
        expr();
        finish();
    }

    // --- behavior ---

    private void behaviorDef() {
        start(SyntaxKind.BEHAVIOR_DEF);
        bump();   // behavior
        expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
        if (eat(SyntaxKind.COLON)) {
            behaviorSig();
        } else if (eat(SyntaxKind.ASSIGN)) {
            pipeBehavior();
            while (at(SyntaxKind.ENSURES_KW)) {
                ensuresClause();
            }
        } else {
            error(new ParseMessage.ABehaviorIsWrittenWithAColonOrAnEquals());
        }
        finish();
    }

    private void behaviorSig() {
        start(SyntaxKind.BEHAVIOR_SIG);
        paramList();
        expect(SyntaxKind.ARROW, Reading.A_DECLARATION);
        retType();
        boolean more = true;
        while (more) {
            if (at(SyntaxKind.CONSTRUCTS_KW)) {
                nameClause(SyntaxKind.CONSTRUCTS_CLAUSE);
            } else if (at(SyntaxKind.DEPENDS_KW)) {
                dependsClause();
            } else if (at(SyntaxKind.ENSURES_KW)) {
                ensuresClause();
            } else {
                more = false;
            }
        }
        finish();
    }

    /** A behavior postcondition, either one expression over a single output type or one or more
     * output-case arms. The latter is recognized only when the leading qualified-name sequence is
     * followed by {@code ->}; equality is {@code ==}, so an ordinary expression beginning with a
     * name cannot be mistaken for a named arm. */
    private void ensuresClause() {
        start(SyntaxKind.ENSURES_CLAUSE);
        bump();   // ensures
        if ((at(SyntaxKind.IDENT) || at(SyntaxKind.UNDERSCORE)) && nth(1) == SyntaxKind.ASSIGN) {
            bump();
            bump();
        }
        if (atEnsuresArm()) {
            ensuresArm();
            while (at(SyntaxKind.PIPE) && atEnsuresArmFrom(1)) {
                bump();
                ensuresArm();
            }
        } else {
            expr();
        }
        finish();
    }

    private void ensuresArm() {
        start(SyntaxKind.ENSURES_ARM);
        qualifiedName();
        // A pipe joins another case to this arm only where a case still stands before the arrow;
        // where it does not, the pipe begins the next arm and `ensuresClause` takes it. Which of the
        // two it is is one question, so it is one reading of the tokens ahead.
        while (at(SyntaxKind.PIPE) && atEnsuresArmFrom(1)) {
            bump();
            qualifiedName();
        }
        expect(SyntaxKind.ARROW, Reading.A_DECLARATION);
        expr();
        finish();
    }

    private boolean atEnsuresArm() {
        return atEnsuresArmFrom(0);
    }

    /** Whether a qualified-name list beginning {@code offset} tokens ahead ends at an arrow. */
    private boolean atEnsuresArmFrom(int offset) {
        int i = offset;
        if (nth(i) != SyntaxKind.IDENT) {
            return false;
        }
        while (true) {
            i++;
            while (nth(i) == SyntaxKind.DOT && nth(i + 1) == SyntaxKind.IDENT) {
                i += 2;
            }
            if (nth(i) == SyntaxKind.ARROW) {
                return true;
            }
            if (nth(i) != SyntaxKind.PIPE || nth(i + 1) != SyntaxKind.IDENT) {
                return false;
            }
            i++;
        }
    }

    private void paramList() {
        start(SyntaxKind.PARAM_LIST);
        expect(SyntaxKind.LPAREN, Reading.A_DECLARATION);
        if (!at(SyntaxKind.RPAREN)) {
            param();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                param();
            }
        }
        expect(SyntaxKind.RPAREN, Reading.A_DECLARATION);
        finish();
    }

    private void param() {
        start(SyntaxKind.PARAM);
        expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
        expect(SyntaxKind.COLON, Reading.A_DECLARATION);
        retType();
        finish();
    }

    /** A {@code constructs} clause: the keyword then a bare comma list of names, tolerating a
     * trailing comma (which has no closing bracket to bound it). Either clause names through a
     * module, so a behavior declares what it builds and what it depends on the same way it writes
     * any other name. */
    private void nameClause(SyntaxKind kind) {
        start(kind);
        bump();   // constructs
        nameList();
        finish();
    }

    /** A {@code depends on} clause. {@code on} is a contextual soft-keyword (a bare identifier), so
     * a field or a parameter may still be named on; only the position right after {@code depends}
     * reads it as the second half of the keyword. */
    private void dependsClause() {
        start(SyntaxKind.DEPENDS_CLAUSE);
        bump();   // depends
        if (atContextual("on")) {
            bump();   // on
        } else {
            error(new ParseMessage.ADependencyClauseIsTwoWords());
        }
        nameList();
        finish();
    }

    private void nameList() {
        expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
        dottedTail();
        while (eat(SyntaxKind.COMMA)) {
            if (!at(SyntaxKind.IDENT)) {
                break;   // a trailing comma is consumed and ends the list
            }
            bump();   // ident
            dottedTail();
        }
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
        expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
        dottedTail();
        finish();
    }

    // --- fn ---

    /** Whether a modifier opens a {@code let}: {@code private let}, {@code partial let}, or
     * {@code private partial let}. Both are contextual soft-keywords, so a parameter or field may
     * still be named {@code private} or {@code partial} — only the word directly before a
     * {@code let} (or before the other modifier and then a {@code let}) is read as one. */
    private void fnDef() {
        start(SyntaxKind.FN_DEF);
        if (atContextual("private")) {
            start(SyntaxKind.PRIVATE_MODIFIER);
            bump();   // private (a contextual soft-keyword, kept out of the fn name)
            finish();
        }
        if (atContextual("partial")) {
            start(SyntaxKind.PARTIAL_MODIFIER);
            bump();   // partial (a contextual soft-keyword, kept out of the fn name)
            finish();
        }
        bump();   // let
        String name = at(SyntaxKind.IDENT) ? tokenText(mi(0)) : "?";
        expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
        if (at(SyntaxKind.LPAREN)) {
            fnParamList(name);   // with none written the definition is a value, and there is no list
        }
        if (eat(SyntaxKind.COLON)) {
            retType();
        }
        expect(SyntaxKind.ASSIGN, Reading.A_DECLARATION);
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

    /** The parenthesized parameters of a function definition. It is written only where there are
     * parameters — an empty {@code ()} would be a second spelling of the value form, so it is
     * refused rather than read as a definition taking nothing. */
    private void fnParamList(String name) {
        start(SyntaxKind.FN_PARAM_LIST);
        expect(SyntaxKind.LPAREN, Reading.A_DECLARATION);
        if (at(SyntaxKind.RPAREN)) {
            error(new ParseMessage.ALetWithNoParametersIsWrittenWithoutParens(name));
        }
        if (!at(SyntaxKind.RPAREN)) {
            fnParam();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                fnParam();
            }
        }
        expect(SyntaxKind.RPAREN, Reading.A_DECLARATION);
        finish();
    }

    /** A helper's parameter: a name, or a pattern that opens what the parameter receives. A plain
     * name stays a bare token — it is what almost every parameter is, and the tree it makes is the
     * one every reader of {@code FN_PARAM} already expects. */
    private void fnParam() {
        start(SyntaxKind.FN_PARAM);
        if (at(SyntaxKind.LPAREN) || at(SyntaxKind.LBRACE) || atCtorPattern()) {
            pattern();
        } else {
            expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
        }
        if (eat(SyntaxKind.COLON)) {
            paramType();
        }
        finish();
    }

    /** A helper parameter's type. A function type is an ordinary type form, so this is what every
     * other type position reads; the name is kept for the one caller that reads a parameter. */
    private void paramType() {
        retType();
    }

    /** A function type {@code (A, B) -> C}. Its result is a whole type, so {@code ->} is
     * right-associative and {@code (A) -> B | C} keeps reading as {@code (A) -> (B | C)}. */
    private void fnType() {
        start(SyntaxKind.FN_TYPE);
        expect(SyntaxKind.LPAREN, Reading.A_DECLARATION);
        if (!at(SyntaxKind.RPAREN)) {
            retType();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                retType();
            }
        }
        expect(SyntaxKind.RPAREN, Reading.A_DECLARATION);
        expect(SyntaxKind.ARROW, Reading.A_DECLARATION);
        retType();
        finish();
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
            error(new ParseMessage.AnExampleOnlyFileStartsWithItsModule());
        }
        qualifiedName();   // target module path
        finish();
    }

    /** {@code example <target>} then one-or-more {@code |}-led rows. {@code example} is a contextual
     * soft-keyword; the target names a behavior or a pure helper in this module. */
    private void exampleDef() {
        start(SyntaxKind.EXAMPLE_DEF);
        bump();   // example
        expect(SyntaxKind.IDENT, Reading.AN_EXAMPLE);   // target name
        if (!at(SyntaxKind.PIPE)) {
            error(new ParseMessage.AnExampleNeedsAtLeastOneRow());
        }
        while (eat(SyntaxKind.PIPE)) {
            exampleRow();
        }
        finish();
    }

    /** {@code [ "desc" : ] ( args ) -> expected} — an argument list, then the expected result
     * (a bare type name asserts the arm; a construction/literal asserts the whole value), or
     * {@code <?>} where the answer has not been written yet.
     *
     * <p>{@code <?>} is read here and nowhere else. What it says is that this row's answer is owed,
     * which is a thing only a row has; admitted as an expression it would be writable in a body, an
     * input and a {@code with} value, and every one of those would then have to refuse it. */
    private void exampleRow() {
        start(SyntaxKind.EXAMPLE_ROW);
        if (at(SyntaxKind.STRING_LIT) && nth(1) == SyntaxKind.COLON) {
            // A row's name is what says which row it is from outside the file, so a name written
            // here is held to naming something. Said where the name is written, on the text of the
            // literal rather than on its spelling: `"\t"` is written with two characters and names
            // as little as `""` does.
            if (CstLexer.textOf(tokenText(mi(0))).isBlank()) {
                error(new ParseMessage.ARowNameSaysNothing());
            }
            bump();   // "name"
            bump();   // :
        }
        argList();   // the input tuple, reusing ARG_LIST
        if (at(SyntaxKind.WITH_KW)) {
            withClause();   // supplies fakes for what the target depends on (value dependencies)
        }
        expect(SyntaxKind.ARROW, Reading.AN_EXAMPLE);
        if (!eat(SyntaxKind.UNANSWERED)) {
            expr();      // expected
        }
        finish();
    }

    /** {@code with <dep> = <expr> (, <dep> = <expr>)*} — value fakes for what a behavior depends on. */
    private void withClause() {
        start(SyntaxKind.WITH_CLAUSE);
        bump();   // with
        withBinding();
        while (eat(SyntaxKind.COMMA)) {
            if (!at(SyntaxKind.IDENT)) {
                break;   // a trailing comma is consumed and ends the clause
            }
            withBinding();
        }
        finish();
    }

    private void withBinding() {
        start(SyntaxKind.WITH_BINDING);
        // The dependency, named as a behavior is named anywhere else: bare where the scope settles
        // it, qualified through its module where the author says which one is meant.
        expect(SyntaxKind.IDENT, Reading.AN_EXAMPLE);
        dottedTail();
        expect(SyntaxKind.ASSIGN, Reading.AN_EXAMPLE);
        boolean saved = noLambda;
        noLambda = true;
        expr();                     // its faked value, which the row's `->` ends
        noLambda = saved;
        finish();
    }

    /** {@code fake <injected> | rows} — a function fake: a table of input→output rows for an injected
     * dependency. {@code fake} is contextual; the target is the name after it, written as a behavior
     * is written anywhere else — bare, or qualified through the module that declares it. */
    private void fakeDef() {
        start(SyntaxKind.FAKE_DEF);
        bump();   // fake
        expect(SyntaxKind.IDENT, Reading.AN_EXAMPLE);   // target injected behavior
        dottedTail();
        if (!at(SyntaxKind.PIPE)) {
            error(new ParseMessage.AFakeNeedsAtLeastOneRow());
        }
        while (eat(SyntaxKind.PIPE)) {
            fakeRow();
        }
        finish();
    }

    /** {@code ( args ) -> output} or {@code _ -> output} (a default). */
    private void fakeRow() {
        start(SyntaxKind.FAKE_ROW);
        if (at(SyntaxKind.UNDERSCORE)) {
            bump();   // _ — the row every unlisted input falls to
        } else {
            argList();
        }
        expect(SyntaxKind.ARROW, Reading.AN_EXAMPLE);
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
        eat(SyntaxKind.QUESTION);   // `T?` in a signature — core only, rejected later for a user module
        finish();
    }

    /** Whether the parenthesised run at the cursor is a function type's parameter list: its closing
     * paren is followed by {@code ->}. A tuple type is written the same way without the arrow. */
    private boolean atFnTypeParams() {
        return parenRunFollowedByArrow();
    }

    /**
     * The narrow type production — a data field, a type argument, a tuple's member — where
     * {@link #retType} reads a whole type. What it reads is the same in every one of the three; what
     * differs is the continuation the position does not read, and neither continuation is a stray
     * token: a `{@code |}` is an author reaching for a choice and a `{@code ?}` for an absence. So
     * each is recognized where it is written and refused by name, ahead of the delimiter that would
     * otherwise be the whole report. Recognizing a form in order to refuse it is not reading it: what
     * the production admits is unchanged, and nothing downstream is handed a form it does not build.
     *
     * <p>The refused continuation is consumed so the declaration around it still parses, and the
     * author sees the one thing they wrote wrong rather than it and everything it displaced.
     *
     * @param optionalSuffix whether a `{@code ?}` after the type belongs to this position
     * @param ifUnion what to say about a `{@code |}`, which none of the three reads
     */
    private <M extends Message & Reported> void narrowType(boolean optionalSuffix, M ifUnion) {
        narrowMember(optionalSuffix);
        if (at(SyntaxKind.PIPE)) {
            error(ifUnion);
            while (eat(SyntaxKind.PIPE)) {
                narrowMember(optionalSuffix);
            }
        }
    }

    /**
     * One member of what a narrow position was given, read the way that position reads a type. The
     * members after a refused `{@code |}` are read this way too and not with a bare {@link #typeRef}:
     * a `{@code ?}` on one of them is forbidden there for the same reason it is forbidden on the
     * first, and read without that the `{@code ?}` would be left standing for the closing delimiter
     * to trip over — the report this whole reading exists to prevent, reappearing inside the recovery
     * meant to prevent it.
     */
    private void narrowMember(boolean optionalSuffix) {
        typeRef();
        if (optionalSuffix) {
            eat(SyntaxKind.QUESTION);
        } else if (at(SyntaxKind.QUESTION)) {
            error(new ParseMessage.AnOptionalIsNotWrittenInsideAnotherType());
            bump();   // ?
        }
    }

    private void typeRef() {
        if (at(SyntaxKind.LPAREN) && atFnTypeParams()) {
            // A function type and a tuple type both open with `(` and read alike up to the closing
            // paren, so the token after it decides.
            fnType();
            return;
        }
        if (at(SyntaxKind.LPAREN)) {
            start(SyntaxKind.TUPLE_TYPE);
            bump();   // (
            if (!at(SyntaxKind.RPAREN)) {
                narrowType(false, new ParseMessage.AnAnonymousUnionIsNotWrittenInsideAnotherType());
                while (eat(SyntaxKind.COMMA)) {
                    if (at(SyntaxKind.RPAREN)) {
                        break;
                    }
                    narrowType(false, new ParseMessage.AnAnonymousUnionIsNotWrittenInsideAnotherType());
                }
            }
            expect(SyntaxKind.RPAREN, Reading.A_DECLARATION);
            finish();
            return;
        }
        start(SyntaxKind.TYPE_REF);
        if (at(SyntaxKind.TYPEVAR)) {
            bump();
        } else {
            expect(SyntaxKind.IDENT, Reading.A_DECLARATION);
            // a type may be named through its module (`example.billing.Amount`) or an import alias
            // (`B.Amount`), so the head is a dotted name like a module's own
            dottedTail();
            if (at(SyntaxKind.LT)) {
                typeArgs();
            }
        }
        finish();
    }

    private void typeArgs() {
        start(SyntaxKind.TYPE_ARGS);
        expect(SyntaxKind.LT, Reading.A_DECLARATION);
        narrowType(false, new ParseMessage.AnAnonymousUnionIsNotWrittenInsideAnotherType());
        while (eat(SyntaxKind.COMMA)) {
            if (at(SyntaxKind.GT)) {
                break;
            }
            narrowType(false, new ParseMessage.AnAnonymousUnionIsNotWrittenInsideAnotherType());
        }
        expect(SyntaxKind.GT, Reading.A_DECLARATION);
        finish();
    }

    // --- behavior body / block ---

    /** A brace-delimited block: {@code let}/{@code guard} statements then a result expression. */
    private void blockExpr() {
        start(SyntaxKind.BLOCK_EXPR);
        expect(SyntaxKind.LBRACE, Reading.AN_EXPRESSION);
        blockStatements();
        expect(SyntaxKind.RBRACE, Reading.AN_EXPRESSION);
        finish();
    }

    /** The statement sequence a behavior body is: {@code let}/{@code guard} lines then one result. */
    private void blockStatements() {
        while (true) {
            if (at(SyntaxKind.LET_KW)) {
                if (atLetPattern()) {
                    letDestructure();
                } else {
                    letStmt();
                }
            } else if (at(SyntaxKind.GUARD_KW)) {
                guardStmt();
            } else {
                break;
            }
        }
        if (at(SyntaxKind.RBRACE)) {
            // The block closes with nothing to be its value. Often the result was not omitted but
            // absorbed: layout does not end a statement, so a line starting with `(`, `.` or an
            // operator continues the line above. At EOF the block is merely unterminated, which
            // blockExpr's expect(RBRACE) reports instead — one error, not two.
            error(new ParseMessage.ABlockEndsInOneExpression());
        } else if (!at(SyntaxKind.EOF)) {
            expr();   // the result expression
        }
    }

    private void letStmt() {
        start(SyntaxKind.LET_STMT);
        bump();   // let
        expect(SyntaxKind.IDENT, Reading.AN_EXPRESSION);
        if (eat(SyntaxKind.COLON)) {
            retType();
        }
        expect(SyntaxKind.ASSIGN, Reading.AN_EXPRESSION);
        expr();
        finish();
    }

    /** Whether the {@code let} at the cursor binds a pattern rather than a plain name. A local
     * helper definition does not exist inside a block — {@code let f (x) = …} is written at the top
     * level — so a name followed by {@code (} here opens a value, it does not take a parameter. */
    private boolean atLetPattern() {
        if (nth(1) == SyntaxKind.LPAREN || nth(1) == SyntaxKind.LBRACE) {
            return true;
        }
        if (nth(1) != SyntaxKind.IDENT) {
            return false;
        }
        return nth(pastDottedName(1)) == SyntaxKind.LPAREN;
    }

    private void letDestructure() {
        start(SyntaxKind.LET_DESTRUCTURE);
        bump();   // let
        pattern();
        expect(SyntaxKind.ASSIGN, Reading.A_PATTERN);
        expr();
        finish();
    }

    /**
     * A binding pattern: what a {@code let} statement, a helper parameter or a lambda parameter
     * binds. Only irrefutable shapes are written — a name, a tuple, a newtype opened by its
     * constructor, a record's fields — because a binding has no other arm to fall to. Whether a
     * written name is a newtype or a sum's case is not something the parser knows, so the shape is
     * read here and judged once the name resolves.
     */
    private void pattern() {
        if (at(SyntaxKind.LPAREN)) {
            start(SyntaxKind.PATTERN_TUPLE);
            bump();   // (
            if (!at(SyntaxKind.RPAREN)) {
                pattern();
                while (eat(SyntaxKind.COMMA)) {
                    if (at(SyntaxKind.RPAREN)) {
                        break;   // trailing comma
                    }
                    pattern();
                }
            }
            expect(SyntaxKind.RPAREN, Reading.A_PATTERN);
            finish();
            return;
        }
        if (at(SyntaxKind.LBRACE)) {
            patternRecord();
            return;
        }
        if (atCtorPattern()) {
            start(SyntaxKind.PATTERN_CTOR);
            expect(SyntaxKind.IDENT, Reading.A_PATTERN);
            dottedTail();   // a newtype may be named through its module, as in a match arm
            expect(SyntaxKind.LPAREN, Reading.A_PATTERN);
            pattern();
            expect(SyntaxKind.RPAREN, Reading.A_PATTERN);
            finish();
            return;
        }
        start(SyntaxKind.PATTERN_NAME);
        nameOrDiscard(Reading.A_PATTERN);
        finish();
    }

    /**
     * A position that takes a name or the discard.
     *
     * <p>{@code _} is a token of its own rather than a name (a name may not begin with one), and
     * these are the positions the language writes it in: a binding pattern, a departure arm for the
     * clauses that carry no name, a fake table's default row, and an invariant clause's name, where
     * a clause called {@code _} is answered by the rule it breaks. A match arm is not one of them —
     * every arm names a case, and there is no wildcard arm. What it means where it stands is
     * unchanged — it is read on as the name it was read as before it had a token, so this settles
     * how it is written and nothing about what it binds.
     */
    private void nameOrDiscard(Reading reading) {
        if (at(SyntaxKind.UNDERSCORE)) {
            bump();
            return;
        }
        expect(SyntaxKind.IDENT, reading);
    }

    /** A possibly-dotted name followed by {@code (}: the constructor form. */
    private boolean atCtorPattern() {
        if (!at(SyntaxKind.IDENT)) {
            return false;
        }
        return nth(pastDottedName(0)) == SyntaxKind.LPAREN;
    }

    private void patternRecord() {
        start(SyntaxKind.PATTERN_RECORD);
        expect(SyntaxKind.LBRACE, Reading.A_PATTERN);
        if (!at(SyntaxKind.RBRACE)) {
            patternField();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RBRACE)) {
                    break;   // trailing comma
                }
                patternField();
            }
        }
        expect(SyntaxKind.RBRACE, Reading.A_PATTERN);
        finish();
    }

    /** {@code f} binds the field under its own name; {@code f = x} renames it. */
    private void patternField() {
        start(SyntaxKind.PATTERN_FIELD);
        expect(SyntaxKind.IDENT, Reading.A_PATTERN);
        if (eat(SyntaxKind.ASSIGN)) {
            expect(SyntaxKind.IDENT, Reading.A_PATTERN);
        }
        finish();
    }

    private void guardStmt() {
        start(SyntaxKind.GUARD_STMT);
        bump();   // guard
        expr();
        attemptBinder();
        expect(SyntaxKind.ELSE_KW, Reading.AN_EXPRESSION);
        elseBody();
        finish();
    }

    /**
     * What an {@code else} takes: one expression, or the arms an attempted construction departs by
     * per invariant clause ({@code | nonEmpty -> NoLines | uniqueProducts -> DuplicateProduct}). No
     * expression begins with {@code |}, so the two forms are told apart by the first token. That the
     * arms name clauses of the attempted type, and cover them, is decided where the type is known.
     */
    private void elseBody() {
        if (at(SyntaxKind.PIPE)) {
            elseArms();
        } else {
            expr();
        }
    }

    private void elseArms() {
        start(SyntaxKind.ELSE_ARMS);
        while (at(SyntaxKind.PIPE)) {
            elseArm();
        }
        finish();
    }

    /** {@code | <clause> -> e}, or {@code | _ -> e} for the clauses that carry no name. */
    private void elseArm() {
        start(SyntaxKind.ELSE_ARM);
        bump();   // |
        nameOrDiscard(Reading.AN_EXPRESSION);
        expect(SyntaxKind.ARROW, Reading.AN_EXPRESSION);
        expr();
        finish();
    }

    // --- expressions (precedence ladder; left-associative via wrap) ---

    private boolean noConstruct = false;
    /** Set where an expression is followed by a `->` that belongs to the enclosing form rather than
     * to the expression: an example row's `with` value. A parenthesised value there has exactly the
     * shape of a lambda's parameter list, and the enclosing form has the prior claim. */
    private boolean noLambda = false;

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
        postfixExpr();
    }

    /**
     * A primary and everything written after it: a field taken off it, or an argument list applied
     * to it. Both are left-recursive, so each wraps what came before.
     *
     * <p>Application is here rather than at an identifier, so what is applied is any expression —
     * {@code choose(flag)(x)}, {@code (if c then f else g)(x)}. An argument list must begin on the
     * line its callee ends on: a `(` that opens a line is a parenthesised expression, so a block
     * whose result is a tuple written under a call reads as that result.
     */
    private void postfixExpr() {
        primaryExpr();
        while (true) {
            if (at(SyntaxKind.DOT) && nth(1) == SyntaxKind.IDENT) {
                int m = markForFieldAccess();
                wrap(m, SyntaxKind.FIELD_ACCESS);
                bump();   // .
                bump();   // field
                finish();
            } else if (at(SyntaxKind.LPAREN) && !lineBreakBeforeNextToken()) {
                int m = markForFieldAccess();
                wrap(m, SyntaxKind.APPLY_EXPR);
                argList();
                finish();
            } else {
                return;
            }
        }
    }

    private static boolean isCmpOp(SyntaxKind k) {
        return k == SyntaxKind.EQ || k == SyntaxKind.NE || k == SyntaxKind.LT
                || k == SyntaxKind.LE || k == SyntaxKind.GT || k == SyntaxKind.GE;
    }

    private void primaryExpr() {
        SyntaxKind k = current();
        // a lambda: `x -> e`
        if (k == SyntaxKind.IDENT && nth(1) == SyntaxKind.ARROW && !noLambda) {
            start(SyntaxKind.LAMBDA_EXPR);
            start(SyntaxKind.PATTERN_NAME);
            bump();   // param
            finish();
            bump();   // ->
            expr();
            finish();
            return;
        }
        // a parenthesised lambda: `(a, b) -> e`
        if (k == SyntaxKind.LPAREN && !noLambda && isBlockParams()) {
            parenLambda();
            return;
        }
        // a bare field getter `.field` (Elm-style) = the getter (x) -> x.field. A leading `.` is
        // otherwise always an error, and `.5` lexes as a decimal, so `DOT IDENT` is unambiguous.
        if (k == SyntaxKind.DOT && nth(1) == SyntaxKind.IDENT) {
            start(SyntaxKind.FIELD_GETTER);
            bump();   // .
            bump();   // field
            finish();
            return;
        }
        switch (k) {
            case MATCH_KW -> matchExpr();
            case IF_KW -> ifExpr();
            case UNREACHABLE_KW -> unreachableExpr();
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
                error(new ParseMessage.AnExpressionWasExpected());
                start(SyntaxKind.ERROR_TOKEN);
                finish();   // zero-width error node; the caller resynchronises
            }
        }
    }

    /** {@code unreachable "reason"} — the reason is a string literal rather than an expression, so
     * it is readable without running the model. */
    private void unreachableExpr() {
        start(SyntaxKind.UNREACHABLE_EXPR);
        bump();   // unreachable
        if (at(SyntaxKind.STRING_LIT)) {
            start(SyntaxKind.LITERAL_EXPR);
            bump();
            finish();
        } else {
            error(new ParseMessage.UnreachableStatesItsReasonAsAString());
        }
        finish();
    }

    /** {@code (p, ...) -> body} — the caller has confirmed the shape via {@link #isBlockParams}.
     * Each parameter is a pattern, so a lambda opens what it receives where it names it. */
    private void parenLambda() {
        start(SyntaxKind.LAMBDA_EXPR);
        expect(SyntaxKind.LPAREN, Reading.AN_EXPRESSION);
        if (!at(SyntaxKind.RPAREN)) {
            pattern();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                pattern();
            }
        }
        expect(SyntaxKind.RPAREN, Reading.AN_EXPRESSION);
        expect(SyntaxKind.ARROW, Reading.AN_EXPRESSION);
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
            expect(SyntaxKind.RPAREN, Reading.AN_EXPRESSION);
            retagTop(SyntaxKind.TUPLE_EXPR);
            finish();
            return;
        }
        expect(SyntaxKind.RPAREN, Reading.AN_EXPRESSION);
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
            expect(SyntaxKind.RBRACKET, Reading.AN_EXPRESSION);
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
        expect(SyntaxKind.RBRACKET, Reading.AN_EXPRESSION);
        finish();
    }

    private void ifExpr() {
        start(SyntaxKind.IF_EXPR);
        bump();   // if
        expr();
        attemptBinder();
        expect(SyntaxKind.THEN_KW, Reading.AN_EXPRESSION);
        expr();
        expect(SyntaxKind.ELSE_KW, Reading.AN_EXPRESSION);
        elseBody();
        finish();
    }

    /**
     * The {@code as x} of an attempted construction — {@code if T(v) as x then … else …} and the
     * {@code guard T(v) as x else …} that desugars to it. Only the binder is read here; that what
     * precedes it is a construction, and that the type has an invariant to attempt, are decided
     * where the expression is known (the AST is built from a name, not a type).
     */
    private void attemptBinder() {
        if (eat(SyntaxKind.AS_KW)) {
            expect(SyntaxKind.IDENT, Reading.AN_EXPRESSION);
        }
    }

    /**
     * {@code match e with | A -> … | B -> …}. An arm belongs to the innermost match whose arms it is
     * indented past: a {@code |} at or left of the enclosing match's arm column closes this match and
     * is left for that one. Without the column rule a match inside an arm body swallows the arms that
     * follow it — the enclosing match's own cases — and the error surfaces far from the layout that
     * caused it (`B is not a case of <the inner sum>`).
     */
    private void matchExpr() {
        start(SyntaxKind.MATCH_EXPR);
        bump();   // match
        boolean saved = noConstruct;
        noConstruct = true;
        expr();   // scrutinee
        noConstruct = saved;
        expect(SyntaxKind.WITH_KW, Reading.AN_EXPRESSION);
        int enclosing = matchArmColumns.isEmpty() ? -1 : matchArmColumns.peek();
        // this match's arm column: the leading `|` when written, else the first case's own column
        int armColumn = columnOf(mi(0));
        eat(SyntaxKind.PIPE);   // optional leading `|`
        matchArmColumns.push(armColumn);
        matchCase();
        while (at(SyntaxKind.PIPE) && columnOf(mi(0)) > enclosing) {
            bump();   // |
            matchCase();
        }
        matchArmColumns.pop();
        finish();
    }

    /** The 0-based column of the token at {@code index}, walking back to the last newline in the
     * trivia. A match arm's column is what decides which match it belongs to. */
    private int columnOf(int index) {
        int column = 0;
        for (int i = index - 1; i >= 0; i--) {
            String text = tokens.get(i).text();
            int newline = text.lastIndexOf('\n');
            if (newline >= 0) {
                return column + (text.length() - newline - 1);
            }
            column += text.length();
        }
        return column;
    }

    /** {@code A [| B ...] [binding] [{ fields }] [as x] -> body} — kept structural for lowering. */
    private void matchCase() {
        start(SyntaxKind.MATCH_CASE);
        // A name, and not the discard: every arm names a case and there is no wildcard arm, so `_`
        // is refused here where it is read in a fake's rows and an attempt's departures.
        expect(SyntaxKind.IDENT, Reading.A_PATTERN);
        dottedTail();
        while (at(SyntaxKind.PIPE) && nth(1) == SyntaxKind.IDENT) {
            // an or-pattern alternative; a `|` that begins the next case is followed by the arrow
            // path instead. Only consume `|` here when another case name follows.
            bump();   // |
            bump();   // ident
            dottedTail();
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
                expect(SyntaxKind.IDENT, Reading.A_PATTERN);
                if (eat(SyntaxKind.ASSIGN)) {
                    expect(SyntaxKind.IDENT, Reading.A_PATTERN);
                }
                while (eat(SyntaxKind.COMMA)) {
                    if (at(SyntaxKind.RBRACE)) {
                        break;
                    }
                    expect(SyntaxKind.IDENT, Reading.A_PATTERN);
                    if (eat(SyntaxKind.ASSIGN)) {
                        expect(SyntaxKind.IDENT, Reading.A_PATTERN);
                    }
                }
            }
            expect(SyntaxKind.RBRACE, Reading.A_PATTERN);
        }
        // whole-value binding `as x`
        if (eat(SyntaxKind.AS_KW)) {
            expect(SyntaxKind.IDENT, Reading.A_PATTERN);
        }
        expect(SyntaxKind.ARROW, Reading.A_PATTERN);
        expr();
        finish();
    }

    /**
     * The rest of a dotted name, after its first identifier has been read. Every position that names
     * something declared elsewhere — a module, a pipeline stage, a type, a match arm's case — may
     * write it through the declaring module or an import alias, so all of them read the tail here
     * rather than each spelling out the loop (issue #177). A binding after the name is a bare
     * identifier, so no dot follows it and the two do not run together.
     */
    private void dottedTail() {
        while (at(SyntaxKind.DOT) && nth(1) == SyntaxKind.IDENT) {
            bump();   // .
            bump();   // ident
        }
    }

    /**
     * The offset just past a dotted name starting at {@code start}, which must be an identifier —
     * what a lookahead needs to see the token that follows the name. The counterpart of
     * {@link #dottedTail} for a decision made before anything is consumed.
     */
    private int pastDottedName(int start) {
        int n = start + 1;
        while (nth(n) == SyntaxKind.DOT && nth(n + 1) == SyntaxKind.IDENT) {
            n += 2;
        }
        return n;
    }

    /** {@code ( IDENT [casePattern] )} — a newtype-destructuring sub-pattern, nestable for a
     * newtype over a newtype. Kept structural (every token bumped) so the tree stays lossless. */
    private void casePattern() {
        expect(SyntaxKind.LPAREN, Reading.A_PATTERN);
        expect(SyntaxKind.IDENT, Reading.A_PATTERN);
        dottedTail();   // the layer a pattern opens is named like any other type (issue #177)
        if (at(SyntaxKind.LBRACE)) {
            // `Some(Booking { member })`: the parens open a *newtype*, so a record named in them has
            // nothing to open. Its fields are destructured directly, as on a user case.
            error(new ParseMessage.ARecordsFieldsAreDestructuredDirectly());
            while (!at(SyntaxKind.RBRACE) && !at(SyntaxKind.EOF)) {
                bump();
            }
            eat(SyntaxKind.RBRACE);
        } else if (at(SyntaxKind.LPAREN)) {
            casePattern();
        }
        expect(SyntaxKind.RPAREN, Reading.A_PATTERN);
    }

    /**
     * An identifier-led primary: a construction or a bare variable, each of which a field-access
     * chain or an argument list may follow.
     *
     * <p>A name that is applied is not read here. {@code name(args)} is the variable and the
     * argument list {@link #postfixExpr} writes after it, and {@code Mod.name(args)} is that
     * argument list after a field read — so what is applied is a subexpression whichever way it is
     * written, and whether {@code Mod.name} is a namespace member or an ordinary field read is
     * left to resolution, which knows the bindings in force.
     */
    private void identExpr() {
        if (!noConstruct && nth(1) == SyntaxKind.LBRACE) {
            // construction `Type { ... }` (unless suppressed, as in a match scrutinee)
            newDataExpr();
        } else {
            start(SyntaxKind.VAR_EXPR);
            bump();   // ident
            finish();
        }
    }

    private void newDataExpr() {
        start(SyntaxKind.NEW_DATA_EXPR);
        bump();   // Type
        expect(SyntaxKind.LBRACE, Reading.AN_EXPRESSION);
        if (!at(SyntaxKind.RBRACE)) {
            initElem();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RBRACE)) {
                    break;
                }
                initElem();
            }
        }
        expect(SyntaxKind.RBRACE, Reading.AN_EXPRESSION);
        finish();
    }

    private void initElem() {
        if (at(SyntaxKind.SPREAD)) {
            start(SyntaxKind.SPREAD_MEMBER);
            bump();   // ...
            expect(SyntaxKind.IDENT, Reading.AN_EXPRESSION);
            // a spread may name a field path (`...c.address`), not only a local
            while (at(SyntaxKind.DOT) && nth(1) == SyntaxKind.IDENT) {
                bump();   // .
                bump();   // field
            }
            finish();
        } else {
            start(SyntaxKind.FIELD_INIT);
            expect(SyntaxKind.IDENT, Reading.AN_EXPRESSION);
            if (eat(SyntaxKind.ASSIGN)) {
                expr();
            }
            finish();
        }
    }

    private void argList() {
        start(SyntaxKind.ARG_LIST);
        expect(SyntaxKind.LPAREN, Reading.AN_EXPRESSION);
        if (!at(SyntaxKind.RPAREN)) {
            arg();
            while (eat(SyntaxKind.COMMA)) {
                if (at(SyntaxKind.RPAREN)) {
                    break;
                }
                arg();
            }
        }
        expect(SyntaxKind.RPAREN, Reading.AN_EXPRESSION);
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
     *
     * <p>The one way the tree gains depth that {@link #start} does not see: {@code 1+1+1+…} is read
     * by a loop, so the frames never nest while the tree does. The level this adds sits on top of
     * the ones already open, so that is what is counted, and the same refusal is made here.
     */
    private void wrap(int mark, SyntaxKind kind) {
        List<Green> top = stack.peek().children;
        List<Green> tail = top.subList(mark, top.size());
        int deepest = 0;
        for (Green c : tail) {
            deepest = Math.max(deepest, c.depth());
        }
        if (stack.size() + deepest >= MAX_DEPTH) {
            refuseAsTooDeep();
        }
        Frame f = new Frame(kind);
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

    /** Opens a node — and the boundary every production that nests has to pass to do it, which is
     *  what lets one refusal here end a descent none of them checks for itself. */
    private void start(SyntaxKind kind) {
        // Two levels, not one: the frame about to open is a level, and whatever it ends up holding
        // is a level under that — a node with nothing but a token in it is already two deep. What
        // is counted here is the tree the frame would leave behind, which is what MAX_DEPTH bounds.
        if (stack.size() + 2 > MAX_DEPTH) {
            refuseAsTooDeep();
        }
        stack.push(new Frame(kind));
    }

    private void finish() {
        Frame f = stack.pop();
        stack.peek().children.add(Green.node(f.kind, f.children));
    }

    /** Says where the source outgrew {@link #MAX_DEPTH} — at the token that reached it, which is the
     *  position no walk finding the same limit in its own stack could have claimed — and ends the
     *  descent. */
    private void refuseAsTooDeep() {
        // Not "an expression": the bound is the tree's, and a type nested through its arguments or a
        // pattern through a tuple reaches it the same way. Naming a part with `let` is the answer to
        // one of those and not to the others, so what is asked for is the thing they share.
        error(new DeclarationMessage.ItNestsDeeperThanIsRead());
        throw new TooDeep();
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
        return contextualAt(0, text);
    }

    /** The same question about the {@code n}th meaningful token ahead, for a modifier that may be
     * followed by another one. */
    private boolean contextualAt(int n, String text) {
        return nth(n) == SyntaxKind.IDENT && tokenText(mi(n)).equals(text);
    }

    private boolean eat(SyntaxKind kind) {
        if (at(kind)) {
            bump();
            return true;
        }
        return false;
    }

    /**
     * Reads {@code kind}, or says it was wanted here.
     *
     * <p>The rule is given by the caller rather than fixed here, because which part of the language
     * did not read is a property of what is being read and not of the token that was missing: the
     * same missing {@code ->} is example syntax in a row and expression syntax in a lambda, and a
     * reader looking one of them up is not asking about the other.
     */
    private void expect(SyntaxKind kind, Reading reading) {
        if (at(kind)) {
            bump();
            return;
        }
        Object wanted = kind.display();
        Object found = current().display();
        error(switch (reading) {
            case A_DECLARATION -> new ParseMessage.ADeclarationExpectedSomethingElse(wanted, found);
            case AN_EXPRESSION -> new ParseMessage.AnExpressionExpectedSomethingElse(wanted, found);
            case A_PATTERN -> new ParseMessage.APatternExpectedSomethingElse(wanted, found);
            case AN_EXAMPLE -> new ParseMessage.AnExampleExpectedSomethingElse(wanted, found);
        });
    }

    /**
     * Which part of the language is being read where a token is wanted.
     *
     * <p>Named rather than given as a code, so that what a site chooses is what it is reading and
     * the rule the reader looks up follows from it. The same missing {@code ->} is example syntax in
     * a row and expression syntax in a lambda, and a reader looking one of them up is not asking
     * about the other.
     */
    private enum Reading { A_DECLARATION, AN_EXPRESSION, A_PATTERN, AN_EXAMPLE }

    /** The kind of the next meaningful token. */
    private SyntaxKind current() {
        return tokens.get(mi(0)).kind();
    }

    /** The kind of the nth meaningful token ahead (0 = current), stopping at EOF. */
    private SyntaxKind nth(int n) {
        return tokens.get(mi(n)).kind();
    }

    /**
     * Whether a line break stands between the cursor and the next meaningful token.
     *
     * <p>An argument list is read as applying to what precedes it only when nothing separates them.
     * Everywhere else the grammar ignores line breaks, and a leading `.` or operator continues the
     * line above — but an argument list cannot: a block whose statement ends in a list or a tuple is
     * followed by a result expression that often opens with `(`, and reading that as an application
     * takes the block's result away. The standard library is written that way, so this is not a
     * style one could ask authors to avoid.
     */
    private boolean lineBreakBeforeNextToken() {
        for (int i = pos; i < tokens.size(); i++) {
            if (!tokens.get(i).kind().isTrivia()) {
                return false;
            }
            if (tokens.get(i).text().indexOf('\n') >= 0) {
                return true;
            }
        }
        return false;
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

    /**
     * Distinguishes a lambda's parameter list from a parenthesised expression: the run is a
     * parameter list when its closing {@code )} is followed by {@code ->}. A parameter is a pattern
     * rather than only a name, so what is between the parens is not inspected here — {@link
     * #pattern} decides what it may be. {@code ()} is not a parameter list; a lambda takes at least
     * one parameter.
     */
    private boolean isBlockParams() {
        return nth(1) != SyntaxKind.RPAREN && parenRunFollowedByArrow();
    }

    /**
     * Whether the parenthesised run at the cursor closes on a {@code )} that {@code ->} follows.
     *
     * <p>Walks the tokens from the cursor with one moving index rather than asking for the nth
     * meaningful token each step: {@link #mi} counts from {@code pos} every time, which would make a
     * run of length L cost L steps per token and the nest of runs in {@code ((((1))))} cost the cube
     * of its depth. The lookahead runs at every level of the nest, so that is what it costs.
     */
    private boolean parenRunFollowedByArrow() {
        int depth = 0;
        for (int i = mi(0); i < tokens.size(); i++) {
            SyntaxKind k = tokens.get(i).kind();
            if (k.isTrivia()) {
                continue;
            }
            if (k == SyntaxKind.EOF) {
                return false;
            }
            if (k == SyntaxKind.LPAREN) {
                depth++;
            } else if (k == SyntaxKind.RPAREN && --depth == 0) {
                return kindAfter(i) == SyntaxKind.ARROW;
            }
        }
        return false;
    }

    /** The kind of the first meaningful token after the one at {@code index}. */
    private SyntaxKind kindAfter(int index) {
        for (int i = index + 1; i < tokens.size(); i++) {
            SyntaxKind k = tokens.get(i).kind();
            if (!k.isTrivia()) {
                return k;
            }
        }
        return SyntaxKind.EOF;
    }

    private <M extends Message & Reported> void error(M said) {
        int i = mi(0);
        int width = Math.max(1, tokens.get(i).width());
        errors.add(CstError.of(offset[i], width, said));
    }
}
