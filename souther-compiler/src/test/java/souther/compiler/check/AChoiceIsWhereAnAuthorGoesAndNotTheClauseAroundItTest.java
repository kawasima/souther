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

    /** The lines of the report about what may stand at {@code position} under {@code clause}. */
    private static List<String> accountedFor(String clause, String position) {
        String model = """
                module m
                %s
                data N = { n: Int, s: String }
                    invariant r = %s

                behavior f : (v: N) -> Answer
                let f (v) = Yes
                """.formatted(ANSWER, clause);
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity()).lines()
                .map(String::strip)
                .filter(each -> each.startsWith("· not accounted for:")
                        && each.contains("stand at " + position + ":"))
                .toList();
    }
}
