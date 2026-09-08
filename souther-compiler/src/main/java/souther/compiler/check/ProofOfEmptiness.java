package souther.compiler.check;

import souther.compiler.values.RelationalLack;
import souther.compiler.values.Refusal;
import souther.compiler.values.Sameness;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;

/**
 * The sentence a reader is told, written from what a state was shown empty by.
 *
 * <p>Two questions, and this is the second of them. Which proof holds is a question about a state
 * and is settled by walking it ({@link ConstraintState}); how that proof is named is a question
 * about the declaration whose positions it is about, and it is settled here. A proof arrives as
 * what it is — what emptied the pair, and where — and what comes back is what can be said about it
 * in a value that declares these positions in this order.
 *
 * <p><b>Answered for every proof, and not only for the ones a walk reaches today.</b> What arrives
 * is a {@link Confinement.Admission}, and the rules below are about what one of those can say: a
 * lack at several blocks, a lack about blocks together, one at a block of subjects the value does
 * not name. Which of those a walk of a state happens to produce is that walk's business and moves
 * as the compiler learns to show more — the naming has to be right about each of them the day it
 * arrives, so it is settled from the proof and never from where the proof came from.
 */
final class ProofOfEmptiness {

    private ProofOfEmptiness() {
    }

    /**
     * What is said about a state shown to hold nothing, or nothing where it was not shown.
     *
     * @param shown what emptied the pair of readings, and where
     * @param positions where the value declares each of its positions, in the order it declares
     *                  them — which is what settles the naming wherever a proof names several
     *                  blocks
     * @param general what can be said whatever the readings show, or null where nothing showed the
     *                state empty. The nearer sentences below are preferred to it where there is
     *                one to write
     */
    static <A> Optional<Emptiness> named(Confinement.Admission<A> shown,
                                         SequencedMap<A, Emptiness.AtAField.Where> positions,
                                         Emptiness general) {
        // A position whose ends cross, which is nearer than the general form: it says not only that
        // the rules contradict but where they leave nothing.
        //
        // Only where there is a position to name. A state can hold nothing without any one position
        // being what holds nothing — a choice every alternative of which is impossible is one, and
        // the alternatives may fail at different positions — and the particular proof is particular
        // by naming a place. Written without one, the sentence read off it would name whatever place
        // the reader happened to be at, which is the declaration's own value.
        //
        // And the set of values and the range sharing none, which is the same kind of proof about
        // the pair rather than about one of them. Written only where the pair is what holds nothing:
        // a state the numbers refuse is refused by the numbers, whatever the sets and the ranges
        // leave beside them.
        //
        // And where what showed it is those readings met with what is required of the positions
        // elsewhere, the proof says that and not which of them the range came from. Which reading
        // left the pair nothing is a question about the pair, and this is an answer neither of them
        // reached: the values a position is allowed and the bounds the rules require it to be within
        // share nothing.
        Emptiness said = shown.byTheReadings() ? switch (shown.by()) {
            case ORDER -> new Emptiness.EmptyOrderedInterval();
            case SET_AND_RANGE -> new Emptiness.NoAllowedValueInRange();
            case POSITIONS_HELD_AS_ONE -> new Emptiness.NoCommonValueForEqualPositions();
            // Which of the arguments a relation refuses by is the witness's to say, and the two are
            // two sentences: one is about how many values there are and the other about nothing of
            // the kind.
            case POSITIONS_HELD_APART -> shown.site().together()
                    .all(lack -> lack instanceof RelationalLack.ABlockApartFromItself)
                    ? new Emptiness.PositionsHeldAsOneAreHeldApart()
                    : new Emptiness.NoDistinctValuesForPositionsHeldApart();
            case NOTHING_SHOWN, VALUES, RULES_TOGETHER -> null;
        } : new Emptiness.NoAllowedValueWithinRequiredBounds();
        if (said == null) {
            return Optional.ofNullable(general);
        }
        // Positions stated to differ, which is a lack about all of them at once and at none of
        // them. Said as its own place rather than through the one below: that one names the block
        // whose positions are one value, and these positions are not one value — read through it,
        // a proof would say the rules hold them as one, which is what they state they are not.
        if (shown.site().nearest() == Refusal.Nearest.OF_THEM_TOGETHER) {
            List<Emptiness.AtAField.Where> apart =
                    declaredIn(shown.site().together().blocks(), positions);
            return Optional.of(apart.size() < 2 ? Emptiness.preferred(general, said)
                    : Emptiness.preferred(general, new Emptiness.AtPositionsHeldApart(apart, said)));
        }
        // One block of the proof is named, and it is named as what it is: a place where it is one
        // position, and the positions together where it is several.
        //
        // One and not all of them. Two blocks left nothing are two lacks, and a sentence over both
        // would say their positions are one value — a relation no rule of the model states. Which
        // one is settled by the order the value declares its positions, as which of several empty
        // positions is named is: read off the state's own set, the block named would be the one
        // whose clause was met first, and moving a clause would move the refusal.
        //
        // And read off the blocks each of which holds nothing, which is what this sentence is
        // about. A refusal also carries what no assignment to some blocks satisfies, and each of
        // those blocks is left values of its own — named here, a reader would be sent to a place
        // whose own rules are fine with what they leave it, and which of the two a proof was about
        // would turn on which the value declares first.
        List<Emptiness.AtAField.Where> where = nearestBlock(shown.site().atEachOf(), positions);
        if (where.size() == 1) {
            return Optional.of(Emptiness.preferred(general,
                    new Emptiness.AtAField(where.getFirst(), said)));
        }
        if (where.size() > 1) {
            return Optional.of(Emptiness.preferred(general,
                    new Emptiness.AtEqualPositions(where, said)));
        }
        // No position to name, which is what a choice refused at two of them leaves: each of them
        // holds values some alternative stands at, so what was shown is about the whole product.
        //
        // Only where what was shown is true of the pair rather than of one position. A range with
        // nothing in it is a position's own answer and is said of that position or not at all —
        // written without one, the sentence read off it would name whatever place the reader
        // happened to be at, which is the declaration's own value.
        return Optional.of(!shown.byTheReadings()
                || shown.by() == Confinement.EmptyBy.SET_AND_RANGE
                ? Emptiness.preferred(general, said) : general);
    }

    /**
     * The places of the block whose earliest position the value declares first, in that order.
     *
     * <p>One block and not the union of them. What each of these says is that the positions in it
     * are one value and that value has none, which is a lack per block — read together, a
     * declaration whose {@code p == q} and whose {@code r == s} are each contradictory would be
     * told that all four are one value, which no rule of it says.
     *
     * <p>Empty where no block's positions are all declared here, which is a block about subjects
     * this value does not name. What can be said then is what the general proof says.
     */
    private static <A> List<Emptiness.AtAField.Where> nearestBlock(
            Set<Sameness.Block<A>> blocks,
            SequencedMap<A, Emptiness.AtAField.Where> positions) {
        Map<A, Integer> ordinal = new LinkedHashMap<>();
        positions.keySet().forEach(each -> ordinal.put(each, ordinal.size()));
        List<Integer> nearest = null;
        Sameness.Block<A> chosen = null;
        for (Sameness.Block<A> block : blocks) {
            List<Integer> where = declared(block, ordinal);
            // A block one of whose positions this value does not declare is one no sentence can be
            // written about, and it says nothing about the blocks beside it.
            if (where != null && (nearest == null || earlier(where, nearest))) {
                nearest = where;
                chosen = block;
            }
        }
        if (chosen == null) {
            return List.of();
        }
        Sameness.Block<A> named = chosen;
        List<Emptiness.AtAField.Where> out = new ArrayList<>();
        positions.forEach((position, place) -> {
            if (named.holds(position)) {
                out.add(place);
            }
        });
        return out;
    }

    /**
     * Where the value declares the positions of every one of {@code blocks}, in that order.
     *
     * <p>All of them and not one, which is what parts this from {@link #nearestBlock}. A lack about
     * blocks together is one lack about all of them, so every place it names is part of the
     * sentence; there is no choosing between them, and dropping one would say the rules refuse
     * fewer positions than they do.
     *
     * <p>Empty where the value declares none of one of those positions, which is a lack about
     * subjects it does not name. What can be said then is what the general proof says.
     */
    private static <A> List<Emptiness.AtAField.Where> declaredIn(
            Set<Sameness.Block<A>> blocks,
            SequencedMap<A, Emptiness.AtAField.Where> positions) {
        Set<A> named = new LinkedHashSet<>();
        for (Sameness.Block<A> block : blocks) {
            for (A member : block.members()) {
                if (!positions.containsKey(member)) {
                    return List.of();
                }
                named.add(member);
            }
        }
        List<Emptiness.AtAField.Where> out = new ArrayList<>();
        positions.forEach((position, place) -> {
            if (named.contains(position)) {
                out.add(place);
            }
        });
        return out;
    }

    /** Where the value declares each of a block's positions, in that order, or null where it
     *  declares none of one of them. */
    private static <A> List<Integer> declared(Sameness.Block<A> block, Map<A, Integer> ordinal) {
        List<Integer> out = new ArrayList<>();
        for (A member : block.members()) {
            Integer at = ordinal.get(member);
            if (at == null) {
                return null;
            }
            out.add(at);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /**
     * Whether one block is declared before another, comparing the places they are at.
     *
     * <p>Every place and not the first of them. Two blocks may begin at one position — a state left
     * with no value at {@code p} with {@code q} and at {@code p} with {@code r} has both, since
     * what is carried is one witness per way the rules were shown empty and not a partition — and
     * a reader that stopped at the first would pick whichever of them a set happened to iterate to.
     * Which is an order salted per run of the machine, so one model would be refused two ways.
     *
     * <p>A total order over the blocks a value can name, since two of them made of declared
     * positions are the same block wherever their places are the same. Shorter first where one is
     * the beginning of the other, which is the only pair the places do not tell apart.
     */
    private static boolean earlier(List<Integer> these, List<Integer> those) {
        for (int at = 0; at < Math.min(these.size(), those.size()); at++) {
            if (!these.get(at).equals(those.get(at))) {
                return these.get(at) < those.get(at);
            }
        }
        return these.size() < those.size();
    }
}
