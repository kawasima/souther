package souther.compiler.query;

import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;

import java.util.List;

/**
 * What a search for something standing in one rule of a body's decision came to.
 *
 * <p>Only the first of these settles anything about the model. A row composed and seen taking the
 * rule is what shows something can stand there, and that is the proof ADR-0091 asks for; every other
 * answer is this compiler having looked and not found, which leaves the rule where it was.
 *
 * <p><b>And a witness is not a row anybody is owed.</b> What this composed is evidence that the rule
 * can be reached; whether the rows written for the behavior reach it is the other question, and a
 * value this search built is in nobody's {@code example} block. The two are the same object read for
 * two things, and reading one for the other is what would let a search satisfy a coverage item
 * nobody wrote a row for.
 */
public sealed interface RuleWitness {

    /**
     * A row was composed and its run took the rule.
     *
     * @param by the values it was composed at, which a proposal for the rule may be offered from
     */
    record Stands(List<FixtureTemplate> by) implements RuleWitness {

        public Stands {
            by = List.copyOf(by);
        }
    }

    /**
     * A row was composed against the rule and its run went elsewhere.
     *
     * <p>Not the rule being unreachable. Which values were tried is this search's choice, and a
     * reading anywhere in the chain from a condition to a class may have steered them wrong — which
     * is why what a run did is asked at all.
     */
    record WentElsewhere() implements RuleWitness {}

    /** Nothing composed a row against the rule, in the words a search comes back with. */
    record NothingComposed(Generator.UnresolvedCombination why) implements RuleWitness {}

    /**
     * A row was composed and nothing watched it run.
     *
     * <p>Told apart from a run that went elsewhere, which is what an empty account would read as. A
     * build that does not instrument its rows records no place, and a row nobody watched says
     * nothing about which rule it took.
     */
    record NothingWatched() implements RuleWitness {}
}
