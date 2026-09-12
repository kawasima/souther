package souther.compiler.query;

import souther.compiler.execute.BoundaryValues;
import souther.compiler.execute.ExampleExecution;
import souther.compiler.execute.RowTrials;
import souther.compiler.observe.ArmObservation;
import souther.compiler.inputs.TermPath;


import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.SiteNumbering;
import souther.compiler.reach.Reachability;
import souther.compiler.diag.DiagnosticCode;
import souther.compiler.diag.msg.DeadBranchMessage;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.check.RuleCitation;
import souther.compiler.diag.Citation;
import souther.compiler.diag.Localizable;
import souther.compiler.diag.SourcePos;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.examples.FixtureReader;
import souther.compiler.ast.Hir;
import souther.compiler.check.AnalysisBody;
import souther.compiler.check.FakeTables;
import souther.compiler.check.AtomSpace;
import souther.compiler.check.BoundaryOutput;
import souther.compiler.check.ElementBindings;
import souther.compiler.check.DeclarationCitations;
import souther.compiler.check.DeclarationReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.RuleRef;
import souther.compiler.publish.PublicationOrders;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.CheckSurface;
import souther.compiler.check.Sig;
import souther.compiler.check.SpecImplementation;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeOps;
import souther.compiler.types.ValueName;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.ExpectationState;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.MeasureReason;
import souther.compiler.observe.RowIdentity;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Stage;
import souther.compiler.partition.AnswersStoodIn;
import souther.compiler.partition.Axis;
import souther.compiler.partition.ClassOfAPosition;
import souther.compiler.partition.DomainPoint;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.partition.PointRole;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReads;
import souther.compiler.partition.DecisionRule;
import souther.compiler.partition.GenerationOutcome;
import souther.compiler.partition.Generator;
import souther.compiler.partition.InputClassifications;
import souther.compiler.partition.ObservedInputs;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;

/** How well a module's {@code example} rows cover what it declares. */
public final class Adequacy {

    /**
     * How much of this a build asked to be told.
     *
     * <p>Off by default, and one dial rather than several. What separates the levels is what they
     * cost: reading what the rows already established is free, and finding out which arms they went
     * through means generating a second set of classes and running every row again. A build that did
     * not ask for the second should not pay for it, and a report that quietly measured anyway would
     * make every keystroke in an editor generate bytecode nobody reads.
     *
     * <p>Which measures a level leaves out is not read off this. A level says what work to do, and
     * every measure says for itself how much of it was made — so a caller deciding from here what a
     * measure's silence means is a second answer to a question the measure already answered.
     */
    public enum Level {
        /** No measurement is made of the rows. What the model itself says is derived as ever — the
         *  cases a signature has, the classes a position divides into, the lines its rules draw —
         *  and every measure that would have read the rows against them says nobody asked, which is
         *  not the same as saying nothing. */
        OFF,
        /** What the rows already ran established, and what the rules say without running anything.
         *  Nothing is instrumented and no row runs a second time. */
        WITNESS,
        /** That, and what the arms took instrumenting the classes and running every row again to
         *  find out. */
        ALL;

        /**
         * Whether the classes are instrumented and the rows run again to record what they went
         * through.
         *
         * <p>Named for the work and not for what a measure comes to. It was {@code measuresArms},
         * which reads as "nothing about the arms is available" — and the arms a body has are read
         * off the checked bodies whatever this says, so that reading is exactly what a caller
         * gating on the name went without (issue #955).
         */
        public boolean runsInstrumentedRows() {
            return this == ALL;
        }

        /**
         * Whether a build at this level asks for values to be composed at the lines it measures.
         *
         * <p>Its own question and not the one above it, though today they answer alike. What a
         * candidate settles is whether a row can be written at a point the rows missed, and finding
         * out costs a decoder run per point — 380 of them and sixteen seconds on the corpus this
         * was measured on, against a second of everything else a build at {@code witness} does. That
         * is not reading what the rows already established, so it is not what this level promises
         * (issue #955).
         *
         * <p><b>Read where a query is chosen, and nowhere inside one.</b> This says which question a
         * build puts, not what the answer to a question does — {@link BoundarySearch} composes
         * because it was asked, and it never reads a level to find out how much. A search that
         * decided its own work from a dial is the shape this issue is about: a caller who wanted
         * the values had nowhere to say so, and one who did not still paid for the decision to be
         * made inside.
         *
         * <p>About measuring, and not about a person asking for rows. What {@code souther examples
         * --generate} composes is asked for by the request rather than by the level, and it builds
         * whatever answering that request takes.
         *
         * <p>What a level that does not compose gets is the rules' own answer: a point inside what
         * every rule reaching it leaves is writable because the rules say so, and a point only a
         * value could settle stays unknown — reported, and counted against nobody, which is the
         * account the specification already gives an edge nothing has settled.
         */
        public boolean composesValues() {
            return this == ALL;
        }

        /**
         * Whether the rows this compilation already ran are read, and what the rules say derived
         * from them.
         *
         * <p>Named for the work, as the one above it is. It was {@code reports}, which invited the
         * reading this issue is about: a caller that took "this level does not report" for "this
         * evidence is not wanted" was deciding what a measure's answer meant from the level again,
         * one dial down (issue #955).
         */
        public boolean readsRows() {
            return this != OFF;
        }
    }

    /**
     * What a build asked for: how much to measure, and whether to be told it as warnings.
     *
     * <p>Two things rather than one, because a caller can want either without the other.
     * {@code souther examples} wants the measurement without the warnings — its whole output is the
     * report, which says everything these warnings would say and says it in one place, so printing
     * both would be the same news twice.
     *
     * <p>What a build is held to is not here, because it is not asked for: every obligation the
     * account derives is a row the model asks for ({@link #refuses}). A measure a build did not ask
     * for is one that was not made rather than one that is outside the question, so a level that
     * measures less leaves a verdict of {@code undetermined} rather than a shorter list of what is
     * owed.
     */
    public record Asked(Level level, boolean warn) {

        /**
         * Nothing measured is nothing to be warned about.
         *
         * <p>Held here rather than defended against wherever the warnings are made. `--adequacy
         * off` asks to be warned — the word names a level and the flag beside it is a build's
         * default — and that request contradicts itself: what a warning says is what a measure
         * found, and at this level no measure was made. Read as written, whoever emits them had to
         * ask the level whether to believe the request, which is this issue's shape arriving in the
         * one place it had not been taken out of (issue #955).
         */
        public Asked {
            warn = warn && level.readsRows();
        }

        public static final Asked NOTHING = new Asked(Level.OFF, false);

        /** Measured and said. */
        public static Asked warningsAt(Level level) {
            return new Asked(level, true);
        }

        /**
         * Everything measured, for a report that is the whole of what a command answers with.
         *
         * <p>{@code souther examples} asks for this. That command chooses no measurement — its
         * output is the report, so everything is measured — and what the report marks as a gap is
         * the account's ({@link #refuses}) rather than a word the caller wrote: a report answering
         * a narrower question than the build beside it is how {@code souther examples --strict}
         * came to exit 0 on a model a compile refused, with the gaps printed in the report that had
         * just called it satisfied.
         */
        public static Asked fullReport() {
            return new Asked(Level.ALL, false);
        }

        /**
         * As much as {@code level} measures, for a report to read. An editor asks for this: what it
         * draws beside a declaration is a report, and a warning saying the same thing again would be
         * the same news twice on the same line.
         */
        public static Asked reportOnly(Level level) {
            return new Asked(level, false);
        }

    }

    /** What the build asked for. Absent is {@link Asked#NOTHING}. */
    public record Requested() implements Input<Asked> {}

    /**
     * Everything measured about one module, for a caller that wants it in one piece. A map is null
     * where the question could not be answered at all, which is not the same as an empty one.
     *
     * <p>The lines each behavior's positions met are not here. A reader that measures a behavior
     * reads its account, and a reader that shows a border whole asks {@link BoundaryReadings} by
     * name — put here beside the accounts made from it, it would be a surface nobody asked for and
     * a second way to reach the same answer.
     *
     * @param signatures what the rows establish about each behavior's inputs and output
     * @param partitions what they establish about its classes
     * @param accounts   what each behavior is owed a row for at the lines its own rules draw
     *                   ({@link BodyBorders})
     * @param branches   what they establish about the arms of each body
     */
    public record Of(Map<String, SignatureEvidence> signatures,
                     Map<String, PartitionEvidence> partitions,
                     Map<String, Measure<List<BorderObligationPointAssessment>>> accounts,
                     Map<String, BranchEvidence> branches) {}

    /** Nothing read, so nothing proven and nothing shown wrong. What a measure gets where the
     *  reading is not available, which leaves every arm owed whatever it was owed. */
    public static final souther.compiler.check.PathReachability.Answers.AsRun NOTHING_PROVEN =
            new souther.compiler.check.PathReachability.Answers.AsRun(souther.compiler.check.PathReachability.Answers.NONE, Set.of());

    static Asked askedOf(Db db) {
        Asked asked = db.ask(new Requested()).value();
        return asked == null ? Asked.NOTHING : asked;
    }

    static Level levelOf(Db db) {
        return askedOf(db).level();
    }

    /**
     * What this compilation's rows have to record as they run.
     *
     * <p>Derived from the level rather than being the level, because what changes the bytecode is
     * only whether the arms are wanted. Two levels that want the same thing are then one evaluation,
     * and asking for a wider report does not re-run the rows.
     */
    static ArmObservation armsAsked(Db db) {
        return levelOf(db).runsInstrumentedRows()
                ? ArmObservation.RECORD : ArmObservation.OMIT;
    }

    /**
     * One answer for every behavior a module declares, which is what a measure of a module answers.
     *
     * <p><b>The key set is the producer's and there is nowhere to drop one.</b> A measure whose
     * question a behavior cannot be asked answers that it could not be asked, and a measure that
     * skipped the behavior instead published a map with a hole in it — one its own readers then
     * read three ways: as a composition with nothing to measure, as the whole query not having
     * answered, and as this compiler disagreeing with itself. Two of those are wrong about a
     * behavior whose declaration rests on a name nothing resolved, and the third stops the report.
     *
     * <p>So the loop is here and the caller writes what one behavior comes to. It is handed a
     * declaration and owes a value: a mapper that answered {@code null} would be the skip again,
     * written as a return.
     *
     * <p>Two behaviors of one name would be one entry, which is why what went in is counted against
     * what came out. Nothing produces that today — a module's declarations are settled before this
     * — and a key set alone cannot tell it from an answer that was overwritten.
     */
    static <T> Answer<Map<String, T>> answerEveryBehavior(
            CheckSurface prepared,
            java.util.function.Function<Hir.BehaviorDef, T> answer) {
        Map<String, T> out = new LinkedHashMap<>();
        for (Hir.BehaviorDef behavior : prepared.behaviors()) {
            out.put(behavior.name(), java.util.Objects.requireNonNull(answer.apply(behavior),
                    () -> "no answer for `" + behavior.name() + "` of `" + prepared.name() + "`"));
        }
        if (out.size() != prepared.behaviors().size()) {
            throw new IllegalStateException("`" + prepared.name() + "` declares "
                    + prepared.behaviors().size() + " behaviors and answered for " + out.size());
        }
        return Answer.of(Ordered.map(out));
    }

    /**
     * What the rows say about one behavior's signature.
     *
     * <p>An aggregate and not a number of its own: the numbers are the output's and the inputs'.
     * What its own measurement carries is that there was something to count and it was counted, and
     * what it went without is the union of what its parts went without and what the signature
     * measure itself could not see.
     */
    public record SignatureEvidence(OutputCaseEvidence output,
                                    Measure<List<InputCaseEvidence>> inputs,
                                    InputPositions layout,
                                    Measure<Counted> counted) {

        /**
         * What the account keys a case of one of these inputs on.
         *
         * <p>The one place the rule is written. A case of a sum an input ranges over and the class
         * that sum makes of the position are one thing a row is owed for where the behavior has
         * that position; where its input is read at its stages, nothing divides a position of its
         * own and the case is what it is owed at. Both the finding about such a case and the entry
         * the account publishes for it are made here, so a consumer joining one to the other is
         * joining two readings of one value rather than two values that have to agree.
         *
         * @throws IllegalStateException where the layout was not read, which is a signature
         *         nothing could be measured of and so a case nothing found missing
         */
        public souther.compiler.partition.WhereACaseOfAnInputIsOwed owedAt(
                String behavior, int at, TypeSymbol missing) {
            return switch (layout) {
                case InputPositions.Declared(List<String> names) -> {
                    if (at < 0 || at >= names.size()) {
                        throw new IllegalStateException("the cases of input " + at + " of `"
                                + behavior + "` were measured at a position its declaration does"
                                + " not have");
                    }
                    yield new ObligationIdentity.OfAClass(new ClassOfAPosition(
                            new souther.compiler.partition.AxisId(behavior, names.get(at)),
                            missing.name()));
                }
                case InputPositions.AtStages _ ->
                        new ObligationIdentity.OfAnInputCase(behavior, at, missing);
                case InputPositions.NotRead _ -> throw new IllegalStateException(
                        "the cases of input " + at + " of `" + behavior + "` are owed somewhere"
                                + " and this compilation did not read what it takes");
            };
        }

        /**
         * The obligations this measure's own account holds, for the input at {@code at}.
         *
         * <p>What a row is owed at, published where nothing else publishes it: the cases of an
         * input of a behavior with no position of its own. Where it has one, the axes carry that
         * entry and this is empty — one obligation is one entry, and a second array listing it
         * would be the same thing published twice for a consumer to reconcile.
         *
         * <p>Every case a row is owed at and not the ones no row wrote. An account is what there is
         * to cover; which of them a finding is about is the finding's.
         */
        public List<ObligationIdentity> owned(String behavior, InputCaseEvidence input) {
            if (!(layout instanceof InputPositions.AtStages)) {
                return List.of();
            }
            List<ObligationIdentity> out = new ArrayList<>();
            for (TypeSymbol each : input.coverable()) {
                out.add(owedAt(behavior, input.at(), each));
            }
            return List.copyOf(out);
        }

        /**
         * That the signature's cases were counted.
         *
         * <p><b>Whether the positions are known is not whether the boundary was worked out.</b> They
         * are two questions and this measure exists because they are: a declared behavior writes its
         * parameters, so its layout is known off the declaration whatever the boundary did, and only
         * the cases at each position go unread. A {@code >->} writes none — it takes what its first
         * stage takes — so a composition is the one shape whose layout has nowhere else to come
         * from, and the one whose positions can be unknown.
         *
         * <p><b>Which is why they are inside a measure and not a bare list.</b> Held as a list, an
         * unknown layout is an empty one — the same bytes as a behavior that takes nothing — and a
         * reader counting the entries would answer "no positions" to a question nobody could
         * answer. The same reason the positions of the partition measure are inside one
         * ({@code PartitionEvidence.partitioned}), which answers a different question about a
         * different set: what the model divides, which no declaration gives.
         */
        public record Counted() {}

        /** Why the signature has no numbers. */
        public enum NotASum implements NotApplicableReason {
            /** Neither the output nor any input is a sum, so there is no case anywhere for a row to
             *  cover and no row could make one. Held rather than read back from the two empty case
             *  sets below it: a reader that counted them would be answering a different question —
             *  how many cases there are — and getting this one right by coincidence. */
            NOT_A_SUM;

            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_BEHAVIOR;
            }
        }

        /** The same, for a measurement nobody asked for. */
        public enum NoRows implements NotMeasuredReason {
            /** No row names this behavior, so nothing was established about it either way. */
            NO_ROWS;

            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_BEHAVIOR;
            }
        }

        public static SignatureEvidence notASum(OutputCaseEvidence output,
                                                List<InputCaseEvidence> inputs,
                                                InputPositions layout) {
            return new SignatureEvidence(output, at(inputs), layout,
                    new Measure.NotApplicable<>(NotASum.NOT_A_SUM));
        }

        public static SignatureEvidence noRows(OutputCaseEvidence output,
                                               List<InputCaseEvidence> inputs,
                                               InputPositions layout) {
            return new SignatureEvidence(output, at(inputs), layout,
                    new Measurement.NotMeasured<>(NoRows.NO_ROWS));
        }

        /**
         * Something the boundary this measures is made of was missing, so no case of it was read.
         *
         * <p><b>The positions are answered for where the declaration says how many there are, and
         * not otherwise.</b> A declared behavior writes its parameters, so they are known and each
         * of them says its own cases were not read. A {@code >->} composition writes none: it takes
         * what its first stage takes, and that is what could not be worked out — so how many
         * positions it has is unknown, and the measure says so rather than answering nought.
         *
         * <p>Which is the whole of why this measure holds a measurement and not a list. The two
         * states are a behavior that takes nothing and a behavior nobody could count the positions
         * of, and as a list they are the same empty one.
         */
        public static SignatureEvidence notMeasurable(Hir.BehaviorDef behavior,
                                                      BoundaryForMeasurement.NotDerived why) {
            String name = behavior.name();
            // The positions themselves, where the declaration writes them. They are read off the
            // declaration and nothing about them went short — what could not be read is the cases
            // at each of them, which is each position's own answer. A measurement of the list that
            // said it was weakened would be a shortfall in a thing that has none, and the same two
            // states this measure exists to tell apart would be three.
            Measure<List<InputCaseEvidence>> positions =
                    behavior instanceof Hir.SpecBehavior spec
                            ? at(declaredPositions(name, spec, why))
                            : why.failed(name);
            // And where the cases of those positions are owed is not known either: what a behavior
            // takes is what could not be worked out, and a layout read off the kind of the
            // declaration here would be this measure answering from the one place that says it
            // could not.
            return new SignatureEvidence(OutputCaseEvidence.notMeasurable(why, name), positions,
                    new InputPositions.NotRead(), why.failed(name));
        }

        /** One entry per parameter the declaration writes, each saying its cases were not read. */
        private static List<InputCaseEvidence> declaredPositions(String behavior,
                                                                 Hir.SpecBehavior spec,
                                                                 BoundaryForMeasurement.NotDerived
                                                                         why) {
            List<InputCaseEvidence> out = new ArrayList<>(spec.params().size());
            for (int at = 0; at < spec.params().size(); at++) {
                out.add(InputCaseEvidence.notMeasurable(at, why, behavior));
            }
            return out;
        }

        /** The positions, where something wrote them down — the boundary, or the declaration the
         *  boundary was to have been built from. Every one of them, whatever was read at each. */
        static Measure<List<InputCaseEvidence>> at(List<InputCaseEvidence> inputs) {
            return new Measurement.Complete<>(List.copyOf(inputs));
        }

        /** Nobody asked for a measurement, so neither this nor anything under it was made. Its
         *  parts are built the same way rather than being emptied here: {@link #of} is the union of
         *  what they went without, and a measure nobody made went without nothing — so a signature
         *  assembled that way over unmeasured parts would come out complete. */
        public static SignatureEvidence notAsked(OutputCaseEvidence output,
                                                 List<InputCaseEvidence> inputs,
                                                 InputPositions layout) {
            return new SignatureEvidence(output, at(inputs), layout,
                    new Measurement.NotMeasured<>(NothingWasAsked.NOT_ASKED));
        }

        /** What the rows came to: the union of what its parts went without, and nothing else. An
         *  aggregate with a fact of its own would be a fact its parts do not have, and a reader of
         *  one of them would be right about a measure the whole contradicts. */
        // (the union is below; `weakening()` hands it on so nothing above lists the parts again)
        public static SignatureEvidence of(OutputCaseEvidence output,
                                           List<InputCaseEvidence> inputs,
                                           InputPositions layout) {
            WeakeningSet by = output.cases().weakening();
            for (InputCaseEvidence each : inputs) {
                by = by.union(each.cases().weakening());
            }
            return new SignatureEvidence(output, at(inputs), layout, by.isEmpty()
                    ? new Measurement.Complete<>(new Counted())
                    : new Measurement.Partial<>(new Counted(), by));
        }

        /** What every measure of this signature went without. The one place its parts are listed. */
        public WeakeningSet weakening() {
            return counted.weakening();
        }

        /** Whether this is what a behavior missing something its boundary is made of comes to. */
        public boolean notMeasurable() {
            return BoundaryForMeasurement.wasNotDerived(counted);
        }

        /**
         * The positions, where anything said how many there are.
         *
         * <p>Which is not the same as the boundary having been worked out: a declared behavior's
         * are its parameters, and they are here whatever became of the boundary — each of them
         * saying for itself what was read of its cases. What has none is a composition whose first
         * stage's boundary did not work out, which is the one shape with nowhere else to take a
         * layout from.
         *
         * <p>Throws there, the way {@link OutputCaseEvidence#seen()} and {@code PairSpace.counts()}
         * do. An accessor that answered an empty list instead would be the thing the measure around
         * it was introduced to remove: a reader would get an answer and no sign that nobody counted.
         */
        public List<InputCaseEvidence> positions() {
            return inputs.made().orElseThrow(() -> new IllegalStateException(
                    "a signature whose boundary was not read was asked for its positions"));
        }

        public SignatureEvidence {
            // And each of them where it says it is. Two things say which input a piece of evidence
            // is about — where it sits in this list, which is what the document publishes as the
            // order of `signature.inputs`, and what the evidence answers, which is what a finding
            // names a position by. They are read by different surfaces, so a list assembled out of
            // step would publish an array whose first entry called itself the second, and each
            // surface would go on being right about the one it reads.
            //
            // Asked of the positions where there are any. A measure that could not count them has
            // none to be out of step with, which is not the same as having none.
            List<InputCaseEvidence> at = inputs.made().orElse(null);
            if (at != null) {
                for (int i = 0; i < at.size(); i++) {
                    if (at.get(i).at() != i) {
                        throw new IllegalArgumentException("the evidence at input " + i
                                + " says it is input " + at.get(i).at());
                    }
                }
            }
        }
    }

    /**
     * What can arrive at each position of each behavior's input, in this module.
     *
     * <p>Asked once, here, and read by every measure that needs a denominator. What a signature's
     * cases are, what a position divides into, what arms a row is owed and what a body's
     * {@code unreachable} claims are held against are projections of one reading, and deriving that
     * reading per measure is what let a case the rules refuse stay in one denominator while another
     * had already taken it out.
     */
    public record Inputs(String name) implements Key<Map<String, InputDomain>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, InputDomain>> compute(Db db) {
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<RuleReadingSource> reading = Shapes.ruleReading(db, name);
            Answer<Map<String, DeclaredSig>> sigs = db.ask(new Bodies.DeclaredSignatures(name));
            Answer<Hir.Module> settled = db.ask(new Bodies.Settled(name));
            if (!prepared.present() || !scope.present() || !sigs.present() || !settled.present()
                    || !reading.present()) {
                return Answer.absent();
            }
            // A module a declaration of which has no boundary representation is one this says
            // nothing about. Why it has none was reported where the declaration is — a name that
            // denotes nothing, a field carrying a shape that cannot cross — and what a case can
            // arrive at cannot be read through such a declaration: asked anyway, the reading meets
            // a shape no position can have and says so about this compiler, which is true and is
            // not what the author of a mistyped model needs.
            if (!db.ask(new Shapes.Derived(name)).present()) {
                return Answer.absent();
            }
            // What the behaviors state about their own answers, which name locations of an input as
            // readily as a body does and reach them by the same paths.
            Map<String, souther.compiler.check.StatedContract> stated =
                    db.ask(new Bodies.StatedContracts(name)).value();
            Map<String, InputDomain> out = new LinkedHashMap<>();
            for (Hir.BehaviorDef behavior : prepared.value().behaviors()) {
                if (!(behavior instanceof Hir.SpecBehavior spec)) {
                    continue;   // a composition's inputs are its first stage's, read there
                }
                DeclaredSig declared = sigs.value().get(spec.name());
                if (declared != null) {
                    // The implementation the body was checked against, which is where a read of a
                    // parameter gets the binding it carries: the check binds `fn`'s own binders and
                    // the lowering leaves them alone. A behavior nothing implements has positions
                    // all the same.
                    Answer<Hir.FnDef> fn = db.ask(new Bodies.SettledFn(name, spec.name()));
                    SpecImplementation.Implemented implemented = fn.present()
                            ? SpecImplementation.align(spec, fn.value()) : null;
                    out.put(spec.name(), InputDomain.of(declared,
                            implemented == null ? List.of() : implemented.declaredInputs(),
                            reading.value(), db.ask(new Front.Reading()).value(),
                            // What this behavior's body reads, so the reading is closed over the
                            // paths its measurement names as well as the ones the enumeration
                            // finds. Asked as the reading is made and never after it: one that
                            // grew a position when somebody looked one up would answer a question
                            // differently depending on what had been asked before it.
                            demandOf(db, name, spec, implemented,
                                    scope.value(), statedOf(stated, spec)),
                            db.readings()));
                }
            }
            return Answer.of(Ordered.map(out));
        }
    }

    /**
     * The finite input paths one behavior's measurement is going to name.
     *
     * <p>Read off the body with a path environment and nothing else — no reading of the input, which
     * is what is being built. Which location a name stands for is settled by the parameters, the
     * bindings on the way and the case an arm selects; whether a row is ever written there is the
     * reading's answer, asked of the reading afterwards about the path this produced.
     *
     * <p>Both producers, because either alone leaves the other's rules about a position this had
     * not been told about. A body is where most of them are written and an injected behavior has
     * none at all, and a clause of the behavior draws its lines whether or not anything implements
     * it.
     */
    private static souther.compiler.inputs.InputDemand demandOf(
            Db db, String module, Hir.SpecBehavior spec,
            SpecImplementation.Implemented implemented, Symbols symbols,
            souther.compiler.check.StatedContract stated) {
        return statedIn(stated, symbols, bodyIn(db, module, spec, implemented, symbols));
    }

    /** The locations the implementation reads, or none where nothing implements the behavior. */
    private static souther.compiler.inputs.InputDemand bodyIn(
            Db db, String module, Hir.SpecBehavior spec,
            SpecImplementation.Implemented implemented, Symbols symbols) {
        Bodies.CheckedBody checked = implemented == null ? null
                : db.ask(new Bodies.CheckedBehavior(module, spec.name())).value();
        if (checked == null) {
            return souther.compiler.inputs.InputDemand.NONE;
        }
        // Which declared input each binder stands for, asked of the reading that divides an
        // implementation's parameters rather than measured off the front of its list.
        Map<souther.compiler.types.BindingId, String> parameters = new LinkedHashMap<>();
        for (SpecImplementation.ParameterBinding.AnInput input : implemented.declaredInputs()) {
            souther.compiler.types.BindingId binding = input.written().binder().binding();
            if (binding != null) {
                parameters.put(binding, input.declared().name());
            }
        }
        return souther.compiler.inputs.InputDemand.of(checked.body(),
                souther.compiler.inputs.InputReads.ofParameters(parameters, checked.elements()),
                symbols);
    }

    /**
     * And the locations the behavior's own clauses name, added to them.
     *
     * <p>A second source and not a second reading. What draws a line on an input is written in a
     * body or in an {@code ensures}, and a reading built over one of them answers about the rules of
     * the other by not having the position they are about — which is the same silence a depth used
     * to produce, arriving from the other producer. An injected behavior has no body at all and
     * still states rules.
     *
     * <p>The parameters under the bindings the declaration gave them, which is not what a body binds:
     * a clause names them where it was written.
     */
    private static souther.compiler.inputs.InputDemand statedIn(
            souther.compiler.check.StatedContract stated, Symbols symbols,
            souther.compiler.inputs.InputDemand demand) {
        if (stated == null || stated.isEmpty()) {
            return demand;
        }
        Map<souther.compiler.types.BindingId, String> parameters = new LinkedHashMap<>();
        for (souther.compiler.core.Contract.Param param : stated.params()) {
            parameters.putIfAbsent(param.binding(), param.name());
        }
        souther.compiler.inputs.InputReads names =
                souther.compiler.inputs.InputReads.ofWhatIsDeclared(parameters);
        souther.compiler.inputs.InputDemand out = demand;
        for (souther.compiler.check.StatedContract.StatedRule rule : stated.rules()) {
            for (souther.compiler.check.StatedContract.Conjunct conjunct : rule.conjuncts()) {
                souther.compiler.core.Core said = conjunct.stated().orNull();
                if (said != null) {
                    out = out.and(souther.compiler.inputs.InputDemand
                            .of(said, names, symbols).paths());
                }
            }
        }
        return out;
    }

    /**
     * One behavior's reading, or an empty one where the module's could not be made.
     *
     * <p>An absent reading is not a reading that found nothing: the difference is what the caller
     * goes on to say, and what it may say about a behavior whose signature is not in hand is
     * nothing. Written once so that each reader does not decide again what to do without one.
     */
    private static souther.compiler.check.PathReachability.Answers arrivalsOf(
            Map<String, souther.compiler.check.PathReachability.Answers> read,
            Hir.SpecBehavior spec) {
        return read == null ? souther.compiler.check.PathReachability.Answers.NONE
                : read.getOrDefault(spec.name(),
                        souther.compiler.check.PathReachability.Answers.NONE);
    }

    /**
     * What a row would be written for: the input as it was read, and where the model divides it.
     *
     * <p><b>The one place a subject is made.</b> Which classes a position has and what a number
     * there is measured on are two answers about one behavior, and a caller that chose them
     * separately could pair a reading of one input with classes measured at another — two behaviors
     * taking a parameter spelled the same way is all it takes, and then a row is composed on one
     * reading's orders and read back through the other's walk. Here both come from the same
     * {@code (module, behavior)}, so there is nothing to pair.
     *
     * <p><b>Made where it is used and never kept in an answer.</b> The reading is a way of asking
     * the declarations further questions, and an answer holding one is an answer whose comparison
     * walks a capability rather than a value — which is what decides whether a compile changed
     * anything. So what the questions answer stays what it was, and whoever needs a subject asks
     * for the parts here: a caller that depends on this depends on the geometry, on the input's
     * reading and on the names it was read against, and is recomputed when any of them moves.
     *
     * <p>Null where the behavior has no such reading, which is a behavior nothing measured.
     */
    private static souther.compiler.partition.MeasuredInput subjectOf(
            Db db, String module, Hir.SpecBehavior spec) {
        souther.compiler.partition.Partitions.Partitioning divided =
                db.ask(new Divided(module, spec.name())).value();
        Answer<DerivedSymbols> scope = Names.derivedSymbols(db, module);
        Answer<RuleReadingSource> reading = Shapes.ruleReading(db, module);
        Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(module));
        if (divided == null || !scope.present() || !sigs.present() || !reading.present()) {
            return null;
        }
        // The reading is the one the classification holds, not one looked up beside it. Both this
        // and what divides it are about the same behavior of the same module, so there is no pair
        // to get wrong.
        if (!(BoundaryForMeasurement.of(sigs.value(), db.ask(new Inputs(module)).value(), spec)
                instanceof BoundaryForMeasurement.Derived(
                        Sig _, InputForMeasurement.Local(Hir.SpecBehavior _, InputDomain domain)))) {
            return null;
        }
        return souther.compiler.partition.MeasuredInput.of(spec.name(),
                domain.reading(reading.value()), divided);
    }

    /** What one behavior states about its answer, or nothing where it states none. A behavior
     *  declaring nothing is not in the map at all, which is what says it states nothing. */
    private static souther.compiler.check.StatedContract statedOf(
            Map<String, souther.compiler.check.StatedContract> declared, Hir.SpecBehavior spec) {
        return declared == null ? null : declared.get(spec.name());
    }


    /**
     * What the model's own rules say arrives at each place of each behavior of one module.
     *
     * <p>Asked once and here, for the reason every other reading of this is: what a position is
     * divided into, which lines are owed a row, which arms are owed one and what a body declares
     * about a case are projections of one universe of possible executions, and a derivation per
     * measure is a chance per measure to disagree.
     *
     * <p>What this adds to {@link Reachable} is the conditions on the way. That one holds a
     * comparison against what the declarations leave a position, which is the same answer wherever
     * in a body the comparison stands — so a guard whose departure the guards above it have already
     * ruled out came back as an arm still owed a row.
     */
    public record PathReached(String name)
            implements Key<Map<String, souther.compiler.check.PathReachability.Answers>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, souther.compiler.check.PathReachability.Answers>> compute(Db db) {
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<RuleReadingSource> reading = Shapes.ruleReading(db, name);
            if (!prepared.present() || !scope.present() || !reading.present()) {
                return Answer.absent();
            }
            Answer<Bodies.Elaborated> checked = db.ask(new Bodies.Checked(name));
            if (!checked.present()) {
                // The bodies this would be about were not elaborated, so there is nothing here to
                // read them off. Answered rather than absent, the places nobody could look for read
                // as places the model does not have — a fact about this compile having stopped,
                // said in the words of a fact about the model.
                return Answer.absent();
            }
            Map<String, souther.compiler.core.Core> bodies = checked.value().behaviorBodies();
            if (bodies.isEmpty()) {
                // Elaborated, and holding no body: there are no places to be about, and that is
                // what the model says. Which is why this stays a present answer and the one above
                // does not.
                return Answer.of(Ordered.map(Map.of()));
            }
            souther.compiler.coverage.CoverageSites.Plan plan = checked.value().plan();
            // Asked here and not above, because this is where one is needed: what a behavior's
            // boundary came to is asked of the signatures and the readings together, and the
            // answer above is about there being no places to ask it of. Asked at the way in, a
            // module whose signatures could not be worked out would stop saying the one thing this
            // already knows about it.
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            if (!sigs.present()) {
                return Answer.absent();
            }
            Map<String, InputDomain> readInputs = db.ask(new Inputs(name)).value();
            Map<String, souther.compiler.check.PathReachability.Answers> out = new LinkedHashMap<>();
            for (Hir.BehaviorDef behavior : prepared.value().behaviors()) {
                // A composition has no body of its own and so no places of its own, and a behavior
                // whose input this compilation could not read is one nothing here is measured
                // against. Both are read off the one classification rather than worked out again:
                // the other reader of this same walk skipped what it could not read while this one
                // measured it against an input with no positions, which is the two answering one
                // question in two places.
                if (!(BoundaryForMeasurement.of(sigs.value(), readInputs, behavior)
                        instanceof BoundaryForMeasurement.Derived(
                                Sig _, InputForMeasurement.Local(Hir.SpecBehavior spec,
                                        InputDomain read)))) {
                    continue;
                }
                souther.compiler.core.Core body = bodies.get(spec.name());
                Hir.FnDef fn = db.ask(new Bodies.SettledFn(name, spec.name())).value();
                if (body == null || fn == null) {
                    continue;
                }
                out.put(spec.name(), souther.compiler.check.PathReachability.of(
                        body, db.ask(new Front.Reading()).value(),
                        SpecImplementation.align(spec, fn), plan, read, reading.value()));
            }
            return Answer.of(Ordered.map(out));
        }
    }

    /**
     * The rules each of one module's bodies states, read off the body alone.
     *
     * <p>The half of {@link Decides} that no run is involved in. Its own key because two other
     * questions are asked of it — how a run is placed among the rules ({@link Placements}), and
     * what a search can stand in one ({@link DecisionSearch}) — and a reading made again at each
     * of them would be free to hold one body's rules beside another's answer about them.
     */
    public record DecisionReadings(String name)
            implements Key<Map<String, souther.compiler.partition.DecisionReading>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, souther.compiler.partition.DecisionReading>> compute(Db db) {
            Answer<CheckSurface> prepared = db.ask(new Shapes.CheckSurface(name));
            Answer<RuleReadingSource> reading = Shapes.ruleReading(db, name);
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            Answer<Bodies.Elaborated> checked = db.ask(new Bodies.Checked(name));
            if (!prepared.present() || !reading.present() || !sigs.present()
                    || !checked.present()) {
                return Answer.absent();
            }
            Map<String, InputDomain> readInputs = db.ask(new Inputs(name)).value();
            Map<String, souther.compiler.partition.DecisionReading> out = new LinkedHashMap<>();
            for (Hir.BehaviorDef behavior : prepared.value().behaviors()) {
                // A composition has no body of its own, and a behavior whose input this compilation
                // could not read is one whose conditions name no position. Both are read off the one
                // classification the other readers of this walk read.
                if (!(BoundaryForMeasurement.of(sigs.value(), readInputs, behavior)
                        instanceof BoundaryForMeasurement.Derived(
                                Sig _, InputForMeasurement.Local(Hir.SpecBehavior spec,
                                        InputDomain read)))) {
                    continue;
                }
                AnalysisBody analysis = checked.value().analysisBodies().get(spec.name());
                if (analysis == null) {
                    continue;
                }
                out.put(spec.name(), souther.compiler.partition.DecisionReading.of(spec.name(),
                        analysis.core(), read.reading(reading.value()),
                        InputReads.ofParametersWhereCallsStand(read.parameterReads(),
                                ElementBindings.of(analysis.core(), analysis.elements(),
                                        reading.value().symbols())),
                        spec.dependsOnBehaviors()));
            }
            return Answer.of(Ordered.map(out));
        }
    }

    /**
     * How a run of each body is placed among the rules its decision states.
     *
     * <p>One answer for the three readers of it: the coverage the account keeps, the search that
     * settles whether anything can stand in a rule, and the settlement table a proposal is weighed
     * in. Built from the rules and the emitted body, which is work proportional to both — asked
     * where each of them stood, one module paid for it three times over and the three were free to
     * place one run in three different rules.
     *
     * <p>A behavior whose body was not lowered has no entry. There is nothing to place a run
     * against, which is not a placement that found nothing.
     */
    public record Placements(String name)
            implements Key<Map<String, souther.compiler.partition.RulesTaken>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, souther.compiler.partition.RulesTaken>> compute(Db db) {
            Answer<Map<String, souther.compiler.partition.DecisionReading>> read =
                    db.ask(new DecisionReadings(name));
            Answer<Bodies.Elaborated> checked = db.ask(new Bodies.Checked(name));
            if (!read.present() || !checked.present()) {
                return Answer.absent();
            }
            CoverageSites.Plan plan = checked.value().plan();
            Map<String, souther.compiler.partition.RulesTaken> out = new LinkedHashMap<>();
            read.value().forEach((behavior, rules) -> {
                souther.compiler.core.Core emitted =
                        checked.value().behaviorBodies().get(behavior);
                if (emitted != null) {
                    out.put(behavior,
                            souther.compiler.partition.RulesTaken.of(rules, emitted, plan));
                }
            });
            return Answer.of(Ordered.map(out));
        }
    }

    /**
     * The decision each of one module's bodies states, as the rules of it.
     *
     * <p>Its own key beside {@link PathReached}. That one asks where a run can get to and answers
     * per place; this one asks what the body decides and answers per path, which is a different
     * grain — several paths lead to one place, and what tells them apart is the conditions they
     * consulted.
     *
     * <p>Of the body the analysis reads, which is the tree the language's own operations stand in.
     * A rule an author wrote through one of them is a rule of the model, and the emitted tree has
     * it expanded into what it does.
     *
     * <p><b>And which of the rules the rows took.</b> The account is a function of the body and the
     * rows together — what rules there are is read off the body alone, and what stands in one is a
     * row that took it — so the two are answered here rather than left to be put together by
     * whoever holds both.
     *
     * <p>Absent where the bodies were not elaborated, for the reason {@link PathReached} is: a
     * module the compile stopped in has no rules to be read, and an empty answer would say the
     * bodies state no decision.
     */
    public record Decides(String name) implements Key<Map<String, DecisionEvidence>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, DecisionEvidence>> compute(Db db) {
            Answer<Map<String, souther.compiler.partition.DecisionReading>> read =
                    db.ask(new DecisionReadings(name));
            Answer<Bodies.Elaborated> checked = db.ask(new Bodies.Checked(name));
            if (!read.present() || !checked.present()) {
                return Answer.absent();
            }
            boolean instrumented = levelOf(db).runsInstrumentedRows();
            Optional<SiteNumbering> numbering =
                    Optional.of(SiteNumbering.of(checked.value().numberingIdentity()));
            Map<String, RowReading> byTarget = db.ask(new RowReadings(name)).value();
            Map<String, souther.compiler.partition.RulesTaken> placed =
                    db.ask(new Placements(name)).value();
            Map<String, DecisionEvidence> out = new LinkedHashMap<>();
            read.value().forEach((behavior, rules) -> out.put(behavior, new DecisionEvidence(rules,
                    whatTheRowsTook(name, rules,
                            placed == null ? null : placed.get(behavior), instrumented,
                            RowReadings.readingFor(byTarget, behavior), numbering))));
            return Answer.of(Ordered.map(out));
        }

        /**
         * Which rules the rows of one behavior took, or why none of them could be placed.
         *
         * <p>The gates in the order the work happens in, so what comes back is what stopped it
         * rather than whichever condition an expression happened to test first: a build that does
         * not instrument its rows records no place, and rows nobody wrote run nowhere.
         *
         * <p><b>Two nothings and they are not one.</b> A behavior nobody wrote a row for has its
         * rules uncovered and a build may be held to that; a behavior whose rows nothing came back
         * from has rules nothing was read about, and what they are owed is unknown. Asked of the
         * reading's own answer rather than of how many rows it happened to hand over, because that
         * hands back the same empty list for both — which is what let a rule a row may already take
         * be reported as one no row takes (issue #996).
         */
        private static Measure<DecisionEvidence.RowsPlaced> whatTheRowsTook(String module,
                souther.compiler.partition.DecisionReading rules,
                souther.compiler.partition.RulesTaken against, boolean instrumented,
                RowReading observed, Optional<SiteNumbering> numbering) {
            if (!instrumented) {
                return new Measurement.NotMeasured<>(DecisionEvidence.NotAsked.NOT_ASKED);
            }
            if (against == null) {
                // The model says this behavior writes a body and nothing lowered it. What its rows
                // take is unknown rather than none, and reads identically without this.
                return new Measurement.FailedToMeasure<>(
                        DecisionEvidence.Unreadable.THE_BODY_WAS_NOT_READ,
                        WeakeningSet.of(new Weakening.BodiesNotElaborated(module)));
            }
            if (observed.armsUnseen()) {
                // The rows ran and carry no account of where they went, so nothing can be put
                // against a rule. Started and not finished, which says what it went without.
                return new Measurement.FailedToMeasure<>(
                        DecisionEvidence.Unreadable.THE_ROWS_CARRY_NO_ACCOUNT,
                        observed.measured().weakening());
            }
            List<RowOutcome> rows = observed.rowsSeen();
            // Nothing read is not the same as nothing written. Where a source could not be
            // evaluated at all, the rows taking these rules may be sitting in it, and calling the
            // rules uncovered would tell an author to write what is already there.
            if (rows.isEmpty() && observed.someRowsUnseen()) {
                return new Measurement.FailedToMeasure<>(
                        DecisionEvidence.Unreadable.NO_ROW_CAME_BACK,
                        observed.measured().weakening());
            }
            // One entry per row, whether or not anything watched it. Taking only the accounts would
            // leave a row nothing watched out of every number the reading answers with, which is a
            // reading that went without something and does not say so.
            List<Generator.Watched> watched = new ArrayList<>();
            for (RowOutcome row : rows) {
                watched.add(ObservedInputs.of(row, numbering).watched());
            }
            return DecisionEvidence.of(rules.behavior(), against, watched,
                    observed.measured().weakening());
        }
    }

    /**
     * The branches of one module that the model's own rules make dead.
     *
     * <p>The other half of the proof {@link PathReached} makes. One reading, two readers, and they
     * are not the same reader: taking an obligation away leaves an author with less to do, and
     * saying a branch is dead tells them something is wrong. What keeps them apart is that both act
     * on {@link Reachability.Unreachable} and neither acts on anything else — an unsettled place
     * keeps its rows and is said nothing about.
     *
     * <p>Not gated on what the build asked to measure. A dead branch is a defect in the model and
     * not a gap in its rows, so it is said whether or not anybody asked for a coverage report.
     */
    public record DeadBranches(String name) implements Key<Boolean> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            // What arrives once the rows have run, which is what every other consumer reads. A row
            // that went through an arm this reading had proven nothing reaches settles it: what
            // happened happened, the proof was wrong rather than the row, and warning about it
            // would be a false report about a model the rows have already shown is fine.
            Answer<Map<String, souther.compiler.check.PathReachability.Answers.AsRun>> arrives =
                    db.ask(new Arrived(name));
            if (!arrives.present()) {
                return Answer.absent();
            }
            // In the order an author reads them. The walk numbers an inner fork while it is inside
            // the arm that holds it, so the order it finds things in is a fact about the traversal;
            // where a warning sits in the output should be a fact about the source.
            List<Dead> found = new ArrayList<>();
            arrives.value().forEach((behavior, asRun) -> asRun.answers().found()
                    .forEach((where, said) -> {
                        // Only the forks this module's own source wrote. A call into another
                        // module splices that module's forks in here, and an argument this call
                        // site hands them can leave one of their arms unreachable — which is true,
                        // and is a fact about the call rather than a defect in either module. The
                        // author cannot take that branch out; it is not theirs.
                        // An arm a run could have been recorded in. An arm that answers nothing —
                        // the `unreachable` an author writes at a case the rules refuse — is not
                        // one: it is the author saying what this reading proves, and telling them
                        // to take it out is telling them off for being right. The denominator
                        // counts the probed arms, and this reports the probed arms.
                        if (where instanceof ControlPlace.Arm
                                arm && arm.isMeasured() && arm.writtenBy(name)
                                && said instanceof souther.compiler.reach.Reachability.Unreachable
                                        unreachable) {
                            found.add(new Dead(arm, unreachable.proof()));
                        }
                    }));
            // In the order an author reads them. The walk numbers an inner fork while it is inside
            // the arm that holds it, so what order it finds them in is a fact about the traversal;
            // where a warning sits in the output should be a fact about the source.
            found.sort(java.util.Comparator.comparing((Dead each) -> at(db, each.arm()),
                    SourcePos.IN_WRITTEN_ORDER));
            List<Report> reports = new ArrayList<>();
            for (Dead each : found) {
                reports.add(warning(db, each.arm(), each.proof()));
            }
            return Answer.of(true, reports);
        }

        /**
         * One dead branch as the warning a build reads.
         *
         * <p><b>The one place a proof is taken apart.</b> The switch is exhaustive with no default,
         * so a proof added to the reading stops here and is given words, rather than falling into a
         * sentence written for something else. Everything that decides anything reads the three
         * answers and never this.
         */
        private static Report warning(
                Db db, ControlPlace.Arm arm,
                souther.compiler.reach.Proof proof) {
            return Report.of(new DeadBranchProofWords(
                    Warnings.pointedAt(Sites.placeOf(db, arm.anchor()))
                            .say(new DeadBranchMessage.NothingReachesThisBranch()))
                    .of(proof)
                    .hint(new DeadBranchMessage.TakeItOutOrLetSomethingReachIt())
                    .build());
        }

        /**
         * The one place a proof is turned into words.
         *
         * <p>Named rather than written where it is used, so that what may ask a proof what it says
         * is one class and can be held to being one: the check that fixes this reads the compiled
         * calls, and a class with a name is what it can name.
         *
         * <p>It says a word for every arm or does not compile. A proof's arms are not types this
         * package can name, so there is no switch to fall through and no default to write.
         */
        private record DeadBranchProofWords(souther.compiler.diag.Diagnostic.Builder said)
                implements souther.compiler.reach.Proof.Words<
                        souther.compiler.diag.Diagnostic.Builder> {

            /** What {@code proof} says, in these words. */
            souther.compiler.diag.Diagnostic.Builder of(souther.compiler.reach.Proof proof) {
                return proof.said(this);
            }

            @Override
            public souther.compiler.diag.Diagnostic.Builder conditionsThatCannotAllHold(
                    List<souther.compiler.reach.PathDecision> decisions) {
                souther.compiler.diag.Diagnostic.Builder out =
                        said.hint(new DeadBranchMessage.TheConditionsOnTheWayHereCannotAllHold());
                for (souther.compiler.reach.PathDecision each : decisions) {
                    out = out.secondary(souther.compiler.diag.Region.point(each.at()),
                            each.held() ? new DeadBranchMessage.ThisOneHoldsOnTheWayHere()
                                    : new DeadBranchMessage.ThisOneFailsOnTheWayHere());
                }
                return out;
            }

            @Override
            public souther.compiler.diag.Diagnostic.Builder outsideInputDomain(
                    TermPath position,
                    souther.compiler.numeric.NumericDomain.Bounds admits,
                    souther.compiler.reach.PathDecision departure) {
                return said.hint(new DeadBranchMessage.ThePositionStopsShortOfIt(
                        position.toString(), shown(admits)));
            }

            /**
             * What a position's values come to, as an author reads them.
             *
             * <p>In the shape a generated row's name is written in, so that the sentence about a
             * branch and the row a report offers beside it say a range the same way. An end nothing
             * bounds is left out rather than written as an infinity nobody typed.
             */
            private static String shown(souther.compiler.numeric.NumericDomain.Bounds admits) {
                String low = admits.min() == null ? null
                        : admits.min().at() + (admits.min().inclusive() ? " <= " : " < ");
                String high = admits.max() == null ? null
                        : (admits.max().inclusive() ? " <= " : " < ") + admits.max().at();
                return low == null && high == null ? "any number"
                        : (low == null ? "x" : low + "x") + (high == null ? "" : high);
            }

            @Override
            public souther.compiler.diag.Diagnostic.Builder everyCaseRefused(
                    String position, List<souther.compiler.types.TypeSymbol> cases) {
                return said.hint(new DeadBranchMessage.EveryCaseItIsWrittenForIsRefused(
                        position,
                        cases.stream().map(souther.compiler.types.TypeSymbol::name)
                                .collect(java.util.stream.Collectors.joining(", "))));
            }
        }

        /** One dead branch and how it was shown, before either is turned into words. */
        private record Dead(ControlPlace.Arm arm,
                            souther.compiler.reach.Proof proof) {}

        /** Where a report about an arm points, read the way {@link Warnings#pointedAt} reads it. */
        private static souther.compiler.diag.SourcePos at(
                Db db, ControlPlace.Arm arm) {
            return switch (Sites.placeOf(db, arm.anchor())) {
                case Citation.Written written -> written.at();
                case Citation.Unplaced unplaced -> unplaced.at();
                case Citation.Reached reached -> reached.at();
                case Citation.UnplacedElsewhere out -> out.at();
                // Nowhere to point, so nothing to order it by. First, and the same first every run.
                case Citation.OutOfSight _ -> new souther.compiler.diag.SourcePos(0, 0);
            };
        }

    }


    /**
     * The effective reachability of every behavior of one module.
     *
     * <p>Derived once and read by both of the measures that exclude by it. Asked separately they
     * would disagree exactly where it matters most: an arm put back into the branch measure by a row
     * that reached it would still be taken out of the signature's, and the case behind it would stay
     * unowed over a proof already known to be wrong.
     */
    public record Arrived(String name)
            implements Key<Map<String, souther.compiler.check.PathReachability.Answers.AsRun>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, souther.compiler.check.PathReachability.Answers.AsRun>>
                compute(Db db) {
            Answer<Map<String, souther.compiler.check.PathReachability.Answers>> proven =
                    db.ask(new PathReached(name));
            if (!proven.present()) {
                return Answer.absent();
            }
            // The numbering the recordings below are read under, which is this module's own — and
            // none where its bodies were not read, in which case there is nothing to read them as.
            Optional<SiteNumbering> numbering = numberingOf(db, name);
            // Every row that went there, whether or not it states what the behavior answers. What a
            // proof about reach is disproved by is a run arriving, and a row whose answer is owed
            // arrives the same way any other does — so the two are one set here, where the arm
            // account keeps them apart. The questions are different: whether anything can get here
            // is about the machine, and whether anything says what it answers is about the rows.
            Set<ArmProbe> lit = new LinkedHashSet<>();
            for (RowReading observed : db.ask(new RowReadings(name)).value().values()) {
                for (RowOutcome row : observed.rowsSeen()) {
                    lit.addAll(armsSeenIn(row, numbering));
                }
            }
            Map<String, souther.compiler.check.PathReachability.Answers.AsRun> out =
                    new LinkedHashMap<>();
            proven.value().forEach((behavior, answers) -> out.put(behavior, answers.asRunWith(lit)));
            return Answer.of(Ordered.map(out));
        }
    }

    /**
     * How far the reading of each behavior's rows got, and what it read.
     *
     * <p>An answer of its own because it is one, and because more than one thing reads it. Every
     * measure counted over the rows reads it; so does the document, which prints how many rows a
     * behavior has and how many of them are waiting. The document used to walk the sources itself
     * and build the same thing a second time — the same loop over {@code Output.Examples}, the same
     * gathering of what stopped each one — so which rows a behavior had and what its measures were
     * counted over were two readings that happened to agree (issue #996).
     *
     * <p>Total over the module's behaviors, and answers for every one of them whether or not
     * anything was seen: a behavior with no row at all is the case a source nobody could evaluate
     * matters most for. What the level asked for is answered here as well — a build that does not
     * read rows gets a reading that says so, rather than every caller writing that gate again.
     */
    public record RowReadings(String name) implements Key<Map<String, RowReading>> {

        /**
         * The reading for one behavior of a module this answered for.
         *
         * <p>Total over the behaviors it is asked about, so a key that is not there is this map's
         * contract broken rather than a state a caller reads something into. Both readings a caller
         * could invent are answers this already gives — {@code NOT_ASKED} is the level saying it
         * reads no rows and {@code NONE} is a reading that finished and found none — so a caller
         * choosing between them from a missing key is deciding what the producer answered by
         * looking at what it did not say (issue #996).
         *
         * @throws IllegalStateException where {@code answered} omits {@code behavior}
         */
        public static RowReading readingFor(Map<String, RowReading> answered, String behavior) {
            RowReading there = answered.get(behavior);
            if (there == null) {
                throw new IllegalStateException("the rows of `" + behavior
                        + "` were not answered for; the reading answers for " + answered.keySet());
            }
            return there;
        }

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, RowReading>> compute(Db db) {
            if (!levelOf(db).readsRows()) {
                Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
                if (!prepared.present()) {
                    return Answer.absent();
                }
                Map<String, RowReading> none = new LinkedHashMap<>();
                for (Hir.BehaviorDef behavior : prepared.value().behaviors()) {
                    none.put(behavior.name(), RowReading.NOT_ASKED);
                }
                return Answer.of(Ordered.map(none));
            }
            // Not copied into an ordered map. What `rowsOf` answers with reads a behavior nothing
            // named as whatever stopped every source, and a copy taken of its entries would answer
            // for the names it holds and drop that.
            return Answer.of(rowsOf(db, name));
        }
    }

    /**
     * The signature evidence for every behavior of one module.
     *
     * <p>A module's question, not a source's, although the rows are evaluated per source. A behavior's
     * rows are written across the module's own file and any number of attached {@code examples for}
     * files, so asking this of one source at a time would report the cases the other files cover as
     * uncovered. The per-source answers are read for their values and united here; no row is run twice.
     */
    public record Witnesses(String name) implements Key<Map<String, SignatureEvidence>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, SignatureEvidence>> compute(Db db) {
            // The assembly. A measure says what it could and could not establish, so a module one
            // of whose declarations did not come out is one it has something to say about — while
            // the readings it reads are derived only where the module did come out, and say so.
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            if (!prepared.present() || !scope.present() || !sigs.present()) {
                return Answer.absent();
            }
            // Whether anything was asked of the rows at all. Read here rather than at whoever wants
            // the answer: what the level decides is what work to do, and the work this measure does
            // is reading every row of the module (issue #955).
            boolean asked = levelOf(db).readsRows();
            Map<String, RowReading> byTarget = db.ask(new RowReadings(name)).value();
            Map<String, InputDomain> readInputs = db.ask(new Inputs(name)).value();
            // What each body can answer with, so that a case only an unreachable arm produces is not
            // counted. Read from the same reachability the arms are counted by.
            souther.compiler.query.Bodies.Elaborated checkedBodies =
                    db.ask(new Bodies.Checked(name)).value();
            Map<String, souther.compiler.core.Core> producing =
                    checkedBodies == null ? Map.of() : checkedBodies.behaviorBodies();
            souther.compiler.coverage.CoverageSites.Plan producingPlan =
                    checkedBodies == null
                            ? souther.compiler.coverage.CoverageSites.Plan.NONE
                            : checkedBodies.plan();
            Map<String, souther.compiler.check.PathReachability.Answers.AsRun> reachableArms = db.ask(new Arrived(name)).value();
            return answerEveryBehavior(prepared.value(), behavior ->
                    // What this measure works from, or the fact that it has none. A behavior left
                    // out of this map reads as a measure nobody asked for, and a measure nobody
                    // asked for goes without nothing — so a behavior nothing could be established
                    // about would be held to nothing at all, and say nothing about it.
                    switch (BoundaryForMeasurement.of(sigs.value(), readInputs, behavior)) {
                        case BoundaryForMeasurement.NotDerived why ->
                                SignatureEvidence.notMeasurable(behavior, why);
                        case BoundaryForMeasurement.Derived(Sig sig, InputForMeasurement input) ->
                                evidenceOf(behavior.name(), sig, scope.value(), asked,
                                        RowReadings.readingFor(byTarget, behavior.name()),
                                        InputPositions.of(input),
                                        InputCaseExclusions.of(input),
                                        producing.get(behavior.name()), producingPlan,
                                        reachableArms == null ? NOTHING_PROVEN
                                                : reachableArms.getOrDefault(behavior.name(),
                                                        NOTHING_PROVEN));
                    });
        }

    }

    /**
     * What every behavior of one module reaches of the distinctions its model draws.
     *
     * <p>A module's question for the same reason the witnesses are: a behavior's rows are written
     * across its own source and any attached files, and a class covered in one of them is covered.
     */
    public record Coverage(String name) implements Key<Map<String, PartitionEvidence>> {

        @Override
        public String module() {
            return name;
        }

        /**
         * The measures of every behavior this module divides.
         *
         * <p>Some of what it asks for is asked and not read. {@code Db.ask} records the read, so a
         * query whose answer nobody looks at is still an edge: this measure is recomputed when
         * that query's answer moves. Dropping the call would drop the edge, which is a change to
         * when this is recomputed and not a tidy-up, so the asks stay until somebody says which of
         * the two each edge is — a dependency this measure has, or one a rewrite left behind.
         * Written as a call and not a binding, so that what is wanted is on the page.
         */
        @Override
        public Answer<Map<String, PartitionEvidence>> compute(Db db) {
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            if (!prepared.present() || !scope.present() || !sigs.present()) {
                return Answer.absent();
            }
            db.ask(new Bodies.Checked(name));
            Level level = levelOf(db);
            Map<String, RowReading> byTarget = db.ask(new RowReadings(name)).value();
            Map<String, InputDomain> readInputs = db.ask(new Inputs(name)).value();
            // What the guards above each place leave, asked once for the module and read by
            // every measure below — the same reason the reading of the input is.
            db.ask(new PathReached(name));
            // What every line this module's rules drew came to, asked once and read here. Measuring a
            // line takes building values, which is not this measure's work and not work to do twice.
            // What each behavior states about its answer, read into the representation the analysis
            // holds it in. A comparison written there draws a line as a `guard`'s does.
            db.ask(new Bodies.StatedContracts(name));

            Map<String, Measure<List<BorderAssessment>>> lines =
                    db.ask(new BoundaryReadings(name)).value();
            if (lines == null) {
                return Answer.absent();
            }
            // Whether there is a subject first, and what could be read of it second. The two
            // questions are asked in that order because a measure that does not apply is owed no
            // input: a composition is measured at its stages whether or not its own signature
            // worked out, and answering "the boundary was not derived" for one would be this
            // measure reporting a prerequisite it never had. Which is a question about the model —
            // the declaration says what it is — and not about what this compilation produced,
            // which is the only thing asked below.
            return answerEveryBehavior(prepared.value(), behavior -> {
                if (!(behavior instanceof Hir.SpecBehavior spec)) {
                    return PartitionEvidence.NONE;   // measured at its stages, not here
                }
                return switch (BoundaryForMeasurement.of(sigs.value(), readInputs, spec)) {
                    case BoundaryForMeasurement.NotDerived why ->
                            PartitionEvidence.notMeasurable(why, spec.name());
                    case BoundaryForMeasurement.Derived(Sig _, InputForMeasurement _) ->
                            measured(db, name, spec, level,
                                    byTarget, lines.get(spec.name()));
                };
            });
        }

        /** What one behavior whose boundary was worked out reaches of what its model divides it
         *  into. */
        private PartitionEvidence measured(Db db, String name, Hir.SpecBehavior spec,
                                           Level level,
                                           Map<String, RowReading> byTarget,
                                           Measure<List<BorderAssessment>> lines) {
            // A behavior whose signature and input were both read is one the model divides
            // somewhere or nowhere, and either is an answer. A declaration that did not come out
            // leaves the input unread, which is said above rather than here.
            souther.compiler.partition.Partitions.Partitioning divided =
                    db.ask(new Divided(name, spec.name())).value();
            if (divided == null) {
                throw new IllegalStateException("`" + spec.name() + "` has a signature and a"
                        + " reading of its input, and no reading of what the model divides it"
                        + " into");
            }
            // The measurement itself, which is the partitioning above beside the reading it was
            // made against. What a row is placed by comes off it, so nothing here pairs a walk
            // with classes.
            souther.compiler.partition.MeasuredInput subject = subjectOf(db, name, spec);
            if (subject == null) {
                throw new IllegalStateException("`" + spec.name() + "` has a reading of what the"
                        + " model divides it into, and no measurement of the input it divides");
            }
            RowReading seen = RowReadings.readingFor(byTarget, spec.name());
            if (lines == null) {
                // Nothing came back about this behavior's lines, from a question that has
                // everything it needs to answer. Read as no lines, a behavior whose measure
                // stopped would be counted as one the model draws nothing about.
                throw new IllegalStateException("`" + spec.name() + "` has a signature and a"
                        + " reading of what the model divides it into, and no answer about the"
                        + " lines that reading drew");
            }
            // Counted with nothing a body claims in scope. What was claimed travels beside the
            // numbers rather than into them ({@link Claimed}), and the two meet where a report
            // is written.
            return Coverages.of(subject, seen, level,
                    db.ask(new Front.Adequacy()).value().measures());
        }
    }

    /**
     * What the model divides one behavior into: every position, every class, every line.
     *
     * <p><b>The one derivation.</b> What a report says is not covered, what a build is refused over
     * and what a generator writes a row for have to be the same positions and the same classes, and
     * this was worked out separately by each of the three — one meaning derived in three places,
     * which is three chances to disagree about a model nobody edited in between.
     *
     * <p>Keyed by the behavior and not by the module, because that is the unit the work is in. A
     * caller wanting one behavior's positions had to have every behavior's derived to get them.
     *
     * <p>What is not here is the reading of the declarations this was worked out from. That holds a
     * way of asking them a further question and belongs to whoever is asking; kept in the answer, it
     * would make the answer compare by which compute had built it.
     */
    public record Divided(String name, String behavior)
            implements Key<souther.compiler.partition.Partitions.Partitioning> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<souther.compiler.partition.Partitions.Partitioning> compute(Db db) {
            Answer<BodyDivided> read = db.ask(new Dividing(name, behavior));
            return read.present() ? Answer.of(read.value().geometry()) : Answer.absent();
        }
    }

    /**
     * What one reading of a body leaves, of the parts an answer may hold.
     *
     * <p>The reading of the input itself is not here. It holds a way of asking the declarations a
     * further question, so it compares by which of them built it — held in an answer, it would make
     * the answer a thing two compilations of one source could not find equal.
     */
    record BodyDivided(souther.compiler.partition.Partitions.Partitioning geometry,
                       java.util.Map<souther.compiler.partition.ConditionOccurrence,
                               souther.compiler.diag.Citation> conditionsMet,
                       Map<Integer, Citation> rulesReachedAt) {}

    /**
     * Where the reading that divided one behavior met each condition it places itself.
     *
     * <p>Beside {@link Divided} and read off the same reading. Told apart by what a reader holds:
     * one holds a row to compose and asks what the model divides, and this is asked by a reader
     * writing a sentence about a condition and holding no place at all.
     *
     * <p>Its own question so that what each of the two says stops where its own meaning stops. Both
     * are worked out again whenever the reading comes out different; what a report is told about a
     * place then comes back the same where nothing about the places moved, and goes no further.
     *
     * <p>Of the reading that met the conditions and not of the module that wrote them. A condition
     * a reader can go and open is placed by whoever wrote it
     * ({@link Sites.WhereAConditionIsWritten}); what is here is the rest — a construct this
     * compiler composed, a condition of a shape the reading has no words for, code in a file this
     * compilation does not hold.
     */
    public record ConditionsMet(String name, String behavior)
            implements Key<java.util.Map<souther.compiler.partition.ConditionOccurrence,
                    souther.compiler.diag.Citation>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<java.util.Map<souther.compiler.partition.ConditionOccurrence,
                souther.compiler.diag.Citation>> compute(Db db) {
            Answer<BodyDivided> read = db.ask(new Dividing(name, behavior));
            return read.present() ? Answer.of(read.value().conditionsMet()) : Answer.absent();
        }
    }

    /**
     * Where the reading that divided one behavior met each rule it places itself.
     *
     * <p>Beside {@link ConditionsMet} and read off the same reading, and the same question one
     * level up: a rule a reader can go and open is placed by whoever wrote it
     * ({@link Sites.WhereARuleIsWritten}), and what is here is the rest — a rule of a body written
     * in a file this compilation holds none of, which a report shows at the call it came in
     * through.
     *
     * <p>Under the address the reading handed out, which is a number counted within this
     * behavior's reading. Which behavior that is is what the rule says, so a reader holding a
     * handle asks for the module and the behavior it already has.
     */
    public record RulesReached(String name, String behavior)
            implements Key<Map<Integer, Citation>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<Integer, Citation>> compute(Db db) {
            Answer<BodyDivided> read = db.ask(new Dividing(name, behavior));
            return read.present() ? Answer.of(read.value().rulesReachedAt()) : Answer.absent();
        }
    }

    /**
     * One reading of one behavior's body, which both questions about it are projections of.
     *
     * <p>Here rather than at each of them, because a body is read once: answered apart, the two
     * would be two readings of one body, agreeing until the day one of them was taught something
     * the other was not.
     */
    record Dividing(String name, String behavior) implements Key<BodyDivided> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<BodyDivided> compute(Db db) {
            // The assembly. What says whether there is anything to divide is the behavior's own
            // signature: a module one of whose declarations did not come out still has behaviors
            // whose boundary was built, and those are divided like any other.
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<RuleReadingSource> reading = Shapes.ruleReading(db, name);
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            if (!prepared.present() || !scope.present() || !sigs.present()
                    || !reading.present()) {
                return Answer.absent();
            }
            Hir.SpecBehavior spec = specOf(prepared.value(), behavior);
            if (spec == null) {
                return Answer.absent();   // no such behavior here, or one measured at its stages
            }
            // The reading the measurement is made against, which is what makes it a measurement of
            // this behavior's input rather than of nothing. A module holding a type nobody could
            // name has none, and a measurement made without one divides an input with no positions:
            // what comes back is a partitioning that divides nothing, which is what a behavior
            // whose rules part nothing comes back with. Absent here, so the two are not one answer.
            //
            // Read off the one classification, so that what a missing signature and a missing
            // reading come to is not worked out again here. Both come back absent, which is this
            // query's answer surface and not one reason: what has no partitioning has none however
            // it came to have none, and which prerequisite went missing is said by the measures
            // that report it.
            if (!(BoundaryForMeasurement.of(sigs.value(), db.ask(new Inputs(name)).value(), spec)
                    instanceof BoundaryForMeasurement.Derived(
                            Sig _, InputForMeasurement.Local(Hir.SpecBehavior _,
                                    InputDomain domain)))) {
                return Answer.absent();
            }
            souther.compiler.query.Bodies.Elaborated checked =
                    db.ask(new Bodies.Checked(name)).value();
            Map<String, souther.compiler.core.Core> bodies =
                    checked == null ? Map.of() : checked.behaviorBodies();
            souther.compiler.coverage.CoverageSites.Plan plan =
                    checked == null
                            ? souther.compiler.coverage.CoverageSites.Plan.NONE : checked.plan();
            Coverages.Partitioned read = Coverages.partitioningOf(spec,
                    domain.reading(reading.value()), bodies.get(behavior),
                    plan,
                    arrivalsOf(db.ask(new PathReached(name)).value(), spec),
                    statedOf(db.ask(new Bodies.StatedContracts(name)).value(), spec),
                    // The other reading of the same body, which is where a rule about the strings
                    // at a position still stands as the operation the author wrote.
                    checked == null ? null : checked.analysisBodies().get(behavior),
                    // One allowance for this measure, made here and handed on. Asked for again
                    // further in, a position would be allowed its machines once per caller and what
                    // the two came to would be bought by nobody.
                    db.ask(new Front.Adequacy()).value().measures()
                            .allowanceForBehaviorDistinctions());
            return Answer.of(new BodyDivided(read.geometry(), read.conditionsMet(),
                    read.rulesReachedAt()));
        }
    }

    /**
     * What a search finds standing in each rule of one behavior's decision that no row took.
     *
     * <p><b>Work somebody asked for, and its own key for that reason</b>, exactly as the search of a
     * border's points is. It composes a value for every position of the behavior and runs it, once
     * per rule, which is work a measurement everybody pays for may not carry.
     *
     * <p>Over the rules no row took, because a rule a row took already has something standing in it
     * and the row is the proof. What comes back about the rest is what ADR-0091 calls the evidence:
     * a composed row seen taking the rule shows something can stand there, and every other answer is
     * this compiler having looked without finding.
     *
     * <p>Nothing here says a rule is unreachable. A search that composed nothing tried the values it
     * chose, and how many of them it tried was its own; a row that went elsewhere was steered by a
     * reading that may be wrong anywhere along it. Both leave the rule where it was.
     */
    public record DecisionSearch(String name, String behavior)
            implements Key<Map<DecisionRule, RuleSettlement>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<DecisionRule, RuleSettlement>> compute(Db db) {
            Map<String, DecisionEvidence> decisions = db.ask(new Decides(name)).value();
            DecisionEvidence evidence = decisions == null ? null : decisions.get(behavior);
            Answer<CheckSurface> prepared = db.ask(new Shapes.CheckSurface(name));
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            Answer<Bodies.Elaborated> checked = db.ask(new Bodies.Checked(name));
            if (evidence == null || !prepared.present() || !sigs.present() || !checked.present()) {
                return Answer.absent();
            }
            Hir.SpecBehavior spec = specOf(prepared.value(), behavior);
            Sig sig = sigs.value().get(behavior);
            souther.compiler.core.Core emitted =
                    checked.value().behaviorBodies().get(behavior);
            if (spec == null || sig == null || emitted == null) {
                return Answer.absent();
            }
            souther.compiler.partition.MeasuredInput subject = subjectOf(db, name, spec);
            Coverages.Probe probe = subject == null ? null
                    : probing(sig, subject, constructing(db, name),
                            runningRowsOf(trialling(db, name), behavior, sig,
                                    numberingOf(db, name),
                                    RequiredDependencies.of(db, name, behavior)),
                            answering(db, name, behavior, subject));
            if (probe == null) {
                // Nothing builds the values, or nothing says what the behavior has to be stood in
                // for, so no candidate goes through anything. Absent rather than an answer saying
                // nothing stands anywhere, which is what a search that ran and found nothing says
                // and is not this.
                return Answer.absent();
            }
            // The one placement of this module, which the account's coverage was read with. Built
            // again here, a candidate this search ran would be put in a rule the account has no
            // entry for.
            Map<String, souther.compiler.partition.RulesTaken> placed =
                    db.ask(new Placements(name)).value();
            souther.compiler.partition.RulesTaken taken =
                    placed == null ? null : placed.get(behavior);
            if (taken == null) {
                return Answer.absent();
            }
            souther.compiler.inputs.SearchRegion declared = subject.quantities().region();
            // The arms the branch count leaves out, which is what says a way is one the model
            // refuses rather than one a search came up short on. Asked of the same subtraction the
            // count is made by, so the two cannot disagree about an arm.
            Map<String, souther.compiler.check.PathReachability.Answers.AsRun> arrivals =
                    db.ask(new Arrived(name)).value();
            Set<CoverageSites.AsWritten> unreachedArms = arrivals == null ? Set.of()
                    : BranchEvidence.unreached(checked.value().plan().arms(behavior),
                            arrivals.getOrDefault(behavior, NOTHING_PROVEN));
            // Asked once, because what it answers is one list and asking it per rule would walk the
            // rules once for every rule.
            Set<DecisionRule> toSettle = new LinkedHashSet<>(evidence.notTakenByRows());
            Map<DecisionRule, RuleSettlement> out = new LinkedHashMap<>();
            for (souther.compiler.partition.DecisionReading.Ruled ruled
                    : evidence.read().found()) {
                if (!toSettle.contains(ruled.rule())) {
                    continue;
                }
                out.put(ruled.rule(), whatSettles(ruled, probe, taken, declared, unreachedArms));
            }
            return Answer.of(Ordered.map(out));
        }

        /**
         * What settles one rule's requirement.
         *
         * <p>The model is asked first, and its answer is not a search's. What the way states may
         * ask one position to be two things at once, which the readings that already exist show no
         * row takes — a fact about the model, carried out as itself. Nothing is composed against
         * such a way, and saying so as a search that came to nothing would leave a reader opening a
         * search's reason to find out whether the model said anything.
         *
         * <p>Where the model leaves it open, a row is composed and run. Nothing is fixed at a
         * place: a border's search is handed the positions its point names and fills the rest under
         * what stands on the way, and a rule names no point, so the way is the whole of what the
         * row has to be.
         */
        private static RuleSettlement whatSettles(
                souther.compiler.partition.DecisionReading.Ruled ruled, Coverages.Probe probe,
                souther.compiler.partition.RulesTaken taken,
                souther.compiler.inputs.SearchRegion declared,
                Set<CoverageSites.AsWritten> unreachedArms) {
            CoverageSites.AsWritten unreached = armNothingReaches(ruled, unreachedArms);
            if (unreached != null) {
                return RuleSettlement.of(new RuleRequirement.Excluded.AnArmNothingReaches(
                        unreached));
            }
            return switch (souther.compiler.partition.Reachability.of(ruled.states(), declared)) {
                case souther.compiler.partition.Reachability.NothingReaches nothing ->
                        RuleSettlement.of(new RuleRequirement.Excluded.OnePositionCannotBeBoth(
                                nothing.why()));
                case souther.compiler.partition.Reachability.Reaching reaching ->
                        whatASearchFinds(ruled, probe, taken, reaching);
            };
        }

        /**
         * The arm of this way nothing arrives at, or null where none of them is one.
         *
         * <p>Asked of the arms the branch count left out, which is the one answer to it. An arm the
         * author wrote stands at a place per call site of whatever carries it, and what a rule's
         * way names is the arm — so a reading that took one place for the arm would settle the way
         * by whichever copy it met first, and a model whose helper is reachable from one call site
         * and not from another would be answered by the order the copies were written in.
         *
         * <p>Matched on what the author wrote, which is all a rule of the decision has: the
         * construct and which of its arms the way went down. What that names may be more than one
         * obligation, which is why the answer it is looked up in is one an arm is in only where
         * every obligation it names is out.
         */
        private static CoverageSites.AsWritten armNothingReaches(
                souther.compiler.partition.DecisionReading.Ruled ruled,
                Set<CoverageSites.AsWritten> unreached) {
            for (souther.compiler.partition.ShownBy each : ruled.shownBy()) {
                if (each instanceof souther.compiler.partition.ShownBy.AtAnArm(var fork, var part)
                        && unreached.contains(
                                new CoverageSites.AsWritten(fork.origin(), part))) {
                    return new CoverageSites.AsWritten(fork.origin(), part);
                }
            }
            return null;
        }

        /**
         * What a search for a row standing in one rule found, where the model leaves it open.
         *
         * <p>Every answer here is this compiler having looked. None of them says the rule is out of
         * reach: which values were tried is this search's choice, and a reading anywhere in the
         * chain from a condition to a class may have steered them wrong.
         *
         * <p>What the way asks of the answers goes in with the request, so the row is composed in
         * the environment it is about. Composed against whatever the behavior answers generally and
         * corrected after, a way whose own answer composes was refused for a generic one that did
         * not — an answer of a union is chosen case by case, and the case a way names need not be
         * the case chosen for a way that names none.
         *
         * <p>And in the words the search came back with, whichever of them it is: nothing stood in
         * for a dependency, a way that wants a table, a budget that stopped the composing. Folded
         * to one word, an author reading the rule was told a search came to nothing and not what it
         * came to nothing on.
         */
        private static RuleSettlement whatASearchFinds(
                souther.compiler.partition.DecisionReading.Ruled ruled, Coverages.Probe probe,
                souther.compiler.partition.RulesTaken taken,
                souther.compiler.partition.Reachability.Reaching reaching) {
            return switch (probe.attempt("a rule of the decision", Map.of(), reaching,
                    ruled.demands())) {
                // What the composing came to, said on the axis it is about. The rule is left where
                // it was and nothing here is a word about the model: the way may be the easiest row
                // in the file to write by hand, and a requirement carrying this reason would say
                // otherwise.
                case Generator.BoundaryAttempt.NoRow none ->
                        RuleSettlement.nothingToTryWith(none.why());
                case Generator.BoundaryAttempt.Built built ->
                        RuleSettlement.of(whereItWent(built.row().toRun(), probe, taken, ruled));
            };
        }

        /**
         * Where the composed row turned out to go.
         *
         * <p>The row as it was composed, run as it is. Rebuilt here from its values and an
         * environment worked out beside it, the row that ran would be a row the search never
         * composed — and what it did would be recorded against the one that is offered.
         */
        private static RuleRequirement whereItWent(
                souther.compiler.partition.RowToRun composed, Coverages.Probe probe,
                souther.compiler.partition.RulesTaken taken,
                souther.compiler.partition.DecisionReading.Ruled ruled) {
            if (!(probe.read(composed).watched() instanceof Generator.Watched.Ran(var seen))) {
                return new RuleRequirement.Unsettled.NothingWatchedTheRow();
            }
            // What the row did, and not what it was composed against. A row steered here by a
            // reading that is wrong anywhere along the way arrives somewhere else, and it looks
            // like a witness until something asks the run.
            //
            // Asked as the three answers there are rather than as whether it is this rule. A run
            // this reading could not place is this compiler falling short and says nothing about
            // where the row went, so a reason added to that reading is a case to decide about here
            // rather than a run quietly reported as having gone elsewhere.
            return switch (taken.takenBy(seen)) {
                case souther.compiler.partition.RulesTaken.WhichRule.TookThis took
                        when took.rule().equals(ruled.rule()) ->
                        new RuleRequirement.Required(composed);
                case souther.compiler.partition.RulesTaken.WhichRule.TookThis _ ->
                        new RuleRequirement.Unsettled.AComposedRowWentElsewhere();
                case souther.compiler.partition.RulesTaken.WhichRule.CouldNotTell couldNot ->
                        new RuleRequirement.Unsettled.CouldNotTellWhereTheRowWent(couldNot.why());
            };
        }
    }

    /**
     * The same lines, with a value composed at each point that is worth one.
     *
     * <p><b>Work somebody asked for, and its own key for that reason.</b> Composing a value puts it
     * through this module's own decoders and costs a decoder run for each point it settles — 380 of
     * them and sixteen seconds on the corpus this was measured on, against a second for everything
     * else a build at {@code witness} does. A measurement everybody pays for may not carry that, and
     * an editor that wants the rows at one behavior's edges may not have to wait for every
     * behavior's.
     *
     * <p><b>Not a second assessment.</b> Every border, every demand, every coverage and every
     * projection is carried through untouched, and the only thing put in is the attempt at the
     * points the measurement itself says are worth one. So the two answers are ordered rather than
     * rival — this one holds strictly more evidence about the same lines, and a verdict read off
     * evidence can gain a witness and never lose one.
     *
     * <p><b>Asked of the readings, and folded after.</b> {@link Boundaries} is the same readings
     * folded, and a row is composed against the conditions the reading it is for is reached under —
     * so this takes {@link Readings} and folds what came back, which is the same fold and not a
     * second one. Taken from {@code Boundaries} instead, the conditions would be those of whichever
     * reading that fold kept. What keeps the two in this order is {@link LineReadings}: what a fold
     * gives back is not what a search takes.
     *
     * <p>Which is what lets it be asked later than the measurement, or not at all. Nobody having
     * asked is said by this key not having been asked, and not by an answer inside the measurement
     * reporting that nobody did.
     */
    public record BoundarySearch(String name, String behavior)
            implements Key<List<BorderAssessment>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<BorderAssessment>> compute(Db db) {
            LineReadings measured = db.ask(new Readings(name, behavior)).value();
            if (measured == null) {
                return Answer.absent();
            }
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            souther.compiler.partition.Partitions.Partitioning divided =
                    db.ask(new Divided(name, behavior)).value();
            if (!prepared.present() || !scope.present() || !sigs.present() || divided == null) {
                return Answer.absent();
            }
            Hir.SpecBehavior spec = specOf(prepared.value(), behavior);
            Sig sig = sigs.value().get(behavior);
            if (spec == null || sig == null) {
                return Answer.absent();
            }
            souther.compiler.partition.MeasuredInput subject = subjectOf(db, name, spec);
            if (subject == null) {
                return Answer.absent();
            }
            return Answer.of(Coverages.merged(Coverages.searched(measured, subject,
                    probing(sig, subject, constructing(db, name),
                            runningRowsOf(trialling(db, name), behavior, sig,
                                    numberingOf(db, name),
                                    RequiredDependencies.of(db, name, behavior)),
                            answering(db, name, behavior, subject)),
                    divided.reaching())));
        }

    }

    /**
     * A way to try to build a row of one behavior and see what it did, or nothing where there is
     * nothing to try against.
     *
     * <p>Nothing rather than a check that refuses nothing. A row built without the decoder is a row
     * nobody has put through anything, and counting one as a witness would turn "the classes are
     * missing" into "the edge can be written".
     *
     * <p>Beside the searches rather than inside one of them. What it takes to build a row of a
     * behavior and run it is the same whatever the row is being looked for — a point of a line, a
     * rule of the decision — and a second maker of one would be a second answer to which decoder a
     * candidate goes through.
     */
    static Coverages.Probe probing(Sig sig, souther.compiler.partition.MeasuredInput subject,
                                   BoundaryValues building, Generator.Trial trial,
                                   AnswersForARule answers) {
        return building == null || answers == null ? null
                : new ARowBuiltAndRun(sig, subject, building, trial, answers);
    }

    /**
     * Building a row of one behavior through this module's own decoders, and running it.
     *
     * <p>Named rather than written where it is made. Every place a broad failure is caught is
     * licensed one at a time and by name, and a class named by where it stands among the file's
     * anonymous ones moves when anything above it does — so what the licence is about would go on
     * reading as a licence for something else.
     */
    private static final class ARowBuiltAndRun implements Coverages.Probe {

        private final Sig sig;

        private final souther.compiler.partition.MeasuredInput subject;

        private final BoundaryValues building;

        private final Generator.Trial trial;

        /**
         * What composes the stand-ins a row built here goes out with.
         *
         * <p>The composer and not one composition of it. What a row stands the dependencies in
         * with differs between the things this behavior is searched for — a point asks nothing of
         * them, a rule asks what the body read — and only the request knows which it is. Held as
         * one answer, every search of this behavior composed its row under the answers of a way
         * that asks nothing, and a caller wanting others put them on afterwards: the row that was
         * run and the row that was built were two rows, and a way whose own answer composes was
         * refused for the generic one that did not.
         */
        private final AnswersForARule answers;

        private ARowBuiltAndRun(Sig sig, souther.compiler.partition.MeasuredInput subject,
                                BoundaryValues building, Generator.Trial trial,
                                AnswersForARule answers) {
            this.sig = sig;
            this.subject = subject;
            this.building = building;
            this.trial = trial;
            this.answers = answers;
        }

        @Override
        public Generator.BoundaryAttempt attempt(String label,
                java.util.Map<souther.compiler.partition.RealizationTarget,
                        souther.compiler.numeric.Place> fixing,
                souther.compiler.partition.Reachability.Reaching reaching,
                souther.compiler.partition.AnswersDemanded demands) {
            Generator.CandidateCheck check =
                    (at, candidate) -> built(building.build(sig.ins().get(at), candidate.value()));
            try {
                return Generator.probeFixing(subject, label, fixing, reaching, check,
                        answers.of(demands));
            } catch (LinkageError _) {
                // The generated classes would not link, so nothing can be built to find out what a
                // model admits. Nothing was tried, which is not the same as everything tried being
                // refused, and neither of them says the row cannot be written.
                return null;
            }
        }

        @Override
        public RowAsRead read(souther.compiler.partition.RowToRun row) {
            try {
                return RowAsRead.of(sig, building, trial, row);
            } catch (LinkageError _) {
                // The same, asked by reading a row through them — answered as a row nothing built,
                // which is not a row seen to stand somewhere else.
                return RowAsRead.nothingRead();
            }
        }
    }


    /**
     * A subject of one position for what one dependency answers.
     *
     * <p>Made here because this is where a behavior's input is read and paired with what is
     * measured at it, and a second maker of a reading would be free to read the same declarations
     * another way. What it stands for is not an input — it is what a row pins a dependency to — and
     * it is read by the composer of one value and by nothing else.
     *
     * <p>Named by what no source can write, because nothing outside the composer reads the name: a
     * spelling an author could also write would turn up in a message about their model as a
     * position they never declared.
     *
     * <p>What the declarations it opens have already been read as is borrowed from the reading of
     * the behavior's own input, which is the reading this is being composed beside. Started afresh,
     * every declaration the answer's type reaches would be read again once per rule of the body.
     *
     * <p>Null where the module did not build far enough to say how a declaration is read, which is
     * a value nothing composes rather than a value composed out of less.
     */
    private static souther.compiler.partition.MeasuredInput standingFor(
            Db db, String module, souther.compiler.partition.MeasuredInput beside,
            souther.compiler.types.Type answers) {
        RuleReadingSource reading = Shapes.ruleReading(db, module).value();
        souther.compiler.check.ReadingPolicy policy = db.ask(new Front.Reading()).value();
        if (reading == null || policy == null) {
            return null;
        }
        souther.compiler.inputs.InputReading read = souther.compiler.inputs.InputDomain.of(
                List.of(new souther.compiler.inputs.InputDomain.Parameter(ANSWER, null, answers)),
                reading, policy, beside.machines()).reading(reading);
        return souther.compiler.partition.MeasuredInput.of(ANSWER, read,
                souther.compiler.partition.Partitions.of(ANSWER, read, policy));
    }

    /**
     * One subject per dependency, made once for the behavior.
     *
     * <p>Once and not once per way through the body. What a dependency answers does not depend on
     * which rule a row is being composed for, and a subject made per rule reads every declaration
     * that answer reaches once for every rule — which is the reading of one type done as many times
     * as the body has ways.
     *
     * <p>Without an entry for a dependency whose answer no position stands at, which is what a
     * composer reads as a value it cannot make.
     */
    private static Map<ValueName.Behavior, AnswerSubjects> standingForEach(
            Db db, String module, souther.compiler.partition.MeasuredInput beside,
            RequiredDependencies requires) {
        Map<ValueName.Behavior, AnswerSubjects> out = new LinkedHashMap<>();
        for (RequiredDependencies.Required each : requires.inOrder()) {
            // A union of cases is no position's shape, so what a value of it is, is a value of one
            // of its cases — and each of those is a type a position stands at. Read as one subject
            // apiece rather than refused, since a row standing nothing in for such a dependency is
            // a row nothing applies.
            if (each.signature().out() instanceof BoundaryOutput.Cases cases) {
                Map<souther.compiler.types.TypeSymbol,
                        souther.compiler.partition.MeasuredInput> byCase = new LinkedHashMap<>();
                List<souther.compiler.types.TypeSymbol> ordered =
                        cases.members().stream().sorted().toList();
                // In the order the cases themselves come in, which is the one a case carries and
                // not the one a set happened to hold them in. A way that says nothing about which
                // case an answer is takes the first, so where that order is a set's, one run
                // composes one case and the next composes another — and a block that offers
                // different rows for one model twice is one nobody can read against the last.
                for (souther.compiler.types.TypeSymbol member : ordered) {
                    souther.compiler.partition.MeasuredInput standing = standingFor(db, module,
                            beside, new souther.compiler.types.Type.Ref(member));
                    if (standing != null) {
                        byCase.put(member, standing);
                    }
                }
                if (!byCase.isEmpty()) {
                    out.put(each.dependency(), new AnswerSubjects(null, ordered, byCase));
                }
                continue;
            }
            souther.compiler.partition.MeasuredInput standing =
                    standingFor(db, module, beside, each.signature().out().type());
            if (standing != null) {
                out.put(each.dependency(), new AnswerSubjects(standing, List.of(), Map.of()));
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** What the reading of a dependency's answer calls the whole of it. */
    private static final String ANSWER = "$answer";

    /**
     * Every behavior's lines, for a caller whose question is about the module.
     *
     * <p>Aggregation and nothing else: it asks the behavior's own question once per behavior and
     * puts the answers in a map. Nothing is worked out here that is not worked out there, which is
     * what keeps a caller that wants the module from being a second reading of it.
     */
    public static Map<String, List<BorderAssessment>> boundariesOf(Db db, String module) {
        return byBehavior(db, module, name -> new Boundaries(module, name));
    }

    /**
     * The lines each behavior was measured at, as the measurement read them.
     *
     * <p>{@link BoundaryReadings} without the measure beside each answer, for a caller asking what
     * the lines are rather than how far the reading that found them got. Which of the two questions
     * this is answers whether values were composed as well: a build that composes them is measured
     * at the searched lines, and this is those.
     */
    public static Map<String, List<BorderAssessment>> readingsOf(Db db, String module) {
        Map<String, Measure<List<BorderAssessment>>> lines =
                db.ask(new BoundaryReadings(module)).value();
        if (lines == null) {
            return null;
        }
        Map<String, List<BorderAssessment>> out = new LinkedHashMap<>();
        lines.forEach((behavior, read) -> out.put(behavior, read.made().orElseGet(List::of)));
        return java.util.Collections.unmodifiableMap(out);
    }

    /**
     * What a generation comes to where writing a row could not answer a finding, or null where one
     * could.
     *
     * <p>The one place that says which findings a row is an answer to. Two readers ask it and they
     * ask at different times: a generation asks by producing an outcome for every finding, and
     * somebody deciding whether to offer an author the work asks before anything is composed,
     * because composing is what costs. Written twice, the two would come apart the day a strategy
     * was written for a subject that had none — the offer staying quiet about work that was waiting,
     * or made for work nothing can do.
     *
     * <p>Null and not an arm of the outcome. "A row could answer this" is not something a generation
     * comes to; it is the absence of a reason it could not, and the answer itself takes the
     * generation.
     *
     * <p>Exhaustive with no {@code default}, so a subject added to a finding is decided here rather
     * than inheriting whichever answer sat under a catch-all.
     */
    public static GenerationOutcome whereNoRowCouldAnswer(About about) {
        return switch (about) {
            // A row stands at a line whoever the line is owed to, so a line a declaration is owed
            // is answered here the way a line a body drew is. Whether anything composes that row is
            // the other question and is the generation's ({@link #accountFor});
            // answered here, the two would be one and a reader asking whether a row could settle
            // this would be told what the search happens to be arranged to do.
            case About.APointOfABorder _, About.APointOfADeclaredBorder _,
                 About.ACaseNoRowAppliesItTo _, About.AClassNoRowIsIn _,
                 About.AnArmNoRowGoesThrough _ -> null;
            // A row is written and is waiting for its answer, at an arm or on its own. What is left
            // is the answer, which is the author's to write and nothing a search can compose; a row
            // offered for either would be a second row for a question already written down.
            case About.ARowAtAnArmAwaitsItsAnswer _, About.AnUnansweredRow _ ->
                    new GenerationOutcome.NotApplicable(GenerationOutcome.NotApplicable.Reason
                            .A_ROW_HERE_IS_WAITING_FOR_ITS_ANSWER);
            case About.ACaseNoRowExpects _ -> new GenerationOutcome.NotSupported(
                    GenerationOutcome.NotSupported.Reason.NO_STRATEGY_FOR_AN_OUTPUT_CASE);
            // A row stands here and the search that settled the rule composed it, so what became
            // of it is that search's answer and is read where the rows are ({@link #atRule}).
            case About.ARuleNoRowTakes _ -> null;
            // What the rows were seen doing rather than what they owe.
            case About.ACaseNothingWasSeenToProduce _ ->
                    new GenerationOutcome.NotApplicable(GenerationOutcome.NotApplicable
                            .Reason.AN_ACCOUNT_OF_WHAT_THE_ROWS_DID);
            // What the model says, read to the end. A row does not change it.
            case About.APositionNoLineDivides _ ->
                    new GenerationOutcome.NotApplicable(GenerationOutcome.NotApplicable
                            .Reason.A_FACT_ABOUT_THE_MODEL);
            // <b>Asked of the finding and not read off which kind it is.</b> The two reasons here
            // are the same distinction this compiler makes everywhere else — a shortfall of ours
            // against something the model states — and a finding about something with no line
            // carries both halves. Listed by kind, every rule with no line was a measure we could
            // not make, including the ones we read from end to end and understood, and a build was
            // told its own model's silence was our failure to read it.
            case About.OfSomethingNotRead notRead -> new GenerationOutcome.NotApplicable(
                    notRead.finding().readingStopped()
                            // A row would answer a question nothing asked, and offering one would
                            // be reporting our own shortfall as the author's work.
                            ? GenerationOutcome.NotApplicable.Reason.NOTHING_WAS_MEASURED
                            // The rule was read to the end and draws no line. Nothing is missing
                            // and no row anybody writes changes what it states.
                            : GenerationOutcome.NotApplicable.Reason.A_FACT_ABOUT_THE_MODEL);
            case About.APositionWhoseRulesWereNotReached _,
                 About.APositionReadWiderThanItsRules _, About.AQuestionNothingAnswered _ ->
                    new GenerationOutcome.NotApplicable(GenerationOutcome.NotApplicable
                            .Reason.NOTHING_WAS_MEASURED);
        };
    }

    /**
     * Every behavior's rows, for a caller printing a block for the module.
     *
     * <p>Aggregation, as {@link #boundariesOf} is. What a generation costs is paid per behavior it
     * is asked about, so a caller wanting one behavior's rows does not have every behavior's
     * searched to get them — which is what an editor offering to write the rows one behavior does
     * not cover was paying.
     *
     * <p>In the order the module declares them, because the block printed from this is read against
     * the one before it.
     */
    public static Map<String, Filling> generatedOf(Db db, String module) {
        CheckSurface prepared =
                db.ask(new Shapes.CheckSurface(module)).value();
        if (prepared == null) {
            return null;
        }
        Map<String, Filling> out = new LinkedHashMap<>();
        for (Hir.BehaviorDef behavior : prepared.behaviors()) {
            if (!(behavior instanceof Hir.SpecBehavior spec)) {
                continue;
            }
            Filling filled = db.ask(new Generated(module, spec.name())).value();
            if (filled != null) {
                out.put(spec.name(), filled);
            }
        }
        return Ordered.map(out);
    }

    /**
     * The rows the module's declarations are owed, one answer per point of each authored line.
     *
     * <p>Beside {@link #generatedOf}, which answers per behavior. A line an {@code invariant} drew is
     * not any behavior's, so a walk over the behaviors answers for every finding but those — and a
     * block that printed only what it managed would read as though it had filled everything, which
     * is the whole reason a disposition is kept beside each finding rather than dropped.
     *
     * <p><b>A search over the readings and not a fold of them.</b> A row is composed by walking one
     * behavior's inputs and the line is owed once over every behavior carrying the type, so the
     * readings are walked in the order the module declares them and the walk stops at the first that
     * composed a row (issue #1076). A reading that composes nothing is not the line composing
     * nothing: a record narrowing the position may refuse the value the line names where a plain
     * field takes it.
     *
     * <p>Which readings are walked is {@code scope}'s to say, and it settles the lines this is about
     * as well: a line no reading the request admits carries is not a question this was put. The
     * work is done here rather than in a key of its own, because what it costs is
     * {@link BoundarySearch} — which is keyed, so a reading is searched once however many lines are
     * resolved from it, and a request about one behavior spends nothing on the rest.
     */
    public static BorderAccount accountFor(Db db, String module, GenerationScope scope) {
        List<BorderObligationPointAssessment> points =
                db.ask(new Obligations(module, scope)).value();
        java.util.SequencedMap<souther.compiler.partition.BorderObligationPoint,
                BorderAccount.Answer> resolved = new LinkedHashMap<>();
        RuleReadingSource ruleReading = Shapes.ruleReading(db, module).value();
        souther.compiler.check.ReadingPolicy policy = db.ask(new Front.Reading()).value();
        if (points == null || ruleReading == null || policy == null) {
            return new BorderAccount(module, scope, resolved);
        }
        // What the declarations wrote their lines on, for the points that have a declaration.
        // Reading a declaration costs no search, so an account of one behavior pays nothing here
        // for the rest of the module; what it would pay for is asking the declarations' own
        // account, which is built from every reading there is.
        Map<TypeSymbol, souther.compiler.check.DeclaredBorders> declarations = new LinkedHashMap<>();
        for (BorderObligationPointAssessment debt : points) {
            // A point this module answers for nothing at: a line owed to declarations elsewhere,
            // whose values this module's are held to and whose row is somebody else's to write.
            // Which is the attribution's answer and no second reading of it.
            if (!debt.keptBy(module)) {
                continue;
            }
            // Which lines this request is about, settled once and here. A line no reading the
            // request asked about carries is not a question this was put — read further down, a
            // renderer would be deciding a second time what the request had already decided.
            if (debt.carriedBy().stream().noneMatch(scope::admits)) {
                continue;
            }
            resolved.put(debt.point(), new BorderAccount.Answer(debt,
                    debt.id().owedToTheDeclaration().isPresent()
                            ? axisOf(debt.id(), declarations, Shapes.publishedDeclarations(db),
                                    Shapes.declarationCitations(db), ruleReading, policy,
                                    db.readings()) : null,
                    PointResolver.resolveAt(debt.owed(), List.copyOf(debt.met().keySet()),
                            reading -> readingOf(db, module, scope, debt, debt.at(), reading))));
        }
        return new BorderAccount(module, scope, resolved);
    }

    /**
     * The rows one request is offered, from the two searches that compose them.
     *
     * <p>Both halves and one answer. A behavior's own rows and the rows a declaration's line is owed
     * are asked for in two ways and are work for one person, so which rows go out is settled here
     * rather than wherever they are printed — and the joining of them is a question about the work
     * rather than a step of the layout.
     *
     * <p>Here rather than in a key of its own, for the reason the two aggregations above are: what a
     * generation costs is paid by {@link Generated} and {@link BoundarySearch}, which are keyed, so
     * nothing is searched twice however many times this is asked. What identifies the question is
     * the request, and it is a value the caller states.
     */
    public static Offering offeredFor(Db db, OfferingRequest request) {
        Map<String, Filling> generated;
        if (request.scope() instanceof GenerationScope.Behavior one) {
            // One behavior, asked about on its own. Generating rows searches the pair space and
            // composes values at the edges, and a caller that named a behavior would otherwise pay
            // for every other behavior of the module to find out about the one it asked for.
            Filling only = db.ask(new Generated(request.module(), one.name())).value();
            generated = only == null ? Map.of() : Map.of(one.name(), only);
        } else {
            generated = generatedOf(db, request.module());
        }
        if (generated == null) {
            return null;
        }
        // And what the module's own declarations are owed, which is no behavior's and so is in none
        // of the fillings above. Asked whenever rows are, because what a declaration's line is owed
        // is an obligation of the same account as the rest.
        BorderAccount account = accountFor(db, request.module(), request.scope());
        Composition composed = Composition.composed(request, generated, account);
        // And then only the rows whose going would cost the offering something. A candidate is
        // composed for one thing and the positions that thing does not name hold whatever the row
        // has to hold, so a row composed for one item can stand where another item asks — and the
        // two went out as two pieces of work because nothing asked what an offered row settles.
        Settlements table = Settlements.of(db, composed);
        Set<RowKey> kept = table.keeping();
        // And what the rows that are left answer, which is what the block may not say nothing
        // offers. A row composed for one thing standing where another asks is the whole of this,
        // and a note printed over it would send a person after work that is already in front of
        // them.
        Set<ObligationIdentity> answered = new LinkedHashSet<>();
        for (ObligationIdentity item : table.requested()) {
            if (kept.stream().anyMatch(row -> table.at(row, item).settles())) {
                answered.add(item);
            }
        }
        return composed.keeping(kept, answered);
    }

    /**
     * What composes the stand-ins for one behavior's rows, or nothing where the module did not
     * build far enough to say what it requires.
     *
     * <p>The composer and not a composition. What a row stands the dependencies in with depends on
     * what the thing it is composed for asks of them, and that is the request's — so this is asked
     * once for the behavior and each search hands it its own demand. Made per demand instead, every
     * asker would read the declarations again for the part of the answer that does not vary: which
     * dependencies there are, and where a value for each is read.
     */
    private static AnswersForARule answering(Db db, String module, String behavior,
                                             souther.compiler.partition.MeasuredInput subject) {
        RequiredDependencies requires = RequiredDependencies.of(db, module, behavior);
        // What the module states for its dependencies, which is the environment a row is composed
        // inside. Absent where resolution did not reach the blocks, and a row composed as though
        // the module stated none would write over a table it was about to be pasted beside.
        FakeTables blocks = db.ask(new Names.FakeTables(module)).value();
        return requires == null || blocks == null ? null
                : new AnswersForARule(requires, standingForEach(db, module, subject, requires),
                        blocks);
    }

    /**
     * What one behavior's reading of one line holds at one of its points.
     *
     * <p>Asked of {@link BoundarySearch} and never of the measurement. The two are both called an
     * attempt and they answer different questions: a measurement builds a value where the level asks
     * it to, and says whether the point exists; a generation is asked afterwards and composes the
     * row an author is offered. Read from the measurement, a build at {@code witness} composes
     * nothing, and a block that had just printed a row would say nothing offers one.
     *
     * <p>Asked only where the scope admits the behavior, so a request about one behavior spends
     * nothing on the rest — which is what a search per reading was costing an editor.
     *
     * <p>One reading and never a behavior's. A behavior carrying the type at two positions meets
     * the line twice, and what a search of one of them came to is a fact about that position — the
     * rules reaching it, the values its decoder took. Folded to one answer per behavior, the second
     * position's was dropped and which one survived was whichever the search walked first.
     */
    static PointResolver.ReadingEvidence readingOf(
            Db db, String module, GenerationScope scope, BorderObligationPointAssessment debt,
            DomainPoint role,
            BorderObligationPointAssessment.Reading reading) {
        if (!scope.admits(reading.behavior())) {
            return new PointResolver.ReadingEvidence.OutOfScope();
        }
        List<BorderAssessment> searched =
                db.ask(new BoundarySearch(module, reading.behavior())).value();
        if (searched == null) {
            return new PointResolver.ReadingEvidence.NoAnswer();
        }
        // The same line, found in the search's own reading of this behavior by the line itself. A
        // border is a value, so this is the reading the debt was made from and not one that happens
        // to look like it — and where a behavior holds the line twice `owedAt` refuses rather than
        // choosing, which is the same refusal the debt's own readings are built under.
        souther.compiler.partition.Border line = debt.met().get(reading).border();
        if (!(BorderAssessment.owedAt(searched, line, role) instanceof ItemAssessment.Owed here)) {
            throw new IllegalStateException("a reading owing nothing at a point its line owes one"
                    + " at: " + debt.point() + " at " + reading);
        }
        if (!here.searches().ran()) {
            // The search answered about this behavior and looked for nothing here, at a point the
            // line says is worth searching. That is the search and the debt disagreeing about one
            // point rather than evidence of anything, and a state read as either would report our
            // own bookkeeping as an answer about the line.
            throw new IllegalStateException("nothing was searched for at " + debt.point()
                    + ", which the line says is worth searching, at " + reading);
        }
        return new PointResolver.ReadingEvidence.Searched(here.searches());
    }

    /** The same, with a value composed at every point worth one — which is a request, and costs what
     *  {@link BoundarySearch} costs. */
    public static Map<String, List<BorderAssessment>> searchedBoundariesOf(Db db, String module) {
        return byBehavior(db, module, name -> new BoundarySearch(module, name));
    }

    private static Map<String, List<BorderAssessment>> byBehavior(
            Db db, String module, java.util.function.Function<String,
                    Key<List<BorderAssessment>>> asked) {
        CheckSurface prepared =
                db.ask(new Shapes.CheckSurface(module)).value();
        if (prepared == null) {
            return null;
        }
        Map<String, List<BorderAssessment>> out = new LinkedHashMap<>();
        for (Hir.BehaviorDef behavior : prepared.behaviors()) {
            if (!(behavior instanceof Hir.SpecBehavior spec)) {
                continue;   // a composition's inputs are its first stage's, measured there
            }
            List<BorderAssessment> lines = db.ask(asked.apply(spec.name())).value();
            if (lines != null) {
                out.put(spec.name(), lines);
            }
        }
        return Ordered.map(out);
    }

    /**
     * The measurement a row of one behavior is read against, asked of the store by name.
     *
     * <p>For a reader that holds a row and not a search. A row a declaration's line is owed is
     * composed by whichever reading could compose it, and that behavior need not be one anything
     * else was asked about — so what it takes to read the row is asked for here rather than taken
     * off an answer about generating rows, which such a behavior has none of.
     *
     * <p>Null where the behavior is not one this module declares with an input of its own, or
     * where its input has no reading — which is a behavior nothing measured rather than one
     * measured into nothing.
     */
    static souther.compiler.partition.MeasuredInput subjectOf(Db db, String module,
                                                              String behavior) {
        CheckSurface prepared =
                db.ask(new Shapes.CheckSurface(module)).value();
        if (prepared == null) {
            return null;
        }
        Hir.SpecBehavior spec = specOf(prepared, behavior);
        return spec == null ? null : subjectOf(db, module, spec);
    }

    /**
     * The numbering a run of {@code module}'s classes is read under.
     *
     * <p>Asked of the check that holds the bodies, which is where a module's numbering is derived.
     * A module whose bodies were not read has none, and none is what comes back — not a numbering
     * of nowhere standing in for it. Such a stand-in is a numbering some other module's places
     * would be read under, and every reader that aligned against it would be told its recordings
     * were of somewhere else; what a reader without a numbering has is no account of any run, which
     * is what each of them says.
     *
     * <p><b>For a caller that wants the numbering and nothing else.</b> Several of the keys here
     * read the checked bodies for other things beside it — what a behavior's body is, what its
     * elements bind, whether the bodies came back at all — and those take the numbering off the
     * value they are already holding. This is the same route rather than a second one, and calling
     * it from there would be asking the store for something in hand.
     *
     * <p>The check owns the numbering, so this reads the numbering it issued. What a plan adds is
     * where each place is in the bodies, which a caller reading a recorded number back as a place
     * does not use — and deriving one for it walks every body of the module to say nothing more.
     */
    static Optional<SiteNumbering> numberingOf(Db db, String module) {
        Bodies.Elaborated checked = db.ask(new Bodies.Checked(module)).value();
        return checked == null ? Optional.empty()
                : Optional.of(SiteNumbering.of(checked.numberingIdentity()));
    }

    /** The behavior of that name that has inputs of its own, or null. A composition's inputs are its
     *  first stage's and are divided there. */
    private static Hir.SpecBehavior specOf(CheckSurface prepared,
                                           String name) {
        for (Hir.BehaviorDef each : prepared.behaviors()) {
            if (each instanceof Hir.SpecBehavior spec && spec.name().equals(name)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * Every line each of a module's behaviors met, and how far the reading that found them got.
     *
     * <p>Where the lines a behavior was read at are kept. Two accounts are made from these — what a
     * behavior is owed a row for ({@link PartitionEvidence}) and what a module's declarations
     * are ({@link DeclaredBorders}) — and what a report shows of a border whole is this: a block
     * accounts for a border's four points whosever they are, which is a different question from
     * whose debt each of them is.
     *
     * <p>Whether the values are composed is the build's to ask for, and it is asked here rather than
     * inside either key below. A level says how much work to do; what a search does when it is asked
     * is not a thing it may decide, which is the reading that put the composing inside the
     * measurement in the first place.
     */
    public record BoundaryReadings(String name)
            implements Key<Map<String, Measure<List<BorderAssessment>>>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, Measure<List<BorderAssessment>>>> compute(Db db) {
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            if (!prepared.present() || !sigs.present()) {
                return Answer.absent();
            }
            Level level = levelOf(db);
            Map<String, InputDomain> readInputs = db.ask(new Inputs(name)).value();
            return answerEveryBehavior(prepared.value(),
                    behavior -> linesReadIn(db, name, behavior, sigs.value(), readInputs,
                            level.composesValues()));
        }
    }

    /**
     * The lines one behavior was read at, and how far that reading got.
     *
     * <p>What a reading is, asked of one behavior rather than of a map over the module, so that a
     * caller may ask about the behaviors its question is about.
     *
     * <p>{@code composes} says whether to put values through this module's decoders at the lines,
     * which is a decoder run at every point and the whole cost of a search. Which lines there are
     * does not turn on it: a point is read wherever the model carries the rule whether or not
     * anybody composed a value there. That is what lets a caller learn that a point has a reading in
     * a behavior it is not going to search — and a caller that skipped the behavior instead would be
     * told the point has one reading and that its walk of it saw everything.
     */
    static Measure<List<BorderAssessment>> linesReadIn(Db db, String name, Hir.BehaviorDef behavior,
            Map<String, Sig> sigs, Map<String, InputDomain> readInputs, boolean composes) {
        if (!(behavior instanceof Hir.SpecBehavior spec)) {
            return BoundaryDerivation.noSubject();   // measured at its stages, not here
        }
        if (BoundaryForMeasurement.of(sigs, readInputs, spec)
                instanceof BoundaryForMeasurement.NotDerived why) {
            // No boundary to read the rules at, so the lines are a measure that was asked for and
            // could not be finished rather than a model that draws none.
            return why.failed(spec.name());
        }
        souther.compiler.partition.Partitions.Partitioning divided =
                db.ask(new Divided(name, spec.name())).value();
        if (divided == null) {
            throw new IllegalStateException("`" + spec.name() + "` has a signature and a reading"
                    + " of its input, and no reading of what the model divides it into");
        }
        List<BorderAssessment> read = composes
                ? db.ask(new BoundarySearch(name, spec.name())).value()
                : db.ask(new Boundaries(name, spec.name())).value();
        if (read == null) {
            throw new IllegalStateException("`" + spec.name() + "` has a reading of what"
                    + " the model divides it into, and no answer about the lines that"
                    + " reading drew");
        }
        return BoundaryDerivation.of(read, divided.borderClosure(), divided.inputIsEmpty());
    }

    /**
     * What the rows established about every line one behavior's rules drew.
     *
     * <p>The one authority on a boundary. Whether a row sits at it and whether a row could sit at it
     * were established in two places under two sets of rules — the report read one, the generator read
     * the other — and the two disagreed about the same line: a boundary the report declined to name
     * because a row had gone unread was one the generator handed to an author anyway, and a boundary
     * the projection could not promise was one the generator had already built a value for and thrown
     * the answer away.
     *
     * <p>Nothing is composed here. What a value put through this module's decoders settles is
     * {@link BoundarySearch}'s, which reads this and adds to it — so what everybody pays for is the
     * reading, and what costs a decoder run for each point it settles is paid by whoever asked for it.
     *
     * <p>The lines alone, without how far the reading that found them got. What holds the two
     * together is {@link BoundaryReadings}, and everything that accounts for rows reads that.
     */
    public record Boundaries(String name, String behavior)
            implements Key<List<BorderAssessment>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<BorderAssessment>> compute(Db db) {
            LineReadings readings = db.ask(new Readings(name, behavior)).value();
            return readings == null ? Answer.absent() : Answer.of(Coverages.merged(readings));
        }
    }

    /**
     * The same lines, one entry per reading of one.
     *
     * <p>Below {@link Boundaries} and asked by whatever has something to do while the readings are
     * still apart. A guard inside a non-recursive helper is read once per call of that helper, and
     * each of those is reached under its caller's own conditions — so what a row for it may be
     * composed out of is a reading's own answer, and a search made after the readings are one has
     * to pick one of them to compose against.
     *
     * <p>Answered as {@link LineReadings} rather than as the same list a line comes back in, so that
     * which of the two a caller is holding is a thing the compiler knows.
     */
    public record Readings(String name, String behavior)
            implements Key<LineReadings> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<LineReadings> compute(Db db) {
            // What the lines are read against, which is the whole of what this needs: a behavior
            // this module declares with an input of its own, read, and measured. Every question
            // that has to have been answered for one to exist is asked where it is made.
            souther.compiler.partition.MeasuredInput subject = subjectOf(db, name, behavior);
            if (subject == null) {
                return Answer.absent();
            }
            // Whether a guard's boundary can be decided at all: meeting it takes the comparison having
            // been evaluated, which only the instrumented classes say. And whether anything was
            // measured against the rows at all, which is what `off` answers.
            Level level = levelOf(db);
            return Answer.of(assess(subject,
                    RowReadings.readingFor(db.ask(new RowReadings(name)).value(), behavior), level,
                    // The numbering the rows' recordings are read under, which is this module's own.
                    numberingOf(db, name)));
        }

        /** Every line of one behavior, with what the rows and the decoder say about each. */
        private static LineReadings assess(souther.compiler.partition.MeasuredInput subject,
                                           RowReading observed, Level level,
                                           Optional<SiteNumbering> numbering) {
            souther.compiler.partition.Partitions.Partitioning partitioning = subject.partitioning();
            // Two sources and not one. A line drawn at a count of a position comes off that position's
            // axis; a line drawn between two positions comes off the comparison and has no axis to come
            // off — the body of a behavior whose inputs are plain numbers nothing bounds draws lines
            // while having no axis at all. Which lines there are of either kind is the reading of the
            // model's, and this is handed both rather than assembling one of them.
            List<BorderAssessment> out = new ArrayList<>();
            for (Axis axis : partitioning.axes()) {
                // A reading that ran, so its answer is one of the two a reading comes to. The third
                // state belongs to the lines between two positions, where the question is not put at
                // all, and is spelled there rather than here — a boolean lifted at the boundary it
                // is answered at cannot arrive somewhere as the wrong one of the three.
                out.addAll(Coverages.assess(partitioning.along(axis), subject, observed, level,
                        ItemAssessment.WritabilityProjection.ofReading(
                                partitioning.edgeIsKnownWritable(axis.term())), numbering));
            }
            out.addAll(Coverages.assessBetween(subject, observed, level, numbering));
            return new LineReadings(out);
        }

    }


    /**
     * Which arms of each behavior's body the rows go through.
     *
     * <p>Branch-<em>arm</em> coverage, and nothing larger. Going through both arms of two nested
     * conditions is four arms and says nothing about whether their combinations were tried, so nothing
     * here calls this covering the paths a body has.
     *
     * <p>What the measure has where it has anything is {@link ArmSummary}: every arm the behavior
     * is owed a row for, where each of them stands, and whether the set of them is the set it owes.
     * An arm the model's own rules prove nothing reaches is not one of them — it is instrumented,
     * because a probe is what would show the proof wrong, and it is not owed, because no row can
     * light it. Which arms those are is {@link ArmReachability}'s answer, asked once for the module
     * and read by every measure.
     */
    public record BranchEvidence(Measure<ArmSummary> measured) {

        /**
         * Why a behavior's arms have no number, where nobody asked for one.
         *
         * <p>The first gate that did not open is the answer. They are asked in that order because
         * that is the order the work happens in: a body has to exist before anything can be asked
         * about it, the build has to ask before the classes are generated, and a row has to name the
         * behavior before any of it is about this one.
         */
        public enum NotAsked implements NotMeasuredReason {
            /** The build did not ask for the arms, which cost a second run of every row. */
            NOT_ASKED,
            /** No row names this behavior. The measurement is opted into by writing one, and
             *  reaching the behavior through somebody else's row is not opting in. */
            NO_ROWS;

            /** What the build asked for is one value for the run; which behaviors a row names is
             *  not. */
            @Override
            public MeasureReason.About about() {
                return switch (this) {
                    case NOT_ASKED -> MeasureReason.About.THE_RUN;
                    case NO_ROWS -> MeasureReason.About.THE_BEHAVIOR;
                };
            }
        }

        /**
         * Why there is nothing here for the arm measure to be about, and no row would give it
         * something.
         *
         * <p>Two answers over three ways to owe no arm: a behavior this module does not implement,
         * one implemented without a fork, and one whose every fork the rules already prove nothing
         * reaches. The last two are one answer, for the reason {@link #NO_ARM_OBLIGATIONS} gives.
         * All of them are the model's answer rather than a run's, so none waits on the
         * instrumentation — which is what lets this be asked before the level is (issue #955).
         */
        public enum NoArms implements NotApplicableReason {
            /** A {@code >->} composition or a behavior with no {@code let}. Its arms, where it has
             *  any, are its stages' and are measured there. */
            NO_BODY,
            /**
             * The body is here and owes no arm.
             *
             * <p>Named for the obligations and not for the forks. {@link
             * souther.compiler.coverage.CoverageSites} numbers the arms a row can be in or out of,
             * and a fork under something that aborts is registered with no site at all — so an
             * empty list says this behavior owes no arm, and saying it has no fork would be this
             * measure claiming something it did not read.
             *
             * <p>One answer and not two. A fork whose ways the rules settle was going to be the
             * second — "every arm proven unreachable" — and no model reaches it: a fork a row gets
             * to has a way the row takes, so something stays owed, and a fork nothing gets to has
             * no site to be owed at. A state nothing produces is a word in a document no compiler
             * writes and a branch no test can reach, so the two are the one answer the obligations
             * give.
             */
            NO_ARM_OBLIGATIONS;

            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_BEHAVIOR;
            }
        }

        /** The rows ran without instrumentation, so what they went through went with it. The one
         *  reason here that is a measurement started and not finished. */
        public enum Unreadable implements FailureReason {
            UNREADABLE;

            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_BEHAVIOR;
            }
        }

        /**
         * The bodies this measure counts arms in were not made.
         *
         * <p>Named for the absence and not for any of the things that cause it: a module the
         * compile stopped in has no elaborated bodies, and nothing here can tell which of the ways
         * that happens it was. What a reader of this knows is that the model says a body is written
         * and what it holds was not read.
         *
         * <p>Its own reason rather than {@link NoArms#NO_BODY}, which is the claim it used to be
         * answered with. That claim is about the model and this is about the compile, and the two
         * were one answer while the measure read the elaborated bodies for both (issue #996).
         */
        public enum Unelaborated implements FailureReason {
            BODIES_NOT_ELABORATED;

            /** The module the behavior is in, which another behavior of the same run need not be
             *  in. */
            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_BEHAVIOR;
            }
        }

        public static BranchEvidence noArms(NoArms reason) {
            return new BranchEvidence(new Measure.NotApplicable<>(reason));
        }

        /** The model says this behavior writes a body and nothing elaborated it, so what it owes
         *  was not read. */
        public static BranchEvidence unelaborated(String module) {
            return new BranchEvidence(new Measurement.FailedToMeasure<>(
                    Unelaborated.BODIES_NOT_ELABORATED,
                    WeakeningSet.of(new Weakening.BodiesNotElaborated(module))));
        }

        public static BranchEvidence notAsked(BranchEvidence.NotAsked reason) {
            return new BranchEvidence(new Measurement.NotMeasured<>(reason));
        }

        public static BranchEvidence unreadable(WeakeningSet by) {
            return new BranchEvidence(
                    new Measurement.FailedToMeasure<>(Unreadable.UNREADABLE, by));
        }

        /**
         * The arms of {@code all} a row can still be asked for.
         *
         * <p>Which arms a behavior owes, said once. It is asked before the level is — an empty
         * answer is a behavior with nothing here to measure, whatever any build asked for — and
         * again where the numbers are made, and a second derivation beside this one would be two
         * denominators free to disagree about one body.
         */
        public static List<CoverageSites.ArmSite> owed(
                List<CoverageSites.ArmSite> all,
                souther.compiler.check.PathReachability.Answers.AsRun reachable) {
            return all.stream()
                    .filter(site -> !(reachable.answers().at(site.place())
                            instanceof Reachability.Unreachable))
                    .toList();
        }

        /**
         * The arms of the source nothing arrives at, named the way a reader of the model names one.
         *
         * <p>The arm and not the place. An arm is one obligation however often a helper carrying it
         * is called, and the copies stand at places of their own — a value refused at one call site
         * is nothing about the arm while another call site reaches it. So an obligation is one
         * nothing arrives at exactly where no copy of it survives {@link #owed}, which is the same
         * subtraction the count is made by rather than a second reading of the same places.
         *
         * <p><b>And named by what the author wrote, which is less than an obligation.</b> A fork the
         * caller decides is one obligation per rule handed in, and a reader that has the source's
         * own arm has no rule to tell them apart by — so one of these is here only where every
         * obligation it names is out. Answered per obligation and looked up by the arm, an arm
         * reachable under one supplied rule would come back unreachable because a sibling under
         * another is, which takes an obligation away on the strength of a different one.
         *
         * <p>Read as the difference and not by asking each arm for all of its copies: what the
         * count leaves out is what is out, and a second walk deciding it again is free to leave out
         * something the count kept.
         */
        public static Set<CoverageSites.AsWritten> unreached(
                List<CoverageSites.ArmSite> all,
                souther.compiler.check.PathReachability.Answers.AsRun reachable) {
            Set<CoverageSites.Obligation> stands = new java.util.LinkedHashSet<>();
            owed(all, reachable).forEach(site -> stands.add(site.obligation()));
            Set<CoverageSites.AsWritten> written = new java.util.LinkedHashSet<>();
            Set<CoverageSites.AsWritten> kept = new java.util.LinkedHashSet<>();
            for (CoverageSites.ArmSite site : all) {
                written.add(site.obligation().asWritten());
                if (stands.contains(site.obligation())) {
                    kept.add(site.obligation().asWritten());
                }
            }
            written.removeAll(kept);
            return java.util.Collections.unmodifiableSet(written);
        }

        /**
         * The arms a set of probes is about, which is asked here because here is where both are.
         *
         * <p>A probe is the number a run through an arm was recorded at; which arm that is, is what
         * the sites hold. Nothing further along has the sites, so a probe carried past this point
         * arrives everywhere else as a token its reader cannot resolve — which is what a fact about
         * a contradicted proof used to be made of.
         *
         * <p>Refused where no site of this behavior was numbered for it. A proof contradicted at an
         * arm is a proof about one of these arms, and a probe none of them was issued for means the
         * two lists are of different behaviors.
         */
        private static Set<CoverageSites.Obligation> armsBehind(List<CoverageSites.ArmSite> all,
                                                                Set<ArmProbe> probes) {
            Set<CoverageSites.Obligation> out = new java.util.LinkedHashSet<>();
            for (ArmProbe probe : probes) {
                CoverageSites.ArmSite site = all.stream()
                        .filter(each -> each.index().equals(probe))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException(
                                "a run was recorded at " + probe + ", which is no arm of these"));
                out.add(site.obligation());
            }
            return out;
        }

        /**
         * The arms of one behavior, with the ones nothing reaches taken out of what it is owed.
         *
         * <p>Taken out here and not where the probes are numbered. The plan says where
         * instrumentation is; this says which of it is owed a row, and the two are different
         * questions — a site with no probe could never disprove the reachability it was excluded by.
         *
         * <p>Three things can leave this weaker than complete, and each of them is written onto what
         * it is about rather than onto the measurement over all of them. A reading that did not run
         * out leaves the arms it may have lit and did not undecided, and leaves the arms it lit
         * alight. A fork whose occurrences nothing tells apart takes its own arms out of the count.
         * A proof a row has already disproved is not about any one arm at all — the arms stand where
         * they stand and what is in doubt is whether the set of them is the set this behavior owes.
         * The measurement's own weakening is assembled from those, and never read back to work out
         * what an arm came to: folded into one word, the second took the first's meaning — an arm no
         * row goes through, and nothing uncertain about it, stopped being reported because a helper
         * elsewhere in the body could not be told apart.
         */
        public static BranchEvidence measured(String behavior,
                                              List<CoverageSites.ArmSite> all,
                                              Set<ArmProbe> covered,
                                              Set<ArmProbe> awaiting,
                                              souther.compiler.check.PathReachability.Answers.AsRun reachable,
                                              WeakeningSet weakenings) {
            ArmAccount account = ArmAccount.of(owed(all, reachable), covered, awaiting, weakenings,
                    ArmCensus.of(armsBehind(all, reachable.provedWrong())));
            WeakeningSet by = account.weakening();
            return new BranchEvidence(by.isEmpty()
                    ? new Measurement.Complete<>(account.summary())
                    : new Measurement.Partial<>(account.summary(), by));
        }

        /**
         * The account, for a caller that has established there is one.
         *
         * <p>Throws where there is none, and that is what it is for. The accessors this replaces
         * answered a measurement with no value with an empty list and a zero, so a caller that
         * forgot to ask got a number that looked like a measurement and was not. Here the same
         * forgetting stops the caller rather than reaching a document. A caller writing a document
         * asks {@link #measured()} and is handed the value only where there is one; this is for the
         * ones that have already settled that there is.
         *
         * <p>And what it hands over is the whole of what a consumer may read. Every question a
         * surface has about the arms — how many, which ones, what is open about them and whether the
         * count is over the set the behavior owes — is {@link ArmSummary}'s. There is nothing else
         * here to ask, which is the point: the accessors beside this one answered from what weakened
         * the measurement, and every reader of them was working an arm's state out of a set of
         * reasons that were never about one arm.
         */
        public ArmSummary arms() {
            return measured.made().orElseThrow(() -> new IllegalStateException(
                    "a branch measurement with no arms was read for them: " + measured.why()));
        }

        /** Whether this behavior has arms for the measure to be about. */
        public boolean applicable() {
            return !(measured instanceof Measure.NotApplicable<ArmSummary>);
        }
    }

    /**
     * The arms every behavior of one module has, and which of them the rows reach.
     *
     * <p>A hit belongs to the behavior whose arm it is, whichever behavior's row lit it: a row about
     * {@code A} that calls {@code B} did go through {@code B}'s arm, and pretending otherwise would
     * report an arm as unreached that runs on every build. What is <em>not</em> inherited is the
     * question: a behavior nobody wrote a row for is not asked about its unreached arms at all. The
     * measurement is opted into by writing a row, and reaching a behavior sideways is not opting in.
     */
    public record BranchCoverage(String name) implements Key<Map<String, BranchEvidence>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, BranchEvidence>> compute(Db db) {
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            if (!prepared.present()) {
                return Answer.absent();
            }
            boolean instrumented = levelOf(db).runsInstrumentedRows();
            // Asked whatever the level is. The plan is read off the checked bodies and nothing in it
            // waits on a run, so taking `Plan.NONE` where the build did not ask for the instrumented
            // classes bought nothing and left a body that owes no arm looking like a body nobody
            // measured (issue #955).
            Bodies.Elaborated checked = db.ask(new Bodies.Checked(name)).value();
            CoverageSites.Plan plan =
                    checked == null ? CoverageSites.Plan.NONE : checked.plan();
            // Whether the bodies came back at all. Which behaviors have one is the model's answer
            // and is asked of the declarations below; this is the other question — whether what a
            // body holds could be read — and answering both from this map is what made a module the
            // compile stopped in report every behavior as one with no body (issue #996).
            boolean bodiesRead = checked != null;
            // Off the value already in hand, which is the same answer numberingOf asks the store
            // for: a module whose bodies were not read has no numbering, and its rows have no
            // account of a run to be read under one.
            Optional<SiteNumbering> numbering =
                    checked == null ? Optional.empty()
                            : Optional.of(SiteNumbering.of(checked.numberingIdentity()));
            Map<String, RowReading> byTarget = db.ask(new RowReadings(name)).value();
            // Sorted as they are gathered, by what the row that lit them states. A row that states
            // what it expects covers the arms it went through; a row whose answer is owed went
            // through them and asserts nothing about them, and the two arrive here as one set only
            // if this is the place that forgets which was which. Read off what each row states,
            // which is where a row's own text was read, rather than by going back to the source.
            Set<ArmProbe> lit = new LinkedHashSet<>();
            Set<ArmProbe> awaiting = new LinkedHashSet<>();
            for (RowReading observed : byTarget.values()) {
                for (RowOutcome row : observed.rowsSeen()) {
                    (awaitsItsAnswer(row) ? awaiting : lit).addAll(armsSeenIn(row, numbering));
                }
            }

            Map<String, souther.compiler.check.PathReachability.Answers.AsRun> reachable = db.ask(new Arrived(name)).value();

            return answerEveryBehavior(prepared.value(), behavior -> {
                // The arms, and not every site of the behavior. A comparison of a guard's condition
                // has a site of its own and is not a fork a row is in or out of, so counting it here
                // would report an arm the body does not have.
                List<CoverageSites.ArmSite> arms =
                        plan.arms(behavior.name());
                RowReading observed = RowReadings.readingFor(byTarget, behavior.name());
                souther.compiler.check.PathReachability.Answers.AsRun arrives =
                        reachable == null ? NOTHING_PROVEN
                                : reachable.getOrDefault(behavior.name(), NOTHING_PROVEN);
                BranchEvidence absent = whyNoArms(name, prepared.value().writesItsOwnBody(behavior),
                        bodiesRead, arms, arrives, instrumented, observed);
                if (absent != null) {
                    return absent;
                }
                List<ArmProbe> here = arms.stream().map(CoverageSites.ArmSite::index).toList();
                Set<ArmProbe> covered = new LinkedHashSet<>(lit);
                covered.retainAll(here);
                Set<ArmProbe> awaited = new LinkedHashSet<>(awaiting);
                awaited.retainAll(here);
                return BranchEvidence.measured(behavior.name(), arms, covered, awaited,
                        arrives, rowsBehind(observed));
            });
        }

        /**
         * The first gate the arm measurement did not get through, or null where it got through them
         * all.
         *
         * <p>One gate per condition, in the order the work happens in, so that what a caller reads back
         * is the thing that stopped it rather than whichever condition an expression happened to test
         * first.
         *
         * <p>Whether anything is owed here comes before what the build asked for, and it is read off
         * the model. A behavior that owes no arm owes none at every level, and answering
         * {@code NOT_ASKED} for it would hold a verdict open for a measurement that would find
         * nothing however it was made — which is the defect the applicability answer exists to
         * prevent, one size down from the {@code >->} composition it was written for (issue #955).
         *
         * <p><b>And a proof of absence in the model comes before either.</b> The two inapplicable
         * answers below are claims about what is written — this behavior has no body of its own,
         * this body owes no arm — so both are gated on the compile having got far enough to say so.
         * Both used to be read off what came back: the first from the elaborated bodies and the
         * second from the plan, which is itself read off them. A module the compile stopped in has
         * neither, so it answered {@code NO_BODY} for every behavior in it — beside a report line
         * saying {@code implemented} (issue #996).
         *
         * @param writesItsOwnBody what the declarations say, from the one reader of them
         * @param bodiesRead       whether the elaborated bodies came back, which is what the arms
         *                         and the plan below are read from
         */
        private static BranchEvidence whyNoArms(String module, boolean writesItsOwnBody,
                boolean bodiesRead,
                List<CoverageSites.ArmSite> arms,
                souther.compiler.check.PathReachability.Answers.AsRun arrives,
                boolean instrumented, RowReading observed) {
            if (!writesItsOwnBody) {
                return BranchEvidence.noArms(BranchEvidence.NoArms.NO_BODY);
            }
            if (!bodiesRead) {
                // The model says there is a body. Nothing read it, so what it owes is unknown —
                // which is not the same as owing nothing, and reads identically without this.
                return BranchEvidence.unelaborated(module);
            }
            // What is owed, and not what was numbered. An arm the rules prove nothing arrives at is
            // instrumented and is not owed, so a behavior whose every numbered arm is one of those
            // owes as little as a behavior that forks nowhere.
            if (BranchEvidence.owed(arms, arrives).isEmpty()) {
                return BranchEvidence.noArms(BranchEvidence.NoArms.NO_ARM_OBLIGATIONS);
            }
            if (!instrumented) {
                return BranchEvidence.notAsked(BranchEvidence.NotAsked.NOT_ASKED);
            }
            if (observed.armsUnseen()) {
                // Started and not finished, so it says what it went without. The entries that say
                // the instrumentation was not there are exactly what a reader needs to know why
                // there is no number, and they used to be somewhere else on the page.
                return BranchEvidence.unreadable(rowsBehind(observed));
            }
            // Nothing read is not the same as nothing written. Where a source could not be evaluated
            // at all, the rows this behavior is waiting on may be sitting in it, and answering
            // `NO_ROWS` would tell an author to write what is already there. The measure goes ahead
            // on what was seen and comes back undecided, which is what it is.
            if (observed.rowsSeen().isEmpty() && !observed.someRowsUnseen()) {
                return BranchEvidence.notAsked(BranchEvidence.NotAsked.NO_ROWS);
            }
            return null;
        }

        /**
         * What the rows behind an arm measurement leave it weaker by.
         *
         * <p>A row that did not finish went somewhere before it stopped, and what it went through
         * was dropped with it — so the arms it did not light are undecided rather than unreached,
         * and the arms that were lit are still lit. A source nothing evaluated leaves no row to
         * find at all. Both arrive here as the fact they are rather than as a word for how far the
         * measurement got.
         */
        private static WeakeningSet rowsBehind(RowReading observed) {
            Set<Weakening> out = new LinkedHashSet<>();
            // Both kinds are already here. A row that stopped is a reason of its own, written where
            // it stopped; this used to walk the dispositions beside them and say it a second time,
            // in a vocabulary that named the row without saying which source it is in (issue #996).
            for (Incompleteness.Met gap : observed.gaps()) {
                out.add(new Weakening.ObservationIncomplete(gap));
            }
            return WeakeningSet.ofAll(out);
        }
    }

    /**
     * What a module's sources saw of one behavior.
     *
     * <p>The rows alone, and what stopped them being seen is beside this rather than in it: a
     * {@link RowReading} holds one of these under a {@link Measurement}, and what that reading went
     * without is the measurement's. The two have to reach a reader together, because a measure with
     * only the rows cannot tell a case no row covers from a case a row it never saw covers — which
     * is why {@link RowReading} is what a measure is handed and this is not.
     *
     * @param rows what was observed
     */
    public record Observed(List<RowOutcome> rows) {

        public static final Observed NONE = new Observed(List.of());

        public Observed {
            rows = List.copyOf(rows);
        }
    }

    /**
     * How far the reading of one behavior's rows got, and what it read.
     *
     * <p><b>The one measure that can never be inapplicable.</b> A behavior has rows to read, even
     * where there are none of them, so there is nothing here for {@code NotApplicable} to be about
     * and this is a {@link Measurement} rather than a {@link Measure}. Which is what #996 was: the
     * measures counted <em>over</em> the rows may every one of them have nothing to be about — a
     * behavior with no body, an output that is not a sum, rules that divide no position — and then
     * a run that went without something had nobody left to carry it. The reading always can.
     *
     * <p>{@code Complete} where every source was observed and every row came to a decision;
     * {@code Partial} where rows were read and something was gone without; {@code FailedToMeasure}
     * where nothing was read at all; {@code NotMeasured} where the level does not read rows. So
     * zero rows read in full and no rows read are two states rather than one empty list, which is
     * a question three readers used to answer by hand.
     *
     * <p>What the reading went without lives in the weakening and nowhere else. It used to sit
     * beside the rows as a list, and every reader took what it wanted from that list by its own
     * rule — five of them, disagreeing about which reasons bear on what. Which of them bear on a
     * given measure is still that measure's own answer; what has changed is that they all read one
     * thing, and that whatever none of them takes is still carried here.
     */
    public record RowReading(Measurement<Observed> measured) {

        /**
         * A reading that read no rows and went without nothing: this behavior has none written.
         *
         * <p>Not the same as {@link #NOT_ASKED}, and not the same as rows nothing came back from.
         *
         * <p><b>An answer and not a default.</b> Both of these are things {@link RowReadings}
         * says, and which of them a behavior gets is its answer to give — so a caller reaching for
         * one where the map did not answer is deciding what the producer said from what it did not
         * say. {@link RowReadings#readingFor} is how a caller gets one. What is left here is
         * building a fixture, which has no producer to ask.
         */
        public static final RowReading NONE =
                new RowReading(new Measurement.Complete<>(Observed.NONE));

        /** Nothing was asked of a behavior's rows, which is not a reading that found none. An
         *  answer, for the reason {@link #NONE} is. */
        public static final RowReading NOT_ASKED =
                new RowReading(new Measurement.NotMeasured<>(RowReading.NotAsked.ROWS_NOT_ASKED));

        /** Why a reading was not made. Its own enum: what the level did not ask for is not one of
         *  the ways a reading that was made came out. */
        public enum NotAsked implements NotMeasuredReason {
            /** This build does not read rows, so nothing was seen and nothing is owed about it. */
            ROWS_NOT_ASKED;

            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_RUN;
            }
        }

        /** Nothing came back at all, so a measure over what remains is over none of them. */
        public enum Unavailable implements FailureReason {
            ROWS_UNAVAILABLE;

            @Override
            public MeasureReason.About about() {
                return MeasureReason.About.THE_BEHAVIOR;
            }
        }

        /**
         * The reading of {@code rows}, given what its sources went without.
         *
         * <p>The one place the states are chosen between, so that no caller pairs rows with an
         * account of them they do not go with.
         */
        public static RowReading of(List<RowOutcome> rows, List<Incompleteness> gaps) {
            if (gaps.isEmpty()) {
                return new RowReading(new Measurement.Complete<>(new Observed(rows)));
            }
            Set<Weakening> by = new LinkedHashSet<>();
            for (Incompleteness gap : gaps) {
                by.add(Weakening.ObservationIncomplete.of(gap));
            }
            WeakeningSet went = WeakeningSet.ofAll(by);
            return new RowReading(rows.isEmpty()
                    ? new Measurement.FailedToMeasure<>(Unavailable.ROWS_UNAVAILABLE, went)
                    : new Measurement.Partial<>(new Observed(rows), went));
        }

        /**
         * The rows this reading saw, which is none where it saw none and none where it read
         * nothing at all.
         *
         * <p>Named for what it is. It is a projection and not the measurement's value: those two
         * nothings are different states and this hands back the same empty list for both, so a
         * caller asking whether it is empty has asked a question this cannot answer. What the
         * reading came to is {@link #measured}, and a caller that needs the value asks it for one.
         *
         * <p>Here because every measure counted over the rows walks them and does not care which
         * nothing it got — it counts what it was given and says what it went without separately.
         * A caller that reads a meaning off the emptiness is the defect this issue is about
         * (issue #996).
         */
        public List<RowOutcome> rowsSeen() {
            return measured.made().map(Observed::rows).orElseGet(List::of);
        }

        /** What this reading went without, as the reasons themselves, each once and with everywhere
         *  it was met. One projection, shared with the document that prints them
         *  ({@link WeakeningSet#observationCauses}). */
        public Set<Incompleteness.Met> gaps() {
            return measured.weakening().observationCauses();
        }

        /** Whether everything there was to see was seen. Only then does an unreached thing mean
         * nothing reaches it, rather than nothing was watching. */
        public boolean complete() {
            return measured instanceof Measurement.Complete<Observed>;
        }

        /**
         * Whether the arms were asked for and not produced.
         *
         * <p>The rows then ran without instrumentation and carry no arms at all, which reads
         * exactly like a body no row goes through. Not the same as arms nobody asked for.
         */
        public boolean armsUnseen() {
            return gaps().stream()
                    .anyMatch(gap -> gap.fact().code()
                            == Incompleteness.Code.INSTRUMENTATION_ABSENT);
        }

        /**
         * Whether some rows were never seen at all, as against seen and not finished.
         *
         * <p>The difference decides who has to notice. A row that ran out of time is here, and says
         * so: its state is dropped rather than read, so it arrives with no inputs and no expected arm
         * and every measure that reads a row finds one it cannot place. A source that could not be
         * evaluated leaves no row to find — the rows it holds may cover anything, and a measure over
         * the rows that remain is a measure over some of them with nothing in it to say so.
         *
         * <p>Which codes say it is each code's own answer ({@link Incompleteness.Code#leftNoRowRead}).
         */
        public boolean someRowsUnseen() {
            return gaps().stream().anyMatch(gap -> gap.fact().code().leftNoRowRead());
        }
    }

    /**
     * Every behavior of one module, with what its sources saw and what stopped them.
     *
     * <p>A reason with no behavior to attach it to — a whole source that could not be evaluated —
     * belongs to all of them: nothing in it was seen, so nothing about any behavior it holds rows for
     * is settled.
     */
    static Map<String, RowReading> rowsOf(Db db, String module) {
        Output.RowsRead.Of read = db.ask(new Output.RowsRead(module)).value();
        if (read == null) {
            return Map.of();
        }
        Map<String, RowReading> out = new LinkedHashMap<>();
        // What ran, which is what a measure is counted over. A row nothing came back for is not a
        // row a measure reads and is not one it may pass over either: what it would have covered is
        // unknown, which is what the gaps beside it say.
        read.byBehavior().forEach((behavior, its) ->
                out.put(behavior, RowReading.of(its.ran(), read.gapsFor(behavior))));
        return new WithFallback(out, read.everywhere());
    }

    /** The map above, answering for a behavior nothing named with whatever stopped every source. A
     * behavior with no rows of its own is still not measurable where a source went unread. */
    private static final class WithFallback extends java.util.AbstractMap<String, RowReading> {

        private final Map<String, RowReading> known;
        private final RowReading fallback;
        private final boolean nothingEverywhere;

        WithFallback(Map<String, RowReading> known, List<Incompleteness> everywhere) {
            this.known = known;
            this.nothingEverywhere = everywhere.isEmpty();
            this.fallback = RowReading.of(List.of(), everywhere);
        }

        @Override
        public RowReading get(Object key) {
            RowReading there = known.get(key);
            return there != null ? there : fallback;
        }

        @Override
        public RowReading getOrDefault(Object key, RowReading absent) {
            RowReading there = known.get(key);
            return there != null ? there : (nothingEverywhere ? absent : fallback);
        }

        @Override
        public Set<Entry<String, RowReading>> entrySet() {
            return known.entrySet();
        }

        /**
         * Two of these are one where they answer alike, which is over what is written down here and
         * not over the entries alone.
         *
         * <p>What this says is what it holds and what it answers for a name it has no entry for.
         * Compared as a map — which is what comparing the entries is — two of these would be one
         * wherever the rows agreed, and a compile that could read every source would be
         * indistinguishable from one that could not read any of them.
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof WithFallback that && known.equals(that.known)
                    && fallback.equals(that.fallback)
                    && nothingEverywhere == that.nothingEverywhere;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(known, fallback, nothingEverywhere);
        }
    }

    /**
     * Rows that would fill what every behavior of one module has not covered.
     *
     * <p>Its own key rather than part of the coverage, because filling the combinations searches the
     * pair space, which nobody who only wanted a report should pay for. The rows at the edges are not
     * that: {@link Boundaries} builds one value per line to find out whether a line can be written at,
     * and the row that comes of it is read from there rather than searched for again.
     *
     * <p>The two kinds of row stay apart in the answer. Filling a combination and writing a row at an
     * edge are different requests, asked with different flags, and a caller that merged them could not
     * take one without the other.
     *
     * <p>What a row of this behavior stands its dependencies in with is not here. A row of a
     * behavior with nothing to fill can still be owed for a declaration's line, and such a row
     * needs one too — so it is asked of every behavior that contributes a row rather than of the
     * fillings, which are only some of them.
     */
    public record Filling(souther.compiler.partition.FillResult composed,
                          Generator.GenerationResult boundaries,
                          Generated.RowsForRules rules,
                          List<GenerationDisposition> generation) {

        public Filling {
            generation = List.copyOf(generation);
        }

    }

    /**
     * One finding, and what the generator can do about it.
     *
     * <p>Held as a pair rather than as rows with the finding forgotten, because what an author needs
     * to read is which part of the shortfall was answered and which was not. A block that printed
     * only what it managed reads as though it filled everything.
     *
     * <p>Named for generation and not for gaps, which is what it used to be called. A gap is what a
     * build refuses over, and that is not what decides whether a row can be composed for a
     * finding: the two are separate readings of one set of findings, and a name that said gap kept
     * the older arrangement alive in every reader that met it.
     */
    public record GenerationDisposition(Finding finding, Optional<ObligationIdentity> item,
                                        GenerationOutcome outcome) {

        public GenerationDisposition {
            item = item == null ? Optional.empty() : item;
        }

        /**
         * The finding and what came of it, for a finding nothing offers a row for.
         *
         * <p>Empty and not a name for the finding: what a row would be offered for is the thing an
         * offering answers, and a measure this compiler could not make is not one of those. A
         * reader asking whether something else answers this has nothing to ask about, which is what
         * having no item says.
         */
        public GenerationDisposition(Finding finding, GenerationOutcome outcome) {
            this(finding, Optional.empty(), outcome);
        }
    }

    public record Generated(String name, String behavior) implements Key<Filling> {

        @Override
        public String module() {
            return name;
        }

        /**
         * What a search of this module's behaviors filled.
         *
         * <p>Two of the queries it asks are asked and not read, for the reason written on
         * {@link Coverage#compute}: the ask is what records the edge, and taking it out changes
         * when this is recomputed.
         */
        @Override
        public Answer<Filling> compute(Db db) {
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            // What a row is offered for is what the coverage found, so a coverage that did not
            // answer leaves this nothing to offer from. Absence and not `PartitionEvidence.NONE`:
            // that answer says the model holds nothing to cover, and read for this one it turned a
            // compile that stopped into a module with no work in it (issue #996).
            Answer<Map<String, PartitionEvidence>> coverage = db.ask(new Coverage(name));
            // What the module states of a parameter's type is read off its definitions and the
            // behaviors a body of it can name, and both are answers that can be absent. Absence and
            // not an empty table, for the reason above: read as a table with nothing in it, a module
            // whose signatures could not be worked out became a module that states no value of any
            // type, and every row it is offered was composed as though the author had written none.
            Answer<Map<String, Hir.FnDef>> definitions =
                    db.ask(new Bodies.ModuleDefinitions(name));
            Answer<Map<ValueName.Behavior, Sig>> reachable = db.ask(new Bodies.Reachable(name));
            if (!prepared.present() || !scope.present() || !sigs.present() || !coverage.present()
                    || !definitions.present() || !reachable.present()) {
                return Answer.absent();
            }
            souther.compiler.query.Bodies.Elaborated checked =
                    db.ask(new Bodies.Checked(name)).value();
            Map<String, souther.compiler.core.Core> bodies =
                    checked == null ? Map.of() : checked.behaviorBodies();
            souther.compiler.coverage.CoverageSites.Plan plan =
                    checked == null
                            ? souther.compiler.coverage.CoverageSites.Plan.NONE : checked.plan();
            // And what its numbers mean, which the empty plan above has none of: it stands for
            // every module whose bodies were not read and so is nobody's numbering. Taken off the
            // value in hand instead, a module without one says it has none.
            Optional<SiteNumbering> numbering =
                    checked == null ? Optional.empty()
                            : Optional.of(SiteNumbering.of(checked.numberingIdentity()));
            Map<String, RowReading> byTarget = db.ask(new RowReadings(name)).value();
            Map<String, InputDomain> readInputs = db.ask(new Inputs(name)).value();
            // What the guards above each place leave, asked once for the module and read by
            // every measure below — the same reason the reading of the input is.
            db.ask(new PathReached(name));
            Symbols symbols = scope.value();

            // And what each behavior states about its answer, which draws lines of its own.
            db.ask(new Bodies.StatedContracts(name));

            // The whole account. A generation is a surface with a reader: what it offers rows for
            // is everything the model is owed, and a rule of a decision is one of those.
            List<Finding> findings = accountOf(db, name);
            Map<String, PartitionEvidence> partitions = coverage.value();

            Hir.SpecBehavior spec = specOf(prepared.value(), behavior);
            souther.compiler.partition.Partitions.Partitioning divided =
                    db.ask(new Divided(name, behavior)).value();
            if (spec == null || divided == null) {
                return Answer.absent();
            }
            // What this generation composes against, taken from the one classification of what a
            // measure of this behavior works from rather than looked up here. A generation that
            // read the signatures and the readings itself would be deciding a second time what a
            // missing entry means.
            if (!(BoundaryForMeasurement.of(sigs.value(), readInputs, spec)
                    instanceof BoundaryForMeasurement.Derived(
                            Sig sig, InputForMeasurement.Local(Hir.SpecBehavior _,
                                    InputDomain read)))) {
                return Answer.absent();
            }
            // Asked whatever the level is. Somebody asking for the rows is what a generation is,
            // and the rows at an edge are what a composed value settles — so this pays for the
            // composing because it is what was asked for, not because a dial was turned up.
            List<BorderAssessment> edges = db.ask(new BoundarySearch(name, behavior)).value();
            if (edges == null) {
                // A search that did not answer leaves this nothing to offer from, which is the
                // reading the coverage above already gets. Read as no lines, the findings would be
                // walked against a behavior said to have none — and the first one about a line
                // would come back as the search and the finding being about different lines, which
                // is a sentence about neither.
                return Answer.absent();
            }
            // This behavior's own, grouped here because that is what a generation is asked for.
            // What each finding is about is the finding's; a walk that read them out of a map keyed
            // by behavior would be reading the grouping as the answer.
            List<Finding> owed = findings == null ? List.of()
                    : findings.stream().filter(each -> each.subject().isBehavior(behavior)).toList();
            souther.compiler.partition.MeasuredInput subject = subjectOf(db, name, spec);
            if (subject == null) {
                return Answer.absent();
            }
            // What this run is asked for, settled before the search and before anything that can
            // stop it. Every way out of the generation below holds this same list.
            souther.compiler.partition.GenerationPlan asked =
                    planFor(subject, owed, partitions.get(behavior),
                            checked == null ? CoverageSites.Plan.NONE : checked.plan());
            souther.compiler.partition.FillResult composed;
            try {
                composed = rowsFor(spec, sig, Shapes.ruleReading(db, name).value(), asked,
                        baselines(name, spec, sig, definitions.value(), reachable.value(),
                                prepared.value(), symbols,
                                // What the declarations of this module denote, and not what a check
                                // settled about them: a generation is a measurement of a module
                                // that need not have been accepted — this same answer is worked out
                                // where a body did not check — and a declaration the check said
                                // nothing about is a value this cannot reach rather than a fault.
                                new souther.compiler.check.ResolvedFieldTypes(symbols)),
                        bodies.get(behavior), plan, numbering,
                        RowReadings.readingFor(byTarget, behavior),
                        constructing(db, name),
                        read,
                        runningRowsOf(trialling(db, name), behavior, sig, numbering,
                                RequiredDependencies.of(db, name, behavior)),
                        // What every row this composes stands the dependencies in with, settled
                        // before the search so that a candidate is run in the environment the row
                        // it becomes goes out with.
                        supplying(db, name, behavior, subject),
                        levelOf(db).runsInstrumentedRows(),
                        db.ask(new Front.Adequacy()).value().generation());
            } catch (LinkageError _) {
                // The generated classes would not link, so nothing can be built to find out
                // what a model admits. Saying so is not the same as saying the combinations are
                // impossible, so none of them is reported as one.
                //
                // Caught around the search and not around the answer. A finding's answer is
                // owed whatever the search did, and a failure that skipped the walk over the
                // findings would take them out of a list that is meant to hold every one —
                // which is the same defect the list was written against, arriving as control
                // flow rather than as a value.
                composed = souther.compiler.partition.FillResult.nothingWasLookedFor(asked,
                        Generator.UnresolvedCombination.Reason.LINKAGE_FAILED,
                        List.of(new souther.compiler.partition.GenerationReason
                                .LinkageFailed(behavior)));
            }
            // The rows the requirement search already stood in each rule, which is where a row for
            // a rule comes from. Not a second search: settling whether a rule is owed a row is
            // composing a value, running it and asking what rule the run took, and a value that
            // came back certified is a row an author can be handed.
            RowsForRules rules = rowsForRules(db, name, behavior, owed,
                    RowReadings.readingFor(byTarget, behavior),
                    db.ask(new Front.Adequacy()).value().generation(), composed.rows().size());
            return Answer.of(new Filling(composed, offeredHere(behavior, edges), rules,
                    dispositions(owed, rules,
                            // This behavior's readings and no others. What a finding of this
                            // behavior is about is a line its own rules drew, and such a line is
                            // read only in the body that wrote it — so a wider account walks
                            // readings this has no finding at, and a request about one behavior
                            // pays for the searches of the rest.
                            accountFor(db, name, new GenerationScope.Behavior(behavior)),
                            composed)));
        }

        /**
         * What the generator can do about each finding, and about every one of them.
         *
         * <p>Every finding, and not the ones a build refuses over. Those are two questions asked of
         * one set of findings, and the first used to be the doorway of the second — so a finding
         * nobody had asked to be held to went unanswered, and no strategy could be written for one
         * until something gated on it first. What a build refuses over does not reach this.
         *
         * <p>Walked over the findings rather than over the strategies, which is the other half. A
         * walk over the strategies answers for the findings somebody wrote a strategy for and
         * leaves the rest unmentioned, and a block that says nothing about a finding it printed two
         * lines above reads as though it had filled everything.
         *
         * <p>Which answer a finding gets is decided by whether a strategy takes findings of that
         * kind, and never by what a search came back with. A finding row synthesis is not about at
         * all is {@link GenerationOutcome.NotApplicable}, which is not a strategy waiting to be
         * written: nothing anyone writes turns a measure this compiler could not make into a row
         * somebody can write. The shapes are listed one at a time so that one added later does not
         * compile until somebody has said which of the four it is.
         */
        private static List<GenerationDisposition> dispositions(List<Finding> findings,
                                                      RowsForRules rules,
                                                      BorderAccount account,
                                                      souther.compiler.partition.FillResult
                                                              composed) {
            List<GenerationDisposition> out = new ArrayList<>();
            for (Finding finding : findings) {
                GenerationOutcome none = whereNoRowCouldAnswer(finding.about());
                out.add(new GenerationDisposition(finding, itemOf(finding),
                        none != null ? none
                        : switch (finding.about()) {
                            // Asked of the module's account, which is where a row for a point is
                            // searched for, and asked of the line: the finding is about the point
                            // however many readings there are, and a row composed at any of them
                            // answers it.
                            case About.APointOfABorder(var point) ->
                                    account.outcomeForTheLine(point.point());
                            case About.ACaseNoRowAppliesItTo(var _, var _, var owed) ->
                                    atCase(owed, composed);
                            case About.AClassNoRowIsIn(var missing) -> atClass(missing, composed);
                            case About.AnArmNoRowGoesThrough(var arm) -> atArm(arm, composed);
                            case About.ARuleNoRowTakes(var _, var ruled) ->
                                    atRule(finding, ruled.rule(), rules);
                            // The ones above, which is what `none` was not null for.
                            // A line a declaration is owed is not one of this behavior's findings,
                            // so nothing reaches here with one.
                            case About.APointOfADeclaredBorder _,
                                    About.ACaseNoRowExpects _, About.ACaseNothingWasSeenToProduce _,
                                    About.ARowAtAnArmAwaitsItsAnswer _, About.AnUnansweredRow _,
                                    About.APositionNoLineDivides _,
                                    About.APositionThisCouldNotRead _,
                                    About.ARuleWithoutALine _, About.ARuleNothingClassified _,
                                    About.APositionWhoseRulesWereNotReached _,
                                    About.APositionReadWiderThanItsRules _,
                                    About.AQuestionNothingAnswered _ ->
                                    throw new IllegalStateException(finding.about().toString());
                        }));
            }
            return out;
        }

        /**
         * What a row would be offered for, which the finding says of itself.
         *
         * <p>Asked of the finding and worked out nowhere. Whether a finding is about something a
         * row is owed for is the shape's own answer ({@link About.OfAnObligation}) and so is which
         * thing it is, so there is nothing here to decide: a walk that named the obligation again
         * would be a second identity for one thing, free to say something the account never said —
         * which is what the case of an input had, and why a case and the class of its position
         * could be two entries of one account.
         *
         * <p>Empty for the rest, which are findings no row answers: a measure this compiler could
         * not make, a position the model draws no line through, a row waiting for its answer.
         */
        private static Optional<ObligationIdentity> itemOf(Finding finding) {
            return finding.about() instanceof About.OfAnObligation it
                    ? Optional.of(it.obligationIdentity()) : Optional.empty();
        }

        /**
         * What a row composed for something that asks nothing of the dependencies stands them in
         * with.
         *
         * <p>Every dependency the behavior requires, answered out of what its own answer type
         * leaves. A row for a class or an arm is composed from what the positions divide into and
         * says nothing about what a dependency answers; a row of a behavior that requires one is
         * still a row nothing applies, so what it takes to be run goes out with it.
         *
         * <p>Composed once for the behavior rather than once per row. What the way asks is what
         * differs between rows, and these are the answers of a way that asks nothing — so a value
         * per row would be the same value composed as many times as the block is long.
         *
         * <p>What could not be composed comes back as that and not as nothing to compose. A row
         * offered without a stand-in it needs is a row a person completes and cannot run, so the
         * rows of such a behavior are held back where they are read — which takes telling the two
         * apart, and an empty list tells nobody anything.
         */
        private static AnswersStoodIn supplying(
                Db db, String module, String behavior,
                souther.compiler.partition.MeasuredInput subject) {
            AnswersForARule answers = answering(db, module, behavior, subject);
            return answers == null
                    ? new AnswersStoodIn.NothingComposed(Generator
                            .UnresolvedCombination.Reason.NOTHING_STANDS_IN_FOR_A_DEPENDENCY)
                    : answers.of(souther.compiler.partition.AnswersDemanded.NOTHING);
        }

        /**
         * A row for each rule of this behavior something was seen standing in.
         *
         * <p>Read off the search that settled which rules are owed a row rather than searched for
         * again. That search composes a value, runs it and asks the reading which rule the run
         * took, and keeps it only where the answer is this rule — so what it holds is a stimulus
         * certified to take the way, which is the whole of what a row for one is. Composed a second
         * time here, the two searches would be free to come to different values for one rule, and
         * the block would offer a row the account had settled nothing with.
         */
        private static RowsForRules rowsForRules(
                Db db, String module, String behavior, List<Finding> owed, RowReading observed,
                souther.compiler.partition.AdequacyPolicy.OfTheGeneration budget,
                int alreadyOffered) {
            // The rules this behavior is owed a row for, which is what the findings say. Read off
            // the search instead, a row would be offered for a rule nothing reported — and a rule
            // goes unreported where the reading could not place every row, which is exactly where
            // a row handed to a person may be one already written.
            Set<DecisionRule> asked = new LinkedHashSet<>();
            for (Finding finding : owed) {
                // Only where the account can say the rule is missing. A finding whose measurement
                // went without something says a row may take the rule already, and a proposal is
                // work offered against an obligation established as missing — offered against one
                // that is not, the block hands a person a row that may be in the file in front of
                // them.
                if (finding.about() instanceof About.ARuleNoRowTakes(var _, var ruled)
                        && finding.weakenedBy().isEmpty()) {
                    asked.add(ruled.rule());
                }
            }
            if (asked.isEmpty()) {
                return new RowsForRules(asked, Map.of(), null);
            }
            // Rows exist that nothing read, so what is left uncovered is unknown and a row handed
            // to a person may already be sitting in the file that could not be evaluated. The same
            // answer the fill gives, for the same reason: a row for a rule is a row.
            if (observed.someRowsUnseen()) {
                return new RowsForRules(asked, Map.of(),
                        Generator.UnresolvedCombination.Reason.THE_ROWS_WERE_NOT_READ);
            }
            Map<DecisionRule, RuleSettlement> settled =
                    db.ask(new DecisionSearch(module, behavior)).value();
            if (settled == null) {
                return new RowsForRules(asked, Map.of(), null);
            }
            Map<DecisionRule, Generator.GeneratedRow> out = new LinkedHashMap<>();
            boolean stopped = false;
            for (Map.Entry<DecisionRule, RuleSettlement> each : settled.entrySet()) {
                if (!asked.contains(each.getKey())
                        || !(each.getValue().requirement()
                                instanceof RuleRequirement.Required(var stoodBy))) {
                    continue;
                }
                // What one block may hand a person, counted over every row in it. A body of five
                // independent decisions states as many rules as their ways multiply to, and a
                // block with a row apiece is the list nobody reads that the limit stands between
                // an author and. Where it stops, the rules left over say so rather than going
                // quiet.
                if (alreadyOffered + out.size() >= budget.rowLimit()) {
                    stopped = true;
                    break;
                }
                out.put(each.getKey(), new Generator.GeneratedRow(
                        List.of(new Generator.Purpose.ForADecisionRule(each.getKey())),
                        stoodBy.inputs(), stoodBy.answers()));
            }
            return new RowsForRules(asked, out, stopped
                    ? Generator.UnresolvedCombination.Reason.THE_BLOCK_IS_AS_LONG_AS_IT_MAY_BE
                    : null);
        }

        /**
         * The rules a run was asked to offer a row for, the rows it offers, and why the rest have
         * none.
         *
         * <p>The three together because a rule with no row here is not a rule nothing stood in: the
         * requirement search stood something in every one of these, and what is missing is a row in
         * this block. Held apart, whoever answered for such a rule would have an absence to make a
         * sentence out of.
         *
         * <p><b>Asked for and composed are two sets.</b> What this run was set is every rule the
         * account reports missing; what it composed is as many of those as the block has room for.
         * Read off the second, a rule the limit stopped at would be a rule nobody weighs a row
         * against — and a row composed for a class that happens to take that rule as well would
         * discharge it with nothing in a position to notice, which is the whole of what one
         * identity for the two was introduced to make possible.
         *
         * @param asked         every rule this behavior is owed a row for
         * @param byRule        the row a person is handed for each rule that has one
         * @param whyNotTheRest null where every rule asked for has one
         */
        public record RowsForRules(Set<DecisionRule> asked,
                                   Map<DecisionRule, Generator.GeneratedRow> byRule,
                                   Generator.UnresolvedCombination.Reason whyNotTheRest) {

            /** A run that was asked for no rule's row, which is what a behavior nothing searched
             *  has. An answer and not an empty map standing in for one. */
            public static final RowsForRules NOTHING =
                    new RowsForRules(Set.of(), Map.of(), null);

            public RowsForRules {
                asked = Ordered.set(asked);
                byRule = Ordered.map(byRule);
                if (!asked.containsAll(byRule.keySet())) {
                    throw new IllegalArgumentException(
                            "a row was composed for a rule this run was not asked about");
                }
            }
        }

        /**
         * The row the requirement search stood in one rule.
         *
         * <p>There is always one. A rule is a finding only where that search came back with a value
         * it had run and seen take the way, so a finding here with no row is the two readings of
         * one search's answer disagreeing rather than a search that came to nothing.
         */
        private static GenerationOutcome atRule(Finding finding, DecisionRule rule,
                                                RowsForRules rules) {
            Generator.GeneratedRow row = rules.byRule().get(rule);
            if (row != null) {
                return new GenerationOutcome.Generated(List.of(row));
            }
            // A rule the account cannot call missing, which is not a measure nobody made. The
            // reading of the runs is what is in the way rather than anything about the rule, and a
            // row is not offered against an obligation nothing has established.
            if (!finding.weakenedBy().isEmpty()) {
                return new GenerationOutcome.NotApplicable(GenerationOutcome.NotApplicable.Reason
                        .THE_MEASURE_DOES_NOT_ESTABLISH_THIS);
            }
            if (rules.whyNotTheRest() == null) {
                throw new IllegalStateException(
                        "a rule is owed a row, nothing stood in it and nothing says why: " + rule);
            }
            return new GenerationOutcome.CannotGenerate(new Generator.UnresolvedCombination(
                    List.of(), rules.whyNotTheRest()));
        }

        /**
         * The arm's own attempt, read off what the search for the combinations made.
         *
         * <p>A combination of the body's own decisions is a way through each of the forks it reads,
         * so a row composed for one takes an arm of each — and the row that answers a finding about
         * one of them is found by that arm's own number. The two searches used to be two worlds:
         * the combinations composed rows before any finding was consulted, while the finding about
         * an arm was told nothing composes an input for one. Both were true and they were about the
         * same rows.
         *
         * <p>And where nothing was tried, what the reading of the body says about arriving at the
         * arm — which is an answer about the arm and never an absence. An arm no combination
         * claimed used to have no entry at all, and the sentence read off that absence said rows
         * are composed for classes and combinations and nothing takes this arm, of a body two of
         * whose own classes walk straight into it.
         *
         * <p>There is no absence to read now. A fill is total over the plan it was asked with, and
         * this finding is one of the arms that plan names — so the entry is there whatever the run
         * did, including the runs that never looked at anything.
         */
        private static GenerationOutcome atArm(
                CoverageSites.ArmSite arm,
                souther.compiler.partition.FillResult composed) {
            // Asked at the place this finding names, of the arm the run was asked about. A finding
            // names one site of the arm it is about and the plan is asked for every splice of it,
            // so a key built from the one site alone found nothing wherever the arm stood in the
            // body more than once.
            souther.compiler.partition.ArmDisposition answer =
                    composed.discharge().at(arm.index());
            if (answer == null) {
                throw new IllegalStateException(
                        "a finding names an arm this run was not asked about: " + arm.index());
            }
            return switch (answer) {
                case souther.compiler.partition.ArmDisposition.Built built ->
                        new GenerationOutcome.Generated(List.of(composed.rowFor(built.rowId())));
                // What every place a row was looked for came to, all of it. They are not one fact
                // and they do not order against each other: one the model refuses says the arm may
                // be unreachable, one the search stopped at says nothing at all, and a reader
                // handed whichever came first was handed the order the search happened to walk.
                case souther.compiler.partition.ArmDisposition.Unresolved none ->
                        new GenerationOutcome.CannotGenerate(none.why());
                case souther.compiler.partition.ArmDisposition.NoWayIn none ->
                        nothingReaches(none);
            };
        }


        /**
         * What a reader is told about an arm nothing was composed for, from what the reading of the
         * body says about arriving there.
         *
         * <p>Two kinds of news and the reading is what tells them apart. A run reaching the arm is
         * something the model settles — no row changes it, and asking an author to write one sends
         * them after a row that cannot exist. Everything else is this compiler falling short of
         * saying what steers a row there, and a row for such an arm may be the easiest one in the
         * file to write by hand.
         *
         * <p>Over every place the arm stands in, and the model has to settle all of them. One place
         * this compiler could not read the way to leaves a row for the arm writable, so the arm is
         * our shortfall however many of its other places the model refuses — and what is missing at
         * each of those places is carried whole, since they are not one fact and do not order
         * against each other.
         */
        private static GenerationOutcome nothingReaches(
                souther.compiler.partition.ArmDisposition.NoWayIn none) {
            if (none.theModelSettlesIt()) {
                return new GenerationOutcome.NotApplicable(
                        GenerationOutcome.NotApplicable.Reason.A_FACT_ABOUT_THE_MODEL);
            }
            SequencedSet<GenerationOutcome.NotSupported.Reason> missing = new LinkedHashSet<>();
            for (souther.compiler.reading.PathAccess access : none.access()) {
                if (access instanceof souther.compiler.reading.PathAccess.Unsupported(var why)) {
                    missing.add(switch (why) {
                        case NO_WAY_IN_CAN_BE_NAMED -> GenerationOutcome.NotSupported.Reason
                                .NO_WAY_INTO_THIS_ARM_CAN_BE_NAMED;
                        case WAYS_NOT_ENUMERABLE -> GenerationOutcome.NotSupported.Reason
                                .THE_WAYS_INTO_THIS_ARM_ARE_NOT_ENUMERABLE;
                        case MORE_WAYS_IN_THAN_ARE_READ -> GenerationOutcome.NotSupported.Reason
                                .MORE_WAYS_IN_THAN_THE_READING_HOLDS;
                        case RUNS_WHERE_SOMETHING_CALLS_IT -> GenerationOutcome.NotSupported.Reason
                                .THE_ARM_RUNS_WHERE_SOMETHING_CALLS_IT;
                        case THE_CONSTRUCTION_DECIDES_IT -> GenerationOutcome.NotSupported.Reason
                                .A_CONSTRUCTION_DECIDES_THIS_ARM;
                    });
                }
            }
            return new GenerationOutcome.NotSupported(missing);
        }

        /**
         * The class's own attempt, read off what the search for it made.
         *
         * <p>Found by identity — the position's own name and the class's own id — which is the same
         * pair the search was asked for. The rows it offers used to be matched back by the words in
         * their names, which is a spelling two positions of one type share, and it was the search
         * that decided what to compose while the finding went looking for something to claim.
         *
         * <p>Nothing is built here. The search is made once, where the rows are, and this reads
         * what it came to: a second attempt would be a second answer about one class, and the two
         * would differ the first time either side of the search moved.
         */
        private static GenerationOutcome atClass(PartitionEvidence.AxisClass missing,
                                                 souther.compiler.partition.FillResult composed) {
            // The spelling the search labels this class with, which is what the block prints beside
            // the rows. Written another way here, one class came out under two names — the
            // search's `c.f=C` and this one's `C at c.f` — and the block, which drops a line it has
            // already said, said the same fact twice because the two lines were not the same line.
            return atClass(missing.axis().at(), missing.name(), composed);
        }

        /**
         * The same, for a finding that names its class some other way.
         *
         * <p>One reader of what a search made, whatever the finding was about. A case of an input
         * and a class of a position are two findings about one class, and answering them from two
         * readings of one search is two answers that agree until either moves.
         *
         * <p>Total over the plan, so there is no absence here to interpret. A class this run was
         * not asked about is a finding and a plan disagreeing about what is owed, which is a defect
         * in this compiler rather than something to write a sentence about — the two are made from
         * one reading of the rows and cannot honestly differ.
         */
        private static GenerationOutcome atClass(souther.compiler.partition.AxisId at,
                                                 String classId,
                                                 souther.compiler.partition.FillResult composed) {
            souther.compiler.partition.ClassDisposition answer =
                    composed.discharge().at(new ClassOfAPosition(at, classId));
            if (answer == null) {
                throw new IllegalStateException(
                        "a finding names a class this run was not asked about: " + at + "=" + classId);
            }
            return switch (answer) {
                case souther.compiler.partition.ClassDisposition.Built built ->
                        new GenerationOutcome.Generated(List.of(composed.rowFor(built.rowId())));
                case souther.compiler.partition.ClassDisposition.Unresolved none ->
                        new GenerationOutcome.CannotGenerate(none.why());
            };
        }

        /**
         * What the axes can do about a case of an input no row applies the behavior to.
         *
         * <p>A case of a position is one of the classes that position divides into, so the row that
         * answers this finding is the row composed for that class — asked for by the position's own
         * name and the class's own id, exactly as {@link #atClass} asks. The rows the search
         * produced used to be matched back by the words in their names, which is a spelling two
         * parameters of one type share.
         *
         * <p>Where the position has no axis, nothing takes the finding. Read off the axes rather
         * than off an empty row list, which would be the same as calling a search that found
         * nothing a fact about the model.
         */
        private static GenerationOutcome atCase(ObligationIdentity owed,
                                                souther.compiler.partition.FillResult composed) {
            // A case of an input nothing divides into classes. What this run composes rows from is
            // the classes of a position, so there is no position here for it to have looked at —
            // the same answer it gives for a class at a position this subject has no axis for.
            if (owed instanceof ObligationIdentity.OfAnInputCase) {
                return new GenerationOutcome.NotSupported(
                        GenerationOutcome.NotSupported.Reason.NO_AXIS_AT_THIS_POSITION);
            }
            return atCase(((ObligationIdentity.OfAClass) owed).classOfAPosition(), composed);
        }

        private static GenerationOutcome atCase(ClassOfAPosition owed,
                                                souther.compiler.partition.FillResult composed) {
            // Asked of the subject the search was made over, which is what says what this run had
            // classes for. Which class of which position the case is, is the finding's own answer
            // and is not worked out here: a second reading of it could name a class the account
            // does not keep, and then a row would be offered for one thing and weighed against
            // another.
            if (!composed.plan().subject().divides(owed)) {
                return new GenerationOutcome.NotSupported(
                        GenerationOutcome.NotSupported.Reason.NO_AXIS_AT_THIS_POSITION);
            }
            return atClass(owed.at(), owed.classId(), composed);
        }

        /**
         * What the edge strategy composed, read off what the boundary assessment already tried.
         *
         * <p>Its own answer and not the dispositions': a line is measured against the rows, and a
         * behavior no row names has no gap at any of its lines while every one of them is still a
         * row worth offering. Keying what is offered on what a build refuses would leave a model
         * with no rows at all — the one an author most wants rows for — with nothing.
         *
         * <p><b>The points this behavior is owed a row at, and no others.</b> The two points against
         * a line an {@code invariant} drew are the declaration's — one row settles the line however
         * many positions carry the type — and they are offered once, where the line is resolved
         * ({@link BorderAccount}). Offered from here as well, one authored line comes out as a row
         * per position of every behavior that carries it. The regions either side stay, because
         * where a region stops is settled by every other rule reaching this position.
         */
        private static Generator.GenerationResult offeredHere(String behavior,
                                                          List<BorderAssessment> boundaries) {
            List<Generator.GeneratedRow> rows = new ArrayList<>();
            List<Generator.UnresolvedCombination> unresolved = new ArrayList<>();
            List<souther.compiler.partition.GenerationReason> stopped = new ArrayList<>();
            // What this behavior is owed a row for, as the values a row is composed at: one per
            // point, since a row at a point answers everything a row there is owed for.
            for (OwedBoundaryPoint point
                    : OwedBoundaryPoint.oneForEachPoint(OwedBoundaryPoint.across(boundaries)).at()) {
                ItemAssessment.Owed each = point.item();
                // Every search of the point, because a block short of rows is short of what each of
                // them did not offer. One of them stands for none of the others: a reading searched
                // twice can have composed a row under one caller's conditions and been stopped
                // under the other's, and both are things this run has to account for.
                for (ItemAssessment.Attempt made : each.searches().each()) {
                switch (made) {
                    case ItemAssessment.Attempt.Built built -> rows.add(built.row());
                    // A search a budget of this compiler's ended came to nothing like any other,
                    // and what a block short of rows records is that nothing came of it. Which
                    // figure ended it is the point's own to say and is said where a reader asks
                    // about the point, not in a list of what this run did not offer.
                    case ItemAssessment.Attempt.Stopped why -> unresolved.add(why.why());
                    // And a search that ran to the end of what this compiler writes. It came to
                    // nothing the way the one above did, and what it writes some of is the point's
                    // own to say, in the same place — a list of what this run did not offer is no
                    // more the place for that than it is for a figure.
                    case ItemAssessment.Attempt.Unexhausted why -> unresolved.add(why.why());
                    // And a search whose answer was about less than the point had. It came to
                    // nothing in the same sense the two above did, and which figure made its answer
                    // short is the point's own to say, in the same place.
                    case ItemAssessment.Attempt.Limited why -> unresolved.add(why.why());
                    // And a point no search was made for, which the block is short of a row for
                    // like any other. Its word says that rather than saying what a search found.
                    case ItemAssessment.Attempt.Unplanned why -> unresolved.add(why.why());
                    case ItemAssessment.Attempt.Unresolved why -> {
                        unresolved.add(why.why());
                        // And where the decoders were out of reach, the block is short of rows it
                        // would otherwise have offered, which is a thing about this run rather than
                        // about the point. Read off the reason because that is where the search
                        // records it: a boundary search keeps no reasons list of its own, which is
                        // what the pairs half above takes its copy of this from.
                        if (why.why().reason()
                                == Generator.UnresolvedCombination.Reason.LINKAGE_FAILED) {
                            stopped.add(new souther.compiler.partition.GenerationReason
                                    .LinkageFailed(behavior));
                        }
                    }
                    // Nothing to build against. Does not arrive: the evaluation is asked only of a
                    // module that checked, so a module with no classes has no rows either, and a
                    // boundary with no rows behind it is undecided rather than missed. Reaching it
                    // takes the backend failing on a module that checked, which is a defect in the
                    // backend rather than a state of the source.
                    case ItemAssessment.Attempt.Unavailable _ ->
                            stopped.add(new souther.compiler.partition.GenerationReason
                                    .NothingToBuildAgainst(behavior));
                    // Nothing was searched for here, which is what the measurement says of a point a
                    // row already sits at and of one nothing measured. Neither is news: saying so
                    // per point would put the compiler's own bookkeeping in a list of an author's
                    // work.
                    //
                }
                }
                // A point the measurement does say is worth searching is a different thing, and it
                // is the same thing `atEdge` refuses: this walks what a search answered, so a hole
                // in it is the search having skipped a point it was asked about.
                if (!each.searches().ran() && each.worthSearching()) {
                    throw new IllegalStateException("nothing was searched for at "
                            + point + ", which is worth searching");
                }
            }
            return new Generator.GenerationResult(rows, unresolved,
                    stopped.stream().distinct().toList());
        }

        /**
         * The values a row's positions can be composed against, in the order the search should try
         * them.
         *
         * <p>Two kinds, and the difference between them is what the model says. A row the author
         * wrote naming a value at each position is a set of values they reached for together, and
         * is an origin whole. A {@code let} of a parameter's own type is a value of that position
         * and says nothing about any other, so it is an origin for that position alone — every one
         * of them, since a second value is another value a reader recognises rather than a reason
         * to fall back on the classes, and never two of them put together here.
         *
         * <p>The names and nothing else. What each value is, is read where the row is read, by the
         * same reading a written row naming it goes through — so nothing here holds a copy of it to
         * disagree with.
         */
        private static List<Generator.Baseline> baselines(
                String module, Hir.SpecBehavior spec, Sig sig, Map<String, Hir.FnDef> values,
                Map<ValueName.Behavior, Sig> behaviors,
                CheckSurface prepared, Symbols symbols,
                souther.compiler.observe.FieldTypes fields) {
            List<Generator.Baseline> out = new ArrayList<>();
            // What the author has already written, first and whole. A row of theirs names a set of
            // values that go together, which is more than this can say of one value chosen per
            // position on its own — and it is the set they reached for, which is what makes a row
            // written against it read as one column moved.
            for (Hir.Example block : prepared.examples()) {
                if (!block.target().equals(spec.name())) {
                    continue;
                }
                for (Hir.ExampleRow row : block.rows()) {
                    Generator.Baseline named = namesIn(spec, row.inputs());
                    if (!named.isEmpty() && !out.contains(named)) {
                        out.add(named);
                    }
                }
            }
            // Then every value the module states of a parameter's own type, in the order it states
            // them, one origin per turn. Narrowed to the only value of a type, a module that states
            // a second one lost the spread from every row of every behavior taking it.
            out.addAll(named(module, spec, sig, values, behaviors, symbols, fields));
            return List.copyOf(out);
        }

        /** The parameters a row names a module-level value at, which is the only thing a spread can
         *  be written over: a row writing the value out has no name for this to reach it by. */
        private static Generator.Baseline namesIn(Hir.SpecBehavior spec, List<Hir.Expr> inputs) {
            Map<String, Generator.Baseline.Named> at = new LinkedHashMap<>();
            for (int p = 0; p < inputs.size() && p < spec.params().size(); p++) {
                if (inputs.get(p) instanceof Hir.Var.Denoting denoting
                        && denoting.denotes() instanceof souther.compiler.types.ValueName.Helper helper) {
                    at.put(spec.params().get(p).name(),
                            new Generator.Baseline.Named(helper.module(), denoting.name()));
                }
            }
            return new Generator.Baseline(at);
        }

        /**
         * The values the module states of each parameter's type, one origin apiece.
         *
         * <p><b>One parameter each, and never a tuple made here.</b> An origin says what a row is
         * written against, and a row written against a value the model states reads as that value
         * with one class moved. What the module states is a value of a type; that two of them go
         * together is a further thing, and nothing states it.
         *
         * <p>So each origin names the one parameter it has a stated value for and says nothing
         * about the rest, which the composition fills from their classes. That is what the partial
         * map on {@link Generator.Baseline} is: a position it does not name is one it makes no
         * claim about. A tuple assembled here would be a claim — {@code (a1, b1)} written as though
         * the author had put those two values together — and the only thing available to assemble
         * it by is the order the file happens to declare them in. Taken as the n-th of each list,
         * swapping two unrelated declarations moved the origin every generated row of the behavior
         * was written against, and two parameters of one type got {@code (x, x)} and {@code (y, y)}
         * and never {@code (x, y)}, off a diagonal nothing in the model draws.
         *
         * <p>A whole tuple is still an origin where the author wrote one: a row of theirs naming a
         * value at each position is a set of values they reached for together, and that is read
         * from the rows rather than assembled ({@link #namesIn}).
         */
        static List<Generator.Baseline> named(String module, Hir.SpecBehavior spec, Sig sig,
                                                      Map<String, Hir.FnDef> values,
                                                      Map<ValueName.Behavior, Sig> behaviors,
                                                      Symbols symbols,
                                                      souther.compiler.observe.FieldTypes fields) {
            // What a value is declared to be, asked of the one walk that answers it. A second
            // reading of a definition's type here would be a second answer about what a row may
            // name, differing from the reading that builds the row at whatever either forgot.
            // Refusing where a declaration does not read, because this is downstream of a check:
            // one that does not read was refused there, so meeting one here is this compiler being
            // wrong rather than a module being written.
            souther.compiler.check.DeclaredTypeReading evidence =
                    new souther.compiler.check.DeclaredTypeReading(
                            new souther.compiler.check.DeclarationFacts(
                                    new souther.compiler.check.FieldRead(symbols, fields,
                                            souther.compiler.check.FieldRead.Unreadable.REFUSED)),
                            values, behaviors);
            Map<TypeSymbol, List<String>> stated = new LinkedHashMap<>();
            for (Map.Entry<String, Hir.FnDef> each : values.entrySet()) {
                if (!each.getValue().params().isEmpty()
                        || !(each.getValue().body() instanceof Hir.FnBody.Written written)
                        || !(evidence.declaredTypeOf(written.expr())
                                instanceof souther.compiler.types.Type.Ref(TypeSymbol of))) {
                    continue;
                }
                stated.computeIfAbsent(of, _ -> new ArrayList<>()).add(each.getKey());
            }
            List<Hir.Param> takes = spec.params();
            List<Generator.Baseline> out = new ArrayList<>();
            for (int p = 0; p < takes.size() && p < sig.inputTypes().size(); p++) {
                if (!(sig.inputTypes().get(p) instanceof souther.compiler.types.Type.Ref(
                        TypeSymbol of))) {
                    continue;
                }
                for (String value : stated.getOrDefault(of, List.of())) {
                    Generator.Baseline origin = new Generator.Baseline(Map.of(
                            takes.get(p).name(), new Generator.Baseline.Named(module, value)));
                    if (!out.contains(origin)) {
                        out.add(origin);
                    }
                }
            }
            return out;
        }

        private static souther.compiler.partition.FillResult rowsFor(
                Hir.SpecBehavior spec, Sig sig, RuleReadingSource reading,
                souther.compiler.partition.GenerationPlan asked,
                List<Generator.Baseline> baselines,
                souther.compiler.core.Core body,
                souther.compiler.coverage.CoverageSites.Plan plan,
                Optional<SiteNumbering> numbering, RowReading observed,
                BoundaryValues building, InputDomain domain,
                Generator.Trial trial, AnswersStoodIn stood, boolean recording,
                souther.compiler.partition.AdequacyPolicy.OfTheGeneration budget) {
            if (observed.someRowsUnseen()) {
                // Rows exist that nothing read. What they cover is unknown, so what is left uncovered
                // is unknown too — and a generated row is a specific piece of work handed to a person,
                // which may already be sitting in the file that could not be evaluated.
                return souther.compiler.partition.FillResult.nothingWasLookedFor(asked,
                        Generator.UnresolvedCombination.Reason.THE_ROWS_WERE_NOT_READ,
                        List.of(new souther.compiler.partition.GenerationReason.RowsNotRead(
                                spec.name(), observed.gaps())));
            }
            List<RowOutcome> rows = observed.rowsSeen();
            // The measurement's own axes, so that a row is placed by the walk it was measured at.
            souther.compiler.partition.MeasuredInput.MeasuredAxes axes = asked.subject().axes();
            Generator.CandidateCheck check = building == null ? Generator.CandidateCheck.ANY
                    : (at, candidate) -> built(building.build(sig.ins().get(at), candidate.value()));

            // Where each row's values sit, and what its run did. Both come off the one outcome:
            // the first is what a pair count is taken over, the second is what says which of the
            // body's combinations the row was seen filling.
            List<Generator.ObservedRow> existing = rows.stream()
                    .map(row -> new Generator.ObservedRow(
                            InputClassifications.of(row.inputs(), axes),
                            watched(row, recording, numbering)))
                    .toList();
            return Generator.fill(asked, existing, check,
                    souther.compiler.reading.CoverageRead
                            .of(spec.name(), body, plan, domain, reading),
                    trial, baselines, stood, budget);
        }

        /**
         * What this build is asking the generator for, settled before anything that can stop it.
         *
         * <p>The classes off the partition measure and the arms off the findings, which is where
         * each of them was established. Made here rather than inside the search so that the ways a
         * generation ends without searching — the rows could not be read, the classes would not
         * link — hold the same list as the search does. Made there, each of them answered with a
         * reason about the run and no word about the thing a reader was asking after.
         */
        private static souther.compiler.partition.GenerationPlan planFor(
                souther.compiler.partition.MeasuredInput subject, List<Finding> owed,
                PartitionEvidence evidence, CoverageSites.Plan plan) {
            // The arms this build is owed a row at, which the measure established and this reads.
            // A combination the body settles together is where one is looked for and is not itself
            // owed a row — nothing reports one — so what is searched follows from the findings
            // rather than from the shape of the search space.
            // In the order the findings were established, which is the order this build raised
            // them in. Handed over as a list rather than as the set that kept them once apiece:
            // what the plan is asking for is the order, and this is where what the order means is
            // known.
            //
            // Every place a run through the arm is recorded at, and not the one the finding named.
            // A helper carrying a fork is spliced into each call site, so what steers a row into
            // one splice is not what steers it into another — and asked at a single occurrence the
            // answer was whichever the walk wrote first: one body with its two call sites swapped
            // offered a row for the arm in one order and said nothing could steer one in the other.
            LinkedHashMap<CoverageSites.Obligation, List<ArmProbe>> arms = new LinkedHashMap<>();
            for (Finding finding : owed) {
                if (finding.about()
                        instanceof About.AnArmNoRowGoesThrough(CoverageSites.ArmSite arm)) {
                    arms.computeIfAbsent(arm.obligation(), of -> everyPlaceOf(plan, of));
                }
            }
            return new souther.compiler.partition.GenerationPlan(subject, classesOwed(evidence),
                    arms.values().stream().map(Generator.ArmOwed::new).toList());
        }

        /**
         * Every place a run through one arm of the model is recorded at.
         *
         * <p>Asked of the plan that numbered them, which is where a construct of the model and the
         * places it stands in the running tree are already related. The arm the finding named is
         * one of these, so the list is never empty for an arm anything was measured about.
         */
        private static List<ArmProbe> everyPlaceOf(CoverageSites.Plan plan,
                                                   CoverageSites.Obligation arm) {
            List<ArmProbe> out = new ArrayList<>();
            for (CoverageSites.ArmSite site : plan.arms(arm.behavior())) {
                if (site.obligation().equals(arm)) {
                    out.add(site.index());
                }
            }
            return out;
        }

        /**
         * The classes this build is owed a row at, off the partition measure's own reading.
         *
         * <p>The same evidence a class finding is made from, taken a second way. Where the measure
         * reached a class, nothing is owed there; where it did not, a row is. What separates the two
         * projections is that a finding is a gap and needs the rows to have been measured to be one
         * — a behavior nothing wrote a row for has no gaps, which is not the same as having nothing
         * to write. So a position with no reading behind it is owed a row at every class, which is
         * what an empty {@code covered} says, and the report's own line about it is said elsewhere.
         *
         * <p>A behavior the coverage query holds nothing for arrives as {@link
         * PartitionEvidence#NONE} and is owed nothing. Reading the written rows a second time here
         * would be a plan derived from something other than the evidence, which is the arrangement
         * this replaces — and it would be one no test covers, the query answering every behavior
         * the generator reaches.
         */
        private static List<ClassOfAPosition> classesOwed(PartitionEvidence evidence) {
            // Gathered once apiece and handed over in the order the measure holds the positions
            // and their classes in, which is the order this walk reached them. The set keeps the
            // once-apiece; the list is what says what the order is.
            Set<ClassOfAPosition> out = new LinkedHashSet<>();
            for (PartitionEvidence.AxisCoverage axis : evidence.axes()) {
                Set<String> covered = axis.reached().made()
                        .map(PartitionEvidence.AxisCoverage.Reached::covered)
                        .orElseGet(Set::of);
                for (String cls : axis.classes()) {
                    if (!covered.contains(cls)) {
                        out.add(new ClassOfAPosition(axis.at(), cls));
                    }
                }
            }
            return List.copyOf(out);
        }
    }

    /**
     * What a construction came to, in the generator's own words.
     *
     * <p>Two vocabularies for one answer, which is what a boundary between two packages costs. The
     * mapping is here and nowhere else, so a shape added on either side has one place to be
     * answered rather than as many as there are callers.
     */
    private static Generator.CandidateCheck.Built built(BoundaryValues.Built what) {
        return switch (what) {
            case BoundaryValues.Built.Value(var observed) ->
                    new Generator.CandidateCheck.Built.Value(observed);
            case BoundaryValues.Built.Refused(var why) ->
                    new Generator.CandidateCheck.Built.Refused(why);
        };
    }

    /**
     * A way to build values against this module's own classes, or nothing where there are none to
     * build against.
     *
     * <p>The classes an evaluation runs against, not a second generation of them. Whether a value
     * builds is the decoder's answer and the counting an evaluation carries does not change it —
     * nothing here runs a row, so no budget is installed and the counted entry points count against
     * nothing. Asking for the uncounted classes instead would generate every one of them again to get
     * the same answers.
     */
    static BoundaryValues constructing(Db db, String module) {
        ExampleExecution asked = ExampleExecutions.of(db, module);
        return asked == null ? null : db.execution().values(asked);
    }

    /**
     * A way to run rows against this module's own classes, or nothing where none can be run.
     *
     * <p>Nothing where the compile is not measuring, which is the one condition worth stating
     * outright. Classes emitted without the calls that record where a run went give a run nothing
     * was recorded of, and that reads exactly like a run that went nowhere — so a search told to
     * confirm its candidates against them would find every one of them missing and offer nothing at
     * all. Where they are absent the search says its rows went unconfirmed, which is what happened.
     *
     * <p>A budget is installed here, unlike where values are only built. A row this composed is a
     * row nobody wrote, so a model that does not finish on one is this search's to stop.
     */
    static RowTrials trialling(Db db, String module) {
        // Whether this compile is measuring at all is read here and not there: it is what the build
        // was asked to be held to, which is not a question about running anything.
        if (!levelOf(db).runsInstrumentedRows()) {
            return null;
        }
        ExampleExecution asked = ExampleExecutions.of(db, module);
        return asked == null ? null : db.execution().trials(asked, armsAsked(db));
    }

    /**
     * A way to run one behavior's composed rows, said in the generator's words.
     *
     * <p>Two seams and one adaptation, the way the check that a value can be built already is. What
     * runs a row is the evaluation's business and speaks in what a fixture states; what a generator
     * has is a template it composed. Neither has to know the other's word for a row.
     *
     * <p>Beside {@link #trialling} rather than inside the search, because running a composed row is
     * what anybody asking after one does — the search that composes them, and whoever asks what one
     * of them would settle.
     */
    static Generator.Trial runningRowsOf(RowTrials trials, String behavior, Sig sig,
                                         Optional<SiteNumbering> numbering,
                                         RequiredDependencies requires) {
        if (trials == null || numbering.isEmpty() || requires == null) {
            // Nothing runs where nothing applies the behavior, nothing is read where the module has
            // no numbering to read it under, and nothing is applied where what the behavior
            // requires could not be worked out — the three are the same module, so the rest follow
            // the first and are said rather than assumed.
            return Generator.Trial.NOTHING_RUNS;
        }
        RowTrials.OfBehavior application = trials.forBehavior(behavior, sig);
        return row -> {
            List<RowTrials.AnsweredWith> standing = requires.standingIn(row.answers());
            if (standing == null) {
                // A row short of a stand-in the behavior requires is a row nothing applies, which
                // is said here rather than by a construction failing: what comes back from that is
                // a row nothing was seen doing, and so is this — but only this one knows why.
                return new Generator.Watched.NoAccount();
            }
            return application
                .run(row.inputs().stream()
                        .map(souther.compiler.partition.FixtureTemplate::value).toList(), standing)
                // Read under the numbering the caller is asking about. What a run left behind says
                // which numbering it was made under, so a recording of classes numbered otherwise
                // is refused here rather than answered about places it was never near.
                .<Generator.Watched>map(seen -> new Generator.Watched.Ran(
                        numbering.orElseThrow().align(seen)))
                .orElseGet(Generator.Watched.NoAccount::new);
        };
    }

    /**
     * What one measure found and nothing filled.
     *
     * <p>What a kind is, is what a measure found. Whether it is about something the model owes a row
     * at is {@link #isAboutAnObligation}, and what a build then does about it is decided where a
     * build is — a kind about an obligation has to carry a diagnostic code, or a build would fail
     * over something it never printed; the agreement is held by a test rather than by reading one
     * off the other.
     */
    public enum Kind {
        /** A case of the output no row expects. */
        OUTPUT_CASE_UNSPECIFIED(DiagnosticCode.E1913),
        /** A case of an input no row applies the behavior to. */
        INPUT_CASE_UNSPECIFIED(DiagnosticCode.E1915),
        /** A line some rule draws that no row sits on. */
        BOUNDARY_UNMET(DiagnosticCode.E1916),
        /** An arm of the body no row goes through. */
        ARM_UNREACHED(DiagnosticCode.E1918),
        /**
         * A row whose answer is owed, and an arm whose only rows are those.
         *
         * <p>One kind for the row and for the arm, because it is one thing to do about them: write
         * down what the system answers. Apart from {@link #ARM_UNREACHED} because that is what an
         * arm no row reaches is, and an arm a row goes through is not one — published under it,
         * a consumer reading the document would be told nothing reached an arm a row did reach.
         */
        UNANSWERED_ROW(DiagnosticCode.E1934),
        /** A case some row expects and nothing was seen to produce. Said only of a behavior some row
         *  saw answer with a case: where nothing was observed at all, this is true of every case and
         *  is what the rows say of themselves. */
        OUTPUT_CASE_UNVERIFIED(null),
        /** A class of an axis no row is in. */
        AXIS_CLASS_UNCOVERED(DiagnosticCode.E1931),
        /**
         * A rule of the decision the body states that no row takes.
         *
         * <p>Said of the rules something was seen standing in. A rule the model's own rules leave
         * no value for is not owed one, and a rule this compiler looked for and did not find is
         * neither covered nor a gap — which is {@link RuleRequirement}'s three answers, and none of
         * them is a finding except the first. What the rows did is the other question and is
         * answered by the coverage this is counted against.
         *
         * <p>Beside {@link #ARM_UNREACHED} and not among it. An arm is what the author wrote and is
         * owed a row once however often a helper carrying it is called; a rule is a way through the
         * body, and two rules can go through one arm. A body whose arms answer alike states two
         * rules and one row through each arm covers them, so neither measure is the other read
         * another way.
         */
        DECISION_RULE_UNCOVERED(DiagnosticCode.E1935),
        /**
         * A point away from a border that no row is at — the {@code IN} or the {@code OUT} point.
         *
         * <p>Beside {@link #BOUNDARY_UNMET} rather than among its findings, and the difference is
         * which criterion a build is held to. A row on the line and a row one step over are what
         * simplified domain coverage asks for; a row well inside and a row well outside are the two
         * further items reliable domain coverage adds. A build held to the second refuses over this
         * one and a build held to the first does not, which is {@link Criterion}'s to say — the
         * report still names neither criterion as satisfied.
         *
         * <p>Not a measure of its own. It comes off the same assessment of the same border as the
         * points against the line, so what a build refuses over is a reading of one measurement and
         * never a second one made to different rules.
         */
        DOMAIN_POINT_UNCOVERED(DiagnosticCode.E1917),
        /**
         * A position the model draws no line through.
         *
         * <p>A fact about the model, and only said where the derivation ran to the end and found
         * nothing. A position this could not read is {@link #PARTITION_NOT_READ}: the two were one
         * finding, and the sentence this one prints was told to authors whose own body compared the
         * position two lines above.
         */
        PARTITION_NOT_DERIVABLE(null),
        /** A position something is written about that this did not read, with what stopped it. */
        PARTITION_NOT_READ(null),
        /**
         * A rule written about a position that nothing took in, and which of its questions stands.
         *
         * <p>One kind and not one per measure. What happened is that a rule raised a question and
         * nothing answered it; which section of a document a reader meets it in follows from the
         * question, and is decided where the document is written. Named for the partition, this said
         * that the fact belonged to that measure — and a rule about where the values stop would have
         * been printed under the classes, two headings away from the border it is about, which is
         * the shape of issue #842.
         *
         * <p>Not read off the borders either. A line this could not fold has no border to iterate,
         * and that is exactly when its question stands.
         *
         * <p>Told apart from {@link #PARTITION_NOT_READ} because they are different things to act
         * on. Nothing was established about a position this could not read; here the model said
         * something and no reading of it answered.
         *
         * <p>Named by the rule. A position was all a reader used to be given, which sent them
         * looking for a rule the sentence never named — and the sentence was written off one
         * reading's account of itself, so it was said of models every rule of which had been read
         * (issue #842).
         */
        RULE_UNACCOUNTED(null),
        /**
         * A position the axes measure whose rules the walk never reached.
         *
         * <p>Its own finding beside {@link #RULE_UNACCOUNTED}. There is no rule to name,
         * and a reader told that every rule was accounted for is told the opposite of the one thing
         * worth knowing about the position.
         */
        PARTITION_RULES_NOT_REACHED(null),
        /**
         * A position whose values are read from a product this reading cannot show the rules admit.
         *
         * <p>Its own finding beside the two above, and the one of the three that is not a limit an
         * author can go looking for a clause behind: every rule arrived and every rule was read.
         * What it qualifies is the classes rather than their absence, so it is said at positions the
         * axes measured as readily as at positions they did not.
         */
        PARTITION_VALUES_NOT_SEPARATED(null);

        private final DiagnosticCode code;

        Kind(DiagnosticCode code) {
            this.code = code;
        }

        /** The code a build is told this under, where it is told at all. */
        public Optional<DiagnosticCode> code() {
            return Optional.ofNullable(code);
        }

        /**
         * Whether a finding of this kind is about something the model owes a row at.
         *
         * <p>The one division everything else is a projection of. A report marks these, a block
         * offers rows against them and a build refuses over the ones a measure established — three
         * surfaces reading one answer, none of them deciding it.
         *
         * <p>Said as what the kind is and not as what a build does with it, which is the shape the
         * bars left behind. What a build refuses over was a word the caller wrote, so the table
         * that survived them was named for the refusal; read that way, refusing is the primitive
         * and being owed is derived from it, which is backwards. Whether the model owes a row is
         * the model's answer, and a build refusing is one of the things that follow.
         *
         * <p>An exhaustive switch, so a kind added later does not compile until somebody has said
         * which of the three it is.
         */
        public boolean isAboutAnObligation() {
            return switch (this) {
                // Every obligation the model derives, whichever derivation states it: a case of a
                // signature, a point of a border, an arm of a body, a class of a position, a rule
                // of the decision, and a row whose answer is owed. One account, so one answer.
                case OUTPUT_CASE_UNSPECIFIED, INPUT_CASE_UNSPECIFIED, BOUNDARY_UNMET, ARM_UNREACHED,
                     UNANSWERED_ROW, DOMAIN_POINT_UNCOVERED, AXIS_CLASS_UNCOVERED,
                     DECISION_RULE_UNCOVERED -> true;
                // An observation: what was seen rather than what is owed. A case nothing was
                // observed producing is the rows' own account of themselves.
                case OUTPUT_CASE_UNVERIFIED -> false;
                // And this compiler's own shortfall: what a measure could not establish, and what
                // it established about the model rather than about the rows. Neither is a row
                // somebody owes, and nothing can make one of them into one.
                case PARTITION_NOT_DERIVABLE, PARTITION_NOT_READ, RULE_UNACCOUNTED,
                     PARTITION_RULES_NOT_REACHED, PARTITION_VALUES_NOT_SEPARATED -> false;
            };
        }

        /**
         * Which question of the account answers about findings of this kind.
         *
         * <p>Here so that a surface reading part of the account names the kinds it has a reader for
         * and is handed the questions those come out of. Written at each surface instead, which
         * query answers about which kind was a condition somebody had to keep true by hand, and a
         * surface that got it wrong would gate on an account missing exactly the kinds it was
         * gating on.
         *
         * <p>A {@code switch} with nothing to fall through to: a kind added below is a kind some
         * question has to be named for.
         */
        public AccountPart answeredBy() {
            return switch (this) {
                case DECISION_RULE_UNCOVERED -> AccountPart.THE_DECISION;
                case OUTPUT_CASE_UNSPECIFIED, INPUT_CASE_UNSPECIFIED, BOUNDARY_UNMET,
                     ARM_UNREACHED, UNANSWERED_ROW, OUTPUT_CASE_UNVERIFIED, AXIS_CLASS_UNCOVERED,
                     DOMAIN_POINT_UNCOVERED, PARTITION_NOT_DERIVABLE, PARTITION_NOT_READ,
                     RULE_UNACCOUNTED, PARTITION_RULES_NOT_REACHED,
                     PARTITION_VALUES_NOT_SEPARATED -> AccountPart.THE_MEASURES;
            };
        }
    }

    /**
     * Which question of this compiler's the account is answered by.
     *
     * <p>Two questions and one account. What a surface acts on is the account; what it costs to
     * answer is a question about this compiler's work, and the two are told apart here so that
     * neither is spelled as the other. A surface with a reader for the whole of it asks for the
     * whole of it; one that will say nothing about a part names the kinds it reads and pays for
     * what those come out of.
     */
    public enum AccountPart {

        /** {@link Findings}: everything the measures over a behavior's rows and its model found. */
        THE_MEASURES,

        /**
         * {@link DecisionFindings}: the rules of the decision each body states.
         *
         * <p>Apart from the rest because of what settling one costs. The rules of a body are its
         * ways, which multiply, and each one nothing was seen taking has a value composed and run
         * against it — so a build that will say nothing about them is not asked to pay for them.
         */
        THE_DECISION
    }

    /**
     * Where a report about {@code finding} belongs.
     *
     * <p>The one place this is decided. Two surfaces write sentences about a finding — the warnings
     * a build reads and the document a person reads — and a rule kept in both would let the same
     * finding be shown in two places, which is what a reader has no way to tell from two findings.
     *
     * <p>Asked and not carried. What a finding says is settled by the reading that made it; where it
     * is shown is settled by where the code it is about is now, which is somebody else's answer. A
     * finding that carried it would be a new finding every time a helper it names slid down a file.
     *
     * <p>Which somebody differs by kind, and the switch is where that is said. A fork written in a
     * source this compilation holds is placed by the module that wrote it; one the language writes
     * is placed by the plan that reached it; a line the declarations owe is placed by the
     * declaration it is shown at, which is not always in the module keeping the account; and a
     * finding about a behavior as a whole is placed by that behavior. A row is the one shown where
     * the reading already had it, for the reason
     * {@code WhatStillHoldsAPlaceUnderAFindingIsReadOnTwoAxesTest} records.
     *
     * <p>A switch with nothing to fall through to. Which place a kind of finding is shown at is a
     * decision about that kind, so a kind added to {@link About} arrives here as a compile error
     * rather than being shown wherever the last {@code default} happened to point.
     *
     * @param module whose reading made it, which is what says where a behavior of it is declared
     * @throws souther.compiler.query.Sites.NothingPlacesIt where nothing this compilation holds
     *         places it
     */
    public static Citation placeOf(Db db, String module, Finding finding) {
        return switch (finding.about()) {
            // An arm is shown where the fork it is one of is, which the fork's own module answers —
            // or, for a fork nobody here wrote, where this compilation came in through.
            case About.AnArmNoRowGoesThrough(CoverageSites.ArmSite arm) ->
                    Sites.placeOf(db, arm.anchor());
            case About.ARowAtAnArmAwaitsItsAnswer(CoverageSites.ArmSite arm) ->
                    Sites.placeOf(db, arm.anchor());
            // A line the declarations owe is shown at one of them, and which one the debt says.
            // Where that one is written is the module that wrote it answering, which is not always
            // the module keeping the account.
            case About.APointOfADeclaredBorder(DeclaredDebt owed) ->
                    whereItIsWritten(db, owed.pointAt());
            // A row is shown where it is written, which is in this module's own source.
            case About.AnUnansweredRow(String _, RowIdentity _, SourcePos at) -> Citation.of(at);
            // Everything else is about the behavior as a whole — what its rows do not reach, what
            // its rules do not divide, what nothing here could read of them. Shown at the behavior.
            case About.ACaseNoRowExpects _, About.ACaseNothingWasSeenToProduce _,
                    About.ACaseNoRowAppliesItTo _, About.AClassNoRowIsIn _,
                    About.ARuleNoRowTakes _,
                    About.APointOfABorder _, About.APositionNoLineDivides _,
                    About.ARuleWithoutALine _, About.ARuleNothingClassified _,
                    About.APositionThisCouldNotRead _, About.APositionReadWiderThanItsRules _,
                    About.APositionWhoseRulesWereNotReached _, About.AQuestionNothingAnswered _ ->
                    whereItIsDeclared(db, module, finding.subject());
        };
    }

    /** Where the declaration a finding is shown at is written. */
    private static Citation whereItIsWritten(Db db, TypeSymbol.AtModule declared) {
        return somewhere(db.ask(new Sites.WhereADeclarationIsWritten(declared)),
                "the declaration " + declared.name());
    }

    /** Where the behavior a finding is about is declared. */
    private static Citation whereItIsDeclared(Db db, String module, FindingSubject subject) {
        if (!(subject instanceof FindingSubject.OfABehavior behavior)) {
            throw new IllegalStateException(
                    "a finding shown at a behavior is a finding about one: " + subject);
        }
        return somewhere(db.ask(new Sites.WhereABehaviorIsDeclared(module, behavior.name())),
                "the behavior `" + behavior.name() + "` of " + module);
    }

    /** {@code asked}'s answer, where a report is about to be written at it. */
    private static Citation somewhere(Answer<Citation> asked, String subject) {
        if (!asked.present()) {
            throw new Sites.NothingPlacesIt(subject);
        }
        return asked.value();
    }

    /**
     * One thing a measure established, on the behavior it is about.
     *
     * <p>{@code about} is what the measure established, as itself. Every reader projects it into its
     * own words; nothing here does that for them. It used to be the arguments of a message in the
     * order its key took them, which made the shape of every kind's payload follow from what four
     * of them needed for one of three readers, and left the rest carrying whatever a report happened
     * to print.
     *
     * <p>{@code kind} is derived from {@code about} rather than held beside it, so a kind and what
     * it is about cannot come apart. It is not a second thing to get right when a kind is added.
     *
     * <p>{@code status} is the measurement this came out of. A finding from a measurement that could
     * not be completed is worth printing — it says a row may be missing — and is not worth failing a
     * build over, because telling an author to write a row they may already have written is worse than
     * saying nothing.
     *
     * <p><b>The caret a report puts under one of these is not here.</b> Where a report about it
     * belongs is {@link Adequacy#placeOf}'s answer, asked by whoever is about to write a sentence.
     * Held here, an edit that moved a helper and changed nothing it does would be an edit to every
     * finding of every module that calls it, and no test of what this compiler answers could see
     * the difference — the findings would all be new and all say what they said.
     *
     * <p>Which is not the same as holding no place at all. Values reached through {@link About}
     * still carry places of their own, and what those are for is being read one family at a time;
     * {@code WhatStillHoldsAPlaceUnderAFindingIsReadOnTwoAxesTest} is where they are counted,
     * and the module-boundary cut of issue #1472 waits on that count.
     */
    public record Finding(FindingSubject subject, WeakeningSet weakenedBy, About about) {

        /**
         * What a report calls what this is about.
         *
         * <p>Here rather than at each reader, so that a message wanting a word for the subject does
         * not reach past it for the one kind of subject it happens to know about.
         */
        public String named() {
            return subject.named();
        }

        /**
         * A finding one measurement established, carrying what <em>that</em> measurement went
         * without.
         *
         * <p>The measurement rather than the set, because the set is what a caller gets wrong. Every
         * finding used to be handed a {@code WeakeningSet} worked out somewhere above it, and the
         * one place that produced several from one method handed them all the same one — the
         * signature's, which is the union of its output's and every input's. A case the output was
         * counted for in full then read as undecided because an input had a row nobody could
         * classify, and a build stopped refusing a gap it had established (spec §e1913).
         *
         * <p>Asked for the measurement, a caller hands over the one it is looking at rather than a
         * set worked out somewhere above. That is not a type saying which measurement goes with
         * which subject — the two are still separate arguments and a caller can still pair them
         * wrongly. What it removes is the argument that invited a set from anywhere at all, and what
         * holds the rest is a regression run through this producer with two leaves that went without
         * different things.
         */
        public static Finding by(String behavior, Measure<?> found, About about) {
            return by(new FindingSubject.OfABehavior(behavior), found, about);
        }

        /** The same, about whatever the measure was of. */
        public static Finding by(FindingSubject subject, Measure<?> found, About about) {
            return new Finding(subject, found.weakening(), about);
        }

        /**
         * The same, where what found it is an obligation rather than a measure.
         *
         * <p>An obligation's coverage is a fold of the readings and not a measurement of anything
         * ({@link ObligationCoverage}), so it has what it went without and no status. It is taken
         * whole for the reason the measure above is: what a caller hands over is the thing it is
         * looking at, and there is no argument here to pass a set worked out somewhere else.
         */
        public static Finding by(FindingSubject subject, ObligationCoverage found, About about) {
            return new Finding(subject, found.weakening(), about);
        }

        /**
         * The same, where what found it is the reading of a body's decision.
         *
         * <p>A fourth and not one of the three, because what a decision reading went without is
         * neither a measure's status nor a fold of the readings of a line: a row it could not place
         * among the rules and a row nothing watched each leave a rule nothing was seen taking as
         * one a row may already take. Taken whole for the reason the others are — a rule of a body
         * rests on one reading of one set of runs, and a caller handing over a set assembled beside
         * it could give one rule's finding what another behavior's reading went without.
         */
        public static Finding by(FindingSubject subject, DecisionEvidence found, About about) {
            return new Finding(subject, found.weakening(), about);
        }

        /** The same, about a behavior. */
        public static Finding by(String behavior, ObligationCoverage found, About about) {
            return by(new FindingSubject.OfABehavior(behavior), found, about);
        }

        /**
         * Something the report says that no measurement established.
         *
         * <p>A rule this compiler could not read, a position nothing divides, a question nobody
         * answered, a row whose answer is owed: each is worth telling an author and none of them is
         * a measure coming to an answer. Nothing weakened them because nothing measured them, and a
         * build's answer to one is the account's alone.
         *
         * <p>What that does not say is whether a build refuses. Being read rather than measured and
         * being refused over are different questions, and folding them left a finding read straight
         * off the source with no way to be a gap: a row written {@code <?>} is as certain as a fact
         * gets and is exactly the work a build should stop for. Which kinds a build refuses over is
         * {@link #refuses} and is asked there.
         */
        public static Finding noticed(String behavior, About about) {
            return noticed(new FindingSubject.OfABehavior(behavior), about);
        }

        /** The same, about whatever it was noticed of. */
        public static Finding noticed(FindingSubject subject, About about) {
            return new Finding(subject, WeakeningSet.none(), about);
        }

        /**
         * What a build does about a finding, which is what neither surface used to say.
         *
         * <p>Three answers and not two, because the question is decided by two facts. Collapsing the
         * middle one into {@link #REPORTED} would say a measure decided something it did not: a kind
         * a build refuses over, from a measurement that came to no answer, is not a gap and is not a
         * kind nobody gates on either. A report already tells that one apart in words — "undecided
         * whether a row" against "no row" — and a document that had only two words would have put
         * them under one.
         */
        public enum Disposition {
            /** A gap a build refuses over. */
            REFUSED,
            /** A kind a build refuses over, from a measurement that came to no answer. */
            UNDECIDED,
            /** Not a kind a build refuses over, whatever its measurement managed. */
            REPORTED
        }

        public Finding {
            java.util.Objects.requireNonNull(about, "a finding is about something");
        }

        /**
         * Which kind of thing this is, read off what it is about.
         *
         * <p>The one place the two are related. A kind handed in beside the subject was a pair that
         * could disagree, and nothing checked it; here there is no pair. The two border kinds come
         * off one assessment of one point, and which of them a point is, is the role's answer —
         * written here rather than at the measure that found it and at every reader that sorts
         * findings, which is where the closed-border rule would otherwise be spelled three times.
         */
        public Kind kind() {
            return switch (about) {
                case About.ACaseNoRowExpects _ -> Kind.OUTPUT_CASE_UNSPECIFIED;
                case About.ACaseNothingWasSeenToProduce _ -> Kind.OUTPUT_CASE_UNVERIFIED;
                case About.ACaseNoRowAppliesItTo _ -> Kind.INPUT_CASE_UNSPECIFIED;
                case About.AClassNoRowIsIn _ -> Kind.AXIS_CLASS_UNCOVERED;
                // Asked of the role, whosever the line is: a body's line and a declaration's are the
                // same technique's item and are told apart under the same two codes.
                case About.ABorderObligation owed -> owed.role().againstTheLine()
                        ? Kind.BOUNDARY_UNMET : Kind.DOMAIN_POINT_UNCOVERED;
                case About.APositionNoLineDivides _ -> Kind.PARTITION_NOT_DERIVABLE;
                case About.ARuleWithoutALine _, About.ARuleNothingClassified _ ->
                        Kind.PARTITION_NOT_READ;
                // One word, whatever stopped the reading, and the reason beside it says which.
                // PARTITION_RULES_NOT_REACHED belongs to the finding above — a position the axes
                // did measure — and the two write nothing but the position, so sharing the word
                // would put two findings a reader can tell apart in the report under one a
                // consumer cannot.
                case About.APositionThisCouldNotRead _ -> Kind.PARTITION_NOT_READ;
                case About.APositionWhoseRulesWereNotReached _ ->
                        Kind.PARTITION_RULES_NOT_REACHED;
                case About.APositionReadWiderThanItsRules _ ->
                        Kind.PARTITION_VALUES_NOT_SEPARATED;
                case About.AQuestionNothingAnswered _ -> Kind.RULE_UNACCOUNTED;
                case About.AnArmNoRowGoesThrough _ -> Kind.ARM_UNREACHED;
                case About.ARuleNoRowTakes _ -> Kind.DECISION_RULE_UNCOVERED;
                // The row and the arm whose rows are all owed answers are one thing to do, and it
                // is not the thing an unreached arm is. A row goes through this arm, so publishing
                // it as an arm nothing reaches would tell a consumer the opposite of what happened.
                case About.AnUnansweredRow _, About.ARowAtAnArmAwaitsItsAnswer _ ->
                        Kind.UNANSWERED_ROW;
            };
        }

        /**
         * What a build does about this one: the kind and the measurement behind it, together.
         *
         * <p>The one statement of it. Both surfaces of a report write this word rather than reading
         * the kinds a second time, so what a report marks and what a build refuses over cannot come
         * apart.
         */
        public Finding.Disposition disposition() {
            if (!kind().isAboutAnObligation()) {
                return Finding.Disposition.REPORTED;
            }
            // What the measurement that found this went without, and not a word for how far it
            // got. A build refuses over a gap a measure established; where something the measure
            // reads could not be read, what it did not find is undecided rather than absent.
            return weakenedBy.isEmpty()
                    ? Finding.Disposition.REFUSED : Finding.Disposition.UNDECIDED;
        }

        /** Whether a build is entitled to refuse over this. */
        public boolean isAdequacyGap() {
            return disposition() == Finding.Disposition.REFUSED;
        }

        public Optional<DiagnosticCode> code() {
            return kind().code();
        }
    }

    /**
     * One line a declaration is owed, with what became of it and where the declaration is.
     *
     * <p>Held together because they are asked together and by more than one reader: a verdict rests
     * on what became of the line, a document publishes which declaration owes it, a report prints it
     * under that declaration, and a generation answers what it can do about it. Each of those
     * working it out from the readings is what the debt was introduced to stop.
     *
     * <p>One of these per line the module owes, and never one per owner of it. A line two of the
     * module's declarations took an end in together is one row to write, so it is one debt, one
     * finding, one item of the verdict and one thing to generate; who owes it is a list and that is
     * all the list is.
     *
     * @param debt   what the readings of the line came to
     * @param axis   what the line is on, in the words the declaration wrote it in. Here rather than
     *               on the debt because only a declaration has such a word: a reading names the
     *               position it met the line at and there are as many of those as there are
     *               positions, and a line no declaration drew is on nothing anybody named
     * @param owners the module's own declarations that owe it. Where each of them is written is
     *               asked of the module that wrote it, and is no part of this. Never
     *               empty: a line no declaration here owes is not this module's debt and is not one
     *               of these
     */
    public record DeclaredDebt(BorderObligationPointAssessment debt, String axis,
                               List<Owner> owners) {

        /**
         * One declaration that owes the line.
         *
         * <p>Which declaration, and not where it is written. A reader is sent to it by asking the
         * module that wrote it where it is, which is a question of its own and one whose answer
         * moves when the file does — while what the line is owed by does not.
         */
        public record Owner(TypeSymbol.AtModule declaration) {

            public Owner {
                if (declaration == null) {
                    throw new IllegalArgumentException("an owner is some declaration");
                }
            }
        }

        public DeclaredDebt {
            owners = List.copyOf(owners);
            if (debt == null || owners.isEmpty()) {
                throw new IllegalArgumentException("a debt is some declaration's, somewhere");
            }
            if (axis == null) {
                throw new IllegalArgumentException(
                        "a declaration's line is a line on something it wrote: " + debt.point());
            }
        }

        /** What this point asks of a row, as a report writes it. */
        public String said() {
            return debt.said(axis);
        }

        /** What a row here would have to do, as a report writes it. */
        public String against() {
            return debt.against(axis);
        }

        /**
         * Whether the declaration has a quantity to say the point on.
         *
         * <p>Asked rather than read back off {@link #said()}. A line between two positions writes
         * its level as a distance from the other one, which is a reading's name for it and not the
         * declaration's, so there is no quantity here and the point is the rule's line and the role
         * on it. A report comparing the sentence with the rule's name to work that out would be
         * deciding from the words what the account already answers.
         */
        public boolean namesItsQuantity() {
            return against() != null;
        }

        /** What a finding about it is about, which is every declaration that owes it. */
        public FindingSubject.OfADeclaration subject() {
            return new FindingSubject.OfADeclaration(
                    owners.stream().map(Owner::declaration).toList());
        }

        /**
         * One declaration to point at, for a reader that has room for one.
         *
         * <p>The first in the order the line names its owners, which is the declarations' own order
         * and not the order a walk found them in. A choice about where to put a mark and not about
         * whose the line is: what a finding is about is every one of {@link #owners}, and a reader
         * wanting the rest asks for them.
         *
         * <p>Which declaration and not where it is. Where a declaration is written is the module
         * that wrote it answering, asked when a sentence is about to be written; carried here, an
         * edit that moved the declaration and changed nothing it says would be an edit to this
         * debt and to every finding made of it.
         */
        public TypeSymbol.AtModule pointAt() {
            return owners.getFirst().declaration();
        }
    }

    /**
     * What a module's declarations are owed, and how far the reading it was made from got.
     *
     * <p>Every debt and not only the ones something is short of. A line a row already stands at is
     * what a verdict rests on as much as one nothing stands at: read off the findings, the debts
     * that are covered are not there at all, and a verdict would be settled by a denominator made
     * of the gaps.
     *
     * <p><b>The module's own, which takes two things and not one.</b> A line is here when one of
     * this module's declarations owes it and some behavior of this module reads it. Owing it is the
     * line's answer ({@link souther.compiler.partition.AuthoredLine#ownersIn}): a clause of an
     * imported type says what it says wherever the type is carried, and a row written for it settles
     * the question for everybody, so a module carrying the type is asked for work it cannot do and
     * cannot check. Reading it is what there is to measure with — what a point asks of a row is read
     * off the readings — so a module that owes a line no behavior of it carries holds no debt for it
     * here, and nothing is said about the line at all.
     *
     * <p>An account and not a list, for the reason a behavior's is a {@link Measure}: what is owed
     * is read off the lines this module's behaviors met, so a reading that did not run out may have
     * left this short of debts it never saw. Handed the debts alone, a reader has a list that is
     * empty for two reasons — a module whose declarations owe nothing, and one whose lines nobody
     * could read — and the second reads as the first.
     *
     * <p>Two halves, because they are short of different things. {@code reading} is what finding
     * the debts went without; what became of each debt is the debt's own measurement, and
     * {@link #weakening()} is both.
     *
     * @param owed    every debt this module's declarations hold, covered or not
     * @param reading what finding them went without, under the behavior whose reading went without
     *                it. By behavior and not as one set, because a reader shown some of a module's
     *                behaviors is owed what those behaviors' readings went without and not what the
     *                rest did — folded into one set here, a view filtered to one behavior would
     *                carry a reason about a position its reader cannot see
     */
    public record DeclaredBoundaries(List<DeclaredDebt> owed,
                                     Map<String, WeakeningSet> reading) {

        public DeclaredBoundaries {
            owed = List.copyOf(owed);
            // In the order the readings were made, which is what everything downstream of a
            // weakening is written in: a set of reasons keeps the order they were discovered in so
            // that two runs of one compile write one document, and a copy that did not would hand
            // that set its causes in whatever order a hash gave.
            reading = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(reading));
        }

        /**
         * The same account as a reader shown only {@code behaviors} is owed.
         *
         * <p><b>Every part of it and not the ones that are easy to filter.</b> A debt is what its
         * readings came to together, so a debt kept whole while its readings are filtered carries
         * what a behavior the reader cannot see went without — the same fact, arriving by the half
         * of the account nobody trimmed. So a debt none of them reads is dropped and a debt some of
         * them read is made again from those readings, beside the reading each of them went
         * without.
         *
         * <p>What is not sliced is who owes the line: a declaration owes it wherever the type is
         * carried, and which behaviors a reader is shown is no part of that.
         */
        public DeclaredBoundaries keptFor(java.util.Set<String> behaviors) {
            Map<String, WeakeningSet> kept = new LinkedHashMap<>(reading);
            kept.keySet().retainAll(behaviors);
            List<DeclaredDebt> owedHere = new ArrayList<>();
            for (DeclaredDebt each : owed) {
                BorderObligationPointAssessment debt = each.debt().keptFor(behaviors);
                if (debt != null) {
                    owedHere.add(new DeclaredDebt(debt, each.axis(), each.owners()));
                }
            }
            return new DeclaredBoundaries(owedHere, kept);
        }

        /**
         * What this account went without, all of it.
         *
         * <p>The reading that found the debts and the measurement of each of them. A verdict and a
         * report read this rather than adding the two up themselves, which is what left the reading
         * out of the module's status while every debt said it had been measured in full.
         */
        public WeakeningSet weakening() {
            WeakeningSet out = WeakeningSet.none();
            for (WeakeningSet went : reading.values()) {
                out = out.union(went);
            }
            for (DeclaredDebt each : owed) {
                out = out.union(each.debt().item().weakening());
            }
            return out;
        }
    }

    /**
     * That account, made once.
     *
     * <p>The one place the readings of a line are folded into what is owed. A report, a build's
     * refusal, a document and a generation all ask what became of a line an {@code invariant} drew,
     * and four foldings of one set of readings are four answers about it.
     */
    public record DeclaredBorders(String name) implements Key<DeclaredBoundaries> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<DeclaredBoundaries> compute(Db db) {
            Map<String, Measure<List<BorderAssessment>>> lines =
                    db.ask(new BoundaryReadings(name)).value();
            List<BorderObligationPointAssessment> points =
                    db.ask(new Obligations(name, new GenerationScope.Module())).value();
            if (lines == null || points == null) {
                // Nobody read this module's lines, so what its declarations are owed was not
                // measured either. Answered as an account with no debts, that would be this module
                // owing nothing — which is what a module whose every line is covered also answers.
                return Answer.absent();
            }
            // What finding the debts went without, which is what the readings they are found from
            // went without. Carried rather than dropped: a reading that did not run out may have
            // left a line this module's declarations owe unread, and an account that said nothing
            // about it would report the debts it happened to see as all there are.
            Map<String, WeakeningSet> went = new LinkedHashMap<>();
            lines.forEach((behavior, read) -> {
                if (!read.weakening().isEmpty()) {
                    went.put(behavior, read.weakening());
                }
            });
            if (points.isEmpty()) {
                return Answer.of(new DeclaredBoundaries(List.of(), went));
            }
            // Where a declaration is, which is what an owner is named by and is no part of what the
            // points are. Asked here, once, and its absence is this measure having no answer rather
            // than a debt built without it.
            RuleReadingSource reading = Shapes.ruleReading(db, name).value();
            souther.compiler.check.ReadingPolicy policy = db.ask(new Front.Reading()).value();
            if (reading == null || policy == null) {
                return Answer.absent();
            }
            Map<TypeSymbol, souther.compiler.check.DeclaredBorders> declarations =
                    new LinkedHashMap<>();
            List<DeclaredDebt> out = new ArrayList<>();
            // A run that stops at a body's own rule exists in that body and nowhere else, so no
            // declaration is owed a row inside it however the line beside it was written; and this
            // module keeps an account only where its own declarations are among what owes the
            // point. Which points those are is the reading's own answer, carried through the
            // gathering: a module reading a line another module wrote and narrowing nothing about
            // it owes nothing here, which is the dependency it carries rather than a debt.
            for (BorderObligationPointAssessment debt : points) {
                List<DeclaredDebt.Owner> owners = new ArrayList<>();
                for (TypeSymbol.AtModule owner : debt.ownersIn(name)) {
                    owners.add(new DeclaredDebt.Owner(owner));
                }
                // A point this module keeps no account of: a row its own reading settled, which
                // is that body's to write, or a line owed to declarations elsewhere. Left out here
                // and gathered all the same, so that whoever does keep the account has every
                // reading of it.
                if (owners.isEmpty()) {
                    continue;
                }
                out.add(new DeclaredDebt(debt,
                        axisOf(debt.id(), declarations, Shapes.publishedDeclarations(db),
                                Shapes.declarationCitations(db), reading, policy, db.readings()),
                        owners));
            }
            return Answer.of(new DeclaredBoundaries(out, went));
        }

    }

    /**
     * What a line is on, as the declaration wrote it.
     *
     * <p>The value a newtype wraps is written {@code value}, which is what the author writes in the
     * clause. A coordinate spells an empty path as "the value", which is what it is called where a
     * sentence says it rather than where a line is named.
     *
     * <p>Asked only of a line some declaration drew, and refused of the rest. A line a body's own
     * rule drew is on no quantity anybody wrote: where it is is the reading's answer and there are
     * as many of those as there are positions carrying the rule, so a point named after one of them
     * would be named after a place it is not owed at. Answered with the rule's own name instead,
     * a report said a comparison was the thing being compared.
     */
    private static String axisOf(souther.compiler.partition.BorderObligationId id,
                                 Map<TypeSymbol, souther.compiler.check.DeclaredBorders> read,
                                 PublishedDeclarations published, DeclarationCitations citations,
                                 RuleReadingSource reading,
                                 souther.compiler.check.ReadingPolicy policy,
                                 DeclarationReadings machines) {
        TypeSymbol declaredOn = id.owedToTheDeclaration().orElseThrow(
                () -> new IllegalStateException("what a line with no declaration is on is not"
                        + " something anybody wrote: " + id));
        String named = declarationRead(read, declaredOn, published, citations, reading, policy,
                        machines)
                // Which line of the declaration this is, asked of the rule. Taken apart
                // here, a reader would be deciding which rules have a clause and a
                // conjunct, which is the rule's own answer.
                .nameOf(id.declaredLine().orElseThrow());
        // A clause whose end this could not read from the declaration has no form to print, and
        // the rule's own name is the whole of what there is to call the line.
        return named == null ? id.saidWithoutAPlace() : named;
    }

    /** The declaration's own reading of its own rules, kept: it draws as many lines as its
     *  clauses have ends, and each of them would otherwise read the declaration again. */
    private static souther.compiler.check.DeclaredBorders declarationRead(
            Map<TypeSymbol, souther.compiler.check.DeclaredBorders> kept, TypeSymbol declaredOn,
            PublishedDeclarations published, DeclarationCitations citations,
            RuleReadingSource reading, souther.compiler.check.ReadingPolicy policy,
            DeclarationReadings machines) {
        return kept.computeIfAbsent(declaredOn, each -> souther.compiler.check.DeclaredBorders
                .of(each, published, citations, reading, policy, machines));
    }


    /**
     * Every point this module's lines are owed a row at, with all the readings of each.
     *
     * <p>The one gathering of the readings, which every account of what is owed is a projection of.
     * A line is read wherever the model carries the rule, and a row for it is owed once — so the
     * readings of one point are what a search of it walks and a report's occurrences are, and two
     * gatherings of them are two answers to how much work there is.
     *
     * <p>The scope says where values are composed, and never which lines are read. Every reading of
     * a point is gathered whatever the scope, because how many there are is what says whether a walk
     * of them saw everything; what a narrower scope buys is not paying for a decoder run at the
     * points of a behavior it was not asked about.
     *
     * <p>Whose each point is is carried through rather than asked here
     * ({@link souther.compiler.partition.PointAttribution}), which is what makes this one gathering
     * and not the declarations' one: {@link DeclaredBorders} keeps the account of the points this
     * module's declarations own, and a behavior keeps the account of the points its own rules
     * settled.
     *
     * <p>Nothing is composed. What building a row at a point came to is asked of
     * {@link BoundarySearch}, one reading at a time and by whoever is offering a row — so what
     * everybody pays for is the reading, and a request about one behavior spends nothing on the
     * rest.
     */
    public record Obligations(String name, GenerationScope scope)
            implements Key<List<BorderObligationPointAssessment>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<BorderObligationPointAssessment>> compute(Db db) {
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            if (!prepared.present() || !sigs.present()) {
                return Answer.absent();
            }
            Level level = levelOf(db);
            List<BorderAssessment> readings = new ArrayList<>();
            // Every behavior's lines, and values composed at the ones the scope admits. How many
            // readings a point has is a fact about the model, so a scope that left the other
            // behaviors' lines unread would hand back a point that has one reading — and a walk of
            // that one would be a walk of everything there is, which is the reading that says a row
            // cannot be written at the line.
            //
            // Every reading, also because which account a point falls in is a question about the
            // point and not about the lines it was found on. A line another module wrote can be
            // stopped where this module's declaration takes the position in, and the run beside it
            // is then this module's to answer for — dropped here, that point would be gathered
            // nowhere.
            //
            // How far finding them got is carried by whoever keeps an account, not here: what is
            // gathered is the points, and being short of some of them is a fact about the reading.
            Map<String, InputDomain> readInputs = db.ask(new Inputs(name)).value();
            for (Hir.BehaviorDef behavior : prepared.value().behaviors()) {
                readings.addAll(linesReadIn(db, name, behavior, sigs.value(), readInputs,
                        level.composesValues() && scope.admits(behavior.name()))
                        .made().orElseGet(List::of));
            }
            if (readings.isEmpty()) {
                return Answer.of(List.of());
            }
            return Answer.of(BorderObligationPointAssessment.across(readings));
        }
    }

    /**
     * What each behavior of {@code name} is owed a row for at the lines its own rules drew, and how
     * far the reading that found those lines got.
     *
     * <p>The behaviors' side of the one relation {@link Obligations} gathers, the way
     * {@link DeclaredBorders} is the declarations' side: every point is in one of the two accounts
     * or in neither, and which is the point's own answer
     * ({@link BorderObligationPointAssessment#belongsToBehaviorAccount}). Nothing here reads the
     * lines and works the account out again — a count, a finding, a verdict and an offering that
     * each did so were four answers to how much work there is, and two of them disagreed.
     *
     * <p>A measure per behavior and not a list, for the reason the declarations' account is one:
     * what a behavior is owed is read off the lines its positions met, so a reading that did not run
     * out may have left the account short, and a verdict handed the entries alone would take a
     * behavior whose lines nobody could derive for one with nothing to answer for. The measure is
     * the lines' own ({@link BoundaryReadings}), read as the account.
     */
    public record BodyBorders(String name)
            implements Key<Map<String, Measure<List<BorderObligationPointAssessment>>>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, Measure<List<BorderObligationPointAssessment>>>> compute(Db db) {
            Map<String, Measure<List<BorderAssessment>>> lines =
                    db.ask(new BoundaryReadings(name)).value();
            List<BorderObligationPointAssessment> points =
                    db.ask(new Obligations(name, new GenerationScope.Module())).value();
            if (lines == null || points == null) {
                return Answer.absent();
            }
            Map<String, Measure<List<BorderObligationPointAssessment>>> out = new LinkedHashMap<>();
            lines.forEach((behavior, read) -> out.put(behavior, read.readAs(_ ->
                    points.stream().filter(point -> point.belongsToBehaviorAccount(behavior))
                            .toList())));
            return Answer.of(java.util.Collections.unmodifiableMap(out));
        }
    }

    /**
     * Everything the measures found, whatever each of them is about.
     *
     * <p>The one statement of what counts as a finding. A report prints these, a build is warned about
     * the ones that are gaps, and {@code souther examples --strict} refuses on the same ones — three
     * projections of this and no second reading of the evidence. Computed whether or not the build
     * asked to be warned, because a report wants them either way; what the level decides is which
     * measures were made at all.
     */
    public record Findings(String name) implements Key<List<Finding>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<Finding>> compute(Db db) {
            Answer<CheckSurface> prepared =
                    db.ask(new Shapes.CheckSurface(name));
            if (!prepared.present()) {
                return Answer.absent();
            }
            Map<String, SignatureEvidence> signatures = db.ask(new Witnesses(name)).value();
            // Asked whatever the level is. Each of these says for itself how much of it was made —
            // a line a fork drew comes back `ARMS_NOT_ASKED` where the rows were not instrumented,
            // and a line an invariant drew is measured either way — so dropping them here was this
            // deciding a second time what a measure had already answered, and dropping with them
            // every gap the measures did establish (issue #955).
            Map<String, PartitionEvidence> partitions = db.ask(new Coverage(name)).value();
            Map<String, Measure<List<BorderObligationPointAssessment>>> accounts =
                    db.ask(new BodyBorders(name)).value();
            Map<String, BranchEvidence> branches = db.ask(new BranchCoverage(name)).value();

            // One list and not a block per behavior. What each finding is about is its own
            // ({@link FindingSubject}), and a map keyed by behavior has no key for a finding about
            // a declaration — so one had to be filed under whichever behavior carrying the type a
            // walk reached first, which is a choice nothing made and a reader cannot check
            // (issue #1062). Whoever prints a block per behavior groups these; the model says what
            // each is about.
            //
            // In the order the module declares its behaviors, because a build reads the warnings
            // these become and a set of warnings whose order moves between runs is a diff nobody
            // wrote.
            List<Finding> out = new ArrayList<>();
            for (Hir.BehaviorDef behavior : prepared.value().behaviors()) {
                unansweredRows(prepared.value().module(), behavior.name(), out);
                signatureFindings(behavior.name(),
                        signatures == null ? null : signatures.get(behavior.name()), out);
                partitionFindings(behavior,
                        partitions == null ? null : partitions.get(behavior.name()),
                        accounts == null ? null : accounts.get(behavior.name()), out);
                BranchEvidence branch = branches == null ? null : branches.get(behavior.name());
                if (branch != null && branch.measured().made().isPresent()) {
                    out.addAll(armFindings(behavior.name(), branch.arms()));
                }
            }
            declaredFindings(db, name, out);
            return Answer.of(List.copyOf(out));
        }


        /**
         * The rules of one behavior's decision that no row takes and something can stand in.
         *
         * <p>Three answers upstream and one of them is a finding. A rule the model's own rules
         * leave no value for is owed nothing; a rule the search looked for and did not find is
         * neither covered nor a gap, and reporting it would be a shortfall of this compiler told to
         * an author as work of theirs. Only a rule something was seen standing in is a row somebody
         * can write. None of the three is about what the rows do, which is what the coverage these
         * are asked over already answered.
         *
         * <p>The search is asked only where a rule is left. A behavior whose rows take every rule
         * settles the question without composing anything, which is what keeps this off the builds
         * that have nothing to find.
         */
        private static void decisionFindings(Db db, String module, String behavior,
                                             DecisionEvidence decision, List<Finding> out) {
            // Only where a reading of the runs was made. A measure with no value has not found a
            // rule nothing takes — it has found nothing — and the arms measure answers the same way
            // for the same reason: what is missing where nothing was read is not a set of gaps, and
            // what the build is told instead is the measure's own word for why there is no number.
            if (decision == null || decision.took().made().isEmpty()
                    || decision.notTakenByRows().isEmpty()) {
                return;
            }
            Map<DecisionRule, RuleSettlement> settled =
                    db.ask(new DecisionSearch(module, behavior)).value();
            if (settled == null) {
                return;
            }
            // Made from the reading that found it, which is what says whether a build may refuse
            // over one: a rule nothing was seen taking, where a row could not be placed, is one a
            // row may already take.
            for (souther.compiler.partition.DecisionReading.Ruled ruled
                    : decision.read().found()) {
                RuleSettlement came = settled.get(ruled.rule());
                if (came != null && came.requirement() instanceof RuleRequirement.Required) {
                    out.add(Finding.by(new FindingSubject.OfABehavior(behavior), decision,
                            new About.ARuleNoRowTakes(behavior, ruled)));
                }
            }
        }

        /**
         * What the module's own declarations are short of, from the debts the module holds.
         *
         * <p>One finding per authored line and not per reading of it. A clause of a {@code data}
         * says something about the type wherever the type is carried, so a row standing at the line
         * is evidence about the type and the behaviors carrying it have nothing to add: over
         * {@code crm} one clause of {@code UserId} is read at 126 positions of 74 behaviors, and
         * discharging what that asked for meant writing 126 rows that each stand at the same point
         * (issue #1062).
         *
         * <p>Read off {@link DeclaredBorders} rather than folded here. The debts are what a verdict
         * rests on, what a document publishes and what a generation answers about, and a finding is
         * one more reading of them — worked out again here, each of those consumers would be
         * answering from the readings and the aggregation would hold in none of them.
         */
        private static void declaredFindings(Db db, String module, List<Finding> out) {
            DeclaredBoundaries account = db.ask(new DeclaredBorders(module)).value();
            if (account == null) {
                return;
            }
            for (DeclaredDebt owed : account.owed()) {
                ObligationAssessment item = owed.debt().item();
                if (!(item.disposition() instanceof ObligationDisposition.Unmet)) {
                    continue;
                }
                out.add(Finding.by(owed.subject(), item.coverage(),
                        new About.APointOfADeclaredBorder(owed)));
            }
        }

        /**
         * What the rows left undone about the cases of one signature.
         *
         * <p>Each finding is carried at its own measure's account: a case nothing claims is, where
         * that measure could not read every row, a case nothing <em>seen</em> claims — which is why
         * it is said as undecided rather than withheld. Which of them are said at all is each
         * measure's own question below.
         *
         * <p>Takes the name rather than the whole declaration, because that is what it uses — and
         * because a producer that needs a compiled behavior to run can only be held to
         * what some source happens to produce. What decides a build's answer here is which
         * measurement each finding is given, and the states that tell a right answer from a wrong
         * one are states a fixture may or may not reach; handed the evidence, this can be shown the
         * state itself.
         *
         * <p>Where a case of an input is owed is the evidence's own answer and is asked of it. The
         * measure knows both halves — which input it is of, and whether the behavior has a position
         * of its own — so a finding worked out here would be the second reading of one rule.
         */
        static void signatureFindings(String behavior,
                                      SignatureEvidence signature, List<Finding> out) {
            if (signature == null || signature.counted().made().isEmpty()) {
                return;
            }
            OutputCaseEvidence output = signature.output();
            for (TypeSymbol missing : output.unspecified()) {
                out.add(Finding.by(behavior, output.cases(),
                        new About.ACaseNoRowExpects(missing)));
            }
            // Where the behavior answered for no row, every case is unverified and naming each of
            // them adds nothing to that. Asked of the rows rather than of the declaration: the two
            // agree only while the one thing that applies a behavior is the compile that generated
            // it, and a run that did apply an injected behavior would go on saying nothing about the
            // cases it was never seen to produce.
            //
            // How many rows were answered for, rather than whether any case was observed. A run whose
            // answers are of a type nothing here names observed no case and produced answers all the
            // same, and the cases it did not confirm are worth naming exactly as anywhere else.
            //
            // Left out here rather than at the printing, so that what a report shows and what a build
            // is told come from one list.
            if (output.cases().made().map(OutputCaseEvidence.Cases::answeredRows).orElse(0) > 0) {
                for (TypeSymbol missing : output.unverified()) {
                    if (!output.unspecified().contains(missing)) {
                        out.add(Finding.by(behavior, output.cases(),
                                new About.ACaseNothingWasSeenToProduce(missing)));
                    }
                }
            }
            // Walked as the evidence rather than by index: which input this is, is the evidence's
            // own answer now, so a finding is not handed a number worked out beside the list.
            //
            // Over the positions there are, and asked rather than defaulted. A measure that did not
            // reach the boundary has no position for a gap to be at; standing in an empty list for
            // one would walk it and find nothing, which reads the same as a behavior every position
            // of which is covered.
            if (signature.inputs().made().isEmpty()) {
                return;
            }
            for (InputCaseEvidence input : signature.positions()) {
                for (TypeSymbol missing : input.unspecified()) {
                    // This input's own measurement. One position whose rows could not be classified
                    // says nothing about the position beside it, and a finding handed the signature's
                    // union would report both as undecided over one of them.
                    //
                    // And the class of that position the case is, which is the account's key for
                    // the one thing this and the domain measure are both about. Named here, where
                    // the position is in hand, so that the two halves of one obligation are two
                    // readings of one entry rather than two entries that happen to coincide.
                    out.add(Finding.by(behavior, input.cases(),
                            new About.ACaseNoRowAppliesItTo(input, missing,
                                    signature.owedAt(behavior, input.at(), missing))));
                }
            }
        }

        /**
         * The rows of {@code behavior} whose answers are owed, one finding each.
         *
         * <p>Read off the module's own text, which is where the fact is settled. Every other way of
         * reaching it goes through something that answers a different question and drops this one
         * when its own answer is no: an arm carries it only while no other row covers the arm, only
         * while the behavior has arms at all, and only while nothing weakened the measurement over
         * the rows; a statement carries it only while the row's values are small enough to hand on.
         * None of those is what makes a row's answer owed.
         *
         * <p>{@link Finding#noticed} because nothing measured it. There is no run behind this and
         * nothing about it could have come out otherwise — the row is written and its answer is
         * not — so it carries no weakening and a build's answer to it is the account's alone.
         */
        private static void unansweredRows(Hir.Module module, String behavior, List<Finding> out) {
            for (Hir.Example example : module.examples()) {
                if (!behavior.equals(example.target())) {
                    continue;
                }
                for (Hir.ExampleRow row : example.rows()) {
                    if (row.expected() instanceof Hir.Expected.Unanswered) {
                        out.add(Finding.noticed(behavior,
                                new About.AnUnansweredRow(behavior, row.identity(), row.pos())));
                    }
                }
            }
        }

        /** What the rows reach of what the model distinguishes. A boundary is named only where the
         *  position was read on every row: a row writing the very number the rule names, whose
         *  observation was cut short elsewhere in the same input, is not a row that missed. */
        private static void partitionFindings(Hir.BehaviorDef behavior, PartitionEvidence partition,
                                              Measure<List<BorderObligationPointAssessment>> account,
                                              List<Finding> out) {
            if (partition == null) {
                return;
            }
            for (PartitionEvidence.AxisCoverage axis : partition.axes()) {
                // A class nothing sits in, where nothing was measured, is not a class no row is in.
                // Stopped here rather than where the line is printed: a finding is something a measure
                // established, and one from a measure that was never made is not established at all.
                if (axis.reached().made().isEmpty()) {
                    continue;
                }
                for (PartitionEvidence.AxisClass missing : axis.uncovered()) {
                    out.add(Finding.by(behavior.name(), axis.reached(),
                            new About.AClassNoRowIsIn(missing)));
                }
            }
            // This behavior's account, walked as the things it is owed. One finding per thing and
            // not one per reading: a guard on a name every case of a sum spreads is read once
            // under each case and is one row to write, and a place two of this body's rules drew
            // a line at leaves a run owed to each of them, which are two. A line the declarations
            // are owed is answered once for the module and is no part of this account.
            for (BorderObligationPointAssessment owed
                    : account == null ? List.<BorderObligationPointAssessment>of()
                            : account.made().orElseGet(List::of)) {
                // The one state a finding is made of, and the account is what says which that is.
                // A point no row was measured against is not a gap, neither is one nothing has
                // shown a row can be written at, and neither is one the readings left undecided.
                if (!(owed.item().disposition() instanceof ObligationDisposition.Unmet)) {
                    continue;
                }
                out.add(Finding.by(behavior.name(), owed.item().coverage(),
                        new About.APointOfABorder(owed)));
            }
            // What the model divides this position no way at all, which is the classes question and
            // is answered only for a position that has none.
            //
            // Over the verdict and not over a boolean read off it. The three answers are three
            // different things to say — the model divides nothing here, this could not read what is
            // written here, and a rule divides it in a way no line of this measure holds — and a
            // reader that asked only "is it the first" filed the other two under the first's
            // sentence (issue #1249). A fourth answer arrives here as a compile error.
            for (souther.compiler.partition.UndividedPosition position : partition.notDerivable()) {
                switch (position.why()) {
                    case souther.compiler.partition.UndividedPosition.Why.Absent _ ->
                            out.add(Finding.noticed(behavior.name(),
                                    new About.APositionNoLineDivides(position)));
                    // Both are said by the rule that stopped it, in a finding of its own with the
                    // rule named. Said here as well, they would be one situation under two
                    // sentences, and the one here has no rule to name.
                    case souther.compiler.partition.UndividedPosition.Why.CannotDerive _,
                         souther.compiler.partition.UndividedPosition.Why.StatedWithoutALine _ -> { }
                }
            }
            // And what this could not read, asked of the one reading that answers it. A position
            // with classes can still carry a statement nothing read, so this is not filtered by the
            // list above.
            for (PartitionEvidence.NotRead each : partition.notRead()) {
                // Not measured, because nothing here established anything either way about it.
                out.add(Finding.noticed(behavior.name(),
                        switch (each) {
                            case PartitionEvidence.NotRead.ARule rule ->
                                    new About.ARuleWithoutALine(rule);
                            case PartitionEvidence.NotRead.AnUnclassifiedRule rule ->
                                    new About.ARuleNothingClassified(rule);
                            case PartitionEvidence.NotRead.APosition position ->
                                    new About.APositionThisCouldNotRead(position);
                        }));
            }
            // And what the reading could not hold together, which is neither of the two above: no
            // rule is answerable for it and nothing went unreached. Said whatever the axes made of
            // the position, since what it qualifies is the classes and not their absence.
            for (souther.compiler.inputs.PositionValuesNotSeparated each : partition.notSeparated()) {
                out.add(Finding.noticed(behavior.name(),
                        new About.APositionReadWiderThanItsRules(each)));
            }
            // A position the axes did measure, whose rules this reading is short of. A different
            // thing to act on from one nothing divided: the classes beside it are what the model
            // was read to say, and what was left unread may yet refuse one of them.
            //
            // Read off what the reading of the model recorded, and not off the measures it
            // weakened. A location is measured at as many numbers as the rules name of it and one
            // stop under the location weakens every one of them, so a finding per measure is one
            // thing that went wrong said as many times as the location has numbers. Which measures
            // it weakened is each measure's own to carry beside its classes.
            for (Weakening each : partition.partitioned().weakening().causes()) {
                if (each instanceof Weakening.ModelReadingIncomplete(
                        souther.compiler.partition.ClosureGap.RulesNotReached gap)) {
                    out.add(Finding.noticed(behavior.name(),
                            new About.APositionWhoseRulesWereNotReached(gap)));
                }
            }
            // One per question a rule raised and nothing answered, whether or not the position it
            // is at came back with an axis. A rule that arrived and went unaccounted for is a fact
            // about the rule; that no axis could be derived is a fact about a measure, and the
            // second used to decide whether the first was said at all.
            for (PartitionEvidence.Unanswered each : partition.unanswered()) {
                // The question as the accounting holds it, whose own contract is that it is handed
                // on whole. Which of the names it carries a reader is shown, and what words the
                // question is put in, are the reader's — and both used to be settled here, one of
                // them only to be overruled by every surface that printed it.
                out.add(Finding.noticed(behavior.name(),
                        new About.AQuestionNothingAnswered(each)));
            }
        }

        /**
         * An arm no row goes through, at the arm and not at the declaration: what to do about it is
         * written there.
         *
         * <p>One per arm the account has settled that way. An arm a row that never finished might
         * have gone through is undecided and is named as that, and calling it unreached sends the
         * author after a row that exists.
         *
         * <p>Written off the account and nothing else, which is what lets it be held to that on its
         * own. What a build does about a finding turns on what the measurement behind it went
         * without, and the measurement behind this one is the arm's own reading — handed the branch
         * measure instead, an arm the rows certainly do not reach was reported as one nobody could
         * decide, because a fork somewhere else in the body stood for a number of rules nothing
         * established.
         */
        static List<Finding> armFindings(String behavior, ArmSummary arms) {
            List<Finding> out = new ArrayList<>();
            for (ArmObligation.Counted arm : arms.unmet()) {
                // The arm itself and not words about it. What to call one differs between a report,
                // which is written in one language, and a diagnostic, which is written in the
                // reader's — and the two readings ask the same arm rather than one of them being
                // handed the other's answer.
                //
                // Which of the two sentences the arm gets is the account's answer. An arm a row
                // already stands at, waiting for what the system answers, is work of a different
                // kind from an arm nobody has written a row for, and a reader that asked only
                // whether the arm was covered would send an author to write a second row beside the
                // one they have.
                out.add(Finding.by(behavior, arm.coverage(),
                        switch (arm.awaited()) {
                            case ArmObligation.Awaited.A_ROW_IS ->
                                    new About.ARowAtAnArmAwaitsItsAnswer(arm.display());
                            case ArmObligation.Awaited.NOTHING_IS ->
                                    new About.AnArmNoRowGoesThrough(arm.display());
                        }));
            }
            return List.copyOf(out);
        }
    }

    /**
     * What the rules of each body's decision are owed, for the surfaces that read the account.
     *
     * <p><b>Its own query, and not part of {@link Findings}.</b> Settling whether a rule is owed a
     * row composes a value and runs it, once per rule a row was not seen taking — and the rules of
     * a body are its ways, which multiply. Asked where every other measure's findings are, a
     * compile that wants none of this paid for all of it: one class of typing tests went from
     * under a second to nearly three minutes, and a body written to be too wide to walk from one
     * second to over half an hour.
     *
     * <p><b>Canonical is not eager.</b> There is one account and every surface is a reading of it;
     * what that says is that the surfaces read one answer rather than each making their own, and
     * not that a build which asks no surface a question computes it. This is that one answer, and
     * a surface demands it when it has a reader.
     *
     * <p>Read together with {@link Findings} by {@link #accountOf}, which is the only way in: a
     * surface joining the two lists itself would be a second statement of what the account holds,
     * free to leave one of them out.
     */
    public record DecisionFindings(String name) implements Key<List<Finding>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<Finding>> compute(Db db) {
            Answer<CheckSurface> prepared = db.ask(new Shapes.CheckSurface(name));
            if (!prepared.present()) {
                return Answer.absent();
            }
            Map<String, DecisionEvidence> decisions = db.ask(new Decides(name)).value();
            List<Finding> out = new ArrayList<>();
            for (Hir.BehaviorDef behavior : prepared.value().behaviors()) {
                Findings.decisionFindings(db, name, behavior.name(),
                        decisions == null ? null : decisions.get(behavior.name()), out);
            }
            return Answer.of(List.copyOf(out));
        }
    }

    /**
     * Everything one module is owed a row for, which is what a surface acts on.
     *
     * <p>The account, and it means one thing. Every question it is answered by is asked here, so
     * that what this module is owed is the same list whoever asked — a value that came out
     * differently for two callers is not an account of anything, and a consumer comparing two
     * surfaces of one run would be comparing two definitions.
     *
     * <p>What it costs to answer is a separate question and is asked by {@link
     * #whatAWarningCouldBeAbout}, which is not this and does not claim to be.
     */
    public static List<Finding> accountOf(Db db, String module) {
        return partsOfTheAccount(db, module, EnumSet.allOf(AccountPart.class));
    }

    /**
     * The findings a build held to {@code held} could be warned about, and no more of the account.
     *
     * <p><b>Not the account.</b> A warning is said about a finding a build refuses over, so the
     * kinds nothing refuses over are kinds this surface will say nothing about whatever they hold —
     * and what answers those kinds is work this build would pay for and never read. Which
     * questions those are is not decided here: each kind says which question answers it, and the
     * ones about an obligation name the questions this asks.
     *
     * <p>So the laziness is about which queries are demanded and never about what an account
     * means. A caller that wants the account asks {@link #accountOf}, which asks all of them.
     */
    public static List<Finding> whatAWarningCouldBeAbout(Db db, String module) {
        EnumSet<AccountPart> asked = EnumSet.noneOf(AccountPart.class);
        for (Kind kind : Kind.values()) {
            if (kind.isAboutAnObligation()) {
                asked.add(kind.answeredBy());
            }
        }
        return partsOfTheAccount(db, module, asked);
    }

    /**
     * What {@code asked} of the account comes to, put together in one place.
     *
     * <p>Private, because which parts of it a caller gets is not a caller's to choose: the two
     * above are the two questions anybody here asks, and a third would be a third meaning of the
     * word account.
     */
    private static List<Finding> partsOfTheAccount(Db db, String module,
                                                   Set<AccountPart> asked) {
        List<Finding> out = new ArrayList<>();
        for (AccountPart part : asked) {
            List<Finding> found = switch (part) {
                case THE_MEASURES -> db.ask(new Findings(module)).value();
                case THE_DECISION -> db.ask(new DecisionFindings(module)).value();
            };
            // A question this compile could not answer leaves the account unanswered rather than
            // short by one part of it. Read as an empty list, a surface would gate on the rest and
            // say nothing about the half nobody could read.
            if (found == null) {
                return null;
            }
            out.addAll(found);
        }
        return List.copyOf(out);
    }

    /**
     * What a build asked to be told, as warnings on the declarations they are about.
     *
     * <p>Only what a person can act on. A position the model draws no line through is named in the
     * report and is not a warning: 398 of them across the corpus this was measured on, and every one
     * of them says "no rule was written here", which is a fact about the model and not a mistake in
     * it. A row waiting for a {@code let} is not one either — waiting is the normal state of a model
     * being written.
     *
     * <p>A gap is reported only where the measurement was complete. An undecided one — a row whose
     * value could not be read — is a fact about the reading, and telling an author to write a row
     * they may already have written is worse than saying nothing.
     */
    public record Warnings(String name) implements Key<Boolean> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            Asked asked = askedOf(db);
            // What the build asked to be told, and nothing about how much was measured. A finding
            // is what a measure found, so a level that made none produces none and there is
            // nothing here to hold back (issue #955).
            if (!asked.warn()) {
                return Answer.of(true);
            }
            // What a warning here could be about, which is a projection of the account and not the
            // account. Asked for the whole of it, this build would settle what it is held to say
            // nothing about; read off part of it and called the account, the word would mean two
            // things in one compiler.
            List<Finding> found = whatAWarningCouldBeAbout(db, name);
            if (found == null) {
                return Answer.absent();
            }
            List<Report> reports = new ArrayList<>();
            for (Finding finding : found) {
                if (finding.isAdequacyGap()) {
                    reports.add(warning(db, name, finding));
                }
            }
            return Answer.of(true, reports);
        }

        /**
         * What a row at one point of a border shows, as a hint.
         *
         * <p>Asked of the role, and in the role's own vocabulary. A hint saying which side of the
         * line the value falls on would be keyed on the border being closed or open rather than on
         * the role — {@code n <= 100} is at its ON point on the line and {@code n < 100} is at its
         * OFF point there — so it would be a second reading of one finding, sitting under a sentence
         * that just named the role.
         *
         * <p>One place, because two questions raise these. A line owed at one reading of it and a
         * line owed once over all of them are the same technique's item, and what a row at the point
         * shows is the same thing to say about either.
         */
        private static void hintFor(PointRole role,
                                    souther.compiler.diag.Diagnostic.Builder built) {
            switch (role) {
                case ON -> built.hint(
                        new ExampleMessage.ARowJustInsideShowsTheBorderIsNotFurtherIn());
                case OFF -> built.hint(
                        new ExampleMessage.ARowJustOutsideShowsTheBorderIsNotFurtherOut());
                // Said only to a build held to reliable domain coverage, which is where the two
                // kinds part. In its own words: what a row well inside shows is not what a row a
                // step over shows, and the neighbour's hint would send an author to the wrong value.
                case IN -> built.hint(
                        new ExampleMessage.ARowWellInsideShowsTheBorderIsWhatDivides());
                case OUT -> built.hint(
                        new ExampleMessage.ARowWellOutsideShowsTheBorderIsWhatDivides());
            }
        }

        /**
         * One finding as the warning a build reads.
         *
         * <p>The message keys are written out per kind rather than derived from the code's name, so
         * that a scan for the keys this names finds them — a key built by concatenation is one nothing
         * can see is used. Which findings get here is {@link Finding#isAdequacyGap}'s answer and not
         * this method's.
         */
        private static Report warning(Db db, String module, Finding finding) {
            About said = finding.about();
            souther.compiler.diag.Diagnostic.Builder built = pointedAt(placeOf(db, module, finding))
                    .say(switch (said) {
                        case About.ACaseNoRowExpects(var missing) ->
                                new ExampleMessage.NoRowExpectsThatCase(
                                        missing.name(), finding.named());
                        case About.ACaseNoRowAppliesItTo(var input, var missing, var _) ->
                                new ExampleMessage.NoRowAppliesItToThatCase(missing.name(),
                                        // How a person is told which input, which is one-based and
                                        // is this sentence's to spell.
                                        String.valueOf(input.at() + 1), finding.named());
                        // The rule named without a place. Nothing here knows what to call a
                        // source, so a line and a column written into the sentence would be read
                        // against whichever file the reader has in mind. Where a fork of a body
                        // drew the line, the place is pointed at rather than said, and which
                        // construct it was is a phrase the catalog holds in every language.
                        //
                        // Which point of the border this is crosses that and does not replace it.
                        // How the rule is named follows from whether it has a place; which of the
                        // border's points went unmet is the measurement's own answer, and a
                        // sentence deciding one of them from the other would be reading a rule off
                        // a role.
                        // Which of the two rules this is, is the role's answer, exactly as the kind
                        // is: a point against the line and a point away from it are owed by
                        // different criteria and are told under different codes.
                        // A line owed once over every reading of it. The rule has a name — a
                        // declaration's clause always does — so there is one sentence and not two,
                        // and what the line is on is what the declaration wrote rather than the
                        // position some behavior met it at.
                        case About.APointOfADeclaredBorder(var owed) ->
                                owed.debt().role().againstTheLine()
                                        ? new ExampleMessage.NoRowIsAtThePointOfTheBorderARuleDrew(
                                                owed.debt().role().name(), owed.axis(),
                                                owed.against(), owed.debt().id().saidWithoutAPlace())
                                        : new ExampleMessage
                                                .NoRowIsAtThePointAwayFromTheBorderARuleDrew(
                                                owed.debt().role().name(), owed.axis(),
                                                owed.against(), owed.debt().id().saidWithoutAPlace());
                        // A body's line, owed once wherever it is read, so the sentence names no
                        // quantity: which of the four points, and which rule — by name where the
                        // author gave it one, and as what the rule is where they gave none and it
                        // is found by where it is written. Writing where the point is takes a
                        // quantity and a quantity is a reading's, so that is said under this, by
                        // the reading whose word it is.
                        // Asked of the rule the point is filed under and not of a handle for it.
                        // What the sentence says is what the author called the rule, or what the
                        // rule is where they called it nothing; how a reader is sent to it is the
                        // other question, and a rule reached at two calls has an answer per call.
                        case About.APointOfABorder(var point) ->
                                switch (point.point().line().provenance()) {
                            case RuleRef.Named named ->
                                    point.role().againstTheLine()
                                            ? new ExampleMessage.NoRowIsAtThePointOfTheLineARuleDrew(
                                                    point.role().name(), named.citedName())
                                            : new ExampleMessage
                                                    .NoRowIsAtThePointAwayFromTheLineARuleDrew(
                                                    point.role().name(), named.citedName());
                            case RuleRef.Written written ->
                                    point.role().againstTheLine()
                                            ? new ExampleMessage
                                                    .NoRowIsAtThePointOfTheLineAConstructDrew(
                                                    point.role().name(), whatItIs(written))
                                            : new ExampleMessage
                                                    .NoRowIsAtThePointAwayFromTheLineAConstructDrew(
                                                    point.role().name(), whatItIs(written));
                        };
                        case About.AnArmNoRowGoesThrough(var arm) ->
                                new ExampleMessage.NoRowGoesThroughThatArm(
                                        phraseFor(arm), arm.behavior());
                        case About.ARowAtAnArmAwaitsItsAnswer(var arm) ->
                                new ExampleMessage.ARowAtThatArmAwaitsItsAnswer(
                                        phraseFor(arm), arm.behavior());
                        // The row's own name where it wrote one, which is what says which row is
                        // meant from outside the file. An unnamed row is pointed at instead: the
                        // report is anchored where the row is written, and an ordinal is not words
                        // about a row.
                        case About.AnUnansweredRow(var behavior, var row, var _) ->
                                row instanceof RowIdentity.Named named
                                        ? new ExampleMessage.TheNamedRowsAnswerIsOwed(
                                                named.name(), behavior)
                                        : new ExampleMessage.TheRowsAnswerIsOwed(behavior);
                        // The class and the position it is a class of, in the partition's own
                        // words — which are the words the report writes for the same finding.
                        case About.AClassNoRowIsIn(var missing) ->
                                new ExampleMessage.NoRowIsInThatClass(missing.name(),
                                        missing.axis().name(), finding.named());
                        // The behavior and nothing else. What tells one rule from another is the
                        // proposition an account keys on, written the one way round that makes a
                        // comparison and its denial one column — so a sentence spelling it would
                        // show an author a comparison they did not write. Which rule it is, is
                        // said underneath, one note per condition.
                        case About.ARuleNoRowTakes(var behavior, var _) ->
                                new ExampleMessage.NoRowTakesADecisionRule(behavior);
                        // Kinds no build is told about under any code. Listed rather than
                        // defaulted, so that one added later has to be answered here rather than
                        // arriving as a warning with no sentence.
                        case About.ACaseNothingWasSeenToProduce _,
                                About.APositionNoLineDivides _, About.APositionThisCouldNotRead _, About.ARuleWithoutALine _,
                                About.ARuleNothingClassified _,
                                About.APositionWhoseRulesWereNotReached _,
                                About.APositionReadWiderThanItsRules _,
                                About.AQuestionNothingAnswered _ ->
                                throw new IllegalArgumentException(
                                        "no message for " + finding.kind());
                    });
            switch (said) {
                case About.ACaseNoRowExpects(var missing) ->
                        built.hint(new ExampleMessage.WriteARowExpectingThatCase(missing.name()));
                // The same hints, asked of the role. What a row at each point shows is a fact
                // about the point and not about which of the two questions raised it.
                case About.APointOfADeclaredBorder(var owed) ->
                        hintFor(owed.debt().role(), built);
                case About.APointOfABorder(var point) -> {
                    // Asked of the point, and in the point's own vocabulary. A hint saying which
                    // side of the line the value falls on would be keyed on the border being closed
                    // or open rather than on the role — `n <= 100` is at its ON point on the line
                    // and `n < 100` is at its OFF point there — so it would be a second reading of
                    // one finding, sitting under a sentence that just named the role.
                    hintFor(point.role(), built);
                    // Each reading of the line, in its own words. The sentence names no quantity
                    // — the line is owed once wherever it is read — so where a row can be written
                    // and what it has to do there is said here, one note per reading, in the
                    // order the sentences sort and never the order the walk took.
                    List<BorderObligationPointAssessment.ReadingSaid> readings =
                            point.readingsSaid();
                    for (BorderObligationPointAssessment.ReadingSaid read : readings.subList(0,
                            Math.min(readings.size(),
                                    BorderObligationPointAssessment.READINGS_SAID))) {
                        built.hint(new ExampleMessage.TheLineAsReadAt(read.at(), read.asks()));
                    }
                    if (readings.size() > BorderObligationPointAssessment.READINGS_SAID) {
                        built.hint(new ExampleMessage.MoreReadingsOfTheLine(
                                readings.size() - BorderObligationPointAssessment.READINGS_SAID));
                    }
                    // Where the rule has a place rather than a name, the place is a second region
                    // and not words in the sentence: a renderer resolves what to call its file,
                    // and a body written out of sight says so off its own coordinate.
                    //
                    // Where the guard is in a file this compile has none of, there is nothing to
                    // point at and the label says where the code came from instead. It used to be
                    // dropped, on the grounds that a label naming no source would be read against
                    // the file the diagnostic is in; a label no longer takes its file from where it
                    // is shown, so what was left unsaid can be said.
                    // One marker, from the one handle a document would write of the several a rule
                    // reached at several calls offers — chosen where that choice is made rather
                    // than by whichever reading this happened to walk first.
                    if (PublicationOrders.handleFor(point.citations(),
                                    cited -> Sites.placeOf(db, cited)).orElse(null)
                            instanceof RuleCitation.Written written) {
                        switch (Sites.placeOf(db, written)) {
                            case souther.compiler.diag.Citation.Written w ->
                                    built.secondary(souther.compiler.diag.Region.point(w.at()),
                                            new ExampleMessage.TheConstructThatDrawsTheLine(
                                                    whatItIs(written.rule())));
                            case souther.compiler.diag.Citation.Reached r ->
                                    built.secondary(souther.compiler.diag.Region.point(r.at()),
                                            new ExampleMessage.TheConstructThatDrawsTheLine(
                                                    whatItIs(written.rule())));
                            // Nowhere this compilation can put a marker. Where the guard is written
                            // out of sight the label says so instead; where it is in a text the
                            // caller handed over there is no declaration to name and nothing to say,
                            // so there is no label. A marker over such a region is not an option:
                            // a place a reader is sent to names its source, and this one cannot.
                            case souther.compiler.diag.Citation.Elsewhere e ->
                                    built.secondaryOutOfSight(e.provenance(),
                                            new ExampleMessage.TheConstructThatDrawsTheLine(
                                                    whatItIs(written.rule())));
                            case souther.compiler.diag.Citation.Unplaced _ -> { }
                        }
                    }
                }
                case About.AnArmNoRowGoesThrough _ ->
                        built.hint(new ExampleMessage.EitherARowIsMissingOrNothingReachesIt());
                // One note per condition, which is what tells this rule from the rules beside it.
                // The sentence above says only which behavior, so a rule whose conditions were
                // dropped here would be a finding two of which a reader cannot act on.
                case About.ARuleNoRowTakes(var behavior, var ruled) -> {
                    Bodies.Elaborated checked = db.ask(new Bodies.Checked(module)).value();
                    for (DecisionRuleReading read : DecisionRuleReading.of(ruled,
                            checked == null ? CoverageSites.Plan.NONE : checked.plan(), behavior)) {
                        said(db, built, read);
                    }
                }
                // Which of the two this arm is, is already settled: a row is there. What is left is
                // the answer, so the hint says how it is written rather than what might be wrong.
                case About.ARowAtAnArmAwaitsItsAnswer _, About.AnUnansweredRow _ ->
                        built.hint(new ExampleMessage.ReplaceTheMarkWithWhatTheSystemAnswers());
                // Said as the row to write and not as the class to cover. A class is met by a
                // value falling in it, and what an author writes is the value — a hint naming the
                // class alone leaves them to work out which of the position's values is one.
                case About.AClassNoRowIsIn(var missing) ->
                        built.hint(new ExampleMessage.WriteARowWhoseValueThereIsInThatClass(
                                missing.axis().path(), missing.name()));
                // The message says all there is to say. Written out rather than defaulted, for the
                // reason the switch above gives.
                case About.ACaseNoRowAppliesItTo _, About.ACaseNothingWasSeenToProduce _,
                        About.APositionNoLineDivides _,
                        About.APositionThisCouldNotRead _, About.ARuleWithoutALine _,
                        About.ARuleNothingClassified _,
                        About.APositionWhoseRulesWereNotReached _,
                        About.APositionReadWiderThanItsRules _,
                        About.AQuestionNothingAnswered _ -> { }
            }
            return Report.of(built.build());
        }

        /**
         * Where a reader is sent for a finding: the place, or where the code was reached from when
         * it is written out of sight.
         *
         * <p>Only where to put the caret. What the warning says about that place is the body's, said
         * off the coordinate it is built at — which carries the same provenance this reads, so the
         * two cannot come apart.
         */
        static souther.compiler.diag.Diagnostic.Builder pointedAt(Citation cited) {
            return switch (cited) {
                case Citation.Written written -> souther.compiler.diag.Diagnostic.at(written.at());
                case Citation.Unplaced unplaced ->
                        souther.compiler.diag.Diagnostic.at(unplaced.at());
                case Citation.Reached reached -> souther.compiler.diag.Diagnostic.at(reached.at());
                case Citation.UnplacedElsewhere out -> souther.compiler.diag.Diagnostic.at(out.at());
                // Nowhere to point, and which module wrote the code is known. Said as that rather
                // than as no place at all: the reading that moves a report to where a reader can be
                // sent needs the answer this finding already has, and would otherwise work it out
                // again from whichever module the report was filed under.
                case Citation.OutOfSight out ->
                        souther.compiler.diag.Diagnostic.atCodeWrittenOutOfSight(out.provenance());
            };
        }

        /**
         * What a sentence calls a rule that has no name, as a phrase the reader's language supplies.
         *
         * <p>One phrase per kind of such rule and not one over them, because a reader acts on the
         * kind: sent to a comparison they are sent to a line and owed a row either side of it, and
         * sent to a predicate they are sent to a set of values told from the rest. Which construct
         * stands around either is a fact about the body and not about the rule, so no phrase here is
         * a keyword.
         *
         * <p>Written out rather than built from the rule's own word, because these are catalog keys
         * and the catalog holds them in every language. No {@code default}, so a kind of written
         * rule added to the seal arrives here as a case with no phrase rather than as one quietly
         * answered with its neighbour's.
         *
         * <p>What reaches this is a rule that drew a line, which today is a comparison: the sentence
         * is about a line and a predicate draws none, so a border's rule is a comparison's
         * ({@link souther.compiler.partition.LineOrigin}). The predicate phrase is here because the
         * seal is total and not because a document writes one, and what holds the two words together
         * for a document that does is asked where such a document is written.
         */
        private static Localizable whatItIs(
                RuleRef.Written rule) {
            return switch (rule) {
                case RuleRef.Comparison _ ->
                        Localizable.of("construct.comparison");
                case RuleRef.Fork _ ->
                        Localizable.of("construct.fork");
                case RuleRef.Predicate _ ->
                        Localizable.of("construct.predicate");
            };
        }

        /**
         * What a sentence calls one arm, as a phrase the catalog holds in every language.
         *
         * <p>Chosen here and not where the arm was found. The measurement answers what the arm is —
         * a construct and a way through it — and what to call one is a question only a sentence with
         * a reader has; the report writes a short word for the same arm and this writes a phrase, and
         * neither is the other's to decide. Written off the name the pair already settles, so a
         * construct added to the language arrives here as a case with no phrase rather than as one
         * quietly answered with a neighbour's.
         */
        private static Localizable phraseFor(
                souther.compiler.coverage.CoverageSites.Site arm) {
            return switch (arm.name()) {
                case THEN -> Localizable.of("arm.then");
                case ELSE -> Localizable.of("arm.else");
                case CONTINUED -> Localizable.of("arm.continued");
                case KEPT -> Localizable.of("arm.kept");
                case DROPPED -> Localizable.of("arm.dropped");
                case CONSTRUCTED -> Localizable.of("arm.constructed");
                case CASE -> Localizable.of("arm.case", casesOf(arm));
                case DEPARTURE -> clauseOf(arm)
                        .map(c -> Localizable.of("arm.departure.clause", c))
                        .orElseGet(() -> Localizable.of("arm.departure"));
                // Not an arm, so no warning is about one. Reaching this is the branch measure and
                // this sentence disagreeing about what it counts.
                case COMPARISON -> throw new IllegalStateException(
                        "no arm was unreached here: " + arm);
            };
        }

        /**
         * One condition of a decision rule, said under the sentence about the rule.
         *
         * <p>Exhaustive with no {@code default}, so a shape added to the reading is one somebody
         * words rather than one that goes quiet — and a rule described by fewer conditions than it
         * turns on is a rule a reader cannot tell from the one beside it.
         *
         * <p>What each of them says is which construct and which way, and never the proposition an
         * account keys on: the author reads their own comparison at the place this points to.
         */
        private static void said(Db db, souther.compiler.diag.Diagnostic.Builder built,
                                 DecisionRuleReading read) {
            switch (read) {
                case DecisionRuleReading.AComparisonCameOut(var comparison, var held) ->
                        label(built, comparison.at(), held
                                ? new ExampleMessage.TheRuleTakesThisComparisonHolding()
                                : new ExampleMessage.TheRuleTakesThisComparisonFailing());
                case DecisionRuleReading.AForkTookAnArm(var arm) ->
                        label(built, Sites.placeOf(db, arm.anchor()),
                                new ExampleMessage.TheRuleGoesThroughThisArm(phraseFor(arm)));
                // Every shape with nothing to send a reader to, said as one note. What differs
                // between them is which part of this compiler fell short, which is not something
                // an author acts on — and a note is written for each so that the rule is never
                // described by fewer conditions than it turns on.
                case DecisionRuleReading.AConditionIsNotShown _,
                        DecisionRuleReading.AComparisonIsNotPlaced _,
                        DecisionRuleReading.AForkIsNotPlaced _ ->
                        built.hint(new ExampleMessage.OneConditionOfTheRuleIsNotShown());
            }
        }

        /**
         * One condition of the rule, marked where the author wrote it.
         *
         * <p>A marker and not a sentence naming a place. Nothing here knows what to call a source,
         * so a line and a column written into the words would be read against whichever file the
         * reader has in mind — which is the same reason the construct that draws a line is marked
         * rather than said.
         *
         * <p>Where there is nowhere to put one, the note says the condition cannot be shown. A
         * marker over a region whose source this compilation does not hold is not an option: a
         * place a reader is sent to names its source, and that one cannot.
         */
        private static <M extends ExampleMessage & souther.compiler.diag.msg.Supporting> void label(
                souther.compiler.diag.Diagnostic.Builder built, Citation at, M said) {
            switch (at) {
                case Citation.Written w ->
                        built.secondary(souther.compiler.diag.Region.point(w.at()), said);
                case Citation.Reached r ->
                        built.secondary(souther.compiler.diag.Region.point(r.at()), said);
                case Citation.OutOfSight out ->
                        built.secondaryOutOfSight(out.provenance(), said);
                // Nowhere this compilation can put a marker and no source to name instead. Said as
                // a condition that cannot be shown, which is what it is: a place a reader is sent
                // to names its source, and neither of these has one.
                case Citation.Unplaced _, Citation.UnplacedElsewhere _ ->
                        built.hint(new ExampleMessage.OneConditionOfTheRuleIsNotShown());
            }
        }

        private static String casesOf(souther.compiler.coverage.CoverageSites.Site arm) {
            return arm.outcome() instanceof souther.compiler.coverage.SourceOutcome.Matched matched
                    ? matched.cases().stream()
                            .map(souther.compiler.types.TypeSymbol::name)
                            .collect(java.util.stream.Collectors.joining(" | "))
                    : "";
        }

        private static java.util.Optional<String> clauseOf(
                souther.compiler.coverage.CoverageSites.Site arm) {
            return arm.outcome() instanceof souther.compiler.coverage.SourceOutcome.Failed(
                    souther.compiler.coverage.SourceOutcome.FailedBy.Construction(var clause))
                    ? clause : Optional.empty();
        }

    }

    /**
     * The cases an input position has to be covered at: what it divides into, read through the names
     * it writes its values under.
     *
     * <p>A position typed as one data has one case, and covering it is not a question: any row at all
     * covers it, so reporting {@code 1/1} everywhere adds a number that is never anything else. What
     * is worth counting is a position that can be more than one thing — which a
     * {@code data DecisionN = Decision} is, since its values are the cases of {@code Decision} under a
     * name. Asked of the written type, that name is where the reading stops, and a position the
     * declaration divides two ways comes back as one the model divides no way.
     *
     * <p>The names come off by {@link TypeOps#base}, and what a row wrote at the position has the same
     * ones taken off it ({@link FixtureReader#caseUnder}) — the terminal and the layers of the one walk
     * that says how far a newtype reaches, so neither end decides for itself how far that is.
     *
     * <p>Both ends or neither. A denominator read through the names has no member in common with a
     * numerator answering with the outermost of them, so every row would land outside the set it is
     * counted in: {@code 1} of {@code 2} covered, and both of the two still owed a row.
     */
    private static Set<TypeSymbol> inputCoverableCases(Type t, Symbols symbols) {
        return casesOfSum(TypeOps.base(t, symbols), symbols);
    }

    /**
     * The cases the output has to be covered at, which is not quite what a row's expected arm is held
     * against ({@link TypeOps#outputCases}).
     *
     * <p>Not {@link #inputCoverableCases}. The two were one function on the strength of running the
     * same way, and they are not the same question: an output written under a name is answered with
     * that name — the arm a row states, the arm a result is read as, and the set the two are held
     * against all say {@code DecisionN} — so counting its cases here would name arms no row may write.
     * What a name over a sum means at an output is a question of its own and is not answered here.
     *
     * <p>The arm check is wider than this on purpose: it uses the single name of a position that is
     * not a sum at all to catch a row that wrote the wrong one.
     */
    private static Set<TypeSymbol> outputCoverableCases(Type t, Symbols symbols) {
        return casesOfSum(t, symbols);
    }

    /** What a sum divides into, and nothing for a type that is not one. The one thing the two
     *  measures above share; what tells them apart is which type each hands it. */
    private static Set<TypeSymbol> casesOfSum(Type t, Symbols symbols) {
        return TypeOps.isSumType(t, symbols)
                ? new LinkedHashSet<>(AtomSpace.subjectAtoms(t, symbols))
                : Set.of();
    }

    /**
     * @param excluded which of the cases declared at each position the rules refuse, which is what
     *                 decides the denominator here. Not the type's cases alone: a case the rules
     *                 refuse is one no row can be built at, and counting it holds the model short
     *                 for ever. Handed the answer and not the reading it was read off, so that a
     *                 behavior with no reading of its own has nothing to be handed in its place
     */
    static SignatureEvidence evidenceOf(String name, Sig sig, Symbols symbols, boolean asked,
                                        RowReading seen,
                                        InputPositions layout,
                                        InputCaseExclusions excluded,
                                        souther.compiler.core.Core body,
                                        souther.compiler.coverage.CoverageSites.Plan plan,
                                        souther.compiler.check.PathReachability.Answers.AsRun reachable) {
        List<RowOutcome> rows = seen.rowsSeen();
        // The cases the output type has, less the ones only an arm nothing reaches produces. A case
        // no reachable producer answers with is not a gap in the rows.
        Set<TypeSymbol> declaredOut = souther.compiler.partition.ProducedCases.of(
                body, plan, reachable.answers(), outputCoverableCases(sig.outputType(), symbols));
        Set<TypeSymbol> specified = new LinkedHashSet<>();
        Set<TypeSymbol> observed = new LinkedHashSet<>();
        Set<TypeSymbol> verified = new LinkedHashSet<>();
        int unreadableOut = 0;
        int answered = 0;

        List<Type> ins = sig.inputTypes();
        List<Set<TypeSymbol>> declaredIn = new ArrayList<>(ins.size());
        List<Set<TypeSymbol>> inSpecified = new ArrayList<>(ins.size());
        List<Set<TypeSymbol>> inExecuted = new ArrayList<>(ins.size());
        List<Set<TypeSymbol>> inVerified = new ArrayList<>(ins.size());
        List<Set<TypeSymbol>> inExcluded = new ArrayList<>(ins.size());
        int[] unreadableIn = new int[ins.size()];
        for (int i = 0; i < ins.size(); i++) {
            Set<TypeSymbol> declared = inputCoverableCases(ins.get(i), symbols);
            declaredIn.add(declared);
            inSpecified.add(new LinkedHashSet<>());
            inExecuted.add(new LinkedHashSet<>());
            inVerified.add(new LinkedHashSet<>());
            inExcluded.add(excluded.at(i, declared));
        }

        // What the model declares is settled above and holds whether or not anybody measured; what
        // the rows made of it is below. A build that asked for nothing gets the first and says so
        // about the second, in each measure and not in the one above them.
        if (!asked) {
            List<InputCaseEvidence> none = new ArrayList<>(ins.size());
            for (int i = 0; i < ins.size(); i++) {
                none.add(InputCaseEvidence.notAsked(i, declaredIn.get(i), inExcluded.get(i)));
            }
            OutputCaseEvidence out = OutputCaseEvidence.notAsked(declaredOut);
            return out.cases() instanceof Measure.NotApplicable<OutputCaseEvidence.Cases>
                    && none.stream().allMatch(in ->
                            in.cases() instanceof Measure.NotApplicable<InputCaseEvidence.Cases>)
                    ? SignatureEvidence.notASum(out, none, layout)
                    : SignatureEvidence.notAsked(out, none, layout);
        }

        for (RowOutcome row : rows) {
            boolean held = row.disposition() == Disposition.HELD;
            if (row.expectedArm() != null) {
                specified.add(row.expectedArm());
            } else if (!declaredOut.isEmpty()) {
                unreadableOut++;   // an expectation whose case the text does not say
            }
            if (row.answered()) {
                answered++;
            }
            if (row.observed()) {
                observed.add(row.resultArm());
                if (held) {
                    verified.add(row.resultArm());
                }
            }
            for (int i = 0; i < ins.size(); i++) {
                if (declaredIn.get(i).isEmpty()) {
                    continue;   // not a sum: nothing to cover at this position
                }
                TypeSymbol written = i < row.inputCases().size() ? row.inputCases().get(i) : null;
                if (written == null) {
                    unreadableIn[i]++;
                    continue;
                }
                if (row.stage().reached(Stage.FIXTURES_VALIDATED)) {
                    inSpecified.get(i).add(written);
                }
                if (row.stage().reached(Stage.INVOKED)) {
                    inExecuted.get(i).add(written);
                }
                if (held) {
                    inVerified.get(i).add(written);
                }
            }
        }

        // What the rows this was counted over went without. A source none of whose rows were seen
        // may hold the row that covers a case, so a count over what remains is a count over some of
        // them — and that is these measures' own business, not something the signature above them
        // holds on their behalf. A row that was seen and did not finish is not here: it arrives
        // through the case it could not be classified into, which is what the counts already say.
        Set<Weakening> unseen = new LinkedHashSet<>();
        for (Incompleteness.Met gap : seen.gaps()) {
            if (gap.fact().code().leftNoRowRead()) {
                unseen.add(new Weakening.ObservationIncomplete(gap));
            }
        }
        WeakeningSet observedWentWithout = WeakeningSet.ofAll(unseen);
        boolean anyRowWasSeen = !rows.isEmpty();
        OutputCaseEvidence output = OutputCaseEvidence.of(name, declaredOut,
                new OutputCaseEvidence.Cases(specified, observed, verified, unreadableOut,
                        answered), anyRowWasSeen, observedWentWithout);
        List<InputCaseEvidence> inputs = new ArrayList<>(ins.size());
        for (int i = 0; i < ins.size(); i++) {
            inputs.add(InputCaseEvidence.of(name, i, declaredIn.get(i), inExcluded.get(i),
                    new InputCaseEvidence.Cases(inSpecified.get(i), inExecuted.get(i),
                            inVerified.get(i), unreadableIn[i]), anyRowWasSeen,
                    observedWentWithout));
        }
        // Asked before the rows are, because it is not about them. A signature with no sum anywhere
        // in it has nothing for this measure to be about, and writing every row anybody could write
        // would not give it one — so it is inapplicable rather than unmeasured, and a build is not
        // told to go and do something about it.
        if (output.declared().isEmpty()
                && inputs.stream().allMatch(in -> in.declared().isEmpty())) {
            return SignatureEvidence.notASum(output, inputs, layout);
        }
        if (rows.isEmpty() && seen.complete()) {
            return SignatureEvidence.noRows(output, inputs, layout);
        }
        // And the signature is the union of its parts, with nothing of its own. What the rows went
        // without reaches it through every case measure that was counted over them, so holding it
        // here as well would be the one fact arriving twice.
        return SignatureEvidence.of(output, inputs, layout);
    }

    private Adequacy() {}

    /**
     * Whether the row is waiting for its answer, so that what it went through is not evidence that
     * anything about the model was asserted there.
     *
     * <p>Asked of what the row was read as, which the outcome carries whatever became of it. Asked
     * of what the row states instead, a row whose values were too large to hand on would sort with
     * the rows that assert something: a statement carries an expectation only while it carries the
     * values, and dropping the values dropped the answer being owed with them.
     *
     * <p>Not asked of {@link RowOutcome#expectedArm()} being absent either. A row that names no
     * case has none of those, and it states an answer all the same.
     */
    private static boolean awaitsItsAnswer(RowOutcome row) {
        return row.expectation() == ExpectationState.OWED;
    }

    /**
     * The arms {@code row} was seen at, which is none where nothing watched it.
     *
     * <p>What the two readers here are gathering is the arms some row of the module reached, so a
     * row with no account of its run adds nothing to it. Said by adding nothing rather than by
     * standing an empty run in for the missing one: a run that reached nowhere is a fact about a
     * row, and one nobody watched is the absence of any — and a value made to carry the second
     * would answer the first about every place it was asked.
     *
     * <p>That a row was left undecided is not lost by this: it is said where the row is reported,
     * of the row rather than of the arms.
     */
    private static Set<ArmProbe> armsSeenIn(RowOutcome row, Optional<SiteNumbering> numbering) {
        return switch (ObservedInputs.of(row, numbering).watched()) {
            case Generator.Watched.Ran(var account) -> account.arms();
            case Generator.Watched.NoAccount _ -> Set.of();
        };
    }

    /**
     * What came of running {@code row}, as something that says which of the two nothings it is.
     *
     * <p>A row whose counting was never read, and a compile that records nothing of any row, both
     * leave an empty account — and neither of them is a row that went nowhere. Handed over as an
     * account, the difference is gone by the time anything acts on it, and a combination the row
     * may well fill reads as one it was shown not to.
     */
    private static souther.compiler.partition.Generator.Watched watched(RowOutcome row,
                                                                        boolean recording,
                                                                        Optional<SiteNumbering>
                                                                                numbering) {
        if (!recording) {
            // The row ran — every row of an evaluated source does — and nothing was recording it.
            // Answered as having no account rather than as a run with an empty one, which is what a
            // row that reached nothing leaves and is a different thing to have found out.
            return new souther.compiler.partition.Generator.Watched.NoAccount();
        }
        // What the row's own run came to, which is one reading and is made where a tuple of values
        // is read. Whether this build was recording is the question above and is this caller's: it
        // follows from what was asked for rather than from the row.
        return ObservedInputs.of(row, numbering).watched();
    }

}
