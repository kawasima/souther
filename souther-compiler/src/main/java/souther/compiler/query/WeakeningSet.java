package souther.compiler.query;

import souther.compiler.observe.Incompleteness;
import souther.compiler.partition.ClosureGap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything that leaves one measurement weaker than it looks, and the one way measurements are put
 * together.
 *
 * <p>A parent measure's account of itself is the union of what its parts went without. That is the
 * whole of the arithmetic: a report adding up nine measures asks each of them what weakened it and
 * unions the answers, where it used to ask each of them a boolean and rebuild the boolean from the
 * fields beside it.
 *
 * <p><b>A set of facts.</b> The same fact reaching a parent by two paths is one fact — a rule this
 * compiler could not read leaves the partition measure short at every position it bears on — and a
 * parent that reported it twice would be counting the paths. So union is idempotent, commutative
 * and associative, and two of these holding the same facts are one value.
 *
 * <p><b>And the fact is what the thing it is about says a fact is, never the value carrying it.</b>
 * Three of the arms hold more than the fact: an observation carries where it was met, a rule with
 * no line carries the handle an author is sent to, and a question that stands carries that handle
 * and what each reading of it was short of. Every one of those is declared, at the type that holds
 * it, to be no part of what tells one from another — so folded on the value they are two facts, and
 * the count, the entries of a verdict and the place a reader is sent to all follow whichever the
 * walk met first. What those carry is accumulated instead, and the accumulation is commutative, so
 * a fact met at two places is one fact citing both.
 *
 * <p>Which arm holds what is settled here, in one {@code switch} with no {@code default}. An arm
 * added later is a compile error until somebody says whether the value it holds is the fact.
 *
 * <p><b>No order.</b> Nothing here says which fact comes before which. What a document prints them
 * in is the order that document publishes the kind in, decided where the crossing to a sequence is
 * made and not by whatever a walk put them in.
 *
 * <p>A value, because these travel inside {@code Db} answers. An answer that never equals its own
 * recomputation is one {@code Db} reports as changed on every run, and everything that read it runs
 * again over a model nobody edited — which {@code MeasureClosure.Closed} was already arranged
 * against.
 */
public final class WeakeningSet {

    private static final WeakeningSet NONE = new WeakeningSet(Set.of());

    /**
     * The facts, each once, and nothing about the order they were found in.
     *
     * <p>Held as what it is rather than as the map it is built through. One fact is one element
     * once the fold has happened, so equality is the set's — and a key beside them would be a
     * second thing for an answer to be equal on, of a type nothing about a weakening decides.
     */
    private final Set<Weakening> causes;

    private WeakeningSet(Set<Weakening> causes) {
        this.causes = causes;
    }

    /** Nothing weakened it, which is what a complete measurement has and what a measurement nobody
     *  asked for has. */
    public static WeakeningSet none() {
        return NONE;
    }

    public static WeakeningSet of(Weakening... causes) {
        return ofAll(List.of(causes));
    }

    public static WeakeningSet ofAll(Collection<? extends Weakening> causes) {
        return folded(causes);
    }

    /** The facts, each once. In no order: what tells them apart is what each of them is, and a
     *  reader that wants them in one has to say which one. */
    public Set<Weakening> causes() {
        return causes;
    }

    /** Both, as one. The identity is {@link #none()}, and a fact in both sides arrives once, cited
     *  at everywhere either side cited it. */
    public WeakeningSet union(WeakeningSet other) {
        if (other == null || other.causes.isEmpty()) {
            return this;
        }
        if (causes.isEmpty()) {
            return other;
        }
        List<Weakening> both = new ArrayList<>(causes);
        both.addAll(other.causes);
        return folded(both);
    }

    public boolean isEmpty() {
        return causes.isEmpty();
    }

    /** One entry per fact, with what evidenced each accumulated. The map is how the fold is done
     *  and is no part of what comes out of it. */
    private static WeakeningSet folded(Collection<? extends Weakening> causes) {
        Map<Object, Weakening> byFact = new HashMap<>();
        for (Weakening each : causes) {
            byFact.merge(factOf(each), each, WeakeningSet::merged);
        }
        return byFact.isEmpty() ? NONE : new WeakeningSet(Set.copyOf(byFact.values()));
    }

    /**
     * The reasons among these that are an observation gone missing.
     *
     * <p>Taking an arm of the sum out, and nothing more: what is here is what
     * {@link Weakening.ObservationIncomplete} holds, unchanged and unread. So it is not a second
     * interpretation of a weakening — it decides nothing about what any of these meant — and a
     * reader that wants the reasons has one way to get them.
     *
     * <p>Which matters because two readers want them for different things. A measure asks which of
     * these bear on it, by whatever rule is its own; a document prints them as the lines under a
     * behavior. Written out at each, the two would be two walks over one set, and a report's list
     * could hold a reason no measure carried.
     */
    public Set<Incompleteness.Met> observationCauses() {
        Set<Incompleteness.Met> out = new HashSet<>();
        for (Weakening each : causes) {
            if (each instanceof Weakening.ObservationIncomplete gap) {
                out.add(gap.met());
            }
        }
        return Set.copyOf(out);
    }

    /**
     * What tells one of these from another.
     *
     * <p>The value itself, wherever everything the arm holds is part of the fact. Where it holds
     * what evidenced the fact beside it, the fact alone — and which of the two an arm is, is the
     * arm's own declaration, asked of the type that made the distinction rather than repeated here.
     */
    private static Object factOf(Weakening one) {
        return switch (one) {
            case Weakening.ObservationIncomplete it -> it.met().fact();
            // The reading of the model says what one of its gaps is. Two places put these
            // together — what a measure's reading came to, and this — and a quotient written at
            // each would be two answers to one question.
            case Weakening.ModelReadingIncomplete it -> it.cause().fact();
            // The rest are their own fact: every value they hold is what a reader is told, and two
            // of them that are equal are one thing that went wrong.
            case Weakening.OutputCasesUnreadable _, Weakening.InputCasesUnreadable _,
                 Weakening.BorderValueUnreadable _, Weakening.BodiesNotElaborated _,
                 Weakening.BoundaryNotDerived _, Weakening.InputNotRead _,
                 Weakening.PairSpaceTruncated _, Weakening.ProofContradicted _,
                 Weakening.ArmsUnsettled _, Weakening.DecisionOfRowUnreadable _,
                 Weakening.DecisionRunNotWatched _,
                 Weakening.DecisionReadingIncomplete _ -> one;
        };
    }

    /** Two occurrences of one fact, as one. Commutative, so which was met first decides nothing. */
    private static Weakening merged(Weakening had, Weakening also) {
        return switch (had) {
            case Weakening.ObservationIncomplete it -> new Weakening.ObservationIncomplete(
                    it.met().mergedWith(alsoA(Weakening.ObservationIncomplete.class, also).met()));
            case Weakening.ModelReadingIncomplete it -> new Weakening.ModelReadingIncomplete(
                    ClosureGap.merged(it.cause(),
                            alsoA(Weakening.ModelReadingIncomplete.class, also).cause()));
            // Equal under the key and holding nothing but the fact, so both are the same value.
            case Weakening.OutputCasesUnreadable _, Weakening.InputCasesUnreadable _,
                 Weakening.BorderValueUnreadable _, Weakening.BodiesNotElaborated _,
                 Weakening.BoundaryNotDerived _, Weakening.InputNotRead _,
                 Weakening.PairSpaceTruncated _, Weakening.ProofContradicted _,
                 Weakening.ArmsUnsettled _, Weakening.DecisionOfRowUnreadable _,
                 Weakening.DecisionRunNotWatched _,
                 Weakening.DecisionReadingIncomplete _ -> had;
        };
    }

    /**
     * The other one, where it really is the same arm.
     *
     * <p>Two facts that are one fact are two of one arm, because what tells them apart is a value
     * of that arm's own. Said here rather than left to a cast, so that an arm added whose fact is
     * a type another already answers with is this and not a class this method turned out not to
     * hold.
     */
    private static <T extends Weakening> T alsoA(Class<T> arm, Weakening also) {
        if (arm.isInstance(also)) {
            return arm.cast(also);
        }
        throw new IllegalArgumentException(
                "two weakenings of one fact that are not one kind: " + arm + " and " + also);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WeakeningSet it && causes.equals(it.causes);
    }

    @Override
    public int hashCode() {
        return causes.hashCode();
    }

    @Override
    public String toString() {
        return causes.toString();
    }
}
