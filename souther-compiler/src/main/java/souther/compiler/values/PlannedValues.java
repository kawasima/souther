package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A reading of one value's positions, said in full before any of it is worked out.
 *
 * <p>What {@link AdmissibleValues} is, one step earlier. A position holds a description of the
 * values it admits ({@link AdmittedPlan}) rather than the values, and a choice this cannot tell the
 * branches of holds both. Turned into a reading by {@link #resolve}, which builds what has to be
 * built, once, under an allowance.
 *
 * <p><b>Why there are two types.</b> Whether the values are worked out decides which questions may
 * be asked: what a position admits, whether the reading speaks for it, whether anything stands
 * anywhere. Asked of this, every one of them would either be a guess or a machine built at the
 * moment somebody happened to ask — which is how what a clause costs came to depend on where its
 * brackets fell. So this holds only what the fold needs, and the answers live on the other side of
 * {@link #resolve}. There is no way back.
 *
 * @param <A> what a position is called
 */
public sealed interface PlannedValues<A> {

    /**
     * A reading whose shape is settled: these alternatives, or nothing at all.
     *
     * <p>The same nine things {@link AdmissibleValues} holds, with descriptions where it has sets.
     * What each of them is for is written there and is not written twice; what is different is that
     * a box may describe a set that turns out to be empty, since telling that is the work this
     * whole arrangement puts off.
     *
     * <p>And reached the way a reading is reached, for the reason written there: the parts state
     * relations to each other that no part states on its own, so they are this one's own and a
     * caller arrives at a description by writing one.
     */
    @souther.compiler.reading.StateOfAReading
    final class Settled<A> implements PlannedValues<A> {

        /** The parts, together — see {@link AdmissibleValues}, whose reasoning this is. */
        private record Parts<A>(PlannedHeld<A> held, Map<A, AdmittedPlan> perPosition,
                                Standing<A> standing,
                                Map<Sameness.Block<A>, AdmittedPlan> guaranteed,
                                AdmittedPlan defaultGuaranteed,
                                boolean guaranteedTogether, Set<Sameness.Block<A>> tangled,
                                Set<Sameness.Block<A>> widened) {

            private Parts {
                perPosition = said(perPosition);
                // A guarantee empty at one position is empty at all of them — see
                // {@link AdmissibleValues}. Only what is settled empty counts: a description
                // nobody has worked out is not a reason to throw a promise away, and where it does
                // turn out empty the resolved reading drops it then.
                if (held instanceof PlannedHeld.Nothing
                        || defaultGuaranteed instanceof AdmittedPlan.Nothing
                        || guaranteed.values().stream()
                                .anyMatch(AdmittedPlan.Nothing.class::isInstance)) {
                    guaranteed = Map.of();
                    defaultGuaranteed = AdmittedPlan.NONE;
                    guaranteedTogether = true;
                }
                guaranteed = Collections.unmodifiableMap(new LinkedHashMap<>(guaranteed));
                tangled = Collections.unmodifiableSet(new LinkedHashSet<>(tangled));
                widened = Collections.unmodifiableSet(new LinkedHashSet<>(widened));
                // Everything filed under a block, said in this reading's own terms — see
                // {@link AdmissibleValues}, whose reasoning this is.
                Sameness<A> mine = held instanceof PlannedHeld.Alternatives<A> it
                        ? it.commonSameness() : Sameness.discrete();
                mine.filing(guaranteed.keySet(), tangled, widened);
            }
        }

        private final Parts<A> parts;

        /** The one constructor there is, and it takes the parts as one — see
         *  {@link AdmissibleValues}, where a maker handed the parts is what may not be written. */
        private Settled(Parts<A> parts) {
            this.parts = parts;
        }

        /** What the rules leave: the alternatives, or nothing. */
        public PlannedHeld<A> held() {
            return parts.held();
        }

        /** What each position's own rules leave it, every alternative merged. */
        public Map<A, AdmittedPlan> perPosition() {
            return parts.perPosition();
        }

        /** What the rules of the model could not say, and what stopped this reading saying it. */
        @Override
        public Standing<A> standing() {
            return parts.standing();
        }

        /** What each block is guaranteed to admit. */
        public Map<Sameness.Block<A>, AdmittedPlan> guaranteed() {
            return parts.guaranteed();
        }

        /** What a position this holds no guarantee for is guaranteed to admit. */
        public AdmittedPlan defaultGuaranteed() {
            return parts.defaultGuaranteed();
        }

        /** Whether the guarantees are about whole values. */
        public boolean guaranteedTogether() {
            return parts.guaranteedTogether();
        }

        /** The blocks whose correlations this reading has lost. */
        public Set<Sameness.Block<A>> tangled() {
            return parts.tangled();
        }

        /** The blocks whose answer cannot be guaranteed to be what the read rules leave them. */
        public Set<Sameness.Block<A>> widened() {
            return parts.widened();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Settled<?> it && parts.equals(it.parts);
        }

        /** What it holds, as this kind of value — see {@link ValueHash}. The parts are a record,
         *  and a record's number is one its last component joins unchanged, so handing that up is
         *  handing up a number whatever hashes this next can still take apart. */
        @Override
        public int hashCode() {
            return ValueHash.ofOnePart(Settled.class, parts.hashCode());
        }

        @Override
        public String toString() {
            return parts.toString();
        }
    }

    /** Nothing read and nothing missed, which is what a reading starts from. */
    static <A> PlannedValues<A> top() {
        return new Settled<>(new Settled.Parts<>(
                PlannedHeld.one(PlannedHeld.Alternative.at(Map.of())), Map.of(),
                Standing.nothing(), Map.of(), AdmittedPlan.ANY, true, Set.of(), Set.of()));
    }

    /** One position said to admit what {@code plan} describes, and nothing missed. */
    static <A> PlannedValues<A> at(A atom, AdmittedPlan plan) {
        Map<A, AdmittedPlan> said = Map.of(atom, plan);
        return new Settled<>(new Settled.Parts<>(
                plan instanceof AdmittedPlan.Nothing ? new PlannedHeld.Nothing<>()
                        : PlannedHeld.one(PlannedHeld.Alternative.at(said)),
                said, Standing.nothing(), Map.of(Sameness.Block.of(atom), plan),
                AdmittedPlan.ANY, true, Set.of(), Set.of()));
    }

    /**
     * Two positions said to hold one value, which is what an equality between them states.
     *
     * <p>Nothing is narrowed anywhere: what this says is that the two are one side of the product,
     * so whatever either of them is stated to admit is what both admit. Which is why it is a
     * reading of the values at all — a rule relating two positions was one nothing here could take
     * in, and what it left was two positions with two answers and a rule between them that reached
     * nothing.
     */
    static <A> PlannedValues<A> holdingAsOne(A here, A there) {
        Sameness.Block<A> block = Sameness.of(here, there).blockOf(here);
        // Promised at the block, though it narrows nothing there. The keys of the promise are the
        // footprint as well — the blocks a rule of this reading reached — and this rule reached
        // one: it shapes the relation without touching what any position admits. Left out, a
        // choice between two equalities would be a union of two relations that no product holds
        // and would say it lost nothing, since what it reads to decide that is this key set.
        return new Settled<>(new Settled.Parts<>(
                PlannedHeld.one(PlannedHeld.Alternative.of(
                        new PlannedHeld.Box<>(Map.of(block, AdmittedPlan.ANY)))),
                Map.of(), Standing.nothing(), Map.of(block, AdmittedPlan.ANY),
                AdmittedPlan.ANY, true, Set.of(), Set.of()));
    }

    /**
     * Two positions said to hold different values, which is what a denial between them states.
     *
     * <p>Narrowing neither, the way an equality narrows neither: what a denial says is that no one
     * value stands at both, which is a relation between two sides of the product rather than a
     * statement about either. So it is held beside the product ({@link Apartness}) and what it
     * comes to is worked out where the values each side is left are in hand.
     *
     * <p><b>And guaranteeing nothing at either of them.</b> What is guaranteed is a lower bound —
     * these values are admitted whatever else is read — and no value can be shown admitted at one
     * of two positions held apart without an assignment for the other. Over a carrier of one value
     * a denial admits nothing at all, so a guarantee of every value would be false of a model
     * somebody can write. Said here rather than wherever a denial is met with something, because
     * meeting a guarantee with nothing leaves nothing: every conjunction this rule reaches has it
     * without a second rule saying so, and a choice beside it keeps what its other branch
     * guarantees, which is right — a value satisfying that branch is under no denial.
     */
    static <A> PlannedValues<A> heldApart(A here, A there) {
        Map<Sameness.Block<A>, AdmittedPlan> promised = new LinkedHashMap<>();
        promised.put(Sameness.Block.of(here), AdmittedPlan.NONE);
        promised.put(Sameness.Block.of(there), AdmittedPlan.NONE);
        return new Settled<>(new Settled.Parts<>(
                PlannedHeld.one(new PlannedHeld.Alternative<>(
                        new PlannedHeld.Box<>(Map.of()), Apartness.of(here, there))),
                Map.of(), Standing.nothing(), promised, AdmittedPlan.ANY, true,
                Set.of(), Set.of()));
    }

    /**
     * A rule this could not read, which says nothing about any position and spoils the ones it
     * names.
     *
     * <p>{@code named} may be empty — a rule reaching no position this can name is still a rule
     * that was not read, and that it was is a fact about the clause somebody wrote rather than
     * about any position of this reading ({@code Adoption}).
     *
     * <p>Nothing here was opened by an alternative. What this leaves at the positions it names is
     * its own account of them, said directly; that a choice above holds this clause in one of its
     * branches is that choice's fact, and reaches the positions from where the choice is.
     */
    static <A> PlannedValues<A> unreadable(Set<A> named, UnreadReason why) {
        // Nothing is guaranteed anywhere, and at the positions it does not name as much as at the
        // ones it does: what a rule this has no word for admits is not known, so a choice offering
        // it as an alternative is offering nothing that can be counted on.
        return new Settled<>(new Settled.Parts<>(
                PlannedHeld.one(PlannedHeld.Alternative.at(Map.of())), Map.of(),
                Standing.of(named, why), Map.of(), AdmittedPlan.NONE, true, Set.of(),
                Set.of()));
    }

    /**
     * Whether anything is admitted, as far as the descriptions say so on their own.
     *
     * <p>Free, and no allowance is touched. {@link Emptiness#UNDECIDED} wherever telling would take
     * building something — which is most of the time, and is the point: a decision made here is a
     * decision made on what the walk had reached.
     */
    default Emptiness emptiness() {
        return anyAlternativeAdmits((_, _) -> Emptiness.NONEMPTY);
    }

    /**
     * Whether an alternative survives a question asked of every position it names, out of the
     * descriptions alone.
     *
     * <p>{@link AdmissibleValues#anyAlternativeAdmits}'s walk over a reading that has not been
     * worked out, and the same rule: an alternative stands where every position of it still admits
     * something, and the reading stands where any alternative does.
     *
     * <p><b>The question is asked of a position only where answering it needs no machine.</b> A
     * description this has already got a set for is one the caller may be asked about; a pattern is
     * a machine somebody has to make, and making one here is the work this whole arrangement puts
     * off — so the position waits, and the alternative waits with it. What must not happen is
     * answering the caller's question from the description alone: a position whose plan is already
     * a set admits something and may still share none with what the caller knows, and a reading
     * that called that alternative live settled a branch nobody can be in as one somebody can.
     */
    default Emptiness anyAlternativeAdmits(AskedOfEachBlock<A> asked) {
        return switch (this) {
            case Settled<A> it -> switch (it.held()) {
                case PlannedHeld.Nothing<A> _ -> Emptiness.EMPTY;
                case PlannedHeld.Alternatives<A> boxes -> {
                    Emptiness any = Emptiness.identityForJoin();
                    for (PlannedHeld.Alternative<A> box : boxes.boxes()) {
                        Emptiness stands = Emptiness.identityForMeet();
                        for (Map.Entry<Sameness.Block<A>, AdmittedPlan> each
                                : box.at().entrySet()) {
                            stands = stands.met(askedOf(each.getKey(), each.getValue(), asked));
                            if (stands.endsAMeet()) {
                                break;
                            }
                        }
                        // And what its denials come to. A block stated to differ from itself is
                        // settled by reading the rule and needs no values, so it is settled here;
                        // everything else a denial says is settled against the values its blocks
                        // are left, and those are descriptions on this side of {@link #resolve}.
                        //
                        // Said of the alternative that holds them and not of the reading, which is
                        // the grain the question is asked at — an alternative beside one carrying a
                        // denial stands on its own rules.
                        //
                        // And held together with what the blocks came to, since an alternative
                        // stands where its blocks and its denials both leave it standing.
                        if (!stands.endsAMeet() && !box.apart().isEmpty()) {
                            stands = stands.met(box.apart().holdsABlockApartFromItself()
                                    ? Emptiness.EMPTY : Emptiness.UNDECIDED);
                        }
                        any = any.joined(stands);
                        if (any.endsAJoin()) {
                            yield any;
                        }
                    }
                    yield any;
                }
            };
        };
    }

    /**
     * The positions every alternative is refused at, out of the descriptions alone.
     *
     * <p>{@link AdmissibleValues#refusedInEveryAlternativeAt} of a reading that has not been worked
     * out, and the same rule: a position no alternative has a value at is one the lack can be named
     * at, and where the alternatives are refused at different positions there is none. A position
     * whose description this could not ask about is not one of them — it was not refused, it was not
     * asked.
     */
    default Refusal<A> refusedInEveryAlternativeAt(AskedOfEachBlock<A> asked) {
        if (!(this instanceof Settled<A> it
                && it.held() instanceof PlannedHeld.Alternatives<A> boxes)) {
            return Refusal.nowhere();
        }
        Refusal<A> everywhere = null;
        for (PlannedHeld.Alternative<A> box : boxes.boxes()) {
            Refusal<A> here = refusalIn(box, asked);
            if (here.isNowhere()) {
                return Refusal.nowhere();
            }
            everywhere = everywhere == null ? here : Refusal.shownByBoth(everywhere, here);
            if (everywhere.isNowhere()) {
                return Refusal.nowhere();
            }
        }
        return everywhere == null ? Refusal.nowhere() : everywhere;
    }

    /**
     * Where one alternative was refused, out of the descriptions alone.
     *
     * <p>The blocks and the relation alike, as
     * {@link AdmissibleValues#refusedInEveryAlternativeAt} does. What this side can say of a
     * relation is what needs no values: a block stated to differ from itself is refused by reading
     * the rule, and everything else a denial says waits for the sets.
     *
     * <p>Said here and not left to the reading below it. What an alternative was refused by is only
     * knowable while it is being refused, so an answer that dropped the alternative and worked out
     * afterwards why the reading holds nothing would find the general form.
     */
    private static <A> Refusal<A> refusalIn(PlannedHeld.Alternative<A> box,
                                            AskedOfEachBlock<A> asked) {
        Map<Sameness.Block<A>, AdmittedPlan> at = box.at();
        // The block and not its positions — see {@link AdmissibleValues}.
        return Refusal.ofAnAlternative(at,
                (block, plan) -> askedOf(block, plan, asked).isEmpty(),
                WhatARelationShows.statedApart(box.apart()));
    }

    /** What one block's description comes to under the question, waiting where a machine would
     *  have to be made to ask it. */
    private static <A> Emptiness askedOf(Sameness.Block<A> block, AdmittedPlan plan,
                                         AskedOfEachBlock<A> asked) {
        return switch (plan) {
            case AdmittedPlan.Nothing _ -> Emptiness.EMPTY;
            case AdmittedPlan.Everything _ -> asked.of(block, ValueSet.ANY);
            case AdmittedPlan.Of it -> asked.of(block, it.set());
            case AdmittedPlan.Pattern _, AdmittedPlan.Both _, AdmittedPlan.Either _ ->
                    Emptiness.UNDECIDED;
        };
    }

    /**
     * What a rule left standing at each position, and what stopped this reading taking it in.
     *
     * <p>Bookkeeping about the rules and not about the values, so it is here rather than on the
     * other side of {@link #resolve}: a caller answering for which rule stopped where is asking
     * about what was read, and that is known as soon as it is read. What resolve adds is the one
     * reason that is about the answer and not about a rule.
     */
    default Standing<A> standing() {
        return switch (this) {
            case Settled<A> it -> it.standing();
        };
    }

    /**
     * Whether nothing is admitted, taking in what {@code by} has already worked out as well.
     *
     * <p>{@link #emptiness} answers off the descriptions alone, and a language is a description of
     * a machine rather than the set it comes to — so two patterns nothing satisfies together read
     * as undecided there, and stay undecided until something builds them. This asks the same
     * question of the position's answer, which may have built exactly that: what a branch of a rule
     * came to is a plan, and where the reading of the whole value already worked that plan out, the
     * result is there to be read.
     *
     * <p>Nothing is built and nothing is spent. So a false here is "nothing established that this
     * is empty" and never "something stands in this" — a caller acting on it declines to claim
     * rather than claiming the opposite.
     */
    default boolean holdsNothingAsBuilt(Allowance<A> by) {
        if (emptiness().isEmpty()) {
            return true;
        }
        Sameness<A> heldAsOne = sameness();
        for (A atom : subjects()) {
            ValueSet known = by.known(heldAsOne.blockOf(atom), at(atom));
            if (known != null && known.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which positions this reading holds as one value, whatever alternative a value stands in —
     * see {@link AdmissibleValues#sameness}.
     *
     * <p>Worked out where it is asked rather than kept beside the alternatives. Nothing is built to
     * answer it, which is the difference between this side of {@link #resolve} and the other: there
     * the answer at a block is a set somebody paid for, and the coordinate it was paid for under
     * is held with it.
     */
    default Sameness<A> sameness() {
        return switch (this) {
            case Settled<A> it -> it.held() instanceof PlannedHeld.Alternatives<A> boxes
                    ? boxes.commonSameness() : Sameness.discrete();
        };
    }

    /**
     * What every alternative is described as refused by, which is what will have emptied the
     * reading if nothing else does.
     *
     * <p>Told from the descriptions and without building, which is how far this side of
     * {@link #resolve} can go: a plan settled at nothing is settled, and a plan nobody has worked
     * out says neither way. What a denial says needs no plan at all where its two ends are one
     * block, so that much of the relation is read here beside the blocks rather than after them.
     *
     * <p>Every alternative and not one of them, the same way a dead choice is put together: a
     * block one alternative is left nothing at is one another may stand at.
     *
     * <p>Less what a position answers for itself, this being the reading's own proof rather than
     * what refused an alternative — see {@link Refusal#withoutWhatAPositionAnswers}. Taken once
     * the alternatives have been put together, since a witness every one of them shows is one the
     * reading shows whether or not the reading is the place to keep it.
     */
    default Refusal<A> refusedBy() {
        if (!(this instanceof Settled<A> it
                && it.held() instanceof PlannedHeld.Alternatives<A> boxes)) {
            return Refusal.nowhere();
        }
        Refusal<A> everywhere = null;
        for (PlannedHeld.Alternative<A> box : boxes.boxes()) {
            Map<Sameness.Block<A>, AdmittedPlan> at = box.at();
            Refusal<A> said = Refusal.ofAnAlternative(at,
                    (_, plan) -> plan instanceof AdmittedPlan.Nothing,
                    WhatARelationShows.statedApart(box.apart()));
            everywhere = everywhere == null ? said : Refusal.shownByBoth(everywhere, said);
            if (everywhere.isNowhere()) {
                return Refusal.nowhere();
            }
        }
        return everywhere == null ? Refusal.nowhere()
                : everywhere.withoutWhatAPositionAnswers();
    }


    /**
     * The positions this reading narrowed, which is what a reader asking what it took in is asking.
     *
     * <p>Free, and no allowance is touched: a position was narrowed where what it holds is not
     * every value, and a description says which of those it is by being one shape or another.
     */
    default Set<A> adoptedAt() {
        Set<A> out = new LinkedHashSet<>();
        adoptedIn(this).forEach(atom -> {
            if (!(at(atom) instanceof AdmittedPlan.Everything)) {
                out.add(atom);
            }
        });
        return out;
    }

    private static <A> Set<A> adoptedIn(PlannedValues<A> of) {
        return switch (of) {
            case Settled<A> it -> adopted(it);
        };
    }

    /** Whether nothing satisfies these rules, so far as that is settled. */
    default boolean isBottom() {
        return emptiness().isEmpty();
    }

    /** The same blocks as the positions they are made of, which is the coordinate a reading with
     *  no alternatives left answers in. */
    private static <A> Set<Sameness.Block<A>> eachApart(Set<Sameness.Block<A>> these) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        these.forEach(block -> block.members().forEach(each -> out.add(Sameness.Block.of(each))));
        return out;
    }

    /** The same blocks, said in {@code into}'s coordinates — see
     *  {@link AdmissibleValues#mapped}. */
    private static <A> Set<Sameness.Block<A>> mapped(Set<Sameness.Block<A>> these,
                                                     Sameness<A> into) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        these.forEach(block -> block.members().forEach(each -> out.add(into.blockOf(each))));
        return out;
    }

    /**
     * Both readings holding at once.
     *
     * <p>Nothing is built and nothing can be refused: what two descriptions come to is a
     * description, so a conjunction costs what it costs to say and the saying is free.
     *
     * <p>A choice whose branches are not settled is not one of these. Which branch of a clause
     * survives is a question about the whole of what was read of it — the values and the order
     * together — so it is held a layer out, where both of those are ({@code StatedByClauses}), and
     * a conjunction distributes over it there.
     */
    default PlannedValues<A> meet(PlannedValues<A> other) {
        Settled<A> here = (Settled<A>) this;
        Settled<A> there = (Settled<A>) other;
        // Promising what both sides promise, where both promise their positions together — see
        // {@link AdmissibleValues#meet}, whose reasoning this is and which is not repeated.
        boolean apart = !here.guaranteedTogether() || !there.guaranteedTogether();
        PlannedHeld<A> both = met(here, there);
        // The coordinates the conjunction answers in, which are the two readings' equalities
        // conjoined and closed — see {@link AdmissibleValues#meet}.
        Sameness<A> heldAsOne = both instanceof PlannedHeld.Alternatives<A> it
                ? it.commonSameness() : Sameness.discrete();
        return new Settled<>(new Settled.Parts<>(both,
                narrowed(here.perPosition(), there.perPosition()),
                here.standing().and(there.standing()),
                // And nothing where nothing stands — see {@link AdmissibleValues#meet}.
                apart || !(both instanceof PlannedHeld.Alternatives<A>) ? Map.of()
                        : guaranteedMet(here, there, heldAsOne),
                apart ? AdmittedPlan.NONE
                        : AdmittedPlan.meeting(List.of(here.defaultGuaranteed(),
                                there.defaultGuaranteed())),
                true,
                mapped(both(here.tangled(), there.tangled()), heldAsOne),
                mapped(both(both(here.widened(), there.widened()),
                        both(here.tangled(), there.tangled())), heldAsOne)));
    }

    /**
     * The alternatives of a conjunction: every pair of one from each side.
     *
     * <p>Distributed rather than merged first, for the reason {@link AdmissibleValues} gives. What
     * is not done here is dropping the pairs nothing stands in: whether a pair stands is a question
     * about descriptions, and it is answered where they are worked out.
     */
    private static <A> PlannedHeld<A> met(Settled<A> here, Settled<A> there) {
        if (here.held() instanceof PlannedHeld.Nothing
                || there.held() instanceof PlannedHeld.Nothing) {
            return new PlannedHeld.Nothing<>();
        }
        Set<PlannedHeld.Alternative<A>> live = new LinkedHashSet<>();
        for (PlannedHeld.Alternative<A> one : alternatives(here)) {
            for (PlannedHeld.Alternative<A> two : alternatives(there)) {
                live.add(one.meet(two));
            }
        }
        return live.isEmpty() ? new PlannedHeld.Nothing<>()
                : new PlannedHeld.Alternatives<>(live);
    }

    private static <A> Set<PlannedHeld.Alternative<A>> alternatives(Settled<A> of) {
        return of.held() instanceof PlannedHeld.Alternatives<A> it ? it.boxes() : Set.of();
    }

    /** Both sides holding at each position, each side missing one holding every value. */
    private static <A> Map<A, AdmittedPlan> narrowed(Map<A, AdmittedPlan> these,
                                                     Map<A, AdmittedPlan> those) {
        Map<A, AdmittedPlan> out = new LinkedHashMap<>(these);
        those.forEach((atom, plan) ->
                out.merge(atom, plan, (one, other) -> AdmittedPlan.meeting(List.of(one, other))));
        return out;
    }

    /**
     * What both sides guarantee where the two are stated together, at every block either holds a
     * guarantee for — see {@link AdmissibleValues#guaranteedBy}.
     *
     * <p>A conjunction leaves a coarser relation, so a block here covers several of a side's own
     * and what that side promises is what it promises at all of them ({@link #promisedAcross}).
     */
    private static <A> Map<Sameness.Block<A>, AdmittedPlan> guaranteedMet(
            Settled<A> here, Settled<A> there, Sameness<A> heldAsOne) {
        Refinement<A> mine = Refinement.of(here.sameness(), heldAsOne);
        Refinement<A> theirs = Refinement.of(there.sameness(), heldAsOne);
        Map<Sameness.Block<A>, AdmittedPlan> out = new LinkedHashMap<>();
        named(here, there, heldAsOne).forEach(each -> {
            List<AdmittedPlan> promised = promisesFor(here, mine, each);
            promised.addAll(promisesFor(there, theirs, each));
            out.put(each, AdmittedPlan.meeting(promised));
        });
        return out;
    }

    /**
     * What either side guarantees where one of the two holds, at every block either holds a
     * guarantee for.
     *
     * <p>A choice leaves a finer relation, so a block here is inside one block of a side's own and
     * what that side promises there is promised of it: whoever satisfies that alternative holds a
     * value from it at every position the block covers.
     */
    private static <A> Map<Sameness.Block<A>, AdmittedPlan> guaranteedJoined(
            Settled<A> here, Settled<A> there, Sameness<A> heldAsOne) {
        Refinement<A> mine = Refinement.of(heldAsOne, here.sameness());
        Refinement<A> theirs = Refinement.of(heldAsOne, there.sameness());
        Map<Sameness.Block<A>, AdmittedPlan> out = new LinkedHashMap<>();
        named(here, there, heldAsOne).forEach(each -> out.put(each, AdmittedPlan.joining(
                List.of(promisedAt(here, mine.coarseBlockOf(each)),
                        promisedAt(there, theirs.coarseBlockOf(each))))));
        return out;
    }

    /** The blocks of the relation being answered in that either side holds a guarantee for. */
    private static <A> Set<Sameness.Block<A>> named(Settled<A> here, Settled<A> there,
                                                    Sameness<A> heldAsOne) {
        Set<Sameness.Block<A>> out = mapped(here.guaranteed().keySet(), heldAsOne);
        out.addAll(mapped(there.guaranteed().keySet(), heldAsOne));
        return out;
    }

    /** What one reading promises at one of its own blocks, which is its default where it promised
     *  nothing there. */
    private static <A> AdmittedPlan promisedAt(Settled<A> of, Sameness.Block<A> own) {
        return of.guaranteed().getOrDefault(own, of.defaultGuaranteed());
    }

    /**
     * Every promise one reading made about the value {@code block} stands for — see
     * {@link AdmissibleValues#promisesFor}, whose reasoning this is.
     *
     * <p>The promises and not what they come to, though a description costs nothing to say. What a
     * block is promised is one meet over both sides, and one written here would be a bracket the
     * meet below has to be relied on to take out again.
     */
    private static <A> List<AdmittedPlan> promisesFor(Settled<A> of, Refinement<A> into,
                                                      Sameness.Block<A> block) {
        List<AdmittedPlan> out = new ArrayList<>();
        into.fineBlocksWithin(block).forEach(each -> out.add(promisedAt(of, each)));
        return out;
    }

    /** What each position holds across the alternatives, which a description makes free. */
    default AdmittedPlan at(A atom) {
        return switch (this) {
            case Settled<A> it -> across(it, atom);
        };
    }

    /** What one position holds across a settled reading's alternatives, which is what the block
     *  it is on holds in each of them. */
    private static <A> AdmittedPlan across(Settled<A> of, A atom) {
        return switch (of.held()) {
            case PlannedHeld.Nothing<A> _ -> of.perPosition().getOrDefault(atom, AdmittedPlan.ANY);
            case PlannedHeld.Alternatives<A> boxes -> AdmittedPlan.joining(
                    boxes.boxes().stream().map(box -> box.get(atom)).toList());
        };
    }


    /**
     * Either reading holding, the alternatives merged back into one product.
     *
     * <p>Over the positions both spoke about, since a position one of them left open is one the two
     * of them together leave open.
     *
     * <p>A choice between alternatives written at two positions is a union of two products, and no
     * product holds it — so merging is where the relation goes. It goes unnoticed: the projections
     * survive a union, because the projection of a union is the union of the projections, and it is
     * the next conjunction that spends what was lost. Two such readings are met one position at a
     * time, and a pair the two of them refuse between them is a pair neither intersection excludes.
     *
     * <p>What an alternative nothing could read left open is not said here, and cannot be. Which
     * positions those are turns on which branches anybody can be in, and that is settled over the
     * whole declaration — after this choice, out of what it and every other one came to. So it
     * arrives afterwards ({@link AdmissibleValues#alsoOpenedAt}), from the one walk that knows both
     * the alternatives an author wrote and what became of them.
     *
     * <p><b>Both branches are ones somebody can take.</b> Which branches those are is not decided
     * here. What a choice leaves turns on whether either branch admits anything, and that is a
     * question about the whole of what was read of the clause — the values and the order together —
     * so it is asked a layer out and this is called with the answer already in hand
     * ({@code StatedByClauses}). Asked here as well, the two would be two answers to one question,
     * and the one made of values alone would drop a branch the order refused and keep one the
     * values did.
     */
    default PlannedValues<A> joinLive(PlannedValues<A> other) {
        return joinedLive(other, false);
    }

    /**
     * Either reading holding, with the alternatives of the two held apart.
     *
     * <p>The same choice, read without merging what it leaves back into one product. Held apart, a
     * conjunction after it distributes over the alternatives pairwise ({@link #meet}), the pairs
     * nothing stands in drop out where the values are worked out ({@link #resolve}), and what is
     * left is what the rules leave. Which is why nothing is owed here: the union of two products is
     * what it is, and this states it rather than approximating it.
     *
     * <p>How many may be held is not this reading's to decide. What bounds them is settled from the
     * clauses before any of them is read ({@code ExpansionCost}), so that precision cannot turn on
     * how a fold was bracketed.
     *
     * <p>Both branches are ones somebody can take, by the rule {@link #joinLive} states.
     */
    default PlannedValues<A> joinLiveApart(PlannedValues<A> other) {
        return joinedLive(other, true);
    }

    /**
     * A choice neither branch of which anybody can take.
     *
     * <p>No branch speaks for the other, so answering with either would settle the proof by the
     * order the operands were written in. Nor may they be met: a meet is a conjunction and the
     * alternatives were never stated together.
     *
     * <p>What is left is the positions both of them leave nothing at, which is an answer about the
     * whole value. A block one branch was left nothing at is one the other may stand at, so what
     * the choice is left nothing at is what neither of them has a value for; where there is no such
     * position the choice still admits nothing, and says so of no position in particular.
     *
     * <p>This realises a decision rather than making one, and what arrives is the readings as they
     * were read. Which alternatives nobody can take is a fact of the whole clause, and a branch
     * these values are perfectly happy with is one the ranges may have refused — so the whole
     * admits nothing because the choice does, and a position is named only where every alternative
     * of these values left it empty. There is no operation for saying the first fact to a reading,
     * and there is nothing for one to do: a reading records what these rules left, and what a
     * composition of them settled is the composition's to hold.
     */
    default PlannedValues<A> bothDead(PlannedValues<A> other) {
        Settled<A> here = settled();
        Settled<A> there = other.settled();
        Map<A, AdmittedPlan> empty = new LinkedHashMap<>();
        adopted(here).forEach(atom -> {
            if (at(atom) instanceof AdmittedPlan.Nothing
                    && other.at(atom) instanceof AdmittedPlan.Nothing) {
                empty.put(atom, AdmittedPlan.NONE);
            }
        });
        return new Settled<>(new Settled.Parts<>(new PlannedHeld.Nothing<>(), empty,
                here.standing().and(there.standing()),
                Map.of(), AdmittedPlan.NONE, true,
                eachApart(both(here.tangled(), there.tangled())),
                eachApart(both(here.widened(), there.widened()))));
    }

    /**
     * A choice both branches of which stand, as one description.
     *
     * <p>What {@link #joinLive} and {@link #joinLiveApart} come to, which is where the rules of
     * each are written. Arithmetic over descriptions and not over sets, which is why it costs
     * nothing and can be done before anything is built.
     */
    private PlannedValues<A> joinedLive(PlannedValues<A> other, boolean apart) {
        Settled<A> here = settled();
        Settled<A> there = other.settled();
        PlannedHeld<A> held = apart ? apart(here, there) : merged(here, there);
        Sameness<A> heldAsOne = held instanceof PlannedHeld.Alternatives<A> it
                ? it.commonSameness() : Sameness.discrete();
        Map<Sameness.Block<A>, AdmittedPlan> covered = guaranteedJoined(here, there, heldAsOne);
        AdmittedPlan coveredElsewhere = AdmittedPlan.joining(
                List.of(here.defaultGuaranteed(), there.defaultGuaranteed()));
        // What an alternative nothing could read left open is not said here — see
        // {@link #joinLive}, which says where it arrives instead.
        Standing<A> spoiled = here.standing().and(there.standing());
        // A union of two products alike everywhere but at one place is the product with that place
        // widened, so the promise survives as one about whole values where the alternatives are
        // written at no more than one position between them. Anywhere else the union holds a value
        // from one alternative at one position beside a value from the other at another, which is a
        // combination neither of them stands for.
        //
        // Sufficient and not necessary, and deliberately so. A union is also a product where one
        // alternative promises everything the other does, and where the two differ at only one
        // position however many they are written at — and both of those compare the two boxes a
        // bracketing happened to put together, so a choice of three alternatives answers one way
        // written to the left and another to the right. Both were tried, and both broke that a
        // choice is one connective and not a tree. Coarse and the same either way is the trade,
        // and what it costs is a promise this could have kept rather than one it could not.
        Set<Sameness.Block<A>> shapedBy = mapped(promisedAt(here), heldAsOne);
        shapedBy.addAll(mapped(promisedAt(there), heldAsOne));
        return new Settled<>(new Settled.Parts<>(held,
                widenedBy(here.perPosition(), there.perPosition()), spoiled,
                covered, coveredElsewhere,
                here.guaranteedTogether() && there.guaranteedTogether() && shapedBy.size() <= 1,
                // Merging a union back into one product loses a relation among the blocks the
                // alternatives are written at, and outside those the two of them agree on
                // everything by saying nothing. Read by the same sufficient condition as the
                // promise above, so a choice at one block keeps both.
                apart || shapedBy.size() <= 1
                        ? mapped(both(here.tangled(), there.tangled()), heldAsOne)
                        : both(mapped(both(here.tangled(), there.tangled()), heldAsOne), shapedBy),
                mapped(both(here.widened(), there.widened()), heldAsOne)));
    }

    /** The alternatives of both, which is what the choice leaves where they are held apart. */
    private static <A> PlannedHeld<A> apart(Settled<A> here, Settled<A> there) {
        Set<PlannedHeld.Alternative<A>> boxes = new LinkedHashSet<>(alternatives(here));
        boxes.addAll(alternatives(there));
        return new PlannedHeld.Alternatives<>(boxes);
    }

    /** The one product holding both readings' alternatives, which is what a choice comes to while
     *  the alternatives are held one at a time. */
    private static <A> PlannedHeld<A> merged(Settled<A> here, Settled<A> there) {
        Sameness<A> heldAsOne = here.sameness().common(there.sameness());
        Map<Sameness.Block<A>, AdmittedPlan> out = new LinkedHashMap<>();
        Set<Sameness.Block<A>> named = new LinkedHashSet<>();
        adopted(here).forEach(atom -> named.add(heldAsOne.blockOf(atom)));
        Reached<A> mine = Reached.of(here, heldAsOne);
        Reached<A> yours = Reached.of(there, heldAsOne);
        for (Sameness.Block<A> block : named) {
            AdmittedPlan theirs = yours.at(block);
            // A block one side says nothing about is one the choice says nothing about, since a
            // value satisfying that side may hold anything there. The block is kept where it is
            // more than one position: what those positions being one value says stands whatever
            // they admit.
            if (theirs instanceof AdmittedPlan.Everything) {
                if (!block.isOne()) {
                    out.put(block, AdmittedPlan.ANY);
                }
                continue;
            }
            out.put(block, AdmittedPlan.joining(List.of(mine.at(block), theirs)));
        }
        // And what every alternative of both states to differ, which the choice states as well.
        // Merging a union into the smallest product containing it widens what the blocks hold; it
        // does not licence forgetting a rule both branches wrote, and a denial dropped here is one
        // no equality read beside the choice can be refused against.
        return PlannedHeld.one(new PlannedHeld.Alternative<>(new PlannedHeld.Box<>(out),
                Apartness.commonTo(List.of(apartInEveryAlternative(here, heldAsOne),
                        apartInEveryAlternative(there, heldAsOne)), heldAsOne)));
    }

    /** What every alternative of one reading states to differ, said at {@code finer} — see
     *  {@link Apartness#commonTo}. */
    private static <A> Apartness<A> apartInEveryAlternative(Settled<A> of, Sameness<A> finer) {
        return Apartness.commonTo(
                alternatives(of).stream().map(PlannedHeld.Alternative::apart).toList(), finer);
    }

    /** Either side holding at each position, which is what both spoke about. */
    private static <A> Map<A, AdmittedPlan> widenedBy(Map<A, AdmittedPlan> these,
                                                      Map<A, AdmittedPlan> those) {
        Map<A, AdmittedPlan> out = new LinkedHashMap<>();
        these.forEach((atom, plan) -> {
            AdmittedPlan there = those.get(atom);
            if (there != null) {
                out.put(atom, AdmittedPlan.joining(List.of(plan, there)));
            }
        });
        return out;
    }

    /** The positions an alternative beside this one may have widened — see
     *  {@link AdmissibleValues}. */
    private static <A> Set<Sameness.Block<A>> promisedAt(Settled<A> of) {
        return of.guaranteed().keySet();
    }

    /**
     * This as a settled reading, which a branch admitting nothing always is.
     *
     * <p>A choice is kept open only where neither branch is settled empty, so anything that reached
     * here by being empty is one. Written as a cast rather than as an arm, because the alternative
     * is an arm nothing can reach and a reader wondering what it would mean.
     */
    private Settled<A> settled() {
        if (this instanceof Settled<A> it) {
            return it;
        }
        throw new IllegalStateException("a choice nobody could settle is not a settled reading");
    }

    /** The positions this holds an answer about, in the order they were read. */
    private static <A> Set<A> adopted(Settled<A> of) {
        Set<A> out = new LinkedHashSet<>();
        switch (of.held()) {
            case PlannedHeld.Nothing<A> _ -> out.addAll(of.perPosition().keySet());
            case PlannedHeld.Alternatives<A> it ->
                    it.boxes().forEach(box -> out.addAll(box.positions()));
        }
        return out;
    }

    /**
     * The reading this describes, with everything it describes worked out.
     *
     * <p>The one way across, and there is no way back. What is built is built here, once, under
     * {@code by}'s allowance; a choice this could not settle is settled from the values, by the
     * rules a finished reading has always used; and where the allowance ran out, the position is
     * left holding every value with {@link UnreadReason#EXACT_VALUES_TOO_COSTLY} standing at it.
     *
     * <p>So a reading that has arrived has no decision left in it. Nothing downstream builds a
     * machine, and nothing downstream is holding a description of an answer as though it were one.
     *
     * <p><b>And what could not be built comes back beside it.</b> Whether the reading admits
     * anything, and which limit stopped this compiler, are settled by the same work — and a caller
     * given the values alone has to guess them from a set widened to everything, where every guess
     * is the wrong one. See {@link Realized}.
     */
    default Realized<A> resolve(Allowance<A> by) {
        return switch (this) {
            case Settled<A> it -> AdmissibleValues.realize(it, by);
        };
    }

    /**
     * The machines this reading asks for.
     *
     * <p>What a refusal is answered from. A machine is made under a position's allowance, where
     * every rule reaching the position has paid in, so which reading asked for the one that was
     * refused is a question the far end cannot answer — and this is what a reading holds so that it
     * can be answered here instead of guessed from which positions somebody named.
     *
     * <p>The patterns and not the places they were written. A machine is the pattern's, so a
     * reading that asked for one is one that asked for that pattern, and two readings asking for
     * the same pattern are two answerable for one refusal — which is what writing the same clause
     * twice comes to and is not something to tell apart here.
     */
    default Set<Asked<A>> asked() {
        Settled<A> it = settled();
        Set<Asked<A>> out = new LinkedHashSet<>();
        it.perPosition().forEach((atom, plan) -> asked(out, atom, plan));
        // The positions and not the blocks: a machine is asked for by a pattern somebody wrote at
        // a place, and where a rule holds two places as one value the pattern was written at each
        // of them.
        switch (it.held()) {
            case PlannedHeld.Nothing<A> _ -> { }
            case PlannedHeld.Alternatives<A> boxes -> boxes.boxes().forEach(box ->
                    box.at().forEach((block, plan) ->
                            block.members().forEach(atom -> asked(out, atom, plan))));
        }
        return out;
    }

    private static <A> void asked(Set<Asked<A>> out, A atom, AdmittedPlan plan) {
        plan.asked().forEach(each -> out.add(new Asked<>(atom, each)));
    }

    /**
     * One machine a reading asked for, and the position it was asked for.
     *
     * <p>Both, because a refusal is about both. A machine is the pattern's, so the same pattern
     * written into two rules is one machine that both asked for; an allowance is the position's, so
     * a machine refused while one position was worked out is nothing another position's rules
     * asked. Keyed by the pattern alone, a rule that wrote that pattern about one position was
     * handed a refusal that happened at another — the same shape as answering from the place, one
     * axis over.
     *
     * @param at the position whose answer the machine was being built for
     * @param plan the pattern whose machine it is
     */
    record Asked<A>(A at, souther.compiler.regex.PatternPlan plan) {

        public Asked {
            if (at == null || plan == null) {
                throw new IllegalArgumentException(
                        "a machine is asked for by a pattern, for a position");
            }
        }
    }

    /**
     * The same reading with more said about what stopped it.
     *
     * <p>For a caller that worked a branch out to decide something and has to keep what it learned.
     * A branch probed and not built is one this compiler could not show empty, and where it is kept
     * as a branch anybody might be in, the reason nobody knows has to be kept with it — dropped,
     * the reading says a position is open where what is true is that nothing looked.
     */
    default PlannedValues<A> alsoStanding(Standing<A> why) {
        if (why.isEmpty()) {
            return this;
        }
        Settled<A> it = settled();
        Sameness<A> heldAsOne = it.sameness();
        Set<Sameness.Block<A>> widened = new LinkedHashSet<>(it.widened());
        why.positions().forEach(atom -> widened.add(heldAsOne.blockOf(atom)));
        return new Settled<>(new Settled.Parts<>(it.held(), it.perPosition(),
                it.standing().and(why), it.guaranteed(), it.defaultGuaranteed(),
                it.guaranteedTogether(), it.tangled(), widened));
    }

    /** Every subject this reading is filed under — see {@link AdmissibleValues#subjects}. */
    default Set<A> subjects() {
        Set<A> out = new LinkedHashSet<>();
        switch (this) {
            case Settled<A> it -> {
                if (it.held() instanceof PlannedHeld.Alternatives<A> boxes) {
                    boxes.boxes().forEach(box -> out.addAll(box.positions()));
                }
                out.addAll(it.perPosition().keySet());
                out.addAll(it.standing().positions());
                members(it.guaranteed().keySet(), out);
                members(it.tangled(), out);
                members(it.widened(), out);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** The positions the blocks are of, which is what a reading is filed under whatever it holds
     *  them as. */
    private static <A> void members(Set<Sameness.Block<A>> these, Set<A> out) {
        these.forEach(block -> out.addAll(block.members()));
    }

    /** What was said, which is what is not every value: a position nothing narrowed is held by
     *  being absent, and holding it would make one reading two states. */
    private static <A> Map<A, AdmittedPlan> said(Map<A, AdmittedPlan> at) {
        Map<A, AdmittedPlan> out = new LinkedHashMap<>();
        at.forEach((atom, plan) -> {
            if (!(plan instanceof AdmittedPlan.Everything)) {
                out.put(atom, plan);
            }
        });
        return Collections.unmodifiableMap(out);
    }

    /** Every position of either, in the order they were recorded. */
    static <A> Set<A> both(Set<A> these, Set<A> those) {
        if (those.isEmpty()) {
            return these;
        }
        if (these.isEmpty()) {
            return those;
        }
        Set<A> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return out;
    }

}
