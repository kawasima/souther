package souther.compiler.coverage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.check.Comparison;
import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.core.Core;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ModelOccurrence;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading of a body reads can be keyed by what the model states there.
 *
 * <p>A rule is read where the language's operations stand and a run through it is recorded where
 * they are expanded, so a reader that holds a fact about a rule and a reader that holds a recording
 * are reading different trees. What they join on has to name every comparison a reading reads, name
 * no two of them alike, and be the same value however the reading was reached. None of that is
 * something the types promise, and this asks the models.
 *
 * <p><b>Where the crossing is partial.</b> A construct written inside one of the language's own
 * operations is one the model states nothing at, and the operation stands in the tree a reading is
 * taken over — so such a construct is not in that tree to be read. The absence is on the side where
 * the operations have been expanded, and what says so is asked here rather than left to be read off
 * the code that answers it.
 *
 * <p><b>The population is every module the corpora hold, and models written for shapes no corpus
 * has.</b> A helper called twice, an operation of the language that evaluates a closure it was handed
 * twice, and a call of one whose own body compares something: measured without them, the questions
 * below are answered by walks that never met a second copy of anything, and one of them is answered
 * by a walk that never met the absence it is about.
 */
@Tag("population")
class WhatAReadingReadsCanBeKeyedByWhatTheModelStatesTest {

    /** One helper called twice: one written comparison, two copies of it in the tree that runs. */
    private static final String SPLICED = """
            module spliced

            let picked (n: Int): Bool = n >= 240

            behavior over : (a: Int, b: Int) -> Bool
            let over (a, b) = picked(a) && picked(b)
            """;

    /**
     * One comparison in a key an operation of the language asks twice: one construct of the model,
     * two places the tree that runs writes it.
     */
    private static final String ASKED_TWICE = """
            module askedtwice

            data Low
            data High

            behavior pick : (xs: List<Int>) -> Low | High
            let pick (xs) =
                if List.length(List.distinctBy(x -> x > 0, xs)) > 1 then High else Low
            """;

    /**
     * A comparison written inside one of the language's own operations, reached by calling it.
     *
     * <p>{@code List.isEmpty} compares a length against zero in its own body. The model states
     * nothing there — what it defines the meaning of is the operation — so this is what the crossing
     * comes back empty for, and a corpus that calls no such operation never puts the question.
     */
    private static final String STATES_NOTHING = """
            module statesnothing

            behavior blank : (xs: List<Int>) -> Bool
            let blank (xs) = List.isEmpty(xs)

            behavior sized : (xs: List<Int>) -> Bool
            let sized (xs) = List.length(xs) > 2
            """;

    /**
     * Every comparison a reading of the model reads states a construct of the model.
     *
     * <p>What lets the reading's side be keyed by {@link ModelOccurrence} at all. A comparison this
     * came back empty for is one a reading reads and has no name for on the model's side, and no key
     * chosen here would reach it.
     */
    @Test
    void everyComparisonAReadingReadsStatesAConstructOfTheModel() {
        List<String> stateNothing = new ArrayList<>();
        int read = 0;
        for (Read each : everyBody()) {
            for (ConstructOccurrence occurrence : comparisonsIn(each.readings())) {
                read++;
                if (ModelOccurrence.statedAt(occurrence).isEmpty()) {
                    stateNothing.add(each.where() + ": " + occurrence);
                }
            }
        }

        int met = read;
        assertTrue(read > 0, "no comparison of any reading was met at all");
        assertEquals(List.of(), stateNothing,
                () -> "of " + met + " comparisons the readings read, the model states nothing at "
                        + stateNothing.size() + ": " + stateNothing);
    }

    /**
     * And no two of them state the same one.
     *
     * <p>What lets a fact a reading establishes be filed under the construct of the model rather
     * than under a materialisation of it. Two comparisons of one reading under one name would be two
     * facts in one entry, and the reader that asked would be handed whichever was written last.
     *
     * <p>Asked of a module and not of a body, because what is keyed by this is carried past the body
     * it was read in.
     *
     * <p>Over every comparison the readings read and not over the ones that came back with a
     * construct. Selected by having one, this would be injective over whatever subset answered and
     * would say so however few of them did.
     */
    @Test
    void noTwoComparisonsOfOneReadingStateOneConstructOfTheModel() {
        List<String> shared = new ArrayList<>();
        List<String> stateNothing = new ArrayList<>();
        int read = 0;
        for (Module each : everyModule()) {
            Map<ModelOccurrence, String> met = new LinkedHashMap<>();
            for (Read body : each.bodies()) {
                for (ConstructOccurrence occurrence : comparisonsIn(body.readings())) {
                    read++;
                    Optional<ModelOccurrence> states = ModelOccurrence.statedAt(occurrence);
                    if (states.isEmpty()) {
                        stateNothing.add(body.where() + ": " + occurrence);
                        continue;
                    }
                    String already = met.putIfAbsent(states.get(), body.where() + ": " + occurrence);
                    if (already != null) {
                        shared.add(already + " and " + body.where() + ": " + occurrence
                                + " both state " + states.get());
                    }
                }
            }
        }

        assertTrue(read > 0, "no comparison of any reading was met at all");
        assertEquals(List.of(), stateNothing,
                () -> "a comparison of a reading states no construct of the model, so this was"
                        + " asked of fewer than the readings read: " + stateNothing);
        assertEquals(List.of(), shared,
                () -> "two comparisons of one reading state one construct of the model: " + shared);
    }

    /**
     * One occurrence states one construct of the model, wherever it is met.
     *
     * <p>Whether the crossing is a function of the occurrence alone. The walk that answers it reads
     * the copies the occurrence carries and nothing else, which is what the code says and not what
     * the models say — and a reader that held the answer beside the occurrence, or a numbering that
     * kept one, would be right only if this holds. Asked over every module of every corpus at once,
     * so an occurrence met in two compilations is asked twice.
     */
    @Test
    void oneOccurrenceStatesOneConstructOfTheModelWhereverItIsMet() {
        Map<ConstructOccurrence, Optional<ModelOccurrence>> answered = new LinkedHashMap<>();
        List<String> disagreed = new ArrayList<>();
        int asked = 0;
        for (Read each : everyBody()) {
            List<ConstructOccurrence> both = new ArrayList<>(comparisonsIn(each.readings()));
            comparisonNodesIn(List.of(each)).forEach(
                    node -> both.add(((Core.Binary) node).occurrence()));
            for (ConstructOccurrence occurrence : both) {
                asked++;
                Optional<ModelOccurrence> states = ModelOccurrence.statedAt(occurrence);
                Optional<ModelOccurrence> already = answered.putIfAbsent(occurrence, states);
                if (already != null && !already.equals(states)) {
                    disagreed.add(occurrence + " states " + already + " and " + states);
                }
            }
        }

        assertTrue(asked > 0, "no occurrence was asked at all");
        assertEquals(List.of(), disagreed,
                () -> "one occurrence states two constructs of the model: " + disagreed);
    }

    /**
     * The tree that runs holds comparisons the model states nothing at.
     *
     * <p>The control for the first question. A construct written inside one of the language's own
     * operations is materialised once per call of it and the model states none of them, so the
     * crossing is partial where it is taken over the tree that runs — and a corpus in which it was
     * total everywhere would answer the first question without the question having been put.
     */
    @Test
    void theTreeThatRunsHoldsComparisonsTheModelStatesNothingAt() {
        List<String> stateNothing = new ArrayList<>();
        for (Read each : everyBody()) {
            for (Core node : comparisonNodesIn(List.of(each))) {
                if (ModelOccurrence.statedAt(((Core.Binary) node).occurrence()).isEmpty()) {
                    stateNothing.add(each.where() + ": " + node.pos());
                }
            }
        }

        assertTrue(!stateNothing.isEmpty(),
                "no emitted comparison of any model stands where the model states nothing, so"
                        + " nothing here says the crossing is partial at all");
    }

    /**
     * A comparison the model states nothing at reaches the tree that runs and reaches no reading.
     *
     * <p>Which side the crossing is partial on, and the whole of why a reading may be keyed by what
     * the model states. An operation of the language stands in the tree a reading is taken over, so
     * a comparison written inside the operation's own body is not in that tree to be read — the
     * absence is on the emitted side, where the operation has been expanded into what it does.
     *
     * <p>Both halves are asked of the same behaviors, and a reading that read no comparison at all
     * would answer the second half without the question having been put. So a behavior writing a
     * comparison of its own stands beside the one that writes none: the readings walk comes back
     * with that one, which is what says it was walking.
     */
    @Test
    void aComparisonTheModelStatesNothingAtReachesNoReading() {
        List<String> emittedStateNothing = new ArrayList<>();
        List<String> readStateNothing = new ArrayList<>();
        List<String> read = new ArrayList<>();
        for (Read each : bodiesOf(List.of(STATES_NOTHING))) {
            for (Core node : comparisonNodesIn(List.of(each))) {
                if (ModelOccurrence.statedAt(((Core.Binary) node).occurrence()).isEmpty()) {
                    emittedStateNothing.add(each.where() + ": " + node.pos());
                }
            }
            for (ConstructOccurrence occurrence : comparisonsIn(each.readings())) {
                read.add(each.where() + ": " + occurrence);
                if (ModelOccurrence.statedAt(occurrence).isEmpty()) {
                    readStateNothing.add(each.where() + ": " + occurrence);
                }
            }
        }

        assertTrue(!emittedStateNothing.isEmpty(),
                "the tree that runs holds no comparison the model states nothing at, so this says"
                        + " nothing about which side the crossing is partial on");
        assertTrue(!read.isEmpty(),
                "the readings read no comparison of these behaviors at all, so their holding none"
                        + " the model states nothing at says nothing");
        assertEquals(List.of(), readStateNothing,
                () -> "a reading of a behavior calling an operation of the language reads a"
                        + " comparison the model states nothing at: " + readStateNothing);
    }

    /**
     * A helper called twice is two places the tree that runs writes one comparison, told apart by
     * the occurrence.
     *
     * <p>The control for the third question. Told by what the source wrote alone, the two calls
     * would be one occurrence and one of the two places would have no name of its own — which is the
     * answer that question would come back with if the copies were not part of what it reads.
     */
    @Test
    void aHelperCalledTwiceIsTwoPlacesToldApart() {
        Set<ConstructOccurrence> places = new LinkedHashSet<>();
        Set<ModelOccurrence> stated = new LinkedHashSet<>();
        for (Read each : bodiesOf(List.of(SPLICED))) {
            for (Core node : comparisonNodesIn(List.of(each))) {
                ConstructOccurrence occurrence = ((Core.Binary) node).occurrence();
                places.add(occurrence);
                ModelOccurrence.statedAt(occurrence).ifPresent(stated::add);
            }
        }

        assertEquals(2, places.size(),
                () -> "the helper's one comparison stands in a copy per call of it: " + places);
        assertEquals(2, stated.size(),
                () -> "and the model states one construct per copy: " + stated);
    }

    /**
     * Every place the tree that runs writes a comparison is one the occurrence it carries tells from
     * the others.
     *
     * <p>What lets the emitted side be keyed by what its nodes carry. The grain is the node: the
     * numbering hands out one place per comparison node it reaches, and a node reached twice on the
     * way through a body is one comparison written once. So two nodes under one occurrence are two
     * places a run could be recorded at with one name between them, and whichever of them a reader
     * asked about it would be told about the other.
     */
    @Test
    void everyPlaceTheTreeThatRunsWritesAComparisonIsToldFromTheOthers() {
        List<String> shared = new ArrayList<>();
        int places = 0;
        for (Module each : everyModule()) {
            Map<ConstructOccurrence, Core> met = new LinkedHashMap<>();
            for (Core node : comparisonNodesIn(each.bodies())) {
                places++;
                Core already = met.putIfAbsent(((Core.Binary) node).occurrence(), node);
                if (already != null) {
                    shared.add(already.pos() + " and " + node.pos() + " are two places under "
                            + ((Core.Binary) node).occurrence());
                }
            }
        }

        assertTrue(places > 0, "no comparison of any emitted body was met at all");
        assertEquals(List.of(), shared,
                () -> "two places the tree that runs writes a comparison share one occurrence: "
                        + shared);
    }

    /**
     * An operation of the language that asks a closure twice is two places the tree that runs writes
     * one construct of the model.
     *
     * <p>The control for the crossing being one to many, and for the question above being asked of a
     * population where a coarser key would answer differently. Where every construct of the model
     * has one place, a key of either grain does, and nothing said which was being measured.
     */
    @Test
    void anOperationAskingAClosureTwiceIsTwoPlacesOfOneConstruct() {
        Map<ModelOccurrence, Set<ConstructOccurrence>> places = new LinkedHashMap<>();
        for (Read each : bodiesOf(List.of(ASKED_TWICE))) {
            for (Core node : comparisonNodesIn(List.of(each))) {
                ConstructOccurrence occurrence = ((Core.Binary) node).occurrence();
                ModelOccurrence.statedAt(occurrence).ifPresent(states ->
                        places.computeIfAbsent(states, _ -> new LinkedHashSet<>())
                                .add(occurrence));
            }
        }

        assertEquals(List.of(1, 2),
                places.values().stream().map(Set::size).sorted().toList(),
                () -> "one construct of the model is written into the tree that runs twice and the"
                        + " other once: " + places);
    }

    /** One behavior of one module, as both readings of its body. */
    private record Read(String name, String behavior, Core readings, Core emitted) {

        String where() {
            return name + "." + behavior;
        }
    }

    /** One module, as every behavior both readings of whose body were built. Grouped, because what
     *  a reading is keyed by is carried past the body it was read in and met again in another. */
    private record Module(List<Read> bodies) {}

    /** Which comparisons of the model {@code body} holds, in the order met. */
    private static Set<ConstructOccurrence> comparisonsIn(Core body) {
        Set<ConstructOccurrence> out = new LinkedHashSet<>();
        walk(body, out::add);
        return out;
    }

    /**
     * Every node the emitted bodies write a comparison at, one per node.
     *
     * <p>Held by identity, because the grain the numbering works at is the node: a node reached
     * twice on the way through a body is one comparison written once, and counting it twice would
     * put a place in the population that the numbering never hands a name to.
     */
    private static List<Core> comparisonNodesIn(List<Read> bodies) {
        Map<Core, Boolean> met = new IdentityHashMap<>();
        List<Core> out = new ArrayList<>();
        for (Read each : bodies) {
            walkNodes(each.emitted(), node -> {
                if (met.put(node, Boolean.TRUE) == null) {
                    out.add(node);
                }
            });
        }
        return out;
    }

    private static void walk(Core e, Consumer<ConstructOccurrence> out) {
        walkNodes(e, node -> out.accept(((Core.Binary) node).occurrence()));
    }

    private static void walkNodes(Core e, Consumer<Core> out) {
        if (e instanceof Core.Binary binary && binary.occurrence() != null
                && binary.origin() != null && binary.origin().isWritten()
                && Comparison.of(binary).isPresent()) {
            out.accept(binary);
        }
        Core.forEachChild(e, child -> walkNodes(child, out));
    }

    private static List<Read> everyBody() {
        List<Read> out = new ArrayList<>();
        everyModule().forEach(each -> out.addAll(each.bodies()));
        return out;
    }

    private static List<Module> everyModule() {
        List<List<String>> sources = new ArrayList<>();
        ConformanceCorpus.all().forEach(corpus -> sources.add(corpus.sources()));
        sources.add(List.of(SPLICED));
        sources.add(List.of(ASKED_TWICE));
        sources.add(List.of(STATES_NOTHING));
        List<Module> out = new ArrayList<>();
        sources.forEach(each -> out.addAll(modulesOf(each)));
        return out;
    }

    private static List<Read> bodiesOf(List<String> sources) {
        List<Read> out = new ArrayList<>();
        modulesOf(sources).forEach(each -> out.addAll(each.bodies()));
        return out;
    }

    private static List<Module> modulesOf(List<String> sources) {
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        compilation.answerEverything();
        List<Module> out = new ArrayList<>();
        for (String module : compilation.modules()) {
            Bodies.Elaborated checked =
                    compilation.db().ask(new Bodies.Checked(module)).value();
            if (checked == null) {
                continue;
            }
            List<Read> bodies = new ArrayList<>();
            checked.analysisBodies().forEach((behavior, analysis) -> {
                Core emitted = checked.behaviorBodies().get(behavior);
                // Both readings or neither. A behavior with one of the two is one the crossing is
                // never taken over, and taking a question about the join over it would be asking
                // about a body only one side of the join has.
                if (emitted != null) {
                    bodies.add(new Read(module, behavior, analysis.core(), emitted));
                }
            });
            out.add(new Module(bodies));
        }
        assertTrue(!out.isEmpty(),
                () -> "a source set compiled to no module at all: " + compilation.errors());
        return out;
    }
}
