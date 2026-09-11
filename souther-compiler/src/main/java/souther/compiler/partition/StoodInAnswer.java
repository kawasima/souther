package souther.compiler.partition;

import souther.compiler.types.ValueName;

/**
 * What a row stands one dependency in with.
 *
 * <p>One value, answering every call the row makes, which is what a {@code with} on a row states. A
 * way that needs the dependency to answer differently at different calls needs a table beside the
 * block instead, and is a way nothing composes a row for — said where a row is composed rather than
 * here, since what this is is a value a row states.
 *
 * <p><b>And a row stands a dependency in whether or not the body decides on what it answers.</b> A
 * behavior that calls a clock and turns on nothing it says still cannot be applied without one, so
 * such a dependency is answered too. What the decision turns on is what the value was chosen
 * against, and is no part of what the value is.
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
