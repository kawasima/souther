package souther.compiler.examples;

import souther.compiler.types.ValueName;

import java.util.function.Function;

/**
 * Where a stand-in for a dependency is made.
 *
 * <p>One place, and the readings that need one enter it. Two do: a row somebody wrote, whose
 * {@code with} and {@code fake} are read where the rest of the row is, and a row a search composed
 * to find out what a rule takes. What each of them hands over is a way of answering — the reading
 * of what the row states is the reader's own work and is done before this — so what is here is the
 * one statement of what a stand-in is.
 *
 * <p>Not a reading of anything itself. Which row of a table answers a call is the table's own rule
 * and is asked where the table is ({@link ExampleStatements.Standins#answering}); a reading built
 * here would be a second answer to it.
 */
final class StandingIn {

    private StandingIn() {}

    /**
     * A stand-in for {@code dependency} that answers by {@code answers}.
     *
     * @param inputs  how many inputs the dependency takes, which decides what an instance of it can
     *                be made into
     * @param answers what it answers, for the arguments it is called with
     */
    static DependencyStandin by(ValueName.Behavior dependency, int inputs,
                                Function<Object[], Object> answers) {
        return new DependencyStandin(dependency, inputs, answers);
    }
}
