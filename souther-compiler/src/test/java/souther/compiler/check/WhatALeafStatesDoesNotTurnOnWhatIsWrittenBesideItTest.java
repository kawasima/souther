package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which number a leaf states a line on does not turn on how the leaf is spelled.
 *
 * <p>What a leaf states is one question with one answer: which numbers the comparison is over is
 * what its canonical form says, and whether one of this value's positions is held to another is
 * what the two whole sides say. Both are what the attribution of an end to a conjunct already runs
 * on. A second reading of the same question is a second answer, and the narrower of the two then
 * decides wherever it happens to be asked.
 *
 * <p><b>So this is a contract between readers rather than a list of shapes.</b> Each spelling below
 * is one rule about how long a string is, written under a choice beside another rule about the same
 * length. Whatever the choice comes to, it is about that length — and a spelling that came back
 * about nothing would be one a second reader classified for itself.
 *
 * <p>Which is what {@code String.length(s) * 2 >= 4} was: the arithmetic reaches the length and a
 * walk over the two sides does not, so under a choice the rule was one this compiler said nothing
 * about and the border came back as a model that draws no line.
 *
 * <p>Not that the answers agree, because they do not and should not. One spelling is a bound this
 * compiler places and another is one it does not, so a choice offering the first draws a line and
 * one offering the second leaves an end nobody worked out. What may not differ is what the answer
 * is about.
 */
class WhatALeafStatesDoesNotTurnOnWhatIsWrittenBesideItTest {

    private static final String YES_OR_NO = """
            data Yes
            data No
            data Answer = Yes | No
            """;

    /** One rule about how long the string at {@code s} is, written five ways. */
    private static final List<String> ABOUT_THE_LENGTH = List.of(
            "String.length(s) >= 2",
            "2 <= String.length(s)",
            "Bool.not(String.length(s) < 2)",
            "String.length(s) * 2 >= 4",
            "String.length(s) + 1 >= 3");

    @Test
    void everySpellingOfOneRuleIsAboutTheNumberItIsAbout() {
        List<String> about = new ArrayList<>();
        for (String spelling : ABOUT_THE_LENGTH) {
            about.add(spelling + ": " + numberSpokenOf(spelling + " || String.length(s) >= 9"));
        }
        assertEquals(ABOUT_THE_LENGTH.stream()
                        .map(each -> each + ": String.length(v.s)").toList(),
                about,
                "each of them holds the length down, so the choice between one of them and another"
                        + " bound on the length is about the length");
    }

    /**
     * And a rule about another number is not pulled into it.
     *
     * <p>The control. Without it, a reading that answered {@code String.length(v.s)} to everything
     * would pass the rule above, and what it would have shown is that the answer does not turn on
     * the question either.
     */
    @Test
    void andARuleAboutAnotherNumberIsNotPulledIn() {
        assertEquals("v.n", numberSpokenOf("Int.abs(n) >= 5 || n >= 9"),
                "the rule is about the number at `n`, and no reading of it reaches the string");
    }

    /**
     * And a choice bounds a number only where both of its alternatives do.
     *
     * <p>What an inner choice hands the one above it is the numbers it holds down, and a number
     * only one of its alternatives holds down is not one of them: a value taking the other stands
     * anywhere on it. So the pair below differ, and they differ at the inner choice alone.
     *
     * <p>The first leaves the length wherever it was — a value with {@code n} at three is any
     * length — so the alternative beside it holds no end open and the model draws no line. The
     * second holds the length at two whichever inner alternative is taken, so what the third one
     * says about it is a line nobody worked out.
     */
    @Test
    void andAChoiceBoundsANumberOnlyWhereBothAlternativesDo() {
        assertEquals(
                List.of("border      not applicable (the rules of this behavior draw no line)",
                        "border      not measured (no line was derived at any position)"),
                List.of(borderIn("(String.length(s) >= 2 || n >= 3)"
                                + " || String.length(s) * 2 >= 4"),
                        borderIn("(String.length(s) >= 2 || String.length(s) >= 5)"
                                + " || String.length(s) * 2 >= 4")),
                "the inner choice holds the length nowhere in the first and at two in the second");
    }

    /**
     * And an end a choice settled stays settled, whatever else leaves the same number open.
     *
     * <p>Two lines on one length. The first stands under a choice whose other alternative holds the
     * length nowhere, so what that choice leaves it is every value and the line is answered for.
     * The second is written outside any choice, where nothing answers for it — so the rule leaves
     * the length open, and no choice is one an author can be sent to about it.
     *
     * <p>Asked whether the length is still open, the first line finds that it is, because of the
     * second, and comes back naming the choice its own alternative had already settled. So the ends
     * are struck off one at a time and never by the number they are ends of.
     *
     * <p>Of the ends' own line and not of the words in it. The reading that says which values may
     * stand at a position is short here too and says so in the same words about the same choice —
     * they are two readings of one operator — so a filter on the phrase alone reads one reading's
     * sentence as the other's.
     */
    @Test
    void andAnEndAChoiceSettledStaysSettled() {
        assertEquals(List.of(),
                linesOf("(String.length(s) * 2 >= 4 || n >= 3)"
                        + " && String.length(s) * 3 >= 6").stream()
                        .filter(each -> each.startsWith("· not read:")
                                && each.contains("left open by a choice"))
                        .toList(),
                "the line under the choice was answered for by the alternative beside it, and the"
                        + " one outside reaches no choice at all");
    }

    /** What the document says about this behavior's border. */
    private static String borderIn(String clause) {
        return linesOf(clause).stream()
                .filter(each -> each.startsWith("border"))
                .findFirst().orElse("nothing");
    }

    /**
     * What number the document's answer about this behavior is about.
     *
     * <p>Whichever sentence carries it: a line the model draws names the number it falls on, and an
     * end nobody worked out names the number it is an end of. Which of the two came out is what
     * this deliberately does not read.
     */
    private static String numberSpokenOf(String clause) {
        List<String> lines = linesOf(clause);
        for (String each : lines) {
            if (each.startsWith("· read as check/")) {
                return each.substring("· read as check/".length()).split(":")[0];
            }
            // The number is what stands between the quotes, and what follows them is where in the
            // rule a reader goes. Read as everything after the first quote, the place came back as
            // part of the number the moment the sentence began saying one.
            if (each.startsWith("· not read:") && each.contains(" about `")) {
                String after = each.substring(each.indexOf(" about `") + " about `".length());
                return after.substring(0, after.indexOf('`'));
            }
        }
        return "nothing: " + lines;
    }

    private static List<String> linesOf(String clause) {
        Compilation compilation = Compilation.ofSource("""
                module demo
                %s
                data N = { n: Int, s: String }
                    invariant r = %s

                behavior check : (v: N) -> Answer
                let check (v) = Yes
                """.formatted(YES_OR_NO, clause), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity()).lines()
                .map(String::strip)
                .toList();
    }
}
