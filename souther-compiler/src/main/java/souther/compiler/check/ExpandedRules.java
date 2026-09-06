package souther.compiler.check;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The clauses a walk of a declaration and what it spreads found, and whether it found all of them.
 *
 * <p>Two things and not one, because a walk can come back with clauses and still be short. A
 * declaration spreads three types, one of which belongs to a module whose expansion could not be
 * worked out: what the other two state is true of every value of it and worth reading, and the rule
 * the third states was not read at all. A list alone says the first and loses the second, and a
 * reader with the list reports a value as held to exactly what it managed to read.
 *
 * <p>{@link #everyRuleReached} is what a reading turns into the widening it already has a word for.
 * It is not "the reading understood every clause" — a clause read to the end and found to be a shape
 * nothing takes apart is reached, and is that reading's own limit to report. It is the narrower
 * claim that nothing was left out before the reading began.
 */
public record ExpandedRules(List<TypeOps.Declared> reached, boolean everyRuleReached) {

    public ExpandedRules {
        reached = List.copyOf(reached);
    }

    /**
     * The clauses, where every rule that applies was reached, and nothing where one was not.
     *
     * <p>The way out for a reader that may not proceed on part of the rules — one building
     * something a value is afterwards held to, where what is not read is what is not enforced.
     * Empty is not a shorter list here: it is the reader being told it does not have what it asked
     * for, in a shape it cannot mistake for a declaration that states nothing.
     *
     * <p>There is deliberately no accessor that hands over the clauses alone. Every way to them
     * says which obligation the reader is meeting: this one, or {@link #reached()} for a reader
     * that has already recorded what it did not get.
     *
     * <p>And the clauses as what was reached rather than as their trees, so that a reader holding
     * one holds the shape its author wrote it in beside it. Handed the trees alone, a reader
     * wanting the parts had to split one, and what a helper's body joined is a conjunction there.
     */
    public Optional<List<TypeOps.Declared>> whole() {
        return everyRuleReached ? Optional.of(reached) : Optional.empty();
    }

    /** These and {@code other}'s together, reaching everything only where both did. */
    ExpandedRules and(ExpandedRules other) {
        List<TypeOps.Declared> both = new ArrayList<>(reached);
        both.addAll(other.reached);
        return new ExpandedRules(both, everyRuleReached && other.everyRuleReached);
    }
}
