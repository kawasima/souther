package souther.compiler.query;

import souther.compiler.check.AssumedContract;
import souther.compiler.check.StatedContract;
import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a caller depends on of a contract is what it states, over every contract the conformance
 * corpus writes.
 *
 * <p>A contract is read into terms, and a term carries where it was written and the ordinal its
 * module numbered it with. Neither is anything a caller reads — it substitutes its own arguments in
 * and reads what the terms say — so two readings of one declaration that differ only in where the
 * file put it are the same dependency. {@link StatedContract#assumptions()} is where that is
 * decided, and {@link souther.compiler.check.TermMeaning} is what it decides with.
 *
 * <p>This asks it of {@link Bodies.Assumptions}, over what the corpus states. It does not ask it of
 * every kind of term: the corpus states one {@code ensures}, which reaches a handful of the node
 * kinds a reading is projected over, and a kind whose place was compared in a shape this never
 * meets would pass here. That is {@code EveryTermIsReadForWhatItSaysTest}, which asks the same
 * question of one node kind at a time and reports the kinds it did not reach. Both are wanted: one
 * holds the projection, and this holds what the query graph does with it.
 *
 * <p>Moving the whole file is the edit: every position in it changes, and every construct is
 * numbered after the ones the blank lines did not add, so a place surviving anywhere in a contract
 * shows up here as an inequality.
 */
@Tag("population")
class WhatACallerAssumesIsWhatWasStatedNotWhereItWasWrittenTest {

    /** Every behavior that states something, in every module of every corpus. */
    private static Map<ValueName.Behavior, AssumedContract> assumed(Compilation c) {
        Map<ValueName.Behavior, AssumedContract> out = new LinkedHashMap<>();
        for (String module : c.modules()) {
            Map<String, StatedContract> stated =
                    c.db().ask(new Bodies.StatedContracts(module)).value();
            if (stated == null) {
                continue;
            }
            for (String behavior : stated.keySet()) {
                ValueName.Behavior named = new ValueName.Behavior(module, behavior);
                Answer<AssumedContract> answer = c.db().ask(new Bodies.Assumptions(named));
                if (answer.present()) {
                    out.put(named, answer.value());
                }
            }
        }
        return out;
    }

    private static Compilation compiled(List<String> files, List<String> sources, String before) {
        Map<String, String> byId = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            byId.put(files.get(i), before + sources.get(i));
        }
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        return c;
    }

    @Test
    void movingEveryLineOfEveryCorpusChangesNoContractACallerDependsOn() {
        List<String> checked = new ArrayList<>();
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            Map<ValueName.Behavior, AssumedContract> where =
                    assumed(compiled(corpus.files(), corpus.sources(), ""));
            Map<ValueName.Behavior, AssumedContract> moved =
                    assumed(compiled(corpus.files(), corpus.sources(), "\n\n\n"));

            assertEquals(where.keySet(), moved.keySet(),
                    corpus.name() + " states the same contracts wherever its lines are");
            assertEquals(where, moved,
                    corpus.name() + " states the same thing three lines further down");
            if (!where.isEmpty()) {
                checked.add(corpus.name() + ":" + where.size());
            }
        }
        assertTrue(!checked.isEmpty(),
                "some corpus writes a contract, or this asks nothing: " + checked);
    }
}
