package souther.compiler.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.check.AnalysisBody;
import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ExpansionLineage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The comparisons of a checked body are told apart by what the source wrote and which copy they
 * stand in.
 *
 * <p>Of the tree the checker produced, which is where the occurrence has to arrive. What the
 * inlining pass settled is on {@link souther.compiler.ast.Hir.Expansion}, and elaboration is what
 * carries it down onto the nodes a reader below holds — so a walk of the expansions says nothing
 * about whether a reader of a body can tell two copies of a comparison apart.
 *
 * <p>Both representations, walked apart. Which of them a comparison stands in is what an occurrence
 * must not turn on: the tree the backend emits has the language's own operations expanded into what
 * they do and the tree the analysis reads keeps them standing, so a comparison written inside one of
 * those operations stands in a copy in the first and is not in the second at all.
 */
@Tag("population")
class AComparisonOfACopyIsNotTheOneItWasCopiedFromTest {

    /** One helper called twice: one written comparison, two copies. */
    private static final String SPLICED = """
            module demo

            let picked (n: Int): Bool = n >= 240

            behavior over : (a: Int, b: Int) -> Bool
            let over (a, b) = picked(a) && picked(b)
            """;

    /** A comparison written in a block handed to one of the language's own operations. */
    private static final String COMBINATOR = """
            module combinator

            behavior over : (xs: List<Int>) -> Bool
            let over (xs) = List.all(x -> x >= 240, xs)
            """;

    /**
     * No two comparisons of one body share an occurrence, over everything this repository compiles.
     *
     * <p>What every reader below joins on. Two comparisons under one occurrence are two places a
     * rule read off one, a line drawn on one and a run recorded at one cannot be told apart.
     */
    @Test
    void noTwoComparisonsOfOneBodyShareAnOccurrence() {
        int[] comparisons = new int[1];
        List<String> shared = new ArrayList<>();
        for (Map.Entry<String, Core> body : everyBody()) {
            Map<ConstructOccurrence, Set<Core.Binary>> at = comparisonsIn(body.getValue());
            comparisons[0] += at.size();
            at.forEach((which, nodes) -> {
                if (nodes.size() > 1) {
                    shared.add(body.getKey() + " " + which + ": " + nodes.size());
                }
            });
        }

        assertTrue(comparisons[0] > 0, "no comparison was met at all, so this says nothing");
        assertEquals(List.of(), shared,
                () -> "two comparisons of one body share an occurrence: " + comparisons[0]
                        + " occurrences walked");
    }

    /**
     * A helper called twice holds its comparison twice, and the two are two occurrences of one
     * written comparison.
     *
     * <p>Both halves at once: the origins are equal, so what the source wrote is one thing, and the
     * occurrences are not, so the copies are two. Either half alone is a claim the other refutes —
     * two origins would say the author wrote two comparisons, and one occurrence would say the two
     * calls are one place.
     */
    @Test
    void aHelperCalledTwiceHoldsTwoOccurrencesOfOneWrittenComparison() {
        for (Map.Entry<String, Core> body : bodiesOf(List.of(List.of(SPLICED)))) {
            if (!body.getKey().equals("over")) {
                continue;
            }
            List<ConstructOccurrence> picked = new ArrayList<>();
            comparisonsIn(body.getValue()).keySet().forEach(which -> {
                if (wroteIt(which, "demo", "picked")) {
                    picked.add(which);
                }
            });

            assertEquals(2, picked.size(),
                    () -> "the helper's one comparison stands once per call of it: " + picked);
            assertEquals(1, picked.stream().map(ConstructOccurrence::origin).distinct().count(),
                    () -> "and the two are copies of one comparison the author wrote: " + picked);
            assertEquals(2, picked.stream().map(ConstructOccurrence::lineage).distinct().count(),
                    () -> "and the copies are told apart by the calls they were made at: " + picked);
        }
    }

    /**
     * An occurrence is of one tree, and the trees part where one expands an operation of the
     * language and the other leaves it standing.
     *
     * <p>What this value is and is not. {@code List.all} is expanded where the backend emits from
     * and stands where the analysis reads, so a comparison the author wrote inside the block handed
     * to it stands in two copies in the first tree and in none in the second. Both are true of the
     * tree they are of, and an occurrence that read the same in both would be one of them describing
     * the other's materialisation.
     *
     * <p>So what the two trees agree about is what the source wrote, and nothing here joins them on
     * more than that. Which model occurrence they are two readings of is a further question, and the
     * answer to it is not this value.
     */
    @Test
    void anOccurrenceIsOfTheTreeItStandsIn() {
        Map<String, Core> emitted = new LinkedHashMap<>();
        Map<String, Core> analysis = new LinkedHashMap<>();
        Compilation compilation = Compilation.ofSources(List.of(COMBINATOR), ModulePath.EMPTY);
        compilation.answerEverything();
        for (String module : compilation.modules()) {
            Bodies.Elaborated checked =
                    compilation.db().ask(new Bodies.Checked(module)).value();
            if (checked == null) {
                continue;
            }
            emitted.putAll(checked.behaviorBodies());
            checked.analysisBodies().forEach((name, body) -> {
                if (body != null) {
                    analysis.put(name, body.core());
                }
            });
        }

        Set<ConstructOccurrence> inEmitted = comparisonsIn(emitted.get("over")).keySet();
        Set<ConstructOccurrence> inAnalysis = comparisonsIn(analysis.get("over")).keySet();

        List<ConstructOccurrence> wroteItEmitted = inEmitted.stream()
                .filter(which -> wroteIt(which, "combinator", "over"))
                .toList();
        List<ConstructOccurrence> wroteItAnalysis = inAnalysis.stream()
                .filter(which -> wroteIt(which, "combinator", "over"))
                .toList();

        assertEquals(1, wroteItEmitted.size(),
                () -> "the author wrote one comparison: " + inEmitted);
        assertEquals(1, wroteItAnalysis.size(),
                () -> "and the analysis reads that one: " + inAnalysis);
        // The same construct of the source, so the trees agree about what was written.
        assertEquals(wroteItEmitted.get(0).origin(), wroteItAnalysis.get(0).origin(),
                "the two trees hold the same written comparison");
        // And not the same occurrence, because it stands in copies in one tree and in none in the
        // other. Stated the strong way round: the analysis reads it where it was written, and the
        // emitted tree reads it inside what expanding the operation made.
        assertEquals(ExpansionLineage.ORIGINAL, wroteItAnalysis.get(0).lineage(),
                "the analysis reads the comparison where the author wrote it");
        assertTrue(!(wroteItEmitted.get(0).lineage() instanceof ExpansionLineage.Original),
                () -> "and the emitted tree reads it inside the copies expanding the operation"
                        + " made: " + wroteItEmitted);
        // And the operation's own body brings comparisons the analysis never sees, which is what
        // says the two trees were not made to agree by dropping the difference between them.
        assertTrue(inEmitted.size() > inAnalysis.size(),
                () -> "the expanded operation brought no comparison of its own. emitted: "
                        + inEmitted + ", analysis: " + inAnalysis);
    }

    /** Whether {@code which} is a comparison the body of {@code module.definition} wrote — asked of
     *  the owner rather than of how it is spelled. */
    private static boolean wroteIt(ConstructOccurrence which, String module, String definition) {
        return which.origin().owner() instanceof souther.compiler.types.WrittenOwner.Body body
                && body.module().equals(module) && body.definition().equals(definition);
    }

    /** Every comparison of {@code body}, by occurrence, with the distinct nodes at each. */
    private static Map<ConstructOccurrence, Set<Core.Binary>> comparisonsIn(Core body) {
        Map<ConstructOccurrence, Set<Core.Binary>> out = new LinkedHashMap<>();
        walk(body, out);
        return out;
    }

    private static void walk(Core e, Map<ConstructOccurrence, Set<Core.Binary>> out) {
        // Written, because a comparison this compiler composed is one no rule is read off and no
        // occurrence names: it stands where it was made rather than once per call of a body.
        if (e instanceof Core.Binary binary && binary.origin() != null
                && binary.origin().isWritten()) {
            out.computeIfAbsent(binary.occurrence(),
                    _ -> Collections.newSetFromMap(new IdentityHashMap<>())).add(binary);
        }
        Core.forEachChild(e, child -> walk(child, out));
    }

    private static List<Map.Entry<String, Core>> everyBody() {
        List<List<String>> sources = new ArrayList<>();
        ConformanceCorpus.all().forEach(corpus -> sources.add(corpus.sources()));
        sources.add(List.of(SPLICED));
        sources.add(List.of(COMBINATOR));
        return bodiesOf(sources);
    }

    /** Every body of every module of {@code sources}, in both representations. */
    private static List<Map.Entry<String, Core>> bodiesOf(List<List<String>> sources) {
        List<Map.Entry<String, Core>> out = new ArrayList<>();
        for (List<String> each : sources) {
            Compilation compilation = Compilation.ofSources(each, ModulePath.EMPTY);
            compilation.answerEverything();
            int before = out.size();
            for (String module : compilation.modules()) {
                Bodies.Elaborated checked =
                        compilation.db().ask(new Bodies.Checked(module)).value();
                if (checked == null) {
                    continue;
                }
                out.addAll(checked.behaviorBodies().entrySet());
                for (Map.Entry<String, AnalysisBody> read : checked.analysisBodies().entrySet()) {
                    if (read.getValue() != null) {
                        out.add(Map.entry(read.getKey(), read.getValue().core()));
                    }
                }
            }
            assertTrue(out.size() > before,
                    () -> "a source set compiled to no body at all: " + compilation.errors());
        }
        return out;
    }
}
