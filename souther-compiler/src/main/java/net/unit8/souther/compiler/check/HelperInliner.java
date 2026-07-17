package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Expands calls to helper {@code fn}s inline (spec 12.5: a named fn is the same as an inline block).
 *
 * <p>A helper fn is a {@code fn} with no matching behavior — it writes its own parameter types
 * (spec 13.1) and, unlike a behavior fn, is not lowered to a class of its own. Instead every call
 * {@code h(a, b)} is rewritten to {@code let $k_p1 = a in let $k_p2 = b in <body>}, with the
 * helper's parameters α-renamed to fresh {@code $}-prefixed names so they cannot capture a caller
 * local (a source identifier never starts with {@code $}). Because the body is spliced into the
 * caller, the caller's construction-permission check, {@code requires} inference, and codegen all
 * see the helper's constructions and injected calls directly — exactly as if the code had been
 * written inline (spec 12.5). Helpers must not recurse (directly or indirectly), which keeps the
 * expansion finite; a cycle is rejected up front.
 */
public final class HelperInliner {

    private final Map<String, Ast.FnDef> helpers;
    private int counter = 0;

    private HelperInliner(Map<String, Ast.FnDef> helpers) {
        this.helpers = helpers;
    }

    /** A helper is a fn whose name is not a behavior's; behavior fns are lowered on their own. */
    public static HelperInliner forModule(Ast.Module module) {
        Set<String> behaviorNames = new HashSet<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            behaviorNames.add(b.name());
        }
        Map<String, Ast.FnDef> helpers = new HashMap<>();
        for (Ast.FnDef fn : module.fns()) {
            if (!behaviorNames.contains(fn.name())) {
                helpers.put(fn.name(), fn);
            }
        }
        HelperInliner inliner = new HelperInliner(helpers);
        inliner.rejectRecursion();
        return inliner;
    }

    /** The helper fns of the module, keyed by name (for the standalone signature check). */
    public Map<String, Ast.FnDef> helpers() {
        return helpers;
    }

    /** The list combinators that take a block, and how many parameters that block has (spec 18.4). */
    private static final Map<String, Integer> BLOCK_ARITY =
            Map.of("map", 1, "filter", 1, "all", 1, "any", 1, "fold", 2);

    /** Rewrites every helper call in {@code e} to its inlined body. */
    public Ast.Expr inline(Ast.Expr e) {
        return switch (e) {
            case Ast.Call rawCall -> {
                Ast.Call call = desugarNamedBlock(rawCall);
                List<Ast.Expr> args = new ArrayList<>();
                for (Ast.Expr a : call.args()) {
                    args.add(inline(a));
                }
                Ast.FnDef helper = helpers.get(call.fn());
                if (helper == null) {
                    yield new Ast.Call(call.fn(), args, call.pos());   // builtin or injected behavior
                }
                if (args.size() != helper.params().size()) {
                    throw new CompileException(call.pos(), "helper `fn " + helper.name() + "` takes "
                            + helper.params().size() + " argument(s) but is called with " + args.size());
                }
                int k = counter++;
                Map<String, String> subst = new HashMap<>();
                List<String> fresh = new ArrayList<>();
                for (Ast.FnParam p : helper.params()) {
                    String f = "$" + k + "_" + p.name();
                    subst.put(p.name(), f);
                    fresh.add(f);
                }
                Ast.Expr body = inline(rename(helper.body(), subst));   // expand nested helpers too
                // wrap innermost-first so the parameters bind in declared order
                for (int i = fresh.size() - 1; i >= 0; i--) {
                    body = new Ast.LetIn(fresh.get(i), args.get(i), body, call.pos());
                }
                yield body;
            }
            case Ast.FieldAccess fa -> new Ast.FieldAccess(inline(fa.target()), fa.field(), fa.pos());
            case Ast.Binary bin -> new Ast.Binary(bin.op(), inline(bin.left()), inline(bin.right()), bin.pos());
            case Ast.Not not -> new Ast.Not(inline(not.operand()), not.pos());
            case Ast.NewData nd -> new Ast.NewData(nd.typeName(), inlineInits(nd.inits()), nd.spreads(), nd.pos());
            case Ast.Match m -> {
                List<Ast.Case> cases = new ArrayList<>();
                for (Ast.Case c : m.cases()) {
                    cases.add(new Ast.Case(c.armType(), c.binding(), inline(c.body()), c.pos()));
                }
                yield new Ast.Match(inline(m.scrutinee()), cases, m.pos());
            }
            case Ast.If iff -> new Ast.If(inline(iff.cond()), inline(iff.then()), inline(iff.els()), iff.pos());
            case Ast.LetIn li -> new Ast.LetIn(li.name(), inline(li.value()), inline(li.body()), li.pos());
            case Ast.ListLit lit -> new Ast.ListLit(inlineList(lit.elements()), lit.pos());
            case Ast.ListComp comp -> new Ast.ListComp(inline(comp.element()), inlineList(comp.guards()), comp.pos());
            case Ast.Block block -> new Ast.Block(block.params(), inline(block.body()), block.pos());
            case Ast.IntLit ignored -> e;
            case Ast.StringLit ignored -> e;
            case Ast.BoolLit ignored -> e;
            case Ast.Var ignored -> e;
        };
    }

    private List<Ast.Expr> inlineList(List<Ast.Expr> es) {
        List<Ast.Expr> out = new ArrayList<>();
        for (Ast.Expr e : es) {
            out.add(inline(e));
        }
        return out;
    }

    private List<Ast.FieldInit> inlineInits(List<Ast.FieldInit> inits) {
        List<Ast.FieldInit> out = new ArrayList<>();
        for (Ast.FieldInit i : inits) {
            out.add(new Ast.FieldInit(i.name(), inline(i.value()), i.pos()));
        }
        return out;
    }

    /**
     * A helper fn passed to a list combinator by name is sugar for a block that wraps a call:
     * {@code all(xs, positive)} becomes {@code all(xs, $b0 => positive($b0))} (spec 12.5, "名前で
     * 直接渡す。同じこと"). The generated block has one parameter per helper parameter, so a later
     * arity check against the combinator (e.g. {@code fold} wants two) still applies. The block is
     * then expanded inline like any other helper call.
     */
    private Ast.Call desugarNamedBlock(Ast.Call call) {
        Integer arity = BLOCK_ARITY.get(call.fn());
        if (arity == null) {
            return call;
        }
        int idx = call.args().size() - 1;   // the block is the last argument of every combinator
        if (idx < 0 || !(call.args().get(idx) instanceof Ast.Var v)) {
            return call;
        }
        Ast.FnDef helper = helpers.get(v.name());
        if (helper == null) {
            return call;   // a bare name that is not a helper is left for the type checker to report
        }
        int k = counter++;
        List<String> params = new ArrayList<>();
        List<Ast.Expr> callArgs = new ArrayList<>();
        for (int i = 0; i < helper.params().size(); i++) {
            String p = "$b" + k + "_" + i;
            params.add(p);
            callArgs.add(new Ast.Var(p, v.pos()));
        }
        Ast.Block block = new Ast.Block(params, new Ast.Call(v.name(), callArgs, v.pos()), v.pos());
        List<Ast.Expr> args = new ArrayList<>(call.args());
        args.set(idx, block);
        return new Ast.Call(call.fn(), args, call.pos());
    }

    /**
     * Capture-avoiding renaming of the helper's free parameter references. A binder that shadows a
     * parameter name (a {@code let}, {@code match} binding, or block parameter of the same name)
     * drops that name from the substitution for its scope, so an inner rebinding is left untouched.
     */
    private static Ast.Expr rename(Ast.Expr e, Map<String, String> subst) {
        return switch (e) {
            case Ast.Var v -> subst.containsKey(v.name()) ? new Ast.Var(subst.get(v.name()), v.pos()) : e;
            case Ast.FieldAccess fa -> new Ast.FieldAccess(rename(fa.target(), subst), fa.field(), fa.pos());
            case Ast.Call call -> new Ast.Call(call.fn(), renameList(call.args(), subst), call.pos());
            case Ast.Binary bin -> new Ast.Binary(bin.op(), rename(bin.left(), subst), rename(bin.right(), subst), bin.pos());
            case Ast.Not not -> new Ast.Not(rename(not.operand(), subst), not.pos());
            case Ast.NewData nd -> {
                List<Ast.FieldInit> inits = new ArrayList<>();
                for (Ast.FieldInit i : nd.inits()) {
                    inits.add(new Ast.FieldInit(i.name(), rename(i.value(), subst), i.pos()));
                }
                List<String> spreads = new ArrayList<>();
                for (String s : nd.spreads()) {
                    spreads.add(subst.getOrDefault(s, s));   // `..param` copies the renamed binding
                }
                yield new Ast.NewData(nd.typeName(), inits, spreads, nd.pos());
            }
            case Ast.Match m -> {
                List<Ast.Case> cases = new ArrayList<>();
                for (Ast.Case c : m.cases()) {
                    Map<String, String> inner = c.binding() == null ? subst : without(subst, c.binding());
                    cases.add(new Ast.Case(c.armType(), c.binding(), rename(c.body(), inner), c.pos()));
                }
                yield new Ast.Match(rename(m.scrutinee(), subst), cases, m.pos());
            }
            case Ast.If iff -> new Ast.If(rename(iff.cond(), subst), rename(iff.then(), subst), rename(iff.els(), subst), iff.pos());
            case Ast.LetIn li -> {
                Ast.Expr value = rename(li.value(), subst);
                Ast.Expr body = rename(li.body(), without(subst, li.name()));
                yield new Ast.LetIn(li.name(), value, body, li.pos());
            }
            case Ast.ListLit lit -> new Ast.ListLit(renameList(lit.elements(), subst), lit.pos());
            case Ast.ListComp comp -> new Ast.ListComp(rename(comp.element(), subst), renameList(comp.guards(), subst), comp.pos());
            case Ast.Block block -> {
                Map<String, String> inner = subst;
                for (String p : block.params()) {
                    inner = without(inner, p);
                }
                yield new Ast.Block(block.params(), rename(block.body(), inner), block.pos());
            }
            case Ast.IntLit ignored -> e;
            case Ast.StringLit ignored -> e;
            case Ast.BoolLit ignored -> e;
        };
    }

    private static List<Ast.Expr> renameList(List<Ast.Expr> es, Map<String, String> subst) {
        List<Ast.Expr> out = new ArrayList<>();
        for (Ast.Expr e : es) {
            out.add(rename(e, subst));
        }
        return out;
    }

    private static Map<String, String> without(Map<String, String> subst, String name) {
        if (!subst.containsKey(name)) {
            return subst;
        }
        Map<String, String> copy = new HashMap<>(subst);
        copy.remove(name);
        return copy;
    }

    /** Rejects a helper that calls itself directly or through other helpers (spec 13.1). */
    private void rejectRecursion() {
        for (Ast.FnDef h : helpers.values()) {
            visit(h.name(), h, new LinkedHashSet<>());
        }
    }

    private void visit(String root, Ast.FnDef h, Set<String> onPath) {
        if (!onPath.add(h.name())) {
            throw new CompileException(h.pos(), "helper `fn " + h.name()
                    + "` is recursive (directly or through other helpers), which is not allowed: "
                    + "a helper is expanded inline, so it must bottom out (spec 13.1)");
        }
        Set<String> called = new HashSet<>();
        collectHelperCalls(h.body(), called);
        for (String c : called) {
            Ast.FnDef callee = helpers.get(c);
            if (callee != null) {
                visit(root, callee, onPath);
            }
        }
        onPath.remove(h.name());
    }

    private void collectHelperCalls(Ast.Expr e, Set<String> out) {
        if (e instanceof Ast.Call call && helpers.containsKey(call.fn())) {
            out.add(call.fn());
        }
        forEachChild(e, c -> collectHelperCalls(c, out));
    }

    private static void forEachChild(Ast.Expr e, java.util.function.Consumer<Ast.Expr> f) {
        switch (e) {
            case Ast.NewData nd -> nd.inits().forEach(i -> f.accept(i.value()));
            case Ast.FieldAccess fa -> f.accept(fa.target());
            case Ast.Call call -> call.args().forEach(f);
            case Ast.Binary bin -> {
                f.accept(bin.left());
                f.accept(bin.right());
            }
            case Ast.Not not -> f.accept(not.operand());
            case Ast.Match m -> {
                f.accept(m.scrutinee());
                m.cases().forEach(c -> f.accept(c.body()));
            }
            case Ast.If iff -> {
                f.accept(iff.cond());
                f.accept(iff.then());
                f.accept(iff.els());
            }
            case Ast.ListLit lit -> lit.elements().forEach(f);
            case Ast.ListComp comp -> {
                f.accept(comp.element());
                comp.guards().forEach(f);
            }
            case Ast.LetIn li -> {
                f.accept(li.value());
                f.accept(li.body());
            }
            case Ast.Block block -> f.accept(block.body());
            case Ast.IntLit ignored -> { }
            case Ast.StringLit ignored -> { }
            case Ast.BoolLit ignored -> { }
            case Ast.Var ignored -> { }
        }
    }
}
