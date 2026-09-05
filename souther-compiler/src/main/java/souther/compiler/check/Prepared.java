package souther.compiler.check;

import souther.compiler.source.SourceId;

import souther.compiler.ast.Hir;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The module a check and a codegen run over: its imported names written as the definitions they
 * denote, and the helpers its artifact must carry taken on as its own definitions.
 *
 * <p>Two things and one reason. A name an import brought in is written qualified because that is the
 * spelling the table a call expands against is keyed by, and a definition is taken on because the
 * class this module emits has to hold a method for it. What is taken on is not one kind of thing: a
 * recursive helper another module declared, which cannot be inlined into whoever calls it, and a
 * definition minted here for what a row writes at a position, which has no call site to be inlined
 * into. They arrive for different reasons and leave as the same list, because every reader of it —
 * the expansion table, the check, the backend, the fixture reader — wants the same thing of them,
 * which is that the artifact carries them. Which of the two one is, the definition says
 * ({@link Hir.FnDef#role}).
 *
 * <p>Scoped to the module and not to an output. The classes are emitted once per module
 * ({@code Output.Classes}), and every example row attached to it — from its own file and from every
 * {@code examples for} file naming it — runs against those classes. So what is emitted for a row is
 * emitted over all of them, and which rows are reported on is a question asked later, by
 * {@code Output.Examples}, which carries the source file in its key. A state that took one would be
 * pulling an output's concern up into what the module is.
 *
 * <p>What it holds is parts and not a tree. Each route out of it answers with the state of the part
 * it is for, and the projection writes those back into the shape a whole-module pass takes. The
 * other way round is what this rung used to be: the rungs below answered per declaration and per
 * definition, the assemblies poured the answers into an {@link Hir.Module}, and from here there was
 * nothing left to hand over but nodes.
 */
public final class Prepared {

    /** That every declaration this module writes came out and every definition it wrote did — the
     *  half a reader wanting a whole module has, and the half {@link CheckSurface} does not claim. */
    private final Desugared.Module desugared;
    private final CheckSurface surface;
    private final List<Hir.FnDef> takenOn;
    /** Worked out once, as the rungs below work theirs out. */
    private volatile Hir.Module projected;

    private Prepared(Desugared.Module desugared, CheckSurface surface) {
        this.desugared = desugared;
        this.surface = surface;
        this.takenOn = List.of();
    }

    /**
     * The definitions minted for this module's row operands, in the order they were emitted.
     *
     * <p>The assembly's, handed on. A row's operand is a value this compilation writes for it,
     * reached from a row and from nothing a source can spell, and the walk that minted it is the one
     * that knows which method is whose.
     */
    public List<Hir.FnDef> rowDefs() {
        return surface.rowDefs();
    }

    /**
     * The assembly, beside the witness that every declaration this module writes came out.
     *
     * <p>Two halves and neither is made here. {@link CheckSurface} is where the parts were joined —
     * an imported name written out, a definition held to what it came out as, a row's operand given
     * a method — and it is the same assembly a best-effort reading of the module is given, so
     * nothing about the module is decided twice. {@link Derived.Module} is the claim the assembly
     * does not make, and it is what everything reached from here rests on: a module one of whose
     * declarations has no representation is one there is nothing to emit for.
     *
     * <p>Refused where the two were not made from one antecedent, and the antecedent is compared
     * rather than anything read off it. Three of them, one for each part this answers with: what
     * a reader gets from here is the assembly's definitions and the witness's declarations, so both
     * have to have been made from the same things. The settling both
     * were built over is compared as the state and not as its tree — a settling answers a module
     * beside the recursive calls its clauses left standing, and two that left different calls
     * standing share the tree. The normalized declarations are compared as themselves, because
     * normalizing reads the names a module was resolved against and two readings of one tree against
     * two scopes are two declarations.
     *
     * <p>A name is what a module is called and not which module it is, so without this two
     * compilations each writing a {@code module m} pair: the fields of one module's declarations
     * beside the claim made about another's, and every reader below told something true of a module
     * it is not looking at.
     *
     * @throws IllegalArgumentException where the witness and the assembly were not made from one
     *     settling and one reading of its declarations
     */
    public static Prepared prepare(Desugared.Module desugared, CheckSurface surface) {
        if (!desugared.settledFrom(surface.settling())) {
            throw new IllegalArgumentException("the declarations of `" + desugared.name()
                    + "` were not derived from the settling `" + surface.name()
                    + "` was assembled over");
        }
        List<Normalized.Def> derived = new ArrayList<>();
        for (Derived.Def def : desugared.defs()) {
            derived.add(def.declaration());
        }
        if (!derived.equals(surface.declarations())) {
            throw new IllegalArgumentException("the declarations `" + desugared.name()
                    + "` had a representation derived for are not the ones `" + surface.name()
                    + "` was assembled from");
        }
        if (!desugared.fns().equals(surface.desugaredFrom())) {
            throw new IllegalArgumentException("the definitions `" + desugared.name()
                    + "` came out with are not the ones `" + surface.name()
                    + "` was assembled from");
        }
        return new Prepared(desugared, surface);
    }

    /** What the module is called. */
    public String name() {
        return surface.name();
    }

    /** The behaviors this module declares, which no rung at or below this one rewrites. */
    public List<Hir.BehaviorDef> behaviors() {
        return surface.behaviors();
    }

    /**
     * Where {@code behavior}'s body comes from (spec §injected-behavior, §unwritten-behavior).
     *
     * <p>How the behavior is written. What a compile emitted for it and what a run can apply are
     * different questions with owners of their own, and this is not an answer to either: a reader
     * wanting to know whether anything will run a behavior asks what will run it.
     *
     * <p>The state and not a flag, so that a reader says which of the two questions about a
     * body-less behavior it is asking: whether there is anything here to run, or whether Java is the
     * one supplying it. Those are the same answer for a behavior with no {@code depends on} and
     * different answers for one that declares it (issue #936).
     */
    public BehaviorImplementation implementationOf(Hir.BehaviorDef behavior) {
        return Requirements.implementationOf(module(), behavior);
    }

    /** Whether {@code behavior}'s body is written here as a {@code let} of its own name, which a
     *  {@code >->} composition's is not. Read from the declarations, so it answers whether or not
     *  this module was elaborated ({@link Requirements#writesItsOwnBody}). */
    public boolean writesItsOwnBody(Hir.BehaviorDef behavior) {
        return Requirements.writesItsOwnBody(module(), behavior);
    }

    /** Whether {@code behavior} is a {@code >->} composition, whose positions, lines and arms are
     *  its stages' ({@link Requirements#isComposition}). */
    public boolean isComposition(Hir.BehaviorDef behavior) {
        return Requirements.isComposition(behavior);
    }

    /** The names its source offers to whatever reads it, which no stage rewrites. */
    public List<String> exposing() {
        return surface.exposing();
    }

    /**
     * Its declarations, each of them the derived declaration and not the node.
     *
     * <p>The answers the rung below gave, handed on. Nothing here rewrites a declaration, so there
     * is nothing that could have made the claim false and no reason to hand over a value that no
     * longer carries it.
     */
    public List<Derived.Def> defs() {
        return desugared.defs();
    }

    /** The example blocks attached to this module, from its own file and from every file naming
     * it, each of them read the way this module reaches its names. */
    public List<Example> examples() {
        List<Example> blocks = new ArrayList<>();
        for (Hir.Example block : surface.examples()) {
            blocks.add(new Example(block));
        }
        return List.copyOf(blocks);
    }

    /** Its definitions, the taken-on ones not among them — those are what the artifact carries
     * beside what the module wrote. */
    public List<Desugared.Fn> fns() {
        return surface.fns();
    }

    /** The fake tables it writes, each read the way this module reaches its names. */
    FakeTables fakes() {
        return surface.fakes();
    }

    /** Which module each imported name came from. */
    public Map<String, String> importedFrom() {
        return surface.importedFrom();
    }

    /**
     * Which method each row operand's value runs as, by the operand.
     *
     * <p>The correspondence {@link #prepare} constructed when it emitted the methods, keyed on
     * operand identity. Every reader that needs to know which method is whose reads this; nothing
     * re-derives it by counting rows, because a count over anything but the whole walk is a
     * different numbering.
     */
    public Map<Hir.Expr, String> operandMethods() {
        return surface.operandMethods();
    }

    /**
     * This module's artifact with all of its rows — what an example run over the module is given.
     */
    public ForExamples forExamples() {
        return new ForExamples(this, examples());
    }

    /**
     * The same over the rows {@code sourceId} wrote, which each block says of itself.
     *
     * <p>The selection is made here rather than handed in. A module's rows come from its own file
     * and from every {@code examples for} file naming it, and a run reports on one of those files at
     * a time — so what varies is which of these rows, and an operation that took a list of them
     * would be a way to put any rows at all into the state that says these are the module's.
     *
     * <p>Read off each block's own place. A parallel record of where the blocks came from would be
     * the same answer kept somewhere else, and a caller holding one that had fallen out of step had
     * no way to know.
     */
    public ForExamples forExamplesWrittenIn(SourceId sourceId) {
        List<Example> mine = new ArrayList<>();
        for (Example block : examples()) {
            if (block.read().pos().isIn(sourceId)) {
                mine.add(block);
            }
        }
        return new ForExamples(this, mine);
    }

    /**
     * One example block of this module, with every name in it that denotes another module's
     * definition written qualified.
     *
     * <p>Its own type because a reader of one leans on it and would say something else without it:
     * a row whose names are written bare builds no fixture, and what the author is told is that the
     * fixture cannot be built — a report about their model for a reason that is not in it. Measured
     * over a compile of the suite, where the rows qualification touches are the rows whose readings
     * change and no others.
     *
     * <p>Reached from {@link #prepare} and from nothing else.
     */
    public static final class Example {

        private final Hir.Example block;

        private Example(Hir.Example block) {
            this.block = block;
        }

        /** The behavior the rows are about. */
        public String target() {
            return block.target();
        }

        /**
         * The block.
         *
         * <p>For a reader that holds this state, as {@link Desugared.Fn#read()} is. What the state
         * says is that the names in it are the ones this module reaches, and a reader that walks
         * the rows has been handed that rather than left to hope for it.
         */
        public Hir.Example read() {
            return block;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Example other && block.equals(other.block);
        }

        @Override
        public int hashCode() {
            return block.hashCode();
        }
    }

    /**
     * The module projected for an example run: the example blocks it reads, and the artifact their
     * rows are evaluated in.
     *
     * <p>Its own type because that pairing is what an example run needs and neither half is enough:
     * the blocks say what to try and the artifact says what is there to try it against — a row's
     * operand is a method because this module emitted one for it, and a run that was handed the
     * blocks alone would be looking for it in a class that does not carry it.
     *
     * <p>What it claims is about its input and not about its outcome. Nothing here says a row
     * agreed with anything; that is what running them answers.
     */
    public static final class ForExamples {

        private final Prepared module;
        private final List<Example> examples;

        private ForExamples(Prepared module, List<Example> examples) {
            this.module = module;
            this.examples = List.copyOf(examples);
        }

        /** What the module is called. */
        public String name() {
            return module.name();
        }

        /** The behaviors a row names. */
        public List<Hir.BehaviorDef> behaviors() {
            return module.behaviors();
        }

        /** The definitions the module wrote. */
        public List<Desugared.Fn> fns() {
            return module.fns();
        }

        /** Where {@code behavior}'s body comes from. How it is written, and no answer to what will
         *  run it. A fake stands in for an injection target; a row waits for either state with no
         *  body here. */
        public BehaviorImplementation implementationOf(Hir.BehaviorDef behavior) {
            return module.implementationOf(behavior);
        }

        /**
         * The definitions its artifact carries beside them — what a row applies.
         *
         * <p>Nodes, and the boundary is why. What is taken on is another module's definition,
         * reached through what that module published; it comes here from a question that is not a
         * rung of this ladder and holds no proposition to pass on ({@code Bodies.Settled}, measured
         * in #710). A state claimed of one would be claimed of a value nothing here established it
         * of.
         */
        public List<Hir.FnDef> takenOn() {
            return module.takenOn;
        }

        /** The example blocks this run is over. */
        public List<Example> examples() {
            return examples;
        }

        /** What its rows run against, which is the module's whole and not one file's: a module's
         * own fakes are what its attached files' rows run against, and the other way round. */
        public FakeTables fakes() {
            return module.fakes();
        }

        /**
         * The behavior an {@code example} of this module names.
         *
         * <p>A row targets a behavior of the module it is written in (spec §example-evaluable), so
         * a target read off a row is a declaration of this one. Said here because every reader of a
         * row needs it and each restating it would be that rule kept in several places — which is
         * the shape a stand-in's target was in before it carried what it denotes.
         */
        public ValueName.Behavior targeted(String behavior) {
            return new ValueName.Behavior(name(), behavior);
        }

        /**
         * Every behavior this module writes a stand-in for: the target of a {@code fake} and the
         * dependency a {@code with} on a row supplies.
         *
         * <p>Both forms, because both are stand-ins (spec §example-fakes) and a reader asking what
         * this module states about a behavior wants either. Told apart from
         * {@link FakeTables#answering}, which asks which *block* stands in for a dependency: a
         * {@code with} writes no table, so a reader taking that answer for this one passes over
         * every behavior a row supplies without one — which is how a {@code with} for a dependency
         * another module declares came to be compared against nothing.
         *
         * <p>What is written and not what is comparable. Whether a stand-in can be held against a
         * recorded row turns on what the dependency takes, and that is the reading's question to
         * ask; this one is answered from the text.
         *
         * <p>The module's, not one file's, as {@link #fakes} is: a module's stand-ins are what its
         * attached files' rows run against and the other way round, so which modules a reading has
         * to take the rows of is a fact about the module. Read off {@link #examples} it would be one
         * file's {@code with}s wherever this is a projection of a source, and the modules the other
         * files reach would go unread — which is the reading this answer exists to complete.
         */
        public Set<ValueName.Behavior> standsInFor() {
            Set<ValueName.Behavior> named = new LinkedHashSet<>(module.fakes().answering().keySet());
            for (Example block : module.examples()) {
                for (Hir.ExampleRow row : block.read().rows()) {
                    for (Hir.With supplied : row.withs()) {
                        if (supplied.standsInFor() != null) {
                            named.add(supplied.standsInFor());
                        }
                    }
                }
            }
            return named;
        }

        /** Which method each row operand runs as, whole-module like the fakes: the methods were
         * emitted over every file's rows, so a run over one file's reads the correspondence the
         * emission constructed rather than numbering its own subset from zero. */
        public Map<Hir.Expr, String> operandMethods() {
            return module.operandMethods();
        }

        /** The artifact the rows run in. */
        Hir.Module module() {
            return module.module();
        }
    }

    /**
     * The parts written back into the shape a pass over a whole module takes.
     *
     * <p>Built in one place and never read back into parts. This is where the module leaves the
     * ladder: {@code Lower.settle} hands it to a pass that settles helper parameter types across
     * the whole of it, and what that answers with is a tree carrying no proposition — measured in
     * #710, which is why {@code Bodies.Settled} is not a rung.
     */
    Hir.Module module() {
        Hir.Module built = projected;
        if (built != null) {
            return built;
        }
        projected = built = surface.module().withTakenOn(takenOn);
        return built;
    }

    /**
     * The same, for the tests that audit what a module carries at each stage.
     *
     * <p>They ask about the payload rather than about the claim. What the checks, the adequacy
     * report and the runner read is the parts above, each of which is what this state has to say
     * about that part; a reader wanting one of those asks for it rather than for this.
     */
    public Hir.Module tree() {
        return module();
    }

    /**
     * Everything this state answers with.
     *
     * <p>Not the projection alone. The tree carries what the module declared and what it writes; the
     * definitions minted for its rows are beside it and are in no tree, so two states whose trees
     * compare equal can still say different things about the rows. They do, and without touching the
     * source: a row's operand is wrapped in the type the position it stands at contributes, and that
     * position comes from a behavior's signature — which an edit to an imported module can change
     * while this module's text stays as it was.
     *
     * <p>A state is a query's answer, and a store decides whether anything downstream has to be
     * asked again by comparing the answer it recomputed with the one it held. An answer that leaves
     * out what it answers with is one the store cannot tell has changed.
     *
     * <p>The correspondence between an operand and its method is left out on purpose: it is keyed on
     * operand identity, over the very nodes the tree hands out, so it says nothing a tree and the
     * definitions built from it do not already say.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof Prepared other && desugared.equals(other.desugared)
                && surface.equals(other.surface) && takenOn.equals(other.takenOn);
    }

    @Override
    public int hashCode() {
        return desugared.hashCode() * 31 + surface.hashCode();
    }
}
