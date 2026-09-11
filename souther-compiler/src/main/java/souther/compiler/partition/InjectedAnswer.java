package souther.compiler.partition;

import souther.compiler.types.ValueName;

import java.util.List;

/**
 * What a dependency answered, as a row can pin it.
 *
 * <p>The behavior and what it was applied to, which is what tells one of these from another. A body
 * asking one dependency about two things draws two distinctions, and a value keyed on the
 * dependency alone would run them together — after which a table would say the body decides less
 * than it does, and a row written for one of them would be read as answering both.
 *
 * <p><b>And not the call.</b> A body asking one dependency twice about one thing asks one question,
 * whichever line each call is written on. Keyed by the call, the two would be two columns, and a
 * table with a column apiece admits an assignment where one answer is two answers — an assignment
 * no row can be written at, since a row stands a dependency in for the whole of its run.
 *
 * <p>The arguments as what tells two askings apart rather than as expressions, for the same reason:
 * two spellings of one argument are one question asked. What an argument has to be is known and not
 * controlled — a number the model settles is as much a question asked as a position a row writes at
 * — which is {@link DecisionArgument}'s, beside the subjects rather than among them.
 *
 * @param dependency which behavior the row stands in for, named as the provisioning names it
 * @param arguments  what it was applied to, in the order the declaration takes them
 */
public record InjectedAnswer(ValueName.Behavior dependency, List<DecisionArgument> arguments) {

    public InjectedAnswer {
        if (dependency == null || arguments == null) {
            throw new IllegalArgumentException(
                    "an answer of a dependency is some behavior's, applied to something");
        }
        arguments = List.copyOf(arguments);
    }

    /**
     * What this answer is, as an identity spells it.
     *
     * <p>The behavior under the module that declares it. Two modules may declare behaviors of one
     * name, and a spelling that left the module off would have one column for two dependencies —
     * and, where an order is taken over these, would settle that order by which of them a reader
     * happened to meet.
     */
    public String spelled() {
        StringBuilder out = new StringBuilder(dependency.module()).append('.')
                .append(dependency.name()).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            out.append(i == 0 ? "" : ", ").append(arguments.get(i).spelled());
        }
        return out.append(')').toString();
    }

    @Override
    public String toString() {
        return spelled();
    }
}
