package souther.compiler.partition;

import souther.compiler.coverage.AlignedObservation;
import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleKey;
import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.DeclarationReadings;
import souther.compiler.check.FieldDomains;
import souther.compiler.check.Shape;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.reading.PathAccess;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Place;
import souther.compiler.observe.Classification;
import souther.compiler.observe.Incompleteness;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeReachName;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;

/**
 * Rows for the classes and the arms a caller says are owed one.
 *
 * <p>The plan comes in and this composes for nothing of its own. Which class of which position no
 * row sits in, and which arm no row goes through, is what a measure reads off the rows and reports;
 * a search working either out again is a second reading of one fact, free to offer a row at a class
 * the report calls reached. A combination of the body's decisions is where a witness for an arm is
 * looked for and is not itself a thing anyone is owed a row at.
 *
 * <p>What comes out is inputs and nothing else. The expected answer is left for a person, because the
 * compiler does not know it: the whole point of a model with no {@code let} is that the answer lives in
 * a legacy system or in someone's head, and a generator that guessed would turn a question into an
 * assertion nobody made.
 *
 * <p>What it reports is not only the rows. Everything on the plan gets an entry saying what came of
 * it, with which of {@link UnresolvedCombination.Reason} it was — the list is that enum's to keep,
 * and naming it here would be a second copy going stale. A generator that returned only the rows it
 * managed would read as though the rest were covered, and one that gave the same answer to every kind
 * would send an author looking for a value that does not exist while a row they could write in a line
 * went unwritten.
 */
public final class Generator {

    /** How many assignments of values one parameter is tried at in one pass. A parameter with
     *  something held in reserve is walked twice, each pass under this bound. The figure is
     *  {@link CompositionBudget}'s, where every budget of this compiler's is named. */
    private static final int MAX_TUPLES =
            CompositionBudget.ASSIGNMENTS_A_SEARCH_COMPOSES.maximum();

    /**
     * How many things one combination may be asking before the search gives up on it.
     *
     * <p>A combination is a class apiece at the positions it is about, and where it leaves a
     * position more than one class it is asking more than one thing. What this bounds is how many of
     * those a search may try: a group whose reading is wrong misses at every one of them, and
     * without a bound it would work its way through the whole space while every other group waited.
     *
     * <p><b>Readings and not assignments.</b> Counted over whole assignments, most of what the bound
     * was spent on differed only at positions the combination says nothing about — so three tries
     * were three rows asking the same thing, and a combination's second meaning went untried however
     * much of the bound was left ({@link Interpretation}).
     */
    private static final int MOST_INTERPRETATIONS = 3;

    /**
     * How many rows one reading of a combination is run for before the search moves on.
     *
     * <p>Counted in runs, because a run is what this is protecting. Reaching the arm is what a row
     * for a combination has to do and only the behavior can say whether it did, so every candidate
     * that composes costs a run — while a candidate the model refuses costs nothing but the
     * composing, and counting those would let a model whose rules refuse a few compositions spend a
     * reading's whole share without ever asking the behavior anything.
     *
     * <p>Its own bound and not the class walk's. {@link #MOST_REPAIRS} bounds a walk whose every
     * candidate is built and no more; this bounds one whose every candidate is run. The two are the
     * same shape of search and are not the same cost, and one number over both would be set by
     * whichever of them it hurt more.
     */
    private static final int MOST_RUNS_PER_INTERPRETATION = 3;

    /**
     * One row's worth of input, and what it was composed for.
     *
     * <p>The purpose and not the classes it turned out to sit in. Those are two questions — which
     * row this is, and what this run of the generator is handing it — and the second moves with
     * the rest of the model while the first does not. Held as the second, a row composed for one
     * class was named for every position it happened to hold, and an edit somewhere else in the
     * model renamed a row nothing about had changed (issue #967).
     *
     * <p>What a candidate turned out to sit in is not here at all, which is the point: a reader
     * that had it would be free to use it as evidence of coverage, and a row this run offers is a
     * question rather than evidence of anything.
     *
     * <p><b>As many purposes as it answers.</b> One candidate can be what two obligations were
     * waiting for — two arms of the body taken by the one path through it — and each of them is
     * answered by it. Held as one purpose, the two were written as a single composite thing with a
     * name made by joining theirs, which reads as an obligation that was never raised; held as
     * none but the first, the second went unanswered beside a row that answered it.
     *
     * @param purposes what the row was composed for, in the order the things were taken
     * @param inputs   one value per parameter, in the order the behavior takes them
     */
    public record GeneratedRow(List<Purpose> purposes, List<FixtureTemplate> inputs) {

        public GeneratedRow {
            purposes = List.copyOf(purposes);
            inputs = List.copyOf(inputs);
            if (purposes.isEmpty()) {
                throw new IllegalArgumentException("a row is composed for something");
            }
        }

        /** One row composed for one thing, which is what most of the searches here compose. */
        public GeneratedRow(Purpose purpose, List<FixtureTemplate> inputs) {
            this(List.of(purpose), inputs);
        }

        /** What the row is about, where this package has a name for it. An arm is named by the
         *  report and not here, so a row composed for one contributes nothing. */
        public List<String> labels() {
            return purposes.stream().flatMap(purpose -> purpose.labels().stream()).toList();
        }
    }

    /**
     * What a row was composed for.
     *
     * <p>In this package's own words and not the report's. A finding is what a report is written
     * from and lives a layer above; what a search here is asked for is a class of a position, a
     * combination the body decides together, or a point of a border — and a reader upstream joins
     * those to its findings by identity ({@link GenerationResult}).
     */
    public sealed interface Purpose {

        /** What a report writes this row as being about, one label per thing it was composed for. */
        List<String> labels();

        /** One class of one position: the row a class no row is in is owed. */
        record ForAClass(AxisId at, String classId, String label) implements Purpose {

            @Override
            public List<String> labels() {
                return List.of(label);
            }
        }

        /**
         * One arm of the body: the row an arm nothing reaches is owed.
         *
         * <p>The arm and not the combination a witness for it was found at. A combination is where
         * the search looked; what a reader is owed a row for is the arm, and the two came apart as
         * soon as one combination was allowed to answer two arms — written as the combination, that
         * row named a thing nobody asked about and hid the two things that were asked.
         *
         * <p>By the probe alone, which is the arm's identity everywhere. What an arm is called is
         * the report's word ({@code ArmVocabulary}) and is not something this package spells: a
         * name made here would be a second vocabulary for one thing, free to drift from the one the
         * finding is written in.
         */
        record ForAnArm(ArmProbe probe) implements Purpose {

            @Override
            public List<String> labels() {
                return List.of();
            }
        }

        /**
         * One point of one border: the row an edge nothing sits on is owed.
         *
         * <p>What it was composed for, which is not the same as what a reader may be shown. A
         * border's points coincide — a row at the bottom of two ranges is at both — so what a block
         * offers such a row under is the renderer's to decide, and this says what the search was
         * asked for.
         */
        record ForAPoint(String label) implements Purpose {

            @Override
            public List<String> labels() {
                return List.of(label);
            }
        }

        /**
         * A row nothing here can name from one thing.
         *
         * <p>A border's points coincide — each probe fills the positions its own edge does not name
         * from the bottom of their domains, so two minimum edges compose one row — and which of
         * them is offered is what changes when something else is written. A row named for whichever
         * happened to be offered would be renamed by an edit that did not touch it, so it is
         * written without a name, which the language allows: an unnamed row cannot be addressed
         * from outside, and that is the state of a row nobody has named yet.
         */
        record Unstated() implements Purpose {

            @Override
            public List<String> labels() {
                return List.of();
            }
        }
    }

    /**
     * A row that is already written, as the two things this reads it for.
     *
     * <p>Where its values sit is what says which classes it fills; what its run did is what says
     * which arms of the body it goes through. The second is not derivable from the first, which is
     * the whole of what this issue is about — a row whose values sit in a combination's classes and
     * whose run went elsewhere took none of the arms it names, and looks from the values alone
     * exactly like one that took them.
     *
     * @param at      which class of each divided position the row's values fall in
     * @param watched what came of running it. A sum and not an account that may be empty: a run
     *                that recorded nothing and a row nothing recorded are the same empty account
     *                and are not the same fact, and which of them this is decides what may be
     *                concluded from the row
     */
    public record ObservedRow(Map<AxisId, Classification> at, Watched watched) {

        public ObservedRow {
            at = Map.copyOf(at);
            watched = watched == null ? new Watched.NoAccount() : watched;
        }

        /** A row nothing here can say anything about the run of, for a caller with none to read. */
        public static ObservedRow unseen(Map<AxisId, Classification> at) {
            return new ObservedRow(at, new Watched.NoAccount());
        }
    }

    /**
     * A combination no row could be written for, and why.
     *
     * <p>{@code ALL_CANDIDATES_REJECTED} is not a proof that the combination is impossible. It says
     * every value this tried was refused, which is a fact about the values tried; another value of the
     * same classes may well build. So nothing anywhere records the combination as impossible: what
     * a pair space holds is what the rows reach and what is unknown, and this compiler assesses
     * reachability nowhere.
     *
     * @param said what the class said about itself where it said anything, in its own words. Kept
     *             beside the reason rather than folded into it: the reason is the category a reader
     *             acts on, and this is the sentence that says which case of it this was. Folded into
     *             {@code detail} it would be printed where the subject goes.
     */
    public record UnresolvedCombination(List<String> classes, Reason reason, String detail,
                                        Optional<String> said) {

        public enum Reason {
            /**
             * Nothing here knows how to compose a value of the shape asked for.
             *
             * <p>A fact about this compiler rather than about the model — a collection of more
             * elements than a row is worth carrying is one case of it, and a position nothing built
             * a value at is another. Which is why no sentence read off it says a value cannot be
             * written: the row may be the easiest one in the file to write by hand.
             *
             * <p>And why nothing decides this from the shape of the question. A reason read off the
             * kind of term outlives whatever made it true, and says a value cannot be composed while
             * the same generation composes one in the row above.
             */
            NOTHING_COMPOSES_ONE,
            /** Every value tried was refused at construction. */
            ALL_CANDIDATES_REJECTED,
            /**
             * The search left something untried.
             *
             * <p>Not that it stopped. A figure with no room for the candidate in front of it leaves
             * something untried, and so does a walk that ran to the end of a population this
             * compiler writes some of — the second stopped nothing and there is no number in it, and
             * a word saying a search halted would send a reader looking for one. What was left, and
             * whether raising anything reaches it, is what travels beside this
             * ({@link CompositionBudget}, {@link CompositionRepertoire}).
             *
             * <p>What it licenses is one thing either way, which is why it is one word: nothing here
             * was shown about the model, so a reader may not act on it as they may act on
             * {@link #THE_RULES_LEAVE_NOTHING_THERE}.
             */
            THE_SEARCH_LEFT_SOMETHING_UNTRIED,
            /**
             * The rules leave no value here, and the whole of what they leave was walked.
             *
             * <p>Apart from every other word here, and the difference is the whole point of having
             * it. The rest say what this compiler did not manage; this one says what the model
             * settles — every position of the point is bounded, every combination of those bounds
             * was tried, and none of them reaches it. A reader may act on this and may not act on
             * the others (ADR-0091).
             */
            THE_RULES_LEAVE_NOTHING_THERE,
            /**
             * One position of the row would have to be two things at once.
             *
             * <p>What the model settles, as {@link #THE_RULES_LEAVE_NOTHING_THERE} is, and not
             * something this compiler fell short of. A class under one case of a sum and a class
             * under another are classes of positions that are not in one value: no row is a
             * {@code FeedQuery} and has a {@code GlobalQuery}'s {@code tag}. Reported as a value
             * nothing composed, an author would go looking for a row that cannot exist.
             */
            ONE_POSITION_CANNOT_BE_BOTH,
            /** The module's classes were not there to build a candidate against. */
            NOTHING_TO_BUILD_AGAINST,
            /**
             * A way into the arm was read, and one of the decisions on it places at no class.
             *
             * <p>So there is nothing to steer a row by along that way: a row put at the classes the
             * rest of it leaves may go the other way round that fork, and would be offered for an
             * arm it never takes. A fact about what the partition divides this body's positions
             * into, and not about whether a run reaches the arm.
             */
            THE_WAY_IN_PLACES_AT_NO_CLASS,
            /** The build asked for no values to be composed, so nothing was tried here. What such a
             *  point is owed is whatever it was owed; what is missing is a row to offer for it. */
            NO_VALUES_WERE_ASKED_FOR,
            /**
             * The position was held back, so no class of it was searched for.
             *
             * <p>Some row wrote a value here that could not be read, which leaves what the rows
             * cover at this position unknown — and so what they do not cover. A row offered for a
             * class here may be one already sitting in the file, which is a specific piece of work
             * handed to somebody who has done it.
             *
             * <p>Told apart from {@link #THE_SEARCH_LEFT_SOMETHING_UNTRIED} because they are different pieces of news
             * and only one of them is about this search: that one says the budget ran out with the
             * class still owed, this says the class was never a thing to look for.
             */
            THE_POSITION_WAS_WITHHELD,
            /**
             * The group of decisions this belongs to was wider than the walk offers, so no
             * combination of it was looked in.
             *
             * <p>Told apart from {@link #THE_SEARCH_LEFT_SOMETHING_UNTRIED} for the reason {@link
             * #THE_POSITION_WAS_WITHHELD} is: that one says the budget ran out while walking, and
             * this says the walk never started. Raising the row budget changes the first and not
             * the second.
             *
             * <p>Named at all because the alternative is silence about the one thing raising a
             * budget does not fix. What the arm itself came to is its own entry's to say — a row
             * through it comes from the way into it whether or not anything above it was walked —
             * and this is the second half of that entry where there is one.
             */
            THE_GROUP_WAS_NOT_OFFERED,
            /**
             * The rows were not read, so nothing was searched for at all.
             *
             * <p>What made them unreadable is said in its own words beside this, and is a fact
             * about the evaluation rather than about any class. Named here so that a class is not
             * told the search reached it and stopped.
             */
            THE_ROWS_WERE_NOT_READ,
            /** The generated classes would not link, so the decoders could not be reached. Told
             * apart from the one above it because they were there, which is not what that says. */
            LINKAGE_FAILED,
            /**
             * No row composed for it was seen reaching it.
             *
             * <p>Said that way round because it is what the search establishes. Some of the
             * assignments tried may have composed nothing at all, so a word about what every row
             * did would be a word about rows there were none of; what holds of all of them is that
             * none was a witness.
             *
             * <p><b>Of a combination and of a point of a line alike.</b> Both are things a row is
             * composed for and both are answered by watching what the row turned out to do — a
             * combination by the arms the run took, a point by the walk that reads a row at one. A
             * second word for the second of them would be the same sentence said twice.
             *
             * <p>Not a proof that either is unreachable, and nothing reads it as one. It is a fact
             * about the candidates — and, where the reading that named the thing is wrong, about
             * that reading. Either way it stays untried rather than being counted as offered
             * (ADR-0091).
             */
            NO_CERTIFIED_WITNESS,
            /**
             * The walk ran to the end of what it had and put no candidate forward at all.
             *
             * <p>Nothing was built, so nothing was refused, and nothing stopped it: what it had to
             * offer was nothing. A fact about what this compiler can compose here, like
             * {@link #NOTHING_COMPOSES_ONE} and unlike the two words about the model — another
             * reading of the same position may well offer one.
             *
             * <p>It used to be spelled as no reason having been recorded, which was a confession
             * this compiler had failed to say why. The confession was real and belonged somewhere
             * else: a class or an arm the run never answered for. That absence cannot be built now,
             * and what is left here is a walk that ran and came back empty — which is a thing that
             * happened rather than a thing nobody wrote down.
             */
            NO_CANDIDATE_WAS_OFFERED,
            /**
             * No reading of the line was searched, so nothing was looked for at the point.
             *
             * <p>A line an {@code invariant} drew is owed once over every behavior carrying the
             * type, and a row at it is composed by walking one of those behaviors' inputs. Where
             * the search of every one this request was about had no answer to give, the walk came
             * back having looked at nothing — which is a fact about this run and says nothing
             * whatever about the line.
             *
             * <p>Apart from {@link #NO_CANDIDATE_WAS_OFFERED}, which is a walk that ran. Read as
             * that, a request that could not look at the one reading it was about would have
             * reported the line as refusing a row.
             */
            NO_READING_OF_THE_LINE_COULD_BE_SEARCHED;

            /**
             * Whether this reason proves there is nothing to find, which one of them does.
             *
             * <p>Asked rather than matched on. Every reader of one of these has the same question —
             * may I say the model settles this, or am I saying what this compiler did not manage —
             * and each that answered it by naming the one word carried a copy of the decision
             * ADR-0091 took. A reason added is then a case here rather than a word that quietly
             * joins whichever side a reader's condition happened to leave it on.
             *
             * <p>Named as {@link souther.compiler.query.PartitionEvidence.PairSpace} names it, since
             * it is the same question about the same thing.
             */
            public boolean provesInfeasible() {
                return switch (this) {
                    case THE_RULES_LEAVE_NOTHING_THERE, ONE_POSITION_CANNOT_BE_BOTH -> true;
                    // Every one of these is this compiler falling short, and none of them is the
                    // model saying anything: another value of the same classes may well build.
                    case NOTHING_COMPOSES_ONE, ALL_CANDIDATES_REJECTED, THE_SEARCH_LEFT_SOMETHING_UNTRIED,
                         NOTHING_TO_BUILD_AGAINST, NO_VALUES_WERE_ASKED_FOR, LINKAGE_FAILED,
                         NO_CERTIFIED_WITNESS, THE_GROUP_WAS_NOT_OFFERED,
                         THE_POSITION_WAS_WITHHELD, THE_ROWS_WERE_NOT_READ,
                         THE_WAY_IN_PLACES_AT_NO_CLASS, NO_CANDIDATE_WAS_OFFERED,
                         NO_READING_OF_THE_LINE_COULD_BE_SEARCHED -> false;
                };
            }

            /**
             * The word a search these budgets stopped comes back with.
             *
             * <p>One way only. Which budget stopped a search is what the search hands over, and this
             * is the word readers of the word have always had — read the other way round, a reader
             * would be recovering a budget from something that never held one: two of these come
             * back with the same word and one of them comes back with it without any budget having
             * been reached at all.
             *
             * <p>The words are what each of these has always said, kept rather than tidied. A budget
             * carries what stopped a search; what a report and a document say of it is a separate
             * decision from this one, and moving a word here would move it for them.
             *
             * <p>Budgets that come back with different words are not one stop. Nothing composes such
             * a set — a stop is one place — and this says so rather than choosing between them,
             * which would be a precedence over causes at the one layer that has none.
             */
            public static Reason wordFor(java.util.Collection<CompositionBudget> budgets) {
                Reason word = null;
                for (CompositionBudget each : budgets) {
                    Reason here = switch (each) {
                        case ELEMENTS_A_PROPOSAL_HOLDS, CHARACTERS_A_PROPOSAL_HOLDS,
                             PLACES_A_PAIR_IS_TRIED_AT -> NOTHING_COMPOSES_ONE;
                        case PAIRINGS_BUILT_AT_ONCE, ELEMENTS_A_TOTAL_IS_SPREAD_OVER,
                             SHAPES_OF_A_TOTAL_OFFERED, WAYS_DOWN_TO_A_TOTAL_TRIED,
                             STEPS_A_SEARCH_MAY_TAKE, ASSIGNMENTS_A_SEARCH_COMPOSES,
                             VALUES_OF_AN_UNBOUNDED_PROGRESSION_TRIED,
                             LEVELS_A_SIDE_IS_ASKED_AT -> THE_SEARCH_LEFT_SOMETHING_UNTRIED;
                        // Reaching these stops no composing, so no search comes back from one of
                        // them and there is no word to give. Asked for one all the same, this says
                        // so rather than lending a word from a budget that does stop something.
                        // The last stops a reading of a body rather than a search for a value, and
                        // what it stopped is carried where that reading is
                        // ({@link DecisionReading.Enumeration}).
                        case TIMES_THE_RULES_ARE_ASKED_AGAIN,
                             VALUES_A_POSITION_ON_THE_WAY_IS_TRIED_AT,
                             DEPTH_A_CONSTRUCTION_PLAN_DESCENDS,
                             PATHS_OF_A_DECISION_READ -> throw new IllegalArgumentException(
                                "no search comes back from this budget, so it has no word: " + each);
                    };
                    if (word != null && word != here) {
                        throw new IllegalStateException("one search stopped by budgets that come"
                                + " back with two words: " + budgets);
                    }
                    word = here;
                }
                if (word == null) {
                    throw new IllegalArgumentException(
                            "a search nothing stopped has no word to read off what stopped it");
                }
                return word;
            }

            /**
             * The same answer in the words a walk of a coverage item comes back with.
             *
             * <p>Two vocabularies for one distinction, and this is the whole of what relates them.
             * A walk says one of two things and a search says one of thirteen, so the projection
             * runs this way and never the other — read back, eleven words would have to name a
             * walk's answer and none of them does.
             *
             * <p>Exhaustive, with the eleven named. A word added is a word somebody has to decide
             * about here, and deciding is what a {@code default} would do on their behalf: it would
             * put the new word among the ones no walk says, which is the answer for eleven of them
             * and is nobody's to assume for the twelfth.
             */
            public Realization.Unknown.Reason asAWalksAnswer() {
                return switch (this) {
                    case NOTHING_COMPOSES_ONE -> Realization.Unknown.Reason.NOTHING_COMPOSED_ONE;
                    case THE_SEARCH_LEFT_SOMETHING_UNTRIED -> Realization.Unknown.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED;
                    // What a walk of a coverage item comes back with is what it did, and these are
                    // what somebody else did: the model settling the point, a candidate refused, a
                    // module with no classes, a position held back, a group never offered.
                    case ALL_CANDIDATES_REJECTED, THE_RULES_LEAVE_NOTHING_THERE,
                         ONE_POSITION_CANNOT_BE_BOTH, NOTHING_TO_BUILD_AGAINST,
                         NO_VALUES_WERE_ASKED_FOR, LINKAGE_FAILED, NO_CERTIFIED_WITNESS,
                         THE_GROUP_WAS_NOT_OFFERED, THE_POSITION_WAS_WITHHELD,
                         THE_ROWS_WERE_NOT_READ, THE_WAY_IN_PLACES_AT_NO_CLASS,
                         NO_CANDIDATE_WAS_OFFERED, NO_READING_OF_THE_LINE_COULD_BE_SEARCHED ->
                            throw new IllegalStateException(
                                    "no walk of a coverage item comes back with this: " + this);
                };
            }
        }

        public UnresolvedCombination {
            classes = List.copyOf(classes);
            said = said == null ? Optional.empty() : said;
        }

        public UnresolvedCombination(List<String> classes, Reason reason, String detail) {
            this(classes, reason, detail, Optional.empty());
        }

        public UnresolvedCombination(List<String> classes, Reason reason) {
            this(classes, reason, null, Optional.empty());
        }

        /**
         * What one of these is really about, where several say the same thing.
         *
         * <p>A position nothing can write a value for makes every combination it takes part in
         * unfillable, and saying so once per combination is one fact repeated a hundred times. The
         * position is the fact; the combinations are arithmetic on it.
         */
        public String subject() {
            return detail == null ? String.join(" x ", classes) : detail;
        }
    }

    /**
     * What a generation with nothing owed came to.
     *
     * <p>The rows offered at a behavior's boundaries are this: composed for points, which nobody is
     * asked about and nothing keeps a list of. What a run against a plan comes to is
     * {@link FillResult}, and holding both in one shape meant a result that had dropped its
     * obligations and one that never had any were the same value.
     */
    public record GenerationResult(List<GeneratedRow> rows, List<UnresolvedCombination> unresolved,
                                   List<GenerationReason> reasons) {

        public static final GenerationResult NONE =
                new GenerationResult(List.of(), List.of(), List.of());

        public GenerationResult {
            rows = List.copyOf(rows);
            unresolved = List.copyOf(unresolved);
            reasons = List.copyOf(reasons);
        }

        /**
         * Whether this result has nothing to say — which a reason is not.
         *
         * <p>A result that is nothing but a reason used to answer yes, and the block it belongs to
         * was dropped whole. So an author who asked what to write got silence where the answer was
         * that the generator could not look, and silence is what having nothing left to write looks
         * like.
         */
        public boolean isEmpty() {
            return rows.isEmpty() && unresolved.isEmpty() && reasons.isEmpty();
        }
    }

    /**
     * A value the module already states that a row's positions can be composed against.
     *
     * <p>A name per position and nothing else. What each value <em>is</em> is the module's to say,
     * and a row naming it is a row an author writes today — the reading that builds a fixture
     * expands the name where the row is read, so nothing here has to hold the value or agree with
     * it.
     *
     * <p>Why a row wants one: where the gap is a class at one position, the row a reader of a table
     * recognises is that class against values the model already puts beside it. Composed from the
     * classes alone, every position of the row holds whatever the search happened to name there,
     * and a reader has to work out which of the differences the answer turned on (issue #967).
     *
     * <p><b>Only what the model states, which may be one position of several.</b> The map is
     * partial and a position it does not name is one this origin makes no claim about: the search
     * composes that position from its classes. A behavior of several parameters written against one
     * value apiece, chosen for each on its own, would be a row whose positions the model never says
     * anything about together — so a tuple is an origin only where an author wrote a row with one,
     * which is a set of values they reached for together, and never one assembled from values the
     * file declares one after the other.
     *
     * <p>Which positions an origin names is what makes it an origin for a class or not. A row for a
     * class of {@code to} written against a value of {@code from} has its own position composed
     * like any other, so it is not that value with one field moved — it is a row with a
     * recognisable value somewhere else. Still worth offering, and not ahead of one that grounds
     * the class it is for: see {@link #nearestFirst}.
     */
    public record Baseline(Map<String, Named> at) {

        public Baseline {
            at = Map.copyOf(at);
        }

        /** Whether this names a value at any position at all. */
        public boolean isEmpty() {
            return at.isEmpty();
        }

        /** A value the module states, by the name a row writes it under. */
        public record Named(String module, String name) {}
    }

    /**
     * What composing a row for one class of one position came to.
     *
     * <p>Held per class and keyed by the class, because a class is what a finding is about and the
     * row offered for it is what answers that finding. A search whose results were a list of rows
     * left the two joined by whatever a reader could match — the words in a row's name — and a row
     * is named for what it was composed for rather than for everything it turns out to settle.
     */
    private sealed interface ClassAttempt {

        /** The position, by the name every reading of it uses. */
        AxisId at();

        /** The class, by the id the partition gave it — never the label, which two positions of
         *  one type spell the same way. */
        String classId();

        /** A row composed for this class. */
        record Built(AxisId at, String classId, GeneratedRow row) implements ClassAttempt {}

        /** No row came of it, and why. Never a statement that none exists. */
        record Unresolved(AxisId at, String classId, UnresolvedCombination why)
                implements ClassAttempt {}
    }

    /**
     * Whether a value written this way can be built at all.
     *
     * <p>The one thing a generator cannot work out for itself. A record's fields can constrain each
     * other, and whether two values are allowed together is the derived decoder's answer, not a rule
     * that can be read off the types one at a time.
     */
    @FunctionalInterface
    public interface CandidateCheck {

        /**
         * What building the candidate at one parameter came to: what was built, or why nothing was.
         *
         * <p>What was built and not only whether it built. Where a candidate landed is the
         * decoder's answer — a newtype's construction may narrow it, and a rule relating two
         * fields decides whether it exists at all — and a caller that had only the refusal was
         * left reading its own request back as the answer.
         */
        Built build(int parameter, FixtureTemplate candidate);

        /** Whether the candidate was refused, for a caller that has nothing to do with what it is. */
        default Optional<String> refuse(int parameter, FixtureTemplate candidate) {
            return build(parameter, candidate) instanceof Built.Refused refused
                    ? Optional.of(refused.why()) : Optional.empty();
        }

        /**
         * Nothing is refused and nothing is built — what a caller with no runtime to build against
         * uses.
         *
         * <p>{@link Built.NothingBuiltIt} and not a value: there is no runtime here, so no
         * candidate went through one, and a reader that took silence for a value would be reading
         * what it asked for back as what it got.
         */
        CandidateCheck ANY = (_, _) -> new Built.NothingBuiltIt();

        /**
         * A check that says which candidates are refused and nothing about the rest.
         *
         * <p>For a caller with no runtime: what it accepts, nothing built, so there is nothing for
         * it to hand back. Written as a value that was built, a reader asking where a candidate
         * landed would be handed what it had asked for.
         */
        static CandidateCheck refusing(Refusal said) {
            return (parameter, candidate) -> said.at(parameter, candidate)
                    .<Built>map(Built.Refused::new).orElseGet(Built.NothingBuiltIt::new);
        }

        /** Which candidates are refused, and why. */
        @FunctionalInterface
        interface Refusal {

            /** Empty where the candidate is allowed; the reason it is not, otherwise. */
            Optional<String> at(int parameter, FixtureTemplate candidate);
        }

        /** What came of building one candidate. */
        sealed interface Built {

            /** It built, and this is what it came to. */
            record Value(souther.compiler.observe.ObservedValue observed) implements Built {}

            /** It did not, and why. Never a claim that no value of the shape can be built. */
            record Refused(String why) implements Built {}

            /**
             * Nothing built it, so nothing here can say what it is.
             *
             * <p>Told apart from a value because they are not the same news. A caller checking
             * where a candidate landed has an answer in one case and none in the other, and a
             * candidate nothing built is offered on the strength of the reading that composed it —
             * which is what the row says of itself either way.
             */
            record NothingBuiltIt() implements Built {}
        }
    }

    /**
     * A way to run a composed row and see what it did.
     *
     * <p>The other thing a generator cannot work out for itself, and the one this issue is about.
     * Which combination a row sits in is settled by running it: everything before that is a reading
     * of the body, and a reading is what may be wrong.
     *
     * <p>Separate from {@link CandidateCheck} because the questions are. That one is asked of one
     * value at one position while the row is being composed, and answers whether the value can be
     * built at all; this is asked of the whole row afterwards, and answers where it went.
     */
    @FunctionalInterface
    public interface Trial {

        /** What running {@code inputs} through the behavior came to. */
        Watched run(List<FixtureTemplate> inputs);

        /** Nothing runs here — what a caller with no runtime to run against uses. */
        Trial NOTHING_RUNS = _ -> new Watched.NoAccount();
    }

    /**
     * What came of running one composed row.
     *
     * <p>A sum, so that a caller has to say which of them it has. Having no account of a row and
     * having one that shows it reached nothing are the same emptiness read off a set of places and
     * are not the same fact: the first leaves every combination as untried as it was, and the second
     * is a row that missed.
     */
    public sealed interface Watched {

        /** It ran, something was recording, and this is what it was seen doing. Also what a row
         *  that aborted part way comes back as: it went where it went before it stopped, and that
         *  is recorded. */
        record Ran(AlignedObservation seen) implements Watched {}

        /**
         * Nothing here can say what it did.
         *
         * <p>Three things come to this and they are one arm because nothing tells them apart by
         * acting differently: nothing ran the row, something ran it and nothing was recording, and
         * something ran it and the recording was never read. What none of them is, is a run that
         * reached nothing — that is {@link Ran} of an empty account, and it is the one difference
         * anything here turns on.
         */
        record NoAccount() implements Watched {}
    }

    // --- composing the rows ---------------------------------------------------------------------

    /**
     * Rows for every class of the behavior's positions no written row sits in.
     *
     * <p>Deterministic: the axes are ordered before anything starts, ties go to the lower index, and
     * nothing consults a clock or a hash order — the same model and the same rows produce the same
     * rows twice. Nothing is asked about the body here, so no arm is looked for.
     */
    public static FillResult fill(MeasuredInput subject, List<ObservedRow> existing,
                                        CandidateCheck check,
                                        AdequacyPolicy.OfTheGeneration budget) {
        return fill(subject, existing, check,
                new souther.compiler.reading.CoverageRead.Read(List.of(),
                        new LinkedHashMap<>()), budget);
    }

    /**
     * The same, and a row through every arm the body has.
     *
     * <p>Two questions and one set of rows. A class is what the model divides a position into and is
     * answerable with no body to read; an arm is a place in the body, and where a row through it is
     * looked for is what the reading says it takes to arrive there. The classes go first: what each
     * is owed is one row, and a budget the arms spent first left a class the report names with
     * nothing offered for it.
     */
    public static FillResult fill(MeasuredInput subject, List<ObservedRow> existing,
                                        CandidateCheck check,
                                        souther.compiler.reading.CoverageRead.Read read,
                                        AdequacyPolicy.OfTheGeneration budget) {
        return fill(subject, existing, check, read, Trial.NOTHING_RUNS, budget);
    }

    /**
     * The same, running each row composed at a combination to see whether it took the arm.
     *
     * <p>Which is the only thing that can say so. A row is composed by narrowing each position to
     * the classes the combination leaves it, and every step of that narrowing is a reading of the
     * body — so a row that misses is what a reading being wrong looks like, and a row that misses
     * looks like one that arrives until something watches it.
     *
     * <p>A row that missed is not offered and the arm stays unanswered. It is not evidence that the
     * arm is unreachable: what was shown is that these candidates were not witnesses (ADR-0091).
     */
    public static FillResult fill(MeasuredInput subject, List<ObservedRow> existing,
                                        CandidateCheck check,
                                        souther.compiler.reading.CoverageRead.Read read,
                                        Trial trial, AdequacyPolicy.OfTheGeneration budget) {
        // Both in the order their own walks reached them: the positions the search fixes them in,
        // and the numbers the plan gave the arms. Each is what that walk means by its order.
        return fill(planOver(subject, everyClassNoRowSitsIn(subject, existing),
                        List.copyOf(read.arms().keySet())),
                existing, check, read, trial, List.of(), budget);
    }

    /**
     * A plan over what a caller gathered, in the order they gathered it.
     *
     * <p>Ordered, because the plan is. Which order it is belongs to whoever gathered the
     * obligations: a walk that gathers each thing once knows what its own order means, and a set
     * handed over here would leave that to whatever collection the caller happened to hold — so
     * this takes the answer rather than the collection it was kept in.
     */
    public static GenerationPlan planOver(MeasuredInput subject, List<ClassOfAPosition> classes,
                                          List<ArmProbe> arms) {
        return new GenerationPlan(subject, classes, arms.stream().map(ArmOwed::new).toList());
    }

    /**
     * The same, for a caller that gathered its obligations itself.
     *
     * <p>A test standing the search up on its own is the caller this is for. The plan is still what
     * the search is asked with — there is no way in that does not carry one — and this is where the
     * one such a caller holds is assembled.
     */
    public static FillResult fill(MeasuredInput subject, List<ObservedRow> existing,
                                        CandidateCheck check,
                                        souther.compiler.reading.CoverageRead.Read read,
                                        Trial trial, List<Baseline> baselines,
                                        List<ClassOfAPosition> classesOwed,
                                        List<ArmProbe> armsOwed,
                                        AdequacyPolicy.OfTheGeneration budget) {
        return fill(planOver(subject, classesOwed, armsOwed), existing, check, read, trial,
                baselines, budget);
    }

    /**
     * Every arm a combination of the body may take, which is at least every arm one does take.
     *
     * <p><b>Not what a build asks for.</b> Which arms are owed a row is what measuring them
     * established, and a build hands that in. This is for a caller with no measurement beside it —
     * a test standing the search up on its own — and it says so by being a list the caller passes
     * rather than one the search makes for itself.
     *
     * <p><b>And <em>may</em> rather than <em>does</em>, which the name carries because the answer
     * cannot.</b> An offered group is walked, so what it contributes is exact: a choice whose
     * factors leave a position nothing is not a combination and is not counted. A group the budget
     * held back is not walked, and what it contributes is the union over the way in and every
     * outcome of every factor — which includes arms no single combination of it claims, since two
     * factors that disagree about a position have choices no row sits in.
     *
     * <p>That direction is the safe one and the other is not. An arm left out of what a caller asks
     * for is an arm this composes nothing for, and a caller with no measurement beside it has
     * nothing to tell that from an arm nothing could be composed for. An arm asked for and not
     * found says what each place it was looked in came to.
     */
    public static Set<ArmProbe> everyArmACombinationMayTake(
            MeasuredInput subject, List<souther.compiler.reading.Interaction> groups,
            AdequacyPolicy.OfTheGeneration budget) {
        Set<ArmProbe> out = new LinkedHashSet<>();
        InteractionCells.Offered offered =
                InteractionCells.of(groups, ordered(subject).axes(), budget);
        for (InteractionCells.Group group : offered.groups()) {
            for (int index = 0; index < group.size(); index++) {
                CellSelection selection = group.at(index);
                if (selection != null) {
                    out.addAll(claimed(selection));
                }
            }
        }
        // And the arms behind a group the limit held back. They are arms the combinations take —
        // what the limit settled is that nothing walked them, which is the search's answer and not
        // a fact about which arms exist. Left out, a caller with no measurement beside it asks for
        // fewer arms because this compiler declined to look, and never learns that it did.
        for (InteractionCells.NotOffered held : offered.notOffered()) {
            out.addAll(armsIn(held.claims()));
        }
        return out;
    }

    /**
     * Which arm of the body a search steers a row towards.
     *
     * <p>A handle and not an identity. What a row is owed for is one arm the author wrote, however
     * many times a helper carrying it is spliced in; the probe is one of those occurrences, and it
     * is what a search has to name because a run is recorded at an occurrence. So a proposal says
     * which obligation it targets and carries one of these to reach it, and the two are not the
     * same value.
     *
     * <p>Named rather than carried as the number the plan gave it. Spelled as a bare {@code int} it
     * put the things a run is asked about into two vocabularies, and anything holding both had to
     * say which kind of thing a number was every time it read one.
     */
    public record ArmOwed(ArmProbe probe) {}

    /**
     * Every class of every position no row the author wrote sits in.
     *
     * <p><b>Not what a build asks for.</b> Which classes are owed a row is what the partition
     * measure established, and a build hands that in. This is for a caller with no measurement
     * beside it — a test standing the search up on its own — and it says so by being a list the
     * caller passes rather than one the search makes for itself.
     *
     * <p>Read off the values the rows state, which needs nothing run: where a row stands is settled
     * by what is written at each position. So the answer is the same one the measure reaches, and a
     * build that ran nothing is not a build with nothing to generate for.
     *
     * <p>A row of the author's can sit in more than one class of a position at once — a list with
     * one element under a line and one over it — and each of them is covered. Read as one class,
     * the rest would be asked for again, which is work the author has already done.
     */
    public static List<ClassOfAPosition> everyClassNoRowSitsIn(MeasuredInput subject,
                                                       List<ObservedRow> existing) {
        // Gathered once apiece and handed over in the order the walk reached them, which is the
        // order the search fixes the positions in. The set is how "once apiece" is kept; what a
        // caller is given is the order, because that is what the plan is asking for.
        Set<ClassOfAPosition> out = new LinkedHashSet<>();
        for (Axis axis : ordered(subject).axes()) {
            Set<String> covered = new LinkedHashSet<>();
            for (ObservedRow row : existing) {
                Classification here = row.at().get(axis.id());
                if (here != null) {
                    covered.addAll(here.classIds());
                }
            }
            for (PartitionClass cls : axis.classes()) {
                if (!covered.contains(cls.id())) {
                    out.add(new ClassOfAPosition(axis.id(), cls.id()));
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * The same, composing each row's positions against a value the module already states where
     * there is one for them.
     *
     * <p>Which changes what a row says rather than what it is for. A row is composed for one class
     * either way; what a baseline settles is where the positions the row is <em>not</em> about
     * stand, and a value the model already names is one a reader recognises — so the difference
     * between the row and what is already written is the class, and the class alone.
     */
    public static FillResult fill(GenerationPlan plan, List<ObservedRow> existing,
                                        CandidateCheck check,
                                        souther.compiler.reading.CoverageRead.Read read,
                                        Trial trial, List<Baseline> baselines,
                                        AdequacyPolicy.OfTheGeneration budget) {
        MeasuredInput subject = plan.subject();
        List<ClassOfAPosition> classesOwed = plan.classesOwed();
        List<ArmProbe> armsOwed = plan.armsOwed().stream().map(ArmOwed::probe).toList();
        MeasuredInput.MeasuredAxes ordered = ordered(subject);
        // A position where some row's value could not be read is a position nothing is known about.
        // A row generated for a class there may be a row that is already written, and telling an
        // author to write one is worse than saying nothing: it is a specific piece of work that is
        // already done.
        List<GenerationReason> undecided = new ArrayList<>();
        // The positions that were held back, kept so that the classes of one are answered for by
        // name. A reason about the position says what happened to it; a class of it is a thing this
        // run was asked for, and is owed an entry of its own saying it was never looked for.
        Set<AxisId> withheld = new LinkedHashSet<>();
        // And the positions the rules leave no room for anything at, kept for the same reason: a
        // class of one is a thing this run was asked for, and what the rules leave there is an
        // answer about the model rather than an absence.
        Set<AxisId> leftNoRoom = new LinkedHashSet<>();
        // Which of them the search keeps, decided here and narrowed from the same projection: the
        // ones kept are these axes and not a list assembled beside them, so the walk they are read
        // by is still the one they were measured at.
        Set<AxisId> keptAxes = new LinkedHashSet<>();
        for (Axis axis : ordered.axes()) {
            // A position inside a collection the rules leave no room in. No value stands there in
            // any row, so no class of it is a cell to fill — and left in, every combination of the
            // row would be one no row can be written for, including the ones that name a position
            // beside it and have nothing to do with this one.
            if (holdsNothing(subject, axis)) {
                leftNoRoom.add(axis.id());
                continue;
            }
            if (readEverywhere(axis, existing)) {
                keptAxes.add(axis.id());
            } else {
                undecided.add(new GenerationReason.PositionWithheld(axis.id()));
                withheld.add(axis.id());
            }
        }
        MeasuredInput.MeasuredAxes axes = ordered.where(axis -> keptAxes.contains(axis.id()));
        // No return where nothing was kept. Nothing being divided is a fact about the classes, and
        // the arms below do not read the classes for their answer: what it takes to arrive at an arm
        // is what the reading of the body says, and that reading was made before this was called.
        // Stopped here, an arm that reading had an answer for was left with no entry at all, and
        // whoever read the result for one had nothing to go on but the absence.
        //
        // Which class of which position is owed a row, handed in by whoever read the rows. The
        // search keeps no list of its own: a class is owed one where nothing sits in it, and what
        // sits where is what the partition measure reads off the rows — so a search working it out
        // a second time is a second reading of one fact, free to disagree with the reported one.
        //
        // Walked in the search's own order over the positions it kept, so a plan naming a class of
        // a position nothing could be read at is dropped with that position: why it went is already
        // said, and it is not a class this run failed at.
        List<int[]> owed = new ArrayList<>();
        for (int i = 0; i < axes.size(); i++) {
            for (int c = 0; c < axes.get(i).classes().size(); c++) {
                if (classesOwed.contains(
                        new ClassOfAPosition(axes.get(i).id(), axes.get(i).classes().get(c).id()))) {
                    owed.add(new int[] {i, c});
                }
            }
        }

        // The values a row can be written against, resolved once for the behavior. Read per class,
        // this was the same walk through the decoders for every class owed, for an answer that is a
        // fact about the module rather than about the class asking.
        // The references this run composes, numbered by the run that composes them. A name a row
        // writes for a value the module states reaches a declaration and is some reference of it,
        // and no source wrote that one: the occurrence begins here, so this is what says which it
        // is. One minter for the run, so that two of them are two.
        FixtureReferences references = new FixtureReferences();

        List<ResolvedOrigin> origins = resolve(axes, baselines, check, references);

        // The rows this run composes, each numbered where it is composed. The number is an
        // identity and nothing reads it as a place: what says two obligations were answered by one
        // line is that both entries name the same one.
        SequencedMap<RowId, ComposedRow> composed = new LinkedHashMap<>();
        List<ClassAttempt> attempts = new ArrayList<>();
        // Which row answered which class. A row is a line in the file and the same line can answer
        // several things, so what says a class was answered is the entry naming the row rather than
        // anything written on the row itself.
        Map<ClassOfAPosition, RowId> answeredAt = new LinkedHashMap<>();
        List<UnresolvedCombination> unresolved = new ArrayList<>();
        List<GenerationReason> reasons = new ArrayList<>(undecided);
        // The classes first. What each is owed is one row, and the arms below are looked for among
        // combinations that would be composed either way — so a budget the combinations spent
        // first left a class the report names with nothing offered for it and a search limit
        // beside it, over rows nobody is owed.
        // Every class on the list gets an entry, including the ones the limit stopped the search
        // before. What was not tried is a fact of this run and is written down here — read off the
        // count of a reason at the end, it arrived at a reader as a fact about the model instead:
        // a class nothing looked at answered "no reason recorded", and an arm answered "nothing
        // reaches it" (issue #967).
        int classesLeft = 0;
        for (int i = 0; i < owed.size(); i++) {
            int[] at = owed.get(i);
            if (composed.size() >= budget.rowLimit()) {
                classesLeft = owed.size() - i;
                for (int cut = i; cut < owed.size(); cut++) {
                    Axis axis = axes.get(owed.get(cut)[0]);
                    UnresolvedCombination why = new UnresolvedCombination(
                            List.of(label(axis, owed.get(cut)[1])),
                            UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED);
                    attempts.add(new ClassAttempt.Unresolved(axis.id(),
                            axis.classes().get(owed.get(cut)[1]).id(), why));
                    unresolved.add(why);
                }
                break;
            }
            ClassAttempt attempt = rowFor(axes, at[0], at[1], origins, check, references);
            attempts.add(attempt);
            switch (attempt) {
                case ClassAttempt.Built made -> {
                    // Its own row, whatever the ones beside it are written as. Two classes can be
                    // answered by rows written the same way and are still two rows the search
                    // composed one apiece — each is offered for its own class, and merging them
                    // would take one of the two classes its answer.
                    answeredAt.put(new ClassOfAPosition(attempt.at(), attempt.classId()),
                            compose(composed, made.row().inputs()));
                }
                case ClassAttempt.Unresolved none -> unresolved.add(none.why());
            }
        }
        // And the arms this run was asked for, one at a time and each from its own places to look.
        // What it takes to arrive at an arm is what the reading of the body says, and that is where
        // a row for one comes from. A combination the body settles together is a second place: a
        // row found there arrives at the arm and exercises the combination at once, so it is looked
        // in first — which is a preference between two answers and not one of them standing in for
        // the other. An arm no combination is over is answered from its way in all the same.
        Set<ArmProbe> left = new LinkedHashSet<>(armsOwed);
        Map<ArmProbe, RowId> built = new LinkedHashMap<>();
        Map<ArmProbe, List<UnresolvedCombination>> failed = new LinkedHashMap<>();
        // Arms the row budget ran out before, which is what the search stopping looks like from an
        // arm. Told apart from an arm with nowhere to look, because raising the budget changes one
        // of them and nothing about the other.
        Set<ArmProbe> cutOff = new LinkedHashSet<>();
        // And the arms behind a group nothing walked, which is a second silence with a different
        // cause. The one above is a budget that ran out with the arm still owed; this is a group
        // the offer never opened, and raising the budget does not reach it.
        InteractionCells.Offered offered =
                InteractionCells.of(read.interactions(), axes.axes(), budget);
        // The combinations worth looking in, built once. A group builds a cell where it is asked
        // for one, so a walk per arm builds every cell of it again for an answer that does not
        // depend on which arm is asking.
        List<WhereToLook>cells = placesFor(new LinkedHashSet<>(armsOwed), offered);
        Set<ArmProbe> notOffered = new LinkedHashSet<>();
        // Which arms each held-back group could have been searched at, kept per group so that the
        // summary at the end can be counted off the entries rather than worked out a second way.
        List<List<ArmProbe>> behindEachHeldGroup = new ArrayList<>();
        for (InteractionCells.NotOffered held : offered.notOffered()) {
            List<ArmProbe> behindIt = armsIn(held.claims());
            behindEachHeldGroup.add(behindIt);
            notOffered.addAll(behindIt);
        }
        boolean unconfirmed = false;
        // What each set of values did when it was run, so that a row two arms were both composed
        // the same values for is applied once.
        Map<List<String>, Watched> ran = new LinkedHashMap<>();
        for (ArmProbe probe : armsOwed) {
            if (!left.contains(probe)) {
                // A row already composed was watched going through it, which is the one thing that
                // says so. Nothing is composed a second time for what a run was seen doing.
                continue;
            }
            for (WhereToLook place : whereToLookFor(probe, read, cells, axes.axes())) {
                if (composed.size() >= budget.rowLimit()) {
                    cutOff.add(probe);
                    break;
                }
                if (place.tried == null) {
                    place.tried = witnessFor(axes, place.at, check, trial, ran,
                            List.of(probe), origins, references);
                }
                // Each of the three, one at a time, so that a fourth added later has to be decided
                // about here rather than fall in with whichever of these a cast happened to take.
                // What else this row goes through is what watching it says, and nothing else: a
                // cell claims the arms a reading believes a row filling it takes, and discharging
                // them on that belief is the reading certifying itself (issue #1009).
                GeneratedRow row;
                List<ArmProbe> also;
                switch (place.tried) {
                    case Witness.NoCombination none -> {
                        noRow(unresolved, failed, probe, new UnresolvedCombination(List.of(),
                                UnresolvedCombination.Reason.ONE_POSITION_CANNOT_BE_BOTH, null,
                                Optional.of(none.said())));
                        continue;
                    }
                    case Witness.Exhausted none -> {
                        noRow(unresolved, failed, probe, new UnresolvedCombination(
                                none.classes(), none.reason(), none.detail(), none.said()));
                        continue;
                    }
                    case Witness.Limited none -> {
                        // The search stopped, which is this run's news and not the model's. Said as
                        // that, whatever the candidates it did try came to.
                        noRow(unresolved, failed, probe, new UnresolvedCombination(none.classes(),
                                UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED));
                        continue;
                    }
                    case Witness.Certified made -> {
                        row = made.row();
                        also = alsoThrough(made.by().seen(), left, probe);
                    }
                    case Witness.Unconfirmed offer -> {
                        row = offer.row();
                        // Nothing watched it, so what it is offered for is what the reading says
                        // and not what anything saw, and no other arm comes off the list for it.
                        // Said once for the behavior: it is one fact about this generation.
                        also = List.of();
                        unconfirmed = true;
                    }
                }
                // The row already offering these values where there is one, so that a class's row
                // an arm also goes through is one line and not two. What each of them is offered for
                // is the entries naming it, so nothing is written on the row here.
                RowId kept = keep(composed, row.inputs());
                built.put(probe, kept);
                left.remove(probe);
                also.forEach(each -> {
                    built.put(each, kept);
                    left.remove(each);
                });
                break;
            }
            if (built.containsKey(probe) || cutOff.contains(probe) || failed.containsKey(probe)
                    || !(read.armAt(probe) instanceof PathAccess.Ways)) {
                continue;
            }
            // A way in was read and nothing was tried along it, which is two things and not one:
            // every way this reading has places at no class of any position, and — where the arm is
            // behind one — a group the limit held back was never walked either. Both are said. They
            // do not order against each other: one is what the partition divides this body into and
            // the other is what this run declined to do, and a reader handed whichever a condition
            // reached first was handed the order the branches were written in.
            List<UnresolvedCombination> nowhere = new ArrayList<>();
            nowhere.add(new UnresolvedCombination(List.of(),
                    UnresolvedCombination.Reason.THE_WAY_IN_PLACES_AT_NO_CLASS));
            if (notOffered.contains(probe)) {
                nowhere.add(new UnresolvedCombination(List.of(),
                        UnresolvedCombination.Reason.THE_GROUP_WAS_NOT_OFFERED));
            }
            unresolved.addAll(nowhere);
            failed.computeIfAbsent(probe, _ -> new ArrayList<>()).addAll(nowhere);
        }
        // One entry per arm the run was asked about, in the order it was asked. An arm the limit
        // cut off carries that beside whatever was tried before it: a place the model refuses says
        // nothing about the ones nobody got to, and an arm answered by the first alone was reported
        // as settled by the model on the strength of a search that stopped.
        Map<ArmOwed, ArmDisposition> armAnswers = new LinkedHashMap<>();
        for (ArmProbe probe : armsOwed) {
            RowId row = built.get(probe);
            List<UnresolvedCombination> why = new ArrayList<>(
                    failed.getOrDefault(probe, List.of()));
            ArmOwed asked = new ArmOwed(probe);
            if (row != null) {
                armAnswers.put(asked, new ArmDisposition.Built(row));
            } else if (cutOff.contains(probe)) {
                why.add(new UnresolvedCombination(List.of(),
                        UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED));
                armAnswers.put(asked, new ArmDisposition.Unresolved(why));
            } else if (!why.isEmpty()) {
                armAnswers.put(asked, new ArmDisposition.Unresolved(why));
            } else {
                // Nothing was tried, and the reading says why: no run reaches the arm, or this
                // compiler cannot state what steers a row there. Either way it is an answer about
                // the arm and not an absence for a reader to make one of.
                armAnswers.put(asked, new ArmDisposition.NoWayIn(read.armAt(probe)));
            }
        }
        // Said once, at the end, and about both searches. One that ran out on the classes stopped
        // whether or not the arms had anything left to do, and two limits reported apart would be
        // read as two searches.
        // Counted off what the limit actually stopped. An arm still on the list because every
        // combination claiming it was refused is not one the limit cut off, and counting it here
        // told a reader to raise a limit that would change nothing.
        if (classesLeft + cutOff.size() > 0) {
            reasons.add(new GenerationReason.SearchLimit(subject.behavior(),
                    classesLeft + cutOff.size()));
        }
        // And the groups the limit held back that this run was asked about an arm behind. What it
        // says is what the search did not do: none of that group's combinations was looked in, so
        // nothing here exercises the decisions in it together. Whether the arms behind it got rows
        // is a different question and their own entries answer it — a row through an arm comes from
        // the way into it whether or not anything above it was walked.
        //
        // Against what was asked for, and not against what was left owed at the end. Read off what
        // was left, the line would come and go with whether the ways in happened to compose, which
        // is not what the limit did; asked about nothing behind it, the group costs this run
        // nothing and is not named (issue #967).
        int heldBackAndAskedAbout = 0;
        for (List<ArmProbe> behindIt : behindEachHeldGroup) {
            if (behindIt.stream().anyMatch(armsOwed::contains)) {
                heldBackAndAskedAbout++;
            }
        }
        if (heldBackAndAskedAbout > 0) {
            reasons.add(new GenerationReason.GroupsNotOffered(subject.behavior(),
                    heldBackAndAskedAbout));
        }
        if (unconfirmed) {
            reasons.add(new GenerationReason.RowsNotConfirmed(subject.behavior()));
        }
        // One entry per class the plan named, which the walk above wrote for the ones it kept a
        // position for. A class of a position that was held back was never a thing to look for, and
        // it says that here rather than being left out — a class the plan named and nothing
        // answered for is what a reader downstream had to invent a sentence about.
        Map<ClassOfAPosition, ClassDisposition> classAnswers = new LinkedHashMap<>();
        for (ClassAttempt attempt : attempts) {
            ClassOfAPosition key = new ClassOfAPosition(attempt.at(), attempt.classId());
            classAnswers.put(key, switch (attempt) {
                case ClassAttempt.Built _ -> new ClassDisposition.Built(answeredAt.get(key));
                case ClassAttempt.Unresolved none -> new ClassDisposition.Unresolved(none.why());
            });
        }
        for (ClassOfAPosition asked : classesOwed) {
            if (classAnswers.containsKey(asked)) {
                continue;
            }
            if (withheld.contains(asked.at())) {
                classAnswers.put(asked, new ClassDisposition.Unresolved(
                        new UnresolvedCombination(List.of(labelOf(subject, asked)),
                                UnresolvedCombination.Reason.THE_POSITION_WAS_WITHHELD)));
            } else if (leftNoRoom.contains(asked.at())) {
                // The position is inside a collection the rules cap at none, so no value stands
                // there in any row this model admits. Which is what the model says rather than what
                // this search fell short of, and a reader may act on it.
                classAnswers.put(asked, new ClassDisposition.Unresolved(
                        new UnresolvedCombination(List.of(labelOf(subject, asked)),
                                UnresolvedCombination.Reason.THE_RULES_LEAVE_NOTHING_THERE)));
            }
        }
        return new FillResult(plan, composed, unresolved, reasons,
                new Discharge(classAnswers, armAnswers));
    }

    /**
     * Which arms a combination claims a run through, by the numbers the plan gave them.
     *
     * <p>Only the arms. A combination's claims are what a run through it would be recorded at, and
     * a comparison is one of those — it is a place a run passes and not a way through a fork, so
     * nothing about an arm is owed for it.
     */
    private static List<ArmProbe> claimed(CellSelection selection) {
        return armsIn(selection.claims());
    }

    /**
     * A combination this run may look in, with the arms it claims read off it once.
     *
     * <p>A group builds a cell when it is asked for one, so asking again is building it again —
     * and what a cell is does not depend on which arm is being looked for in it. The arms it claims
     * are read here for the same reason.
     *
     * <p>What came of searching it is held here too, and is null where nothing has yet. One cell is
     * searched once however many arms are looked for in it: the candidates it admits and what they
     * did are facts about the cell, and composing them again per arm is the same work done twice for
     * the same answer.
     */
    private static final class WhereToLook {

        private final CellSelection at;

        private final List<ArmProbe> claims;

        private Witness tried;

        private WhereToLook(CellSelection at, List<ArmProbe> claims) {
            this.at = at;
            this.claims = claims;
        }
    }

    /**
     * The combinations worth looking in, walked once.
     *
     * <p>Only the ones claiming an arm this run was asked about. A group's cells are as many as the
     * budget allows and most of them claim nothing on the list, so keeping all of them would hold a
     * search space nobody asked about — which is what the cells were before a finding decided what
     * to look for.
     */
    private static List<WhereToLook>placesFor(Set<ArmProbe> armsOwed,
                                              InteractionCells.Offered offered) {
        List<WhereToLook>out = new ArrayList<>();
        for (InteractionCells.Group group : offered.groups()) {
            for (int index = 0; index < group.size(); index++) {
                CellSelection selection = group.at(index);
                // A choice whose factors leave a position nothing is no combination the body has a
                // path to, and there is nothing to look in.
                if (selection == null) {
                    continue;
                }
                List<ArmProbe> claims = claimed(selection);
                if (claims.stream().anyMatch(armsOwed::contains)) {
                    out.add(new WhereToLook(selection, claims));
                }
            }
        }
        return out;
    }

    /**
     * Where a row through {@code probe} is looked for, in the order they are looked in.
     *
     * <p>Two sources and neither is the other's fallback. A combination the body settles together
     * claiming this arm comes first, because a row found there answers the arm and exercises the
     * combination at once; the ways into the arm come after, and answer it whether or not anything
     * meets above it. Reversed, the same row would be composed twice over; left with only the
     * first, an arm under a body whose decisions meet nowhere had nowhere to be looked for at all
     * (issue #1009).
     *
     * <p>A way that places at no class is not somewhere to look. What comes back is where a row can
     * be steered, and how many that is is what the caller reads to tell an arm nothing was tried
     * for from one every attempt failed at.
     */
    private static List<WhereToLook>whereToLookFor(
            ArmProbe probe, souther.compiler.reading.CoverageRead.Read read,
            List<WhereToLook>cells,
            List<Axis> axes) {
        List<WhereToLook>out = new ArrayList<>();
        for (WhereToLook place : cells) {
            if (place.claims.contains(probe)) {
                out.add(place);
            }
        }
        if (read.armAt(probe) instanceof PathAccess.Ways ways) {
            for (souther.compiler.reading.WayIn way : ways.ways()) {
                CellSelection at = InteractionCells.at(way, ways.arrivesAt(), axes);
                // The same place twice is one place. A combination of the body's decisions and a
                // way into one of its arms can name the same classes held to the same run — a
                // combination of one factor is exactly the way into the arm it settles — and the
                // two are independent readings that neither know nor need to know that. Looked in
                // twice, the second composes the same candidates against the same rules for an
                // answer the first already has, and says what it came to a second time.
                if (at != null && out.stream().noneMatch(already -> already.at.sameAs(at))) {
                    // The way into one arm, which is nowhere else's to look in and so is not kept
                    // beyond this arm's search.
                    out.add(new WhereToLook(at, List.of(probe)));
                }
            }
        }
        return out;
    }

    /**
     * What one combination came to with no row, written down once for the cell and once per arm.
     *
     * <p>Said once for the cell, because what a cell came to is one fact about it and a block
     * printing it once per arm looked for there says the same thing as many times as the body has
     * arms. Kept per arm as well, because what an arm was owed and what it got is the arm's own
     * account.
     */
    private static void noRow(List<UnresolvedCombination> unresolved,
                              Map<ArmProbe, List<UnresolvedCombination>> failed, ArmProbe probe,
                              UnresolvedCombination why) {
        if (!unresolved.contains(why)) {
            unresolved.add(why);
        }
        failed.computeIfAbsent(probe, _ -> new ArrayList<>()).add(why);
    }

    /**
     * Which other arms still owed a row this run was seen going through.
     *
     * <p>What a run did and not what a reading expects of it. One row can be a row through several
     * arms — every arm on the way to the one it was composed for is one it takes — and the arm it
     * was composed for is left out here because it is already answered.
     */
    private static List<ArmProbe> alsoThrough(AlignedObservation seen,
                                              Set<ArmProbe> left, ArmProbe probe) {
        List<ArmProbe> out = new ArrayList<>();
        for (ArmProbe each : left) {
            if (!each.equals(probe) && seen.lit(each)) {
                out.add(each);
            }
        }
        return out;
    }

    /**
     * The row as this generation offers it, which is the one already offering the same values where
     * there is one.
     *
     * <p>Two arms searched for on their own can come to one row: what steers a row into an arm is a
     * conjunction of decisions, and two arms of one body ask for values that agree everywhere the
     * ways in agree. Written down twice, an author is handed the same line twice and told it
     * answers two different things.
     */
    private static RowId keep(SequencedMap<RowId, ComposedRow> composed,
                              List<FixtureTemplate> inputs) {
        List<String> written = inputs.stream().map(FixtureTemplate::text).toList();
        for (Map.Entry<RowId, ComposedRow> already : composed.entrySet()) {
            // Whatever the row beside it was composed for, and not the arms alone. One set of
            // values is one line in the file: a class's row and an arm's row of the same values are
            // one row that fills the class and goes through the arm, and written down twice the
            // second was printed over the first and took its name away with it.
            //
            // By what the rows are written as, which is what "the same line" means and is a string
            // to compare. A template also carries the expression it stands for, and holding two
            // rows to that walks two trees for an answer the text already gave.
            if (already.getValue().writtenAs().equals(written)) {
                return already.getKey();
            }
        }
        return compose(composed, inputs);
    }

    /**
     * A row of these values, numbered where it is composed.
     *
     * <p>The number is minted here and read nowhere as a place. What the identity is for is saying
     * that two entries name one line, and an identity that meant "the nth row offered" would move
     * with whatever the offer was ordered by — which is the arrangement a row's own account of what
     * it was composed for came apart under.
     */
    private static RowId compose(SequencedMap<RowId, ComposedRow> composed,
                                 List<FixtureTemplate> inputs) {
        RowId id = new RowId(composed.size());
        composed.put(id, new ComposedRow(inputs));
        return id;
    }

    /** The arms a list of claims names, by the numbers the plan gave them. Shared with the groups
     *  the limit held back, which have claims and no cell to read them off. */
    private static List<ArmProbe> armsIn(
            List<ControlClaim> claims) {
        List<ArmProbe> out = new ArrayList<>();
        for (ControlClaim claim : claims) {
            if (claim.at() instanceof ControlPlace.Arm arm
                    && arm.probe().isPresent()) {
                out.add(arm.probe().get());
            }
        }
        return out;
    }

    /**
     * How many assignments the walk over the origins tries before it gives up on a class.
     *
     * <p>One budget for the whole walk, over every origin and every distance. Held per origin, a
     * model stating a hundred values had a hundred budgets and the number was no bound on anything;
     * and the walk is nearest-first, so a budget spent is a budget spent on the rows nearest what
     * the model already says.
     *
     * <p>Its own budget and never {@link #MAX_TUPLES}. That one bounds the walk over the values one
     * parameter's fields may take once the classes are settled; this bounds the walk over which
     * classes to settle them at, and the two multiply — shared, one of them would be spent by the
     * other and which of them ran out would depend on the model.
     *
     * <p><b>The only bound.</b> There was a second one — at most two positions moved beside the one
     * the row is about — which was a rule about what a row may say wearing the clothes of a search
     * limit: a witness three moves away was not tried however much budget was left, and what came
     * back said no row was composed rather than that this had stopped looking. If a row that moves
     * many positions is one this should not offer, that is a policy with a name and a sentence of
     * its own, not a constant inside a loop.
     */
    private static final int MOST_REPAIRS = 64;

    /**
     * A row for one class: composed against what the model already says where it can be, and moving
     * as little else as it takes.
     *
     * <p>Outward from what a reader would recognise: the target alone before the target and one
     * supporting position, before the target and two, and within one distance the values the model
     * states in the order they were gathered, the classes last. What that order is and why is
     * {@link #nearestFirst}'s to say; what this does is walk it and stop at the first row that
     * lands in the class.
     *
     * <p><b>Not the other way round.</b> The synthetic composition used to run first and its
     * failure ended the class: a row the baseline could have been written for came back as one
     * nothing composed, because a representative chosen from the classes alone broke a rule that
     * relates two positions while the model's own value does not. Composing is one of the origins,
     * not the gate in front of them.
     *
     * <p>And a refusal of the exact mutation is a reason to repair it, not to abandon the origin.
     * Where {@code f = C} needs {@code g = G2} beside it, what a reader wants is the baseline with
     * both moved — {@code Cond {...none, f = C, g = G2}} — and falling back to a
     * composition moves everything the classes happened to name. The supporting position is part of
     * the row and no part of what it is for: the row is still named for the class alone
     * ({@link Purpose.ForAClass}).
     */
    private static ClassAttempt rowFor(MeasuredInput.MeasuredAxes axes, int at, int cls,
                                       List<ResolvedOrigin> origins, CandidateCheck check,
                                       FixtureReferences references) {
        Axis axis = axes.get(at);
        String classId = axis.classes().get(cls).id();
        String label = label(axis, cls);
        // A class is a demand over one position: it asks for that class there and says nothing
        // about anywhere else, which is what every other position being free means. Written as a
        // reading, it goes through the same walk a combination's readings do.
        Interpretation reading = new Interpretation(Map.of(at, cls));
        // Every baseline the module states rather than the one this compiler picked. Narrowed to
        // the only value of a type, a module that states a second one lost the spread from every
        // row of every behavior taking it — a change somewhere else in the file, answering a
        // question nobody asked it. What order they are walked in is {@link #nearestFirst}'s to
        // say; how many of them may be built is this class's own budget.
        Building building = new Building(axes, at, classId, label, check, MOST_REPAIRS, references);
        Traversal stated = nearestFirst(axes.axes(), reading, origins, (_, _) -> true, building);
        if (stated == Traversal.SATISFIED) {
            return new ClassAttempt.Built(axis.id(), classId, building.found);
        }
        // The composition, whatever the stated values spent, and with a budget of its own.
        Building composing = new Building(axes, at, classId, label, check, MOST_REPAIRS, references);
        Traversal composed = composing(axes.axes(), reading, origins, (_, _) -> true, composing);
        if (composed == Traversal.SATISFIED) {
            return new ClassAttempt.Built(axis.id(), classId, composing.found);
        }
        // What the walks came to, added up the way a combination's readings are. A class has the
        // one reading — its own class at its own position — so what is left to say is whether
        // either walk was stopped in front of work nobody did.
        Completeness looked = building.builds == 0 && composing.builds == 0
                ? Completeness.NOTHING_YET : Completeness.NOTHING_YET.searched();
        if (stated == Traversal.STOPPED || composed == Traversal.STOPPED) {
            looked = looked.cutShort();
        }
        Attempt last = composing.last == null ? building.last : composing.last;
        UnresolvedCombination why = switch (looked.found()) {
            // Nothing to try: the class cannot stand at its own position beside what the position
            // itself requires, under any origin. Which is the model not having this row rather than
            // a search that failed to find it.
            case Completeness.Nothing.NO_READING -> new UnresolvedCombination(List.of(label),
                    UnresolvedCombination.Reason.ONE_POSITION_CANNOT_BE_BOTH, null,
                    Optional.of("nothing this class can stand beside was left to try"));
            // The search stopped in front of a candidate it did not build. Said so whatever the ones
            // it did build came to: the refusal of the sixty-fourth is a fact about that candidate,
            // and offered as the class's answer it stands for a space the search never entered.
            case Completeness.Nothing.SEARCH_STOPPED -> new UnresolvedCombination(List.of(label),
                    UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED);
            case Completeness.Nothing.LOOKED_EVERYWHERE -> last == null
                    ? new UnresolvedCombination(List.of(label),
                            UnresolvedCombination.Reason.NO_CANDIDATE_WAS_OFFERED)
                    : new UnresolvedCombination(List.of(label), last.reason(), last.detail(),
                            last.said());
        };
        return new ClassAttempt.Unresolved(axis.id(), classId, why);
    }

    /**
     * Building the candidates offered for one class, up to what this class may spend.
     *
     * <p>What it costs to try a candidate here is one build, so that is what is counted. A candidate
     * no baseline can be written for costs nothing and is not counted: it is a way of writing a row
     * that this origin does not have, and the budget is over the rows built rather than over the
     * walk.
     *
     * <p>The bound is refused in front of the candidate rather than taken after it. A consumer that
     * did exactly as many as it was allowed and then reported having stopped said a candidate was
     * left where none was ({@link Traversal}).
     */
    private static final class Building implements Taking<Candidate> {

        // The row this walk was for, once one lands in the class. Read where the walk says it was
        // satisfied and nowhere else: a walk that stopped and a walk that finished are two answers
        // now, and reading a field to tell them apart is what having three of them is for.

        private final MeasuredInput.MeasuredAxes axes;

        private final int at;

        private final String classId;

        private final String label;

        private final CandidateCheck check;

        private final int most;

        /** What the last candidate that composed nothing came to. */
        private Attempt last;

        /** How many were built, which is what this is allowed so many of. */
        private int builds;

        /** The row, once one lands in the class. */
        private GeneratedRow found;

        /** The run's minter for the references what this composes will hold. */
        private final FixtureReferences references;

        private Building(MeasuredInput.MeasuredAxes axes, int at, String classId, String label,
                         CandidateCheck check, int most, FixtureReferences references) {
            this.axes = axes;
            this.at = at;
            this.classId = classId;
            this.label = label;
            this.check = check;
            this.most = most;
            this.references = references;
        }

        @Override
        public Taken take(Candidate candidate) {
            Map<String, FixtureTemplate> given = candidate.from().composes() ? Map.of()
                    : against(axes, candidate.delta(), candidate.where(),
                            candidate.from().baseline(), references);
            if (!candidate.from().composes() && given.isEmpty()) {
                return Taken.AND_MORE;   // nothing here can be written against the model's value
            }
            if (builds >= most) {
                return Taken.NOT_TAKEN;   // this candidate is the work nobody did
            }
            builds++;
            Attempt made = build(axes, candidate.where(), check, given);
            if (made.row() == null) {
                last = made;
                return Taken.AND_MORE;
            }
            if (!inTheClass(axes, at, classId, made.row().inputs(), check)) {
                last = new Attempt(null, UnresolvedCombination.Reason.NO_CERTIFIED_WITNESS, label,
                        Optional.empty());
                return Taken.AND_MORE;
            }
            found = new GeneratedRow(new Purpose.ForAClass(axes.get(at).id(), classId, label),
                    made.row().inputs());
            return Taken.AND_DONE;
        }
    }

    /**
     * A value the module states, with where its own values already sit read off once.
     *
     * <p>Read once for the generation and never once per class. Where a baseline's values fall is a
     * fact about the module, and asking it again for every class owed is the same walk through the
     * decoders for the same answer.
     *
     * <p><b>Two origins standing alike are still two origins.</b> Where a row's values sit is what
     * orders this search; what those values are is what the model builds and what a rule relating
     * two of them accepts. So the same classes reached from two values of the module are two
     * candidates and not one, and a search that dropped the second answered a class it could have
     * written a row for.
     *
     * @param baseline what the module states, or a baseline naming nothing where the row is
     *                 composed from the classes
     * @param stands   where {@code baseline}'s own values already sit, which is what a move is
     *                 measured against and what a spread writes over
     * @param index    where this came in the order the origins were gathered, which is what orders
     *                 two origins one distance away
     */
    private record ResolvedOrigin(Baseline baseline, int[] stands, int index) {

        /** Whether this is the composition rather than a value the module states. */
        boolean composes() {
            return baseline.isEmpty();
        }

        /**
         * How much of what a demand is about this states a value of.
         *
         * <p>By the parameters and not by the positions. Two positions under one parameter are two
         * things a demand may be about and one value an origin can state, and an origin counted
         * once per position would be twice as grounded for a demand over two fields of one record
         * as for one over two records.
         */
        int grounding(Set<String> asked) {
            int out = 0;
            for (String head : asked) {
                if (baseline.at().containsKey(head)) {
                    out++;
                }
            }
            return out;
        }
    }

    /**
     * One assignment to try, and the origin it is a move away from.
     *
     * <p>The distance is a fact about the pair and travels with it. Worked out again where the row
     * is written, the order the search went in and the row that came out of it were two readings of
     * one thing.
     *
     * @param from  the origin this row is written against
     * @param where the classes this assignment puts every position at
     * @param delta where {@code where} does not stand where {@code from} does
     */
    private record Candidate(ResolvedOrigin from, int[] where, Delta delta) {}

    /**
     * The assignments written against a value the module states, nearest first, one at a time.
     *
     * <p>Handed over rather than handed back. What a walk may cost is a fact about whoever is paying
     * — a class counts what it builds, an arm counts what it runs, and a candidate the model refuses
     * costs neither of them anything — so the bound belongs to the consumer and the order belongs
     * here. Bounded here instead, one number stood for two kinds of work, and the arm was held to
     * the class's.
     *
     * <p><b>How much of what the demand is about the origin states, before anything else.</b> A row
     * for a class of {@code to} written against a value of {@code from} has {@code to} composed from
     * its classes like any other position — so it is not that value with one field moved, which is
     * the thing this exists to offer. It is a row with a recognisable value somewhere else, worth
     * offering after every origin that grounds what the row is about and not among them. A demand
     * over several positions is grounded by degrees, and the origins that state more of them come
     * first.
     *
     * <p>Not a tie-break inside a distance. An origin that does not name the position is measured
     * against a tuple partly of the search's own making — {@link #stands} fills what the origin
     * says nothing about from the classes, and what is filled that way sits at distance zero by
     * construction. So the ungrounded origin arrived as a nearest one and won, and the value the
     * model states of the position the row is about went untried.
     *
     * <p><b>Then distance, among the values the model states.</b> How far a row moves from the
     * value it is written against is what this is minimising, so every baseline is tried at what
     * the demand asks for alone before any is tried with a supporting field moved. Within one
     * distance the origins keep the order they were gathered in, which puts a value the author's
     * own rows name before one the module merely states — the whole of one origin's moves at that
     * distance before the next origin's. Ordered with the supporting sets outside the origins,
     * provenance decided only which origin won a given set of supporting positions, and a later
     * origin that happened to repair on an earlier set beat an earlier origin that repaired on a
     * later one.
     */
    private static Traversal nearestFirst(List<Axis> axes, Interpretation reading,
                                          List<ResolvedOrigin> origins, Admits admits,
                                          Taking<Candidate> taking) {
        int[] about = about(reading);
        Set<String> asked = reading.heads(axes);
        // The origins that state most of what the demand is about, whole and at every distance,
        // before any that state less of it.
        for (int grounds = asked.size(); grounds >= 0; grounds--) {
            List<ResolvedOrigin> here = new ArrayList<>();
            for (ResolvedOrigin origin : origins) {
                if (!origin.composes() && origin.grounding(asked) == grounds) {
                    here.add(origin);
                }
            }
            if (here.isEmpty()) {
                continue;
            }
            Traversal walked = gather(axes, reading, about, here, admits, taking);
            if (walked != Traversal.EXHAUSTED) {
                return walked;
            }
        }
        return Traversal.EXHAUSTED;
    }

    /**
     * The assignments composed from the classes, for a consumer none of the stated ones answered.
     *
     * <p><b>Its own walk and not the tail of the other one.</b> A composed row is not a nearer
     * baseline: its distance is measured from values the search itself named, so it is zero by
     * construction and says nothing about how far the row is from what a reader recognises. Put in
     * the same order, a row composed from the classes won against every baseline that needed one
     * supporting field — which is the objective read backwards.
     *
     * <p>And walked whatever the stated values spent. It is not one of them and was not competing
     * with them for what they may cost, so a consumer that walked one list until its own bound ran
     * out never reached it — which is what left a class the model can hold a row for saying the
     * search had stopped.
     */
    private static Traversal composing(List<Axis> axes, Interpretation reading,
                                       List<ResolvedOrigin> origins, Admits admits,
                                       Taking<Candidate> taking) {
        int[] about = about(reading);
        for (ResolvedOrigin origin : origins) {
            if (!origin.composes()) {
                continue;
            }
            Traversal walked = gather(axes, reading, about, List.of(origin), admits, taking);
            if (walked != Traversal.EXHAUSTED) {
                return walked;
            }
        }
        return Traversal.EXHAUSTED;
    }

    /**
     * Every assignment these origins offer for one reading, nearest first, handed to
     * {@code taking}.
     *
     * <p>Walked by the size of the supporting set and handed over by the distance the assignment
     * came to, which are two numbers and not one. A set of {@code k} positions moves {@code k} of
     * them; the reading may move more, where a class it asks for cannot stand beside where the
     * origin's own value does. So an assignment is never nearer than the set that produced it, and
     * everything at one distance has been produced by the time the walk finishes the sets of that
     * size — which is what lets this hand them over in distance order without holding the whole
     * space to sort it.
     */
    private static Traversal gather(List<Axis> axes, Interpretation reading, int[] about,
                                    List<ResolvedOrigin> origins, Admits admits,
                                    Taking<Candidate> taking) {
        // What the demand asks for settled first, and each origin's own classes kept at every
        // position that can keep them beside it. Worked out once per origin: it is where that
        // origin's walk starts from and does not change with how far the walk has gone.
        List<Started> bases = new ArrayList<>();
        for (ResolvedOrigin origin : origins) {
            int[] base = standing(axes, wanting(axes, origin.stands(), reading), about, admits);
            if (base != null) {
                bases.add(new Started(origin, base));   // null is this reading not being one value
            }
        }
        // Produced further away than the set that produced them, kept until the walk reaches that
        // distance rather than handed over early.
        Map<Integer, List<Candidate>> waiting = new LinkedHashMap<>();
        // A set names positions the row is not about, so the largest one is every position but
        // those. Walked further, the sets are empty and the only thing left to do is hand over what
        // the readings moved beyond them, which is what happens below either way.
        for (int moved = 0; moved <= axes.size() - about.length; moved++) {
            for (Started origin : bases) {
                for (int[] supporting : supportingSets(axes, about, moved, origin.base())) {
                    for (int[] where : assignmentsOver(axes, origin.base(), supporting)) {
                        Candidate candidate = new Candidate(origin.from(), where,
                                Delta.between(origin.from().stands(), where));
                        waiting.computeIfAbsent(candidate.delta().size(), _ -> new ArrayList<>())
                                .add(candidate);
                    }
                }
            }
            // Everything this far and nearer. Asked of every distance up to this one rather than of
            // this one alone: what is due is a fact about the distances, and a walk that read it off
            // the size of the set it had just finished would leave a nearer assignment sitting in
            // the map for as long as the set that produced it was larger than it.
            Traversal walked = handOut(waiting, moved, taking);
            if (walked != Traversal.EXHAUSTED) {
                return walked;
            }
        }
        // What the readings moved beyond the largest set walked. Nothing generates at these
        // distances any more, so they are handed over rather than dropped — and handed over the same
        // way, because which of two candidates one distance apart comes first is one rule and not
        // one per place a candidate leaves this walk.
        return handOut(waiting, Integer.MAX_VALUE, taking);
    }

    /**
     * Where one origin's walk for one reading starts: what the reading asks for, and the origin's
     * own classes wherever they can be kept beside it.
     *
     * <p>A pair and not a map keyed by the origin. An origin holds where its values stand, which is
     * an array, and an array's equality is its own — so a map of them works only for as long as
     * every key is the one instance that was put there, and reads as though it worked either way.
     */
    private record Started(ResolvedOrigin from, int[] base) {}

    /**
     * Every candidate of {@code waiting} no further than {@code upTo}, nearest first, handed to
     * {@code taking}.
     *
     * <p>Within one distance the origins keep the order they were gathered in. Sorted rather than
     * generated that way, because an assignment reaches one distance from more than one size of
     * supporting set.
     */
    private static Traversal handOut(Map<Integer, List<Candidate>> waiting, int upTo,
                                     Taking<Candidate> taking) {
        for (int distance : new java.util.TreeSet<>(waiting.keySet())) {
            if (distance > upTo) {
                return Traversal.EXHAUSTED;
            }
            for (Candidate candidate : sortedByOrigin(waiting.remove(distance))) {
                switch (taking.take(candidate)) {
                    case NOT_TAKEN -> {
                        return Traversal.STOPPED;
                    }
                    case AND_DONE -> {
                        return Traversal.SATISFIED;
                    }
                    case AND_MORE -> { }
                }
            }
        }
        return Traversal.EXHAUSTED;
    }

    private static List<Candidate> sortedByOrigin(List<Candidate> due) {
        due.sort(java.util.Comparator.comparingInt(candidate -> candidate.from().index()));
        return due;
    }


    /**
     * Which positions beside the ones a row is about it may move, {@code moved} at a time.
     *
     * <p>In the axes' own order and combinations of it, so two runs of one model walk the same
     * assignments in the same order and offer the same rows.
     *
     * <p><b>Only the positions the row stands somewhere at.</b> A position at no class of this row
     * is one no assignment over it moves, so a set that names it moves fewer positions than it has
     * members — and the walk over the sets is what tells the search how far a candidate is. Left in,
     * a row one position from its origin was offered behind rows two away, and the number the budget
     * was spent by counted what the search reached for rather than what it moved.
     */
    private static List<int[]> supportingSets(List<Axis> axes, int[] about, int moved, int[] base) {
        List<int[]> out = new ArrayList<>();
        chooseSupporting(axes, about, moved, 0, new int[moved], 0, out, base);
        return out;
    }

    private static void chooseSupporting(List<Axis> axes, int[] about, int moved, int from,
                                         int[] taken, int filled, List<int[]> out, int[] base) {
        if (filled == moved) {
            out.add(taken.clone());
            return;
        }
        for (int i = from; i < axes.size(); i++) {
            if (anchored(about, i) || base[i] == NOT_HERE) {
                continue;
            }
            taken[filled] = i;
            chooseSupporting(axes, about, moved, i + 1, taken, filled + 1, out, base);
        }
    }

    /**
     * Every assignment over {@code base} that moves the positions in {@code supporting}, each of the
     * rest standing where {@code base} puts it.
     *
     * <p>The supporting positions take each of their classes in turn, and never the one they already
     * stood at — so a set of {@code k} positions moves {@code k} of them, and the assignment that
     * moves fewer is the one a smaller set already produced.
     */
    private static List<int[]> assignmentsOver(List<Axis> axes, int[] base, int[] supporting) {
        List<int[]> out = new ArrayList<>();
        // Cloned, because the walk settles the supporting positions in place and puts back what it
        // found. A row about a class under one case of a sum would otherwise carry the classes of
        // the positions under another, which is a row that has to be two things at once.
        walkSupporting(axes, base.clone(), supporting, 0, out);
        return out;
    }

    /**
     * Which class each position's value falls in for the value the model already states there, or
     * null where nothing built one to look at.
     *
     * <p>Read off what was built and never off what the baseline was asked to be. A position with
     * no baseline stands where a composition would put it — the row is not about it either way, and
     * what it holds is what the classes give it.
     *
     * <p>Nothing where no runtime built the values: a distance measured from a baseline nothing
     * looked at would be measured from a guess, and the composition is the origin this run has.
     */
    private static int[] stands(MeasuredInput.MeasuredAxes axes, Baseline baseline,
                                CandidateCheck check, FixtureReferences references) {
        MeasuredInput subject = axes.subject();
        List<souther.compiler.observe.ObservedValue> observed = new ArrayList<>();
        for (String parameter : subject.parameters()) {
            Baseline.Named named = baseline.at().get(parameter);
            if (named == null) {
                // Not a value this origin names, and not one it needs: the axes under it are read
                // off the composition below.
                observed.add(new souther.compiler.observe.ObservedValue.Unknown("no baseline"));
                continue;
            }
            if (!(check.build(observed.size(),
                    FixtureTemplate.named(named.module(), named.name(), references.next()))
                            instanceof CandidateCheck.Built.Value(var value))) {
                return null;
            }
            observed.add(value);
        }
        Map<AxisId, Classification> where = InputClassifications.of(observed, axes);
        int[] out = composes(axes.axes());
        for (int i = 0; i < axes.size(); i++) {
            Classification here = where.get(axes.get(i).id());
            if (here == null) {
                continue;
            }
            for (String id : here.classIds()) {
                int found = classIn(axes.get(i), id);
                if (found >= 0) {
                    out[i] = found;
                    break;
                }
            }
        }
        return out;
    }

    /**
     * The origins this generation has, in the order the search tries them.
     *
     * <p>Resolved once for the behavior. Where a baseline's own values sit is read through the same
     * check every candidate goes through, and asking it again for each class owed is that walk done
     * once per class for an answer that does not change with the class.
     *
     * <p>An origin nothing built is left out here, once. A distance measured from a baseline nothing
     * looked at would be measured from a guess.
     *
     * <p>The composition last, and always there. It is not one of the values the model states —
     * where it stands is where the classes put it, which the search itself named — so it is not
     * ordered among them; and it is not a baseline that failed, so nothing the baselines spend takes
     * it away.
     */
    private static List<ResolvedOrigin> resolve(MeasuredInput.MeasuredAxes axes,
                                                List<Baseline> baselines, CandidateCheck check,
                                                FixtureReferences references) {
        List<ResolvedOrigin> out = new ArrayList<>();
        for (Baseline baseline : baselines) {
            int[] stands = stands(axes, baseline, check, references);
            if (stands != null) {
                out.add(new ResolvedOrigin(baseline, stands, out.size()));
            }
        }
        out.add(new ResolvedOrigin(new Baseline(Map.of()), composes(axes.axes()), out.size()));
        return List.copyOf(out);
    }

    /** Whether {@code axis} is one of the positions an assignment is about. */
    private static boolean anchored(int[] anchors, int axis) {
        for (int each : anchors) {
            if (each == axis) {
                return true;
            }
        }
        return false;
    }

    /** Where every position stands when a row is composed from the classes alone. */
    private static int[] composes(List<Axis> axes) {
        return standing(axes, null, new int[0]);
    }

    private static void walkSupporting(List<Axis> axes, int[] where, int[] supporting, int filled,
                                       List<int[]> out) {
        if (filled == supporting.length) {
            out.add(where.clone());
            return;
        }
        int axis = supporting[filled];
        int stood = where[axis];
        // A position this row stands at no class of is not one to move it through. What it would
        // have taken is not a class of this row, so every assignment over it is the same row.
        if (stood == NOT_HERE) {
            walkSupporting(axes, where, supporting, filled + 1, out);
            return;
        }
        for (int c = 0; c < axes.get(axis).classes().size(); c++) {
            // Where it already stands is not a move, and the assignment that makes it is the one
            // the smaller set already produced.
            if (c == stood) {
                continue;
            }
            where[axis] = c;
            // And a class the rest of the assignment cannot be beside is not a move either: it is
            // a row that would have to be two things at once, which no value is.
            if (requiredBy(axes, where) instanceof Requirements.Merge.Merged) {
                walkSupporting(axes, where, supporting, filled + 1, out);
            }
        }
        where[axis] = stood;
    }

    /**
     * Whether the candidate's value at {@code at} really is in {@code classId}.
     *
     * <p>Asked of what was built and never of what was asked for. A class names the values it
     * stands for and a row writes one of them, and between the two are the decoders and the rules
     * the model states — a construction may narrow what it is given, and a value written under a
     * name is read back through it. So where the check built the candidate, where it landed is read
     * off the built value by the walk every written row's values go through
     * ({@link InputClassifications}).
     *
     * <p>True where nothing built it. There is no runtime to put a candidate through, so nothing
     * here can say where it went — and a row nothing could judge is offered as it was composed,
     * which is what {@code Trial.NOTHING_RUNS} leaves a row that nothing ran.
     */
    private static boolean inTheClass(MeasuredInput.MeasuredAxes axes, int at, String classId,
                                      List<FixtureTemplate> inputs, CandidateCheck check) {
        List<souther.compiler.observe.ObservedValue> observed = new ArrayList<>();
        for (int p = 0; p < inputs.size(); p++) {
            if (!(check.build(p, inputs.get(p)) instanceof CandidateCheck.Built.Value(var value))) {
                return true;   // nothing built it, so nothing says where it went
            }
            observed.add(value);
        }
        Classification where =
                InputClassifications.of(observed, axes).get(axes.get(at).id());
        return where != null && where.classIds().contains(classId);
    }

    /**
     * {@code composed} with every position a baseline names written against that baseline.
     *
     * <p>A parameter no moved position is under is written as the baseline itself: the value is
     * already in the model and this row is not about it, so naming it says so. A parameter some
     * moved position is under is written as the baseline with those fields moved, which is the row
     * — the difference between it and what the model already says is what the row is for, and
     * everything else standing where the model puts it is what makes that readable (issue #967).
     *
     * <p><b>Which fields those are is {@link Delta}'s to say and is not worked out again here.</b>
     * How far a row is from its origin is what the search ordered itself by, and which fields a
     * spread writes over is that same difference projected onto the parameters. Counted a second
     * time here, the two were free to disagree: the position the row is about was written out even
     * where the origin already stood in that class, so a row that is the model's own value came out
     * as that value with a field set to what it already held.
     *
     * <p>What a baseline cannot be written for is kept as it was composed, and silently: this is
     * how a row is written and not whether one could be. A position the baseline reaches through
     * more than one field, a class with no value to put there, a value the model refuses beside the
     * rest of the row — each of them leaves that parameter composed from its classes, which is a
     * row that says the same thing in more words.
     */
    private static Map<String, FixtureTemplate> against(MeasuredInput.MeasuredAxes axes,
                                                        Delta delta, int[] where,
                                                        Baseline baseline,
                                                        FixtureReferences references) {
        MeasuredInput subject = axes.subject();
        Map<String, FixtureTemplate> out = new LinkedHashMap<>();
        for (int p = 0; p < subject.parameters().size() && p < subject.types().size(); p++) {
            String parameter = subject.parameters().get(p);
            Baseline.Named at = baseline.at().get(parameter);
            if (at == null) {
                continue;
            }
            FixtureTemplate named = FixtureTemplate.named(at.module(), at.name(),
                    references.next());
            List<Integer> moved = delta.under(axes.axes(), parameter);
            FixtureTemplate written = moved.isEmpty() ? named
                    : withFieldsMoved(subject, p, axes.axes(), moved, where, named);
            // Left out where the baseline cannot be written for this assignment, which leaves that
            // parameter to be composed from its classes. How a row is written never decides
            // whether the model allows it — the check below asks that of every parameter alike.
            if (written != null) {
                out.put(parameter, written);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * The baseline with the fields this assignment moves under one parameter set to values of the
     * classes it moves them to, or null where this cannot be written.
     *
     * <p>Each field reached in one step. A position further down is a record inside a record, and
     * writing it means spreading the value at every step on the way — which is a row that names
     * values this has not been asked whether it can name. Such a parameter keeps what the classes
     * composed for it, which says the same thing and says it in full.
     *
     * @param moved which axes under this parameter the row does not stand where the origin does,
     *              read off the one difference the search ordered itself by
     */
    private static FixtureTemplate withFieldsMoved(MeasuredInput subject, int p, List<Axis> axes,
                                                   List<Integer> moved, int[] where,
                                                   FixtureTemplate baseline) {
        if (!(subject.types().get(p) instanceof Type.Ref(TypeSymbol built))
                || !(subject.symbols().scope().reach(built) instanceof TypeReachName.Written type)) {
            return null;
        }
        Map<String, FixtureTemplate> fields = new LinkedHashMap<>();
        LocationWrites writing = new LocationWrites();
        for (int i : moved) {
            Axis axis = axes.get(i);
            if (axis.path().steps().size() != 1
                    || !(axis.path().steps().get(0) instanceof TermPath.Step.Field field)) {
                return null;
            }
            // A position the row stands at no class of. It differs from where the origin stands and
            // there is no class to take a value from, so this parameter is one the baseline cannot
            // be written for.
            if (where[i] == NOT_HERE) {
                return null;
            }
            // The class's own values, and only those: a class composed through a constructor is a
            // walk this does not do, and one nothing can produce a value for has nothing to put
            // here.
            if (!(axis.classes().get(where[i]).representatives().evaluate()
                    instanceof RepresentativeSource.Evaluation.Values values)) {
                return null;
            }
            // A field two of the moved axes are of. The baseline can be written for one of them or
            // for the other and this walk writes fields, so it writes neither and says the
            // parameter cannot be written — which is what every other thing it cannot do here
            // answers with.
            FixtureTemplate written = values.written().get(0);
            if (writing.write(axis.path(), List.of(written))
                    == LocationWrites.Written.CONFLICTING) {
                return null;
            }
            fields.put(field.name(), written);
        }
        if (fields.isEmpty()) {
            return null;
        }
        // Written out rather than spread where the row moves every field the value has. The spread
        // is what says the row is that value with something changed, and a spread whose moved fields
        // cover the whole record says it over a value that contributes nothing — the same row in
        // more words, and a reader comparing it against what the file states finds every field
        // different.
        //
        // The values are the ones this candidate was built and run with. Composing the parameter
        // again from its classes would be a different row wearing this one's answer: a rule relating
        // two positions can refuse what the classes name while the model's own value builds, which
        // is why the value the model states is where this search starts.
        List<String> declared = fieldsOf(subject, built);
        if (declared != null && fields.keySet().containsAll(declared)) {
            Map<String, FixtureTemplate> written = new LinkedHashMap<>();
            for (String field : declared) {
                written.put(field, fields.get(field));
            }
            return FixtureTemplate.record(type, written);
        }
        return FixtureTemplate.spreading(type, baseline, fields);
    }

    /**
     * The fields a value of {@code built} has, in the order they are written, or null where it is
     * not a record.
     *
     * <p>Every field it has and not the ones its own declaration lists: a record that includes
     * another's fields has those too, and a row that wrote over the listed ones and dropped the
     * spread would drop the included ones with it.
     */
    private static List<String> fieldsOf(MeasuredInput subject, TypeSymbol built) {
        // A value written under a name is not written field by field: what the row writes is the
        // name round a value, and the fields belong to what the name wraps. So the position has to
        // wear no name as well as be a record — read where a position's reading is made, which
        // answers both, rather than walked from the declaration a second time.
        TypeView view = TypeView.of(Type.ref(built), subject.symbols());
        return !view.isWrapped()
                        && view.shape() instanceof Shape.Product(TypeSymbol _,
                                Map<String, Type> fields)
                ? List.copyOf(fields.keySet()) : null;
    }

    /**
     * Where a position the row is not about stands: the first of its classes something can write a
     * value for, among the ones {@code admits} allows.
     *
     * <p>The first that <em>can</em>, and not the first. A class nothing composes a value for is
     * still a class of the position, and a row standing there is a row that cannot be built — so
     * taking the first outright made every row of every other position unbuildable whenever one
     * position happened to declare such a class first.
     *
     * <p>The first of the rest where none can, which is a row that will not build. Said that way
     * rather than by refusing here: what could not be composed and why is {@link #build}'s answer,
     * and a second place deciding it would be a second reason for the same row.
     */
    private static int standingAt(Axis axis, java.util.function.IntPredicate admits) {
        int first = -1;
        for (int c = 0; c < axis.classes().size(); c++) {
            if (!admits.test(c)) {
                continue;
            }
            if (first < 0) {
                first = c;
            }
            if (axis.classes().get(c).representatives().buildable()) {
                return c;
            }
        }
        return first;
    }

    /**
     * What building a row at one boundary came to: the row, or why there is none.
     *
     * <p>One or the other, and the type says so. A caller measuring the boundary reads whether a row
     * was built, which is a value that went through the decoder and so a witness that the edge can be
     * written; a caller offering work to a person reads the row itself. The attempt is made once and
     * both read it.
     */
    public sealed interface BoundaryAttempt {

        /**
         * What the way to the point asked that this could not compose against.
         *
         * <p>Carried out with the answer because it is what the answer was arrived at without. A
         * row composed while a condition above the line was left out may not arrive there, and a
         * search that came back empty may have come back empty over that — so a reader of either
         * gets the same account, and neither is read as though the whole way had been used.
         *
         * <p>Empty is the ordinary case and says so: every condition the walk stated was one this
         * put a value under.
         */
        List<ReachabilityGap.Uncomposed> unrepresented();

        /** A value with the edge in it, built and accepted. */
        record Built(GeneratedRow row, List<ReachabilityGap.Uncomposed> unrepresented)
                implements BoundaryAttempt {

            public Built {
                unrepresented = List.copyOf(unrepresented);
            }
        }

        /** No row came of it, and why. Never a statement that none exists. */
        record Unresolved(UnresolvedCombination why, List<ReachabilityGap.Uncomposed> unrepresented)
                implements BoundaryAttempt {

            public Unresolved {
                unrepresented = List.copyOf(unrepresented);
            }
        }

        /**
         * No row came of it, and a budget of this compiler's is why.
         *
         * <p>Beside {@link Unresolved} and not a field on it. A search that tried what it had and a
         * search this compiler ended leave the reader different work — only the second has a figure
         * somebody could raise — and every reader that has to tell them apart is one an exhaustive
         * switch already stops.
         *
         * <p>{@code why} is the word such a search has always come back with, read off the budgets
         * so that the two cannot part.
         */
        record Stopped(UnresolvedCombination why, java.util.Set<CompositionBudget> by,
                       java.util.Set<CompositionRepertoire> notAllOf,
                       List<ReachabilityGap.Uncomposed> unrepresented)
                implements BoundaryAttempt {

            public Stopped {
                unrepresented = List.copyOf(unrepresented);
                by = java.util.Set.copyOf(by);
                notAllOf = java.util.Set.copyOf(notAllOf);
                if (by.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a search this compiler stopped says which budget stopped it");
                }
                // The word is the budgets' to say, so this pair cannot be put here disagreeing.
                // Left to whoever builds one, the two are a copy of one answer kept beside it —
                // which is what a stop lost its budget to before it travelled at all.
                if (why.reason() != UnresolvedCombination.Reason.wordFor(by)) {
                    throw new IllegalArgumentException("a search stopped by " + by
                            + " does not come back with " + why.reason());
                }
            }

            /** One at the label given, in the word its budgets come back with. */
            static Stopped at(String label, java.util.Set<CompositionBudget> by,
                              List<ReachabilityGap.Uncomposed> unrepresented) {
                return at(label, null, by, java.util.Set.of(), unrepresented);
            }

            /** The same, of a search that has something to say about where it stopped, and that
             *  separately walked some of a population. */
            static Stopped at(String label, String detail, java.util.Set<CompositionBudget> by,
                              java.util.Set<CompositionRepertoire> notAllOf,
                              List<ReachabilityGap.Uncomposed> unrepresented) {
                return new Stopped(new UnresolvedCombination(List.of(label),
                        UnresolvedCombination.Reason.wordFor(by), detail), by, notAllOf,
                        unrepresented);
            }
        }

        /**
         * No row came of it, and what this compiler writes of a population is why.
         *
         * <p>Beside {@link Stopped} and never one of its shapes. That one was holding a candidate a
         * figure had no room for, and its word is read off the figures; nothing was refused here,
         * and there is no number to read a word off or for a reader to raise. Written as a
         * {@code Stopped} with an empty set of figures, every reader of one would be reading a stop
         * that never happened.
         *
         * <p>The word is the same word all the same. What a reader concludes is that the point is
         * open because this compiler did not look at everything, which is true of both; what closes
         * it differs, and that is what travels here.
         */
        record Unexhausted(UnresolvedCombination why, java.util.Set<CompositionRepertoire> writes,
                           List<ReachabilityGap.Uncomposed> unrepresented)
                implements BoundaryAttempt {

            public Unexhausted {
                unrepresented = List.copyOf(unrepresented);
                writes = java.util.Set.copyOf(writes);
                if (writes.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a search that says it saw some of them says some of what");
                }
            }

            /** One at the label given, of a search that has something to say about what it saw. */
            static Unexhausted at(String label, String detail,
                                  java.util.Set<CompositionRepertoire> writes,
                                  List<ReachabilityGap.Uncomposed> unrepresented) {
                return new Unexhausted(new UnresolvedCombination(List.of(label),
                        UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED, detail), writes, unrepresented);
            }
        }

        /**
         * A search that came to its own answer, over less than the point had.
         *
         * <p><b>Beside {@link Stopped} rather than one of its shapes.</b> A stopped search has no
         * answer but the stopping, so the word it comes back with follows from the budgets and is
         * checked against them where one is built. Here the search ran to the end of what it was
         * handed and said what it found; what the figure adds is that the thing it was handed was
         * short. So the two halves are independent, and requiring the word to be the budgets' would
         * refuse every one of these — a figure that stops no search has no word to be read off it.
         *
         * <p>What this licenses is what {@link Stopped} licenses: the question is open, and open
         * because of a figure somebody could raise. What it does not license is reading the word as
         * the whole story, which is the mistake that made a value this compiler declined to plan
         * for look like one the model admits no row at.
         */
        record Limited(UnresolvedCombination why, Set<CompositionBudget> by,
                       List<ReachabilityGap.Uncomposed> unrepresented)
                implements BoundaryAttempt {

            public Limited {
                unrepresented = List.copyOf(unrepresented);
                by = Set.copyOf(by);
                if (by.isEmpty()) {
                    throw new IllegalArgumentException(
                            "an answer short of what the point had says which figure made it short");
                }
            }

            /** One at the label given, in the word the search itself came back with. */
            static Limited at(String label, UnresolvedCombination.Reason why, String detail,
                              java.util.Set<CompositionBudget> by,
                              List<ReachabilityGap.Uncomposed> unrepresented) {
                return new Limited(new UnresolvedCombination(List.of(label), why, detail), by,
                        unrepresented);
            }
        }

        /**
         * No search was made for the point: the value could not be planned.
         *
         * <p>Beside the two above and not one of them, because nothing ran. A reader of this is
         * owed the same thing a reader of {@link Stopped} is — the point is open on a figure — and
         * is owed it about a search that never happened, which is what its word says.
         */
        record Unplanned(UnresolvedCombination why, Set<CompositionBudget> by,
                         List<ReachabilityGap.Uncomposed> unrepresented)
                implements BoundaryAttempt {

            public Unplanned {
                unrepresented = List.copyOf(unrepresented);
                by = Set.copyOf(by);
                if (by.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a point nothing was planned for says which figure left it unplanned");
                }
            }

            /** One at the label given, in the word a reading nothing searched comes back with. */
            static Unplanned at(String label, Set<CompositionBudget> by,
                                List<ReachabilityGap.Uncomposed> unrepresented) {
                return new Unplanned(new UnresolvedCombination(List.of(label),
                        UnresolvedCombination.Reason.NO_READING_OF_THE_LINE_COULD_BE_SEARCHED),
                        by, unrepresented);
            }
        }
    }

    /**
     * A row at one coverage item of a border, built through the module's own decoders.
     *
     * <p>One entry, whatever the border was drawn on. What a search is handed is where each position
     * has to stand — one of them for a line at a position's own value, two for a line where two
     * positions stand apart, and as many as the rule named for a line over a form — and writing a row
     * with some positions fixed is one procedure. Written as a method per shape of line, the two that
     * existed offered different candidates for the same position and a third would have been a third
     * offer.
     *
     * <p><b>Every position of the item is fixed at once</b>, which is what makes the row one at the
     * item. A search that settled one and left the others to their own ranges would produce a row
     * beside the line as readily as one at it.
     *
     * <p><b>And the item is not the whole of what a row has to be.</b> A line inside a guard or an
     * arm is reached by rows that got past what stands above it, and what stands above it is about
     * positions the item never names. So {@code reaching} is the other half and is conjoined with
     * {@code fixing} rather than filled in around it: what it asks in cases is what the rest of the
     * row is built under, and what it asks in numbers puts the positions it bounds somewhere it
     * admits. Left out, a row carried the value the line is drawn at and turned back above it — and
     * the walk that reads a row at a point then said, correctly, that it does not stand there.
     *
     * <p>One row per boundary rather than one row covering several, because a row is a question put to
     * a person and a row sitting on three edges at once is three answers they have to separate.
     *
     * <p>Nothing here decides that a boundary cannot be written at. A refusal is a refusal of the
     * candidates that were tried, and another value of the same edge may build; what comes back says
     * which of the two happened and leaves the reading to the caller.
     *
     * <p>What each position's number is measured on is not passed in. It is the subject's reading to
     * answer, and a caller handing it over would be handing an answer it worked out somewhere else —
     * for a term this reading might have standing somewhere other than where that caller found it.
     */
    public static BoundaryAttempt probeFixing(MeasuredInput subject, String label,
                                              Map<RealizationTarget, Place> fixing,
                                              Reachability.Reaching reaching, CandidateCheck check) {
        LocationWrites decided = new LocationWrites();
        // What the rest of the row has to sit beside. A field of a record is not chosen from its own
        // type once another field of that record is fixed: the rule relating them says what is left,
        // and taking the bottom of the type's range instead is how a boundary that can be written
        // came back as one every value tried was refused at.
        Map<TermPath, Place> settled = new LinkedHashMap<>();
        // Which budgets each edge held values back at, and not the word for it. The word is one of
        // two and says nothing about which figure; kept as the word, a refusal that turned out to
        // be this compiler stopping could not say what stopping it cost.
        Map<TermPath, java.util.Set<CompositionBudget>> heldBack = new LinkedHashMap<>();
        // Beside it and not in it. What one edge did not offer is two facts of two kinds, and a
        // reader that had them in one map would have to know which of them it could raise.
        Map<TermPath, java.util.Set<CompositionRepertoire>> writesSomeOf = new LinkedHashMap<>();
        // Where every position of this row stands: the item's, and the ones the way to it bounds.
        // One map, because a row is one row — walked as two, the second was chosen from what the
        // declarations leave and the first from what reaches the border, and only one of them was
        // about the row being written.
        Standing where = alsoOnTheWay(subject, fixing, reaching);
        Map<RealizationTarget, Place> standing = where.at();
        for (Map.Entry<RealizationTarget, Place> each : standing.entrySet()) {
            // Beside another where the item fixes more than one position. The way's are not counted
            // in: what that limit is about is a number met by several values being asked to stand
            // beside a second position of the same item, and a position bounded on the way is one
            // this could leave to its own range without the row stopping being a row at the item.
            Edge edge = edgeAt(subject, each.getKey(), each.getValue(),
                    fixing.size() > 1, reaching.region());
            if (edge.values().isEmpty()) {
                return edge.cameToNothing(label, where.unrepresented());
            }
            TermPath at = each.getKey().writeRoot();
            // Two terms at one location is that location asked for two things at once — a string of
            // a length and the string itself — and what a row writes at a location is one value.
            // The fixing keeps them apart ({@link Realization.Found}) and this cannot, so it says so
            // rather than writing whichever came last and offering half the point as the whole.
            //
            // Refused whatever the second edge offers, and not only where it offers something else.
            // What is recorded beside the value here — where the row settles and what the edge held
            // back — is the edge's own answer, and two edges have two of those however alike their
            // values are. Written as one, the row would carry one edge's account of a place both
            // were asked about.
            //
            // Which two locations are one is {@link LocationWrites}' answer and is asked of it once.
            // Asked here as well, this would be a second account of that, and the two would part
            // over a location inside another.
            //
            // Anything but the first ask, and not only an ask that disagrees. That two edges offer
            // the same values is not the two being one ask here, for the reason above: what travels
            // beside the values is each edge's own, and the second would be recorded as the first's.
            if (decided.write(at, edge.values()) != LocationWrites.Written.FIRST) {
                return new BoundaryAttempt.Unresolved(new UnresolvedCombination(List.of(label),
                        UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE),
                        where.unrepresented());
            }
            if (edge.settledAt() != null) {
                settled.put(at, edge.settledAt());
            }
            heldBack.put(at, edge.stoppedBy());
            writesSomeOf.put(at, edge.notAllOf());
        }
        List<FixtureTemplate> inputs = new ArrayList<>();
        for (int p = 0; p < subject.parameters().size() && p < subject.types().size(); p++) {
            String head = subject.parameters().get(p);
            // Whether a candidate was turned away for standing somewhere else, which is what tells
            // a search that ran out of candidates from one that certified none of the ones it had.
            // Per parameter, since it is this parameter's search the answer is about: shared, a
            // candidate turned away under one parameter would name the reason another failed for.
            boolean[] uncertified = {false};
            CandidateCheck certified =
                    certifying(check, subject, p, standing, uncertified);
            Map<TermPath, List<FixtureTemplate>> here = new LinkedHashMap<>();
            for (RealizationTarget target : standing.keySet()) {
                // A position the way also narrows is not fixed at a value here. What has to hold of
                // it is one thing said two ways — a place the item asks for, and a case the way
                // says the value turned out to be — and one location is decided once: the narrowing
                // says how the value is built and the place says which of the values built that way
                // is accepted ({@link #certifying}). Handed over as both, it is a position with two
                // accounts, which is what {@link ConstructionPlan} refuses and what it is right to
                // refuse.
                if (target.writeRoot().head().equals(head)
                        && reaching.requirements().at(target.writeRoot()) == null) {
                    here.put(target.writeRoot(), decided.at(target.writeRoot()));
                }
            }
            Outcome tried = valueAt(subject, p, here, settled, reaching.requirements(), certified);
            if (tried instanceof Outcome.Built(FixtureTemplate value)) {
                inputs.add(value);
                continue;
            }
            // What the edge under this parameter held back is learned here and not where the
            // search ran, so this is where it is put to the rule — the same rule, and not a second
            // reading of it: an offer still holding values means nothing has been exhausted, and a
            // plan short of the point is not yet anything a reader is owed.
            Outcome answered = switch (tried) {
                // None of these is a search claiming every candidate was refused, which is the one
                // claim what the edge held back bears on ({@link #whatTheEdgeHeldBack}). A search
                // that stopped, one that ran to the end of what this compiler writes, and one that
                // never ran are already saying the point is open on this compiler, and adding what
                // an edge was short of would be a second account of the same emptiness.
                //
                // Which is true in both vocabularies and not only in the figures. What the edge was
                // short of travels with the answer that is about it, and never onto one that was
                // settled before the edge's offer was in question.
                case Outcome.Built _, Outcome.Stopped _, Outcome.Unexhausted _,
                     Outcome.Unplanned _ -> tried;
                case Outcome.Unresolved(UnresolvedCombination.Reason word, String said) -> {
                    UnresolvedCombination.Reason itsWord =
                            nothingStoodWhereItWasBuilt(uncertified[0], word);
                    yield whatTheSearchCameTo(whatTheEdgeHeldBack(heldBack, here, itsWord),
                            whatTheEdgeHeldBack(writesSomeOf, here, itsWord),
                            Set.of(), new Outcome.Unresolved(itsWord, said));
                }
                // Taken apart into what the search itself came to and what the plan was short of,
                // which is what the rule is asked in terms of. Handed over whole, the plan's
                // figures would be beside an offer's without anything having decided that a reader
                // is owed both.
                case Outcome.Limited(UnresolvedCombination.Reason word, String said,
                                     Set<CompositionBudget> planCut) -> {
                    UnresolvedCombination.Reason itsWord =
                            nothingStoodWhereItWasBuilt(uncertified[0], word);
                    yield whatTheSearchCameTo(whatTheEdgeHeldBack(heldBack, here, itsWord),
                            whatTheEdgeHeldBack(writesSomeOf, here, itsWord),
                            planCut, new Outcome.Unresolved(itsWord, said));
                }
            };
            return switch (answered) {
                case Outcome.Unresolved(UnresolvedCombination.Reason word, String said) ->
                        new BoundaryAttempt.Unresolved(
                                new UnresolvedCombination(List.of(label), word, said),
                                where.unrepresented());
                case Outcome.Stopped(Set<CompositionBudget> by,
                                     Set<CompositionRepertoire> writes, String said) ->
                        BoundaryAttempt.Stopped.at(label, said, by, writes,
                                where.unrepresented());
                case Outcome.Unexhausted(Set<CompositionRepertoire> writes, String said) ->
                        BoundaryAttempt.Unexhausted.at(label, said, writes,
                                where.unrepresented());
                case Outcome.Limited(UnresolvedCombination.Reason word, String said,
                                     Set<CompositionBudget> by) ->
                        BoundaryAttempt.Limited.at(label, word, said, by, where.unrepresented());
                case Outcome.Unplanned(Set<CompositionBudget> by) ->
                        BoundaryAttempt.Unplanned.at(label, by, where.unrepresented());
                // A row was composed, which was answered above and is not an account of a point
                // nothing was composed for.
                case Outcome.Built _ -> throw new IllegalStateException(
                        "a composed row is not something to say a point came to nothing in");
            };
        }
        return new BoundaryAttempt.Built(
                new GeneratedRow(new Purpose.ForAPoint(label), inputs), where.unrepresented());
    }

    /**
     * The item's positions, and a place inside the region for each position the way to it bounds.
     *
     * <p><b>Chosen and not left to the range.</b> What the conditions above a line leave a position
     * is a run, and every value of that run reaches the line as well as any other — so one of them
     * is taken and the row is written there. Left as a run for the composer to fill from the
     * declarations, the value it took was the bottom of the declared type, which is outside the run
     * wherever a condition above the line moved it: the row then carried the line's value and turned
     * back before reaching the comparison.
     *
     * <p>One at a time, with the region told what was chosen before the next is asked. A condition
     * relating two positions leaves neither of them a run the other's choice does not move, so two
     * places taken from two independent runs meet the rules only where they happen to.
     *
     * <p>A position this cannot place is left out rather than refused. There is no order to read it
     * on, the region says nothing about it, or nothing of the carrier lies in what is left — none of
     * those is a row that cannot be written, and each is a row this composes the way it did before
     * the way was carried here at all.
     */
    private static Standing alsoOnTheWay(MeasuredInput subject, Map<RealizationTarget, Place> fixing,
                                         Reachability.Reaching reaching) {
        Map<RealizationTarget, Place> out = new LinkedHashMap<>(fixing);
        List<ReachabilityGap.Uncomposed> unrepresented = new ArrayList<>();
        java.util.Set<TermPath> taken = new java.util.LinkedHashSet<>();
        fixing.keySet().forEach(target -> taken.add(target.writeRoot()));
        souther.compiler.inputs.SearchRegion here = reaching.region();
        for (Map.Entry<RealizationTarget, Place> each : fixing.entrySet()) {
            if (each.getValue() instanceof Count count) {
                here = here.given(each.getKey().term(), count);
            }
        }
        for (OnTheWay.TakenIn cut : reaching.boundedOnTheWay()) {
            List<NumericTerm.FromOnePosition> owing = new ArrayList<>();
            boolean shared = false;
            boolean placeable = true;
            for (NumericTerm term : cut.cut().form().coefs().keySet()) {
                // This very number already stands somewhere: the item asked for it, or an earlier
                // cut did. Nothing to place, and the cut is answered at it either way.
                if (out.containsKey(RealizationTarget.of(term))) {
                    continue;
                }
                // A number this reader cannot place beside the ones already standing. What it can
                // do is choose a value for a position ({@link NumericWitness}); what a number over a
                // run asks for is a container built to come to it, which is a second demand to
                // compose beside the item's own and not a value to choose. So the cut goes
                // unrepresented for the same reason a cut whose positions nothing composed a value
                // for does — and the reason is that two demands were asked of one row here, not
                // that the number has nowhere to be written.
                NumericTerm.FromOnePosition at = term.atOnePosition();
                if (at == null) {
                    placeable = false;
                    break;
                }
                // Another number taken at the same location. A row writes one value where a
                // location is, and that one value would have to answer both — a string of a length
                // and the string itself is the shape of it. Nothing here composes a value to two
                // numbers at once, so the cut is one this could not put a value under.
                //
                // Which locations are one is asked of the reader that owns it, because a container
                // written whole and a position inside it are one location spelled two ways. Kept
                // here as a lookup of the path, this would place a cut the writing then refuses,
                // and a cut that cannot be represented would sink the whole point rather than being
                // reported as the one thing it is.
                if (taken.stream().anyMatch(
                        each -> LocationWrites.oneLocation(each, at.position()))) {
                    shared = true;
                    break;
                }
                owing.add(at);
            }
            // The whole cut at once, because a cut over two positions is one statement about the
            // pair: which values one of them may take depends on what the other took, and a value
            // chosen for the first without asking is right about its own run and wrong about the
            // pair as often as not.
            NumericWitness.Standing found = shared || !placeable ? null
                    : NumericWitness.of(here, owing,
                            term -> subject.quantities().ordersOf(term).answered());
            Map<NumericTerm.FromOnePosition, Place> standing =
                    found == null ? null : found.at();
            if (standing == null) {
                // And where a budget of this compiler's is why the walk found nothing, that rather
                // than the word for a walk that had everything and reached none of it.
                unrepresented.add(new ReachabilityGap.Uncomposed(cut, shared
                        ? new ReachabilityGap.Why.TwoNumbersAtOneLocation()
                        : found != null && !found.stoppedBy().isEmpty()
                                ? ReachabilityGap.Why.TheWalkForItsPositionsWasStopped.by(
                                        found.stoppedBy())
                                : new ReachabilityGap.Why.NoValueComposedForItsPositions()));
                continue;
            }
            for (Map.Entry<NumericTerm.FromOnePosition, Place> each : standing.entrySet()) {
                if (each.getValue() instanceof Count count) {
                    here = here.given(each.getKey(), count);
                }
                taken.add(each.getKey().position());
                out.put(new RealizationTarget.AtOnePosition(each.getKey()), each.getValue());
            }
        }
        return new Standing(out, unrepresented);
    }

    /**
     * Where the positions of one row stand, and what the way asked that nothing could put a value
     * under.
     *
     * <p>The second is carried out with the first because it is what the row was composed without.
     * A search that could not act on a condition above the line has composed a row that may not
     * arrive there, and an account of the attempt that did not say so would have an author reading
     * "no row was seen reaching it" beside a way that says everything on it was taken in.
     */
    private record Standing(Map<RealizationTarget, Place> at,
                            List<ReachabilityGap.Uncomposed> unrepresented) {}

    /**
     * {@code check}, refusing any candidate at this parameter that does not read back at the place
     * it is being built for.
     *
     * <p><b>An acceptance condition and not an assertion.</b> A candidate that reads back somewhere
     * else is one candidate the search has tried, and the search goes on to the next — the same
     * shape a class's witness is certified with. Written as a throw, or as a refusal of the whole
     * point, one candidate landing elsewhere would be reported as a point no row can be written at.
     *
     * <p>What it asks is the property {@code TermRealizations} states of itself: every value built
     * there reads back as the number it was built for, and the way that would break is "a row
     * offered at an edge it does not stand on". Asked through the reading a row's own values are
     * read by, so nothing here is a second account of where a value stands.
     *
     * <p><b>A prune and not the acceptance of the row.</b> What a candidate can be held to here is
     * one parameter's value against one place, which is cheap and is less than the question. Whether
     * a row stands at a point takes the whole row and what running it recorded, and it is
     * {@code StandingAtAPoint}'s — asked of every row that is offered, after it is composed. So a
     * candidate this lets through is one worth going on with, and never one this has declared to be
     * a row at the point.
     *
     * @param refused set where a candidate was turned away for this and nothing else, which is what
     *                tells a search that ran out of candidates from one that certified none
     */
    private static CandidateCheck certifying(CandidateCheck check, MeasuredInput subject, int parameter,
                                             Map<RealizationTarget, Place> fixing,
                                             boolean[] refused) {
        return (at, candidate) -> {
            CandidateCheck.Built built = check.build(at, candidate);
            // Nothing built it, so nothing here can say where it went, and the row is offered as it
            // was composed.
            if (at != parameter || !(built instanceof CandidateCheck.Built.Value(var observed))) {
                return built;
            }
            for (Map.Entry<RealizationTarget, Place> each : fixing.entrySet()) {
                if (!subject.parameters().get(parameter).equals(each.getKey().writeRoot().head())) {
                    continue;
                }
                // Only a reading that placed the value somewhere else turns a candidate away. A
                // reading that could not be made says nothing about where the value is, and a
                // search that pruned on it would be spending this compiler's own limit as though
                // it were an answer about the value. The whole-row reading that follows is where
                // not being able to tell is recorded.
                if (readsBackAt(subject, parameter, observed, each.getKey(), each.getValue())
                        instanceof RealizationReadback.Elsewhere(String why)) {
                    refused[0] = true;
                    return new CandidateCheck.Built.Refused(why);
                }
            }
            return built;
        };
    }

    /**
     * What reading one candidate back at the term's position came to.
     *
     * <p><b>Three answers, and the third is not the second.</b> A value read where it was built for
     * and a value read somewhere else are what this walk can establish; everything else is the walk
     * unable to look, and this compiler being unable to look is not the model putting the value
     * elsewhere. Held as a {@code null} beside a sentence, the two are one — the sentence is
     * written whenever a number does not come back, so a value whose observation a limit cut short
     * is refused with a sentence saying it does not stand where it plainly does.
     *
     * <p>One occurrence is enough, as it is for a row that was written: a row stands at a point
     * where one of its readings does. And a reading that could not be made at one occurrence does
     * not take back another that placed the value — the answers are looked at in that order.
     */
    private static RealizationReadback readsBackAt(MeasuredInput subject, int parameter,
                                                   souther.compiler.observe.ObservedValue observed,
                                                   RealizationTarget target, Place at) {
        // The orders to read it back on, asked of the reading that answered them when the value was
        // built. Carried over from there instead, the two ends of one question would be two values
        // free to part, and a row would be read back on an order nothing composed it against.
        souther.compiler.inputs.TermOrders on = subject.quantities().ordersOf(target.term());
        if (on == null) {
            return new RealizationReadback.CouldNotTell(new ReadbackGap.NoOrdersOnTheEdge());
        }
        // Only this parameter's value is filled in: the walk reads the one the path names, and the
        // others are not this candidate's to say anything about.
        List<souther.compiler.observe.ObservedValue> row = new ArrayList<>(
                java.util.Collections.nCopies(subject.parameters().size(), null));
        row.set(parameter, observed);
        // Over the arms, so a walk that comes to answer a third way is one this is taught about
        // rather than one read as the walk and the type disagreeing.
        return switch (subject.inputs().valuesAt(row, target.term().subjectPath())) {
            case WalkResult.CouldNotWalk<List<souther.compiler.observe.ObservedValue>> _ ->
                    new RealizationReadback.CouldNotTell(new ReadbackGap.WalkAndTypeDisagree());
            case WalkResult.Reached(List<souther.compiler.observe.ObservedValue> values) -> {
                if (values.isEmpty()) {
                    yield new RealizationReadback.CouldNotTell(
                            new ReadbackGap.NoValueAtThePosition());
                }
                // What the number is of, asked the way the term's own reader asks it. A number one
                // position answers is read at each value standing there, and a row stands at a
                // point where one of its readings does; a number over a run is read of all of them
                // at once, since that is what the walk was given and any one of them is not it.
                Set<Incompleteness.Code> unread = EnumSet.noneOf(Incompleteness.Code.class);
                boolean stands = switch (target) {
                    case RealizationTarget.AtOnePosition _ -> {
                        boolean any = false;
                        for (souther.compiler.observe.ObservedValue value : values) {
                            switch (on.read(value)) {
                                case NumericTerm.Reading.Number number ->
                                        any |= number.value().compareTo(at) == 0;
                                case NumericTerm.Reading.Missing missing ->
                                        unread.add(missing.code());
                                case NumericTerm.Reading.NotNumber _ -> { }
                            }
                        }
                        yield any;
                    }
                    case RealizationTarget.OverARun _ -> switch (on.readOver(values)) {
                        case NumericTerm.Reading.Number number ->
                                number.value().compareTo(at) == 0;
                        case NumericTerm.Reading.Missing missing -> {
                            unread.add(missing.code());
                            yield false;
                        }
                        case NumericTerm.Reading.NotNumber _ -> false;
                    };
                };
                if (stands) {
                    yield new RealizationReadback.AtRequestedPlace();
                }
                if (!unread.isEmpty()) {
                    yield new RealizationReadback.CouldNotTell(
                            new ReadbackGap.Observation(unread));
                }
                yield new RealizationReadback.Elsewhere("it was composed to put " + target.term()
                        + " at " + at + " and does not stand there");
            }
        };
    }

    /**
     * What reading a candidate back said about where it stands.
     *
     * <p>This walk's own vocabulary and not the one an account is written in. What is asked here is
     * one parameter's value against one place, which is a cheap prune of the search — a candidate
     * this lets through is one worth going on with, and never one this has declared to be a row at
     * the point. What settles that is the whole row, read after it is composed, and what the
     * <em>account</em> then says is written in the account's words.
     */
    private sealed interface RealizationReadback {

        /** The value reads back as the number it was built for. */
        record AtRequestedPlace() implements RealizationReadback {}

        /** It reads back as some other number, and this is how that is said. */
        record Elsewhere(String why) implements RealizationReadback {}

        /** Nothing here could say where it reads, and this is what stopped the saying. */
        record CouldNotTell(ReadbackGap why) implements RealizationReadback {}
    }

    /**
     * What stopped one candidate being read back where it was built for.
     *
     * <p>Kept apart from what an account calls a gap. The cases here are this walk's own — an edge
     * that carried no orders, a path the walk and the type disagree about — and only one of them is
     * about the observation. Shared with the account's vocabulary, a prune's reasons would be
     * offered as reasons a point cannot be shown writable, and three of these have nothing to do
     * with that.
     */
    private sealed interface ReadbackGap {

        /** The fixing's edge kept no orders, so there is nothing to read the value on. */
        record NoOrdersOnTheEdge() implements ReadbackGap {}

        /** The walk and the declared type disagree about the path, which the quantity reports. */
        record WalkAndTypeDisagree() implements ReadbackGap {}

        /** The row wrote no value at the position the term names. */
        record NoValueAtThePosition() implements ReadbackGap {}

        /** The observation of the value did not come back whole. */
        record Observation(Set<Incompleteness.Code> causes) implements ReadbackGap {}
    }

    /**
     * The values that stand at one position's place of the item.
     *
     * <p>The axis's own edge where the subject has an axis at this position, which is where a count
     * taken of a location is met by whatever carries that count. Where it has none — a behavior whose
     * inputs nothing bounds has no axis and its body still draws lines between them — the value is
     * written from the declared type.
     *
     * @param besideAnother whether another position of the same item is fixed too. A count taken of a
     *                      location is met by several values and only one of them can be offered
     *                      beside a second position that is being fixed as well, which is a limit of
     *                      the reading this replaced rather than a rule: it is preserved here so that
     *                      collapsing the two searches into one changed nothing, and removing it is
     *                      its own answer to give
     */
    private static Edge edgeAt(MeasuredInput subject, RealizationTarget target, Place at,
                               boolean besideAnother,
                               souther.compiler.inputs.SearchRegion within) {
        // A number met by several values can offer only one of them beside a second position being
        // fixed as well. Whether it is met by several is the realization's question and not the kind
        // of term's: an operation whose inverse is single-valued would be the same kind of term and
        // would have been turned away here with nothing saying so (#1027).
        if (besideAnother && !TermRealizations.onlyOneValueAnswersIt(target)) {
            return Edge.none(UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        // Which value answers the number is `TermRealizations`' one answer — asked of it whatever
        // kind of number this is, so that what can be built is settled in one place. Read off the
        // kind of term here as well, an operation would gain a value nothing writes for it on the
        // day the arm for it was written, with nothing failing to say so.
        //
        // Where the value is written, which is the traversal that follows one. It stops where a
        // value is built rather than where a name is read, and that is the answer this question
        // wants: a name every case of a sum spreads is readable on a value of the sum and is not a
        // place a value is composed for. Asked here whether or not the number is one a measure was
        // drawn on: a measure says where the model divides a number and not where a value of it can
        // be put, so a search reading the second off a measure would be composing at a place this
        // walk says nothing is written.
        Type writtenAt = subject.inputs().typeAtWrittenPath(target.writeRoot());
        if (writtenAt == null) {
            return Edge.none(UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        // And both orders the value is read back on, which are the reading's. Taken off the type
        // above, the walk that answers where a value is written would be answering what a number
        // there is measured on as well, and the two are one value only for as long as no term
        // arrives where they part.
        souther.compiler.inputs.TermOrders on = subject.quantities().ordersOf(target.term());
        return edgeFrom(TermRealizations.at(writtenAt, on, at, within, subject.rules(),
                subject.inputs().policy()), target, at);
    }

    /**
     * The axes in the order the search fixes them.
     *
     * <p>Most classes first, and then parameter order and the path, so that two runs of one model
     * order them the same way and the rows come out in the same order twice.
     */
    private static MeasuredInput.MeasuredAxes ordered(MeasuredInput subject) {
        return subject.axes().where(Axis::derivable)
                .sortedBy(Comparator.comparingInt((Axis a) -> -a.classes().size())
                        .thenComparingInt(a -> {
                            int at = subject.parameters().indexOf(a.path().head());
                            return at < 0 ? Integer.MAX_VALUE : at;
                        })
                        .thenComparing(a -> a.path().toString()));
    }

    /** Whether every existing row said where it sat at this position. One that did not leaves the
     * position undecided: what the rows cover there is unknown, so what they do not cover is unknown
     * too. */
    private static boolean readEverywhere(Axis axis, List<ObservedRow> existing) {
        for (ObservedRow row : existing) {
            Classification where = row.at().get(axis.id());
            // Whether the reading stopped, and not whether it placed anything. A row whose list
            // holds one value in a class and one nothing could read placed something and is short
            // of the rest, so what it does not cover here is as unknown as if it had placed nothing.
            if (where != null && where.stopped() != null) {
                return false;
            }
        }
        return true;
    }

    /** Where {@code id} sits among {@code axis}'s classes, or -1 where it is none of them. */
    private static int classIn(Axis axis, String id) {
        for (int c = 0; c < axis.classes().size(); c++) {
            if (axis.classes().get(c).id().equals(id)) {
                return c;
            }
        }
        return -1;
    }




    /** What a row is about, in the words the model uses. The class's label rather than its id: an id
     * is scoped by carrying its own path, and a description that carries the path already would say
     * it twice. */
    private static String label(Axis axis, int cls) {
        return axis.path() + "=" + axis.classes().get(cls).label();
    }

    /**
     * The same, for a class named the way an obligation names it.
     *
     * <p>What a report writes beside the row, worked out where the classes are rather than kept
     * beside the obligation. A label copied into the obligation would be a second spelling of the
     * class, free to disagree with the axis the day either moved.
     */
    static String labelOf(MeasuredInput subject, ClassOfAPosition owed) {
        for (Axis axis : subject.axes().axes()) {
            if (!axis.id().equals(owed.at())) {
                continue;
            }
            for (PartitionClass cls : axis.classes()) {
                if (cls.id().equals(owed.classId())) {
                    return axis.path() + "=" + cls.label();
                }
            }
        }
        throw new IllegalStateException(
                "a row was owed for a class the subject does not divide: " + owed);
    }

    /**
     * The positions the cell is about, at the classes the row came to hold.
     *
     * <p>The cell says which classes a position may hold and the row holds one of them, so the name
     * is read off the row: a name carrying the set would say what the cell allows rather than what
     * this row is. Positions the cell says nothing about stay out — the assignment chose them and
     * the combination says nothing about where they stand.
     *
     * <p>For saying which combination a search went to and came back from, and not for naming a
     * row: a row is composed for the arms it was looked for.
     */
    private static List<String> labels(List<Axis> axes, InteractionCells.Cell cell, int[] where) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < axes.size(); i++) {
            if (cell.narrows(i) && where[i] >= 0) {
                out.add(label(axes.get(i), where[i]));
            }
        }
        return out;
    }

    // --- looking for a row that fills a combination ----------------------------------------------

    /**
     * What the search for a row filling one combination came to.
     *
     * <p>A row seen filling the combination and a row offered because nothing could watch it are
     * not the same thing to have found. They differ in what may afterwards be concluded from the row
     * and in whether this generation may say its rows were confirmed, so which of them it is, is the
     * answer — rather than something read back off an empty account of the run.
     *
     * <p>And coming back with nothing is three answers rather than one. A combination the model does
     * not have, a search that tried everything the combination leaves, and a search a bound stopped
     * are three different pieces of news: the first takes the combination away, the second is about
     * the model, and the third is about this search and says nothing about the model at all. Held as
     * one value with a reason inside it, the third arrived wearing the second's clothes — the last
     * candidate's refusal offered as the combination's answer, over candidates nothing tried
     * ({@link Completeness}).
     */
    private sealed interface Witness {

        /** A row seen filling the combination, carrying the witness — that being the only value
         *  which says so. */
        record Certified(GeneratedRow row, CellSelection.CertifiedWitness by) implements Witness {}

        /** A row nothing could watch, offered on the strength of the reading alone. */
        record Unconfirmed(GeneratedRow row, int[] where) implements Witness {}

        /** No reading to look at: the model does not have this combination. Never a search that
         *  failed. */
        record NoCombination(String said) implements Witness {}

        /** Every candidate of every reading was tried and none answered, and what the last of them
         *  came to. Read as the combination's answer, that is what it is: nothing was left untried
         *  behind it. */
        record Exhausted(List<String> classes, UnresolvedCombination.Reason reason, String detail,
                         Optional<String> said) implements Witness {}

        /** A bound stopped the search with candidates it had not tried. What the ones it did try
         *  came to is that candidate's news and not this combination's. */
        record Limited(List<String> classes) implements Witness {}
    }

    /**
     * A row that fills {@code selection}, looked for among the assignments it leaves open.
     *
     * <p>Composing and confirming are one act here and are two questions. A candidate is composed by
     * fixing every position, which the combination settles for some of them and the assignment
     * settles for the rest; then it is run, and what it did is held against what the combination
     * says a row filling it does. A candidate that went elsewhere is dropped and another assignment
     * is tried, because which assignment was chosen is a choice this made rather than something the
     * combination said.
     *
     * <p>What a run of candidates that all missed establishes is that they were not witnesses. It is
     * not that the combination is unreachable, and it is not by itself that the reading naming the
     * combination is wrong — the assignments were this search's, and so was the number of them.
     */
    private static Witness witnessFor(MeasuredInput.MeasuredAxes axes,
                                      CellSelection selection, CandidateCheck check, Trial trial,
                                      Map<List<String>, Watched> applied, List<ArmProbe> takes,
                                      List<ResolvedOrigin> origins, FixtureReferences references) {
        Reading reading =
                new Reading(axes, selection, check, trial, applied, takes, origins, references);
        Traversal walked = selection.interpretations(reading);
        return walked == Traversal.SATISFIED ? reading.found : reading.nothing(walked);
    }

    /**
     * Looking for a row at one combination, over the readings of what it asks.
     *
     * <p>What the combination can be asking is the combination's to enumerate and how many of them
     * this may look at is this search's to bound, which are two things and were one. Counted off by
     * the enumeration, the bound was spent on combinations of names before anything asked whether a
     * value could hold them — so a combination whose first few names cannot be in one value went
     * unanswered with its readings untried.
     *
     * <p>So a reading no value can hold costs nothing here: it is not a reading. What is counted is
     * the readings this actually searched, and the bound is refused in front of the one after them
     * — which is a reading that exists and that nobody looked at, and the only thing that makes this
     * search incomplete.
     */
    private static final class Reading implements Taking<Interpretation> {

        private final MeasuredInput.MeasuredAxes axes;

        private final CellSelection selection;

        private final CandidateCheck check;

        private final Trial trial;

        private final Map<List<String>, Watched> applied;

        private final List<ArmProbe> takes;

        private final List<ResolvedOrigin> origins;

        /** Whether the combination offered anything at all, which tells a combination the model
         *  does not have from one whose readings are none of them one value. */
        private boolean offered;

        /** How many readings were searched, which is what this is allowed so many of. */
        private int searched;

        /** What the last candidate that composed nothing came to, for a search that tried them
         *  all. */
        private Attempt last;

        /** Where the last candidate stood, which is what names the combination in a report. */
        private int[] where;

        /** Whether a row was composed, run, and seen going somewhere else. */
        private boolean missed;

        private Completeness looked = Completeness.NOTHING_YET;

        /** The row, once one is seen filling the combination or offered because nothing watched
         *  it. */
        private Witness found;

        private Reading(MeasuredInput.MeasuredAxes axes, CellSelection selection,
                        CandidateCheck check, Trial trial, Map<List<String>, Watched> applied,
                        List<ArmProbe> takes, List<ResolvedOrigin> origins,
                        FixtureReferences references) {
            this.axes = axes;
            this.selection = selection;
            this.check = check;
            this.trial = trial;
            this.applied = applied;
            this.takes = takes;
            this.origins = origins;
            this.references = references;
        }

        /** The run's minter for the references what this composes will hold. */
        private final FixtureReferences references;

        @Override
        public Taken take(Interpretation reading) {
            offered = true;
            int[] about = about(reading);
            // Whether one value can hold what this reading asks, which is the model's answer and not
            // the combination's. Asked of the classes it pins alone: what they require is required
            // whichever value the row is written against, so this does not change with the origin.
            if (standing(axes.axes(), wanting(axes.axes(), null, reading), about,
                    selection.cell()::admits)
                    == null) {
                // not a reading, and so no part of what this search is allowed
                return Taken.AND_MORE;
            }
            if (searched >= MOST_INTERPRETATIONS) {
                return Taken.NOT_TAKEN;   // a reading of this combination that nobody looked at
            }
            searched++;
            // The values the model states first, nearest first, and what they may spend counted in
            // runs. Then the composition, whatever they spent: it is not one of them and was not
            // competing with them for their share, and a caller told the stated values were all
            // refused would go looking for a value the model cannot hold.
            Traversal walked = nearestFirst(axes.axes(), reading, origins, selection.cell()::admits,
                    new Running(MOST_RUNS_PER_INTERPRETATION));
            if (walked == Traversal.SATISFIED) {
                return Taken.AND_DONE;
            }
            Traversal composed = composing(axes.axes(), reading, origins, selection.cell()::admits,
                    new Running(MOST_RUNS_PER_INTERPRETATION));
            if (composed == Traversal.SATISFIED) {
                return Taken.AND_DONE;
            }
            looked = walked == Traversal.STOPPED || composed == Traversal.STOPPED
                    ? looked.cutShort() : looked.searched();
            return Taken.AND_MORE;
        }

        /** What to say when no reading answered. */
        private Witness nothing(Traversal walked) {
            if (walked == Traversal.STOPPED) {
                looked = looked.cutShort();
            }
            List<String> named =
                    where == null ? List.of() : labels(axes.axes(), selection.cell(), where);
            return switch (looked.found()) {
                // No reading to look at. Either the combination offered nothing, or none of what it
                // offered is one value — and both are the model not having this combination rather
                // than a search that failed at it.
                case Completeness.Nothing.NO_READING -> new Witness.NoCombination(offered
                        ? "the positions this combination names are not in one value"
                        : "a position this combination names has nothing left at it");
                // Something was left undone: a reading nobody looked at, or a candidate nobody ran.
                // Said as that whatever the ones that were tried came to — the miss of the third of
                // them is a fact about that candidate, and offered as the combination's answer it
                // stands for a space this never entered.
                case Completeness.Nothing.SEARCH_STOPPED -> new Witness.Limited(named);
                case Completeness.Nothing.LOOKED_EVERYWHERE -> {
                    if (missed) {
                        // Rows were composed and run, and went somewhere else. Which says they were
                        // not witnesses, and not that the combination is unreachable.
                        yield new Witness.Exhausted(named,
                                UnresolvedCombination.Reason.NO_CERTIFIED_WITNESS, null,
                                Optional.empty());
                    }
                    if (last != null && last.row() == null) {
                        yield new Witness.Exhausted(named, last.reason(), last.detail(),
                                last.said());
                    }
                    // Nothing was composed and nothing was refused, which takes every reading
                    // leaving no assignment at all. Named rather than guessed at, the same way
                    // every other empty result here is.
                    yield new Witness.Exhausted(named,
                            UnresolvedCombination.Reason.NO_CANDIDATE_WAS_OFFERED, null,
                            Optional.empty());
                }
            };
        }

        /**
         * Running the candidates offered for one reading, up to what that reading may cost.
         *
         * <p>Counted in runs, because a run is what this is protecting. Reaching the arm is what a
         * row for a combination has to do and only the behavior can say whether it did — so a
         * candidate the model refuses costs nothing, and neither does one whose values a run was
         * already watched at. Counted per candidate instead, a model whose rules refuse a few
         * compositions spent a reading's whole share without asking the behavior anything, and a
         * reading whose remaining candidates were all values something had already run came back
         * saying the search had stopped.
         *
         * <p>So the bound is refused in front of a candidate that needs a run there is none left
         * for. That candidate is the run nobody did, and it is the only thing here that leaves this
         * reading incomplete.
         */
        private final class Running implements Taking<Candidate> {

            private final int most;

            private int runs;

            private Running(int most) {
                this.most = most;
            }

            @Override
            public Taken take(Candidate candidate) {
                Map<String, FixtureTemplate> given = candidate.from().composes() ? Map.of()
                        : against(axes, candidate.delta(), candidate.where(),
                                candidate.from().baseline(), references);
                if (!candidate.from().composes() && given.isEmpty()) {
                    // nothing here can be written against the model's value
                    return Taken.AND_MORE;
                }
                where = candidate.where();
                last = build(axes, candidate.where(), check, given);
                if (last.row() == null) {
                    // nothing composed here; another assignment may compose
                    return Taken.AND_MORE;
                }
                // Composed for the arms it was looked for, and not for the combination it was found
                // at. The combination is where the search went; the arms are what somebody is owed
                // a row at. One row answering two of them is two answers and not one composite
                // thing.
                GeneratedRow named = new GeneratedRow(
                        takes.stream().map(Purpose.ForAnArm::new).map(Purpose.class::cast).toList(),
                        last.row().inputs());
                // Run once per set of values, however many places a row of them was looked for.
                // What a run of one row did is one fact: two arms searched on their own can come to
                // the same values, and running them again would be the same row applied twice and
                // counted twice.
                //
                // Keyed by what the row is written as. A template is the text and the expression it
                // stands for, and the second is a tree whose equality is its own — so a pair of
                // them makes no key, while the text is the whole of what a row applied twice would
                // be.
                List<String> written = named.inputs().stream().map(FixtureTemplate::text).toList();
                Watched watched = applied.get(written);
                if (watched == null) {
                    if (runs >= most) {
                        return Taken.NOT_TAKEN;   // this candidate is the run nobody did
                    }
                    runs++;
                    watched = trial.run(named.inputs());
                    applied.put(written, watched);
                }
                switch (watched) {
                    // Nothing can say where it went, so nothing certifies it and nothing refutes
                    // it. Offered as it was before anything ran, and said to be. Both of the ways
                    // that happens come here: nothing applied the row, or nothing was recording
                    // while it was applied.
                    case Watched.NoAccount _ -> {
                        found = new Witness.Unconfirmed(named, candidate.where());
                        return Taken.AND_DONE;
                    }
                    case Watched.Ran ran -> {
                        // Through the one thing that can say a row filled a combination, which is
                        // the same thing a row already in the file is put through.
                        Optional<CellSelection.CertifiedWitness> seen =
                                selection.certifying(candidate.where(), ran.seen());
                        if (seen.isPresent()) {
                            found = new Witness.Certified(named, seen.get());
                            return Taken.AND_DONE;
                        }
                        missed = true;
                    }
                }
                return Taken.AND_MORE;
            }
        }
    }

    /** The positions one reading is about, in the axes' own order. */
    private static int[] about(Interpretation reading) {
        return reading.at().stream().mapToInt(Integer::intValue).sorted().toArray();
    }




    // --- turning classes into a row -------------------------------------------------------------

    private record Attempt(GeneratedRow row, UnresolvedCombination.Reason reason, String detail,
                           Optional<String> said) {

        static Attempt of(GeneratedRow row) {
            return new Attempt(row, null, null, Optional.empty());
        }

        static Attempt no(UnresolvedCombination.Reason reason, String detail) {
            return new Attempt(null, reason, detail, Optional.empty());
        }
    }

    /**
     * One assignment of classes, built into the values a row would carry.
     *
     * <p>What a position offers is values, and what has to build is all of them together: the check is
     * the model's own constructor over the whole input, so a field's value can be refused for what
     * another field was given. The unit being tried is the tuple, not the value — walking one index
     * across every position at once tries the diagonal of the choices and misses the rest, and two
     * fields whose rules are the same but written in a different order then land on different indices
     * and never meet.
     *
     * <p>So the assignments are walked outward from the one where every position takes its first
     * value: then the ones a single position has moved one step from, then two, and so on. Nearest
     * first, because a position's first value is the one that stands for it, and a row that took a
     * later one at every position is a row further from what the model says it is about. The walk
     * stops at {@link #MAX_TUPLES}, and stopping is reported as having stopped rather than as
     * everything having been refused.
     *
     * <p>The parameters {@code given} names are written as it says instead of composed, which is
     * what makes a value the model already states an origin of the search rather than a
     * rewrite of its answer. Composed first and rewritten after, a row the baseline could have been
     * written for came back as one nothing composed — a representative chosen from the classes
     * alone breaks a rule relating two positions while the model's own value does not — and a row
     * the baseline needed nothing beside came back carrying whatever the composition had needed.
     */
    private static Attempt build(MeasuredInput.MeasuredAxes axes, int[] where,
                                 CandidateCheck check, Map<String, FixtureTemplate> given) {
        MeasuredInput subject = axes.subject();
        LocationWrites decided = new LocationWrites();
        // What every position of this row has to be for the classes it sits in to exist. Read off
        // the paths and off the classes together, because both state one: a position under a
        // refinement requires it by being there at all, and a class of the position above states
        // the same requirement by being the class it is.
        Requirements required = Requirements.NONE;
        for (int i = 0; i < axes.size(); i++) {
            // A position this row stands at no class of. What it would have required is not
            // something the row has to meet, and there is nothing to compose for it.
            if (where[i] == NOT_HERE) {
                continue;
            }
            TermPath path = axes.get(i).path();
            String at = label(axes.get(i), where[i]);
            Requirements.Merge both =
                    required.merge(axes.get(i).requiring(axes.get(i).classes().get(where[i])));
            // A row that would have to be two things at one position. Which is not a combination
            // the model has at all — said here, and said as that: reported as a value nothing
            // composed, an author would go looking for a row that cannot exist.
            if (!(both instanceof Requirements.Merge.Merged merged)) {
                // Which position, and which two it would have to be. The reason is the category a
                // reader acts on and this is the sentence that says which case of it this was —
                // without it an author is told a row is impossible and left to work out why.
                Requirements.Merge.Conflict against = (Requirements.Merge.Conflict) both;
                return new Attempt(null, UnresolvedCombination.Reason.ONE_POSITION_CANNOT_BE_BOTH,
                        at, Optional.of("`" + against.at() + "` would have to be both "
                                + against.one().spelled() + " and " + against.other().spelled()));
            }
            required = merged.requirements();
            PartitionClass cls = axes.get(i).classes().get(where[i]);
            switch (cls.representatives().evaluate()) {
                // A class that narrows the position states the narrowing and nothing else. What
                // stands at the narrowed position is built there, out of the narrowed type — which
                // is where the values this class would have offered came from in the first place.
                //
                // Said once for every kind of case, because a class narrows or it does not: a case
                // holding a record offers no value and a case wrapping one offers the value it
                // wraps, and taking the second as a value of the unnarrowed position is one
                // location decided twice, under two names. The plan reads the first of them and the
                // class fixed at the narrowed position is never looked at.
                case RepresentativeSource.Evaluation.Values values -> {
                    if (cls.selects() == null
                            && decided.write(path, values.written())
                                    == LocationWrites.Written.CONFLICTING) {
                        // Two of this row's classes are of one location and offer different values
                        // for it. Taking either leaves the other's class unanswered while the row
                        // is offered as covering it, so neither is taken.
                        return new Attempt(null,
                                UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE, at,
                                Optional.of("`" + path + "` would have to hold two values at once"));
                    }
                }
                // Not a value but how one is arrived at: the walk below builds one at this position,
                // field by field, the way it builds every other record. What it is built through is
                // already in the requirements, which is where the plan reads it.
                case RepresentativeSource.Evaluation.Compose _ -> { }
                // What the class said about itself. A class that recorded why nothing was produced
                // for it knows something this does not, and the two answers are not the same claim:
                // one is that nothing was arrived at, and the other is that nothing can be. Read as
                // the first, a case somebody can write in one line is reported as a row that does
                // not exist.
                case RepresentativeSource.Evaluation.NothingProducible cannot -> {
                    return new Attempt(null, UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE, at,
                            Optional.of(cannot.why()));
                }
            }
        }
        List<FixtureTemplate> inputs = new ArrayList<>();
        for (int p = 0; p < subject.parameters().size() && p < subject.types().size(); p++) {
            FixtureTemplate written = given.get(subject.parameters().get(p));
            if (written != null) {
                // Written as the caller says, and put through the same check a composed value goes
                // through: how a row is written never decides whether the model allows it.
                Optional<String> refused = check.refuse(p, written);
                if (refused.isPresent()) {
                    return new Attempt(null, UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED,
                            subject.parameters().get(p), refused);
                }
                inputs.add(written);
                continue;
            }
            Outcome tried = valueFor(subject, p, axes.axes(), decided, required, check);
            switch (tried) {
                case Outcome.Built(FixtureTemplate value) -> inputs.add(value);
                // The word alone. What this composes is one assignment of classes, and what it
                // hands back has never carried a figure — an assignment that came to nothing is
                // tried again at the next one, so there is no account here for a figure to be
                // owed to. Which figures were reached is the same question on this route as it is
                // for every other budget, and it is not this one's.
                case Outcome.Unresolved(UnresolvedCombination.Reason why, String detail) -> {
                    return Attempt.no(why, detail);
                }
                case Outcome.Stopped stopped -> {
                    return Attempt.no(stopped.why(), stopped.detail());
                }
                case Outcome.Unexhausted some -> {
                    return Attempt.no(some.why(), some.detail());
                }
                case Outcome.Limited(UnresolvedCombination.Reason why, String detail,
                                     java.util.Set<CompositionBudget> _) -> {
                    return Attempt.no(why, detail);
                }
                // A value nothing planned, which this route says in the word for a reading no
                // search could be made of. It carries no figure for the same reason none of the
                // others does here.
                case Outcome.Unplanned _ -> {
                    return Attempt.no(UnresolvedCombination.Reason
                            .NO_READING_OF_THE_LINE_COULD_BE_SEARCHED, null);
                }
            }
        }
        // The values, and no name. What a row is about is what it was composed for, which is the
        // caller's question and not this one's: this is handed an assignment and does not know
        // whether it is a class, a combination the body decides together, or an edge. Named here
        // from the assignment, every row said every position it happened to hold — which is what
        // put three classes in the name of a row composed for one (issue #967).
        return Attempt.of(new GeneratedRow(new Purpose.Unstated(), inputs));
    }

    /**
     * One parameter's value, searched for on its own.
     *
     * <p>Each parameter is built and refused on its own — the check is the model's constructor for that
     * one type, and a rule relating two of them is not something a model can write. So the choices
     * under one parameter and the choices under another do not multiply, and searching them together
     * would spend the bound on assignments that differ only in a parameter already settled. Two
     * parameters of eight either-or fields are two searches of 256, not one of 65,536.
     */
    private static Outcome valueFor(MeasuredInput subject, int p, List<Axis> axes,
                                    LocationWrites decided,
                                    Requirements required, CandidateCheck check) {
        TermPath at = TermPath.of(subject.parameters().get(p));
        Map<TermPath, List<FixtureTemplate>> here = new LinkedHashMap<>();
        for (Axis axis : axes) {
            List<FixtureTemplate> already = decided.at(axis.path());
            if (axis.path().head().equals(at.head()) && already != null) {
                here.put(axis.path(), already);
            }
        }
        return valueAt(subject, p, here, settledIn(here), required, check);
    }

    /**
     * The positions a caller fixed at one number.
     *
     * <p>Only where the position has a single value to take. A class offers one value to stand for
     * it, and that is the one the row will carry, so the rest of the record can be chosen beside it;
     * a position still holding several is not settled at all and nothing is claimed of it.
     */
    private static Map<TermPath, Place> settledIn(Map<TermPath, List<FixtureTemplate>> decided) {
        Map<TermPath, Place> out = new LinkedHashMap<>();
        decided.forEach((path, candidates) -> {
            if (candidates.size() == 1) {
                Place number = Counts.writtenIn(candidates.get(0).value());
                if (number != null) {
                    out.put(path, number);
                }
            }
        });
        return out;
    }

    /**
     * One parameter's value, with the positions the caller fixed already decided.
     *
     * <p>{@code additional} is what has to hold besides whatever the fixed paths already state.
     * What a path under a refinement requires is read from the path, where it is written, and the
     * plan puts the two together — so a caller with nothing of its own to add hands over nothing
     * and loses none of it.
     */
    private static Outcome valueAt(MeasuredInput subject, int p,
                                   Map<TermPath, List<FixtureTemplate>> decided,
                                   Map<TermPath, Place> settled,
                                   Requirements additional, CandidateCheck check) {
        // Where a value has to be built under this parameter, worked out once. What each position
        // may take, the search that chooses them one at a time, and the composing of what was chosen
        // all read this, so there is no second reading of the declarations for one of them to
        // disagree with.
        TermPath root = TermPath.of(subject.parameters().get(p));
        // How many the rules say a list at a position holds at the fewest, read from the same
        // reading of the parameter the values are chosen against. A list built around an element
        // has to meet that too: a row holding an element in the class and breaking the rule about
        // how many the list holds is not a row.
        FieldDomains under = rulesOf(subject.types().get(p), subject.rules(),
                subject.inputs().policy(), under(root, settled), subject.machines());
        ConstructionPlan.Result planned = ConstructionPlan.of(subject.types().get(p), root,
                subject.symbols(), decided.keySet(), additional,
                (at, building) -> heldRange(under, at, building, subject.rules()));
        ConstructionPlan plan;
        switch (planned) {
            case ConstructionPlan.Result.Planned made -> plan = made.plan();
            // What the model settles, which is not something this fell short of. Said before
            // anything was searched for and standing however far the search then went, so no figure
            // of this compiler's stands beside it: an author raising one would raise it and be told
            // the same thing.
            case ConstructionPlan.Result.Refused(ConstructionPlan.ModelRefusal why) -> {
                return switch (why) {
                    // The same answer the class search gives when two classes select different
                    // refinements of one position.
                    case ConstructionPlan.ModelRefusal.Conflict against ->
                            new Outcome.Unresolved(
                                    UnresolvedCombination.Reason.ONE_POSITION_CANNOT_BE_BOTH,
                                    "`" + against.at() + "` would have to be both "
                                            + against.one().spelled() + " and "
                                            + against.other().spelled());
                    case ConstructionPlan.ModelRefusal.NoRoom noRoom ->
                            new Outcome.Unresolved(
                                    UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                                    "`" + noRoom.at() + "` would have to hold " + noRoom.needed()
                                            + " for the value placed in it, and the rules leave"
                                            + " room for " + noRoom.holds().most());
                };
            }
            // What the caller asked for is under a position the plan stopped short of reading, so
            // there is nothing to search: a row composed against such a plan would be one the
            // caller's own value is missing from. Nothing here ran, so nothing here has a search's
            // word to give — what travels is the figure, and what a reader is told is that no
            // search could be made rather than what one found.
            case ConstructionPlan.Result.Beyond(Set<CompositionBudget> by) -> {
                return new Outcome.Unplanned(by);
            }
            // Something is asked for under a position that holds nothing until a narrowing says
            // what stands there, and this search has not said which. There is no plan, so nothing
            // was searched and nothing was refused: what a reader is owed is that no value was
            // composed and where the way down stopped. Said as a row nothing composes rather than
            // as a failure of this compiler, because a model reaches it — a class may state a
            // narrowing below a position nothing narrows.
            case ConstructionPlan.Result.Unnarrowed(TermPath where, List<Refinement> narrowings) -> {
                return new Outcome.Unresolved(UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                        narrowings.isEmpty()
                                ? "`" + where + "` holds nothing that could be built under it"
                                : "`" + where + "` stands at "
                                        + narrowings.stream().map(Refinement::spelled)
                                                .collect(java.util.stream.Collectors.joining(" or "))
                                        + ", and nothing said which of them the value under it is");
            }
        }
        Choices choices = choicesOf(subject, p, plan, decided, settled);
        if (choices.missingAt() != null) {
            // A position nothing stands at is the declarations' answer where the plan reached
            // everything, and this compiler's where it did not: what the search would have been
            // offered at a position the plan stopped short of is whole values of a type it declined
            // to look inside, and none being available is that decision and not a fact about the
            // model.
            return whatTheSearchCameTo(Set.of(), Set.of(), choices.missingUnderAFigure(),
                    new Outcome.Unresolved(UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                            choices.missingAt()));
        }
        Outcome product = walk(subject, p, choices, check);
        if (product instanceof Outcome.Built) {
            return product;
        }
        // Every position took its value knowing only what the caller had settled, so a rule relating
        // two of them was satisfied only where the lists happened to already hold a pair that does.
        // Asked again choosing one position at a time, each from what is left once the ones before it
        // are asserted, which is the only way `a < b` is met in general.
        Outcome conditioned = conditioned(subject, p, plan, decided, settled, check);
        if (conditioned instanceof Outcome.Built) {
            return conditioned;
        }
        // A pass that stopped at its bound has not tried everything it had, and neither pass may be
        // reported as though it had: `ALL_CANDIDATES_REJECTED` is what a reader is told nothing else
        // can be written at, and a search still holding assignments it never composed has not
        // established that.
        Set<CompositionBudget> stopped = EnumSet.noneOf(CompositionBudget.class);
        Set<CompositionRepertoire> writes = EnumSet.noneOf(CompositionRepertoire.class);
        for (Outcome each : List.of(product, conditioned)) {
            // Both passes' budgets and not one of them. Neither pass outranks the other here: each
            // stopped where it stopped, and a reader wanting to know what would let this go further
            // is owed every budget that would.
            //
            // And what either of them walked in part, which is the same reckoning in the other
            // vocabulary: a pass that wrote some of a population has not shown that nothing else is
            // there, whether or not the other pass met a figure.
            if (each instanceof Outcome.Stopped(Set<CompositionBudget> by,
                    Set<CompositionRepertoire> notAllOf, String _)) {
                stopped.addAll(by);
                writes.addAll(notAllOf);
            }
            if (each instanceof Outcome.Unexhausted(Set<CompositionRepertoire> notAllOf,
                    String _)) {
                writes.addAll(notAllOf);
            }
        }
        if (!stopped.isEmpty()) {
            return new Outcome.Stopped(stopped, writes, null);
        }
        if (!writes.isEmpty()) {
            return new Outcome.Unexhausted(writes, null);
        }
        // Every value that was offered was refused, which is only the whole story where every value
        // the rules allow was offered. A position that read a count past what a row is built to carry,
        // or that has more pairings than are built at once, held something back, and saying so is the
        // difference between a fact about the model and a fact about this.
        // What each of them was short of, handed over unmerged and unranked. Which of the two a
        // reader is owed is one decision and it is made in one place.
        // Nothing of a population is short here. What a proposal holds back is a figure of this
        // compiler's over what it builds, and every value of the kind it does build is one it would
        // offer.
        HeldBack held = heldBack(subject, plan, under);
        return whatTheSearchCameTo(held.offer(), Set.of(), held.plan(), product);
    }

    /**
     * What the search came to, said as an answer about the whole point or as one about less.
     *
     * <p>The one place that decides it, because the decision is one sentence: an ordinary answer
     * over a plan that stopped short is not an answer about everything the point had. Made at each
     * of the places a search can come back empty, the places would part — and the way they part is
     * that one of them says the model has nothing here, which is the reading this exists to stop.
     *
     * <p>A row is an answer whatever the plan gave up on. What the figure took away is positions
     * the row did not need, and a reader owed something about a value they have in hand is a reader
     * being told about this compiler's bookkeeping.
     */
    private static Outcome whatTheSearchCameTo(Set<CompositionBudget> offerCut,
                                               Set<CompositionRepertoire> offerWritesSomeOf,
                                               Set<CompositionBudget> planCut, Outcome came) {
        return switch (came) {
            case Outcome.Unresolved(UnresolvedCombination.Reason why, String detail) -> {
                // An offer still holding values outranks a plan short of the point, and does not
                // join it. A position that was never offered everything its rules allow leaves a
                // search that has not tried what it had — so nothing here has been exhausted, and
                // the plan being short is not yet anything a reader is owed: raise the offer's
                // figure and the search may compose a row without the plan changing at all. Said
                // together, an author is sent to raise a figure that would change nothing.
                //
                // Which holds however the offer came to be short of everything, and the two ways it
                // does are not joined either. One is a figure to raise and one is work nobody has
                // done, so what outranks the plan is that the offer is incomplete, and what a
                // reader is then told is which of the two made it so.
                // And the two together where both are, since neither is the other's absence: a
                // figure refused a candidate and a population was walked in part, and a reader owed
                // one of them is owed the other. Kept as the stop alone, the second is lost at the
                // one boundary that had it.
                if (!offerCut.isEmpty()) {
                    yield new Outcome.Stopped(offerCut, offerWritesSomeOf, detail);
                }
                if (!offerWritesSomeOf.isEmpty()) {
                    yield new Outcome.Unexhausted(offerWritesSomeOf, detail);
                }
                yield planCut.isEmpty() ? came : new Outcome.Limited(why, detail, planCut);
            }
            // A search a figure stopped already names one, and nothing here turns that into a
            // second account of the same emptiness. A row stands whatever the plan gave up on, and
            // a value nothing planned had no search for this to be about.
            case Outcome.Built _, Outcome.Stopped _, Outcome.Unexhausted _, Outcome.Limited _,
                 Outcome.Unplanned _ -> came;
        };
    }

    /**
     * What the one edge under this parameter held back, where the refusal is of the values it
     * offered.
     *
     * <p>Values that were never built were not among the ones refused, so what the edge held back
     * is part of why a reader was told nothing could be written. Asked in one place, because every
     * outcome that can come back saying every candidate was refused is owed the same reading —
     * spelled at one of them, the others report a refusal of values that were never offered.
     *
     * <p>Only where one edge offered them. A point of a form fixes several positions under one
     * parameter, and which of their edges the refusal was about is not something this knows; taken
     * from whichever came first, the reason named the wrong position's search.
     */
    private static <T> Set<T> whatTheEdgeHeldBack(
            Map<TermPath, Set<T>> heldBack,
            Map<TermPath, List<FixtureTemplate>> here, UnresolvedCombination.Reason why) {
        if (here.size() != 1 || why != UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED) {
            return Set.of();
        }
        return heldBack.getOrDefault(here.keySet().iterator().next(), Set.of());
    }

    /**
     * The word a search comes back with where it offered candidates and read none of them back
     * standing at the point.
     *
     * <p>Said in one place because it is one rule. Such a search has not shown that every value the
     * rules allow was refused: what it found out is that what it built did not stand where it was
     * built for, which is its own answer. Spelled at each outcome that can carry the word, the
     * outcomes part the first time the rule is edited.
     */
    private static UnresolvedCombination.Reason nothingStoodWhereItWasBuilt(
            boolean uncertified, UnresolvedCombination.Reason why) {
        return uncertified && why == UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED
                ? UnresolvedCombination.Reason.NO_CERTIFIED_WITNESS : why;
    }

    /**
     * Whether {@code axis} stands inside a collection the rules leave no room in.
     *
     * <p>Asked of the collections the position is inside and not of the position itself: what a rule
     * capping a collection at none says is that nothing stands at any position under it, whatever
     * the values there could otherwise be. Asked of every one of them, because a position two
     * sequences deep needs each of them to hold something — read off the outermost alone, a list of
     * lists whose inner lists hold nothing was offered rows for what the inner lists hold.
     */
    private static boolean holdsNothing(MeasuredInput subject, Axis axis) {
        for (TermPath inside : axis.path().sequencesContainingIt()) {
            // A position of the input, because the axis is at one and a container it stands inside
            // is a position the same reading found on the way down to it.
            if (subject.quantities().mostHeldAt(
                    new souther.compiler.inputs.PositionId(inside)) < 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * From how few to how many the rules let the value built at {@code path} hold.
     *
     * <p>Both ends off one reading of {@code rules}, which is the reading the values are chosen
     * against. A collection built around an element has to meet both — the floor says how many it
     * holds in all, and the cap says whether that many fit — and taken
     * as two readings the two answered about different states of the row.
     */
    private static DeclaredBounds.CountRange heldRange(FieldDomains rules, TermPath path,
                                                       Type building,
                                                       RuleReadingSource ruleSource) {
        RuleKey field = fieldUnder(path);
        return Partitions.heldRange(building, ruleSource,
                field == null ? null : rules.heldAt(field));
    }

    /**
     * Why a position of this parameter offered less than its rules allow, or null where none did.
     *
     * <p>Off the reading the values were chosen against, handed in rather than made again. A rule
     * counting one field against another asks for nothing in particular until the row fixes the
     * other, so a reading without them answers about a rule this row is no longer under — and would
     * say "every value tried was refused" of a position whose values were never built. Read a
     * second time here, the two readings are of one row and are free to come apart the first time
     * either is given something the other is not.
     */
    private static HeldBack heldBack(MeasuredInput subject, ConstructionPlan plan,
                                     FieldDomains rules) {
        // Every budget that held a position back, and not the first or the strongest. Two positions
        // stopped by two budgets are two things this compiler declined to do, and a reader asking
        // what would let the search go further is owed both — read as one, whichever the walk met
        // last was the whole answer.
        java.util.Set<CompositionBudget> budgets =
                java.util.EnumSet.noneOf(CompositionBudget.class);
        for (ConstructionPlan.Slot each : plan.slots()) {
            RuleKey field = fieldUnder(each.at());
            budgets.addAll(Partitions.notBuilt(each.type(), subject.rules(),
                    subject.inputs().policy(), field == null ? null : rules.heldAt(field)));
        }
        return new HeldBack(budgets, plan.cutBy());
    }

    /**
     * What a search of this parameter had less of than the point had.
     *
     * <p><b>Two ways of having less, and they are neither one set of budgets nor a choice made
     * here.</b> A position that offered fewer values than its rules allow leaves a search that was
     * not given everything there was to try; a plan that stopped short of a position leaves a
     * search whose answer is about fewer positions than the value has. The figures look alike and
     * what a reader is owed differs, so putting them in one set would leave the only way back being
     * to ask a budget which kind of shortfall it was — and the same figure is either, depending on
     * where it was reached.
     *
     * <p>Which of the two a reader is owed is decided in one place
     * ({@link #whatTheSearchCameTo}) and not here. Decided here as well, the rule would be written
     * twice, and the second reader to learn of a short offer — the one that learns it only after
     * the search came back — would have nowhere to say so.
     *
     * <p><b>Both are this compiler's, and nothing read here says the model refuses anything.</b>
     * What the model settles about a collection with no room for what is placed in it is settled
     * where the plan is made ({@link ConstructionPlan.ModelRefusal.NoRoom}), before any of this
     * ran. Asked again here, it would be asked of a search that had already happened — and
     * whichever answer came back first would be the one a reader got.
     *
     * @param offer what the offers were short of
     * @param plan  what the plan was short of. Either may be empty, and both being empty is a
     *              search that had the whole of what the point had
     */
    private record HeldBack(Set<CompositionBudget> offer, Set<CompositionBudget> plan) {

        private HeldBack {
            offer = Set.copyOf(offer);
            plan = Set.copyOf(plan);
        }
    }

    /**
     * One parameter's value, chosen a position at a time.
     *
     * <p>Depth first, so that a position is chosen against a projection that already has the ones
     * before it in it. The projection is the same one the whole search started from — what the
     * record's rules leave each of its fields — asked again with the assignment so far settled into
     * it, which is what {@link FieldDomains#of} is for.
     *
     * <p>Second, and not instead. What it costs is a reading of the record's rules per position per
     * branch, and the search in front of it answers most rows without any of that; running this one
     * first would spend it on every row to change none of them.
     */
    private static Outcome conditioned(MeasuredInput subject, int p, ConstructionPlan plan,
                                       Map<TermPath, List<FixtureTemplate>> decided,
                                       Map<TermPath, Place> settled,
                                       CandidateCheck check) {
        List<ConstructionPlan.Slot> found = plan.slots();
        // What the caller fixed goes first, so that everything chosen after it is chosen beside it.
        // A class stands for one value and a boundary is one value, and neither is worth deciding
        // after the positions whose range it settles.
        List<ConstructionPlan.Slot> positions = new ArrayList<>(
                found.stream().filter(each -> decided.containsKey(each.at())).toList());
        positions.addAll(
                found.stream().filter(each -> !decided.containsKey(each.at())).toList());
        Budget budget = new Budget();
        FixtureTemplate built = descend(subject, p, plan, positions, 0, new LinkedHashMap<>(),
                new LinkedHashMap<>(settled), decided, check, budget);
        if (built != null) {
            return new Outcome.Built(built);
        }
        return budget.cutShort
                ? new Outcome.Stopped(
                        java.util.Set.of(CompositionBudget.ASSIGNMENTS_A_SEARCH_COMPOSES), null)
                : new Outcome.Unresolved(UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED,
                        null);
    }

    /**
     * What is left of the bound on one search, and whether it ran out.
     *
     * <p>The two are one fact and are held together. Nothing left is not the same as everything
     * tried, and a search that stopped at the bound reporting that every value was refused would put
     * a combination nobody looked at beside the ones that were.
     */
    private static final class Budget {

        private int left = MAX_TUPLES;
        private boolean cutShort;

        /**
         * Whether there is room to compose one more assignment.
         *
         * <p>The only place the bound is called reached. Spending the last of it is not the same as
         * being short of it: a search whose last assignment was composed and refused has tried
         * everything it had, and marking it where the count reaches zero would report the one search
         * that finished as the one that stopped.
         */
        boolean spend() {
            if (left <= 0) {
                cutShort = true;
                return false;
            }
            left--;
            return true;
        }
    }

    /**
     * The assignment this branch leads to, or null where none of them builds.
     *
     * @param chosen  what the positions before this one took
     * @param settled the numbers among them, which is what a projection can be asked about
     * @param budget  assignments left to compose, shared down the whole search
     */
    private static FixtureTemplate descend(MeasuredInput subject, int p, ConstructionPlan plan,
                                           List<ConstructionPlan.Slot> positions, int index,
                                           Map<TermPath, FixtureTemplate> chosen,
                                           Map<TermPath, Place> settled,
                                           Map<TermPath, List<FixtureTemplate>> decided,
                                           CandidateCheck check, Budget budget) {
        if (index == positions.size()) {
            if (!budget.spend()) {
                return null;
            }
            FixtureTemplate whole = compose(plan.root(), chosen, subject.rules(), subject.inputs().policy());
            return whole != null && check.refuse(p, whole).isEmpty() ? whole : null;
        }
        ConstructionPlan.Slot position = positions.get(index);
        TermPath where = position.at();
        for (FixtureTemplate candidate : candidatesAt(subject, p, position, settled, decided)) {
            chosen.put(where, candidate);
            Place number = Counts.writtenIn(candidate.value());
            if (number != null) {
                settled.put(where, number);
            }
            FixtureTemplate found = descend(subject, p, plan, positions, index + 1, chosen, settled,
                    decided, check, budget);
            if (found != null) {
                return found;
            }
            chosen.remove(where);
            settled.remove(where);
            if (budget.cutShort) {
                return null;
            }
        }
        return null;
    }

    /** What one position can take, given what the positions before it took. */
    private static List<FixtureTemplate> candidatesAt(MeasuredInput subject, int p,
                                                      ConstructionPlan.Slot position,
                                                      Map<TermPath, Place> settled,
                                                      Map<TermPath, List<FixtureTemplate>> decided) {
        List<FixtureTemplate> fixed = decided.get(position.at());
        if (fixed != null) {
            return fixed;
        }
        TermPath at = TermPath.of(subject.parameters().get(p));
        FieldDomains left = rulesOf(subject.types().get(p), subject.rules(),
                subject.inputs().policy(), under(at, settled), subject.machines());
        RuleKey field = fieldUnder(position.at());
        return Partitions.displacedRepresentativesOf(position.type(), subject.rules(),
                subject.inputs().policy(), field == null ? null : left.at(field).bounds(),
                field == null ? null : left.heldAt(field));
    }

    /**
     * Every position a row chooses a value at, in the order they are decided.
     *
     * @param at        the paths, so that an assignment can be read back as which value went where
     * @param values    what each of those positions can take, never empty
     * @param reserves  what each holds back for the case where everything above was refused, which is
     *                  usually nothing. Kept apart rather than appended: the search is over the
     *                  product of {@code values} and is bounded, so a value added to one position
     *                  moves the assignments past it further back — and a row that was being reached
     *                  would stop being reached over a widening at a position it does not involve
     * @param missingAt the position nothing at all can be written at, where there is one — which is
     *                  not a choice to make but a reason there is no row
     * @param plan      what these positions are positions of, carried so that an assignment is
     *                  composed back into the shape the positions were taken from rather than into
     *                  one worked out a second time
     */
    private record Choices(ConstructionPlan plan, List<TermPath> at,
                           List<List<FixtureTemplate>> values,
                           List<List<FixtureTemplate>> reserves, String missingAt,
                           Set<CompositionBudget> missingUnderAFigure) {

        Choices {
            missingUnderAFigure = Set.copyOf(missingUnderAFigure);
        }

        static Choices missing(ConstructionPlan plan, String at,
                               java.util.Set<CompositionBudget> under) {
            return new Choices(plan, List.of(), List.of(), List.of(), at, under);
        }

        boolean anythingHeldBack() {
            return reserves.stream().anyMatch(each -> !each.isEmpty());
        }

        /** The same positions, each offering what it held back as well. */
        List<List<FixtureTemplate>> widened() {
            List<List<FixtureTemplate>> out = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                List<FixtureTemplate> here = new ArrayList<>(values.get(i));
                here.addAll(reserves.get(i));
                out.add(List.copyOf(here));
            }
            return List.copyOf(out);
        }
    }

    /**
     * What the positions of one row can take: the axes at the classes this assignment fixes, and every
     * other position at whatever stands for its type.
     *
     * <p>The plan's positions and not a walk of its own, which is what leaves nothing for these and
     * the composing to disagree about. A record is not a position but the fields under it are, and a
     * position the caller has already decided keeps what it was given.
     *
     * @param decided what the caller fixed: the classes of an axis, or the single value a boundary is
     *                to be reached at
     */
    private static Choices choicesOf(MeasuredInput subject, int p, ConstructionPlan plan,
                                     Map<TermPath, List<FixtureTemplate>> decided,
                                     Map<TermPath, Place> settled) {
        RuleReadingSource ruleSource = subject.rules();
        ReadingPolicy policy = subject.inputs().policy();
        TermPath at = TermPath.of(subject.parameters().get(p));
        List<TermPath> paths = new ArrayList<>(decided.keySet());
        List<List<FixtureTemplate>> values = new ArrayList<>(decided.values());
        // A position the caller fixed holds nothing back: it was given the value it is to take.
        List<List<FixtureTemplate>> reserves = new ArrayList<>(
                java.util.Collections.nCopies(paths.size(), List.<FixtureTemplate>of()));
        FieldDomains left = rulesOf(subject.types().get(p), ruleSource, policy, under(at, settled),
                subject.machines());
        for (ConstructionPlan.Slot slot : plan.slots()) {
            if (paths.contains(slot.at())) {
                continue;   // an axis decides here
            }
            RuleKey field = fieldUnder(slot.at());
            souther.compiler.numeric.NumericDomain.Bounds here =
                    field == null ? null : left.at(field).bounds();
            List<FixtureTemplate> stands = Partitions.representativesHolding(slot.type(), ruleSource,
                    policy, here, field == null ? null : left.heldAt(field));
            if (stands.isEmpty()) {
                // Nothing could be written at all: a position of a type nothing stands for. Which is
                // not the same as a value that was written and refused, and reporting it as one sends
                // the author looking for a rule relating two inputs that has nothing to do with it.
                //
                // And where this is a position the plan stopped short at, nothing standing for it is
                // that decision rather than a fact about the model: what such a position is offered
                // is whole values of a type this declined to look inside. Read off the plan's own
                // leaf and not off whether the plan was cut anywhere, so a figure reached at one
                // position is never the reason given for another having nothing.
                return Choices.missing(plan, slot.at() + ": " + Type.show(slot.type()),
                        slot.leaf() instanceof ConstructionPlan.Leaf.Beneath(var cutBy)
                                ? cutBy : java.util.Set.of());
            }
            paths.add(slot.at());
            values.add(stands);
            reserves.add(Partitions.inReserve(slot.type(), ruleSource, policy, here));
        }
        return new Choices(plan, paths, values, reserves, null, java.util.Set.of());
    }

    /**
     * A row stands at no class of this axis.
     *
     * <p>Not a class this could not choose. A position under a narrowing the row does not meet is
     * one the row is not at — the reading of a written row says the same of it, standing nowhere
     * below a case it is not — so the assignment has nothing to say there and says that.
     */
    private static final int NOT_HERE = -1;

    /**
     * What an assignment requires of the row, or the position two of its classes disagree about.
     *
     * <p>One merge and no second account. An axis the assignment is not at requires nothing: it is
     * not part of this row, so what it would have needed is not something the row has to meet.
     */
    private static Requirements.Merge requiredBy(List<Axis> axes, int[] where) {
        Requirements required = Requirements.NONE;
        for (int i = 0; i < axes.size() && i < where.length; i++) {
            if (where[i] == NOT_HERE) {
                continue;
            }
            Requirements.Merge both =
                    required.merge(axes.get(i).requiring(axes.get(i).classes().get(where[i])));
            if (!(both instanceof Requirements.Merge.Merged merged)) {
                return both;
            }
            required = merged.requirements();
        }
        return new Requirements.Merge.Merged(required);
    }

    /**
     * Where every position stands for a row about the class at {@code at}, keeping what
     * {@code from} put at the positions that can keep it.
     *
     * <p>The row is about one class, so what it requires is settled first and everything else is
     * chosen beside it. A position whose own narrowing the row does not meet stands at no class of
     * it — offered its first class regardless, every row about a class under one case of a sum
     * would ask to be another case as well, and none of them would be composed.
     *
     * @return which axis the row is about, or {@link #NOT_HERE} where it is about none
     */
    private static int[] standing(List<Axis> axes, int[] from, int[] anchors) {
        return standing(axes, from, anchors, (_, _) -> true);
    }

    /**
     * The same preference, with {@code cls} put at {@code at}.
     *
     * <p>What a row about one class starts from: the class it is for, and whatever an origin put at
     * the positions beside it.
     */
    private static int[] wanting(List<Axis> axes, int[] from, Interpretation reading) {
        int[] wanted = new int[axes.size()];
        java.util.Arrays.fill(wanted, NOT_HERE);
        if (from != null) {
            System.arraycopy(from, 0, wanted, 0, Math.min(from.length, wanted.length));
        }
        for (Map.Entry<Integer, Integer> pin : reading.pins().entrySet()) {
            wanted[pin.getKey()] = pin.getValue();
        }
        return wanted;
    }

    /** Which classes of a position something outside the requirements will have. */
    private interface Admits {

        boolean at(int axis, int cls);
    }

    /**
     * The same, among the classes {@code admits} allows — which is what a cell of the body's own
     * combinations leaves at each position.
     *
     * <p>Null where the anchors cannot be in one value, which is not an assignment that failed but a
     * combination the model does not have.
     *
     * @param anchors which positions the assignment is about, in the order they are settled. They
     *                take the class {@code from} gives them and keep it; everything else is chosen
     *                beside them. The one place a preference becomes a legal assignment: a caller
     *                counting classes off produces what it would like, and this is what says which
     *                of it a value can be
     */
    private static int[] standing(List<Axis> axes, int[] from, int[] anchors, Admits admits) {
        int[] where = new int[axes.size()];
        java.util.Arrays.fill(where, NOT_HERE);
        // What the row is about, settled before anything is chosen beside it. A class never
        // contradicts its own position: what a path requires is required at the positions above it,
        // and what a class selects is selected at the position itself.
        Requirements required = Requirements.NONE;
        for (int at : anchors) {
            if (from == null || from[at] == NOT_HERE) {
                continue;
            }
            where[at] = from[at];
            if (!(required.merge(axes.get(at).requiring(axes.get(at).classes().get(from[at])))
                    instanceof Requirements.Merge.Merged merged)) {
                return null;
            }
            required = merged.requirements();
        }
        for (int i = 0; i < axes.size(); i++) {
            if (where[i] != NOT_HERE || anchored(anchors, i)) {
                continue;
            }
            Axis axis = axes.get(i);
            Requirements soFar = required;
            // What the position itself requires, before any class of it is chosen. A position the
            // row cannot be at takes no class, whichever class would otherwise have stood here.
            if (!soFar.compatibleWith(axis.requirements())) {
                continue;
            }
            int here = i;
            int kept = from != null && i < from.length && from[i] != NOT_HERE
                    && admits.at(here, from[i])
                    && soFar.compatibleWith(axis.requiring(axis.classes().get(from[i])))
                    ? from[i]
                    : standingAt(axis, c -> admits.at(here, c) && soFar.compatibleWith(
                            axis.requiring(axis.classes().get(c))));
            where[i] = kept;
            if (kept != NOT_HERE
                    && soFar.merge(axis.requiring(axis.classes().get(kept)))
                            instanceof Requirements.Merge.Merged merged) {
                required = merged.requirements();
            }
        }
        return where;
    }

    /**
     * The rules of the record a parameter is, or nothing where it is not one.
     *
     * <p>One reading of the parameter, not one per record inside it. A clause on the outer record
     * says what is left for a position two levels down, and a reading rebuilt at the inner record
     * has never seen it.
     *
     * <p>Written once because two readers want it: what a position is offered, and why a position
     * offered less than its rules allow. Those are the two halves of one floor and they were the two
     * halves this was already asymmetric about.
     *
     * <p>Read with what the walk that read the inputs has already made of these declarations.
     * Settling a coordinate is what makes each of these a reading of its own — a probe fixes a
     * different value every time and none of the readings is the declaration's own — but what the
     * declaration's string rules come to is settled by the rules and not by what is fixed beside
     * them, so a reading here that borrowed nothing would build every one of those machines again
     * for each value probed.
     */
    private static FieldDomains rulesOf(Type type, RuleReadingSource source, ReadingPolicy policy,
                                        Map<RuleKey, Count> settled,
                                        DeclarationReadings machines) {
        // Whether the position is a record, and which record, are one answer and it is the
        // reading's. The rules are then read on the declaration the fields came off — a position
        // written under a name takes its fields from what that name wraps, and reading the rules on
        // the name instead would be asking a declaration that has no such field.
        return TypeView.of(type, source.symbols()).shape()
                        instanceof Shape.Product(TypeSymbol.AtModule declared, Map<String, Type> _)
                ? FieldDomains.of(declared, source, policy, settled, machines) : FieldDomains.NONE;
    }

    /**
     * What the parameter's own rules call {@code path}, or null where none of them can name it
     * ({@link TermPath#ruleKey}).
     *
     * <p>Null for that and for nothing else. The parameter itself is a name those rules do write —
     * the one of no steps — and the readings asked by it answer about it like any other, so folding
     * it in here would be this deciding that a value has nothing to say about itself.
     */
    private static RuleKey fieldUnder(TermPath path) {
        return path.ruleKey();
    }

    /** The settled positions of one parameter, named the way the reading of that parameter names
     * them: from the value itself, with the parameter dropped. */
    private static Map<RuleKey, Count> under(TermPath root,
                                                                    Map<TermPath, Place> settled) {
        if (settled.isEmpty()) {
            return Map.of();
        }
        Map<RuleKey, Count> out = new LinkedHashMap<>();
        settled.forEach((path, at) -> {
            if (!path.isAtOrUnder(root) || !(at instanceof Count number)) {
                return;
            }
            RuleKey field = path.ruleKeyUnder(root);
            // Where no clause of the parameter can name the position, nothing of this parameter's
            // rules is about it and there is nothing to settle. A position inside a sequence is one,
            // and so is one under a narrowing: the rules that name it are the narrowed value's.
            if (field != null && !field.isTheValueItself()) {
                out.put(field, number);
            }
        });
        return out;
    }

    /**
     * What came of trying the assignments for one parameter: its value, or why there is none.
     *
     * <p><b>Four states and not a record whose fields make them.</b> Held as a value beside a
     * reason beside a set of budgets, what may stand with what was something every reader worked
     * out again, and the arm that had to be added here is one whose fields overlap two of the
     * others. Written as arms, a reader that has one of them has everything that arm carries and
     * nothing that belongs to another.
     */
    private sealed interface Outcome {

        /** A value was composed. */
        record Built(FixtureTemplate value) implements Outcome {}

        /** None was, and no figure of this compiler's is why. */
        record Unresolved(UnresolvedCombination.Reason why, String detail) implements Outcome {}

        /**
         * A search this compiler stopped before it had tried what it held.
         *
         * <p>No word of its own, because the word such a search comes back with is the budgets' to
         * say and is read off them wherever it is wanted. Kept here as well, the two could part.
         */
        record Stopped(Set<CompositionBudget> by, Set<CompositionRepertoire> notAllOf,
                       String detail) implements Outcome {

            public Stopped {
                by = Set.copyOf(by);
                notAllOf = Set.copyOf(notAllOf);
                if (by.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a search this compiler stopped says which figure stopped it");
                }
            }

            /** One where nothing was separately known about a population this writes some of. */
            Stopped(Set<CompositionBudget> by, String detail) {
                this(by, Set.of(), detail);
            }

            /** The word a search these stopped comes back with. */
            UnresolvedCombination.Reason why() {
                return UnresolvedCombination.Reason.wordFor(by);
            }
        }

        /**
         * A search that ran through everything this compiler writes, which is not everything there
         * is.
         *
         * <p><b>Beside {@link Stopped} and never a shape of it.</b> A stopped search was holding a
         * candidate a figure had no room for, and raising the figure tries it. Nothing was refused
         * here: what this compiler writes ran out, and what reaches the rest is somebody writing
         * more of it. Held as one, a reader is sent to raise a number that changes nothing.
         *
         * <p>The word is the same word, and that is not the two being one thing. What a reader
         * concludes is alike — the point is open because this compiler did not look at everything —
         * and what closes it is not, which is why the populations travel rather than the word alone.
         */
        record Unexhausted(Set<CompositionRepertoire> notAllOf, String detail)
                implements Outcome {

            public Unexhausted {
                notAllOf = Set.copyOf(notAllOf);
                if (notAllOf.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a search that says it saw some of them says some of what");
                }
            }

            /** The word such a search comes back with, which says the point is open on this
             *  compiler and names nothing to raise. */
            UnresolvedCombination.Reason why() {
                return UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED;
            }
        }

        /**
         * No search was run: the value could not be planned.
         *
         * <p>No word, because a word here is a search's answer and none was made. The one thing
         * known is which figure left the plan unable to reach what the caller asked for, and
         * whoever turns this into an account says so in words about a search that did not happen.
         *
         * <p>Apart from {@link Limited} for the same reason it is apart at the account: the two
         * leave a reader the same work and did not happen the same way, and an outcome that held
         * both would have the history recoverable only from which fields were filled in.
         */
        record Unplanned(Set<CompositionBudget> by) implements Outcome {

            public Unplanned {
                by = Set.copyOf(by);
                if (by.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a value nothing planned says which figure left it unplanned");
                }
            }
        }

        /**
         * The search came to an answer of its own, and that answer is not the whole of what there
         * was to look at.
         *
         * <p><b>Both halves are its own information, which is what tells this from {@link
         * Stopped}.</b> A stopped search has no outcome but the stopping, so its word follows from
         * the budgets. Here the search ran to the end of what it was given and said what it found —
         * every candidate refused, or nothing composed — and separately the thing it was given was
         * short. Neither half is recoverable from the other, so no rule relates {@code why} to
         * {@code by} and none may be written: a figure that stops no search has no word, and asking
         * these budgets for one is what refuses to answer.
         *
         * <p>What a reader is owed is the ordinary word and the figure both. Told only the word, a
         * point this compiler declined to plan for is counted as one the model admits no row at;
         * told only the figure, a reader is looking for a search that never stopped.
         */
        record Limited(UnresolvedCombination.Reason why, String detail,
                       Set<CompositionBudget> by) implements Outcome {

            public Limited {
                by = Set.copyOf(by);
                if (by.isEmpty()) {
                    throw new IllegalArgumentException(
                            "an answer short of what there was says which figure made it short");
                }
            }
        }
    }

    /**
     * The assignments, nearest first, until one builds.
     *
     * <p>Breadth-first over the choices: the assignment where every position takes its first value,
     * then every assignment one step from one already tried. Deterministic, because the order the
     * positions were collected in is the order their steps are taken in, and a row is compared against
     * the last run's to see what changed.
     *
     * <p>Twice where a position held something back, and the second pass runs only after the first ran
     * out. What the positions offer ordinarily is searched whole before anything held in reserve is
     * offered at all, so a row the first pass reaches is reached at the assignment it always was: a
     * wider set of choices is a longer walk to every assignment in it, and a widening meant for one
     * position would otherwise take rows away from the rest.
     */
    private static Outcome walk(MeasuredInput subject, int p, Choices choices, CandidateCheck check) {
        Outcome tried = over(subject, p, choices.plan(), choices.at(), choices.values(), check);
        // Only where the ordinary assignments ran out. A search that stopped at the bound has not
        // tried them all, and starting a wider one in front of the ones it never reached would spend
        // what is left on assignments further from what the model says the row is about, while the
        // nearer ones stay untried.
        boolean ranOut = tried instanceof Outcome.Unresolved(UnresolvedCombination.Reason why,
                String _) && why == UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED;
        if (!ranOut || !choices.anythingHeldBack()) {
            return tried;
        }
        return over(subject, p, choices.plan(), choices.at(), choices.widened(), check);
    }

    /** One pass over one set of choices, from the assignment where every position takes its first
     * value outward. */
    private static Outcome over(MeasuredInput subject, int p, ConstructionPlan plan, List<TermPath> at,
                                List<List<FixtureTemplate>> values, CandidateCheck check) {
        int positions = at.size();
        ArrayDeque<int[]> next = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();
        int[] first = new int[positions];
        next.add(first);
        seen.add(Arrays.toString(first));

        int tried = 0;
        // Recorded where the bound is reached rather than read afterwards off what was left behind.
        // A walk that stopped and a walk that ran out are told apart by the queue today and would go
        // on being told apart by it until the day a second thing empties it.
        boolean stopped = false;
        while (!next.isEmpty()) {
            if (tried == MAX_TUPLES) {
                stopped = true;
                break;
            }
            int[] assignment = next.poll();
            tried++;
            Map<TermPath, FixtureTemplate> chosen = new LinkedHashMap<>();
            for (int i = 0; i < positions; i++) {
                chosen.put(at.get(i), values.get(i).get(assignment[i]));
            }
            FixtureTemplate built = compose(plan.root(), chosen, subject.rules(), subject.inputs().policy());
            if (built != null && check.refuse(p, built).isEmpty()) {
                return new Outcome.Built(built);
            }
            for (int i = 0; i < positions; i++) {
                if (assignment[i] + 1 >= values.get(i).size()) {
                    continue;
                }
                int[] stepped = assignment.clone();
                stepped[i]++;
                if (seen.add(Arrays.toString(stepped))) {
                    next.add(stepped);
                }
            }
        }
        // Nothing left to try is every assignment refused; a bound reached is the search having
        // stopped, and the difference is what the reader is owed. Neither carries a detail: what
        // these are about is the combination, and a detail is read as the position that is the fact
        // behind several of them.
        return stopped
                ? new Outcome.Stopped(
                        java.util.Set.of(CompositionBudget.ASSIGNMENTS_A_SEARCH_COMPOSES), null)
                : new Outcome.Unresolved(UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED,
                        null);
    }

    /**
     * The value at one position of the plan: what the assignment chose there, or a record built out
     * of the positions under it.
     *
     * <p>Null where the assignment put nothing at a position the plan asks for one at, and where a
     * name this module cannot write leaves no value to write. It used to be null for a third reason
     * — the walk that collected the choices and this one disagreeing about where the positions
     * were — and that reason has nowhere left to come from: both are this plan's positions, so a
     * disagreement between them is not something the two can be in.
     *
     * <p>What was built is handed back to the recipe that said how to build it, which puts on the
     * names the position writes its values under. The composing and the writing are one recipe
     * because they are one fact about the position: a class of {@code data DecisionN = Decision}
     * composes an {@code Approved} and the row carries {@code DecisionN(Approved { id = 1 })}.
     * Composed without that, the row carries a value of a type the parameter does not declare.
     */
    private static FixtureTemplate compose(ConstructionPlan.Node node,
                                           Map<TermPath, FixtureTemplate> chosen, RuleReadingSource ruleSource,
                                           ReadingPolicy policy) {
        return PlanComposer.compose(node, new FromTheAssignment(chosen), ruleSource, policy);
    }

    /**
     * The values of a plan as the search's assignment settled them.
     *
     * <p>Every position of the plan is one the search chose at, so every field of a record is
     * composed from what stands under it and nothing here reads a rule. What is left where a
     * position has nothing is nothing: a row missing a value the plan asks for is not a row.
     */
    private record FromTheAssignment(Map<TermPath, FixtureTemplate> chosen)
            implements PlanComposer.Values {

        @Override
        public FixtureTemplate at(ConstructionPlan.Slot slot) {
            return chosen.get(slot.at());
        }

        @Override
        public Map<String, FixtureTemplate> under(ConstructionPlan.Built built,
                                                  PlanComposer.Under under) {
            Map<String, FixtureTemplate> fields = new LinkedHashMap<>();
            for (Map.Entry<String, ConstructionPlan.Node> each : built.under().entrySet()) {
                FixtureTemplate value = under.of(each.getValue());
                if (value == null) {
                    return null;
                }
                fields.put(each.getKey(), value);
            }
            return fields;
        }
    }

    /**
     * What a row is to carry where a boundary is drawn: the values to try there, why there are none
     * where there are none, and the number the position itself is thereby settled at.
     *
     * <p>The last is a number only sometimes. A line on the content of a location settles that
     * location at it, and what the rest of the record may hold is read from the rules relating them;
     * a line on a count taken of a location settles no number inside it, and saying it did would tell
     * the rest of the row that a string's length is the number the string holds.
     *
     * <p>The orders the values were built against are not among them. Reading a value back asks the
     * same question building it asked, and the reading is what answers it both times — kept here,
     * an answer would travel from the one to the other and the two would be free to part.
     */
    record Edge(TermRealizations.Realization came, Place settledAt) {

        static Edge none(UnresolvedCombination.Reason why) {
            return new Edge(new TermRealizations.Realization.None(why), null);
        }

        List<FixtureTemplate> values() {
            return came instanceof TermRealizations.Realization.Built built
                    ? built.values() : List.of();
        }

        /** Which budgets of this compiler's stopped this edge offering more than it did, and empty
         *  where none did. The same set whether or not anything was offered: what a budget is, is
         *  what this compiler declined to do, and that does not turn on what came of the rest. */
        java.util.Set<CompositionBudget> stoppedBy() {
            return switch (came) {
                case TermRealizations.Realization.Built built -> built.heldBack();
                case TermRealizations.Realization.Stopped stopped -> stopped.by();
                case TermRealizations.Realization.Unexhausted _,
                     TermRealizations.Realization.None _ -> java.util.Set.of();
            };
        }

        /**
         * What this edge holds some of rather than all of, and empty where it holds all of what
         * there is.
         *
         * <p>Beside {@link #stoppedBy()} and never folded into it. Both say the edge is not
         * everything there is, and only one of them is a number somebody could raise — so a reader
         * handed one set would raise what it could of it and read the rest as work it had already
         * asked for.
         */
        java.util.Set<CompositionRepertoire> notAllOf() {
            return switch (came) {
                case TermRealizations.Realization.Built built -> built.notAllOf();
                case TermRealizations.Realization.Stopped stopped -> stopped.notAllOf();
                case TermRealizations.Realization.Unexhausted some -> some.notAllOf();
                case TermRealizations.Realization.None _ -> java.util.Set.of();
            };
        }

        /**
         * What this edge found, beside the word, or null where it has nothing to add.
         *
         * <p>Carried from the walk that composed nothing rather than worked out here. Two walks
         * come back with one word and what they met differs, and a reader left with the word alone
         * would be telling them apart by what the sentence does not say.
         */
        String detail() {
            return switch (came) {
                case TermRealizations.Realization.None none -> none.detail();
                case TermRealizations.Realization.Unexhausted some -> some.detail();
                case TermRealizations.Realization.Built _,
                     TermRealizations.Realization.Stopped _ -> null;
            };
        }

        /** What to report where no value was offered here at all. */
        UnresolvedCombination.Reason reason() {
            return switch (came) {
                case TermRealizations.Realization.None none -> none.why();
                case TermRealizations.Realization.Stopped stopped ->
                        UnresolvedCombination.Reason.wordFor(stopped.by());
                // The same word a figure comes back with, and for the same reason a reader has: the
                // point is open because this compiler did not look at everything, not because the
                // model answered. What differs is what would close it, which is the sentence beside
                // the word and not the word.
                case TermRealizations.Realization.Unexhausted _ ->
                        UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED;
                case TermRealizations.Realization.Built _ -> throw new IllegalStateException(
                        "an edge that offered values asked why it offered none");
            };
        }

        /**
         * What an edge that offered nothing comes to, as the outcome an account is handed.
         *
         * <p><b>Which arm it is decided here and nowhere else.</b> An edge is short of everything
         * there is in two vocabularies, and a caller choosing the arm by asking one of them whether
         * it is empty is a caller that has to be taught every vocabulary there will ever be — which
         * is how the second of them came to be dropped at the one place that asked about the first.
         * Asked of the edge, a vocabulary added is a case here rather than a silence at every
         * caller.
         *
         * <p>The figures first, because only they name something anybody could raise; what is
         * walked in part travels with them all the same, since a stop does not make it untrue.
         */
        BoundaryAttempt cameToNothing(String label,
                                      List<ReachabilityGap.Uncomposed> unrepresented) {
            if (!stoppedBy().isEmpty()) {
                return BoundaryAttempt.Stopped.at(label, detail(), stoppedBy(), notAllOf(),
                        unrepresented);
            }
            if (!notAllOf().isEmpty()) {
                return BoundaryAttempt.Unexhausted.at(label, detail(), notAllOf(), unrepresented);
            }
            return new BoundaryAttempt.Unresolved(
                    new UnresolvedCombination(List.of(label), reason(), detail()), unrepresented);
        }

    }

    /**
     * One realization as an edge of the search.
     *
     * <p>Where the root itself is settled, and where it is not. A term that is a location's content
     * is fixed at the place the line was drawn on; a term that is what an operation answered leaves
     * the root free, since the number is not what stands there — so what the search records as
     * settled is the one and not the other. The one question here the variant genuinely settles, and
     * asked of the variant.
     */
    private static Edge edgeFrom(TermRealizations.Realization made, RealizationTarget target,
                                 Place at) {
        Place settled = switch (target.term()) {
            case NumericTerm.ValueOf _ -> at;
            // What an operation answered is not what its root holds — three characters is not the
            // position standing at three, and a hundred is not what the list adding up to it holds.
            case NumericTerm.TakenOf _, NumericTerm.TakenOver _ -> null;
        };
        return new Edge(made, settled);
    }

    private Generator() {}
}
