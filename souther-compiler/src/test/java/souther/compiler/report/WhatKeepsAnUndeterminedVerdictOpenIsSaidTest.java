package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleReportAnchor;
import souther.compiler.types.WrittenOwner;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.FilingCoordinate;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.inputs.TermPath;
import souther.compiler.partition.ClosureGap;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Weakening;
import souther.compiler.query.WeakeningSet;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A verdict of {@code undetermined} says whether measuring again with more would answer any of it.
 *
 * <p>The model here is issue #1196's: a behavior that forks on a list computed from its input.
 * Every class of the position is derived and every one of them is covered, both arms are reached,
 * both outputs are specified — and the measure stays partial, because the comparison inside
 * {@code List.isEmpty} is about a value made from the position and nothing works out what it says
 * about the values there.
 *
 * <p>Nothing anybody writes closes that, and nothing any allowance changes either. Before this, the
 * report said {@code undetermined} and stopped, which is the same word it says over a row that did
 * not come back and a space too large to walk — so a person was left to work out for themselves
 * that measuring again would find exactly the same thing.
 */
class WhatKeepsAnUndeterminedVerdictOpenIsSaidTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String MODEL = """
            module probe.empty

            data 一般
            data 管理職
            data 役職 = 一般 | 管理職

            data 申請 = { 役職: 役職 }

            data 理由あり
            data 理由なし

            let 理由 (申請: 申請): List<役職> =
                match 申請.役職 with
                    | 一般   -> [ 一般 ]
                    | 管理職 -> []

            behavior 判定する : (申請: 申請) -> 理由あり | 理由なし
            let 判定する (申請) =
                if List.isEmpty(理由(申請)) then 理由なし else 理由あり

            example 判定する
                | "一般社員には理由がある" : (申請 { 役職 = 一般 })   -> 理由あり
                | "管理職には理由がない"   : (申請 { 役職 = 管理職 }) -> 理由なし
            """;

    private static AdequacyReport measured() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }

    /**
     * The verdict does not change, and that is the point.
     *
     * <p>{@code undetermined} means a measure that could have found a gap and was not made, which
     * is true here: the rule about the derived value may divide the position finer than the sum's
     * cases do, and nothing worked it out. What was missing was never the verdict.
     */
    @Test
    void theVerdictIsStillUndetermined() {
        AdequacyReport report = measured();

        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, report.adequacy(),
                () -> report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertTrue(report.adequacyGaps().isEmpty(),
                () -> "and no gap: there is nothing left to write " + report.adequacyGaps());
    }

    /**
     * And what holds it open is what no allowance of this compiler's reaches.
     *
     * <p>Two things, of one cause: the rule nothing could classify, and the decision measure that
     * could not be made because no run through any of its rules can be recognised. Allowing more
     * makes neither of them.
     */
    @Test
    void whatKeepsItOpenIsWhatAWiderRunDoesNotReach() {
        AdequacyReport report = measured();

        assertEquals(new AdequacyReport.UnderAWiderRun(0, 2), report.underAWiderRun(),
                () -> "what keeps it open: " + report.whatKeepsTheVerdictOpen());
    }

    /**
     * And it is the rule inside {@code List.isEmpty}, named as such.
     *
     * <p>The count alone would pass over a model whose verdict was held open by something else
     * entirely, so the one fact is read as well: this is a reproduction and it is meant to keep
     * reproducing the thing it was written for.
     */
    @Test
    void andItIsTheRuleAboutTheValueTheListWasMadeFrom() {
        List<AdequacyOpening> open = measured().whatKeepsTheVerdictOpen();

        assertTrue(open.stream()
                        .filter(AdequacyOpening.ByWeakening.class::isInstance)
                        .map(each -> ((AdequacyOpening.ByWeakening) each).cause())
                        .anyMatch(cause -> cause instanceof Weakening.ModelReadingIncomplete it
                                && it.cause() instanceof ClosureGap.QuestionUnanswered asked
                                && asked.question() instanceof StandingQuestion
                                        .NothingClassifiesIt rule
                                && rule.why() instanceof BlockReason.RuleAboutADerivedValue),
                () -> "the comparison in `List.isEmpty` is about a value made from the position: "
                        + open);
    }

    /** And a person reading the report is told so, under the verdict. */
    @Test
    void theReportSaysItUnderTheVerdict() {
        String human = measured().human(SourceRendering.namedByIdentity(SourceLayouts.NONE));

        assertTrue(human.contains("""
                adequacy: undetermined
                  what keeps it open
                    may change in a wider run     0
                    unaffected by a wider run     2
                """), human);
    }

    /** And a build reading the document is told the fact, not the count. */
    @Test
    void theDocumentSaysTheFactAndLeavesTheCountingToWhoeverWantsIt() {
        JsonNode root = JSON.readTree(measured().json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));

        assertEquals("undetermined", root.get("adequacy").asString());
        assertEquals(2, root.get("keptOpenBy").size(), root.get("keptOpenBy").toString());
        assertEquals("rule_unread", root.get("keptOpenBy").get(0).get("kind").asString());
        for (JsonNode each : root.get("keptOpenBy")) {
            assertEquals("unaffected", each.get("runSensitivity").asString(), each.toString());
        }
    }

    /**
     * A model nobody has written rows for is open on the measures nobody made.
     *
     * <p>The other half of what {@code undetermined} covers, and the half a list of what fell short
     * says nothing about. Nothing here fell short: the measures were never started, so they weaken
     * nothing and a verdict read off the weakenings alone came back open on nothing at all.
     *
     * <p>{@code unaffected} is right and means what it says. Allowing more does not make a
     * measurement nobody asked for; what does is a row, and a row is a change to the model rather
     * than a wider run of this compiler over it. What a person may go on to do is what the reason
     * says, which is why the reason travels beside it.
     */
    @Test
    void aModelWithNoRowsIsOpenOnTheMeasuresNobodyMade() {
        AdequacyReport report = noRows();
        JsonNode open = JSON.readTree(report.json(SourceRendering.namedByIdentity(SourceLayouts.NONE))).get("keptOpenBy");

        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, report.adequacy(),
                () -> report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertFalse(open.isEmpty(),
                "a verdict nobody could settle is open on the measures nobody made");
        for (JsonNode each : open) {
            assertEquals("not_measured", each.get("kind").asString(), each.toString());
            assertEquals("no_rows", each.get("reason").asString(), each.toString());
            assertEquals("unaffected", each.get("runSensitivity").asString(), each.toString());
        }
    }

    /**
     * And the invariant the two halves are for: an open verdict is open on something.
     *
     * <p>What the conformance corpus found a counterexample to, before a measure nobody made was
     * one of these. Held over the models here rather than as a sentence: a settled verdict is open
     * on nothing, and an unsettled one names what it is unsettled by.
     *
     * <p>{@link #refused()} is the half that is easy to leave out and is the one this passed
     * without. A model with a gap is settled — a refusal is an answer — and the rules of it can
     * still hold a comparison nothing read, so the facts are there to be listed and listing them
     * would be saying a verdict nobody is waiting on is being waited on. Over the other three
     * models alone, this passed while the page and the document said different things about
     * exactly that report.
     */
    @Test
    void anOpenVerdictIsOpenOnSomethingAndASettledOneIsNot() {
        for (AdequacyReport each : List.of(measured(), settled(), noRows(), refused())) {
            assertEquals(each.adequacy() == AdequacyReport.AdequacyStatus.UNDETERMINED,
                    !each.whatKeepsTheVerdictOpen().isEmpty(),
                    () -> each.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        }
    }

    /**
     * And the three surfaces say one thing about it, which is what asking once buys.
     *
     * <p>The count, the page and the document were three readings of the verdict being open, and
     * two of them worked it out where they were rendering. So a refused report answered {@code 0/1}
     * to a caller, printed nothing to a person, and wrote {@code []} to a build — three answers, of
     * which the first two say a settled verdict is held open.
     */
    @Test
    void theCountThePageAndTheDocumentAgreeOnARefusedReport() {
        AdequacyReport report = refused();
        JsonNode root = JSON.readTree(report.json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));

        assertEquals(AdequacyReport.AdequacyStatus.NOT_SATISFIED, report.adequacy(),
                () -> report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertEquals(new AdequacyReport.UnderAWiderRun(0, 0), report.underAWiderRun());
        assertFalse(report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)).contains("what keeps it open"),
                report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertTrue(root.get("keptOpenBy").isEmpty(), root.get("keptOpenBy").toString());
    }

    /**
     * The issue's model with a row taken out, which a build refuses over.
     *
     * <p>The same comparison nothing read is still there, so the facts a verdict could be open on
     * are there too — and the verdict is settled all the same, because a gap outranks everything
     * about how much was measured.
     */
    private static AdequacyReport refused() {
        return reportOf(MODEL.substring(0, MODEL.lastIndexOf("    | \"管理職には理由がない\"")));
    }

    /**
     * A settled verdict is open on nothing, whichever way it settled.
     *
     * <p>The field is written all the same, so a consumer reads an array rather than asking whether
     * one is there. What it holds is what keeps the word open — a build refused over a gap has an
     * answer whatever else went unmeasured, which is what makes this not a second list of
     * everything that fell short.
     */
    @Test
    void aSettledVerdictIsOpenOnNothing() {
        AdequacyReport report = settled();
        JsonNode root = JSON.readTree(report.json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));

        assertNotEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, report.adequacy(),
                () -> report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertTrue(root.get("keptOpenBy").isEmpty(), root.get("keptOpenBy").toString());
    }

    /** A model whose one behavior is measured in full, which settles the verdict either way. */
    private static AdequacyReport settled() {
        return reportOf("""
                module probe.settled

                data In = { n: Int }
                data Out = { n: Int }

                behavior go : (in: In) -> Out
                    constructs Out

                let go (in) = Out { n = in.n }

                example go
                    | "one" : (In { n = 1 }) -> Out { n = 1 }
                """);
    }

    /**
     * And one nobody has written a row for, whose rules draw a line a row is owed at.
     *
     * <p>The invariant is what makes this the case it is for. Without a line there is nothing the
     * bar refuses over, every measure answers that it was never going to be made, and the verdict
     * is settled by having nothing to answer for. With one, a measure that could have found a gap
     * was not made — and that is a verdict held open by no weakening at all.
     */
    private static AdequacyReport noRows() {
        return reportOf("""
                module probe.norows

                data Days = Int
                    invariant value >= 1 && value <= 20

                data Grant = { days: Days }
                data NotEntitled

                behavior go : (worked: Int) -> Grant | NotEntitled
                """);
    }

    private static AdequacyReport reportOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }

    /**
     * Two facts one document calls the same thing are two entries, and this is why nothing folds.
     *
     * <p>The array's multiplicity is the facts', so a reader counting it counts facts. Folded on
     * what an entry is printed as, two rules this compiler could not read would come back as one —
     * which is the collapse the whole of this work is about, arriving one step from the end, and it
     * would arrive as somebody tidying up a list with repeats in it.
     *
     * <p>Both halves are here on purpose. Two entries alike must stay two, and two entries the same
     * kind with different answers must stay two as well: the first is what a {@code distinct()}
     * takes out, and the second is what folding on the kind alone takes out.
     *
     * <p>Counted and not listed. What is asked here is how many of each word the array holds, which
     * is what a reader counting it counts; which of two entries comes first is the order the
     * document publishes them in, and is asked where that order is decided.
     */
    @Test
    void twoFactsOfOneKindAreTwoEntries() {
        assertEquals(Map.of("rule_unread/unaffected", 2L),
                howManyOfEach(keptOpenBy(WeakeningSet.of(
                        ruleUnread(new BlockReason.RuleAboutADerivedValue(), "a"),
                        ruleUnread(new BlockReason.RuleAboutADerivedValue(), "b")))),
                "two rules nothing could read are two things to tell a person");
        assertEquals(Map.of("rule_unread/may_change", 1L, "rule_unread/unaffected", 1L),
                howManyOfEach(keptOpenBy(WeakeningSet.of(
                        ruleUnread(new BlockReason.PatternTooCostly(), "a"),
                        ruleUnread(new BlockReason.RuleAboutADerivedValue(), "b")))),
                "and one word over two answers is two entries, not one of either");
    }

    /** How many entries each word has, which is what the multiplicity of this array says. */
    private static Map<String, Long> howManyOfEach(List<String> said) {
        return said.stream().collect(Collectors.groupingBy(word -> word, Collectors.counting()));
    }

    /**
     * And one fact found twice is one entry, which is the fold this leaves alone.
     *
     * <p>{@link WeakeningSet#union} keeps one of two equal facts, so a rule found from three
     * measures is one thing to tell a person. That is a fold on what the facts are, and it happens
     * before anything here — which is the whole of why nothing here folds again.
     */
    @Test
    void oneFactFoundTwiceIsOneEntry() {
        Weakening once = ruleUnread(new BlockReason.RuleAboutADerivedValue(), "a");

        assertEquals(List.of("rule_unread/unaffected"),
                keptOpenBy(WeakeningSet.of(once).union(WeakeningSet.of(once))));
    }

    /** The document's entries, as {@code kind/runSensitivity}, from a set of facts. */
    private static List<String> keptOpenBy(WeakeningSet open) {
        List<String> out = new ArrayList<>();
        for (Weakening each : open.causes()) {
            out.add(AdequacyReport.kindOf(each) + "/"
                    + each.runSensitivity().name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** One rule this compiler stopped on, at {@code term}. */
    private static Weakening ruleUnread(BlockReason.RuleReadingStopped why, String term) {
        return new Weakening.ModelReadingIncomplete(ClosureGap.QuestionUnanswered.of(
                StandingQuestion.NothingClassifiesIt.of(
                        new RuleCitation.Written(
                                new RuleRef.Comparison("go",
                                        new SourceConstructOrigin(
                                                new WrittenOwner.Body("m", "b"), 0, 0,
                                                SourceConstruct.IF)),
                                new RuleReportAnchor.ByTheModuleThatWroteIt()),
                        new FilingCoordinate.AtPosition(TermPath.of(term)), why)));
    }
}
