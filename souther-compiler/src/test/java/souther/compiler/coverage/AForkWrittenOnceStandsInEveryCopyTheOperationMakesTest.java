package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ModelOccurrence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A fork the author wrote once stands in every copy the operation makes of it.
 *
 * <p>{@code List.distinctBy} asks its key twice, so a fork written inside the key is written into
 * the tree that runs twice. The two are two decisions — a run settles each on a value of its own —
 * and they are one fork of the model, which is what the author wrote.
 *
 * <p>What everything filed under a fork rests on. Told apart, the two are two places a run passes
 * and two ways a condition can be settled; read as one, a path that goes one way in the first copy
 * and the other way in the second reads as one condition settled two ways, which is no path at all
 * — and nothing reports that, because such a path is dropped rather than refused.
 */
class AForkWrittenOnceStandsInEveryCopyTheOperationMakesTest {

    /** One fork, written in a key an operation asks twice. */
    private static final String MODEL = """
            module m

            data Low
            data High

            behavior pick : (xs: List<Bool>) -> Low | High
            let pick (xs) =
                if List.length(List.distinctBy(x -> if x then 1 else 0, xs)) > 1
                then High
                else Low
            """;

    @Test
    void oneForkOfTheModelIsSeveralDecisionsInTheTreeThatRuns() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core emitted = checked.behaviorBodies().get("pick");

        List<ConstructOccurrence> forks = forksIn(emitted);
        assertEquals(forks.size(), new LinkedHashSet<>(forks).size(),
                () -> "every fork of the tree that runs is told from every other: " + forks);

        // The forks the model states, by which fork of it each is. A copy made inside a library
        // operation is that operation's and no fork this model states, so what is left is the two
        // the author wrote: the one this behavior tests and the one it wrote into the key.
        Map<ModelOccurrence, Set<ConstructOccurrence>> byModel = new LinkedHashMap<>();
        for (ConstructOccurrence each : forks) {
            ModelOccurrence.statedAt(each).ifPresent(stated ->
                    byModel.computeIfAbsent(stated, any -> new LinkedHashSet<>()).add(each));
        }

        assertEquals(List.of(1, 2),
                byModel.values().stream().map(Set::size).sorted().toList(),
                () -> "one fork is written into the tree that runs twice and the other once: "
                        + byModel);
    }

    /** Which fork each fork of {@code body} is, in the order met. */
    private static List<ConstructOccurrence> forksIn(Core body) {
        List<ConstructOccurrence> out = new ArrayList<>();
        walk(body, out);
        return out;
    }

    private static void walk(Core e, List<ConstructOccurrence> out) {
        switch (e) {
            case Core.If iff -> out.add(iff.occurrence());
            case Core.Match match -> out.add(match.occurrence());
            case Core.IfConstructed built -> out.add(built.occurrence());
            default -> { }
        }
        Core.forEachChild(e, child -> walk(child, out));
    }
}
