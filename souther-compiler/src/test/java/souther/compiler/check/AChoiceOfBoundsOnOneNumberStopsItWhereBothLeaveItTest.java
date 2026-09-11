package souther.compiler.check;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A choice between two bounds on one of a value's numbers stops it where the two of them leave it.
 *
 * <p>Every alternative of a choice is a rule some values meet and no value owes the branch beside
 * it anything, so what the choice holds a number to is what either alternative holds it to. That is
 * a join, and it is composed once, where the branches have their fate
 * ({@link Confinement.Planned#either}).
 *
 * <p><b>Which numbers, and who owns each.</b> Where the values at a position stop is the reading of
 * ends' ({@link OrderedReading}); a number an operation answers of that position — how long the
 * string standing there is — is another order, keyed apart so that no rule can reach both
 * ({@link DerivedNumber}). Held under one key, the two would be two mechanisms settling one order,
 * and which line a report drew would be whichever of them ran last.
 *
 * <p><b>An envelope, and never a claim that the rules are said by it.</b> Two alternatives naming
 * one size each leave the run between them, and no value has a size in between — so this is read
 * where a line is looked for and is no evidence that the number is exactly represented. The pair is
 * held below.
 */
class AChoiceOfBoundsOnOneNumberStopsItWhereBothLeaveItTest {

    private static final String YES_OR_NO = """
            data Yes
            data No
            data Answer = Yes | No
            """;

    private static final List<String> A_LINE_AT_TWO =
            List.of("border      borders 1   obligations 0/0");
    private static final List<String> NO_LINE =
            List.of("border      not applicable (the rules of this behavior draw no line)");
    private static final List<String> NOT_MEASURED =
            List.of("border      not measured (no line was derived at any position)");

    /**
     * Two ordinary bounds on how long the string is, one under a choice.
     *
     * <p>Written alone, or with {@code &&} between them, these are a border this compiler draws.
     * Under a choice it drew none and said the end was one nothing could work out — which was the
     * reading of ends answering about a number it does not count, because the walk that classifies
     * a comparison stops at a choice.
     */
    @Test
    void twoBoundsOnALengthLeaveTheLooserOfThem() {
        assertEquals(List.of(A_LINE_AT_TWO, lineAt("String.length(v.s)", "2")),
                List.of(borderIn("String.length(s) >= 2 || String.length(s) >= 3"),
                        readingIn("String.length(s) >= 2 || String.length(s) >= 3")),
                "a value taking either alternative is at least two characters long, and one at"
                        + " least three is one alternative's business alone");
    }

    /**
     * And it does not turn on which of them was written first.
     *
     * <p>Both answers are pinned and not merely held against each other. Two readings that agree
     * are two readings that agree, and a change leaving them both saying the model draws no line
     * would pass a test asking only that.
     */
    @Test
    void andNotOnWhichWasWrittenFirst() {
        assertEquals(List.of(A_LINE_AT_TWO, A_LINE_AT_TWO),
                List.of(borderIn("String.length(s) >= 2 || String.length(s) >= 3"),
                        borderIn("String.length(s) >= 3 || String.length(s) >= 2")),
                "a choice is between its alternatives and not between their order");
    }

    /** Nor on how the alternatives were bracketed. */
    @Test
    void norOnHowTheyWereBracketed() {
        assertEquals(List.of(A_LINE_AT_TWO, A_LINE_AT_TWO),
                List.of(borderIn("(String.length(s) >= 2 || String.length(s) >= 3)"
                                + " || String.length(s) >= 4"),
                        borderIn("String.length(s) >= 2"
                                + " || (String.length(s) >= 3 || String.length(s) >= 4)")),
                "the same three alternatives, and the brackets are not a fact about the rule");
    }

    /** And one bound written twice is that bound. */
    @Test
    void andOneBoundWrittenTwiceIsThatBound() {
        assertEquals(List.of(A_LINE_AT_TWO, A_LINE_AT_TWO),
                List.of(borderIn("String.length(s) >= 2"),
                        borderIn("String.length(s) >= 2 || String.length(s) >= 2")),
                "the same rule on both sides holds the length where the rule holds it");
    }

    /**
     * And an alternative that says nothing about the number leaves it where it was.
     *
     * <p>The negative control, and the one an implementation reading "both branches bound
     * something" would fail: a value taking the second alternative is under no obligation about the
     * length, so the choice draws no line on it however plain the first alternative is.
     */
    @Test
    void andAnAlternativeSayingNothingOfItLeavesItWhereItWas() {
        assertEquals(NO_LINE, borderIn("String.length(s) >= 2 || n >= 3"),
                "nothing holds the length down in the second alternative, so the choice holds it"
                        + " nowhere");
    }

    /**
     * And an alternative nobody can be in leaves the one beside it drawing the line.
     *
     * <p>No string is both {@code "a"} and {@code "b"}, so no value takes the first alternative and
     * what is left of the rule is the second. Which branch that is is settled by the readings that
     * decide whether anybody can be in one, and the bound on the length is composed under their
     * answer — asked of the lengths, this branch says the length is at least nine and the choice
     * would come back drawn at two anyway, which is the right line for the wrong reason.
     */
    @Test
    void andABranchNobodyCanBeInLeavesTheOtherDrawingIt() {
        assertEquals(A_LINE_AT_TWO,
                borderIn("(s == \"a\" && s == \"b\" && String.length(s) >= 9)"
                        + " || String.length(s) >= 2"),
                "the rule is its right half, and that half draws a line at two");
    }

    /**
     * And a denial reaching the same number is the same rule.
     *
     * <p>{@code not(length < 3)} is {@code length >= 3} and arrives by another road: the claim is
     * turned where the leaf is read rather than written that way by an author. A reading that had
     * asked which operator was written would answer about the shape instead of about the rule, and
     * this choice would come back as one it could not follow.
     */
    @Test
    void andADenialReachingTheSameNumberIsTheSameRule() {
        assertEquals(List.of(A_LINE_AT_TWO, A_LINE_AT_TWO),
                List.of(borderIn("String.length(s) >= 2 || String.length(s) >= 3"),
                        borderIn("String.length(s) >= 2 || Bool.not(String.length(s) < 3)")),
                "one rule about the length, written two ways");
    }

    /**
     * And a rule stating a line nothing can place is short of it still.
     *
     * <p>The control for every answer above. {@code Int.abs(n)} is a number this reading cannot
     * name, so a rule about it says the values stop somewhere and leaves where unknown — and the
     * choice offering it is one whose end nothing worked out. Without this, everything here would
     * hold of an implementation that had come to answer "no line" under any choice at all.
     */
    @Test
    void andARuleStatingALineNothingCanPlaceIsShortOfItStill() {
        assertEquals(NOT_MEASURED, borderIn("n >= 2 || Int.abs(n) >= 5"),
                "the alternative states where `v.n` stops and nothing here worked out where");
    }

    /**
     * And the envelope is where the ends are, not a claim that everything inside it is a value.
     *
     * <p>Two alternatives naming one size each leave the run between them, and a set of four is a
     * row nobody can write. So both lines are drawn — the sizes are where the model says they are —
     * and the run between them comes back with nothing standing on it, because every value tried
     * there was refused. The two are different contracts and this holds them apart: read as a
     * representation of the sizes, the range would say four is one of them.
     */
    @Test
    void andTheEnvelopeIsWhereTheEndsAreAndNotWhatTheNumberHolds() {
        assertEquals(List.of("border      borders 2   obligations 0/0",
                        "· read as check/Set.size(c): = 3",
                        "· read as check/Set.size(c): in 3 < Set.size(c) <= 5"
                                + " — nothing composed one: every value tried at"
                                + " 3 < Set.size(c) <= 5 was refused",
                        "· read as check/Set.size(c): = 5",
                        "· read as check/Set.size(c): in 3 <= Set.size(c) < 5"
                                + " — nothing composed one: every value tried at"
                                + " 3 <= Set.size(c) < 5 was refused"),
                linesOfSource("""
                        module example.rooms

                        data Codes = Set<String>
                            invariant said = Set.size(value) == 3 || Set.size(value) == 5

                        data Yes
                        data No
                        data Answer = Yes | No

                        behavior check : (c: Codes) -> Answer
                        let check (c) = Yes
                        """,
                        each -> each.startsWith("border") || each.startsWith("· read as")),
                "the ends are at three and five, and no set has a size between them");
    }

    /**
     * And a branch whose rules leave the number no value leaves the choice what the other leaves.
     *
     * <p>Every value of the choice is in the branch beside it, so what the choice stops the length
     * at is what that branch stops it at. Which this reading may say: that the ends of the first
     * have crossed is its own knowledge, and acting on it inside itself is not deciding a fate —
     * nobody outside is told, and whether anybody is in the branch stands on what the values and
     * the orders say.
     *
     * <p>Handed to the join the position's ranges are composed by, such a branch arrives as one
     * this reading was told somebody can be in, which is a promise nothing here made.
     */
    @Test
    void andABranchWhoseRulesLeaveTheNumberNoValueLeavesTheOtherDrawingIt() {
        assertEquals(
                List.of(lineAt("String.length(v.s)", "7"), lineAt("String.length(v.s)", "7")),
                List.of(readingIn("(String.length(s) >= 5 && String.length(s) <= 3)"
                                + " || String.length(s) >= 7"),
                        readingIn("(String.length(s) == 3 && String.length(s) == 5)"
                                + " || String.length(s) >= 7")),
                "no value of the first alternative exists, so the line is the second's — and the"
                        + " ends of the first are not where anything is");
    }

    /**
     * And it does not turn on where the brackets were put, whatever the alternatives came to.
     *
     * <p>The one thing a choice may never be. A branch whose rules leave the length no value and a
     * branch that says nothing about the length read alike off a range — both are absent from it —
     * and the two are opposite answers: the first is one nobody is in, and the second puts every
     * length on the order. Read as one, these three alternatives come to a line under one
     * bracketing and to none under the other.
     */
    @Test
    void andNotOnWhereTheBracketsWere() {
        String crossed = "(String.length(s) >= 5 && String.length(s) <= 3)";
        String unplaced = "String.length(s) * 2 >= 4";
        assertEquals(List.of(NOT_MEASURED, NOT_MEASURED),
                List.of(borderIn("(" + crossed + " || " + crossed + ") || " + unplaced),
                        borderIn(crossed + " || (" + crossed + " || " + unplaced + ")")),
                "no value of the crossed alternatives exists, so every value is in the third —"
                        + " whose line is one nothing placed, whichever way the brackets fall");
    }

    /**
     * And a single rule stating an end past the order is such a branch too.
     *
     * <p>No string is longer than the largest whole number, so no value is in that alternative and
     * every value of the choice is in the one beside it — a line at two where that one places one,
     * and an end nobody worked out where it does not.
     *
     * <p>Which is the same fact as the pair above and reaches it another way: there two rules of a
     * conjunction stopped the length past each other, and here one rule stops it past the order.
     * Read as a rule that says nothing about the length, the alternative beside it settles nothing
     * and the choice comes back as a model that draws no line.
     */
    @Test
    void andSoIsOneRuleStatingAnEndPastTheOrder() {
        assertEquals(List.of(A_LINE_AT_TWO, NOT_MEASURED),
                List.of(borderIn("String.length(s) > 9223372036854775807"
                                + " || String.length(s) >= 2"),
                        borderIn("String.length(s) > 9223372036854775807"
                                + " || String.length(s) * 2 >= 4")),
                "nobody is in the first alternative, so the choice is the second — which draws a"
                        + " line in one and states one nothing placed in the other");
    }

    /** What the document says the line was read as. */
    private static List<String> lineAt(String number, String value) {
        return List.of("· read as check/" + number + ": = " + value,
                "· read as check/" + number + ": in " + value + " < " + number);
    }

    private static List<String> borderIn(String clause) {
        return linesOf(clause, each -> each.startsWith("border"));
    }

    private static List<String> readingIn(String clause) {
        return linesOf(clause, each -> each.startsWith("· read as"));
    }

    private static List<String> linesOf(String clause,
                                        java.util.function.Predicate<String> which) {
        return linesOfSource("""
                module demo
                %s
                data N = { n: Int, s: String }
                    invariant r = %s

                behavior check : (v: N) -> Answer
                let check (v) = Yes
                """.formatted(YES_OR_NO, clause), which);
    }

    private static List<String> linesOfSource(String source,
                                              java.util.function.Predicate<String> which) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts())).lines()
                .map(String::strip)
                .filter(which)
                .toList();
    }
}
