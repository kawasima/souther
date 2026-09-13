package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.BindingId;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.ValueName;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Lower stage (ADR-0021): rewrites the surface AST toward the form the backend emits, so the
 * backend only emits and never rewrites. It runs before the type checker, whose body check consumes
 * the lowered form (a behavior's permission and {@code depends on} are defined on the inlined body,
 * spec §blocks).
 *
 * <p>It inlines every behavior-implementing {@code fn} body once (spec §blocks) and desugars the
 * body-level constructs that have a plain-AST equivalent — currently the guard-only list
 * comprehension {@code [e | g]}, which becomes {@code if g then [e] else []}. The backend then
 * emits from the lowered module instead of re-running the inliner or shaping these constructs
 * itself, and the type checker's body check consumes the same lowered form. Helper fns are left
 * untouched — they are inlined at their call sites, so a comprehension inside one is desugared once
 * it lands in a behavior body.
 */
public final class Lower {

    private Lower() {}

    /**
     * The two trees this stage answers with: the surface module it settled helper parameter types on,
     * and the lowered module the backend emits from. The type check reads both — the surface one for
     * the declarations, the lowered one for the bodies — so they must be the same settling, which is
     * why they are handed back together rather than settled again downstream.
     */
    public record Lowered(Hir.Module settled, Hir.Module lowered) {}

    /**
     * {@code module} with every helper parameter the author left unwritten carrying the type its body
     * gives it.
     *
     * <p>Settling comes before the expansion because inlining is what carries a parameter's type onto
     * the binding a call becomes (issue #178): a type settled afterwards would never reach it.
     */
    public static Hir.Module settle(CheckSurface surface, Symbols symbols,
                                    PublishedDeclarations published, DeclarationKinds kinds,
                                    Map<ValueName.Behavior, ReqSig> reqSigs) {
        return HelperParams.settle(surface.module(), symbols, published, kinds, reqSigs);
    }

    /**
     * One fn as the backend emits it: its helper calls expanded and its comprehensions desugared.
     *
     * <p>A behavior body and a recursive helper both survive to the backend this way — non-recursive
     * calls expanded, recursive calls left standing (spec §fn-declaration). A recursive helper expands its own
     * body with its parameters hidden from helper resolution ({@code foldFrom}'s {@code step} is a
     * parameter, not a same-named user helper), which is what {@code recursive} says. A helper that is
     * neither is fully inlined at its call sites and never emitted, so nothing asks for it here.
     */
    public static Expansion<Hir.FnDef> body(Hir.FnDef fn, HelperInliner inliner, boolean recursive) {
        return body(fn, inliner, recursive, Set.of());
    }

    /**
     * The same, for a behavior's implementation, told what its behavior declares in {@code depends
     * on} — the names that arrive as the {@code let}'s trailing parameters (spec §depends-on). A
     * helper has none, and neither has a recursive helper's own body.
     */
    public static Expansion<Hir.FnDef> body(Hir.FnDef fn, HelperInliner inliner, boolean recursive,
                                            Set<String> dependencies) {
        Hir.Expr expanded = recursive
                ? inliner.inlineRecursiveBody(fn)
                : inliner.inline(fn.writtenBody(), dependencies(fn, dependencies), inliner.bodyOf(fn.name()));
        // What this expansion could not remove travels with what it produced. The inliner was made
        // for this body, so what it left standing is this body's and nothing else's.
        return new Expansion<>(fn.withBody(new Hir.FnBody.Written(desugar(expanded))),
                inliner.leftStanding(), inliner.provenance(), inliner.suppliedRules());
    }

    /** Which bindings the {@code depends on} names are: the trailing parameters that carry them. A
     * name in the body is one of them only when it was answered with that binding — a binding in force
     * wins over the declaration it shadows (spec §fn-rules), so the spelling alone does not say. */
    private static Set<BindingId> dependencies(Hir.FnDef fn, Set<String> dependencies) {
        Set<BindingId> bindings = new HashSet<>();
        for (Hir.FnParam p : fn.params()) {
            if (dependencies.contains(p.name())) {
                bindings.add(p.binder().id());
            }
        }
        return bindings;
    }

    /**
     * {@code module} with {@code fns} as its declarations and {@code takenOn} as what it emits
     * without having declared — the tree the backend emits from. The two stay apart down here: both
     * become methods, and only the first is this module's to answer for.
     *
     * <p>The declarations are carried across untouched. This used to desugar every data's invariant
     * as well, for a backend that elaborated the clause on its way to emitting it; the clause a
     * construction runs is the checker's now (issue #1080), and a tree of clauses nothing runs is a
     * second executable-looking form for the next reader to reach for.
     */
    public static Hir.Module lowered(Hir.Module module, List<Hir.FnDef> fns,
                                     List<Hir.FnDef> takenOn) {
        return module.withFns(fns).withTakenOn(takenOn);
    }

    /** Desugars one expression the way a body is desugared, for the paths that hold a single
     * expression rather than a module: the codec emitters, whose decoders and encoders are still
     * AST-level, run their expressions through this before they are typed and emitted. */
    public static Hir.Expr desugarExpr(Hir.Expr e) {
        return desugar(e);
    }

    /** Post-order rewrite: desugar the children first, then the node itself if it is a comprehension. */
    private static Hir.Expr desugar(Hir.Expr e) {
        Hir.Expr mapped = Hir.mapChildren(e, Lower::desugar, s -> s);
        return mapped instanceof Hir.ListComp comp ? listCompToIf(comp) : mapped;
    }

    /**
     * {@code [element | g1, g2]} is {@code if g1 then (if g2 then [element] else []) else []}: the
     * element is included exactly when every guard holds, giving a 0-or-1 element list (spec §stdlib-list).
     * The guards nest rather than joining with {@code &&} so a later guard is not evaluated once an
     * earlier one is false — {@code &&} evaluates both sides (spec §stdlib-bool), which would run (and could
     * abort in) a guard the original comprehension short-circuited past.
     */
    private static Hir.Expr listCompToIf(Hir.ListComp comp) {
        // The `if` stands where the comprehension was written; the two lists are this lowering's
        // own — no run of characters in the file spells either of them.
        Hir.Expr result = new Hir.ListLit(List.of(comp.element()),
                SourceConstructOrigin.unwritten(), comp.pos(), null);
        List<Hir.Expr> guards = comp.guards();
        for (int i = guards.size() - 1; i >= 0; i--) {
            // The fork is the comprehension's answer rather than one minted here, so a
            // comprehension a helper holds answers the same in every body that expanded it, and the
            // reading that runs before this lowering names the fork this builds.
            result = new Hir.If(guards.get(i), result,
                    new Hir.ListLit(List.of(), SourceConstructOrigin.unwritten(), comp.pos(), null),
                    comp.forkOfGuard(i), comp.pos(), comp.region());
        }
        return result;
    }
}
