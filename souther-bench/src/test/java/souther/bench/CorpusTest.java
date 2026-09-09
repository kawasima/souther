package souther.bench;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.check.ChoicesRead;
import souther.compiler.query.Compilation;

import java.util.LinkedHashMap;
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

    /**
     * And what each of those compiles did with the choices the corpus states, taken as it is made.
     *
     * <p>Around the compile that happens rather than around a compile of its own. These are held
     * for the class, so a reading taken in one test method would be of whatever was left to do by
     * the time that method ran — which is nothing, wherever another method reached the corpus
     * first.
     */
    private static final Map<Corpus, ChoicesRead.Snapshot> READ = new LinkedHashMap<>();

    private static synchronized Compilation compiled(Corpus corpus) {
        return COMPILED.computeIfAbsent(corpus, each -> {
            ChoicesRead.Snapshot before = ChoicesRead.snapshot();
            Compilation compilation = each.compile();
            READ.put(each, ChoicesRead.snapshot().since(before));
            return compilation;
        });
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
        READ.clear();
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
     * That something a corpus states is a choice, and that settling it is work this compile did.
     *
     * <p>Of the corpora together and not of each of them. What a carried corpus is for is a model
     * somebody would write, and a rule stating alternatives is one thing such a model has rather
     * than something each of them must: required per corpus, the requirement would be met by
     * writing a choice into a model that has no use for one, which is the corpus answering for a
     * measurement instead of for an application.
     *
     * <p>What each of the fates costs is the generated measurement's to reach, and it is held to
     * reaching them ({@code EveryChoiceMeasurementReachesTheReadingItIsAboutTest}). What is claimed
     * here is only that the paths a whole-compile figure is reported over — {@code warm},
     * {@code phase}, {@code edit} — arrive at a choice at all. Without it those figures sit where
     * they are however much slower settling a choice becomes.
     *
     * <p>Not read off the sources. A count of {@code ||} in the text says a choice was written and
     * not that a reading took one in, and the two part exactly where this would stop being true.
     */
    @Test
    void someCorpusStatesAChoiceAndSettlingItIsWorkThisCompileDoes() {
        long stated = 0;
        long carried = 0;
        for (Corpus corpus : Corpus.all()) {
            compiled(corpus);
            stated += READ.get(corpus).stated();
            carried += READ.get(corpus).carriedToSettlement();
        }
        assertTrue(stated > 0,
                "no rule of any corpus states a choice, so nothing the compile measurements are"
                        + " taken over ever settles one");
        assertTrue(carried > 0,
                "every choice the corpora state was decided off the descriptions of its branches,"
                        + " so nothing reaches the settlement the measurements are taken over");
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
