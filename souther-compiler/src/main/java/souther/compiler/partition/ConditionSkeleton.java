package souther.compiler.partition;

import souther.compiler.core.Core;
import souther.compiler.types.BinOp;

import java.util.ArrayList;
import java.util.List;

/**
 * What a condition tests, cut into the parts a rule can be about.
 *
 * <p>A condition is a tree and only some of it decides the fork. {@code a > 0 && f(g(b > 1))} tests
 * two things — a comparison and whatever {@code f} answers — and the comparison inside {@code g}'s
 * argument is not one of them: it is written on the way to a value, and what that value comes to is
 * not something reading it says. So the parts are taken off the shape that decides the answer and
 * never off the whole subtree.
 *
 * <p><b>One cut, for every reader that has to agree about the parts.</b> Which rules a fork's
 * condition already states is settled between three readers, and each of them working out for itself
 * where a part ends is three sets of parts that agree until one of them learns a shape. So the
 * cutting is here and the readers say only what they own.
 *
 * <p><b>Structure and not evaluation.</b> What is followed is what says "the answer here is the
 * answer there": the two sides of an {@code &&}, the body of a binding whose value the answer does
 * not depend on. A name standing for a condition is not followed here — reading what a binding holds
 * makes this a walk of where values come from, and the parts of a condition would then depend on how
 * many names an author put between the fork and what it tests.
 *
 * <p><b>Which does not mean a name is left standing for nothing.</b> Cutting is one question and
 * what a part turns out to be is another: an owner holding a part asks the reading what the name
 * stands for ({@link souther.compiler.inputs.InputReads#denotes}) and answers about that. So the
 * cut is of the shape and the resolving is the owner's, and neither is doing the other's work.
 */
final class ConditionSkeleton {

    private ConditionSkeleton() {}

    /**
     * The parts of {@code condition}, in the order they are written.
     *
     * <p>Nodes of the tree handed in, so a reader that met one while walking the same tree has the
     * same object. Told apart by being those objects and never by what they hold: {@code a > 0 && a
     * > 0} writes one comparison twice, and parts compared by their contents would be one part.
     */
    static List<Core> atoms(Core condition) {
        List<Core> out = new ArrayList<>();
        cut(condition, out);
        return out;
    }

    private static void cut(Core e, List<Core> out) {
        switch (e) {
            // Both sides decide the fork: one of them coming out the wrong way is the whole answer,
            // and a rule about either is a rule the fork tests.
            case Core.Binary both when both.op() == BinOp.AND || both.op() == BinOp.OR -> {
                cut(both.left(), out);
                cut(both.right(), out);
            }
            // A binding whose body is the answer. What it computes is a value the body may or may
            // not read, and reading it here would be following where a value came from rather than
            // what this expression answers with.
            case Core.LetIn let -> cut(let.body(), out);
            // And everything else is a part. Not because nothing is inside it, but because what is
            // inside is on the way to a value: an argument, a closure, the target of a field.
            case null -> { }
            default -> out.add(e);
        }
    }
}
