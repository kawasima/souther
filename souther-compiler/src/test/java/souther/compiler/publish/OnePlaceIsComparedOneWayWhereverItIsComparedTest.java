package souther.compiler.publish;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.check.RuleReportAnchor;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.source.SourceId;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.WrittenOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A place is compared one way wherever this compiler compares places.
 *
 * <p>Two things this document chooses are chosen out of places: which of several a fact is written
 * at, and which of several handles a rule is reached by. Written as two comparisons, the two came
 * apart — a line and a column compared as text put line ten before line nine, while the order over
 * places put line nine first, so one thing had two orders and which a reader saw turned on which
 * question was being asked.
 *
 * <p>So what is asked here is that the two agree, over a pair that tells them apart. A comparison
 * of its own would answer this by being written the same way twice, which is the arrangement this
 * exists to say is not enough.
 */
class OnePlaceIsComparedOneWayWhereverItIsComparedTest {

    private static final Citation EARLIER = Citation.of(pos(9, 1));
    private static final Citation LATER = Citation.of(pos(10, 1));

    /** One rule with no name, so that what tells two handles of it apart is where each was
     *  reached. */
    private static final RuleRef.Comparison WRITTEN_RATHER_THAN_NAMED =
            new RuleRef.Comparison("b", new SourceConstructOrigin(
                    new WrittenOwner.Body("m", "b"), 0, 0, SourceConstruct.BINARY));

    /** And one the author named, which is reached by that name from anywhere. */
    private static final RuleRef.Invariant NAMED = new RuleRef.Invariant(new Clause.Ref(
            new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 0),
            Optional.empty()));

    /**
     * The ways in this reading has met the rule at, numbered as the reading that met them numbers
     * them: against the place, so that one place met twice is one way in.
     */
    private static final List<Citation> WAYS_IN = new ArrayList<>();

    /** Where each way in leads, which is what a citation no longer carries and what a document
     *  asks for when it writes the sentence. */
    private static final PublishedRuleHandle.WhereARuleIs PLACES = cited -> switch (cited.anchor()) {
        case RuleReportAnchor.ByTheModuleThatWroteIt _ -> EARLIER;
        case RuleReportAnchor.ByTheReadingThatMetIt(String _, String _, int reach) ->
                WAYS_IN.get(reach);
    };

    private static RuleCitation writtenAt(Citation at) {
        int reach = WAYS_IN.indexOf(at);
        if (reach < 0) {
            reach = WAYS_IN.size();
            WAYS_IN.add(at);
        }
        return new RuleCitation.Written(WRITTEN_RATHER_THAN_NAMED,
                new RuleReportAnchor.ByTheReadingThatMetIt("m", "b", reach));
    }

    @Test
    void theHandleAndThePlaceAgreeAboutWhichOfTwoLinesComesFirst() {
        Optional<PublishedAt> place = PublicationOrders.placeFor(List.of(LATER, EARLIER));
        Optional<RuleCitation> handle = PublicationOrders.handleFor(List.of(
                writtenAt(LATER), writtenAt(EARLIER)), PLACES);

        assertEquals(Optional.of(9), place.map(PublishedAt::line),
                "the place nearest the top of the file is the one a fact is written at");
        assertEquals(Optional.of(writtenAt(EARLIER)), handle,
                "and the handle at that same place is the one a rule is reached by");
    }

    /**
     * And two handles at one position that a report writes differently are told apart by the same
     * comparison, because there is only the one.
     *
     * <p>A rule written here and a rule reached from here are the same source, line and column and
     * two different sentences. A comparison of handles that stopped at the numbers left the choice
     * between them to whichever the caller's set iterated first — the thing the fold below exists
     * to have removed. Nothing here builds such a pair: the arm that carries one is made where a
     * body is spliced, and this holds instead that a handle is compared by the order over places,
     * which is what tells that pair apart.
     */
    @Test
    void aHandleIsComparedByTheOrderOverPlaces() {
        assertEquals(PublicationOrders.placeFor(List.of(EARLIER, LATER)),
                PublicationOrders.handleFor(List.of(
                                writtenAt(EARLIER),
                                writtenAt(LATER)), PLACES)
                        .map(each -> PLACES.of((RuleCitation.Written) each))
                        .flatMap(PublishedAt::of),
                "one order over places, asked twice");
    }

    /**
     * And two rules written in a text this compilation cannot name are told apart by where.
     *
     * <p>The document's own field for a place takes a source, so there is none for these — and the
     * sentence about a rule prints the numbers all the same, so a reader sees two. Told apart by
     * the field's shape alone, the two came out as one value and which was written fell to
     * whichever the set of handles iterated first.
     */
    @Test
    void twoRulesInATextThisCannotNameAreToldApartByWhereTheyAre() {
        RuleCitation earlier = writtenAt(Citation.of(new SourcePos(1, 1)));
        RuleCitation later = writtenAt(Citation.of(new SourcePos(2, 2)));

        assertEquals(PublicationOrders.handleFor(List.of(earlier, later), PLACES),
                PublicationOrders.handleFor(List.of(later, earlier), PLACES),
                "which of the two a caller had first decides nothing");
        assertEquals(Optional.of(earlier), PublicationOrders.handleFor(List.of(later, earlier), PLACES),
                "and the one nearest the top of the text is the one a document writes");
    }

    /** And a place a reader can be sent to comes before one nobody can. */
    @Test
    void aPlaceAReaderCanBeSentToComesBeforeOneNobodyCan() {
        RuleCitation held = writtenAt(EARLIER);
        RuleCitation unnamed = writtenAt(Citation.of(new SourcePos(1, 1)));

        assertEquals(Optional.of(held),
                PublicationOrders.handleFor(List.of(unnamed, held), PLACES),
                "a reader sent to a file is better served than one sent to two numbers");
    }

    /** A name the author gave comes before a place they did not. */
    @Test
    void aNameComesBeforeAPlace() {
        assertEquals(Optional.of(new RuleCitation.Named(NAMED)),
                PublicationOrders.handleFor(List.of(
                        writtenAt(EARLIER), new RuleCitation.Named(NAMED)), PLACES));
    }

    private static SourcePos pos(int line, int column) {
        return new SourcePos(line, column, new SourceId("0"));
    }
}
