package souther.compiler.check;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ExpansionLineage;
import souther.compiler.types.SourceConstructOrigin;

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
 * What the source wrote and which copy it is in tell one construct of a body from every other.
 *
 * <p>What an occurrence rests on ({@link ConstructOccurrence}). A rule read off a construct, a line
 * drawn on it and a run recorded at it join on that pair, so two constructs sharing one would be two
 * places a reader cannot tell apart, and one construct under two would be a reading of it that never
 * reaches the other.
 *
 * <p>Measured per copy and not per body. One helper called twice from one body is two copies of
 * every construct in it, and that is what the copy half is for rather than a collision — so a
 * measure over the body alone would report the thing the design exists to make possible.
 *
 * <p>Read off {@link Hir.Expansion}, which is the expansion as a node and carries where its copy is.
 * The lineage is built here by descending, the way every reader below builds it, so what is measured
 * is the identity the compiler hands out rather than a second one written for the measure.
 *
 * <p><b>Nothing here says which kinds of site the models happen to hold.</b> What may name a copy is
 * settled by the projection that makes one ({@code HelperInliner}), which refuses an application it
 * cannot put in the source's words; a test pinning the kinds this repository's models reach would
 * make a corpus into the rule, and would go red for a model that is fine.
 */
@Tag("population")
class AWrittenConstructIsMaterialisedOnceUnderOneExpansionTest {

    /**
     * One helper called twice from one body.
     *
     * <p>Its comparison is one construct the source wrote and two in the tree, under two copies. A
     * lineage that did not tell the two calls apart reports one of them, so this failing is what
     * says the measure below has something to measure.
     */
    private static final String SPLICED = """
            module demo

            let picked (n: Int): List<Int> = [ n | n >= 240 ]

            behavior over : (a: Int, b: Int) -> List<Int>
            let over (a, b) = picked(a) ++ picked(b)
            """;

    /**
     * A comparison handed to one of the language's own combinators.
     *
     * <p>Where the two representations part. What the backend emits has {@code List.all} expanded
     * into the fold it is, so the comparison stands inside that copy; what the analysis reads keeps
     * the operation standing, so it stands at the call. Without this the measure runs over bodies
     * the two representations agree about, and comes back saying the case it is for is fine.
     */
    private static final String COMBINATOR = """
            module combinator

            behavior over : (xs: List<Int>) -> Bool
            let over (xs) = List.all(x -> x >= 240, xs)
            """;

    /** What one walk of one body in one representation found. */
    private static final class Found {

        /** The distinct nodes at each occurrence. By identity: two nodes that read alike are two
         *  materialisations, and one node reached twice is one. */
        private final Map<ConstructOccurrence, Set<Hir.Binary>> at = new LinkedHashMap<>();

        private final String where;

        Found(String where) {
            this.where = where;
        }

        void materialised(ConstructOccurrence which, Hir.Binary node) {
            at.computeIfAbsent(which,
                    _ -> Collections.newSetFromMap(new IdentityHashMap<>())).add(node);
        }
    }

    @Test
    void aWrittenConstructIsMaterialisedOnceInOneCopy() {
        List<Found> walks = everyInlinedBody();

        List<String> twice = new ArrayList<>();
        int[] occurrences = new int[1];
        for (Found found : walks) {
            occurrences[0] += found.at.size();
            found.at.forEach((which, nodes) -> {
                if (nodes.size() > 1) {
                    twice.add(found.where + " " + which + ": " + nodes.size());
                }
            });
        }

        assertTrue(!walks.isEmpty(), "no body was walked at all, so this says nothing");
        assertTrue(occurrences[0] > 0,
                "no written construct was met at all, so this says nothing");
        assertEquals(List.of(), twice,
                () -> "two constructs of one body share an occurrence, so what wrote them and which"
                        + " copy they are in does not tell them apart. Walks: " + walks.size()
                        + ", occurrences: " + occurrences[0]);
    }

    /** And a construct standing in two copies is two occurrences, which is what the measure above
     *  is measuring the absence of within one. */
    @Test
    void oneConstructCalledTwiceStandsInTwoCopies() {
        boolean twoCopies = false;
        Map<SourceConstructOrigin, Integer> seen = new LinkedHashMap<>();
        for (Found found : inlinedBodiesOf(List.of(List.of(SPLICED)))) {
            Map<SourceConstructOrigin, Integer> copiesOf = new LinkedHashMap<>();
            found.at.keySet().forEach(which -> copiesOf.merge(which.origin(), 1, Integer::sum));
            twoCopies |= copiesOf.values().stream().anyMatch(copies -> copies > 1);
            copiesOf.forEach((origin, copies) -> seen.merge(origin, copies, Integer::max));
        }

        assertTrue(twoCopies,
                () -> "the guard of the helper called twice is one occurrence, so the lineage does"
                        + " not tell two calls of it apart: " + seen);
    }

    /**
     * And the bodies walked include one the two representations expand differently.
     *
     * <p>What says the measure met the case it is for. The operation stands in one representation
     * and is expanded in the other, so the comparison the author wrote in the block handed to it is
     * under a copy in one and under none in the other.
     */
    @Test
    void thePopulationReachesAComparisonInsideAnOperationOfTheLanguage() {
        List<Found> walks = inlinedBodiesOf(List.of(List.of(COMBINATOR)));

        List<String> copied = new ArrayList<>();
        List<String> asWritten = new ArrayList<>();
        for (Found found : walks) {
            found.at.keySet().forEach(which ->
                    (which.lineage() instanceof ExpansionLineage.Original ? asWritten : copied)
                            .add(found.where + " " + which));
        }

        assertTrue(!copied.isEmpty(),
                () -> "no comparison stands in a copy, so the operation was expanded nowhere and the"
                        + " two representations were never made to differ: " + asWritten);
        assertTrue(!asWritten.isEmpty(),
                () -> "no comparison stands as written, so the operation was expanded in both"
                        + " representations: " + copied);
    }

    private static List<Found> everyInlinedBody() {
        List<List<String>> sources = new ArrayList<>();
        ConformanceCorpus.all().forEach(corpus -> sources.add(corpus.sources()));
        sources.add(List.of(SPLICED));
        sources.add(List.of(COMBINATOR));
        return inlinedBodiesOf(sources);
    }

    /**
     * Every body of every module of {@code sources}, in both representations.
     *
     * <p>Both, and walked apart. Which of them a construct stands in is what an occurrence must not
     * turn on, so what is asked here is whether either materialises a construct twice in one copy;
     * whether the two agree with each other is the question after this one, and gathering them would
     * answer this one with the walk's own doing.
     */
    private static List<Found> inlinedBodiesOf(List<List<String>> sources) {
        List<Found> out = new ArrayList<>();
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
                Lower.Lowered lowered =
                        compilation.db().ask(new Bodies.Lowering(module)).value();
                if (lowered != null) {
                    for (Hir.FnDef fn : lowered.lowered().fns()) {
                        out.add(walked("emitted " + module + "." + fn.name(), fn.writtenBody()));
                    }
                }
                for (String behavior : checked.behaviorBodies().keySet()) {
                    var discharge = compilation.db()
                            .ask(new Bodies.BodyForInvariantDischarge(module, behavior)).value();
                    if (discharge != null) {
                        out.add(walked("analysis " + module + "." + behavior,
                                discharge.value().writtenBody()));
                    }
                }
            }
            assertTrue(out.size() > before,
                    () -> "a source set compiled to no body at all: " + compilation.errors());
        }
        return out;
    }

    private static Found walked(String where, Hir.Expr body) {
        Found found = new Found(where);
        walk(body, ExpansionLineage.ORIGINAL, found);
        return found;
    }

    private static void walk(Hir.Expr e, ExpansionLineage copy, Found into) {
        if (e instanceof Hir.Binary binary && binary.origin() != null
                && binary.origin().isWritten()) {
            into.materialised(new ConstructOccurrence(binary.origin(), copy), binary);
        }
        // The body of an expansion is the copy; its arguments are the caller's expressions and stand
        // where the call does. Walked under one lineage, an argument would be filed inside the
        // callee, and a construct written at the call site would come out standing in a body it is
        // not in.
        if (e instanceof Hir.Expansion expansion) {
            expansion.bound().forEach(bound -> walk(bound.value(), copy, into));
            expansion.given().forEach(given -> walk(given.value(), copy, into));
            walk(expansion.body(), copy.copiedInto(expansion.callee(), expansion.at()), into);
            return;
        }
        Hir.forEachChild(e, child -> walk(child, copy, into));
    }
}
