package souther.compiler.partition;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.conformance.RepositoryModels;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.RuleWithoutALine;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every rule whose reading stopped leaves a question standing where it was filed.
 *
 * <p>What the measures rest on. A measure is held open by a question and by nothing else, so a rule
 * this compiler did not finish reading that raises none is one the measure closes over — and it
 * closes silently, because a finding is still published and a reader of the report still sees the
 * rule.
 *
 * <p><b>Asked of every reader that can file one.</b> Which of them owes a question is a decision
 * each of them makes where it files: the accounting of a declaration's clauses raises it, and a
 * body's comparison, a clause of an {@code ensures} and a predicate over the strings say instead
 * that nothing classifies them.
 * Nothing in the types holds them to the same answer, so this does — a reader added, or one that
 * starts filing a stop where it used to file a statement, fails here rather than taking a measure
 * quietly with it.
 *
 * <p>Over the models this repository carries and over the ones written here for the readers the
 * corpora do not exercise, since what is being checked is a property of the readers rather than of
 * any one model.
 */
@Tag("population")
class ARuleWhoseReadingStoppedLeavesAQuestionTest {

    /** A comparison in a body that no reading takes apart. */
    private static final String A_COMPARISON_NOBODY_READS = """
            module probe.guarded

            data Pair = { x: Int, y: Int }
            data Low
            data High

            behavior pick : (p: Pair) -> Low | High
            let pick (p) =
                if Int.multiply(p.x, p.x) < 10
                    then High
                    else Low
            """;

    /** A clause of an `ensures` stated in a form nothing reads. */
    private static final String AN_ENSURES_NOBODY_READS = """
            module probe.ensures

            data Item = { price: Int }
            data Ok = { at: Int }

            behavior look : (item: Item) -> Ok
                ensures Bool.not(item.price > 100)
            """;

    /** A predicate over the strings of a value an operation handed out, which is about those
     *  strings and not the ones at the position they came from. */
    private static final String A_PREDICATE_OVER_A_MADE_VALUE = """
            module probe.codes

            data Person = { code: String }
            data Count = Int

            behavior seen : (people: List<Person>) -> Count
                constructs Count
            let seen (people) =
                Count(List.length(
                    List.filter(s -> String.startsWith("JP", s),
                        List.map(q -> q.code, people))))
            """;

    @Test
    void everyStoppedFindingHasAQuestionAtThePlaceItWasFiled() throws Exception {
        List<String> alone = new ArrayList<>();
        int stopped = 0;
        for (Compilation compilation : every()) {
            for (String module : compilation.modules()) {
                Map<String, PartitionEvidence> coverage =
                        compilation.db().ask(new Adequacy.Coverage(module)).value();
                if (coverage == null) {
                    continue;
                }
                for (PartitionEvidence evidence : coverage.values()) {
                    Set<String> asked = new LinkedHashSet<>();
                    for (PartitionEvidence.Unanswered each : evidence.unanswered()) {
                        asked.add(each.asked().rule() + " @ " + each.at());
                    }
                    for (RuleWithoutALine each : evidence.rulesWithoutALine()) {
                        if (!(each.why() instanceof BlockReason.RuleReadingStopped)) {
                            continue;
                        }
                        stopped++;
                        String where = each.rule() + " @ " + each.at().path();
                        if (!asked.contains(where)) {
                            alone.add(where + " (" + each.why().getClass().getSimpleName() + ")");
                        }
                    }
                }
            }
        }

        assertEquals(List.of(), alone,
                "a rule this compiler did not finish reading, with nothing standing at the place it"
                        + " was filed: the measures close over it and the report still names it");
        assertTrue(stopped > 0, "no reading stopped anywhere, so this checked nothing");
    }

    /** The models this repository carries, and the readers they do not exercise. */
    private static List<Compilation> every() {
        List<Compilation> out = new ArrayList<>(RepositoryModels.all());
        for (String each : List.of(A_COMPARISON_NOBODY_READS, AN_ENSURES_NOBODY_READS,
                A_PREDICATE_OVER_A_MADE_VALUE)) {
            Compilation one = Compilation.ofSource(each, "Main");
            one.measure(Adequacy.Asked.fullReport());
            one.answerEverything();
            out.add(one);
        }
        return out;
    }
}
