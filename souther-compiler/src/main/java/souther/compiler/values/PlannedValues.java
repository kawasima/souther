package souther.compiler.values;

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
     */
    record Settled<A>(PlannedHeld<A> held, Map<A, AdmittedPlan> perPosition,
                      Standing<A> standing,
                      Map<Sameness.Block<A>, AdmittedPlan> guaranteed,
                      AdmittedPlan defaultGuaranteed,
                      boolean guaranteedTogether, Set<Sameness.Block<A>> tangled,
                      Set<Sameness.Block<A>> widened) implements PlannedValues<A> {

        public Settled {
            perPosition = said(perPosition);
            // A guarantee empty at one position is empty at all of them — see
            // {@link AdmissibleValues}. Only what is settled empty counts: a description nobody has
            // worked out is not a reason to throw a promise away, and where it does turn out empty
            // the resolved reading drops it then.
            if (held instanceof PlannedHeld.Nothing
                    || defaultGuaranteed instanceof AdmittedPlan.Nothing
                    || guaranteed.values().stream().anyMatch(AdmittedPlan.Nothing.class::isInstance)) {
                guaranteed = Map.of();
                defaultGuaranteed = AdmittedPlan.NONE;
                guaranteedTogether = true;
            }
            guaranteed = Collections.unmodifiableMap(new LinkedHashMap<>(guaranteed));
            tangled = Collections.unmodifiableSet(new LinkedHashSet<>(tangled));
            widened = Collections.unmodifiableSet(new LinkedHashSet<>(widened));
            // Everything filed under a block, said in this reading's own coordinates — see
            // {@link AdmissibleValues}, whose reasoning this is.
            Sameness<A> mine = held instanceof PlannedHeld.Alternatives<A> it
                    ? commonTo(it) : Sameness.discrete();
            mine.filing(guaranteed.keySet(), tangled, widened);
        }
    }

    /** Nothing read and nothing missed, which is what a reading starts from. */
    static <A> PlannedValues<A> top() {
        return new Settled<>(PlannedHeld.one(PlannedHeld.Box.at(Map.of())), Map.of(),
                Standing.nothing(), Map.of(), AdmittedPlan.ANY, true, Set.of(), Set.of());
    }

    /** One position said to admit what {@code plan} describes, and nothing missed. */
    static <A> PlannedValues<A> at(A atom, AdmittedPlan plan) {
        Map<A, AdmittedPlan> said = Map.of(atom, plan);
        return new Settled<>(
                plan instanceof AdmittedPlan.Nothing ? new PlannedHeld.Nothing<>()
                        : PlannedHeld.one(PlannedHeld.Box.at(said)),
                said, Standing.nothing(), Map.of(Sameness.Block.of(atom), plan),
                AdmittedPlan.ANY, true, Set.of(), Set.of());
    }

    /**
     * Two positions said to hold one value — see {@link AdmissibleValues#holdingAsOne}.
     */
    static <A> PlannedValues<A> holdingAsOne(A here, A there) {
        Sameness.Block<A> block = Sameness.of(here, there).blockOf(here);
        // Promised at the block, though it narrows nothing there — see
        // {@link AdmissibleValues#holdingAsOne}.
        return new Settled<>(
                PlannedHeld.one(new PlannedHeld.Box<>(Map.of(block, AdmittedPlan.ANY))),
                Map.of(), Standing.nothing(), Map.of(block, AdmittedPlan.ANY),
                AdmittedPlan.ANY, true, Set.of(), Set.of());
    }

    /**
     * Two positions said to hold different values — see {@link AdmissibleValues#heldApart}.
     */
    static <A> PlannedValues<A> heldApart(A here, A there) {
        Map<Sameness.Block<A>, AdmittedPlan> promised = new LinkedHashMap<>();
        promised.put(Sameness.Block.of(here), AdmittedPlan.NONE);
        promised.put(Sameness.Block.of(there), AdmittedPlan.NONE);
        return new Settled<>(
                PlannedHeld.one(new PlannedHeld.Alternative<>(
                        new PlannedHeld.Box<>(Map.of()), Apartness.of(here, there))),
                Map.of(), Standing.nothing(), promised, AdmittedPlan.ANY, true,
                Set.of(), Set.of());
    }

    /**
     * A rule this could not read, which says nothing about any position and spoils the ones it
     * names — see {@link AdmissibleValues#unreadable}.
     */
    static <A> PlannedValues<A> unreadable(Set<A> named, UnreadReason why) {
        return new Settled<>(PlannedHeld.one(PlannedHeld.Box.at(Map.of())), Map.of(),
                Standing.of(named, why), Map.of(), AdmittedPlan.NONE, true, Set.of(),
                Set.of());
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
                    Emptiness any = Emptiness.EMPTY;
                    for (PlannedHeld.Alternative<A> box : boxes.boxes()) {
                        Emptiness stands = Emptiness.NONEMPTY;
                        for (Map.Entry<Sameness.Block<A>, AdmittedPlan> each
                                : box.at().entrySet()) {
                            stands = stands.met(askedOf(each.getKey(), each.getValue(), asked));
                            if (stands == Emptiness.EMPTY) {
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
                        if (stands != Emptiness.EMPTY && !box.apart().isEmpty()) {
                            stands = box.apart().holdsABlockApartFromItself()
                                    ? Emptiness.EMPTY : Emptiness.UNDECIDED;
                        }
                        any = any.joined(stands);
                        if (any == Emptiness.NONEMPTY) {
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
            return new Refusal.Nowhere<>();
        }
        Refusal<A> everywhere = null;
        for (PlannedHeld.Alternative<A> box : boxes.boxes()) {
            Refusal<A> here = refusalIn(box, asked);
            if (here.isNowhere()) {
                return new Refusal.Nowhere<>();
            }
            everywhere = everywhere == null ? here : Refusal.shownByBoth(everywhere, here);
            if (everywhere.isNowhere()) {
                return new Refusal.Nowhere<>();
            }
        }
        return everywhere == null ? new Refusal.Nowhere<>() : everywhere;
    }

    /**
     * Where one alternative was refused, out of the descriptions alone.
     *
     * <p>The blocks first and the relation after, as
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
        Set<Sameness.Block<A>> here = new LinkedHashSet<>();
        // The block and not its positions — see {@link AdmissibleValues}.
        box.at().forEach((block, plan) -> {
            if (askedOf(block, plan, asked) == Emptiness.EMPTY) {
                here.add(block);
            }
        });
        if (!here.isEmpty()) {
            return new Refusal.AtEachOf<>(here);
        }
        for (Apartness.Edge<A> edge : box.apart().edges()) {
            if (edge.isOfOneBlock()) {
                return new Refusal.OfThemTogether<>(
                        new RelationalWitness.ABlockApartFromItself<>(edge.one()));
            }
        }
        return new Refusal.Nowhere<>();
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
        if (emptiness() == Emptiness.EMPTY) {
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
                    ? commonTo(boxes) : Sameness.discrete();
        };
    }

    /**
     * The blocks of more than one position every alternative describes as admitting nothing,
     * which is what will have emptied the reading if nothing else does.
     *
     * <p>Told from the descriptions and without building, which is how far this side of
     * {@link #resolve} can go: a plan settled at nothing is settled, and a plan nobody has worked
     * out says neither way. Which is enough for the two facts this proof rests on — that a value
     * several positions share has none, and that each of them has something on its own.
     *
     * <p>Every alternative and not one of them, the same way a dead choice is put together: a
     * block one alternative is left nothing at is one another may stand at.
     */
    default Set<Sameness.Block<A>> emptiedBlocks() {
        if (!(this instanceof Settled<A> it
                && it.held() instanceof PlannedHeld.Alternatives<A> boxes)) {
            return Set.of();
        }
        Set<Sameness.Block<A>> everywhere = null;
        for (PlannedHeld.Alternative<A> box : boxes.boxes()) {
            Set<Sameness.Block<A>> here = new LinkedHashSet<>();
            box.at().forEach((block, plan) -> {
                if (!block.isOne() && plan instanceof AdmittedPlan.Nothing) {
                    here.add(block);
                }
            });
            if (everywhere == null) {
                everywhere = here;
            } else {
                everywhere.retainAll(here);
            }
            if (everywhere.isEmpty()) {
                return Set.of();
            }
        }
        return everywhere == null ? Set.of() : Collections.unmodifiableSet(everywhere);
    }

    /** What every alternative holds as one value. */
    private static <A> Sameness<A> commonTo(PlannedHeld.Alternatives<A> boxes) {
        Sameness<A> out = null;
        for (PlannedHeld.Alternative<A> box : boxes.boxes()) {
            out = out == null ? box.sameness() : out.common(box.sameness());
        }
        return out == null ? Sameness.discrete() : out;
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
        return emptiness() == Emptiness.EMPTY;
    }

    /**
     * This where it already admits nothing, and a reading admitting nothing where it does not —
     * see {@link AdmissibleValues#leavingNothing}.
     */
    default PlannedValues<A> leavingNothing() {
        if (isBottom()) {
            return this;
        }
        return switch (this) {
            case Settled<A> it -> new Settled<>(new PlannedHeld.Nothing<>(), Map.of(),
                    it.standing(), Map.of(), AdmittedPlan.NONE, true,
                    eachApart(it.tangled()), eachApart(it.widened()));
        };
    }

    /** The same blocks as the positions they are made of, which is the coordinate a reading with
     *  no alternatives left answers in — see {@link AdmissibleValues#leavingNothing}. */
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
                ? commonTo(it) : Sameness.discrete();
        return new Settled<>(both,
                narrowed(here.perPosition(), there.perPosition()),
                here.standing().and(there.standing()),
                apart ? Map.of() : guaranteedBy(here, there, heldAsOne, true),
                apart ? AdmittedPlan.NONE
                        : AdmittedPlan.meeting(List.of(here.defaultGuaranteed(),
                                there.defaultGuaranteed())),
                true,
                mapped(both(here.tangled(), there.tangled()), heldAsOne),
                mapped(both(both(here.widened(), there.widened()),
                        both(here.tangled(), there.tangled())), heldAsOne));
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

    /** What both sides guarantee, at every block either holds a guarantee for, each side
     *  missing one standing at its own default — see {@link AdmissibleValues#guaranteedBy}. */
    private static <A> Map<Sameness.Block<A>, AdmittedPlan> guaranteedBy(
            Settled<A> here, Settled<A> there, Sameness<A> heldAsOne, boolean met) {
        Set<Sameness.Block<A>> named = mapped(here.guaranteed().keySet(), heldAsOne);
        named.addAll(mapped(there.guaranteed().keySet(), heldAsOne));
        Map<Sameness.Block<A>, AdmittedPlan> out = new LinkedHashMap<>();
        named.forEach(each -> {
            List<AdmittedPlan> two = List.of(promisedFor(here, each), promisedFor(there, each));
            out.put(each, met ? AdmittedPlan.meeting(two) : AdmittedPlan.joining(two));
        });
        return out;
    }

    /** What one reading promises the value {@code block} stands for, whichever blocks of its own
     *  it holds those positions in. */
    private static <A> AdmittedPlan promisedFor(Settled<A> of, Sameness.Block<A> block) {
        return of.guaranteed().getOrDefault(
                of.sameness().blockOf(block.members().iterator().next()), of.defaultGuaranteed());
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
     * Either reading holding, both branches being ones somebody can take.
     *
     * <p>Which branches those are is not decided here. What a choice leaves turns on whether either
     * branch admits anything, and that is a question about the whole of what was read of the clause
     * — the values and the order together — so it is asked a layer out and this is called with the
     * answer already in hand ({@code StatedByClauses}). Asked here as well, the two would be two
     * answers to one question, and the one made of values alone would drop a branch the order
     * refused and keep one the values did.
     */
    default PlannedValues<A> joinLive(PlannedValues<A> other) {
        return joinedLive(other, false);
    }

    /** Either reading holding, with the alternatives of the two held apart — see
     *  {@link AdmissibleValues#joinApart}. Both branches are ones somebody can take, by the rule
     *  {@link #joinLive} states. */
    default PlannedValues<A> joinLiveApart(PlannedValues<A> other) {
        return joinedLive(other, true);
    }

    /**
     * A choice neither branch of which anybody can take.
     *
     * <p>No branch speaks for the other, so answering with either would settle the proof by the
     * order the operands were written in. What is left is the positions both of them leave nothing
     * at, which is an answer about the whole value — see {@link AdmissibleValues#joinApart}, whose
     * reasoning this is.
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
        return new Settled<>(new PlannedHeld.Nothing<>(), empty,
                here.standing().and(there.standing()),
                Map.of(), AdmittedPlan.NONE, true,
                eachApart(both(here.tangled(), there.tangled())),
                eachApart(both(here.widened(), there.widened())));
    }

    /**
     * A choice both branches of which stand, as one description.
     *
     * <p>The rules are {@link AdmissibleValues#joinApart}'s and the reasoning is written there.
     * What is here is the same arithmetic over descriptions rather than sets, which is why it costs
     * nothing and can be done before anything is built.
     */
    private PlannedValues<A> joinedLive(PlannedValues<A> other, boolean apart) {
        Settled<A> here = settled();
        Settled<A> there = other.settled();
        PlannedHeld<A> held = apart ? apart(here, there) : merged(here, there);
        Sameness<A> heldAsOne = held instanceof PlannedHeld.Alternatives<A> it
                ? commonTo(it) : Sameness.discrete();
        Map<Sameness.Block<A>, AdmittedPlan> covered = guaranteedBy(here, there, heldAsOne, false);
        AdmittedPlan coveredElsewhere = AdmittedPlan.joining(
                List.of(here.defaultGuaranteed(), there.defaultGuaranteed()));
        // What an alternative nothing could read left open is not said here — see
        // {@link AdmissibleValues#join}, whose reasoning this is.
        Standing<A> spoiled = here.standing().and(there.standing());
        Set<Sameness.Block<A>> shapedBy = mapped(promisedAt(here), heldAsOne);
        shapedBy.addAll(mapped(promisedAt(there), heldAsOne));
        return new Settled<>(held,
                widenedBy(here.perPosition(), there.perPosition()), spoiled,
                covered, coveredElsewhere,
                here.guaranteedTogether() && there.guaranteedTogether() && shapedBy.size() <= 1,
                apart || shapedBy.size() <= 1
                        ? mapped(both(here.tangled(), there.tangled()), heldAsOne)
                        : both(mapped(both(here.tangled(), there.tangled()), heldAsOne), shapedBy),
                mapped(both(here.widened(), there.widened()), heldAsOne));
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
        for (Sameness.Block<A> block : named) {
            A member = block.members().iterator().next();
            AdmittedPlan theirs = across(there, member);
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
            out.put(block, AdmittedPlan.joining(List.of(across(here, member), theirs)));
        }
        return PlannedHeld.one(new PlannedHeld.Box<>(out));
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
            case Settled<A> it -> resolved(it, by);
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

    private static <A> Realized<A> resolved(Settled<A> of, Allowance<A> by) {
        Unbuilt<A> gaveUp = new Unbuilt<>();
        Map<A, ValueSet> perPosition = realized(of.perPosition(), of.sameness(), by, gaveUp);
        AdmissibleValues.Held<A> held = switch (of.held()) {
            case PlannedHeld.Nothing<A> _ -> new AdmissibleValues.Held.Nothing<A>();
            case PlannedHeld.Alternatives<A> boxes -> alternatives(boxes, by, gaveUp);
        };
        // The coordinates the answer is in, which are not the ones it was described in. An
        // alternative dropped for admitting nothing is one whose equalities the rest need not
        // state, so what the survivors hold as one may be coarser than what every description did
        // — and everything filed under a block is said in the answer's own before it is built.
        Sameness<A> heldAsOne = held instanceof AdmissibleValues.Held.Alternatives<A> it
                ? it.commonSameness() : Sameness.discrete();
        Map<Sameness.Block<A>, AdmittedPlan> promising = new LinkedHashMap<>();
        of.guaranteed().forEach((block, plan) -> promising.merge(
                heldAsOne.blockOf(block.members().iterator().next()), plan,
                (one, other) -> AdmittedPlan.meeting(List.of(one, other))));
        return new Realized<>(new AdmissibleValues<>(held, perPosition,
                gaveUp.beside(of.standing()),
                promised(promising, by), promised(of.defaultGuaranteed(), by.elsewhere()),
                of.guaranteedTogether(),
                mapped(of.tangled(), heldAsOne),
                mapped(both(of.widened(), gaveUp.names()), heldAsOne)),
                gaveUp.aboutARule(), gaveUp.aboutTheAnswer());
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
        return new Settled<>(it.held(), it.perPosition(), it.standing().and(why),
                it.guaranteed(), it.defaultGuaranteed(), it.guaranteedTogether(), it.tangled(),
                widened);
    }

    /**
     * The alternatives, with the ones nothing stands in dropped.
     *
     * <p>Where the invariant a settled reading has is kept: a box with a side admitting nothing
     * stands for nothing, and now that the sides are values it can be seen and taken out. Where
     * every box goes, nothing satisfies the rules.
     */
    private static <A> AdmissibleValues.Held<A> alternatives(PlannedHeld.Alternatives<A> boxes,
                                                             Allowance<A> by, Unbuilt<A> gaveUp) {
        Set<AdmissibleValues.Alternative<A>> live = new LinkedHashSet<>();
        Set<PlannedHeld.Alternative<A>> standing = new LinkedHashSet<>();
        Set<Sameness.Block<A>> emptied = null;
        for (PlannedHeld.Alternative<A> box : boxes.boxes()) {
            Map<Sameness.Block<A>, ValueSet> at = builtIn(box, by, gaveUp);
            if (at.values().stream().noneMatch(ValueSet::isEmpty)) {
                // The relation crosses unchanged. What a denial says is about the blocks and not
                // about what they were described as holding, so building the descriptions is not
                // where it could be lost or gained.
                live.add(new AdmissibleValues.Alternative<>(
                        new AdmissibleValues.Box<>(at), box.apart()));
                standing.add(box);
                continue;
            }
            emptied = AdmissibleValues.alsoEmptied(emptied, at);
        }
        if (live.isEmpty()) {
            return new AdmissibleValues.Held.Nothing<>(emptied == null ? Set.of() : emptied);
        }
        // What each block the alternatives agree on holds across the ones that stand, described
        // first and built once. Read off the sets instead, a join of two languages would be a
        // machine nobody counted.
        Sameness<A> common = commonTo(new PlannedHeld.Alternatives<>(standing));
        Set<Sameness.Block<A>> named = new LinkedHashSet<>();
        standing.forEach(box ->
                box.positions().forEach(position -> named.add(common.blockOf(position))));
        Map<Sameness.Block<A>, ValueSet> across = new LinkedHashMap<>();
        for (Sameness.Block<A> block : named) {
            A member = block.members().iterator().next();
            AdmittedPlan plan = AdmittedPlan.joining(
                    standing.stream().map(box -> box.get(member)).toList());
            Realization made = by.realizer(block).of(plan);
            gaveUp.note(block, made);
            if (!made.upperBound().isAny()) {
                across.put(block, made.upperBound());
            }
        }
        return AdmissibleValues.Held.Alternatives.of(live, common, across);
    }

    /** One alternative's descriptions as the sets they come to, each built under its own block's
     *  allowance. */
    private static <A> Map<Sameness.Block<A>, ValueSet> builtIn(PlannedHeld.Alternative<A> box,
                                                                Allowance<A> by,
                                                                Unbuilt<A> gaveUp) {
        Map<Sameness.Block<A>, ValueSet> out = new LinkedHashMap<>();
        box.at().forEach((block, plan) -> {
            Realization made = by.realizer(block).of(plan);
            gaveUp.note(block, made);
            out.put(block, made.upperBound());
        });
        return out;
    }

    /** Each position's own description as the set it comes to, built under the allowance of the
     *  block that position is on, the ones nobody could build widened to every value and written
     *  down as such. */
    private static <A> Map<A, ValueSet> realized(Map<A, AdmittedPlan> of, Sameness<A> heldAsOne,
                                                 Allowance<A> by, Unbuilt<A> gaveUp) {
        Map<A, ValueSet> out = new LinkedHashMap<>();
        of.forEach((atom, plan) -> {
            Sameness.Block<A> block = heldAsOne.blockOf(atom);
            Realization made = by.realizer(block).of(plan);
            gaveUp.note(block, made);
            out.put(atom, made.upperBound());
        });
        return out;
    }

    /**
     * The same for a promise, which widens the other way.
     *
     * <p>A promise nobody could work out promises nothing, and that is the strongest thing that
     * stays true — where an answer this could not build widens to every value, a guarantee it could
     * not build shrinks to none. Nothing is recorded: a reader short of a guarantee has been told
     * no more than the truth, and the reasons below are about the upper bound.
     */
    private static <A> Map<Sameness.Block<A>, ValueSet> promised(
            Map<Sameness.Block<A>, AdmittedPlan> of, Allowance<A> by) {
        Map<Sameness.Block<A>, ValueSet> out = new LinkedHashMap<>();
        of.forEach((block, plan) -> out.put(block, promised(plan, by.realizer(block))));
        return out;
    }

    private static ValueSet promised(AdmittedPlan plan, Realizer by) {
        Realization made = by.of(plan);
        return made.isExact() ? made.upperBound() : ValueSet.NONE;
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
