package souther.compiler.partition;

import souther.compiler.types.ValueName;

import java.util.List;

/**
 * What a row stands a dependency in with, at one of the things it is asked.
 *
 * <p>The identity a decision reads, the arguments as a row writes them, and the value chosen for
 * them. A run turns the three into the instance the implementation is constructed with; a block
 * turns them into the {@code with} or the table row a person reads. Neither of them works out which
 * asking it is about, because that was settled where the body was read and is carried here.
 *
 * <p><b>Carried and not collapsed.</b> Folded to one answer per dependency on the way, a row that
 * needs one to answer differently at different calls has nothing left to key a table on by the time
 * anything would write one — and the block would have to choose between offering a row that
 * answers the wrong call and saying the way cannot be composed for, neither of which is true.
 *
 * <p><b>And a row stands a dependency in whether or not the body decides on what it answers.</b> A
 * behavior that calls a clock and turns on nothing it says still cannot be applied without one, so
 * such a dependency is answered too — and what it answers for is every call, there being no asking
 * to name. Which of the two this is is {@link Asking}'s, so nothing downstream reads an absence.
 *
 * <p>The values are {@link FixtureTemplate}s and not something built, for the reason a row's inputs
 * are: what goes to a person is text and what a run needs is the tree, and deriving either from the
 * other would be a second spelling of one value.
 *
 * @param dependency which behavior this stands in for
 * @param asking     what it answers for
 * @param value      what it answers with
 */
public record StoodInAnswer(ValueName.Behavior dependency, Asking asking, FixtureTemplate value) {

    public StoodInAnswer {
        if (dependency == null || asking == null || value == null) {
            throw new IllegalArgumentException("an answer stood in is some asking answered");
        }
    }

    /** What one answer a row states is an answer for. */
    public sealed interface Asking {

        /**
         * Every call the row makes, which is what a {@code with} states.
         *
         * <p>What a dependency the decision turns on nothing of is answered for, and what a
         * dependency is answered for when one value serves every asking of it: a table whose rows
         * all say the same thing is that one answer written as many times as the body asks.
         */
        record ForEveryCall() implements Asking {}

        /**
         * One asking, and what the row writes at each of its arguments.
         *
         * <p><b>The identity and the written arguments are not one thing said twice.</b> What tells
         * two askings apart is {@link DecisionArgument}, which names a position or a number the
         * model settles; what a table is keyed on is a value written down. A row that writes
         * {@code Sku("a-1")} at the position an asking named is the witness for that asking, exactly
         * as the value beside it is the witness for its answer.
         *
         * @param answer    which asking of the dependency
         * @param appliedTo what the row writes at each of its arguments, in the order the
         *                  dependency takes them
         */
        record OfOne(InjectedAnswer answer, List<FixtureTemplate> appliedTo) implements Asking {

            public OfOne {
                if (answer == null || appliedTo == null) {
                    throw new IllegalArgumentException("an asking answered is some asking");
                }
                appliedTo = List.copyOf(appliedTo);
                if (appliedTo.size() != answer.arguments().size()) {
                    throw new IllegalArgumentException("an asking of "
                            + answer.arguments().size() + " arguments written with "
                            + appliedTo.size());
                }
            }

            /** The arguments as one line, which is how a table's row is keyed and written. */
            public List<String> writtenAs() {
                return appliedTo.stream().map(FixtureTemplate::text).toList();
            }
        }
    }
}
