package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.semantics.ConditionJoin;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Which of the parts an author wrote are rules of the world a clause is being read in.
 *
 * <p>A reading asked what one conjunct was holding is a reading of the declaration under a rule set
 * the author did not write ({@link InvariantChecker.Reach#withoutParts}), and every question about
 * that world has to be answered out of that rule set. Made once from the reach and handed to each
 * reading, so that whether a reader honours it is not a question anybody has to ask of the reader:
 * what is left out is left out of the tree it walks.
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

    /** The part roots this world does not hold, by identity: two conjuncts spelled alike are two
     *  parts, and a set comparing them by what they say would leave out both. */
    private final Set<Core> omitted;

    private ClauseView(Set<Core> omitted) {
        this.omitted = omitted;
    }

    /** The world the author wrote, where every part of every clause is a rule. */
    private static final ClauseView WHOLE = new ClauseView(Collections.emptySet());

    /** The same, for a reading of the declaration as it stands. */
    static ClauseView whole() {
        return WHOLE;
    }

    /**
     * The world {@code without} leaves of a clause whose parts are {@code parts}.
     *
     * <p>{@link #whole()} where it leaves none of them out, so that a reading under a reach that
     * omits nothing is the reading of the clause as written and is that by being it.
     */
    static ClauseView of(List<Clauses.StatedPart> parts, PartsLeftOut without) {
        // Asked before the parts are walked, because almost every reading there is asks for the
        // declaration whole: a clause is viewed for every clause of every value, and a reading that
        // leaves nothing out would otherwise build a set to find nothing in.
        if (!without.leavesAnythingOut()) {
            return WHOLE;
        }
        Set<Core> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Clauses.StatedPart each : parts) {
            if (without.excludes(each.id())) {
                out.add(each.expr());
            }
        }
        return out.isEmpty() ? WHOLE : new ClauseView(out);
    }

    /** Whether every part of every clause is a rule of this world, which almost every reading is
     *  made in. Asked where working out the shape of a clause would be the cost of finding out. */
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
        return omitted.isEmpty() ? "the whole clause" : "without " + omitted.size() + " part(s)";
    }
}
