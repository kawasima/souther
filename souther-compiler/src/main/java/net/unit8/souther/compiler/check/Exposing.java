package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;
import net.unit8.souther.compiler.Prelude;
import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves standard-library {@code exposing} imports (spec §stdlib). Souther auto-imports nothing:
 * a module reaches the library either qualified ({@code List.map}) or by importing the names it
 * wants — {@code import List ( map, filter )} — after which it may write them bare. This pass turns
 * each such bare call into its qualified form up front, so the rest of the compiler only ever sees
 * qualified library calls; the {@code import List ( ... )} lines are then dropped from the module.
 *
 * <p>It mirrors Elm's {@code import List exposing (map)}: the qualified access always works, and the
 * import merely lets a name be written without its qualifier. A name exposed from two libraries at
 * once is ambiguous and rejected — qualify it instead.
 */
public final class Exposing {

    private final Map<String, String> exposed;   // bare name → qualified, e.g. "map" → "List.map"

    private Exposing(Map<String, String> exposed) {
        this.exposed = exposed;
    }

    /** Rewrites {@code module}'s exposed bare library calls to qualified form and strips its
     *  {@code import List ( ... )} lines. User-module imports are left untouched. */
    public static Ast.Module rewrite(Ast.Module module) {
        Set<String> ownNames = new HashSet<>();
        for (Ast.FnDef fn : module.fns()) {
            ownNames.add(fn.name());
        }
        for (Ast.BehaviorDef b : module.behaviors()) {
            ownNames.add(b.name());
        }

        Map<String, String> exposed = new HashMap<>();
        List<Ast.Import> keptImports = new ArrayList<>();
        for (Ast.Import imp : module.imports()) {
            if (!Prelude.isQualifier(imp.module())) {
                keptImports.add(imp);   // an ordinary user-module import — resolved elsewhere
                continue;
            }
            for (String name : imp.names()) {
                String qualified = imp.module() + "." + name;
                if (!Prelude.hasQualified(qualified)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.import.notstdfn").title("check.module.title")
                                    .at(imp.pos()).args(name, imp.module()).build(),
                            "`" + name + "` is not a function in the standard library module `"
                                    + imp.module() + "` (spec §stdlib).");
                }
                if (ownNames.contains(name)) {
                    continue;   // the module defines its own `name`; that shadows the import
                }
                String prior = exposed.putIfAbsent(name, qualified);
                if (prior != null && !prior.equals(qualified)) {
                    throw CompileException.of(
                            Diagnostic.of(null, "check.import.ambiguous").title("check.module.title")
                                    .at(imp.pos()).args(name, prior, qualified).build(),
                            "`" + name + "` is exposed from both `" + prior + "` and `" + qualified
                                    + "` — call it qualified instead of importing both (spec §stdlib).");
                }
            }
        }

        // Nothing to qualify and no stdlib import to strip — the common case for a module that
        // reaches the library only through explicit qualifiers, or none at all. Skip the AST rebuild.
        if (exposed.isEmpty() && keptImports.size() == module.imports().size()) {
            return module;
        }

        Exposing pass = new Exposing(exposed);
        List<Ast.Def> defs = new ArrayList<>();
        for (Ast.Def def : module.defs()) {
            defs.add(pass.rewriteDef(def));
        }
        List<Ast.FnDef> fns = new ArrayList<>();
        for (Ast.FnDef fn : module.fns()) {
            fns.add(new Ast.FnDef(fn.name(), fn.params(), fn.declaredReturn(), fn.intrinsicKey(),
                    pass.rw(fn.body()), fn.pos()));
        }
        return new Ast.Module(module.name(), module.exposing(), module.exposedOutputs(),
                keptImports, defs, module.behaviors(), fns, module.pos());
    }

    private Ast.Def rewriteDef(Ast.Def def) {
        if (def instanceof Ast.Data d && d.invariant().isPresent()) {
            return new Ast.Data(d.name(), d.newtype(), d.includes(), d.fields(),
                    Optional.of(rw(d.invariant().get())), d.decoder(), d.encoder(), d.pos());
        }
        return def;
    }

    private Ast.Expr rw(Ast.Expr e) {
        return switch (e) {
            case Ast.Call c -> {
                List<Ast.Expr> args = new ArrayList<>();
                for (Ast.Expr a : c.args()) {
                    args.add(rw(a));
                }
                String fn = c.fn();
                if (fn.indexOf('.') < 0 && exposed.containsKey(fn)) {
                    fn = exposed.get(fn);
                }
                yield new Ast.Call(fn, args, c.pos());
            }
            case Ast.Binary b -> new Ast.Binary(b.op(), rw(b.left()), rw(b.right()), b.pos());
            case Ast.If iff -> new Ast.If(rw(iff.cond()), rw(iff.then()), rw(iff.els()), iff.pos());
            case Ast.Match m -> {
                List<Ast.Case> cases = new ArrayList<>();
                for (Ast.Case cs : m.cases()) {
                    cases.add(new Ast.Case(cs.caseTypes(), cs.binding(), rw(cs.body()), cs.pos()));
                }
                yield new Ast.Match(rw(m.scrutinee()), cases, m.pos());
            }
            case Ast.FieldAccess fa -> new Ast.FieldAccess(rw(fa.target()), fa.field(), fa.pos());
            case Ast.NewData nd -> {
                List<Ast.FieldInit> inits = new ArrayList<>();
                for (Ast.FieldInit fi : nd.inits()) {
                    inits.add(new Ast.FieldInit(fi.name(), rw(fi.value()), fi.pos()));
                }
                yield new Ast.NewData(nd.typeName(), inits, nd.spreads(), nd.pos());
            }
            case Ast.LetIn li -> new Ast.LetIn(li.name(), rw(li.value()), rw(li.body()), li.pos());
            case Ast.Block bl -> new Ast.Block(bl.params(), rw(bl.body()), bl.pos());
            case Ast.ListLit ll -> {
                List<Ast.Expr> elems = new ArrayList<>();
                for (Ast.Expr x : ll.elements()) {
                    elems.add(rw(x));
                }
                yield new Ast.ListLit(elems, ll.pos());
            }
            case Ast.ListComp lc -> {
                List<Ast.Expr> guards = new ArrayList<>();
                for (Ast.Expr g : lc.guards()) {
                    guards.add(rw(g));
                }
                yield new Ast.ListComp(rw(lc.element()), guards, lc.pos());
            }
            case Ast.Neg n -> new Ast.Neg(rw(n.operand()), n.pos());
            case Ast.Tuple tup -> {
                List<Ast.Expr> els = new ArrayList<>();
                for (Ast.Expr x : tup.elements()) {
                    els.add(rw(x));
                }
                yield new Ast.Tuple(els, tup.pos());
            }
            case Ast.TupleGet tg -> new Ast.TupleGet(rw(tg.tuple()), tg.index(), tg.arity(), tg.pos());
            default -> e;   // Var and the literals carry no nested calls
        };
    }
}
