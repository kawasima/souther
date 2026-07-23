package net.unit8.souther.compiler.check;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A small numeric abstract domain — a per-atom interval plus difference-bound constraints
 * ({@code a - b <= c}, the octagon-style relational part) — over named atoms. An atom is the
 * numeric content of a variable, a field chain, or a newtype's wrapped value (see
 * {@link InvariantChecker}). Constants are {@link BigDecimal}; {@code null} bounds are ±infinity.
 *
 * <p>This is the decision procedure the invariant-discharge check runs on: {@link #assume} tightens
 * the domain along a {@code require}/{@code if} guard or an input newtype's invariant, and
 * {@link #entails} / {@link #refutes} answer whether a construction's invariant is discharged or is
 * definitely violated on the current path. It is deliberately bounded to interval + difference-bound;
 * an invariant the checker cannot express here is left opaque (its runtime check stays), so the set of
 * facts it can prove is exactly the set of guards a user can write to discharge them (spec
 * §invariant-discharge). Instances are immutable — each operation returns a fresh domain, threaded
 * functionally like {@code TotalityChecker}'s scope map.
 */
final class NumericDomain {

    /** A comparison of a {@link LinearForm} against zero. */
    enum Rel { GE, GT, LE, LT, EQ }

    /** An affine form {@code const + Σ coef·atom} over the domain's atoms. */
    record LinearForm(BigDecimal constant, Map<String, BigDecimal> coefs) {
        static LinearForm constant(BigDecimal c) {
            return new LinearForm(c, Map.of());
        }

        static LinearForm atom(String a) {
            return new LinearForm(BigDecimal.ZERO, Map.of(a, BigDecimal.ONE));
        }

        LinearForm plus(LinearForm o) {
            Map<String, BigDecimal> m = new HashMap<>(coefs);
            o.coefs.forEach((k, v) -> m.merge(k, v, BigDecimal::add));
            m.values().removeIf(v -> v.signum() == 0);
            return new LinearForm(constant.add(o.constant), m);
        }

        LinearForm negate() {
            Map<String, BigDecimal> m = new HashMap<>();
            coefs.forEach((k, v) -> m.put(k, v.negate()));
            return new LinearForm(constant.negate(), m);
        }

        LinearForm minus(LinearForm o) {
            return plus(o.negate());
        }
    }

    private final boolean bottom;                              // an infeasible path (guards contradict)
    private final Map<String, BigDecimal> lo;                  // atom -> lower bound (null key absent = -inf)
    private final Map<String, BigDecimal> hi;                  // atom -> upper bound (absent = +inf)
    private final Map<String, Map<String, BigDecimal>> diff;   // diff[a][b] = tightest known (a - b)

    private NumericDomain(boolean bottom, Map<String, BigDecimal> lo, Map<String, BigDecimal> hi,
                          Map<String, Map<String, BigDecimal>> diff) {
        this.bottom = bottom;
        this.lo = lo;
        this.hi = hi;
        this.diff = diff;
    }

    static NumericDomain top() {
        return new NumericDomain(false, Map.of(), Map.of(), Map.of());
    }

    boolean isBottom() {
        return bottom;
    }

    // --- assume: tighten along `f rel 0` -------------------------------------------------------

    /** The domain refined by asserting {@code f rel 0}. Non-difference, non-interval forms are not
     * representable and leave the domain unchanged (sound: it stays weaker, never wrongly tighter). */
    NumericDomain assume(LinearForm f, Rel rel) {
        if (bottom) {
            return this;
        }
        return switch (rel) {
            case GE -> assumeLe(f.negate());          // f >= 0  <=>  -f <= 0
            case GT -> assumeLt(f.negate());
            case LE -> assumeLe(f);
            case LT -> assumeLt(f);
            case EQ -> assumeLe(f).assumeLe(f.negate());
        };
    }

    private NumericDomain assumeLe(LinearForm g) {
        return addLe(g, false);
    }

    private NumericDomain assumeLt(LinearForm g) {
        // strict < is approximated by <= (sound for discharge; loses nothing on the cases we need)
        return addLe(g, true);
    }

    /** Assert {@code g <= 0} (or {@code g < 0} when strict), updating an interval or a difference. */
    private NumericDomain addLe(LinearForm g, boolean strict) {
        Map<String, BigDecimal> c = g.coefs();
        if (c.isEmpty()) {
            boolean ok = strict ? g.constant().signum() < 0 : g.constant().signum() <= 0;
            return ok ? this : bottom();
        }
        if (c.size() == 1) {
            Map.Entry<String, BigDecimal> e = c.entrySet().iterator().next();
            String a = e.getKey();
            BigDecimal k = e.getValue();
            // k·a + const <= 0  =>  a <= -const/k (k>0, an upper bound)  or  a >= -const/k (k<0, a
            // lower bound). Round an inexact quotient conservatively — toward +inf for an upper bound,
            // toward -inf for a lower bound — so the recorded bound is never tighter than the true one.
            // A tighter-than-true bound would make entails/refutes unsound (a false E2010).
            boolean upper = k.signum() > 0;
            java.math.MathContext mc = new java.math.MathContext(
                    34, upper ? java.math.RoundingMode.CEILING : java.math.RoundingMode.FLOOR);
            BigDecimal bound = g.constant().negate().divide(k, mc);
            return upper ? withHi(a, bound) : withLo(a, bound);
        }
        if (c.size() == 2 && isUnitDifference(c)) {
            // {a:+1, b:-1}·+ const <= 0  =>  a - b <= -const
            String a = null;
            String b = null;
            for (Map.Entry<String, BigDecimal> e : c.entrySet()) {
                if (e.getValue().signum() > 0) {
                    a = e.getKey();
                } else {
                    b = e.getKey();
                }
            }
            return withDiff(a, b, g.constant().negate());
        }
        return this;   // not representable — leave unchanged (sound)
    }

    private static boolean isUnitDifference(Map<String, BigDecimal> c) {
        int pos = 0;
        int neg = 0;
        for (BigDecimal v : c.values()) {
            if (v.compareTo(BigDecimal.ONE) == 0) {
                pos++;
            } else if (v.compareTo(BigDecimal.ONE.negate()) == 0) {
                neg++;
            } else {
                return false;
            }
        }
        return pos == 1 && neg == 1;
    }

    // --- entails / refutes ---------------------------------------------------------------------

    /** Whether the domain proves {@code f rel 0} (the construction's invariant is discharged). */
    boolean entails(LinearForm f, Rel rel) {
        if (bottom) {
            return true;   // an infeasible path discharges anything
        }
        return switch (rel) {
            case GE -> proveLe(f.negate(), false);    // f >= 0  <=>  -f <= 0
            case GT -> proveLe(f.negate(), true);
            case LE -> proveLe(f, false);
            case LT -> proveLe(f, true);
            case EQ -> proveLe(f, false) && proveLe(f.negate(), false);
        };
    }

    /** Whether the domain proves {@code ¬(f rel 0)} — the invariant is <em>definitely</em> violated
     * on this path (a compile error, the path-sensitive generalization of the constant check). */
    boolean refutes(LinearForm f, Rel rel) {
        if (bottom) {
            return false;   // an unreachable path violates nothing
        }
        return switch (rel) {
            case GE -> proveLe(f, true);              // ¬(f >= 0)  <=>  f < 0
            case GT -> proveLe(f, false);             // ¬(f > 0)   <=>  f <= 0
            case LE -> proveLe(f.negate(), true);     // ¬(f <= 0)  <=>  f > 0  <=>  -f < 0
            case LT -> proveLe(f.negate(), false);    // ¬(f < 0)   <=>  f >= 0 <=>  -f <= 0
            case EQ -> false;                          // not needed; never refute equality here
        };
    }

    /** Whether {@code g <= 0} (or {@code g < 0} when strict) follows from the domain. */
    private boolean proveLe(LinearForm g, boolean strict) {
        BigDecimal hiG = upperBound(g);
        if (hiG != null) {
            int s = hiG.signum();
            if (strict ? s < 0 : s <= 0) {
                return true;
            }
        }
        if (g.coefs().size() == 2 && isUnitDifference(g.coefs())) {
            String a = null;
            String b = null;
            for (Map.Entry<String, BigDecimal> e : g.coefs().entrySet()) {
                if (e.getValue().signum() > 0) {
                    a = e.getKey();
                } else {
                    b = e.getKey();
                }
            }
            BigDecimal ab = closedDiff(a, b);      // proven upper bound on (a - b)
            if (ab != null) {
                BigDecimal bound = g.constant().negate();   // want a - b <= -const
                int s = ab.compareTo(bound);
                if (s < 0 || (!strict && s == 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The interval upper bound of {@code f}, or {@code null} if unbounded above. */
    private BigDecimal upperBound(LinearForm f) {
        BigDecimal acc = f.constant();
        for (Map.Entry<String, BigDecimal> e : f.coefs().entrySet()) {
            BigDecimal k = e.getValue();
            BigDecimal b = k.signum() > 0 ? hi.get(e.getKey()) : lo.get(e.getKey());
            if (b == null) {
                return null;   // unbounded in the contributing direction
            }
            acc = acc.add(k.multiply(b));
        }
        return acc;
    }

    // --- assignment ----------------------------------------------------------------------------

    /** The domain after {@code atom := f}: drop every prior fact about {@code atom}, then record its
     * new interval bounds from {@code f} (relational facts about {@code f} are not re-derived). */
    NumericDomain assign(String atom, LinearForm f) {
        if (bottom) {
            return this;
        }
        NumericDomain d = forget(atom);
        BigDecimal up = d.upperBound(f);
        BigDecimal down = negOrNull(d.upperBound(f.negate()));
        NumericDomain r = d;
        if (up != null) {
            r = r.withHi(atom, up);
        }
        if (down != null) {
            r = r.withLo(atom, down);
        }
        return r;
    }

    private static BigDecimal negOrNull(BigDecimal v) {
        return v == null ? null : v.negate();
    }

    private NumericDomain forget(String atom) {
        Map<String, BigDecimal> nlo = new HashMap<>(lo);
        Map<String, BigDecimal> nhi = new HashMap<>(hi);
        nlo.remove(atom);
        nhi.remove(atom);
        Map<String, Map<String, BigDecimal>> nd = new HashMap<>();
        diff.forEach((a, row) -> {
            if (!a.equals(atom)) {
                Map<String, BigDecimal> nr = new HashMap<>(row);
                nr.remove(atom);
                if (!nr.isEmpty()) {
                    nd.put(a, nr);
                }
            }
        });
        return new NumericDomain(false, nlo, nhi, nd);
    }

    // --- immutable updates ---------------------------------------------------------------------

    private NumericDomain bottom() {
        return new NumericDomain(true, Map.of(), Map.of(), Map.of());
    }

    private NumericDomain withHi(String a, BigDecimal bound) {
        Map<String, BigDecimal> nhi = new HashMap<>(hi);
        nhi.merge(a, bound, NumericDomain::min);
        NumericDomain d = new NumericDomain(false, lo, nhi, diff);
        return d.feasible(a) ? d : bottom();
    }

    private NumericDomain withLo(String a, BigDecimal bound) {
        Map<String, BigDecimal> nlo = new HashMap<>(lo);
        nlo.merge(a, bound, NumericDomain::max);
        NumericDomain d = new NumericDomain(false, nlo, hi, diff);
        return d.feasible(a) ? d : bottom();
    }

    private NumericDomain withDiff(String a, String b, BigDecimal bound) {
        Map<String, Map<String, BigDecimal>> nd = new HashMap<>();
        diff.forEach((k, v) -> nd.put(k, new HashMap<>(v)));
        nd.computeIfAbsent(a, k -> new HashMap<>()).merge(b, bound, NumericDomain::min);
        return new NumericDomain(false, lo, hi, nd);
    }

    private boolean feasible(String a) {
        BigDecimal l = lo.get(a);
        BigDecimal h = hi.get(a);
        return l == null || h == null || l.compareTo(h) <= 0;
    }

    /** The tightest proven upper bound on {@code a - b}, closing difference facts transitively over a
     * bounded number of hops, or {@code null} if none is known. */
    private BigDecimal closedDiff(String a, String b) {
        if (a.equals(b)) {
            return BigDecimal.ZERO;
        }
        // Bellman-Ford style relaxation of `a - x <= c` edges to reach `a - b`.
        Map<String, BigDecimal> best = new HashMap<>();
        best.put(a, BigDecimal.ZERO);
        Set<String> atoms = new HashSet<>(diff.keySet());
        diff.values().forEach(r -> atoms.addAll(r.keySet()));
        for (int i = 0; i < atoms.size() + 1; i++) {
            boolean changed = false;
            for (Map.Entry<String, Map<String, BigDecimal>> row : diff.entrySet()) {
                BigDecimal du = best.get(row.getKey());
                if (du == null) {
                    continue;
                }
                for (Map.Entry<String, BigDecimal> edge : row.getValue().entrySet()) {
                    BigDecimal cand = du.add(edge.getValue());
                    BigDecimal cur = best.get(edge.getKey());
                    if (cur == null || cand.compareTo(cur) < 0) {
                        best.put(edge.getKey(), cand);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        return best.get(b);
    }

    private static BigDecimal min(BigDecimal x, BigDecimal y) {
        return x.compareTo(y) <= 0 ? x : y;
    }

    private static BigDecimal max(BigDecimal x, BigDecimal y) {
        return x.compareTo(y) >= 0 ? x : y;
    }
}
