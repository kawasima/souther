package souther.compiler.check;

import souther.compiler.stdlib.Stdlib;
import souther.compiler.semantics.BuiltFrom;
import souther.compiler.semantics.Combinator;
import souther.compiler.ast.DefinitionName;
import souther.compiler.ast.Hir;
import souther.compiler.ast.StructuralCost;
import souther.compiler.ast.WrittenName;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.EtaOrigin;
import souther.compiler.types.ExpansionLineage;
import souther.compiler.types.ExpansionSite;
import souther.compiler.types.ParameterSlot;
import souther.compiler.types.ReferenceOrigin;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.ReachName;
import souther.compiler.types.ValueName;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.HelperMessage;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.DeclaringCode;
import souther.compiler.diag.QuotedFrom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Expands calls to helper {@code fn}s inline (spec §blocks: a named helper is the same as an inline block).
 *
 * <p>A helper fn is a {@code fn} with no matching behavior — it writes its own parameter types
 * (spec §fn-declaration) and, unlike a behavior fn, is not lowered to a class of its own. Instead every call
 * {@code h(a, b)} is rewritten to {@code let $k_p1 = a in let $k_p2 = b in <body>}, with the
 * helper's parameters α-renamed to fresh {@code $}-prefixed names so they cannot capture a caller
 * local (a source identifier never starts with {@code $}). Because the body is spliced into the
 * caller, the caller's construction-permission check, {@code depends on} inference, and codegen all
 * see the helper's constructions and injected calls directly — exactly as if the code had been
 * written inline (spec §blocks). Helpers must not recurse (directly or indirectly), which keeps the
 * expansion finite; a cycle is rejected up front.
 */
public final class HelperInliner {

    /**
     * Which declaration each name reaches, and the module they are reached in. A binding an expansion
     * makes belongs to a definition of that module, which is what tells it from the same helper
     * expanded elsewhere.
     *
     * <p>Not final: a recursive helper's own body is expanded against a table narrowed by its
     * parameters ({@link #inlineRecursiveBody}), and what that narrows is what a call reaches — never
     * what recurses, which {@link #recursiveInReach} settled over the table as it was built.
     */
    private HelperTable table;
    /**
     * The behaviors a body expanded here may name, with how many inputs each takes — the module's
     * own callable ones and the ones it borrows (spec {@code [#calling-a-behavior]}).
     *
     * <p>Apart from {@link #helpersOf} because a behavior is never expanded: what stands behind its
     * name may be a Java implementation, so a body that reached past it to the {@code let} would be
     * a second answer to the same name. All this holds is what reifying the name needs, which is its
     * arity; the query layer works out which behaviors are here, since which of them may be named is
     * a fact about the module rather than about any one body.
     */
    private Map<ValueName.Behavior, Integer> callableBehaviors = Map.of();
    /**
     * The values whose own answer this expansion was given, so that a reference to one need not
     * copy its body. Empty for every expansion that has no such answer — the tree the backend emits
     * from is one, and it copies every value as it always did.
     *
     * <p>Two answers rather than one, and they are read in {@link #settled}: the type a reference
     * stands under, and the constant a reference is written out as. A value has the first and may
     * have the second.
     */
    private Preserved settledValues = Preserved.NONE;
    private java.util.function.Function<ValueName, Object> settledConstants = _ -> null;
    /** What each declaration this table reaches calls, and which of them recurse. A function of the
     * table, so a narrowed table does not narrow it: what recurses was settled over the table as it
     * was built. */
    private final HelperGraph graph;
    /**
     * How much this pass has written into each body, which is what tells two of its writings apart.
     *
     * <p>Counted per body and not per pass. What a body is written into is not affected by what was
     * written into another — {@link BindingOwner.Expansion} says so of its ordinal — and one pass
     * expands many bodies: a helper checked on its own and then the behavior that calls it, every
     * definition a module publishes, every clause of a declaration's invariant. Counted across all of
     * them, a body's bindings moved when a body beside it gained an expansion or lost one to a
     * refusal, and a query answer that moves is a query answer everything below it is recomputed for.
     *
     * <p>Counted per body and not per writing, either. Two writings can share one body — one per
     * clause of an invariant, one per argument of a helper being checked — so a count that restarted
     * with each would give two of them the same binding.
     */
    private final Map<BindingOwner, Integer> written = new HashMap<>();
    /**
     * The values this expansion is inside, from the body being written down to here.
     *
     * <p>On the pass rather than in a {@link Writing}, because it is neither: a value's body is
     * substituted within the writing that names it, and the substitution of one value can carry the
     * expansion into another. It is a path through the tree being written, pushed and popped around
     * each substitution, and it is empty again whenever a body is finished with — including a body
     * that was refused partway through.
     */
    private final Set<String> substituting = new LinkedHashSet<>();

    /**
     * One writing of one body, and everything that is true only while it runs.
     *
     * <p>Four things were four fields, each saved and put back on its own, and one of them was left
     * standing after a refusal because the line that emptied it was on the path that did not run.
     * Held together they are made whole and dropped whole: what a writing holds is the writing's, and
     * a writing that did not finish takes it with it.
     *
     * @param destination the body being written into, which is where this pass's own names are
     *                    numbered. One body however deep the expansions inside it go: what a fresh
     *                    spelling has to be is unlike every other name in the body it is written
     *                    into, and how far in it stands is no part of that
     * @param enclosing which copy is being written, which is what the bindings an expansion
     *                  introduces belong under. A helper expanded into a body writes its copy under
     *                  the body; a call inside that helper writes its copy under the first copy, so
     *                  one helper expanded at two places holds two of everything inside it. The
     *                  destination is the same for both and cannot tell them apart, which is what
     *                  made this a second component rather than a reading of the first
     * @param binders the minter for this pass's own names — an expansion writes names no source
     *                wrote, so they belong to this writing rather than to the definition whose text
     *                it splices
     * @param dependencies which bindings the {@code depends on} parameters of a behavior's
     *                     {@code let} are; empty while writing anything else, because only a
     *                     behavior's {@code let} has them (spec §depends-on)
     * @param scopedLambdas the lambdas reached by a binding rather than by a name: one a block's
     *                      {@code let} binds, one handed to a function parameter. Apart from
     *                      {@link #table} because they are apart — a declaration is reached by a name
     *                      — and inside the writing because a lambda is in scope for as long as the
     *                      body holding it is being written and not one call longer
     * @param suppliedFrom which callables in scope came from outside the code being written, and
     *                     where they crossed out of it: the copy whoever wrote the callable handed
     *                     it to, and the parameter it filled. Set where a callable this writing did
     *                     not write is bound to a parameter and carried unchanged through every
     *                     copy that hands it on ({@link #crossedInto})
     */
    private record Writing(BindingOwner destination, BindingOwner enclosing,
                           ExpansionLineage lineage, Hir.Binders binders,
                           Set<BindingId> dependencies,
                           Map<BindingId, ScopedLambda> scopedLambdas,
                           Map<BindingId, ExpansionSite.Supplied.Handover> suppliedFrom) {

        /**
         * The same writing, one copy deeper.
         *
         * <p>Everything but which copy is being written stays: the body is the same body, the names
         * this pass mints are numbered among the same ones, what the behavior depends on is still
         * what it depends on, and a lambda in scope outside the call is in scope inside it. What
         * changes is what the bindings written from here belong under.
         *
         * <p>Not a writing of its own. One started here would number this pass's names from zero
         * again and would begin with no lambda in scope, so a function handed to the call would stop
         * being reachable from inside the body it was handed to.
         *
         * <p>{@code deeper} is that copy said the other way. What a binding belongs to is this
         * pass's answer and has this pass's counting in it; what a construct is a copy of is the
         * sites the splicing went through, and it is carried here rather than read back off the
         * owner — a reader that recovered it from there would be taking an identity out of a value
         * that says how the compiler ran.
         */
        Writing inside(BindingOwner copy, ExpansionLineage deeper,
                       Map<BindingId, ExpansionSite.Supplied.Handover> supplied) {
            // Where the callables in scope crossed in. A boundary in force outside this copy is
            // still in force inside it — the copy being written is code the caller's callable was
            // handed to, and where that callable came from is not changed by being handed on. What
            // this call adds is the callables that crossed in at it, which the caller works out.
            Map<BindingId, ExpansionSite.Supplied.Handover> here =
                    new LinkedHashMap<>(suppliedFrom);
            here.putAll(supplied);
            return new Writing(destination, copy, deeper, binders, dependencies, scopedLambdas,
                    here);
        }
    }

    /** The writing in force, or null outside one. Nothing public reads it without starting one. */
    private Writing writing;

    /**
     * {@code work} done with the bindings it writes belonging to {@code copy}.
     *
     * <p>Not a writing of its own ({@link Writing#inside}). What is being written into has not
     * changed and neither has what is in scope; what has changed is which copy the names written
     * from here belong to.
     */
    private Hir.Expr insideThisCopy(BindingOwner copy, ExpansionLineage deeper,
                                    Map<BindingId, ExpansionSite.Supplied.Handover> supplied,
                                    java.util.function.Supplier<Hir.Expr> work) {
        Writing outer = writing;
        writing = outer.inside(copy, deeper, supplied);
        try {
            return work.get();
        } finally {
            writing = outer;
        }
    }

    /**
     * A lambda reached by a binding, and where the author wrote it.
     *
     * <p>The two are one fact about one binding, so they are registered and dropped together. Held in
     * two tables they had to be put and removed twice, and a binding that made it into one of them
     * alone is a lambda a report cannot name or a name an expansion cannot reach.
     *
     * <p>{@code origin} is null where a {@code let} bound the lambda: the binding is then written
     * where the author wrote it, and a report about the lambda has nowhere else to point. It says
     * something only when a call site handed the lambda to a function parameter, because the lambda is
     * then registered under a synthetic name — a spelling that must never reach a diagnostic.
     */
    private record ScopedLambda(Hir.FnDef fn, LambdaOrigin origin) {

        ScopedLambda(Hir.FnDef fn) {
            this(fn, null);
        }
    }

    /** Where a lambda given to a function parameter was written: the parameter it fills, the helper
     * that declares that parameter, and the lambda's own position. Asked by the binding and not by
     * the spelling: two combinators nested one inside the other give their function parameters the
     * same name as often as not, and a report that found the outer one's lambda would point at
     * another author's line.
     *
     * <p>What a report needs, and nothing else. Where the block crossed into an operation is a
     * separate fact and is kept where identity is settled ({@code Writing#suppliedFrom}): held here
     * as well it was a second answer to one question, and the two came apart the moment one
     * operation handed a block on to another. */
    private record LambdaOrigin(String param, String owner, SourcePos pos) {}

    private HelperInliner(HelperTable table, HelperGraph graph) {
        this.table = table;
        this.graph = graph;
    }

    /** The body of {@code fn} in this module — what an expansion written into it belongs to. */
    public BindingOwner bodyOf(String fn) {
        return new BindingOwner.OfValue(table.module(), fn);
    }

    /** A helper is a fn whose name is not a behavior's; behavior fns are lowered on their own. The
     * prelude helpers join the inlining map under the qualified names they are reached by
     * ({@code Bool.not}), a module's own under the bare names it declared them with — so the two
     * never stand for one key, and how a call came to name one of them was settled before this. */
    public static HelperInliner forModule(Hir.Module module, Stdlib stdlib) {
        return forModule(module, Map.of(), stdlib);
    }

    /**
     * The same, with the definitions other modules publish to this one — each under the qualified name
     * it is reached by here, and each already closed by the module that declares it.
     *
     * <p>They join the inlining map but not {@code own}: a definition another module published is
     * one this module expands and not one it declared. Which of them this module ends up emitting is
     * no part of this — it follows from what expanding this module's trees leaves standing, and is
     * answered where that is collected.
     */
    public static HelperInliner forModule(Hir.Module module, Map<String, Hir.FnDef> imported,
                                          Stdlib stdlib) {
        HelperTable table = HelperTable.of(module, imported, InliningPolicy.FULL, stdlib);
        return new HelperInliner(table, HelperGraph.of(table));
    }

    /**
     * The inlining an expansion needs, over the helpers alone.
     *
     * <p>Which helper a call expands to, and which calls are left standing because the helper
     * recurses, follow from the helpers and nothing else — so a body is expanded without reading the
     * bodies beside it. What a module emits is not read here and is not read anywhere from a table:
     * it is what its expansions left standing, taken from {@link #leftStanding} by whoever drove
     * them.
     */
    public static HelperInliner forHelpers(String module, Map<String, Hir.FnDef> own,
                                           Stdlib stdlib) {
        return forHelpers(module, own, InliningPolicy.FULL, stdlib);
    }

    /**
     * The same, resolving only what {@code policy} says to resolve.
     *
     * <p>{@link InliningPolicy#DISCHARGE} leaves the standard library out of the table, so a call to
     * one of its operations is not a helper call here and survives as written. Nothing else changes:
     * a module's own helper is expanded, and a recursive call is left standing, by the same rules.
     */
    public static HelperInliner forHelpers(String module, Map<String, Hir.FnDef> own,
                                           InliningPolicy policy, Stdlib stdlib) {
        return forHelpers(module, own, Map.of(), policy, stdlib);
    }

    /**
     * The same, with the definitions other modules publish to this one joining the table.
     *
     * <p>They are in the table and not in {@code own}, as they are for {@link #forModule}: an imported
     * definition is one this module expands and not one it declares.
     */
    public static HelperInliner forHelpers(String module, Map<String, Hir.FnDef> declared,
                                           Map<String, Hir.FnDef> imported, InliningPolicy policy,
                                           Stdlib stdlib) {
        HelperTable table = HelperTable.of(module, declared, Map.of(), imported, policy, stdlib);
        return over(table, HelperGraph.of(table));
    }

    /**
     * The inlining over a table and the graph of that table, both worked out elsewhere.
     *
     * <p>What a call reaches and what recurses are facts about a module's declarations, so a compile
     * asks them once and every body of that module is expanded against the same two answers. The
     * factories above are for a caller holding declarations rather than answers.
     */
    public static HelperInliner over(HelperTable table, HelperGraph graph) {
        return new HelperInliner(table, graph);
    }

    /**
     * The same, told which behaviors a body expanded here may name and how many inputs each takes.
     *
     * <p>Told rather than worked out: which behaviors those are follows from the declarations of
     * this module and of the ones it imports, which is not what this pass reads. A body expanded
     * without being told names none of them, and a name it cannot reify is left as it was written
     * for the check to report.
     */
    public HelperInliner namingBehaviors(Map<ValueName.Behavior, Integer> arities) {
        this.callableBehaviors = Map.copyOf(arities);
        return this;
    }

    /**
     * The same, told what the values named in {@code settled} were settled as, so that a reference
     * to one of them is not copied.
     *
     * <p>For the checks that read a definition on its own. A definition is checked against what the
     * definitions it names were settled as, which is what a check of each of them already worked
     * out; copying the body instead re-derives that answer once per name that reaches it, so a
     * chain of values costs the chain again per link. What the backend emits is the other question
     * and unchanged: a value is substituted where it is named (ADR-0072), and the tree it is
     * substituted into is not built here.
     */
    public HelperInliner readingSettledValues(Preserved settled,
                                             java.util.function.Function<ValueName, Object> constants) {
        this.settledValues = settled;
        this.settledConstants = constants;
        return this;
    }

    /** The helpers a module declares: the fns its source wrote that implement no behavior, keyed by
     * the name it declared each under. Not what it took on to emit — that is {@link #takenOnBy}, and
     * the two are separate components of the module so that this answer is the same at every stage. */
    public static Map<String, Hir.FnDef> helpersOf(Hir.Module module) {
        return keyed(module, module.fns());
    }

    /** The helpers a module emits without having declared them, keyed by the name it reaches each by.
     * Empty until the pass that works out what the module reaches has run. */
    public static Map<String, Hir.FnDef> takenOnBy(Hir.Module module) {
        return keyed(module, module.takenOn());
    }

    private static Map<String, Hir.FnDef> keyed(Hir.Module module, List<Hir.FnDef> fns) {
        Set<String> behaviorNames = new HashSet<>();
        for (Hir.BehaviorDef b : module.behaviors()) {
            behaviorNames.add(b.name());
        }
        Map<String, Hir.FnDef> out = new LinkedHashMap<>();
        for (Hir.FnDef fn : fns) {
            if (isHelperName(behaviorNames, fn.name())) {
                out.put(fn.name(), fn);
            }
        }
        return out;
    }

    /**
     * Whether a fn of this module is one of its helpers — which is every fn it declares that is not
     * the body of a behavior.
     *
     * <p>Asked of both representations. {@code Resolve} needs the helper names of a module it has not
     * resolved yet, to answer the names its bodies write; every reader after it asks {@link
     * #helpersOf}. The rule is the same one, so it is here rather than restated over the syntax tree.
     */
    public static boolean isHelperName(Set<String> behaviorNames, String fn) {
        return !behaviorNames.contains(fn);
    }

    /**
     * Whether a module hands a definition of this name to a reader that asked for it.
     *
     * <p>Written over the name and what kind of body it has, because a module's own definitions are
     * read at both representations: a scope asks what an import brings into the value namespace, of
     * a module nothing has resolved, and everything after resolution asks the same question of one
     * it has. The rule is the same one, so it is here rather than restated over each tree.
     */
    public static boolean publishes(Set<String> exposing, String fn, boolean hasWrittenBody,
                                    List<String> wanted) {
        return hasWrittenBody && exposing.contains(fn) && wanted.contains(fn);
    }

    /**
     * Every recursive helper this expansion left a call to standing, by the name it was reached by.
     *
     * <p>What the expansion did. A call is left standing because expanding it would not terminate,
     * and the method it stays a call to has to be somewhere — so the requirement is made at the
     * moment the decision is, by whoever made it.
     *
     * <p>In the order they were met, because a reader reporting one of a group reports the one it
     * reached first.
     */
    private final java.util.SequencedSet<ReachName.Declaration> leftStanding = new java.util.LinkedHashSet<>();

    /**
     * What each expansion being asked about has left standing, innermost last.
     *
     * <p>Beside {@link #leftStanding} and not read off it. That set answers for every tree this
     * inliner was driven over, which is what a module has to emit; a reading holding one tree needs
     * the calls standing in that tree, and the two are the same set only where an inliner expanded
     * one thing. Written here as the decision is made, for the same reason the other is.
     *
     * <p>A list because one asked expansion can run inside another — a declaration's clauses are
     * expanded one at a time inside the expansion of the declaration — and a call standing in the
     * inner tree stands in the outer one, which holds it.
     */
    private final java.util.List<java.util.SequencedSet<ReachName.Declaration>> standingHere =
            new java.util.ArrayList<>();

    /** Bindings holding the same elements as another binding, and bindings holding elements made
     *  from another's. Written where an expansion removes the operation that says so. */
    private final ElementProvenance.Builder provenance = new ElementProvenance.Builder();

    /**
     * The lambdas given to an operation that answers one of their results per element, and the
     * binding of the container each walks.
     *
     * <p>Written where the operation is still there to be asked and read where the lambda is
     * applied, which is the one binding the two ends have in common: the lambda is registered under
     * it, and every application of it expands from there. Its own parameter — the name an element
     * arrives under — does not exist yet at the first point and is what the second records.
     *
     * <p>Inside one expansion of one body, as everything here is. Nothing survives past
     * {@link #provenance}, which is what carries the fact onwards.
     */
    private final Map<BindingId, BindingId> pointwise = new LinkedHashMap<>();
    /** Which rule each expansion was handed, by the parameter it was handed to. Read here for the
     *  same reason the element bindings are: this is where the call site still stands. */
    private final souther.compiler.coverage.SuppliedRules.Builder supplied =
            new souther.compiler.coverage.SuppliedRules.Builder();
    /** Which rule each binding that holds one stands for. Kept by binding rather than by the writing
     *  it was made in: a binding tells itself from every other, and what a name stands for is the
     *  same question wherever the name is read. */
    private final Map<BindingId, souther.compiler.coverage.SuppliedRules.RuleIdentity> rules = new LinkedHashMap<>();
    /** Which declaration each binding that holds a callable stands for. A name is where a callable
     *  was put, and the copies a call through it makes are copies of what it holds. Read by the same
     *  thing that reads it where the declarations are walked, so the two cannot come to disagree
     *  about what a name means. */
    private souther.compiler.coverage.NamedCallables callables =
            souther.compiler.coverage.NamedCallables.NONE;

    /** Every recursion in reach, which is exactly what {@link #inline} leaves a call standing to —
     *  this module's own, what its imports publish to it, and the library underneath both. What a
     *  standing call can be typed against, whatever this module turns out to reach. */
    public java.util.SequencedSet<ReachName.Declaration> recursiveInReach() {
        return graph.recursive();
    }

    /** The recursive helpers this module declares. A call to one of them is left standing by
     * {@link #inline}, as is a call to any recursion in reach — the graph's own {@code recursive}
     * set is what {@code inline} asks, so one this module does not declare is left standing too and
     * is answered for by whoever collects what an expansion could not remove.
     *
     * <p>Answered in declaration order, which is the order a check reporting one of them reports in.
     * The order is the graph's and is carried, not rebuilt. */
    public java.util.SequencedSet<ReachName.Declaration> recursiveHelpers() {
        java.util.SequencedSet<ReachName.Declaration> result = new java.util.LinkedHashSet<>();
        for (ReachName.Declaration reference : graph.recursive()) {
            // Held here, which is asked at the address this module puts what it reaches that way —
            // the entry says both, so neither is worked out from the other.
            if (table.held().containsKey(DefinitionName.of(reference))) {
                result.add(reference);
            }
        }
        return result;
    }

    /**
     * What this expansion left standing: every recursive helper a call of it survived to, in the
     * order they were met.
     *
     * <p>Read off the expansion that made them. An inliner is made fresh for the tree it expands, so
     * this answers for that tree and for nothing beside it — the caller that drove the expansion is
     * the one that knows which tree that was, and it is the one that carries the answer onwards.
     *
     * <p>What a call reaches from here is not in it. A helper left standing has a body of its own
     * that may reach further recursions, and following that is the call graph's to answer
     * ({@link HelperGraph#reachedFrom}) rather than something to re-walk here.
     */
    public java.util.SequencedSet<ReachName.Declaration> leftStanding() {
        return java.util.Collections.unmodifiableSequencedSet(leftStanding);
    }

    /**
     * Where the elements of what this expansion's bindings hold came from.
     *
     * <p>Taken from the same place {@link #leftStanding} is: the inliner was made for this body, so
     * what it wrote down is this body's. An operation over a collection is expanded into a walk, and
     * afterwards nothing in the tree says its answer held the elements of anything — so the relation
     * is written while the operation is still there and read by whoever needs it later.
     */
    public ElementProvenance provenance() {
        return provenance.built();
    }

    /** Which rule each expansion of this body was handed, taken from the same place. */
    public souther.compiler.coverage.SuppliedRules suppliedRules() {
        return supplied.built();
    }

    /**
     * Records where the elements of what {@code binding} holds came from, if from anywhere.
     *
     * <p>Read off the expansion the argument is. What operation it was is what the expansion carries
     * ({@link Hir.Expansion#callee}), and which of its arguments held the container is what the
     * library's signature says ({@link Combinators}). Which binding that argument became is counted
     * rather than named: an expansion writes a binding per value argument in the parameters' order,
     * and a combinator's one function argument leaves none — so the container's place among the
     * bindings is its parameter's, one earlier where the function came before it.
     *
     * <p>Two relations and not one. Where the operation answers the elements it was given, what the
     * two bindings hold are the same values and a rule about one is a rule about the other; where it
     * answers what a closure made of them, the values came from there and are not those values, and
     * only the first may be walked through.
     */
    private void elementsCameFrom(Hir.Binder binding, Hir.Expr argument) {
        if (!(argument instanceof Hir.Expansion expansion)) {
            return;
        }
        BuiltFrom<DeclaredArgument> built =
                DefaultBoundOperationFacts.get().buildsItsResultFrom(expansion.callee());
        if (built == null) {
            return;
        }
        DeclaredArgument holds = built.holdsTheElementsOf();
        DeclaredArgument made = holds != null ? null : built.derivesItsElementsFrom();
        BindingId container = boundFor(expansion, holds != null ? holds : made);
        if (container == null) {
            return;
        }
        if (holds != null) {
            provenance.holdsTheSameAs(binding.id(), container);
        } else {
            provenance.derivesFrom(binding.id(), container);
        }
    }

    /**
     * The binding {@code expansion} wrote for the argument {@code which} names, or null where it
     * wrote none.
     *
     * <p>Counted rather than named. An expansion writes a binding per value argument in the
     * parameters' order and a function argument leaves none, so the argument's place among the
     * bindings is its parameter's, one earlier where a function came before it. An operation taking
     * no function has none to skip, which is why the closure is asked for separately rather than
     * assumed.
     */
    private static BindingId boundFor(Hir.Expansion expansion, DeclaredArgument which) {
        if (which == null) {
            return null;
        }
        int parameter = CallArguments.positionOf(which, expansion.callee());
        Combinator handed = Combinators.of(expansion.callee());
        int at = parameter - (handed != null && handed.closureArg() < parameter ? 1 : 0);
        return at < 0 || at >= expansion.bound().size() ? null
                : expansion.bound().get(at).binder().id();
    }

    /**
     * The module's helper fns, keyed by the name it reaches each of them by — what it declared, and
     * what it took on to emit as a method of its own.
     *
     * <p>Both, because both are emitted here and both are checked here. Which module declared one is
     * not read off this map or off the key: the declaration says it
     * ({@link Hir.FnDef#declaredBy}), and a check whose rule is about the declaring module — what
     * may be walked, what must be proven total — asks it there.
     */
    public Map<DefinitionName, HelperEntry> held() {
        return table.held();
    }

    /** Every declaration this body can reach, this module's own and the library's, by the reference
     *  it is reached by. What a reader asking about a fork wants: the fork may have been written in
     *  either. */
    public Map<ReachName.Declaration, HelperEntry> reachable() {
        return table.reachable();
    }

    /**
     * A definition {@code module} publishes, closed so that it means in a reader what it means here.
     *
     * <p>Closing is expansion: the module's own values and non-recursive helpers are substituted into
     * the body, so no bare name of this module is left for the reader to read against its own
     * definitions (ADR-0067). A recursive helper is the one thing expansion cannot remove — it is
     * lowered to a method, so the call stays a call — and it is qualified here instead, under the
     * module that declares it. The reader emits that method as one of its own, exactly as it already
     * does for a recursive prelude helper it reaches.
     *
     * <p>What comes back is named qualified too. The name is the definition's identity across
     * modules, and a bare one is only how a reader happens to write it: two modules may publish a
     * {@code tally}, and a reader may reach one of them without importing it at all — through the
     * body of something else it imported.
     */
    public Hir.FnDef closeAcross(Hir.FnDef fn, String module) {
        if (!module.equals(table.module())) {
            // The two below would be about different modules. The body is closed against this
            // table, so what recurses is this module's answer; the reference is what a reader
            // reaches the declaration by, and a declaring module that is not this one would be a
            // body closed against one module's declarations and handed over as another's.
            throw new IllegalArgumentException("`" + module + "` is not the module this expands"
                    + " into, which is `" + table.module() + "`");
        }
        // Its own module is reading here, so it reaches its own declaration bare — which is the
        // reference the graph over that module's table is keyed by.
        ReachName.Declaration here = new ReachName.Own(new ValueName.Helper(module, fn.name()));
        Hir.Expr closed = graph.recurses(here)
                ? inlineRecursiveBody(fn) : inline(fn.writtenBody(), bodyOf(fn.name()));
        return fn.reachedAs(new ReachName.OfModule(new ValueName.Helper(module, fn.name())))
                .withBody(new Hir.FnBody.Written(
                        HelperNames.publishedBy(HelperNames.qualifyHelpersOf(closed, module), module)));
    }

    /** The module these helpers belong to — the one whose bodies this expands into. */
    public String moduleName() {
        return table.module();
    }

    /** The declaration reached by {@code name} across the prelude and the module's own helpers, or
     * null where the name reaches none. For a reader walking a set of names this pass answered with —
     * the recursive helpers, say. A reader holding a call asks {@link #applied} instead, because what
     * a call applies is not decided by how it is spelled. */
    public Hir.FnDef helper(ReachName.Declaration reference) {
        return table.reached(reference);
    }

    /** Where this module holds what {@code reference} reaches, or null where it reaches nothing
     *  here. The table's answer: a caller that paired the two itself would be keeping a second
     *  statement of what an entry says. */
    public souther.compiler.ast.DefinitionName heldAt(ReachName.Declaration reference) {
        return table.heldAt(reference);
    }

    /** The body {@code call} applies, or null where it applies something no body stands behind. */
    public Hir.FnDef applied(Hir.Apply call) {
        return appliedHelper(call);
    }

    /**
     * Which argument of the walk holds its block.
     *
     * <p>The walk is the one privileged loop primitive that takes a block (spec §stdlib-list); its
     * block is the first argument and has two parameters (`(acc, x)`, spec §pipe). A bare name
     * passed in its place is sugar for a block that wraps a call. The other combinators
     * (map/filter/all/any) are ordinary prelude helpers derived from the walk (ADR-0028), so they
     * need no such desugaring — a name reaches their function parameter directly.
     *
     * <p>Which argument, and not which operation: what the walk is, the library says
     * ({@link Stdlib#theWalk}).
     */
    private static final int BLOCK_ARG_OF_THE_WALK = 0;

    /**
     * The rewrite {@code call} takes, or null where it takes none.
     *
     * <p>The one place that decides it, because more than one reader needs the same answer and they
     * are not allowed to differ: the pass that writes the rewrite out, the walk that reads the call
     * graph, and the check that holds an argument to the parameter it lands on. A sugar is the call
     * it becomes with some arguments already supplied, so one written with a different number of
     * them is not that call at all — and a reader that took the rewrite anyway would credit an edge
     * to a declaration this call never reaches.
     *
     * <p>Which names are sugar is the library's ({@link Stdlib#rewriteOf}) and is asked there.
     * Written out here instead, the answer stopped agreeing with the library the day a second sugar
     * was added, and the disagreement is an edge quietly missing from the call graph.
     */
    private static Stdlib.Rewrite rewriteTaken(Stdlib stdlib, Hir.Apply call) {
        if (call.answered() == null) {
            return null;   // it reaches no library name, so there is no sugar to write out
        }
        // Whether a name is sugar is the library's answer about one of its own operations, so it is
        // asked with the operation rather than with the reference rendered. Anything else reaches
        // no library name and has no sugar to write out.
        if (!(call.answered().denotes() instanceof ValueName.Stdlib.Operation operation)) {
            return null;
        }
        Stdlib.Rewrite rewrite = stdlib.rewriteOf(operation);
        return rewrite != null && call.args().size() == rewrite.keptArgs() ? rewrite : null;
    }

    /**
     * The helper {@code call} applies, by the name a table is keyed by — the callee's own, or what a
     * sugar it takes rewrites to. Null where what is applied is not a name that reaches a
     * declaration: a binding holding a lambda is applied by the expression and reaches nothing.
     */
    private static ReachName.Declaration calledHelper(Stdlib stdlib, Hir.Apply call) {
        if (!(call.function() instanceof Hir.Var.Denoting callee)) {
            return null;
        }
        Stdlib.Rewrite rewrite = rewriteTaken(stdlib, call);
        if (rewrite != null) {
            return new ReachName.OfLibrary(rewrite.target());
        }
        // An edge to a declaration, and to nothing else. Applying a binding, or the library's
        // namespace, reaches no declaration to draw one to — which the reference says, where this
        // read the denotation and had to name the kinds that are not one.
        return callee.reachedAs() instanceof ReachName.Declaration reached ? reached : null;
    }

    /** The call a sugared name becomes, written out: what it becomes and what it supplies are the
     * library's to say ({@link Stdlib#rewriteOf}), and this is where it is done. {@code List.fold(step,
     * seed, xs)} is {@code List.foldFrom(step, seed, xs, 0)} — the walk from the head. Rewriting here,
     * before inlining, means the step reaches {@code foldFrom} (the one recursive helper) directly
     * rather than through a wrapper that would pass the function on as a value. */
    private static Hir.Apply desugar(Stdlib stdlib, Hir.Apply call) {
        Stdlib.Rewrite rewrite = rewriteTaken(stdlib, call);
        if (rewrite == null) {
            return call;
        }
        // Where the supplied arguments go is the rewrite's, which is why the list is built there:
        // this writes each of them out as the literal no one wrote, and puts none of them anywhere.
        List<Hir.Expr> args = rewrite.arguments(call.args(),
                constant -> new Hir.IntLit(constant, call.pos(), null));
        // The library name this reaches for is the pass's; the application is the author's, and so
        // is what they applied there — a report about this call quotes the sugar they wrote and not
        // the operation it stands for, which is private to the library and takes another argument.
        //
        // The reference is the one the sugar was written as. There is one to take: a rewrite is
        // found only for a call whose callee is answered ({@link #rewriteTaken}), which is a name.
        Hir.Var.Denoting sugar = call.answered();
        return call.replacedBy(
                Hir.Var.respelled(rewrite.target().qualified(),
                        new ReachName.OfLibrary(rewrite.target()), sugar.origin(),
                        sugar.pos(), sugar.region()),
                args);
    }

    /** Inlines a recursive helper's own body, expanding the non-recursive helper calls it makes while
     * leaving its own parameters alone. A parameter that shares a module helper's name — {@code
     * foldFrom}'s function parameter {@code step} in a module that also defines a helper {@code step} —
     * is a parameter application, not a call to that helper, so the same-named helpers are hidden while
     * the body is expanded. */
    public Hir.Expr inlineRecursiveBody(Hir.FnDef h) {
        List<ReachName.Declaration> parameters = new ArrayList<>();
        for (Hir.FnParam p : h.params()) {
            // A parameter hides the module's own helper of that name, which is the one a bare name
            // here reaches. Another module's and the library's are reached under a qualifier that
            // no parameter can be written as, so there is nothing of theirs for one to hide.
            parameters.add(new ReachName.Own(new ValueName.Helper(table.module(), p.name())));
        }
        HelperTable outer = table;
        // Narrowed, not rebuilt: what a call reaches changes, what recurses does not. A graph taken
        // over the narrowed table would find this very helper non-recursive and expand its own call
        // forever.
        table = table.hiding(parameters);
        try {
            return inline(h.writtenBody(), bodyOf(h.name()));
        } finally {
            table = outer;
        }
    }

    /**
     * What this application decides for the variables {@code helper}'s signature left open — one
     * fresh variable per variable it wrote, over its parameters and its declared return together.
     * Empty where it wrote none, which is every call of a helper that names its types outright.
     *
     * <p>Every unsolved variable in a helper's declared types is that helper's own: a variable enters
     * a type only where the core writes one in a signature or where a helper's own settling mints one
     * for its parameters, and a reference to a declared type carries none. So renaming by name over
     * the whole signature at once binds nothing it should not.
     */
    private static Map<String, Type> instantiation(Hir.FnDef helper, BindingOwner mine) {
        Map<String, Type> applied = new LinkedHashMap<>();
        for (Hir.FnParam p : helper.params()) {
            collectVariables(p.type(), mine, applied);
        }
        collectVariables(helper.declaredReturn(), mine, applied);
        return applied;
    }

    private static void collectVariables(Hir.RetType declared, BindingOwner mine,
                                         Map<String, Type> applied) {
        if (declared == null || !mentionsRetTypeVar(declared)) {
            return;
        }
        Type.mentions(TypeOps.resolveParamType(declared), t -> {
            if (t instanceof Type.Var v) {
                applied.computeIfAbsent(v.name(), name -> new Type.MetaVar(mine, name));
            }
            return false;   // a collector, not a test: every position is visited
        });
    }

    /**
     * What a function argument is declared as where it comes from, or null where nothing this
     * expansion can see declares it.
     *
     * <p>A name standing for a function an enclosing call supplied is one this expansion is holding,
     * and what that call declared of it is written on it. Every other name — a helper's own
     * parameter, a binding holding a function — is declared where it is bound, and the scope the
     * boundary is read in is what answers for it.
     */
    private Hir.RetType arrivesAs(Hir.Expr arg) {
        if (!(arg instanceof Hir.Var.Denoting named)) {
            return null;
        }
        Hir.FnDef is = expands(named);
        if (is == null || is.declaredReturn() == null) {
            return null;
        }
        List<Hir.RetType> params = new ArrayList<>();
        for (Hir.FnParam p : is.params()) {
            if (p.type() == null) {
                return null;   // it does not say what it takes, so it says nothing whole
            }
            params.add(p.type());
        }
        return new Hir.RetType(
                List.of(new Hir.FnType(params, is.declaredReturn(), is.pos())), is.pos());
    }

    /** What a function parameter's declared type says, with what this application decided written
     * into it — or null where the parameter's type is not a lone function type. */
    private static Hir.FnType declaredFn(Hir.RetType declared, Map<String, Type> applied) {
        if (declared == null
                || !(TypeOps.substitute(TypeOps.resolveParamType(declared), applied)
                        instanceof Type.FnOf fn)) {
            return null;
        }
        List<Hir.RetType> params = new ArrayList<>();
        for (Type p : fn.params()) {
            params.add(stating(p, declared.pos()));
        }
        return new Hir.FnType(params, stating(fn.result(), declared.pos()), declared.pos());
    }

    /** {@code t} as a written type with no surface text: what it denotes is decided, and no source
     * stands for it. */
    private static Hir.RetType stating(Type t, SourcePos pos) {
        return new Hir.RetType(List.of(Hir.TypeRef.of(t, pos)), pos);
    }

    /** {@code declared} with what this application decided written into it, or as it stands where it
     * left nothing open. The type is written as a reference with no surface text: what it denotes is
     * decided, and no source stands for it. */
    private static Hir.RetType instantiated(Hir.RetType declared, Map<String, Type> applied) {
        if (declared == null || applied.isEmpty() || !mentionsRetTypeVar(declared)) {
            return declared;
        }
        Type at = TypeOps.substitute(TypeOps.resolveParamType(declared), applied);
        return new Hir.RetType(List.of(Hir.TypeRef.of(at, declared.pos())), declared.pos());
    }

    /** Whether a declared type has a type variable inside it. A generic declared return ({@code
     * Map.updateOrInsert}'s {@code Map<'k, 'a>}) says nothing concrete at a call site, so it is not carried —
     * the caller's own arguments are what fix those variables. */
    private static boolean mentionsRetTypeVar(Hir.RetType ret) {
        return ret != null && ret.cases().stream().anyMatch(HelperInliner::mentionsTypeVar);
    }

    /**
     * Asked of what the reference denotes, not of how it was spelled. A reference a
     * helper's own settling wrote carries its type and no surface text at all
     * ({@link Hir.TypeRef#of}), so reading the spelling answers no about every one of them.
     *
     * <p>The whole reference is in what it denotes: {@code List<'a>} resolves to a type that holds
     * the variable, so the argument and a tuple's elements are not walked again here. This runs
     * after resolution, and a reference that has not been read is refused by {@link
     * Hir.TypeRef#denotes()} rather than answered off its spelling.
     */
    static boolean mentionsTypeVar(Hir.TypeTerm term) {
        if (term instanceof Hir.FnType fn) {
            return fn.params().stream().anyMatch(HelperInliner::mentionsRetTypeVar)
                    || mentionsRetTypeVar(fn.result());
        }
        if (!(term instanceof Hir.TypeRef ref)) {
            return false;
        }
        return Type.mentions(ref.denotes(), t -> t instanceof Type.Var);
    }

    /**
     * The parameters a call's callee declares, as the caller wrote the name: a helper's own, or —
     * for the {@code List.fold} sugar — {@code foldFrom}'s without the index the sugar supplies.
     * Null when the name is not a helper (a builtin, an injected behavior, or unknown).
     */
    private List<Hir.FnParam> declaredParams(Hir.Apply call) {
        if (call.answered() == null) {
            return null;   // it reaches no declaration, so none of them declares anything
        }
        Stdlib.Rewrite rewrite = rewriteTaken(table.library(), call);
        if (rewrite != null) {
            Hir.FnDef target = table.reached(new ReachName.OfLibrary(rewrite.target()));
            return target == null ? null : target.params().subList(0, rewrite.keptArgs());
        }
        ReachName.Declaration reached = call.answered().reachesADeclaration();
        Hir.FnDef helper = reached == null ? null : table.reached(reached);
        return helper == null ? null : helper.params();
    }

    /**
     * Rejects a lambda written on a parameter that takes a value. The standard library takes its
     * function first and its collection last (spec §pipe), so the arguments given the other way round
     * are the common first mistake — and left alone the lambda travels on as an ordinary value, to be
     * reported deep in the expansion as a block that escaped, against a rule about first-class
     * functions the caller has not met yet. Reported here, at the call, the parameter it landed on and
     * the one that takes the function are both still in hand, so the order can be named.
     *
     * <p>Checked against the name as written, before {@code List.fold} desugars to {@code foldFrom}:
     * the report names the caller's own call, not what the sugar expands to. A block with no
     * parameters is a braced block, not a lambda, and is left to the checker.
     */
    private void checkFunctionArgumentPlacement(Hir.Apply call) {
        List<Hir.FnParam> params = declaredParams(call);
        if (params == null || params.size() != call.args().size()) {
            return;   // not a helper, or an arity mismatch reported with the call itself
        }
        int fnParam = -1;
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).type() != null && params.get(i).type().asFn() != null) {
                fnParam = i;
                break;
            }
        }
        for (int i = 0; i < params.size(); i++) {
            Hir.RetType declared = params.get(i).type();
            if (declared == null || declared.asFn() != null
                    || !(call.args().get(i) instanceof Hir.Block lambda) || lambda.params().isEmpty()) {
                continue;
            }
            String param = params.get(i).name();
            if (fnParam < 0) {
                throw CompileException.of(Diagnostic.at(lambda.pos())
                        .say(new HelperMessage.ThisArgumentTakesNoFunction(call.written(),
                                String.valueOf(i + 1), param))
                        .build());
            }
            String shape = params.stream().map(Hir.FnParam::name)
                    .collect(java.util.stream.Collectors.joining(", "));
            throw CompileException.of(Diagnostic.at(lambda.pos())
                    .say(new HelperMessage.TheFunctionGoesToAnotherArgument(call.written(),
                            String.valueOf(i + 1), param, String.valueOf(fnParam + 1),
                            params.get(fnParam).name()))
                    .hint(new HelperMessage.WriteTheCallThisWay(call.written(), shape))
                    .build());
        }
    }

    /**
     * The body a call applies, or null where it applies something no body stands behind.
     *
     * <p>What is applied follows from what the call denotes. A binding applies the lambda bound
     * there, if one is; a declared name applies the declaration it reaches. Neither is asked of the
     * other, so a parameter spelled like a helper is the parameter, and a lambda bound to a name a
     * module declares is the lambda — with nothing to tell the two apart by.
     */
    private Hir.FnDef appliedHelper(Hir.Apply call) {
        if (call.answered() == null) {
            // it names nothing, or what is applied is not a name at all: either way no body stands
            // behind it, and the expression works out what is applied
            return null;
        }
        return expands(call.answered());
    }

    /**
     * What a rule handed to the function parameter {@code parameter} is expanded under.
     *
     * <p>Written here and read by whoever asks which rule a call site supplied, so the name and the
     * question about it are one thing. Two spellings of it would agree until the day one of them
     * changed, and what would go wrong then is that copies of a fork deciding by two different
     * rules would quietly be counted as one.
     */
    public static String suppliedAs(String parameter) {
        return SUPPLIED + parameter;
    }

    /** The one spelling for a rule written at a call site. */
    private static final String SUPPLIED = "$";

    /**
     * Which rule {@code named} stands for, or null where nothing here says.
     *
     * <p>A name bound to a rule is not the rule: two names for one declaration are one rule, and one
     * name in two copies of a body is one rule as well, while the bindings behind them are as many
     * as there are copies. So a local is looked through to what it was given, and what is answered
     * is what the author wrote.
     */
    private souther.compiler.coverage.SuppliedRules.RuleIdentity ruleOf(Hir.Var.Denoting named) {
        if (!(named.denotes() instanceof ValueName.Local local)) {
            return named.denotes() == null ? null
                    : new souther.compiler.coverage.SuppliedRules.RuleIdentity.Named(
                            named.denotes());
        }
        return rules.get(local.id());
    }

    /**
     * Which declaration {@code named} reaches, following a name to what it was bound to.
     *
     * <p>Null where nothing here says. A parameter holding a function is one: which callable it is
     * was decided by whoever called this, and answering with the parameter's own spelling would be
     * a declaration of that name, of which there is none.
     */
    private ReachName.Declaration reaches(Hir.Var.Denoting named) {
        return callables.reached(named);
    }

    /** Says that {@code binding} holds whatever {@code value} is. */
    private void holds(BindingId binding, Hir.Expr value) {
        callables = callables.and(binding, value);
    }

    /** Says that {@code binding} holds {@code rule}, where anything says what it holds. */
    private void stands(BindingId binding, souther.compiler.coverage.SuppliedRules.RuleIdentity rule) {
        if (rule != null) {
            rules.put(binding, rule);
        }
    }

    /** The same, of a rule written out where it stands. */
    private static souther.compiler.coverage.SuppliedRules.RuleIdentity ruleOf(Hir.Block written) {
        return written.rule().isWritten()
                ? new souther.compiler.coverage.SuppliedRules.RuleIdentity.Written(written.rule())
                : null;
    }

    /**
     * The body {@code named} stands for here, or null where no body stands behind it.
     *
     * <p>The one place that answers it, so a name applied and a name handed over get the same answer.
     * The two halves of the answer arrive together, as one name: how it is written here — bare for a
     * definition of this module, qualified for the library and for what another module publishes —
     * is the namespace the table is keyed by, and which namespace to look in is decided by what it
     * denotes, never by the text. A name that names nothing has neither, so it is not asked at all.
     */
    private Hir.FnDef expands(Hir.Var.Denoting named) {
        ReachName.Declaration reachedBy = named.reachesADeclaration();
        return switch (named.denotes()) {
            // applying something that is not a name: what is applied is worked out by the expression,
            // and no declaration stands behind it
            case null -> null;
            // A binding holds a lambda only inside the writing that put it there, so asked outside
            // one — which is where a check reads a call without expanding anything — a binding stands
            // for nothing this can answer with. It was answered that way before by a table that
            // happened to be empty there; it is answered that way now because there is no writing to
            // ask.
            case ValueName.Local local -> {
                ScopedLambda lambda = writing == null ? null
                        : writing.scopedLambdas().get(local.id());
                yield lambda == null ? null : lambda.fn();
            }
            case ValueName.Helper _, ValueName.Stdlib _ ->
                    reachedBy == null ? null : table.reached(reachedBy);
            // a construction, an injected behavior, `None`, or a name that denotes nothing: each is
            // applied by something other than an expansion, and each is reported where it belongs
            case ValueName.OfType _, ValueName.Behavior _, ValueName.Builtin _ -> null;
        };
    }

    /**
     * The arguments of a call that stays a call, with a dependency handed over by name replaced by
     * the block that forwards to it: {@code depth(code, fetch)} becomes {@code depth(code, (c) ->
     * fetch(c))}, which spec §blocks says is the same thing.
     *
     * <p>Where the callee is expanded the two already are the same thing — the expansion substitutes
     * the argument's name into the parameter's applications, so the name only ever stands in a call.
     * A recursive helper is lowered to a method instead (spec §fn-declaration), which leaves the argument
     * standing as a value, and a {@code depends on} parameter is reached through the behavior it
     * names rather than bound to a slot. Forwarding here is what makes the two callees say the same
     * thing about the same argument.
     *
     * <p>What is forwarded is the parameter, not every name spelled like it: the argument is matched
     * against the binder its name was answered with. A binding in force wins over the declaration it
     * shadows (spec §fn-rules), so a local named after a dependency is a local, and wrapping it would
     * both call the wrong thing and report a value as an uncallable name.
     */
    private List<Hir.Expr> forwardDependencies(Hir.FnDef callee, List<Hir.Expr> args) {
        if (callee == null || writing.dependencies().isEmpty()) {
            return args;
        }
        List<Hir.Expr> out = new ArrayList<>(args);
        for (int i = 0; i < callee.params().size() && i < out.size(); i++) {
            Hir.RetType declared = callee.params().get(i).type();
            Hir.FnType want = declared == null ? null : declared.asFn();
            if (want == null || !(out.get(i) instanceof Hir.Var.Denoting v)
                    || !(v.denotes() instanceof ValueName.Local local)
                    || !writing.dependencies().contains(local.id())) {
                continue;
            }
            out.set(i, etaExpand(v, want.params().size(), _ -> "$" + next() + "_" + v.name()));
        }
        return out;
    }

    /** As {@link #inline(Hir.Expr, BindingOwner)}, for the body of a behavior {@code let} whose
     * {@code depends on} parameters are the trailing bindings named in {@code dependencies}. */
    public Hir.Expr inline(Hir.Expr e, Set<BindingId> dependencies, BindingOwner into) {
        heldToTheBound(e);
        return writing(into, dependencies, () -> inline(e));
    }

    /**
     * Rewrites every helper call in {@code e} to its inlined body, into {@code into}.
     *
     * <p>{@code into} is the body being written: the bindings an expansion introduces belong to it,
     * so two copies of one helper's body spliced into two definitions do not answer as one binding.
     */
    public Hir.Expr inline(Hir.Expr e, BindingOwner into) {
        heldToTheBound(e);
        return writing(into, Set.of(), () -> inline(e));
    }

    /**
     * The same, answering with what this one expansion left standing as well as with what it
     * produced.
     *
     * <p>For a reader that has to tell a call the expansion meant to leave from one it was supposed
     * to remove. Both are a helper applied and the finished tree does not say which, so the answer
     * is taken where it is made rather than worked out again from the tree
     * ({@link CallsLeftStanding}).
     */
    Expansion<Hir.Expr> expanding(Hir.Expr e, BindingOwner into) {
        return expanding(() -> inline(e, into));
    }

    /**
     * What one run of {@code expansion} left standing, for a driver expanding something this class
     * has no single entry point for — the several clauses of one declaration, expanded one after
     * another into the tree the declaration becomes.
     */
    <T> Expansion<T> expanding(java.util.function.Supplier<T> expansion) {
        java.util.SequencedSet<ReachName.Declaration> asked = new java.util.LinkedHashSet<>();
        standingHere.add(asked);
        try {
            return new Expansion<>(expansion.get(), asked);
        } finally {
            standingHere.remove(standingHere.size() - 1);
        }
    }

    /**
     * Runs {@code expansion} as one writing into {@code into}.
     *
     * <p>The writing is a value and it is made whole: nothing it holds is left from the writing
     * before, and nothing it holds outlives it — including where an expansion was refused partway
     * through, which is a thing that happens, because a caller records a refusal and hands the next
     * body to the same pass.
     *
     * <p>A writing may hold another. A helper's body is expanded while the body that called it is
     * being expanded, so the one in force is put back when this one is done rather than dropped.
     */
    private Hir.Expr writing(BindingOwner into, Set<BindingId> dependencies,
                             java.util.function.Supplier<Hir.Expr> expansion) {
        Writing outer = writing;
        // Numbered among what this pass has written into that body, so a second writing into it — a
        // second clause of one invariant, a second argument of one helper — writes bindings of its
        // own rather than the first one's over again.
        BindingOwner mine = new BindingOwner.Synthesized(into, BindingOwner.Pass.INLINER,
                written.merge(into, 1, Integer::sum) - 1);
        // The writing is what places the copies, so it is what they are written under. Rooted at
        // the body instead, two writings into one body would place one call's copy in one spot
        // twice — the body cannot tell them apart, and which writing this is is exactly what does.
        writing = new Writing(into, mine, ExpansionLineage.ORIGINAL, new Hir.Binders(mine),
                dependencies, new HashMap<>(), new LinkedHashMap<>());
        try {
            return expansion.get();
        } finally {
            writing = outer;
        }
    }

    /**
     * Refuses a body whose expansion would say more than a definition holds.
     *
     * <p>Before the expansion and not after it. What the expansion builds is what a walk after it
     * descends, so a body that composed past the bound is one nothing downstream can be asked
     * about — and the expansion that built it descends it too, so finding out by running it is
     * finding out by running out.
     *
     * <p>Counted over what the source wrote, with each name standing for what it reaches. The body
     * as written was already held to the bound where it was written; what is asked here is the
     * larger question, which only substitution can answer: a definition can be small and name three
     * that are not.
     */
    private void heldToTheBound(Hir.Expr body) {
        StructuralCost.Composed composed = StructuralCost.composed(body, this::substitutedAt);
        if (composed.isPastTheBound()) {
            throw CompileException.of(Diagnostic
                    .say(new DeclarationMessage.SubstitutingAValueIsMoreStructureThanIsHeld(
                            composed.past().name(), StructuralCost.MAX))
                    .at(composed.past().pos())
                    .hint(new DeclarationMessage.WriteItAsABehaviorOfItsOwn())
                    .build());
        }
    }

    /** The body {@code name} would put here, or null where the name stands for itself — asked as
     *  {@link #valueOf} and {@link #expandCall} ask it, so what is counted is what is spliced. */
    private Hir.Expr substitutedAt(Hir.Var.Denoting name) {
        ReachName.Declaration reaches = name.reachesADeclaration();
        if (!(name.denotes() instanceof ValueName.Helper) || reaches == null
                || graph.recurses(reaches)) {
            return null;
        }
        Hir.FnDef reached = table.reached(reaches);
        return reached == null || reached.body() == null ? null : reached.writtenBody();
    }

    /** The next number this pass has for the body it is writing into. */
    private int next() {
        return written.merge(writing.destination(), 1, Integer::sum) - 1;
    }


    /** Rewrites every helper call in {@code e} to its inlined body, into the body this writing names.
     * Private, because there is no body to write into until a writing says which, and the writings
     * are started above. */
    private Hir.Expr inline(Hir.Expr e) {
        return switch (e) {
            // Applying something other than a name. The applied expression is bound first and the
            // application reads the binding, which is the shape every reader downstream already has
            // — and which says outright what the order is: the function is worked out once, before
            // any argument, and the binding is what is applied.
            case Hir.Apply raw when !raw.calleeIsAName() -> {
                Hir.Binder f = writing.binders().binder("$fn" + next(), raw.function().pos());
                // What the application reaches is the binding. What a report about it quotes is
                // what the author wrote — a field read applied (`deps.count(x)`) has a spelling,
                // and quoting the binding would name `$fn0`, which is nowhere in the source — and
                // the application carries that, this being a rewrite of what it applies.
                ValueName.Local applied = new ValueName.Local(f.name(), f.id());
                yield inline(new Hir.LetIn(f, raw.function(), null, false, null,
                        // A read of the binding this pass just made, which no source wrote.
                        raw.replacedBy(Hir.Var.respelled(f.name(),
                                new ReachName.InScope(applied), null, raw.function().pos(),
                                raw.function().region())),
                        raw.pos(), raw.region()));
            }
            case Hir.Apply rawCall -> expandCall(rawCall);
            case Hir.FieldAccess fa -> fa.withTarget(inline(fa.target()));
            case Hir.Binary bin -> new Hir.Binary(bin.op(), inline(bin.left()), inline(bin.right()),
                    bin.origin(), bin.pos(), bin.region());
            case Hir.Neg neg -> new Hir.Neg(inline(neg.operand()), neg.pos(), neg.region());
            case Hir.NewData nd -> newData(nd);
            case Hir.Match m -> {
                List<Hir.Case> cases = new ArrayList<>();
                for (Hir.Case c : m.cases()) {
                    cases.add(new Hir.Case(c.caseTypes(), c.binding(), inline(c.body()), c.unwrapAsserts(), c.pos()));
                }
                yield new Hir.Match(inline(m.scrutinee()), cases, m.origin(), m.pos(), m.region());
            }
            case Hir.If iff -> new Hir.If(inline(iff.cond()), inline(iff.then()), inline(iff.els()),
                    iff.origin(), iff.pos(), iff.region());
            case Hir.IfConstructed ic -> new Hir.IfConstructed(inline(ic.construct()), ic.binder(),
                    inline(ic.then()), Hir.mapArms(ic.els(), this::inline), ic.origin(), ic.pos(),
                    ic.region());
            // Already expanded. Its body may still hold calls of its own — a helper whose callee was
            // not in the table when this ran the first time — so it is walked like any other.
            case Hir.Expansion ex -> {
                List<Hir.Bound> bound = new ArrayList<>();
                for (Hir.Bound b : ex.bound()) {
                    bound.add(new Hir.Bound(b.binder(), b.declaredType(), inline(b.value())));
                }
                List<Hir.Given> given = new ArrayList<>();
                for (Hir.Given g : ex.given()) {
                    given.add(new Hir.Given(g.declaredType(), inline(g.value()), g.applied(),
                            g.arrivesAs()));
                }
                // Walked with this expansion as the copy being written: a call the body still holds
                // is one this expansion made, not one the body around it made.
                yield new Hir.Expansion(ex.callee(), ex.application(), ex.at(), bound, given,
                        ex.declaredReturn(),
                        insideThisCopy(ex.application(),
                                writing.lineage().copiedInto(ex.callee(), ex.at()), Map.of(),
                                () -> inline(ex.body())),
                        ex.pos(), ex.region());
            }
            case Hir.LetIn li -> {
                // What the value turns out to be is what decides this, so it is worked out first: a
                // lambda the author wrote and a named function read as a value are the same block by
                // the time either gets here, and a `let` should not tell them apart.
                Hir.Expr value = inline(li.value());
                // A binding that holds a function, read into another binding: the second names the
                // same function, so it is registered under it. Nothing is copied — what a name means
                // is what it was given, and here it was given a binding.
                Hir.FnDef aliased = value instanceof Hir.Var.Denoting v ? expands(v) : null;
                if (aliased != null) {
                    BindingId alias = li.binder().id();
                    // A second name for a callable is the same callable, so what the first crossed
                    // into the second crossed into, and a report about either points where the
                    // block was written. Dropped, an operation's body binding its parameter to a
                    // name of its own would be running code of its own under the second name, and
                    // the envelope it opened would never close.
                    BindingId first = value instanceof Hir.Var.Denoting v2
                            && v2.denotes() instanceof ValueName.Local held ? held.id() : null;
                    ScopedLambda under =
                            first == null ? null : writing.scopedLambdas().get(first);
                    writing.scopedLambdas().put(alias,
                            under != null && under.origin() != null
                                    ? new ScopedLambda(aliased, under.origin())
                                    : new ScopedLambda(aliased));
                    ExpansionSite.Supplied.Handover crossed =
                            first == null ? null : writing.suppliedFrom().get(first);
                    if (crossed != null) {
                        writing.suppliedFrom().put(alias, crossed);
                    }
                    stands(alias, ruleOf((Hir.Var.Denoting) value));
                    holds(alias, value);
                    Hir.Expr aliasBody = inline(li.body());
                    writing.scopedLambdas().remove(alias);
                    writing.suppliedFrom().remove(alias);
                    yield references(aliasBody, alias)
                            ? new Hir.LetIn(li.binder(), value, li.declaredType(), li.annotated(),
                                    li.opens(), aliasBody, li.pos(), li.region())
                            : aliasBody;
                }
                if (!(value instanceof Hir.Block lambda)) {
                    yield new Hir.LetIn(li.binder(), value, li.declaredType(), li.annotated(),
                            li.opens(), inline(li.body()), li.pos(), li.region());
                }
                // a function bound to a local: registered under that binding, so each application of
                // it in the body expands inline (β-reduction) exactly as a named helper does. Its
                // parameters are untyped, so their types flow in from the arguments at expansion. No
                // runtime closure is built as long as it does not escape.
                //
                // It cannot reach itself: a `let` does not bind its own name in its value (spec
                // 16.1), so a name inside spelled like it is whatever it was outside, and expansion
                // follows what a name denotes rather than how it is spelled.
                List<Hir.FnParam> params = new ArrayList<>();
                for (Hir.Binder p : lambda.params()) {
                    params.add(new Hir.FnParam(p, null));
                }
                BindingId bound = li.binder().id();
                writing.scopedLambdas().put(bound, new ScopedLambda(
                        Hir.FnDef.lambda(li.name(), params, null,
                                new Hir.FnBody.Written(lambda.body()), li.pos())));
                // Read off what the author bound, not off what it became: a name reaching a place
                // that wants a function is wrapped in a block written at that place, and two names
                // for one declaration would come out as two rules written in two places.
                stands(bound, li.value() instanceof Hir.Var.Denoting named ? ruleOf(named)
                        : ruleOf(lambda));
                holds(bound, li.value());
                Hir.Expr body = inline(li.body());
                writing.scopedLambdas().remove(bound);
                // if the binding is still read, the function was used as a value, not just applied —
                // it escapes, which needs a runtime closure. Keep the binding so the check that
                // reports an escaping block sees it.
                yield references(body, bound)
                        ? new Hir.LetIn(li.binder(), lambda, li.declaredType(), li.annotated(),
                                li.opens(), body, li.pos(), li.region())
                        : body;
            }
            case Hir.ListLit lit -> new Hir.ListLit(inlineList(lit.elements()), lit.origin(),
                    lit.pos(), lit.region());
            case Hir.RowCollection row -> new Hir.RowCollection(inlineList(row.elements()),
                    row.origin(), row.pos(), row.region());
            case Hir.Tuple tup -> new Hir.Tuple(inlineList(tup.elements()), tup.pos(), tup.region());
            case Hir.TupleGet tg -> new Hir.TupleGet(inline(tg.tuple()), tg.index(), tg.arity(), tg.pos(),
                    tg.region());
            case Hir.ListComp comp -> new Hir.ListComp(inline(comp.element()), inlineList(comp.guards()),
                    comp.origin(), comp.pos(), comp.region());
            case Hir.Block block -> new Hir.Block(block.params(), inline(block.body()), block.rule(),
                    block.pos(),
                    block.region());
            case Hir.IntLit _ -> e;
            case Hir.DecimalLit _ -> e;
            case Hir.StringLit _ -> e;
            case Hir.BoolLit _ -> e;
            case Hir.Unreachable _ -> e;
            case Hir.Var v -> valueOf(v);
        };
    }

    /**
     * One call of a name, with the callee's body in place of it where a body stands behind the name.
     *
     * <p>Not every call has one. A builtin, an injected behavior, a function-typed parameter and a
     * recursive helper are all applied by something other than an expansion, so the call stays a call
     * and only its arguments are expanded. What is left — a non-recursive helper, a value that is a
     * function, a lambda a binding holds — becomes an {@link Hir.Expansion}: one node, because the
     * callee's signature is one statement and this call decides its variables once.
     */
    private Hir.Expr expandCall(Hir.Apply rawCall) {
        if (rawCall.answered() == null && rawCall.calleeIsAName()) {
            // The callee names nothing, which was reported where it is written. No body stands
            // behind it, no library sugar reaches for it, and no parameter list holds its arguments
            // against anything — so the call stays as it is and only its arguments are expanded.
            List<Hir.Expr> args = new ArrayList<>();
            for (Hir.Expr a : rawCall.args()) {
                args.add(inline(a));
            }
            return rawCall.withArgs(args);
        }
        checkFunctionArgumentPlacement(rawCall);
        Hir.Apply call = desugarNamedBlock(desugar(table.library(), rawCall));
        List<Hir.Expr> args = new ArrayList<>();
        for (Hir.Expr a : call.args()) {
            args.add(inline(a));
        }
        Hir.FnDef helper = appliedHelper(call);
        // What is applied, where it is a name that names something. A body stands behind nothing
        // else, so where this is absent the call is left as it is.
        Hir.Var.Denoting callee = call.answered();
        // a recursive helper is reached by the name it is declared under; a lambda a binding
        // holds is not one, whatever it is called
        ReachName.Declaration reaches =
                callee == null ? null : callee.reachesADeclaration();
        boolean standing = reaches != null && graph.recurses(reaches);
        if (standing) {
            // The requirement this expansion just made: the call stays a call, so a method for what
            // it reaches has to be emitted wherever this tree ends up.
            leftStanding.add(reaches);
            for (java.util.SequencedSet<ReachName.Declaration> asked : standingHere) {
                asked.add(reaches);
            }
        }
        if (helper == null || standing) {
            // builtin, injected behavior, a function-typed parameter, or a recursive helper —
            // a recursive helper is lowered to a method, so its call stays a Call (spec §fn-declaration);
            // only its args inline.
            return call.withArgs(forwardDependencies(helper, args));
        }
        // A declaration written with no parameter list is a value ([#fn-declaration]), so
        // applying it applies whatever function that value is — not the declaration, which
        // takes nothing. The value is substituted and the arguments are applied to it.
        if (helper.params().isEmpty() && !args.isEmpty()
                && call.function() instanceof Hir.Var named) {
            return inline(call.replacedBy(valueOf(named), args));
        }
        if (args.size() != helper.params().size()) {
            throw wrongArity(call, helper, args.size());
        }
        // Everything this expansion writes belongs to it: the bindings its arguments become,
        // the one a lambda given to a function parameter is registered under, the one its
        // declared return is carried on, and every binding copied out of the callee's body.
        // One minter, so no two of them are the same binding, and a reader can ask of any of
        // them which call it came from.
        // An expansion is what its bindings belong to, so two of them have to be two — and what
        // says so is that the application it is of can be told from every other of its kind. Asked
        // here, after what is expanded has been decided, because the two are separate questions:
        // whether a call is expanded is about the callee, and this is about what the expansion can
        // be named by. A call reaching here with no such application is not a call to leave
        // standing — nothing composes one — and saying so is what this is.
        if (!(call.application() instanceof ApplicationOrigin.Identified at)) {
            throw new IllegalStateException(
                    "a helper expanded at an application that says only why it is here: " + call);
        }
        // And where the copy is, which is the same call said in words the source settles. Worked
        // out here, where the application is still in hand, and refused where it projects to
        // nothing — a copy this cannot name is one whose constructs would be named by whatever the
        // application happens to carry, and some of that is this compiler's own counting.
        ExpansionSite site = siteOf(at, call);
        // And the copy itself, which is what a callable this call hands over crosses into. Worked
        // out before the arguments are bound because that is where a boundary is recorded, and the
        // boundary is this copy.
        ExpansionLineage.Expansion deeper = writing.lineage().copiedInto(callee.denotes(), site);
        ExpansionLineage.Step crossingInto = deeper.step();
        BindingOwner mine =
                new BindingOwner.Expansion(writing.enclosing(), callee.denotes(), at);
        Hir.Binders ours = new Hir.Binders(mine);
        // What the callee's signature leaves open, this call decides. Its variables are
        // instantiated once, here, over the whole signature at once — so a variable it wrote
        // in two of its parameters is one variable in what this expansion writes, and two
        // calls of it decide separately. Splitting the signature into a binding per parameter
        // is what would otherwise lose that: each binding's type would be read on its own,
        // and nothing left afterwards says the two came from one application.
        Map<String, Type> applied = instantiation(helper, mine);
        // Which declaration this copy is of, following a name to what it holds. The name is where
        // a callable was put and is not the callable: read as one, a copy made through a name is a
        // copy of a declaration nobody wrote, and nothing downstream can match it to the one whose
        // parameters were named.
        Arguments arguments =
                bindArguments(rawCall, call, helper, args, applied, ours, mine, reaches(callee),
                        crossingInto);
        // A body this compile cannot show is copied with the call site stamped over it, so a report
        // from inside it points at the user's call rather than at a line nobody holds — and the
        // stamp says that is what it is doing, so nothing downstream reads the call as the place the
        // code is written.
        DeclaringCode declaring = whereTheBodyIs(call, helper);
        Copy copy = new Copy(helper.writtenBody(), ours);
        // What was proved of the bindings this body has, said again of the ones the copy gives them.
        // The body being copied is one already expanded — a lambda registered here is registered
        // with its calls spliced in — so the operation that proved anything of it is gone from what
        // is copied, and nothing downstream proves it again of the bindings the copy makes. Said
        // here because this is where the whole renaming is in hand and nothing has been written from
        // it yet.
        provenance.carriedAcross(copy.renaming());
        Renaming renaming = new Renaming(arguments.subst(), copy,
                declaring == null ? null : call.pos().standingInFor(declaring),
                declaring == null ? null : standingIn(call.region(), declaring));
        // The nested helpers too, and written under this copy: a call inside the callee's body
        // belongs to the copy of that body it stands in, so a helper expanded at two places holds
        // two of everything inside it. The body being written into is the same for both and cannot
        // tell them apart.
        Hir.Expr body = insideThisCopy(mine, deeper, arguments.supplied(),
                () -> inline(rename(helper.writtenBody(), renaming)));
        List<Hir.Bound> bound = new ArrayList<>(arguments.bound());
        // A scoped lambda the body still names was passed rather than applied, so nothing
        // reduced it and the name would stand for nothing. It is bound to what it names, which
        // is what a lambda given a name is anywhere else.
        arguments.unreduced().forEach((id, lambda) -> {
            if (references(body, id)) {
                bound.add(lambda);
            }
        });
        arguments.unreduced().keySet().forEach(writing.scopedLambdas()::remove);
        return new Hir.Expansion(callee.denotes(), mine, site, bound, arguments.given(),
                instantiated(helper.declaredReturn(), applied), body, call.pos(), call.region());
    }

    /**
     * Where the copy {@code at} makes is, or a refusal where the application projects to nothing the
     * source settles.
     *
     * <p>The one crossing from why an application is here to which call it is. Three of the four
     * kinds of application reach an expansion and each projects differently: one the source wrote is
     * the call itself, a block a name was expanded into is the reference the author wrote, and a
     * block bound inside a copy is that copy and the parameter it filled.
     *
     * <p><b>Refused rather than given a name of some other kind.</b> A reference this compiler
     * composed says nothing but a number, and a binding written by a pass is numbered among what
     * that pass wrote — a copy named by either is a copy whose name moves when the compiler is asked
     * to do the same work in another order, and every construct inside it moves with it. Stopping
     * here is how such an application gets looked at rather than absorbed.
     *
     * <p><b>Why nothing reaches those refusals today, read off what makes one.</b> The applications
     * and references a pass composes are the collection a row writes in brackets, which stand for
     * {@code fromList} and {@code empty} — and the library declares both as intrinsics. Only an
     * operation the library writes a body for is in the table this inlines from
     * ({@link HelperTable}), so a composed application never reaches an expansion. That is a fact
     * about what the library declares rather than about what any model happens to be written with,
     * and it is what the refusals below rest on.
     */
    private ExpansionSite siteOf(ApplicationOrigin.Identified at, Hir.Apply call) {
        // A callable this copy was handed, applied where the copy taking it applies it. Asked
        // before what the application was written as, because that is the operation's own code: the
        // body of `List.any` writes the application of its parameter, and reading the site off it
        // would say the copy was made in the library where what is being copied is the caller's.
        //
        // Whichever way the author spelled the callable. A lambda written at the call, a name they
        // bound first, and a second name for either are one fact, and this is where the three meet
        // — so a model reads the same however the closure was written down.
        ExpansionSite.Supplied.Handover supplied = handedToThisCopy(call);
        if (supplied != null) {
            // And which application of it this copy is. An operation may apply a block it was
            // handed more than once, and each of those is a copy of the block's body standing on
            // its own — so the handover alone would name them all alike.
            return new ExpansionSite.Supplied(supplied, appliedAt(call));
        }
        return switch (at) {
            case ApplicationOrigin.Written(SourceConstructOrigin application) ->
                    new ExpansionSite.Written(application);
            case ApplicationOrigin.Eta(EtaOrigin cause) -> etaSiteOf(cause, call);
            // What a pass wrote because of something it can name. Nothing reaches an expansion this
            // way today, and what such an application projects to is a question about the pass that
            // wrote it: it is answered when one turns up, by whoever writes it.
            case ApplicationOrigin.Derived derived -> throw new IllegalStateException(
                    "a copy of a body made at an application a pass composed: " + derived
                            + " at " + call.pos());
        };
    }

    /**
     * Where this copy was handed the callable {@code call} applies, or null where it was not handed
     * one.
     *
     * <p>Read off what the call applies rather than off how the application was written: what
     * matters is whose code is about to be copied, and a caller's callable is the caller's wherever
     * the application of it stands.
     *
     * <p>And read off where the callable crossed in rather than off where it is being applied. An
     * operation may hand a block it was given straight on to another, and the application that runs
     * it then stands in the second while the code it runs was handed to the first — so the copy
     * this names is the one the caller's code was left behind at, which is the one the boundary was
     * recorded at when the callable was bound.
     */
    private ExpansionSite.Supplied.Handover handedToThisCopy(Hir.Apply call) {
        if (call.answered() == null
                || !(call.answered().denotes() instanceof ValueName.Local local)) {
            return null;
        }
        return writing.suppliedFrom().get(local.id());
    }

    /**
     * Which application of a supplied block {@code call} is, as the source wrote it.
     *
     * <p>A site of the body the block is being copied into, which is what tells one application of
     * it from another: {@code List.distinctBy} writes two applications of its key, and the two
     * copies of that key differ in nothing else. Read off the application rather than counted, so
     * that the two are the same two whichever order the expansions ran in.
     *
     * <p><b>Read off the application alone.</b> Where the block came from is what the handover
     * beside this says, and asking it again here would be asking the question this is a component of
     * — an operation that binds the block it was handed to a name of its own applies it through that
     * name, and following the name back would come to the handover and start over. So an application
     * written as a call is that call, and one a name was expanded into is that name.
     *
     * <p>Refused where neither is what the source wrote. Such an application is named by what a pass
     * composed, and a copy named by that is one whose name moves when the compiler is asked to do
     * the same work in another order — the same reason a call a pass wrote is refused where a site
     * is worked out.
     */
    private static ExpansionSite.Direct appliedAt(Hir.Apply call) {
        ReferenceOrigin named;
        switch (call.application()) {
            case ApplicationOrigin.Written(SourceConstructOrigin at) -> {
                return new ExpansionSite.Written(at);
            }
            case ApplicationOrigin.Eta(EtaOrigin.Declaration(ReferenceOrigin reference)) ->
                    named = reference;
            case ApplicationOrigin.Eta(EtaOrigin.Bound(BindingId _, ReferenceOrigin reference)) ->
                    named = reference;
            default -> throw new IllegalStateException(
                    "a block applied at an application no source wrote: " + call.application()
                            + " at " + call.pos());
        }
        if (named instanceof SourceReferenceOrigin written) {
            return new ExpansionSite.Named(written);
        }
        throw new IllegalStateException(
                "a block applied at a name this compiler composed: " + named
                        + " at " + call.pos());
    }

    /** The same, for the block a name or a binding was expanded into. */
    private ExpansionSite etaSiteOf(EtaOrigin cause, Hir.Apply call) {
        switch (cause) {
            case EtaOrigin.Declaration(ReferenceOrigin reference) -> {
                // The reference the author wrote, counted within what wrote it. A reference this
                // compiler composed carries a number and nothing else, so it names no copy.
                if (reference instanceof SourceReferenceOrigin written) {
                    return new ExpansionSite.Named(written);
                }
                throw new IllegalStateException(
                        "a copy of a body made at a name this compiler composed: " + reference
                                + " at " + call.pos());
            }
            case EtaOrigin.Bound(BindingId binding, ReferenceOrigin reference) -> {
                // The block a call handed to a parameter, named by where it crossed in: the copy it
                // was handed to and the parameter it filled, both recorded when the callable was
                // bound. Whichever way the author spelled it — a lambda written at the call, a name
                // they bound first, a second name for either — and however many operations it was
                // handed on through since.
                ExpansionSite.Supplied.Handover handed = writing.suppliedFrom().get(binding);
                if (handed != null) {
                    return new ExpansionSite.Supplied(handed, appliedAt(call));
                }
                // And a lambda the author bound to a name and then wrote where a value goes. No
                // call handed it to anything, so there is no copy and no parameter to name it by —
                // what the author wrote is the name, which is what every other name written where a
                // value goes is named by.
                if (reference instanceof SourceReferenceOrigin written) {
                    return new ExpansionSite.Named(written);
                }
                throw new IllegalStateException(
                        "a copy of a body made at a binding no call handed to a parameter and no"
                                + " source wrote a name for: " + binding + " at " + call.pos());
            }
        }
    }

    /**
     * A call written with a different number of arguments than the callee takes, named against
     * whichever of the two the author wrote.
     *
     * <p>A lambda given to a function parameter is inlined under a synthetic name, so a report that
     * quoted the callee would quote a name nowhere in the source. Where the callee is one of those,
     * the parameter count is reported against the lambda instead.
     */
    private CompileException wrongArity(Hir.Apply call, Hir.FnDef helper, int given) {
        ScopedLambda applied = call.answered() != null
                && call.answered().denotes() instanceof ValueName.Local local
                ? writing.scopedLambdas().get(local.id()) : null;
        LambdaOrigin origin = applied == null ? null : applied.origin();
        if (origin != null) {
            return CompileException.of(Diagnostic.at(origin.pos())
                    .say(new HelperMessage.TheBlockTakesAnotherNumberOfArguments(origin.param(),
                            origin.owner(), String.valueOf(given),
                            String.valueOf(helper.params().size())))
                    .build());
        }
        return CompileException.of(Diagnostic.at(call.appliedAt())
                .say(new HelperMessage.CalledWithAnotherNumberOfArguments(helper.name(),
                        String.valueOf(helper.params().size()), String.valueOf(given)))
                .build());
    }

    /**
     * What a callable handed to parameter {@code slot} of the copy {@code taking} crosses out of,
     * given what it was already crossing out of where the call is written.
     *
     * <p>The copy that was handed it by whoever wrote it. That is where the code the callable is
     * made of stops and somebody else's begins, and a copy that was handed the callable from
     * inside is passing on what it was given rather than being handed anything of its own — so the
     * crossing stays where it was, however many copies it goes through afterwards.
     *
     * <p>Which is one fact and not two. A copy the callable was handed to further in is the copy an
     * application of it stands in, and a lineage already says that; what nothing else says is which
     * copy the writer's code was left behind at.
     */
    private static ExpansionSite.Supplied.Handover crossedInto(
            ExpansionSite.Supplied.Handover crossing, ExpansionLineage.Step taking, int slot) {
        return crossing != null ? crossing
                : new ExpansionSite.Supplied.Handover(taking, new ParameterSlot(slot));
    }

    /**
     * What one call's arguments become where the callee's body is spliced in.
     *
     * <p>A value argument becomes a binding the body reads by name; a function argument becomes
     * neither — it leaves no binding, so what the signature said about it is held in {@code given}
     * and nowhere else. {@code subst} answers the callee's parameters in both cases, so the copied
     * body reads one thing per parameter however the parameter arrives.
     *
     * <p>{@code unreduced} holds the lambdas registered under a fresh binding, each with the binding
     * a {@code let} would hold it in. They are the ones this expansion may not reduce away — a
     * representation that keeps a call standing keeps the application inside it — so the binding is
     * built ahead and used only for the ones the body still names afterwards. Being the keys of that
     * map is also what says which registrations this call has to drop when it is done.
     *
     * <p>{@code supplied} is where each callable this call hands over crossed out of the code that
     * wrote it — this copy for one written or named here, and the boundary it already carried for
     * one this copy was itself handed. What the body being spliced in reads it under.
     */
    private record Arguments(Map<BindingId, Substituted> subst, List<Hir.Bound> bound,
                             List<Hir.Given> given, Map<BindingId, Hir.Bound> unreduced,
                             Map<BindingId, ExpansionSite.Supplied.Handover> supplied) {}

    /**
     * Binds one call's arguments against the callee's parameters, this call's variables written in.
     *
     * <p>{@code rawCall} is the call as the author wrote it, which is what a report about an argument
     * quotes; {@code call} is what it desugared to, which is what the expansion is built from.
     */
    private Arguments bindArguments(Hir.Apply rawCall, Hir.Apply call, Hir.FnDef helper,
                                    List<Hir.Expr> args, Map<String, Type> applied,
                                    Hir.Binders ours, BindingOwner mine,
                                    ReachName.Declaration declaration,
                                    ExpansionLineage.Step crossingInto) {
        // what stands in the body for each of the callee's parameters: the name it is written
        // as and what that name resolved to at the call site, so the expansion carries the
        // argument's own answer rather than deciding one for it
        Map<BindingId, Substituted> subst = new HashMap<>();
        Map<BindingId, ExpansionSite.Supplied.Handover> handedHere = new LinkedHashMap<>();
        Map<BindingId, Hir.Bound> unreduced = new LinkedHashMap<>();
        List<Hir.Bound> bound = new ArrayList<>();
        List<Hir.Given> given = new ArrayList<>();
        // Where this call is the application of a lambda that answers one result per element of a
        // container, the element arrives under the parameter being bound below. Read before the
        // loop, since what it is about is the call and not any one argument.
        BindingId walked = pointwise.get(appliedLambda(call));
        // And where this call is the operation that hands such a lambda its elements, the two ends
        // of that fact are among the arguments: the lambda is one and the container is another. Both
        // are gathered as they are met and joined once the loop has them.
        BuiltFrom<DeclaredArgument> builtByCallee =
                DefaultBoundOperationFacts.get().buildsItsResultFrom(calleeOf(call));
        DeclaredArgument mapsEach = builtByCallee == null ? null
                : builtByCallee.mapsEachElementOf();
        BindingId theLambda = null;
        BindingId theContainer = null;
        for (int i = 0; i < helper.params().size(); i++) {
            Hir.FnParam p = helper.params().get(i);
            Hir.Expr arg = args.get(i);
            if (p.type() != null && p.type().asFn() != null) {
                // a function argument is not a value, so it cannot be bound to a let. A named
                // function is substituted directly (f(x) becomes inc(x)); a lambda is
                // registered under a fresh name as a scoped helper, so each application of the
                // parameter β-reduces to the lambda's body, as a let-bound lambda does (spec §blocks).
                // Asked of the callee as written, not of what it expanded to: applying a
                // function parameter is what removes it, because the application β-reduces
                // to the lambda's body, so the expansion holds no reference either way.
                given.add(new Hir.Given(instantiated(p.type(), applied), arg,
                        references(helper.writtenBody(), p.binder().id()), arrivesAs(arg)));
                Hir.FnType declares = declaredFn(p.type(), applied);
                // Which rule this call handed to this parameter, said where the call site is
                // still here to say it. What the expansion holds afterwards is the rule's own body
                // standing where the parameter was, and nothing in that says whether the caller
                // supplied the rule or what the rule reads.
                // Taken from the call as the author wrote it. A name handed to a function
                // parameter is wrapped in a lambda before it gets here where the arities have to be
                // made to meet, and the wrapper is written at the call site -- so read off what
                // arrives, naming one declaration at two call sites would be two rules.
                Hir.Expr authored = rawCall.args().size() == args.size()
                        ? rawCall.args().get(i) : arg;
                souther.compiler.coverage.SuppliedRules.RuleIdentity handedIn = authored instanceof Hir.Var.Denoting handed ? ruleOf(handed)
                        : authored instanceof Hir.Block written ? ruleOf(written) : null;
                // Both, or nothing. A copy whose declaration nothing here names cannot be matched
                // against the one whose parameters were named, and recording it under a name that
                // is not a declaration's would put it under one nobody wrote.
                if (handedIn != null && declaration != null) {
                    supplied.handed(mine, declaration, p.name(), handedIn);
                }
                if (arg instanceof Hir.Var.Denoting supplyingName
                        && supplyingName.denotes() instanceof ValueName.Local suppliedLocal) {
                    handedHere.put(suppliedLocal.id(),
                            crossedInto(writing.suppliedFrom().get(suppliedLocal.id()),
                                    crossingInto, i));
                }
                if (arg instanceof Hir.Var.Denoting fnName) {
                    // A name handed to a function parameter is substituted through: what
                    // applies it applies what it stands for. What it stands for is declared
                    // somewhere — a helper's own parameter, a binding, a function an
                    // enclosing call gave — and that declaration is carried on the boundary,
                    // so the two are read against each other without either being re-typed.
                    // The reference it was reached by, carried across rather than rebuilt: what a
                    // name handed to a function parameter stands for is reached exactly as the
                    // caller reached it.
                    subst.put(p.binder().id(), new Substituted(fnName.name(), fnName.reachedAs()));
                } else if (arg instanceof Hir.Block lambda) {
                    Hir.Binder f = ours.binder(suppliedAs(p.name()), lambda.pos());
                    subst.put(p.binder().id(), Substituted.of(f));
                    // The lambda is registered under what the callee declared of the
                    // parameter it was given to, this application's variables written in. So
                    // where the callee applies it, that application expands like any other
                    // call and is read against the signature there — in the one place the
                    // types this application decided are in force. Registering it bare is
                    // what used to throw the signature away at the point it was reduced,
                    // leaving nothing between the caller's function and what was declared of
                    // it (issues #318, #320).
                    List<Hir.FnParam> lparams = new ArrayList<>();
                    for (int lp = 0; lp < lambda.params().size(); lp++) {
                        lparams.add(new Hir.FnParam(lambda.params().get(lp),
                                declares == null || lp >= declares.params().size() ? null
                                        : declares.params().get(lp)));
                    }
                    // the lambda's body is caller code, so it is not renamed by this helper's
                    // substitution — only the enclosing helper body is.
                    writing.scopedLambdas().put(f.id(), new ScopedLambda(
                            Hir.FnDef.lambda(f.name(), lparams,
                                    declares == null ? null : declares.result(),
                                    new Hir.FnBody.Written(lambda.body()), lambda.pos()),
                            new LambdaOrigin(p.name(), helper.name(), lambda.pos())));
                    // A block written at this call was written by whoever wrote the call, so what it
                    // crosses into is this copy whatever stood before it. Recorded in the one place
                    // a crossing is, so that handing it on from inside reads the same as handing on
                    // a name.
                    handedHere.put(f.id(), crossedInto(null, crossingInto, i));
                    stands(f.id(), ruleOf(lambda));
                    unreduced.put(f.id(),
                            new Hir.Bound(f, instantiated(p.type(), applied), lambda));
                    // Only where the lambda takes the one value an element arrives as. A closure
                    // given more — an index beside the element — answers about a pair, and one
                    // answer per element says nothing about which of the two a projection is of.
                    if (lambda.params().size() == 1) {
                        theLambda = f.id();
                    }
                } else {
                    throw notAFunction(rawCall, p, i, arg);
                }
            } else {
                // the binding the argument is bound to; the reads of the parameter inside the
                // body are answered with it, so a read says which binding it is rather than
                // where it happens to be written
                Hir.Binder f = ours.binder(p.name(), call.pos());
                subst.put(p.binder().id(), Substituted.of(f));
                // carry the parameter's declared type onto the binding, so a value known to
                // be a sum (an annotated `s: S`) is not narrowed to the argument's specific
                // case when the body is re-checked inline — a `match s` inside still sees S.
                bound.add(new Hir.Bound(f, instantiated(p.type(), applied), arg));
                // Where the argument is itself the expansion of an operation over a collection,
                // what this binding holds came from that operation's own container — and by the
                // time anything reads the tree, the operation is gone. Recorded here, which is the
                // one place both ends are in hand.
                elementsCameFrom(f, arg);
                // The element this application walks arrives under this binding, and the fact that
                // it does was proved where the operation handing it out still stood. What is
                // recorded is the pair and no expression: what the closure answers is read off the
                // tree afterwards, under the licence this gives.
                if (walked != null) {
                    provenance.projectsEachElementOf(f.id(), walked);
                }
                if (mapsEach != null
                        && i == CallArguments.positionOf(mapsEach, calleeOf(call))) {
                    theContainer = f.id();
                }
            }
        }
        // Both ends or neither. A lambda taking one value and a container the operation walks one
        // answer per element of are one fact between them, and half of it licenses nothing.
        if (theLambda != null && theContainer != null) {
            pointwise.put(theLambda, theContainer);
        }
        return new Arguments(subst, bound, given, unreduced, Map.copyOf(handedHere));
    }

    /** The binding a lambda this call applies was registered under, or null where the call applies
     *  something else. */
    private static BindingId appliedLambda(Hir.Apply call) {
        return call.answered() != null
                && call.answered().denotes() instanceof ValueName.Local local ? local.id() : null;
    }

    /** What this call reaches, for a reader asking what the library declares of it. */
    private static ValueName calleeOf(Hir.Apply call) {
        return call.answered() == null ? null : call.answered().denotes();
    }

    /**
     * A value written where a function goes — the argument-order mistake made with a named helper
     * rather than a lambda. Named against the call as written, with the declared order.
     */
    private CompileException notAFunction(Hir.Apply rawCall, Hir.FnParam p, int index, Hir.Expr arg) {
        List<Hir.FnParam> written = declaredParams(rawCall);
        String shape = written == null ? null : written.stream()
                .map(Hir.FnParam::name)
                .collect(java.util.stream.Collectors.joining(", "));
        Diagnostic.Builder d = Diagnostic.at(arg.pos())
                .say(new HelperMessage.ThisArgumentTakesAFunction(rawCall.written(),
                        String.valueOf(index + 1), p.name()));
        if (shape != null) {
            d.hint(new HelperMessage.WriteTheCallThisWay(rawCall.written(), shape));
        }
        return CompileException.of(d.build());
    }

    /**
    /**
     * What made the block a name used as a value stands for necessary.
     *
     * <p>Told by what the reference reaches and not by how it is spelled. A pass may respell a name
     * the author wrote — a helper of another module is written qualified in a body carried out of
     * it — so a spelling that is the pass's says nothing about whose reference it is, and asking
     * that question is what would put the two respelled ones on the wrong side.
     *
     * <p>Total, because a name has one of the two answers by the time it is here. A name reading a
     * binding is told by the binding; a name reaching a declaration is some reference of it and
     * carries which, whoever wrote it ({@link Hir.Var.Denoting}). There is nothing left to refuse.
     */
    private static EtaOrigin etaOf(Hir.Var function) {
        if (function instanceof Hir.Var.Denoting named
                && named.reachedAs() instanceof ReachName.InScope in
                && in.denotes() instanceof ValueName.Local local) {
            return new EtaOrigin.Bound(local.id(), named.origin());
        }
        return new EtaOrigin.Declaration(function.origin());
    }

    /**
     * {@code function} as the function value it names: a block taking as many parameters as the
     * function takes and applying it to them. The same value the author would get by spelling the
     * lambda, so nothing downstream has to know which of the two was written.
     *
     * <p>{@code binderName} names the block's parameters, given the index of each. A source
     * identifier never starts with {@code $}, so a name from any of the callers cannot capture a
     * local of the body it is written into.
     */
    private Hir.Block etaExpand(Hir.Var function, int arity, IntFunction<String> binderName) {
        List<Hir.Binder> params = new ArrayList<>();
        List<Hir.Expr> args = new ArrayList<>();
        for (int i = 0; i < arity; i++) {
            Hir.Binder p = writing.binders().binder(binderName.apply(i), function.pos());
            params.add(p);
            args.add(Hir.Var.local(p, function.pos()));
        }
        // The block and the application in it are this pass's: what the author wrote there is a
        // name, and these are the parameters and the call it stands for. Which expansion it is, is
        // said here, where the name that made it necessary is still in hand — a reader below has
        // only the shape, and the shape is one every composed application wears.
        return new Hir.Block(params,
                Hir.Apply.synthetic(function, args, new ApplicationOrigin.Eta(etaOf(function)),
                        function.pos(), null),
                souther.compiler.types.RuleOrigin.unwritten(), function.pos(), null);
    }

    /**
     * How many inputs the declaration {@code v} reaches takes, where that declaration is one a name
     * written in value position stands for — and empty where the name reaches no such declaration.
     *
     * <p>This asks the declaration and nothing else: not whether the implementation is a Souther
     * body, a kernel or a Java one, which is what is on the other side of the name and not the
     * name's business. A helper, a library function and a behavior differ in what stands behind
     * them and not in what reification needs, which is how many arguments the block it becomes
     * takes.
     *
     * <p>Empty covers a declaration taking nothing and every name that stands for no declaration at
     * all: a binding, a construction, a library name the library does not declare, and a behavior
     * this body may not name. Each of those is left as it was written and reported where it is
     * used. A declaration taking nothing has no function value to become rather than one this
     * declines to make: a {@code let} with no parameter list is a value and is written without
     * {@code ()}, and there is no block taking no parameter to expand it to.
     */
    private OptionalInt declarationArity(Hir.Var name) {
        if (!(name instanceof Hir.Var.Denoting v)) {
            return OptionalInt.empty();   // it stands for no declaration to take anything
        }
        int arity = switch (v.denotes()) {
            case ValueName.Stdlib.Operation lib -> {
                Stdlib.Entry entry = table.library().entry(lib);
                Hir.FnDef declared = entry == null ? null : entry.declaration();
                yield declared == null ? 0 : declared.params().size();
            }
            // A namespace is not applied to anything, so it takes no arguments.
            case ValueName.Stdlib.Namespace _ -> 0;
            case ValueName.Helper _ -> {
                ReachName.Declaration reaches = v.reachesADeclaration();
                Hir.FnDef declared = reaches == null ? null : table.reached(reaches);
                yield declared == null || declared.body() == null ? 0 : declared.params().size();
            }
            // A behavior's name handed over is the behavior: the block applies the behavior, so the
            // emitted code goes through the behavior's class and not through the `let` that
            // implements it. Only the ones a body may name are here — a behavior with a requirement
            // is a binding by the time it can be written.
            case ValueName.Behavior b -> callableBehaviors.getOrDefault(b, 0);
            // A binding holds whatever it was given; a construction, a checker built-in and a name
            // that denotes nothing stand for no declaration at all.
            case ValueName.Local _, ValueName.OfType _, ValueName.Builtin _ -> 0;
            case null -> 0;
        };
        return arity == 0 ? OptionalInt.empty() : OptionalInt.of(arity);
    }

    /**
     * A name written where a value goes, as the value it stands for.
     *
     * <p>A name that reaches a declaration taking arguments is the function it names, written out.
     * A recursive helper is written out too — the call inside stays the call it has to be.
     *
     * <p>A name that denotes a value — a {@code let} written with no parameter list — is expanded to
     * the expression it was defined as. A value is not module state: its body is elaborated where it
     * was declared and substituted at each reference, so nothing is held between them and there is
     * no order in which the module's values come into being. A recursive value is left alone here;
     * the recursion check reports it under its own name.
     *
     * <p>Anything else — a binding, a unit data — is the name itself.
     */
    private Hir.Expr valueOf(Hir.Var v) {
        OptionalInt arity = declarationArity(v);
        if (arity.isPresent()) {
            int k = next();
            return inline(etaExpand(v, arity.getAsInt(), i -> "$v" + k + "_" + i));
        }
        if (!(v instanceof Hir.Var.Denoting named)
                || !(named.denotes() instanceof ValueName.Helper)) {
            return v;
        }
        // Asked with the reference the table is keyed by. The graph is keyed as the table is, and a
        // spelling agrees with that key only where a pass has already written it out qualified.
        ReachName.Declaration reaches = named.reachesADeclaration();
        Hir.FnDef value = reaches == null ? null : table.reached(reaches);
        if (value == null || value.body() == null || graph.recurses(reaches)) {
            return v;
        }
        Hir.Expr settled = settled(named);
        return settled != null ? settled : substituted(named.reaches(), value.writtenBody());
    }

    /**
     * What {@code v} stands for where this expansion was told the value's own answer, or null where
     * it was not and the body has to be copied.
     *
     * <p>Two answers, because two things read a substituted value. What it types as is read by the
     * check, and a reference standing under the settled type says it. What it is a constant of is
     * read by everything that asks whether an expression is known at compile time — a
     * {@code String.matches} pattern, the argument a construction proves its invariant against —
     * and those fold a tree rather than resolve a name ({@link ConstEval}), so a constant is written
     * out as the literal it folded to and they go on reading a literal. Where the value is neither
     * — a construction, a collection, anything a fold does not reach — the reference stands and the
     * check reads its type.
     *
     * <p>The literal is written at the reference. A value is substituted at each of its references
     * (ADR-0072), so what stands there is this reference's, and a report about it belongs where the
     * name was written rather than in the body it came from.
     */
    private Hir.Expr settled(Hir.Var.Denoting v) {
        if (settledValues.valueKept(v.denotes()) == null) {
            return null;
        }
        Object constant = settledConstants.apply(v.denotes());
        return constant == null ? v : literal(constant, v.pos());
    }

    /** {@code constant} as the expression a fold would read it back out of, or null for a value no
     * literal spells. */
    private static Hir.Expr literal(Object constant, SourcePos pos) {
        return switch (constant) {
            // A value the fold arrived at, which no run of characters in the file spells.
            case Long i -> new Hir.IntLit(i, pos, null);
            case java.math.BigDecimal d -> new Hir.DecimalLit(d, pos, null);
            case String s -> new Hir.StringLit(s, pos, null);
            case Boolean b -> new Hir.BoolLit(b, pos, null);
            default -> null;
        };
    }

    /**
     * The body of the value {@code reached} stands for, expanded here — and a refusal where this
     * expansion is already substituting that value.
     *
     * <p>Substituting a value into itself has no end, so an expansion that reached one would descend
     * until the stack ran out. Which modules that can happen to is answered before anything expands a
     * body of one, and it is {@link ValueCycles} that answers it and says so to the author. This is
     * not that rule said twice: it is about this algorithm rather than about the module, and what it
     * gives is that expanding is bounded whatever it is handed. A caller that reached here with a
     * module the answer above would have refused gets a failure naming the value, rather than a stack
     * that ran out and a report about an expression nesting too deeply.
     *
     * <p>What it holds is the path and not what it has seen: a value named twice in one body is
     * substituted twice, side by side, and only one inside the other is re-entry.
     *
     * <p>The path is also what says whose job the mark is. What a value carried is written over the
     * whole expansion once it is whole, by the substitution no other substitution is inside — the
     * outermost one holds every subtree the ones under it produced, and the mark is a flag, so
     * writing it there says of each node what writing it at every level said. Written at every
     * level it is written over each subtree once per level that subtree is under, which is the
     * depth of a chain of values times its length. The walk allocates nothing — rebuilding an
     * expression hands back what it was given where nothing changed — so what it costs is the
     * walking, and nothing downstream of it can see that it ran twice.
     */
    private Hir.Expr substituted(String reached, Hir.Expr body) {
        if (!substituting.add(reached)) {
            throw new ExpansionCycle("`" + reached + "` is substituted into itself ("
                    + String.join(" -> ", substituting) + " -> " + reached + "), and a module whose"
                    + " values are not well founded is refused before a body of it is expanded");
        }
        try {
            Hir.Expr expanded = inline(body);
            return substituting.size() == 1 ? HelperNames.carriedByValue(expanded) : expanded;
        } finally {
            substituting.remove(reached);
        }
    }

    /**
     * A construction, with any spread of a value bound first.
     *
     * <p>A spread names a value the way any other position does, but it holds a name rather than an
     * expression, so the value cannot be substituted in place. It is bound to a fresh {@code $}-name
     * ahead of the construction and the spread copies that binding — the shape a spread of a local
     * already has, so nothing downstream learns a new one.
     */
    private Hir.Expr newData(Hir.NewData nd) {
        List<Hir.Binder> bound = new ArrayList<>();
        List<Hir.Expr> values = new ArrayList<>();
        List<Hir.Var> spreads = new ArrayList<>();
        for (Hir.Var spread : nd.spreads()) {
            Hir.FnDef value = valueSpread(spread);
            if (value == null) {
                spreads.add(spread);
                continue;
            }
            Hir.Binder name = writing.binders().binder(
                    "$s" + next() + "_" + spread.answered().denotes().name(), spread.pos());
            bound.add(name);
            values.add(substituted(spread.name(), value.writtenBody()));
            spreads.add(Hir.Var.local(name, spread.pos()));
        }
        Hir.Expr built = nd.with(inlineInits(nd.inits()), spreads);
        // The bindings a spread of a value becomes stand where the construction stands.
        for (int i = bound.size() - 1; i >= 0; i--) {
            built = new Hir.LetIn(bound.get(i), values.get(i), null, false, null, built, nd.pos(),
                    nd.region());
        }
        return built;
    }

    private List<Hir.Expr> inlineList(List<Hir.Expr> es) {
        List<Hir.Expr> out = new ArrayList<>();
        for (Hir.Expr e : es) {
            out.add(inline(e));
        }
        return out;
    }

    private List<Hir.FieldInit> inlineInits(List<Hir.FieldInit> inits) {
        List<Hir.FieldInit> out = new ArrayList<>();
        for (Hir.FieldInit i : inits) {
            out.add(i.withValue(inline(i.value())));
        }
        return out;
    }

    /**
     * A helper fn passed to {@code fold} by name is sugar for a block that wraps a call:
     * {@code List.fold(step, seed, xs)} with a named {@code step} becomes
     * {@code List.fold(($b0, $b1) -> step($b0, $b1), seed, xs)} (spec §blocks, "名前で直接渡す。同じこと").
     * The generated block has one parameter per helper parameter, so a later arity check against
     * {@code fold} (it wants two) still applies. The block is then expanded inline like any other
     * helper call. Only {@code fold} needs this — map/filter/all/any are helpers whose function
     * parameter the inliner binds directly (see {@link #inline}).
     */
    private Hir.Apply desugarNamedBlock(Hir.Apply call) {
        if (call.answered() == null) {
            return call;   // it reaches nothing, so it is no named block to desugar
        }
        // Only the walk takes a block, and which operation that is, the library says.
        Integer idx = table.library().theWalk().equals(call.answered().denotes())
                ? BLOCK_ARG_OF_THE_WALK : null;
        if (idx == null || idx >= call.args().size()
                || !(call.args().get(idx) instanceof Hir.Var.Denoting named)) {
            return call;
        }
        Hir.FnDef helper = expands(named);
        if (helper == null) {
            return call;   // a bare name that stands for no body is left for the type checker to report
        }
        int k = next();
        Hir.Block block = etaExpand(named, helper.params().size(), i -> "$b" + k + "_" + i);
        List<Hir.Expr> args = new ArrayList<>(call.args());
        args.set(idx, block);
        return call.withArgs(args);
    }

    /**
     * One copy of a body, and the bindings it has of its own.
     *
     * <p>A body spliced into a caller brings its bindings with it, and a second splice brings them
     * again — so each copy owns them rather than the definition they were written in. A binding met
     * in the copy is answered with the copy's, and every name that read it in the original reads the
     * copy's too; a name the substitution answers is the caller's and is left alone.
     */
    private static final class Copy {

        private final Map<BindingId, BindingId> mine = new HashMap<>();

        /** Which owner in the body being copied became which here. */
        private final Map<BindingOwner, BindingOwner> owners = new HashMap<>();

        /** The minter for each of those, so a copy's bindings are numbered among that copy's. */
        private final Map<BindingOwner, Hir.Binders> minters = new HashMap<>();

        /**
         * Every binding the body introduces, given one of this copy's, before any of it is written.
         *
         * <p>Assigned in one pass so that a binder and the names that read it are moved together
         * whatever order the copy is written in: a read met before its binder would otherwise be
         * left on the original while the binder moved, and the two would no longer be one binding.
         *
         * <p><b>And the owners are moved with them.</b> A body already holding expansions is copied
         * whole — a helper whose own calls were spliced in before it was reached — so the bindings
         * inside those expansions belong to owners of their own. Given this copy's root owner
         * outright, every one of them would land under it, and two of the callee's own expansions
         * would write into one place. What is kept is the shape: the root becomes this copy's root,
         * and an owner under it becomes the same owner under what its own owner became.
         *
         * @param binders the minter for this copy's root, which is the expansion's own — an
         *                expansion has already written its arguments from it, and the body's direct
         *                bindings are numbered after those rather than over them
         */
        private Copy(Hir.Expr body, Hir.Binders binders) {
            this.root = binders.owner();
            minters.put(root, binders);
            eachBinder(body, binder ->
                    mine.put(binder.id(),
                            mintedFor(binder.id().owner()).binder(binder.name(), binder.pos())
                                    .id()));
        }

        private final BindingOwner root;

        /**
         * This copy's owner for one the body has.
         *
         * <p>The one place an owner is moved, so that what a binding belongs to and what an
         * expansion node says it wrote are the same answer. Worked out twice, the two would agree
         * until one of them learned a shape the other did not.
         *
         * <p>The shape is kept and the bottom is replaced. Whatever the body's own bindings belong
         * to becomes this copy's root; an expansion written under that becomes the same expansion
         * under what its own owner became. So a helper whose calls were spliced in before it was
         * reached keeps the tree of copies it holds, one deeper, rather than having all of it land
         * in one place.
         */
        BindingOwner ownerOf(BindingOwner original) {
            BindingOwner moved = owners.get(original);
            if (moved != null) {
                return moved;
            }
            BindingOwner made = switch (original) {
                case BindingOwner.Expansion it ->
                        new BindingOwner.Expansion(ownerOf(it.within()), it.expanded(), it.at());
                case BindingOwner.Synthesized it ->
                        new BindingOwner.Synthesized(ownerOf(it.within()), it.pass(), it.ordinal());
                // The body's own. What it was called where it was written says nothing here: the
                // copy is this expansion's, so what its bindings belong to is this expansion.
                default -> root;
            };
            owners.put(original, made);
            return made;
        }

        /** The minter for what {@code original} became, one per owner so that two copies of one
         *  expansion number their bindings apart. */
        private Hir.Binders mintedFor(BindingOwner original) {
            return minters.computeIfAbsent(ownerOf(original), Hir.Binders::new);
        }

        /** Which of the body's bindings became which of this copy's, for a reader whose question is
         *  about the renaming itself rather than about any one name in it. */
        Map<BindingId, BindingId> renaming() {
            return mine;
        }

        /** This copy's binder for one the body has. The copy is this pass's writing, however much
         * it reads like the body it was taken from, so it claims no name position: the place the
         * author wrote that name is the original binding's and stays with it. */
        Hir.Binder of(Hir.Binder binder) {
            return new Hir.Binder(WrittenName.synthetic(binder.name(), binder.pos()),
                    mine.get(binder.id()), binder.pos());
        }

        /** The same, for a name that reads one. A name bound outside the body — a parameter, which
         * the substitution answers — is not this copy's to move. */
        ValueName of(ValueName denotes) {
            if (!(denotes instanceof ValueName.Local local)) {
                return denotes;
            }
            BindingId here = mine.get(local.id());
            return here == null ? denotes : new ValueName.Local(local.name(), here);
        }

        /**
         * The same, for the reference that reads one.
         *
         * <p>A whole reference, so that a route and a declaration from two different references are
         * never paired. What this moves is a binding and nothing else, and a binding is reached
         * where it is bound — so where it moved, the reference is the one for the binding it moved
         * to, and where it did not, the reference is the one that stood here.
         */
        ReachName of(ReachName reference) {
            return of(reference.denotes()) instanceof ValueName.Local moved
                    && !moved.equals(reference.denotes())
                    ? new ReachName.InScope(moved) : reference;
        }
    }

    /**
     * What stands in the body for one of the callee's parameters: the name it is written as, and what
     * that name means.
     *
     * <p>One fact and one value. Written as two tables it was possible to put a name into one and no
     * answer into the other, so a read of the name had to be checked at run time against the table
     * that was supposed to answer it — and the two could only be made to agree by a check, never by
     * construction. Here the name and its answer are put in together or not at all.
     */
    private record Substituted(String name, ReachName reachedAs) {

        /** The binding an expansion made, read as the name the body will read. A binding is reached
         *  where it is bound, so its own name is the whole of it. */
        static Substituted of(Hir.Binder binder) {
            return new Substituted(binder.name(),
                    new ReachName.InScope(new ValueName.Local(binder.name(), binder.id())));
        }

    }

    /**
     * What one expansion rewrites as it copies the callee's body: which parameter reads become which
     * names, which of the body's own bindings become which, and what position the copy carries.
     *
     * <p>The three are fixed for the whole copy and none of them changes as the walk descends, so they
     * travel together rather than as three parameters threaded through every node kind.
     */
    private record Renaming(Map<BindingId, Substituted> subst, Copy copy, SourcePos at,
                            Region over) {

        /** What {@code denotes} stands for in this copy, or null where nothing does — an inner binder
         * that happens to spell a parameter's name is a different binding with a different id, so a
         * reference under it is not in the substitution and is left to {@link #copy}. */
        Substituted substituted(ValueName denotes) {
            return denotes instanceof ValueName.Local local ? subst.get(local.id()) : null;
        }

        /**
         * The position a rebuilt node carries: the call site where the copy is stamped with it, and
         * otherwise the node's own.
         *
         * <p>A prelude helper is copied with the call site stamped over it, so a type error inside its
         * body points at the user's call — {@code filter(xs, x -> x * 2)} — rather than at a line of
         * {@code souther.list} the user never wrote. A module-own helper, and a lambda given to a fn
         * parameter, keep the positions their bodies have.
         * The caller's argument expressions are spliced in separately and keep their own either way.
         */
        SourcePos at(SourcePos own) {
            return at != null ? at : own;
        }

        /** Whether this copy is being stamped with the call site rather than keeping its own. */
        boolean stamps() {
            return at != null;
        }

        /**
         * The stretch of source a rebuilt node is written over: the call's where the copy is stamped
         * with it, and otherwise the node's own.
         *
         * <p>The same rule as {@link #at}, over the same nodes, because a copy stamped with the call
         * site is written where the call is — all of it, not one point of it. A node that took the
         * call's position and kept the callee's extent would say a report is about characters of one
         * file at a line of another.
         */
        Region over(Region own) {
            return at != null ? over : own;
        }
    }

    /**
     * Every binder written inside {@code e}, itself included where {@code e} is one.
     *
     * <p>The node kinds that introduce a binding are named here and nowhere else in this pass, so a
     * kind added later is added once. The walk into the children is {@link Hir#forEachChild}, which
     * is exhaustive over the expression kinds, so a new one cannot be missed.
     */
    private static void eachBinder(Hir.Expr e, java.util.function.Consumer<Hir.Binder> f) {
        switch (e) {
            case Hir.LetIn li -> f.accept(li.binder());
            // What was given to a function parameter is walked here although it is not a slot: it is
            // not code the expansion runs — the body holds it wherever the callee applies it — but a
            // copy of this body has to move its binders along with the rest, or the copy would name
            // a binding that stayed behind.
            case Hir.Expansion ex -> {
                ex.bound().forEach(b -> f.accept(b.binder()));
                ex.given().forEach(g -> eachBinder(g.value(), f));
            }
            case Hir.Block b -> b.params().forEach(f);
            case Hir.IfConstructed ic -> f.accept(ic.binder());
            case Hir.Match m -> {
                for (Hir.Case c : m.cases()) {
                    if (c.binding() != null) {
                        f.accept(c.binding());
                    }
                }
            }
            default -> { }
        }
        forEachChild(e, child -> eachBinder(child, f));
    }

    /**
     * Renaming of the helper's parameter references, matched by {@code BindingId}. An inner binder
     * that happens to spell a parameter's name is a different binding with a different id, so a
     * reference under it names that binding, is not in the substitution, and is left untouched —
     * capture avoidance is the resolver's answer, not a rule of this walk.
     *
     * <p>A callee is renamed as the subexpression it is, so applying a name asks the same question a
     * read of it asks and can only get the same answer: an application {@code f(x)} of a parameter
     * becomes a call to what the parameter was bound to — a function argument, or a value whose type
     * the application is then checked against. There is no separate rule for the callee position to
     * fall out of step with. A spelling that means something else — a builtin, a helper — was
     * resolved to that before renaming and is not a {@code ValueName.Local}, so it is left alone.
     *
     * <p>What each rebuilt node's position becomes is {@link Renaming#at}'s to say, and it says it
     * once for every node kind here.
     */
    private Hir.Expr rename(Hir.Expr e, Renaming renaming) {
        return switch (e) {
            case Hir.Var v -> renameVar(v, renaming);
            // The field's occurrence goes the way the initialiser's does below: it is in the callee's
            // file, so a copy being read against the caller's does not carry it. A report about the
            // read is anchored at the field, so keeping it would send one to the callee's source
            // while everything around it points at the call.
            case Hir.FieldAccess fa -> renaming.stamps()
                    ? Hir.FieldAccess.restamped(rename(fa.target(), renaming), fa.field(),
                            renaming.at(fa.pos()), renaming.over(fa.region()))
                    : fa.withTarget(rename(fa.target(), renaming));
            // the callee is renamed as the expression it is, like every other subexpression. A name
            // applied is an `Hir.Var` held here, so it goes through the arm above and is substituted
            // exactly as a read of it would be.
            //
            // What the author applied is the application's own answer and is not read off that, so
            // it is stamped here beside it: the name travels with the copy and the place it was
            // written at is in the callee's file, which a copy read against another one may not
            // carry — the rule the field read above goes by.
            case Hir.Apply call -> call.with(
                    renaming.stamps()
                            ? call.applied().restamped(renaming.at(call.function().pos()),
                                    renaming.over(call.function().region()))
                            : call.applied(),
                    rename(call.function(), renaming),
                    renameList(call.args(), renaming),
                    renaming.at(call.pos()), renaming.over(call.region()));
            case Hir.Binary bin -> new Hir.Binary(bin.op(), rename(bin.left(), renaming),
                    rename(bin.right(), renaming), bin.origin(), renaming.at(bin.pos()),
                    renaming.over(bin.region()));
            case Hir.Neg neg -> new Hir.Neg(rename(neg.operand(), renaming), renaming.at(neg.pos()),
                    renaming.over(neg.region()));
            case Hir.NewData nd -> {
                List<Hir.FieldInit> inits = new ArrayList<>();
                for (Hir.FieldInit i : nd.inits()) {
                    // A copy stamped with the call site is being read against the caller's file,
                    // so the field's occurrence — which is in the callee's — does not come with it.
                    Hir.Expr filled = rename(i.value(), renaming);
                    inits.add(renaming.stamps()
                            ? new Hir.FieldInit(i.name(), filled, renaming.at(i.pos()))
                            : i.withValue(filled));
                }
                // `..param` copies the renamed binding: a name slot asks what a name asks
                List<Hir.Var> spreads = new ArrayList<>();
                for (Hir.Var s : nd.spreads()) {
                    spreads.add(renameVar(s, renaming));
                }
                yield nd.with(inits, spreads, renaming.at(nd.pos()), renaming.over(nd.region()));
            }
            case Hir.Match m -> {
                List<Hir.Case> cases = new ArrayList<>();
                for (Hir.Case c : m.cases()) {
                    cases.add(new Hir.Case(c.caseTypes(),
                            c.binding() == null ? null : renaming.copy().of(c.binding()),
                            rename(c.body(), renaming),
                            c.unwrapAsserts(), renaming.at(c.pos())));
                }
                yield new Hir.Match(rename(m.scrutinee(), renaming), cases, m.origin(),
                        renaming.at(m.pos()), renaming.over(m.region()));
            }
            case Hir.If iff -> new Hir.If(rename(iff.cond(), renaming), rename(iff.then(), renaming),
                    rename(iff.els(), renaming), iff.origin(), renaming.at(iff.pos()),
                    renaming.over(iff.region()));
            // the success binder has its own BindingId, so a reference to it is not a candidate for
            // substitution, and neither the construction nor the else value can reach it
            case Hir.IfConstructed ic -> new Hir.IfConstructed(
                    rename(ic.construct(), renaming), renaming.copy().of(ic.binder()),
                    rename(ic.then(), renaming),
                    Hir.mapArms(ic.els(), body -> rename(body, renaming)),
                    ic.origin(), renaming.at(ic.pos()), renaming.over(ic.region()));
            case Hir.LetIn li -> {
                Hir.Expr value = rename(li.value(), renaming);
                Hir.Expr body = rename(li.body(), renaming);
                yield new Hir.LetIn(renaming.copy().of(li.binder()), value, li.declaredType(), li.annotated(),
                        li.opens(), body, renaming.at(li.pos()), renaming.over(li.region()));
            }
            // A body already expanded once, being copied into another caller. The signature comes
            // along as it stands: what its variables stand for is settled while each copy is typed,
            // from that copy's own arguments, so two copies decide separately without the variables
            // having to be minted again here.
            case Hir.Expansion ex -> {
                List<Hir.Bound> bound = new ArrayList<>();
                for (Hir.Bound b : ex.bound()) {
                    bound.add(new Hir.Bound(renaming.copy().of(b.binder()), b.declaredType(),
                            rename(b.value(), renaming)));
                }
                List<Hir.Given> given = new ArrayList<>();
                for (Hir.Given g : ex.given()) {
                    given.add(new Hir.Given(g.declaredType(),
                            rename(g.value(), renaming), g.applied(),
                            g.arrivesAs()));
                }
                // What this copy of the expansion wrote, from the one place an owner is moved. Kept
                // as it was, the two copies of one already-expanded body would say they wrote into
                // the same place while their bindings had gone to two.
                yield new Hir.Expansion(ex.callee(),
                        renaming.copy().ownerOf(ex.application()), ex.at(), bound, given,
                        ex.declaredReturn(), rename(ex.body(), renaming),
                        renaming.at(ex.pos()), renaming.over(ex.region()));
            }
            case Hir.ListLit lit -> new Hir.ListLit(renameList(lit.elements(), renaming),
                    lit.origin(), renaming.at(lit.pos()), renaming.over(lit.region()));
            case Hir.RowCollection row -> new Hir.RowCollection(renameList(row.elements(), renaming),
                    row.origin(), renaming.at(row.pos()), renaming.over(row.region()));
            case Hir.Tuple tup -> new Hir.Tuple(renameList(tup.elements(), renaming),
                    renaming.at(tup.pos()), renaming.over(tup.region()));
            case Hir.TupleGet tg -> new Hir.TupleGet(rename(tg.tuple(), renaming), tg.index(), tg.arity(),
                    renaming.at(tg.pos()), renaming.over(tg.region()));
            case Hir.ListComp comp -> new Hir.ListComp(rename(comp.element(), renaming),
                    renameList(comp.guards(), renaming), comp.origin(), renaming.at(comp.pos()),
                    renaming.over(comp.region()));
            case Hir.Block block -> {
                List<Hir.Binder> params = new ArrayList<>();
                for (Hir.Binder p : block.params()) {
                    params.add(renaming.copy().of(p));
                }
                // The rule is the block's own and is not renamed. What a copy is stamped with is
                // where a reader is sent, and which rule this is has to be the same in every copy.
                yield new Hir.Block(params,
                        rename(block.body(), renaming), block.rule(),
                        renaming.at(block.pos()), renaming.over(block.region()));
            }
            case Hir.IntLit lit -> renaming.stamps()
                    ? new Hir.IntLit(lit.value(), renaming.at(lit.pos()), renaming.over(lit.region()))
                    : e;
            case Hir.DecimalLit lit -> renaming.stamps()
                    ? new Hir.DecimalLit(lit.value(), renaming.at(lit.pos()),
                            renaming.over(lit.region()))
                    : e;
            case Hir.StringLit lit -> renaming.stamps()
                    ? new Hir.StringLit(lit.value(), renaming.at(lit.pos()),
                            renaming.over(lit.region()))
                    : e;
            case Hir.BoolLit lit -> renaming.stamps()
                    ? new Hir.BoolLit(lit.value(), renaming.at(lit.pos()),
                            renaming.over(lit.region()))
                    : e;
            // it names nothing, so a substitution has nothing to rewrite in it
            case Hir.Unreachable u -> renaming.stamps()
                    ? new Hir.Unreachable(u.reason(), renaming.at(u.pos()), renaming.over(u.region()))
                    : e;
        };
    }

    /**
     * Where the body this call expands is written, or null when the copy may keep the positions it
     * was written at — which is what both call sites branch on.
     *
     * <p>Read off the body's own position rather than worked out here. Which text the body was
     * parsed from was settled where that text was turned into positions, by the caller
     * that knew what that text was, and a second answer here would be a second authority — which is
     * what this was: it asked whether this compile could quote the place, and before that which
     * module declared the body. Both happened to agree while the only body from elsewhere was the
     * standard library's, and a module of the same project told them apart by failing — its body is
     * in a file the reader holds, and it was being treated as shipped source, reported at the call
     * with the caret sized for a construction three files away.
     *
     * <p>What this is still the first to know is the name a reader here reaches the body by. A parse
     * of a published module knows the module and not which of its declarations a caller will land
     * on, so the provenance is refined with the call's name and its arm is kept.
     *
     * <p>A lambda is not asked. It is not a declaration and has no source of its own: one the caller
     * wrote is in the caller's file, and one written in a body from elsewhere was given the call site
     * when that body was copied. Either way its positions are the ones already decided for the body
     * holding it, and asking again would answer about the wrong thing.
     *
     * <p>Nor is a declaration this compile can show. A body read from a file the reader holds keeps
     * the positions it was written at, and so does one read from a text the caller handed over — the
     * caller can put those numbers in front of somebody. Only a text this compile cannot show has a
     * declaration to name instead, which is the question asked here.
     */
    private DeclaringCode whereTheBodyIs(Hir.Apply call, Hir.FnDef helper) {
        if (call.answered() == null
                || call.answered().denotes() instanceof ValueName.Local
                || helper.pos() == null
                || !(helper.pos().quotedFrom() instanceof QuotedFrom.TextItCannotShow)) {
            return null;
        }
        // Reached rather than declared: `List.map` is what a reader here writes and what a report
        // about it should quote, and which module declares it is the other half, read off the
        // declaration rather than split back out of the name.
        return helper.pos().reachedBy(call.answered().reaches());
    }

    /** {@code call}'s own place, said to stand in for a body written out of sight — what a copy that
     *  may not keep its own positions is given, ends and all, so no part of it claims to be written
     *  where the rest of it only stands. */
    private static Region standingIn(Region call, DeclaringCode declaring) {
        if (call == null || call.start() == null || call.end() == null) {
            return call;
        }
        return new Region(call.start().standingInFor(declaring),
                call.end().standingInFor(declaring));
    }

    /**
     * A name in the copy, reading whatever it reads here.
     *
     * <p>Three cases, and they differ in whether the characters this name stands at still spell it.
     * A parameter read becomes a read of the binding this expansion made, which is this pass's
     * however much it reads like the parameter — the same thing {@link Copy#of(Hir.Binder)} says
     * about the binder. A copy stamped with the call site is read against the caller's file, and the
     * occurrence it was written at is in the callee's. Neither is written where it stands, so
     * neither keeps an occurrence; both keep somewhere to complain and the stretch of source the
     * name they replaced was read over.
     *
     * <p>The third is an ordinary name in a body keeping its own positions, and it keeps its
     * occurrence with them. Rebuilding one from {@link Hir.Var#name()} and a position — which is
     * what this did — takes the canonical name and measures it at the anchor, so a decomposed
     * spelling comes out of an expansion a unit short and a qualified one written over a line break
     * comes out as far as its spelling is long.
     */
    private Hir.Var renameVar(Hir.Var name, Renaming renaming) {
        if (!(name instanceof Hir.Var.Denoting v)) {
            return name;   // it names nothing, so there is nothing to rename it to
        }
        Substituted stands = renaming.substituted(v.denotes());
        if (stands != null) {
            // A copy of the body reads the same reference the source wrote. Which copy it is is the
            // expansion around it, not the reference.
            return Hir.Var.respelled(stands.name(), stands.reachedAs(), v.origin(),
                    renaming.at(v.pos()), renaming.over(v.region()));
        }
        ReachName reaches = renaming.copy().of(v.reachedAs());
        if (renaming.stamps()) {
            return Hir.Var.respelled(v.name(), reaches, v.origin(),
                    renaming.at(v.pos()), renaming.over(v.region()));
        }
        return new Hir.Var.Denoting(v.written(), reaches, v.origin(), v.region());
    }

    private List<Hir.Expr> renameList(List<Hir.Expr> es, Renaming renaming) {
        List<Hir.Expr> out = new ArrayList<>();
        for (Hir.Expr e : es) {
            out.add(rename(e, renaming));
        }
        return out;
    }

    /**
     * The value a spread copies, or null where it copies something else — a parameter, a binding, or
     * a name that merely shares a value's spelling.
     *
     * <p>The spread carries what it resolved to, so this asks that rather than matching the spelling
     * against the module's definitions: a binding in force wins over a declaration, and a spread is
     * no exception.
     */
    private Hir.FnDef valueSpread(Hir.Var name) {
        if (!(name instanceof Hir.Var.Denoting spread)
                || !(spread.denotes() instanceof ValueName.Helper)) {
            return null;
        }
        // by the name it is reached by here, which for another module's value is the qualified one.
        // Asked of what the name reaches and not of what this module has as its own fns: a published
        // value is substituted where it is spread exactly as one declared here is, and it is not one
        // of this module's fns — nothing emits a value.
        ReachName.Declaration reached = spread.reachesADeclaration();
        Hir.FnDef value = reached == null ? null : table.reached(reached);
        return value == null || !value.params().isEmpty() || value.body() == null
                || graph.recurses(reached) ? null : value;
    }

    /**
     * Whether anything in {@code e} reads {@code binding}.
     *
     * <p>It asks what each name denotes, so a binder inside {@code e} that spells its name the same
     * is another binding and answers no. Nothing here tracks scope, because the tree already carries
     * the answer scope decided: that is what resolution is for.
     */
    private static boolean references(Hir.Expr e, BindingId binding) {
        ValueName denotes = switch (e) {
            case Hir.Var.Denoting v -> v.denotes();
            case Hir.Apply c when c.answered() != null -> c.answered().denotes();
            default -> null;
        };
        if (denotes instanceof ValueName.Local local && local.id().equals(binding)) {
            return true;
        }
        boolean[] found = {false};
        forEachChild(e, child -> found[0] |= references(child, binding));
        return found[0];
    }


    /**
     * The helpers of {@code table} that {@code e} calls, added to {@code out}.
     *
     * <p>Static because the value-cycle check asks it of a table it builds for itself, before an
     * inliner exists. One walk either way: an edge of this graph is what it is, and a reader that
     * counted a different set of them would be reading a different graph.
     */
    static void helperCallsIn(Stdlib stdlib, Hir.Expr e,
                              Map<ReachName.Declaration, HelperEntry> table,
                              Set<ReachName.Declaration> out) {
        // Applying a function-typed parameter, or a binding holding a function, is not a call to
        // whatever else bears that name. The call carries what it resolved to, so it is asked rather
        // than matched against the helper table — a parameter named like a helper was reaching the
        // graph as a call to that helper, which made `let f (g: (Int) -> Int) = g(1)` recursive.
        if (e instanceof Hir.Apply call) {
            // A sugar is written out before inlining, so a body that folds reaches the recursive
            // `foldFrom` — recursion classification and what a module has to emit must see that.
            ReachName.Declaration fn = calledHelper(stdlib, call);
            if (fn != null && table.containsKey(fn)) {
                out.add(fn);
            }
        }
        forEachChild(e, c -> helperCallsIn(stdlib, c, table, out));
    }

    /** Applies {@code f} to every direct subexpression of {@code e}; the one exhaustive walk
     * lives on the AST, so a node kind added later cannot be skipped here unnoticed. */
    private static void forEachChild(Hir.Expr e, java.util.function.Consumer<Hir.Expr> f) {
        Hir.forEachChild(e, f);
    }
}
