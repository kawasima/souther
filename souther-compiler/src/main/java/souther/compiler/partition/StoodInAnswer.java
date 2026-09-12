package souther.compiler.partition;

import souther.compiler.types.ValueName;

/**
 * What a row stands one dependency in with.
 *
 * <p>One value, answering every call the row makes, which is what a {@code with} on a row states.
 * Row-local, so two rows standing one dependency in two ways sit in one block and neither reaches
 * past itself — what a row writes is preferred to whatever a module says.
 *
 * <p><b>And a row stands a dependency in whether or not the body decides on what it answers.</b> A
 * behavior that calls a clock and turns on nothing it says still cannot be applied without one, so
 * such a dependency is answered too. What the decision turns on is what the value was chosen
 * against, and is no part of what the value is.
 *
 * <p>A way that needs the dependency to answer differently at different calls needs a table written
 * for the module, which is not a row's to carry and not what this is. Such a way is one nothing
 * composes a row for here, said where a row is composed.
 *
 * <p>The value is a {@link FixtureTemplate} and not something built, for the reason a row's inputs
 * are: what goes to a person is text and what a run needs is the tree, and deriving either from the
 * other would be a second spelling of one value.
 *
 * @param dependency which behavior this stands in for
 * @param value      what it answers with
 */
public record StoodInAnswer(ValueName.Behavior dependency, FixtureTemplate value) {

    public StoodInAnswer {
        if (dependency == null || value == null) {
            throw new IllegalArgumentException("an answer stood in is some dependency answered");
        }
    }
}
