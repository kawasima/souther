package souther.compiler.partition;

import souther.compiler.types.ValueName;

/**
 * What a row stands one dependency in with.
 *
 * <p>Two things answer a dependency while a row runs, and a row says which. The row itself, with a
 * value of its own written as a {@code with}; or the table the module already states beside the
 * rows, which every row written by hand runs against and which a row composed here has no reason to
 * be outside of. Row-local either way, so two rows standing one dependency in two ways sit in one
 * block and neither reaches past itself.
 *
 * <p><b>And a row stands a dependency in whether or not the body decides on what it answers.</b> A
 * behavior that calls a clock and turns on nothing it says still cannot be applied without one, so
 * such a dependency is answered too. What the decision turns on is what the value was chosen
 * against, and is no part of what the value is.
 *
 * <p>An arm and not a value that may be missing. A row leaning on the module writes nothing at that
 * dependency, and a reader handed an absence there would have to decide for itself whether the row
 * was leaning on the module or was short of an answer — which are a row that runs and a row that
 * does not.
 */
public sealed interface StoodInAnswer {

    /** Which behavior this stands in for. */
    ValueName.Behavior dependency();

    /**
     * The row answers it itself, with one value answering every call the row makes.
     *
     * <p>What a {@code with} on a row states, and what is preferred to whatever the module says. A
     * way that needs the dependency to answer differently at different calls is not one a value
     * here can serve; such a way leans on the module's table or is a way nothing composes a row
     * for, said where a row is composed.
     *
     * <p>The value is a {@link FixtureTemplate} and not something built, for the reason a row's
     * inputs are: what goes to a person is text and what a run needs is the tree, and deriving
     * either from the other would be a second spelling of one value.
     *
     * @param dependency which behavior this stands in for
     * @param value      what it answers with
     */
    record OnTheRow(ValueName.Behavior dependency, FixtureTemplate value) implements StoodInAnswer {

        public OnTheRow {
            if (dependency == null || value == null) {
                throw new IllegalArgumentException("an answer stood in is some dependency answered");
            }
        }
    }

    /**
     * The table the module states for it answers, and the row writes nothing.
     *
     * <p>The environment several rows share rather than anything this row carries. What the table
     * answers at a call is the table's own rule and is asked where the table is, so nothing of it
     * is copied here — a row holding a copy would be a second spelling of one table, and the two
     * would be free to disagree once the author edited the block.
     *
     * @param dependency which behavior this stands in for
     */
    record InTheModule(ValueName.Behavior dependency) implements StoodInAnswer {

        public InTheModule {
            if (dependency == null) {
                throw new IllegalArgumentException("an answer stood in is some dependency answered");
            }
        }
    }
}
