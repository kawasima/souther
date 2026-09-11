package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.partition.DecisionReading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule nothing was read about is not a rule no row takes.
 *
 * <p>Two nothings, and the account may refuse over one of them. A behavior nobody wrote a row for
 * has its rules uncovered and an author writes a row; a behavior whose rows nothing could be read
 * of has rules nothing was read about, and a row taking one of them may be sitting in the file the
 * reading could not evaluate. Told as one, a build is refused over rules an author has already
 * written rows for.
 *
 * <p>Which is why the coverage half of the account is a measure. The four states a measurement has
 * are the four answers here — nobody asked, there was nothing to read, it was read in part, it was
 * read whole — and a sum of its own had one word for the first three with nothing behind it to say
 * a finding may not be refused over.
 */
class ARuleNothingWasReadAboutIsNotARuleNoRowTakesTest {

    /** One body with two ways and a row down each, so the reading has rules and takes them all. */
    private static final String MODEL = """
            module example.decide

            data Yes
            data No
            data Verdict = Yes | No
            data Count = Int
                invariant value >= 0

            behavior decides : (a: Count) -> Verdict
            let decides (a) = if a.value > 5 then Yes else No

            example decides
                | "over"  : (Count(6)) -> Yes
                | "under" : (Count(1)) -> No
            """;

    @Test
    void aReadingThatPlacedNoRunLeavesEveryRuleUnsaidRatherThanUncovered() {
        DecisionEvidence read = decision();
        assertFalse(read.rules().isEmpty(), "the body states rules");

        // The same rules, with a reading of the runs that could not be finished.
        DecisionEvidence unread = new DecisionEvidence(read.read(),
                new Measurement.FailedToMeasure<>(
                        DecisionEvidence.Unreadable.NO_ROW_CAME_BACK,
                        WeakeningSet.of(new Weakening.BodiesNotElaborated("example.decide"))));

        assertEquals(java.util.List.of(), unread.notTakenByRows(),
                () -> "no rule is said to be one no row takes: " + unread.notTakenByRows());
        assertFalse(unread.weakening().isEmpty(),
                "and the account says what the reading went without");
        assertTrue(unread.covered().isEmpty(),
                "which is not a count of none taken");
    }

    /**
     * And a finding made from a reading that placed some of the runs is not one to refuse over.
     *
     * <p>Said of the finding rather than of the report, because the disposition is what every
     * surface reads: a build refuses over it, a page marks it, and a generation decides from it
     * whether to offer a row. One of the three reading it differently is the state this carries.
     */
    @Test
    void aRuleFoundByAReadingThatWentWithoutSomethingIsUndecidedAndNotRefused() {
        DecisionEvidence read = decision();
        DecisionEvidence partly = new DecisionEvidence(read.read(),
                new Measurement.Partial<>(read.took().made().orElseThrow(),
                        WeakeningSet.of(new Weakening.DecisionRunNotWatched("decides"))));

        DecisionReading.Ruled ruled = partly.read().found().get(0);
        Adequacy.Finding finding = Adequacy.Finding.by(
                new FindingSubject.OfABehavior("decides"), partly,
                new About.ARuleNoRowTakes("decides", ruled));

        assertEquals(Adequacy.Finding.Disposition.UNDECIDED,
                finding.disposition(Adequacy.AdequacyBar.CLASSES),
                () -> "a rule a row may already take is not a gap: " + finding);
        assertFalse(finding.isAdequacyGap(Adequacy.AdequacyBar.CLASSES),
                "so no build is refused over it");
    }

    /** And a build that reads rows without recording where they went says so rather than counting
     *  every rule as one no row takes. */
    @Test
    void aBuildThatRecordsNothingSaysSoRatherThanCountingTheRulesUncovered() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(Adequacy.Level.WITNESS));
        compilation.answerEverything();

        DecisionEvidence took = compilation.db().ask(new Adequacy.Decides("example.decide"))
                .value().get("decides");
        assertTrue(took.took().made().isEmpty(), "nothing was read about the runs");
        assertEquals(DecisionEvidence.NotAsked.NOT_ASKED, took.took().why(),
                "and the measure says the build did not ask");
        assertEquals(java.util.List.of(), took.notTakenByRows(),
                "so no rule is reported as one no row takes");
        assertEquals(java.util.List.of(),
                compilation.db().ask(new Adequacy.DecisionFindings("example.decide")).value(),
                "and the account holds no finding about one");
    }

    private static DecisionEvidence decision() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport(Adequacy.AdequacyBar.CLASSES));
        compilation.answerEverything();
        return compilation.db().ask(new Adequacy.Decides("example.decide")).value().get("decides");
    }
}
