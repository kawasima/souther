package souther.compiler.query;

import souther.compiler.cst.SourceLayout;
import souther.compiler.Reserved;
import souther.compiler.source.SourceId;

import souther.compiler.ast.Ast;
import souther.compiler.ast.WrittenName;
import souther.compiler.regex.PatternPlan;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Primary;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.ImportMessage;
import souther.compiler.diag.SourcePos;
import souther.compiler.check.Exposing;
import souther.compiler.check.BehaviorImplementation;
import souther.compiler.check.Scoping;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.meta.ModulePath;
import souther.compiler.observe.RowIdentity;
import souther.compiler.meta.PublishedClasses;
import souther.compiler.meta.ModuleReadback;
import souther.compiler.meta.ReadableModule;
import souther.compiler.meta.Readback;
import souther.compiler.meta.ReadbackReasons;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;

/**
 * Getting from text to a set of named modules: the inputs, the parse of each source, which module
 * each source declares, and the modules this compilation reaches that no source declares.
 *
 * <p>Everything here is about sources and names. Nothing yet knows what a name means — that starts
 * at {@link Names}.
 */
public final class Front {

    private Front() {}

    /** The namespace the compiler ships. A user module may not take a reserved name, or it could
     * grant itself the core's privileges. */
    private static final String RESERVED = "souther";

    /** Every source id this compilation was given, in the order they were given. The order is the
     * index a diagnostic names when it says which file it came from. */
    public record Ids() implements Input<List<SourceId>> {}

    /** The text of one source. */
    public record Text(SourceId id) implements Input<String> {
        @Override
        public SourceId sourceId() {
            return id;
        }
    }

    /** The compiled modules of the projects this one depends on. */
    public record Path() implements Input<ModulePath> {}

    /**
     * The name a source with no {@code module} header takes. Absent means a header is required,
     * which is what linking a set of sources needs: a module reached by an import has to be named.
     */
    public record DefaultName() implements Input<String> {}

    /**
     * Modules the caller has already found unusable and did not hand over — a file an editor holds
     * out of the compile because of its own syntax errors.
     *
     * <p>An importer of one is left alone rather than told the module is unknown: the error belongs
     * to the file that will not parse, which reports it separately, and saying it again on the
     * importer sends the author to a file that is fine.
     */
    public record Broken() implements Input<Set<String>> {}

    /**
     * Whether these sources are the compiler's own. The reserved namespace is what keeps a user
     * module from granting itself the core's privileges; the core modules are in it by definition,
     * so compiling them is the one case where the name is theirs to take.
     */
    public record Core() implements Input<Boolean> {}

    /**
     * What this compilation allows one row's evaluation: the steps and the depth that decide it, and
     * the wait after which it is given up on.
     *
     * <p>Read once, here, rather than out of a system property wherever a row happens to be evaluated.
     * A property read at class initialization is fixed for the life of the JVM, which is the wrong
     * answer in every long-lived one — a build daemon, an editor's language server — where the compile
     * a setting was written for is not the one that reads it. Held as an input, it belongs to the
     * compilation, and two of them in one JVM may differ.
     *
     * <p>Absent means {@link souther.compiler.execute.EvaluationPolicy#DEFAULT}.
     */
    public record Policy() implements Input<souther.compiler.execute.EvaluationPolicy> {}

    /**
     * The standard library this compilation reads names against.
     *
     * <p>Held as an input for the reason the two above are: everything a compilation resolves is
     * resolved against one library, and a reader that fetched its own could be resolving one module
     * against a different set of declarations from the module beside it. What sets it is the
     * compilation, once, as it starts.
     *
     * <p>The library is the same value for every compilation in a process today — there is one
     * bundled library and no way to name another. It travels as an input all the same, because what
     * a name means is a question about the compilation asking, and an answer that reached for a
     * process-wide value would be right by coincidence rather than by construction.
     */
    public record Library() implements Input<souther.compiler.stdlib.Stdlib> {}
    /**
     * How much of a declaration's clauses a reading may hold apart.
     *
     * <p>Held as an input for the reason the one above is: it belongs to the compilation, and every
     * reading of one declaration has to be under the same one. Read here and handed to the analysis,
     * which never makes one — a policy made where it is needed is one that can differ between two
     * readings of the same declaration, and each would answer a position differently while both
     * stayed sound.
     */

    public record Reading() implements Input<souther.compiler.check.ReadingPolicy> {

        /**
         * What a compilation sets, and the one place the number is written.
         *
         * <p>A guardrail against pathological expansion and not a precision setting: measured over
         * the compiler's own suite the largest expansion any clause reaches is five, and over the
         * bench corpus every clause reaches one. So nothing written in this repository is read by
         * the fallback at any limit of eight or more, and this is a wide margin over anything
         * observed rather than an optimum derived from it. What the design needs is that a finite
         * limit exists.
         *
         * <p>The scale a reading will lay a grid out at is the same kind of number and is set the
         * same way. A scale a domain writes is a count of places money or a rate is kept to, which
         * is single digits; a thousand either way from the point is a wide margin over that and
         * costs a number a thousand digits wide at the corners of one divide. Past it the answer is
         * what a scale the reading cannot read as one number already gets — which side of nought it
         * is on, and no grid.
         *
         * <p>And the two figures a reading builds machines against: what a position's answer may
         * cost, and what handing each of its rules on as the set it leaves may cost. What each of
         * them bounds is written where it is declared
         * ({@link souther.compiler.regex.PatternPlan.Budget#OF_ADMITTED_VALUES},
         * {@link souther.compiler.regex.PatternPlan.Budget#OF_WHAT_A_RULE_LEAVES}); what is settled
         * here is that a compilation grants them, so that no reader anywhere makes itself an
         * allowance at the moment it wants a machine.
         *
         * <p>Held here rather than beside the policy it makes, so that reading a declaration cannot
         * reach it: what governs a reading is handed to it, and a default it could pick up is a
         * default two readings of one declaration can differ by.
         */
        static final souther.compiler.check.ReadingPolicy STANDARD =
                new souther.compiler.check.ReadingPolicy(64, 1000,
                        souther.compiler.regex.PatternPlan.Budget.OF_ADMITTED_VALUES,
                        souther.compiler.regex.PatternPlan.Budget.OF_WHAT_A_RULE_LEAVES);
    }

    /**
     * What measuring a behavior and composing rows for it may spend.
     *
     * <p>Held as an input for the reason the two above are: it belongs to the compilation, and every
     * measurement of one behavior has to be under the same one. Read here and handed to the
     * analysis, which never makes one — a budget made where it is needed is one two measurements of
     * a model can differ by, and each would report different coverage of it while both stayed
     * sound.
     */
    public record Adequacy()
            implements Input<souther.compiler.partition.AdequacyPolicy> {

        /**
         * What a compilation sets, and the one place these figures are written.
         *
         * <p>Guardrails and not precision settings, each set with room over anything observed. The
         * pair space is twenty thousand: a behavior of a dozen positions of a handful of classes
         * each runs to hundreds, and past twenty thousand the count is of a space no set of rows
         * addresses. The row limit is two hundred, which is where a block stops being something a
         * person reads and pastes. A group's choices are capped at four thousand and ninety-six,
         * which a body reaches only by settling thirteen decisions on one value.
         *
         * <p>What a body's rules about the strings at a position may build is the figure a reading
         * spends on the same kind of work, and the same size for the same reason — the formats a
         * model writes are a few hundred states, and past this one the pattern is one nobody meant
         * to write. Its own figure all the same: a measurement is what it leaves partial, and a
         * compilation that wanted a behavior told apart more finely than its declarations are read
         * exactly is one this lets say so.
         *
         * <p>Held here rather than beside the policy it makes, so that measuring a behavior cannot
         * reach it: what governs a measurement is handed to it, and a default it could pick up is a
         * default two measurements of one behavior can differ by.
         */
        static final souther.compiler.partition.AdequacyPolicy STANDARD =
                new souther.compiler.partition.AdequacyPolicy(
                        new souther.compiler.partition.AdequacyPolicy.OfTheMeasures(20_000,
                                PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS),
                        new souther.compiler.partition.AdequacyPolicy.OfTheGeneration(200, 4096));
    }

    /** One source, parsed, with the text of each declaration kept for publishing. Every position in
     * what comes back names this source, so a writing that later joins another file's module still
     * says where it was written. */
    public record Parsed(SourceId id) implements Key<CstFrontend.Parsed> {
        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public Answer<CstFrontend.Parsed> compute(Db db) {
            Answer<String> text = db.ask(new Text(id));
            if (!text.present()) {
                return Answer.absent();
            }
            try {
                return Answer.of(CstFrontend.parseWithSlices(
                        text.value(), db.ask(new DefaultName()).value(), id));
            } catch (CompileException e) {
                return Answer.absent(e);
            }
        }
    }

    /**
     * How one source is laid out: where its meaningful tokens and its lines fall.
     *
     * <p>Its own question and not a reading of {@link Text}. What reads this wants where a place
     * sits — a debug table, a document that writes line numbers — and that is a narrower fact than
     * what the file says: a comment reworded to a line of the same width lays out identically, so
     * this comes out equal and what asked it is left alone. Read from the text instead, every such
     * reader would move for every keystroke anywhere in the file.
     */
    public record LayoutOf(SourceId id) implements Key<SourceLayout> {
        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public Answer<SourceLayout> compute(Db db) {
            Answer<String> text = db.ask(new Text(id));
            return text.present() ? Answer.of(SourceLayout.of(text.value(), id)) : Answer.absent();
        }
    }

    /**
     * Which source declares which module. An {@code examples for X} file declares no module of its
     * own — it contributes rows to X — so it is listed apart, under the module it names.
     *
     * <p>A source that will not parse is in neither list. Its own error is reported where it is
     * parsed, and here it is simply a module this compilation does not have: an importer of it gets
     * the absence, not a second copy of the syntax error.
     */
    public record Layout() implements Key<Layout.Of> {

        /**
         * Which source each module and each of its example files is written in.
         *
         * @param idOfModule the source each module was declared in
         * @param exampleFilesOf the {@code examples for} sources contributing to each module
         * @param exampleFileTargets the module each {@code examples for} source names, by source id
         */
        public record Of(Map<String, SourceId> idOfModule,
                         Map<String, List<SourceId>> exampleFilesOf,
                         Map<SourceId, String> exampleFileTargets) {}

        @Override
        public Answer<Layout.Of> compute(Db db) {
            List<SourceId> ids = db.ask(new Ids()).value();
            if (ids == null) {
                return Answer.absent();
            }
            Map<String, SourceId> idOfModule = new LinkedHashMap<>();
            Map<String, List<SourceId>> exampleFilesOf = new LinkedHashMap<>();
            Map<SourceId, String> exampleFileTargets = new LinkedHashMap<>();
            for (SourceId id : ids) {
                Answer<CstFrontend.Parsed> parsed = db.ask(new Parsed(id));
                if (!parsed.present()) {
                    continue;   // reported where it was parsed
                }
                Ast.Module m = parsed.value().module();
                if (m.exampleFileTarget() != null) {
                    exampleFileTargets.put(id, m.exampleFileTarget());
                    exampleFilesOf.computeIfAbsent(m.exampleFileTarget(), k -> new ArrayList<>()).add(id);
                    continue;
                }
                idOfModule.putIfAbsent(m.name(), id);
            }
            return Answer.of(new Layout.Of(Ordered.map(idOfModule), Ordered.map(exampleFilesOf),
                    Ordered.map(exampleFileTargets)));
        }
    }

    /**
     * One module a source declared, read for its standard-library imports: the module without those
     * lines and what they brought in, together, with its name checked against the reserved
     * namespace.
     *
     * <p>Both halves from one reading. What a bare name means is decided against the table, and the
     * module the table is about is the one the rows of every {@code examples for} file naming it
     * have joined — a value an attached file declares collides with an import of that spelling the
     * same way one in the model file does. Read a second time from the model file alone, the table
     * would answer for a module nothing else in this compilation holds.
     *
     * <p>The rows themselves join here for the same reason: a module's examples are its examples
     * wherever they were written. Which file a row came from is {@link Names.Examples}' business,
     * not this one's.
     */
    public record Checked(String name) implements Key<Exposing.Checked> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Exposing.Checked> compute(Db db) {
            Layout.Of layout = db.ask(new Layout()).value();
            if (layout == null) {
                return Answer.absent();
            }
            SourceId id = layout.idOfModule().get(name);
            if (id == null) {
                return Answer.absent();   // not declared by any source; the path may still have it
            }
            Answer<CstFrontend.Parsed> parsed = db.ask(new Parsed(id));
            if (!parsed.present()) {
                return Answer.absent();
            }
            Ast.Module raw = parsed.value().module();
            if (!Boolean.TRUE.equals(db.ask(new Core()).value())) {
                Report reserved = reservedNamespace(raw.name(), raw.pos());
                if (reserved != null) {
                    return Answer.absent(reserved);
                }
            }
            // The attached files join before the imports are read, because their values are the
            // module's values: a name one of them declares reaches the value namespace exactly as a
            // name in the model file does, so an import of that spelling collides with it the same
            // way. They bring no imports of their own — an attached file writes none — so what is
            // read here is still the model file's lines.
            Ast.Module joined = withAttachedRows(db, raw, layout.exampleFilesOf()
                    .getOrDefault(name, List.of()));
            Exposing.Checked checked = Exposing.check(joined,
                    db.ask(new Library()).value().names());
            if (checked.refused().isEmpty()) {
                return Answer.of(checked);
            }
            // An import line that could not do its job. Reported here because this is where the
            // import lines are read, and reported rather than raised so the rest of the module —
            // and every other file beside it — is still read and still answers.
            List<Report> reports = new ArrayList<>();
            for (Exposing.Refusal refusal : checked.refused()) {
                reports.add(said(refusal));
            }
            return Answer.of(checked, reports);
        }

        /**
         * The module with every attached file's examples, fakes and values appended.
         *
         * <p>The values join the module the rows join, as the fakes do: a module's own values are what its
         * attached files' rows name, and the other way round. Nothing outside can reach them either way —
         * an attached file is not a module and is not imported, and the values it declares are in no
         * {@code exposing}.
         */
        private Ast.Module withAttachedRows(Db db, Ast.Module m, List<SourceId> files) {
            if (files.isEmpty()) {
                return m;
            }
            List<Ast.Example> examples = new ArrayList<>(m.examples());
            List<Ast.Fake> fakes = new ArrayList<>(m.fakes());
            List<Ast.FnDef> fns = new ArrayList<>(m.fns());
            Set<String> taken = new LinkedHashSet<>();
            for (Ast.FnDef fn : m.fns()) {
                taken.add(fn.name());
            }
            for (SourceId id : files) {
                Answer<CstFrontend.Parsed> file = db.ask(new Parsed(id));
                if (!file.present()) {
                    continue;
                }
                examples.addAll(file.value().module().examples());
                fakes.addAll(file.value().module().fakes());
                for (Ast.FnDef value : file.value().module().fns()) {
                    // A name already declared is not merged, so the module has no duplicate to report
                    // against a position in a file it cannot quote — the attached file's own key reports
                    // it, where the position and the file agree (see Output.Examples).
                    if (taken.add(value.name())) {
                        fns.add(value);
                    }
                }
            }
            // An attached file writes rows and the values they name, which are declarations of the
            // target module. What that module takes on to emit is worked out further down and is
            // nothing an attached file adds to.
            return new Ast.Module(m.name(), m.exposing(), m.exposedOutputs(), m.imports(), m.defs(),
                    m.behaviors(), fns, m.takenOn(), examples, fakes, m.exampleFileTarget(),
                    m.pos());
        }
    }

    /**
     * One module as its source declared it, with the standard-library {@code exposing} lines already
     * dropped.
     *
     * <p>The half of {@link Checked} that a reader walking declarations wants. What those lines
     * brought in is the other half and is asked for as {@link LibraryClaims}: nearly everything here
     * reads the module and would be rebuilt by an edit to any import line if it held the table too.
     * Neither half is computed twice — both are projections of the one reading.
     *
     * <p>What the reading found comes with it. Asking for a module is how a reader finds out whether
     * reading it went wrong ({@link Names.Sound}), and a projection that answered the module and
     * kept the reports to itself would say a module with a refused import line was read cleanly.
     * The reports are the same reports, so a compilation collecting them sees one of each.
     */
    public record Exposed(String name) implements Key<Ast.Module> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Ast.Module> compute(Db db) {
            Answer<Exposing.Checked> checked = db.ask(new Checked(name));
            return checked.present()
                    ? Answer.of(checked.value().module(), checked.reports())
                    : Answer.absent(checked.reports());
        }
    }

    /**
     * The modules this compilation reaches that no source declares, read off the path. A module
     * found there brings its own reaches with it, so a dependency of a dependency arrives too.
     *
     * <p>Where each of its behaviors gets its body comes with it: no {@code let} is published, so
     * that cannot be read off a module here the way it is read off a source.
     */
    public record FromPath() implements Key<FromPath.Of> {

        /**
         * One module off the path, and everything about it this compilation cannot work out again
         * from the module alone.
         *
         * <p>One record and not a map apiece. Each of these is a fact about the module that was lost
         * on the way in — where each behavior's body comes from because no {@code let} was
         * published, the library
         * names because the lines that carried them are dropped once read, where it was reached
         * because a source of this compile is the only place a reader can be sent. A second map is a
         * second place to remember to fill, and the one that was not filled is what left an
         * invariant's bare names denoting nothing.
         *
         * @param reachedFrom every nearest place in a source of this compilation on the way to
         *        this module: each {@code import} line that names it, or that names whichever
         *        module off the path led here. Every one of them and not the first, because a
         *        dependency two files import is reached by both and a report about it is one an
         *        author editing either has to be told — an editor marking one file leaves the other
         *        looking clean while the build fails. Empty only where nothing this compile can
         *        quote reached it.
         */
        public record OnThePath(ReadableModule read, List<SourcePos> reachedFrom) {

            /** Copied, for the reason {@link ReadableModule} is: this is remembered, and what is
             *  remembered is a value. */
            public OnThePath {
                reachedFrom = List.copyOf(reachedFrom);
            }

            public Ast.Module module() {
                return read.module();
            }

            /** What it declares, indexed where it was read back. */
            public Map<String, Ast.Def> declarations() {
                return read.declarations();
            }

            /** Where each of its behaviors gets its body, as the module that declared it decided. */
            public Map<String, BehaviorImplementation> behaviorImplementations() {
                return read.behaviorImplementations();
            }

            public List<Scoping.Claim> libraryClaims() {
                return read.libraryClaims();
            }
        }

        /**
         * The modules this compilation can see, and the ones it can see are there and will not
         * read.
         *
         * @param modules the ones this compilation may read declarations from
         * @param refused the ones it will not, and knows are there all the same — a module that
         *        took a name no module may take, and one this compiler cannot read what it
         *        published. Which of those two a name is settles two different questions, and
         *        answering only the first made a module that is on the path and refused come back as
         *        one nobody has heard of: the author was told both that it took a reserved name and
         *        that there is no such module.
         */
        public record Of(Map<String, OnThePath> modules, Set<String> refused) {}

        @Override
        public Answer<FromPath.Of> compute(Db db) {
            Layout.Of layout = db.ask(new Layout()).value();
            ModulePath path = db.ask(new Path()).value();
            if (layout == null || path == null) {
                return Answer.of(new FromPath.Of(Map.of(), Set.of()));
            }
            // Read the graph, work out where each of its modules is reached from, and only then say
            // anything. Each of the three needs the one before it finished: a module is read once
            // and a route to it may turn up long after, so a place written down as it was read is
            // the places it had by then — with two dependencies of a project both reaching a third,
            // which of them was read first decided whether the second's importer was told anything.
            PublishedClasses classes = path.declarations();
            Map<String, ReadableModule> read = new LinkedHashMap<>();
            List<Report> reports = new ArrayList<>();
            // Where a source of this compile names each module it reaches, and which modules each
            // module off the path reaches in turn.
            Map<String, List<SourcePos>> named = new LinkedHashMap<>();
            Map<String, List<String>> edges = new LinkedHashMap<>();
            // The ones this compilation will not have, whatever the path holds. On the path and
            // refused, which is not the same as absent — and two separate reasons, because the name
            // a module took and what its artifact carries are two questions and an artifact can be
            // wrong about both.
            Map<String, Diagnostic> refused = new LinkedHashMap<>();
            Map<String, Readback.Failure> unreadable = new LinkedHashMap<>();
            Deque<String> pending = new ArrayDeque<>();
            for (String declared : layout.idOfModule().keySet()) {
                Ast.Module m = db.ask(new Exposed(declared)).value();
                if (m != null) {
                    reaches(m).forEach((reach, where) -> {
                        if (where != null) {
                            named.computeIfAbsent(reach, k -> new ArrayList<>()).add(where);
                        }
                        pending.add(reach);
                    });
                }
            }
            Set<String> tried = new HashSet<>();
            while (!pending.isEmpty()) {
                String name = pending.poll();
                if (layout.idOfModule().containsKey(name) || !tried.add(name)) {
                    continue;
                }
                Readback<ReadableModule> readback = ModuleReadback.read(name, classes,
                        db.ask(new Library()).value().names());
                if (readback instanceof Readback.NotReady.SaysNothing<ReadableModule>) {
                    continue;   // absent; which of its importers minds is worked out below
                }
                // Two questions about one artifact, and neither answers the other. Whether the name
                // is one a module may take is settled by the name; whether what it carries can be
                // read is settled by what it carries. Asked in sequence, the first to fail decided
                // what the author heard — so a module that took a reserved name and was built by
                // another compiler was two things to fix and was told as one, and fixing that one
                // brought the other out. Both are said.
                Diagnostic.Builder reserved = reservedNamespaceTaken(name);
                if (reserved != null) {
                    // The same as the two below: the name was taken by a module this compile has no
                    // file for, so the report says which module and points nowhere.
                    refused.put(name,
                            reserved.atCodeWrittenOutOfSight(ModuleReadback.provenanceOf(name))
                                    .build());
                }
                if (readback instanceof Readback.NotReady.Unreadable<ReadableModule>(
                        String about, Readback.Failure why)) {
                    unreadable.put(about, why);
                    continue;
                }
                if (reserved != null) {
                    continue;   // readable, and still not a name this compilation will take
                }
                ReadableModule module = ((Readback.Ready<ReadableModule>) readback).value();
                read.put(name, module);
                List<String> reaches = List.copyOf(reaches(module.module()).keySet());
                edges.put(name, reaches);
                pending.addAll(reaches);
            }
            Map<String, List<SourcePos>> reachedFrom = reachedFrom(named, edges);
            Map<String, OnThePath> found = new LinkedHashMap<>();
            read.forEach((name, module) -> found.put(name,
                    new OnThePath(module, reachedFrom.getOrDefault(name, List.of()))));
            for (Map.Entry<String, Diagnostic> taken : refused.entrySet()) {
                reports.add(saidAbout(taken.getKey(), taken.getValue(), reachedFrom));
            }
            for (Map.Entry<String, Readback.Failure> beyond : unreadable.entrySet()) {
                reports.add(saidAbout(beyond.getKey(), cannotBeReadBack(beyond.getKey(),
                        beyond.getValue()), reachedFrom));
            }
            // An incomplete path is one module needing another, and not a module being absent. Two
            // dependencies of a project may each need a third that is not there, and those are two
            // things to be told: they are missing from two places, an author reaching one of them
            // has not reached the other, and a report saying the first while pointing at an import
            // that arrives at the second says where the code is and is wrong about it.
            for (Map.Entry<String, List<String>> reaching : edges.entrySet()) {
                for (String needed : reaching.getValue()) {
                    // Refused is not absent: a module that took a name no module may take, or one
                    // this compiler will not read, is on the path and has been told so.
                    if (read.containsKey(needed) || refused.containsKey(needed)
                            || unreadable.containsKey(needed)
                            || layout.idOfModule().containsKey(needed)
                            || Reserved.isQualifier(needed)) {
                        continue;
                    }
                    reports.add(saidAbout(reaching.getKey(), needs(needed, reaching.getKey()),
                            reachedFrom));
                }
            }
            SequencedSet<String> notRead = new LinkedHashSet<>(refused.keySet());
            notRead.addAll(unreadable.keySet());
            return Answer.of(new FromPath.Of(Ordered.map(found), Ordered.set(notRead)), reports);
        }
    }

    /**
     * A module of this compilation, wherever it came from: declared by a source, or read off the
     * path. Absent when nothing here has it, which is what an import of an unknown module sees.
     *
     * <p>A module reaches here as it was written, with the qualified behavior references it names
     * still qualified. Which import each of them asks for is worked out where the scope is
     * assembled ({@link souther.compiler.check.Scoping#importsOf}), so nothing between a module
     * being read and being answered here rewrites what it says.
     */
    public record Available(String name) implements Key<Ast.Module> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Ast.Module> compute(Db db) {
            Answer<Ast.Module> exposed = db.ask(new Exposed(name));
            if (exposed.present()) {
                return Answer.of(exposed.value());
            }
            FromPath.OnThePath fromPath = onThePath(db, name);
            return fromPath == null ? Answer.absent() : Answer.of(fromPath.module());
        }
    }

    /**
     * The modules a module imports, named once each, in the order it names them.
     *
     * <p>Its own question for the same reason {@link Exposes} is: what reads this wants the shape of
     * the workspace around a module, and that shape survives almost every edit to the module itself.
     * Reading the module here would put every body on the far side of an answer about its header.
     */
    public record ImportedModules(String name) implements Key<List<String>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<String>> compute(Db db) {
            if (db.ask(new Available(name)).value() == null) {
                return Answer.of(List.of());
            }
            Set<String> imported = new LinkedHashSet<>();
            for (Ast.Import imp : Names.importsOf(db, name)) {
                imported.add(imp.module());
            }
            return Answer.of(List.copyOf(imported));
        }
    }

    /**
     * The library names a module's imports let it write bare, keyed by the bare spelling.
     *
     * <p>Asked of every module this compilation has and not only of the ones a source declared. The
     * import lines are dropped once checked, so what they brought in outlives them and has to be
     * carried; a module off the path carries it the same way, and answering an empty table there
     * left every bare name in a published invariant denoting nothing.
     */
    public record LibraryClaims(String name) implements Key<List<Scoping.Claim>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<Scoping.Claim>> compute(Db db) {
            Answer<Exposing.Checked> checked = db.ask(new Checked(name));
            if (checked.present()) {
                return Answer.of(List.copyOf(checked.value().claims()));
            }
            FromPath.OnThePath onThePath = onThePath(db, name);
            return onThePath == null ? Answer.absent()
                    : Answer.of(List.copyOf(onThePath.libraryClaims()));
        }
    }

    /**
     * Every place a source of this compilation reaches each module from: the lines that name it,
     * and — for one no line here names — the lines naming whichever module off the path led there.
     *
     * <p>Worked out over the whole graph rather than as it is walked. A module is read once and a
     * route to it may be found after that, so a place written down when it was read is the places
     * it had by then; where two dependencies of a project both reach a third, which of them was
     * read first would decide whether the second's importer is told anything. The walk gathers what
     * names what, and this answers.
     *
     * <p>A closure and not a step, for the same reason. What reaches a module reaches everything
     * that module reaches, however far down, and each place is kept once — the answer grows and
     * stops growing, whatever order the edges arrive in.
     */
    private static Map<String, List<SourcePos>> reachedFrom(Map<String, List<SourcePos>> named,
                                                            Map<String, List<String>> edges) {
        Map<String, List<SourcePos>> places = new LinkedHashMap<>();
        Deque<String> pending = new ArrayDeque<>();
        named.forEach((module, where) -> {
            if (alsoReachedFrom(places, module, where)) {
                pending.add(module);
            }
        });
        while (!pending.isEmpty()) {
            String module = pending.poll();
            List<SourcePos> here = List.copyOf(places.get(module));
            for (String reach : edges.getOrDefault(module, List.of())) {
                if (!reach.equals(module) && alsoReachedFrom(places, reach, here)) {
                    pending.add(reach);
                }
            }
        }
        return places;
    }

    /** {@code where} added to the places {@code module} is reached from, each place once. Whether
     *  any of them was new, which is what says there is anything further to carry on. */
    private static boolean alsoReachedFrom(Map<String, List<SourcePos>> places, String module,
                                           List<SourcePos> where) {
        List<SourcePos> held = places.computeIfAbsent(module, k -> new ArrayList<>());
        boolean added = false;
        for (SourcePos one : where) {
            if (!held.contains(one)) {
                held.add(one);
                added = true;
            }
        }
        return added;
    }

    /**
     * {@code about}, a report concerning code written in {@code module}, said at the nearest place a
     * source of this compilation reaches it — or left where it is where nothing here reaches it at
     * all.
     *
     * <p>One function for every report this walk makes, because the rule is one rule. What is known
     * about a module off the path is its name; the module is where the code is, and where a reader
     * can be sent is the import lines that arrive there. Each producer used to spell this out for
     * itself and take the provenance off whatever position its diagnostic happened to carry, which
     * meant a producer with nothing to point at could not be written — the reserved-name report
     * carried a place in the artifact only so that this step could read the module back out of it.
     *
     * <p>Every route and not the first: a dependency two files import is reached by both, and a
     * report about it is one an author editing either has to be told. An editor marking one file
     * leaves the other looking clean while the build fails.
     */
    private static Report saidAbout(String module, Diagnostic about,
                                    Map<String, List<SourcePos>> reachedFrom) {
        List<SourcePos> here = reachedFrom.getOrDefault(module, List.of());
        if (here.isEmpty()) {
            return Report.of(about);
        }
        return Report.of(about.reachedFrom(here, ModuleReadback.provenanceOf(module),
                new ModuleMessage.ItIsReachedFromHereToo()));
    }

    /**
     * What the author of an importing project is told about a module the class path carries and this
     * compiler will not read.
     *
     * <p>One sentence naming the module, with why as a note. The failure is the publishing project's
     * and the reading project's author has one thing to do about any of them, so which rule about
     * publishing was broken is said under the report rather than as the report — an author shown
     * "a published module agrees with this compiler" as the rule they are in breach of has been
     * handed somebody else's obligation.
     *
     * <p>Which failure it was is said by {@link ReadbackReasons} and not here. The fact is about the
     * artifact and is the same fact wherever it is read, so it is written once and every reader of
     * a readback failure says the same sentence for it; what is this one's is the report around it —
     * whose module it is about, and that there is one thing to do about any of them.
     */
    static Diagnostic cannotBeReadBack(String module, Readback.Failure why) {
        return ReadbackReasons
                .said(Diagnostic.say(new ModuleMessage.TheModuleCannotBeReadBack(module)), why)
                .hint(new ModuleMessage.RebuildItOrCompileAgainstWhatBuiltIt(module))
                .atCodeWrittenOutOfSight(ModuleReadback.provenanceOf(module))
                .build();
    }

    /**
     * What to tell the author about a library import line that could not do its job.
     *
     * <p>A switch over every refusal there is, with nothing to fall through to, for the reason
     * {@link Names} says it about the other namespace: a rule added to the check is a rule this
     * compilation has to have something to say about, and one that reached here with nothing to say
     * would be an import quietly bringing in nothing.
     *
     * <p>The place comes from the line the refusal names, which is where a reader of a module this
     * compilation has source for is sent. A module read off the class path refuses the same way over
     * a line nobody holds, and what is done about that is not this.
     */
    private static Report said(Exposing.Refusal refusal) {
        return switch (refusal) {
            case Exposing.Refusal.NoSuchLibraryFunction(Ast.Import imp, String named) ->
                    Report.raised(Diagnostic.at(imp.pos())
                            .say(new ImportMessage.NameIsNotAStandardLibraryFunction(
                                    named, imp.module()))
                            .build());
        };
    }

    /**
     * A module off the path needing one that is not there.
     *
     * <p>The code is written in {@code module}, which this compile has no file for, so there is
     * nowhere to send a reader and the module is what a reader is told instead
     * ({@link Primary.Unavailable}). Said here rather than left for {@link #saidAbout} to work out:
     * a report that says nothing about where its code is may be moved anywhere, and one that says
     * refuses a move that would put it somewhere else
     * ({@link Diagnostic.MovedSomewhereElsesCode}).
     *
     * <p>Package-private for the reason {@link #cannotBeReadBack} is: what it answers is which
     * provenance this compilation is reporting about, which is a fact this package states rather
     * than a fragment of one method.
     */
    static Diagnostic needs(String needed, String module) {
        return Diagnostic.say(new ModuleMessage.AModuleItNeedsIsNotOnThePath(needed, module))
                .hint(new ModuleMessage.AddItToThisProjectsDependencies(needed))
                .atCodeWrittenOutOfSight(ModuleReadback.provenanceOf(module))
                .build();
    }

    /** What the path carries for {@code name}, or null when no module of that name came off it. */
    static FromPath.OnThePath onThePath(Db db, String name) {
        FromPath.Of path = db.ask(new FromPath()).value();
        return path == null ? null : path.modules().get(name);
    }

    /**
     * The type names a module exposes.
     *
     * <p>Its own question, not a read of the module. Everything that resolves a name against another
     * module asks this, and a module changes far more often than its {@code exposing} line does —
     * reading the whole module here would mean a new behavior in one module rebuilding every module
     * that imports a type from it.
     */
    public record Exposes(String name) implements Key<Set<String>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Set<String>> compute(Db db) {
            Ast.Module m = db.ask(new Available(name)).value();
            return Answer.of(m == null ? Set.of()
                    : souther.compiler.check.Registry.baseNames(m.exposing()));
        }
    }

    /**
     * The behavior names a module declares.
     *
     * <p>Its own question for the reason {@link Exposes} is: what reads this wants one line of a
     * module's header, and that survives almost every edit to the module itself.
     */
    public record Behaviors(String name) implements Key<Set<String>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Set<String>> compute(Db db) {
            Ast.Module m = db.ask(new Exposed(name)).value();
            if (m == null) {
                FromPath.OnThePath fromPath = onThePath(db, name);
                m = fromPath == null ? null : fromPath.module();
            }
            return m == null ? Answer.absent() : Answer.of(Scoping.behaviorNames(m));
        }
    }

    /**
     * The module a source declares, when it is the source that declares it. A second source naming
     * the same module is the one reported: the first has a claim on the name, and the message
     * belongs on the file the author would have to change.
     */
    public record Declares(SourceId id) implements Key<String> {
        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public Answer<String> compute(Db db) {
            Answer<CstFrontend.Parsed> parsed = db.ask(new Parsed(id));
            Layout.Of layout = db.ask(new Layout()).value();
            if (!parsed.present() || layout == null
                    || parsed.value().module().exampleFileTarget() != null) {
                return Answer.absent();
            }
            Ast.Module m = parsed.value().module();
            if (id.equals(layout.idOfModule().get(m.name()))) {
                return Answer.of(m.name());
            }
            return Answer.absent(Report.raised(Diagnostic.say(new ModuleMessage.DuplicateModule(m.name()))
                            .at(m.pos()).build()));
        }
    }

    /**
     * The sources that wrote any of a module's {@code example} blocks or {@code fake} tables, in the
     * order the layout holds them: the module's own first, then each attached {@code examples for}
     * file.
     *
     * <p>Which sources, and not which block came from where. A block carries a position and a
     * position says which source it is in, so a list running alongside the blocks is that answer
     * written a second time, held in step by nothing but two walks agreeing on an order — and a
     * reader of the pair had to say what a length that did not match meant. What is asked of this is
     * which sources have something to report on, which is a question about the set.
     *
     * <p>A source that wrote neither is not one of them. It has nothing said about it, so a run over
     * it would report on a file with nothing to report.
     */
    public record ExampleSources(String name) implements Key<SequencedSet<SourceId>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<SequencedSet<SourceId>> compute(Db db) {
            Layout.Of layout = db.ask(new Layout()).value();
            if (layout == null) {
                return Answer.of(Collections.unmodifiableSequencedSet(new LinkedHashSet<>()));
            }
            SequencedSet<SourceId> wrote = new LinkedHashSet<>();
            SourceId own = layout.idOfModule().get(name);
            List<SourceId> sources = new ArrayList<>();
            if (own != null) {
                sources.add(own);
            }
            sources.addAll(layout.exampleFilesOf().getOrDefault(name, List.of()));
            for (SourceId id : sources) {
                CstFrontend.Parsed parsed = db.ask(new Parsed(id)).value();
                if (parsed == null) {
                    continue;
                }
                if (!parsed.module().examples().isEmpty() || !parsed.module().fakes().isEmpty()) {
                    wrote.add(id);
                }
            }
            return Answer.of(Collections.unmodifiableSequencedSet(wrote));
        }
    }

    /**
     * Whether an {@code examples for} file has a module to attach to. The rows contribute to a
     * module this compilation does not have, so there is nothing to run them against.
     */
    public record AttachedTo(SourceId id) implements Key<String> {
        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public Answer<String> compute(Db db) {
            Layout.Of layout = db.ask(new Layout()).value();
            if (layout == null) {
                return Answer.absent();
            }
            String target = layout.exampleFileTargets().get(id);
            if (target == null) {
                return Answer.absent();   // not an `examples for` file
            }
            if (layout.idOfModule().containsKey(target)) {
                return Answer.of(target);
            }
            SourcePos pos = db.ask(new Parsed(id)).present()
                    ? db.ask(new Parsed(id)).value().module().pos() : null;
            return Answer.absent(Report.raised(Diagnostic.at(pos)
                    .say(new ExampleMessage.TheModuleIsNotBeingCompiled(target))
                    .build()));
        }
    }

    /**
     * The module a source is part of: the one it declares, or — for an {@code examples for} file —
     * the one its rows are for.
     *
     * <p>One question, because a reader that has a file and wants to ask about the names in it needs
     * both answers and has no way to tell in advance which it will get. Asking it of the header
     * instead gives the first only, and a file that declares no module of its own then belongs to
     * nothing and is asked nothing.
     *
     * <p>No reports of its own: what is wrong with the source is already said by {@link Declares}
     * and {@link AttachedTo}, and saying it a second time here would put two markers on one line.
     */
    public record ModuleOf(SourceId id) implements Key<String> {
        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public Answer<String> compute(Db db) {
            Layout.Of layout = db.ask(new Layout()).value();
            if (layout == null) {
                return Answer.absent();
            }
            String attached = layout.exampleFileTargets().get(id);
            if (attached != null) {
                return layout.idOfModule().containsKey(attached)
                        ? Answer.of(attached)
                        : Answer.absent();   // names a module this compilation does not have
            }
            for (Map.Entry<String, SourceId> declared : layout.idOfModule().entrySet()) {
                if (declared.getValue().equals(id)) {
                    return Answer.of(declared.getKey());
                }
            }
            return Answer.absent();
        }
    }

    /**
     * The rows this source names, held to naming one row each.
     *
     * <p>A name is what says which row is meant from outside the file it is written in — a report
     * line two runs are compared on, a test that prepares an environment for one row. Two rows of one
     * behavior carrying one name leave that unanswerable, and the failure it produces is the silent
     * one: whatever keys on the name finds one of the two and nothing says the other was meant. So a
     * name is unique among the rows one behavior has, over the module's own source and every
     * {@code examples for} file attached to it, which is where two rows of one behavior most easily
     * collide.
     *
     * <p>Every row carrying a name more than one row carries is reported, each in the source it is
     * written in. Neither of two colliding rows is "the duplicate": which source came first is what a
     * caller hands the compiler — a build walks its files sorted, a command line takes them as typed —
     * so a rule that named one of them would say different things about one model depending on how
     * the compile was started. Reporting both needs no order at all, and it is what "checked where
     * the row is written" means when the two rows are written in different files.
     *
     * <p>Per source, because a position is a line and a column: this key can be quoted against this
     * file, and the module's key could only be quoted against the module's own (the reason
     * {@link Output.Examples} reports a duplicate value name the same way).
     */
    public record RowNames(SourceId id) implements Key<Boolean> {

        /** One name, as one behavior's rows carry it. */
        private record Carried(String target, String name) {}

        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            String module = db.ask(new ModuleOf(id)).value();
            Layout.Of layout = db.ask(new Layout()).value();
            CstFrontend.Parsed here = db.ask(new Parsed(id)).value();
            if (module == null || layout == null || here == null) {
                return Answer.of(Boolean.TRUE);   // what is wrong with the source is said where it is read
            }
            Map<Carried, Integer> carried = new LinkedHashMap<>();
            for (SourceId source : sourcesOf(layout, module)) {
                CstFrontend.Parsed parsed = db.ask(new Parsed(source)).value();
                if (parsed != null) {
                    forEachNamedRow(parsed.module(),
                            (target, name, _) -> carried.merge(new Carried(target, name), 1, Integer::sum));
                }
            }
            List<Report> reports = new ArrayList<>();
            forEachNamedRow(here.module(), (target, name, pos) -> {
                if (carried.getOrDefault(new Carried(target, name), 0) > 1) {
                    reports.add(Report.of(Diagnostic.at(pos)
                            .say(new ExampleMessage.TheNameIsOnMoreThanOneRow(name, target))
                            .build()));
                }
            });
            return reports.isEmpty() ? Answer.of(Boolean.TRUE) : Answer.of(Boolean.TRUE, reports);
        }

        /** The sources that write this module's rows: its own, and every file attached to it. */
        private static List<SourceId> sourcesOf(Layout.Of layout, String module) {
            List<SourceId> sources = new ArrayList<>();
            SourceId own = layout.idOfModule().get(module);
            if (own != null) {
                sources.add(own);
            }
            sources.addAll(layout.exampleFilesOf().getOrDefault(module, List.of()));
            return sources;
        }

        private static void forEachNamedRow(Ast.Module m, NamedRow read) {
            for (Ast.Example example : m.examples()) {
                for (Ast.ExampleRow row : example.rows()) {
                    if (row.identity() instanceof RowIdentity.Named named) {
                        read.accept(example.target(), named.name(), row.pos());
                    }
                }
            }
        }

        private interface NamedRow {
            void accept(String target, String name, SourcePos pos);
        }
    }

    /**
     * A source module that also exists on the path. Which one an import means would be decided by
     * nothing the author wrote, and the two would be different types under one name — the same
     * reason two sources may not share a name.
     */
    public record ShadowsPath(String name) implements Key<Boolean> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            Layout.Of layout = db.ask(new Layout()).value();
            ModulePath path = db.ask(new Path()).value();
            if (layout == null || path == null || !layout.idOfModule().containsKey(name)) {
                return Answer.of(Boolean.FALSE);
            }
            // Whether the path has the name, and not whether what it has can be read. Two modules
            // under one name are two answers to what that name means however either of them was
            // built, so this never depended on the reading — and asking by reading put every way an
            // artifact can fail into a question whose whole answer is yes or no.
            if (!ModuleReadback.carry(name, path.declarations())) {
                return Answer.of(Boolean.FALSE);
            }
            return Answer.absent(Report.raised(Diagnostic.say(new ModuleMessage.TheModuleIsCompiledHereAndOnThePath(name))
                            .hint(new ModuleMessage.RenameItOrDropTheDependency(name)).nowhere().build()));
        }
    }

    /**
     * Every module name this compilation knows — declared by a source, or read off the path whether
     * or not it may be used.
     *
     * <p>Knowing a name and being able to read what it declares are two questions, and this is the
     * first. An import of a module the path holds and this compilation refuses is a mistake about
     * what that module is called itself, not about whether there is any such thing; told the second
     * as well, an author is left with two reports that cannot both be true.
     */
    public record ModuleNames() implements Key<Set<String>> {
        @Override
        public Answer<Set<String>> compute(Db db) {
            Layout.Of layout = db.ask(new Layout()).value();
            FromPath.Of path = db.ask(new FromPath()).value();
            Set<String> names = new LinkedHashSet<>();
            if (layout != null) {
                names.addAll(layout.idOfModule().keySet());
            }
            if (path != null) {
                names.addAll(path.modules().keySet());
                names.addAll(path.refused());
            }
            return Answer.of(Ordered.set(names));
        }
    }

    /** The names a source module declares — every module this compilation could compile. */
    public record Declared() implements Key<List<String>> {
        @Override
        public Answer<List<String>> compute(Db db) {
            Layout.Of layout = db.ask(new Layout()).value();
            return layout == null ? Answer.of(List.of())
                    : Answer.of(List.copyOf(layout.idOfModule().keySet()));
        }
    }

    /**
     * Every module name {@code m} names: the ones it imports, the qualifier of every type it writes
     * with one, and the qualifier of every behavior it names that way. Neither kind of qualified
     * reference needs an import line, so reading only the import lines would leave a module it
     * reaches unread.
     *
     * <p>Public because a second set of published classes is read the same way
     * ({@link souther.compiler.meta.PublishedUniverse}): which modules a module's declarations name
     * is one question, and a reader that answered it a second time would answer it differently the
     * day a new way of naming one arrives.
     */
    public static Map<String, SourcePos> reaches(Ast.Module m) {
        Map<String, SourcePos> names = new LinkedHashMap<>();
        Map<String, String> aliases = new HashMap<>();
        for (Ast.Import imp : m.imports()) {
            names.putIfAbsent(imp.module(), imp.pos());
            if (imp.alias() != null) {
                aliases.put(imp.alias(), imp.module());
            }
        }
        // A type reference a pass wrote before resolution names nothing written anywhere, and has
        // no name to read a qualifier off. A `>->` stage and a `depends on` have one whatever wrote
        // them: `Ast.Var`'s constructor reads its name, so there is no such thing as one without —
        // the two are asked differently because the two answer differently, not by oversight.
        List<WrittenName> written = new ArrayList<>();
        for (Ast.TypeRef ref : Names.typeRefs(m)) {
            if (ref.written() != null) {
                written.add(ref.written());
            }
        }
        for (Ast.BehaviorDef b : m.behaviors()) {
            List<Ast.Var> named = switch (b) {
                case Ast.PipeBehavior pipe -> pipe.stages();
                case Ast.SpecBehavior spec -> spec.dependsOn();
            };
            for (Ast.Var ref : named) {
                written.add(ref.written());
            }
        }
        for (WrittenName ref : written) {
            int dot = ref.canonical().lastIndexOf('.');
            if (dot > 0) {
                String qualifier = ref.canonical().substring(0, dot);
                names.putIfAbsent(aliases.getOrDefault(qualifier, qualifier), ref.pos());
            }
        }
        names.remove(m.name());
        return names;
    }

    /** A module in the reserved namespace, or one named like a standard-library qualifier — or null
     * when the name is the module's to take. */
    static Report reservedNamespace(String name, SourcePos pos) {
        Diagnostic.Builder said = reservedNamespaceTaken(name);
        return said == null ? null : Report.raised(said.at(pos).build());
    }

    /**
     * Whether {@code name} is a name a module may take, as the diagnostic rather than the report,
     * and with no place on it.
     *
     * <p>Asked of the name and nothing else. Whether a module may be called this is settled by the
     * spelling, so a caller with a source to point at puts the place on afterwards and a caller
     * reading an artifact — which has no line anybody holds — has nothing to take off again. It used
     * to take a coordinate either way, which meant the artifact reader had to have parsed the module
     * before it could ask a question that never depended on the parse.
     */
    static Diagnostic.Builder reservedNamespaceTaken(String name) {
        if (name.equals(RESERVED) || name.startsWith(RESERVED + ".")) {
            return Diagnostic.say(new ModuleMessage.TheModuleIsInTheReservedNamespace(name));
        }
        // The short qualifiers are how the standard library is reached (`List.map`, `import
        // String`); a user module by one of these names would shadow the library and could not be
        // imported.
        if (Reserved.isQualifier(name)) {
            return Diagnostic.say(new ModuleMessage.TheModuleTakesTheStandardLibraryQualifier(name));
        }
        return null;
    }
}
