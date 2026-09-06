package souther.compiler.query;

import souther.compiler.values.Realization;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A machine made of a plan is a fact about the plan, and the store answers for it once however many
 * readings ask.
 *
 * <p>A declaration with a pattern rule is read for every question that reaches it — every
 * construction in a body, every input a behavior takes, the count of what the module's types hold.
 * Each reading used to plan the pattern and build its machine again. What is held here is that the
 * machine is a question of the store, keyed by the plan: asked from anywhere, it is there once, and
 * the reading that asks borrows it rather than paying for it.
 *
 * <p>Read off what the store holds rather than off how long a compile took. A machine built twice
 * costs time and shows up in no answer, which is the one way this could regress with every other
 * test green.
 */
class AMachineMadeOfAPlanIsLentToEveryReadingThatAsksForItTest {

    /** One pattern-ruled declaration, constructed in two behaviors and taken as input by a third. */
    private static final String PATTERNED = """
            module example.lent

            data Code = String
                invariant String.matches("[A-Z]{2}[0-9]{3}", value)

            data Tagged = { code: Code, n: Int }

            behavior first : (n: Int) -> Tagged
                constructs Tagged
            let first (n) = Tagged { code = Code("AB123"), n = n }

            behavior second : (n: Int) -> Tagged
                constructs Tagged
            let second (n) = Tagged { code = Code("CD456"), n = n + 1 }

            behavior third : (t: Tagged) -> Int
            let third (t) = t.n
            """;

    /** The same shape with nothing said about a string, so no machine is ever asked for. */
    private static final String UNPATTERNED = """
            module example.unlent

            data Code = String

            data Tagged = { code: Code, n: Int }

            behavior first : (n: Int) -> Tagged
                constructs Tagged
            let first (n) = Tagged { code = Code("AB123"), n = n }

            behavior third : (t: Tagged) -> Int
            let third (t) = t.n
            """;

    @Test
    void thePatternsMachineIsOneAnswerOfTheStore() {
        Compilation compilation = Compilation.ofSource(PATTERNED, "Main");
        compilation.answerEverything();
        Db db = compilation.db();

        List<Machines.Realized> realized = realizedIn(db);
        assertFalse(realized.isEmpty(), "a pattern rule is read, and its machine is asked for");
        for (Machines.Realized each : realized) {
            assertTrue(db.ask(each).value() instanceof Realization.Exact,
                    () -> "a plan of this model is affordable and comes to a set: " + each);
        }
        assertFalse(extentsIn(db).isEmpty(),
                "where the strings the rule admits stop is asked for, and answered once");
    }

    @Test
    void aModelThatStatesNothingAboutAStringAsksForNoMachine() {
        // The control for the assertion above: the keys are found by walking the store, and a walk
        // that found a key for no reason would find one here as well.
        Compilation compilation = Compilation.ofSource(UNPATTERNED, "Main");
        compilation.answerEverything();

        assertEquals(List.of(), realizedIn(compilation.db()),
                "nothing plans a machine where no rule names a pattern");
        assertEquals(List.of(), extentsIn(compilation.db()),
                "and nothing asks where the strings of a set stop");
    }

    @Test
    void aBodyEditedLeavesTheMachinesWhereTheyWere() {
        // A machine is about a plan, and a body changes no plan. So an edit to a body — which reads
        // the declaration again for every construction it holds — finds the answers it had and adds
        // nothing to them.
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("f0", PATTERNED);
        Compilation compilation = Compilation.ofDocuments(byId, Set.of(),
                souther.compiler.meta.ModulePath.EMPTY);
        compilation.diagnostics();
        Db db = compilation.db();
        List<Machines.Realized> before = realizedIn(db);
        List<Machines.Extent> extentsBefore = extentsIn(db);

        byId.put("f0", PATTERNED + """

                behavior fourth : (n: Int) -> Tagged
                    constructs Tagged
                let fourth (n) = Tagged { code = Code("EF789"), n = n + 2 }
                """);
        compilation.update(byId, Set.of());
        compilation.diagnostics();

        assertEquals(before, realizedIn(db),
                "the machines a module's rules come to are the same after a body is added");
        assertEquals(extentsBefore, extentsIn(db),
                "and so is where the strings they admit stop");
        for (Machines.Realized each : before) {
            assertTrue(db.isComputed(each), () -> "still held: " + each);
        }
    }

    private static List<Machines.Realized> realizedIn(Db db) {
        return db.everyAnswer().keySet().stream()
                .filter(Machines.Realized.class::isInstance)
                .map(Machines.Realized.class::cast)
                .toList();
    }

    private static List<Machines.Extent> extentsIn(Db db) {
        return db.everyAnswer().keySet().stream()
                .filter(Machines.Extent.class::isInstance)
                .map(Machines.Extent.class::cast)
                .toList();
    }
}
