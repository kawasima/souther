package souther.compiler.sites;

import souther.compiler.ast.Hir;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.source.SourceId;
import souther.compiler.types.SourceConstructOrigin;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every expression occurrence one revision of a module's source was written with.
 *
 * <p>What an editor asks about is an occurrence — this field read, that call — and what it has to
 * point with is a position. The two are joined here and nowhere else: the walk that mints a {@link
 * SourceSiteId} is the walk that checks the rule the id rests on, so a reader that holds one holds
 * an occurrence that was found to be the only one written over those characters.
 *
 * <p>Taken from the resolved module and not from anything below it. A pass carries an extent across
 * on purpose and helper expansion stamps a call site's extent over a copied body, so after one has
 * run an extent no longer says which occurrence it is (ADR-0102). Here nothing has run: what a name
 * means is settled, which is what tells {@code x.field} from {@code Module.name}, and no body has
 * been copied into another.
 *
 * <p>Expressions only. A type name is written in its own positions and answered about by its own
 * question ({@code Names.DenotedAt}), and nothing here would add to that.
 *
 * <p>An answer of the query graph, so it is a value and two of them are equal when they hold the
 * same sites — an edit that puts the source back where it was costs nothing downstream.
 */
public final class AuthoredSites {

    /** What the walk came to. Two refusals rather than one, because they are two mistakes: one says
     *  the tree contradicts what an extent is worth, the other that a region contradicts what a
     *  region is. */
    public sealed interface Census {

        /** Every occurrence, each identified by the characters it was written over. */
        record Identified(AuthoredSites sites) implements Census {}

        /** Two expressions written over one stretch of source, so neither can be named by it. */
        record TwoOccurrencesOneExtent(Region extent) implements Census {}

        /** A region that begins in one source and ends in another, which is no stretch of text. */
        record OneRegionTwoSources(Region extent) implements Census {}
    }

    /**
     * The occurrences, each under the characters it was written over.
     *
     * <p>The expression is kept beside the identity rather than looked up again. A second walk to
     * find the node at an extent would be a second answer about which occurrence an extent is, from
     * a walk that has not checked what this one checked — and the check is the whole of what an
     * identity here is worth.
     */
    private final Map<Region, Hir.Expr> byExtent;

    private AuthoredSites(Map<Region, Hir.Expr> byExtent) {
        this.byExtent = Map.copyOf(byExtent);
    }

    /**
     * What one walk of a module's source found: its occurrences, and where each construct it wrote
     * stands.
     *
     * <p>Two answers and one walk, which is the whole of why they are made together. A module wrote
     * what it wrote once, and two walks of it would be two answers to that — agreeing until the day
     * one of them was taught something the other was not.
     *
     * <p>The forks are answered whether or not the occurrences could be told apart. Two expressions
     * written over one stretch of source is a fact about extents; which fork is which is settled by
     * an identity the extents play no part in.
     */
    public record Walked(Census census, WrittenForks forks) {}

    /** {@code module} walked, once. */
    public static Walked walk(Hir.Module module) {
        Walk walk = new Walk();
        walk.module(module);
        return new Walked(walk.refusal != null ? walk.refusal
                : new Census.Identified(new AuthoredSites(walk.byExtent)),
                new WrittenForks(walk.byOrigin));
    }

    /** The occurrences of {@code module}, or why they could not be told apart. */
    public static Census of(Hir.Module module) {
        return walk(module).census();
    }

    /** How many occurrences were found. What a measurement reads, and what says a walk reached a
     *  document at all. */
    public int count() {
        return byExtent.size();
    }

    /**
     * The occurrence written over exactly {@code extent}, or null where none was.
     *
     * <p>An exact lookup and not a containment one. A caller holding an extent has it from the tree
     * that wrote it, and asking which occurrence covers a cursor is a different question with a
     * different answer — the innermost of several, where this has at most one. Keeping them apart is
     * also what keeps {@code Region.encloses} out of here: it is true of a null region and of
     * everything, which is the answer a lookup must never give.
     */
    SourceSiteId site(Region extent) {
        return extent != null && byExtent.containsKey(extent) ? new SourceSiteId(extent) : null;
    }

    /**
     * What was written at {@code site}.
     *
     * <p>Takes the identity and not the characters. An extent is how a site is found and an id is
     * that it was: only this walk mints one, so a reader holding one is holding an occurrence this
     * revision was found to have, and there is no way to ask about a stretch of source that is not
     * one.
     */
    Hir.Expr written(SourceSiteId site) {
        return site == null ? null : byExtent.get(site.extent());
    }

    /**
     * The narrowest occurrence written over {@code at}, or null where none is.
     *
     * <p>A different question from {@link #site}, and kept apart from it by name as well as by
     * signature. That one is handed an extent by whoever read it off the tree and answers whether it
     * is an occurrence; this one is handed a place somebody's cursor is at and answers which
     * occurrence they are in. Several are, one inside another, and the narrowest is the one they are
     * looking at.
     *
     * <p>The containment is written out rather than taken from {@code Region.encloses}, which is
     * true of a region that is nowhere and so true of everything — an answer a lookup must never
     * give.
     */
    SourceSiteId innermostContaining(SourcePos at) {
        Region narrowest = null;
        for (Region extent : byExtent.keySet()) {
            if (contains(extent, at) && (narrowest == null || contains(narrowest, extent))) {
                narrowest = extent;
            }
        }
        return narrowest == null ? null : new SourceSiteId(narrowest);
    }

    /** Whether {@code at} is within {@code extent} — from its start inclusive to its end exclusive,
     *  which is what a region is, and in the text the region is in. */
    private static boolean contains(Region extent, SourcePos at) {
        return extent.start().isInTheSameTextAs(at)
                && !at.isBefore(extent.start()) && at.isBefore(extent.end());
    }

    /** Whether {@code outer} covers the whole of {@code inner}, ends allowed to meet. */
    private static boolean contains(Region outer, Region inner) {
        return outer.start().isInTheSameTextAs(inner.start())
                && !inner.start().isBefore(outer.start()) && !outer.end().isBefore(inner.end());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthoredSites sites && byExtent.equals(sites.byExtent);
    }

    @Override
    public int hashCode() {
        return byExtent.hashCode();
    }

    @Override
    public String toString() {
        return byExtent.size() + " authored sites";
    }

    /**
     * The walk, and what it refuses.
     *
     * <p>It keeps the first refusal rather than gathering them: what a second one would say is that
     * the same rule is broken again, and the revision is already not one an occurrence can be named
     * in. It goes on walking all the same, because the forks under a stretch of source two
     * expressions were written over are still forks the module wrote.
     */
    private static final class Walk {

        private final Map<Region, Hir.Expr> byExtent = new LinkedHashMap<>();
        /** Where each fork this module wrote stands, under the identity a copy cannot change.
         *  Filled beside {@link #byExtent} and never instead of it: they answer two questions about
         *  one walk, and a second walk would be a second answer to the first. */
        private final Map<SourceConstructOrigin, SourcePos> byOrigin = new LinkedHashMap<>();
        private Census refusal;

        /**
         * Files where a fork the source wrote is.
         *
         * <p>Called beside {@code take} by the kinds a body takes an arm of, so that a kind given
         * arms later is a kind whose neighbours here are visibly doing this. A kind that carries an
         * origin and is no fork is left out and says so where it is walked. The first stands: a
         * fork is written once, and a tree holding a second node under one origin is a copy, which
         * the module that wrote it does not have.
         */
        private void wrote(SourceConstructOrigin origin, SourcePos at) {
            if (origin != null && origin.isWritten() && at != null) {
                byOrigin.putIfAbsent(origin, at);
            }
        }

        void module(Hir.Module module) {
            if (module == null) {
                return;
            }
            for (Hir.Def def : module.defs()) {
                def(def);
            }
            for (Hir.BehaviorDef behavior : module.behaviors()) {
                behavior(behavior);
            }
            for (Hir.FnDef fn : module.fns()) {
                fn(fn);
            }
            for (Hir.FnDef fn : module.takenOn()) {
                fn(fn);
            }
            for (Hir.Example example : module.examples()) {
                example(example);
            }
            for (Hir.Fake fake : module.fakes()) {
                fake(fake);
            }
        }

        private void def(Hir.Def def) {
            switch (def) {
                case Hir.Data data -> {
                    for (Hir.InvariantClause clause : data.invariants()) {
                        expr(clause.expr());
                    }
                }
                case Hir.SumData _, Hir.UnitData _ -> { }
            }
        }

        private void behavior(Hir.BehaviorDef behavior) {
            switch (behavior) {
                case Hir.SpecBehavior spec -> {
                    for (Hir.EnsuresClause clause : spec.ensures()) {
                        for (Hir.EnsuresArm arm : clause.arms()) {
                            expr(arm.expr());
                        }
                    }
                }
                // The stages of a composition are names, and each is an occurrence like any other.
                case Hir.PipeBehavior pipe -> {
                    for (Hir.Var stage : pipe.stages()) {
                        expr(stage);
                    }
                }
            }
        }

        private void fn(Hir.FnDef fn) {
            switch (fn.body()) {
                case Hir.FnBody.Written written -> expr(written.expr());
                // A kernel the backend supplies. There is no body to have written anything in.
                case Hir.FnBody.Intrinsic _ -> { }
            }
        }

        private void example(Hir.Example example) {
            for (Hir.ExampleRow row : example.rows()) {
                for (Hir.Expr input : row.inputs()) {
                    expr(input);
                }
                for (Hir.With with : row.withs()) {
                    expr(with.value());
                }
                switch (row.expected()) {
                    case Hir.Expected.Asserted(Hir.Expr answer) -> expr(answer);
                    // A row whose answer is owed and one whose answer did not parse write nothing
                    // here, so there is nothing written for a site to be at.
                    case Hir.Expected.Unanswered _, Hir.Expected.Unwritten _ -> { }
                }
            }
        }

        private void fake(Hir.Fake fake) {
            expr(fake.target());
            for (Hir.FakeRow row : fake.rows()) {
                switch (row.matched()) {
                    case Hir.Matched.Arguments(List<Hir.Expr> inputs) -> inputs.forEach(this::expr);
                    // A row that answers for anything writes no arguments, so there is nothing
                    // written for a site to be at.
                    case Hir.Matched.Anything _ -> { }
                }
                expr(row.output());
            }
        }

        /**
         * One expression, and everything written inside it.
         *
         * <p>Every kind says what it takes, so a kind added to the language is a compile error here
         * rather than an occurrence that quietly has no site. The one that takes nothing is the
         * expansion: it is a copy of a body written somewhere else, placed here by a pass, and it
         * wears the extent of the call it was placed in — so neither it nor anything under it is an
         * occurrence this source wrote. Nothing puts one in a resolved module today, and this does
         * not rest on that.
         */
        private void expr(Hir.Expr e) {
            if (e == null) {
                return;
            }
            switch (e) {
                case Hir.IntLit _, Hir.DecimalLit _, Hir.StringLit _, Hir.BoolLit _, Hir.Var _,
                     Hir.Unreachable _ -> take(e);
                case Hir.Neg neg -> {
                    take(e);
                    expr(neg.operand());
                }
                case Hir.FieldAccess access -> {
                    take(e);
                    expr(access.target());
                }
                case Hir.Apply apply -> {
                    take(e);
                    expr(apply.function());
                    each(apply.args());
                }
                case Hir.Binary binary -> {
                    take(e);
                    expr(binary.left());
                    expr(binary.right());
                }
                case Hir.NewData data -> {
                    take(e);
                    for (Hir.FieldInit init : data.inits()) {
                        expr(init.value());
                    }
                    for (Hir.Var spread : data.spreads()) {
                        expr(spread);
                    }
                }
                case Hir.Match match -> {
                    take(e);
                    wrote(match.origin(), match.pos());
                    expr(match.scrutinee());
                    for (Hir.Case one : match.cases()) {
                        expr(one.body());
                    }
                }
                case Hir.If branch -> {
                    take(e);
                    wrote(branch.origin(), branch.pos());
                    expr(branch.cond());
                    expr(branch.then());
                    expr(branch.els());
                }
                case Hir.IfConstructed attempt -> {
                    take(e);
                    wrote(attempt.origin(), attempt.pos());
                    expr(attempt.construct());
                    expr(attempt.then());
                    for (Hir.ElseArm arm : attempt.els()) {
                        expr(arm.body());
                    }
                }
                // A collection literal carries an origin and is no fork: nothing takes an arm of
                // one, so there is no arm of it for a report to be about.
                case Hir.ListLit list -> {
                    take(e);
                    each(list.elements());
                }
                case Hir.RowCollection collection -> {
                    take(e);
                    each(collection.elements());
                }
                case Hir.Tuple tuple -> {
                    take(e);
                    each(tuple.elements());
                }
                case Hir.TupleGet get -> {
                    take(e);
                    expr(get.tuple());
                }
                case Hir.ListComp comp -> {
                    take(e);
                    // The comprehension itself is not a fork; each of its guards lowers to one, and
                    // where that fork is written is where the guard is. Asked of the comprehension,
                    // which is what numbers them, so this and the lowering cannot come to number
                    // the guards differently.
                    for (int guard = 0; guard < comp.guards().size(); guard++) {
                        wrote(comp.forkOfGuard(guard), comp.guards().get(guard).pos());
                    }
                    expr(comp.element());
                    each(comp.guards());
                }
                case Hir.LetIn let -> {
                    take(e);
                    expr(let.value());
                    expr(let.body());
                }
                case Hir.Block block -> {
                    take(e);
                    expr(block.body());
                }
                case Hir.Expansion _ -> { }
            }
        }

        private void each(java.util.List<Hir.Expr> exprs) {
            for (Hir.Expr e : exprs) {
                expr(e);
            }
        }

        /**
         * Records the occurrence written over {@code extent}, where the author wrote one there.
         *
         * <p>Three answers and not two. A node no one wrote has no extent, and a copy wears an extent
         * whose characters spell something else — {@code SourcePos.wasCopiedHere} is what tells the
         * second from a written one, the file being the same either way. Both are simply not
         * occurrences of this source. What is refused is a region that names two sources: a region is
         * a stretch of one text, and one that is not says nothing about how far anything runs.
         */
        private void take(Hir.Expr written) {
            // The first refusal is the one reported, and the walk goes on: what a second would say
            // is that the same rule is broken again, while the forks under it are still forks this
            // module wrote and are still to be found.
            if (refusal != null) {
                return;
            }
            Region extent = written.region();
            if (extent == null) {
                return;
            }
            if (extent.start().wasCopiedHere() || extent.end().wasCopiedHere()) {
                return;
            }
            SourceId opens = fileOf(extent.start());
            SourceId closes = fileOf(extent.end());
            if (opens == null || closes == null) {
                return;
            }
            if (!opens.equals(closes)) {
                refusal = new Census.OneRegionTwoSources(extent);
                return;
            }
            if (byExtent.putIfAbsent(extent, written) != null) {
                refusal = new Census.TwoOccurrencesOneExtent(extent);
            }
        }

        /** The source a position names, or null where it names none — a place a report can quote
         *  without being able to open it is not somewhere an author wrote an expression. */
        private static SourceId fileOf(SourcePos at) {
            return at.quotedFrom() instanceof QuotedFrom.ASourceThisCompileHolds(SourceId file)
                    ? file : null;
        }
    }
}
