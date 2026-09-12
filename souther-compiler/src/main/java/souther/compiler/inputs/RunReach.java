package souther.compiler.inputs;

import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleKey;
import souther.compiler.check.ValueGuarantees;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.Induction;
import souther.compiler.numeric.Intervals;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.semantics.Accumulation;
import souther.compiler.types.Type;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Where a number taken over a run of values runs.
 *
 * <p>A total stands at no position, so nothing published about a position says where it runs. What
 * does say is the run: as many values as stand at the path it reads from, each of them one of them,
 * put together by the step the operation repeats from the value it starts at.
 *
 * <p><b>Two facts and a theorem, and none of the three is written here.</b> What the operation
 * starts from and repeats is {@code semantics.Accumulation}, declared once for a reader that
 * discharges a rule and a reader that measures a model alike. What every value the run walks
 * guarantees is {@link ValueGuarantees}, which is what the discharge check reads of a container's
 * elements in the same words. That a walk cannot leave a range whose seed is inside it and whose
 * step stays inside it is {@link Induction}. This is the projection of the first two onto the third,
 * and there is no sentence here saying that a total of amounts at or above nought is at or above
 * nought — that follows, and follows for whatever the model declares rather than for the case
 * somebody wrote down.
 *
 * <p><b>What the values guarantee, and never what a context leaves them.</b> A run walks every
 * occurrence of its path and fixes no case and no condition, so what may be assumed of the value at
 * one of them is what holds of every value of its type. A reading narrowed by which case a value
 * turned out to be would be a bound half the occurrences are outside.
 *
 * <p>Nothing is read off how many the container holds. The induction does not have that as an input,
 * so a total of amounts at or above nought is at or above nought whether the list holds none of them
 * or a thousand — the empty container answers the seed, and the seed is inside the range or the range
 * is not proved.
 */
final class RunReach {

    private RunReach() {}

    /** The two numbers a walk's step is applied to. Enough of a name for the arithmetic to relate
     *  them, and they are not positions of anything: no row writes an accumulator. */
    private enum Atom {
        ACCUMULATOR,
        ELEMENT
    }

    private static final LinearForm<Atom> ACCUMULATOR =
            LinearForm.atom(Atom.ACCUMULATOR);

    private static final LinearForm<Atom> CARRIED =
            ACCUMULATOR.plus(LinearForm.atom(Atom.ELEMENT));

    /**
     * What the step answers, given a reading that has the accumulator and the element in it.
     *
     * <p>Read off the domain and not off a pair of ranges, so the arithmetic here is the arithmetic
     * every other reader of this compiler uses. A sum is a form and the domain answers it; a product
     * is not a form and is the one the interval algebra answers, which is the same division the
     * discharge check's step makes.
     */
    @FunctionalInterface
    private interface Step {

        NumericDomain.Bounds in(NumericDomain<Atom> reading, NumericDomain.Bounds element);
    }

    /**
     * Where {@code over} runs, or null where nothing here settles it.
     *
     * <p>Null wherever a part is missing rather than an open range, so a caller meets this against
     * what else it knows and a missing part leaves that alone.
     *
     * @param orders the term's own orders. The two numbers the step is applied to stand on the two
     *               of them — a value at the run's path is decoded on what it is observed on, and
     *               what the walk carries is measured on what the term answers — and a range may
     *               not be asserted about a number whose spacing is guessed. The pair is kept apart
     *               here for the reason it is a pair: the operations that reach this answer what
     *               they walk, so the two orders agree today, and a reader collapsing them holds
     *               whichever one it happened to mean on the day one of them does not
     * @param typeAt what stands where a path names
     */
    static NumericDomain.Bounds of(NumericTerm.TakenOver over, TermOrders orders,
                                   Function<TermPath, Type> typeAt, RuleReadingContext reading) {
        orders.areOf(over);
        Accumulation walk = DefaultBoundOperationFacts.get().accumulation(over.operation());
        NumericDomain.Bounds element = ofTheValuesWalked(over.source(), typeAt, reading);
        Granularity answeredOn = spacingOf(orders.answered());
        Granularity observedOn = spacingOf(orders.observed());
        if (walk == null || element == null || answeredOn == null || observedOn == null) {
            return null;
        }
        NumericDomain.Bounds seed = startedFrom(walk.identity());
        Step step = repeating(walk.combine());
        if (seed == null || step == null) {
            return null;
        }
        Map<Atom, Granularity> kinds =
                Map.of(Atom.ACCUMULATOR, answeredOn, Atom.ELEMENT, observedOn);
        return Induction.proves(element, guaranteed -> new Walked(
                NumericDomain.<Atom>top().assuming(Atom.ELEMENT, guaranteed, kinds),
                guaranteed, seed, step, kinds));
    }

    /**
     * What every value the run walks guarantees, or null where the declarations say nothing of it.
     *
     * <p>Asked of the type standing where the run reads from, about that value itself. Every value
     * the walk is handed is a value of that type, so what such a value guarantees of itself is what
     * may be assumed of each of them — and it is the same answer at every occurrence, which is what
     * a walk that fixes no case and no condition needs.
     *
     * <p>Nothing here works out what a clause of some value above it calls this place. A record's
     * own rule about a field it holds would sharpen this and is not read: reaching it means deciding
     * which steps of a place are names a rule writes, and that question has one home
     * ({@code TermPath.ruleKeyUnder}) which hands its answer to the readers chosen for it. What is
     * lost by not asking is sharpness and never soundness — a bound this does not have is a bound
     * nothing is claimed from.
     *
     * <p>What comes back is a range out of a numeric domain, so both its ends are numbers. That is
     * what lets it be asserted about an atom as it stands: a range whose end is a value the
     * arithmetic has no number for — a text position stopping at {@code "A"} — is refused where one
     * is asked for, and a reader wiring some other source of element bounds in here owes that
     * filter.
     */
    private static NumericDomain.Bounds ofTheValuesWalked(RunSource source,
                                                          Function<TermPath, Type> typeAt,
                                                          RuleReadingContext reading) {
        Type walked = typeAt.apply(source.subjectPath());
        return walked == null ? null
                : ValueGuarantees.of(walked, reading).get(RuleKey.THE_VALUE);
    }

    /** The value the walk starts from, as a range, or null where this reading has no number for it.
     *  The empty list a {@code List.concat} starts from is a value the library states and this
     *  reading has no number for. */
    private static NumericDomain.Bounds startedFrom(Accumulation.Identity identity) {
        return switch (identity) {
            case ZERO -> at(BigDecimal.ZERO);
            case ONE -> at(BigDecimal.ONE);
            case EMPTY -> null;
        };
    }

    /** How the values on one order are spaced, or null where nothing orders them. */
    private static Granularity spacingOf(souther.compiler.check.Carrier on) {
        return on == null ? null : on.spacing();
    }

    private static NumericDomain.Bounds at(BigDecimal value) {
        Endpoint end = Endpoint.inclusive(Count.of(value));
        return new NumericDomain.Bounds(end, end);
    }

    /** The step the walk repeats, or null where this reading carries no arithmetic for it. */
    private static Step repeating(Accumulation.Combine combine) {
        return switch (combine) {
            case ADD -> (reading, _) -> reading.boundsOf(CARRIED);
            case MULTIPLY -> (reading, element) ->
                    Intervals.product(reading.boundsOf(ACCUMULATOR), element);
            case APPEND -> null;
        };
    }

    /**
     * One walk over a run, read against what is assumed of its two numbers.
     *
     * <p>The element is assumed once, where this is made, and the accumulator is assumed a candidate
     * at a time. Both are read out of the one domain, so what a candidate was made from and what it
     * is checked against are the same assumptions.
     */
    private record Walked(NumericDomain<Atom> reading, NumericDomain.Bounds element,
                          NumericDomain.Bounds seed, Step repeats, Map<Atom, Granularity> kinds)
            implements Induction.Prepared {

        @Override
        public boolean isBottom() {
            return reading.isBottom();
        }

        @Override
        public NumericDomain.Bounds seed() {
            return seed;
        }

        @Override
        public NumericDomain.Bounds step() {
            return repeats.in(reading, element);
        }

        @Override
        public Collection<NumericDomain.Bounds> whatTheStepIsHanded() {
            return List.of(element);
        }

        @Override
        public Induction.Prepared assuming(NumericDomain.Bounds candidate) {
            return new Walked(reading.assuming(Atom.ACCUMULATOR, candidate, kinds),
                    element, seed, repeats, kinds);
        }
    }
}
