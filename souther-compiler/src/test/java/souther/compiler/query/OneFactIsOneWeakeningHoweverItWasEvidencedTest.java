package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleCitation;
import souther.compiler.types.WrittenOwner;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.source.SourceId;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.FilingCoordinate;
import souther.compiler.inputs.RuleReasons;
import souther.compiler.inputs.WhereInTheRule;
import souther.compiler.inputs.InputQuestion;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.inputs.TermPath;
import souther.compiler.inputs.TwoAccountsOfOneQuestion;
import souther.compiler.inputs.WhatAQuestionStandsOn;
import souther.compiler.observe.Incompleteness;
import souther.compiler.partition.ClosureGap;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a measurement went without is a set of facts, and what tells two facts apart is what the
 * fact says it is.
 *
 * <p>Three of the arms hold more than the fact. An observation carries where it was said, a rule
 * with no line carries the handle an author is sent to, and a question that stands carries both
 * that handle and what each part of it was short of — and every one of those is declared, at the
 * type that holds it, to be no part of what tells one from another. A rule this compiler could not
 * read, met from two behaviors and cited at whichever place each of them was reading, is one thing
 * to tell a person.
 *
 * <p>So the union folds on the fact and never on the value. Folded on the value, a set that says
 * it holds each fact once holds one of them twice, and everything that counts what it holds counts
 * the paths a fact arrived by: a verdict names the same thing twice, and whichever of the two the
 * walk met first decides which place a reader is sent to.
 *
 * <p>And the evidence is kept. A fold that answered the count by taking one of the two and dropping
 * the other would be the same loss the other way round — the fact would be one, and where it was
 * met would be wherever the walk got to first.
 */
class OneFactIsOneWeakeningHoweverItWasEvidencedTest {

    @Test
    void anObservationMetAtTwoPlacesIsOneFact() {
        WeakeningSet here = observed(new SourcePos(1, 1));
        WeakeningSet there = observed(new SourcePos(2, 2));

        assertEquals(1, here.union(there).causes().size(),
                "one thing that was not observed, said of two places, is one fact");
        assertEquals(here.union(there), there.union(here),
                "which place was met first is no part of what the union comes to");
        // Neither side stands in for the union. A fold that kept one of the two would say the fact
        // once and say it was met only where the walk got to first.
        assertNotEquals(here, here.union(there),
                "a fact met at a second place is not the same value as one met at the first");
        assertEquals(Set.of(Citation.of(new SourcePos(1, 1)), Citation.of(new SourcePos(2, 2))),
                here.union(there).observationCauses().iterator().next().citations(),
                "and the one fact can send a reader to either place");
    }

    @Test
    void anObservationWithNoPlaceIsTheSameFactAsOneWithA() {
        WeakeningSet placed = observed(new SourcePos(1, 1));
        WeakeningSet unplaced = of(Weakening.ObservationIncomplete.of(
                Incompleteness.of(Incompleteness.Code.INSTRUMENTATION_ABSENT,
                        Incompleteness.Scope.MODULE, "m")));

        assertEquals(1, placed.union(unplaced).causes().size(),
                "an occurrence with nowhere to send a reader is the same fact as one with a place");
        assertEquals(Set.of(Citation.of(new SourcePos(1, 1))),
                placed.union(unplaced).observationCauses().iterator().next().citations(),
                "and the one that had nowhere to send a reader takes nothing away from the one"
                        + " that did");
    }

    @Test
    void aRuleCitedTwoWaysIsOneFact() {
        WeakeningSet here = ruleWithoutALine(reachedAt(9));
        WeakeningSet fromACall = ruleWithoutALine(reachedAt(3));

        assertEquals(1, here.union(fromACall).causes().size(),
                "a rule and the handle for it are two questions, and the union folds on the rule");
        assertEquals(here.union(fromACall), fromACall.union(here),
                "which reader cited it first is no part of what the union comes to");
        assertEquals(Set.of(reachedAt(9), reachedAt(3)),
                questionIn(here.union(fromACall)).question().cited(),
                "and the one rule keeps every handle a reader was offered");
    }

    @Test
    void aQuestionCitedTwoWaysIsOneFact() {
        WeakeningSet here = standingQuestion(reachedAt(9),
                new BlockReason.UnreadComparisonForm());
        WeakeningSet fromACall = standingQuestion(reachedAt(3),
                new BlockReason.UnreadComparisonForm());

        assertEquals(1, here.union(fromACall).causes().size(),
                "which rule it is and what it asks are what tell one standing question from"
                        + " another");
        assertEquals(Set.of(reachedAt(9), reachedAt(3)),
                questionIn(here.union(fromACall)).question().cited(),
                "and the one question keeps every handle a reader was offered");
    }

    /**
     * A question two readers cited two ways is one question, and what the author wrote it short of
     * is untouched.
     *
     * <p>The two things it carries beside the fact do not have one algebra. A handle is how a
     * reader is sent to the rule, and two of them are two ways to the same place, so they join. The
     * parts the author wrote are one answer about the model in the order they wrote it, so there is
     * nothing there to join — what two readings of it come to is that they agree.
     */
    @Test
    void aQuestionCitedTwoWaysKeepsTheAuthorsOneAccountOfWhatStoppedIt() {
        BlockReason.RuleReadingStopped form = new BlockReason.UnreadComparisonForm();
        BlockReason.AnswerRealizationStopped answer = new BlockReason.ExactValuesTooCostly();
        RuleCitation here = reachedAt(9);
        RuleCitation fromACall = reachedAt(3);
        WeakeningSet met = standingQuestion(here, form, answer);
        WeakeningSet metElsewhere = standingQuestion(fromACall, form, answer);

        assertEquals(1, met.union(metElsewhere).causes().size(),
                "which rule it is and what it asks are what tell one standing question from"
                        + " another");
        assertEquals(Set.of(here, fromACall),
                questionIn(met.union(metElsewhere)).question().cited(),
                "and both handles came with it");
        assertEquals(new WhatAQuestionStandsOn(RuleReasons.one(form), Optional.of(answer)),
                questionIn(met.union(metElsewhere)).question().stopped(),
                "and what the author wrote it short of is what it was, in their order");
    }

    /**
     * And two accounts of one question cannot disagree about that order.
     *
     * <p>Not two things to put together. What a question stands for is the parts of the rule that
     * raised it, in the order the author wrote them, and a document says it as that — so two
     * readers that came back with two orders for one question are two readings one of which is
     * wrong, and either taking one or joining them publishes a precedence nothing in the model
     * decides.
     *
     * <p>Two parts of the rule, because that is where an order the model has runs. A limit the
     * position's answer ran into is no part of one and stands under no order beside them, so a
     * pair of accounts that met it at different points have not disagreed about anything.
     */
    @Test
    void twoAccountsOfOneQuestionCannotDisagreeOnTheAuthorsOrder() {
        BlockReason.RuleReadingStopped form = new BlockReason.UnreadComparisonForm();
        BlockReason.RuleReadingStopped domain = new BlockReason.UnreadComparisonDomain();
        WeakeningSet met = standingQuestion(reachedAt(9), form, domain);
        WeakeningSet theOtherWayRound =
                standingQuestion(reachedAt(12), domain, form);

        assertThrows(TwoAccountsOfOneQuestion.class, () -> met.union(theOtherWayRound),
                "two readings of one question that disagree about what the author wrote are not"
                        + " something to put together");
    }

    /**
     * The laws, over the atoms this is about.
     *
     * <p>They hold of a set of Java values as well, which is why they said nothing while the atoms
     * were wrong. What they are worth is here: the atom is the fact, so three occurrences of one
     * fact come to one whichever way they are bracketed, and what evidenced each is all still there.
     */
    @Test
    void theUnionIsIdempotentCommutativeAndAssociativeOverFactsAndMergesTheirEvidence() {
        WeakeningSet here = observed(new SourcePos(1, 1));
        WeakeningSet there = observed(new SourcePos(2, 2));
        WeakeningSet elsewhere = observed(new SourcePos(3, 3));

        assertEquals(here, here.union(here), "a fact unioned with itself is the fact");
        assertEquals(here.union(there), there.union(here), "the order of two is no part of the sum");
        assertEquals(here.union(there).union(elsewhere), here.union(there.union(elsewhere)),
                "how three were bracketed is no part of the sum");
        assertEquals(1, here.union(there).union(elsewhere).causes().size(),
                "three occurrences of one fact are one fact");
        assertEquals(3, here.union(there).union(elsewhere)
                        .observationCauses().iterator().next().citations().size(),
                "and it was met at all three places");
    }

    /** The one question that stands, out of a set holding nothing else. */
    private static ClosureGap.QuestionUnanswered questionIn(WeakeningSet weakened) {
        return (ClosureGap.QuestionUnanswered) modelReadingIn(weakened);
    }

    private static ClosureGap modelReadingIn(WeakeningSet weakened) {
        Weakening only = weakened.causes().iterator().next();
        return ((Weakening.ModelReadingIncomplete) only).cause();
    }

    private static WeakeningSet observed(SourcePos where) {
        return of(Weakening.ObservationIncomplete.of(
                Incompleteness.at(Incompleteness.Code.INSTRUMENTATION_ABSENT,
                        Incompleteness.Scope.MODULE, "m", where)));
    }

    private static WeakeningSet ruleWithoutALine(RuleCitation cited) {
        return of(new Weakening.ModelReadingIncomplete(ClosureGap.QuestionUnanswered.of(
                StandingQuestion.NothingClassifiesIt.of(cited,
                        new FilingCoordinate.AtPosition(TermPath.of("x")),
                        new BlockReason.UnreadComparisonForm()))));
    }

    private static WeakeningSet standingQuestion(RuleCitation cited,
                                                 BlockReason.RuleReadingStopped... stopped) {
        List<RuleReasons.Placed> written = new ArrayList<>();
        for (int i = 0; i < stopped.length; i++) {
            written.add(new RuleReasons.Placed(
                    new SourcePos(1, i + 1, new SourceId("one")),
                    WhereInTheRule.theRuleItself(), stopped[i]));
        }
        return of(new Weakening.ModelReadingIncomplete(ClosureGap.QuestionUnanswered.of(
                StandingQuestion.Exact.of(cited,
                        new InputQuestion.AboutAPosition(TermPath.of("x")),
                        new WhatAQuestionStandsOn(RuleReasons.from(written), Optional.empty())))));
    }

    /** The same, for a question that stands on what its rule left and on its position's answer. */
    private static WeakeningSet standingQuestion(RuleCitation cited,
                                                 BlockReason.RuleReadingStopped stopped,
                                                 BlockReason.AnswerRealizationStopped answer) {
        return of(new Weakening.ModelReadingIncomplete(ClosureGap.QuestionUnanswered.of(
                StandingQuestion.Exact.of(cited,
                        new InputQuestion.AboutAPosition(TermPath.of("x")),
                        new WhatAQuestionStandsOn(RuleReasons.one(stopped),
                                Optional.of(answer))))));
    }

    private static RuleRef.Comparison comparison() {
        return new RuleRef.Comparison("b", new SourceConstructOrigin(
                new WrittenOwner.Body("m", "b"), 1, 1, SourceConstruct.IF));
    }

    /**
     * One handle for that rule, reached at {@code line}.
     *
     * <p>Two handles of one rule are two places. Which of the two ways a rule is found is the
     * rule's own answer, so a comparison is reached where it was read and never by a name.
     */
    private static RuleCitation reachedAt(int line) {
        return new RuleCitation.WrittenAt(comparison(),Citation.of(new SourcePos(line, 1)));
    }

    private static WeakeningSet of(Weakening one) {
        return WeakeningSet.of(one);
    }
}
