package souther.bench;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.check.ChoicesRead;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every corpus still compiles. The benchmarks themselves are not run here — a shared machine
 * says nothing reliable about how long anything takes — but what they are measured against has to
 * keep working, and a language change that leaves a corpus behind would otherwise be found the next
 * time someone took a measurement and read a faster number as an improvement.
 *
 * <p>Compiling is the whole claim. Whether the compiler still answers what it did about a model this
 * size is the conformance corpus's, in {@code souther-compiler}, and looking for it here would find
 * nothing: these sources are timed, not read back, and every measure a report carries could move
 * without one of them failing to compile.
 */
@Tag("population")
class CorpusTest {

    /**
     * Each corpus compiled once for the class.
     *
     * <p>Three questions are asked of the same compiles and none of them changes one, so a compile
     * per question is the same answer worked out again — and a corpus is the size someone writes,
     * so working it out again is most of what this class costs.
     */
    private static final Map<Corpus, Compilation> COMPILED = new LinkedHashMap<>();

    private static synchronized Compilation compiled(Corpus corpus) {
        return COMPILED.computeIfAbsent(corpus, Corpus::compile);
    }

    /** What one action of a measurement did with the choices the corpora state. */
    private static ChoicesRead.Snapshot doing(Runnable action) {
        ChoicesRead.Snapshot before = ChoicesRead.snapshot();
        action.run();
        return ChoicesRead.snapshot().since(before);
    }

    /**
     * And given back when the class is done with them.
     *
     * <p>A fork runs its classes one after another and keeps the JVM, so what is held statically is
     * held for every class after this one as well. What is kept here is two answered compilations of
     * models the size somebody writes, with their classes materialised — a floor under the heap that
     * the rest of this module's tests would be running above for a saving that is this class's.
     */
    @AfterAll
    static void released() {
        COMPILED.clear();
    }

    @Test
    void everyCorpusCompiles() {
        for (Corpus corpus : Corpus.all()) {
            corpus.check(compiled(corpus));
        }
    }

    /**
     * That something here is still several sources compiled as one.
     *
     * <p>What a corpus is for is measuring the compiler against a model of the size someone writes,
     * and a model that size is written in several files that name each other. A corpus that lost
     * that — a file dropped, a corpus replaced by a smaller one — would go on compiling and go on
     * being timed, and every question about what happens between two modules would be asked of
     * inputs that have only one. The failure is silent by construction: nothing about a single-file
     * compile looks wrong.
     *
     * <p>What this does not claim: that a body here is spliced across a module. The corpora import
     * each other's types and none of them calls the other's helpers, so nothing measured here
     * exercises a body copied out of another module's file. That is held by
     * {@code ACopiedBodyIsReadAgainstAFileThisCompileHasTest} on a fixture written for it.
     */
    @Test
    void someCorpusIsSeveralSourcesHandedOverAsOneCompile() {
        for (Corpus corpus : Corpus.all()) {
            if (corpus.sources().size() < 2) {
                continue;
            }
            if (compiled(corpus).modules().size() >= 2) {
                return;
            }
        }
        throw new AssertionError("no corpus hands several sources naming several modules to one"
                + " compile: " + Corpus.all().stream()
                        .map(c -> c + " (" + c.sources().size() + " sources)").toList());
    }

    /**
     * That the whole-compile figures are taken over compiles that settle a choice.
     *
     * <p>Of the actions the figures are the times of, run here untimed
     * ({@link WholeCompile#warmRound}, {@link Phases#walk}). A compile of a corpus made some other
     * way is a compile this asks about and nothing reports, and the two would part the first time
     * one of them changed — which is the same defect as reporting a figure for a path nothing
     * arrives at, one level up.
     *
     * <p>Of the corpora together and not of each of them. What a carried corpus is for is a model
     * somebody would write, and a rule stating alternatives is one thing such a model has rather
     * than something each of them must: required per corpus, the requirement would be met by
     * writing a choice into a model that has no use for one, which is the corpus answering for a
     * measurement instead of for an application. What each of the fates costs is the generated
     * measurement's to reach, and it is held to reaching them
     * ({@code EveryChoiceMeasurementReachesTheReadingItIsAboutTest}).
     *
     * <p>Not read off the sources. A count of {@code ||} in the text says a choice was written and
     * not that a reading took one in, and the two part exactly where this would stop being true.
     */
    @Test
    void theWholeCompileFiguresAreTakenOverCompilesThatSettleAChoice() {
        long stated = 0;
        long carried = 0;
        for (Corpus corpus : Corpus.all()) {
            ChoicesRead.Snapshot warm = doing(() -> WholeCompile.warmRound(corpus));
            ChoicesRead.Snapshot phase = doing(() -> Phases.walk(corpus));
            stated += Math.min(warm.stated(), phase.stated());
            carried += Math.min(warm.carriedToSettlement(), phase.carriedToSettlement());
        }
        assertTrue(stated > 0,
                "a whole compile of these corpora reads no choice, so `warm` and `phase` sit where"
                        + " they are however much slower settling one becomes");
        assertTrue(carried > 0,
                "every choice these compiles read was decided off the descriptions of its branches,"
                        + " so the figures reach nothing the settlement does");
    }

    /**
     * And that settling a choice is work some timed edit does.
     *
     * <p>Some and not each. An edit figure is the cost of what that edit invalidated, so an edit
     * that leaves the rules of a declaration where they were reads no rule of it — which is the
     * store being incremental and not a gap. What would be a gap is every edit doing that, since
     * then no edit figure would answer for a choice at all, and a change to what an edit costs a
     * declaration stating alternatives would come back as no change.
     *
     * <p>So the edits are run rather than reasoned about. Which of them reaches a declaration
     * stating a choice depends on what the store invalidates and on which file the corpus writes
     * the rule in, and neither is a thing to write down here and have go quietly out of date.
     */
    @Test
    void settlingAChoiceIsWorkSomeTimedEditDoes() {
        List<String> reaching = new ArrayList<>();
        for (Corpus corpus : Corpus.all()) {
            for (Incremental.Edit edit : Incremental.edits(corpus).edits()) {
                if (doing(() -> edit.round().accept(0)).carriedToSettlement() > 0) {
                    reaching.add(corpus + " " + edit.name());
                }
            }
        }
        assertTrue(!reaching.isEmpty(),
                "no edit these figures are taken over settles a choice, so nothing in the edit"
                        + " series answers for what an edit costs a declaration that states one");
    }

    /** And a corpus of one source is one on purpose, not one that lost its files: it names one
     *  module, so there was never a second file for it to have lost. */
    @Test
    void aCorpusOfOneSourceNamesOneModule() {
        for (Corpus corpus : Corpus.all()) {
            if (corpus.sources().size() == 1) {
                assertTrue(compiled(corpus).modules().size() == 1,
                        () -> corpus + " is one source and names several modules");
            }
        }
    }
}
