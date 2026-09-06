package souther.compiler.query;

import souther.compiler.execute.jvm.JvmExampleDeadlines;
import souther.compiler.execute.jvm.JvmProgramImages;
import souther.compiler.DefaultStdlib;
import souther.compiler.source.SourceId;

import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.LabeledRegion;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.Located;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.Primary;
import souther.compiler.diag.ReportContext;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.diag.WhereCodeIsWritten;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One compile, as a set of questions that can be asked of it.
 *
 * <p>A caller sets the sources and then asks: for the classes, for a module's errors, for what a
 * name denotes. What it does with an error is its own — the batch compiler raises them together and
 * stops, an editor publishes them all per file and carries on — and that decision is the only thing
 * that differs between them. Nothing here runs a pipeline; asking a question is what makes the work
 * that answers it happen, and only that work.
 */
public final class Compilation {

    private final Db db = new Db();
    /**
     * Where the JVM implementation gets this compilation's classes.
     *
     * <p>One of them, and held here rather than made where it is wanted. Two adapters over one store
     * would read the same answers today and still be wrong: the run that decides whether the rows
     * hold and the run a Java binding drives have to be against one program, and what makes them one
     * is that there is one thing they both ask. It is the reason {@link #loader()} keeps the loader
     * it made rather than making another over the same classes.
     *
     * <p>Named for the machine it is of. What a compilation holds for the JVM is not the general
     * shape of holding something for a backend, and a second one would want the name.
     */
    private final JvmProgramImages jvmProgramImages = new QueryJvmProgramImages(db);
    /**
     * And what this compilation's own rows are run under.
     *
     * <p>Not one of it for the reason the images are one of them. What every run of these rows has
     * to agree on is the program and the terms: two runs against different classes are not two runs
     * of one model, and two runs under different limits do not say one thing about it. How a run
     * keeps those terms is that run's, and a run outside the compile keeps them differently because
     * it is somewhere else — a row a Java binding drives reaches an implementation that answers out
     * of the caller's world, which no row this compile decides does. So this is the compile's
     * arrangement and not the compilation's only one, and a caller running rows of its own brings
     * the arrangement its world needs rather than taking this over.
     */
    private final ChosenJvmExampleDeadlines jvmExampleDeadlines = new ChosenJvmExampleDeadlines();
    /** Which source each id was, for a caller that identifies sources by index. */
    private final Map<SourceId, Integer> indexOfId = new LinkedHashMap<>();
    /** The sources this compilation currently has, so one that goes away can be forgotten. */
    private final Set<SourceId> held = new LinkedHashSet<>();
    /** The loader over the classes as they were when it was made, which says for itself whether the
     *  one it has is still a loader over what there is. */
    private final LoaderOverClasses loader = new LoaderOverClasses();

    private Compilation() {
        // Read now, so this compilation is held to what the settings said when it started rather than
        // to what the first compile in this JVM happened to read. A caller with a reason of its own
        // says so with withEvaluationPolicy.
        db.set(new Front.Policy(), souther.compiler.execute.EvaluationPolicy.fromSettings());
        // The one place a reading policy is made. Everything that reads a declaration is handed
        // this one, so a declaration read twice in one compilation is read the same way both times.
        db.set(new Front.Reading(), Front.Reading.STANDARD);
        // And the one place this compilation's standard library is settled. Read here so that every
        // name resolved for it is resolved against one library, rather than against whichever one
        // each reader reached for.
        db.set(new Front.Library(), DefaultStdlib.get());
        // And the one place an adequacy budget is made. Every measure of one behavior is handed
        // this one, so a behavior measured twice in one compilation is measured under the same
        // limits both times.
        db.set(new Front.Adequacy(), Front.Adequacy.STANDARD);
        // And the one place the implementation that runs this compile's programs is named. What
        // decides whether a constant construction holds and whether a row holds has to run the
        // program, and ADR-0032 settles that it is run as the program that will ship. Which
        // implementation that is belongs here, so that nothing deciding whether the language
        // accepts a program names one.
        db.running(new souther.compiler.execute.jvm.JvmProgramExecution(jvmProgramImages,
                jvmExampleDeadlines));
    }

    /**
     * Where the JVM gets this compilation's classes, for a caller binding Java to its rows.
     *
     * <p>What acceptance asks is asked of a capability and reaches it through the store, because a
     * query key is handed nothing but the store. This is the other side of the same wiring: a caller
     * that brings its own implementation of an injected behavior holds the compilation itself, so it
     * is handed this rather than made to go through the store to reach it.
     */
    public JvmProgramImages jvmProgramImages() {
        return jvmProgramImages;
    }

    /** A compile of several sources identified by their position, the way a build hands them over.
     * An import naming no module among them is resolved against {@code path}. */
    public static Compilation ofSources(List<String> sources, ModulePath path) {
        Compilation c = new Compilation();
        List<SourceId> ids = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            SourceId id = idOfSourceIndex(i);
            ids.add(id);
            c.indexOfId.put(id, i);
            c.db.set(new Front.Text(id), sources.get(i));
        }
        c.db.set(new Front.Ids(), List.copyOf(ids));
        c.db.set(new Front.Path(), path);
        return c;
    }

    /**
     * The source id a compile handed a plain list of sources gives the {@code i}-th of them.
     *
     * <p>A build identifies its sources by where they are in the list it passed, and a result carries
     * that id back out. Both ends have to agree on it, so it is written once here rather than assumed
     * at each of them.
     *
     * <p>An id, and not a name. Nothing downstream may print one at a person or read a path out of
     * one: what to call a file is the caller's answer and depends on which others are in front of the
     * reader, so a renderer asks for it. A report printed this number where a file name belongs for
     * as long as that was left unsaid.
     */
    public static SourceId idOfSourceIndex(int i) {
        return new SourceId(String.valueOf(i));
    }

    /** A compile of one source. A source with no {@code module} header takes
     * {@code defaultModuleName}, which a set of linked sources cannot allow: a module reached by an
     * import has to be named. */
    public static Compilation ofSource(String source, String defaultModuleName) {
        return ofSource(source, defaultModuleName, ModulePath.EMPTY);
    }

    /**
     * As {@link #ofSource(String, String)}, resolving an import that names no module here against
     * {@code path} — the compiled modules of the projects this one depends on.
     *
     * <p>One source is how many a caller has, not how many modules it may reach. {@code run} is
     * handed a single file and used to be given the empty path with it, which made every import of
     * another user module an unknown one; what a caller can name and what the compile can resolve
     * are separate, and this is the second of them.
     */
    public static Compilation ofSource(String source, String defaultModuleName, ModulePath path) {
        Compilation c = ofSources(List.of(source), path);
        c.db.set(new Front.DefaultName(), defaultModuleName);
        return c;
    }

    /**
     * A compile of a workspace, where each source is identified by the caller — a document URI.
     * {@code broken} names the modules whose sources the caller held back because they will not
     * parse, so an importer of one is left alone rather than told the module is unknown.
     */
    public static Compilation ofDocuments(Map<String, String> byId, Set<String> broken,
                                          ModulePath path) {
        Compilation c = new Compilation();
        for (Map.Entry<String, String> e : byId.entrySet()) {
            c.db.set(new Front.Text(new SourceId(e.getKey())), e.getValue());
        }
        c.db.set(new Front.Ids(), byId.keySet().stream().map(SourceId::new).toList());
        c.db.set(new Front.Broken(), Set.copyOf(broken));
        c.db.set(new Front.Path(), path);
        c.held.addAll(byId.keySet().stream().map(SourceId::new).toList());
        return c;
    }

    /**
     * The same workspace after an edit: every source as it now reads, and which modules the caller
     * is still holding back.
     *
     * <p>Nothing is thrown away. A source whose text is unchanged is unchanged, and an answer that
     * comes out the same as before leaves everything that read it alone — so what this costs is what
     * the edit actually reached.
     */
    public void update(Map<String, String> byId, Set<String> broken) {
        // A source that is gone is forgotten, along with the module it declared. Which module that
        // was has to be read before the layout is told the source is gone.
        for (SourceId gone : List.copyOf(held)) {
            if (!byId.containsKey(gone.value())) {
                db.forget(gone, moduleDeclaredBy(gone));
                held.remove(gone);
            }
        }
        for (Map.Entry<String, String> e : byId.entrySet()) {
            db.set(new Front.Text(new SourceId(e.getKey())), e.getValue());
        }
        db.set(new Front.Ids(), byId.keySet().stream().map(SourceId::new).toList());
        db.set(new Front.Broken(), Set.copyOf(broken));
        held.addAll(byId.keySet().stream().map(SourceId::new).toList());
    }

    /** The module {@code id} declares, as this compilation currently has it. */
    private String moduleDeclaredBy(SourceId id) {
        Front.Layout.Of layout = db.ask(new Front.Layout()).value();
        if (layout == null) {
            return null;
        }
        for (Map.Entry<String, SourceId> e : layout.idOfModule().entrySet()) {
            if (e.getValue().equals(id)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** A compile of one of the compiler's own core sources, which may take a reserved name. */
    public static Compilation ofCoreSource(String source) {
        Compilation c = ofSource(source, null);
        c.db.set(new Front.Core(), Boolean.TRUE);
        return c;
    }

    /** Every class this compilation generated. */
    public Map<String, ClassFileImage> classes() {
        Map<String, ClassFileImage> all = db.ask(new Output.All()).value();
        return all == null ? Map.of() : all;
    }

    /**
     * A loader over what this compilation produced and the modules it was compiled against — the one
     * a caller runs the generated code with.
     *
     * <p>{@link #classes()} alone is not enough to run any of it. A module resolved on the path is
     * read for its declarations and is deliberately not re-emitted here, so a behavior of this
     * compilation that builds one of its values has no class for it in this map; the path's classes
     * have to be under the map rather than beside it. Which order that is, and why a loader that
     * delegates first would answer with a stale build instead, is settled once for every caller —
     * the same composition the compile-time evaluation runs against.
     *
     * <p>The same loader for as long as the classes are the same classes. A class is its binary name
     * and the loader that defined it, so two loaders over one compilation are two definitions of
     * every type it generated, and a value made under the first is not a value of the second's
     * classes. What that looks like is a model answering wrongly: a decoded key stops equalling the
     * key a behavior looks up, so the lookup misses and the behavior answers with its default. There
     * is no exception and nothing that mentions loading. The path's classes divide the same way —
     * {@link ModulePath#loader} defines them afresh each time it is asked — so it is the composed
     * loader that is kept and not one layer of it.
     *
     * <p>Kept against the classes rather than for the life of this compilation, because an edit
     * makes a different program. A loader held across an edit would be one that cannot see it,
     * which is the first fault the other way round.
     *
     * <p>Whether it still has one is {@link LoaderOverClasses}'s, which is where what "the same
     * classes" means is written and what is held to it. Not a condition here: a rule inside the
     * method that hands out a loader can only be reached through whatever produces two class sets,
     * and the one that matters — a module built again to what it already was — is the one no
     * ordinary edit reaches.
     *
     * <p>What decides it is the classes and nothing else, because a compilation's module path is
     * settled where it is made and never after. Were one ever able to be given another path, its
     * parent loader would move under classes that had not, and what a loader is kept against would
     * have to be the two of them together.
     */
    public ClassLoader loader() {
        Map<String, ClassFileImage> classes = classes();
        return loader.of(classes, () -> Output.loader(db, classes));
    }

    /** A module as everything below the check reads it — derived, desugared, and carrying the
     * recursive prelude helpers it reaches. Null where it did not get that far. */
    public Prepared module(String name) {
        return db.ask(new Shapes.Prepared(name)).value();
    }

    /**
     * The names of the behaviors {@code module} declares, or null where its declarations could not
     * be read.
     *
     * <p>What the source says rather than what got as far as being measured. A caller resolving a
     * name that arrived from outside needs the first: a behavior that was declared and could not be
     * prepared is a measurement that is missing, and answering "no such name" over it would report
     * the one as the other. Which is also why the unreadable case is null rather than empty — an
     * empty set is a module that declares no behavior, and that is an answer.
     */
    public Set<String> declaredBehaviors(String module) {
        return db.ask(new Front.Behaviors(module)).value();
    }

    /** The signatures of the behaviors {@code module} declares — what each takes and what it
     * answers, as the shapes a decoder and an encoder are built for. */
    public Map<String, Sig> signatures(String module) {
        Map<String, Sig> sigs = db.ask(new Bodies.Signatures(module)).value();
        return sigs == null ? Map.of() : sigs;
    }

    /** Every source id this compilation was given, in order. */
    public List<SourceId> sourceIds() {
        List<SourceId> ids = db.ask(new Front.Ids()).value();
        return ids == null ? List.of() : ids;
    }

    /**
     * Answers everything there is to answer about these sources — the classes, the constant
     * constructions, the examples — without deciding anything about what was found. A caller that
     * wants every problem at once asks for this and then reads {@link #reports()}, or one of the
     * readings of it: {@link #failure()}, {@link #errors()}, {@link #warnings()},
     * {@link #diagnostics()}.
     */
    public void answerEverything() {
        structuralReports();
        db.ask(new Output.All());
        for (String module : modules()) {
            db.ask(new Output.ConstConstructions(module));
            for (SourceId id : exampleSourcesOf(module)) {
                db.ask(new Front.RowNames(id));
                db.ask(new Names.StandInBlocks(id));
                db.ask(Output.Examples.asked(db, module, id));
            }
            db.ask(new Output.SaidDisagreements(module));
            answerWarnings(module);
        }
    }

    /**
     * Asks every question whose whole answer is a warning, for one module.
     *
     * <p>Here rather than at each caller because there are three of them — this class and both of
     * {@link souther.compiler.Compiler}'s entry points — and a warning added to one of them is a
     * warning the other two do not report. What the editor shows and what the command line shows
     * would then differ by which list a check was written into.
     *
     * <p>Each ask answers nothing on its own; what it is for is the reports it carries.
     */
    public void answerWarnings(String module) {
        db.ask(new Names.UnusedImports(module));
        // A defect in the model rather than a gap in its rows, so it is asked whether or not this
        // build wanted a coverage report.
        db.ask(new Adequacy.DeadBranches(module));
        // Costs nothing unless the build asked to be told.
        db.ask(new Adequacy.Warnings(module));
    }

    /**
     * The sources that wrote any of {@code module}'s example rows or fakes, in order.
     *
     * <p>The fakes as well as the rows, because a source that wrote only a fake still has something
     * said about it: a fake that answers otherwise than a row records is reported at both, and the
     * fake's side is this source's to say.
     */
    public java.util.SequencedSet<SourceId> exampleSourcesOf(String module) {
        java.util.SequencedSet<SourceId> wrote = db.ask(new Front.ExampleSources(module)).value();
        return wrote == null
                ? java.util.Collections.unmodifiableSequencedSet(new LinkedHashSet<>()) : wrote;
    }

    /** What this compilation was asked to measure. Set before anything is asked; the answers are
     * memoised, so a later change would leave one measured and the next not. */
    public void measure(Adequacy.Asked asked) {
        db.set(new Adequacy.Requested(), asked);
    }

    /** Measured and warned about, at {@code level}. */
    public void measure(Adequacy.Level level) {
        measure(Adequacy.Asked.warningsAt(level));
    }

    /**
     * How long this compilation gives one row to be evaluated, or one written statement to be read
     * against another. Returns this compilation, so it can be said where the sources are.
     *
     * <p>A build has no reason to say: the default is long enough that no terminating row is cut
     * short, which is the only thing a build wants from it. What is said here is said about this
     * compilation and no other, so a caller that wants a row to run out of budget — a test about what
     * is reported when one does not come back — gets that without holding every other compile in the
     * same JVM to the same wait.
     *
     * <p>The wait among the terms and not a second one beside them. Said as its own input it would be
     * a wait the JVM kept and the boundary did not know about, so an execution asked what it was held
     * to would answer the default while the run it was answering for was already being given up on.
     * Which is why {@link #withEvaluationPolicy} said afterwards replaces this along with the rest of
     * them: it states the terms, and this states one of them.
     *
     * @throws IllegalArgumentException if {@code budget} is not positive; a row that is given no time
     *     at all is given up on before it starts, and every behavior is reported as one this
     *     compiler could not answer for.
     */
    public Compilation withExampleBudget(java.time.Duration budget) {
        // Refused by the terms rather than here. What a positive wait is is the policy's to say —
        // it takes any positive length, down to below a millisecond — and a second reading of it
        // here was a stricter one: a wait of a few hundred microseconds is a wait, and this said it
        // was none at all.
        db.set(new Front.Policy(), Output.policyOf(db).withCompilerTimeout(budget));
        return this;
    }

    /**
     * How the JVM implementation runs this compilation's rows and readings. Returns this
     * compilation, so it can be said where the sources are.
     *
     * <p>Not a term. What a row is held to — the steps, the depth, the wait — is
     * {@link #withEvaluationPolicy} and {@link #withExampleBudget}, and those are said in words any
     * execution can be held to. This names the arrangement that keeps the wait on this machine, so
     * it is offered as the implementation's seam rather than as an input of this compilation's, and
     * it is reached the way {@link #jvmProgramImages} is.
     *
     * <p>One caller says one. A test asking what the compiler says about work that did not come back
     * says an arrangement under which the work it picks out does not come back — otherwise it has to
     * write a model that does not terminate and race a clock to see it reported, and a loaded host
     * loses that race in the direction that matters. Nothing this compiler ships says one: a row
     * driven from Java runs under the machine a build runs under, differing in that what it hands
     * outside is serviced rather than never arriving.
     *
     * <p>Said before any row is run, for the reason {@link Db#running} takes what runs a
     * compilation's programs once: what runs one is beside the memos rather than in them, so a row
     * already answered is not answered again because the arrangement changed, and the store would go
     * on handing out what the first one said. A replacement after that is refused.
     *
     * @throws IllegalStateException where rows have already been run under the arrangement this
     *     replaces
     */
    public Compilation withJvmExampleDeadlines(JvmExampleDeadlines arrangement) {
        jvmExampleDeadlines.chosen(arrangement);
        return this;
    }

    /**
     * What this compilation allows one row's evaluation. Returns this compilation, so it can be said
     * where the sources are.
     *
     * <p>A build has no reason to say: the default is set so that no row a model states reaches it.
     * What a caller says here is said about this compilation alone, so a test holding a row to a few
     * steps does not hold every other compile in the same JVM to them.
     */
    public Compilation withEvaluationPolicy(souther.compiler.execute.EvaluationPolicy policy) {
        db.set(new Front.Policy(), policy);
        return this;
    }

    /**
     * The same for how much of a declaration's clauses a reading may hold apart.
     *
     * <p>Not a knob a build has. Nothing an author writes reaches the fallback at the default, so
     * what it is here for is holding a reading to a small limit and watching the fallback answer —
     * which is the only way that path is reached at all, and it would rot unread otherwise.
     */
    Compilation withReadingPolicy(souther.compiler.check.ReadingPolicy policy) {
        db.set(new Front.Reading(), policy);
        return this;
    }

    /**
     * The same for what measuring a behavior and composing rows for it may spend.
     *
     * <p>Not a knob a build has either. What it is here for is holding a measurement to a small
     * limit and watching what the limit reports — a model that reaches any of the three defaults is
     * larger than anything in this repository, so the paths past them would rot unread otherwise.
     */
    Compilation withAdequacyPolicy(souther.compiler.partition.AdequacyPolicy policy) {
        db.set(new Front.Adequacy(), policy);
        return this;
    }

    /** How well one module's rows cover it. {@link #answerEverything()} need not have run: the
     * measures ask for what they read. */
    public Adequacy.Of adequacy(String module) {
        return new Adequacy.Of(db.ask(new Adequacy.Witnesses(module)).value(),
                db.ask(new Adequacy.Coverage(module)).value(),
                db.ask(new Adequacy.BodyBorders(module)).value(),
                db.ask(new Adequacy.BranchCoverage(module)).value());
    }

    public Db db() {
        return db;
    }

    /** The names of the modules these sources declare, in the order the sources were given. */
    public List<String> modules() {
        List<String> declared = db.ask(new Front.Declared()).value();
        return declared == null ? List.of() : declared;
    }

    /** The id of the source that declares {@code module}, or null when nothing here does. */
    public SourceId sourceIdOf(String module) {
        Front.Layout.Of layout = db.ask(new Front.Layout()).value();
        return layout == null ? null : layout.idOfModule().get(module);
    }

    /**
     * The module {@code sourceId} is part of — the one it declares, or the one its rows are for —
     * or null when this compilation does not have the source.
     *
     * <p>The other direction of {@link #sourceIdOf}, and not its inverse: a module is declared in
     * one source, and several sources may be part of it.
     */
    public String moduleOf(SourceId sourceId) {
        return db.ask(new Front.ModuleOf(sourceId)).value();
    }

    /**
     * The problems that stop this compilation before any module is looked at: a source that names a
     * module twice, one that shadows a module already on the path, and a cycle among them.
     *
     * <p>These come first because each one makes "which module is this name" unanswerable, and
     * every question below that is about a name.
     */
    public List<Db.Found> structuralReports() {
        List<Db.Found> found = new ArrayList<>();
        Answer<Front.Layout.Of> layout = db.ask(new Front.Layout());
        for (Report report : layout.reports()) {
            found.add(new Db.Found(null, null, report));
        }
        for (SourceId id : sourceIds()) {
            for (Report report : db.ask(new Front.Declares(id)).reports()) {
                found.add(new Db.Found(null, id, report));
            }
            for (Report report : db.ask(new Front.AttachedTo(id)).reports()) {
                found.add(new Db.Found(null, id, report));
            }
        }
        for (String module : modules()) {
            for (Report report : db.ask(new Front.ShadowsPath(module)).reports()) {
                found.add(new Db.Found(module, null, report));
            }
            for (Report report : db.ask(new Names.InCycle(module)).reports()) {
                found.add(new Db.Found(module, null, report));
            }
        }
        return found;
    }

    /**
     * Every problem these sources have, on the source it belongs to. A source with none maps to an
     * empty list.
     *
     * <p>Where a report goes is the report's to say, not this method's: an error in an imported
     * module lands on that module's document however far away it was asked for, and a failing
     * example row lands on the file the row was written in. A report about a module the caller has
     * no document for is said where that module was reached from, which is a document the caller
     * does have ({@link #reports()}) — left as it was found it would have nowhere to go, and a
     * compile that emitted nothing would publish nothing to say why.
     *
     * <p>This is the only place one report becomes several entries. A problem written in two files is
     * said in both, so an author editing either is told, and each entry carries the source its
     * primary region is in — which is not the file it is filed under, for the file that holds the
     * other half. A reader turns that pair into what to quote and what to link to
     * ({@link souther.compiler.diag.DiagnosticView}).
     */
    public Map<SourceId, List<Located>> diagnostics() {
        answerEverything();
        Map<SourceId, List<Located>> byId = new LinkedHashMap<>();
        for (SourceId id : sourceIds()) {
            byId.put(id, new ArrayList<>());
        }
        for (Db.Found found : reports()) {
            SourceId primary = filedUnderOf(found);
            for (SourceId id : publishSourceIdsOf(found)) {
                List<Located> on = byId.get(id);
                if (on != null) {
                    // Listed under the file the report claims, and read from the file this entry
                    // is for. A problem written in two files is one report said in both, and on
                    // the second of them the text being read is that second file.
                    on.add(new Located(found.report().diagnostic(),
                            ReportContext.of(primary, id)));
                }
            }
        }
        Map<SourceId, List<Located>> published = new LinkedHashMap<>();
        byId.forEach((id, found) -> published.put(id, List.copyOf(found)));
        return published;
    }

    /**
     * Everything this compilation found, as a reader may be shown it.
     *
     * <p>The one set. What a terminal is told and what an editor is told are the same problems said
     * the same way, so both read this and neither reads {@link Db#allReports()} — two readings of one
     * set is how a failure came to stop a build on the command line and leave an editor showing a
     * clean file.
     *
     * <p>What a reading adds is where a report may be sent. A question about a module read off the
     * class path is answered against text this compile reassembled from what the module published,
     * and a report it finds carries a coordinate in that text: a line and a column of a file nobody
     * holds. Filed as it stands, it lands wherever those numbers happen to fall in the file the
     * author is looking at, or nowhere at all. So it is said at the nearest place on the way to that
     * module a source of this compilation writes — the {@code import} line naming it, or the one
     * naming whichever dependency led there — with the code's own home named rather than pointed at
     * ({@link Diagnostic#reachedFrom}).
     */
    public List<Db.Found> reports() {
        // Read again for repeats, because reading for where a reader can be sent is what makes two
        // of them. Coordinates in a module's own reassembled text tell apart two findings this
        // compilation collected separately; said at the place that module was reached from, both
        // are one sentence at one caret, and an author shown it twice has nothing to tell them
        // apart by either.
        Map<Repeat, Db.Found> reports = new LinkedHashMap<>();
        for (Db.Found found : db.allReports()) {
            Db.Found said = citable(found);
            reports.putIfAbsent(new Repeat(said.module(), filedUnderOf(said),
                    said.report().problem()), said);
        }
        return new ArrayList<>(reports.values());
    }

    /** One thing said once: what {@link Db#allReports()} tells apart before a report is read for
     *  where it may be said, asked again of what that reading left. */
    private record Repeat(String module, SourceId sourceId, Diagnostic.Identity problem) {}

    /**
     * {@code found} where a reader can be sent to it — itself, for the reports already pointing at a
     * source this compile holds.
     *
     * <p>Which of them those are, and what to say about them, is one question rather than a source
     * test here and a provenance lookup after it. What a report is moved with is what it already says
     * about where its code is written; the module this walk was about answers only for a report that
     * says nothing, which is the one case with nothing to prefer.
     *
     * <p>What a raised report carried as the text its pass threw it with is dropped with the move
     * ({@link Report#legacyMessage}). That text was rendered with the old coordinate written into
     * it, and a message naming one place beside a caret at another is the defect twice. The body of
     * a {@link Diagnostic#literal} is not that and is not dropped: it is the whole of what such a
     * diagnostic says, there being no message under it to render again.
     */
    private Db.Found citable(Db.Found found) {
        Diagnostic said = found.report().diagnostic();
        SourceProvenance about = whatToMove(said, found.module());
        if (about == null) {
            return found;
        }
        Front.FromPath.OnThePath onThePath = Front.onThePath(db, found.module());
        if (onThePath == null || onThePath.reachedFrom().isEmpty()) {
            return found;
        }
        return new Db.Found(found.module(), found.sourceId(), Report.of(
                said.reachedFrom(onThePath.reachedFrom(), about,
                        new ModuleMessage.ItIsReachedFromHereToo())));
    }

    /**
     * Where the code a report is about is written, for a report this reading should move — and null
     * for one it should leave alone.
     *
     * <p>Two questions, and they are not the same one. Whether this compilation can send a reader to
     * where the report points is about the place; what the report says about where its code is
     * written is about the code. A report pointing at a call in the reader's file says its code is
     * elsewhere and needs no moving, so reading one off the other would move it.
     *
     * <p>Its own answer wherever it has one. A position carries which text it is in and whose code it
     * holds separately, so a report from a body spliced into a module's published text says the
     * body's module and not the text's — and reading the module this walk was about instead would put
     * them back together, which is the inference this whole change removes.
     *
     * <p>The module only where nothing was pointed at at all. There is nothing to read an answer off,
     * and the module the report was filed under is the whole of what is known about it.
     *
     * <p>A report in a text this compilation cannot name is left where it is. Its position is one
     * whoever handed the text over can use and this is not being read by them — but nothing about it
     * says where its code came from, and moving it would mean answering that from the module, which
     * is the inference again.
     */
    static SourceProvenance whatToMove(Diagnostic said, String module) {
        if (module == null) {
            return null;
        }
        boolean nowhereToSendAReader = switch (said.primary()) {
            case Primary.Unavailable _, Primary.Nowhere _ -> true;
            case Primary.InSource _, Primary.InAnUnnamedText _ -> false;
        };
        if (!nowhereToSendAReader) {
            return null;
        }
        return switch (said.whereItsCodeIsWritten()) {
            case WhereCodeIsWritten.Elsewhere(SourceProvenance from) -> from;
            case WhereCodeIsWritten.Unstated _ ->
                    souther.compiler.meta.ModuleReadback.provenanceOf(module);
            // A report saying its code is where it points has somewhere to point, so it did not
            // reach here.
            case WhereCodeIsWritten.Here _ -> null;
        };
    }


    /** The one failure these sources have, or null when they have none. */
    public CompileException failure() {
        return failure(reports());
    }

    /**
     * The failure among {@link #structuralReports()}, or null when there is none — what a caller
     * that stops before any module is looked at asks for.
     *
     * <p>Its own method so that {@link #failure(List)} can stay private.
     */
    public CompileException structuralFailure() {
        return failure(structuralReports());
    }

    /**
     * The errors among {@code found} as one exception, the first of them leading — or null when
     * nothing there is an error.
     *
     * <p>Every error, not the first. A compilation answers each of its questions and files what it
     * found, so by the time anything asks for the failure they are all in hand; handing back one of
     * them sends the author to fix that one and compile again to be told the next, which the
     * compiler already knew. The exception carries a list, and the command line and
     * {@code --format json} render each of them, so nothing between here and the author needs them
     * to be one.
     *
     * <p>Leading matters because a caller reading {@link CompileException#code()} or its message
     * reads the first, and that is the error a reader is sent to first. First means first in the
     * order the sources were given, then where in the source it is — not first worked out. A
     * question is answered when something asks it, and what asks first is an implementation detail:
     * resolving one module's names reaches another module's, so ordering by that would let moving a
     * report earlier in the compiler change which file a batch compile sends the author to. A report
     * about no source in particular comes before all of them, and one about no position before the
     * rest of its source.
     *
     * <p>The order the sources were given is the index a compile of a list of them assigns
     * ({@link #ofSources}). A compile of documents assigns none — a workspace has no first file —
     * and there the reports fall together and are ordered by position alone. What asks this is a
     * batch compile; an editor reads {@link #diagnostics()}, which files each report under the
     * source it is in and never has the question.
     *
     * <p>Two reports at one position keep the order the checker produced them in — a stable sort is
     * what that takes. A check that reports each of its own violations puts them all at the
     * declaration it is about, so this is the ordinary case rather than a tie to break arbitrarily.
     *
     * <p>Where a diagnostic is says where to look, and nothing about what caused what. An error a
     * checker reports off a value another error produced — a name that resolved to nothing, a body
     * that could not be typed — can be written to the left of the error it came from, and then it
     * leads. That is not this order failing to find the cause: a cause is not recoverable from two
     * positions, and a secondary diagnostic is the checker's to withhold where it should not be said
     * at all. This decides how the errors are presented; whether one of them should have been
     * reported is the checker's own question.
     *
     * <p>Not public. The reports a surface reads are the ones {@link #reports()} answers, and a
     * caller able to pass a list of its own is one able to pass the set before it was read for where
     * a reader can be sent. What is left inside this package is this ordering rule and its test.
     */
    CompileException failure(List<Db.Found> found) {
        List<Db.Found> errors = new ArrayList<>();
        for (Db.Found f : found) {
            if (f.report().isError()) {
                errors.add(f);
            }
        }
        if (errors.isEmpty()) {
            return null;
        }
        errors.sort(Comparator.comparingInt(this::orderOf)
                .thenComparingInt(f -> lineOf(f.report().diagnostic()))
                .thenComparingInt(f -> columnOf(f.report().diagnostic())));
        Db.Found first = errors.get(0);
        List<Located> rest = new ArrayList<>();
        for (Db.Found f : errors.subList(1, errors.size())) {
            rest.add(new Located(f.report().diagnostic(),
                    ReportContext.inFile(filedUnderOf(f))));
        }
        return first.report().asException()
                .alsoReporting(rest)
                .inSource(filedUnderOf(first));
    }

    /** Where a report sits among the sources for ordering, with the one naming none before them all. */
    private int orderOf(Db.Found found) {
        int index = indexOf(found);
        return index < 0 ? -1 : index;
    }

    /**
     * Where in its file a report sits, for ordering — and before every line of it where it points at
     * no part of the file.
     *
     * <p>A report about the file rather than about a line of it comes first, and so does one whose
     * numbers are of a text this compile could not name: those numbers are not of the file it is
     * listed under, so ordering by them would interleave it with the lines of a file it says nothing
     * about.
     */
    private static SourcePos orderingPositionOf(Diagnostic diagnostic) {
        return diagnostic == null ? null : switch (diagnostic.primary()) {
            case Primary.InSource(souther.compiler.diag.DiagnosticPlace.InSource place) ->
                    place.region().start();
            case Primary.InAnUnnamedText _, Primary.Unavailable _, Primary.Nowhere _ -> null;
        };
    }

    private static int lineOf(Diagnostic diagnostic) {
        SourcePos pos = orderingPositionOf(diagnostic);
        return pos == null ? -1 : pos.line();
    }

    private static int columnOf(Diagnostic diagnostic) {
        SourcePos pos = orderingPositionOf(diagnostic);
        return pos == null ? -1 : pos.column();
    }

    /**
     * Which of this compilation's sources a report is listed under: the one it claims
     * ({@link Db.Found#claimedSourceId()}), or — where it claims none, or claims one this compile
     * was not handed — the one that declares the module it was about.
     *
     * <p>Listed under, and not "the source its primary region is in". Those were one method and one
     * word for two questions, and the second of them has no answer here any more: a report that
     * points into a source says which one, on the place itself
     * ({@link souther.compiler.diag.Primary.InSource}), and nothing needs to be told. What is left
     * is this one, which the report cannot answer — a report about a module, one whose code is out
     * of sight, one in a text this compile could not name, all have to be listed somewhere for an
     * author to be shown them, and only a caller holding the files can say where.
     *
     * <p>So the fallback below is not a place. It is where the report is listed, and nothing reads
     * it as a caret: a renderer quotes from the place the report points at and names the file it is
     * listed under, which for a report pointing nowhere is the whole of what it says. Read as a
     * place — which it was, while one field answered both — it put a line and a column from one
     * source against the text of another.
     *
     * <p>The fallback guards availability as much as attribution. A position may have been read
     * from a source this compile does not have: the prelude, or a module read back off the module
     * path. Filing a report under a name {@link #diagnostics()} has no entry for would drop it
     * silently, and a report the author never sees is worse than one listed on the wrong file.
     *
     * <p>Which module the report is a failure of is a third question and not this one. A report
     * points into another module wherever the code it is about was written there — an invariant of
     * this module reaching a construction in a helper another module declares — and the caret stays
     * on that code, because that is what the report is about. Where it is said is
     * {@link #publishSourceIdsOf(Db.Found)}.
     */
    public SourceId filedUnderOf(Db.Found found) {
        SourceId claimed = found.claimedSourceId();
        if (claimed != null && sourceIds().contains(claimed)) {
            return claimed;
        }
        return found.module() == null ? null : sourceIdOf(found.module());
    }

    /**
     * Every source a report is said at, the one its primary region is in first. One entry for nearly
     * everything; several for a problem that belongs to each of the places it points at and to none
     * of them more.
     *
     * <p>Read off the regions rather than off a list of files, so every entry is somewhere the
     * report has something to show.
     *
     * <p>Which of them those are is what the labels say ({@link LabeledRegion#belongsToFinding}),
     * and is not worked out here. A rule of one module is broken by code written in another — an
     * invariant here reaching a construction in a helper declared there — and neither place reads as
     * the whole of it: an author sent only to the helper is looking at code that is fine to call
     * from anywhere else, and one sent only to the clause is looking at a call that builds nothing.
     * The check that found it is what knows the two are one problem, and a second region pointed at
     * to explain a mistake written elsewhere is not that case and says so by not being labelled one.
     *
     * <p>Nothing here reads which module the compile was walking. A caret that left its module is a
     * coordinate, and the same two regions are the same problem in the same two files whichever of
     * them was being checked — reading the traversal would put a marker in front of one author and
     * not the other for a difference neither of them wrote.
     */
    public List<SourceId> publishSourceIdsOf(Db.Found found) {
        SourceId primary = filedUnderOf(found);
        List<SourceId> saidAt = new ArrayList<>();
        if (primary != null) {
            saidAt.add(primary);
        }
        for (LabeledRegion label : found.report().diagnostic().secondary()) {
            if (!label.belongsToFinding()) {
                continue;
            }
            // A label with nowhere to point puts the report in front of nobody new. It is part
            // of what is found wrong and is in a file this compile does not have, so there is no
            // author here to tell and no marker to place; what it has to say is said wherever the
            // report is already said.
            switch (label.place()) {
                case souther.compiler.diag.DiagnosticPlace.InSource in -> {
                    if (!saidAt.contains(in.source())) {
                        saidAt.add(in.source());
                    }
                }
                case souther.compiler.diag.DiagnosticPlace.Unavailable _ -> { }
            }
        }
        return List.copyOf(saidAt);
    }

    /** Where a report sits in the order the sources were given, or -1 when it names none. Only for
     * ordering: which file a report is listed under is {@link #filedUnderOf(Db.Found)}. */
    private int indexOf(Db.Found found) {
        SourceId id = filedUnderOf(found);
        if (id == null) {
            return -1;
        }
        Integer index = indexOfId.get(id);
        return index == null ? -1 : index;
    }

    /**
     * The warnings among {@code found}, in order, each tagged with the source its primary region is
     * in — the same tag {@link #failure} puts on an error, so a warning can be quoted where it is.
     *
     * <p>One entry per warning, never one per file it is said at. A problem written in two files is
     * one thing to be told about on a terminal; the second telling is what an editor needs, and that
     * is {@link #diagnostics()}.
     */
    public List<Located> warnings() {
        List<Located> warnings = new ArrayList<>();
        List<Db.Found> found = reports();
        for (Db.Found f : found) {
            if (!f.report().isError()) {
                warnings.add(new Located(f.report().diagnostic(), ReportContext.inFile(filedUnderOf(f))));
            }
        }
        return warnings;
    }

    /**
     * The errors among {@code found}, tagged as {@link #warnings} tags a warning.
     *
     * <p>All of them, where {@link #failure} answers with one. A caller that stops at the first
     * error wants the first; one that goes on to say something about the whole compilation has
     * already read past it, and showing a reader one error beside an account of everything else
     * would leave them to wonder what the rest of the errors were.
     */
    public List<Located> errors() {
        List<Located> errors = new ArrayList<>();
        List<Db.Found> found = reports();
        for (Db.Found f : found) {
            if (f.report().isError()) {
                errors.add(new Located(f.report().diagnostic(), ReportContext.inFile(filedUnderOf(f))));
            }
        }
        return errors;
    }
}
