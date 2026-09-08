package souther.compiler.check;

import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.values.Allowance;
import souther.compiler.values.ConjoinedAdmissibleValues;
import souther.compiler.values.StringMachineAnswers;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * What the rules say, in each of the languages this reading has for saying it.
 *
 * <p>Several domains and one state. A clause relates numbers, or bounds one position on its order,
 * or names the values a position may take, or settles a predicate that relates to nothing, and each
 * of those is written where something can be reasoned about it — but what a caller asks of them
 * together is one question, and it is whether anything at all satisfies what has been taken in. That
 * is {@link #isBottom}, and it is asked of the whole because a reader that asked one domain would
 * answer that a value exists whenever the domain it happened to ask had nothing to say.
 * {@code value == "A"} beside {@code value /= "A"} reaches no number and leaves the predicates
 * contradictory, and a count taken from the numbers alone called that type inhabited.
 *
 * <p><b>What each domain is for, and that they overlap on purpose.</b>
 *
 * <pre>
 *     numbers    affine relations between positions a model adds and subtracts
 *     ordered    one position bounded against a written value, on whatever order it has
 *     values     which values a position may take, as a finite set or a finite exclusion
 *     facts      a predicate about a position, settled one way or the other
 * </pre>
 *
 * <p>Two of them answering for one clause is not two meanings of it. {@code value > 5} is an affine
 * relation and a bound on an order, and each domain abstracts it in a way that is safe on its own:
 * the numbers can relate it to a sibling position and the ordering cannot, the ordering holds it
 * over a date and the numbers cannot. Where both hold it, both are right, and neither is the other's
 * copy. What must not happen is a rule reaching none of them and being read as a rule that said
 * nothing, which is what an exception list — this domain covers the positions that one does not —
 * arranges the first time a type falls between the two.
 *
 * <p>The parts are readable, and only in one direction. A reader wanting the numbers may have them —
 * an interval is what a bound is read off, and nothing else answers that — but a reader asking
 * whether a value exists asks here and never assembles the answer out of the parts. A domain added
 * later is a component of this and an arm of {@link #isBottom}, and every such reader has it without
 * being touched.
 *
 * <p>Two questions are asked here and they are one answer. Whether any value of a declaration
 * exists, and whether a path a construction stands on is reached, are both whether anything at all
 * satisfies what has been taken in — so both are {@link #isBottom}, under the name each context
 * reads it by ({@link FieldDomains#infeasible}, {@link Known#reachesNothing}). They were once two
 * readers of two domains, and each of them answered that there was something wherever the domain it
 * happened to ask had nothing to say.
 */
public record ConstraintState<A>(NumericDomain<A> numbers, PredicateFacts<A> facts,
                                 Confinement.Conjoined<A> confinement, boolean shown) {

    /** Nothing taken in, so nothing ruled out. */
    public static <A> ConstraintState<A> top() {
        return new ConstraintState<>(NumericDomain.top(), PredicateFacts.none(),
                Confinement.Conjoined.top(), false);
    }

    /**
     * Which values the positions may take, for a reader that needs the sets themselves.
     *
     * <p>Not a way to the answer this record gives. What a position admits is a question the values
     * answer; whether anything satisfies the rules is {@link #isBottom}, and a reader assembling
     * that out of the parts is the reading this question came apart along.
     */
    public ConjoinedAdmissibleValues<A> values() {
        return confinement.values();
    }

    /**
     * Whether nothing satisfies what has been taken in.
     *
     * <p>A contradiction found anywhere is a contradiction, and one found nowhere is only what these
     * readings were able to show. What a domain cannot express it leaves alone, so each of them may
     * hold one on its own — but two of them holding halves of one is not something either can find,
     * and where that is what happens the two are one component ({@link Confinement}) rather than two
     * arms of this. A set of values and a range on an order are such a pair, and reading them as two
     * arms is what left a declaration whose strings and whose ends share no value accepted.
     *
     * <p><b>And a half one domain holds against a half another one holds is the last arm.</b> Two
     * components put in one is what to do where one language can say the whole of what the other
     * does; where they cannot — the numbers relate positions and hold no choice, the alternatives
     * hold a choice and relate nothing — what is shared is a position, and what each of them
     * requires of it is met in one place ({@link #positionEnvelope}) and asked of the reading that
     * holds the alternatives. So the arms below are every component's own answer, and the reduction
     * between them is one more, asked last.
     *
     * <p>And {@code shown}, which is a caller having shown it by an argument none of the domains
     * makes: a condition no case of what it is written over satisfies is one nothing enters, and
     * what says so is a reading of the cases rather than anything an interval or a predicate holds.
     * It is another piece of evidence for the one answer and not a second answer — a state that
     * carried its own reachability beside the domains would be two records to keep in step, and the
     * first domain added without touching the second would put them out of agreement.
     */
    public boolean isBottom() {
        return isBottom(StringMachineAnswers.NONE);
    }

    /** The same, borrowing what {@code machines} has already made where the pair's answer needs
     *  a machine. */
    public boolean isBottom(StringMachineAnswers machines) {
        return shownByAnother() || admitted(machines).holdsNothing();
    }

    /**
     * Whether a component other than the pair of readings shows that nothing satisfies this.
     *
     * <p>Asked before the pair's answer wherever both are wanted, because the pair's takes a walk
     * over every alternative and these take none. What that ordering is for is written at
     * {@link #admitted}: it decides which proof a reader is told as well as what is worked out.
     */
    private boolean shownByAnother() {
        return shown || numbers.isBottom() || facts.isBottom();
    }

    /**
     * What the values and the ends of this state leave, asked with what the components beside them
     * require of the positions where they leave something.
     *
     * <p>The order is the whole of it. Every component's own answer comes first, and the reduction
     * is asked only where each of them left something: an emptiness one of them can show alone is
     * shown alone, and what is left for the reduction is the contradiction no component holds. That
     * is what makes a proof out of it — read the other way round, a state the numbers refuse would
     * be reported against a restriction derived from those very numbers.
     *
     * <p>So the envelope here is never {@link PositionEnvelope.NothingIsLeft}: the one component
     * that says so is the numbers, and their own answer was asked above.
     */
    private Confinement.Admission<A> admitted(StringMachineAnswers machines) {
        if (shownByAnother()) {
            return confinement.admission(machines);
        }
        return switch (positionEnvelope()) {
            // Nowhere for any position to be is a component of this state holding nothing, which is
            // that component's answer and is read where it is asked. What the pair leaves is what
            // the pair leaves, and this says nothing to it.
            case PositionEnvelope.NothingIsLeft<A> _ -> confinement.admission(machines);
            case PositionEnvelope.Restrictions<A> it -> confinement.admission(it, machines);
        };
    }

    /**
     * Where each position must sit, according to every component of this state but the one that
     * holds the alternatives.
     *
     * <p>A walk over the components rather than a list somebody wrote. Each of them is a language
     * for saying something about a value, and whether what it says reaches one position at a time is
     * a question about that language — so a component added here answers it or stops this, and the
     * three answers are the three roles a component can have. Left to a list, the component added
     * would be the one nobody remembered, and a reduction that quietly leaves a language out is the
     * defect this whole mechanism is about.
     *
     * <p>Over the positions this state's own reading holds, which is what makes it a projection of
     * this state and not an answer about somebody else's table. What each of them is ordered on is
     * what a count crosses into a range through, and that is the reading's answer rather than one
     * worked out again here.
     */
    PositionEnvelope<A> positionEnvelope() {
        Map<A, Carrier> carriers = confinement.carriers();
        Map<A, PositionRestriction> out = new LinkedHashMap<>();
        for (String component : COMPONENTS) {
            switch (component) {
                // The numbers relate positions to each other, so where one of them is left is read
                // off all the rules at once and never off what was written about it.
                case "numbers" -> {
                    // What the component says about itself before what it says about a place. A
                    // domain admitting no assignment leaves nowhere for any position to be, which
                    // is about the state and about no place in it — asked position by position, a
                    // state with no position to ask about would answer that nothing was said.
                    if (numbers.isBottom()) {
                        return new PositionEnvelope.NothingIsLeft<>();
                    }
                    for (Map.Entry<A, Carrier> each : carriers.entrySet()) {
                        narrowing(out, each.getKey(),
                                switch (numbers.projectionOf(each.getKey())) {
                                    case NumericDomain.Projection.NotSpokenOf _ ->
                                            new PositionRestriction.NotSpokenOf();
                                    case NumericDomain.Projection.Within it ->
                                            each.getValue().within(it.bounds());
                                    // Answered above, where it is about the state rather than
                                    // about the position this loop is at.
                                    case NumericDomain.Projection.NothingIsLeft _ ->
                                            throw new IllegalStateException(
                                                    "a domain that holds nothing was asked where a"
                                                            + " position is");
                                });
                    }
                }
                // A predicate settled one way or the other, and an argument made outside the
                // domains. Neither says where a position lies on its order.
                case "facts", "shown" -> { }
                // What this envelope is asked of. A reading projected into the envelope it is then
                // met against would refuse whatever it already said, which is every alternative it
                // holds.
                case "confinement" -> { }
                default -> throw new IllegalStateException(
                        "a state holds " + component + " and nothing says whether it places a"
                                + " position, leaves that to another component, or is what the"
                                + " placing is asked of");
            }
        }
        return new PositionEnvelope.Restrictions<>(out);
    }

    /** {@code out} with {@code position} also held to {@code said}, which is what two components
     *  requiring it to be somewhere leave between them. */
    private static <A> void narrowing(Map<A, PositionRestriction> out, A position,
                                      PositionRestriction said) {
        PositionRestriction had = out.get(position);
        if (had == null) {
            out.put(position, said);
            return;
        }
        // Two of them speaking is one of them speaking, and a position neither of them named stays
        // one nothing spoke of.
        out.put(position, had instanceof PositionRestriction.NotSpokenOf
                && said instanceof PositionRestriction.NotSpokenOf
                ? had : new PositionRestriction.Within(had.interval().meet(said.interval())));
    }

    /** The components of this state, so that one added is one somebody answers for. */
    private static final List<String> COMPONENTS =
            Arrays.stream(ConstraintState.class.getRecordComponents())
                    .map(RecordComponent::getName).toList();

    /**
     * This, with nothing satisfying it, shown by an argument outside the domains.
     *
     * <p>Written here rather than as a contradiction lodged in one of them. Said as {@code 1 <= 0}
     * in the numbers, which is how it read before, the claim came out as an arithmetic one: a reader
     * asking why a state holds nothing was told the rules about numbers conflict, and a reader
     * asking whether a path is reached was pulled back to {@code numbers.isBottom()} — which is the
     * reading this question came apart along.
     */
    ConstraintState<A> shownToHoldNothing() {
        return shown ? this : new ConstraintState<>(numbers, facts, confinement, true);
    }

    /**
     * Why nothing satisfies what has been taken in, or empty where something may.
     *
     * <p>Asked of the state, as {@link #isBottom} is, and for the same reason: which domain holds
     * the contradiction is not something a caller can be left to work out by asking them one at a
     * time. Where more than one does, the more particular proof is written
     * ({@link Emptiness#preferred}) — a state whose answer turned on which domain a reader happened
     * to ask first would refuse one declaration two ways.
     *
     * @param positions where each position of the value sits, <em>in the order the value declares
     *                  them</em>. A proof that names a place needs one to name, and the order is
     *                  what settles it where the rules leave several positions empty at once: read
     *                  off the state's own map, the place named would be the one whose clause was
     *                  read first, and moving a clause would move the refusal
     */
    public Optional<Emptiness> holdsNothing(SequencedMap<A, Emptiness.AtAField.Where> positions) {
        return holdsNothing(positions, StringMachineAnswers.NONE);
    }

    /** The same, borrowing what {@code machines} has already made where the pair's answer needs
     *  a machine. */
    public Optional<Emptiness> holdsNothing(SequencedMap<A, Emptiness.AtAField.Where> positions,
                                            StringMachineAnswers machines) {
        Confinement.Admission<A> shown = admitted(machines);
        // What can be said whatever the readings show, which the nearer sentences are preferred to.
        Emptiness why = shownByAnother() || shown.holdsNothing()
                ? new Emptiness.ConflictingRules() : null;
        // Which proof holds is what this state answers; what it is called in a value declaring
        // these positions is {@link ProofOfEmptiness}'s, and is about what a proof can say rather
        // than about the walk that reached this one.
        return ProofOfEmptiness.named(shown, positions, why);
    }

    /**
     * Both readings holding at once.
     *
     * <p>Every domain, and the same domain on both sides. What a conjunction of two readings leaves
     * is what each domain's conjunction leaves, because the domains do not talk to each other: a
     * rule reaches whichever of them has a word for it and stays there, so two readings are said
     * together by saying each language's half together. And {@code shown} is either side's, an
     * argument outside the domains being no less an argument for the pair.
     *
     * <p>Written here rather than by whoever needs it. A conjunction is what this record means, so a
     * caller assembling one out of {@link #numbers} and the rest would be deciding for itself which
     * domains a conjunction reaches — and a domain added later would be left out of every such
     * caller without a word. Added here, it is in all of them.
     *
     * <p><b>One of the four is not additive, and that is why it is a conjunction rather than a
     * reading.</b> Rules union, predicates union, ranges merge at a position — three representations
     * that grow by what is added to them. Admissible values are a union of products, so a
     * conjunction of two distributes: {@code m} alternatives against {@code n} are {@code m × n},
     * and over vocabularies that share no position not one of those products is ever dropped. Ten
     * parameters of a record leaving two alternatives would be a thousand and twenty-four, outside
     * the budget that admitted each reading and counted per declaration. So that component holds the
     * readings apart ({@link ConjoinedAdmissibleValues}) and pays the product only where two of them
     * name the same position.
     *
     * <p>Both sides have to be said in one vocabulary already. Two readings of two values name their
     * positions the way each value declares them, and met without being renamed first they would
     * hold each other's rules — which is {@link #renamed} and not this.
     *
     * @param sets what the answer being built out of these two may spend on the sets it comes to.
     *             The caller's, because that answer is the caller's: each of these was read under
     *             the allowance of its own declaration and is bounded by it, and what they come to
     *             where their vocabularies meet is a third set neither of them paid for. Taken from
     *             one of the two instead, which of them paid would follow which side {@code .meet}
     *             was written on, and with it how exactly the composition came out
     */
    public ConstraintState<A> meet(ConstraintState<A> other, Allowance<A> sets) {
        return new ConstraintState<>(numbers.meet(other.numbers), facts.meet(other.facts),
                confinement.meet(other.confinement, sets), shown || other.shown);
    }

    /**
     * The same rules about the same subjects, said in the vocabulary {@code naming} gives them.
     *
     * <p>What one reading calls a position is what the value it read declares it. A caller relating
     * several values has names of its own, and this is how a reading crosses into them — so that two
     * of them can be met, and so that a rule relating two of them has somewhere to be said at all.
     *
     * <p><b>A change of vocabulary and not a fold.</b> {@link NumericDomain#over} adds the
     * coefficients of two atoms arriving at one name, which is right of a form a caller wrote in two
     * spellings of one number and wrong of a state: two positions under one name would bound each
     * other, hold each other's values, and settle each other's predicates. So what is taken is an
     * {@link InjectiveRenaming} rather than a function, and it is one renaming for all four domains
     * — a subject may sit in one of them and no other, so a naming that is injective on each domain
     * read alone can still put two subjects under one name, and only something that sees the whole
     * vocabulary can refuse that.
     *
     * <p>What may not collide is every subject of the conjunction and not of each reading in turn.
     * Where the names a caller gives do not themselves say which reading a subject came from, pass
     * one renaming to every state that will be met, and it holds that for all of them. Where they
     * do — a name carrying which of several values it is a position of — the readings are kept apart
     * by the shape of the names, and a renaming of its own for each is enough. Whichever it is, it
     * is the caller's to say, and to say where the names are made rather than here.
     */
    public <B> ConstraintState<B> renamed(InjectiveRenaming<A, B> naming) {
        // The fold in `over` cannot fire: `naming` refuses a second subject at a name some other
        // subject already has, so no two atoms of this state reach one name to be added together.
        return new ConstraintState<>(numbers.over(naming::apply), facts.renamed(naming::apply),
                confinement.renamed(naming::apply), shown);
    }

    /** This, with {@code f rel 0} taken as holding. */
    public ConstraintState<A> taking(LinearForm<A> f, Rel rel, Map<A, Granularity> kinds) {
        return new ConstraintState<>(numbers.assume(f, rel, kinds), facts, confinement, shown);
    }

    /** This, with the predicate {@code key} taken as holding, or as failing. */
    ConstraintState<A> taking(A key, boolean positive) {
        return new ConstraintState<>(numbers, facts.assume(key, positive), confinement, shown);
    }

    /**
     * This, with the clauses of one declaration read into its values.
     *
     * <p><b>Not two readings combined.</b> What stands here is what nothing read leaves until this
     * is called, and it is called once — so the answer is the same either way today. Written
     * against that rather than against what the state happens to hold, because a reading is a union
     * of alternatives and a conjunction of two of them is the union of the pairs: two readings met
     * here would be two declarations' alternatives multiplied outside the budget either of them was
     * admitted under, and the budget is what makes the reading of one declaration bounded at all.
     *
     * <p>Met with what nothing read leaves rather than assigned, which is not the same answer. A
     * reading a choice reached across two positions promises nothing about whole values, and a
     * conjunction with one of those promises nothing anywhere — so the meet gives up a lower bound
     * that an assignment would keep. That is {@link AdmissibleValues#guaranteedAt}'s rule about
     * conjunctions firing on a side that read nothing, and lifting it is about which rules went
     * unread rather than about how the alternatives are held.
     */
    ConstraintState<A> takingRead(Confinement.Worked<A> read, Allowance<A> sets) {
        return takingRead(read, sets, StringMachineAnswers.NONE);
    }

    /** The same, borrowing what {@code machines} has already made where taking the reading in
     *  asks what either side was shown empty by. */
    ConstraintState<A> takingRead(Confinement.Worked<A> read, Allowance<A> sets,
                                  StringMachineAnswers machines) {
        return new ConstraintState<>(numbers, facts, confinement.taking(read, sets, machines),
                shown);
    }

    /**
     * This, with {@code bounded} taken as holding of the positions it bounds.
     *
     * @param on what each of those positions is ordered by, which is what a range and a set of
     *           values are put together over. Handed in rather than worked out here: the carrier
     *           was settled where the clauses were read, and a table built again would be a second
     *           answer to a question already asked
     */
    ConstraintState<A> taking(OrderedIntervals<A> bounded, Map<A, Carrier> on) {
        return new ConstraintState<>(numbers, facts, confinement.taking(bounded, on), shown);
    }

    /**
     * The same rules with one atom settled at a value.
     *
     * <p>The one place a settling is stated, so that a reading made with one and a reading settled
     * after it was made say the same thing. Written twice, the second would be free to state it
     * under a different spacing and answer a position differently while both stayed sound.
     */
    static <A> ConstraintState<A> settling(ConstraintState<A> state, A atom,
                                           souther.compiler.numeric.Count at,
                                           souther.compiler.numeric.Granularity spacing) {
        return state.taking(
                LinearForm.<A>atom(atom)
                        .minus(LinearForm.<A>constant(at.at())),
                Rel.EQ, java.util.Map.of(atom, spacing));
    }
}
