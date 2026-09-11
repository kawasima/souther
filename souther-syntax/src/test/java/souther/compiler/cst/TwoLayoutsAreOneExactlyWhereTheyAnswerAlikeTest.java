package souther.compiler.cst;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Placement;
import souther.compiler.diag.PhysicalPos;
import souther.compiler.diag.SourcePos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two layouts are one value exactly where they answer alike.
 *
 * <p>A layout is asked one thing: where a place sits in the text as it now stands. So two of them
 * being one value has to mean that they answer that the same way for every place either can be
 * asked — because what holds one compares it against the one an edit replaced it with, and keeps
 * what it had wherever the two are equal. An equality that is coarser than the answer is a reader
 * kept on numbers the new text does not have.
 *
 * <p>Which is why the constructs are part of it. A place names a token <em>of a construct</em>, so
 * the same tokens falling into constructs differently is a different set of answers. Compared by
 * where the tokens sit with the constructs run together, the two texts below came out equal while
 * disagreeing about where five of the places they can both be asked about are.
 */
class TwoLayoutsAreOneExactlyWhereTheyAnswerAlikeTest {

    /**
     * Texts whose tokens sit in exactly the same lines and columns, parsed into different
     * constructs.
     *
     * <p>A witness rather than an argument. {@code fake} and {@code data} are the same width and
     * begin different things, so every token of the two texts is written where the other's is and
     * the last line is one construct in one and two in the other.
     */
    private static final String AS_DATA = """
            module m

            data A = Int
            data B = Int
            """;

    private static final String AS_A_FAKE = AS_DATA.replace("data B", "fake B");

    /** Every place either layout can be asked about, over the constructs and tokens they hold. */
    private static Map<SourcePos, PhysicalPos> answers(SourceLayout laidOut) {
        Map<SourcePos, PhysicalPos> out = new LinkedHashMap<>();
        for (int construct = 0; construct < 6; construct++) {
            for (int token = 0; token < 6; token++) {
                SourcePos place = Placement.aTextWithNoIdentity().at(construct, token, 0);
                out.put(place, laidOut.resolve(place));
            }
        }
        return out;
    }

    /**
     * The witness answers differently, so the two are not one value.
     *
     * <p>Stated as the disagreement first. A layout that is equal to one answering differently is
     * the defect; the places it disagrees at are what a reader kept across the edit would have been
     * shown.
     */
    @Test
    void twoLayoutsThatAnswerDifferentlyAreNotOneValue() {
        SourceLayout asData = SourceLayout.of(AS_DATA);
        SourceLayout asAFake = SourceLayout.of(AS_A_FAKE);

        List<SourcePos> apart = new ArrayList<>();
        answers(asData).forEach((place, sits) -> {
            if (!sits.equals(answers(asAFake).get(place))) {
                apart.add(place);
            }
        });
        assertTrue(!apart.isEmpty(),
                "the two texts answer differently somewhere, or this witnesses nothing");

        assertNotEquals(asData, asAFake,
                () -> "they disagree about where " + apart + " is, so they are not one layout");
    }

    /**
     * And the witness is a witness: the tokens of the two sit in the same places.
     *
     * <p>The control. Two texts that differ in where their tokens sit are told apart by that
     * alone, and would say nothing about whether the constructs are part of the answer.
     */
    @Test
    void andTheTwoTextsWriteTheirTokensInTheSameLinesAndColumns() {
        assertNotEquals(AS_DATA, AS_A_FAKE, "the two texts differ");

        assertEquals(whereTheTokensSit(AS_DATA), whereTheTokensSit(AS_A_FAKE),
                "every token of the one is written where the other's is, so what tells the two"
                        + " layouts apart is which constructs they fall into and nothing else");
    }

    /** Where the meaningful tokens of {@code source} sit, in the order they are written. */
    private static List<PhysicalPos> whereTheTokensSit(String source) {
        SourceLayout laidOut = SourceLayout.of(source);
        List<PhysicalPos> out = new ArrayList<>();
        PhysicalPos last = null;
        for (int offset = 0; offset < source.length(); offset++) {
            SourcePos place = laidOut.placeAt(offset);
            if (place.within() != 0) {
                continue;
            }
            PhysicalPos sits = laidOut.resolve(place);
            if (!sits.equals(last)) {
                out.add(sits);
                last = sits;
            }
        }
        assertTrue(out.size() > 5, "a text of two declarations has tokens to ask about");
        return out;
    }

    /**
     * And a text laid out twice is the same layout, which is what the whole comparison is for.
     *
     * <p>A layout answers where a place sits, so what leaves it equal is an edit that moves no
     * token to another line or column — rewording a comment on a line of its own, and not writing a
     * comment above something, which moves every line under it. The places survive both; only the
     * second leaves what they resolve to where it was.
     */
    @Test
    void oneTextLaidOutTwiceIsOneLayout() {
        String commented = AS_DATA.replace("data A", "// what is held\ndata A");

        assertEquals(SourceLayout.of(AS_DATA), SourceLayout.of(AS_DATA));
        assertEquals(SourceLayout.of(AS_DATA).hashCode(), SourceLayout.of(AS_DATA).hashCode());
        assertEquals(SourceLayout.of(commented),
                SourceLayout.of(commented.replace("// what is held", "// what it holds")),
                "a comment reworded on its own line moves nothing, so what was read against this"
                        + " layout is not read again");
    }
}
