package souther.compiler.coverage;

import souther.compiler.core.Core;
import souther.compiler.flow.Anonymous;
import souther.compiler.flow.AnonymousPath;
import souther.compiler.flow.ValueArrivals;

/**
 * Whether evaluating an expression can answer a value.
 *
 * <p>An {@code unreachable} answers none and aborts instead, and an expression that has to evaluate
 * one on its way answers none either. This is what tells an arm the rows can be in from an arm that
 * only states a combination the model rules out — the first is a fork a row takes, the second is not
 * an arm at all.
 *
 * <p>A projection of {@link ValueArrivals} and not a reading of its own. Whether a value arrives and
 * what it comes to are one question wherever an operator stops as soon as its answer is settled:
 * {@code a > 1 && unreachable} arrives for every small {@code a} and never for a large one, and
 * nothing reading only the shape of the node can say which. So there is one reading, and this is that
 * reading asked whether there is any way at all.
 *
 * <p>Read off the tree and not off the types. An {@code unreachable} is written as {@code Never}, but
 * the position it stands in usually states a shape, and that shape is what the elaborator records on
 * the node — so a {@code match} arm answering {@code unreachable} carries the type of what its
 * siblings answer. The node kind is what survives.
 *
 * <p>What a call answers is not looked into. A non-recursive helper is inlined into the body that uses
 * it, so its arms are already here; a recursive one is a shared method, and a helper that never
 * returns leaves the arm that calls it counted. The arguments are looked at, because they are
 * evaluated before the call is — but a function passed as one is made and not run, so what its body
 * does when the call gets round to it is the call's business and stays unread.
 */
public final class NormalReturn {

    private final ValueArrivals<AnonymousPath> reading;

    private NormalReturn(ValueArrivals<AnonymousPath> reading) {
        this.reading = reading;
    }

    /**
     * The reading of one body, which every question about a node in it is asked of.
     *
     * <p>Rooted, because what a name reads is not a fact about the node that reads it. A
     * {@code Core.Read} standing under a {@code let} that bound it to a number written out is settled
     * by that number; the same node standing in a body that binds nothing is a position of the input.
     * Asking about a subtree on its own would answer the second of those about the first.
     */
    public static NormalReturn ofBody(Core body) {
        return new NormalReturn(ValueArrivals.ofBody(body, Anonymous.NAMING));
    }

    /**
     * The same, of a tree the language's own operations stand in.
     *
     * <p>Which kind of tree this is, is the caller's to say and this class's to spend. A call kept
     * standing is a defect in a tree the operations were expanded in and the model naming an
     * operation in one they stand in, and a reader that answered it for itself would be deciding
     * what a tree is from the outside of the reading that walks it.
     */
    public static NormalReturn ofBodyWhereTheOperationsStand(Core body) {
        return new NormalReturn(
                ValueArrivals.ofBodyWhereTheOperationsStand(body, Anonymous.NAMING));
    }

    /** Whether {@code e}, standing where it stands in this body, can be evaluated to a value. */
    public boolean at(Core e) {
        return reading.arrivesAt(e);
    }

    /**
     * Whether a run can go down arm {@code part} of {@code fork}.
     *
     * <p>A second question about an arm and not the same one. An arm whose body answers a value is
     * still an arm no run enters where the condition never comes out its way — {@code true} sends
     * every run to the first, and a comparison against the largest whole number sends every run to
     * the second. Asked of the reading rather than worked out again here, because which arms a
     * condition can reach is what that reading already answers and two accounts of it would be two
     * answers to keep in step.
     *
     * <p>Anything but a fork on a value answers yes. Which arm of a {@code match} a run takes is
     * which case the value is, and which arm of an attempted construction it takes is whether the
     * thing was made — neither is a truth this reading has anything to say about.
     */
    public boolean mayEnter(Core fork, int part) {
        return !(fork instanceof Core.If iff) || reading.comesAt(iff.cond()).mayCome(part == 0);
    }

    /**
     * Whether {@code e} can be evaluated to a value, read as a body of its own.
     *
     * <p>For a caller that has an expression and nothing above it. Every name in it is free, which is
     * what it means for nothing to have bound them.
     */
    public static boolean of(Core e) {
        return ofBody(e).at(e);
    }
}
