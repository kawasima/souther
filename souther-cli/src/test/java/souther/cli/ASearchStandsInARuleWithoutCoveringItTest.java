package souther.cli;

import org.junit.jupiter.api.Test;
import souther.compiler.partition.DecisionRule;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.DecisionEvidence;
import souther.compiler.query.RuleWitness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A search that composes a value and sees it take a rule shows something can stand there, and
 * covers nothing.
 *
 * <p>The two are the same object read for two things. What the search built is evidence about the
 * model — this rule is one a row can be written at — and it is in nobody's {@code example} block, so
 * the rows have not reached it. Read for the other, a search would satisfy a coverage item nobody
 * wrote a row for, and the account would report a model as answered by rows that do not exist.
 *
 * <p>The rule under test is the one a conjunction masks: rows through both arms of {@code A && B}
 * leave the way where {@code A} held and {@code B} failed untried, and something plainly stands
 * there.
 */
class ASearchStandsInARuleWithoutCoveringItTest {

    private static final String MODEL = """
            module example.search

            data Count = Int
                invariant value >= 0
            data Yes
            data No
            data Answer = Yes | No

            behavior decides : (a: Count, b: Count) -> Answer
            let decides (a, b) = if a.value > 5 && b.value > 5 then Yes else No

            example decides
                | "both"  : (Count(6), Count(6)) -> Yes
                | "first" : (Count(1), Count(6)) -> No
            """;

    @Test
    void aSearchFindsSomethingStandingInTheRuleTheRowsLeft() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String module = compilation.modules().get(0);

        DecisionEvidence evidence =
                compilation.db().ask(new Adequacy.Decides(module)).value().get("decides");
        assertEquals(3, evidence.rules().size(), "the body states three rules");
        assertEquals(2, evidence.covered().getAsInt(), "the rows take two of them");
        List<DecisionRule> open = evidence.nothingStandsIn();
        assertEquals(1, open.size(), () -> "and one is left: " + open);

        Map<DecisionRule, RuleWitness> found =
                compilation.db().ask(new Adequacy.DecisionSearch(module, "decides")).value();
        assertNotNull(found, "the search ran");
        assertEquals(open, List.copyOf(found.keySet()),
                "it looks at the rules no row took and at no others");
        assertInstanceOf(RuleWitness.Stands.class, found.get(open.get(0)),
                () -> "and it composes a value that takes the rule: " + found);
    }

    /** And what it built is not a row of the model, so the rules the rows took do not move. */
    @Test
    void andWhatItBuiltCoversNothing() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String module = compilation.modules().get(0);

        DecisionEvidence before =
                compilation.db().ask(new Adequacy.Decides(module)).value().get("decides");
        assertTrue(compilation.db().ask(new Adequacy.DecisionSearch(module, "decides")).value()
                        .values().stream().anyMatch(RuleWitness.Stands.class::isInstance),
                "the search stands somewhere");
        DecisionEvidence after =
                compilation.db().ask(new Adequacy.Decides(module)).value().get("decides");

        assertEquals(before.covered().getAsInt(), after.covered().getAsInt(),
                "what the rows took is what the rows took");
        assertEquals(before.nothingStandsIn(), after.nothingStandsIn(),
                "and the rule the search stood in is still one no row is in");
    }
}
