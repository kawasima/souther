package souther.compiler.query;

import souther.compiler.coverage.AlignedObservation;
import souther.compiler.partition.DecisionReading;
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
     * The rules nothing has been shown to stand in.
     *
     * <p>Neither covered nor a gap. A row standing in a rule is what shows something can, and
     * nothing has looked anywhere else — so a row is not owed here and the rule has not gone away.
     * What would move one of these is a search that composes a value and sees it take the rule,
     * which is evidence about the model rather than about the rows.
     */
    public List<DecisionRule> nothingStandsIn() {
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
         * The rules the runs took, and the runs whose rule could not be told.
         *
         * <p>The second is beside the first rather than folded into it. A run this compiler cannot
         * place is not a run that took no rule — it took one — and counting it as neither would make
         * the reading look complete over rows it could say nothing about.
         */
        record Read(Set<DecisionRule> rules, int runsNotPlaced) implements Taken {

            public Read {
                rules = new LinkedHashSet<>(rules);
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

    /** What the runs of {@code seen} took, in the shape this holds it. */
    public static Taken of(RulesTaken against,
                           List<AlignedObservation> runs) {
        Set<DecisionRule> took = new LinkedHashSet<>();
        int notPlaced = 0;
        for (AlignedObservation each : runs) {
            switch (against.takenBy(each)) {
                case RulesTaken.WhichRule.TookThis it -> took.add(it.rule());
                case RulesTaken.WhichRule.CouldNotTell it -> {
                    if (it.why() == RulesTaken.WhichRule.Why.NO_RULE_IS_RECOGNISABLE) {
                        return new Taken.NothingWasRead(Taken.Why.NO_RULE_IS_RECOGNISABLE);
                    }
                    notPlaced++;
                }
            }
        }
        return new Taken.Read(took, notPlaced);
    }
}
