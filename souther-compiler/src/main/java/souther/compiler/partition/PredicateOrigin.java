package souther.compiler.partition;

import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.check.RuleReportAnchor;

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
 * <p><b>Three things and three layers, none of them standing for another.</b> Which rule of the
 * model this is, minted where the source was read and the same at every call of a helper holding it.
 * {@link #occurrence} is which of this body's readings of that rule this one is, issued by the walk
 * that met them. And this value is what a piece of evidence carries: the identity of one reading,
 * which is what an account of what became of each rule is filed under.
 *
 * <p>The rule through the handle and not beside it. Which predicate this reads and how a reader is
 * sent to it are one answer, and held as two components they could be built about two rules — where
 * the identity a document files this under and the sentence it writes beside it are of different
 * rules ({@link RuleCitation}).
 *
 * @param occurrence which reading of that rule this is. What tells two readings of one rule apart,
 *                   and the only thing here that does — {@code helper("JP", code)} and
 *                   {@code helper("US", code)} read one rule twice and divide one position two ways
 * @param rule       which predicate, which is the rule and the whole of it
 * @param anchor     which question says where it is written, which is how a reader finds a rule
 *                   with no name. About the application: a condition holding two predicates is two
 *                   rules, and a reader sent to the condition is given one handle for both
 */
public record PredicateOrigin(PredicateOccurrence occurrence, RuleRef.Predicate rule,
                              RuleReportAnchor anchor)
        implements RuleEvidenceOrigin {

    public PredicateOrigin {
        if (occurrence == null || rule == null || anchor == null) {
            throw new IllegalArgumentException(
                    "a reading of a predicate is a reading of some rule, told from the others, and"
                            + " written somewhere");
        }
    }

    /**
     * How a reader finds it, which is by where it is written.
     *
     * <p>Made here rather than kept, so that the handle is of {@link #rule} and can be of no other.
     * Kept beside the rule, the two could be built about different predicates — and a document
     * writing both would file an entry under one rule with a sentence about another.
     */
    @Override
    public RuleCitation cited() {
        return new RuleCitation.Written(rule, anchor);
    }
}
