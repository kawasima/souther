package souther.compiler.query;

import souther.compiler.partition.DecisionReading;
import souther.compiler.partition.Generator;
import souther.compiler.partition.DecisionRule;
import souther.compiler.partition.RulesTaken;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The decision one behavior's body states, and which of its rules the rows took.
 *
 * <p>Two halves and one value, because the account is a function of the body and the rows together:
 * what rules there are is read off the body alone, and what stands in one is a row that took it.
 * Held apart, a reader would have to put them back together and would be free to put one body's
 * rules beside another's runs.
 *
 * @param read  the rules the body states
 * @param taken which of them the rows were seen taking
 */
public record DecisionEvidence(DecisionReading read, Taken taken) {

    public DecisionEvidence {
        Objects.requireNonNull(read, "a decision is some body's");
        Objects.requireNonNull(taken, "there is always an answer to what the rows took");
    }

    /**
     * The rules no row was seen taking.
     *
     * <p>Named for what this knows, which is the body and the rows written for it. Whether anything
     * can stand in one of these is a further question and a further answer — a search settles it —
     * and a name that said nothing stands in them would be this answering about a world it has not
     * looked at.
     */
    public List<DecisionRule> notTakenByRows() {
        Set<DecisionRule> covered = taken instanceof Taken.Read seen ? seen.rules() : Set.of();
        return read.rules().stream().filter(rule -> !covered.contains(rule)).toList();
    }

    /** What the rows were seen taking, or why nothing was seen. */
    public sealed interface Taken {

        /**
         * Nothing was read, and why.
         *
         * <p>An answer and not an empty set. A behavior whose rows nobody ran and one whose rows ran
         * and took no rule are different things, and a reader handed the empty set for both would
         * report the second of them for the first.
         */
        record NothingWasRead(Why why) implements Taken {}

        /**
         * What every row of the behavior came to.
         *
         * <p><b>Three states and every row is in one.</b> A row whose rule was told is placed; a
         * row something watched and nothing could place took a rule this reading cannot recognise;
         * and a row nothing watched says nothing about which rule it took. The three are different
         * facts and the third is not the second — a run with no account did not go nowhere, it went
         * somewhere nothing recorded.
         *
         * <p>Held to adding up, so that a row cannot go missing between the rows read and what is
         * counted here. That is what a reading of this is for: the numbers say the measurement was
         * made in full only where every row was watched, and a row dropped on the way would make an
         * incomplete reading look complete.
         *
         * @param rowsRead    how many rows this reading was given, which the three below are the
         *                    whole of. Held beside them so that a row cannot go missing between
         *                    the rows written and what is counted: a reading that dropped one is
         *                    refused here rather than reported as a reading of the rest
         * @param rules       the rules some row was seen taking
         * @param rowsPlaced  how many rows were placed at one of them
         * @param rowsNotPlaced rows something watched whose rule this reading could not tell
         * @param rowsNotWatched rows nothing watched, which is this compiler's shortfall and not
         *                       anything about the model
         */
        record Read(int rowsRead, Set<DecisionRule> rules, int rowsPlaced, int rowsNotPlaced,
                    int rowsNotWatched) implements Taken {

            public Read {
                rules = new LinkedHashSet<>(rules);
                if (rowsPlaced < 0 || rowsNotPlaced < 0 || rowsNotWatched < 0) {
                    throw new IllegalArgumentException("rows are counted from none: " + rowsPlaced
                            + "/" + rowsNotPlaced + "/" + rowsNotWatched);
                }
                if (rowsPlaced + rowsNotPlaced + rowsNotWatched != rowsRead) {
                    throw new IllegalArgumentException("a reading of " + rowsRead
                            + " rows accounted for " + (rowsPlaced + rowsNotPlaced + rowsNotWatched)
                            + " of them");
                }
                if (rowsPlaced < rules.size()) {
                    throw new IllegalArgumentException("more rules were taken than rows took one: "
                            + rules.size() + " rules by " + rowsPlaced + " rows");
                }
            }

            /** Whether every row of the behavior was one something watched. */
            public boolean everyRowWasWatched() {
                return rowsNotWatched == 0;
            }
        }

        /** Why nothing was read about which rules the rows took. */
        enum Why {
            /** The build does not run rows with the instrumentation a place is recorded by. */
            THE_ROWS_ARE_NOT_INSTRUMENTED,
            /** No row names this behavior, so nothing ran to take a rule. */
            NO_ROWS,
            /** Every rule of the body carries a condition no run through it is recorded at. */
            NO_RULE_IS_RECOGNISABLE
        }
    }

    /** The rules the body states, for a reader that asks what it decides and not what ran. */
    public List<DecisionRule> rules() {
        return read.rules();
    }

    /**
     * How many rules some row was seen taking, where anything was read.
     *
     * <p>Absent where nothing was: a number of zero says the rows took none of the rules, which is
     * not what a build that read no rows found out.
     */
    public OptionalInt covered() {
        return taken instanceof Taken.Read read
                ? OptionalInt.of(read.rules().size()) : OptionalInt.empty();
    }

    /**
     * What the rows of one behavior came to, one answer per row.
     *
     * <p>Walked over the rows and never over what came back watched. A row nothing watched is a row
     * all the same, and taking the accounts first and the rows never would leave it out of every
     * number here — which is a reading that went without something reporting that it did not.
     *
     * @param watched what watched each row, which is an account or the fact that there is none
     */
    public static Taken of(RulesTaken against, List<Generator.Watched> watched) {
        Set<DecisionRule> took = new LinkedHashSet<>();
        int placed = 0;
        int notPlaced = 0;
        int notWatched = 0;
        for (Generator.Watched each : watched) {
            // Exhaustive, so a row cannot fall through into none of the counts. What a row that was
            // watched came to is asked below; that a row was not watched is answered here, because
            // it is a fact about this build rather than about where the row went.
            switch (each) {
                case Generator.Watched.NoAccount _ -> notWatched++;
                case Generator.Watched.Ran(var seen) -> {
                    switch (against.takenBy(seen)) {
                        case RulesTaken.WhichRule.TookThis it -> {
                            took.add(it.rule());
                            placed++;
                        }
                        case RulesTaken.WhichRule.CouldNotTell it -> {
                            if (it.why() == RulesTaken.WhichRule.Why.NO_RULE_IS_RECOGNISABLE) {
                                return new Taken.NothingWasRead(Taken.Why.NO_RULE_IS_RECOGNISABLE);
                            }
                            notPlaced++;
                        }
                    }
                }
            }
        }
        return new Taken.Read(watched.size(), took, placed, notPlaced, notWatched);
    }
}
