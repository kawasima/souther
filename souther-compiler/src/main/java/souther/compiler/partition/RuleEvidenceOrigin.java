package souther.compiler.partition;

import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;

/**
 * One reading of one rule that said something about how a position's values are divided.
 *
 * <p>Identity and nothing else. Which rule of the model this is a reading of, and how a reader finds
 * that rule: those are the two questions every such reading answers, whatever the rule did. What it
 * did is the answer of whichever kind of reading it is, and it is not here.
 *
 * <p><b>Two kinds, because a model divides a position two ways.</b> A rule can put a line on the
 * order the values are counted on — a bound, a comparison, a conjunct of an {@code ensures} — and
 * what such a reading carries is where the line falls, which side of it the written value is on and
 * what a row at either point shows ({@link LineOrigin}). Or it can tell a set of values from the
 * rest, which is what a predicate over the strings at a position does, and there is no line, no side
 * and no point ({@link PredicateOrigin}). The two were one type while every piece of evidence was a
 * line, and the equation stopped holding the day a rule divided a position without drawing one.
 *
 * <p><b>Which is why the line's vocabulary stays below.</b> A reader holding one of these cannot ask
 * what the line was: there is no such method here, so a reading with no line cannot be asked for one
 * and cannot answer with something invented. Raised to this level, {@code lineFacts} would be a
 * total contract with an arm that has nothing true to say — and what a reader would get is a side
 * about a line that has none, read as though it were an answer.
 *
 * <p><b>And identity is the whole value, never the rule alone.</b> A non-recursive helper is
 * expanded at each call, so one rule the author wrote is read at several places, each dividing the
 * position it names differently. What files two of those apart is what tells the readings apart,
 * which each kind holds its own answer to — so an account keyed by {@link #rule()} would close on
 * the first reading everything the rest were owed.
 */
public sealed interface RuleEvidenceOrigin permits LineOrigin, PredicateOrigin {

    /**
     * Which rule of the model this is a reading of.
     *
     * <p>The same value however many times the rule is read, which is what makes it the rule and
     * not the reading ({@link RuleRef}).
     */
    RuleRef rule();

    /**
     * The handle a report sends a reader to the rule by.
     *
     * <p>The other half of the same question: which rule it is, and how a reader finds it. A rule
     * the author named is found by that name wherever it is read; one written rather than named is
     * found by where it is written, which is a place no reader can invent.
     */
    RuleCitation cited();
}
