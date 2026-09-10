package souther.compiler.flow;

/**
 * Whether the language's own operations stand in the tree being read, or are expanded in it.
 *
 * <p>The one thing about a body this reading cannot work out for itself, and the caller always
 * knows: one representation of a body keeps a call to an operation standing for a reader to quote,
 * and another has it expanded into what it does. Both are the same body.
 *
 * <p>What it decides is what a call kept standing means. In the tree the operations are expanded in,
 * one is this compiler having failed to expand it — a defect, and refused where it is met. In the
 * tree they stand in, one is the model naming an operation: the arguments are evaluated and what the
 * operation answers is a value this reading has no words for, which is an ordinary answer and not a
 * shortfall of anything.
 *
 * <p>Stated rather than guessed. A reading that read a kept call either way would have to decide
 * from the node which tree it was in, and the node is the same node — so the guard that catches an
 * unexpanded call in the expanded tree would be the first thing to go.
 */
public enum WhereTheOperationsAre {

    /** They stand in this tree: a call kept standing is one the model names. */
    STAND_IN_IT,

    /** They are expanded in this tree: a call kept standing is one this compiler failed to
     *  expand. */
    ARE_EXPANDED_IN_IT
}
