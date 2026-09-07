package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ModelOccurrence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A comparison the author wrote once is watched wherever the operation evaluates it.
 *
 * <p>{@code List.distinctBy} asks its key twice — once to see whether the key was already met and
 * once to record it — so a comparison written inside the key is written into the tree that runs
 * twice. The model states one rule all the same: which of the two applications ran is the
 * operation's business, and the author wrote one comparison.
 *
 * <p><b>Written for the shape rather than found in a corpus.</b> The crossing from what a model
 * states to where a run through it is written down was built holding one place per construct, and
 * the measurement that said so walked the models this work happened to have. None of them called an
 * operation that evaluates a closure twice, so the first model that did stopped the compile. What
 * the library does is read off the library, and this holds the crossing to it.
 *
 * <p>Three ways of breaking it and one test. Refusing the second materialisation fails, because the
 * join comes back with two; keeping only the last fails, for the same reason; and telling the two
 * apart by where inside the operation they stand fails, because the model would then state two
 * rules where the author wrote one.
 */
class AComparisonWrittenOnceIsWatchedWhereverTheOperationEvaluatesItTest {

    /** One comparison, written in a key an operation asks twice. */
    private static final String MODEL = """
            module m

            data Low
            data High

            behavior pick : (xs: List<Int>) -> Low | High
            let pick (xs) =
                if List.length(List.distinctBy(x -> x > 0, xs)) > 1 then High else Low
            """;

    @Test
    void oneRuleOfTheModelIsSeveralPlacesARunThroughItIsWatched() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core analysis = checked.analysisBodies().get("pick").core();
        Core emitted = checked.behaviorBodies().get("pick");

        // The rules the author wrote, read where the language's operations stand. `x > 0` is one of
        // them; the fork's own `> 1` is the other, and the key's is the one this is about.
        List<ModelOccurrence> stated = new ArrayList<>();
        for (ConstructOccurrence each : comparisonsIn(analysis)) {
            ModelOccurrence.statedAt(each).ifPresent(stated::add);
        }
        assertEquals(stated.size(), new LinkedHashSet<>(stated).size(),
                () -> "the analysis reads each rule once: " + stated);

        ComparisonEmissionIndex index =
                ComparisonEmissionIndex.ofBody("pick", emitted, checked.plan());
        List<Integer> watched = stated.stream().map(each -> index.madeFor(each).size()).toList();

        assertEquals(List.of(1, 2), watched.stream().sorted().toList(),
                () -> "one rule is written into the tree that runs twice and the other once: "
                        + stated);
    }

    /** Which comparison of the model each comparison of {@code body} is, in the order met. */
    private static Set<ConstructOccurrence> comparisonsIn(Core body) {
        Set<ConstructOccurrence> out = new LinkedHashSet<>();
        walk(body, out);
        return out;
    }

    private static void walk(Core e, Set<ConstructOccurrence> out) {
        if (e instanceof Core.Binary binary && binary.occurrence() != null
                && binary.origin() != null && binary.origin().isWritten()
                && souther.compiler.check.Comparison.of(binary).isPresent()) {
            out.add(binary.occurrence());
        }
        Core.forEachChild(e, child -> walk(child, out));
    }
}
