package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleRef;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.CoverageObligation;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.values.AdmissibleSet;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position says which of its rules went unanswered, and that is not what a reading says of
 * itself.
 *
 * <p>The models of issue #842. Both bound a numeric newtype and divide it with a guard, and the
 * reading that turns clauses into sets of values is short of the position's rules in both — it has
 * no word for a range. Asked of that reading, the two are the same model. Asked of the rules, they
 * are not: in the first every clause was taken in by something, and in the second one was taken in
 * by nothing.
 */
class APositionSaysWhichOfItsRulesWentUnansweredTest {

    /** The issue's model: an ordering invariant, and a guard dividing what it bounds. */
    private static final String EVERY_RULE_ANSWERED = """
            module example.rooms

            data Length = Int
                invariant min = value >= 1
                invariant max = value <= 100

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2
            """;

    /** The same, with a clause neither reading takes in. */
    private static final String ONE_RULE_UNANSWERED = """
            module example.rooms

            data Length = Int
                invariant min = value >= 1
                invariant max = value <= 100
                invariant even = value * value >= 4

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2
            """;

    private static Position positionOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        assertNotNull(sigs);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get("price"), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES).positions().stream()
                .filter(p -> p.path().toString().equals("length"))
                .findFirst().orElseThrow();
    }

    /**
     * Every rule taken in, and the reading of values short of them all the same.
     *
     * <p>The pair of assertions is the point. The completeness beside the position says what it has
     * always said; what changed is that a report may no longer read it as the model having gone
     * unread.
     */
    @Test
    void aBoundIsAnsweredEvenWhereTheReadingOfValuesIsShort() {
        Position length = positionOf(EVERY_RULE_ANSWERED);

        assertEquals(List.of(), length.unansweredQuestions(),
                "both bounds were taken in, by the reading that turns them into ends");
        assertInstanceOf(AdmissibleSet.Completeness.Wider.class, length.completeness(),
                "and the reading of values is short of the rules, as it always was");
    }

    /**
     * A clause nothing took in leaves its questions standing, and each names the clause.
     *
     * <p>Which is what the report never had. An author was told that a rule about the position went
     * unread, with nothing saying which rule — two lines above a boundary drawn from one of the
     * rules the sentence was about.
     *
     * <p>Two of them, because the clause is read to two different depths. {@code value * value >= 4}
     * restricts which values may stand at the position whatever anything folds, so that question is
     * raised and nothing answered it; whether it also places an end there is what folding the
     * product would decide, and nothing did.
     */
    @Test
    void aClauseNothingTookInIsNamed() {
        List<StandingQuestion> open = positionOf(ONE_RULE_UNANSWERED).unansweredQuestions();

        assertEquals(List.of("invariant Length (even)", "invariant Length (even)"),
                open.stream().map(each -> ((RuleRef.Named) each.rule()).citedName()).toList(),
                "the clause the author wrote, as a report names it — and not the position it "
                        + "is about");
        StandingQuestion.Exact asked = open.stream()
                .filter(StandingQuestion.Exact.class::isInstance)
                .map(StandingQuestion.Exact.class::cast).findFirst().orElseThrow(
                        () -> new AssertionError("which values may stand there is raised: " + open));
        assertEquals(CoverageObligation.ADMITTED_VALUES, asked.obligation());
        assertTrue(asked.asks() instanceof InputQuestion.AboutAPosition at
                        && at.path().equals(TermPath.of("length")),
                () -> "about the position the newtype stands at, which is what the value its"
                        + " clauses are written on is called out here: " + asked.asks());
        StandingQuestion.BoundaryUndetermined undecided = open.stream()
                .filter(StandingQuestion.BoundaryUndetermined.class::isInstance)
                .map(StandingQuestion.BoundaryUndetermined.class::cast).findFirst().orElseThrow(
                        () -> new AssertionError("and whether it bounds is not: " + open));
        assertEquals(TermPath.of("length"), undecided.at().path(),
                "the question nothing worked out is filed where the reading stopped");
        assertFalse(undecided.holdsOpen(CoverageObligation.Measure.PARTITION),
                "and it is about the end alone, so the classes rest on nothing here");
    }
}
