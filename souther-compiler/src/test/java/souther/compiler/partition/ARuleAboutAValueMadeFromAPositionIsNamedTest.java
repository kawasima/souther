package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule an author wrote about a value an operation made from a position is named at that position.
 *
 * <p>Not measured there. What a {@code map} answers is made from what stands at a position and is
 * not those values, so a line drawn there would be at values the rule is not about — and an author
 * cannot tell such a line from one their model states. Reading the rule back through the closure is
 * a different capability and not one this has.
 *
 * <p>What it must not be is silent. The author wrote a comparison; a reading that placed it nowhere
 * reported a model that states no rule there, which is the answer a body with no rule in it gives.
 * So where the values came from is said, and that what the rule says about them here is not worked
 * out.
 *
 * <p>Both ends of that relation are bindings an expansion wrote. The operation is gone by the time
 * anything reads the tree — a walk that only grows a collection is rewritten, and two such walks in
 * a row are joined into one — so what says the values came from there was written where the
 * operation still stood.
 */
class ARuleAboutAValueMadeFromAPositionIsNamedTest {

    private static final String MODULE = "example.scores";

    private static final String MODEL = """
            module example.scores

            data Person = { age: Int }
            data Score = Int
            data Count = Int

            behavior scored : (people: List<Person>) -> Count
                constructs Count, Score
            let scored (people) =
                Count(List.length(
                    List.filter(s -> s.value >= 18,
                        List.map(q -> Score(q.age + 100), people))))
            """;

    private static PartitionEvidence measured() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        PartitionEvidence scored = compilation.db()
                .ask(new Adequacy.Coverage(MODULE)).value().get("scored");
        assertNotNull(scored, "the model under test compiles");
        return scored;
    }

    /** The rule is named, at the position the values it compares came from. */
    @Test
    void theRuleIsNamedAtThePositionItsValuesCameFrom() {
        assertEquals(List.of("people[*]"), measured().notRead().stream()
                        .filter(each -> each.reason()
                                == UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE)
                        .map(PartitionEvidence.NotRead::at).toList(),
                () -> "said once, where the values came from: " + measured().notRead());
    }

    /** And it names the rule, since one was read: this is not a position nothing was written at. */
    @Test
    void itNamesTheRuleAndNotOnlyThePosition() {
        assertTrue(measured().notRead().stream()
                        .filter(each -> each.reason()
                                == UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE)
                        .allMatch(each -> each instanceof PartitionEvidence.NotRead
                                .AnUnclassifiedRule),
                () -> "a rule was read, so the finding has one to name: " + measured().notRead());
    }

    /** No line is drawn, since the values the rule is about are not the values there. */
    @Test
    void noLineIsDrawnAtThatPosition() {
        assertEquals(List.of(), measured().axes().stream()
                        .map(PartitionEvidence.AxisCoverage::path).toList());
    }

    /** And a reader is told which of the two it is, in the words the document promises. */
    @Test
    void theReportSaysWhichOfTheTwoItIs() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String human = AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));

        assertTrue(human.contains("it is about a value made from this one, and what it says about"
                        + " the values here is not worked out, about `people[*]`"),
                human);
    }
}
