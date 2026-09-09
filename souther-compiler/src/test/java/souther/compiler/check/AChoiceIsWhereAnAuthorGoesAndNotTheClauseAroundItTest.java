package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the reading that says which values may stand at a position tells an author about a choice.
 *
 * <p>It has recorded which of its shortfalls a choice is answerable for since the road was opened
 * ({@code UnreadReason.ALTERNATIVE_NOT_READ}), and said the same word for it as for a clause
 * written in a form nothing here takes apart. So an author was told to rewrite a comparison that
 * was read from end to end, while the branch beside it — the thing they can act on — went unnamed.
 *
 * <p>And where the choice is, because the word alone does not say which one. Two choices leaving
 * one position open are two things to lift, and the rule and the position say nothing about which
 * of them a reader is being told.
 */
class AChoiceIsWhereAnAuthorGoesAndNotTheClauseAroundItTest {

    private static final String ANSWER = "data Answer = Yes | No";

    /**
     * A branch nothing reads, beside one that never names the position it leaves open.
     *
     * <p>{@code String.reverse(s) /= ""} is a form this reading does not enter, and nothing else
     * says anything about {@code v.s} — so what leaves it open is the choice, and the sentence says
     * so and says where the operator is. It said the form was unreadable, which is true of the
     * branch and is not what an author does anything about at {@code v.s}.
     */
    @Test
    void theValuesReadingNamesTheChoiceAndWhereItIs() {
        assertEquals(List.of("· not accounted for: invariant N (r) — which values may stand at"
                        + " v.s: left open by a choice in it whose other alternative this compiler"
                        + " does not read, at 4:26; written in a form this compiler does not read"),
                accountedFor("n >= 2 || String.reverse(s) /= \"\"", "v.s"),
                "the branch beside the one that was read is what an author can act on, and the"
                        + " sentence says which branch");
    }

    /**
     * And two choices leaving one position open are two things to lift.
     *
     * <p>One rule, one position and one word between them, so the operator each was written at is
     * the whole of the difference. Said as one entry, an author lifting the branch they were shown
     * finds the position exactly as open as it was.
     */
    @Test
    void twoChoicesLeavingOnePositionOpenAreTwoThingsToLift() {
        assertEquals(2, accountedFor(
                        "n >= 2 || String.reverse(s) /= \"\" || Int.abs(n) >= 5", "v.s").stream()
                        .flatMap(line -> List.of(line.split("; ")).stream())
                        .filter(each -> each.contains("left open by a choice"))
                        .distinct().count(),
                () -> "each choice is somewhere an author goes: "
                        + accountedFor("n >= 2 || String.reverse(s) /= \"\""
                                + " || Int.abs(n) >= 5", "v.s"));
    }

    /**
     * And where both readings reach one choice, they reach one place.
     *
     * <p>Two entries and one address. They are two facts — what may stand at a position, and where
     * the values there stop — so a document folding them would answer one of the two questions
     * twice; and they are one thing for an author to do, so entries that named no place left a
     * reader to lift one and find the other still there.
     *
     * <p>Asked of the addresses and not of the words. Two sentences that happen to mention a choice
     * apiece say nothing about whether it is the same choice, which is the whole of what a reader
     * holding both wants to know.
     */
    @Test
    void bothReadingsOfOneChoiceSendAnAuthorToOnePlace() {
        String clause = "n >= 2 || (Int.abs(n) >= 5 && String.reverse(s) /= \"\")";
        assertEquals(List.of("· not accounted for: invariant N (r) — which values may stand at"
                        + " v.s: left open by a choice in it whose other alternative this compiler"
                        + " does not read, at 4:26; written in a form this compiler does not read"),
                accountedFor(clause, "v.s"),
                "the values reading is short at the choice written at 4:26");
        assertEquals(List.of("· not read: invariant N (r) — left open by a choice in it whose other"
                        + " alternative this compiler does not read, about `v.n`, at 4:26"),
                linesStartingWith(clause, "· not read:"),
                "and the reading of ends is short at the same one, so an author has one place to"
                        + " go and two things they are told about it");
    }

    /**
     * And one choice an expansion put in two places is one place under either reading.
     *
     * <p>The two readings reach a document by seams of their own — one through the account of a
     * standing question, the other through the findings about a rule with no line — so each answers
     * for itself how many places an author has to go. Fixed on one of them, the other went on
     * saying what its own producer happened to hand it.
     *
     * <p>What is asked is the address and not the number of lines. The readings say different
     * things about the operator and are entitled to a line apiece; what they may not do is send an
     * author to two places for one {@code ||}.
     */
    @Test
    void oneChoiceExpandedTwiceIsOnePlaceUnderEitherReading() {
        // A choice both readings are short at, written once and expanded twice.
        String model = """
                module m
                %s
                let alt (x: Int, y: String): Bool =
                    x >= 2 || (Int.abs(x) >= 5 && String.reverse(y) /= "")

                data N = { n: Int, s: String }
                    invariant r = alt(n, s) && alt(n, s)

                behavior f : (v: N) -> Answer
                let f (v) = Yes
                """.formatted(ANSWER);

        assertEquals(List.of("4:12"), placesIn(model, "· not accounted for:"),
                "the values reading is sent to the operator in the helper, once");
        assertEquals(List.of("4:12"), placesIn(model, "· not read:"),
                "and the reading of ends to the same one, so an author has one place to go");
    }

    /**
     * Every place the report's {@code word} lines send a reader, in the order they are written.
     *
     * <p>Not deduplicated, which is the whole of what this reads. How many places an author is sent
     * to is the fact under test; folded here, two entries pointing at one operator would read as
     * one and an address made to compare by identity would go unnoticed.
     */
    private static List<String> placesIn(String model, String word) {
        return linesOfModel(model).stream()
                .filter(each -> each.startsWith(word))
                .flatMap(line -> List.of(line.split(", at ")).stream().skip(1))
                .map(each -> each.split(";")[0].strip())
                .toList();
    }

    /** The lines of the report about what may stand at {@code position} under {@code clause}. */
    private static List<String> accountedFor(String clause, String position) {
        return linesStartingWith(clause, "· not accounted for:").stream()
                .filter(each -> each.contains("stand at " + position + ":"))
                .toList();
    }

    /** The lines of the report about {@code clause} that begin with {@code word}. */
    private static List<String> linesStartingWith(String clause, String word) {
        return linesOfModel("""
                module m
                %s
                data N = { n: Int, s: String }
                    invariant r = %s

                behavior f : (v: N) -> Answer
                let f (v) = Yes
                """.formatted(ANSWER, clause)).stream()
                .filter(each -> each.startsWith(word))
                .toList();
    }

    /** Every line of the report about {@code model}, stripped. */
    private static List<String> linesOfModel(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity()).lines()
                .map(String::strip)
                .toList();
    }
}
