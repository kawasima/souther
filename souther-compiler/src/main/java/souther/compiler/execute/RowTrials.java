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
     * What one dependency answers while a row runs.
     *
     * <p>The dependency and what answers it, and nothing about which distinctions the body drew on
     * it: that is settled where the body is read, and what reaches here is what the row came to —
     * so this layer never has to be taught a vocabulary that belongs to the reading.
     *
     * <p>Which of the two things answers is the row's to have said. A row writing a value of its
     * own runs against that value and a row writing nothing runs against the table its module
     * states, and a runner left to work out which from whether a value arrived would be deciding
     * what the composition already decided.
     */
    sealed interface AnsweredWith {

        /** Which behavior this stands in for. */
        ValueName.Behavior dependency();

        /**
         * What that behavior takes and answers, which is what a value here is built through and
         * what decides what an instance of it can be. The dependency's own and not the target's: a
         * stand-in stands where the dependency does.
         */
        Sig signature();

        /**
         * The row states the value, and it answers every call the row makes.
         *
         * <p>What a row's {@code with} states, and what is preferred to whatever the module says.
         */
        record OnTheRow(ValueName.Behavior dependency, Sig signature, Hir.Expr answers)
                implements AnsweredWith {

            public OnTheRow {
                if (dependency == null || signature == null || answers == null) {
                    throw new IllegalArgumentException("a dependency stood in answers something");
                }
            }
        }

        /**
         * The table the module states answers it, call by call.
         *
         * <p>Nothing of the table travels here. Which of its rows answers a call is the table's own
         * rule, asked where the table is built; carried as values, this would be a second copy of a
         * table the run is being held against.
         */
        record InTheModule(ValueName.Behavior dependency, Sig signature) implements AnsweredWith {

            public InTheModule {
                if (dependency == null || signature == null) {
                    throw new IllegalArgumentException("a dependency stood in answers something");
                }
            }
        }
    }
}
