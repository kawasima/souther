package souther.compiler.cst;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Placement;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.source.SourceId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A place read against a text it is not in is refused, whichever kind of text either is.
 *
 * <p>A place is which of the things written in <em>its</em> text it is. The same numbers name
 * something else in another text, so reading one against the other comes back as a line and a
 * column that looks like an answer and points at whatever happens to sit there. Refused, the same
 * mistake arrives as a mistake — which is how a marker for a problem written in two files was found
 * being resolved against the file the report was listed under.
 *
 * <p>Over every pair of texts and not over the pair that was found. Which kind of text a place is in
 * is a sum of three, two of which carry something to tell one text from another, and a compilation
 * holds both kinds at once: the files it is reading, and the texts it put back together out of what
 * the modules it read back published. Asked only of files, the pairs that cross the published arm
 * went through in silence.
 */
class APlaceIsReadAgainstTheTextItIsInTest {

    private static final String SOURCE = """
            module m

            data Amount = Int
            """;

    /**
     * Every kind of text a place is in, with two of each kind that carries a name.
     *
     * <p>Two apiece because one of each kind cannot tell "the same kind" from "the same text": a
     * check that refused across kinds and passed within one would be green over a population of
     * one each, and it is reading one module's published text as another's that this is for.
     */
    private static Map<String, Placement> texts() {
        Map<String, Placement> out = new LinkedHashMap<>();
        out.put("a file of this compile", Placement.aFileOfThisCompile(new SourceId("a.sou")));
        out.put("another file of this compile", Placement.aFileOfThisCompile(new SourceId("b.sou")));
        out.put("what lib.a published",
                Placement.whatAModulePublished(new SourceProvenance.APublishedModule("lib.a")));
        out.put("what lib.b published",
                Placement.whatAModulePublished(new SourceProvenance.APublishedModule("lib.b")));
        out.put("a text with no name", Placement.aTextWithNoIdentity());
        return out;
    }

    /** Whether a text carries anything to tell it from another. */
    private static boolean carriesAName(Placement text) {
        return !(text.at(0, 0).quotedFrom() instanceof QuotedFrom.TextItCannotName);
    }

    /**
     * Refused exactly where both texts say which they are and say different things.
     *
     * <p>Both directions in one claim, so that widening the refusal cannot be done by refusing
     * everything: a place is read against its own text far more often than against another's, and a
     * guard that stopped that would stop the compiler.
     */
    @Test
    void aPlaceIsRefusedExactlyWhereBothTextsAreNamedAndAreNotTheSameText() {
        List<String> refused = new ArrayList<>();
        List<String> owed = new ArrayList<>();
        texts().forEach((laidOutAs, layout) -> texts().forEach((placedIn, held) -> {
            if (carriesAName(layout) && carriesAName(held) && !laidOutAs.equals(placedIn)) {
                owed.add(laidOutAs + " <- " + placedIn);
            }
            if (refuses(layout, held)) {
                refused.add(laidOutAs + " <- " + placedIn);
            }
        }));

        assertEquals(owed, refused,
                "a place in one text read against the layout of another, wherever both texts say"
                        + " which they are");
    }

    /**
     * And the population is one this could be got wrong over.
     *
     * <p>The control the claim above needs. Written down as what the pairs come to rather than left
     * to the reader: a population where nothing is owed a refusal makes the equality above hold of
     * a guard that refuses nothing.
     */
    @Test
    void thePopulationHoldsPairsOfEveryKindThisCouldGetWrong() {
        List<String> kinds = new ArrayList<>();
        texts().values().forEach(text ->
                kinds.add(text.at(0, 0).quotedFrom().getClass().getSimpleName()));

        assertEquals(List.of("ASourceThisCompileHolds", "ASourceThisCompileHolds",
                        "TextItCannotShow", "TextItCannotShow", "TextItCannotName"),
                kinds,
                "every arm of what a place says about its text, and two of each that names one");
    }

    /** A place in its own text resolves, for every kind of text there is. */
    @Test
    void andAPlaceReadAgainstItsOwnTextIsAnswered() {
        texts().forEach((said, text) -> {
            SourceLayout laidOut = SourceLayout.of(SOURCE, text);
            assertNotNull(laidOut.resolve(laidOut.placeAt(SOURCE.indexOf("Amount"))),
                    () -> "a place read against the layout it came from, in " + said);
        });
    }

    /**
     * And a place this text does not hold is refused too, whatever text it says it is in.
     *
     * <p>The half the check above cannot make. Two texts nobody named match as far as anything here
     * can see, so a place from one read against the other gets through — and then it names a
     * construct or a token this text does not have, which is the same mistake arriving by the one
     * door the identity check has to leave open. Answered by moving it to the nearest token there
     * is, it came back as a line and a column that read like an answer.
     */
    @Test
    void andAPlaceThisTextDoesNotHoldIsRefusedWhereverItSaysItIsFrom() {
        SourceLayout laidOut = SourceLayout.of(SOURCE);

        assertThrows(SourceLayout.NoSuchPlace.class,
                () -> laidOut.resolve(Placement.aTextWithNoIdentity().at(9, 0, 0)),
                "a construct this text does not have");
        assertThrows(SourceLayout.NoSuchPlace.class,
                () -> laidOut.resolve(Placement.aTextWithNoIdentity().at(0, 9, 0)),
                "and a token that construct does not have");
        assertThrows(SourceLayout.NoSuchPlace.class,
                () -> laidOut.resolve(Placement.aTextWithNoIdentity().at(0, -1, 0)),
                "and there is no token before the first");
    }

    /**
     * A place running past the end of the text is not one of those: it is where a reader is sent.
     *
     * <p>Which of this text's things a place is at and how far past that thing it sits are two
     * questions. A report about a source the author stopped in the middle of draws its caret over
     * what was never typed, so it points at or past the end — and the end is where that reader goes.
     * Refused with the other two, rendering a syntax error would raise one.
     */
    @Test
    void andOneRunningPastTheEndOfTheTextSendsAReaderToTheEndOfIt() {
        SourceLayout laidOut = SourceLayout.of(SOURCE);
        SourcePos pastIt = laidOut.placeAt(SOURCE.indexOf("Int")).along(SOURCE.length());

        assertEquals(laidOut.resolve(laidOut.placeAt(SOURCE.length())), laidOut.resolve(pastIt),
                "as far as the text goes and no further");
        assertTrue(laidOut.offsetOf(pastIt) > SOURCE.length(),
                "while what it says about the text is left as it was said: where a reader is sent"
                        + " is the answer that is held to the text, and how far along is not");
    }

    /** Whether {@code layout}'s text refuses a place made against {@code held}'s. */
    private static boolean refuses(Placement layout, Placement held) {
        SourceLayout laidOut = SourceLayout.of(SOURCE, layout);
        SourcePos place = SourceLayout.of(SOURCE, held).placeAt(SOURCE.indexOf("Amount"));
        try {
            laidOut.resolve(place);
            return false;
        } catch (SourceLayout.NotThisText refused) {
            assertTrue(refused.getMessage().contains("read against the layout of"),
                    () -> "and says which layout was asked: " + refused.getMessage());
            return true;
        }
    }
}
