package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;
import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lowers {@code 金額(500)} — a call whose name is a newtype — to the record literal
 * {@code 金額 { value = 500 }} ({@code Ast.NewData}) before type-checking and codegen. Doing it once
 * here means every later stage — the {@code typeOf} dispatch, the tail restriction, CTFE collection,
 * and the backend — sees only {@code NewData}, so the two spellings of a newtype construction get
 * identical treatment and no stage special-cases the call form (ADR-0032; the implicit field is
 * {@code value}, ADR-0014).
 */
public final class NewtypeDesugar {
    private NewtypeDesugar() {}

    /** Rewrites every {@code Call(newtype, [arg])} in the module's fn bodies to a {@code NewData}. */
    public static Ast.Module rewrite(Ast.Module m, Map<String, Ast.Def> symbols) {
        List<Ast.FnDef> fns = new ArrayList<>();
        for (Ast.FnDef fn : m.fns()) {
            Ast.Expr body = fn.body() == null ? null : go(fn.body(), symbols);
            fns.add(new Ast.FnDef(fn.name(), fn.params(), fn.declaredReturn(), fn.intrinsicKey(),
                    body, fn.pos()));
        }
        return new Ast.Module(m.name(), m.exposing(), m.exposedOutputs(), m.imports(),
                m.defs(), m.behaviors(), fns, m.examples(), m.fakes(), m.exampleFileTarget(), m.pos());
    }

    private static Ast.Expr go(Ast.Expr e, Map<String, Ast.Def> symbols) {
        return switch (e) {
            case Ast.Call call -> {
                List<Ast.Expr> args = mapExprs(call.args(), symbols);
                if (symbols.get(call.fn()) instanceof Ast.Data nt && nt.newtype()) {
                    if (args.size() != 1) {
                        throw CompileException.of(
                                Diagnostic.of(null, "check.newtype.arity").title("check.arity.title")
                                        .at(call.pos(), call.fn().length()).args(call.fn(), args.size())
                                        .build(),
                                "`" + call.fn() + "` wraps one value, but is applied to " + args.size()
                                        + " argument(s)");
                    }
                    yield new Ast.NewData(call.fn(),
                            List.of(new Ast.FieldInit("value", args.get(0), call.pos())),
                            List.of(), call.pos());
                }
                yield new Ast.Call(call.fn(), args, call.pos());
            }
            case Ast.NewData nd -> {
                List<Ast.FieldInit> inits = new ArrayList<>();
                for (Ast.FieldInit fi : nd.inits()) {
                    inits.add(new Ast.FieldInit(fi.name(), go(fi.value(), symbols), fi.pos()));
                }
                yield new Ast.NewData(nd.typeName(), inits, nd.spreads(), nd.pos());
            }
            case Ast.Neg neg -> new Ast.Neg(go(neg.operand(), symbols), neg.pos());
            case Ast.Binary bin ->
                    new Ast.Binary(bin.op(), go(bin.left(), symbols), go(bin.right(), symbols), bin.pos());
            case Ast.FieldAccess fa -> new Ast.FieldAccess(go(fa.target(), symbols), fa.field(), fa.pos());
            case Ast.ListLit lit -> new Ast.ListLit(mapExprs(lit.elements(), symbols), lit.pos());
            case Ast.ListComp comp ->
                    new Ast.ListComp(go(comp.element(), symbols), mapExprs(comp.guards(), symbols), comp.pos());
            case Ast.LetIn li ->
                    new Ast.LetIn(li.name(), go(li.value(), symbols), li.declaredType(), go(li.body(), symbols), li.pos());
            case Ast.If iff ->
                    new Ast.If(go(iff.cond(), symbols), go(iff.then(), symbols), go(iff.els(), symbols), iff.pos());
            case Ast.Block b -> new Ast.Block(b.params(), go(b.body(), symbols), b.pos());
            case Ast.Tuple tup -> new Ast.Tuple(mapExprs(tup.elements(), symbols), tup.pos());
            case Ast.TupleGet tg -> new Ast.TupleGet(go(tg.tuple(), symbols), tg.index(), tg.arity(), tg.pos());
            case Ast.Match mt -> {
                List<Ast.Case> cases = new ArrayList<>();
                for (Ast.Case c : mt.cases()) {
                    cases.add(new Ast.Case(c.caseTypes(), c.binding(), go(c.body(), symbols), c.pos()));
                }
                yield new Ast.Match(go(mt.scrutinee(), symbols), cases, mt.pos());
            }
            default -> e;   // literals, Var — no child expressions to rewrite
        };
    }

    private static List<Ast.Expr> mapExprs(List<Ast.Expr> es, Map<String, Ast.Def> symbols) {
        List<Ast.Expr> out = new ArrayList<>();
        for (Ast.Expr e : es) {
            out.add(go(e, symbols));
        }
        return out;
    }
}
