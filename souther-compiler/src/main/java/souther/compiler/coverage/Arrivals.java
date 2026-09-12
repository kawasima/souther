package souther.compiler.coverage;

import souther.compiler.core.Core;

/**
 * Whether an expression answers a value, for a reader that asks about more than one of them.
 *
 * <p>{@link NormalReturn} is rooted: it answers about the nodes of one tree and refuses a node from
 * anywhere else, because reading such a node as a body of its own has every name in it free and a
 * name bound to something that aborts is what makes the difference. Which tree a reader's questions
 * are about is the reader's own fact, and this is where the reader says it.
 *
 * <p>So the root is named where the reading is made. A body is one tree and a declaration's clause
 * is another — a clause is checked whenever a value is built and stands on the way to nothing — and
 * what differs between the two readers is which node each of them names, not what either does with
 * the nodes under it.
 */
public interface Arrivals {

    /** Whether {@code e} answers a value. */
    boolean at(Core e);

    /**
     * The nodes of the tree {@code root} is the root of, read once.
     *
     * <p>One way in, and the root is named. Which tree the questions are about is a fact about that
     * tree and never about the node being asked: a node under a binding is one the binding is
     * evaluated before, and the same node read as the root of its own tree has that name free. A
     * reader that took each node it was asked about for a root would answer the second question
     * everywhere, and a way of saying so is a way of getting it wrong.
     *
     * <p>Built when something first asks, most expressions holding no fork and the question being
     * asked only about the arms of one.
     *
     * <p>A reader that reads several trees makes one of these per tree, where it crosses into one.
     * A body is a tree; so is a declaration's clause, which stands in no body — what differs is
     * which node the caller names, not how the answer is worked out.
     */
    static Arrivals inTheTree(Core root) {
        NormalReturn answering = NormalReturn.lazilyWhereTheOperationsStand(root);
        return answering::at;
    }

    /**
     * Every expression taken for one that answers a value, for a reader that is not told which tree
     * its expressions stand in.
     *
     * <p>Not a root and not a guess at one. A reader handed a part of a clause and no clause has
     * nothing to root a reading at, and rooting at the part is the one answer that is wrong — it
     * reads the part as a tree of its own and loses whatever bound a name above it. So this says
     * what it does not know, and what it costs is an arm that answers no value counted among the
     * values, which is what such an arm came to before anybody asked.
     *
     * <p>For the reader to stop needing this, the walk that hands it a part has to hand over the
     * clause the part was read out of.
     */
    static Arrivals everyArmIsTakenForAValue() {
        return e -> true;
    }

}
