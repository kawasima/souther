package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Lower stage (ADR-0021): rewrites the surface AST toward the form the backend emits, so the
 * backend only emits and never rewrites. It runs before the type checker, whose body check consumes
 * the lowered form (a behavior's permission and {@code requires} are defined on the inlined body,
 * spec 12.5).
 *
 * <p>It inlines every behavior-implementing {@code fn} body once (spec 12.5) and desugars the
 * body-level constructs that have a plain-AST equivalent — currently the guard-only list
 * comprehension {@code [e | g]}, which becomes {@code if g then [e] else []}. The backend then
 * emits from the lowered module instead of re-running the inliner or shaping these constructs
 * itself, and the type checker's body check consumes the same lowered form. Helper fns are left
 * untouched — they are inlined at their call sites, so a comprehension inside one is desugared once
 * it lands in a behavior body. Later slices grow this stage into a typed Core IR and move the
 * remaining backend-side rewrites (fold shaping, {@code match} lowering, closure conversion) here.
 */
public final class Lower {

    private Lower() {}

    /** Returns {@code module} with each behavior-implementing fn body inlined and every list
     * comprehension (in a body or a data invariant) desugared to an {@code if}. */
    public static Ast.Module run(Ast.Module module) {
        HelperInliner inliner = HelperInliner.forModule(module);
        Set<String> behaviorNames = new HashSet<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            behaviorNames.add(b.name());
        }
        Set<String> recursive = inliner.recursiveHelpers();
        List<Ast.FnDef> fns = new ArrayList<>();
        for (Ast.FnDef fn : module.fns()) {
            // A behavior body and a recursive helper both survive to the backend with their body
            // inlined (non-recursive calls expanded, recursive calls left standing, spec 13.1). A
            // non-recursive helper is fully inlined at its call sites and never emitted — drop it.
            if (behaviorNames.contains(fn.name()) || recursive.contains(fn.name())) {
                // a recursive helper expands its own body with its parameters hidden from helper
                // resolution (foldFrom's `step` is a parameter, not a same-named user helper).
                Ast.Expr expanded = recursive.contains(fn.name())
                        ? inliner.inlineRecursiveBody(fn)
                        : inliner.inline(fn.body());
                fns.add(new Ast.FnDef(fn.name(), fn.params(), fn.declaredReturn(), fn.intrinsicKey(),
                        desugar(expanded), fn.pos()));
            }
        }
        List<Ast.Def> defs = new ArrayList<>();
        for (Ast.Def def : module.defs()) {
            if (def instanceof Ast.Data d && d.invariant().isPresent()) {
                defs.add(new Ast.Data(d.name(), d.newtype(), d.includes(), d.fields(),
                        Optional.of(desugar(d.invariant().get())), d.decoder(), d.encoder(), d.pos()));
            } else {
                defs.add(def);
            }
        }
        return new Ast.Module(module.name(), module.exposing(), module.exposedOutputs(),
                module.imports(), defs, module.behaviors(), fns,
                module.examples(), module.fakes(), module.exampleFileTarget(), module.pos());
    }

    /** Post-order rewrite: desugar the children first, then the node itself if it is a comprehension. */
    private static Ast.Expr desugar(Ast.Expr e) {
        Ast.Expr mapped = Ast.mapChildren(e, Lower::desugar);
        return mapped instanceof Ast.ListComp comp ? listCompToIf(comp) : mapped;
    }

    /**
     * {@code [element | g1, g2]} is {@code if g1 then (if g2 then [element] else []) else []}: the
     * element is included exactly when every guard holds, giving a 0-or-1 element list (spec 18.4).
     * The guards nest rather than joining with {@code &&} so a later guard is not evaluated once an
     * earlier one is false — {@code &&} evaluates both sides (spec 18.1), which would run (and could
     * abort in) a guard the original comprehension short-circuited past.
     */
    private static Ast.Expr listCompToIf(Ast.ListComp comp) {
        Ast.Expr result = new Ast.ListLit(List.of(comp.element()), comp.pos());
        List<Ast.Expr> guards = comp.guards();
        for (int i = guards.size() - 1; i >= 0; i--) {
            result = new Ast.If(guards.get(i), result, new Ast.ListLit(List.of(), comp.pos()), comp.pos());
        }
        return result;
    }
}
