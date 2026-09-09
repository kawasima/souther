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
 * <h2>What makes two of them one</h2>
 *
 * <p>An author's editing place, and not how many times a reading met the operator. How many times
 * it was met is the reading's own answer ({@code check.ChoiceSite}), which is an identity two runs
 * over one model give differently and which no published answer may hold. What an author is owed is
 * how many places they have to go, and one operator written once is one place however often an
 * expansion copies it: rewriting it there answers every copy.
 *
 * <p>So a place is one somebody can edit, and {@link #at} is the one thing that decides whether a
 * citation is one. A citation whose code is written elsewhere — spliced in from a module this
 * compile holds no file for — points at the call rather than at the operator, and an author sent
 * there under a word meaning "inside the rule" would be looking for a {@code ||} that is not in
 * front of them and could not edit it if it were. There is nothing inside such a rule to send them
 * to, so what they get is the rule.
 *
 * <p>Which leaves this type with one thing to be wrong about and a constructor that refuses it.
 * The alternative was to carry every citation and say in prose which of its five arms count, and
 * prose is what was wrong here before: this file said expansions were told apart by where they were
 * reached, which is the opposite of what {@link #at} does and of what the report was already
 * measured to say.
 */
public sealed interface WhereInTheRule {

    /** The rule, with nothing inside it singled out: what a reader lifts is the whole of it. */
    record TheRuleItself() implements WhereInTheRule {}

    /**
     * One place inside the rule, which is where a reader is sent instead of to the rule.
     *
     * <p>Told from another by the citation, which is where the code is written rather than an
     * identity of it. Reached only through {@link #at}, which is why the refusal below cannot be
     * met: a caller that could build one of these from any citation is a caller that can send an
     * author inside a rule they do not have.
     */
    record APlaceInIt(Citation at) implements WhereInTheRule {

        public APlaceInIt {
            if (at == null) {
                throw new IllegalArgumentException("a place inside a rule is somewhere");
            }
            if (at instanceof Citation.Elsewhere) {
                throw new IllegalArgumentException("a place inside a rule is one an author can"
                        + " edit, and code written elsewhere has none: " + at);
            }
        }
    }

    /** The rule as a whole, for a reader with nothing inside it to point at. */
    static WhereInTheRule theRuleItself() {
        return THE_RULE_ITSELF;
    }

    /**
     * The place {@code at} names, where an author wrote the code there, and the rule where they
     * did not.
     *
     * <p>The one place the question is asked, so that the two readings short at one operator answer
     * it alike. Asked twice, the day one of them treated a splice as a place inside the rule the
     * other would still be sending readers to the rule, and the addresses a consumer joins on would
     * have come apart for a reason nothing in the model says.
     */
    static WhereInTheRule at(Citation cited) {
        return cited instanceof Citation.Elsewhere ? theRuleItself() : new APlaceInIt(cited);
    }

    /** The one of those, since it holds nothing and two of them say the same thing. */
    WhereInTheRule THE_RULE_ITSELF = new TheRuleItself();
}
