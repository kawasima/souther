package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a branch's constraint still binds under a choice is read off what the alternatives leave.
 *
 * <p>Two alternatives holding a position to the same values hold it there whether or not either of
 * them could be read to the end: the choice is one of them, and both of them say the same thing.
 * So a clause a reading has no word for, written beside a constraint, does not decide what the
 * constraint comes to — and the report says the same about a rule however much of the clause beside
 * it this compiler happens to understand today.
 *
 * <p><b>Both directions, because one of them alone is met by saying nothing.</b> Where the
 * alternatives really do leave the position differently, the unread one is why the choice is as
 * wide as it is and the rule is not reported as one that drew no line. A reading that opened
 * nothing would pass the first half of this and publish a finding about a rule that holds nothing
 * down.
 */
class WhetherAConstraintStillBindsIsReadOffWhatTheAlternativesLeaveTest {

    /** A clause about {@code code} that no reading of this compiler's takes in. */
    private static final String UNREAD = ARuleNoReadingTakesIn.about("code");

    /** {@code tag} held away from one value, beside a clause this reads. */
    private static final String NARROWS_TAG = "(tag /= \"x\" && code /= \"p\")";

    /** The same, beside a clause it does not. */
    private static final String NARROWS_TAG_BESIDE_UNREAD = "(tag /= \"x\" && " + UNREAD + ")";

    /**
     * A clause nothing reads, written beside a constraint, does not take the constraint back.
     *
     * <p>All three alternatives hold {@code tag} away from {@code "x"}, so the choice does, and the
     * rule is one read to the end that draws no line at it. What differs between them is only how
     * much of the clause standing beside that constraint this compiler has a word for.
     */
    @Test
    void aClauseNothingReadsBesideAConstraintDoesNotTakeItBack() {
        assertTrue(noLineAboutTag(NARROWS_TAG + " || (tag /= \"x\" && code /= \"q\")"),
                "both alternatives were read whole");
        assertTrue(noLineAboutTag(
                        NARROWS_TAG_BESIDE_UNREAD + " || " + NARROWS_TAG_BESIDE_UNREAD),
                "and neither of them was");
        assertTrue(noLineAboutTag(NARROWS_TAG_BESIDE_UNREAD + " || tag /= \"x\""),
                "and where one of them was and the other was not");
    }

    /**
     * An alternative nothing reads that says nothing about the position does take it back.
     *
     * <p>{@code tag /= "x" || (code /= "p" && f(code))}: a value satisfying the right alternative
     * owes the left one nothing and this compiler cannot say which values those are, so what the
     * clause leaves {@code tag} is every value and no rule of it holds the position down.
     */
    @Test
    void anAlternativeThatSaysNothingAboutThePositionDoesTakeItBack() {
        assertTrue(noneAboutTag("tag /= \"x\" || (code /= \"p\" && " + UNREAD + ")"),
                "nothing here holds tag down, so there is no rule to report as drawing no line");
        assertTrue(noneAboutTag("tag /= \"x\" || code /= \"p\""),
                "and the same where both alternatives were read: the choice leaves tag open");
    }

    /**
     * Where the brackets fall between three alternatives does not move the answer.
     *
     * <p>One rule written two ways. Which positions each choice left open is settled for that
     * choice out of what its own two alternatives leave, so the inner choice of one grouping and
     * the inner choice of the other are asked about different pairs of branches and answer
     * differently — and what the rule comes to is the same. Held over sources rather than over
     * accounts built by hand, since an opening written into a fixture is the answer being assumed.
     */
    @Test
    void whereTheBracketsFallDoesNotMoveTheAnswer() {
        String held = NARROWS_TAG_BESIDE_UNREAD + " || tag /= \"x\"";
        assertEquals(noLinesOf("(" + NARROWS_TAG + " || " + held + ")"),
                noLinesOf(NARROWS_TAG + " || (" + held + ")"),
                "one rule, one account of it");

        String open = NARROWS_TAG_BESIDE_UNREAD + " || code /= \"q\"";
        assertEquals(noLinesOf("(" + NARROWS_TAG + " || " + open + ")"),
                noLinesOf(NARROWS_TAG + " || (" + open + ")"),
                "and the same where an alternative is why the choice is as wide as it is");
    }

    /** Whether the report says the invariant drew no line at {@code r.tag}. */
    private static boolean noLineAboutTag(String clause) {
        return noLinesOf(clause).stream().anyMatch(line -> line.contains("`r.tag`"));
    }

    /** And whether it says nothing about that position at all. */
    private static boolean noneAboutTag(String clause) {
        return !noLineAboutTag(clause);
    }

    /**
     * The lines of the report saying a rule was read to the end without a line, or not read.
     *
     * <p>Named by the rule and the position and not by where either was written, so two sources
     * that put the same clause under different brackets are compared on what they say about the
     * rule rather than on how many characters stand before it.
     */
    private static Set<String> noLinesOf(String clause) {
        return human(clause).lines()
                .map(String::trim)
                .filter(line -> line.contains("no line:") || line.contains("not read:"))
                .collect(Collectors.toSet());
    }

    private static String human(String clause) {
        Compilation compilation = Compilation.ofSource("""
                module m

                data Ok
                data R = { tag: String, code: String }
                    invariant one = %s

                behavior f : (r: R) -> Ok
                    constructs Ok
                let f (r) = Ok
                """.formatted(clause), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity());
    }
}
