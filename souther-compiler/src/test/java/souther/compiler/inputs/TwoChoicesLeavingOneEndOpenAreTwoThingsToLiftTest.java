package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many ends a position is left with is how many choices left one, and the line they leave short
 * is the one line.
 *
 * <p>Two questions about one position, and the counts differ. What an author has to do is lift each
 * choice, and lifting one of them leaves the other exactly where it was — so two of them are two
 * things. What the border measure is short of is a line at the position, and a line derived twice
 * would be a line drawn twice.
 *
 * <p>Read at the position rather than off a document, because that is where both counts are still
 * separate. A document says the sentence about the rule once, and a reader counting there is
 * counting sentences.
 */
class TwoChoicesLeavingOneEndOpenAreTwoThingsToLiftTest {

    private static String model(String clause) {
        return """
                module demo
                data Yes
                data No
                data Answer = Yes | No
                data N = { n: Int }
                    invariant r = %s

                behavior check : (v: N) -> Answer
                let check (v) = Yes
                """.formatted(clause);
    }

    /** Two choices of one rule, each leaving the end at one position open. */
    @Test
    void twoChoicesOfOneRuleAreTwo() {
        assertEquals(2, endsLeftOpenIn(
                        "(n >= 2 || Int.abs(n) >= 5) && (n >= 7 || Int.abs(n) >= 9)"),
                "lifting either of them leaves the other where it was");
    }

    /**
     * And one choice written once and expanded twice is two.
     *
     * <p>One operator at one place, reached twice. What an author is sent to is a place and is not
     * which choice it is, so the two are told apart by being two occurrences rather than by where
     * they are written — counted by the place, a helper holding a choice would be one thing to lift
     * however many times it is used.
     */
    @Test
    void andAHelperExpandedTwiceIsTwo() {
        assertEquals(2, endsLeftOpenIn("""
                alt(n) && alt(n)
                """.strip(), """
                let alt (x: Int) : Bool = x >= 2 || Int.abs(x) >= 5
                """.strip()),
                "one place, two occurrences, and each of them is a choice to lift");
    }

    /** And one choice is one, which is what the two above are counted against. */
    @Test
    void andOneChoiceIsOne() {
        assertEquals(1, endsLeftOpenIn("n >= 2 || Int.abs(n) >= 5"),
                "one alternative nothing follows, beside one branch that bounds the position");
    }

    /** And a rule whose ends were all worked out leaves none. */
    @Test
    void andARuleWhoseEndsWereWorkedOutLeavesNone() {
        assertEquals(0, endsLeftOpenIn("n >= 2 || n <= 0"),
                "both alternatives were followed, so nothing is left open here");
    }

    private static int endsLeftOpenIn(String clause, String... before) {
        Compilation compilation = Compilation.ofSource(
                before.length == 0 ? model(clause)
                        : model(clause).replace("data N = {",
                                before[0] + "\n\ndata N = {"), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        InputDomain inputs = compilation.db()
                .ask(new Adequacy.Inputs("demo")).value().get("check");
        List<Position> positions = inputs.positions();
        return positions.stream().mapToInt(each -> each.endsLeftOpen().size()).sum();
    }
}
