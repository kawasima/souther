package souther.compiler.check;

import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every declaration the corpora import states one thing, whichever module asks.
 *
 * <p>The same property as
 * {@link WhatADeclarationsClausesStateIsOneAnswerWhicheverModuleAsksTest}, over the models this
 * repository carries rather than over sources written to ask it. Two runs of one question, and the
 * sources are the axis: what a written module holds is the shapes somebody chose, and what a corpus
 * holds is the shapes a model reached on its way to doing something else — a clause spanning a list
 * of another declaration, a name spelled outside ASCII, an imported behavior a clause reaches
 * through.
 *
 * <p><b>Both halves of a reading are compared, and only one of them is held to anything.</b> Two
 * readings in which every clause stopped agree over what stated, because neither stated anything, so
 * the clauses a reading has no form for are compared beside the ones it does and the count below
 * refuses a sweep in which nothing stated. But no clause of a corpus stops, so what this says is
 * that a clause <i>with a form</i> has one answer whoever asks. The other arm is held beside this,
 * over a source written to reach it: a corpus is a model somebody wrote to work, and a clause the
 * discharge reader has no form for is not something to put in one.
 */
@Tag("population")
class EveryDeclarationACorpusImportsStatesOneThingWhoeverAsksTest {

    @Test
    void everyDeclarationACorpusImportsStatesOneThingWhoeverAsks() {
        int edges = 0;
        int stated = 0;
        List<String> stopped = new ArrayList<>();
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            Compilation compilation = corpus.analyse().compilation();
            compilation.answerEverything();
            Db db = compilation.db();
            for (ClauseReadings.Edge edge
                    : ClauseReadings.importsOf(db, compilation.modules())) {
                ClauseReadings.Read asking = ClauseReadings.readBy(db, edge.asking(), edge.named());
                assertEquals(ClauseReadings.readBy(db, edge.declaring(), edge.named()), asking,
                        "in `" + corpus + "`, `" + edge.named() + "` states one thing where it was"
                                + " written and another where `" + edge.asking() + "` reads it");
                edges++;
                stated += asking.stated().size();
                asking.stopped().forEach(each -> stopped.add(corpus + " " + each));
            }
        }
        assertTrue(edges > 0,
                "no module of any corpus reaches a declaration another wrote, so this compared"
                        + " nothing — the corpora no longer carry an import of a declaration");
        assertTrue(stated > 0,
                "the sweep found " + edges + " imported declarations and not one clause of them"
                        + " reached a form, so every comparison above held of nothing; these"
                        + " stopped: " + stopped);
    }
}
