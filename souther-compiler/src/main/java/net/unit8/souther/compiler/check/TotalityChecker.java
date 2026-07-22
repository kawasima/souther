package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that recursion is total by default (spec §fn-declaration): a module-own recursive helper
 * that is not {@code partial} must be <em>structurally recursive</em> — every recursive call passes,
 * in some fixed argument position, a value that is a strictly smaller part of the corresponding
 * parameter (a sub-term obtained by {@code match} on a field or a case, or an element handed to a
 * list combinator's closure). Self-referential data is finite by construction (the inhabitability
 * check), so descending on a smaller part bottoms out — the helper terminates, and its examples can
 * be evaluated at compile time.
 *
 * <p>A {@code partial} helper opts out (it is not checked and may not terminate). The stdlib's
 * {@code List.foldFrom} (index recursion) is trusted total and exempt — only bare-named module-own
 * helpers are checked. Numeric ({@code n - 1}) and index ({@code i + 1}) recursion are not structural
 * (Souther has no inductive {@code Nat}) and must be {@code partial}. Mutual recursion is not yet
 * checked; a non-{@code partial} mutually-recursive helper is rejected conservatively.
 */
final class TotalityChecker {

    private TotalityChecker() {}

    /** Checks every non-{@code partial}, module-own recursive helper for structural recursion. */
    static void check(HelperInliner inliner) {
        Map<String, Ast.FnDef> own = inliner.helpers();
        Map<String, Set<String>> ownEdges = ownCallGraph(own);
        for (String name : inliner.recursiveHelpers()) {
            Ast.FnDef h = own.get(name);
            // A qualified name (`List.foldFrom`) is a prelude recursive helper injected into the module
            // (trusted total); only bare-named user helpers are checked. `partial` opts out.
            if (h == null || h.partial() || name.indexOf('.') >= 0) {
                continue;
            }
            Set<String> cycle = cycleMembers(name, ownEdges);
            if (cycle.size() > 1) {
                // mutual recursion: not checked yet — reject conservatively so it can never hang
                throw error(h, name, "check.totality.mutual",
                        "recursive helper `let " + name + "` is mutually recursive; totality of mutual"
                                + " recursion is not checked yet — mark it `partial` to opt out");
            }
            checkSelfRecursive(h, name);
        }
    }

    /** A self-recursive helper is structural iff there is a single argument position that strictly
     * decreases in every recursive call. */
    private static void checkSelfRecursive(Ast.FnDef h, String name) {
        Set<String> paramNames = new HashSet<>();
        for (Ast.FnParam p : h.params()) {
            paramNames.add(p.name());
        }
        List<RecCall> calls = new ArrayList<>();
        walk(h.body(), name, paramNames, Map.of(), calls);
        if (calls.isEmpty()) {
            return;   // no self-call reaches here (defensive; a recursive helper has at least one)
        }
        for (int i = 0; i < h.params().size(); i++) {
            String paramName = h.params().get(i).name();
            boolean decreasesEverywhere = true;
            for (RecCall c : calls) {
                if (i >= c.call.args().size()
                        || !strictSmaller(c.call.args().get(i), c.lt, paramNames).contains(paramName)) {
                    decreasesEverywhere = false;
                    break;
                }
            }
            if (decreasesEverywhere) {
                return;   // structural — this position bottoms out in every call
            }
        }
        RecCall first = calls.get(0);
        throw error(first.call, name, "check.totality.notstructural",
                "recursive helper `let " + name + "` is not structurally recursive: `" + name
                        + "(...)` passes no argument that is a strictly smaller part of a parameter."
                        + " Recurse on a part obtained by `match` (a field or a case), count with"
                        + " `fold`, or mark the helper `partial`");
    }

    /** A recorded self-call, with the smaller-than-parameter relation in scope where it appears. */
    private record RecCall(Ast.Call call, Map<String, Set<String>> lt) {}

    /**
     * A standard-library list combinator that hands the elements of its list argument to a closure:
     * the closure is argument {@code closureArg}, its element is closure parameter {@code elementParam},
     * and the list is argument {@code listArg}. Recursing on that element inside the closure is
     * structural when the list is (part of) a parameter — the common way to walk an {@code List<T>}
     * of children (spec §stdlib-list). An unlisted combinator is conservatively treated as
     * non-element-producing (the recursion is rejected, never wrongly accepted).
     */
    private record Combinator(int closureArg, int elementParam, int listArg) {}

    private static final Map<String, Combinator> COMBINATORS = Map.of(
            "List.fold", new Combinator(0, 1, 2),
            "List.foldFrom", new Combinator(0, 1, 2),
            "List.map", new Combinator(0, 0, 1),
            "List.filter", new Combinator(0, 0, 1),
            "List.all", new Combinator(0, 0, 1),
            "List.any", new Combinator(0, 0, 1),
            "List.find", new Combinator(0, 0, 1),
            "List.partition", new Combinator(0, 0, 1));

    /**
     * Walks {@code e}, threading {@code lt} (each local name -&gt; the parameters it is a strictly
     * smaller part of), and records every call to {@code name} (the self-call). A {@code match} case
     * binding is a strictly smaller part of the parameters the scrutinee is rooted at; a {@code let}
     * of a field is too.
     */
    private static void walk(Ast.Expr e, String name, Set<String> paramNames,
                             Map<String, Set<String>> lt, List<RecCall> calls) {
        switch (e) {
            case Ast.Match m -> {
                walk(m.scrutinee(), name, paramNames, lt, calls);
                Set<String> rooted = rootParams(m.scrutinee(), lt, paramNames);
                for (Ast.Case c : m.cases()) {
                    Map<String, Set<String>> inner = lt;
                    if (c.binding() != null && !rooted.isEmpty()) {
                        inner = with(lt, c.binding(), rooted);   // the bound value is smaller than each root
                    }
                    walk(c.body(), name, paramNames, inner, calls);
                }
            }
            case Ast.LetIn li -> {
                walk(li.value(), name, paramNames, lt, calls);
                Set<String> smaller = strictSmaller(li.value(), lt, paramNames);
                Map<String, Set<String>> inner = smaller.isEmpty() ? lt : with(lt, li.name(), smaller);
                walk(li.body(), name, paramNames, inner, calls);
            }
            case Ast.Call call -> {
                if (call.fn().equals(name)) {
                    calls.add(new RecCall(call, lt));
                }
                Combinator combo = COMBINATORS.get(call.fn());
                for (int ai = 0; ai < call.args().size(); ai++) {
                    Ast.Expr arg = call.args().get(ai);
                    if (combo != null && ai == combo.closureArg() && arg instanceof Ast.Block step
                            && combo.elementParam() < step.params().size()
                            && combo.listArg() < call.args().size()) {
                        // `step` iterates the list argument; each element it is handed is a member of
                        // that list, so if the list is (part of) a parameter, the element is a strictly
                        // smaller part of it. Bind the element parameter accordingly for the step body.
                        Set<String> elemRoots =
                                rootParams(call.args().get(combo.listArg()), lt, paramNames);
                        Map<String, Set<String>> inner = elemRoots.isEmpty()
                                ? lt
                                : with(lt, step.params().get(combo.elementParam()), elemRoots);
                        walk(step.body(), name, paramNames, inner, calls);
                    } else {
                        walk(arg, name, paramNames, lt, calls);
                    }
                }
            }
            default -> forEachChild(e, child -> walk(child, name, paramNames, lt, calls));
        }
    }

    /** The parameters {@code e} is a (possibly-improper) descendant of — a parameter itself, a field
     * chain rooted at one, or a local already known to be smaller than one. Used for a {@code match}
     * scrutinee: unwrapping a case of such a value yields a strictly smaller part. */
    private static Set<String> rootParams(Ast.Expr e, Map<String, Set<String>> lt, Set<String> paramNames) {
        return switch (e) {
            case Ast.Var v -> {
                Set<String> s = new HashSet<>();
                if (paramNames.contains(v.name())) {
                    s.add(v.name());
                }
                s.addAll(lt.getOrDefault(v.name(), Set.of()));
                yield s;
            }
            case Ast.FieldAccess fa -> rootParams(fa.target(), lt, paramNames);
            default -> Set.of();
        };
    }

    /** The parameters {@code e} is a <em>strictly</em> smaller part of — a field access (a field is
     * strictly smaller than its target), or a local already known to be smaller. A bare parameter is
     * not strictly smaller than itself. */
    private static Set<String> strictSmaller(Ast.Expr e, Map<String, Set<String>> lt, Set<String> paramNames) {
        return switch (e) {
            case Ast.Var v -> lt.getOrDefault(v.name(), Set.of());
            case Ast.FieldAccess fa -> rootParams(fa.target(), lt, paramNames);
            default -> Set.of();
        };
    }

    private static Map<String, Set<String>> with(Map<String, Set<String>> lt, String name, Set<String> params) {
        Map<String, Set<String>> copy = new HashMap<>(lt);
        copy.put(name, params);
        return copy;
    }

    // --- call graph over module-own helpers (for detecting mutual recursion) ---

    private static Map<String, Set<String>> ownCallGraph(Map<String, Ast.FnDef> own) {
        Map<String, Set<String>> edges = new HashMap<>();
        for (Ast.FnDef h : own.values()) {
            Set<String> called = new HashSet<>();
            collectOwnCalls(h.body(), own.keySet(), called);
            edges.put(h.name(), called);
        }
        return edges;
    }

    private static void collectOwnCalls(Ast.Expr e, Set<String> own, Set<String> out) {
        if (e instanceof Ast.Call call && own.contains(call.fn())) {
            out.add(call.fn());
        }
        forEachChild(e, c -> collectOwnCalls(c, own, out));
    }

    /** The helpers on {@code name}'s recursive cycle: those reachable from {@code name} that also reach
     * {@code name} back. Includes {@code name} when it is (self- or mutually-) recursive. */
    private static Set<String> cycleMembers(String name, Map<String, Set<String>> edges) {
        Set<String> forward = reachable(name, edges);
        Set<String> cycle = new HashSet<>();
        for (String m : forward) {
            if (reachable(m, edges).contains(name)) {
                cycle.add(m);
            }
        }
        return cycle;
    }

    private static Set<String> reachable(String from, Map<String, Set<String>> edges) {
        Set<String> seen = new HashSet<>();
        java.util.Deque<String> work = new java.util.ArrayDeque<>(edges.getOrDefault(from, Set.of()));
        while (!work.isEmpty()) {
            String n = work.poll();
            if (seen.add(n)) {
                work.addAll(edges.getOrDefault(n, Set.of()));
            }
        }
        return seen;
    }

    private static CompileException error(Ast.FnDef h, String name, String key, String message) {
        return CompileException.of(
                Diagnostic.of("E2001", key).title("check.totality.title")
                        .at(h.pos(), name.length()).args(name).build(),
                message);
    }

    private static CompileException error(Ast.Call call, String name, String key, String message) {
        return CompileException.of(
                Diagnostic.of("E2001", key).title("check.totality.title")
                        .at(call.pos(), call.fn().length()).args(name).build(),
                message);
    }

    // --- a direct-child visitor mirroring the one in HelperInliner/TypeChecker ---

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
            case Ast.IntLit _, Ast.DecimalLit _, Ast.StringLit _, Ast.BoolLit _, Ast.Var _ -> { }
        }
    }
}
