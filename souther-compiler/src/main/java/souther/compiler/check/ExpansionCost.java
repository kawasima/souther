package souther.compiler.check;

import souther.compiler.core.Core;

import java.util.List;

/**
 * How many alternatives reading a clause as a union of products would come to, counted before any
 * of them is built.
 *
 * <p>A budget has to be measurable before the work it limits, and the number of alternatives a
 * clause really comes to is not: finding that out is the expansion this is asked to bound. So what
 * is counted is the syntax. A choice adds, a conjunction multiplies, a denial swaps the two, and
 * anything else is one.
 *
 * <p>Duplicates are not refunded. {@code A || A || B} costs three, though a reading of it holds two
 * alternatives: telling that it holds two takes the normalisation whose cost this stands in for.
 * What is promised is an upper bound on what a reading will build, and never the count itself.
 *
 * <p><b>Counted on the fold that does the reading, and not by a walk of its own.</b> Which shapes
 * are connectives, where a denial goes and what is left as a leaf are {@link ClauseReading}'s. A
 * second walk stating that again is a second answer, and the two agree only until somebody changes
 * one of them. Read as a {@code ClauseReading<Long>} the recursion is the same code, so a shape
 * added to the reading is one this counts by the same act.
 *
 * <p>What comes apart there is not a wrong number. It is a declaration admitted under a budget it
 * then expands past, which is the dynamic widening the domain is chosen before the fold to avoid.
 *
 * <p>The count saturates at one past the limit it was asked about. Past that the number is not
 * wanted — what is asked of it is whether the limit is exceeded — and a conjunction of choices
 * leaves the range of a count long before it leaves the range of what an author can write.
 *
 * <p>A binding neither adds nor multiplies: what a clause under one comes to is what the clause
 * comes to. So this carries no environment and its scope leaves what it was given
 * ({@link ClauseScope#unchanged}) — the count is over the shape, and a shape that composes nothing
 * costs nothing of its own.
 */
final class ExpansionCost implements ClauseReading<Long, Void> {

    /** One past the limit, which is where the arithmetic stops.
     *
     *  <p>Counted in a wider type than the limit is written in, and answered in one. Counted in the
     *  limit's own type, one past the largest limit is the smallest number there is and every count
     *  is under it — so the guardrail would admit exactly the expansions it exists to refuse, and
     *  would do it at the one limit somebody reaching for "no limit" would write. */
    private final long ceiling;

    ExpansionCost(int limit) {
        this.ceiling = (long) limit + 1;
    }

    /**
     * What the clauses of one declaration come to, which is their product: they are met, and a meet
     * of two unions is the union of the pairs.
     *
     * @param limit the count is asked about, saturating at one past it
     */
    static long of(List<Core> clauses, int limit) {
        ExpansionCost counting = new ExpansionCost(limit);
        long cost = counting.nothingSaid();
        for (Core clause : clauses) {
            cost = counting.both(cost, counting.read(clause, true, null, ClauseScope.unchanged()));
        }
        return cost;
    }

    /** Nothing read is one alternative — the empty product — and is the identity of the fold. */
    private long nothingSaid() {
        return 1L;
    }

    /**
     * One, whatever a reading later makes of it.
     *
     * <p>A part a reading has no word for is one alternative and not none: what it leaves is
     * everything, which is a box like any other. Counting it as none would let a clause of unread
     * parts come out costing nothing and be admitted under any budget.
     */
    @Override
    public Long whole(Core e, boolean positive, Void at) {
        return 1L;
    }

    /**
     * Both connectives are counted to the end, because what is being counted is what the shape
     * composes: a conjunction multiplies the alternatives of its halves and a choice adds them.
     */
    @Override
    public Descent<Long> at(ClauseExpr.Joined join) {
        return switch (join.how()) {
            case BOTH -> new Descent.Into<>(this::both);
            case EITHER -> new Descent.Into<>(
                    (one, other) -> Math.min(ceiling, one + other));
        };
    }

    private Long both(Long one, Long other) {
        return Math.min(ceiling, one * other);
    }
}
