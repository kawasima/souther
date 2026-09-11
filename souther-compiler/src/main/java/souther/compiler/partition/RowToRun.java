package souther.compiler.partition;

import java.util.List;

/**
 * A row a search put together, as everything it takes to run one.
 *
 * <p>What the row writes at the behavior's positions, and what it stands the behavior's dependencies
 * in with. Both, because a row is one row: a behavior that depends on another is applied with an
 * instance per dependency, so values at the positions are not yet something to run.
 *
 * <p>Beside {@link ComposedRow} and not the same thing. That one is a line of a file — what tells
 * two offered rows apart is what they are written as — and this is what a run is handed. Held as
 * one, the identity of a line would turn on what a dependency was stood in with, and two rows that
 * a person would read as one line would be two.
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
