package souther.compiler.numeric;

/**
 * The order a position's values run on, as far as anything holding a range has to know it.
 *
 * <p>What a range is a range of. An {@link OrderedInterval} is a pair of ends and says which values
 * it leaves only against this: {@code [Long.MIN_VALUE .. Long.MAX_VALUE]} and a pair of absent ends
 * are two writings of every {@code Int} and two different sets of every decimal, and nothing about
 * the pair alone tells them apart.
 *
 * <p>One method, and it is the one question the ranges have of a carrier. What a rule's literal
 * counts to, what a value is written back as, and which machine a pattern names are the carrier's
 * as well and are no part of this — held here, the arithmetic would depend on the language's types,
 * and the type that answers all of them ({@code souther.compiler.check.Carrier}) implements this
 * rather than being reached from here.
 */
public interface ValueOrder {

    /**
     * Every value this order has, as the ends it runs between.
     *
     * <p>Where a reading of the rules starts, and what a range is interpreted against. A pair of
     * absent ends is not this: it is a range that stops nothing, which is every value of the order
     * for a carrier that runs on forever and is more than the order has for one that stops.
     */
    OrderedInterval extent();
}
