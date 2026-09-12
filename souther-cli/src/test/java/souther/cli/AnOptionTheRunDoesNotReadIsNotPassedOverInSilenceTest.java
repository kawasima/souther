package souther.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An option the run does not read is not passed over in silence.
 *
 * <p>Not that every option written is read — {@code api Option --source String} answers about
 * {@code Option} and drops the option, and says which of the two it read rather than refusing the
 * line. What holds of all of them is the weaker sentence: an option a line writes is one that
 * invocation has a reader for, or the line is refused.
 *
 * <p>Has a reader for, and not that the output changed. {@code compile ok.sou --color always} writes
 * what it writes without the option — nothing went wrong, so there was no diagnostic to colour — and
 * the colour policy was read all the same. {@code --format human} names the default and is read
 * too. Reading a difference in the output as the sign that an option applied would call both of
 * those the defect this class is about, and would call every option whose effect depends on the
 * source one whenever the source does not exercise it. What is asked here is whether the invocation
 * has anywhere to read the option, which is a question about the line and is settled from the line.
 *
 * <p>{@code --limit} on its own is that shape: it is read at one place, inside the branch
 * {@code --search} opens, so a line that writes it without {@code --search} sets a variable nothing
 * looks at — and a reader is handed the listing they would have got anyway as though the number
 * they wrote had been honoured. Silence is the one thing the command line cannot say here, and
 * {@code examples} already holds the opposite standard for {@code --module} and
 * {@code --behavior}: a name that resolves to no subject is a usage error, not a report of nothing.
 *
 * <p>Every command, and not only the ones whose parser thought to ask. {@code doc} and {@code api}
 * read their options by position and had no case for a token they did not expect, so
 * {@code api Option --nope} ran as though it were not there and {@code api --search fold --limit 1}
 * printed every hit.
 */
class AnOptionTheRunDoesNotReadIsNotPassedOverInSilenceTest {

    @TempDir
    Path dir;

    private record Said(int code, String err, String out) {}

    /** Runs the command line and answers with its exit code and what it wrote. */
    private Said run(String... args) {
        PrintStream originalErr = System.err;
        PrintStream originalOut = System.out;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int code = Main.dispatch(args);
            return new Said(code, err.toString(StandardCharsets.UTF_8),
                    out.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalErr);
            System.setOut(originalOut);
        }
    }

    private static final String MODEL = """
            module example.timesheet

            data MinuteOfDay = Int
                invariant withinDay = value >= 0 && value <= 1440

            data Shift =
                { startsAt: MinuteOfDay
                , endsAt: MinuteOfDay
                }
                invariant endsAfterStart = startsAt < endsAt

            data Short
            data Long

            behavior classify : (shift: Shift) -> Short | Long

            let classify (shift) =
                if shift.endsAt.value - shift.startsAt.value >= 480 then Long else Short

            example classify
                | "short" : (Shift { startsAt = MinuteOfDay(540), endsAt = MinuteOfDay(600) })
                    -> Short
            """;

    private Path model() throws Exception {
        Path file = dir.resolve("timesheet.sou");
        Files.writeString(file, MODEL);
        return file;
    }

    // --- the option that is read behind another one -----------------------------------------------

    /** The refusal comes before the listing, so the reader is not shown an answer to misread. */
    @Test
    void limitWithoutSearchReportsNothing() {
        Said said = run("doc", "--limit", "5");

        assertEquals("", said.out(), said.out());
    }

    /** {@code doc}'s own conditional option: the limit is read out of the search and nowhere else. */
    @Test
    void limitWithoutSearchIsRefused() {
        Said said = run("doc", "--limit", "5");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--limit`"), said.err());
        assertTrue(said.err().contains("`--search`"), said.err());
    }

    /**
     * What the option promises, which is that two is how many are shown — not that the answer got
     * shorter, and not how many the specification happens to say the term.
     *
     * <p>Counted in hits. A hit is several lines, the section and the context quoted under it, so
     * counting lines measures how much each one had to say; two hits under either limit print the
     * same text and the comparison holds with the option never read. That is how this passed here
     * and failed on CI. The control says when the question is empty.
     */
    @Test
    void limitWithSearchShowsThatManyHits() {
        Said said = run("doc", "--search", "type", "--limit", "2");
        Said all = run("doc", "--search", "type", "--limit", "0");

        assertEquals(0, said.code(), said.err());
        assertTrue(hits(all) > 2, "a term with no more hits than the limit measures nothing: " + all.out());
        assertEquals(2, hits(said), said.out());
    }

    /**
     * The value that is not there. `--limit` is read inside a loop that skipped it when nothing
     * followed, so this searched under the default and answered as though the option had not been
     * written — the defect this class is about, one option over.
     */
    @Test
    void anOptionWrittenWithoutItsValueIsRefused() {
        Said said = run("doc", "--search", "newtype", "--limit");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--limit`"), said.err());
        assertEquals("", said.out(), said.out());
    }

    /**
     * Which options take a value is written twice: in the table, where the walk above the dispatch
     * decides whether the token after an option is its value or the next argument, and in the parser
     * that reads it. Nothing makes the two agree, and a disagreement is not a compile error — a
     * parser reading a value the table calls a flag takes the argument after it, and one the table
     * calls valued has its value read as an argument.
     *
     * <p>Asked by writing each option the way the table says it is written, with a source file after
     * it, and looking at what became of the file. The observation does not go through the table: a
     * parser that swallowed the file says it was given none, and one that read the value as a second
     * file says it could not open it. Both are what a disagreement produces, in either direction.
     *
     * <p>Over the commands whose subject is a file. {@code doc}, {@code api} and {@code japi} read
     * their arguments by position rather than in a loop, so {@code --search}, {@code --limit} and
     * {@code --source} are outside this and are held by the cases written out above.
     */
    @Test
    void theTableAndTheParsersAgreeOnWhichOptionsTakeAValue() throws Exception {
        Path out = dir.resolve("out");
        Path empty = Files.createDirectories(dir.resolve("empty"));
        Path formatted = canonical();
        for (String option : Main.knownOptions()) {
            String owner = Main.optionOwners(option).split("/")[0];
            if (!List.of("compile", "examples", "fmt").contains(owner)) {
                continue;
            }
            String value = valueFor(option, empty, out);
            assertEquals(CliOption.takesValue(option), value != null,
                    option + ": this test has no value to write for an option that takes one");

            List<String> line = new ArrayList<>(List.of(owner));
            if (owner.equals("compile")) {
                line.addAll(List.of("-d", out.toString()));
            }
            line.add(option);
            if (value != null) {
                line.add(value);
            }
            line.add(formatted.toString());

            Said said = run(line.toArray(String[]::new));

            assertFalse(said.err().contains("at least one .sou file"),
                    line + ": the file was read as this option's value: " + said.err());
            assertFalse(said.err().contains("io error"),
                    line + ": this option's value was read as a file: " + said.err());
            assertFalse(said.err().contains("unknown option"), line + ": " + said.err());
        }
    }

    /** What to write after the option, or null where the table says it takes nothing. */
    private String valueFor(String option, Path empty, Path out) {
        return switch (option) {
            case "--format" -> "human";
            case "--lang" -> "en";
            case "--color" -> "never";
            case "--adequacy" -> "off";
            case "--warnings" -> "report";
            case "--module" -> "example.timesheet";
            case "--behavior" -> "classify";
            case "-cp", "--class-path" -> empty.toString();
            case "-d", "--dir" -> out.toString();
            default -> null;
        };
    }

    /** The model in the form `fmt --check` accepts, so that probing `--check` says nothing about
     *  how the file happened to be written here. */
    private Path canonical() throws Exception {
        Path file = model();
        assertEquals(0, run("fmt", "-w", file.toString()).code());
        return file;
    }

    /** What the search answered, without the lines quoted under each one or the count of the rest. */
    private static long hits(Said said) {
        return said.out().lines()
                .filter(line -> !line.startsWith("    ") && !line.startsWith("… "))
                .count();
    }

    /**
     * Every option the table says needs another, asked of a command that takes it — so a pair added
     * tomorrow is under this without anything here being written again.
     *
     * <p>Beside the ones written out above rather than instead of them. This reads the same table
     * the check reads, so a pair the table is missing is a pair it does not ask about: dropping a
     * row from the relation leaves this passing over one fewer option and saying nothing. The named
     * cases are what holds each row down.
     */
    @Test
    void everyOptionThatNeedsAnotherIsRefusedWithoutIt() {
        for (String option : Main.knownOptions()) {
            String needed = CliOption.needed(option);
            if (needed == null) {
                continue;
            }
            String owner = Main.optionOwners(option).split("/")[0];
            // With its value, so that what this reads is the option it is missing and not the value
            // it is missing — an option written last is refused for that first.
            Said said = CliOption.takesValue(option)
                    ? run(owner, option, "1") : run(owner, option);

            assertEquals(2, said.code(), option + ": " + said.err());
            assertTrue(said.err().contains("`" + needed + "`"),
                    option + " needs " + needed + ": " + said.err());
        }
    }

    // --- the option another option's value leaves unread -------------------------------------------

    /**
     * {@code --color} is read where the human renderer is built, so a line that writes it under
     * {@code --format json} has asked for a colour policy nothing in the run is a reader of. That is
     * the same silence as {@code --limit} without {@code --search}, and it is not a fact about
     * the two options being written together: what makes the reader unreachable is the value
     * {@code --format} was given.
     */
    @Test
    void colorUnderJsonIsRefused() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--format", "json", "--color", "always");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--color`"), said.err());
        assertTrue(said.err().contains("`--format json`"), said.err());
        assertEquals("", said.out(), said.out());
    }

    /**
     * Whatever value it was written with, {@code auto} included. The value a line writes and the
     * value in force where nobody wrote one are the same string and are not the same statement — a
     * line that writes {@code --color auto} has asked for a colour policy, and reading the default
     * back out as though it had been asked for is how an option that was written stops being
     * distinguishable from one that was not.
     */
    @Test
    void colorUnderJsonIsRefusedWhateverValueItWasWrittenWith() throws Exception {
        for (String value : List.of("auto", "always", "never")) {
            Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                    "--format", "json", "--color", value);

            assertEquals(2, said.code(), value + ": " + said.err());
            assertTrue(said.err().contains("`--color`"), value + ": " + said.err());
        }
    }

    /** The order they are written in is not one of the conditions. */
    @Test
    void colorBeforeFormatIsTheSameLine() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--color", "always", "--format", "json");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--color`"), said.err());
    }

    /**
     * And the human renderer is the line that was meant. The option is read there whether or not this
     * compile had a diagnostic to colour, which is why the answer does not depend on the source.
     */
    @Test
    void colorUnderHumanIsTheLineThatWasMeant() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--format", "human", "--color", "always");

        assertEquals(0, said.code(), said.err());
    }

    /** And a line that leaves {@code --color} out asks for nothing under either format. */
    @Test
    void jsonWithoutColorIsTheLineItAlwaysWas() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--format", "json");

        assertEquals(0, said.code(), said.err());
    }

    /**
     * Every command that takes the two, so one added tomorrow is under this without anything here
     * being written again. Read off the same table the refusal for a foreign option is read from:
     * {@code --color} and {@code --format} are owned by the same commands, and a command that took
     * one without the other would be a command this reads and finds nothing to say about.
     */
    @Test
    void everyCommandThatTakesColorRefusesItUnderJson() throws Exception {
        for (String command : Main.optionOwners("--color").split("/")) {
            List<String> line = new ArrayList<>(List.of(command, model().toString()));
            if (command.equals("compile")) {
                line.addAll(List.of("-d", dir.resolve("out").toString()));
            }
            line.addAll(List.of("--format", "json", "--color", "always"));

            Said said = run(line.toArray(String[]::new));

            assertEquals(2, said.code(), line + ": " + said.err());
            assertTrue(said.err().contains("`--color`"), line + ": " + said.err());
            assertEquals("", said.out(), line + ": " + said.out());
        }
    }

    /** Written in the language the line asks for, as every other refusal is. */
    @Test
    void theColorRefusalIsWrittenInTheLanguageTheLineAsksFor() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--format", "json", "--color", "always", "--lang", "ja");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--color`"), said.err());
        assertFalse(said.err().contains("is not read with"), said.err());
    }

    // --- the value the option does not have --------------------------------------------------------

    /**
     * {@code --format} matched {@code json} where it was read and let everything else fall to the
     * human renderer, so a caller that asked for JSON and misspelt it was answered with a snippet and
     * the exit code the JSON run gives — nothing on the line said this compiler has no such format.
     */
    @Test
    void aFormatThisCompilerHasNoSuchThingAsIsRefused() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--format", "jsn");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--format`"), said.err());
        assertTrue(said.err().contains("jsn"), said.err());
        assertTrue(said.err().contains("json"), said.err());
        assertEquals("", said.out(), said.out());
    }

    @Test
    void aColorPolicyThisCompilerHasNoSuchThingAsIsRefused() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--color", "bright");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--color`"), said.err());
        assertTrue(said.err().contains("bright"), said.err());
    }

    /** Spelt as the option is spelt. A value is the string it is, and one that differs by its case
     *  is a value this compiler does not have — {@code always} is not {@code ALWAYS}. */
    @Test
    void aValueSpeltInAnotherCaseIsAnotherValue() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--color", "ALWAYS");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--color`"), said.err());
    }

    /**
     * {@code --lang} is not a closed set — a language this compiler ships no catalog for is a reader
     * saying which language they read — so what is held is that the value is a language tag.
     * {@code en-!!} is the case that says it: the tolerant reading this used to go through keeps
     * {@code en} and drops what follows, so the reader was answered in English and never told that
     * the rest of what they wrote said nothing.
     */
    @Test
    void aLanguageTagWithASubtagThatNamesNothingIsRefused() throws Exception {
        for (String tag : List.of("!!", "en-!!", "ja_JP.UTF-8")) {
            Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                    "--lang", tag);

            assertEquals(2, said.code(), tag + ": " + said.err());
            assertTrue(said.err().contains("`--lang`"), tag + ": " + said.err());
            assertTrue(said.err().contains(tag), tag + ": " + said.err());
            assertEquals("", said.out(), tag + ": " + said.out());
        }
    }

    /**
     * A value written where a language goes names a language, and a blank one names it badly. Read
     * as a way of writing nothing it is the same silence one option along: the line asked for a
     * language and was answered under whatever else was in force.
     */
    @Test
    void aBlankLanguageTagIsRefused() throws Exception {
        for (String tag : List.of("", "   ")) {
            Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                    "--lang", tag);

            assertEquals(2, said.code(), "[" + tag + "]: " + said.err());
            assertTrue(said.err().contains("`--lang`"), "[" + tag + "]: " + said.err());
            assertEquals("", said.out(), "[" + tag + "]: " + said.out());
        }
    }

    /**
     * And a tag is accepted whether or not this compiler answers in it. {@code fr} names a language
     * somebody reads; that nobody has translated the messages into it yet is what the fallback to
     * the base catalog is for, and is not a mistake in what they wrote.
     */
    @Test
    void aLanguageThisCompilerHasNoCatalogForIsAccepted() throws Exception {
        for (String tag : List.of("ja", "en", "fr", "fr-CA", "ja_JP")) {
            Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                    "--lang", tag);

            assertEquals(0, said.code(), tag + ": " + said.err());
        }
    }

    /**
     * Every value each of them has, so that the refusal above is a refusal of what is outside the
     * set and not of everything. A row nothing ever accepts measures nothing; these are what say the
     * set is the set the usage text writes.
     */
    @Test
    void everyValueTheseOptionsHaveIsAccepted() throws Exception {
        for (String format : List.of("human", "json")) {
            Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                    "--format", format);

            assertEquals(0, said.code(), format + ": " + said.err());
        }
        for (String color : List.of("auto", "always", "never")) {
            Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                    "--color", color);

            assertEquals(0, said.code(), color + ": " + said.err());
        }
    }

    /**
     * What the value is, before what it is read with. The line writes a format this compiler has no
     * such thing as and a colour beside it; answering it with what {@code --color} goes unread under
     * would be answering about a line its author did not write.
     */
    @Test
    void aValueOutsideTheSetIsAnsweredBeforeWhatItWouldBeReadWith() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(),
                "--format", "jsn", "--color", "always");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--format`"), said.err());
        assertFalse(said.err().contains("is not read with"), said.err());
    }

    // --- the two that may not be written together -------------------------------------------------

    @Test
    void everyExclusivePairIsRefusedTogether() throws Exception {
        for (List<String> pair : CliOption.exclusive()) {
            String owner = Main.optionOwners(pair.get(0)).split("/")[0];
            Said said = run(owner, model().toString(), pair.get(0), pair.get(1));

            assertEquals(2, said.code(), pair + ": " + said.err());
            assertTrue(said.err().contains("`" + pair.get(0) + "`"), pair + ": " + said.err());
            assertTrue(said.err().contains("`" + pair.get(1) + "`"), pair + ": " + said.err());
        }
    }

    /** The refusal names the option the way the line wrote it, not the way the table lists it. */
    @Test
    void anOptionIsNamedAsItWasWritten() throws Exception {
        Said said = run("fmt", model().toString(), "--write", "--check");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--write`"), said.err());
        assertFalse(said.err().contains("`-w`"), said.err());
    }

    // --- the commands that were not asking ---------------------------------------------------------

    /**
     * {@code api} reads its options by position, so a token it did not expect fell through to the
     * name it was asked about and the run went on.
     */
    @Test
    void aCommandThatReadsByPositionRefusesAnUnknownOption() {
        Said said = run("api", "Option", "--nope");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("unknown option `--nope`"), said.err());
        assertEquals("", said.out(), said.out());
    }

    /** An option that exists and is another command's, asked of a command that reads by position. */
    @Test
    void aCommandThatReadsByPositionRefusesAnotherCommandsOption() {
        Said said = run("api", "--search", "fold", "--limit", "1");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("unknown option `--limit`"), said.err());
        assertTrue(said.err().contains("it is an option of doc"), said.err());
        assertEquals("", said.out(), said.out());
    }

    @Test
    void docRefusesAnUnknownOption() {
        Said said = run("doc", "--nope");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("unknown option `--nope`"), said.err());
    }

    @Test
    void japiRefusesAnUnknownOption() {
        Said said = run("japi", "java.lang.String", "--nope");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("unknown option `--nope`"), said.err());
    }

    /** What each of those commands does with a line of its own is untouched. */
    @Test
    void aCommandThatReadsByPositionStillAnswers() {
        Said said = run("api", "--search", "fold");

        assertEquals(0, said.code(), said.err());
        assertTrue(said.out().contains("List.fold"), said.out());
    }

    /**
     * An option of the command's own, written where the command does not read it. The check above
     * the dispatch passes it — it is {@code api}'s option, written with the value {@code api} takes
     * — and the command answers the front of the line. Which one it read is what {@code doc} says of
     * the same line, and is what tells the reader the rest of what they wrote did not apply.
     */
    @Test
    void aCommandThatReadsByPositionSaysWhichQuestionItAnswered() {
        Said said = run("api", "Option", "--source", "String");

        assertEquals(0, said.code(), said.err());
        assertTrue(said.err().contains("reading `Option`"), said.err());
        assertTrue(said.err().contains("--source"), said.err());
        assertTrue(said.out().contains("Option.map"), said.out());
    }

    /** And says nothing where the line asked for one thing. */
    @Test
    void aLineThatAsksForOneThingIsAnsweredWithoutANote() {
        Said said = run("api", "--source", "String");

        assertEquals(0, said.code(), said.err());
        assertFalse(said.err().contains("reads one at a time"), said.err());
    }

    // --- what a value is ---------------------------------------------------------------------------

    /**
     * A value spelt like an option is a value. The check walks the line the way the parsers do, so
     * a module named {@code --generate} is the module the command was asked about — a wrong name,
     * answered as one — and not an option that arrived twice.
     */
    @Test
    void aValueSpeltLikeAnOptionIsAValue() throws Exception {
        Said said = run("examples", model().toString(), "--module", "--generate");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("--generate"),
                "the token after `--module` is the name it was given: " + said.err());
        assertFalse(said.err().contains("unknown option"), said.err());
    }

    /** A short option a command does not know still reads as a path, which is the older rule. */
    @Test
    void aShortOptionAnotherCommandOwnsIsStillAPath() throws Exception {
        Said said = run("compile", model().toString(), "-d", dir.resolve("out").toString(), "-w");

        assertNotEquals(2, said.code(), said.err());
        assertFalse(said.err().contains("unknown option"), said.err());
    }

    /**
     * And is not passed on to a command with nowhere to put it. Reading an unknown short token as an
     * operand is what every other command does with it; {@code mcp} has no operand, read none of
     * what it was handed, and served as though the line had been written on its own.
     */
    @Test
    void aCommandWithNoOperandRefusesWhatItWouldNotRead() {
        Said said = run("mcp", "-w");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`souther mcp`"), said.err());
        assertTrue(said.err().contains("-w"), said.err());
    }

    /** A path is not an operand of it either. */
    @Test
    void aCommandWithNoOperandRefusesAPathToo() {
        Said said = run("mcp", "model.sou");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("model.sou"), said.err());
    }

    /** And an option nobody has is still refused above it, as it was. */
    @Test
    void aCommandWithNoOperandStillRefusesAnUnknownOption() {
        Said said = run("mcp", "--nope");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("unknown option `--nope`"), said.err());
    }

    // --- the language the refusal is written in ----------------------------------------------------

    /**
     * Read off the same line it refuses. The command line resolves the language in one place and
     * every answer it gives is in that language; a refusal raised before the subcommand parses is
     * still one of its answers.
     */
    @Test
    void aRefusalIsWrittenInTheLanguageTheLineAsksFor() throws Exception {
        Said said = run("examples", model().toString(), "--nope", "--lang", "ja");

        assertEquals(2, said.code(), said.err());
        assertTrue(said.err().contains("`--nope`"), said.err());
        assertFalse(said.err().contains("unknown option"), said.err());
    }
}
