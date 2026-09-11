package souther.compiler.query;

import souther.compiler.execute.ExampleExecution;
import souther.compiler.observe.WrittenStatements;
import souther.compiler.observe.Observations;
import souther.compiler.observe.ArmObservation;
import souther.compiler.source.SourceId;

import souther.compiler.generated.EvaluationArtifact;
import souther.compiler.generated.GeneratedImplementations;
import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.generated.ProbeImage;
import souther.compiler.execute.ConstantConstruction;
import souther.compiler.execute.ConstantOutcome;
import souther.compiler.execute.ProgramExecution;
import souther.compiler.execute.WrittenValue;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.check.ExpandedClauseLookup;
import souther.compiler.check.InvariantStatements;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.ExpandedClauses;
import souther.compiler.types.TypeKey;
import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.DataChecker;
import souther.compiler.check.Lower;
import souther.compiler.check.ReqSig;
import souther.compiler.check.Sig;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.InvariantHeader;
import souther.compiler.check.TypeOps;
import souther.compiler.core.EnsuresEnforcement;
import souther.compiler.codegen.Backend;
import souther.compiler.codegen.Emissions;
import souther.compiler.codegen.Instrumentation;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ClassFileDeclarations;
import souther.compiler.meta.ModuleMetadata;
import souther.compiler.meta.ModuleReadback;
import souther.compiler.meta.ModulePath;

import souther.compiler.types.ValueName;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bytecode a module comes to, and the two things that can only be asked once it exists: whether
 * its constant constructions satisfy their invariants, and whether its examples hold.
 *
 * <p>Both of those load classes, so both read {@link Linked} rather than one module's own — an
 * example may reach across an import, and the class it reaches has to be there. {@link All} is what
 * a build writes out, and nothing that runs code reads it: a key that did would be recomputed by an
 * edit to any module in the workspace.
 */
public final class Output {

    private Output() {}

    /**
     * One module's classes as they are published: the program this compilation checked, with the
     * declarations added for an importer to read back.
     *
     * <p>What is added is read and never run, so a jar carries the program this compile checked and
     * not the same bytes as any other set of classes made from it. {@link Evaluated} is the same
     * program counted, and carries none of this.
     */
    public record Classes(String name) implements Key<Map<String, ClassFileImage>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, ClassFileImage>> compute(Db db) {
            Inputs in = inputs(db, name);
            if (in == null) {
                return Answer.absent();
            }
            try {
                Emissions emitted = Backend.generate(
                        shipped(in), in.scope(), in.scope().library().kernelSignatures(),
                        in.typePackages(), in.sigs(), in.imported(),
                        in.injected(),
                        in.callees(), in.requirements(), in.checked(), in.compositions(),
                        in.dischargeClauses(), in.invariantStatements(), in.shapes(), in.checks(),
                        in.standingCalls(), new TheTextsThisCompileHolds(db));
                publishDeclarations(db, emitted);
                return Answer.of(emitted.seal());
            } catch (CompileException e) {
                return Answer.absent(e);
            }
        }

        /**
         * The module as what ships carries it: without the methods emitted for its rows' operands.
         *
         * <p>A row runs against {@link Evaluated}'s classes, which keep them; what is written out is
         * the same program less definitions nothing it holds references — an operand's method is
         * reached from a row and from nothing else. Which definitions those are is read off the
         * correspondence the preparation constructed, not off the shape of a name.
         */
        private static Hir.Module shipped(Inputs in) {
            if (in.rowMethods().isEmpty()) {
                return in.lowered();
            }
            List<Hir.FnDef> kept = new ArrayList<>();
            for (Hir.FnDef fn : in.lowered().takenOn()) {
                if (!in.rowMethods().contains(fn.name())) {
                    kept.add(fn);
                }
            }
            return in.lowered().withTakenOn(kept);
        }

        /**
         * The parts of a generation both {@link Classes} and {@link Evaluated} need, asked once.
         *
         * <p>Both answer with a module's bytecode and differ only in whether each arm records that it
         * ran. Two copies of this would be two chances for the measured classes and the shipped ones to
         * stop being the same program, which is the one thing a measurement of them may not do.
         */
        record Inputs(Hir.Module lowered, DerivedSymbols scope, Map<String, String> typePackages,
                      Map<ValueName.Behavior, Sig> sigs, Map<ValueName.Behavior, Sig> imported,
                      Set<ValueName.Behavior> injected,
                      Map<ValueName.Behavior, ReqSig> callees,
                      Map<String, List<BehaviorRequirement>> requirements,
                      Bodies.Elaborated checked,
                      Map<ValueName.Behavior, souther.compiler.core.Composition> compositions,
                      ExpandedClauseLookup dischargeClauses,
                      InvariantStatements invariantStatements,
                      Map<souther.compiler.types.TypeSymbol.AtModule,
                              souther.compiler.core.ValueShape> shapes,
                      Map<ValueName.Behavior, EnsuresEnforcement> checks,
                      Set<String> rowMethods,
                      Map<String, souther.compiler.types.Type> standingCalls) {}

        static Inputs inputs(Db db, String name) {
            Answer<Bodies.Elaborated> checked = db.ask(new Bodies.Checked(name));
            // What each composed behavior routes, settled where the composition was checked. The
            // emitter used to walk the declaration for it a second time.
            Answer<Map<ValueName.Behavior, souther.compiler.core.Composition>> compositions =
                    db.ask(new Compositions.Of(name));
            Answer<Lower.Lowered> lowering = db.ask(new Bodies.Lowering(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            // The same answer the check read. The backend replays the composition walk and emits
            // the codecs a signature says are needed, so building its own would be the boundary's
            // question answered a third time.
            // The behaviors this module can name, each under the declaration it belongs to: what
            // the check typed the compositions against, so the emitter routes over the same ones.
            Answer<Map<ValueName.Behavior, Sig>> signatures = db.ask(new Bodies.Reachable(name));
            Answer<Map<ValueName.Behavior, Sig>> imported = db.ask(new Bodies.Imported(name));
            Answer<Set<ValueName.Behavior>> injected =
                    db.ask(new Bodies.ImportedInjected(name));
            Answer<Map<ValueName.Behavior, ReqSig>> callees = db.ask(new Bodies.CalleeSigs(name));
            Answer<souther.compiler.check.Prepared> prepared = db.ask(new Shapes.Prepared(name));
            Answer<Map<String, List<BehaviorRequirement>>> requirements =
                    db.ask(new Bodies.Requirements(name));
            // A derived decoder maps a clause onto the Raoh constraint that says the same thing, and it
            // is written against the operations an author wrote — which the lowered module no longer has.
            // Where each behavior of this module has its clause checked. A decision of the
            // language's, so it is asked for rather than made here: the emitter and the checked
            // program are two readers of it, and each making it from the contracts and the injected
            // set would be two answers to one question.
            // Whether this module's clauses could be expanded at all. A precondition of emitting
            // the module and not something the emitter reads: a decoder is what the boundary holds
            // a value to, so one built where a rule could not be read holds it to less than the
            // model says and carries no word for having done so. What a clause of a declaration is
            // still comes from the lookup below, one declaration at a time.
            Answer<Map<TypeKey, ExpandedClauses>> expandable =
                    db.ask(new Shapes.ExpandedDeclarationClauses(name));
            Answer<Map<ValueName.Behavior, EnsuresEnforcement>> checks =
                    db.ask(new Bodies.EnsuresChecks(name));
            // What must hold of a value of each declared data, and the binding each field is read
            // through. Both are the check's answer; the emitter used to elaborate the clauses again
            // and work the bindings out a second time.
            Answer<Map<souther.compiler.types.TypeSymbol.AtModule, souther.compiler.core.ValueShape>>
                    shapes = db.ask(new Shapes.ValueShapes(name));
            // What a call left standing is typed against — the same answer the check typed it
            // against. The emitter re-types the expressions it emits (a clause, a rule), so a
            // signature table of its own would be a second reading of what a name means, and the two
            // would agree only until one of them was edited.
            Answer<Map<String, souther.compiler.types.Type>> standing =
                    db.ask(new Bodies.RecursiveCallSigs(name, souther.compiler.check.InliningPolicy.FULL));
            // What each conjunct of a declaration's rules states. The mapping onto a decoder's
            // constraints is about what a rule says, and reading that off the tree recognises a rule
            // written out and declines the same rule named through a helper.
            Answer<RuleReadingSource> reading = Shapes.ruleReading(db, name);
            if (!checked.present() || !compositions.present()
                    || !lowering.present() || !scope.present() || !imported.present()
                    || !signatures.present() || !injected.present() || !callees.present()
                    || !prepared.present() || !requirements.present() || !expandable.present()
                    || !checks.present() || !standing.present() || !shapes.present()
                    || !reading.present()) {
                return null;
            }
            return new Inputs(lowering.value().lowered(), scope.value(),
                    prepared.value().importedFrom(), signatures.value(), imported.value(),
                    injected.value(),
                    callees.value(), requirements.value(), checked.value(), compositions.value(),
                    Shapes.expandedClauses(db), InvariantStatements.of(reading.value()),
                    shapes.value(), checks.value(),
                    Set.copyOf(prepared.value().operandMethods().values()), standing.value());
        }

        /**
         * Puts this module's declarations on the classes that ship, as its source wrote them.
         *
         * <p>What ships carries them so that another compilation can import this module from a jar
         * without its source. That is the whole of what they are for: a declaration written into a
         * class is read by {@link ModuleReadback} through {@link Output#declarationsRead}, which
         * reads the classes this compilation publishes and the ones on the path, and by nothing that
         * runs a program. So they go on here and not on {@link Evaluated}, whose classes are run and
         * never read for what the module declares.
         *
         * <p>Which is also what keeps a declaration's text out of what an evaluation is built from.
         * It is written down as it was written, comments and all, so rewording a comment beside a
         * declaration rewrites these classes; put on the evaluated classes as well, that would make
         * every example of the module something to establish again. What an edit does to the
         * positions under it is another matter and reaches an evaluation either way.
         *
         * <p>Nothing here can fail in a way worth reporting. A module with no source of its own was
         * read off the path, and its jar was stamped where it was built; and the declarations are
         * only asked for once the module has checked, so they are there.
         */
        private void publishDeclarations(Db db, Emissions classes) {
            Front.Layout.Of layout = db.ask(new Front.Layout()).value();
            SourceId id = layout == null ? null : layout.idOfModule().get(name);
            if (id == null) {
                return;
            }
            CstFrontend.Parsed written = db.ask(new Front.Parsed(id)).value();
            Answer<Map<String, Sig>> sigs = db.ask(new Bodies.Signatures(name));
            Map<String, souther.compiler.check.BehaviorImplementation> implementations =
                    db.ask(new Bodies.Implementation(name)).value();
            // The resolved module beside the written one. What a declaration reaches is read off
            // the names it resolved to, a clause being written among bindings that may be spelled
            // like a helper; and it is read before the invariants are settled, since settling
            // inlines the very calls this is looking for.
            Answer<souther.compiler.ast.Hir.Module> resolved = db.ask(new Names.Resolved(name));
            // How this module writes a type down, which is what a computed signature is published
            // in. Asked of the module rather than taken off the type: what a declaration is and
            // what this module calls it are two things, and only the second may be published.
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            if (written == null || !sigs.present() || implementations == null
                    || !resolved.present() || !scope.present()) {
                return;
            }
            ModuleMetadata.stamp(classes, written.module(), resolved.value(),
                    written.slices(), sigs.value(), implementations,
                    scope.value().scope()::reach);
        }

    }

    /**
     * Every class this compilation generates. A module that did not check contributes none, which is
     * what makes an example of a module that reaches it fail to load rather than run against
     * something that was never checked.
     */
    public record All() implements Key<Map<String, ClassFileImage>> {
        @Override
        public Answer<Map<String, ClassFileImage>> compute(Db db) {
            List<String> declared = db.ask(new Front.Declared()).value();
            Map<String, ClassFileImage> all = new LinkedHashMap<>();
            if (declared == null) {
                return Answer.of(Map.of());
            }
            for (String name : declared) {
                Map<String, ClassFileImage> classes = db.ask(new Classes(name)).value();
                if (classes != null) {
                    all.putAll(classes);
                }
            }
            return Answer.of(Ordered.map(all));
        }
    }

    /**
     * Every module whose classes a module can load: itself, and what it imports, transitively.
     *
     * <p>An example row constructs values of the types its module names and applies behaviors those
     * types reach, and a name it can write is one an import brought in — so the import graph is the
     * bound on what its evaluation loads. A module outside it cannot be reached by any name the row
     * can write.
     *
     * <p>The walk is iterative rather than a question asking itself about the module it reached,
     * because two modules naming each other would then be a question that depends on itself. That is
     * already an error, and it is found before any of this is asked, but this key is asked while the
     * error is on screen too.
     */
    public record Reaches(String name) implements Key<List<String>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<String>> compute(Db db) {
            Set<String> reached = new LinkedHashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.add(name);
            while (!pending.isEmpty()) {
                String module = pending.removeFirst();
                if (!reached.add(module)) {
                    continue;
                }
                List<String> imports = db.ask(new Front.ImportedModules(module)).value();
                if (imports != null) {
                    pending.addAll(imports);
                }
            }
            return Answer.of(List.copyOf(reached));
        }
    }

    /**
     * The classes an evaluation of one module's code loads: its own, and those of every module it
     * reaches.
     *
     * <p>Not {@link All}. What a module comes out as changes whenever anything its generation reads
     * changes, and {@link All} reads every module's — so a key built on it is recomputed by an edit
     * anywhere in the workspace, however far from what the edit was about. Reading only what it
     * reaches is what keeps an edit to one module from re-running the examples of every other one.
     */
    public record Linked(String name) implements Key<Map<String, ClassFileImage>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, ClassFileImage>> compute(Db db) {
            List<String> reaches = db.ask(new Reaches(name)).value();
            if (reaches == null) {
                return Answer.of(Map.of());
            }
            Map<String, ClassFileImage> linked = new LinkedHashMap<>();
            // Furthest first, so the module being evaluated is put on last: a name two of them
            // generate is the near one's, which is the order All puts them in as well.
            for (int i = reaches.size() - 1; i >= 0; i--) {
                Map<String, ClassFileImage> classes = db.ask(new Classes(reaches.get(i))).value();
                if (classes != null) {
                    linked.putAll(classes);
                }
            }
            return Answer.of(Ordered.map(linked));
        }
    }

    /**
     * One module's classes, counting what they go through — and, where asked, recording which arms
     * they took.
     *
     * <p>Its own key rather than an argument to {@link Classes}, for the reason a probed generation had one:
     * {@link Classes} is what ships, and widening it to mean "counted, sometimes" would put the
     * decision of whether a jar refers to the compiler inside a parameter. These are never stamped and
     * never written out.
     *
     * <p>Counting is not optional the way recording the arms is. Every evaluation runs against
     * counted classes, because what holds a row to a budget it cannot exceed is the counting itself
     * — a row evaluated against uncounted classes has nothing but a clock behind it.
     *
     * <p>Two answers and not three. An absence with nothing of its own to report says this compile
     * has no inputs to generate the module from; an absence carrying a refusal says the program was
     * refused, and the refusal is what a reader is owed. A generation that contradicts itself — a
     * node reached that the plan it was made from does not hold, an arm numbered that nothing wrote
     * — is neither, and answering with the first for it would report a module with no arms to read
     * exactly as a module whose arms were all read. So it is not caught here.
     */
    public record Evaluated(String name, ArmObservation arms)
            implements Key<EvaluationArtifact> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<EvaluationArtifact> compute(Db db) {
            Classes.Inputs in = Classes.inputs(db, name);
            if (in == null) {
                return Answer.absent();
            }
            Instrumentation instrumentation = arms == ArmObservation.RECORD
                    ? Instrumentation.COUNTING.measuring() : Instrumentation.COUNTING;
            try {
                Emissions emitted = Backend.generate(
                        in.lowered(), in.scope(), in.scope().library().kernelSignatures(),
                        in.typePackages(), in.sigs(), in.imported(),
                        in.injected(),
                        in.callees(), in.requirements(), in.checked(), in.compositions(),
                        in.dischargeClauses(), in.invariantStatements(), in.shapes(), in.checks(),
                        in.standingCalls(), new TheTextsThisCompileHolds(db), instrumentation);
                // The classes, what they implement and whose numbers a run through them leaves,
                // from the one emission that decided all three.
                return Answer.of(new EvaluationArtifact(emitted.seal(), emitted.implemented(),
                        emitted.probes()));
            } catch (CompileException e) {
                return Answer.absent(e);
            }
        }
    }

    /**
     * What an evaluation of one module's rows loads: every module the rows can reach, counted, with
     * arms recorded in the one module a measurement is about.
     *
     * <p>The two scopes differ on purpose, and getting them the same way round is what this key is
     * for. Arms belong to the module whose report reads the numbers, so probing an import would number
     * arms against a plan nothing here can read. A budget belongs to the evaluation, and an evaluation
     * goes wherever the row goes — so a row that steps into an import and loops there has to be
     * counted while it is in there, or the counting stops at the module boundary and the clock decides
     * again.
     *
     * <p>Only the modules this compilation declares are here. A module from the path is not
     * regenerated at all: what a published one carries is what an importer needs to read its
     * declarations, so regenerating it would produce some of its classes and not the rest, and a
     * module split between this set and the parent hands its own types to its own implementation
     * under two definitions of them. {@link #evaluationLoader} takes the path's classes whole and
     * counts them on the way in, so the counting still does not stop at the import.
     */
    public record EvaluationLinked(String name, ArmObservation arms)
            implements Key<EvaluationArtifact> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<EvaluationArtifact> compute(Db db) {
            List<String> reaches = db.ask(new Reaches(name)).value();
            if (reaches == null) {
                // What this module reaches always includes this module, so there is no such answer.
                // Absent rather than an empty set of classes with an empty manifest beside it: a
                // manifest saying nothing is generated is an answer, and it would tell every row that
                // nothing applies its behavior, which is what a module with no `let` anywhere looks
                // like. Nothing was worked out, so nothing is said.
                return Answer.absent();
            }
            Front.Layout.Of layout = db.ask(new Front.Layout()).value();
            Map<String, ClassFileImage> linked = new LinkedHashMap<>();
            GeneratedImplementations implemented = null;
            ProbeImage probes = new ProbeImage.Uninstrumented();
            // Furthest first, so the module being evaluated is put on last, as Linked does.
            for (int i = reaches.size() - 1; i >= 0; i--) {
                String reached = reaches.get(i);
                // Only the modules this compilation declares. A module from the path is not generated
                // here at all: what a published one carries is what an importer needs to read its
                // declarations, so regenerating it would produce some of its classes and not the rest
                // — and a module split between this loader and the parent hands its own types to its
                // own implementation under two different definitions of them. The evaluation loader
                // takes the path's classes whole and counts them on the way in.
                if (layout == null || !layout.idOfModule().containsKey(reached)) {
                    continue;
                }
                Answer<EvaluationArtifact> classes = db.ask(new Evaluated(reached,
                        reached.equals(name) ? arms : ArmObservation.OMIT));
                // A module this compilation declares and could not generate makes this absent rather
                // than making the set one class short. Evaluating against a set with a hole in it
                // produces a class that will not load, or a stale one found further up the loader
                // chain, or an example that fails for neither reason — and all three read as a fault
                // in the model.
                if (classes.value() == null) {
                    return Answer.absent(classes.reports());
                }
                linked.putAll(classes.value().classes());
                if (reached.equals(name)) {
                    // The probes are the evaluated module's: it is the one asked for them, and the
                    // ones it reaches were asked to record nothing.
                    probes = classes.value().probes();
                    implemented = classes.value().implementations();
                }
            }
            if (implemented == null) {
                // The module being evaluated was not generated here, so what applies its behaviors is
                // not known — and an evaluation is over its rows. Absent for the reason the loop above
                // is absent one class short: a run given no manifest for it would read every one of
                // its rows as one nothing applies.
                return Answer.absent();
            }
            return Answer.of(new EvaluationArtifact(Ordered.map(linked), implemented, probes));
        }
    }

    /** The class loader compile-time code runs against: this compilation's classes over the ones the
     * projects it depends on already built. */
    static ClassLoader loader(Db db, Map<String, ClassFileImage> classes) {
        ModulePath path = db.ask(new Front.Path()).value();
        ClassLoader parent = path == null ? Output.class.getClassLoader()
                : path.loader(Output.class.getClassLoader());
        return new MemoryClassLoader(classes, parent);
    }

    /**
     * Where this compilation reads declarations of a module from — its own generated classes first,
     * then the path.
     *
     * <p>The same two places the evaluation loader draws classes from, and in the same order, because
     * they are answers to one question: which module a name means here. A reader with only what this
     * compilation generated would find nothing for a module that arrived compiled, which is the
     * ordinary shape of a project rather than an edge of one.
     *
     * <p>Its own first, for the reason the loader has: a module being compiled here wins over one of
     * the same name on the path.
     */
    public static ClassFileDeclarations declarationsRead(Db db) {
        Map<String, ClassFileImage> generated = db.ask(new All()).value();
        ModulePath path = db.ask(new Front.Path()).value();
        return new ClassFileDeclarations(binaryName -> {
            ClassFileImage here = generated == null ? null : generated.get(binaryName);
            if (here != null) {
                return here.bytes();
            }
            return path == null ? null : path.bytes(binaryName);
        });
    }

    /**
     * The loader an <em>evaluation</em> runs against, where a class read from the path is counted on
     * the way in.
     *
     * <p>A published module carries what an importer needs to read its declarations and no more, so a
     * behavior's body stays in the jar it was built into. Regenerating what does travel and taking
     * the rest from the jar is the one thing that must not be done: a class defined here and one
     * defined by the parent are different types under one binary name, so a module split between them
     * hands its own types to its own implementation and the cast fails — which is reported as an
     * example that does not hold, about a model that is fine.
     *
     * <p>So the path's classes are defined here, whole, with a counted point on every backward
     * branch. One loader, one version of every type, and a row that loops inside a dependency spends
     * the budget there rather than running until the wait ends.
     */
    public static ClassLoader evaluationLoader(Db db) {
        ModulePath path = db.ask(new Front.Path()).value();
        ClassLoader compiler = Output.class.getClassLoader();
        if (path == null) {
            return compiler;
        }
        return new ClassLoader(compiler) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = path.bytes(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] counted = souther.compiler.evaluate.Recounted.of(bytes);
                return defineClass(name, counted, 0, counted.length);
            }
        };
    }

    /**
     * What this compilation allows one row's evaluation.
     *
     * <p>Read from the compilation rather than from a system property at the point of use, so that two
     * compiles in one JVM may differ and so that a long-lived one — a build daemon, an editor's
     * language server — is not held for its whole life to whatever the first compile in it read.
     */
    public static souther.compiler.execute.EvaluationPolicy policyOf(Db db) {
        souther.compiler.execute.EvaluationPolicy said = db.ask(new Front.Policy()).value();
        return said == null ? souther.compiler.execute.EvaluationPolicy.DEFAULT : said;
    }

    /**
     * Whether each constant newtype construction in a module satisfies its invariant, by running the
     * same bytecode a run-time construction would. A check that cannot be loaded or run here is left
     * to the run-time check.
     */
    public record ConstConstructions(String name) implements Key<Boolean> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            Answer<souther.compiler.check.Prepared> prepared = db.ask(new Shapes.Prepared(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            if (!prepared.present() || !scope.present()) {
                return Answer.absent();
            }
            List<DataChecker.ConstCheck> checks;
            try {
                checks = DataChecker.constNewtypeChecks(prepared.value().fns(), scope.value());
            } catch (CompileException e) {
                return Answer.absent(e);
            }
            if (checks.isEmpty()) {
                return Answer.of(Boolean.TRUE);
            }
            ProgramExecution execution = db.execution();
            List<Report> reports = new ArrayList<>();
            for (DataChecker.ConstCheck check : checks) {
                ConstantConstruction written = asked(db, check);
                // The other two answers say nothing here. A construction that holds is a
                // construction nobody is told about, and one this compile could not evaluate is
                // left to the check that runs when the program does (ADR-0032).
                if (execution.check(written) instanceof ConstantOutcome.Violates(var clause)) {
                    reports.add(Report.raised(Diagnostic.at(check.pos())
                                    .say(clause.isEmpty()
                                            ? new DataMessage.TheWrittenValueViolatesTheInvariant(
                                                    shown(written))
                                            : new DataMessage.TheWrittenValueViolatesTheClause(
                                                    shown(written), clause.get()))
                                    .build()));
                }
            }
            return reports.isEmpty() ? Answer.of(Boolean.TRUE) : Answer.absent(reports);
        }

        /**
         * The construction as a question about the program: what was written, and what the type it
         * builds is declared to hold of its values.
         *
         * <p>The clauses are read here and not by whatever runs the check. They are the declaration
         * of the module that declares the type, which is this compiler's to answer for; a runner
         * that read them itself would be reaching back into the query graph in the middle of
         * running, which is the arrangement this boundary replaces.
         */
        private ConstantConstruction asked(Db db, DataChecker.ConstCheck check) {
            return new ConstantConstruction(name, check.typeName(), check.type(),
                    writtenValue(check.value()), clausesOf(db, check), check.pos());
        }

        /** What the type is declared to hold of its values, in declaration order — the names, which
         *  are what a report of a constant that broke one quotes, and which every representation of
         *  the declaration agrees on. None where the declaring module has no scope here, which is a
         *  module this compilation does not have rather than one whose clauses could not be read. */
        private static List<ConstantConstruction.Clause> clausesOf(Db db,
                DataChecker.ConstCheck check) {
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, check.type().module());
            if (!scope.present()) {
                return List.of();
            }
            List<ConstantConstruction.Clause> named = new ArrayList<>();
            for (InvariantHeader clause
                    : TypeOps.invariantHeadersGoverning(check.type(), scope.value())) {
                named.add(new ConstantConstruction.Clause(clause.name()));
            }
            return named;
        }

        /**
         * The constant in the four a source can write it as.
         *
         * <p>A fold answers with the object it happened to make, and which of them it is is what
         * the language wrote. Anything else is this compiler having folded to something no source
         * states, which is not a fact about the program being compiled.
         */
        private static WrittenValue writtenValue(Object value) {
            return switch (value) {
                case Long whole -> new WrittenValue.Whole(whole);
                case Boolean truth -> new WrittenValue.Truth(truth);
                case String text -> new WrittenValue.Text(text);
                case java.math.BigDecimal decimal -> new WrittenValue.Decimal(decimal);
                default -> throw new IllegalStateException("a constant folded to "
                        + value.getClass().getName()
                        + ", which is not one of the four a source can write");
            };
        }

        /** The construction as the source wrote it, for the message that quotes it. */
        private static String shown(ConstantConstruction written) {
            return written.typeName() + "(" + switch (written.value()) {
                case WrittenValue.Text(String text) -> "\"" + text + "\"";
                case WrittenValue.Whole(long whole) -> String.valueOf(whole);
                case WrittenValue.Truth(boolean truth) -> String.valueOf(truth);
                case WrittenValue.Decimal(java.math.BigDecimal decimal) -> decimal.toString();
            } + ")";
        }
    }

    /**
     * Every input for which two of a module's written statements about one behavior answer
     * differently — a {@code fake} row or a {@code with} against an {@code example} row that records
     * what the behavior owes.
     *
     * <p>Asked of the module and not of a source, because the two sides of one disagreement need not
     * be in one file: a module's fakes are what its attached files' rows run against and the other way
     * round. Each source's key projects the ones written in it ({@link SaidDisagreements}), so one
     * disagreement is said at both of the places it is written and computed once.
     *
     * <p>The answer is what the check came to and not the disagreements alone: a statement that could
     * not be read within its budget is a statement nothing was decided about, and a bare list of
     * disagreements says that with the same empty list it says agreement with.
     */
    public record Disagreements(String name)
            implements Key<WrittenStatements.Readings> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<WrittenStatements.Readings> compute(Db db) {
            // Asked ahead of the reading environment, and answered rather than left absent: a
            // module that did not check states nothing yet, which is a different answer from one
            // this could not read.
            if (!db.ask(new Bodies.Checked(name)).present()) {
                return Answer.of(WrittenStatements.Readings.NONE);
            }
            ExampleExecution asked = ExampleExecutions.of(db, name);
            if (asked == null) {
                return Answer.absent();
            }
            if (!(db.execution().statements(asked)
                    instanceof souther.compiler.observe.StatementReading.Read(var said))) {
                return Answer.absent();   // nothing was read, so nothing is said
            }
            return Answer.of(said);
        }
    }

    /**
     * The module's disagreements ({@link Disagreements}), said.
     *
     * <p>One disagreement is one warning. It is anchored at the recorded row and points at the
     * stand-in, and it is said at both of the sources they are written in, so an author editing
     * either file is told. Which of the two carries the caret is not a claim that the other is the
     * one in the wrong; it is where a reader starts reading, and the message names both.
     *
     * <p>Its own key rather than a second thing {@link Examples} says. That key answers what a
     * source's rows turned out to be, and it goes absent for reasons that have nothing to do with
     * what two statements say about each other — a name the source declares twice, a dependency list
     * that could not be read — and took these with it. Asked of the module and not of a source,
     * because one disagreement is one thing to say and its two sides need not be in one file. The
     * value is {@code true} and the reports are the answer, as {@code Adequacy.Warnings} is.
     *
     * <p>What is still needed is a module that checks and links: no behavior is applied, but building
     * a fixture runs the decoders its types derive and the helpers it applies, and those are classes
     * this compile generated. So a module with a type error says nothing here — its rows say nothing
     * anywhere yet — and a batch compile that stops at the first error never asks.
     *
     * <p>A warning. The two contradict, and which of them the model is to be held to is not readable
     * from the text — that would be a claim about which side is derived from the other, and neither
     * is.
     *
     * <p>A fake whose table could not be read in time is a warning of its own (E1920): what it and
     * the rows state was not compared, which is not what saying nothing means here.
     */
    public record SaidDisagreements(String name) implements Key<Boolean> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            WrittenStatements.Readings read =
                    db.ask(new Disagreements(name)).value();
            if (read == null) {
                return Answer.of(true);
            }
            List<Report> reports = new ArrayList<>();
            // Each is filed under the source its own caret is in, which is what a report with
            // nothing beside its place is filed under.
            for (WrittenStatements.Disagreement d : read.disagreements()) {
                reports.add(Report.of(said(d)));
            }
            for (WrittenStatements.UnreadFake f : read.unread()) {
                reports.add(Report.of(unread(f)));
            }
            return Answer.of(true, reports);
        }

        /** One fake that could not be read: the caret on the behavior it names, and what stopped. */
        private static Diagnostic unread(WrittenStatements.UnreadFake f) {
            // Which of the three it was travels into the message, because what to do about them
            // differs — and one of them is not about the model at all.
            WrittenStatements.Unread why = f.why();
            Diagnostic.Builder said = Diagnostic.at(f.at())
                    .say(why.isDepth()
                            ? new ExampleMessage.NotComparedTheTableReachedItsDepthLimit(
                                    f.target(), why.limitShown())
                            : why.isSteps()
                                    ? new ExampleMessage.NotComparedTheTableSpentItsSteps(
                                            f.target(), why.limitShown())
                                    : why.isStack()
                                            ? new ExampleMessage.NotComparedTheTableRanOutOfStack(
                                                    f.target(), why.limitShown())
                                            : new ExampleMessage.NotComparedTheTableDidNotAnswer(
                                                    f.target(), why.limitShown()));
            return (why.isDepth()
                    ? said.hint(new ExampleMessage.TheTableComparedRecursesTooDeeply(f.target()))
                    : why.isSteps()
                    ? said.hint(new ExampleMessage.TheTableComparedGoesRoundTooManyTimes(f.target()))
                    : why.isStack()
                    ? said.hint(new ExampleMessage.TheStackGotThereFirstWhenComparing(f.target()))
                    : said.hint(new ExampleMessage.NotAnsweringIsNotTwoAnswers(f.target())))
                    .build();
        }

        /** One disagreement: the caret on the recorded row, a second region on the stand-in in
         * whichever file it was written in, and what each of them answers. */
        private static Diagnostic said(WrittenStatements.Disagreement d) {
            WrittenStatements.Statement recorded = d.recorded();
            WrittenStatements.Statement standIn = d.standIn();
            boolean viaWith = d.viaWith();
            // The region says which file it is in, and whether that is worth printing is the
            // renderer's — it already leaves the name out where it matches the one in the heading.
            // Deciding it here meant naming the source a second time beside a region that carries
            // one, which is two answers to one question and the shape #760 was.
            return Diagnostic.at(recorded.region())
                    .say(viaWith
                            ? new ExampleMessage.TheRowAndTheWithDisagree(d.behavior())
                            : new ExampleMessage.TheRowAndTheFakeDisagree(d.behavior()))
                    .secondary(standIn.region(),
                            viaWith ? new ExampleMessage.TheWithIsHere(d.behavior())
                                    : new ExampleMessage.TheFakeRowIsHere(d.behavior()))
                    .hint(viaWith
                            ? new ExampleMessage.WhatTheRowSaysAndWhatTheWithSays(recorded.answer(),
                                    standIn.answer())
                            : new ExampleMessage.WhatTheRowSaysAndWhatTheFakeSays(recorded.answer(),
                                    standIn.answer()))
                    .build();
        }
    }

    /**
     * What each behavior of one module wrote down, and what stopped a source being read.
     *
     * <p>The one place a module's sources are gathered. A behavior's rows are written across its own
     * file and any number of attached {@code examples for} files, so which rows it has is an answer
     * over all of them together — and a caller assembling that again decides for itself what a
     * source that did not answer means, which is a decision made here once: it counts against every
     * behavior, because which behaviors it wrote rows for is exactly what could not be read.
     *
     * <p>What is here are the rows as they were read and the reasons a reading fell short, and
     * nothing made of either. What a measurement makes of them is {@link Adequacy.RowReadings},
     * which is asked only where a build measures; what a behavior owes is a fact about the model,
     * and an output holding a checked program reads it whether or not anything was measured.
     */
    public record RowsRead(String name) implements Key<RowsRead.Of> {

        /**
         * What was read, by the behavior each row is of.
         *
         * <p>A reason belongs to one behavior or to more than one, and the two are kept apart here
         * rather than merged into each entry. Merged, the same reason is in the answer twice — once
         * under every behavior it counts against and once as itself — and a reader adding up what
         * it was told would count a source nobody could evaluate once per behavior in it.
         *
         * @param everywhere what stopped a reading in a way larger than one behavior, which counts
         *     against every behavior of the module, including the ones no entry names
         */
        public record Of(Map<String, ReadRows> byBehavior,
                         List<souther.compiler.observe.Incompleteness> everywhere) {

            /** What counts against {@code behavior}: what stopped a reading of its own rows, and
             *  what stopped one larger than any behavior. */
            public List<souther.compiler.observe.Incompleteness> gapsFor(String behavior) {
                List<souther.compiler.observe.Incompleteness> gaps =
                        new java.util.ArrayList<>(everywhere);
                ReadRows its = byBehavior.get(behavior);
                if (its != null) {
                    gaps.addAll(its.gaps());
                }
                return List.copyOf(gaps);
            }

            public Of {
                // Ordered, because what is read out of it is read in an order: a module's behaviors
                // are shown in the order they were gathered, and a map keyed by a hash would show
                // one nothing decided, which can differ between two runs of one compiler.
                byBehavior = java.util.Collections.unmodifiableMap(
                        new java.util.LinkedHashMap<>(byBehavior));
                everywhere = List.copyOf(everywhere);
            }
        }

        /**
         * One behavior's rows, and what stopped a reading of that behavior's own.
         *
         * <p>Every row the module wrote for it, in the order they are written, and not only the
         * ones something came back for. A reading that stopped leaves rows nothing was seen of —
         * the classes would not link, the source produced no observation at all — and those rows
         * are still rows someone wrote: listed as what was read, a reader would be handed a
         * behavior that says nothing about an input that is written down in front of it.
         *
         * <p>{@code gaps} is its own, and not everything that counts against it. What stopped a
         * reading of the whole source is larger than any behavior in it and is said once, beside
         * these; a reader that wants both asks {@link Of#gapsFor}.
         */
        public record ReadRows(List<ReadRow> rows,
                               List<souther.compiler.observe.Incompleteness> gaps) {

            public ReadRows {
                rows = List.copyOf(rows);
                gaps = List.copyOf(gaps);
            }

            /** The rows something came back for, which is what a measurement is counted over. */
            public List<souther.compiler.observe.RowOutcome> ran() {
                List<souther.compiler.observe.RowOutcome> out = new ArrayList<>();
                for (ReadRow row : rows) {
                    if (row instanceof ReadRow.Ran(souther.compiler.observe.RowOutcome outcome)) {
                        out.add(outcome);
                    }
                }
                return out;
            }
        }

        /**
         * One written row, and what became of reading it.
         *
         * <p>Two arms and no third: something came back for the row, or nothing did and this says
         * why. Written as a sum so that a reader deciding what to do with a module's rows has to
         * say what it does with the ones nothing came back for — dropped, they are rows an output
         * would never hear of, and a behavior would read as having nothing to say about them.
         */
        public sealed interface ReadRow {

            /** What the row names itself. */
            souther.compiler.observe.RowIdentity identity();

            /** Where it is written. */
            souther.compiler.diag.SourcePos at();

            /** The row ran, and this is what it came to. */
            record Ran(souther.compiler.observe.RowOutcome outcome) implements ReadRow {

                public Ran {
                    if (outcome == null) {
                        throw new IllegalArgumentException("a row that ran came to something");
                    }
                }

                @Override
                public souther.compiler.observe.RowIdentity identity() {
                    return outcome.identity();
                }

                @Override
                public souther.compiler.diag.SourcePos at() {
                    return outcome.at();
                }
            }

            /**
             * Nothing came back for the row, and why nothing did.
             *
             * <p>The reason is the reading's and not the row's: what the row states was never read,
             * so there is nothing about it to say beyond which row it is and that this compile did
             * not get to it.
             */
            record NotRun(souther.compiler.observe.RowIdentity identity,
                          souther.compiler.diag.SourcePos at,
                          souther.compiler.observe.Incompleteness.Code why) implements ReadRow {

                public NotRun {
                    if (identity == null || at == null || why == null) {
                        throw new IllegalArgumentException("a row nothing came back for is still a"
                                + " row, written somewhere, for a reason it was not read");
                    }
                    if (!why.leftNoRowRead()) {
                        throw new IllegalArgumentException(why + " is a reason a row that was read"
                                + " fell short, and this row was not read");
                    }
                }
            }
        }

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<RowsRead.Of> compute(Db db) {
            java.util.SequencedSet<SourceId> origins =
                    db.ask(new Front.ExampleSources(name)).value();
            if (origins == null) {
                return Answer.of(new RowsRead.Of(Map.of(), List.of()));
            }
            Answer<souther.compiler.check.Prepared> prepared = db.ask(new Shapes.Prepared(name));
            Map<String, List<ReadRow>> written = new LinkedHashMap<>();
            Map<String, List<souther.compiler.observe.Incompleteness>> stopped =
                    new LinkedHashMap<>();
            List<souther.compiler.observe.Incompleteness> everywhere = new ArrayList<>();
            Set<String> named = new LinkedHashSet<>();
            for (SourceId sourceId : origins) {
                Examples.Of observed = db.ask(Examples.asked(db, name, sourceId)).value();
                // What this source wrote and what became of reading it, put together here — where
                // both are still this source's. Flattened first and matched afterwards, a row of
                // one source takes a reason that happened in another: two sources exampling one
                // behavior leave two reasons under its name, and nothing in either says which row
                // it is about.
                readOneSource(prepared, sourceId, observed, written, named);
                if (observed == null) {
                    // The source was not evaluated at all. Which behaviors it wrote rows for is
                    // exactly what cannot be read, so it counts against every one of them.
                    everywhere.add(souther.compiler.observe.Incompleteness.ofSource(
                            souther.compiler.observe.Incompleteness.Code.OBSERVATION_ABSENT,
                            sourceId));
                    continue;
                }
                for (souther.compiler.observe.Incompleteness gap : observed.incompleteness()) {
                    java.util.Optional<String> one = gap.behavior();
                    if (one.isPresent()) {
                        stopped.computeIfAbsent(one.get(), _ -> new ArrayList<>()).add(gap);
                    } else {
                        everywhere.add(gap);   // larger than a behavior, so about all of them
                    }
                }
            }
            // Every behavior of the module, and not only the ones something was seen of. A gap
            // larger than a behavior counts against all of them, and keying this on what was seen
            // gave it to exactly the behaviors it was least about: one with no row at all is the
            // case a source nobody could evaluate matters most for, and it was the one that got
            // nothing.
            named.addAll(stopped.keySet());
            if (prepared.present() && prepared.value() != null) {
                prepared.value().behaviors().forEach(each -> named.add(each.name()));
            }
            Map<String, ReadRows> out = new LinkedHashMap<>();
            for (String behavior : named) {
                out.put(behavior, new ReadRows(written.getOrDefault(behavior, List.of()),
                        stopped.getOrDefault(behavior, List.of())));
            }
            return Answer.of(new RowsRead.Of(out, distinct(everywhere)));
        }

        /**
         * Every row the module wrote, by the behavior it is a row of, each with what came back for
         * it.
         *
         * <p>Read off what was written rather than off what came back, because those are two sets
         * and only the first is the model. A reading that stopped leaves the second short — the
         * classes would not link, a source produced no observation at all — and a row missing from
         * it is a row someone wrote that nothing downstream would ever hear of.
         *
         * <p>What is attached to a written row is the outcome recorded for it, found by what a row
         * names itself and where it is written, which is what an outcome is made with. A row with
         * none takes the reason its reading fell short for: whatever stopped that behavior's rows,
         * or what stopped the whole reading where nothing was said of the behavior.
         */
        static void readOneSource(Answer<souther.compiler.check.Prepared> prepared,
                SourceId sourceId, Examples.Of observed, Map<String, List<ReadRow>> into,
                Set<String> named) {
            if (!prepared.present() || prepared.value() == null) {
                // Nothing says what this source wrote, so what came back is all there is to say —
                // and a module whose declarations could not be read is one every reader is already
                // told about.
                if (observed != null) {
                    for (souther.compiler.observe.RowOutcome row : observed.rows()) {
                        into.computeIfAbsent(row.target(), _ -> new ArrayList<>())
                                .add(new ReadRow.Ran(row));
                        named.add(row.target());
                    }
                }
                return;
            }
            for (souther.compiler.check.Prepared.Example block
                    : prepared.value().forExamplesWrittenIn(sourceId).examples()) {
                souther.compiler.ast.Hir.Example written = block.read();
                List<ReadRow> mine = into.computeIfAbsent(written.target(),
                        _ -> new ArrayList<>());
                named.add(written.target());
                for (souther.compiler.ast.Hir.ExampleRow row : written.rows()) {
                    souther.compiler.observe.RowOutcome came = observed == null ? null
                            : among(observed.rows(), written.target(), row);
                    mine.add(came != null ? new ReadRow.Ran(came)
                            : new ReadRow.NotRun(row.identity(), row.pos(),
                                    whyNothingCameBack(written.target(), row, observed, sourceId)));
                }
            }
        }

        /** The outcome recorded for {@code row} of {@code behavior}, or null where nothing came
         * back for it. A row is what it names itself and where it is written, which is what an
         * outcome carries of it. */
        private static souther.compiler.observe.RowOutcome among(
                List<souther.compiler.observe.RowOutcome> outcomes, String behavior,
                souther.compiler.ast.Hir.ExampleRow row) {
            for (souther.compiler.observe.RowOutcome each : outcomes) {
                if (each.target().equals(behavior) && each.identity().equals(row.identity())
                        && each.at().equals(row.pos())) {
                    return each;
                }
            }
            return null;
        }

        /**
         * Why nothing came back for a row, taken from what happened where the row is written.
         *
         * <p>This source and no other. A behavior may be exampled in its own module and in an
         * attached file, and what stopped a reading of one of them says nothing about the other —
         * a row taking a reason from wherever one was recorded under its behavior's name would be
         * told about a file it is not in.
         *
         * <p>Where nothing was observed of the source at all, that is the reason and it is the
         * source's. Otherwise it is what this source recorded of this behavior, and a reading is
         * only ever short of a row for a reason it recorded — so a row with neither an outcome nor
         * a reason is this compiler having lost one, which is the thing a reader must never be
         * handed as a row that was never written.
         */
        private static souther.compiler.observe.Incompleteness.Code whyNothingCameBack(
                String behavior, souther.compiler.ast.Hir.ExampleRow row, Examples.Of observed,
                SourceId sourceId) {
            if (observed == null) {
                return souther.compiler.observe.Incompleteness.Code.OBSERVATION_ABSENT;
            }
            for (souther.compiler.observe.Incompleteness gap : observed.incompleteness()) {
                if (gap.code().leftNoRowRead()
                        && gap.behavior().map(behavior::equals).orElse(true)) {
                    return gap.code();
                }
            }
            throw new IllegalStateException("nothing came back for " + behavior + " "
                    + row.identity().shown() + " in " + sourceId + ", and nothing there says why:"
                    + " a reading is short of a row only for a reason it recorded");
        }

        /** One entry per reason. A module's classes failing to be instrumented is one fact, and
         * looking for them once per source is not three facts. */
        private static List<souther.compiler.observe.Incompleteness> distinct(
                List<souther.compiler.observe.Incompleteness> gaps) {
            Map<Object, souther.compiler.observe.Incompleteness> byIdentity = new LinkedHashMap<>();
            for (souther.compiler.observe.Incompleteness gap : gaps) {
                byIdentity.putIfAbsent(gap.identity(), gap);
            }
            return List.copyOf(byIdentity.values());
        }
    }

    /**
     * The examples of one module, evaluated. Every module's examples are evaluated before any
     * failure stops a compile, so a change to a widely-imported data says how far it reaches in one
     * compile rather than one module per round.
     */
    public record Examples(String name, SourceId sourceId, ArmObservation arms)
            implements Key<Examples.Of> {

        /**
         * The rows of one source, run the one way this compilation runs them.
         *
         * <p>Which mode is derived here rather than chosen by the caller, so that a compile evaluates
         * its rows once. What a measurement needs beyond a compile is the arms each row took, and
         * asking for that as a second key ran every row a second time — against different bytecode,
         * on a second wait, with a second chance to be reported differently. Two sets of outcomes for
         * one model can disagree, and a report built half from each says a case is verified and its
         * branch unreached in the same breath.
         *
         * <p>Keyed on what is actually emitted rather than on the adequacy level, because two levels
         * that measure the same thing should not be two compiles.
         */
        public static Examples asked(Db db, String name, SourceId sourceId) {
            return new Examples(name, sourceId, Adequacy.armsAsked(db));
        }

        /**
         * What this source's rows turned out to be.
         *
         * <p>The answer carries a value even when rows failed, because a failing row is still an
         * observation: it says which case the behavior actually produced, and which inputs were legal.
         * An answer that went absent on the first failure would leave every adequacy measure reading
         * nothing and reporting gaps that the rows in front of it already covered.
         */
        public record Of(List<souther.compiler.observe.RowOutcome> rows,
                         List<souther.compiler.observe.Incompleteness> incompleteness) {

            public static final Of NONE = new Of(List.of(), List.of());

            public Of {
                rows = List.copyOf(rows);
                incompleteness = List.copyOf(incompleteness);
            }
        }

        @Override
        public String module() {
            return name;
        }

        @Override
        public SourceId sourceId() {
            return sourceId;
        }

        /**
         * The rows this source wrote, run — and the fakes it wrote, built.
         *
         * <p>The fakes here rather than in {@link #evaluate}, which a measurement runs a second time
         * against instrumented classes. Whether a table can be built
         * is not a question a measurement asks, and asking it twice would have one table answered for
         * by two builds.
         */
        @Override
        public Answer<Examples.Of> compute(Db db) {
            Answer<Examples.Of> ran = evaluate(db, name, sourceId, arms);
            if (!(fakeTables(db, name, sourceId)
                    instanceof souther.compiler.observe.TableBuild.Built(
                            List<Diagnostic> wrong))) {
                // The tables this source's fakes state were not built, so this key did not answer
                // for the source. A fake is a statement about a behavior the way a row is, and an
                // answer that carried the rows and said nothing about the fakes reads as fakes that
                // are fine — which is what a source writing only fakes got, because its rows are
                // empty and nothing else here had anything to say.
                //
                // Absent is how a source that produced no observation is said; the reading that
                // counts it names it for what it is (`OBSERVATION_ABSENT`, of the source).
                return Answer.absent(ran.reports());
            }
            if (wrong.isEmpty()) {
                return ran;
            }
            List<Report> reports = new ArrayList<>(ran.reports());
            for (Diagnostic d : wrong) {
                reports.add(Report.of(d));
            }
            return new Answer<>(ran.value(), reports);
        }

        /**
         * What building the fakes written in this source says about them.
         *
         * <p>Every fake it wrote, whether or not a row reaches one. A fake is a statement about the
         * behavior it stands in for the way a row is, and the two other places a table is built each
         * need something else to be there — a row of a behavior that depends on the faked one
         * ({@code resolveFake}), or a row recorded for the faked one itself (the reading behind
         * {@link Disagreements}) — so a module that writes neither had its table built nowhere.
         *
         * <p>Which source wrote which fake is passed rather than used here: which of two tables
         * written for one dependency answers is a fact about the module, so the reading that knows
         * that is the one that picks what this source's share of them is.
         */
        private static souther.compiler.observe.TableBuild fakeTables(Db db, String name,
                SourceId sourceId) {
            ExampleExecution asked = ExampleExecutions.of(db, name);
            return asked == null
                    ? new souther.compiler.observe.TableBuild.NotBuiltHere()
                    : db.execution().fakeTables(asked, sourceId);
        }

        /**
         * The rows of one source, run.
         *
         * <p>What varies between running rows to compile a module and running them to measure it is
         * what the run records, and that is what is passed. Which classes carry it is the runner's,
         * so the two cannot become two evaluations by a caller reaching for a different set: a row
         * that held under one and failed under the other would be a difference in the measurement
         * and not in the model, and the report has no way to tell.
         */
        static Answer<Examples.Of> evaluate(Db db, String name, SourceId sourceId,
                                            ArmObservation arms) {
            ExampleExecution asked = ExampleExecutions.of(db, name);
            if (asked == null) {
                return Answer.absent();   // nothing here can have its examples evaluated yet
            }
            if (asked.forExamplesWrittenIn(sourceId).examples().isEmpty()) {
                return Answer.of(Examples.Of.NONE);
            }
            List<Report> reports = new ArrayList<>(alreadyDeclared(db, name, sourceId));
            if (!reports.isEmpty()) {
                return Answer.absent(reports);   // a row naming one would read the other declaration
            }
            if (!(db.execution().run(asked, sourceId, arms)
                    instanceof souther.compiler.observe.RowRun.Ran(Observations observed))) {
                // Nothing ran. Where the arms were wanted, that is a measurement nobody made and it
                // is said so — falling back to a run that records nothing is not open, because what
                // holds a row to a budget is the counting and a row run without it is back on the
                // clock. Where they were not, nothing was worked out at all.
                return arms == ArmObservation.OMIT ? Answer.absent()
                        : Answer.of(new Examples.Of(List.of(), List.of(
                                souther.compiler.observe.Incompleteness.of(
                                        souther.compiler.observe.Incompleteness.Code.INSTRUMENTATION_ABSENT,
                                        souther.compiler.observe.Incompleteness.Scope.MODULE, name))));
            }
            for (Diagnostic failure : observed.failures()) {
                reports.add(Report.of(failure));
            }
            return Answer.of(new Examples.Of(observed.rows(), observed.incompleteness()), reports);
        }

        /**
         * A value this source declares under a name something already declares — the module itself, or an
         * attached file ahead of this one. The rows would read that other declaration, so the one written
         * here would say nothing.
         *
         * <p>Reported from this key rather than from the module's own duplicate check because a position
         * is a line and a column: the module's key can only be quoted against the module's file, and the
         * position is in this one.
         */
        private static List<Report> alreadyDeclared(Db db, String name, SourceId sourceId) {
            Front.Layout.Of layout = db.ask(new Front.Layout()).value();
            if (layout == null || sourceId.equals(layout.idOfModule().get(name))) {
                return List.of();   // the module's own source declares what it declares
            }
            Set<String> taken = new LinkedHashSet<>(declaredIn(db, layout.idOfModule().get(name)));
            List<Report> reports = new ArrayList<>();
            for (SourceId id : layout.exampleFilesOf().getOrDefault(name, List.of())) {
                CstFrontend.Parsed parsed = db.ask(new Front.Parsed(id)).value();
                if (parsed == null) {
                    continue;
                }
                for (Ast.FnDef value : parsed.module().fns()) {
                    boolean fresh = taken.add(value.name());
                    if (!fresh && id.equals(sourceId)) {
                        reports.add(Report.of(Diagnostic.at(value.written().reportedAt())
                                .say(new ExampleMessage.TheNameIsAlreadyDeclared(value.name(), name))
                                .build()));
                    }
                }
                if (id.equals(sourceId)) {
                    break;   // the files after this one are their own key's to report
                }
            }
            return reports;
        }

        /** The names the values of one source declare, or none where nothing parsed it. */
        private static Set<String> declaredIn(Db db, SourceId id) {
            Set<String> names = new LinkedHashSet<>();
            CstFrontend.Parsed parsed = id == null ? null : db.ask(new Front.Parsed(id)).value();
            if (parsed != null) {
                for (Ast.FnDef fn : parsed.module().fns()) {
                    names.add(fn.name());
                }
            }
            return names;
        }

    }
}
