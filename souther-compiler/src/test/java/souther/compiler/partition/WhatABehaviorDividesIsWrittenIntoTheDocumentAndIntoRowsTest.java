package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a behavior divides a position into reaches the two things a division is for.
 *
 * <p>A class is a promise to two readers, and neither of them is the stage that made it. A document
 * tells a person which cases their model tells apart, and a generator writes a value into a row for
 * each of them — and a class that reaches neither is a distinction this compiler found and nobody
 * can act on.
 *
 * <p>So this is over the ends and not over any stage. Every stage between was already right about
 * its own part while a position an author had written a rule for came back undivided, which is the
 * kind of thing only the ends show.
 */
class WhatABehaviorDividesIsWrittenIntoTheDocumentAndIntoRowsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String MODEL = """
            module demo

            data Home
            data Abroad
            data Where = Home | Abroad

            behavior route : (code: String) -> Where
            let route (code) = {
                guard String.startsWith("JP", code) else Abroad
                Home
            }

            example route
                | "home" : ("JP-1") -> Home
            """;

    @Test
    void theClassesAreWrittenIntoTheDocument() {
        JsonNode axes = JSON.readTree(json()).get("modules").get(0).get("behaviors").get(0)
                .get("partition").get("axes");

        List<String> classes = new ArrayList<>();
        axes.forEach(axis -> axis.get("classes").forEach(each -> classes.add(each.asString())));
        assertEquals(List.of(
                        "code/String.startsWith(\"JP\", x)",
                        "code/not String.startsWith(\"JP\", x)"),
                classes,
                "a person is told which strings this behavior tells apart");
    }

    /**
     * And a value can be written into a row for each of them.
     *
     * <p>The other end. A class nobody can compose a value for is still a class — the model divides
     * the position whether or not this compiler can write the row — so this is not what makes them
     * classes. What it holds is that these two are not that: an author asked to cover them can be
     * given something to start from, and the two are given different things.
     */
    @Test
    void andADifferentValueCanBeWrittenForEachOfThem() {
        Compilation compilation = compilation();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Axis axis = compilation.db().ask(new Adequacy.Divided(module, "route")).value()
                .axes().get(0);

        List<String> written = new ArrayList<>();
        for (PartitionClass each : axis.classes()) {
            List<FixtureTemplate> made = Partitions.standingFor(each.representatives(), rules,
                    ReadAs.THE_COMPILATION_DOES, Set.of());
            assertFalse(made.isEmpty(), "a row can be composed for `" + each.label() + "`");
            made.forEach(one -> written.add(one.text()));
        }
        assertEquals(written.size(), Set.copyOf(written).size(),
                "each class is covered by a value of its own: " + written);
    }

    private static String json() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(each -> each.diagnostic().code() + " " + each.diagnostic().primary())
                        .toList(),
                "the model under test compiles");
        return AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts()));
    }

    private static Compilation compilation() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(each -> each.diagnostic().code() + " " + each.diagnostic().primary())
                        .toList(),
                "the model under test compiles");
        assertNotNull(compilation.db().ask(new Adequacy.Divided(compilation.modules().get(0),
                "route")).value(), "the behavior was divided");
        return compilation;
    }
}
