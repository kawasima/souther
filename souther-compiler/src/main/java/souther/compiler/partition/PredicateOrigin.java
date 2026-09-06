package souther.compiler.partition;

import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;

/**
 * One reading of a predicate applied in a behavior's body.
 *
 * <p>Beside {@link LineOrigin} and holding less, because a predicate places no line. There is no
 * side of a value it keeps, no neighbour on the other side, no point a row stands at against it, and
 * nothing about where a run through it is written down — a class such a rule divides a position into
 * is met by writing a value there rather than by getting anything to answer. What the rule leaves is
 * which values it tells from the rest, and that is a fact about the sets it admits, said by whoever
 * worked them out and not carried here.
 *
 * <p><b>Three things and three layers, none of them standing for another.</b> {@link #rule} is which
 * rule of the model this is, minted where the source was read and the same at every call of a helper
 * holding it. {@link #occurrence} is which of this body's readings of that rule this one is, issued
 * by the walk that met them. And this value is what a piece of evidence carries: the identity of one
 * reading, which is what an account of what became of each rule is filed under.
 *
 * @param rule       which predicate, which is the rule and the whole of it
 * @param occurrence which reading of that rule this is. What tells two readings of one rule apart,
 *                   and the only thing here that does — {@code helper("JP", code)} and
 *                   {@code helper("US", code)} read one rule twice and divide one position two ways
 * @param written    how a reader finds the rule, which is where it is written. The application's own
 *                   place: a condition holding two predicates is two rules, and a reader sent to the
 *                   condition is given one handle for both
 */
public record PredicateOrigin(RuleRef.Predicate rule, PredicateOccurrence occurrence,
                              RuleCitation.WrittenAt written)
        implements RuleEvidenceOrigin {

    public PredicateOrigin {
        if (rule == null || occurrence == null || written == null) {
            throw new IllegalArgumentException(
                    "a reading of a predicate is a reading of some rule, told from the others, and"
                            + " written somewhere");
        }
    }

    /** Where it is written, which is how a rule with no name is found. */
    @Override
    public RuleCitation cited() {
        return written;
    }
}
