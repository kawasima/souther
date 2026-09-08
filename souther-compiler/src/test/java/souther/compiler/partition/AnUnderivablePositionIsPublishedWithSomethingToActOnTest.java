package souther.compiler.partition;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.About;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A position reported as one this compiler could not read is published with what it was short of.
 *
 * <p>The end of the chain the verdict is a projection of. What leaves a position short is
 * enumerated where the verdict is made ({@code PendingPosition.complete}); the findings a document
 * is written from are made by the readers that hold those same facts. So the two agree by both
 * coming from there, and this is what says they do.
 *
 * <p><b>Not how the verdict is worked out.</b> Nothing here is an input to it, and nothing here is
 * read to produce it — a report built from a verdict, or a verdict recovered from what happened to
 * be published, is the reconstruction the accounting exists to take away. What is checked is that a
 * reader shown the first is not left with nothing to act on, and that a reader shown a position the
 * model states something about is not sent after a limit that is not there.
 */
@Tag("population")
class AnUnderivablePositionIsPublishedWithSomethingToActOnTest {

    /** A rule about a pair of positions, which each of them is read through and neither divided by. */
    private static final String A_RULE_ABOUT_A_PAIR = """
            module probe

            data Ok

            data Span = { from: Int, to: Int }
                invariant ordered = from <= to

            behavior read : (s: Span) -> Ok
            let read (s) = Ok
            """;

    /** A rule nothing takes in, which leaves a question standing at the position it names. */
    private static final String A_RULE_NOTHING_READ = """
            module probe

            data Ok

            data Code = String
                invariant unreadable = UNREAD

            behavior read : (c: Code) -> Ok
            let read (c) = Ok
            """.replace("UNREAD", souther.compiler.ARuleNoReadingTakesIn.about("value"));

    /** And a position with nothing written about it at all. */
    private static final String NOTHING_WRITTEN = """
            module probe

            data Ok

            data Plain = { a: Int, b: String }

            behavior read : (p: Plain) -> Ok
            let read (p) = Ok
            """;

    private static final List<String> MODELS =
            List.of(A_RULE_ABOUT_A_PAIR, A_RULE_NOTHING_READ, NOTHING_WRITTEN);

    /** Every position of the models this compiler is written against. */
    @Test
    void everyCorpusPositionIsPublishedAsItsVerdictSays() {
        List<String> wrong = new ArrayList<>();
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            wrong.addAll(disagreeingIn(corpus.analyse().report()));
        }
        assertEquals(List.of(), wrong,
                "a verdict and what a document says about the position disagree");
    }

    /** And of the shapes above, which a corpus need not hold. */
    @Test
    void everyPositionOfTheseModelsIsPublishedAsItsVerdictSays() {
        List<String> wrong = new ArrayList<>();
        for (String model : MODELS) {
            wrong.addAll(disagreeingIn(reportOf(model)));
        }
        assertEquals(List.of(), wrong,
                "a verdict and what a document says about the position disagree");
    }

    /** And both halves are reachable in that population, which a count of nothing is not. */
    @Test
    void thePopulationHoldsPositionsOfBothKinds() {
        List<UndividedPosition> undivided = new ArrayList<>();
        for (String model : MODELS) {
            for (AdequacyReport.BehaviorReport behavior : behaviorsOf(reportOf(model))) {
                undivided.addAll(behavior.evidence().partition().notDerivable());
            }
        }
        assertFalse(undivided.stream().noneMatch(
                        each -> each.why() instanceof UndividedPosition.Why.CannotDerive),
                () -> "no position came back underivable: " + undivided);
        assertFalse(undivided.stream().noneMatch(
                        each -> each.why() instanceof UndividedPosition.Why.StatedWithoutALine),
                () -> "no position came back stating something with no line: " + undivided);
    }

    /** Where a verdict and what the findings say about the position do not agree. */
    private static List<String> disagreeingIn(AdequacyReport report) {
        List<String> out = new ArrayList<>();
        for (AdequacyReport.BehaviorReport behavior : behaviorsOf(report)) {
            Set<TermPath> shortHere = new LinkedHashSet<>();
            for (Adequacy.Finding finding : behavior.findings()) {
                TermPath at = shortAt(finding);
                if (at != null) {
                    shortHere.add(at);
                }
            }
            for (UndividedPosition each : behavior.evidence().partition().notDerivable()) {
                boolean underivable = each.why() instanceof UndividedPosition.Why.CannotDerive;
                boolean published = shortHere.contains(each.at());
                if (underivable != published) {
                    out.add(behavior.name() + " at " + each.at() + ": " + each.why()
                            + (published ? " with a finding saying this compiler was short here"
                                    : " with nothing published for a reader to act on"));
                }
            }
        }
        return out;
    }

    /**
     * Where a finding says this compiler was short at a position, or null where it says something
     * else.
     *
     * <p>What counts is a finding about the reach of the readings: a question of one of the
     * position's rules that nothing answered, or a reading that never got to the position. A rule
     * filed at the position that came to no line is not one of those — the reading of it finished,
     * and the model states what it states.
     *
     * <p>Every kind of finding there is, with no {@code default}. What this test claims is that a
     * verdict and the findings at the position agree, and a kind added and not answered for here
     * would be one the claim quietly stopped covering — the check would go on passing while saying
     * less than it says.
     */
    private static TermPath shortAt(Adequacy.Finding finding) {
        return switch (finding.about()) {
            case About.AQuestionNothingAnswered asked -> switch (asked.asked().asked()) {
                case souther.compiler.inputs.StandingQuestion.Exact it -> it.asks().path();
                case souther.compiler.inputs.StandingQuestion.Unclassified it ->
                        it.at().path();
            };
            case About.APositionThisCouldNotRead read -> read.finding().finding().at();
            case About.APositionWhoseRulesWereNotReached gap -> gap.gap().at().at();
            // A rule filed at the position that nothing worked out the questions of, which counts:
            // it is the position being short of whatever the rule states there.
            case About.ARuleNothingClassified rule -> rule.finding().finding().at().path();
            case
            // A rule filed at the position and read from end to end, which is the model stating
            // something rather than the position being short of anything.
                 About.ARuleWithoutALine _,
            // The values the position was read as, wider than its rules leave them: a fact about
            // the set and about no rule, and about a position that may well be measured.
                 About.APositionReadWiderThanItsRules _,
            // And the verdict itself, which is what this is held against rather than part of it.
                 About.APositionNoLineDivides _,
            // Everything else a document says, none of which is about the reach of a reading at a
            // position: what the rows cover, what an obligation came to, what a case or an arm is
            // short of.
                 About.ACaseNoRowExpects _, About.ACaseNothingWasSeenToProduce _,
                 About.ACaseNoRowAppliesItTo _, About.AClassNoRowIsIn _,
                 About.APointOfABorder _, About.APointOfADeclaredBorder _,
                 About.AnArmNoRowGoesThrough _, About.ARowAtAnArmAwaitsItsAnswer _ -> null;
        };
    }

    private static List<AdequacyReport.BehaviorReport> behaviorsOf(AdequacyReport report) {
        List<AdequacyReport.BehaviorReport> out = new ArrayList<>();
        report.modules().forEach(each -> out.addAll(each.behaviors()));
        return out;
    }

    private static AdequacyReport reportOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }
}
