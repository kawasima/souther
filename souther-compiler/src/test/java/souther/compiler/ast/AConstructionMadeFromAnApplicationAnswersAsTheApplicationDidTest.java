package souther.compiler.ast;

import souther.compiler.diag.SourcePos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code T(v)} at a newtype is written as an application and means a construction, and the node that
 * replaces the application answers what the application answered.
 *
 * <p>Where a construction came from is settled when its source is read, and a desugaring is not a
 * reading — it changes which form says the same thing. So the application is what the transition
 * takes: an application carried into a body by a value that body named means a construction carried
 * the same way, and a pass turning one into the other has nothing of its own to say about it.
 *
 * <p>Asked of the transition rather than of a compile, because the answer is not observable in one:
 * the desugaring runs where every application it rewrites is still the body's own. What that costs
 * is that nothing would report the day it stops being true, which is what this is.
 *
 * <p>What is applied does not enter the question, so a literal stands there.
 */
class AConstructionMadeFromAnApplicationAnswersAsTheApplicationDidTest {

    private static final SourcePos SOMEWHERE = new SourcePos(3, 7);

    @Test
    void aConstructionMadeFromAnApplicationCarriesWhereTheApplicationCameFrom() {
        Hir.Apply carried = anApplication().carriedByValue();

        assertSame(carried.origin(), constructionOf(carried).origin(),
                "the application is what already answered, and the form replacing it says the same");
    }

    @Test
    void andOneMadeFromAnApplicationTheBodyWroteIsTheBodysOwn() {
        Hir.Apply own = anApplication();

        assertSame(own.origin(), constructionOf(own).origin(),
                "a body's own application means a construction the body wrote");
    }

    /** The control: the two applications answer differently, so the pair above is two answers and
     *  not one written twice. */
    @Test
    void andTheTwoApplicationsDoNotAnswerAlike() {
        assertNotEquals(anApplication().origin(), anApplication().carriedByValue().origin(),
                "a construction carried in by a value is not one the body wrote");
    }

    /** Where the application stands is the construction's too: one run of characters, read as what
     *  it means. */
    @Test
    void andTheConstructionStandsWhereTheApplicationDid() {
        Hir.Apply application = anApplication();

        assertSame(application.pos(), constructionOf(application).pos(),
                "the construction is what the characters at the application mean");
    }

    private static Hir.NewData constructionOf(Hir.Apply application) {
        return Hir.NewData.fromApply(application,
                new Hir.Name.Unanswered(WrittenName.synthetic("T", SOMEWHERE)),
                List.of(new Hir.FieldInit("value", new Hir.IntLit(1, SOMEWHERE, null), SOMEWHERE)));
    }

    private static Hir.Apply anApplication() {
        return Hir.Apply.synthetic(new Hir.IntLit(1, SOMEWHERE, null), List.of(),
                new souther.compiler.types.ApplicationOrigin.ComposedFixture(), SOMEWHERE, null);
    }
}
