package souther.compiler.examples;

import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.observe.Observations;
import souther.compiler.observe.WaitShown;
import souther.compiler.generated.EvaluationArtifact;
import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.check.AtomSpace;
import souther.compiler.core.Contract;
import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.Symbols;
import souther.compiler.ast.Hir;
import souther.compiler.check.Sig;
import souther.compiler.check.BoundaryInput;
import souther.compiler.check.BoundaryOutput;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;
import souther.compiler.check.TypeOps;
import souther.compiler.check.TypeView;
import souther.compiler.coverage.RunRecord;
import souther.compiler.coverage.Probe;
import souther.compiler.generated.ProbeImage;
import souther.compiler.diag.Diagnostic;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.SoutherJvmAbi;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.evaluate.DepthLimitExceeded;
import souther.compiler.evaluate.EvaluationContext;
import souther.compiler.evaluate.StepLimitExceeded;
import souther.compiler.diag.SourcePos;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.ExpectationState;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.Mismatch;
import souther.compiler.observe.RowStatement;
import souther.compiler.observe.RowStatements;
import souther.compiler.observe.StoodIn;
import souther.compiler.observe.Verdict;
import souther.compiler.observe.FailurePhase;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.RowIdentity;
import souther.compiler.observe.Applied;
import souther.compiler.observe.Counting;
import souther.compiler.observe.Run;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Stage;

import souther.compiler.meta.Agreement;
import souther.compiler.meta.DeclarationAgreement;
import souther.compiler.meta.PublishedClasses;
import souther.compiler.meta.Readback;
import souther.compiler.meta.ReadbackReasons;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Evaluates a module's {@code example}s at compile time and reports any mismatch as a compile error.
 * It mirrors {@link Runner}: it decodes each row's input expressions into the target
 * behavior's parameter types through their derived decoders, has the behavior applied to them, and
 * compares the result against the expected arm (a bare type name) or value (a construction/literal).
 * A failing example fails the build, exactly as a construction whose constant argument breaks its
 * invariant does.
 *
 * <p>What applies the behavior is not here. It is given to the run ({@link Answering}), because every
 * part of applying one is a fact about the loader its classes come from: which class the behavior is,
 * what its constructor takes, and what a stand-in has to be to be taken by it. What is here is what a
 * row <em>means</em> — which value a fixture states, which row of a fake's table answers, what an
 * answer is compared against, what a failure is reported as — and none of that changes with the
 * answerer. A row's inputs cross as {@link Handed} and the answer comes back as whatever the answerer
 * applies, read by the one reading a run has.
 *
 * <p>A row whose behavior this run has something applying is evaluated, and one whose behavior
 * nothing applies is recorded (spec {@code example-pending}). What is refused is a target that is
 * not a behavior at all — a helper {@code let} of that name ({@code E1902}).
 * A {@code depends on} dependency is satisfied by a fake supplied at the example: a
 * {@code with dep = value} on the row (a constant/value dependency) or a {@code fake dep | table}
 * declaration (an input-keyed function dependency). What the fake answers is read here; making it
 * something the behavior can be constructed with is the answerer's ({@link DependencyStandin}).
 * Inputs, expected values, and fake entries are fixtures — literals, newtype constructions, record
 * constructions, and helpers applied to those (ADR-0077), each compiled as a definition of the
 * module and run as one.
 *
 * <p>Two things a row needs are not here, because they are not about what an example means:
 * {@link FixtureReader} reads what a fixture states as the value it states — through
 * {@link NeutralForm} for the form a decoder reads and {@link OperandRunner} for an operand run as the
 * method its module emits — and {@code RowWork} owns the state one row builds up while it is
 * evaluated. {@link RowEvaluation} is what a caller running one row is answered with, which is a
 * different thing from either.
 *
 * <p>What a module <em>wrote</em> is not here either. {@link ExampleStatements} reads a module's
 * written statements against each other and reports where two of them answer differently, which is a
 * different question for a different key and reads none of this class's state. The one thing they
 * share is a fake's table: it is built there ({@link ExampleStatements#standins}) and answers by its
 * own rule ({@link ExampleStatements.Standins#answering}), and the stand-in a row runs against asks
 * the same one — a second reading of one table is what <em>E1919</em> exists to catch.
 */
public final class ExampleVerifier {

    /**
     * Evaluates every example in {@code module}; returns one diagnostic per failing example row
     * (empty when all pass). Does not throw for a failed example — the caller aggregates. A fixture
     * of an imported type decodes against its declaring module's package, which its
     * {@link TypeSymbol} names (a cross-module example).
     *
     * <p>Classes this compile did not generate resolve under {@code parent} — where an example calls
     * into a module that was compiled by another project, that is the loader its classes come from.
     *
     * <p>{@code requirements} says what each behavior takes injected and in what order: the fakes a
     * row supplies are passed to the injecting constructor in that order, which is the same answer
     * the emitter built that constructor from.
     *
     * <p>{@code values} are the values a row may name beyond this module's own: the ones its imports
     * bring in, each already closed over the module that published it (ADR-0074).
     *
     * <p>{@code answering} says what will apply a behavior for each row. The loader is built here
     * because it is this run's — a row's fixtures are decoded and held to their invariants whether or
     * not there is anything to run them against — and it is handed to {@code answering}, which is what
     * makes an answerer applying this compile's own classes one that can only exist over them.
     *
     * <p>{@code artifact} is taken whole rather than as its classes and what they implement. Both are
     * of one compile and a run has no use for a pairing of two, so the loader is built from one half
     * of it and the other half goes to {@code answering} from the same value.
     *
     * <p>{@code declared} is where the declarations of the module the rows are written for are read
     * from, for holding an answer's own against. Given as something to ask rather than as the
     * declarations, because a run whose answers are all this compile's own has nothing to hold and
     * never asks: there are no two builds there, and reading a module's declarations to compare them
     * with themselves is work for an answer nobody could have brought.
     *
     * <p>And it has to be of the module whose rows these are, which is checked here because here is
     * where both are in hand. Past this point the module the rows belong to is gone: an answerer is
     * given a manifest and a loader, and the manifest is what says which module's implementations it
     * applies. A run handed another module's artifact would look that module's behaviors up by name —
     * and a name it has one of would be applied, so a row would be answered by an implementation of
     * something else rather than failing to find anything.
     *
     * <p>{@code contracts} is what the module's behaviors declare of what they answer, which is what
     * says there is a check to run over a row's values. Read from the answer that owns it rather
     * than from the classes: a check that will not load is a compile that did not emit what it said
     * it would, and reading the loader for this would take that for a behavior with nothing to say.
     *
     * @throws IllegalArgumentException where the artifact is of another module
     */
    public static Observations check(souther.compiler.check.Prepared.ForExamples module,
                                     Symbols symbols,
                                     souther.compiler.observe.FieldTypes fields,
                                     Map<ValueName.Behavior, Sig> sigs,
                                     EvaluationArtifact artifact,
                                     Supplier<PublishedClasses> declared,
                                     Map<String, List<BehaviorRequirement>> requirements,
                                     ClassLoader parent, Map<String, Hir.FnDef> values,
                                     Deadline deadline, EvaluationPolicy policy,
                                     Answering answering,
                                     Map<ValueName.Behavior, Contract> contracts) {
        if (!artifact.implementations().module().equals(module.name())) {
            throw new IllegalArgumentException("the rows are `" + module.name()
                    + "`'s and the artifact is `" + artifact.implementations().module()
                    + "`'s; what applies a behavior would be looked up in the wrong module");
        }
        if (module.examples().isEmpty()) {
            return Observations.NONE;
        }
        ExampleVerifier v = evaluating(module, symbols, fields, sigs, artifact, declared,
                requirements, parent, values, deadline, policy, answering, contracts);
        List<Diagnostic> failures = new ArrayList<>();
        List<RowOutcome> rows = new ArrayList<>();
        List<Incompleteness> incompleteness = new ArrayList<>();
        for (souther.compiler.check.Prepared.Example block : module.examples()) {
            Hir.Example ex = block.read();
            try {
                v.checkExample(ex, failures, rows);
            } catch (LinkageError _) {
                // The generated classes would not link, so nothing could be evaluated here. The
                // case this was written for is the runtime being off the classpath (it is
                // `provided`, like CTFE), where the build-time pass still checks this example — but
                // a `LinkageError` is also what a class that will not verify raises, so what is
                // recorded is that the linking failed and not which of its causes it was. Nothing
                // was observed, and a measure that read the empty result as "no row covers this"
                // would report a gap nobody left.
                incompleteness.add(Incompleteness.at(Incompleteness.Code.LINKAGE_FAILED,
                        Incompleteness.Scope.BEHAVIOR, ex.target(), ex.pos()));
            }
        }
        // A row that could not be decided is read here rather than restated at each place it happens,
        // so what a measure sees and what stopped it can never disagree.
        for (RowOutcome outcome : rows) {
            if (outcome.disposition() == Disposition.INCOMPLETE) {
                incompleteness.add(
                        Incompleteness.ofRow(leftUndecidedBy(outcome.failurePhase()), outcome));
            }
        }
        return new Observations(failures, rows, incompleteness);
    }

    /**
     * The same state {@link #check} runs the rows in, kept so one row can be run at a time.
     *
     * <p>Everything a row is built from is here and none of it is optional: a fixture is decoded
     * through a derived decoder against this module's symbols, its signatures and this compile's
     * classes, so a value cannot be constructed without the whole of it. Making it once and running
     * rows against it is what lets the loop belong to a caller — which is what it has to be when
     * what an implementation answers out of changes between one row and the next.
     */
    public static ExampleVerifier evaluating(souther.compiler.check.Prepared.ForExamples module,
                                      Symbols symbols,
                                      souther.compiler.observe.FieldTypes fields,
                                      Map<ValueName.Behavior, Sig> sigs,
                                      EvaluationArtifact artifact,
                                      Supplier<PublishedClasses> declared,
                                      Map<String, List<BehaviorRequirement>> requirements,
                                      ClassLoader parent, Map<String, Hir.FnDef> values,
                                      Deadline deadline, EvaluationPolicy policy,
                                      Answering answering,
                                      Map<ValueName.Behavior, Contract> contracts) {
        MemoryClassLoader loader = new MemoryClassLoader(artifact.classes(), parent);
        return new ExampleVerifier(module, symbols, fields, sigs, requirements, loader, values,
                deadline, policy, answering.over(artifact.implementations(), loader), declared,
                contracts, artifact.probes());
    }

    /**
     * One row of {@code behavior}, run now.
     *
     * <p>What it answers is what the run observed and what was said about it. The observation is
     * what a machine decides from; the diagnostics say which value differed and where, which the
     * outcome does not carry — a comparison that failed inside one arm is the same outcome as one
     * that held. What either means for whoever asked is theirs.
     *
     * <p>Read the same way as in a bulk run, by the same call — so a row does not mean one thing when
     * a compile runs it and another when a caller does.
     */
    public RowEvaluation one(String behavior, Hir.ExampleRow row) {
        ExampleTarget target = targetOf(behavior);
        if (target == null) {
            throw new IllegalStateException("`" + behavior + "` has no target to run its rows"
                    + " against, and a row of it should not have been enumerated");
        }
        Sig sig = sigs.get(module.targeted(behavior));
        if (sig == null) {
            throw new IllegalStateException("`" + behavior + "` is evaluable and has no signature");
        }
        List<Diagnostic> said = new ArrayList<>();
        // What is wrong with the answer rather than with the row, said here because here is the
        // whole of what this caller gets. A bulk run says it once for the behavior and every row of
        // it is in one report; a row handed over on its own is the only place its reader looks, so
        // one stopping at ANSWERER_ESTABLISHMENT would otherwise carry the phase and nothing that
        // says why.
        if (target.handing() instanceof Handing.NotEstablished(Agreement why)) {
            said.add(cannotBeHeldTo(row.pos(), target.name(), why));
        }
        List<RowOutcome> outcomes = new ArrayList<>();
        checkRow(target, sig, outCases(sig.outputType()), row, said, outcomes);
        if (outcomes.size() != 1) {
            throw new IllegalStateException("a row was run and " + outcomes.size()
                    + " outcomes were recorded");
        }
        return new RowEvaluation(outcomes.get(0), said);
    }

    /**
     * The explicit entries of the block standing in for {@code behavior}, read as values, where one
     * block does.
     *
     * <p>Three kinds of written thing are not entries, each for a reason ADR-0093 already gives, and
     * two of them are settled before this looks. The {@code _} row states no input and is the table's
     * fallback rather than one of its explicit rows. An explicit row shadowed by an earlier one
     * stating the same arguments is never what dispatch picks, and the compiler refuses it (E1926),
     * so what {@code Standins.explicit} holds is rows the fake can answer with.
     *
     * <p>The third is a whole table: where more than one block names the behavior, none of them
     * stands in for it (E1933) and there is nothing here to enumerate. Running the entries of one
     * would report disagreements about values no fake would ever answer with — the mistake ADR-0093
     * was written to avoid, one level up from the row it was written about. Which block stands in is
     * {@link souther.compiler.check.FakeTables#unique}'s to say, and is the same answer the reading
     * that produces E1919 is over. Whether that block can be stood in with is decided below, by
     * building it.
     *
     * <p>A {@code with dep = value} is not here at all. It states no input — what reaches the
     * dependency is whatever the parent behavior computes — and is a fixture bound to the run of one
     * row rather than a statement about the dependency.
     */
    List<StandinEntry> standinEntries(BoundExamples of, String behavior) {
        Sig sig = sigs.get(module.targeted(behavior));
        if (sig == null) {
            return List.of();
        }
        // The block that answers, where one does. A behavior more than one block names has none,
        // and the entries a caller would enumerate here are entries of a table nothing stands in
        // with.
        souther.compiler.check.FakeTables.Occurrence.Resolved answering =
                module.fakes().unique().get(module.targeted(behavior));
        if (answering == null) {
            return List.of();
        }
        Hir.Fake first = answering.read();
        FixtureReader fixtures = newFixtureReader();
        ExampleStatements.BuiltTable built = ExampleStatements.standins(fixtures, first, sig.ins(),
                sig.out(), new ArrayList<>());
        if (built == null) {
            return List.of();   // a table with a row that will not build stands in with nothing
        }
        List<StandinEntry> entries = new ArrayList<>();
        List<StatedRow> rows = recordedRowsOf(of, behavior, fixtures, sig);
        for (ExampleStatements.Standin.Explicit entry : built.standins().explicit()) {
            // What the row states, read the one way a table's row is read. What a person is shown
            // is beside it and is this reader's own: the two are not one string, which is the
            // whole reason a machine-readable half is carried at all.
            RowStatements.StandInRead.EntryRead carried =
                    ExampleStatements.carried(fixtures, entry);
            List<String> shownInputs = new ArrayList<>();
            for (int i = 0; i < entry.arguments().length; i++) {
                shownInputs.add(fixtures.shown(fixtures.structured(entry.arguments()[i]),
                        sig.ins().get(i).type()));
            }
            List<ObservedValue> inputs = new ArrayList<>();
            for (RowStatements.StandInRead.Written argument : carried.arguments()) {
                inputs.add(argument.value());
            }
            List<RecordedRow> alsoBy = new ArrayList<>();
            for (StatedRow stated : rows) {
                if (statesTheSame(stated.arguments(), entry.arguments())) {
                    alsoBy.add(stated.handle());
                }
            }
            entries.add(new StandinEntry(of, behavior, first.pos(), entry,
                    inputs, carried.answer().value(), shownInputs,
                    fixtures.shown(fixtures.structured(entry.answer().value()), sig.outputType()),
                    alsoBy));
        }
        return entries;
    }

    /** A recorded row and the arguments it states, read without running it. */
    private record StatedRow(RecordedRow handle, Object[] arguments) {}

    /**
     * The behavior's recorded rows whose inputs could be read, each with what it states.
     *
     * <p>A row that will not build states nothing, and it is reported as the input error it is where
     * the row is evaluated; read otherwise here than there it would be associated with an entry on an
     * assertion the model itself refuses.
     */
    private List<StatedRow> recordedRowsOf(BoundExamples of, String behavior,
                                           FixtureReader fixtures, Sig sig) {
        List<StatedRow> found = new ArrayList<>();
        for (souther.compiler.check.Prepared.Example block : module.examples()) {
            Hir.Example written = block.read();
            if (!written.target().equals(behavior)) {
                continue;
            }
            for (Hir.ExampleRow row : written.rows()) {
                if (row.inputs().size() != sig.ins().size()) {
                    continue;
                }
                Object[] args = new Object[sig.ins().size()];
                boolean read = true;
                for (int i = 0; i < args.length && read; i++) {
                    try {
                        args[i] = fixtures.built(row.inputs().get(i), sig.ins().get(i));
                    } catch (FixtureException _) {
                        read = false;
                    }
                }
                if (read) {
                    found.add(new StatedRow(new RecordedRow(of, behavior, row), args));
                }
            }
        }
        return found;
    }

    /** The equality fake dispatch already keys on, asked of two statements of one input. */
    private static boolean statesTheSame(Object[] a, Object[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!souther.runtime.Values.equal(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * What the answerer answers for {@code entry}'s input, held to what the entry states.
     *
     * <p>{@code observe} and not a second {@code evaluate}: one adjudicates an obligation and this
     * relates two answers, and spelling them apart keeps a consumer from sliding a fake entry into
     * the row default by accident.
     *
     * <p>Dispatch is not re-asked whether the entry is the row that answers its own inputs. #716
     * holds exactly that, and re-checking it here would make this a second checker of an invariant
     * that already has one.
     */
    StandinObservation observe(String behavior, StandinEntry entry) {
        // Through the run's deadline, as a row is — which is what decides where the work runs and
        // what bounds it, and is the same answer for both. An observation reaching the applied code
        // by a different route would be the binding meaning two things again.
        switch (deadline.given(new Deadline.Work.Table(behavior, entry.at()),
                () -> observing(behavior, entry))) {
            case Deadline.Outcome.Finished(StandinObservation observed) -> {
                return observed;
            }
            case Deadline.Outcome.Overran(Runnable abandon) -> {
                abandon.run();
                return new StandinObservation.Unobserved(
                        new StandinObservation.Reason.TheObservationRanOut(
                                "the observation did not answer within "
                                        + within(deadline) + "ms of this compile's own time"));
            }
            case Deadline.Outcome.Threw(Throwable cause) -> {
                return new StandinObservation.Unobserved(whatTheWorkerThrew(cause));
            }
        }
    }

    /**
     * Whether {@code behavior} states anything of what it answers.
     *
     * <p>Declaration metadata, so nothing is applied to answer it. It is the same fact
     * {@link ContractObservation.NothingStated} reports after a row has run, asked before one is.
     */
    boolean states(String behavior) {
        return ensures.states(module.targeted(behavior));
    }

    /**
     * What the bound implementation answered for {@code row}'s inputs, held to what {@code behavior}
     * declares of what it answers and to nothing the row records.
     *
     * <p>Through the run's deadline, as a row and a stand-in's entry are, and for that reason: the
     * binding decides where applied code runs and what bounds it, and a third route to it would be
     * the binding meaning three things.
     */
    ContractObservation contractOnly(String behavior, Hir.ExampleRow row) {
        switch (deadline.given(new Deadline.Work.WholeRow(behavior, row.pos(), row.identity()),
                () -> checkingContract(behavior, row))) {
            case Deadline.Outcome.Finished(ContractObservation observed) -> {
                return observed;
            }
            case Deadline.Outcome.Overran(Runnable abandon) -> {
                abandon.run();
                return new ContractObservation.Unobserved(
                        new StandinObservation.Reason.TheObservationRanOut(
                                "the observation did not answer within "
                                        + within(deadline) + "ms of this compile's own time"));
            }
            case Deadline.Outcome.Threw(Throwable cause) -> {
                return new ContractObservation.Unobserved(whatTheWorkerThrew(cause));
            }
        }
    }

    private ContractObservation checkingContract(String behavior, Hir.ExampleRow row) {
        Sig sig = sigs.get(module.targeted(behavior));
        ExampleTarget target = targetOf(behavior);
        if (sig == null || target == null) {
            return new ContractObservation.Unobserved(
                    new StandinObservation.Reason.TheEntryWasNotRead(
                            "`" + behavior + "` has nothing to apply its inputs to"));
        }
        Answerer.Answer.Something applies;
        switch (target.handing()) {
            case Handing.NothingApplies _ -> {
                return new ContractObservation.Unobserved(
                        new StandinObservation.Reason.TheImplementationWasNotReached(
                                "nothing this run was given applies `" + behavior + "`"));
            }
            case Handing.NotEstablished(Agreement why) -> {
                return new ContractObservation.Unobserved(
                        new StandinObservation.Reason.TheImplementationIsOfAnotherBuild(
                                String.valueOf(why)));
            }
            case Handing.MayApply(Answerer.Answer.Something something) -> applies = something;
        }
        // After the binding is known good and before anything is applied. A behavior that states
        // nothing holds an implementation to nothing whatever it answers, so applying it would spend
        // a call to learn what the declaration already said — and asking this first would answer
        // "the model states nothing" for a binding nothing may be handed to, sending its author to
        // write a clause that would still not run.
        if (!ensures.states(module.targeted(behavior))) {
            return new ContractObservation.NothingStated(behavior);
        }
        FixtureReader fixtures = newFixtureReader();
        Object[] args;
        try {
            args = new Object[sig.ins().size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = fixtures.built(row.inputs().get(i), sig.ins().get(i));
            }
        } catch (FixtureException fe) {
            return new ContractObservation.Unobserved(
                    new StandinObservation.Reason.TheEntryWasNotRead(
                            String.valueOf(fe.getMessage())));
        }
        // What the row records is not read at all — not built, not compared. That is the whole of
        // what makes this a different oracle from `evaluate`'s, and reading it here to report it
        // beside a broken clause would put the recorded answer back into a face that does not have
        // one.
        Object answered;
        try {
            answered = applies.applying(List.of())
                    .to(handed(fixtures, target, args, sig.ins()));
        } catch (InvocationFailure f) {
            return new ContractObservation.Unobserved(
                    new StandinObservation.Reason.TheInvocationAborted(
                            String.valueOf(f.getCause())));
        } catch (ImplementationNotReached | StandinNotBuilt e) {
            return new ContractObservation.Unobserved(
                    new StandinObservation.Reason.TheImplementationWasNotReached(
                            String.valueOf(e.getMessage())));
        } catch (FixtureException fe) {
            return new ContractObservation.Unobserved(
                    new StandinObservation.Reason.AValueCouldNotCross(
                            String.valueOf(fe.getMessage())));
        }
        answered = projected(answered, sig.outputType());
        // Into this compile's classes before the check, for the reason
        // `keepsWhatIsDeclaredOfWhatItAnswered` does it: the emitted check guards each rule with an
        // `instanceof` against the class this compile emitted, so an answer of another loader's
        // classes matches no guard and every rule is skipped — the check would run and say nothing.
        Object here;
        try {
            here = inTheseClasses(fixtures, fixtures.typeOf(answered), sig, answered);
        } catch (FixtureException | ImplementationNotReached e) {
            return new ContractObservation.Unobserved(
                    new StandinObservation.Reason.AValueCouldNotCross(
                            String.valueOf(e.getMessage())));
        }
        String why = ensures.notHeld(module.targeted(behavior), args, here);
        // Written here and not in the arm. What writes a value the way a fixture writes one needs the
        // module's declarations to tell a newtype from what it wraps, and an arm holding neither
        // could only fall back on the value's own `toString` — a shape no report should be written
        // from, and one that would differ from every other value this compiler shows a reader.
        if (why == null) {
            return new ContractObservation.NoClauseWasBroken();
        }
        // Read off what the check was applied to and not off what came back. The two are one value
        // — the second is the first through this module's own decoder — but only one of them is what
        // the clause saw, and a report of a verdict shows what the verdict was about.
        ObservedValue observed = fixtures.observed(here);
        // The declared output is the position, which is what tells a set from a list where the
        // answer is one. A union falls through it to the value itself, there being nothing at a
        // union for a renderer to read.
        return new ContractObservation.Broken(why, observed,
                fixtures.shown(observed, sig.outputType()));
    }

    /** The wait, as every report of this compiler's writes one ({@link WaitShown}). */
    private static String within(Deadline deadline) {
        return WaitShown.of(deadline.timeout());
    }

    /**
     * What an observation makes of what its worker threw.
     *
     * <p>The same cut a row makes, and for the reason a row makes it. {@code Threw} says the work
     * ended with a throwable and nothing more, so taking all of them for "it ran out" would turn a
     * defect in this machinery into an ordinary answer about a stand-in — a reader would be told the
     * two could not be compared where in fact this code failed. What the implementation itself threw
     * never arrives here: it is {@link InvocationFailure} and was already answered.
     *
     * <p>Answers the reason and not an observation, so both faces wrap it in their own. Answering one
     * face's type and casting it in the other is a narrowing nothing checks, and it holds only while
     * this has the one arm it has today.
     */
    private static StandinObservation.Reason whatTheWorkerThrew(Throwable cause) {
        FailurePhase overspent = overspending(cause);
        if (overspent != null) {
            return new StandinObservation.Reason.TheObservationRanOut(
                    "the observation went through more than " + overspent + " allows");
        }
        if (cause instanceof StackExhaustedException || cause instanceof StackOverflowError) {
            return new StandinObservation.Reason.TheObservationRanOut(
                    "the observation ran out of stack");
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            // Said of an observation and not of a stand-in's entry: a contract check comes through
            // here as well, and naming one of the two callers would report the other's cancellation
            // as something it is not.
            throw new java.util.concurrent.CancellationException(
                    "interrupted while making an observation");
        }
        if (cause instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException(cause);
    }

    private StandinObservation observing(String behavior, StandinEntry entry) {
        Sig sig = sigs.get(module.targeted(behavior));
        ExampleTarget target = targetOf(behavior);
        if (sig == null || target == null) {
            return new StandinObservation.Unobserved(
                    new StandinObservation.Reason.TheEntryWasNotRead(
                            "`" + behavior + "` has nothing to apply its inputs to"));
        }
        // The same gate a row passes, asked the same way. An implementation that a row may not be
        // handed to may not be handed an entry's values either: it reads them by the declarations
        // some other build wrote, and what came back would be the two builds disagreeing rather than
        // the stand-in and the implementation.
        Answerer.Answer.Something applies;
        switch (target.handing()) {
            case Handing.NothingApplies _ -> {
                return new StandinObservation.Unobserved(
                        new StandinObservation.Reason.TheImplementationWasNotReached(
                                "nothing this run was given applies `" + behavior + "`"));
            }
            case Handing.NotEstablished(Agreement why) -> {
                return new StandinObservation.Unobserved(
                        new StandinObservation.Reason.TheImplementationIsOfAnotherBuild(
                                String.valueOf(why)));
            }
            case Handing.MayApply(Answerer.Answer.Something something) -> applies = something;
        }
        FixtureReader fixtures = newFixtureReader();
        Object[] args;
        // A table entry states the whole value, always: what a fake answers with is what it was
        // written with, and there is no grain below that for it to have stated instead.
        Expectation.Asserts stated;
        try {
            args = new Object[sig.ins().size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = fixtures.built(entry.written().names().get(i), sig.ins().get(i));
            }
            stated = new Expectation.TheValue(fixtures.assertedExpected(
                    entry.written().row().output(), sig.out()).asserted());
        } catch (FixtureException fe) {
            return new StandinObservation.Unobserved(
                    new StandinObservation.Reason.TheEntryWasNotRead(
                            String.valueOf(fe.getMessage())));
        }
        Object answered;
        try {
            answered = applies.applying(List.of())
                    .to(handed(fixtures, target, args, sig.ins()));
        } catch (InvocationFailure f) {
            return new StandinObservation.Unobserved(
                    new StandinObservation.Reason.TheInvocationAborted(
                            String.valueOf(f.getCause())));
        } catch (ImplementationNotReached | StandinNotBuilt e) {
            return new StandinObservation.Unobserved(
                    new StandinObservation.Reason.TheImplementationWasNotReached(
                            String.valueOf(e.getMessage())));
        } catch (FixtureException fe) {
            return new StandinObservation.Unobserved(
                    new StandinObservation.Reason.AValueCouldNotCross(
                            String.valueOf(fe.getMessage())));
        }
        answered = projected(answered, sig.outputType());
        return fixtures.holds(stated, answered, sig.outputType())
                instanceof Verdict.NotHeld(Mismatch differs)
                ? new StandinObservation.OtherThanStated(entry.stated(),
                        fixtures.observed(answered), fixtures.shown(differs.path()))
                : new StandinObservation.AsStated();
    }

    /**
     * What a measure loses when a row ends undecided, read off what stopped the row.
     *
     * <p>The two vocabularies are kept apart here and joined nowhere else. What stopped a row is a
     * fact about the row and is written where the row stopped; what a measure can no longer answer is
     * a fact about the measure, and this is the one place the first is read for the second. A row
     * carrying the measure's word would be the row answering a question that is not about it.
     *
     * <p>A switch with no default, so a phase added later is a compile error here. What it asks for
     * is what a reader of a measure is to be told when a row stops that way, which is not something
     * to be defaulted into whatever the nearest existing answer happens to be.
     *
     * <p><b>The three limits are their own word.</b> A figure this compiler compared the row against
     * and a row the evaluation had no answer for are the same loss and not the same news: the first
     * is met again or not depending on what the run allows, and the second is met again whatever it
     * allows. Said alike, a person was told a row did not come back and could not tell which of the
     * two they were holding. The stack running out is not among them — nothing here compared
     * anything against it — so it stays with the rest.
     *
     * @throws IllegalStateException for {@link FailurePhase#NONE}, which is a row that ended
     *         undecided with nothing having gone wrong. This is reached from a row of that
     *         disposition alone, so the state is one nothing produces; admitted into an arm beside
     *         the real causes, it was a word for it all the same
     */
    private static Incompleteness.Code leftUndecidedBy(FailurePhase phase) {
        return switch (phase) {
            case ANSWERER_ESTABLISHMENT -> Incompleteness.Code.ANSWERER_NOT_ESTABLISHED;
            case STEP_LIMIT, DEPTH_LIMIT, TIMEOUT ->
                    Incompleteness.Code.ROW_EVALUATION_LIMIT_REACHED;
            case INPUT_FIXTURE, EXPECTED_FIXTURE, ENSURES, FAKE_RESOLUTION, INVOCATION, COMPARISON,
                 STACK_EXHAUSTED, VALUE_CROSSING -> Incompleteness.Code.ROW_UNDECIDED;
            case NONE -> throw new IllegalStateException(
                    "a row that ended undecided says what stopped it, and nothing went wrong here");
        };
    }

    private final souther.compiler.check.Prepared.ForExamples module;
    private final Symbols symbols;
    /** What a value of a declaration is made of, as the check settled it. */
    private final souther.compiler.observe.FieldTypes fields;
    /** The shape of every behavior a row here may name: this module's own, and the ones a stand-in
     * reaches in another module. Keyed by the declaration, so a borrowed dependency and a namesake
     * declared here are two entries. */
    private final Map<ValueName.Behavior, Sig> sigs;
    /** What each behavior of this module takes injected, in the order its constructor takes it. */
    private final Map<String, List<BehaviorRequirement>> requirements;
    private final MemoryClassLoader loader;
    /** Whether the classes a row is run against record where it goes, and in whose numbers. What a
     * run leaves behind is those numbers, so this is what says what they address. */
    private final ProbeImage probes;
    /** The values a row may name: this module's own, and the ones its imports bring in. */
    private final Map<String, Hir.FnDef> values;
    /** What one row gets to be evaluated within ({@link #checkRow}). Carried rather than looked up,
     * so two compiles in one JVM need not agree on it. Reading a written statement is held to a
     * deadline of its own, which is {@link ExampleStatements}'. */
    private final Deadline deadline;
    /** What one row's evaluation is allowed: the steps and the depth it is decided by, and the wait
     * the machinery running it is given. */
    private final EvaluationPolicy policy;
    /** What applies a behavior for a row. One for the run, so no row's meaning depends on which
     * answerer it had. */
    private final Answerer answerer;
    /** Where the declarations the rows are written for are read from, asked for only if something
     * has to be held against them. A run of this compile's own answers never calls it. */
    private final Supplier<PublishedClasses> declared;
    /**
     * What each set of declarations was held to say for each behavior, so one answer is not read
     * twice.
     *
     * <p>By identity of what carries them, because that is what a run has of an answer's
     * declarations — and only ever as a memo. Nothing here decides that two answers are of one build
     * because they arrived as one object: emptying this changes what is paid and not what is
     * answered.
     *
     * <p>By behavior as well as by classes, because that is what was asked. One jar answers several
     * behaviors, and what each of them reaches is its own — so a memo kept by the classes alone
     * would answer for the second behavior with what was worked out about the first.
     *
     * <p>What holding an answer's declarations against this module said, per answer and behavior.
     *
     * <p>Concurrent because a caller owns the loop over the rows and may run them alongside each
     * other — this face says parallelism is theirs, and a cache that came apart while two of them
     * read it would be a failure that does not reproduce. Keyed by the declarations' identity, which
     * is what {@link PublishedClasses} has: two readings of one set of classes are one answer, and
     * two sets that happen to read alike are not.
     */
    private final Map<PublishedClasses, Map<String, Agreement>> agreements =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** The behaviors an answer could not be established for have already been reported about. A
     * behavior's rows may be written in more than one block, and what is reported is about neither
     * the block nor the row. It is per source, which is what a verifier is: a diagnostic is said
     * where it can be quoted, and a reader of the other source would otherwise be shown rows that
     * stopped with nothing saying why. */
    private final Set<String> said = new LinkedHashSet<>();
    /** What holds a row's values to what the behavior declares of what it answers. */
    private final EnsuresChecks ensures;

    private ExampleVerifier(souther.compiler.check.Prepared.ForExamples module,
                            Symbols symbols,
                            souther.compiler.observe.FieldTypes fields,
                            Map<ValueName.Behavior, Sig> sigs,
                            Map<String, List<BehaviorRequirement>> requirements,
                            MemoryClassLoader loader, Map<String, Hir.FnDef> values,
                            Deadline deadline, EvaluationPolicy policy, Answerer answerer,
                            Supplier<PublishedClasses> declared,
                            Map<ValueName.Behavior, Contract> contracts,
                            ProbeImage probes) {
        this.probes = probes;
        this.ensures = new EnsuresChecks(loader, contracts, sigs.keySet());
        this.module = module;
        this.symbols = symbols;
        this.fields = fields;
        this.sigs = sigs;
        this.requirements = requirements;
        this.loader = loader;
        this.values = values;
        this.deadline = deadline;
        this.policy = policy;
        this.answerer = answerer;
        this.declared = declared;
    }

    /**
     * A reader for one row, held for as long as evaluating that row lasts.
     *
     * <p>Never shared between two of them. What a reading builds up — the bindings in force, the
     * values being expanded, the helper it is inside — is that row's, and a worker that ran out of
     * its budget goes on writing to it after the answer was given up on. So the isolation is the
     * reader, and it is the reader alone: everything else this class holds is read-only once a row
     * starts.
     */
    private FixtureReader newFixtureReader() {
        return new FixtureReader(module, symbols, fields, values, loader);
    }

    // --- one example (a target and its rows) --------------------------------------------------

    private void checkExample(Hir.Example ex, List<Diagnostic> out, List<RowOutcome> rows) {
        ExampleTarget target = targetOf(ex.target());
        if (target == null) {
            out.add(notRunnable(ex));
            return;
        }
        // Said once for the behavior in this source: not once for each of its rows, and not once for
        // each block they are written in. One answer and one module disagreeing is one fact, and a
        // behavior's rows may be written in as many blocks as they belong in.
        if (target.agreement() != null && !(target.agreement() instanceof Agreement.Agree)
                && said.add(target.name())) {
            out.add(cannotBeHeldTo(ex.pos(), target.name(), target.agreement()));
        }
        Sig sig = sigs.get(module.targeted(target.name()));
        if (sig == null) {
            throw new IllegalStateException("`" + target.name() + "` is evaluable but has no signature");
        }
        Set<TypeSymbol> outCases = outCases(sig.outputType());
        for (Hir.ExampleRow row : ex.rows()) {
            checkRow(target, sig, outCases, row, out, rows);
        }
    }

    /**
     * What a row runs: the behavior's name, what it takes injected in the order its constructor takes
     * it, and what this run has to apply it.
     *
     * <p>Which kind of behavior it is does not survive to here. A row applies the class the module
     * emitted, and that class is reached the same way whichever way the behavior was written — the
     * name says which, and the requirements say what to hand it.
     *
     * @param agreement what holding the answer's declarations against this module's said, or null
     *                  where there was nothing to hold — the answer is this compile's own, or
     *                  nothing answers the behavior at all
     */
    private record ExampleTarget(String name, List<BehaviorRequirement> requirements,
                                 Answerer.Answer answer, Agreement agreement) {

        /**
         * Whether values may be handed to what answers this behavior, and if so to what.
         *
         * <p>One answer rather than two conditions each caller keeps. Whether anything applies the
         * behavior and whether what applies it was built against this module are asked together
         * because a caller that has values to hand over needs both answered before it hands them,
         * and asked separately one of them goes unasked — {@code observe} asked neither and applied
         * an implementation a row would have been kept away from.
         *
         * <p>A sealed type, so a caller that does not consider an arm is a compile error rather than
         * a path that quietly hands the values over anyway.
         */
        Handing handing() {
            if (!(answer instanceof Answerer.Answer.Something applies)) {
                return new Handing.NothingApplies();
            }
            return agreement != null && !(agreement instanceof Agreement.Agree)
                    ? new Handing.NotEstablished(agreement)
                    : new Handing.MayApply(applies);
        }
    }

    /** What may be handed a behavior's values, or why nothing may be. */
    private sealed interface Handing {

        /** It may be applied, and this is what applies it. */
        record MayApply(Answerer.Answer.Something applies) implements Handing {}

        /** Nothing this run was given applies the behavior. */
        record NothingApplies() implements Handing {}

        /** What would apply it could not be established as being of the module being evaluated, so
         *  no value of this module's may be handed to it. */
        record NotEstablished(Agreement why) implements Handing {}
    }

    /**
     * The behavior a row is about, and what this run has to apply it with.
     *
     * <p>Asked of the answerer rather than read off how the behavior is written. The two say the same
     * thing while a compile's own classes are the only thing that applies anything, and they come
     * apart the moment something else can: what a row can be held to is decided by what this run was
     * given, so it is what this run was given that is asked.
     *
     * <p>A row nothing applies is <em>recorded</em> rather than evaluated (spec
     * {@code example-pending}). That is not a lesser state: a model being migrated onto has nothing
     * applying anything at the start, and the rows harvested from the system it replaces are what says
     * what each behavior owes. They are checked as far as they can be — every fixture is built, so a
     * value that breaks an invariant is found the day it is written — and evaluation begins by itself
     * the moment something applies the behavior.
     *
     * <p>Null when this module declares no behavior of that name; what to say about that is
     * {@link #notRunnable}'s.
     *
     * <p>A composition is run by applying the class its module emits, the same class normal execution
     * applies. Its stages with a body are constructed by it and handed the fields they need, so what
     * a row has to supply are the injected behaviors the stages reach — which is what its requirement
     * list holds, in the order its constructor takes them.
     */
    private ExampleTarget targetOf(String name) {
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (!b.name().equals(name)) {
                continue;
            }
            Answerer.Answer answer = answerer.of(name);
            return new ExampleTarget(name, requirements.getOrDefault(name, List.of()), answer,
                    heldTo(name, answer));
        }
        return null;
    }

    /**
     * What holding the answer's declarations against this module's says, or null where there is
     * nothing to hold.
     *
     * <p>Asked here, which is before a row states anything: whether a run may hand this answer a row
     * does not depend on the row, and a row that may not be handed over has to be able to stop having
     * been held to everything a row can be held to without being run.
     *
     * <p>The two ways there is nothing to hold are not the same and are both null. An answer of this
     * compile's own is of the module being evaluated because it is of this compile of it — one build,
     * so there is no second set of declarations. A behavior nothing applies has no declarations to
     * bring at all, and its rows are recorded rather than run whatever any build says.
     */
    private Agreement heldTo(String behavior, Answerer.Answer answer) {
        // A switch, so an answer this was never shown is a compile error here rather than one of the
        // two ways silently taken for it.
        return switch (answer) {
            case Answerer.Answer.Nothing _ -> null;
            case Answerer.Answer.Something something -> switch (something.origin()) {
                // An answerer is written outside this package, so what it hands back is a thing to
                // be refused rather than a state of this compiler. Saying nothing is not saying
                // "this compile's own": read that way, an implementation would be out of the
                // question by returning null, which is what the abstract accessor was for.
                case null -> new Agreement.NoOriginStated(module.name());
                case TheCompilesOwn _ -> null;
                case Origin.Published published -> agreements
                        .computeIfAbsent(published.classes(),
                                _ -> new java.util.concurrent.ConcurrentHashMap<>())
                        .computeIfAbsent(behavior, named -> DeclarationAgreement.of(module.name(),
                                named, declared.get(), published.classes(),
                                symbols.library()));
            };
        };
    }

    /**
     * What a behavior whose answer could not be established as this module's reports.
     *
     * <p>Two things to say and not one. Declarations that differ are established: both were read and
     * one of them says something else, and what to do about it is to build again. Declarations that
     * could not be read establish nothing about either — the answer may well be of exactly this
     * module — and reporting that as a stale build would be reporting a difference on evidence
     * nobody has.
     */
    private Diagnostic cannotBeHeldTo(SourcePos at, String target, Agreement said) {
        return switch (said) {
            case Agreement.Agree _ ->
                    throw new IllegalStateException("an agreement that holds reports nothing");
            case Agreement.Disagree differs -> Diagnostic.at(at)
                    .say(new ExampleMessage.TheAnswerIsOfAnotherBuild(target, differs.module(),
                            differs.declaration()))
                    .hint(new ExampleMessage.BuildWhatAnswersItAgainstThisRevision(differs.module()))
                    .build();
            case Agreement.NoOriginStated stated -> Diagnostic.at(at)
                    .say(new ExampleMessage.WhetherTheAnswerIsOfThisModuleCannotBeTold(target,
                            stated.module()))
                    .hint(new ExampleMessage.ItDidNotSayWhichBuildItReadsBy(stated.module()))
                    .build();
            case Agreement.Unreadable unreadable -> cannotBeTold(at, target, unreadable);
        };
    }

    /**
     * What a row is told when a set of declarations could not be read.
     *
     * <p>Three questions and one answer each: whose declarations could not be read, why they could
     * not, and what there is to do about it. Kept apart because they are answered from different
     * things — the side, the reading's own reason, and the two together — and a reader given only
     * the first two has been told what happened and not what to do.
     */
    private static Diagnostic cannotBeTold(SourcePos at, String target,
                                           Agreement.Unreadable unreadable) {
        Diagnostic.Builder said = Diagnostic.at(at)
                .say(new ExampleMessage.WhetherTheAnswerIsOfThisModuleCannotBeTold(
                        target, unreadable.module()));
        return whatToDoAbout(
                whyItCannotBeTold(whoseDeclarations(said, unreadable), unreadable.reading()),
                unreadable)
                .build();
    }

    /**
     * Whose declarations could not be read.
     *
     * <p>Which side it was decides what the reader is to do about it, so this says whose they are.
     * Naming the answer's build for what this compile could not read would send someone to rebuild
     * the one thing that is not in question.
     */
    private static Diagnostic.Builder whoseDeclarations(Diagnostic.Builder said,
                                                       Agreement.Unreadable unreadable) {
        return switch (unreadable.side()) {
            case THE_ANSWER -> switch (unreadable.reading()) {
                case Readback.NotReady.SaysNothing<?> _ -> said.hint(
                        new ExampleMessage.ItsClassesCarryNoDeclarations(unreadable.module()));
                case Readback.NotReady.Unreadable<?> _ -> said.hint(
                        new ExampleMessage.WhatItPublishedCannotBeReadHere(unreadable.module()));
            };
            case THE_MODULE_BEING_EVALUATED -> said.hint(
                    new ExampleMessage.ThisCompileCannotReadItsOwnDeclarationsOf(
                            unreadable.module()));
        };
    }

    /**
     * What there is to do about it, which is not the same for every way a reading stops.
     *
     * <p>Two things and one question between them: whether the classes are short of a module they
     * could carry, or whether what they do carry is wrong. Short of one, the artifact is fine and
     * there is a module to supply — and which module is the whole of what the reader needs. Wrong,
     * there is nothing to supply and the artifact has to be built again. Said as one sentence for
     * both, a run whose answer was built without a dependency is told to rebuild something that was
     * never at fault, with the module that is missing named nowhere.
     *
     * <p>Only a module can be supplied, which is what puts the line between the two where it is. An
     * artifact naming a declaration whose class is not there is short of something too, and it is a
     * class of its own module — nobody adds one, so what there is to do is build the artifact that
     * left it out.
     *
     * <p>Decided here rather than beside the reason, because it is this reader's. What a failure is
     * is the same fact wherever it is read ({@link ReadbackReasons}); what to do about it is not. A
     * compilation reading its path is told which module is missing by the walk over the path
     * itself, and there is no such walk behind an answer's classes.
     *
     * <p>A switch over every failure there is, and over every way a line can fail, with nothing to
     * fall through to. Either is a way a reading can stop, and one that reached here with nothing to
     * say would be a reader told what happened and left to guess what to do.
     */
    private static Diagnostic.Builder whatToDoAbout(Diagnostic.Builder said,
                                                    Agreement.Unreadable unreadable) {
        Agreement.Side side = unreadable.side();
        return switch (unreadable.reading()) {
            // Nothing at all under the name: what is short is the module itself.
            case Readback.NotReady.SaysNothing<?>(String module) -> supply(said, module, side);
            case Readback.NotReady.Unreadable<?>(String module, Readback.Failure why) ->
                    switch (why) {
                        case Readback.Failure.InvalidExposure(
                                Readback.Exposure line, List<Readback.Exposure> _) ->
                                switch (line) {
                                    case Readback.Exposure.NoSuchModule(String needed) ->
                                            supply(said, needed, side);
                                    case Readback.Exposure.NoSuchLibraryFunction _,
                                         Readback.Exposure.NotExposed _,
                                         Readback.Exposure.NoSuchName _,
                                         Readback.Exposure.AliasTaken _,
                                         Readback.Exposure.BroughtTwice _,
                                         Readback.Exposure.CollidesWithADeclaration _ ->
                                            buildItAgain(said, module, side);
                                };
                        case Readback.Failure.Incompatible _,
                             Readback.Failure.DeclarationMissing _,
                             Readback.Failure.AnotherModule _,
                             Readback.Failure.UnreadableMetadata _,
                             Readback.Failure.InvalidPublishedSyntax _,
                             Readback.Failure.UnresolvedPublishedNames _,
                             Readback.Failure.InvalidDeclarations _ ->
                                buildItAgain(said, module, side);
                    };
        };
    }

    /** The classes are short of {@code module}: it is put where they are read from. */
    private static Diagnostic.Builder supply(Diagnostic.Builder said, String module,
                                             Agreement.Side side) {
        return switch (side) {
            case THE_ANSWER ->
                    said.hint(new ExampleMessage.BuildWhatAnswersItAgainstAPathThatCarries(module));
            case THE_MODULE_BEING_EVALUATED ->
                    said.hint(new ModuleMessage.AddItToThisProjectsDependencies(module));
        };
    }

    /** What {@code module}'s classes carry is wrong: the artifact is built again. */
    private static Diagnostic.Builder buildItAgain(Diagnostic.Builder said, String module,
                                                   Agreement.Side side) {
        return switch (side) {
            case THE_ANSWER ->
                    said.hint(new ExampleMessage.BuildWhatAnswersItAgainstThisRevision(module));
            case THE_MODULE_BEING_EVALUATED ->
                    said.hint(new ModuleMessage.RebuildItOrCompileAgainstWhatBuiltIt(module));
        };
    }

    /**
     * The reading's own reason, under what the reader was told about whose declarations they are.
     *
     * <p>Said here and not left out. What a run has to go on when an answer cannot be held to this
     * module is why its declarations could not be read — a dependency the classes leave out and an
     * artifact from another compiler are two things to do something about, and a reader told only
     * that what was published cannot be read here has been given neither. Said of whichever side
     * could not be read, because the reason is a fact about that side's classes either way; which
     * side it is decides what the reader is to do about it and is said above this. Nothing is added
     * for a set of classes that carry nothing: what would be said is that they carry nothing, which
     * the hint above it already says.
     */
    private static Diagnostic.Builder whyItCannotBeTold(Diagnostic.Builder said,
                                                        Readback.NotReady<?> reading) {
        return reading instanceof Readback.NotReady.Unreadable<?>(String _, Readback.Failure why)
                ? ReadbackReasons.said(said, why)
                : said;
    }

    /** The injection target named {@code name} in this module — a valid target for a fake; null if
     * not found or not one. How a behavior is written is the module's to say, so the rule is
     * read from there rather than spelled again here. */
    private Hir.SpecBehavior injectedSpec(String name) {
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (b instanceof Hir.SpecBehavior spec && spec.name().equals(name)
                    && module.implementationOf(spec).isInjectionTarget()) {
                return spec;
            }
        }
        return null;
    }

    /**
     * The diagnostic for a target this module declares no behavior for: a name it does not know
     * ({@code E1901}), or a helper {@code let} of that name ({@code E1902}).
     *
     * <p>Only reached when {@link #targetOf} found no behavior, so being injected is not one of the
     * reasons any more: an injected behavior is a behavior, and its rows are recorded. What is left
     * for {@code E1902} is a target that is not a behavior at all.
     */
    private Diagnostic notRunnable(Hir.Example ex) {
        String name = ex.target();
        boolean isHelper = module.fns().stream().anyMatch(f -> f.name().equals(name));
        if (!isHelper) {
            return Diagnostic.at(ex.pos()).say(new ExampleMessage.NoBehaviorOfThatName(name)).build();
        }
        return Diagnostic.at(ex.pos())
                .say(new ExampleMessage.TheTargetCannotBeEvaluated(name))
                .hint(new ExampleMessage.WhatAnExampleRuns(
                        "it is a helper `let`, and this module declares no behavior of that name"))
                .build();
    }

    // --- one row ------------------------------------------------------------------------------

    /**
     * One row's evaluation, and the state it builds up while it runs: its own diagnostics, and the
     * helper it is currently inside.
     *
     * <p>A row that does not finish leaves its worker running — a pure computation reaches no interrupt
     * point, so cancelling it is a request and not a stop. So nothing that worker can still write to is
     * read by the rows after it: the evaluation has its own {@link FixtureReader}, so the state a
     * fixture builds up (which values it is expanding, which helper it is in) belongs to this row, and
     * the diagnostics it produces are its own list, handed to the caller only when it finishes. A late
     * result is dropped rather than landing among another row's.
     *
     * <p>One reader for the whole row. The inputs, the expectation, each {@code with} and every fake
     * table the row resolves are read through this one, so what a row spent is one row's whatever
     * part of it was being read.
     */
    private static final class RowWork implements java.util.concurrent.Callable<List<Diagnostic>> {

        private final ExampleVerifier verifier;
        /** This row's, and only this row's. */
        private final FixtureReader fixtures;
        private final ExampleTarget target;
        private final Sig sig;
        private final Set<TypeSymbol> outCases;
        private final Hir.ExampleRow row;
        private final RowState state = new RowState();

        RowWork(ExampleVerifier of, ExampleTarget target, Sig sig,
                      Set<TypeSymbol> outCases, Hir.ExampleRow row) {
            this.verifier = of;
            this.fixtures = of.newFixtureReader();
            this.target = target;
            this.sig = sig;
            this.outCases = outCases;
            this.row = row;
        }

        /**
         * The row, and the arms it went through on the way.
         *
         * <p>The collecting is started and stopped here because a row is a thread: this worker runs
         * this row's body and nothing else, so what the probe saw on it is what this row reached. A row
         * that does not finish never gets here to read it, and the set goes with the thread's state —
         * which is what stops the next row on a reused worker from starting where this one left off.
         */
        @Override
        public List<Diagnostic> call() {
            List<Diagnostic> mine = new ArrayList<>();
            // Under the numbering the classes this row runs against were emitted with. Classes
            // that record nothing start no recording, and what comes back is no account of a run.
            if (verifier.probes
                    instanceof ProbeImage.Instrumented(var numbering)) {
                Probe.begin(numbering);
            }
            // On this thread, because this thread is the evaluation: the budget belongs to the row,
            // and a worker reused for the next row would otherwise start where this one left off.
            EvaluationContext.begin(verifier.policy.stepLimit(),
                    verifier.policy.recursionDepthLimit());
            try {
                verifier.checkRowNow(fixtures, target, sig, outCases, row, mine, state);
                if (verifier.probes instanceof ProbeImage.Instrumented) {
                    state.recorded = new RunRecord.Recorded(Probe.snapshot());
                }
            } finally {
                // Read on every way out, not only the one where the row came back. A row stopped by
                // its budget is the row whose cost is most worth knowing, and reading it after the
                // call that throws recorded nothing for exactly that row — which then said it had
                // spent nothing, the same thing a row that never ran says.
                state.stepsSpent = EvaluationContext.spent(verifier.policy.stepLimit());
                EvaluationContext.end();
                Probe.end();
            }
            return mine;
        }
    }

    /**
     * What a row's evaluation reached and saw.
     *
     * <p>{@link #reached} is written by the row's worker and read by the caller when the row runs out
     * of time — the one field read across that boundary, so it is the one field that is volatile.
     * Whether the row stopped in a fixture's helper or inside the behavior is exactly that difference,
     * and a timeout that cannot say which reports the same thing for two different problems.
     *
     * <p>Everything else is written by the worker and read only after it has finished, which the future
     * establishes: a row that does not finish has its state dropped rather than read.
     */
    static final class RowState {
        /**
         * How far the row got and what entered the behavior, as one value.
         *
         * <p>One field because they are one fact, and because a row takes back having entered one: an
         * answerer that comes back saying it never got in retracts what was published for it, and two
         * fields let a reader take the stage from before that and the answer from after. What it would
         * then hold is a row that says a behavior was entered with nothing that entered it, which is a
         * state no evaluation produces and which {@link RowOutcome} refuses to be built from.
         *
         * <p>Written only by the row's own worker, so reading it to write the next one is not a race
         * with another writer.
         */
        volatile Reached reached = new Reached(Stage.NONE, null);
        private Disposition disposition = Disposition.FAILED;
        private FailurePhase failurePhase = FailurePhase.NONE;
        private TypeSymbol expectedArm;
        private TypeSymbol resultArm;
        private final List<TypeSymbol> inputCases = new ArrayList<>();
        private final List<ObservedValue> inputs = new ArrayList<>();
        /**
         * What the row states, written once the values it states have been read.
         *
         * <p>Until then, what this compile came away with is nothing: a row it refused before
         * reading the values, and one whose reading did not finish, both leave it here. Why is not
         * said here — the stage, the disposition and the phase beside it are that answer, and this
         * one says the thing they do not, which is that the row's values are not here.
         *
         * <p>Written by the row's own worker and read after it has finished, as everything but
         * {@link #reached} is — except that a row given up on is read while the worker still holds
         * it, and what it says then is what it said before the row began.
         */
        private volatile RowStatement statement = new RowStatement.StoppedBeforeItsValues();
        /** What this row was seen to do, where the classes it ran were generated to say so. Nothing
         * recorded otherwise, and nothing recorded for a row that did not finish — a snapshot read
         * from a row still running would be some of what it did rather than what it did, and a row
         * nobody watched did not pass nowhere. */
        private RunRecord recorded = new RunRecord.NoAccount();
        /** What the row cost, in the unit it is held to. Written by the worker when it finishes, so
         * read only for a row that did. */
        private long stepsSpent;

        /** Records where the row stopped, and that it did. */
        void failed(FailurePhase phase) {
            this.disposition = Disposition.FAILED;
            this.failurePhase = phase;
        }

        /** Records that the row could not be decided — an absence of evidence, not a failure. */
        void incomplete(FailurePhase phase) {
            this.disposition = Disposition.INCOMPLETE;
            this.failurePhase = phase;
        }

        /** Records how far the row has got, keeping whatever entered the behavior. */
        void got(Stage next) {
            reached = reached.at(next);
        }

        /** Records that the behavior was entered, and what entered it. */
        void entered(Applied applied) {
            reached = new Reached(Stage.INVOKED, applied);
        }

        /**
         * Takes back the row having entered the behavior.
         *
         * <p>What was published for it goes with it: everything at {@link Stage#INVOKED} says what
         * applied it, and a row whose answerer came back saying it never got in applied nothing.
         */
        void neverEntered() {
            reached = new Reached(Stage.FIXTURES_VALIDATED, null);
        }
    }

    /**
     * How far a row's evaluation got, and what had entered the behavior if anything had.
     *
     * <p>The two together, because a reader of a row still running is entitled to a pair that some
     * moment of the evaluation actually held. Null where nothing entered.
     */
    record Reached(Stage stage, Applied applied) {

        /** The same, having got as far as {@code next}. */
        Reached at(Stage next) {
            return new Reached(next, applied);
        }
    }

    /**
     * What the row states, as something that did not read the source can hold it.
     *
     * <p>Read once, from what this evaluation already has: what it read of each stand-in, the inputs
     * it built and observed, and what it made of the expectation. Read a second time somewhere else,
     * the helpers a fixture names would be applied a second time — which is counted twice against
     * the row and does whatever they do twice.
     *
     * <p>What the statement is made of them is not decided here. Whether these are values a reader
     * can be given, and which of them stopped the row being handed over, is one question with one
     * owner ({@link RowStatements#read}) — and that owner is somewhere this run cannot be named
     * from, so what the row states cannot come to depend on what this run found to apply the
     * behavior with.
     */
    private static RowStatement statementOf(List<StoodInFor> standIns, List<ObservedValue> inputs,
                                            Expectation stated) {
        List<RowStatements.StandInRead> read = new ArrayList<>();
        for (StoodInFor standIn : standIns) {
            read.add(standIn.stated());
        }
        return RowStatements.read(read, inputs, stated);
    }

    /** What the row turned out to be, from the state its worker left. */
    private RowOutcome outcomeOf(ExampleTarget target, Hir.ExampleRow row, RowState state) {
        Reached reached = state.reached;
        return new RowOutcome(row.pos(), target.name(), row.identity(), expectationOf(row),
                reached.stage(), state.disposition, state.failurePhase, state.expectedArm,
                state.resultArm, state.inputCases, state.inputs, state.statement,
                ran(reached, new Counting.Read(state.stepsSpent, state.recorded)));
    }

    /**
     * What the row's source put where its answer goes.
     *
     * <p>Off the row and not off what the evaluation came away with. This is settled before
     * anything runs and stays true however the run ends — a row too large to hand over states
     * nothing and is still a row whose answer is owed, and a reading that stopped before the values
     * says nothing about what was written where the answer goes.
     *
     * <p>A row whose answer did not parse is owed nothing. It is refused where it is written, so
     * nobody is waiting on an author for it; what it is short of is a module that compiles.
     */
    private static ExpectationState expectationOf(Hir.ExampleRow row) {
        return switch (row.expected()) {
            case Hir.Expected.Unanswered _ -> ExpectationState.OWED;
            case Hir.Expected.Asserted _, Hir.Expected.Unwritten _ -> ExpectationState.NOT_OWED;
        };
    }

    /**
     * What became of the row's evaluation: what applied the behavior, and what this compile counted.
     *
     * <p>The first is what the row entered said of itself, taken where the row entered anything. Read
     * off the stage rather than restated, so a row that never entered a behavior cannot record
     * something as having applied it — and asked of what was entered rather than of the answerer,
     * because which of several things applies a behavior is settled per behavior. The second is taken
     * whatever the row reached, because the counting starts with the evaluation and a fixture applies
     * the helpers it names before the behavior is reached.
     */
    private static Run ran(Reached reached, Counting counting) {
        return new Run(reached.applied() == null ? new Applied.Nothing() : reached.applied(),
                counting);
    }

    /**
     * One row, evaluated within the budget: running the methods its operands were emitted as,
     * applying the behavior and comparing the result are one evaluation, so a row cannot buy more
     * time by applying more helpers (ADR-0077). Only this thread adds to {@code out} — see
     * {@link RowEvaluation} for why the row's own worker must not.
     */
    private void checkRow(ExampleTarget target, Sig sig, Set<TypeSymbol> outCases, Hir.ExampleRow row,
                          List<Diagnostic> out, List<RowOutcome> rows) {
        RowWork evaluation = new RowWork(this, target, sig, outCases, row);
        switch (deadline.given(
                new Deadline.Work.WholeRow(target.name(), row.pos(), row.identity()),
                evaluation)) {
            case Deadline.Outcome.Finished(List<Diagnostic> found) -> {
                out.addAll(found);
                rows.add(outcomeOf(target, row, evaluation.state));
            }
            case Deadline.Outcome.Overran(Runnable abandon) -> {
                // Only what the worker publishes: the rest of its state is still being written. How
                // far it got is the difference between a fixture's helper that will not stop and a
                // behavior that will not stop, which is what the author has to know, and what entered
                // the behavior comes with it because the two are one reading.
                Reached reached = evaluation.state.reached;
                abandon.run();
                // Not E1910. What did not come back was not shown to go round more than an example
                // may — it was not counted at all, which is what the compiler's own work around the
                // counted points looks like. Saying the model does not terminate here would
                // put a diagnostic on a model that may be right, and send its author to make
                // something structural that already is.
                out.add(Diagnostic.at(row.pos())
                        .say(new ExampleMessage.TheEvaluationDidNotAnswer(
                                within(deadline)))
                        .hint(new ExampleMessage.NotAnsweringIsNotNotTerminating()).build());
                // No spend is read: the worker is still writing to its state, and a count taken
                // while it runs would be some of what it spent rather than what it spent. That is
                // what the row says — not zero, which is what a row that passed no counted point
                // says.
                // What the row states is read from what the worker published before it was given up
                // on, which for a row that never got its values read is that this compile did not
                // come away with them.
                // The inputs are the ones the statement holds, where the worker got as far as
                // saying what the row states: it says that only once every input has been read, and
                // what it holds is a copy taken then. Read off the worker's own list instead, this
                // row would say it handed over nothing while the statement beside it says what it
                // handed over.
                RowStatement stated = evaluation.state.statement;
                rows.add(new RowOutcome(row.pos(), target.name(),
                        row.identity(), expectationOf(row), reached.stage(), Disposition.INCOMPLETE,
                        FailurePhase.TIMEOUT, null, null, List.of(),
                        stated instanceof RowStatement.Stated values ? values.inputs() : List.of(),
                        stated, ran(reached, new Counting.Unread())));
            }
            case Deadline.Outcome.Threw(Throwable cause) -> {
                // The evaluated code stopped itself, having gone through more than it was allowed.
                // What it spent is a property of what the row does, so this is the same answer on
                // every machine — which is the whole reason the code counts rather than the compiler
                // timing it.
                FailurePhase overspent = overspending(cause);
                if (overspent != null) {
                    out.add(overBudget(row, overspent));
                    evaluation.state.incomplete(overspent);
                    rows.add(outcomeOf(target, row, evaluation.state));
                    return;
                }
                // Every way the stack can run out arrives here, because everything the worker threw
                // arrives here. A site that happens to cross a reflection boundary names the helper
                // it was in; one that does not — a generated decoder, an encoder, the reader's own
                // walk — says only that it ran out, and both are the same answer about the same
                // thing. Classifying at the sites instead left the ones with no boundary to cross
                // falling through as a compiler failure.
                if (cause instanceof StackExhaustedException nt) {
                    out.add(stackRanOut(row, nt.getMessage()));
                    evaluation.state.incomplete(FailurePhase.STACK_EXHAUSTED);
                    rows.add(outcomeOf(target, row, evaluation.state));
                    return;
                }
                if (cause instanceof StackOverflowError) {
                    out.add(stackRanOut(row, "the evaluation overflowed the stack"));
                    evaluation.state.incomplete(FailurePhase.STACK_EXHAUSTED);
                    rows.add(outcomeOf(target, row, evaluation.state));
                    return;
                }
                // Whoever is compiling asked to stop. Nothing is known about this row — no result was
                // produced and no comparison was made — so this is not a failing example, and
                // reporting it as one would put a diagnostic on a model that may be correct.
                if (cause instanceof java.util.concurrent.CancellationException) {
                    throw new java.util.concurrent.CancellationException(
                            "interrupted while evaluating an example of `" + target.name() + "`");
                }
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException(cause);
            }
        }
    }

    /**
     * Which budget {@code cause} says was spent, or null where it says nothing about a budget.
     *
     * <p>Told apart here rather than at each place code runs, because what an author does about the
     * two is not the same: a loop that will not stop is bounded or made structural, and a recursion
     * that goes too deep is made to recurse on a part of its argument.
     */
    private static FailurePhase overspending(Throwable cause) {
        if (cause instanceof StepLimitExceeded) {
            return FailurePhase.STEP_LIMIT;
        }
        if (cause instanceof DepthLimitExceeded) {
            return FailurePhase.DEPTH_LIMIT;
        }
        return null;
    }

    /**
     * The evaluation went through more than the policy allows.
     *
     * <p>What this says is what the counting establishes and no more: the evaluation did not finish
     * within the budget. Whether the model terminates is a different question and this cannot answer
     * it — a `partial` recursion that never stops reaches this, and so does one that would have
     * stopped a hundred steps later. Reported as "this example did not terminate", it told the author
     * of a model that does terminate that their model does not, and pointed them at a recursion to
     * make structural that already was.
     *
     * <p>The two budgets are said apart because what to do about them differs: a loop is bounded or
     * given more, and a recursion is made structural or given more depth. Each names the limit it
     * reached and the setting that sets it, so the reader can tell which of the two answers applies
     * to them without being told which one their model is.
     */
    private Diagnostic overBudget(Hir.ExampleRow row, FailurePhase which) {
        boolean depth = which == FailurePhase.DEPTH_LIMIT;
        return Diagnostic.at(row.pos())
                // As written, not as a number the locale groups: `50,000` is not a budget anyone
                // set, and the setting that sets it takes the ungrouped form.
                .say(depth
                        ? new ExampleMessage.TheEvaluationReachedItsDepthLimit(
                                Integer.toString(policy.recursionDepthLimit()))
                        : new ExampleMessage.TheEvaluationSpentItsSteps(
                                Long.toString(policy.stepLimit())))
                .hint(depth ? new ExampleMessage.ReachingTheDepthLimitIsNotDiverging()
                        : new ExampleMessage.SpendingTheBudgetIsNotDiverging())
                .build();
    }

    /**
     * The JVM stack ran out before the counted depth limit was reached.
     *
     * <p>Not E1910 either, and for a reason of its own: how many frames a stack holds is decided by
     * how large they are, which is the helper's business and the JIT's. The depth limit is what is
     * meant to stop a recursion, and reaching this instead means the two are set wrong for this model
     * — which the author can act on, and which says nothing about whether the recursion terminates.
     */
    private Diagnostic stackRanOut(Hir.ExampleRow row, String why) {
        return Diagnostic.at(row.pos())
                .say(new ExampleMessage.TheStackRanOutBeforeTheDepthLimit(why,
                        Integer.toString(policy.recursionDepthLimit())))
                .hint(new ExampleMessage.TheDepthLimitIsWhatShouldStopIt()).build();
    }

    private void checkRowNow(FixtureReader fixtures, ExampleTarget target, Sig sig,
                             Set<TypeSymbol> outCases, Hir.ExampleRow row, List<Diagnostic> out,
                             RowState state) {
        List<BoundaryInput> ins = sig.ins();
        if (row.inputs().size() != ins.size()) {
            out.add(Diagnostic.at(row.pos())
                    .say(new ExampleMessage.TheRowHandsOverAnotherNumberOfInputs(target.name(),
                            String.valueOf(ins.size()), String.valueOf(row.inputs().size())))
                    .build());
            state.failed(FailurePhase.INPUT_FIXTURE);
            return;
        }
        Object[] args = new Object[ins.size()];
        for (int i = 0; i < ins.size(); i++) {
            try {
                args[i] = fixtures.built(row.inputs().get(i), ins.get(i));
            } catch (FixtureException fe) {
                out.add(Diagnostic.at(row.pos())
                        .say(new ExampleMessage.AnInputCouldNotBeBuilt(target.name(),
                                String.valueOf(i + 1), fe.getMessage()))
                        .build());
                state.failed(FailurePhase.INPUT_FIXTURE);
                return;
            }
            state.inputCases.add(caseWritten(fixtures, row.inputs().get(i), ins.get(i).type()));
            state.inputs.add(fixtures.observed(args[i]));
        }
        // What the row states of the answer, and what a clause is held to it by. A row whose answer
        // is owed states neither: there is nothing to build, no arm to hold against the target's,
        // and nothing for a declaration to be decided from until the behavior has answered.
        Expectation stated;
        Evidence evidence;
        switch (row.expected()) {
            case Hir.Expected.Asserted(Hir.Expr answer) -> {
                // validate the expected arm/value against the output cases before running. Which case
                // the row asserts is read through what it names, so a row may name a value where it may
                // name a case. Only where that answers is there an arm to hold against the target's: a
                // helper answers with a case nothing here can read off the text, and reporting that as
                // an arm the target cannot produce refused a row whose expectation was right.
                TypeSymbol named = fixtures.constructedCase(answer);
                state.expectedArm = named;
                if (named != null && !outCases.isEmpty() && !outCases.contains(named)) {
                    String expectedArm = fixtures.expectedArm(answer);
                    List<String> names = new ArrayList<>();
                    for (TypeSymbol c : outCases) {
                        names.add(c.name());
                    }
                    out.add(Diagnostic.at(row.pos())
                            .say(new ExampleMessage.NotOneOfTheResultCases(
                                    expectedArm != null ? expectedArm : named.name(), target.name()))
                            .hint(new ExampleMessage.TheResultCasesAre(String.join(", ", names)))
                            .build());
                    state.failed(FailurePhase.EXPECTED_FIXTURE);
                    return;
                }
                // Build the expected value before running: a row whose expectation cannot be built
                // states no expectation, and comparing a result against a value nothing built reported
                // a mismatch against an empty expected value — a wrong answer for a row that was right.
                //
                // At the grain the row states it — the value it wrote, or the case it named and
                // nothing under it — and read once. What a declaration is held to is read from that
                // grain below rather than worked out beside it: two readings of which grain a row
                // wrote can disagree, and then a row is compared as one thing and held to a clause as
                // another.
                try {
                    TypeSymbol only = fixtures.caseOnly(answer);
                    FixtureReader.ExpectedValue expected =
                            only != null ? null : fixtures.assertedExpected(answer, sig.out());
                    stated = only != null ? new Expectation.TheCase(only)
                            : new Expectation.TheValue(expected.asserted());
                    evidence = evidenceOf(fixtures, stated, expected, answer, sig);
                } catch (FixtureException fe) {
                    out.add(Diagnostic.at(row.pos())
                            .say(new ExampleMessage.TheExpectedValueCouldNotBeBuilt(target.name(),
                                    fe.getMessage()))
                            .build());
                    state.failed(FailurePhase.EXPECTED_FIXTURE);
                    return;
                }
            }
            case Hir.Expected.Unanswered _ -> {
                stated = new Expectation.Owed();
                // Read only where the row states an answer, which is what the statement beside it
                // says. What such a row answers is held to the declaration below, on the answer.
                evidence = null;
            }
            case Hir.Expected.Unwritten _ -> {
                // The row's answer did not parse, and the parse said so where it is written. There
                // is nothing here to run and nothing to add: a second sentence about the same
                // characters would send the author to look at the row twice.
                state.failed(FailurePhase.EXPECTED_FIXTURE);
                return;
            }
        }
        // What stands in for each dependency, read here and not where a run needs it. What the row
        // states of a stand-in and what a run applies the behavior with are two halves of one
        // reading — the fixtures a `with` or a table names are applied once, and both halves are
        // what that one application produced. What is wrong with one is not said here: a row nothing
        // was going to run is not a row a missing stand-in stops, and saying it here would report a
        // behavior with no implementation as a behavior with no fake.
        List<StoodInFor> standIns = readStandIns(fixtures, target, row);
        state.statement = statementOf(standIns, state.inputs, stated);
        state.got(Stage.FIXTURES_VALIDATED);
        // What the row states, held to what the behavior declares of what it answers. Before
        // anything is applied, and so before the row is let go for having nothing to apply it: the
        // values are here either way, and a recorded row stating an answer the model rules out is a
        // wrong record however long it waits for a body.
        //
        // What the row has is handed over as it stands. Which rules a declaration decides from an
        // answer and which it decides from a case alone is the declaration's own, worked out where
        // its check is emitted; nothing here reads a clause to choose.
        //
        // Asked of a row that states an answer. A row whose answer is owed states nothing a clause
        // relating an answer to its inputs can be held to; what it answers is held to the same
        // clauses below, where the answer is the model's rather than the row's.
        if (!(stated instanceof Expectation.Owed)
                && !keepsWhatIsDeclared(row, target, args, evidence, sig, out, state)) {
            return;
        }
        // Stated as a switch and not as a test for one of the two: what a run can have for a behavior
        // may come to say more than it does here, and a reader written as a test would go on taking one
        // of its ways with an answer it was never shown.
        Answerer.Answer.Something applies;
        switch (target.handing()) {
            case Handing.NothingApplies _ -> {
                // Everything a row can be held to without something to run it has been: its arity, its
                // inputs against their types and invariants, and its expectation against the output's
                // cases. What is left needs something to apply the behavior, and this run has nothing.
                // A fake is not it — a fake stands in for a dependency while some *other* behavior's
                // row runs, and this row is about this behavior.
                state.disposition = Disposition.PENDING;
                state.failurePhase = FailurePhase.NONE;
                return;
            }
            case Handing.NotEstablished _ -> {
                // Everything this row can be held to without being run has been, and it is not handed
                // over: what would read its values could not be established as reading them by the
                // declarations they were built from. Nothing was decided about the model, and what
                // stopped it was the answer — which is what the row says of itself, rather than being
                // worked out again by whoever reads it. The behavior's own diagnostic says why.
                state.incomplete(FailurePhase.ANSWERER_ESTABLISHMENT);
                return;
            }
            case Handing.MayApply(Answerer.Answer.Something something) -> applies = something;
        }
        List<DependencyStandin> standins = applyingWith(standIns, out);
        if (standins == null) {
            state.failed(FailurePhase.FAKE_RESOLUTION);
            return;   // a fake was missing/invalid, and what a run is told about it is now said
        }
        Answerer.Applying applying;
        try {
            applying = applies.applying(standins);
        } catch (StandinNotBuilt e) {
            // The row states the stand-in and it could not be made into an instance the behavior can
            // be constructed with. Nothing was applied, so the row stops where a row whose dependency
            // has no fake stops, and says the same thing about itself.
            out.add(ExampleStatements.unbuildableFake(whereDeclared(e.dependency(), row),
                    spelling(e.dependency()), e.getMessage()));
            state.failed(FailurePhase.FAKE_RESOLUTION);
            return;
        }
        Object result;
        // What entered it and that it entered, published as one: a row given up on is read from
        // another thread, and half of this pair is a row that never happened.
        state.entered(applying.applied());
        try {
            result = applying.to(handed(fixtures, target, args, ins));
        } catch (InvocationFailure f) {
            applicationFailed(fixtures, row, stated, f.getCause(), out, state);
            return;
        } catch (ImplementationNotReached e) {
            // Nothing was applied: what would have applied it could not be reached. Told as the row
            // aborting, which is what it was told while this and the applied code failing arrived as
            // one throw — saying it differently is a change to what a row is told, and a different
            // thing from where the two are told apart.
            state.neverEntered();
            aborted(fixtures, row, stated, String.valueOf(e.getMessage()), out, state);
            return;
        } catch (FixtureException fe) {
            // The row's input could not be put in the form the answerer reads. Nothing about the row
            // was established — it was never handed over — so it is undecided rather than failed, and
            // no diagnostic is said about a model that may be right. Which is as far as this goes:
            // no answerer a compile has crosses, so what more to say about such a row is settled by
            // whatever first supplies one that does.
            state.neverEntered();
            state.incomplete(FailurePhase.VALUE_CROSSING);
            return;
        }
        result = projected(result, sig.outputType());
        // The case the run answered with is the one the value is. Its class names the module that
        // declares it, and what this module means by that class's spelling is a different question.
        state.resultArm = fixtures.typeOf(result);
        // The answer is in hand, which is as far as a row gets before anything is done with it. A
        // measure reading what the behavior answered reads a row that got here, and the two things
        // done with an answer — holding it to the declaration, holding it to what the row states —
        // are past it rather than folded into it.
        state.got(Stage.ANSWERED);
        if (!keepsWhatIsDeclaredOfWhatItAnswered(fixtures, row, target, sig, args, result, out,
                state)) {
            return;
        }
        // Compared where the row states something to compare against. A row whose answer is owed
        // ends here: it was applied, it answered, and what it answered keeps what the behavior
        // declares of it; there is nothing further to hold the answer to, and the answer nobody
        // wrote is reported as the row's own work rather than found here.
        if (!(stated instanceof Expectation.Asserts asserts)) {
            state.disposition = Disposition.NOTHING_TO_HOLD;
            state.failurePhase = FailurePhase.NONE;
            return;
        }
        state.got(Stage.COMPARED);
        // Asked of what the row stated, which is what decides what being the same answer means: a
        // row that wrote a value is held to the value, and one that named a case is held to the
        // case and nothing under it.
        if (fixtures.holds(asserts, result, sig.outputType())
                instanceof Verdict.NotHeld(Mismatch differs)) {
            // The whole of each side, so the two can be read against each other, and then where they
            // part: a row that wrote a name the answer does not wear differs at one position by its
            // type, which reading two whole values does not say on its own.
            out.add(mismatch(fixtures, row, fixtures.shown(stated),
                    answerShown(fixtures, stated, result, sig.outputType()), differs));
            state.failed(FailurePhase.COMPARISON);
            return;
        }
        state.disposition = Disposition.HELD;
        state.failurePhase = FailurePhase.NONE;
    }

    /**
     * The case an input fixture supplies at {@code position}, or null where its text does not say. A
     * form this cannot read is not a reason to lose the row: the measure that reads it says it could
     * not classify.
     *
     * <p>Read at the position and not off the fixture alone. A position writing its values under a
     * name divides into what its base divides into, so what a row there supplies is the case under
     * that name — the same projection, in the direction that reads rather than the one that derives.
     */
    private TypeSymbol caseWritten(FixtureReader fixtures, Hir.Expr fixture, Type position) {
        try {
            return fixtures.caseUnder(TypeView.of(position, symbols).wrappers(), fixture);
        } catch (RuntimeException e) {
            if (overspending(e) != null) {
                throw e;   // the row's budget is gone; it is not a form that could not be read
            }
            return null;
        }
    }

    /**
     * The Souther value behind a behavior's answer. A member this module declared is the answer
     * itself; a primitive or an imported type reached the union through a bridge case (spec §jvm-anonymous-union),
     * and a row writes the value, not the bridge case — which is a name this source does not have.
     * The same projection a Souther caller does, done on the loaded classes.
     */
    private Object projected(Object result, Type out) {
        if (result == null || !(out instanceof Type.Union)) {
            return result;
        }
        for (TypeSymbol member : AtomSpace.subjectAtoms(out, symbols)) {
            if (!member.isDeclaredByLanguage()
                    && member instanceof TypeSymbol.AtModule at
                    && at.module().equals(module.name())) {
                continue;
            }
            if (SoutherJvmAbi.nameOf(new GeneratedClass.BridgeCase(module.name(), member)).is(result.getClass())) {
                try {
                    return result.getClass().getMethod("value").invoke(result);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("a bridge case with no `value`: " + result.getClass(), e);
                }
            }
        }
        return result;
    }

    /**
     * What the behavior's declaration is held to, read off the grain the row stated at.
     *
     * <p>The grain is settled once, where the row's expectation is read, and this takes it as it
     * stands. What it adds is the one thing a clause needs and a comparison does not: a case that
     * determines a value is a whole answer, so the value it determines is built and the clause is
     * run against it — a name standing for values a row did not write is not.
     *
     * @param expected the row's own value where it wrote one, which was computed by running the
     *     module's code and is not asked for again
     * @param answer   what the row wrote where its answer goes, which is why this is asked only of
     *     a row that wrote one
     */
    private Evidence evidenceOf(FixtureReader fixtures, Expectation stated,
                                FixtureReader.ExpectedValue expected, Hir.Expr answer, Sig sig) {
        return switch (stated) {
            case Expectation.TheValue _ -> new Evidence.Answer(expected.live());
            case Expectation.TheCase(TypeSymbol only) ->
                    symbols.declaredNode(only) instanceof Hir.UnitData
                            ? new Evidence.Answer(
                                    fixtures.buildFixture(answer, sig.out()).value())
                            : new Evidence.Case(only);
            // A row whose answer is owed states nothing a declaration can be decided from, and the
            // reading that gets here is the one that read what the row states.
            case Expectation.Owed _ -> throw new IllegalStateException(
                    "a row whose answer is owed states nothing to hold a declaration to");
        };
    }

    /**
     * What a row states of the answer, which is one of two things.
     *
     * <p>The row wrote a value, or it wrote the case the answer is and nothing under it. Which of
     * the two it has is all this side knows: what a declaration can be decided from either is the
     * declaration's, and is answered by the check it is emitted as.
     */
    private sealed interface Evidence {

        /** The answer itself, as the row's own code produced it. */
        record Answer(Object value) implements Evidence {}

        /** The case the answer is, as this module declares it. */
        record Case(TypeSymbol name) implements Evidence {}
    }

    /**
     * Whether what the row states keeps what the behavior declares of what it answers; false with
     * the refusal reported, and the row recorded as having stopped here.
     *
     * <p>An answer is projected first. A value that crossed out of another module arrives wearing
     * the case this module bridges it in, and the check reads the carrier — which is the order the
     * emitted code puts the two in as well: project, check, and only then narrow to what runs. A
     * case needs none of that: it is the name this module declares it under, and it crossed nothing.
     */
    private boolean keepsWhatIsDeclared(Hir.ExampleRow row, ExampleTarget target, Object[] args,
                                        Evidence evidence, Sig sig, List<Diagnostic> out,
                                        RowState state) {
        ValueName.Behavior behavior = module.targeted(target.name());
        String why = switch (evidence) {
            case Evidence.Answer(Object value) ->
                    ensures.notHeld(behavior, args, projected(value, sig.outputType()));
            case Evidence.Case(TypeSymbol name) -> ensures.notHeldForCase(behavior, args, name);
        };
        if (why == null) {
            return true;
        }
        out.add(Diagnostic.at(row.pos())
                .say(new ExampleMessage.ARowDoesNotKeepWhatTheBehaviorStates(target.name(), why))
                .hint(new ExampleMessage.TheDeclarationIsWhatSaysWhatItAnswers(target.name()))
                .build());
        state.failed(FailurePhase.ENSURES);
        return false;
    }

    /**
     * Whether what the behavior <em>answered</em> keeps what it declares of what it answers.
     *
     * <p>Asked of an injected behavior only, and that is not a rule of its own. A behavior with a
     * body checks its own answer where it answers, so asking again here would be a clause checked
     * twice — which is silent and costs a run on every row. An injected one has no body to check in
     * and its answer is checked where it enters generated code, and applying it for a row is such an
     * entry. Where the application's only Java calls it, this is the only one there will ever be.
     *
     * <p>The check that runs is the emitted one, the same {@code $Ensures.check} a crossing invokes,
     * so what a clause means is worked out where it was emitted and not read a second time here.
     *
     * <p>Before the row's own comparison. What the answer disagrees with here is the model, and a row
     * told only that it expected one value and saw another would send its author to look at the row.
     *
     * <p>The answer is brought into this compile's classes first, and that is not a convenience. The
     * emitted check guards each rule with an {@code instanceof} against the class this compile
     * emitted for the case, so an answer of another loader's classes matches no guard and every rule
     * is skipped — the check would run and say nothing, for every implementation, wrong or right.
     * Bringing it over is the line the Decoder draws for a value arriving from outside, which is
     * exactly what {@code AtEachCrossing} says this check is for.
     */
    private boolean keepsWhatIsDeclaredOfWhatItAnswered(FixtureReader fixtures, Hir.ExampleRow row,
                                                        ExampleTarget target, Sig sig, Object[] args,
                                                        Object answered, List<Diagnostic> out,
                                                        RowState state) {
        // Asked of what applied the row and not of how the behavior is written. The reason the
        // check runs here is that nothing else ran it: an implementation supplied from outside is
        // what answered, so the callee's own check is not what came back. A behavior written with a
        // body whose answer a binding supplied instead is in exactly that position, and reading
        // `injected` would excuse it on the strength of a body that did not run.
        if (!(state.reached.applied() instanceof Applied.Bound)) {
            return true;
        }
        Object here;
        try {
            here = inTheseClasses(fixtures, state.resultArm, sig, answered);
        } catch (FixtureException | ImplementationNotReached e) {
            // The answer could not be brought into the classes the check reads, so nothing was
            // checked. Undecided rather than failed: the model may be right, and this saw nothing.
            state.incomplete(FailurePhase.VALUE_CROSSING);
            return false;
        }
        ValueName.Behavior behavior = module.targeted(target.name());
        String why = ensures.notHeld(behavior, args, here);
        if (why == null) {
            return true;
        }
        out.add(Diagnostic.at(row.pos())
                .say(new ExampleMessage.AnImplementationDoesNotKeepWhatTheBehaviorStates(
                        target.name(), why))
                .hint(new ExampleMessage.TheDeclarationIsWhatSaysWhatItAnswers(target.name()))
                .build());
        state.failed(FailurePhase.ENSURES);
        return false;
    }

    /**
     * {@code value} as this compile's classes, read at the case it is.
     *
     * <p>Out through the neutral form and back in through this module's own decoder, which is the
     * one crossing there is. The case is where it is read: an answer is the case it turned out to
     * be, and reading it at the union it came out of would ask for the envelope a position adds
     * rather than for the value.
     *
     * <p>A value already of these classes goes through it too. Telling the two apart would mean
     * comparing a class identity, which is the question this whole seam exists to not ask.
     */
    private Object inTheseClasses(FixtureReader fixtures, TypeSymbol is, Sig sig, Object value) {
        // The case it turned out to be, where it is one. An answer that is not a declared type — a
        // scalar, a bare collection — has no case to be read at, and reading it at the output's own
        // shape is the same walk one step out. Answering "kept" for those, which reading the arm
        // alone does, would leave every behavior answering `Int` or `List<Todo>` unchecked.
        if (is != null) {
            NeutralValue neutral = fixtures.neutralAt(value, new Type.Ref(is),
                    "what `" + is.name() + "` was answered as");
            return new Crossing(loader).crossed(is, neutral.read());
        }
        NeutralValue neutral = fixtures.neutralAt(value, sig.outputType(),
                "what `" + sig.outputType() + "` was answered as");
        return new Crossing(loader).crossed(sig.out(), neutral.read());
    }

    // --- fakes for what a behavior depends on ---------------------------------------------------

    /**
     * What was read for one of the behavior's dependencies.
     *
     * <p>Two halves of one reading. What a run hands the behavior is an instance the loader the
     * implementation came from can be constructed with, and what the row states of the stand-in is
     * values; both come of building what was written, and building it twice would apply the helpers
     * a fixture names twice — counted twice against the row and doing whatever they do twice.
     */
    private sealed interface StoodInFor {

        /** What the row states of it, which it has whether or not anything is going to run it. */
        RowStatements.StandInRead stated();

        /** It was read: this is what a run applies the behavior with, and what the row states. */
        record Read(DependencyStandin runtime,
                    RowStatements.StandInRead stated) implements StoodInFor {}

        /**
         * Nothing was read that states what the dependency answers.
         *
         * <p>{@code saying} is what a run that needed it is told, said where that run is and not
         * here: a row nothing applies the behavior for is not a row a missing stand-in stops. Empty
         * where what is wrong is wrong about a table — that is said once where the table is written,
         * and every row reaching it would otherwise repeat it.
         */
        record NotRead(RowStatements.StandInRead stated,
                       List<Diagnostic> saying) implements StoodInFor {

            public NotRead {
                saying = List.copyOf(saying);
            }
        }
    }

    /**
     * What stands in for each of the target's requirements, in the order its constructor takes them,
     * up to and including the first that could not be read.
     *
     * <p>Stopping there rather than reading the rest: what a row states of its stand-ins is the
     * first one that could not be handed over, and a run needing them is stopped by the first one
     * missing. Reading past it would build fixtures for a row that is already not going to run.
     */
    private List<StoodInFor> readStandIns(FixtureReader fixtures, ExampleTarget target,
                                          Hir.ExampleRow row) {
        List<StoodInFor> standIns = new ArrayList<>(target.requirements().size());
        for (BehaviorRequirement req : target.requirements()) {
            StoodInFor standIn = readStandIn(fixtures, target.name(), req, row);
            standIns.add(standIn);
            if (standIn instanceof StoodInFor.NotRead) {
                break;
            }
        }
        return standIns;
    }

    /**
     * What a run applies the behavior with, from what was read; null where a stand-in it needs was
     * not, having said so.
     *
     * <p>Where what a run is told about a missing stand-in is said. What is wrong with one is a
     * fact whichever row met it, and this is the one place a row is stopped by it — so a row that
     * was never going to be applied is not told about it at all.
     */
    private static List<DependencyStandin> applyingWith(List<StoodInFor> standIns,
                                                        List<Diagnostic> out) {
        List<DependencyStandin> runtime = new ArrayList<>(standIns.size());
        for (StoodInFor standIn : standIns) {
            switch (standIn) {
                case StoodInFor.NotRead(RowStatements.StandInRead _, List<Diagnostic> saying) -> {
                    out.addAll(saying);
                    return null;
                }
                case StoodInFor.Read(DependencyStandin applies, RowStatements.StandInRead _) ->
                        runtime.add(applies);
            }
        }
        return runtime;
    }

    /**
     * What stands in for one requirement, read once.
     *
     * <p>What a stand-in answers and nothing more. Making it something the behavior can be constructed
     * with is a fact about the loader the implementation comes from, so it is the answerer's
     * ({@link Answerer.Answer.Something#applying}) — and reading a row's fakes is the same reading whoever that is.
     */
    private StoodInFor readStandIn(FixtureReader fixtures, String target,
                                   BehaviorRequirement req, Hir.ExampleRow row) {
        // The behavior, as the declaration it is. What a requirement is, is settled where the
        // `depends on` clause is read; the name this module happens to reach it by is not asked for
        // here and never decides which behavior a stand-in is built against.
        ValueName.Behavior dependency = req.dependency();
        String depName = dependency.name();
        Sig depSig = sigs.get(dependency);
        if (depSig == null) {
            // Nothing this module can name says what it answers, which is not a missing fake. A
            // dependency reaches here only after the check accepted the clause that named it, so a
            // signature that is not here is a module that did not build far enough to have one.
            return notRead(dependency, row.pos(), fakeMissingDiag(target, req, row, "`" + depName
                    + "` has no signature to build a stand-in against"));
        }
        BoundaryOutput outType = depSig.out();
        // What stands in, and where it was written, is ExampleProvisioning's; building the value it
        // answers with is this reader's.
        return switch (ExampleProvisioning.standingIn(row.withs(), dependency, module)) {
            case ExampleProvisioning.Standin.OnTheRow onTheRow -> {
                Hir.With w = onTheRow.written();
                try {
                    Object value = fixtures.buildFixture(w.value(), outType).value();
                    // A `with` lists nothing and answers everything: one shape for the two ways of
                    // writing what a dependency answers, so that a reader of a row has one question
                    // to ask of either.
                    yield new StoodInFor.Read(
                            new DependencyStandin(dependency, depSig.ins().size(), _ -> value),
                            RowStatements.StandInRead.of(dependency, w.pos(), takes(depSig),
                                    List.of(), new StoodIn.Otherwise.Answer(
                                            fixtures.observed(value), w.value().pos())));
                } catch (FixtureException fe) {
                    // The row does supply a fake. What failed is building its value, which is a
                    // different problem from a dependency nothing stands in for.
                    yield notRead(dependency, w.value().pos(), Diagnostic.at(w.value().pos())
                            .say(new ExampleMessage.TheFakeValueCouldNotBeBuilt(depName,
                                    fe.getMessage()))
                            .build());
                }
            }
            case ExampleProvisioning.Standin.InTheModule inTheModule ->
                    tableStandin(fixtures, inTheModule.table(), dependency, depSig);
            case ExampleProvisioning.Standin.Nothing _ -> {
                String spelt = spelling(dependency);
                yield notRead(dependency, row.pos(), fakeMissingDiag(target, req, row,
                        "add `with " + spelt + " = ...` on the row, or a `fake " + spelt
                                + "` table"));
            }
            // Saying nothing, as a row reaching a table that would not build says nothing: what is
            // wrong is wrong about what the module wrote and is said where the blocks are. A row
            // told a stand-in is missing here would be pointed at a requirement it wrote two
            // answers to, and one told a table has no output for its input would be pointed at a
            // row of a table that stands in for nothing.
            case ExampleProvisioning.Standin.MoreThanOneBlock _ ->
                    notRead(dependency, row.pos());
        };
    }

    /**
     * What the dependency declares it takes, as the row states it.
     *
     * <p>Off the signature the stand-in's values were built and compared against, which is the one
     * the table's rows were held to. What a reader of the row compares an argument at has to be
     * that one: read again from wherever a reader can reach the declaration, it would be a second
     * reading of the same declaration and would answer for a dependency this program does not
     * publish at all.
     */
    private static List<Type> takes(Sig signature) {
        List<Type> takes = new ArrayList<>();
        for (BoundaryInput input : signature.ins()) {
            takes.add(input.type());
        }
        return takes;
    }

    /** A dependency nothing was read for, and what a run that needed it is told. */
    private static StoodInFor notRead(ValueName.Behavior dependency, SourcePos at,
                                      Diagnostic... saying) {
        return new StoodInFor.NotRead(RowStatements.StandInRead.nothingRead(dependency, at),
                List.of(saying));
    }

    /** How to write {@code dependency} here, for a hint that shows what to type. */
    private String spelling(ValueName.Behavior dependency) {
        return souther.compiler.check.Requirements.writtenIn(module.name(), dependency);
    }

    /**
     * A dependency nothing stands in for. Where the target is not the definition that asked for it —
     * a composition takes the requirements of its stages — the stage that wants it is named, so the
     * author is not left to walk the composition to find out which one.
     */
    private Diagnostic fakeMissingDiag(String target, BehaviorRequirement req, Hir.ExampleRow row,
                                       String detail) {
        List<String> stages = new ArrayList<>();
        for (String requester : req.requiredBy()) {
            if (!requester.equals(target)) {
                stages.add("`" + requester + "`");
            }
        }
        Diagnostic.Builder d = stages.isEmpty()
                ? Diagnostic.at(row.pos())
                        .say(new ExampleMessage.ADependencyHasNoFake(target,
                                req.dependency().name()))
                : Diagnostic.at(row.pos())
                        .say(new ExampleMessage.ADependencyReachedThroughHasNoFake(target,
                                req.dependency().name(), String.join(", ", stages)));
        return d.hint(new ExampleMessage.WriteAFakeLikeThis(detail)).build();
    }

    /** Precomputes a function fake's input→output table (decoded fixtures) as a tuple-keyed lookup, and
     * answers by matching an actual input tuple by value equality, falling back to the {@code _}
     * default or a miss. Works for any arity: a 0/1-input dep's tuple has 0/1 elements, a 2+-input
     * dep's has one per parameter (issue #57). What the row states of the table is read off the same
     * build, so the two say what one table was written to answer. */
    private StoodInFor tableStandin(FixtureReader fixtures,
                                    souther.compiler.check.FakeTables.Occurrence.Resolved standingIn,
                                    ValueName.Behavior dependency, Sig depSig) {
        Hir.Fake fk = standingIn.read();
        // The dependency's own signature, which admitted what its boundary carries. Rebuilding the
        // types from what it declared would put them through that walk a second time, and a
        // stand-in stands where the behavior does.
        List<BoundaryInput> paramTypes = depSig.ins();
        // Built the one way a table is built ({@link ExampleStatements#standins}), on this row's own
        // reader, so a row that does not finish inside a table's helper is still inside a helper. What
        // is wrong with the table is said where the fake is written, and said once: this row and every
        // other row reaching the same fake would each repeat the one thing wrong with the one table.
        ExampleStatements.BuiltTable built =
                ExampleStatements.standins(fixtures, fk, paramTypes, depSig.out(), new ArrayList<>());
        if (built == null) {
            return notRead(dependency, fk.pos());
        }
        if (!ExampleStatements.notKept(ensures, standingIn, built).isEmpty()) {
            // A table stating what the dependency declares cannot happen is not one to stand in
            // with, as a table that will not build is not. The row stops without a fake and says
            // nothing of its own: what is wrong is wrong about the table, and is said once where the
            // table is written. Running against it would put the rest of this behavior in a state
            // the model rules out, and everything the row then reported would be about a run that
            // cannot happen.
            return notRead(dependency, fk.pos());
        }
        // The dispatch, which is what a row runs against. What the table was written with and cannot
        // dispatch to is said where the fake is written, and is nothing a stand-in can answer with.
        ExampleStatements.Standins table = built.standins();
        String depName = ExampleStatements.wrote(fk);
        int arity = paramTypes.size();
        java.util.function.Function<Object[], Object> body = a -> {
            Object[] key = java.util.Arrays.copyOf(a, arity);
            // The table's own rule, which is the rule the reading that holds it against the rows
            // recorded for the behavior asks too. One answer to "which row answers this" (E1919).
            ExampleStatements.Standin answering = table.answering(key);
            if (answering == null) {
                throw new FakeMissException("`" + depName + "` has no output for "
                        + java.util.Arrays.toString(key));
            }
            return answering.answer().value();
        };
        // What the table was written to answer, off the same build the dispatch above is. The rows
        // it cannot dispatch to are not among them: a reader walking these is walking answers the
        // stand-in can give, and which rows those are is the table's own rule to have decided.
        List<RowStatements.StandInRead.EntryRead> entries = new ArrayList<>();
        for (ExampleStatements.Standin.Explicit entry : table.explicit()) {
            entries.add(ExampleStatements.carried(fixtures, entry));
        }
        // The `_` row's answer, quoted where that answer is written rather than where the row
        // begins: it is the only value the row states, and it is the one a reader is sent to.
        ExampleStatements.Standin.Fallback fallback = table.fallback();
        StoodIn.Otherwise otherwise = fallback == null ? new StoodIn.Otherwise.NothingStated()
                : new StoodIn.Otherwise.Answer(fixtures.observed(fallback.answer().value()),
                        fallback.row().output().pos());
        return new StoodInFor.Read(new DependencyStandin(dependency, arity, body),
                RowStatements.StandInRead.of(dependency, fk.pos(), takes(depSig), entries,
                        otherwise));
    }

    /**
     * Where the dependency a stand-in was written for is declared, for a report about the stand-in
     * to be quoted against.
     *
     * <p>The row's own position for a dependency another module declares. The declaration is a place
     * in a source this report is not about, and sending an author there would send them to a file
     * with nothing wrong in it — what they can act on is the stand-in they wrote, which is on the
     * row. A dependency this module declares is quoted at its declaration, which is where the shape
     * a stand-in has to be made into is stated.
     */
    private SourcePos whereDeclared(ValueName.Behavior dependency, Hir.ExampleRow row) {
        if (!dependency.module().equals(module.name())) {
            return row.pos();
        }
        Hir.SpecBehavior dep = injectedSpec(dependency.name());
        return dep == null ? row.pos() : dep.pos();
    }


    /**
     * The answer, written beside what the row stated of it.
     *
     * <p>Rendering and not a second comparison: what a reader is shown of the answer is as much of
     * it as the row said anything about, so a row that named a case is shown the answer named and a
     * row that wrote a value is shown the value written out. A switch, so a grain added later is
     * shown deliberately rather than however the arm it falls into happens to read.
     */
    private static String answerShown(FixtureReader fixtures, Expectation stated, Object result,
                                      Type answers) {
        return switch (stated) {
            case Expectation.TheCase _ -> fixtures.describeActual(result);
            // The whole answer where the row said nothing about it either. A row whose answer is
            // owed is shown what came back, which is what an author about to write the answer down
            // is reading the report for; shown a case instead, they would be handed less than the
            // row is short of.
            case Expectation.TheValue _, Expectation.Owed _ ->
                    fixtures.shown(fixtures.structured(result), answers);
        };
    }

    private Diagnostic mismatch(FixtureReader fixtures, Hir.ExampleRow row, String expected,
                                String actual, Mismatch differs) {
        // Underline the expected result (the part the row asserts), not the whole row, so the marker
        // lands on something meaningful rather than a single column at the row's start.
        SourcePos pos = row.expected().pos();
        int width = Math.max(1, expected.length());
        Diagnostic.Builder b = Diagnostic.at(pos, width)
                .say(new ExampleMessage.TheRowDoesNotHold())
                .diff(actual, expected);
        // Where the two are of different types, the values alone do not say so — a newtype and the
        // base it wraps are written the same way by an encoder, and were reported as a mismatch
        // between two identical renderings. So the position and the two names are said.
        if (differs != null && differs.reason() == Mismatch.Reason.TYPE) {
            b.hint(new ExampleMessage.TheTwoAreOfDifferentTypes(fixtures.shown(differs.path()),
                    fixtures.typeShown(differs.expected()),
                    fixtures.typeShown(differs.observed(), differs.position())));
        }
        // The row's own words, where it has any. An unnamed row's ordinal is not words about the
        // row, and the position the report is already anchored at says which row it is.
        if (row.identity() instanceof RowIdentity.Named named) {
            b.hint(new ExampleMessage.WhatTheRowSaid(named.name()));
        }
        return b.build();
    }

    private Set<TypeSymbol> outCases(Type out) {
        return TypeOps.outputCases(out, symbols);
    }

    // --- what a row hands over, and what it makes of a failure ---------------------------------

    /**
     * The row's inputs as what it hands over: each value as this compile built it, and the form a
     * derived decoder reads, for an answerer whose classes are not this compile's.
     *
     * <p>The second is worked out only if it is asked for. The answerer this compile has of its own
     * applies the classes these values already are, so nothing crosses and nothing is walked.
     */
    private static List<Handed> handed(FixtureReader fixtures, ExampleTarget target, Object[] args,
                                       List<BoundaryInput> ins) {
        List<Handed> over = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++) {
            Object built = args[i];
            BoundaryInput at = ins.get(i);
            String what = "input " + (i + 1) + " of `" + target.name() + "`";
            over.add(new Handed(built, () -> fixtures.neutral(built, at, what)));
        }
        return over;
    }

    /**
     * What the row makes of a failure the applied code came back with.
     *
     * <p>The one reading of it, and it is here rather than where the behavior was applied because it
     * is about the row: which phase it stopped in, what the author is told, whether it is a failure at
     * all. Whoever applied the behavior carried the failure out and said nothing about it
     * ({@link InvocationFailure}), so the same throw from this compile's own classes and from an
     * implementation supplied from outside is read the same way — which is what stops a row's meaning
     * from depending on which answerer it had.
     *
     * <p>A budget spent is raised on rather than read: it is about the row's cost, which is held to
     * where the row is given its deadline, and read as anything else the reason is lost.
     */
    private void applicationFailed(FixtureReader fixtures, Hir.ExampleRow row, Expectation stated,
                                   Throwable cause, List<Diagnostic> out, RowState state) {
        if (overspending(cause) != null) {
            throw (RuntimeException) cause;
        }
        if (cause instanceof FakeMissException fm) {
            out.add(Diagnostic.at(row.pos())
                    .say(new ExampleMessage.AFakeHadNoOutputForAnInput(fm.getMessage()))
                    .build());
            state.failed(FailurePhase.FAKE_RESOLUTION);
            return;
        }
        // a point the model declared could not arise: what the row found is not a wrong answer but a
        // premise that does not hold, so it is reported as that rather than as a mismatch. Matched by
        // name: the applied code runs under its own loader, and this only needs to know which abort it
        // was.
        if (cause != null && "souther.runtime.UnreachableReached".equals(cause.getClass().getName())) {
            out.add(Diagnostic.at(row.pos())
                    .say(new ExampleMessage.TheRowReachedAnUnreachablePoint(cause.getMessage()))
                    .hint(new ExampleMessage.EitherTheRowOrTheReasonIsWrong()).build());
            state.failed(FailurePhase.INVOCATION);
            return;
        }
        if (cause instanceof StackOverflowError) {
            // Named where the behavior was applied, and told apart from a fixture's helper running
            // out, which is named where that happened.
            out.add(stackRanOut(row, "the behavior overflowed the stack"));
            state.incomplete(FailurePhase.STACK_EXHAUSTED);
            return;
        }
        aborted(fixtures, row, stated,
                cause == null ? "aborted" : String.valueOf(cause.getMessage()), out, state);
    }

    /** The behavior stopped itself while the row ran — an invariant it broke, or anything else it
     * ended with. Reported against what the row said it would answer, so the two can be read together. */
    private void aborted(FixtureReader fixtures, Hir.ExampleRow row, Expectation stated, String why,
                         List<Diagnostic> out, RowState state) {
        out.add(mismatch(fixtures, row, fixtures.shown(stated), "aborted: " + why, null));
        state.failed(FailurePhase.INVOCATION);
    }

    /** A fake table had no output for an input the behavior asked for (and no {@code _} default). */
    private static final class FakeMissException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        FakeMissException(String message) {
            super(message);
        }
    }
}
