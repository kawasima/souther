package souther.compiler.check;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * The predicates the guards have settled on the current path, each keyed by a canonical rendering of
 * the call that states it (see {@link InvariantChecker}). A predicate that is not a comparison —
 * {@code List.allDistinctBy}, {@code List.contains}, {@code String.matches} — holds no numeric relation
 * and so has nowhere to live in {@link souther.compiler.numeric.NumericDomain}; this is where it lives instead.
 *
 * <p>Nothing here reasons: two predicates relate only by being the same key. What that buys is the
 * guarantee the discharge check needs — a construction whose invariant states a predicate is
 * discharged by a guard stating the same predicate of the same term, and reported when there is
 * none. Immutable, threaded functionally alongside the numeric domain.
 *
 * <p>What the guards settled is held as they settled it ({@link SettledPredicates}) and read out
 * once, where a reader asks. Every way a predicate arrives — a guard settling one, two readings said
 * together, a change of vocabulary — is a composition and costs nothing that grows with what the
 * path had already.
 *
 * <p>Kept in the order the predicates were settled. Nothing here answers with one of them, and a
 * renaming that has to refuse two subjects under one name names whichever it reaches first — read
 * off a set whose iteration order is salted once per run, which of two collisions is reported would
 * move between runs of the same compiler.
 *
 * @param <A> what a fact is filed under. The subject is the caller's, because one reading's
 *            predicates are met with another's only once both are said in one vocabulary
 */
public final class PredicateFacts<A> {

    /**
     * A contradiction settled under names this state does not have.
     *
     * <p>What a change of vocabulary leaves of guards that cannot all hold. Nothing there is asked
     * of the naming: a path nothing reaches says the same thing under any names, and handing its
     * subjects over would have a renaming refusing two of them over a disagreement on a path the
     * program never takes.
     */
    private final boolean contradictedUnderOtherNames;

    private final SettledPredicates<A> settled;

    /** What {@link #settled} comes to, computed where it is asked for and kept. */
    private Settlings<A> settlings;

    /** The predicates settled each way, and whether one of them was settled both ways. */
    private record Settlings<A>(boolean contradictory, Set<A> holds, Set<A> fails) {}

    private PredicateFacts(boolean contradictedUnderOtherNames, SettledPredicates<A> settled) {
        this.contradictedUnderOtherNames = contradictedUnderOtherNames;
        this.settled = settled;
    }

    private Settlings<A> settlings() {
        if (settlings == null) {
            Set<A> holds = new LinkedHashSet<>();
            Set<A> fails = new LinkedHashSet<>();
            boolean contradictory = false;
            for (SettledPredicates.One<A> one : settled.distinct()) {
                Set<A> theOtherWay = one.positive() ? fails : holds;
                contradictory = contradictory || theOtherWay.contains(one.key());
                (one.positive() ? holds : fails).add(one.key());
            }
            settlings = new Settlings<>(contradictory, Collections.unmodifiableSet(holds),
                    Collections.unmodifiableSet(fails));
        }
        return settlings;
    }

    /** Contradictory guards, which is one key settled both ways however it was reached. */
    private static <A> PredicateFacts<A> bottom() {
        return new PredicateFacts<>(true, SettledPredicates.none());
    }

    /** Nothing settled either way. */
    public static <A> PredicateFacts<A> none() {
        return new PredicateFacts<>(false, SettledPredicates.none());
    }

    /**
     * Whether the predicates settled here cannot all hold.
     *
     * <p>A claim about the values and not about the reading: what it takes to reach it is one key
     * settled both ways, which is a contradiction whatever the predicate says. So it is read where a
     * value is asked for as well as where a path is — a declaration stating a predicate of its value
     * and denying the same predicate of the same value has no value, and nothing about that answer
     * needs the numbers.
     */
    public boolean isBottom() {
        return contradictedUnderOtherNames || settlings().contradictory();
    }

    /** The facts with {@code key} settled. Settling it both ways makes the path infeasible. */
    PredicateFacts<A> assume(A key, boolean positive) {
        return new PredicateFacts<>(contradictedUnderOtherNames,
                settled.and(SettledPredicates.of(key, positive)));
    }

    /** Whether the guards prove {@code key} (or its negation, when {@code positive} is false). */
    boolean entails(A key, boolean positive) {
        return isBottom() || (positive ? settlings().holds() : settlings().fails()).contains(key);
    }

    /** Whether the guards prove the opposite of what {@code positive} asks of {@code key}. */
    boolean refutes(A key, boolean positive) {
        return !isBottom() && (positive ? settlings().fails() : settlings().holds()).contains(key);
    }

    /**
     * Both readings settled at once.
     *
     * <p>A predicate one of them holds and the other denies is one key settled both ways, which is
     * the same contradiction reaching this the same way it reaches it from a single reading.
     * Nothing else here relates two predicates, so the rest is what each of them settled, settled.
     */
    public PredicateFacts<A> meet(PredicateFacts<A> other) {
        return new PredicateFacts<>(
                contradictedUnderOtherNames || other.contradictedUnderOtherNames,
                settled.and(other.settled));
    }

    /**
     * The same facts about the same predicates, under the names {@code naming} gives their subjects.
     *
     * <p>The naming has to name two subjects two subjects. Two of them arriving under one name would
     * be one predicate settled by two readings that never said anything about each other, and where
     * one holds it and the other denies it this would come out contradictory over a disagreement
     * nobody wrote. Which is not checked here — this sits below the package that owns a whole
     * vocabulary — and is what {@link InjectiveRenaming} is, handed over by
     * {@link ConstraintState#renamed}. Every subject held here passes through the naming, so a
     * caller holding one of those sees all of them.
     */
    public <B> PredicateFacts<B> renamed(Function<A, B> naming) {
        if (isBottom()) {
            return bottom();
        }
        SettledPredicates<B> out = SettledPredicates.none();
        for (A key : settlings().holds()) {
            out = out.and(SettledPredicates.of(naming.apply(key), true));
        }
        for (A key : settlings().fails()) {
            out = out.and(SettledPredicates.of(naming.apply(key), false));
        }
        return new PredicateFacts<>(false, out);
    }
}
