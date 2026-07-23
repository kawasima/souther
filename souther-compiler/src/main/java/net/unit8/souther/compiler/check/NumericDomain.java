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
        if (rel == Rel.EQ) {
            return addLe(f, false).addLe(f.negate(), false);
        }
        // Reduce `f rel 0` to `g <= 0` (or `g < 0`): negate the form for >=/>, keep it for <=/<.
        return addLe(negOf(rel) ? f.negate() : f, strictOf(rel));
    }

    /** Assert {@code g <= 0} (or {@code g < 0} when strict), updating an interval or a difference. A
     * form outside the interval/difference fragment is not representable and leaves the domain
     * unchanged (sound). Strictness only sharpens the constant case; for an interval or difference a
     * strict {@code < 0} is recorded as {@code <= 0} (weaker, so still sound). */
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
        String[] ab = unitDiffAtoms(c);
        if (ab != null) {
            return withDiff(ab[0], ab[1], g.constant().negate());   // a - b <= -const
        }
        return this;   // not representable — leave unchanged (sound)
    }

    /** The two atoms of a unit difference {@code {a:+1, b:-1}} as {@code {a, b}}, or {@code null} if
     * {@code c} is not a two-atom form with coefficients +1 and -1. */
    private static String[] unitDiffAtoms(Map<String, BigDecimal> c) {
        if (c.size() != 2) {
            return null;
        }
        String a = null;
        String b = null;
        for (Map.Entry<String, BigDecimal> e : c.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ONE) == 0) {
                a = e.getKey();
            } else if (e.getValue().compareTo(BigDecimal.ONE.negate()) == 0) {
                b = e.getKey();
            } else {
                return null;
            }
        }
        return a != null && b != null ? new String[] {a, b} : null;
    }

    /** True for {@code GT}/{@code LT} (a strict comparison). */
    private static boolean strictOf(Rel rel) {
        return rel == Rel.GT || rel == Rel.LT;
    }

    /** True for {@code GE}/{@code GT} — the form is negated to reduce the comparison to {@code <= 0}. */
    private static boolean negOf(Rel rel) {
        return rel == Rel.GE || rel == Rel.GT;
    }

    // --- entails / refutes ---------------------------------------------------------------------

    /** Whether the domain proves {@code f rel 0} (the construction's invariant is discharged). */
    boolean entails(LinearForm f, Rel rel) {
        if (bottom) {
            return true;   // an infeasible path discharges anything
        }
        if (rel == Rel.EQ) {
            return proveLe(f, false) && proveLe(f.negate(), false);
        }
        return proveLe(negOf(rel) ? f.negate() : f, strictOf(rel));
    }

    /** Whether the domain proves {@code ¬(f rel 0)} — the invariant is <em>definitely</em> violated
     * on this path (a compile error, the path-sensitive generalization of the constant check). The
     * negation flips both bits of the comparison: {@code ¬(f >= 0)} is {@code f < 0}, etc. */
    boolean refutes(LinearForm f, Rel rel) {
        if (bottom || rel == Rel.EQ) {
            return false;   // an unreachable path violates nothing; equality is never refuted here
        }
        return proveLe(negOf(rel) ? f : f.negate(), !strictOf(rel));
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
        String[] ab = unitDiffAtoms(g.coefs());
        if (ab != null) {
            BigDecimal diffBound = closedDiff(ab[0], ab[1]);   // proven upper bound on (a - b)
            if (diffBound != null) {
                BigDecimal bound = g.constant().negate();      // want a - b <= -const
                int s = diffBound.compareTo(bound);
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
        NumericDomain d = new NumericDomain(false, lo, hi, nd);
        // Contradictory guards make the path infeasible: with a - b <= bound now recorded, if the
        // difference facts also prove b - a <= back and bound + back < 0, then 0 <= bound + back < 0.
        // Mark it bottom so entails discharges everything and refutes fires nothing — no false E2010
        // on a dead path.
        BigDecimal back = d.closedDiff(b, a);
        if (back != null && bound.add(back).signum() < 0) {
            return bottom();
        }
        return d;
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
