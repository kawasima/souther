package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.SourceConstructOrigin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A comparison an author wrote is numbered, wherever in a condition the tree that runs put it.
 *
 * <p>What a site is for is saying that a run reached the comparison. A comparison with none cannot
 * be said to have run and cannot be said not to have — it is absent from the accounting rather than
 * unmet in it, which is the one answer no reader can act on and no count can be short by.
 *
 * <p>The condition of the fork inside a library combinator is not a comparison. A closure handed to
 * {@code List.filter} is inlined as a binding of the element with the author's own comparison under
 * it, so the condition is a {@code let} and the comparison is one level in. Numbered only through
 * the connectives, every comparison an author wrote inside a closure went unnumbered — measured on
 * {@code develop} at {@code 2130fb2f}, where both comparisons below came back with no site at all.
 *
 * <p>Two closures rather than one, because the fix has to keep them apart. They are the same fork of
 * the same library declaration, inlined twice; what tells them apart is which comparison each was
 * handed, and a site keyed on the fork would give them one number between them.
 */
class AComparisonIsNumberedWhereverItIsWrittenTest {

    private static final String MODULE = "example.people";

    private static final String MODEL = """
            module example.people

            data Age = Int
                invariant value >= 0
            data Person =
                { age: Age
                }

            behavior twice : (a: List<Person>, b: List<Person>) -> List<Person>
            let twice (a, b) =
                List.filter(x -> x.age.value >= 18, a)
                    ++ List.filter(y -> y.age.value >= 65, b)
            """;

    /** Every comparison of {@code twice}, by the source construct that wrote it. */
    private static Map<SourceConstructOrigin, ConstructOccurrence> comparisonsOfTwice() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(MODULE)).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get("twice");
        assertNotNull(body, "twice has a body");
        CoverageSites.Plan plan = checked.plan();
        Map<SourceConstructOrigin, ConstructOccurrence> out = new LinkedHashMap<>();
        for (Core each : comparisonsIn(body)) {
            Core.Binary comparison = (Core.Binary) each;
            plan.comparisons().occurrenceAt(comparison).filter(plan::instruments)
                    .ifPresent(site -> out.put(comparison.origin(), site));
        }
        return out;
    }

    private static List<Core> comparisonsIn(Core e) {
        List<Core> out = new ArrayList<>();
        collect(e, out);
        return out;
    }

    private static void collect(Core e, List<Core> out) {
        if (e instanceof Core.Binary) {
            out.add(e);
        }
        Core.forEachChild(e, child -> collect(child, out));
    }

    /** Both of them, and one number each. */
    @Test
    void aComparisonInsideAClosureHandedToACombinatorIsNumbered() {
        Map<SourceConstructOrigin, ConstructOccurrence> numbered = comparisonsOfTwice();
        assertEquals(2, numbered.size(),
                () -> "each closure's comparison is numbered, and they are two: " + numbered);
        assertEquals(2, Set.copyOf(numbered.values()).size(),
                () -> "and neither takes the other's number: " + numbered);
    }

    /** The two are one fork of one declaration inlined twice, and are still two comparisons. */
    @Test
    void twoClosuresOfOneCombinatorAreTwoComparisons() {
        Map<SourceConstructOrigin, ConstructOccurrence> numbered = comparisonsOfTwice();
        List<SourceConstructOrigin> written = numbered.keySet().stream()
                .filter(origin -> origin.module().equals(MODULE)).toList();
        assertEquals(2, written.size(),
                () -> "each is keyed by the construct the author's own module wrote, and not by the"
                        + " library's fork holding it: " + numbered.keySet());
    }
}
