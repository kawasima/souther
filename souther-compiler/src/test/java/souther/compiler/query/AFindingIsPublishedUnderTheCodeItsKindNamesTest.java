package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.DiagnosticCode;
import souther.compiler.diag.msg.ExampleMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The row and the arm its answer is owed at are published under one rule.
 *
 * <p>Two places say which rule a finding is about and they are written apart: {@link Adequacy.Kind}
 * carries the code a build refuses under, and the message the report writes carries the code a
 * person looks up. Neither is read off the other, so they can drift — and this pair drifted while
 * being changed, the kind moving to the rule about a row's answer while the sentence went on
 * carrying the rule about an arm nothing reaches. A reader following that code arrives at a rule
 * saying no row goes through the arm, about an arm a row does go through.
 *
 * <p>What this does not check is the same agreement for every other kind. Which sentence a finding
 * becomes is chosen inside the reader that writes warnings, so pairing them in general means
 * exposing that choice; this pins the pair that moved. A finding that is not a gap at the bar in
 * force never becomes a sentence at all, so a sweep over every finding would be comparing codes for
 * findings nothing published.
 */
class AFindingIsPublishedUnderTheCodeItsKindNamesTest {

    private static final String OWED_AT_AN_ARM = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (cost: Amount) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (cost) = {
                guard cost.value <= 100 else Waiting { cost = cost }
                Submitted { cost = cost }
            }

            example submit
                | "under" : (Amount(50)) -> Submitted { cost = Amount(50) }
                | "over" : (Amount(101)) -> <?>
            """;

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(OWED_AT_AN_ARM, "Main");
        compilation.measure(Adequacy.Level.ALL);
        compilation.answerEverything();
        return compilation;
    }

    /** The kind both findings carry, which is the rule about a row's answer and not the arm's. */
    @Test
    void bothFindingsCarryTheRuleAboutARowsAnswer() {
        List<Adequacy.Kind> owed = new ArrayList<>();
        for (Adequacy.Finding finding : compiled().db()
                .ask(new Adequacy.Findings("example.trip")).value()) {
            if (finding.about() instanceof About.AnUnansweredRow
                    || finding.about() instanceof About.ARowAtAnArmAwaitsItsAnswer) {
                owed.add(finding.kind());
            }
        }

        assertEquals(List.of(Adequacy.Kind.UNANSWERED_ROW, Adequacy.Kind.UNANSWERED_ROW), owed,
                "the row and the arm its answer is owed at are one rule");
        assertEquals(DiagnosticCode.E1934, Adequacy.Kind.UNANSWERED_ROW.code().orElseThrow(),
                "and that rule has a code, since a build refuses over it");
    }

    /** And the sentences a person reads carry it too, which is the half that drifted. */
    @Test
    void andSoDoTheSentencesAPersonReads() {
        List<String> said = new ArrayList<>();
        for (Db.Found found : compiled().db().allReports()) {
            switch (found.report().diagnostic().said()) {
                case ExampleMessage.ARowAtThatArmAwaitsItsAnswer _,
                     ExampleMessage.TheNamedRowsAnswerIsOwed _,
                     ExampleMessage.TheRowsAnswerIsOwed _ ->
                        said.add(found.report().diagnostic().code());
                default -> { }
            }
        }

        assertEquals(List.of("E1934", "E1934"), said,
                "the code a reader looks up is the code the build refused under");
    }
}
