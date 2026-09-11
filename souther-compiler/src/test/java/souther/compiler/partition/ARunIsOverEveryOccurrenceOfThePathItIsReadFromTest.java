package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.inputs.RunSource;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run holds a path and nothing else, so it is only over the walks every occurrence of that path is
 * in.
 *
 * <p>The pair of shapes is the whole of it. A walk over a sequence captured from outside the closure
 * it stands in is over every occurrence of its path, and the rule about its total is a line. A walk
 * over a sequence belonging to the closure's own parameter is over the occurrences under one element
 * of the walk outside it, and its path — inside two sequences — names every occurrence under every
 * one of them. The second is not a reading this can have until a run says which occurrences it is
 * over, and what it must not be is the first.
 *
 * <p>Held at the reading and at the representation both. The reading asks before it builds one; the
 * representation refuses one built anyway, which is what says the promise is the type's and not one
 * caller's care.
 */
class ARunIsOverEveryOccurrenceOfThePathItIsReadFromTest {

    private static final String MODULE = "example.claims";

    private static final String MODEL = """
            module example.claims

            data Amount = Int
                invariant value >= 0

            data Item = { amount: Amount, free: Bool }
            data Group = { lines: List<Item> }

            data Big = { threshold: Int }
            data Small
            data Kind = Big | Small

            data Needed
            data NotNeeded
            data Verdict = Needed | NotNeeded

            let matches (lines: List<Item>, k: Kind): Bool =
                match k with
                    | Big { threshold } ->
                        List.sum(List.map(line -> line.amount.value, lines)) >= threshold
                    | Small -> false

            behavior overASequenceFromOutsideTheClosure
                    : (lines: List<Item>, kinds: List<Kind>) -> Verdict
            let overASequenceFromOutsideTheClosure (lines, kinds) =
                if List.length(List.filter(k -> matches(lines, k), kinds)) >= 1
                    then Needed else NotNeeded

            behavior overTheClosuresOwnSequence : (groups: List<Group>) -> Verdict
            let overTheClosuresOwnSequence (groups) =
                if List.length(List.filter(g ->
                        List.sum(List.map(line -> line.amount.value, g.lines)) >= 100000,
                        groups)) >= 1
                    then Needed else NotNeeded
            """;

    /**
     * The walk whose sequence came from outside the closure is read, and its rule is a line on the
     * total.
     *
     * <p>The shape a claim's central rule is written in: the closure decides one thing about a
     * candidate and the total it decides it by is of a list the behavior was given.
     */
    @Test
    void aWalkOverASequenceFromOutsideTheClosureIsReadAsARun() {
        assertTrue(reasonsOf("overASequenceFromOutsideTheClosure")
                        .contains(UndividedPosition.Reason.RULE_ABOUT_A_RUN),
                "the rule is about what the values at the place come to, which is a run and not a"
                        + " class of any of them");
        String report = report();
        for (String point : List.of("ON", "OFF", "IN", "OUT")) {
            assertTrue(report.contains("the " + point + " point "),
                    () -> "a " + point + " point is owed: " + report);
        }
        // And what each of them is owed against is the total, which is the reading's word: the
        // point itself names no quantity, since a line is owed once wherever it is read.
        assertTrue(report.contains("read as overASequenceFromOutsideTheClosure/"
                        + "List.sum(lines[*].amount)"),
                () -> "and the points are owed against the total: " + report);
    }

    /**
     * The walk over the closure's own sequence is not, and no line is drawn anywhere near it.
     *
     * <p>What it must not come out as is a rule about `groups[*].lines[*].amount`, which is every
     * line of every group: the walk is over one group's lines, and a total of all of them is a
     * number no rule in this model is written about. Reported as a rule about a value that came
     * from the position, which is what it is — the reading followed the value to where it came from
     * and could not say what the rule says about the values there.
     */
    @Test
    void aWalkOverTheClosuresOwnSequenceIsNotReadAsARunOverAllOfThem() {
        assertFalse(report().contains("List.sum(groups[*].lines[*].amount)"),
                () -> "a total of every line of every group is a number this model does not"
                        + " compare: " + report());
        assertFalse(reasonsOf("overTheClosuresOwnSequence")
                        .contains(UndividedPosition.Reason.RULE_ABOUT_A_RUN),
                "and it is not said to be a rule about a run, which promises a border was drawn");
        assertTrue(reasonsOf("overTheClosuresOwnSequence")
                        .contains(UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE),
                "the rule is one about a value made from what stands there, which is a reading"
                        + " this did not have rather than one the model does not state");
    }

    /** The reading asks the question rather than building one and hoping. */
    @Test
    void theReadingAsksBeforeItBuildsOne() {
        assertNotNull(RunSource.overTheOccurrencesAt(
                        TermPath.of("lines").element().then("amount")),
                "a place inside one sequence is a place every occurrence of which is in the run");
        assertNull(RunSource.overTheOccurrencesAt(
                        TermPath.of("groups").element().then("lines").element().then("amount")),
                "a place inside two is not, and the run says which of them it is over by holding a"
                        + " path — which says neither");
    }

    /** And one built anyway is refused, so the promise is the type's. */
    @Test
    void oneBuiltAnywayIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunSource.ProjectedOccurrences(
                        TermPath.of("groups").element().then("lines").element().then("amount")),
                "every occurrence of the path is in the run, and a path inside two sequences has"
                        + " occurrences under each element of the outer one");
    }

    private static List<UndividedPosition.Reason> reasonsOf(String behavior) {
        return evidence().get(behavior).notRead().stream()
                .map(PartitionEvidence.NotRead::reason).toList();
    }

    private static Map<String, PartitionEvidence> evidence() {
        return measured().db().ask(new Adequacy.Coverage(MODULE)).value();
    }

    private static String report() {
        return AdequacyReport.of(measured()).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    private static Compilation measured() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }
}
