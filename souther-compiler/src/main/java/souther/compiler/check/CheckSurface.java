package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The module a best-effort reading of a compilation runs over, assembled once.
 *
 * <p>It carries no claim that every declaration derived or that the module can be emitted. What it
 * is, is the one place the parts are put together: an imported name written as the definition it
 * denotes, a definition held to what it came out as, an example and a fake table written the same
 * way, and a definition minted for what each row writes at a position. Every reader that wants a
 * whole module to walk over gets it from here, so those are decided once and never counted out
 * again.
 *
 * <p>Its declarations are the ones {@link Normalized} answered for, handed in rather than worked out
 * here: failing to derive a representation does not put an earlier spelling back into the tree. A
 * module holding one declaration that could not be derived is still read for what its other
 * definitions say, and the declaration that could not be derived is here in the form every reader
 * below the settling expects — not beside them in a spelling one rung up, which nothing in a tree
 * would say was different.
 *
 * <p>Not a rung, and it says so ({@link Assembly}). What a state of this package says is that
 * something was established of a value; what this says is where the parts were joined.
 * {@link Prepared} is the state — it is this assembly beside the witness that every declaration came
 * out — and a reader that has to have a whole module asks for that one.
 */
public final class CheckSurface implements Assembly {

    /** The settling the parts were joined over, kept rather than its tree: what a pass reads off
     *  this is the tree, and what says which module it is an assembly of is the state. */
    private final InvariantSettled settling;
    private final List<Normalized.Def> declarations;
    private final List<Desugared.Fn> fns;
    private final List<Hir.Example> examples;
    /** What the module declares a stand-in for, and how many blocks declare each: read once, where
     *  the names were, and carried rather than worked out again from the blocks. */
    private final FakeTables fakes;
    private final List<Hir.FnDef> rowDefs;
    /**
     * The definitions this was joined from, as they were handed in.
     *
     * <p>Kept beside the ones above because the two are not the same values: what is on the surface
     * has had its imported names written out and its state proved again of what came out, and what
     * came in is the answer the desugaring gave. A caller pairing this assembly with the witness
     * that every definition came out compares what each was made from, and the assembly's is this.
     */
    private final List<Desugared.Fn> desugaredFrom;
    private final Map<Hir.Expr, String> operandMethods;
    /** Worked out once, as the rungs beside this work theirs out. */
    private volatile Hir.Module projected;

    private CheckSurface(InvariantSettled settling, List<Normalized.Def> declarations,
                         List<Desugared.Fn> fns, List<Desugared.Fn> desugaredFrom,
                         List<Hir.Example> examples, FakeTables fakes,
                         List<Hir.FnDef> rowDefs, Map<Hir.Expr, String> operandMethods) {
        this.settling = settling;
        this.declarations = List.copyOf(declarations);
        this.fns = List.copyOf(fns);
        this.desugaredFrom = List.copyOf(desugaredFrom);
        this.examples = List.copyOf(examples);
        this.fakes = fakes;
        this.rowDefs = List.copyOf(rowDefs);
        this.operandMethods = operandMethods;
    }

    /**
     * The parts of {@code settling} joined.
     *
     * <p>{@code normalized} is the declarations as the one producer of that form answered for them
     * ({@code Shapes.NormalizedDeclarations}), handed in rather than worked out again here. Worked
     * out here, this would be a second producer of the normalized form, and a declaration read off
     * this surface could differ from the same one read anywhere else. {@code desugared} is the same
     * of the definitions, answered for by {@code Shapes.DesugaredFns}.
     *
     * <p>{@code scope} is what a name means below the derivation, which is what a definition is held
     * to after it is rewritten; {@code signatures} is what each behavior takes and answers with,
     * which says where a row's values stand.
     *
     * <p>Which answer stands in for which part is checked and not taken from the key it arrived
     * under. Both tables are keyed by the name written here, and a name is a name in some module —
     * so an answer about another declaration or another definition of the same spelling would
     * otherwise be built in, and a reader would be handed something about one part under the name of
     * another.
     *
     * <p>Null where a part the module writes is not among what was handed in. Both producers answer
     * for everything a module writes, so this is a module handed parts that are not its own rather
     * than a module something failed on. What it would carry otherwise is a module short of
     * something it writes, which is read as one that does not write it — which is how a name a
     * module exposes came back as a name it must have imported.
     *
     * @throws IllegalArgumentException where an answer is for a part other than the one it stands in
     *     for
     */
    public static CheckSurface assemble(InvariantSettled settling,
                                        Map<String, Normalized.Def> normalized,
                                        Map<String, Desugared.Fn> desugared, Symbols scope,
                                        Map<ValueName.Behavior, Sig> signatures,
                                        FakeTables declared) {
        Hir.Module settled = settling.module();
        List<Normalized.Def> declarations = new ArrayList<>();
        for (InvariantSettled.Def def : settling.defs()) {
            Normalized.Def came = normalized.get(def.name());
            if (came == null) {
                return null;
            }
            // Which declaration each answer is for is checked and not taken from the key it arrived
            // under, for the reason Derived.Module#assemble states: a bare name is a name in some
            // module, so an answer for another module's declaration of the same name would
            // otherwise be built into this one.
            if (!came.declaredKey().equals(def.declaredKey())) {
                throw new IllegalArgumentException("the declaration normalized under `" + def.name()
                        + "` is " + came.declaredKey() + ", not " + def.declaredKey());
            }
            declarations.add(came);
        }
        // An imported definition is written here bare and denotes the module that declares it.
        // Spelling it out, once, settles the name this module reaches it by, which is what the table
        // a call expands against is keyed by and what the method a recursive helper becomes is
        // called.
        String self = settled.name();
        // In the order the module wrote them, and not the order they were worked out in: what a
        // report about two definitions wrong the same way names is the earlier of them.
        List<Desugared.Fn> fns = new ArrayList<>();
        List<Desugared.Fn> desugaredFrom = new ArrayList<>();
        for (Hir.FnDef wrote : settled.fns()) {
            Desugared.Fn came = desugared.get(wrote.name());
            if (came == null) {
                return null;
            }
            // And which definition each answer is for, for the reason Desugared.Module#assemble
            // states: a module carries definitions of several modules under names of one shape, so
            // the key tells them apart even less here than it does a declaration.
            if (!came.read().name().equals(wrote.name())
                    || !Objects.equals(came.read().declaredIn(), wrote.declaredIn())) {
                throw new IllegalArgumentException("the definition desugared under `" + wrote.name()
                        + "` is `" + came.read().name() + "` of " + came.read().declaredIn()
                        + ", not `" + wrote.name() + "` of " + wrote.declaredIn());
            }
            desugaredFrom.add(came);
            fns.add(Desugared.Fn.reestablish(
                    HelperNames.qualifyImportsIn(came.read(), self), scope));
        }
        List<Hir.Example> examples = new ArrayList<>();
        for (Hir.Example block : settled.examples()) {
            examples.add(HelperNames.qualifyImportsIn(block, self));
        }
        // The blocks with their imported names spelled out, classified as they already were: what
        // a block stands in for and how many blocks name it were settled where the names were
        // read, and writing a name out does not touch either.
        FakeTables fakes = FakeTables.namesWrittenOut(declared, self);
        CheckSurface written = new CheckSurface(settling, declarations, fns, desugaredFrom, examples, fakes,
                List.of(), Map.of());
        // What each row operand computes, emitted beside the module's own so a row runs its operand
        // in the program the behavior it is about is applied in. Which method is whose is kept with
        // the assembly: it is decided here and read wherever a row is run, never counted out again.
        RowFixtures.Emitted rows = RowFixtures.emitted(written, scope, signatures);
        return rows.defs().isEmpty() ? written
                : new CheckSurface(settling, declarations, fns, desugaredFrom, examples, fakes,
                        List.copyOf(rows.defs().values()), rows.methods());
    }

    /** What the module is called. */
    public String name() {
        return settling.name();
    }

    /** The settling the parts were joined over — the state and not its tree, so that what a caller
     *  compares this against is what a settling answered rather than half of it. */
    InvariantSettled settling() {
        return settling;
    }

    /** The declarations joined here, as the one producer of that form answered for them. */
    List<Normalized.Def> declarations() {
        return declarations;
    }

    /** And the definitions, as the desugaring answered for them — what this was joined from, which
     *  is what a caller pairing it with a witness compares. */
    List<Desugared.Fn> desugaredFrom() {
        return desugaredFrom;
    }

    /** The behaviors it declares, which no rung at or below this rewrites. */
    public List<Hir.BehaviorDef> behaviors() {
        return settling.module().behaviors();
    }

    /** The names it exposes. */
    public List<String> exposing() {
        return settling.module().exposing();
    }

    /** Whether {@code behavior}'s body is written here as a {@code let} of its own name, which a
     *  composition's is not. Read from the declarations, so it answers whether or not this module
     *  was elaborated. */
    public boolean writesItsOwnBody(Hir.BehaviorDef behavior) {
        return Requirements.writesItsOwnBody(module(), behavior);
    }

    /** Where {@code behavior}'s body comes from. How the behavior is written, which is a question
     *  about the source and not about what a compile made of it. */
    public BehaviorImplementation implementationOf(Hir.BehaviorDef behavior) {
        return Requirements.implementationOf(module(), behavior);
    }


    /** Its definitions, as they came out. */
    public List<Desugared.Fn> fns() {
        return fns;
    }

    /** Its example blocks, written with their imported names spelled out. */
    public List<Hir.Example> examples() {
        return examples;
    }

    /** And what it declares a stand-in for, the blocks written with their imported names spelled
     *  out. */
    public FakeTables fakes() {
        return fakes;
    }

    /** The definitions minted for this module's row operands, in the order they were emitted. */
    public List<Hir.FnDef> rowDefs() {
        return rowDefs;
    }

    /** Which method each row operand's value runs as, by the operand. */
    public Map<Hir.Expr, String> operandMethods() {
        return operandMethods;
    }

    /** Which module each imported name was written out to. */
    public Map<String, String> importedFrom() {
        Map<String, String> from = new LinkedHashMap<>();
        for (Hir.Import imp : settling.module().imports()) {
            for (String name : imp.names()) {
                from.put(name, imp.module());
            }
        }
        return from;
    }

    /**
     * The parts written back into the shape a pass over a whole module takes.
     *
     * <p>Built in one place and never read back into parts. This is where the module leaves the
     * assembly: what reads it is handed a tree carrying no proposition, which is why nothing that
     * takes one is a rung.
     */
    public Hir.Module module() {
        Hir.Module built = projected;
        if (built != null) {
            return built;
        }
        List<Hir.FnDef> definitions = new ArrayList<>();
        for (Desugared.Fn fn : fns) {
            definitions.add(fn.read());
        }
        List<Hir.Def> nodes = new ArrayList<>();
        for (Normalized.Def declared : declarations) {
            nodes.add(declared.node());
        }
        List<Hir.Fake> blocks = new ArrayList<>();
        for (FakeTables.Occurrence occurrence : fakes.written()) {
            blocks.add(occurrence.read());
        }
        projected = built = settling.module().withDefs(nodes).withFns(definitions)
                .withExamples(examples).withFakes(blocks);
        return built;
    }

    /** What it answers with. The tree covers the parts that were written back into it; what it was
     *  joined from is answered beside the tree and is compared beside it, since two surfaces made
     *  from different answers are two surfaces however alike the trees came out. */
    @Override
    public boolean equals(Object o) {
        return o instanceof CheckSurface other && module().equals(other.module())
                && rowDefs.equals(other.rowDefs) && desugaredFrom.equals(other.desugaredFrom);
    }

    @Override
    public int hashCode() {
        return module().hashCode();
    }
}
