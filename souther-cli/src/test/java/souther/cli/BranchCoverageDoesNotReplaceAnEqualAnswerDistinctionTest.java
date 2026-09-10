package souther.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An arm covered under one decision says nothing about the distinction it stood for under another.
 *
 * <p>A helper forking on one input, called from both arms of a fork on another, answering alike
 * under one of them. Rows can reach every arm the author wrote while never putting the two together
 * one way, because a helper's arms are counted once across its call sites
 * ({@link AHelpersArmIsOneObligationHoweverOftenItIsCalledTest}) — so the arm measure cannot tell
 * the rows that tried that combination from the rows that did not.
 *
 * <p>Which is why an equal-answer fold is not a fold anything else makes up for: a criterion that
 * dropped the distinction because the answers agree would be resting on the arms to keep it, and
 * the arms do not.
 */
class BranchCoverageDoesNotReplaceAnEqualAnswerDistinctionTest {

    /** Under {@code Safe} the helper's arms answer alike; under {@code Risky} they do not. */
    private static final String MODEL = """
            module example.equalanswers

            data On
            data Off
            data Flag = On | Off

            data Safe
            data Risky
            data Mode = Safe | Risky

            data X
            data Y
            data Z
            data Answer = X | Y | Z

            let choose (flag: Flag, yes: Answer, no: Answer): Answer =
                match flag with
                    | On  -> yes
                    | Off -> no

            behavior decide : (mode: Mode, flag: Flag) -> Answer

            let decide (mode, flag) =
                match mode with
                    | Safe  -> choose(flag, X, X)
                    | Risky -> choose(flag, Y, Z)

            example decide
                | "safe on"   : (Safe, On)   -> X
                | "risky on"  : (Risky, On)  -> Y
                | "risky off" : (Risky, Off) -> Z
            """;

    /** The row that tries the combination the rows above leave out. */
    private static final String AND_SAFE_OFF = MODEL
            + "    | \"safe off\"  : (Safe, Off)  -> X\n";

    @Test
    void everyArmIsReachedWithoutTryingTheCombination() throws Exception {
        String report = reportOn(MODEL);

        assertTrue(branchLine(report).endsWith("4/4"),
                () -> "every arm the author wrote is reached:\n" + report);
        assertTrue(report.contains("unknown"),
                () -> "and a combination of the two forks is untried:\n" + report);
    }

    /**
     * The two models, and what the arms say about them.
     *
     * <p>The point of the test. Writing the missing row changes which combinations the rows reach
     * and leaves the arm measure exactly where it was, so nothing an arm reports separates a model
     * that tried that combination from one that did not.
     */
    @Test
    void andTheArmMeasureCannotTellTheTwoApart() throws Exception {
        String without = reportOn(MODEL);
        String with = reportOn(AND_SAFE_OFF);

        assertTrue(branchLine(with).equals(branchLine(without)),
                () -> "the arms answer alike for both:\n" + branchLine(without)
                        + "\n" + branchLine(with));
        assertNotEquals(combinationLine(without), combinationLine(with),
                () -> "while the combinations do not:\n" + without + "\n" + with);
    }

    private static String branchLine(String report) {
        return lineWith(report, "branch ");
    }

    private static String combinationLine(String report) {
        return lineWith(report, "combination ");
    }

    private static String lineWith(String report, String measure) {
        for (String line : report.lines().toList()) {
            if (line.strip().startsWith(measure)) {
                return line.strip();
            }
        }
        throw new AssertionError("no `" + measure.strip() + "` line in:\n" + report);
    }

    private static String reportOn(String model) throws Exception {
        Path file = Files.createTempDirectory("souther-equal-answers").resolve("model.sou");
        Files.writeString(file, model);
        PrintStream was = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Main.main(new String[] {"examples", file.toString()});
        } finally {
            System.setOut(was);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
