package souther.compiler.publish;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.diag.SourcePos;
import souther.compiler.source.SourceId;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.WrittenOwner;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two handles a document writes apart are two values, and two it writes alike are one.
 *
 * <p>What this type is for. Choosing one of several takes a comparison, and a comparison that comes
 * out equal for two handles a reader can tell apart leaves the choice to whichever the set of them
 * iterated first — a document whose sentence turns on the order a walk happened to take.
 *
 * <p>So the property is the one the projection has to keep, in both directions. Two handles that are
 * one value are written the same way, which is what lets either be chosen; two that are written
 * differently are not one value, which is what stops the choice from being arbitrary. A rule's kind
 * is in the sentence, so it is in the projection — left out, a comparison and a rule about the
 * strings at a position written in one place came to one value and were printed two ways.
 *
 * <p>And the order agrees with the equality, because both are asked of the same value. A comparison
 * answering zero where {@code equals} answers false is a pair the sort calls interchangeable and the
 * reader does not.
 */
class TwoHandlesADocumentWritesApartAreTwoValuesTest {

    private static final SourceId WHERE = new SourceId("0");

    private static final Citation AT = Citation.of(new SourcePos(9, 1, WHERE));

    /**
     * Two rules with no name, written at one place, of the two kinds there are.
     *
     * <p>One place, because what is being asked is whether the kind survives the projection. Told
     * apart by where they are, this pair would say nothing about the word.
     */
    private static final RuleRef.Comparison COMPARISON = new RuleRef.Comparison("b",
            new SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 0, 0,
                    SourceConstruct.BINARY));

    private static final RuleRef.Predicate PREDICATE = new RuleRef.Predicate("b",
            new SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 1, 0,
                    SourceConstruct.CALL));

    @Test
    void aComparisonAndAPredicateAtOnePlaceAreTwoHandles() {
        PublishedRuleHandle comparison =
                PublishedRuleHandle.of(new RuleCitation.WrittenAt(COMPARISON, AT));
        PublishedRuleHandle predicate =
                PublishedRuleHandle.of(new RuleCitation.WrittenAt(PREDICATE, AT));

        assertNotEquals(comparison, predicate,
                "a reader sent to a line and a reader sent to a set are told two things");
        assertNotEquals(0, Integer.signum(comparison.compareTo(predicate)),
                "so the order tells them apart rather than calling either the one to write");
    }

    /**
     * And which of the two a caller had first decides nothing.
     *
     * <p>The failure this is about, said as what a run does: the same pair, offered the other way
     * round, comes back with the same answer.
     */
    @Test
    void whichOfThemACallerHadFirstDecidesNothing() {
        RuleCitation comparison = new RuleCitation.WrittenAt(COMPARISON, AT);
        RuleCitation predicate = new RuleCitation.WrittenAt(PREDICATE, AT);

        assertEquals(PublicationOrders.handleFor(List.of(comparison, predicate)),
                PublicationOrders.handleFor(List.of(predicate, comparison)),
                "the handle a document writes is not the one a set of them iterated first");
    }

    /**
     * Two handles that are equal are written the same way, and two that are not are not.
     *
     * <p>The property in full, over a population that varies each part of each kind of sentence in
     * turn. A part left out of the projection shows up as two of these being one value while the
     * two sentences differ.
     */
    @Test
    void twoHandlesAreOneValueExactlyWhereADocumentWritesThemAlike() {
        List<RuleCitation> population = everyShapeOfSentence();
        for (RuleCitation one : population) {
            for (RuleCitation other : population) {
                PublishedRuleHandle here = PublishedRuleHandle.of(one);
                PublishedRuleHandle there = PublishedRuleHandle.of(other);
                boolean written = said(one).equals(said(other));

                assertEquals(written, here.equals(there),
                        () -> "one value exactly where a document writes them alike: "
                                + said(one) + " and " + said(other));
                assertEquals(written, here.compareTo(there) == 0,
                        () -> "and the order agrees with the equality: "
                                + said(one) + " and " + said(other));
            }
        }
    }

    /**
     * One handle of every shape of sentence a document writes.
     *
     * <p>Varied one part at a time: the kind of rule, whose rule it is, and where it is. Two of
     * these that a document writes alike are the pair either of which may be chosen, and every
     * other pair is one it has to tell apart.
     *
     * <p>Code out of sight is not among them, and it is not being passed over: a citation of it is
     * made where a source is placed and there is no way to write one from out here. What such a
     * handle says is held over a compilation instead, where one arises
     * ({@code ARuleWithoutALineIsNamedByTheRule}).
     */
    private static List<RuleCitation> everyShapeOfSentence() {
        RuleRef.Named named = new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 0),
                Optional.of(new ClauseName("cap"))));
        RuleRef.Named alsoNamed = new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 1),
                Optional.of(new ClauseName("floor"))));
        return List.of(
                new RuleCitation.Named(named),
                new RuleCitation.Named(alsoNamed),
                new RuleCitation.WrittenAt(COMPARISON, AT),
                new RuleCitation.WrittenAt(PREDICATE, AT),
                new RuleCitation.WrittenAt(COMPARISON,
                        Citation.of(new SourcePos(10, 1, WHERE))),
                new RuleCitation.WrittenAt(COMPARISON,
                        Citation.of(new SourcePos(9, 1))));
    }

    private static String said(RuleCitation cited) {
        return cited.said(SourceNameResolver.identity(), null);
    }

    /** That the population above is one a document writes more than one sentence for, so the pairs
     *  it is asked about are pairs. */
    @Test
    void thePopulationHoldsSentencesADocumentWritesApart() {
        assertTrue(everyShapeOfSentence().stream().map(
                        TwoHandlesADocumentWritesApartAreTwoValuesTest::said)
                .distinct().count() > 1,
                "a population a document writes one sentence for says nothing about telling two"
                        + " apart");
    }
}
