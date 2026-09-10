package souther.compiler.query;

import souther.compiler.check.Sig;
import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.execute.BoundaryValues;
import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.partition.ClassOfAPosition;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.partition.Generator;
import souther.compiler.partition.InputClassifications;
import souther.compiler.partition.ObservedInputs;
import souther.compiler.partition.StandingAtAPoint;
import souther.compiler.observe.Classification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * What each row a run offers would do about each thing the run was asked to offer a row for.
 *
 * <p>The whole table and not the diagonal. A row is composed for one thing, and what else it turns
 * out to answer is the question this exists to put — so every row is asked about every item, and a
 * row's own item is one entry of its column like any other.
 *
 * <p><b>The items are what was asked for.</b> They come from the plan a run was made with and from
 * the points its searches were put, never from what the rows say they were composed for: a row
 * carries the classes and arms it may be named after and never a line, so an item universe read off
 * the rows would be missing every line in the block.
 *
 * <p>Nothing here is a measurement. Each entry says what would follow if the row were written, and
 * the rows are questions nobody has answered yet.
 */
public record Settlements(List<ObligationIdentity> requested,
                          SequencedMap<ObligationIdentity, RowKey> composedFor,
                          SequencedMap<RowKey, Map<ObligationIdentity, Settlement>> byRow) {

    public Settlements {
        requested = List.copyOf(requested);
        composedFor = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(composedFor));
        byRow = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(byRow));
    }

    /** What the row {@code rowKey} addresses would do about {@code item}, for a reader holding
     *  both. */
    public Settlement at(RowKey rowKey, ObligationIdentity item) {
        Map<ObligationIdentity, Settlement> here = byRow.get(rowKey);
        if (here == null || !here.containsKey(item)) {
            throw new IllegalArgumentException("no entry for " + rowKey + " at " + item);
        }
        return here.get(item);
    }

    /**
     * What a set of rows puts in front of a person for one item.
     *
     * <p>Two ways, and a reduction has to keep both. A row that settles the item answers it whoever
     * it was composed for; and the row composed <em>for</em> the item is what a person was offered
     * for it, whether or not this walk can tell that it settles it — a row whose reading came back
     * undetermined is still the one piece of work anybody was handed there.
     *
     * <p>Written once because it is what {@link #keeping()} preserves. Said as two rules in two
     * places, the second is the one a later reader drops as an oversight.
     */
    public boolean offers(Set<RowKey> rowKeys, ObligationIdentity item) {
        for (RowKey rowKey : rowKeys) {
            if (byRow.get(rowKey).get(item).settles()) {
                return true;
            }
        }
        return rowKeys.contains(composedFor.get(item));
    }

    /**
     * The rows to keep: every one whose going would cost the offering something.
     *
     * <p>What is preserved is {@link #offers}, for every item. So the result holds, of the rows
     * {@code R*} it comes back with and the rows {@code R} it was given:
     *
     * <ol>
     *   <li>every item {@code R} offers something for, {@code R*} offers something for;</li>
     *   <li>no row of {@code R*} can go and leave that true.</li>
     * </ol>
     *
     * <p><b>Which is not "every row left settles something nothing else does".</b> A row kept by the
     * second half of {@code offers} settles nothing this could tell about — it is there because it
     * is the only thing composed for its item — and a reduction written to the shorter sentence
     * would drop it and take that item's only offer with it.
     *
     * <p>Nor is it the smallest set: a different, smaller set of rows may offer for the same items.
     * Irredundant is what this is, and finding a minimum is a different question.
     *
     * <p>Only {@link Settlement.Settles} counts as settling. A row that cannot be told about is not
     * a row that answers, and counting it would drop the row that did.
     *
     * <p>Walked from the back, so a row that came first stays. Which of two rows answering the same
     * things a person is handed is arbitrary, and taking the earlier one keeps the block steady:
     * the order rows are composed in is the order the searches were asked, and an edit somewhere
     * later in the model does not move what is offered above it.
     */
    public Set<RowKey> keeping() {
        Map<ObligationIdentity, Integer> count = new LinkedHashMap<>();
        for (ObligationIdentity item : requested) {
            int settling = 0;
            for (Map<ObligationIdentity, Settlement> here : byRow.values()) {
                if (here.get(item).settles()) {
                    settling++;
                }
            }
            count.put(item, settling);
        }
        // What each row was composed for, the way round this asks it. Read out of the map the other
        // way for every row, the walk would go over every item once per row to find the few that
        // name it.
        Map<RowKey, List<ObligationIdentity>> composedHere = new LinkedHashMap<>();
        composedFor.forEach((item, rowKey) ->
                composedHere.computeIfAbsent(rowKey, _ -> new ArrayList<>()).add(item));
        List<RowKey> inOrder = new ArrayList<>(byRow.keySet());
        Set<RowKey> kept = new LinkedHashSet<>(inOrder);
        for (int at = inOrder.size() - 1; at >= 0; at--) {
            RowKey rowKey = inOrder.get(at);
            if (goes(rowKey, composedHere.getOrDefault(rowKey, List.of()), count)) {
                kept.remove(rowKey);
                byRow.get(rowKey).forEach((item, settlement) -> {
                    if (settlement.settles()) {
                        count.merge(item, -1, Integer::sum);
                    }
                });
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(
                inOrder.stream().filter(kept::contains).toList()));
    }

    /**
     * Whether the offering offers as much without the row {@code rowKey} addresses as with it —
     * {@link #offers} for every item, read off the counts rather than walked again.
     *
     * <p>The counts hold the kept rows and this one among them. So an item this row settles needs a
     * second settler to be left after it goes, and an item it was composed for and settles nothing
     * of needs one at all: without a settler, taking the row away takes the item's only offer.
     *
     * @param composedHere what this row was composed for
     */
    private boolean goes(RowKey rowKey, List<ObligationIdentity> composedHere,
                         Map<ObligationIdentity, Integer> count) {
        Map<ObligationIdentity, Settlement> here = byRow.get(rowKey);
        for (Map.Entry<ObligationIdentity, Settlement> each : here.entrySet()) {
            if (each.getValue().settles() && count.get(each.getKey()) <= 1) {
                return false;
            }
        }
        // And what was composed for it. An item whose own row settles nothing this could tell about
        // is one nobody would be offered a row for at all, which is a piece of work going missing
        // rather than a row being said once instead of twice.
        for (ObligationIdentity item : composedHere) {
            if (!here.get(item).settles() && count.get(item) < 1) {
                return false;
            }
        }
        return true;
    }

    /** The items some row of this offering settles, which is what a reduction has to keep answered. */
    public Set<ObligationIdentity> settled() {
        Set<ObligationIdentity> out = new LinkedHashSet<>();
        for (ObligationIdentity item : requested) {
            for (Map<ObligationIdentity, Settlement> here : byRow.values()) {
                if (here.get(item).settles()) {
                    out.add(item);
                    break;
                }
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * The table for one offering.
     *
     * <p>Read with the same walk a written row is read with. Where a candidate's values sit is
     * {@link InputClassifications}'s answer, whether it stands at a line is
     * {@link StandingAtAPoint}'s, and which arms it takes is what running it recorded — the three
     * questions a measurement puts to the rows in a file, put here to the rows a person is being
     * handed.
     */
    public static Settlements of(Db db, Composition offering) {
        String module = offering.request().module();
        Map<String, Sig> sigs = db.ask(new Bodies.Signatures(module)).value();
        BoundaryValues building = Adequacy.constructing(db, module);
        souther.compiler.execute.RowTrials trials = Adequacy.trialling(db, module);
        List<ObligationIdentity> requested = new ArrayList<>();
        SequencedMap<ObligationIdentity, RowKey> composedFor = new LinkedHashMap<>();
        SequencedMap<RowKey, Map<ObligationIdentity, Settlement>> byRow = new LinkedHashMap<>();
        // How each behavior reads the lines the module's declarations own. A behavior's own account
        // holds the lines it is owed a row at and none of these — that is what the account is for —
        // so a walk that looked only there would find no reading of a declared line anywhere and
        // answer that no row stands at one, of rows composed to stand at exactly that.
        Map<BorderObligationPoint, Map<String, List<BorderAssessment>>> declaredReadings =
                readingsOfTheDeclaredLines(db, module, offering.request().boundaries());
        // A reader for every behavior a row is written under, and not only for the ones a search
        // answered about. A row a declaration's line is owed is composed by whichever reading could
        // compose it, and that behavior need not be one anything else was asked of — read off the
        // searches alone, such a row came back undetermined at every item, so nothing it stands at
        // could ever make another row redundant.
        Map<String, OneBehavior> reading = new LinkedHashMap<>();
        Set<String> carriers = new LinkedHashSet<>(offering.rowsByBehavior().keySet());
        carriers.addAll(offering.searched().keySet());
        for (String behavior : carriers) {
            Adequacy.Filling filling = offering.searched().get(behavior);
            OneBehavior read = OneBehavior.of(db, module, behavior, filling,
                    sigs == null ? null : sigs.get(behavior), building, trials,
                    declaredReadings);
            if (read == null) {
                continue;   // nothing here can read a row of it, which its rows are told below
            }
            reading.put(behavior, read);
            requested.addAll(read.owed());
            if (filling != null) {
                composedFor.putAll(read.composed(filling));
            }
        }
        // And the points the module's declarations are owed, which are no behavior's own. A row of
        // whichever behavior composed one answers the line for everybody, so they are items of the
        // offering rather than of the block a row happens to sit in.
        if (offering.account() != null) {
            offering.account().resolved().forEach((point, answer) -> {
                ObligationIdentity item = new ObligationIdentity.OfALine(point);
                switch (answer.resolution()) {
                    case PointResolution.Generated(var at, var row) -> {
                        requested.add(item);
                        composedFor.put(item, RowKey.of(at.behavior(), row));
                    }
                    // Asked for and nothing came of it, which is still a thing this run is short of
                    // — and something else may stand there, which is what makes it worth asking.
                    case PointResolution.Unresolved _ -> requested.add(item);
                    // Not asked for at all: a row already stands there, or nothing measured it. A
                    // point in nobody's way is not work this run offers, and holding it here would
                    // let a candidate be the only offer for it.
                    case PointResolution.NoSearch _ -> { }
                }
            });
        }
        List<ObligationIdentity> items = List.copyOf(new LinkedHashSet<>(requested));
        offering.rowsByBehavior().forEach((behavior, rows) -> {
            OneBehavior read = reading.get(behavior);
            for (OfferedRow row : rows) {
                // Read once for the row and asked of every item. What a row is — where its values
                // sit and what running it recorded — does not change between the questions put to
                // it, and reading it per item would be the same row read as many times as this run
                // happens to be asked about, at the price of running it that many times.
                RowAsRead one = read == null ? RowAsRead.nothingRead() : read.read(row.inputs());
                Map<ObligationIdentity, Settlement> here = new LinkedHashMap<>();
                for (ObligationIdentity item : items) {
                    here.put(item, read == null ? undetermined(one) : read.settlementOf(one, item));
                }
                byRow.put(row.key(), Collections.unmodifiableMap(here));
            }
        });
        return new Settlements(items, composedFor, byRow);
    }

    /**
     * Which behaviors read each line a declaration owns, and what each of their readings is.
     *
     * <p>Asked of the debts and not of any behavior. A line a declaration draws is read wherever the
     * type is carried, and where a row written in one behavior's terms stands is a question about
     * that behavior's reading of it — so the readings are what a row is put to, one per position
     * that meets the line.
     */
    private static Map<BorderObligationPoint, Map<String, List<BorderAssessment>>>
            readingsOfTheDeclaredLines(Db db, String module, boolean boundaries) {
        Map<BorderObligationPoint, Map<String, List<BorderAssessment>>> out = new LinkedHashMap<>();
        if (!boundaries) {
            return out;
        }
        Adequacy.DeclaredBoundaries account = db.ask(new Adequacy.DeclaredBorders(module)).value();
        if (account == null) {
            return out;
        }
        for (Adequacy.DeclaredDebt owed : account.owed()) {
            Map<String, List<BorderAssessment>> here =
                    out.computeIfAbsent(owed.debt().point(), _ -> new LinkedHashMap<>());
            owed.debt().met().forEach((reading, at) ->
                    here.computeIfAbsent(reading.behavior(), _ -> new ArrayList<>()).add(at));
        }
        return out;
    }

    /**
     * One behavior's own reading of a candidate: what it is asked for, and how a row of it is read.
     *
     * <p>Made once per behavior and not once per row. What a row is read against — where its
     * positions are, what the model divides them into, and which lines this behavior's readings meet
     * — is the behavior's and does not move between the rows of one block.
     */
    private record OneBehavior(String behavior,
                               souther.compiler.partition.MeasuredInput subject, Sig sig,
                               BoundaryValues building, Generator.Trial trial,
                               List<ClassOfAPosition> classes, List<Generator.ArmOwed> arms,
                               Map<ArmProbe, CoverageSites.Obligation> armsOf,
                               Map<CoverageSites.Obligation, List<ArmProbe>> occurrencesOf,
                               Map<ObligationIdentity.OfALine, List<AtAPoint>> reads) {

        /**
         * A reader for one behavior, and what this run asked of that behavior.
         *
         * <p><b>Two things, and a carrier may have only the first.</b> How a row is read is what its
         * positions are and what runs it; what this run asked for is a search's answer, and a
         * behavior that composed a row for somebody else's line was asked nothing. Read together,
         * the account of a behavior nothing was searched for is fetched to build it — which asks the
         * search this run had decided not to make, and puts its points into a universe as work
         * nobody was set.
         *
         * @param filling what this run asked of the behavior, or null where it asked nothing. A
         *                carrier with rows and no filling still has its rows read: what it holds is
         *                a row somebody else's line needed
         */
        static OneBehavior of(Db db, String module, String behavior, Adequacy.Filling filling,
                              Sig sig, BoundaryValues building,
                              souther.compiler.execute.RowTrials trials,
                              Map<BorderObligationPoint, Map<String, List<BorderAssessment>>>
                                      declared) {
            // What a row of this behavior is measured against, asked of the store rather than
            // taken off a search. A behavior that composed a row for a declaration's line and was
            // asked nothing else has no search to take it from, and its rows are read like any
            // other.
            souther.compiler.partition.MeasuredInput subject = Adequacy.subjectOf(db, module,
                    behavior);
            if (subject == null) {
                return null;
            }
            Map<ObligationIdentity.OfALine, List<AtAPoint>> reads = new LinkedHashMap<>();
            // Where this behavior meets each point its own rules are owed a row at. Read whether or
            // not anything was asked of the behavior, for the reason the declarations' lines below
            // are: a row written under it stands where it stands, and whether this run went looking
            // for one has nothing to do with it.
            //
            // Which row is offered there is not here. That is one search over every reading of the
            // point, and the module's account holds it ({@link BorderAccount}) — a behavior
            // resolving its own points beside that would be one search made twice, free to come to
            // two answers about one row.
            List<BorderObligationPointAssessment> points = db.ask(
                    new Adequacy.Obligations(module, new GenerationScope.Behavior(behavior)))
                    .value();
            for (BorderObligationPointAssessment point
                    : points == null ? List.<BorderObligationPointAssessment>of() : points) {
                if (!point.belongsToBehaviorAccount(behavior)) {
                    continue;
                }
                ObligationIdentity.OfALine item = new ObligationIdentity.OfALine(point.point());
                point.met().forEach((_, at) -> reads.computeIfAbsent(item, _ -> new ArrayList<>())
                        .add(new AtAPoint(at.border(), at.owedAt(point.at()).criterion())));
            }
            // And this behavior's readings of the lines the declarations own, which its own rules
            // are owed none of. Read the same way and for the same reason.
            declared.forEach((point, byBehavior) -> {
                for (BorderAssessment at : byBehavior.getOrDefault(behavior, List.of())) {
                    if (at.at(point.point()) instanceof ItemAssessment.Owed owed) {
                        reads.computeIfAbsent(new ObligationIdentity.OfALine(point),
                                _ -> new ArrayList<>())
                                .add(new AtAPoint(at.border(), owed.criterion()));
                    }
                }
            });
            // Which arm each place a run is recorded at is one of, both ways round. A search names
            // an occurrence because that is where a run is recorded; what a row is owed for is the
            // arm the author wrote, and one arm has as many occurrences as there are call sites of
            // the helper carrying it. Read off the plan that numbered them, which is where the two
            // are already related — worked out here, it would be a second answer to which arm a
            // probe is one of.
            Map<ArmProbe, CoverageSites.Obligation> armsOf = new LinkedHashMap<>();
            Map<CoverageSites.Obligation, List<ArmProbe>> occurrencesOf = new LinkedHashMap<>();
            Bodies.Elaborated checked = db.ask(new Bodies.Checked(module)).value();
            CoverageSites.Plan plan =
                    checked == null ? CoverageSites.Plan.NONE : checked.plan();
            for (CoverageSites.ArmSite site : plan.arms(behavior)) {
                armsOf.put(site.index(), site.obligation());
                occurrencesOf.computeIfAbsent(site.obligation(), _ -> new ArrayList<>())
                        .add(site.index());
            }
            // What this behavior was asked to offer a row for, which is the search's answer and
            // is nothing where nothing asked it.
            return new OneBehavior(behavior, subject, sig, building,
                    sig == null || trials == null ? Generator.Trial.NOTHING_RUNS
                            : Adequacy.runningRowsOf(trials, behavior, sig,
                                    Adequacy.numberingOf(db, module)),
                    filling == null ? List.of() : filling.composed().plan().classesOwed(),
                    filling == null ? List.of() : filling.composed().plan().armsOwed(),
                    armsOf, occurrencesOf, reads);
        }

        /**
         * Which row was composed for each of them, where one was.
         *
         * <p>Read off what the searches answered with and never off the rows. A row carries the
         * classes and arms it may be named after and never a line, so a walk from the rows would
         * have every line in the block composed for nothing.
         */
        Map<ObligationIdentity, RowKey> composed(Adequacy.Filling filling) {
            Map<ObligationIdentity, RowKey> out = new LinkedHashMap<>();
            for (ClassOfAPosition each : classes) {
                if (filling.composed().discharge().at(each)
                        instanceof souther.compiler.partition.ClassDisposition.Built built) {
                    out.put(new ObligationIdentity.OfAClass(each),
                            RowKey.of(behavior, filling.composed().rowFor(built.rowId())));
                }
            }
            for (Generator.ArmOwed each : arms) {
                CoverageSites.Obligation arm = armsOf.get(each.probe());
                if (arm != null && filling.composed().discharge().at(each)
                        instanceof souther.compiler.partition.ArmDisposition.Built built) {
                    out.put(new ObligationIdentity.OfAnArm(arm),
                            RowKey.of(behavior, filling.composed().rowFor(built.rowId())));
                }
            }
            return out;
        }

        /**
         * What this behavior was asked to offer a row for.
         *
         * <p>Its classes and its arms, and no point of a line. A row at a point is owed once
         * however many readings there are, and it is offered from the module's account of them —
         * so a behavior listing its own points here would put one piece of work into a run twice
         * and let the two answer differently.
         */
        List<ObligationIdentity> owed() {
            List<ObligationIdentity> out = new ArrayList<>();
            classes.forEach(each -> out.add(new ObligationIdentity.OfAClass(each)));
            // The arm and not the place a search steers a row to. A helper carrying a fork is
            // spliced into each call site, so what the plan names is one of those occurrences —
            // the one a run through this arm would be recorded at, chosen where the finding was
            // made. What a row is offered for is the arm.
            for (Generator.ArmOwed each : arms) {
                CoverageSites.Obligation arm = armsOf.get(each.probe());
                if (arm == null) {
                    continue;
                }
                out.add(new ObligationIdentity.OfAnArm(arm));
            }
            return out;
        }

        /** The row as the two things every question here is put to ({@link RowAsRead}). */
        RowAsRead read(List<FixtureTemplate> inputs) {
            return RowAsRead.of(sig, building, trial, inputs);
        }

        Settlement settlementOf(RowAsRead asRead, ObligationIdentity item) {
            return switch (item) {
                case ObligationIdentity.OfAClass(var owed) -> inClass(asRead, owed);
                case ObligationIdentity.OfAnArm(var owed) -> throughArm(asRead, owed);
                case ObligationIdentity.OfALine at -> atThePoint(asRead, at);
            };
        }

        /**
         * Whether the row's value at the position falls in the class.
         *
         * <p>Of this behavior's positions only. An axis names the behavior it divides, so a class of
         * another one is not something a row written here has a value at — which is a row that does
         * not settle it rather than one nothing could tell about.
         */
        private Settlement inClass(RowAsRead asRead, ClassOfAPosition owed) {
            if (!behavior.equals(owed.at().behavior())) {
                return new Settlement.DoesNotSettle();
            }
            if (asRead.values() == null) {
                return undetermined(asRead);
            }
            Classification at =
                    InputClassifications.of(asRead.values(), subject.axes()).get(owed.at());
            if (at == null) {
                return new Settlement.DoesNotSettle();
            }
            return switch (at) {
                case Classification.Classified in -> in.classIds().contains(owed.classId())
                        ? new Settlement.Settles() : new Settlement.DoesNotSettle();
                case Classification.Unclassified _ -> new Settlement.Undetermined(
                        Settlement.Reason.THE_VALUES_COULD_NOT_BE_READ);
            };
        }

        /**
         * Whether running the row goes through the arm.
         *
         * <p>Not something the values answer. A row whose values sit in the classes a way into an
         * arm leaves may still go elsewhere, so the account of the run is the whole of the evidence
         * — and where there is none, this says so rather than reading the absence as a row that
         * missed.
         */
        private Settlement throughArm(RowAsRead asRead, CoverageSites.Obligation owed) {
            return switch (asRead.watched()) {
                // Any occurrence of it. The arm is what the author wrote and a helper carrying it
                // stands in the running tree once per call site, so a run through any of those is
                // a run through the arm — which is the reading the arm account already takes.
                case Generator.Watched.Ran(var account) ->
                        occurrencesOf.getOrDefault(owed, List.of()).stream().anyMatch(account::lit)
                                ? new Settlement.Settles() : new Settlement.DoesNotSettle();
                case Generator.Watched.NoAccount _ ->
                        new Settlement.Undetermined(Settlement.Reason.NO_ACCOUNT_OF_THE_RUN);
            };
        }

        /**
         * Whether the row stands at the point, as this behavior reads the line.
         *
         * <p>A line is owed a row once and is met at whichever position reads it, so a point this
         * behavior's readings do not meet is one a row written here does not settle. Where they do,
         * the walk that reads a written row against the point reads this one.
         */
        private Settlement atThePoint(RowAsRead asRead, ObligationIdentity.OfALine at) {
            List<AtAPoint> here = reads.get(at);
            if (here == null || here.isEmpty()) {
                // No reading of this line in this behavior. A row written here has no value on the
                // line at all, which is a row that does not settle the point rather than one
                // nothing could tell about.
                return new Settlement.DoesNotSettle();
            }
            if (asRead.values() == null) {
                return undetermined(asRead);
            }
            ObservedInputs observed = asRead.asInputs();
            // Existential over the readings, the way a point met at one position of a behavior is
            // met: a row standing on the line anywhere the behavior reads it is a row at the point.
            Settlement answer = new Settlement.DoesNotSettle();
            for (AtAPoint one : here) {
                Settlement said = switch (StandingAtAPoint.met(subject.at(one.line()),
                        List.of(observed), one.criterion(),
                        one.line().origin().recordedAt())) {
                    case StandingAtAPoint.Met.Reached _ -> new Settlement.Settles();
                    case StandingAtAPoint.Met.NotAtPoint _ -> new Settlement.DoesNotSettle();
                    case StandingAtAPoint.Met.NotWatched _ ->
                            new Settlement.Undetermined(Settlement.Reason.NO_ACCOUNT_OF_THE_RUN);
                    // Whichever reason there was none, this reader is deciding whether the row
                    // before it answers the point, and none of them lets it say so.
                    case StandingAtAPoint.Met.CouldNotTell _ -> new Settlement.Undetermined(
                            Settlement.Reason.THE_VALUES_COULD_NOT_BE_READ);
                };
                if (said.settles()) {
                    return said;
                }
                if (said instanceof Settlement.Undetermined) {
                    answer = said;
                }
            }
            return answer;
        }

    }

    /** One position's reading of one line, as a row is put to it: the border this behavior met and
     *  what a row there has to do. */
    private record AtAPoint(souther.compiler.partition.Border line,
                            souther.compiler.partition.Criterion criterion) {}

    /** What an item that needs the values is told, where they are not here. */
    private static Settlement undetermined(RowAsRead asRead) {
        return new Settlement.Undetermined(asRead.whyNotRead());
    }
}
