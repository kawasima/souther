package souther.compiler.inputs;

import souther.compiler.check.RuleCitation;
import souther.compiler.diag.Citation;

/**
 * Where inside a rule an author is sent, which is not a second way to name the rule.
 *
 * <p>What a reader lifts is a rule for most of what this compiler is short of, and a part of one
 * for the rest. A clause the reading of ends could not turn into a line is lifted by rewriting the
 * clause; an end a choice in it left open is lifted at the {@code ||}, and the clause beside it
 * reads perfectly well. Said with the rule alone, the second is the first — which is how two
 * choices of one clause came to one entry that named neither.
 *
 * <p><b>Not {@link RuleCitation}.</b> That answers how a reader finds the rule, and
 * {@link RuleCitation#requireReached} refuses a place beside a rule the author named because there
 * a place would be a second spelling of the identity. This answers a different question — where in
 * the rule, once it is found — so a rule with a name has one of these that points inside it and is
 * still found by its name.
 *
 * <p><b>{@link Citation} and not a bare position.</b> A choice written in a helper and expanded
 * twice is one operator an author wrote and two places this compile met it, and the two are what a
 * reader is sent to: {@link Citation.Reached} carries the call each copy stands in, so the
 * expansions are told apart by where they were reached rather than by an identity a published
 * answer may not hold. A choice written in the clause itself is {@link Citation.Written} and is one
 * however often it is read.
 *
 * <p>Which makes this the join between the two readings. Both are short at one choice, each says a
 * different thing about it, and both say it of the same place — so a consumer holding what the
 * values left and what the ends left knows they are one thing to fix.
 */
public sealed interface WhereInTheRule {

    /** The rule, with nothing inside it singled out: what a reader lifts is the whole of it. */
    record TheRuleItself() implements WhereInTheRule {}

    /**
     * One place inside the rule, which is where a reader is sent instead of to the rule.
     *
     * <p>Told from another by the citation, which is where this compile met the code rather than an
     * identity of it.
     */
    record APlaceInIt(Citation at) implements WhereInTheRule {

        public APlaceInIt {
            if (at == null) {
                throw new IllegalArgumentException("a place inside a rule is somewhere");
            }
        }
    }

    /** The rule as a whole, for a reader with nothing inside it to point at. */
    static WhereInTheRule theRuleItself() {
        return THE_RULE_ITSELF;
    }

    /** The one of those, since it holds nothing and two of them say the same thing. */
    WhereInTheRule THE_RULE_ITSELF = new TheRuleItself();
}
