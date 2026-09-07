package souther.cli;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An archive stops being usable without anyone doing anything wrong: the binary is rebuilt or
 * copied elsewhere, the JDK is replaced, a write is cut short. Every one of those leaves a file
 * where the launcher looks, and what the run does with it is the launcher's to answer for — a run
 * that loads its own classes is slower and is still the run that was asked for.
 *
 * <p>Both of the ways the archive is reached are held here, because they are reached with different
 * flags: a command that may write one is handed the archive to write as well as to read, and a
 * command that may not is handed it to read.
 */
class AnArchiveTheJvmCannotUseCostsTheRunNothingButTheArchiveIT {

    private static Path binary;

    @BeforeAll
    static void theBuiltBinary() {
        binary = Path.of(System.getProperty("souther.binary", "target/souther"));
        assertTrue(Files.isExecutable(binary),
                "the prepended launcher is built before this runs: " + binary.toAbsolutePath());
    }

    @Test
    void aCompileReadsWhatItCanOfADamagedArchiveAndWritesItsClasses(@TempDir Path work)
            throws Exception {
        Path cache = damagedArchiveUnder(work);
        Path source = work.resolve("trip.sou");
        Files.writeString(source, """
                module trip exposing ( Draft, name )

                data Draft = { who: String }

                behavior name : (d: Draft) -> String
                let name (d) = d.who
                """);
        Path out = work.resolve("out");

        run(cache, "compile", "-d", out.toString(), source.toString());

        assertTrue(Files.isRegularFile(out.resolve("trip").resolve("Draft.class")),
                "the compile is the run, and the archive is what it would have started from");
    }

    @Test
    void aCommandThatOnlyReadsOneAnswersOverADamagedArchive(@TempDir Path work) throws Exception {
        String wrote = run(damagedArchiveUnder(work), "doc", "cli/commands");

        assertTrue(wrote.contains("souther"), "the page was written: " + wrote);
    }

    /** A cache holding a file under the name the launcher will look for, and nothing an archive. */
    private static Path damagedArchiveUnder(Path work) throws Exception {
        Path cache = work.resolve("cache");
        Path archives = Files.createDirectories(cache.resolve("souther"));
        byte[] noise = new byte[4096];
        new Random(0).nextBytes(noise);
        Files.write(archives.resolve(System.getProperty("souther.version") + ".jsa"), noise);
        return cache;
    }

    private static String run(Path cache, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                Stream.concat(Stream.of(binary.toString()), Stream.of(args)).toList());
        builder.environment().put("XDG_CACHE_HOME", cache.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(),
                "`souther " + String.join(" ", args) + "` answered instead: " + said);
        return said;
    }
}
