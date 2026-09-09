package souther.compiler.query;

import souther.compiler.check.CoverageObligation;
import souther.compiler.check.ElementBindings;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.inputs.FilingCoordinate;
import souther.compiler.inputs.InputQuestion;
import souther.compiler.inputs.RulesWithNoLine;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.partition.LinesWhereTheyFall;
import souther.compiler.publish.PublicationOrders;
import souther.compiler.ast.Hir;
import souther.compiler.check.PathReachability;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.numeric.Place;
import souther.compiler.core.Core;
import souther.compiler.coverage.ComparisonEmissionSite;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.SiteNumbering;
import souther.compiler.observe.Classification;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.RowOutcome;
import souther.compiler.partition.ObservedInputs;
import souther.compiler.partition.Axis;
import souther.compiler.partition.Border;
import souther.compiler.partition.Criterion;
import souther.compiler.partition.ReachingCuts;
import souther.compiler.partition.Demand;
import souther.compiler.partition.DomainPoint;
import souther.compiler.partition.BorderQuantity;
import souther.compiler.partition.StandingAtAPoint;
import souther.compiler.partition.LevelRealizer;
import souther.compiler.partition.Realization;
import souther.compiler.partition.EnsuresThresholds;
import souther.compiler.partition.GuardThresholds;
import souther.compiler.partition.BoundaryLine;
import souther.compiler.partition.PartitionClass;
import souther.compiler.partition.Partitions;
import souther.compiler.partition.InputClassifications;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Measuring one behavior's rows against the distinctions its model draws.
 *
 * <p>Each position is measured on its own. A row that could not be read at one position still says
 * what it says at the others, so an unreadable value leaves that axis undecided and nothing else.
 */
final class Coverages {

    /**
     * What the model divides one behavior into, and the reading of its declarations that produced
     * it.
     *
     * <p>Two things and not one, because only the first is what the model says. The reading holds a
     * way of asking the declarations reaching an input a further question, so it belongs to whoever
     * is doing the asking and compares by which of them built it — kept inside the geometry, it made
     * the geometry a thing no answer could hold.
     *
     * <p>Handed over together because they are produced together and reading is what costs: every
     * rule of every parameter is read to arrive at either, and a caller that wanted both and asked
     * twice would read them twice.
     */
    record Partitioned(Partitions.Partitioning geometry,
                       souther.compiler.inputs.Quantities reading,
                       java.util.Map<souther.compiler.partition.ConditionOccurrence,
                               souther.compiler.diag.Citation> conditionsMet) {}

    /**
     * The positions one behavior is measured at, with what its own comparisons divide them into.
     *
     * <p>Asked here rather than worked out again wherever it is needed. What a report says is not
     * covered and what a generator writes a row for have to be the same positions and the same classes,
     * and two derivations of them would be two chances to disagree.
     *
     * <p><b>Measured against the reading it is handed.</b> Which positions the input has and what
     * its rules leave the numbers at them are the reading's to say, and the names and the policy
     * both were read against come with it. Taken apart and handed over as a domain beside the names
     * to read it under, the measurement would be free to be made against one reading and the
     * behavior's rows walked by another.
     */
    static Partitioned partitioningOf(Hir.SpecBehavior behavior,
                                      souther.compiler.inputs.InputReading read,
                                      Core body,
                                      CoverageSites.Plan plan,
                                      PathReachability.Answers arrives,
                                      souther.compiler.check.StatedContract stated,
                                      souther.compiler.check.AnalysisBody analysis,
                                      souther.compiler.values.Allowance<
                                              souther.compiler.inputs.NumericTerm.FromOnePosition>
                                              distinctions) {
        RuleReadingSource ruleSource = read.rules();
        ReadingPolicy policy = read.domain().policy();
        souther.compiler.inputs.Quantities quantities = read.quantities();
        Partitions.Partitioning partitioning =
                Partitions.of(behavior.name(), read, policy);
        // What the behavior states about its own answer, which is read whether or not anything
        // implements it: a clause is written against the declaration, so an injected behavior draws
        // its lines like any other and there is no body for them to have come out of.
        EnsuresThresholds.Clauses clauses = EnsuresThresholds.of(stated, read);
        // What the analysis tree binds to the elements of what, built once for both readers of it.
        // The bindings an expansion wrote are the bindings of the tree it expanded: the two
        // representations of one body are two expansions with two sets of them, so `elements` —
        // which is the emitted tree's — is about bindings neither reader below has.
        ElementBindings standing = analysis == null
                ? ElementBindings.NONE
                : ElementBindings.of(analysis.core(), analysis.elements(),
                        read.symbols());
        // Whether there is a tree to read is the reading's own answer, so a body with no analysis
        // representation is handed over and comes back with nothing rather than being checked for
        // here as well.
        GuardThresholds.Guards guards = body == null ? GuardThresholds.Guards.NONE
                : GuardThresholds.of(behavior.name(), analysis, body, plan, read, standing, arrives);
        // And what the declarations state between two of this input's positions. Such a rule places
        // no end at either of them, so the reading of ends has nothing to draw it from; read here,
        // it is a line like the two above and is arranged with them.
        // And what the behavior states about the strings at its positions, in its body and in its
        // own clauses alike. Read off the tree that has those rules in it: the emitted body has
        // each of them expanded into what it does, so a reading of it holds none — which is why
        // this is the other body and not the one above. Both go to one reader, because a term
        // written about in both places is one term with one set of classes, and two readers would
        // be two measures of it, each told nothing of the other's.
        souther.compiler.partition.BehaviorSetStatements.Read sets =
                souther.compiler.partition.BehaviorSetStatements.of(behavior.name(), analysis, stated, read,
                        read.domain().parameterReads(), standing, distinctions, guards.forks());
        List<souther.compiler.partition.LineDrawn> declared =
                souther.compiler.partition.DeclaredThresholds.between(behavior.name(), read);
        // Every producer of one kind of line, put together before the position is divided. Two
        // rules at one value are one cut and stay separate obligations, which is what the merge
        // below does — applied one producer at a time, a clause and a guard naming one number would
        // divide the position twice.
        // And each of them at the positions the name it was drawn on reaches, before anything is
        // matched against an axis. A field every case of a sum spreads is named at the sum and
        // written under a case, so one line there is one line per case — on the same number and from
        // the same rule.
        souther.compiler.partition.LinesWhereTheyFall.Filed filed =
                souther.compiler.partition.LinesWhereTheyFall.of(read,
                        both(both(clauses.evidence(), guards.evidence()), sets.statements()),
                        sets.blocked(),
                        both(declared, both(clauses.between(), guards.between())));
        return new Partitioned(Partitions.withEvidence(partitioning, quantities,
                filed.evidence(), filed.blocked(), distinctions, ruleSource, policy,
                // And the lines this had nowhere to put, which are findings of the same kind: a rule
                // of the model that came to no line at a position it is about.
                everyRuleWithNoLine(clauses, guards, filed, sets),
                filed.between(),
                // What a row had to satisfy to arrive at each comparison, from the walk that
                // assumed it. A clause of a declaration is not written at a place in a body and has
                // nothing on the way to it, so only the guards have any of this.
                guards.reaching()), quantities,
                // Where this reading met each condition it places itself, beside the geometry and
                // not inside it. A report points at a condition and an answer says which condition
                // it is, and the two are kept apart so that moving one leaves the other alone.
                guards.conditionsMet());
    }

    /**
     * Every rule that came to no line, from all three readers, sorted the one way.
     *
     * <p>Which of them found a rule is not part of what any of them found: a clause of a
     * declaration, a body's comparison and a line nothing could place are three producers of one
     * kind of evidence, and a reader downstream is told the same thing about any of them.
     */
    private static RulesWithNoLine everyRuleWithNoLine(
            EnsuresThresholds.Clauses clauses, GuardThresholds.Guards guards,
            LinesWhereTheyFall.Filed filed,
            souther.compiler.partition.BehaviorSetStatements.Read sets) {
        // And the rules about the strings that divided no position, which are findings of the same
        // kind. Left out, a rule an author wrote would reach the measure, come to nothing, and be
        // shown to nobody — while the position it names came back as one the model says nothing
        // about.
        souther.compiler.inputs.RulesWithNoLine.Gathered found =
                new souther.compiler.inputs.RulesWithNoLine.Gathered();
        filed.blocked().forEach(each -> found.add(each.reported()));
        // And the rules that reached the measure and divide no position. They hold nothing open —
        // a rule read to the end that tells nothing apart has been read, and one about a value an
        // operation made from a position is about that value — so they are said and nothing waits
        // on them.
        sets.saying().forEach(found::add);
        // And the forks whose condition no reader took in. A question for each and no finding: what
        // a report is owed about such a rule is that nothing worked out what it states, which the
        // question says, and where it says it is what the reading got to rather than what the fork
        // is about.
        sets.forks().forEach(each ->
                each.filed().forEach((at, why) -> found.unclassified(each.cited(), at, why)));
        return clauses.noLine().and(guards.noLine()).and(filed.notPlaced()).and(found.found());
    }

    /** The two producers' lines, in one list. */
    private static <T> List<T> both(List<T> declared, List<T> compared) {
        if (declared.isEmpty()) {
            return compared;
        }
        List<T> all = new ArrayList<>(declared);
        all.addAll(compared);
        return List.copyOf(all);
    }

    /**
     * What the classes measure comes to for one behavior.
     *
     * <p>What the model divides this behavior into is made once by
     * {@link souther.compiler.query.Adequacy.Divided} and read off {@code subject} here. Worked out
     * again on the way in, this and the boundaries beside it would be two derivations of one thing
     * and two chances to disagree about it. The lines those rules drew are not part of it: what a
     * behavior is owed at them is the module's one relation projected to it
     * ({@link souther.compiler.query.Adequacy.BodyBorders}), and the classes measure reads none of
     * it.
     */
    static PartitionEvidence of(souther.compiler.partition.MeasuredInput subject,
                                souther.compiler.query.Adequacy.RowReading observed,
                                souther.compiler.query.Adequacy.Level level,
                                souther.compiler.partition.AdequacyPolicy.OfTheMeasures budget) {
        List<RowOutcome> rows = observed.rowsSeen();
        Partitions.Partitioning partitioning = subject.partitioning();

        List<PartitionEvidence.AxisCoverage> axes = new ArrayList<>();

        // The measures that divide their number, which are the ones a row is placed at. Handed
        // every measure, this asks a classifier about numbers there are no classes to place a value
        // in, and the count it comes back with is over a set no answer here is about.
        Readings readings = Readings.of(rows, subject,
                observed.gaps().stream()
                        .filter(gap -> gap.fact().code().leftNoRowRead()).toList());
        // Walked as the reading holds it: a location at a time, and each measure beside the
        // location it sits at. What one of its measures is owed to say includes what the reading of
        // the position left unread, which is the position's answer — asked of the entry the walk is
        // at rather than of whichever measure happens to be in hand.
        for (Readings.AtPosition at : readings.positions()) {
            for (Readings.AxisReading reading : at.axes()) {
                axes.add(coverageOf(at, reading, readings, partitioning, level.readsRows()));
            }
        }
        // Each measure asked its own closure, and neither told from the length of what came back.
        // What one of them is short of says nothing about the other: a rule whose line nothing
        // could read leaves the border measure short while the classes either side of it were read
        // in full, and the enumeration above is the same case the other way round.
        return new PartitionEvidence(
                PartitionDerivation.of(axes, partitioning.partitionClosure(),
                        partitioning.inputIsEmpty()),
                pairsOf(subject.behavior(), readings, level.readsRows(), budget),
                partitioning.undivided(), partitioning.rulesWithoutALine(), partitioning.blocked(),
                // What the model asked and nothing answered, taken whole and not gathered as the
                // axes are walked. The questions are the model's; whether a position could be
                // measured is the separate answer `undivided` beside them carries, and a position
                // no axis came back for still has whatever was written about it.
                partitioning.notSeparated(), unansweredIn(partitioning),
                readings.whyUnclassified());
    }

    /**
     * What every row said at every position, read once.
     *
     * <p>The measures below were putting the same question to the rows — which class is this row in,
     * here — and answering the follow-up differently. The single-position coverage counted the rows
     * that could not say and reported them as undecided; the combinations left those rows out and
     * reported what remained as untried. One reading, so that a row nothing could place is the same
     * fact wherever it is read.
     *
     * @param unseen rows that were never observed at all, which no reading can show — each as the
     *               reason it was not observed, so that a measure weakened by one of them says which
     */
    record Readings(List<AtPosition> positions, List<WhereARowSat> byRow,
                    List<Incompleteness.Met> unseen) {

        /**
         * What the rows came to at every measure of one location.
         *
         * <p>The unit a reader of a location answers in: the location's own account, and beside it
         * what the rows said at each number measured there. Which measure belongs to which location
         * is settled while this is built and is never worked out again from a measure in hand.
         */
        record AtPosition(souther.compiler.partition.MeasuredInput.MeasuredPosition position,
                          List<AxisReading> axes) {}

        /**
         * What the rows came to at one measure.
         *
         * <p>The classes they reached, how many could not say where they were, and one reason per
         * kind for those that could not. Read once as the rows are walked: a caller that asked
         * again with a measure in hand would be putting a question to this reading about something
         * it holds, which is what an axis from another measurement gets a wrong answer out of.
         */
        record AxisReading(Axis axis, int at, Set<String> covered, int couldNotSay,
                           List<Incompleteness> stopped) {}

        /** Where one row was placed at each measure, in the order the measures are walked. Null at
         *  a measure the row is not placed by, which is one the model only bounds. */
        record WhereARowSat(List<Classification> at) {}

        static Readings of(List<RowOutcome> rows,
                           souther.compiler.partition.MeasuredInput subject,
                           List<Incompleteness.Met> unseen) {
            // The measures a row is placed at, in the measurement's own order, and what each row
            // came to at each of them — asked for in that order and answered in it, so nothing
            // here looks a measure up in what it was just handed.
            souther.compiler.partition.MeasuredInput.MeasuredAxes walked = subject.partitionAxes();
            List<WhereARowSat> read = new ArrayList<>(rows.size());
            for (RowOutcome row : rows) {
                read.add(new WhereARowSat(InputClassifications.placedAt(row.inputs(), walked)));
            }
            // And the same run of measures cut where the locations cut it, which is what the walk
            // above is the flattening of.
            List<AtPosition> out = new ArrayList<>();
            int index = 0;
            for (souther.compiler.partition.MeasuredInput.MeasuredPosition at
                    : subject.measurements()) {
                List<AxisReading> here = new ArrayList<>();
                for (Axis axis : at.partitionAxes().axes()) {
                    here.add(readingOf(axis, index++, read));
                }
                out.add(new AtPosition(at, List.copyOf(here)));
            }
            return new Readings(List.copyOf(out), List.copyOf(read), List.copyOf(unseen));
        }

        /**
         * What the rows came to at the measure the walk has reached.
         *
         * <p>A row that could not read a value here is counted among those that could not say,
         * whatever else it placed; one that read every value and put none in a class is not, since
         * it was read and says so. One reason per kind, which is what a hundred rows too large at
         * one position are — how many there were is the count beside it.
         */
        static AxisReading readingOf(Axis axis, int at, List<WhereARowSat> rows) {
            Set<String> covered = new LinkedHashSet<>();
            Map<Object, Incompleteness> byKind = new LinkedHashMap<>();
            int couldNotSay = 0;
            for (WhereARowSat row : rows) {
                Classification said = row.at().get(at);
                if (said instanceof Classification.Classified in) {
                    covered.addAll(in.classIds());
                }
                if (said != null && said.stopped() != null) {
                    couldNotSay++;
                    byKind.putIfAbsent(said.stopped().identity(), said.stopped());
                }
            }
            return new AxisReading(axis, at, covered, couldNotSay,
                    List.copyOf(byKind.values()));
        }

        boolean someRowsUnseen() {
            return !unseen.isEmpty();
        }

        boolean noRows() {
            return byRow.isEmpty();
        }

        /** Every measure a row is placed at, in the order they were walked. The flattening of the
         *  locations above and not a second list beside them. */
        List<AxisReading> everyAxis() {
            List<AxisReading> out = new ArrayList<>();
            positions.forEach(at -> out.addAll(at.axes()));
            return List.copyOf(out);
        }

        /**
         * Whether every row said where it was at {@code read}.
         *
         * <p>Only then does a class or a combination nothing sits in mean nothing reaches it. One
         * row that could not be placed at one of the positions leaves every class of that position,
         * and every combination it takes part in, undecided rather than untried.
         */
        WeakeningSet weakening(List<AxisReading> read) {
            Set<Weakening> out = new LinkedHashSet<>();
            for (Incompleteness.Met gap : unseen) {
                out.add(new Weakening.ObservationIncomplete(gap));
            }
            // Every reading's, whatever else met the same thing. What makes two of these one fact
            // and what happens to the places they were met at is the account's, asked once there
            // rather than settled again by whichever reading this walk reached first.
            for (AxisReading each : read) {
                each.stopped().forEach(gap -> out.add(Weakening.ObservationIncomplete.of(gap)));
            }
            return WeakeningSet.ofAll(out);
        }

        /**
         * Why the rows that could not be placed could not be placed — one reason per kind, in the
         * order the measures were walked.
         *
         * <p>Walked in that order rather than in a row's own map, which is built with
         * {@code Map.copyOf} and so iterates in an order that changes between runs. A report that
         * changes between runs cannot be compared between runs.
         */
        List<Incompleteness> whyUnclassified() {
            return reasonsIn(everyAxis());
        }

        /** The same over any run of measures the walk produced. */
        static List<Incompleteness> reasonsIn(List<AxisReading> read) {
            Map<Object, Incompleteness> byKind = new LinkedHashMap<>();
            for (AxisReading each : read) {
                each.stopped().forEach(gap -> byKind.putIfAbsent(gap.identity(), gap));
            }
            return List.copyOf(byKind.values());
        }
    }

    /**
     * The two-class combinations the rows reach.
     *
     * <p>A combination a row sits in is <em>proven reachable</em>, and the row is the proof. One no row
     * sits in has not been shown unreachable — nothing has tried to build a value for it — so it is
     * unknown rather than missing. The difference matters: calling it unreachable flatters the number,
     * and calling it missing sends the author after a row that may not exist.
     *
     * <p>Pairs, rather than the full product, for the reason pairwise testing exists: most rules that
     * two inputs are involved in are decided by those two, and the product of every input is more rows
     * than anyone writes. A behavior with one divided position has no pairs at all, which is why the
     * single-position coverage is measured on its own and not derived from this.
     */
    private static PartitionEvidence.PairSpace pairsOf(String behavior,
                                                      Readings readings, boolean asked,
                                                      souther.compiler.partition.AdequacyPolicy
                                                              .OfTheMeasures budget) {
        // Every measure a row is placed at, which is the locations above flattened rather than a
        // list somebody gathered beside them. A pair is between two positions, so this question is
        // the one that reads across them.
        List<Readings.AxisReading> read = readings.everyAxis();
        List<Axis> axes = read.stream().map(Readings.AxisReading::axis).toList();
        // The product of what a row can be written at, not of what the types declare. A case the
        // rules refuse is not a class of its position at all, so the slice of the product it would
        // have taken part in is not here to be counted — which is a different thing from a pair
        // whose two classes each have rows but never together.
        //
        // And a pair no value is in is not one either. A position under one case of a sum and a
        // position under another are not in one value, so their classes make no combination: counted
        // as the product, the measure of a behavior taking a sum would fall by however many
        // combinations the model does not have.
        //
        // Kept as the pairs they were worked out between, rather than added up here. Which two
        // positions a combination is between is what the walk knows at the moment it counts, and a
        // sum is the one projection of that from which no reader can get it back.
        List<PartitionEvidence.PairSpace.AxisPair> space = new ArrayList<>();
        for (int i = 0; i < axes.size(); i++) {
            for (int j = i + 1; j < axes.size(); j++) {
                long between = combinationsOf(axes.get(i), axes.get(j));
                if (between > 0) {
                    space.add(new PartitionEvidence.PairSpace.AxisPair(
                            new PartitionEvidence.PairSpace.Between(
                                    axes.get(i).id(), axes.get(j).id()), between));
                }
            }
        }
        if (space.isEmpty()) {
            return PartitionEvidence.PairSpace.NONE;
        }
        // Before anything about the rows, because there are none to be about: a build that asked for
        // no measurement read no row, and what the model holds stays what the model says it is.
        if (!asked) {
            return PartitionEvidence.PairSpace.notAsked(space);
        }
        // Before the size of the space is worth mentioning. A combination nothing tried to sit in is
        // not a combination left untried by anybody, and how many of them there are says nothing
        // about a behavior no row names.
        if (readings.noRows() && !readings.someRowsUnseen()) {
            return PartitionEvidence.PairSpace.noRows(space);
        }
        long total = space.stream().mapToLong(PartitionEvidence.PairSpace.AxisPair::total).sum();
        if (total > budget.pairSpace()) {
            return PartitionEvidence.PairSpace.truncated(behavior, space, total,
                    budget.pairSpace());
        }
        // One set of combinations per relation, in the order the relations were worked out. What is
        // counted is the same as it was; where the count goes is what changed.
        SequencedMap<PartitionEvidence.PairSpace.Between, Set<String>> reached =
                new LinkedHashMap<>();
        space.forEach(pair -> reached.put(pair.between(), new LinkedHashSet<>()));
        // Which relation each pair of positions is, worked out once. Which two positions they are
        // does not turn on the row, and made inside the walk over the rows it is a name built and
        // thrown away for every row the behavior has.
        Map<Long, Set<String>> byPositions = new LinkedHashMap<>();
        for (int i = 0; i < axes.size(); i++) {
            for (int j = i + 1; j < axes.size(); j++) {
                Set<String> here = reached.get(new PartitionEvidence.PairSpace.Between(
                        axes.get(i).id(), axes.get(j).id()));
                if (here != null) {
                    byPositions.put((long) i * axes.size() + j, here);
                }
            }
        }
        for (Readings.WhereARowSat where : readings.byRow()) {
            for (int i = 0; i < axes.size(); i++) {
                for (int j = i + 1; j < axes.size(); j++) {
                    Set<String> here = byPositions.get((long) i * axes.size() + j);
                    if (here == null) {
                        continue;
                    }
                    // Every pairing the row reaches, and only those. A row whose list holds
                    // elements either side of a line stands in both classes there, and which of
                    // them went with what the position beside it holds is settled by which element
                    // each came from — taken as every combination, a row is evidence for a pair
                    // none of its elements is in.
                    for (Map.Entry<String, String> pair : Classification.pairsOf(
                            where.at().get(i), where.at().get(j))) {
                        // Which classes, within the relation that holds them. The relation is the
                        // key above and a class id is unique within its axis, so what is written
                        // here needs to tell two combinations of these two positions apart and no
                        // more.
                        here.add(pair.getKey() + " " + pair.getValue());
                    }
                }
            }
        }
        SequencedMap<PartitionEvidence.PairSpace.Between, Integer> counts = new LinkedHashMap<>();
        reached.forEach((between, in) -> counts.put(between, in.size()));
        PartitionEvidence.PairSpace.CoveredBetween made = new PartitionEvidence.PairSpace.CoveredBetween(counts);
        WeakeningSet by = readings.weakening(read);
        return new PartitionEvidence.PairSpace(space, by.isEmpty()
                ? new Measurement.Complete<>(made) : new Measurement.Partial<>(made, by));
    }

    /**
     * Whether the rules made this position's classes by cutting or parting its values.
     *
     * <p>Asked of the axis's own two lists, which are what say so. A position whose classes are the
     * cases of a sum has neither: nothing was cut, nothing was parted, and the classes are what the
     * value's shape already had. So this is what tells a division the rules made from one they
     * merely count.
     */
    private static boolean cutOrParted(Axis axis) {
        return !axis.cuts().isEmpty() || !axis.parted().isEmpty();
    }

    /**
     * How many combinations two positions' classes make between them.
     *
     * <p>The product where the two are in one value, and less where they are not. What a row at a
     * class has to be is one merge ({@link souther.compiler.inputs.Requirements}), and the same
     * merge is what the generator asks before it composes a row — so what is counted here and what
     * a row is offered for come from one reading rather than from two that agree until a case is
     * added.
     */
    private static long combinationsOf(Axis one, Axis other) {
        long count = 0;
        for (PartitionClass here : one.classes()) {
            for (PartitionClass there : other.classes()) {
                if (one.requiring(here).compatibleWith(other.requiring(there))) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * The questions the rules of this behavior's inputs raise that nothing answered, as a document
     * says them.
     *
     * <p>Once, off the partitioning, and not once per axis. The model raises them and an axis is
     * one reader of them: a position no axis came back for still has whatever was written about it,
     * and an axis is re-pointed at another number as a body's rules are read, so a question found
     * beside one is not thereby a question about the number that axis ended up measuring.
     */
    private static List<PartitionEvidence.Unanswered> unansweredIn(
            souther.compiler.partition.Partitions.Partitioning partitioning) {
        return partitioning.unanswered().stream()
                .map(PartitionEvidence.Unanswered::new)
                .toList();
    }

    /**
     * Whether one standing question is one this axis is a reader of.
     *
     * <p>Applicability and not ownership. Both sides name what they are about in the same
     * vocabulary and the comparison is between those two names — which is a different thing from
     * recovering the question's subject from what the axis happens to be measured at.
     */
    private static boolean appliesTo(StandingQuestion asked, Axis axis) {
        return switch (asked) {
            case StandingQuestion.Exact one -> switch (one.asks()) {
                case InputQuestion.AboutAPosition it -> it.path().equals(axis.path());
                case InputQuestion.AboutANumber it -> it.about().equals(axis.subject());
            };
            // Where the reader was sent, which is what such a question has instead of a subject.
            // A measure of the place a rule was filed at is a reader of it: nothing worked out what
            // the rule states there, so what this axis says about the place rests on it.
            case StandingQuestion.Unclassified one -> switch (one.at()) {
                case FilingCoordinate.AtPosition it -> it.path().equals(axis.path());
                case FilingCoordinate.OfTerm it -> it.term().equals(axis.term());
            };
        };
    }



    private static PartitionEvidence.AxisCoverage coverageOf(Readings.AtPosition where,
            Readings.AxisReading reading, Readings readings,
            souther.compiler.partition.Partitions.Partitioning partitioning,
            boolean asked) {
        Axis axis = reading.axis();
        souther.compiler.partition.PositionAccount at = where.position().position();
        List<String> classes = axis.classes().stream().map(PartitionClass::id).toList();
        // Which of this position's rules nothing accounted for, each named. Read off the position
        // rather than worked out here, and in the questions' own words: the vocabulary beside it
        // says why a division could not be derived, which is a different question, and borrowing it
        // left a reader with a sentence that named neither.
        PartitionEvidence.AxisCoverage.Reading read = new PartitionEvidence.AxisCoverage.Reading(
                at.residue().rulesLeftUnread().isEmpty()
                        ? PartitionEvidence.AxisCoverage.Reach.EVERY_RULE
                        : PartitionEvidence.AxisCoverage.Reach.SOME_OUT_OF_SIGHT,
                // Of the questions standing at this position, the ones this measure is the reader
                // of. What classes are made of is which values may stand somewhere; where the line
                // falls is the border measure's question, and counting it here would put a number
                // #869 separated back together. The questions themselves are beside the measures
                // and are said there once.
                partitioning.unanswered().stream()
                        .filter(each -> appliesTo(each, axis))
                        .noneMatch(each -> each.holdsOpen(
                                CoverageObligation.Measure.PARTITION)));
        // Nothing a body claims is in scope here. What a row is owed at is counted first and on its
        // own, and what was declared about those positions is put beside it afterwards
        // ({@link ClaimReport}) — which is what keeps a claim from narrowing a denominator by being
        // in reach of the code that counts one.
        // Which classes there are is the model's answer and is above; what the rows reach of them is
        // below, and a build that asked for no measurement read no row.
        if (!asked) {
            return PartitionEvidence.AxisCoverage.notAsked(axis.id(),
                    axis.term().toString(), classes, axis.divides(), cutOrParted(axis), read);
        }
        if (readings.noRows() && !readings.someRowsUnseen()) {
            return PartitionEvidence.AxisCoverage.noRows(axis.id(),
                    axis.term().toString(), classes, axis.divides(), cutOrParted(axis), read);
        }
        PartitionEvidence.AxisCoverage.Reached reached =
                new PartitionEvidence.AxisCoverage.Reached(reading.covered(),
                        reading.couldNotSay());
        WeakeningSet by = readings.weakening(List.of(reading));
        return new PartitionEvidence.AxisCoverage(axis.id(), axis.term().toString(),
                classes, axis.divides(), cutOrParted(axis), read, by.isEmpty()
                        ? new Measurement.Complete<>(reached)
                        : new Measurement.Partial<>(reached, by));
    }

    /**
     * A way to find out whether a value can be built at a boundary, or nothing where there is nothing
     * to build against.
     *
     * <p>The decoder a row's own fixture goes through, reached through the generator. It is the only
     * thing in the compiler that can answer the question — an invariant relating two fields refuses a
     * pair each field would have accepted alone — and it answers it one way: what it builds is a
     * witness, and what it refuses is a refusal of the candidates it tried.
     */
    interface Probe {

        /**
         * What building a row for {@code label} came to, with each position of the item fixed
         * where {@code fixing} puts it and the rest of the row built to reach the border, or null
         * where the attempt could not be made at all — which leaves the point unknown rather than
         * refused.
         *
         * <p>One method, whatever the border was drawn on. What the row is for is the coverage item
         * and what is fixed to build it is a placement that stands for it; a side of a border is met
         * by a row anywhere in it, so a row labelled by the places a search happened to compose
         * would name a witness as though it were the item.
         *
         * <p>What each position's number is measured on is not among the arguments. That is the
         * reading the row is composed against, which the generator holds; passed in, a caller would
         * be answering a question about where a term stands from wherever it found the term.
         *
         * @param reaching what the row has to be to arrive at the border at all. Handed in beside
         *                 the placement rather than left out: the placement is about the positions
         *                 the item names and a condition above the line is about the others, and a
         *                 row is one row
         */
        souther.compiler.partition.Generator.BoundaryAttempt attempt(
                String label,
                Map<souther.compiler.partition.RealizationTarget, Place> fixing,
                souther.compiler.partition.Reachability.Reaching reaching);

        /**
         * A composed row built and run, so that what it turned out to be can be asked.
         *
         * <p>The same reading a written row goes through ({@link RowAsRead}). A candidate is a row
         * nobody has been handed yet and is otherwise a row like any other, so what it stands at is
         * read by the walk that reads the file's rows and never by a second account written where
         * the candidate was composed.
         */
        RowAsRead read(java.util.List<souther.compiler.partition.FixtureTemplate> inputs);
    }

    /**
     * Everything known about each line one position's rules drew.
     *
     * <p>The one place either question about a boundary is answered. Whether a row sits at it is read
     * off this compilation's rows; whether a row could sit at it is settled by the projection where
     * that read every rule, and by building a value where it did not. Both used to be worked out twice
     * — once for the report and once for the rows the generator offers — under rules that did not
     * quite agree, so a line the report called undecided was one the generator handed to an author
     * anyway.
     *
     * <p>An invariant's bound is met by writing the value: outside it nothing can be constructed, so
     * the value is the whole of what there is to reach. A guard's is not — the comparison has to have
     * been evaluated, and a row can carry the exact value and never reach the guard that cares about
     * it because an earlier branch went the other way. Nothing measures that until the arms are
     * instrumented, so a guard's boundary is unmeasured rather than met or missed.
     *
     * <p>Whether the build asked for the arms at all and whether the run then managed to read them
     * are two questions, and {@code observed} answers the second — the two fail differently, and a
     * measure that took one answer for both could not say which had happened.
     *
     * <p>What a row had already satisfied when each comparison ran is threaded here as well as into
     * {@link #assessBetween}, because which shape of quantity a rule cuts says nothing about where a
     * row for it may be written — and a region put into one of the two paths would leave the other
     * searching over everything its position could ever hold. No model was found where it moves an
     * answer down this path, and no test holds it: the points of a line at one position all sit
     * beside the line, and every line tried that the region excludes turned out to be one
     * {@link souther.compiler.check.PathReachability} had already taken the obligation away for.
     * Whether those two always coincide is not established here — they are different readings — so
     * what this says is that a path is not left short of what it is owed, and not that the region is
     * idle here.
     */
    static List<BorderAssessment> assess(
            List<Border> lines, souther.compiler.partition.MeasuredInput subject,
            souther.compiler.query.Adequacy.RowReading observed,
            souther.compiler.query.Adequacy.Level level,
            ItemAssessment.WritabilityProjection projection,
            java.util.Optional<SiteNumbering> numbering) {
        // One entry per reading and not per line. A guard inside a non-recursive helper is read once
        // per call of that helper, and the rows do not owe the same border twice for having been
        // offered it twice — but each reading is reached under its caller's own conditions, so what
        // a row for it may be composed out of is the reading's and is settled while the readings are
        // still apart. They are brought together by {@link #merged}, after that.
        List<BorderAssessment> out = new ArrayList<>();
        for (Border each : lines) {
            out.add(assessed(each, reading(subject.at(each), projection), observed, level,
                    numbering));
        }
        return List.copyOf(out);
    }

    /**
     * The same lines, with what building a value at each point that is worth one came to.
     *
     * <p>Takes what was measured rather than measuring again. A search is evidence added to an
     * assessment and never a second one: the border, what it demands, whether a row is at it and
     * whether the rules prove it writable are all carried through untouched, and the only thing this
     * puts in is the attempt at the points the measurement itself says are worth an attempt.
     *
     * <p>Which is what makes composing later safe to do. What shows a point writable is read off the
     * evidence, and nothing a search finds is evidence against a point — so this can add a ground and
     * can never take one away, whenever it is run and however many points it is run over.
     */
    static LineReadings searched(LineReadings measured,
                                 souther.compiler.partition.MeasuredInput input,
                                 Probe probe, ReachingCuts reaching) {
        LevelRealizer realizer = new LevelRealizer();
        List<BorderAssessment> out = new ArrayList<>();
        for (BorderAssessment border : measured.each()) {
            OneSearchOfABorder search = searching(border.border(), input, probe, realizer,
                    wayTo(border.border(), reaching));
            java.util.Map<DomainPoint, ItemAssessment> items = new java.util.LinkedHashMap<>();
            for (DomainPoint point : border.items().keySet()) {
                ItemAssessment item = border.at(point);
                items.put(point, item instanceof ItemAssessment.Owed owed && owed.worthSearching()
                        ? owed.settledBy(search.search(owed.criterion(),
                                border.border().label(point)))
                        : item);
            }
            out.add(new BorderAssessment(border.border(), items));
        }
        return new LineReadings(out);
    }

    /**
     * How a row for one border came to be looked for where it is.
     *
     * <p>Beside the border and not part of it. What a border is is what the rows are owed at, and it
     * is the same border wherever a row for it is looked for — put inside, two readings of one line
     * reached under different conditions would be two obligations, and a count of what an author
     * owes would move with how much of a body this compiler managed to read.
     *
     * <p>Nothing on the way, for a line a declaration draws. An invariant is about the values and
     * holds wherever one stands, so there is nowhere for a row to have come from; a guard is at a
     * place in a body, and a row that never arrives there is no row at its line whatever it holds.
     * Read off which kind of rule it is rather than off whether anything was collected: a guard
     * nothing narrows and a clause then say the same thing for the same stated reason, and each says
     * it in its own account rather than by coming back with nothing.
     */
    private static souther.compiler.partition.WayToTheBorder wayTo(
            Border border, ReachingCuts reaching) {
        return border.origin().comparisonAt().map(reaching::wayTo)
                .orElse(souther.compiler.partition.WayToTheBorder.UNTOUCHED);
    }

    /**
     * What differs between the two shapes of border when a point of one is assessed.
     *
     * <p>Three answers and no more. How a row is read is one — at a position's own term, or at both
     * terms of a line between two — what a search does is another, and whether the rules alone prove
     * a row can be written at this border is the third. Everything else about a point is the same
     * either way, and writing it out per shape is what let one shape acquire a rule the other had:
     * the gates before a search were asked twice and a third search went in beside them without
     * them.
     */
    private interface OneShapeOfBorder {

        /** Whether one of {@code rows} meets {@code criterion}, and whether that could be told. */
        StandingAtAPoint.Met met(Criterion criterion, List<ObservedInputs> rows);

        /** What reading the rules this reading took in established about a row being writable at
         *  this border. Three answers rather than two: a shape whose rules were never put the
         *  question is not one whose rules failed to answer it. */
        ItemAssessment.WritabilityProjection projection();
    }

    /** What building a row at one point of a border comes to. Its own interface beside the reading
     *  above, because searching is not measuring: one is what this compilation already established
     *  and the other is work somebody asked for. */
    private interface OneSearchOfABorder {

        /** What building a row at it came to, asked only where one is worth building. */
        ItemAssessment.Attempt search(Criterion criterion, String label);
    }

    /**
     * What each of one border's four points came to.
     *
     * <p>Every role, and the ones nobody is owed a row in carry the border's own reason rather than
     * being left out. What a row has to do is the criterion's ({@link Criterion}); what it has to do
     * <em>beyond</em> that is the border's — a line a fork of a body drew is met by getting the
     * comparison to answer as well as by writing the value, and that holds of all four of its points.
     * Read off the role instead, an {@code IN} point of a guard would be met by a row that never
     * reached the guard.
     *
     * <p>One place, for both shapes of border. Which of the four a build is told about, whether a
     * point is worth building a candidate for, and what a point that owes no row carries are rules
     * about a point and not about a shape, so a shape added later inherits them rather than being
     * trusted to repeat them.
     */
    private static BorderAssessment assessed(Border border, OneShapeOfBorder shape,
                                             souther.compiler.query.Adequacy.RowReading observed,
                                             souther.compiler.query.Adequacy.Level level,
                                             java.util.Optional<SiteNumbering> numbering) {
        // Whether meeting this border takes the comparison having run, asked of the rule rather than
        // read off which kind it is, and asked once for the border rather than once per point. A
        // guard's line is about a place in a body and is reached or not; an invariant's and a
        // clause's are about the values — one refuses everything outside its bound, the other states
        // a relation — so for both of those writing the value is the whole of what there is to reach.
        boolean guard = border.origin().comparisonAt().isPresent();
        Measurement<ItemAssessment.Coverage> absent =
                whyNothingWasReadAgainstTheLine(guard, observed, level);

        // The rows as the values they hold and what running them recorded, which is the whole of
        // what a point is met by. Read once for the border: what a row is stays the same however
        // many of the four points it is put to.
        List<ObservedInputs> rows = observed.rowsSeen().stream()
                .map(row -> ObservedInputs.of(row, numbering)).toList();

        java.util.Map<DomainPoint, ItemAssessment> items = new java.util.LinkedHashMap<>();
        for (DomainPoint point : border.answers().keySet()) {
            items.put(point, switch (border.demand(point)) {
                case Demand.NotOwed not -> new ItemAssessment.NotOwed(not.reason());
                case Demand.Owed owed -> {
                    Measurement<ItemAssessment.Coverage> coverage = absent != null ? absent
                            : verdictOf(shape.met(owed.criterion(), rows), guard,
                                    border, observed);
                    // No search. Nothing was searched for here, and that is said by there being no
                    // search rather than by a search saying nobody asked: whether a value was
                    // composed is a fact about who asked for one, and a measurement that carried it
                    // was answering a question it had not been put.
                    yield new ItemAssessment.Owed(owed.criterion(), coverage,
                            shape.projection(), SearchOutcomes.none());
                }
            });
        }
        return new BorderAssessment(border, items);
    }

    /**
     * One border, read on whatever it was drawn on.
     *
     * <p>The one reading. Whether a row sits at an item is the quantity's answer ({@link
     * BorderQuantity#standsAt}), where a row would have to stand is the quantity's too
     * ({@link BorderQuantity#standingAt}) and finding one there is the realizer's — so nothing here
     * asks which kind of line this is. Two readings written apart is what left a criterion about one
     * place reaching the reader of a pair as an {@code IllegalStateException}.
     */
    private static OneShapeOfBorder reading(
            souther.compiler.partition.MeasuredInput.BorderReading line,
            ItemAssessment.WritabilityProjection projection) {
        List<ComparisonEmissionSite> site =
                line.border().origin().recordedAt();
        return new OneShapeOfBorder() {

            @Override
            public StandingAtAPoint.Met met(Criterion criterion, List<ObservedInputs> rows) {
                return StandingAtAPoint.met(line, rows, criterion, site);
            }

            @Override
            public ItemAssessment.WritabilityProjection projection() {
                return projection;
            }
        };
    }

    /**
     * The readings of one behavior's lines, one entry per line.
     *
     * <p>What each reading saw, kept whole: a point one reading found a row at is found, and no
     * other reading of the line takes that back.
     *
     * <p><b>After everything that is a reading's own.</b> Which conditions a row has to satisfy to
     * reach the comparison is one of those, so a search is made per reading and the searches are
     * put together here rather than one being made against whichever reading came first. Put
     * together first, the region a row is composed in is one reading's, chosen by the order a walk
     * took.
     *
     * <p><b>And never across two debts.</b> A line holds the authored line and where it was read;
     * a debt holds the authored line and the value it is at — so two readings under one line are one
     * debt by construction, and a pair that is not says the two identities have come apart.
     */
    static List<BorderAssessment> merged(LineReadings readings) {
        java.util.SequencedMap<BoundaryLine, BorderAssessment> out = new java.util.LinkedHashMap<>();
        for (BorderAssessment each : readings.each()) {
            out.merge(BoundaryLine.of(each.border()), each, Coverages::asOneLine);
        }
        return List.copyOf(out.values());
    }

    /**
     * How a row is looked for at one border, on whatever it was drawn on.
     *
     * <p>Beside {@link #reading} rather than inside it. Both are about one border and neither is the
     * other: what the rows already established is read whatever anybody asked for, and building a
     * value is work somebody asked for and pays for.
     */
    private static OneSearchOfABorder searching(Border border,
                                                souther.compiler.partition.MeasuredInput input,
                                                Probe probe, LevelRealizer realizer,
                                                souther.compiler.partition.WayToTheBorder within) {
        souther.compiler.partition.MeasuredInput.BorderReading line = input.at(border);
        souther.compiler.inputs.Quantities rules = input.quantities();
        BorderQuantity quantity = line.quantity();
        List<ComparisonEmissionSite> site =
                border.origin().recordedAt();
        // Built here and gone when the search is. What a row has to be to arrive is a way of asking
        // about values rather than something that says what it is, so it is what the walk runs
        // against and never what the answer keeps; the account it was built from is what travels.
        souther.compiler.partition.Reachability reaching =
                souther.compiler.partition.Reachability.of(within, rules.region());
        return new OneSearchOfABorder() {

            @Override
            public ItemAssessment.Attempt search(Criterion criterion, String label) {
                // Nothing to build against. Told apart from nobody having asked, which is not a
                // state anything here can be in: this runs because somebody asked.
                if (probe == null) {
                    return new ItemAssessment.Attempt.Unavailable(
                            ItemAssessment.Attempt.Reason.NO_CLASSES);
                }
                // A way one position would have to take two of its cases to reach, which no value
                // is. Said in that word and not in the one for a walk that tried what the rules
                // leave and reached nothing: nothing was walked here, and what settles it is that
                // the two cases are not in one value.
                if (!(reaching instanceof souther.compiler.partition.Reachability.Reaching able)) {
                    return new ItemAssessment.Attempt.Unresolved(
                            new souther.compiler.partition.Generator.UnresolvedCombination(
                                    java.util.List.of(label),
                                    souther.compiler.partition.Generator.UnresolvedCombination
                                            .Reason.ONE_POSITION_CANNOT_BE_BOTH), within);
                }
                // Where a row would have to stand is asked of the quantity, and finding one there of
                // the realizer. What it composes is a candidate and no part of the item: another row
                // in the same side is at the point as much as this one would be, so what the row is
                // offered for goes in beside it rather than being read back off it.
                return switch (realizer.realize(quantity.standingAt(criterion), able.region())) {
                    case Realization.Found found -> {
                        souther.compiler.partition.Generator.BoundaryAttempt made =
                                probe.attempt(label, found.fixing(), able);
                        yield whatCameOfIt(made, label, within,
                                () -> standingThere(probe, line, criterion, site,
                                        (souther.compiler.partition.Generator.BoundaryAttempt.Built)
                                                made));
                    }
                    // And the two ways of finding nothing are not one answer. A walk of the whole
                    // of what the rules leave that reaches no value settles the point; a search
                    // that stopped, or one that composed no candidate at all, settles nothing
                    // (ADR-0091).
                    case Realization.Impossible _ -> new ItemAssessment.Attempt.Unresolved(
                            new souther.compiler.partition.Generator.UnresolvedCombination(
                                    java.util.List.of(label),
                                    souther.compiler.partition.Generator.UnresolvedCombination
                                            .Reason.THE_RULES_LEAVE_NOTHING_THERE), within);
                    // A walk that reached no placement. Where a budget of this compiler's is why it
                    // reached none, that travels: the point is one this declined to look further
                    // for, which is not the point being one nothing promises.
                    case Realization.Unknown unknown -> unknown.stoppedBy().isEmpty()
                            ? new ItemAssessment.Attempt.Unresolved(
                                    new souther.compiler.partition.Generator.UnresolvedCombination(
                                            java.util.List.of(label), wordOf(unknown)), within)
                            : new ItemAssessment.Attempt.Stopped(
                                    new souther.compiler.partition.Generator.UnresolvedCombination(
                                            java.util.List.of(label), wordOf(unknown)),
                                    within, java.util.List.of(),
                                    PublicationOrders.COMPOSITION_BUDGETS.keep(unknown.stoppedBy()),
                                    PublicationOrders.COMPOSITION_REPERTOIRES.keep(
                                            java.util.List.of()));
                };
            }
        };
    }

    /**
     * Two readings of one line, as the one line they are readings of.
     *
     * <p><b>Nothing is chosen between.</b> A helper called from two places is one line read once
     * and searched twice — the authored line and the target are the same, and what differs is the
     * region a row for it was composed in — so the two are put together and neither stands for the
     * other. Kept as whichever saw more, whatever the other established was gone before anything
     * downstream could ask, and a point whose only search a budget stopped came out holding nothing.
     *
     * <p>Point by point rather than border by border. Two readings of one line can have seen
     * different things at different points, and one answer for the whole border would settle a
     * point from what happened at another.
     */
    private static BorderAssessment asOneLine(BorderAssessment a, BorderAssessment b) {
        if (!a.border().obligation().equals(b.border().obligation())) {
            throw new IllegalStateException("two readings of one line owing different rows: "
                    + a.border().obligation() + " and " + b.border().obligation());
        }
        java.util.Map<DomainPoint, ItemAssessment> kept = new java.util.LinkedHashMap<>();
        for (DomainPoint point : a.items().keySet()) {
            kept.put(point, together(a.at(point), b.at(point)));
        }
        return new BorderAssessment(a.border(), kept);
    }

    /**
     * What two searches of one point of one reading come to, dimension by dimension.
     *
     * <p><b>Not one of them.</b> They are searches of the same point under one reading, and the
     * point is owed once — so what a reader is owed is what both of them found out. Taking whichever
     * saw more, every fact the other one established was gone before anything downstream could ask:
     * a search a budget of this compiler's stopped, dropped for one that came back with nothing, is
     * how a point this declined to work on left the count as one the model admits no row at.
     *
     * <p>Each dimension by whatever owns it. What the rows came to is coverage's own question
     * ({@link ObligationCoverage#acrossOneReadingsSearches}); what the rules prove is a reading of
     * the declarations and the two searches read the same ones, so a difference there is not
     * something to fold but something that has gone wrong.
     */
    private static ItemAssessment together(ItemAssessment a, ItemAssessment b) {
        if (!(a instanceof ItemAssessment.Owed one) || !(b instanceof ItemAssessment.Owed two)) {
            // Two readings of one line owe the same points, so a point no row is owed at is that
            // under both — and there is nothing about it to put together.
            return a;
        }
        if (!one.criterion().equals(two.criterion())) {
            throw new IllegalStateException("two searches of one point asking for different"
                    + " values: " + one.criterion() + " and " + two.criterion());
        }
        if (one.projection() != two.projection()) {
            throw new IllegalStateException("two searches of one point disagreeing about what the"
                    + " rules prove there: " + one.projection() + " and " + two.projection());
        }
        return new ItemAssessment.Owed(one.criterion(),
                ObligationCoverage.acrossOneReadingsSearches(one.coverage(), two.coverage()),
                one.projection(), one.searches().plus(two.searches()));
    }

    /**
     * A candidate the walk that reads a row at a point was seen to stand there, or a search that
     * composed a row and did not.
     *
     * <p><b>The one thing that says a row is at a point.</b> {@link StandingAtAPoint} answers it for
     * the rows in the file and for the rows a person is being offered, so a candidate goes through
     * it before it is offered rather than after somebody has been handed it. What a search knows
     * while it is composing is narrower — that each position it fixed reads back at the place it was
     * fixed at — and a row can pass that and turn back above the comparison, which is exactly the
     * disagreement this removes: the generator and the reading are no longer two accounts of one
     * model, because the generator asks the reading.
     *
     * <p><b>Where it fails open, and where it does not.</b> The two halves of standing at a point
     * are not the same question. Whether the values are at the level is read off the row and is
     * this compiler's to answer; whether the comparison was answered takes something having watched
     * the run, which is not. So a row nothing watched is offered — the values were seen at the
     * level and the rest is a thing nobody could put a question about — and a row whose values
     * could not be read at the point is not: that is the walk unable to confirm the half this
     * compiler owns, of a value this compiler has just built, which is the two readings coming
     * apart in the one place this exists to close.
     *
     * <p>So what holds of an offered row is that its values were seen at the point, and the run
     * either reached the comparison or was not watched. Kept open at both, an answer that used to
     * be a disagreement could go on being one under another name, and the measure would go green
     * over it.
     *
     * <p>An answer about this search and never about the model. A row composed here that does not
     * stand at the point says the way to the border was not composed against in full; the point is
     * owed the same row it was owed before, and nothing here says one cannot be written.
     */
    private static StandingAtAPoint.Met standingThere(
            Probe probe, souther.compiler.partition.MeasuredInput.BorderReading line,
            Criterion criterion,
            List<ComparisonEmissionSite> site,
            souther.compiler.partition.Generator.BoundaryAttempt.Built built) {
        souther.compiler.partition.ObservedInputs read =
                probe.read(built.row().inputs()).asInputs();
        if (read == null) {
            // Nothing came back to read the row off, which is not an observation of it and is not a
            // position holding no value either. What did not happen here is the running: the values
            // would not build a second time, or the model refused them, or the classes would not
            // link — and every one of those is something this run did rather than a value a limit
            // shortened or a place a row wrote nothing at. Named as the first, a linkage failure
            // arrives at the account as an observation that was stopped and the report says a limit
            // did something that never fired; named as the second, it says the row put nothing
            // where nothing ever looked.
            return new StandingAtAPoint.Met.CouldNotTell(
                    Set.of(souther.compiler.partition.ReadingGap.COULD_NOT_READ_ROW));
        }
        return StandingAtAPoint.met(line, List.of(read), criterion, site);
    }

    /** Whether an observation is among the reasons a reading came to nothing. */
    private static boolean observed(StandingAtAPoint.Met.CouldNotTell why) {
        return why.why().stream()
                .anyMatch(each -> each instanceof souther.compiler.partition.ReadingGap.Observation);
    }

    /**
     * The observations among them, which is what a gap about an observation may hold.
     *
     * <p>Only those. A walk that reached no value is a reason the reading came to nothing and is
     * not a thing an observation did, so it travels as far as the account's own reasons go and no
     * further.
     */
    private static EstablishmentGap.Observation stopped(StandingAtAPoint.Met.CouldNotTell why) {
        Set<Incompleteness.Code> codes = new LinkedHashSet<>();
        for (souther.compiler.partition.ReadingGap each : why.why()) {
            if (each instanceof souther.compiler.partition.ReadingGap.Observation it) {
                codes.add(it.code());
            }
        }
        return EstablishmentGap.Observation.of(codes);
    }

    /**
     * The word a walk that reached no placement comes back with.
     *
     * <p>The realizer's own, kept as it is. What it says is which of the two things a walk that
     * found nothing did, and it says it whether or not a figure of this compiler's was reached —
     * which is why the figure travels beside it rather than being read out of it.
     */
    private static souther.compiler.partition.Generator.UnresolvedCombination.Reason wordOf(
            Realization.Unknown unknown) {
        return switch (unknown.why()) {
            case NOTHING_COMPOSED_ONE -> souther.compiler.partition.Generator
                    .UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE;
            case THE_SEARCH_LEFT_SOMETHING_UNTRIED -> souther.compiler.partition.Generator
                    .UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED;
        };
    }

    /**
     * What a search of the module's own decoders came to, in this measure's words.
     *
     * <p>{@code within} travels as far as the answer does. The region is what the search ran in,
     * and a run that composed a row and never saw it arrive is the answer that most needs it — the
     * candidate came out of this box, and a condition on the way that nothing represented in the
     * box is a thing an author would want to know at exactly that point.
     *
     * <p>Including where the decoders could not be reached. This is asked only of a placement the
     * realizer found, so the region was walked and a candidate came out of it before anything was
     * run — which makes it a search that came to nothing rather than a search nobody made, and it
     * carries what it was looking over like the rest. Filed as one nobody made, it was the one
     * outcome of a search that dropped its region, and the reader that wanted it back built the
     * same value by hand a moment later.
     */
    private static ItemAssessment.Attempt whatCameOfIt(
            souther.compiler.partition.Generator.BoundaryAttempt made, String subject,
            souther.compiler.partition.WayToTheBorder within,
            Supplier<StandingAtAPoint.Met> standing) {
        return switch (made) {
            case null -> new ItemAssessment.Attempt.Unresolved(
                    new souther.compiler.partition.Generator.UnresolvedCombination(
                            List.of(subject),
                            souther.compiler.partition.Generator.UnresolvedCombination.Reason
                                    .LINKAGE_FAILED), within);
            // The way as the walk left it, and beside it what the composer could not act on. Two
            // stages and two answers: a condition the walk stated stays stated, and writing the
            // composer's own failure onto the way would have one condition wearing two of the
            // walk's words.
            //
            // And the row read back, which is where a composed value becomes a value at the point
            // or stops short of being one. The three answers of that reading are the three this
            // hands on, and this is the only place they are turned into what a search came to.
            case souther.compiler.partition.Generator.BoundaryAttempt.Built built ->
                    switch (standing.get()) {
                        case StandingAtAPoint.Met.AtPoint _ ->
                                new ItemAssessment.Attempt.Certified(built.row(), within,
                                        built.unrepresented());
                        // Composed and not seen where it was composed for. Which says the way to
                        // the border was not composed against in full and never that the point is
                        // unwritable, and it carries what the composer could not act on because
                        // that is the first thing that would explain it.
                        case StandingAtAPoint.Met.NotAtPoint _ ->
                                new ItemAssessment.Attempt.Unresolved(
                                        new souther.compiler.partition.Generator
                                                .UnresolvedCombination(List.of(subject),
                                                souther.compiler.partition.Generator
                                                        .UnresolvedCombination.Reason
                                                        .NO_CERTIFIED_WITNESS),
                                        within, built.unrepresented());
                        // And a reading that could not look is not a reading that looked. The value
                        // was built and the decoders took it; what did not happen is the reading
                        // back. Said as the answer above, a point a value was just built at is
                        // reported as one nothing can write a row at.
                        // And only the observations among the reasons make an observation's gap. A
                        // walk that reached no value made no observation, so there is none to have
                        // been stopped, and a gap built from it would put a limit's name on
                        // something no limit did. Where that is all there was, what this run did is
                        // the search's own to say, in the generator's words.
                        case StandingAtAPoint.Met.CouldNotTell it ->
                                observed(it) ? new ItemAssessment.Attempt.Unverified(built.row(),
                                        within, built.unrepresented(), stopped(it))
                                        : new ItemAssessment.Attempt.Unresolved(
                                                new souther.compiler.partition.Generator
                                                        .UnresolvedCombination(List.of(subject),
                                                        souther.compiler.partition.Generator
                                                                .UnresolvedCombination.Reason
                                                                .NO_CERTIFIED_WITNESS),
                                                within, built.unrepresented());
                    };
            case souther.compiler.partition.Generator.BoundaryAttempt.Unresolved left ->
                    new ItemAssessment.Attempt.Unresolved(left.why(), within,
                            left.unrepresented());
            // And a search a budget of this compiler's ended, which is the one outcome here that
            // names something anybody could raise. Said as the one above, an obligation this
            // declined to work on left the count as one the model admits no row at.
            case souther.compiler.partition.Generator.BoundaryAttempt.Stopped left ->
                    new ItemAssessment.Attempt.Stopped(left.why(), within, left.unrepresented(),
                            PublicationOrders.COMPOSITION_BUDGETS.keep(left.by()),
                            PublicationOrders.COMPOSITION_REPERTOIRES.keep(left.notAllOf()));
            // A search that ran to the end of what this compiler writes, where that is not the end
            // of what there is to write. It leaves the point open the way the one above does and
            // names nothing anybody could raise, which is why it arrives as its own arm and its
            // gap holds a vocabulary of its own.
            case souther.compiler.partition.Generator.BoundaryAttempt.Unexhausted left ->
                    new ItemAssessment.Attempt.Unexhausted(left.why(), within,
                            left.unrepresented(),
                            PublicationOrders.COMPOSITION_REPERTOIRES.keep(left.writes()));
            // A search that ran to the end of what it was handed, where what it was handed was
            // short of the point. It names a figure like the one above and its word is its own, so
            // the two are carried side by side rather than one being read off the other.
            case souther.compiler.partition.Generator.BoundaryAttempt.Limited left ->
                    new ItemAssessment.Attempt.Limited(left.why(), within, left.unrepresented(),
                            PublicationOrders.COMPOSITION_BUDGETS.keep(left.by()));
            // And a point no search was made for at all. It names a figure like the two above and
            // is not an outcome of a search, which is what keeps it out of what the readings of a
            // line together establish.
            case souther.compiler.partition.Generator.BoundaryAttempt.Unplanned left ->
                    new ItemAssessment.Attempt.Unplanned(left.why(), within, left.unrepresented(),
                            PublicationOrders.COMPOSITION_BUDGETS.keep(left.by()));
        };
    }

    /**
     * What was established about each line a body draws between two of its positions.
     *
     * <p>Beside the lines an axis carries rather than among them. A line between two positions is on
     * neither of them, so there is no axis to hang it off — and a behavior can have one while having
     * no axis at all, which is every model whose inputs are plain numbers nothing bounds.
     *
     * <p>Nothing is promised of one yet, and the projection says so in the one word for it. Whether
     * a row can be written on the line takes a place both positions admit, and reading each position
     * on its own does not answer that: two ranges overlapping is not two positions holding a pair,
     * and what refuses the pair need not be in either range. So the reading is not made rather than
     * made and coming to nothing, and the line is one nothing has shown to be writable — reported and
     * not counted, the same account any other unpromised edge gets.
     */
    static List<BorderAssessment> assessBetween(
            souther.compiler.partition.MeasuredInput subject,
            souther.compiler.query.Adequacy.RowReading observed,
            souther.compiler.query.Adequacy.Level level,
            java.util.Optional<SiteNumbering> numbering) {
        Partitions.Partitioning partitioning = subject.partitioning();
        // One entry per reading, the way a line at a place is read: what several readings of one
        // line come to is one answer, and it is put together where the last thing that is a
        // reading's own has been asked ({@link #merged}).
        List<BorderAssessment> out = new ArrayList<>();
        for (Border each : partitioning.between()) {
            out.add(assessed(each, reading(subject.at(each),
                            ItemAssessment.WritabilityProjection.NOT_COMPUTED),
                    observed, level, numbering));
        }
        return List.copyOf(out);
    }

    /**
     * What a reading of the rows comes to, once what could not be read is accounted for.
     *
     * <p>A row standing where the line is that nothing watched reach the comparison is counted here
     * as a row that did not meet the point. It is a miss the measure can hide: what the rows went
     * without is said below, and a run nobody watched is one of the things it says. So the
     * distinction the walk makes is not carried further — a hit needs a row seen reaching the
     * comparison, and everything else is answered by how whole the reading was.
     */
    private static Measurement<ItemAssessment.Coverage> verdictOf(
            StandingAtAPoint.Met met, boolean guard, souther.compiler.partition.Border border,
            souther.compiler.query.Adequacy.RowReading observed) {
        if (met instanceof StandingAtAPoint.Met.Reached) {
            // Found is found: a row settles this whatever else went unread, so nothing weakens it.
            return new Measurement.Complete<>(new ItemAssessment.Coverage.Hit());
        }
        // What is not found is undecided rather than absent, wherever the reading behind it was not
        // whole — and now says which reading it was. A row whose value here could not be read; a row
        // nothing read at all, which may be the row that is at this value; and, for a line a fork
        // drew, a row that never finished and so never reached the comparison.
        Set<Weakening> by = new LinkedHashSet<>();
        // One per reason, each in its own words. What the rows leave open is the same whichever it
        // was — no row of theirs settles the point — and why there was nothing to read is not, so
        // every reason travels even where this block treats them alike.
        if (met instanceof StandingAtAPoint.Met.CouldNotTell it) {
            for (souther.compiler.partition.ReadingGap why : it.why()) {
                by.add(new Weakening.BorderValueUnreadable(border, why));
            }
        }
        for (Incompleteness.Met gap : observed.gaps()) {
            // Rows nothing read at all bear on every line. Rows that were read and did not finish
            // bear on a line a fork drew and on no other: meeting one takes the comparison having
            // run, which a row that stopped never reached.
            //
            // Read off the reasons rather than off the dispositions beside them. What a row that
            // stopped costs a measure is said once, where the row stopped (`ExampleVerifier`), and
            // a second reading here was a second statement of it that could differ (issue #996).
            if (gap.fact().code().leftNoRowRead()
                    || (guard && gap.fact().scope() == Incompleteness.Scope.ROW)) {
                by.add(new Weakening.ObservationIncomplete(gap));
            }
        }
        ItemAssessment.Coverage seen = new ItemAssessment.Coverage.NoHit();
        return by.isEmpty() ? new Measurement.Complete<>(seen)
                : new Measurement.Partial<>(seen, WeakeningSet.ofAll(by));
    }

    /**
     * Why nothing was read against a line at all, or null where the rows are what answer it.
     *
     * <p>The gates, behind one name. Which of them a line goes through is settled by the level the
     * build asked for and by whether a fork or an invariant drew it, and that is one decision with
     * one owner — a caller writing the choice out again, and a check enumerating what a reading can
     * come to, would be two more statements of it, free to say what this stopped saying.
     */
    static Measurement<ItemAssessment.Coverage> whyNothingWasReadAgainstTheLine(
            boolean drawnByAFork,
            souther.compiler.query.Adequacy.RowReading observed,
            souther.compiler.query.Adequacy.Level level) {
        return drawnByAFork ? whyNoGuardLine(observed, level) : whyNoInvariantLine(observed, level);
    }

    /**
     * The first gate a {@code guard}'s line did not get through.
     *
     * <p>Its own path, because a guard's line and an invariant's are not measured the same way and so
     * cannot fail to be measured for the same reasons. Meeting this one takes the comparison having
     * run, which puts the arms in front of the rows: nothing a row carries decides it until the
     * classes that record where the row went exist and survived.
     */
    private static Measurement<ItemAssessment.Coverage> whyNoGuardLine(
            souther.compiler.query.Adequacy.RowReading observed,
            souther.compiler.query.Adequacy.Level level) {
        Measurement<ItemAssessment.Coverage> nobodyAsked = whyNothingWasAsked(level);
        if (nobodyAsked != null) {
            return nobodyAsked;
        }
        if (!level.runsInstrumentedRows()) {
            return new Measurement.NotMeasured<>(ItemAssessment.Coverage.NotAsked.ARMS_NOT_ASKED);
        }
        if (observed.armsUnseen()) {
            // Started and not finished, so it says what it went without.
            Set<Weakening> by = new LinkedHashSet<>();
            for (Incompleteness.Met gap : observed.gaps()) {
                by.add(new Weakening.ObservationIncomplete(gap));
            }
            return new Measurement.FailedToMeasure<>(
                    ItemAssessment.Coverage.CouldNotAsk.ARMS_UNREADABLE, WeakeningSet.ofAll(by));
        }
        return whyNoInvariantLine(observed, level);
    }

    /** What every line of every kind says where the build asked for no measurement: the rules drew
     *  it, and nothing was read against it. Asked before either path below, because neither of them
     *  is about a run that did not happen. */
    private static Measurement<ItemAssessment.Coverage> whyNothingWasAsked(
            souther.compiler.query.Adequacy.Level level) {
        return level.readsRows() ? null
                : new Measurement.NotMeasured<>(ItemAssessment.Coverage.NotAsked.NOT_ASKED);
    }

    /**
     * The first gate an invariant's line did not get through.
     *
     * <p>Only the one: nothing outside the bound can be constructed, so writing the value is the
     * whole of what there is to reach and no instrumentation is owed. This is why the two origins
     * are asked separately — an invariant's line can never be waiting on the arms, and a measure
     * that could say so would be able to say something that is not true of it.
     */
    private static Measurement<ItemAssessment.Coverage> whyNoInvariantLine(
            souther.compiler.query.Adequacy.RowReading observed,
            souther.compiler.query.Adequacy.Level level) {
        Measurement<ItemAssessment.Coverage> nobodyAsked = whyNothingWasAsked(level);
        if (nobodyAsked != null) {
            return nobodyAsked;
        }
        List<RowOutcome> rows = observed.rowsSeen();
        boolean someRowsUnseen = observed.someRowsUnseen();
        // Nothing read is not the same as nothing written. A source that could not be evaluated may
        // hold the row that is at this line, so the question is undecided rather than unasked, and
        // the reading below settles it that way.
        return rows.isEmpty() && !someRowsUnseen
                ? new Measurement.NotMeasured<>(ItemAssessment.Coverage.NotAsked.NO_ROWS) : null;
    }


    private Coverages() {}

}
