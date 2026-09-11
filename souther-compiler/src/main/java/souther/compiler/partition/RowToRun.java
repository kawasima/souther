package souther.compiler.partition;

import java.util.List;

/**
 * A row a search put together, as everything it takes to run one.
 *
 * <p>What the row writes at the behavior's positions, and what it stands the behavior's dependencies
 * in with. Both, because a row is one row: a behavior that depends on another is applied with an
 * instance per dependency, so values at the positions are not yet something to run.
 *
 * <p>Beside {@link ComposedRow} and not the same thing. That one is a line of a file, read for what
 * tells two lines apart; this is what a run is handed, read for what it takes to apply the
 * behavior. The two hold the same facts and are asked different questions, and a run handed a line
 * would be a run reading an identity for the values to apply.
 *
 * <p>One value rather than two lists passed side by side. The inputs and the answers are two halves
 * of one account of what a row is, and a call taking them apart is a place where half of one row
 * can be handed on with the other half of another.
 *
 * @param inputs  what the row writes at each position of the behavior, in the order it takes them
 * @param answers what it stands each asking of a dependency in with
 */
public record RowToRun(List<FixtureTemplate> inputs, List<StoodInAnswer> answers) {

    public RowToRun {
        if (inputs == null || answers == null) {
            throw new IllegalArgumentException("a row is values at positions and answers stood in");
        }
        inputs = List.copyOf(inputs);
        answers = List.copyOf(answers);
    }

    /** A row of a behavior that depends on nothing, which stands nothing in. */
    public static RowToRun of(List<FixtureTemplate> inputs) {
        return new RowToRun(inputs, List.of());
    }
}
