package souther.compiler.partition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One rule of the decision a body states: the conditions a path through it consulted, and what each
 * came out as.
 *
 * <p>A path and not an assignment. Every condition the path never reached is absent here rather
 * than present as a don't-care, which is what makes a short-circuit legible: under {@code A && B}
 * the answer for {@code A} failing is a rule that says nothing about {@code B}, and the answer for
 * {@code B} failing is a rule that says {@code A} held.
 *
 * <p><b>Told apart by what it consulted, not by the order it consulted it in.</b> Two paths that
 * ask the same distinctions and get the same answers are one rule however the tree interleaved
 * them, so the vector is compared as the map from a distinction to its answer. The order is kept
 * beside that, for a reader, because the order a walk met the conditions in is the order an author
 * wrote them.
 *
 * <p>A rule is what one obligation of the decision derivation is owed at. What settles whether it is
 * owed at all, and whether a row takes it, are asked elsewhere: a vector says what a row would have
 * to satisfy and never that one can be written.
 */
public record DecisionRule(Map<DecisionCondition, DecidedCondition> consulted) {

    public DecisionRule {
        consulted = Collections.unmodifiableMap(new LinkedHashMap<>(consulted));
    }

    /** The conditions in the order the walk met them, which is the order the author wrote them. */
    public List<DecidedCondition> inOrder() {
        return List.copyOf(consulted.values());
    }

    /**
     * What this rule says about {@code condition}, or null where the path never consulted it.
     *
     * <p>Null is the don't-care and is the whole of it. A reader asking whether a rule can host the
     * point of a line asks this and gets three answers apart: the polarity that admits the point,
     * the one that does not, and the path having settled before the condition was reached.
     */
    public DecidedCondition at(DecisionCondition condition) {
        return consulted.get(condition);
    }

    /** Whether any condition on this path is one the reading had no words for. */
    public boolean consultedSomethingUnread() {
        return consulted.keySet().stream()
                .anyMatch(DecisionCondition.AConditionNotRead.class::isInstance);
    }
}
