package souther.compiler.examples;

import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.observe.WrittenStatements.Disagreement;
import souther.compiler.observe.WrittenStatements.Readings;
import souther.compiler.observe.WrittenStatements.Statement;
import souther.compiler.observe.WrittenStatements.Unread;
import souther.compiler.observe.WrittenStatements.UnreadFake;
import souther.compiler.source.SourceId;

import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.ast.Hir;
import souther.compiler.core.Contract;
import souther.compiler.check.Sig;
import souther.compiler.check.BoundaryInput;
import souther.compiler.check.BoundaryOutput;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeOps;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.evaluate.DepthLimitExceeded;
import souther.compiler.evaluate.EvaluationContext;
import souther.compiler.evaluate.StepLimitExceeded;
import souther.compiler.observe.FailurePhase;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.RowStatements;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads the statements a module writes about one of its behaviors, and reports where two of them
 * answer differently.
 *
 * <p>Nothing is applied. A row of an {@code example} says what a behavior will owe for an input; a
 * {@code fake} table and a {@code with} say what stands in for it while some *other* behavior's row
 * runs. Those are two people writing down an answer to the same question, and where the answers
 * differ the model contradicts itself whichever one is right — which is readable from the text
 * alone, with no behavior run and no result to compare against (#306).
 *
 * <p>Apart from {@link ExampleVerifier} because they answer different questions for different keys
 * and neither reads the other's state. What they share is underneath both: {@link FixtureReader}
 * reads a written fixture as the value it states, and each of these says for itself what a fixture
 * that will not build, or will not finish, means where it is.
 *
 * <p>A written {@code fake} table is built here too, because it is written. A table stands in for a
 * behavior and is read against the rows recorded for it, and a module can write one that neither of
 * those reaches — nothing depends on the faked behavior and no row records what it owes. It still
 * states what it states, and what is wrong with it is wrong wherever it is read from.
 *
 * <p>{@link Standins} is the one place a fake's table is built and the one rule by which a row of it
 * answers ({@link Standins#answering}). Both are shared with the proxy {@link ExampleVerifier}
 * installs while a row runs, deliberately: a second reading of one table is the thing <em>E1919</em>
 * exists to catch, so there must not be a second reading to drift.
 */
public final class ExampleStatements {

    /**
     * Another module's rows, as that module writes them.
     *
     * <p>What a reading here needs of a module it stands in for a behavior of: the rows that record
     * what that behavior owes, and the scope they are read in. Not its execution — the values are
     * built and compared in the reading that holds this, so nothing crosses a loader.
     */
    public record Declaring(souther.compiler.check.Prepared.ForExamples forExamples,
                            Symbols symbols, FieldTypes fields,
                            Map<String, Hir.FnDef> values) {}

    private final souther.compiler.check.Prepared.ForExamples module;
    private final Symbols symbols;
    /** What a value of a declaration is made of, as the check settled it. */
    private final FieldTypes fields;
    /** The shape of every behavior a statement here may name, keyed by the declaration it is: this
     * module's own and the ones it borrows. A stand-in may name a dependency another module
     * declares, and a table under bare spellings answers one entry for that and for a namesake
     * declared here. */
    private final Map<ValueName.Behavior, Sig> sigs;
    private final MemoryClassLoader loader;
    /** The values a statement may name: this module's own, and the ones its imports bring in. */
    private final Map<String, Hir.FnDef> values;
    /** A reader kept for showing a value that was already built ({@link #shown}), which is the one
     * thing a reader does that reads none of its own state: nothing is expanded, no binding is put in
     * force and no helper is run, so there is no reading here to isolate. What it is for is the
     * module's encoders and its loader. Every actual reading gets {@link #newFixtureReader()}. */
    private final FixtureReader rendering;
    /** What one written statement gets to be read within ({@link #within}). Carried rather than
     * looked up, so two compiles in one JVM need not agree on it. */
    private final Deadline deadline;
    /** What one reading is allowed. A statement is read by running the helpers its fixtures apply, so
     * it is held to the same counted budget a row is: what is read is decided by what the statements
     * say, not by how fast the host reading them is. */
    private final EvaluationPolicy policy;
    /** What holds a fake's row to what the dependency declares of what it answers. */
    private final EnsuresChecks ensures;
    /** The modules whose behaviors this one writes stand-ins for, by name. */
    private final Map<String, Declaring> declaring;

    private ExampleStatements(souther.compiler.check.Prepared.ForExamples module, Symbols symbols,
                              FieldTypes fields,
                              Map<ValueName.Behavior, Sig> sigs,
                              MemoryClassLoader loader, Map<String, Hir.FnDef> values,
                              Deadline deadline, EvaluationPolicy policy,
                              Map<ValueName.Behavior, Contract> contracts,
                              Map<String, Declaring> declaring) {
        this.ensures = new EnsuresChecks(loader, contracts, sigs.keySet());
        this.declaring = declaring;
        this.module = module;
        this.symbols = symbols;
        this.fields = fields;
        this.sigs = sigs;
        this.loader = loader;
        this.values = values;
        this.deadline = deadline;
        this.policy = policy;
        this.rendering = new FixtureReader(module, symbols, fields, values, loader);
    }

    /**
     * Where a row of {@code built} states values the faked behavior declares cannot go together.
     *
     * <p>Asked after the table is built rather than while it is being built, so that the two readers
     * of a table ask it in the two ways each needs: where the fake is written it is reported on, and
     * where a row would stand in with it, it is what says the table is not one to stand in with. The
     * second is why this is not left to the check the stand-in's answer would meet at the crossing:
     * a row run against a dependency state the model rules out reaches the rest of its behavior in a
     * state nothing can arise in, and what that row then reports is about a run that cannot happen.
     *
     * <p>The rows the table can answer with, which is what {@link Standins#explicit} is. A row the
     * dispatch never reaches states nothing the fake stands in with, and what is wrong with it is
     * that it answers nothing ({@link #cannotAnswer}). The {@code _} row is not here either: it
     * states no input, so there is no relation to hold its answer to.
     *
     * <p>{@code table} is a block whose target reached a behavior, which is what every caller holds
     * ({@link souther.compiler.check.FakeTables#naming}), so the declaration its rows are held to is
     * there to be asked about. Whether that block is also the one standing in for the behavior is
     * not asked: a row of a block is held to what the dependency declares because it was written,
     * and a block that stands in for nothing because another names the same behavior states what it
     * states.
     */
    static List<Diagnostic> notKept(EnsuresChecks ensures,
                                    souther.compiler.check.FakeTables.Occurrence.Resolved table,
                                    BuiltTable built) {
        Hir.Fake fk = table.read();
        List<Diagnostic> said = new ArrayList<>();
        for (Standin.Explicit standin : built.standins().explicit()) {
            // The behavior the table stands in for, which is what declares the clause its rows are
            // held to. Taken from the classification: a dependency another module declares states
            // its own, and minting a name in this module would hold the row to nothing.
            String why = ensures.notHeld(table.behavior(),
                    standin.arguments(), standin.answer().value());
            if (why != null) {
                said.add(Diagnostic.at(standin.row().pos())
                        .say(new ExampleMessage.AFakeRowDoesNotKeepWhatTheDependencyStates(
                                wrote(fk), why))
                        .hint(new ExampleMessage.TheDeclarationIsWhatSaysWhatItAnswers(wrote(fk)))
                        .build());
            }
        }
        return said;
    }

    /**
     * The {@code _} row this table falls through to, or null where it has none.
     *
     * <p>The last one written. A {@code _} with another after it is a row the dispatch never
     * returns, so which one answers is the last and not the first.
     *
     * <p>Read off what the rows state and nothing else, so it is the same question whether or not
     * there is anything to build the answers with. That is what lets a reader with no values in
     * hand ask whether this table can fail to answer ({@link #answersEveryCall}) — the rule that
     * decides it is this one, asked once.
     */
    static Hir.FakeRow fallenThroughTo(Hir.Fake table) {
        Hir.FakeRow last = null;
        for (Hir.FakeRow row : table.rows()) {
            if (row.matched() instanceof Hir.Matched.Anything) {
                last = row;
            }
        }
        return last;
    }

    /**
     * Whether this table has an answer for whatever it is asked.
     *
     * <p>A property of the table and not of any call. A table with a {@code _} row answers every
     * call that reaches it; one without answers the calls its rows state and refuses the rest,
     * which is <em>E1909</em> where the row that made the call was written.
     *
     * <p>Off the same reading the dispatch falls through by, so the two cannot come apart: a reader
     * told a table answers everything and a dispatch that then refuses a call would be two answers
     * to one question, and the reader's is the one somebody acted on.
     */
    public static boolean answersEveryCall(Hir.Fake table) {
        return fallenThroughTo(table) != null;
    }

    /** What the author wrote for a fake's target, for a message that quotes the source. */
    public static String wrote(Hir.Fake table) {
        return table.target().written().quoted();
    }

    /** The same for a {@code with}. */
    static String wrote(Hir.With supplied) {
        return supplied.dep().written().quoted();
    }

    /**
     * The stretch a report about a fake's table marks: the target, as it was written.
     *
     * <p>Taken from the name and not measured from its spelling. A target may be written through the
     * module that declares the behavior, and the characters that reach are not the canonical name's
     * — a qualifier and its dots are part of what the marker covers, and an author may write spaces
     * or a comment between them.
     */
    public static Region marked(Hir.Fake fk) {
        return fk.target().written().region();
    }

    /**
     * A reader for one written statement, held for as long as reading it lasts.
     *
     * <p>Never shared between two of them. Reading a fixture runs the method a row's operand was
     * emitted as, and a {@code partial} helper compiled into it may not stop, so a reading that runs
     * out of its budget is asked to stop and cannot be made to — and what it goes on writing to is
     * this.
     */
    private FixtureReader newFixtureReader() {
        return new FixtureReader(module, symbols, fields, values, loader);
    }

    /**
     * How to make a reader for statements written in {@code declaredIn}.
     *
     * <p>That module's rows, values and names, on this reading's loader. The two halves of what this
     * decides are why: a fixture is read in the scope it was written in, and the values it comes to
     * are compared with the ones a stand-in here comes to — so the scope is the other module's and
     * the execution is this one's.
     */
    private java.util.function.Supplier<FixtureReader> readersFor(String declaredIn) {
        if (declaredIn.equals(module.name())) {
            return this::newFixtureReader;
        }
        Declaring elsewhere = declaring.get(declaredIn);
        return () -> new FixtureReader(elsewhere.forExamples(), elsewhere.symbols(),
                elsewhere.fields(), elsewhere.values(), loader);
    }

    private Set<TypeSymbol> outCases(Type out) {
        return TypeOps.outputCases(out, symbols);
    }

    // --- two statements about one behavior -----------------------------------------------------

    /**
     * Every input for which two of this module's written statements about one behavior answer
     * differently.
     *
     * <p>{@code module} is the whole module — every row and every fake, whichever source wrote it. A
     * fake and the rows it disagrees with need not be in one file, so neither side can be found from
     * one source's share of them. Which source each came from is what its own place says.
     *
     * <p>No behavior is applied. What a fake answers for an input is decided by the rule the fake
     * itself dispatches with ({@link Standins#answering}) — the same rule, not a second reading of it — and
     * the two answers are compared as the written values they are built into.
     */
    public static Readings disagreements(souther.compiler.check.Prepared.ForExamples module, Symbols symbols,
                                         FieldTypes fields,
                                         Map<ValueName.Behavior, Sig> sigs,
                                         Map<String, ClassFileImage> classes,
                                         ClassLoader parent, Map<String, Hir.FnDef> values,
                                         Deadline deadline, EvaluationPolicy policy,
                                         Map<ValueName.Behavior, Contract> contracts,
                                         Map<String, Declaring> declaring) {
        if (module.examples().isEmpty() && module.fakes().written().isEmpty()) {
            return Readings.NONE;
        }
        // Which behaviors have both a stand-in and rows of their own, read off the text. Two written
        // statements are what this is about, and where there are not two there is nothing to build:
        // a module whose faked behaviors are exampled nowhere — which is most of them, since a fake is
        // written so that some *other* behavior's rows can run — is settled here, before a class is
        // loaded or a worker is started.
        Set<ValueName.Behavior> contested = contested(module, sigs, declaring);
        if (contested.isEmpty()) {
            return Readings.NONE;
        }
        ExampleStatements v = new ExampleStatements(module, symbols, fields, sigs,
                new MemoryClassLoader(classes, parent), values, deadline, policy, contracts,
                declaring);
        try {
            return v.collectDisagreements(contested);
        } catch (LinkageError e) {
            // Comparing what two statements answer runs the generated values' own equality, and on a
            // host with no runtime that is the first thing here to touch it: a row's fixtures build
            // without it, so the reading gets as far as the comparison. What ends it is not about any
            // fake — every one in every module would say the same thing — and it is recorded once,
            // where the rows are evaluated, as `within` answers for a build that could not start.
            if (runtimeAbsent(e)) {
                return Readings.NONE;
            }
            throw e;
        }
    }

    /**
     * The tables a source is the one to build: what its {@code fake} rows state and nothing else
     * does.
     *
     * <p>Its own answer because two things ask it. Building them asks it to know what to walk, and
     * whatever runs the building asks it to know whether there is anything to run — and a caller
     * that worked the second one out for itself would be walking the blocks again.
     *
     * <p>Every block written here, and not only the ones that answer for a behavior. A table is
     * built because it is written (spec §example-fakes): what is wrong inside a block is wrong
     * whether or not anything stands in with it, and a behavior more than one block names is a
     * refusal about the module rather than a reason to stop reading what those blocks say. A block
     * whose target reached no behavior has no signature to be built against and is not here.
     */
    public static List<souther.compiler.check.FakeTables.Occurrence.Resolved> tablesBuiltIn(
            souther.compiler.check.Prepared.ForExamples module, Map<ValueName.Behavior, Sig> sigs,
            SourceId sourceId) {
        List<souther.compiler.check.FakeTables.Occurrence.Resolved> building = new ArrayList<>();
        for (souther.compiler.check.FakeTables.Occurrence.Resolved table : module.fakes().naming()) {
            if (!table.read().pos().isIn(sourceId)) {
                continue;   // written in another source, and built by that source's own reading
            }
            if (sigs.get(table.behavior()) == null) {
                continue;   // nothing here can say what it answers, so there is no table to build
            }
            building.add(table);
        }
        return building;
    }

    /**
     * What building this module's fake tables says about the ones {@code sourceId} wrote, each built
     * once.
     *
     * <p>Because they are written, not because something reads them. A table is built to stand in for
     * a behavior (where {@link ExampleVerifier} resolves it for a row) and to be read against the rows
     * recorded for it ({@link #againstFake}), and a module can write one that neither of those reaches:
     * nothing in it depends
     * on the faked behavior, and no row records what that behavior owes. The table still states what
     * it states, and what is wrong with it is wrong wherever it is read from.
     *
     * <p>The one place it is said, so a build and a row cannot come to two answers about one table.
     * A row that applies a fake it could not build says nothing about the table any more — it does not
     * run, and the error at the fake is what the compile fails on — which also ends the same table
     * being reported once per row that reaches it.
     *
     * <p>Every block whose target reached a behavior, whether or not it is the one standing in for
     * it: a block that stands in for nothing because another names the same behavior still states
     * what its rows state, and an author who merges the blocks would otherwise be shown a fault only
     * then. A block whose target reached no behavior is not built — there is no signature to build
     * it against, and what is wrong with it is said where the name is read.
     *
     * <p>{@code module} holds every fake, since what a module declares a stand-in for is a fact
     * about the module and not about one of its files, and {@code sourceId} is the file this run
     * reports on. Which of them a fake is written in is what its own place says, so the two are never out of
     * step.
     */
    public static List<Diagnostic> fakeTables(souther.compiler.check.Prepared.ForExamples module, Symbols symbols,
                                              FieldTypes fields,
                                              Map<ValueName.Behavior, Sig> sigs,
                                              Map<String, ClassFileImage> classes,
                                              ClassLoader parent, Map<String, Hir.FnDef> values,
                                              SourceId sourceId,
                                              Deadline deadline, EvaluationPolicy policy,
                                              Map<ValueName.Behavior, Contract> contracts) {
        List<souther.compiler.check.FakeTables.Occurrence.Resolved> building =
                tablesBuiltIn(module, sigs, sourceId);
        if (building.isEmpty()) {
            return List.of();
        }
        // Building a table reads statements written here and nothing another module wrote, so it
        // needs no reading of one.
        ExampleStatements v = new ExampleStatements(module, symbols, fields, sigs,
                new MemoryClassLoader(classes, parent), values, deadline, policy, contracts,
                Map.of());
        List<Diagnostic> said = new ArrayList<>();
        for (souther.compiler.check.FakeTables.Occurrence.Resolved table : building) {
            Hir.Fake fk = table.read();
            Sig sig = sigs.get(table.behavior());
            // Within a budget of its own, for the reason a row and a reading each have one: a row of
            // the table applies helpers, and a `partial` one may not stop.
            Read<List<Diagnostic>> read = v.within(reader -> {
                List<Diagnostic> wrong = new ArrayList<>();
                BuiltTable built = standins(reader, fk, sig.ins(), sig.out(), wrong);
                if (built != null) {
                    for (Shadowed dead : built.shadowed()) {
                        wrong.add(cannotAnswer(fk, dead));
                    }
                    wrong.addAll(notKept(v.ensures, table, built));
                }
                return wrong;
            }, new Deadline.Work.Table(wrote(fk), fk.pos()));
            switch (read) {
                case Read.Got(List<Diagnostic> wrong) -> said.addAll(wrong);
                case Read.Overspent(FailurePhase which, long limit) ->
                        said.add(unreadableFake(fk, Unread.overspending(which, limit)));
                case Read.StackRanOut(int depthLimit) ->
                        said.add(unreadableFake(fk, new Unread.StackRanOut(depthLimit)));
                case Read.Unanswered(Duration within) ->
                        said.add(unreadableFake(fk, new Unread.DidNotAnswer(within)));
                // Not about this fake: the runtime is `provided`, so a host without it builds no value
                // at all and every fake in every module would say the same thing. Where the rows are
                // evaluated that is recorded once, as an incompleteness.
                case Read.RuntimeAbsent() -> { }
            }
        }
        return List.copyOf(said);
    }

    /**
     * What one reading came to: the statement, or the reason there is no statement.
     *
     * <p>The reason is answered rather than dropped because the two reasons are held against
     * different things. A statement that did not finish is about this module, and where nothing else
     * builds what it was reading it is this reading's to say; a host with no runtime is about the
     * host, and says the same thing about every fake and every row in every module compiled on it.
     * A reader that got only "nothing was read" would have to pick one of those, and each caller
     * would pick for itself.
     */
    private sealed interface Read<T> {

        /** What the reading answered. Null where the statement is written in a form that states
         * nothing — an input that will not build, an expectation that cannot be read. */
        record Got<T>(T value) implements Read<T> {}

        /** The reading spent what the policy allows, and which budget it spent: the statements say
         * something this compiler will not read all of, and it says so the same way on every host. */
        record Overspent<T>(FailurePhase which, long limit) implements Read<T> {}

        /** The stack ran out before the counted depth limit was reached, and the limit it did not
         * reach. Not a statement about the table either: how many frames a stack holds is decided by
         * how large they are. */
        record StackRanOut<T>(int depthLimit) implements Read<T> {}

        /** The reading stopped answering, and the wait it was held to: what a report has to name is
         * what the wait actually was, not what a second reader of the setting makes of it later. Not
         * a budget in the sense the counted limits are — those are spent by the code, and this is
         * time the compiler spent without answering.
         *
         * <p>Not a statement about the model. Whatever did not come back was not counted — the
         * compiler's own work around the points it places, or a call into classes that have none —
         * so nothing here can say the statements are at fault. */
        record Unanswered<T>(Duration within) implements Read<T> {}

        /** This host has no runtime to build a value against. */
        record RuntimeAbsent<T>() implements Read<T> {}

        /** What was read, or null — for a caller that has nothing of its own to say about why
         * there is nothing. Both reasons come out the same here on purpose: the reading that did
         * not happen is reported by whoever else reads the same statement. */
        default T orNull() {
            return this instanceof Got(T value) ? value : null;
        }
    }

    /**
     * One statement, read within its own share of the budget.
     *
     * <p>Per statement rather than per module. Reading a fixture runs compiled code, and a `partial`
     * helper in it may not stop — so a budget covering the whole reading is one a
     * single slow row can spend, and spending it would drop every other statement's reading with it:
     * a plain contradiction elsewhere in the module would go unsaid because of a row it has nothing
     * to do with. It is what {@link ExampleVerifier#checkRow} already does for a row it evaluates, and this reads
     * strictly fewer statements than that evaluates rows.
     *
     * <p>What did not finish states nothing. Who says so is the caller's, and differs: a row and a
     * {@code with} written on one are evaluated elsewhere and report it there (E1910), while a fake
     * table is built here and, where no row depends on the behavior it stands in for, nowhere else.
     */
    private <T> Read<T> within(java.util.function.Function<FixtureReader, T> read,
                               Deadline.Work what) {
        return within(this::newFixtureReader, read, what);
    }

    /** The same, reading a statement written in another module through a reader made for it. */
    private <T> Read<T> within(java.util.function.Supplier<FixtureReader> readers,
                               java.util.function.Function<FixtureReader, T> read,
                               Deadline.Work what) {
        // On a reader of its own, and that is all a reading needs to be given: a worker that runs out
        // of budget is asked to stop and cannot be made to — a fixture reaches no interrupt point — so
        // it may still be inside `expandedValue`, holding a binding or a half-walked expansion. What a
        // late worker can still write to is that reader, and the statement after it gets another.
        FixtureReader reader = readers.get();
        switch (deadline.given(what, () -> counted(() -> read.apply(reader)))) {
            case Deadline.Outcome.Finished(T value) -> {
                return new Read.Got<>(value);
            }
            case Deadline.Outcome.Overran(Runnable abandon) -> {
                // Nothing here was going to read how far it got, so it is given up on at once.
                abandon.run();
                return new Read.Unanswered<>(deadline.timeout());
            }
            case Deadline.Outcome.Threw(Throwable cause) -> {
                // The reading spent what the policy allows. That is an answer about the statements —
                // the same one on every host — and is told apart from a reading that stopped answering,
                // which is an answer about the host.
                if (cause instanceof StepLimitExceeded) {
                    return new Read.Overspent<>(FailurePhase.STEP_LIMIT, policy.stepLimit());
                }
                if (cause instanceof DepthLimitExceeded) {
                    return new Read.Overspent<>(FailurePhase.DEPTH_LIMIT,
                            policy.recursionDepthLimit());
                }
                // The stack ran out. Said here for the reason the two above are: everything the
                // worker threw arrives at this one place, so classifying anywhere else leaves the
                // paths that do not go through that place unclassified — and an Error rethrown from
                // here is a compiler failure rather than a report about the statement.
                if (cause instanceof StackOverflowError || cause instanceof StackExhaustedException) {
                    // Both forms of the same thing. A stack that runs out inside a helper crosses a
                    // reflection boundary on the way here and arrives named; one that runs out
                    // anywhere else arrives raw. Recognising only the raw one left the named one to
                    // the rethrow below, where a `RuntimeException` leaves the compilation as a
                    // failure of the compiler rather than as a report about the table.
                    return new Read.StackRanOut<>(policy.recursionDepthLimit());
                }
                // One thing ends a reading without the model or this code being at fault: a host with
                // no runtime to build a value against, since the runtime is `provided` (as it is for
                // CTFE).
                if (runtimeAbsent(cause)) {
                    return new Read.RuntimeAbsent<>();
                }
                // Anything else is this code being wrong. An empty reading says the statements agree,
                // so answering with one would leave a broken compiler reporting every model
                // consistent, and only a test that reached the broken shape would ever say otherwise.
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause);
            }
        }
    }

    /**
     * {@code read}, run with this reading's budget counting on the thread it runs on.
     *
     * <p>Started here rather than around the whole reading, because a budget covering every statement
     * is one a single statement can spend — and spending it would drop the reading of every other one
     * with it, so a plain contradiction elsewhere in the module would go unsaid because of a fixture
     * it has nothing to do with. It is the same reason each statement gets its own deadline.
     */
    private <T> T counted(java.util.function.Supplier<T> read) {
        EvaluationContext.begin(policy.stepLimit(), policy.recursionDepthLimit());
        try {
            return read.get();
        } finally {
            EvaluationContext.end();
        }
    }

    /**
     * Whether {@code cause} is this host having no runtime rather than anything being wrong.
     *
     * <p>The runtime is {@code provided}, so a build that compiles Souther without it on the class
     * path can generate the classes and not load them. That is the one {@link LinkageError} that says
     * nothing about the model or about this code — the rest of the family says a class was generated
     * or loaded wrong ({@code VerifyError}, {@code ClassFormatError}, {@code NoSuchMethodError}), and
     * reading those as "no runtime" would let a broken back end report every model consistent.
     */
    private static boolean runtimeAbsent(Throwable cause) {
        return cause instanceof NoClassDefFoundError missing
                && missing.getMessage() != null
                && missing.getMessage().replace('/', '.').startsWith("souther.runtime.");
    }

    /**
     * What the dependency a {@code with} stands in for answers, or null where nothing here says.
     *
     * <p>Null covers both a name that denoted no behavior — refused where it is read, so nothing
     * further is said about it — and one whose module this reading did not get. Asked in one place
     * because a lookup under a key of the wrong kind, or under none, is the mistake this whole
     * reading is keyed by declarations to avoid.
     */
    private static Sig shapeOf(Map<ValueName.Behavior, Sig> sigs, Hir.With supplied) {
        return supplied.standsInFor() == null ? null : sigs.get(supplied.standsInFor());
    }

    /**
     * The behaviors this module both stands in for — the target of a {@code fake}, the dependency a
     * {@code with} that takes no input answers for ({@link #againstWiths}) — and records rows of.
     *
     * <p>Both sides as the declarations they are. A row is recorded for a behavior of the module it
     * is written in and a stand-in may name one another module declares, so comparing the spellings
     * would put a table written for a borrowed dependency against the rows of a namesake here.
     */
    private static Set<ValueName.Behavior> contested(
            souther.compiler.check.Prepared.ForExamples module, Map<ValueName.Behavior, Sig> sigs,
            Map<String, Declaring> declaring) {
        Set<ValueName.Behavior> both = new LinkedHashSet<>();
        for (ValueName.Behavior each : module.standsInFor()) {
            // What the module writes a stand-in for is the whole frontier; which of those can be
            // held against a recorded row is this reading's question. A `with` states no input, so
            // it and a row are about one call only where the dependency takes none — every other
            // input reaching it is what the parent behavior computed, which no recorded row states.
            Sig sig = sigs.get(each);
            boolean comparable = module.fakes().unique().containsKey(each)
                    || (sig != null && sig.inputTypes().isEmpty());
            if (comparable && !recordedFor(each, module, declaring).isEmpty()) {
                both.add(each);
            }
        }
        return both;
    }

    /**
     * The {@code example} blocks that record what {@code behavior} owes.
     *
     * <p>Where the behavior is declared, always: a row names a behavior of the module it is written
     * in (spec §example-evaluable), so the rows for one another module declares are that module's.
     * Empty where this reading was not given that module — nothing was compared, and the reading
     * says so by having nothing rather than by taking silence for agreement.
     */
    private static List<Hir.Example> recordedFor(ValueName.Behavior behavior,
                                                 souther.compiler.check.Prepared.ForExamples module,
                                                 Map<String, Declaring> declaring) {
        souther.compiler.check.Prepared.ForExamples where = behavior.module().equals(module.name())
                ? module
                : declaring.containsKey(behavior.module())
                        ? declaring.get(behavior.module()).forExamples() : null;
        if (where == null) {
            return List.of();
        }
        List<Hir.Example> blocks = new ArrayList<>();
        for (souther.compiler.check.Prepared.Example block : where.examples()) {
            if (block.target().equals(behavior.name())) {
                blocks.add(block.read());
            }
        }
        return blocks;
    }

    /** One recorded row, read as far as it can be without running it. What it says is left as written
     * — rendering it builds the fixture a second time, and only a row that turns out to disagree is
     * ever shown. */
    private record RecordedRow(Hir.Expr expected, Object[] arguments, Answered answer) {}

    private Readings collectDisagreements(Set<ValueName.Behavior> contested) {
        Map<ValueName.Behavior, List<RecordedRow>> recorded = new LinkedHashMap<>();
        for (ValueName.Behavior behavior : contested) {
            for (Hir.Example ex : recordedFor(behavior, module, declaring)) {
                readRecorded(behavior, ex, recorded);
            }
        }
        if (recorded.isEmpty()) {
            return Readings.NONE;
        }
        List<Disagreement> found = new ArrayList<>();
        List<UnreadFake> timedOut = new ArrayList<>();
        // The blocks that answer, which is one block per behavior. A behavior more than one names
        // has no table standing in for it, so there is nothing there to be held against a recorded
        // row: the two statements this compares are a stand-in and a row, and what those blocks
        // left is not a stand-in.
        module.fakes().unique().forEach((_, table) ->
                againstFake(table, recorded, found, timedOut));
        for (int i = 0; i < module.examples().size(); i++) {
            againstWiths(module.examples().get(i).read(), recorded, found);
        }
        return new Readings(found, timedOut);
    }

    /**
     * The rows of one example, as much of each as is comparable.
     *
     * <p>A row is read here the way the evaluator reads it: its arity against the signature, its
     * inputs built against their parameter types, and its expectation against the output's cases and
     * then built. A row that fails any of those states nothing — each is reported as the arity, input,
     * arm or fixture error it is where the row is evaluated — and a row read otherwise here than there
     * would be held to a stand-in on an assertion the model itself refuses.
     */
    private void readRecorded(ValueName.Behavior behavior, Hir.Example ex,
                              Map<ValueName.Behavior, List<RecordedRow>> into) {
        Sig sig = sigs.get(behavior);
        if (sig == null) {
            return;
        }
        // The rows as the module that wrote them writes them: its own values and its own names,
        // because a fixture means what it means in the scope it was written in. Built here, on this
        // reading's loader, so the two values a comparison is about are of one execution and the
        // equality that decides it is the language's own.
        java.util.function.Supplier<FixtureReader> readers = readersFor(behavior.module());
        Set<TypeSymbol> cases = outCases(sig.outputType());
        for (Hir.ExampleRow row : ex.rows()) {
            if (row.inputs().size() != sig.inputTypes().size()) {
                continue;
            }
            // What a fake is held to is what the rows of the behavior it stands in for say that
            // behavior answers. A row whose answer is owed says nothing of the kind, and one whose
            // answer did not parse says nothing at all, so neither is a row a table can disagree
            // with.
            if (!(row.expected() instanceof Hir.Expected.Asserted(Hir.Expr asserted))) {
                continue;
            }
            Read<RecordedRow> read = within(readers, reader -> {
                Object[] arguments = builtOrNull(reader, row.inputs(), sig.ins());
                if (arguments == null) {
                    return null;
                }
                Answered answer = readExpected(reader, asserted, sig.out(), cases);
                return answer instanceof Answered.Unreadable ? null
                        : new RecordedRow(asserted, arguments, answer);
            }, new Deadline.Work.Fixtures(ex.target(), row.pos(), row.identity()));
            // A reading that did not finish is not said here, whichever reason ended it. The same row
            // is evaluated where the example is checked, which builds these fixtures and then runs the
            // behavior on top of them, so a fixture that overruns this overruns that too and is E1910
            // there, at the row.
            RecordedRow got = read.orNull();
            if (got == null) {
                continue;
            }
            into.computeIfAbsent(behavior, _ -> new ArrayList<>()).add(got);
        }
    }

    /** One fake against the rows recorded for the behavior it stands in for. */
    private void againstFake(souther.compiler.check.FakeTables.Occurrence.Resolved standingIn,
                             Map<ValueName.Behavior, List<RecordedRow>> recorded,
                             List<Disagreement> found, List<UnreadFake> timedOut) {
        Hir.Fake fk = standingIn.read();
        List<RecordedRow> rows = recorded.get(standingIn.behavior());
        Sig sig = sigs.get(standingIn.behavior());
        if (rows == null || sig == null) {
            return;
        }
        // The whole table, built the one way the proxy builds it, and held to the dependency's
        // declaration the one way a table is held to it. Inside the reading because holding it runs
        // the module's code, which is what a reading is what it costs of.
        Read<BuiltTable> read = within(
                reader -> {
                    BuiltTable made = standins(reader, fk, sig.ins(), sig.out(), new ArrayList<>());
                    return made == null || !notKept(ensures, standingIn, made).isEmpty()
                            ? null : made;
                },
                new Deadline.Work.Table(wrote(fk), fk.pos()));
        // A switch, so that a fourth reason for a reading to end has to decide what a fake does about
        // it rather than falling in with one of these.
        switch (read) {
            // Said here, because here is where it can be. The other place a table is built is
            // `resolveFake`, which runs while a row of a behavior that *depends on* this one is
            // evaluated — and a fake nothing depends on has no such row, so an overrun that went
            // unsaid here would leave "the two agree" as the answer to a comparison never made.
            // The marker goes over the target, as the name itself was written.
            case Read.Overspent(FailurePhase which, long limit) -> {
                timedOut.add(new UnreadFake(wrote(fk), marked(fk),
                        Unread.overspending(which, limit)));
                return;
            }
            case Read.StackRanOut(int depthLimit) -> {
                timedOut.add(new UnreadFake(wrote(fk), marked(fk),
                        new Unread.StackRanOut(depthLimit)));
                return;
            }
            case Read.Unanswered(Duration within) -> {
                timedOut.add(new UnreadFake(wrote(fk), marked(fk),
                        new Unread.DidNotAnswer(within)));
                return;
            }
            // Not about this fake. The runtime is `provided`, so a host without it builds no value at
            // all, and every fake and every row in the module would say the same thing; what was not
            // read for that reason is recorded once, where the rows are evaluated. Defensive: with no
            // runtime no recorded row builds either, and a reading with no rows read never reaches a
            // fake — so nothing arrives here today.
            case Read.RuntimeAbsent() -> {
                return;
            }
            case Read.Got(BuiltTable _) -> { }
        }
        // The whole table or nothing, and two ways to have nothing: a table with a row that will not
        // build, and one with a row stating what the dependency declares cannot happen. Both answer
        // nothing here, and what is wrong with either is reported where the fake is written
        // ({@link #fakeTables}).
        //
        // The second is not only a duplicate spared. A disagreement says two descriptions of one
        // behavior differ and neither is the right one (ADR-0093); a refused row says the
        // declaration decides and the fake is the side that is wrong. Said about one pair, the two
        // contradict each other — so once the declaration has ruled a table out, there is no
        // description left here for a recorded row to disagree with.
        BuiltTable built = read.orNull();
        if (built == null) {
            return;
        }
        // What the fake answers with, which is what a comparison is about. A row the table cannot
        // dispatch to is written and unreachable, and reporting a disagreement about it would be a
        // disagreement over a value the fake never gives (ADR-0093).
        Standins table = built.standins();
        for (RecordedRow row : rows) {
            Standin answering = table.answering(row.arguments());
            if (answering == null) {
                continue;   // the table answers nothing for this input; E1909's where it is used
            }
            Answered stood = new Answered.Whole(answering.answer());
            if (differs(row.answer(), stood)) {
                // The output, not the row: what disagrees is the answer, and the marker lands on it
                // the way a row's does on its expected.
                found.add(new Disagreement(wrote(fk),
                        said(row.expected(), row.answer()),
                        said(answering.row().output(), stood),
                        false));
            }
        }
    }

    /**
     * The {@code with}s on the rows of one example, against the rows recorded for the dependency each
     * stands in for — where the dependency takes no input, and there only.
     *
     * <p>A {@code with} is a fixture bound to the run of the row it is written on, not a statement
     * about the dependency. It answers whatever it is asked, but that is a fact about the function it
     * installs, not about which of the dependency's inputs it was written for: what reaches the
     * dependency is whatever the parent behavior computes and passes — a normalised field, one of two
     * branches, one of several calls — and none of that is readable from the {@code with}. Held
     * against every recorded row, it would drop the parent row's own input and read a row-local
     * assumption as a claim about all of them, so two rows faking two different answers for two
     * different orders would be reported as contradicting each other while both are right.
     *
     * <p>A dependency that takes nothing has one input, {@code ()}, so a {@code with} for it and a row
     * recorded for it are about the same call, and the comparison is the same one a {@code fake} row
     * gets. A fake row states its own inputs, which is why it needs no such condition: the recorded
     * input picks the row that answers it ({@link Standins#answering}), with nothing evaluated in between.
     *
     * <p>A {@code with} takes precedence over a {@code fake} table while the row carrying it runs
     * (see where {@link ExampleVerifier} resolves a fake). That is dispatch, and it is not brought
     * here: the table is still a
     * statement about the same behavior, written for every other row and every other run, and a
     * {@code with} beside it does not settle what it states.
     */
    private void againstWiths(Hir.Example ex,
                              Map<ValueName.Behavior, List<RecordedRow>> recorded,
                              List<Disagreement> found) {
        for (Hir.ExampleRow row : ex.rows()) {
            for (Hir.With w : row.withs()) {
                Sig depSig = shapeOf(sigs, w);
                List<RecordedRow> rows =
                        depSig == null ? null : recorded.get(w.standsInFor());
                if (rows == null || !depSig.inputTypes().isEmpty()) {
                    continue;
                }
                // As with a recorded row: a `with` is written on a row, and that row is evaluated
                // where the example is checked, installing this same value. What did not finish here
                // did not finish there, and there is where it is said (E1910).
                Answered constant = within(
                        reader -> readStandIn(reader, w.value(), depSig.out(),
                                outCases(depSig.outputType())),
                        new Deadline.Work.With(wrote(w), w.value().pos())).orNull();
                if (constant == null || constant instanceof Answered.Unreadable) {
                    continue;
                }
                for (RecordedRow recordedRow : rows) {
                    if (differs(recordedRow.answer(), constant)) {
                        found.add(new Disagreement(wrote(w),
                                said(recordedRow.expected(), recordedRow.answer()),
                                said(w.value(), constant),
                                true));
                    }
                }
            }
        }
    }

    /**
     * One statement, with what it answers rendered — done here, where a disagreement is known, so a
     * row that agrees never pays for a description nobody reads.
     *
     * <p>The marker is measured off the written expression and not off the rendering. The rendering is
     * the value the encoder writes, which is a different length from the text it was written as — a
     * date is its ISO form, a qualified case name is its short one — so a marker measured from it
     * underlines the wrong columns and can run past the end of the line.
     */
    private Statement said(Hir.Expr written, Answered asserted) {
        return new Statement(written.reportedAt(), shown(asserted));
    }

    /** What an answer says, from what was already read. Rendering from the text again would build
     * the fixture a second time — running whatever helpers it applies, outside the budget the first
     * reading was held to — and could show a value other than the one that was compared. */
    private String shown(Answered asserted) {
        return switch (asserted) {
            case Answered.CaseOnly only -> only.name().name();
            case Answered.Whole whole -> rendering.describeActual(whole.fixture().value());
            case Answered.Unreadable _ ->
                    throw new IllegalStateException("nothing was read, so nothing disagreed");
        };
    }

    /** The written inputs decoded against {@code types}, or null where one would not build — that is
     * <em>E1903</em>'s to say where the row is evaluated, and a row whose inputs are not values names
     * no input to hold a stand-in against. */
    private static Object[] builtOrNull(FixtureReader fixtures, List<Hir.Expr> written,
                                        List<BoundaryInput> types) {
        Object[] values = new Object[types.size()];
        for (int i = 0; i < types.size(); i++) {
            try {
                values[i] = fixtures.built(written.get(i), types.get(i));
            } catch (FixtureException | StackExhaustedException _) {
                return null;
            }
        }
        return values;
    }

    /**
     * A fake's table, decoded — the one form both the proxy and the consistency check read.
     *
     * <p>The whole table, not the part a reader happens to need. The rule that picks a row is
     * order-sensitive, so a check that re-derived it would be free to pick another; and a table is
     * valid or it is not, so a check that built only the row it was asking about would report what a
     * fake answers for a table the proxy refuses to build at all. What the report says a fake answers
     * has to be what the fake answers.
     */
    record Standins(List<Standin.Explicit> explicit, Standin.Fallback fallback) {

        /**
         * Every row here is one {@link #answering} can return.
         *
         * <p>A row the dispatch would never reach is not part of the table's semantics, and is held
         * apart where the table is built ({@link BuiltTable}). So a reader walking {@link #explicit}
         * is walking answers the fake can give, which is what a reader of a listing has to be able to
         * assume of the rule beside it — and the assumption is held here rather than left to whoever
         * fills the list, since a listing and a rule that disagree are what this exists to prevent.
         */
        public Standins {
            explicit = List.copyOf(explicit);
            for (int i = 0; i < explicit.size(); i++) {
                if (answering(explicit.subList(0, i), null, Standin.Explicit::arguments,
                        explicit.get(i).arguments()) != null) {
                    throw new IllegalArgumentException(
                            "a table holds no row its dispatch cannot return, and the row at " + i
                                    + " states what an earlier one states");
                }
            }
        }

        /**
         * Which row answers {@code arguments}: the first explicit row stating them, and otherwise the
         * {@code _} row. Null where the table answers nothing, which is <em>E1909</em>'s to say where
         * the fake is used.
         *
         * <p>The table's own operation, not something a reader of it does. Both the proxy a row runs
         * against and the reading that holds the table to what the rows record ask it here, and a
         * second answer to "which row answers this" is exactly what <em>E1919</em> is for — so there
         * is one, and adding another means writing it somewhere it plainly does not belong.
         */
        Standin answering(Object[] arguments) {
            return answering(explicit, fallback, Standin.Explicit::arguments, arguments);
        }

        /**
         * The same rule over rows that have not been built into answers yet.
         *
         * <p>Which row answers and which row can never answer are one question, so there is one rule
         * and this is it. Deciding it needs what a row states and nothing else — an answer a row was
         * built into says nothing about whether the row is ever asked — so it is asked of what states
         * rather than of what answers, and a row whose answer has not been built (and, being
         * unreachable, never will be) is decided by the same walk as the rest.
         *
         * <p>Only the rows that name arguments are compared, and {@code states} is how this reads
         * them: the {@code _} row arrives as the fallback and is the answer where none of them
         * matched, so it is never asked what it names — which is why its type does not have to have
         * an answer to that.
         */
        static <R, E extends R> R answering(List<E> explicit, R fallback,
                                            Function<E, Object[]> states, Object[] arguments) {
            for (E stated : explicit) {
                if (sameArguments(states.apply(stated), arguments)) {
                    return stated;
                }
            }
            return fallback;
        }

        /** Whether a fake's row states the arguments a call arrived with, each by the language's
         * equality. A multi-input dependency is matched as a tuple (spec §example-fakes), so this walks them. */
        private static boolean sameArguments(Object[] arguments, Object[] key) {
            if (arguments.length != key.length) {
                return false;
            }
            for (int i = 0; i < arguments.length; i++) {
                if (!souther.runtime.Values.equal(arguments[i], key[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * What a row of a fake's table states: the arguments it answers for — none, for the {@code _}
     * row — and where it is written.
     *
     * <p>All that deciding which row answers reads. A row is written before any of it is built, and
     * whether the table can ever reach it is settled from what it states, so the answer a row was
     * built into is not part of the question and a row that answers nothing never has one built.
     */
    sealed interface Stated {

        /** Where the row is written. */
        Hir.FakeRow row();
    }

    /** A row as written, with the arguments it names read and nothing else of it built. */
    record Written(Object[] arguments, Hir.FakeRow row) implements Stated {}

    /** The {@code _} row as written. It names no arguments, so there are none here to have been
     *  read: what it answers for is every call the rows beside it do not. */
    record WroteAnything(Hir.FakeRow row) implements Stated {}

    /**
     * One row of the table the fake dispatches with: what it states, and the answer it was built
     * into.
     *
     * <p>The two rows are two types, because what may be asked of them differs. A row that names
     * arguments is the one a reader of the table enumerates, and reading what it names is reading
     * the source it was written from; the {@code _} row names none, and a reader holding one has
     * nothing of that kind to ask for. Held as one type, every such reader would have to decide for
     * itself which it had, and a reader that decided wrongly would read a row that answers for
     * anything as one that answers for no arguments — which is a row nobody can write.
     */
    sealed interface Standin extends Stated {

        /** The answer this row was built into. */
        FixtureReader.BuiltFixture answer();

        /** A row that answers for the arguments it names. */
        record Explicit(Object[] arguments, Hir.FakeRow row, FixtureReader.BuiltFixture answer)
                implements Standin {

            public Explicit {
                if (!(row.matched() instanceof Hir.Matched.Arguments)) {
                    throw new IllegalArgumentException(
                            "an explicit row names the arguments it answers for");
                }
            }

            /** Where those arguments are written, which is a question this row has an answer to
             *  because that is what made it one of these. */
            List<Hir.Expr> names() {
                return ((Hir.Matched.Arguments) row.matched()).inputs();
            }
        }

        /** The {@code _} row: what the table answers with where no row above it names the call's
         *  arguments. */
        record Fallback(Hir.FakeRow row, FixtureReader.BuiltFixture answer) implements Standin {}
    }

    /**
     * One row of a built table, as something that did not read the source can hold it.
     *
     * <p>The one reading of what a table's row states, for the two that want it: a row of the faked
     * behavior carries it to an output that has to answer the dependency itself, and a reader of the
     * examples repository is shown it beside the text. Read twice, the two would be free to observe
     * one row as two values.
     *
     * <p>Each value with where that value is written, and not with where the row is. A row states
     * one for each of the dependency's arguments and one more for the answer; told only which row a
     * value that could not be carried is on, an author is left to work out which of them nothing
     * could be made of.
     */
    static RowStatements.StandInRead.EntryRead carried(FixtureReader fixtures,
                                                       Standin.Explicit entry) {
        List<RowStatements.StandInRead.Written> arguments = new ArrayList<>();
        for (int i = 0; i < entry.arguments().length; i++) {
            arguments.add(new RowStatements.StandInRead.Written(
                    fixtures.observed(entry.arguments()[i]), entry.names().get(i).pos()));
        }
        return new RowStatements.StandInRead.EntryRead(arguments,
                new RowStatements.StandInRead.Written(fixtures.observed(entry.answer().value()),
                        entry.row().output().pos()),
                entry.row().pos());
    }

    /**
     * A row the table's dispatch can never return, and the row it returns instead.
     *
     * <p>Two ways a row is written and never reached, and the dispatch decides both: an explicit row
     * stating what an earlier explicit row states is never the first match, and a {@code _} row
     * followed by another {@code _} is never the one a table falls through to. Which of the pair is
     * the dead one is not the same in the two — the earlier explicit row answers, and the later
     * {@code _} does — so what is recorded is the pair rather than a rule for reading it off.
     *
     * <p>Where the rows are written, and not what they were built into: nothing of an unreachable row
     * is built, so there is nothing else of it to carry.
     */
    record Shadowed(Hir.FakeRow row, Hir.FakeRow answeredBy) {}

    /**
     * A fake's table as it was built: what it dispatches with, and the rows it was written with that
     * it cannot dispatch to.
     *
     * <p>Apart, because they are read by different things. {@link Standins} is the fake's semantics —
     * the proxy a row runs against and the reading that holds the table to what the rows record ask
     * it, and a reader that enumerates it is enumerating rows the fake can answer with. What was
     * written and cannot be reached is a fact about the source, which is the reporting's to say and
     * nothing else's.
     */
    record BuiltTable(Standins standins, List<Shadowed> shadowed) {

        public BuiltTable {
            shadowed = List.copyOf(shadowed);
        }
    }

    /**
     * {@code fk}'s table, decoded against {@code ins} and {@code outType}; null (with a diagnostic
     * reported) where a row states the wrong number of inputs, or any part of any row will not
     * build — one bad row is a table the fake cannot stand in with.
     *
     * <p>Shape first, then values. A row whose input count is wrong is wrong however its output
     * builds, and building the output first would run whatever helpers it applies before saying so —
     * so a table with an arity slip and a slow or non-terminating output reported the second problem
     * instead of the first, or ran out of time before reporting either.
     *
     * <p>Building only. What a table states is held to what the dependency declares where the table
     * is reported on ({@link #notKept}), which is where a diagnostic about it can be said — this is
     * called from three places and two of them discard what they are told, so holding it here would
     * be work done for an answer nobody reads.
     */
    static BuiltTable standins(FixtureReader fixtures, Hir.Fake fk, List<BoundaryInput> ins,
                                     BoundaryOutput outType, List<Diagnostic> out) {
        for (Hir.FakeRow r : fk.rows()) {
            if (r.matched() instanceof Hir.Matched.Arguments(List<Hir.Expr> inputs)
                    && inputs.size() != ins.size()) {
                out.add(unbuildableFake(r.pos(), wrote(fk), "a row has " + inputs.size()
                        + " input(s) where the dependency takes " + ins.size()));
                return null;
            }
        }
        // Which `_` the table falls through to is the last one written, and that is read off the
        // rows rather than built: a `_` with another after it answers nothing, so nothing of it is
        // built either.
        Hir.FakeRow lastDefault = fallenThroughTo(fk);
        List<Written> reachable = new ArrayList<>();
        List<Standin.Explicit> explicit = new ArrayList<>();
        List<Shadowed> shadowed = new ArrayList<>();
        Standin.Fallback fallback = null;
        // In the order the rows are written, each read as far as it is reached: what a row states,
        // then — for a row the table can return — what it answers. A row the dispatch never returns
        // is a row nothing asks for what it states, so building its answer would be work done for a
        // statement nothing reads, and where that work is what fails or overruns the table would be
        // reported for a row that is not part of it. Reading every row's arguments first and every
        // answer after would move that fault rather than remove it: a row whose answer is wrong is
        // wrong wherever a later row's arguments take their time.
        try {
            for (Hir.FakeRow r : fk.rows()) {
                if (!(r.matched() instanceof Hir.Matched.Arguments(List<Hir.Expr> inputs))) {
                    if (r != lastDefault) {
                        shadowed.add(new Shadowed(r, lastDefault));
                        continue;
                    }
                    fallback = new Standin.Fallback(r,
                            fixtures.buildFixture(r.output(), outType));
                    continue;
                }
                Object[] arguments = new Object[ins.size()];
                for (int i = 0; i < ins.size(); i++) {
                    arguments[i] = fixtures.built(inputs.get(i), ins.get(i));
                }
                Written written = new Written(arguments, r);
                // Whether the table would return this row when asked what this row states, asked of
                // the table this row is in. Not of the rows before it: a `_` written above an
                // explicit row answers where the explicit row is absent and not where it is, so a
                // reading that took any answer as a shadow would call a live row dead. And not by a
                // second comparison of arguments, which is the one thing this must not grow — the
                // rule that decides which row answers is the rule that decides which row cannot.
                List<Written> with = new ArrayList<>(reachable);
                with.add(written);
                Stated answers = Standins.answering(with,
                        lastDefault == null ? null : new WroteAnything(lastDefault),
                        Written::arguments, arguments);
                if (answers != written) {
                    shadowed.add(new Shadowed(r, answers.row()));
                    continue;
                }
                reachable.add(written);
                // A dependency that returns a sum has no single decoder; each row names one case, so
                // decode the row's output against that case's type (as an expected value is).
                explicit.add(new Standin.Explicit(arguments, r,
                        fixtures.buildFixture(r.output(), outType)));
            }
        } catch (FixtureException fe) {
            out.add(unbuildableFake(fk.pos(), wrote(fk), fe.getMessage()));
            return null;
        } catch (StackExhaustedException nt) {
            out.add(unbuildableFake(fk.pos(), wrote(fk), nt.getMessage()));
            return null;
        }
        return new BuiltTable(new Standins(explicit, fallback), shadowed);
    }

    /**
     * A row of a fake's table that the table's dispatch can never return.
     *
     * <p>Said at the row that answers nothing, and quoting the row answered instead — which is the
     * one thing an author cannot read off the text: an explicit row is shadowed by an earlier one and
     * a {@code _} row by a later one, so which of the two is dead is decided by the rule rather than
     * by the order they are written in.
     */
    static Diagnostic cannotAnswer(Hir.Fake fk, Shadowed dead) {
        return Diagnostic.at(dead.row().pos())
                .say(dead.row().matched() instanceof Hir.Matched.Anything
                        ? new ExampleMessage.ALaterDefaultRowAnswersInstead(wrote(fk))
                        : new ExampleMessage.AnEarlierRowAnswersTheseArguments(wrote(fk)))
                .secondary(souther.compiler.diag.Region.point(dead.answeredBy().pos()),
                        new ExampleMessage.TheRowThatAnswersIsHere(wrote(fk)))
                .build();
    }

    /**
     * A fake that was supplied and could not be built. A dependency nothing stands in for is a different
     * problem, and saying this as that named the dependency as its own requester and reported a fake as
     * missing where one was written (issue #206).
     */
    static Diagnostic unbuildableFake(SourcePos pos, String dependency, String reason) {
        return Diagnostic.at(pos)
                .say(new ExampleMessage.TheFakeCouldNotBeBuilt(dependency, reason))
                .build();
    }

    /**
     * A fake whose table did not finish being built, so nothing it states was checked.
     *
     * <p>A warning, where a table that will not build is an error: waiting says that this table was
     * not read, not that it is wrong, and which of the two it is cannot be told from having waited.
     * The budget is the one this wait was held to rather than the setting read back later, and it is
     * written out rather than passed as a number, which a locale would group into a budget nobody set.
     */
    private static Diagnostic unreadableFake(Hir.Fake fk, Unread why) {
        Diagnostic.Builder said = Diagnostic.at(marked(fk))
                .say(why.isDepth()
                        ? new ExampleMessage.TheTableReachedItsDepthLimit(wrote(fk),
                                why.limitShown())
                        : why.isSteps()
                                ? new ExampleMessage.TheTableSpentItsSteps(wrote(fk),
                                        why.limitShown())
                                : why.isStack()
                                        ? new ExampleMessage.TheTableRanOutOfStack(wrote(fk),
                                                why.limitShown())
                                        : new ExampleMessage.TheTableDidNotAnswer(wrote(fk),
                                                why.limitShown()));
        return (why.isDepth() ? said.hint(new ExampleMessage.TheTableRecursesTooDeeply(wrote(fk)))
                : why.isSteps()
                        ? said.hint(new ExampleMessage.TheTableGoesRoundTooManyTimes(wrote(fk)))
                        : why.isStack()
                                ? said.hint(new ExampleMessage.TheStackGotThereFirst(wrote(fk)))
                                : said.hint(
                                        new ExampleMessage
                                                .TheTableNotAnsweringIsNotTheTableBeingWrong(
                                                        wrote(fk)))).build();
    }

    // --- comparison ---------------------------------------------------------------------------

    /**
     * What someone wrote down as an answer, at the grain they wrote it.
     *
     * <p>A row may state the case and nothing under it, or the whole value, and the two sides of a
     * comparison need not have been written the same way — so what is compared is read off each side
     * once, here, rather than at every place two answers meet.
     *
     * <p>Apart from {@link FixtureReader.BuiltFixture} because the two are different things. A value
     * is built or it is not; an answer may deliberately say less than a value — a row naming a case
     * and nothing under it says only that — and only a recorded row may. Reading a stand-in as an
     * answer let a {@code with} that cannot be built at all be compared as though it named a case.
     */
    sealed interface Answered {

        /** The case, and nothing under it: a bare name denoting one. */
        record CaseOnly(TypeSymbol name) implements Answered {}

        /** The whole value. */
        record Whole(FixtureReader.BuiltFixture fixture) implements Answered {}

        /** Nothing that can be read: a fixture that did not build or did not finish, or an
         * expectation naming a case the behavior cannot answer with. What is wrong with it is
         * reported where the row is evaluated; here it states nothing to be held against. */
        record Unreadable() implements Answered {}
    }

    /**
     * What a stand-in stands in with: the whole value, always. Unreadable where it will not build,
     * which is where it stands in for nothing at all — that is reported where the fake is resolved.
     */
    private static Answered readStandIn(FixtureReader fixtures, Hir.Expr written,
                                        BoundaryOutput outType, Set<TypeSymbol> cases) {
        if (written == null || refusedCase(fixtures, written, cases)) {
            return new Answered.Unreadable();
        }
        try {
            return new Answered.Whole(fixtures.buildFixture(written, outType));
        } catch (FixtureException | StackExhaustedException _) {
            return new Answered.Unreadable();
        }
    }

    /**
     * What a recorded row asserts: the case alone where it named one and nothing under it, and the
     * whole value otherwise.
     *
     * <p>Built through the same builder a row's expectation goes through, so what is compared is what
     * the row asserts: a value a helper answered with is that value, not that value read back into
     * the form a fixture is written in and decoded again (issue #214).
     */
    private static Answered readExpected(FixtureReader fixtures, Hir.Expr written,
                                         BoundaryOutput outType, Set<TypeSymbol> cases) {
        if (written == null || refusedCase(fixtures, written, cases)) {
            return new Answered.Unreadable();
        }
        if (fixtures.caseOnly(written) != null) {
            return new Answered.CaseOnly(fixtures.constructedCase(written));
        }
        try {
            return new Answered.Whole(fixtures.buildFixture(written, outType));
        } catch (FixtureException | StackExhaustedException _) {
            return new Answered.Unreadable();
        }
    }

    /** Whether {@code written} names a case the position cannot hold — <em>E1904</em> where the row
     * is evaluated, and nothing about the behavior it was written for. */
    private static boolean refusedCase(FixtureReader fixtures, Hir.Expr written,
                                       Set<TypeSymbol> cases) {
        TypeSymbol named = fixtures.constructedCase(written);
        return named != null && !cases.isEmpty() && !cases.contains(named);
    }

    /**
     * Whether two written answers to one input state different things.
     *
     * <p>Where either names a case and nothing more, the comparison is on the case: there is no value
     * under it to compare, and holding a full value against it would report a disagreement the row
     * never stated. The case compared is the one the value turned out to be, resolved to the type
     * this module knows it under — not the class it arrives in, which does not tell one module's
     * {@code Missing} from another's.
     *
     * <p>Where either states nothing there is nothing to disagree with. Read against a written
     * answer, silence is not a contradiction.
     */
    private static boolean differs(Answered left, Answered right) {
        if (left instanceof Answered.Unreadable || right instanceof Answered.Unreadable) {
            return false;
        }
        if (left instanceof Answered.Whole l && right instanceof Answered.Whole r) {
            return !souther.runtime.Values.equal(l.fixture().value(), r.fixture().value());
        }
        TypeSymbol one = caseOf(left);
        TypeSymbol other = caseOf(right);
        return one != null && other != null && !one.equals(other);
    }

    /** The case an assertion is about, or null where nothing says. */
    private static TypeSymbol caseOf(Answered a) {
        return switch (a) {
            case Answered.CaseOnly c -> c.name();
            case Answered.Whole w -> w.fixture().caseName();
            case Answered.Unreadable _ -> null;
        };
    }
}
