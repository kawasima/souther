package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleCitation;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.publish.PublishedRuleHandle;
import souther.compiler.publish.RuleHandleProse;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A comparison is written rather than named, so the question it raises is cited by where it is.
 *
 * <p>{@code RuleRef.named} says a guard's rule is never rendered to a reader on its own: there is
 * nothing the author called it, and "the comparison" sends them nowhere. An invariant's clause has a
 * name and is found by it. One string over both would have to spell a place as a name, which is the
 * assumption a report carrying only names was making before a producer without one reached it.
 *
 * <p>Not the reading occurrence either. Where a rule was read is the partition's, and one comparison
 * inside a helper is read once per call — a question the model raised once would then have to pick
 * one of them to show. This is where the author wrote it, which is one however often it is read.
 */
class AGuardsQuestionIsCitedByWhereItIsWrittenTest {

    /** A comparison stating a line at 20 that this compiler cannot fold, beside a bound it can. */
    private static final String MODEL = """
            module example.repro

            data Length = Int
                invariant min = value >= 1

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value <= Int.min(20, 30) then 1 else 2

            example price
                | "one" : (Length(1)) -> 1
            """;

    /** The page these read, assembled once. The places it may send a reader to are worked out when
     *  it is assembled, so what a sentence says is asked of the page rather than of the finding. */
    private static AdequacyReport.BehaviorReport page() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().get(0);
    }

    private static PartitionEvidence partition() {
        return page().partition();
    }

    /** The findings this reading left that a reader is sent to a place for. */
    private static List<PartitionEvidence.NotRead.AnUnclassifiedRule> writtenComparisons() {
        return writtenComparisons(page());
    }

    /** The same, of a page already assembled. */
    private static List<PartitionEvidence.NotRead.AnUnclassifiedRule> writtenComparisons(
            AdequacyReport.BehaviorReport page) {
        return page.partition().notRead().stream()
                .filter(PartitionEvidence.NotRead.AnUnclassifiedRule.class::isInstance)
                .map(PartitionEvidence.NotRead.AnUnclassifiedRule.class::cast)
                .filter(each -> each.cited().stream()
                        .anyMatch(RuleCitation.Written.class::isInstance))
                .toList();
    }

    /**
     * The comparison this compiler could not read is reported, and it is one finding.
     *
     * <p>What such a rule divides is the part that was not read, so it raises no question — and it
     * is still a rule the author wrote about a position, which is what a report has to say. Said as
     * nothing, the position came back as one the model divides no way, two tokens from the
     * comparison about it.
     */
    @Test
    void aComparisonNothingCouldReadIsStillReportedAtThePositionItIsAbout() {
        List<PartitionEvidence.NotRead.AnUnclassifiedRule> said = writtenComparisons();

        assertEquals(List.of("length"),
                said.stream().map(PartitionEvidence.NotRead::at).toList(),
                () -> "one finding, at the position's own values, which is what the rule bounds: "
                        + said);
    }

    /** And it is cited by where the author wrote it, not by a name it does not have and not by
     *  the construct standing round it. */
    @Test
    void itIsCitedByThePlaceAndNamedByNothing() {
        AdequacyReport.BehaviorReport page = page();
        PartitionEvidence.NotRead.AnUnclassifiedRule one = writtenComparisons(page).getFirst();

        RuleCitation.Written written = one.cited().stream()
                .filter(RuleCitation.Written.class::isInstance)
                .map(each -> (RuleCitation.Written) each).findFirst()
                .orElseThrow(() -> new AssertionError("a comparison has no name, so it is cited by"
                        + " where it is written: " + one.cited()));
        // And the question it names is the writing module's, because this compilation holds the
        // file the comparison is in. Asked of the page, which worked the answer out when it was
        // assembled.
        assertInstanceOf(RuleReportAnchor.ByTheModuleThatWroteIt.class, written.anchor(),
                () -> "a comparison in a file this compile holds is placed by whoever wrote it: "
                        + written);
        String said = RuleHandleProse.said(
                PublishedRuleHandle.of(written, page.rulePlace()),
                SourceNameResolver.identity(), null);
        assertTrue(said.startsWith("comparison@"),
                () -> "what the rule is and where it is written: " + said);
    }

    /** The invariant beside it keeps its name, which is the other half of the same rule. */
    @Test
    void anInvariantIsStillCitedByTheNameTheAuthorGaveIt() {
        Compilation compilation = Compilation.ofSource("""
                module example.repro

                data Length = Int
                    invariant square = value * value >= 4

                behavior price : (length: Length) -> Int
                let price (length) = if length.value >= 5 then 1 else 2

                example price
                    | "one" : (Length(5)) -> 1
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        PartitionEvidence evidence = AdequacyReport.of(compilation)
                .modules().get(0).behaviors().get(0).partition();

        RuleCitation.Named named = evidence.unanswered().get(0).cited().stream()
                .filter(RuleCitation.Named.class::isInstance)
                .map(RuleCitation.Named.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("an invariant is cited by the name the"
                        + " author gave it: " + evidence.unanswered().get(0).cited()));
        assertEquals("invariant Length (square)", named.rule().citedName());
    }
}
