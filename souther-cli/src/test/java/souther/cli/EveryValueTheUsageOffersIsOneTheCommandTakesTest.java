package souther.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A value the usage offers is a value the command takes.
 *
 * <p>The table holds what an option is called and what it takes so that the usage text and the
 * parsers cannot come apart. That holds while one option is one command's. Where two commands share
 * a spelling and read it differently, the value spelling has to be the reading's as well — and it
 * was not: `--adequacy` was widened to `examples` with the table's own words left as `compile`'s, so
 * the usage offered `off`, `witness` and `all` under a command whose whole output is the report and
 * which refuses all three.
 *
 * <p>Asked by running the command with each value the usage prints, which is the only way to ask it
 * of the parser rather than of a second list beside it. What a value <em>means</em> is not asked
 * here; what is asked is that the command does not turn it down for being a word it does not know.
 */
class EveryValueTheUsageOffersIsOneTheCommandTakesTest {

    /** How a refused command line exits, which is what this is looking for the absence of. */
    private static final int REFUSED = 2;

    @Test
    void everyValueTheUsagePrintsIsOneItsCommandAccepts() throws Exception {
        Path file = Files.createTempDirectory("souther-usage").resolve("rate.sou");
        Files.writeString(file, """
                module example.rate

                data Charged = { cost: Int }

                behavior submit : (cost: Int) -> Charged
                    constructs Charged

                let submit (cost) = Charged { cost = cost }

                example submit
                    | "one" : (1) -> Charged { cost = 1 }
                """);
        Path out = Files.createTempDirectory("souther-usage-out");

        List<String> refused = new ArrayList<>();
        for (CliCommand command : List.of(CliCommand.EXAMPLES, CliCommand.COMPILE)) {
            // The options that command takes, and not a pair written out here: an option moved off
            // a command would otherwise be run against it and refused for not being its.
            for (CliOption option : List.of(CliOption.ADEQUACY)) {
                if (!Main.optionOwners(option.spelling()).contains(command.spelling())) {
                    continue;
                }
                for (String value : command.valueSpelling(option).split("\\|")) {
                    List<String> line = new ArrayList<>(
                            List.of(command.spelling(), file.toString(), option.spelling(), value));
                    if (command == CliCommand.COMPILE) {
                        line.addAll(List.of("-d", out.toString()));
                    }
                    if (code(line) == REFUSED) {
                        refused.add(String.join(" ", line));
                    }
                }
            }
        }
        assertEquals(List.of(), refused,
                "the usage prints these and the command turns them down");
    }

    private static int code(List<String> args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            return Main.dispatch(args.toArray(String[]::new));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
