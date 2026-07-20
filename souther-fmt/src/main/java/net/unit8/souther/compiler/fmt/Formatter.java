package net.unit8.souther.compiler.fmt;

import net.unit8.souther.compiler.cst.CstParser;
import net.unit8.souther.compiler.cst.SyntaxElement;
import net.unit8.souther.compiler.cst.SyntaxKind;
import net.unit8.souther.compiler.cst.SyntaxNode;
import net.unit8.souther.compiler.cst.SyntaxToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.unit8.souther.compiler.fmt.Doc.HARDLINE;
import static net.unit8.souther.compiler.fmt.Doc.LINE;
import static net.unit8.souther.compiler.fmt.Doc.SOFTLINE;
import static net.unit8.souther.compiler.fmt.Doc.concat;
import static net.unit8.souther.compiler.fmt.Doc.group;
import static net.unit8.souther.compiler.fmt.Doc.nest;
import static net.unit8.souther.compiler.fmt.Doc.text;

/**
 * A single-canonical-form (gofmt-style) formatter over the concrete syntax tree. It re-derives the
 * layout from the tree's structure and a fixed style — a product type as a leading-comma block, a
 * record literal as a trailing-comma {@code Type { ... }}, a {@code |>} pipeline one stage per line —
 * choosing inline or broken by whether the construct fits {@link #WIDTH} columns. Full-line comments
 * are kept above the construct they precede; blank lines between top-level items collapse to one.
 */
public final class Formatter {

    private static final int INDENT = 4;
    private static final int WIDTH = 100;

    private Formatter() {
    }

    /** Formats source text into its canonical form. Assumes the source parses without syntax errors;
     * a caller that cannot assume that should check {@link CstParser#parse} first. */
    public static String format(String source) {
        SyntaxNode file = CstParser.parse(source).root();
        return new Formatter().file(file).render(WIDTH);
    }

    // --- top level ---

    private Doc file(SyntaxNode file) {
        List<Doc> parts = new ArrayList<>();
        SyntaxKind prev = null;
        for (SyntaxNode item : file.childNodes()) {
            if (!isTopLevel(item.kind())) {
                continue;
            }
            Leading lead = leading(item);
            if (prev != null) {
                parts.add(HARDLINE);
                if (blankBetween(prev, item.kind())) {
                    parts.add(HARDLINE);
                }
            }
            for (String c : lead.comments) {
                parts.add(text(c));
                parts.add(HARDLINE);
            }
            parts.add(item(item));
            prev = item.kind();
        }
        for (String c : trailingComments(file)) {
            parts.add(HARDLINE);
            parts.add(text(c));
        }
        parts.add(HARDLINE);   // files end with a single newline
        return concat(parts);
    }

    /** One blank line separates every top-level item, except the module header and its imports,
     * which stay tight together (as gofmt keeps a package clause and its import block). */
    private static boolean blankBetween(SyntaxKind prev, SyntaxKind current) {
        boolean header = (prev == SyntaxKind.MODULE_HEADER || prev == SyntaxKind.IMPORT_DECL)
                && current == SyntaxKind.IMPORT_DECL;
        return !header;
    }

    private static boolean isTopLevel(SyntaxKind k) {
        return k == SyntaxKind.MODULE_HEADER || k == SyntaxKind.IMPORT_DECL
                || k == SyntaxKind.DATA_DEF || k == SyntaxKind.BEHAVIOR_DEF || k == SyntaxKind.FN_DEF
                || k == SyntaxKind.EXAMPLE_DEF || k == SyntaxKind.EXAMPLES_FILE_HEADER;
    }

    private Doc item(SyntaxNode n) {
        return switch (n.kind()) {
            case MODULE_HEADER -> moduleHeader(n);
            case IMPORT_DECL -> importDecl(n);
            case DATA_DEF -> dataDef(n);
            case BEHAVIOR_DEF -> behaviorDef(n);
            case FN_DEF -> fnDef(n);
            case EXAMPLES_FILE_HEADER -> examplesFileHeader(n);
            case EXAMPLE_DEF -> exampleDef(n);
            default -> text(n.text().strip());
        };
    }

    // --- example ---

    private Doc examplesFileHeader(SyntaxNode n) {
        return concat(text("examples for "), qualifiedName(n.child(SyntaxKind.QUALIFIED_NAME).orElseThrow()));
    }

    private Doc exampleDef(SyntaxNode n) {
        List<SyntaxToken> ids = idents(n);   // ["example", target]
        String target = ids.size() >= 2 ? ids.get(1).text() : "";
        List<Doc> rows = new ArrayList<>();
        for (SyntaxNode row : childNodes(n, SyntaxKind.EXAMPLE_ROW)) {
            rows.add(concat(HARDLINE, exampleRow(row)));
        }
        return concat(text("example "), text(target), nest(INDENT, concat(rows)));
    }

    private Doc exampleRow(SyntaxNode n) {
        List<Doc> parts = new ArrayList<>();
        parts.add(text("| "));
        n.token(SyntaxKind.STRING_LIT).ifPresent(desc -> {
            parts.add(text(desc.text()));
            parts.add(text(" : "));
        });
        List<Doc> args = new ArrayList<>();
        for (SyntaxNode a : n.child(SyntaxKind.ARG_LIST).map(this::exprChildren).orElse(List.of())) {
            args.add(expr(a));
        }
        parts.add(concat(text("("), Doc.join(text(", "), args), text(")")));
        parts.add(text(" -> "));
        List<SyntaxNode> expected = exprChildren(n);   // the row's expr child that is not the ARG_LIST
        if (!expected.isEmpty()) {
            parts.add(expr(expected.get(0)));
        }
        return concat(parts);
    }

    private Doc moduleHeader(SyntaxNode n) {
        Doc d = concat(text("module "), qualifiedName(n.child(SyntaxKind.QUALIFIED_NAME).orElseThrow()));
        return n.child(SyntaxKind.EXPOSING_CLAUSE)
                .map(c -> concat(d, text(" "), exposing(c)))
                .orElse(d);
    }

    private Doc exposing(SyntaxNode clause) {
        List<Doc> entries = new ArrayList<>();
        for (SyntaxNode e : childNodes(clause, SyntaxKind.EXPOSED_ENTRY)) {
            Doc name = qualifiedName(e.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
            entries.add(e.child(SyntaxKind.RET_TYPE)
                    .map(rt -> concat(name, text(" : "), retType(rt)))
                    .orElse(name));
        }
        return concat(text("exposing ( "), Doc.join(text(", "), entries), text(" )"));
    }

    private Doc importDecl(SyntaxNode n) {
        Doc module = qualifiedName(n.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
        List<Doc> names = new ArrayList<>();
        n.child(SyntaxKind.NAME_LIST).ifPresent(list -> {
            for (SyntaxToken t : idents(list)) {
                names.add(text(t.text()));
            }
        });
        return concat(text("import "), module, text(" ( "), Doc.join(text(", "), names), text(" )"));
    }

    // --- data ---

    private Doc dataDef(SyntaxNode n) {
        String name = firstIdent(n);
        List<Doc> invariants = new ArrayList<>();
        for (SyntaxNode inv : childNodes(n, SyntaxKind.INVARIANT_CLAUSE)) {
            invariants.add(concat(HARDLINE, text("invariant "), expr(onlyExpr(inv))));
        }

        var product = n.child(SyntaxKind.PRODUCT_BODY);
        if (product.isPresent()) {
            return concat(text("data "), text(name), text(" ="),
                    nest(INDENT, concat(concat(HARDLINE, productBody(product.get())), concat(invariants))));
        }
        var sum = n.child(SyntaxKind.SUM_BODY);
        if (sum.isPresent()) {
            List<Doc> cases = new ArrayList<>();
            for (SyntaxToken t : idents(sum.get())) {
                cases.add(text(t.text()));
            }
            return concat(text("data "), text(name), text(" = "), Doc.join(text(" | "), cases));
        }
        var newtype = n.child(SyntaxKind.NEWTYPE_BODY);
        if (newtype.isPresent()) {
            String inner = idents(newtype.get()).get(0).text();
            return concat(text("data "), text(name), text(" = "), text(inner), nest(INDENT, concat(invariants)));
        }
        return concat(text("data "), text(name));   // unit
    }

    /** The leading-comma product block: {@code { f1: T1\n, f2: T2\n}}. Always multi-line. */
    private Doc productBody(SyntaxNode body) {
        List<Doc> members = new ArrayList<>();
        for (SyntaxNode m : body.childNodes()) {
            if (m.kind() == SyntaxKind.FIELD) {
                members.add(field(m));
            } else if (m.kind() == SyntaxKind.SPREAD_MEMBER) {
                members.add(concat(text("..."), text(firstIdent(m))));
            }
        }
        List<Doc> lines = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            lines.add(concat(i == 0 ? text("{ ") : concat(HARDLINE, text(", ")), members.get(i)));
        }
        lines.add(concat(HARDLINE, text("}")));
        return concat(lines);
    }

    private Doc field(SyntaxNode n) {
        Doc d = concat(text(firstIdent(n)), text(": "), typeRef(typeChild(n)));
        return n.token(SyntaxKind.QUESTION).isPresent() ? concat(d, text("?")) : d;
    }

    // --- behavior ---

    private Doc behaviorDef(SyntaxNode n) {
        String name = firstIdent(n);
        var sig = n.child(SyntaxKind.BEHAVIOR_SIG);
        if (sig.isPresent()) {
            SyntaxNode s = sig.get();
            Doc params = paramList(s.child(SyntaxKind.PARAM_LIST).orElseThrow());
            Doc ret = retType(s.child(SyntaxKind.RET_TYPE).orElseThrow());
            List<Doc> clauses = new ArrayList<>();
            for (SyntaxNode c : s.childNodes()) {
                if (c.kind() == SyntaxKind.CONSTRUCTS_CLAUSE) {
                    clauses.add(concat(HARDLINE, text("constructs "), nameList(c)));
                } else if (c.kind() == SyntaxKind.REQUIRES_CLAUSE) {
                    clauses.add(concat(HARDLINE, text("requires "), nameList(c)));
                }
            }
            return concat(text("behavior "), text(name), text(" : "), params, text(" -> "), ret,
                    nest(INDENT, concat(clauses)));
        }
        SyntaxNode pipe = n.child(SyntaxKind.PIPE_BEHAVIOR).orElseThrow();
        List<SyntaxNode> stages = childNodes(pipe, SyntaxKind.STAGE);
        List<Doc> tail = new ArrayList<>();
        for (int i = 1; i < stages.size(); i++) {
            tail.add(concat(LINE, text(">-> "), stage(stages.get(i))));
        }
        Doc body = group(nest(INDENT, concat(LINE, stage(stages.get(0)), concat(tail))));
        Doc declaredOut = pipe.child(SyntaxKind.RET_TYPE)
                .map(rt -> concat(text(" -> "), retType(rt))).orElse(Doc.NIL);
        return concat(text("behavior "), text(name), text(" ="), body, declaredOut);
    }

    private Doc paramList(SyntaxNode n) {
        List<Doc> params = new ArrayList<>();
        for (SyntaxNode p : childNodes(n, SyntaxKind.PARAM)) {
            params.add(concat(text(firstIdent(p)), text(": "), retType(p.child(SyntaxKind.RET_TYPE).orElseThrow())));
        }
        return concat(text("("), Doc.join(text(", "), params), text(")"));
    }

    private Doc stage(SyntaxNode n) {
        StringBuilder sb = new StringBuilder();
        for (SyntaxToken t : idents(n)) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(t.text());
        }
        return text(sb.toString());
    }

    private Doc nameList(SyntaxNode clause) {
        List<Doc> names = new ArrayList<>();
        for (SyntaxToken t : idents(clause)) {
            names.add(text(t.text()));
        }
        return Doc.join(text(", "), names);
    }

    // --- fn ---

    private Doc fnDef(SyntaxNode n) {
        String name = firstIdent(n);
        Doc params = fnParamList(n.child(SyntaxKind.FN_PARAM_LIST).orElseThrow());
        Doc ret = n.child(SyntaxKind.RET_TYPE).map(rt -> concat(text(": "), retType(rt))).orElse(Doc.NIL);
        Doc head = concat(text("let "), text(name), text(" "), params, ret);

        var intrinsic = n.child(SyntaxKind.INTRINSIC_BODY);
        if (intrinsic.isPresent()) {
            String raw = intrinsic.get().token(SyntaxKind.STRING_LIT).orElseThrow().text();
            return concat(head, text(" = intrinsic "), text(raw));
        }
        var block = n.child(SyntaxKind.BLOCK_EXPR);
        if (block.isPresent()) {
            return concat(head, text(" = "), block(block.get()));
        }
        SyntaxNode body = onlyExpr(n);
        return concat(head, text(" ="), group(nest(INDENT, concat(LINE, expr(body)))));
    }

    private Doc fnParamList(SyntaxNode n) {
        List<Doc> params = new ArrayList<>();
        for (SyntaxNode p : childNodes(n, SyntaxKind.FN_PARAM)) {
            Doc d = text(firstIdent(p));
            var fnType = p.child(SyntaxKind.FN_TYPE);
            if (fnType.isPresent()) {
                d = concat(d, text(": "), fnType(fnType.get()));
            } else {
                var rt = p.child(SyntaxKind.RET_TYPE);
                if (rt.isPresent()) {
                    d = concat(d, text(": "), retType(rt.get()));
                }
            }
            params.add(d);
        }
        return concat(text("("), Doc.join(text(", "), params), text(")"));
    }

    // --- types ---

    private Doc fnType(SyntaxNode n) {
        List<Doc> params = new ArrayList<>();
        Doc result = Doc.NIL;
        boolean afterArrow = false;
        for (SyntaxElement e : meaningful(n)) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.ARROW) {
                afterArrow = true;
            } else if (e instanceof SyntaxNode c && c.kind() == SyntaxKind.RET_TYPE) {
                if (afterArrow) {
                    result = retType(c);
                } else {
                    params.add(retType(c));
                }
            }
        }
        return concat(text("("), Doc.join(text(", "), params), text(") -> "), result);
    }

    private Doc retType(SyntaxNode n) {
        List<Doc> cases = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (isTypeNode(c.kind())) {
                cases.add(typeRef(c));
            }
        }
        return Doc.join(text(" | "), cases);
    }

    private Doc typeRef(SyntaxNode n) {
        if (n.kind() == SyntaxKind.TUPLE_TYPE) {
            List<Doc> elems = new ArrayList<>();
            for (SyntaxNode c : n.childNodes()) {
                if (isTypeNode(c.kind())) {
                    elems.add(typeRef(c));
                }
            }
            return concat(text("("), Doc.join(text(", "), elems), text(")"));
        }
        var typevar = n.token(SyntaxKind.TYPEVAR);
        if (typevar.isPresent()) {
            return text(typevar.get().text());
        }
        String name = firstIdent(n);
        var args = n.child(SyntaxKind.TYPE_ARGS);
        if (args.isEmpty()) {
            return text(name);
        }
        List<Doc> typeArgs = new ArrayList<>();
        for (SyntaxNode c : args.get().childNodes()) {
            if (isTypeNode(c.kind())) {
                typeArgs.add(typeRef(c));
            }
        }
        return concat(text(name), text("<"), Doc.join(text(", "), typeArgs), text(">"));
    }

    private static boolean isTypeNode(SyntaxKind k) {
        return k == SyntaxKind.TYPE_REF || k == SyntaxKind.TUPLE_TYPE;
    }

    // --- expressions ---

    private Doc expr(SyntaxNode n) {
        return switch (n.kind()) {
            case LITERAL_EXPR -> text(firstMeaningfulToken(n).text());
            case VAR_EXPR -> text(firstIdent(n));
            case FIELD_ACCESS -> concat(expr(firstExprChild(n)), text("."), text(lastIdent(n)));
            case CALL_EXPR -> call(n);
            case BINARY_EXPR -> binary(n);
            case UNARY_EXPR -> concat(text("-"), expr(onlyExpr(n)));
            case PIPE_EXPR -> pipe(n);
            case PAREN_EXPR -> concat(text("("), expr(onlyExpr(n)), text(")"));
            case TUPLE_EXPR -> concat(text("("), Doc.join(text(", "), exprDocs(n)), text(")"));
            case LIST_EXPR -> list(n);
            case LIST_COMP -> listComp(n);
            case IF_EXPR -> ifExpr(n);
            case MATCH_EXPR -> matchExpr(n);
            case LAMBDA_EXPR -> lambda(n);
            case NEW_DATA_EXPR -> newData(n);
            case BLOCK_EXPR -> block(n);
            default -> text(n.text().strip());
        };
    }

    private Doc call(SyntaxNode n) {
        StringBuilder fn = new StringBuilder();
        for (SyntaxToken t : idents(n)) {
            if (fn.length() > 0) {
                fn.append('.');
            }
            fn.append(t.text());
        }
        List<SyntaxNode> args = n.child(SyntaxKind.ARG_LIST).map(this::exprChildren).orElse(List.of());
        if (args.isEmpty()) {
            return concat(text(fn.toString()), text("()"));
        }
        List<Doc> argDocs = new ArrayList<>();
        for (SyntaxNode a : args) {
            argDocs.add(expr(a));
        }
        return concat(text(fn.toString()), group(concat(text("("),
                nest(INDENT, concat(SOFTLINE, Doc.join(concat(text(","), LINE), argDocs))),
                SOFTLINE, text(")"))));
    }

    private Doc binary(SyntaxNode n) {
        List<SyntaxNode> ops = exprChildren(n);
        String op = operatorText(n);
        return concat(expr(ops.get(0)), text(" " + op + " "), expr(ops.get(1)));
    }

    private Doc pipe(SyntaxNode n) {
        List<Doc> stages = new ArrayList<>();
        Doc head = collectPipe(n, stages);
        List<Doc> tail = new ArrayList<>();
        for (Doc s : stages) {
            tail.add(concat(LINE, text("|> "), s));
        }
        return group(concat(head, nest(INDENT, concat(tail))));
    }

    /** Flattens a left-nested {@code |>} chain: returns the head doc and fills {@code stages} with each
     * right-hand stage in source order. */
    private Doc collectPipe(SyntaxNode n, List<Doc> stages) {
        List<SyntaxNode> ops = exprChildren(n);
        SyntaxNode left = ops.get(0);
        SyntaxNode right = ops.get(1);
        Doc head;
        if (left.kind() == SyntaxKind.PIPE_EXPR) {
            head = collectPipe(left, stages);
        } else {
            head = expr(left);
        }
        stages.add(expr(right));
        return head;
    }

    private Doc list(SyntaxNode n) {
        List<Doc> elems = exprDocs(n);
        if (elems.isEmpty()) {
            return text("[]");
        }
        return group(concat(text("["),
                nest(INDENT, concat(SOFTLINE, Doc.join(concat(text(","), LINE), elems))),
                SOFTLINE, text("]")));
    }

    private Doc listComp(SyntaxNode n) {
        List<Doc> exprs = exprDocs(n);
        List<Doc> guards = exprs.subList(1, exprs.size());
        return concat(text("["), exprs.get(0), text(" | "), Doc.join(text(", "), guards), text("]"));
    }

    private Doc ifExpr(SyntaxNode n) {
        List<SyntaxNode> parts = exprChildren(n);
        return group(concat(text("if "), expr(parts.get(0)), text(" then"),
                nest(INDENT, concat(LINE, expr(parts.get(1)))),
                LINE, text("else"),
                nest(INDENT, concat(LINE, expr(parts.get(2))))));
    }

    private Doc matchExpr(SyntaxNode n) {
        SyntaxNode scrutinee = exprChildren(n).get(0);
        List<Doc> cases = new ArrayList<>();
        for (SyntaxNode c : childNodes(n, SyntaxKind.MATCH_CASE)) {
            cases.add(concat(HARDLINE, matchCase(c)));
        }
        return concat(text("match "), expr(scrutinee), text(" with"), nest(INDENT, concat(cases)));
    }

    private Doc matchCase(SyntaxNode n) {
        StringBuilder pattern = new StringBuilder();
        SyntaxNode body = null;
        boolean afterArrow = false;
        for (SyntaxElement e : meaningful(n)) {
            if (afterArrow) {
                body = (SyntaxNode) e;
                break;
            }
            if (e instanceof SyntaxToken t) {
                if (t.kind() == SyntaxKind.ARROW) {
                    afterArrow = true;
                    continue;
                }
                if (pattern.length() > 0 && t.kind() != SyntaxKind.COMMA) {
                    pattern.append(' ');
                }
                pattern.append(t.text());
            }
        }
        return concat(text("| "), text(pattern.toString()), text(" -> "), expr(body));
    }

    private Doc lambda(SyntaxNode n) {
        List<SyntaxToken> params = idents(n);
        Doc paramsDoc;
        if (n.token(SyntaxKind.LPAREN).isPresent()) {
            List<Doc> ps = new ArrayList<>();
            for (SyntaxToken t : params) {
                ps.add(text(t.text()));
            }
            paramsDoc = concat(text("("), Doc.join(text(", "), ps), text(")"));
        } else {
            paramsDoc = text(params.get(0).text());
        }
        return concat(paramsDoc, text(" -> "), expr(lastExprChild(n)));
    }

    private Doc newData(SyntaxNode n) {
        String typeName = firstIdent(n);
        List<Doc> members = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (c.kind() == SyntaxKind.SPREAD_MEMBER) {
                members.add(concat(text("..."), text(firstIdent(c))));
            } else if (c.kind() == SyntaxKind.FIELD_INIT) {
                var value = firstExprChildOpt(c);
                members.add(value.map(v -> concat(text(firstIdent(c)), text(" = "), expr(v)))
                        .orElse(text(firstIdent(c))));   // shorthand `field`
            }
        }
        if (members.isEmpty()) {
            return concat(text(typeName), text(" {}"));
        }
        return concat(text(typeName), group(concat(text(" {"),
                nest(INDENT, concat(LINE, Doc.join(concat(text(","), LINE), members))),
                LINE, text("}"))));
    }

    private Doc block(SyntaxNode n) {
        List<Doc> lines = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            Doc d = switch (c.kind()) {
                case LET_STMT -> concat(text("let "), text(firstIdent(c)), text(" = "), expr(onlyExpr(c)));
                case TUPLE_DESTRUCTURE -> tupleDestructure(c);
                case REQUIRE_STMT -> requireStmt(c);
                default -> expr(c);   // the result expression
            };
            lines.add(concat(HARDLINE, d));
        }
        return concat(text("{"), nest(INDENT, concat(lines)), HARDLINE, text("}"));
    }

    private Doc tupleDestructure(SyntaxNode n) {
        List<Doc> names = new ArrayList<>();
        for (SyntaxToken t : idents(n)) {
            names.add(text(t.text()));
        }
        return concat(text("let ("), Doc.join(text(", "), names), text(") = "), expr(onlyExpr(n)));
    }

    private Doc requireStmt(SyntaxNode n) {
        List<SyntaxNode> exprs = exprChildren(n);
        return concat(text("require "), expr(exprs.get(0)), text(" else "), expr(exprs.get(1)));
    }

    // --- comments / blank lines ---

    private record Leading(List<String> comments) {}

    /** The full-line comments that sit directly above a top-level item (its leading trivia). Blank
     * lines are normalised away by {@link #blankBetween}, so only the comment text is carried. */
    private Leading leading(SyntaxNode n) {
        List<String> comments = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t) {
                if (t.kind() == SyntaxKind.WHITESPACE) {
                    continue;
                }
                if (t.kind() == SyntaxKind.LINE_COMMENT) {
                    comments.add(t.text().stripTrailing());
                } else {
                    break;   // the first real token
                }
            } else {
                break;   // the first child node
            }
        }
        return new Leading(comments);
    }

    private List<String> trailingComments(SyntaxNode file) {
        List<String> out = new ArrayList<>();
        int lastNode = -1;
        List<SyntaxElement> es = file.children();
        for (int i = 0; i < es.size(); i++) {
            if (es.get(i) instanceof SyntaxNode) {
                lastNode = i;
            }
        }
        for (int i = lastNode + 1; i < es.size(); i++) {
            if (es.get(i) instanceof SyntaxToken t && t.kind() == SyntaxKind.LINE_COMMENT) {
                out.add(t.text().stripTrailing());
            }
        }
        return out;
    }

    // --- CST navigation ---

    private Doc qualifiedName(SyntaxNode n) {
        StringBuilder sb = new StringBuilder();
        for (SyntaxToken t : idents(n)) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(t.text());
        }
        return text(sb.toString());
    }

    private List<Doc> exprDocs(SyntaxNode n) {
        List<Doc> out = new ArrayList<>();
        for (SyntaxNode c : exprChildren(n)) {
            out.add(expr(c));
        }
        return out;
    }

    private List<SyntaxNode> exprChildren(SyntaxNode n) {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (isExprKind(c.kind())) {
                out.add(c);
            }
        }
        return out;
    }

    private SyntaxNode firstExprChild(SyntaxNode n) {
        return exprChildren(n).get(0);
    }

    private Optional<SyntaxNode> firstExprChildOpt(SyntaxNode n) {
        List<SyntaxNode> c = exprChildren(n);
        return c.isEmpty() ? Optional.empty() : Optional.of(c.get(0));
    }

    private SyntaxNode lastExprChild(SyntaxNode n) {
        List<SyntaxNode> c = exprChildren(n);
        return c.get(c.size() - 1);
    }

    private SyntaxNode onlyExpr(SyntaxNode n) {
        return firstExprChild(n);
    }

    private static boolean isExprKind(SyntaxKind k) {
        return switch (k) {
            case LITERAL_EXPR, VAR_EXPR, FIELD_ACCESS, CALL_EXPR, BINARY_EXPR, UNARY_EXPR, PIPE_EXPR,
                 PAREN_EXPR, TUPLE_EXPR, LIST_EXPR, LIST_COMP, IF_EXPR, MATCH_EXPR, LAMBDA_EXPR,
                 NEW_DATA_EXPR, BLOCK_EXPR -> true;
            default -> false;
        };
    }

    private List<SyntaxNode> childNodes(SyntaxNode n, SyntaxKind kind) {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (c.kind() == kind) {
                out.add(c);
            }
        }
        return out;
    }

    private List<SyntaxToken> idents(SyntaxNode n) {
        List<SyntaxToken> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                out.add(t);
            }
        }
        return out;
    }

    private String firstIdent(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                return t.text();
            }
        }
        throw new IllegalStateException("no identifier in " + n.kind());
    }

    private String lastIdent(SyntaxNode n) {
        String last = null;
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                last = t.text();
            }
        }
        return last;
    }

    private String operatorText(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia() && isBinaryOperator(t.kind())) {
                return t.text();
            }
        }
        throw new IllegalStateException("no operator in " + n.kind());
    }

    private static boolean isBinaryOperator(SyntaxKind k) {
        return switch (k) {
            case EQ, NE, LT, LE, GT, GE, AND, OR, PLUS, MINUS, STAR, SLASH, PLUSPLUS -> true;
            default -> false;
        };
    }

    private SyntaxNode typeChild(SyntaxNode n) {
        for (SyntaxNode c : n.childNodes()) {
            if (isTypeNode(c.kind())) {
                return c;
            }
        }
        throw new IllegalStateException("no type in " + n.kind());
    }

    private List<SyntaxElement> meaningful(SyntaxNode n) {
        List<SyntaxElement> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxNode || (e instanceof SyntaxToken t && !t.isTrivia())) {
                out.add(e);
            }
        }
        return out;
    }

    private SyntaxToken firstMeaningfulToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                return t;
            }
        }
        throw new IllegalStateException("no token in " + n.kind());
    }
}
