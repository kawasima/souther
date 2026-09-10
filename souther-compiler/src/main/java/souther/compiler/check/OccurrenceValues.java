package souther.compiler.check;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.types.TypeSymbol;

import java.math.BigDecimal;
import java.util.Set;

/**
 * How many values the value at a name may take, which is not how much it holds.
 *
 * <p>The same numbers answer two questions and they are never one question. A rule reading
 * {@code >= 2} bounds the values of a field where the field is a number and counts what it holds
 * where something counts it, and a set asked to hold two of an element bounded to a single number is
 * refused on the first reading of the element and the second of the set. Kept apart from
 * {@link OccurrenceCounts} for that reason and not because the readings differ.
 *
 * <p>An upper bound and not a count. A rule the reading could not take in leaves the ends wider than
 * the values reach — a range of eight with a hole in it is eight here and seven in fact — and that is
 * the direction this is for. What the number may never be is short of the values there are, so it is
 * given only where both ends are there and the values between them are spaced one apart. Where they
 * are spaced at all, which the ends themselves do not say: a decimal bounded at either end lies
 * between two whole numbers as readily as an integer does, and there are unboundedly many of it.
 *
 * <p>A declaration whose rules contradict answers nothing here: the ends are gone along with
 * everything else the domain held. That a type has no value at all is
 * {@link FieldDomains#infeasible()}, asked of the declaration and before this.
 */
public final class OccurrenceValues {

    /** What a name no declaration wrote about leaves: no count of values at all. */
    public static final OccurrenceValues NOTHING_READ = new OccurrenceValues(null);

    private final InvariantChecker.Seeded seeded;

    private OccurrenceValues(InvariantChecker.Seeded seeded) {
        this.seeded = seeded;
    }

    /** What the declaration {@code named} is leaves the values at each of its names. */
    public static OccurrenceValues of(TypeSymbol.AtModule named, RuleReadingSource source,
                                      ReadingPolicy policy, DeclarationReadings machines) {
        return of(named, source, policy, Set.of(), machines);
    }

    /** The same, reading for itself. */
    public static OccurrenceValues of(TypeSymbol.AtModule named, RuleReadingSource source,
                                      ReadingPolicy policy) {
        return of(named, source, policy, Set.of(), DeclarationReadings.NONE);
    }

    /** The same, with the declarations {@code granted} names supposed to hold values, reading for
     *  itself. */
    static OccurrenceValues of(TypeSymbol.AtModule named, RuleReadingSource source,
                                 ReadingPolicy policy, Set<TypeSymbol> granted) {
        return of(named, source, policy, granted, DeclarationReadings.NONE);
    }

    /**
     * The same, with the declarations {@code granted} names supposed to hold values.
     *
     * <p>Read this way by whatever is asking what would be true if some declaration had values. Its
     * rules are what say it has none — its own, and the ones under whatever it wraps — so supposing
     * it has a value is not reading it at all.
     */
    static OccurrenceValues of(TypeSymbol.AtModule named, RuleReadingSource source,
                                 ReadingPolicy policy,
                                 Set<TypeSymbol> granted,
                                 DeclarationReadings machines) {
        return new OccurrenceValues(
                InvariantChecker.seedFields(named, source, policy, java.util.Map.of(),
                        InvariantChecker.Reach.stoppingAt(granted), machines));
    }

    /**
     * How many whole numbers the value at {@code path} may be, or {@link Cardinality#UNKNOWN} where
     * that is not what it is or the rules leave it open at an end.
     *
     * <p>Both ends are read as they are held. A number spaced one apart has its
     * ends recorded as whole numbers it stops at, so an end that is neither is one this cannot
     * count — and counting it anyway by rounding would name a number of values the rules never left.
     */
    public Cardinality wholeValuesAt(RuleKey path) {
        if (seeded == null) {
            return Cardinality.UNKNOWN;
        }
        FactSubject atom = seeded.atoms().get(path);
        if (atom == null) {
            return Cardinality.UNKNOWN;
        }
        if (seeded.numbers().spacingOf(atom) != Granularity.DISCRETE) {
            // Ends that happen to be whole are what a value between two of them has as well, so a
            // reading that took them for the count would give a decimal bounded either way as many
            // values as an integer between the same two. Asked of the spacing and not of the ends.
            return Cardinality.UNKNOWN;
        }
        NumericDomain.Bounds bounds = seeded.numbers().boundsOf(atom);
        BigDecimal least = wholeEnd(bounds.min());
        BigDecimal most = wholeEnd(bounds.max());
        if (least == null || most == null) {
            return Cardinality.UNKNOWN;
        }
        BigDecimal span = most.subtract(least).add(BigDecimal.ONE);
        if (span.signum() <= 0) {
            return Cardinality.none(new Emptiness.EmptyNumericInterval());
        }
        try {
            return Cardinality.atMost(span.longValueExact());
        } catch (ArithmeticException tooMany) {
            return Cardinality.UNKNOWN;
        }
    }

    /** The number an end stops at, or null where it stops at nothing whole. */
    private static BigDecimal wholeEnd(souther.compiler.numeric.Endpoint end) {
        return end != null && end.inclusive() && end.at() instanceof Count at && at.whole()
                ? at.at() : null;
    }
}
