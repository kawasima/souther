package souther.compiler.execute;

import souther.compiler.ast.Hir;
import souther.compiler.check.Sig;
import souther.compiler.coverage.Observation;
import souther.compiler.types.ValueName;

import java.util.List;
import java.util.Optional;

/**
 * A way to run rows nobody wrote and see where they went.
 *
 * <p>What a search needs and an evaluation does not. A row composed to fill a gap has no expected
 * value to be held to; what is wanted of it is which arms it reached, so that a candidate can be
 * offered on the strength of what it actually covers rather than on what it looks like it would.
 *
 * <p>A way to ask, for the reason {@link BoundaryValues} is one: the classes the rows are run
 * against are settled once and every candidate goes through the same ones.
 */
@FunctionalInterface
public interface RowTrials {

    /** A way to run rows of {@code behavior}. */
    OfBehavior forBehavior(String behavior, Sig sig);

    /**
     * A way to run one row's inputs and see what it did.
     *
     * <p>Empty is an ordinary answer and not a failure. Nothing having run a row leaves every
     * combination as untried as it was; a row that ran and reached nothing is a row that missed, and
     * the two must not come back as one value.
     */
    @FunctionalInterface
    interface OfBehavior {

        /**
         * What running {@code inputs} was seen doing, or empty where nothing could run them.
         *
         * @param answers what to stand each of the target's dependencies in with, in the order the
         *                target requires them. A behavior that depends on another is applied with an
         *                instance per dependency, so a row of one that hands none is a row nothing
         *                applies
         */
        Optional<Observation> run(List<Hir.Expr> inputs, List<AnsweredWith> answers);
    }

    /**
     * What one dependency answers while a row runs, as the value a row states.
     *
     * <p>The dependency and what it answers, and nothing about which distinctions the body drew on
     * it: that is settled where the body is read, and what reaches here is a row's value — so this
     * layer never has to be taught a vocabulary that belongs to the reading.
     *
     * <p>One value, for every call the row makes, which is what a row's {@code with} states.
     *
     * @param dependency which behavior this stands in for
     * @param signature  what that behavior takes and answers, which is what the value here is built
     *                   through and what decides what an instance of it can be. The dependency's
     *                   own and not the target's: a stand-in stands where the dependency does
     * @param answers    the value
     */
    record AnsweredWith(ValueName.Behavior dependency, Sig signature, Hir.Expr answers) {

        public AnsweredWith {
            if (dependency == null || signature == null || answers == null) {
                throw new IllegalArgumentException("a dependency stood in answers something");
            }
        }
    }
}
