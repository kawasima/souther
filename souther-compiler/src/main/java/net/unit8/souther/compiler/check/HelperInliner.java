package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;
import net.unit8.souther.compiler.Prelude;
import net.unit8.souther.compiler.diag.SourcePos;
import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Expands calls to helper {@code fn}s inline (spec 12.5: a named helper is the same as an inline block).
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

    private final Map<String, Ast.FnDef> helpers;   // prelude + module-own, keyed by name (inlining)
    private final Map<String, Ast.FnDef> own;       // the module's own helpers (standalone check)
    private final Set<String> recursive = new HashSet<>();   // own helpers on a call cycle (spec 13.1)
    private int counter = 0;

    private HelperInliner(Map<String, Ast.FnDef> helpers, Map<String, Ast.FnDef> own) {
        this.helpers = helpers;
        this.own = own;
    }

    /** A helper is a fn whose name is not a behavior's; behavior fns are lowered on their own. The
     * auto-imported prelude helpers (spec §reserved-namespace) join the inlining map so a bare
     * {@code not(x)} expands at any call site; a module-own helper of the same name shadows one. */
    public static HelperInliner forModule(Ast.Module module) {
        Set<String> behaviorNames = new HashSet<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            behaviorNames.add(b.name());
        }
        Map<String, Ast.FnDef> own = new HashMap<>();
        for (Ast.FnDef fn : module.fns()) {
            if (!behaviorNames.contains(fn.name())) {
                own.put(fn.name(), fn);
            }
        }
        // prelude helpers are keyed by their qualified name (`List.map`); a module's own helpers by
        // their bare name (`対象明細`). A qualified call resolves to the prelude, a bare call to the
        // module's own — the standard library has no bare names (spec §stdlib).
        Map<String, Ast.FnDef> helpers = new HashMap<>(Prelude.helpers());
        helpers.putAll(own);
        HelperInliner inliner = new HelperInliner(helpers, own);
        inliner.classifyRecursion();
        return inliner;
    }

    /** The module's own helpers that recurse (self or mutual) and so are lowered to methods rather
     * than inlined (spec 13.1). A call to one is left standing by {@link #inline}. */
    public Set<String> recursiveHelpers() {
        return recursive;
    }

    /** The module's own helper fns, keyed by name (for the standalone signature check). The
     * auto-imported prelude helpers are excluded — they are validated once, on their own. */
    public Map<String, Ast.FnDef> helpers() {
        return own;
    }

    /**
     * Inlines helper calls inside every data's {@code invariant}, so a rule named with a {@code let}
     * (e.g. {@code invariant 正の数(value)}) expands to its body before the invariant is type-checked
     * or emitted — the same lowering a behavior body gets (spec 12.5, §invariant-expressions).
     */
    public Ast.Module withInlinedInvariants(Ast.Module m) {
        List<Ast.Def> defs = new ArrayList<>();
        for (Ast.Def def : m.defs()) {
            if (def instanceof Ast.Data d && d.invariant().isPresent()) {
                defs.add(new Ast.Data(d.name(), d.newtype(), d.includes(), d.fields(),
                        java.util.Optional.of(inline(d.invariant().get())),
                        d.decoder(), d.encoder(), d.pos()));
            } else {
                defs.add(def);
            }
        }
        return new Ast.Module(m.name(), m.exposing(), m.exposedOutputs(), m.imports(),
                defs, m.behaviors(), m.fns(), m.examples(), m.fakes(), m.exampleFileTarget(), m.pos());
    }

    /** Looks up a helper by name across the prelude and the module's own helpers, or null if the
     * name is not a helper (a builtin, injected behavior, or unknown). Used to type-check a function
     * passed to a helper's function parameter against the declared type, at the call site. */
    public Ast.FnDef helper(String name) {
        return helpers.get(name);
    }

    /** {@code fold} is the one privileged loop primitive that takes a block (spec 18.4); its block is
     * the first argument and has two parameters (`(acc, x)`, spec §pipe). A bare name passed in its
     * place is sugar for a block that wraps a call. The map is from the combinator name to the block's
     * argument index. The other combinators (map/filter/all/any) are ordinary prelude helpers derived
     * from fold (ADR-0028), so they need no such desugaring — a name reaches their function parameter
     * directly. */
    private static final Map<String, Integer> BLOCK_ARG = Map.of("List.fold", 0);

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
                if (helper == null || recursive.contains(call.fn())) {
                    // builtin, injected behavior, or a recursive helper — a recursive helper is
                    // lowered to a method, so its call stays a Call (spec 13.1); only its args inline.
                    yield new Ast.Call(call.fn(), args, call.pos());
                }
                if (args.size() != helper.params().size()) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.helper.arity").title("check.arity.title")
                                    .at(call.pos(), call.fn().length())
                                    .args(helper.name(), helper.params().size(), args.size()).build(),
                            "helper `let " + helper.name() + "` takes " + helper.params().size()
                                    + " argument(s) but is called with " + args.size());
                }
                int k = counter++;
                Map<String, String> subst = new HashMap<>();
                Set<String> fnParams = new HashSet<>();
                Map<String, Ast.FnDef> scopedLambdas = new HashMap<>();   // lambdas given to fn params
                List<String> letNames = new ArrayList<>();
                List<Ast.Expr> letValues = new ArrayList<>();
                List<Ast.ParamType> letTypes = new ArrayList<>();
                for (int i = 0; i < helper.params().size(); i++) {
                    Ast.FnParam p = helper.params().get(i);
                    Ast.Expr arg = args.get(i);
                    if (p.type() instanceof Ast.FnType) {
                        // a function argument is not a value, so it cannot be bound to a let. A named
                        // function is substituted directly (f(x) becomes inc(x)); a lambda is
                        // registered under a fresh name as a scoped helper, so each application of the
                        // parameter β-reduces to the lambda's body, as a let-bound lambda does (spec 12.5).
                        if (arg instanceof Ast.Var fnName) {
                            subst.put(p.name(), fnName.name());
                            fnParams.add(p.name());
                        } else if (arg instanceof Ast.Block lambda) {
                            String f = "$" + k + "_" + p.name();
                            subst.put(p.name(), f);
                            fnParams.add(p.name());
                            List<Ast.FnParam> lparams = new ArrayList<>();
                            for (String lp : lambda.params()) {
                                lparams.add(new Ast.FnParam(lp, null, lambda.pos()));
                            }
                            // the lambda's body is caller code, so it is not renamed by this helper's
                            // substitution — only the enclosing helper body is.
                            scopedLambdas.put(f, new Ast.FnDef(f, lparams, null, null, lambda.body(), lambda.pos()));
                        } else {
                            throw CompileException.of(
                                    Diagnostic.of(null, "check.fn.mustbenamed").title("check.fn.title")
                                            .at(arg.pos()).args(p.name(), helper.name()).build(),
                                    "the function passed to `" + p.name() + "` of `let " + helper.name()
                                            + "` must be a named function or a lambda");
                        }
                    } else {
                        String f = "$" + k + "_" + p.name();
                        subst.put(p.name(), f);
                        letNames.add(f);
                        letValues.add(arg);
                        // carry the parameter's declared type onto the binding, so a value known to
                        // be a sum (an annotated `s: S`) is not narrowed to the argument's specific
                        // case when the body is re-checked inline — a `match s` inside still sees S.
                        letTypes.add(p.type());
                    }
                }
                scopedLambdas.forEach(helpers::put);
                // a prelude helper's body is stamped with the call site, so errors inside it point at
                // the user's call, not at the shipped source of souther.* (a module-own helper keeps
                // its own positions, which already lie in the user's file).
                SourcePos at = own.containsKey(helper.name()) ? null : call.pos();
                Ast.Expr body = inline(rename(helper.body(), subst, fnParams, at));   // expand nested helpers too
                scopedLambdas.keySet().forEach(helpers::remove);
                // wrap innermost-first so the value parameters bind in declared order
                for (int i = letNames.size() - 1; i >= 0; i--) {
                    body = new Ast.LetIn(letNames.get(i), letValues.get(i), letTypes.get(i), body, call.pos());
                }
                yield body;
            }
            case Ast.FieldAccess fa -> new Ast.FieldAccess(inline(fa.target()), fa.field(), fa.pos());
            case Ast.Binary bin -> new Ast.Binary(bin.op(), inline(bin.left()), inline(bin.right()), bin.pos());
            case Ast.Neg neg -> new Ast.Neg(inline(neg.operand()), neg.pos());
            case Ast.NewData nd -> new Ast.NewData(nd.typeName(), inlineInits(nd.inits()), nd.spreads(), nd.pos());
            case Ast.Match m -> {
                List<Ast.Case> cases = new ArrayList<>();
                for (Ast.Case c : m.cases()) {
                    cases.add(new Ast.Case(c.caseTypes(), c.binding(), inline(c.body()), c.pos()));
                }
                yield new Ast.Match(inline(m.scrutinee()), cases, m.pos());
            }
            case Ast.If iff -> new Ast.If(inline(iff.cond()), inline(iff.then()), inline(iff.els()), iff.pos());
            case Ast.LetIn li when li.value() instanceof Ast.Block lambda -> {
                // a lambda bound to a local: register it as a scoped helper so each application in
                // the body expands inline (β-reduction), exactly like a named helper. Its parameters
                // are untyped, so their types flow in from the arguments at expansion. No runtime
                // closure is built as long as the lambda does not escape.
                if (mentions(lambda.body(), li.name())) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.fn.recursivelambda").title("check.fn.title")
                                    .at(lambda.pos()).args(li.name()).build(),
                            "the lambda bound to `" + li.name() + "` refers to itself; a recursive lambda"
                                    + " would not bottom out when expanded inline");
                }
                List<Ast.FnParam> params = new ArrayList<>();
                for (String p : lambda.params()) {
                    params.add(new Ast.FnParam(p, null, lambda.pos()));
                }
                Ast.FnDef synth = new Ast.FnDef(li.name(), params, null, null, lambda.body(), li.pos());
                Ast.FnDef shadowed = helpers.put(li.name(), synth);
                Ast.Expr body = inline(li.body());
                if (shadowed == null) {
                    helpers.remove(li.name());
                } else {
                    helpers.put(li.name(), shadowed);
                }
                // if the name still occurs, the lambda was used as a value, not just applied — it
                // escapes, which needs a runtime closure. Keep the binding so the "a block is not a
                // value" check reports it.
                yield mentions(body, li.name())
                        ? new Ast.LetIn(li.name(), inline(lambda), li.declaredType(), body, li.pos())
                        : body;
            }
            case Ast.LetIn li -> new Ast.LetIn(li.name(), inline(li.value()), li.declaredType(), inline(li.body()), li.pos());
            case Ast.ListLit lit -> new Ast.ListLit(inlineList(lit.elements()), lit.pos());
            case Ast.Tuple tup -> new Ast.Tuple(inlineList(tup.elements()), tup.pos());
            case Ast.TupleGet tg -> new Ast.TupleGet(inline(tg.tuple()), tg.index(), tg.arity(), tg.pos());
            case Ast.ListComp comp -> new Ast.ListComp(inline(comp.element()), inlineList(comp.guards()), comp.pos());
            case Ast.Block block -> new Ast.Block(block.params(), inline(block.body()), block.pos());
            case Ast.IntLit _ -> e;
            case Ast.DecimalLit _ -> e;
            case Ast.StringLit _ -> e;
            case Ast.BoolLit _ -> e;
            case Ast.Var _ -> e;
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
     * A helper fn passed to {@code fold} by name is sugar for a block that wraps a call:
     * {@code List.fold(step, seed, xs)} with a named {@code step} becomes
     * {@code List.fold(($b0, $b1) -> step($b0, $b1), seed, xs)} (spec 12.5, "名前で直接渡す。同じこと").
     * The generated block has one parameter per helper parameter, so a later arity check against
     * {@code fold} (it wants two) still applies. The block is then expanded inline like any other
     * helper call. Only {@code fold} needs this — map/filter/all/any are helpers whose function
     * parameter the inliner binds directly (see {@link #inline}).
     */
    private Ast.Call desugarNamedBlock(Ast.Call call) {
        Integer idx = BLOCK_ARG.get(call.fn());
        if (idx == null || idx >= call.args().size()
                || !(call.args().get(idx) instanceof Ast.Var v)) {
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
     *
     * <p>{@code fnParams} names the parameters bound to a function argument: those are also rewritten
     * in call position, so an application {@code f(x)} of a function parameter becomes a call to the
     * fn it was passed. A value parameter is never rewritten as a callee, so a parameter that happens
     * to share a builtin's name still calls the builtin.
     *
     * <p>{@code at}, when non-null, is stamped onto every rebuilt node in place of its own position.
     * A prelude helper is expanded with the call site as {@code at}, so a type error inside its body
     * points at the user's call — {@code filter(xs, x -> x * 2)} — not at a line of {@code souther.list}
     * the user never wrote. A module-own helper passes {@code null} and keeps its own positions. The
     * caller's argument expressions, spliced in separately, keep their own positions either way.
     */
    private Ast.Expr rename(Ast.Expr e, Map<String, String> subst, Set<String> fnParams, SourcePos at) {
        return switch (e) {
            case Ast.Var v -> subst.containsKey(v.name()) ? new Ast.Var(subst.get(v.name()), at(at, v.pos())) : e;
            case Ast.FieldAccess fa -> new Ast.FieldAccess(rename(fa.target(), subst, fnParams, at), fa.field(), at(at, fa.pos()));
            case Ast.Call call -> {
                String callee = fnParams.contains(call.fn()) && subst.containsKey(call.fn())
                        ? subst.get(call.fn()) : call.fn();
                yield new Ast.Call(callee, renameList(call.args(), subst, fnParams, at), at(at, call.pos()));
            }
            case Ast.Binary bin -> new Ast.Binary(bin.op(), rename(bin.left(), subst, fnParams, at), rename(bin.right(), subst, fnParams, at), at(at, bin.pos()));
            case Ast.Neg neg -> new Ast.Neg(rename(neg.operand(), subst, fnParams, at), at(at, neg.pos()));
            case Ast.NewData nd -> {
                List<Ast.FieldInit> inits = new ArrayList<>();
                for (Ast.FieldInit i : nd.inits()) {
                    inits.add(new Ast.FieldInit(i.name(), rename(i.value(), subst, fnParams, at), at(at, i.pos())));
                }
                List<String> spreads = new ArrayList<>();
                for (String s : nd.spreads()) {
                    spreads.add(subst.getOrDefault(s, s));   // `..param` copies the renamed binding
                }
                yield new Ast.NewData(nd.typeName(), inits, spreads, at(at, nd.pos()));
            }
            case Ast.Match m -> {
                List<Ast.Case> cases = new ArrayList<>();
                for (Ast.Case c : m.cases()) {
                    Map<String, String> inner = c.binding() == null ? subst : without(subst, c.binding());
                    cases.add(new Ast.Case(c.caseTypes(), c.binding(), rename(c.body(), inner, fnParams, at), at(at, c.pos())));
                }
                yield new Ast.Match(rename(m.scrutinee(), subst, fnParams, at), cases, at(at, m.pos()));
            }
            case Ast.If iff -> new Ast.If(rename(iff.cond(), subst, fnParams, at), rename(iff.then(), subst, fnParams, at), rename(iff.els(), subst, fnParams, at), at(at, iff.pos()));
            case Ast.LetIn li -> {
                Ast.Expr value = rename(li.value(), subst, fnParams, at);
                Ast.Expr body = rename(li.body(), without(subst, li.name()), fnParams, at);
                yield new Ast.LetIn(li.name(), value, li.declaredType(), body, at(at, li.pos()));
            }
            case Ast.ListLit lit -> new Ast.ListLit(renameList(lit.elements(), subst, fnParams, at), at(at, lit.pos()));
            case Ast.Tuple tup -> new Ast.Tuple(renameList(tup.elements(), subst, fnParams, at), at(at, tup.pos()));
            case Ast.TupleGet tg -> new Ast.TupleGet(rename(tg.tuple(), subst, fnParams, at), tg.index(), tg.arity(), at(at, tg.pos()));
            case Ast.ListComp comp -> new Ast.ListComp(rename(comp.element(), subst, fnParams, at), renameList(comp.guards(), subst, fnParams, at), at(at, comp.pos()));
            case Ast.Block block -> {
                // α-rename the block's own parameters to fresh `$`-names. Caller code — a lambda passed
                // to a function parameter — is spliced into this block's scope during inlining; if the
                // block bound a plain name (`acc`/`x`, as the derived combinators do) it would capture a
                // caller variable of the same name. Fresh `$`-names cannot collide with caller code.
                Map<String, String> inner = subst;
                List<String> freshParams = new ArrayList<>();
                for (String p : block.params()) {
                    String fresh = "$b" + (counter++) + "_" + p;
                    freshParams.add(fresh);
                    inner = with(inner, p, fresh);
                }
                yield new Ast.Block(freshParams, rename(block.body(), inner, fnParams, at), at(at, block.pos()));
            }
            case Ast.IntLit _ -> e;
            case Ast.DecimalLit _ -> e;
            case Ast.StringLit _ -> e;
            case Ast.BoolLit _ -> e;
        };
    }

    /** The position to stamp on a rebuilt node: the override {@code at} for a prelude helper, or the
     * node's own position when {@code at} is null (a module-own helper keeps its positions). */
    private static SourcePos at(SourcePos at, SourcePos own) {
        return at != null ? at : own;
    }

    private List<Ast.Expr> renameList(List<Ast.Expr> es, Map<String, String> subst, Set<String> fnParams, SourcePos at) {
        List<Ast.Expr> out = new ArrayList<>();
        for (Ast.Expr e : es) {
            out.add(rename(e, subst, fnParams, at));
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

    /** {@code subst} with {@code name} rebound to {@code fresh} (a copy; the original is untouched). */
    private static Map<String, String> with(Map<String, String> subst, String name, String fresh) {
        Map<String, String> copy = new HashMap<>(subst);
        copy.put(name, fresh);
        return copy;
    }

    /** Records the module's own helpers that lie on a call cycle (self or mutual). A recursive helper
     * is lowered to a method that may call itself, rather than inlined (spec 13.1). A helper is
     * recursive iff it can reach itself through helper calls; every member of a mutual cycle is
     * reached from itself, so all are marked. */
    private void classifyRecursion() {
        for (String name : own.keySet()) {
            if (reaches(name, name, new HashSet<>())) {
                recursive.add(name);
            }
        }
    }

    /** Whether {@code target} is reachable from {@code from} through helper-call edges. Prelude
     * helpers never call a module's own helpers, so a cycle stays within the module's own helpers. */
    private boolean reaches(String from, String target, Set<String> seen) {
        Ast.FnDef h = helpers.get(from);
        if (h == null) {
            return false;
        }
        Set<String> called = new HashSet<>();
        collectHelperCalls(h.body(), called);
        for (String c : called) {
            if (c.equals(target)) {
                return true;
            }
            if (seen.add(c) && reaches(c, target, seen)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code name} occurs as a variable or a call target anywhere in {@code e}. Used to
     * spot a self-referencing lambda and to tell whether a let-bound lambda escapes (is used as a
     * value) after its applications have been expanded away. */
    private static boolean mentions(Ast.Expr e, String name) {
        if (e instanceof Ast.Var v && v.name().equals(name)) {
            return true;
        }
        if (e instanceof Ast.Call c && c.fn().equals(name)) {
            return true;
        }
        boolean[] found = {false};
        forEachChild(e, child -> found[0] |= mentions(child, name));
        return found[0];
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
            case Ast.Neg neg -> f.accept(neg.operand());
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
            case Ast.Tuple tup -> tup.elements().forEach(f);
            case Ast.TupleGet tg -> f.accept(tg.tuple());
            case Ast.ListComp comp -> {
                f.accept(comp.element());
                comp.guards().forEach(f);
            }
            case Ast.LetIn li -> {
                f.accept(li.value());
                f.accept(li.body());
            }
            case Ast.Block block -> f.accept(block.body());
            case Ast.IntLit _ -> { }
            case Ast.DecimalLit _ -> { }
            case Ast.StringLit _ -> { }
            case Ast.BoolLit _ -> { }
            case Ast.Var _ -> { }
        }
    }
}
