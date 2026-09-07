package souther.compiler.coverage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.core.Core;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ModelOccurrence;
import souther.compiler.types.WrittenOwner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each construct of the model the emitted bodies hold is asked for where a run through it is
 * recorded, and answered once or not at all.
 *
 * <p>What {@link ComparisonEmissionIndex} is for: a rule is read where the language's operations
 * stand and a run is recorded where they are expanded, so the two are read off different trees and
 * the construct of the model is what they agree about. Keyed by anything the emitted walk handed
 * out, the reading that states rules would have to hold a name it has no way to make.
 *
 * <p>Empty is an answer here. A comparison behind an abort is one the emitter numbers nothing for,
 * and what the index says of it is that — not that no run answers through it, and not that its
 * arrival could not be projected.
 */
@Tag("population")
class WhereARunThroughAConstructOfTheModelIsRecordedTest {

    /** One helper called twice: two constructs of the model, and two places a run is recorded. */
    private static final String SPLICED = """
            module demo

            let picked (n: Int): Bool = n >= 240

            behavior over : (a: Int, b: Int) -> Bool
            let over (a, b) = picked(a) && picked(b)
            """;

    /**
     * Every comparison the emitted bodies hold is one construct of the model, and each is answered
     * about once.
     */
    @Test
    void eachConstructOfTheModelIsAskedForOnePlace() {
        Map<String, Integer> answered = new TreeMap<>();
        int[] constructs = new int[1];
        for (Compiled each : everyModule()) {
            ComparisonEmissionIndex index =
                    ComparisonEmissionIndex.of(each.bodies(), each.plan());
            for (ModelOccurrence states : constructsIn(each.bodies())) {
                constructs[0]++;
                answered.merge(index.madeFor(states).stream()
                        .anyMatch(one -> one.site().isPresent()) ? "one place" : "none", 1,
                        Integer::sum);
            }
        }

        assertTrue(constructs[0] > 0, "no construct of the model was met at all");
        // Both answers are real: a corpus with only the first would say nothing about the second
        // being an answer rather than a gap, and one with only the second would say the index
        // answers nothing at all.
        assertEquals(List.of("none", "one place"), List.copyOf(answered.keySet()),
                () -> "the emitted bodies hold constructs of only one kind, so nothing here says"
                        + " what the index answers: " + answered);
    }

    /**
     * A helper called twice is two constructs of the model, and the index answers about each.
     *
     * <p>The control for the key. Keyed by what the source wrote alone, the two calls would be one
     * entry and one of the two places a run is recorded would be reachable from nothing.
     */
    @Test
    void aHelperCalledTwiceIsTwoConstructsWithTwoPlaces() {
        Set<ComparisonEmissionSite> places = new LinkedHashSet<>();
        List<ModelOccurrence> picked = new ArrayList<>();
        for (Compiled each : compiled(List.of(SPLICED))) {
            ComparisonEmissionIndex index =
                    ComparisonEmissionIndex.of(each.bodies(), each.plan());
            for (ModelOccurrence states : constructsIn(each.bodies())) {
                if (states.origin().owner() instanceof WrittenOwner.Body body
                        && body.definition().equals("picked")) {
                    picked.add(states);
                    index.madeFor(states).forEach(one -> one.site().ifPresent(places::add));
                }
            }
        }

        assertEquals(2, picked.size(),
                () -> "the helper's one comparison is one construct of the model per call of it: "
                        + picked);
        assertEquals(2, places.size(),
                () -> "and each of them is asked for a place of its own: " + places);
    }

    /** Which module's bodies, and what numbered them. */
    private record Compiled(ModuleBodies bodies, CoverageSites.Plan plan) {}

    private static Set<ModelOccurrence> constructsIn(ModuleBodies of) {
        Set<ModelOccurrence> out = new LinkedHashSet<>();
        of.bodies().values().forEach(body -> walk(body, out));
        return out;
    }

    private static void walk(Core e, Set<ModelOccurrence> out) {
        if (e instanceof Core.Binary binary && binary.origin() != null
                && binary.origin().isWritten()) {
            ModelOccurrence.statedAt(binary.occurrence()).ifPresent(out::add);
        }
        Core.forEachChild(e, child -> walk(child, out));
    }

    private static List<Compiled> everyModule() {
        List<List<String>> sources = new ArrayList<>();
        ConformanceCorpus.all().forEach(corpus -> sources.add(corpus.sources()));
        sources.add(List.of(SPLICED));
        List<Compiled> out = new ArrayList<>();
        sources.forEach(each -> out.addAll(compiled(each)));
        return out;
    }

    private static List<Compiled> compiled(List<String> sources) {
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        compilation.answerEverything();
        List<Compiled> out = new ArrayList<>();
        for (String module : compilation.modules()) {
            Bodies.Elaborated checked =
                    compilation.db().ask(new Bodies.Checked(module)).value();
            if (checked == null) {
                continue;
            }
            out.add(new Compiled(
                    new ModuleBodies(module, new LinkedHashMap<>(checked.behaviorBodies())),
                    checked.plan()));
        }
        assertTrue(!out.isEmpty(),
                () -> "a source set compiled to no module at all: " + compilation.errors());
        return out;
    }
}
