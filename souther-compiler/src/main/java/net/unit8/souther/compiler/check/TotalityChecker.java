package net.unit8.souther.compiler.check;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that recursion is total by default (spec §fn-declaration): a module-own recursive helper
 * that is not {@code partial} must terminate, proven by <em>size-change termination</em> (Lee–Jones–
 * Ben-Amram) over the structural sub-term order. Every recursive call is a size-change graph relating
 * caller parameters to callee arguments: an argument that is a strictly smaller part of a parameter
 * (a sub-term from a {@code match} on a field or a case, or an element handed to a list combinator's
 * closure) is a strict ({@code <}) arc; an argument passed through unchanged is a non-increasing
 * ({@code =}) arc. Composing these graphs to a fixpoint, the recursion terminates iff every idempotent
 * cycle carries a strictly decreasing parameter. Self-referential data is finite by construction (the
 * inhabitability check), so a proven descent bottoms out — the helper terminates, and its examples can
 * be evaluated at compile time.
 *
 * <p>This subsumes self-recursion (a group of one) and mutual recursion (a strongly-connected group)
 * in one analysis; it accepts strictly more than a single-decreasing-position check, e.g. structural
 * lexicographic recursion. A {@code partial} helper opts out (it is not checked and may not terminate);
 * if any member of a mutually-recursive group is {@code partial} the whole group is skipped — a cycle
 * through an unchecked member cannot be certified, so its other members are not independently certified
 * either. The stdlib's {@code List.foldFrom} (index recursion) is trusted total and exempt — only
 * bare-named module-own helpers are checked. Numeric ({@code n - 1}) and index ({@code i + 1})
 * recursion are not structural (Souther has no inductive {@code Nat}) and must be {@code partial}.
 */
final class TotalityChecker {

    /** The composition-closure of a group's size-change graphs is bounded above by the number of
     * distinct labeled graphs, which grows with parameter count; a group of many-parameter helpers
     * could otherwise blow up the worklist and hang the check. When the closure exceeds this many
     * graphs the group is rejected as too complex to prove total (conservative — sound, not accepted). */
    private static final int MAX_CLOSURE = 50_000;

    private TotalityChecker() {}

    /** Checks every non-{@code partial}, module-own recursive helper (or group) for size-change
     * termination. */
    static void check(HelperInliner inliner) {
        Map<String, Ast.FnDef> own = inliner.helpers();
        Map<String, Set<String>> ownEdges = ownCallGraph(own);
        Set<String> handled = new HashSet<>();
        for (String name : inliner.recursiveHelpers()) {
            Ast.FnDef h = own.get(name);
            // A qualified name (`List.foldFrom`) is a prelude recursive helper injected into the module
            // (trusted total); only bare-named user helpers are checked.
            if (h == null || name.indexOf('.') >= 0) {
                continue;
            }
            Set<String> group = cycleMembers(name, ownEdges);   // the strongly-connected group (>= 1)
            if (!handled.add(name)) {
                continue;   // a sibling of an already-analyzed group
            }
            handled.addAll(group);
            // `partial` opts out; a `partial` anywhere in a mutual group opts the whole group out.
            if (group.stream().anyMatch(m -> own.get(m).partial())) {
                continue;
            }
            Built built = buildScgs(group, own);
            Set<Scg> closure = close(built.scgs());
            if (closure == null) {
                throw tooComplex(group, own);
            }
            if (isSizeChangeTerminating(closure)) {
                continue;
            }
            throw notTerminating(group, own, built.firstCall());
        }
    }

    /** The rejection for a group that is not size-change terminating. A group of one keeps the
     * structural-recursion message, reported at a representative self-call; a larger group reports the
     * mutual failure at its lexicographically-first member (a stable anchor). */
    private static CompileException notTerminating(Set<String> group, Map<String, Ast.FnDef> own,
                                                   Map<String, Ast.Call> firstCall) {
        if (group.size() == 1) {
            String name = group.iterator().next();
            Ast.FnDef h = own.get(name);
            String message = "recursive helper `let " + name + "` is not structurally recursive: `" + name
                    + "(...)` passes no argument that is a strictly smaller part of a parameter."
                    + " Recurse on a part obtained by `match` (a field or a case), count with"
                    + " `fold`, or mark the helper `partial`";
            Ast.Call at = firstCall.get(name);
            return at == null
                    ? error(h, name, "check.totality.notstructural", message)
                    : error(at, name, "check.totality.notstructural", message);
        }
        Ast.FnDef anchor = own.get(java.util.Collections.min(group));
        String members = backtickJoin(group);
        return error(anchor, anchor.name().length(), members, "check.totality.sizechange",
                "recursive helpers " + members + " are mutually recursive but not size-change"
                        + " terminating: no argument strictly decreases around every recursive cycle."
                        + " Recurse on a strictly smaller part obtained by `match`, or mark one of them"
                        + " `partial` to opt out");
    }

    /** The rejection for a group whose size-change closure exceeds {@link #MAX_CLOSURE}: it may or may
     * not terminate, but it is too complex to decide, so it is rejected conservatively. */
    private static CompileException tooComplex(Set<String> group, Map<String, Ast.FnDef> own) {
        Ast.FnDef anchor = own.get(java.util.Collections.min(group));
        String members = backtickJoin(group);
        return error(anchor, anchor.name().length(), members, "check.totality.toocomplex",
                "recursion " + members + " is too complex to prove total by size-change analysis;"
                        + " mark one of them `partial` to opt out");
    }

    // --- size-change graphs -------------------------------------------------

    /** A size-change arc label: {@code LT} is a strict ({@code <}) decrease, {@code EQ} is
     * non-increasing ({@code =}); a {@code null} cell is an unknown relation (no arc). */
    private enum Rel { LT, EQ }

    /** The size-change graph of one call edge {@code from -> to}: {@code m[i][j]} relates parameter
     * {@code i} of the caller to argument position {@code j} (parameter {@code j}) of the callee.
     * Equality is by value over {@code from}, {@code to}, and the matrix — required, since the closure
     * lives in a {@code HashSet} and idempotence is tested with {@code equals}. */
    private static final class Scg {
        final String from;
        final String to;
        final Rel[][] m;

        Scg(String from, String to, Rel[][] m) {
            this.from = from;
            this.to = to;
            this.m = m;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Scg other)) {
                return false;
            }
            return from.equals(other.from) && to.equals(other.to) && Arrays.deepEquals(m, other.m);
        }

        @Override
        public int hashCode() {
            return (from.hashCode() * 31 + to.hashCode()) * 31 + Arrays.deepHashCode(m);
        }
    }

    /** The size-change graphs of a group, plus a representative self/mutual call per member (its
     * source position for a rejection message — recorded here so the reject path need not re-walk). */
    private record Built(List<Scg> scgs, Map<String, Ast.Call> firstCall) {}

    /** Builds the per-call-edge size-change graphs for every member of {@code group}. */
    private static Built buildScgs(Set<String> group, Map<String, Ast.FnDef> own) {
        List<Scg> scgs = new ArrayList<>();
        Map<String, Ast.Call> firstCall = new HashMap<>();
        for (String f : group) {
            Ast.FnDef def = own.get(f);
            List<Ast.FnParam> params = def.params();
            Set<String> paramNames = new HashSet<>();
            Map<String, Integer> idxOf = new HashMap<>();
            for (int i = 0; i < params.size(); i++) {
                paramNames.add(params.get(i).name());
                idxOf.put(params.get(i).name(), i);
            }
            List<RecCall> calls = new ArrayList<>();
            walk(def.body(), group, paramNames, Map.of(), Map.of(), calls);
            for (RecCall rc : calls) {
                firstCall.putIfAbsent(f, rc.call());
                int toArity = own.get(rc.callee()).params().size();
                Rel[][] m = new Rel[params.size()][toArity];
                int cols = Math.min(toArity, rc.call().args().size());
                for (int j = 0; j < cols; j++) {
                    Ast.Expr arg = rc.call().args().get(j);
                    Set<String> strict = strictSmaller(arg, rc.lt(), rc.eq(), paramNames);
                    Set<String> root = rootParams(arg, rc.lt(), rc.eq(), paramNames);
                    for (String p : strict) {
                        m[idxOf.get(p)][j] = Rel.LT;
                    }
                    for (String p : root) {
                        if (!strict.contains(p) && m[idxOf.get(p)][j] == null) {
                            m[idxOf.get(p)][j] = Rel.EQ;
                        }
                    }
                }
                scgs.add(new Scg(f, rc.callee(), m));
            }
        }
        return new Built(scgs, firstCall);
    }

    /** Relational composition {@code a;b} ({@code a.to == b.from}): an arc {@code i -> k} is {@code LT}
     * if some middle {@code j} carries a strict arc on either side, else {@code EQ} if some {@code j}
     * carries two {@code EQ} arcs, else absent. */
    private static Scg compose(Scg a, Scg b) {
        int rows = a.m.length;
        int cols = b.m.length > 0 ? b.m[0].length : 0;
        int mid = a.m.length > 0 ? a.m[0].length : 0;
        Rel[][] r = new Rel[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int k = 0; k < cols; k++) {
                Rel best = null;
                for (int j = 0; j < mid && j < b.m.length; j++) {
                    Rel aj = a.m[i][j];
                    Rel bj = b.m[j][k];
                    if (aj == null || bj == null) {
                        continue;
                    }
                    if (aj == Rel.LT || bj == Rel.LT) {
                        best = Rel.LT;
                        break;
                    }
                    best = Rel.EQ;
                }
                r[i][k] = best;
            }
        }
        return new Scg(a.from, b.to, r);
    }

    /** Closes {@code base} under composition to a fixpoint, or returns {@code null} if the closure
     * exceeds {@link #MAX_CLOSURE} (the group is then rejected as too complex — never accepted unproven).
     * Finite otherwise: the arcs range over a two-element label set on fixed dimensions and
     * {@code from}/{@code to} over a finite group, so only finitely many distinct graphs exist. */
    private static Set<Scg> close(List<Scg> base) {
        Set<Scg> all = new HashSet<>(base);
        java.util.Deque<Scg> work = new java.util.ArrayDeque<>(base);
        while (!work.isEmpty()) {
            if (all.size() > MAX_CLOSURE) {
                return null;
            }
            Scg x = work.poll();
            for (Scg y : new ArrayList<>(all)) {
                if (x.to.equals(y.from)) {
                    Scg c = compose(x, y);
                    if (all.add(c)) {
                        work.add(c);
                    }
                }
                if (y.to.equals(x.from)) {
                    Scg c = compose(y, x);
                    if (all.add(c)) {
                        work.add(c);
                    }
                }
            }
        }
        return all;
    }

    /** The Lee–Jones–Ben-Amram criterion: the group terminates iff every idempotent self-loop in the
     * closure carries a strictly decreasing parameter (a diagonal {@code LT}). An unknown relation is
     * no arc, so acceptance always rests on a proven descent — a non-terminating helper is never
     * accepted. */
    private static boolean isSizeChangeTerminating(Set<Scg> closure) {
        for (Scg g : closure) {
            if (!g.from.equals(g.to) || !compose(g, g).equals(g)) {
                continue;   // only idempotent self-loops witness a cycle
            }
            boolean strictDiagonal = false;
            for (int i = 0; i < g.m.length; i++) {
                if (i < g.m[i].length && g.m[i][i] == Rel.LT) {
                    strictDiagonal = true;
                    break;
                }
            }
            if (!strictDiagonal) {
                return false;
            }
        }
        return true;
    }

    /** A recorded recursive call to a group member, with the callee and the smaller-than / equal-to
     * relations ({@code lt} / {@code eq}) in scope where it appears. */
    private record RecCall(String callee, Ast.Call call,
                           Map<String, Set<String>> lt, Map<String, Set<String>> eq) {}

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
     * Walks {@code e}, threading {@code lt} (each local -&gt; the parameters it is a strictly smaller
     * part of) and {@code eq} (each local -&gt; the parameters it is exactly equal to, e.g. a {@code let}
     * alias), and records every call to a member of {@code group}. A {@code match} case binding is a
     * strictly smaller part of the parameters the scrutinee is rooted at; a {@code let} carries the
     * strict or equal relation of its value forward.
     */
    private static void walk(Ast.Expr e, Set<String> group, Set<String> paramNames,
                             Map<String, Set<String>> lt, Map<String, Set<String>> eq,
                             List<RecCall> calls) {
        switch (e) {
            case Ast.Match m -> {
                walk(m.scrutinee(), group, paramNames, lt, eq, calls);
                Set<String> rooted = rootParams(m.scrutinee(), lt, eq, paramNames);
                for (Ast.Case c : m.cases()) {
                    Map<String, Set<String>> inner = lt;
                    if (c.binding() != null && !rooted.isEmpty()) {
                        inner = with(lt, c.binding(), rooted);   // the bound value is smaller than each root
                    }
                    walk(c.body(), group, paramNames, inner, eq, calls);
                }
            }
            case Ast.LetIn li -> {
                walk(li.value(), group, paramNames, lt, eq, calls);
                Set<String> smaller = strictSmaller(li.value(), lt, eq, paramNames);
                Set<String> equal = eqRoots(li.value(), eq, paramNames);
                Map<String, Set<String>> ltInner = smaller.isEmpty() ? lt : with(lt, li.name(), smaller);
                Map<String, Set<String>> eqInner = equal.isEmpty() ? eq : with(eq, li.name(), equal);
                walk(li.body(), group, paramNames, ltInner, eqInner, calls);
            }
            case Ast.Call call -> {
                if (group.contains(call.fn())) {
                    calls.add(new RecCall(call.fn(), call, lt, eq));
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
                                rootParams(call.args().get(combo.listArg()), lt, eq, paramNames);
                        Map<String, Set<String>> inner = elemRoots.isEmpty()
                                ? lt
                                : with(lt, step.params().get(combo.elementParam()), elemRoots);
                        walk(step.body(), group, paramNames, inner, eq, calls);
                    } else {
                        walk(arg, group, paramNames, lt, eq, calls);
                    }
                }
            }
            default -> forEachChild(e, child -> walk(child, group, paramNames, lt, eq, calls));
        }
    }

    /** The parameters {@code e} is a (possibly-improper) descendant of — a parameter itself, an exact
     * alias of one (through {@code eq}), a field chain rooted at one, or a local already known to be
     * smaller than one. Used for a {@code match} scrutinee: unwrapping a case of such a value yields a
     * strictly smaller part. */
    private static Set<String> rootParams(Ast.Expr e, Map<String, Set<String>> lt,
                                          Map<String, Set<String>> eq, Set<String> paramNames) {
        return switch (e) {
            case Ast.Var v -> {
                Set<String> s = new HashSet<>();
                if (paramNames.contains(v.name())) {
                    s.add(v.name());
                }
                s.addAll(lt.getOrDefault(v.name(), Set.of()));
                s.addAll(eq.getOrDefault(v.name(), Set.of()));
                yield s;
            }
            case Ast.FieldAccess fa -> rootParams(fa.target(), lt, eq, paramNames);
            default -> Set.of();
        };
    }

    /** The parameters {@code e} is a <em>strictly</em> smaller part of — a field access (a field is
     * strictly smaller than its target), or a local already known to be smaller. A bare parameter, or
     * an exact alias of one, is not strictly smaller than itself. */
    private static Set<String> strictSmaller(Ast.Expr e, Map<String, Set<String>> lt,
                                             Map<String, Set<String>> eq, Set<String> paramNames) {
        return switch (e) {
            case Ast.Var v -> lt.getOrDefault(v.name(), Set.of());
            case Ast.FieldAccess fa -> rootParams(fa.target(), lt, eq, paramNames);
            default -> Set.of();
        };
    }

    /** The parameters {@code e} is <em>exactly equal</em> to — a bare parameter or an alias of one. A
     * field access is strictly smaller, not equal, so it is not here (it is in {@link #strictSmaller}). */
    private static Set<String> eqRoots(Ast.Expr e, Map<String, Set<String>> eq, Set<String> paramNames) {
        if (e instanceof Ast.Var v) {
            Set<String> s = new HashSet<>();
            if (paramNames.contains(v.name())) {
                s.add(v.name());
            }
            s.addAll(eq.getOrDefault(v.name(), Set.of()));
            return s;
        }
        return Set.of();
    }

    private static Map<String, Set<String>> with(Map<String, Set<String>> env, String name, Set<String> params) {
        Map<String, Set<String>> copy = new HashMap<>(env);
        copy.put(name, params);
        return copy;
    }

    private static String backtickJoin(Set<String> names) {
        List<String> sorted = new ArrayList<>(names);
        java.util.Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('`').append(sorted.get(i)).append('`');
        }
        return sb.toString();
    }

    // --- call graph over module-own helpers (for grouping mutual recursion) ---

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

    private static CompileException error(Ast.FnDef h, int underlineLen, String arg, String key, String message) {
        return CompileException.of(
                Diagnostic.of("E2001", key).title("check.totality.title")
                        .at(h.pos(), underlineLen).args(arg).build(),
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
