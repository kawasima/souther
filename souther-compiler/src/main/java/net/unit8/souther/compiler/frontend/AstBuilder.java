package net.unit8.souther.compiler.frontend;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.cst.LineIndex;
import net.unit8.souther.compiler.cst.SyntaxElement;
import net.unit8.souther.compiler.cst.SyntaxKind;
import net.unit8.souther.compiler.cst.SyntaxNode;
import net.unit8.souther.compiler.cst.SyntaxToken;
import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;
import net.unit8.souther.compiler.diag.SourcePos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the compiler's {@link Ast} from a concrete syntax tree. This is where the surface forms the
 * parser deliberately kept lossless are lowered to the desugared AST every later stage expects:
 * {@code |>} folds into a call's last argument, {@code require} becomes an {@code if}, {@code let}
 * becomes a {@code LetIn}, {@code T?} becomes {@code Option<T>}, and the {@code let}/{@code match}
 * destructurings expand into positional reads. The CST feeds the formatter and the LSP untouched;
 * only the compiler pipeline runs through here.
 */
public final class AstBuilder {

    private final LineIndex lines;
    private String moduleName = "";
    private int matchWholeCounter = 0;
    private int tupleCounter = 0;

    private AstBuilder(String source) {
        this.lines = new LineIndex(source);
    }

    /** Builds a module from a parsed source file. A header-less source is named
     * {@code defaultModuleName}; a {@code null} default makes the header required. */
    public static Ast.Module build(SyntaxNode sourceFile, String source, String defaultModuleName) {
        return new AstBuilder(source).module(sourceFile, defaultModuleName);
    }

    // --- module ---

    private Ast.Module module(SyntaxNode file, String defaultModuleName) {
        Optional<SyntaxNode> exampleFile = file.child(SyntaxKind.EXAMPLES_FILE_HEADER);
        if (exampleFile.isPresent()) {
            return exampleFileModule(file, exampleFile.get());
        }
        Optional<SyntaxNode> header = file.child(SyntaxKind.MODULE_HEADER);
        String name;
        SourcePos pos;
        Map<String, Ast.RetType> exposedOutputs = new HashMap<>();
        List<String> exposing = new ArrayList<>();
        if (header.isPresent()) {
            SyntaxNode h = header.get();
            name = qualifiedNameText(h.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
            pos = pos(h);
        } else if (defaultModuleName != null) {
            name = defaultModuleName;
            pos = pos(file);
        } else {
            throw error(pos(file), "parse.module", "expected `module` declaration");
        }
        moduleName = name;   // set before any type is read, so type-variable gating knows the namespace
        header.flatMap(h -> h.child(SyntaxKind.EXPOSING_CLAUSE))
                .ifPresent(c -> readExposing(c, exposing, exposedOutputs));

        List<Ast.Import> imports = new ArrayList<>();
        List<Ast.Def> defs = new ArrayList<>();
        List<Ast.BehaviorDef> behaviors = new ArrayList<>();
        List<Ast.FnDef> fns = new ArrayList<>();
        List<Ast.Example> examples = new ArrayList<>();
        List<Ast.Fake> fakes = new ArrayList<>();
        for (SyntaxNode n : file.childNodes()) {
            switch (n.kind()) {
                case IMPORT_DECL -> imports.add(importDecl(n));
                case DATA_DEF -> defs.add(dataDef(n));
                case BEHAVIOR_DEF -> behaviors.add(behaviorDef(n));
                case FN_DEF -> fns.add(fnDef(n));
                case EXAMPLE_DEF -> examples.add(example(n));
                case FAKE_DEF -> fakes.add(fake(n));
                default -> { /* MODULE_HEADER handled above; ERROR nodes are reported already */ }
            }
        }
        return new Ast.Module(name, exposing, exposedOutputs, imports, defs, behaviors, fns,
                examples, fakes, null, pos);
    }

    /** An {@code examples for <module>} file: an example-only contribution to its target module. Any
     * non-example declaration in it is E1906. The returned {@link Ast.Module} carries only examples
     * and its {@code exampleFileTarget}; the compiler merges it into the target. */
    private Ast.Module exampleFileModule(SyntaxNode file, SyntaxNode header) {
        String target = qualifiedNameText(header.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
        moduleName = target;
        SourcePos pos = pos(header);
        List<Ast.Example> examples = new ArrayList<>();
        List<Ast.Fake> fakes = new ArrayList<>();
        for (SyntaxNode n : file.childNodes()) {
            switch (n.kind()) {
                case EXAMPLE_DEF -> examples.add(example(n));
                case FAKE_DEF -> fakes.add(fake(n));
                case EXAMPLES_FILE_HEADER -> { /* the header itself */ }
                case IMPORT_DECL, DATA_DEF, BEHAVIOR_DEF, FN_DEF -> throw CompileException.of(
                        Diagnostic.of("E1906", "check.example.file.only").title("check.example.title")
                                .at(pos(n)).build(),
                        "an `examples for` file may contain only examples");
                default -> { /* ERROR nodes already reported */ }
            }
        }
        return new Ast.Module(target, List.of(), new HashMap<>(), List.of(), List.of(), List.of(),
                List.of(), examples, fakes, target, pos);
    }

    /** {@code example <target> | rows...}. The contextual {@code example} lexes as an identifier, so
     * the target is the second identifier token. */
    private Ast.Example example(SyntaxNode n) {
        List<SyntaxToken> idents = identTokens(n);
        String target = idents.size() >= 2 ? idents.get(1).text() : "";
        SourcePos pos = idents.size() >= 2 ? posOf(idents.get(1)) : pos(n);
        List<Ast.ExampleRow> rows = new ArrayList<>();
        for (SyntaxNode row : childNodes(n, SyntaxKind.EXAMPLE_ROW)) {
            rows.add(exampleRow(row));
        }
        return new Ast.Example(target, rows, pos);
    }

    /** {@code [ "desc" : ] ( inputs ) -> expected}. The description is a leading string token; the
     * inputs are the {@code ARG_LIST}'s expressions; the expected is the remaining expression. */
    private Ast.ExampleRow exampleRow(SyntaxNode n) {
        String description = n.token(SyntaxKind.STRING_LIT).map(t -> stringValue(t.text())).orElse(null);
        List<Ast.Expr> inputs = new ArrayList<>();
        n.child(SyntaxKind.ARG_LIST).ifPresent(list -> {
            for (SyntaxNode a : exprChildren(list)) {
                inputs.add(expr(a));
            }
        });
        List<Ast.With> withs = new ArrayList<>();
        n.child(SyntaxKind.WITH_CLAUSE).ifPresent(clause -> {
            for (SyntaxNode b : childNodes(clause, SyntaxKind.WITH_BINDING)) {
                withs.add(new Ast.With(firstIdentText(b), expr(firstExprChild(b)), pos(b)));
            }
        });
        // the expected is the row's own expr child (ARG_LIST holds the inputs; WITH_CLAUSE the fakes)
        List<SyntaxNode> expectedNodes = exprChildren(n);
        Ast.Expr expected = expectedNodes.isEmpty() ? null : expr(expectedNodes.get(0));
        return new Ast.ExampleRow(description, inputs, withs, expected, pos(n));
    }

    /** {@code fake <target> | rows}. The contextual {@code fake} lexes as an identifier, so the
     * target is the second identifier token. */
    private Ast.Fake fake(SyntaxNode n) {
        List<SyntaxToken> idents = identTokens(n);
        String target = idents.size() >= 2 ? idents.get(1).text() : "";
        SourcePos pos = idents.size() >= 2 ? posOf(idents.get(1)) : pos(n);
        List<Ast.FakeRow> rows = new ArrayList<>();
        for (SyntaxNode row : childNodes(n, SyntaxKind.FAKE_ROW)) {
            rows.add(fakeRow(row));
        }
        return new Ast.Fake(target, rows, pos);
    }

    /** {@code ( args ) -> out} or {@code _ -> out}. A row with no {@code ARG_LIST} is the default. */
    private Ast.FakeRow fakeRow(SyntaxNode n) {
        Optional<SyntaxNode> args = n.child(SyntaxKind.ARG_LIST);
        List<Ast.Expr> inputs = new ArrayList<>();
        args.ifPresent(list -> {
            for (SyntaxNode a : exprChildren(list)) {
                inputs.add(expr(a));
            }
        });
        boolean isDefault = args.isEmpty();
        List<SyntaxNode> exprs = exprChildren(n);   // the output (not inside ARG_LIST)
        Ast.Expr output = exprs.isEmpty() ? null : expr(exprs.get(0));
        return new Ast.FakeRow(isDefault ? null : inputs, output, isDefault, pos(n));
    }

    private void readExposing(SyntaxNode clause, List<String> names, Map<String, Ast.RetType> outputs) {
        for (SyntaxNode entry : childNodes(clause, SyntaxKind.EXPOSED_ENTRY)) {
            String name = qualifiedNameText(entry.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
            names.add(name);
            entry.child(SyntaxKind.RET_TYPE).ifPresent(rt -> outputs.put(name, retType(rt)));
        }
    }

    private Ast.Import importDecl(SyntaxNode n) {
        String module = qualifiedNameText(n.child(SyntaxKind.QUALIFIED_NAME).orElseThrow());
        List<String> names = new ArrayList<>();
        n.child(SyntaxKind.NAME_LIST).ifPresent(list -> {
            for (SyntaxToken t : identTokens(list)) {
                names.add(t.text());
            }
        });
        return new Ast.Import(module, names, pos(n));
    }

    // --- data ---

    private Ast.Def dataDef(SyntaxNode n) {
        String name = firstIdentText(n);
        SourcePos pos = pos(n);
        Optional<Ast.Expr> invariant = invariant(n);

        Optional<SyntaxNode> product = n.child(SyntaxKind.PRODUCT_BODY);
        if (product.isPresent()) {
            List<String> includes = new ArrayList<>();
            List<Ast.Field> fields = new ArrayList<>();
            for (SyntaxNode member : product.get().childNodes()) {
                if (member.kind() == SyntaxKind.SPREAD_MEMBER) {
                    includes.add(firstIdentText(member));
                } else if (member.kind() == SyntaxKind.FIELD) {
                    fields.add(field(member));
                }
            }
            return new Ast.Data(name, false, includes, fields, invariant,
                    Optional.empty(), Optional.empty(), pos);
        }
        Optional<SyntaxNode> sum = n.child(SyntaxKind.SUM_BODY);
        if (sum.isPresent()) {
            List<String> cases = new ArrayList<>();
            for (SyntaxToken t : identTokens(sum.get())) {
                cases.add(t.text());
            }
            return new Ast.SumData(name, cases, Optional.empty(), Optional.empty(), pos);
        }
        Optional<SyntaxNode> newtype = n.child(SyntaxKind.NEWTYPE_BODY);
        if (newtype.isPresent()) {
            SyntaxToken inner = identTokens(newtype.get()).get(0);
            Ast.TypeRef innerType = new Ast.TypeRef(inner.text(), null, posOf(inner));
            List<Ast.Field> fields = List.of(new Ast.Field("value", innerType, posOf(inner)));
            return new Ast.Data(name, true, List.of(), fields, invariant,
                    Optional.empty(), Optional.empty(), pos);
        }
        return new Ast.UnitData(name, pos);
    }

    /** Conjoins every {@code invariant} clause under {@code &&}; each line must hold. */
    private Optional<Ast.Expr> invariant(SyntaxNode dataDef) {
        Optional<Ast.Expr> acc = Optional.empty();
        for (SyntaxNode clause : childNodes(dataDef, SyntaxKind.INVARIANT_CLAUSE)) {
            Ast.Expr next = expr(onlyExpr(clause));
            acc = Optional.of(acc.map(prev -> (Ast.Expr)
                            new Ast.Binary(Ast.BinOp.AND, prev, next, next.pos()))
                    .orElse(next));
        }
        return acc;
    }

    private Ast.Field field(SyntaxNode n) {
        String name = firstIdentText(n);
        Ast.TypeRef type = typeRef(typeChild(n));
        if (n.token(SyntaxKind.QUESTION).isPresent()) {
            type = new Ast.TypeRef("Option", type, type.pos());   // `T?` → Option<T>
        }
        return new Ast.Field(name, type, pos(n));
    }

    // --- behavior ---

    private Ast.BehaviorDef behaviorDef(SyntaxNode n) {
        String name = firstIdentText(n);
        SourcePos pos = pos(n);
        Optional<SyntaxNode> sig = n.child(SyntaxKind.BEHAVIOR_SIG);
        if (sig.isPresent()) {
            SyntaxNode s = sig.get();
            List<Ast.Param> params = new ArrayList<>();
            s.child(SyntaxKind.PARAM_LIST).ifPresent(pl -> {
                for (SyntaxNode p : childNodes(pl, SyntaxKind.PARAM)) {
                    params.add(new Ast.Param(firstIdentText(p), retType(p.child(SyntaxKind.RET_TYPE).orElseThrow()), pos(p)));
                }
            });
            Ast.RetType ret = retType(s.child(SyntaxKind.RET_TYPE).orElseThrow());
            List<String> constructs = new ArrayList<>();
            List<String> requires = new ArrayList<>();
            for (SyntaxNode clause : s.childNodes()) {
                if (clause.kind() == SyntaxKind.CONSTRUCTS_CLAUSE) {
                    for (SyntaxToken t : identTokens(clause)) {
                        constructs.add(t.text());
                    }
                } else if (clause.kind() == SyntaxKind.REQUIRES_CLAUSE) {
                    for (SyntaxToken t : identTokens(clause)) {
                        requires.add(t.text());
                    }
                }
            }
            return new Ast.SpecBehavior(name, params, ret, constructs, requires, pos);
        }
        SyntaxNode pipe = n.child(SyntaxKind.PIPE_BEHAVIOR).orElseThrow();
        List<String> stages = new ArrayList<>();
        for (SyntaxNode st : childNodes(pipe, SyntaxKind.STAGE)) {
            StringBuilder sb = new StringBuilder();
            for (SyntaxToken t : identTokens(st)) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(t.text());
            }
            stages.add(sb.toString());
        }
        Ast.RetType declaredOut = pipe.child(SyntaxKind.RET_TYPE).map(this::retType).orElse(null);
        return new Ast.PipeBehavior(name, stages, declaredOut, pos);
    }

    // --- fn ---

    private Ast.FnDef fnDef(SyntaxNode n) {
        String name = firstIdentText(n);
        SourcePos pos = pos(n);
        List<Ast.FnParam> params = new ArrayList<>();
        n.child(SyntaxKind.FN_PARAM_LIST).ifPresent(pl -> {
            for (SyntaxNode p : childNodes(pl, SyntaxKind.FN_PARAM)) {
                params.add(fnParam(p));
            }
        });
        Ast.RetType declaredReturn = n.child(SyntaxKind.RET_TYPE).map(this::retType).orElse(null);
        boolean partial = n.child(SyntaxKind.PARTIAL_MODIFIER).isPresent();

        Optional<SyntaxNode> intrinsic = n.child(SyntaxKind.INTRINSIC_BODY);
        if (intrinsic.isPresent()) {
            if (!isReservedNamespace(moduleName)) {
                throw error(pos, "parse.intrinsic.core",
                        "`intrinsic` is a core privilege: only a module in the reserved `souther`"
                                + " namespace may declare one (ADR-0028)");
            }
            String key = stringValue(intrinsic.get().token(SyntaxKind.STRING_LIT).orElseThrow().text());
            return new Ast.FnDef(name, params, declaredReturn, key, null, partial, pos);
        }
        SyntaxNode bodyNode = onlyExpr(n);
        return new Ast.FnDef(name, params, declaredReturn, null, expr(bodyNode), partial, pos);
    }

    private Ast.FnParam fnParam(SyntaxNode p) {
        String name = firstIdentText(p);
        Ast.ParamType type = null;
        Optional<SyntaxNode> fnType = p.child(SyntaxKind.FN_TYPE);
        if (fnType.isPresent()) {
            type = fnType(fnType.get());
        } else {
            Optional<SyntaxNode> rt = p.child(SyntaxKind.RET_TYPE);
            if (rt.isPresent()) {
                type = retType(rt.get());
            }
        }
        return new Ast.FnParam(name, type, pos(p));
    }

    private Ast.FnType fnType(SyntaxNode n) {
        List<Ast.RetType> params = new ArrayList<>();
        Ast.RetType result = null;
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
        return new Ast.FnType(params, result, pos(n));
    }

    // --- types ---

    private Ast.RetType retType(SyntaxNode n) {
        List<Ast.TypeRef> cases = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (c.kind() == SyntaxKind.TYPE_REF || c.kind() == SyntaxKind.TUPLE_TYPE) {
                cases.add(typeRef(c));
            }
        }
        return new Ast.RetType(cases, cases.get(0).pos());
    }

    private Ast.TypeRef typeRef(SyntaxNode n) {
        if (n.kind() == SyntaxKind.TUPLE_TYPE) {
            List<Ast.TypeRef> elems = new ArrayList<>();
            for (SyntaxNode c : n.childNodes()) {
                if (c.kind() == SyntaxKind.TYPE_REF || c.kind() == SyntaxKind.TUPLE_TYPE) {
                    elems.add(typeRef(c));
                }
            }
            if (elems.size() == 1) {
                return elems.get(0);   // `(T)` reads as grouping
            }
            return new Ast.TypeRef(null, null, elems, pos(n));
        }
        Optional<SyntaxToken> typevar = n.token(SyntaxKind.TYPEVAR);
        if (typevar.isPresent()) {
            String v = typevar.get().text();
            if (!isReservedNamespace(moduleName)) {
                throw error(pos(n), "parse.typevar.core",
                        "type variable `" + v + "` is only allowed in the core (the reserved `souther`"
                                + " namespace); a user model stays bounded (ADR-0028)", v);
            }
            return new Ast.TypeRef(v, null, pos(n));   // name begins with `'` → Type.Var
        }
        String name = firstIdentText(n);
        Optional<SyntaxNode> args = n.child(SyntaxKind.TYPE_ARGS);
        if (args.isEmpty()) {
            return new Ast.TypeRef(name, null, pos(n));
        }
        List<Ast.TypeRef> typeArgs = new ArrayList<>();
        for (SyntaxNode c : args.get().childNodes()) {
            if (c.kind() == SyntaxKind.TYPE_REF || c.kind() == SyntaxKind.TUPLE_TYPE) {
                typeArgs.add(typeRef(c));
            }
        }
        if (name.equals("Map")) {
            // carry the value in `arg` and the key in `tupleElems` (ADR-0040)
            Ast.TypeRef key = typeArgs.get(0);
            Ast.TypeRef value = typeArgs.get(typeArgs.size() - 1);
            return new Ast.TypeRef("Map", value, List.of(key), pos(n));
        }
        return new Ast.TypeRef(name, typeArgs.get(0), pos(n));   // List<T> / Set<T> / Option<T>
    }

    // --- expressions ---

    private Ast.Expr expr(SyntaxNode n) {
        return switch (n.kind()) {
            case LITERAL_EXPR -> literal(n);
            case VAR_EXPR -> new Ast.Var(firstIdentText(n), pos(n));
            case FIELD_ACCESS -> fieldAccess(n);
            case CALL_EXPR -> call(n);
            case BINARY_EXPR -> binary(n);
            case UNARY_EXPR -> new Ast.Neg(expr(onlyExpr(n)), pos(n));
            case PIPE_EXPR -> pipe(n);
            case PAREN_EXPR -> expr(onlyExpr(n));
            case TUPLE_EXPR -> new Ast.Tuple(exprList(n), pos(n));
            case LIST_EXPR -> new Ast.ListLit(exprList(n), pos(n));
            case LIST_COMP -> listComp(n);
            case IF_EXPR -> ifExpr(n);
            case MATCH_EXPR -> matchExpr(n);
            case LAMBDA_EXPR -> lambda(n);
            case NEW_DATA_EXPR -> newData(n);
            case BLOCK_EXPR -> block(n);
            default -> throw error(pos(n), "parse.expr", "expected an expression");
        };
    }

    private Ast.Expr literal(SyntaxNode n) {
        SyntaxToken t = firstMeaningfulToken(n);
        SourcePos pos = posOf(t);
        return switch (t.kind()) {
            case INT_LIT -> new Ast.IntLit(Long.parseLong(t.text()), pos);
            case DECIMAL_LIT -> new Ast.DecimalLit(new BigDecimal(stripDecimalSuffix(t.text())), pos);
            case STRING_LIT -> new Ast.StringLit(stringValue(t.text()), pos);
            case TRUE_KW -> new Ast.BoolLit(true, pos);
            case FALSE_KW -> new Ast.BoolLit(false, pos);
            default -> throw error(pos, "parse.expr", "expected a literal");
        };
    }

    private Ast.Expr fieldAccess(SyntaxNode n) {
        Ast.Expr target = expr(firstExprChild(n));
        SyntaxToken field = lastIdentToken(n);
        return new Ast.FieldAccess(target, field.text(), posOf(field));
    }

    private Ast.Expr call(SyntaxNode n) {
        StringBuilder fn = new StringBuilder();
        SyntaxToken first = null;
        for (SyntaxElement e : meaningful(n)) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                if (fn.length() > 0) {
                    fn.append('.');
                }
                fn.append(t.text());
                if (first == null) {
                    first = t;
                }
            }
        }
        List<Ast.Expr> args = new ArrayList<>();
        n.child(SyntaxKind.ARG_LIST).ifPresent(list -> {
            for (SyntaxNode a : exprChildren(list)) {
                args.add(expr(a));
            }
        });
        return new Ast.Call(fn.toString(), args, posOf(first));
    }

    private Ast.Expr binary(SyntaxNode n) {
        List<SyntaxNode> operands = exprChildren(n);
        SyntaxToken op = operatorToken(n);
        return new Ast.Binary(binOp(op.kind()), expr(operands.get(0)), expr(operands.get(1)), posOf(op));
    }

    private static Ast.BinOp binOp(SyntaxKind k) {
        return switch (k) {
            case EQ -> Ast.BinOp.EQ;
            case NE -> Ast.BinOp.NE;
            case LT -> Ast.BinOp.LT;
            case LE -> Ast.BinOp.LE;
            case GT -> Ast.BinOp.GT;
            case GE -> Ast.BinOp.GE;
            case AND -> Ast.BinOp.AND;
            case OR -> Ast.BinOp.OR;
            case PLUS -> Ast.BinOp.ADD;
            case MINUS -> Ast.BinOp.SUB;
            case STAR -> Ast.BinOp.MUL;
            case SLASH -> Ast.BinOp.DIV;
            case PLUSPLUS -> Ast.BinOp.CONCAT;
            default -> throw new IllegalStateException("not a binary operator: " + k);
        };
    }

    /** {@code e |> f(a)} → {@code f(a, e)}; {@code e |> f} → {@code f(e)}; {@code e |> Mod.name}
     * → {@code Mod.name(e)} (spec §pipe). */
    private Ast.Expr pipe(SyntaxNode n) {
        List<SyntaxNode> operands = exprChildren(n);
        Ast.Expr left = expr(operands.get(0));
        Ast.Expr right = expr(operands.get(1));
        if (right instanceof Ast.Call c) {
            List<Ast.Expr> args = new ArrayList<>(c.args());
            args.add(left);
            return new Ast.Call(c.fn(), args, c.pos());
        }
        if (right instanceof Ast.Var v) {
            return new Ast.Call(v.name(), List.of(left), v.pos());
        }
        if (right instanceof Ast.FieldAccess fa && fa.target() instanceof Ast.Var base) {
            return new Ast.Call(base.name() + "." + fa.field(), List.of(left), fa.pos());
        }
        throw CompileException.of(
                Diagnostic.of(null, "parse.vpipe.right").title("parse.title").at(right.pos()).build(),
                "the right side of `|>` must be a function call or a function name");
    }

    private Ast.Expr listComp(SyntaxNode n) {
        List<SyntaxNode> exprs = exprChildren(n);
        Ast.Expr element = expr(exprs.get(0));
        List<Ast.Expr> guards = new ArrayList<>();
        for (int i = 1; i < exprs.size(); i++) {
            guards.add(expr(exprs.get(i)));
        }
        return new Ast.ListComp(element, guards, pos(n));
    }

    private Ast.Expr ifExpr(SyntaxNode n) {
        List<SyntaxNode> exprs = exprChildren(n);
        return new Ast.If(expr(exprs.get(0)), expr(exprs.get(1)), expr(exprs.get(2)), pos(n));
    }

    private Ast.Expr lambda(SyntaxNode n) {
        List<String> params = new ArrayList<>();
        for (SyntaxToken t : identTokens(n)) {
            params.add(t.text());
        }
        Ast.Expr body = expr(lastExprChild(n));
        return new Ast.Block(params, body, pos(n));
    }

    private Ast.Expr newData(SyntaxNode n) {
        String typeName = firstIdentText(n);
        List<Ast.FieldInit> inits = new ArrayList<>();
        List<String> spreads = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (c.kind() == SyntaxKind.SPREAD_MEMBER) {
                spreads.add(firstIdentText(c));
            } else if (c.kind() == SyntaxKind.FIELD_INIT) {
                String field = firstIdentText(c);
                Optional<SyntaxNode> value = firstExprChildOpt(c);
                Ast.Expr v = value.isPresent()
                        ? expr(value.get())
                        : new Ast.Var(field, pos(c));   // shorthand `field` means `field = field`
                inits.add(new Ast.FieldInit(field, v, pos(c)));
            }
        }
        return new Ast.NewData(typeName, inits, spreads, pos(n));
    }

    private Ast.Expr matchExpr(SyntaxNode n) {
        List<SyntaxNode> exprs = exprChildren(n);
        Ast.Expr scrutinee = expr(exprs.get(0));
        List<Ast.Case> cases = new ArrayList<>();
        for (SyntaxNode c : childNodes(n, SyntaxKind.MATCH_CASE)) {
            cases.add(matchCase(c));
        }
        return new Ast.Match(scrutinee, cases, pos(n));
    }

    private Ast.Case matchCase(SyntaxNode n) {
        List<SyntaxElement> es = meaningful(n);
        SourcePos casePos = pos(n);
        int i = 0;
        List<String> caseTypes = new ArrayList<>();
        caseTypes.add(tokenText(es.get(i++)));
        while (i + 1 < es.size() && isToken(es.get(i), SyntaxKind.PIPE) && isToken(es.get(i + 1), SyntaxKind.IDENT)) {
            i++;                                  // |
            caseTypes.add(tokenText(es.get(i++)));
        }
        // Option's positional binding `Some v`: a bare identifier before `{` / `as` / `->`
        String someBinding = null;
        if (i < es.size() && isToken(es.get(i), SyntaxKind.IDENT)) {
            someBinding = tokenText(es.get(i++));
        }
        // field destructuring `{ field [= var], ... }`
        List<String> fieldNames = new ArrayList<>();
        List<String> fieldVars = new ArrayList<>();
        if (i < es.size() && isToken(es.get(i), SyntaxKind.LBRACE)) {
            i++;   // {
            while (i < es.size() && !isToken(es.get(i), SyntaxKind.RBRACE)) {
                String field = tokenText(es.get(i++));
                String var = field;
                if (i < es.size() && isToken(es.get(i), SyntaxKind.ASSIGN)) {
                    i++;
                    var = tokenText(es.get(i++));
                }
                fieldNames.add(field);
                fieldVars.add(var);
                if (i < es.size() && isToken(es.get(i), SyntaxKind.COMMA)) {
                    i++;
                }
            }
            if (i < es.size()) {
                i++;   // }
            }
        }
        // whole-value binding `as x`
        String asBinding = null;
        if (i < es.size() && isToken(es.get(i), SyntaxKind.AS_KW)) {
            i++;
            asBinding = tokenText(es.get(i++));
        }
        boolean isSome = caseTypes.size() == 1 && caseTypes.get(0).equals("Some");
        if (isSome && asBinding != null) {
            throw error(casePos, "parse.option.positional",
                    "Option's wrapped value is bound positionally: write `| Some v`, not `| Some as v`");
        }
        // skip the arrow, then the body is the trailing expression node
        Ast.Expr body = expr(lastExprChild(n));

        String binding = someBinding != null ? someBinding : asBinding;
        if (!fieldNames.isEmpty()) {
            String whole = binding != null ? binding : "$m" + (matchWholeCounter++);
            for (int k = fieldNames.size() - 1; k >= 0; k--) {
                body = new Ast.LetIn(fieldVars.get(k),
                        new Ast.FieldAccess(new Ast.Var(whole, casePos), fieldNames.get(k), casePos),
                        body, casePos);
            }
            binding = whole;
        }
        return new Ast.Case(caseTypes, binding, body, casePos);
    }

    /** A brace block: its {@code let}/{@code require} statements folded into the result expression. */
    private Ast.Expr block(SyntaxNode n) {
        List<SyntaxNode> stmts = new ArrayList<>();
        SyntaxNode result = null;
        for (SyntaxNode c : n.childNodes()) {
            switch (c.kind()) {
                case LET_STMT, TUPLE_DESTRUCTURE, REQUIRE_STMT -> stmts.add(c);
                default -> result = c;   // the trailing result expression
            }
        }
        return foldStatements(stmts, 0, result);
    }

    private Ast.Expr foldStatements(List<SyntaxNode> stmts, int index, SyntaxNode result) {
        if (index == stmts.size()) {
            return expr(result);   // a block always ends in a result expression
        }
        SyntaxNode s = stmts.get(index);
        SourcePos pos = pos(s);
        return switch (s.kind()) {
            case LET_STMT -> new Ast.LetIn(firstIdentText(s), expr(onlyExpr(s)),
                    foldStatements(stmts, index + 1, result), pos);
            case REQUIRE_STMT -> {
                List<SyntaxNode> exprs = exprChildren(s);
                yield new Ast.If(expr(exprs.get(0)), foldStatements(stmts, index + 1, result),
                        expr(exprs.get(1)), pos);
            }
            case TUPLE_DESTRUCTURE -> tupleDestructure(s, foldStatements(stmts, index + 1, result));
            default -> throw error(pos, "parse.expr", "unexpected statement");
        };
    }

    private Ast.Expr tupleDestructure(SyntaxNode s, Ast.Expr rest) {
        List<String> names = new ArrayList<>();
        for (SyntaxToken t : identTokens(s)) {
            names.add(t.text());
        }
        SourcePos pos = pos(s);
        Ast.Expr value = expr(onlyExpr(s));
        if (names.size() == 1) {
            return new Ast.LetIn(names.get(0), value, rest, pos);   // `let (x) = e` is `let x = e`
        }
        String whole = "$t" + (tupleCounter++);
        Ast.Expr body = rest;
        for (int i = names.size() - 1; i >= 0; i--) {
            body = new Ast.LetIn(names.get(i),
                    new Ast.TupleGet(new Ast.Var(whole, pos), i, names.size(), pos), body, pos);
        }
        return new Ast.LetIn(whole, value, body, pos);
    }

    // --- CST navigation helpers ---

    private List<Ast.Expr> exprList(SyntaxNode n) {
        List<Ast.Expr> out = new ArrayList<>();
        for (SyntaxNode c : exprChildren(n)) {
            out.add(expr(c));
        }
        return out;
    }

    /** The direct child nodes that are expressions, in order. */
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

    /** The single expression child of a node that wraps exactly one (a block statement, an invariant,
     * a unary/fn body). */
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

    /** The direct IDENT tokens of a node, in order (skipping keywords, punctuation, and trivia). */
    private List<SyntaxToken> identTokens(SyntaxNode n) {
        List<SyntaxToken> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                out.add(t);
            }
        }
        return out;
    }

    private String firstIdentText(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                return t.text();
            }
        }
        throw new IllegalStateException("no identifier in " + n.kind());
    }

    private SyntaxToken lastIdentToken(SyntaxNode n) {
        SyntaxToken last = null;
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                last = t;
            }
        }
        return last;
    }

    private SyntaxToken operatorToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia() && isBinaryOperator(t.kind())) {
                return t;
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
            if (c.kind() == SyntaxKind.TYPE_REF || c.kind() == SyntaxKind.TUPLE_TYPE) {
                return c;
            }
        }
        throw new IllegalStateException("no type in " + n.kind());
    }

    private String qualifiedNameText(SyntaxNode n) {
        StringBuilder sb = new StringBuilder();
        for (SyntaxToken t : identTokens(n)) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(t.text());
        }
        return sb.toString();
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

    private static boolean isToken(SyntaxElement e, SyntaxKind kind) {
        return e instanceof SyntaxToken t && t.kind() == kind;
    }

    private static String tokenText(SyntaxElement e) {
        return ((SyntaxToken) e).text();
    }

    private SyntaxToken firstMeaningfulToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                return t;
            }
            if (e instanceof SyntaxNode c) {
                SyntaxToken inner = firstMeaningfulTokenOrNull(c);
                if (inner != null) {
                    return inner;
                }
            }
        }
        throw new IllegalStateException("no token under " + n.kind());
    }

    private SyntaxToken firstMeaningfulTokenOrNull(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                return t;
            }
            if (e instanceof SyntaxNode c) {
                SyntaxToken inner = firstMeaningfulTokenOrNull(c);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    private SourcePos pos(SyntaxNode n) {
        return lines.posOf(firstMeaningfulToken(n).start());
    }

    private SourcePos posOf(SyntaxToken t) {
        return lines.posOf(t.start());
    }

    // --- literal decoding ---

    private static String stripDecimalSuffix(String raw) {
        return raw.endsWith("m") ? raw.substring(0, raw.length() - 1) : raw;
    }

    /** Decodes a raw string-literal slice (quotes and escapes included) to its value. */
    private static String stringValue(String raw) {
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
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> e;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private CompileException error(SourcePos pos, String messageKey, String legacyMessage, Object... args) {
        return CompileException.of(
                Diagnostic.of(null, messageKey).title("parse.title").at(pos).args(args).build(),
                legacyMessage);
    }

    /** Whether {@code name} sits in the compiler-shipped {@code souther} namespace (ADR-0028); only
     * those modules may write type variables and {@code intrinsic} bodies. */
    private static boolean isReservedNamespace(String name) {
        return name.equals("souther") || name.startsWith("souther.");
    }
}
