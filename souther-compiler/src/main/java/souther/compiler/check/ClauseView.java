package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.semantics.ConditionJoin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * One clause the author wrote, as the world a reading is made in holds it.
 *
 * <p>A reading asked what one conjunct was holding is a reading of the declaration under a rule set
 * the author did not write ({@link InvariantChecker.Reach#withoutParts}), and every question about
 * that world has to be answered out of that rule set. This is the one answer to what that rule set
 * is for one clause: which of its parts are rules here, and where under the tree they are.
 *
 * <p><b>Handed out and never asked for.</b> Which parts a world holds is decided where the world is
 * ({@link PartsLeftOut#viewOf}), and nothing anywhere is given a way to ask about one part on its
 * own. A reading holds this and reads {@link #present} or walks past what {@link #omits} says is not
 * here; a reading that only had a rule to consult is a reading that can be written without
 * consulting it, which is what left the connectives composed over conjuncts their world had taken
 * away while every walk beside them left the same conjuncts out.
 *
 * <p><b>A part outside this world is not a part nothing could read.</b> The two arrive at the same
 * place in every state — nothing is said about the positions it names — and they are different
 * facts about the reading, which is why nothing here is recorded as read: a subtree this omits is
 * never handed to {@link ClauseReading#whole}, never told to the walk that collects parts, and never
 * one an account can name. What a reading of this world says about the conjunct is nothing, and what
 * it says about how far it got is that the conjunct was not among its rules.
 *
 * <p><b>The parts are the author's and this only chooses among them.</b> Which parts a clause has
 * was settled where it was split ({@link Clauses.StatedPart}), so nothing here recognises a
 * connective: a node is out of this world by being one of those parts and by nothing else, and a
 * conjunction of them is out where all of them are.
 */
final class ClauseView {

    /** Every part the author wrote, whether or not this world holds it. */
    private final List<Clauses.StatedPart> authored;
    /** The ones it holds, in the order they were written. */
    private final List<Clauses.StatedPart> present;
    /** And the roots of the ones it does not, by identity: two conjuncts spelled alike are two
     *  parts, and a set comparing them by what they say would leave out both. */
    private final Set<Core> omitted;

    private ClauseView(List<Clauses.StatedPart> authored, List<Clauses.StatedPart> present,
                       Set<Core> omitted) {
        this.authored = authored;
        this.present = present;
        this.omitted = omitted;
    }

    /**
     * The clause whole, which is what the author wrote and what almost every reading is made in.
     *
     * <p>Nothing is worked out: a world holding every part omits no root, and the parts it holds are
     * the parts there are. A clause is viewed for every clause of every value, so what a reading of
     * the declaration as it stands pays to be told that is nothing.
     */
    static ClauseView whole(List<Clauses.StatedPart> authored) {
        return new ClauseView(authored, authored, Set.of());
    }

    /** The world the author wrote, told without the parts of any clause — see {@link #asWritten}. */
    private static final ClauseView AS_WRITTEN = new ClauseView(null, null, Set.of());

    /**
     * The world the author wrote, for a reading with no clause's parts in hand.
     *
     * <p>A form nobody split into parts is read through this, and so is a question about a tree
     * asked of the shape alone. Nothing is left out, so nothing is walked past — and there are no
     * parts here to present, which {@link #present} says by refusing rather than by answering that
     * a world holding everything holds nothing.
     */
    static ClauseView asWritten() {
        return AS_WRITTEN;
    }

    /** The same clause with {@code omitted} left out, which is how a counterfactual holds it. */
    static ClauseView without(List<Clauses.StatedPart> authored, Set<Core> omitted) {
        List<Clauses.StatedPart> here = new ArrayList<>(authored.size());
        for (Clauses.StatedPart each : authored) {
            if (!omitted.contains(each.expr())) {
                here.add(each);
            }
        }
        return new ClauseView(authored, Collections.unmodifiableList(here),
                Collections.unmodifiableSet(omitted));
    }

    /** An identity set for {@link #without}, which is what a world's omissions are told apart by. */
    static Set<Core> roots() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /** Every part the author wrote, for the readers that are about the clause rather than about
     *  what any world holds of it. */
    List<Clauses.StatedPart> authored() {
        if (authored == null) {
            throw new IllegalStateException(
                    "this world was not made from a clause's parts and has none to name");
        }
        return authored;
    }

    /** The parts this world holds, which is what a reading of it reads. Empty where the world holds
     *  none of the clause, which is a clause that is no rule of it. */
    List<Clauses.StatedPart> present() {
        if (present == null) {
            throw new IllegalStateException(
                    "this world was not made from a clause's parts and has none to present");
        }
        return present;
    }

    /** Whether every part of the clause is a rule of this world, which almost every reading is made
     *  in. Asked where working out the shape of a clause would be the cost of finding out. */
    boolean omitsNothing() {
        return omitted.isEmpty();
    }

    /**
     * Whether no rule of this world is anywhere under {@code shape}.
     *
     * <p>A part by itself, and a conjunction every part of which is out. A choice is one part
     * however many comparisons stand between its brackets — an author writes an alternative and
     * cannot write half of one — so it is out only by being the part that is out.
     */
    boolean omits(ClauseExpr shape) {
        if (omitted.isEmpty()) {
            return false;
        }
        for (Core each : shape.spelled()) {
            if (omitted.contains(each)) {
                return true;
            }
        }
        return switch (shape) {
            case ClauseExpr.Scoped it -> omits(it.body());
            case ClauseExpr.Joined it ->
                    it.how() == ConditionJoin.BOTH && it.positive()
                            && omits(it.left()) && omits(it.right());
            case ClauseExpr.Leaf _ -> false;
        };
    }

    @Override
    public String toString() {
        return omitted.isEmpty() ? "the whole clause" : "without " + omitted.size() + " of it";
    }
}
