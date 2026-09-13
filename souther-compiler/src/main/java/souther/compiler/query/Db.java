package souther.compiler.query;

import souther.compiler.check.DeclarationReadings;
import souther.compiler.check.LentReadings;
import souther.compiler.check.ResolvedSymbols;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.StoreWork;
import souther.compiler.check.Symbols;
import souther.compiler.check.TheCompilationsSources;
import souther.compiler.source.SourceId;
import souther.compiler.types.TypeKey;
import souther.compiler.values.StringFacts;
import souther.compiler.values.StringMachineAnswers;

import java.util.ArrayDeque;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.diag.Primary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The store a compilation's questions are asked of: it answers a {@link Key} by running it once,
 * keeps the answer, and hands the same answer to everyone who asks again.
 *
 * <p>It also watches. While a key is being answered, every key it asks is recorded against it, so
 * afterwards the graph knows what each answer was built from. That is what makes a question
 * depending on itself visible at the moment it happens rather than as a stack overflow, and it is
 * what lets an edit be absorbed: change a source, and the only answers recomputed are the ones that
 * read something that changed.
 *
 * <p>Recomputing is not the same as changing. An answer that comes out equal to the one it replaces
 * leaves everything that read it alone — which is what keeps an edit local. Retyping a helper's body
 * changes that module's classes; it does not change what the module declares, so nothing that
 * imports it is looked at again.
 *
 * <p>That rests on the answers being values, and two of them carry it. {@link Front.Layout} is what
 * stops an edit to any source from reaching the whole workspace: every parse feeds it, and it comes
 * out the same. {@link Names.Declarations} is what stops an edit at the module boundary: an importer
 * builds against what a module declares, not against its bodies. Both are maps of records, and
 * {@code IncrementalCompilationTest} pins both.
 *
 * <p>A module's classes carry it too. Each is a {@link souther.compiler.jvm.ClassFileImage}, which
 * is what its bytes say, so a module regenerated to what it already was leaves the examples that
 * load it alone. What that does not reach is an example of one module and an edit to another it
 * merely imports: the classes it is run against are the ones its module reaches, so they change
 * when any of those does. Narrowing that to the classes an example actually loads is per-definition
 * work and is not here.
 *
 * <p>Two rules run through the rest of them, and an answer can break either one.
 *
 * <p>A computed node is a value. What {@code equals} says is what stops work, so such an answer says
 * what it means and not where it came from; one that never equals the answer it replaces leaves
 * nothing downstream of it ever kept, while every test of what the compiler says stays green. That
 * rules out a kind of thing and not a missing method: an object that reads this store when it is
 * asked — a registry, a scope over one, a loader — is the same as another when the store is, which
 * is where it came from. Those are built inside a {@code compute} and used there, which is also what
 * makes their reads land on the question being answered. {@link Names#derivedSymbols} is one, handed
 * out and not kept.
 *
 * <p>A supplied node is whatever was supplied. An {@link Input} is not computed and its answer is
 * what somebody handed in, so what its equality decides is not whether a compile came to the same
 * thing — it is whether the outside changed. A way of running something may be handed in that way
 * and there is no compute to build it inside; what it costs is that a new one reads as a change,
 * which is a conservative answer about the outside rather than a wrong answer about a compile.
 *
 * <p>{@code EverythingAnAnswerHoldsMeansSomethingTest} is what says which answers still break the
 * first rule, and {@code EveryAnswerThisCompilerDeclaresIsSettledTest} what says which of the two a
 * question is read under.
 *
 * <p>An edge is what its consumer means. A collection gathered per module is an index, and a
 * question asked per definition depends on the entries it reaches rather than on the index. Read
 * whole, an index hands the finer question the coarser one's identity: an edit anywhere in the
 * module — or in a module it imports — arrives as an edit to every definition in it, and again no
 * test of what the compiler answers can see the difference. Keeping the index is fine, and so is
 * reading one to answer a question about a single entry — {@link Bodies.Stated} does exactly that.
 * What a per-definition question may not do is take the index as its own dependency.
 *
 * <p>Which does not mean every producer has to be split. What the consumer reads has to stop where
 * its meaning stops, and a key between the two is where that happens: the broad answer is
 * recomputed as often as its own inputs move, and the cut is what the consumer depends on.
 * {@link Names.Meanings} is what a module's names mean, cut from the whole assembly they are read
 * off — so declaring a behavior, which adds a value name, stops there. {@link Bodies.CalleeSigsForBody}
 * is the signatures one body names, cut from its module's index of everything callable in it.
 * {@link Bodies.ContractsForBody} is a body's contracts asked entry by entry.
 *
 * <p>A cut is one way to stop an index short of a reader. The other is to hand the reader the
 * questions rather than the table, so that the index is asked for when it is read and not when it
 * is built — which is what {@link souther.compiler.check.Registry} does for a module's declarations
 * and {@link souther.compiler.check.Denoting} for what its names mean. It answers what a cut cannot
 * here: which of a module's meanings a body needs is a question about what a body's scope is, and
 * nothing has to decide it, because a body that reads none of them depends on none of them and the
 * one report that reads every name in sight depends on every name in sight. That is issue #835,
 * and {@code IncrementalCompilationTest} holds both halves.
 *
 * <p>Not everything a store holds is an answer. What runs its programs is one such thing, and so is
 * what a reading of a declaration borrows from ({@link #readings()}): the reading of a declaration
 * that one question made is handed to the next question that would have made it, for as long as the
 * revision it was made under is the current one. Neither is compared and neither decides what is
 * recomputed — that is settled by the memos, before either is asked anything.
 *
 * <p>One store is one workspace over time, not one compile. It is not thread-safe and does not need
 * to be: the work inside a compile is a graph walk, not a set of independent jobs.
 */
public final class Db implements StoreWork {

    /**
     * What is known about one key.
     *
     * @param answer the answer as of {@code verifiedAt}
     * @param verifiedAt the revision this answer was last known to be current at
     * @param changedAt the revision the answer last came out different at
     * @param reads what answering it read, in the order it read them
     */
    private record Memo(Answer<?> answer, long verifiedAt, long changedAt, Set<Key<?>> reads) {

        Memo verifiedAt(long revision) {
            return new Memo(answer, revision, changedAt, reads);
        }
    }

    /** Bumped by every input that is given a value it did not already have. */
    private long revision;

    /**
     * Which world the answers in hand are of. What it counts is not how much has been asked but
     * how often the outside changed, so two questions answered at one revision were answered of
     * one world — which is what {@link #readings()} lends work across and nothing else here needs.
     */
    long revision() {
        return revision;
    }

    /**
     * What a reading of a declaration borrows from, for this store.
     *
     * <p>Beside the memos, as what runs the programs is, and for the same reason: it is not a value
     * and this store never compares it. What it holds is work that has been done — the reading of a
     * declaration that a question of this store already made — and handing that to the next reader
     * is not an answer being kept. Which questions are recomputed is settled by the memos and by
     * them alone, before this is asked anything.
     *
     * <p>One per store, made when it is first asked for, because sharing among readers is the whole
     * of what it does and two of them would share nothing.
     */
    public DeclarationReadings readings() {
        if (readings == null) {
            readings = new LentReadings(this::machinesOf, this::revision, this);
        }
        return readings;
    }

    private DeclarationReadings readings;

    /**
     * Where this store's modules' rules are read from.
     *
     * <p>Kept here because it is this compilation's, and made from what this compilation answers:
     * a module's scope is read off the store when it is asked for, and the clauses are read from
     * the one place a declaration's are answered. Nothing about a source is taken from whoever asks
     * for one, so no reader can bring parts of its own and have them read as the compilation's.
     */
    RuleReadingSource ruleReadingFor(String module) {
        if (sources == null) {
            sources = new TheCompilationsSources(this::scopeOf,
                    named -> ask(new Shapes.ClausesExpandedFor(named)).value(),
                    Shapes.publishedDeclarations(this),
                    Shapes.declarationKinds(this),
                    Shapes.declarationNewtypes(this),
                    Shapes.newtypeInners(this),
                    Shapes.fieldBindings(this),
                    Shapes.clauseLocations(this));
        }
        return sources.of(module);
    }

    private TheCompilationsSources sources;

    /** The scope {@code module}'s names resolve in, or null where this compilation resolves no such
     *  module. Asked once: what it answers is assembled where it is asked for. */
    private Symbols scopeOf(String module) {
        Answer<ResolvedSymbols> scope = Names.resolvedSymbols(this, module);
        return scope.present() ? scope.value() : null;
    }


    /** What this store answers about {@code declaration}'s string machines, for a reading to
     *  borrow — nothing to borrow, where it has no answer for the declaration at all. Either way
     *  the reading keeps what it builds: that is what its own counterfactual is handed. */
    private StringMachineAnswers machinesOf(TypeKey declaration) {
        Answer<StringFacts> facts = ask(new Machines.OfDeclaration(declaration));
        return facts.present()
                ? StringMachineAnswers.borrowing(facts.value(), readings().extents())
                : StringMachineAnswers.unborrowed(readings().extents());
    }

    private final Map<Key<?>, Memo> memos = new HashMap<>();
    /**
     * Whether whoever set this walk going has since stopped wanting the answer. Asked where a
     * question is about to be answered and nowhere else, so a walk stops between two questions and
     * never inside one.
     *
     * <p>Being asked to stop is not an input changing. The answers already reached are answers of
     * this revision and stay: nothing can move an input while a walk is running, because the walk is
     * what the one thread holding this store is doing. Only the key that never returned a value is
     * left without a memo, which is what {@link #ask} does with a throw either way.
     */
    private Abandonment abandonment = Abandonment.NEVER;
    /** The keys being answered right now, outermost first — the chain a cycle is found on. */
    private final Set<Key<?>> inProgress = new LinkedHashSet<>();
    /** The reads of each in-progress key, innermost frame last. */
    private final Deque<Set<Key<?>>> frames = new ArrayDeque<>();
    /**
     * Keys whose current answer was reached through a cycle. A cyclic answer depends on where the
     * cycle was entered, so keeping it would make one caller's answer depend on whether another
     * asked first; the whole chain in flight is discarded, not only the part inside the cycle,
     * because an answer built from a cyclic one is as entry-dependent as the cyclic one.
     *
     * <p>That is more than the cycle, and it costs: while one exists, every key that reached it is
     * recomputed on every ask. It is affordable because nothing in a compile is supposed to get
     * here — modules that name each other are found by {@link Names.Cycles} and stopped before any
     * question is asked that would answer itself.
     */
    private final Set<Key<?>> throughCycle = new HashSet<>();
    /** Every key that has ever reported something, in the order it first did — the order reports
     * are read back in. */
    private final List<Key<?>> spoke = new ArrayList<>();
    private final Set<Key<?>> hasSpoken = new LinkedHashSet<>();

    /**
     * What runs this compilation's programs, for the questions the language can only answer by
     * running one.
     *
     * <p>Beside the memos and not in them. An answer is a value — this store stops work by
     * comparing one with the one it replaces, and everything under an answer has to mean something
     * by {@code equals} for that to hold. What runs a program is not a value and has no business
     * being compared: it is a term of the compilation, set where the compilation is set up, and it
     * is the same one for as long as the store lives.
     */
    private souther.compiler.execute.ProgramExecution execution;

    /**
     * Names what runs this compilation's programs, and it may be named once.
     *
     * <p>Set once because nothing here would notice it changing. What a key read to run a program
     * is not a read this store recorded — the runner is beside the memos, not in them — so an
     * answer worked out under one runner is not invalidated by a second being named, and the store
     * would go on handing out the first one's answers for as long as they stood. What is held here
     * is that there is never a second one; naming it before anything is asked is the caller's to
     * get right, and a compilation does it where it is set up.
     *
     * @throws IllegalStateException where one has already been named
     */
    public Db running(souther.compiler.execute.ProgramExecution execution) {
        if (execution == null) {
            throw new IllegalArgumentException("a compilation runs its programs with something");
        }
        if (this.execution != null) {
            throw new IllegalStateException("this store already has something that runs its"
                    + " programs, and answers have been worked out with it");
        }
        this.execution = execution;
        return this;
    }

    /**
     * What runs this compilation's programs.
     *
     * <p>Raises where nothing was named. A store built by hand and asked a question that has to run
     * the program is a caller that set up half a compilation; saying so here names what is missing,
     * where the alternative is a null reference thrown from inside whatever was about to run.
     */
    public souther.compiler.execute.ProgramExecution execution() {
        if (execution == null) {
            throw new IllegalStateException("nothing was named to run this compilation's programs");
        }
        return execution;
    }

    /**
     * Gives an input its value. An input set to what it already held changes nothing, so a caller
     * may hand over a whole workspace on every keystroke. Returns this store, so a compilation can
     * be set up in one expression.
     */
    public <T> Db set(Input<T> key, T value) {
        Answer<T> now = Answer.of(value);
        Memo known = memos.get(key);
        if (known != null && known.answer().equals(now)) {
            return this;
        }
        revision++;
        memos.put(key, new Memo(now, revision, revision, Set.of()));
        return this;
    }

    /**
     * Drops everything kept about a source that is no longer part of this compilation — its text,
     * and every answer that was about it or about the module it declared.
     *
     * <p>A store lives as long as the workspace does, so what it keeps about a file it will never be
     * asked about again is held for nothing. Dropping an answer is always safe: the next question
     * that wants it computes it.
     */
    public void forget(SourceId sourceId, String moduleName) {
        set(new Front.Text(sourceId), null);
        // Dropping by module name is exact, not approximate. Every question about a module reads its
        // declaring source through the workspace layout, and the layout names one source per module
        // however many claim it — so while this source was the module, every answer about that module
        // was an answer about this source. A source that claimed a name it did not get is passed a
        // null module name and takes nothing else with it.
        memos.keySet().removeIf(key -> sourceId.equals(key.sourceId())
                || (moduleName != null && moduleName.equals(key.module())));
        hasSpoken.removeIf(key -> !memos.containsKey(key));
        spoke.removeIf(key -> !memos.containsKey(key));
    }

    /**
     * Says what makes a walk of this store stop short: while {@code asked} answers true, the next
     * question that has to be answered raises {@link Abandoned} instead.
     *
     * <p>What is left behind is what was answered. A key whose computation was cut through leaves no
     * memo — it never returned a value — while every key that answered inside it keeps one, because
     * an answer of this revision is an answer of this revision however the walk that wanted it
     * ended. The next walk pays for what this one did not finish and for nothing else.
     */
    public void abandonWhen(Abandonment abandonment) {
        this.abandonment = abandonment;
    }

    /** Answers {@code key}, computing it if nothing kept from before still holds. */
    @SuppressWarnings("unchecked")
    public <T> Answer<T> ask(Key<T> key) {
        recordRead(key);
        Memo memo = memos.get(key);
        if (memo != null && memo.verifiedAt() == revision) {
            return (Answer<T>) memo.answer();
        }
        if (inProgress.contains(key)) {
            // Everything in flight has now seen an answer that depends on where it was entered.
            throughCycle.addAll(inProgress);
            return key.onCycle(List.copyOf(inProgress));
        }
        // Before either of the two things that cost anything, and after the one that does not. An
        // answer already verified at this revision is a lookup and a return; checking what is left
        // is a walk of everything the answer read, and working one out is a walk of whatever it
        // reaches. A store told to stop while it is re-verifying a graph nothing moved is a store
        // being asked its cheapest question over and over, and that is the question an edit asks.
        abandonment.stopIfAsked();
        if (memo != null && stillHolds(memo)) {
            memos.put(key, memo.verifiedAt(revision));
            return (Answer<T>) memo.answer();
        }
        inProgress.add(key);
        frames.push(new LinkedHashSet<>());
        Answer<T> answer;
        Set<Key<?>> read;
        // This key leaves nothing of itself behind, whether it answered or threw: not its frame, not
        // its place in the chain, not a cycle mark. A compile error is a value, so a throw is an
        // internal fault, and a store kept across edits would otherwise never keep this key again.
        //
        // What a dependency answered before the throw is kept, and should be: a query is a function
        // of its inputs, so an answer that was reached is as good as any other. Only the key that
        // failed is unfinished.
        boolean reachedThroughCycle = false;
        try {
            answer = key.compute(this);
        } finally {
            // In the order they were read, so a caller walking the graph for reports meets them in
            // the order the work happened. An unordered copy here makes the first error a module
            // reports depend on hash order.
            read = Collections.unmodifiableSet(frames.pop());
            inProgress.remove(key);
            reachedThroughCycle = throughCycle.remove(key);
        }
        if (!reachedThroughCycle) {
            long changedAt = memo != null && memo.answer().equals(answer)
                    ? memo.changedAt() : revision;
            memos.put(key, new Memo(answer, revision, changedAt, read));
        }
        if (!answer.reports().isEmpty() && hasSpoken.add(key)) {
            spoke.add(key);
        }
        return answer;
    }

    /**
     * Whether {@code memo}'s answer can be kept: everything it read still answers what it did when
     * this answer was made. Asking each of them is what settles that, and each of those may settle
     * the same way without running anything.
     *
     * <p>Told to stop, it stops where it has got to. What it walks is a graph and what it does at
     * each step may be nothing at all — a dependency another question has already verified is a
     * lookup — so the step is this loop's, and asking is left to this loop rather than to what it
     * calls.
     */
    private boolean stillHolds(Memo memo) {
        // Verification is not a read: a key being checked is not a dependency of whoever happened to
        // ask at the moment it was checked.
        frames.push(new LinkedHashSet<>());
        try {
            for (Key<?> read : memo.reads()) {
                // Here, and not left to the ask below. A dependency already verified at this
                // revision is answered before that one gets as far as asking, so a walk over a graph
                // that another question has already been through would go the whole way without
                // being asked once — which is the walk this is, most of the time.
                abandonment.stopIfAsked();
                ask(read);
                Memo dependency = memos.get(read);
                if (dependency == null || dependency.changedAt() > memo.verifiedAt()) {
                    return false;
                }
            }
        } finally {
            frames.pop();
        }
        return true;
    }

    /**
     * Everything this compilation now has to report, in the order the keys first reported it.
     *
     * <p>Only questions asked since the last input changed are read. A question that was asked and
     * recomputed contributes what it says now, so a problem that was fixed is not still listed —
     * and a question that stopped being asked contributes nothing, because there is no longer
     * anything that wants to know. Deleting the last row of an {@code examples for} file is the
     * second case: nothing asks whether that file's rows hold any more, so nothing recomputes the
     * answer, and reading it would put a failure on a line that is not there.
     *
     * <p>Being asked is what {@code verifiedAt} records. Answering a key sets it, and so does finding
     * that a kept answer still holds, so every key reached since the last input change carries this
     * revision and every key that was not reached carries an older one.
     *
     * <p>"Since the last input change" is not "in the last round of asking", and the two come apart
     * if a caller asks for less than it did before without changing anything: what the wider round
     * reached still counts as reached. Every caller here asks for the whole compilation, so it does
     * not arise — but a caller that asked only about the file in front of the author would need a
     * generation of its own, separate from the input revision.
     */
    public List<Found> allReports() {
        Map<Told, Found> found = new LinkedHashMap<>();
        for (Key<?> key : spoke) {
            abandonment.stopIfAsked();
            Memo memo = memos.get(key);
            if (memo == null || memo.verifiedAt() != revision) {
                continue;
            }
            for (Report report : memo.answer().reports()) {
                // One problem is one diagnostic, whichever questions found it. Two of them can, and
                // legitimately: a helper is checked on its own and again in each body it is expanded
                // into, and both are looking at the same line. The first to say it is the one that
                // says it, so the order is the order the work happened in.
                Found one = new Found(key.module(), key.sourceId(), report);
                found.putIfAbsent(new Told(key.module(), one.claimedSourceId(), report.problem()),
                        one);
            }
        }
        return new ArrayList<>(found.values());
    }

    /** One thing the author is told, wherever it was found: a problem, on a file. */
    private record Told(String module, SourceId sourceId, Diagnostic.Identity problem) {}

    /** What {@code key} read while it was answered, empty if it has not been asked. */
    public Set<Key<?>> dependenciesOf(Key<?> key) {
        Memo memo = memos.get(key);
        return memo == null ? Set.of() : memo.reads();
    }

    /** Whether {@code key}'s answer is being kept — it has been asked, and was not reached through
     * a cycle. */
    public boolean isComputed(Key<?> key) {
        return memos.containsKey(key);
    }

    /**
     * Every answer being kept, by the question it answers.
     *
     * <p>For the one reader that is about the store rather than about a compile: whether the answers
     * it keeps are values is a question about all of them at once, and there is no question to ask
     * that would enumerate them. Nothing in a compile reads this — a pass wanting an answer asks for
     * the one it wants.
     */
    Map<Key<?>, Answer<?>> everyAnswer() {
        Map<Key<?>, Answer<?>> out = new LinkedHashMap<>();
        memos.forEach((key, memo) -> out.put(key, memo.answer()));
        return out;
    }

    /**
     * A report, the module it is about, and the source the key that found it names — either of which
     * may be null.
     *
     * <p>Which source the report is anchored in is read off this rather than stored beside it, so
     * there is one answer and not two that can come apart. A module named but no source is the last
     * fallback, and only a caller holding the module layout can apply it —
     * {@link Compilation#sourceIdOf} is where that answer is finished.
     *
     * <p>Where the report is said is a further question and not this one. A problem written in more
     * than one file is said in each, which the check that found it states about the regions it
     * points at ({@link souther.compiler.diag.msg.FindingRegion}) and nothing here knows;
     * {@link Compilation#publishSourceIdsOf(Found)} is what reads the two together.
     */
    public record Found(String module, SourceId sourceId, Report report) {

        /**
         * The source this report claims, before a compile decides whether it has it: the one the
         * report's primary position was read from, else the one the key asked about. Null when
         * neither says, which leaves the module's own source as the last word — a fallback only a
         * caller that knows the module layout can apply.
         *
         * <p>The position comes before the key, and that ordering is the whole of what a reader
         * needs. A key says which input was asked about; a position says where the caret sits, and
         * the line under the caret is quoted out of the file this names. Where the two disagree,
         * answering with the key's shows a reader a line they did not write — which is what a
         * question asked about a module whose rows were written in an attached {@code examples for}
         * file did.
         *
         * <p>Nothing a report carries beside its position is read here. A report used to be able to
         * name its own file, and the two sites that did read that name off a value holding it beside
         * the place — so the answer was the position's, spelled somewhere else, with nothing keeping
         * the two the same.
         *
         * <p>Anchored, not owned. What this settles is the file the report is filed under and quoted
         * from; whether the problem is also written in some other file is a question about the
         * regions it points at, and is asked of them.
         */
        public SourceId claimedSourceId() {
            return switch (report.diagnostic().primary()) {
                case Primary.InSource(DiagnosticPlace.InSource place) -> place.source();
                // Everything else is filed under the source this answer was being read for. Which is
                // a choice this makes and not something the report said: a place in a text nobody
                // named, a report about code out of sight and one about no stretch of text at all
                // have no file of their own, and a report has to be filed somewhere for anybody to
                // be shown it.
                case Primary.InAnUnnamedText _, Primary.Unavailable _, Primary.Nowhere _ -> sourceId;
            };
        }
    }

    /**
     * Does work whose result another question may be handed, and answers with what this store was
     * asked while it was done.
     *
     * <p>The reads are recorded for the question being answered, as they would be had it done the
     * work itself — it did, this once — and they are handed back so that the next question to be
     * given the result can be recorded as having read them too. Without that, work shared between
     * two questions would leave the second kept over an edit to what doing it read.
     */
    @Override
    public <T> Made<T> watching(Supplier<T> work) {
        frames.push(new LinkedHashSet<>());
        Set<Key<?>> read;
        T made;
        try {
            made = work.get();
        } finally {
            read = Set.copyOf(frames.pop());
        }
        read.forEach(this::recordRead);
        return new Made<>(made, () -> read.forEach(this::recordRead));
    }

    private void recordRead(Key<?> key) {
        Set<Key<?>> frame = frames.peek();
        if (frame != null) {
            frame.add(key);
        }
    }
}
