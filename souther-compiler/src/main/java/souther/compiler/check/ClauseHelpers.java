package souther.compiler.check;

import souther.compiler.semantics.ConditionJoin;
import souther.compiler.ast.Hir;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.TypeKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The clauses a module declares — a data's {@code invariant} and a behavior's {@code ensures} — with
 * the helpers they name expanded.
 *
 * <p>A clause is expanded well before a body is. An importer reads an included data's invariant
 * through the symbol table, so it has to be expanded by the time the table is built, and the settling
 * of the helper parameter types it reaches has to happen first — expanding a call carries the
 * parameter's type onto the binding the call becomes, so a type settled afterwards would never reach
 * the expansion. The two are done together here, in that order, and each caller says which of the two
 * representations it wants.
 *
 * <p>There are two, and they are read by different things. The settled form is what travels to an
 * importer and what the backend emits; the discharge form leaves the language's own operations
 * standing, because the analysis has rules about them ({@link InliningPolicy}).
 *
 * <p>Both kinds of clause are here because the expansion is one rule. What a helper call comes to
 * does not depend on whether the clause it stands in is written of a value or of an answer, and the
 * two representations are the same two either way.
 */
public final class ClauseHelpers {

    private ClauseHelpers() {}

    /**
     * Settles the helper parameter types the author left unwritten, then inlines the helper calls in
     * every data's {@code invariant}.
     *
     * <p>The settling is done here as well as in {@link Lower}, and is idempotent: a parameter already
     * typed is left alone, and {@code Lower} settles what only the fully desugared module can
     * determine.
     *
     * <p>An invariant is pure and cannot call an injected behavior (spec §invariant-expressions), so
     * nothing here needs the injected signatures to settle the helpers an invariant reaches.
     *
     * <p>{@code published} is what the modules this one imports offer it. An invariant names what is
     * in scope where it is written, and an imported definition is in scope there as it is in a body; it
     * is substituted here for the reason a body's is, so what the invariant carries afterwards names
     * nothing of the module that declared it. The names are written qualified first, because that is
     * the spelling the table is keyed by — {@link HelperNames#qualifyImportsIn} does it again for the
     * bodies below, and says the same thing both times.
     */
    static Expansion<Hir.Module> withSettledInvariants(Hir.Module m, Symbols symbols,
                                                       Map<String, Hir.FnDef> published) {
        Hir.Module settled = settled(m, symbols);
        HelperInliner inliner = HelperInliner.forModule(settled, published, symbols.library());
        // What these expansions could not remove comes back with what they produced. A clause is the
        // one place a module writes an expression that is not a definition, so a recursion reached
        // from one is reached from nowhere a reader of the module's declarations would look.
        return new Expansion<>(withInlinedInvariants(inliner, settled), inliner.leftStanding());
    }

    /**
     * Every declaration {@code m} makes, with its clauses expanded to what a reading of them takes:
     * the definitions this module can name substituted, the language's own operations left standing
     * ({@link InliningPolicy#DISCHARGE}).
     *
     * <p>This is the same settling {@link #withSettledInvariants} does, stopped one step earlier, and
     * it reads the same table: what a clause names is substituted whether this module declared it or
     * imported it. Which is why it is done here and can be done nowhere else — a clause may name a
     * definition its module never exposed, and no importer has a name for one.
     *
     * <p><b>Total over what {@code m} declares.</b> Every declaration is here, one that wrote no
     * clause with an empty list of them. A map holding only the declarations that wrote something
     * cannot tell a declaration with nothing to say from one this failed to expand, and a reader
     * falling back for the first falls back for the second while saying nothing about it. The cost
     * that shape was avoiding — a module's answer moving when a declaration was written beside one
     * that could not change it — is paid at the module and not at the declaration, which is where
     * this is read from ({@code Shapes}).
     *
     * <p>The kinds with no {@code invariant} to write are here on their own footing:
     * {@link ExpandedClauses#nothingToExpand} says the HIR gives them none, rather than the
     * expansion having produced none.
     */
    public static Map<TypeKey, ExpandedClauses> expandedClausesOf(
            Expandable expandable, Symbols symbols, Map<String, Hir.FnDef> published) {
        Hir.Module m = expandable.module();
        Hir.Module settled = settled(m, symbols);
        HelperInliner inliner = HelperInliner.forHelpers(m.name(), HelperInliner.helpersOf(settled),
                published, InliningPolicy.DISCHARGE, symbols.library());
        Map<TypeKey, ExpandedClauses> out = new LinkedHashMap<>();
        for (Hir.Def def : settled.defs()) {
            TypeKey declares = def.declares().key();
            // Expanded first and the constructions written as constructions after, which is the
            // order the settled form is put together in: it inlines ({@link InvariantSettled}) and
            // normalises the result ({@link Normalized.Def}). Normalising first would leave a
            // construction that appears only inside a helper's body written as an application
            // here and as a construction there, and what tells the two representations apart is
            // what {@link InliningPolicy} says and nothing else.
            // What each clause's own expansion left standing, taken from the run that made it. The
            // inliner's own answer is about every tree it was driven over, and a clause told that a
            // call standing in a sibling stands in it would be read as one this cannot follow when
            // nothing in it is.
            List<Made> made = new ArrayList<>();
            Hir.Def expanded = withInlinedInvariants(inliner, def, made::add);
            out.put(declares,
                    NewtypeDesugar.rewriteInvariantsOf(expanded, symbols) instanceof Hir.Data d
                            ? new ExpandedClauses(declares, paired(d.invariants(), made))
                            : ExpandedClauses.nothingToExpand(declares));
        }
        return Map.copyOf(out);
    }

    /**
     * What a declaration with no {@code invariant} to write has: nothing, said as an answer.
     *
     * <p>Here because this is where expanded clauses are made. A caller outside this package cannot
     * build one, which is the point — and a kind the HIR gives no clauses still needs an answer, so
     * the one place that mints them offers it rather than leaving a second way in.
     */
    public static ExpandedClauses noClausesToExpand(TypeKey declaration) {
        return ExpandedClauses.nothingToExpand(declaration);
    }

    /**
     * Each clause with what its own expansion left standing, in the order they were written.
     *
     * <p>The two lists are the same walk's answers and are put together the moment both are in
     * hand, so that nothing below holds a clause beside a set that is not its own.
     */
    private static List<ExpandedClauses.Expanded> paired(List<Hir.InvariantClause> clauses,
                                                         List<Made> made) {
        if (clauses.size() != made.size()) {
            throw new IllegalStateException("this compiler expanded " + made.size()
                    + " clauses and wrote down " + clauses.size());
        }
        List<ExpandedClauses.Expanded> out = new ArrayList<>();
        for (int each = 0; each < clauses.size(); each++) {
            out.add(new ExpandedClauses.Expanded(clauses.get(each), made.get(each).standing(),
                    made.get(each).shape()));
        }
        return out;
    }

    /** {@code m} with its helper parameter types settled and the names in its invariants written
     * qualified — what both representations are expanded from, so neither reads a table the other
     * would key differently. */
    static Hir.Module settled(Hir.Module m, Symbols symbols) {
        return HelperNames.withQualifiedInvariants(HelperParams.settle(m, symbols, Map.of()));
    }

    /**
     * Inlines helper calls inside every data's {@code invariant}, so a rule named with a {@code let}
     * (e.g. {@code invariant 正の数(value)}) expands to its body before the invariant is type-checked
     * or emitted — the same lowering a behavior body gets (spec §blocks, §invariant-expressions).
     */
    private static Hir.Module withInlinedInvariants(HelperInliner inliner, Hir.Module m) {
        List<Hir.Def> defs = new ArrayList<>();
        for (Hir.Def def : m.defs()) {
            defs.add(withInlinedInvariants(inliner, def, _ -> { }));
        }
        List<Hir.BehaviorDef> behaviors = new ArrayList<>();
        for (Hir.BehaviorDef behavior : m.behaviors()) {
            behaviors.add(behavior instanceof Hir.SpecBehavior spec && !spec.ensures().isEmpty()
                    ? withInlinedEnsures(inliner, m.name(), spec) : behavior);
        }
        return m.withDefs(defs).withBehaviors(behaviors);
    }

    /**
     * {@code def} with the helper calls in its clauses expanded as {@code inliner} expands them.
     *
     * <p>The one step both representations take. Written once so that what either does before it
     * and after it is done at the same point of the same walk: a step one of them takes on the
     * clause as written and the other on the clause with the helpers in it would tell the two
     * apart by something other than {@link InliningPolicy}.
     */
    private static Hir.Def withInlinedInvariants(HelperInliner inliner, Hir.Def def,
                                                java.util.function.Consumer<Made> met) {
        if (!(def instanceof Hir.Data d) || d.invariants().isEmpty()) {
            return def;
        }
        BindingOwner declared = new BindingOwner.OfData(d.declares());
        return new Hir.Data(d.written(), d.declares(), d.newtype(), d.includes(), d.fields(),
                Hir.mapClauses(d.invariants(), clause -> {
                    // The shape its author wrote it in, and the parts expanded where they stand —
                    // so the clause is what those parts compose, and a reading of it holds each of
                    // them where the shape says to look.
                    AuthoredShape shape = shapeOf(clause);
                    Expansion<Hir.Expr> one = inliner.expanding(() ->
                            expandedOver(shape, part -> inliner.inline(part, declared)));
                    met.accept(new Made(CallsLeftStanding.of(one.standing()), shape));
                    return one.value();
                }),
                d.pos());
    }

    /**
     * What expanding one clause produced beside its tree: what the expansion left standing, and the
     * parts its author wrote as the expansion made them.
     *
     * <p>What is left standing is the clause's and is the same answer for every part of it. A part
     * of its own would be a finer answer than the reading that takes it asks for, and giving it one
     * now would change what a clause that stopped stops on.
     */
    private record Made(CallsLeftStanding standing, AuthoredShape shape) {}

    /** {@code spec} with the helper calls in its {@code ensures} expanded as {@code inliner} expands
     * them — which representation that leaves is the inliner's to say, and the same walk gives
     * either. */
    private static Hir.SpecBehavior withInlinedEnsures(HelperInliner inliner, String module,
                                                       Hir.SpecBehavior spec) {
        BindingOwner owner = new BindingOwner.OfSignature(
                new souther.compiler.types.ValueName.Behavior(module, spec.name()));
        List<Hir.EnsuresClause> clauses = new ArrayList<>();
        for (Hir.EnsuresClause clause : spec.ensures()) {
            List<Hir.EnsuresArm> arms = new ArrayList<>();
            for (Hir.EnsuresArm arm : clause.arms()) {
                arms.add(arm.with(inliner.inline(arm.expr(), owner)));
            }
            clauses.add(new Hir.EnsuresClause(clause.name(), List.copyOf(arms),
                    clause.pos(), clause.region()));
        }
        return new Hir.SpecBehavior(spec.written(), spec.params(), spec.ret(),
                spec.constructs(), spec.dependsOn(), List.copyOf(clauses), spec.pos());
    }

    /**
     * Where a written clause begins: the earliest position anything in it carries.
     *
     * <p>A node's own position is where its operator is written, so the position of {@code a && b} is
     * the {@code &&}. A reader is pointed at the clause, which starts at whatever of it comes first.
     *
     * <p>Of what was written. Asked of an expanded tree it answers with a position inside whatever
     * was expanded into it, which is why the reader that wants both this and the expansion gets them
     * made together ({@link ClausesForDischarge}).
     */
    static SourcePos beginsAt(Hir.Expr e) {
        SourcePos[] found = {e.pos()};
        Hir.forEachChild(e, child -> {
            SourcePos inner = beginsAt(child);
            if (inner != null && (found[0] == null || inner.isBefore(found[0]))) {
                found[0] = inner;
            }
        });
        return found[0];
    }

    /**
     * One part of a clause as its author wrote it, with the place it holds among that clause's
     * parts.
     *
     * <p>What a reader of one needs is the tree and the identity, and the identity is the rule it is
     * a part of together with this place ({@link #idFor}). The place is assigned where the clause is
     * split and nowhere else, which is why nobody outside {@link ClauseHelpers} can make one of
     * these: a second walk that numbered the parts for itself would agree with this one until either
     * changed its mind about what a part is.
     *
     * <p>The number is not offered on its own. Read off here, it would be a number a caller could
     * put beside whichever rule it happened to be holding, which is the pair this exists to keep
     * from being assembled by hand.
     */
    public static final class AuthoredPart {

        private final int ordinal;
        private final Hir.Expr written;

        private AuthoredPart(int ordinal, Hir.Expr written) {
            this.ordinal = ordinal;
            this.written = written;
        }

        /** The part itself, as the author wrote it and before anything is expanded into it. */
        public Hir.Expr written() {
            return written;
        }

        /**
         * What this part is called as a part of {@code rule}.
         *
         * <p>The kind of clause comes back with the name, so a reader that holds the parts of one
         * kind is holding them and not whatever the caller happened to pass. One split serves both
         * kinds: what an author joins out of conjuncts is joined the same way in a {@code data}'s
         * invariant and in a behavior's {@code ensures}, and a second split for the second kind is
         * a second answer to which parts a clause has.
         */
        public <R extends RuleRef.Named> PartId<R> idFor(R rule) {
            return new PartId<>(rule, ordinal);
        }

        /**
         * Two of these are one where they are the same text in the same place among the parts.
         *
         * <p>Written out because this is not a record, and it is not a record so that only the split
         * that numbers the parts can make one. What it travels inside is an answer a query keeps
         * ({@link ExpandedClauses}), and an answer is a value: compared by identity, a declaration
         * whose clauses are what they were would come back unequal every time it was worked out, and
         * everything that reads them would be done again.
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof AuthoredPart each
                    && ordinal == each.ordinal && written.equals(each.written);
        }

        @Override
        public int hashCode() {
            return ordinal * 31 + written.hashCode();
        }

        @Override
        public String toString() {
            return "part " + ordinal;
        }
    }

    /**
     * The conjuncts of a clause, in the order they are written — what a reader sees as separate
     * clauses, each with the place it holds among them.
     *
     * <p>For the expansions that split a clause before expanding it, and for nobody else. A reader
     * that wants the parts of a clause asks the clause ({@code TypeOps.Declared.parts}): splitting
     * a tree is how the parts are made, and a tree an expansion has already been over holds
     * conjunctions the author did not write, so a second split of one is a second answer to which
     * parts there are.
     */
    static List<AuthoredPart> conjunctsOf(Hir.Expr e) {
        List<AuthoredPart> out = new ArrayList<>();
        parts(shapeOf(e), out);
        return List.copyOf(out);
    }

    private static void parts(AuthoredShape shape, List<AuthoredPart> out) {
        switch (shape) {
            case AuthoredShape.One it -> out.add(it.part());
            case AuthoredShape.Both it -> {
                parts(it.left(), out);
                parts(it.right(), out);
            }
        }
    }

    /**
     * The shape {@code e} was written in, with each part numbered where it stands among them.
     *
     * <p>The one place a clause is read for the several rules an author wrote it as. Everything
     * anybody asks of that reading is an answer over this shape — which parts there are, the clause
     * with each of them expanded where it stands, and which subtree of a reading each of them
     * became — so there is one answer to what a clause is made of and the rest are walks over it.
     */
    private static AuthoredShape shapeOf(Hir.Expr e) {
        return shaped(e, new int[1]);
    }

    private static AuthoredShape shaped(Hir.Expr e, int[] numbered) {
        if (e instanceof Hir.Binary b
                && ConditionJoin.of(b.op()).orElse(null) == ConditionJoin.BOTH) {
            // Left before right, which is the order the clause is written in and the order the
            // parts are numbered in.
            AuthoredShape left = shaped(b.left(), numbered);
            return new AuthoredShape.Both(b, left, shaped(b.right(), numbered));
        }
        return new AuthoredShape.One(new AuthoredPart(numbered[0]++, e));
    }

    /**
     * Where the part numbered {@code ordinal} of {@code clause} is written, and nothing where the
     * clause has no such part.
     *
     * <p>Over the same split every part is numbered by, which is what makes this an answer about
     * the part a reader is holding rather than about whichever conjunct a second count landed on.
     * A reader asking here has a {@link PartId} and no tree, and the tree it would have to be given
     * is the one before anything was expanded into it — so the split is done here, where that tree
     * is what the declaration holds.
     *
     * @param clause the clause as its author wrote it, before any expansion
     */
    public static SourcePos placeOfPart(Hir.Expr clause, int ordinal) {
        List<AuthoredPart> parts = conjunctsOf(clause);
        return ordinal < parts.size() ? beginsAt(parts.get(ordinal).written()) : null;
    }

    /** {@code shape} with each of the parts its author wrote replaced by what {@code onPart} makes
     *  of it, written back into the nodes the author joined them with. */
    private static Hir.Expr expandedOver(AuthoredShape shape, UnaryOperator<Hir.Expr> onPart) {
        return switch (shape) {
            case AuthoredShape.One it -> onPart.apply(it.part().written());
            case AuthoredShape.Both it -> new Hir.Binary(it.written().op(),
                    expandedOver(it.left(), onPart), expandedOver(it.right(), onPart),
                    it.written().origin(), it.written().pos(), it.written().region());
        };
    }
}
