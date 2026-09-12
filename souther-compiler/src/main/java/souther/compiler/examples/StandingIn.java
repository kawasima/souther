package souther.compiler.examples;

import souther.compiler.ast.Hir;
import souther.compiler.check.BoundaryInput;
import souther.compiler.check.FakeTables;
import souther.compiler.check.Sig;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Where a stand-in for a dependency is made.
 *
 * <p>One place, and the readings that need one enter it. Two do: a row somebody wrote, whose
 * {@code with} and {@code fake} are read where the rest of the row is, and a row a search composed
 * to find out what a rule takes. What each of them hands over is a way of answering — the reading
 * of what the row states is the reader's own work and is done before this — so what is here is the
 * one statement of what a stand-in is.
 *
 * <p>Deciding nothing about what a stand-in answers. A value a row wrote arrives built, and which
 * row of a table answers a call is the table's own rule, asked where the table is
 * ({@link ExampleStatements.Standins#answering}); a rule written here would be a second answer to
 * it. What {@link #byTheTable} adds is the one way a table becomes something a behavior can be
 * applied with, which both the run that verifies a written row and the run a search makes need and
 * neither should have its own version of.
 */
final class StandingIn {

    private StandingIn() {}

    /**
     * A stand-in for {@code dependency} that answers by {@code answers}.
     *
     * @param inputs  how many inputs the dependency takes, which decides what an instance of it can
     *                be made into
     * @param answers what it answers, for the arguments it is called with
     */
    static DependencyStandin by(ValueName.Behavior dependency, int inputs,
                                Function<Object[], Object> answers) {
        return new DependencyStandin(dependency, inputs, answers);
    }

    /**
     * What a module's table stands {@code dependency} in with, and the table it was read off, or
     * null where the table is not one to stand in with.
     *
     * <p>Both, off one build. What a report says the table answers has to be what the run was held
     * against, and a reader building its own copy to list would be listing a second table — so the
     * dispatch and the rows a reader walks come back together and whoever wants only one of them
     * leaves the other.
     *
     * <p>Null for a table that will not build and for one stating what the dependency declares
     * cannot happen. Both are wrong about the table and are said once, where the table is written;
     * standing in with either would put the rest of the behavior in a state the model rules out,
     * and everything said afterwards would be about a run that cannot happen.
     */
    static OffATable byTheTable(FixtureReader fixtures, EnsuresChecks ensures,
                                FakeTables.Occurrence.Resolved stated,
                                ValueName.Behavior dependency, Sig signature) {
        Hir.Fake fk = stated.read();
        // The dependency's own signature, which admitted what its boundary carries. Rebuilding the
        // types from what it declared would put them through that walk a second time, and a
        // stand-in stands where the behavior does.
        List<BoundaryInput> paramTypes = signature.ins();
        // Built the one way a table is built, on the caller's own reader, so a row that does not
        // finish inside a table's helper is still inside a helper. What is wrong with the table is
        // said where the fake is written, and said once: this row and every other row reaching the
        // same fake would each repeat the one thing wrong with the one table.
        ExampleStatements.BuiltTable built = ExampleStatements.standins(
                fixtures, fk, paramTypes, signature.out(), new ArrayList<>());
        if (built == null || !ExampleStatements.notKept(ensures, stated, built).isEmpty()) {
            return null;
        }
        ExampleStatements.Standins table = built.standins();
        String wrote = ExampleStatements.wrote(fk);
        int arity = paramTypes.size();
        Function<Object[], Object> answers = a -> {
            Object[] key = Arrays.copyOf(a, arity);
            // The table's own rule, which is the rule the reading that holds it against the rows
            // recorded for the behavior asks too. One answer to "which row answers this" (E1919).
            ExampleStatements.Standin answering = table.answering(key);
            if (answering == null) {
                throw new FakeMissException(
                        "`" + wrote + "` has no output for " + Arrays.toString(key));
            }
            return answering.answer().value();
        };
        return new OffATable(by(dependency, arity, answers), table);
    }

    /** A stand-in a module's table gives, and the table it dispatches over. */
    record OffATable(DependencyStandin applies, ExampleStatements.Standins table) {}
}
