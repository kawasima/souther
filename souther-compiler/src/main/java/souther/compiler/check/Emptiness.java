package souther.compiler.check;

import souther.compiler.types.TypeSymbol;

import java.util.List;

/**
 * Why a count came to none — the proof, and not the sentence anybody is told.
 *
 * <p>What this answers is "how was it shown that no value of this exists", which is a question about
 * the reading rather than about the report. A refusal written where nothing carries the answer can
 * only assume one, and assuming is what left a declaration refused for rules that cannot all hold
 * being told it refers to itself. So the answer travels with the count: a count of none is
 * {@link Cardinality.None} and holds one of these, and there is no other way to write one.
 *
 * <p>Nothing here says what to do about it. {@code MakeTheFieldOptional}, {@code ProbablyRecursive},
 * a message key — none of those belong, because each of them is a reading of a proof and not a proof,
 * and putting one here would mean a wording settled where a count is taken. What sentence to write,
 * what to suggest, and where to point are chosen from these in one place, and changing any of them
 * leaves this alone.
 *
 * <p>The constructors are cut by what was shown and not by what showed it. Two readings arriving at
 * the same proof write the same one, and a reading moved from one class to another changes nothing
 * here. Where two of them would both be true, the more particular one is written:
 * {@link ConflictingRules} is the general form, for a domain that could show its rules contradict and
 * nothing further, and a reading that can say the values at a position leave an empty range says
 * {@link EmptyNumericInterval} instead. Which is written is settled by
 * {@link Emptiness#category()} and the order the positions are declared in, never by the order a
 * traversal happens to reach them.
 *
 * <p>The bottom a rising starts from is not one of these. A member of a component the rising has not
 * finished with has not been shown to have no value — it has not been shown anything — and a proof
 * made there would say A has none because B has none and B has none because A has none, which proves
 * nothing. {@link TypeCardinality} holds those readings back until the rising stops and then writes
 * {@link NoBaseInComponent}, whose proof is that the least fixed point was reached with every one of
 * them still at nothing.
 *
 * <p>Which readings those are is settled by reading the proofs, so a proof has to name what it rests
 * on: <strong>a reading that consulted a child's count and came to none carries that child's proof
 * inside its own.</strong> {@link AtAField}, {@link NonEmptyCollectionWithNoElement} and
 * {@link AcrossEveryCase} take a child for that reason, and a proof that takes none — every other
 * one — says the reading consulted none. A reading added later that looked at a child and then
 * wrote a leaf would say it stands on its own when it does not, and a cycle running through it would
 * be discharged as something shown.
 *
 * <p>Reading the proofs rather than what the reading touched, because touching is not resting on. A
 * record with a set that cannot be filled beside a field holding a member of its own cycle has been
 * shown to have no value by the set alone; a reading that took having reached the cycle for having
 * leaned on it would call that record part of the cycle, and the two declarations that hold it only
 * through the record along with it.
 */
public sealed interface Emptiness {

    /**
     * The rules the declaration states cannot all hold, so nothing satisfies them.
     *
     * <p>The general form. It says the domain could show a contradiction and could not say more than
     * that, so a reading that can name what it found writes that instead.
     */
    record ConflictingRules() implements Emptiness {}

    /** The values a position may take run from a lower end above its upper one. */
    record EmptyNumericInterval() implements Emptiness {}

    /**
     * The rules leave a position no value its order holds.
     *
     * <p>What was shown, and not one of the ways of showing it. Three shapes come to this: two ends
     * that cross, one end the order does not reach — above the last case an enumeration declares,
     * below the empty string — and two equalities naming different values, an equality being both
     * ends at once. A proof cut to any one of them would be a proof the other two are written as,
     * and the sentence read off it would send an author after a rule the model does not contain.
     *
     * <p>The declaration's own rules, which is what tells this from {@link EmptyNumericInterval}:
     * that one is a position whose <em>type</em> leaves it no value to be counted between, reached
     * while counting the positions of a record. This is the rules contradicting, reached before any
     * position is counted, and it is {@link ConflictingRules} with the place filled in.
     *
     * <p>Over whatever order the position has. {@code value > 5 && value < 3} and
     * {@code value > Date("2020-01-01") && value < Date("2010-01-01")} are one shape and are shown
     * one way, so they are refused in one sentence — which they were not, for as long as only the
     * first reached a domain.
     */
    record EmptyOrderedInterval() implements Emptiness {}

    /**
     * The values a position is allowed and the range its order is left share none.
     *
     * <p>Three things are true and they are not the same fact: the set admits something, the range
     * holds something, and nothing is in both. So this is not {@link EmptyOrderedInterval}, which is
     * the range holding nothing whatever the values say, and not a reading that admits nothing
     * either — it is what neither of them could say alone.
     *
     * <p>Written without a place where the alternatives are refused at different positions.
     * {@code (x = "A", y = "B")} beside {@code (x = "C", y = "D")}, met with a rule allowing
     * {@code x} up to {@code "A"} and {@code y} from {@code "D"}, leaves each position holding
     * values some alternative stands at — so what was shown is about the whole product, and naming
     * a position would name one the rules are fine with.
     */
    record NoAllowedValueInRange() implements Emptiness {}

    /**
     * The values a position is allowed and the bounds the rules require it to be within share none.
     *
     * <p>What no reading of the position showed on its own. Its values are a set some rule left it,
     * its order is a range some rule left it, and the bounds here are what follows from the rules
     * once every reading of them has said where the position may be — {@code x} between one and
     * two, and {@code x} three or more because it is one past a {@code y} that is at least two.
     * Each of those is satisfiable and no two of them were ever asked together.
     *
     * <p>Which is why this says the bounds are required and not where they come from. Any reading
     * of the state that can say where one position lies may require it, and a proof naming which of
     * them did would be a new proof for each — an author acts on the same fact whichever it was,
     * and a rule about the position alone can put a bound on it as readily as a rule relating it to
     * another.
     *
     * <p>Not {@link NoAllowedValueInRange}, which is the same shape between the two readings a
     * declaration's own clauses are read into. The difference is worth keeping: that one is
     * answered by those two, and this one is not answered until what every other reading requires
     * of the position is asked with them.
     */
    record NoAllowedValueWithinRequiredBounds() implements Emptiness {}

    /**
     * Positions the rules hold as one value are left no value they can all hold.
     *
     * <p>Not a position's own lack. Each of them on its own is left something — {@code p} may be
     * {@code Done} and {@code r} may be {@code Ready} — and what has nothing is the one value the
     * rules say the two of them are. So the place this is said at is the positions together, and
     * a sentence naming one of them would send an author after a rule that place is fine with.
     *
     * <p>And not {@link ConflictingRules} either, which is what it read as for as long as an
     * equality between two positions reached no reading: the rules do contradict, and this says
     * which two facts about which places cannot both hold.
     */
    record NoCommonValueForEqualPositions() implements Emptiness {}

    /**
     * Positions the rules state to hold different values are left no way of differing.
     *
     * <p>The other half of what a rule between two positions can say, and the same shape of lack:
     * each of them is left values of its own, and what has nothing is an assignment to all of them
     * at once. So it is said of the positions together, and a sentence naming one would send an
     * author after a rule that place is fine with.
     *
     * <p><b>Not {@link NoCommonValueForEqualPositions} with the rule turned round.</b> That one is
     * a lack at one place — the value several positions are — and this one is a lack at no place.
     * A reader shown the first is shown where to look; a reader shown this is shown which rules
     * cannot hold between them.
     */
    record NoDistinctValuesForPositionsHeldApart() implements Emptiness {}

    /**
     * A set is asked to hold more values that differ than there are of what it holds.
     *
     * <p>One number and not two. What was compared is whether the rules admit any size this small,
     * so the fewest they do admit is a number nothing here read — the reading that would answer it
     * is a different one from the reading that refused, and putting its answer beside this one would
     * be two precisions in one sentence.
     *
     * @param available an upper bound on how many values of what it holds differ, which is what the
     *                  comparison was made against. A bound and not a count: it may have been
     *                  rounded up, and the refusal holds because no size even that small is admitted
     */
    record SetRequiresTooManyDistinctValues(long available) implements Emptiness {}

    /** The rules leave a collection no size at all: not empty, and no size above it either. */
    record NoAllowedCollectionSize() implements Emptiness {}

    /** A collection the rules will not let be empty, of an element there is no value of. */
    record NonEmptyCollectionWithNoElement(Emptiness element) implements Emptiness {}

    /**
     * A declaration reached from here has no value, which is where this proof leaves off.
     *
     * <p>The boundary of the reading. Why that one has none is its own answer, held where its own
     * count is, and carrying it here would grow one proof by the whole of another's for no question
     * anybody asks of it.
     */
    record TheNameHasNone(TypeSymbol name) implements Emptiness {}

    /**
     * Declarations written in terms of each other with nothing to bottom out.
     *
     * <p>The one proof that is not read off a declaration. Every member of {@code members} was
     * granted nothing and rose only as far as what could be shown, and the rising stopped with all of
     * them still at nothing — so no value of any of them is built in finitely many steps. Written
     * once the rising has stopped, because that is when it is true.
     *
     * @param through how this member reaches the others, which is what a suggestion is chosen from
     */
    record NoBaseInComponent(List<TypeSymbol> members, Emptiness through) implements Emptiness {

        public NoBaseInComponent {
            members = List.copyOf(members);
            if (members.isEmpty()) {
                throw new IllegalArgumentException("a component nothing is in is not a component");
            }
        }
    }

    /**
     * A position of the declaration has no value, which leaves the declaration none.
     *
     * <p>A step and not a proof: what is proven is {@link #under}, and this says where. The position
     * is here rather than on the proofs below it so that one place in a value is written down once —
     * a proof carrying a path of its own beside this one would be the same fact in two spellings.
     *
     * <p><b>Which place, and never the text of one.</b> Two readings make these and each writes the
     * place in its own words — a declaration's rules call it {@code cap}, an input's reading calls
     * it {@code p.cap} — and the one thing they agree on is whether the lack is at the value the
     * reading is of or somewhere in it. That is the question a reader asks, so it is a case here
     * rather than a spelling to be compared: read back off the text, what a value says about itself
     * is whatever the two vocabularies happen to write it as.
     *
     * @param where where the lack is
     */
    record AtAField(Where where, Emptiness under) implements Emptiness {

        /** Where a lack is, in the words of whichever reading found it. */
        public sealed interface Where {

            /** The value the reading is of: what a newtype wraps, or a parameter. */
            record TheValueItself() implements Where {}

            /**
             * Somewhere in it, written out.
             *
             * @param spelled what the reading that found it calls the place, for a reader
             */
            record In(String spelled) implements Where {

                public In {
                    if (spelled == null || spelled.isEmpty()) {
                        throw new IllegalArgumentException(
                                "somewhere in a value is somewhere, and the value itself is the "
                                        + "case beside this one");
                    }
                }
            }
        }
    }

    /**
     * Several positions of the declaration hold one value, and that value has none.
     *
     * <p>{@link AtAField} where the place is more than one place. A step and not a proof, the same
     * way: what is proven is {@link #under}, and this says where — and where is all of them at
     * once, because the lack is the one value they share and no one of them is answerable for it.
     *
     * <p>Beside {@link AtAField} rather than a widening of it. That one names a place, and a
     * reader of it is shown a place; two of them would make a reader work out from the count of
     * names whether it was being shown a position or a set of them, and the singleton case would
     * read as both.
     *
     * @param where the places, in the order the value declares them
     */
    record AtEqualPositions(List<AtAField.Where> where, Emptiness under) implements Emptiness {

        public AtEqualPositions {
            where = List.copyOf(where);
            if (where.size() < 2) {
                throw new IllegalArgumentException(
                        "one place is a place, and is said as being at a field");
            }
        }
    }

    /**
     * Several positions of the declaration are stated to differ, and cannot all be told apart.
     *
     * <p>{@link AtEqualPositions} for the rule the other way round, and a different sentence rather
     * than the same one about different places. Those positions hold one value between them and
     * this value has none; these positions hold a value each and there are not enough values to go
     * round. A reader shown the first goes to the value they share, and there is nowhere for a
     * reader of this to go but to the rules between them.
     *
     * @param where the places, in the order the value declares them
     */
    record AtPositionsHeldApart(List<AtAField.Where> where, Emptiness under) implements Emptiness {

        public AtPositionsHeldApart {
            where = List.copyOf(where);
            if (where.size() < 2) {
                throw new IllegalArgumentException(
                        "positions stated to differ are more than one position");
            }
        }
    }

    /**
     * Every case of a sum, or every member of a union, has no value.
     *
     * <p>All of them and not one of them. A sum has a value wherever any case does, so what proves it
     * has none is the whole list, and picking one of them to speak for the rest would answer a
     * question about which case is at fault that nothing asked.
     */
    record AcrossEveryCase(List<Emptiness> cases) implements Emptiness {

        public AcrossEveryCase {
            cases = List.copyOf(cases);
            if (cases.isEmpty()) {
                throw new IllegalArgumentException(
                        "a sum with no case to read is one nothing was shown about");
            }
        }
    }

    /**
     * How near a proof is to the declaration it is about, which is what decides between two of them.
     *
     * <p>A declaration may be shown to have no value in more than one way at once — rules that
     * contradict and a field with nothing in it — and which proof is carried has to be the same
     * whichever order the reading happened to take. Nearer is chosen: a declaration's own rules
     * before a position's shape, and a position's shape before something another declaration lacks.
     */
    enum Nearness { DIRECT, STRUCTURAL, PROPAGATED }

    /**
     * Which of two proofs of one count is written, either of which may be absent.
     *
     * <p>The rule the constructors are cut by, made a function so that it is applied and not
     * remembered. Nearer first, and then the more particular of two equally near ones:
     * {@link ConflictingRules} is the general form and anything else says more, so a state where two
     * domains both hold a contradiction writes the one that can name what it found.
     *
     * <p>For two proofs of the <em>same</em> count. Which of several positions a record is refused
     * for is a different question — it is settled by the order the fields are declared in — and is
     * asked where the positions are.
     */
    static Emptiness preferred(Emptiness one, Emptiness other) {
        if (one == null) {
            return other;
        }
        if (other == null) {
            return one;
        }
        int nearer = one.category().compareTo(other.category());
        if (nearer != 0) {
            return nearer < 0 ? one : other;
        }
        return general(one) && !general(other) ? other : one;
    }

    /** Whether this proof says the rules contradict and nothing further. */
    private static boolean general(Emptiness why) {
        return why instanceof ConflictingRules;
    }

    /** How near this proof is to the declaration it is about. */
    default Nearness category() {
        return switch (this) {
            // The declaration's own rules. An empty interval is one of these and not a shape: it is
            // the rules contradicting, with the place and the reason filled in.
            case ConflictingRules _, EmptyOrderedInterval _, NoAllowedValueInRange _,
                 NoAllowedValueWithinRequiredBounds _,
                 NoCommonValueForEqualPositions _,
                 NoDistinctValuesForPositionsHeldApart _ -> Nearness.DIRECT;
            case EmptyNumericInterval _, SetRequiresTooManyDistinctValues _,
                 NoAllowedCollectionSize _ -> Nearness.STRUCTURAL;
            case TheNameHasNone _, NoBaseInComponent _ -> Nearness.PROPAGATED;
            case AtAField at -> at.under().category();
            case AtEqualPositions at -> at.under().category();
            case AtPositionsHeldApart at -> at.under().category();
            case NonEmptyCollectionWithNoElement held -> held.element().category();
            // As far off as its furthest case: the sum has none because all of them have none, so a
            // proof reaching another declaration is a proof this one reaches it too.
            case AcrossEveryCase every -> every.cases().stream()
                    .map(Emptiness::category)
                    .max(java.util.Comparator.naturalOrder())
                    .orElseThrow();
        };
    }
}
