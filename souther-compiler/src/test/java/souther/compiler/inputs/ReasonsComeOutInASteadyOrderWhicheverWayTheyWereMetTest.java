package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a question stands on comes out the same however the walk met it.
 *
 * <p>No order of anybody's is claimed here: which of two reasons an author wrote first is settled
 * by where they wrote them, and that is asked where the places are. What is held is the other half
 * of the old claim, and it is a claim about this compiler rather than about a model — a projection
 * out of this reaches a document, so a sequence left to whichever order a walk happened to take
 * would have one compiler over one source publish two documents, and which one an author saw would
 * be the run they happened to make.
 *
 * <p>Measured by handing the same reasons over both ways round. Nothing else changes, so anything
 * that comes out different is settled by the order they arrived in and by nothing else.
 *
 * <p>Two shapes, because the fold and the order are two things. Reasons alike in everything but the
 * word are told apart by the word; reasons alike in the word and not in what they are about are
 * told apart by that, and the entries are two either way.
 */
class ReasonsComeOutInASteadyOrderWhicheverWayTheyWereMetTest {

    private static final BlockReason.RuleReadingStopped FORM =
            new BlockReason.UnreadComparisonForm();

    private static final BlockReason.RuleReadingStopped DOMAIN =
            new BlockReason.UnreadComparisonDomain();

    /** Two reasons about one thing are put in the order their vocabulary declares them in. */
    @Test
    void twoReasonsAboutOneThingArePutInTheOrderTheVocabularyDeclares() {
        assertEquals(RuleReasons.from(List.of(said(0, FORM), said(0, DOMAIN))).reasons(),
                RuleReasons.from(List.of(said(0, DOMAIN), said(0, FORM))).reasons(),
                "nothing an author wrote tells them apart, so nothing about how they were met may"
                        + " decide it either");
    }

    /**
     * And two about two things come out the same whichever was met first.
     *
     * <p>Which of them an author wrote first is not asked here and is not decided here. Any order
     * that is the same twice will do, and pinning one would make a decision about a document out of
     * a tie-break nobody reads.
     */
    @Test
    void andTwoReasonsAboutTwoThingsComeOutTheSameWhicheverWasMetFirst() {
        assertEquals(RuleReasons.from(List.of(said(0, FORM), said(1, DOMAIN))).said(),
                RuleReasons.from(List.of(said(1, DOMAIN), said(0, FORM))).said(),
                "which of them this compiler met first is a fact about the walk and about no"
                        + " model");
    }

    /** And two about two things are two entries, which is what the comparison above is over. */
    @Test
    void andTwoThingsAreTwoEntries() {
        assertEquals(2, RuleReasons.from(List.of(said(0, FORM), said(1, FORM))).said().size(),
                "an author wrote two of them and has two things to look at, and the word says"
                        + " neither");
    }

    /**
     * And two alike in the word are settled by what they are about, not by which was met first.
     *
     * <p>The half an order over the words alone cannot reach. Two of these agree about everything a
     * document prints except which thing they are about, so a comparison that reads the word finds
     * them equal and leaves them wherever the walk put them — which is the order this whole carrier
     * is here to keep out of a document.
     */
    @Test
    void andTwoAlikeInTheWordAreSettledByWhatTheyAreAbout() {
        assertEquals(RuleReasons.from(List.of(said(0, FORM), said(1, FORM))).said(),
                RuleReasons.from(List.of(said(1, FORM), said(0, FORM))).said(),
                "one word and two things it is about is still two entries, and which of them this"
                        + " compiler met first is a fact about the walk");
    }

    /** One reason about the part numbered {@code part}, sent to the rule as a whole. */
    private static RuleReasons.Said said(int part, BlockReason.RuleReadingStopped reason) {
        return new RuleReasons.Said(RuleSite.at(new PartId<>(new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("demo", "N")), 0),
                Optional.empty())), part)), RuleSite.theRuleItself(), reason);
    }
}
