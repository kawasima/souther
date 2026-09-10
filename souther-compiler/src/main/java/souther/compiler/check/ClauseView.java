package souther.compiler.check;

import souther.compiler.semantics.ConditionJoin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
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

    /** Every part the author wrote, kept for the one thing they answer that this world does not:
     *  which rule of the model the clause is. Not shown, for the reason {@link #present} gives. */
    private final List<Clauses.StatedPart> authored;
    /** The parts this world holds, in the order they were written. */
    private final List<Clauses.StatedPart> present;
    /** And where in the clause the ones it does not are: two conjuncts spelled alike stand at two
     *  places, and a set comparing them by what they say would leave out both. */
    private final Set<ClauseOccurrence> omitted;

    private ClauseView(List<Clauses.StatedPart> authored, List<Clauses.StatedPart> present,
                       Set<ClauseOccurrence> omitted) {
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
        List<Clauses.StatedPart> here = List.copyOf(authored);
        return new ClauseView(here, here, Set.of());
    }

    /** The world the author wrote, of a form nobody split into parts — see {@link #asWritten}. */
    private static final ClauseView AS_WRITTEN = whole(List.of());

    /**
     * The world the author wrote, for a reading with no clause's parts in hand.
     *
     * <p>A form nobody split into parts is read through this, and so is a question about a tree
     * asked of the shape alone. Nothing is left out, so nothing is walked past, and the parts here
     * are none because none were written — which is not the same fact as a world holding none of a
     * clause, and is told from it where it matters: a clause reaching a value is written in parts
     * ({@code InvariantChecker.Written}), so one made from this is refused there rather than read
     * as a clause every world has taken away.
     */
    static ClauseView asWritten() {
        return AS_WRITTEN;
    }

    /**
     * The same clause with the parts {@code left} names left out, which is how a counterfactual
     * holds it.
     *
     * <p>Told which parts by their names and never by their trees, which is what a world is asked
     * with ({@link PartsLeftOut}); where each of them stands is worked out here, off the shape the
     * clause was read into, so that what the walk matches on is a place in the clause. Handed a set
     * of nodes instead, a caller could build one that tells two conjuncts spelled alike apart by
     * what they say, and both would go — and a walk over a tree built for it by a substitution
     * would match none of them, which is a world holding rules the caller took away.
     */
    static ClauseView without(List<Clauses.StatedPart> authored,
                              Set<PartId<RuleRef.Invariant>> left) {
        List<Clauses.StatedPart> here = new ArrayList<>(authored.size());
        Set<ClauseOccurrence> omitted = new LinkedHashSet<>();
        for (Clauses.StatedPart each : authored) {
            if (left.contains(each.id())) {
                omitted.add(each.of().at());
            } else {
                here.add(each);
            }
        }
        return omitted.isEmpty() ? whole(authored)
                : new ClauseView(List.copyOf(authored), Collections.unmodifiableList(here),
                        omitted);
    }

    /**
     * Which rule of the model the clause is, read off the parts the author wrote it in.
     *
     * <p>The one thing the author's parts answer that this world's do not. Every part of a clause is
     * a part of that clause, so the rule is the parts' answer and not a second thing anybody
     * carries — held beside them, a value about one rule could be built about two
     * ({@code OneWayFromAStateToTheRuleItIsAboutTest}). And the author's rather than this world's,
     * since which rule a clause is does not turn on what a counterfactual left out and a world
     * holding none of the clause would have no part left to answer from.
     */
    RuleRef.Invariant rule() {
        return authored.get(0).id().rule();
    }

    /**
     * The parts this world holds, which is what a reading of it reads. Empty where the world holds
     * none of the clause, which is a clause that is no rule of it.
     *
     * <p>The only parts this shows. What the author wrote is the clause's and belongs where a
     * clause is named — offered here beside these, a reader holding a world would have both lists
     * and would have to be written to take the right one, which is the whole of what handing it a
     * world instead of a rule was for.
     */
    List<Clauses.StatedPart> present() {
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
        if (omitted.contains(shape.at())) {
            return true;
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
