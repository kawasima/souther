package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingOwner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A module's clauses as the discharge analysis reads them — the one thing that hands a reader a
 * clause to classify.
 *
 * <p>Reading a clause is four facts, and every one of them has been got wrong by somebody deriving
 * it for themselves: <em>what the author wrote</em>, <em>where they wrote it</em>, <em>which
 * representation the analysis reads it in</em>, and <em>whether all of it was read</em>. They are not
 * independent — the position belongs to the written form, the analysis wants the expanded one, and an
 * expansion carries the positions of the body it copies in — so a reader holding some of them and
 * working out the rest is a reader that can hold a matched set or a mismatched one with nothing to
 * tell them apart. Both kinds of clause had a reader of their own doing exactly that, and they
 * disagreed: the data reader placed its answers on what was written and the rule reader placed them
 * on what it had expanded.
 *
 * <p>So the facts arrive together or not at all. This owns the expansion and never hands it out, and
 * what it gives back is a {@link ClauseReading} — a written conjunct that knows where it is and what
 * it comes to. There is no argument anywhere below for a reader to pass a position in, which is what
 * makes the wrong position unwritable rather than merely wrong.
 *
 * <p>What is <em>not</em> here is the reading the check itself does of a whole clause
 * ({@link ClauseHelpers}). That answers a different question — what a
 * construction owes — and is read against the values a construction hands over rather than placed in
 * front of an author.
 */
public final class ClausesForDischarge {

    private final Hir.Module settled;
    /** Kept and not handed out. A caller given the expansion could expand a clause before splitting
     *  or placing it, which is the one order that loses both the author's units and their
     *  positions. */
    private final HelperInliner expansion;

    private ClausesForDischarge(Hir.Module settled, HelperInliner expansion) {
        this.settled = settled;
        this.expansion = expansion;
    }

    /**
     * The clauses {@code expandable} declares, ready to be read.
     *
     * <p>{@code published} is what the modules this one imports offer it: a clause names what is in
     * scope where it is written, and an imported definition is in scope there as it is in a body.
     */
    public static ClausesForDischarge of(Expandable expandable, Symbols symbols,
                                         PublishedDeclarations declarations, DeclarationKinds kinds,
                                         Map<String, Hir.FnDef> published) {
        Hir.Module settled =
                ClauseHelpers.settled(expandable.module(), symbols, declarations, kinds);
        return new ClausesForDischarge(settled, HelperInliner.forHelpers(settled.name(),
                HelperInliner.helpersOf(settled), published, InliningPolicy.DISCHARGE,
                symbols.library()));
    }

    /** The declarations that state an {@code invariant}, as their authors wrote them. */
    public List<Hir.Data> declarationsThatState() {
        List<Hir.Data> out = new ArrayList<>();
        for (Hir.Def def : settled.defs()) {
            if (def instanceof Hir.Data data && !data.invariants().isEmpty()) {
                out.add(data);
            }
        }
        return out;
    }

    /** The behaviors that state an {@code ensures}, as their authors wrote them, by the name each is
     *  declared under. */
    public Map<String, Hir.SpecBehavior> behaviorsThatState() {
        Map<String, Hir.SpecBehavior> out = new LinkedHashMap<>();
        for (Hir.BehaviorDef behavior : settled.behaviors()) {
            if (behavior instanceof Hir.SpecBehavior spec && !spec.ensures().isEmpty()) {
                out.put(spec.name(), spec);
            }
        }
        return out;
    }

    /**
     * A clause as a reader of it gets it: one reading per conjunct the author wrote, each knowing
     * where it is written and what it comes to for the analysis.
     *
     * <p>Split first, placed from what was written, expanded last. That order is the whole of the
     * correctness and it is said here once — an expansion carries the positions of the body it copies
     * in and splices in whatever that body is made of, so a clause expanded any earlier is placed
     * inside the helper it names and split where that helper's author put an {@code &&}.
     *
     * @param owner what the names in an expansion belong to — a declaration or a signature
     */
    public List<ClauseReading> conjunctsOf(Hir.Expr written, BindingOwner owner) {
        List<ClauseReading> out = new ArrayList<>();
        for (ClauseHelpers.AuthoredPart each : ClauseHelpers.conjunctsOf(written)) {
            Expansion<Hir.Expr> read = expansion.expanding(each.written(), owner);
            out.add(new ClauseReading(each, read.value(), CallsLeftStanding.of(read.standing())));
        }
        return out;
    }

    /**
     * One conjunct of a clause, as written and as read.
     *
     * <p>Where it is comes from what was written and from nothing else, so it is asked of this rather
     * than carried beside it: a position that can be passed is a position that can be passed wrongly,
     * and the one thing every reader of this got wrong was which tree they took it from.
     *
     * <p>The part and not the tree alone, so that the place this conjunct holds among the parts of
     * its clause travels with it. That place was assigned where the clause was split, two lines up;
     * dropped here, the readers below counted the conjuncts again to get it back, and a count made
     * where a reading stands is a count that moves with what that reading managed.
     *
     * @param part     the conjunct as the split issued it, which is what a caller holding the rule
     *                 asks for the name of this part ({@link ClauseHelpers.AuthoredPart#idFor})
     * @param standing which calls the expansion that produced {@code read} left standing — the
     *                 fourth of the facts this file exists to hand over together, and the one a
     *                 reader looking at {@code read} alone cannot recover
     */
    public record ClauseReading(ClauseHelpers.AuthoredPart part, Hir.Expr read,
                                CallsLeftStanding standing) {

        /** The tree and what its expansion left standing, which is what a reading of it takes. */
        ClauseAsExpanded asExpanded() {
            return new ClauseAsExpanded(read, standing);
        }

        /** The conjunct as the author wrote it, before anything was expanded into it. */
        public Hir.Expr written() {
            return part.written();
        }

        /** Where the author wrote it — the earliest position anything written carries. */
        public SourcePos at() {
            return ClauseHelpers.beginsAt(written());
        }

        /** The stretch of source it is written over, for a reader holding it against the clause it
         *  came from. */
        public Region region() {
            return written().region();
        }
    }
}
