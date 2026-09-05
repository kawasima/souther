package souther.compiler.partition;

import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Carrier;
import souther.compiler.check.RuleKey;
import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.StringPredicates;
import souther.compiler.check.DeclaredClauses;
import souther.compiler.inputs.Distinctions;
import souther.compiler.check.Shape;
import souther.compiler.check.TypeView;
import souther.compiler.check.FieldDomains;
import souther.compiler.check.NarrowedBounds;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.Quantities;
import souther.compiler.inputs.Position;
import souther.compiler.inputs.StructuralInspection;
import souther.compiler.inputs.TypeBounds;
import souther.compiler.inputs.BlockReason;
import souther.compiler.values.Allowance;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.FilingCoordinate;
import souther.compiler.inputs.RuleWithoutALine;
import souther.compiler.inputs.RulesWithNoLine;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Place;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeReachName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.Map;

/**
 * The equivalence classes a model already states, read off the types a behavior takes.
 *
 * <p>A type's cases are the classes; a threshold in an invariant is where one class ends and the next
 * begins. Nothing is invented: a position the model draws no line through has no classes, and is
 * reported as not derivable. An {@code Int} with no invariant is not silently split at zero, because
 * that would measure coverage of a rule the model never stated and report a gap for failing to test it.
 */
public final class Partitions {

    /**
     * What the model divides one behavior into: every position, every line, and what the reading
     * that produced them could not settle.
     *
     * <p><b>What it is, and nothing about who read it.</b> The reading of the declarations is what
     * this is worked out from, and it used to be carried here as well — so the geometry compared by
     * which reading had built it, and could not be an answer this compiler keeps. It is one now, and
     * the reading is handed to the few places that go on asking it further questions.
     *
     * <p><b>Every line, and not the makings of some of them.</b> A line between two positions was
     * held here as a {@link Border} while a line on one position was held as the cuts it would be
     * assembled from — so what borders a behavior has was settled by whoever assembled them, from a
     * reading of the declarations made where the assembling happened. Two readers therefore had two
     * chances to disagree about which lines exist, which is the thing this being one answer is for.
     *
     * @param unanswered the questions the rules of this behavior's inputs raise that nothing
     *                answered. Beside the axes and not inside one: the model raises them and an
     *                axis is one reader of them, so a position no axis came back for still has
     *                whatever was written about it — and an axis is re-pointed at another number as
     *                a body's rules are read, which would carry a question about one number onto
     *                another
     * @param measurements the locations of this behavior's input this phase answers for, in the
     *                order the reading found them, each holding the numbers it is measured at. A
     *                location is measured at as many numbers as the rules name of it — {@code
     *                Time.hour(slot.at)} and {@code Time.minute(slot.at)} are two measures at one
     *                location — and none is as much an answer as one. What a behavior is measured
     *                at is settled by what its types say and by what its body compares, and a count
     *                of either is not a measure of what any of that costs (see this package's
     *                documentation). Both {@link #positions()} and {@link #axes()} are read off
     *                this, so which measure answers for which location is what the model said and
     *                not a rule a reader invented
     * @param between the lines drawn between two positions, which divide neither of them
     * @param along   the lines drawn on one position, by the measure they are on. Every measure a
     *                row can be written against has an entry, and one that parts a number where
     *                the position holds no value has no line to have one for
     * @param inputIsEmpty the proof that the rules reaching this input leave no value at all, or
     *                null where no such proof was found. One fact about the behavior and not one per
     *                rule or per position: two clauses each admitting values are empty together, so
     *                neither of them is the one at fault and there is no position for it to be
     *                about. Null is "not proved empty" and never "there is a value" — what a proof
     *                of emptiness has no proof of is not the opposite claim
     */
    public record Partitioning(List<PositionMeasurements> measurements,
                               List<StandingQuestion> unanswered,
                               java.util.Set<NumericTerm> uncertain,
                               List<RuleWithoutALine> rulesWithoutALine,
                               List<souther.compiler.inputs.PositionValuesNotSeparated> notSeparated,
                               List<Border> between,
                               java.util.Map<AxisId, List<Border>> along,
                               ReachingCuts reaching,
                               MeasureClosure.OfThePartition partitionClosure,
                               MeasureClosure.OfTheBorder borderClosure,
                               souther.compiler.inputs.EmptyInput inputIsEmpty) {
        public Partitioning {
            measurements = List.copyOf(measurements);
            unanswered = List.copyOf(unanswered);
            uncertain = java.util.Set.copyOf(uncertain);
            rulesWithoutALine = List.copyOf(rulesWithoutALine);
            notSeparated = List.copyOf(notSeparated);
            between = List.copyOf(between);
            java.util.Map<AxisId, List<Border>> lines = new LinkedHashMap<>();
            along.forEach((at, drawn) -> lines.put(at, List.copyOf(drawn)));
            along = java.util.Collections.unmodifiableMap(lines);
            // Made where the reading is, never here. A closure this constructor could compute would
            // be one a caller assembling a `Partitioning` by hand could also have written, and
            // `Closed` is a conclusion about a reading rather than a shape of the lists beside it.
            if (partitionClosure == null || borderClosure == null) {
                throw new IllegalArgumentException(
                        "a partitioning with no account of what each measure's reading came to");
            }
        }

        /**
         * The locations this phase answers for, in the order the reading found them.
         *
         * <p>Read off the measurements rather than kept beside them. What a reading of a location
         * came to, where the walk stopped and what the location is left with are true of it once
         * however many numbers measure it, and a second list of them would be the same locations
         * answering to two lists that nothing holds in step.
         */
        public List<PositionAccount> positions() {
            return measurements.stream().map(PositionMeasurements::position).toList();
        }

        /**
         * The measures made of those locations, in the order the rules name the numbers.
         *
         * <p>For a reader whose question is about a number. Which location a measure is of is
         * where the measure sits, so a reader that needs the location asks {@link #measurements()}
         * and does not put the two back together from how a path is spelled.
         */
        public List<Axis> axes() {
            return measurements.stream().flatMap(each -> each.axes().stream()).toList();
        }

        /**
         * The positions no measure of them came back with anything to divide them by, each saying
         * which kind of nothing that is.
         *
         * <p>Read off the measurements, where both halves of the answer already are: whether
         * anything measures the location is what it holds, and what the rules written about it came
         * to is folded there over every number it is measured at. Kept beside them, the two would
         * be one reading of the positions and one list built from it, in step for as long as
         * whoever wrote the second remembered the first.
         *
         * <p>The structural reason outranks the rules': where the walk could not reach into what
         * the position holds, a rule naming something inside it is a second description of that
         * same stop and the first is the cause.
         */
        public List<UndividedPosition> undivided() {
            List<UndividedPosition> out = new ArrayList<>();
            for (PositionMeasurements at : measurements) {
                PendingPosition pending = PendingPosition.of(at.position(), at.hasMeasures());
                if (pending != null) {
                    out.add(pending.complete(at.inspection()));
                }
            }
            return List.copyOf(out);
        }

        /**
         * The positions this reading did not get to the rules of.
         *
         * <p>Off the same two answers the verdict above is, and neither is read from the other.
         * Both phases have spoken by the time a measurement exists — what the position's own
         * declarations answered and what a body's rules drew — and a candidate that neither of them
         * answered is what an author is waiting on.
         */
        public List<souther.compiler.inputs.PositionReadingBlocked> blocked() {
            List<souther.compiler.inputs.PositionReadingBlocked> out = new ArrayList<>();
            for (PositionMeasurements at : measurements) {
                PendingPosition pending = PendingPosition.of(at.position(), at.hasMeasures());
                souther.compiler.inputs.PositionReadingBlocked stopped =
                        pending == null ? null : pending.reportable();
                if (stopped != null && !out.contains(stopped)) {
                    out.add(stopped);
                }
            }
            return List.copyOf(out);
        }

        /**
         * The lines drawn on one position, which is what a measure of them is over.
         *
         * <p>Empty where the position has none, which is an answer: a position the rules part
         * nowhere is measured at no line. Read here rather than assembled by the reader, so that
         * what lines a behavior has is settled once by what the model says.
         */
        public List<Border> along(Axis axis) {
            return along.getOrDefault(axis.id(), List.of());
        }

        /** Whether an edge of this term is a value some row could carry.
         *
         * <p>False where a rule reaching the value the term is taken of was not read in full. Every
         * edge here is then where the rules this could read stop, and a rule it could not read can
         * refuse that value as easily as the one beyond it — so the edge is not known to be writable
         * and asking for a row at it is asking for work nobody may be able to do.
         *
         * <p>And false at a count, unless every count that measure could give is one some value has
         * (the operation's own {@code EveryAnswerItCanGiveHasASourceValue}). What the projection
         * settles is which numbers
         * the rules leave, and three is a number they leave whether or not three of the thing exist:
         * a `Set<Bool>` is capped at two, and a `List<T>` of one needs a `T` that something inhabits.
         * The domain has no term for either, so such an edge is settled by a value rather than by an
         * argument — a row at it, or one this built — which is the account an edge nothing has
         * settled already gets. Read as a proof, a floor no value reaches became a row an author was
         * told to write.
         *
         * <p>A string's length is the one that stays proven, because a string of any length is
         * written by repeating a character. Declining the proof there too would take away every
         * `String.length` edge in the corpus over collections that have no values. */
        public boolean edgeIsKnownWritable(NumericTerm term) {
            return !uncertain.contains(term) && switch (term) {
                // What a location holds is what its type holds, so an edge on it is an edge some
                // value stands at by the type having been declared.
                case NumericTerm.ValueOf _ -> true;
                // And what an operation answered is the operation's to say. Asked of the arm it is
                // taken as instead, every operation sharing an arm would carry one answer: a string
                // of any length exists and a `Set<Bool>` of three does not, and both are counts.
                case NumericTerm.TakenOf taken -> DefaultBoundOperationFacts.get()
                        .everyAnswerItCanGiveHasASourceValue(taken.operation());
                // A run has as many values as a row wrote and each of them is chosen, so whether
                // some run adds up to a given total is a question about what the elements may hold
                // and how many there may be — not one the operation answers about itself. Nothing
                // here has walked that, so no edge on such a number is known writable, and the
                // point says as much rather than promising a row.
                case NumericTerm.TakenOver _ -> false;
            };
        }

        /**
         * The measures that divide their number into classes, which is what a partition is over.
         *
         * <p>Named here so that a reader wanting them asks for them. A measure may be a boundary
         * and no partition — a bound refuses everything outside it, so there is no class on the far
         * side and what such a number gets is an edge worth a row — and every reader that answered
         * this with a filter of its own was one that could be written without it.
         */
        public List<Axis> partitionAxes() {
            return measurements.stream().flatMap(each -> each.partitionAxes().stream()).toList();
        }

        /**
         * This reading's own line where it read {@code asked}, or null where it read none.
         *
         * <p><b>The line back, and not whether there is one.</b> A caller holding a border holds
         * whatever was written beside it wherever it came from, and what the readers of a line go
         * on to ask — what it demands of a row, where a run below it stops — is answered off the
         * value they were handed. Given a yes, a caller would read its own copy; given this, it
         * reads the one this reading made.
         *
         * <p>Which is also what makes asking here worth anything. Whether a border is one of this
         * reading's is what its behavior, its numbers, the position it is on and the orders it is
         * measured on were each being compared for, one attribute at a time and one more of them
         * each time somebody found a way past. A line is a value with an identity, and the reading
         * that drew it is holding it.
         *
         * <p>Asked by {@link Border#sameReadingAs} and not by the record's own equality, which is
         * what that method exists for: the same border met in the same place owing the same things
         * is one line, whatever a level was spelled as or what order a run's ends were listed in.
         */
        public Border held(Border asked) {
            for (Border each : between) {
                if (each.sameReadingAs(asked)) {
                    return each;
                }
            }
            for (List<Border> lines : along.values()) {
                for (Border each : lines) {
                    if (each.sameReadingAs(asked)) {
                        return each;
                    }
                }
            }
            return null;
        }
    }

    /**
     * The same, reading the input's rules here.
     *
     * <p>For a caller that has no reading of them in hand. The pipeline that measures a behavior
     * reads them once and hands the same one to everything that asks, since each of these reading
     * its own is every rule of every parameter read again to arrive at the same answers.
     */
    public static Partitioning of(String behavior, InputDomain inputs, RuleReadingSource ruleSource,
                                  ReadingPolicy policy) {
        return of(behavior, inputs.reading(ruleSource), policy);
    }

    /**
     * The axes of one behavior, derived from the one reading of its input.
     *
     * <p>Nothing is read here. Which positions the input has and what can stand at each of them is
     * {@link InputDomain}'s, asked once and read by every measure; what this adds is which of them
     * an axis is drawn at, what each class is called and how a row for it is written.
     *
     * @param behavior what the axes are named after, which is the behavior the reading was made for
     */
    public static Partitioning of(String behavior, InputReading input,
                                  ReadingPolicy policy) {
        InputDomain inputs = input.domain();
        Quantities quantities = input.quantities();
        RuleReadingSource ruleSource = input.rules();
        java.util.Set<NumericTerm> uncertain = new java.util.LinkedHashSet<>();
        RulesWithNoLine.Gathered rulesWithoutALine = new RulesWithNoLine.Gathered();
        // What the reading could not hold together, asked of every position it read rather than of
        // the ones left pending. This qualifies the classes and does not stand in for them: a
        // position with classes read from a product wider than the rules admit is exactly where it
        // has something to say, and a position with none is no more affected than any other.
        List<souther.compiler.inputs.PositionValuesNotSeparated> notSeparated = new ArrayList<>();
        List<StandingQuestion> standing = new ArrayList<>();
        // The positions this phase answers for, each with what measures it, as they are made. A
        // position is what a reader of a stop or an absence is asking about, and a measure is what
        // a reader of a class or a line is; holding the second under the first is what keeps which
        // of them answers for the other from being worked out again by whoever asks.
        List<Drawn> drawn = new ArrayList<>();
        for (Position position : inputs.positions()) {
            // Not at a position made of positions. Such a one is given up in favour of what is
            // under it and carries no classes of its own, so what this qualifies is not there —
            // and the same reading is said at each of the positions that do carry them, which is
            // where an author can act on it.
            if (position.valuesNotSeparated()
                    && !(position.structure()
                            instanceof souther.compiler.inputs.StructuralInspection.Decomposed)) {
                notSeparated.add(
                        new souther.compiler.inputs.PositionValuesNotSeparated(position.path()));
            }
            axisOf(behavior, position, ruleSource, policy, drawn, uncertain, rulesWithoutALine);
            // What the rules of this position raise that nothing answered, gathered from the
            // reading that found it. Once per position and not once per axis: a question is the
            // model's, and which axis is standing beside it is this compiler's business.
            //
            // At every position the reading found, including one this drew no axis at. A position
            // given up in favour of its fields has none, and read off the axes its questions would
            // be dropped for the position having been decomposed — which is a fact about this
            // compiler deciding what a document says the model left standing. Nothing writes one
            // there today; it is gathered here so that the day something does, the question is
            // reported rather than lost.
            standing.addAll(position.unansweredQuestions());
        }
        // Every position the reading found, including the ones nothing divides: a report names what
        // it could not measure at one of those, and a body's comparison can still draw the first
        // line there. Nothing is dropped for how many there are. What an axis is worth is not known
        // here — `withThresholds` has not run, so a position a `guard` divides has no cut yet — and
        // a selection made now would be made where the least is known about what it selects
        // (see this package's documentation).
        List<PositionAccount> positions = drawn.stream().map(Drawn::at).toList();
        List<Axis> kept = drawn.stream().flatMap(each -> each.axes().stream()).toList();
        // A position undivided because a rule about it drew no line says that here, without waiting
        // for a body: a type bounded by a rule this cannot read is one whether or not any behavior
        // compares it, and so is one bounded by a rule read to the end that divides nothing. What
        // the rules came to is whether either happened, and which of the two it was — settled
        // beside each axis, as it is once a body has spoken.
        List<PositionMeasurements> settled = new ArrayList<>();
        // What every position's rules came to, the findings and the questions together. A location
        // is asked what its rules came to, and a rule nothing could classify is one of them: read
        // off the findings alone, a position whose only rule was one nothing worked out came back
        // as one no rule was written about.
        unclassifiedIn(standing, rulesWithoutALine);
        RulesWithNoLine cameToHere = rulesWithoutALine.found();
        List<RuleWithoutALine> refused = cameToHere.reported();
        for (Drawn at : drawn) {
            BodyCutInspection came = null;
            for (Axis axis : at.axes()) {
                came = BodyCutInspection.combined(came,
                        cameTo(axis.term(), axis.path(), cameToHere));
            }
            if (came == null) {
                came = cameTo(null, at.at().path(), cameToHere);
            }
            settled.add(new PositionMeasurements(at.at(), at.axes(), came));
        }
        // The lines first, because the closure is a conclusion about them: whether the reading ran
        // out is asked of what it produced beside what it found, and not of the gaps alone. Both
        // producers write into the one account — here there is only the one. A rule of a declaration
        // that relates two positions draws a line on neither of them, and every line on no position
        // is arranged with the others where they are all in hand, which this phase is not.
        LinesRead read = new LinesRead();
        java.util.Map<AxisId, List<Border>> lines = linesAlong(kept, quantities, read);
        read.returning(lines.values().stream().flatMap(List::stream).toList());
        MeasureClosure.Both closed = MeasureClosure.of(positions, standing, read);
        return new Partitioning(List.copyOf(settled), standing, uncertain,
                refused,
                List.copyOf(notSeparated),
                List.of(), lines, ReachingCuts.NONE,
                closed.partition(), closed.border(),
                // Asked once, of the one reading that holds every parameter's rules together. A
                // contradiction between two declarations is visible nowhere else, and a reader
                // asking per position or per rule would be asking what neither of them decides.
                quantities.emptiness().orElse(null));
    }

    /**
     * One position and what the declarations measured it at, before what its rules came to is
     * folded over those measures.
     *
     * <p>Not a {@link PositionMeasurements}, which is what this phase answers with and which says
     * what the rules came to. The fold wants every rule of every position in hand, and that is one
     * walk later than the walk that reads the positions — so this is what the first walk leaves,
     * and the answer is made once, whole. Written as the answer with the fold left out, the type a
     * reader is handed would carry a state only this file ever passes through.
     */
    private record Drawn(PositionAccount at, List<Axis> axes) {}

    /**
     * What the rules this phase drew no line from leave at one number of a position, or at the
     * position itself where nothing measures it.
     *
     * <p>Whether this phase left anything with no line, and not which limit it was. A limit belongs
     * to the rule it stopped, and the findings carry it there; taken as the position's, the first
     * rule of however many were stopped alike was the one a report named.
     *
     * <p>The number where a measure was made of it. A {@code String} is measured more than one
     * way: a rule about a length that nothing could read leaves the length blocked and says
     * nothing about the string's own values, and matched by path alone either measure answered for
     * both.
     *
     * <p>Where nothing measures the location, every rule filed anywhere in it is one the location
     * is left with — filed by where its coordinate sits, and not by which number the reading
     * happened to name. How exactly a rule was filed is how far its reading got, which is what the
     * finding says and not a claim about what the rule divides; a location with no measure has no
     * number for such a rule to be left at instead.
     *
     * <p>A finding the reading did not name a number for is at the position, and answers for every
     * measure on it: what number it was about is what was not read, so a measure cannot be excused
     * by it naming another.
     */
    private static BodyCutInspection cameTo(NumericTerm.FromOnePosition term,
                                            TermPath path,
                                            RulesWithNoLine found) {
        // Each bit asked of what says it, and neither read off the other's list. Every rule that
        // came to no line is reported here, whichever of the two things happened to the reading, so
        // the list alone answers neither question — the reason a finding carries is what says which
        // of them it is.
        boolean stated = found.modelStatements().stream()
                .anyMatch(one -> filedHere(one.at(), term, path));
        // And a reading stopped wherever a finding says so, or wherever a rule nothing classified
        // stands. Both are the same sentence about this compiler reaching different surfaces: a
        // reader that files no finding for such a rule leaves the question as the only thing
        // saying it, and one whose questions are the accounting's leaves the finding.
        boolean stopped = found.readingsThatStopped().stream()
                        .anyMatch(one -> filedHere(one.at(), term, path))
                || found.unclassified().stream()
                        .anyMatch(one -> filedHere(one.at(), term, path));
        if (!stated && !stopped) {
            return new BodyCutInspection.Exhausted();
        }
        // What the rules came to, asked of each of them and kept apart. A reading that stopped on
        // one rule is not answered for by another read from end to end, and a rule read from end to
        // end is the model stating something whatever became of the reading beside it.
        return new BodyCutInspection.NoLine(stopped, stated);
    }

    /**
     * The questions about rules nothing classified, put where a location's own account is read.
     *
     * <p>They are questions and they are also what one of this location's rules came to, and the
     * two readers of them want different things: one asks what holds a measure open and the other
     * asks what became of the rules written here. Neither is read off the other.
     */
    private static void unclassifiedIn(List<StandingQuestion> asked,
                                       RulesWithNoLine.Gathered into) {
        for (StandingQuestion each : asked) {
            if (each instanceof StandingQuestion.Unclassified it) {
                into.asked(it);
            }
        }
    }

    /** Whether a finding filed at {@code at} is one about this measure of this location. */
    private static boolean filedHere(FilingCoordinate at, NumericTerm.FromOnePosition term,
                                     TermPath path) {
        if (term == null) {
            return at.path().equals(path);
        }
        return switch (at) {
            case FilingCoordinate.OfTerm it -> it.term().equals(term);
            case FilingCoordinate.AtPosition it -> it.path().equals(path);
        };
    }

    /**
     * The numbers this position is measured at, which is not always the one the declarations named.
     *
     * <p>A position no rule of its own divides, whose body measures numbers of it: a bare
     * {@code List<String>} nothing bounds, under a {@code guard List.length(t.names) > 0}. The line
     * is on that number, so an axis about it is what there is to make — there is nothing else here
     * for one to be about, and dropping the evidence loses a line the body draws.
     *
     * <p><b>As many as the rules name.</b> {@code Time.hour(slot.at) >= 9 && Time.minute(slot.at)
     * >= 30} draws two lines on two numbers of one location, and both are measures. There is no
     * answering such a location with one of its numbers: picking one drops the other's lines and
     * picking neither drops both, and a location with two lines drawn on it then gets the sentence
     * a body with no comparison in it gets.
     *
     * <p>In the order the rules were read, so that what a report lists and what a search enumerates
     * are in the order an author wrote them.
     */
    private static List<NumericTerm.FromOnePosition> numbersMeasuring(
            PositionMeasurements at, List<RuleEvidence> evidence,
            List<ClassingBlocker> blocked, EvidenceAccount account) {
        TermPath path = at.position().path();
        List<RuleEvidence> here = evidence.stream()
                .filter(each -> each.at().position().equals(path)).toList();
        List<NumericTerm.FromOnePosition> numbers = new ArrayList<>();
        for (RuleEvidence each : here) {
            if (!numbers.contains(each.at())) {
                numbers.add(each.at());
            }
        }
        // And the numbers a rule was to divide and did not. A blocker is what keeps a position's
        // classes from being composed out of the rules that worked, so a number reached by one and
        // by no evidence is exactly the number it has to reach — measured off the evidence alone,
        // the one case a blocker exists for is the one it never arrives at.
        for (ClassingBlocker each : blocked) {
            if (each.at().position().equals(path) && !numbers.contains(each.at())) {
                numbers.add(each.at());
            }
        }
        // A position the declarations already divide keeps the measures they gave it. What a body
        // says about another number of such a position is not taken up as a second measure, and
        // this is where that is said: an account with no entry could not tell a policy from a loss.
        if (at.hasMeasures()) {
            List<NumericTerm.FromOnePosition> declared =
                    at.axes().stream().map(Axis::term).toList();
            here.stream().filter(each -> !declared.contains(each.at()))
                    .forEach(each -> account.disposedOf(each,
                            new EvidenceAccount.Disposition.ThePositionIsAlreadyMeasured(path)));
            return numbers.stream().filter(declared::contains).toList();
        }
        return numbers;
    }

    /**
     * One axis, with what the rules about its number divide it into.
     *
     * <p>The same walk whichever number it is. What the declarations named and what a body measures
     * a position by are two ways to arrive at a number and one thing to do with it, and a second
     * route through this would be a second answer to what a rule about a number comes to.
     */
    private static BodyCutInspection measureAt(List<Axis> out, PositionMeasurements at, Axis axis,
                                  NumericTerm.FromOnePosition term, List<RuleEvidence> evidence,
                                  List<ClassingBlocker> blocked,
                                  Quantities reading, RuleReadingSource ruleSource,
                                  ReadingPolicy policy,
                                  Allowance<NumericTerm.FromOnePosition> allowance,
                                  RulesWithNoLine rules, RulesWithNoLine.Gathered found,
                                  EvidenceAccount account) {
        String behavior = at.position().behavior();
        // What a report calls this measure, which is what its number is called under this
        // behavior. Read the same way the measure itself takes its name, so that a piece of
        // evidence said to be measured here and the measure it was measured at are one name.
        AxisId id = AxisId.of(behavior, term);
        Type type = at.position().type();
        List<RuleEvidence> mine = evidence.stream()
                .filter(each -> each.at().equals(term)).toList();
        List<Threshold> here = RuleEvidence.linesIn(mine);
        List<GuardThresholds.Guards.Singled> points = RuleEvidence.pointsIn(mine);
        // What this term's values can be, which is the type's bound already narrowed by whatever
        // the record it sits in says about it. Reading the type again here would put a threshold
        // back inside a range the record has no values in.
        NumericDomain.Bounds domain = domainOf(reading, term);
        // Both orders as the reading has them. Worked out here from the type this reader happens
        // to hold, the answer would be about wherever that type came from rather than about where
        // the reading has this term standing.
        TermOrders orders = reading.ordersOf(term);
        Carrier carrier = orders.answered();
        // What the rules about this position tell apart as sets, which is a vocabulary of its own.
        // Where they are the whole of what divides the position, the classes are what they come to
        // between them and there is nothing to put them beside.
        // What the rules leave the position divided into, asked once. The vocabulary decides the
        // classes and nothing else: what the lines cut, where the rules part the position and what
        // the account is owed are observations of their own, so everything below runs whichever way
        // this came out. A second route past them would be a second answer about all of them.
        TypeView view = TypeView.of(type, ruleSource.symbols());
        Classing.Result answered = Classing.of(term, mine, blocked, carrier,
                at.position().admits(), allowance,
                place -> standing(view, carrier, place, ruleSource));
        Classing.Classed classed = answered.classed();
        // What became of each rule, applied and not decided again. The answer is total over what
        // was handed in, so a rule with nothing said about it is not something this loop can leave
        // behind — which is what the account below refuses, and what a stage deciding the three
        // cases for itself was one forgotten branch away from doing.
        for (Classing.Disposed each : answered.forEach()) {
            switch (each.outcome()) {
                // The classes are made out of it. A rule that states a set is measured here, at the
                // axis they are on; a line and a value singled out are measured by the walk below,
                // which is where what they leave is worked out.
                case DIVIDED -> {
                    if (each.what() instanceof RuleEvidence.BySet) {
                        account.measured(each.what(), id);
                    }
                }
                // Read, and tells none of the position's values from the others. Reported, and the
                // rules beside it divide the position exactly as they would have without it.
                case TOLD_NOTHING_APART -> {
                    found.add(RuleWithoutALine.of(each.what().by().rule(),
                            each.what().by().cited(),
                            new FilingCoordinate.AtPosition(term.position()), each.why()));
                    account.disposedOf(each.what(),
                            new EvidenceAccount.Disposition.TheRuleDividedNothing(term));
                }
                // There are no classes, so this rule is among none. Whether this stage got as far
                // as qualifying it is its own business and not something a reader is told.
                case NOT_COMPOSED -> account.disposedOf(each.what(),
                        new EvidenceAccount.Disposition.TheClassesWereNotComposed(term));
                // A line, or a value singled out that no set class took up. It still cuts the
                // position, and the walk below is where that is said.
                case LEFT_TO_THE_WALK -> { }
            }
        }
        // The answer's own population, and not what is left after taking the rest away. Subtracted
        // here, the same question is answered twice — and the day the two subtract differently the
        // classes are composed out of one list and recorded as composed out of another.
        List<RuleEvidence> dividing = answered.dividing();
        // And what the walk below still reads, which is not the same list. Only the classes turn on
        // whether they composed: where they did not, the lines a rule drew are still lines and the
        // places it parts the position are still places, so the walk runs over everything that
        // reached here but the rules proved to tell nothing apart — those state nothing, and a line
        // read off one would be a line the model does not draw.
        List<RuleEvidence> taken = answered.forEach().stream()
                .filter(each -> each.outcome() == Classing.Outcome.LEFT_TO_THE_WALK
                        || each.outcome() == Classing.Outcome.DIVIDED)
                .map(Classing.Disposed::what).toList();
        // One switch and not three conditionals. What each answer means for the classes, for the
        // rules they were composed from and for whether the cuts stand is the answer's, said once;
        // read off it three times, each reading is a place to disagree about what the arm meant.
        Composition made = switch (classed) {
            // The classes are the runs of values the walk below works out, and the cuts are as they
            // have always been.
            case Classing.Classed.OnTheOrder _ -> Composition.ON_AN_ORDER;
            // A cut is a place on the order, and a class that is a set has no answer to where it
            // lies — so a position whose classes are sets has none, and a value singled out is in
            // them rather than beside them. Published as both, one distinction would be stated
            // twice in two algebras and every line would fall in no class ({@link Axis}).
            case Classing.Classed.Composed(List<PartitionClass> it) ->
                    new Composition(it, composedFrom(dividing), false, null);
            // No classes and nothing they were composed from, and the cuts stand: they are
            // observations of their own rather than a projection of the classes.
            case Classing.Classed.NotComposed(var why) ->
                    new Composition(List.of(), List.of(), true, why);
        };
        // What a reader is told about the rules the classes were not composed out of. The account
        // for them is above, with every other rule's — this is the sentence, and only where the
        // classes were the group's own to compose: a blocker says what became of its own rule and
        // has already been reported, so a reason copied from it onto the rules beside it would name
        // them for something that happened to another.
        //
        // Into the gathering the whole stage answers with, and not a list of this method's own:
        // what a reader is told is assembled once, after every position has been measured, so a
        // finding kept here would be one nobody reads.
        if (made.why() != null && blocked.isEmpty()) {
            RuleEvidence.statementsIn(answered.notComposed())
                    .forEach(each -> found.add(RuleWithoutALine.of(each.origin().rule(),
                            each.origin().cited(),
                            new FilingCoordinate.AtPosition(term.position()), made.why())));
        }
        // Said here because this is where they were asked. A blocker carried in and never reached
        // is a denominator this stage closed over the rules that worked, which the account refuses.
        account.heldShut(blocked);
        RulesWithNoLine stated = rules;
        if (here.isEmpty() && !points.isEmpty()) {
            // Nothing orders this position, so its classes are the values singled out and
            // everything else. Ranges here would ask the rows for a distinction between the two
            // sides of a value the behavior treats alike.
            taken.stream().filter(each -> !(each instanceof RuleEvidence.BySet))
                    .forEach(each -> account.measured(each, id));
            return made(out, at, behavior, term,
                    made.classesFor(axis, () -> singledClasses(points, term, type, reading,
                            domain, ruleSource)),
                    made.divides(),
                    // A cut is a place on the order the values are counted on, and a class that is
                    // a set has no answer to where it lies — so where the classes are sets there
                    // are no cuts, and the value singled out is in them rather than beside them.
                    // Published as both, the position would state one distinction twice in two
                    // algebras, and every line on it would fall in no class ({@link Axis}).
                    made.keepsCuts() ? mergedPoints(cutsOf(axis), points, carrier) : List.of(),
                    partedOf(axis), narrowedOf(axis),
                    new BodyCutInspection.Evidence(), stated);
        }
        // Filtered once, and both answers read the filtered list. A line outside what the
        // position holds divides nothing, and it is not a boundary either: leaving it in the
        // cuts while the intervals dropped it asks for a row at a value the record refuses,
        // which is the thing being fixed here happening again one field over. The end the
        // position stops short of is outside it as much as anything past it is.
        List<Threshold> reachable = new ArrayList<>();
        for (RuleEvidence each : taken) {
            if (each instanceof RuleEvidence.BySet) {
                continue;   // already answered for above, as a class the vocabularies will not hold
            }
            if (!(each instanceof RuleEvidence.Divides(Threshold line))) {
                // A value singled out beside an ordering. The model has drawn the further
                // distinction itself, so the value is one more line among the ranges and it is
                // merged with them below.
                account.measured(each, id);
                continue;
            }
            // Asked of the place the line falls at, which the position need not hold a value at.
            // Read off the value instead, a line between two of the position's values is one the
            // rules leave nothing at.
            //
            // No disposition, and that is the point: this is not a way evidence may leave this
            // stage. The reader that produces it already refuses a line falling outside what the
            // quantity it cuts ever holds and names the rule, against a type's own range and
            // against the range the record it sits in leaves. So a line that gets past that reader
            // and is dropped here is a line lost with nothing said, and the account below says so.
            if (domain != null && !admits(domain, line.parts())) {
                continue;
            }
            account.measured(each, id);
            reachable.add(line);
        }
        // Through `excluding`, so that a class list replaced by the intervals a threshold cuts
        // keeps only the exclusions it still has classes for.
        //
        // A rule read and left outside what the position holds divided nothing, and it is not
        // a rule that went unread either: what it says was understood. So the answer there is
        // that the rules were exhausted, which is what keeps `NoLine` meaning that a rule was
        // written about the position rather than everything that came to nothing.
        NumericDomain.Bounds within = domain;
        return made(out, at, behavior, term,
                made.classesFor(axis, () -> Intervals.classesOf(
                        Intervals.of(reachable, within == null ? null : within.min(),
                                within == null ? null : within.max(), carrier),
                        term, type, reading, policy, ruleSource,
                        within == null ? null : within.min(),
                        within == null ? null : within.max())),
                made.divides(),
                // Asked of the answer, as the branch above asks it. A cut is a place on the order
                // the values are counted on, and a class that is a set has no answer to where it
                // lies — so a position whose classes are sets carries none, and one that kept the
                // cuts a declaration left would be an axis every line of which falls in no class
                // ({@link Axis}). A bounded string with a rule about its prefixes is exactly that
                // pair, and it is a model somebody writes.
                made.keepsCuts()
                        ? mergedPoints(merged(cutsOf(axis), reachable, carrier), points, carrier)
                        : List.of(),
                reachable.stream()
                        .map(each -> Parting.by(each.parts(), each.origin().authoredLine()))
                        .toList(),
                narrowedOf(axis),
                reachable.isEmpty() ? null : new BodyCutInspection.Evidence(), stated);
    }

    /**
     * The measure the rules about one number came to, kept where it measures something.
     *
     * <p>A run of classes, the lines cut on the number, or where the rules part it: with none of
     * the three the rules measured the number at nothing, and what there is to say is about the
     * location rather than about a measure of it. Kept anyway, the location would be counted among
     * the measures — which is what a measure of nothing was.
     */
    private static BodyCutInspection made(List<Axis> out, PositionMeasurements at, String behavior,
                                          NumericTerm.FromOnePosition term,
                                          List<PartitionClass> classes,
                                          List<RuleEvidenceOrigin> divides, List<Cut> cuts,
                                          List<Parting> parted, NarrowedBounds narrowed,
                                          BodyCutInspection drew, RulesWithNoLine rules) {
        if (classes.isEmpty() && cuts.isEmpty() && parted.isEmpty()) {
            return cameTo(term, at.position().path(), rules);
        }
        out.add(Axis.of(behavior, term, classes, divides, cuts, parted, narrowed));
        return drew != null ? drew : cameTo(term, at.position().path(), rules);
    }

    /**
     * What a number is already divided into, or what the rules now read about it divide it into.
     *
     * <p>Refinement and not replacement. What a body draws is evidence arriving after the model's
     * own, and evidence only ever tells a position's values apart more finely — so where the model
     * already divides the number, the lines a body draws are lines among those classes and the
     * classes stay as they are. Rebuilt from the lines, a position the model divides three ways
     * would come back divided two ways, and the loss reads as the model never having stated the
     * third.
     *
     * <p>Which is a rule about the classes and not about the carrier. A position whose rules name
     * the values it holds is divided just as finely, so a {@code guard} over it replaced what the
     * model states. The two agree wherever this fires, and by construction rather than by luck: an
     * enumeration's cases are its classes, and a crossing never leaves a position whose type states
     * classes without any ({@code LocalInspection}'s {@code constructibleAt}).
     *
     * @param otherwise the classes to use where nothing divides the number yet, asked for only
     *                  there — a number that already has classes has no use for them, and working
     *                  them out would be a reading whose answer is thrown away
     */
    private static List<PartitionClass> classesOf(
            Axis axis, java.util.function.Supplier<List<PartitionClass>> otherwise) {
        return axis != null && axis.derivable() ? axis.classes() : otherwise.get();
    }

    /**
     * What one answer about a position's classes means for the measure built from it.
     *
     * <p>Three things follow from which answer it was, and they follow together: what the classes
     * are, which rules they were composed out of, and whether the cuts a line drew stand. Read off
     * the answer once each, the three are three places to decide what the arm meant — and a measure
     * that said its classes came out of rules it did not compose is one nothing can hold to it.
     *
     * @param keepsCuts whether the cuts stand. They do wherever the classes are not sets: a cut is
     *                  a place on the order and a set class has no answer to where it lies
     * @param why       what stopped the classes, or null where nothing did
     */
    private record Composition(List<PartitionClass> classes, List<RuleEvidenceOrigin> divides,
                               boolean keepsCuts, BlockReason.RuleWithoutLineReason why) {

        /** The order is the vocabulary, so the run of classes the caller works out is the answer. */
        private static final Composition ON_AN_ORDER =
                new Composition(List.of(), List.of(), true, null);

        boolean onAnOrder() {
            return this == ON_AN_ORDER;
        }

        /**
         * What the axis's classes are, which is this answer's to say and not a caller's.
         *
         * <p>Where the classes were not composed there are none, and what the declarations already
         * left is not them. A position the model divides is divided by everything said about it —
         * so a list kept because it happened to be there before is a denominator that says a
         * distinction was taken up when this stage has just said it was not, and the rows are owed
         * at classes nothing holds them to.
         *
         * <p>Everywhere else refinement is the rule ({@link #classesOf}): what a body draws is
         * evidence arriving after the model's own, and where the model already divides the number
         * the classes stay as they are.
         */
        List<PartitionClass> classesFor(Axis axis, Supplier<List<PartitionClass>> onTheOrder) {
            if (why != null) {
                return List.of();
            }
            return classesOf(axis, onAnOrder() ? onTheOrder : this::classes);
        }
    }

    /** Which rules the classes were composed out of, which is every distinction that went in. */
    private static List<RuleEvidenceOrigin> composedFrom(List<RuleEvidence> mine) {
        return mine.stream().map(RuleEvidence::by).toList();
    }

    /** The lines already cut on the number, of which there are none where nothing measured it. */
    private static List<Cut> cutsOf(Axis axis) {
        return axis == null ? List.of() : axis.cuts();
    }

    /** Likewise where the rules already part it. */
    private static List<Parting> partedOf(Axis axis) {
        return axis == null ? List.of() : axis.parted();
    }

    /** And where the rules leave its ends, which is an answer about this number and no other. */
    private static NarrowedBounds narrowedOf(Axis axis) {
        return axis == null ? NarrowedBounds.NOTHING : axis.narrowed();
    }

    /**
     * The same axes, with what the behavior's own comparisons divide them into.
     *
     * <p>This is where a numeric position stops being one undivided range. A type's invariant bounds
     * what can exist; a {@code guard} says where the behavior does something else, and both sides of
     * that line hold values a row can write. The cuts merge into one partition and the origins stay
     * apart, so reaching the line through one rule still leaves the others unmet.
     *
     * <p><b>Not the way in.</b> These take a list per kind of thing a rule can say and put them
     * together, and putting them together is not the reading's order — every range comes before
     * every equality, whatever order a body wrote them in. What the rules said arrives as one list
     * in that order ({@link #withEvidence}); these are here for a caller writing the lines itself,
     * which has no reading to be in the order of.
     */
    static Partitioning withThresholds(Partitioning base,
                                       Quantities reading,
                                       List<Threshold> thresholds,
                                       RuleReadingSource ruleSource, ReadingPolicy policy,
                                       Allowance<NumericTerm.FromOnePosition> allowance) {
        return withThresholds(base, reading, thresholds, ruleSource, policy, RulesWithNoLine.NONE,
                allowance);
    }

    /**
     * The same, told which positions a comparison names that nothing turned into a line.
     *
     * <p>A position left undivided is not thereby a position the model divides no way. What this
     * takes in is the other half of that: the body compared it, and the form the comparison is
     * written in is one no reader here takes apart. Carried rather than re-derived, because the only
     * place that knows is the reader that gave up.
     */
    static Partitioning withThresholds(Partitioning base,
                                       Quantities reading,
                                       List<Threshold> thresholds,
                                       RuleReadingSource ruleSource, ReadingPolicy policy,
                                       RulesWithNoLine rulesWithoutALine,
                                       Allowance<NumericTerm.FromOnePosition> allowance) {
        return withThresholds(base, reading, thresholds, ruleSource, policy, rulesWithoutALine,
                List.of(), allowance);
    }

    /**
     * The same, with the values a body singles out as well.
     *
     * <p>An equality is not a place to cut. What it distinguishes is one value from every other,
     * so where nothing else divides the position its classes are those two — and the second of them
     * is not a range, which is a thing a class is allowed not to be. Where an ordering comparison
     * divides the position as well, the model has drawn the further distinction itself and the value
     * is one more line among the ranges.
     */
    static Partitioning withThresholds(Partitioning base,
                                       Quantities reading,
                                       List<Threshold> thresholds,
                                       RuleReadingSource ruleSource, ReadingPolicy policy,
                                       RulesWithNoLine rulesWithoutALine,
                                       List<GuardThresholds.Guards.Singled> singled,
                                       Allowance<NumericTerm.FromOnePosition> allowance) {
        return withThresholds(base, reading, thresholds, ruleSource, policy, rulesWithoutALine,
                singled, List.of(), allowance);
    }

    /**
     * The same, with the lines a body draws between two of its positions.
     *
     * <p>Carried through rather than derived here. A line between two positions divides neither of
     * them, so nothing about it belongs to an axis — it is read where the comparison is and travels
     * beside the partition, which is what keeps a position the classes could say nothing about from
     * losing the line its body draws about it.
     */
    static Partitioning withThresholds(Partitioning base,
                                       Quantities reading,
                                       List<Threshold> thresholds,
                                       RuleReadingSource ruleSource, ReadingPolicy policy,
                                       RulesWithNoLine rulesWithoutALine,
                                       List<GuardThresholds.Guards.Singled> singled,
                                       List<LineDrawn> between,
                                       Allowance<NumericTerm.FromOnePosition> allowance) {
        return withThresholds(base, reading, thresholds, ruleSource, policy, rulesWithoutALine,
                singled, between, ReachingCuts.NONE, allowance);
    }

    /**
     * The same, told what a row has already had to satisfy by the time it reaches each comparison.
     *
     * <p>Carried and not re-derived, which is the whole discipline {@link ReachingCuts} is written
     * around: what a region may assume is what the walk of the body actually took in, and a reading
     * that recovered it from where a comparison sits would be free to name a condition nothing here
     * could read.
     */
    static Partitioning withThresholds(Partitioning base,
                                       Quantities reading,
                                       List<Threshold> thresholds,
                                       RuleReadingSource ruleSource, ReadingPolicy policy,
                                       RulesWithNoLine rulesWithoutALine,
                                       List<GuardThresholds.Guards.Singled> singled,
                                       List<LineDrawn> between,
                                       ReachingCuts reaching,
                                       Allowance<NumericTerm.FromOnePosition> allowance) {
        List<RuleEvidence> evidence = new ArrayList<>();
        thresholds.forEach(each -> evidence.add(new RuleEvidence.Divides(each)));
        singled.forEach(each -> evidence.add(new RuleEvidence.Singles(each)));
        // Nothing here tells a set of the position's values from the rest, so nothing composes one
        // and the allowance for composing them is never asked for a machine. It is still handed in
        // rather than made: an allowance is what a compilation's grant becomes, and one made here
        // would be a meter at every position that nobody granted.
        return withEvidence(base, reading, evidence, List.of(), allowance, ruleSource, policy,
                rulesWithoutALine, between, reaching);
    }

    /**
     * The same, given what the rules said as the reading of them met it.
     *
     * <p>One list and not one per kind of thing a rule can say. What this stage does with a piece of
     * evidence — divide a position by it, leave it outside what the position holds, or not take it
     * up at all — is the same question whichever kind it is, and the account of what became of each
     * is over all of them ({@link EvidenceAccount}). Handed one list per kind, both the numbers a
     * position is measured at and that account have to put them back together, and a position
     * measured at one kind alone is measured at neither.
     */
    public static Partitioning withEvidence(Partitioning base,
                                            Quantities reading,
                                            List<RuleEvidence> evidence,
                                            List<ClassingBlocker> blocked,
                                            Allowance<NumericTerm.FromOnePosition> allowance,
                                            RuleReadingSource ruleSource, ReadingPolicy policy,
                                            RulesWithNoLine rulesWithoutALine,
                                            List<LineDrawn> between,
                                            ReachingCuts reaching) {
        // Both producers of one kind of evidence. What a body compared and what a type's own rules
        // bound are read by different readers and answer the same question, so a position either of
        // them wrote about and neither could turn into a line is named once, whichever wrote it.
        RulesWithNoLine.Gathered found = new RulesWithNoLine.Gathered();
        found.addAll(base.rulesWithoutALine());
        found.addAll(rulesWithoutALine);
        unclassifiedIn(base.unanswered(), found);
        RulesWithNoLine gathered = found.found();
        // And what these readers could not classify, beside the questions the base reading already
        // had. One list of what holds a measure open, for the one reader of it.
        //
        // Taken from the same fold as the findings, because a rule both readers met is one rule
        // however many of them say so. Concatenated instead, the reader of the report was told
        // twice about one rule nothing worked out.
        List<StandingQuestion> asked = new ArrayList<>(base.unanswered().stream()
                .filter(each -> !(each instanceof StandingQuestion.Unclassified)).toList());
        asked.addAll(gathered.unclassified());
        List<PositionMeasurements> measurements = new ArrayList<>();
        EvidenceAccount account = new EvidenceAccount(evidence, blocked);
        // A position at a time, so that what a body's rules add is added where the position already
        // is. Walked as one run of measures, which position each of them came back for is a
        // question this would have to answer again once the rules name a second number of one.
        for (PositionMeasurements at : base.measurements()) {
            List<Axis> here = new ArrayList<>();
            // What the rules came to about the location, folded as its numbers are measured. One
            // sentence for the location: a number the rules divide and a number they say nothing
            // about are both measures of it, and which of the two answers a report is owed is
            // taken together rather than chosen between by the order this happens to walk them in.
            BodyCutInspection came = null;
            List<NumericTerm.FromOnePosition> numbers =
                    numbersMeasuring(at, evidence, blocked, account);
            for (NumericTerm.FromOnePosition term : numbers) {
                // The measure of this number where the declarations made one, and nothing where a
                // body's rules are the first to name it. What such a measure starts from is what
                // the location is, which is where this reads it from.
                Axis measured = at.axes().stream()
                        .filter(each -> each.term().equals(term)).findFirst().orElse(null);
                came = BodyCutInspection.combined(came,
                        measureAt(here, at, measured, term, evidence,
                                blocked.stream()
                                        .filter(each -> each.at().equals(term)).toList(),
                                reading, ruleSource, policy, allowance, gathered, found, account));
            }
            // The measures nothing new was said about, kept as they are, and what they were left
            // with folded in beside the rest.
            for (Axis axis : at.axes()) {
                if (numbers.contains(axis.term())) {
                    continue;
                }
                here.add(axis);
                came = BodyCutInspection.combined(came,
                        cameTo(axis.term(), axis.path(), gathered));
            }
            if (came == null) {
                came = cameTo(null, at.position().path(), gathered);
            }
            measurements.add(at.measuredAt(here, came));
        }
        List<Axis> out = measurements.stream().flatMap(each -> each.axes().stream()).toList();
        account.everyPieceWasDisposedOf(out);
        // Both producers into the one account. A line that divides a position leaves its border on
        // the position and a line between two leaves its border beside them; they are the same
        // reading, and an accounting over one of them says nothing about the other.
        LinesRead read = new LinesRead();
        java.util.Map<AxisId, List<Border>> lines = linesAlong(out, reading, read);
        List<Border> across = Border.allOf(between, partedByQuantity(out), read);
        read.returning(lines.values().stream().flatMap(List::stream).toList());
        read.returning(across);
        MeasureClosure.Both closed = MeasureClosure.of(base.positions(), asked, read);
        return new Partitioning(measurements, asked, base.uncertain(),
                // Read after every position was measured, so that what a position's classes would
                // not compose is in it. Taken before, the list is what the producers handed over and
                // nothing this stage found out.
                List.copyOf(found.found().reported()),
                // Carried across: what a reading could not hold together is a fact about the
                // declarations, and a body drawing a line on a position does not make the product
                // it was read from the relation the rules admit.
                // Turned into borders here and nowhere else, so that every rule about one
                // quantity is arranged together however the rules were written — a body's
                // condition and a clause cut one form as readily as two conditions do.
                // Every rule about one quantity arranged together, whichever producer its border
                // came from. A line that divides a position leaves its division on the axis and,
                // where the position has no value beside it, its border over here — and the two
                // sides of that border are runs of what all of them leave.
                base.notSeparated(), across,
                lines,
                reaching, closed.partition(), closed.border(),
                // Carried across. Whether the rules leave the input a value is the declarations'
                // answer, and a body drawing lines on what they left does not change it.
                base.inputIsEmpty());
    }

    /**
     * The lines each position has, assembled where the reading of the declarations is.
     *
     * <p>Here and not at whoever measures them. Which lines a position has is what the model says,
     * and a reader assembling them needs what the rules leave the term — so assembled at the reader,
     * it takes a reading of its own and the set of lines becomes that reader's answer rather than
     * the model's.
     *
     * <p>Only the positions there is anything to measure at. A position with no classes and no cuts
     * has no line to draw, and an entry saying so would be a list of nothings per behavior.
     */
    private static java.util.Map<AxisId, List<Border>> linesAlong(
            List<Axis> axes, Quantities reading, LinesRead read) {
        Map<AxisId, List<Border>> out = new LinkedHashMap<>();
        for (Axis axis : axes) {
            if (axis.asksForARow()) {
                out.put(axis.id(),
                        bordersOf(axis, reading, reading.runsBetween(axis.term()), read));
            }
        }
        return out;
    }

    /**
     * The classes a position divided only by equalities has: each value singled out, and the rest.
     *
     * <p>The last of those is not an interval and is not asked to be. What a class needs is a way to
     * say whether a value is in it and a value that stands for it, and a complement has both — the
     * shape a class has been limited to is what this is here to stop being the limit.
     */
    private static List<PartitionClass> singledClasses(List<GuardThresholds.Guards.Singled> points,
                                                       NumericTerm.FromOnePosition term, Type type,
                                                       Quantities reading,
                                                       NumericDomain.Bounds within, RuleReadingSource ruleSource) {
        // Asked here rather than handed in beside the term. A term and a pair of orders are two
        // arguments, and two arguments can be about two terms; the reading is one argument that
        // answers about whichever term it is asked.
        TermOrders orders = reading.ordersOf(term);
        Carrier carrier = orders.answered();
        List<Place> values = new ArrayList<>();
        for (GuardThresholds.Guards.Singled each : points) {
            if (values.stream().noneMatch(had -> had.sameAs(each.value()))) {
                values.add(each.value());
            }
        }
        // The position, read once: every class below writes its value under the same names.
        TypeView view = TypeView.of(type, ruleSource.symbols());
        List<PartitionClass> classes = new ArrayList<>();
        for (Place value : values) {
            String written = carrier.written(value);
            classes.add(classAt(term + "/= " + written, "= " + written,
                    holding(orders, new Recognition.CountIs.At(value)),
                    standing(view, carrier, value, ruleSource)));
        }
        Place other = carrier.somethingOtherThan(values, within);
        String label = "/= " + String.join(", ",
                values.stream().map(carrier::written).toList());
        Recognition away = holding(orders,
                new Recognition.CountIs.AwayFrom(values));
        classes.add(other == null
                ? PartitionClass.ungeneratable(term + "/" + label, label, away,
                        "nothing here composed a value of this position other than the ones"
                                + " singled out")
                : classAt(term + "/" + label, label, away,
                        standing(view, carrier, other, ruleSource)));
        // Classes of the number the values were singled out of, said where that is known.
        return classes.stream().map(each -> each.ofTheNumber(term)).toList();
    }

    /** A class over the one value that stands for it, or one nothing produces where there is no
     *  such value — which is what a position wearing a name this module cannot write leaves. */
    private static PartitionClass classAt(String id, String label, Recognition is,
                                          FixtureTemplate standing) {
        return standing == null
                ? PartitionClass.ungeneratable(id, label, is,
                        "nothing here can write a value of this position")
                : PartitionClass.of(id, label, is, RepresentativeSource.of(standing));
    }

    /** A count written at a position, wearing every name that position declares — which the reading
     *  of the position says, and nothing here asks again. */
    private static FixtureTemplate standing(TypeView view, Carrier carrier, Place at,
            RuleReadingSource ruleSource) {
        return WornNames.under(view.wrappers(),
                FixtureTemplate.on(carrier, at, ruleSource.symbols().scope()::reach), ruleSource);
    }

    /** A class that reads the count of the number {@code on} is of out of a row, and answers about
     *  it. The number comes from the orders rather than beside them: a class of one number built on
     *  another's order is what the pair naming its own number is here to stop. */
    private static Recognition holding(TermOrders on, Recognition.CountIs is) {
        return new Recognition.OfACount(on.term().atOnePosition(), on, is);
    }

    /** The cuts a position has, with the values a body singled out added as lines of their own. */
    private static List<Cut> mergedPoints(List<Cut> had, List<GuardThresholds.Guards.Singled> points,
                                          Carrier carrier) {
        Map<String, Cut> byValue = new LinkedHashMap<>();
        for (Cut cut : had) {
            byValue.put(cut.key(), cut);
        }
        for (GuardThresholds.Guards.Singled each : points) {
            Cut cut = Cut.at(carrier, each.value(), each.origin());
            byValue.merge(cut.key(), cut, (there, _) -> there.and(each.origin()));
        }
        return List.copyOf(byValue.values());
    }

    /**
     * What the rules leave one term, including a term an axis only took on here.
     *
     * <p>Which numbers a position is measured at is not settled by the reading of the declarations
     * alone: a bare list nothing bounds becomes an axis about its length where a body measures it,
     * and what such a term guarantees of its own values is what bounds it. Asked of the reading
     * rather than kept per term beside it, which is where the two came to disagree.
     */
    private static NumericDomain.Bounds domainOf(Quantities reading,
                                                 NumericTerm term) {
        return reading.runsBetween(term);
    }

    /**
     * Where the rules part each of this behavior's quantities, by the quantity they are on.
     *
     * <p>Read off the axes, which is where a division of a position is recorded whether or not the
     * position has a value at it. A line over a form of several positions divides none of them and
     * is on a quantity of its own, which no axis names — those arrive with the lines themselves.
     */
    private static Map<String, List<Parting>> partedByQuantity(List<Axis> axes) {
        Map<String, List<Parting>> out = new LinkedHashMap<>();
        for (Axis axis : axes) {
            if (axis.parted().isEmpty()) {
                continue;
            }
            out.computeIfAbsent(QuantityKey.of(LinearForm.atom(axis.term())).key(),
                    _ -> new ArrayList<>()).addAll(axis.parted());
        }
        return out;
    }

    /** Whether the rules leave the quantity anything at the place a line falls. */
    private static boolean admits(NumericDomain.Bounds within, Seam parts) {
        return (within.min() == null || parts.at().compare(within.min().at()) <= 0)
                && (within.max() == null || parts.at().compare(within.max().at()) >= 0);
    }

    private static List<Cut> merged(List<Cut> had, List<Threshold> thresholds, Carrier carrier) {
        Map<String, Cut> byValue = new LinkedHashMap<>();
        for (Cut cut : had) {
            byValue.put(cut.key(), cut);
        }
        for (Threshold each : thresholds) {
            // A line the position has no value at is not a cut of it. It divides the position all
            // the same — the classes either side are what the model distinguishes — and there is no
            // value for a row to be written at, so there is no border here either. The rule's own
            // border is on the quantity it wrote, which can name the line.
            if (each.value() == null) {
                continue;
            }
            Cut cut = Cut.at(carrier, each.value(), each.origin());
            byValue.merge(cut.key(), cut, (there, _) -> there.and(each.origin()));
        }
        return List.copyOf(byValue.values());
    }

    /**
     * The borders a position's rules drew, one per rule that drew a cut.
     *
     * <p>One entry per line and not per point. What each of them owes a row at, in each of the four
     * roles the technique names, is the border's own answer — including the roles it owes nothing in
     * and why. Built as a list of points instead, a role nobody was owed a row in and a role this
     * reader forgot to build were the same thing.
     *
     * <p>Keeping a rule per cut means the same value can be owed three times. That is the point: an
     * invariant and two guards that name one value are three rules, and a row that meets one of them
     * has met one.
     *
     * <p>Not visible outside this package, so that what lines a behavior has is asked of the
     * partitioning ({@link Partitioning#along}) and never assembled again. Assembling them takes
     * what the rules leave the term, which is a reading of the declarations — so a caller that could
     * assemble them would be deciding, from a reading of its own, which lines exist to be measured.
     */
    static List<Border> bordersOf(Axis axis, Quantities reading, NumericDomain.Bounds within,
                                  LinesRead read) {
        // Both orders of the number this axis measures, as the reading has them. A pair put
        // together here out of the position's order and the carrier a cut was drawn on is a pair
        // whose two halves came from two places, and the day they part is the day a row is decoded
        // on a count the value is not written in.
        TermOrders orders = reading.ordersOf(axis.term());
        List<Border> out = new ArrayList<>();
        // Every place the rules part this position's values, collected before any border is built.
        // What each border owes away from its line is a run of the arrangement they make together,
        // and a border built without them reads its two sides to the end of the order — so a row in
        // the partition after next answered for a point inside the one this border bounds.
        // Every place the rules part this position's values: the ones its cuts stand at, and the
        // ones no cut stands at because the position holds no value there. A border built from the
        // cuts alone read its two sides past exactly the lines that were left out.
        List<Parting> parted = new ArrayList<>(axis.parted());
        for (Cut cut : axis.cuts()) {
            BoundaryTarget where = BoundaryTarget.at(
                    new BorderQuantity.OfACoordinate(axis.id().behavior(), axis.term(), orders),
                    new Level.OnACarrier(cut.carrier(), cut.at()));
            for (LineOrigin origin : cut.origins()) {
                // Every rule that drew a line here, as it was read. Which of them fall in one place
                // is the arrangement's answer, and telling them apart here kept the first and lost
                // the rest — so a run bounded by two rules knew about one of them.
                parted.addAll(Border.partedBy(where, origin));
            }
        }
        for (Cut cut : axis.cuts()) {
            // The level is on the cut's carrier, which is the one the rule was read on. What the
            // quantity is measured on is the reading's answer and not read off the line: a line
            // drawn on a count taken of a position would otherwise be written back as a value of
            // the position.
            BoundaryTarget target = BoundaryTarget.at(
                    new BorderQuantity.OfACoordinate(axis.id().behavior(), axis.term(), orders),
                    new Level.OnACarrier(cut.carrier(), cut.at()));
            for (LineOrigin origin : cut.origins()) {
                // One cut, one border. Whether the quantity reaches the line is settled where the
                // cut was made — a bound's line is an end of what the bound leaves — so there is
                // nothing to test here and nothing to drop. It used to answer null and be dropped
                // without a word, which is how a strict bound on a carrier with no step left the
                // measure saying the behavior's rules draw no line anywhere (issue #1079).
                //
                // The line is written down as it is met and the border where it lands, so that the
                // day something is written between the two the reading is held to having lost one.
                read.found(target, origin);
                out.add(read.drew(Border.at(target, origin, within, parted, axis.narrowed())));
            }
        }
        return List.copyOf(out);
    }

    /**
     * One position, turned into an axis or given up in favour of what is under it.
     *
     * <p>The local answer first, whole: the classes the position's declarations state and the lines
     * its rules draw come back as one value, so what "local evidence ran out" means is an answer
     * rather than two empty lists to be noticed. Only where it ran out does what the position is
     * made of decide anything — a position its own declarations answered for keeps its axis,
     * whatever is under it, and that precedence is the arm this is written in rather than the order
     * of two {@code if}s.
     *
     * <p>Of the structural answers only {@code Children} takes the position away: the fields are
     * positions of their own and were read as such, and the classes belong to them. A leaf and a
     * block both leave the position standing, pending what a body's rules say later.
     *
     * <p><b>A position with local evidence and children is unreachable today, and is refused rather
     * than resolved.</b> Only a product has children, and a product states no distinction — its type
     * declares no division — and carries no cut, having no order for a rule to name a value on. A
     * language that grew one would have this reader keeping the parent's axis while the reading kept
     * its fields, and two readers of one input disagreeing about which positions there are is the
     * thing this arrangement exists to stop.
     */
    private static void axisOf(String behavior, Position position, RuleReadingSource ruleSource,
                               ReadingPolicy policy,
                               List<Drawn> drawn,
                               java.util.Set<NumericTerm> uncertain,
                               RulesWithNoLine.Gathered rulesWithoutALine) {
        rulesWithoutALine.addAll(position.rulesWithoutALine());
        NumericTerm.FromOnePosition term = position.term();
        switch (LocalInspection.of(position, ruleSource, policy)) {
            case LocalPartition.Divided divided -> {
                if (position.structure() instanceof StructuralInspection.Decomposed) {
                    throw new IllegalStateException(
                            "`" + position.path() + "` both divides and is made of positions; the"
                                    + " reading of an input and the axes drawn from it disagree"
                                    + " about which positions there are");
                }
                if (divided.cuts() instanceof CutEvidence.Present cut && cut.uncertain()) {
                    uncertain.add(term);
                }
                // No continuation, because something answered for the position and a fallback is
                // what a position with no answer is left with. What the reading came to is carried
                // all the same: a `Map` a rule about its size divides is one nothing was read into,
                // and taking that off the axis with the fallback is how the stop went unreported
                // (issue #1084).
                PositionAccount at = PositionAccount.of(behavior, position, null);
                drawn.add(new Drawn(at,
                        List.of(Axis.of(behavior, term, divided.classes(), List.of(),
                                divided.cuts().cuts(), List.of(), position.narrowedEnds()))));
            }
            // Nothing local divides the position, which is what licenses asking what it is made of.
            // What its rules still raise is carried rather than acted on here: a position made of
            // positions is given up in favour of what is under it either way, and a question about
            // the whole value says nothing about which of its fields it would have divided.
            case LocalPartition.Open _ -> {
                switch (position.structure()) {
                    // The one answer that takes the position away: what is under it is what the
                    // classes belong to, and those positions were read on their own.
                    case StructuralInspection.Decomposed _ -> { }
                    // A leaf and a block are both positions still to be answered for, and each
                    // carries what the walk found and what its rules still raise, which is what
                    // keeps the position from completing as one the model divides no way.
                    case StructuralInspection.Retained retained -> {
                        // The position and no measure of it. Nothing local divided the number the
                        // declarations name, and a measure with nothing in it would be a location
                        // counted among the measures — what is still to be answered for here is
                        // the position's to carry.
                        drawn.add(new Drawn(
                                PositionAccount.of(behavior, position, retained.continuation()),
                                List.of()));
                    }
                }
            }
        }
    }



    // --- small helpers ----------------------------------------------------------------------------

    /** Values that could stand for a type wherever nothing else has been said about the position — the
     * inner value of a newtype, a field no axis divides. A record is not one of these: its fields are
     * composed, which is the generator's work and not a value this can hand over. */
    static List<FixtureTemplate> representativesOf(Type type, RuleReadingSource ruleSource,
                                                   ReadingPolicy policy) {
        return representativesOf(type, ruleSource, policy, null);
    }

    /**
     * The same, for a position the record it sits in has already narrowed.
     *
     * <p>{@code within} is what is left of the position once the rest of the assignment is settled:
     * an {@code endsAt} beside a {@code startsAt} of 1439 can only be 1440, and the value this offers
     * has to come from there rather than from the bottom of the type's own range.
     */
    static List<FixtureTemplate> representativesOf(Type type, RuleReadingSource ruleSource, ReadingPolicy policy,
                                                   NumericDomain.Bounds within) {
        return representativesOf(type, ruleSource, policy, within, java.util.Set.of());
    }

    /**
     * The same, with the newtypes this is already inside the value of.
     *
     * <p>Carried because what stands for a collection is built from what stands for its element, which
     * is this question again. A name met while its own value is being built is a type written in terms
     * of itself and is given up on — the names and not a count of them, since how many names a value
     * wears on the way down is not what has to be stopped.
     */
    static List<FixtureTemplate> representativesOf(Type type, RuleReadingSource ruleSource, ReadingPolicy policy,
                                                   NumericDomain.Bounds within,
                                                   java.util.Set<TypeSymbol> expanding) {
        return type == null ? List.of()
                : representativesOf(TypeView.of(type, ruleSource.symbols()), ruleSource, policy,
                        within, expanding);
    }

    /**
     * The same, of a position that has already been read.
     *
     * <p>One reading, three answers taken from it. What the position is, with every name off, is what
     * the values are made of; the rules of every name it wears are the rules its value is under; and
     * the names themselves are what the value is written with. Held as one walk, how far to look to
     * know what a rule is about and how far to descend to write the value were the same number — and
     * one name is right for the second and wrong for the first, so a rule on a name that wraps a name
     * was never reached.
     *
     * <p>Rules first, then what the shape stands for. Each rule is a reason to offer another value
     * and never to withdraw one already offered: which of them the whole of the rules admits is the
     * decoder's answer, so a position carrying a format and a floor gets a value from each and the
     * order they were declared in does not decide whether one builds.
     */
    static List<FixtureTemplate> representativesOf(TypeView view, RuleReadingSource ruleSource,
                                                   ReadingPolicy policy,
                                                   NumericDomain.Bounds within,
                                                   java.util.Set<TypeSymbol> expanding) {
        // Already inside the value of one of the names this wears, so the type is written in terms of
        // itself and there is nothing to hand back. Which is the answer and not a limit: no value of
        // such a type exists.
        java.util.Set<TypeSymbol> inside = new LinkedHashSet<>(expanding);
        for (TypeSymbol wears : view.wrappers()) {
            if (!inside.add(wears)) {
                return List.of();
            }
        }
        // A name this module cannot write leaves no value to write. Asked of the position and not of
        // each value: whether the names can be spelled is a fact about where the value is going, and
        // a value at a position wearing one of them is no value here whatever it is.
        if (!(WornNames.of(view.wrappers(), ruleSource) instanceof WornNames.Spelled spelled)) {
            return List.of();
        }
        // What the rules ask for, and then what the position is where nothing was written about it.
        List<FixtureTemplate> bare = new ArrayList<>();
        bare.addAll(whereTheRulesLeaveTheValue(view, ruleSource, within));
        bare.addAll(whatAFormatAsksFor(view, ruleSource));
        // What the rules say the value holds, before the value that would hold nothing.
        bare.addAll(Witnesses.holding(view.shape(), leastHeld(view, ruleSource),
                ruleSource, policy, inside));
        List<FixtureTemplate> ofTheShape =
                whatTheShapeStandsFor(view.shape(), ruleSource, policy, within, inside);
        bare.addAll(ofTheShape);

        List<FixtureTemplate> candidates = new ArrayList<>();
        for (FixtureTemplate each : bare) {
            candidates.add(RepresentativeSource.under(spelled.names(), each));
        }
        // Where the shape stands for no value of its own, what stands at the position is one of the
        // kinds its type divides into, and the classes are what name them. Asked only there, because
        // a class of a position says what stands at the position under it: asked of a shape that has
        // a value of its own, the answer is that value again, arrived at by building it.
        if (ofTheShape.isEmpty()) {
            candidates.addAll(dividedInto(view, ruleSource, policy, inside));
        }
        Map<String, FixtureTemplate> once = new LinkedHashMap<>();
        for (FixtureTemplate each : candidates) {
            once.putIfAbsent(each.text(), each);
        }
        return List.copyOf(once.values());
    }

    /**
     * What a position divides into, or nothing where its type states no division.
     *
     * <p>The first class anything can be produced for and not all of them: what is wanted here is a
     * value of the position, and the classes are how a position that has more than one kind of value
     * says which kinds there are. Their recipes carry the names the position wears already
     * ({@link RepresentativeSource}), so nothing is put on them here.
     *
     * <p>Where there were classes and none produced a value, that is the answer. Each said nothing
     * can be produced for it and why, and arriving at a value another way is this deciding the
     * classes were wrong about themselves — the answer they carry is the one an author is shown.
     */
    private static List<FixtureTemplate> dividedInto(TypeView view, RuleReadingSource ruleSource,
                                                     ReadingPolicy policy,
                                                     java.util.Set<TypeSymbol> expanding) {
        for (PartitionClass each : PartitionClasses.of(
                Distinctions.ofType(view, ruleSource.symbols()), view, ruleSource, policy,
                expanding)) {
            List<FixtureTemplate> stands =
                    standingFor(each.representatives(), ruleSource, policy, expanding);
            if (!stands.isEmpty()) {
                return stands;
            }
        }
        return List.of();
    }

    /**
     * The value at the edge the rules leave, or nothing where they leave no edge.
     *
     * <p>Read of the position and not of one of its names: a value wearing two names is bounded by
     * the rules written on either, and both are what its reading came to. Nothing where no rule and
     * no narrowing said anything — the value the position stands for is what answers there, and a
     * number named here as well would be the same number said twice.
     */
    private static List<FixtureTemplate> whereTheRulesLeaveTheValue(TypeView view,
                                                                    RuleReadingSource ruleSource,
                                                                    NumericDomain.Bounds within) {
        DeclaredBounds.Bounds own = DeclaredBounds.of(view, ruleSource);
        if (own == null) {
            return List.of();   // nothing here reads a number of this position at all
        }
        NumericDomain.Bounds bounds = TypeBounds.admissible(own, within);
        Place held = bounds == null || bounds.saysNothing() ? null : inside(bounds, own.carrier());
        FixtureTemplate at = held == null ? null
                : FixtureTemplate.on(own.carrier(), held, ruleSource.symbols().scope()::reach);
        return at == null ? List.of() : List.of(at);
    }

    /**
     * A value each rule about the characters of a string admits, innermost name first.
     *
     * <p>Read in the representation the analysis reads, which is where a library predicate is still
     * the operation it was written as. In the settled form it is the body it expands to, and the
     * reading below has no word for that.
     *
     * <p>Innermost first, which is an order over the proposals and not over the rules: every name's
     * rules govern the value, whichever end they are read from. What the order decides is which
     * proposal a bounded search reaches before it stops, and the value a name wraps is the one its
     * own rules were written closest to.
     */
    private static List<FixtureTemplate> whatAFormatAsksFor(TypeView view,
                                                            RuleReadingSource ruleSource) {
        if (!(view.shape() instanceof Shape.Scalar scalar) || scalar.prim() != Type.Prim.STRING) {
            return List.of();
        }
        List<DeclaredClauses.OnAName> written = DeclaredClauses.of(view.wrappers(), ruleSource);
        List<FixtureTemplate> out = new ArrayList<>();
        for (int name = written.size() - 1; name >= 0; name--) {
            for (DeclaredClauses.Conjunct each : written.get(name).conjuncts()) {
                // Asked of what the predicate means and not of what the decoder is told. The two are
                // different questions: a constraint is what a generated class declares to the
                // runtime, which is a format and nothing else, and this is which strings the rule
                // admits — asked through the constraint, every predicate the decoder has no word for
                // proposed no value, and a position an author had written a rule for was offered
                // `"x"` and refused.
                //
                // Told which strings only where the reading came to them. Why it did not is the
                // reading's to keep and nothing here has a use for it: a rule this could not read
                // proposes no value, the same as one whose strings nobody can paste.
                StringPredicates.Reading admits =
                        StringPredicates.statedByWritten(each.expr(), ruleSource.symbols());
                String text = admits instanceof StringPredicates.Reading.Accepting it
                        ? writtenFor(it.accepts()) : null;
                if (text != null) {
                    out.add(FixtureTemplate.string(text));
                }
            }
        }
        return out;
    }

    /**
     * The value a shape stands for where nothing has been written about the position.
     *
     * <p>Exhaustive, with no {@code default}: what stands at a position is a question about each
     * kind of position, and a shape added later is one this has to be told about rather than one
     * that falls to whichever arm it was not named in.
     */
    private static List<FixtureTemplate> whatTheShapeStandsFor(Shape shape,
                                                               RuleReadingSource ruleSource,
                                                               ReadingPolicy policy,
                                                               NumericDomain.Bounds within,
                                                               java.util.Set<TypeSymbol> inside) {
        return switch (shape) {
            case Shape.Scalar scalar -> standsForA(scalar, within, ruleSource);
            // The empty one, for every collection nothing has said otherwise about. A row whose
            // collection is not what it is about should say so by carrying nothing, and where no rule
            // counts what the position holds there is nothing else to go on.
            case Shape.Sequence _, Shape.Mapping _ -> List.of(FixtureTemplate.emptyCollection());
            // Absence, which every optional holds. Answered here rather than through the classes
            // because what a class says about `Some` is what stands for the element, and asking that
            // while the element is being built is the element asking for itself.
            case Shape.Optional _ -> List.of(FixtureTemplate.none());
            // A unit data is one value, and naming it writes it. Nothing tells its one value from
            // another, so no class of the position names it and it is said here.
            case Shape.Unit unit ->
                    ruleSource.symbols().scope().reach(unit.name())
                            instanceof TypeReachName.Written written
                            ? List.of(FixtureTemplate.unitCase(written)) : List.of();
            // A record is written field by field, against the rules relating them.
            case Shape.Product product -> product.name() instanceof TypeSymbol.AtModule named
                    ? composed(named, ruleSource, policy, inside) : List.of();
            // What a sum or a union stands for is which of its cases it is, which is what the
            // position divides into rather than a value to name here.
            case Shape.Sum _, Shape.Cases _ -> List.of();
            // And the shapes that carry no value of their own, or that this compiler could not read.
            case Shape.Unresolved _, Shape.Tuple _, Shape.Function _, Shape.Uninhabited _,
                 Shape.Bottom _, Shape.Erroneous _, Shape.Undecided _ -> List.of();
        };
    }

    /** The value a primitive stands for, which for a number is one the position is left able to
     *  hold and for everything else is one fixed value. */
    private static List<FixtureTemplate> standsForA(Shape.Scalar scalar, NumericDomain.Bounds within,
                                                    RuleReadingSource ruleSource) {
        return switch (scalar.prim()) {
            case INT, DECIMAL -> numberStandingFor(numbersOf(scalar), within, ruleSource);
            case STRING -> List.of(FixtureTemplate.string("x"));
            case BOOL -> List.of(FixtureTemplate.bool(true));
            // A date is built from its ISO 8601 form, which is how a row writes one. One fixed day
            // rather than today's: a generated row is compared with the last one to see what changed,
            // and a value that read the clock would change every time nothing had.
            case DATE -> List.of(FixtureTemplate.date("2000-01-01"));
            case TIME -> List.of(FixtureTemplate.time("00:00:00"));
            case DATETIME -> List.of(FixtureTemplate.dateTime("2000-01-01T00:00:00"));
            case INSTANT -> List.of(FixtureTemplate.instant("2000-01-01T00:00:00Z"));
            // Bytes nobody wrote. What a row would carry is a value of somebody's making, and there
            // is none here to make it out of.
            case RAW -> List.of();
        };
    }

    /** A number the position is left able to hold, or none where what is left of it holds none. */
    private static List<FixtureTemplate> numberStandingFor(Carrier carrier,
                                                           NumericDomain.Bounds within,
                                                           RuleReadingSource ruleSource) {
        Place at = inside(within, carrier);
        FixtureTemplate standing = at == null ? null
                : FixtureTemplate.on(carrier, at, ruleSource.symbols().scope()::reach);
        return standing == null ? List.of() : List.of(standing);
    }

    /**
     * The values a recipe arrives at, whichever way it arrives at them.
     *
     * <p>{@code Values} and {@code Compose} are two ways of writing where a representative comes
     * from and not two answers about whether there is one, so a reader asking for one takes both
     * here. Read as a reader's own two questions — is it generatable, and does it hold values — a
     * class naming a constructor answered yes and then handed over nothing, and the position it
     * stood at was reported as one no value can be written at (issue #651).
     */
    static List<FixtureTemplate> standingFor(RepresentativeSource source, RuleReadingSource ruleSource,
                                             ReadingPolicy policy,
                                             java.util.Set<TypeSymbol> expanding) {
        return switch (source.evaluate()) {
            case RepresentativeSource.Evaluation.Values values -> values.written();
            case RepresentativeSource.Evaluation.Compose compose ->
                    composed(compose.through(), ruleSource, policy, expanding).stream()
                            .map(compose::written).toList();
            case RepresentativeSource.Evaluation.NothingProducible _ -> List.of();
        };
    }

    /**
     * A record composed field by field, or nothing where one of its fields has nothing to stand for
     * it.
     *
     * <p>{@code expanding} carries the names this is already inside the value of, so a record
     * reached from its own field is given up on there rather than composed forever. Path-local: a
     * name is in it only while the value under it is being built, so a type met twice in two
     * branches is composed in both.
     *
     * <p>Each field is chosen against what the record's rules leave it, which is the reading
     * {@link FieldDomains} makes of them: the range a clause leaves the value, and the floor a
     * clause counting it puts on what it holds. Chosen from the field's type alone, a record whose
     * rule asks its list for two is handed one holding none, and the row it was composed for comes
     * back as one every value tried was refused at.
     *
     * <p>Nothing about the clauses relating two fields, which are read where a row is searched for
     * one position at a time. Whether the values may be held together is the decoder's answer — the
     * same answer every other candidate this offers is put through.
     */
    private static List<FixtureTemplate> composed(TypeSymbol.AtModule record, RuleReadingSource ruleSource,
                                                  ReadingPolicy policy,
                                                  java.util.Set<TypeSymbol> expanding) {
        return composed(record, ruleSource, policy, expanding, Map.of());
    }

    /**
     * The same, with some fields already decided.
     *
     * <p>A field handed over is not chosen and is settled like any other: the rules are read again
     * with it in them, so what the fields beside it may be is asked of the record as it is being
     * built rather than of the record before anything was said. A caller composing the rest itself
     * would be choosing them against {@code a < b} with {@code a} open, which is the reading this
     * loop exists to avoid.
     *
     * @param given what stands at some of the fields, by name. A name no field has is nothing this
     *              can build, and is the caller asking for a value of another type
     */
    static List<FixtureTemplate> composed(TypeSymbol.AtModule record, RuleReadingSource ruleSource,
                                          ReadingPolicy policy,
                                          java.util.Set<TypeSymbol> expanding,
                                          Map<String, FixtureTemplate> given) {
        if (expanding.contains(record)) {
            return List.of();
        }
        Map<String, FixtureTemplate> chosen =
                fieldsOf(record, ruleSource, policy, expanding, given);
        return chosen == null || !(ruleSource.symbols().scope().reach(record)
                instanceof TypeReachName.Written written)
                        ? List.of() : List.of(FixtureTemplate.record(written, chosen));
    }

    /**
     * What stands at each field of {@code record}, or null where one of them has nothing to stand
     * for it.
     *
     * <p>Beside {@link #composed} and under it: this is the choosing, and writing the record is what
     * a caller then does with it. Asked on its own by the composing of a plan, which has the record
     * to write and needs what goes in it — a caller that took a written record apart again to get at
     * the fields would be reading one answer back out of another.
     */
    static Map<String, FixtureTemplate> fieldsOf(TypeSymbol.AtModule record,
                                                 RuleReadingSource ruleSource, ReadingPolicy policy,
                                                 java.util.Set<TypeSymbol> expanding,
                                                 Map<String, FixtureTemplate> given) {
        // What the record is made of, read where a position's reading is made. A walk from the
        // declaration to its fields is that same reading taken a second time, and the two part
        // wherever one of them reaches through a name the other stops at.
        //
        // And the name has to be the record's own, because the rules below are read on the name
        // the caller gave. Read through a name to another declaration's fields, the fields would
        // be one declaration's and the rules another's, and every field would be chosen against
        // rules that name nothing it has.
        TypeView view = TypeView.of(Type.ref(record), ruleSource.symbols());
        if (expanding.contains(record) || view.isWrapped()
                || !(view.shape() instanceof Shape.Product(TypeSymbol _,
                        Map<String, Type> fields))) {
            return null;
        }
        if (fields.isEmpty()) {
            return null;   // a unit has no fields to compose, and is named rather than built
        }
        java.util.Set<TypeSymbol> inside = new LinkedHashSet<>(expanding);
        inside.add(record);
        Map<RuleKey, Count> settled = new LinkedHashMap<>();
        FieldDomains left = FieldDomains.of(record, ruleSource, policy, settled);
        Map<String, FixtureTemplate> chosen = new LinkedHashMap<>();
        if (!fields.keySet().containsAll(given.keySet())) {
            return null;
        }
        for (Map.Entry<String, Type> field : fields.entrySet()) {
            FixtureTemplate at = given.get(field.getKey());
            if (at == null) {
                RuleKey named =
                        RuleKey.of(field.getKey());
                List<FixtureTemplate> stands = representativesHolding(field.getValue(), ruleSource,
                        policy, left.at(named).bounds(), left.heldAt(named), inside);
                if (stands.isEmpty()) {
                    return null;
                }
                at = stands.get(0);
            }
            chosen.put(field.getKey(), at);
            // Settled, and the rules read again with it in them. A field chosen against the rules as
            // they stand before anything is settled is chosen against `a < b` with `a` still open,
            // which leaves `b` its whole range and takes the bottom of it.
            if (Counts.writtenIn(at.value()) instanceof Count count) {
                settled.put(RuleKey.of(field.getKey()), count);
                left = FieldDomains.of(record, ruleSource, policy, settled);
            }
        }
        return chosen;
    }

    /** How many of whatever counts a value the rules on it require it to hold, read where the rules
     * are: {@link DeclaredBounds#leastCountOf}. Of the position as it was read, since how many a
     * value holds is what the rules of every name it wears say. */
    static int leastHeld(TypeView view, RuleReadingSource ruleSource) {
        return DeclaredBounds.leastCountOf(view, ruleSource);
    }

    /** The same, where the record the position sits in has a rule about it too. */
    static int leastHeld(TypeView view, RuleReadingSource ruleSource, FieldDomains.Held held) {
        return DeclaredBounds.leastCountOf(view, ruleSource, held);
    }

    /**
     * From how few to how many the rules let a value at the position hold, as one answer.
     *
     * <p><b>Both ends of one reading, and there is no reading of the cap alone.</b> A caller
     * deciding whether there is room for a value being placed needs the floor to know how many the
     * whole collection has to hold and the cap to know whether that many fit. Taken as two
     * readings, the two can be of different states of the row — which is how a floor settled where
     * the caller had fixed a field came to be compared with a cap read where it had not. So the
     * cap has no projection of its own here: a caller wanting it takes both and is holding one
     * answer.
     */
    static DeclaredBounds.CountRange heldRange(Type type, RuleReadingSource ruleSource,
                                               FieldDomains.Held held) {
        return DeclaredBounds.countsHeld(TypeView.of(type, ruleSource.symbols()), ruleSource, held);
    }

    /**
     * Why a position offered less than its rules allow, or null where it offered everything.
     *
     * <p>Two things are told apart from a refusal here, and both are facts about this rather than about
     * the model. A count past what a row is built to carry means no value of the shape the rule asks
     * for was built at all — a list of a million exists and somebody could write one — so a reader told
     * "every value tried was refused" would go looking for the rule that refuses a value nothing
     * refuses. More pairings between a map's key and value than are built at once means values of the
     * shape were built and refused and more of them were never reached, which is a search that stopped.
     *
     * <p>Asked only where nothing was written. It re-reads what a position could have offered, and a
     * row that was written has no reason to pay for that.
     *
     * <p>{@code held} for the same reason {@link #representativesHolding} takes one: the floor this
     * reads is the floor that was built against, and a reading here that knew only the type would
     * say "every value tried was refused" of a position whose values were never built.
     */
    static java.util.Set<CompositionBudget> notBuilt(Type type, RuleReadingSource ruleSource,
                                                     ReadingPolicy policy,
                                                     FieldDomains.Held held) {
        TypeView view = TypeView.of(type, ruleSource.symbols());
        return Witnesses.heldBackFor(view.shape(), leastHeld(view, ruleSource, held),
                ruleSource, policy);
    }

    /**
     * The {@code index}th number the rules on {@code type} leave it able to hold, or null where it has
     * no such number.
     *
     * <p>Asked where several values of one type are needed and they have to differ — the elements of a
     * set, the keys of a map. Counted from inside the range rather than from zero, because a second
     * value the type itself refuses would have the collection refused for its elements while saying
     * nothing about how many of them there are.
     *
     * <p>Only a whole number steps. Between two decimals there is no next value, so a dense carrier
     * names the one number inside its range and no more.
     */
    static Place numberInside(TypeView view, RuleReadingSource ruleSource, int index) {
        Carrier carrier = numbersOf(view.shape());
        if (carrier == null) {
            return null;
        }
        NumericDomain.Bounds range =
                TypeBounds.admissible(DeclaredBounds.of(view, ruleSource), null);
        Place from = inside(range, carrier);
        if (from == null || !(carrier instanceof Carrier.Whole)) {
            return from != null && index == 0 ? from : null;
        }
        Count stepped = Count.number(from).plus(index);
        return holdsCount(range, stepped) ? stepped : null;
    }

    /**
     * The order a position's own numbers are counted on, or null where its values are not numbers.
     *
     * <p>Of the shape, which is what the position is with every name off — so this is a projection
     * of the reading and not a second look at what the names wrap. Asked of the type instead, it
     * walked the names again to find the number under them, which is the reading's answer and was
     * already in hand.
     *
     * <p>Exhaustive over the primitives, with no {@code default}: whether a primitive's values are
     * counted is a question about each of them, and one added later is one this has to be told about
     * rather than one that falls to the arm it was not named in.
     */
    private static Carrier numbersOf(Shape shape) {
        if (!(shape instanceof Shape.Scalar scalar)) {
            return null;
        }
        return switch (scalar.prim()) {
            case INT -> Carrier.WHOLE;
            case DECIMAL -> Carrier.DENSE;
            // Ordered, some of them, and none of them counted in numbers of its own: a date is a
            // count of days and a string a count of characters, which is what a rule about them
            // counts rather than what the value is.
            case STRING, BOOL, DATE, TIME, DATETIME, INSTANT, RAW -> null;
        };
    }

    /**
     * The same, with what a floor asks for offered ahead of it.
     *
     * <p>Both, and the floor first. Each is what one rule was read to produce and which of them the
     * whole of the rules admits is the decoder's answer, so neither withdraws the other — the same
     * reading {@link #representativesOf(TypeView, RuleReadingSource, ReadingPolicy,
     * NumericDomain.Bounds, java.util.Set)} makes of a position whose own rules give it a floor,
     * made here of one whose floor is its record's. What the order decides is not which is right:
     * the search over a
     * row's positions is bounded, so a position offering the value that holds nothing first spends
     * an assignment on a value the rule refuses, and rows at positions the rule has nothing to do
     * with are what runs out.
     */
    static List<FixtureTemplate> representativesHolding(Type type, RuleReadingSource ruleSource,
                                                        ReadingPolicy policy,
                                                        NumericDomain.Bounds within,
                                                        FieldDomains.Held held) {
        return representativesHolding(TypeView.of(type, ruleSource.symbols()), ruleSource, policy,
                within, held, java.util.Set.of());
    }

    /** The same, of a position nothing here has read yet. */
    static List<FixtureTemplate> representativesHolding(Type type, RuleReadingSource ruleSource,
                                                        ReadingPolicy policy,
                                                        NumericDomain.Bounds within,
                                                        FieldDomains.Held held,
                                                        java.util.Set<TypeSymbol> expanding) {
        return representativesHolding(TypeView.of(type, ruleSource.symbols()), ruleSource, policy,
                within, held, expanding);
    }

    /** The same, with the names this is already inside the value of, for the same reason
     *  {@link #representativesOf} carries them. */
    static List<FixtureTemplate> representativesHolding(TypeView view, RuleReadingSource ruleSource,
                                                        ReadingPolicy policy,
                                                        NumericDomain.Bounds within,
                                                        FieldDomains.Held held,
                                                        java.util.Set<TypeSymbol> expanding) {
        List<FixtureTemplate> candidates = new ArrayList<>();
        // Under every name the position wears, because a floor read off the record says how much the
        // value holds and not what it is written as: a field of a newtype over a list takes a list
        // inside that newtype's own name.
        //
        // A name this module cannot write leaves no value to write, which is asked once of the
        // position rather than of each value built for it.
        if (WornNames.of(view.wrappers(), ruleSource) instanceof WornNames.Spelled spelled) {
            for (FixtureTemplate bare : Witnesses.holding(view.shape(),
                    leastHeld(view, ruleSource, held), ruleSource, policy, expanding)) {
                candidates.add(RepresentativeSource.under(spelled.names(), bare));
            }
        }
        candidates.addAll(representativesOf(view, ruleSource, policy, within, expanding));
        Map<String, FixtureTemplate> once = new LinkedHashMap<>();
        for (FixtureTemplate each : candidates) {
            once.putIfAbsent(each.text(), each);
        }
        return List.copyOf(once.values());
    }

    /**
     * The same, with a second value offered at a numeric position.
     *
     * <p>One value is enough only where the rules that decide it are the rules the projection holds.
     * A disequality is a hole no range keeps, and a form that is neither an interval nor a difference
     * is not recorded at all — so the value a range gives up can be one those rules refuse, and a
     * position offering only that has nothing left to try.
     *
     * <p>Displaced and not invented. A whole number has a next one; a decimal between two ends has a
     * midpoint, and where it has no second end it gets no second value, because an epsilon is a value
     * the type does not name. A candidate is a proposal the decoder answers — it carries no promise
     * that the value is writable, which is what separates this from a boundary a person is asked to
     * write.
     */
    static List<FixtureTemplate> displacedRepresentativesOf(Type type, RuleReadingSource ruleSource,
                                                            ReadingPolicy policy,
                                                            NumericDomain.Bounds within,
                                                            FieldDomains.Held held) {
        // The position, read once and handed to everything below: what it ordinarily offers, what it
        // holds back, where its rules leave a number, and the names any of those go under.
        TypeView view = TypeView.of(type, ruleSource.symbols());
        List<FixtureTemplate> base = new ArrayList<>(representativesHolding(
                view, ruleSource, policy, within, held, java.util.Set.of()));
        // What a position holds back for the product search's second pass is on offer here from the
        // start. This pass runs only where both of those have already failed, and a position keeping
        // a value from the last search there is a value nothing will ever be tried at.
        for (FixtureTemplate kept : inReserve(view, ruleSource, policy, within)) {
            if (base.stream().noneMatch(each -> each.text().equals(kept.text()))) {
                base.add(kept);
            }
        }
        Carrier carrier = numbersOf(view.shape());
        if (carrier == null) {
            return List.copyOf(base);
        }
        NumericDomain.Bounds range =
                TypeBounds.admissible(DeclaredBounds.of(view, ruleSource), within);
        Place step = displaced(range, carrier);
        if (step == null) {
            return List.copyOf(base);
        }
        FixtureTemplate value = standing(view, carrier, step, ruleSource);
        // Nothing to offer beside the first, either because the order has no value there or because
        // a name the position wears is one this module cannot write.
        if (value == null || base.stream().anyMatch(each -> each.text().equals(value.text()))) {
            return List.copyOf(base);
        }
        base.add(value);
        return List.copyOf(base);
    }

    /** A second count of a range, or null where the carrier has none to give. */
    private static Place displaced(NumericDomain.Bounds range, Carrier carrier) {
        Place from = inside(range, carrier);
        if (from == null) {
            return null;
        }
        Endpoint min = range == null ? null : range.min();
        Endpoint max = range == null ? null : range.max();
        if (carrier.spacing() == Granularity.DENSE) {
            // No smallest step, so the only second count a range names is one inside both its ends —
            // and the one already on offer is that count where the range is open below.
            return min == null || max == null || !min.inclusive() ? null
                    : carrier.somethingInside(Endpoint.exclusive(min.at()), max);
        }
        Count up = Count.number(from).plus(1);
        if (holdsCount(range, up)) {
            return up;
        }
        Count down = Count.number(from).minus(1);
        return holdsCount(range, down) ? down : null;
    }

    /** Whether a range holds a count, with no range holding everything. */
    private static boolean holdsCount(NumericDomain.Bounds range, Place at) {
        return range == null || range.admits(at);
    }

    /**
     * A value a source can carry that {@code regex} accepts, or null where there is none to offer.
     *
     * <p>Null two ways, and they are one answer here: a pattern whose machine costs more than
     * writing a value is allowed, and one every string of which is something nobody can paste. What
     * a caller does with each of them is offer no candidate, so they are not told apart — a row is
     * offered or it is not. A pattern outside the subset this compiler reads never reaches here:
     * the reading says so, and the caller offers no candidate for the same reason.
     *
     * <p>Read by the one thing here that reads patterns. What this used to have was a reader of its
     * own, which meant two answers to "what does this pattern accept" and one model where they
     * could differ.
     */
    private static String writtenFor(souther.compiler.regex.PatternSyntax syntax) {
        souther.compiler.regex.Language language = souther.compiler.regex.PatternPlan.of(syntax)
                .compile(souther.compiler.regex.PatternPlan.Budget.OF_A_WITNESS.meter());
        return language == null ? null : language.someWritten();
    }

    /** A count the position holds, or null where it holds none. The ends decide it, so nothing here
     * reads one of them as a number and loses whether the range reaches it. */
    private static Place inside(NumericDomain.Bounds within, Carrier carrier) {
        if (within == null) {
            return Count.ZERO;
        }
        Place between = carrier.somethingInside(within.min(), within.max());
        if (between == null) {
            return null;
        }
        // On the carrier's own grid, and then still inside. A number between two counts it can hold
        // is not always one of them (see Carrier#onTheGrid).
        Place held = carrier.onTheGrid(between);
        return held != null && within.admits(held) ? held : null;
    }

    /**
     * What a position can offer once everything it ordinarily offers has been refused.
     *
     * <p>The far edge of a range, for a position that is a bounded number. A rule relating this
     * position to another is what refuses the near one: `low < high` with `low` already fixed leaves
     * `high` a range starting at the value `low` took, and over decimals that range starts at the one
     * number the rule will not accept, because a strict bound there has no next value to step to. The
     * far edge is not knowing which number the rule wants — it is having a second number to be
     * refused at, which a range offering one edge does not.
     *
     * <p>Held back rather than offered beside the others, because what a row is searched for is the
     * product of what its positions offer and that search is bounded. A value added to one position
     * moves every assignment past it further back, so offering this one among the rest would lose
     * rows that were being reached at positions this has nothing to do with.
     */
    static List<FixtureTemplate> inReserve(Type type, RuleReadingSource ruleSource, ReadingPolicy policy,
                                           NumericDomain.Bounds within) {
        return inReserve(TypeView.of(type, ruleSource.symbols()), ruleSource, policy, within);
    }

    /** The same, of a position that has already been read. */
    static List<FixtureTemplate> inReserve(TypeView view, RuleReadingSource ruleSource,
                                           ReadingPolicy policy, NumericDomain.Bounds within) {
        // A far edge is a rule's, and a rule is written on a name. A position wearing none carries
        // no rule of its own, so there is no edge here to hold anything back at.
        if (!view.isWrapped()) {
            return List.of();
        }
        DeclaredBounds.Bounds own = DeclaredBounds.of(view, ruleSource);
        NumericDomain.Bounds bounds = TypeBounds.admissible(own, within);
        // The far end has to be a value the position holds. Where the range stops short of it there
        // is nothing there to hold back, and a dense order has no value beside it to hold back
        // instead — what is inside is already what the first tier offers.
        if (bounds == null || bounds.min() == null || bounds.max() == null
                || !bounds.max().inclusive()
                || bounds.max().at().sameAs(bounds.min().at())) {
            return List.of();
        }
        FixtureTemplate held = WornNames.under(view.wrappers(),
                FixtureTemplate.on(own.carrier(), bounds.max().at(),
                        ruleSource.symbols().scope()::reach), ruleSource);
        if (held == null) {
            return List.of();   // a name this module cannot write leaves no value to hold back
        }
        // Nothing already on offer: a range whose far edge is the number the base type stands for
        // would otherwise hold the same value twice, once in each tier.
        return representativesOf(view, ruleSource, policy, within, java.util.Set.of()).stream()
                .map(FixtureTemplate::text).anyMatch(held.text()::equals)
                ? List.of() : List.of(held);
    }

    private Partitions() {}
}
