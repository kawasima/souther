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
 * <p>The arguments as subjects rather than as expressions, for the same reason: what a row controls
 * is what the argument comes to, and two spellings of one argument are one question asked. An
 * argument this reading cannot say as a subject leaves the answer unnamed, which is what a
 * condition over it being unread says.
 *
 * @param dependency which behavior the row stands in for, named as the provisioning names it
 * @param arguments  what it was applied to, in the order the declaration takes them
 */
public record InjectedAnswer(ValueName.Behavior dependency, List<DecisionSubject> arguments) {

    public InjectedAnswer {
        if (dependency == null || arguments == null) {
            throw new IllegalArgumentException(
                    "an answer of a dependency is some behavior's, applied to something");
        }
        arguments = List.copyOf(arguments);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(dependency.name()).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            out.append(i == 0 ? "" : ", ").append(arguments.get(i));
        }
        return out.append(')').toString();
    }
}
