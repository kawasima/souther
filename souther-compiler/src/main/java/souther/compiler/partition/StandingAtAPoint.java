package souther.compiler.partition;

import souther.compiler.coverage.AlignedObservation;
import souther.compiler.coverage.ComparisonEmissionSite;
import souther.compiler.inputs.TermPath;
import souther.compiler.observe.ObservedValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether a tuple of values stands at one point of a border.
 *
 * <p>One walk for every kind of line, and the one place this is asked. What a value put at the point
 * is is the quantity's to read — it is the one thing that knows what it is a quantity of — and what
 * this adds is what belongs to the rows: which reading of a row a point is tried against, and that a
 * line a fork drew is met by getting the comparison to answer as well as by writing the value.
 *
 * <p>Asked of a tuple and not of a row, so that what is already written and what a generation has
 * just composed are put the same question. Written against a row, the question could only be asked
 * of what is in the file — and the second answer somebody wrote for a candidate would be this rule
 * again, free to agree with it until one of them moved.
 *
 * <p>Nothing here concludes anything about coverage. It says what these values do at this point; who
 * may act on that, and what a point nothing was seen at means, is the caller's.
 */
public final class StandingAtAPoint {

    /**
     * What a walk over some tuples came to at one point.
     *
     * <p>Four answers because the two that are not found are not one fact. A tuple that could not be
     * read leaves the point undecided; a tuple that stands at the level and has no account of its
     * run is one nothing can say reached the comparison, which is not the same as one that ran and
     * did not reach it. Which of them a caller may treat as a miss is the caller's to say.
     *
     * <p><b>And the two that found the values are a case of their own.</b> A caller that wants to
     * know whether the values were seen where the line is asks {@link AtPoint}, and one that wants
     * to know whether anything watched the run tells its two arms apart — so which readers those
     * two answers are alike to is settled here, once, rather than by each of them writing the pair
     * into an arm of its own switch. A caller free to write its own pair is free to write any pair,
     * and the pair that costs something is a walk that could not look put beside a walk that looked
     * and found nothing.
     */
    public sealed interface Met {

        /** The values stand where the line is. */
        sealed interface AtPoint extends Met {}

        /** And something watched the run reach the comparison, where reaching it was asked. */
        record Reached() implements AtPoint {}

        /** And nothing watched it get there, which this found out rather than concluded. */
        record NotWatched() implements AtPoint {}

        /** The values were read, and they are not where the line is. */
        record NotAtPoint() implements Met {}

        /**
         * There was nothing at the point to compare, and this is what stopped there being one.
         *
         * <p>Carries every reason rather than the fact of there being some, and never one of them
         * over another. A reader handed the case alone can say only that something went unread —
         * which is {@code Observed} from {@code TruncatedByLimit} from {@code Absent} being lost one
         * layer before anybody needs it; handed the strongest, it is lost wherever a point met both.
         */
        record CouldNotTell(Set<ReadingGap> why) implements Met {

            public CouldNotTell {
                if (why == null || why.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a point nothing could be told about says what stopped the telling");
                }
                // In the order they were met, for the reason a report keeps any order.
                why = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(why));
            }
        }

        Met REACHED = new Reached();

        Met NOT_WATCHED = new NotWatched();

        Met NOT_AT_POINT = new NotAtPoint();
    }

    /**
     * The first of {@code rows} that stands there, or why none was found.
     *
     * <p>Takes the line as one measurement's reading of it, so that the walk a row's values are
     * found by is the one the line was measured against. Handed the quantity beside a walk, a
     * caller could put a line drawn at one reading to the rows of a behavior read at another —
     * which two behaviors taking a parameter spelled the same way is all it takes.
     *
     * @param watched every place a run through the comparison a row has to have got an answer out
     *             of is recorded, for a rule that meeting takes more than standing at the level.
     *             Empty where standing there is the whole of it. The places a run is written down
     *             and not which comparison it is, because what this asks them of is a run's own
     *             record.
     *             <p>Several where one rule is written into the tree that runs more than once, and
     *             a run that got an answer out of any of them got one out of the rule: they are
     *             one comparison the author wrote, and which of its copies ran is the operation's
     *             business rather than the model's
     */
    public static Met met(MeasuredInput.BorderReading line,
                          List<ObservedInputs> observed, Criterion criterion,
                          List<ComparisonEmissionSite> watched) {
        BorderQuantity quantity = line.quantity();
        BehaviorInputs where = line.subject().inputs();
        Set<ReadingGap> unreadable = new java.util.LinkedHashSet<>();
        boolean unwatched = false;
        for (ObservedInputs one : observed) {
            // A row has more than one value at a position inside a sequence, and standing at a point
            // is one element standing there. Asked for one value, such a row answered with none and
            // every point on such a line came back undecided — a measurement that could not look,
            // said of a row that wrote the values plainly.
            // The first reading both answers the point and says which steps the line's positions
            // take; the rest are tried under each choice those steps allow.
            Map<TermPath, Integer> held = new LinkedHashMap<>();
            OneReadingOfARow first = new OneReadingOfARow(where, one, Map.of(), held);
            boolean stands = false;
            Set<ReadingGap> stopped = new java.util.LinkedHashSet<>();
            for (OneReadingOfARow reading : readings(where, one, quantity, criterion, first, held)) {
                switch (quantity.standsAt(criterion, reading)) {
                    // A reading that could not look. What the row wrote nothing at is not among
                    // these: the quantity answers for the row there, since it is the quantity that
                    // knows whether a position it wrote nothing at leaves it a value.
                    case BorderQuantity.Stands.CouldNotTell it -> stopped.addAll(it.why());
                    case BorderQuantity.Stands.No _ -> { }
                    case BorderQuantity.Stands.Yes _ -> stands = true;
                }
                if (stands) {
                    break;
                }
            }
            if (stands) {
                if (watched.isEmpty()) {
                    return Met.REACHED;   // writing the value is the whole of what there is to reach
                }
                switch (one.watched()) {
                    case Generator.Watched.Ran(var account) -> {
                        if (gotAnAnswerOutOfTheRule(watched, account)) {
                            return Met.REACHED;
                        }
                    }
                    // It stands where the line is and nothing watched it get there. Said rather
                    // than counted as a row that did not reach the comparison: a run that reached
                    // nothing is something this found out, and a run nobody watched is not.
                    case Generator.Watched.NoAccount _ -> unwatched = true;
                }
            }
            unreadable.addAll(stopped);
        }
        // Every reason any row met, the way one reading collects every reason its terms met. A
        // point tried against several rows is one this could not tell about for whatever stopped
        // any of them, and taking the strongest would say which row this walk began with.
        if (!unreadable.isEmpty()) {
            return new Met.CouldNotTell(unreadable);
        }
        return unwatched ? Met.NOT_WATCHED : Met.NOT_AT_POINT;
    }

    /**
     * One row's values under one reading of it: an element chosen at each step inside a sequence
     * the line's positions take.
     *
     * <p>A row standing at a point is one of its readings standing there, and a reading has to be
     * one: two positions under one person are that person's two values, and offering the first
     * person's age beside the second person's status would have a row stand at a point neither
     * element is at. So an element is chosen per step and every position takes the one chosen for
     * the steps it shares — the same rule a pair of classes is read by, since it is the same
     * question.
     *
     * <p>Which readings there are is not known before the quantity has asked, since which positions
     * a line is over is its to say. So the choices are collected as it asks and the reading is run
     * again under each, until one stands or they are used up.
     */
    private static final class OneReadingOfARow implements BorderQuantity.Observation {

        private final BehaviorInputs where;
        private final ObservedInputs observedInputs;
        /** The element chosen at each step, for this reading. */
        private final Map<TermPath, Integer> chosen;
        /** How many elements each step was found to have, over every reading so far. */
        private final Map<TermPath, Integer> held;

        OneReadingOfARow(BehaviorInputs where, ObservedInputs observedInputs,
                         Map<TermPath, Integer> chosen,
                         Map<TermPath, Integer> held) {
            this.where = where;
            this.observedInputs = observedInputs;
            this.chosen = chosen;
            this.held = held;
        }

        @Override
        public WalkResult<ObservationAtPoint> at(TermPath path) {
            // Over the arms, so that a walk coming to answer a third way is one this has to be
            // taught about rather than one quietly read as a walk that could not be made.
            return switch (where.occurrencesAt(observedInputs.inputs(), path)) {
                // The walk and the type disagree, which is the quantity's to report.
                case WalkResult.CouldNotWalk<List<BehaviorInputs.Occurrence>> _ ->
                        WalkResult.couldNotWalk();
                case WalkResult.Reached(List<BehaviorInputs.Occurrence> values) ->
                        WalkResult.reached(standingAmong(values));
            };
        }

        /** Which of the row's answers this reading gets at a position the walk arrived at. */
        private ObservationAtPoint standingAmong(List<BehaviorInputs.Occurrence> values) {
            if (values.isEmpty()) {
                // The row wrote no element here, which is a row that was read. What that leaves a
                // quantity is the quantity's to say, and it says it where it knows what the
                // position is worth to the number it is reading.
                return ObservationAtPoint.WROTE_NOTHING;
            }
            for (BehaviorInputs.Occurrence each : values) {
                each.at().forEach((step, ordinal) -> held.merge(step, ordinal + 1, Math::max));
            }
            for (BehaviorInputs.Occurrence each : values) {
                if (agrees(each)) {
                    return new ObservationAtPoint.Value(each.value());
                }
            }
            // No value here under this reading. Not a stop: the reading names an element this
            // position does not have, and another reading is where its values are.
            return ObservationAtPoint.ANOTHER_READING;
        }

        /**
         * Every value the row wrote at {@code path}, whichever elements this reading chose.
         *
         * <p>No choosing and nothing recorded to choose between. A number taken over a run is over
         * all of them, so there is no element for a reading to have picked and no second reading to
         * try — which is why this neither reads {@code chosen} nor adds to {@code held}.
         *
         * <p>An empty run is a row that wrote no element, and a total over nothing is what the walk
         * starts from rather than a value nobody could read. So the row is not marked as having
         * written nothing here: it wrote a container, and what it holds is none.
         */
        @Override
        public WalkResult<List<ObservedValue>> everyValueAt(TermPath path) {
            // The walk's own answer handed on, which is the quantity's to report where it could not
            // be taken, as it is for the one value a place holds.
            return where.valuesAt(observedInputs.inputs(), path);
        }

        /** Whether {@code each} was reached through the elements this reading chose. */
        private boolean agrees(BehaviorInputs.Occurrence each) {
            for (Map.Entry<TermPath, Integer> step : each.at().entrySet()) {
                Integer picked = chosen.get(step.getKey());
                if (picked != null && !picked.equals(step.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The readings of one row a point is tried against.
     *
     * <p>The first is run before the rest are known: which steps the line's positions take is the
     * quantity's to say as it reads them, so it says so by being asked once. Every choice those
     * steps allow follows it.
     */
    private static List<OneReadingOfARow> readings(BehaviorInputs where, ObservedInputs observed,
                                                   BorderQuantity quantity, Criterion criterion,
                                                   OneReadingOfARow first,
                                                   Map<TermPath, Integer> held) {
        quantity.standsAt(criterion, first);
        List<OneReadingOfARow> out = new ArrayList<>();
        for (Map<TermPath, Integer> choice : readingsOver(held)) {
            out.add(new OneReadingOfARow(where, observed, choice, held));
        }
        return out;
    }

    /**
     * Every reading of a row over the steps {@code held} says its positions take.
     *
     * <p>One choice per step, in every combination — which is a product and not a zip, because two
     * steps a row's positions do not take together are two independent choices. Bounded, since a
     * row holding several long lists has more readings than a measure is worth.
     */
    private static List<Map<TermPath, Integer>> readingsOver(Map<TermPath, Integer> held) {
        List<Map<TermPath, Integer>> out = new ArrayList<>();
        out.add(Map.of());
        for (Map.Entry<TermPath, Integer> step : held.entrySet()) {
            List<Map<TermPath, Integer>> wider = new ArrayList<>();
            for (Map<TermPath, Integer> each : out) {
                for (int i = 0; i < step.getValue() && wider.size() < MOST_READINGS; i++) {
                    Map<TermPath, Integer> deeper = new LinkedHashMap<>(each);
                    deeper.put(step.getKey(), i);
                    wider.add(deeper);
                }
            }
            out = wider;
        }
        return out;
    }

    /** How many readings of one row a point is tried against. */
    private static final int MOST_READINGS = 256;

    private StandingAtAPoint() {}

    /**
     * Whether a run got an answer out of the rule, given every place the rule is watched at.
     *
     * <p><b>Any of them, because they are one rule.</b> A library operation may evaluate a closure
     * it was handed more than once — {@code List.distinctBy} asks its key twice — so a comparison
     * the author wrote once is written into the tree that runs more than once and each copy is
     * watched. Which of them ran is the operation's business; what the model states is the one rule,
     * and a run that got an answer out of any copy got one out of it.
     *
     * <p>Asked for all of them, a row would owe a run through every copy an operation happens to
     * make — a debt against how the library is written rather than against anything the model says.
     */
    static boolean gotAnAnswerOutOfTheRule(List<ComparisonEmissionSite> watched,
                                           AlignedObservation account) {
        return watched.stream().anyMatch(account::reached);
    }
}
