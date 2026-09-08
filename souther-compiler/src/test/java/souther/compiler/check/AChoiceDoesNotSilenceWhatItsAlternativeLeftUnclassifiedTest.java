package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A rule this compiler could not classify keeps its question when a choice is written above it.
 *
 * <p>What a comparison states about where the values at a name stop is settled by reading its form,
 * and where that reading stops the question stands: the rule may place a line and may not, and
 * which of those it is is what reading further would answer. Standing alone, and standing under a
 * conjunction, such a rule says so. Under a choice it said nothing at all — the walk that classifies
 * goes into a conjunction and stops at a choice, so the comparison was never read for the line it
 * states, and the clause came out as one raising no question about where the values stop.
 *
 * <p><b>Which is the sentence a document then writes about somebody's model.</b> A behavior whose
 * rules raise no boundary question is one whose border is not applicable, and what that says is that
 * the rules draw no line. So a limit of this compiler went out as a fact about the model, and an
 * author reading it was told to stop looking for a line their own clause may well draw.
 *
 * <p><b>What this does not do is decide what a choice means.</b> Where a choice draws a line is
 * settled where the branches are, and a choice whose alternatives are read to the end and hold a
 * name nowhere between them draws none — which a reading that composes the branches can say and
 * this one cannot. So a name under such a choice is one this compiler declines to speak for, which
 * is weaker than the answer there is and is not a claim about the model.
 */
class AChoiceDoesNotSilenceWhatItsAlternativeLeftUnclassifiedTest {

    private static final String NOT_MEASURED =
            "border      not measured (no line was derived at any position)";

    private static final String NOT_APPLICABLE =
            "border      not applicable (the rules of this behavior draw no line)";

    /**
     * The question a comparison raises on its own, which is what the two below are read against.
     *
     * <p>Written here so that what the choice does to it is the difference between the models and
     * not something this test states twice.
     */
    @Test
    void aComparisonNothingClassifiedRaisesItsQuestionOnItsOwn() {
        assertEquals(List.of(NOT_MEASURED), borderIn("Int.abs(n) >= 5"),
                "the rule is about a value made from the position, and where it stops the position"
                        + " is what reading further would answer");
    }

    /** And keeps it under a conjunction, which the walk that classifies already goes into. */
    @Test
    void andKeepsItUnderAConjunction() {
        assertEquals(List.of("border      borders 1   obligations 0/0"
                        + "   (not all of it was measured)"),
                borderIn("n >= 2 && Int.abs(n) >= 5"),
                "the conjunct beside it draws a line, and the question this one raises still"
                        + " stands");
    }

    /** And under a choice, which is what was lost. */
    @Test
    void andKeepsItUnderAChoice() {
        assertEquals(List.of(NOT_MEASURED), borderIn("n >= 2 || Int.abs(n) >= 5"),
                "whether the choice draws a line at `n` turns on what the alternative holds it to,"
                        + " which is what nothing here worked out");
    }

    /**
     * And where the branch beside it is one nobody can be in.
     *
     * <p>The rule is its right half. A reading that composed the branches would say so; this one
     * does not have to, and what it may not do is answer as though the left half settled anything.
     */
    @Test
    void andWhereTheBranchBesideItIsOneNobodyCanBeIn() {
        assertEquals(List.of(NOT_MEASURED), borderIn("s < \"\" || Int.abs(n) >= 5"),
                "nothing satisfies the left alternative, so the rule is the right one — and its"
                        + " line is what reading further would answer");
    }

    /**
     * And a choice whose alternatives were read to the end says what it said before.
     *
     * <p>The control, and the half this may not move. Both alternatives place an end and between
     * them they hold `n` nowhere; nothing about that turns on a reading having stopped, so the
     * model draws no line and the document says so.
     */
    @Test
    void andAChoiceReadToTheEndDrawsNoLineAsBefore() {
        assertEquals(List.of(NOT_APPLICABLE), borderIn("n >= 2 || n <= 0"),
                "both alternatives were read, so what they leave `n` is the model's answer");
    }

    /** And a rule that draws a line still draws it. */
    @Test
    void andARuleThatDrawsALineStillDoesSo() {
        assertEquals(List.of("border      borders 1   obligations 0/0"), borderIn("n >= 2"),
                "no choice stands here and nothing about this rule went unclassified");
    }

    /** What the document says about this behavior's border. */
    private static List<String> borderIn(String clause) {
        Compilation compilation = Compilation.ofSource("""
                module demo

                data Yes
                data No
                data Answer = Yes | No

                data N = { n: Int, s: String }
                    invariant r = %s

                behavior check : (v: N) -> Answer
                let check (v) = Yes
                """.formatted(clause), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Predicate<String> border = each -> each.startsWith("border");
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity()).lines()
                .map(String::strip).filter(border).toList();
    }
}
