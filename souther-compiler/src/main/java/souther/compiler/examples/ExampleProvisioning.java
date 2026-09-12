package souther.compiler.examples;

import souther.compiler.ast.Hir;
import souther.compiler.check.FakeTables;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

/**
 * What stands in for a behavior's requirement while an example runs (spec §example-fakes).
 *
 * <p>Two things may: a {@code with} written on the row, and a {@code fake} table written beside it.
 * The row is looked at first, so a row that answers a dependency itself answers it whatever the
 * module says. A requirement nothing was written for is one nothing can run against, which is
 * E1908; one written for more than once is a refusal said where the blocks are, and the row is
 * left with nothing to run against and nothing of its own to say.
 *
 * <p>Said here rather than inside the verifier that reports it, because the question is asked over
 * and over: by a run about to build a stand-in, by a reader that only wants to know what a row still
 * owes and has no values to build, and by a search composing a row nobody has written — which asks
 * it of the empty row and gets back what the module answers on its own. What was written and where
 * is handed back rather than a yes or no, since each of them does something different with it.
 *
 * <p>What a row contributes is the {@code with}s on it, taken as a list rather than as the row, so
 * that a row nobody has written yet can be asked the same question by contributing none.
 */
public final class ExampleProvisioning {

    private ExampleProvisioning() {}

    /** What was found to stand in, and where it was written. */
    public sealed interface Standin {

        /** The row answers this one itself, with a value that ignores what it is asked. */
        record OnTheRow(Hir.With written) implements Standin {}

        /** A table beside the rows answers it. */
        record InTheModule(FakeTables.Occurrence.Resolved table) implements Standin {}

        /** Nothing answers it, and a row whose target requires it cannot be run. */
        record Nothing() implements Standin {}

        /**
         * More than one block names it, so none of them stands in for it.
         *
         * <p>Told apart from {@link Nothing} because they are different facts about the module and
         * a reader acts differently on each. A row cannot be run either way; what is wrong is that
         * nothing was written in the one case and that too much was in the other, and a row saying
         * a stand-in is missing where two are written names the author's own rows as the problem.
         * The refusal is said where the blocks are written, so the row says nothing of its own.
         */
        record MoreThanOneBlock(FakeTables.Declaration.Conflict blocks) implements Standin {}
    }

    /**
     * What stands in for {@code dependency}, given what the row supplies of its own.
     *
     * @param onTheRow the {@code with}s written on the row; empty for a row not written yet
     * @param declared what the module's {@code fake} blocks declare, which is all of a module this
     *                 question reads — so a search composing a row it has no module in hand for
     *                 asks it the same way a verifier with one does
     */
    public static Standin standingIn(List<Hir.With> onTheRow, ValueName.Behavior dependency,
                                     FakeTables declared) {
        for (Hir.With written : onTheRow) {
            if (dependency.equals(written.standsInFor())) {
                return new Standin.OnTheRow(written);
            }
        }
        return switch (declared.declaredFor(dependency)) {
            case FakeTables.Declaration.Missing _ -> new Standin.Nothing();
            case FakeTables.Declaration.One(FakeTables.Occurrence.Resolved table) ->
                    new Standin.InTheModule(table);
            case FakeTables.Declaration.Conflict blocks -> new Standin.MoreThanOneBlock(blocks);
        };
    }

    /**
     * Of {@code required}, the ones nothing was written for, in the order they were required.
     *
     * <p>Nothing written, and not every dependency a row cannot be run against. A dependency more
     * than one block names is not one a stand-in is missing for, and reporting it as one would
     * point an author at a requirement they wrote two answers to.
     */
    public static List<ValueName.Behavior> unsupplied(List<Hir.With> onTheRow,
                                                      List<ValueName.Behavior> required,
                                                      FakeTables declared) {
        List<ValueName.Behavior> owed = new ArrayList<>();
        for (ValueName.Behavior dependency : required) {
            if (standingIn(onTheRow, dependency, declared) instanceof Standin.Nothing) {
                owed.add(dependency);
            }
        }
        return List.copyOf(owed);
    }
}
