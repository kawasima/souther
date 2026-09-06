package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.query.Compilation;
import souther.compiler.query.Front;
import souther.compiler.query.Shapes;
import souther.compiler.types.TypeKey;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A count of how many values a type has says whether it got every rule, and a count short of one
 * says nothing about a type having none.
 *
 * <p>What empties a type is what its rules leave. A rule that could not be worked out may be the one
 * that empties it, so a count taken over the rules that happened to be readable can be right and may
 * not report itself as settled — the type it calls inhabited is one nobody finished reading.
 *
 * <p>Two tests, at the two seams, because a run that goes wrong goes wrong at one of them. The first
 * is the mechanism: given a reading in which one declaration the count walks into cannot be read,
 * the count says so. The second is what the query makes of that. Held together in one test, a red
 * run would leave which of the two moved to be worked out by hand.
 *
 * <p>Apart from the reading's own answer to the same absence, which is a rule not reached
 * ({@code AnExpansionThatDidNotHappenIsARuleNotReachedTest}). One {@code Unavailable} and three
 * consumers, each owing something different: the two are not one test because they are not one
 * contract.
 */
class ACountOfWhatATypeHoldsSaysWhetherItGotEveryRuleTest {

    /** A type whose values a rule of another declaration bears on, reached by a spread. */
    private static final String MODEL = """
            module demo

            data Held = { n: Int }
                invariant n >= 1

            data Row = { ...Held, m: Int }
            """;

    /**
     * The count is told it could not read one declaration, and says so.
     *
     * <p>The reading is the compilation's own but for one answer, so what moves between the two runs
     * is the availability of that one declaration and nothing else. Written by breaking the module
     * instead, the run would be measuring whatever else stopped along with it.
     */
    @Test
    void aCountThatCouldNotReadARuleSaysSo() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource whole = RuleReadings.of(compilation, module);
        List<Hir.Def> declarations =
                compilation.db().ask(new Shapes.Prepared(module)).value().defs().stream()
                        .map(each -> each.declaration().node()).map(Hir.Def.class::cast).toList();
        ReadingPolicy policy = compilation.db().ask(new Front.Reading()).value();

        assertTrue(TypeCardinality.solve(declarations, whole, policy).everyRuleReached(),
                "every rule of this model can be read");

        TypeKey held = new TypeKey(module, "Held");
        RuleReadingSource shortOfOne = new RuleReadingSource(whole.symbols(),
                named -> named.equals(held)
                        ? new ExpandedClauseResult.Unavailable(named) : whole.invariants().of(named));

        assertFalse(TypeCardinality.solve(declarations, shortOfOne, policy).everyRuleReached(),
                "a count that walked into a declaration whose rules could not be worked out has not"
                        + " read every rule, whatever numbers it arrived at");
    }

    /**
     * And what the query makes of a count that was short: nothing, said as nothing.
     *
     * <p>Reached the way a compile reaches it — a module whose own values are not well founded, so
     * its clauses are never expanded, beside one that spreads a declaration of it. The cycle is that
     * module's error and is reported there; what is held here is that the module beside it does not
     * come back with a count of what its types hold.
     */
    @Test
    void aCountShortOfARuleIsNotAnAnswerAboutWhatHasNoValue() {
        assertInstanceOf(UninhabitableTypes.WithNoValue.NotCounted.class,
                countedIn("let floor = floor"),
                "a type left uninhabitable by a rule nobody read would be reported as inhabited");
        assertInstanceOf(UninhabitableTypes.WithNoValue.Counted.class,
                countedIn("let floor = 1"),
                "and the same model is counted once that rule can be read");
    }

    /** What {@code Shapes.TypesWithNoValue} answers about the spreading module, with {@code floor}
     *  written as {@code written}. */
    private static UninhabitableTypes.WithNoValue countedIn(String written) {
        Compilation compilation = Compilation.ofSources(List.of("""
                module owner exposing ( Held )

                %s

                data Held = { n: Int }
                    invariant n >= floor
                """.formatted(written), """
                module app.rows exposing ( Row )

                import owner ( Held )

                data Row = { ...Held, m: Int }
                """), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        return compilation.db().ask(new Shapes.TypesWithNoValue("app.rows")).value();
    }
}
