package souther.compiler;

import souther.compiler.diag.SourceRendering;
import souther.compiler.observe.ArmObservation;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code witness} promises is that nothing is instrumented and no row runs a second time.
 *
 * <p>Held as a structure and not as a clock. A stopwatch says a run got slower and never says what
 * it got slower doing, so a level that quietly started generating a second set of classes would
 * pass it on a fast enough host. What the level promises is that one key is never asked, and the
 * database records which keys were answered.
 *
 * <p>The measures themselves are asked at this level — a line an invariant drew is measured wherever
 * the rows ran (issue #955) — so the report below is the whole of what a {@code witness} run
 * produces, findings and verdict together. That is the run this holds to the promise.
 */
class WhatAWitnessRunDoesNotPayForTest {

    private static final String MODEL = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Charged = { cost: Amount }
            data Refused = { reason: String }

            behavior submit : (cost: Amount) -> Charged | Refused
                constructs Charged, Refused

            let submit (cost) = {
                guard cost.value <= 100 else Refused { reason = "over" }
                Charged { cost = cost }
            }

            example submit
                | "within" : (Amount(50)) -> Charged
            """;

    @Test
    void nothingIsInstrumentedAndNoRowRunsASecondTime() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(Adequacy.Level.WITNESS));
        compilation.answerEverything();
        String report = AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));

        assertTrue(report.contains("border"), "the whole of a witness run was asked for: " + report);
        assertFalse(compilation.db().isComputed(
                        new Output.EvaluationLinked("example.trip", ArmObservation.RECORD)),
                "witness asked for the instrumented classes");
        assertFalse(compilation.db().isComputed(
                        new Output.Evaluated("example.trip", ArmObservation.RECORD)),
                "witness ran the rows against them");
    }

    /** And the level that pays for them does ask, so the check above is one something can fail. */
    @Test
    void theLevelThatPaysForThemAsksForThem() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(Adequacy.Level.ALL));
        compilation.answerEverything();
        AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));

        assertTrue(compilation.db().isComputed(
                new Output.Evaluated("example.trip", ArmObservation.RECORD)));
    }
}
