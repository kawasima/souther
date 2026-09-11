package souther.compiler.check;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A condition of two values says the same thing however it is written against a truth value.
 *
 * <p>What a reading below is given is an atom and a polarity, and the ways of arriving at one are
 * the language's rather than each reader's: {@code p}, {@code p == true}, {@code p /= false} and a
 * denial of the denial of any of them are one condition, and the four with the polarity turned over
 * are the other. A reader that learned a spelling at a time would answer for the ones somebody had
 * got to, and the answers below are what such a gap looks like from outside — a body's arm the rules
 * prove nothing reaches is owed a row under one spelling and not under the next.
 *
 * <p>Read off the arms rather than off the values a position admits. What the rule says about the
 * position is what makes the arm unreachable, so the arm account is downstream of the whole reading
 * — and a test on the values would pass over a reading that answered them and never reached the
 * body.
 */
class EverySpellingOfATruthValuedConditionIsOneConditionTest {

    /** A body of two arms, one of which the rule on its input rules out. */
    private static String model(String rule, String flag, String out) {
        return """
                module example.truth

                data Ok
                data No
                data Out = Ok | No

                data F = { flag: Bool }
                    invariant RULE

                behavior read : (f: F) -> Out
                let read (f) = if f.flag then Ok else No

                example read
                    | "one" : (F { flag = FLAG }) -> OUT
                """.replace("RULE", rule).replace("FLAG", flag).replace("OUT", out);
    }

    /** Every way of saying the flag holds. */
    private static final List<String> HOLDS = List.of(
            "flag",
            "flag == true",
            "flag /= false",
            "Bool.not(flag == false)",
            "Bool.not(Bool.not(flag))");

    /** And every way of saying it does not. */
    private static final List<String> DOES_NOT = List.of(
            "Bool.not(flag)",
            "flag == false",
            "flag /= true",
            "Bool.not(flag == true)");

    /**
     * One arm is owed, because the rule leaves no value the other arm runs on.
     *
     * <p>The number and not merely the agreement. Five spellings agreeing on {@code 1/2} would be
     * five readings that all missed the rule, which is agreement about nothing — so what is held is
     * that each of them read it.
     */
    @Test
    void everySpellingOfTheFlagHoldingOwesTheOneArmItsRuleLeaves() {
        for (String rule : HOLDS) {
            String report = report(model(rule, "true", "Ok"));
            assertTrue(report.contains("branch      1/1"),
                    () -> "`" + rule + "` leaves no value the other arm runs on:\n" + report);
        }
    }

    /** And the same with the polarity turned over, which is what says the polarity is read at all. */
    @Test
    void everySpellingOfTheFlagNotHoldingOwesTheOtherArm() {
        for (String rule : DOES_NOT) {
            String report = report(model(rule, "false", "No"));
            assertTrue(report.contains("branch      1/1"),
                    () -> "`" + rule + "` leaves no value the other arm runs on:\n" + report);
        }
    }

    /**
     * And the reports are the same document, not merely the same number.
     *
     * <p>Held over the whole of what is said about the model, since a spelling read as a form
     * nothing takes apart leaves its mark somewhere other than the arms — a question standing
     * unanswered, a position nothing divides — and a check on one line would pass over it.
     */
    @Test
    void theWholeAnswerIsTheSameWhicheverSpellingIsWritten() {
        for (List<String> spellings : List.of(HOLDS, DOES_NOT)) {
            String flag = spellings == HOLDS ? "true" : "false";
            String out = spellings == HOLDS ? "Ok" : "No";
            String first = report(model(spellings.get(0), flag, out));
            for (String rule : spellings.subList(1, spellings.size())) {
                assertEquals(first, report(model(rule, flag, out)),
                        "`" + rule + "` is `" + spellings.get(0) + "` written another way");
            }
        }
    }

    /** Every way of saying a string holds a format, over a position a rule divides. */
    private static final List<String> A_PREDICATE_HOLDS = List.of(
            "String.contains(\"x\", value)",
            "String.contains(\"x\", value) == true",
            "String.contains(\"x\", value) /= false",
            "Bool.not(String.contains(\"x\", value) == false)");

    /** One string position, with the rule under test and nothing else about it. */
    private static String divided(String rule) {
        return """
                module example.spelt

                data Ok

                data S = String
                    invariant it = RULE

                data Form = { s: S }

                behavior read : (f: Form) -> Ok
                let read (f) = Ok
                """.replace("RULE", rule);
    }

    /**
     * And a rule about a string is one rule however it is written against a truth value.
     *
     * <p>The other reader of the same normalisation. What such a rule does to the position is
     * answered by the reading that turns clauses into sets, and the walk that draws lines takes
     * that answer — so both spellings come to it through one reader. Answered by the walk itself,
     * off the shape of the clause, a predicate written plainly reached the question and the same
     * predicate compared with {@code true} did not.
     */
    @Test
    void everySpellingOfAPredicateOverAStringRestrictsThePositionAlike() {
        for (String rule : A_PREDICATE_HOLDS) {
            String report = report(divided(rule));

            assertTrue(report.contains(
                            "no line: invariant S (it) — it restricts this position to the values"),
                    () -> "`" + rule + "` holds `f.s` to what it admits, and this says so:\n"
                            + report);
        }
    }

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }
}
