package souther.cli;

import org.junit.jupiter.api.Test;

import souther.compiler.query.ItemAssessment;
import souther.compiler.report.AdequacyReport;


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A build is held to every obligation the account derives, and what it asks for is how much to
 * measure.
 *
 * <p>The levels say what to measure: what separates {@code witness} from {@code all} is a second set
 * of classes and a second run of every row. Which of what was measured a build then refuses over
 * was a second word beside them — a bar — and a criterion a caller selects is a budget: one model
 * came back satisfied under one word and refused under another, with the gaps printed in the report
 * either way. So there is one answer now, and a compile that measured everything is told about
 * everything the model owes a row at.
 */
class ABuildIsHeldToEveryObligationTheAccountDerivesTest {

    /**
     * A model whose rows sit on the line and one step over it, and nowhere else.
     *
     * <p>So the {@code IN} and {@code OUT} points of the line its guard draws have no row, which
     * the build is held to like everything else.
     */
    private static final String ON_THE_LINE_ONLY = """
            module example.limit

            data Ok
            data TooHigh

            behavior grade : (score: Int) -> Ok | TooHigh

            let grade (score) = {
                guard score <= 100 else TooHigh
                Ok
            }

            example grade
                | "on the line" : (100) -> Ok
                | "a step over" : (101) -> TooHigh
            """;

    /** A row away from the line is owed like a row on it, so the build that measured everything is
     *  told about the two that have none. */
    @Test
    void aBuildThatMeasuredEverythingIsHeldToThePointsAwayFromTheLine() throws Exception {
        Run compiled = compile(ON_THE_LINE_ONLY, "all");

        assertEquals(1, compiled.code(), compiled.out() + compiled.err());
        assertTrue(compiled.err().contains("E1917"), compiled.err());
        assertTrue(compiled.err().contains("IN") || compiled.err().contains("OUT"), compiled.err());
    }

    /** And a build that measured nothing is told nothing, which is the dial that is left. */
    @Test
    void aBuildThatMeasuredNothingIsToldNothing() throws Exception {
        Run compiled = compile(ON_THE_LINE_ONLY, "off");

        assertEquals(0, compiled.code(), compiled.out() + compiled.err());
        assertFalse(compiled.err().contains("E1917"), compiled.err());
    }

    @Test
    void aWordThatNamesNoLevelIsRefusedWhereItIsWritten() throws Exception {
        Run compiled = compile(ON_THE_LINE_ONLY, "thorough");

        assertEquals(2, compiled.code(), compiled.out() + compiled.err());
        assertTrue(compiled.err().contains("off, witness or all"), compiled.err());
    }

    /**
     * A verdict rests on the evidence the account asks for, which is every point of a border.
     *
     * <p>The other half of what being held to something means. A build that refuses over a missing
     * {@code IN} row and calls a model satisfied while the {@code IN} point could not be measured
     * is answering two questions in one report. The border here is the pair: the points against the
     * line came to an answer and the two away from it did not.
     *
     * <p>A line this body drew, because that is where all four points are this behavior's. A run
     * beside a clause's line is owed to the type and is answered once for the module, so a verdict
     * about one behavior would not be resting on it at all.
     *
     * <p>The other side of it is the compile above: the same two points, measured and refused over.
     * Without that this would pass on a verdict held open by anything at all.
     */
    @Test
    void aVerdictRestsOnEveryPointOfTheBorder() {
        souther.compiler.query.Measurement<java.util.List<
                souther.compiler.query.BorderAssessment>> lines = AReportOfOneBorder.measured(
                        AReportOfOneBorder.assessed(AReportOfOneBorder.aBorderABodyDrew(),
                                role -> role.againstTheLine()
                                        ? AReportOfOneBorder.settled(
                                                new ItemAssessment.Coverage.Hit())
                                        : AReportOfOneBorder.undecided()));
        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED,
                AReportOfOneBorder.verdictOf(lines),
                "two of the points it asks for came to no answer");
    }

    private record Run(int code, String out, String err) {}

    private static Run compile(String model, String adequacy) throws Exception {
        Path file = Files.createTempDirectory("souther-reliable").resolve("limit.sou");
        Files.writeString(file, model);
        Path out = Files.createTempDirectory("souther-reliable-out");
        return cli("compile", file.toString(), "-d", out.toString(),
                "--adequacy", adequacy, "--warnings", "error");
    }

    private static Run cli(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int code;
        try {
            code = Main.dispatch(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new Run(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }
}
