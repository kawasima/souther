package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowers {@code 金額(500)} — a call whose name is a newtype — to the record literal
 * {@code 金額 { value = 500 }} ({@code Hir.NewData}) before type-checking and codegen. Doing it once
 * here means every later stage — the {@code typeOf} dispatch, the tail restriction, CTFE collection,
 * and the backend — sees only {@code NewData}, so the two spellings of a newtype construction get
 * identical treatment and no stage special-cases the call form (ADR-0032; the implicit field is
 * {@code value}, ADR-0014).
 *
 * <p>A module's fn bodies and its invariants are rewritten at different points, because they are
 * settled at different points: an invariant is spread, qualified and inlined before the bodies are,
 * so {@link #rewriteInvariants} runs where that settling ends and {@link #rewrite} runs one question
 * later. Both spellings reach every stage as a {@code NewData} either way, which is the property a
 * stage is written against.
 */
public final class NewtypeDesugar {
    private NewtypeDesugar() {}

    /** Rewrites every {@code Call(newtype, [arg])} in the module's fn bodies to a {@code NewData}. */
    public static Hir.Module rewrite(Hir.Module m, Symbols symbols) {
        List<Hir.FnDef> fns = new ArrayList<>();
        for (Hir.FnDef fn : m.fns()) {
            fns.add(rewriteOf(fn, symbols));
        }
        return m.withFns(fns);
    }

    /**
     * One definition's body, with each newtype construction written in it rewritten to the
     * construction it is.
     *
     * <p>A definition at a time, because an application that wraps no single value is wrong in the
     * body that wrote it and in no other. Rewriting them together answers for a whole module, and
     * one such application would leave every other definition without the form the check and the
     * backend read.
     */
    public static Hir.FnDef rewriteOf(Hir.FnDef fn, Symbols symbols) {
        Hir.FnBody body = switch (fn.body()) {
            case Hir.FnBody.Written w -> new Hir.FnBody.Written(go(w.expr(), symbols));
            case Hir.FnBody.Intrinsic i -> i;
        };
        return fn.withBody(body);
    }

    /**
     * Rewrites every {@code Call(newtype, [arg])} in each declaration's {@code invariant} to a
     * {@code NewData}. Run where the invariants are settled — after the helpers an invariant names
     * are expanded into it — so a construction written in a helper arrives here as the construction
     * it is, and every check over an invariant reads one spelling rather than two.
     */
    public static Hir.Module rewriteInvariants(Hir.Module m, Symbols symbols) {
        List<Hir.Def> defs = new ArrayList<>();
        for (Hir.Def def : m.defs()) {
            defs.add(rewriteInvariantsOf(def, symbols));
        }
        return m.withDefs(defs);
    }

    /**
     * One declaration's invariants, with each newtype construction written in them rewritten to the
     * construction it is.
     *
     * <p>A declaration at a time, because what is wrong with one clause is wrong with the
     * declaration that wrote it and with nothing else. Rewriting them together would answer for a
     * whole module, and one bad application would leave every other declaration without the form
     * every later stage reads.
     */
    public static Hir.Def rewriteInvariantsOf(Hir.Def def, Symbols symbols) {
        if (def instanceof Hir.Data d && !d.invariants().isEmpty()) {
            return new Hir.Data(d.written(), d.declares(), d.newtype(), d.includes(), d.fields(),
                    Hir.mapClauses(d.invariants(), inv -> rewriteInvariant(inv, symbols)),
                    d.pos());
        }
        return def;
    }

    /**
     * One rule written in an invariant, rewritten the same way whether it is the whole clause or one
     * part of it.
     *
     * <p>Named rather than reached through a declaration, because a part of a clause is read
     * alongside the clause it is a part of and the two have to say the same thing about the same
     * text. Sent through the declaration instead, a part would have to be carried on a declaration
     * built to hold it, and would then take whatever else rewriting a declaration comes to mean.
     */
    public static Hir.Expr rewriteInvariant(Hir.Expr invariant, Symbols symbols) {
        return go(invariant, symbols);
    }

    private static Hir.Expr go(Hir.Expr e, Symbols symbols) {
        return switch (e) {
            case Hir.Apply call -> {
                List<Hir.Expr> args = mapExprs(call.args(), symbols);
                // Whether this name is a type or something else was answered when the module's names
                // were resolved. Asking the type namespace again here would read a binding of the
                // same spelling as the type it shadows, and rewrite an application of it into a
                // construction — both of which compile, so the meaning would change in silence.
                TypeSymbol built = call.answered() != null
                        && call.answered().denotes() instanceof ValueName.OfType named
                        ? named.type() : null;
                // The type name is the one the author applied, which a construction is named by. A
                // callee denoting a type is one they wrote, so this holds wherever the branch is
                // taken; asked of the callee it would be whatever a lowering had put there.
                if (built != null && call.applied().name() instanceof WrittenName wrote
                        && symbols.declaredNode(built) instanceof Hir.Data nt
                        && nt.newtype() && args.size() == 1) {
                    // `T(v)` is what the author wrote and a construction is what it means, so the
                    // node that replaces the application stands over the same characters.
                    yield Hir.NewData.fromApply(call, new Hir.Name.Denoting(wrote, built),
                            List.of(new Hir.FieldInit("value", args.get(0), call.pos())));
                }
                yield call.withArgs(args);
            }
            case Hir.NewData nd -> {
                List<Hir.FieldInit> inits = new ArrayList<>();
                for (Hir.FieldInit fi : nd.inits()) {
                    inits.add(fi.withValue(go(fi.value(), symbols)));
                }
                yield nd.with(inits, nd.spreads());
            }
            case Hir.Neg neg -> new Hir.Neg(go(neg.operand(), symbols), neg.pos(), neg.region());
            case Hir.Binary bin ->
                    new Hir.Binary(bin.op(), go(bin.left(), symbols), go(bin.right(), symbols),
                            bin.origin(), bin.pos(), bin.region());
            case Hir.FieldAccess fa -> fa.withTarget(go(fa.target(), symbols));
            case Hir.RowCollection row -> new Hir.RowCollection(mapExprs(row.elements(), symbols),
                    row.origin(), row.pos(), row.region());
            case Hir.ListLit lit -> new Hir.ListLit(mapExprs(lit.elements(), symbols), lit.origin(),
                    lit.pos(), lit.region());
            case Hir.ListComp comp ->
                    new Hir.ListComp(go(comp.element(), symbols), mapExprs(comp.guards(), symbols),
                            comp.origin(), comp.pos(),
                            comp.region());
            case Hir.LetIn li ->
                    new Hir.LetIn(li.binder(), go(li.value(), symbols), li.declaredType(), li.annotated(), li.opens(),
                            go(li.body(), symbols), li.pos(), li.region());
            // A construction written inside a helper is written `T(v)` there too, and reaches an
            // invariant already expanded. What `given` holds is inside the body as well, and is
            // rewritten there.
            case Hir.Expansion ex -> {
                List<Hir.Bound> bound = new ArrayList<>();
                for (Hir.Bound b : ex.bound()) {
                    bound.add(new Hir.Bound(b.binder(), b.declaredType(), go(b.value(), symbols)));
                }
                yield new Hir.Expansion(ex.callee(), ex.application(), bound, ex.given(),
                        ex.declaredReturn(), go(ex.body(), symbols), ex.pos(), ex.region());
            }
            case Hir.If iff ->
                    new Hir.If(go(iff.cond(), symbols), go(iff.then(), symbols), go(iff.els(), symbols),
                            iff.origin(), iff.pos(), iff.region());
            // the attempted construction is written `T(v)` too, so it is a Call until this rewrites it
            case Hir.IfConstructed ic ->
                    new Hir.IfConstructed(go(ic.construct(), symbols), ic.binder(),
                            go(ic.then(), symbols), arms(ic.els(), symbols), ic.origin(), ic.pos(),
                            ic.region());
            case Hir.Block b -> new Hir.Block(b.params(), go(b.body(), symbols), b.rule(), b.pos(),
                    b.region());
            case Hir.Tuple tup -> new Hir.Tuple(mapExprs(tup.elements(), symbols), tup.pos(),
                    tup.region());
            case Hir.TupleGet tg -> new Hir.TupleGet(go(tg.tuple(), symbols), tg.index(), tg.arity(),
                    tg.pos(), tg.region());
            case Hir.Match mt -> {
                List<Hir.Case> cases = new ArrayList<>();
                for (Hir.Case c : mt.cases()) {
                    cases.add(new Hir.Case(c.caseTypes(), c.binding(), go(c.body(), symbols),
                            c.unwrapAsserts(), c.pos()));
                }
                yield new Hir.Match(go(mt.scrutinee(), symbols), cases, mt.origin(), mt.pos(),
                        mt.region());
            }
            default -> e;   // literals, Var — no child expressions to rewrite
        };
    }

    private static List<Hir.Expr> mapExprs(List<Hir.Expr> es, Symbols symbols) {
        List<Hir.Expr> out = new ArrayList<>();
        for (Hir.Expr e : es) {
            out.add(go(e, symbols));
        }
        return out;
    }

    private static List<Hir.ElseArm> arms(List<Hir.ElseArm> arms, Symbols symbols) {
        List<Hir.ElseArm> out = new ArrayList<>();
        for (Hir.ElseArm arm : arms) {
            out.add(arm.with(go(arm.body(), symbols)));
        }
        return out;
    }
}
