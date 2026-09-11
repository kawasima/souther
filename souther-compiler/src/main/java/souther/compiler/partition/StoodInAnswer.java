package souther.compiler.partition;

import souther.compiler.types.ValueName;

import java.util.List;

/**
 * What a row stands one asking of a dependency in with.
 *
 * <p>The identity a decision reads, the arguments as a row writes them, and the value chosen for
 * it. A run turns the three into the instance the implementation is constructed with; a block turns
 * them into the {@code with} or the table row a person reads. Neither works out which asking it is
 * about, because that is settled here and was read off the body.
 *
 * <p><b>The identity and the written arguments are not one thing said twice.</b> What tells two
 * askings apart is {@link DecisionArgument}, which names a position or a number the model settles;
 * what a table is keyed on is a value written down. A row that writes {@code Sku("a-1")} at the
 * position an asking named is the witness for that asking, exactly as the value below is the
 * witness for its answer.
 *
 * <p>The values are {@link FixtureTemplate}s and not something built, for the reason a row's inputs
 * are: what goes to a person is text and what a run needs is the tree, and deriving either from the
 * other would be a second spelling of one value.
 *
 * @param answer    which asking of which dependency this answers
 * @param appliedTo what the row writes at each of that asking's arguments, in the order the
 *                  dependency takes them
 * @param value     what it answers with
 */
public record StoodInAnswer(InjectedAnswer answer, List<FixtureTemplate> appliedTo,
                            FixtureTemplate value) {

    public StoodInAnswer {
        if (answer == null || appliedTo == null || value == null) {
            throw new IllegalArgumentException("an answer stood in is some asking answered");
        }
        appliedTo = List.copyOf(appliedTo);
        if (appliedTo.size() != answer.arguments().size()) {
            throw new IllegalArgumentException("an asking of " + answer.arguments().size()
                    + " arguments written with " + appliedTo.size());
        }
    }

    /** Which dependency this stands in for. */
    public ValueName.Behavior dependency() {
        return answer.dependency();
    }
}
