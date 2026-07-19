package net.unit8.souther.compiler.core;

import net.unit8.souther.compiler.SourcePos;
import net.unit8.souther.compiler.ast.Ast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The Core IR (ADR-0021): the lowered form of a behavior body the backend emits from.
 *
 * <p>Core is the surface expression AST after the {@link net.unit8.souther.compiler.check.Lower}
 * stage has inlined helpers and desugared surface-only forms. It differs from the AST in what it
 * makes explicit: a construct the backend used to re-detect and shape during emission becomes its
 * own node here, so the backend only emits. The first such node is {@link Fold} — the one
 * privileged list loop (spec 18.4, ADR-0028) — which the AST carries as an ordinary
 * {@code List.fold} call. Later slices add explicit nodes for {@code match} lowering and closure
 * conversion, and drop the corresponding special cases from the emitter.
 *
 * <p>Core is structural, not typed: the backend infers types as it emits, as it does from the AST
 * today. A surface-only node (a list comprehension) never appears — the Lower stage has already
 * rewritten it — so translating one is a compiler bug, not a case to handle.
 */
public sealed interface Core {

    SourcePos pos();

    /**
     * Rebuilds the equivalent surface expression. Two callers need AST rather than Core: the codec
     * emitter, which is AST-level, reaches the shared value emitter through {@code Core.of} then here;
     * and the backend's closure path, which asks the type checker (AST-based) about a runtime-selected
     * function, since Core is untyped. {@code of} and {@code toAst} round-trip (a fold call becomes
     * {@link Fold} and back).
     */
    default Ast.Expr toAst() {
        return switch (this) {
            case Int x -> new Ast.IntLit(x.value(), x.pos());
            case Decimal x -> new Ast.DecimalLit(x.value(), x.pos());
            case Str x -> new Ast.StringLit(x.value(), x.pos());
            case Bool x -> new Ast.BoolLit(x.value(), x.pos());
            case Var x -> new Ast.Var(x.name(), x.pos());
            case Neg n -> new Ast.Neg(n.operand().toAst(), n.pos());
            case FieldAccess fa -> new Ast.FieldAccess(fa.target().toAst(), fa.field(), fa.pos());
            case Binary b -> new Ast.Binary(b.op(), b.left().toAst(), b.right().toAst(), b.pos());
            case Call c -> new Ast.Call(c.fn(), toAstAll(c.args()), c.pos());
            case If iff -> new Ast.If(iff.cond().toAst(), iff.then().toAst(), iff.els().toAst(), iff.pos());
            case LetIn li -> new Ast.LetIn(li.name(), li.value().toAst(), li.body().toAst(), li.pos());
            case Block bl -> new Ast.Block(bl.params(), bl.body().toAst(), bl.pos());
            case ListLit l -> new Ast.ListLit(toAstAll(l.elements()), l.pos());
            case Tuple t -> new Ast.Tuple(toAstAll(t.elements()), t.pos());
            case TupleGet tg -> new Ast.TupleGet(tg.tuple().toAst(), tg.index(), tg.arity(), tg.pos());
            case NewData nd -> {
                List<Ast.FieldInit> inits = new ArrayList<>();
                for (FieldInit i : nd.inits()) {
                    inits.add(new Ast.FieldInit(i.name(), i.value().toAst(), i.pos()));
                }
                yield new Ast.NewData(nd.typeName(), inits, nd.spreads(), nd.pos());
            }
            case Match m -> {
                List<Ast.Case> cases = new ArrayList<>();
                for (Case c : m.cases()) {
                    cases.add(new Ast.Case(c.caseTypes(), c.binding(), c.body().toAst(), c.pos()));
                }
                yield new Ast.Match(m.scrutinee().toAst(), cases, m.pos());
            }
            // fold(step, seed, xs): step block first, list last — the inverse of `of` (F#/Elm order).
            case Fold f -> new Ast.Call(FOLD, List.of(
                    new Ast.Block(f.params(), f.body().toAst(), f.pos()),
                    f.seed().toAst(), f.source().toAst()), f.pos());
        };
    }

    private static List<Ast.Expr> toAstAll(List<Core> cs) {
        List<Ast.Expr> out = new ArrayList<>();
        for (Core c : cs) {
            out.add(c.toAst());
        }
        return out;
    }

    record Int(long value, SourcePos pos) implements Core {}

    record Decimal(BigDecimal value, SourcePos pos) implements Core {}

    record Str(String value, SourcePos pos) implements Core {}

    record Bool(boolean value, SourcePos pos) implements Core {}

    record Var(String name, SourcePos pos) implements Core {}

    record Neg(Core operand, SourcePos pos) implements Core {}

    record FieldAccess(Core target, String field, SourcePos pos) implements Core {}

    record Binary(Ast.BinOp op, Core left, Core right, SourcePos pos) implements Core {}

    /** A call to a builtin, an injected behavior, or an intrinsic (a helper fn is already inlined). */
    record Call(String fn, List<Core> args, SourcePos pos) implements Core {}

    record If(Core cond, Core then, Core els, SourcePos pos) implements Core {}

    record LetIn(String name, Core value, Core body, SourcePos pos) implements Core {}

    /** A second-class block: the operand of a {@link Fold}, or an escaping lambda a {@code let}
     * binds (a closure). Kept as its own node until closure conversion gets an explicit Core form. */
    record Block(List<String> params, Core body, SourcePos pos) implements Core {}

    record ListLit(List<Core> elements, SourcePos pos) implements Core {}

    /** A tuple {@code (e1, e2, ...)} (ADR-0036); the backend emits it as an {@code Object[]}. */
    record Tuple(List<Core> elements, SourcePos pos) implements Core {}

    /** Reads a tuple element by index (a {@code let (x, y) = t} destructure); {@code arity} is the
     * pattern's name count, checked against the tuple's size (ADR-0036). */
    record TupleGet(Core tuple, int index, int arity, SourcePos pos) implements Core {}

    record FieldInit(String name, Core value, SourcePos pos) {}

    record NewData(String typeName, List<FieldInit> inits, List<String> spreads, SourcePos pos)
            implements Core {}

    record Case(List<String> caseTypes, String binding, Core body, SourcePos pos) {}

    record Match(Core scrutinee, List<Case> cases, SourcePos pos) implements Core {}

    /**
     * {@code fold(source, seed, (acc, x) -> body)} — the one privileged list loop (spec 18.4). Made
     * explicit so the backend emits an index loop without re-detecting the {@code List.fold} call and
     * unpacking its block argument.
     */
    record Fold(Core source, Core seed, List<String> params, Core body, SourcePos pos)
            implements Core {}

    /** The qualified name of the fold intrinsic (spec 18.4); its third argument is the loop block. */
    String FOLD = "List.fold";

    /** Translates a lowered behavior body (helpers inlined, surface forms desugared) to Core. */
    static Core of(Ast.Expr e) {
        return switch (e) {
            case Ast.IntLit x -> new Int(x.value(), x.pos());
            case Ast.DecimalLit x -> new Decimal(x.value(), x.pos());
            case Ast.StringLit x -> new Str(x.value(), x.pos());
            case Ast.BoolLit x -> new Bool(x.value(), x.pos());
            case Ast.Var x -> new Var(x.name(), x.pos());
            case Ast.Neg n -> new Neg(of(n.operand()), n.pos());
            case Ast.FieldAccess fa -> new FieldAccess(of(fa.target()), fa.field(), fa.pos());
            case Ast.Binary b -> new Binary(b.op(), of(b.left()), of(b.right()), b.pos());
            case Ast.If iff -> new If(of(iff.cond()), of(iff.then()), of(iff.els()), iff.pos());
            case Ast.LetIn li -> new LetIn(li.name(), of(li.value()), of(li.body()), li.pos());
            case Ast.Block bl -> new Block(bl.params(), of(bl.body()), bl.pos());
            case Ast.ListLit l -> new ListLit(ofAll(l.elements()), l.pos());
            case Ast.Tuple t -> new Tuple(ofAll(t.elements()), t.pos());
            case Ast.TupleGet tg -> new TupleGet(of(tg.tuple()), tg.index(), tg.arity(), tg.pos());
            case Ast.NewData nd -> ofNewData(nd);
            case Ast.Match m -> ofMatch(m);
            case Ast.Call c -> ofCall(c);
            case Ast.ListComp comp -> throw new IllegalStateException(
                    "a list comprehension must be lowered to an `if` before Core translation");
        };
    }

    private static Core ofCall(Ast.Call c) {
        if (c.fn().equals(FOLD) && c.args().size() == 3 && c.args().get(0) instanceof Ast.Block block) {
            // fold(step, seed, xs): the step block is first, the list last (F#/Elm order, spec §pipe)
            return new Fold(of(c.args().get(2)), of(c.args().get(1)), block.params(),
                    of(block.body()), c.pos());
        }
        return new Call(c.fn(), ofAll(c.args()), c.pos());
    }

    private static Core ofNewData(Ast.NewData nd) {
        List<FieldInit> inits = new ArrayList<>();
        for (Ast.FieldInit i : nd.inits()) {
            inits.add(new FieldInit(i.name(), of(i.value()), i.pos()));
        }
        return new NewData(nd.typeName(), inits, nd.spreads(), nd.pos());
    }

    private static Core ofMatch(Ast.Match m) {
        List<Case> cases = new ArrayList<>();
        for (Ast.Case c : m.cases()) {
            cases.add(new Case(c.caseTypes(), c.binding(), of(c.body()), c.pos()));
        }
        return new Match(of(m.scrutinee()), cases, m.pos());
    }

    private static List<Core> ofAll(List<Ast.Expr> es) {
        List<Core> out = new ArrayList<>();
        for (Ast.Expr e : es) {
            out.add(of(e));
        }
        return out;
    }
}
