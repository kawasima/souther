package souther.compiler.check;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What came of trying to read one clause statically.
 *
 * <p>Three answers, and the first two are conclusions about the clause while the third is not. "The
 * reading finished and these are the parts to establish" and "the clause came out one way on its
 * own" are both things the model settles. "The reading did not finish" is a fact about this compiler
 * on this run, and it settles nothing — whether the clause is inside the fragment is exactly what was
 * not found out. Held as one answer, an author is told that no guard discharges their construction
 * because an analysis fell over on it, and nothing fails while that is happening, since a stop and a
 * negative conclusion are both silent.
 *
 * <p>Read off what the walk that reads clauses answered, and not worked out beside it. The walk
 * reads the clause as the comparisons it states before it decides anything — {@code Int.compare(1,
 * 2) >= 0} is a call to a reader folding what an author wrote, and is the order of {@code 1} and
 * {@code 2} to this walk — so a reader folding the written form first sees a call, and comes back
 * with a relation any guard implying it would discharge for a clause no value satisfies.
 */
public sealed interface CapabilityResult {

    /**
     * The reading finished, and the clause has these parts to establish.
     *
     * <p>All of them, which is what a list of parts says and a flat set of readings does not. A
     * clause read as a bound in one part and as a term in another is not discharged by either guard
     * alone. The alternatives are inside a part ({@link RequiredPart.Routed}), which is the shape
     * {@link Predicates.Clause#dischargedBy} has.
     *
     * <p>Never empty. A clause with no part to establish is one that came out on its own, which is
     * {@link Decided}, and an empty list is what a reading that never ran would produce as well.
     */
    record Analyzed(List<RequiredPart> parts) implements CapabilityResult {

        public Analyzed {
            if (parts == null || parts.isEmpty()) {
                throw new IllegalArgumentException(
                        "a clause with nothing to establish came out on its own");
            }
            parts = List.copyOf(parts);
        }

        public static Analyzed of(RequiredPart... parts) {
            return new Analyzed(List.of(parts));
        }

        /** One part carried by one or more routes, which is what most clauses come to. */
        public static Analyzed routed(StaticRoute... routes) {
            return new Analyzed(
                    List.of(new RequiredPart.Routed(new LinkedHashSet<>(List.of(routes)))));
        }
    }

    /**
     * The clause came out one way before any construction was looked at.
     *
     * <p>{@code invariant 1 >= 0} holds of every value, so nothing is asked of a guard;
     * {@code invariant 1 < 0} holds of none, so no guard establishes it and no value of the type can
     * be built. Both are answers a reading reached, and neither is a clause this compiler could not
     * read — which is what both of them used to be reported as, in opposite directions.
     *
     * <p>Both signs here, and not the true one with the false one left to fall wherever it falls.
     * They come from one fold of one expression. What an author should be told about
     * {@code Decided(false)} — a line in an editor, a diagnostic at the declaration, or a type
     * nothing inhabits — is a separate question, and one about the language rather than about this
     * accounting.
     *
     * @param holds which way it folded
     */
    record Decided(boolean holds) implements CapabilityResult {}

    /**
     * The reading stopped, so there is no conclusion about the clause.
     *
     * <p>{@code where} is for whoever is working out why, and is not published. What a document says
     * out of this arm is that this compiler did not finish; it names nothing about the clause,
     * because nothing about the clause was established.
     */
    record AnalysisStopped(String where) implements CapabilityResult {

        public AnalysisStopped {
            if (where == null) {
                throw new IllegalArgumentException("a reading that stopped says where it was");
            }
        }
    }

    /**
     * What the reading of a clause came to, said as one of these.
     *
     * <p>The whole of the mapping, so that it is one thing to hold to rather than a shape assembled
     * wherever a classification is wanted. What the walk owes, what it folded to, and what it stopped
     * on are all that walk's own answers; nothing here asks the clause a second question.
     *
     * <p>A fold is the answer where there is one, and is not said beside the parts: a clause that
     * holds of every value asks nothing of a guard whatever route the walk also found for it, and one
     * that holds of none is discharged by nothing. Where a conjunction folds only in part the fold is
     * not the clause's — {@link Predicates.Fold#and} keeps it undecided — and the parts are.
     */
    static CapabilityResult of(Predicates.Owed owed) {
        if (owed.folded() != Predicates.Fold.NOT_DECIDED) {
            return new Decided(owed.folded() == Predicates.Fold.HOLDS);
        }
        // One for one, in the order they were read. What was not read is a part like any other, so
        // a clause half of which could not be read is not described entirely by the half that was —
        // an author would write the guard that discharges that half and find the construction still
        // refused — and two parts outside the fragment are two parts rather than the first of them.
        List<RequiredPart> parts = new ArrayList<>();
        for (Predicates.Part each : owed.parts()) {
            parts.add(switch (each) {
                case Predicates.Part.Carried it -> new RequiredPart.Routed(routesOf(it.clause()));
                case Predicates.Part.Unread it ->
                        new RequiredPart.OutsideTheFragment(it.why());
            });
        }
        return new Analyzed(parts);
    }

    /** The ways one part could be discharged, which is every one the walk built for it. A part with
     *  neither is not one of these — the walk answers that it could not read it. */
    private static Set<StaticRoute> routesOf(Predicates.Clause clause) {
        Set<StaticRoute> routes = new LinkedHashSet<>();
        if (clause.numeric() != null) {
            routes.add(new StaticRoute.AsABound());
        }
        if (clause.fact() != null) {
            routes.add(new StaticRoute.AsATerm());
        }
        return routes;
    }
}
