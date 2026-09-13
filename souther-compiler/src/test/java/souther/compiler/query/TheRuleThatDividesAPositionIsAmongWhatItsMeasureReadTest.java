package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.check.RuleCitation;
import souther.compiler.partition.RuleEvidenceOrigin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule that divided a position is among the handles that position's measure holds.
 *
 * <p>What a reader is sent to is asked of the measure that read the rule. A rule that composed the
 * classes at a position is read nowhere else: it is not a question nothing answered, and it is not
 * a rule this reading gave up on — those are the two arms a gathering written from the arrays
 * happened to hold, and a rule that did divide the position reached neither. So the page named the
 * class no row is in and had no handle for the rule that made that class.
 *
 * <p><b>The third arm is what this is about.</b> Below, the citation is asserted to reach the
 * closure while reaching neither of the other two — so a closure that answered from those alone is
 * red here, which is the state the defect was found in.
 */
class TheRuleThatDividesAPositionIsAmongWhatItsMeasureReadTest {

    private static final String MODULE = "example.codes";

    /**
     * A predicate over the strings at a position, which tells a set of its values from the rest.
     *
     * <p>A rule with no line, so the classes it composes are sets and the reading of it is what
     * they were composed out of. A numeric guard would draw a line instead, and the classes would
     * be the runs of values on the order — which is a position whose measure holds no such reading
     * at all, and would hold this rule over nothing.
     */
    private static final String MODEL = """
            module example.codes

            data Answer = Yes | No

            behavior f : (code: String) -> Answer
            let f (code) = if String.startsWith("JP", code) then Yes else No
            """;

    @Test
    void aRuleThatComposedThePositionsClassesIsOneThePageCanBeSentTo() {
        PartitionEvidence measured = measured();

        Set<RuleCitation> divides = new LinkedHashSet<>();
        for (PartitionEvidence.AxisCoverage axis : measured.axes()) {
            for (RuleEvidenceOrigin origin : axis.divides()) {
                divides.add(origin.cited());
            }
        }
        // The subject exists. Asked of a model whose positions no rule divides, everything below
        // holds of a measure that reads no rules at all.
        assertEquals(1, divides.size(),
                () -> "the model under test has a rule that divided a position: " + measured.axes());

        assertTrue(measured.ruleCitations().containsAll(divides),
                () -> "the rule that composed the classes is among what this measure read: "
                        + divides + " against " + measured.ruleCitations());
    }

    /**
     * And it is there by being what divided the position, rather than by turning up beside it.
     *
     * <p>The control for the assertion above. A closure gathered from the questions nothing
     * answered and the rules this reading gave up on would hold every other handle this measure has
     * and not this one, and would pass the first test on a model where some rule happened to be in
     * both.
     */
    @Test
    void andReachesTheClosureThroughNothingElse() {
        PartitionEvidence measured = measured();

        Set<RuleCitation> elsewhere = new LinkedHashSet<>();
        measured.unanswered().forEach(each -> elsewhere.addAll(each.cited()));
        measured.notRead().forEach(each -> elsewhere.addAll(each.cited()));

        for (PartitionEvidence.AxisCoverage axis : measured.axes()) {
            for (RuleEvidenceOrigin origin : axis.divides()) {
                assertFalse(elsewhere.contains(origin.cited()),
                        () -> "the rule that divided `" + axis.name() + "` is read by no other"
                                + " array of this measure: " + origin.cited() + " among "
                                + elsewhere);
            }
        }
    }

    private static PartitionEvidence measured() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        PartitionEvidence f = compilation.db()
                .ask(new Adequacy.Coverage(MODULE)).value().get("f");
        assertNotNull(f, "the model under test compiles");
        assertEquals(List.of("code"),
                f.axes().stream().map(PartitionEvidence.AxisCoverage::name).toList(),
                "the position under test was measured");
        return f;
    }
}
