package souther.compiler.publish;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.RowIdentity;
import souther.compiler.observe.RowRef;
import souther.compiler.observe.Target;
import souther.compiler.source.SourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order a document says what a module could not read in is the document's, whatever order the
 * readers met the reasons in.
 *
 * <p>What is asked here is the property the array could not have while an account handed it over in
 * the order a walk filled a collection: that two runs meeting the same facts write the same
 * document. So the same facts are handed over in every order there is of them, and what comes out
 * is one sequence.
 *
 * <p>Over the entries a document writes and not over the report, because that is where the order is
 * put on. A test over the whole report would pass for a model whose facts happen to be met in one
 * order, which is every model this compiler is tested against today.
 */
class WhatWentUnreadIsWrittenInOneOrderHoweverItWasMetTest {

    private static final PublishedIncompleteness A_ROW = entry(
            Incompleteness.Code.ROW_UNDECIDED, row(1), 4, 1);
    private static final PublishedIncompleteness A_POSITION = entry(
            Incompleteness.Code.VALUE_UNREADABLE, new Target.AtPosition("take", "x"), 9, 3);
    private static final PublishedIncompleteness A_BEHAVIOR = entry(
            Incompleteness.Code.LINKAGE_FAILED, new Target.OfBehavior("take"), 2, 7);
    private static final PublishedIncompleteness A_MODULE = entry(
            Incompleteness.Code.INSTRUMENTATION_ABSENT, new Target.OfModule("m"), 1, 1);

    @Test
    void everyOrderTheyCouldBeMetInIsWrittenTheSameWay() {
        List<PublishedIncompleteness> facts =
                List.of(A_ROW, A_POSITION, A_BEHAVIOR, A_MODULE);
        List<PublishedIncompleteness> written =
                PublicationOrders.WHAT_WENT_UNREAD.arrange(facts).written();

        for (List<PublishedIncompleteness> met : everyOrderOf(facts)) {
            assertEquals(written, PublicationOrders.WHAT_WENT_UNREAD.arrange(met).written(),
                    () -> "met as " + met);
        }
        assertEquals(24, everyOrderOf(facts).size(), "four facts are met in twenty-four orders");
    }

    /** And it is the narrowest thing that went unread first, which is the one an author can act on
     *  without reading the rest of the report. */
    @Test
    void theNarrowestThingThatWentUnreadIsSaidFirst() {
        assertEquals(List.of(A_ROW, A_POSITION, A_BEHAVIOR, A_MODULE),
                PublicationOrders.WHAT_WENT_UNREAD
                        .arrange(List.of(A_MODULE, A_BEHAVIOR, A_POSITION, A_ROW)).written());
    }

    /** Two facts alike but for where they were met are two entries, in the order of the places. */
    @Test
    void twoPlacesForOneWordAreWrittenNearestTheTopOfTheFileFirst() {
        PublishedIncompleteness near = entry(Incompleteness.Code.ROW_UNDECIDED, row(1), 4, 1);
        PublishedIncompleteness far = entry(Incompleteness.Code.ROW_UNDECIDED, row(2), 40, 1);

        assertEquals(List.of(near, far),
                PublicationOrders.WHAT_WENT_UNREAD.arrange(List.of(far, near)).written());
    }

    /**
     * A fact met at several places is written at one of them, and at the same one every run.
     *
     * <p>Chosen from the places and never from the citations. A citation that sends a reader
     * nowhere is not a place, so it is no candidate — and a fact met only at those is a fact this
     * writes no place for.
     */
    @Test
    void oneFactMetAtSeveralPlacesIsWrittenAtTheFirstOfThem() {
        Citation first = Citation.of(pos(2, 1));
        Citation later = Citation.of(pos(9, 4));

        assertEquals(PublicationOrders.placeFor(List.of(first, later)),
                PublicationOrders.placeFor(List.of(later, first)),
                "which of them a reader met first decides nothing");
        assertEquals(Optional.of(pos(2, 1)),
                PublicationOrders.placeFor(List.of(later, first)).map(PublishedAt::at));
    }

    /** And a citation with nowhere to send a reader takes no part in the choosing. */
    @Test
    void aCitationThatSendsAReaderNowhereIsNoPlace() {
        assertTrue(PublishedAt.of(Citation.of(new SourcePos(1, 1))).isEmpty(),
                "a position in a text this compilation cannot name is not a place");
        assertEquals(Optional.empty(), PublicationOrders.placeFor(Set.of()));
    }

    /**
     * Two entries this order cannot tell apart are refused rather than written.
     *
     * <p>The property the arrangement rests on, asked of the arrangement rather than assumed of the
     * order: where a comparison came out equal for two entries that are not equal, which of them a
     * document wrote first would be whichever the caller handed over first.
     */
    @Test
    void twoEntriesOneOrderCannotTellApartAreRefused() {
        CanonicalArrangement.Order<String> blind =
                CanonicalArrangement.Order.by((_, _) -> 0);

        assertEquals(List.of("a", "a"), blind.arrange(List.of("a", "a")).written(),
                "two entries a document writes alike are two entries");
        assertThrows(IllegalArgumentException.class, () -> blind.arrange(List.of("a", "b")));
    }

    private static PublishedIncompleteness entry(Incompleteness.Code code, Target about,
                                                 int line, int column) {
        return new PublishedIncompleteness(new Incompleteness.Fact(code, about),
                PublishedAt.of(Citation.of(pos(line, column))), false);
    }

    private static Target row(int ordinal) {
        return new Target.OfRow(new RowRef("take", new SourceId("0"),
                new RowIdentity.Unnamed(ordinal)));
    }

    private static SourcePos pos(int line, int column) {
        return new SourcePos(line, column, new SourceId("0"));
    }

    /** Every order the facts could be met in, so that nothing here is asked of one of them. */
    private static <T> List<List<T>> everyOrderOf(List<T> of) {
        if (of.isEmpty()) {
            return List.of(List.of());
        }
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < of.size(); i++) {
            List<T> rest = new ArrayList<>(of);
            T here = rest.remove(i);
            for (List<T> tail : everyOrderOf(rest)) {
                List<T> one = new ArrayList<>();
                one.add(here);
                one.addAll(tail);
                out.add(List.copyOf(one));
            }
        }
        return List.copyOf(out);
    }
}
