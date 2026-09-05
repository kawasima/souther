package souther.compiler.values;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Several readings of admissible values, held together and not multiplied together.
 *
 * <p>An {@link AdmissibleValues} is a union of products, so a conjunction of two of them distributes
 * — every alternative of one against every alternative of the other. That is right where the two
 * speak about the same positions, and it is what a declaration's own clauses are read with, under a
 * budget counted before any of them is read. Two readings that share no position are a different
 * case: no pair is ever dropped, every product is the two maps put side by side, and the {@code m ×
 * n} alternatives say exactly what the pair {@code (m, n)} says. Expanding there buys nothing and
 * costs the product.
 *
 * <p>Which is not a corner case once readings of several values are said together. Ten parameters of
 * a record whose clauses leave two alternatives are a thousand and twenty-four alternatives, and the
 * budget that admitted each reading was counted per declaration and has nothing to say about their
 * conjunction.
 *
 * <p><b>The factors have pairwise disjoint vocabularies.</b> That is the invariant, and it is what
 * makes the answers exact rather than approximate:
 *
 * <pre>
 *     anyAlternativeAdmits   every factor having one, since an alternative of the conjunction is
 *                            one alternative of each of them side by side
 *     at(subject)            the one factor that names it, since no other says anything about it
 * </pre>
 *
 * <p>So a conjunction is kept factored only where keeping it factored loses nothing. Where two
 * factors do share a position, the exact conjunction is the product, and it is {@link
 * AdmissibleValues#meet} that works it out — this decides where that is needed and never what it
 * comes to.
 *
 * <p><b>What is not promised.</b> Not that a conjunction is bounded. Two readings that name the same
 * position, each admitted under a budget of its own, still multiply when they are met, and nothing
 * here stops that: what is bounded is one declaration's reading, and a caller conjoining two of them
 * over one vocabulary is asking for their product. What is promised is that a conjunction pays the
 * product only where the positions actually meet.
 *
 * <p>No join. A choice between alternatives happens while one declaration is read, which is below
 * this and inside {@link AdmissibleValues}. A disjunction of two factored conjunctions would have to
 * expand them to be said at all, and nothing asks for one.
 */
public final class ConjoinedAdmissibleValues<A> {

    /** In the order the readings arrived. Nothing here answers with one of them, but what is written
     *  out of a compilation has to come out the same on two runs of it. */
    private final List<AdmissibleValues<A>> factors;
    /**
     * Which factor names each subject, worked out once.
     *
     * <p>Both the index the answers are read off and the thing that holds the invariant up: a
     * subject arriving from two factors is two readings that were called disjoint and are not, and
     * it is seen here rather than by a caller wondering why a position was answered from a reading
     * that says nothing about it. Built for every one of these, so a factoring that goes wrong is
     * refused where it is made and not where it is read.
     */
    private final Map<A, AdmissibleValues<A>> naming;

    /**
     * What put these readings together, and what will put them together with the next one.
     *
     * <p>Carried rather than passed in, because the allowance it holds belongs to the answer being
     * built and not to whoever is asking for the next meet. A conjunction is the one place two
     * readings of one declaration come together, so the composer that paid for the sets in it is
     * the composer the next conjunction has to spend from — handed a fresh one, two patterns at a
     * position would each be affordable and their product would be bought twice.
     *
     * <p>Null where nothing has been read, which is a conjunction with no factors and nothing to
     * put together.
     */
    private final Allowance<A> sets;

    private ConjoinedAdmissibleValues(List<AdmissibleValues<A>> factors, Allowance<A> sets) {
        this.factors = List.copyOf(factors);
        this.sets = sets;
        Map<A, AdmissibleValues<A>> named = new LinkedHashMap<>();
        for (AdmissibleValues<A> each : this.factors) {
            for (A subject : each.subjects()) {
                if (named.put(subject, each) != null) {
                    throw new IllegalStateException("`" + subject + "` is named by two of these"
                            + " readings, so they are not the disjoint factors this holds");
                }
            }
        }
        this.naming = Collections.unmodifiableMap(named);
    }

    /**
     * What put these sets together, for a reader that has to put one of them together with another.
     *
     * <p>Null where nothing was read. A reader holding that is holding every value at every
     * position, which is what it would have composed its way to anyway.
     */
    public Allowance<A> sets() {
        return sets;
    }

    /**
     * The same readings, spending what {@code sets} allows.
     *
     * <p>What an answer built out of other answers does with them. The readings below were put
     * together where each was read, each spending its own declaration's allowance; met into one
     * they are a different admitted set, over positions this caller names, and it is that set the
     * allowance has to be about. So the composer comes from whoever is building the answer, and the
     * readings are taken under it rather than each bringing its own.
     *
     * <p>Nothing already read is undone. What is being said is where the next machine is charged.
     */
    public ConjoinedAdmissibleValues<A> under(Allowance<A> sets) {
        return this.sets == sets ? this : new ConjoinedAdmissibleValues<>(factors, sets);
    }

    /** Nothing read, so every position holds every value — and nothing to put together. */
    public static <A> ConjoinedAdmissibleValues<A> top() {
        return new ConjoinedAdmissibleValues<>(List.of(), null);
    }

    /** One reading, which is a conjunction of one, beside what put its sets together. */
    public static <A> ConjoinedAdmissibleValues<A> of(AdmissibleValues<A> read, Allowance<A> sets) {
        return new ConjoinedAdmissibleValues<>(List.of(read), sets);
    }

    /**
     * The readings this holds, in the order they arrived.
     *
     * <p>Not public. How a conjunction is held apart is this type's own business — every question
     * anybody asks of it is asked of the conjunction and answered from whichever factor names the
     * subject — and a caller counting the factors would be reading a representation as though it
     * were an answer. What is here for is a test measuring that a conjunction of readings over
     * disjoint vocabularies is not multiplied into one.
     */
    List<AdmissibleValues<A>> factors() {
        return factors;
    }

    /**
     * Whether any reading has been taken into this.
     *
     * <p>For a caller that takes a reading in once and has to be able to say so — a state takes one
     * in against nothing having been taken in yet, and a second reading arriving there would replace
     * the first without a word.
     *
     * <p><b>Whether and not how many.</b> How many factors are here is how many vocabularies the
     * readings fell into, which is not how many readings there were: two readings that name the
     * same position are one factor, since the exact conjunction of those is their product. A count
     * of factors offered as a count of readings would be a count of the representation — which is
     * why {@link #factors} is not something a caller may have either.
     *
     * <p>Whether, on the other hand, is the same question of both. Normalising never leaves nothing
     * where a reading went in, so a conjunction has a factor exactly when a reading was taken.
     */
    public boolean hasReadings() {
        return !factors.isEmpty();
    }

    /**
     * Whether an alternative of the conjunction survives a question asked of every position.
     *
     * <p>Factor by factor, and never the product of them. The vocabularies are disjoint, so an
     * alternative of the conjunction is one alternative of each factor put side by side and the
     * positions each of them names are asked about in that factor alone: the conjunction stands
     * where every factor has an alternative that stands, and holds nothing where any factor has
     * none. Expanded into the product first, the same answer would be reached over as many
     * alternatives as the factors multiply to.
     *
     * <p>Nothing read is every value at every position, which stands whatever is asked — the
     * question is one about which values a rule leaves, and no rule left any.
     */
    public Emptiness anyAlternativeAdmits(AskedOfEachBlock<A> asked, AskedOfARelation<A> relating) {
        Emptiness stands = Emptiness.NONEMPTY;
        for (AdmissibleValues<A> each : factors) {
            stands = stands.met(each.anyAlternativeAdmits(asked, relating));
            if (stands == Emptiness.EMPTY) {
                return stands;
            }
        }
        return stands;
    }

    /**
     * The positions every alternative of the conjunction is refused at.
     *
     * <p>Read off each factor and put together. An alternative of the conjunction is one alternative
     * of every factor side by side, so it is refused at a position exactly where the factor naming
     * that position is — and a position every alternative of its own factor is refused at is one
     * every alternative of the conjunction is refused at.
     */
    public Refusal<A> refusedInEveryAlternativeAt(AskedOfEachBlock<A> asked,
                                                  AskedOfARelation<A> relating) {
        Set<Sameness.Block<A>> blocks = new LinkedHashSet<>();
        Refusal<A> together = null;
        for (AdmissibleValues<A> each : factors) {
            switch (each.refusedInEveryAlternativeAt(asked, relating)) {
                case Refusal.AtEachOf<A> it -> blocks.addAll(it.blocks());
                // The first of them, which is the first in the order the readings arrived and so
                // the order the rules were written. Every factor refused is refused of the
                // conjunction, so each of these is true of it and naming one is naming one of
                // several true things; put together, two lacks about two sets of blocks would say
                // that no assignment to all of them stands, which neither factor showed.
                case Refusal.OfThemTogether<A> it -> together =
                        together == null ? it : together;
                case Refusal.Nowhere<A> _ -> { }
            }
        }
        if (!blocks.isEmpty()) {
            return new Refusal.AtEachOf<>(blocks);
        }
        return together == null ? new Refusal.Nowhere<>() : together;
    }

    /**
     * Which values may stand at one subject.
     *
     * <p>Asked of the factor that names it and of no other. The rest do not name it, and a reading
     * that does not name a subject admits every value at it — so meeting their answers in would be
     * meeting with everything.
     */
    public ValueSet at(A subject) {
        AdmissibleValues<A> names = naming.get(subject);
        return names == null ? ValueSet.ANY : names.at(subject);
    }

    /**
     * Whether what is held at one subject is what the rules leave it, rather than something wider.
     *
     * <p>Asked of the factor that names it. A reading that does not name a subject did not widen
     * anything there — it holds every value at it exactly, having been told nothing — so a
     * conjunction no factor of which names the subject is exact at it.
     */
    public boolean projectionExactAt(A subject) {
        AdmissibleValues<A> names = naming.get(subject);
        return names == null || names.projectionExactAt(subject);
    }

    /**
     * Everything that stopped a subject's rules from being read, empty where nothing did.
     *
     * <p>Asked of the factor that names it, for the same reason: a reading that never heard of a
     * subject has no rule about it that went unread.
     */
    public List<UnreadReason> whyUnread(A subject) {
        AdmissibleValues<A> names = naming.get(subject);
        return names == null ? List.of() : names.whyUnread(subject);
    }

    /**
     * Which positions one subject is held as one value with, which is the coordinate its answer is
     * in.
     *
     * <p>Asked of the reading that names it. A subject no reading names is one nothing said an
     * equality about, so it is its own — and two readings kept apart never name one subject
     * between them, which is what lets this be the one reading's answer rather than a relation
     * assembled out of several.
     */
    public Sameness.Block<A> blockOf(A subject) {
        AdmissibleValues<A> names = naming.get(subject);
        return names == null ? Sameness.Block.of(subject) : names.blockOf(subject);
    }

    /** The blocks of several positions the rules left no value, over every reading held here —
     *  see {@link AdmissibleValues#emptiedBlocks}. */
    public Set<Sameness.Block<A>> emptiedBlocks() {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        factors.forEach(each -> out.addAll(each.emptiedBlocks()));
        return Collections.unmodifiableSet(out);
    }

    /** Every subject any factor names. */
    public Set<A> subjects() {
        return naming.keySet();
    }

    /**
     * Both conjunctions holding at once.
     *
     * <p>The factors of the two put together and then normalised: factors whose vocabularies meet
     * are one factor, and what that factor is is {@link AdmissibleValues#meet}'s to say.
     *
     * <p><b>Over connected components and not over pairs.</b> Merging two overlapping factors makes
     * a factor with both vocabularies, which may now meet a third that neither of them met — a
     * factor over {@code {a, b}} beside one over {@code {c}}, met with one over {@code {b, c}},
     * is one factor over all three. Merged pairwise in one pass, the result would hold two factors
     * that share {@code c}, and every answer that rests on the vocabularies being disjoint would be
     * answering from a factor that is not the only one naming its subject.
     */
    public ConjoinedAdmissibleValues<A> meet(ConjoinedAdmissibleValues<A> other) {
        if (other.factors.isEmpty()) {
            return this;
        }
        if (factors.isEmpty()) {
            return other;
        }
        // One answer is being built, so one composer is spending for it. Two readings that were put
        // together by different composers are two answers, and meeting them would charge a position
        // of one against the allowance of the other. An assertion because it is a fact about how
        // this compiler reads a declaration rather than about any model.
        assert sets == other.sets
                : "two readings put together by different composers are two answers";
        List<AdmissibleValues<A>> both = new ArrayList<>(factors);
        both.addAll(other.factors);
        return new ConjoinedAdmissibleValues<>(byComponent(both, sets), sets);
    }

    /**
     * The same readings about the same subjects, under the names {@code naming} gives them.
     *
     * <p>Factor by factor, and the vocabularies stay disjoint because the naming names two subjects
     * two subjects — which is the caller's to hold to and is what
     * {@code souther.compiler.check.InjectiveRenaming} is.
     */
    public <B> ConjoinedAdmissibleValues<B> renamed(Function<A, B> naming) {
        List<AdmissibleValues<B>> out = new ArrayList<>(factors.size());
        factors.forEach(each -> out.add(each.renamed(naming)));
        // The allowances go with the names. It is the same answer being built, so what a position
        // has spent is what it has spent whichever vocabulary it is filed under.
        return new ConjoinedAdmissibleValues<>(out, sets == null ? null : sets.renamed(naming));
    }

    /**
     * One factor per connected component of "these two name a subject in common".
     *
     * <p><b>Which factors are one is found by walking; the order they are met in is not.</b> The
     * walk reaches a component's members through whichever subject it happens to look at first, and
     * met as they are reached, a component of three would be folded in the order its vocabularies
     * were iterated. {@link AdmissibleValues#meet} does not answer the same either way — what stopped
     * a position's rules from being read is the first reason given for it, so two readings that
     * both went unread at one position are told apart by which of them was met first. Read off a
     * walk, that reason would be settled by which subject a factor's vocabulary happened to list
     * first rather than by which reading arrived first.
     *
     * <p>So the members are collected, and then the readings are folded in the order they arrived.
     * Connectivity decides who is met with whom; arrival decides in what order.
     */
    private static <A> List<AdmissibleValues<A>> byComponent(List<AdmissibleValues<A>> of,
                                                             Allowance<A> sets) {
        List<Set<A>> vocabularies = new ArrayList<>(of.size());
        of.forEach(each -> vocabularies.add(each.subjects()));
        // Which factors name each subject, which is what says two of them are in one component
        // without every pair of them being compared.
        Map<A, List<Integer>> naming = new LinkedHashMap<>();
        for (int at = 0; at < of.size(); at++) {
            int here = at;
            vocabularies.get(at).forEach(
                    subject -> naming.computeIfAbsent(subject, _ -> new ArrayList<>()).add(here));
        }
        List<AdmissibleValues<A>> out = new ArrayList<>();
        boolean[] taken = new boolean[of.size()];
        for (int at = 0; at < of.size(); at++) {
            if (taken[at]) {
                continue;
            }
            // Every reading this one reaches, gathered before any of them is met.
            SortedSet<Integer> members = new TreeSet<>();
            taken[at] = true;
            members.add(at);
            Deque<Integer> reaching = new ArrayDeque<>();
            reaching.add(at);
            while (!reaching.isEmpty()) {
                for (A subject : vocabularies.get(reaching.remove())) {
                    for (int also : naming.getOrDefault(subject, List.of())) {
                        if (!taken[also]) {
                            taken[also] = true;
                            members.add(also);
                            reaching.add(also);
                        }
                    }
                }
            }
            // And then met. Handed over in the order they arrived, which is the order what they
            // say about each position is written down in; which of them is built first is settled
            // in there, from what they are — see {@link AdmissibleValues#metAll}.
            out.add(AdmissibleValues.metAll(members.stream().map(of::get).toList(), sets));
        }
        return out;
    }

    @Override
    public String toString() {
        return factors.isEmpty() ? "everything" : String.join(" and ", factors.stream()
                .map(Object::toString).toList());
    }
}
