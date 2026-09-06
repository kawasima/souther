package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleRef;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position whose two coordinates are both spoken for says which rules compete for it.
 *
 * <p>A {@code String} is the one thing that can be measured two ways — its own order, and the
 * length of it — and which of them a position is measured at is settled by whichever the model
 * wrote about. Where its own type chose neither and the value it sits in states an end on each,
 * choosing either would put a line the author can read beside one they cannot see, so both rules
 * are left unread.
 *
 * <p>Both of them, and each by name. One line said the position was short of something and left the
 * author to work out which two of their clauses were in the way — and said it in the words of a
 * form this compiler cannot read, which is a cause it was never observed to have: the clauses were
 * read perfectly well.
 *
 * <p>Its own reason and not one of the three beside it. What is missing is not a reader for an
 * expression, nor a carrier, nor a class about two positions: it is a rule for which coordinate
 * wins, and an author sent after any of the other three would be looking for something that is
 * already there.
 */
class APositionMeasuredTwoWaysNamesBothRulesTest {

    private static final String ORDER_FIRST = """
            module m

            data Ok
            data R = { s: String }
                invariant low  = s >= "m"
                invariant long = String.length(s) >= 3

            behavior f : (v: R) -> Ok
                constructs Ok
            let f (v) = Ok
            """;

    /** The same two rules, written the other way round. */
    private static final String LENGTH_FIRST = """
            module m

            data Ok
            data R = { s: String }
                invariant long = String.length(s) >= 3
                invariant low  = s >= "m"

            behavior f : (v: R) -> Ok
                constructs Ok
            let f (v) = Ok
            """;

    /** Both clauses are named, and both under the reason that says what is missing. */
    @Test
    void bothRulesAreNamedAndNeitherIsCalledUnreadableSyntax() {
        List<PartitionEvidence.NotRead> said = notRead(ORDER_FIRST);

        assertEquals(2, said.size(), said::toString);
        assertEquals(Set.of(souther.compiler.partition.UndividedPosition.Reason
                        .COMPETING_COORDINATES),
                said.stream().map(PartitionEvidence.NotRead::reason)
                        .collect(java.util.stream.Collectors.toSet()),
                said::toString);
        assertEquals(2, rules(ORDER_FIRST).size(), said::toString);
        // Each at the coordinate its own rule is about, which is what makes them two rules rather
        // than one said twice: the position is measured two ways and one clause took each.
        assertEquals(List.of("v.s", "String.length(v.s)"),
                said.stream().map(PartitionEvidence.NotRead::at).toList());
    }

    /**
     * And which two they are does not depend on the order the author wrote them in.
     *
     * <p>The reading that gave them up looks at both, so a set built from one of them would be the
     * walk's answer rather than the model's — which is the shape a report keyed on "the first
     * limit in the way" already had.
     */
    @Test
    void theSameTwoRulesWhicheverOrderTheyAreWrittenIn() {
        assertEquals(named(rules(ORDER_FIRST)), named(rules(LENGTH_FIRST)));
        assertEquals(Set.of("invariant R (low)", "invariant R (long)"), named(rules(ORDER_FIRST)));
    }

    /**
     * And each of them is said twice, because two different things are owed about it.
     *
     * <p>What became of the rule here, and what a measure is waiting on. The first is a finding:
     * this rule has no line at this coordinate, and the reader that gave up says why. The second is
     * a question: whether the rule puts an end there is what nothing worked out, so the measure
     * that answers about ends stays open until something does.
     *
     * <p>Which of the position's two numbers it is measured at is settled by the walk over the
     * input and by nothing else, so the question is the walk's own. Read off the accounting of the
     * declaration's clauses — where it is not, because the clauses were read — the position came
     * back with nothing standing at it and its measures closed over a rule this could not use.
     */
    @Test
    void andEachIsBothAFindingAndAQuestion() {
        assertTrue(human(ORDER_FIRST).contains("not read: invariant R (low)"), human(ORDER_FIRST));
        assertTrue(human(ORDER_FIRST).contains("not accounted for: invariant R (low)"),
                human(ORDER_FIRST));
    }

    private static Set<String> named(Set<RuleRef> rules) {
        Set<String> out = new LinkedHashSet<>();
        rules.forEach(each -> out.add(switch (each) {
            case RuleRef.Named it -> it.citedName();
            case RuleRef.Written it -> "the " + it.whatItIs();
        }));
        return out;
    }

    private static Set<RuleRef> rules(String model) {
        Set<RuleRef> out = new LinkedHashSet<>();
        for (PartitionEvidence.NotRead each : notRead(model)) {
            out.add(((PartitionEvidence.NotRead.ARule) each).rule());
        }
        return out;
    }

    private static List<PartitionEvidence.NotRead> notRead(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().get(0)
                .partition().notRead();
    }

    private static String human(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity());
    }
}
