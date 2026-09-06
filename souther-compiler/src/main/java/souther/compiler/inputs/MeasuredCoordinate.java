package souther.compiler.inputs;

import souther.compiler.check.FieldDomains;
import souther.compiler.check.NumberAt;
import souther.compiler.types.ValueName;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of a position's numbers it is measured at, or that nothing here answers.
 *
 * <p>A position has one axis and a {@code String} is the one value with two numbers — its own order
 * and the length of it — so which of them a position is measured at is a question, and it has three
 * answers rather than two. Carried as a flag, the third had nowhere to be: the reader took whichever
 * of the two it looked at first, and the rules about the other went out with nothing saying they
 * had.
 *
 * <p><b>One answer for one position, and every reader of it reads this.</b> Choosing the coordinate
 * and saying what became of each rule are two computations, and they are not two decisions: a second
 * reader working out for itself which coordinate a rule is on would be free to disagree with the one
 * that chose, and what they disagreed about would be which rules the position was measured by.
 *
 * <p><b>Not a claim that the model is short of something.</b> {@link Undetermined} says this
 * compiler has no rule for choosing between numbers a model wrote about equally, which is why the
 * candidates travel with it: what a reader is owed is which coordinates were competing, and a report
 * that had only the position would leave an author to work out which of their clauses were in the
 * way.
 */
sealed interface MeasuredCoordinate {

    /** The coordinate, where one of them answers. */
    record At(NumberAt.OfWhatNumber coordinate) implements MeasuredCoordinate {

        public At {
            if (coordinate == null) {
                throw new IllegalArgumentException("a position measured somewhere is measured at a"
                        + " number");
            }
        }
    }

    /**
     * More than one coordinate is spoken for at the standing that answers, and nothing here chooses.
     *
     * <p>Choosing either would put a line the author can read beside one they cannot see, so the
     * position is left as one nothing divides — the coarser of the two things that could be said,
     * and the one that claims nothing.
     */
    record Undetermined(Set<NumberAt.OfWhatNumber> candidates) implements MeasuredCoordinate {

        public Undetermined {
            if (candidates.size() < 2) {
                throw new IllegalArgumentException("what is undetermined is a choice between"
                        + " coordinates, and this holds " + candidates);
            }
            candidates = Set.copyOf(candidates);
        }
    }

    /**
     * The one decision, made from what the position's own type wrote and then from what reached it.
     *
     * <p><b>Two standings, and uniqueness asked within each.</b> The position's own type answers
     * first: a rule reaching the position from the value it sits in states an end on a coordinate,
     * and it does not say which coordinate the position is — letting it say so takes an axis away,
     * and a {@code Name} measured on its own order would stop being so the day a record bounded the
     * length of it. So a type that answers once is answered for, and the ends at the position are
     * not a tie breaker. A type that answers twice is not answered for either: the ends are at the
     * standing below and cannot settle a question the standing above left open.
     *
     * <p><b>What the rules are about, and not which of them placed an end.</b> Whether a clause came
     * to an end is a fact about the clauses beside it and about this compiler's arithmetic; which
     * number a position is is neither. Read off the ends, a type whose one rule is
     * {@code String.length(value) /= 0} was measured on the string's own order — the length was no
     * number of the model at all.
     *
     * <p>The ends are read as ends, because that is the whole of what one of them says here: it
     * names no coordinate for the position and states where one stops. Whether such an end could be
     * read at all is settled where it was placed — a value with an end on its own order is a value
     * something compared — so there is no case to rule out again here, and one written would be
     * this reading deciding a second time what an end is.
     *
     * <p><b>The ends are every end at the position, and the standings are still two.</b> A reading
     * of the value a position sits in holds the clauses of the position's own type as well, rebased
     * under the name it reached them by, so the list below is not the rules of one declaration and
     * no subtraction makes it so. It does not have to be: the standing below is read only where the
     * one above named nothing, and a type that named no number of its value wrote no rule to place
     * an end on one. So what is left there is what reached the position from elsewhere, and it is
     * left there by the answer above rather than by a filter here.
     *
     * @param writtenAbout which numbers the position's own type wrote about
     * @param ends         every end placed at the position, whichever declaration wrote it
     * @param taken        the operation this type's values are counted by, or null where none
     *                     counts them
     */
    static MeasuredCoordinate of(Set<NumberAt.OfWhatNumber> writtenAbout,
                                 List<FieldDomains.Placed> ends,
                                 ValueName.Stdlib taken) {
        Set<NumberAt.OfWhatNumber> eligible = eligible(taken);
        MeasuredCoordinate own = settledBy(intersect(writtenAbout, eligible));
        if (own != null) {
            return own;
        }
        Set<NumberAt.OfWhatNumber> outside = new LinkedHashSet<>();
        for (FieldDomains.Placed each : ends) {
            if (eligible.contains(each.at().of())) {
                outside.add(each.at().of());
            }
        }
        MeasuredCoordinate reached = settledBy(outside);
        // Where neither standing spoke, the position is measured on its own values. Not a coordinate
        // anything chose: a position nobody wrote a rule about is one nothing is taken of here, and
        // what stands at it is what a row writes there.
        return reached != null ? reached : new At(ITS_OWN_VALUE);
    }

    /** The position's own value, which every position has and no rule has to name. */
    NumberAt.OfWhatNumber ITS_OWN_VALUE = new NumberAt.OfWhatNumber.OfItsOwnValue();

    /** The number {@code operation} answers of what stands at a position. */
    static NumberAt.OfWhatNumber answeredBy(ValueName operation) {
        return new NumberAt.OfWhatNumber.OfWhatAnOperationAnswers(operation);
    }

    /**
     * The coordinates a position of this type can be measured at.
     *
     * <p><b>Which numbers a rule mentions and which numbers this position has are two questions.</b>
     * {@code Int.abs(x)} is a number of the place {@code x} stands at and is no axis of it: a rule
     * about one would arrive here as a third candidate, and a position with two rules about numbers
     * nothing measures it by would come back as one no coordinate could be chosen for. So what a
     * rule is about is asked of the rules, and what may measure the position is asked of its type.
     *
     * <p>What stands at the position is always one of them, and the type says whether there is a
     * second: the number an operation answers is there where the type declares an operation that
     * counts its values.
     */
    private static Set<NumberAt.OfWhatNumber> eligible(ValueName.Stdlib taken) {
        Set<NumberAt.OfWhatNumber> out = new LinkedHashSet<>();
        out.add(ITS_OWN_VALUE);
        if (taken != null) {
            out.add(answeredBy(taken));
        }
        return out;
    }

    private static Set<NumberAt.OfWhatNumber> intersect(Set<NumberAt.OfWhatNumber> these,
                                                        Set<NumberAt.OfWhatNumber> those) {
        Set<NumberAt.OfWhatNumber> out = new LinkedHashSet<>(these);
        out.retainAll(those);
        return out;
    }

    /** What one standing comes to: the coordinate where it named one, no answer where it named
     *  none, and no choice where it named more. */
    private static MeasuredCoordinate settledBy(Set<NumberAt.OfWhatNumber> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.size() == 1
                ? new At(candidates.iterator().next())
                : new Undetermined(candidates);
    }
}
