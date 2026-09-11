package souther.compiler.cst;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A place is the same place after an edit that writes only trivia — both ends of it.
 *
 * <p>The start was never in doubt: a meaningful token's start is that token's, and whitespace before
 * it moves the offset and not the count. The end is the half that can be got wrong, because the
 * offset a token ends at is the offset the next one starts at wherever nothing separates them. Read
 * as an offset, {@code foo}'s end in {@code foo(} is the bracket's start and its end in
 * {@code foo (} is three units into {@code foo} — so writing a space moved it, and every region
 * closing on that token moved with it.
 *
 * <p>Which is the whole of what the two questions are. What owns an offset is a question about the
 * offset, and at a boundary the answer is the token after it; where a token ends is a question about
 * the token, and the answer is on the token whatever is written next. A region belongs to a node, so
 * it is asked the second way.
 */
class BothEndsOfATokenStayPutWhenOnlyTriviaIsWrittenTest {

    private static final String GLUED = """
            module m

            data Amount = { v: Int }

            let twice (n: Int): Int = n * 2
            """;

    /** The same model with a space written where two meaningful tokens were touching. */
    private static final String SPACED = GLUED.replace("twice (n", "twice  (n")
            .replace("{ v: Int }", "{ v : Int }");

    /**
     * Both ends of every meaningful token are the same place in the two texts.
     *
     * <p>Over every token rather than the pair the edit is between, because what is being claimed is
     * that the edit moved nothing — a claim about the whole text, which a check of the one token
     * would leave to a reader to believe about the rest.
     */
    @Test
    void everyPlaceASourceMakesSurvivesASpaceWrittenBetweenTwoTokens() {
        assertNotEquals(GLUED, SPACED, "the two texts differ, or this is comparing a text to itself");

        assertEquals(bothEndsOf(GLUED), bothEndsOf(SPACED),
                "a space between two tokens is not a place moving: what a place says is which of the"
                        + " things written here it is, and the same things are written");
    }

    /** And the ends are ends: a token's end is past its start by the width of the token. */
    @Test
    void theEndOfATokenIsThatTokensPlaceCarriedItsWidthAlong() {
        SourceLayout laidOut = SourceLayout.of(GLUED);
        SyntaxToken twice = meaningfulTokens(CstParser.parse(GLUED).root()).stream()
                .filter(each -> "twice".equals(each.text())).findFirst().orElseThrow();

        SourcePos start = laidOut.at(twice);
        SourcePos end = laidOut.after(twice);

        assertEquals(start.construct(), end.construct(), "one token is in one construct");
        assertEquals(start.token(), end.token(), "and the end of a token is on that token");
        assertEquals("twice".length(), end.within() - start.within(),
                "as far past its start as the token is wide");
        assertTrue(start.isBefore(end), "and after where it begins");
    }

    /**
     * The control: a meaningful token written in does move the places after it.
     *
     * <p>Without this the claim above holds of a layout that answered one place for everything. What
     * is conservative about a place is exactly this — writing something moves what follows it — and a
     * check that only watches trivia would stay green if the two were confused.
     */
    @Test
    void andSomethingWrittenInDoesMoveThePlacesAfterIt() {
        assertNotEquals(bothEndsOf(GLUED),
                bothEndsOf(GLUED.replace("n * 2", "n * 2 + 0")),
                "a token written in is not trivia, and the places after it are not the same places");
    }

    /** Where each meaningful token of {@code source} begins and ends. */
    private static List<SourcePos> bothEndsOf(String source) {
        SourceLayout laidOut = SourceLayout.of(source);
        List<SourcePos> places = new ArrayList<>();
        for (SyntaxToken token : meaningfulTokens(CstParser.parse(source).root())) {
            places.add(laidOut.at(token));
            places.add(laidOut.after(token));
        }
        assertTrue(places.size() > 20, "a text of a few declarations has tokens to ask about");
        return places;
    }

    private static List<SyntaxToken> meaningfulTokens(SyntaxNode node) {
        List<SyntaxToken> out = new ArrayList<>();
        collect(node, out);
        return out;
    }

    private static void collect(SyntaxNode node, List<SyntaxToken> into) {
        for (SyntaxElement child : node.children()) {
            if (child instanceof SyntaxNode inner) {
                collect(inner, into);
            } else if (child instanceof SyntaxToken token && !token.isTrivia()) {
                into.add(token);
            }
        }
    }
}
