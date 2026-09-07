package souther.compiler.check;

import souther.compiler.values.KnownExtents;
import souther.compiler.values.TextExtent;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Text;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a revision worked out about where sets stop goes when the revision moves.
 *
 * <p>The answer itself would survive it. Where a set's strings stop is settled by the set, so one
 * worked out under one revision is right under the next, and a table that outlived them all would
 * answer just as well. What it would also do is hold every set an author passed through on the way
 * to the one they meant, for as long as the process lives — which is what a lifetime is chosen to
 * stop, and why this is a fact about the lender rather than a fact about extents.
 *
 * <p>Beside the readings and dropped by the same move, since the two are shared on the same terms:
 * within one revision there is one world, and what was worked out in it is worked out of that
 * world.
 */
class WhatARevisionKnowsAboutSetsGoesWhenTheRevisionMovesTest {

    private static final ValueSet A_SET = new ValueSet.Finite(Set.of(new Value.Text("JP")));

    private static final TextExtent WHERE_IT_STOPS =
            new TextExtent.One(Text.of("JP"), Text.of("JQ"));

    @Test
    void whatWasWorkedOutUnderOneRevisionIsNotAnsweredUnderTheNext() {
        AtomicLong revision = new AtomicLong(1);
        KnownExtents known = new LentReadings(DeclarationReadings.NONE, revision::get,
                StoreWork.UNWATCHED).extents();

        known.remember(A_SET, WHERE_IT_STOPS);
        assertEquals(WHERE_IT_STOPS, known.of(A_SET),
                "worked out under this revision and answered under it");

        revision.incrementAndGet();
        assertNull(known.of(A_SET),
                "and not carried into a world it was not worked out in");
    }
}
