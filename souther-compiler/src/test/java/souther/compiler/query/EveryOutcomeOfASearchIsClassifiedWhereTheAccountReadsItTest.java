package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.observe.Incompleteness;
import souther.compiler.partition.CompositionBudget;
import souther.compiler.partition.CompositionRepertoire;
import souther.compiler.partition.Generator;
import souther.compiler.partition.ConditionOccurrence;
import souther.compiler.partition.ConditionReportAnchor;
import souther.compiler.partition.OnTheWay;
import souther.compiler.partition.WayToTheBorder;
import souther.compiler.publish.PublicationOrders;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every outcome a search can come to says which of three things it is to an account, and it says it
 * where the account asks.
 *
 * <p>What an account puts to a search is one question: did anything show a row can be written here,
 * was a showing stopped by a budget of this compiler's, or is there neither. There are more
 * outcomes than the question has answers, so the map between them is where a mistake lives — and
 * the mistake it has made before is an outcome falling to whichever arm a reader wrote last, which
 * turns this compiler's own limit into the model refusing a row.
 *
 * <p><b>The outcomes are enumerated from the type and not from a list here.</b> An outcome added is
 * one this has no representative for, and what fails is the count rather than a case quietly
 * getting the answer its neighbour has.
 */
class EveryOutcomeOfASearchIsClassifiedWhereTheAccountReadsItTest {

    private static final ConditionOccurrence MET = new ConditionOccurrence("b", 0);

    private static final WayToTheBorder WAY = new WayToTheBorder(List.of(
            new OnTheWay.Declined(MET,
                    new ConditionReportAnchor.WhereTheReadingMetIt("m", MET),
                    new OnTheWay.Why.NoWordsForTheShape())));

    /** One of each outcome, by the leaf that names it. */
    private static Map<Class<?>, ItemAssessment.Attempt> oneOfEach() {
        Map<Class<?>, ItemAssessment.Attempt> out = new LinkedHashMap<>();
        for (ItemAssessment.Attempt each : List.of(certified(), unverified(), stopped(),
                unexhausted(), limited(), unplanned(), unresolved(), unavailable())) {
            out.put(each.getClass(), each);
        }
        return out;
    }

    /**
     * Every outcome the type declares has one here, so what follows is about all of them.
     *
     * <p>The first thing this checks and the reason the rest is worth reading. A representative
     * missing is an outcome nothing below says anything about, and the tests would go on passing
     * while the account had a case nobody had classified.
     */
    @Test
    void everyOutcomeTheTypeDeclaresHasOneHere() {
        List<String> declared = new ArrayList<>();
        for (Class<?> each : ItemAssessment.Attempt.class.getPermittedSubclasses()) {
            declared.add(each.getSimpleName());
        }
        List<String> held = new ArrayList<>();
        oneOfEach().keySet().forEach(each -> held.add(each.getSimpleName()));

        assertEquals(declared.stream().sorted().toList(), held.stream().sorted().toList(),
                "an outcome a search can come to and nothing here is a representative of");
    }

    /**
     * What each outcome tells an account about a row being writable at the point.
     *
     * <p>Read through the capability rather than by naming the leaves: an outcome says whether a
     * budget of this compiler's stopped it by implementing {@code Prevented}, and this is what that
     * comes to where the answer is read.
     */
    @Test
    void anOutcomeSaysAShowingWasStoppedExactlyWhereABudgetStoppedIt() {
        oneOfEach().forEach((leaf, attempt) -> {
            WritabilityKnowledge knowledge = WritabilityKnowledge.of(nothingShown(),
                    SearchOutcomes.of(attempt));
            if (attempt instanceof ItemAssessment.Attempt.Prevented it) {
                assertEquals(WritabilityKnowledge.Prevented.by(it.by()), knowledge,
                        () -> leaf.getSimpleName() + " is a showing a budget stopped, and what"
                                + " stopped it is what it hands over");
            } else {
                assertInstanceOf(WritabilityKnowledge.NoEvidence.class, knowledge,
                        () -> leaf.getSimpleName() + " met no budget on the way to an answer, so"
                                + " calling it prevented would say this compiler was stopped where"
                                + " it was not");
            }
        });
    }

    /**
     * And where that leaves the obligation, which is the whole of what the account is for.
     *
     * <p>A point every row was read against and none is at. A budget of this compiler's leaves it
     * counted and undecided; anything else leaves it out of the count, because nothing has shown
     * the row an author would be told to write is one that exists.
     */
    @Test
    void aBudgetLeavesTheObligationCountedAndNothingElseDoes() {
        oneOfEach().forEach((leaf, attempt) -> {
            ObligationDisposition disposition = ObligationDisposition.of(
                    new ObligationCoverage.Missed(),
                    WritabilityKnowledge.of(nothingShown(), SearchOutcomes.of(attempt)));
            if (attempt instanceof ItemAssessment.Attempt.Prevented) {
                // Which question is open, rather than what it is open on. What stopped the showing
                // is the attempt's own and is read back from it two rows above; asserted here it
                // would move with whatever this table produced.
                assertEquals(List.of(
                                ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.class),
                        assertInstanceOf(ObligationDisposition.Undecided.class, disposition)
                                .because().written().stream()
                                .map(ObligationDisposition.Uncertainty::question).toList(),
                        () -> leaf.getSimpleName() + " is this compiler being stopped, which is not"
                                + " the model refusing a row and may not take one out of the count");
            } else {
                assertEquals(ObligationDisposition.Undecided.about(List.of(
                                new ObligationDisposition.Uncertainty
                                        .WhetherARowCanBeWritten.NothingShowedIt())),
                        disposition,
                        () -> leaf.getSimpleName() + " has shown nothing and was stopped by"
                                + " nothing, which is still this compiler and not the model: the"
                                + " point stays owed and nobody can say whether a row fits it");
            }
        });
    }

    /**
     * The order the searches are folded in does not change what the account reads.
     *
     * <p>The law a rank cannot satisfy. Two searches of one point stopped by two figures are two
     * pieces of work and neither stands for the other, so what comes out has to hold both — and
     * holding both is what makes the answer the same whichever was walked first.
     */
    @Test
    void theOrderTheSearchesAreFoldedInDoesNotChangeTheAnswer() {
        List<ItemAssessment.Attempt> made =
                List.of(unresolved(), stopped(), stoppedBy(CompositionBudget.STEPS_A_SEARCH_MAY_TAKE),
                        unverified());

        WritabilityKnowledge forward = WritabilityKnowledge.of(nothingShown(),
                new SearchOutcomes(made));
        WritabilityKnowledge backward = WritabilityKnowledge.of(nothingShown(),
                new SearchOutcomes(made.reversed()));

        assertEquals(forward, backward, "the searches of one point are a set of facts about it");
        WritabilityKnowledge.Prevented held = assertInstanceOf(
                WritabilityKnowledge.Prevented.class, forward);
        assertEquals(List.of(EstablishmentGap.Observation.of(
                        EnumSet.of(Incompleteness.Code.VALUE_TRUNCATED)),
                        EstablishmentGap.Composition.of(EnumSet.of(
                                CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS,
                                CompositionBudget.STEPS_A_SEARCH_MAY_TAKE))),
                held.by().written(),
                "and every figure that would have to give is there, none of them ranked away");
    }

    /**
     * Gaps of one kind arriving apart are the same as one gap holding both.
     *
     * <p>Which is what makes the law above an equality rather than a set of values that agree. Two
     * searches each naming one code are the same thing as one naming both, so a reader comparing
     * two accounts is comparing what was established and not how it arrived.
     */
    @Test
    void howTheGapsWereSplitOnTheWayIsNotSomethingAnAccountHolds() {
        WritabilityKnowledge apart = WritabilityKnowledge.Prevented.of(Set.of(
                EstablishmentGap.Observation.of(EnumSet.of(Incompleteness.Code.VALUE_TRUNCATED)),
                EstablishmentGap.Observation.of(EnumSet.of(Incompleteness.Code.VALUE_UNREADABLE))));
        WritabilityKnowledge together = WritabilityKnowledge.Prevented.by(
                EstablishmentGap.Observation.of(EnumSet.of(Incompleteness.Code.VALUE_TRUNCATED,
                        Incompleteness.Code.VALUE_UNREADABLE)));

        assertEquals(together, apart,
                "two observations naming one cause each are one observation naming both");
    }

    /** A row read back is grounds, and grounds outrank nothing — they answer a different question. */
    @Test
    void aRowReadBackIsGroundsWhateverElseWasStopped() {
        ItemAssessment.Owed owed = new ItemAssessment.Owed(
                new souther.compiler.partition.Criterion.AtTheLevel(
                        souther.compiler.partition.Level.ACount.of(1)),
                new Measurement.Complete<>(new ItemAssessment.Coverage.NoHit()),
                ItemAssessment.WritabilityProjection.UNPROVEN,
                new SearchOutcomes(List.of(stopped(), certified())));

        assertTrue(owed.writabilityEvidence().has(
                        ItemAssessment.WritabilityEvidence.Ground.A_VALUE_WAS_BUILT),
                "one search read a row back, and no other search takes that back");
        assertInstanceOf(WritabilityKnowledge.Established.class,
                WritabilityKnowledge.of(owed.writabilityEvidence(), owed.searches()),
                "so the point is established, and what stopped the other search is not the answer");
    }

    private static ItemAssessment.WritabilityEvidence nothingShown() {
        return ItemAssessment.WritabilityEvidence.of(Set.of());
    }

    private static ItemAssessment.Attempt certified() {
        return ItemAssessment.Attempt.Built.certified(
                new Generator.GeneratedRow(new Generator.Purpose.ForAPoint("p.x = 11"), List.of()),
                WAY);
    }

    private static ItemAssessment.Attempt unverified() {
        return new ItemAssessment.Attempt.Unverified(
                new Generator.GeneratedRow(new Generator.Purpose.ForAPoint("p.x = 11"), List.of()),
                WAY, List.of(),
                EstablishmentGap.Observation.of(EnumSet.of(Incompleteness.Code.VALUE_TRUNCATED)));
    }

    private static ItemAssessment.Attempt stopped() {
        return stoppedBy(CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS);
    }

    private static ItemAssessment.Attempt stoppedBy(CompositionBudget budget) {
        return new ItemAssessment.Attempt.Stopped(
                new Generator.UnresolvedCombination(List.of("p.x = 11"),
                        Generator.UnresolvedCombination.Reason.wordFor(Set.of(budget))),
                WAY, List.of(), PublicationOrders.COMPOSITION_BUDGETS.keep(EnumSet.of(budget)),
                PublicationOrders.COMPOSITION_REPERTOIRES.keep(List.of()));
    }

    /**
     * A search that ran to the end of what this compiler writes, which is not the end of what there
     * is.
     *
     * <p>Written with no figure at all, which is the half of this the one above cannot have. Given
     * a budget as well, the representative would be a stop by another name and would say nothing
     * about the case this arm exists for: a point left open by work nobody has done rather than by
     * a number somebody could raise.
     */
    private static ItemAssessment.Attempt unexhausted() {
        return new ItemAssessment.Attempt.Unexhausted(
                new Generator.UnresolvedCombination(List.of("List.sum(p.xs) = 7"),
                        Generator.UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED),
                WAY, List.of(), PublicationOrders.COMPOSITION_REPERTOIRES.keep(
                        EnumSet.of(CompositionRepertoire.WAYS_A_TOTAL_IS_SPREAD)));
    }

    /**
     * A search whose own answer was about less than the point had.
     *
     * <p>Written with a word no budget comes back with, which is the half of this that the one
     * above cannot have. Given the word its figure would produce, the representative would agree
     * with {@link ItemAssessment.Attempt.Stopped} by construction and say nothing about the case
     * this arm exists for.
     */
    private static ItemAssessment.Attempt limited() {
        return new ItemAssessment.Attempt.Limited(
                new Generator.UnresolvedCombination(List.of("p.x = 11"),
                        Generator.UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED),
                WAY, List.of(), PublicationOrders.COMPOSITION_BUDGETS.keep(
                        EnumSet.of(CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS)));
    }

    /**
     * A point no search was made for, which the account reads as prevented like any other.
     *
     * <p>Its word says no search happened. Given one a search comes back with, this and {@link
     * ItemAssessment.Attempt.Limited} would be one state a reader tells apart by which fields were
     * filled in.
     */
    private static ItemAssessment.Attempt unplanned() {
        return new ItemAssessment.Attempt.Unplanned(
                new Generator.UnresolvedCombination(List.of("p.x = 11"),
                        Generator.UnresolvedCombination.Reason
                                .NO_READING_OF_THE_LINE_COULD_BE_SEARCHED),
                WAY, List.of(), PublicationOrders.COMPOSITION_BUDGETS.keep(
                        EnumSet.of(CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS)));
    }

    private static ItemAssessment.Attempt unresolved() {
        return new ItemAssessment.Attempt.Unresolved(
                new Generator.UnresolvedCombination(List.of("p.x = 11"),
                        Generator.UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED), WAY);
    }

    private static ItemAssessment.Attempt unavailable() {
        return new ItemAssessment.Attempt.Unavailable(
                ItemAssessment.Attempt.Reason.NO_CLASSES);
    }
}
