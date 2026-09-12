package souther.compiler.partition;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of a file, and nothing about what it was composed for.
 *
 * <p>What it answers is the discharge's to say. Held here as well, the two were free to disagree:
 * a row composed for a class and later found to take an arm was merged into one line, the merged
 * line replaced the row a reader is offered, and the class's own entry went on holding the line
 * from before the merge. Nobody read the stale one, and the next reader would have.
 *
 * <p><b>A line is its values and what it stands the dependencies in with.</b> Both are written on
 * it and both decide what happens when it is applied, so two lines that answer one dependency
 * differently are two lines however alike their inputs are. Kept as the inputs alone, the row a
 * reader is offered was assembled from one line and run in another environment, and the second was
 * the one the search had certified. A line that leaves a dependency to the module's table answers
 * it differently from one writing a value for it, and the two are told apart the same way.
 *
 * @param inputs  one value per parameter, in the order the behavior takes them
 * @param answers what it stands each dependency its target requires in with
 */
public record ComposedRow(List<FixtureTemplate> inputs, List<StoodInAnswer> answers) {

    public ComposedRow {
        inputs = List.copyOf(inputs);
        answers = List.copyOf(answers);
    }

    /** What the row is written as, which is what tells one line of a file from another. */
    public List<String> writtenAs() {
        List<String> out = new ArrayList<>(
                inputs.stream().map(FixtureTemplate::text).toList());
        for (StoodInAnswer each : answers) {
            // The one leaning on the module writes no value, and the name alone is what tells it
            // from a line writing one — two lines answering a dependency two ways are two lines.
            out.add(switch (each) {
                case StoodInAnswer.OnTheRow(var dependency, var value) ->
                        dependency.name() + " = " + value.text();
                case StoodInAnswer.InTheModule(var dependency) -> dependency.name();
            });
        }
        return List.copyOf(out);
    }
}
