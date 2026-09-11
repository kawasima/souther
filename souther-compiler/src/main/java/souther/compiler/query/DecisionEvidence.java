package souther.compiler.query;

import souther.compiler.observe.MeasureReason;
import souther.compiler.partition.DecisionReading;
import souther.compiler.partition.DecisionRule;
import souther.compiler.partition.Generator;
import souther.compiler.partition.RulesTaken;

import java.util.ArrayList;
import java.util.Collections;
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
 * <p><b>The second half is a measure, the way every other measure of a behavior is.</b> Which rules
 * the rows took is a question that can go unasked, be asked and come back with nothing readable, or
 * come back read in part — and those are the four states {@link Measure} already has. Written as a
 * sum of its own, the three nothings were one word with no weakening behind it: a build that read
 * no row and one whose rows are all placed and take none of the rules both said the rules are
 * uncovered, and a bar refused over the first.
 *
 * @param read the rules the body states
 * @param took which of them the rows were seen taking, and how far that reading got
 */
public record DecisionEvidence(DecisionReading read, Measure<RowsPlaced> took) {

    public DecisionEvidence {
        Objects.requireNonNull(read, "a decision is some body's");
        Objects.requireNonNull(took, "there is always an answer to what the rows took");
        // A reading that would not hold the body's ways apart comes back with none of them, so a
        // run placed against those rules was placed against nothing. Folded in here and not left
        // to each reader: the rules and the runs are one measurement, and a coverage that called
        // itself complete over no rules would say every rule of the body is taken.
        //
        // Only where there is a value to weaken. A measure that was not made says so, and a
        // measure that could not be finished already carries what stopped it; neither claims
        // anything about rules this reading never had.
        WeakeningSet unread = derivationOf(read);
        if (!unread.isEmpty() && took.made().isPresent()) {
            took = new Measurement.Partial<>(took.made().orElseThrow(),
                    took.weakening().union(unread));
        }
    }

    /**
     * The rules no row was seen taking, where the rows were read at all.
     *
     * <p>Named for what this knows, which is the body and the rows written for it. Whether anything
     * can stand in one of these is a further question and a further answer — a search settles it —
     * and a name that said nothing stands in them would be this answering about a world it has not
     * looked at.
     *
     * <p>Empty where no reading was made, which is not every rule being taken. A caller that acts
     * on these asks {@link #took()} whether there was a reading first: what a rule nothing read is
     * owed is unknown, and a list of all of them would be read as a list of gaps.
     */
    public List<DecisionRule> notTakenByRows() {
        if (took.made().isEmpty()) {
            return List.of();
        }
        Set<DecisionRule> covered = took.made().orElseThrow().rules();
        return read.rules().stream().filter(rule -> !covered.contains(rule)).toList();
    }

    /**
     * What every row of the behavior came to.
     *
     * <p><b>Three states and every row is in one.</b> A row whose rule was told is placed; a row
     * something watched and nothing could place took a rule this reading cannot recognise; and a
     * row nothing watched says nothing about which rule it took. The three are different facts and
     * the third is not the second — a run with no account did not go nowhere, it went somewhere
     * nothing recorded.
     *
     * <p>Held to adding up, so that a row cannot go missing between the rows read and what is
     * counted here. That is what a reading of this is for: the numbers say the measurement was made
     * in full only where every row was watched, and a row dropped on the way would make an
     * incomplete reading look complete.
     *
     * <p>What the reading went without is not here. It is the measurement's — {@link
     * Measurement.Partial} carries it — which is what keeps one fact in one place: a value that
     * held its own weakening beside a measurement holding another is two answers to what a reader
     * may trust.
     *
     * @param rowsRead       how many rows this reading was given, which the three below are the
     *                       whole of
     * @param rules          the rules some row was seen taking
     * @param rowsPlaced     how many rows were placed at one of them
     * @param rowsNotPlaced  rows something watched whose rule this reading could not tell
     * @param rowsNotWatched rows nothing watched, which is this compiler's shortfall and not
     *                       anything about the model
     */
    public record RowsPlaced(int rowsRead, Set<DecisionRule> rules, int rowsPlaced,
                             int rowsNotPlaced, int rowsNotWatched) {

        public RowsPlaced {
            // Sealed and not only copied. What the account answers about a behavior is read off
            // this — which rules were covered, which were not — and a set a caller can add to is a
            // value whose answers change after it was made.
            rules = Collections.unmodifiableSet(new LinkedHashSet<>(rules));
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

    /**
     * Why nobody read which rules the rows took, where nothing went wrong in the reading.
     *
     * <p>One answer, and a behavior no row names is not it. A reading over no rows is a reading
     * that was made: it places none of them and finds that no rule is taken, which is what an
     * author writing the first row of that behavior is told. Answered as a measure nobody made, a
     * body whose every way is uncovered would be a body nothing is owed for.
     */
    public enum NotAsked implements NotMeasuredReason {

        /** The build does not run rows with the instrumentation a place is recorded by. */
        NOT_ASKED;

        /** What the build asked for is one value for the whole run. */
        @Override
        public MeasureReason.About about() {
            return MeasureReason.About.THE_RUN;
        }
    }

    /**
     * Why a reading that was asked for and started came back with no placement at all.
     *
     * <p>Every one of these leaves what the rows take unknown rather than empty. A rule nothing was
     * seen taking under one of them is a rule a row may already take, so no account may call it
     * missing and no bar may refuse over it.
     */
    public enum Unreadable implements FailureReason {

        /** The body this reading places runs in was not read, so there is nothing to place against. */
        THE_BODY_WAS_NOT_READ,

        /** Rows are written for this behavior and none of them came back to be read. */
        NO_ROW_CAME_BACK,

        /** The rows ran without the instrumentation a place is recorded by, so they carry no run to
         *  put against a rule. */
        THE_ROWS_CARRY_NO_ACCOUNT,

        /** Every rule of the body carries a condition no run through it is recorded at, so no run
         *  could be placed whatever the rows did. */
        NO_RULE_IS_RECOGNISABLE;

        @Override
        public MeasureReason.About about() {
            return MeasureReason.About.THE_BEHAVIOR;
        }
    }

    /** The rules the body states, for a reader that asks what it decides and not what ran. */
    public List<DecisionRule> rules() {
        return read.rules();
    }

    /**
     * What the derivation of the rules went without, which is not what the rows went without.
     *
     * <p>A reading that stopped at a figure comes back with none of the body's rules rather than
     * some of them, so an empty list is two different facts — a body that decides nothing, and a
     * body whose ways this compiler would not hold apart. Carried here so that a reader asking the
     * account is told which, rather than reading it off a count that is zero either way.
     */
    public WeakeningSet derivation() {
        return derivationOf(read);
    }

    private static WeakeningSet derivationOf(DecisionReading read) {
        return read.enumeration() instanceof DecisionReading.Enumeration.StoppedAtAFigure
                ? WeakeningSet.of(
                        new Weakening.DecisionReadingIncomplete(read.behavior(),
                                read.enumeration()))
                : WeakeningSet.none();
    }

    /**
     * How many rules some row was seen taking, where anything was read.
     *
     * <p>Absent where nothing was: a number of zero says the rows took none of the rules, which is
     * not what a build that read no rows found out.
     */
    public OptionalInt covered() {
        return took.made().map(read -> OptionalInt.of(read.rules().size()))
                .orElseGet(OptionalInt::empty);
    }

    /**
     * What this account of the behavior's decision went without, all of it.
     *
     * <p>The whole of it, so that a caller hands over the thing it is looking at rather than a set
     * worked out above: a row this reading could not place and a row nothing watched both leave a
     * rule nothing was seen taking as one a row may already take, and a finding given only the
     * first would be refused over where it should be undecided. What the derivation of the rules
     * went without is in here too, folded in where this was made.
     */
    public WeakeningSet weakening() {
        return took.weakening();
    }

    /**
     * What the rows of one behavior came to, one answer per row.
     *
     * <p>Walked over the rows and never over what came back watched. A row nothing watched is a row
     * all the same, and taking the accounts first and the rows never would leave it out of every
     * number here — which is a reading that went without something reporting that it did not.
     *
     * @param watched what watched each row, which is an account or the fact that there is none
     * @param whatTheRowsWentWithout what the reading of the rows went without, which this reading
     *                               is short of as well: a rule nothing was seen taking may be
     *                               taken by a row that reading never saw
     */
    public static Measure<RowsPlaced> of(String behavior, RulesTaken against,
                                         List<Generator.Watched> watched,
                                         WeakeningSet whatTheRowsWentWithout) {
        Set<DecisionRule> took = new LinkedHashSet<>();
        List<Weakening> whyNotPlaced = new ArrayList<>();
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
                            // A reading that recognises no rule at all placed nothing and would
                            // place nothing however many rows it was given. What the rows take is
                            // then unknown rather than none, and this is the measure saying so.
                            if (it.why() == RulesTaken.WhichRule.Why.NO_RULE_IS_RECOGNISABLE) {
                                return new Measurement.FailedToMeasure<>(
                                        Unreadable.NO_RULE_IS_RECOGNISABLE,
                                        WeakeningSet.of(new Weakening.DecisionOfRowUnreadable(
                                                behavior, it.why())));
                            }
                            whyNotPlaced.add(
                                    new Weakening.DecisionOfRowUnreadable(behavior, it.why()));
                            notPlaced++;
                        }
                    }
                }
            }
        }
        WeakeningSet went = WeakeningSet.ofAll(whyNotPlaced).union(whatTheRowsWentWithout);
        if (notWatched > 0) {
            went = went.union(
                    WeakeningSet.of(new Weakening.DecisionRunNotWatched(behavior)));
        }
        RowsPlaced made =
                new RowsPlaced(watched.size(), took, placed, notPlaced, notWatched);
        // A row this reading could not place says what stopped it, and a row nothing watched says
        // what the run went without. Either leaves the placement partial; neither may arrive as a
        // complete reading of the rows.
        if (notPlaced + notWatched > 0 && went.isEmpty()) {
            throw new IllegalStateException("a row this reading could not place says what stopped"
                    + " it, and " + (notPlaced + notWatched) + " say nothing");
        }
        return went.isEmpty() ? new Measurement.Complete<>(made)
                : new Measurement.Partial<>(made, went);
    }
}
