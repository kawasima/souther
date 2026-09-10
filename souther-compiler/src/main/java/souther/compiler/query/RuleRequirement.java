package souther.compiler.query;

import souther.compiler.inputs.Requirements;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.partition.RulesTaken;

import java.util.List;

/**
 * What settles whether a rule of a body's decision is owed a row.
 *
 * <p>The three states ADR-0091 fixes, and they do not reduce to one another. A rule something has
 * been shown to stand in is required; one the readings that already exist show no row takes is
 * excluded; one this compiler looked at without finding is neither, and stays where it was.
 *
 * <p><b>What answers this is not one thing.</b> A search answers the first and the last, and it
 * cannot answer the middle: what a search came back with is always this compiler having looked, and
 * a model refusing a way is a fact the readings already hold. So the middle is its own shape here
 * rather than a word a search comes back with — written the other way round, a reader would have to
 * open a search's reason to find out whether the model said anything, and a reason added to that
 * vocabulary would change what the account means.
 *
 * <p><b>And a witness is not a row anybody is owed.</b> What a search built is evidence that the
 * rule can be reached; whether the rows written for the behavior reach it is the other question,
 * and a value this search built is in nobody's {@code example} block.
 */
public sealed interface RuleRequirement {

    /**
     * The readings that already exist show no row takes this rule.
     *
     * <p>A fact about the model, and the one answer here that is not about what this compiler
     * managed. The way asks one position to be two things at once, which no value is —
     * {@link souther.compiler.partition.Reachability.NothingReaches} is where that is established
     * and this carries what it established rather than a word for it.
     */
    record Excluded(Requirements.Merge.Conflict why) implements RuleRequirement {

        public Excluded {
            if (why == null) {
                throw new IllegalArgumentException(
                        "a way no row takes is one something showed no row takes");
            }
        }
    }

    /**
     * Something was seen standing in the rule, which is what shows a row can be written at it.
     *
     * @param stoodBy the values it was composed at, which a proposal for the rule may be offered
     *                from. Not the proposal itself: what this shows is that the rule can be
     *                reached, and a row an author can complete has more to it than that
     */
    record Required(List<FixtureTemplate> stoodBy) implements RuleRequirement {

        public Required {
            stoodBy = List.copyOf(stoodBy);
        }
    }

    /**
     * This compiler looked and did not find, with what it did.
     *
     * <p>None of these says the rule is out of reach. Which values were tried is this search's
     * choice, and a reading anywhere in the chain from a condition to a class may have steered them
     * wrong — which is why what a run did is asked at all.
     */
    sealed interface Unsettled extends RuleRequirement {

        /** A row was composed against the rule and its run took another. */
        record AComposedRowWentElsewhere() implements Unsettled {}

        /**
         * A row was composed and run, and this reading could not say which rule it took.
         *
         * <p>Beside {@link AComposedRowWentElsewhere} and not among it. That one is a row seen
         * going somewhere else, which is something about where the row went; this is this compiler
         * being unable to place it, which is something about the reading — a rule of the body that
         * no run through it is recorded at leaves every run unplaceable, and so does a run that
         * matched more than one.
         *
         * @param why what stopped the reading placing it, in its own words
         */
        record CouldNotTellWhereTheRowWent(RulesTaken.WhichRule.Why why) implements Unsettled {

            public CouldNotTellWhereTheRowWent {
                if (why == null) {
                    throw new IllegalArgumentException(
                            "a reading that could not place a run says what stopped it");
                }
            }
        }

        /** Nothing composed a row against the rule, in the words a search comes back with. */
        record NothingComposedARow(Generator.UnresolvedCombination why) implements Unsettled {

            public NothingComposedARow {
                if (why == null) {
                    throw new IllegalArgumentException("a search that came to nothing says what of");
                }
            }
        }

        /**
         * A row was composed and nothing watched it run.
         *
         * <p>Told apart from a run that went elsewhere, which is what an empty account would read
         * as. A build that does not instrument its rows records no place, and a row nobody watched
         * says nothing about which rule it took.
         */
        record NothingWatchedTheRow() implements Unsettled {}
    }
}
