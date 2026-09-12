package souther.compiler.query;

import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.Requirements;
import souther.compiler.partition.Generator;
import souther.compiler.partition.RowToRun;
import souther.compiler.partition.RulesTaken;

/**
 * What settles whether a rule of a body's decision is owed a row.
 *
 * <p>The three states ADR-0091 fixes, and they do not reduce to one another. A rule something has
 * been shown to stand in is required; one the model's own rules leave no value for is excluded;
 * one this compiler looked at without finding is neither, and stays where it was.
 *
 * <p><b>None of them is about what the rows do.</b> Whether an authored row takes a rule is the
 * coverage measure's question and is answered against the runs; this asks whether anything could
 * take it at all. A rule every row misses may be required, and a rule no value can reach is
 * excluded however the rows are written — the two questions are orthogonal, and a sentence here
 * that said "no row takes it" would put the coverage answer in the requirement's mouth.
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
     * The model's own rules leave no value that takes this rule, so no run of it exists.
     *
     * <p>A fact about the model, and the one answer here that is not about what this compiler
     * managed. Not "no row takes it": that is what the rows happen to do and no author is refused
     * over it here; this says no row anybody writes could.
     *
     * <p><b>Two readings already establish it and neither is the other.</b> One is about the way
     * itself — it asks a position to be two things at once, which no value is. The other is about
     * a construct the way goes through — a case a position's own rules refuse has an arm nothing
     * arrives at, and the arms are already counted without it. Carried as what each established
     * rather than folded to a word, so that a reader is sent to the reading that answered.
     */
    sealed interface Excluded extends RuleRequirement {

        /**
         * The way asks one position to be two things at once.
         *
         * <p>{@link souther.compiler.partition.Reachability.NothingReaches} is where that is
         * established and this carries what it established.
         */
        record OnePositionCannotBeBoth(Requirements.Merge.Conflict why) implements Excluded {

            public OnePositionCannotBeBoth {
                if (why == null) {
                    throw new IllegalArgumentException(
                            "a way nothing can take is one something showed nothing can take");
                }
            }
        }

        /**
         * The way goes through an arm the readings show nothing arrives at.
         *
         * <p>The same fact the branch measure counts by, asked here rather than answered again. An
         * arm for a case the position's own rules refuse is out of that count, and a rule whose way
         * goes down it is out of this one — read separately, a search would compose against the
         * arm, have every candidate refused, and report the model's own answer as this compiler
         * having looked and not found.
         *
         * <p>The arm the author wrote and not a place a run through it is recorded at. A helper
         * carrying a fork stands once per call site, and an arm one call site cannot reach is one
         * another may — so what shows the way out of reach is the arm being out of the count, which
         * is settled over every copy of it at once.
         *
         * <p>Arms and not every construct on the way. What a comparison's outcome was proven to be
         * is the other half of the same reading and the sites have no place for it to be asked at,
         * so a rule turning on one is settled the way it was before.
         */
        record AnArmNothingReaches(CoverageSites.Obligation arm) implements Excluded {

            public AnArmNothingReaches {
                if (arm == null) {
                    throw new IllegalArgumentException("an arm nothing reaches is some arm");
                }
            }
        }
    }

    /**
     * Something was seen standing in the rule, which is what shows a row can be written at it.
     *
     * @param stoodBy the row it was composed at — the values at the positions and what the
     *                dependencies were stood in with — which a proposal for the rule may be offered
     *                from. Whole, because what was seen taking the rule is the whole of it: a
     *                behavior deciding on what a dependency answers takes one rule under one answer
     *                and another under another, and the values alone would be offered as a row that
     *                takes either. Not the proposal itself: what this shows is that the rule can be
     *                reached, and a row an author can complete has more to it than that
     */
    record Required(RowToRun stoodBy) implements RuleRequirement {

        public Required {
            if (stoodBy == null) {
                throw new IllegalArgumentException("a rule stood in is stood in by some row");
            }
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
