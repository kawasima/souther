package souther.compiler.check;

import souther.compiler.core.Contract.Guard;
import souther.compiler.core.Contract.Param;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.List;
import java.util.Optional;

/**
 * What a caller of a behavior may take as holding of its answer.
 *
 * <p>The declaration's own reading is {@link StatedContract}, which says where each conjunct was
 * written and holds the terms the author's places are on — what an editor shows and what a
 * diagnostic reports by. This is what is left of it when the question is a caller's: it substitutes
 * its own arguments into the terms and reads what they say, and it reports nothing, so where the
 * author wrote them is no part of what it depends on.
 *
 * <p>Two types and not one reading used two ways. A caller's answer is one a query keeps, so what
 * {@code equals} says of it is what stops the work downstream ({@link souther.compiler.query.Db});
 * were it the declaration's reading, a blank line above the behavior would make every body that
 * calls it a body to check again. The terms here are {@link TermMeaning}, which is a term read for
 * what it says and gives no way to ask where it stands — so the value this hands over and the value
 * it is compared by are the same value.
 *
 * <p>A rule is what applies it, what {@code value} stands for where it does, and what it states. Not
 * which clause it was written under nor which rule of the declaration it is: both name the rule for
 * a report, and a caller writes none. Carried, they would put the declaration's own numbering into
 * what a caller depends on, and a clause written above another would be an edit to every caller of
 * the behavior.
 */
public record AssumedContract(ValueName.Behavior behavior, List<Param> params, Type output,
                              List<AssumedRule> rules) {

    public AssumedContract {
        params = List.copyOf(params);
        rules = List.copyOf(rules);
    }

    /**
     * Where a test reads the behaviors whose rules a caller took in, one entry per conjunct
     * substituted into a call, and null everywhere else.
     *
     * <p>Beside {@link PathEngine#SEEDED} and for the same reason. Taking a rule in is silent: what
     * it does is narrow what the caller may hold of the answer, and a body that is correct either
     * way is checked either way. So a compile that stopped reading contracts at all would say
     * nothing about it, and the whole of what an author would notice is a diagnostic this analysis
     * exists to avoid needing.
     *
     * <p>What is recorded is the behavior whose contract it is, because that is what a reader of
     * this wants to name — whether a compile reached the assumption a caller of <em>this</em>
     * behavior depends on. Where it was taken in is not here: a call carries no occurrence, so the
     * body would have to be threaded through this analysis for a reader that has the corpus in front
     * of it and can ask which bodies call what.
     *
     * <p>Public because the corpora this is asked over live in another module. Assigned by the test
     * that is reading and set back to null after; a compile running while it is assigned writes to
     * it from whatever thread it is on, so what is assigned has to take concurrent writes.
     */
    public static List<ValueName.Behavior> TAKEN_IN;

    /**
     * Records that a rule of this contract was substituted into a caller's arguments.
     *
     * <p>Called where the substitution is made and nowhere else, so what lands here is what a caller
     * took in rather than what it was offered: a contract handed to the analysis and never reached —
     * because the call went to no arm, or because the hand-over stopped — is not one of these.
     */
    void takenIn() {
        List<ValueName.Behavior> watching = TAKEN_IN;
        if (watching != null) {
            watching.add(behavior);
        }
    }

    /** One rule: when it applies, what {@code value} is where it does, and what it states. */
    public record AssumedRule(Guard guard, BindingId value, List<Conjunct> conjuncts) {

        public AssumedRule {
            conjuncts = List.copyOf(conjuncts);
        }
    }

    /**
     * One conjunct as a caller has it: what it comes to, or nothing where typing it did not finish.
     *
     * <p>An absence and not a second arm. What a reading that stopped licenses about the model is a
     * question for whoever reports on the declaration ({@link TypedClause}); to a caller both come
     * to the same thing, which is that there is nothing here to take as holding.
     */
    public record Conjunct(Optional<TermMeaning> means) {

        public Conjunct {
            if (means == null) {
                throw new IllegalArgumentException("a conjunct says what it comes to or that it did"
                        + " not come to anything");
            }
        }
    }

    /** Where nothing is stated this can be read from, so a caller asking what to assume has nothing
     *  to take from it. */
    public boolean isEmpty() {
        return rules.isEmpty();
    }
}
